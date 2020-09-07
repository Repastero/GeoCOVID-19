package geocovid;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import cern.jet.random.Uniform;
import geocovid.agents.BuildingAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.ForeignHumanAgent;
import geocovid.agents.WorkplaceAgent;
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

public class ContextCreator implements ContextBuilder<Object> {
	private List<BuildingAgent> homePlaces = new ArrayList<BuildingAgent>();
	private List<WorkplaceAgent>workPlaces = new ArrayList<WorkplaceAgent>();
	private List<WorkplaceAgent>schoolPlaces = new ArrayList<WorkplaceAgent>();
	private List<WorkplaceAgent>universityPlaces = new ArrayList<WorkplaceAgent>();
	
	private Map<Long, String> placesType = new HashMap<>();
	private Map<String, PlaceProperty> placesProperty = new HashMap<>();
	
	private Context<Object> context;
	private Geography<Object> geography;
	
	private int simulationStartDay;
	private int simulationMaxTick;
	private int simulationMinDay;
	private int deathLimit;
	private int infectedAmount;
	private int outbreakStartTick;
	private int lockdownStartTick;
	
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias
	private int unemployedCount; // Contador de empleos faltantes
	private int unschooledCount; // Contador de bancos faltantes en escuelas
	
	/** Ver <b>corridas</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	static int corridas = 50;
	
	static final int WEEKLY_TICKS = 12*7; // ticks que representan el tiempo de una semana
	static final int WEEKEND_TICKS = 12*2; // ticks que representan el tiempo que dura el fin de semana
	
	@Override
	public Context<Object> build(Context<Object> context) {
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters params = ScheduleParameters.createOneTime(0d, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "startSimulation");
		params = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "printSimulationDuration");

		// Crear la proyeccion para almacenar los agentes GIS (EPSG:4326).
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		this.geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);
		setBachParameters();
		
		RunEnvironment.getInstance().endAt(simulationMaxTick);
		
		// Schedule one shot para agregar infectados
		params = ScheduleParameters.createOneTime(outbreakStartTick, 0.9d);
		schedule.schedule(params, this, "infectLocalRandos", infectedAmount);
		
		int[] phases = DataSet.LOCKDOWN_PHASES;
		int[] phasesStartDay = DataSet.LOCKDOWN_PHASES_DAYS;
		for (int i = 0; i < phases.length; i++) {
			params = ScheduleParameters.createOneTime(phasesStartDay[i] * 12, 0.9d);
			schedule.schedule(params, this, "initiateLockdown", phases[i], DataSet.SECTORAL);
		}
		
		// Contagia al azar, segun media y desvio std
		if (DataSet.SECTORAL == 0) {
			// Al tick 960 media/moda = 3 | al tick 1200 media/moda = 7
			params = ScheduleParameters.createNormalProbabilityRepeating(48, 12, 36, 12, 0.9d);
			schedule.schedule(params, this, "infectRandos", 1);
		}
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		//-setWeekendMovement();
		
		this.context = context;
		context.add(new InfeccionReport(simulationMinDay, deathLimit)); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartDay)); // Para calcular temperatura diaria, para estela
		
		loadPlacesShapefile();
		PlaceProperty.loadPlacesProperties(placesProperty);
		loadParcelsShapefile(DataSet.SECTORAL);
		
		BuildingManager.createActivitiesChances();
		
		initHumans();
		
		return context;
	}
	
	/**
	 * Programa en schedule los metodos para cambiar matrices de markov en los periodos de fines de semanas.<p>
	 * Cuando termina se asigna "setHumansDefaultTMMC"
	 */
	private void setWeekendMovement() {
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters params;
		int endWKND;
		for (int i = WEEKLY_TICKS - WEEKEND_TICKS; i < lockdownStartTick; i += WEEKLY_TICKS) {
			params = ScheduleParameters.createOneTime(i, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, "setHumansWeekendTMMC");
			endWKND = (i + WEEKEND_TICKS > lockdownStartTick) ? lockdownStartTick : i + WEEKEND_TICKS;
			params = ScheduleParameters.createOneTime(endWKND, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, "setHumansDefaultTMMC");
		}
	}
	
