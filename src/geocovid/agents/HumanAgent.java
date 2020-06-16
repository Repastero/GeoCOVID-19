package geocovid.agents;

import static repast.simphony.essentials.RepastEssentials.RemoveAgentFromContext;
import static repast.simphony.essentials.RepastEssentials.AddAgentToContext;

import geocovid.BuildingManager;
import geocovid.DataSet;
import geocovid.InfeccionReport;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.*;
import repast.simphony.random.RandomHelper;

public class HumanAgent {
	// Puntero matriz markov //
	public static int[][][] youngTMMC;
	public static int[][][] adultTMMC;
	public static int[][][] elderTMMC;
	public static int[][][] travelerTMMC;
	public static int[][][] infectedYoungTMMC;
	public static int[][][] infectedAdultTMMC;
	public static int[][][] infectedElderTMMC;
	///////////////////////////
	
	private BuildingAgent currentBuilding = null;
	private BuildingAgent homePlace;
	private BuildingAgent workPlace;
	
	private int[] currentPosition = {0,0};
	private int[] workplacePosition = {0,0};
	
	private int currentActivity = 0; // Localizacion actual es el estado de markov donde esta. El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros (supermercados, farmacias, etc)
	private int indexTMMC;
	private int ageGroup = 0;
	private double icuChance = DataSet.ICU_CHANCE_PER_AGE_GROUP[1];
	private double asxChance = DataSet.ASX_INFECTIOUS_RATE[1];
	private double travelRadius = -1;
	
	protected static ISchedule schedule;
	private static int agentIDCounter = 0;
	private int agentID = ++agentIDCounter;
	
    // ESTADOS //
    public boolean exposed			= false;
    public boolean asxInfectious	= false; // Asymptomatic Infectious
    public boolean symInfectious	= false; // Symptomatic Infectious
    public boolean hospitalized		= false; // Hospitalized to ICU
    public boolean recovered		= false;
    public boolean dead				= false;
    /////////////
    
	public HumanAgent(int ageGroup, BuildingAgent home, BuildingAgent work, int[] workPos, int tmmc) {
		this.homePlace = home;
		this.workPlace = work;
		this.workplacePosition = workPos;
		this.indexTMMC = tmmc;
		this.ageGroup = ageGroup;
		this.icuChance = DataSet.ICU_CHANCE_PER_AGE_GROUP[ageGroup];
		this.travelRadius = DataSet.TRAVEL_RADIUS_PER_AGE_GROUP[ageGroup];
		this.asxChance = DataSet.ASX_INFECTIOUS_RATE[ageGroup];
	}
	
	public static void initAgentID() {
		agentIDCounter = 0;
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
	}
	
