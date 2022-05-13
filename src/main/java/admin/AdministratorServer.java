package admin;

import com.sun.net.httpserver.HttpServer;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
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
        ArrayList<TaxiInfo> taxis;
        try {
            taxis = new ArrayList<>(Server.getInstance().addTaxi(taxiInfo));
        } catch (TaxiAlreadyExistException e) {
            return Response.status(406).build();
        }
        TaxiInsertionResponse insertionResponse = new TaxiInsertionResponse();
        insertionResponse.setTaxis(taxis);
        insertionResponse.setStartingPosition(AdministratorServer.startingPositions[AdministratorServer.randomGenerator.nextInt(4)]);
        return Response.ok(insertionResponse).build();
    }

    @DELETE
    @Path("delete/{id}")
    public Response removeTaxi(@PathParam("id") int taxiId) {
        try {
            Server.getInstance().removeTaxi(taxiId);
        } catch (TaxiNotFoundException e) {
            return Response.status(404).build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("taxis")
    @Produces({"application/xml", "application/json"})
    public Response getTaxis() {
        List<TaxiInfo> taxis = Server.getInstance().getTaxiInfo();
        return Response.ok(new TaxiList(taxis)).build();
    }

    @GET
    @Path("taxis/{id}/{n}")
    @Produces({"application/xml", "application/json"})
    public Response getNMeasurements(@PathParam("id") int taxiId, @PathParam("n") int numberOfMeasurements) {
        // TODO
        return null;
    }

    @GET
    @Path("taxis/measurements/{t1}/{t2}")
    @Produces({"application/xml", "application/json"})
    public Response getMeasurementAverage(@PathParam("t1") long startRange, @PathParam("t2") long endRange) {
        // TODO
        return null;
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
            TaxiMeasurement newMeasurement = new TaxiMeasurement(m.getKm(), m.getBattery(), m.getPollutionMeasurements());
        }
        return newMeasurements;
    }
}

@XmlRootElement
class TaxiList {
    private List<TaxiInfo> taxis;

    public TaxiList() {
    }

    public TaxiList(List<TaxiInfo> taxis) {
        this.taxis = taxis;
    }

    public List<TaxiInfo> getTaxis() {
        return taxis;
    }

    public void setTaxis(List<TaxiInfo> taxis) {
        this.taxis = taxis;
    }
}

class TaxiAlreadyExistException extends Exception {
}

class TaxiNotFoundException extends Exception {
}