	public void startSimulation() {
		simulationStartTime = System.currentTimeMillis();
	}
	
	public void printSimulationDuration() {
		final long simTime = System.currentTimeMillis() - simulationStartTime;
		System.out.println("Tiempo simulacion: " + (simTime / (double)(1000*60)) + " minutos");
		
		System.out.println("Susceptibles: " + (DataSet.LOCAL_HUMANS[DataSet.SECTORAL] + DataSet.LOCAL_TRAVELER_HUMANS[DataSet.SECTORAL] + DataSet.FOREIGN_TRAVELER_HUMANS[DataSet.SECTORAL]));
		
		System.out.println("Infectados acumulados: " + InfeccionReport.getExposedCount());
		System.out.println("Infectados por estela: " + InfeccionReport.getExposedToCSCount());
    
		System.out.println("Recuperados: " + InfeccionReport.getRecoveredCount());
		System.out.println("Muertos: " + InfeccionReport.getDeathsCount());
		
		System.out.println("Infectados acumulados Niños: " + InfeccionReport.getChildExposedCount());
		System.out.println("Recuperados Niños: " + InfeccionReport.getChildRecoveredCount());
		System.out.println("Muertos Niños: " + InfeccionReport.getChildDeathsCount());
		
		System.out.println("Infectados acumulados Jovenes: " + InfeccionReport.getYoungExposedCount());
		System.out.println("Recuperados Jovenes: " + InfeccionReport.getYoungRecoveredCount());
		System.out.println("Muertos Jovenes: " + InfeccionReport.getYoungDeathsCount());
		
		System.out.println("Infectados acumulados Adultos: " + InfeccionReport.getAdultExposedCount());
		System.out.println("Recuperados Adultos: " + InfeccionReport.getAdultRecoveredCount());
		System.out.println("Muertos Adultos: " + InfeccionReport.getAdultDeathsCount());
		
		System.out.println("Infectados acumulados Mayores: " + InfeccionReport.getElderExposedCount());
		System.out.println("Recuperados Mayores: " + InfeccionReport.getElderRecoveredCount());
		System.out.println("Muertos Mayores: " + InfeccionReport.getElderDeathsCount());
		
		System.out.println("Infectados acumulados Muy Mayores: " + InfeccionReport.getHigherExposedCount());
		System.out.println("Recuperados Muy Mayores: " + InfeccionReport.getHigherRecoveredCount());
		System.out.println("Muertos Muy Mayores: " + InfeccionReport.getHigherDeathsCount());
		
		System.out.println("Dias de epidemia: " + (int) (RunEnvironment.getInstance().getCurrentSchedule().getTickCount() - outbreakStartTick) / 12);
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos locales seteada en los parametros y los infecta.
	 */
	public void infectLocalRandos(int amount) {
		int infected = 0;
		Iterable<Object> collection = context.getRandomObjects(HumanAgent.class, amount << 2); // Busco por 4, por las dudas
		for (Iterator<Object> iterator = collection.iterator(); iterator.hasNext();) {
			HumanAgent humano = (HumanAgent) iterator.next();
			if (!humano.isForeign()) {
				humano.setInfectious(true);
				if (++infected == amount)
					break;
			}
		}
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos seteada en los parametros y los infecta.
	 */
	public void infectRandos(int amount) {
		Iterable<Object> collection = context.getRandomObjects(HumanAgent.class, amount);
		for (Iterator<Object> iterator = collection.iterator(); iterator.hasNext();) {
			HumanAgent humano = (HumanAgent) iterator.next();
			if (!humano.exposed)
				humano.setInfectious(true);
		}
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan los fines de semana.
	 */
	public void setHumansWeekendTMMC() {
		HumanAgent.localTMMC[0]			= MarkovChains.CHILD_WEEKEND_TMMC;
		HumanAgent.localTMMC[1]			= MarkovChains.YOUNG_WEEKEND_TMMC;
		HumanAgent.localTMMC[2]			= MarkovChains.ADULT_WEEKEND_TMMC;
		HumanAgent.localTMMC[3]			= MarkovChains.ELDER_WEEKEND_TMMC;
		HumanAgent.localTMMC[4]			= MarkovChains.HIGHER_WEEKEND_TMMC;
		HumanAgent.travelerTMMC			= MarkovChains.TRAVELER_WEEKEND_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.<p>
	 * Ver {@link #initHumans()}
	 */
	@SuppressWarnings("unused")
	public void setHumansDefaultTMMC() {
		if (DataSet.SECTORAL == 0) {
			/*HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS2_JULIO_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS2_JULIO_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS2_JULIO_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS2_JULIO_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS2_JULIO_TMMC;
		*/
			HumanAgent.localTMMC[0]	= MarkovChains.CHILD_DEFAULTS2_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_DEFAULTS2_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_DEFAULTS2_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ELDER_DEFAULTS2_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_DEFAULTS2_TMMC;
		}
		
		else {
			/*
			HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS11_JULIO_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS11_JULIO_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS11_JULIO_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS11_JULIO_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS11_JULIO_TMMC;
			*/
			HumanAgent.localTMMC[0]	= MarkovChains.CHILD_DEFAULTS11_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_DEFAULTS11_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_DEFAULTS11_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ELDER_DEFAULTS11_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_DEFAULTS11_TMMC;
		
		}
		
		HumanAgent.travelerTMMC	= MarkovChains.TRAVELER_DEFAULTS2S11_TMMC;
		
		HumanAgent.infectedLocalTMMC[0] = MarkovChains.INFECTED_CHILD_TMMC;
		HumanAgent.infectedLocalTMMC[1] = MarkovChains.INFECTED_YOUNG_TMMC;
		HumanAgent.infectedLocalTMMC[2] = MarkovChains.INFECTED_ADULT_TMMC;
		HumanAgent.infectedLocalTMMC[3] = MarkovChains.INFECTED_ELDER_TMMC;
		HumanAgent.infectedLocalTMMC[4] = MarkovChains.INFECTED_HIGHER_TMMC;
		HumanAgent.infectedTravelerTMMC = MarkovChains.INFECTED_TRAVELER_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se van a utilizar al comenzar cada fase.
	 */
	public void initiateLockdown(int phase, int sectoral) {
		switch (phase) {
		case 0:
			// Reapertura progresiva (Fase 4)
			setHumansDefaultTMMC();
			BuildingManager.limitActivitiesCapacity(4d);
			DataSet.MASK_INFECTION_RATE_REDUCTION = 30;
			break;
		case 1:
			// Reapertura progresiva (Fase 4)
			if (sectoral == 0) {
				HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS2_JULIO_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS2_JULIO_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS2_JULIO_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS2_JULIO_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS2_JULIO_TMMC;
			}
			else {
				
				HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS11_JULIO_TMMC;
						/*
				HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS11_JULIO_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS11_JULIO_TMMC;
			*/
			}
			
				BuildingManager.limitActivitiesCapacity(4d);
				DataSet.MASK_INFECTION_RATE_REDUCTION = 30;
			break;
		case 2:
			// Nueva normalidad (Fase 5)
			if (sectoral == 0) {
				HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS2_AGOSTO_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS2_AGOSTO_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS2_AGOSTO_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS2_AGOSTO_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS2_AGOSTO_TMMC;
			}
			else {
				
				/*HumanAgent.localTMMC[0]	= MarkovChains.CHILD_DEFAULTS11_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_DEFAULTS11_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_DEFAULTS11_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_DEFAULTS11_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_DEFAULTS11_TMMC;
				*/
				HumanAgent.localTMMC[0]	= MarkovChains.CHILD_PARANAS11_AGOSTO_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_PARANAS11_AGOSTO_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_PARANAS11_AGOSTO_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ELDER_PARANAS11_AGOSTO_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_PARANAS11_AGOSTO_TMMC;
			}
			BuildingManager.limitActivitiesCapacity(3d);
			DataSet.MASK_INFECTION_RATE_REDUCTION = 30;
			break;
		case 3:
			// TODO Aca va fase 3 o trifasica
			break;
		default:
			break;
		}
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		// Dia calendario, para calcular temperatura (0 - 364)
		simulationStartDay	= ((Integer) params.getValue("diaInicioSimulacion")).intValue();
		// Dias maximo de simulacion - por mas que "diaMinimoSimulacion" sea mayor (0 = Infinito)
		simulationMaxTick	= ((Integer) params.getValue("diasSimulacion")).intValue() * 12;
		// Dias minimo de simulacion - por mas que supere "cantidadMuertosLimite" (0 ...)
		simulationMinDay	= ((Integer) params.getValue("diasMinimoSimulacion")).intValue();
		// Cantidad de muertos que debe superar para finalizar simulacion - ver "diaMinimoSimulacion" (0 = Infinito)
		deathLimit			= ((Integer) params.getValue("cantidadMuertosLimite")).intValue();
		// Dia de entrada de infectados
		outbreakStartTick	= ((Integer) params.getValue("diaEntradaCaso")).intValue() * 12;
		// Cantidad de infectados iniciales
		infectedAmount		= ((Integer) params.getValue("cantidadInfectados")).intValue();
		// Dias de delay desde que entran los infectados para iniciar cuarentena (0 = Ninguno)
		lockdownStartTick	= ((Integer) params.getValue("diaInicioCuarentena")).intValue() * 12;
		// Cuarentena comienza segun primer caso + inicio cuarentena
		lockdownStartTick	+= outbreakStartTick;
		
		corridas			= (Integer) params.getValue("corridas");
	}

	private void loadPlacesShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(DataSet.SHP_FILE_PLACES[DataSet.SECTORAL]);
		placesType.clear();
		
		//long id;
		long idParcel;
		//String name;
		String type;
		//int rating;
		
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Invalid geometry: " + feature.getID());
			} 
			if (geom instanceof Point) {
				//id = (Long)feature.getAttribute("id");
				idParcel = (Long)feature.getAttribute("id_parcel");
				//name = (String) feature.getAttribute("name");
				type = (String) feature.getAttribute("type");
				//rating = (int) feature.getAttribute("ratings");
				
				type = type.split("\\+")[0]; // TODO si no es lugar de trabajo, tendria que sumar % segun 2ndo type
				
				placesType.put(idParcel, type);
				if (!placesProperty.containsKey(type))
					placesProperty.put(type, new PlaceProperty(type));
			}
			else {
				System.err.println("Error creating agent for " + geom);
			}
		}
		features.clear();
	}

	private void loadParcelsShapefile(int k) {
		List<SimpleFeature> features = loadFeaturesFromShapefile(DataSet.SHP_FILE_PARCELS[DataSet.SECTORAL]);
		homePlaces.clear();
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		BuildingAgent tempBuilding = null;
		WorkplaceAgent tempWorkspace = null;
		String placeType;
		maxParcelId = 0;
		PlaceProperty placeProp;
		GeometryFactory geometryFactory = new GeometryFactory();
		
		BuildingManager.initManager(context, geography);
		
		double maxX = -180d;
		double minX = 180d;
		double maxY = -90d;
		double minY = 90d;
		double tempX, tempY;
		
		long id;
		long blockId;
		//String state;
		String type;
		int area;
		int coveredArea;
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			
			// Busca los valores min y max de X e Y
			// para crear el Extent o Boundary que incluye a todas las parcelas
			tempX = geom.getCoordinate().x;
			if (tempX > maxX)		maxX = tempX;
			else if (tempX < minX)	minX = tempX;
			tempY = geom.getCoordinate().y;
			if (tempY > maxY)		maxY = tempY;
			else if (tempY < minY)	minY = tempY;
			//
			
			if (geom == null || !geom.isValid()) {
				System.err.println("Invalid geometry: " + feature.getID());
				continue;
			}
			if (geom instanceof Point) {
				// Formato propio OV
				id = (Long)feature.getAttribute("id");
				blockId = (Long)feature.getAttribute("block");
				type = (String)feature.getAttribute("type");
				area = (int)feature.getAttribute("area");
				coveredArea = (int)feature.getAttribute("cover_area");
			}
			else { // Polygon
				// Formato Catastro Parana - modificado
				id = (Long)feature.getAttribute("id");
				blockId = (int)feature.getAttribute("block");
				//state = (String)feature.getAttribute("state"); // E H B
				type = (String)feature.getAttribute("type");
				area = (int) Math.round((double)feature.getAttribute("area"));
				coveredArea = (int) Math.round((double)feature.getAttribute("cover_area"));
				// Ignoro estos tipos de Buildings
				if (type.contains("BALDIO") || type.contains("BAULERA") || type.contains("COCHERA"))
					continue;
				// Convierto las geometrias Polygon a Point
				geom = geometryFactory.createPoint(geom.getCentroid().getCoordinate());
				
			}
			// Guarda la ultima ID de parcela, para crear ficticias
			if (id > maxParcelId)
				maxParcelId = id;
			// Los terrenos sin construir los tomo igual
			if (area == 0) {
				area = 100;
				coveredArea = 80;
			}
			// Chekea si la ID de parcela pertenece a la de un Place
			if (!placesType.containsKey(id)) { // es una vivienda entonces
				// Si tiene menos de 25 m2 cubiertos y area menor a 30 m2, se omite
				if (coveredArea < 25) {
					if (area < 30)
						continue;
					else
						coveredArea = (int) (area * 0.8);
				}
				// Si es un hogar con mas de los m2 promedio, se limita
				if (k == 0) {
					if (coveredArea > 100) // Sec2
						coveredArea = 100;
				}
				else if (coveredArea > 120) // Sec11
					coveredArea = 120;
				
				tempBuilding = new BuildingAgent(geom, id, blockId, type, area, coveredArea);
				homePlaces.add(tempBuilding);
				context.add(tempBuilding);
				geography.move(tempBuilding, geom);
			}
			else {
				placeType = placesType.remove(id);
				if (!WorkplaceAgent.CLOSED_PLACES.contains(placeType)) { // si no esta cerrado
					// Si tiene menos de 25 m2 cubiertos y no es un Place al aire libre, se incrementa
					if ((coveredArea < 25) && !WorkplaceAgent.OPEN_AIR_PLACES.contains(placeType)) {
						coveredArea = (int) (area * 0.8);
					}
					//
					placeProp = placesProperty.get(placeType);
					if (placeProp.getActivityType() == 0) { // lodging
						// Si es alojamiento, divido la superficie por 80 por casa
						for (int i = 0; i < (area / 80); i++) {
							tempBuilding = new BuildingAgent(geom, id, blockId, type, 80, 70);
							homePlaces.add(tempBuilding);
							context.add(tempBuilding);
							geography.move(tempBuilding, geom);
						}
					}
					else {
						tempWorkspace = new WorkplaceAgent(geom, id, blockId, placeType, area, coveredArea, placeType, placeProp.getWorkersPerPlace(), placeProp.getWorkersPerArea());
						if (placeProp.getActivityType() == 1) { // trabajo / estudio
							if (placeType.contains("school"))
								schoolPlaces.add(tempWorkspace);
							else if (placeType.contains("university"))
								universityPlaces.add(tempWorkspace);
							else {
								workPlaces.add(tempWorkspace);
							}
						}
						else { // ocio, otros
							workPlaces.add(tempWorkspace);
							// Si es lugar con atencion al publico, se agrega a la lista de actividades
							BuildingManager.addPlace(placeType, tempWorkspace, placeProp);
						}
						context.add(tempWorkspace);
						geography.move(tempWorkspace, geom);
					}
				}
			}
		}
		features.clear();
		
		BuildingManager.setBoundary(minX, maxX, minY, maxY);
	}
	
	/**
	 * Lee el archivo GIS y retorna sus items en una lista.
	 * @param filename  ruta del archivo shape
	 * @return lista con SimpleFeatures
	 */
	private List<SimpleFeature> loadFeaturesFromShapefile(String filename) {
		URL url = null;
		try {
			url = new File(filename).toURI().toURL();
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}
		//
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		ShapefileDataStore store = new ShapefileDataStore(url);
		SimpleFeatureIterator fiter = null;
		try {
			fiter = store.getFeatureSource().getFeatures().features();
			while (fiter.hasNext()) {
				features.add(fiter.next());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			// liberar recursos
			fiter.close();
			store.dispose();
		}
		//
		return features;
	}
	
	/**
	 * Selecciona hogares al azar y los duplica, incrementando su id.
	 * @param extraHomes
	 */
	private void createFictitiousHomes(int extraHomes) {
		BuildingAgent tempBuilding, tempHome;
		int[] ciIndexes = IntStream.range(0, homePlaces.size()).toArray();
		int indexesCount = homePlaces.size()-1;
		int randomIndex;
		
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				randomIndex = RandomHelper.nextIntFromTo(0, indexesCount);
				tempHome = homePlaces.get(ciIndexes[randomIndex]);
				ciIndexes[randomIndex] = ciIndexes[indexesCount--];
				//
				tempBuilding = new BuildingAgent(tempHome);
				tempBuilding.setId(++maxParcelId);
				homePlaces.add(tempBuilding);
				context.add(tempBuilding);
				geography.move(tempBuilding, tempBuilding.getGeometry());
			}
		}
	}
	
	/**
	 * Crea Humanos y asigna a cada uno un lugar aleatorio en la grilla, como posicion del hogar.
	 */
	private void createHuman(int ageGroup, BuildingAgent home, BuildingAgent work) {
		int[] workPos = null;
		HumanAgent tempHuman;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
		}
		// Si tiene hogar es local, si no extranjero
		if (home != null)
			tempHuman = new HumanAgent(ageGroup, home, work, workPos, false);
		else
			tempHuman = new ForeignHumanAgent(ageGroup, home, work, workPos);
		context.add(tempHuman);
		tempHuman.setStartLocation();
	}

