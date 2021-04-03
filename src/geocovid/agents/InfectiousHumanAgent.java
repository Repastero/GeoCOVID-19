package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Esta clase se utiliza para representar en mapa la ubicacion del infectado.
 */
public class InfectiousHumanAgent {
	
	private int humanAgentID;
	private Coordinate currentCoordinate;
	private boolean hidden = false;

	public InfectiousHumanAgent(int agentID, Coordinate coordinate) {
		this.humanAgentID = agentID;
		this.currentCoordinate = coordinate;
	}

	public int getAgentID() {
		return humanAgentID;
	}
	
	public Coordinate getCurrentCoordinate() {
		return currentCoordinate;
	}

	public void setCurrentCoordinate(Coordinate currentCoordinate) {
		this.currentCoordinate = currentCoordinate;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
}
