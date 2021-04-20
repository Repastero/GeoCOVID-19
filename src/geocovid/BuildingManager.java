package geocovid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import geocovid.agents.BuildingAgent;
import geocovid.agents.HomeAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.InfectiousHumanAgent;
import geocovid.agents.PublicTransportAgent;
import geocovid.agents.WorkplaceAgent;
import geocovid.contexts.SubContext;
import repast.simphony.context.Context;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;

/**
 * Contiene los places de trabajo, ocio y otros.<p>
 * Implementa metodos para asignar places segun la actividad del humano, cerrar y abrir places.<p>
 * Tambien controla la creacion y desplazamiento del indicador de humano infectado en mapa.
 */
public final class BuildingManager {
	/** Rectangulos con las coordenadas minimas y maximas de cada seccional */
	private Envelope[] sectoralsBoundary;
	/** Coordenadas del centro de cada seccional */
	private Coordinate[] sectoralsCentre;
	
	private static Context<Object> mainContext; // Para agregar indicador de humano infectado
	private static Geography<Object> geography; // Para agregar indicador de humano infectado en mapa
	private static GeometryFactory geometryFactory = new GeometryFactory(); // Para crear punto al azar
	
	/** Puntero al objeto que controla el transporte publico */
	private PublicTransportAgent publicTransport;
	
	/** Lugares de entretenimiento / otros, separados por seccional */
	private Map<String, List<List<WorkplaceAgent>>> placesMap = new HashMap<>();
	/** Lugares unicamente de trabajo */
	private Map<String, List<WorkplaceAgent>> workplacesMap = new HashMap<>();
	/** Indicadores en mapa, de humanos infectados */
	private Map<Integer, InfectiousHumanAgent> infectiousHumans = new HashMap<>();
	
	/** Cantidad de Places de cada tipo en cada seccional */
	private Map<String, int[]> placesCount = new HashMap<>();
	/** Suma total de Places de cada tipo */
	private Map<String, Integer> placesTotal = new HashMap<>();
	
	/** Temporal - Places de entretenimiento */
	private List<PlaceProperty> enterPropList = new ArrayList<PlaceProperty>();
	/** Temporal - Places de otros */
	private List<PlaceProperty> otherPropList = new ArrayList<PlaceProperty>();
	
	/** Types que estan disponibles en el SHP de places */
	private String[] entertainmentTypes;
	/** Chances de visitar cada lugar de ocio por cada grupo etario */
	private int[][] entertainmentChances;
	/** Sumatoria de chances por cada grupo etario */
	private int[] entertainmentChancesSum;
	
	/** Types que estan disponibles en el SHP de places */
	private String[] otherTypes;
	/** Chances de visitar cada lugar de otros por cada grupo etario */
	private int[][] otherChances;
	/** Sumatoria de chances por cada grupo etario */
	private int[] otherChancesSum;
	
	/** Listado de types de Places y Workplaces que estan cerrados */ 
	private final Set<String> closedPlaces = new HashSet<String>();
	
	/** Ultimo limite de aforo en Places */
	private double activitiesCapacityLimit = 0d;
	
	private SubContext context; // Puntero
	private int sectoralsCount; // Puntero
	
	/**
	 * Reinicia colecciones de Places, y guarda referencia de SubContext y cantidad de seccionales
	 * @param subContext sub contexto municipio
	 * @param sectorals cantidad de seccionales en municipio
	 */
	public BuildingManager(SubContext subContext, int sectorals) {
		publicTransport = null;
		activitiesCapacityLimit = 1d;
		//
		placesMap.clear(); // Por si cambio el SHP entre corridas
		workplacesMap.clear(); // Por si cambio el SHP entre corridas
		infectiousHumans.clear(); // Por si quedo algun infeccioso
		//
		enterPropList.clear(); // Por las dudas
		otherPropList.clear(); // Por las dudas
		//
		closedPlaces.clear(); // Por las dudas
		//
		placesCount.clear(); // Por las dudas
		placesTotal.clear(); // Por las dudas
		//
		this.context = subContext; 
		this.sectoralsCount = sectorals;
	}
	
