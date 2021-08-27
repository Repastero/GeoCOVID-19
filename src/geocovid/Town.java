package geocovid;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Contiene las caracteristicas habitacionales de cada ciudad y los dias de cambio de fases.
 */
public final class Town {
	/** Dias de delay en la entrada de infectados */
	public static int outbreakStartDelay = 0;
	/** Cantidad de infectados iniciales en cada municipio */
	public static int firstInfectedAmount = 1;
	
	/** Dias por defecto de los cambios de unidades de transporte publico */
	private final double[] PUBLIC_TRANSPORT_QUANTIFICATION	= {};
	
	/** Nombre del municipio a simular */
	public String townName;
	
	/** Tipo de ciudad */ 
	public int regionType;
	/** Indice de region de la ciudad (0 sur, 1 centro o 2 norte) */ 
	public int regionIndex;
	
	/** Cantidad de Humanos locales (no salen a trabajar) */
	public int localHumans;
	/** Cantidad de Humanos que trabajan afuera */
	public int localTravelerHumans;
	/** Cantidad de Humanos que viven afuera */
	public int foreignTravelerHumans;
	
	/** Tipo de seccional segun indice */
	public int[] sectoralsTypes; // este valor se podria tomar analizando el shapefile
	/** Porcentaje de la poblacion en cada seccional (segun cantidad de parcelas) */
	public double[] sectoralsPopulation; // este valor se podria tomar analizando el shapefile
	public int sectoralsCount;
	
	/** Dias desde el 01-01-2020 donde ocurre el cambio de fase<p>
	 * Para calcular dias entre fechas o sumar dias a fecha:<p>
	 * @see <a href="https://www.timeanddate.com/date/duration.html?d1=1&m1=1&y1=2020&">Dias entre fechas</a>
	 * @see <a href="https://www.timeanddate.com/date/dateadd.html?d1=1&m1=1&y1=2020&">Sumar dias a fecha</a>
	 */
	public int[] lockdownPhasesDays;	
	/** Dias desde el 01-01-2020 que ingresa el primer infectado */
	public int outbreakStartDay;
	
	/** Cantidad de colectivos maximos que presenta cada ciudad */
	public int publicTransportUnits;
	/** Si la ciudad tiene una temporada turistica */
	public boolean touristSeasonAllowed;
	
	/** Datos de vacunacion:
	 * String: nombre del departamento donde se realizó la vacunacion
	 * Integer: Dia de vacunacion
	 * Integer[]: Cantidad de vacunados de acuerdo al grupo etario 
	 *  */
	private Map<Integer, int[]> vaccinationTown;
	
	/** Datos de vacunacion de la segunda dosis:
	 * String: nombre del departamento donde se realizó la vacunacion
	 * Integer: Dia de vacunacion
	 * Integer[]: Cantidad de vacunados de acuerdo al grupo etario 
	 *  */
	private Map<Integer, int[]> vaccinationTownDose2;
	
	
	
	public Town(String name) {
		this.setTown(name);
	}
	
	private void setTownData(int regionTyp, int regionIdx, int locals, int travelers, int foreign, int[] secTypes, double[] secPop, int[] phasesDays, int obStartDay, int pubTransUnit, boolean tourSeasonAllowed) {
		regionType = regionTyp;
		regionIndex = regionIdx;
		//
		localHumans = locals;
		localTravelerHumans = travelers;
		foreignTravelerHumans = foreign;
		//
		sectoralsTypes = secTypes;
		sectoralsPopulation = secPop;
		sectoralsCount = secTypes.length;
		//
		lockdownPhasesDays = phasesDays;
		outbreakStartDay = obStartDay + outbreakStartDelay;
		//
		publicTransportUnits = pubTransUnit;
		touristSeasonAllowed = tourSeasonAllowed;
	}
	
	public int getLocalPopulation() {
		return localHumans + localTravelerHumans;
	}
	
	public String getPlacesPropertiesFilepath() {
		String type;
		switch (regionType) {
		default:
			type = "town";
			break;
		}
		return String.format("./data/%s-places-markov.csv", type);
	}
	
	public String getParcelsFilepath() {
		return String.format("./data/%s/parcels.shp", townName);
	}
	
	public String getPlacesFilepath() {
		return String.format("./data/%s/places.shp", townName);
	}
	
	/**
	 * Si cambia la ciudad, carga sus atributos.
	 * @param name nombre de ciudad
	 * @return <b>true</b> si se cambio de ciudad
	 */
	public boolean setTown(String name) {
		// Chequeo si es la misma
		if (name.equals(townName))
			return false;
		townName = name;
		//
		switch (townName) {
		case "town":
			setTownData(
				0,0,0,0,0,new int[] {},new double[] {},new int[] {},182,120,false);
			break;
		default:
			throw new InvalidParameterException("Ciudad erronea: " + townName);
		}
		return true;
	}
	
