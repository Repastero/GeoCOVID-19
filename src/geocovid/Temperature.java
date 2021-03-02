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
	private static final double[] temperature = new double[366];
	/** Beta diario para cada tipo de seccional */
	private static double[] infectionRate = new double[2];
	/** Beta diario para espacios al aire libre */
	private static double infectionRateOutside;
	/** Valor de contagio diario estando fuera del contexto */
	private static int oocContagionChance;
	
	/**
	 * Lee de archivo csv los datos de temperatura media del ano actual.<p>
	 * Calcula la tasa de contagio en interiores y exteriores.
	 * @param year ano de inicio (2020 o +)
	 * @param days dias desde fecha de inicio
	 */
	public Temperature(int year, int days) {
		currentYear = year;
		dayOfTheYear = days;
		loadWeatherData(year); // Leer temp del ano inicio
		// Suma anos si la cantidad de dias > 365
		while (dayOfTheYear > 365) {
			dayOfTheYear -= 366;
			++currentYear;
		}
		// Si la cantidad de dias supera el ano de inicio, se vuelve a leer 
		if (year != currentYear)
			loadWeatherData(currentYear);
		//
		odCurrentTemp = temperature[dayOfTheYear];
		// Setear las chances de infeccion del dia inicial
		updateInfectionChances();
	}
	
	@ScheduledMethod(start = 12d, interval = 12d, priority = ScheduleParameters.FIRST_PRIORITY)
	public static void updateDailyTemperature() {
		// Ultimo dia del ano, lee los datos del ano siguiente
		if (++dayOfTheYear == 366) {
			dayOfTheYear = 0;
			loadWeatherData(++currentYear);
		}
		//
		odCurrentTemp = temperature[dayOfTheYear];
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
		oocContagionChance = (int) (150000 - (Town.oocContagionValue * infectionRateOutside));
	}
	
	/**
	 * Lee el archivo de temperaturas medias y guarda los valores en array temperature. 
	 * @param file ruta de archivo csv
	 * @param index posicion inicial del array temperature
	 * @param dayFrom desde que dia leer
	 * @param dayTo hasta que dia leer
	 */
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
		String weatherFile = Town.getWeatherFilepath(year);
		readCSV(weatherFile, 0, 0, 366);
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
	
	public static int getOOCContagionChance() {
		return oocContagionChance;
	}
}