	public static void setMainContextAndGeography(Context<Object> con, Geography<Object> geo) {
		mainContext = con;
		geography = geo;
	}
	
	/**
	 * {@link BuildingManager#publicTransport}
	 * @param ptAgent instancia de PublicTransportAgent
	 */
	public void setPublicTransport(PublicTransportAgent ptAgent) {
		publicTransport = ptAgent;
	}
	
	/**
	 * @return {@link BuildingManager#publicTransport}
	 */
	public PublicTransportAgent getPublicTransport() {
		return publicTransport;
	}
	
	/**
	 * Inicializa los Envelopes con los limites 2d de cada seccional
	 * @param x1 minimo
	 * @param x2 maximo
	 * @param y1 minimo
	 * @param y2 maximo
	 */
	public void setBoundary(double[] x1, double[] x2, double[] y1, double[] y2) {
		sectoralsBoundary = new Envelope[sectoralsCount]; 
		sectoralsCentre = new Coordinate[sectoralsCount];
		for (int i = 0; i < x1.length; i++) {
			sectoralsBoundary[i] = new Envelope(x1[i], x2[i], y1[i], y2[i]);
			sectoralsCentre[i] = sectoralsBoundary[i].centre();
		}
	}
	
	/**
	 * @return {@link BuildingManager#sectoralsCentre}
	 */
	public Coordinate[] getSectoralsCentre() {
		return sectoralsCentre;
	}
	
	/**
	 * Agregar el Workplace a la lista que pertenece segun la actividad.
	 * @param secIndex indice seccional
	 * @param build WorkplaceAgent a agregar
	 * @param prop PlaceProperty de place
	 */
	public void addPlace(int secIndex, WorkplaceAgent build, PlaceProperty prop) {
		String type = prop.getGooglePlaceType();
		// Chequear si ya se agregaron de este tipo
		if (placesMap.containsKey(type)) {
			// Agregar el nuevo Building
			placesMap.get(type).get(secIndex).add(build);
			// Sumar 1 mas a la seccional que corresponda
			++placesCount.get(type)[secIndex];
		}
		// Si es el primer Place de su tipo, inicializar listas que van en Maps
		else {
			// Crear una lista del nuevo tipo de Place, para cada seccional
			List<List<WorkplaceAgent>> buildList = new ArrayList<List<WorkplaceAgent>>(sectoralsCount);
			for (int i = 0; i < sectoralsCount; i++) {
				buildList.add(new ArrayList<WorkplaceAgent>());
			}
			// Agregar el nuevo Building
			buildList.get(secIndex).add(build);
			placesMap.put(type, buildList);
			// Sumar 1 mas a la seccional que corresponda
			placesCount.put(type, new int[sectoralsCount]);
			++placesCount.get(type)[secIndex];
			//
			if (prop.getActivityType() == 2) // Ocio
				enterPropList.add(prop);
			else
				otherPropList.add(prop);
		}
	}
	
	/**
	 * Agregar el Workplace a la lista que pertenece segun Type de lugar de trabajo.
	 * @param type tipo de place
	 * @param build WorkplaceAgent a agregar
	 */
	public void addWorkplace(String type, WorkplaceAgent build) {
		// Chequear si ya se agregaron de este tipo
		if (workplacesMap.containsKey(type)) {
			workplacesMap.get(type).add(build);
		}
		// Si es el primer Workplace de su tipo, inicializar lista que va en Map
		else {
			List<WorkplaceAgent> buildList = new ArrayList<WorkplaceAgent>();
			buildList.add(build);
			workplacesMap.put(type, buildList);
		}
	}
	
