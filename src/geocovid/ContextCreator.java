package geocovid;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import cern.jet.random.Uniform;
import geocovid.agents.BuildingAgent;
import geocovid.agents.ForeignHumanAgent;
import geocovid.agents.HomeAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.PublicTransportAgent;
import geocovid.agents.WorkplaceAgent;
import repast.simphony.context.Context;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;

public class ContextCreator implements ContextBuilder<Object> {
	private List<List<HomeAgent>> homePlaces;	// Lista de hogares en cada seccional
	private List<WorkplaceAgent>workPlaces = new ArrayList<WorkplaceAgent>();		// Lista de lugares de trabajo
	private List<WorkplaceAgent>schoolPlaces = new ArrayList<WorkplaceAgent>();		// Lista de lugares de estudio primario/secundario
	private List<WorkplaceAgent>universityPlaces = new ArrayList<WorkplaceAgent>();	// Lista de lugares de estudio terciario/universitario
	
	private HumanAgent[] contextHumans;	// Array de HumanAgent parte del contexto
	private int localHumansCount;		// Cantidad de humanos que viven en contexto
	private int foreignHumansCount;		// Cantidad de humanos que viven fuera del contexto
	private int[] localHumansIndex;		// Indice en contextHumans de caga grupo etario (local)
	
	private ForeignHumanAgent[] touristHumans;	// Array de HumanAgent temporales/turistas
	private int[] lodgingPlacesSI;				// Seccionales donde existen lugares de hospedaje
	private ISchedulableAction touristSeasonAction;
	private ISchedulableAction youngAdultsPartyAction;
	
	private Set<Integer> socialDistIndexes;		// Lista con ids de humanos que respetan distanciamiento
	
	private Map<String, PlaceProperty> placesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place 
	
	private ISchedule schedule;
	private Context<Object> context;
	private Geography<Object> geography;
	
	// Parametros de simulacion
	private int simulationStartYear;
	private int simulationStartDay;
	private int simulationMaxTick;
	private int simulationMinDay;
	private int deathLimit;
	private int infectedAmount;
	private int simulationRun;
	private int outbreakStartTick;
	private int weekendStartTick;
	private String townName;
	
	private long simulationStartTime;	// Tiempo inicio de simulacion
	private long maxParcelId;	// Para no repetir ids, al crear casas ficticias
	
	private int[][] employedCount;	// Cupo de trabajadores
	private int[][] schooledCount;	// Cupo de estudiantes pri/sec
	private int[][] collegiateCount;// Cupo de estudiantes ter/uni
	private int unemployedCount;	// Contador de empleos faltantes
	private int unschooledCount;	// Contador de bancos faltantes en escuelas
	private int noncollegiateCount; // Contador de bancos faltantes en universidades
	
	private String currentMonth = null;	// Para distinguir entre cambios de markovs
	private boolean publicTransportEnabled = false;	// Flag colectivo
	private boolean weekendTMMCEnabled = false;	// Flag fin de seamana
	static final int WEEKLY_TICKS = 12*7;	// Ticks que representan el tiempo de una semana
	static final int WEEKEND_TICKS = 12*2;	// Ticks que representan el tiempo que dura el fin de semana
	
	static final boolean DEBUG_MSG = false;	// Flag para imprimir valores de inicializacion
	
	public ContextCreator() {
		printJarVersion(this.getClass());
	}
	
	@Override
	public Context<Object> build(Context<Object> context) {
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters params = ScheduleParameters.createOneTime(0d, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "startSimulation");
		params = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "printSimulationDuration");
		
