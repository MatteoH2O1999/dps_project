package taxi.FSM.States;

import taxi.*;
import taxi.FSM.TaxiState;
import taxi.communication.TaxiComms;

public class Riding implements TaxiState {
    public final static Riding RIDING = new Riding();

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
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        return null;
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        return null;
    }
}
