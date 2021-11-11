package geocovid;

import java.net.JarURLConnection;
import java.text.SimpleDateFormat;

import geocovid.agents.BuildingAgent;
import geocovid.agents.HumanAgent;
import geocovid.contexts.ConcordContext;
import geocovid.contexts.GchuContext;
import geocovid.contexts.ParanaContext;
import geocovid.contexts.SubContext;
import repast.simphony.context.Context;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;

/**
 * Contexto principal de modelo.
 */
public class ContextCreator implements ContextBuilder<Object> {
	private ISchedule schedule; // Puntero
	private Geography<Object> geography; // Puntero
	
	// Parametros de simulacion
	private int simulationStartYear;
	private int simulationStartDay;
	private int simulationMaxTick;
	private int deathLimit;
	private int simulationRun;
	private int obStartDelayDays;
	private int weekendStartTick;
	
	/** Tiempo inicio de simulacion */
	private long simulationStartTime;
	
	/** Suma inicial de agentes humanos */
	private int humansCount;
	
	/** Lista de municipios a simular */
	static final String[] TOWN_NAMES = { // se puede variar la cantidad, pero no repetir
		"parana","gualeguay","diamante","nogoya","victoria","sansalvador",
		"gualeguaychu","uruguay","federacion","colon","islasdelibicuy",
		"concordia","lapaz","villaguay","federal","tala","feliciano"
	};
	
	public ContextCreator() {
		// Para corridas en batch imprime fecha de compilacion
		printJarVersion(this.getClass());
	}
	
	@Override
	public Context<Object> build(Context<Object> context) {
		simulationStartTime = System.currentTimeMillis();
		
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
		// Programa metodo para inicio de simulacion - para medir duracion
		ScheduleParameters params = ScheduleParameters.createOneTime(0d, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "startSimulation");
		// Programa metodo para fin de simulacion - para imprimir reporte final
		params = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "printSimulationDuration");
		
