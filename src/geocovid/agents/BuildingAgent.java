package geocovid.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import geocovid.DataSet;
import repast.simphony.random.RandomHelper;

public class BuildingAgent {
	// Atributos del GIS
	private Geometry geometry;
	private long id;
	private long blockId;
	private String type;
	private int area;
	private int coveredArea;
	//
	private int capacity;
	private int size[] = {0,0}; // ancho x largo
	private int grid[][];
	private boolean outdoor;
	
	private List<HumanAgent> spreadersList = new ArrayList<HumanAgent>(); // Lista de HumanAgent trasmisores
	private Map<Integer, HumanAgent> humans = new HashMap<>();
	
	private Map<Integer, SurfaceAgent> surfacesMap = new HashMap<>();
	
	public BuildingAgent(Geometry geo, long id, long blockId, String type, int area, int coveredArea) {
		this.geometry = geo;
		this.id = id;
		this.blockId = blockId;
		this.type = type;
		this.area = area;
		this.coveredArea = coveredArea;
		//
		setBuildingShape();
	}
	
	public BuildingAgent(BuildingAgent ba) {
		this.geometry = ba.geometry;
		this.id = ba.id;
		this.blockId = ba.blockId;
		this.type = ba.type;
		this.area = ba.area;
		this.coveredArea = ba.coveredArea;
		//
		setBuildingShape();
	}
	
	/**
	 * Crea la grilla de posiciones, segun forma y area
	 */
	private void setBuildingShape() {
		// Si es espacio verde tomo toda el area
		int realArea;
		if (coveredArea > 0) {
			realArea = coveredArea;
			outdoor = false;
		}
		else {
			realArea = area;
			outdoor = true;
		}
		// Se resta un porcentaje del area para paredes, muebles, etc.
		realArea *= DataSet.BUILDING_AVAILABLE_AREA;
		if (geometry instanceof Point) {
			// Si es solo un punto, tomar la superficie como un cuadrado
			size[1] = (int)Math.sqrt(realArea);
			size[0] = size[1] + 1;
		}
		else {
			// Si es una forma, tomar medida mas chica como el ancho
			Envelope env = geometry.getEnvelopeInternal();
			int width = (int)(env.getWidth() * 111320); 	// grados cartesianos a metros
			int height = (int)(env.getHeight() * 111320);	// grados cartesianos a metros
			
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
	 * @param human
	 * @return {x, y} o null
	 */
	public int[] insertHuman(HumanAgent human) {
		// TODO ver si usar fuerza bruta nomas, buscar un punto o armar array con posiciones libres
		if (humans.size() >= capacity) {
			System.out.println("building full "+human.getAgentID());
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
	 * Inserta el humano en la posicion de la grilla dada y en humansMap
	 * @param human
	 * @param pos
	 * @return
	 */
	public int[] insertHuman(HumanAgent human, int[] pos) {
		int humanId = human.getAgentID();
		grid[pos[0]][pos[1]] = humanId;
		humans.put(humanId, human);
		if (human.isContagious()) {
			spreadersList.add(human);
			if ((humans.size() - spreadersList.size()) != 0)
				spreadVirus(human.getAgentID(), pos);
		}
		else if (!human.wasExposed()) {
			if (!spreadersList.isEmpty())
				findNearbySpreaders(human, pos);
			// Se omite la estela en caso de espacios abiertos
			if (!outdoor && !human.wasExposed() && !surfacesMap.isEmpty())
				checkIfSurfaceContaminated(human, pos);
		}
		return pos;
	}

	/**
	 * Remueve al humano de la grilla y de las listas
	 * @param human
	 * @param pos
	 */
	public void removeHuman(HumanAgent human, int[] pos) {
		int humanId = human.getAgentID();
		// Si quedo afuera, no se continua
		if (pos == null)
			return;
		//
		grid[pos[0]][pos[1]] = 0;
		if (!humans.remove(humanId, human))
			System.out.println("not found "+human.getAgentID());
		if (human.isContagious()) {
			removeSpreader(human, pos);
		}
	}
	
	public void addSpreader(HumanAgent human) {
		// Si comienza a contagiar luego de ingresar al building
		spreadersList.add(human);
		if ((humans.size() - spreadersList.size()) != 0)
			spreadVirus(human.getAgentID(), human.getCurrentPosition());
	}
	
	public void removeSpreader(HumanAgent human, int[] pos) {
		spreadersList.remove(human);
		// Se omite la estela en caso de espacios abiertos
		if (outdoor)
			return;
		// Se crea la estela cuando el contagioso sale de la parcela
		int csId = getSurfaceId(pos);
		SurfaceAgent surface = surfacesMap.get(csId);
		if (surface == null) {
			// Se crea una superficie con la posicion como ID
			surfacesMap.put(csId, new SurfaceAgent(csId));
		}
		else {
			// Si la superficie ya estaba contaminada, se 'renueva' el virus
			surface.updateLifespan();
		}
	}
	
    /**
     * Esparce el virus a los humanos susceptibles a su alrededor.
     * @param id 
     * @see DataSet#INFECTION_RADIUS
     * @see DataSet#INFECTION_RATE
     */
    public void spreadVirus(int spId, int[] spPos) {
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
		    	if ((preyId != 0) && (preyId != spId)) { // Si no esta vacio o es el mismo
		    		prey = humans.get(preyId);
					if (!prey.wasExposed()) {
						if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.INFECTION_RATE) {
							prey.setExposed();
						}
					}
		    	}
		    }
		}
    }
    
    /**
     * Busca en la lista de trasmisores los que estan dentro del radio de contagio, y se contagia.
     * @see DataSet#INFECTION_RADIUS
     * @see DataSet#INFECTION_RATE
     */
    public void findNearbySpreaders(HumanAgent human, int[] pos) {
    	// TODO ver que es mas rapido -> calcular distancia con infectados o buscar en grid
    	int[] spPos = new int[2];
    	for (HumanAgent spreader : spreadersList) {
    		spPos = spreader.getCurrentPosition();
            if (Math.max(Math.abs(pos[0] - spPos[0]), Math.abs(pos[1] - spPos[1])) <= DataSet.INFECTION_RADIUS) {
				if (RandomHelper.nextIntFromTo(1, 100) <= DataSet.INFECTION_RATE) {
					human.setExposed();
					break;
				}
            }
    	}
    }
    
    /**
     * Verifica si la superficie donde esta parado el Humano esta contaminada y checkea si el Humano se contagia.
     */
    public void checkIfSurfaceContaminated(HumanAgent human, int[] pos) {
    	int csId = getSurfaceId(pos);
    	SurfaceAgent surface = surfacesMap.get(csId);
    	if (surface != null) {
    		// Si en el ultimo checkeo la superficie seguia contaminada
    		if (surface.isContaminated()) {
    			if (RandomHelper.nextIntFromTo(1, 100) <= surface.getInfectionRate()) {
    				human.setExposed();
    			}
			}
    		// Es preferible no eliminar la superficie contaminada, para utilizar el objeto posteriormente
    	}
    }
    
    /**
     * El ID de superficie se calcula como: (Y * ANCHO) + X.
     * @param pos de superficie
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
		return capacity;
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
		return humans.size();
	}
}
