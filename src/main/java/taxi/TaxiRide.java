package taxi;

import seta.Coordinate;

public class TaxiRide {
    private Coordinate startCoordinate, arrivalCoordinate;

    public TaxiRide() {
    }

    public TaxiRide(Coordinate startCoordinate, Coordinate arrivalCoordinate) {
        this.startCoordinate = startCoordinate;
        this.arrivalCoordinate = arrivalCoordinate;
    }

    public Coordinate getStartCoordinate() {
        return startCoordinate;
    }

    public Coordinate getArrivalCoordinate() {
        return arrivalCoordinate;
    }

    public void setStartCoordinate(Coordinate startCoordinate) {
        this.startCoordinate = startCoordinate;
    }

    public void setArrivalCoordinate(Coordinate arrivalCoordinate) {
        this.arrivalCoordinate = arrivalCoordinate;
    }

    @Override
    public String toString() {
        return startCoordinate + " -> " + arrivalCoordinate;
    }
}
