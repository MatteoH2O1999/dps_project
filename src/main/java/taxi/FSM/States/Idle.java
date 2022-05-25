package taxi.FSM.States;

import taxi.FSM.TaxiState;

public class Idle implements TaxiState {
    public final static Idle IDLE = new Idle();

    @Override
    public void execute() {
    }
}
