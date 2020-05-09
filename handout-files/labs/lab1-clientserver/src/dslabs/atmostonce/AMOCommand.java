package dslabs.atmostonce;

import dslabs.framework.Command;
import dslabs.framework.Address;
import lombok.Data;

@Data
public final class AMOCommand implements Command {
    // Your code here...
    private final Command command;
    private final int sequenceNum;
    private final Address senderAddr;
}
