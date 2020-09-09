package geocovid;

/**
 * Matrices de 4x4x4 - Probabilidades sobre 1000. 4 periodos del dia X 4
 * posiciones actuales X 4 probabilidades de lugares.<p>
 * <i>MaxiF: La probabilidad de la cadena de markov de movimiento temporal es un
 * arreglo que: probabilidadTMMC[P,i,j], donde P es el periodo del dia (8-11 11-14 14-17 17-20hs)
 * i es el nodo de donde sale, y j es el nodo a donde va.<p>
 * El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros
 * (supermercados, farmacias, etc) Ej: probabilidadTMMC[1][1][2] es la
 * probabilidad de que en el periodo 1 salga del trabajo 1 al lugar de ocio 2</i>
 */
public final class MarkovChains {
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. */
	public static final int CHILD_DEFAULT_TMMC[][][] = {
			{ { 50, 875, 25, 50 }, { 25, 900, 25, 50 }, { 25, 900, 25, 50 }, { 25, 900, 25, 50 } },
			{ { 900, 50, 25, 25 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 } },
			{ { 100, 600, 250, 50 }, { 25, 850, 100, 25 }, { 100, 350, 500, 50 }, { 100, 500, 300, 100 } },
			{ { 500, 100, 300, 100 }, { 300, 100, 300, 300 }, { 600, 0, 300, 100 }, { 500, 0, 200, 300 } } };

	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. */
	public static final int YOUNG_DEFAULT_TMMC[][][] = CHILD_DEFAULT_TMMC;

	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. */
	public static final int ADULT_DEFAULT_TMMC[][][] = {
			{ { 25, 925, 25, 25 }, { 25, 925, 25, 25 }, { 25, 925, 25, 25 }, { 25, 925, 25, 25 } },
			{ { 900, 50, 25, 25 }, { 650, 200, 100, 50 }, { 700, 110, 95, 95 }, { 700, 110, 95, 95 } },
			{ { 250, 500, 225, 25 }, { 100, 850, 25, 25 }, { 200, 675, 100, 25 }, { 200, 675, 100, 25 } },
			{ { 500, 150, 250, 100 }, { 50, 250, 400, 300 }, { 200, 100, 600, 100 }, { 500, 0, 250, 250 } } };

	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. */
	public static final int ELDER_DEFAULT_TMMC[][][] = ADULT_DEFAULT_TMMC;

	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. */
	public static final int HIGHER_DEFAULT_TMMC[][][] = {
			{ { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 } },
			{ { 900, 0, 25, 75 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 800, 0, 150, 50 }, { 300, 0, 450, 250 }, { 700, 0, 300, 0 }, { 700, 0, 0, 300 } },
			{ { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 } } };

	/** Matriz modificada para los Humanos que viven afuera. */
	public static final int TRAVELER_DEFAULT_TMMC[][][] = {
			{ { 0, 800, 100, 100 }, { 0, 800, 100, 100 }, { 0, 800, 100, 100 }, { 0, 800, 100, 100 } },
			{ { 900, 0, 50, 50 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 400, 200, 100 }, { 300, 500, 100, 100 }, { 300, 350, 250, 100 }, { 300, 400, 200, 100 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } } };

	/// *******************************CUARENTENA1*******************************
	// Confinamiento con salida a compras.
	public static final int CHILD_CONFINEMENT_TMMC[][][] = {
			{ { 935, 0, 1, 64 }, { 935, 0, 1, 64 }, { 935, 0, 1, 64 }, { 935, 0, 1, 64 } },
			{ { 880, 0, 1, 119 }, { 880, 0, 1, 119 }, { 880, 0, 1, 119 }, { 880, 0, 1, 119 } },
			{ { 880, 0, 1, 129 }, { 880, 0, 1, 119 }, { 880, 0, 1, 119 }, { 880, 0, 1, 119 } },
			{ { 935, 0, 1, 64 }, { 935, 0, 1, 64 }, { 935, 0, 1, 64 }, { 935, 0, 1, 64 } } };

