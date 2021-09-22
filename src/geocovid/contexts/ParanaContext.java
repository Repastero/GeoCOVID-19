package geocovid.contexts;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.MarkovChains;
import geocovid.PlaceProperty;
import geocovid.Town;

/**
 * Implementacion de <b>SubContext</b> para municipios tipo Parana.
 */
public class ParanaContext extends SubContext {
	// Punteros matriz markov //
	private static int[][][][][] localTMMC = new int[2][DataSet.AGE_GROUPS][4][4][4];
	private static int[][][][] isolatedLocalTMMC = new int[DataSet.AGE_GROUPS][4][4][4];
	
	//public static int[][][] travelerTMMC;
	//public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	private static final double[] HOUSE_INHABITANTS_MEAN = {3.5, 5.5};

	/** Area en m2 para hogares */
	private static final int[] HOME_BUILDING_AREA = {125, 150};
	/** Area construida en m2 para hogares */
	private static final int[] HOME_BUILDING_COVERED_AREA = {100, 120};

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	private static final double[][] LOCAL_HUMANS_PER_AGE_GROUP = {
			{ 5d, 35d, 30d, 30d, 0d},	// Seccional 2
			{ 5d, 25d, 35d, 35d, 0d}	// Seccional 11
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	private static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 22d, 34d, 34d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	private static final double[][][] OCCUPATION_PER_AGE_GROUP = { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
			// Seccional 2 - 49.22% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  62d,  25d,  13d},	// 15-24
			{  14d,  76d,  10d},	// 25-39
			{   0d,  88d,  12d},	// 40-64
			{   0d,   0d, 100d}},	// 65+
			// Seccional 11 - 38.54% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  57d,  11d,  32d},	// 15-24
			{  10d,  62d,  28d},	// 25-39
			{   0d,  72d,  28d},	// 40-64
			{   0d,   0d, 100d}}	// 65+
			// 61% ocupados entre las 3 franjas activas
	};

	/** Porcentaje que trabaja en el hogar */
	private static final int[] WORKING_FROM_HOME = {5, 7};	// 02: menos del 5% | 11: menos del 7%
	/** Porcentaje que trabaja al exterior */
	private static final int[] WORKING_OUTDOORS = {5, 30};	// 02: S/D. Porcentaje ínfimo. | 11: 30%.

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	private static final int[] TRAVEL_OUTSIDE_CHANCE = {60, 20};	// Segun Abelardo es 75 y 25%, pero bajamos un poco por la epidemia
	
	private static Map<String, PlaceProperty> customPlacesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place
	private static String currentMonth = null;	// Para distinguir entre cambios de markovs
	private static boolean weekendTMMCEnabled = false;	// Flag fin de seamana
	
	public ParanaContext(Town contextTown) {
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
			buildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			buildingManager.setPTUnits(town.getPTPhaseUnits(1));
			setSocialDistancing(80);
			setMaskEffectivity(0.30);
			// Se inicia Junio sin ventilacion en hogares, oficinas y ocio 
			buildingManager.ventilateHomes(false);
			buildingManager.ventilateWorkplaces(false);
			buildingManager.ventilateEntertainmentPlaces(false);
			break;
		case 182: // 1 julio - solo Parana
			buildingManager.setPTUnits(town.getPTPhaseUnits(0)); // comienza el paro de choferes
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			buildingManager.openPlaces("bar", "restaurant", "sports_school", "gym", "sports_club");
			setSocialDistancing(60);
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			buildingManager.limitActivitiesCapacity(3d);
			break;
		case 229: // 17 agosto
			// Nueva normalidad (Fase 5)
			buildingManager.setPTUnits(town.getPTPhaseUnits(2)); // finaliza el paro de choferes
			setSocialDistancing(30);
			break;
		case 244: // 31 agosto - solo Parana
			// Vuelta a atras por saturacion de sistema sanitario (Fase 4)
			buildingManager.closePlaces("bar", "restaurant", "sports_school", "gym", "sports_club", "park");
			buildingManager.setPTUnits(town.getPTPhaseUnits(3));
			setSocialDistancing(25);
			buildingManager.limitActivitiesCapacity(1.5d);
			break;
		case 254: // 11 septiembre
			// Desde Septiembre que termina la fresca vuelven a ventilar hogares
			buildingManager.ventilateHomes(true);
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			//
			setSocialDistancing(20);
			break;
		case 257: // 14 septiembre
			setMaskEffectivity(0.25);
			// A partir de ahora si ventilan en ocio
			buildingManager.ventilateEntertainmentPlaces(true);
			buildingManager.openPlaces("bar", "restaurant", "sports_school", "gym", "sports_club");
			buildingManager.limitEntertainmentActCap(1d); //la gente aprovecha a salir
			break;
		case 264: // 21 septiembre
			buildingManager.openPlaces("sports_club", "church", "sports_complex", "park");
			buildingManager.limitEntertainmentActCap(1.1d);
			break;
		case 273: // 1 octubre
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.setPTUnits(town.getPTPhaseUnits(4));
			buildingManager.limitActivitiesCapacity(1.6d, 1.75d);
			break;
		case 302: // 29 octubre
			buildingManager.openPlaces("casino", "nursery_school", "association_or_organization");
			buildingManager.limitEntertainmentActCap(1.8d);
			break;
		case 310: // 6 noviembre
			buildingManager.setPTUnits(town.getPTPhaseUnits(5));
			break;
		case 340: // 6 diciembre - domingo
			buildingManager.limitEntertainmentActCap(2.1d);
			// Festejos cada 7 dias entre jovenes - 1% de la poblacion a 1 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans * 0.01d);
			startRepeatingYoungAdultsParty(7, tmp, 1d, true, true);
			break;
		case 343: // 9 diciembre
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.openPlaces("bus_station", "lodging", "childrens_party_service", "night_club", "tourist_attraction", "campground", "spa");
			buildingManager.setPTUnits(town.getPTPhaseUnits(6));
			break;
		case 348: // 14 diciembre
			// Aumentan las compras por las fiestas
			setMaskEffectivity(0.20);
			setSocialDistancing(10);
			buildingManager.limitOtherActCap(1.5d);
			// Verano sin ventilacion en hogares 
			buildingManager.ventilateHomes(false);
			break;
		case 358: // 24 diciembre
			// Aumentan mas las compras y las juntadas por las fiestas
			buildingManager.limitActivitiesCapacity(1d, 1.5d);
		case 365: // 31 diciembre
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans / 15 * 0.8d);
			scheduleForcedEvent(8, true, true, tmp, 15, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 366: // 1 enero
		case 359: // 25 diciembre
			// Festejos entre jovenes - 4% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.localHumans * 0.04d);
			scheduleYoungAdultsParty(tmp, 1d, true, false);
			break;
		case 376: // 11 enero
			// Merma el movimiento en Enero
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(2d, 1.5d);
			break;
		case 397: // 1 febrero
			// Aumenta un poco el movimiento en Febrero
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			buildingManager.limitEntertainmentActCap(2.5d); // mas actividades afuera
			break;
		case 411: // 15 febrero
			buildingManager.limitActivitiesCapacity(3d);
			buildingManager.openPlaces("club");
			break;
		case 425: // 1 marzo
			// Fin verano sin ventilacion en hogares, y ahora se cumple en lugares de trabajo
			buildingManager.ventilateHomes(true);
			buildingManager.ventilateWorkplaces(true);
			// Aumenta el movimiento, casi vida normal
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitOtherActCap(2.4d);
			buildingManager.openPlaces("library", "school","primary_school", "secondary_school");
			setSchoolProtocol(true);//habilita protocolo burbuja 50% 
			break;
		case 435: // 11 marzo
			// Aumenta el ocio 
			buildingManager.limitEntertainmentActCap(1.5d);
			buildingManager.openPlaces("movie_theater");
			break;
		case 445: // 21 marzo
			// Aumenta un poco mas el ocio 
			buildingManager.limitEntertainmentActCap(1.3d);
			break;
		case 459: // 4 abril - domingo
			// Vuelven a controlar aforo
			buildingManager.limitEntertainmentActCap(1.5d);
			setMaskEffectivity(0.25);
			// Almuerzo domingo de pascuas - 50% de la poblacion dividida en grupos de 10 personas, todas adentro
			tmp = (int) Math.round(town.localHumans / 10 * 0.5d);
			scheduleForcedEvent(7, false, true, tmp, 10, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 471: // 16 abril
			// Nuevas medidas
			buildingManager.ventilateHomes(false);
			setSocialDistancing(20);
			buildingManager.limitEntertainmentActCap(2.4d);
			// Merman las jodas, por que vuelven a controlar
			stopRepeatingYoungAdultsParty();
			break;
		case 487: // 2 mayo , nuevas restricciones
			buildingManager.closePlaces("library", "school","primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			buildingManager.limitEntertainmentActCap(2.7d);
			break;
		case 494: // 9 mayo , fin de nuevas restricciones
			buildingManager.limitEntertainmentActCap(3d);
			buildingManager.openPlaces("library", "school","primary_school", "secondary_school",
			"casino","club","childrens_party_service", "night_club");
			break;
		case 507: // 22 mayo , nuevas restricciones fase1
			setSocialDistancing(30);
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitEntertainmentActCap(3d);
			buildingManager.closePlaces(
			// Trabajo/estudio
			"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
			// Ocio
			"movie_theater", "bar", "sports_complex", "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
			 "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery");
			break;
		case 516: // 31 mayo , fin de nuevas restricciones fase 1
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitEntertainmentActCap(3d);
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
