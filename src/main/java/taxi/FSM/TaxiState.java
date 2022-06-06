package taxi.FSM;

import taxi.*;
import taxi.communication.TaxiComms;

public interface TaxiState {
    void execute(Taxi taxi);
    Boolean decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest);
    Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest);
    void addTaxi(TaxiInfo taxi);
    void removeTaxi(TaxiInfo taxi);
}
