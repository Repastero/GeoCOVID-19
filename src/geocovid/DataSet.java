package geocovid;

/**
 * Atributos estaticos y finales comunes a todos los agentes.
 */
public final class DataSet {
	/** Cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** Espacios entre puestos de trabajo (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 3;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Distancia entre pupitres dobles (minimo 2) */
	public static final int SPACE_BETWEEN_STUDENTS	= 4;	// Distancia en metros = (SPACE_BETWEEN_STUDENTS / HUMANS_PER_SQUARE_METRE)
	
	/** Metros de largo y ancho de aulas */
	public static final int CLASSROOM_SIZE	= 5;
	/** Cantidad de alumnos por aula */
	public static final int CLASS_SIZE		= 30;
	
	/** Porcentaje del area construida ocupable en casas (minimo .1) */
	public static final double BUILDING_AVAILABLE_AREA	= 0.6;
	/** Porcentaje del area construida ocupable en places (minimo .1) */
	public static final double WORKPLACE_AVAILABLE_AREA	= 0.8;
	
	/** Limite de aforo en Places por defecto durante cuarentena (valor minimo variable segun HUMANS_PER_SQUARE_METER) */ 
	public static final double DEFAULT_PLACES_CAP_LIMIT		= 4d;	// metros cuadrados de superficie util, por persona
	/** Multiplicador del limit de aforo en Places de ocio */
	public static final double ENTERTAINMENT_CAP_LIMIT_MOD	= 2d;
	
	/** Valor por cual se multiplica beta para obtener la chance de contagio al realizar una actividad fuera del contexto */
	public static final int DEFAULT_OOC_CONTAGION_VALUE = 160;	// aumentar para incrementar el contagio fuera del contexto
	
	/** Valor de beta base */
	public static final double INFECTION_RATE = 24.64d;
	
	/** Modificador beta de droplets en lugares al aire libre */
	public static final double DROPLET_OUTSIDE_MOD		= 0.4d;
	/** Modificador beta de droplet en lugares ventilados */
	public static final double DROPLET_VENTILATED_MOD	= 0.85d;
	
	/** Modificador beta de fomites en base a droplets */
	public static final double FOMITE_IR_MOD			= 0.5d;
	/** Modificador beta de fomites en lugares al aire libre */
	public static final double FOMITE_OUTSIDE_MOD		= 0.5d;
	
	/** Modificador beta de aerosol en base a droplets */
	public static final double AEROSOL_IR_MOD			= 0.05d;
	/** Modificador beta de aerosol en lugares al aire libre */
	public static final double AEROSOL_OUTSIDE_MOD		= 0.125d;
	/** Modificador beta de aerosol en lugares ventilados */
	public static final double AEROSOL_VENTILATED_MOD	= 0.5d;
	
	/** Modificador de chance de contagio en viviendas precarias */
	public static final double INFECTION_RATE_SEC11_MOD	  = 0.9d; // 0.9 = 90% del valor comun | 1 = sin modificar
	
	/** Radio que puede contagiar un infectado directamente por droplets */
	public static final int	DROPLET_INFECTION_RADIUS	= 3;	// Radio en metros = (DROPLET_INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Radio que puede contagiar un infectado via aerea por aerosol */
	public static final int	AEROSOL_INFECTION_RADIUS	= 5;	// Radio en metros = (AEROSOL_INFECTION_RADIUS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Tiempo de contacto que debe tener un infectado para contagiar */
	public static final double INFECTION_EXPOSURE_TIME	= 0.375d;	// 15 min en ticks (1 tick = 40 min)
	
	/** Fraccion de reduccion de beta al estar en aislamiento */
	public static final double	ISOLATION_INFECTION_RATE_REDUCTION	= 0.80d;	// fraccion de uno (0 para desactivar)
	
	/** Tiempo antes del periodo sintomatico durante cual puede producir contacto estrecho (si closeContactsEnabled) */
	public static final int	CLOSE_CONTACT_INFECTIOUS_TIME	= 2*24;	// en ticks (2 dias)
	/** Tiempo de cuarentena preventivo al ser contacto estrecho o convivir con sintomatico (si prevQuarantineEnabled) */
	public static final int	PREVENTIVE_QUARANTINE_TIME		= 14*24;	// en ticks (14 dias)
	