		// Crear la proyeccion para almacenar los agentes GIS (EPSG:4326).
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		this.geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);
		
		setBachParameters(); // Lee parametros de simulacion
		RunEnvironment.getInstance().endAt(simulationMaxTick);
		Town.outbreakStartDelay = obStartDelayDays;
		
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		BuildingAgent.initInfAndPDRadius(); // Crea posiciones del radio de infeccion

		context.add(new InfectionReport(simulationStartDay, deathLimit)); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartYear, simulationStartDay)); // Para leer temperatura diaria y calcular tasas de contagio
		
		SubContext.setGeography(geography); // Todos los SubContext usan la misma geografia
		createSubContexts(context); // Crear sub contexts por cada ciudad
		
		// BuildingManager usa el contexto principal y geografia
		BuildingManager.setMainContextAndGeography(context, geography);
		
		return context;
	}
	
	public void startSimulation() {
		simulationStartTime = System.currentTimeMillis();
	}
	
	/**
	 * Imprime en consola el reporte final de simulacion.
	 */
	public void printSimulationDuration() {
		final long simTime = System.currentTimeMillis() - simulationStartTime;
		final double cumExposed = InfectionReport.getCumExposed();
		System.out.printf("Simulacion Nº: %5d | Seed: %d | Tiempo: %.2f minutos%n",
				simulationRun, RandomHelper.getSeed(), (simTime / (double)(1000*60)));
		System.out.printf("Dias epidemia: %5d%n",
				(int) (schedule.getTickCount()) / 24 - obStartDelayDays);
		System.out.printf("Susceptibles: %6d | Infectados: %d%n",
				humansCount,
				InfectionReport.getCumExposed());
		if (cumExposed > 0) {
			System.out.printf("Inf. por aerosol: %.2f%% | Inf. por estela: %.2f%%%n",
					(InfectionReport.getCumExposedToAero() * 100 / cumExposed),
					(InfectionReport.getCumExposedToCS() * 100 / cumExposed));
			System.out.printf("Inf. en colectivos: %d - %.2f%% | Inf. en escuelas: %d - %.2f%%%n",
					(InfectionReport.getCumExposedPublicTransport()),
					(InfectionReport.getCumExposedPublicTransport() * 100 / cumExposed),
					(InfectionReport.getCumExposedSchool()),
					(InfectionReport.getCumExposedSchool() * 100 / cumExposed));
			System.out.printf("Recuperados:  %6d | Muertos: %d%n",
					InfectionReport.getCumRecovered(),
					InfectionReport.getCumDeaths());
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				System.out.println(InfectionReport.getInfectedReport(i));
			}
		}
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		// Ano simulacion, para calcular temperatura (queda fijo en 2020)
		simulationStartYear	= 2020;
		// Dia de inicio, desde la fecha 01/01/2020
		simulationStartDay	= ((Integer) params.getValue("diaInicioSimulacion")).intValue();
		// Dias maximo de simulacion - por mas que "diaMinimoSimulacion" sea mayor (0 = Infinito)
		simulationMaxTick	= ((Integer) params.getValue("diasSimulacion")).intValue() * 24;
		// Dias hasta primer sabado
		int daysFromSat = (4 + simulationStartDay) % 7; // el 4to dia de 2020 es sabado
		weekendStartTick = (daysFromSat == 0) ? 0 : (7 - daysFromSat) * 24;
		// Cantidad de muertos que debe superar para finalizar simulacion - ver "diaMinimoSimulacion" (0 = Infinito)
		deathLimit			= ((Integer) params.getValue("cantidadMuertosLimite")).intValue();
		// Dias de demora en entrada de infectados
		obStartDelayDays	= ((Integer) params.getValue("diasRetrasoEntradaCaso")).intValue();
		// Cantidad de corridas para hacer en batch
		simulationRun		= (Integer) params.getValue("corridas");
	}
	
	/**
	 * Crea sub contextos para cada ciudad, de acuerdo a la region que pertenecen.
	 * @param context contexto principal
	 */
	private void createSubContexts(Context<Object> context) {
		humansCount = 0;
		Town tempTown;
		SubContext subContext;
		SubContext lastContexts[] = new SubContext[3];
		for (int i = 0; i < TOWN_NAMES.length; i++) {
			tempTown = new Town(TOWN_NAMES[i]);
			humansCount += tempTown.localHumans;
			// Segun el indice de la region, el tipo de sub contexto
			if (tempTown.regionType == 0)
				subContext = new ParanaContext(tempTown);
			else if (tempTown.regionType == 1)
				subContext = new GchuContext(tempTown);
			else
				subContext = new ConcordContext(tempTown);
			//
			context.addSubContext(subContext);
			// Programa movilidad en humanos del sub contexto
			scheduleHumanInteractions(subContext);
			// Guardar el ultimo de cada tipo de sub contexto
			lastContexts[tempTown.regionType] = subContext;
		}
		
		// Programar el cambio de markovs en fines de semana
		for (SubContext cont : lastContexts) {
			if (cont != null) { // si se creo ciudad de esta region
				scheduleWeekendPeriod(cont);
			}
		}
	}
	
	/**
	 * Programa en schedule los metodos para que los humanos cambien de estado y calculen contactos.
	 * @param subContext sub contexto
	 */
	private void scheduleHumanInteractions(Object subContext) {
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(0, 1, 0.5);
		schedule.schedule(params, subContext, "switchHumanLocation");
		if (DataSet.COUNT_INTERACTIONS) {
			params = ScheduleParameters.createRepeating(24, 24, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, subContext, "computeAvgSocialInteractions");
		}
	}
	
	/**
	 * Programa en schedule los metodos para cambiar matrices de markov en los periodos de fines de semanas.
	 * @param subContext sub contexto
	 */
	private void scheduleWeekendPeriod(Object subContext) {
		ScheduleParameters params;
		params = ScheduleParameters.createRepeating(weekendStartTick, DataSet.WEEKLY_TICKS, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, subContext, "setHumansWeekendTMMC", true);
		params = ScheduleParameters.createRepeating(weekendStartTick + DataSet.WEEKEND_TICKS, DataSet.WEEKLY_TICKS, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, subContext, "setHumansWeekendTMMC", false);
	}
	
	/**
	 * Imprime la hora de compilacion del jar (si existe).
	 * @param cl clase actual
	 */
	private static void printJarVersion(Class<?> cl) {
	    try {
	        String rn = cl.getName().replace('.', '/') + ".class";
	        JarURLConnection j = (JarURLConnection) cl.getClassLoader().getResource(rn).openConnection();
	        long totalMS = j.getJarFile().getEntry("META-INF/MANIFEST.MF").getTime();
	        // Convierte de ms a formato fecha hora
			SimpleDateFormat sdFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			System.out.println("Fecha y hora de compilacion: " + sdFormat.format(totalMS));
	    } catch (Exception e) {
	    	// Si no es jar, no imprime hora de compilacion
	    }
	}
}
