package geocovid.agents;

import static repast.simphony.essentials.RepastEssentials.RemoveAgentFromContext;
import static repast.simphony.essentials.RepastEssentials.AddAgentToContext;

import repast.simphony.engine.schedule.ScheduleParameters;

public class ForeignHumanAgent extends HumanAgent {
	
	private boolean inContext = true;

	public ForeignHumanAgent(int ageGroup, BuildingAgent home, BuildingAgent job, int[] posJob, int tmmc) {
		super(ageGroup, home, job, posJob, tmmc);
	}
	
	@Override
	public void removeInfectiousFromContext() {
		if (inContext) {
			super.removeInfectiousFromContext();
			inContext = false;
		}
	}
	
	@Override
	public void addRecoveredToContext() {
		// Si esta hospitalizado o vive afuera no vuelve a entrar
		if (hospitalized)
			return;
		
		// Si se recupera, vuelve al dia siguiente
		double newDayTick = Math.ceil(schedule.getTickCount() / 12) * 12;
		
        // Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(newDayTick, 0.6d); // ScheduleParameters.FIRST_PRIORITY
		schedule.schedule(params, this, "switchLocation");
	}
	
	@Override
	public BuildingAgent switchActivity(int prevActivityIndex, int activityIndex) {
		BuildingAgent newBuilding = null;
		double endTime = schedule.getTickCount();
        switch (activityIndex) {
	    	case 1: // 1 Trabajo / Estudio
	    		newBuilding = getPlaceOfWork();
	    		endTime += 3; // la actividad de trabajo dura 4 ticks
	    		AddAgentToContext("GeoCOVID-19", this);
	    		inContext = true;
	    		break;
	    	default: // 0, 1, 3
	    		endTime += 1; // resto de actividades dura 1 tick
	    		if (prevActivityIndex == 1) { // trabajo
	    			RemoveAgentFromContext("GeoCOVID-19", this);
	    			inContext = false;
	    		}
	    		break;
        }
        
        // Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(endTime, 0.6d); // ScheduleParameters.FIRST_PRIORITY
		schedule.schedule(params, this, "switchLocation");
		
		return newBuilding;
	}
}
