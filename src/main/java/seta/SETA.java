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
    public final static String address = "localhost";
    public final static int port = 1883;
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
            this.mqttClient = new MqttClient("tcp://" + SETA.address + ":" + SETA.port, "1");
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
                Thread.sleep(this.getNextRideInterval() * 1000);
            } catch (InterruptedException e) {
                System.out.println("Thread.sleep was interrupted.");
                e.printStackTrace();
                return;
            }
        }
        this.finalizeSETA();
    }

    private void initializeSETA() {
        this.initializeMQTTClient();
        this.initializeMQTTCallback();
        this.initializeSubscriptions();
    }

    private void initializeMQTTClient() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setKeepAliveInterval(60);
        try {
            this.mqttClient.connect(mqttConnectOptions);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeMQTTCallback() {
        this.mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                throw new RuntimeException(cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                System.out.println("Received message from topic " + topic);
                Gson gson = new Gson();
                if (MQTTTopics.isAckTopic(topic)) {
                    District d = MQTTTopics.districtFromTopic(topic);
                    RideAck acks = gson.fromJson(new String(message.getPayload()), RideAck.class);
                    System.out.println("Received message with the following payload:");
                    System.out.println("\tDistrict: " + d.getId());
                    System.out.println("\tAcks: " + acks + "\n");
                    int ack = acks.getRideAck();
                    updateAck(d, ack);
                } else if (MQTTTopics.isRideTopic(topic)) {
                    throw new RuntimeException("Taxis should not write to a ride topic.");
                } else {
                    throw new RuntimeException("I don't know how you even got here...");
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
    }

    private void initializeSubscriptions() {
        try {
            this.mqttClient.subscribe(MQTTTopics.getAckTopic(null));
        } catch (MqttException e) {
            System.out.println("Failure in subscribing to ack topics.");
            throw new RuntimeException(e);
        }
    }

    private void finalizeSETA() {
        try {
            this.mqttClient.unsubscribe(MQTTTopics.getAckTopic(null));
        } catch (MqttException e) {
            System.out.println("Failure in unsubscribing from ack topics.");
            throw new RuntimeException(e);
        }
        try {
            this.mqttClient.disconnect();
        } catch (MqttException e) {
            System.out.println("Failed to disconnect the client");
            throw new RuntimeException(e);
        }
        try {
            this.mqttClient.close();
        } catch (MqttException e) {
            System.out.println("Failed to close the client.");
            throw new RuntimeException(e);
        }
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
        System.out.println("Sending new ride...");
        int requestId = this.idCounter;
        this.idCounter++;
        District rideDistrict = District.fromCoordinate(taxiRide.getStartCoordinate());
        RideRequest rideRequest = new RideRequest(requestId, taxiRide);
        ArrayList<RideRequest> oldRides = this.oldRideRequests.get(rideDistrict.getId() - 1);
        oldRides.add(rideRequest);
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
            throw new RuntimeException(e);
        }
        System.out.println("Sent message on topic " + topic);
        System.out.println("with payload:");
        System.out.println(rideRequestPinned);
    }

    private void updateRideList(District d) {
        List<RideRequest> listToUpdate = this.oldRideRequests.get(d.getId() - 1);
        int lastConfirmedAck = this.rideAck[d.getId()];
        listToUpdate.removeIf(request -> request.getRequestId() <= lastConfirmedAck);
    }

    private void sendUpdate(District d) {
        System.out.println("Sending update after receiving ack...");
        RideRequestPinned rideRequestPinned = new RideRequestPinned();
        rideRequestPinned.setNewRide(null);
        rideRequestPinned.setRequestId(-1);
        rideRequestPinned.setAllRides(this.oldRideRequests.get(d.getId() - 1));

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
            throw new RuntimeException(e);
        }
        System.out.println("Sent message on topic " + topic);
        System.out.println("with payload:");
        System.out.println(rideRequestPinned);
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
