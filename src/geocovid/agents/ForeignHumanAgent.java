package geocovid.agents;

import static repast.simphony.essentials.RepastEssentials.AddAgentToContext;
import static repast.simphony.essentials.RepastEssentials.RemoveAgentFromContext;

import repast.simphony.engine.schedule.ScheduleParameters;

public class ForeignHumanAgent extends HumanAgent {
	
	private boolean inContext = true;

	public ForeignHumanAgent(int secHome, int secHomeIndex, int ageGroup, BuildingAgent job, int[] posJob) {
		super(secHome, secHomeIndex, ageGroup, null, job, posJob, true);
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
		ScheduleParameters params = ScheduleParameters.createOneTime(newDayTick, 0.6d);
		schedule.schedule(params, this, "switchLocation");
	}
	
	@Override
	public BuildingAgent switchActivity(int prevActivityIndex, int activityIndex) {
		BuildingAgent newBuilding = null;
		double endTime = schedule.getTickCount();
		// Si toca casa, sale del contexto por 1 tick
		if (activityIndex == 0) {
			if (inContext) {
    			RemoveAgentFromContext("GeoCOVID-19", this);
    			inContext = false;
    		}
    		endTime += 1;
		}
		else {
    		if (!inContext) {
    			AddAgentToContext("GeoCOVID-19", this);
    			inContext = true;
    		}
    		return super.switchActivity(prevActivityIndex, activityIndex);
		}
		
        // Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(endTime, 0.6d);
		schedule.schedule(params, this, "switchLocation");
		
		return newBuilding;
	}
}
