package taxi.FSM.States;

import com.sun.jersey.api.client.*;
import seta.*;
import taxi.*;
import taxi.FSM.*;
import taxi.communication.TaxiComms;

public class Idle implements TaxiState {
    private final static String address = "localhost";
    private final static int port = 1338;
    private final static String deletePath = "/server/delete/";

    public final static Idle IDLE = new Idle();

    @Override
    public void execute(Taxi taxi) {
        System.out.println("Checking if recharge is requested...");
        if (taxi.getRechargeRequested()) {
            taxi.stateInfo.setTimestamp(System.currentTimeMillis());
            taxi.setCurrentState(Recharging.RECHARGING);
            return;
        }
        System.out.println("Checking if exit is requested...");
        if (taxi.getExitRequested()) {
            Client client = Client.create();
            String webAddress = "http://" + Idle.address + ":" + Idle.port + Idle.deletePath + taxi.id;
            WebResource webResource = client.resource(webAddress);
            ClientResponse clientResponse = webResource.delete(ClientResponse.class);
            if (clientResponse.getStatus() != 200) {
                throw new RuntimeException("Error while communicating removal to the server");
            }
            taxi.setCurrentState(Terminating.TERMINATING);
            return;
        }
        System.out.println("Checking if recharge is required...");
        if (taxi.getBattery() < 30) {
            taxi.stateInfo.setTimestamp(System.currentTimeMillis());
            taxi.setCurrentState(Recharging.RECHARGING);
            return;
        }
        System.out.println("Getting next request");
        RideRequest nextRequest = taxi.getNextRequest();
        System.out.println("Next request: " + nextRequest);
        if ((nextRequest != null) && (taxi.getCompletedElectionAck() < nextRequest.getRequestId())) {
            System.out.println("Starting election...");
            taxi.stateInfo.setCurrentRequest(nextRequest);
            taxi.setCurrentState(Electing.ELECTING);
        } else {
            System.out.println("Awaiting change...");
            try {
                taxi.awaitChange();
            } catch (InterruptedException e) {
                System.out.println("Interrupted wait in IDLE.execute");
                throw new RuntimeException(e);
            }
            System.out.println("Detected change...");
        }
    }

    @Override
    public Decision decide(Taxi taxi, TaxiComms.TaxiRideRequest rideRequest) {
        Coordinate rideStartingCoordinate = new Coordinate(rideRequest.getRideInfo().getRideStart());
        Coordinate currentTaxiPosition = taxi.getCurrentPosition();
        if (!District.fromCoordinate(rideStartingCoordinate).equals(District.fromCoordinate(currentTaxiPosition))) {
            System.out.println("Request " + rideRequest + " is from another district. Other taxi can take care of it...");
            return new Decision(false, true);
        }
        if (rideRequest.getRideInfo().getRequestId() <= taxi.getCompletedElectionAck()) {
            System.out.println("Request " + rideRequest + " is already been satisfied. Stopping the election...");
            return new Decision(true, true);
        }
        System.out.println("Request will be served. Waiting for change...");
        try {
            taxi.awaitChange();
        } catch (InterruptedException e) {
            System.out.println("Interrupted wait in IDLE.decide");
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Boolean canRecharge(Taxi taxi, TaxiComms.TaxiRechargeRequest rechargeRequest) {
        System.out.println("I'm idle. Recharge request " + rechargeRequest + " is granted...");
        return true;
    }

    @Override
    public Boolean addTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        System.out.println("I'm idle. Adding taxi " + taxiInfo + "...");
        taxi.addTaxi(taxiInfo);
        return true;
    }

    @Override
    public Boolean removeTaxi(Taxi taxi, TaxiInfo taxiInfo) {
        System.out.println("I'm idle. Removing taxi " + taxiInfo + "...");
        taxi.removeTaxi(taxiInfo);
        return true;
    }
}
