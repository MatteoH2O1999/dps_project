package admin;

import com.sun.net.httpserver.HttpServer;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import sensors.Measurement;
import seta.Coordinate;
import taxi.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.util.*;

class ServerProcess {
    private final static String address = "localhost";
    private final static int port = 1338;

    public static void main(String[] args) {
        String address = "http://" + ServerProcess.address + ":" + ServerProcess.port + "/";
        HttpServer server;
        try {
            server = HttpServerFactory.create(address);
        } catch (IOException e) {
            System.out.println("Something went wrong with the creation of the server.");
            e.printStackTrace();
            return;
        }
        server.start();

        System.out.println("Server running on address: " + address);
        System.out.println("Press any key to stop...");
        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println("Something went wrong while reading from stdin.");
            e.printStackTrace();
            return;
        }
        System.out.println("Stopping server...");
        server.stop(10);
        System.out.println("Server stopped!!!");
    }
}

@Path("server")
public class AdministratorServer {
    private static final Random randomGenerator = new Random();
    private static final Coordinate[] startingPositions = {new Coordinate(0, 0), new Coordinate(0, 9), new Coordinate(9, 0), new Coordinate(9, 9)};

    @POST
    @Path("add")
    @Consumes({"application/xml", "application/json"})
    @Produces({"application/xml", "application/json"})
    public Response addTaxi(TaxiInfo taxiInfo) {
        System.out.printf("Trying to add taxi: \n%s", taxiInfo.toString() + "\n");
        ArrayList<TaxiInfo> taxis;
        try {
            taxis = new ArrayList<>(Server.getInstance().addTaxi(taxiInfo));
            taxis.remove(taxiInfo);
        } catch (TaxiAlreadyExistException e) {
            System.out.println("Insertion failed.\n");
            return Response.status(406).build();
        }
        System.out.println("Insertion successful.");
        TaxiInsertionResponse insertionResponse = new TaxiInsertionResponse();
        insertionResponse.setTaxis(taxis);
        insertionResponse.setStartingPosition(AdministratorServer.startingPositions[AdministratorServer.randomGenerator.nextInt(4)]);
        System.out.println("Sending to taxi following message:\n" + insertionResponse + "\n");
        return Response.ok(insertionResponse).build();
    }

    @DELETE
    @Path("delete/{id}")
    public Response removeTaxi(@PathParam("id") int taxiId) {
        System.out.println("Trying to remove taxi with id: " + taxiId);
        try {
            Server.getInstance().removeTaxi(taxiId);
        } catch (TaxiNotFoundException e) {
            System.out.println("Deletion failed.\n");
            return Response.status(404).build();
        }
        System.out.println("Deletion successful.\n");
        return Response.ok().build();
    }

    @GET
    @Path("taxis")
    @Produces({"application/xml", "application/json"})
    public Response getTaxis() {
        System.out.println("Sending taxi list to client...");
        List<TaxiInfo> taxis = Server.getInstance().getTaxiInfo();
        if (taxis.size() == 0) {
            System.out.println("Failed.\n");
            return Response.status(404).build();
        }
        int[] taxiIds = new int[taxis.size()];
        for (int i = 0; i < taxis.size(); i++) {
            taxiIds[i] = taxis.get(i).getId();
        }
        System.out.println("Success.\n");
        return Response.ok(new TaxiList(taxiIds)).build();
    }