	/**
	 * Crea Humanos dependiendo la franja etaria, lugar de trabajo y vivienda.
	 * @see DataSet#HUMANS_PER_AGE_GROUP
	 * @see DataSet#localHumans
	 */
	private void initHumans() {
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		setHumansDefaultTMMC();
		BuildingAgent.initInfectionRadius(); // Crea posiciones de infeccion en grilla
		
		int localHumansCount = DataSet.LOCAL_HUMANS[DataSet.SECTORAL] + DataSet.LOCAL_TRAVELER_HUMANS[DataSet.SECTORAL];
		// Crear casas ficticias si es que faltan
		int extraHomes = (localHumansCount / DataSet.HOUSE_INHABITANTS_MEAN[DataSet.SECTORAL]) - homePlaces.size();
		if (extraHomes > 0) {
			createFictitiousHomes(extraHomes);
			System.out.println("HOGARES PROMEDIO FALTANTES: "+extraHomes);
		}
		else
			System.out.println("HOGARES PROMEDIO SOBRANTES: "+extraHomes*-1);
		Uniform disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.size()-1);
		//
		
		int[] locals = new int[DataSet.AGE_GROUPS];
		int[] localTravelers = new int[DataSet.AGE_GROUPS];
		int[] foreignTravelers = new int[DataSet.AGE_GROUPS];
		
