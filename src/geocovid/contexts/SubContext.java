package geocovid.contexts;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import cern.jet.random.Uniform;
import geocovid.BuildingManager;
import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.PlaceProperty;
import geocovid.Town;
import geocovid.agents.BuildingAgent;
import geocovid.agents.ClassroomAgent;
import geocovid.agents.ForeignHumanAgent;
import geocovid.agents.HomeAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.PublicTransportAgent;
import geocovid.agents.WorkplaceAgent;
import repast.simphony.context.DefaultContext;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;

/**
 * Contexto que representa a un municipio.
 */
public abstract class SubContext extends DefaultContext<Object> {
	private long lastHomeId;	// Para no repetir ids, al crear hogares ficticios
	private List<List<HomeAgent>> homePlaces;	// Lista de hogares en cada seccional
	private List<WorkplaceAgent> workPlaces = new ArrayList<WorkplaceAgent>();		// Lista de lugares de trabajo
	private List<WorkplaceAgent> schoolPlaces = new ArrayList<WorkplaceAgent>();	// Lista de lugares de estudio primario/secundario (aulas)
	private List<WorkplaceAgent> universityPlaces = new ArrayList<WorkplaceAgent>();// Lista de lugares de estudio terciario/universitario
	
	private HumanAgent[] contextHumans;	// Array de HumanAgent parte del contexto
	private int localHumansCount;		// Cantidad de humanos que viven en contexto
	private int foreignHumansCount;		// Cantidad de humanos que viven fuera del contexto
	private int[] localHumansIndex;		// Indice en contextHumans de caga grupo etario (local)
	
	private ForeignHumanAgent[] touristHumans;	// Array de HumanAgent temporales/turistas
	private int[] lodgingPlacesSI;				// Seccionales donde existen lugares de hospedaje
	private ISchedulableAction touristSeasonAction;
	private ISchedulableAction youngAdultsPartyAction;
	private ISchedulableAction startSchoolProtocolAction;
	
	protected Map<String, PlaceProperty> placesProperty = new HashMap<>(); // Lista de atributos de cada tipo de Place
	protected BuildingManager buildingManager;
	protected Town town;
	
	private ISchedule schedule;
	private static Geography<Object> geography;
	
	private int[][] employedCount;	// Cupo de trabajadores
	private int[][] schooledCount;	// Cupo de estudiantes pri/sec
	private int[][] collegiateCount;// Cupo de estudiantes ter/uni
	private int employedSum;		// Total de trabajadores
	private int schooledSum;		// Total de estudiantes pri/sec
	private int collegiateSum;		// Total de estudiantes ter/uni
	
	private int unemployedCount;	// Contador de empleos faltantes
	private int unschooledCount;	// Contador de bancos faltantes en escuelas
	private int noncollegiateCount; // Contador de bancos faltantes en universidades
	
	private double maskInfRateReduction;	// Fraccion de reduccion de beta al usar barbijo
	private boolean wearMaskOutdoor;		// Si al aire libre se usa tapaboca
	private boolean wearMaskWorkspace;		// Si en oficinas y aulas usan tapaboca entre empleados y alumnos
	private boolean wearMaskCustomerService;// Si en lugares de atencion al publico usan tapaboca entre empleados
	private boolean wearMaskCustomer;		// Si en lugares de otros/ocio usan tapaboca entre clientes y empleados
	
	private Set<Integer> socialDistIndexes;	// Lista con ids de humanos que respetan distanciamiento
	private int socialDistPercentage;		// Porcentaje de la poblacion que respeta el distanciamiento social
	private boolean socialDistOutdoor;		// Si al aire libre se respeta el distanciamiento social
	private boolean socialDistWorkspace;	// Si entre empleados respetan el distanciamiento social
	
	private boolean closeContactsEnabled;	// Habilitada la "pre infeccion" de contactos estrechos y cuarentena preventiva de los mismos
	private boolean prevQuarantineEnabled;	// Habilitada la cuarentena preventiva para las personas que conviven con un sintomatico
	
	private boolean outbreakStarted;		// Indica que ya se infectaron locales
	
	static final boolean DEBUG_MSG = false;	// Flag para imprimir valores de inicializacion
	//
	
	public abstract int[][][] getIsolatedLocalTMMC(int ageGroup);
	public abstract int[][][] getLocalTMMC(int sectoralType, int ageGroup);
	
	/**
	 * Setea las caracteristicas de una nueva fase.
	 * @param phase dia
	 */
	public abstract void updateLockdownPhase(int phase);
	public abstract double[][] getOccupationPerAG(int secType);
	public abstract int getWorkingFromHome(int secType);
	public abstract int getWorkingOutdoors(int secType);
	public abstract double getHouseInhabitantsMean(int secType);
	public abstract double[] getLocalHumansPerAG(int secType);
	public abstract double[] getForeignHumansPerAG();
	public abstract int getHomeBuldingArea(int secType);
	public abstract int getHomeBuldingCoveredArea(int secType);
	
	public abstract int travelOutsideChance(int secType);

	public SubContext(Town contextTown) {
		super(contextTown.townName);
		this.town = contextTown;
	}
	
	public static void setGeography(Geography<Object> geo) {
		geography = geo;
	}
	
	public BuildingManager getBuildManager() {
		return buildingManager;
	}
	
	public int getTownRegion() {
		return town.regionIndex;
	}
	