	/** Cantidad de droplets recibidos segun estado de markov y cercania fisica */
	public static final double[] STATE_DROPLET_VOLUME	= {1d, 0.5d, 0.8d, 0.2d};	// TODO a revisar
	
	/** % de contagio minimo, para seguir contando como superficie contaminada */
	public static final int	CS_MIN_INFECTION_RATE		= 10;	// sobre 100
	
	/** Ticks que representan el tiempo de una semana */
	public static final int WEEKLY_TICKS	= 7*24;
	/** Ticks que representan el tiempo que dura el fin de semana */
	public static final int WEEKEND_TICKS	= 2*24;
	
	/** Duracion de periodo de incubacion */
	public static final int EXPOSED_PERIOD_MEAN			= 5*24;	// 5 dias
	public static final int EXPOSED_PERIOD_DEVIATION	= 1*24;	// 1 dia desvio standard
	
	/** Duracion de periodo infectado sintomatico/asintomatico en ticks para todos */
	public static final int	INFECTED_PERIOD_MEAN		= 5*24;	// 5 dias
	public static final int INFECTED_PERIOD_DEVIATION	= 1*24;	// 1 dia desvio standard
	
	/** Duracion de aislamiento de un infectado sintomatico o asintomatico conciente */
	public static final int	QUARANTINED_PERIOD_MEAN		= 10*24; // 10 dias
	public static final int QUARANTINED_PERIOD_DEVIATION=  2*24; // 2 dia desvio standard
	
	/** Duracion de inmunidad por vacuna */
	public static final int VACCINE_IMMUNITY_PERIOD_MEAN		= 10*30*24; // 10 meses https://www.nature.com/articles/s41392-021-00686-1
	public static final int VACCINE_IMMUNITY_PERIOD_DEVIATION	=  1*30*24; // 1 meses
	/** Duracion de inmunidad natural */
	public static final int NATURAL_IMMUNITY_PERIOD_MEAN		= VACCINE_IMMUNITY_PERIOD_MEAN;
	public static final int NATURAL_IMMUNITY_PERIOD_DEVIATION	= VACCINE_IMMUNITY_PERIOD_DEVIATION;
	
	/** Duracion en ICU luego de terminar periodo de infectado */
	public static final int EXTENDED_ICU_PERIOD			= 9*24; // 9 dia mas desde infeccioso
	
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
	public static final String[] AGE_GROUP_LABELS			= {"Niños", "Jovenes", "Adultos", "Mayores", "Muy Mayores"};
	/** Porcentaje poblacion de cada grupo etario */
	public static final double[] HUMANS_PER_AGE_GROUP		= {17.167, 17.238, 23.467, 29.074, 13.054}; // Fuente: Proyeccion INTA 2021 - Entre Rios
	
	/** Distancia para que se considere contacto personal */
	public static final int	PERSONAL_DISTANCE				= 3; // Radio en metros = (PERSONAL_DISTANCE / (HUMANS_PER_SQUARE_METRE / 2)
	/** Habilitar que se cuenten los contactos personales */
	public static final boolean COUNT_INTERACTIONS			= false; // En false se reduce bastante el tiempo de simulacion
	/** Para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	
	/** % de casos graves que pueden derivar a UTI - de cada grupo etario */
	public static final double[] SEVERE_CASE_CHANCE_PER_AG	= {0.008, 0.024, 0.074, 4.402, 28.612}; // sobre 100 - valores calculados por Pierino
	
	/** % de casos que mueren antes de ingresar a UTI - de cada grupo etario */
	public static final double	DEFAULT_PREICU_DEATH_RATE	= 1d; // sobre 100
	/** % de casos graves en UTI que mueren al terminar periodo de internacion */
	public static final double	DEFAULT_ICU_DEATH_RATE		= 55d; // sobre 100
	
	/** Probabilidad de poseer una comorbilidad segun rango etario - sobre 1000 */
	public static final int[][] DISEASE_CHANCE_PER_AGE_GROUP	= {
		{ 93, 93, 93, 93, 93}, // Asma
		{  1, 29, 49,115,203}, // Diabetes
		{  1, 23, 26, 50, 78}, // EPOC
		{  1,126,197,379,620}, // Hipertension
		{173, 78,247,418,350}  // Obesidad
	};
	/** Modificador de casos graves por comorbilidad - Asma, Diabetes, EPOC, Hipertension, Obesidad */
	public static final double[] DISEASE_SEVERE_CASE_CHANCE_MOD	= {0.123, 0.310, 0.125, 0.268, 0.586};
		
