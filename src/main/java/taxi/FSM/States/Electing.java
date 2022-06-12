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
        System.out.println("Starting election for request " + taxi.stateInfo.getCurrentRequest());
        Boolean decision = taxi.getDecision(taxi.stateInfo.getCurrentRequest().getRequestId());
        boolean valid;
        if (decision == null) {
            System.out.println("No previous decision was found. Asking the network for permission to take care of the ride...");
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
            System.out.println("Decision after election: " + valid);
            System.out.println("Saving decision...");
            taxi.setDecision(taxi.stateInfo.getCurrentRequest().getRequestId(), valid);
        } else {
            System.out.println("Decision already found: " + decision);
            valid = decision;
        }
        if (valid) {
            System.out.println("Preparing to take care of ride...");
            RideAck rideAck = new RideAck(taxi.stateInfo.getCurrentRequest().getRequestId());
            Gson gson = new Gson();
            String jsonMessage = gson.toJson(rideAck);
            MqttMessage message = new MqttMessage(jsonMessage.getBytes());
            message.setRetained(true);
            message.setQos(1);
            String topic = MQTTTopics.getAckTopic(District.fromCoordinate(taxi.getCurrentPosition()));
            System.out.println("Sending ack update: " + rideAck);
            try {
                taxi.mqttClient.publish(topic, message);
            } catch (MqttException e) {
                System.out.println("Error in sending message to MQTT topic: " + topic);
                throw new RuntimeException(e);
            }
            System.out.println("Waiting for ack confirmation...");
            while (taxi.getCompletedElectionAck() < taxi.stateInfo.getCurrentRequest().getRequestId()) {
                try {
                    taxi.awaitChange();
                } catch (InterruptedException e) {
                    System.out.println("Wait interrupted in ELECTING.execute");
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Starting ride...");
            taxi.setCurrentState(Riding.RIDING);
        } else {
            System.out.println("Not taking care of this ride...");
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
            System.out.println("Request " + rideRequest + " is from another district. Other taxi can take care of it...");
            return new Decision(false, true);
        }
        if (rideRequest.getRideInfo().getRequestId() <= taxi.getCompletedElectionAck()) {
            System.out.println("Request " + rideRequest + " is already been satisfied. Stopping the election...");
            return new Decision(true, true);
        }
        if (taxi.stateInfo.getCurrentRequest().getRequestId() != rideRequest.getRideInfo().getRequestId()) {
            try {
                System.out.println("Not electing for request " + rideRequest + ". Waiting for change...");
                taxi.awaitChange();
            } catch (InterruptedException e) {
                System.out.println("Interrupted wait in ELECTING.decide");
            }
            return null;
        }
        if (currentTaxiPosition.getDistanceTo(rideStartingCoordinate) > otherTaxiPosition.getDistanceTo(rideStartingCoordinate)) {
            System.out.println("Other taxi is nearer to satisfy request " + rideRequest);
            return new Decision(false, true);
        }
        if (currentTaxiPosition.getDistanceTo(rideStartingCoordinate) < otherTaxiPosition.getDistanceTo(rideStartingCoordinate)) {
            System.out.println("I'm nearer to satisfy request " + rideRequest);
            return new Decision(true, false);
        }
        System.out.println("We have the same distance from request " + rideRequest);
        float currentTaxiBattery = taxi.getBattery();
        float otherBattery = rideRequest.getTaxiState().getBattery();
        if (currentTaxiBattery < otherBattery) {
            System.out.println("Other battery has more charge...");
            return new Decision(false, true);
        }
        if (currentTaxiBattery > otherBattery) {
            System.out.println("My battery has more charge...");
            return new Decision(true, false);
        }
        System.out.println("Our batteries have the same charge...");
        int taxiId = taxi.id;
        int otherId = rideRequest.getTaxiId();
        if (taxiId > otherId) {
            System.out.println("My ID is greater than the other taxi's...");
            return new Decision(true, false);
        }
        System.out.println("The other taxi has a greater ID than mine...");
        return new Decision(false, true);
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        System.out.println("I'm electing. Recharge request " + rechargeRequest + " is granted...");
        return true;
    }

    @Override
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        System.out.println("Cannot add taxi while electing. Waiting for change...");
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
        System.out.println("Cannot remove taxi while electing. Waiting for change...");
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
