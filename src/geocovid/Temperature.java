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
	/** Contador anual */
	private static int currentYear;
	/** Contador diario */
	private static int dayOfTheYear;
	
	/** Numero de regiones: sur, centro y norte */
	private static final int REGIONS = 3;
	
	/** Temperaturas diarias anuales (exterior) */
	private static final double[][] TEMPERATURES = new double[REGIONS][366];
	/** Temperatura del dia actual */
	private static double[] DAILY_TEMPERATURE = new double[REGIONS];
	
	/** Beta base inicial */
	private static final double BETA_BASE = 24.64d;
	/** Offset en porcentaje para variacion de beta base */
	private static final double [] BETA_VARIANT = new double [366];
	/** Beta diario para cada tipo de seccional */
	private static final double[][] INFECTION_RATE = new double[REGIONS][2];
	/** Beta diario para cada tipo de seccional ventilada */	
	private static final double[][] VEN_INFECTION_RATE = new double[REGIONS][2];
	/** Beta diario para espacios al aire libre */
	private static final double[] OD_INFECTION_RATE = new double[REGIONS];
	
	/** Beta diario para contagio por aerosol */
	private static final double[] AEROSOL_IR = new double[REGIONS];
	/** Beta diario para contagio por aerosol en espacios cerrados pero ventilados */
	private static final double[] VEN_AEROSOL_IR = new double[REGIONS];
	/** Beta diario para contagio por aerosol en espacios al aire libre */
	private static final double[] OD_AEROSOL_IR = new double[REGIONS];
	
	/** Beta diario para contagio por estela o fomites */
	private static final double[] FOMITE_IR = new double[REGIONS];
	/** Beta diario para contagio por estela o fomites en espacios al aire libre */
	private static final double[] OD_FOMITE_IR = new double[REGIONS];
	
	/** Valor de contagio diario estando fuera del contexto */
	private static final int[] OOC_CONTAGION_CHANCE = new int[REGIONS];
	
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
	 * beta en viviendas precarias, en espacios abiertos y chance de contagio fuera del contexto. 
	 */
	private static void updateInfectionChances() {
		for (int r = 0; r < REGIONS; r++) {
			DAILY_TEMPERATURE[r] = TEMPERATURES[r][dayOfTheYear];
			
			INFECTION_RATE[r][0] = BETA_VARIANT[dayOfTheYear] * BETA_BASE * ((DAILY_TEMPERATURE[r] / 2) - 22.36d) / -17.11d;
			INFECTION_RATE[r][1] = INFECTION_RATE[r][0] * DataSet.INFECTION_RATE_SEC11_MOD;
			
			VEN_INFECTION_RATE[r][0] = INFECTION_RATE[r][0] * DataSet.DROPLET_VENTILATED_MOD;
			VEN_INFECTION_RATE[r][1] = INFECTION_RATE[r][1] * DataSet.DROPLET_VENTILATED_MOD;
			OD_INFECTION_RATE[r] = INFECTION_RATE[r][0] * DataSet.DROPLET_OUTSIDE_MOD;
			
			AEROSOL_IR[r] = INFECTION_RATE[r][0] * DataSet.AEROSOL_IR_MOD;
			VEN_AEROSOL_IR[r] = AEROSOL_IR[r] * DataSet.AEROSOL_VENTILATED_MOD;
			OD_AEROSOL_IR[r] = AEROSOL_IR[r] * DataSet.AEROSOL_OUTSIDE_MOD;
			
			FOMITE_IR[r] = INFECTION_RATE[r][0] * DataSet.FOMITE_IR_MOD;
			OD_FOMITE_IR[r] = FOMITE_IR[r] * DataSet.FOMITE_OUTSIDE_MOD;
			
			OOC_CONTAGION_CHANCE[r] = -1; // reiniciar
		}
	}
	
	/**
	 * Lee el archivo de temperaturas medias y guarda los valores en array temperature. 
	 * @param file ruta de archivo csv
	 * @param index posicion inicial del array temperature
	 * @param dayFrom desde que dia leer
	 * @param dayTo hasta que dia leer
	 */
	private static void readTemperatureFile(String file, int index, int dayFrom, int dayTo) {
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
							TEMPERATURES[r][index] = Double.valueOf(nextLine[r]);
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
	
	/**
	 * Lee el archivo de variantes (offset en porcentaje en base al beta ) y guarda los valores en array variant. 
	 * @param file ruta de archivo csv
	 * @param index posicion inicial del array variant
	 * @param dayFrom desde que dia leer
	 * @param dayTo hasta que dia leer
	 */
	private static void readBetaVariantFile(String file, int index, int dayFrom, int dayTo) {
		boolean headerFound = false;
		CSVReader reader = null;
		String [] nextLine;
		int i = 0;
		try {
			reader = new CSVReader(new FileReader(file), ';');
			while ((nextLine = reader.readNext()) != null) {
				if (i >= dayFrom) {
					try {
 						BETA_VARIANT[index] = Double.valueOf(nextLine[0]);
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
		String weatherFile = String.format("./data/%d-temperature.csv", year);
		String variantFile = String.format("./data/%d-variant.csv", year);
		readTemperatureFile(weatherFile, 0, 0, 366);
		readBetaVariantFile(variantFile, 0, 0, 366);
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
			return OD_INFECTION_RATE[region];
		else if (ventilated)
			return VEN_INFECTION_RATE[region][sectoralType];
		return INFECTION_RATE[region][sectoralType];
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
			return OD_AEROSOL_IR[region];
		else if (ventilated)
			return VEN_AEROSOL_IR[region];
		return AEROSOL_IR[region];
	}
	
	/**
	 * Chance de contagio por estela, segun region y parametros.
	 * @param region indice (0,1,2)
	 * @param outdoor en un parcela al exterior
	 * @return <b>double</b> beta (0 a 1)
	 */
	public static double getFomiteInfectionRate(int region, boolean outdoor) {
		if (outdoor)
			return OD_FOMITE_IR[region];
		return FOMITE_IR[region];
	}
	
	/**
	 * Chance de contagio fuera del contexto o parcelas, segun region.
	 * @param region indice (0,1,2)
	 * @param ooc modificador contagio fuera de contexto
	 * @return <b>int</b> chance contagio
	 */
	public static int getOOCContagionChance(int region, int ooc) {
		if (OOC_CONTAGION_CHANCE[region] < 0)
			OOC_CONTAGION_CHANCE[region] = (int) (300000 - (ooc * OD_INFECTION_RATE[region]));
		return OOC_CONTAGION_CHANCE[region];
	}
}
