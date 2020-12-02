package geocovid;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import cern.jet.random.Uniform;
import geocovid.agents.BuildingAgent;
import geocovid.agents.ForeignHumanAgent;
import geocovid.agents.HomeAgent;
import geocovid.agents.HumanAgent;
import geocovid.agents.PublicTransportAgent;
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
	private List<List<HomeAgent>> homePlaces = new ArrayList<List<HomeAgent>>(DataSet.SECTORALS_COUNT);
	private List<WorkplaceAgent>workPlaces = new ArrayList<WorkplaceAgent>();
	private List<WorkplaceAgent>schoolPlaces = new ArrayList<WorkplaceAgent>();
	private List<WorkplaceAgent>universityPlaces = new ArrayList<WorkplaceAgent>();
	
	private HumanAgent[] contextHumans;
	private int localHumansCount;
	private int foreignHumansCount;
	
	private Set<Integer> socialDistIndexes;
	
	private Map<String, PlaceProperty> placesProperty = new HashMap<>();
	
	private ISchedule schedule;
	private Context<Object> context;
	private Geography<Object> geography;
	
	private int simulationStartYear;
	private int simulationStartDay;
	private int simulationMaxTick;
	private int simulationMinDay;
	private int deathLimit;
	private int infectedAmount;
	private int outbreakStartTick;
	private int weekendStartTick;
	
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias
	private int unemployedCount; // Contador de empleos faltantes
	private int unschooledCount; // Contador de bancos faltantes en escuelas
	private int noncollegiateCount; // Contador de bancos faltantes en universidades
	
	/** Ver <b>corridas</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	private int simulationRun = 50;
	
	private boolean publicTransportEnabled = false;
	private String currentMonth = null;
	private boolean weekendTMMCEnabled = false;
	static final int WEEKLY_TICKS = 12*7; // ticks que representan el tiempo de una semana
	static final int WEEKEND_TICKS = 12*2; // ticks que representan el tiempo que dura el fin de semana
	
	public ContextCreator() {
		printJarVersion(this.getClass());
	}
	
	@Override
	public Context<Object> build(Context<Object> context) {
		schedule = RunEnvironment.getInstance().getCurrentSchedule();
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
		
		// Programa los cambios de fases, pasadas y futuras
		int phaseDay;
		int[] phasesStartDay = DataSet.LOCKDOWN_PHASES_DAYS;
		for (int i = 0; i < phasesStartDay.length; i++) {
			phaseDay = phasesStartDay[i] - simulationStartDay;
			if (phaseDay > 0)	// Fase futura
				phaseDay *= 12;
			else				// Fase pasada
				phaseDay = 0;
			params = ScheduleParameters.createOneTime(phaseDay, 0.9d);
			schedule.schedule(params, this, "initiateLockdownPhase", i);
		}
		
		// Reinicio estos valores por las dudas
		publicTransportEnabled = false;
		weekendTMMCEnabled = false;
		currentMonth = null;
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		setWeekendMovement();
		
		this.context = context;
		context.add(new InfectionReport(simulationMinDay, deathLimit)); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartYear, simulationStartDay)); // Para calcular temperatura diaria, para estela
		
		placesProperty = PlaceProperty.loadPlacesProperties();
		loadParcelsShapefile();
		loadPlacesShapefile();
		placesProperty.clear();
		BuildingManager.createActivitiesChances();
		
		DataSet.setDefaultValues();
		
		initHumans();
		
		homePlaces.clear();
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		return context;
	}
	
	/**
	 * Programa en schedule los metodos para cambiar matrices de markov en los periodos de fines de semanas.<p>
	 * Cuando termina se asigna "setHumansDefaultTMMC"
	 */
	private void setWeekendMovement() {
		ScheduleParameters params;
		int endWKND;
		for (int i = weekendStartTick; i < simulationMaxTick; i += WEEKLY_TICKS) {
			params = ScheduleParameters.createOneTime(i, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, "setHumansWeekendTMMC", true);
			endWKND = (i + WEEKEND_TICKS > simulationMaxTick) ? simulationMaxTick : i + WEEKEND_TICKS;
			params = ScheduleParameters.createOneTime(endWKND, ScheduleParameters.FIRST_PRIORITY);
			schedule.schedule(params, this, "setHumansWeekendTMMC", false);
			//System.out.println(i/12+" "+(endWKND-1)/12);
		}
	}
	
	public void startSimulation() {
		simulationStartTime = System.currentTimeMillis();
	}
	
	public void printSimulationDuration() {
		// Por las dudas al finalizar borro la referencia de HumanAgents
		contextHumans = null;
		
		final long simTime = System.currentTimeMillis() - simulationStartTime;
		
		System.out.printf("Simulacion N°: %3d | Tiempo: %.2f minutos%n", simulationRun, (simTime / (double)(1000*60)));
		System.out.printf("Dias epidemia: %3d%n", (int) (schedule.getTickCount() - outbreakStartTick) / 12);
		
		System.out.printf("Susceptibles: %5d | Infectados: %d | Infectados por estela: %d%n",
				(localHumansCount + foreignHumansCount),
				InfectionReport.getCumExposed(),
				InfectionReport.getCumExposedToCS());
    	
		System.out.printf("Recuperados:  %5d | Muertos: %d%n",
				InfectionReport.getCumRecovered(),
				InfectionReport.getCumDeaths());
		
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			System.out.println(InfectionReport.getInfectedReport(i));
		}
	}
	
	/**
	 * Selecciona al azar la cantidad de Humanos locales seteada en los parametros y los infecta.
	 */
	public void infectLocalRandos(int amount) {
		if (amount == 0)
			return;
		InfectionReport.outbreakStarted = true; // a partir de ahora hay riesgo de infeccion
		int infected = 0;
		Set<Integer> indexes = new HashSet<Integer>(amount, 1f);
		int i;
		do {
			i = RandomHelper.nextIntFromTo(foreignHumansCount, localHumansCount + foreignHumansCount - 1);
			// Chequea si no es repetido
			if (indexes.add(i)) {
				contextHumans[i].setInfectious(true, true); // Asintomatico
				++infected;
			}
		} while (infected != amount);
	}
	
	/**
	 * Setea aleatoriamente a Humanos si respetan el distanciamiento social, segun porcentaje de la poblacion dado.
	 * @param porcentaje
	 */
	private void setSocialDistancing(int porc) {
		int newAmount = (int) Math.ceil((localHumansCount + foreignHumansCount) * porc) / 100;
		int oldAmount = 0;
		// El porcentaje era cero
		if (DataSet.getSDPercentage() == 0 && porc > 0) {
			socialDistIndexes = new HashSet<Integer>(newAmount);
		}
		else
			oldAmount = socialDistIndexes.size();
		DataSet.setSDPercentage(porc);
		
		// El nuevo porcentaje es menor
		if (newAmount < oldAmount) {
			int toRemove = oldAmount - newAmount;
			for (java.util.Iterator<Integer> it = socialDistIndexes.iterator(); it.hasNext();) {
				Integer index = it.next();
				contextHumans[index].setSociallyDistanced(false);
				it.remove();
				if (--toRemove == 0)
					break;
			}
		}
		// El nuevo porcentaje es mayor
		else if (newAmount > oldAmount) {
			int toAdd = newAmount - oldAmount;
			int i;
			do {
				i = RandomHelper.nextIntFromTo(0, localHumansCount + foreignHumansCount - 1);
				if (socialDistIndexes.add(i)) {
					contextHumans[i].setSociallyDistanced(true);
					--toAdd;
				}
			} while (toAdd != 0);
		}
	}
	
	/**
	 * Habilitar o deshabilita la opcion de usar transporte publico. 
	 */
	private void enablePublicTransport(boolean enabled) {
		if (enabled && !publicTransportEnabled) {
			publicTransportEnabled = true;
			PublicTransportAgent pt = new PublicTransportAgent();
			context.add(pt);
			BuildingManager.setPublicTransport(pt);
		}
		else if (!enabled && publicTransportEnabled) {
			publicTransportEnabled = false;
			context.remove(BuildingManager.getPublicTransport());
			BuildingManager.setPublicTransport(null);
		}
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan los fines de semana.
	 */
	public void setHumansWeekendTMMC(boolean enabled) {
		weekendTMMCEnabled = enabled;
		for (int i = 0; i < DataSet.AGE_GROUPS-1; i++) {
			MarkovChains.setWeekendDiff(HumanAgent.localTMMC[0][i], enabled);
			MarkovChains.setWeekendDiff(HumanAgent.localTMMC[1][i], enabled);
		}
		HumanAgent.travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
	}
	
	public void setTMMCs(String month, int[][][][] tmmc0, int[][][][] tmmc1) {
		if (!month.equals(currentMonth)) {
			currentMonth = month; 
			for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
				HumanAgent.localTMMC[0][i]	= tmmc0[i];
				HumanAgent.localTMMC[1][i]	= tmmc1[i];
			}
		}
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.<p>
	 * Ver {@link #initHumans()}
	 */
	public void setDefaultTMMC() {
		setTMMCs("default", MarkovChains.SEC2_DEFAULT_TMMC, MarkovChains.SEC11_DEFAULT_TMMC);
		
		HumanAgent.travelerTMMC	= MarkovChains.TRAVELER_DEFAULT_TMMC;
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			HumanAgent.isolatedLocalTMMC[i] = MarkovChains.ISOLATED_TMMC;
		}
		HumanAgent.infectedTravelerTMMC = MarkovChains.ISOLATED_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se van a utilizar al comenzar cada fase.
	 */
	public void initiateLockdownPhase(int phase) {
		boolean lockdownOverWKD = false;
		// Chequea si se cambio de fase durante el fin de semana y no es la primer fase
		if (weekendTMMCEnabled && schedule.getTickCount() > 0d) {
			lockdownOverWKD = true;
			setHumansWeekendTMMC(false); // para restar la matriz de finde
		}
		//
		switch (phase) {
		case 0: // 12 junio
			// Reapertura progresiva (Fase 4)
			BuildingManager.closePlaces(new String[] {
					// Trabajo/estudio
					"lodging", "nursery_school", "association_or_organization", "primary_school", "secondary_school", "university",
					// Ocio
					"movie_theater", "bar", "sports_complex",  "school", "bus_station", "church", "sports_school", "spa", "night_club", "gym", "tourist_attraction",
					"restaurant", "stadium", "sports_club", "park", "library", "cultural_center", "club", "casino", "campground", "art_gallery" });
			setTMMCs("june", MarkovChains.SEC2_JUNE_TMMC, MarkovChains.SEC11_JUNE_TMMC);
			BuildingManager.limitActivitiesCapacity(DataSet.DEFAULT_PLACES_CAP_LIMIT);
			enablePublicTransport(true);
			setSocialDistancing(80);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 1: //  1 julio
			enablePublicTransport(false); // comienza el paro de choferes
			break;
		case 2: // 20 julio
			// Reapertura progresiva (Fase 4)
			BuildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			setTMMCs("july", MarkovChains.SEC2_JULY_TMMC, MarkovChains.SEC11_JULY_TMMC);
			BuildingManager.limitActivitiesCapacity(4d);
			setSocialDistancing(80);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 3: // 3 agosto
			// Mini veranito
			setTMMCs("august", MarkovChains.SEC2_AUGUST_TMMC, MarkovChains.SEC11_AUGUST_TMMC);
			BuildingManager.limitActivitiesCapacity(3d);
			setSocialDistancing(65);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 4: // 17 agosto
			// Nueva normalidad (Fase 5)
			enablePublicTransport(true); // finaliza el paro de choferes
			BuildingManager.limitActivitiesCapacity(4d);
			setSocialDistancing(80);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 5: // 31 agosto
			// Vuelta a atras por saturacion de sistema sanitario (Fase 4)
			BuildingManager.closePlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club", "park"});
			BuildingManager.limitActivitiesCapacity(4d);
			setSocialDistancing(70);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 6: // 11 septiembre
			// Nuevas medidas (contacto estrecho)
			DataSet.enableCloseContacts();
			DataSet.enablePrevQuarantine();
			//
			BuildingManager.limitActivitiesCapacity(2d);
			setSocialDistancing(60);
			DataSet.setMaskEffectivity(0.25);
			break;
		case 7: // 14 septiembre
			BuildingManager.openPlaces(new String[] {"bar", "restaurant", "sports_school", "gym", "sports_club"});
			setTMMCs("september", MarkovChains.SEC2_SEPTEMBER_TMMC, MarkovChains.SEC11_SEPTEMBER_TMMC);
			BuildingManager.limitActivitiesCapacity(3d);
			setSocialDistancing(50);
			DataSet.setMaskEffectivity(0.20);
			break;
		case 8: // 21 septiembre
			BuildingManager.openPlaces(new String[] {"sports_club", "church", "sports_complex", "park"});
			setSocialDistancing(40);
			DataSet.setMaskEffectivity(0.15);
			break;
		case 9: // 9 octubre
			setTMMCs("october", MarkovChains.SEC2_OCTOBER_TMMC, MarkovChains.SEC11_OCTOBER_TMMC);
			break;
		case 10: // 29 octubre
			BuildingManager.openPlaces(new String[] {"casino", "nursery_school"});
			setSocialDistancing(30);
			break;
		case 11: // 6 noviembre
			// Nueva alversoetapa
			setTMMCs("default", MarkovChains.SEC2_DEFAULT_TMMC, MarkovChains.SEC11_DEFAULT_TMMC);
			setSocialDistancing(20);
			break;
		default:
			break;
		}
		// Si corresponde, suma la matriz de fin de semana a las nuevas matrices
		if (lockdownOverWKD)
			setHumansWeekendTMMC(true);  // para sumar la matriz de finde
	}

	/**
	 * Lee los valores de parametros en la interfaz de Simphony (Archivo "GeoCOVID-19.rs\parameters.xml").
	 */
	private void setBachParameters() {
		Parameters params = RunEnvironment.getInstance().getParameters();
		// Ano simulacion, para calcular temperatura (2020 - 2022)
		simulationStartYear	= ((Integer) params.getValue("anoInicioSimulacion")).intValue();
		// Dia calendario, para calcular temperatura (0 - 364)
		simulationStartDay	= ((Integer) params.getValue("diaInicioSimulacion")).intValue();
		// Dias maximo de simulacion - por mas que "diaMinimoSimulacion" sea mayor (0 = Infinito)
		simulationMaxTick	= ((Integer) params.getValue("diasSimulacion")).intValue() * 12;
		// Dias minimo de simulacion - por mas que supere "cantidadMuertosLimite" (0 ...)
		simulationMinDay	= ((Integer) params.getValue("diasMinimoSimulacion")).intValue();
		// Dias hasta primer sabado (0 ...)
		weekendStartTick	= ((Integer) params.getValue("diasPrimerFinde")).intValue() * 12;
		// Cantidad de muertos que debe superar para finalizar simulacion - ver "diaMinimoSimulacion" (0 = Infinito)
		deathLimit			= ((Integer) params.getValue("cantidadMuertosLimite")).intValue();
		// Dia de entrada de infectados
		outbreakStartTick	= ((Integer) params.getValue("diaEntradaCaso")).intValue() * 12;
		// Cantidad de infectados iniciales
		infectedAmount		= ((Integer) params.getValue("cantidadInfectados")).intValue();
		// Cantidad de corridas para hacer en batch
		simulationRun		= (Integer) params.getValue("corridas");
	}

	private void loadParcelsShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(DataSet.SHP_FILE_PARCELS);
		homePlaces.clear();
		for (int i = 0; i < DataSet.SECTORALS_COUNT; i++) {
			homePlaces.add(new ArrayList<HomeAgent>());
		}
		maxParcelId = 0;
		
		HomeAgent tempBuilding = null;
		GeometryFactory geometryFactory = new GeometryFactory();
		
		BuildingManager.initManager(context, geography);

		double tempX, tempY;
		double maxX[] = new double[DataSet.SECTORALS_COUNT];
		double minX[] = new double[DataSet.SECTORALS_COUNT];
		double maxY[] = new double[DataSet.SECTORALS_COUNT];
		double minY[] = new double[DataSet.SECTORALS_COUNT];
		Arrays.fill(maxX, -180d);
		Arrays.fill(minX, 180d);
		Arrays.fill(maxY, -90d);
		Arrays.fill(minY, 90d);
		
		int id;
		int sectoral, sectoralType, sectoralIndex;
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Parcel invalid geometry: " + feature.getID());
				continue;
			}
			// Polygon - Formato Catastro Parana - modificado
			id = (int)feature.getAttribute("id");
			sectoral = (int)feature.getAttribute("sec");
			//group = (int)feature.getAttribute("gru");
			//block = (int)feature.getAttribute("manz");
			//parcel = (int)feature.getAttribute("parc");
			//area = (int)feature.getAttribute("area");
			//coveredArea = (int)feature.getAttribute("c_area");
			
			// Busca los valores min y max de X e Y
			// para crear el Extent o Boundary que incluye a todas las parcelas
			sectoralIndex = sectoral - 1;
			tempX = geom.getCoordinate().x;
			if (tempX > maxX[sectoralIndex])
				maxX[sectoralIndex] = tempX;
			else if (tempX < minX[sectoralIndex])
				minX[sectoralIndex] = tempX;
			tempY = geom.getCoordinate().y;
			if (tempY > maxY[sectoralIndex])
				maxY[sectoralIndex] = tempY;
			else if (tempY < minY[sectoralIndex])
				minY[sectoralIndex] = tempY;
			//

			// Convierto las geometrias Polygon a Point
			geom = geometryFactory.createPoint(geom.getCentroid().getCoordinate());
			
			// Guarda la ultima ID de parcela, para crear ficticias
			if (id > maxParcelId)
				maxParcelId = id;
			
			sectoralType = DataSet.SECTORALS_TYPES[sectoral - 1];
			tempBuilding = new HomeAgent(sectoralType, sectoral - 1, geom.getCoordinate(), id);
			homePlaces.get(sectoral - 1).add(tempBuilding);
			context.add(tempBuilding);
			geography.move(tempBuilding, geom);
		}
		features.clear();
		
		BuildingManager.setBoundary(minX, maxX, minY, maxY);
	}
	
	private void loadPlacesShapefile() {
		List<SimpleFeature> features = loadFeaturesFromShapefile(DataSet.SHP_FILE_PLACES);
		workPlaces.clear();
		schoolPlaces.clear();
		universityPlaces.clear();
		
		PlaceProperty placeProp;
		PlaceProperty placeSecProp;
		
		String type;
		String[] types;
		
		Coordinate coord;
		Coordinate[] coords = BuildingManager.getSectoralsCentre();
		double minDistance, tempDistance = 0d;
		int sectoralType, sectoralIndex = 0;
		int buildingArea;
		//Name,type,ratings,latitude,longitude,place_id
		for (SimpleFeature feature : features) {
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (geom == null || !geom.isValid()) {
				System.err.println("Place invalid geometry: " + feature.getID() + (int)feature.getAttribute("id"));
			}
			if (geom instanceof Point) {
				//id = (Long)feature.getAttribute("id");
				//name = (String) feature.getAttribute("name");
				type = (String) feature.getAttribute("type");
				//rating = (int) feature.getAttribute("rating");
				
				// Separar types y tomar el primero
				types = type.split("\\+");
				placeProp = placesProperty.get(types[0]);
				if (placeProp == null) {
					System.out.println("Type de Place desconocido: " + types[0]);
					continue;
				}
				type = types[0];
				buildingArea = placeProp.getBuildingArea();
				
				// Si tiene 2 types se suma el area del segundo
				if (types.length > 1) {
					placeSecProp = placesProperty.get(types[1]);
					if (placeSecProp != null) {
						buildingArea += placeSecProp.getBuildingArea();
					}
					else {
						System.out.println("Type secundario de Place desconocido: " + types[1]);
					}
				}
				
				// Buscar la seccional mas cercana para asignar a este Place
				minDistance = 180f;
				coord = geom.getCoordinate();
				for (int i = 0; i < coords.length; i++) {
					tempDistance = coords[i].distance(coord);
					if (tempDistance < minDistance) {
						minDistance = tempDistance;
						sectoralIndex = i;
					}
				}
				sectoralType = DataSet.SECTORALS_TYPES[sectoralIndex];
				
				// Crear Agente con los atributos el Place
				WorkplaceAgent tempWorkspace = new WorkplaceAgent(sectoralType, sectoralIndex, coord, ++maxParcelId, type, placeProp.getActivityType(),
						buildingArea, placeProp.getBuildingCArea(), placeProp.getWorkersPerPlace(), placeProp.getWorkersPerArea());
				
				// Agrupar el Place con el resto del mismo type
				if (placeProp.getActivityType() == 1) { // trabajo / estudio
					if (type.contains("primary_school") || type.contains("secondary_school") || type.contains("technical_school"))
						schoolPlaces.add(tempWorkspace);
					else if (type.contains("university"))
						universityPlaces.add(tempWorkspace);
					else
						workPlaces.add(tempWorkspace);
					// Si es lugar sin atencion al publico, se agrega a la lista de lugares de trabajo/estudio
					BuildingManager.addWorkplace(type, tempWorkspace);
				}
				else { // ocio, otros
					workPlaces.add(tempWorkspace);
					// Si es lugar con atencion al publico, se agrega a la lista de actividades
					BuildingManager.addPlace(sectoralIndex, tempWorkspace, placeProp);
				}
				
				// Agregar al contexto
				context.add(tempWorkspace);
				geography.move(tempWorkspace, geom);
			}
			else {
				System.err.println("Error creating agent for " + geom);
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
	 * @param secIndex
	 * @param extraHomes
	 */
	private void createFictitiousHomes(int secIndex, int extraHomes) {
		GeometryFactory geometryFactory = new GeometryFactory();
		HomeAgent tempBuilding, tempHome;
		int[] ciIndexes = IntStream.range(0, homePlaces.get(secIndex).size()).toArray();
		int indexesCount = homePlaces.get(secIndex).size()-1;
		int randomIndex;
		
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				randomIndex = RandomHelper.nextIntFromTo(0, indexesCount);
				tempHome = homePlaces.get(secIndex).get(ciIndexes[randomIndex]);
				ciIndexes[randomIndex] = ciIndexes[indexesCount--];
				//
				tempBuilding = new HomeAgent(tempHome);
				tempBuilding.setId(++maxParcelId);
				homePlaces.get(secIndex).add(tempBuilding);
				context.add(tempBuilding);
				geography.move(tempBuilding, geometryFactory.createPoint(tempBuilding.getCoordinate()));
			}
		}
	}
	
	/**
	 * Selecciona hogares al azar y los elimina.
	 * @param secIndex
	 * @param extraHomes
	 */
	private void deleteExtraHomes(int secIndex, int extraHomes) {
		HomeAgent tempHome;
		int indexesCount = homePlaces.get(secIndex).size()-1;
		int randomIndex;
		for (int i = 0; i <= extraHomes; i++) {
			if (indexesCount >= 0) {
				randomIndex = RandomHelper.nextIntFromTo(0, indexesCount);
				tempHome = homePlaces.get(secIndex).remove(randomIndex);
				context.remove(tempHome);
				--indexesCount;
			}
		}
	}
	
	/**
	 * Crea Humanos y asigna a cada uno un lugar aleatorio en la grilla, como posicion del hogar.
	 * @param secType
	 * @param secIndex
	 * @param ageGroup
	 * @param home
	 * @param work
	 * @return
	 */
	private HumanAgent createHuman(int secType, int secIndex, int ageGroup, BuildingAgent home, BuildingAgent work) {
		int[] workPos = null;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
		}
		HumanAgent tempHuman = new HumanAgent(secType, secIndex, ageGroup, home, work, workPos, false);
		context.add(tempHuman);
		tempHuman.setStartLocation();
		return tempHuman;
	}
	
	/**
	 * Crea Humanos extranjeros, sin hogar pero con lugar de trabajo.
	 * @param ageGroup
	 * @param work
	 * @return
	 */
	private HumanAgent createForeignHuman(int ageGroup, BuildingAgent work) {
		int[] workPos = null;
		int secType = 0, secIndex = 0;
		// Se le asigna una posicion fija en el trabajo, si es que trabaja
		if (work instanceof WorkplaceAgent) {
			workPos = ((WorkplaceAgent)work).getWorkPosition();
			// Si tiene trabajo se le asigna como hogar la seccional del lugar donde trabaja
			secType = work.getSectoralType();
			secIndex = work.getSectoralIndex();
		}
		HumanAgent tempHuman = new ForeignHumanAgent(secType, secIndex, ageGroup, work, workPos);
		context.add(tempHuman);
		tempHuman.setStartLocation();
		return tempHuman;
	}

	/**
	 * Crea Humanos dependiendo la franja etaria, lugar de trabajo y vivienda.
	 */
	private void initHumans() {
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		setDefaultTMMC();
		BuildingAgent.initInfAndPDRadius(); // Crea posiciones de infeccion en grilla
		
		int[][] locals = new int[DataSet.SECTORALS_COUNT][DataSet.AGE_GROUPS];
		int[][] localTravelers = new int[DataSet.SECTORALS_COUNT][DataSet.AGE_GROUPS];
		localHumansCount = 0;
		
		int localCount;
		int localTravelersCount;
		int sectoralType;
		int i, j, k;
		for (i = 0; i < DataSet.SECTORALS_COUNT; i++) {
			sectoralType = DataSet.SECTORALS_TYPES[i];
			localCount = (int) (((DataSet.LOCAL_HUMANS + DataSet.LOCAL_TRAVELER_HUMANS) * DataSet.SECTORALS_POPULATION[i]) / 100);
			localTravelersCount = (int) ((DataSet.LOCAL_TRAVELER_HUMANS * DataSet.SECTORALS_POPULATION[i]) / 100);
			int extraHomes = (int) (localCount / DataSet.HOUSE_INHABITANTS_MEAN[sectoralType]) - homePlaces.get(i).size();
			if (extraHomes > 0) {
				createFictitiousHomes(i, extraHomes);
				System.out.println("HOGARES PROMEDIO FALTANTES EN SEC " + (i+1) + ": " + extraHomes);
			}
			else {
				deleteExtraHomes(i, extraHomes*-1);
				System.out.println("HOGARES PROMEDIO SOBRANTES EN SEC " + (i+1) + ": " + extraHomes*-1);
			}
			// Crear humanos que viven y trabajan/estudian en contexto
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				locals[i][j] = (int) Math.ceil((localCount * DataSet.HUMANS_PER_AGE_GROUP[j]) / 100);
				localHumansCount += locals[i][j];
				localTravelers[i][j] = (int) Math.ceil((localTravelersCount * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[sectoralType][j]) / 100);
				locals[i][j] -= localTravelers[i][j];
			}
			//
		}
		
		int foreignCount = DataSet.FOREIGN_TRAVELER_HUMANS;
		int[] foreignTravelers = new int[DataSet.AGE_GROUPS];
		foreignHumansCount = 0;
		// Crear humanos que viven fuera y trabajan/estudian en contexto
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			foreignTravelers[i] = (int) Math.ceil((foreignCount * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[0][i]) / 100);
			foreignHumansCount += foreignTravelers[i];
		}
		//
		
		// DEBUG
		/*
		for (i = 0; i < DataSet.SECTORALS_COUNT; i++)
			for (j = 0; j < DataSet.AGE_GROUPS; j++)
				System.out.println(locals[i][j] + "," + localTravelers[i][j]);
		for (j = 0; j < DataSet.AGE_GROUPS; j++)
			System.out.println(foreignTravelers[j]);
		*/
		
		contextHumans = new HumanAgent[localHumansCount + foreignHumansCount];
		int humInd = 0;
		
		HumanAgent tempHuman = null;
		HomeAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		noncollegiateCount = 0;
		
		// Primero se crean los extranjeros, se asume que hay cupo de lugares de estudio y trabajo
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < foreignTravelers[i]; j++) {
				tempJob = findAGWorkingPlace(0, i, null);
				contextHumans[humInd++] = createForeignHuman(i, tempJob);
			}
		}
		
		Uniform disUniHomesIndex;
		// Segundo se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < DataSet.SECTORALS_COUNT; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			sectoralType = DataSet.SECTORALS_TYPES[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < localTravelers[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					//
					tempHuman = createHuman(sectoralType, i, j, tempHome, null);
					tempHome.addOccupant(tempHuman);
					contextHumans[humInd++] = tempHuman;
				}
			}
		}
		
		// Por ultimo se crean los 100% locales
		for (i = 0; i < DataSet.SECTORALS_COUNT; i++) {
			disUniHomesIndex = RandomHelper.createUniform(0, homePlaces.get(i).size()-1);
			sectoralType = DataSet.SECTORALS_TYPES[i];
			for (j = 0; j < DataSet.AGE_GROUPS; j++) {
				for (k = 0; k < locals[i][j]; k++) {
					tempHome = homePlaces.get(i).get(disUniHomesIndex.nextInt());
					tempJob = findAGWorkingPlace(sectoralType, j, tempHome);
					//
					tempHuman = createHuman(sectoralType, i, j, tempHome, tempJob);
					tempHome.addOccupant(tempHuman);
					contextHumans[humInd++] = tempHuman;
				}
			}
		}
		
		if (unemployedCount != 0)
			System.out.println("PUESTOS TRABAJO FALTANTES: " + unemployedCount);
		if (unschooledCount != 0)
			System.out.println("BANCOS EN ESCUELA FALTANTES: " + unschooledCount);
		if (noncollegiateCount != 0)
			System.out.println("BANCOS EN FACULTAD FALTANTES: " + noncollegiateCount);
	}

	/**
	 * Busca lugar de trabajo/estudio en las distintas colecciones (escuela, facultad, trabajo), segun tipo de seccional, franja etaria y ocupacion<p>
	 * @param secType
	 * @param ageGroup
	 * @param home 
	 * @return WorkplaceAgent, BuildingAgent o null
	 */
	private BuildingAgent findAGWorkingPlace(int secType, int ageGroup, BuildingAgent home) {
		BuildingAgent workplace = null;
		//
		double occupation[] = DataSet.OCCUPATION_PER_AGE_GROUP[secType][ageGroup];
		int r = RandomHelper.nextIntFromTo(1, 100);
		int i = 0;
        while (r > occupation[i]) {
        	r -= occupation[i];
        	++i;
        }
        
        if (i == 0) { // estudiante
        	if (ageGroup == 0 || (ageGroup == 1 && (occupation[i] - r < occupation[i]*.4d))) { // 40% es primario
        		workplace = findWorkingPlace(schoolPlaces);
            	if (workplace == null) {
            		workplace = home;
            		++unschooledCount;
            	}
        	}
        	else {
        		workplace = findWorkingPlace(universityPlaces);
            	if (workplace == null) {
            		workplace = home;
            		++noncollegiateCount;
            	}
        	}
        }
        else if (i == 1) { // trabajor
        	int wp = RandomHelper.nextIntFromTo(1, 100);
        	// Primero ver si tiene un trabajo convencional
        	if (wp <= DataSet.WORKING_FROM_HOME[secType] + DataSet.WORKING_OUTDOORS[secType]) {
        		// Si no, puede trabajar en la casa o al exterior
        		wp = RandomHelper.nextIntFromTo(1, DataSet.WORKING_FROM_HOME[secType] + DataSet.WORKING_OUTDOORS[secType]);
        		if (wp <= DataSet.WORKING_FROM_HOME[secType])
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
	
	/**
	 * Imprime la hora de compilacion del jar (si existe).
	 * @param cl
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
