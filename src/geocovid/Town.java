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
		{158,159,170,181,182,188,214,226,231,255,269,281,292,313,317,331,335,340,345,348,358,359,362,365,366,369,375,397,413,425,434,439,463,478,488,505,516,528,538},	// lacapital
		{0},// rosario TODO completar
		{0}	// ??????? TODO completar
	};
	
	/** Dias por defecto de los cambios de unidades de transporte publico */
	private final double[] PUBLIC_TRANSPORT_QUANTIFICATION	= {1, .6}; // 60% desde Abril 2020 hasta ahora (Junio 2021)
	
	/** Nombre del municipio a simular */
	public String townName;
	
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
	
	public Town(String name) {
		this.setTown(name);
	}
	
	private void setTownData(int regionTyp, int regionIdx, int locals, int travelers, int foreign, int[] secTypes, double[] secPop, int[] phasesDays, int obStartDay, int pubTransUnit, boolean tourSeasonAllowed) {
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
		publicTransportUnits = pubTransUnit;
		touristSeasonAllowed = tourSeasonAllowed;
	}
	
	public int getLocalPopulation() {
		return localHumans + localTravelerHumans;
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
			type = "???????";
			break;
		}
		return String.format("./data/%s-places-markov.csv", type);
	}
	
	public String getParcelsFilepath() {
		return String.format("./data/%s/parcels.shp", townName);
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
		// Tipos Santa Fe
		case "lacapital": // 398610
			setTownData(
				0,1,
				366610,
				32000,
				0,
				new int[] {0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1},
				new double[] {6.7, 6.05, 6.225, 5.625, 5.5, 7.3, 9.85, 9, 5.25, 8.3, 2.675, 2.6, 2.5, 8.15, 7.325, 3.45, 3.5},
				DEFAULT_PHASES_DAYS[0],
				194,
				200, // 200-210 coches en total, en los horarios pico
				false);
			break;
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
		else if (phase > PUBLIC_TRANSPORT_QUANTIFICATION.length)
			return getPTSectoralUnits(publicTransportUnits);
		// Calcula la cantidad en funcionamiento segun fase
		else {
			int units = (int) Math.round(publicTransportUnits * PUBLIC_TRANSPORT_QUANTIFICATION[phase]);
			if (units > publicTransportUnits)
				units = publicTransportUnits;
			return getPTSectoralUnits(units);
		} 
	}
}
