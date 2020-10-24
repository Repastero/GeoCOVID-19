package geocovid;

public final class DataSet {
	/** Indice secciona: 0 = Secciona 2 | 1 = Seccional 11 */
	public static final int SECTORAL = 0;
	
	/** Limite de chance de contagio al realizar una actividad fuera del contexto (a menor limite, mayor chance) */
	public static final int[] OOC_CONTAGION = {100000, 100000};
	
	public static final String[] SHP_FILE_PARCELS	= {"./data/sec2.shp",		"./data/sec11.shp"};
	public static final String[] SHP_FILE_PLACES	= {"./data/sec2-places.shp","./data/sec11-places.shp"};
	public static final String CSV_FILE_PLACES_PROPERTIES = "./data/sec2+11-places-markov.csv";
	
	/** Entre <b>LOCAL_HUMANS</b> y <b>LOCAL_TRAVELER_HUMANS</b> tendria que dar 14383 para sec2 y 16885 para sec11 */
	public static final int[] LOCAL_HUMANS				= {6883, 11885};	// Cantidad de Humanos locales (no salen a trabajar)
	public static final int[] LOCAL_TRAVELER_HUMANS		= {7500,  5000};	// Cantidad de Humanos que trabajan afuera
	public static final int[] FOREIGN_TRAVELER_HUMANS	= {6000,  1000};	// Cantidad de Humanos que viven afuera
	
	public static final int[] LOCKDOWN_PHASES		= {0, 1, 3, 1, 2, 1, 4, 2,  5};	// Numero de fase en orden de cambio
	public static final int[] LOCKDOWN_PHASES_DAYS	= {0,38,52,59,66,80,91,94,101};	// Dia 0 = 12 de Junio
	/* Dia de inicio de cada fase
	 * 12 junio
	 * 20 julio
	 *  3 agosto
	 * 10 agosto
	 * 17 agosto
	 * 31 agosto
	 * 11 septiembre
	 * 14 septiembre
	 * 21 septiembre
	 */
	
	/** cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** cantidad media de humanos por hogar (minimo 1) */
	public static final double[] HOUSE_INHABITANTS_MEAN= {3.5, 5.5};
	/** espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 4;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.6;
	/** porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.75;
	
	/** limite de aforo en Places por defecto (valor minimo variable segun HUMANS_PER_SQUARE_METER) */ 
	public static final double DEFAULT_PLACES_CAP_LIMIT = 4d;	// metros cuadrados de superficie util, por persona
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE				= 26;	// sobre 100
	/** radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 4;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.2d;	// ticks
	
	/** % de reduccion de INFECTION_RATE al estar en aislamiento */
	public static final int	ISOLATION_INFECTION_RATE_REDUCTION	= 80;	// sobre 100 (0 para desactivar)
	
	/** % de reduccion de INFECTION_RATE al usar barbijo */
	private static int maskInfRateReduction;	// sobre 100 - 30 segun bibliografia (0 para desactivar)
	/** Si al aire libre se usa tapaboca */
	private static boolean wearMaskOutdoor;
	/** Si entre empleados usan tapaboca */
	private static boolean wearMaskWorkspace;
	
	/** % de la poblacion que respeta el distanciamiento social */
	private static int socialDistPercentage;	// sobre 100 (0 para desactivar)
	/** Si al aire libre se respeta el distanciamiento social */
	private static boolean socialDistOutdoor;
	/** Si entre empleados respetan el distanciamiento social */
	private static boolean socialDistWorkspace;
	
	/** tiempo antes del periodo sintomatico durante cual puede producir contacto estrecho (si closeContactsEnabled) */
	public static final int	CLOSE_CONTACT_INFECTIOUS_TIME	= 24;	// en ticks (2 dias)
	/** tiempo de cuarentena preventivo al ser contacto estrecho o convivir con sintomatico (si prevQuarantineEnabled) */
	public static final int	PREVENTIVE_QUARANTINE_TIME		= 168;	// en ticks (14 dias)
	
	/** Si est� habilitada la "pre infeccion" de contactos estrechos y cuarentena preventiva de los mismos */
	private static boolean closeContactsEnabled;
	/** Si est� habilitada la cuarentena preventiva para las personas que conviven con un sintomatico */
	private static boolean prevQuarantineEnabled;
	
	/** % inicial de contagio al estar en contacto con una superficie contaminada */
	public static final int	CS_INFECTION_RATE			= 26;	// sobre 100
	/** % de contagio minimo, para seguir contando como superficie contaminada */
	public static final int	CS_MIN_INFECTION_RATE		= 10;	// sobre 100
	
