package geocovid;

import java.security.InvalidParameterException;

/**
 * Contiene las caracteristicas habitacionales de cada ciudad y los dias de cambio de fases.
 */
public final class Town {
	/** Dias de delay en la entrada de infectados */
	public static int outbreakStartDelay = 0;
	/** Cantidad de infectados iniciales en cada municipio */
	public static int firstInfectedAmount = 1;
	/** Dias por defecto de cambio de fase */
	private static final int[][] DEFAULT_PHASES_DAYS = {
		{163,201,215,229,254,257,264,273,302,310,343,348,358,359,365,366,388,411,425,435},	// pna
		{163,201,215,229,254,257,264,273,302,310,343,348,358,359,365,366,388,411,425},		// gchu
		{163,201,215,    254,257,264,281,302,310,343,348,358,359,365,366,388,411,425}		// concord
	};
	/** Dias por defecto de los cambios de unidades de transporte publico
	 * [enero, febrero, marzo, abril, mayo, junio, julio, agosto, septiembre, octubre, noviembre, diciembre - 2020] */

	public final double[] PUBLIC_TRANSPORT_QUALIFICATION		= {0.22, 0.28, 0.34, 0.4, 0.45, 0.51, 0.75};
	
	/** Cantidad maxima de transporte en Parana del municipio a simular */
	public static final int	PUBLIC_TRANSPORT_MAX_UNIT				= 140;	// Parana

	/** Nombre del municipio a simular */
	public String townName;
	
	/** Tipo de ciudad (0 parana, 1 gchu o 2 concord) */ 
	public int regionType;
	/** Indice de region de la ciudad (0 sur, 1 centro o 2 norte) */ 
	public int regionIndex;
	
	/** Cantidad de Humanos locales (no salen a trabajar) */
	public int localHumans;
	/** Cantidad de Humanos que trabajan afuera */
	public int localTravelerHumans;
	/** Cantidad de Humanos que viven afuera */
	public int foreignTravelerHumans;
	
	/** Tipo de seccional segun indice: 0 tipo 2 | 1 tipo 11 */
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
	
	/** Si existe transporte publico en ciudad (colectivo) */
	public boolean publicTransportAllowed;
	/** Si la ciudad tiene una temporada turistica */
	public boolean touristSeasonAllowed;
	
	public Town(String name) {
		this.setTown(name);
	}
	
	private void setTownData(int regionTyp, int regionIdx, int locals, int travelers, int foreign, int[] secTypes, double[] secPop, int[] phasesDays, int obStartDay, boolean pubTransAllowed, boolean tourSeasonAllowed) {
		regionType = regionTyp;
		regionIndex = regionIdx;
		//
		localHumans = locals;
		localTravelerHumans = travelers;
		foreignTravelerHumans = foreign;
		//
		sectoralsTypes = secTypes;
		sectoralsPopulation = secPop;
		sectoralsCount = secTypes.length;
		//
		lockdownPhasesDays = phasesDays;
		outbreakStartDay = obStartDay + outbreakStartDelay;
		//
		publicTransportAllowed = pubTransAllowed;
		touristSeasonAllowed = tourSeasonAllowed;
	}
	
	public int getLocalPopulation() {
		return localHumans + localTravelerHumans;
	}
	
	public String getPlacesPropertiesFilepath() {
		String type;
		switch (regionType) {
		case 0:
			type = "parana";
			break;
		case 1:
			type = "gchu";
			break;
		default:
			type = "concord";
			break;
		}
		return String.format("./data/%s-places-markov.csv", type);
	}
	
	public String getParcelsFilepath() {
		return String.format("./data/%1$s/%1$s.shp", townName);
	}
	
	public String getPlacesFilepath() {
		return String.format("./data/%s/places.shp", townName);
	}
	
