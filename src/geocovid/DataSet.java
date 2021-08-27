package geocovid;

/**
 * Atributos estaticos y finales comunes a todos los agentes.
 */
public final class DataSet {
	/** Cantidad maxima de humanos por m2 (minimo 1) */
	public static final int HUMANS_PER_SQUARE_METER	= 4;
	/** Espacios entre puestos de trabajo (minimo 1) */
	public static final int SPACE_BETWEEN_WORKERS	= 3;	// Distancia en metros = (SPACE_BETWEEN_WORKERS / (HUMANS_PER_SQUARE_METRE / 2)
	/** Espacios entre puestos de estudio (minimo 1) */
	public static final int SPACE_BETWEEN_STUDENTS	= 3;	// Distancia en metros = (SPACE_BETWEEN_STUDENTS / (HUMANS_PER_SQUARE_METRE / 2)
	
	/** Metros de largo y ancho de aulas */
	public static final int CLASSROOM_SIZE	= 4;
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
	public static final int DEFAULT_OOC_CONTAGION_VALUE = 16000;	// aumentar para incrementar el contagio fuera del contexto
	
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
	public static final int	CS_MIN_INFECTION_RATE		= 7;	// sobre 100
	
	/** Ticks que representan el tiempo de una semana */
	public static final int WEEKLY_TICKS = 7*24;
	/** Ticks que representan el tiempo que dura el fin de semana */
	public static final int WEEKEND_TICKS = 2*24;
	
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
	public static final String[] AGE_GROUP_LABELS			= {"NiÃ±os", "Jovenes", "Adultos", "Mayores", "Muy Mayores"};
	/** Porcentaje poblacion de cada grupo etario */
	public static final double[] HUMANS_PER_AGE_GROUP		= {14.40d, 17.92d, 22.88d, 31.10d, 13.70d}; // Abelardo Parana
	
	/** Distancia para que se considere contacto personal */
	public static final int	PERSONAL_DISTANCE				= 3; // Radio en metros = (PERSONAL_DISTANCE / (HUMANS_PER_SQUARE_METRE / 2)
	/** Habilitar que se cuenten los contactos personales */
	public static final boolean COUNT_INTERACTIONS			= false; // En false se reduce bastante el tiempo de simulacion
	/** Para que el reporte de "Contactos diarios" no tenga en cuenta los repetidos en el dia */
	public static final boolean COUNT_UNIQUE_INTERACTIONS	= false;
	
	/** % de casos graves que entra en UTI - de cada grupo etario */
	public static final double[] ICU_CHANCE_PER_AGE_GROUP	= {0.011d,  0.031d,  0.081d,  4.644d, 30.518d};	// sobre 100 - valores nuevos calculados por varias estadisticas
	/** % de casos en UTI que mueren al terminar periodo de internacion */
	public static final double	DEFAULT_ICU_DEATH_RATE		= 65d;	// sobre 100

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
	
	/**
	 * Proporciones de vacunas de las distintas marcas que han llegado a Argentina<p>
	 * Orden: AstraZeneca  COVISHIELD   Sinopharm     Sputnik V<p>
	 * fecha Actualizacion: 07/07/2021<p>
	 * FUENTE: datos.salud.gov<p>
	 */
	public static final double[]	PROPORTION_OF_VACCINES				=  {35.483654,  2.530984,   25.381372,   36.603989} ;
	
	/**
	 * Eficacia de la primera DOSE de las distintas vacunas<p>
	 * La eficacia de AstraZeneca y COVISHIELD es igual ya que son el mismo componente pero de distintos laboratorios
	 * fuentes:
	 *  AstraZeneca  COVISHIELD: Vacunas SARS-COV2 marzo 2021 - Angel LM de Francisco<p>
	 *  Sinopharm: <p>
	 *  Sputnik V: https://sputnikvaccine.com/esp/about-vaccine/clinical-trials/<p>
	 *  TODO valores provisorio, fuentes no tan claras
	 */
	public static final double[] 	MEAN_VACCINE_EFFICACY_ONE_DOSE					=	{76, 76, 15, 80};	
	/**
	 * Desvío de la Eficacia de la segunda DOSE de las distintas vacunas
	 */

	public static final double 		DS_VACCINE_EFFICACY_ONE_DOSE					=	5;
	/**
	 * Media de dias  y desvio estandar  de la inmmunización con una sola DOSE
	 * La media de los dias se tomo como referencia 
	 * TODO esto habria que re ver si cambia para las distintas vacunas
	 */
	public static final int    		MEAN_DAYS_AFTER_INMMUNIZATION_TO_BE_VACCINATED_ONE_DOSE=	22*24;
	public static final int    		DS_DAYS_AFTER_INMMUNIZATION_TO_BE_VACCINATED_ONE_DOSE	=	2*24;

	/**
	 * Proporciones de vacunas de las distintas marcas que han llegado a Argentina
	 * Orden: AstraZeneca  COVISHIELD   Sinopharm     Sputnik V
	 * fecha Actualizacion: 14/06/2021
	 * FUENTE: datos.salud.gov
	 */
	public static final double[]	PROPORTION_OF_VACCINES_TWO_DOSE				=  {24.4677981,   0.1860178,  39.7707092,  35.5754749};	
	
	/**
	 * Eficacia de la segunda DOSE de las distintas vacunas.<p>
	 * La eficacia de AstraZeneca y COVISHIELD es igual ya que son el mismo componente pero de distintos lugares.
	 * Tambien las probabilidades se calculan de acuerdo a las eficacias totales. Ej: Sputnik eficacia 91.8%, la primera tiene 80%, del 20% restante el 52% debe inmmunizarse con la segunda dosis 
	 * fuentes:
	 *  AstraZeneca  COVISHIELD: <p>
	 *  Sinopharm: https://www.sadi.org.ar/rss/item/1403-manual-del-vacunador-vacuna-sinopharm<p>
	 *  Sputnik V: https://sputnikvaccine.com/esp/about-vaccine/clinical-trials/<p>
	 *  TODO valores provisorio, fuentes no tan claras
	 */
	public static final double[] 	MEAN_VACCINE_EFFICACY_TWO_DOSE=	{64d, 64d, 71d, 52d};	
	
	/**
	 * Desvío de la Eficacia de la segunda DOSE de las distintas vacunas
	 */
	public static final double 		DS_VACCINE_EFFICACY_TWO_DOSE  =	5;
	
	/**
	 * Media de dias de la inmmunización con la segunda DOSE
	 * TODO esto habria que re ver si cambia para las distintas vacunas
	 */
	public static final int    		MEAN_DAYS_AFTER_INMMUNIZATION_TO_BE_VACCINATED_TWO_DOSE=	22*24;
	/**
	 * Desvio estandar  de la inmmunización con la segunda DOSE
	 */
	public static final int    		DS_DAYS_AFTER_INMMUNIZATION_TO_BE_VACCINATED_TWO_DOSE	=	2*24;

	/**
	 * Porcentaje de los agentes que han sido vacunados exitosamente y se desea que pasen al estado recuperados,
	 * el resto solo generaria inmunidad para llegar a un estado grave e ir a UTI
	 */
	public static final int    		HUMANS_PER_CHANGE_STATE_RECOVERED	= 70;
}
