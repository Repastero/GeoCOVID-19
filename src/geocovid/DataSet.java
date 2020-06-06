package geocovid;

public final class DataSet {
	static int			cantHumanosFijos			= 6000;	//cantidad de humanos que reside y trabaja
	static int			cantHumanosExtranjeros		= 1000;
	static int			cantHumanosLocales			= 1000;
	//
	public static final int HOUSE_INHABITANTS_MEAN = 4;
	//
	public static final int	HUMANOS_PORCENTAJE_DESEMPLEADO	= 10;	//porcentaje de humanos que no asisten a su lugar de trabajo
	public static final int	HUMANOS_PORCENTAJE_EXTERIOR		= 5;	//porcentaje de humanos que trabajan al exterior - fuera de edificios
	//
	
	/** % de contagio al estar en contacto con un infectado */
	public static final int	INFECTION_RATE			= 60;	// % de contagio sobre 100 
	public static final int	INFECTION_RADIUS		= 2;	// radio en metros de contagio
		
	/** duracion de periodo de incubacion */
	public static final int EXPOSED_PERIOD_MEAN		= 60;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION= 12;	// 1 dia desvio standard
	
	/** duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN_AG  = 60;	// 5 a 6 dias sintomatico
	public static final int INFECTED_PERIOD_DEVIATION= 12;	// 1 dia desvio standard
	
	/** % sobre 100 de casos asintomaticos con respecto a los sintomatcos*/
	public static final double[] ASX_INFECTIOUS_RATE= {74d, 42d, 10d};	// % 42 en total de ser infectado asintomatico
	
	/** % sobre 100 de cada grupo etario <p> 5 a 24 / 25 a 64 / > 65 anos */
	public static final double[]HUMANS_PER_AGE_GROUP			= {59d, 31d, 10d};
	/** radio en metros en los que se desplazan los humanos para ir a lugares de ocio u otros (no aplica a adultos) */
	public static final double[]TRAVEL_RADIUS_PER_AGE_GROUP		= {750d, -1d, 500d};
	/** % sobre 100 de casos graves que entra en UTI - de cada grupo etario */
	public static final double[]ICU_CHANCE_PER_AGE_GROUP		= {0.2d, 5d, 36d};
	/** % sobre 100 de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE					= 42d;	// % que muere en UTI
}