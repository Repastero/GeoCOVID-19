package geocovid;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

public final class PlaceProperty {
	private String googleMapsType;
	private int activityType;
	private int workersPerPlace;
	private int WorkersPerArea;
	private int[] activityChances = new int[DataSet.AGE_GROUPS];
	
	public PlaceProperty(String gmapsType) {
		this.googleMapsType = gmapsType;
	}
	
	public PlaceProperty(String gmapsType, int type, int workersPlace, int workersArea, int[] chances) {
		this.googleMapsType = gmapsType;
		this.activityType = type;
		this.workersPerPlace = workersPlace;
		this.WorkersPerArea = workersArea;
		this.activityChances = chances;
	}
	
	private static int[] readHeader(String[] rows) throws Exception {
		//String[] header = {"Google_Place_type","Google_Maps_description","Google_Maps_type","Activity_type","Workers_place","Workers_area","Chance_1","Chance_2","Chance_3","Chance_4","Chance_5"};
		String[] headers = {"Google_Maps_type","Activity_type","Workers_place","Workers_area","Chance_1","Chance_2","Chance_3","Chance_4","Chance_5"};
		int[] indexes = new int[9];
		
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
	
	public static void loadPlacesProperties(Map<String, PlaceProperty> placesProperty) {
		String gmapsType;
		String chance;
		PlaceProperty placeProperty;
		int placesCount = placesProperty.size();
		
		boolean headerFound = false;
		CSVReader reader = null;
		String[] nextLine;
		int[] dataIndexes = {};
		try {
			reader = new CSVReader(new FileReader(DataSet.CSV_FILE_PLACES_PROPERTIES), ';');
			while ((nextLine = reader.readNext()) != null) {
				if (!headerFound) {
					dataIndexes = readHeader(nextLine);
					headerFound = true;
				}
				
				gmapsType = nextLine[dataIndexes[0]];
				if (placesProperty.containsKey(gmapsType)) {
					placeProperty = placesProperty.get(gmapsType);
					try {
						placeProperty.setActivityType(Integer.valueOf(nextLine[dataIndexes[1]]));
						placeProperty.setWorkersPerPlace(Integer.valueOf(nextLine[dataIndexes[2]]));
						placeProperty.setWorkersPerArea(Integer.valueOf(nextLine[dataIndexes[3]]));
					} catch (NumberFormatException e) {
						System.out.println(String.join(", ", nextLine));
						//e.printStackTrace();
					}
					for (int i = 0; i < DataSet.AGE_GROUPS; i++) {
						placeProperty.setActivityChances(i, 10); // por ahora, todas las chances son iguales
						/*
						chance = nextLine[dataIndexes[4+i]];
						if (chance.isBlank() || chance.contentEquals("0"))
							placeProperty.setActivityChances(i, 0);
						else
							placeProperty.setActivityChances(i, (int) Math.round(Double.valueOf(chance)*1000));
						*/
					}
					if (--placesCount == 0)
						break;
				}
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
	}

	public String getGoogleMapsType() {
		return googleMapsType;
	}

	public int getActivityType() {
		return activityType;
	}

	public int getWorkersPerPlace() {
		return workersPerPlace;
	}

	public int getWorkersPerArea() {
		return WorkersPerArea;
	}

	public int[] getActivityChances() {
		return activityChances;
	}

	public void setGoogleMapsType(String googleMapsType) {
		this.googleMapsType = googleMapsType;
	}

	public void setActivityType(int activityType) {
		this.activityType = activityType;
	}

	public void setWorkersPerPlace(int workersPerPlace) {
		this.workersPerPlace = workersPerPlace;
	}

	public void setWorkersPerArea(int workersPerArea) {
		WorkersPerArea = workersPerArea;
	}

	public void setActivityChances(int[] activityChances) {
		this.activityChances = activityChances;
	}
	
	public void setActivityChances(int agIndex, int chance) {
		this.activityChances[agIndex] = chance;
	}
}
