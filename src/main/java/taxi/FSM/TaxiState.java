package taxi.FSM;

import taxi.*;
import taxi.communication.TaxiComms;

public interface TaxiState {
    void execute(Taxi taxi);
    boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest);
    void addTaxi(TaxiInfo taxi);
    void removeTaxi(TaxiInfo taxi);
}
