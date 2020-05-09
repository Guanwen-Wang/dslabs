package dslabs.paxos;

import dslabs.framework.Message;
import lombok.Data;

import dslabs.atmostonce.AMOResult;

@Data
public final class PaxosReply implements Message {
    // Your code here...
    private final AMOResult result;
}
