package geocovid;

/**
 * Matrices de 4x4x4 - Probabilidades sobre 1000. 4 periodos del dia X 4
 * posiciones actuales X 4 probabilidades de lugares.<p>
 * <i>MaxiF: La probabilidad de la cadena de markov de movimiento temporal es un
 * arreglo que: probabilidadTMMC[P,i,j], donde P es el periodo del dia (8-12 12-16 16-20 20-24hs)
 * i es el nodo de donde sale, y j es el nodo a donde va.<p>
 * El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros
 * (supermercados, farmacias, etc) Ej: probabilidadTMMC[1][1][2] es la
 * probabilidad de que en el periodo 1 salga del trabajo 1 al lugar de ocio 2</i>
 */
public final class MarkovChains {
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. Seccional 2. */
	public static final int CHILD_SEC2_DEFAULT_TMMC[][][] = {
			{ {  25,900, 25, 50 }, {  25,900, 25, 50 }, {  25,900, 25, 50 }, {  25,900, 25, 50 } },
			{ { 900, 50, 25, 25 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 }, { 800, 10, 95, 95 } },
			{ {  50,550,300,100 }, {  25,800,125, 50 }, {  50,300,450,200 }, {  50,450,300,200 } },
			{ { 500,100,300,100 }, { 500,100,200,200 }, { 700,  0,200,100 }, { 700,  0,100,200 } } };
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. Seccional 11. */
	public static final int CHILD_SEC11_DEFAULT_TMMC[][][] = {
			{ {  25,850, 75, 50 }, {  25,850, 75, 50 }, {  25,850, 75, 50 }, {  25,850, 75, 50 } },
			{ { 850, 50, 75, 25 }, { 750, 10,150, 90 }, { 750, 10,150, 90 }, { 750, 10,150, 90 } },
			{ {  50,550,300,100 }, {  25,700,200, 75 }, {  50,275,500,175 }, {  50,400,350,200 } },
			{ { 400,100,400,100 }, { 400,100,300,200 }, { 600,  0,300,100 }, { 600,  0,200,200 } } };
	
	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. Seccional 2. */
	public static final int YOUNG_SEC2_DEFAULT_TMMC[][][] = CHILD_SEC2_DEFAULT_TMMC;
	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. Seccional 11. */
	public static final int YOUNG_SEC11_DEFAULT_TMMC[][][] = CHILD_SEC11_DEFAULT_TMMC;
	
	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. Seccional 2. */
	public static final int ADULT_SEC2_DEFAULT_TMMC[][][] = {
			{ {  25,800, 25,150 }, {  25,800, 25,150 }, {  25,800, 25,150 }, {  25,800, 25,150 } },
			{ { 900, 50, 25, 25 }, { 650,200, 50,100 }, { 700,100, 75,125 }, { 700,100, 75,125 } },
			{ { 250,400,100,250 }, { 200,500, 50,250 }, { 200,450,100,250 }, { 200,450,100,250 } },
			{ { 500,150,200,150 }, {  50,250,400,300 }, { 200,100,500,200 }, { 500,  0,250,250 } } };
	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. Seccional 11. */
	public static final int ADULT_SEC11_DEFAULT_TMMC[][][] = {
			{ { 100,600,150,150 }, { 100,600,150,150 }, { 100,600,150,150 }, { 100,600,150,150 } },
			{ { 900, 50, 25, 25 }, { 650,200, 50,100 }, { 700,100, 75,125 }, { 700,100, 75,125 } },
			{ { 300,250,200,250 }, { 300,350,100,250 }, { 300,300,150,250 }, { 300,300,150,250 } },
			{ { 500,125,225,150 }, {  50,200,450,300 }, { 200, 75,500,225 }, { 500,  0,250,250 } } };
	
	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. Seccional 2. */
	public static final int ELDER_SEC2_DEFAULT_TMMC[][][] = ADULT_SEC2_DEFAULT_TMMC;
	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. Seccional 11. */
	public static final int ELDER_SEC11_DEFAULT_TMMC[][][] = ADULT_SEC11_DEFAULT_TMMC;
	
	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. Seccional 2. */
	public static final int HIGHER_SEC2_DEFAULT_TMMC[][][] = {
			{ { 700, 0, 75,225 }, { 700, 0, 75,225 }, { 700, 0, 75,225 }, { 700, 0, 75,225 } },
			{ { 900, 0, 25, 75 }, { 800, 0,100,100 }, { 800, 0,100,100 }, { 800, 0,100,100 } },
			{ { 800, 0,100,100 }, { 500, 0,200,300 }, { 700, 0,  0,300 }, { 700, 0,  0,300 } },
			{ { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 } } };
	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. Seccional 11. */
	public static final int HIGHER_SEC11_DEFAULT_TMMC[][][] = {
			{ { 675, 0,100,225 }, { 675, 0,100,225 }, { 675, 0,100,225 }, { 675, 0,100,225 } },
			{ { 900, 0, 25, 75 }, { 800, 0,100,100 }, { 800, 0,100,100 }, { 800, 0,100,100 } },
			{ { 600, 0,200,200 }, { 450, 0,250,300 }, { 650, 0, 50,300 }, { 650, 0, 50,300 } },
			{ { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 }, { 950, 0, 25, 25 } } };
	
