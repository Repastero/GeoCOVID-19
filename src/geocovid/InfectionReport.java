package geocovid;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * Lleva la sumatoria de los estados de los Humanos (expuestos, hospitalizados, etc), contactos personales y actividades diarias.
 */
public class InfectionReport {
	public static int simulationStartDay;
	//
	private static int deathLimit;
	//
	private static int cumExposedAll;		// Acumulado
	private static int cumExposedToCSAll;	// Acumulado
	private static int cumExposedToAeroAll;	// Acumulado
	private static int insAsxInfectiousAll;	// Instantaneo
	private static int insSymInfectiousAll;	// Instantaneo
	private static int insHospitalizedAll;	// Instantaneo
	private static int cumHospitalizedAll;	// Acumulado
	private static int cumRecoveredAll;		// Acumulado
	private static int cumDeathsAll;		// Acumulado
	private static int dailyAsxCasesAll; 	// Instantaneo
	private static int dailySymCasesAll; 	// Instantaneo
	private static int cumVaccinedAll;			// Acumulado
	private static int cumVaccinedTwoDoseAll;	
	//
	private static int[] cumExposed;		// Acumulado
	private static int[] insAsxInfectious;	// Instantaneo
	private static int[] insSymInfectious;	// Instantaneo
	private static int[] insHospitalized;	// Instantaneo
	private static int[] cumHospitalized;	// Acumulado
	private static int[] cumRecovered;		// Acumulado
	private static int[] cumDeaths;			// Acumulado
	private static int[] dailyCases;		// Instantaneo
	private static int[] cumVaccined;					// Acumulado+
	private static int[] cumVaccinedTwoDose;
	//
	private static int[] dailyStatesContagion;	// Instantaneo
	//
	private static int contextsInteracting;	// Cantidad de sub contextos
	private static double[] avgContextsSI;	// Contactos promedio por sub contextos
	//
	private static int[] dailyStatesTicks;		// Cuenta ticks
	private static int totalDailyStatesTicks;	// Cuenta ticks
	//
	private static int cumExposedPublicTransport;	// Acumulado
	private static int cumTicketTransportPublic;	// Acumulado
	private static int insSpacePublicTransport;		// 
	//
	private static int cumExposedSchool;
	
	public InfectionReport(int startDay, int maxDeaths) {
		simulationStartDay	= startDay;
		deathLimit			= maxDeaths;
		
		cumExposedAll		= 0;
		cumExposedToCSAll	= 0;
		cumExposedToAeroAll = 0;
		insAsxInfectiousAll	= 0;
		insSymInfectiousAll	= 0;
		insHospitalizedAll	= 0;
		cumHospitalizedAll	= 0;
		cumRecoveredAll		= 0;
		cumDeathsAll		= 0;
		dailyAsxCasesAll	= 0;
		dailySymCasesAll	= 0;
		cumVaccinedAll		= 0;	// Acumulado
		cumVaccinedTwoDoseAll= 0;
		
		cumExposed		= new int[DataSet.AGE_GROUPS];
		insAsxInfectious= new int[DataSet.AGE_GROUPS];
		insSymInfectious= new int[DataSet.AGE_GROUPS];
		insHospitalized	= new int[DataSet.AGE_GROUPS];
		cumHospitalized	= new int[DataSet.AGE_GROUPS];
		cumRecovered	= new int[DataSet.AGE_GROUPS];
		cumDeaths		= new int[DataSet.AGE_GROUPS];
		cumVaccined		= new int[DataSet.AGE_GROUPS];
		cumVaccinedTwoDose = new int[DataSet.AGE_GROUPS];
		dailyCases      =  new int[DataSet.AGE_GROUPS];
		
		contextsInteracting		= 0;
		avgContextsSI	= new double[DataSet.AGE_GROUPS];
		
		dailyStatesContagion= new int[4];
		dailyStatesTicks	= new int[4];
		totalDailyStatesTicks	= 0;
		
		cumExposedPublicTransport  =  0;
		cumTicketTransportPublic   =  0;
		insSpacePublicTransport    =  0;
		
		cumExposedSchool     =  0;
	}
	
