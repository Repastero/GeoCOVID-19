package geocovid;

public final class DataSet {
	/** Indice secciona: 0 = Secciona 2 | 1 = Seccional 11 */
	public static final int SECTORAL = 0;
	
	public static final String[] SHP_FILE_PARCELS	= {"./data/sec2.shp",		"./data/sec11.shp"};
	public static final String[] SHP_FILE_PLACES	= {"./data/sec2-places.shp","./data/sec11-places.shp"};
	public static final String CSV_FILE_PLACES_PROPERTIES = "./data/sec2+11-places-markov.csv";
	
	/** Entre <b>LOCAL_HUMANS</b> y <b>LOCAL_TRAVELER_HUMANS</b> tendria que dar 14383 para sec2 y 16885 para sec11 */
	public static final int[] LOCAL_HUMANS				= {6500, 10000};	// Cantidad de Humanos locales (no salen a trabajar)
	public static final int[] LOCAL_TRAVELER_HUMANS		= {8000,  6000};	// Cantidad de Humanos que trabajan afuera
	public static final int[] FOREIGN_TRAVELER_HUMANS	= {6000,  1000};	// Cantidad de Humanos que viven afuera
	
	public static final int[] LOCKDOWN_PHASES		= {0, 1, 2, 1};	// Numero de fase en orden de cambio
	public static final int[] LOCKDOWN_PHASES_DAYS	= {0, 5, 10, 15};	// Dia de inicio de cada fase
	
	/** cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** cantidad media de humanos por hogar (minimo 1) */
	public static final int[] HOUSE_INHABITANTS_MEAN= {4, 6};
	/** espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 4;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	/** metros cuadrados por persona para limitar aforo en Places (en espacios cerrados) */
	public static final int SQUARE_METERS_PER_HUMAN	= 4;	// TODO buscar segun disposicion municipal
	
	/** porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.6;
	/** porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.75;
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE				= 26;	// sobre 100
	/** radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 4;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.2d;	// ticks 
	/** % de reduccion de INFECTION_RATE al usar barbijo */
	public static final int	MASK_INFECTION_RATE_REDUCTION = 30;	// sobre 100
	
	/** % inicial de contagio al estar en contacto con una superficie contaminada */
	public static final int	CS_INFECTION_RATE			= 22;	// sobre 100
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
	public static final double[] HUMANS_PER_AGE_GROUP			= {14.4d, 17.92d, 22.88d, 31.1d, 13.7d}; // Abelardo Parana
	
	public static final double[][] LOCAL_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar dentro y trabajo/estudio fuera - Inventado
			{20d, 20d, 30d, 30d, 0d},	// Seccional 2
			{15d, 15d, 35d, 35d, 0d}	// Seccional 11
	};
	public static final double[][] FOREIGN_HUMANS_PER_AGE_GROUP	= {	// Humanos con hogar fuera y trabajo/estudio dentro - Inventado
			{10d, 20d, 35d, 35d, 0d},	// Seccional 2
			{ 5d, 10d, 40d, 45d, 0d}	// Seccional 11
	};
	
	//En la Seccional 02 más del 40% trabaja y en la 11 ronda el 20%	-> 170% y 90% entre franjas 2,3,4
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
	public static final int[] WORKING_OUTDOORS		= {5, 30};	// 02: S/D. Porcentaje ínfimo. | 11: 30%.
	/** para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	/** grado de precision frente a longitud */
	public static final double DEGREE_PRECISION				= 11.132d / 0.0001d; // Fuente https://en.wikipedia.org/wiki/Decimal_degrees
	/** radio en grados en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[] TRAVEL_RADIUS_PER_AGE_GROUP= {1000d / DEGREE_PRECISION, 1250d / DEGREE_PRECISION, -1d, -1d, 750d / DEGREE_PRECISION}; // metros div metros por grado (longitud)
	/** % sobre 100 de que al realizar actividades de ocio u otros salga del contexto */
	public static final int[] TRAVEL_OUTSIDE_CHANCE	= {60, 20};	// Segun Abelardo es 75 y 25%, pero bajamos un poco por la epidemia
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP	= {0.008d, 0.024d, 0.074d, 4.402d, 28.612d};	// sobre 100 - Pierinox
	/** % de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE				= 42d;	// sobre 100
}
