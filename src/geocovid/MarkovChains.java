package geocovid;

/**
 * Matrices de 4x4x4 - Probabilidades sobre 1000. 4 periodos del dia X 4
 * posiciones actuales X 4 probabilidades de lugares.<p>
 * MaxiF: La probabilidad de la cadena de markov de movimiento temporal es un
 * arreglo que: probabilidadTMMC[P,i,j], donde P es el periodo del dia (8-12 12-16 16-20 20-24hs)
 * i es el nodo de donde sale, y j es el nodo a donde va.<p>
 * El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros
 * (supermercados, farmacias, etc) Ej: probabilidadTMMC[1][1][2] es la
 * probabilidad de que en el periodo 1 salga del trabajo 1 al lugar de ocio 2</i>
 */
public final class MarkovChains {
	
	public static final int YOUNG_DEFAULT_TMMC[][][] = {
		{ {  75,850, 50, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25, 25 }, { 550,300,100, 50 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 } },
		{ { 175,550,250, 25 }, {  50,850, 75, 25 }, { 200,350,400, 50 }, { 200,350,300,150 } },
		{ { 525,100,275,100 }, { 325,150,225,300 }, { 575,  0,325,100 }, { 525,  0,225,250 } } };
	public static final int ADULT_DEFAULT_TMMC[][][] = {
		{ {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 200,575,200, 25 }, { 100,850, 25, 25 }, { 200,675,100, 25 }, { 200,675,100, 25 } },
		{ { 525,150,225,100 }, { 225,250,225,300 }, { 575,100,225,100 }, { 525,  0,225,250 } } };
	public static final int HIGHER_DEFAULT_TMMC[][][] = {
		{ { 700,0,125,175 }, { 1000,0,0,0 }, { 700,0,125,175 }, { 700,0,125,175 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 800,0,175, 25 }, { 1000,0,0,0 }, { 700,0,300,  0 }, { 700,0,  0,300 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 950,0, 25, 25 }, { 950,0, 25, 25 } } };
	/**	Matrices de markov en tiempos normales */
	public static final int[][][][] DEFAULT_TMMC = {YOUNG_DEFAULT_TMMC, YOUNG_DEFAULT_TMMC, ADULT_DEFAULT_TMMC, ADULT_DEFAULT_TMMC, HIGHER_DEFAULT_TMMC};
	
	/** Matriz modificada para los Humanos que viven afuera. */
	public static final int TRAVELER_DEFAULT_TMMC[][][] = {
		{ {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 950,  0, 25, 25 }, { 850,  0,100, 50 }, { 820,  0, 90, 90 }, { 820,  0, 90, 90 } },
		{ { 200,575,200, 25 }, { 100,850, 25, 25 }, { 200,675,100, 25 }, { 200,675,100, 25 } },
		{ {1000,  0,  0,  0 }, {1000,  0,  0,  0 }, {1000,  0,  0,  0 }, {1000,  0,  0,  0 } } };
	/** Matriz modificada para los Humanos que viven afuera, para fines de semana. */
	public static final int TRAVELER_WEEKEND_TMMC[][][] = {
		{ { 350,400, 75,175 }, { 350,400, 75,175 }, { 350,400, 75,175 }, { 350,400, 75,175 } },
		{ { 950,  0, 50, 50 }, { 850,  0,100, 50 }, { 820,  0, 90, 90 }, { 820,  0, 90, 90 } },
		{ { 400,225,275,100 }, { 475,325,100,100 }, { 450,275,175,100 }, { 450,275,175,100 } },
		{ { 975,  0, 25,  0 }, { 975,  0, 25,  0 }, { 975,  0, 25,  0 }, { 975,  0, 25,  0 } } };
	
	/** Matriz modificada para los Humanos turistas. */
	public static final int TOURIST_DEFAULT_TMMC[][][] = {
		{ { 200,0,400,400 }, { 1000,0,0,0 }, { 200,0,400,400 }, { 200,0,400,400 } },
		{ { 900,0, 50, 50 }, { 1000,0,0,0 }, { 700,0,200,100 }, { 700,0,200,100 } },
		{ { 350,0,350,300 }, { 1000,0,0,0 }, { 300,0,400,300 }, { 300,0,400,300 } },
		{ { 600,0,250,150 }, { 1000,0,0,0 }, { 450,0,250,300 }, { 450,0,300,250 } } };
	