	/**
	 * Cierra los lugares de trabajo/estudio y las actividades dadas.
	 * @param typesToClose tipos de Places
	 */
	public void closePlaces(String[] typesToClose) {
		for (String type : typesToClose) {
			if (closedPlaces.contains(type)) // Ya esta cerrado
				continue;
			// Primero busca entre los lugares de trabajo
			if (workplacesMap.containsKey(type)) {
				workplacesMap.get(type).forEach(work -> work.close()); // Cierra el workplace
				closedPlaces.add(type);
			}
			// Si no, es una actividad
			else if (placesMap.containsKey(type)) {
				// Cierra los Places del mismo tipo
				placesMap.get(type).forEach(sect -> sect.forEach(work -> work.close()));
				closedPlaces.add(type);
			}
		}
	}
	
	/**
	 * Abre los lugares de trabajo/estudio y las actividades dadas.
	 * @param typesToOpen tipos de Places
	 */
	public void openPlaces(String[] typesToOpen) {
		for (String type : typesToOpen) {
			if (!closedPlaces.contains(type)) // No esta cerrado
				continue;
			// Primero busca entre los lugares de trabajo
			if (workplacesMap.containsKey(type)) {
				// Abre los Workplaces del mismo tipo
				workplacesMap.get(type).forEach(work -> work.open());
				closedPlaces.remove(type);
			}
			// Si no, es una actividad
			else if (closedPlaces.contains(type)) {
				placesMap.get(type).forEach(sect -> sect.forEach(work -> work.open()));
				closedPlaces.remove(type);
			}
		}
	}
	
	/**
	 * Ventilar o no hogares.
	 * @param ventilate
	 */
	public void ventilateHomes(boolean ventilate) {
		Stream<Object> iteral = context.getObjectsAsStream(HomeAgent.class);
		iteral.forEach(home -> ((HomeAgent) home).setVentilated(ventilate));
	}
	
	/**
	 * Ventilar o no places de trabajo, escuelas y universidades.
	 * @param ventilate
	 */
	public void ventilateWorkplaces(boolean ventilate) {
		for (List<WorkplaceAgent> workplaces : workplacesMap.values()) {
			workplaces.forEach(work -> work.setVentilated(ventilate));
		}
	}
	
	/**
	 * Ventilar o no places tipo otros.
	 * @param ventilate
	 */
	public void ventilateOtherPlaces(boolean ventilate) {
		for (String type : otherTypes) {
			placesMap.get(type).forEach(sect -> sect.forEach(work -> work.setVentilated(ventilate)));
		}
	}
	
	/**
	 * Ventilar o no places tipo ocio.
	 * @param ventilate
	 */
	public void ventilateEntertainmentPlaces(boolean ventilate) {
		for (String type : entertainmentTypes) {
			placesMap.get(type).forEach(sect -> sect.forEach(work -> work.setVentilated(ventilate)));
		}
	}
	
	/**
	 * Retorna los places de trabajo que corresponden al tipo dado.
	 * @param type tipo de Workplace
	 * @return lista de <b>WorkplaceAgent</b>
	 */
	public List<WorkplaceAgent> getWorkplaces(String type) {
		return workplacesMap.get(type);
	}
	
	/**
	 * Suma las chances de cada sub tipo y las guarda en los array dados.
	 * @param propList lista de PlaceProperty
	 * @param types tipos principales de places
	 * @param chances chances por franja etaria
	 * @param chancesSum chances totales por franja etaria
	 */
	private void fillActivitiesChances(List<PlaceProperty> propList, String[] types, int[][] chances, int[] chancesSum) {
		PlaceProperty pp;
		int i, j;
		for (i = 0; i < propList.size(); i++) {
			pp = propList.get(i);
			types[i] = pp.getGooglePlaceType();
			chances[i] = pp.getActivityChances();
			for (j = 0; j < chances[i].length; j++) {
				chancesSum[j] += chances[i][j];
			}
		}
	}
	
