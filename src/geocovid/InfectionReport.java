package geocovid;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * El objetivo de esta clase es puramente informativo y mas que nada para llevar la cuenta de los Humanos infectados que no siempre estan en el Contexto.
 */
public class InfectionReport {
	private static int simulationMinDay;
	private static int deathLimit;
	//
	private static int daysCount;
	private static int cumExposedAll;		// Acumulado
	private static int cumExposedToCSAll;	// Acumulado
	private static int insAsxInfectiousAll;	// Instantaneo
	private static int insSymInfectiousAll;	// Instantaneo
	private static int insHospitalizedAll;	// Instantaneo
	private static int cumHospitalizedAll;	// Acumulado
	private static int cumRecoveredAll;		// Acumulado
	private static int cumDeathsAll;		// Acumulado
	//
	private static int[] cumExposed;		// Acumulado
	private static int[] insAsxInfectious;	// Instantaneo
	private static int[] insSymInfectious;	// Instantaneo
	private static int[] insHospitalized;	// Instantaneo
	private static int[] cumHospitalized;	// Acumulado
	private static int[] cumRecovered;		// Acumulado
	private static int[] cumDeaths;			// Acumulado
	//
	private static int[] sumOfSocialInteractions;
	private static int[] humansInteracting;
	private static double[] averageSocialInteractions;
	
	public InfectionReport(int minDay, int maxDeaths) {
		simulationMinDay = minDay;
		deathLimit = maxDeaths;
		
		daysCount = 0;
		cumExposedAll = 0;
		cumExposedToCSAll = 0;
		insAsxInfectiousAll = 0;
		insSymInfectiousAll = 0;
		insHospitalizedAll = 0;
		cumHospitalizedAll = 0;
		cumRecoveredAll = 0;
		cumDeathsAll = 0;
		
		cumExposed = new int[DataSet.AGE_GROUPS];
		insAsxInfectious = new int[DataSet.AGE_GROUPS];
		insSymInfectious = new int[DataSet.AGE_GROUPS];
		insHospitalized = new int[DataSet.AGE_GROUPS];
		cumHospitalized = new int[DataSet.AGE_GROUPS];
		cumRecovered = new int[DataSet.AGE_GROUPS];
		cumDeaths = new int[DataSet.AGE_GROUPS];
		
		sumOfSocialInteractions = new int[DataSet.AGE_GROUPS];
		humansInteracting = new int[DataSet.AGE_GROUPS];
		averageSocialInteractions = new double[DataSet.AGE_GROUPS];
	}
	
	@ScheduledMethod(start = 12d, interval = 12d, priority = 0.99d)
	public void checkPandemicEnd() {
		// Calcula las interacciones promedio y reinicia vectores
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			averageSocialInteractions[i] = sumOfSocialInteractions[i] / (double)humansInteracting[i];
			sumOfSocialInteractions[i] = 0;
			humansInteracting[i] = 0;
		}
		
