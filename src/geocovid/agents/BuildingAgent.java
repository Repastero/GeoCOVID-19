package geocovid.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.Temperature;
import geocovid.contexts.SubContext;
import repast.simphony.random.RandomHelper;

/**
 * Clase base de parcelas.
 */
public class BuildingAgent {
	private SubContext context;	// Puntero
	private int sectoralType;	// Indice de tipo de seccional
	private int sectoralIndex;	// Indice de seccional
	// Atributos del GIS
	private Coordinate coordinate;
	private long id;
	private String type;
	private int area;
	private int coveredArea;
	private int realArea;
	private boolean workingPlace = false;
	// Atributos de la grilla
	private int capacity;
	private int startingRow = 0; // indice de fila inicial para no trabajadores 
	private int size[] = {0,0}; // ancho x largo
	private boolean outdoor;
	// Atributos staticos para checkear el radio de infeccion
	private static int infCircleRadius;
	private static int xShiftsInf[];
	private static int yShiftsInf[];
	// Atributos staticos para checkear el radio de contactos personales
	private static int xShiftsPD[];
	private static int yShiftsPD[];
	//
	/** Lista de HumanAgent trasmisores */
	private List<HumanAgent> spreadersList = new ArrayList<HumanAgent>();
	/** Lista de HumanAgent pre-trasmisores (sintomaticos) */
	private List<HumanAgent> preSpreadersList = new ArrayList<HumanAgent>();
	/** Mapa de HumanAgent dentro de parcela <Id Humano, HumanAgent> */
	private Map<Integer, HumanAgent> humansMap = new HashMap<>();
	/** Mapa de SurfaceAgent dentro de parcela <Id Superficie, SurfaceAgent> */
	private Map<Integer, SurfaceAgent> surfacesMap = new HashMap<>();
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, long id, String type, int area, int coveredArea) {
		this.context = subContext;
		this.sectoralType = secType;
		this.sectoralIndex = secIndex;
		this.coordinate = coord;
		this.id = id;
		this.type = type;
		this.area = area;
		this.coveredArea = coveredArea;
	}
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, long id, String type, int area, int coveredArea, double areaModifier, boolean workplace) {
		// Constructor Home/Workplace
		this(subContext, secType, secIndex, coord, id, type, area, coveredArea);
		//
		this.workingPlace = workplace;
		setRealArea(areaModifier);
		setBuildingShape();
	}
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, int realArea, boolean outdoor) {
		// Constructor Evento
		this(subContext, secType, secIndex, coord, 0xFFFFFFFF, "event", 0, 0);
		if (outdoor)
			this.area = realArea;
		else
			this.coveredArea = realArea;
		//
		this.outdoor = outdoor;
		setBuildingShape();
	}
	
	public BuildingAgent(BuildingAgent ba) {
		// Constructor para crear copia de ba
		this(ba.context, ba.sectoralType, ba.sectoralIndex, ba.coordinate, ba.id, ba.type, ba.area, ba.coveredArea);
		//
		this.realArea = ba.realArea;
		setBuildingShape();
	}
	
	/**
	 * Crea un circulo de acuerdo a el radio de infeccion y de distancia personal<p>
	 * y guarda los desplazamientos de X e Y a partir del centro
	 * @see DataSet#INFECTION_RADIUS
	 * @see DataSet#PERSONAL_DISTANCE
	 */
	public static void initInfAndPDRadius() {
		final int[] shiftsCount = {8,20,36,56,80,128,164,204,248,296,348,444,508,576,648,724,804,948,1040,1136};
		// Chequeo que radio este dentro del rango
		int radius = DataSet.INFECTION_RADIUS;
		if (radius < 1 || radius > 20)
			radius = 4;
		xShiftsInf = new int[shiftsCount[radius-1]];
		yShiftsInf = new int[shiftsCount[radius-1]];
		infCircleRadius	= getCircleRadius(radius, xShiftsInf, yShiftsInf);
		// Chequeo que radio este dentro del rango
		radius = DataSet.PERSONAL_DISTANCE;
		if (radius < 1 || radius > 20)
			radius = 4;
		xShiftsPD = new int[shiftsCount[radius-1]];
		yShiftsPD = new int[shiftsCount[radius-1]];
		getCircleRadius(radius, xShiftsPD, yShiftsPD);
	}
	
	/**
	 * Carga los valores de desplazamientos de X e Y, segun el radio dado.
	 * @param radius radio
	 * @param xs desvios de x
	 * @param ys desvios de y
	 * @return <b>int</b> radio del circulo
	 */
	public static int getCircleRadius(int radius, int[] xs, int[] ys) {
		// Desvio maximo de X + Y
		int circleRadius = radius + (radius / 6) + 1;
		int absX;
		int indxy = 0;
		for (int x = -radius; x <= radius; x++) {
			absX = Math.abs(x);
			for (int y = -radius; y <= radius; y++) {
				if (x == 0 && y == 0) // Posicion centro, la del infectado
					continue;
				if (absX + Math.abs(y) <= circleRadius) {
					xs[indxy] = x;
					ys[indxy] = y;
					++indxy;
				}
			}
		}
		return circleRadius;
	}
	
	/**
	 * Setea el area real de parcela, segun area cubierta y area ocupable
	 * @param availableAreaMod
	 */
	private void setRealArea(double availableAreaMod) {
		// Si es espacio verde tomo toda el area
		if (coveredArea > 0) {
			realArea = coveredArea;
			outdoor = false;
		}
		else {
			realArea = area;
			outdoor = true;
		}
		// Se resta un porcentaje del area para paredes, muebles, etc.
		realArea *= availableAreaMod;
	}
	
	/**
	 * Crea la grilla de posiciones, segun forma y area
	 */
	private void setBuildingShape() {
		// Se multiplica el area por la cantidad de Humanos por m2
		final int humansCap = realArea * DataSet.HUMANS_PER_SQUARE_METER;
		
		// Si es solo un punto, tomar la superficie como un cuadrado
		// Busca el numero de X mas alto que sea factor de realArea
		for (int i = 1; i * i <= humansCap; i++) {
            if (humansCap % i == 0) {
            	size[0] = i;
    	        size[1] = humansCap / i;
            }
		}
		
		// Si Y es mas del doble que X, uso calculo viejo
		if (size[0] < size[1] >> 1) {
			size[1] = (int)Math.sqrt(humansCap);
			size[0] = size[1] + 1;
		}
		
		capacity = size[0]*size[1];
	}
	
	/**
	 * Asigna un lugar en la grilla para el nuevo ingreso, si hay capacidad
	 * @param human HumanAgent
	 * @return posicion {x, y} o null
	 */
	public int[] insertHuman(HumanAgent human) {
		if (humansMap.size() >= capacity) { 
			//-System.out.println("Building full - ID: "+getId()+" type: "+getType());
			return null;
		}
		
		int x, y;
		do {
			x = RandomHelper.nextIntFromTo(0, size[0]-1);
			y = RandomHelper.nextIntFromTo(startingRow, size[1]-1);
		} while (humansMap.containsKey(getPosId(x, y)));
		
		int[] humanPos = {x,y};
		return insertHuman(human, humanPos);
	}
	
	/**
	 * Inserta el humano en la posicion de la grilla dada y en humansMap.
	 * @param human HumanAgent
	 * @param pos nueva posicion
	 * @return posicion {x, y} en grilla
	 */
	public int[] insertHuman(HumanAgent human, int[] pos) {
		if (humansMap.put(getPosId(pos), human) != null)
			return null; // Lugar ocupado
		if (human.isContagious())
			addSpreader(human);
		return pos;
	}
	
	/**
	 * Remueve al humano de la grilla y de las listas
	 * @param human HumanAgent
	 * @param pos {x, y} en grilla
	 */
	public void removeHuman(HumanAgent human, int[] pos) {
		// Si quedo afuera, no se continua
		if (pos == null)
			return;
		int posId = getPosId(pos);
		// Busca contactos cercanos
		if (DataSet.COUNT_INTERACTIONS)
			sociallyInteract(human, pos);
		// Si es susceptible se busca si pudo contagiarse
		if (!human.wasExposed()) {
			// Primero busca fuentes de contagio directo
			if (!spreadersList.isEmpty())
				findNearbySpreaders(human, pos, spreadersList);
			// Si se contagia directamente, no checkea estela
			if (!human.wasExposed() && !surfacesMap.isEmpty())
				checkIfSurfaceContaminated(human, posId);
		}
		// Si es un lugar de trabajo y existen pre contagiosos, se chequean contactos estrechos
		if (workingPlace && !preSpreadersList.isEmpty()) {
			if (human.isPreInfectious()) {
				spreadVirus(human, pos);
				removePreSpreader(human, pos); // No puede ser de ambos tipos de spreader
			}
			else if (!human.wasExposed()) // Los ya expuestos estan exeptuados
				findNearbySpreaders(human, pos, preSpreadersList);
		}
		// Si es contagioso se buscan contactos cercanos susceptibles
		if (human.isContagious()) {
			if ((humansMap.size() - spreadersList.size()) != 0) // no hay susceptibles
				spreadVirus(human, pos);
			// 
			removeSpreader(human, pos);
		}
		//
		if (!humansMap.remove(posId, human))
			System.err.println("Humano no encontrado en Building "+getId()+" - Type: "+ type +" - Hid: "+human.getAgentID());
	}
	
	/**
	 * Lo mismo que <b>spreadVirus</b> pero no discrimina covidosos.
	 * @param agent humano
	 * @param agentPos posicion
	 */
	public void sociallyInteract(HumanAgent agent, int[] agentPos) {
		int spId = agent.getAgentID();
		HumanAgent contact;
		int posX, posY;
		for (int i = 0; i < xShiftsPD.length; i++) {
			posX = agentPos[0] + xShiftsPD[i];
			posY = agentPos[1] + yShiftsPD[i];
	    	if (posX >= 0 && posY >= 0) { // no de un valor negativo
	    		if (posX < size[0] && posY < size[1]) { // no se salga de la grilla
		    		contact = humansMap.get(getPosId(posX, posY));
			    	if (contact != null) { // Si hay humano
			    		if (Math.abs(agent.getRelocationTime() - contact.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
				    		agent.addSocialInteraction(contact.getAgentID());
				    		contact.addSocialInteraction(spId);
			    		}
			    	}
		    	}
	    	}
		}
	}
	
	/**
	 * Se agregan recien llegados y los que cambiaron de estado luego de ingresar al building.
	 * @param human HumanAgent contagioso
	 */
	public void addSpreader(HumanAgent human) {
		spreadersList.add(human);
	}
	
	/**
	 * Se remueven contagiosos y se crea o actualiza la estela que dejan en el lugar.<p>
	 * Si cambia de estado estando en el building, se asume que la carga viral es minima y no contagia directamente.
	 * @param human HumanAgent que sale del building o se recupera
	 * @param pos {x, y} en grilla
	 */
	public void removeSpreader(HumanAgent human, int[] pos) {
		// Si quedo afuera, no se continua
		if (pos == null)
			return;
		spreadersList.remove(human);
		// Se crea la estela cuando el contagioso sale de la parcela
		int csId = getPosId(pos);
		SurfaceAgent surface = surfacesMap.get(csId);
		if (surface == null) {
			// Se crea una superficie con la posicion como ID
			surfacesMap.put(csId, new SurfaceAgent(context.getTownRegion(), outdoor));
		}
		else {
			// Si la superficie ya estaba contaminada, se 'renueva' el virus
			surface.updateLifespan();
		}
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
	 * @param pos {x, y} en grilla
	 */
	public void removePreSpreader(HumanAgent human, int[] pos) {
		preSpreadersList.remove(human);
	}
	
	/**
	 * Chequea todos los modificadores/escenarios para verificar si hay contagio.
	 * @param spreader
	 * @param prey
	 * @return <b>true</b> si hubo contagio
	 */
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey) {
		double infectionRate = Temperature.getInfectionRate(context.getTownRegion(), outdoor, sectoralType);
		if (Math.abs(spreader.getRelocationTime() - prey.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
			// Si es un lugar de trabajo se chequea si respetan distanciamiento y usan cubreboca
			if (workingPlace) {
				if (context.getSDPercentage() != 0) {
					// Si es un lugar cerrado o se respeta el distanciamiento en exteriores
					if ((!outdoor) || context.sDOutdoor()) {
						// Si los dos humanos respetan el distanciamiento
						if (spreader.distanced && prey.distanced) {
							// Si se respeta el distanciamiento en lugares de trabajo
							if (!(spreader.atWork() && prey.atWork()) || context.sDWorkspace())
								return false;
						}
					}
				}
				// Si esta en la etapa donde puede crear casos de contactos cercanos
				if (spreader.isPreInfectious()) {
					if (spreader.atWork() && prey.atWork()) {
						// Si no usan cubrebocas en el lugar de trabajo, existe contacto estrecho
						if (!context.wearMaskWorkspace()) {
							prey.setCloseContact(spreader.getInfectiousStartTime());
							return true;
						}
						return false;
					}
				}
				else if (context.getMaskEffectivity() > 0) {
					// Si es un lugar cerrado o se usa cubreboca en exteriores
					if ((!outdoor) || context.wearMaskOutdoor()) {
						// Si el contagio es entre cliente-cliente/empleado-cliente o entre empleados que usan cubreboca
						if (!(spreader.atWork() && prey.atWork()) || context.wearMaskWorkspace()) {
							infectionRate *= 1 - context.getMaskEffectivity();
						}
					}
				}
			}
			else if (spreader.quarantined) {
				// Si esta aislado, se supone tiene todas las precauciones para no contagiar
				infectionRate *= 1 - DataSet.ISOLATION_INFECTION_RATE_REDUCTION;
			}
			if (RandomHelper.nextDoubleFromTo(0d, 100d) <= infectionRate) {
				prey.setExposed();
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Esparce el virus a los humanos susceptibles con los que tuvo contacto cercano y prolongado.
	 * @param spHuman HumanAgent contagioso
	 * @param spPos posicion en grilla de spHuman
	 */
	private void spreadVirus(HumanAgent spHuman, int[] spPos) {
		HumanAgent prey;
		// Recorre las posiciones de la grilla al rededor del infectado, buscando nuevos huespedes
		int posX, posY;
		for (int i = 0; i < xShiftsInf.length; i++) {
			posX = spPos[0] + xShiftsInf[i];
			posY = spPos[1] + yShiftsInf[i];
	    	if (posX >= 0 && posY >= 0) { // no de un valor negativo
	    		if (posX < size[0] && posY < size[1]) { // no se salga de la grilla
	    			prey = humansMap.get(getPosId(posX, posY));
			    	if (prey != null) { // Si hay humano
						if (!prey.wasExposed()) {
							checkContagion(spHuman, prey);
			    		}
			    	}
				}
			}
		}
	}
    
	/**
	 * Busca en la lista de trasmisores los que tuvo contacto cercano y prolongado, y chequea contagio.
	 * @param human HumanAgent susceptible
	 * @param pos posicion en grilla de human
	 * @param spreaders lista de HumanAgent contagiosos
	 */
	private void findNearbySpreaders(HumanAgent human, int[] pos, List<HumanAgent> spreaders) {
		int[] spPos = new int[2];
		int xShift, yShift;
		for (HumanAgent spreader : spreaders) {
			spPos = spreader.getCurrentPosition();
			// Fix temporal al raro bug del spreader fantasma 
			if (spPos == null) {
				if (spreaders == spreadersList)
					System.err.println("spreadersList fantasma!");
				else
					System.err.println("preSpreadersList fantasma!");
				// saco el fantasma de la lista para que no moleste mas
				spreaders.remove(spreader);
				continue;
			}
			//
			xShift = Math.abs(pos[0] - spPos[0]);
			yShift = Math.abs(pos[1] - spPos[1]);
			if (xShift < infCircleRadius && yShift < infCircleRadius) { // que X o Y individualmente no exedan el radio de contagio
				if (xShift + yShift <= infCircleRadius) { // que la suma del desplazamiento no exeda el radio de contagio
					if (checkContagion(spreader, human)) // si se contagia, deja de buscar trasmisores
						break;
				}
			}
		}
	}
    
	/**
	 * Verifica si la superficie donde esta parado el Humano esta contaminada y checkea si el Humano se contagia.
	 * @param human HumanAgent susceptible
	 * @param pos posicion en grilla de human
	 */
	private void checkIfSurfaceContaminated(HumanAgent human, int csId) {
		SurfaceAgent surface = surfacesMap.get(csId);
		if (surface != null) {
			// Si en el ultimo checkeo la superficie seguia contaminada
			if (surface.isContaminated()) {
				if (RandomHelper.nextIntFromTo(1, 100) <= surface.getInfectionRate()) {
					human.setExposed();
					InfectionReport.addExposedToCS();
				}
			}
		// Es preferible no eliminar la superficie contaminada, para utilizar el objeto posteriormente
		}
	}
    
	/**
	 * El ID de superficie se calcula como: (Y * ANCHO) + X.
	 * @param pos {x, y} de superficie
	 * @return <b>int</b> id superficie
	 */
	private int getPosId(int[] pos) {
		return (pos[1]*size[0])+pos[0];
	}
	
	private int getPosId(int x, int y) {
		return (y*size[0])+x;
	}
    
	public int[] getSize() {
		return size;
	}

	public int getWidth() {
		return size[0];
	}
	
	public int getHeight() {
		return size[1];
	}
	
	public int getStartingRow() {
		return startingRow;
	}
	
	public void setStartingRow(int row) {
		startingRow = row;
		// Resto de capacidad la cantidad de ubicaciones que estan reservadas
		capacity -= startingRow * size[0];
	}
	
	public int getNumberOfSpots() {
		return coveredArea;
	}
	
	public int getSectoralType() {
		return sectoralType;
	}
	
	public int getSectoralIndex() {
		return sectoralIndex;
	}
	
	public Coordinate getCoordinate() {
		return coordinate;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public double getArea() {
		return area;
	}
	
	public void setArea(int area) {
		this.area = area;
	}
	
	public double getCoveredArea() {
		return coveredArea;
	}
	
	public void setCoveredArea(int coveredArea) {
		this.coveredArea = coveredArea;
	}
	
	public int getRealArea() {
		return realArea;
	}
	
	public int getHumansAmount() {
		return humansMap.size();
	}
	
	public int getCapacity() {
		return capacity;
	}
	
	public void setCapacity(int limit) {
		this.capacity = limit;
	}
	
	public boolean isOutdoor() {
		return outdoor;
	}
	
	public List<HumanAgent> getSpreadersList() {
		return spreadersList;
	}
	
	public void setSpreadersList(List<HumanAgent> spreadersList) {
		this.spreadersList = spreadersList;
	}
	
	public List<HumanAgent> getPreSpreadersList() {
		return preSpreadersList;
	}
	
	public void setPreSpreadersList(List<HumanAgent> preSpreadersList) {
		this.preSpreadersList = preSpreadersList;
	}
}
