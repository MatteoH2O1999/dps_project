package taxi.FSM.States;

import io.grpc.*;
import seta.*;
import taxi.*;
import taxi.FSM.*;
import taxi.communication.*;

import java.util.*;

public class Recharging implements TaxiState {
    public final static Recharging RECHARGING = new Recharging();

    @Override
    public void execute(Taxi taxi) {
        System.out.println("Starting recharge procedure...");
        District district = District.fromCoordinate(taxi.getCurrentPosition());
        taxi.mqttUnsubscribe(MQTTTopics.getAckTopic(district));
        taxi.mqttUnsubscribe(MQTTTopics.getRideTopic(district));
        taxi.setCompletedElectionAck(null);
        taxi.clearRequests();
        System.out.println("Sending recharge requests to all taxis...");
        List<TaxiInfo> taxis = taxi.getTaxis();
        List<RechargeRequestThread> threads = new ArrayList<>();
        for (TaxiInfo taxiInfo :
                taxis) {
            threads.add(new RechargeRequestThread(taxiInfo, taxi.stateInfo.getTimestamp(), district));
        }
        for (Thread t :
                threads) {
            t.start();
        }
        try {
            for (Thread t :
                    threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted join in RECHARGING.execute");
            throw new RuntimeException(e);
        }
        System.out.println("Recharge requests have all been accepted. Going to recharge station...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            System.out.println("Interrupted sleep in RECHARGING.execute");
            throw new RuntimeException(e);
        }
        taxi.setRechargeRequested(false);
        taxi.stateInfo.clearTimestamp();
        taxi.setCurrentPosition(district.getRechargeStation());
        System.out.println("Recharging...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            System.out.println("Interrupted sleep in RECHARGING.execute");
            throw new RuntimeException(e);
        }
        System.out.println("Recharge completed...");
        taxi.setBattery(100);
        taxi.mqttSubscribe(MQTTTopics.getRideTopic(district), 1);
        taxi.mqttSubscribe(MQTTTopics.getAckTopic(district), 1);
        taxi.setCurrentState(Idle.IDLE);
    }

    @Override
    public Decision decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        System.out.println("I'm recharging. Other taxis can handle this...");
        return new Decision(false, true);
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        Long timestamp = taxi.stateInfo.getTimestamp();
        if (timestamp == null) {
            System.out.println("I have not yet created my request. Other taxi comes first...");
            return true;
        }
        District currentDistrict = District.fromCoordinate(taxi.getCurrentPosition());
        District otherTaxiDistrict = District.fromCoordinate(new Coordinate(rechargeRequest.getTaxiPosition()));
        if (!currentDistrict.equals(otherTaxiDistrict)) {
            System.out.println("Recharge request " + rechargeRequest + " is for another recharge station...");
            return true;
        }
        if (rechargeRequest.getTimestamp() < timestamp) {
            System.out.println("Recharge request " + rechargeRequest + " has a lower timestamp than mine...");
            return true;
        }
        System.out.println("Recharge request " + rechargeRequest + " has a greater timestamp than mine. Waiting for me to finish recharging...");
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted await change in RECHARGING.canRecharge");
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        System.out.println("Cannot add taxi while recharging. Waiting...");
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in RECHARGING.addTaxi");
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        System.out.println("Cannot remove taxi while recharging. Waiting...");
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in RECHARGING.removeTaxi");
            throw new RuntimeException(e);
        }
        return null;
    }
}


class RechargeRequestThread extends Thread {
    private final TaxiInfo taxiInfo;
    private final long timestamp;
    private final District district;

    public RechargeRequestThread(TaxiInfo taxiInfo, long timestamp, District d) {
        this.taxiInfo = taxiInfo;
        this.timestamp = timestamp;
        this.district = d;
    }

    @Override
    public void run() {
        Coordinate requestedStationCoordinate = this.district.getRechargeStation();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(taxiInfo.getIpAddress(), taxiInfo.getPort()).usePlaintext().build();
        TaxiCommunicationGrpc.TaxiCommunicationBlockingStub stub = TaxiCommunicationGrpc.newBlockingStub(channel);
        TaxiComms.TaxiRechargeRequest taxiRechargeRequest = TaxiComms.TaxiRechargeRequest.newBuilder()
                .setTimestamp(this.timestamp)
                .setTaxiPosition(TaxiComms.Coordinates.newBuilder()
                        .setX(requestedStationCoordinate.getX())
                        .setY(requestedStationCoordinate.getY())
                        .build())
                .build();
        TaxiComms.TaxiRechargeResponse response = stub.requestRecharge(taxiRechargeRequest);
        if (response == null) {
            throw new RuntimeException("Error in response from taxi:" + this.taxiInfo);
        }
        channel.shutdown();
    }
}
