package sensors;

import java.util.*;

public class SensorBuffer implements Buffer {
    private final static int windowSize = 8;
    private final static int windowOverlapSize = 4;


    private final ArrayList<Measurement> measurementWindow = new ArrayList<>();
    private final ArrayList<Measurement> measurements = new ArrayList<>();

    @Override
    public void addMeasurement(Measurement m) {
        this.measurementWindow.add(m);
        if (this.measurementWindow.size() == SensorBuffer.windowSize) {
            this.updateMeasurements();
        }
    }

    private synchronized void updateMeasurements() {
        Measurement sample = this.measurements.get(0);
        String id = sample.getId();
        String type = sample.getType();
        double avg = 0.0;
        for (Measurement measurement :
                this.measurementWindow) {
            avg += measurement.getValue();
        }
        this.measurements.add(new Measurement(id, type, avg / SensorBuffer.windowSize, System.currentTimeMillis()));
        this.measurementWindow.subList(0, SensorBuffer.windowOverlapSize).clear();
    }

    @Override
    public List<Measurement> readAllAndClean() {
        return this.readAndClean();
    }

    private synchronized List<Measurement> readAndClean() {
        ArrayList<Measurement> newMeasurements = new ArrayList<>();
        for (Measurement m :
                this.measurements) {
            Measurement newMeasurement = new Measurement(m.getId(), m.getType(), m.getValue(), m.getTimestamp());
            newMeasurements.add(newMeasurement);
        }
        this.measurements.clear();
        return newMeasurements;
    }
}
