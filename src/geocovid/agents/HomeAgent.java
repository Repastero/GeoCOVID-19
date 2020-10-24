package geocovid.agents;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;

import geocovid.DataSet;

public class HomeAgent extends BuildingAgent {
	private List<HumanAgent> occupants = new ArrayList<HumanAgent>();
	private double lastQuarentineST = -DataSet.PREVENTIVE_QUARANTINE_TIME;
	
	public HomeAgent(Geometry geo, long id, long blockid, String type, int area, int coveredArea) {
		super(geo, id, blockid, type, area, coveredArea);
	}
	
	public HomeAgent(BuildingAgent ba) {
		super(ba);
	}
	
	public void addOccupant(HumanAgent occupant) {
		occupants.add(occupant);
	}
	
	/**
	 * Pone en cuarentena preventiva a los ocupantes no expuestos.
	 * @param startTime
	 */
	public void startPreventiveQuarentine(double startTime) {
		// Chequear que no junten dos periodos de cuarentena
		if ((startTime - lastQuarentineST) > DataSet.PREVENTIVE_QUARANTINE_TIME) {
			lastQuarentineST = startTime;
			for (HumanAgent occupant : occupants) {
				// Se encuarentenan los no estuvieron expuestos 
				if (!occupant.wasExposed())
					occupant.startSelfQuarantine();
			}
		}
	}
}