package geocovid.contexts;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.MarkovChains;
import geocovid.PlaceProperty;
import geocovid.Town;

/**
 * Implementacion de <b>SubContext</b> para municipios tipo Gualeguaychu.
 */
public class GchuContext extends SubContext {
	// Punteros matriz markov //
	private static int[][][][][] localTMMC = new int[2][DataSet.AGE_GROUPS][4][4][4];
	private static int[][][][] isolatedLocalTMMC = new int[DataSet.AGE_GROUPS][4][4][4];
	
	//public static int[][][] travelerTMMC;
	//public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	private static final double[] HOUSE_INHABITANTS_MEAN = {3, 0};

	/** Area en m2 para hogares */
	private static final int[] HOME_BUILDING_AREA = {100, 0};
	/** Area construida en m2 para hogares */
	private static final int[] HOME_BUILDING_COVERED_AREA = {80, 0};

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	private static final double[][] LOCAL_HUMANS_PER_AGE_GROUP = {
			{ 0d, 24d, 38d, 38d, 0d},	// Seccional 2
			{ 0d,  0d,  0d,  0d, 0d}	// Seccional 11
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	private static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 26d, 32d, 32d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	private static final double[][][] OCCUPATION_PER_AGE_GROUP = { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
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
	private static final int[] WORKING_FROM_HOME = {10, 0};	// 02: 10% de poblacion activa
	/** Porcentaje que trabaja al exterior */
	private static final int[] WORKING_OUTDOORS = {5, 0};	// 02: menos del 5%

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	private static final int[] TRAVEL_OUTSIDE_CHANCE = {45, 0};	// Se desplazan mas de 1500 metros el 47% de la población
	
	/** % de casos graves en UTI que mueren al terminar periodo de internacion */
	public static final double	ICU_DEATH_RATE = 75d; // ma mortalida Gchu
	
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
			buildingManager.closePlaces(
					// Trabajo/estudio
					"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
					// Ocio
					"movie_theater", "bar", "sports_complex", "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"restaurant", "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" );
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(1d);
			setSocialDistancing(70);
			setMaskEffectivity(0.25);
			// Se inicia Junio sin ventilacion en hogares, oficinas y ocio 
			buildingManager.ventilateHomes(false);
			buildingManager.ventilateWorkplaces(false);
			buildingManager.ventilateEntertainmentPlaces(false);
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			buildingManager.limitActivitiesCapacity(1.7);
			buildingManager.openPlaces("bar", "restaurant", "sports_school", "gym", "sports_club");
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			setSocialDistancing(60);
			buildingManager.limitActivitiesCapacity(2.9);
			break;
		case 229: // 17 agosto
			// Nueva normalidad (Fase 5)
			buildingManager.limitActivitiesCapacity(3.4);
			break;
		case 244: // 31 agosto - solo Gchu
			// Vuelta a atras por saturacion de sistema sanitario (Fase 4)
			buildingManager.closePlaces("bar", "restaurant", "sports_school", "gym", "sports_club", "park");
			buildingManager.limitActivitiesCapacity(2.8);
			break;
		case 254: // 11 septiembre
			// Desde Septiembre que termina la fresca vuelven a ventilar hogares
			buildingManager.ventilateHomes(true);
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			//
			setSocialDistancing(50);
			buildingManager.limitActivitiesCapacity(3d);
			break;
		case 257: // 14 septiembre
			// A partir de ahora si ventilan en ocio
			buildingManager.ventilateEntertainmentPlaces(true);
			buildingManager.openPlaces("bar", "restaurant", "sports_school", "gym", "sports_club");
			break;
		case 264: // 21 septiembre
			buildingManager.openPlaces("sports_club", "church", "sports_complex", "park");
			break;
		case 273: // 1 octubre
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			setSocialDistancing(30);
			buildingManager.limitActivitiesCapacity(1.5);
			break;
		case 302: // 29 octubre
			buildingManager.openPlaces("casino", "nursery_school", "association_or_organization");
			buildingManager.limitActivitiesCapacity(1.4);
			setSocialDistancing(25);
			break;
		case 310: // 6 noviembre
			setMaskEffectivity(0.25);
			buildingManager.limitActivitiesCapacity(2d);
			break;
		case 343: // 9 diciembre
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.limitActivitiesCapacity(1.9);
			buildingManager.openPlaces("bus_station", "lodging", "childrens_party_service", "night_club", "tourist_attraction", "campground", "spa");
			// Festejos entre jovenes - 1% de la poblacion a 1 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans * 0.01d);
			startRepeatingYoungAdultsParty(7, tmp, 1d, true, true);
			break;
		case 348: // 14 diciembre
			// Verano sin ventilacion en hogares 
			buildingManager.ventilateHomes(false);
			setSocialDistancing(20);
			buildingManager.limitActivitiesCapacity(1.8);
			break;
		case 358: // 24 diciembre
			// Aumentan mas las compras y las juntadas por las fiestas
			buildingManager.limitActivitiesCapacity(1.2, 1.8);
			setSocialDistancing(10);
			setMaskEffectivity(0.15);
		case 365: // 31 diciembre
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans / 15 * 0.8d);
			scheduleForcedEvent(8, true, true, tmp, 15, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 366: // 1 enero
			buildingManager.limitActivitiesCapacity(1.8);
			// Periodo turistico todo el mes de Enero - 2.5% de la poblacion, 1% infectados
			tmp = (int) Math.round(town.localHumans * 0.025d);
			setTouristSeason(30, 3, tmp, 0.01d); // 30 dias, 3 dias recambio
		case 359: // 25 diciembre
			// Festejos entre jovenes - 8% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.localHumans * 0.08d);
			scheduleYoungAdultsParty(tmp, 1d, true, false);
			break;
		case 376: // 11 enero
			// Merma el movimiento en Enero
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(1.75, 2d);
			break;
		case 397: // 1 febrero
			// Aumenta un poco el movimiento en Febrero
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			buildingManager.limitEntertainmentActCap(2.7); // mas actividades afuera
			break;
		case 411: // 15 febrero
			buildingManager.limitActivitiesCapacity(3.5); // para que bajen los casos
			buildingManager.openPlaces("movie_theater", "cultural_center", "art_gallery", "club");
			break;
		case 425: // 1 marzo
			// Fin verano sin ventilacion en hogares, y ahora se cumple en lugares de trabajo
			buildingManager.ventilateHomes(true);
			buildingManager.ventilateWorkplaces(true);
			// Aumenta el movimiento, casi vida normal
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitActivitiesCapacity(1.7, 2.2);
			buildingManager.openPlaces("library", "school","primary_school", "secondary_school");
			setSchoolProtocol(true); // habilita protocolo burbuja 50%
			break;
		case 435: // 11 marzo
			// Aumenta el ocio 
			buildingManager.limitActivitiesCapacity(1.5, 1.75);
			buildingManager.openPlaces("movie_theater");
			break;
		case 445: // 21 marzo
			// Aumenta un poco mas el ocio 
			buildingManager.limitActivitiesCapacity(1.25, 1.3);
			break;
		case 456: // 1 abril - jueves
			// Inicio Semana Santa
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.limitActivitiesCapacity(1d, 1);
			break;
		case 459: // 4 abril - domingo
			// Almuerzo domingo de pascuas - 50% de la poblacion dividida en grupos de 10 personas, todas adentro
			tmp = (int) Math.round(town.localHumans / 10 * 0.5d);
			scheduleForcedEvent(7, false, true, tmp, 10, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 460: // 1 abril - jueves
			// Fin Semana Santa
			setTMMCs("default", MarkovChains.DEFAULT_TMMC);
			break;
		case 471: // 16 abril
			// Nuevas medidas
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.ventilateHomes(false);
			buildingManager.limitActivitiesCapacity(1.75, 1.8);
			// Merman las jodas, por que vuelven a controlar
			stopRepeatingYoungAdultsParty();
			break;
		case 487: // 2 mayo - nuevas restricciones
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.limitActivitiesCapacity(2.25, 1.9);
			buildingManager.closePlaces("library", "school","primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			break;
		case 494: // 9 mayo - fin de nuevas restricciones
			buildingManager.limitActivitiesCapacity(1.75, 2);
			buildingManager.openPlaces("library", "school", "primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			break;
		case 507: // 22 mayo - nuevas restricciones fase 1
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			buildingManager.limitActivitiesCapacity(2.25, 2.4);
			buildingManager.closePlaces(
			// Trabajo/estudio
			 "primary_school", "secondary_school", "university",
			// Ocio
			"movie_theater", "school",  "childrens_party_service", "church", "spa", "night_club", "gym", 
			"cultural_center", "club", "casino", "campground", "art_gallery");
			break;
		case 516: // 31 mayo - fin de nuevas restricciones fase 1
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.limitActivitiesCapacity(1.75, 1.2);
			buildingManager.openPlaces(
			// Ocio
			"church", "spa", "park", "cultural_center"); // "childrens_party_service" no?
			break;
		case 523: // 7 junio  
			buildingManager.openPlaces("movie_theater", "gym");
			break;
		case 527: // 11 junio
			buildingManager.limitActivitiesCapacity(1.25, 1.4d);
			buildingManager.openPlaces("primary_school");
			break;
		case 538: // 22 junio
			buildingManager.openPlaces("secondary_school");
			buildingManager.limitEntertainmentActCap(1.7);//1.5
			break;
		case 556: // 10 julio sabado
			// Inicio receso escolar de invierno, 2 semanas
			buildingManager.closePlaces("school", "primary_school", "secondary_school");
			buildingManager.limitEntertainmentActCap(1.6);
			break;
		case 572: // 26 julio lunes
			// Fin receso escolar de invierno
			buildingManager.openPlaces("school", "primary_school", "secondary_school");
			buildingManager.limitEntertainmentActCap(1.4);
			setMaskEffectivity(0.20);
			setSocialDistancing(10);
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			break;
		case 583: // 6 agosto viernes
			setTMMCs("default", MarkovChains.DEFAULT_TMMC);
			buildingManager.limitEntertainmentActCap(1.25);
			//se mantiene las mismas restriccones , se aumenta el aforo en algunas actividades, 
			//falta habilitra "night_club", "stadium","childrens_party_service,"club""
			//https://www.argentina.gob.ar/noticias/nuevas-disposiciones-sanitarias-ante-pandemia-de-covid-19
			break;
		case 607: // 30 agosto lunes
			// Presencialidad plena en escuelas
			//setSchoolProtocol(false);
			// Ventila hogares desde Septiembre
			buildingManager.ventilateHomes(true);
			break;
		case 640: // 2 octubre sabado
			// Distancia-que???
			setSocialDistancing(0);
			buildingManager.openPlaces("night_club", "stadium","childrens_party_service","club");
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
	@Override
	public double getICUDeathRate() { return ICU_DEATH_RATE; }
}