    @GET
    @Path("taxis/{id}/{n}")
    @Produces({"application/xml", "application/json"})
    public Response getNMeasurements(@PathParam("id") int taxiId, @PathParam("n") int numberOfMeasurements) {
        System.out.println("Sending last " + numberOfMeasurements + " measurements about taxi with id " + taxiId + "...");
        List<TaxiMeasurement> taxiMeasurements = Server.getInstance().getTaxiMeasurements();
        taxiMeasurements.removeIf(taxiMeasurement -> taxiMeasurement.getId() != taxiId);
        taxiMeasurements.sort((o1, o2) -> {
            long diff = o2.getTimestamp() - o1.getTimestamp();
            return (int)diff;
        });
        if (taxiMeasurements.size() == 0) {
            System.out.println("Failed.\n");
            return Response.status(404).build();
        }
        if (taxiMeasurements.size() >= numberOfMeasurements) {
            taxiMeasurements = taxiMeasurements.subList(0, numberOfMeasurements - 1);
        } else {
            numberOfMeasurements = taxiMeasurements.size();
        }

        TaxiStats stats = new TaxiStats();

        int numberOfMeasuresInTaxi = 0;
        float avgRides = 0.0F;
        float km = 0.0F;
        float battery = 0.0F;
        UntimedMeasurement measurement = new UntimedMeasurement();

        for (TaxiMeasurement taxiMeasurement :
                taxiMeasurements) {
            avgRides += taxiMeasurement.getNumberOfRides();
            km += taxiMeasurement.getKm();
            battery += taxiMeasurement.getBattery();
            for (Measurement m :
                    taxiMeasurement.getPollutionMeasurements()) {
                measurement.setValue(measurement.getValue() + m.getValue());
                numberOfMeasuresInTaxi++;
            }
        }
        measurement.setValue(measurement.getValue() / numberOfMeasuresInTaxi);

        stats.setKm(km / numberOfMeasurements);
        stats.setBattery(battery / numberOfMeasurements);
        stats.setNumberOfRides(avgRides / numberOfMeasurements);
        stats.setPollutionMeasurement(measurement);
        stats.setTimestamp(System.currentTimeMillis());
        System.out.println("Success.\n");
        return Response.ok(stats).build();
    }

    @GET
    @Path("taxis/measurements/{t1}/{t2}")
    @Produces({"application/xml", "application/json"})
    public Response getMeasurementAverage(@PathParam("t1") long startRange, @PathParam("t2") long endRange) {
        System.out.println("Sending taxi information with timestamps between " + startRange + " and " + endRange + "...");
        List<TaxiMeasurement> taxiMeasurements = Server.getInstance().getTaxiMeasurements();
        taxiMeasurements.removeIf(taxiMeasurement -> taxiMeasurement.getTimestamp() > endRange || taxiMeasurement.getTimestamp() < startRange);

        if (taxiMeasurements.size() == 0) {
            System.out.println("Failed.\n");
            return Response.status(404).build();
        }

        TaxiStats stats = new TaxiStats();

        int numberOfMeasurements = 0;
        float avgRides = 0.0F;
        float km = 0.0F;
        float battery = 0.0F;
        UntimedMeasurement measurement = new UntimedMeasurement();

        for (TaxiMeasurement taxiMeasurement :
                taxiMeasurements) {
            avgRides += taxiMeasurement.getNumberOfRides();
            km += taxiMeasurement.getKm();
            battery += taxiMeasurement.getBattery();
            for (Measurement m :
                    taxiMeasurement.getPollutionMeasurements()) {
                measurement.setValue(measurement.getValue() + m.getValue());
                numberOfMeasurements++;
            }
        }
        measurement.setValue(measurement.getValue() / numberOfMeasurements);

        stats.setKm(km / taxiMeasurements.size());
        stats.setBattery(battery / taxiMeasurements.size());
        stats.setNumberOfRides(avgRides / taxiMeasurements.size());
        stats.setPollutionMeasurement(measurement);
        stats.setTimestamp(System.currentTimeMillis());
        System.out.println("Success.\n");
        return Response.ok(stats).build();
    }

    @POST
    @Path("measure")
    @Consumes({"application/xml", "application/json"})
    public Response addMeasurement(TaxiMeasurement taxiMeasurement) {
        System.out.println("Adding measurement:" + taxiMeasurement.toString() + "\n");
        Server.getInstance().addTaxiMeasurement(taxiMeasurement);
        return Response.ok().build();
    }
}


class Server {
    private static final Server instance = new Server();

    private final ArrayList<TaxiInfo> taxis = new ArrayList<>();
    private final ArrayList<TaxiMeasurement> taxiMeasurements = new ArrayList<>();

    public static Server getInstance() {
        return Server.instance;
    }

    public synchronized List<TaxiInfo> addTaxi(TaxiInfo taxiInfo) throws TaxiAlreadyExistException {
        if (checkIfTaxiExists(taxiInfo.getId())) {
            throw new TaxiAlreadyExistException();
        }
        this.taxis.add(taxiInfo);
        return this.getTaxiInfo();
    }

    public synchronized void removeTaxi(int taxiId) throws TaxiNotFoundException {
        if (!checkIfTaxiExists(taxiId)) {
            throw new TaxiNotFoundException();
        }
        this.taxis.removeIf(taxiInfo -> taxiInfo.getId() == taxiId);
    }

