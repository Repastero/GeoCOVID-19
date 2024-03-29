package geocovid.agents;

import java.util.HashMap;
import java.util.Map;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.MarkovChains;
import geocovid.Temperature;
import geocovid.Utils;
import geocovid.contexts.SubContext;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedulableAction;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
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
	private int currentState = 0;
	/** Indice de franja etaria */
	private int ageGroup = 0;
	/** Chance de padecer caso grave */
	private double severeCaseChance = 0d;
	/** Humano extranjero */
	private boolean foreignTraveler = false;
	/** Humano turista */
	private boolean touristTraveler = false;
	/** Ultimo tick que cambio de building */
	private double relocationTime = -1d;
	/** Contador para demorar cambio de estado */
	private int ticksDelay = 0;
	/** Tick de inicio de periodo infeccioso */
	private double infectiousStartTime;
	
    /** Nivel de inmunidad parcial por vacuna */
    private int immunityLevel = DataSet.IMMUNITY_LVL_NONE;
    /** Referencia al fin del periodo de inmunidad natural o por vacuna */
    private ISchedulableAction immunityAction;
	
	/** Tiene una actividad programada */
	private boolean activityQueued = false;
	/** Duracion en ticks de actividad programada */
	private int queuedDuration;
	/** Parcela de actividad programada */
	private BuildingAgent queuedBuilding;
	/** Indice estado de markov de actividad programada */
	private int queuedActivityState;
	
	/** Puntero a ISchedule para programar acciones */
	protected static ISchedule schedule;
	/** Contador Id de agente */
	private static int agentIDCounter = 0;
	/** Id de agente */
	private int agentID = ++agentIDCounter;
	
    /** Mapa de contactos diarios <Id contacto, cantidad de contactos> */
    private Map<Integer, Integer> socialInteractions = new HashMap<>();
	
    // ESTADOS //
    protected boolean exposed;		// Contagiado
    protected boolean asxInfectious;// Infeccioso asintomatico
    protected boolean symInfectious;// Infeccioso sintomatico
    protected boolean hospitalized;	// Hospitalizado en UTI
    protected boolean recovered;	// Recuperado
    protected boolean deceased;		// Fallecido
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
		setSCChance(ageGroup);
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
	
	/** @return {@link HumanAgent#recovered} */
	public boolean hasRecovered() {
		return recovered;
	}
	
	/** @return {@link HumanAgent#deceased} */
	public boolean isDead() {
		return deceased;
	}
	
	/** @return <b>true</b> si en estado infeccioso */
	public boolean isContagious() {
		return (asxInfectious || symInfectious);
	}
	
	/** @return <b>true</b> si es sintomatico */
	public boolean isSymptomatic() {
		return symInfectious;
	}
	
	/** @return <b>true</b> si en actividad trabajo */
	public boolean atWork() {
		return (currentState == 1);
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
	
	/** @return {@link HumanAgent#currentState} */
	public int getCurrentState() {
		return currentState;
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
	
	/** @return {@link HumanAgent#inCloseContact} */
	public boolean isInCloseContact() {
		return inCloseContact;
	}
	
	/**
	 * Setea la chance de padecer caso grave segun franja etaria y comorbilidades.
	 * @param ag indice franja etaria
	 */
	private void setSCChance(int ag) {
		double increasedRisk = 0d;
		int r;
		for (int i = 0; i < DataSet.DISEASE_SEVERE_CASE_CHANCE_MOD.length; i++) {
			r = RandomHelper.nextIntFromTo(1, 1000);
			if (r <= DataSet.DISEASE_CHANCE_PER_AGE_GROUP[i][ag])
				increasedRisk += DataSet.DISEASE_SEVERE_CASE_CHANCE_MOD[i];
		}
		this.severeCaseChance = DataSet.SEVERE_CASE_CHANCE_PER_AG[ag] + increasedRisk;
	}
	
	/**
	 * Inicia o finaliza el periodo en que puede generar contactos estrechos en el trabajo.
	 * @param value iniciar o finalizar
	 */
	public void setPreInfectious(boolean value) {
		// Si verdaderamente cambia de estado
		if (value != preInfectious) {
			// Si esta en el ambito (trabajo/estudio) donde puede tener contactos estrechos
			if (atWork() && currentBuilding instanceof WorkplaceAgent) {
				// Se agrega o remueve de la lista de "pre contagiosos"
				if (value)
					((WorkplaceAgent) currentBuilding).addPreSpreader(this);
				else
					((WorkplaceAgent) currentBuilding).removePreSpreader(this, currentPosition);
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
	 * Informa la cantidad de contactos en el dia y reinicia el Map.
	 * @see DataSet#COUNT_UNIQUE_INTERACTIONS
	 * @return contactos personales diarios
	 */
	public int getSocialInteractions() {
		int count = 0;
		if (DataSet.COUNT_UNIQUE_INTERACTIONS) {
			count = socialInteractions.size();
		}
		else {
			for (Object value : socialInteractions.values())
				count += (Integer)value;
		}
		socialInteractions.clear();
		return count;
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
		
		currentState = 0;
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
	 * Compara si un valor aleatorio entre 0 y 100 es menor igual a la chance de contagio.
	 * @param infectionRate beta contagio
	 * @see DataSet#VACCINE_INFECTION_CHANCE_MOD
	 * @return <b>true</b> si el agente se contagia
	 */
	public boolean checkContagion(double infectionRate) {
		// Chequea inmunidad parcial por vacuna
		if (immunityLevel != DataSet.IMMUNITY_LVL_NONE)
			infectionRate *= DataSet.VACCINE_INFECTION_CHANCE_MOD[immunityLevel];
		if (RandomHelper.nextDoubleFromTo(0d, 100d) <= infectionRate) {
			setExposed();
			return true;
		}
		return false;
	}
	
	/**
	 * Setea inicio de contagio, define el tiempo de incubacion y si va ser asinto o sintomatico.<p>
	 * Si contactos estrechos esta habilitado, es sintomatico y tiene lugar de trabajo; programa inicio de pre-contagio.
	 * @see DataSet#EXPOSED_PERIOD_MEAN
	 * @see DataSet#EXPOSED_PERIOD_DEVIATION
	 * @see DataSet#ASX_INFECTIOUS_RATE
	 * @see DataSet#CLOSE_CONTACT_INFECTIOUS_TIME
	 */
	private void setExposed() {
		// Una vez expuesto, no puede volver a contagiarse
		if (exposed)
			return;
		
		// Se contagia del virus
		exposed = true;
		InfectionReport.addExposed(ageGroup, currentState);
		int period = Utils.getStdNormalDeviate(DataSet.EXPOSED_PERIOD_MEAN, DataSet.EXPOSED_PERIOD_DEVIATION);
		
		// Define al comienzo de infeccion si va a ser asintomatico o sintomatico
		boolean asymp = (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ASX_INFECTIOUS_RATE[ageGroup]);
		InfectionReport.addDailyCases(ageGroup, asymp);
		
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
	 * Si es sintomatico y cuarentena preventiva esta habilitada, aisla integrantes del hogar.
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
				((HomeAgent) homePlace).startPreventiveQuarentine((int) schedule.getTickCount());
			}
			
			// Se aisla si es sintomatico
			startQuarantine();
			symInfectious = true;
			InfectionReport.modifySYMInfectiousCount(ageGroup, 1);
		}
		//
		
		if (currentBuilding != null && currentPosition != null) {
			// Si tiene position dentro de un Building, se crear el marcador de infeccioso
			currentBuilding.addSpreader(this);
			context.getBuildManager().createInfectiousHuman(agentID, currentBuilding.getCoordinate());
		}
				
		int period = Utils.getStdNormalDeviate(DataSet.INFECTED_PERIOD_MEAN, DataSet.INFECTED_PERIOD_DEVIATION);
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setRecovered");
	}
	
	/**
	 * Recuperacion de infeccion.<p>
	 * Si es sintomatico hay chances de que sea un caso grave y quedar internado en UTI<p>
	 * Un caso grave puede derivar en critico y causar la muerte pre-UTI<p>
	 * En caso de estar internado en UTI demora un tiempo hasta que vuelve al contexto.
	 * @see DataSet#VACCINE_SEVERE_CASE_CHANCE_MOD
	 * @see DataSet#DEFAULT_PREICU_DEATH_RATE
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
		
		// Fin periodo de contagio
		asxInfectious = false;
		symInfectious = false;
		// Se elimina como spreader y el marcador de infectado
		if (currentBuilding != null && currentPosition != null)
			currentBuilding.removeSpreader(this, currentPosition);
		context.getBuildManager().deleteInfectiousHuman(agentID);
		
		// Chance caso severo
		double severeCC = severeCaseChance;
		// Chequea inmunidad parcial por vacuna
		if (immunityLevel != DataSet.IMMUNITY_LVL_NONE) {
			severeCC *= DataSet.VACCINE_SEVERE_CASE_CHANCE_MOD[immunityLevel];
		}
		// Si es caso grave, se interna o muere sin internacion
		if (RandomHelper.nextDoubleFromTo(0, 100) <= severeCC) {
			if (RandomHelper.nextDoubleFromTo(0, 100) <= context.getPreICUDeathRate()) {
				// Caso critico, se muere antes de ICU
				deceased = true;
				InfectionReport.addDead(ageGroup);
			}
			else {
				// Mover a ICU hasta que se cure o muera
				hospitalized = true;
				InfectionReport.modifyHospitalizedCount(ageGroup, 1);
				// X dias en ICU
				ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(
						schedule.getTickCount() + DataSet.EXTENDED_ICU_PERIOD, ScheduleParameters.FIRST_PRIORITY);
				schedule.schedule(scheduleParams, this, "dischargeFromICU");
			}
			removeFromContext();
		}
		// Si es caso leve, se recupera y reanuda vida normal
		else {
			setImmune(true);
			InfectionReport.addRecovered(ageGroup);
		}
	}
	
	/**
	 * Da el alta de internacion en UTI o tiene un chance que fallezca. 
	 * @see DataSet#DEFAULT_ICU_DEATH_RATE
	 */
	public void dischargeFromICU() {
		hospitalized = false;
		InfectionReport.modifyHospitalizedCount(ageGroup, -1);
		if (RandomHelper.nextDoubleFromTo(0, 100) <= context.getICUDeathRate()) {
			// Se muere en ICU
			deceased = true;
			InfectionReport.addDead(ageGroup);
		}
		else {
			// Sale de ICU - continua vida normal
			setImmune(true);
			InfectionReport.addRecovered(ageGroup);
			addRecoveredToContext();
		}
	}
	
	/**
	 * Si es sintomatico, inicia el periodo de quarentena en el hogar.
	 * @see DataSet#QUARANTINED_PERIOD_MEAN
	 * @see DataSet#QUARANTINED_PERIOD_DEVIATION
	 */
	public void startQuarantine() {
		quarantined = true;
		// Calcula el periodo de quarentena
		int period = Utils.getStdNormalDeviate(DataSet.QUARANTINED_PERIOD_MEAN, DataSet.QUARANTINED_PERIOD_DEVIATION);
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
	 * Recuperar e inmunizar por medio natural o vacuna.
	 * @param natural por medio natural
	 */
	public void setImmune(boolean natural) {
		// Ni puede ganar inmunidad si no se recupero del contagio 
		if (exposed && !recovered)
			return;
		exposed = true;
		recovered = true;
		
		if (natural) {
			// Gana inmunidad total, por un tiempo
			immunityLevel = DataSet.IMMUNITY_LVL_HIGH;
			startImmunityPeriod(DataSet.NATURAL_IMMUNITY_PERIOD_MEAN, DataSet.NATURAL_IMMUNITY_PERIOD_DEVIATION);
		}
	}
	
	/**
	 * Setea nivel de inmunidad por vacuna o por fin de inmunidad natural.
	 * @param immLevel nivel de inmunidad
	 * @see DataSet#IMMUNITY_LVL_NONE
	 * @see DataSet#IMMUNITY_LVL_LOW
	 * @see DataSet#IMMUNITY_LVL_MED
	 * @see DataSet#IMMUNITY_LVL_HIGH 
	 */
	public void setImmunityLevel(int immLevel) {
		// Pierde toda inmunidad, pasa a susceptible si corresponde
		if (immLevel == DataSet.IMMUNITY_LVL_NONE) {
			if (immunityLevel == DataSet.IMMUNITY_LVL_HIGH) {
				setSusceptible();
			}
			immunityLevel = immLevel;
			return;
		}
		// Gana nivel de inmunidad, no se puede reducir (salvo a NONE)
		else if (immLevel > immunityLevel) {
			if (immunityLevel == DataSet.IMMUNITY_LVL_HIGH) {
				setImmune(false);
			}
			immunityLevel = immLevel;
		}
		startImmunityPeriod(DataSet.VACCINE_IMMUNITY_PERIOD_MEAN, DataSet.VACCINE_IMMUNITY_PERIOD_DEVIATION);
	}
	
	/**
	 * Calcula y programa la duracion de inmunidad adquirida.
	 * @param meanTicks media
	 * @param stdDevTicks desvio estandar
	 */
	public void startImmunityPeriod(int meanTicks, int stdDevTicks) {
		// Calcula el tiempo de inmunidad
		int period = Utils.getStdNormalDeviate(meanTicks, stdDevTicks);
		// Programa el fin de inmunidad
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		if (immunityAction != null)
			schedule.removeAction(immunityAction);
		immunityAction = schedule.schedule(scheduleParams, this, "setImmunityLevel", DataSet.IMMUNITY_LVL_NONE);
	}
	
	/**
	 * Reinicia el estado de expuesto y recuperado.
	 */
	public void setSusceptible() {
		exposed = false;
		recovered = false;
	}
	
	/**
	 * Setea los parametros de actividad programada.
	 * @param actState indice de estado (0,1,2,3)
	 * @param building parcela donde realiza actividad
	 * @param ticksDuration duracion en ticks de actividad
	 */
	public void queueActivity(int actState, BuildingAgent building, int ticksDuration) {
		queuedActivityState = actState;
		queuedBuilding = building;
		queuedDuration = ticksDuration;
		activityQueued = true;
	}
	
	/**
	 * Selecciona la parcela donde realizar la nueva actividad.
	 * @param prevStateIndex indice de estado previo
	 * @param stateIndex indice de nuevo estado
	 * @return <b>BuildingAgent</b> o <b>null</b>
	 */
	public BuildingAgent switchActivity(int prevStateIndex, int stateIndex) {
		BuildingAgent newBuilding;
        switch (stateIndex) {
	    	case 0: // 0 Casa
	    		newBuilding = homePlace;
	    		break;
	    	case 1: // 1 Trabajo / Estudio
	    		newBuilding = workPlace;
	    		break;
	    	default: // 2 Ocio / 3 Otros (supermercados, farmacias, etc)
	    		newBuilding = context.getBuildManager().findRandomPlace(sectoralType, sectoralIndex, stateIndex, this, currentBuilding, ageGroup);
	    		break;
        }
        return newBuilding;
	}
	
    /**
     * Cambia el estado y parcela segun TMMC (Timed mobility markov chains).
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
    		relocate(queuedBuilding, queuedActivityState);
    		return;
    	}
    	
    	// Calcula el indice del periodo del dia, segun tick actual
        final int p = ((int)schedule.getTickCount() % 24) / 6;	// 0 1 2 3
        // Probabilidad aleatoria de 1 a 1000 para elegir estado en matrices
        int r = RandomHelper.nextIntFromTo(1, 1000);
        int i = 0;
        
        int[][][] matrixTMMC = null;
        if (!foreignTraveler)
        	matrixTMMC = (!quarantined ? context.getLocalTMMC(sectoralType, ageGroup) : context.getIsolatedLocalTMMC(ageGroup));
        else if (touristTraveler)
        	matrixTMMC = MarkovChains.TOURIST_DEFAULT_TMMC;
        //else
        //	matrixTMMC = (!quarantined ? travelerTMMC : infectedTravelerTMMC);
        
        // Recorre la matriz correspondiente al periodo del dia y estado actual
        while (r > matrixTMMC[p][currentState][i]) {
        	// Avanza el indice de estado, si es posible restar probabilidad
        	r -= matrixTMMC[p][currentState][i];
        	++i;
        }
        
        // Si el nuevo lugar es de distinto tipo, lo cambia
        if (currentState != i) {
        	BuildingAgent tempBuilding = switchActivity(currentState, i);
        	relocate(tempBuilding, i);
        }
        
        // Incrementa el tiempo del estado actual
        InfectionReport.increaseStateTime(i);
    }
    
	/**
	 * Cambia la ubicacion del agente e indice de estado.
	 * @param newBuilding nueva parcela o null
	 * @param newState indice nuevo estado
	 */
	private void relocate(BuildingAgent newBuilding, int newState) {
    	// Lo remueve si esta dentro de una parcela
        if (currentBuilding != null) {
        	currentBuilding.removeHuman(this, currentPosition);
        	currentBuilding = null;
        }
        // Si estuvo afuera, tiene una chance de volver infectado
        else if (!exposed && context.localOutbreakStarted()) {
			double oocInfRate = (schedule.getTickCount() - relocationTime)
					/ Temperature.getOOCContagionChance(context.getTownRegion(), context.getOOCContagionValue());
        	checkContagion(oocInfRate);
        }
        currentState = newState;
        
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
