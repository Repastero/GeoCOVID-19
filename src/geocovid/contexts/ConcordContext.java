package geocovid.contexts;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.MarkovChains;
import geocovid.PlaceProperty;
import geocovid.Town;

/**
 * Implementacion de <b>SubContext</b> para municipios tipo Concordia.
 */
public class ConcordContext extends SubContext {
	// Punteros matriz markov //
	public static int[][][][][] localTMMC		= new int[2][DataSet.AGE_GROUPS][][][];
	public static int[][][][] isolatedLocalTMMC	= new int[DataSet.AGE_GROUPS][][][];
	
	public static int[][][] travelerTMMC;
	public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	public static final double[] HOUSE_INHABITANTS_MEAN	= {3.7, 4.1};

	/** Area en m2 para hogares */
	public static final int[] HOME_BUILDING_AREA = {100, 100};
	/** Area construida en m2 para hogares */
	public static final int[] HOME_BUILDING_COVERED_AREA = {80, 80};

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	public static final double[][] LOCAL_HUMANS_PER_AGE_GROUP	= {
			{ 0d, 24d, 38d, 38d, 0d},	// Seccional 2
			{ 0d, 20d, 40d, 40d, 0d}	// Seccional 11
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	public static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 20d, 35d, 35d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double[][][] OCCUPATION_PER_AGE_GROUP	= { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
			// Seccional 2 - 44% ocupados
			{{100d,   0d,   0d},	// 5-15
			{  62d,  18d,  20d},	// 16-25
			{  14d,  69d,  17d},	// 26-40
			{   0d,  80d,  20d},	// 41-64
			{   0d,   0d, 100d}},	// 65+
			// Seccional 11 - 35% ocupados
			{{100d,   0d,   0d},	// 5-15
			{  57d,   9d,  34d},	// 16-25
			{  10d,  56d,  34d},	// 26-40
			{   0d,  66d,  34d},	// 41-64
			{   0d,   0d, 100d}}	// 65+
			// 51% ocupados entre las 3 franjas activas
	};

	/** Porcentaje que trabaja en el hogar */
	public static final int[] WORKING_FROM_HOME	= { 5,12};	// 10% de poblacion activa
	/** Porcentaje que trabaja al exterior */
	public static final int[] WORKING_OUTDOORS	= { 4, 6};	// menos del 5%

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	public static final int[] TRAVEL_OUTSIDE_CHANCE	= {50, 40};	// Se desplazan mas de 1500 metros el 60% de la poblacion (bajamos un poco por la epidemia)
	
	private static Map<String, PlaceProperty> customPlacesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place
	private static String currentMonth = null;	// Para distinguir entre cambios de markovs
	private static boolean weekendTMMCEnabled = false;	// Flag fin de seamana
	
	public ConcordContext(Town contextTown) {
		super(contextTown);
		
		if (customPlacesProperty.isEmpty()) // Para no volver a leer si se reinicia simulacion, o si se crea otra instancia
			customPlacesProperty = PlaceProperty.loadPlacesProperties(town.getPlacesPropertiesFilepath());
		super.placesProperty = customPlacesProperty;
		
		// Setea valores por defecto de barbijo, distanciamiento y matrices de markov
		setDefaultValues();
		setDefaultTMMC();
		
		// Inicializar contexto
		super.init();
	}
	
	@Override
	public void updateLockdownPhase(int phase) {
		int tmp;
		switch (phase) {
		case 163: // 12 junio
			// Reapertura progresiva (Fase 4)
			buildingManager.closePlaces(new String[] {
					// Trabajo/estudio
					"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
					// Ocio
					"movie_theater", "sports_complex",  "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" });
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			setSocialDistancing(95);
			setMaskEffectivity(0.30);
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			buildingManager.openPlaces(new String[] {"sports_school", "gym", "sports_club", "church"});
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			break;
		case 254: // 11 septiembre
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			//
			setSocialDistancing(80);
			break;
		case 257: // 14 septiembre
			break;
		case 264: // 21 septiembre
			buildingManager.openPlaces(new String[] {"sports_club", "sports_complex", "park"});
			break;
		case 281: // 9 octubre
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.limitActivitiesCapacity(2.5d);
			setSocialDistancing(50);
			break;
		case 302: // 29 octubre
			buildingManager.openPlaces(new String[] {"casino", "nursery_school", "association_or_organization"});
			buildingManager.limitActivitiesCapacity(1.5d);
			setMaskEffectivity(0.25);
			setSocialDistancing(40);
			break;
		case 310: // 6 noviembre
			setSocialDistancing(30);
			break;
		case 343: // 9 diciembre
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.openPlaces(new String[] {"bus_station", "lodging", "childrens_party_service", "night_club", "tourist_attraction", "campground", "spa"});
			// Festejos entre jovenes - 1% de la poblacion a 1 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.getLocalPopulation() * 0.01d);
			startRepeatingYoungAdultsParty(7, tmp, 1d, true, true);
			break;
		case 348: // 14 diciembre
			setSocialDistancing(20);
			break;
		case 358: // 24 diciembre
			buildingManager.limitActivitiesCapacity(1d);
			setMaskEffectivity(0.15);
		case 365: // 31 diciembre
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.getLocalPopulation() / 15 * 0.8d);
			scheduleForcedEvent(16, true, true, tmp, 15, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 366: // 1 enero
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.limitActivitiesCapacity(1.75d);
		case 359: // 25 diciembre
			// Festejos entre jovenes - 4% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.getLocalPopulation() * 0.04d);
			scheduleYoungAdultsParty(tmp, 1d, true, false);
			break;
		case 388: // 23 enero
			buildingManager.limitOtherActCap(2.5d);
			buildingManager.limitEntertainmentActCap(2d);
			break;
		case 411: // 15 febrero
			buildingManager.limitEntertainmentActCap(2.5d);
			//buildingManager.limitActivitiesCapacity(3d); // para que bajen los casos
			buildingManager.openPlaces(new String[] {"movie_theater", "cultural_center", "art_gallery", "club"});
			break;
		case 425: // 1 marzo
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.openPlaces(new String[] {"library", "school"});
			// quedan cerrados -> university,stadium,primary_school,secondary_school
			break;
		default:
			throw new InvalidParameterException("Dia de fase no implementada: " + phase);
		}
	}
	
	/**
	 * Como la simulacion puede comenzar antes de la pandemia se inicia sin medidas de prevencion.
	 */
	public void setDefaultValues() {
		setMaskValues(0, false, false, true);
		setSDValues(0, true, false);
		disableCloseContacts();
		disablePrevQuarantine();
	}
	
	/** 
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.
	 */
	public static void setDefaultTMMC() {
		if (!"default".equals(currentMonth)) {
			setTMMCs("default", MarkovChains.DEFAULT_TMMC);
			
			travelerTMMC	= MarkovChains.TRAVELER_DEFAULT_TMMC;
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				isolatedLocalTMMC[i] = MarkovChains.ISOLATED_TMMC;
			}
			infectedTravelerTMMC = MarkovChains.ISOLATED_TMMC;
		}
	}
	
