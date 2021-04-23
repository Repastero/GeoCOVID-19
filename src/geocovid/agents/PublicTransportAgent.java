package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.contexts.SubContext;

/**
 * Representa las unidades de transporte publico.
 */
public class PublicTransportAgent extends WorkplaceAgent {
	private static int ptSeats = DataSet.PUBLIC_TRANSPORT_MAX_SEAT;

	
	public PublicTransportAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, int activityType, int area, int coveredArea, int workersPlace, int workersArea) {
		super(subContext,  sectoralType,  sectoralIndex,  coord,  id,  workType,  activityType,  area, coveredArea, workersPlace,  workersArea);
		
	}
	
	@Override
	public int[] insertHuman(HumanAgent human) {
		int[] pos = new int[2];
		pos = super.insertHuman(human);
		if(pos!=null) {
			InfectionReport.addCumTicketTransportPublic();
		}
	return pos;
	}
	
	
	@Override
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey) {
		if(super.checkContagion(spreader, prey)) { 
			InfectionReport.addCumExposedPublicTransport();
			return true;
		}
		
		return false;
	}
	
	@Override
	public void limitCapacity(double sqMetersPerHuman) {
		super.limitCapacity(ptSeats);

	}
	
	public static int getPtSeats() {
		return ptSeats;
	}
	
	public static void setPtSeats(int ptSeatsChange) {
		ptSeats = ptSeatsChange;
	}
	
	
}
