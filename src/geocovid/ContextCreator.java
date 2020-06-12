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
	
	private Map<Long, String> placesType = new HashMap<>();
	
	private Context<Object> context;
	private Geography<Object> geography;
	
	private Integer infectedAmount;
	private Integer outbreakStartTime;
	private Integer lockdownStartTime;
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias

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
		params = ScheduleParameters.createOneTime(outbreakStartTime + lockdownStartTime, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "initiateLockdown");
		
		this.context = context;
		context.add(new InfeccionReport()); // Unicamente para la grafica en Repast Simphony
		
		loadPlacesShapefile();
		loadParcelsShapefile();
		initHumans();
				
		return context;
	}
	
	public void startSimulation() {
		simulationStartTime = System.currentTimeMillis();
	}
	
	public void printSimulationDuration() {
		final long simTime = System.currentTimeMillis() - simulationStartTime;
		System.out.println("Tiempo simulacion: " + (simTime / (double)(1000*60)) + " minutos");
		
		System.out.println("Susceptibles: " + (DataSet.localHumans + DataSet.localTravelerHumans)); // TODO los extranjeros tambien se suman a susceptibles ???
		System.out.println("Expuestos: " + InfeccionReport.getExposedCount());
		System.out.println("Recuperados: " + InfeccionReport.getRecoveredCount());
		System.out.println("Muertos: " + InfeccionReport.getDeathsCount());
		
		System.out.println("Expuestos Niños y Jovenes: " + InfeccionReport.getYoungExposedCount());
		System.out.println("Recuperados Niños y Jovenes: " + InfeccionReport.getYoungRecoveredCount());
		System.out.println("Muertos Niños y Jovenes: " + InfeccionReport.getYoungDeathsCount());
		
		System.out.println("Expuestos Adultos: " + InfeccionReport.getAdultExposedCount());
		System.out.println("Recuperados Adultos: " + InfeccionReport.getAdultRecoveredCount());
		System.out.println("Muertos Adultos: " + InfeccionReport.getAdultDeathsCount());
		
		System.out.println("Expuestos Mayores: " + InfeccionReport.getElderExposedCount());
		System.out.println("Recuperados Mayores: " + InfeccionReport.getElderRecoveredCount());
		System.out.println("Muertos Mayores: " + InfeccionReport.getElderDeathsCount());
		
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
	 * Asignar las matrices de markov que se van a utilizar al comenzar la cuarentena.
	 */
	public void initiateLockdown() {
		/*
		// Confinamiento con salida a compras.
		HumanAgent.youngTMMC = MarkovChains.YOUNG_HARD_CONFINEMENT_TMMC;
		HumanAgent.adultTMMC = MarkovChains.ADULT_HARD_CONFINEMENT_TMMC;
		HumanAgent.elderTMMC = MarkovChains.ELDER_HARD_CONFINEMENT_TMMC;
		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_CONFINEMENT_TMMC;
		HumanAgent.infectedYoungTMMC = MarkovChains.INFECTION_YOUNG_TMMC;
		HumanAgent.infectedAdultTMMC = MarkovChains.INFECTION_ADULT_TMMC;
		HumanAgent.infectedElderTMMC = MarkovChains.INFECTION_ELDER_TMMC;
		*/
		
		/*
		//cuarentena en españa
		HumanAgent.youngTMMC = MarkovChains.YOUNG_SPAIN_TMMC;
		HumanAgent.adultTMMC = MarkovChains.ADULT_SPAIN_TMMC;
		HumanAgent.elderTMMC = MarkovChains.ELDER_SPAIN_TMMC;
		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_CONFINEMENT_TMMC;
		HumanAgent.infectedYoungTMMC = MarkovChains.INFECTION_YOUNG_TMMC;
		HumanAgent.infectedAdultTMMC = MarkovChains.INFECTION_ADULT_TMMC;
		HumanAgent.infectedElderTMMC = MarkovChains.INFECTION_ELDER_TMMC;
		*/
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		infectedAmount		= (Integer) params.getValue("cantidadInfectados");
		outbreakStartTime	= (Integer) params.getValue("tiempoEntradaCaso");
		lockdownStartTime	= (Integer) params.getValue("tiempoInicioCuarentena");
		
		DataSet.localHumans				= (Integer) params.getValue("cantHumanos");
		DataSet.foreignTravelerHumans	= (Integer) params.getValue("cantHumanosExtranjeros");
		DataSet.localTravelerHumans		= (Integer) params.getValue("cantHumanosLocales");
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
		schoolPlaces.clear();
		workPlaces.clear();		
		
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
						if (placeType.contains("school") || placeType.contains("university"))
							schoolPlaces.add(tempWorkspace);
						else {
							workPlaces.add(tempWorkspace);
							
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
	
	private void createHuman(int ageGroup, BuildingAgent home, BuildingAgent work) {
		createHuman(ageGroup, home, work, ageGroup);
	}
	
	/**
	 * Crea Humanos y asigna a cada uno un lugar aleatorio en la grilla, como posicion del hogar.
	 */
	private void createHuman(int ageGroup, BuildingAgent home, BuildingAgent work, int tmmc) {
		int[] workPos = null;
		HumanAgent tempHuman;
		if (work instanceof WorkplaceAgent)
			workPos = ((WorkplaceAgent)work).getWorkPosition();
		if (home != null) // si tiene hogar es local, si no extranjero
			tempHuman = new HumanAgent(ageGroup, home, work, workPos, tmmc);
		else
			tempHuman = new ForeignHumanAgent(ageGroup, home, work, workPos, tmmc);
		context.add(tempHuman);
		tempHuman.setStartLocation();
	}

	/**
	 * Crea Humanos dependiendo la franja etaria.
	 * @see DataSet#HUMANS_PER_AGE_GROUP
	 * @see DataSet#localHumans
	 */
	private void initHumans() {
		// TODO este metodo tiene mas lineas que el Diego! ver de fraccionarlo
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		
		// Matrices de Markov por defecto
		HumanAgent.youngTMMC = MarkovChains.YOUNG_DEFAULT_TMMC;
		HumanAgent.adultTMMC = MarkovChains.ADULT_DEFAULT_TMMC;
		HumanAgent.elderTMMC = MarkovChains.ELDER_DEFAULT_TMMC;
		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_DEFAULT_TMMC;
		/*
		HumanAgent.youngTMMC = MarkovChains.YOUNG_CONFINEMENT_TMMC;
		HumanAgent.adultTMMC = MarkovChains.ADULT_CONFINEMENT_TMMC;
		HumanAgent.elderTMMC = MarkovChains.ELDER_CONFINEMENT_TMMC;
		HumanAgent.travelerTMMC = MarkovChains.TRAVELER_CONFINEMENT_TMMC;
		*/
		HumanAgent.infectedYoungTMMC = MarkovChains.YOUNG_DEFAULT_TMMC;
		HumanAgent.infectedAdultTMMC = MarkovChains.ADULT_DEFAULT_TMMC;
		HumanAgent.infectedElderTMMC = MarkovChains.ELDER_DEFAULT_TMMC;
		
		// Cntadores
		int localHumansCount = DataSet.localHumans + DataSet.localTravelerHumans;
		int unemployedCount = 0;
		//
		
		// Crear humanos que viven y trabajan/estudian en OV
		int age1Count = (int) ((localHumansCount * DataSet.HUMANS_PER_AGE_GROUP[0]) / 100);
		int age2Count = (int) ((localHumansCount * DataSet.HUMANS_PER_AGE_GROUP[1]) / 100);
		int age3Count = localHumansCount - (age1Count + age2Count);
		//
		
		// Crear humanos que viven dentro y trabajan o estudian fuera de OV
		// los de 3era edad no los tengo en cuenta, se suponen que esos no trabajan
		int age1LocalCount = (int) ((age1Count * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[0]) / 100);
		int age2LocalCount = (int) ((age2Count * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[1]) / 100);
		// resto estos de los locales
		age1Count -= age1LocalCount;
		age2Count -= age2LocalCount;
		//
		
		// Crear humanos que viven fuera y trabajan o estudian en OV
		// los de 3era edad no los tengo en cuenta, se suponen que esos no trabajan
		int age1ForeignCount = (int) ((DataSet.foreignTravelerHumans * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[0]) / 100);
		int age2ForeignCount = (int) ((DataSet.foreignTravelerHumans * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[1]) / 100);
		//
		
		//System.out.println("Locals: "+ age1Count + " - " + age2Count + " - " + age3Count);
		//System.out.println("Local Travelers:   "+ age1LocalCount + " - " + age2LocalCount);
		//System.out.println("Foreign Travelers: "+ age1ForeignCount + " - " + age2ForeignCount);
		
		BuildingAgent tempHome = null;
		BuildingAgent tempJob = null;
		
		// Crear casas ficticias si es que faltan
		int extraHomes = (localHumansCount / DataSet.HOUSE_INHABITANTS_MEAN) - homePlaces.size();
		if (extraHomes > 0)
			createFictitiousHomes(extraHomes);
		Uniform disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.size()-1);
		//
		
		// Este generador de randoms, se usa para catalogar algunos locales
		Uniform disUniformWork  = RandomHelper.createUniform(1, 100);
		
		int i;
		// Primero se crean los extranjeros, se asume que hay cupo de lugares de estudio y trabajo
		for (i = 0; i < age1ForeignCount; i++) {
			tempJob = findWorkingPlace(schoolPlaces);
			createHuman(0, null, tempJob, 3);
		}
		for (i = 0; i < age2ForeignCount; i++) {
			tempJob = findWorkingPlace(workPlaces);
			createHuman(1, null, tempJob, 3);
		}
		//
		
		// Segundo se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < age1LocalCount; i++) {
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());;
			createHuman(0, tempHome, null, 3);
		}
		for (i = 0; i < age2LocalCount; i++) {
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());;
			createHuman(1, tempHome, null, 3);
		}
		//
		
		// Por ultimo se crean los 100% locales
		for (i = 0; i < age1Count; i++) {
			// 1era franja etaria -> Escuela
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			tempJob = findWorkingPlace(schoolPlaces);
			if (tempJob == null) {
				// Si no estudia, trabaja
				tempJob = findWorkingPlace(workPlaces);
				if (tempJob == null) {
					// No encuentra trabajo
					tempJob = tempHome;
					++unemployedCount;
				}
			}
			createHuman(0, tempHome, tempJob);
		}
		for (i = 0; i < age2Count; i++) {
			// 2da franja etaria -> Trabajo
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			if (disUniformWork.nextInt() <= DataSet.LAZY_HUMANS_PERCENTAGE) {
				// Estos no trabaja o trabaja en su domicilio
				tempJob = tempHome;
			}
			else if (disUniformWork.nextInt() <= DataSet.OUTSIDE_WORKERS_PERCENTAGE) {
				// Trabaja al exterior
				tempJob = null;
			}
			else {
				// Trabajador
				tempJob = findWorkingPlace(workPlaces);
				if (tempJob == null) {
					tempJob = findWorkingPlace(schoolPlaces);
					if (tempJob == null) {
						// No encuentra trabajo
						++unemployedCount;
					}
				}
			}
			createHuman(1, tempHome, tempJob);
		}
		for (i = 0; i < age3Count; i++) {
			// 3era franja etaria -> Casa
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			createHuman(2, tempHome, tempHome);
		}
		//
		
		System.out.println("HUMANOS TOTALES: " + (localHumansCount + DataSet.foreignTravelerHumans));
		System.out.println("PUESTOS TRABAJO FALTANTES: " + unemployedCount);
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
