package geocovid.agents;

import geocovid.DataSet;
import repast.simphony.engine.environment.RunEnvironment;

public class SurfaceAgent {
	@SuppressWarnings("unused")
	private int agentID;
	private double creationTime = 0d;
	private boolean contaminated = true;
	
	public SurfaceAgent(int id) {
		this.agentID = id;
		this.updateLifespan();
	}
	
	public boolean isContaminated() {
		return contaminated;
	}
	
	public void updateLifespan() {
		creationTime = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
	}
	
	public int getInfectionRate() {
		double time = RunEnvironment.getInstance().getCurrentSchedule().getTickCount() - creationTime;
		int infectionRate = (int) (((DataSet.CS_INFECTION_LIFESPAN - time) / DataSet.CS_INFECTION_LIFESPAN) * DataSet.CS_INIT_INFECTION_RATE);
		contaminated = (infectionRate > 0);
		return infectionRate;
	}
}