	/** Cantidad tipos de vacunas */
	public static final int VACCINES_TYPES = 6;
	/** Nombres de tipos de vacunas */
	public static final String[] VACCINES_TYPES_NAME = {"AstraZeneca","Cansino","Moderna","Pfizer","Sinopharm","Sputnik"};
	/** Cantidad de dosis por tipo de vacuna */
	public static final int[] VACCINES_DOSES = {3, 1, 3, 3, 3, 3};
	
	/** Mediana de efectividad de vacunas por tipo y dosis */
	public static final int[][] VACCINES_EFFICACY_MEAN = {
			{63, 68, 92, 52, 65, 72}, // Primera dosis
			{82,  0, 94, 95, 80, 91}, // Segunda dosis
			{82,  0, 94, 95, 80, 91}, // Tercera dosis revisar
	};
	/** Desvio de efectividad de vacunas por dosis */
	public static final int[] VACCINES_EFFICACY_DEVIATION = {10, 5, 5};
	
	/** Tipos de vacuna combinables - 1er con 2nda dosis */
	public static final boolean[][] VACCINES_COMBO = {
		{false, false, false, true,  false, false}, // AstraZeneca (con Sputnik)
		{false, false, false, false, false, false}, // Cansino
		{false, false, false, false, false, false}, // Moderna
		{false, false, false, false, false, false}, // Pfizer
		{false, false, false, false, false, false}, // Sinopharm
		{true,  false, true,  false, false, false}, // Sputnik (con AstraZeneca, Moderna)
	};
	
	/** % de inmunizados completamente - en casos de vacunacion exitosa */
	public static final int VACCINE_IMMUNITY_CHANCE				= 75;
	
	/** Delay para ganar inmunidad por vacuna */
	public static final int[] VACCINE_IMMUNITY_DELAY_MEAN		= {14*24, 14*24, 14*24}; // 14 dias
	/** Desvio en delay para ganar inmunidad por vacuna */
	public static final int[] VACCINE_IMMUNITY_DELAY_DEVIATION	= { 1*24,  1*24, 1*24}; // 1 dia
	
	// Niveles de inmunidad
	public static final int IMMUNITY_LVL_NONE	= -1;
	public static final int IMMUNITY_LVL_LOW	= 0;
	public static final int IMMUNITY_LVL_MED	= 1;
	public static final int IMMUNITY_LVL_HIGH	= 2;
	//
	
	/** Modificador de chance infeccion al estar vacunado no exitosamente / exitosamente / inmunizado */
	public static final double[] VACCINE_INFECTION_CHANCE_MOD	= {0.3,  0.05, 0d}; // 30% - 5% - 0% (ver niveles de inmunidad)
	
	/** Modificador de chance caso grave al estar vacunado no exitosamente / exitosamente / inmunizado */
	public static final double[] VACCINE_SEVERE_CASE_CHANCE_MOD	= {0.1, 0.001, 0d}; // 10% - 0.1% - 0% (ver niveles de inmunidad)
	
	/** Ancho en metros de colectivo */
	public static final int	PUBLIC_TRANSPORT_UNIT_WIDTH		= 3;	// 2.6m carrozado, 3m para redondear
	/** Cantidad maxima de asientos<p>
	 * Fuente: http://servicios.infoleg.gob.ar/infolegInternet/anexos/45000-49999/48212/norma.htm
	 * http://leivag1991vehicles.blogspot.com/2018/10/metalpar-tronador-mk2-chasis-of1418.html
	 */
	public static final int	PUBLIC_TRANSPORT_MAX_SEATED		= 31;	// 
	/** Cantidad maxima de gente parada<p>
	 * Fuente: http://servicios.infoleg.gob.ar/infolegInternet/anexos/45000-49999/48212/norma.htm +
	 */
	public static final int	PUBLIC_TRANSPORT_MAX_STANDING	= 10;	// 
}
