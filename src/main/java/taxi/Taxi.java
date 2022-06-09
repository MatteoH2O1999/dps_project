package taxi;

import com.google.gson.Gson;
import com.sun.jersey.api.client.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;
import sensors.*;
import seta.*;
import taxi.FSM.*;
import taxi.communication.*;
import taxi.communication.TaxiCommunicationGrpc.TaxiCommunicationImplBase;
import taxi.FSM.States.Idle;

import java.io.*;
import java.util.*;

class TaxiProcess {
    public static final String address = "localhost";
    public static final int port = 1338;
    public static final String addPath = "/server/add";
    public static final String infoPath = "/server/measure";
    public static final int timeBetweenMeasurements = 15;

    public static void main(String[] args) {
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        String address;
        int id, port;
        try {
            System.out.println("Insert the ID of the taxi:");
            id = Integer.parseInt(userInput.readLine());
        } catch (IOException e) {
            System.out.println("Something is wrong with your keyboard.");
            throw new RuntimeException(e);
        } catch (NumberFormatException e) {
            System.out.println("Invalid id. Must be an integer.");
            return;
        }
        try {
            System.out.println("Insert address:");
            address = userInput.readLine();
        } catch (IOException e) {
            System.out.println("Something is wrong with your keyboard.");
            throw new RuntimeException(e);
        }
        try {
            System.out.println("Insert the port of the taxi:");
            port = Integer.parseInt(userInput.readLine());
        } catch (IOException e) {
            System.out.println("Something is wrong with your keyboard.");
            throw new RuntimeException(e);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port. Must be an integer.");
            return;
        }
        Taxi taxi = new Taxi(id, address, port);
        taxi.start();
        String input;
        do {
            System.out.println("Write 'recharge' to recharge the taxi, 'quit' to gently stop the taxi or 'do nothing' to do absolutely nothing");
            try {
                System.out.println("Insert action:");
                input = userInput.readLine();
            } catch (IOException e) {
                System.out.println("Something is wrong with your keyboard.");
                throw new RuntimeException(e);
            }
            switch (input) {
                case "recharge":
                    taxi.requestRecharge();
                    break;
                case "do nothing":
                case "quit":
                    break;
                default:
                    System.out.println("Invalid action");
            }
        } while (!input.equals("quit"));
        taxi.requestExit();
        try {
            taxi.join();
        } catch (InterruptedException e) {
            System.out.println("Something happened during controlled termination");
            throw new RuntimeException(e);
        }
    }
}

public class Taxi extends Thread{
    public final int id;
    public final String address;
    public final int port;
    public final TaxiStateInfo stateInfo = new TaxiStateInfo();

    public Server server;
    public MqttClient mqttClient;

    private final Buffer sensorBuffer;
    private final PM10Simulator pm10Sensor;
    private final InformationThread informationThread;

    private volatile boolean running = true;
    private TaxiState currentState;

    private final List<TaxiInfo> otherTaxis = new ArrayList<>();
    private Coordinate currentPosition;
    private float battery = 100.0F;
    private int completedRides = 0;
    private float completedKm = 0.0F;

    private Integer completedElectionAck = null;
    private List<RideRequest> requests = null;
    private final HashMap<Integer, Boolean> decisions = new HashMap<>();

    private volatile Boolean rechargeRequested = false;
    private volatile Boolean exitRequested = false;

