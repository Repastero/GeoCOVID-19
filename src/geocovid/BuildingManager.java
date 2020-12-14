package geocovid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import geocovid.agents.BuildingAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.InfectiousHumanAgent;
import geocovid.agents.PublicTransportAgent;
import geocovid.agents.WorkplaceAgent;
import repast.simphony.context.Context;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;

public final class BuildingManager {
	private static final Envelope[] SECTORALS_BOUNDARY = new Envelope[DataSet.SECTORALS_COUNT]; // Rectangulos con las coordenadas minimas y maximas de cada seccional 
	private static final Coordinate[] SECTORALS_CENTRE = new Coordinate[DataSet.SECTORALS_COUNT]; // Coordenadas del centro de cada seccional
	
	private static Context<Object> context; // Lo uso para crear el Query con PropertyEquals
	private static Geography<Object> geography; // Lo uso para crear el Query con PropertyEquals
	private static GeometryFactory geometryFactory = new GeometryFactory(); // Para crear punto al azar
	
	private static PublicTransportAgent publicTransport; // Por si viajan en transporte publico
	
	private static Map<String, List<List<WorkplaceAgent>>> placesMap = new HashMap<>();	// Lugares de entretenimiento / otros, separados por seccional
	private static Map<String, List<WorkplaceAgent>> workplacesMap = new HashMap<>();	// Lugares unicamente de trabajo
	
	private static Map<Integer, InfectiousHumanAgent> infectiousHumans = new HashMap<>(); // Humanos infectaods
	
	private static Map<String, int[]> placesCount = new HashMap<>();	// Cantidad de Places de cada tipo en cada seccional
	private static Map<String, Integer> placesTotal = new HashMap<>();	// Suma total de Places de cada tipo
	
	// Temporal
	private static List<PlaceProperty> enterPropList = new ArrayList<PlaceProperty>();
	private static List<PlaceProperty> otherPropList = new ArrayList<PlaceProperty>();
	//
	
	// Types que estan disponibles en el SHP de places
	private static String[] entertainmentTypes;
	// Chances de visitar cada lugar de ocio por cada grupo etario
	private static int[][] entertainmentChances;
	// Sumatoria de chances por cada grupo etario
	private static int[] entertainmentChancesSum;
	
	// Types que estan disponibles en el SHP de places
	private static String[] otherTypes;
	// Chances de visitar cada lugar de otros por cada grupo etario
	private static int[][] otherChances;
	// Sumatoria de chances por cada grupo etario
	private static int[] otherChancesSum;
	
	// Listado de types de Places y Workplaces que estan cerrados 
	private static final Set<String> closedPlaces = new HashSet<String>();
	
	// Ultimo limite de aforo en Places
	private static double activitiesCapacityLimit = 0d;
	
	/**
	 * Reinicia colecciones de Places, y guarda referencia de Context y Geography
	 * @param con Context
	 * @param geo Geography
	 */
	public static void initManager(Context<Object> con, Geography<Object> geo) {
		context = con;
		geography = geo;
		//
		publicTransport = null;
		activitiesCapacityLimit = 0d;
		//
		placesMap.clear(); // Por si cambio el SHP entre corridas
		infectiousHumans.clear(); // Por si quedo algun infeccioso
		//
		enterPropList.clear(); // Por las dudas
		otherPropList.clear(); // Por las dudas
		//
		closedPlaces.clear(); // Por las dudas
		//
		placesCount.clear(); // Por las dudas
		placesTotal.clear(); // Por las dudas
	}
	
	public static void setPublicTransport(PublicTransportAgent ptAgent) {
		publicTransport = ptAgent;
	}
	
	public static PublicTransportAgent getPublicTransport() {
		return publicTransport;
	}
	