		// Crear la proyeccion para almacenar los agentes GIS (EPSG:4326).
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		this.geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);
		setBachParameters();
		
		RunEnvironment.getInstance().endAt(simulationMaxTick);
		
		// Schedule one shot para agregar infectados
		params = ScheduleParameters.createOneTime(outbreakStartTick, 0.9d);
		schedule.schedule(params, this, "infectLocalRandos", infectedAmount);
		
		Town.setTown(townName);
		// Programa los cambios de fases, pasadas y futuras
		int phaseDay;
		int[] phasesStartDay = Town.lockdownPhasesDays;
		for (int i = 0; i < phasesStartDay.length; i++) {
			phaseDay = phasesStartDay[i] - simulationStartDay;
			if (phaseDay > 0)	// Fase futura
				phaseDay *= 12;
			else				// Fase pasada
				phaseDay = 0;
			params = ScheduleParameters.createOneTime(phaseDay, 0.9d);
			schedule.schedule(params, this, "initiateLockdownPhase", phasesStartDay[i]);
		}
		
		// Reinicio estos valores por las dudas
		publicTransportEnabled = false;
		weekendTMMCEnabled = false;
		currentMonth = null;
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		setWeekendMovement();
		
		this.context = context;
		context.add(new InfectionReport(simulationMinDay, deathLimit)); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartYear, simulationStartDay)); // Para calcular temperatura diaria, para estela
		
		if (placesProperty.isEmpty()) // Para no volver a leer si se reinicia simulacion
			placesProperty = PlaceProperty.loadPlacesProperties();
		homePlaces = new ArrayList<List<HomeAgent>>(Town.sectoralsCount);
		loadParcelsShapefile();
		loadPlacesShapefile();
		BuildingManager.createActivitiesChances();
		
		DataSet.setDefaultValues();
		
		initHumans();
		
		homePlaces.clear();
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		return context;
	}
	
	/**
	 * Programa en schedule los metodos para cambiar matrices de markov en los periodos de fines de semanas.<p>
	 * Cuando termina se asigna "setHumansDefaultTMMC"
	 */
	private void setWeekendMovement() {
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(weekendStartTick, WEEKLY_TICKS, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "setHumansWeekendTMMC", true);
		params = ScheduleParameters.createRepeating(weekendStartTick + WEEKEND_TICKS, WEEKLY_TICKS, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "setHumansWeekendTMMC", false);
	}
	
	public void startSimulation() {
		simulationStartTime = System.currentTimeMillis();
	}
	
	public void printSimulationDuration() {
		// Por las dudas al finalizar borro la referencia de HumanAgents
		contextHumans = null;
		
		final long simTime = System.currentTimeMillis() - simulationStartTime;
		
		System.out.printf("Simulacion N°: %3d | Tiempo: %.2f minutos%n", simulationRun, (simTime / (double)(1000*60)));
		System.out.printf("Dias epidemia: %3d%n", (int) (schedule.getTickCount() - outbreakStartTick) / 12);
		
		System.out.printf("Susceptibles: %5d | Infectados: %d | Infectados por estela: %d%n",
				(localHumansCount + foreignHumansCount),
				InfectionReport.getCumExposed(),
				InfectionReport.getCumExposedToCS());
    	
		System.out.printf("Recuperados:  %5d | Muertos: %d%n",
				InfectionReport.getCumRecovered(),
				InfectionReport.getCumDeaths());
		
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			System.out.println(InfectionReport.getInfectedReport(i));
		}
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos locales seteada en los parametros y los infecta.
	 */
	public void infectLocalRandos(int amount) {
		if (amount == 0)
			return;
		InfectionReport.outbreakStarted = true; // a partir de ahora hay riesgo de infeccion
		int infected = 0;
		Set<Integer> indexes = new HashSet<Integer>(amount, 1f);
		int i;
		do {
			// Saltea primer y ultima franja etaria
			i = RandomHelper.nextIntFromTo(localHumansIndex[0], localHumansIndex[localHumansIndex.length - 2] - 1);
			// Chequea si no es repetido
			if (indexes.add(i)) {
				contextHumans[i].setInfectious(true, true); // Asintomatico
				++infected;
			}
		} while (infected != amount);
	}
	
	/**
	 * Setear temporada turistica, para simular el ingreso de turistas
	 * @param duration dias totales de turismo
	 * @param interval dias de intervalo entre grupo de turistas
	 * @param touristAmount cantidad por grupo de turistas
	 * @param infectedAmount cantidad de infectados asintomaticos por grupo de turistas
	 */
	@SuppressWarnings("unused")
	private void setTouristSeason(int duration, int interval, int touristAmount, int infectedAmount) {
		// Si es la primer simulacion
		if (lodgingPlacesSI == null) { // vector con indices de seccionales de Places tipo lodging
			// Recorrer los lugares de alojamiento y guardar los indices de seccional
			List<WorkplaceAgent> lodgingPlaces = BuildingManager.getWorkplaces("lodging");
			if (lodgingPlaces == null) {
				System.err.println("No existen Places de alojamiento (lodging)");
				return;
			}
			lodgingPlacesSI = new int[lodgingPlaces.size()];
			for (int i = 0; i < lodgingPlacesSI.length; i++)
				lodgingPlacesSI[i] = lodgingPlaces.get(i).getSectoralIndex();
		}
		
		// Por las dudas limpiar turistas
		touristHumans = null;
		// Programar las acciones de recambio de turistas y de fin de temporada turistica
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(schedule.getTickCount(), (interval * 12), ScheduleParameters.FIRST_PRIORITY);
		touristSeasonAction = schedule.schedule(params, this, "newTouristGroup", touristAmount, infectedAmount);
		params = ScheduleParameters.createOneTime(schedule.getTickCount() + (duration * 12) - 0.1d, ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "endTouristSeason");
	}
	
	public void newTouristGroup(int tourist, int infected) {
		// Si ya existe el vector de turistas, sacar del contexto el grupo anterior
		if (touristHumans != null) {
			for (ForeignHumanAgent human : touristHumans)
				human.removeInfectiousFromContext();
		}
		// Si no existe el vector de turistas, crear segun tamano del grupo 
		else
			touristHumans = new ForeignHumanAgent[tourist];
		
		int secIndex;
		ForeignHumanAgent tempHuman;
		// Crear Humanos turista, agregarlos al contexto y al vector 
		for (int i = 0; i < tourist; i++) {
			// Seleccionar al azar una seccional de la lista de lugares de alojamiento
			secIndex = lodgingPlacesSI[RandomHelper.nextIntFromTo(0, lodgingPlacesSI.length-1)];
			tempHuman = new ForeignHumanAgent(secIndex);
			if (i < infected)
				tempHuman.setInfectious(true, false); // Asintomatico
			context.add(tempHuman);
			touristHumans[i] = tempHuman;
		}
	}
	
	public void endTouristSeason() {
		// Eliminar la accion programada que renueva el grupo de turistas y sacar del contexto el ultimo grupo
		schedule.removeAction(touristSeasonAction);
		if (touristHumans != null) {
			for (HumanAgent tourist : touristHumans)
				tourist.removeInfectiousFromContext();
		}
		// Por las dudas limpiar turistas
		touristHumans = null;
	}

	/**
	 * Arma un listado con Humanos seleccionados al azar, segun cupo de cada franja etaria y que no estan aislados.
	 * @param events cantidad de eventos
	 * @param humansPerEvent cantidad de Humanos por evento
	 * @param humansByAG las cantidades de Humanos por franja etaria a seleccionar por evento
	 * @return array de HumanAgent
	 */
	private HumanAgent[][] gatherHumansForEvent(int events, int humansPerEvent, int[] humansByAG) {
		HumanAgent[][] participants = new HumanAgent[events][humansPerEvent];
		//
		HumanAgent tempHuman;
		int rndIndex, rndFrom, rndTo;
		int partIndex = 0;
		for (int i = 0; i < humansByAG.length; i++) { // Franjas
			// Busca desde el indice del grupo etario anterior, al siguiente 
			rndFrom = (i == 0) ? 0 : localHumansIndex[i - 1];
			rndTo = localHumansIndex[i] - 1;
			// Si tiene que seleccionar mas de los disponibles
			if (humansByAG[i] > (rndTo - rndFrom + 1)) {
				System.err.println("Cantidad de Humanos insuficiente en franja etaria: " + i);
				break;
			}
			for (int j = 0; j < humansByAG[i]; j++) { // Participantes
				for (int k = 0; k < events; k++) { // Eventos
					rndIndex = RandomHelper.nextIntFromTo(rndFrom, rndTo);
					tempHuman = contextHumans[rndIndex];
					// Chequea que no este aislado y no tenga una actividad programada
					if (!tempHuman.isIsolated() && !tempHuman.isActivityQueued()) {
						participants[k][partIndex] = tempHuman;
					}
				}
				++partIndex;
			}
		}
		//
		return participants;
	}
	
	/**
	 * Asigna Humanos a cada evento.
	 * @param buildings lista de Building de eventos
	 * @param humans array de HumanAgents agrupados por evento
	 * @param duration en ticks
	 */
	private void distributeHumansInEvent(List<BuildingAgent> buildings, HumanAgent[][] humans, int duration) {
		BuildingAgent tempBuilding;
		HumanAgent tempHuman;
		for (int i = 0; i < buildings.size(); i++) {
			tempBuilding = buildings.get(i);
			for (int j = 0; j < humans[i].length; j++) {
				tempHuman = humans[i][j];
				if (tempHuman != null) {
					humans[i][j].queueActivity(2, tempBuilding, duration); // TODO tendria que cambiar el tipo, por ahora es ocio
				}
			}
		}
	}
	
	/**
	 * Programa actividad forzada en Places para ciertos Humanos.
	 * @param type tipo secundario de Place
	 * @param secondaryOnly si se ignora el tipo primario
	 * @param maxPlaces cantidad de Places al azar (-1 para todos)
	 * @param humansPerPlace cantidad de Humanos por Place
	 * @param ageGroupPerc porcentaje de Humanos por franja etaria
	 * @param ticksDuration duracion de la actividad en ticks
	 */
	@SuppressWarnings("unused")
	private void scheduleForcedActivity(String type, Boolean secondaryOnly, int maxPlaces, int humansPerPlace, int[] ageGroupPerc, int ticksDuration) {
		PlaceProperty placeProp = placesProperty.get(type);
		if (placeProp == null) {
			System.err.println("Type de Place desconocido: " + type);
			return;
		}
		
		List<BuildingAgent> places;
		if (secondaryOnly) // No toma el resto de places en el grupo primario
			places = BuildingManager.getActivityBuildings(maxPlaces, placeProp.getGooglePlaceType(), placeProp.getGoogleMapsType());
		else
			places = BuildingManager.getActivityBuildings(maxPlaces, placeProp.getGooglePlaceType());
		
		if (places.isEmpty()) {
			System.err.println("No existen Places del type: " + type);
			return;
		}
		
		int[] agPerBuilding = new int[ageGroupPerc.length];
		int adjHumPerPlace = 0; // Cantidad ajustada de Humanos por Place
		for (int i = 0; i < ageGroupPerc.length; i++) {
			agPerBuilding[i] = Math.round((humansPerPlace * ageGroupPerc[i]) / 100);
			adjHumPerPlace += agPerBuilding[i];
		}
		
		HumanAgent[][] activeHumans = gatherHumansForEvent(places.size(), adjHumPerPlace, agPerBuilding);
		distributeHumansInEvent(places, activeHumans, ticksDuration);
	}
	
	/**
	 * Programa evento forzado en Building ficticio para ciertos Humanos.
	 * @param realArea valor neto de area donde sucede evento 
	 * @param outdoor si el evento sucede al aire libre
	 * @param events cantidad de eventos a crear
	 * @param humansPerEvent cantidad de Humanos por evento
	 * @param ageGroupPerc porcentaje de Humanos por franja etaria
	 * @param ticksDuration duracion del evento en ticks
	 */
	private void scheduleForcedEvent(int realArea, boolean outdoor, int events, int humansPerEvent, int[] ageGroupPerc, int ticksDuration) {
		int[] agPerBuilding = new int[ageGroupPerc.length];
		int adjHumPerPlace = 0; // Cantidad ajustada de Humanos por Place
		for (int i = 0; i < ageGroupPerc.length; i++) {
			agPerBuilding[i] = Math.round((humansPerEvent * ageGroupPerc[i]) / 100);
			adjHumPerPlace += agPerBuilding[i];
		}
		
		HumanAgent[][] activeHumans = gatherHumansForEvent(events, adjHumPerPlace, agPerBuilding);
		
		List<BuildingAgent> buildings = new ArrayList<BuildingAgent>();
		Coordinate[] coords = BuildingManager.getSectoralsCentre();
		int sectoralType, sectoralIndex;
		for (int i = 0; i < events; i++) {
			sectoralIndex = RandomHelper.nextIntFromTo(0, Town.sectoralsCount - 1);
			sectoralType = Town.sectoralsTypes[sectoralIndex];
			buildings.add(new BuildingAgent(sectoralType, sectoralIndex, coords[sectoralIndex], realArea, outdoor));
		}
		
		distributeHumansInEvent(buildings, activeHumans, ticksDuration);
	}
	
	/**
	 * Programa fiestas entre jovenes adultos segun los parametros dados.
	 * @param humansTotal cantidad total de humanos en fiestas 
	 * @param sqPerHuman cuadrados por humano
	 * @param outdoor si las fiestas son al aire libre
	 * @param indoor si las fiestas son en lugares cerrados
	 */
	public void scheduleYoungAdultsParty(int humansTotal, double sqPerHuman, boolean outdoor, boolean indoor) {
		// Segun la cantidad de participantes, calcula: la cantidad de fiestas, participantes en cada una y area neta 
	    int humansPerParty = ((int) Math.sqrt(humansTotal) + 1) << 1; // humanos por fiesta
	    int parties = humansTotal / humansPerParty; // cantidad de fiestas
	    int area = (int) ((humansPerParty / DataSet.HUMANS_PER_SQUARE_METER) * sqPerHuman); // metros del area
	    // Programa el evento
	    if (outdoor && indoor) { // mitad y mitad
	    	scheduleForcedEvent(area, true, parties - (parties >> 1), humansPerParty, new int[] {0,65,35,0,0}, 4); // 4 ticks = 6 horas
	    	scheduleForcedEvent(area, false, parties >> 1			, humansPerParty, new int[] {0,65,35,0,0}, 4); // 4 ticks = 6 horas
	    }
	    else { // afuera o adentro
	    	scheduleForcedEvent(area, outdoor, parties, humansPerParty, new int[] {0,65,35,0,0}, 4); // 4 ticks = 6 horas
	    }
	}
	
	/**
	 * Programa por tiempo indeterminado la creacion de fiestas entre jovenes adultos segun los parametros dados.
	 * @param interval dias entre eventos
	 * @param humansTotal cantidad total de humanos en fiestas 
	 * @param sqPerHuman cuadrados por humano
	 * @param outdoor si las fiestas son al aire libre
	 * @param indoor si las fiestas son en lugares cerrados
	 */
	private void startRepeatingYoungAdultsParty(int interval, int humansTotal, double sqPerHuman, boolean outdoor, boolean indoor) {
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(schedule.getTickCount(), (interval * 12), ScheduleParameters.FIRST_PRIORITY);
		youngAdultsPartyAction = schedule.schedule(params, this, "scheduleYoungAdultsParty", humansTotal, sqPerHuman, outdoor, indoor);
	}
	
	/**
	 * Detiene la creacion programada de fiestas entre jovenes adultos.
	 */
	@SuppressWarnings("unused")
	private void stopRepeatingYoungAdultsParty() {
		if (!removeYAPartyAction()) {
			ScheduleParameters params;
			params = ScheduleParameters.createOneTime(schedule.getTickCount() + 0.1d);
			schedule.schedule(params, this, "removeYAPartyAction");
		}
	}
	
	public boolean removeYAPartyAction() {
		// Eliminar la accion programada que crea fiestas para jovenes adultos
		return schedule.removeAction(youngAdultsPartyAction);
	}
	
	/**
	 * Setea aleatoriamente a Humanos si respetan el distanciamiento social, segun porcentaje de la poblacion dado.
	 * @param porcentaje
	 */
	private void setSocialDistancing(int porc) {
		int newAmount = (int) Math.ceil((localHumansCount + foreignHumansCount) * porc) / 100;
		int oldAmount = 0;
		// El porcentaje era cero
		if (DataSet.getSDPercentage() == 0 && porc > 0) {
			socialDistIndexes = new HashSet<Integer>(newAmount);
		}
		else
			oldAmount = socialDistIndexes.size();
		DataSet.setSDPercentage(porc);
		
		// El nuevo porcentaje es menor
		if (newAmount < oldAmount) {
			int toRemove = oldAmount - newAmount;
			for (java.util.Iterator<Integer> it = socialDistIndexes.iterator(); it.hasNext();) {
				Integer index = it.next();
				contextHumans[index].setSociallyDistanced(false);
				it.remove();
				if (--toRemove == 0)
					break;
			}
		}
		// El nuevo porcentaje es mayor
		else if (newAmount > oldAmount) {
			int toAdd = newAmount - oldAmount;
			int i;
			do {
				i = RandomHelper.nextIntFromTo(0, localHumansCount + foreignHumansCount - 1);
				if (socialDistIndexes.add(i)) {
					contextHumans[i].setSociallyDistanced(true);
					--toAdd;
				}
			} while (toAdd != 0);
		}
	}
	
	/**
	 * Si lo permite la ciudad, habilitar o deshabilitar la opcion de usar transporte publico. 
	 */
	private void enablePublicTransport(boolean enabled) {
		if (Town.publicTransportAllowed) {
			if (enabled && !publicTransportEnabled) {
				publicTransportEnabled = true;
				PublicTransportAgent pt = new PublicTransportAgent();
				context.add(pt);
				BuildingManager.setPublicTransport(pt);
			}
			else if (!enabled && publicTransportEnabled) {
				publicTransportEnabled = false;
				context.remove(BuildingManager.getPublicTransport());
				BuildingManager.setPublicTransport(null);
			}
		}
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan los fines de semana, para los primeros 4 grupos etarios.
	 */
	public void setHumansWeekendTMMC(boolean enabled) {
		weekendTMMCEnabled = enabled;
		for (int i = 0; i < DataSet.AGE_GROUPS-1; i++) {
			MarkovChains.setWeekendDiff(HumanAgent.localTMMC[0][i], enabled);
			MarkovChains.setWeekendDiff(HumanAgent.localTMMC[1][i], enabled);
		}
		HumanAgent.travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
	}
	
	public void setTMMCs(String month, int[][][][] tmmc0, int[][][][] tmmc1) {
		if (!month.equals(currentMonth)) {
			currentMonth = month; 
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				HumanAgent.localTMMC[0][i]	= tmmc0[i];
				HumanAgent.localTMMC[1][i]	= tmmc1[i];
			}
		}
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.<p>
	 * Ver {@link #initHumans()}
	 */
	public void setDefaultTMMC() {
		setTMMCs("default", MarkovChains.SEC2_DEFAULT_TMMC, MarkovChains.SEC11_DEFAULT_TMMC);
		
		HumanAgent.travelerTMMC	= MarkovChains.TRAVELER_DEFAULT_TMMC;
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			HumanAgent.isolatedLocalTMMC[i] = MarkovChains.ISOLATED_TMMC;
		}
		HumanAgent.infectedTravelerTMMC = MarkovChains.ISOLATED_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se van a utilizar al comenzar cada fase.
	 */
	public void initiateLockdownPhase(int phase) {
		boolean lockdownOverWKD = false;
		int tmp;
		// Chequea si se cambio de fase durante el fin de semana y no es la primer fase
		if (weekendTMMCEnabled && schedule.getTickCount() > 0d) {
			lockdownOverWKD = true;
			setHumansWeekendTMMC(false); // para restar la matriz de finde
		}
		//
		switch (phase) {
		case 163: // 12 junio
			// Reapertura progresiva (Fase 4)
			BuildingManager.closePlaces(new String[] {
					// Trabajo/estudio
					"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
					// Ocio
					"movie_theater", "bar", "sports_complex",  "school", "bus_station", "childrens_party_service", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"restaurant", "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" });
			setTMMCs("june", MarkovChains.SEC2_JUNE_TMMC, MarkovChains.SEC11_JUNE_TMMC);
			BuildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			enablePublicTransport(true);
			setSocialDistancing(90);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 182: //  1 julio - solo Parana
			enablePublicTransport(false); // comienza el paro de choferes
			break;
		case 201: // 20 julio
			// Reapertura progresiva (Fase 4)
			BuildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			setTMMCs("july", MarkovChains.SEC2_JULY_TMMC, MarkovChains.SEC11_JULY_TMMC);
			break;
		case 215: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.SEC2_AUGUST_TMMC, MarkovChains.SEC11_AUGUST_TMMC);
			BuildingManager.limitActivitiesCapacity(3.5d);
			setSocialDistancing(80);
			break;
		case 229: // 17 agosto
			// Nueva normalidad (Fase 5)
			enablePublicTransport(true); // finaliza el paro de choferes
			setSocialDistancing(70);
			break;
		case 244: // 31 agosto - solo Parana
			// Vuelta a atras por saturacion de sistema sanitario (Fase 4)
			setSocialDistancing(60);
			BuildingManager.closePlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club", "park"});
			break;
		case 254: // 11 septiembre
			// Nuevas medidas (contacto estrecho)
			DataSet.enableCloseContacts();
			DataSet.enablePrevQuarantine();
			//
			BuildingManager.limitActivitiesCapacity(3.5d);
			setSocialDistancing(50);
			break;
		case 257: // 14 septiembre
			BuildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			setTMMCs("september", MarkovChains.SEC2_SEPTEMBER_TMMC, MarkovChains.SEC11_SEPTEMBER_TMMC);
			break;
			
		case 264: // 21 septiembre
			BuildingManager.openPlaces(new String[] {"sports_club", "church", "sports_complex", "park"});
			BuildingManager.limitActivitiesCapacity(4d);
			setSocialDistancing(45);
			break;
		case 273: // 1 octubre
			setTMMCs("october", MarkovChains.SEC2_OCTOBER_TMMC, MarkovChains.SEC11_OCTOBER_TMMC);
			setSocialDistancing(40);
			break;
		case 302: // 29 octubre
			BuildingManager.openPlaces(new String[] {"casino", "nursery_school", "association_or_organization"});
			BuildingManager.limitActivitiesCapacity(3.5d);
			setSocialDistancing(30);
			break;
		case 310: // 6 noviembre
			// Nueva alversoetapa
			setTMMCs("november", MarkovChains.SEC2_NOVEMBER_TMMC, MarkovChains.SEC11_NOVEMBER_TMMC);
			break;
		case 343: // 9 diciembre
			setTMMCs("holidays", MarkovChains.SEC2_HOLIDAYS_TMMC, MarkovChains.SEC11_HOLIDAYS_TMMC);
			BuildingManager.openPlaces(new String[] {"bus_station", "childrens_party_service", "night_club", "tourist_attraction", "campground"});
			break;
			
		case 358: // 24 diciembre
			BuildingManager.limitOtherActCap(1d);
			setSocialDistancing(20);
			setTMMCs("november", MarkovChains.SEC2_NOVEMBER_TMMC, MarkovChains.SEC11_NOVEMBER_TMMC);
		case 365: // 31 diciembre
			// Cenas familiares - 80% de la poblacion dividida en grupos de 15 personas, mitad afuera y mitad adentro
			tmp = (int) Math.round(Town.getLocalPopulation() / 15 * 0.8d);
			scheduleForcedEvent(18, true,  tmp - (tmp >> 1),15, new int[] {14, 18, 23, 32, 13}, 2); // 2 ticks = 3 horas
			scheduleForcedEvent(18, false, tmp >> 1,		15, new int[] {14, 18, 23, 32, 13}, 2); // 2 ticks = 3 horas
			break;
			
		case 366: // 1 enero
			DataSet.setMaskEffectivity(0.2);
			BuildingManager.limitActivitiesCapacity(2.5d); //3d
			setTMMCs("october", MarkovChains.SEC2_OCTOBER_TMMC, MarkovChains.SEC11_OCTOBER_TMMC);
			// Periodo turistico todo el mes de Enero
			//setTouristSeason(30, 3, 2000, 10); // 30 dias, 3 dias recambio, 2000 turistas por grupo, 10 infecciosos por grupo
		case 359: // 25 diciembre
			// Festejos entre jovenes - 4% de la poblacion a 1.2 cuadrados por persona, al aire libre
			tmp = (int) Math.round(Town.getLocalPopulation() * 0.04d);
			scheduleYoungAdultsParty(tmp, 1.2d, true, false);
			break;
			
		case 374: // 9 enero
			// Festejos entre jovenes - 0.8% de la poblacion a 1.2 cuadrados por persona, mitad afuera y mitad adentro
			tmp = (int) Math.round(Town.getLocalPopulation() * 0.008d);
			startRepeatingYoungAdultsParty(7, tmp, 1.2d, true, true);
			break;
		case 388: // 23 enero
			BuildingManager.limitActivitiesCapacity(3.5d);
			break;
		default:
			throw new InvalidParameterException("Dia de fase no implementada: " + phase);
		}
		// Si corresponde, suma la matriz de fin de semana a las nuevas matrices
		if (lockdownOverWKD)
			setHumansWeekendTMMC(true);  // para sumar la matriz de finde
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		// Ano simulacion, para calcular temperatura (2020 - 2022)
		simulationStartYear	= 2020;
		// Dia de inicio, desde la fecha 01/01/2020
		simulationStartDay	= ((Integer) params.getValue("diaInicioSimulacion")).intValue();
		// Dias maximo de simulacion - por mas que "diaMinimoSimulacion" sea mayor (0 = Infinito)
		simulationMaxTick	= ((Integer) params.getValue("diasSimulacion")).intValue() * 12;
		// Dias minimo de simulacion - por mas que supere "cantidadMuertosLimite" (0 ...)
		simulationMinDay	= ((Integer) params.getValue("diasMinimoSimulacion")).intValue();
		// Dias hasta primer sabado
		int daysFromSat = (4 + simulationStartDay) % 7; // el 4to dia de 2020 es sabado
		weekendStartTick = (daysFromSat == 0) ? 0 : (7 - daysFromSat) * 12;
		// Cantidad de muertos que debe superar para finalizar simulacion - ver "diaMinimoSimulacion" (0 = Infinito)
		deathLimit			= ((Integer) params.getValue("cantidadMuertosLimite")).intValue();
		// Dia de entrada de infectados
		outbreakStartTick	= ((Integer) params.getValue("diaEntradaCaso")).intValue() * 12;
		// Cantidad de infectados iniciales
		infectedAmount		= ((Integer) params.getValue("cantidadInfectados")).intValue();
		// Cantidad de corridas para hacer en batch
		simulationRun		= (Integer) params.getValue("corridas");
		// Nombre del municipio a simular
		townName			= (String) params.getString("nombreMunicipio");
	}

	private void loadParcelsShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(Town.getParcelsFilepath());
		homePlaces.clear();
		for (int i = 0; i < Town.sectoralsCount; i++) {
			homePlaces.add(new ArrayList<HomeAgent>());
		}
		maxParcelId = 0;
		
		HomeAgent tempBuilding = null;
		GeometryFactory geometryFactory = new GeometryFactory();
		
		BuildingManager.initManager(context, geography);

		double tempX, tempY;
		double maxX[] = new double[Town.sectoralsCount];
		double minX[] = new double[Town.sectoralsCount];
		double maxY[] = new double[Town.sectoralsCount];
		double minY[] = new double[Town.sectoralsCount];
		Arrays.fill(maxX, -180d);
		Arrays.fill(minX, 180d);
		Arrays.fill(maxY, -90d);
		Arrays.fill(minY, 90d);
		
		int id;
		int sectoral, sectoralType, sectoralIndex;
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Parcel invalid geometry: " + feature.getID());
				continue;
			}
			// Polygon - Formato Catastro Parana - modificado
			id = (int)feature.getAttribute("id");
			sectoral = (int)feature.getAttribute("sec");
			//group = (int)feature.getAttribute("gru");
			//block = (int)feature.getAttribute("manz");
			//parcel = (int)feature.getAttribute("parc");
			//area = (int)feature.getAttribute("area");
			//coveredArea = (int)feature.getAttribute("c_area");
			
			// Busca los valores min y max de X e Y
			// para crear el Extent o Boundary que incluye a todas las parcelas
			sectoralIndex = sectoral - 1;
			tempX = geom.getCoordinate().x;
			if (tempX > maxX[sectoralIndex])
				maxX[sectoralIndex] = tempX;
			else if (tempX < minX[sectoralIndex])
				minX[sectoralIndex] = tempX;
			tempY = geom.getCoordinate().y;
			if (tempY > maxY[sectoralIndex])
				maxY[sectoralIndex] = tempY;
			else if (tempY < minY[sectoralIndex])
				minY[sectoralIndex] = tempY;
			//

			// Convierto las geometrias Polygon a Point
			geom = geometryFactory.createPoint(geom.getCentroid().getCoordinate());
			
			// Guarda la ultima ID de parcela, para crear ficticias
			if (id > maxParcelId)
				maxParcelId = id;
			
			sectoralType = Town.sectoralsTypes[sectoral - 1];
			tempBuilding = new HomeAgent(sectoralType, sectoral - 1, geom.getCoordinate(), id);
			homePlaces.get(sectoral - 1).add(tempBuilding);
			context.add(tempBuilding);
			geography.move(tempBuilding, geom);
		}
		features.clear();
		
		BuildingManager.setBoundary(minX, maxX, minY, maxY);
	}
	
	private void loadPlacesShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(Town.getPlacesFilepath());
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		PlaceProperty placeProp;
		PlaceProperty placeSecProp;
		
		String type;
		String[] types;
		
		Coordinate coord;
		Coordinate[] coords = BuildingManager.getSectoralsCentre();
		double minDistance, tempDistance = 0d;
		int sectoralType, sectoralIndex = 0;
		int buildingArea;
		
		int schoolVacancies = 0, universityVacancies = 0, workVacancies = 0;
		
		//Name,type,ratings,latitude,longitude,place_id
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Place invalid geometry: " + feature.getID() + (int)feature.getAttribute("id"));
			}
			if (geom instanceof Point) {
				//id = (Long)feature.getAttribute("id");
				//name = (String) feature.getAttribute("name");
				type = (String) feature.getAttribute("type");
				//rating = (int) feature.getAttribute("rating");
				
				// Separar types y tomar el primero
				types = type.split("\\+");
				placeProp = placesProperty.get(types[0]);
				if (placeProp == null) {
					System.out.println("Type de Place desconocido: " + types[0]);
					continue;
				}
				type = types[0];
				buildingArea = placeProp.getBuildingArea();
				
				// Si tiene 2 types se suma el area del segundo
				if (types.length > 1) {
					placeSecProp = placesProperty.get(types[1]);
					if (placeSecProp != null) {
						buildingArea += placeSecProp.getBuildingArea();
					}
					else {
						System.out.println("Type secundario de Place desconocido: " + types[1]);
					}
				}
				
				// Buscar la seccional mas cercana para asignar a este Place
				minDistance = 180f;
				coord = geom.getCoordinate();
				for (int i = 0; i < coords.length; i++) {
					tempDistance = coords[i].distance(coord);
					if (tempDistance < minDistance) {
						minDistance = tempDistance;
						sectoralIndex = i;
					}
				}
				sectoralType = Town.sectoralsTypes[sectoralIndex];
				
				// Crear Agente con los atributos el Place
				WorkplaceAgent tempWorkspace = new WorkplaceAgent(sectoralType, sectoralIndex, coord, ++maxParcelId, type, placeProp.getActivityType(),
						buildingArea, placeProp.getBuildingCArea(), placeProp.getWorkersPerPlace(), placeProp.getWorkersPerArea());
				
				// Agrupar el Place con el resto del mismo type
				if (placeProp.getActivityType() == 1) { // trabajo / estudio
					if (type.contains("primary_school") || type.contains("secondary_school") || type.contains("technical_school")) {
						schoolPlaces.add(tempWorkspace);
						schoolVacancies += tempWorkspace.getVacancy();
					}
					else if (type.contains("university")) {
						universityPlaces.add(tempWorkspace);
						universityVacancies += tempWorkspace.getVacancy();
					}
					else {
						workPlaces.add(tempWorkspace);
						workVacancies += tempWorkspace.getVacancy();
					}
					// Si es lugar sin atencion al publico, se agrega a la lista de lugares de trabajo/estudio
					BuildingManager.addWorkplace(type, tempWorkspace);
				}
				else { // ocio, otros
					workPlaces.add(tempWorkspace);
					workVacancies += tempWorkspace.getVacancy();
					// Si es lugar con atencion al publico, se agrega a la lista de actividades
					BuildingManager.addPlace(sectoralIndex, tempWorkspace, placeProp);
				}
				
				// Agregar al contexto
				context.add(tempWorkspace);
				geography.move(tempWorkspace, geom);
			}
			else {
				System.err.println("Error creating agent for " + geom);
			}
		}
		features.clear();
		
		if (DEBUG_MSG) {
			System.out.println("CUPO ESTUDIANTES PRI/SEC: " + schoolVacancies);
			System.out.println("CUPO ESTUDIANTES TER/UNI: " + universityVacancies);
			System.out.println("CUPO TRABAJADORES: " + workVacancies);
		}
	}
	
	/**
	 * Lee el archivo GIS y retorna sus items en una lista.
	 * @param filename  ruta del archivo shape
	 * @return lista con SimpleFeatures
	 */
	private List<SimpleFeature> loadFeaturesFromShapefile(String filename) {
		URL url = null;
		try {
			url = new File(filename).toURI().toURL();
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}
		//
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		ShapefileDataStore store = new ShapefileDataStore(url);
		SimpleFeatureIterator fiter = null;
		try {
			fiter = store.getFeatureSource().getFeatures().features();
			while (fiter.hasNext()) {
				features.add(fiter.next());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			// liberar recursos
			fiter.close();
			store.dispose();
		}
		//
		return features;
	}
	
	/**
	 * Selecciona hogares al azar y los duplica, incrementando su id.
	 * @param secIndex
	 * @param extraHomes
	 */
	private void createFictitiousHomes(int secIndex, int extraHomes) {
		GeometryFactory geometryFactory = new GeometryFactory();
		HomeAgent tempBuilding, tempHome;
		int[] ciIndexes = IntStream.range(0, homePlaces.get(secIndex).size()).toArray();
		int indexesCount = homePlaces.get(secIndex).size()-1;
		int randomIndex;
		
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				randomIndex = RandomHelper.nextIntFromTo(0, indexesCount);
				tempHome = homePlaces.get(secIndex).get(ciIndexes[randomIndex]);
				ciIndexes[randomIndex] = ciIndexes[indexesCount--];
				//
				tempBuilding = new HomeAgent(tempHome);
				tempBuilding.setId(++maxParcelId);
				homePlaces.get(secIndex).add(tempBuilding);
				context.add(tempBuilding);
				geography.move(tempBuilding, geometryFactory.createPoint(tempBuilding.getCoordinate()));
			}
		}
	}
	
	/**
	 * Selecciona los ultimos hogares y los elimina.
	 * @param secIndex
	 * @param extraHomes
	 */
	private void deleteExtraHomes(int secIndex, int extraHomes) {
		HomeAgent tempHome;
		int indexesCount = homePlaces.get(secIndex).size()-1;
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				tempHome = homePlaces.get(secIndex).remove(indexesCount);
				context.remove(tempHome);
				--indexesCount;
			}
		}
	}
	
	/**
	 * Crea Humanos y asigna a cada uno un lugar aleatorio en la grilla, como posicion del hogar.
	 * @param secType
	 * @param secIndex
	 * @param ageGroup
	 * @param home
	 * @param work
	 * @return
	 */
	private HumanAgent createHuman(int secType, int secIndex, int ageGroup, BuildingAgent home, BuildingAgent work) {
		int[] workPos = null;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
		}
		HumanAgent tempHuman = new HumanAgent(secType, secIndex, ageGroup, home, work, workPos);
		context.add(tempHuman);
		return tempHuman;
	}
	
	/**
	 * Crea Humanos extranjeros, sin hogar pero con lugar de trabajo.
	 * @param ageGroup
	 * @param work
	 * @return
	 */
	private HumanAgent createForeignHuman(int ageGroup, BuildingAgent work) {
		int[] workPos = null;
		int secType = 0, secIndex = 0;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
			// Si tiene trabajo se le asigna como hogar la seccional del lugar donde trabaja
			secType = work.getSectoralType();
			secIndex = work.getSectoralIndex();
		}
		HumanAgent tempHuman = new ForeignHumanAgent(secType, secIndex, ageGroup, work, workPos);
		context.add(tempHuman);
		return tempHuman;
	}

	/**
	 * Crea Humanos dependiendo la franja etaria, lugar de trabajo y vivienda.
	 */
	private void initHumans() {
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		setDefaultTMMC();
		BuildingAgent.initInfAndPDRadius(); // Crea posiciones de infeccion en grilla
		
		int[][] locals = new int[Town.sectoralsCount][DataSet.AGE_GROUPS];
		int[][] localTravelers = new int[Town.sectoralsCount][DataSet.AGE_GROUPS];
		
		localHumansCount = 0;
		localHumansIndex = new int[DataSet.AGE_GROUPS];
		int localCount;
		int localTravelersCount;
		int sectoralType;
		int i, j, k;
		for (i = 0; i < Town.sectoralsCount; i++) {
			sectoralType = Town.sectoralsTypes[i];
			localCount = (int) (((Town.localHumans + Town.localTravelerHumans) * Town.sectoralsPopulation[i]) / 100);
			localTravelersCount = (int) ((Town.localTravelerHumans * Town.sectoralsPopulation[i]) / 100);
			int extraHomes = (int) (localCount / DataSet.HOUSE_INHABITANTS_MEAN[sectoralType]) - homePlaces.get(i).size();
			if (extraHomes > 0) {
				createFictitiousHomes(i, extraHomes);
				if (DEBUG_MSG)
					System.out.println("HOGARES PROMEDIO FALTANTES EN SEC " + (i+1) + ": " + extraHomes);
			}
			else if (extraHomes < -1) {
				deleteExtraHomes(i, extraHomes*-1);
				if (DEBUG_MSG)
					System.out.println("HOGARES PROMEDIO SOBRANTES EN SEC " + (i+1) + ": " + extraHomes*-1);
			}
			// Crear humanos que viven y trabajan/estudian en contexto
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				locals[i][j] = (int) Math.ceil((localCount * DataSet.HUMANS_PER_AGE_GROUP[j]) / 100);
				localHumansCount += locals[i][j];
				localHumansIndex[j] += locals[i][j];
				localTravelers[i][j] = (int) Math.ceil((localTravelersCount * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[sectoralType][j]) / 100);
				locals[i][j] -= localTravelers[i][j];
			}
			//
		}
		
		int foreignCount = Town.foreignTravelerHumans;
		int[] foreignTravelers = new int[DataSet.AGE_GROUPS];
		foreignHumansCount = 0;
		// Calcular la cantidad de humanos que vive fuera y trabaja/estudia en contexto
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			foreignTravelers[i] = (int) Math.ceil((foreignCount * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[0][i]) / 100);
			foreignHumansCount += foreignTravelers[i];
		}
		//
		
		contextHumans = new HumanAgent[localHumansCount + foreignHumansCount];
		// Calcula el indice en contextHumans donde comienza cada grupo etario
		int[] humIdx = new int[DataSet.AGE_GROUPS]; // Indice desde cual ubicar cada grupo etario
		for (j = 1; j < DataSet.AGE_GROUPS; j++) {
			localHumansIndex[j] += localHumansIndex[j - 1];
			humIdx[j] = localHumansIndex[j - 1];
		}
		
		HumanAgent tempHuman = null;
		HomeAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		noncollegiateCount = 0;
		loadOccupationalNumbers();
		
		Uniform disUniHomesIndex;
		// Primero se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < Town.sectoralsCount; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			sectoralType = Town.sectoralsTypes[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < localTravelers[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					//
					tempHuman = createHuman(sectoralType, i, j, tempHome, null);
					tempHome.addOccupant(tempHuman);
					contextHumans[humIdx[j]++] = tempHuman;
					// Se resta primero la capacidad de estudiantes primario, universitarios y por ultimo trabajadores
					if (schooledCount[sectoralType][j] > 0)
						--schooledCount[sectoralType][j];
					else if (collegiateCount[sectoralType][j] > 0)
						--collegiateCount[sectoralType][j];
					else if (employedCount[sectoralType][j] > 0)
						--employedCount[sectoralType][j];
				}
			}
		}
		
		// Segundo se crean los 100% locales
		for (i = 0; i < Town.sectoralsCount; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			sectoralType = Town.sectoralsTypes[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < locals[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					tempJob = findAGWorkingPlace(sectoralType, j, tempHome);
					//
					tempHuman = createHuman(sectoralType, i, j, tempHome, tempJob);
					tempHome.addOccupant(tempHuman);
					contextHumans[humIdx[j]++] = tempHuman;
				}
			}
		}
		
		int foreignInd = localHumansCount; // Primer indice en contextHumans de extranjeros
		// Por ultimo se crean los extranjeros, si ya no quedan cupos de trabajo trabajan fuera
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < foreignTravelers[i]; j++) {
				tempJob = findAGWorkingPlace(0, i);
				contextHumans[foreignInd++] = createForeignHuman(i, tempJob);
			}
		}
		
		if (DEBUG_MSG) {
			if (unschooledCount != 0)
				System.out.println("CUPO ESTUDIANTES PRI/SEC FALTANTES: " + unschooledCount);
			if (noncollegiateCount != 0)
				System.out.println("CUPO ESTUDIANTES TER/UNI FALTANTES: " + noncollegiateCount);
			if (unemployedCount != 0)
				System.out.println("CUPO TRABAJADORES FALTANTES: " + unemployedCount);
		}
	}
	
	/**
	 * Calcula la cantidad de estudiantes y trabajadores locales
	 */
	private void loadOccupationalNumbers() {
		employedCount = new int[2][DataSet.AGE_GROUPS];
		schooledCount = new int[2][DataSet.AGE_GROUPS];
		collegiateCount = new int[2][DataSet.AGE_GROUPS];
		//
		int i, j;
		double[] percPP = {0d, 0d};
		for (i = 0; i < Town.sectoralsCount; i++) {
			percPP[Town.sectoralsTypes[i]] += Town.sectoralsPopulation[i];
		}
		//
		int humansCount;
		int schooledSum = 0, collegiateSum = 0, employedSum = 0;
		for (i = 0; i < 2; i++) {
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				humansCount = (int) Math.ceil((Town.localHumans + Town.localTravelerHumans) * percPP[i] * DataSet.HUMANS_PER_AGE_GROUP[j] / 10000);
				switch (j) {
				case 0: // todos primarios/secundarios
					schooledCount[i][j] = (int) Math.ceil((humansCount * DataSet.OCCUPATION_PER_AGE_GROUP[i][j][0]) / 100);
					break;
				case 1: // 45% primarios/secundarios, resto universitarios
					schooledCount[i][j] = (int) Math.ceil((humansCount * (DataSet.OCCUPATION_PER_AGE_GROUP[i][j][0] * 0.45)) / 100);
					collegiateCount[i][j] = (int) Math.ceil((humansCount * DataSet.OCCUPATION_PER_AGE_GROUP[i][j][0]) / 100) - schooledCount[i][j];
					break;
				default:// todos universitarios
					collegiateCount[i][j] = (int) Math.ceil((humansCount * DataSet.OCCUPATION_PER_AGE_GROUP[i][j][0]) / 100);
					break;
				}
				employedCount[i][j] = (int) Math.ceil((humansCount * DataSet.OCCUPATION_PER_AGE_GROUP[i][j][1]) / 100);
				//
				schooledSum += schooledCount[i][j];
				collegiateSum += collegiateCount[i][j];
				employedSum += employedCount[i][j];
			}
		}
		
		if (DEBUG_MSG) {
			System.out.println("ESTUDIANTES LOCALES PRI/SEC: " + schooledSum);
			System.out.println("ESTUDIANTES LOCALES TER/UNI: " + collegiateSum);
			System.out.println("TRABAJADORES LOCALES: " + employedSum);
		}
	}
	
	/**
	 * Busca lugar de trabajo/estudio si hay cupo para el tipo de seccional y franja etaria del humano<p>
	 * @param secType
	 * @param ageGroup
	 * @param home 
	 * @return WorkplaceAgent, BuildingAgent o null
	 */
	private BuildingAgent findAGWorkingPlace(int secType, int ageGroup, BuildingAgent home) {
		BuildingAgent workplace = null;
		//
		if (schooledCount[secType][ageGroup] > 0) { // estudiante primario/secundario
			--schooledCount[secType][ageGroup];
    		workplace = findWorkingPlace(schoolPlaces);
        	if (workplace == null) {
        		workplace = home;
        		++unschooledCount;
        	}
		}
		else if (collegiateCount[secType][ageGroup] > 0) { // estudiante universitario
			--collegiateCount[secType][ageGroup];
    		workplace = findWorkingPlace(universityPlaces);
        	if (workplace == null) {
        		workplace = home;
        		++noncollegiateCount;
        	}
		}
		else if (employedCount[secType][ageGroup] > 0) { // trabajor
			--employedCount[secType][ageGroup];
        	int wp = RandomHelper.nextIntFromTo(1, 100);
        	// Primero ver si tiene un trabajo convencional
        	if (wp <= DataSet.WORKING_FROM_HOME[secType] + DataSet.WORKING_OUTDOORS[secType]) {
        		// Si no, puede trabajar en la casa o al exterior
        		wp = RandomHelper.nextIntFromTo(1, DataSet.WORKING_FROM_HOME[secType] + DataSet.WORKING_OUTDOORS[secType]);
        		if (wp <= DataSet.WORKING_FROM_HOME[secType])
        			workplace = home;
        		else
        			workplace = null;
        	}
        	else {
	        	workplace = findWorkingPlace(workPlaces);
	        	if (workplace == null)
	        		++unemployedCount;
        	}
		}
		else { // inactivo
			workplace = home;
		}
		//
		return workplace;
	}
	
	/**
	 * Busca lugar de trabajo/estudio para extranjeros segun tipo de seccional, franja etaria y ocupacion<p>
	 * @param secType
	 * @param ageGroup
	 * @return WorkplaceAgent, BuildingAgent o null
	 */
	private BuildingAgent findAGWorkingPlace(int secType, int ageGroup) {
		BuildingAgent workplace = null; // inactivo por defecto
		//
		double occupation[] = DataSet.OCCUPATION_PER_AGE_GROUP[secType][ageGroup];
		int r = RandomHelper.nextIntFromTo(1, 100);
		int i = 0;
        while (r > occupation[i]) {
        	r -= occupation[i];
        	++i;
        }
        if (i == 0) { // estudiante
        	if (ageGroup == 0 || (ageGroup == 1 && (occupation[i] - r < occupation[i]*.45d))) { // 45% es primario/secundario
        		workplace = findWorkingPlace(schoolPlaces);
        	}
        	else {
        		workplace = findWorkingPlace(universityPlaces);
        	}
        }
        else if (i == 1) { // trabajor
	        workplace = findWorkingPlace(workPlaces);
        }
		//
		return workplace;
	}
	
	/**
	 * Busca y resta una posicion de trabajador en la lista de lugares.
	 * @param list
	 * @return WorkplaceAgent o null
	 */
	private WorkplaceAgent findWorkingPlace(List<WorkplaceAgent> list) {
		int index;
		WorkplaceAgent workplace = null;
		if (!list.isEmpty()) {
			index = RandomHelper.nextIntFromTo(0, list.size()-1);
			workplace = list.get(index);
			workplace.reduceVacancies();
			if (!workplace.vacancyAvailable())
				list.remove(index);
		}
		return workplace;
	}
	
	/**
	 * Imprime la hora de compilacion del jar (si existe).
	 * @param cl
	 */
	private static void printJarVersion(Class<?> cl) {
	    try {
	        String rn = cl.getName().replace('.', '/') + ".class";
	        JarURLConnection j = (JarURLConnection) cl.getClassLoader().getResource(rn).openConnection();
	        long totalMS = j.getJarFile().getEntry("META-INF/MANIFEST.MF").getTime();
	        // Convierte de ms a formato fecha hora
			SimpleDateFormat sdFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			System.out.println("Fecha y hora de compilacion: " + sdFormat.format(totalMS));
	    } catch (Exception e) {
	    	// Si no es jar, no imprime hora de compilacion
	    }
	}
}
