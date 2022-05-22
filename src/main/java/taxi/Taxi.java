package taxi;

import seta.Coordinate;
import taxi.FSM.TaxiState;

import java.util.*;

class TaxiProcess {
    public static void main(String[] args) {
    }
}

public class Taxi {
    private final int id;
    private final String address;
    private final int port;

    private TaxiState currentState;

    private final List<TaxiInfo> otherTaxis;
    private Coordinate currentPosition;

    public Taxi(Coordinate startingPosition, TaxiInfo taxiInfo, List<TaxiInfo> otherTaxis) {
        this.currentPosition = startingPosition;
        this.id = taxiInfo.getId();
        this.address = taxiInfo.getIpAddress();
        this.port = taxiInfo.getPort();
        this.otherTaxis = otherTaxis;
        this.sayHello();
    }

    private void sayHello() {
        // TODO
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
}
