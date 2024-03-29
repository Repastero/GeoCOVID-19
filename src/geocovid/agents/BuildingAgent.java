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
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.random.RandomHelper;

/**
 * Clase base de parcelas.
 */
public class BuildingAgent {
	protected SubContext context;	// Puntero
	private static ISchedule schedule; // Puntero 
	private int sectoralType;	// Indice de tipo de seccional
	private int sectoralIndex;	// Indice de seccional
	// Atributos del GIS
	private Coordinate coordinate;
	private int id;
	private String type;
	private int area;
	private int coveredArea;
	private int realArea;
	private boolean workingPlace;
	// Atributos de dimensiones
	private int capacity;
	private int startingRow = 0; // indice de fila inicial para no trabajadores 
	private int size[] = {0,0}; // ancho x largo
	private boolean outdoor;
	private boolean ventilated = true; // por defecto todas las parcelas ventiladas
	// Atributos staticos para checkear el radio de infeccion
	private static int dropletCircleRadius;
	private static int aerosolCircleRadius;
	// Atributos staticos para checkear el radio de contactos personales
	private static int xShiftsPD[];
	private static int yShiftsPD[];
	//
	/** Lista de HumanAgent trasmisores */
	protected List<HumanAgent> spreadersList = new ArrayList<HumanAgent>();
	/** Mapa de HumanAgent dentro de parcela <Id Humano, HumanAgent> */
	protected Map<Integer, HumanAgent> humansMap = new HashMap<>();
	/** Mapa de SurfaceAgent dentro de parcela <Id Superficie, SurfaceAgent> */
	protected Map<Integer, SurfaceAgent> surfacesMap = new HashMap<>();
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, int id, String type, int area, int coveredArea) {
		this.context = subContext;
		this.sectoralType = secType;
		this.sectoralIndex = secIndex;
		this.coordinate = coord;
		this.id = id;
		this.type = type;
		this.area = area;
		this.coveredArea = coveredArea;
	}
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, int id, String type, int area, int coveredArea, double areaModifier, boolean workplace) {
		// Constructor Home/Workplace
		this(subContext, secType, secIndex, coord, id, type, area, coveredArea);
		//
		this.workingPlace = workplace;
		setRealArea(areaModifier);
		setBuildingShape();
	}
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, int id, String type, int width, int lenght, boolean workplace) {
		// Constructor Home/Workplace de dimensiones especificas
		this(subContext, secType, secIndex, coord, id, type, width * lenght * DataSet.HUMANS_PER_SQUARE_METER, width * lenght * DataSet.HUMANS_PER_SQUARE_METER);
		//
		this.workingPlace = workplace;
		setBuildingShape(width, lenght);
	}
	
	public BuildingAgent(SubContext subContext, int secType, int secIndex, Coordinate coord, int realArea, boolean outdoor) {
		// Constructor Evento
		this(subContext, secType, secIndex, coord, 0xFFFFFFFF, "event", 0, 0);
		if (outdoor)
			this.area = realArea;
		else
			this.coveredArea = realArea;
		//
		this.realArea = realArea;
		this.outdoor = outdoor;
		setBuildingShape();
	}
	
	public BuildingAgent(BuildingAgent ba) {
		// Constructor para crear copia de ba
		this(ba.context, ba.sectoralType, ba.sectoralIndex, ba.coordinate, ba.id, ba.type, ba.area, ba.coveredArea);
		//
		this.realArea = ba.realArea;
		this.outdoor = ba.outdoor;
		this.size[0] = ba.size[0];
		this.size[1] = ba.size[1];
		this.capacity = ba.capacity;
		//
		this.workingPlace = ba.workingPlace;
	}
	
	/**
	 * Calcula el radio de contagio por droplet y aerosol<p>
	 * Crea las posiciones dentro del circulo de distancia personal
	 * @see DataSet#DROPLET_INFECTION_RADIUS
	 * @see DataSet#AEROSOL_INFECTION_RADIUS
	 * @see DataSet#PERSONAL_DISTANCE
	 */
	public static void initInfAndPDRadius() {
		// Asigno ISchedule de la nueva instancia
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
		SurfaceAgent.init(schedule);
		//
		final int[] shiftsCount = {8,20,36,56,80,128,164,204,248,296,348,444,508,576,648};
		// Setear radio de contagio por droplet y aerosol
		dropletCircleRadius	= getCircleRadius(DataSet.DROPLET_INFECTION_RADIUS);
		aerosolCircleRadius = getCircleRadius(DataSet.AEROSOL_INFECTION_RADIUS);
		// Chequeo que radio este dentro del rango
		int radius = DataSet.PERSONAL_DISTANCE;
		if (radius < 1 || radius > 15)
			radius = 4;
		// Crea array para contener los desvios de X e Y en contactos personales
		xShiftsPD = new int[shiftsCount[radius-1]];
		yShiftsPD = new int[shiftsCount[radius-1]];
		getCircleRadius(radius, xShiftsPD, yShiftsPD);
	}
	
	/**
	 * Retorna el alcance maximo del radio dado.
	 * @param radius
	 * @return <b>int</b> radio del circulo
	 */
	public static int getCircleRadius(int radius) {
		// Desvio maximo de X + Y
		return radius + (radius / 6) + 1;
	}
	
	/**
	 * Carga los valores de desplazamientos de X e Y, segun el radio dado.
	 * @param radius radio
	 * @param xs desvios de x
	 * @param ys desvios de y
	 * @return <b>int</b> radio del circulo
	 */
	public static int getCircleRadius(int radius, int[] xs, int[] ys) {
		int circleRadius = getCircleRadius(radius);
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
	 * Crea forma de parcela segun area y setea dimensiones
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
	 * Crea forma y dimensiones de parcela segun atributos
	 * @param mWidth ancho en metros
	 * @param mLenght largo en metros
	 */
	private void setBuildingShape(int mWidth, int mLenght) {
		// Se multiplican los metros por la cantidad de Humanos por m2
		size[0] = mWidth * DataSet.HUMANS_PER_SQUARE_METER;
		size[1] = mLenght * DataSet.HUMANS_PER_SQUARE_METER;
		capacity = size[0]*size[1];
	}
	
	/**
	 * Si hay capacidad, asigna un lugar en parcela para el nuevo ingreso
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
	 * Inserta el humano en la posicion dada y en humansMap.
	 * @param human HumanAgent
	 * @param pos nueva posicion
	 * @return posicion {x, y} en parcela
	 */
	public int[] insertHuman(HumanAgent human, int[] pos) {
		if (humansMap.put(getPosId(pos), human) != null)
			return null; // Lugar ocupado
		if (human.isContagious())
			addSpreader(human);
		human.aerosoled = false; // reinicia el estado de afectado por aerosol
		return pos;
	}
	
	/**
	 * Remueve al humano de parcela y de las listas
	 * @param human HumanAgent
	 * @param pos {x, y} en parcela
	 */
	public void removeHuman(HumanAgent human, int[] pos) {
		// Si quedo afuera, no se continua
		if (pos == null)
			return;
		int posId = getPosId(pos);
		// Busca contactos cercanos
		if (DataSet.COUNT_INTERACTIONS)
			sociallyInteract(human, pos);
		// Si es susceptible busca si pudo contagiarse por los 3 tipos de contagio
		if (!human.wasExposed()) {
			// Primero busca fuentes de contagio directo
			if (!spreadersList.isEmpty()) {
				findNearbySpreaders(human, pos, spreadersList);
			}
			// Si se contagia directamente, no checkea estela
			if (!human.wasExposed() && !surfacesMap.isEmpty())
				checkIfSurfaceContaminated(human, posId);
		}
		// Puede existir contacto estrecho entre trabajadores / estudiantes
		if (workingPlace && context.closeContactsEnabled()) {
			lookForCloseContacts(human, pos);
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
		double lastTick = schedule.getTickCount();
		// Primero revisa si el tiempo de contacto minimo
		if ((lastTick - agent.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME)
			return;
		int spId = agent.getAgentID();
		HumanAgent contact;
		int posX, posY;
		
		/* Si la cantidad de posiciones en el radio de contacto es mayor a la cantidad
		 * de humanos en parcela, mide distancia con cada humano */
		if (xShiftsPD.length > humansMap.size()) {
			for (Map.Entry<Integer, HumanAgent> entry : humansMap.entrySet()) {
				contact = entry.getValue();
				// Revisa que el contacto este en parcela el tiempo minimo
				if (contact == agent || ((lastTick - contact.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME))
					continue;
				//
				posX = Math.abs(agentPos[0] - contact.getCurrentPosition()[0]);
				posY = Math.abs(agentPos[1] - contact.getCurrentPosition()[1]);
				// Chequea el radio
				if (posX < dropletCircleRadius && posY < dropletCircleRadius) { // que X o Y individualmente no exedan el radio de contacto
					if (posX + posY <= dropletCircleRadius) { // que la suma del desplazamiento no exeda el radio de contacto
			    		agent.addSocialInteraction(contact.getAgentID());
			    		contact.addSocialInteraction(spId);
					}
				}
			}
		}
		/* Si por lo contrario hay mas humanos que posiciones en el radio de contacto,
		 * revisa si hay humanos en cada posicion del radio de contacto */
		else {
			for (int i = 0; i < xShiftsPD.length; i++) {
				posX = agentPos[0] + xShiftsPD[i];
				posY = agentPos[1] + yShiftsPD[i];
		    	if (posX >= 0 && posY >= 0) { // no de un valor negativo
		    		if (posX < size[0] && posY < size[1]) { // que no salga de limites
			    		contact = humansMap.get(getPosId(posX, posY));
				    	if (contact != null) { // Si hay humano
							// Revisa que el contacto este en parcela el tiempo minimo
							if ((lastTick - contact.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
					    		agent.addSocialInteraction(contact.getAgentID());
					    		contact.addSocialInteraction(spId);
				    		}
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
	 * @param pos {x, y} en parcela
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
	 * Setea inicio de contagio en humano.
	 * @param prey HumanAgent susceptible
	 * @param infRate beta contagio
	 */
	protected boolean infectHuman(HumanAgent prey, double infRate) {
		return prey.checkContagion(infRate);
	}
	
	/**
	 * Retorna chance de contagio directo en parcela.
	 * @return <b>double</b> beta (0 a 1)
	 */
	private double getDirectInfectionRate() {
		return Temperature.getInfectionRate(context.getTownRegion(), sectoralType, outdoor, ventilated);
	}
	
	/**
	 * Retorna chance de contagio aereo en parcela.
	 * @return <b>double</b> beta (0 a 1)
	 */
	private double getAerosolInfectionRate() {
		return Temperature.getAerosolInfectionRate(context.getTownRegion(), outdoor, ventilated);
	}
	
	/**
	 * Chequea todos los modificadores/escenarios para verificar si hay contagio.
	 * @param spreader HumanAgent contagioso
	 * @param prey HumanAgent susceptible
	 * @return <b>true</b> si hubo contagio
	 */
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey, double infectionRate) {
		// Si es un lugar de trabajo se chequea si respetan distanciamiento y usan cubreboca
		if (workingPlace) {
			if (spreader.isPreInfectious()) {
				return checkCloseContact(spreader, prey);
			}
			if (infectionRate == 0)
				return false;
		}
		else if (spreader.quarantined) {
			// Si esta aislado, se supone tiene todas las precauciones para no contagiar
			infectionRate *= 1 - DataSet.ISOLATION_INFECTION_RATE_REDUCTION;
		}
		
		// Se modifica la cantidad de droplets segun estado
		infectionRate *= DataSet.STATE_DROPLET_VOLUME[spreader.getCurrentState()];
		return infectHuman(prey, infectionRate);
	}
	
	/**
	 * Busca si se produce contacto estrecho entre humanos cercanos.
	 * @param human HumanAgent pre-contagioso o susceptible
	 * @param pos {x, y} en parcela
	 */
	protected void lookForCloseContacts(HumanAgent human, int[] pos) {}
	
	/**
	 * Chequea si hay contacto estrecho entre los dos humanos.
	 * @param preSpreader HumanAgent pre-contagioso
	 * @param prey HumanAgent susceptible
	 * @return <b>true</b> si hubo contacto estrecho
	 */
	protected boolean checkCloseContact(HumanAgent preSpreader, HumanAgent prey) { return false; }
	
	/**
	 * Esparce el virus a los humanos susceptibles con los que tuvo contacto cercano y prolongado.
	 * @param spHuman HumanAgent contagioso
	 * @param spPos posicion en parcela de spHuman
	 */
	protected void spreadVirus(HumanAgent spreader, int[] spPos) {
		int[] pos = new int[2];
		int xShift, yShift;
		HumanAgent prey;
		double lastTick = schedule.getTickCount();
		
		// Primero revisa si el tiempo que estuvo alcanza para contagiar
		if ((lastTick - spreader.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME)
			return;
		
		// Contagia todos los humanos susceptibles en parcela
		for (Map.Entry<Integer, HumanAgent> entry : humansMap.entrySet()) {
			prey = entry.getValue();
			// Revisa si es susceptible y este en parcela un tiempo mayor al de contagio
			if (prey.wasExposed() || ((lastTick - prey.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME))
				continue;
			// Calcula la distancia en X e Y
			pos = prey.getCurrentPosition();
			xShift = Math.abs(pos[0] - spPos[0]);
			yShift = Math.abs(pos[1] - spPos[1]);
			// Chequea si hay contagio
			checkDistance(xShift, yShift, spreader, prey);
		}
	}
    
	/**
	 * Busca en la lista de trasmisores los que tuvo contacto cercano y prolongado, y chequea contagio.
	 * @param prey HumanAgent susceptible
	 * @param pos posicion en parcela de human
	 * @param spreaders lista de HumanAgent contagiosos o pre-contagiosos
	 */
	protected void findNearbySpreaders(HumanAgent prey, int[] pos, List<HumanAgent> spreaders) {
		int[] spPos = new int[2];
		int xShift, yShift;
		double lastTick = schedule.getTickCount();
		
		// Primero revisa si el tiempo que estuvo alcanza para contagiarse
		if ((lastTick - prey.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME)
			return;
		
		for (HumanAgent spreader : spreaders) {
			// Fix temporal al raro bug del spreader fantasma 
			if (spreader.getCurrentPosition() == null || spreader.getCurrentBuilding() != this) {
				if (spreaders == spreadersList)
					System.err.println("spreadersList fantasma!");
				else
					System.err.println("preSpreadersList fantasma!");
				// saco el fantasma de la lista para que no moleste mas
				spreaders.remove(spreader);
				continue;
			}
			//
			// Revisa que el spreader este en parcela un tiempo mayor al de contagio
			if ((lastTick - spreader.getRelocationTime()) < DataSet.INFECTION_EXPOSURE_TIME)
				continue;
			// Calcula la distancia en X e Y
			spPos = spreader.getCurrentPosition();
			xShift = Math.abs(pos[0] - spPos[0]);
			yShift = Math.abs(pos[1] - spPos[1]);
			// Chequea si hay contagio
			if (checkDistance(xShift, yShift, spreader, prey))
				break;
		}
	}
	
	/**
	 * Mide si esta en radio de contagio por aerosol/droplet y se contagia.
	 * @param xDist distancia en X
	 * @param yDist distancia en Y
	 * @param spreader HumanAgent contagioso
	 * @param prey HumanAgent susceptible
	 * @return <b>true</b> si hay contagio
	 */
	private boolean checkDistance(int xDist, int yDist, HumanAgent spreader, HumanAgent prey) {
		boolean insideAerosolRadius = true;
		// Primero chequea por radio aerosol
		if (!spreader.isPreInfectious() && !prey.aerosoled) {
			insideAerosolRadius = false;
			if (xDist < aerosolCircleRadius && yDist < aerosolCircleRadius) { // que X o Y individualmente no exedan el radio de contagio
				if (xDist + yDist <= aerosolCircleRadius) { // que la suma del desplazamiento no exeda el radio de contagio
					insideAerosolRadius = true;
					if (ventilated) // si esta ventilado cuenta un solo aerosol
						prey.aerosoled = true;
					if (checkContagion(spreader, prey, getAerosolInfectionRate())) { // si se contagia por aerosol, no hace falta contagiar por droplet
						InfectionReport.addExposedToAero();
						return true;
					}
				}
			}
		}
		if (insideAerosolRadius) { // si esta en radio de aerosol, esta en radio de droplet
			// Segundo chequea por radio droplet
			if (xDist < dropletCircleRadius && yDist < dropletCircleRadius) { // que X o Y individualmente no exedan el radio de contagio
				if (xDist + yDist <= dropletCircleRadius) { // que la suma del desplazamiento no exeda el radio de contagio
					if (checkContagion(spreader, prey, getDirectInfectionRate())) {
						return true;
					}
				}
			}
		}
		return false;
	}
    
	/**
	 * Verifica si la superficie donde esta parado el Humano esta contaminada y checkea si el Humano se contagia.
	 * @param human HumanAgent susceptible
	 * @param pos posicion en parcela de human
	 */
	protected void checkIfSurfaceContaminated(HumanAgent human, int csId) {
		SurfaceAgent surface = surfacesMap.get(csId);
		if (surface != null) {
			int csBeta = surface.getInfectionRate();
			if (csBeta != 0) {
				if (human.checkContagion(csBeta)) {
					InfectionReport.addExposedToCS();
				}
			}
			else {
				surfacesMap.remove(csId);
			}
		}
	}
    
	/**
	 * El ID de superficie se calcula como: (Y * ANCHO) + X.
	 * @param pos {x, y} de superficie
	 * @return <b>int</b> id superficie
	 */
	protected int getPosId(int[] pos) {
		return (pos[1] * size[0]) + pos[0];
	}
	
	private int getPosId(int x, int y) {
		return (y * size[0]) + x;
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
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
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
	
	public void setVentilated(boolean vent) {
		ventilated = vent;
	}

}
