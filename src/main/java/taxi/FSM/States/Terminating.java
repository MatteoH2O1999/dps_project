package taxi.FSM.States;

import taxi.FSM.TaxiState;

public class Terminating implements TaxiState {
    public final static Terminating TERMINATING = new Terminating();

    @Override
    public void execute() {
    }
}
