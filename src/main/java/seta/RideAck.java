package seta;

public class RideAck {
    private int rideAck;

    public RideAck() {
    }

    public RideAck(int rideAck) {
        this.rideAck = rideAck;
    }

    public int getRideAck() {
        return rideAck;
    }

    public void setRideAck(int rideAck) {
        this.rideAck = rideAck;
    }

    @Override
    public String toString() {
        return "Ride ack: " + this.rideAck;
    }
}
