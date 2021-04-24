package geocovid.agents;

import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.MarkovChains;
import geocovid.Temperature;
import geocovid.contexts.SubContext;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;

/**
 * Clase base de Agente Humano.
 */
public class HumanAgent {
	/** Puntero al contexto actual */
	protected SubContext context;
	/** Parcela actual o null si en exterior */
	private BuildingAgent currentBuilding = null;
	/** Parcela hogar o null si extranjero */
	private BuildingAgent homePlace;
	/** Parcela trabajo o null si en exterior o desempleado */
	private BuildingAgent workPlace;
	/** Ubicacion actual dentro de parcela o null si afuera*/ 
	private int[] currentPosition = {0,0};
	/** Ubicacion en parcela trabajo o null si en exterior o desempleado */
	private int[] workplacePosition = {0,0};
	
	/** Indice de tipo de seccional origen */
	private int sectoralType;
	/** Indice de seccional origen */
	private int sectoralIndex;
	/** Indice estado de markov donde esta (0 es la casa, 1 es el trabajo/estudio, 2 es ocio, 3 es otros) */
	private int currentActivity = 0;
	/** Indice de franja etaria */
	private int ageGroup = 0;
	/** Humano extranjero */
	private boolean foreignTraveler = false;
	/** Humano turista */
	private boolean touristTraveler = false;
	/** Ultimo tick que cambio actividad */
	private double relocationTime = -1d;
	/** Contador para demorar cambio de actividad */
	private int ticksDelay = 0;
	/** Tick de inicio de periodo infeccioso */
	private double infectiousStartTime;
	
	/** Tiene una actividad programada */
	private boolean activityQueued = false;
	/** Duracion en ticks de actividad programada */
	private int queuedDuration;
	/** Parcela de actividad programada */
	private BuildingAgent queuedBuilding;
	/** Indice estado de markov de actividad programada */
	private int queuedActIndex;
	
	/** Puntero a ISchedule para programar acciones */
	protected static ISchedule schedule;
	/** Cntador Id de agente */
	private static int agentIDCounter = 0;
	/** Id de agente */
	private int agentID = ++agentIDCounter;
	
    /** Mapa de contactos diarios <Id contacto, cantidad de contactos> */
    private Map<Integer, Integer> socialInteractions = new HashMap<>();
	
    // ESTADOS //
    public boolean exposed;			// Contagiado
    public boolean asxInfectious;	// Infeccioso asintomatico
    public boolean symInfectious;	// Infeccioso sintomatico
    public boolean hospitalized;	// Hospitalizado en UTI
    public boolean recovered;		// Recuperado
    // Extras
    public boolean quarantined;		// Aislado por sintomatico o cuarentena preventiva
    public boolean distanced;		// Respeta distanciamiento social
    public boolean aerosoled;		// Fue afectado por aerosol en parcela actual (o aerosolized?)
    //
    private boolean inCloseContact;	// Si estuvo en contacto estrecho con sintomatico
    private boolean preInfectious;	// Si "contagia" a contactos estrechos
    /////////////
    
	public HumanAgent(SubContext subContext, int secHome, int secHomeIndex, int ageGroup, BuildingAgent home, BuildingAgent work, int[] workPos) {
		this.context = subContext;
		this.sectoralType = secHome;
		this.sectoralIndex = secHomeIndex;
		this.homePlace = home;
		this.workPlace = work;
		this.workplacePosition = workPos;
		this.ageGroup = ageGroup;
	}
    
	public HumanAgent(SubContext subContext, int secHome, int secHomeIndex, int ageGroup, BuildingAgent home, BuildingAgent work, int[] workPos, boolean foreign, boolean tourist) {
		this(subContext, secHome, secHomeIndex, ageGroup, home, work, workPos);
		this.foreignTraveler = foreign;
		this.touristTraveler = tourist;
	}
	
	public static void initAgentID() {
		agentIDCounter = 0;
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
	}
	
	/** @return {@link HumanAgent#agentID} */
	public int getAgentID() {
		return agentID;
	}
	
	/** @return {@link HumanAgent#ageGroup} */
	public int getAgeGroup() {
		return ageGroup;
	}
	
	/** @return {@link HumanAgent#foreignTraveler} */
	public boolean isForeign() { 
		return foreignTraveler;
	}
	
