package geocovid.agents;

import static repast.simphony.essentials.RepastEssentials.AddAgentToContext;
import static repast.simphony.essentials.RepastEssentials.RemoveAgentFromContext;

import java.util.HashMap;
import java.util.Map;

import cern.jet.random.Normal;
import geocovid.BuildingManager;
import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.MarkovChains;
import geocovid.Temperature;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;

public class HumanAgent {
	// Puntero matriz markov //
	public static int[][][][][] localTMMC			= new int[2][DataSet.AGE_GROUPS][][][];
	public static int[][][][] isolatedLocalTMMC		= new int[DataSet.AGE_GROUPS][][][];
	
	public static int[][][] travelerTMMC;
	public static int[][][] infectedTravelerTMMC;
	///////////////////////////
	
	private BuildingAgent currentBuilding = null;
	private BuildingAgent homePlace;
	private BuildingAgent workPlace;
	
	private int[] currentPosition = {0,0};
	private int[] workplacePosition = {0,0};
	
	private int sectoralType;
	private int sectoralIndex;
	private int currentActivity = 0; // Localizacion actual es el estado de markov donde esta. El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros (supermercados, farmacias, etc)
	private int ageGroup = 0;
	private boolean foreignTraveler = false;
	private boolean touristTraveler = false;
	private double relocationTime = -1d;
	protected int halfTicksDelay = 0;
	
	private boolean activityQueued = false;
	private int queuedDuration;
	private BuildingAgent queuedBuilding;
	private int queuedActIndex;
	
	protected static ISchedule schedule;
	private static Normal actDist = RandomHelper.createNormal(2, 1);
	private static int agentIDCounter = 0;
	private int agentID = ++agentIDCounter;
	
    // ESTADOS //
    public boolean exposed			= false;
    public boolean asxInfectious	= false; // Asymptomatic Infectious
    public boolean symInfectious	= false; // Symptomatic Infectious
    public boolean hospitalized		= false; // Hospitalized to ICU
    public boolean recovered		= false;
    // Extras
    public boolean quarantined		= false; // Si se aisla por ser sintomatico
    public boolean distanced		= false; // Si respeta distanciamiento social
    //
    private boolean inCloseContact	= false; // Si estuvo en contacto estrecho con sinto
    private boolean preInfectious	= false; // Si "contagia" a contactos estrechos
    /////////////
    
    private double infectiousStartTime;
    
    private Map<Integer, Integer> socialInteractions = new HashMap<>(); // ID contacto, cantidad de contactos
    
	public HumanAgent(int secHome, int secHomeIndex, int ageGroup, BuildingAgent home, BuildingAgent work, int[] workPos) {
		this.sectoralType = secHome;
		this.sectoralIndex = secHomeIndex;
		this.homePlace = home;
		this.workPlace = work;
		this.workplacePosition = workPos;
		this.ageGroup = ageGroup;
	}
    
	public HumanAgent(int secHome, int secHomeIndex, int ageGroup, BuildingAgent home, BuildingAgent work, int[] workPos, boolean foreign, boolean tourist) {
		this(secHome, secHomeIndex, ageGroup, home, work, workPos);
		this.foreignTraveler = foreign;
		this.touristTraveler = tourist;
	}
	
	public static void initAgentID() {
		agentIDCounter = 0;
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
	}
	
	public int getAgentID() {
		return agentID;
	}
	
	public int getAgeGroup() {
		return ageGroup;
	}
	
	public boolean isForeign() { 
		return foreignTraveler;
	}
	
	public BuildingAgent getPlaceOfWork() {
		return workPlace;
	}

	public boolean wasExposed() {
		return exposed;
	}
	
	public boolean isContagious() {
		return (asxInfectious || symInfectious);
	}
	
	public boolean atWork() {
		return (currentActivity == 1);
	}
	
	public boolean isIsolated() {
		return quarantined;
	}
	
	public int[] getCurrentPosition() {
		return currentPosition;
	}
	
	public double getRelocationTime() {
		return relocationTime;
	}
	
	public boolean isPreInfectious() {
		return preInfectious;
	}
	
	public double getInfectiousStartTime() {
		return infectiousStartTime;
	}
	
	public boolean isActivityQueued() {
		return activityQueued;
	}
	
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
	
	public void setSociallyDistanced(boolean sociallyDistanced) {
		distanced = sociallyDistanced;
	}
	
	public void addSocialInteraction(int humanId) {
		// Aca uso la ID del humano como key, por si se quiere saber cuantos contactos se repiten
		if (socialInteractions.containsKey(humanId))
			socialInteractions.put(humanId, socialInteractions.get(humanId) + 1);
		else
			socialInteractions.put(humanId, 1);
	}
	
