package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.PlaceProperty;
import geocovid.contexts.SubContext;

/**
 * Representa las unidades de transporte publico.
 */
public class PublicTransportAgent extends WorkplaceAgent {
	
	public PublicTransportAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, PlaceProperty pp) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, pp.getActivityState(), DataSet.PUBLIC_TRANSPORT_UNIT_WIDTH, pp.getBuildingArea() / DataSet.PUBLIC_TRANSPORT_UNIT_WIDTH,
				(pp.getWorkersPerPlace() > 0 ? pp.getWorkersPerPlace() : (pp.getBuildingArea() / pp.getWorkersPerArea())));
		
		this.setMaximumCapacity(DataSet.PUBLIC_TRANSPORT_MAX_SEATED + DataSet.PUBLIC_TRANSPORT_MAX_STANDING);
	}
	
	@Override
	public int[] insertHuman(HumanAgent human) {
		int[] pos = super.insertHuman(human);
		if (pos != null) {
			InfectionReport.addCumTicketTransportPublic();
		}
		return pos;
	}
	
	@Override
	protected void infectHuman(HumanAgent prey) {
		InfectionReport.addCumExposedPublicTransport();
		super.infectHuman(prey);
	}
	
	@Override
	public void limitCapacity(double sqMetersPerHuman) {
		
	}
	
	/**
	 * Cambia el aforo de acuerdo a la cantidad de personas.
	 * @param humanSites: capacidad de humanos
	 */	
	public void limitUnitCapacity(int humanSites) {
		int cap;
		if (humanSites > 0d) {
			cap = humanSites;
			if (cap <= getWorkPositionsCount()) 
				cap = getWorkPositionsCount() + 1; // permitir al menos un cliente
		}
		else
			cap = getMaximumCapacity();
		setCapacity(cap);
	}
}
