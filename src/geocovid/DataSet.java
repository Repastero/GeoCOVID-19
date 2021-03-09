package geocovid;

public final class DataSet {
	
	/** Cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** Cantidad media de humanos por hogar (minimo 1) */
	public static final double[] HOUSE_INHABITANTS_MEAN	= {3.5, 5.5};
	/** Espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 4;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** Porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.5;
	/** Porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.75;
	
	/** Area en m2 para hogares */
	public static final int[] HOME_BUILDING_AREA = {125, 150};
	/** Area construida en m2 para hogares */
	public static final int[] HOME_BUILDING_COVERED_AREA = {100, 120};
	
	/** Limite de aforo en Places por defecto durante cuarentena (valor minimo variable segun HUMANS_PER_SQUARE_METER) */ 
	public static final double DEFAULT_PLACES_CAP_LIMIT		= 4d;	// metros cuadrados de superficie util, por persona
	/** Multiplicador del limit de aforo en Places de ocio */
	public static final double ENTERTAINMENT_CAP_LIMIT_MOD	= 2d;
	
	/** Modificador de chance de contagio en lugares al aire libre */
	public static final double INFECTION_RATE_OUTSIDE_MOD = 0.5d; // 0.5 = 50% de adentro | 1 = sin modificar
	/** Modificador de chance de contagio en parcelas tipo seccional 11 */
	public static final double INFECTION_RATE_SEC11_MOD	  = 0.9d; // 0.9 = 90% del valor comun | 1 = sin modificar
	
	/** Radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 4;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.2d;	// ticks
	
	/** Fraccion de reduccion de beta al estar en aislamiento */
	public static final double	ISOLATION_INFECTION_RATE_REDUCTION	= 0.80d;	// fraccion de uno (0 para desactivar)
	
	/** Fraccion de reduccion de beta al usar barbijo */
	private static double maskInfRateReduction;	// 30% segun bibliografia
	/** Si al aire libre se usa tapaboca */
	private static boolean wearMaskOutdoor;
	/** Si entre empleados usan tapaboca */
	private static boolean wearMaskWorkspace;
	
	/** Porcentaje de la poblacion que respeta el distanciamiento social */
	private static int socialDistPercentage;	// sobre 100 (0 para desactivar)
	/** Si al aire libre se respeta el distanciamiento social */
	private static boolean socialDistOutdoor;
	/** Si entre empleados respetan el distanciamiento social */
	private static boolean socialDistWorkspace;
	
	/** Tiempo antes del periodo sintomatico durante cual puede producir contacto estrecho (si closeContactsEnabled) */
	public static final int	CLOSE_CONTACT_INFECTIOUS_TIME	= 24;	// en ticks (2 dias)
	/** Tiempo de cuarentena preventivo al ser contacto estrecho o convivir con sintomatico (si prevQuarantineEnabled) */
	public static final int	PREVENTIVE_QUARANTINE_TIME		= 168;	// en ticks (14 dias)
	
	/** Si está habilitada la "pre infeccion" de contactos estrechos y cuarentena preventiva de los mismos */
	private static boolean closeContactsEnabled;
	/** Si está habilitada la cuarentena preventiva para las personas que conviven con un sintomatico */
	private static boolean prevQuarantineEnabled;
	
	/** % inicial de contagio al estar en contacto con una superficie contaminada */
	public static final int	CS_INFECTION_RATE			= 26;	// sobre 100
	/** % de contagio minimo, para seguir contando como superficie contaminada */
	public static final int	CS_MIN_INFECTION_RATE		= 12;	// sobre 100
	
	// Informacion de temperatura para calcular la duracion del virus en superficie contaminada
	public static final int	OUTDOORS_MIN_TEMPERATURE	= 10;	// temperatura media minima anual en exteriores
	public static final int	OUTDOORS_MAX_TEMPERATURE	= 35;	// temperatura media maxima anual en exteriores
	public static final int	INDOORS_MIN_TEMPERATURE		= 20;	// temperatura media minima anual en interiores
	public static final int	INDOORS_MAX_TEMPERATURE		= 30;	// temperatura media maxima anual en interiores
	
	/** Duracion de periodo de incubacion */
	public static final int EXPOSED_PERIOD_MEAN			= 60;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION	= 12;	// 1 dia desvio standard
	