	/**
	 * Crea arrays con las chances de realizar cada actividad segun grupo etario.
	 */
	public void createActivitiesChances() {
		// Guarda las chances para actividades tipo ocio
		entertainmentTypes = new String[enterPropList.size()];
		entertainmentChances = new int[enterPropList.size()][DataSet.AGE_GROUPS];
		entertainmentChancesSum = new int[DataSet.AGE_GROUPS];
		fillActivitiesChances(enterPropList, entertainmentTypes, entertainmentChances, entertainmentChancesSum);
		// Guarda las chances para actividades tipo otros
		otherTypes = new String[otherPropList.size()];
		otherChances = new int[otherPropList.size()][DataSet.AGE_GROUPS];
		otherChancesSum = new int[DataSet.AGE_GROUPS];
		fillActivitiesChances(otherPropList, otherTypes, otherChances, otherChancesSum);
		
		// Ya no tienen uso estas listas temporales
		enterPropList.clear();
		otherPropList.clear();
		
		// Guarda en otro Map la suma total de cada tipo de Place
		placesCount.forEach((key, value) -> {
			int placesSum = value[0];
			for (int i = value.length - 1; i > 0; i--)
				placesSum += value[i];
			placesTotal.put(key, placesSum);
		});
	}
	
	/**
	 * Limita aforo en Places tipo Otros y Ocio.
	 * @param sqMeters metros cuadrados por persona
	 */
	public void limitActivitiesCapacity(double sqMeters) {
		if (sqMeters == activitiesCapacityLimit)
			return;
		activitiesCapacityLimit = sqMeters;
		for (List<List<WorkplaceAgent>> workplaces : placesMap.values()) {
			workplaces.forEach(sect -> sect.forEach(work -> work.limitCapacity(sqMeters)));
		}
	}
	
	/**
	 * Limita aforo en Places tipo Otros.
	 * @param sqMeters metros cuadrados por persona
	 */
	public void limitOtherActCap(double sqMeters) {
		if (sqMeters == activitiesCapacityLimit)
			return;
		activitiesCapacityLimit = sqMeters;
		for (String type : otherTypes) {
			placesMap.get(type).forEach(sect -> sect.forEach(work -> work.limitCapacity(sqMeters)));
		}
	}
	
	/**
	 * Limita el aforo en Places tipo Ocio.
	 * @param sqMeters metros cuadrados por persona
	 */
	public void limitEntertainmentActCap(double sqMeters) {
		if (sqMeters == activitiesCapacityLimit)
			return;
		activitiesCapacityLimit = sqMeters;
		for (String type : entertainmentTypes) {
			placesMap.get(type).forEach(sect -> sect.forEach(work -> work.limitCapacity(sqMeters)));
		}
	}
	
	/**
	 * Selecciona de todas las actividades disponibles, una a realizar. 
	 * @param types tipos de actividades (places)
	 * @param chances probabilidad de actividad por grupo etario
	 * @param chancesSum suma por grupo etario
	 * @param ageGroup indice grupo etario
	 * @return <b>String</b> tipo de actividad (place)
	 */
	public String findNewPlaceType(String[] types, int[][] chances, int[] chancesSum, int ageGroup) {
		// Primero busca el tipo de actividad a realizar
        int rnd = RandomHelper.nextIntFromTo(1, chancesSum[ageGroup]);
        int i;
    	for (i = 0; i < chances.length; i++) {
    		if (rnd <= chances[i][ageGroup])
    			break;
    		rnd -= chances[i][ageGroup];
		}
        //
    	return types[i];
	}
	
