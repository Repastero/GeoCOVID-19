package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

public class InfectiousHumanAgent {
	
	private int humanAgentID; 
	private Coordinate currentCoordinate;

	public InfectiousHumanAgent(int agentID, Coordinate coordinate) {
		this.setAgentID(agentID);
		this.setCurrentCoordinate(coordinate);
	}

	public int getAgentID() {
		return humanAgentID;
	}

	public void setAgentID(int humanAgentID) {
		this.humanAgentID = humanAgentID;
	}
	
	public Coordinate getCurrentCoordinate() {
		return currentCoordinate;
	}

	public void setCurrentCoordinate(Coordinate currentCoordinate) {
		this.currentCoordinate = currentCoordinate;
	}

}
