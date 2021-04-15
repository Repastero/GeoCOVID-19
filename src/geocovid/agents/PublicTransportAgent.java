package geocovid.agents;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.contexts.SubContext;

/**
 * Representa las unidades de transporte publico.
 */
public class PublicTransportAgent extends WorkplaceAgent {
	private int ptSeats;
	private int ptStill;
	private int bussySeat;
	private int bussyStill;


	
	public PublicTransportAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, int activityType, int area, int coveredArea, int workersPlace, int workersArea) {
		super(subContext,  sectoralType,  sectoralIndex,  coord,  id,  workType,  activityType,  area, coveredArea, workersPlace,  workersArea);
		this.ptSeats=DataSet.PUBLIC_TRANSPORT_MAX_SEAT;
		this.ptStill=DataSet.PUBLIC_TRANSPORT_MAX_STILL;
		
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
	public void removeHuman(HumanAgent human, int[] pos) {
//		InfectionReport.removeSeatStill();
		super.removeHuman(human, pos);

	}
	
	@Override
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey) {
		boolean ret;
		ret=super.checkContagion(spreader, prey);
//		System.out.println("Contagio dentro del cole: " + ret);
		if(ret) 
			InfectionReport.addCumExposedPublicTransport();
		
		return ret;
	}
	
	@Override
	public void limitCapacity(double sqMetersPerHuman) {
		//setCapacity(ptStill+ ptSeats);
		setCapacity(ptSeats);
		//setCapacity(ptStill);

	}
		
	public int getPtSeats() {
		return ptSeats;
	}
	public void setPtSeats(int ptSeats) {
		this.ptSeats = ptSeats;
	}
	public int getPtStill() {
		return ptStill;
	}
	public void setPtStill(int ptStill) {
		this.ptStill = ptStill;
	}
	public int getBussySeat() {
		return bussySeat;
	}

	public void setBussySeat(int bussySeat) {
		this.bussySeat = bussySeat;
	}

	public int getBussyStill() {
		return bussyStill;
	}

	public void setBussyStill(int bussyStill) {
		this.bussyStill = bussyStill;
	}
}
