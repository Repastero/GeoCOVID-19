package geocovid.agents;

import geocovid.InfectionReport;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;

/**
 * Representa las unidades de transporte publico.
 */
public class PublicTransportAgent {
	private int region;
	private SurfaceAgent seats[][][];
	private int ptUnits;
	private int ptSeats;
	
	public PublicTransportAgent(int regionIdx, int sectorals, int tranUnits, int tranSeats) {
		this.region = regionIdx;
		this.ptUnits = tranUnits;
		this.ptSeats = tranSeats;
		this.seats = new SurfaceAgent[sectorals][tranUnits][tranSeats];
	}
	
	public void jumpAboard(HumanAgent human, int sectoral) {
		int unit = RandomHelper.nextIntFromTo(0, ptUnits - 1);
		int seat = RandomHelper.nextIntFromTo(0, ptSeats - 1);
		
		if (!human.wasExposed())
			checkIfSurfaceContaminated(human, seats[sectoral][unit][seat]);
		else if (human.isContagious())
			seats[sectoral][unit][seat] = removeSpreader(seats[sectoral][unit][seat]);
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
			surface = new SurfaceAgent(region, true); // Se crea como superficie al exterior
		}
		else {
			// Si la superficie ya estaba contaminada, se 'renueva' el virus
			surface.updateLifespan();
		}
		return surface;
	}
	
	/**
	 * Cada cambio de turno se desinfectan los asientos.
	 */
	@ScheduledMethod(start = 6d, interval = 6d, priority = ScheduleParameters.FIRST_PRIORITY)
	public void cleanUpSeats() {
		for (int i = 0; i < seats.length; i++) {
			for (int j = 0; j < seats[i].length; j++) {
				for (int k = 0; k < seats[i][j].length; k++) {
					if (seats[i][j][k] != null)
						seats[i][j][k].cleanUpSurface();
				}
			}
		}
	}
}
