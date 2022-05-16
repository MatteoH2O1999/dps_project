package admin;

import com.sun.jersey.api.client.*;

import java.io.*;

class AdministrationClientProcess {
    private final static String address = "localhost";
    private final static String port = "1338";

    public static void main(String[] args) {
        AdministrationClient client = new AdministrationClient("http://" + address + ":" + port);
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        int command;
        boolean loop = true;

        do {
            System.out.println("Choose an option:\n0: Get taxi list\n1: Get average of last n measurements of specified taxi\n2: Get average of measurements in range\n3: Quit");
            try {
                command = Integer.parseInt(userInput.readLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid command. Must be an integer");
                return;
            } catch (IOException e) {
                System.out.println("You keyboard is not responding.");
                e.printStackTrace();
                return;
            }
            switch (command) {
                case 0:
                    client.taxiList();
                    break;
                case 1:
                    int id, n;
                    try {
                        System.out.println("Insert id of taxi:");
                        id = Integer.parseInt(userInput.readLine());
                        System.out.println("Insert number of measurements to consider:");
                        n = Integer.parseInt(userInput.readLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid command. Must be an integer");
                        return;
                    } catch (IOException e) {
                        System.out.println("You keyboard is not responding.");
                        e.printStackTrace();
                        return;
                    }
                    client.getLastNMeasurements(id, n);
                    break;
                case 2:
                    long startRange, endRange;
                    try {
                        System.out.println("Insert starting timestamp");
                        startRange = Long.parseLong(userInput.readLine());
                        System.out.println("Insert ending timestamp");
                        endRange = Long.parseLong(userInput.readLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid command. Must be a long.");
                        return;
                    } catch (IOException e) {
                        System.out.println("You keyboard is not responding.");
                        e.printStackTrace();
                        return;
                    }
                    client.getMeasurementRange(startRange, endRange);
                    break;
                case 3:
                    loop = false;
                    break;
                default:
                    System.out.println("Invalid command");
                    return;
            }
        } while (loop);
    }
}

public class AdministrationClient {
    private final Client client;
    private final String serverAddress;

    public AdministrationClient(String serverAddress) {
        this.client = Client.create();
        this.serverAddress = serverAddress;
    }

    public void taxiList() {
        String path = "/server/taxis";
        WebResource webResource = this.client.resource(this.serverAddress + path);
        ClientResponse clientResponse;
        try {
            clientResponse = webResource.type("application/xml").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server unavailable");
            return;
        }
        if (clientResponse.getStatus() == 404) {
            System.out.println("There are no taxis in the system");
        } else {
            TaxiList taxis = clientResponse.getEntity(TaxiList.class);
            System.out.println("Taxi ids in the system:");
            for (int taxiId :
                    taxis.getTaxis()) {
                System.out.println(taxiId);
            }
        }
    }

    public void getLastNMeasurements(int id, int n) {
        String path = "/server/taxis/" + id + "/" + n;
        WebResource webResource = this.client.resource(this.serverAddress + path);
        ClientResponse clientResponse;
        try {
            clientResponse = webResource.type("application/xml").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server unavailable");
            return;
        }
        if (clientResponse.getStatus() == 404) {
            System.out.println("There are no stats for the selected taxi.");
        } else {
            TaxiStats stats = clientResponse.getEntity(TaxiStats.class);
            System.out.println("Average number of rides: "
                    + stats.getNumberOfRides()
                    + "\nAverage distance travelled: "
                    + stats.getKm()
                    + " km\nAverage battery level: "
                    + stats.getBattery()
                    + " %\nAverage pollution level: "
                    + stats.getPollutionMeasurement().getValue()
                    + "\n");
        }
    }

    public void getMeasurementRange(long startRange, long endRange) {
        String path = "/server/taxis/measurements/" + startRange + "/" + endRange;
        WebResource webResource = this.client.resource(this.serverAddress + path);
        ClientResponse clientResponse;
        try {
            clientResponse = webResource.type("application/xml").get(ClientResponse.class);
        } catch (ClientHandlerException e) {
            System.out.println("Server unavailable");
            return;
        }
        if (clientResponse.getStatus() == 404) {
            System.out.println("There are no stats for the selected range.");
        } else {
            TaxiStats stats = clientResponse.getEntity(TaxiStats.class);
            System.out.println("Average number of rides: "
                    + stats.getNumberOfRides()
                    + "\nAverage distance travelled: "
                    + stats.getKm()
                    + " km\nAverage battery level: "
                    + stats.getBattery()
                    + " %\nAverage pollution level: "
                    + stats.getPollutionMeasurement().getValue()
                    + "\n");
        }
    }
}
