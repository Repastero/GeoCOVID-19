package geocovid;

import java.security.InvalidParameterException;

public final class Town {
	/** Nombre del municipio a simular */
	private static String townName;
	/** Dias por defecto de cambio de fase */
	private static final int[] DEFAULT_PHASES_DAYS = new int[] {163,201,215,229,254,257,264,273,302,310,343,358,359,365,366,374,388,411};
	
	/** Cantidad de Humanos locales (no salen a trabajar) */
	public static int localHumans;
	/** Cantidad de Humanos que trabajan afuera */
	public static int localTravelerHumans;
	/** Cantidad de Humanos que viven afuera */
	public static int foreignTravelerHumans;
	
	/** Tipo de seccional segun indice: 0 tipo 2 | 1 tipo 11 */
	public static int[] sectoralsTypes;
	/** Porcentaje de la poblacion en cada seccional (segun cantidad de parcelas) */
	public static double[] sectoralsPopulation;
	public static int sectoralsCount;
	
	/** Dias desde el 01-01-2020 donde ocurre el cambio de fase<p>
	 * Para calcular dias entre fechas o sumar dias a fecha:<p>
	 * @see <a href="https://www.timeanddate.com/date/duration.html?d1=1&m1=1&y1=2020&">Dias entre fechas</a>
	 * @see <a href="https://www.timeanddate.com/date/dateadd.html?d1=1&m1=1&y1=2020&">Sumar dias a fecha</a>
	 */
	public static int[] lockdownPhasesDays;	
	
	/** Indice de region de la ciudad (0 sur, 1 centro o 2 norte) */ 
	public static int regionIndex;
	
	/** Chance de contagio al realizar una actividad fuera del contexto */
	public static int oocContagionValue;
	
	/* Si existe transporte publico en ciudad (colectivo) */
	public static boolean publicTransportAllowed;
	
	private static void setTownData(int locals, int travelers, int foreign, int[] secTypes, double[] secPop, int[] phasesDays, int regionIdx, int oocContagion, boolean pubTransAllowed) {
		localHumans = locals;
		localTravelerHumans = travelers;
		foreignTravelerHumans = foreign;
		//
		sectoralsTypes = secTypes;
		sectoralsPopulation = secPop;
		sectoralsCount = secTypes.length;
		//
		lockdownPhasesDays = phasesDays;
		//
		regionIndex = regionIdx;
		//
		oocContagionValue = oocContagion;
		//
		publicTransportAllowed = pubTransAllowed;
	}
	
	public static int getLocalPopulation() {
		return localHumans + localTravelerHumans;
	}
	
	public static String getPlacesPropertiesFilepath() {
		return "./data/parana-places-markov.csv";
	}
	
	public static String getParcelsFilepath() {
		return String.format("./data/%1$s/%1$s.shp", townName);
	}
	
	public static String getPlacesFilepath() {
		return String.format("./data/%s/places.shp", townName);
	}
	
	public static String getWeatherFilepath(int year) {
		String regionName;
		switch (regionIndex) {
		case 0:
			regionName = "sur";
			break;
		case 2:
			regionName = "norte";
			break;
		default:
			regionName = "centro";
			break;
		}
		return String.format("./data/%d-%s.csv", year, regionName);
	}
	
	public static void setTown(String name) {
		townName = name;
		switch (townName) {
		case "parana":
			setTownData(
				236000,
				20800,
				0,
				new int[] {0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
				new double[] {6.6, 5.5, 3.8, 6.3, 5.1, 1.9, 1.0, 11.9, 2.2, 3.0, 6.1, 9.5, 10.5, 1.0, 8.3, 6.5, 8.6, 2.2},
				new int[] {163,182,201,215,229,244,254,257,264,273,302,310,343,358,359,365,366,374,388,411},
				1,
				3850,
				true);
			break;
		case "gualeguay":
			setTownData(
				40780,
				4500,
				0,
				new int[] {0, 0, 1, 1},
				new double[] {26.12, 23.88, 26.08, 23.92},
				DEFAULT_PHASES_DAYS,
				0,
				3850,
				false);
			break;
		case "diamante":
			setTownData(
				18600,
				2040,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 24.04, 25.96},
				DEFAULT_PHASES_DAYS,
				1,
				3850,
				false);
			break;
		case "nogoya":
			setTownData(
				21200,
				2354,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 25.00, 25.00},
				DEFAULT_PHASES_DAYS,
				1,
				3850,
				false);
			break;
		case "victoria":
			setTownData(
				29700,
				3295,
				0,
				new int[] {0, 0, 1, 1},
				new double[] {27.39, 22.61, 24.45, 25.55},
				DEFAULT_PHASES_DAYS,
				0,
				3850,
				false);
			break;
		case "sansalvador":
			setTownData(
				12660,
				1400,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 24.025, 25.975},
				DEFAULT_PHASES_DAYS,
				1,
				3850,
				false);
			break;
		default:
			throw new InvalidParameterException("Ciudad erronea: " + townName);
		}
	}
}