	/**
	 * Calcula la cantidad de unidades de PTs en funcionamiento por seccional.
	 * @param ptUnits total de unidades en funcionamiento 
	 * @return unidades de PublicTransport en funcionamiento por seccional
	 */
	public int[] getPTSectoralUnits(int ptUnits) {
		double fraction = ptUnits / 100d;
		int units;
		int[] sectoralsPTUnits = new int[sectoralsCount];
		for (int sI = 0; sI < sectoralsCount; sI++) {
			units = (int) Math.round(fraction * sectoralsPopulation[sI]);
			// Cada seccional tiene minimo 1
	      	if (units < 1)
	      		units = 1;
	      	//
	    	sectoralsPTUnits[sI] = units;
		}
		return sectoralsPTUnits;
	}
	
	/**
	 * Calcula la cantidad de PTs en funcionamiento segun fase y retorna cantidad por seccional.
	 * @param phase indice de fase
	 * @return unidades de PublicTransport por seccional segun fase
	 */
	public int[] getPTPhaseUnits(int phase) {
		// Si no hay transporte publico en town, retorna 0
		if (publicTransportUnits == 0)
			return new int[sectoralsCount];
		// Si el indice de fase supera los disponibles, retorna el maximo
		else if (phase > PUBLIC_TRANSPORT_QUANTIFICATION.length)
			return getPTSectoralUnits(publicTransportUnits);
		// Calcula la cantidad en funcionamiento segun fase
		else {
			int units = (int) Math.round(publicTransportUnits * PUBLIC_TRANSPORT_QUANTIFICATION[phase]);
			if (units > publicTransportUnits)
				units = publicTransportUnits;
			return getPTSectoralUnits(units);
		} 
	}

	/**
	 * Lee el archivo de la cantidad de vacunas por departamento de acuerdo a la cantidad de personas que se vacunaron por grupo etario
	 * @params town string de la ciudad.
	 * @params dose boolean que 
	 */
	public Map<Integer, int[]> loadVaccinePoblationAndDate(String town, boolean dose) {
		
		Map<Integer, int[]> Vaccination = new HashMap <Integer, int[]>();
		int amountEtaryGroup[] = new int [5];
		int date=0;
		boolean headerFound = false;
		CSVReader reader = null;
		String[] nextLine;
		int[] dataIndexes = {};
		String filePath = this.getVaccineFilepath(dose);
		try {
			reader = new CSVReader(new FileReader(filePath), ',');
			while ((nextLine = reader.readNext()) != null) {
				if (!headerFound) {
					dataIndexes = readHeader(nextLine);
					headerFound = true;
					continue;
				}
				try {
					date=Integer.valueOf(nextLine[dataIndexes[0]]);				
					amountEtaryGroup[0]= Integer.valueOf(nextLine[dataIndexes[1]]);
					amountEtaryGroup[1]= Integer.valueOf(nextLine[dataIndexes[2]]);
					amountEtaryGroup[2]= Integer.valueOf(nextLine[dataIndexes[3]]);
					amountEtaryGroup[3]= Integer.valueOf(nextLine[dataIndexes[4]]);
					amountEtaryGroup[4]= Integer.valueOf(nextLine[dataIndexes[5]]);
				} catch (NumberFormatException e) {
					System.out.println(String.join(", ", nextLine));
				}
				
				Vaccination.put(date, new int[amountEtaryGroup.length]);
				Vaccination.get(date)[0]=amountEtaryGroup[0];
				Vaccination.get(date)[1]=amountEtaryGroup[1];
				Vaccination.get(date)[2]=amountEtaryGroup[2];
				Vaccination.get(date)[3]=amountEtaryGroup[3];
				Vaccination.get(date)[4]=amountEtaryGroup[4];

			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) { }
		}
		
		return Vaccination;
	}
	

	
	
	private static int[] readHeader(String[] rows) throws Exception {
		String[] headers = {"phase","5-15","16-24","25-40","41-64","65+"};
		int[] indexes = new int[headers.length];
		
		int i,j;
		for (i = 0; i < headers.length; i++) {
			for (j = 0; j < rows.length; j++) {
				if (rows[j].equals(headers[i])) {
					indexes[i] = j;
					break;
				}
			}
			if (j == rows.length) {
				throw new Exception("Falta Header: " + headers[i]);
			}
		}
		return indexes;
	}
	
	/**
	 * se obtiene path donde se encuentra la cantidad de vacunas 
	 * @params 
	 * @return
	 */
	
	public String getVaccineFilepath(boolean dose) {
		if(dose)
			return String.format("./data/vacunaDose1/%s.csv", townName);
		else {
			return String.format("./data/vacunaDose2/%s.csv", townName);
		}
	}
		
	
	public Map<Integer, int[]> getVaccinationTown() {
		return vaccinationTown;
	}
	
	public void setVaccinationTown(Map<Integer, int[]> vaccinationTown) {
		this.vaccinationTown = vaccinationTown;
	}
	
	
	public Map<Integer, int[]> getVaccinationTownDoseTwo() {
		return vaccinationTownDose2;
	}
	
	public void setVaccinationTownDoseTwo(Map<Integer, int[]> vaccinationTown) {
		this.vaccinationTownDose2 = vaccinationTownDose2;
	}

	
}