	@ScheduledMethod(start = 24, interval = 24, priority = 1)
	public void checkPandemicEnd() {
		int i;
		// Reinicia contadores de contactos personales promedio y contagios diarios
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			avgContextsSI[i] = 0d;
			dailyCases[i] = 0;
		}
		contextsInteracting = 0;
		// Reinicia contadores de nuevos casos diarios
		dailyAsxCasesAll = 0;
		dailySymCasesAll = 0;
		// Reinicia los contadores de contagios en estados y ticks por estados
		for (i = 0; i < 4; i++) {
			dailyStatesContagion[i] = 0;
			dailyStatesTicks[i] = 0;
		}
		totalDailyStatesTicks = 0;
		// Termina la simulacion si se supera el limite te muertes (si existe)
		if (deathLimit != 0 && deathLimit < cumDeathsAll) {
			System.out.println("Simulacion finalizada por limite de muertes: "+deathLimit);
			RunEnvironment.getInstance().endRun();
		}
	}
	
	public static void increaseStateTime(int stateIndex) {
		++dailyStatesTicks[stateIndex];
		++totalDailyStatesTicks;
	}
	
	public static void updateSocialInteractions(double[] avgInteractions) {
		// Acumulado diario
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			avgContextsSI[i] += avgInteractions[i];
		}
		++contextsInteracting;
	}
	
	public static void addExposedToCS() {
		// Acumulado
		++cumExposedToCSAll;
	}
	
	public static void addExposedToAero() {
		// Acumulado
		++cumExposedToAeroAll;
	}
	
	public static void addExposed(int agIndex) {
		// Acumulado
		++cumExposedAll;
		++cumExposed[agIndex];
	}
	
	public static void addExposed(int agIndex, int state) {
		++dailyStatesContagion[state];
		addExposed(agIndex);
	}
	
	public static void modifyASXInfectiousCount(int agIndex, int value) {
		// Instantaneo
		insAsxInfectiousAll += value;
		insAsxInfectious[agIndex] += value;
	}
	
	public static void modifySYMInfectiousCount(int agIndex, int value) {
		// Instantaneo
		insSymInfectiousAll += value;
		insSymInfectious[agIndex] += value;
	}
	
	public static void modifyHospitalizedCount(int agIndex, int value) {
		// Acumulado
		if (value > 0) {
			cumHospitalizedAll += value;
			cumHospitalized[agIndex] += value;
		}
		// Instantaneo
		insHospitalizedAll += value;
		insHospitalized[agIndex] += value;
	}
	
	public static void addRecovered(int agIndex) {
		// Acumulado
		++cumRecoveredAll;
		++cumRecovered[agIndex];
	}
	
	public static void addDead(int agIndex) {
		// Acumulado
		++cumDeathsAll;
		++cumDeaths[agIndex];
	}
	
	public static void addDailyCases(int agIndex, boolean asx) {
		if (asx)
			++dailyAsxCasesAll;
		else
			++dailySymCasesAll;
		++dailyCases[agIndex];
	}
	
	public static String getInfectedReport(int agIndex) {
		return String.format("%-12s: Infectados %5d | Hospitalizados %4d | Muertos %3d",
				DataSet.AGE_GROUP_LABELS[agIndex], getCumExposed(agIndex), getCumHospitalized(agIndex), getCumDeaths(agIndex));
	}
	
	public static void addCumExposedPublicTransport() {
		++cumExposedPublicTransport;
	}
	
	public static void removeCumExposedPublicTransport() {
		--cumExposedPublicTransport;
	}
	
	public static void addCumTicketTransportPublic() {
		++cumTicketTransportPublic;
	}
	
	public static void addSeatStill() {
		++insSpacePublicTransport;
	}
	
	public static void removeSeatStill() {
		--insSpacePublicTransport;
	}
	
	public static void addCumExposedSchool() {
		++cumExposedSchool;
	}
	
	public static void addVaccined(int agIndex) {
		// Acumulado
		++cumVaccinedAll;
		++cumVaccined[agIndex];
	}
	
	public static void addVaccinedTwoDose(int agIndex) {
		// Acumulado
		++cumVaccinedTwoDoseAll;
		++cumVaccinedTwoDose[agIndex];
	}
	
	
	// Getters para usar en reportes de Repast Simphony
	public static int getCumExposed()		{ return cumExposedAll; }
	public static int getCumExposedToCS()	{ return cumExposedToCSAll; }
	public static int getCumExposedToAero()	{ return cumExposedToAeroAll; }
	public static int getInsASXInfectious()	{ return insAsxInfectiousAll; }
	public static int getInsSYMInfectious()	{ return insSymInfectiousAll; }
	public static int getInsInfectious()	{ return insAsxInfectiousAll + insSymInfectiousAll; }
	public static int getInsHospitalized()	{ return insHospitalizedAll; }
	public static int getCumHospitalized()	{ return cumHospitalizedAll; }
	public static int getCumRecovered()		{ return cumRecoveredAll; }
	public static int getCumDeaths()		{ return cumDeathsAll; }
	public static int getInsDailyAsxCases()	{ return dailyAsxCasesAll; }
	public static int getInsDailySymCases()	{ return dailySymCasesAll; }
	public static int getInsDailyCases()	{ return dailyAsxCasesAll + dailySymCasesAll; }
	public static int getCumVaccined() 		{ return cumVaccinedAll; }
	public static int getCumVaccinedTwoDose() 		{ return cumVaccinedTwoDoseAll; }
	
	public static int getCumExposed(int ai)			{ return cumExposed[ai]; }
	public static int getInsASXInfectious(int ai)	{ return insAsxInfectious[ai]; }
	public static int getInsSYMInfectious(int ai)	{ return insSymInfectious[ai]; }
	public static int getInsInfectious(int ai)		{ return insAsxInfectious[ai] + insSymInfectious[ai]; }
	public static int getInsHospitalized(int ai)	{ return insHospitalized[ai]; }
	public static int getCumHospitalized(int ai)	{ return cumHospitalized[ai]; }
	public static int getCumRecovered(int ai)		{ return cumRecovered[ai]; }
	public static int getCumDeaths(int ai)			{ return cumDeaths[ai]; }
	public static int getInsDailyCases(int ai)		{ return dailyCases[ai]; }
	
	public static int getChildCumExposed()			{ return cumExposed[0]; }
	public static int getChildInsASXInfectious()	{ return insAsxInfectious[0]; }
	public static int getChildInsSYMInfectious()	{ return insSymInfectious[0]; }
	public static int getChildInsInfectious()		{ return insAsxInfectious[0] + insSymInfectious[0]; }
	public static int getChildInsHospitalized()		{ return insHospitalized[0]; }
	public static int getChildCumHospitalized()		{ return cumHospitalized[0]; }
	public static int getChildCumRecovered()		{ return cumRecovered[0]; }
	public static int getChildCumDeaths()			{ return cumDeaths[0]; }
	public static int getChildInsDailyCases()		{ return dailyCases[0]; }
	public static int getChildCumVaccined() 		{ return cumVaccined[0]; }
	public static int getChildCumVaccinedTwoDose() 	{ return cumVaccinedTwoDose[0]; }
	
	public static int getYoungCumExposed()			{ return cumExposed[1]; }
	public static int getYoungInsASXInfectious()	{ return insAsxInfectious[1]; }
	public static int getYoungInsSYMInfectious()	{ return insSymInfectious[1]; }
	public static int getYoungInsInfectious()		{ return insAsxInfectious[1] + insSymInfectious[1]; }
	public static int getYoungInsHospitalized()		{ return insHospitalized[1]; }
	public static int getYoungCumHospitalized()		{ return cumHospitalized[1]; }
	public static int getYoungCumRecovered()		{ return cumRecovered[1]; }
	public static int getYoungCumDeaths()			{ return cumDeaths[1]; }
	public static int getYoungInsDailyCases()		{ return dailyCases[1]; }
	public static int getYoungCumVaccined() 		{ return cumVaccined[1]; }
	public static int getYoungCumVaccinedTwoDose() 	{ return cumVaccinedTwoDose[1]; }
	
	public static int getAdultCumExposed()			{ return cumExposed[2]; }
	public static int getAdultInsASXInfectious() 	{ return insAsxInfectious[2]; }
	public static int getAdultInsSYMInfectious() 	{ return insSymInfectious[2]; }
	public static int getAdultInsInfectious() 		{ return insAsxInfectious[2] + insSymInfectious[2]; }
	public static int getAdultInsHospitalized() 	{ return insHospitalized[2]; }
	public static int getAdultCumHospitalized()		{ return cumHospitalized[2]; }
	public static int getAdultCumRecovered()		{ return cumRecovered[2]; }
	public static int getAdultCumDeaths()			{ return cumDeaths[2]; }
	public static int getAdultInsDailyCases()		{ return dailyCases[2]; }
	public static int getAdultCumVaccined() 		{ return cumVaccined[2]; }
	public static int getAdultCumVaccinedTwoDose() 	{ return cumVaccinedTwoDose[2]; }
	
	public static int getElderCumExposed()			{ return cumExposed[3]; }
	public static int getElderInsASXInfectious()	{ return insAsxInfectious[3]; }
	public static int getElderInsSYMInfectious()	{ return insSymInfectious[3]; }
	public static int getElderInsInfectious()		{ return insAsxInfectious[3] + insSymInfectious[3]; }
	public static int getElderInsHospitalized()		{ return insHospitalized[3]; }
	public static int getElderCumHospitalized()		{ return cumHospitalized[3]; }
	public static int getElderCumRecovered()		{ return cumRecovered[3]; }
	public static int getElderCumDeaths()			{ return cumDeaths[3]; }
	public static int getElderInsDailyCases()		{ return dailyCases[3]; }
	public static int getElderCumVaccined() 		{ return cumVaccined[3]; }
	public static int getElderCumVaccinedTwoDose() 	{ return cumVaccinedTwoDose[3]; }
	
	public static int getHigherCumExposed()			{ return cumExposed[4]; }
	public static int getHigherInsASXInfectious()	{ return insAsxInfectious[4]; }
	public static int getHigherInsSYMInfectious()	{ return insSymInfectious[4]; }
	public static int getHigherInsInfectious()		{ return insAsxInfectious[4] + insSymInfectious[4]; }
	public static int getHigherInsHospitalized()	{ return insHospitalized[4]; }
	public static int getHigherCumHospitalized()	{ return cumHospitalized[4]; }
	public static int getHigherCumRecovered()		{ return cumRecovered[4]; }
	public static int getHigherCumDeaths()			{ return cumDeaths[4]; }
	public static int getHigherInsDailyCases()		{ return dailyCases[4]; }
	public static int getHigherCumVaccined() 		{ return cumVaccined[4]; }
	public static int getHigherCumVaccinedTwoDose() { return cumVaccinedTwoDose[4]; }
	
	public static double getChildAVGInteractions()	{ return avgContextsSI[0] / contextsInteracting; }
	public static double getYoungAVGInteractions()	{ return avgContextsSI[1] / contextsInteracting; }
	public static double getAdultAVGInteractions()	{ return avgContextsSI[2] / contextsInteracting; }
	public static double getElderAVGInteractions()	{ return avgContextsSI[3] / contextsInteracting; }
	public static double getHigherAVGInteractions()	{ return avgContextsSI[4] / contextsInteracting; }
	
	public static int getDailyHomeTime()			{ return dailyStatesTicks[0] * 100 / totalDailyStatesTicks; }
	public static int getDailyWorkTime()			{ return dailyStatesTicks[1] * 100 / totalDailyStatesTicks; }
	public static int getDailyLeisureTime()			{ return dailyStatesTicks[2] * 100 / totalDailyStatesTicks; }
	public static int getDailyOtherTime()			{ return dailyStatesTicks[3] * 100 / totalDailyStatesTicks; }
	
	public static int getDailyHomeContagion()		{ return dailyStatesContagion[0]; }
	public static int getDailyWorkContagion()		{ return dailyStatesContagion[1]; }
	public static int getDailyLeisureContagion()	{ return dailyStatesContagion[2]; }
	public static int getDailyOtherContagion()		{ return dailyStatesContagion[3]; }
	
	public static int getCumExposedSchool()			{ return cumExposedSchool; }
	public static int getCumExposedPublicTransport(){ return cumExposedPublicTransport; }
	public static int getCumTicketTransportPublic()	{ return cumTicketTransportPublic; }
	public static int getInsSpacePublicTransport()	{ return insSpacePublicTransport; }
}
