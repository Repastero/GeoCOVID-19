package geocovid.agents;

import java.util.HashMap;
import java.util.Map;

import com.vividsolutions.jts.geom.Geometry;

import geocovid.DataSet;
import repast.simphony.random.RandomHelper;

public class WorkplaceAgent extends BuildingAgent {
	//private List<int[]> workersPosition = new ArrayList<int[]>();
	private int[][] workPositions;
	private int workPositionsCount;
	private String workplaceType;
	/** Cantidad maxima de trabajadores en lugar de trabajo */
	private int vacancies = 4;
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
		workersPerType.put("book_store", 3);
		workersPerType.put("car_dealer", 10);
		workersPerType.put("cemetery", 4);
		workersPerType.put("church", 6);
		workersPerType.put("funeral_home", 6);
		workersPerType.put("gym", 6);
		workersPerType.put("laundry", 3);
		workersPerType.put("library", 3);
		workersPerType.put("meal_takeaway", 8);
		workersPerType.put("movie_rental", 3);
		workersPerType.put("moving_company", 6);
		workersPerType.put("museum", 6);
		workersPerType.put("parking", 3);
		workersPerType.put("stadium", 10);
		workersPerType.put("synagogue", 4);
		
		workersPerType.put("park", 2);
		workersPerType.put("ice_cream_shop", 4);
		workersPerType.put("optician", 4);
		workersPerType.put("photographer", 2);
		workersPerType.put("computer_accessories_store", 4);
		
	    //
	    workersPerTypeAndArea = new HashMap<>();
	    workersPerTypeAndArea.put("accounting", 20);
	    workersPerTypeAndArea.put("accounting+local_government_office", 10);
	    workersPerTypeAndArea.put("bakery", 30);
	    workersPerTypeAndArea.put("bakery+cafe", 40);
	    workersPerTypeAndArea.put("bank", 40);
	    workersPerTypeAndArea.put("bar", 30);
	    workersPerTypeAndArea.put("bar+liquor_store", 50);
	    workersPerTypeAndArea.put("beauty_salon", 20);
	    workersPerTypeAndArea.put("beauty_salon+spa", 40);
	    workersPerTypeAndArea.put("bicycle_store", 40);
	    workersPerTypeAndArea.put("bus_station", 60);
	    workersPerTypeAndArea.put("cafe", 40);
	    workersPerTypeAndArea.put("car_repair", 70);
	    workersPerTypeAndArea.put("car_repair+store", 40);
	    workersPerTypeAndArea.put("car_wash", 40);
	    workersPerTypeAndArea.put("casino", 50);
	    workersPerTypeAndArea.put("clothing_store", 30);
	    workersPerTypeAndArea.put("convenience_store", 40);
	    workersPerTypeAndArea.put("courthouse", 40);
	    workersPerTypeAndArea.put("dentist", 60);
	    workersPerTypeAndArea.put("doctor", 60);
	    workersPerTypeAndArea.put("drugstore", 20);
	    workersPerTypeAndArea.put("electronics_store", 40);
	    workersPerTypeAndArea.put("fire_station", 30);
	    workersPerTypeAndArea.put("florist", 40);
	    workersPerTypeAndArea.put("furniture_store", 50);
	    workersPerTypeAndArea.put("gas_station", 30);
	    workersPerTypeAndArea.put("hair_care", 20);
	    workersPerTypeAndArea.put("hardware_store", 40);
	    workersPerTypeAndArea.put("home_goods_store", 40);
	    workersPerTypeAndArea.put("hospital", 60);
	    workersPerTypeAndArea.put("insurance_agency", 15);
	    workersPerTypeAndArea.put("jewelry_store", 20);
	    workersPerTypeAndArea.put("lawyer", 15);
	    workersPerTypeAndArea.put("liquor_store", 40);
	    workersPerTypeAndArea.put("local_government_office", 10);
	    workersPerTypeAndArea.put("locksmith", 30);
	    workersPerTypeAndArea.put("lodging", 80);
	    workersPerTypeAndArea.put("meal_delivery", 30);
	    workersPerTypeAndArea.put("movie_theater", 70);
	    workersPerTypeAndArea.put("night_club", 80);
	    workersPerTypeAndArea.put("pet_store", 30);
	    workersPerTypeAndArea.put("pharmacy", 30);
	    workersPerTypeAndArea.put("pharmacy+veterinary_care", 30);
	    workersPerTypeAndArea.put("physiotherapist", 40);
	    workersPerTypeAndArea.put("police", 40);
	    workersPerTypeAndArea.put("post_office", 20);
	    workersPerTypeAndArea.put("primary_school", 8);
	    workersPerTypeAndArea.put("primary_school+secondary_school", 8);
	    workersPerTypeAndArea.put("real_estate_agency", 30);
	    workersPerTypeAndArea.put("restaurant", 30);
	    workersPerTypeAndArea.put("school", 8);
	    workersPerTypeAndArea.put("secondary_school", 8);
	    workersPerTypeAndArea.put("secondary_school+primary_school", 8);
	    workersPerTypeAndArea.put("shoe_store", 30);
	    workersPerTypeAndArea.put("shoe_store+clothing_store", 30);
	    workersPerTypeAndArea.put("shopping_mall", 80);
	    workersPerTypeAndArea.put("spa", 40);
	    workersPerTypeAndArea.put("storage", 50);
	    workersPerTypeAndArea.put("store", 30);
	    //workersPerTypeAndArea.put("supermarket", 150);
	    workersPerTypeAndArea.put("supermarket+department_store", 120);
	    workersPerTypeAndArea.put("travel_agency", 20);
	    workersPerTypeAndArea.put("university", 12);
	    workersPerTypeAndArea.put("veterinary_care", 40);
	    
