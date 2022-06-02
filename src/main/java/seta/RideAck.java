package seta;

public class RideAck {
    private int electionAck;
    private int rideAck;

    public RideAck() {
    }

    public RideAck(int electionAck, int rideAck) {
        this.electionAck = electionAck;
        this.rideAck = rideAck;
    }

    public int getElectionAck() {
        return electionAck;
    }

    public int getRideAck() {
        return rideAck;
    }

    public void setElectionAck(int electionAck) {
        this.electionAck = electionAck;
    }

    public void setRideAck(int rideAck) {
        this.rideAck = rideAck;
    }

    @Override
    public String toString() {
        return "(Election ack: " + this.electionAck + "; Ride ack: " + this.rideAck + ")";
    }
}
