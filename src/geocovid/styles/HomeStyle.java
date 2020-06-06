package geocovid.styles;

import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.awt.Dimension;
import java.awt.image.BufferedImage;

import geocovid.agents.BuildingAgent;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.render.BasicWWTexture;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.PatternFactory;
import gov.nasa.worldwind.render.WWTexture;
import repast.simphony.visualization.gis3D.PlaceMark;
import repast.simphony.visualization.gis3D.style.MarkStyle;

//https://ows.terrestris.de/osm-gray/service?

public class HomeStyle implements MarkStyle<BuildingAgent>{
	
	private static Map<Integer, WWTexture> textureMap;
	
	public HomeStyle() {
		/**
		 * Use of a map to store textures significantly reduces CPU and memory use
		 * since the same texture can be reused.  Textures can be created for different
		 * agent states and re-used when needed.
		 */
		textureMap = new HashMap<Integer, WWTexture>();
		BufferedImage image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(6, 6), .75f, new Color(0xFFFFFF), Color.BLACK);
		textureMap.put(0, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFFCCCC), Color.BLACK);
		textureMap.put(1, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFF9999), Color.BLACK);
		textureMap.put(2, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFF6666), Color.BLACK);
		textureMap.put(3, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFF3333), Color.BLACK);
		textureMap.put(4, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xFF0000), Color.BLACK);
		textureMap.put(5, new BasicWWTexture(image));
		image = PatternFactory.createPattern(PatternFactory.PATTERN_SQUARE, new Dimension(8, 8), .75f, new Color(0xCC0000), Color.BLACK);
		textureMap.put(6, new BasicWWTexture(image));
	}
	
	@Override
	public double getLineWidth(BuildingAgent obj) {
		return 0;
	}

	@Override
	public WWTexture getTexture(BuildingAgent object, WWTexture texture) {
		// WWTexture is null on first call.
		int humAmount = object.getHumansAmount();
		if (humAmount > 6) // Maximo 6 personas en hogar
			humAmount = 6;
		return textureMap.get(humAmount);
	}

	@Override
	public PlaceMark getPlaceMark(BuildingAgent object, PlaceMark mark) {
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
	public Offset getIconOffset(BuildingAgent obj) {
		return Offset.CENTER;
	}

	@Override
	public double getElevation(BuildingAgent obj) {
		return 0;
	}

	@Override
	public double getScale(BuildingAgent obj) {
		return 1d;
	}

	@Override
	public double getHeading(BuildingAgent obj) {
		return 0;
	}

	@Override
	public String getLabel(BuildingAgent obj) {
		return null;
	}

	@Override
	public Color getLabelColor(BuildingAgent obj) {
		return null;
	}

	@Override
	public Font getLabelFont(BuildingAgent obj) {
		return null;
	}

	@Override
	public Offset getLabelOffset(BuildingAgent obj) {
		return null;
	}

	@Override
	public Material getLineMaterial(BuildingAgent obj, Material lineMaterial) {
		return null;
	}
}
