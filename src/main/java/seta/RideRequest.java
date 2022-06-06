package seta;

import taxi.TaxiRide;

public class RideRequest {
    private TaxiRide ride;
    private int requestId;

    public RideRequest() {
    }

    public RideRequest(int requestId, TaxiRide ride) {
        this.requestId = requestId;
        this.ride = ride;
    }

    public int getRequestId() {
        return requestId;
    }

    public TaxiRide getRide() {
        return ride;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setRide(TaxiRide ride) {
        this.ride = ride;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RideRequest)) {
            return false;
        }
        RideRequest other = (RideRequest) obj;
        return ((other.ride.equals(this.ride)) && (other.requestId == this.requestId));
    }

    @Override
    public String toString() {
        return "ID: " + this.requestId + "\tRide: " + this.ride;
    }
}
