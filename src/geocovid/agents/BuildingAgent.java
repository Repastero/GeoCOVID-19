package geocovid.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import geocovid.DataSet;
import geocovid.InfeccionReport;
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
	//
	private int capacity;
	private int size[] = {0,0}; // ancho x largo
	private int grid[][];
	private boolean outdoor;
	
	private List<HumanAgent> spreadersList = new ArrayList<HumanAgent>(); // Lista de HumanAgent trasmisores
	private Map<Integer, HumanAgent> humansMap = new HashMap<>(); // Mapa <Id Humano, HumanAgent>
	
	private Map<Integer, SurfaceAgent> surfacesMap = new HashMap<>(); // Mapa <Id Superficie, SurfaceAgent>
	
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
		if (geometry instanceof Point) {
			// Si es solo un punto, tomar la superficie como un cuadrado
			size[1] = (int)Math.sqrt(realArea);
			size[0] = size[1] + 1;
		}
		else {
			// Si es una forma, tomar medida mas chica como el ancho
			Envelope env = geometry.getEnvelopeInternal();
			int width = (int)(env.getWidth() * DataSet.DEGREE_PRECISION); 	// grados cartesianos a metros
			int height = (int)(env.getHeight() * DataSet.DEGREE_PRECISION);	// grados cartesianos a metros
			
			// Tomar medida mas chica como el frente de la propiedad
			size[0] = (width >= height) ? height : width;
			// El largo de la propiedad varia segun la sup cubierta
			size[1] = (int)(realArea / size[0]);
			
			// Si el largo queda en cero o un valor muy chico, creo un cuadrado
			if (size[1] == 0 || size[0] / size[1] >= 10) {
				size[1] = (int)Math.sqrt(realArea);
				size[0] = size[1] + 1;
			}
			// Si el area queda muy chica, sumo 1 metro mas de largo y resto 1 de ancho
			else if (size[0] * size[1] < realArea - size[1]) {
				--size[0];
				++size[1];
			}
		}
		
		// Crear grilla
		grid = new int[size[0]][size[1]];
		capacity = size[0]*size[1];
	}
	
	/**
	 * Asigna un lugar en la grilla para el nuevo ingreso
	 * @param human HumanAgent
	 * @return {x, y} o null
	 */
	public int[] insertHuman(HumanAgent human) {
		// TODO ver si usar fuerza bruta nomas, buscar un punto o armar array con posiciones libres
		if (humansMap.size() >= capacity) {
			System.out.println("Building full - ID: "+getId());
			// TODO ver que hacer con el humano si no hay lugar
			return null;
		}
		
		int x, y;
		do {
			x = RandomHelper.nextIntFromTo(0, size[0]-1);
			y = RandomHelper.nextIntFromTo(0, size[1]-1);
		} while (grid[x][y] != 0);
		
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
		int humanId = human.getAgentID();
		grid[pos[0]][pos[1]] = humanId;
		humansMap.put(humanId, human);
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
		// Si es susceptible se busca si pudo contagiarse
		if (!human.wasExposed()) {
			// Primero busca fuentes de contagio directo
			if (!spreadersList.isEmpty())
				findNearbySpreaders(human, pos);
			// Si se contagia directamente, no checkea estela
			if (!human.wasExposed() && !surfacesMap.isEmpty())
				checkIfSurfaceContaminated(human, pos);
		}
		// Si es contagioso se buscan contactos cercanos susceptibles
		else if (human.isContagious()) {
			if ((humansMap.size() - spreadersList.size()) != 0)
				spreadVirus(human, pos);
			// 
			removeSpreader(human, pos);
		}
		//
		grid[pos[0]][pos[1]] = 0;
		if (!humansMap.remove(human.getAgentID(), human))
			System.err.println("Humano no encontrado en Building "+human.getAgentID());
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
		spreadersList.remove(human);
		// Se crea la estela cuando el contagioso sale de la parcela
		int csId = getSurfaceId(pos);
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
	
	/**
     * Esparce el virus a los humanos susceptibles con los que tuvo contacto cercano y prolongado.
	 * @param spHuman HumanAgent contagioso
	 * @param spPos posicion en grilla de spHuman 
     * @see DataSet#INFECTION_RADIUS
     * @see DataSet#INFECTION_EXPOSURE_TIME
     * @see DataSet#INFECTION_RATE
	 */
    private void spreadVirus(HumanAgent spHuman, int[] spPos) {
    	int sprId = spHuman.getAgentID();
    	// Buscar vecinos
    	int radius = DataSet.INFECTION_RADIUS;
		// Si se pasa del inicio
		int startX = (spPos[0] - radius < 0) ? 0 : spPos[0] - radius;
		int startY = (spPos[1] - radius < 0) ? 0 : spPos[1] - radius;
		// Si se pasa del fin
		int endX = (spPos[0] + radius > size[0]) ? size[0] : spPos[0] + radius;
		int endY = (spPos[1] + radius > size[1]) ? size[1] : spPos[1] + radius;
		
		HumanAgent prey = null;
		int preyId;
		// Recorre las posiciones de la grilla al rededor del infectado, buscando nuevos huespedes
		// TODO ver si hay diferencia en rendimiento reccoriendo "humans" y midiendo la distancia
		for (int row = startX; row < endX; row++) {
		    for (int col = startY; col < endY; col++) {
	    		preyId = grid[row][col];
		    	if ((preyId != 0) && (preyId != sprId)) { // Si no esta vacio o es el mismo
		    		prey = humansMap.get(preyId);
					if (!prey.wasExposed()) {
						if (Math.abs(spHuman.getRelocationTime() - prey.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
							if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.INFECTION_RATE) {
								prey.setExposed();
							}
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
     * @see DataSet#INFECTION_RADIUS
     * @see DataSet#INFECTION_EXPOSURE_TIME
     * @see DataSet#INFECTION_RATE
     */
    private void findNearbySpreaders(HumanAgent human, int[] pos) {
    	// TODO ver que es mas rapido -> calcular distancia con infectados o buscar en grid
    	int[] spPos = new int[2];
    	for (HumanAgent spreader : spreadersList) {
    		spPos = spreader.getCurrentPosition();
            if (Math.max(Math.abs(pos[0] - spPos[0]), Math.abs(pos[1] - spPos[1])) <= DataSet.INFECTION_RADIUS) {
            	if (Math.abs(human.getRelocationTime() - spreader.getRelocationTime()) >= DataSet.INFECTION_EXPOSURE_TIME) {
            		if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.INFECTION_RATE) {
            			human.setExposed();
            			break;
            		}
            	}
            }
    	}
    }
    
    /**
     * Verifica si la superficie donde esta parado el Humano esta contaminada y checkea si el Humano se contagia.
     * @param human HumanAgent susceptible
     * @param pos posicion en grilla de human
     */
    private void checkIfSurfaceContaminated(HumanAgent human, int[] pos) {
    	int csId = getSurfaceId(pos);
    	SurfaceAgent surface = surfacesMap.get(csId);
    	if (surface != null) {
    		// Si en el ultimo checkeo la superficie seguia contaminada
    		if (surface.isContaminated()) {
    			if (RandomHelper.nextIntFromTo(1, 100) <= surface.getInfectionRate()) {
    				human.setExposed();
    				InfeccionReport.addExposedToCS();
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
    private int getSurfaceId(int[] pos) {
    	return (pos[1]*size[0])+pos[0];
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
	
	public int getNumberOfSpots() {
		return coveredArea;
	}
	
	public int[][] getGrid() {
		return grid;
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
	
	public int getHumansAmount() {
		return humansMap.size();
	}
}
