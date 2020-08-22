package geocovid.agents;

import repast.simphony.random.RandomHelper;

import java.util.Set;

import com.vividsolutions.jts.geom.Geometry;

import geocovid.DataSet;

public class WorkplaceAgent extends BuildingAgent {
	// Listado de Places cerrados durante pandemia
	public static final Set<String> CLOSED_PLACES = Set.of("beauty_school", "bus_station", "church", "dance_school", "escape_room_center", "function_room_facility", "funeral_home", "language_school", "lodging", "nursery_school", "political_party", "primary_school", "secondary_school", "soccer_field", "sports_club", "sportswear_store", "synagogue", "travel_agency", "university");
	// Listado de Places a cielo abierto
	public static final Set<String> OPEN_AIR_PLACES = Set.of("gas_station","park","parking","soccer_field");
	
	private int[][] workPositions;
	private int workPositionsCount;
	private String workplaceType;
	/** Cantidad maxima de trabajadores en lugar de trabajo */
	private int vacancies = 4;
	
	public WorkplaceAgent(Geometry geo, long id, long blockid, String type, int area, int coveredArea, String workType, int workersPlace, int workersArea) {
		super(geo, id, blockid, type, area, coveredArea, DataSet.WORKPLACE_AVAILABLE_AREA);
		
		this.workplaceType = workType;
		if (workersPlace > 0)
			this.vacancies = workersPlace;
		else if (workersArea > 0)
			this.vacancies = (getNumberOfSpots() / workersArea)+1;
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
		
		// Para calcular el maximo aforo teorico de un local comercial:
		// dividir la superficie util transitable entre 4
		if (!OPEN_AIR_PLACES.contains(workplaceType)) { // si no es al aire libre
			int cap = (getRealArea() / (DataSet.HUMANS_PER_SQUARE_METER * DataSet.SQUARE_METERS_PER_HUMAN));
			if (cap <= workPositionsCount) {
				cap = workPositionsCount + 1; // permitir al menos un cliente
			}
			setCapacity(cap);
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