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
 * Implementacion de <b>SubContext</b> para municipios tipo Gualeguaychu.
 */
public class GchuContext extends SubContext {
	// Punteros matriz markov //
	public static int[][][][][] localTMMC		= new int[2][DataSet.AGE_GROUPS][][][];
	public static int[][][][] isolatedLocalTMMC	= new int[DataSet.AGE_GROUPS][][][];
	
	public static int[][][] travelerTMMC;
	public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	public static final double[] HOUSE_INHABITANTS_MEAN	= {3, 0};

	/** Area en m2 para hogares */
	public static final int[] HOME_BUILDING_AREA = {100, 0};
	/** Area construida en m2 para hogares */
	public static final int[] HOME_BUILDING_COVERED_AREA = {80, 0};

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	public static final double[][] LOCAL_HUMANS_PER_AGE_GROUP	= {
			{ 0d, 24d, 38d, 38d, 0d},	// Seccional 2
			{ 0d,  0d,  0d,  0d, 0d}	// Seccional 11
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	public static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 26d, 32d, 32d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double[][][] OCCUPATION_PER_AGE_GROUP	= { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
			// Seccional 2 - 43.88% ocupados
			{{100d,   0d,   0d},	// 5-15
			{  60d,  18d,  22d},	// 16-25
			{  12d,  69d,  19d},	// 26-40
			{   0d,  80d,  20d},	// 41-64
			{   0d,   0d, 100d}},	// 65+
			// Seccional 11 - 0% ocupados
			{{0d, 0d, 0d},	// 5-15
			{ 0d, 0d, 0d},	// 16-25
			{ 0d, 0d, 0d},	// 26-40
			{ 0d, 0d, 0d},	// 41-64
			{ 0d, 0d, 0d}}	// 65+
			// 61% ocupados entre las 3 franjas activas
	};

	/** Porcentaje que trabaja en el hogar */
	public static final int[] WORKING_FROM_HOME	= {10, 0};	// 02: 10% de poblacion activa
	/** Porcentaje que trabaja al exterior */
	public static final int[] WORKING_OUTDOORS	= { 5, 0};	// 02: menos del 5%

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	public static final int[] TRAVEL_OUTSIDE_CHANCE	= {45, 0};	// Se desplazan mas de 1500 metros el 47% de la población

	/** % sobre 100 de que use el transporte publico al salir de seccional */
	public static final int	PUBLIC_TRANSPORT_CHANCE	= 10; // 12.4% utiliza el colectivo (uso menos por medidas en cuarentena) 
	/** Cantidad de unidades de transporte publico por seccional */
	public static final int	PUBLIC_TRANSPORT_UNITS	= 2;
	/** Cantidad de asientos en cada unidad de transorte publico */
	public static final int	PUBLIC_TRANSPORT_SEATS	= 20;
	
	private static Map<String, PlaceProperty> customPlacesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place
	private static String currentMonth = null;	// Para distinguir entre cambios de markovs
	private static boolean weekendTMMCEnabled = false;	// Flag fin de seamana
	
	public GchuContext(Town contextTown) {
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
					"movie_theater", "bar", "sports_complex",  "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"restaurant", "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" });
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(3d);
			setSocialDistancing(80);
			setMaskEffectivity(0.30);
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			buildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			setSocialDistancing(60);
			break;
		case 229: // 17 agosto
			// Nueva normalidad (Fase 5)
			break;
		case 244: // 31 agosto - solo Gchu
			// Vuelta a atras por saturacion de sistema sanitario (Fase 4)
			buildingManager.closePlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club", "park"});
			buildingManager.limitActivitiesCapacity(3d);
			break;
		case 254: // 11 septiembre
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			//
			setSocialDistancing(50);
			buildingManager.limitActivitiesCapacity(3.5d);
			break;
		case 257: // 14 septiembre
			buildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			break;
		case 264: // 21 septiembre
			buildingManager.openPlaces(new String[] {"sports_club", "church", "sports_complex", "park"});
			break;
		case 273: // 1 octubre
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			setSocialDistancing(30);
			buildingManager.limitActivitiesCapacity(2.5d);
			break;
		case 302: // 29 octubre
			buildingManager.openPlaces(new String[] {"casino", "nursery_school", "association_or_organization"});
			buildingManager.limitActivitiesCapacity(2d);
			setSocialDistancing(25);
			break;
		case 310: // 6 noviembre
			setMaskEffectivity(0.25);
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
			buildingManager.limitActivitiesCapacity(1.5d);
			break;
		case 358: // 24 diciembre
			setSocialDistancing(10);
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
			// Periodo turistico todo el mes de Enero - 2.5% de la poblacion, 1% infectados
			tmp = (int) Math.round(town.getLocalPopulation() * 0.025d);
			setTouristSeason(30, 3, tmp, 0.01d); // 30 dias, 3 dias recambio
		case 359: // 25 diciembre
			// Festejos entre jovenes - 8% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.getLocalPopulation() * 0.08d);
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
		weekendTMMCEnabled = enabled;
		for (int i = 0; i < DataSet.AGE_GROUPS-1; i++) {
			MarkovChains.setWeekendDiff(localTMMC[0][i], enabled);
			MarkovChains.setWeekendDiff(localTMMC[1][i], enabled);
		}
		travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
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
