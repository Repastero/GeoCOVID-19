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
	private static int ptSeats = DataSet.PUBLIC_TRANSPORT_MAX_SEAT;
	
	public PublicTransportAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, PlaceProperty pp) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, pp.getActivityType(), pp.getBuildingArea(), pp.getBuildingCArea(), pp.getWorkersPerPlace(), pp.getWorkersPerArea());
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
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey) {
		if (super.checkContagion(spreader, prey)) { 
			InfectionReport.addCumExposedPublicTransport();
			return true;
		}
		return false;
	}
	
	@Override
	public void limitCapacity(double sqMetersPerHuman) {
		/*
		 * Por ahora se deja el aforo por defecto (el de hogares):
		 * Con un area de 40 y un modificador de 0.85 = 34 metros cuadrados
		 * Area modificada por 4 personas por metro cuadrado = 136 posiciones
		 * Con 136 el tamano de parcela es = 8 x 17 posiciones
		 * Menos una fila del puesto de trabajo = 128 posiciones
		 * Posiciones divididas por 4 personas por m2 = 32 capacidad
		 */
	}
	
	public static int getPtSeats() {
		return ptSeats;
	}
	
	public static void setPtSeats(int ptSeatsChange) {
		ptSeats = ptSeatsChange;
	}
}