	/**
	 * Busca un nuevo place segun actividad a realizar y otros parametros.
	 * @param secType indice tipo seccional
	 * @param secIndex indice seccional
	 * @param type indice tipo actividad
	 * @param human agente humano
	 * @param currentBuilding parcela actual o null
	 * @param ageGroup indice grupo etario
	 * @return <b>BuildingAgent</b> o <b>null</b>
	 */
	public BuildingAgent findRandomPlace(int secType, int secIndex, int type, HumanAgent human, BuildingAgent currentBuilding, int ageGroup) {
		String newActivity;
		if (type == 2) // Entretenimiento
			newActivity = findNewPlaceType(entertainmentTypes, entertainmentChances, entertainmentChancesSum, ageGroup);
		else // Otros
			newActivity = findNewPlaceType(otherTypes, otherChances, otherChancesSum, ageGroup);
		
		// Si la actividad esta cerrada, queda dando vueltas fuera de parcelas
		if (closedPlaces.contains(newActivity))
			return null;
		
		boolean atHome = false;
		if (currentBuilding == null) {
			// Si no esta en un Building, va a una de las seccionales aleatoriamente
			secIndex = RandomHelper.nextIntFromTo(0, sectoralsCount - 1);
		}
		else if (currentBuilding.getSectoralIndex() == secIndex) {
			atHome = true;
			// Si esta en otro barrio se queda, hasta volver a casa
		}
		
		int[] placeSecCount = placesCount.get(newActivity);
    	boolean getOut = false;
    	// Chequear si actividad no esta disponible en la seccional actual
    	if (placeSecCount[secIndex] == 0) {
    		getOut = true;
    	}
    	// Chequear si actividad disponible en otras seccionales
    	else if (placesTotal.get(newActivity) - placeSecCount[secIndex] != 0) {
    		if (atHome) {
				// Si se va fuera del barrio
				if (RandomHelper.nextIntFromTo(1, 100) <= context.travelOutsideChance(secType)) {
					// Si funciona el transporte publico
					if (publicTransport != null) {
						if (RandomHelper.nextIntFromTo(1, 100) <= context.publicTransportChance())
							publicTransport.jumpAboard(human, secIndex);
					}
					getOut = true;
				}
    		}
    	}
    	
    	int rndPlaceIndex;
    	if (getOut) {
    		// Si le toca cambiar de seccional, busca a que seccional ir
    		int placesSum = placesTotal.get(newActivity) - placeSecCount[secIndex]; // restar la % de la seccional donde esta
    		rndPlaceIndex = RandomHelper.nextIntFromTo(0, placesSum - 1);
    		for (int i = 0; i < placeSecCount.length; i++) {
    			if (i == secIndex) // saltea su propia seccional
    				continue;
	    		if (rndPlaceIndex < placeSecCount[i]) {
	    			secIndex = i; // seccional seleccionada
	    			break;
	    		}
	    		rndPlaceIndex -= placeSecCount[i];
    		}
    	}
    	else {
    		// Busca un lugar aleatorio en la seccional donde esta
    		rndPlaceIndex = RandomHelper.nextIntFromTo(0, placeSecCount[secIndex] - 1);
    	}
        return placesMap.get(newActivity).get(secIndex).get(rndPlaceIndex);
	}
	
	/**
	 * Remueve elementos al azar de la lista hasta alcanzar la cantidad deseada.
	 * @param places lista de Places
	 * @param maxSize limite de Places
	 */
	private void resizeBuildingsList(List<BuildingAgent> places, int maxSize) {
		if (maxSize == -1) // Todos los resultados
			return;
		else {
			int pSize = places.size();
			while (pSize > maxSize) {
				places.remove(RandomHelper.nextIntFromTo(0, --pSize)); // 0 a size-1
			}
		}
	}
	
	/**
	 * Crea una lista de todos los BuildingAgent con el mismo tipo primario de Place. 
	 * @param maxBuilding cantidad maxima de resultados
	 * @param priType tipo primario
	 * @return lista de Places <b>BuildingAgent</b>
	 */
	public List<BuildingAgent> getActivityBuildings(int maxBuilding, String priType) {
		List<BuildingAgent> places = new ArrayList<BuildingAgent>();
		placesMap.get(priType).forEach(sect -> places.addAll(sect));
		resizeBuildingsList(places, maxBuilding);
		return places;
	}
	
