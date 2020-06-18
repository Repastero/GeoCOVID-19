package geocovid;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * El objetivo de esta clase es puramente informativo y mas que nada para llevar la cuenta de los Humanos infectados que no siempre estan en el Contexto.
 */
public class InfeccionReport {
	private static int exposedTotalCount;
	private static int asxInfectiousTotalCount;
	private static int symInfectiousTotalCount;
	private static int hospitalizedTotalCount;
	private static int recoveredTotalCount;
	private static int deathsTotalCount;
	//
	private static int[] exposedCount;
	private static int[] asxInfectiousCount;
	private static int[] symInfectiousCount;
	private static int[] hospitalizedCount;
	private static int[] recoveredCount;
	private static int[] deathsCount;
	//
	public InfeccionReport() {
		exposedTotalCount = 0;
		asxInfectiousTotalCount = 0;
		symInfectiousTotalCount = 0;
		hospitalizedTotalCount = 0;
		recoveredTotalCount = 0;
		deathsTotalCount = 0;
		
		exposedCount = new int[DataSet.AGE_GROUPS];
		asxInfectiousCount = new int[DataSet.AGE_GROUPS];
		symInfectiousCount = new int[DataSet.AGE_GROUPS];
		hospitalizedCount = new int[DataSet.AGE_GROUPS];
		recoveredCount = new int[DataSet.AGE_GROUPS];
		deathsCount = new int[DataSet.AGE_GROUPS];
	}
	
	/**
	 * Reinicio diariamente la cantidad de nuevos casos de Humanos infectados.
	 */
	@ScheduledMethod(start = 0d, interval = 12d, priority = 0.99d) //ScheduleParameters.FIRST_PRIORITY
	public void inicializadorDiario() {
		// Termina la simulacion si no hay forma de que se propague el virus y se recuperan todos los infectados
		if ((recoveredTotalCount != 0 || deathsTotalCount != 0) && ((deathsTotalCount + recoveredTotalCount) == exposedTotalCount))
			RunEnvironment.getInstance().endRun();
	}
	
	public static void modifyExposedCount(int agIndex, int value) {
		exposedTotalCount += value;
		exposedCount[agIndex] += value;
	}
	
	public static void modifyASXInfectiousCount(int agIndex, int value) {
		asxInfectiousTotalCount += value;
		asxInfectiousCount[agIndex] += value;
	}
	
	public static void modifySYMInfectiousCount(int agIndex, int value) {
		symInfectiousTotalCount += value;
		symInfectiousCount[agIndex] += value;
	}
	
	public static void modifyHospitalizedCount(int agIndex, int value) {
		hospitalizedTotalCount += value;
		hospitalizedCount[agIndex] += value;
	}
	
	public static void modifyRecoveredCount(int agIndex, int value) {
		recoveredTotalCount += value;
		recoveredCount[agIndex] += value;
	}
	
	public static void modifyDeathsCount(int agIndex, int value) {
		deathsTotalCount += value;
		deathsCount[agIndex] += value;
	}
	
	// Getters para usar en reportes de Repast Simphony
	public static int getExposedCount()				{ return exposedTotalCount; }
	public static int getASXInfectiousCount()		{ return asxInfectiousTotalCount; }
	public static int getSYMInfectiousCount()		{ return symInfectiousTotalCount; }
	public static int getHospitalizedCount()		{ return hospitalizedTotalCount; }
	public static int getRecoveredCount()			{ return recoveredTotalCount; }
	public static int getDeathsCount()				{ return deathsTotalCount; }
	
	public static int getChildExposedCount()		{ return exposedCount[0]; }
	public static int getChildASXInfectiousCount()	{ return asxInfectiousCount[0]; }
	public static int getChildSYMInfectiousCount()	{ return symInfectiousCount[0]; }
	public static int getChildHospitalizedCount()	{ return hospitalizedCount[0]; }
	public static int getChildRecoveredCount()		{ return recoveredCount[0]; }
	public static int getChildDeathsCount()			{ return deathsCount[0]; }
	
	public static int getYoungExposedCount()		{ return exposedCount[1]; }
	public static int getYoungASXInfectiousCount()	{ return asxInfectiousCount[1]; }
	public static int getYoungSYMInfectiousCount()	{ return symInfectiousCount[1]; }
	public static int getYoungHospitalizedCount()	{ return hospitalizedCount[1]; }
	public static int getYoungRecoveredCount()		{ return recoveredCount[1]; }
	public static int getYoungDeathsCount()			{ return deathsCount[1]; }
	
	public static int getAdultExposedCount()		{ return exposedCount[2]; }
	public static int getAdultASXInfectiousCount() 	{ return asxInfectiousCount[2]; }
	public static int getAdultSYMInfectiousCount() 	{ return symInfectiousCount[2]; }
	public static int getAdultHospitalizedCount() 	{ return hospitalizedCount[2]; }
	public static int getAdultRecoveredCount()		{ return recoveredCount[2]; }
	public static int getAdultDeathsCount()			{ return deathsCount[2]; }
	
	public static int getElderExposedCount()		{ return exposedCount[3]; }
	public static int getElderASXInfectiousCount()	{ return asxInfectiousCount[3]; }
	public static int getElderInfectedCount()		{ return symInfectiousCount[3]; }
	public static int getElderHospitalizedCount()	{ return hospitalizedCount[3]; }
	public static int getElderRecoveredCount()		{ return recoveredCount[3]; }
	public static int getElderDeathsCount()			{ return deathsCount[3]; }
	
	public static int getHigherExposedCount()		{ return exposedCount[4]; }
	public static int getHigherASXInfectiousCount()	{ return asxInfectiousCount[4]; }
	public static int getHigherInfectedCount()		{ return symInfectiousCount[4]; }
	public static int getHigherHospitalizedCount()	{ return hospitalizedCount[4]; }
	public static int getHigherRecoveredCount()		{ return recoveredCount[4]; }
	public static int getHigherDeathsCount()			{ return deathsCount[4]; }
	//
}
