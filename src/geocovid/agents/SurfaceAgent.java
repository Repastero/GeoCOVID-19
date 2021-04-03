package geocovid.agents;

import geocovid.DataSet;
import geocovid.Temperature;
import repast.simphony.engine.environment.RunEnvironment;

/**
 * Superficie contaminada (estela).
 */
public class SurfaceAgent {
	/** Tick de contaminacion o re-contaminacion */
	private int creationTime = 0;
	/** Ultimo Tick cuando se calculo infectionRate */
	private int lastUpdateTime = -1;
	/** Tasa de infeccion */
	private int infectionRate;
	/** Flag por si el virus ya expiro */
	private boolean contaminated;
	/** Si superficie al exterior */
	private boolean outdoor;
	/** Region donde se crea superficie */
	private int region;
	
	public SurfaceAgent(int regionIdx, boolean outdoorSurface) {
		this.region = regionIdx;
		this.outdoor = outdoorSurface;
		this.updateLifespan();
	}
	
	public boolean isContaminated() {
		return contaminated;
	}
	
	/**
	 * Descontaminar superficie
	 */
	public void cleanUpSurface() {
		contaminated = false;
	}
	
	/**
	 * Refrescar el tiempo de creacion
	 */
	public void updateLifespan() {
		creationTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		contaminated = true;
	}
	
	/**
	 * Calcula la carga viral de la superficie con respecto al tiempo de vida y temperatura.
	 * @return <b>int</b> tasa de infeccion
	 */
	public int getInfectionRate() {
		int currentTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		if (currentTime != lastUpdateTime) {
			lastUpdateTime = currentTime;
			// Sumo cada tick como una hora, y cada multiplo de 24 suma 24 horas mas
			int hours = currentTime - creationTime;
			hours += ((currentTime / 24) - (creationTime / 24)) * 24;
			//
			double beta = Temperature.getInfectionRate(region, outdoor);
			infectionRate = (int) (Math.exp((-0.028d) * hours) * beta);
			if (infectionRate < DataSet.CS_MIN_INFECTION_RATE) {
				infectionRate = 0;
				contaminated = false;
			}
		}
		return infectionRate;
	}
}