		// Termina la simulacion si no hay forma de que se propague el virus y se recuperan todos los infectados
		if ((cumRecoveredAll != 0 || cumDeathsAll != 0) && ((cumDeathsAll + cumRecoveredAll) == cumExposedAll)) {
			System.out.println("Simulacion finalizada por fin de epidemia");
			RunEnvironment.getInstance().endRun();
		}
		// Termina la simulacion si se cumple el minimo de dias de simulacion y si se supera el limite te muertes (si existe)
		else if ((++daysCount >= simulationMinDay) && (deathLimit != 0 && deathLimit < cumDeathsAll)) {
			System.out.println("Simulacion finalizada por limite de muertes: "+deathLimit);
			RunEnvironment.getInstance().endRun();
		}
	}
	
	public static void updateSocialInteractions(int agIndex, int interactions) {
		// Acumulado diario
		sumOfSocialInteractions[agIndex] += interactions;
		++humansInteracting[agIndex];
	}
	
	public static void addExposedToCS() {
		// Acumulado
		++cumExposedToCSAll;
	}
	
	public static void addExposed(int agIndex) {
		// Acumulado
		++cumExposedAll;
		++cumExposed[agIndex];
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
	
	public static String getInfectedReport(int agIndex) {
		return String.format("%-12s: Infectados %5d | Hospitalizados %4d | Muertos %3d",
				DataSet.AGE_GROUP_LABELS[agIndex], getCumExposed(agIndex), getCumHospitalized(agIndex), getCumDeaths(agIndex));
	}
	
	// Getters para usar en reportes de Repast Simphony
	public static int getCumExposed()		{ return cumExposedAll; }
	public static int getCumExposedToCS()	{ return cumExposedToCSAll; }
	public static int getInsASXInfectious()	{ return insAsxInfectiousAll; }
	public static int getInsSYMInfectious()	{ return insSymInfectiousAll; }
	public static int getInsInfectious()	{ return insAsxInfectiousAll + insSymInfectiousAll; }
	public static int getInsHospitalized()	{ return insHospitalizedAll; }
	public static int getCumHospitalized()	{ return cumHospitalizedAll; }
	public static int getCumRecovered()		{ return cumRecoveredAll; }
	public static int getCumDeaths()		{ return cumDeathsAll; }
	
	public static int getCumExposed(int ai)			{ return cumExposed[ai]; }
	public static int getInsASXInfectious(int ai)	{ return insAsxInfectious[ai]; }
	public static int getInsSYMInfectious(int ai)	{ return insSymInfectious[ai]; }
	public static int getInsHospitalized(int ai)	{ return insHospitalized[ai]; }
	public static int getCumHospitalized(int ai)	{ return cumHospitalized[ai]; }
	public static int getCumRecovered(int ai)		{ return cumRecovered[ai]; }
	public static int getCumDeaths(int ai)			{ return cumDeaths[ai]; }
	
	public static int getChildCumExposed()			{ return cumExposed[0]; }
	public static int getChildInsASXInfectious()	{ return insAsxInfectious[0]; }
	public static int getChildInsSYMInfectious()	{ return insSymInfectious[0]; }
	public static int getChildInsHospitalized()		{ return insHospitalized[0]; }
	public static int getChildCumHospitalized()		{ return cumHospitalized[0]; }
	public static int getChildCumRecovered()		{ return cumRecovered[0]; }
	public static int getChildCumDeaths()			{ return cumDeaths[0]; }
	
	public static int getYoungCumExposed()			{ return cumExposed[1]; }
	public static int getYoungInsASXInfectious()	{ return insAsxInfectious[1]; }
	public static int getYoungInsSYMInfectious()	{ return insSymInfectious[1]; }
	public static int getYoungInsHospitalized()		{ return insHospitalized[1]; }
	public static int getYoungCumHospitalized()		{ return cumHospitalized[1]; }
	public static int getYoungCumRecovered()		{ return cumRecovered[1]; }
	public static int getYoungCumDeaths()			{ return cumDeaths[1]; }
	
	public static int getAdultCumExposed()			{ return cumExposed[2]; }
	public static int getAdultInsASXInfectious() 	{ return insAsxInfectious[2]; }
	public static int getAdultInsSYMInfectious() 	{ return insSymInfectious[2]; }
	public static int getAdultInsHospitalized() 	{ return insHospitalized[2]; }
	public static int getAdultCumHospitalized()		{ return cumHospitalized[2]; }
	public static int getAdultCumRecovered()		{ return cumRecovered[2]; }
	public static int getAdultCumDeaths()			{ return cumDeaths[2]; }
	
	public static int getElderCumExposed()			{ return cumExposed[3]; }
	public static int getElderInsASXInfectious()	{ return insAsxInfectious[3]; }
	public static int getElderInsSYMInfected()		{ return insSymInfectious[3]; }
	public static int getElderInsHospitalized()		{ return insHospitalized[3]; }
	public static int getElderCumHospitalized()		{ return cumHospitalized[3]; }
	public static int getElderCumRecovered()		{ return cumRecovered[3]; }
	public static int getElderCumDeaths()			{ return cumDeaths[3]; }
	
	public static int getHigherCumExposed()			{ return cumExposed[4]; }
	public static int getHigherInsASXInfectious()	{ return insAsxInfectious[4]; }
	public static int getHigherInsSYMInfected()		{ return insSymInfectious[4]; }
	public static int getHigherInsHospitalized()	{ return insHospitalized[4]; }
	public static int getHigherCumHospitalized()	{ return cumHospitalized[4]; }
	public static int getHigherCumRecovered()		{ return cumRecovered[4]; }
	public static int getHigherCumDeaths()			{ return cumDeaths[4]; }

	public static double getChildAVGInteractions()	{ return averageSocialInteractions[0]; }
	public static double getYoungAVGInteractions()	{ return averageSocialInteractions[1]; }
	public static double getAdultAVGInteractions()	{ return averageSocialInteractions[2]; }
	public static double getElderAVGInteractions()	{ return averageSocialInteractions[3]; }
	public static double getHigherAVGInteractions()	{ return averageSocialInteractions[4]; }
	//
}