package geocovid.agents;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.contexts.SubContext;

/**
 * Clase hogar. Contiene la lista de habitantes y setea cuarentena preventiva.
 */
public class HomeAgent extends BuildingAgent {
	private List<HumanAgent> occupants = new ArrayList<HumanAgent>();
	private int lastQuarentineST = -DataSet.PREVENTIVE_QUARANTINE_TIME;
	
	public HomeAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, int id, int homeArea, int homeCoveredArea) {
		super(subContext, sectoralType, sectoralIndex, coord, id, "home", homeArea, homeCoveredArea, DataSet.BUILDING_AVAILABLE_AREA, false);
	}
	
	public HomeAgent(BuildingAgent ba) {
		super(ba);
	}
	
	public void addOccupant(HumanAgent occupant) {
		occupants.add(occupant);
	}
	
	/**
	 * Pone en cuarentena preventiva a los ocupantes no expuestos.
	 * @param startTime tick de inicio
	 */
	public void startPreventiveQuarentine(int startTime) {
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