	/**
	 * Inicializa contexto. Crea parcelas, places, humanos y asigna hogares y lugares de trabajo.
	 */
	public void init() {
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
		// Crea Schedule one shot para agregar infectados el dia de ingreso del primero, o el primer dia de simulacion (si ingresa antes)  
		ScheduleParameters params = ScheduleParameters.createOneTime(
				(InfectionReport.simulationStartDay > town.outbreakStartDay ? 0 : (town.outbreakStartDay - InfectionReport.simulationStartDay) * 24), 0.9d);
		schedule.schedule(params, this, "infectLocalRandos", Town.firstInfectedAmount);
		
		// Programa los cambios de fases, pasadas y futuras
		int phaseDay;
		int[] phasesStartDay = town.lockdownPhasesDays;
		for (int i = 0; i < phasesStartDay.length; i++) {
			phaseDay = phasesStartDay[i] - InfectionReport.simulationStartDay;
			if (phaseDay > 0)	// Fase futura
				phaseDay *= 24;
			else				// Fase pasada
				phaseDay = 0;
			params = ScheduleParameters.createOneTime(phaseDay, 0.9d);
			schedule.schedule(params, this, "initiateLockdownPhase", phasesStartDay[i]);
		}
		
		// Crea BuildingManager para esta ciudad
		buildingManager = new BuildingManager(this, town.sectoralsCount);
		
		// Lee y carga en lista las parcelas hogares
		homePlaces = new ArrayList<List<HomeAgent>>(town.sectoralsCount);
		loadParcelsShapefile();
		// Lee y carga en BuildingManager los places
		loadPlacesShapefile();
		// Creacion de colectivos
		createPublicTransportUnits();
		
		loadOccupationalNumbers();
		createSchoolClassrooms();
		buildingManager.createActivitiesChances();
		
		// Por ultimo crea agentes humanos, les asigna trabajo/estudio y los distribuye en hogares
		initHumans();
		
		// Limpia listas temporales
		homePlaces.clear();
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
	}
	
	/**
	 * Cambia estado de markov de todos los HumanAgents en sub contexto.<p>
	 * Para usar como Schedulable Action
	 */
	public void switchHumanLocation() {
		Stream<Object> iteral = getObjectsAsStream(HumanAgent.class);
		iteral.forEach(h -> ((HumanAgent) h).switchLocation());
	}
	
	/**
	 * Calcula el promedio de contactos diarios de los HumanAgents en sub contexto.<p>
	 * Para usar como Schedulable Action
	 */
	public void computeAvgSocialInteractions() {
		int[] sumOfSocialInteractions	= new int[DataSet.AGE_GROUPS];
		int[] humansInteracting			= new int[DataSet.AGE_GROUPS];
		double[] avgSocialInteractions	= new double[DataSet.AGE_GROUPS];
		// Suma los contactos de cada humano por franja etaria
		Stream<Object> iteral = getObjectsAsStream(HumanAgent.class);
		iteral.forEach(
		h -> {
			HumanAgent human = ((HumanAgent) h);
			int interactions = human.getSocialInteractions();
			int ageGroup = human.getAgeGroup();
			if (!human.isForeign()) { // si es extranjero no cuenta
				sumOfSocialInteractions[ageGroup] += interactions;
				++humansInteracting[ageGroup];
			}
        });
		// Calcula el promedio para cada franja etaria
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			avgSocialInteractions[i] = sumOfSocialInteractions[i] / (double)humansInteracting[i];
		}
		InfectionReport.updateSocialInteractions(avgSocialInteractions);
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos locales seteada en los parametros y los infecta.
	 * @param amount cantidad de infectados iniciales
	 */
	public void infectLocalRandos(int amount) {
		if (amount == 0)
			return;
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
		// A partir de ahora hay riesgo de infeccion local fuera de parcelas
		outbreakStarted = true;
	}
	
