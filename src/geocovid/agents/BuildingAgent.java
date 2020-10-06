package geocovid.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import geocovid.DataSet;
import geocovid.InfectionReport;
import repast.simphony.random.RandomHelper;

public class BuildingAgent {
	// Atributos del GIS
	private Geometry geometry;
	private long id;
	private long blockId;
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
	// Listas de Agentes dentro de Building 
	private List<HumanAgent> spreadersList = new ArrayList<HumanAgent>(); // Lista de HumanAgent trasmisores
	private Map<Integer, HumanAgent> humansMap = new HashMap<>(); // Mapa <Id Humano, HumanAgent>
	private Map<Integer, SurfaceAgent> surfacesMap = new HashMap<>(); // Mapa <Id Superficie, SurfaceAgent>
	//
	public BuildingAgent(Geometry geo, long id, long blockId, String type, int area, int coveredArea) {
		this.geometry = geo;
		this.id = id;
		this.blockId = blockId;
		this.type = type;
		this.area = area;
		this.coveredArea = coveredArea;
		//
		setRealArea(DataSet.BUILDING_AVAILABLE_AREA);
		setBuildingShape();
	}
	
	public BuildingAgent(Geometry geo, long id, long blockId, String type, int area, int coveredArea, double areaModifier) {
		// Constructor Workplace
		this.geometry = geo;
		this.id = id;
		this.blockId = blockId;
		this.type = type;
		this.area = area;
		this.coveredArea = coveredArea;
		this.workingPlace = true;
		//
		setRealArea(areaModifier);
		setBuildingShape();
	}
	
	public BuildingAgent(BuildingAgent ba) {
		// Constructor para crear copia de ba
		this.geometry = ba.geometry;
		this.id = ba.id;
		this.blockId = ba.blockId;
		this.type = ba.type;
		this.area = ba.area;
		this.coveredArea = ba.coveredArea;
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
	 * Carga los valores de desplazamientos de X e Y, segun el radio, en los vectores
	 * @param radius
	 * @param xs
	 * @param ys
	 * @return
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
		if (geometry instanceof Point) {
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
		}
		else {
			/* Para simplificar las cosas convierto las geometrias Polygon a Point
			 * se tendria que probar si hay diferencia usando la forma real del building
			 * tambien habria que cambiar la geometria de construcciones Horizontales
			 */
			
			// Si es una forma, tomar medida mas chica como el ancho
			Envelope env = geometry.getEnvelopeInternal();
			int width = (int)(env.getWidth() * DataSet.DEGREE_PRECISION); 	// grados cartesianos a metros
			int height = (int)(env.getHeight() * DataSet.DEGREE_PRECISION);	// grados cartesianos a metros
			
			// Tomar medida mas chica como el frente de la propiedad
			size[0] = (width >= height) ? height : width;
			// El largo de la propiedad varia segun la sup cubierta
			size[1] = (int)(humansCap / size[0]);
			
			// Si el largo queda en cero o un valor muy chico, creo un cuadrado
			if ((size[1] == 0) || (size[0] / size[1] >= 10)) {
				size[1] = (int)Math.sqrt(humansCap);
				size[0] = size[1] + 1;
			}
			// Si el area queda muy chica, sumo 1 metro mas de largo y resto 1 de ancho
			else if ((size[0] * size[1]) < (humansCap - size[1])) {
				--size[0];
				++size[1];
			}
		}
		capacity = size[0]*size[1];
	}
	