	// Informacion de temperatura para calcular la duracion del virus en superficie contaminada
	public static final int	OUTDOORS_MIN_TEMPERATURE	= 10;	// temperatura media minima anual en exteriores
	public static final int	OUTDOORS_MAX_TEMPERATURE	= 35;	// temperatura media maxima anual en exteriores
	public static final int	INDOORS_MIN_TEMPERATURE		= 20;	// temperatura media minima anual en interiores
	public static final int	INDOORS_MAX_TEMPERATURE		= 30;	// temperatura media maxima anual en interiores
	
	/** duracion de periodo de incubacion */
	public static final int EXPOSED_PERIOD_MEAN			= 60;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION	= 12;	// 1 dia desvio standard
	
	/** duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN_AG		= 60;	// 5 dias
	public static final int INFECTED_PERIOD_DEVIATION	= 12;	// 1 dia desvio standard
	
	/** duracion de aislamiento de un infectado sintomatico o asintomatico conciente */
	public static final int	QUARANTINED_PERIOD_MEAN_AG	= 120;	// 10 dias
	public static final int QUARANTINED_PERIOD_DEVIATION= 24;	// 2 dia desvio standard
	
	/** duracion en ICU luego de terminar periodo de infectado */
	public static final int EXTENDED_ICU_PERIOD			= 48;	// 4 dia mas desde infeccioso
	
	/** % de casos asintomaticos con respecto a los sintomatcos */
	public static final double[] ASX_INFECTIOUS_RATE	= {74d, 58d, 42d, 26d, 10d};	// sobre 100 
	
	/** Grupos etarios:<ul>
	 * <li>5-15 anos
	 * <li>16-25 anos
	 * <li>26-40 anos
	 * <li>41-64 anos
	 * <li>65 o mas anos</ul>
	 */
	public static final int AGE_GROUPS = 5; //cantidad de franjas etarias
	public static final String[] AGE_GROUP_LABELS				= {"Ni�os", "Jovenes", "Adultos", "Mayores", "Muy Mayores"};
	public static final double[] HUMANS_PER_AGE_GROUP			= {14.4d, 17.92d, 22.88d, 31.1d, 13.7d}; // Abelardo Parana
	
