package geocovid;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

/**
 * Contiene las caracteristicas habitacionales de cada ciudad y los dias de cambio de fases.
 */
public final class Town {
	/** Ajuste de poblacion sin menores de 5 anos */
	private static final double POPULATION_ADJUSTMENT = 0.9251081133; // Fuente: Proyeccion INTA 2021 - Santa Fe
	
	public static final Map<String, String[]> DEPARTMENTS_TOWNS  = new HashMap<String, String[]>() {
		private static final long serialVersionUID = -1101465942407751991L;
		{
			put("9dejulio",			new String[]{"tostado"});
			put("belgrano",			new String[]{"lasparejas+lasrosas+armstrong"});
			put("caseros",			new String[]{"casilda", "sanjosedelaesquina+arequito+chabas"});
			put("castellanos",		new String[]{"rafaela", "sunchales"});
			put("constitucion",		new String[]{"villaconstitucion"});
			put("garay",			new String[]{"helvecia+santarosadecalchines+cayasta"});
			put("generallopez",		new String[]{"venadotuerto", "rufino", "firmat", "villacanas"});
			put("generalobligado",	new String[]{"reconquista", "avellaneda", "villaocampo+lastoscas"});
			put("iriondo",			new String[]{"canadadegomez", "totoras"});
			put("lacapital",		new String[]{"santafe"});
			put("lascolonias",		new String[]{"pilar+humboldt+esperanza", "sanjeronimonorte+franck+sancarloscentro"});
			put("rosario",			new String[]{"rosario"});
			put("sancristobal",		new String[]{"sancristobal", "ceres", "sanguillermo+suardi"});
			put("sanjavier",		new String[]{"sanjavier","romang"});
			put("sanjeronimo",		new String[]{"desvioarijon+arocena+coronda", "maciel+gaboto+monje+barrancas", "sangenaro+centeno", "galvez"});
			put("sanjusto",			new String[]{"sanjusto"});
			put("sanlorenzo",		new String[]{"sanlorenzo+puertogeneralsanmartin", "carcarana+sanjeronimosud+roldan", "capitanbermudez+frayluisbeltran"});
			put("sanmartin",		new String[]{"sanjorge+sastre", "eltrebol+carlospellegrini+canadarosquin+piamonte"});
			put("vera",				new String[]{"vera+margarita+calchaqui"});
		}
	};
	
	/** Dias de delay en la entrada de infectados */
	public static int outbreakStartDelay = 0;
	/** Cantidad de infectados iniciales en cada municipio */
	public static int firstInfectedAmount = 1;
	/** Dias por defecto de cambio de fase */
	private static final int[][] DEFAULT_PHASES_DAYS = {
		{158,159,170,181,182,    188,214,226,231,    255,269,281,292,313,317,331,335,340,345,348,358,359,362,365,366,369,375,397,413,425,434,439,463,478,488,505,516,528,538},	// lacapital
		{158,159,170,181,182,184,188,214,226,231,240,255,269,281,    313,    331,335,340,345,348,358,359,362,365,366,369,375,397,413,425,434,439,463,478,488,505,516,528,538},	// rosario
		{158,159,170,181,            214,226,231,    255,269,281,292,313,317,331,335,340,345,348,358,359,362,365,366,369,375,397,413,425,434,439,463,478,488,505,516,528,538},	// otros
	};
	
	/** Dias por defecto de los cambios de unidades de transporte publico */
	private final double[][] PUBLIC_TRANSPORT_QUANTIFICATION	= {
		{1, .6},	// 60% desde Abril 2020 hasta ahora (Junio 2021)
		{1, .5, .8},// 50% desde Marzo 2020, 80% desde Octubre 2020 hasta ahora (Julio 2021)
	};
	
	/** Nombre del municipio o comuna a simular */
	public String townName;
	/** Nombre del departamento al que pertenece <b>townName</b> */
	public String departmentName;
	