	/**
	 * Informa la cantidad de contactos en el dia (si corresponde) y reinicia el Map
	 */
	@ScheduledMethod(start = 12d, interval = 12d, priority = ScheduleParameters.FIRST_PRIORITY)
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
	
	public void removeInfectiousFromContext() {
		if (currentBuilding != null) {
			currentBuilding.removeHuman(this, currentPosition);
			currentBuilding = null;
		}
		RemoveAgentFromContext("GeoCOVID-19", this);
	}
	
	public void addRecoveredToContext() {
		// Si esta hospitalizado o vive afuera no vuelve a entrar
		if (hospitalized)
			return;
		
		AddAgentToContext("GeoCOVID-19", this);
		currentActivity = 0;
		currentPosition = homePlace.insertHuman(this);
		currentBuilding = homePlace;
		switchLocation();
	}
	
	public void setCloseContact(double startTime) {
		if (!inCloseContact) {
			inCloseContact = true;
			// Programa el inicio de cuarentena preventiva
			ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(startTime, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(scheduleParams, this, "startSelfQuarantine");
		}
	}
	
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
		boolean asynto = (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ASX_INFECTIOUS_RATE[ageGroup]) ? true : false;
		
		// Programa el inicio del periodo de contagio
		infectiousStartTime = schedule.getTickCount() + period;
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(infectiousStartTime, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setInfectious", asynto, false);
		
		// Si es sintomatico, programa el inicio del periodo de "pre contagio" a contactos estrechos
		if (!asynto && DataSet.closeContactsEnabled()) {
			scheduleParams = ScheduleParameters.createOneTime(infectiousStartTime - DataSet.CLOSE_CONTACT_INFECTIOUS_TIME, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(scheduleParams, this, "setPreInfectious", true);
		}
	}
	
	public void setInfectious(boolean asyntomatic, boolean initial) {
		// Si es un primer caso, es siempre asintomatico
		if (initial) {
			exposed = true;
			InfectionReport.addExposed(ageGroup);
		}
		// Verificar que sea expuesto
		if (!exposed)
			return;
		
		// Comienza la etapa de contagio asintomatico o sintomatico
		if (asyntomatic) {
			asxInfectious = true;
			InfectionReport.modifyASXInfectiousCount(ageGroup, 1);
		}
		else {
			// Si es local y cuarentena preventiva esta habilitada
			if (!foreignTraveler && DataSet.prevQuarantineEnabled()) {
				// Todos los habitantes del hogar se ponen en cuarentena (exepto los ya expuestos)
				((HomeAgent) homePlace).startPreventiveQuarentine(schedule.getTickCount());
			}
			
			// Si se complica el caso, se interna - si no continua vida normal
			if (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ICU_CHANCE_PER_AGE_GROUP[ageGroup]) {
				// Mover a ICU hasta que se cure o muera
				hospitalized = true;
				InfectionReport.modifyHospitalizedCount(ageGroup, 1);
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
			BuildingManager.createInfectiousHuman(agentID, currentBuilding.getCoordinate());
		}
		
		int mean = DataSet.INFECTED_PERIOD_MEAN_AG;
		int std = DataSet.INFECTED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setRecovered");
	}
	
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
			
			if (currentBuilding != null)
				currentBuilding.removeSpreader(this, currentPosition);
			// Se borra el marcador de infectado
			BuildingManager.deleteInfectiousHuman(agentID);
		}
		else {
			// 5 dias mas en ICU
			ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + DataSet.EXTENDED_ICU_PERIOD, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(scheduleParams, this, "dischargeFromICU");
		}
	}
	
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
	
	public void startQuarantine() {
		quarantined = true;
		
		int mean = DataSet.QUARANTINED_PERIOD_MEAN_AG;
		int std = DataSet.QUARANTINED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "stopQuarantine");
	}
	
