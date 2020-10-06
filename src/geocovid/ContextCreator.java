package geocovid;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	
	private HumanAgent[] contextHumans;
	private int localHumansCount;
	private int foreignHumansCount;
	
	private Set<Integer> socialDistIndexes;
	
	private Map<Long, String> placesType = new HashMap<>();
	private Map<String, PlaceProperty> placesProperty = new HashMap<>();
	
	private ISchedule schedule;
	private Context<Object> context;
	private Geography<Object> geography;
	
	private int simulationStartDay;
	private int simulationMaxTick;
	private int simulationMinDay;
	private int deathLimit;
	private int infectedAmount;
	private int outbreakStartTick;
	private int lockdownStartTick;
	private int weekendStartTick;
	
	private long simulationStartTime;
	
	private long maxParcelId; // Para no repetir ids, al crear casas ficticias
	private int unemployedCount; // Contador de empleos faltantes
	private int unschooledCount; // Contador de bancos faltantes en escuelas
	
	/** Ver <b>corridas</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	private int simulationRun = 100;
	
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
		
		int[] phases = DataSet.LOCKDOWN_PHASES;
		int[] phasesStartDay = DataSet.LOCKDOWN_PHASES_DAYS;
		for (int i = 0; i < phases.length; i++) {
			params = ScheduleParameters.createOneTime(phasesStartDay[i] * 12, 0.9d);
			schedule.schedule(params, this, "initiateLockdownPhase", phases[i], DataSet.SECTORAL);
		}
		
		// Schedules one shot para los inicios y los fines de semana, hasta el comienzo de la cuartentena.
		setWeekendMovement();
		
		this.context = context;
		context.add(new InfectionReport(simulationMinDay, deathLimit)); // Unicamente para la grafica en Repast Simphony
		context.add(new Temperature(simulationStartDay)); // Para calcular temperatura diaria, para estela
		
		loadPlacesShapefile();
		PlaceProperty.loadPlacesProperties(placesProperty);
		loadParcelsShapefile(DataSet.SECTORAL);
		
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
		
		int infected = 0;
		Set<Integer> indexes = new HashSet<Integer>(amount, 1f);
		int i;
		do {
			i = RandomHelper.nextIntFromTo(foreignHumansCount, localHumansCount + foreignHumansCount - 1);
			// Chequea si no es repetido
			if (indexes.add(i)) {
				contextHumans[i].setInfectious(true); // Asintomatico
				++infected;
				//-if (localHumans[i].getAgeGroup() == 2 && localHumans[i].getPlaceOfWork() instanceof WorkplaceAgent) {
			}
		} while (infected != amount);
	}
	
	/**
	 * Setea aleatoriamente a Humanos si respetan el distanciamiento social, segun porcentaje de la poblacion dado 
	 * @param porcentaje
	 */
	@SuppressWarnings("unused")
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
	 * Asignar las matrices de markov que se utilizan los fines de semana.
	 */
	public void setHumansWeekendTMMC(boolean enabled) {
		weekendTMMCEnabled = enabled;
		for (int i = 0; i < DataSet.AGE_GROUPS-1; i++) {
			MarkovChains.setWeekendDiff(HumanAgent.localTMMC[i], enabled);
		}
		HumanAgent.travelerTMMC = (enabled ? MarkovChains.TRAVELER_WEEKEND_TMMC : MarkovChains.TRAVELER_DEFAULT_TMMC);
	}
	
	/**
	 * Asignar las matrices de markov que se utilizan al principio de simulacion.<p>
	 * Ver {@link #initHumans()}
	 */
	public void setHumansDefaultTMMC(int sectoral) {
		if (sectoral == 0) {
			HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC2_JUNE_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC2_JUNE_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC2_JUNE_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC2_JUNE_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC2_JUNE_TMMC;
		}
		else {
			HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC11_JUNE_TMMC;
			HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC11_JUNE_TMMC;
			HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC11_JUNE_TMMC;
			HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC11_JUNE_TMMC;
			HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC11_JUNE_TMMC;
		}
		HumanAgent.travelerTMMC	= MarkovChains.TRAVELER_DEFAULT_TMMC;
		
		for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
			HumanAgent.isolatedLocalTMMC[i] = MarkovChains.ISOLATED_TMMC;
		}
		HumanAgent.infectedTravelerTMMC = MarkovChains.ISOLATED_TMMC;
	}
	
	/**
	 * Asignar las matrices de markov que se van a utilizar al comenzar cada fase.
	 */
	public void initiateLockdownPhase(int phase, int sectoral) {
		boolean lockdownOverWKD = false;
		// Chequea si se cambio de fase durante el fin de semana y no es la primer fase
		if (weekendTMMCEnabled && schedule.getTickCount() > 0d) {
			lockdownOverWKD = true;
			setHumansWeekendTMMC(false); // para restar la matriz de finde
		}
		//
		switch (phase) {
		case 0:
			// Reapertura progresiva (Fase 4)
			BuildingManager.closePlaces(new String[] {
					// Trabajo/estudio
					"lodging", "nursery_school", "political_party", "primary_school", "secondary_school", "university",
					// Ocio
					"amphitheatre", "bar", "basketball_club", "beauty_salon", "beauty_school", "bus_station", "church",
					"dance_school", "escape_room_center", "function_room_facility", "gym", "language_school",
					"restaurant", "soccer_club", "soccer_field", "sports_club", "synagogue" });
			// No setea matrices por que se usan las por defecto "setHumansDefaultTMMC"
			BuildingManager.limitActivitiesCapacity(4d);
			setSocialDistancing(75);//80
			DataSet.setMaskEffectivity(28);
			break;
		case 1:
			// Reapertura progresiva (Fase 4)
			BuildingManager.closePlaces(new String[] {"bar", "dance_school", "gym","restaurant", "sports_club"}); // Ocio
			if (sectoral == 0) {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC2_JULY_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC2_JULY_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC2_JULY_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC2_JULY_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC2_JULY_TMMC;
			}
			else {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC11_JULY_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC11_JULY_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC11_JULY_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC11_JULY_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC11_JULY_TMMC;
			}
			//BuildingManager.limitActivitiesCapacity(4d);
			//setSocialDistancing(70);//75
			DataSet.setMaskEffectivity(26);
			break;
		case 2:
			// Nueva normalidad (Fase 5)
			BuildingManager.openPlaces(new String[] {"bar", "dance_school", "gym","restaurant", "sports_club"}); // Ocio
			if (sectoral == 0) {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC2_AUGUST_TMMC;
			}
			else {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC11_AUGUST_TMMC;
			}
			//BuildingManager.limitActivitiesCapacity(3d);
			//setSocialDistancing(65);//70
			DataSet.setMaskEffectivity(22);
			break;
		case 3:
			BuildingManager.openPlaces(new String[] {"bar", "gym","restaurant"}); // Ocio
			if (sectoral == 0) {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC2_AUGUST_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC2_AUGUST_TMMC;
			}
			else {
				HumanAgent.localTMMC[0]	= MarkovChains.YOUNG_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[1]	= MarkovChains.YOUNG_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[2]	= MarkovChains.ADULT_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[3]	= MarkovChains.ADULT_SEC11_AUGUST_TMMC;
				HumanAgent.localTMMC[4]	= MarkovChains.HIGHER_SEC11_AUGUST_TMMC;
			}
			//BuildingManager.limitActivitiesCapacity(3d);
			//setSocialDistancing(60);
			//DataSet.setMaskEffectivity(18);
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
		// Dias de delay desde que entran los infectados para iniciar cuarentena (0 = Ninguno)
		lockdownStartTick	= ((Integer) params.getValue("diaInicioCuarentena")).intValue() * 12;
		// Cuarentena comienza segun primer caso + inicio cuarentena
		lockdownStartTick	+= outbreakStartTick;
		// Cantidad de corridas para hacer en batch
		simulationRun		= (Integer) params.getValue("corridas");
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
						// Si es lugar sin atencion al publico, se agrega a la lista de lugares de trabajo/estudio
						BuildingManager.addWorkplace(placeType, tempWorkspace);
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
	private HumanAgent createHuman(int ageGroup, BuildingAgent home, BuildingAgent work) {
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
		return tempHuman;
	}

	/**
	 * Crea Humanos dependiendo la franja etaria, lugar de trabajo y vivienda.
	 */
	private void initHumans() {
		HumanAgent.initAgentID(); // Reinicio contador de IDs
		setHumansDefaultTMMC(DataSet.SECTORAL);
		BuildingAgent.initInfAndPDRadius(); // Crea posiciones de infeccion en grilla
		
		int localCount = DataSet.LOCAL_HUMANS[DataSet.SECTORAL] + DataSet.LOCAL_TRAVELER_HUMANS[DataSet.SECTORAL];
		int foreignCount = DataSet.FOREIGN_TRAVELER_HUMANS[DataSet.SECTORAL];
		// Crear casas ficticias si es que faltan
		int extraHomes = (int) (localCount / DataSet.HOUSE_INHABITANTS_MEAN[DataSet.SECTORAL]) - homePlaces.size();
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
		
		localHumansCount = 0;
		foreignHumansCount = 0;
		int i, j;
		// Crear humanos que viven y trabajan/estudian en contexto
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			locals[i] = (int) Math.ceil((localCount * DataSet.HUMANS_PER_AGE_GROUP[i]) / 100);
			localHumansCount += locals[i];
			localTravelers[i] = (int) Math.ceil((DataSet.LOCAL_TRAVELER_HUMANS[DataSet.SECTORAL] * DataSet.LOCAL_HUMANS_PER_AGE_GROUP[DataSet.SECTORAL][i]) / 100);
			locals[i] -= localTravelers[i];
			//
			foreignTravelers[i] = (int) Math.ceil((foreignCount * DataSet.FOREIGN_HUMANS_PER_AGE_GROUP[DataSet.SECTORAL][i]) / 100);
			foreignHumansCount += foreignTravelers[i];
		}
		//
		
		// DEBUG
		//for (i = 0; i < DataSet.AGE_GROUPS; i++)
		//	System.out.println(locals[i] + " | " + localTravelers[i] + " | " + foreignTravelers[i]);
		
		contextHumans = new HumanAgent[localHumansCount + foreignHumansCount];
		int humInd = 0;
		
		BuildingAgent tempHome = null;
		BuildingAgent tempJob = null;
		unemployedCount = 0;
		unschooledCount = 0;
		
		// Primero se crean los extranjeros, se asume que hay cupo de lugares de estudio y trabajo
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < foreignTravelers[i]; j++) {
				tempJob = findAGWorkingPlace(i, null);
				contextHumans[humInd++] = createHuman(i, null, tempJob);
			}
		}
		
		// Segundo se crean los locales, pero que trabajan o estudian fuera
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < localTravelers[i]; j++) {
				tempHome = homePlaces.get(disUniHomesIndex.nextInt());
				contextHumans[humInd++] = createHuman(i, tempHome, null);
			}
		}
		
		// Por ultimo se crean los 100% locales
		for (i = 0; i < DataSet.AGE_GROUPS; i++) {
			for (j = 0; j < locals[i]; j++) {
				tempHome = homePlaces.get(disUniHomesIndex.nextInt());
				tempJob = findAGWorkingPlace(i, tempHome);
				contextHumans[humInd++] = createHuman(i, tempHome, tempJob);
			}
		}
		
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
        	if (workplace == null) {
        		workplace = home;
        		++unschooledCount;
        	}
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