	/** Matriz modificada para los Humanos que viven afuera. */
	public static final int TRAVELER_DEFAULT_TMMC[][][] = {
			{ {  50,725, 75,150 }, {  50,725, 75,150 }, {  50,725, 75,150 }, {  50,725, 75,150 } },
			{ { 900,  0, 50, 50 }, { 800,  0,100,100 }, { 800,  0,100,100 }, { 800,  0,100,100 } },
			{ { 225,400,125,250 }, { 200,500, 50,250 }, { 200,425,125,250 }, { 200,425,125,250 } },
			{ {1000,  0,  0,  0 }, {1000,  0,  0,  0 }, {1000,  0,  0,  0 }, {1000,  0,  0,  0 } } };
	/** Matriz modificada para los Humanos que viven afuera, para fines de semana. */
	public static final int TRAVELER_WEEKEND_TMMC[][][] = {
			{ { 350,400, 75,175 }, { 350,400, 75,175 }, { 350,400, 75,175 }, { 350,400, 75,175 } },
			{ { 900,  0, 50, 50 }, { 800,  0,100,100 }, { 800,  0,100,100 }, { 800,  0,100,100 } },
			{ { 400,150,175,275 }, { 425,200,100,275 }, { 375,175,175,275 }, { 375,175,175,275 } },
			{ { 975,  0, 25,  0 }, { 975,  0, 25,  0 }, { 975,  0, 25,  0 }, { 975,  0, 25,  0 } } };
	
