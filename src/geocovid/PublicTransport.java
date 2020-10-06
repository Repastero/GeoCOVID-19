package geocovid;

import geocovid.agents.HumanAgent;
import geocovid.agents.SurfaceAgent;
import repast.simphony.random.RandomHelper;

public class PublicTransport {
	private SurfaceAgent seats[][] = new SurfaceAgent[DataSet.PUBLIC_TRANSPORT_UNITS][DataSet.PUBLIC_TRANSPORT_SEATS];
	
	public void jumpAboard(HumanAgent human) {
		int unit = RandomHelper.nextIntFromTo(0, DataSet.PUBLIC_TRANSPORT_UNITS-1);
		int seat = RandomHelper.nextIntFromTo(0, DataSet.PUBLIC_TRANSPORT_SEATS-1);
		
		if (!human.wasExposed())
			checkIfSurfaceContaminated(human, seats[unit][seat]);
		else if (human.isContagious())
			seats[unit][seat] = removeSpreader(seats[unit][seat]);
	}
	
	private void checkIfSurfaceContaminated(HumanAgent human, SurfaceAgent surface) {
		if (surface != null) {
			// Si en el ultimo checkeo la superficie seguia contaminada
			if (surface.isContaminated()) {
				if (RandomHelper.nextIntFromTo(1, 100) <= surface.getInfectionRate()) {
					human.setExposed();
					InfectionReport.addExposedToCS();
				}
			}
		}
	}
	
	private SurfaceAgent removeSpreader(SurfaceAgent surface) {
		if (surface == null) {
			// Se crea una superficie con la posicion como ID
			surface = new SurfaceAgent(false);
		}
		else {
			// Si la superficie ya estaba contaminada, se 'renueva' el virus
			surface.updateLifespan();
		}
		return surface;
	}
}