	/**
	 * Setear temporada turistica, para simular el ingreso de turistas
	 * @param duration dias totales de turismo
	 * @param interval dias de intervalo entre grupo de turistas
	 * @param touristAmount cantidad por grupo de turistas
	 * @param infectedFraction fraccion de infectados asintomaticos por grupo de turistas
	 */
	protected void setTouristSeason(int duration, int interval, int touristAmount, double infectedFraction) {
		if (!town.touristSeasonAllowed)
			return;

		int infectedAmount = (int) Math.round(touristAmount * infectedFraction);
		// Si es la primer simulacion
		if (lodgingPlacesSI == null) { // array con indices de seccionales de Places tipo lodging
			// Recorrer los lugares de alojamiento y guardar los indices de seccional
			List<WorkplaceAgent> lodgingPlaces = buildingManager.getWorkplaces("lodging");
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
		params = ScheduleParameters.createRepeating(schedule.getTickCount(), (interval * 24), ScheduleParameters.FIRST_PRIORITY);
		touristSeasonAction = schedule.schedule(params, this, "newTouristGroup", touristAmount, infectedAmount);
		params = ScheduleParameters.createOneTime(schedule.getTickCount() + (duration * 24) - 0.1d, ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "endTouristSeason");
	}
	
	/**
	 * Crea un nuevo grupo de turistas segun los parametros dados.<p>
	 * Si ya existia un grupo, lo elimina del contexto.
	 * @param tourist cantidad turistas
	 * @param infected cantidad infectados
	 */
	public void newTouristGroup(int tourist, int infected) {
		// Si ya existe el array de turistas, sacar del contexto el grupo anterior
		if (touristHumans != null) {
			for (ForeignHumanAgent human : touristHumans)
				human.removeFromContext();
		}
		// Si no existe el array de turistas, crear segun tamano del grupo 
		else
			touristHumans = new ForeignHumanAgent[tourist];
		
		int secIndex;
		ForeignHumanAgent tempHuman;
		// Crear Humanos turista, agregarlos al contexto y al array 
		for (int i = 0; i < tourist; i++) {
			// Seleccionar al azar una seccional de la lista de lugares de alojamiento
			secIndex = lodgingPlacesSI[RandomHelper.nextIntFromTo(0, lodgingPlacesSI.length-1)];
			tempHuman = new ForeignHumanAgent(this, secIndex);
			if (i < infected)
				tempHuman.setInfectious(true, false); // Asintomatico
			add(tempHuman);
			touristHumans[i] = tempHuman;
		}
	}
	
	/**
	 * Finaliza la temporada de turismo, elimina del contexto el ultimo grupo.
	 */
	public void endTouristSeason() {
		// Eliminar la accion programada que renueva el grupo de turistas y sacar del contexto el ultimo grupo
		schedule.removeAction(touristSeasonAction);
		if (touristHumans != null) {
			for (HumanAgent tourist : touristHumans)
				tourist.removeFromContext();
		}
		// Por las dudas limpiar turistas
		touristHumans = null;
	}

	/**
	 * Arma un listado con Humanos seleccionados al azar, segun cupo de cada franja etaria y que no estan aislados.
	 * @param events cantidad de eventos
	 * @param humansPerEvent cantidad de Humanos por evento
	 * @param humansByAG las cantidades de Humanos por franja etaria a seleccionar por evento
	 * @return array <b>HumanAgent[][]</b>
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
			places = buildingManager.getActivityBuildings(maxPlaces, placeProp.getGooglePlaceType(), placeProp.getGoogleMapsType());
		else
			places = buildingManager.getActivityBuildings(maxPlaces, placeProp.getGooglePlaceType());
		
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
	 * @param outdoor si el evento sucede al aire libre (indoor en true para mitad y mitad)
	 * @param indoor si el evento sucede en lugar cerrado (outdoor en true para mitad y mitad)
	 * @param events cantidad de eventos a crear
	 * @param humansPerEvent cantidad de Humanos por evento
	 * @param ageGroupPerc porcentaje de Humanos por franja etaria
	 * @param ticksDuration duracion del evento en ticks
	 */
	public void scheduleForcedEvent(int realArea, boolean outdoor, boolean indoor, int events, int humansPerEvent, int[] ageGroupPerc, int ticksDuration) {
		int[] agPerBuilding = new int[ageGroupPerc.length];
	    int adjHumPerPlace = 0; // Cantidad ajustada de Humanos por Place (tendria que dar igual)
		double doubleP, decimalRest = 0d;
		int integerP;
	    for (int i = 0; i < ageGroupPerc.length; i++) {
	    	// Calculo la cantidad para esta franja etaria
	    	doubleP = (humansPerEvent * ageGroupPerc[i]) / 100d;
	    	// Separo la parte entera
	    	integerP = (int) doubleP;
	    	// Sumo la parte decimal que sobra 
	    	decimalRest += doubleP - integerP;
	    	// Si los restos decimales suman 1 o mas, se suma un entero
	    	if (decimalRest >= 1d) {
	    		decimalRest -= 0.99d;
	    		++integerP;
	    	}
	    	agPerBuilding[i] = integerP;
	    	adjHumPerPlace += agPerBuilding[i];
	    }
		
		HumanAgent[][] activeHumans = gatherHumansForEvent(events, adjHumPerPlace, agPerBuilding);
		
		List<BuildingAgent> buildings = new ArrayList<BuildingAgent>();
		Coordinate[] coords = buildingManager.getSectoralsCentre();
		int sectoralType, sectoralIndex;
		boolean paramOutdoor = outdoor; // para usar como parametro y poder invertir
		// Crea las parcelas ficticias en seccionales aleatorias
		for (int i = 0; i < events; i++) {
			sectoralIndex = RandomHelper.nextIntFromTo(0, town.sectoralsCount - 1);
			sectoralType = town.sectoralsTypes[sectoralIndex];
			buildings.add(new BuildingAgent(this, sectoralType, sectoralIndex, coords[sectoralIndex], realArea, paramOutdoor));
			if (outdoor && indoor) // mitad y mitad
				paramOutdoor = !paramOutdoor;
		}
		
		distributeHumansInEvent(buildings, activeHumans, ticksDuration);
	}
	
	/**
	 * Retorna el mayor divisor del numero dado, que sea menor o igual al maximo.
	 */
	private int getDivisor(int number, int max) {
		int result;
		// Si esta dentro del rango minimo
		if (number < 2 || max < 2)
			return 1;
		for (int i = 2; i <= Math.sqrt(number); i++) {
			if (number % i == 0) {
				result = number / i;
				if (result <= max)
					return result;
			}
		}
		// Es numero primo
		return getDivisor(number - 1, max);
	}
	
	/**
	 * Segun los parametros calcula la cantidad de eventos, participantes en c/u y area neta.
	 * @param humans cantidad de participantes total
	 * @param sqPerHuman posiciones por humano
	 * @return <b>int[]</b> con area de eventos, cantidad de eventos y humanos por evento
	 */
	private int[] getEventParameters(int humans, double sqPerHuman) {
		int humansPerEvent = getDivisor(humans, 250); // humanos por fiesta
		int events = humans / humansPerEvent; // cantidad de fiestas
	    int area = (int) ((humansPerEvent / DataSet.HUMANS_PER_SQUARE_METER) * sqPerHuman) + 1; // metros del area
	    return new int[] {area, events, humansPerEvent};
	}
	
	/**
	 * Programa fiestas entre jovenes adultos segun los parametros dados.
	 * @param humansTotal cantidad total de humanos en fiestas 
	 * @param sqPerHuman cuadrados por humano
	 * @param outdoor si las fiestas son al aire libre (indoor en true para mitad y mitad)
	 * @param indoor si las fiestas son en lugares cerrados (outdoor en true para mitad y mitad)
	 */
	public void scheduleYoungAdultsParty(int humansTotal, double sqPerHuman, boolean outdoor, boolean indoor) {
		int[] eventParams = getEventParameters(humansTotal, sqPerHuman);
	    // Programa el evento
	    scheduleForcedEvent(eventParams[0], outdoor, indoor, eventParams[1], eventParams[2], new int[] {0,65,35,0,0}, 4); // 6 ticks = 4 horas
	}
	
	/**
	 * Programa por tiempo indeterminado la creacion de fiestas entre jovenes adultos segun los parametros dados.
	 * @param interval dias entre eventos
	 * @param humansTotal cantidad total de humanos en fiestas 
	 * @param sqPerHuman cuadrados por humano
	 * @param outdoor si las fiestas son al aire libre
	 * @param indoor si las fiestas son en lugares cerrados
	 */
	protected void startRepeatingYoungAdultsParty(int interval, int humansTotal, double sqPerHuman, boolean outdoor, boolean indoor) {
		int[] eventParams = getEventParameters(humansTotal, sqPerHuman);
		// Programa el evento
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(schedule.getTickCount(), (interval * 24), ScheduleParameters.FIRST_PRIORITY);
		youngAdultsPartyAction = schedule.schedule(params, this, "scheduleForcedEvent",
				eventParams[0], outdoor, indoor, eventParams[1], eventParams[2], new int[] {0,65,35,0,0}, 4); // 6 ticks = 4 horas
	}
	
	/**
	 * Detiene la creacion programada de fiestas entre jovenes adultos.
	 */
	protected void stopRepeatingYoungAdultsParty() {
		if (!removeYAPartyAction()) {
			ScheduleParameters params;
			params = ScheduleParameters.createOneTime(schedule.getTickCount() + 0.1d);
			schedule.schedule(params, this, "removeYAPartyAction");
		}
	}
	
	/**
	 * Eliminar la accion programada que crea fiestas para jovenes adultos
	 * @return <b>true</b> si se elimino la accion
	 */
	public boolean removeYAPartyAction() {
		return schedule.removeAction(youngAdultsPartyAction);
	}
			 
	/*
	 * Activa y programa por tiempo indeterminado la asistencia alternada semana a semana de las burbuhas de alumnos.setchoolProtocol
	 */
	public void setSchoolProtocol(boolean protocol) {
		if (protocol) {	
			ClassroomAgent.setSchoolProtocol(true);
			ScheduleParameters params;
			params = ScheduleParameters.createRepeating(schedule.getTickCount(), DataSet.WEEKLY_TICKS, ScheduleParameters.FIRST_PRIORITY);
			startSchoolProtocolAction = schedule.schedule(params, this, "callSwitchStudyGroup");
		}
		else {
			disableSchoolProtocol();
		}
	}
		
	public void callSwitchStudyGroup() {
		ClassroomAgent.switchStudyGroup();
	}

	/**
	 * Detiene el procolo burbuja en escuelas.
	 */
	private void disableSchoolProtocol() {
		if (!removeSchoolProtocolAction()) {
			ScheduleParameters params;
			params = ScheduleParameters.createOneTime(schedule.getTickCount() + 0.1d);
			schedule.schedule(params, this, "removeSchoolProtocolAction");
		}
	}
			
	/**
	 * Eliminar la accion programada para protocolo burbuja
	 * @return <b>true</b> si se elimino la accion
	 */
	public boolean removeSchoolProtocolAction() {
		ClassroomAgent.setSchoolProtocol(false);
		return schedule.removeAction(startSchoolProtocolAction);
	}
	
	/**
	 * Setea aleatoriamente a Humanos si respetan el distanciamiento social, segun porcentaje de la poblacion dado.
	 * @param porc porcentaje que se distancia
	 */
	protected void setSocialDistancing(int porc) {
		int newAmount = (int) Math.ceil((localHumansCount + foreignHumansCount) * porc) / 100;
		int oldAmount = 0;
		// El porcentaje era cero
		if (getSDPercentage() == 0 && porc > 0) {
			socialDistIndexes = new HashSet<Integer>(newAmount);
		}
		else
			oldAmount = socialDistIndexes.size();
		setSDPercentage(porc);
		
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
	 * Toma las parcelas del shapefile, crea los hogares y los posiciona en el mapa.
	 */
	private void loadParcelsShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(town.getParcelsFilepath());
		// Reinicia la lista de hogares y el id
		homePlaces.clear();
		lastHomeId = 0;
		// Crea la lista de hogares para cada seccional 
		homePlaces.clear();
		for (int i = 0; i < town.sectoralsCount; i++) {
			homePlaces.add(new ArrayList<HomeAgent>());
		}
		
		// Vectores de coordinadas maximas y minimas para cada seccional
		double maxX[] = new double[town.sectoralsCount];
		double minX[] = new double[town.sectoralsCount];
		double maxY[] = new double[town.sectoralsCount];
		double minY[] = new double[town.sectoralsCount];
		Arrays.fill(maxX, -180d);
		Arrays.fill(minX, 180d);
		Arrays.fill(maxY, -90d);
		Arrays.fill(minY, 90d);
		
		int id;
		int sectoral, sectoralType, sectoralIndex;
		double tempX, tempY;
		HomeAgent tempBuilding = null;
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Parcel invalid geometry: " + feature.getID());
				continue;
			}
			if (geom instanceof Point) {
				id = (int)feature.getAttribute("id");
				sectoral = (int)feature.getAttribute("sec");
				
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
	
				// Guarda la ultima ID de parcela, para crear ficticias
				if (id > lastHomeId)
					lastHomeId = id;
				
				// Crea el HomeAgent con los datos obtenidos
				sectoralType = town.sectoralsTypes[sectoral - 1];
				tempBuilding = new HomeAgent(this, sectoralType, sectoral - 1, geom.getCoordinate(), id, getHomeBuldingArea(sectoralType), getHomeBuldingCoveredArea(sectoralType));
				homePlaces.get(sectoral - 1).add(tempBuilding);
				add(tempBuilding);
				geography.move(tempBuilding, geom);
			}
			else {
				System.err.println("Error creating agent for " + geom);
			}
		}
		features.clear();
		
