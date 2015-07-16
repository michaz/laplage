package playground.mzilske.latitude;

public class Location {
	private long accuracy;
	private long timestampMs;
	private double longitude;
	private double latitude;

	public long getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(long accuracy) {
		this.accuracy = accuracy;
	}

	public long getTimestampMs() {
		return timestampMs;
	}

	public void setTimestampMs(long timestampMs) {
		this.timestampMs = timestampMs;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
}
