package taxi.FSM;

import seta.RideRequest;

import java.util.Map;

public class TaxiStateInfo {
    private RideRequest currentRequest;
    private Long timestamp;
    private Map<Integer, Thread> closingThreads;

    public RideRequest getCurrentRequest() {
        return currentRequest;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Map<Integer, Thread> getClosingThreads() {
        return closingThreads;
    }

    public void setCurrentRequest(RideRequest currentRequest) {
        this.currentRequest = currentRequest;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public synchronized void setClosingThreads(Map<Integer, Thread> closingThreads) {
        boolean wasNull = this.closingThreads == null;
        this.closingThreads = closingThreads;
        if (wasNull) {
            notifyAll();
        }
    }

    public synchronized void interruptClosingThread(int taxiId) throws InterruptedException {
        if (this.closingThreads == null) {
            wait();
        }
        if (!this.closingThreads.containsKey(taxiId)) {
            throw new RuntimeException("Taxi is not in list.");
        }
        this.closingThreads.get(taxiId).interrupt();
    }

    public void clearCurrentRequest() {
        this.currentRequest = null;
    }

    public void clearTimestamp() {
        this.timestamp = null;
    }
}
