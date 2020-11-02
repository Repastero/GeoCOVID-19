package geocovid;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import au.com.bytecode.opencsv.CSVReader;


import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * Segun las temperaturas minimas y maximas, calcula los valores ficticios de cada dia del ano.<p>
 * La variacion de temperatura se hace para el hemisferio sur, mas especificamente Argentina.
 */
public class Temperature {
	private static int Years;
	private static int dayOfTheYear;
	private static double odCurrentTemp;
	private static double idCurrentTemp;
	private static double odTempStep;
	private static double idTempStep;
	private static final double[] temperature	= new double[365];
	
	/**
	 * Calcula la temperatura actual y la variacion diaria.
	 * @param DOTY dia del ano (0 a 364)
	 */
	

	public Temperature(int DOTY) {
		dayOfTheYear = (DOTY > 364) ? 0 : DOTY;
		//odTempStep = (DataSet.OUTDOORS_MAX_TEMPERATURE - DataSet.OUTDOORS_MIN_TEMPERATURE) / 182d;
		idTempStep = (DataSet.INDOORS_MAX_TEMPERATURE - DataSet.INDOORS_MIN_TEMPERATURE) / 182d;
		// Setea la temperatura del dia inicial
		//odCurrentTemp = DataSet.OUTDOORS_MIN_TEMPERATURE + (Math.abs(DOTY - 182) * odTempStep);
		idCurrentTemp = DataSet.INDOORS_MIN_TEMPERATURE + (Math.abs(DOTY - 182) * idTempStep);
		loadWeatherData(DataSet.anoSimulacion);
		Years=2020;
		
	}
	public  static double getTemperature(int dia)	{ return temperature[dia]; }

	@ScheduledMethod(start = 12d, interval = 12d, priority = ScheduleParameters.FIRST_PRIORITY)
	public static void setDailyTemperature() {
		if (dayOfTheYear < 182) { // Primeros 6 meses
			//odCurrentTemp -= odTempStep;
			idCurrentTemp -= idTempStep;
		}
		else { // Segundos 6 meses
			//odCurrentTemp += odTempStep;
			idCurrentTemp += idTempStep;
		}
		if (++dayOfTheYear == 364) {// Fin de año
			dayOfTheYear = 0;
			
			Years = Years + 1; 
			loadWeatherData(Years);
		
		}
		odCurrentTemp = getTemperature(dayOfTheYear);   
		//System.out.println(odCurrentTemp);
	}
	private static void readCSV(String file, int index, int dayFrom, int dayTo) {
		//String[] header = {"temperature"};
		boolean headerFound = false;
		CSVReader reader = null;
		String [] nextLine;
		int i = 0;
		try {
			reader = new CSVReader(new FileReader(file), ';');
			while ((nextLine = reader.readNext()) != null) {
				
				if (nextLine.length < 1) {
					System.out.println("Faltan datos");
					return;
				}
				
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
		//temperature;humidity;precipitation;windSpeed
		Years= year;
		String csvPath = "./data/";
		String firstYearFile = String.format("%s%d.csv", csvPath, year);
		//tring secondYearFile = String.format("%s%d.csv", csvPath, year+1);
		//String thirdsecondYearFile = String.format("%s%d.csv", csvPath, year+2);
		
		if (year % 4 == 0) // bisiesto
			readCSV(firstYearFile, 0, 0, 365);
		else
			readCSV(firstYearFile, 0,  0, 365);
		
	}

	
	public static double getTemperature(boolean outdoor) {
		if (outdoor)
			return odCurrentTemp;
		else
			return idCurrentTemp;
	}
}
