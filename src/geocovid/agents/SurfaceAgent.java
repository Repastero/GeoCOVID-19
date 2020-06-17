package geocovid.agents;

import geocovid.DataSet;
import repast.simphony.engine.environment.RunEnvironment;

public class SurfaceAgent {
	private int creationTime = 0;	// Tick de contaminacion o re-contaminacion
	private int lastUpdateTime = -1;// Ultimo Tick cuando se calculo infectionRate
	private int infectionRate;		// Tasa de infeccion
	private boolean contaminated;	// Flag por si el virus ya expiro
	
	public SurfaceAgent() {
		this.updateLifespan();
	}
	
	public boolean isContaminated() {
		return contaminated;
	}
	
	public void updateLifespan() {
		creationTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		contaminated = true;
	}
	
	public int getInfectionRate() {
		int currentTime = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		if (currentTime != lastUpdateTime) {
			lastUpdateTime = currentTime;
			// Sumo cada tick como una hora, y cada multiplo de 12 suma 12 horas mas
			int hours = currentTime - creationTime;
			hours += ((currentTime / 12) - (creationTime / 12)) * 12;
			//
			double beta = Math.exp(-(4.9d) + (DataSet.CS_MEAN_TEMPERATURE/10d));
			infectionRate = (int) (Math.exp((-beta) * hours) * DataSet.CS_INFECTION_RATE);
			contaminated = (infectionRate > 0);
		}
		return infectionRate;
	}
}
