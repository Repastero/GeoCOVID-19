package geocovid.agents;

import java.util.ArrayList;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;

import geocovid.DataSet;
import geocovid.InfectionReport;
import geocovid.contexts.SubContext;

public class ClassroomAgent extends WorkplaceAgent {

	private List<HumanAgent> students = new ArrayList<HumanAgent>();
	private static boolean schoolProtocol; // Habilita protoloco de asistencia 50% semanal
	private static boolean firstGroup = false; // flag para cambio de grupo de estudio
	
	public ClassroomAgent(SubContext subContext, int sectoralType, int sectoralIndex, Coordinate coord, long id, String workType) {
		super(subContext, sectoralType, sectoralIndex, coord, id, workType, 1, DataSet.CLASSROOM_SIZE, DataSet.CLASSROOM_SIZE, DataSet.CLASS_SIZE);
	}
	
	public ClassroomAgent(ClassroomAgent cra) {
		super(cra);
	}
	
	/**
	 * Habilitar o deshabilitar los grupos de estudiantes semanales.
	 * @param firstGroup flag cambio de grupo
	 */
	public static void switchStudyGroup() {
		firstGroup = !firstGroup;
	}
	
	@Override
	public int[] insertHuman(HumanAgent human, int[] pos) {
		if (closed)
			return null;
		if (schoolProtocol) {
			boolean firstGroupStudent = (pos[0] % DataSet.SPACE_BETWEEN_STUDENTS == 0 ? true : false);
			if ((firstGroup && !firstGroupStudent) || (!firstGroup && firstGroupStudent))
				return null;
		}
		students.add(human);
		return super.insertHuman(human, pos);
	}
	
	/**
	 * Crea posiciones en aulas
	 */
	@Override
	public void createWorkPositions() {
		int x = getWidth();
		int y = getHeight();
		workPositions = new int[vacancies][2];
		workPositionsCount = workPositions.length;
		int distance = DataSet.SPACE_BETWEEN_STUDENTS;
		int col = 1;
		int row = 2 * DataSet.HUMANS_PER_SQUARE_METER; // 2 metros de pasillo
		boolean fullBuilding = false; // flag para saber si se utiliza todo el rango de col, row
		for (int i = 1; i < workPositionsCount; i += 2) {
			workPositions[i - 1][0] = col - 1;
			workPositions[i - 1][1] = row;
			workPositions[i][0] = col;
			workPositions[i][1] = row;
			col += distance;
			if (col >= x) {
				col = 1;
				row += distance;
				if (row >= y) {
					if (i + 1 < workPositionsCount) { // TODO esto lo deje como info por las dudas
						// Si faltan crear puestos
						if (fullBuilding) {
							System.out.format("Cupos de estudiantes limitados a %d de %d en tipo: %s id: %d%n", i + 1,
									workPositionsCount, workplaceType, getId());
							workPositionsCount = i + 1;
							vacancies = workPositionsCount;
							break;
						}
						// Si es la primer pasada, vuelve al principio + offset
						System.out.format("Falta espacio para %d cupos de estudiantes en tipo: %s id: %d%n",
								workPositionsCount - (i + 1), workplaceType, getId());
					}
					fullBuilding = true;
				}
			}
		}
	}
	
	@Override
	public void removeHuman(HumanAgent human, int[] pos) {
		if (pos == null)
			return;
		if (schoolProtocol) {
			// Si hay pre-sintomaticos, se aislan los alumnos presentes
			if (!preSpreadersList.isEmpty()) {
				// El pre-sintomaticos setea contacto estrecho en el resto 
				if (human.isPreInfectious()) {
					students.forEach(student -> {
						if (student != human)
							student.setCloseContact(human.getInfectiousStartTime());
					});
				}
				// Se setea contacto estrecho con el primero de la lista
				else if (!human.isInCloseContact()) {
					human.setCloseContact(preSpreadersList.get(0).getInfectiousStartTime());
				}
			}
		}
		students.remove(human);
		super.removeHuman(human, pos);
	}
	
	@Override
	public int[] getWorkPosition() {
		return workPositions[--workPositionsCount];
	}
	
	@Override
	protected void infectHuman(HumanAgent prey) {
		InfectionReport.addCumExposedSchool();
		super.infectHuman(prey);
	}
	
	public static void setSchoolProtocol(boolean schoolProtocol) {
		ClassroomAgent.schoolProtocol = schoolProtocol;
	}
}