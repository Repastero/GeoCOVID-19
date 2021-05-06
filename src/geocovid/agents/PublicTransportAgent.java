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
	private static int ptStill= DataSet.PUBLIC_TRANSPORT_MAX_STILL;

	public PublicTransportAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType, PlaceProperty pp) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, pp.getActivityState(), pp.getBuildingArea(), pp.getBuildingCArea(), pp.getWorkersPerPlace(), pp.getWorkersPerArea());
		this.setMaximumCapacity(ptSeats+ptStill);
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
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey, boolean direct) {
		if (super.checkContagion(spreader, prey, direct)) { 
			InfectionReport.addCumExposedPublicTransport();
			return true;
		}
		return false;
	}
	
	/**
	 * Por ahora se deja el aforo por defecto (el de hogares):
	 * Con un area de 40 y un modificador de 0.85 = 34 metros cuadrados
	 * Area modificada por 4 personas por metro cuadrado = 136 posiciones
	 * Con 136 el tamano de parcela es = 8 x 17 posiciones
	 * Menos una fila del puesto de trabajo = 128 posiciones
	 * Posiciones divididas por 4 personas por m2 = 32 capacidad
	 *  los lugares adicionales se los modelaria como personas paradas.
	 * 
	 * @param humanSites: capacidad de humanos
	 */	
	@Override
	public void limitCapacity(double sqMetersPerHuman) {
		
	}
	/**
	 * Cambia el aforo de acuerdo a la cantidad de personas.
	 * Posiciones divididas por 4 personas por m2 = 32 capacidad, de las cuales 24 son asientos y 8 lugares para que la gente se mantenga parada
	 * (la capacidad no debe superar los 32)
	 * @param humanSites: capacidad de humanos
	 */	
	public void limitBusCapacity(int humanSites) {
		int cap;
		// Para calcular el maximo aforo teorico de un local comercial:
		if (humanSites > 0d) {
			cap = humanSites;
			if (cap <= getWorkPositionsCount()) 
				cap = getWorkPositionsCount() + 1; // permitir al menos un cliente
		}
		else
			cap = getMaximumCapacity();
		
		setCapacity(cap);
	}
	
	public int getPtSeats() {
		return ptSeats;
	}
	
	public void setPtSeats(int ptSeatsChange) {
		ptSeats = ptSeatsChange;
	}

	public int getPtStill() {
		return ptStill;
	}

	public void setPtStill(int ptStill) {
		this.ptStill = ptStill;
	}

	
}
