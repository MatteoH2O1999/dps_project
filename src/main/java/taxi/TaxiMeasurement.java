package taxi;

import sensors.Measurement;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

@XmlRootElement
public class TaxiMeasurement {
    private int id;
    private int numberOfRides;
    private float km;
    private float battery;
    private List<Measurement> pollutionMeasurements = new ArrayList<>();
    private long timestamp;

    public TaxiMeasurement() {
    }

    public TaxiMeasurement(int id, int numberOfRides, float km, float battery, List<Measurement> pollutionMeasurements, long timestamp) {
        if (km <= 0) {
            throw new IllegalArgumentException("Expected positive distance");
        }
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException(("Expected battery level between 0 and 100"));
        }
        this.id = id;
        this.numberOfRides = numberOfRides;
        this.km = km;
        this.battery = battery;
        this.pollutionMeasurements = pollutionMeasurements;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public int getNumberOfRides() {
        return numberOfRides;
    }

    public float getKm() {
        return km;
    }

    public float getBattery() {
        return battery;
    }

    public List<Measurement> getPollutionMeasurements() {
        List<Measurement> newMeasurements = new ArrayList<>();
        for (Measurement m :
                this.pollutionMeasurements) {
            newMeasurements.add(new Measurement(m.getId(), m.getType(), m.getValue(), m.getTimestamp()));
        }
        return newMeasurements;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setNumberOfRides(int numberOfRides) {
        this.numberOfRides = numberOfRides;
    }

    public void setKm(float km) {
        this.km = km;
    }

    public void setBattery(float battery) {
        this.battery = battery;
    }

    public void setPollutionMeasurements(List<Measurement> pollutionMeasurements) {
        this.pollutionMeasurements = new ArrayList<>();
        for (Measurement m :
                pollutionMeasurements) {
            this.pollutionMeasurements.add(new Measurement(m.getId(), m.getType(), m.getValue(), m.getTimestamp()));
        }
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Timestamp: ").append(this.timestamp).append("\n");
        stringBuilder.append("Taxi ID: ").append(this.id).append("\n");
        stringBuilder.append("Number of rides: ").append(this.numberOfRides).append("\n");
        stringBuilder.append("Travelled km: ").append(this.km).append("\n");
        stringBuilder.append("Battery level: ").append(this.battery).append("\n");
        stringBuilder.append("Pollution measurements:\n[");
        for (Measurement m :
                this.getPollutionMeasurements()) {
            stringBuilder.append("\n").append(m.toString()).append("\n");
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
