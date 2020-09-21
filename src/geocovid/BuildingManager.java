package geocovid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import geocovid.agents.BuildingAgent;
import geocovid.agents.InfectiousHumanAgent;
import geocovid.agents.WorkplaceAgent;
import repast.simphony.context.Context;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;

public final class BuildingManager {
	private static final Envelope BOUNDARY = new Envelope(); // Rectangulo con las coordenadas minimas y maximas del GIS
	
	private static Context<Object> context; // Lo uso para crear el Query con PropertyEquals
	private static Geography<Object> geography; // Lo uso para crear el Query con PropertyEquals
	private static GeometryFactory geometryFactory = new GeometryFactory(); // Para crear punto al azar
	
	private static Map<String, List<WorkplaceAgent>> placesMap = new HashMap<>(); // Lugares de entretenimiento / otros
	
	private static Map<Integer, InfectiousHumanAgent> infectiousHumans = new HashMap<>(); // Humanos infectaods
	
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
	
	/**
	 * Reinicia colecciones de Places, y guarda referencia de Context y Geography
	 * @param con Context
	 * @param geo Geography
	 */
	public static void initManager(Context<Object> con, Geography<Object> geo) {
		context = con;
		geography = geo;
		placesMap.clear(); // Por si cambio el SHP entre corridas
		infectiousHumans.clear(); // Por si quedo algun infeccioso
		//
		enterPropList.clear(); // Por las dudas
		otherPropList.clear(); // Por las dudas
	}
	
	/**
	 * Inicializa el Envelope con los limites 2d del contexto
	 * @param x1 minimo
	 * @param x2 maximo
	 * @param y1 minimo
	 * @param y2 maximo
	 */
	public static void setBoundary(double x1, double x2, double y1, double y2) { 
		BOUNDARY.init(x1, x2, y1, y2);
	}
	
	/**
	 * Agregar el Building a la lista que pertenece segun Type.
	 * @param type
	 * @param build
	 * @param placeProp 
	 */
	public static void addPlace(String type, WorkplaceAgent build, PlaceProperty prop) {
		if (placesMap.containsKey(type)) {
			placesMap.get(type).add(build);
		}
		else {
			List<WorkplaceAgent> buildList = new ArrayList<WorkplaceAgent>();
			buildList.add(build);
			placesMap.put(type, buildList);
			//
			if (prop.getActivityType() == 2) // Ocio
				enterPropList.add(prop);
			else
				otherPropList.add(prop);
		}
	}

