package geocovid;

/**
 * Matrices de 4x4x4 - Probabilidades sobre 1000. 4 periodos del dia X 4 posiciones actuales X 4 probabilidades de lugares.<p>
 * <i>MaxiF: La probabilidad de la cadena de markov de movimiento temporal es un arreglo que:
 * probabilidadTMMC[P,i,j], donde P es el periodo del dia (8-11 11-14 14-17 17-20hs)
 * i es el nodo de donde sale, y j es el nodo a donde va.<p>
 * El nodo 0 es la casa, el 1 es el trabajo/estudio, el 2 es ocio, el 3 es otros (supermercados, farmacias, etc)
 * Ej: probabilidadTMMC[1][1][2] es la probabilidad de que en el periodo 1 salga del trabajo 1 al lugar de ocio 2</i>
 */
public final class MarkovChains {
	/** Matriz modificada para los Humanos que estan en la 1er franja etaria. */
	public static final int CHILD_DEFAULT_TMMC[][][] = {
		{ {100,700,100,100},{ 25,925, 25, 25},{100,700,100,100},{100,700,100,100} },
		{ {925, 25, 25, 25},{800,  0,100,100},{800,  0,100,100},{800,  0,100,100} },
		{ { 25,925, 25, 25},{ 25,925, 25, 25},{ 25,925, 25, 25},{ 25,925, 25, 25} },
		{ {925, 25, 25, 25},{ 50, 50,450,450},{700,  0,300,  0},{700,  0,  0,300} }
	};
	
	/** Matriz modificada para los Humanos que estan en la 2er franja etaria. */
	public static final int YOUNG_DEFAULT_TMMC[][][] = CHILD_DEFAULT_TMMC;
	
	/** Matriz modificada para los Humanos que estan en la 3er franja etaria. */
	public static final int ADULT_DEFAULT_TMMC[][][] = YOUNG_DEFAULT_TMMC;
	
	/** Matriz modificada para los Humanos que estan en la 4ta franja etaria. */
	public static final int ELDER_DEFAULT_TMMC[][][] = {
		{ {700,0,100,200},{925,0, 25, 50},{950,0, 25, 25},{950,0,25,25} },
		{ {900,0, 25, 75},{800,0,100,100},{100,0,450,450},{950,0,25,25} },
		{ {700,0,100,200},{800,0,100,100},{700,0,300,  0},{950,0,25,25} },
		{ {700,0,100,200},{800,0,100,100},{700,0,  0,300},{950,0,25,25} }
	};
	
	/** Matriz modificada para los Humanos que estan en la 5ta franja etaria. */
	public static final int HIGHER_DEFAULT_TMMC[][][]= ELDER_DEFAULT_TMMC;
	
	/** Matriz modificada para los Humanos que trabajan afuera o viven afuera. */
	public static final int TRAVELER_DEFAULT_TMMC[][][] = {
		{ {  0,1000,  0,  0},{  0,1000,  0,  0},{  0,1000,  0,  0},{  0,1000,  0,  0} },
		{ {950,   0, 25, 25},{800,   0,100,100},{800,   0,100,100},{800,   0,100,100} },
		{ {300,   0,350,350},{300,   0,350,350},{300,   0,350,350},{300,   0,350,350} },
		{ {950,   0, 25, 25},{100,   0,450,450},{700,   0,300,  0},{700,   0,  0,300} }
	};
	
	///*******************************CUARENTENA1*******************************
	// Confinamiento con salida a compras.
	public static final int CHILD_CONFINEMENT_TMMC[][][] = {
		{ {935,0,1, 64},{935,0,1, 64},{935,0,1, 64},{935,0,1, 64} },
		{ {880,0,1,119},{880,0,1,119},{880,0,1,119},{880,0,1,119} },
		{ {880,0,1,129},{880,0,1,119},{880,0,1,119},{880,0,1,119} },
		{ {935,0,1, 64},{935,0,1, 64},{935,0,1, 64},{935,0,1, 64} }
	};
	
	public static final int YOUNG_CONFINEMENT_TMMC[][][] = CHILD_CONFINEMENT_TMMC;
	
	public static final int ADULT_CONFINEMENT_TMMC[][][] = {
		{ {904,30,1, 65},{904,30,1, 65},{904,30,1, 65},{904,30,1, 65} },
		{ {854,25,1,120},{854,25,1,120},{854,25,1,120},{854,25,1,120} },
		{ {854,25,1,120},{854,25,1,120},{854,25,1,120},{854,25,1,120} },
		{ {904,30,1, 65},{904,30,1, 65},{904,30,1, 65},{904,30,1, 65} }
	};
	
