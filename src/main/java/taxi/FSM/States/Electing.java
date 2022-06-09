package taxi.FSM.States;

import com.google.gson.Gson;
import io.grpc.*;
import org.eclipse.paho.client.mqttv3.*;
import seta.*;
import taxi.*;
import taxi.FSM.*;
import taxi.communication.*;

import java.util.*;

public class Electing implements TaxiState {
    public final static Electing ELECTING = new Electing();

    @Override
    public void execute(Taxi taxi) {
        Boolean decision = taxi.getDecision(taxi.stateInfo.getCurrentRequest().getRequestId());
        boolean valid;
        if (decision == null) {
            List<TaxiInfo> taxis = taxi.getTaxis();
            List<ElectionThread> threads = new ArrayList<>();
            for (TaxiInfo taxiInfo :
                    taxis) {
                threads.add(new ElectionThread(taxiInfo, taxi));
            }
            threads.forEach(Thread::start);
            try {
                for (Thread t :
                        threads) {
                    t.join();
                }
            } catch (InterruptedException e) {
                System.out.println("Interrupted join in ELECTING.execute");
                throw new RuntimeException(e);
            }
            valid = true;
            for (ElectionThread t :
                    threads) {
                if (!t.isElected()) {
                    valid = false;
                    break;
                }
            }
            taxi.setDecision(taxi.stateInfo.getCurrentRequest().getRequestId(), valid);
        } else {
            valid = decision;
        }
        if (valid) {
            RideAck rideAck = new RideAck(taxi.stateInfo.getCurrentRequest().getRequestId());
            Gson gson = new Gson();
            String jsonMessage = gson.toJson(rideAck);
            MqttMessage message = new MqttMessage(jsonMessage.getBytes());
            message.setRetained(true);
            message.setQos(1);
            String topic = MQTTTopics.getAckTopic(District.fromCoordinate(taxi.getCurrentPosition()));
            try {
                taxi.mqttClient.publish(topic, message);
            } catch (MqttException e) {
                System.out.println("Error in sending message to MQTT topic: " + topic);
                throw new RuntimeException(e);
            }
            while (taxi.getCompletedElectionAck() < taxi.stateInfo.getCurrentRequest().getRequestId()) {
                try {
                    taxi.awaitChange();
                } catch (InterruptedException e) {
                    System.out.println("Wait interrupted in ELECTING.execute");
                    throw new RuntimeException(e);
                }
            }
            taxi.setCurrentState(Riding.RIDING);
        } else {
            taxi.stateInfo.clearCurrentRequest();
            taxi.setCurrentState(Idle.IDLE);
        }
    }

    @Override
    public Decision decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        Coordinate currentTaxiPosition = taxi.getCurrentPosition();
        Coordinate otherTaxiPosition = new Coordinate(rideRequest.getTaxiState().getCurrentPosition());
        Coordinate rideStartingCoordinate = new Coordinate(rideRequest.getRideInfo().getRideStart());
        if (!District.fromCoordinate(rideStartingCoordinate).equals(District.fromCoordinate(currentTaxiPosition))) {
            return new Decision(false, true);
        }
        if (rideRequest.getRideInfo().getRequestId() <= taxi.getCompletedElectionAck()) {
            return new Decision(true, true);
        }
        if (taxi.stateInfo.getCurrentRequest().getRequestId() != rideRequest.getRideInfo().getRequestId()) {
            try {
                taxi.awaitChange();
            } catch (InterruptedException e) {
                System.out.println("Interrupted wait in ELECTING.decide");
            }
            return null;
        }
        if (currentTaxiPosition.getDistanceTo(rideStartingCoordinate) > otherTaxiPosition.getDistanceTo(rideStartingCoordinate)) {
            return new Decision(false, true);
        }
        if (currentTaxiPosition.getDistanceTo(rideStartingCoordinate) < otherTaxiPosition.getDistanceTo(rideStartingCoordinate)) {
            return new Decision(true, false);
        }
        float currentTaxiBattery = taxi.getBattery();
        float otherBattery = rideRequest.getTaxiState().getBattery();
        if (currentTaxiBattery < otherBattery) {
            return new Decision(false, true);
        }
        if (currentTaxiBattery > otherBattery) {
            return new Decision(true, false);
        }
        int taxiId = taxi.id;
        int otherId = rideRequest.getTaxiId();
        if (taxiId > otherId) {
            return new Decision(true, false);
        }
        return new Decision(false, true);
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        return true;
    }

    @Override
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in ELECTING.addTaxi");
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in ELECTING.removeTaxi");
            throw new RuntimeException(e);
        }
        return null;
    }
}


class ElectionThread extends Thread {
    private final TaxiInfo taxiInfo;
    private final int taxiId;
    private final float battery;
    private final int currentX, currentY;
    private final int requestId;
    private final int rideStartX, rideStartY;
    private boolean elected;

    public ElectionThread(TaxiInfo taxiInfo, Taxi taxi) {
        this.taxiInfo = taxiInfo;
        this.taxiId = taxi.id;
        this.battery = taxi.getBattery();
        Coordinate currentPosition = taxi.getCurrentPosition();
        this.currentX = currentPosition.getX();
        this.currentY = currentPosition.getY();
        RideRequest request = taxi.stateInfo.getCurrentRequest();
        this.requestId = request.getRequestId();
        Coordinate startCoordinate = request.getRide().getStartCoordinate();
        this.rideStartX = startCoordinate.getX();
        this.rideStartY = startCoordinate.getY();
        this.elected = false;
    }

    @Override
    public void run() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(taxiInfo.getIpAddress(), taxiInfo.getPort()).usePlaintext().build();
        TaxiCommunicationGrpc.TaxiCommunicationBlockingStub stub = TaxiCommunicationGrpc.newBlockingStub(channel);
        TaxiComms.TaxiRideRequest request = TaxiComms.TaxiRideRequest.newBuilder()
                .setTaxiId(this.taxiId)
                .setTaxiState(TaxiComms.TaxiRideRequest.TaxiState.newBuilder()
                        .setBattery(this.battery)
                        .setCurrentPosition(TaxiComms.Coordinates.newBuilder()
                                .setX(this.currentX)
                                .setY(this.currentY)
                                .build())
                        .build())
                .setRideInfo(TaxiComms.TaxiRideRequest.RideInformation.newBuilder()
                        .setRequestId(this.requestId)
                        .setRideStart(TaxiComms.Coordinates.newBuilder()
                                .setX(this.rideStartX)
                                .setY(this.rideStartY)
                                .build())
                        .build())
                .build();
        TaxiComms.TaxiRideResponse response = stub.requestRide(request);
        channel.shutdown();
        this.elected = response.getOk();
    }

    public boolean isElected() {
        if (this.isAlive()) {
            throw new RuntimeException("Thread is still alive");
        }
        return elected;
    }
}
