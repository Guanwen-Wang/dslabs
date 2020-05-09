package dslabs.paxos;

import dslabs.framework.Timer;
import lombok.Data;

@Data
final class ClientTimer implements Timer {
    static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...
    private final PaxosRequest request;
}

// Your code here...
@Data
final class P1aTimer implements Timer {
    static final int P1A_RETRY_MILLIS = 100;

    // Your code here...
    private final P1aMessage p1aMessage;
}

@Data
final class P2aTimer implements Timer {
    static final int P2A_RETRY_MILLIS = 100;

    private final P2aMessage p2aMessage;
}

@Data
final class HeartBeatTimer implements Timer {
    static final int HEARTBEAT_MILLIS = 25;
}

@Data
final class ProposalDecisionTimer implements Timer {
    static final int PROPOSAL_RETRY_MILLIS = 100;

    private final Proposal proposal;
}
@Data
final class HeartBeatCheckTimer implements Timer {
    static final int HEARTBEAT_RETRY_MILLIS = 100;

    private final Ballot ballot;
}
