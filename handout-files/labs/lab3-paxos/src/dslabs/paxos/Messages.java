package dslabs.paxos;

import java.util.TreeMap;
import lombok.Data;
import dslabs.framework.Message;
import dslabs.framework.Address;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOApplication;

import java.util.Map;

// Your code here...
@Data
class ElectionMessage implements Message {
    private final Ballot ballot;
}

@Data
class Proposal implements Message {
    private final Ballot ballot;
    private final int slot_num;
    private final AMOCommand command;
}

@Data
class Decision implements Message {
    private final int slot_num;
    private final AMOCommand command;
}

@Data
class HeartBeat implements Message {    // leader to replica
    private final Ballot ballot;
    private final Map<Integer, AMOCommand> decisions;
    private final int smallest;
}

@Data
class HeartBeatReply implements Message {   // replica to leader
    private final int replicaLastExecutedSlot;
    private final Ballot ballot;
}

@Data
class P1aMessage implements Message {
    private final Ballot ballot;
}

@Data
class P1bMessage implements Message {
    private final Ballot ballot;
    private final Map<Integer, AMOCommand> decisions;
    private final Map<Integer, TreeMap<Ballot, AMOCommand>> acceptedCommands;

}

@Data
class P2aMessage implements Message {
    private final Ballot ballot;
    private final int slot_num;
    private final AMOCommand amoCommand;
}

@Data
class P2bMessage implements Message {
    private final Ballot ballot;
    private final int slot_num;
    private final AMOCommand amoCommand;
}