	/** @return {@link HumanAgent#workPlace} */
	public BuildingAgent getPlaceOfWork() {
		return workPlace;
	}
	
	/** @return {@link HumanAgent#exposed} */
	public boolean wasExposed() {
		return exposed;
	}
	
	/** @return <b>true</b> si en estado infeccioso */
	public boolean isContagious() {
		return (asxInfectious || symInfectious);
	}
	
	/** @return <b>true</b> si en actividad trabajo */
	public boolean atWork() {
		return (currentActivity == 1);
	}
	
	/** @return <b>true</b> si en cuarentena */
	public boolean isIsolated() {
		return quarantined;
	}
	
	/** @return {@link HumanAgent#currentPosition} */
	public int[] getCurrentPosition() {
		return currentPosition;
	}
	
	/** @return {@link HumanAgent#currentBuilding} */ 
	public BuildingAgent getCurrentBuilding() {
		return currentBuilding;
	}
	
	/** @return {@link HumanAgent#currentActivity} */
	public int getCurrentActivity() {
		return currentActivity;
	}
	
	/** @return {@link HumanAgent#relocationTime} */
	public double getRelocationTime() {
		return relocationTime;
	}
	
	/** @return <b>true</b> si crea contactos estrechos */
	public boolean isPreInfectious() {
		return preInfectious;
	}
	
	/** @return {@link HumanAgent#infectiousStartTime} */
	public double getInfectiousStartTime() {
		return infectiousStartTime;
	}
	
	/** @return {@link HumanAgent#activityQueued} */
	public boolean isActivityQueued() {
		return activityQueued;
	}
	
	/**
	 * Inicia o finaliza el periodo en que puede generar contactos estrechos en el trabajo.
	 * @see DataSet#CLOSE_CONTACT_INFECTIOUS_TIME
	 * @param value iniciar o finalizar
	 */
	public void setPreInfectious(boolean value) {
		// Si verdaderamente cambia de estado
		if (value != preInfectious) {
			// Si esta en el ambito (trabajo/estudio) donde puede tener contactos estrechos
			if (atWork() && currentBuilding instanceof WorkplaceAgent) {
				// Se agrega o remueve de la lista de "pre contagiosos"
				if (value)
					currentBuilding.addPreSpreader(this);
				else
					currentBuilding.removePreSpreader(this, currentPosition);
			}
			preInfectious = value;
		}
	}
	
	/**
	 * @param sociallyDistanced respeta distanciamiento social.
	 */
	public void setSociallyDistanced(boolean sociallyDistanced) {
		distanced = sociallyDistanced;
	}
	
	/**
	 * Aumenta cantidad de contactos con el humano de la Id dada.
	 * @param humanId id HumanAgent
	 */
	public void addSocialInteraction(int humanId) {
		// Aca uso la ID del humano como key, por si se quiere saber cuantos contactos se repiten
		if (socialInteractions.containsKey(humanId))
			socialInteractions.put(humanId, socialInteractions.get(humanId) + 1);
		else
			socialInteractions.put(humanId, 1);
	}
	
	/**
	 * Informa la cantidad de contactos en el dia (si corresponde) y reinicia el Map.
	 * @see DataSet#COUNT_UNIQUE_INTERACTIONS
	 */
	@ScheduledMethod(start = 23, interval = 24, priority = ScheduleParameters.LAST_PRIORITY)
	public void newDayBegin() {
		// Se tiene en cuenta unicamente los que viven en la ciudad
		if (!foreignTraveler) {
			int count = 0;
			if (DataSet.COUNT_UNIQUE_INTERACTIONS) {
				count = socialInteractions.size();
			}
			else {
				for (Object value : socialInteractions.values())
					count += (Integer)value;
			}
			InfectionReport.updateSocialInteractions(ageGroup, count);
		}
		socialInteractions.clear();
	}
	
	/**
	 * Remueve el humano de la parcela y contexto actual.
	 */
	public void removeFromContext() {
		if (currentBuilding != null) {
			currentBuilding.removeHuman(this, currentPosition);
			currentBuilding = null;
		}
		context.remove(this);
	}
	
	/**
	 * Re-ingresa al contexto al salir de UTI.
	 */
	public void addRecoveredToContext() {
		// Si esta hospitalizado o vive afuera no vuelve a entrar
		if (hospitalized)
			return;
		context.add(this);
		
		currentActivity = 0;
		currentPosition = homePlace.insertHuman(this);
		currentBuilding = homePlace;
		switchLocation();
	}
	