	/** Tipo de ciudad */ 
	public int regionType;
	/** Indice de region de la ciudad (0 sur, 1 centro o 2 norte) */ 
	public int regionIndex;
	
	/** Cantidad de Humanos locales (no salen a trabajar) */
	public int localHumans;
	/** Cantidad de Humanos que trabajan afuera */
	public int localTravelerHumans;
	/** Cantidad de Humanos que viven afuera */
	public int foreignTravelerHumans;
	
	/** Tipo de seccional segun indice */
	public int[] sectoralsTypes; // este valor se podria tomar analizando el shapefile
	/** Porcentaje de la poblacion en cada seccional (segun cantidad de parcelas) */
	public double[] sectoralsPopulation; // este valor se podria tomar analizando el shapefile
	public int sectoralsCount;
	
	/** Dias desde el 01-01-2020 donde ocurre el cambio de fase<p>
	 * Para calcular dias entre fechas o sumar dias a fecha:<p>
	 * @see <a href="https://www.timeanddate.com/date/duration.html?d1=1&m1=1&y1=2020&">Dias entre fechas</a>
	 * @see <a href="https://www.timeanddate.com/date/dateadd.html?d1=1&m1=1&y1=2020&">Sumar dias a fecha</a>
	 */
	public int[] lockdownPhasesDays;	
	/** Dias desde el 01-01-2020 que ingresa el primer infectado */
	public int outbreakStartDay;
	
	/** Cantidad de colectivos maximos que presenta cada ciudad */
	public int publicTransportUnits;
	/** Si la ciudad tiene una temporada turistica */
	public boolean touristSeasonAllowed;
	
	public Town(String town, String dept) {
		this.setTown(town, dept);
	}
	
	/**
	 * Asigna valores a atributos de ciudad
	 * @param rgnType indice tipo de SubContext
	 * @param rgnIndex indice de region (0 sur, 1 centro o 2 norte)
	 * @param locals cantidad de humanos locales
	 * @param travelersPerc porcentaje de humanos locales que trabajan/estudian fuera
	 * @param foreign cantidad de humanos visitantes
	 * @param secTypes lista de tipos de seccionales (0 media-alta, 1 media-baja)
	 * @param secPop lista porcentaje de poblacion por seccional
	 * @param phasesDays indices de dias de fases
	 * @param obStartDay indice dia contagio comunitario
	 * @param pubTransUnit cantidad de colectivos o 0 para deshabilitar
	 * @param tourSeasonAllowed <b>true</b> para habilitar temporada turistica
	 */
	private void setTownData(int rgnType, int rgnIndex, int locals, double travelersPerc, int foreign, int[] secTypes, double[] secPop, int[] phasesDays, int obStartDay, int pubTransUnit, boolean tourSeasonAllowed) {
		regionType = rgnType;
		regionIndex = rgnIndex;
		//
		localHumans = (int) (locals * POPULATION_ADJUSTMENT);
		localTravelerHumans = (int) (locals * travelersPerc) / 100;
		foreignTravelerHumans = foreign;
		//
		sectoralsTypes = secTypes;
		sectoralsPopulation = secPop;
		sectoralsCount = secTypes.length;
		//
		lockdownPhasesDays = phasesDays;
		outbreakStartDay = obStartDay + outbreakStartDelay;
		//
		publicTransportUnits = pubTransUnit;
		touristSeasonAllowed = tourSeasonAllowed;
	}
	
	public String getPlacesPropertiesFilepath() {
		String type;
		switch (regionType) {
		case 0:
			type = "lacapital";
			break;
		case 1:
			type = "rosario";
			break;
		default:
			type = "otros";
			break;
		}
		return String.format("./data/%s-places-markov.csv", type);
	}
	
	public String getParcelsFilepath() {
		return String.format("./data/%s/%s/parcels.shp", departmentName, townName);
	}
	
