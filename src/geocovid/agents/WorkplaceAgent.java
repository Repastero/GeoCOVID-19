package geocovid.agents;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.contexts.SubContext;
import repast.simphony.random.RandomHelper;

/**
 * Representa las parcelas donde los Humanos trabajan o estudian.
 */
public class WorkplaceAgent extends BuildingAgent {
	protected int[][] workPositions;
	protected int workPositionsCount;
	protected String workplaceType;
	protected int activityType;
	/** Cantidad maxima de trabajadores en lugar de trabajo */
	protected int vacancies = 4;
	/** Capacidad maxima (metros util * humanos por metro cuadrado) */
	private int maximumCapacity;
	protected boolean closed = false;
	/** Lista de HumanAgent pre-trasmisores (sintomaticos) */
	protected List<HumanAgent> preSpreadersList = new ArrayList<HumanAgent>();
	
	public WorkplaceAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, int activityType, int area, int coveredArea, int workersPlace, int workersArea) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, area, (area * coveredArea) / 100, DataSet.WORKPLACE_AVAILABLE_AREA, true);
		
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

	public WorkplaceAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, int activityType, int width, int lenght, int workersPlace) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, width, lenght, true);
		
		this.workplaceType = workType;
		this.activityType = activityType;
		if (workersPlace > 0)
			this.vacancies = workersPlace;
		createWorkPositions();
	}
	
	public WorkplaceAgent(WorkplaceAgent wpa) {
		super(wpa);
		
		this.workplaceType = wpa.workplaceType;
		this.activityType = wpa.activityType;
		this.vacancies = wpa.vacancies;
		createWorkPositions();
	}

	/**
	 * Calcula y setea la capacidad total del place.
	 * @param sqMetersPerHuman metros cuadrados por persona
	 */
	public void limitCapacity(double sqMetersPerHuman) {
		int cap;
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
		else {
			cap = maximumCapacity;
		}
		
		// Que no supere la capacidad por defecto
		if (cap <= maximumCapacity)
			setCapacity(cap);
	}
	
	/**
	 * Crea las posiciones de trabajo fijas segun la cantidad establecida. 
	 */
	public void createWorkPositions() {
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
							// Si hace 2 pasadas y aun faltan puestos, reduce el total
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
		
		maximumCapacity = getCapacity();
		// Por defecto la capacidad maxima es de 1 humano por metro cuadrado
		limitCapacity(1d);
	}
	
	/**
	 * Selecciona al azar una posicion de la lista de puestos, para el trabajador.
	 * @return posicion {x, y}
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
	
	@Override
	protected void lookForCloseContacts(HumanAgent human, int[] pos) {
		// Si es un lugar de trabajo y existen pre contagiosos, se chequean contactos estrechos
		if (!preSpreadersList.isEmpty()) {
			if (human.isPreInfectious()) {
				spreadVirus(human, pos);
				removePreSpreader(human, pos); // No puede ser de ambos tipos de spreader
			}
			else if (!human.wasExposed()) // Los ya expuestos estan exeptuados
				findNearbySpreaders(human, pos, preSpreadersList);
		}
	}
	
	@Override
	protected boolean checkCloseContact(HumanAgent preSpreader, HumanAgent prey) {
		// Si los dos estan trabajando, se aisla el susceptible
		if (prey.atWork()) {
			prey.setCloseContact(preSpreader.getInfectiousStartTime());
			return true;
		}
		return false;
	}
	
	@Override
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey, double infectionRate) {
		// No hay contagio entre pre-infectados y susceptibles
		if (spreader.isPreInfectious()) {
			return super.checkContagion(spreader, prey, 0);
		}
		// Chequea si respetan distanciamiento social
		if (context.getSDPercentage() != 0) {
			// Si es un lugar cerrado o se respeta el distanciamiento en exteriores
			if (!isOutdoor() || context.sDOutdoor()) {
				// Si los dos humanos respetan el distanciamiento
				if (spreader.distanced && prey.distanced) {
					// Si se respeta el distanciamiento en lugares de trabajo
					if (!(spreader.atWork() && prey.atWork()) || context.sDWorkspace()) {
						return super.checkContagion(spreader, prey, 0);
					}
				}
			}
		}
		// Chequea efectividad de cubrebocas
		if (context.getMaskEffectivity() > 0) {
			// Si es un lugar cerrado o se usa cubreboca en exteriores
			if (!isOutdoor() || context.wearMaskOutdoor()) {
				// Si el contagio es entre cliente-cliente/empleado-cliente o entre empleados
				final boolean workers = (spreader.atWork() && prey.atWork());
				if (workers) {
					// Chequea si utilizan cubreboca entre empleados del tipo de actividad de parcela
					if ((activityType == 1 && context.wearMaskWorkspace()) || (activityType > 1 && context.wearMaskCS()))
						infectionRate *= 1 - context.getMaskEffectivity();
				}
				// Chequea si utilizan cubreboca entre clientes y empleados
				else if (context.wearMaskCustomer()) {
					infectionRate *= 1 - context.getMaskEffectivity();
				}
			}
		}
		return super.checkContagion(spreader, prey, infectionRate);
	}
	
	/**
	 * Se agregan recien llegados y los que cambiaron de estado luego de ingresar al building.
	 * @param human HumanAgent pre contagioso
	 */
	public void addPreSpreader(HumanAgent human) {
		preSpreadersList.add(human);
	}
	
	/**
	 * Se remueven pre contagiosos.
	 * @param human HumanAgent que sale del building o cambia de estado
	 * @param pos {x, y} en parcela
	 */
	public void removePreSpreader(HumanAgent human, int[] pos) {
		preSpreadersList.remove(human);
	}
	
	public int getWorkPositionsCount() {
		return workPositionsCount;
	}
	
	public int getMaximumCapacity() {
		return maximumCapacity;
	}
	
	public void setMaximumCapacity(int maximumCapacity) {
		if (getCapacity() > maximumCapacity)
			setCapacity(maximumCapacity);
		this.maximumCapacity = maximumCapacity;
	}
}
