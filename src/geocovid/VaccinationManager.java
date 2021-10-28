package geocovid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import geocovid.agents.HumanAgent;
import geocovid.contexts.SubContext;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.random.RandomHelper;

/**
 * Gestor de vacunacion.<p>
 * Implementa metodos para leer la agenda de vacunacion y la proporcion de tipos de vacunas.<p>
 * Programa metodos para vacunar y generar inmunidad a los HumanAgent.
 */
public class VaccinationManager {
	private SubContext context; // Puntero
	private ISchedule schedule; // Puntero
	private Map<Integer, int[]> vaccineScheduleFD; // Calendario primera dosis
	private Map<Integer, int[]> vaccineScheduleSD; // Calendario segunda dosis
	private int[][][] vaccineTypesRatio; // Proporcion de dosis de cada tipo aplicadas por grupo etario
	private Map<Integer, int[][]> vaccineCombinations; // Segundas dosis combinables y proporciones por grupo etario
	
	private List<List<Integer>> sdQueueList; // Lista de espera segunda dosis
	private Map<Integer, Integer> fdReceivedType; // Tipo de primera dosis aplicada a cada humano
	
	private int[] agIndexes;		// Indice maximo en contextHumans, de humanos por grupo etario
	private int[] unvacAGIndexes;	// Indice maximo en contextHumans, de humanos por grupo etario sin vacunar
	private int[] unvacIndexes;		// Indices ordenado de humanos en contextHumans, sin vacunar o con dosis vencidas
	
