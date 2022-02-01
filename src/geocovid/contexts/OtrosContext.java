package geocovid.contexts;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.MarkovChains;
import geocovid.PlaceProperty;
import geocovid.Town;

/**
 * Implementacion de <b>SubContext</b> para municipios tipo Otros.
 */
public class OtrosContext extends SubContext {
	// Punteros matriz markov //
	private static int[][][][][] localTMMC = new int[2][DataSet.AGE_GROUPS][4][4][4];
	private static int[][][][] isolatedLocalTMMC = new int[DataSet.AGE_GROUPS][4][4][4];
	
	//public static int[][][] travelerTMMC;
	//public static int[][][] infectedTravelerTMMC;
	//
	
	/** Cantidad media de humanos por hogar (minimo 1) */
	private static final double[] HOUSE_INHABITANTS_MEAN = {3, 4.25}; // Inventado

	/** Area en m2 para hogares */
	private static final int[] HOME_BUILDING_AREA = {110, 100}; // Inventado
	/** Area construida en m2 para hogares */
	private static final int[] HOME_BUILDING_COVERED_AREA = {90, 80}; // Inventado

	/** Humanos con hogar dentro y trabajo/estudio fuera - Inventado */
	private static final double[][] LOCAL_HUMANS_PER_AGE_GROUP = {
			{6d, 38d, 32d, 24d, 0d},// Seccional 3
			{7d, 28d, 37d, 28d, 0d}	// Seccional 6
	};
	/** Humanos con hogar fuera y trabajo/estudio dentro - Inventado */
	private static final double[] FOREIGN_HUMANS_PER_AGE_GROUP = {10d, 24d, 36d, 30d, 0d};

	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	private static final double[][][] OCCUPATION_PER_AGE_GROUP = { // Fuente "Mercado de trabajo" 2019-2020 - IPEC | Abelardo
			// Seccionales tipo 3 - 51.2% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  61d,  27d,  12d},	// 15-24
			{  13d,  79d,   8d},	// 25-39
			{   0d,  91d,   9d},	// 40-64
			{   0d,   0d, 100d}},	// 65+
			// Seccionales tipo 6 - 40.5% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  58d,  13d,  29d},	// 15-24
			{  11d,  65d,  24d},	// 25-39
			{   0d,  75d,  25d},	// 40-64
			{   0d,   0d, 100d}}	// 65+
			// 63.8% ocupados entre las 3 franjas activas (al 50-50%)
	};

	/** Porcentaje que trabaja en el hogar */
	private static final int[] WORKING_FROM_HOME = {5, 5};
	/** Porcentaje que trabaja al exterior */
	private static final int[] WORKING_OUTDOORS = {9, 9};

	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	private static final int[] TRAVEL_OUTSIDE_CHANCE = {55, 30};	// Inventado

	private static Map<String, PlaceProperty> customPlacesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place
	private static String currentMonth = null;	// Para distinguir entre cambios de markovs
	private static boolean weekendTMMCEnabled = false;	// Flag fin de seamana

	public OtrosContext(Town contextTown) {
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
		case 158: // 7 junio - domingo
			// Trabajo / Estudio
			buildingManager.closePlaces("lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university");
			// Ocio
			buildingManager.closePlaces("movie_theater", "bar", "sports_complex", "school", "bus_station", "childrens_party_service",
					"church", "sports_school", "spa", "night_club", "gym", "restaurant", "stadium", "sports_club",
					"library", "cultural_center", "club", "casino", "campground", "art_gallery");
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			setSocialDistancing(70);
			setMaskEffectivity(0.30);
			// Se inicia Junio sin ventilacion en hogares, oficinas y ocio 
			buildingManager.ventilateHomes(false);
			buildingManager.ventilateWorkplaces(false);
			buildingManager.ventilateEntertainmentPlaces(false);
			break;
		case 159: // 8 junio - domingo
			// Gastronomicos con reserva previa con un m�ximo del 30% de la capacidad (hasta las 23).
			buildingManager.openPlaces("bar", "restaurant");
			setSocialDistancing(60);
			break;
		case 170: // 19 junio - viernes
			// Bares y restoranes abren de 7 a 24 horas, y los supermercados de 8 a 20 horas.
			setSocialDistancing(50);
			buildingManager.limitActivitiesCapacity(3, 3.5);
			break;
		case 181: // 30 junio - martes
			// Habilitaron celebraciones religiosas de hasta 30 personas.
			buildingManager.openPlaces("church");
			break;
		case 214: // 2 agosto - domingo
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			setSocialDistancing(40);
			break;
		case 226: // 14 agosto - viernes
			buildingManager.limitActivitiesCapacity(2.5, 1.5);
			break;
		case 231: // 19 agosto - miercoles
			buildingManager.limitActivitiesCapacity(1.5, 1.);
			setSocialDistancing(20);
			break;
		case 255: // 12 septiembre - domingo
			// Vuelta atras de fase por 14 dias
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitEntertainmentActCap(1);//1.3
			buildingManager.closePlaces("bar", "restaurant", "church");
			// Nuevas medidas (contacto estrecho)
			enableCloseContacts();
			enablePrevQuarantine();
			break;
		case 269: // 26 septiembre - sabado
			// Cambio de fase, gran cantidad de habilitaciones
			setTMMCs("october", MarkovChains.OCTOBER_TMMC);
			buildingManager.openPlaces("bar", "restaurant", "church", "gym", "sports_club", "sports_school", "sports_complex");
			// Inicio mini veranito
			buildingManager.limitActivitiesCapacity(1.25, 1);
			setMaskEffectivity(0.10);
			setSocialDistancing(10);
			break;
		case 281: // 9 octubre - viernes
			buildingManager.openPlaces("club", "campground");
			// Fin mini veranito
			buildingManager.limitActivitiesCapacity(2.5, 3.2);
			setMaskEffectivity(0.20);
			setSocialDistancing(20);
			break;
		case 292: // 19 octubre - lunes
			// Desde Octubre ventilacion en hogares y ocio
			buildingManager.ventilateHomes(true);
			buildingManager.ventilateEntertainmentPlaces(true);
			buildingManager.limitEntertainmentActCap(2.8);
			break;
		case 313: // 9 noviembre - lunes
			// Se habilitan eventos culturales y actividades recreativas al aire libre
			setTMMCs("november", MarkovChains.NOVEMBER_TMMC);
			buildingManager.limitEntertainmentActCap(2.5);
			buildingManager.openPlaces("cultural_center", "art_gallery");
			break;
		case 317: // 13 noviembre - viernes
			buildingManager.limitEntertainmentActCap(2.3);//2.6
			break;
		case 331: // 27 noviembre - viernes
			// Se habilitan salones para fiestas como bar / restaurant
			buildingManager.openPlaces("night_club");
			break;
		case 335: // 1 diciembre - martes
			buildingManager.limitEntertainmentActCap(2.4);
			// El decreto N� 1531, habilita desde el 1 de diciembre el funcionamiento de jardines maternales de gesti�n privada "nursery_school"
			buildingManager.openPlaces("lodging", "spa");
			break;
		case 340: // 6 diciembre - domingo
			setTMMCs("holidays", MarkovChains.HOLIDAYS_TMMC);
			buildingManager.limitEntertainmentActCap(3); // mas actividades afuera
			// Festejos cada 7 dias entre jovenes - 1% de la poblacion a 1 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans * 0.01d);
			startRepeatingYoungAdultsParty(7, tmp, 1d, true, true);
			break;
		case 345: // 11 diciembre - viernes
			buildingManager.openPlaces("casino");
			break;
		case 348: // 14 diciembre - lunes
			// Aumentan las compras por las fiestas
			setMaskEffectivity(0.20);
			setSocialDistancing(10);
			// Verano sin ventilacion en hogares 
			//buildingManager.ventilateHomes(false); // si dejo de ventilar suben muchos los casos
			break;
		case 358: // 24 diciembre - jueves
			// Inicio receso (no influye)
			buildingManager.closePlaces("courthouse", "local_government_office", "social_services_organization",
					"association_or_organization", "corporate_office");
			// Aumentan mas las compras y las juntadas por las fiestas
			buildingManager.limitEntertainmentActCap(3);
		case 365: // 31 diciembre - jueves
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(town.localHumans / 15 * 0.8d);
			scheduleForcedEvent(8, true, true, tmp, 15, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 362: // 28 diciembre - lunes
		case 369: // 4 enero - lunes
			buildingManager.openPlaces("corporate_office");
			break;
		case 366: // 1 enero - viernes
			// Habilitado, desde el 1 de enero, los viajes a larga distancia.
			buildingManager.openPlaces("bus_station");
			buildingManager.limitActivitiesCapacity(2, 3);
		case 359: // 25 diciembre - viernes
			// Festejos entre jovenes - 4% de la poblacion a 1 cuadrados por persona, al aire libre
			tmp = (int) Math.round(town.localHumans * 0.04d);
			scheduleYoungAdultsParty(tmp, 1d, true, false);
			break;
		case 375: // 10 enero - domingo
			// Merma el movimiento en Enero
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			break;
		case 397: // 1 febrero - lunes
			// Fin receso
			buildingManager.openPlaces("courthouse", "local_government_office", "social_services_organization",
					"association_or_organization");
			// Aumenta un poco el movimiento en Febrero
			setTMMCs("august", MarkovChains.AUGUST_TMMC);
			break;
		case 413: // 17 febrero - miercoles
			buildingManager.limitActivitiesCapacity(1.5, 2.5);
			buildingManager.openPlaces("nursery_school");
			break;
		case 425: // 1 marzo - lunes
			// Fin verano sin ventilacion en hogares, y ahora se cumple en lugares de trabajo
			buildingManager.ventilateHomes(true);
			buildingManager.ventilateWorkplaces(true);
			// Aumenta el movimiento, casi vida normal
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			buildingManager.limitEntertainmentActCap(1.7); 
			break;
		case 434: // 10 marzo - miercoles
			// Cines en Ciudad de SF pueden ocupar hasta el 50% de su capacidad
			buildingManager.openPlaces("movie_theater");
			break;
		case 439: // 15 marzo - lunes
			// En Santa Fe inician las clases presenciales de todos los cursos
			buildingManager.limitEntertainmentActCap(1); 
			buildingManager.openPlaces("library", "school", "primary_school", "secondary_school");
			setSchoolProtocol(true);//habilita protocolo burbuja 50%
			break;
		case 463: // 8 abril- jueves
			// Suspension de actividades culturales
			buildingManager.closePlaces("cultural_center", "art_gallery");
			// Almuerzo domingo de pascuas - 50% de la poblacion dividida en grupos de 10 personas, todas adentro
			tmp = (int) Math.round(town.localHumans / 10 * 0.5d);
			scheduleForcedEvent(7, false, true, tmp, 10, new int[] {14, 18, 23, 32, 13}, 3); // 3 ticks = 2 horas
			break;
		case 478: // 23 abril - viernes, nuevas restricciones
			buildingManager.closePlaces("movie_theater", "bar", "restaurant", "casino", "childrens_party_service", "night_club");
			buildingManager.limitEntertainmentActCap(2);
			setMaskEffectivity(0.25);
			break;
		case 488: // 3 mayo - lunes, fin de nuevas restricciones
			buildingManager.openPlaces("movie_theater", "bar", "restaurant", "casino", "childrens_party_service", "night_club");
			break;
		case 505: // 20 mayo - jueves, nuevas restricciones fase 1
			setSocialDistancing(30);
			setTMMCs("june", MarkovChains.JUNE_TMMC);
			buildingManager.limitEntertainmentActCap(1d);//2.1
			buildingManager.closePlaces(
			// Trabajo/estudio
			"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school",
			// Ocio
			"movie_theater", "bar", "restaurant", "sports_complex", "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym",
			 "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery");
			break;
		case 516: // 31 mayo - lunes
			// TODO segun decreto siguen con muchas restricciones de fase 1 (pero comparando movilidad, no se cumplen)
			setTMMCs("march", MarkovChains.MARCH_TMMC);
			setSocialDistancing(20);
			buildingManager.limitActivitiesCapacity(1.3, 2.1);
			
			buildingManager.openPlaces("bar", "restaurant"); // entre las 6 y 19 horas
			// medidas hasta el 6 que despues se extienden al 11 de junio
			break;
		case 528: // 12 junio - sabado
			// mismas medidas hasta el 26
			break;
		case 538: // 22 junio - martes
			buildingManager.openPlaces("library","primary_school","nursery_school");
			buildingManager.limitEntertainmentActCap(2d);
			break;
		case 542: // 26 junio - sabado
			// mismas medidas hasta el 9 de julio
			break;
		case 555: // 9 julio - viernes al 23 de julio
			buildingManager.openPlaces("lodging","park","movie_theater","church","casino","cultural_center","art_gallery","gym","sports_complex",
			"spa","campground");
			break;
		case 558: // 12 de julio- lunes  al 23/7: receso escolar de invierno
			buildingManager.closePlaces("nursery_school", "primary_school");
			break;
		case 572: // 26 de julio- lunes  fin receso escolar de invierno
			buildingManager.openPlaces( "nursery_school", "primary_school","secondary_school");
			buildingManager.limitActivitiesCapacity(1.5,2);
			break;
		case 577: // 31 de julio : restricciones hasta 6 de agosto
		buildingManager.openPlaces( "sports_club","club","sports_school");
		buildingManager.limitEntertainmentActCap(2.2d);
		    break;
		case 583: //6 de Agosto viernes
			setTMMCs("pre-pandemic", MarkovChains.DEFAULT_TMMC);
			buildingManager.limitEntertainmentActCap(2d);
			//se mantiene las mismas restriccones , se aumenta el aforo en algunas actividades, 
			//falta habilitra "night_club", "stadium","childrens_party_service,"club""
			//https://www.argentina.gob.ar/noticias/nuevas-disposiciones-sanitarias-ante-pandemia-de-covid-19
			break;
		case 640://1 de octubre
			//buildingManager.limitEntertainmentActCap(1.5d);
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
}