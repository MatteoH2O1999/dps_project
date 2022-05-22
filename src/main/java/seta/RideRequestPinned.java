package seta;

import taxi.TaxiRide;

import java.util.ArrayList;

public class RideRequestPinned {
    private TaxiRide newRide;
    private ArrayList<RideRequest> oldRides;
    private int requestId;

    public RideRequestPinned() {
    }

    public RideRequestPinned(int requestId, TaxiRide newRide, ArrayList<RideRequest> oldRides) {
        this.requestId = requestId;
        this.newRide = newRide;
        this.oldRides = oldRides;
    }

    public RideRequestPinned(RideRequest request, ArrayList<RideRequest> oldRides) {
        this.requestId = request.getRequestId();
        this.newRide = request.getRide();
        this.oldRides = oldRides;
    }

    public int getRequestId() {
        return requestId;
    }

    public TaxiRide getNewRide() {
        return newRide;
    }

    public ArrayList<RideRequest> getOldRides() {
        return oldRides;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setNewRide(TaxiRide newRide) {
        this.newRide = newRide;
    }

    public void setOldRides(ArrayList<RideRequest> oldRides) {
        this.oldRides = oldRides;
    }
}