	public VaccinationManager(SubContext subContext) {
		this.context = subContext;
		this.vaccineScheduleFD = new TreeMap<>();
		this.vaccineScheduleSD = new TreeMap<>();
		this.vaccineTypesRatio = new int[2][DataSet.AGE_GROUPS][DataSet.VACCINES_TYPES];
		
		// Inicializar arrays de espera PD
		this.agIndexes = context.getLocalHumansAGIndex();
		this.unvacAGIndexes = agIndexes.clone(); // creo copia del array original
		this.unvacIndexes = IntStream.range(0, agIndexes[agIndexes.length - 1]).toArray();
		
		// Inicializar lista de espera SD y mapa de tipo de dosis administradas
		this.sdQueueList = new ArrayList<List<Integer>>(DataSet.AGE_GROUPS);
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			this.sdQueueList.add(new ArrayList<Integer>());
		}
		this.fdReceivedType = new HashMap<>();
	}
	
	/**
	 * Leer y cargar agenda de vacunacion y proporcion de tipos de vacunas.
	 * @param scheduleFile ruta archivo agenda de vacunacion
	 * @param typesFile ruta archivo % de tipos de vacunas
	 */
	public void loadScheduleFiles(String scheduleFile, String typesFile) {
		// Leer calendario de vacunacion por dia, con tipo y dosis por franja etaria
		loadVaccineSchedule(scheduleFile, vaccineScheduleFD, vaccineScheduleSD);
		// Leer proporcion de dosis por tipo y franja etaria
		loadVaccineTypes(typesFile, vaccineTypesRatio);
		
		// Programar vacunacion segun calendario
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
		scheduleVaccinationDays(vaccineScheduleFD, "applyFirstDoses");
		scheduleVaccinationDays(vaccineScheduleSD, "applySecondDoses");
		// Calcular proporcion vacunas combinables
		this.vaccineCombinations = getVaccineCombos(vaccineTypesRatio);
	}
	
	/**
	 * Programa los metodos de vacunacion segun agenda.
	 * @implNote Deberia reducirse el tick de inicio simulacion del tiempo de inmunidad
	 * @param vacSchedule Map de calendario
	 * @param methodName metodo segun primer o segunda dosis
	 */
	private void scheduleVaccinationDays(Map<Integer, int[]> vacSchedule, String methodName) {
		ScheduleParameters params;
		int vaccinationDay;
		for (Integer dayIdx : vacSchedule.keySet()) {
			vaccinationDay = dayIdx - InfectionReport.simulationStartDay;
			if (vaccinationDay > 0) // Futuro
				vaccinationDay *= 24;
			else // Pasado
				vaccinationDay = 0;
			params = ScheduleParameters.createOneTime(vaccinationDay, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, methodName, dayIdx);
		}
	}
	
	/**
	 * Selecciona un tipo de vacuna al azar, segun los % dados.
	 * @param ratios porcentajes tipos vacunas
	 * @param ratiosSum suma de <b>ratios</b>
	 * @return indice tipo vacuna
	 */
	private int getRandomVaccine(int[] ratios, int ratiosSum) {
		int r = RandomHelper.nextIntFromTo(1, ratiosSum);
		int i = 0;
        while (r > ratios[i]) {
        	r -= ratios[i];
        	++i;
        }
        return i;
	}
	
	/**
	 * Selecciona el tipo de vacuna correspondiente a la segunda dosis.
	 * @param firstDose indice tipo primera dosis
	 * @param ageGroup indice grupo etario
	 * @return indice tipo segunda dosis
	 */
	private int getSDVaccine(int firstDose, int ageGroup) {
		// Si hay dosis con que combinar, selecciona aleatoriamente una de ellas,
		// de lo contrario retorna el mismo tipo que la primer dosis.
		int[][] combos = vaccineCombinations.get(firstDose);
		if (combos == null)
			return firstDose;
		else
			return getRandomVaccine(combos[ageGroup], combos[ageGroup][combos[ageGroup].length - 1]);
	}
	
	/**
	 * Aplica las primeras dosis disponibles por grupos etarios, segun calendario de vacunacion.<p>
	 * Chequea que solo se vacune a humanos vivos y que no presenten sintomas.<p>
	 * El tipo de vacuna se selecciona aleatoriamente segun proporcion de cada tipo por grupo etario.<p>
	 * Si el tipo de vacuna aplicada al humano demanda segunda dosis, se lo agrega a la lista de espera.  
	 * @param day indice dia aplicacion
	 */
	public void applyFirstDoses(int day) {
		int[] dosesPerAG = vaccineScheduleFD.get(day);
		int rndFrom, rndTo, tempIdx;
		HumanAgent human;
		for (int ag = 0; ag < dosesPerAG.length; ag++) {
			if (dosesPerAG[ag] == 0) // no hay dosis para este AG
				continue;
			
			int doses = dosesPerAG[ag];
			InfectionReport.addFirstVaccineDoses(doses); // sumo el total
			rndFrom = (ag == 0) ? 0 : agIndexes[ag - 1];
			rndTo = unvacAGIndexes[ag] - 1;
			// Aplica vacunas mientras sobren dosis y tenga personas sin vacunar
			while (doses > 0 && rndFrom <= rndTo) {
				int r = RandomHelper.nextIntFromTo(rndFrom, rndTo);
				human = context.getHuman(unvacIndexes[r]);
				// Si fallecio, lo saco de la lista, sin vacunar
				if (human.isDead()) {
					unvacIndexes[r] = unvacIndexes[--unvacAGIndexes[ag]];
					--rndTo;
				}
				// Si tiene sintomas, no se le aplica la vacuna
				else if (human.isSymptomatic()) {
					// Lo muevo al principio de la lista
					tempIdx = unvacIndexes[rndFrom];
					unvacIndexes[rndFrom] = unvacIndexes[r];
					unvacIndexes[r] = tempIdx;
					++rndFrom;
				}
				else {
					int vacType = getRandomVaccine(vaccineTypesRatio[0][ag], 1000);
					// Si no fue expuesto o ya se recupero, se aplica vacuna normalmente
					// de lo contrario, gana inmunidad natural
					if (!human.wasExposed() || human.hasRecovered()) {
						applyVaccine(human, vacType, 0);
					}
					if (DataSet.VACCINES_DOSES[vacType] > 1) {
						// Agregar a lista de espera
						sdQueueList.get(ag).add(unvacIndexes[r]);
						fdReceivedType.put(unvacIndexes[r], vacType);
					}
					// Se reduce la cola de no vacunados
					unvacIndexes[r] = unvacIndexes[--unvacAGIndexes[ag]];
					--rndTo;
					--doses;
				}
			}
			//if (doses > 0) // sobran dosis
			//	System.out.println("SOBRAN VACUNAS FD "+ doses + " FRANJA "+ ag + " -> " + (rndTo - rndFrom));
		}
	}
	
	/**
	 * Aplica las segundas dosis disponibles por grupos etarios, segun calendario de vacunacion.<p>
	 * Chequea que solo se vacune a humanos vivos y que no presenten sintomas.<p>
	 * Se asigna el mismo tipo de vacuna que la primer dosis, salvo que sea combinable,
	 * en estos casos se selecciona aleatoriamente segun proporcion de cada tipo combinable por grupo etario.  
	 * @param day indice dia aplicacion
	 */
	public void applySecondDoses(int day) {
		int[] dosesPerAG = vaccineScheduleSD.get(day);
		HumanAgent human;
		for (int ag = 0; ag < dosesPerAG.length; ag++) {
			if (dosesPerAG[ag] == 0) // no hay dosis para este AG
				continue;
			
			int doses = dosesPerAG[ag];
			InfectionReport.addSecondVaccineDoses(doses); // sumo el total
			for (Iterator<Integer> it = sdQueueList.get(ag).iterator(); it.hasNext();) {
				Integer humanIdx = it.next();
				human = context.getHuman(humanIdx);
				// Si fallecio, lo saco de la lista, sin gastar dosis
				if (human.isDead()) {
					it.remove();
				}
				// Si tiene sintomas, se saltea la aplicacion
				else if (!human.isSymptomatic()) {
					int vacType = getSDVaccine(fdReceivedType.get(humanIdx), ag);
					// Si no fue expuesto o ya se recupero, se aplica vacuna normalmente
					// de lo contrario, gana inmunidad natural
					if (!human.wasExposed() || human.hasRecovered()) {
						applyVaccine(human, vacType, 1);
					}
					// Se reduce la cola de espera para 2nda dosis
					it.remove();
					fdReceivedType.remove(humanIdx);
					if (--doses == 0)
						break;
				}
			}
			//if (doses > 0) // sobran dosis
			//	System.out.println("SOBRAN VACUNAS SD "+ doses + " FRANJA "+ ag + " -> "+ sdQueueList.get(ag).size());
		}
	}
	
	/**
	 * Vacuna al humano con el tipo y dosis de vacuna dada.<p>
	 * Calcula y asigna nivel de inmunidad obtenido.
	 * @param human HumanAgent
	 * @param vacType tipo vacuna
	 * @param doseIdx indice dosis (0, 1)
	 */
	private void applyVaccine(HumanAgent human, int vacType, int doseIdx) {
		int efficacy = Utils.getStdNormalDeviate(DataSet.VACCINES_EFFICACY_MEAN[doseIdx][vacType], DataSet.VACCINES_EFFICACY_DEVIATION[doseIdx]);
		int immunityGained = DataSet.IMMUNITY_LVL_NONE;
		if (RandomHelper.nextIntFromTo(1, 100) < efficacy) {
			// Vacuna exitosa
			if (RandomHelper.nextIntFromTo(1, 100) < DataSet.VACCINE_IMMUNITY_CHANCE)
				// Gana inmunidad total
				immunityGained = DataSet.IMMUNITY_LVL_HIGH;
			else
				// Gana inmunidad parcial
				immunityGained = DataSet.IMMUNITY_LVL_MED;
		}
		else {
			// Vacuna no exitosa, gana inmunidad parcial limitada
			immunityGained = DataSet.IMMUNITY_LVL_LOW;
		}
		setDelayedImmunity(human, doseIdx, immunityGained);
	}
	
	/**
	 * Programa el cambio de nivel de inmunidad del humano vacunado.
	 * @param human HumanAgent
	 * @param doseIdx indice dosis (0, 1)
	 * @param immLevel nivel de inmunidad adquirida
	 */
	private void setDelayedImmunity(HumanAgent human, int doseIdx, int immLevel) {
		// Calcula los dias de delay para obtener inmunidad total
		int period = Utils.getStdNormalDeviate(DataSet.VACCINE_IMMUNITY_DELAY_MEAN[doseIdx], DataSet.VACCINE_IMMUNITY_DELAY_DEVIATION[doseIdx]);
		// Programa el inicio de inmunidad
		ScheduleParameters scheduleParams = ScheduleParameters.createOneTime(schedule.getTickCount() + period, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(scheduleParams, human, "setImmunityLevel", immLevel);
	}
	
	/**
	 * Agrega humano al array de no vacunados.
	 * @param humanIdx indice en array del contexto
	 * @param ageGroup grupo etario
	 */
	@SuppressWarnings("unused")
	private void addUnvacHumanIdx(int humanIdx, int ageGroup) {
		unvacIndexes[unvacAGIndexes[ageGroup]] = humanIdx;
		++unvacAGIndexes[ageGroup];
	}
	
	/**
	 * Lee el archivo de cantidad de dosis aplicadas, por fecha y grupo etario.
	 * @param filePath ruta archivo CSV
	 * @param firstDoseSch mapa primeras dosis
	 * @param secondDoseSch mapa segundas dosis
	 */
	private static void loadVaccineSchedule(String filePath, Map<Integer, int[]> firstDoseSch, Map<Integer, int[]> secondDoseSch) {
		final String[] colNames = {"day","dose","5-14","15-24","25-39","40-64","65+"};
		boolean headerFound = false;
		String[] nextLine;
		int[] dataIndexes = {};
		// Leo todas las lineas 
		List<String[]> fileLines = Utils.readCSVFile(filePath, ',', 0);
		if (fileLines == null) {
			System.err.println("Error al leer archivo de vacunacion: " + filePath);
			return;
		}
		for (Iterator<String[]> it = fileLines.iterator(); it.hasNext();) {
			nextLine = it.next();
			try {
				// Como tiene varias columnas es preferible leer el header
				if (!headerFound) {
					dataIndexes = Utils.readHeader(colNames, nextLine);
					headerFound = true;
					continue;
				}
				// No chequeo que los valores sean int y no blanks,
				// de eso se asegura el script que genera el archivo CSV.
				int dayIdx = Integer.valueOf(nextLine[dataIndexes[0]]); // Indice dia aplicacion
				int doseIdx = Integer.valueOf(nextLine[dataIndexes[1]]); // Indice tipo dosis
				int[] dosesPerAG = new int[DataSet.AGE_GROUPS];
				for (int i = 0; i < dosesPerAG.length; i++) {
					dosesPerAG[i] = Integer.valueOf(nextLine[dataIndexes[i + 2]]); // +2 por columnas indices
				}
				if (doseIdx == 0)
					firstDoseSch.put(dayIdx, dosesPerAG);
				else
					secondDoseSch.put(dayIdx, dosesPerAG);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Lee el archivo con proporcion de tipos de vacunas aplicadas, por dosis y grupo etario.
	 * @param filePath ruta archivo CSV
	 * @param vacTypes array con proporcion de tipos de vacuna
	 */
	private static void loadVaccineTypes(String filePath, int[][][] vacTypes) {
		final String[] colNames = {"type","dose","5-14","15-24","25-39","40-64","65+"};
		boolean headerFound = false;
		String[] nextLine;
		int[] dataIndexes = {};
		// Leo todas las lineas 
		List<String[]> fileLines = Utils.readCSVFile(filePath, ',', 0);
		if (fileLines == null) {
			System.err.println("Error al leer archivo de vacunacion: " + filePath);
			return;
		}
		for (Iterator<String[]> it = fileLines.iterator(); it.hasNext();) {
			nextLine = it.next();
			try {
				// Como tiene varias columnas es preferible leer el header
				if (!headerFound) {
					dataIndexes = Utils.readHeader(colNames, nextLine);
					headerFound = true;
					continue;
				}
				// No chequeo que los valores sean double y no blanks,
				// de eso se asegura el script que genera el archivo CSV.
				String vacType = nextLine[dataIndexes[0]]; // Tipo vacuna
				int vacIdx = Utils.findIndex(vacType, DataSet.VACCINES_TYPES_NAME);
				if (vacIdx == -1) {
					System.err.println("Error tipo vacuna desconocida: " + vacType);
					continue;
				}
				int doseIdx = Integer.valueOf(nextLine[dataIndexes[1]]); // Indice dosis (0 o 1)
				for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
					vacTypes[doseIdx][i][vacIdx] = Integer.valueOf(nextLine[dataIndexes[i + 2]]); // +2 por columna nombre e indice
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Calcula para las vacunas combinables, la chance de combinacion con otros tipos. 
	 * @param vacRatios porcentaje de vacunas aplicadas
	 * @return array con combinaciones por tipo de primera dosis
	 */
	private static Map<Integer, int[][]> getVaccineCombos(int[][][] vacRatios) {
		Map<Integer, int[][]> vacCombos = new HashMap<>();
		int sumIdx = DataSet.VACCINES_COMBO.length;
		for (int i = 0; i < DataSet.VACCINES_COMBO.length; i++) {
			int[][] tempChances = new int[DataSet.AGE_GROUPS][sumIdx + 1];
			boolean combinable = false;
			for (int j = 0; j < DataSet.VACCINES_COMBO[i].length; j++) {
				for (int ag = 0; ag < DataSet.AGE_GROUPS; ag++) {
					int chance = 0;
					if (i == j) { // mismo tipo
						chance = vacRatios[1][ag][j];
					}
					else if (DataSet.VACCINES_COMBO[i][j]) { // combinacion
						// Para aproximar cuantas dosis se combinaron, resto segundas de primeras 
						chance = vacRatios[1][ag][j] - vacRatios[0][ag][j];
						if (chance > 0) // si no hay dosis con que combinar, no lo agrego
							combinable = true;
						else
							chance = 0;
					}
					tempChances[ag][j] = chance;
					tempChances[ag][sumIdx] += chance;
				}
			}
			if (combinable) {
				vacCombos.put(i, tempChances);
			}
		}
		return vacCombos;
	}
}