	public static final double[][] LOCAL_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar dentro y trabajo/estudio fuera - Inventado
			{20d, 20d, 30d, 30d, 0d},	// Seccional 2
			{15d, 15d, 35d, 35d, 0d}	// Seccional 11
	};
	public static final double[][] FOREIGN_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar fuera y trabajo/estudio dentro - Inventado
			{10d, 20d, 35d, 35d, 0d},	// Seccional 2
			{ 5d, 10d, 40d, 45d, 0d}	// Seccional 11
	};
	
	//En la Seccional 02 m�s del 40% trabaja y en la 11 ronda el 20%	-> 170% y 90% entre franjas 2,3,4
	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double[][][] OCCUPATION_PER_AGE_GROUP	= { // Datos del "El mapa del trabajo argentino 2019" - CEPE
			// Seccional 2
			{{100d,   0d,   0d},	// 5-15
			{  50d,  30d,  20d},	// 16-25
			{  15d,  65d,  20d},	// 26-40
			{   0d,  75d,  25d},	// 41-64
			{   0d,   0d, 100d}},	// 65+
			// Seccional 11
			{{100d,   0d,   0d},	// 5-15
			{  40d,  10d,  50d},	// 16-25
			{  10d,  35d,  55d},	// 26-40
			{   0d,  45d,  55d},	// 41-64
			{   0d,   0d, 100d}}	// 65+
	};
	
	public static final int[] WORKING_FROM_HOME		= {5, 7};	// 02: menos del 5% | 11: menos del 7%
	public static final int[] WORKING_OUTDOORS		= {5, 30};	// 02: S/D. Porcentaje �nfimo. | 11: 30%.
	
	/** distancia para que se considere contacto personal */
	public static final int	PERSONAL_DISTANCE				= 3;	// Radio en metros = (PERSONAL_DISTANCE / (HUMANS_PER_SQUARE_METRE / 2)
	/** para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	
	/** para que en la grafica de porcentaje de tiempo en actividades, cuente el tiempo de estudio/trabajo como casa (si se queda) */
	public static final boolean COUNT_WORK_FROM_HOME_AS_HOME = true;
	
	/** grado de precision frente a longitud */
	public static final double DEGREE_PRECISION				= 11.132d / 0.0001d; // Fuente https://en.wikipedia.org/wiki/Decimal_degrees
	/** radio en grados en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[] TRAVEL_RADIUS_PER_AGE_GROUP= {1000d / DEGREE_PRECISION, 1500d / DEGREE_PRECISION, -1d, -1d, 1000d / DEGREE_PRECISION}; // metros div metros por grado (longitud)
	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	public static final int[] TRAVEL_OUTSIDE_CHANCE	= {60, 20};	// Segun Abelardo es 75 y 25%, pero bajamos un poco por la epidemia
	
	/** % sobre 100 de que use el transporte publico al salir de seccional */
	public static final int	PUBLIC_TRANSPORT_CHANCE		= 3;
	/** Cantidad de unidades de transporte publico */
	public static final int	PUBLIC_TRANSPORT_UNITS		= 2;
	/** Cantidad de asientos en cada unidad de transorte publico */
	public static final int	PUBLIC_TRANSPORT_SEATS		= 20;
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP	= {0.011d,  0.031d,  0.081d,  4.644d, 30.518d};	// sobre 100 - valores nuevos calculados por varias estadisticas
	/** % de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE				= 42d;	// sobre 100
	
	/**
	 * Como la simulacion puede comenzar antes de la pandemia se inicia sin medidas de prevencion.
	 */
	public static void setDefaultValues() {
		setMaskValues(0, false, false);
		setSDValues(0, true, false);
		disableCloseContacts();
		disablePrevQuarantine();
	}
	
	/**
	 * @param minusRate porcentaje de reduccion de infeccion (0...100)
	 */
	public static void setMaskEffectivity(int minusRate) {
		maskInfRateReduction = minusRate;
	}
	
	/**
	 * @param minusRate porcentaje de reduccion de infeccion (0...100)
	 * @param enableOutdoor utilizar cubreboca en espacios abiertos
	 * @param enableWorkplace utilizar cubreboca entre trabajadores/estudiantes
	 */
	public static void setMaskValues(int minusRate, boolean enableOutdoor, boolean enableWorkplace) {
		maskInfRateReduction = minusRate;
		wearMaskOutdoor = enableOutdoor;
		wearMaskWorkspace = enableWorkplace;
	}
	
	/**
	 * <b>SETEA EL PORCENTAJE! NO EL ATRIBUTO DE LOS HUMANOS!</b>
	 * @param percentage porcentaje de Humanos que respeta la distancia (0...100)
	 */
	public static void setSDPercentage(int percentage) {
		socialDistPercentage = percentage;
	}
	
	/**
	 * @param percentage porcentaje de Huamanos que respeta la distancia (0...100)
	 * @param enableOutdoor respetar la distancia en espacios abiertos
	 * @param enableWorkplace respetar la distancia entre trabajadores/estudiantes
	 */
	public static void setSDValues(int percentage, boolean enableOutdoor, boolean enableWorkplace) {
		socialDistPercentage = percentage;
		socialDistOutdoor = enableOutdoor;
		socialDistWorkspace = enableWorkplace;
	}
	
	/** @return <b>0...100</b> porcentaje de reduccion de infeccion */
	public static int getMaskEffectivity()		{ return maskInfRateReduction; }
	/** @return <b>true</b> si se utiliza cubrebocas en espacios abiertos */
	public static boolean wearMaskOutdoor()		{ return wearMaskOutdoor; }
	/** @return <b>true</b> si trabajadores/estudiantes utilizan cubrebocas en su trabajo/estudio */
	public static boolean wearMaskWorkspace()	{ return wearMaskWorkspace; }
	
	/** @return <b>0...100</b> porcentaje de Humanos que respeta la distancia */
	public static int getSDPercentage()			{ return socialDistPercentage; }
	/** @return <b>true</b> si se respeta la distancia en espacios abiertos */
	public static boolean sDOutdoor()			{ return socialDistOutdoor; }
	/** @return <b>true</b> si trabajadores/estudiantes respetan la distancia en su trabajo/estudio */
	public static boolean sDWorkspace()			{ return socialDistWorkspace; }
	
	/** @return <b>true</b> si estan habilitados los contactos estrechos y la cuarentena preventiva de los mismos */
	public static boolean closeContactsEnabled(){ return closeContactsEnabled; }
	/** Habilita contactos estrechos y su cuarentena preventiva. */
	public static void enableCloseContacts()	{ closeContactsEnabled = true; }
	/** Deshabilita contactos estrechos y su cuarentena preventiva. */
	public static void disableCloseContacts()	{ closeContactsEnabled = false; }
	
	/** @return <b>true</b> si los habitantes del hogar de un sintomatico se ponen en cuarentena */
	public static boolean prevQuarantineEnabled(){ return prevQuarantineEnabled; }
	/** Habilita cuarentena preventiva en hogares de sintomaticos. */
	public static void enablePrevQuarantine()	{ prevQuarantineEnabled = true; }
	/** Deshabilita cuarentena preventiva en hogares de sintomaticos. */
	public static void disablePrevQuarantine()	{ prevQuarantineEnabled = false; }
}
