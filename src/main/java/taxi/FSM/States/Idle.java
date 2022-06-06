package taxi.FSM.States;

import taxi.*;
import taxi.FSM.TaxiState;
import taxi.communication.TaxiComms;

public class Idle implements TaxiState {
    public final static Idle IDLE = new Idle();

    @Override
    public void execute(Taxi taxi) {
    }

    @Override
    public Boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
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
    public void addTaxi(TaxiInfo taxi) {
    }

    @Override
    public void removeTaxi(TaxiInfo taxi) {
    }
}