	/**
	 * Setea que estuvo en contacto estrecho con un pre-sintomatico.
	 * @param startTime tick comienzo de cuarentena
	 */
	public void setCloseContact(double startTime) {
		if (!inCloseContact) {
			inCloseContact = true;
			// Programa el inicio de cuarentena preventiva
			ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(startTime, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(scheduleParams, this, "startSelfQuarantine");
		}
	}
	
	/**
	 * Setea inicio de contagio, define el tiempo de incubacion y si va ser asinto o sintomatico.<p>
	 * Si contactos estrechos esta habilitado, es sintomatico y tiene lugar de trabajo; programa inicio de pre-contagio.
	 * @see DataSet#EXPOSED_PERIOD_MEAN
	 * @see DataSet#EXPOSED_PERIOD_DEVIATION
	 * @see DataSet#ASX_INFECTIOUS_RATE
	 * @see DataSet#CLOSE_CONTACT_INFECTIOUS_TIME
	 */
	public void setExposed() {
		// Una vez expuesto, no puede volver a contagiarse
		if (exposed)
			return;
		// Se contagia del virus
		exposed = true;
		InfectionReport.addExposed(ageGroup);
		int mean = DataSet.EXPOSED_PERIOD_MEAN;
		int std = DataSet.EXPOSED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		
		// Define al comienzo de infeccion si va a ser asintomatico o sintomatico
		boolean asymp = (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ASX_INFECTIOUS_RATE[ageGroup]) ? true : false;
		
		// Programa el inicio del periodo de contagio
		infectiousStartTime = schedule.getTickCount() + period;
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(infectiousStartTime, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setInfectious", asymp, false);
		
		// Si es sintomatico y tiene lugar de trabajo, programa el inicio del periodo de "pre contagio" a contactos estrechos
		if (!asymp && context.closeContactsEnabled()) {
			if (workPlace instanceof WorkplaceAgent) {
				scheduleParams = ScheduleParameters.createOneTime(infectiousStartTime - DataSet.CLOSE_CONTACT_INFECTIOUS_TIME, ScheduleParameters.FIRST_PRIORITY);
				schedule.schedule(scheduleParams, this, "setPreInfectious", true);
			}
		}
	}
	
	/**
	 * Inicia el periodo infeccioso y define la duracion.<p>
	 * Si es sintomatico tiene chances de ser internado en UTI<p>
	 * Si es sintomatico y cuarentena preventiva esta habilitada, aisla integrantes del hogar.
	 * @see DataSet#ICU_CHANCE_PER_AGE_GROUP
	 * @param asymptomatic <b>true</b> si es asintomatico
	 * @param initial <b>true</b> si es de los primeros infectados
	 */
	public void setInfectious(boolean asymptomatic, boolean initial) {
		// Si es un primer caso, es siempre asintomatico
		if (initial) {
			exposed = true;
			InfectionReport.addExposed(ageGroup);
		}
		// Verificar que sea expuesto
		if (!exposed)
			return;
		
		// Comienza la etapa de contagio asintomatico o sintomatico
		if (asymptomatic) {
			asxInfectious = true;
			InfectionReport.modifyASXInfectiousCount(ageGroup, 1);
		}
		else {
			// Si es local y cuarentena preventiva esta habilitada
			if (!foreignTraveler && context.prevQuarantineEnabled()) {
				// Todos los habitantes del hogar se ponen en cuarentena (exepto los ya expuestos)
				((HomeAgent) homePlace).startPreventiveQuarentine(schedule.getTickCount());
			}
			
			// Si se complica el caso, se interna - si no continua vida normal
			if (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ICU_CHANCE_PER_AGE_GROUP[ageGroup]) {
				// Mover a ICU hasta que se cure o muera
				hospitalized = true;
				InfectionReport.modifyHospitalizedCount(ageGroup, 1);
				removeFromContext();
			}
			else {
				// Se aisla por tiempo prolongado si no va a ICU
				startQuarantine();
			}
			symInfectious = true;
			InfectionReport.modifySYMInfectiousCount(ageGroup, 1);
		}
		//
		
		if (!hospitalized && currentBuilding != null && currentPosition != null) {
			// Si no fue a ICU y tiene position dentro de un Building, se crear el marcador de infeccioso
			currentBuilding.addSpreader(this);
			context.getBuildManager().createInfectiousHuman(agentID, currentBuilding.getCoordinate());
		}
		
		int mean = DataSet.INFECTED_PERIOD_MEAN_AG;
		int std = DataSet.INFECTED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setRecovered");
	}
	
	/**
	 * Recuperacion de infeccion.<p>
	 * En caso de estar internado en UTI demora un tiempo hasta que vuelve al contexto.
	 * @see DataSet#EXTENDED_ICU_PERIOD
	 */
	public void setRecovered() {
		// Verificar que este infectado
		if (asxInfectious)
			InfectionReport.modifyASXInfectiousCount(ageGroup, -1);
		else if (symInfectious) {
			InfectionReport.modifySYMInfectiousCount(ageGroup, -1);
			setPreInfectious(false); // termina el periodo de contacto estrecho
		}
		else
			return;
		
		// Se recupera de la infeccion
		asxInfectious = false;
		symInfectious = false;
		if (!hospitalized) {
			recovered = true;
			InfectionReport.addRecovered(ageGroup);
			
			if (currentBuilding != null && currentPosition != null)
				currentBuilding.removeSpreader(this, currentPosition);
			
			// Se borra el marcador de infectado
			context.getBuildManager().deleteInfectiousHuman(agentID);
		}
		else {
			// 5 dias mas en ICU
			ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + DataSet.EXTENDED_ICU_PERIOD, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(scheduleParams, this, "dischargeFromICU");
		}
	}
	
	/**
	 * Da el alta de internacion en UTI o tiene un chance que fallezca. 
	 * @see DataSet#ICU_DEATH_RATE
	 */
	public void dischargeFromICU() {
		hospitalized = false;
		InfectionReport.modifyHospitalizedCount(ageGroup, -1);
		if (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ICU_DEATH_RATE) {
			// Se muere en ICU
			InfectionReport.addDead(ageGroup);
		}
		else {
			// Sale de ICU - continua vida normal
			recovered = true;
			InfectionReport.addRecovered(ageGroup);
			addRecoveredToContext();
		}
	}
	
	/**
	 * Si es sintomatico, inicia el periodo de quarentena en el hogar.
	 * @see DataSet#QUARANTINED_PERIOD_MEAN_AG
	 * @see DataSet#QUARANTINED_PERIOD_DEVIATION
	 */
	public void startQuarantine() {
		quarantined = true;
		// Calcula el periodo de quarentena
		int mean = DataSet.QUARANTINED_PERIOD_MEAN_AG;
		int std = DataSet.QUARANTINED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		// Programa el fin de cuarentena por sintomatico
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "stopQuarantine");
	}
	
	/**
	 * Si estuvo en contacto estrecho o algun familiar es sintomatico, programa el inicio de cuarentena preventiva en el hogar.
	 * @see DataSet#PREVENTIVE_QUARANTINE_TIME
	 */
	public void startSelfQuarantine() {
		inCloseContact = false;
		quarantined = true;
		// Programa el fin de cuarentena preventiva
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + DataSet.PREVENTIVE_QUARANTINE_TIME, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "stopQuarantine");
	}
	
