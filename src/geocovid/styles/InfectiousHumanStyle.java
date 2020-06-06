package geocovid.styles;

import java.awt.Color;

import geocovid.agents.InfectiousHumanAgent;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfaceShape;
import repast.simphony.visualization.gis3D.style.SurfaceShapeStyle;

public class InfectiousHumanStyle implements SurfaceShapeStyle<InfectiousHumanAgent>{

	@Override
	public SurfaceShape getSurfaceShape(InfectiousHumanAgent object, SurfaceShape shape) {
		return new SurfacePolygon();
	}

	@Override
	public Color getFillColor(InfectiousHumanAgent obj) {
		return Color.RED;
	}

	@Override
	public double getFillOpacity(InfectiousHumanAgent obj) {
		return .30d;
	}

	@Override
	public Color getLineColor(InfectiousHumanAgent obj) {
		return Color.BLACK;
	}

	@Override
	public double getLineOpacity(InfectiousHumanAgent obj) {
		return .6d;
	}

	@Override
	public double getLineWidth(InfectiousHumanAgent obj) {
		return 1;
	}

}