	private static void fillActivitiesChances(List<PlaceProperty> propList, String[] types, int[][] chances, int[] chancesSum) {
		PlaceProperty pp;
		int i, j;
		for (i = 0; i < propList.size(); i++) {
			pp = propList.get(i);
			types[i] = pp.getGoogleMapsType();
			chances[i] = pp.getActivityChances(); // TODO copiar la referencia en lugar de cada int ???
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
		
		/* TESTEO
		for (int i = 0; i < entertainmentTypes.length; i++) {
			System.out.println(entertainmentTypes[i]+" "+entertainmentChances[i][0]+" "+entertainmentChances[i][1]+" "+entertainmentChances[i][2]+" "+entertainmentChances[i][3]+" "+entertainmentChances[i][4]);
		}
		System.out.println("TOTAL "+entertainmentChancesSum[0]+" "+entertainmentChancesSum[1]+" "+entertainmentChancesSum[2]+" "+entertainmentChancesSum[3]+" "+entertainmentChancesSum[4]);
		System.out.println("-------------------------------------------------------");
		for (int i = 0; i < otherTypes.length; i++) {
			System.out.println(otherTypes[i]+" "+otherChances[i][0]+" "+otherChances[i][1]+" "+otherChances[i][2]+" "+otherChances[i][3]+" "+otherChances[i][4]);
		}
		System.out.println("TOTAL "+otherChancesSum[0]+" "+otherChancesSum[1]+" "+otherChancesSum[2]+" "+otherChancesSum[3]+" "+otherChancesSum[4]);
		System.out.println("-------------------------------------------------------");
		*/
	}
	
	/**
	 * Metros cuadrados por persona para limitar aforo en Places (en espacios cerrados).
	 * @param sqMeters
	 */
	public static void limitActivitiesCapacity(double sqMeters) {
		for (List<WorkplaceAgent> workplaces : placesMap.values()) {
			workplaces.forEach(work -> work.limitCapacity(sqMeters));
		}
	}
	
	/**
	 * Selecciona una coordenada aleatoria a partir de los margenes del GIS.
	 * @return Coordinate
	 */
	public static Coordinate getRandomCoordinate() {
		double x = RandomHelper.nextDoubleFromTo(BOUNDARY.getMinX(), BOUNDARY.getMaxX());
		double y = RandomHelper.nextDoubleFromTo(BOUNDARY.getMinY(), BOUNDARY.getMaxY());
		return new Coordinate(x, y);
	}
	
	/**
	 * Busca un nuevo lugar para el humano, que quede dentro del limite de distancia del punto dado.
	 * @param geo
	 * @param types
	 * @param chances
	 * @param chancesSum 
	 * @param radius
	 * @return
	 */
	public static WorkplaceAgent findPlace(Geometry geo, String[] types, int[][] chances, int[] chancesSum, double radius, int groupAge) {
		WorkplaceAgent newPlace = null;
        int rnd = RandomHelper.nextIntFromTo(1, chancesSum[groupAge]);
        int i = 0;
        while (rnd > chances[i][groupAge]) {
        	// La suma de las pobabilidades no debe dar mas de 1000
        	rnd -= chances[i][groupAge];
        	++i;
        }
        //
        List<WorkplaceAgent> placesList = placesMap.get(types[i]);
        if (placesList == null) {
        	System.err.println("Sin places para actividad: " + types[i]);
        	return null;
        }
        int count = placesList.size();
        if (radius == -1) { // Radio infinito - selecciona al azar
        	newPlace = placesList.get(RandomHelper.nextIntFromTo(0, count-1));
        }
        else {
	        int[] indexes = new int[count];
	        int index = 0;
	        for (i = 0; i < count; i++) {
	        	if (geo.isWithinDistance(placesList.get(i).getGeometry(), radius)) {
	        		indexes[index++] = i;
	        	}
	        }
	        if (index > 0)
	        	newPlace = placesList.get(indexes[RandomHelper.nextIntFromTo(0, index-1)]);
	        // Si no hay lugares disponibles se quede donde esta
        }
        return newPlace;
	}
	
	/**
	 * Clasifica la busqueda de acuerdo al tipo y radio.
	 * @param type
	 * @param currentBuilding
	 * @param radius
	 * @param ageGroup 
	 * @return
	 */
	public static BuildingAgent findRandomPlace(int type, BuildingAgent currentBuilding, double radius, int ageGroup) {
		// Hay una probabilidad de que el Humano se traslade fuera del contexto para realizar las actividades de ocio u otros
		// Esto aplica a todos los agentes Humanos, tanto locales como extranjeros
		if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.TRAVEL_OUTSIDE_CHANCE[DataSet.SECTORAL])
			return null;
		
		BuildingAgent foundedPlace = null;
		Geometry geo;
		if (currentBuilding != null) {
			geo = currentBuilding.getGeometry();
		}
		else {
			// Si no esta en un Building, esta en un punto aleatorio
			geo = geometryFactory.createPoint(getRandomCoordinate());
		}
		
		if (type == 2) { // Entretenimiento
			foundedPlace = findPlace(geo, entertainmentTypes, entertainmentChances, entertainmentChancesSum, radius, ageGroup);
		}
		else if (type == 3) { // Otros
			foundedPlace = findPlace(geo, otherTypes, otherChances, otherChancesSum, radius, ageGroup);
		}
		return foundedPlace;
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