		// Setea los limites de cada seccional
		buildingManager.setBoundary(minX, maxX, minY, maxY);
	}
	
	/**
	 * Toma los places del shapefile, crea los lugares de trabajo y los posiciona en el mapa.<p>
	 * Carga en BuildingManager los places segun su tipo.
	 */
	private void loadPlacesShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(town.getPlacesFilepath());
		// Reinicia las listas de lugares de trabajo y estudio
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		// Variables temporales
		PlaceProperty placeProp;
		PlaceProperty placeSecProp;
		String type;
		String[] types;
		Coordinate coord;
		Coordinate[] coords = buildingManager.getSectoralsCentre();
		double minDistance, tempDistance;
		int sectoralType, sectoralIndex = 0;
		int buildingArea;
		//
		@SuppressWarnings("unused")
		int universityVacancies = 0, workVacancies = 0;
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Place invalid geometry: " + feature.getID() + (int)feature.getAttribute("id"));
				continue;
			}
			if (geom instanceof Point) {
				type = (String) feature.getAttribute("type");
				
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
				sectoralType = town.sectoralsTypes[sectoralIndex];
				
				// Crear Agente con los atributos el Place
				WorkplaceAgent tempWorkspace = new WorkplaceAgent(this, sectoralType, sectoralIndex, coord, ++lastHomeId, type, placeProp.getActivityState(),
						buildingArea, placeProp.getBuildingCArea(), placeProp.getWorkersPerPlace(), placeProp.getWorkersPerArea());
				
				// Agrupar el Place con el resto del mismo type
				if (placeProp.getActivityState() == 1) { // trabajo / estudio
					if (type.contains("primary_school") || type.contains("secondary_school")) {
						// Creo un ClassroomAgent por escuela para que figure en GIS y luego crear clones 
						tempWorkspace = new ClassroomAgent(this, sectoralType, sectoralIndex, coord, lastHomeId, type); // cambio WorkplaceAgent por ClassroomAgent
						schoolPlaces.add(tempWorkspace);
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
					buildingManager.addWorkplace(type, tempWorkspace);
				}
				else { // ocio, otros
					workPlaces.add(tempWorkspace);
					workVacancies += tempWorkspace.getVacancy();
					// Si es lugar con atencion al publico, se agrega a la lista de actividades
					buildingManager.addPlace(sectoralIndex, tempWorkspace, placeProp);
				}
				// Agregar al contexto
				add(tempWorkspace);
				geography.move(tempWorkspace, geom);
			}
			else {
				System.err.println("Error creating agent for " + geom);
			}
		}
		features.clear();
		
		if (DEBUG_MSG) {
			System.out.println("CUPO ESTUDIANTES TER/UNI: " + universityVacancies);
			System.out.println("CUPO TRABAJADORES: " + workVacancies);
		}
	}
	
	private void createPublicTransportUnits() {
		if (town.publicTransportUnits == 0)
			return;
		// Variables temporales
		GeometryFactory geometryFactory = new GeometryFactory();
		String type = "bus";
		Coordinate secCoord;
		Coordinate[] secCoords = buildingManager.getSectoralsCentre();
		int sectoralType;
		int[] sectoralsPTUnits = town.getPTSectoralUnits(town.publicTransportUnits);
		// Separar types y tomar el primero
		PlaceProperty placeProp = placesProperty.get(type);
		if (placeProp == null) {
			System.out.println("Type de Place desconocido: " + type);
		}
		// Buscar la seccional mas cercana para asignar a este Place
		int ptUnits = 0;
		for (int sI = 0; sI < sectoralsPTUnits.length; sI++) {
			sectoralType = town.sectoralsTypes[sI];
			secCoord = secCoords[sI];
	    	for (int j = 0; j < sectoralsPTUnits[sI]; j++) {
				// Crear Agente con los atributos el Place
				PublicTransportAgent tempPublicTransport = new PublicTransportAgent(this, sectoralType, sI, secCoord, ++lastHomeId, type, placeProp);
				workPlaces.add(tempPublicTransport);
				// Si es lugar con atencion al publico, se agrega a la lista de actividades
				buildingManager.addPlace(sI, tempPublicTransport, placeProp);
				// Agregar al contexto
				add(tempPublicTransport);
				geography.move(tempPublicTransport, geometryFactory.createPoint(secCoord));
			}
	    	ptUnits += sectoralsPTUnits[sI];
		}
		buildingManager.setDefaultPTUnits(ptUnits, sectoralsPTUnits);
	}
	
	/**
	 * Lee el archivo GIS y retorna sus items en una lista.
	 * @param filename  ruta del archivo shape
	 * @return lista de <b>SimpleFeatures</b>
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
	 * @param secIndex indice seccional
	 * @param extraHomes cantidad de hogares faltantes
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
				tempBuilding.setId(++lastHomeId);
				homePlaces.get(secIndex).add(tempBuilding);
				add(tempBuilding);
				geography.move(tempBuilding, geometryFactory.createPoint(tempBuilding.getCoordinate()));
			}
		}
	}
	
	/**
	 * Selecciona los ultimos hogares y los elimina.
	 * @param secIndex indice seccional
	 * @param extraHomes cantidad de hogares sobrantes
	 */
	private void deleteExtraHomes(int secIndex, int extraHomes) {
		HomeAgent tempHome;
		int indexesCount = homePlaces.get(secIndex).size()-1;
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				tempHome = homePlaces.get(secIndex).remove(indexesCount);
				remove(tempHome);
				--indexesCount;
			}
		}
	}
	
	/**
	 * Crea copias de instancias ClassroomAgent para cada escuela hasta cubrir el cupo escolar. 
	 */
	private void createSchoolClassrooms() {
		ClassroomAgent baseCA, tempCA;
		int schools = schoolPlaces.size(); // cantidad real de escuelas
		// Calcula cuantas divisiones por escuela
		int classPerSchool = ((schooledSum / DataSet.CLASS_SIZE) / schools) - 1; // -1 para restar aula original
		int classroomCount = schools; // contador
		for (int i = 1; i <= schools; i++) {
			baseCA = (ClassroomAgent) schoolPlaces.get(i); // aula original
			if (i == schools) { // ultima escuela
				// Calcula la cantidad que falta
				classPerSchool = (schooledSum / DataSet.CLASS_SIZE) - classroomCount;
			}
			for (int j = 0; j < classPerSchool; j++) {
				// Crea copia de instancia
				tempCA = new ClassroomAgent(baseCA);
				tempCA.setId(++lastHomeId);
				//
				schoolPlaces.add(tempCA);
				buildingManager.addWorkplace(baseCA.getType(), tempCA);
				//add(tempCA); // no hace falta por ahora agregarlo al contexto
			}
			classroomCount += classPerSchool;
		}
	}
	
	/**
	 * Crea Humano con los parametros dados y lo agrega al contexto.
	 * @param secType indice tipo seccional
	 * @param secIndex indice seccional
	 * @param ageGroup indice grupo etario
	 * @param home parcela hogar
	 * @param work parcela trabajo o null
	 * @return nuevo agente <b>HumanAgent</b>
	 */
	private HumanAgent createHuman(int secType, int secIndex, int ageGroup, BuildingAgent home, BuildingAgent work) {
		int[] workPos = null;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
		}
		HumanAgent tempHuman = new HumanAgent(this, secType, secIndex, ageGroup, home, work, workPos);
		add(tempHuman);
		return tempHuman;
	}
	
	/**
	 * Crea Humanos extranjeros, sin hogar pero con lugar de trabajo.
	 * @param ageGroup indice grupo etario
	 * @param work parcela trabajo
	 * @return nuevo agente <b>HumanAgent</b>
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
		HumanAgent tempHuman = new ForeignHumanAgent(this, secType, secIndex, ageGroup, work, workPos);
		add(tempHuman);
		return tempHuman;
	}

	/**
	 * Crea Humanos dependiendo la franja etaria, lugar de trabajo y vivienda.
	 */
	private void initHumans() {
		int i, j, k;
		int[][] locals = new int[town.sectoralsCount][DataSet.AGE_GROUPS];
		int[][] localTravelers = new int[town.sectoralsCount][DataSet.AGE_GROUPS];
		int[] foreignTravelers = new int[DataSet.AGE_GROUPS];
		
		HumanAgent tempHuman = null;
		HomeAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		noncollegiateCount = 0;
		int secType;
		int[] humIdx = loadHumansAmount(locals, localTravelers, foreignTravelers);
		
		Uniform disUniHomesIndex;
		// Primero se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < town.sectoralsCount; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			secType = town.sectoralsTypes[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < localTravelers[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					//
					tempHuman = createHuman(secType, i, j, tempHome, null);
					tempHome.addOccupant(tempHuman);
					contextHumans[humIdx[j]++] = tempHuman;
					// Se resta primero la capacidad de estudiantes primario, universitarios y por ultimo trabajadores
					if (schooledCount[secType][j] > 0)
						--schooledCount[secType][j];
					else if (collegiateCount[secType][j] > 0)
						--collegiateCount[secType][j];
					else if (employedCount[secType][j] > 0)
						--employedCount[secType][j];
				}
			}
		}
		
		// Segundo se crean los 100% locales
		for (i = 0; i < town.sectoralsCount; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			secType = town.sectoralsTypes[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < locals[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					tempJob = findAGWorkingPlace(secType, j, tempHome);
					//
					tempHuman = createHuman(secType, i, j, tempHome, tempJob);
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
	 * Calcula la cantidad de humanos locales, locales viajeros y extranjeros; por franja etaria y seccional.
	 * @param locals array locales
	 * @param localTravelers array locales que trabajan afuera
	 * @param foreignTravelers array extranjeros
	 * @return <b>int[]</b> con indices de cada grupo etario
	 */
	private int[] loadHumansAmount(int[][] locals, int[][] localTravelers, int[] foreignTravelers) {
		int localCount;
		int localTravelersCount;
		int foreignCount = town.foreignTravelerHumans;
		int sectoralType;
		int i, j;
		
		// Inicia cantidad de humanos e indices
		localHumansCount = 0;
		foreignHumansCount = 0;
		localHumansIndex = new int[DataSet.AGE_GROUPS];
		
		// Calcula la cantidad de humanos en el contexto de acuerdo a la poblacion del municipio y la distribucion por seccional
		for (i = 0; i < town.sectoralsCount; i++) {
			sectoralType = town.sectoralsTypes[i];
			localCount = (int) ((town.localHumans + town.localTravelerHumans) * town.sectoralsPopulation[i]) / 100;
			localTravelersCount = (int) (town.localTravelerHumans * town.sectoralsPopulation[i]) / 100;
			// Si hay hogares extras, los elimina; Si no alcanzan, se crean 
			int extraHomes = (int) (localCount / getHouseInhabitantsMean(sectoralType)) - homePlaces.get(i).size();
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
			// Guarda la cantidad de humanos que viven en contexto
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				locals[i][j] = (int) Math.round((localCount * DataSet.HUMANS_PER_AGE_GROUP[j]) / 100);
				localHumansCount += locals[i][j];
				localHumansIndex[j] += locals[i][j];
				localTravelers[i][j] = (int) (localTravelersCount * getLocalHumansPerAG(sectoralType)[j]) / 100;
				locals[i][j] -= localTravelers[i][j];
			}
		}
		//
		
		// Calcular la cantidad de humanos que vive fuera y trabaja/estudia en contexto
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			foreignTravelers[i] = (int) (foreignCount * getForeignHumansPerAG()[i]) / 100;
			foreignHumansCount += foreignTravelers[i];
		}
		//
		
		// Inicia el array de humanos en contexto (para accederlos mas facil y rapido, que usando los metodos de Repast)
		contextHumans = new HumanAgent[localHumansCount + foreignHumansCount];
		// Calcula el indice en contextHumans donde comienza cada grupo etario
		int[] humansIdx = new int[DataSet.AGE_GROUPS]; // Indice desde cual ubicar cada grupo etario
		for (i = 1; i < DataSet.AGE_GROUPS; i++) {
			localHumansIndex[i] += localHumansIndex[i - 1];
			humansIdx[i] = localHumansIndex[i - 1];
		}
		//
		return humansIdx;
	}
	
	/**
	 * Calcula la cantidad de estudiantes y trabajadores locales.
	 */
	private void loadOccupationalNumbers() {
		employedCount = new int[2][DataSet.AGE_GROUPS];
		schooledCount = new int[2][DataSet.AGE_GROUPS];
		collegiateCount = new int[2][DataSet.AGE_GROUPS];
		schooledSum = collegiateSum = employedSum = 0;
		//
		int i, j;
		double[] percPP = {0d, 0d};
		for (i = 0; i < town.sectoralsCount; i++) {
			percPP[town.sectoralsTypes[i]] += town.sectoralsPopulation[i];
		}
		//
		int humansCount;
		double[][] occupationPerAG;
		for (i = 0; i < 2; i++) {
			occupationPerAG = getOccupationPerAG(i);
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				humansCount = (int) ((town.localHumans + town.localTravelerHumans) * percPP[i] * DataSet.HUMANS_PER_AGE_GROUP[j] / 10000);
				switch (j) {
				case 0: // todos primarios/secundarios
					schooledCount[i][j] = (int) (humansCount * occupationPerAG[j][0]) / 100;
					break;
				case 1: // 45% primarios/secundarios, resto universitarios
					schooledCount[i][j] = (int) (humansCount * (occupationPerAG[j][0] * 0.45)) / 100;
					collegiateCount[i][j] = (int) ((humansCount * occupationPerAG[j][0]) / 100) - schooledCount[i][j];
					break;
				default:// todos universitarios
					collegiateCount[i][j] = (int) (humansCount * occupationPerAG[j][0]) / 100;
					break;
				}
				employedCount[i][j] = (int) (humansCount * occupationPerAG[j][1]) / 100;
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
	 * Busca lugar de trabajo/estudio si hay cupo para el tipo de seccional y franja etaria del humano.
	 * @param secType indice tipo seccional
	 * @param ageGroup indice grupo etario
	 * @param home hogar de humano
	 * @return <b>BuildingAgent</b> o <b>null</b>
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
        	if (wp <= getWorkingFromHome(secType) + getWorkingOutdoors(secType)) {
        		// Si no, puede trabajar en la casa o al exterior
        		wp = RandomHelper.nextIntFromTo(1, getWorkingFromHome(secType) + getWorkingOutdoors(secType));
        		if (wp <= getWorkingFromHome(secType))
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
	 * Busca lugar de trabajo/estudio para extranjeros segun tipo de seccional, franja etaria y ocupacion.
	 * @param secType indice tipo seccional
	 * @param ageGroup indice grupo etario
	 * @return <b>BuildingAgent</b> o <b>null</b>
	 */
	private BuildingAgent findAGWorkingPlace(int secType, int ageGroup) {
		BuildingAgent workplace = null; // inactivo por defecto
		//
		double occupation[] = getOccupationPerAG(secType)[ageGroup];
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
	 * @param wpList lista de WorkplaceAgents
	 * @return <b>WorkplaceAgent</b> o <b>null</b>
	 */
	private WorkplaceAgent findWorkingPlace(List<WorkplaceAgent> wpList) {
		int index;
		WorkplaceAgent workplace = null;
		if (!wpList.isEmpty()) {
			index = RandomHelper.nextIntFromTo(0, wpList.size()-1);
			workplace = wpList.get(index);
			workplace.reduceVacancies();
			if (!workplace.vacancyAvailable())
				wpList.remove(index);
		}
		return workplace;
	}
	
	/**
	 * @return <b>true</b> si ya hubo contagio local
	 */
	public boolean localOutbreakStarted() {
		return outbreakStarted;
	}
	
	/**
	 * @param minusRate porcentaje de reduccion de infeccion (0...100)
	 * @param enableMaskOutdoor utilizar cubreboca en espacios abiertos
	 * @param enableMaskWorkspace utilizar cubreboca en oficinas y aulas
	 * @param enableMaskCS utilizar cubreboca entre empleados de atencion al publico
	 * @param enableMaskCustomer utilizar cubreboca entre clientes/empleados de otros/ocio
	 */
	public void setMaskValues(int minusRate, boolean enableMaskOutdoor, boolean enableMaskWorkspace, boolean enableMaskCS, boolean enableMaskCustomer) {
		maskInfRateReduction = minusRate;
		wearMaskOutdoor = enableMaskOutdoor;
		wearMaskWorkspace = enableMaskWorkspace;
		wearMaskCustomerService = enableMaskCS;
		wearMaskCustomer = enableMaskCustomer;
	}
	
	/**
	 * @param percentage porcentaje de Huamanos que respeta la distancia (0...100)
	 * @param enableOutdoor respetar la distancia en espacios abiertos
	 * @param enableWorkplace respetar la distancia entre trabajadores/estudiantes
	 */
	public void setSDValues(int percentage, boolean enableOutdoor, boolean enableWorkplace) {
		socialDistPercentage = percentage;
		socialDistOutdoor = enableOutdoor;
		socialDistWorkspace = enableWorkplace;
	}
	
	/** @return <b>true</b> si estan habilitados los contactos estrechos y la cuarentena preventiva de los mismos */
	public boolean closeContactsEnabled(){ return closeContactsEnabled; }
	/** Habilita contactos estrechos y su cuarentena preventiva. */
	public void enableCloseContacts()	{ closeContactsEnabled = true; }
	/** Deshabilita contactos estrechos y su cuarentena preventiva. */
	public void disableCloseContacts()	{ closeContactsEnabled = false; }
	
	/** @return <b>true</b> si los habitantes del hogar de un sintomatico se ponen en cuarentena */
	public boolean prevQuarantineEnabled(){ return prevQuarantineEnabled; }
	/** Habilita cuarentena preventiva en hogares de sintomaticos. */
	public void enablePrevQuarantine()	{ prevQuarantineEnabled = true; }
	/** Deshabilita cuarentena preventiva en hogares de sintomaticos. */
	public void disablePrevQuarantine()	{ prevQuarantineEnabled = false; }
	
	/** @param percentage porcentaje de Humanos que respeta la distancia (0...100) */
	public void setSDPercentage(int percentage) { socialDistPercentage = percentage; }
	/** @return <b>0...100</b> porcentaje de Humanos que respeta la distancia */
	public int getSDPercentage()		{ return socialDistPercentage; }
	/** @return <b>true</b> si se respeta la distancia en espacios abiertos */
	public boolean sDOutdoor()			{ return socialDistOutdoor; }
	/** @return <b>true</b> si trabajadores/estudiantes respetan la distancia en su trabajo/estudio */
	public boolean sDWorkspace()		{ return socialDistWorkspace; }
	
	/** @param minusFrac fraccion de reduccion de infeccion (0...1) */
	public void setMaskEffectivity(double minusFrac) { maskInfRateReduction = minusFrac; }
	/** @return <b>0...1</b> fraccion de reduccion de infeccion */
	public double getMaskEffectivity()	{ return maskInfRateReduction; }
	
	/** @return <b>true</b> si se utiliza tapaboca en espacios abiertos */
	public boolean wearMaskOutdoor()	{ return wearMaskOutdoor; }
	/** @param wear utilizan tapaboca en oficinas y aulas */
	public void setWearMaskWorkspace(boolean wear) { wearMaskWorkspace = wear; }
	/** @return <b>true</b> si entre trabajadores/estudiantes en oficinas/aulas utilizan tapaboca */
	public boolean wearMaskWorkspace()	{ return wearMaskWorkspace; }
	/** @param wear utilizan tapaboca entre trabajadores en atencion al publico */
	public void setWearMaskCS(boolean wear) { wearMaskCustomerService = wear; }
	/** @return <b>true</b> si entre trabajadores en atencion al publico usan tapaboca */
	public boolean wearMaskCS()	{ return wearMaskCustomerService; }
	/** @return <b>true</b> si entre clientes y trabajadores en lugares de otros/ocio usan tapaboca */
	public boolean wearMaskCustomer()	{ return wearMaskCustomer; }
	
	/** @return porcentaje sobre 100 de mortalidad en pacientes internados en UTI */
	public double getICUDeathRate()		{ return DataSet.DEFAULT_ICU_DEATH_RATE; }
	/** @return modificador para calcular chance de contagio fuera del contexto (al aumentar, aumenta chance) */
	public int getOOCContagionValue()	{ return DataSet.DEFAULT_OOC_CONTAGION_VALUE; }
}
