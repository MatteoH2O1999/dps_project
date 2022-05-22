package seta;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;
import taxi.*;

import java.io.IOException;
import java.util.*;

class SETAProcess {
    public static void main(String[] args) {
        SETA seta = new SETA();
        seta.start();
        System.out.println("SETA started!!!");
        System.out.println("Press any key to stop...");
        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println("Something went wrong with your keyboard.");
            throw new RuntimeException(e);
        }
        seta.shutdown();
        try {
            seta.join();
        } catch (InterruptedException e) {
            System.out.println("Something went wrong while waiting for SETA termination.");
            throw new RuntimeException(e);
        }
    }
}

public class SETA extends Thread{
    private final static String address = "localhost";
    private final static int port = 1883;
    private final static long numberOfRidesToGenerate = 2;
    private final static long timeIntervalToGenerateInSeconds = 5;
    private final static int numberOfDistricts = 4;

    private final Random randomGenerator = new Random();
    private final MqttClient mqttClient;
    private volatile boolean loop = true;
    private int idCounter = 1;

    private final ArrayList<ArrayList<RideRequest>> oldRideRequests;
    private final int[] rideAck;

    public SETA() {
        this.oldRideRequests = new ArrayList<>();
        for (int i = 0; i < SETA.numberOfDistricts; i++) {
            oldRideRequests.add(new ArrayList<>());
        }
        this.rideAck = new int[SETA.numberOfDistricts];
        try {
            this.mqttClient = new MqttClient("ftp://" + SETA.address + ":" + SETA.port + "/", "1");
        } catch (MqttException e) {
            System.out.println("Error in instantiating mqtt client. Interrupting...");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        this.initializeSETA();
        while (this.loop) {
            this.sendRide(this.createRide());
            try {
                Thread.sleep(this.getNextRideInterval());
            } catch (InterruptedException e) {
                System.out.println("Thread.sleep was interrupted.");
                e.printStackTrace();
                return;
            }
        }
    }

    private void initializeSETA() {
        this.initializeMQTTClient();
        this.initializeMQTTCallback();
        this.initializeSubscriptions();
    }

    private void initializeMQTTClient() {
        // TODO
    }

    private void initializeMQTTCallback() {
        // TODO
    }

    private void initializeSubscriptions() {
        // TODO
    }

    private static boolean rideIsValid(TaxiRide taxiRide) {
        return Coordinate.getDistanceBetween(taxiRide.getStartCoordinate(), taxiRide.getArrivalCoordinate()) > 0;
    }

    private TaxiRide createRide() {
        TaxiRide ride;
        do {
            Coordinate startCoordinate = new Coordinate(this.randomGenerator.nextInt(10), this.randomGenerator.nextInt(10));
            Coordinate arrivalCoordinate = new Coordinate(this.randomGenerator.nextInt(10), this.randomGenerator.nextInt(10));
            ride = new TaxiRide(startCoordinate, arrivalCoordinate);
        } while (!SETA.rideIsValid(ride));
        return ride;
    }

    private long getNextRideInterval() {
        long expectedTimeBetweenRides = SETA.timeIntervalToGenerateInSeconds / SETA.numberOfRidesToGenerate;
        long interval = (long)this.randomGenerator.nextGaussian() + expectedTimeBetweenRides;
        return Math.abs(interval);
    }

    private synchronized void sendRide(TaxiRide taxiRide) {
        int requestId = this.idCounter;
        this.idCounter++;
        District rideDistrict = District.fromCoordinate(taxiRide.getStartCoordinate());
        RideRequest rideRequest = new RideRequest(requestId, taxiRide);
        ArrayList<RideRequest> oldRides = this.oldRideRequests.get(rideDistrict.getId());
        RideRequestPinned rideRequestPinned = new RideRequestPinned(rideRequest, oldRides);

        Gson gson = new Gson();
        String jsonRequest = gson.toJson(rideRequestPinned);
        String topic = MQTTTopics.getRideTopic(rideDistrict);

        MqttMessage message = new MqttMessage(jsonRequest.getBytes());
        message.setRetained(true);
        message.setQos(1);
        try {
            this.mqttClient.publish(topic, message);
        } catch (MqttException e) {
            System.out.println("Error while sending the message.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void updateRideList(District d) {
        List<RideRequest> listToUpdate = this.oldRideRequests.get(d.getId());
        int lastConfirmedAck = this.rideAck[d.getId()];
        listToUpdate.removeIf(request -> request.getRequestId() <= lastConfirmedAck);
    }

    private void sendUpdate(District d) {
        RideRequestPinned rideRequestPinned = new RideRequestPinned();
        rideRequestPinned.setNewRide(null);
        rideRequestPinned.setRequestId(-1);
        rideRequestPinned.setOldRides(this.oldRideRequests.get(d.getId()));

        Gson gson = new Gson();
        String topic = MQTTTopics.getRideTopic(d);
        String jsonUpdate = gson.toJson(rideRequestPinned);

        MqttMessage message = new MqttMessage(jsonUpdate.getBytes());
        message.setQos(1);
        message.setRetained(true);
        try {
            this.mqttClient.publish(topic, message);
        } catch (MqttException e) {
            System.out.println("Error while sending the message.");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private synchronized void updateAck(District d, int newValue) {
        int oldValue = this.rideAck[d.getId()];
        this.rideAck[d.getId()] = newValue;
        if (oldValue != newValue) {
            this.updateRideList(d);
            this.sendUpdate(d);
        }
    }

    public void shutdown() {
        this.loop = false;
    }
}