	/**
	 * Crea una lista de todos los BuildingAgent con el mismo tipo secundario de Place.
	 * @param maxBuilding cantidad maxima de resultados
	 * @param priType tipo primario
	 * @param secType tipo secundario
	 * @return lista de Places <b>BuildingAgent</b>
	 */
	public List<BuildingAgent> getActivityBuildings(int maxBuilding, String priType, String secType) {
		List<BuildingAgent> places = new ArrayList<BuildingAgent>();
		placesMap.get(priType).forEach(sect -> sect.forEach(
		work -> {
			if (work.getType().equals(secType))
				places.add(work);
        }));
		resizeBuildingsList(places, maxBuilding);
		return places;
	}
	
	/**
	 * Crea un punto en las coordenadas del building donde se encuentra el infeccioso.
	 * @param agentID id de humano infeccioso
	 * @param coordinate coordinadas actuales
	 */
	public void createInfectiousHuman(int agentID, Coordinate coordinate) {
		Point pointGeom = geometryFactory.createPoint(coordinate);
		Geometry geomCircle = pointGeom.buffer(0.00045d); // Crear circunferencia de 50 metros aprox (lat 110540 lon 111320)
		
		InfectiousHumanAgent infectedHuman = new InfectiousHumanAgent(agentID, coordinate);
		geography.move(infectedHuman, geomCircle);
		mainContext.add(infectedHuman);
		//
		infectiousHumans.put(agentID, infectedHuman);
	}
	
	/**
	 * Mueve el punto del infeccioso a las coordenadas del nuevo building.
	 * @param agentID id de humano infeccioso
	 * @param newCoordinate coordinadas actuales
	 */
	public void moveInfectiousHuman(int agentID, Coordinate newCoordinate) {
		InfectiousHumanAgent infHuman = infectiousHumans.get(agentID);
		// Si aun no tiene marcador, se crea desde cero
		if (infHuman == null) {
			createInfectiousHuman(agentID, newCoordinate);
			return;
		}
		// Si la posicion del marcador es reciente, se traslada a la nueva
		if (!infHuman.isHidden()) {
			double lonShift = newCoordinate.x - infHuman.getCurrentCoordinate().x;
			double latShift = newCoordinate.y - infHuman.getCurrentCoordinate().y;
			geography.moveByDisplacement(infHuman, lonShift, latShift);
		}
		// Si dejo de trackear al humano, crea nuevo marcador (geometria)
		else {
			infHuman.setHidden(false);
			//
			Point pointGeom = geometryFactory.createPoint(newCoordinate);
			Geometry geomCircle = pointGeom.buffer(0.00045d); // Crear circunferencia de 50 metros aprox (lat 110540 lon 111320)
			geography.move(infHuman, geomCircle);
			mainContext.add(infHuman);
		}
		infHuman.setCurrentCoordinate(newCoordinate);
	}
	
	/**
	 * Eliminar indicador de humano infectado
	 * @param agentID id de humano previamente infectado
	 */
	public void deleteInfectiousHuman(int agentID) {
		InfectiousHumanAgent infHuman = infectiousHumans.remove(agentID);
		if (infHuman != null) { // si es viajero, puede ser que nunca se creo el marcador
			geography.move(infHuman, null); // sin location
			mainContext.remove(infHuman);
		}
	}

	/**
	 * Ocultar indicador de humano infectado
	 * @param agentID id de humano infeccioso
	 */
	public void hideInfectiousHuman(int agentID) {
		InfectiousHumanAgent infHuman = infectiousHumans.get(agentID);
		if (infHuman != null) {
			infHuman.setHidden(true);
			mainContext.remove(infHuman);
		}
	}
}