	public int getAgentID() {
		return agentID;
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
	
	public int[] getCurrentPosition() {
		return currentPosition;
	}
	
	/**
	 * Los Humanos locales empiezan en sus casas por el primer medio tick
	 */
	public void setStartLocation() {
		// Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(0d, 0.6d);
		schedule.schedule(params, this, "switchLocation");
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
	
	public void setExposed() {
		// Una vez expuesto, no puede volver a contagiarse
		if (exposed)
			return;
		// Se contagia del virus
		exposed = true;
		InfeccionReport.modifyExposedCount(ageGroup, 1);
		int mean = DataSet.EXPOSED_PERIOD_MEAN;
		int std = DataSet.EXPOSED_PERIOD_DEVIATION;
		double period = RandomHelper.createNormal(mean, std).nextDouble();
		period = (period > mean+std) ? mean+std : (period < mean-std ? mean-std: period);
		
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, this, "setInfectious");
	}
	
	public void setInfectious() {
		// Verificar que sea expuesto
		if (!exposed)
			return;
		
		// Comienza la etapa de contagio asintomatico o sintomatico
		if (RandomHelper.nextDoubleFromTo(0, 100) <= asxChance) {
			asxInfectious = true;
			InfeccionReport.modifyASXInfectiousCount(ageGroup, 1);
		}
		else {
			// Si se complica el caso, se interna - si no continua vida normal
			if (RandomHelper.nextDoubleFromTo(0, 100) <= icuChance) {
				// Mover a ICU hasta que se cure o muera
				hospitalized = true;
				InfeccionReport.modifyHospitalizedCount(ageGroup, 1);
			}
			symInfectious = true;
			InfeccionReport.modifySYMInfectiousCount(ageGroup, 1);
		}
		//
		
		if (!hospitalized && currentBuilding != null) {
			currentBuilding.addSpreader(this);
			// Si no fue a ICU y esta dentro de un Building, se crear el marcador de infeccioso
			BuildingManager.createInfectiousHuman(agentID, currentBuilding.getGeometry().getCoordinate());
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
			InfeccionReport.modifyASXInfectiousCount(ageGroup, -1);
		else if (symInfectious)
			InfeccionReport.modifySYMInfectiousCount(ageGroup, -1);
		else
			return;
		
		// Se recupera de la infeccion
		asxInfectious = false;
		symInfectious = false;
		if (!hospitalized) {
			recovered = true;
			InfeccionReport.modifyRecoveredCount(ageGroup, 1);
			
			if (currentBuilding != null)
				currentBuilding.removeSpreader(this, currentPosition);
			// Se borra el marcador de infectado
			BuildingManager.deleteInfectiousHuman(agentID);
		}
		else {
			hospitalized = false;
			InfeccionReport.modifyHospitalizedCount(ageGroup, -1);
			if (RandomHelper.nextDoubleFromTo(0, 100) <= DataSet.ICU_DEATH_RATE) {
				// Se muere en ICU
				dead = true; // flag por si quiere volver al contexto
				InfeccionReport.modifyDeathsCount(ageGroup, 1);
			}
			else {
				// Sale de ICU - continua vida normal
				recovered = true;
				InfeccionReport.modifyRecoveredCount(ageGroup, 1);
				addRecoveredToContext();
			}
		}
	}

	public BuildingAgent switchActivity(int prevActivityIndex, int activityIndex) {
		BuildingAgent newBuilding;
    	int mean, stdDev, maxDev, ndValue;
        switch (activityIndex) {
	    	case 0: // 0 Casa
	    		newBuilding = homePlace;
	    		mean = 100;
	    		stdDev = 50;
	    		maxDev = 75;
	    		break;
	    	case 1: // 1 Trabajo / Estudio
	    		newBuilding = workPlace;
	    		mean = 150;
	    		stdDev = 10;
	    		maxDev = 20;
	    		break;
	    	case 2: // 2 Ocio
	    		newBuilding = BuildingManager.findRandomPlace(2, currentBuilding, travelRadius);
	    		mean = 50;
	    		stdDev = 10;
	    		maxDev = 25;
	    		break;
	    	default: // 3 Otros (supermercados, farmacias, etc)
	    		newBuilding = BuildingManager.findRandomPlace(3, currentBuilding, travelRadius);
	    		mean = 25;
	    		stdDev = 10;
	    		maxDev = 20;
	    		break;
        }
        
        // Calcular tiempo hasta que cambie nuevamente de posicion
	    ndValue = RandomHelper.createNormal(mean, stdDev).nextInt();
	    ndValue = (ndValue > mean+maxDev) ? mean+maxDev : (ndValue < mean-maxDev ? mean-maxDev: ndValue);
	    double temp = schedule.getTickCount() + (ndValue*0.02d);
	    
   	 	// Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(temp, 0.6d); // ScheduleParameters.FIRST_PRIORITY
		schedule.schedule(params, this, "switchLocation");
		
		return newBuilding;
	}
	
	
    /**
    * Cambia la posicion en la grilla segun TMMC (Timed mobility markov chains).
    */
    public void switchLocation() {
    	// No se mueve si esta internado
    	if (hospitalized) {
    		removeInfectiousFromContext();
    		return;
    	}
    	
	    BuildingAgent newBuilding;
	    boolean switchBuilding = false;
        final int p = ((int)schedule.getTickCount() % 12) / 3;	// 0 1 2 3
        int r = RandomHelper.nextIntFromTo(1, 1000);
        int i = 0;
        
        int[][][] matrixTMMC;
        switch (indexTMMC) {
			case 0:
				matrixTMMC = (!symInfectious ? youngTMMC : infectedYoungTMMC);
				break;
			case 1:
				matrixTMMC = (!symInfectious ? adultTMMC : infectedAdultTMMC);
				break;
			case 2:
				matrixTMMC = (!symInfectious ? elderTMMC : infectedElderTMMC);
				break;
			default:
				matrixTMMC = travelerTMMC;
				break;
		}
        
        while (r > matrixTMMC[p][currentActivity][i]) {
        	// La suma de las pobabilidades no debe dar mas de 1000
        	r -= matrixTMMC[p][currentActivity][i];
        	++i;
        }
        
        // Si el nuevo lugar es del mismo tipo, y no es el hogar o trabajo, lo cambia
        if ((currentActivity != i) || (i > 1)) {
        	switchBuilding = true;
        }

        newBuilding = switchActivity(currentActivity, i);
        currentActivity = i;
        
        if (switchBuilding) {
        	// Si esta dentro de un inmueble
            if (currentBuilding != null) {
            	currentBuilding.removeHuman(this, currentPosition);
            	currentBuilding = null;
            }
        	// Si el nuevo lugar es un inmueble
        	if (newBuilding != null) {
        		// Si tiene un lugar de trabajo especifico
        		if ((currentActivity == 1) && (workplacePosition != null)) {
        			currentPosition = newBuilding.insertHuman(this, workplacePosition);
        		}
        		else
        			currentPosition = newBuilding.insertHuman(this);
        		currentBuilding = newBuilding;

        		if (isContagious() && currentPosition != null) {
   					// Si en periodo de contagio y dentro del edificio, mover el marcador
   					BuildingManager.moveInfectiousHuman(agentID, newBuilding.getGeometry().getCoordinate());
    			}
        	}
        	else if (isContagious() && currentActivity == 1) {
        		// Si va afuera a trabajar y es contagioso, oculto el marcador
        		BuildingManager.hideInfectiousHuman(agentID);
        	}
        }
    }
}
