package geocovid;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import au.com.bytecode.opencsv.CSVReader;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;

/**
 * Con los valores de temperatura promedio, calcula el beta en interiores, exteriores y fuera de contexto.
 */
public class Temperature {
	/** Numero de regiones: sur, centro y norte */
	private static final int REGIONS = 3;
	
	private static int currentYear;
	private static int dayOfTheYear;
	
	/** Temperatura exteriores */
	private static double[] odCurrentTemp = new double[REGIONS];
	/** Temperatura interiores */
	private static final double[][] temperature = new double[REGIONS][366];
	
	/** Beta diario para cada tipo de seccional */
	private static double[][] infectionRate = new double[REGIONS][2];
	/** Beta diario para cada tipo de seccional ventilada */	
	private static double[][] ventilatedInfectionRate = new double[REGIONS][2];
	/** Beta diario para espacios al aire libre */
	private static double[] outsideInfectionRate = new double[REGIONS];
	
	/** Beta diario para contagio por aerosol */
	private static double[] aerosolIR = new double[REGIONS];
	/** Beta diario para contagio por aerosol en espacios cerrados pero ventilados */
	private static double[] ventilatedAerosolIR = new double[REGIONS];
	/** Beta diario para contagio por aerosol en espacios al aire libre */
	private static double[] outsideAerosolIR = new double[REGIONS];
	
	/** Beta diario para contagio por estela o fomites */
	private static double[] fomiteIR = new double[REGIONS];
	/** Beta diario para contagio por estela o fomites en espacios al aire libre */
	private static double[] outsideFomiteIR = new double[REGIONS];
	
	/** Valor de contagio diario estando fuera del contexto */
	private static int[] oocContagionChance = new int[REGIONS];
	
	/**
	 * @param startYear ano de inicio (2020 o +)
	 * @param startDay dias desde fecha de inicio
	 */
	public Temperature(int startYear, int startDay) {
		currentYear = startYear;
		dayOfTheYear = startDay;
		initTemperature();
	}
	
	/**
	 * Lee de archivo csv los datos de temperatura media del ano actual.
	 */
	public static void initTemperature() {
		int year = currentYear;
		loadWeatherData(year); // Leer temp del ano inicio
		// Suma anos si la cantidad de dias > 365
		while (dayOfTheYear > 365) {
			dayOfTheYear -= 366;
			++currentYear;
		}
		// Si la cantidad de dias supera el ano de inicio, se vuelve a leer 
		if (year != currentYear)
			loadWeatherData(currentYear);
		// Setear las chances de infeccion del dia inicial
		updateInfectionChances();
	}
	
	@ScheduledMethod(start = 24, interval = 24, priority = ScheduleParameters.FIRST_PRIORITY)
	public static void updateDailyTemperature() {
		// Ultimo dia del ano, lee los datos del ano siguiente
		if (++dayOfTheYear == 366) {
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
		for (int r = 0; r < REGIONS; r++) {
			odCurrentTemp[r] = temperature[r][dayOfTheYear];
			
			infectionRate[r][0] = (45d - odCurrentTemp[r]) / 1.4d; // 24.64 12.42
			infectionRate[r][1] = infectionRate[r][0] * DataSet.INFECTION_RATE_SEC11_MOD;
			
			ventilatedInfectionRate[r][0] = infectionRate[r][0] * DataSet.DROPLET_VENTILATED_MOD;
			ventilatedInfectionRate[r][1] = infectionRate[r][1] * DataSet.DROPLET_VENTILATED_MOD;
			outsideInfectionRate[r] = infectionRate[r][0] * DataSet.DROPLET_OUTSIDE_MOD;
			
			aerosolIR[r] = infectionRate[r][0] * DataSet.AEROSOL_IR_MOD;
			ventilatedAerosolIR[r] = aerosolIR[r] * DataSet.AEROSOL_VENTILATED_MOD;
			outsideAerosolIR[r] = aerosolIR[r] * DataSet.AEROSOL_OUTSIDE_MOD;
			
			fomiteIR[r] = infectionRate[r][0] * DataSet.FOMITE_IR_MOD;
			outsideFomiteIR[r] = fomiteIR[r] * DataSet.FOMITE_OUTSIDE_MOD;
			
			oocContagionChance[r] = (int) (300000 - (DataSet.OOC_CONTAGION_VALUE * outsideInfectionRate[r])); // 36.5 22.5
		}
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
		int r = 0;
		try {
			reader = new CSVReader(new FileReader(file), ';');
			while ((nextLine = reader.readNext()) != null) {
				if (i >= dayFrom) {
					try {
						for (r = 0; r < REGIONS; r++)
							temperature[r][index] = Double.valueOf(nextLine[r]);
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
		String weatherFile = String.format("./data/%d-entrerios.csv", year);
		readCSV(weatherFile, 0, 0, 366);
	}
	
	/**
	 * Chance de contagio segun region parametros.
	 * @param region indice (0,1,2)
	 * @param sectoralType indice tipo (0,1)
	 * @param outdoor en una parcela al exterior
	 * @param ventilated en una parcela ventilada
	 * @return <b>double</b> beta (0 a 1)
	 */
	public static double getInfectionRate(int region, int sectoralType, boolean outdoor, boolean ventilated) {
		if (outdoor)
			return outsideInfectionRate[region];
		else if (ventilated)
			return ventilatedInfectionRate[region][sectoralType];
		return infectionRate[region][sectoralType];
	}
	
	/**
	 * Chance de contagio por aerosol segun region y parametros.
	 * @param region indice (0,1,2)
	 * @param outdoor en una parcela al exterior
	 * @param ventilated en una parcela ventilada
	 * @return <b>double</b> beta (0 a 1)
	 */
	public static double getAerosolInfectionRate(int region, boolean outdoor, boolean ventilated) {
		if (outdoor)
			return outsideAerosolIR[region];
		else if (ventilated)
			return ventilatedAerosolIR[region];
		return aerosolIR[region];
	}
	
	/**
	 * Chance de contagio por estela, segun region y parametros.
	 * @param region indice (0,1,2)
	 * @param outdoor en un parcela al exterior
	 * @return <b>double</b> beta (0 a 1)
	 */
	public static double getFomiteInfectionRate(int region, boolean outdoor) {
		if (outdoor)
			return outsideFomiteIR[region];
		return fomiteIR[region];
	}
	
	/**
	 * Chance de contagio fuera del contexto o parcelas, segun region.
	 * @param region indice (0,1,2)
	 * @return <b>int</b> chance contagio
	 */
	public static int getOOCContagionChance(int region) {
		return oocContagionChance[region];
	}
}
