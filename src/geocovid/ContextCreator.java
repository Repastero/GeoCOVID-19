package geocovid;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Geometry;
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
	
	private Context<Object> context;
	private Geography<Object> geography;
	
	private Integer infectedAmount;
	private Integer outbreakStartTime;
	private Integer lockdownStartTime;
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias
	private int unemployedCount; // Contador de empleos faltantes
	private int unschooledCount; // Contador de bancos faltantes en escuelas
	
	static final int WEEKLY_TICKS = 12*7; // ticks que representan el tiempo de una semana
	static final int WEEKEND_TICKS = 12*2; // ticks que representan el tiempo que dura el fin de semana
	
	static final String SHP_FILE_PARCELS = "./data/ov-4326.shp";
	static final String SHP_FILE_PLACES = "./data/places-matched-4326.shp";
	
	@Override
	public Context<Object> build(Context<Object> context) {
		RunEnvironment.getInstance().endAt(3600); // 300 dias maximo
		
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
		params = ScheduleParameters.createOneTime(outbreakStartTime, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "infectRandos");
		
		// Schedule one shot para iniciar cierre de emergencia (tiempo de primer caso + tiempo inicio cuarentena
		params = ScheduleParameters.createOneTime(lockdownStartTime, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "initiateLockdown");
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		setWeekendMovement();
		
		this.context = context;
		context.add(new InfeccionReport()); // Unicamente para la grafica en Repast Simphony
		
		loadPlacesShapefile();
		loadParcelsShapefile();
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
		
		System.out.println("Susceptibles: " + (DataSet.localHumans + DataSet.localTravelerHumans + DataSet.foreignTravelerHumans));
		
		System.out.println("Infectados acumulados: " + InfeccionReport.getExposedCount());
		System.out.println("Infectados por estela: " + InfeccionReport.getExposedToCSCount());
    
		System.out.println("Recuperados: " + InfeccionReport.getRecoveredCount());
		System.out.println("Muertos: " + InfeccionReport.getDeathsCount());
		
		System.out.println("Infectados acumulados Niños: " + InfeccionReport.getYoungExposedCount());
		System.out.println("Recuperados Niños: " + InfeccionReport.getYoungRecoveredCount());
		System.out.println("Muertos Niños: " + InfeccionReport.getYoungDeathsCount());
		
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
			humano.setExposed();
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
		HumanAgent.localTMMC[0]			= MarkovChains.CHILD_DEFAULT_TMMC;
		HumanAgent.localTMMC[1]			= MarkovChains.YOUNG_DEFAULT_TMMC;
		HumanAgent.localTMMC[2]			= MarkovChains.ADULT_DEFAULT_TMMC;
		HumanAgent.localTMMC[3]			= MarkovChains.ELDER_DEFAULT_TMMC;
		HumanAgent.localTMMC[4]			= MarkovChains.HIGHER_DEFAULT_TMMC;

		HumanAgent.infectedLocalTMMC[0] = MarkovChains.CHILD_DEFAULT_TMMC;
		HumanAgent.infectedLocalTMMC[1] = MarkovChains.YOUNG_DEFAULT_TMMC;
		HumanAgent.infectedLocalTMMC[2] = MarkovChains.ADULT_DEFAULT_TMMC;
		HumanAgent.infectedLocalTMMC[3] = MarkovChains.ELDER_DEFAULT_TMMC;
		HumanAgent.infectedLocalTMMC[4] = MarkovChains.HIGHER_DEFAULT_TMMC;
		
		HumanAgent.travelerTMMC			= MarkovChains.TRAVELER_DEFAULT_TMMC;
		HumanAgent.infectedTravelerTMMC	= MarkovChains.TRAVELER_DEFAULT_TMMC;
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
		
		// Cuarentena en españa
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
		infectedAmount		= (Integer) params.getValue("cantidadInfectados");
		outbreakStartTime	= (Integer) params.getValue("tiempoEntradaCaso");
		lockdownStartTime	= outbreakStartTime + (Integer) params.getValue("tiempoInicioCuarentena");
		
		DataSet.localHumans				= (Integer) params.getValue("cantHumanos");
		DataSet.foreignTravelerHumans	= (Integer) params.getValue("cantHumanosExtranjeros");
		DataSet.localTravelerHumans		= (Integer) params.getValue("cantHumanosLocales");
		DataSet.corridas				= (Integer) params.getValue("corridas");
	}

	private void loadPlacesShapefile() {
		String boundaryFilename = SHP_FILE_PLACES;
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
				System.out.println("Invalid geometry: " + feature.getID());
			} 
			if (geom instanceof Point) {
				//id = (Long)feature.getAttribute("id");
				idParcel = (Long)feature.getAttribute("id_parcel");
				//name = (String) feature.getAttribute("name");
				type = (String) feature.getAttribute("type");
				//rating = (int) feature.getAttribute("ratings");
				
				placesType.put(idParcel, type);
			}
			else {
				System.out.println("Error creating agent for  " + geom);
			}
		}
		features.clear();
	}

	private void loadParcelsShapefile() {
		String boundaryFilename = SHP_FILE_PARCELS;
		List<SimpleFeature> features = loadFeaturesFromShapefile(boundaryFilename);
		homePlaces.clear();
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		BuildingAgent tempBuilding = null;
		WorkplaceAgent tempWorkspace = null;
		String placeType;
		maxParcelId = 0;
		
		// Armo la lista con los tipos de lugares de entretenimiento y otros, para filtrar los lugares de trabajo
		List<String> placesTypeList = Arrays.asList(ArrayUtils.addAll(BuildingManager.entertainmentTypes, BuildingManager.otherTypes));
		BuildingManager.initManager(context, geography);
		
		long id;
		long blockId;
		String type;
		int area;
		int coveredArea;
		
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.out.println("Invalid geometry: " + feature.getID());
				continue;
			}
			if (geom instanceof Point) {
				id = (Long)feature.getAttribute("id");
				blockId = (Long)feature.getAttribute("block");
				type = (String)feature.getAttribute("type");
				area = (int)feature.getAttribute("area");
				coveredArea = (int)feature.getAttribute("cover_area");
				
				if (id > maxParcelId)
					maxParcelId = id;
				
				if (area == 0) { // los terrenos sin construir los tomo igual
					area = 400;
					coveredArea = 320;
				}
				else if ((coveredArea == 0) && (!type.equals("park"))) { // los terrenos sin construir los tomo igual
					coveredArea = (int)(area * .8d);
				}
				
				if (!placesType.containsKey(id)) {
					tempBuilding = new BuildingAgent(geom, id, blockId, type, area, coveredArea);
					homePlaces.add(tempBuilding);
					context.add(tempBuilding);
					geography.move(tempBuilding, geom);
				}
				else {
					placeType = placesType.remove(id);
					if (placeType.contains("lodging")) {
						// Si es alojamiento, divido la superficie por 300 por casa
						for (int i = 0; i < (area / 300); i++) {
							tempBuilding = new BuildingAgent(geom, id, blockId, type, 300, 280);
							homePlaces.add(tempBuilding);
							context.add(tempBuilding);
							geography.move(tempBuilding, geom);
						}
					}
					else {
						tempWorkspace = new WorkplaceAgent(geom, id, blockId, type, area, coveredArea, placeType);
						if (placeType.contains("school"))
							schoolPlaces.add(tempWorkspace);
						else if (placeType.contains("university"))
							universityPlaces.add(tempWorkspace);
						else {
							workPlaces.add(tempWorkspace);
							// Si es lugar con atencion al publico, se agrega a la lista de actividades
							if (placesTypeList.contains(placeType))
								BuildingManager.addPlace(placeType, tempWorkspace);
						}
						context.add(tempWorkspace);
						geography.move(tempWorkspace, geom);
					}
				}
			}
			else {
				System.out.println("Error creating agent for  " + geom);
			}
		}
		features.clear();
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
			if (indexesCount >= 0) { // Si quedan contenedores inside
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

		int localHumansCount = DataSet.localHumans + DataSet.localTravelerHumans;
		// Crear casas ficticias si es que faltan
		int extraHomes = (localHumansCount / DataSet.HOUSE_INHABITANTS_MEAN) - homePlaces.size();
		if (extraHomes > 0)
			createFictitiousHomes(extraHomes);
		Uniform disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.size()-1);
		//
		
		// Este generador de randoms, se usa para catalogar algunos locales
		Uniform disUniformWork  = RandomHelper.createUniform(1, 100);
		
		int[] localHumans			= new int[DataSet.AGE_GROUPS];
		int[] localTravelerHumans	= new int[DataSet.AGE_GROUPS];
		int[] foreignTravelerHumans	= new int[DataSet.AGE_GROUPS];
		
		int i, j;
		// Crear humanos que viven y trabajan/estudian en OV
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			localHumans[i] = (int) Math.ceil((localHumansCount * DataSet.HUMANS_PER_AGE_GROUP[i]) / 100);
			localTravelerHumans[i] = (int) Math.ceil((localHumans[i] * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[i]) / 100);
			localHumans[i] -= localTravelerHumans[i];
			//
			foreignTravelerHumans[i] = (int) Math.ceil((DataSet.foreignTravelerHumans * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[i]) / 100);
		}
		//
		
		// DEBUG
		//for (i = 0; i < DataSet.AGE_GROUPS; i++)
		//	System.out.println(localHumans[i] + " | " + localTravelerHumans[i] + " | " + foreignTravelerHumans[i]);
		
		BuildingAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		
		// Primero se crean los extranjeros, se asume que hay cupo de lugares de estudio y trabajo
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < foreignTravelerHumans[i]; j++) {
				tempJob = findAGWorkingPlace(i);
				createHuman(i, null, tempJob);
			}
		}
		
		// Segundo se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < localTravelerHumans[i]; j++) {
				tempHome = homePlaces.get(disUniHomesIndex.nextInt());
				createHuman(i, tempHome, null);
			}
		}
		
		// Por ultimo se crean los 100% locales
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			// Si son CHILD siempre van a la escuela
			if (i == 0) {
				for (j = 0; j < localHumans[0]; j++) {
					tempHome = homePlaces.get(disUniHomesIndex.nextInt());
					tempJob = findAGWorkingPlace(0);
					createHuman(0, tempHome, tempJob);
				}
			}
			// Si son HIGHER se quedan en la casa, no trabajan
			else if (i == 4) {
				for (j = 0; j < localHumans[4]; j++) {
					tempHome = homePlaces.get(disUniHomesIndex.nextInt());
					createHuman(4, tempHome, tempHome);
				}
			}
			// Si son YOUNG, ADULT o ELDER pueden no trabajar/estudiar
			else {
				for (j = 0; j < localHumans[i]; j++) {
					tempHome = homePlaces.get(disUniHomesIndex.nextInt());
					// Si no trabaja o trabaja en su domicilio
					if (disUniformWork.nextInt() <= DataSet.LAZY_HUMANS_PERCENTAGE)
						tempJob = tempHome;
					// Trabaja al exterior
					else if (disUniformWork.nextInt() <= DataSet.OUTSIDE_WORKERS_PERCENTAGE)
						tempJob = null;
					// Trabaja normalmente
					else
						tempJob = findAGWorkingPlace(i);
					createHuman(i, tempHome, tempJob);
				}
			}
		}
		
		System.out.println("HUMANOS TOTALES: " + (localHumansCount + DataSet.foreignTravelerHumans));
		System.out.println("PUESTOS TRABAJO FALTANTES: " + unemployedCount);
		System.out.println("BANCOS EN ESCUELA FALTANTES: " + unschooledCount);
	}

	/**
	 * Busca lugar de trabajo/estudio en las distintas colecciones (escuela, facultad, trabajo), segun franja etaria y orden:<p>
	 * <ul>
	 * <li>0 -> schoolPlaces
	 * <li>1 -> universityPlaces, schoolPlaces, workPlaces
	 * <li>2 -> workPlaces, universityPlaces
	 * <li>3 -> workPlaces, universityPlaces
	 * <li>4 -> null
	 * </ul>
	 * @param ageGroup
	 * @return WorkplaceAgent o null
	 */
	private WorkplaceAgent findAGWorkingPlace(int ageGroup) {
		WorkplaceAgent workplace = null;
		switch (ageGroup) {
			case 0:
				workplace = findWorkingPlace(schoolPlaces);
				//
				if (workplace == null)
					++unschooledCount;
				break;
			case 1:
				workplace = findWorkingPlace(universityPlaces);
				if (workplace == null)
					workplace = findWorkingPlace(schoolPlaces);
					if (workplace == null)
						workplace = findWorkingPlace(workPlaces);
					//
				if (workplace == null)
					++unemployedCount;
				break;
			case 2:
			case 3:
				workplace = findWorkingPlace(workPlaces);
				if (workplace == null)
					workplace = findWorkingPlace(universityPlaces);
				//
				if (workplace == null)
					++unemployedCount;
				break;
			default:
				// Si es ageGroup 4, tendria que buscar el cajon
				break;
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
