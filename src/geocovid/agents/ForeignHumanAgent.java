package geocovid.agents;

import geocovid.contexts.SubContext;
import repast.simphony.engine.schedule.ScheduleParameters;

/**
 * Agente Humano extranjero que puede entrar y salir del contexto.
 */
public class ForeignHumanAgent extends HumanAgent {
	
	private boolean inContext = true;
	
	public ForeignHumanAgent(SubContext subContext, int secHome, int secHomeIndex, int ageGroup, BuildingAgent job, int[] posJob) {
		super(subContext, secHome, secHomeIndex, ageGroup, null, job, posJob, true, false);
	}
	
	public ForeignHumanAgent(SubContext subContext, int secHomeIndex) {
		// Constructor turista - Tipo seccional 2, grupo etario 2
		super(subContext, 0, secHomeIndex, 2, null, null, null, true, true);
	}
	
	public void addToContext() {
		context.add(this);
		inContext = true;
	}
	
	@Override
	public void removeFromContext() {
		if (inContext) {
			super.removeFromContext();
			inContext = false;
		}
	}
	
	@Override
	public void addRecoveredToContext() {
		// Si esta hospitalizado o vive afuera no vuelve a entrar
		if (hospitalized)
			return;
		
		// Si se recupera, vuelve al dia siguiente
		double newDayTick = Math.ceil(schedule.getTickCount() / 24) * 24;
		
        // Schedule one shot
		ScheduleParameters params = ScheduleParameters.createOneTime(newDayTick, ScheduleParameters.FIRST_PRIORITY);
		schedule.schedule(params, this, "addToContext");
	}
	
	@Override
	public void setInfectious(boolean asyntomatic, boolean initial) {
		if (asyntomatic) // para que no sume como nuevo expuesto
			exposed = true;
		super.setInfectious(asyntomatic, initial);
	}
}
