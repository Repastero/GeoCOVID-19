package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import repast.simphony.random.RandomHelper;

public class WorkplaceAgent extends BuildingAgent {
	private int[][] workPositions;
	private int workPositionsCount;
	private String workplaceType;
	private int activityType;
	/** Cantidad maxima de trabajadores en lugar de trabajo */
	private int vacancies = 4;
	private int defaultCapacity;
	private boolean closed = false;
	
	public WorkplaceAgent(int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, int activityType, int area, int coveredArea, int workersPlace, int workersArea) {
		super(sectoralType, sectoralIndex, coord, id, workType, area, (area * coveredArea) / 100, DataSet.WORKPLACE_AVAILABLE_AREA, true);
		
		this.workplaceType = workType;
		this.activityType = activityType;
		if (workersPlace > 0)
			this.vacancies = workersPlace;
		else if (workersArea > 0)
			this.vacancies = (getNumberOfSpots() / workersArea)+1;
		else
			System.err.println("Sin cupo de trabajadores de Workplace: " + workplaceType);
		createWorkPositions();
	}
	
	public void limitCapacity(double sqMetersPerHuman) {
		int cap = defaultCapacity;
		// Para calcular el maximo aforo teorico de un local comercial:
		// dividir la superficie util transitable entre 4
		if (sqMetersPerHuman > 0d) {
			cap = (int) (getRealArea() / (DataSet.HUMANS_PER_SQUARE_METER * sqMetersPerHuman));
			if (isOutdoor()) // si es al aire libre, la capacidad se dobla
				cap <<= 1;
			if (activityType == 2) // si es un lugar de ocio, se incrementa la capacidad
				cap *= DataSet.ENTERTAINMENT_CAP_LIMIT_MOD;
			if (cap <= workPositionsCount) {
				cap = workPositionsCount + 1; // permitir al menos un cliente
			}
		}
		if (cap <= defaultCapacity)
			setCapacity(cap);
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
		/* Prueba sin cuarentena
		if (workplaceType.contains("primary_school") || workplaceType.contains("secondary_school"))
			distance -= 2;
		*/
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
			if (row + 2 < y)
				++row;
			setStartingRow(row);
		}
		defaultCapacity = getCapacity();
		
		// Por defecto la capacidad maxima es de 1 humano por metro cuadrado
		setCapacity(defaultCapacity / DataSet.HUMANS_PER_SQUARE_METER);
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
	
	public int getVacancy() {
		return vacancies;
	}
	
	public boolean vacancyAvailable() {
		return (vacancies > 0);
	}
	
	public void reduceVacancies() {
		--this.vacancies;
	}
	
	public void close() {
		closed = true;
	}
	
	public void open() {
		closed = false;
	}
	
	@Override
	public int[] insertHuman(HumanAgent human, int[] pos) {
		// Prevenir que ingresen cuando esta cerrado
		if (closed)
			return null;
		return super.insertHuman(human, pos);
	}
}