	public static final int ELDER_CONFINEMENT_TMMC[][][] = {
		{ {850,0,0,150},{850,0,0,150},{850,0,0,150},{975,0,0,25}},
		{ {950,0,0, 50},{950,0,0, 50},{950,0,0, 50},{975,0,0,25}},
		{ {950,0,0, 50},{950,0,0, 50},{950,0,0, 50},{975,0,0,25}},
		{ {900,0,0,100},{900,0,0,100},{900,0,0,100},{975,0,0,25}}
	};
	public static final int HIGHER_CONFINEMENT_TMMC[][][] = ELDER_CONFINEMENT_TMMC;
	
	public static final int TRAVELER_CONFINEMENT_TMMC[][][] = {
		{ {875,60,0, 65},{875,60,0, 65},{875,60,0, 65},{875,60,0, 65} },
		{ {830,50,0,120},{830,50,0,120},{830,50,0,120},{830,50,0,120} },
		{ {830,50,0,120},{830,50,0,120},{830,50,0,120},{830,50,0,120} },
		{ {875,60,0, 65},{875,60,0, 65},{875,60,0, 65},{875,60,0, 65} }
	};
	
	public static final int INFECTED_CHILD_TMMC[][][] = {
		{ {999,0,0,1},{999,0,0,1},{999,0,0,1},{999,0,0,1}},
		{ {999,0,0,1},{999,0,0,1},{999,0,0,1},{999,0,0,1}},
		{ {999,0,0,1},{999,0,0,1},{999,0,0,1},{999,0,0,1}},
		{ {999,0,0,1},{999,0,0,1},{999,0,0,1},{999,0,0,1}}
	};
	
	public static final int INFECTED_YOUNG_TMMC[][][] = INFECTED_CHILD_TMMC;
	
	public static final int INFECTED_ADULT_TMMC[][][] = INFECTED_YOUNG_TMMC;