	public static final int YOUNG_CONFINEMENT_TMMC[][][] = CHILD_CONFINEMENT_TMMC;

	public static final int ADULT_CONFINEMENT_TMMC[][][] = {
			{ { 904, 30, 1, 65 }, { 904, 30, 1, 65 }, { 904, 30, 1, 65 }, { 904, 30, 1, 65 } },
			{ { 854, 25, 1, 120 }, { 854, 25, 1, 120 }, { 854, 25, 1, 120 }, { 854, 25, 1, 120 } },
			{ { 854, 25, 1, 120 }, { 854, 25, 1, 120 }, { 854, 25, 1, 120 }, { 854, 25, 1, 120 } },
			{ { 904, 30, 1, 65 }, { 904, 30, 1, 65 }, { 904, 30, 1, 65 }, { 904, 30, 1, 65 } } };
	public static final int ELDER_CONFINEMENT_TMMC[][][] = ADULT_CONFINEMENT_TMMC;

	public static final int HIGHER_CONFINEMENT_TMMC[][][] = {
			{ { 850, 0, 0, 150 }, { 850, 0, 0, 150 }, { 850, 0, 0, 150 }, { 975, 0, 0, 25 } },
			{ { 950, 0, 0, 50 }, { 950, 0, 0, 50 }, { 950, 0, 0, 50 }, { 975, 0, 0, 25 } },
			{ { 950, 0, 0, 50 }, { 950, 0, 0, 50 }, { 950, 0, 0, 50 }, { 975, 0, 0, 25 } },
			{ { 900, 0, 0, 100 }, { 900, 0, 0, 100 }, { 900, 0, 0, 100 }, { 975, 0, 0, 25 } } };

	public static final int TRAVELER_CONFINEMENT_TMMC[][][] = {
			{ { 875, 60, 0, 65 }, { 875, 60, 0, 65 }, { 875, 60, 0, 65 }, { 875, 60, 0, 65 } },
			{ { 830, 50, 0, 120 }, { 830, 50, 0, 120 }, { 830, 50, 0, 120 }, { 830, 50, 0, 120 } },
			{ { 830, 50, 0, 120 }, { 830, 50, 0, 120 }, { 830, 50, 0, 120 }, { 830, 50, 0, 120 } },
			{ { 875, 60, 0, 65 }, { 875, 60, 0, 65 }, { 875, 60, 0, 65 }, { 875, 60, 0, 65 } } };

