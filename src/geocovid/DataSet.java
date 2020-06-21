package geocovid;

public final class DataSet {
	/** Ver <b>corridas</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	public static int	corridas		= 50;
	/** Ver <b>cantHumanos</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	public static int	localHumans		= 6000;
	/** Ver <b>cantHumanosExtranjeros</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	public static int	foreignTravelerHumans	= 1000;
	/** Ver <b>cantHumanosLocales</b> en <a href="file:../../GeoCOVID-19.rs/parameters.xml">/GeoCOVID-19.rs/parameters.xml</a> */
	public static int	localTravelerHumans		= 1000;
	
	public static final int HOUSE_INHABITANTS_MEAN		= 4;	// cantidad media de humanos por hogar
	public static final int DISTANCE_BETWEEN_WORKERS	= 2;	// distancia en metros entre trabajadores/estudiantes
	public static final double BUILDING_AVAILABLE_AREA	= 0.8;	// porcentaje del area construida ocupable 
	//
	/** % de humanos locales que no trabaja por que no quiere o trabaja en su casa */
	public static final int	LAZY_HUMANS_PERCENTAGE		= 20;
	/** % de humanos locales que trabajan al exterior - fuera de edificios */
	public static final int	OUTSIDE_WORKERS_PERCENTAGE	= 5;
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE			= 60;	// % de contagio sobre 100 
	public static final int	INFECTION_RADIUS		= 2;	// radio en metros de contagio
	
	/** % de contagio al estar en contacto con una superficie contaminada */
	public static final int	CS_INFECTION_RATE		= 100;	// % de contagio inicial sobre 100
	public static final int	CS_MEAN_TEMPERATURE		= 25;	// temperatura media ambiente en grados centigrados
		
	/** duracion de periodo de incubacion */
	public static final int EXPOSED_PERIOD_MEAN		= 60;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION= 12;	// 1 dia desvio standard
	
	/** duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN_AG  = 60;	// 5 a 6 dias sintomatico
	public static final int INFECTED_PERIOD_DEVIATION= 12;	// 1 dia desvio standard
	
	/** % sobre 100 de casos asintomaticos con respecto a los sintomatcos*/
	public static final double[] ASX_INFECTIOUS_RATE = {74d, 74d, 42d, 42d, 10d};	// % total de ser infectado asintomatico
	
	/** % sobre 100 de cada grupo etario <p> 5-15 anos / 16-25 anos / 26-40 anos / 41-64 anos / 65 o mas anos */
	public static final int AGE_GROUPS = 5; //cantidad de franjas etarias
	public static final double[] HUMANS_PER_AGE_GROUP			= {22d, 27d, 27d, 16d, 8d}; // Censo OV 2013
	public static final double[] LOCAL_HUMANS_PER_AGE_GROUP		= {15d, 25d, 40d, 20d, 0d}; // Humanos con hogar dentro y trabajo/estudio fuera
	public static final double[] FOREIGN_HUMANS_PER_AGE_GROUP	= {15d, 30d, 35d, 20d, 0d}; // Humanos con hogar fuera y trabajo/estudio dentro
	
	/** radio en grados en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[] TRAVEL_RADIUS_PER_AGE_GROUP	= {750 / 111320, 1000d / 111320, -1d, -1d, 500d / 111320}; // metros div metros por grado (longitud)
	
	/** % sobre 100 de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP		= {0.2d, 0.2d, 5d, 5d, 36d};
	/** % sobre 100 de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE					= 42d;	// % que muere en UTI
}