	public static final int INFECTED_ELDER_TMMC[][][] = {
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}}
	};
	
	public static final int INFECTED_HIGHER_TMMC[][][] = INFECTED_ELDER_TMMC;
	
	public static final int INFECTED_TRAVELER_TMMC[][][] = INFECTED_ELDER_TMMC;
	
	/////*******************************CUARENTENA MAS SEVERA*******************************
	
	public static final int CHILD_HARD_CONFINEMENT_TMMC[][][] = {
		{ {985,10,0,5},{985,10,0,5},{985,10,0,5},{985,10,0,5} },
		{ {985,10,0,5},{985,10,0,5},{985,10,0,5},{985,10,0,5} },
		{ {985,10,0,5},{985,10,0,5},{985,10,0,5},{985,10,0,5} },
		{ {985,10,0,5},{985,10,0,5},{985,10,0,5},{985,10,0,5} }
	};
	
	public static final int YOUNG_HARD_CONFINEMENT_TMMC[][][] = CHILD_HARD_CONFINEMENT_TMMC;
	
	public static final int ADULT_HARD_CONFINEMENT_TMMC[][][] = YOUNG_HARD_CONFINEMENT_TMMC;
	
	public static final int ELDER_HARD_CONFINEMENT_TMMC[][][] = {
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}}
	};
	
	public static final int HIGHER_HARD_CONFINEMENT_TMMC[][][] = ELDER_HARD_CONFINEMENT_TMMC;
	
	
	///*******************************CUARENTENA4*******************************
	// Confinamiento total de todos estados de sitio.
	public static final int ACTIVE_FULL_DAY_CONFINEMENT_TMMC[][][] = {
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}},
		{ {1000,0,0,0},{1000,0,0,0},{1000,0,0,0},{1000,0,0,0}}
	};
	
	public static final int TRAVELER_FULL_DAY_CONFINEMENT_TMMC[][][] = ACTIVE_FULL_DAY_CONFINEMENT_TMMC;
	
	public static final int ELDER_FULL_DAY_CONFINEMENT_TMMC[][][] = ACTIVE_FULL_DAY_CONFINEMENT_TMMC;
	
	//
	///*******************************CUARENTENA5*******************************
	// Confinamiento de todos s�lo con salida a compras Organizadas, viejos de tarde , jovenes adultos de ma�ana.
	public static final int ACTIVE_HALF_DAY_CONFINEMENT_TMMC[][][] = {
		{ {1000,0,0,  0},{1000,0,0,  0},{1000,0,0,  0},{1000,0,0,  0} },
		{ {1000,0,0,  0},{1000,0,0,  0},{1000,0,0,  0},{1000,0,0,  0} },
		{ { 800,0,0,200},{ 900,0,0,100},{ 800,0,0,200},{ 800,0,0,200} },
		{ { 900,0,0,100},{ 800,0,0,200},{ 800,0,0,200},{ 800,0,0,200} }
	};
	
	public static final int ELDER_HALF_DAY_CONFINEMENT_TMMC[][][] = {
		{ { 700,0,100,200},{ 900,0, 25, 75},{ 700,0,100,200},{ 700,0,100,200} },
		{ { 925,0, 25, 50},{ 800,0,100,100},{ 800,0,100,100},{ 800,0,100,100} },
		{ {1000,0,  0,  0},{1000,0,  0,  0},{1000,0,  0,  0},{1000,0,  0,  0} },
		{ {1000,0,  0,  0},{1000,0,  0,  0},{1000,0,  0,  0},{1000,0,  0,  0} }
	};
	
	public static final int TRAVELER_HALF_DAY_CONFINEMENT_TMMC[][][] = ACTIVE_HALF_DAY_CONFINEMENT_TMMC;
	//Las matrices de validaci�n no fueron modificadas
	// Matrices de comportamiento del pueblo italiano Vo , (rural)
	public static final int YOUNG_ITALY_TMMC[][][] = {
		{ {25,925,25,25},{925,25,25,25},{500,10,250,240},{90,10,450,450} },
		{ {25,925,25,25},{800,10,95,95},{500,10,250,240},{90,10,450,450} },
		{ {25,925,25,25},{800,10,95,95},{500,10,250,240},{100,0,450,450} },
		{ {25,925,25,25},{800,10,95,95},{500,10,250,240},{100,0,600,300} }
	};
	
	// Adultos
	public static final int ADULT_ITALY_TMMC[][][] = {
		{ {490,500,5,5},{965,25,5,5},{500,490,5,5},{500,10,250,240} },
		{ {490,500,5,5},{940,50,5,5},{500,490,5,5},{500,10,250,240} },
		{ {490,500,5,5},{940,50,5,5},{500,490,5,5},{500,10,250,240} },
		{ {490,500,5,5},{940,50,5,5},{500,490,5,5},{500,10,250,240} }
	};
	
	// Mayores
	public static final int ELDER_ITALY_TMMC[][][] = {
		{ {600,0,100,300},{900,0,50,50},{400,0,300,300},{990,0,5,5} },
		{ {600,0,100,300},{900,0,50,50},{300,0,350,350},{990,0,5,5} },
		{ {600,0,100,300},{900,0,50,50},{400,0,300,300},{990,0,5,5} },
		{ {600,0,100,300},{900,0,50,50},{300,0,350,350},{990,0,5,5} }
	};
	
	// Matrices de confinamiento de espa�oles
	public static final int YOUNG_SPAIN_TMMC[][][] = {
		{ {940,0,10,50},{970,0,10,50},{970,0,10,50},{970,0,10,50} },
		{ {940,0,10,50},{970,0,10,50},{970,0,10,50},{970,0,10,50} },
		{ {940,0,10,50},{970,0,10,50},{970,0,10,50},{970,0,10,50} },
		{ {940,0,10,50},{970,0,10,50},{970,0,10,50},{970,0,10,50} }
	};
	
	// Adultos
	public static final int ADULT_SPAIN_TMMC[][][] = {
		{ {925,25,0,50},{925,25,0,50},{900,10,0,90},{900,10,0,90} },
		{ {925,25,0,50},{925,25,0,50},{900,10,0,90},{900,10,0,90} },
		{ {925,25,0,50},{925,25,0,50},{900,10,0,90},{900,10,0,90} },
		{ {925,25,0,50},{925,25,0,50},{900,10,0,90},{900,10,0,90} }
	};
	
	// Mayores
	public static final int ELDER_SPAIN_TMMC[][][] = ADULT_SPAIN_TMMC;
	
	// Mayores 65
	public static final int HIGHER_SPAIN_TMMC[][][] = {
		{ {980,0,0,20},{980,0,0,20},{980,0,0,20},{980,0,0,20} },
		{ {980,0,0,20},{980,0,0,20},{980,0,0,20},{980,0,0,20} },
		{ {980,0,0,20},{980,0,0,20},{980,0,0,20},{980,0,0,20} },
		{ {980,0,0,20},{980,0,0,20},{980,0,0,20},{980,0,0,20} }
	};
}