	/**
	 * Si cambia la ciudad, carga sus atributos.
	 * @param name nombre de ciudad
	 * @return <b>true</b> si se cambio de ciudad
	 */
	public boolean setTown(String name) {
		// Chequeo si es la misma
		if (name.equals(townName))
			return false;
		townName = name;
		//
		switch (townName) {
		// Tipos Parana
		case "parana": // 256800
			setTownData(
				0,1,
				236000,
				20800,
				0,
				new int[] {0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
				new double[] {6.6, 5.5, 3.8, 6.3, 5.1, 1.9, 1.0, 11.9, 2.2, 3.0, 6.1, 9.5, 10.5, 1.0, 8.3, 6.5, 8.6, 2.2},
				new int[] {163,182,201,215,229,244,254,257,264,273,302,310,343,348,358,359,365,366,388,411,425,435},
				182,
				true,
				false);
			break;
		case "gualeguay": // 45280
			setTownData(
				0,0,
				40780,
				4500,
				0,
				new int[] {0, 0, 1, 1},
				new double[] {26.12, 23.88, 26.08, 23.92},
				DEFAULT_PHASES_DAYS[0],
				216,
				false,
				false);
			break;
		case "diamante": // 20640
			setTownData(
				0,1,
				18600,
				2040,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 24.04, 25.96},
				DEFAULT_PHASES_DAYS[0],
				190,
				false,
				false);
			break;
		case "nogoya": // 23554
			setTownData(
				0,1,
				21200,
				2354,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 25.00, 25.00},
				DEFAULT_PHASES_DAYS[0],
				194,
				false,
				false);
			break;
		case "victoria": // 32995
			setTownData(
				0,0,
				29700,
				3295,
				0,
				new int[] {0, 0, 1, 1},
				new double[] {27.39, 22.61, 24.45, 25.55},
				DEFAULT_PHASES_DAYS[0],
				191,
				false,
				false);
			break;
		case "sansalvador": // 14060
			setTownData(
				0,1,
				12660,
				1400,
				0,
				new int[] {0, 1, 1},
				new double[] {50.00, 24.025, 25.975},
				DEFAULT_PHASES_DAYS[0],
				221,
				false,
				false);
			break;
		// Tipos Gualeguaychu
		case "gualeguaychu": // 87930
			setTownData(
				1,0,
				79130,
				8800,
				0,
				new int[] {0, 0, 0, 0, 0, 0, 0},
				new double[] {14.056, 15.25, 20.261, 18.111, 12.241, 8.93, 11.151},
				new int[] {163,201,215,229,244,254,257,264,273,302,310,343,348,358,359,365,366,388,425},
				182,
				true,
				true);
			break;
		case "uruguay": // 77290
			setTownData(
				1,0,
				69590,
				7700,
				1,
				new int[] {0, 0, 0, 0, 0},
				new double[] {12.86, 14.20, 12.925, 19.665, 40.35},
				DEFAULT_PHASES_DAYS[1],
				182,
				true,
				false);
			break;
		case "federacion": // 39120 - en realidad es Chajari
			setTownData(
				1,2,
				35220,
				3900,
				0,
				new int[] {0, 0, 0, 0},
				new double[] {30.65, 22.50, 22.70, 24.15},
				DEFAULT_PHASES_DAYS[1],
				182,
				false,
				true);
			break;
		case "colon": // 28432
			setTownData(
				1,1,
				25600,
				2832,
				0,
				new int[] {0, 0, 0, 0},
				new double[] {20.00, 14.18, 38.785, 27.035},
				DEFAULT_PHASES_DAYS[1],
				182,
				false,
				true);
			break;
		case "ibicuy": // 5086
			setTownData(
				1,0,
				4580,
				506,
				0,
				new int[] {0},
				new double[] {100},
				DEFAULT_PHASES_DAYS[1],
				182,
				false,
				true);
			break;
		// Tipos Concordia
		case "concordia": // 159680
			setTownData(
				2,1,
				143700,
				15980,
				0,
				new int[] {1, 1, 0, 0, 1, 1, 1, 1, 1, 1},
				new double[] { 3.39, 13.09, 16.71, 5.275, 5.6625, 19.26, 13.31, 6.8425, 13.17, 3.29},
				DEFAULT_PHASES_DAYS[2],
				195,
				true,
				false);
			break;
		case "lapaz": // 26054
			setTownData(
				2,2,
				23450,
				2604,
				0,
				new int[] {0, 1, 1},
				new double[] {23.18, 28.61, 48.21},
				DEFAULT_PHASES_DAYS[2],
				193,
				true,
				false);
			break;
		case "villaguay": // 34922
			setTownData(
				2,1,
				31400,
				3522,
				0,
				new int[] {0, 1, 1, 1, 1},
				new double[] {22.45, 22.02, 19.12, 15.38, 21.03},
				DEFAULT_PHASES_DAYS[2],
				225,
				false,
				false);
			break;
		case "federal": // 18380
			setTownData(
				2,2,
				16500,
				1880,
				0,
				new int[] {0, 1, 1, 1, 1},
				new double[] {22.45, 21.78, 16.94, 16.28, 22.55},
				DEFAULT_PHASES_DAYS[2],
				213,
				false,
				false);
			break;
		case "tala": // 13410
			setTownData(
				2,1,
				12050,
				1360,
				0,
				new int[] {0, 1, 1},
				new double[] {22.00, 40.65, 37.35},
				DEFAULT_PHASES_DAYS[2],
				235,
				false,
				false);
			break;
		case "feliciano": // 12350
			setTownData(
				2,2,
				11100,
				1250,
				0,
				new int[] {0, 1, 1},
				new double[] {22.00, 43.25, 34.75},
				DEFAULT_PHASES_DAYS[2],
				244,
				false,
				false);
			break;
		default:
			throw new InvalidParameterException("Ciudad erronea: " + townName);
		}
		return true;
	}
}
