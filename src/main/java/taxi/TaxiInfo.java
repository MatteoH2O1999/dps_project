package taxi;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class TaxiInfo {
    private int id;
    private String ipAddress;
    private int port;

    public TaxiInfo() {
    }

    public TaxiInfo(int id, String ipAddress, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Expected a port number between 1 and 65535");
        }
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public int getId() {
        return id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Expected a port number between 1 and 65535");
        }
        this.port = port;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TaxiInfo)) {
            return false;
        }
        TaxiInfo otherInfo = (TaxiInfo) obj;
        return (this.id == otherInfo.id) && (this.ipAddress.equals(otherInfo.ipAddress)) && (this.port == otherInfo.port);
    }

    public TaxiInfo copy() {
        return new TaxiInfo(this.id, this.ipAddress, this.port);
    }

    @Override
    public String toString() {
        return "ID: " + this.id + "\nAddress: " + this.ipAddress + "\nPort: " + this.port;
    }
}