	public void startSelfQuarantine() {
		inCloseContact = false;
		quarantined = true;
		// Programa el fin de cuarentena preventiva
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + DataSet.PREVENTIVE_QUARANTINE_TIME, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "stopQuarantine");
	}
	
	public void stopQuarantine() {
		// Sale unicamente de cuarentena cuando no tiene sintomas
		if (!symInfectious)
			quarantined = false;
	}
	
	public void queueActivity(int actIndex, BuildingAgent building, int ticksDuration) {
		queuedActIndex = actIndex;
		queuedBuilding = building;
		queuedDuration = ticksDuration;
		activityQueued = true;
	}
	
	public BuildingAgent switchActivity(int prevActivityIndex, int activityIndex) {
		BuildingAgent newBuilding;
    	int halfTick, ndValue;
        switch (activityIndex) {
	    	case 0: // 0 Casa
	    		newBuilding = homePlace;
	        	ndValue = actDist.nextInt();
	        	halfTick = (ndValue > 3) ? 3 : (ndValue < 1 ? 1: ndValue); // .5, 1, 1.5 ticks
	    		break;
	    	case 1: // 1 Trabajo / Estudio
	    		newBuilding = workPlace;
	    		halfTick = 6; // 3 ticks
	    		break;
	    	case 2: // 2 Ocio
	    		newBuilding = BuildingManager.findRandomPlace(sectoralType, sectoralIndex, 2, this, currentBuilding, ageGroup);
	        	ndValue = actDist.nextInt();
	        	halfTick = (ndValue > 3) ? 4 : (ndValue < 1 ? 2 : ndValue + 1); // 1, 1.5, 2 ticks
	    		break;
	    	default: // 3 Otros (supermercados, farmacias, etc)
	    		newBuilding = BuildingManager.findRandomPlace(sectoralType, sectoralIndex, 3, this, currentBuilding, ageGroup);
	    		halfTick = 1; // .5 ticks
	    		break;
        }
        
        // Incrementa el tiempo de la actividad realizada
        if (activityIndex != 1)
        	InfectionReport.addActivityTime(activityIndex, halfTick);
        else if (DataSet.COUNT_WORK_FROM_HOME_AS_HOME && newBuilding == homePlace) // Si estudia/trabaja desde el hogar
        	InfectionReport.addActivityTime(0, halfTick);
        else
        	InfectionReport.addActivityTime(1, halfTick);
        
        halfTicksDelay = halfTick;
		return newBuilding;
	}
	
    /**
     * Cambia la posicion en la grilla segun TMMC (Timed mobility markov chains).
     */
	@ScheduledMethod(start = 0d, interval = 0.5d, priority = 0.6d)
    public void switchLocation() {
		// Resta medio tick
    	if (--halfTicksDelay > 0) {
    		return;
    	}
		
    	// No se mueve si esta internado
    	if (hospitalized) {
    		removeInfectiousFromContext();
    		return;
    	}
    	if (activityQueued) {
    		//-if (queuedDuration-- == 0)
    		//-	activityQueued = false;
    		activityQueued = false;
    		halfTicksDelay = queuedDuration << 1; // de ticks a medios ticks
    		relocate(queuedBuilding, queuedActIndex);
    		return;
    	}
    	
	    boolean switchBuilding = false;
        final int p = ((int)schedule.getTickCount() % 12) / 3;	// 0 1 2 3
        int r = RandomHelper.nextIntFromTo(1, 1000);
        int i = 0;
        
        int[][][] matrixTMMC;
        if (!foreignTraveler)
        	matrixTMMC = (!quarantined ? localTMMC[sectoralType][ageGroup] : isolatedLocalTMMC[ageGroup]);
        else if (touristTraveler)
        	matrixTMMC = MarkovChains.TOURIST_DEFAULT_TMMC;
        else
        	matrixTMMC = (!quarantined ? travelerTMMC : infectedTravelerTMMC);
        
        while (r > matrixTMMC[p][currentActivity][i]) {
        	// La suma de las pobabilidades no debe dar mas de 1000
        	r -= matrixTMMC[p][currentActivity][i];
        	++i;
        }
        
        // Si el nuevo lugar es del mismo tipo, y no es el hogar o trabajo, lo cambia
        if ((currentActivity != i) || (i > 1)) {
        	switchBuilding = true;
        }
        
        BuildingAgent tempBuilding = switchActivity(currentActivity, i);
        if (switchBuilding) {
        	relocate(tempBuilding, i);
        }
    }
    
    private void relocate(BuildingAgent newBuilding, int newActivity) {
    	// Si esta dentro de un inmueble
        if (currentBuilding != null) {
        	currentBuilding.removeHuman(this, currentPosition);
        	currentBuilding = null;
        }
        else if (!exposed && InfectionReport.outbreakStarted) {
        	// Si estuvo afuera, tiene una chance de volver infectado
        	int infectChance = (int) (schedule.getTickCount() - relocationTime);
    		if (RandomHelper.nextIntFromTo(1, Temperature.getOOCContagionChance()) <= infectChance) {
    			setExposed();
    		}
        }
        currentActivity = newActivity;
        
    	// Si el nuevo lugar es un inmueble
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
				BuildingManager.moveInfectiousHuman(agentID, newBuilding.getCoordinate());
			}
    	}
    	else if (isContagious() && atWork()) {
    		// Si va afuera a trabajar y es contagioso, oculto el marcador
    		BuildingManager.hideInfectiousHuman(agentID);
    	}
    	relocationTime = schedule.getTickCount();
    }
}
