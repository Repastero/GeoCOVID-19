package geocovid.agents;

import java.util.HashMap;
import java.util.Map;

import com.vividsolutions.jts.geom.Geometry;

import repast.simphony.random.RandomHelper;

public class WorkplaceAgent extends BuildingAgent {
	//private List<int[]> workersPosition = new ArrayList<int[]>();
	private int[][] workPositions;
	private int workPositionsCount;
	/** Cantidad maxima de trabajdores en lugar de trabajo */
	private int workers = 4;
	/** Cantidad de trajadores por actividad */
	private static Map<String, Integer> workersPerType;
	/** Metros cuadrados por trabajador por actividad */
	private static Map<String, Integer> workersPerTypeAndArea;
	static {
		workersPerType = new HashMap<>();
		workersPerType.put("airport", 20);
		workersPerType.put("aquarium", 6);
		workersPerType.put("art_gallery", 6);
		workersPerType.put("atm", 2);
		workersPerType.put("book_store", 2);
		workersPerType.put("car_dealer", 10);
		workersPerType.put("cemetery", 4);
		workersPerType.put("church", 6);
		workersPerType.put("funeral_home", 6);
		workersPerType.put("gym", 6);
		workersPerType.put("laundry", 3);
		workersPerType.put("library", 3);
		workersPerType.put("meal_takeaway", 10);
		workersPerType.put("movie_rental", 3);
		workersPerType.put("moving_company", 6);
		workersPerType.put("museum", 6);
		workersPerType.put("parking", 3);
		workersPerType.put("stadium", 10);
		workersPerType.put("synagogue", 4);
		
		workersPerType.put("park", 1);
		workersPerType.put("ice_cream_shop", 4);
		workersPerType.put("optician", 3);
		workersPerType.put("photographer", 2);
		workersPerType.put("computer_accessories_store", 3);
		
	    //
	    workersPerTypeAndArea = new HashMap<>();
	    workersPerTypeAndArea.put("accounting", 20);
	    workersPerTypeAndArea.put("accounting+local_government_office", 15);
	    workersPerTypeAndArea.put("bakery", 60);
	    workersPerTypeAndArea.put("bakery+cafe", 70);
	    workersPerTypeAndArea.put("bank", 60);
	    workersPerTypeAndArea.put("bar", 80);
	    workersPerTypeAndArea.put("bar+liquor_store", 80);
	    workersPerTypeAndArea.put("beauty_salon", 30);
	    workersPerTypeAndArea.put("beauty_salon+spa", 80);
	    workersPerTypeAndArea.put("bicycle_store", 50);
	    workersPerTypeAndArea.put("bus_station", 80);
	    workersPerTypeAndArea.put("cafe", 60);
	    workersPerTypeAndArea.put("car_repair", 100);
	    workersPerTypeAndArea.put("car_repair+store", 60);
	    workersPerTypeAndArea.put("car_wash", 40);
	    workersPerTypeAndArea.put("casino", 60);
	    workersPerTypeAndArea.put("clothing_store", 40);
	    workersPerTypeAndArea.put("convenience_store", 50);
	    workersPerTypeAndArea.put("courthouse", 40);
	    workersPerTypeAndArea.put("dentist", 80);
	    workersPerTypeAndArea.put("doctor", 80);
	    workersPerTypeAndArea.put("drugstore", 30);
	    workersPerTypeAndArea.put("electronics_store", 60);
	    workersPerTypeAndArea.put("fire_station", 30);
	    workersPerTypeAndArea.put("florist", 40);
	    workersPerTypeAndArea.put("furniture_store", 60);
	    workersPerTypeAndArea.put("gas_station", 50);
	    workersPerTypeAndArea.put("hair_care", 30);
	    workersPerTypeAndArea.put("hardware_store", 50);
	    workersPerTypeAndArea.put("home_goods_store", 50);
	    workersPerTypeAndArea.put("hospital", 100);
	    workersPerTypeAndArea.put("insurance_agency", 20);
	    workersPerTypeAndArea.put("jewelry_store", 30);
	    workersPerTypeAndArea.put("lawyer", 20);
	    workersPerTypeAndArea.put("liquor_store", 40);
	    workersPerTypeAndArea.put("local_government_office", 15);
	    workersPerTypeAndArea.put("locksmith", 40);
	    workersPerTypeAndArea.put("lodging", 200);
	    workersPerTypeAndArea.put("meal_delivery", 50);
	    workersPerTypeAndArea.put("movie_theater", 80);
	    workersPerTypeAndArea.put("night_club", 100);
	    workersPerTypeAndArea.put("pet_store", 40);
	    workersPerTypeAndArea.put("pharmacy", 40);
	    workersPerTypeAndArea.put("pharmacy+veterinary_care", 50);
	    workersPerTypeAndArea.put("physiotherapist", 40);
	    workersPerTypeAndArea.put("police", 40);
	    workersPerTypeAndArea.put("post_office", 20);
	    workersPerTypeAndArea.put("primary_school", 8);
	    workersPerTypeAndArea.put("primary_school+secondary_school", 8);
	    workersPerTypeAndArea.put("real_estate_agency", 40);
	    workersPerTypeAndArea.put("restaurant", 80);
	    workersPerTypeAndArea.put("school", 10);
	    workersPerTypeAndArea.put("secondary_school", 10);
	    workersPerTypeAndArea.put("secondary_school+primary_school", 8);
	    workersPerTypeAndArea.put("shoe_store", 50);
	    workersPerTypeAndArea.put("shoe_store+clothing_store", 40);
	    workersPerTypeAndArea.put("shopping_mall", 100);
	    workersPerTypeAndArea.put("spa", 40);
	    workersPerTypeAndArea.put("storage", 50);
	    workersPerTypeAndArea.put("store", 40);
	    //workersPerTypeAndArea.put("supermarket", 150);
	    workersPerTypeAndArea.put("supermarket+department_store", 200);
	    workersPerTypeAndArea.put("travel_agency", 30);
	    workersPerTypeAndArea.put("university", 12);
	    workersPerTypeAndArea.put("veterinary_care", 60);
	    
	    workersPerTypeAndArea.put("corporate_office", 20);
	    workersPerTypeAndArea.put("government_office", 15);
	    workersPerTypeAndArea.put("building_materials_supplier", 100);
	    workersPerTypeAndArea.put("medical_office", 70);
	    workersPerTypeAndArea.put("public_medical_center", 50);
	    workersPerTypeAndArea.put("sport_club", 100);
	    workersPerTypeAndArea.put("sports_complex", 200);
	    workersPerTypeAndArea.put("grocery_or_supermarket", 80);
	    workersPerTypeAndArea.put("supermarket+grocery_or_supermarket", 150);
	    workersPerTypeAndArea.put("electronics_store+home_goods_store", 40);
	}
	