	/** Matriz modificada para los Humanos que se aislan por ser sintomaticos. */
	public static final int ISOLATED_TMMC[][][] = {
		{ { 999, 0, 0, 1 }, {1000, 0, 0, 0 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
		{ { 999, 0, 0, 1 }, {1000, 0, 0, 0 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
		{ { 999, 0, 0, 1 }, {1000, 0, 0, 0 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
		{ { 999, 0, 0, 1 }, {1000, 0, 0, 0 }, {1000, 0, 0, 0 }, {1000, 0, 0, 0 } } };
	
	/** Diferencia en actividades los fines de semana. */
	public static final int WEEKEND_DIFF[][][] = {
		{ { 175,-225, 0,50 }, { 0,0,0,0 }, { 175,-225, 0,50 }, { 175,-225, 0,50 } },
		{ {   5, -25,10,10 }, { 0,0,0,0 }, { -40, -10,25,25 }, { -40, -10,25,25 } },
		{ {  25,-175,75,75 }, { 0,0,0,0 }, {  25,-175,75,75 }, {  25,-175,75,75 } },
		{ {  50,-100,50, 0 }, { 0,0,0,0 }, {   0,   0, 0, 0 }, {   0,   0, 0, 0 } } };
	
	public static final int YOUNG_SEC11_DIFF[][][] = {
		{ { 200,-200,  0,0 }, { 0,0,0,0 }, { 200,-200,  0,0 }, { 200,-200,  0,0 } },
		{ { -50,  50,  0,0 }, { 0,0,0,0 }, { -50,  50,  0,0 }, { -50,  50,  0,0 } },
		{ { -25, -25, 50,0 }, { 0,0,0,0 }, { -25, -25, 50,0 }, { -25, -25, 50,0 } },
		{ {-100,   0,100,0 }, { 0,0,0,0 }, {-100,   0,100,0 }, {-100,   0,100,0 } } };
	public static final int ADULT_SEC11_DIFF[][][] = {
		{ { 200,-200, 0,0 }, { 0,0,0,0 }, { 200,-200, 0,0 }, { 200,-200, 0,0 } },
		{ { -50,  50, 0,0 }, { 0,0,0,0 }, { -50,  50, 0,0 }, { -50,  50, 0,0 } },
		{ { -25, -25,50,0 }, { 0,0,0,0 }, { -25, -25,50,0 }, { -25, -25,50,0 } },
		{ { -75,   0,75,0 }, { 0,0,0,0 }, { -75,   0,75,0 }, { -75,   0,75,0 } } };
	public static final int HIGHER_SEC11_DIFF[][][] = {
		{ { 50,0,-50,0 }, { 0,0,0,0 }, { 50,0,-50,0 }, { 50,0,-50,0 } },
		{ {-25,0, 25,0 }, { 0,0,0,0 }, {-25,0, 25,0 }, {-25,0, 25,0 } },
		{ {-50,0, 50,0 }, { 0,0,0,0 }, {-50,0, 50,0 }, {-50,0, 50,0 } },
		{ {-10,0, 10,0 }, { 0,0,0,0 }, {-10,0, 10,0 }, {-10,0, 10,0 } } };
	/** Diferencia en matrices para habitantes de seccional tipo 11 */
	public static final int[][][][] SEC11_DIFF = {YOUNG_SEC11_DIFF, YOUNG_SEC11_DIFF, ADULT_SEC11_DIFF, ADULT_SEC11_DIFF, HIGHER_SEC11_DIFF};
	
	public static final int YOUNG_JUNE_TMMC[][][] = {
		{ { 275,650, 50, 25 }, {  25,900, 50,25 }, {  25,900, 50,25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25, 25 }, { 550,300,100,50 }, { 800, 10, 95,95 }, { 800, 10, 95, 95 } },
		{ { 250,400,250,100 }, { 225,625, 75,75 }, { 300,300,350,50 }, { 300,300,250,150 } },
		{ { 725,100,100, 75 }, { 725,150, 50,75 }, { 825,  0,100,75 }, { 850,  0, 75, 75 } } };
	public static final int ADULT_JUNE_TMMC[][][] = {
		{ { 200,750, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25,25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90,90 }, { 700,120, 90, 90 } },
		{ { 250,450,200,100 }, { 225,675, 25, 75 }, { 300,575,100,25 }, { 300,575,100, 25 } },
		{ { 700,150, 50,100 }, { 700,150, 50,100 }, { 825, 50, 50,75 }, { 850,  0, 50,100 } } };
	public static final int HIGHER_JUNE_TMMC[][][] = {
		{ { 800,0,75,125 }, { 1000,0,0,0 }, { 800,0, 75,125 }, { 800,0,75,125 } },
		{ { 950,0,25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0,75, 75 } },
		{ { 900,0,75, 25 }, { 1000,0,0,0 }, { 900,0,100,  0 }, { 850,0, 0,150 } },
		{ { 990,0, 5,  5 }, { 1000,0,0,0 }, { 990,0,  5,  5 }, { 990,0, 5,  5 } } };
	/** Matrices de markov del mes de Junio / Julio 2020 */
	public static final int[][][][] JUNE_TMMC = {YOUNG_JUNE_TMMC, YOUNG_JUNE_TMMC, ADULT_JUNE_TMMC, ADULT_JUNE_TMMC, HIGHER_JUNE_TMMC};
	
	public static final int YOUNG_AUGUST_TMMC[][][] = {
		{ { 225,700, 50,25 }, {  25,900, 50, 25 }, {  25,900, 50,25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25,25 }, { 550,300,100, 50 }, { 800, 10, 95,95 }, { 800, 10, 95, 95 } },
		{ { 225,450,250,75 }, { 150,700, 75, 75 }, { 300,300,350,50 }, { 300,300,250,150 } },
		{ { 625,100,200,75 }, { 575,150,125,150 }, { 725,  0,200,75 }, { 725,  0,125,150 } } };
	public static final int ADULT_AUGUST_TMMC[][][] = {
		{ { 150,800, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 200,500,200,100 }, { 175,725, 25, 75 }, { 275,600,100, 25 }, { 275,600,100, 25 } },
		{ { 600,150,150,100 }, { 600,150,100,150 }, { 700, 50,150,100 }, { 700,  0,150,150 } } };
	public static final int HIGHER_AUGUST_TMMC[][][] = {
		{ { 750,0,100,150 }, { 1000,0,0,0 }, { 750,0,100,150 }, { 750,0,100,150 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 875,0,100, 25 }, { 1000,0,0,0 }, { 850,0,150,  0 }, { 800,0,  0,200 } },
		{ { 975,0, 10, 15 }, { 1000,0,0,0 }, { 975,0, 10, 15 }, { 975,0, 10, 15 } } };
	/** Matrices de markov del mes de Agosto / Septiembre 2020 */
	public static final int[][][][] AUGUST_TMMC = {YOUNG_AUGUST_TMMC, YOUNG_AUGUST_TMMC, ADULT_AUGUST_TMMC, ADULT_AUGUST_TMMC, HIGHER_AUGUST_TMMC};
	
	public static final int YOUNG_OCTOBER_TMMC[][][] = {
		{ { 200,725, 50,25 }, {  25,900, 50, 25 }, {  25,900, 50,25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25,25 }, { 550,300,100, 50 }, { 800, 10, 95,95 }, { 800, 10, 95, 95 } },
		{ { 200,475,250,75 }, { 150,725, 75, 50 }, { 275,325,350,50 }, { 275,325,250,150 } },
		{ { 600,100,225,75 }, { 425,150,175,250 }, { 675,  0,250,75 }, { 600,  0,175,225 } } };
	public static final int ADULT_OCTOBER_TMMC[][][] = {
		{ { 125,825, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 175,525,200,100 }, { 150,750, 25, 75 }, { 250,625,100, 25 }, { 250,625,100, 25 } },
		{ { 600,150,150,100 }, { 475,150,200,175 }, { 650, 50,200,100 }, { 650,  0,200,150 } } };
	public static final int HIGHER_OCTOBER_TMMC[][][] = {
		{ { 725,0,100,175 }, { 1000,0,0,0 }, { 725,0,100,175 }, { 725,0,100,175 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 850,0,125, 25 }, { 1000,0,0,0 }, { 800,0,200,  0 }, { 800,0,  0,200 } },
		{ { 965,0, 15, 20 }, { 1000,0,0,0 }, { 965,0, 15, 20 }, { 965,0, 15, 20 } } };
	/** Matrices de markov del mes de Octubre 2020 */
	public static final int[][][][] OCTOBER_TMMC = {YOUNG_OCTOBER_TMMC, YOUNG_OCTOBER_TMMC, ADULT_OCTOBER_TMMC, ADULT_OCTOBER_TMMC, HIGHER_OCTOBER_TMMC};
	
	public static final int YOUNG_NOVEMBER_TMMC[][][] = {
		{ { 200,725, 50,25 }, {  25,900, 50, 25 }, {  25,900, 50,25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25,25 }, { 550,300,100, 50 }, { 800, 10, 95,95 }, { 800, 10, 95, 95 } },
		{ { 200,475,250,75 }, { 150,725, 75, 50 }, { 275,325,350,50 }, { 275,325,250,150 } },
		{ { 575,100,250,75 }, { 375,150,200,275 }, { 625,  0,300,75 }, { 575,  0,200,225 } } };
	public static final int ADULT_NOVEMBER_TMMC[][][] = {
		{ { 125,825, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 175,525,200,100 }, { 150,750, 25, 75 }, { 250,625,100, 25 }, { 250,625,100, 25 } },
		{ { 550,150,200,100 }, { 425,175,200,200 }, { 625, 75,200,100 }, { 625,  0,200,175 } } };
	public static final int HIGHER_NOVEMBER_TMMC[][][] = {
		{ { 710,0,115,175 }, { 1000,0,0,0 }, { 710,0,115,175 }, { 710,0,115,175 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 850,0,125, 25 }, { 1000,0,0,0 }, { 750,0,250,  0 }, { 775,0,  0,225 } },
		{ { 965,0, 15, 20 }, { 1000,0,0,0 }, { 965,0, 15, 20 }, { 965,0, 15, 20 } } };
	/** Matrices de markov del mes de Noviembre 2020 */
	public static final int[][][][] NOVEMBER_TMMC = {YOUNG_NOVEMBER_TMMC, YOUNG_NOVEMBER_TMMC, ADULT_NOVEMBER_TMMC, ADULT_NOVEMBER_TMMC, HIGHER_NOVEMBER_TMMC};
	
	public static final int YOUNG_HOLIDAYS_TMMC[][][] = {
		{ { 175,725, 75, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25, 25 }, { 550,300,100, 50 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 } },
		{ { 150,500,300, 50 }, { 100,725,125, 50 }, { 200,325,425, 50 }, { 200,325,325,150 } },
		{ { 550,100,250,100 }, { 350,150,225,275 }, { 600,  0,300,100 }, { 550,  0,225,225 } } };
	public static final int ADULT_HOLIDAYS_TMMC[][][] = {
		{ {  75,825, 50, 50 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 150,525,225,100 }, { 150,750, 50, 50 }, { 200,625,125, 50 }, { 200,625,125, 50 } },
		{ { 525,150,225,100 }, { 225,250,225,300 }, { 575,100,225,100 }, { 525,  0,225,250 } } };
	public static final int HIGHER_HOLIDAYS_TMMC[][][] = {
		{ { 700,0,125,175 }, { 1000,0,0,0 }, { 700,0,125,175 }, { 700,0,125,175 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 800,0,150, 50 }, { 1000,0,0,0 }, { 675,0,300, 25 }, { 675,0, 25,300 } },
		{ { 960,0, 20, 20 }, { 1000,0,0,0 }, { 960,0, 20, 20 }, { 960,0, 20, 20 } } };
	/** Matrices de markov durante las fiestas */
	public static final int[][][][] HOLIDAYS_TMMC = {YOUNG_HOLIDAYS_TMMC, YOUNG_HOLIDAYS_TMMC, ADULT_HOLIDAYS_TMMC, ADULT_HOLIDAYS_TMMC, HIGHER_HOLIDAYS_TMMC};
	
	public static final int YOUNG_MARCH_TMMC[][][] = {
		{ { 125,800, 50, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 }, {  25,900, 50, 25 } },
		{ { 900, 50, 25, 25 }, { 550,300,100, 50 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 } },
		{ { 200,525,250, 25 }, {  50,850, 75, 25 }, { 200,350,400, 50 }, { 200,350,300,150 } },
		{ { 560,100,240,100 }, { 350,150,200,300 }, { 560,  0,340,100 }, { 550,  0,200,250 } } };
	public static final int ADULT_MARCH_TMMC[][][] = {
		{ {  75,875, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 }, {  25,925, 25, 25 } },
		{ { 900, 50, 25, 25 }, { 450,400,100, 50 }, { 700,120, 90, 90 }, { 700,120, 90, 90 } },
		{ { 225,550,200, 25 }, { 100,850, 25, 25 }, { 200,675,100, 25 }, { 200,675,100, 25 } },
		{ { 560,150,190,100 }, { 260,250,190,300 }, { 600,100,200,100 }, { 550,  0,200,250 } } };
	public static final int HIGHER_MARCH_TMMC[][][] = {
		{ { 715,0,110,175 }, { 1000,0,0,0 }, { 715,0,110,175 }, { 715,0,110,175 } },
		{ { 950,0, 25, 25 }, { 1000,0,0,0 }, { 850,0, 75, 75 }, { 850,0, 75, 75 } },
		{ { 825,0,150, 25 }, { 1000,0,0,0 }, { 725,0,275,  0 }, { 725,0,  0,275 } },
		{ { 960,0, 20, 20 }, { 1000,0,0,0 }, { 960,0, 20, 20 }, { 960,0, 20, 20 } } };
	
	/** Matrices de markov Marzo 2021 */
	public static final int[][][][] MARCH_TMMC = {YOUNG_MARCH_TMMC, YOUNG_MARCH_TMMC, ADULT_MARCH_TMMC, ADULT_MARCH_TMMC, HIGHER_MARCH_TMMC};
			
	// FIN DECLARACIONES MATRICES DE MARKOV //

	/**
	 * Suma o resta la matriz de fines de semana, a la matriz dada, y retorna el resultado.
	 * @param base matriz a modificar
	 * @param enable sumar o restar
	 * @return matriz base modificada
	 */
	public static int[][][] setWeekendDiff(int[][][] base, boolean enable) {
		int i,j,k;
		for (i = 0; i < 4; i++) {
			for (j = 0; j < 4; j++) {
				for (k = 0; k < 4; k++) {
					if (enable)
						base[i][j][k] += WEEKEND_DIFF[i][j][k];
					else
						base[i][j][k] -= WEEKEND_DIFF[i][j][k];
				}
			}
		}
		return base;
	}
	
	/**
	 * Suma a matriz base la matriz diff y retorna el resultado.
	 * @param base matriz a modificar
	 * @param diff matriz a sumar
	 * @return matriz base modificada
	 */
	public static int[][][] mergeChainsDiff(int[][][] base, int[][][] diff) {
		int[][][] result = new int[4][4][4];
		int temp;
		int i,j,k;
		for (i = 0; i < 4; i++) {
			for (j = 0; j < 4; j++) {
				for (k = 0; k < 4; k++) {
					temp = base[i][j][k] + diff[i][j][k];
					if (temp < 0 || temp > 1000)
						System.err.println(String.format("Error en matriz diff: [%d][%d][%d]", i, j, k));
					else
						result[i][j][k] = temp;
				}
			}
		}
		return result;
	}
	
	/**
	 * Imprime en formato Java en la consola, la diferencia entre las dos matrices dadas.
	 * @param oldc matriz a modificar
	 * @param newc matriz a restar
	 */
	public static void createChainsDiff(int[][][] oldc, int[][][] newc) {
		int[][][] result = new int[4][4][4];
		int i,j,k;
		for (i = 0; i < 4; i++)
			for (j = 0; j < 4; j++)
				for (k = 0; k < 4; k++)
					result[i][j][k] = oldc[i][j][k] - newc[i][j][k];
		printMarkovChains(result);
	}
	
	/**
	 * Imprime en formato Java en la consola, la matriz de markov dada.
	 * @param markov matriz a imprimir
	 */
	public static void printMarkovChains(int[][][] markov) {
		int i,j,k;
		for (i = 0; i < 4; i++) {
			System.out.print("{ ");
			for (j = 0; j < 4; j++) {
				System.out.print("{ ");
				for (k = 0; k < 4; k++) {
					System.out.print(String.format("%4d", markov[i][j][k]));
					if (k < 3)	System.out.print(",");
				}
				System.out.print(" }");
				if (j < 3)	System.out.print(", ");
			}
			if (i < 3)	System.out.println(" }, ");
			else		System.out.println(" } };");
		}
	}
}