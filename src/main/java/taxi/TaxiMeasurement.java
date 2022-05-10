package taxi;

import sensors.Measurement;

import java.util.ArrayList;

public class TaxiMeasurement {
    private float km;
    private float battery;
    private ArrayList<Measurement> pollutionMeasurements = new ArrayList<>();

    public TaxiMeasurement() {
    }

    public TaxiMeasurement(float km, float battery, ArrayList<Measurement> pollutionMeasurements) {
        if (km <= 0) {
            throw new IllegalArgumentException("Expected positive distance");
        }
        if (battery < 0 || battery > 100) {
            throw new IllegalArgumentException(("Expected battery level between 0 and 100"));
        }
    }

    public float getKm() {
        return km;
    }

    public float getBattery() {
        return battery;
    }

    public ArrayList<Measurement> getPollutionMeasurements() {
        ArrayList<Measurement> newMeasurements = new ArrayList<>();
        for (Measurement m :
                this.pollutionMeasurements) {
            newMeasurements.add(new Measurement(m.getId(), m.getType(), m.getValue(), m.getTimestamp()));
        }
        return newMeasurements;
    }

    public void setKm(float km) {
        this.km = km;
    }

    public void setBattery(float battery) {
        this.battery = battery;
    }

    public void setPollutionMeasurements(ArrayList<Measurement> pollutionMeasurements) {
        this.pollutionMeasurements = new ArrayList<>();
        for (Measurement m :
                pollutionMeasurements) {
            this.pollutionMeasurements.add(new Measurement(m.getId(), m.getType(), m.getValue(), m.getTimestamp()));
        }
    }
}