	public WorkplaceAgent(Geometry geo, long id, long blockid, String type, int area, int coveredArea, String workplaceType) {
		super(geo, id, blockid, type, area, coveredArea);
		
		int workersAmount = workersPerType.getOrDefault(workplaceType, -1);
		if (workersAmount == -1) {
			workersAmount = (getNumberOfSpots() / workersPerTypeAndArea.get(workplaceType))+1;
		}
		
		setWorkers(workersAmount);
		createWorkPositions();
	}
	
	/**
	 * Crea las posiciones de trabajo fijas segun la cantidad establecida. 
	 */
	private void createWorkPositions() {
		int y = getHeight();
		workPositions = new int[getNumberOfSpots()][2];
		
		int row = 0;
		int col = 0;
		for (int i = 0; i < workPositions.length; i++) {
			workPositions[i][0] = row;
			workPositions[i][1] = col;
			// TODO ver si no es mejor crear los puestos mas separados, se podria poner como parametro (0, 1, 2 o mas metros)
			if (++col == y) {
				col = 0;
				++row;
			}
		}
		workPositionsCount = workPositions.length;
	}
	
	/**
	 * Selecciona al azar una posicion de la lista de puestos, para el trabajador.
	 * @return {x, y}
	 */
	public int[] getWorkPosition() {
		int randomIndex = RandomHelper.nextIntFromTo(0, workPositionsCount-1);
		int[] pos = workPositions[randomIndex];
		workPositions[randomIndex] = workPositions[--workPositionsCount];
		return pos;
	}

	public int getWorkers() {
		return workers;
	}

	public void setWorkers(int workers) {
		this.workers = workers;
	}
	
	public void decreaseWorkers() {
		--this.workers;
	}
}