package taxi.FSM.States;

import taxi.*;
import taxi.FSM.TaxiState;
import taxi.communication.TaxiComms;

public class Electing implements TaxiState {
    public final static Electing ELECTING = new Electing();

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
