package taxi.FSM;

import seta.RideRequest;

public class TaxiStateInfo {
    private RideRequest currentRequest;

    public RideRequest getCurrentRequest() {
        return currentRequest;
    }

    public void setCurrentRequest(RideRequest currentRequest) {
        this.currentRequest = currentRequest;
    }

    public void clearCurrentRequest() {
        this.currentRequest = null;
    }
}