	/**
	 * Finaliza el periodo de cuarentena por sintomatico o preventiva.
	 */
	public void stopQuarantine() {
		quarantined = false;
	}
	
	/**
	 * Setea los parametros de actividad programada.
	 * @param actIndex indice de actividad (0,1,2,3)
	 * @param building parcela donde realiza actividad
	 * @param ticksDuration duracion en ticks de actividad
	 */
	public void queueActivity(int actIndex, BuildingAgent building, int ticksDuration) {
		queuedActIndex = actIndex;
		queuedBuilding = building;
		queuedDuration = ticksDuration;
		activityQueued = true;
	}
	
	/**
	 * Selecciona la parcela donde realizar la nueva actividad.
	 * @param prevActivityIndex indice de actividad previa
	 * @param activityIndex indice de nueva actividad
	 * @return <b>BuildingAgent</b> o <b>null</b>
	 */
	public BuildingAgent switchActivity(int prevActivityIndex, int activityIndex) {
		BuildingAgent newBuilding;
        switch (activityIndex) {
	    	case 0: // 0 Casa
	    		newBuilding = homePlace;
	    		break;
	    	case 1: // 1 Trabajo / Estudio
	    		newBuilding = workPlace;
	    		break;
	    	default: // 2 Ocio / 3 Otros (supermercados, farmacias, etc)
	    		newBuilding = context.getBuildManager().findRandomPlace(sectoralType, sectoralIndex, activityIndex, this, currentBuilding, ageGroup);
	    		break;
        }
        return newBuilding;
	}
	
