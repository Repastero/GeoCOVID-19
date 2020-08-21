package geocovid;

public final class DataSet {
	public static final String SHP_FILE_PARCELS = "./data/sec2.shp";
	public static final String SHP_FILE_PLACES = "./data/sec2-places.shp";
	public static final String CSV_FILE_PLACES_PROPERTIES = "./data/sec2-places-markov.csv";
	
	/** cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METRE	= 4;
	/** cantidad media de humanos por hogar (minimo 1) */
	public static final int HOUSE_INHABITANTS_MEAN	= 4;
	/** espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 4;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.5;
	/** porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.7;
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE				= 22;	// sobre 100
	/** radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 4;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.2d;	// ticks 
	
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
	public static final int	INFECTED_PERIOD_MEAN_AG		= 60;	// 5 dias sintomatico
	public static final int INFECTED_PERIOD_DEVIATION	= 12;	// 1 dia desvio standard
	
	/** % de casos asintomaticos con respecto a los sintomatcos */
	public static final double[] ASX_INFECTIOUS_RATE	= {74d, 74d, 42d, 42d, 10d};	// sobre 100 
	
	/** Grupos etarios:<ul>
	 * <li>5-15 anos
	 * <li>16-25 anos
	 * <li>26-40 anos
	 * <li>41-64 anos
	 * <li>65 o mas anos</ul>
	 */
	public static final int AGE_GROUPS = 5; //cantidad de franjas etarias
	public static final double[] HUMANS_PER_AGE_GROUP			= {14.4d, 17.92d, 22.88d, 31.1d, 13.7d}; // Abelardo
	public static final double[] LOCAL_HUMANS_PER_AGE_GROUP		= {10d, 20d, 35d, 35d, 0d}; // Humanos con hogar dentro y trabajo/estudio fuera - Inventado
	public static final double[] FOREIGN_HUMANS_PER_AGE_GROUP	= {10d, 20d, 35d, 35d, 0d}; // Humanos con hogar fuera y trabajo/estudio dentro - Inventado
	
	/** % de estudiantes, trabajadores e inactivos (ama de casa/jubilado/pensionado/otros) segun grupo etario */
	public static final double OCCUPATION_PER_AGE_GROUP[][] = { // Datos del "El mapa del trabajo argentino 2019" - CEPE
			{ 100d,   0d,   0d},	// 5-15
			{  50d,  10d,  40d},	// 16-25	<- 100% mas de inactivos
			{  15d,  55d,  30d},	// 26-40	<- 100% mas de inactivos
			{   0d,  60d,  40d},	// 41-64	<- 100% mas de inactivos
			{   0d,   0d, 100d} };	// 65+
	
	/** para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS = false;
	/** grado de precision frente a longitud */
	public static final double DEGREE_PRECISION = 11.132d / 0.0001d; // Fuente https://en.wikipedia.org/wiki/Decimal_degrees
	/** radio en grados en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[] TRAVEL_RADIUS_PER_AGE_GROUP	= {750d / DEGREE_PRECISION, 1000d / DEGREE_PRECISION, -1d, -1d, 500d / DEGREE_PRECISION}; // metros div metros por grado (longitud)
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP		= {0.2d, 0.2d, 5d, 5d, 36d};	// sobre 100
	/** % de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE					= 42d;	// sobre 100
}