	/**
	 * Asignar las matrices de markov dadas, si cambiaron.
	 * @param month mes o tipo
	 * @param tmmc nueva matriz
	 */
	public static void setTMMCs(String month, int[][][][] tmmc) {
		if (!month.equals(currentMonth)) {
			currentMonth = month;
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				localTMMC[0][i]	= tmmc[i];
				localTMMC[1][i]	= MarkovChains.mergeChainsDiff(tmmc[i], MarkovChains.SEC11_DIFF[i]);
			}
		}
	}
	
	/**
	 * Habilitar o deshabilitar las matrices de markov que se utilizan los fines de semana, para los primeros 4 grupos etarios.
	 * @param enabled inicio o fin de fin de semana
	 */
	public static void setHumansWeekendTMMC(boolean enabled) {
		if (weekendTMMCEnabled != enabled) {
			weekendTMMCEnabled = enabled;
			for (int i = 0; i < DataSet.AGE_GROUPS-1; i++) {
				MarkovChains.setWeekendDiff(localTMMC[0][i], enabled);
				MarkovChains.setWeekendDiff(localTMMC[1][i], enabled);
			}
			travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
		}
	}
	
	/**
	 * Antes de setear las caracteristicas de una nueva fase, chequea si sucede el fin de semana.
	 * @param phaseDay dia nueva fase
	 */
	public void initiateLockdownPhase(int phaseDay) {
		boolean lockdownOverWKD = false;
		// Chequea si se cambio de fase durante el fin de semana y no es la primer fase
		if (weekendTMMCEnabled && InfectionReport.simulationStartDay != phaseDay) {
			lockdownOverWKD = true;
			setHumansWeekendTMMC(false); // para restar la matriz de finde
		}
		updateLockdownPhase(phaseDay);
		// Si corresponde, suma la matriz de fin de semana a las nuevas matrices
		if (lockdownOverWKD)
			setHumansWeekendTMMC(true);  // para sumar la matriz de finde
	}
	
	@Override
	public double[][] getOccupationPerAG(int secType) { return OCCUPATION_PER_AGE_GROUP[secType]; }
	@Override
	public int getWorkingFromHome(int secType) { return WORKING_FROM_HOME[secType]; }
	@Override
	public int getWorkingOutdoors(int secType) { return WORKING_OUTDOORS[secType]; }
	@Override
	public double getHouseInhabitantsMean(int secType) { return HOUSE_INHABITANTS_MEAN[secType]; }

	@Override
	public double[] getLocalHumansPerAG(int secType) { return LOCAL_HUMANS_PER_AGE_GROUP[secType]; }
	@Override
	public double[] getForeignHumansPerAG() { return FOREIGN_HUMANS_PER_AGE_GROUP; }

	@Override
	public int getHomeBuldingArea(int secType) { return HOME_BUILDING_AREA[secType]; }
	@Override
	public int getHomeBuldingCoveredArea(int secType) { return HOME_BUILDING_COVERED_AREA[secType]; }

	@Override
	public int travelOutsideChance(int secType) { return TRAVEL_OUTSIDE_CHANCE[secType]; }

	@Override
	public int[][][] getIsolatedLocalTMMC(int ageGroup) { return isolatedLocalTMMC[ageGroup]; }
	@Override
	public int[][][] getLocalTMMC(int sectoralType, int ageGroup) { return localTMMC[sectoralType][ageGroup]; }
}