	public String getPlacesFilepath() {
		return String.format("./data/%s/%s/places.shp", departmentName, townName);
	}
	
	/**
	 * Si cambia la ciudad, carga sus atributos.
	 * @param town nombre de ciudad
	 * @param dept nombre de departamento
	 * @return <b>true</b> si se cambio de ciudad
	 */
	public boolean setTown(String town, String dept) {
		// Chequeo si es la misma
		if (town.equals(townName))
			return false;
		townName = town;
		departmentName = dept;
		//
		switch (townName) {
		case "tostado": // 53% de 9 de Julio
			setTownData(
				2, 2, 18088, 13, 0,
				new int[] {0, 1, 1, 1},
				new double[] {21.02, 29.12, 24.93, 24.93},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "lasparejas+lasrosas+armstrong": // 86% de Belgrano
			setTownData(
				2, 0, 15255 + 15741 + 13637, 13, 0,
				new int[] {0, 0, 1},
				new double[] {34.178, 35.257, 30.565},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "casilda": // 45% de Caseros
			setTownData(
				2, 0, 40342, 13, 0,
				new int[] {0, 0, 1},
				new double[] {34.735, 35.8075, 29.4575},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "sanjosedelaesquina+arequito+chabas": // 25% de Caseros
			setTownData(
				2, 0, 7616 + 7091 + 7483, 13, 0,
				new int[] {0, 0, 0},
				new double[] {34.32, 31.96, 33.72},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "rafaela": // 53% de Castellanos
			setTownData(
				2, 1, 109790, 13, 0,
				new int[] {0, 0, 0, 1, 1},
				new double[] {26.1, 19.285, 11.61, 20.55, 22.455},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "sunchales": // 12% de Castellanos
			setTownData(
				2, 1, 25690, 13, 0,
				new int[] {0, 1, 1},
				new double[] {57.01, 25.43, 17.56},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "villaconstitucion": // 56% de Constitucion
			setTownData(
				2, 0, 54995, 13, 0,
				new int[] {0, 0, 1, 1},
				new double[] {24.05, 28.94, 30.6, 16.41},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "helvecia+santarosadecalchines+cayasta": // 89% de Garay
			setTownData(
				2, 1, 8019 + 7430 + 5572, 13, 0,
				new int[] {1, 1, 1},
				new double[] {38.14, 26.51, 35.35},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "venadotuerto": // 42% de General Lopez
			setTownData(
				2, 0, 89197, 13, 0,
				new int[] {0, 0, 1, 1},
				new double[] {35.71, 28.29, 20.23, 15.77},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "rufino": // 10% de General Lopez
			setTownData(
				2, 0, 20858, 13, 0,
				new int[] {0, 1},
				new double[] {64, 36},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "firmat": // 11% de General Lopez
			setTownData(
				2, 0, 23035, 13, 0,
				new int[] {0, 0, 1, 1},
				new double[] {33.18, 30.83, 20.325, 15.665},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "villacanas": // 5% de General Lopez
			setTownData(
				2, 0, 10145, 13, 0,
				new int[] {0, 1, 1},
				new double[] {64, 20, 16},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "reconquista": // 43% de General Obligado
			setTownData(
				2, 2, 86973, 13, 0,
				new int[] {0, 0, 1, 1, 1, 1, 1},
				new double[] {15.381, 18.215, 10.376, 12.191, 15.045, 16.86, 11.932},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "avellaneda": // 16% de General Obligado
			setTownData(
				2, 2, 31349, 13, 0,
				new int[] {0, 1, 1, 1, 1},
				new double[] {32.78, 20.44, 18.7125, 15.1125, 12.955},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "villaocampo+lastoscas": // 17% de General Obligado
			setTownData(
				2, 2, 20326 + 13235, 13, 0,
				new int[] {1, 1},
				new double[] {60.57, 39.43},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "canadadegomez": // 44% de Iriondo
			setTownData(
				2, 0, 31727, 13, 0,
				new int[] {0, 0, 0},
				new double[] {39.65, 34.63, 25.72},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "totoras": // 16% de Iriondo
			setTownData(
				2, 0, 11745, 13, 0,
				new int[] {1, 1},
				new double[] {65.445, 34.555},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "santafe": // 73% de La Capital
			setTownData(
				0, 1, 431857, 8, 0,
				new int[] {0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1},
				new double[] {6.7, 6.05, 6.225, 5.625, 5.5, 7.3, 9.85, 9, 5.25, 8.3, 2.675, 2.6, 2.5, 8.15, 7.325, 3.45, 3.5},
				DEFAULT_PHASES_DAYS[0],
				194, // 13 Julio 2020
				200, // 200-210 coches en total, en los horarios pico
				false);
			break;
		//
		case "pilar+humboldt+esperanza": // 51% de Las Colonias
			setTownData(
				2, 1, 5778 + 5522 + 52641, 13, 0,
				new int[] {1, 1, 0, 0, 0, 0},
				new double[] {9.045, 8.635, 29.31, 29.31, 11.85, 11.85},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "sanjeronimonorte+franck+sancarloscentro": // 22% de Las Colonias
			setTownData(
				2, 1, 7399 + 7162 + 12477, 13, 0,
				new int[] {1, 1, 0, 0},
				new double[] {27.37, 26.49, 29.44, 16.7},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "rosario": // 77% de Rosario
			setTownData(
				1, 0, 1004603, 5.75, 0,
				new int[] {0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1},
				new double[] {8.14, 5.30, 3.80, 6.75, 8.73, 4.46, 4.22, 5.27, 6.76, 3.44, 3.77, 5.09, 5.94, 4.52, 1.84, 4.57, 3.10, 3.50, 7.06, 2.22, 0.82, 0.70},
				DEFAULT_PHASES_DAYS[1],
				194, // 13 Julio 2020
				800, // Entre las 3 empresas (y el trolebus ???)
				false);
			break;
		//
		case "sancristobal": // 21% de San Cristobal
			setTownData(
				2, 2, 16510, 13, 0,
				new int[] {0, 0, 1, 1},
				new double[] {40.5, 27.34, 16.38, 15.78},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "ceres": // 23% de San Cristobal
			setTownData(
				2, 2, 17957, 13, 0,
				new int[] {0, 1, 1},
				new double[] {66.358, 17.575, 16.067},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "sanguillermo+suardi": // 23% de San Cristobal
			setTownData(
				2, 2, 9548 + 8521, 13, 0,
				new int[] {1, 1},
				//new double[] {52.825, 47.175},
				new double[] {52.822, 47.178},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "sanjavier": // 54% de San Javier
			setTownData(
				2, 2, 18507, 13, 0,
				new int[] {0, 1, 1, 1},
				new double[] {38.133, 36.571, 14.624, 10.672},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "romang": // 28% de San Javier
			setTownData(
				2, 2, 9723, 13, 0,
				new int[] {1},
				new double[] {100},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "desvioarijon+arocena+coronda": // 30% de San Jeronimo
			setTownData(
				2, 1, 3047 + 3204 + 20652, 13, 0,
				new int[] {1, 1, 1},
				new double[] {11.325, 11.90, 76.775},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "maciel+gaboto+monje+barrancas": // 21% de San Jeronimo
			setTownData(
				2, 1, 6641 + 3639 + 2528 + 5983, 13, 0,
				new int[] {1, 1, 1, 1},
				new double[] {35.327, 19.382, 13.468, 31.823},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "sangenaro+centeno": // 15% de San Jeronimo
			setTownData(
				2, 1, 9933 + 3176, 13, 0,
				new int[] {0, 0, 1},
				new double[] {43.72, 32.06, 24.22},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "galvez": // 24% de San Jeronimo
			setTownData(
				2, 1, 21489, 13, 0,
				new int[] {0, 0, 0},
				new double[] {40.01, 37.05, 22.94},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "sanjusto": // 56% de San Justo
			setTownData(
				2, 2, 24676, 13, 0,
				new int[] {0, 0, 1, 1},
				new double[] {32.108, 12.90, 36.568, 18.424},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "sanlorenzo+puertogeneralsanmartin": // 37% de San Lorenzo
			setTownData(
				2, 0, 52446 + 17618, 13, 0,
				new int[] {0, 0, 1, 1, 1, 1},
				new double[] {27.275, 25.125, 12.88, 9.574, 17.42, 7.726},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "carcarana+sanjeronimosud+roldan": // 22% de San Lorenzo
			setTownData(
				2, 0, 18458 + 2998 + 18989, 13, 0,
				new int[] {0, 1, 1},
				new double[] {45.6325, 7.415, 46.9525},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "capitanbermudez+frayluisbeltran": // 27% de San Lorenzo
			setTownData(
				2, 0, 34147 + 17568, 13, 0,
				new int[] {0, 0, 0, 1, 1, 1},
				new double[] {16.812, 17.342, 16.012, 15.861, 19.193, 14.78},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "sanjorge+sastre": // 38% de San Martin
			setTownData(
				2, 1, 20499 + 6284, 13, 0,
				new int[] {0, 0, 0},
				new double[] {54.704, 21.834, 23.462},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		case "eltrebol+carlospellegrini+canadarosquin+piamonte": // 40% de San Martin
			setTownData(
				2, 1, 13404 + 5900 + 5996 + 3668, 13, 0,
				new int[] {0, 1, 1, 1},
				new double[] {46.27, 20.37, 20.70, 12.66},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		case "vera+margarita+calchaqui": // 72% de Vera
			setTownData(
				2, 2, 22537 + 4716 + 12599, 13, 0,
				new int[] {1, 1, 1},
				new double[] {56.55, 11.835, 31.615},
				DEFAULT_PHASES_DAYS[2],
				194, 0, false);
			break;
		//
		default:
			throw new InvalidParameterException("Ciudad erronea: " + townName);
		}
		return true;
	}
	
	/**
	 * Calcula la cantidad de unidades de PTs en funcionamiento por seccional.
	 * @param ptUnits total de unidades en funcionamiento 
	 * @return unidades de PublicTransport en funcionamiento por seccional
	 */
	public int[] getPTSectoralUnits(int ptUnits) {
		double fraction = ptUnits / 100d;
		int units;
		int[] sectoralsPTUnits = new int[sectoralsCount];
		for (int sI = 0; sI < sectoralsCount; sI++) {
			units = (int) Math.round(fraction * sectoralsPopulation[sI]);
			// Cada seccional tiene minimo 1
	      	if (units < 1)
	      		units = 1;
	      	//
	    	sectoralsPTUnits[sI] = units;
		}
		return sectoralsPTUnits;
	}
	
	/**
	 * Calcula la cantidad de PTs en funcionamiento segun fase y retorna cantidad por seccional.
	 * @param phase indice de fase
	 * @return unidades de PublicTransport por seccional segun fase
	 */
	public int[] getPTPhaseUnits(int phase) {
		// Si no hay transporte publico en town, retorna 0
		if (publicTransportUnits == 0)
			return new int[sectoralsCount];
		// Si el indice de fase supera los disponibles, retorna el maximo
		else if (phase > PUBLIC_TRANSPORT_QUANTIFICATION[regionType].length)
			return getPTSectoralUnits(publicTransportUnits);
		// Calcula la cantidad en funcionamiento segun fase
		else {
			int units = (int) Math.round(publicTransportUnits * PUBLIC_TRANSPORT_QUANTIFICATION[regionType][phase]);
			if (units > publicTransportUnits)
				units = publicTransportUnits;
			return getPTSectoralUnits(units);
		} 
	}
}
