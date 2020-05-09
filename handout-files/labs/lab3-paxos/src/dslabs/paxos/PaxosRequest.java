package dslabs.paxos;

import dslabs.framework.Message;
import lombok.Data;

import dslabs.atmostonce.AMOCommand;

@Data
public final class PaxosRequest implements Message {
    // Your code here...
    private final AMOCommand command;
}
