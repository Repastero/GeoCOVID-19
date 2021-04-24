package geocovid;

/**
 * Atributos estaticos y finales comunes a todos los agentes.
 */
public final class DataSet {
	/** Cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** Espacios entre puestos de trabajo/estudio (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 3;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** Porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.6;
	/** Porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.85;
	
	/** Limite de aforo en Places por defecto durante cuarentena (valor minimo variable segun HUMANS_PER_SQUARE_METER) */ 
	public static final double DEFAULT_PLACES_CAP_LIMIT		= 4d;	// metros cuadrados de superficie util, por persona
	/** Multiplicador del limit de aforo en Places de ocio */
	public static final double ENTERTAINMENT_CAP_LIMIT_MOD	= 2d;
	
	/** Valor por cual se multiplica beta para obtener la chance de contagio al realizar una actividad fuera del contexto */
	public static final int OOC_CONTAGION_VALUE = 1500;	// aumentar para incrementar el contagio fuera del contexto
	
	/** Modificador de chance de contagio en lugares al aire libre */
	public static final double INFECTION_RATE_OUTSIDE_MOD = 0.5d; // 0.5 = 50% de adentro | 1 = sin modificar
	/** Modificador de chance de contagio en parcelas tipo seccional 11 */
	public static final double INFECTION_RATE_SEC11_MOD	  = 0.9d; // 0.9 = 90% del valor comun | 1 = sin modificar
	
	/** Radio que puede contagiar un infectado */
	public static final int	INFECTION_RADIUS			= 3;	// Radio en metros = (INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.375d;	// 15 min en ticks (1 tick = 40 min)
	
	/** Fraccion de reduccion de beta al estar en aislamiento */
	public static final double	ISOLATION_INFECTION_RATE_REDUCTION	= 0.80d;	// fraccion de uno (0 para desactivar)
	
	/** Tiempo antes del periodo sintomatico durante cual puede producir contacto estrecho (si closeContactsEnabled) */
	public static final int	CLOSE_CONTACT_INFECTIOUS_TIME	= 2*24;	// en ticks (2 dias)
	/** Tiempo de cuarentena preventivo al ser contacto estrecho o convivir con sintomatico (si prevQuarantineEnabled) */
	public static final int	PREVENTIVE_QUARANTINE_TIME		= 14*24;	// en ticks (14 dias)
	
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
	public static final int EXPOSED_PERIOD_MEAN			= 5*24;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION	= 1*24;	// 1 dia desvio standard
	
	/** Duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN_AG		= 5*24;	// 5 dias
	public static final int INFECTED_PERIOD_DEVIATION	= 1*24;	// 1 dia desvio standard
	
	/** Duracion de aislamiento de un infectado sintomatico o asintomatico conciente */
	public static final int	QUARANTINED_PERIOD_MEAN_AG	= 10*24;	// 10 dias
	public static final int QUARANTINED_PERIOD_DEVIATION= 2*24;	// 2 dia desvio standard
	
	/** Duracion en ICU luego de terminar periodo de infectado */
	public static final int EXTENDED_ICU_PERIOD			= 4*24;	// 4 dia mas desde infeccioso
	
	/** % de casos asintomaticos con respecto a los sintomatcos */
	public static final double[] ASX_INFECTIOUS_RATE	= {74d, 58d, 42d, 26d, 10d};	// sobre 100 
	
	/** Grupos etarios:<ul>
	 * <li>5-14 anos
	 * <li>15-24 anos
	 * <li>25-39 anos
	 * <li>40-64 anos
	 * <li>65 o mas anos</ul>
	 */
	public static final int AGE_GROUPS = 5; // cantidad de franjas etarias
	public static final String[] AGE_GROUP_LABELS				= {"Niños", "Jovenes", "Adultos", "Mayores", "Muy Mayores"};
	/** Porcentaje poblacion de cada grupo etario */
	public static final double[] HUMANS_PER_AGE_GROUP			= {14.40d, 17.92d, 22.88d, 31.10d, 13.70d}; // Abelardo Parana
	
	/** Distancia para que se considere contacto personal */
	public static final int	PERSONAL_DISTANCE				= 3; // Radio en metros = (PERSONAL_DISTANCE / (HUMANS_PER_SQUARE_METRE / 2)
	/** Habilitar que se cuenten los contactos personales */
	public static final boolean COUNT_INTERACTIONS			= false; // En false se reduce el tiempo de simulacion 25% aprox.
	/** Para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP	= {0.011d,  0.031d,  0.081d,  4.644d, 30.518d};	// sobre 100 - valores nuevos calculados por varias estadisticas
	/** % de casos en UTI que mueren al terminar la infeccion */
	public static final double	ICU_DEATH_RATE				= 65d;	// sobre 100
	
	/** Cantidad maxima de asientos;
	 * Fuente: http://servicios.infoleg.gob.ar/infolegInternet/anexos/45000-49999/48212/norma.htm +
	 * */
	public static final int	PUBLIC_TRANSPORT_MAX_SEAT				= 24;	// 
	
	/** Cantidad maxima de gente parada;
	 * Fuente: http://servicios.infoleg.gob.ar/infolegInternet/anexos/45000-49999/48212/norma.htm +
	 * */
	public static final int	PUBLIC_TRANSPORT_MAX_STILL				= 10;	// 
}