	/** Duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN_AG		= 60;	// 5 dias
	public static final int INFECTED_PERIOD_DEVIATION	= 12;	// 1 dia desvio standard
	
	/** Duracion de aislamiento de un infectado sintomatico o asintomatico conciente */
	public static final int	QUARANTINED_PERIOD_MEAN_AG	= 120;	// 10 dias
	public static final int QUARANTINED_PERIOD_DEVIATION= 24;	// 2 dia desvio standard
	
	/** Duracion en ICU luego de terminar periodo de infectado */
	public static final int EXTENDED_ICU_PERIOD			= 48;	// 4 dia mas desde infeccioso
	
	/** % de casos asintomaticos con respecto a los sintomatcos */
	public static final double[] ASX_INFECTIOUS_RATE	= {74d, 58d, 42d, 26d, 10d};	// sobre 100 
	
	/** Grupos etarios:<ul>
	 * <li>5-14 anos
	 * <li>15-24 anos
	 * <li>25-39 anos
	 * <li>40-64 anos
	 * <li>65 o mas anos</ul>
	 */
	public static final int AGE_GROUPS = 5; //cantidad de franjas etarias
	public static final String[] AGE_GROUP_LABELS				= {"Niños", "Jovenes", "Adultos", "Mayores", "Muy Mayores"};
	public static final double[] HUMANS_PER_AGE_GROUP			= {14.40d, 17.92d, 22.88d, 31.10d, 13.70d}; // Abelardo Parana
	
	public static final double[][] LOCAL_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar dentro y trabajo/estudio fuera - Inventado
			{ 5d, 35d, 30d, 30d, 0d},	// Seccional 2
			{ 5d, 25d, 35d, 35d, 0d}	// Seccional 11
	};
	public static final double[][] FOREIGN_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar fuera y trabajo/estudio dentro - Inventado
			{10d, 30d, 30d, 30d, 0d},	// Seccional 2
			{10d, 20d, 35d, 35d, 0d}	// Seccional 11
	};
	
	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double[][][] OCCUPATION_PER_AGE_GROUP	= { // Fuente "El mapa del trabajo argentino 2019" - CEPE | INDEC - EPH 2020
			// Seccional 2 - 49.22% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  62d,  25d,  13d},	// 15-24
			{  14d,  76d,  10d},	// 25-39
			{   0d,  88d,  12d},	// 40-64
			{   0d,   0d, 100d}},	// 65+
			// Seccional 11 - 38.54% ocupados
			{{100d,   0d,   0d},	// 5-14
			{  57d,  11d,  32d},	// 15-24
			{  10d,  62d,  28d},	// 25-39
			{   0d,  72d,  28d},	// 40-64
			{   0d,   0d, 100d}}	// 65+
			// 61% ocupados entre las 3 franjas activas
	};
	
	public static final int[] WORKING_FROM_HOME	= {5, 7};	// 02: menos del 5% | 11: menos del 7%
	public static final int[] WORKING_OUTDOORS	= {5, 30};	// 02: S/D. Porcentaje ínfimo. | 11: 30%.
	
	/** Distancia para que se considere contacto personal */
	public static final int	PERSONAL_DISTANCE				= 4; // Radio en metros = (PERSONAL_DISTANCE / (HUMANS_PER_SQUARE_METRE / 2)
	/** Habilitar que se cuenten los contactos personales */
	public static final boolean COUNT_INTERACTIONS			= true; // En false se reduce el tiempo de simulacion 25% aprox.
	/** Para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	
	/** Para que en la grafica de porcentaje de tiempo en actividades, cuente el tiempo de estudio/trabajo como casa (si se queda) */
	public static final boolean COUNT_WORK_FROM_HOME_AS_HOME = true;
	
	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	public static final int[] TRAVEL_OUTSIDE_CHANCE	= {60, 20};	// Segun Abelardo es 75 y 25%, pero bajamos un poco por la epidemia
	
	/** % sobre 100 de que use el transporte publico al salir de seccional */
	public static final int	PUBLIC_TRANSPORT_CHANCE	= 8;
	/** Cantidad de unidades de transporte publico por seccional */
	public static final int	PUBLIC_TRANSPORT_UNITS	= 2;
	/** Cantidad de asientos en cada unidad de transorte publico */
	public static final int	PUBLIC_TRANSPORT_SEATS	= 20;
	
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
	 * @param minusRate fraccion de reduccion de infeccion (0...1)
	 */
	public static void setMaskEffectivity(double minusFrac) {
		maskInfRateReduction = minusFrac;
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
	
	/** @return <b>0...1</b> fraccion de reduccion de infeccion */
	public static double getMaskEffectivity()	{ return maskInfRateReduction; }
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
