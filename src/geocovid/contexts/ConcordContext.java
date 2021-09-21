package geocovid.contexts;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.MarkovChains;
import geocovid.PlaceProperty;
import geocovid.Town;

/**
 * Implementacion de <b>SubContext</b> para municipios tipo Concordia.
 */
public class ConcordContext extends SubContext {
	// Punteros matriz markov //
	private static int[][][][][] localTMMC = new int[2][DataSet.AGE_GROUPS][4][4][4];
	private static int[][][][] isolatedLocalTMMC = new int[DataSet.AGE_GROUPS][4][4][4];
	
	//public static int[][][] travelerTMMC;
	//public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	private static final double[] HOUSE_INHABITANTS_MEAN = {3.7, 4.1};

	/** Area en m2 para hogares */
	private static final int[] HOME_BUILDING_AREA = {100, 100};
	/** Area construida en m2 para hogares */
	private static final int[] HOME_BUILDING_COVERED_AREA = {80, 80};

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	private static final double[][] LOCAL_HUMANS_PER_AGE_GROUP = {
			{ 0d, 24d, 38d, 38d, 0d},	// Seccional 2
			{ 0d, 20d, 40d, 40d, 0d}	// Seccional 11
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	private static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 20d, 35d, 35d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	private static final double[][][] OCCUPATION_PER_AGE_GROUP = { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
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
	private static final int[] WORKING_FROM_HOME = {5, 12};	// 10% de poblacion activa
	/** Porcentaje que trabaja al exterior */
	private static final int[] WORKING_OUTDOORS = {4, 6};	// menos del 5%

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	private static final int[] TRAVEL_OUTSIDE_CHANCE = {50, 40};	// Se desplazan mas de 1500 metros el 60% de la poblacion (bajamos un poco por la epidemia)
	
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
			buildingManager.closePlaces(
					// Trabajo/estudio
					"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
					// Ocio
					"movie_theater", "sports_complex", "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" );
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			setSocialDistancing(95);
			setMaskEffectivity(0.30);
			// Se inicia Junio sin ventilacion en hogares, oficinas y ocio 
			buildingManager.ventilateHomes(false);
			buildingManager.ventilateWorkplaces(false);
			buildingManager.ventilateEntertainmentPlaces(false);
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			buildingManager.openPlaces("sports_school", "gym", "sports_club", "church");
			buildingManager.limitActivitiesCapacity(3d);
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			break;
		case 254: // 11 septiembre
			// Desde Septiembre que termina la fresca vuelven a ventilar hogares
			buildingManager.ventilateHomes(true);
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			//
			setSocialDistancing(60);
			break;
		case 257: // 14 septiembre
			buildingManager.ventilateEntertainmentPlaces(true);
			buildingManager.limitActivitiesCapacity(1.2d);
			break;
		case 264: // 21 septiembre
			buildingManager.openPlaces("sports_club", "sports_complex", "park");
			break;
		case 281: // 9 octubre
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.limitActivitiesCapacity(1d);
			setSocialDistancing(20);
			break;
		case 302: // 29 octubre
			buildingManager.openPlaces("casino", "nursery_school", "association_or_organization");
			buildingManager.limitActivitiesCapacity(1d);
			setMaskEffectivity(0.25);
			setSocialDistancing(20);
			break;
		case 310: // 6 noviembre
			buildingManager.limitActivitiesCapacity(1.4d);
			setSocialDistancing(25);
			break;
		case 343: // 9 diciembre
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.openPlaces("bus_station", "lodging", "childrens_party_service", "night_club", "tourist_attraction", "campground", "spa");
			// Festejos cada 7 dias entre jovenes - 1% de la poblacion a 1 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.getLocalPopulation() * 0.01d);
			startRepeatingYoungAdultsParty(7, tmp, 1d, true, true);
			break;
		case 348: // 14 diciembre
			// Aumentan las compras por las fiestas
			setSocialDistancing(25);
			buildingManager.limitActivitiesCapacity(1.5d);
			// Verano sin ventilacion en hogares 
			buildingManager.ventilateHomes(false);
			break;
		case 358: // 24 diciembre
			// Aumentan mas las compras y las juntadas por las fiestas
			buildingManager.limitActivitiesCapacity(1.3d);
			setMaskEffectivity(0.15);
		case 365: // 31 diciembre
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.getLocalPopulation() / 15 * 0.8d);
			scheduleForcedEvent(8, true, true, tmp, 15, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 366: // 1 enero
			buildingManager.limitActivitiesCapacity(2d);
		case 359: // 25 diciembre
			// Festejos entre jovenes - 4% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.getLocalPopulation() * 0.04d);
			scheduleYoungAdultsParty(tmp, 1d, true, false);
			break;
		case 376: // 11 enero
			// Merma el movimiento en Enero
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(2.1d, 2.1d);
			break;
		case 397: // 1 febrero
			// Aumenta un poco el movimiento en Febrero
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			buildingManager.limitEntertainmentActCap(1.5d); 
			break;
		case 411: // 15 febrero
			buildingManager.limitEntertainmentActCap(3d);
			buildingManager.openPlaces("movie_theater", "cultural_center", "art_gallery", "club");
			break;
		case 425: // 1 marzo
			// Fin verano sin ventilacion en hogares, y ahora se cumple en lugares de trabajo
			buildingManager.ventilateHomes(true);
			buildingManager.ventilateWorkplaces(true);
			// Aumenta el movimiento, casi vida normal
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitOtherActCap(3.5d);
			buildingManager.openPlaces("library", "school","primary_school", "secondary_school");
			setSchoolProtocol(true);//habilita protocolo burbuja 50% 
			break;
		case 435: // 11 marzo
			// Aumenta el ocio 
			buildingManager.limitEntertainmentActCap(1.6d);
			buildingManager.openPlaces("movie_theater");
			break;
		case 445: // 21 marzo
			// Aumenta un poco mas el ocio 
			buildingManager.limitEntertainmentActCap(1.3d);
			break;
		case 459: // 4 abril - domingo
			// Vuelven a controlar aforo
			buildingManager.limitEntertainmentActCap(1d);
			setMaskEffectivity(0.25);
			// Almuerzo domingo de pascuas - 50% de la poblacion dividida en grupos de 10 personas, todas adentro
			tmp = (int) Math.round(town.getLocalPopulation() / 10 * 0.5d);
			scheduleForcedEvent(7, false, true, tmp, 10, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 471: // 16 abril
			// Nuevas medidas
			//buildingManager.ventilateHomes(false);
			setSocialDistancing(20);
			buildingManager.limitEntertainmentActCap(1.5d);
			// Merman las jodas, por que vuelven a controlar
			stopRepeatingYoungAdultsParty();
			break;
		case 487: // 2 mayo , nuevas restricciones
			buildingManager.closePlaces("library", "school","primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			buildingManager.limitEntertainmentActCap(2d);
			break;
		case 494: // 9 mayo , fin de nuevas restricciones
			buildingManager.limitEntertainmentActCap(2.4d);
			buildingManager.openPlaces("library", "school","primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			break;
		case 507: // 22 mayo , nuevas restricciones fase1
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitEntertainmentActCap(2.6d);
			buildingManager.closePlaces(
			// Trabajo/estudio
			"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
			// Ocio
			"movie_theater", "bar", "sports_complex", "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
			 "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery");
			break;
		case 516: // 31 mayo , fin de nuevas restricciones fase 1
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitEntertainmentActCap(2.6d);
			buildingManager.openPlaces(
			// Ocio
			"bar", "sports_complex", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym",
			"stadium", "sports_club", "park", "library", "cultural_center", "club");
			break;
		default:
			throw new InvalidParameterException("Dia de fase no implementada: " + phase);
		}
	}
	
	/** 
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.
	 */
	public static void setDefaultTMMC() {
		if (!"default".equals(currentMonth)) {
			//travelerTMMC = MarkovChains.TRAVELER_DEFAULT_TMMC;
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				isolatedLocalTMMC[i] = MarkovChains.ISOLATED_TMMC;
			}
			//infectedTravelerTMMC = MarkovChains.ISOLATED_TMMC;
			setTMMCs("default", MarkovChains.DEFAULT_TMMC);
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
			MarkovChains.cloneChains(tmmc, localTMMC[0]);
			MarkovChains.mergeChainsDiff(tmmc, MarkovChains.SEC11_DIFF, localTMMC[1]);
			// Chequea si se cambio de fase durante el fin de semana
			if (weekendTMMCEnabled) {
				// Aplica diff de fin de semana a nuevas markovs
				weekendTMMCEnabled = false;
				setHumansWeekendTMMC(true);
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
			//travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
		}
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