		int i, j;
		// Crear humanos que viven y trabajan/estudian en OV
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			locals[i] = (int) Math.ceil((localHumansCount * DataSet.HUMANS_PER_AGE_GROUP[i]) / 100);
			localTravelers[i] = (int) Math.ceil((DataSet.LOCAL_TRAVELER_HUMANS[DataSet.SECTORAL] * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[DataSet.SECTORAL][i]) / 100);
			locals[i] -= localTravelers[i];
			//
			foreignTravelers[i] = (int) Math.ceil((DataSet.FOREIGN_TRAVELER_HUMANS[DataSet.SECTORAL] * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[DataSet.SECTORAL][i]) / 100);
		}
		//
		
		// DEBUG
		//for (i = 0; i < DataSet.AGE_GROUPS; i++)
		//	System.out.println(locals[i] + " | " + localTravelers[i] + " | " + foreignTravelers[i]);
		
		BuildingAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		
		// Primero se crean los extranjeros, se asume que hay cupo de lugares de estudio y trabajo
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < foreignTravelers[i]; j++) {
				tempJob = findAGWorkingPlace(i, null);
				createHuman(i, null, tempJob);
			}
		}
		
		// Segundo se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < localTravelers[i]; j++) {
				tempHome = homePlaces.get(disUniHomesIndex.nextInt());
				createHuman(i, tempHome, null);
			}
		}
		
		// Por ultimo se crean los 100% locales
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < locals[i]; j++) {
				tempHome = homePlaces.get(disUniHomesIndex.nextInt());
				tempJob = findAGWorkingPlace(i, tempHome);
				createHuman(i, tempHome, tempJob);
			}
		}
		
		//System.out.println("HUMANOS TOTALES: " + (localHumansCount + foreignTravelerHumans));
		if (unemployedCount != 0)
			System.out.println("PUESTOS TRABAJO FALTANTES: " + unemployedCount);
		if (unschooledCount != 0)
			System.out.println("BANCOS EN ESCUELA FALTANTES: " + unschooledCount);
	}

	/**
	 * Busca lugar de trabajo/estudio en las distintas colecciones (escuela, facultad, trabajo), segun franja etaria y ocupacion<p>
	 * @param sectoral
	 * @param ageGroup
	 * @param home 
	 * @return WorkplaceAgent, BuildingAgent o null
	 */
	private BuildingAgent findAGWorkingPlace(int ageGroup, BuildingAgent home) {
		BuildingAgent workplace = null;
		//
		double occupation[] = DataSet.OCCUPATION_PER_AGE_GROUP[DataSet.SECTORAL][ageGroup];
		int r = RandomHelper.nextIntFromTo(1, 100);
		int i = 0;
        while (r > occupation[i]) {
        	r -= occupation[i];
        	++i;
        }
        
        if (i == 0) { // estudiante
        	if (ageGroup == 0 || (ageGroup == 1 && (occupation[i] - r < occupation[i]*.4d))) // 40% es primario
        		workplace = findWorkingPlace(schoolPlaces);
        	else
        		workplace = findWorkingPlace(universityPlaces);
        	if (workplace == null)
        		++unschooledCount;
        }
        else if (i == 1) { // trabajor
        	int wp = RandomHelper.nextIntFromTo(1, 100);
        	// Primero ver si tiene un trabajo convencional
        	if (wp <= DataSet.WORKING_FROM_HOME[DataSet.SECTORAL] + DataSet.WORKING_OUTDOORS[DataSet.SECTORAL]) {
        		// Si no, puede trabajar en la casa o al exterior
        		wp = RandomHelper.nextIntFromTo(1, DataSet.WORKING_FROM_HOME[DataSet.SECTORAL] + DataSet.WORKING_OUTDOORS[DataSet.SECTORAL]);
        		if (wp <= DataSet.WORKING_FROM_HOME[DataSet.SECTORAL])
        			workplace = home;
        		else
        			workplace = null;
        	}
        	else {
	        	workplace = findWorkingPlace(workPlaces);
	        	if (workplace == null)
	        		++unemployedCount;
        	}
        }
        else { // inactivo
        	workplace = home;
        }
		return workplace;
	}
	
	/**
	 * Busca y resta una posicion de trabajador en la lista de lugares.
	 * @param list
	 * @return WorkplaceAgent o null
	 */
	private WorkplaceAgent findWorkingPlace(List<WorkplaceAgent> list) {
		int index;
		WorkplaceAgent workplace = null;
		if (!list.isEmpty()) {
			index = RandomHelper.nextIntFromTo(0, list.size()-1);
			workplace = list.get(index);
			workplace.reduceVacancies();
			if (!workplace.vacancyAvailable())
				list.remove(index);
		}
		return workplace;
	}
}
