package admin;

import taxi.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.ArrayList;

@Path("server")
public class AdministratorServer {
    private final ArrayList<TaxiInfo> taxis = new ArrayList<>();
    private final ArrayList<TaxiMeasurement> taxiMeasurements = new ArrayList<>();

    public AdministratorServer() {
    }

    @POST
    @Path("add")
    @Consumes({"application/xml", "application/json"})
    @Produces({"application/xml", "application/json"})
    public synchronized Response addTaxi(TaxiInfo taxiInfo) {
        // TODO
        return null;
    }

    @DELETE
    @Path("delete/{id}")
    public synchronized Response removeTaxi(@PathParam("id") int taxiId) {
        // TODO
        return null;
    }

    @GET
    @Path("taxis")
    @Produces({"application/xml", "application/json"})
    public synchronized Response getTaxis() {
        // TODO
        return null;
    }

    @GET
    @Path("taxis/{id}/{n}")
    @Produces({"application/xml", "application/json"})
    public synchronized Response getNMeasurements(@PathParam("id") int taxiId, @PathParam("n") int numberOfMeasurements) {
        // TODO
        return null;
    }

    @GET
    @Path("taxis/measurements/{t1}/{t2}")
    @Produces({"application/xml", "application/json"})
    public synchronized Response getMeasurementAverage(@PathParam("t1") long startRange, @PathParam("t2") long endRange) {
        // TODO
        return null;
    }
}
