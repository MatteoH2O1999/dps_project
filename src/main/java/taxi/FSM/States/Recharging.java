package taxi.FSM.States;

import taxi.*;
import taxi.FSM.TaxiState;
import taxi.communication.TaxiComms;

public class Recharging implements TaxiState {
    public final static Recharging RECHARGING = new Recharging();

    @Override
    public void execute(Taxi taxi) {
    }

    @Override
    public Boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        return null;
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        return null;
    }

    @Override
    public void addTaxi(TaxiInfo taxi) {
    }

    @Override
    public void removeTaxi(TaxiInfo taxi) {
    }
}