    private boolean checkIfTaxiExists(int taxiId) {
        for (TaxiInfo taxi :
                this.taxis) {
            if (taxiId == taxi.getId()) {
                return true;
            }
        }
        return false;
    }

    public synchronized void addTaxiMeasurement(TaxiMeasurement taxiMeasurement) {
        this.taxiMeasurements.add(taxiMeasurement);
    }

    public synchronized List<TaxiInfo> getTaxiInfo() {
        ArrayList<TaxiInfo> newInfo = new ArrayList<>();
        for (TaxiInfo info :
                this.taxis) {
            TaxiInfo newCopy = new TaxiInfo(info.getId(), info.getIpAddress(), info.getPort());
            newInfo.add(newCopy);
        }
        return newInfo;
    }

    public synchronized List<TaxiMeasurement> getTaxiMeasurements() {
        ArrayList<TaxiMeasurement> newMeasurements = new ArrayList<>();
        for (TaxiMeasurement m :
                this.taxiMeasurements) {
            TaxiMeasurement newMeasurement = new TaxiMeasurement(m.getId(), m.getNumberOfRides(), m.getKm(), m.getBattery(), m.getPollutionMeasurements(), m.getTimestamp());
            newMeasurements.add(newMeasurement);
        }
        return newMeasurements;
    }
}

@XmlRootElement
class TaxiList {
    private int[] taxis;

    public TaxiList() {
    }

    public TaxiList(int[] taxis) {
        this.taxis = taxis;
    }

    public int[] getTaxis() {
        return taxis;
    }

    public void setTaxis(int[] taxis) {
        this.taxis = taxis;
    }
}

@XmlRootElement
class TaxiStats {
    private float numberOfRides;
    private float km;
    private float battery;
    private UntimedMeasurement pollutionMeasurement;
    private long timestamp;

    public TaxiStats() {
    }

    public TaxiStats(float numberOfRides, float km, float battery, Measurement pollutionMeasurement) {
        this.numberOfRides = numberOfRides;
        this.km = km;
        this.battery = battery;
        this.pollutionMeasurement = new UntimedMeasurement(pollutionMeasurement);
        this.timestamp = System.currentTimeMillis();
    }

    public TaxiStats(float numberOfRides, float km, float battery, Measurement pollutionMeasurement, long timestamp) {
        this.numberOfRides = numberOfRides;
        this.km = km;
        this.battery = battery;
        this.pollutionMeasurement = new UntimedMeasurement(pollutionMeasurement);
        this.timestamp = timestamp;
    }

    public TaxiStats(float numberOfRides, float km, float battery, UntimedMeasurement pollutionMeasurement) {
        this.numberOfRides = numberOfRides;
        this.km = km;
        this.battery = battery;
        this.pollutionMeasurement = pollutionMeasurement;
        this.timestamp = System.currentTimeMillis();
    }

    public TaxiStats(float numberOfRides, float km, float battery, UntimedMeasurement pollutionMeasurement, long timestamp) {
        this.numberOfRides = numberOfRides;
        this.km = km;
        this.battery = battery;
        this.pollutionMeasurement = pollutionMeasurement;
        this.timestamp = timestamp;
    }

    public float getNumberOfRides() {
        return numberOfRides;
    }

    public float getKm() {
        return km;
    }

    public float getBattery() {
        return battery;
    }

    public UntimedMeasurement getPollutionMeasurement() {
        return pollutionMeasurement;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setNumberOfRides(float numberOfRides) {
        this.numberOfRides = numberOfRides;
    }

    public void setKm(float km) {
        this.km = km;
    }

    public void setBattery(float battery) {
        this.battery = battery;
    }

    public void setPollutionMeasurement(UntimedMeasurement pollutionMeasurement) {
        this.pollutionMeasurement = pollutionMeasurement;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

class UntimedMeasurement {
    private double value;

    public UntimedMeasurement() {
    }

    public UntimedMeasurement(double value) {
        this.value = value;
    }

    public UntimedMeasurement(Measurement measurement) {
        this.value = measurement.getValue();
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}

class TaxiAlreadyExistException extends Exception {
}

class TaxiNotFoundException extends Exception {
}
