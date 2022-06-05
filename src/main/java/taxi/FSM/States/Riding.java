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
    public boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        return false;
    }

    @Override
    public void addTaxi(TaxiInfo taxi) {
    }

    @Override
    public void removeTaxi(TaxiInfo taxi) {
    }
}
