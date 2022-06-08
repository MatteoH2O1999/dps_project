package taxi.FSM.States;

import seta.*;
import taxi.*;
import taxi.FSM.TaxiState;
import taxi.communication.TaxiComms;

public class Idle implements TaxiState {
    public final static Idle IDLE = new Idle();

    @Override
    public void execute(Taxi taxi) {
        if (taxi.getExitRequested()) {
            taxi.setCurrentState(Terminating.TERMINATING);
            return;
        } else if (taxi.getRechargeRequested()) {
            taxi.setCurrentState(Recharging.RECHARGING);
            return;
        }
        RideRequest nextRequest = taxi.getNextRequest();
        if ((nextRequest != null) && (taxi.getCompletedElectionAck() < nextRequest.getRequestId()) && (taxi.getReceivedElectionAck() <= nextRequest.getRequestId())) {
            taxi.stateInfo.setCurrentRequest(nextRequest);
            taxi.setCurrentState(Electing.ELECTING);
        } else {
            try {
                taxi.awaitChange();
            } catch (InterruptedException e) {
                System.out.println("Interrupted wait in IDLE.execute");
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        Coordinate rideStartingCoordinate = new Coordinate(rideRequest.getRideInfo().getRideStart());
        Coordinate currentTaxiPosition = taxi.getCurrentPosition();
        if (!District.fromCoordinate(rideStartingCoordinate).equals(District.fromCoordinate(currentTaxiPosition))) {
            return false;
        }
        if (rideRequest.getRideInfo().getRequestId() <= taxi.getCompletedElectionAck()) {
            return true;
        }
        if (rideRequest.getRideInfo().getRequestId() < taxi.getReceivedElectionAck()) {
            return true;
        }
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in IDLE.decide");
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        return true;
    }

    @Override
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        taxi.addTaxi(taxiInfo);
        return true;
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        taxi.removeTaxi(taxiInfo);
        return true;
    }
}