	/**
	 * Inicializa los Envelopes con los limites 2d de cada seccional
	 * @param x1 minimo
	 * @param x2 maximo
	 * @param y1 minimo
	 * @param y2 maximo
	 */
	public static void setBoundary(double[] x1, double[] x2, double[] y1, double[] y2) {
		for (int i = 0; i < x1.length; i++) {
			SECTORALS_BOUNDARY[i] = new Envelope(x1[i], x2[i], y1[i], y2[i]);
			SECTORALS_CENTRE[i] = SECTORALS_BOUNDARY[i].centre();
		}
	}
	
	public static Coordinate[] getSectoralsCentre() {
		return SECTORALS_CENTRE;
	}
	
	/**
	 * Agregar el Building a la lista que pertenece segun la actividad.
	 * @param secIndex
	 * @param build
	 * @param prop
	 */
	public static void addPlace(int secIndex, WorkplaceAgent build, PlaceProperty prop) {
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
			List<List<WorkplaceAgent>> buildList = new ArrayList<List<WorkplaceAgent>>(DataSet.SECTORALS_COUNT);
			for (int i = 0; i < DataSet.SECTORALS_COUNT; i++) {
				buildList.add(new ArrayList<WorkplaceAgent>());
			}
			// Agregar el nuevo Building
			buildList.get(secIndex).add(build);
			placesMap.put(type, buildList);
			// Sumar 1 mas a la seccional que corresponda
			placesCount.put(type, new int[DataSet.SECTORALS_COUNT]);
			++placesCount.get(type)[secIndex];
			//
			if (prop.getActivityType() == 2) // Ocio
				enterPropList.add(prop);
			else
				otherPropList.add(prop);
		}
	}
	
	/**
	 * Agregar el Building a la lista que pertenece segun Type de lugar de trabajo.
	 * @param type
	 * @param build 
	 */
	public static void addWorkplace(String type, WorkplaceAgent build) {
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
	public static void closePlaces(String[] typesToClose) {
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
	public static void openPlaces(String[] typesToOpen) {
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
	
	private static void fillActivitiesChances(List<PlaceProperty> propList, String[] types, int[][] chances, int[] chancesSum) {
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
	
	public static void createActivitiesChances() {
		entertainmentTypes = new String[enterPropList.size()];
		entertainmentChances = new int[enterPropList.size()][DataSet.AGE_GROUPS];
		entertainmentChancesSum = new int[DataSet.AGE_GROUPS];
		fillActivitiesChances(enterPropList, entertainmentTypes, entertainmentChances, entertainmentChancesSum);

		otherTypes = new String[otherPropList.size()];
		otherChances = new int[otherPropList.size()][DataSet.AGE_GROUPS];
		otherChancesSum = new int[DataSet.AGE_GROUPS];
		fillActivitiesChances(otherPropList, otherTypes, otherChances, otherChancesSum);
		
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
	 * Metros cuadrados por persona para limitar aforo en Places.
	 * @param sqMeters
	 */
	public static void limitActivitiesCapacity(double sqMeters) {
		if (sqMeters == activitiesCapacityLimit)
			return;
		activitiesCapacityLimit = sqMeters;
		for (List<List<WorkplaceAgent>> workplaces : placesMap.values()) {
			workplaces.forEach(sect -> sect.forEach(work -> work.limitCapacity(sqMeters)));
		}
	}
	
	/**
	 * Metros cuadrados por persona para limitar aforo en Places tipo Otros.
	 * @param sqMeters
	 */
	public static void limitOtherActivitiesCapacity(double sqMeters) {
		if (sqMeters == activitiesCapacityLimit)
			return;
		activitiesCapacityLimit = sqMeters;
		for (String type : otherTypes) {
			placesMap.get(type).forEach(sect -> sect.forEach(work -> work.limitCapacity(sqMeters)));
		}
	}
	
	/**
	 * Selecciona de todas las actividades disponibles, una a realizar. 
	 * @param types
	 * @param chances
	 * @param chancesSum
	 * @param groupAge
	 * @return
	 */
	public static String findNewPlaceType(String[] types, int[][] chances, int[] chancesSum, int groupAge) {
		// Primero busca el tipo de actividad a realizar
        int rnd = RandomHelper.nextIntFromTo(1, chancesSum[groupAge]);
        int i;
    	for (i = 0; i < chances.length; i++) {
    		if (rnd <= chances[i][groupAge])
    			break;
    		rnd -= chances[i][groupAge];
		}
        //
    	return types[i];
	}
	
	/**
	 * Clasifica la busqueda de acuerdo al tipo de seccional y franja etaria del Humano.
	 * @param secType
	 * @param secIndex
	 * @param type
	 * @param human
	 * @param currentBuilding
	 * @param ageGroup
	 * @return
	 */
	public static BuildingAgent findRandomPlace(int secType, int secIndex, int type, HumanAgent human, BuildingAgent currentBuilding, int ageGroup) {
		String newActivity;
		if (type == 2) // Entretenimiento
			newActivity = findNewPlaceType(entertainmentTypes, entertainmentChances, entertainmentChancesSum, ageGroup);
		else // Otros
			newActivity = findNewPlaceType(otherTypes, otherChances, otherChancesSum, ageGroup);
		
		// Si la actividad esta cerrada, queda dando vueltas
		if (closedPlaces.contains(newActivity))
			return null;
		
		boolean atHome = false;
		if (currentBuilding == null) {
			// Si no esta en un Building, va a una de las seccionales aleatoriamente
			secIndex = RandomHelper.nextIntFromTo(0, DataSet.SECTORALS_COUNT - 1);
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
				if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.TRAVEL_OUTSIDE_CHANCE[secType]) {
					// Si funciona el transporte publico
					if (publicTransport != null) {
						if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.PUBLIC_TRANSPORT_CHANCE)
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
	
	private static void resizeBuildingsList(List<BuildingAgent> places, int maxSize) {
		// TODO comentar
		if (maxSize == -1) {
			// Todos los resultados
			return;
		}
		else {
			int pSize = places.size();
			while (pSize > maxSize) {
				places.remove(RandomHelper.nextIntFromTo(0, --pSize));
			}
		}
	}
	
	public static List<BuildingAgent> getActivityBuildings(int maxBuilding, String priType) {
		// TODO comentar
		List<BuildingAgent> places = new ArrayList<BuildingAgent>();
		placesMap.get(priType).forEach(sect -> places.addAll(sect));
		resizeBuildingsList(places, maxBuilding);
		return places;
	}
	
	public static List<BuildingAgent> getActivityBuildings(int maxBuilding, String priType, String secType) {
		// TODO comentar
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
	 * @param agentID
	 * @param coordinate
	 */
	public static void createInfectiousHuman(int agentID, Coordinate coordinate) {
		Point pointGeom = geometryFactory.createPoint(coordinate);
		Geometry geomCircle = pointGeom.buffer(0.00045d); // Crear circunferencia de 50 metros aprox (lat 110540 lon 111320)
		
		InfectiousHumanAgent infectedHuman = new InfectiousHumanAgent(agentID, coordinate);
		geography.move(infectedHuman, geomCircle);
		context.add(infectedHuman);
		//
		infectiousHumans.put(agentID, infectedHuman);
	}
	
	/**
	 * Mueve el punto del infeccioso a las coordenadas del nuevo building.
	 * @param agentID
	 * @param newCoordinate
	 */
	public static void moveInfectiousHuman(int agentID, Coordinate newCoordinate) {
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
			context.add(infHuman);
		}
		infHuman.setCurrentCoordinate(newCoordinate);
	}
	
	public static void deleteInfectiousHuman(int agentID) {
		InfectiousHumanAgent infHuman = infectiousHumans.remove(agentID);
		if (infHuman != null) // si es viajero, puede ser que nunca se creo el marcador
			context.remove(infHuman);
	}
	
	public static void hideInfectiousHuman(int agentID) {
		InfectiousHumanAgent infHuman = infectiousHumans.get(agentID);
		if (infHuman != null) {
			infHuman.setHidden(true);
			//-geography.move(infHuman, null); // no lo saca del mapa
			context.remove(infHuman);
		}
	}
}
