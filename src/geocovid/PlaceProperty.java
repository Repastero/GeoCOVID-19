package geocovid;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVReader;

public final class PlaceProperty {
	private String googleMapsType;
	private String googlePlaceType;
	private int activityType;
	private int buildingArea;
	private int buildingCoveredArea;
	private int workersPerPlace;
	private int workersPerArea;
	private int[] activityChances = new int[DataSet.AGE_GROUPS];
	
	public PlaceProperty(String gmapsType, String gplaceType) {
		this.googleMapsType = gmapsType;
		this.googlePlaceType = gplaceType;
	}
	
	public PlaceProperty(String gmapsType, String gplaceType, PlaceProperty pp) {
		this.googleMapsType = gmapsType;
		this.googlePlaceType = gplaceType;
		this.activityType = pp.activityType;
		this.buildingArea = pp.buildingArea;
		this.buildingCoveredArea = pp.buildingCoveredArea;
		this.workersPerPlace = pp.workersPerPlace;
		this.workersPerArea = pp.workersPerArea;
		this.activityChances = pp.activityChances;
	}
	
	public PlaceProperty(String gmapsType, String gplaceType, int type, int avgArea, int avgCoveredArea, int workersPlace, int workersArea, int[] chances) {
		this.googleMapsType = gmapsType;
		this.googlePlaceType = gplaceType;
		this.activityType = type;
		this.buildingArea = avgArea;
		this.buildingCoveredArea = avgCoveredArea;
		this.workersPerPlace = workersPlace;
		this.workersPerArea = workersArea;
		this.activityChances = chances;
	}
	
	private static int[] readHeader(String[] rows) throws Exception {
		String[] headers = {"Google_Maps_type","Google_Place_type","Activity_type","Area","Covered_area","Workers_place","Workers_area","Chance_1","Chance_2","Chance_3","Chance_4","Chance_5"};
		int[] indexes = new int[headers.length];
		
		int i,j;
		for (i = 0; i < headers.length; i++) {
			for (j = 0; j < rows.length; j++) {
				if (rows[j].equals(headers[i])) {
					indexes[i] = j;
					break;
				}
			}
			if (j == rows.length) {
				throw new Exception("Falta Header: " + headers[i]);
			}
		}
		return indexes;
	}
	
	public static Map<String, PlaceProperty> loadPlacesProperties() {
		PlaceProperty placeProperty;
		String gMapsType;
		String gPlaceType;
		String chance;
		Map<String, PlaceProperty> placesProperty = new HashMap<>();
		boolean headerFound = false;
		CSVReader reader = null;
		String[] nextLine;
		int[] dataIndexes = {};
		try {
			String csvPath = Town.getPlacesPropertiesFilepath();
			reader = new CSVReader(new FileReader(csvPath), ',');
			while ((nextLine = reader.readNext()) != null) {
				if (!headerFound) {
					dataIndexes = readHeader(nextLine);
					headerFound = true;
					continue;
				}
				gMapsType = nextLine[dataIndexes[0]]; // Type de Google Maps
				gPlaceType = nextLine[dataIndexes[1]]; // Type de Google Places o custom
				// Si es Type principal, debe tener todos los atributos
				if (gMapsType.equals(gPlaceType)) {
					placeProperty = new PlaceProperty(gMapsType, gPlaceType);
					try {
						placeProperty.setActivityType	(Integer.valueOf(nextLine[dataIndexes[2]]));
						placeProperty.setBuildingArea	(Integer.valueOf(nextLine[dataIndexes[3]]));
						placeProperty.setBuildingCArea	(Integer.valueOf(nextLine[dataIndexes[4]]));
						placeProperty.setWorkersPerPlace(Integer.valueOf(nextLine[dataIndexes[5]]));
						placeProperty.setWorkersPerArea	(Integer.valueOf(nextLine[dataIndexes[6]]));
					} catch (NumberFormatException e) {
						System.out.println(String.join(", ", nextLine));
					}
					// Leer las chances para cada grupo etario
					for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
						chance = nextLine[dataIndexes[7 + i]];
						if (!StringUtils.isBlank(chance))
							placeProperty.setActivityChances(i, Integer.valueOf(chance));
						else
							placeProperty.setActivityChances(i, 0);
					}
				}
				// Si es type secundario, pueden variar el area y trabajadores
				else {
					// Crear copia del type primario
					placeProperty = new PlaceProperty(gMapsType, gPlaceType, placesProperty.get(gPlaceType));
					try {
						if (!StringUtils.isBlank(nextLine[dataIndexes[3]]))
							placeProperty.setBuildingArea	(Integer.valueOf(nextLine[dataIndexes[3]]));
						if (!StringUtils.isBlank(nextLine[dataIndexes[4]]))
							placeProperty.setBuildingCArea	(Integer.valueOf(nextLine[dataIndexes[4]]));
						if (!StringUtils.isBlank(nextLine[dataIndexes[5]]))
							placeProperty.setWorkersPerPlace(Integer.valueOf(nextLine[dataIndexes[5]]));
						if (!StringUtils.isBlank(nextLine[dataIndexes[6]]))
							placeProperty.setWorkersPerArea	(Integer.valueOf(nextLine[dataIndexes[6]]));
					} catch (NumberFormatException e) {
						System.out.println(String.join(", ", nextLine));
					}
				}
				placesProperty.put(gMapsType, placeProperty);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (IOException e) { }
		}
		return placesProperty;
	}

	public String getGoogleMapsType() {
		return googleMapsType;
	}

	public String getGooglePlaceType() {
		return googlePlaceType;
	}

	public int getActivityType() {
		return activityType;
	}

	public int getBuildingArea() {
		return buildingArea;
	}

	public int getBuildingCArea() {
		return buildingCoveredArea;
	}

	public int getWorkersPerPlace() {
		return workersPerPlace;
	}

	public int getWorkersPerArea() {
		return workersPerArea;
	}

	public int[] getActivityChances() {
		return activityChances;
	}

	public void setGoogleMapsType(String googleMapsType) {
		this.googleMapsType = googleMapsType;
	}

	public void setGooglePlaceType(String googlePlaceType) {
		this.googlePlaceType = googlePlaceType;
	}

	public void setActivityType(int activityType) {
		this.activityType = activityType;
	}

	public void setBuildingArea(int buildingArea) {
		this.buildingArea = buildingArea;
	}

	public void setBuildingCArea(int buildingCoveredArea) {
		this.buildingCoveredArea = buildingCoveredArea;
	}

	public void setWorkersPerPlace(int workersPlace) {
		this.workersPerPlace = workersPlace;
	}

	public void setWorkersPerArea(int workersArea) {
		workersPerArea = workersArea;
	}

	public void setActivityChances(int[] activityChances) {
		this.activityChances = activityChances;
	}
	
	public void setActivityChances(int agIndex, int chance) {
		this.activityChances[agIndex] = chance;
	}
}
