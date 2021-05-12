package geocovid.agents;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.contexts.SubContext;

public class ClassroomAgent extends WorkplaceAgent {

	private List<HumanAgent> students = new ArrayList<HumanAgent>();
	private int[][] studentPositions;
	private int studentPositionsCount;
	private String workplaceType;
	private static boolean schoolProtocol; // Habilita protoloco de asistencia 50% semanal
	private static boolean firstGroup = false; // flag para cambio de grupo de estudio
	private int Index = 0;
	
	public ClassroomAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id,
			String workType, int activityType, int area, int coveredArea, int workersPlace) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, activityType, area, coveredArea,
				workersPlace);
		this.workplaceType = workType;
		this.vacancies = workersPlace;
	}
	
	/**
	 * Habilitar o deshabilitar los grupos de estudiantes semanales.
	 * 
	 * @param firstGroup flag cambio de grupo
	 */
	public static void studyGroupChange() {
		if (firstGroup) {
			firstGroup = false;
		} else {
			firstGroup = true;
		}
	}
	
	@Override
	public int[] insertHuman(HumanAgent human, int[] pos) {
		if (closed)
			return null;
		pos = human.getWorkplacePosition();
		if (schoolProtocol) {
			if (firstGroup) {
				if (pos[0] == 0 || pos[0] % 3 == 0) {
					students.add(human);
					return super.insertHuman(human, pos);
				} else
					return null;
			} else if (!(pos[0] == 0) && !(pos[0] % 3 == 0)) {
				students.add(human);
				return super.insertHuman(human, pos);
			} else
				return null;
		} else {
			students.add(human);
			return super.insertHuman(human, pos);
		}
	}
	
	/**
	 * Crea posiciones en aulas
	 */
	@Override
	public void createWorkPositions() {
		int x = getWidth();
		int y = getHeight();
		studentPositions = new int[vacancies][2];
		studentPositionsCount = studentPositions.length;
		int distance = DataSet.SPACE_BETWEEN_STUDENTS;
		int col = 1;
		int row = 0;
		boolean fullBuilding = false; // flag para saber si se utiliza todo el rango de col, row
		for (int i = 1; i < studentPositionsCount; i = i + 2) {
			studentPositions[i - 1][0] = col - 1;
			studentPositions[i - 1][1] = row;
			studentPositions[i][0] = col;
			studentPositions[i][1] = row;
			col += distance;
			if (col >= x) {
				col = 1;
				row += distance;
				if (row >= y) {
					if (i + 1 < studentPositionsCount) { // TODO esto lo deje como info por las dudas
						// Si faltan crear puestos
						if (fullBuilding) {
							System.out.format("Cupos de estudiantes limitados a %d de %d en tipo: %s id: %d%n", i + 1,
									studentPositionsCount, workplaceType, getId());
							studentPositionsCount = i + 1;
							vacancies = studentPositionsCount;
							break;
						}
						// Si es la primer pasada, vuelve al principio + offset
						System.out.format("Falta espacio para %d cupos de estudiantes en tipo: %s id: %d ",
								studentPositionsCount - (i + 1), workplaceType, getId());
					}
					fullBuilding = true;
				}
			}
		}
	}
	
	@Override
	public int[] getWorkPosition() {
		int[] pos = studentPositions[Index];
		studentPositions[Index] = studentPositions[--studentPositionsCount];
		++Index;
		return pos;
	}
	
	@Override
	public void removeHuman(HumanAgent human, int[] pos) {
		if (pos == null)
			return;
		if (human.isContagious()) {
			for (HumanAgent student : students) {
				if (schoolProtocol) {
					int[] desk = student.getWorkplacePosition();
					if (firstGroup) {
						if (desk[0] == 0 || desk[0] % 3 == 0) {
							if (!student.isInCloseContact()) {
								student.setCloseContact(human.getInfectiousStartTime());
							}
						}
					} else {
						if (!student.isInCloseContact()) {
							student.setCloseContact(human.getInfectiousStartTime());
						}
					}
				}
			}
		}
		students.remove(human);
		super.removeHuman(human, pos);
	}
	
	@Override
	protected boolean checkContagion(HumanAgent spreader, HumanAgent prey, boolean direct) {
		if (super.checkContagion(spreader, prey, direct)) {
			InfectionReport.addCumExposedSchool();
			return true;
		}
		return false;
	}
	
	public static boolean isSchoolProtocol() {
		return schoolProtocol;
	}

	public static void setSchoolProtocol(boolean schoolProtocol) {
		ClassroomAgent.schoolProtocol = schoolProtocol;
	}
}