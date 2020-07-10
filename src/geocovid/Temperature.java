package geocovid;

import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * Segun las temperaturas minimas y maximas, calcula los valores ficticios de cada dia del ano.<p>
 * La variacion de temperatura se hace para el hemisferio sur, mas especificamente Argentina.
 */
public class Temperature {
	private static int dayOfTheYear;
	private static double odCurrentTemp;
	private static double idCurrentTemp;
	private static double odTempStep;
	private static double idTempStep;
	
	/**
	 * Calcula la temperatura actual y la variacion diaria.
	 * @param DOTY dia del ano (0 a 364)
	 */
	public Temperature(int DOTY) {
		dayOfTheYear = (DOTY > 364) ? 0 : DOTY;
		odTempStep = (DataSet.OUTDOORS_MAX_TEMPERATURE - DataSet.OUTDOORS_MIN_TEMPERATURE) / 182d;
		idTempStep = (DataSet.INDOORS_MAX_TEMPERATURE - DataSet.INDOORS_MIN_TEMPERATURE) / 182d;
		odCurrentTemp = DataSet.OUTDOORS_MIN_TEMPERATURE + (Math.abs(DOTY - 182) * odTempStep);
		idCurrentTemp = DataSet.INDOORS_MIN_TEMPERATURE + (Math.abs(DOTY - 182) * idTempStep);
	}
	
	@ScheduledMethod(start = 12d, interval = 12d, priority = ScheduleParameters.FIRST_PRIORITY)
	public static void setDailyTemperature() {
		if (dayOfTheYear < 182) { // Primeros 6 meses
			odCurrentTemp = odCurrentTemp - odTempStep;
			idCurrentTemp = idCurrentTemp - idTempStep;
		}
		else { // Segundos 6 meses
			odCurrentTemp = odCurrentTemp + odTempStep;
			idCurrentTemp = idCurrentTemp + idTempStep;
		}
		if (++dayOfTheYear == 364) // Fin de año
			dayOfTheYear = 0;
	}
	
	public static double getTemperature(boolean outdoor) {
		if (outdoor)
			return odCurrentTemp;
		else
			return idCurrentTemp;
	}
}