	/** Matriz modificada para los Humanos que se aislan por ser sintomaticos. */
	public static final int ISOLATED_TMMC[][][] = {
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 }, { 999, 0, 0, 1 } },
			{ {1000, 0, 0, 0 }, {1000, 0, 0, 0 }, {1000, 0, 0, 0 }, {1000, 0, 0, 0 } } };

	/** Diferencia en actividades los fines de semana. */
	public static final int WEEKEND_DIFF[][][] = {
			{{ 250,-300,  0, 50 }, { 250,-300,   0, 50 }, { 250,-300,   0, 50 }, { 250,-300,   0,  50 } },
			{{   0, -20, 10, 10 }, { -45,  -5,  25, 25 }, { -45,  -5,  25, 25 }, { -45,  -5,  25,  25 } },
			{{  25,-100, 50, 25 }, {  25,-100,  50, 25 }, {  25,-100,  50, 25 }, {  25,-100,  50,  25 } },
			{{   0, -15, 15,  0 }, {   0, -15,  15,  0 }, {   0,   0,   0,  0 }, {   0,   0,   0,   0 } } };
	
	// Matrices de markov del mes de Junio para seccionales 2 y 11 de Paraná //
	
	public static final int YOUNG_SEC2_JUNE_DIFF[][][] = {
			{ { 250,-250,   0,  0 }, { 250,-250,   0,   0 }, { 250,-250,   0,  0 }, { 250,-250,  0,   0 } },
			{ {  20, -20,   0,  0 }, { 100,  -5, -50, -45 }, { 100,  -5, -50,-45 }, { 100,  -5,-50, -45 } },
			{ { 150,-150,-100,100 }, { 175,-300,  25, 100 }, { 150,-100,-100, 50 }, { 150,-150,-50,  50 } },
			{ { 425, -75,-275,-75 }, { 425, -75,-175,-175 }, { 250,   0,-175,-75 }, { 250,   0,-75,-175 } } };
	public static final int YOUNG_SEC11_JUNE_DIFF[][][] = {
			{ { 250,-265,  15,  0 }, { 250,-265,  15,   0 }, { 250,-265,  15,  0 }, { 250,-265,  15,   0 } },
			{ {  20, -20,   0,  0 }, { 115,  -5, -70, -40 }, { 115,  -5, -70,-40 }, { 115,  -5, -70, -40 } },
			{ { 250,-150,-200,100 }, { 255,-285,-120, 150 }, { 225, -75,-200, 50 }, { 225,-100,-175,  50 } },
			{ { 490, -75,-340,-75 }, { 500,- 75,-250,-175 }, { 325,   0,-250,-75 }, { 315,   0,-140,-175 } } };
	
	public static final int ADULT_SEC2_JUNE_DIFF[][][] = {
			{ { 200,-200,   0,   0 }, { 200,-200,   0,   0 }, { 200,-200,   0,   0 }, { 200,-200,   0,   0 } },
			{ {  25, -20, -10,   5 }, {  50, -50,   0,   0 }, {  50, -25, -25,   0 }, {  50, -25, -25,   0 } },
			{ {  50,-100,   0,  50 }, { 100,-150,  50,   0 }, { 100,-125,   0,  25 }, { 100,-125,   0,  25 } },
			{ { 350,-100,-150,-100 }, { 800,-200,-350,-250 }, { 700,-100,-450,-150 }, { 400,   0,-200,-200 } } };
	public static final int ADULT_SEC11_JUNE_DIFF[][][] = {
			{ { 150,-150,   0,   0 }, { 150,-150,   0,   0 }, { 150,-150,   0,   0 }, { 150,-150,   0,   0 } },
			{ {  25, -20, -10,   5 }, {  50, -50,   0,   0 }, {  50, -25, -25,   0 }, {  50, -25, -25,   0 } },
			{ { 200,-125, -25, -50 }, { 200,-175,  25, -50 }, { 200,-150,   0, -50 }, { 200,-150,   0, -50 } },
			{ { 350, -75,-175,-100 }, { 800,-150,-400,-250 }, { 700, -75,-450,-175 }, { 400,   0,-200,-200 } } };
	
	public static final int HIGHER_SEC2_JUNE_DIFF[][][] = {
			{ { 150,0, -50,-100 }, { 150,0, -50,-100 }, { 150,0,-50,-100 }, { 150,0,-50,-100 } },
			{ {  25,0,   0, -25 }, {  50,0, -50,   0 }, {  50,0,-50,   0 }, {  50,0,-50,   0 } },
			{ { 100,0, -75, -25 }, { 200,0,-100,-100 }, { 150,0,  0,-150 }, { 150,0,  0,-150 } },
			{ {  35,0, -20, -15 }, {  35,0, -20, -15 }, {  35,0,-20, -15 }, {  35,0,-20, -15 } } };
	public static final int HIGHER_SEC11_JUNE_DIFF[][][] = {
			{ { 145,0, -70,-75 }, { 145,0, -70, -75 }, { 145,0,-70, -75 }, { 145,0,-70, -75 } },
			{ {  25,0,   0,-25 }, {  50,0, -50,   0 }, {  50,0,-50,   0 }, {  50,0,-50,   0 } },
			{ { 175,0,-125,-50 }, { 225,0,-125,-100 }, { 150,0,  0,-150 }, { 150,0,  0,-150 } },
			{ {  35,0, -20,-15 }, {  35,0, -20, -15 }, {  35,0,-20, -15 }, {  35,0,-20, -15 } } };
	
	public static final int YOUNG_SEC2_JUNE_TMMC[][][] = mergeChainsDiff(YOUNG_SEC2_DEFAULT_TMMC, YOUNG_SEC2_JUNE_DIFF);
	public static final int YOUNG_SEC11_JUNE_TMMC[][][] = mergeChainsDiff(YOUNG_SEC11_DEFAULT_TMMC, YOUNG_SEC11_JUNE_DIFF);
	
	public static final int ADULT_SEC2_JUNE_TMMC[][][] = mergeChainsDiff(ADULT_SEC2_DEFAULT_TMMC, ADULT_SEC2_JUNE_DIFF);
	public static final int ADULT_SEC11_JUNE_TMMC[][][] = mergeChainsDiff(ADULT_SEC11_DEFAULT_TMMC, ADULT_SEC11_JUNE_DIFF);
	
	public static final int HIGHER_SEC2_JUNE_TMMC[][][] = mergeChainsDiff(HIGHER_SEC2_DEFAULT_TMMC, HIGHER_SEC2_JUNE_DIFF);
	public static final int HIGHER_SEC11_JUNE_TMMC[][][] = mergeChainsDiff(HIGHER_SEC11_DEFAULT_TMMC, HIGHER_SEC11_JUNE_DIFF);
	
	// Matrices de markov del mes de Julio para seccionales 2 y 11 de Paraná //
	
	public static final int YOUNG_SEC2_JULY_DIFF[][][] = {
			{ { 175,-185,   0, 10 }, { 175,-185,   0,  10 }, { 175,-185,   0, 10 }, { 175,-185,  0,  10 } },
			{ {  10, -10,   0,  0 }, {  25,  -5,   0, -20 }, {  25,  -5,   0,-20 }, {  25,  -5,  0, -20 } },
			{ { 125,-125,-100,100 }, { 150,-275,  25, 100 }, { 125, -75,-100, 50 }, { 125,-125,-50,  50 } },
			{ { 400, -75,-250,-75 }, { 400, -75,-150,-175 }, { 225,   0,-150,-75 }, { 225,   0,-75,-150 } } };
	public static final int YOUNG_SEC11_JULY_DIFF[][][] = {
			{ { 165,-200,  35,  0 }, { 165,-200,  35,   0 }, { 165,-200,  35,  0 }, { 165,-200,  35,   0 } },
			{ {  10, -10,   0,  0 }, {  70,  -5, -50, -15 }, {  70,  -5, -50,-15 }, {  70,  -5, -50, -15 } },
			{ { 175,-150,-125,100 }, { 135,-210, -75, 150 }, { 150, -50,-150, 50 }, { 150, -75,-125,  50 } },
			{ { 460, -80,-315,-65 }, { 460, -80,-215,-165 }, { 300,   0,-235,-65 }, { 280,   0,-115,-165 } } };
	
	public static final int ADULT_SEC2_JULY_DIFF[][][] = {
			{ { 150,-165,  15,   0 }, { 150,-165,  15,   0 }, { 150,-165,  15,   0 }, { 150,-165,  15,   0 } },
			{ {   0, -10,   0,  10 }, {  25, -25,   0,   0 }, {   0,   0, -25,  25 }, {   0,   0, -25,  25 } },
			{ {  10,-100,  15,  75 }, {  60,-150,  65,  25 }, {  60,-125,  15,  50 }, {  60,-125,  15,  50 } },
			{ { 310, -85,-125,-100 }, { 760,-175,-335,-250 }, { 675,-100,-425,-150 }, { 375,   0,-175,-200 } } };
	public static final int ADULT_SEC11_JULY_DIFF[][][] = {
			{ { 100,-100,   0,   0 }, { 100,-100,   0,   0 }, { 100,-100,   0,   0 }, { 100,-100,  0,    0 } },
			{ { -10, -10,   5,  15 }, {  25, -35,   0,  10 }, {  10, -10, -25,  25 }, {  10, -10, -25,  25 } },
			{ { 145,-125, -20,   0 }, { 145,-175,  30,   0 }, { 150,-140,  15, -25 }, { 150,-140,  15, -25 } },
			{ { 300, -50,-150,-100 }, { 750,-125,-375,-250 }, { 675, -75,-425,-175 }, { 375,   0,-175,-200 } } };
	
	public static final int HIGHER_SEC2_JULY_DIFF[][][] = {
			{ { 110,0,-35,-75 }, { 110,0,-35,-75 }, { 110,0,-35, -75 }, { 110,0,-35, -75 } },
			{ {   0,0,  0,  0 }, {  25,0,-40, 15 }, {  25,0,-40,  15 }, {  25,0,-40,  15 } },
			{ {  70,0,-60,-10 }, { 170,0,-85,-85 }, { 135,0, 15,-150 }, { 135,0, 15,-150 } },
			{ {  25,0,-15,-10 }, {  25,0,-15,-10 }, {  25,0,-15, -10 }, {  25,0,-15, -10 } } };
	public static final int HIGHER_SEC11_JULY_DIFF[][][] = {
			{ { 100,0, -50,-50 }, { 100,0, -50, -50 }, { 100,0,-50, -50 }, { 100,0,-50, -50 } },
			{ {   0,0,   0,  0 }, {  10,0, -35,  25 }, {  10,0,-35,  25 }, {  10,0,-35,  25 } },
			{ { 120,0,-100,-20 }, { 200,0,-100,-100 }, { 100,0,  0,-100 }, { 100,0,  0,-100 } },
			{ {  25,0, -15,-10 }, {  25,0, -15, -10 }, {  25,0,-15, -10 }, {  25,0,-15, -10 } } };
	
	public static final int YOUNG_SEC2_JULY_TMMC[][][] = mergeChainsDiff(YOUNG_SEC2_DEFAULT_TMMC, YOUNG_SEC2_JULY_DIFF);
	public static final int YOUNG_SEC11_JULY_TMMC[][][] = mergeChainsDiff(YOUNG_SEC11_DEFAULT_TMMC, YOUNG_SEC11_JULY_DIFF);
	
	public static final int ADULT_SEC2_JULY_TMMC[][][] = mergeChainsDiff(ADULT_SEC2_DEFAULT_TMMC, ADULT_SEC2_JULY_DIFF);
	public static final int ADULT_SEC11_JULY_TMMC[][][] = mergeChainsDiff(ADULT_SEC11_DEFAULT_TMMC, ADULT_SEC11_JULY_DIFF);
	
	public static final int HIGHER_SEC2_JULY_TMMC[][][] = mergeChainsDiff(HIGHER_SEC2_DEFAULT_TMMC, HIGHER_SEC2_JULY_DIFF);
	public static final int HIGHER_SEC11_JULY_TMMC[][][] = mergeChainsDiff(HIGHER_SEC11_DEFAULT_TMMC, HIGHER_SEC11_JULY_DIFF);
	
	// Matrices de markov del mes de Agosto para seccionales 2 y 11 de Paraná //
	
	public static final int YOUNG_SEC2_AUGUST_DIFF[][][] = {
			{ { 100,-125,  25,  0 }, { 100,-125,  25,   0 }, { 100,-125,  25,  0 }, { 100,-125, 25,   0 } },
			{ {   0, -10,   5,  5 }, {  15,  -5,  5,  -15 }, {  15,  -5,   5,-15 }, {  15,  -5,  5, -15 } },
			{ { 100,-100,-100,100 }, { 100,-225,  25, 100 }, { 100, -50,-100, 50 }, { 100,-100,-50,  50 } },
			{ { 350, -75,-225,-50 }, { 350, -75,-125,-150 }, { 200,   0,-150,-50 }, { 200,   0,-50,-150 } } };
	public static final int YOUNG_SEC11_AUGUST_DIFF[][][] = {
			{ {  50,-100,  50,  0 }, {  50,-100,  50,   0 }, {  50,-100,  50,  0 }, {  50,-100,  50,   0 } },
			{ { -25, -10,  25, 10 }, {  40,  -5, -30,  -5 }, {  40,  -5, -30, -5 }, {  40,  -5, -30,  -5 } },
			{ {  75,-125, -50,100 }, {  50,-175, -25, 150 }, {  75, -45, -80, 50 }, {  75, -75, -50,  50 } },
			{ { 415, -75,-290,-50 }, { 430, -75,-205,-150 }, { 275,   0,-225,-50 }, { 250,   0,-100,-150 } } };
	
	public static final int ADULT_SEC2_AUGUST_DIFF[][][] = {
			{ {  100,-100,  0,  0 }, { 100,-100,   0,   0 }, { 100,-100,   0,   0 }, { 100,-100,   0,   0 } },
			{ {  -25, -10, 10, 25 }, {  25, -25,   0,   0 }, {   0,   0, -25,  25 }, {   0,   0, -25,  25 } },
			{ {  -50, -75, 50, 75 }, {  25,-125,  50,  50 }, {   0,-100,  50,  50 }, {   0,-100,  50,  50 } },
			{ { 250, -75,-100,-75 }, { 700,-175,-300,-225 }, { 625,-100,-400,-125 }, { 325,   0,-150,-175 } } };
	public static final int ADULT_SEC11_AUGUST_DIFF[][][] = {
			{ {  60, -60,  0,   0 }, {  60, -60,  0,    0 }, {  60, -60,   0,   0 }, {  60, -60,   0,   0 } },
			{ { -25, -10, 10,  25 }, {   0, -25,  0,   25 }, { -25,   0, -25,  50 }, { -25,   0, -25,  50 } },
			{ { 100,-100,  0,   0 }, { 100,-150, 50,    0 }, { 100,-125,  50, -25 }, { 100,-125,  50, -25 } },
			{ { 250, -50,-125,-75 }, { 700,-125,-350,-225 }, { 625, -75,-400,-150 }, { 325,   0,-150,-175 } } };
	
	public static final int HIGHER_SEC2_AUGUST_DIFF[][][] = {
			{ { 50,0,-25,-25 }, {  50,0,-25,-25 }, {  50,0,-25, -25 }, {  50,0,-25, -25 } },
			{ {  0,0,  0,  0 }, {   0,0,-30, 30 }, {   0,0,-30,  30 }, {   0,0,-30,  30 } },
			{ { 40,0,-40,  0 }, { 150,0,-75,-75 }, { 105,0, 20,-125 }, { 105,0, 20,-125 } },
			{ { 15,0,-10, -5 }, {  15,0,-10, -5 }, {  15,0,-10,  -5 }, {  15,0,-10,  -5 } } };
	public static final int HIGHER_SEC11_AUGUST_DIFF[][][] = {
			{ { 50,0,-30,-20 }, {  50,0,-30,-20 }, { 50,0,-30,-20 }, { 50,0,-30,-20 } },
			{ {  0,0,  0,  0 }, {   0,0,-25, 25 }, {  0,0,-25, 25 }, {  0,0,-25, 25 } },
			{ { 70,0,-75,  5 }, { 150,0,-75,-75 }, { 50,0,  0,-50 }, { 50,0,  0,-50 } },
			{ { 15,0,-10, -5 }, {  15,0,-10, -5 }, { 15,0,-10, -5 }, { 15,0,-10, -5 } } };
	
	public static final int YOUNG_SEC2_AUGUST_TMMC[][][] = mergeChainsDiff(YOUNG_SEC2_DEFAULT_TMMC, YOUNG_SEC2_AUGUST_DIFF);
	public static final int YOUNG_SEC11_AUGUST_TMMC[][][] = mergeChainsDiff(YOUNG_SEC11_DEFAULT_TMMC, YOUNG_SEC11_AUGUST_DIFF);
	
	public static final int ADULT_SEC2_AUGUST_TMMC[][][] = mergeChainsDiff(ADULT_SEC2_DEFAULT_TMMC, ADULT_SEC2_AUGUST_DIFF);
	public static final int ADULT_SEC11_AUGUST_TMMC[][][] = mergeChainsDiff(ADULT_SEC11_DEFAULT_TMMC, ADULT_SEC11_AUGUST_DIFF);
	
	public static final int HIGHER_SEC2_AUGUST_TMMC[][][] = mergeChainsDiff(HIGHER_SEC2_DEFAULT_TMMC, HIGHER_SEC2_AUGUST_DIFF);
	public static final int HIGHER_SEC11_AUGUST_TMMC[][][] = mergeChainsDiff(HIGHER_SEC11_DEFAULT_TMMC, HIGHER_SEC11_AUGUST_DIFF);
	
	// FIN DECLARACIONES MATRICES DE MARKOV //

	/**
	 * Suma o resta la matriz de fines de semana, a la matriz dada, y retorna el resultado.
	 * @param base
	 * @param enable
	 * @return
	 */
	public static int[][][] setWeekendDiff(int[][][] base, boolean enable) {
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				for (int k = 0; k < 4; k++)
					if (enable)
						base[i][j][k] += WEEKEND_DIFF[i][j][k];
					else
						base[i][j][k] -= WEEKEND_DIFF[i][j][k];
		return base;
	}
	
	/**
	 * Suma a matriz base la matriz diff y retorna el resultado.
	 * @param base
	 * @param diff
	 * @return
	 */
	public static int[][][] mergeChainsDiff(int[][][] base, int[][][] diff) {
		int[][][] result = new int[4][4][4];
		int temp;
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				for (int k = 0; k < 4; k++) {
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
	 * @param oldc
	 * @param newc
	 */
	public static void createChainsDiff(int[][][] oldc, int[][][] newc) {
		int[][][] result = new int[4][4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				for (int k = 0; k < 4; k++)
					result[i][j][k] = oldc[i][j][k] - newc[i][j][k];
		printMarkovChains(result);
	}
	
	/**
	 * Imprime en formato Java en la consola, la matriz de markov dada.
	 * @param markov
	 */
	public static void printMarkovChains(int[][][] markov) {
		for (int i = 0; i < 4; i++) {
			System.out.print("{ ");
			for (int j = 0; j < 4; j++) {
				System.out.print("{ ");
				for (int k = 0; k < 4; k++) {
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