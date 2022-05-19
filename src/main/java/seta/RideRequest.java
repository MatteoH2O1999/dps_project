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
}