	public static final int INFECTED_CHILD_TMMC[][][] = {
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } } };

	public static final int INFECTED_YOUNG_TMMC[][][] = INFECTED_CHILD_TMMC;

	public static final int INFECTED_ADULT_TMMC[][][] = INFECTED_CHILD_TMMC;

	public static final int INFECTED_ELDER_TMMC[][][] = INFECTED_CHILD_TMMC;

	public static final int INFECTED_HIGHER_TMMC[][][] = INFECTED_CHILD_TMMC;

	public static final int INFECTED_TRAVELER_TMMC[][][] = INFECTED_CHILD_TMMC;

	///// *******************************CUARENTENA MAS SEVERA*******************************
	public static final int CHILD_HARD_CONFINEMENT_TMMC[][][] = {
			{ { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 } },
			{ { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 } },
			{ { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 } },
			{ { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 }, { 985, 10, 0, 5 } } };

	public static final int YOUNG_HARD_CONFINEMENT_TMMC[][][] = CHILD_HARD_CONFINEMENT_TMMC;

	public static final int ADULT_HARD_CONFINEMENT_TMMC[][][] = {
			{ { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 } },
			{ { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 } },
			{ { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 } },
			{ { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 }, { 940, 30, 0, 30 } } };

	public static final int ELDER_HARD_CONFINEMENT_TMMC[][][] = ADULT_HARD_CONFINEMENT_TMMC;

	public static final int HIGHER_HARD_CONFINEMENT_TMMC[][][] = {
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } } };

	/// *******************************CUARENTENA4*******************************
	// Confinamiento total de todos estados de sitio.// ES UN ESCENARIO MUY IREAL
	public static final int ACTIVE_FULL_DAY_CONFINEMENT_TMMC[][][] = {
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } } };

	public static final int TRAVELER_FULL_DAY_CONFINEMENT_TMMC[][][] = ACTIVE_FULL_DAY_CONFINEMENT_TMMC;

	public static final int ELDER_FULL_DAY_CONFINEMENT_TMMC[][][] = ACTIVE_FULL_DAY_CONFINEMENT_TMMC;

	/// *******************************FINES DE SEMANA*******************************
	public static final int CHILD_WEEKEND_TMMC[][][] = {
			{ { 500, 0, 400, 100 }, { 900, 0, 100, 0 }, { 300, 0, 675, 25 }, { 500, 0, 500, 0 } },
			{ { 575, 0, 300, 125 }, { 900, 0, 100, 0 }, { 300, 0, 675, 25 }, { 500, 0, 500, 0 } },
			{ { 500, 0, 475, 25 }, { 500, 0, 400, 100 }, { 300, 0, 675, 25 }, { 500, 0, 500, 0 } },
			{ { 500, 0, 475, 25 }, { 500, 0, 400, 100 }, { 300, 0, 675, 25 }, { 500, 0, 500, 0 } } };

	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. */
	public static final int YOUNG_WEEKEND_TMMC[][][] = CHILD_WEEKEND_TMMC;

	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. */
	public static final int ADULT_WEEKEND_TMMC[][][] = {
			{ { 700, 100, 100, 100 }, { 700, 100, 100, 100 }, { 300, 50, 500, 150 }, { 300, 50, 500, 150 } },
			{ { 500, 0, 400, 100 }, { 500, 0, 400, 100 }, { 500, 0, 400, 100 }, { 500, 0, 400, 100 } },
			{ { 500, 0, 400, 100 }, { 500, 0, 400, 100 }, { 500, 0, 400, 100 }, { 500, 0, 400, 100 } },
			{ { 700, 100, 100, 100 }, { 700, 100, 100, 100 }, { 300, 50, 500, 150 }, { 900, 0, 100, 0 } } };
	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. */
	public static final int ELDER_WEEKEND_TMMC[][][] = ADULT_WEEKEND_TMMC;

	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. */
	public static final int HIGHER_WEEKEND_TMMC[][][] = {
			{ { 950, 0, 50, 0 }, { 950, 0, 50, 0 }, { 950, 0, 50, 0 }, { 950, 0, 50, 0 } },
			{ { 650, 0, 300, 50 }, { 650, 0, 300, 50 }, { 350, 0, 300, 350 }, { 950, 0, 50, 0 } },
			{ { 650, 0, 300, 50 }, { 650, 0, 300, 50 }, { 350, 0, 300, 350 }, { 950, 0, 50, 0 } },
			{ { 950, 0, 50, 0 }, { 950, 0, 50, 0 }, { 950, 0, 50, 0 }, { 950, 0, 50, 0 } }, };

	/** Matriz modificada para los Humanos que trabajan afuera o viven afuera. */
	public static final int TRAVELER_WEEKEND_TMMC[][][] = {
			{ { 0, 1000, 0, 0 }, { 0, 1000, 0, 0 }, { 0, 1000, 0, 0 }, { 0, 1000, 0, 0 } },
			{ { 950, 0, 25, 25 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 0, 350, 350 }, { 300, 0, 350, 350 }, { 300, 0, 350, 350 }, { 300, 0, 350, 350 } },
			{ { 950, 0, 25, 25 }, { 100, 0, 450, 450 }, { 700, 0, 300, 0 }, { 700, 0, 0, 300 } } };
	// Matrices de dsitanciamineto social julio2020 parana seccional 2
	// Ni�os
	public static final int CHILD_PARANAS2_AGOSTO_TMMC[][][] = {
			{ { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 } },
			{ { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 } },
			{ { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 } },
			{ { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 } } };
	// Jovenes
	public static final int YOUNG_PARANAS2_AGOSTO_TMMC[][][] = {
			{ { 200, 500, 150, 150 }, { 200, 500, 150, 150 }, { 200, 500, 150, 150 }, { 200, 500, 150, 150 } },
			{ { 300, 400, 150, 150 }, { 300, 400, 150, 150 }, { 200, 500, 150, 150 }, { 200, 500, 150, 150 } },
			{ { 200, 500, 150, 150 }, { 200, 500, 150, 150 }, { 200, 500, 150, 150 }, { 200, 500, 150, 150 } },
			{ { 300, 50, 450, 200 }, { 300, 50, 450, 200 },{ 300, 50, 450, 200 }, { 300, 50, 450, 200 } } };

	// Adultos
	public static final int ADULT_PARANAS2_AGOSTO_TMMC[][][] = {
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 200, 600, 50, 150 }, { 200, 600, 50, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 400, 50, 350, 200 }, { 400, 50, 350, 200 }, { 500, 10, 300, 190 }, { 500, 10, 300, 190 } } };

	// Mayores
	public static final int ELDER_PARANAS2_AGOSTO_TMMC[][][] = {
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 200, 600, 50, 150 }, { 200, 600, 50, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 400, 50, 350, 200 }, { 400, 50, 350, 200 }, { 500, 10, 300, 190 }, { 500, 10, 300, 190 } } };

	// Mayores de 65
	public static final int HIGHER_PARANAS2_AGOSTO_TMMC[][][] = {
			{ { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 } },
			{ { 850, 0,  50, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 800, 0, 150,  50 }, { 300, 0, 450, 250 }, { 700, 0, 300,   0 }, { 700, 0,   0, 300 } },
			{ { 900, 0,  50,  50 }, { 900, 0,  50,  50 }, { 900, 0,  50,  50 }, { 900, 0,  50,  50 } } };
	
	// Matrices de dsitanciamineto social agosto2020 parana seccional 2
	// Ni�os
	public static final int CHILD_PARANAS2_JULIO_TMMC[][][] = {
			{ { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 300, 0, 460, 240 }, { 300, 0, 460, 240 } },
			{ { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 550, 0, 210, 240 }, { 550, 0, 210, 240 } },
			{ { 300, 0, 410, 290 }, { 300, 0, 410, 290 }, { 300, 0, 410, 290 }, { 300, 0, 410, 290 } },
			{ { 450, 0, 260, 290 }, { 450, 0, 260, 290 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 } } };
	// Jovenes
	public static final int YOUNG_PARANAS2_JULIO_TMMC[][][] = {
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }},
			{ { 200, 500, 150, 150 }, { 200, 500, 150, 150 }, { 100, 600, 200, 100 }, { 100, 600, 200, 100 } },
			{ { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 }},
			{ { 200, 50, 500, 250 }, { 200, 50, 500, 250 }, { 200, 50, 500, 250 }, { 200, 50, 500, 250 }} };

	// Adultos
	public static final int ADULT_PARANAS2_JULIO_TMMC[][][] = {

			{ { 100, 700, 100, 100 },{ 100, 700, 100, 100 }, { 100, 700, 100, 100 }, { 100, 700, 100, 100 }},
			{ { 200, 600, 50, 150 }, { 200, 600, 50, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 50, 700, 150, 100 },  { 50, 700, 150, 100 },  { 50, 700, 150, 100 },  { 50, 700, 150, 100 } },
			{ { 200, 50, 450, 300 }, { 200, 50, 450, 300 }, { 200, 10, 450, 340 }, { 200, 10, 450, 340 } } };

	// Mayores
	public static final int ELDER_PARANAS2_JULIO_TMMC[][][] = {
			{ { 100, 700, 100, 100 },{ 100, 700, 100, 100 }, { 100, 700, 100, 100 }, { 100, 700, 100, 100 }},
			{ { 200, 600, 50, 150 }, { 200, 600, 50, 150 }, { 100, 600, 150, 150 }, { 100, 600, 150, 150 } },
			{ { 50, 700, 150, 100 },  { 50, 700, 150, 100 },  { 50, 700, 150, 100 },  { 50, 700, 150, 100 } },
			{ { 200, 50, 450, 300 }, { 200, 50, 450, 300 }, { 200, 10, 450, 340 }, { 200, 10, 450, 340 } } };

	// Mayores de 65
	public static final int HIGHER_PARANAS2_JULIO_TMMC[][][] = {
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } } };

	// Matrices de dsitanciamineto social julio2020 parana seccional 11
	// Ni�os
	public static final int CHILD_PARANAS11_JULIO_TMMC[][][] = {
			{ { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 } },
			{ { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 400, 0, 360, 240 } },
			{ { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 } },
			{ { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 } } };
	// Jovenes
	public static final int YOUNG_PARANAS11_JULIO_TMMC[][][] = {
			{ { 600, 300, 50, 50 }, { 600, 300, 50, 50 }, { 600, 300, 50, 50 }, { 600, 300, 50, 50 } },
			{ { 500, 200, 200, 100 }, { 500, 200, 200, 100 }, { 200, 300, 300, 200 }, { 200, 300, 300, 200 } },
			{ { 400, 300, 200, 100 }, { 400, 300, 200, 100 }, { 400, 300, 200, 100 }, { 400, 300, 200, 100 } },
			{ { 400, 50, 300, 250 }, { 400, 50, 300, 250 }, { 400, 50, 300, 250 }, { 400, 50, 300, 250 } } };

	// Adultos
	public static final int ADULT_PARANAS11_JULIO_TMMC[][][] = {
			{ { 500, 400, 50, 50 }, { 500, 400, 50, 50 }, { 500, 400, 50, 50 }, { 500, 400, 50, 50 } },
			{ { 400, 300, 150, 150 }, { 400, 300, 150, 150 }, { 200, 350, 300, 150 }, { 200, 350, 300, 150 } },
			{ { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 } },
			{ { 400, 50, 300, 250 }, { 400, 50, 300, 250 }, { 400, 10, 300, 290 },{ 400, 10, 300, 290 } } };

	// Mayores
	public static final int ELDER_PARANAS11_JULIO_TMMC[][][] = {
			{ { 500, 400, 50, 50 }, { 500, 400, 50, 50 }, { 500, 400, 50, 50 }, { 500, 400, 50, 50 } },
			{ { 400, 300, 150, 150 }, { 400, 300, 150, 150 }, { 200, 350, 300, 150 }, { 200, 350, 300, 150 } },
			{ { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 } },
			{ { 400, 50, 300, 250 }, { 400, 50, 300, 250 }, { 400, 10, 300, 290 },{ 400, 10, 300, 290 } } };

	// Mayores de 65
	public static final int HIGHER_PARANAS11_JULIO_TMMC[][][] = {
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } } };
	// Matrices de dsitanciamineto social agosto2020 parana seccional 11
	// Ni�os
	public static final int CHILD_PARANAS11_AGOSTO_TMMC[][][] = {
			{ { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 }, { 750, 0, 110, 140 } },
			{ { 400, 0, 360, 240 }, { 400, 0, 360, 240 }, { 300, 0, 460, 240 }, { 300, 0, 460, 240 } },
			{ { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 } },
			{ { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 }, { 200, 0, 500, 300 } } };
	// Jovenes
	public static final int YOUNG_PARANAS11_AGOSTO_TMMC[][][] = {
			{ { 600, 300, 50, 50 },  { 600, 300, 50, 50 },  { 600, 300, 50, 50 },  { 600, 300, 50, 50 } },
			{ { 500, 300, 100, 100 }, { 500, 300, 100, 100 }, { 500, 300, 100, 100 },{ 500, 300, 100, 100 }},
			{ { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 } },
			{ { 300, 50, 500, 150 },  { 300, 50, 500, 150 },  { 300, 50, 500, 150 },  { 300, 50, 500, 150 }} };

	// Adultos
	public static final int ADULT_PARANAS11_AGOSTO_TMMC[][][] = {
			{ { 650, 300, 25, 25 }, { 650, 300, 25, 25 }, { 650, 300, 25, 25 },{ 650, 300, 25, 25 } },
			{ { 600, 250, 100, 50 },   { 600, 250, 100, 50 },   { 600, 250, 100, 50 },  { 600, 250, 100, 50 } },
			{ { 400, 300, 250, 50 }, { 400, 300, 250, 50 }, { 400, 300, 250, 50 }, { 400, 300, 250, 50 } },
			{ { 400, 50, 300, 250 },  { 400, 50, 300, 250 },  { 400, 50, 300, 250 },  { 400, 50, 300, 250 }} };

	// Mayores
	public static final int ELDER_PARANAS11_AGOSTO_TMMC[][][] = ADULT_PARANAS11_AGOSTO_TMMC;

	// Mayores de 65
	public static final int HIGHER_PARANAS11_AGOSTO_TMMC[][][] = {
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } } };
	
	//matrices 2 confinamineto agosto (simil fase oro verde)seccional2
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. */
	public static final int CHILD_DEFAULTS2_TMMC[][][] = {
			{ { 500, 0, 250, 250 },  { 500, 0, 250, 250 },  { 500, 0, 250, 250 },  { 500, 0, 250, 250 } },
			{ { 750, 0, 150, 100 }, { 750, 0, 150, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 0, 450, 450 }, { 225, 0, 500, 275 }, { 100, 0, 700, 200 }, { 200, 0, 600, 200 } },
			{ { 500, 0, 350, 150 }, { 300, 0, 350, 350 }, { 600, 0, 300, 100 }, { 500, 0, 200, 300 } } };

	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. */
	public static final int YOUNG_DEFAULTS2_TMMC[][][] = {
			{ { 100, 775, 125, 50 }, { 125, 800, 25, 50 }, { 125, 800, 25, 50 }, { 125, 800, 25, 50 } },
			{ { 400, 450, 125, 25 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 }, { 400, 400, 100, 100 } },
			{ { 100, 600, 250, 50 }, { 100, 600, 250, 50 }, { 100, 600, 250, 50 }, { 100, 600, 250, 50 } },
			{ { 200, 100, 500, 200 }, { 300, 100, 300, 300 }, { 400, 0, 300, 300 }, { 400, 0, 300, 300 } } };


	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. */
	public static final int ADULT_DEFAULTS2_TMMC[][][] = {
			{ { 125, 825, 25, 25 }, { 125, 825, 25, 25 }, { 125, 825, 25, 25 }, { 125, 825, 25, 25 } },
			{ { 500, 350, 50, 100 }, { 500, 350, 50, 100 }, { 400, 450, 100, 50 }, { 400, 450, 100, 50 } },
			{ { 50, 800, 125, 25 }, { 50, 800, 125, 25 }, { 50, 800, 125, 25 }, { 50, 800, 125, 25 } },
			{ { 300, 100, 350, 250 }, { 300, 100, 350, 250 }, { 300, 100, 300, 300 }, { 300, 100, 300, 300 } } };

	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. */
	public static final int ELDER_DEFAULTS2_TMMC[][][] = ADULT_DEFAULTS2_TMMC;

	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. */
	public static final int HIGHER_DEFAULTS2_TMMC[][][] = {
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 }, { 700, 0, 150, 150 } },
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } } };

	/** Matriz modificada para los Humanos que viven afuera. */
	public static final int TRAVELER_DEFAULTS2S11_TMMC[][][] = {
			{ { 100, 700, 100, 100 }, { 100, 700, 100, 100 }, { 100, 700, 100, 100 }, { 100, 700, 100, 100 } },
			{ { 900, 0, 50, 50 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 400, 200, 100 }, { 300, 500, 100, 100 }, { 300, 350, 250, 100 }, { 300, 400, 200, 100 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } } };

	// matrices 2 confinamineto agosto (simil fase oro verde)seccional11
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. */
	public static final int CHILD_DEFAULTS11_TMMC[][][] = {
			{ { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 900, 0, 50, 50 }, { 900, 0, 50, 50 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 0, 400, 300 }, { 300, 0, 400, 300 }, { 300, 0, 400, 300 }, { 300, 0, 400, 300 } },
			{ { 300, 0, 500, 200 }, { 300, 0, 500, 200 }, { 300, 0, 500, 200 }, { 300, 0, 500, 200 } } };

	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. */
	public static final int YOUNG_DEFAULTS11_TMMC[][][] = {
			{ { 700, 200, 50, 50 }, { 700, 200, 50, 50 }, { 700, 200, 50, 50 }, { 700, 200, 50, 50 } },
			{ { 600, 200, 100, 100 }, { 600, 200, 100, 100 }, { 600, 200, 100, 100 }, { 600, 200, 100, 100 } },
			{ { 500, 200, 200, 100 }, { 500, 200, 200, 100 }, { 500, 200, 200, 100 }, { 500, 200, 200, 100 } },
			{ { 300, 50, 400, 250 }, { 300, 50, 400, 250 }, { 300, 50, 400, 250 }, { 300, 50, 400, 250 } } };

	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. */
	public static final int ADULT_DEFAULTS11_TMMC[][][] = {
			{ { 650, 300, 25, 25 }, { 650, 300, 25, 25 }, { 650, 300, 25, 25 }, { 650, 300, 25, 25 } },
			{ { 600, 150, 150, 100 }, { 600, 150, 150, 100 }, { 600, 150, 150, 100 }, { 600, 150, 150, 100 } },
			{ { 500, 250, 150, 100 }, { 500, 250, 150, 100 }, { 500, 250, 150, 100 }, { 500, 250, 150, 100 } },
			{ { 500, 50, 350, 100 }, { 500, 50, 350, 100 }, { 500, 50, 350, 100 }, { 500, 50, 350, 100 } } };

	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. */
	public static final int ELDER_DEFAULTS11_TMMC[][][] = ADULT_DEFAULTS11_TMMC;

	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. */
	public static final int HIGHER_DEFAULTS11_TMMC[][][] = {
			{ { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 }, { 700, 0, 100, 200 } },
			{ { 900, 0, 25, 75 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 800, 0, 150, 50 }, { 300, 0, 450, 250 }, { 700, 0, 300, 0 }, { 700, 0, 0, 300 } },
			{ { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 } } };

	/** Matriz modificada para los Humanos que viven afuera. */
	public static final int TRAVELER_DEFAULTS11_TMMC[][][] = {
			{ { 0, 800, 100, 100 }, { 0, 800, 100, 100 }, { 0, 800, 100, 100 }, { 0, 800, 100, 100 } },
			{ { 900, 0, 50, 50 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 }, { 800, 0, 100, 100 } },
			{ { 300, 400, 200, 100 }, { 300, 500, 100, 100 }, { 300, 350, 250, 100 }, { 300, 400, 200, 100 } },
			{ { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 }, { 1000, 0, 0, 0 } } };
}