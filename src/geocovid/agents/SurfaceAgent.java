package geocovid.agents;

import geocovid.DataSet;
import geocovid.Temperature;
import repast.simphony.engine.schedule.ISchedule;

/**
 * Superficie contaminada (estela).
 */
public class SurfaceAgent {
    /** Puntero a ISchedule para conocer tick */
    private static ISchedule schedule;
	/** Tick de contaminacion o re-contaminacion */
	private int creationTime = 0;
	/** Ultimo Tick cuando se calculo infectionRate */
	private int lastUpdateTime = -1;
	/** Tasa de infeccion */
	private int infectionRate;
	/** Si superficie al exterior */
	private boolean outdoor;
	/** Region donde se crea superficie */
	private int region;
	
	public static void init(ISchedule insSchedule) {
		schedule = insSchedule;
	}
	
	/**
	 * Crea superficie contaminada. 
	 * @param regionIdx indice region
	 * @param outdoorSurface superficie al exterior
	 */
	public SurfaceAgent(int regionIdx, boolean outdoorSurface) {
		this.region = regionIdx;
		this.outdoor = outdoorSurface;
		this.updateLifespan();
	}
	
	/**
	 * Refrescar el tiempo de creacion
	 */
	public void updateLifespan() {
		creationTime = (int) schedule.getTickCount();
	}
	
	/**
	 * Calcula la carga viral de la superficie con respecto al tiempo de vida y temperatura.
	 * @return <b>int</b> tasa de infeccion
	 */
	public int getInfectionRate() {
		int currentTime = (int) schedule.getTickCount();
		if (currentTime != lastUpdateTime) {
			lastUpdateTime = currentTime;
			// Para simplificar toma cada tick como 1 hora,
			// pero 24 ticks son 16 horas reales
			final int hours = lastUpdateTime - creationTime;
			final double beta = Temperature.getFomiteInfectionRate(region, outdoor);
			infectionRate = (int) (Math.exp((-0.08d) * hours) * beta);
			if (infectionRate < DataSet.CS_MIN_INFECTION_RATE)
				infectionRate = 0;
		}
		return infectionRate;
	}
}
