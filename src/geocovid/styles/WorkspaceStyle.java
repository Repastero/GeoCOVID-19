package geocovid.styles;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import geocovid.agents.WorkplaceAgent;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.render.BasicWWTexture;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.PatternFactory;
import gov.nasa.worldwind.render.WWTexture;
import repast.simphony.visualization.gis3D.PlaceMark;
import repast.simphony.visualization.gis3D.style.MarkStyle;

public class WorkspaceStyle implements MarkStyle<WorkplaceAgent>{
	
	private static Map<Integer, WWTexture> textureMap;
	
	public WorkspaceStyle() {
		/**
		 * Use of a map to store textures significantly reduces CPU and memory use
		 * since the same texture can be reused.  Textures can be created for different
		 * agent states and re-used when needed.
		 */
		textureMap = new HashMap<Integer, WWTexture>();
		BufferedImage image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(6, 6), .75f, new Color(0xFFFFFF), Color.BLUE);
		textureMap.put(0, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFFFCC), Color.BLUE);
		textureMap.put(1, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFFF99), Color.BLUE);
		textureMap.put(2, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFFF66), Color.BLUE);
		textureMap.put(3, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFFF33), Color.BLUE);
		textureMap.put(4, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFFF00), Color.BLUE);
		textureMap.put(5, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFCC00), Color.BLUE);
		textureMap.put(6, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xCC9933), Color.BLUE);
		textureMap.put(7, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xCC9900), Color.BLUE);
		textureMap.put(8, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0x996633), Color.BLUE);
		textureMap.put(9, new BasicWWTexture(image));
	}
	
	@Override
	public double getLineWidth(WorkplaceAgent obj) {
		return 0;
	}

	@Override
	public WWTexture getTexture(WorkplaceAgent object, WWTexture texture) {
		// WWTexture is null on first call.
		int humAmount = object.getHumansAmount() / 5; // fracciono cada 5
		if (humAmount > 9) // Maximo 49 humanos en comercio / lugar de trabajo
			humAmount = 9;
		return textureMap.get(humAmount);
	}

	@Override
	public PlaceMark getPlaceMark(WorkplaceAgent object, PlaceMark mark) {
		// PlaceMark is null on first call.
		if (mark == null)
			mark = new PlaceMark();
		
		/**
		 * The Altitude mode determines how the mark appears using the elevation.
		 *   WorldWind.ABSOLUTE places the mark at elevation relative to sea level
		 *   WorldWind.RELATIVE_TO_GROUND places the mark at elevation relative to ground elevation
		 *   WorldWind.CLAMP_TO_GROUND places the mark at ground elevation
		 */
		mark.setAltitudeMode(WorldWind.RELATIVE_TO_GROUND);
		mark.setLineEnabled(false);
		return mark;
	}

	@Override
	public Offset getIconOffset(WorkplaceAgent obj) {
		return Offset.CENTER;
	}

	@Override
	public double getElevation(WorkplaceAgent obj) {
		return 0;
	}

	@Override
	public double getScale(WorkplaceAgent obj) {
		return 1d;
	}

	@Override
	public double getHeading(WorkplaceAgent obj) {
		return 0;
	}

	@Override
	public String getLabel(WorkplaceAgent obj) {
		return null;
	}

	@Override
	public Color getLabelColor(WorkplaceAgent obj) {
		return null;
	}

	@Override
	public Font getLabelFont(WorkplaceAgent obj) {
		return null;
	}

	@Override
	public Offset getLabelOffset(WorkplaceAgent obj) {
		return null;
	}

	@Override
	public Material getLineMaterial(WorkplaceAgent obj, Material lineMaterial) {
		return null;
	}
}
