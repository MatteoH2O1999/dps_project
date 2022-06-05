package taxi;

import com.google.gson.Gson;
import com.sun.jersey.api.client.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.eclipse.paho.client.mqttv3.*;
import sensors.*;
import seta.*;
import taxi.communication.*;
import taxi.communication.TaxiCommunicationGrpc.TaxiCommunicationImplBase;
import taxi.FSM.States.Idle;
import taxi.FSM.TaxiState;

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
                System.out.println("Insert address:");
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

    public Server server;
    public MqttClient mqttClient;

    private final Buffer sensorBuffer;
    public final PM10Simulator pm10Sensor;
    public final InformationThread informationThread;

    private volatile boolean running = true;
    private TaxiState currentState;

    private final List<TaxiInfo> otherTaxis;
    private Coordinate currentPosition;
    private float battery = 100.0F;
    private int completedRides = 0;
    private float completedKm = 0.0F;

    private Integer receivedElectionAck = 0;
    private Integer completedElectionAck = 0;
    private List<RideRequest> requests = null;
    private final HashMap<Integer, Boolean> decisions = new HashMap<>();

    private volatile Boolean rechargeRequested = false;
    private volatile Boolean exitRequested = false;

    public Taxi(int id, String address, int port) {
        Client client = Client.create();
        String webAddress = "http//" + TaxiProcess.address + ":" + TaxiProcess.port + TaxiProcess.addPath;
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
        this.otherTaxis = taxiInsertionResponse.getTaxis();
        this.sensorBuffer = new SensorBuffer();
        this.pm10Sensor = new PM10Simulator(this.sensorBuffer);
        this.pm10Sensor.start();
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
        this.initAck(threads);
        this.initializeState();
        this.initializeGrpc();
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
        this.otherTaxis = otherTaxis;
        this.sensorBuffer = new SensorBuffer();
        this.pm10Sensor = new PM10Simulator(this.sensorBuffer);
        this.pm10Sensor.start();
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
        this.initAck(threads);
        this.initializeState();
        this.initializeGrpc();
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
        while (this.running) {
            this.getCurrentState().execute(this);
        }
    }

    private AckPair sayHello(TaxiInfo taxiInfo) {
        AckPair toReturn;
        Coordinate coordinate = this.getCurrentPosition();
        District district = District.fromCoordinate(coordinate);
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
        Coordinate otherCoordinate = new Coordinate(response.getTaxiPosition().getX(), response.getTaxiPosition().getY());
        District otherDistrict = District.fromCoordinate(otherCoordinate);
        if ((!district.equals(otherDistrict)) || (!response.getOk())) {
            return null;
        }
        toReturn = new AckPair(response.getReceivedAck(), response.getCompletedAck());
        return toReturn;
    }

    private void initAck(List<GreetingThread> threads) {
        List<Integer> receivedAckList = new ArrayList<>();
        List<Integer> completedAckList = new ArrayList<>();
        for (GreetingThread greetingThread :
                threads) {
            if (greetingThread.isRelevant()) {
                receivedAckList.add(greetingThread.getReceivedAck());
                completedAckList.add(greetingThread.getCompletedAck());
            }
        }
        this.setAck(Collections.min(receivedAckList), Collections.min(completedAckList));
    }

    private void initializeMQTT() throws MqttException {
        this.mqttClient = new MqttClient("ftp://" + this.address + ":" + this.port, this.id + "-" + this.port);
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
                // TODO
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

    private synchronized void addTaxi(TaxiInfo taxiInfo) {
        if (this.otherTaxis.contains(taxiInfo)) {
            return;
        }
        this.otherTaxis.add(taxiInfo);
    }

    private synchronized void removeTaxi(TaxiInfo taxiInfo) {
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
        this.currentPosition = currentPosition;
    }

    public synchronized void setAck(int receivedAck, int completedAck) {
        int oldReceived = this.receivedElectionAck;
        int oldCompleted = this.completedElectionAck;
        this.receivedElectionAck = receivedAck;
        this.completedElectionAck = completedAck;
        if ((this.receivedElectionAck != oldReceived) || (this.completedElectionAck != oldCompleted)) {
            notifyAll();
        }
    }

    public synchronized void awaitChange() throws InterruptedException {
        wait();
    }

    public void requestRecharge() {
        this.rechargeRequested = true;
    }

    public void requestExit() {
        this.exitRequested = true;
    }

    public void setRechargeRequested(Boolean rechargeRequested) {
        this.rechargeRequested = rechargeRequested;
    }

    public Boolean getRechargeRequested() {
        return rechargeRequested;
    }

    public void setExitRequested(Boolean exitRequested) {
        this.exitRequested = exitRequested;
    }

    public Boolean getExitRequested() {
        return exitRequested;
    }

    public synchronized int getCompletedElectionAck() {
        return completedElectionAck;
    }

    public synchronized int getReceivedElectionAck() {
        return receivedElectionAck;
    }

    public void setCompletedElectionAck(int completedElectionAck) {
        this.completedElectionAck = completedElectionAck;
    }

    public void setReceivedElectionAck(int receivedElectionAck) {
        this.receivedElectionAck = receivedElectionAck;
    }

    public synchronized void completeRide(float completedKm) {
        this.completedRides++;
        this.completedKm += completedKm;
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
            boolean decision = this.getCurrentState().decide(this, rideRequest);
            this.decisions.put(requestId, decision);
            return decision;
        }
    }

    public void mqttSubscribe(String topic, int qos) {
        try {
            this.mqttClient.subscribe(topic, qos);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    public void mqttUnsubscribe(String topic) {
        try {
            this.mqttClient.unsubscribe(topic);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    class GrpcService extends TaxiCommunicationImplBase {
        @Override
        public void greet(TaxiComms.TaxiGreeting request, StreamObserver<TaxiComms.TaxiGreetingResponse> responseObserver) {
            System.out.println("Received greeting:\n" + request.toString());
            Coordinate currentCoordinates = getCurrentPosition();
            TaxiComms.TaxiInformation taxiInformation = request.getTaxiInfo();
            TaxiComms.Coordinates coordinates = request.getStartingPosition();
            TaxiInfo toAdd = new TaxiInfo(taxiInformation.getId(), taxiInformation.getAddress(), taxiInformation.getPort());
            getCurrentState().addTaxi(toAdd);
            TaxiComms.TaxiGreetingResponse taxiGreetingResponse = TaxiComms.TaxiGreetingResponse.newBuilder()
                    .setOk(true)
                    .setReceivedAck(receivedElectionAck)
                    .setCompletedAck(completedElectionAck)
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
                getCurrentState().removeTaxi(toRemove);
                taxiRemovalResponse = TaxiComms.TaxiRemovalResponse.newBuilder()
                        .setOk(true)
                        .build();
            }
            responseObserver.onNext(taxiRemovalResponse);
            responseObserver.onCompleted();
        }
    }

    class GreetingThread extends Thread {
        private final TaxiInfo taxiInfo;
        private int receivedAck;
        private int completedAck;
        private boolean relevant = true;

        public GreetingThread(TaxiInfo info) {
            this.taxiInfo = info;
        }

        @Override
        public void run() {
            AckPair ackPair = sayHello(this.taxiInfo);
            if (ackPair == null) {
                relevant = false;
            } else {
                this.receivedAck = ackPair.receivedAck;
                this.completedAck = ackPair.completedAck;
            }
        }

        public int getCompletedAck() {
            return completedAck;
        }

        public int getReceivedAck() {
            return receivedAck;
        }

        public boolean isRelevant() {
            return relevant;
        }
    }

    static class AckPair {
        public int receivedAck;
        public int completedAck;

        public AckPair() {
        }

        public AckPair(int receivedAck, int completedAck) {
            this.receivedAck = receivedAck;
            this.completedAck = completedAck;
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
        String address = "http//" + TaxiProcess.address + ":" + TaxiProcess.port + TaxiProcess.infoPath;
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
