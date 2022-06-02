package taxi;

import seta.Coordinate;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

@XmlRootElement
public class TaxiInsertionResponse {
    Coordinate startingPosition;
    ArrayList<TaxiInfo> taxis;

    public TaxiInsertionResponse() {
    }

    public Coordinate getStartingPosition() {
        return startingPosition;
    }

    public ArrayList<TaxiInfo> getTaxis() {
        return taxis;
    }

    public void setStartingPosition(Coordinate startingPosition) {
        this.startingPosition = startingPosition;
    }

    public void setTaxis(ArrayList<TaxiInfo> taxis) {
        this.taxis = taxis;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("Coordinates: " + this.startingPosition.toString());
        str.append("\nTaxis:\n[");
        for (TaxiInfo taxiInfo :
                this.taxis) {
            str.append("\n").append(taxiInfo.toString()).append("\n");
        }
        str.append("]");
        return str.toString();
    }
}
