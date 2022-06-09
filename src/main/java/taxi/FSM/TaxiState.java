package taxi.FSM;

import taxi.*;
import taxi.communication.TaxiComms;

public interface TaxiState {
    void execute(Taxi taxi);
    Decision decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest);
    Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest);
    Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo);
    Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo);
}