    public Taxi(int id, String address, int port) {
        Client client = Client.create();
        String webAddress = "http://" + TaxiProcess.address + ":" + TaxiProcess.port + TaxiProcess.addPath;
        WebResource webResource = client.resource(webAddress);
        TaxiInfo taxiRequest = new TaxiInfo(id, address, port);
        String request = new Gson().toJson(taxiRequest);
        ClientResponse clientResponse = webResource.type("application/json").post(ClientResponse.class, request);
        if (clientResponse.getStatus() != 200) {
            throw new IllegalArgumentException("One of the parameters was invalid. The id is probably in use.");
        }
        TaxiInsertionResponse taxiInsertionResponse = clientResponse.getEntity(TaxiInsertionResponse.class);
        this.currentPosition = taxiInsertionResponse.getStartingPosition();
        this.id = id;
        this.address = address;
        this.port = port;
        this.otherTaxis.clear();
        List<TaxiInfo> taxis = taxiInsertionResponse.getTaxis();
        if (taxis == null) {
            taxis = new ArrayList<>();
        }
        this.otherTaxis.addAll(taxis);
        this.sensorBuffer = new SensorBuffer();
        this.pm10Sensor = new PM10Simulator(this.sensorBuffer);
        this.pm10Sensor.start();
        this.initializeState();
        this.initializeGrpc();
        List<GreetingThread> threads = new ArrayList<>();
        for (TaxiInfo info :
                this.otherTaxis) {
            threads.add(new GreetingThread(info));
        }
        for (GreetingThread t :
                threads) {
            t.start();
        }
        try {
            for (GreetingThread t :
                    threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted while greeting");
            throw new RuntimeException(e);
        }
        try {
            this.initializeMQTT();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        this.informationThread = new InformationThread(this, this.sensorBuffer);
        this.informationThread.start();
    }

    public Taxi(Coordinate startingPosition, TaxiInfo taxiInfo, List<TaxiInfo> otherTaxis) {
        this.currentPosition = startingPosition;
        this.id = taxiInfo.getId();
        this.address = taxiInfo.getIpAddress();
        this.port = taxiInfo.getPort();
        this.otherTaxis.clear();
        if (otherTaxis == null) {
            otherTaxis = new ArrayList<>();
        }
        this.otherTaxis.addAll(otherTaxis);
        this.sensorBuffer = new SensorBuffer();
        this.pm10Sensor = new PM10Simulator(this.sensorBuffer);
        this.pm10Sensor.start();
        this.initializeState();
        this.initializeGrpc();
        List<GreetingThread> threads = new ArrayList<>();
        for (TaxiInfo info :
                this.otherTaxis) {
            threads.add(new GreetingThread(info));
        }
        for (GreetingThread t :
                threads) {
            t.start();
        }
        try {
            for (GreetingThread t :
                    threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted while greeting");
            throw new RuntimeException(e);
        }
        try {
            this.initializeMQTT();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        this.informationThread = new InformationThread(this, this.sensorBuffer);
        this.informationThread.start();
    }

    @Override
    public void run() {
        this.mqttSubscribe(MQTTTopics.getRideTopic(District.fromCoordinate(this.getCurrentPosition())), 1);
        this.mqttSubscribe(MQTTTopics.getAckTopic(District.fromCoordinate(this.getCurrentPosition())), 1);
        while (this.running) {
            this.getCurrentState().execute(this);
        }
    }

    private void sayHello(TaxiInfo taxiInfo) {
        Coordinate coordinate = this.getCurrentPosition();
        ManagedChannel channel = ManagedChannelBuilder.forAddress(taxiInfo.getIpAddress(), taxiInfo.getPort()).usePlaintext().build();
        TaxiCommunicationGrpc.TaxiCommunicationBlockingStub stub = TaxiCommunicationGrpc.newBlockingStub(channel);
        TaxiComms.TaxiGreeting greetingRequest = TaxiComms.TaxiGreeting.newBuilder()
                .setTaxiInfo(TaxiComms.TaxiInformation.newBuilder()
                        .setId(this.id)
                        .setAddress(this.address)
                        .setPort(this.port)
                        .build())
                .setStartingPosition(TaxiComms.Coordinates.newBuilder()
                        .setX(coordinate.getX())
                        .setY(coordinate.getY())
                        .build())
                .build();
        TaxiComms.TaxiGreetingResponse response = stub.greet(greetingRequest);
        channel.shutdown();
        if (!response.getOk()) {
            throw new RuntimeException("Error while greeting");
        }
    }

    private void initializeMQTT() throws MqttException {
        this.mqttClient = new MqttClient("tcp://" + SETA.address + ":" + SETA.port, this.id + "-" + this.port);
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setKeepAliveInterval(60);
        this.mqttClient.connect(connectOptions);
        this.mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // Not used. Assume stable connection for project
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                System.out.println("Received message from " + topic);
                Gson gson = new Gson();
                if (MQTTTopics.isRideTopic(topic)) {
                    System.out.println("Received message from a ride topic: " + topic);
                    RideRequestPinned rideRequestPinned = gson.fromJson(new String(message.getPayload()), RideRequestPinned.class);
                    if (requests == null) {
                        System.out.println("Taxi is receiving from current district for the first time.");
                        System.out.println("Ride requests:\n" + rideRequestPinned.getAllRides().toString());
                        requests = rideRequestPinned.getAllRides();
                        updateRequests();
                    } else {
                        RideRequest newRequest = new RideRequest(rideRequestPinned.getRequestId(), rideRequestPinned.getNewRide());
                        System.out.println("Taxi is receiving a new ride request:\n" + newRequest);
                        if (rideRequestPinned.getNewRide() != null) {
                            System.out.println("Request is not null...");
                            if (!requests.contains(newRequest)) {
                                System.out.println("Adding request...");
                                requests.add(newRequest);
                                updateRequests();
                            }
                        }
                    }
                } else if (MQTTTopics.isAckTopic(topic)) {
                    System.out.println("Received message from an ack topic: " + topic);
                    RideAck rideAck = gson.fromJson(new String(message.getPayload()), RideAck.class);
                    System.out.println("Ack: " + rideAck.toString());
                    setAck(rideAck.getRideAck());
                    updateRequests();
                } else {
                    throw new RuntimeException("What???");
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used
            }
        });
    }

    private void initializeGrpc() {
        if (!this.address.equals("localhost")) {
            throw new RuntimeException("Addresses different than localhost are not supported");
        }
        this.server = ServerBuilder.forPort(this.port).addService(new GrpcService()).build();
        try {
            this.server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeState() {
        this.setCurrentState(Idle.IDLE);
    }

    private synchronized void updateRequests() {
        if (this.completedElectionAck == null) {
            return;
        }
        this.requests.removeIf(rideRequest -> rideRequest.getRequestId() <= this.completedElectionAck);
        System.out.println("Requests updated, notifying everyone of the change...");
        notifyAll();
    }

    public synchronized void addTaxi(TaxiInfo taxiInfo) {
        if (this.otherTaxis.contains(taxiInfo)) {
            return;
        }
        this.otherTaxis.add(taxiInfo);
    }

    public synchronized void removeTaxi(TaxiInfo taxiInfo) {
        if (!this.otherTaxis.contains(taxiInfo)) {
            return;
        }
        this.otherTaxis.remove(taxiInfo);
    }

    public TaxiInfo getTaxiInfo() {
        return new TaxiInfo(this.id, this.address, this.port);
    }

    public synchronized void setCurrentState(TaxiState currentState) {
        this.currentState = currentState;
        System.out.println("New state: " + currentState.getClass().getSimpleName());
        notifyAll();
    }

    public synchronized TaxiState getCurrentState() {
        return currentState;
    }

    public synchronized float getBattery() {
        return this.battery;
    }

    public synchronized void setBattery(float battery) {
        this.battery = battery;
    }

    public synchronized Coordinate getCurrentPosition() {
        return currentPosition;
    }

    public synchronized void setCurrentPosition(Coordinate currentPosition) {
        System.out.println("New position: " + currentPosition);
        this.currentPosition = currentPosition;
    }

    public synchronized void setAck(int completedAck) {
        Integer oldCompleted = this.completedElectionAck;
        this.completedElectionAck = completedAck;
        if (!this.completedElectionAck.equals(oldCompleted)) {
            notifyAll();
        }
    }

    public synchronized void awaitChange() throws InterruptedException {
        wait();
    }

    public synchronized void requestRecharge() {
        this.rechargeRequested = true;
        notifyAll();
    }

    public synchronized void requestExit() {
        this.exitRequested = true;
        notifyAll();
    }

    public synchronized void setRechargeRequested(Boolean rechargeRequested) {
        this.rechargeRequested = rechargeRequested;
    }

    public synchronized Boolean getRechargeRequested() {
        return rechargeRequested;
    }

    public synchronized void setExitRequested(Boolean exitRequested) {
        this.exitRequested = exitRequested;
    }

    public synchronized Boolean getExitRequested() {
        return exitRequested;
    }

    public synchronized int getCompletedElectionAck() {
        if (this.completedElectionAck == null) {
            return 0;
        }
        return completedElectionAck;
    }

    public void setCompletedElectionAck(Integer completedElectionAck) {
        this.completedElectionAck = completedElectionAck;
    }

    public synchronized void completeRide(float completedKm) {
        this.completedRides++;
        this.completedKm += completedKm;
        this.battery -= completedKm;
    }

    public synchronized void resetRide() {
        this.completedRides = 0;
        this.completedKm = 0.0F;
    }

    public synchronized int getCompletedRides() {
        return completedRides;
    }

    public synchronized float getCompletedKm() {
        return completedKm;
    }

    public synchronized List<TaxiInfo> getTaxis() {
        ArrayList<TaxiInfo> newList = new ArrayList<>();
        for (TaxiInfo taxiInfo :
                this.otherTaxis) {
            newList.add(taxiInfo.copy());
        }
        return newList;
    }

    public void stopRunning() {
        this.running = false;
    }

    public boolean getDecision(TaxiComms.TaxiRideRequest rideRequest) {
        int requestId = rideRequest.getRideInfo().getRequestId();
        synchronized (this.decisions) {
            if (this.decisions.containsKey(requestId)) {
                return this.decisions.get(requestId);
            }
            Decision decision = null;
            while (decision == null) {
                decision = this.getCurrentState().decide(this, rideRequest);
            }
            if (decision.save) {
                this.decisions.put(requestId, decision.decision);
            }
            return decision.decision;
        }
    }

    public Boolean getDecision(int requestId) {
        synchronized (this.decisions) {
            if (this.decisions.containsKey(requestId)) {
                return this.decisions.get(requestId);
            }
        }
        return null;
    }

    public void setDecision(int requestId, boolean decision) {
        synchronized (this.decisions) {
            if (this.decisions.containsKey(requestId)) {
                if (this.decisions.get(requestId) != decision) {
                    throw new RuntimeException("Decision map is no longer synchronized");
                }
                return;
            }
            this.decisions.put(requestId, decision);
        }
    }

    public void awaitRecharge(TaxiComms.TaxiRechargeRequest rechargeRequest) {
        Boolean decision = null;
        while (decision == null) {
            decision = this.getCurrentState().canRecharge(this, rechargeRequest);
        }
        if (!decision) {
            throw new RuntimeException("Error in requesting recharge");
        }
    }

    public void mqttSubscribe(String topic, int qos) {
        System.out.println("Subscribing to " + topic);
        try {
            this.mqttClient.subscribe(topic, qos);
        } catch (MqttException e) {
            System.out.println("Error in subscribing to topic " + topic + " with qos " + qos);
            throw new RuntimeException(e);
        }
    }

    public void mqttUnsubscribe(String topic) {
        System.out.println("Unsubscribing from " + topic);
        try {
            this.mqttClient.unsubscribe(topic);
        } catch (MqttException e) {
            System.out.println("Error in unsubscribing to topic " + topic);
            throw new RuntimeException(e);
        }
    }

    private void requestAddTaxi(TaxiInfo taxiInfo) {
        Boolean result = null;
        while (result == null) {
            result = this.getCurrentState().addTaxi(this, taxiInfo);
        }
        if (!result) {
            throw new RuntimeException("Failure to add taxi");
        }
    }

    private void requestRemoveTaxi(TaxiInfo taxiInfo) {
        Boolean result = null;
        while (result == null) {
            result = this.getCurrentState().removeTaxi(this, taxiInfo);
        }
        if (!result) {
            throw new RuntimeException("Failure in removing taxi");
        }
    }

    public synchronized RideRequest getNextRequest() {
        if (this.requests == null) {
            return null;
        }
        RideRequest request = null;
        for (RideRequest rideRequest :
                this.requests) {
            if (request == null) {
                request = rideRequest;
            }
            if (rideRequest.getRequestId() < request.getRequestId()) {
                request = rideRequest;
            }
        }
        return request;
    }

    public synchronized void clearRequests() {
        this.requests = null;
    }

    public void stopSensors() throws InterruptedException {
        this.pm10Sensor.stopMeGently();
        this.informationThread.shutdown();
        this.pm10Sensor.join();
        this.informationThread.join();
    }

    class GrpcService extends TaxiCommunicationImplBase {
        @Override
        public void greet(TaxiComms.TaxiGreeting request, StreamObserver<TaxiComms.TaxiGreetingResponse> responseObserver) {
            System.out.println("Received greeting:\n" + request.toString());
            Coordinate currentCoordinates = getCurrentPosition();
            TaxiComms.TaxiInformation taxiInformation = request.getTaxiInfo();
            TaxiComms.Coordinates coordinates = request.getStartingPosition();
            TaxiInfo toAdd = new TaxiInfo(taxiInformation.getId(), taxiInformation.getAddress(), taxiInformation.getPort());
            requestAddTaxi(toAdd);
            TaxiComms.TaxiGreetingResponse taxiGreetingResponse = TaxiComms.TaxiGreetingResponse.newBuilder()
                    .setOk(true)
                    .setTaxiPosition(TaxiComms.Coordinates.newBuilder()
                            .setX(currentCoordinates.getX())
                            .setY(currentCoordinates.getY())
                            .build())
                    .build();
            responseObserver.onNext(taxiGreetingResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void requestRide(TaxiComms.TaxiRideRequest request, StreamObserver<TaxiComms.TaxiRideResponse> responseObserver) {
            System.out.println("Received ride request:\n" + request.toString());
            TaxiComms.TaxiRideResponse taxiRideResponse = TaxiComms.TaxiRideResponse.newBuilder()
                    .setOk(!getDecision(request))
                    .build();
            responseObserver.onNext(taxiRideResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void requestLeave(TaxiComms.TaxiRemovalRequest request, StreamObserver<TaxiComms.TaxiRemovalResponse> responseObserver) {
            System.out.println("Received request to leave the system from:\n" + request.toString());
            List<TaxiInfo> taxis = getTaxis();
            TaxiInfo toRemove = new TaxiInfo(request.getId(), request.getAddress(), request.getPort());
            TaxiComms.TaxiRemovalResponse taxiRemovalResponse;
            if (!taxis.contains(toRemove)) {
                taxiRemovalResponse = TaxiComms.TaxiRemovalResponse.newBuilder()
                        .setOk(false)
                        .build();
            } else {
                requestRemoveTaxi(toRemove);
                taxiRemovalResponse = TaxiComms.TaxiRemovalResponse.newBuilder()
                        .setOk(true)
                        .build();
            }
            responseObserver.onNext(taxiRemovalResponse);
            responseObserver.onCompleted();
        }

        @Override
        public void requestRecharge(TaxiComms.TaxiRechargeRequest request, StreamObserver<TaxiComms.TaxiRechargeResponse> responseObserver) {
            System.out.println("Received recharge request from:\n" + request.toString());
            awaitRecharge(request);
            TaxiComms.TaxiRechargeResponse response = TaxiComms.TaxiRechargeResponse.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    class GreetingThread extends Thread {
        private final TaxiInfo taxiInfo;

        public GreetingThread(TaxiInfo info) {
            this.taxiInfo = info;
        }

        @Override
        public void run() {
            System.out.println("Greeting taxi: " + this.taxiInfo);
            sayHello(this.taxiInfo);
        }
    }
}

class InformationThread extends Thread {
    private final Gson gson = new Gson();
    private final WebResource webResource;
    private volatile boolean running = true;

    private final Buffer sensorBuffer;
    private final Taxi taxi;

    public InformationThread(Taxi taxi, Buffer sensorBuffer) {
        this.taxi = taxi;
        this.sensorBuffer = sensorBuffer;
        Client client = Client.create();
        String address = "http://" + TaxiProcess.address + ":" + TaxiProcess.port + TaxiProcess.infoPath;
        this.webResource = client.resource(address);
    }

    @Override
    public void run() {
        while (this.running) {
            this.sendMeasurement();
            try {
                Thread.sleep(TaxiProcess.timeBetweenMeasurements * 1000);
            } catch (InterruptedException e) {
                System.out.println("Thread.sleep was interrupted.");
                throw new RuntimeException(e);
            }
        }
    }

    private void sendMeasurement() {
        TaxiMeasurement taxiMeasurement = new TaxiMeasurement();
        taxiMeasurement.setId(this.taxi.id);
        taxiMeasurement.setNumberOfRides(this.taxi.getCompletedRides());
        taxiMeasurement.setBattery(this.taxi.getBattery());
        taxiMeasurement.setKm(this.taxi.getCompletedKm());
        taxiMeasurement.setPollutionMeasurements(this.sensorBuffer.readAllAndClean());
        taxiMeasurement.setTimestamp(System.currentTimeMillis());
        this.taxi.resetRide();
        String jsonRequest = this.gson.toJson(taxiMeasurement);
        ClientResponse clientResponse = this.webResource.type("application/json").post(ClientResponse.class, jsonRequest);
        if (clientResponse.getStatus() != 200) {
            throw new RuntimeException("Something wrong with the server");
        }
    }

    public void shutdown() {
        this.running = false;
    }
}