	    workersPerTypeAndArea.put("corporate_office", 15);
	    workersPerTypeAndArea.put("government_office", 10);
	    workersPerTypeAndArea.put("building_materials_supplier", 80);
	    workersPerTypeAndArea.put("medical_office", 40);
	    workersPerTypeAndArea.put("public_medical_center", 25);
	    workersPerTypeAndArea.put("sport_club", 60);
	    workersPerTypeAndArea.put("sports_complex", 120);
	    workersPerTypeAndArea.put("grocery_or_supermarket", 40);
	    workersPerTypeAndArea.put("supermarket+grocery_or_supermarket", 40);
	    workersPerTypeAndArea.put("electronics_store+home_goods_store", 30);
	}
	
	public WorkplaceAgent(Geometry geo, long id, long blockid, String type, int area, int coveredArea, String workType) {
		super(geo, id, blockid, type, area, coveredArea, DataSet.WORKPLACE_AVAILABLE_AREA);
		
		this.workplaceType = workType;
		if (workersPerType.containsKey(workplaceType))
			this.vacancies = workersPerType.get(workplaceType);
		else if (workersPerTypeAndArea.containsKey(workplaceType))
			this.vacancies = (getNumberOfSpots() / workersPerTypeAndArea.get(workplaceType))+1;
		else
			System.err.println("Sin cupo de trabajadores de Workplace: " + workplaceType);
		createWorkPositions();
	}
	
	/**
	 * Crea las posiciones de trabajo fijas segun la cantidad establecida. 
	 */
	private void createWorkPositions() {
		int x = getWidth();
		int y = getHeight();
		workPositions = new int[vacancies][2];
		workPositionsCount = workPositions.length;
		int distance = DataSet.SPACE_BETWEEN_WORKERS;
		int col = 0;
		int row = 0;
		boolean fullBuilding = false; // flag para saber si se utiliza todo el rango de col, row
		for (int i = 0; i < workPositionsCount; i++) {
			workPositions[i][0] = col;
			workPositions[i][1] = row;
			col += distance;
			if (col >= x) {
				col = distance;
				row += distance;
				if (row >= y) {
					if (i+1 < workPositionsCount) {
						// Si faltan crear puestos 
						if (fullBuilding) {
							// Si hace 2 pasadas de la grilla y aun faltan puestos, reduce el total
							System.out.format("Cupos de trabajo limitados a %d de %d en tipo: %s id: %d%n",
									i+1, workPositionsCount, workplaceType, getId());
							workPositionsCount = i+1;
							vacancies = workPositionsCount;
							break;
						}
						// Si es la primer pasada, vuelve al principio + offset
						System.out.format("Falta espacio para %d cupos de trabajo en tipo: %s id: %d - Inicia segunda pasada%n",
								workPositionsCount-(i+1), workplaceType, getId());
						col = (distance > 1 ? distance >> 1 : 1);
						row = col;
					}
					fullBuilding = true;
				}
			}
		}
		// Separar los trabajadores de los clientes, si quedan filas disponibles
		if (!fullBuilding) {
			if (row == 0) row = 1;
			setStartingRow(row);
		}
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
	
	public boolean vacancyAvailable() {
		return (vacancies > 0);
	}
	
	public void reduceVacancies() {
		--this.vacancies;
	}
}