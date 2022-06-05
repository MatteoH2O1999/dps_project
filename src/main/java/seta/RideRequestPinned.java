package seta;

import taxi.TaxiRide;

import java.util.ArrayList;

public class RideRequestPinned {
    private TaxiRide newRide;
    private ArrayList<RideRequest> allRides;
    private int requestId;

    public RideRequestPinned() {
    }

    public RideRequestPinned(int requestId, TaxiRide newRide, ArrayList<RideRequest> allRides) {
        this.requestId = requestId;
        this.newRide = newRide;
        this.allRides = allRides;
    }

    public RideRequestPinned(RideRequest request, ArrayList<RideRequest> allRides) {
        this.requestId = request.getRequestId();
        this.newRide = request.getRide();
        this.allRides = allRides;
    }

    public int getRequestId() {
        return requestId;
    }

    public TaxiRide getNewRide() {
        return newRide;
    }

    public ArrayList<RideRequest> getAllRides() {
        return allRides;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setNewRide(TaxiRide newRide) {
        this.newRide = newRide;
    }

    public void setAllRides(ArrayList<RideRequest> allRides) {
        this.allRides = allRides;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RideRequestPinned)) {
            return false;
        }
        RideRequestPinned other = (RideRequestPinned) obj;
        return ((other.allRides.equals(this.allRides)) && (other.newRide.equals(this.newRide)) && (other.requestId == this.requestId));
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Request ID: ").append(this.requestId).append("\n");
        stringBuilder.append("New ride: ").append(this.newRide).append("\n");
        stringBuilder.append("[");
        for (RideRequest rideRequest :
                this.getAllRides()) {
            stringBuilder.append("\n").append(rideRequest).append("\n");
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
