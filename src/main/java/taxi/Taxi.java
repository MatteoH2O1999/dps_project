package taxi;

import com.google.gson.Gson;
import com.sun.jersey.api.client.*;
import sensors.*;
import seta.Coordinate;
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
    private final int id;
    private final String address;
    private final int port;

    private final Buffer sensorBuffer;
    private final PM10Simulator pm10Sensor;

    private volatile boolean running = true;
    private TaxiState currentState;

    private final List<TaxiInfo> otherTaxis;
    private Coordinate currentPosition;
    private float battery = 100.0F;

    private Integer receivedElectionAck = 0;
    private Integer completedElectionAck = 0;
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
        for (TaxiInfo info :
                this.otherTaxis) {
            this.sayHello(info);
        }
        this.initializeMQTT();
        this.initializeGrpc();
        this.initializeState();
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
        for (TaxiInfo info :
                this.otherTaxis) {
            this.sayHello(info);
        }
        this.initializeMQTT();
        this.initializeGrpc();
        this.initializeState();
    }

    @Override
    public void run() {
        while (this.running) {
            this.getCurrentState().execute();
        }
    }

    private void sayHello(TaxiInfo taxiInfo) {
        // TODO
    }

    private void initializeMQTT() {
        // TODO
    }

    private void initializeGrpc() {
        // TODO
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

    public synchronized List<TaxiInfo> getTaxis() {
        ArrayList<TaxiInfo> newList = new ArrayList<>();
        for (TaxiInfo taxiInfo :
                this.otherTaxis) {
            newList.add(taxiInfo.copy());
        }
        return newList;
    }
}

class InformationThread extends Thread {
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
    }

    public void shutdown() {
        this.running = false;
    }
}
