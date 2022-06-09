package taxi.FSM.States;

import io.grpc.*;
import org.eclipse.paho.client.mqttv3.MqttException;
import seta.*;
import taxi.*;
import taxi.FSM.*;
import taxi.communication.*;

import java.util.*;

public class Terminating implements TaxiState {
    public final static Terminating TERMINATING = new Terminating();

    @Override
    public void execute(Taxi taxi) {
        District currentDistrict = District.fromCoordinate(taxi.getCurrentPosition());
        taxi.mqttUnsubscribe(MQTTTopics.getAckTopic(currentDistrict));
        taxi.mqttUnsubscribe(MQTTTopics.getRideTopic(currentDistrict));
        List<TaxiInfo> taxis = taxi.getTaxis();
        Map<Integer, TerminatingThread> terminatingThreads = new HashMap<>();
        Map<Integer, Thread> threads = new HashMap<>();
        for (TaxiInfo taxiInfo :
                taxis) {
            TerminatingThread thread = new TerminatingThread(taxiInfo, taxi);
            threads.put(taxiInfo.getId(), thread);
            terminatingThreads.put(taxiInfo.getId(), thread);
        }
        terminatingThreads.forEach((integer, thread) -> thread.start());
        taxi.stateInfo.setClosingThreads(threads);
        terminatingThreads.forEach((integer, thread) -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.out.println("Interrupted join in TERMINATING.execute");
                throw new RuntimeException(e);
            }
        });
        terminatingThreads.forEach((integer, terminatingThread) -> {
            if (!terminatingThread.isValid()) {
                throw new RuntimeException("Error in grpc remove taxi in TERMINATING.execute");
            }
        });
        try {
            taxi.mqttClient.disconnect();
            taxi.mqttClient.close();
        } catch (MqttException e) {
            System.out.println("Error in disconnecting from MQTT in TERMINATING.execute");
            throw new RuntimeException(e);
        }
        taxi.server.shutdown();
        try {
            taxi.stopSensors();
        } catch (InterruptedException e) {
            System.out.println("Join interrupted while stopping sensors");
            throw new RuntimeException(e);
        }
        taxi.setExitRequested(false);
        taxi.stopRunning();
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
        throw new RuntimeException("How???");
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        try {
            taxi.stateInfo.interruptClosingThread(taxiInfo.getId());
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in TERMINATING.removeTaxi");
            throw new RuntimeException(e);
        }
        return true;
    }
}


class TerminatingThread extends Thread {
    private boolean completed;
    private final TaxiInfo taxiInfo;
    private final String address;
    private final int id;
    private final int port;

    public TerminatingThread(TaxiInfo taxiInfo, Taxi taxi) {
        this.taxiInfo = taxiInfo;
        this.completed = false;
        this.address = taxi.address;
        this.id = taxi.id;
        this.port = taxi.port;
    }

    @Override
    public void run() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(taxiInfo.getIpAddress(), taxiInfo.getPort()).usePlaintext().build();
        TaxiCommunicationGrpc.TaxiCommunicationBlockingStub stub = TaxiCommunicationGrpc.newBlockingStub(channel);
        TaxiComms.TaxiRemovalRequest request = TaxiComms.TaxiRemovalRequest.newBuilder()
                .setAddress(this.address)
                .setId(this.id)
                .setPort(this.port)
                .build();
        TaxiComms.TaxiRemovalResponse response = stub.requestLeave(request);
        if (!response.getOk()) {
            throw new RuntimeException("Error in getting taxi removed from: " + taxiInfo);
        }
        this.setCompleted();
    }

    public boolean isValid() {
        return this.isInterrupted() || this.isCompleted();
    }

    private synchronized boolean isCompleted() {
        return completed;
    }

    private synchronized void setCompleted() {
        this.completed = true;
    }
}