	/**
	 * Asigna un lugar en la grilla para el nuevo ingreso
	 * @param human HumanAgent
	 * @return {x, y} o null
	 */
	public int[] insertHuman(HumanAgent human) {
		// TODO ver si usar fuerza bruta nomas, buscar un punto o armar array con posiciones libres
		if (humansMap.size() >= capacity) { // TODO ver esto no es tan preciso, si startingRow > 0, tendria que discriminar trabajadores 
			//-System.out.println("Building full - ID: "+getId()+" type: "+getType());
			// TODO ver que hacer con el humano si no hay lugar
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
	 * @return {x, y} en grilla
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
		sociallyInteract(human, pos);
		// Si es susceptible se busca si pudo contagiarse
		if (!human.wasExposed()) {
			// Primero busca fuentes de contagio directo
			if (!spreadersList.isEmpty())
				findNearbySpreaders(human, pos);
			// Si se contagia directamente, no checkea estela
			if (!human.wasExposed() && !surfacesMap.isEmpty())
				checkIfSurfaceContaminated(human, posId);
		}
		// Si es contagioso se buscan contactos cercanos susceptibles
		else if (human.isContagious()) {
			if ((humansMap.size() - spreadersList.size()) != 0)
				spreadVirus(human, pos);
			// 
			removeSpreader(human, pos);
		}
		//
		if (!humansMap.remove(posId, human))
			System.err.println("Humano no encontrado en Building "+getId()+" - Hid: "+human.getAgentID());
	}
	
	/**
	 * Lo mismo que <b>spreadVirus</b> pero no discrimina covidosos
	 * @param agent
	 * @param agentPos
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
			surfacesMap.put(csId, new SurfaceAgent(outdoor));
		}
		else {
			// Si la superficie ya estaba contaminada, se 'renueva' el virus
			surface.updateLifespan();
		}
	}
	
	private boolean checkContagion(HumanAgent spreader, HumanAgent prey) {
		int infectionRate = DataSet.INFECTION_RATE;
		if (Math.abs(spreader.getRelocationTime() - prey.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
			// Si es un lugar de trabajo se chequea si respetan distanciamiento y usan cubreboca
			if (workingPlace) {
				if (DataSet.getSDPercentage() != 0) {
					// Si es un lugar cerrado o se respeta el distanciamiento en exteriores
					if ((!outdoor) || DataSet.sDOutdoor()) {
						// Si los dos humanos respetan el distanciamiento
						if (spreader.distanced && prey.distanced) {
							// Si se respeta el distanciamiento en lugares de trabajo
							if (!(spreader.atWork() && prey.atWork()) || DataSet.sDWorkspace())
								return false;
						}
					}
				}
				if (DataSet.getMaskEffectivity() != 0) {
					// Si es un lugar cerrado o se usa cubreboca en exteriores
					if ((!outdoor) || DataSet.wearMaskOutdoor()) {
						// Si el contagio es entre cliente-cliente/empleado-cliente o entre empleados que usan cubreboca
						if (!(spreader.atWork() && prey.atWork()) || DataSet.wearMaskWorkspace()) {
							infectionRate -= (infectionRate * DataSet.getMaskEffectivity()) / 100;
						}
					}
				}
			}
			else if (spreader.quarantined) {
				// Si esta aislado, se supone tiene todas las precauciones para no contagiar
				infectionRate -= (infectionRate * DataSet.ISOLATION_INFECTION_RATE_REDUCTION) / 100;
			}
			
			if (RandomHelper.nextIntFromTo(1, 100) <= infectionRate) {
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
	 * @see DataSet#INFECTION_EXPOSURE_TIME
	 * @see DataSet#INFECTION_RATE
	 */
	private void spreadVirus(HumanAgent spHuman, int[] spPos) {
		HumanAgent prey;
		// Recorre las posiciones de la grilla al rededor del infectado, buscando nuevos huespedes
		// TODO ver si hay diferencia en rendimiento reccoriendo "humans" y midiendo la distancia
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
	 * Busca en la lista de trasmisores los que tuvo contacto cercano y prolongado, y se contagia.
	 * @param human HumanAgent susceptible
	 * @param pos posicion en grilla de human 
	 * @see DataSet#INFECTION_EXPOSURE_TIME
	 * @see DataSet#INFECTION_RATE
	 */
	private void findNearbySpreaders(HumanAgent human, int[] pos) {
		// TODO ver que es mas rapido -> calcular distancia con infectados o buscar en grid
		int[] spPos = new int[2];
		int xShift, yShift;
		for (HumanAgent spreader : spreadersList) {
			spPos = spreader.getCurrentPosition();
			xShift = Math.abs(pos[0] - spPos[0]);
			yShift = Math.abs(pos[1] - spPos[1]);
			if (xShift < infCircleRadius && yShift < infCircleRadius) { // que X o Y individualmente no exedan el radio de contagio
				if (xShift + yShift <= infCircleRadius) { // que la suma del desplazamiento no exeda el radio de contagio
					if (checkContagion(spreader, human))
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
	 * @return id superficie
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
	
	public Geometry getGeometry() {
		return geometry;
	}

	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getBlockId() {
		return blockId;
	}

	public void setBlockId(int blockId) {
		this.blockId = blockId;
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
}
