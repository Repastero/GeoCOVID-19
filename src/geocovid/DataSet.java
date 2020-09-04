package geocovid;

public final class DataSet {
	static final String SHP_FILE_PARCELS = "./data/ov-4326.shp";
	static final String SHP_FILE_PLACES = "./data/places-matched-4326.shp";

	/** Cantidad de Humanos locales (no salen a trabajar) */
	public static final int LOCAL_HUMANS			= 5000;
	/** Cantidad de Humanos que trabajan afuera */
	public static final int LOCAL_TRAVELER_HUMANS	= 1000;
	/** Cantidad de Humanos que viven afuera */
	public static final int FOREIGN_TRAVELER_HUMANS	= 1000;

	/** cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** cantidad media de humanos por hogar (minimo 1) */
	public static final int HOUSE_INHABITANTS_MEAN	= 4;
	/** espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 4;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.5;
	/** porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.85;
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE				= 26;	// sobre 100
	/** radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 4;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.2d;	// ticks 
	
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
	
	/** duracion de aislamiento de un infectado sintomatico */
	public static final int	QUARANTINED_PERIOD_MEAN_AG	= 120;	// 10 dias
	public static final int QUARANTINED_PERIOD_DEVIATION= 24;	// 2 dia desvio standard
	
	/** duracion en ICU luego de terminar periodo de infectado */
	public static final int EXTENDED_ICU_PERIOD	= 60;	// 5 dia mas desde infeccioso
	
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
	public static final double[] HUMANS_PER_AGE_GROUP			= {22d, 27d, 27d, 16d, 8d}; // Censo OV 2013
	public static final double[] LOCAL_HUMANS_PER_AGE_GROUP		= {20d, 30d, 30d, 20d, 0d}; // Humanos con hogar dentro y trabajo/estudio fuera
	public static final double[] FOREIGN_HUMANS_PER_AGE_GROUP	= {10d, 50d, 20d, 20d, 0d}; // Humanos con hogar fuera y trabajo/estudio dentro
	
	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double OCCUPATION_PER_AGE_GROUP[][] = { // Datos del "El mapa del trabajo argentino 2019" - CEPE
			{ 100d,   0d,   0d},	// 5-15
			{  50d,  30d,  20d},	// 16-25
			{  15d,  70d,  15d},	// 26-40
			{   0d,  80d,  20d},	// 41-64
			{   0d,   0d, 100d} };	// 65+
	
	/** % de trabajadores que trabajan en su casa */
	public static final int WORKING_FROM_HOME		= 4;
	/** % de trabajadores que trabajan al exterior */
	public static final int WORKING_OUTDOORS		= 5;
	
	/** para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS = false;
	/** grado de precision frente a longitud */
	public static final double DEGREE_PRECISION = 11.132d / 0.0001d; // Fuente https://en.wikipedia.org/wiki/Decimal_degrees
	/** radio en grados en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[] TRAVEL_RADIUS_PER_AGE_GROUP	= {750d / DEGREE_PRECISION, 1000d / DEGREE_PRECISION, -1d, -1d, 500d / DEGREE_PRECISION}; // metros div metros por grado (longitud)
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP		= {0.08d, 0.24d, 0.74d, 4.402d, 28.612d};	// sobre 100
	/** % de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE					= 42d;	// sobre 100
}