    /**
     * Cambia la actividad y parcela segun TMMC (Timed mobility markov chains).
     */
    public void switchLocation() {
		// Resta ticks de duracion de actividad programada
    	if (ticksDelay > 0) {
    		--ticksDelay;
    		return;
    	}
    	// Si debe, inicia actividad programada
    	if (activityQueued) {
    		activityQueued = false;
    		ticksDelay = queuedDuration; // setea contador ticksDelay
    		relocate(queuedBuilding, queuedActIndex);
    		return;
    	}
    	
    	// Calcula el indice del periodo del dia, segun tick actual
        final int p = ((int)schedule.getTickCount() % 24) / 6;	// 0 1 2 3
        // Probabilidad aleatoria de 1 a 1000 para elegir actividad en matrices
        int r = RandomHelper.nextIntFromTo(1, 1000);
        int i = 0;
        
        int[][][] matrixTMMC = null;
        if (!foreignTraveler)
        	matrixTMMC = (!quarantined ? context.getLocalTMMC(sectoralType, ageGroup) : context.getIsolatedLocalTMMC(ageGroup));
        else if (touristTraveler)
        	matrixTMMC = MarkovChains.TOURIST_DEFAULT_TMMC;
        //else
        //	matrixTMMC = (!quarantined ? travelerTMMC : infectedTravelerTMMC);
        
        // Recorre la matriz correspondiente al periodo del dia y actividad actual
        while (r > matrixTMMC[p][currentActivity][i]) {
        	// Avanza el indice de actividad, si es posible restar probabilidad
        	r -= matrixTMMC[p][currentActivity][i];
        	++i;
        }
        
        // Si el nuevo lugar es de distinto tipo, lo cambia
        if (currentActivity != i) {
        	BuildingAgent tempBuilding = switchActivity(currentActivity, i);
        	relocate(tempBuilding, i);
        }
        
        // Incrementa el tiempo de la actividad realizada
        InfectionReport.increaseActivityTime(i);
    }
    
	/**
	 * Cambia la ubicacion del agente e indice de actividad.
	 * @param newBuilding nueva parcela o null
	 * @param newActivity indice nueva actividad
	 */
	private void relocate(BuildingAgent newBuilding, int newActivity) {
    	// Lo remueve si esta dentro de una parcela
        if (currentBuilding != null) {
        	currentBuilding.removeHuman(this, currentPosition);
        	currentBuilding = null;
        }
        // Si estuvo afuera, tiene una chance de volver infectado
        else if (!exposed && context.localOutbreakStarted()) {
        	int infectChance = (int) (schedule.getTickCount() - relocationTime);
        	if (RandomHelper.nextIntFromTo(1, Temperature.getOOCContagionChance(context.getTownRegion())) <= infectChance) {
    			setExposed();
    		}
        }
        currentActivity = newActivity;
        
    	// Si el nuevo lugar es una parcela
    	if (newBuilding != null) {
    		// Si tiene un lugar de trabajo especifico
    		if (atWork() && (workplacePosition != null)) {
    			currentPosition = newBuilding.insertHuman(this, workplacePosition);
    			if (currentPosition == null) { // Si queda fuera del trabajo
    				newBuilding = homePlace; // Se queda en la casa...
    				if (newBuilding != null) // si tiene
    					currentPosition = newBuilding.insertHuman(this);
    			}
    		}
    		else
    			currentPosition = newBuilding.insertHuman(this);
    		currentBuilding = newBuilding;

    		if (isContagious() && currentPosition != null) {
				// Si en periodo de contagio y dentro del edificio, mover el marcador
    			context.getBuildManager().moveInfectiousHuman(agentID, newBuilding.getCoordinate());
			}
    	}
    	else if (isContagious() && atWork()) {
    		// Si va afuera a trabajar y es contagioso, oculto el marcador
    		context.getBuildManager().hideInfectiousHuman(agentID);
    	}
    	relocationTime = schedule.getTickCount();
    }
}
