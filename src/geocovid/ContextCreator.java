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
	
	private Integer cantidadInfectados;
	private Integer tiempoEntradaCaso;
	private Integer tiempoInicioCuarentena;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias

	static final String SHP_FILE_PARCELS = "./data/ov-4326.shp";
	static final String SHP_FILE_PLACES = "./data/places-matched-4326.shp";
	
	@Override
	public Context<Object> build(Context<Object> context) {
		RunEnvironment.getInstance().endAt(3600); // 300 dias maximo
		
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters stopParams = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
		schedule.schedule(stopParams, this, "printSimulationDuration");

		// Crear la proyeccion para almacenar los agentes GIS (EPSG:4326).
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		this.geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("Geography", context, geoParams);
		setBachParameters();
		
		// Schedule one shot para agregar infectados
		ScheduleParameters params = ScheduleParameters.createOneTime(tiempoEntradaCaso, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params , this , "infectRandos");
		
		// Schedule one shot para iniciar cierre de emergencia (tiempo de primer caso + tiempo inicio cuarentena
		params = ScheduleParameters.createOneTime(tiempoEntradaCaso + tiempoInicioCuarentena, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params , this , "initiateLockdown");
		
		this.context = context;
		context.add(new InfeccionReport()); // Unicamente para la grafica en Repast Simphony
		
		loadPlacesShapefile();
		loadParcelsShapefile();
		initHumans();
				
		return context;
	}
	
	public void printSimulationDuration() {
		//-final long simTime = System.currentTimeMillis() - simulationStartTime;
		//-System.out.println("Tiempo simulacion: " + (simTime / (double)(1000*60)) + " minutos");
		System.out.println("Susceptibles: " + DataSet.cantHumanosFijos);
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
		
		System.out.println("Dias de epidemia: " + (int) (RunEnvironment.getInstance().getCurrentSchedule().getTickCount() - tiempoEntradaCaso) / 12);
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos y Mosquitos seteada en los parametros y los infecta.
	 */
	public void infectRandos() {
		Iterable<Object> collection = context.getRandomObjects(HumanAgent.class, cantidadInfectados);
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
		cantidadInfectados					= (Integer) params.getValue("cantidadInfectados");
		tiempoEntradaCaso					= (Integer) params.getValue("tiempoEntradaCaso");
		tiempoInicioCuarentena				= (Integer) params.getValue("tiempoInicioCuarentena");
		
		DataSet.cantHumanosFijos			= (Integer) params.getValue("cantHumanos");
		DataSet.cantHumanosExtranjeros		= (Integer) params.getValue("cantHumanosExtranjeros");
		DataSet.cantHumanosLocales			= (Integer) params.getValue("cantHumanosLocales");
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
						tempBuilding = new WorkplaceAgent(geom, id, blockId, type, area, coveredArea, placeType);
						if (placeType.contains("school") || placeType.contains("university"))
							schoolPlaces.add((WorkplaceAgent) tempBuilding);
						else {
							workPlaces.add((WorkplaceAgent) tempBuilding);
							
							if (placesTypeList.contains(placeType))
								BuildingManager.addPlace(placeType, (WorkplaceAgent)tempBuilding);
						}
						context.add(tempBuilding);
						geography.move(tempBuilding, geom);
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
		// Crear casas que faltan
		
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
	private void createHuman(int ageGroup, BuildingAgent home, BuildingAgent work, int tmmc) {
		int[] posJob = null; 
		if (work instanceof WorkplaceAgent)
			posJob = ((WorkplaceAgent)work).getWorkPosition();
		HumanAgent tempHuman = new HumanAgent(ageGroup, home, work, posJob, tmmc);
		context.add(tempHuman);
		tempHuman.setStartLocation();
	}

	/**
	 * Crea Humanos dependiendo la franja etaria.
	 * @see DataSet#HUMANS_PER_AGE_GROUP
	 * @see DataSet#cantHumanosFijos
	 */
	private void initHumans() {
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
		
		// Crear humanos que viven y trabajan/estudian en OV
		int age1Count = (int) ((DataSet.cantHumanosFijos) * (DataSet.HUMANS_PER_AGE_GROUP[0] / 100));
		int age2Count = (int) ((DataSet.cantHumanosFijos) * (DataSet.HUMANS_PER_AGE_GROUP[1] / 100));
		int age3Count = (DataSet.cantHumanosFijos) - (age1Count + age2Count);
		// TODO falta implementar los humanos que viajan fuera del contexto (Extranjeros y Locales)
		
		// Cntadores
		int countHumans = DataSet.cantHumanosFijos;
		int countFuera = 0;
		int countChori = 0;
		int countWork = 0;
		int countSchool = 0;
		int countRetired = age3Count;
		//
		
		BuildingAgent tempHome = null;
		BuildingAgent tempJob = null;
		
		// Crear casas ficticias si es que faltan
		int extraHomes = (countHumans / DataSet.HOUSE_INHABITANTS_MEAN) - homePlaces.size();
		if (extraHomes > 0)
			createFictitiousHomes(extraHomes);
		//
		
		Uniform disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.size()-1);
		Uniform disUniformWork  = RandomHelper.createUniform(1, 100);
		int i;
		for (i = 0; i < age1Count; i++) {
			// 1era franja etaria -> Escuela
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			tempJob = findWorkingPlace(schoolPlaces);
			if (tempJob == null) {
				// Si no estudia, trabaja
				tempJob = findWorkingPlace(workPlaces);
				if (tempJob == null) {
					// No trabaja o trabaja en su domicilio
					tempJob = tempHome;
					++countChori;
				}
				else ++countWork;
			}
			else ++countSchool;
			createHuman(0, tempHome, tempJob, 0);
		}
		for (i = 0; i < age2Count; i++) {
			// 2da franja etaria -> Trabajo
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			if (disUniformWork.nextInt() <= DataSet.HUMANOS_PORCENTAJE_DESEMPLEADO) {
				// No trabaja o trabaja en su domicilio
				tempJob = tempHome;
				++countChori;
			}
			else if (disUniformWork.nextInt() <= DataSet.HUMANOS_PORCENTAJE_EXTERIOR) {
				// Trabaja al exterior
				tempJob = null;
				++countFuera;
			}
			else {
				// Trabajador
				tempJob = findWorkingPlace(workPlaces);
				if (tempJob == null) {
					tempJob = findWorkingPlace(schoolPlaces);
					if (tempJob == null) {
						// Trabaja al exterior - Si no hay mas cupos
						++countFuera;
					}
					else ++countSchool;
				}
				else ++countWork;
			}
			createHuman(1, tempHome, tempJob, 1);
		}
		for (i = 0; i < age3Count; i++) {
			// 3era franja etaria -> Casa
			tempHome = homePlaces.get(disUniHomesIndex.nextInt());
			createHuman(2, tempHome, tempHome, 2);
		}
		
		System.out.println("CANTIDAD HUMANOS: " + countHumans);
		System.out.println("CANTIDAD TRABAJADORES: " + countWork);
		System.out.println("CANTIDAD ESTUDIANTES: " + countSchool);
		System.out.println("CANTIDAD JUBILADOS: " + countRetired);
		
		System.out.println("CANTIDAD CHORI: " + countChori);
		System.out.println("CANTIDAD FUERA: " + countFuera);
	}
	
	/**
	 * Busca y resta una posicion de trabajador en la lista de lugares.
	 * @param list
	 * @return WorkplaceAgent o null
	 */
	private WorkplaceAgent findWorkingPlace(List<WorkplaceAgent> list) { 
		int index;
		WorkplaceAgent job = null;
		if (!list.isEmpty()) {
			index = RandomHelper.nextIntFromTo(0, list.size()-1);
			job = list.get(index);
			job.decreaseWorkers();
			if (job.getWorkers() == 0)
				list.remove(index);
		}
		return job;
	}
}
