package geocovid;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import au.com.bytecode.opencsv.CSVReader;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * Los valores de temperatura exteriores, se importar desde un archivo csv.<p>
 * Los valores de temperatura interiores se calculan segun los valores minimos y maximos de la zona.<p>
 * La variacion de temperatura interior se hace para el hemisferio sur, mas especificamente Argentina.
 */
public class Temperature {
	private static int currentYear;
	private static int dayOfTheYear;
	/** Temperatura exteriores */
	private static double odCurrentTemp;
	/** Temperatura interiores */
	private static double idCurrentTemp;
	private static double idTempStep;
	private static final double[] temperature = new double[365];
	/** Beta diario para cada tipo de seccional */
	private static double[] infectionRate = new double[2];
	/** Beta diario para espacios al aire libre */
	private static double infectionRateOutside;
	/** Valor de contagio diario estando fuera del contexto */
	private static int oocContagionChance;
	
	/**
	 * Lee de archivo csv los datos de temperatura de exteriores del ano actual.<p>
	 * Calcula la temperatura actual en interiores y la variacion diaria.
	 * @param year ano de inicio (2020 o +)
	 * @param DOTY dia del ano (0 a 364)
	 */
	public Temperature(int year, int DOTY) {
		currentYear = (year < 2020) ? 2020 : year;
		dayOfTheYear = (DOTY > 364) ? 0 : DOTY;
		//
		loadWeatherData(currentYear);
		odCurrentTemp = temperature[dayOfTheYear];
		//
		idTempStep = (DataSet.INDOORS_MAX_TEMPERATURE - DataSet.INDOORS_MIN_TEMPERATURE) / 182d;
		
		// Setea la temperatura del dia inicial
		idCurrentTemp = DataSet.INDOORS_MIN_TEMPERATURE + (Math.abs(DOTY - 182) * idTempStep);
		// Setear las chances de infeccion del dia inicial
		updateInfectionChances();
	}
	
	@ScheduledMethod(start = 12d, interval = 12d, priority = ScheduleParameters.FIRST_PRIORITY)
	public static void updateDailyTemperature() {
		odCurrentTemp = temperature[++dayOfTheYear];
		if (dayOfTheYear < 182) // Primeros 6 meses
			idCurrentTemp -= idTempStep;
		else // Segundos 6 meses
			idCurrentTemp += idTempStep;
		// Ultimo dia del ano, lee los datos del ano siguiente
		if (dayOfTheYear == 364) {
			dayOfTheYear = 0;
			loadWeatherData(++currentYear);
		}
		// Setear las chances de infeccion del dia actual
		updateInfectionChances();
	}
	
	/**
	 * Calcula el beta base, segun temperatura exterior del dia, y con el valor obtenido calcula:<p>
	 * beta en seccional 11, en espacios abiertos y chance de contagio fuera del contexto. 
	 */
	private static void updateInfectionChances() {
		infectionRate[0] = (-13 * odCurrentTemp + 494d) / 13; // Formula Emanuel
		infectionRate[1] = infectionRate[0] * DataSet.INFECTION_RATE_SEC11_MOD;
		infectionRateOutside = infectionRate[0] * DataSet.INFECTION_RATE_OUTSIDE_MOD;
		oocContagionChance = (int) (150000 - (DataSet.OOC_CONTAGION_VALUE * infectionRateOutside));
	}
	
	private static void readCSV(String file, int index, int dayFrom, int dayTo) {
		boolean headerFound = false;
		CSVReader reader = null;
		String [] nextLine;
		int i = 0;
		try {
			reader = new CSVReader(new FileReader(file), ';');
			while ((nextLine = reader.readNext()) != null) {
				if (i >= dayFrom) {
					try {
						temperature[index] = Double.valueOf(nextLine[0]);
						index++;
					} catch (NumberFormatException e) {
						if (headerFound) {
							e.printStackTrace();
							return;
						}
						else {
							headerFound = true;
						}
					}
				}
				if (++i > dayTo)
					break;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) { }
		}
	}
	
	private static void loadWeatherData(int year) {
		String csvPath = "./data/";
		String weatherFile = String.format("%s%d.csv", csvPath, year);
		readCSV(weatherFile, 0, 0, 365);
	}
	
	public static double getInfectionRate(boolean outdoor) {
		if (outdoor)
			return infectionRateOutside;
		return infectionRate[0];
	}
	
	public static double getInfectionRate(boolean outdoor, int sectoralType) {
		if (outdoor)
			return infectionRateOutside;
		return infectionRate[sectoralType];
	}
	
	public static double getTemperature(boolean outdoor) {
		if (outdoor)
			return odCurrentTemp;
		else
			return idCurrentTemp;
	}
	
	public static int getOOCContagionChance() {
		return oocContagionChance;
	}
}
