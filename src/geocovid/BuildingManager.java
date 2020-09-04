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
	
	// Types que estan disponibles en el SHP de places
	protected static final String entertainmentTypes[] = {"bar", "church", "gym", "hair_care", "physiotherapist", "restaurant", "sport_club", "sports_complex", "park"};
	// Chances de visitar cada tipo de place (suma 1000)
	private static final int entertainmentChances[]  = {140, 70, 130, 40, 100, 130, 125, 125, 140};
	// Types que estan disponibles en el SHP de places
	protected static final String otherTypes[] = {"bank", "car_repair", "bakery", "book_store", "clothing_store", "electronics_store", "computer_accessories_store", "hardware_store", "building_materials_supplier", "electronics_store+home_goods_store", "home_goods_store", "laundry", "liquor_store", "meal_takeaway", "pharmacy", "real_estate_agency", "supermarket+grocery_or_supermarket", "grocery_or_supermarket", "veterinary_care", "public_medical_center", "medical_office", "ice_cream_shop", "optician", "photographer", "government_office", "local_government_office", "police"};
	// Chances de visitar cada tipo de place (suma 1000)
	private static final int otherChances[] = {40, 40, 60, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 20, 20, 20, 20, 20};
	
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
	 */
	public static void addPlace(String type, WorkplaceAgent build) {
		if (placesMap.containsKey(type)) {
			placesMap.get(type).add(build);
		}
		else {
			List<WorkplaceAgent> buildList = new ArrayList<WorkplaceAgent>();
			buildList.add(build);
			placesMap.put(type, buildList);
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
	 * @param radius
	 * @return
	 */
	public static WorkplaceAgent findPlace(Geometry geo, String[] types, int[] chances, double radius) {
		WorkplaceAgent newPlace = null;
        int rnd = RandomHelper.nextIntFromTo(1, 1000);
        int i = 0;
        while (rnd > chances[i]) {
        	// La suma de las pobabilidades no debe dar mas de 1000
        	rnd -= chances[i];
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
	 * @return
	 */
	public static BuildingAgent findRandomPlace(int type, BuildingAgent currentBuilding, double radius) {
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
			foundedPlace = findPlace(geo, entertainmentTypes, entertainmentChances, radius);
		}
		else if (type == 3) { // Otros
			foundedPlace = findPlace(geo, otherTypes, otherChances, radius);
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
