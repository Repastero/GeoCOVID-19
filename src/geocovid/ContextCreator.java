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
	
	private Integer infectedAmount;
	private Integer outbreakStartTime;
	private Integer lockdownStartTime;
	private Integer simulationStartDay;
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias
	private int unemployedCount; // Contador de empleos faltantes
	private int unschooledCount; // Contador de bancos faltantes en escuelas
	
	/** Ver <b>corridas</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	static int corridas = 50;
	/** Ver <b>cantHumanos</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	static int localHumans = 6000;
	/** Ver <b>cantHumanosExtranjeros</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	static int foreignTravelerHumans = 1000;
	/** Ver <b>cantHumanosLocales</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	static int localTravelerHumans = 1000;
	
	static final int WEEKLY_TICKS = 12*7; // ticks que representan el tiempo de una semana
	static final int WEEKEND_TICKS = 12*2; // ticks que representan el tiempo que dura el fin de semana
	
	
	@Override
	public Context<Object> build(Context<Object> context) {
		RunEnvironment.getInstance().endAt(8760); // 300 dias maximo
		
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters params = ScheduleParameters.createOneTime(0d, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "startSimulation");
		params = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(params, this, "printSimulationDuration");

		// Crear la proyeccion para almacenar los agentes GIS (EPSG:4326).
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		this.geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);
		setBachParameters();
		
		// Schedule one shot para agregar infectados
		params = ScheduleParameters.createOneTime(outbreakStartTime, 0.9d);
		schedule.schedule(params, this, "infectRandos");
		
		// Schedule one shot para iniciar cierre de emergencia (tiempo de primer caso + tiempo inicio cuarentena
		params = ScheduleParameters.createOneTime(lockdownStartTime, 0.9d);
		schedule.schedule(params, this, "initiateLockdown");
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		setWeekendMovement();
		
		this.context = context;
		context.add(new InfeccionReport()); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartDay)); // Para calcular temperatura diaria, para estela
		
		loadPlacesShapefile();
		PlaceProperty.loadPlacesProperties(placesProperty);
		loadParcelsShapefile();
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
		for (int i = WEEKLY_TICKS - WEEKEND_TICKS; i < lockdownStartTime; i += WEEKLY_TICKS) {
			params = ScheduleParameters.createOneTime(i, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, "setHumansWeekendTMMC");
			endWKND = (i + WEEKEND_TICKS > lockdownStartTime) ? lockdownStartTime : i + WEEKEND_TICKS;
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
		
		System.out.println("Susceptibles: " + (localHumans + localTravelerHumans + foreignTravelerHumans));
		
		System.out.println("Infectados acumulados: " + InfeccionReport.getExposedCount());
		System.out.println("Infectados por estela: " + InfeccionReport.getExposedToCSCount());
    
		System.out.println("Recuperados: " + InfeccionReport.getRecoveredCount());
		System.out.println("Muertos: " + InfeccionReport.getDeathsCount());
		
		System.out.println("Infectados acumulados Ni�os: " + InfeccionReport.getYoungExposedCount());
		System.out.println("Recuperados Ni�os: " + InfeccionReport.getYoungRecoveredCount());
		System.out.println("Muertos Ni�os: " + InfeccionReport.getYoungDeathsCount());
		
		System.out.println("Infectados acumulados Jovenes: " + InfeccionReport.getYoungExposedCount());
		System.out.println("Recuperados Jovenes: " + InfeccionReport.getYoungRecoveredCount());
		System.out.println("Muertos Jovenes: " + InfeccionReport.getYoungDeathsCount());
		
		System.out.println("Infectados acumulados Adultos: " + InfeccionReport.getAdultExposedCount());
		System.out.println("Recuperados Adultos: " + InfeccionReport.getAdultRecoveredCount());
		System.out.println("Muertos Adultos: " + InfeccionReport.getAdultDeathsCount());
		
		System.out.println("Infectados acumulados Mayores: " + InfeccionReport.getElderExposedCount());
		System.out.println("Recuperados Mayores: " + InfeccionReport.getElderRecoveredCount());
		System.out.println("Muertos Mayores: " + InfeccionReport.getElderDeathsCount());
		
		System.out.println("Infectados acumulados Muy Mayores: " + InfeccionReport.getElderExposedCount());
		System.out.println("Recuperados Muy Mayores: " + InfeccionReport.getElderRecoveredCount());
		System.out.println("Muertos Muy Mayores: " + InfeccionReport.getElderDeathsCount());
		
		System.out.println("Dias de epidemia: " + (int) (RunEnvironment.getInstance().getCurrentSchedule().getTickCount() - outbreakStartTime) / 12);
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos y Mosquitos seteada en los parametros y los infecta.
	 */
	public void infectRandos() {
		Iterable<Object> collection = context.getRandomObjects(HumanAgent.class, infectedAmount);
		for (Iterator<Object> iterator = collection.iterator(); iterator.hasNext();) {
			HumanAgent humano = (HumanAgent) iterator.next();
			humano.setExposed(false);
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
		
		HumanAgent.infectedLocalTMMC[0] = MarkovChains.CHILD_WEEKEND_TMMC;
		HumanAgent.infectedLocalTMMC[1] = MarkovChains.YOUNG_WEEKEND_TMMC;
		HumanAgent.infectedLocalTMMC[2] = MarkovChains.ADULT_WEEKEND_TMMC;
		HumanAgent.infectedLocalTMMC[3] = MarkovChains.ELDER_WEEKEND_TMMC;
		HumanAgent.infectedLocalTMMC[4] = MarkovChains.HIGHER_WEEKEND_TMMC;
		
		HumanAgent.travelerTMMC			= MarkovChains.TRAVELER_WEEKEND_TMMC;
		HumanAgent.infectedTravelerTMMC	= MarkovChains.TRAVELER_WEEKEND_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.<p>
	 * Ver {@link #initHumans()}
	 */
	public void setHumansDefaultTMMC() {
		HumanAgent.localTMMC[0]			= MarkovChains.CHILD_PARANAS2_TMMC;
		HumanAgent.localTMMC[1]			= MarkovChains.YOUNG_PARANAS2_TMMC;
		HumanAgent.localTMMC[2]			= MarkovChains.ADULT_PARANAS2_TMMC;
		HumanAgent.localTMMC[3]			= MarkovChains.ELDER_PARANAS2_TMMC;
		HumanAgent.localTMMC[4]			= MarkovChains.HIGHER_PARANAS2_TMMC;

		HumanAgent.infectedLocalTMMC[0] = MarkovChains.CHILD_PARANAS2_TMMC;
		HumanAgent.infectedLocalTMMC[1] = MarkovChains.YOUNG_PARANAS2_TMMC;
		HumanAgent.infectedLocalTMMC[2] = MarkovChains.ADULT_PARANAS2_TMMC;
		HumanAgent.infectedLocalTMMC[3] = MarkovChains.ELDER_PARANAS2_TMMC;
		HumanAgent.infectedLocalTMMC[4] = MarkovChains.HIGHER_PARANAS2_TMMC;
		
		HumanAgent.travelerTMMC			= MarkovChains.TRAVELER_CONFINEMENT_TMMC;
		HumanAgent.infectedTravelerTMMC	= MarkovChains.TRAVELER_CONFINEMENT_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se van a utilizar al comenzar la cuarentena.
	 */
	public void initiateLockdown() {
		// Confinamiento con salida a compras.
		
//		HumanAgent.localTMMC[0] = MarkovChains.CHILD_HARD_CONFINEMENT_TMMC;
//		HumanAgent.localTMMC[1] = MarkovChains.YOUNG_HARD_CONFINEMENT_TMMC;
//		HumanAgent.localTMMC[2] = MarkovChains.ADULT_HARD_CONFINEMENT_TMMC;
//		HumanAgent.localTMMC[3] = MarkovChains.ELDER_HARD_CONFINEMENT_TMMC;
//		HumanAgent.localTMMC[4] = MarkovChains.HIGHER_HARD_CONFINEMENT_TMMC;
//		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_CONFINEMENT_TMMC;
		
		// Cuarentena en espa�a
		/*
		HumanAgent.localTMMC[0] = MarkovChains.YOUNG_SPAIN_TMMC;
		HumanAgent.localTMMC[1] = MarkovChains.YOUNG_SPAIN_TMMC;
		HumanAgent.localTMMC[2] = MarkovChains.ADULT_SPAIN_TMMC;
		HumanAgent.localTMMC[3] = MarkovChains.ELDER_SPAIN_TMMC;
		HumanAgent.localTMMC[4] = MarkovChains.HIGHER_SPAIN_TMMC;
		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_FULL_DAY_CONFINEMENT_TMMC;
		*/
		
		// Infectados sintomaticos
		/*
		HumanAgent.infectedLocalTMMC[0] = MarkovChains.INFECTED_CHILD_TMMC;
		HumanAgent.infectedLocalTMMC[1] = MarkovChains.INFECTED_YOUNG_TMMC;
		HumanAgent.infectedLocalTMMC[2] = MarkovChains.INFECTED_ADULT_TMMC;
		HumanAgent.infectedLocalTMMC[3] = MarkovChains.INFECTED_ELDER_TMMC;
		HumanAgent.infectedLocalTMMC[4] = MarkovChains.INFECTED_HIGHER_TMMC;
		HumanAgent.infectedTravelerTMMC = MarkovChains.INFECTED_TRAVELER_TMMC;
		*/
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		simulationStartDay	= (Integer) params.getValue("diaInicioSimulacion");
		outbreakStartTime	= (Integer) params.getValue("tiempoEntradaCaso");
		infectedAmount		= (Integer) params.getValue("cantidadInfectados");
		lockdownStartTime	= (Integer) params.getValue("tiempoInicioCuarentena");
		lockdownStartTime	+= outbreakStartTime; // Cuarentena comienza segun primer caso + inicio cuarentena
		
		localHumans				= (Integer) params.getValue("cantHumanos");
		foreignTravelerHumans	= (Integer) params.getValue("cantHumanosExtranjeros");
		localTravelerHumans		= (Integer) params.getValue("cantHumanosLocales");
		corridas				= (Integer) params.getValue("corridas");
	}

	private void loadPlacesShapefile() {
		String boundaryFilename = DataSet.SHP_FILE_PLACES;
		List<SimpleFeature> features = loadFeaturesFromShapefile(boundaryFilename);
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

	private void loadParcelsShapefile() {
		String boundaryFilename = DataSet.SHP_FILE_PARCELS;
		List<SimpleFeature> features = loadFeaturesFromShapefile(boundaryFilename);
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
			// Si tiene menos de 20 m2 se omite
			else if (coveredArea < 20) {
				continue;
			}
			// Chekea si la ID de parcela pertenece a la de un Place
			if (!placesType.containsKey(id)) {
				tempBuilding = new BuildingAgent(geom, id, blockId, type, area, coveredArea);
				homePlaces.add(tempBuilding);
				context.add(tempBuilding);
				geography.move(tempBuilding, geom);
			}
			else {
				placeType = placesType.remove(id);
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
		
		int localHumansCount = localHumans + localTravelerHumans;
		// Crear casas ficticias si es que faltan
		int extraHomes = (localHumansCount / DataSet.HOUSE_INHABITANTS_MEAN) - homePlaces.size();
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
			localTravelers[i] = (int) Math.ceil((localTravelerHumans * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[i]) / 100);
			locals[i] -= localTravelers[i];
			//
			foreignTravelers[i] = (int) Math.ceil((foreignTravelerHumans * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[i]) / 100);
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
	 * @param ageGroup
	 * @param home 
	 * @return WorkplaceAgent, BuildingAgent o null
	 */
	private BuildingAgent findAGWorkingPlace(int ageGroup, BuildingAgent home) {
		BuildingAgent workplace = null;
		//
		double occupation[] = DataSet.OCCUPATION_PER_AGE_GROUP[ageGroup];
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
        	workplace = findWorkingPlace(workPlaces);
        	if (workplace == null)
        		++unemployedCount;
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
