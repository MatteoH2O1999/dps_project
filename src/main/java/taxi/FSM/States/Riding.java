package taxi.FSM.States;

import seta.*;
import taxi.*;
import taxi.FSM.*;
import taxi.communication.TaxiComms;

public class Riding implements TaxiState {
    public final static Riding RIDING = new Riding();

    @Override
    public void execute(Taxi taxi) {
        District currentDistrict = District.fromCoordinate(taxi.getCurrentPosition());
        taxi.mqttUnsubscribe(MQTTTopics.getAckTopic(currentDistrict));
        taxi.mqttUnsubscribe(MQTTTopics.getRideTopic(currentDistrict));
        taxi.setCompletedElectionAck(null);
        TaxiRide ride = taxi.stateInfo.getCurrentRequest().getRide();
        taxi.setCurrentPosition(ride.getStartCoordinate());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            System.out.println("Interrupted delivery sleep in RIDING.execute");
            throw new RuntimeException(e);
        }
        taxi.setCurrentPosition(ride.getArrivalCoordinate());
        taxi.completeRide((float) Coordinate.getDistanceBetween(ride.getStartCoordinate(), ride.getArrivalCoordinate()));
        taxi.stateInfo.clearCurrentRequest();
        taxi.clearRequests();
        District newDistrict = District.fromCoordinate(taxi.getCurrentPosition());
        taxi.mqttSubscribe(MQTTTopics.getRideTopic(newDistrict), 1);
        taxi.mqttSubscribe(MQTTTopics.getAckTopic(newDistrict), 1);
        taxi.setCurrentState(Idle.IDLE);
    }

    @Override
    public Decision decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        return new Decision(false, true);
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
