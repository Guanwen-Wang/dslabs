package dslabs.clientserver;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;

/**
 * Simple client that sends requests to a single server and returns responses.
 *
 * See the documentation of {@link Client} and {@link Node} for important
 * implementation notes.
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class SimpleClient extends Node implements Client {
    private final Address serverAddress;

    // Your code here...
    int seqNum = 0;
    Request mostRecentRequest = null;
    Reply mostRecentReply = null;
    Address myAddress = null;
    
    
    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public SimpleClient(Address address, Address serverAddress) {
        super(address);
        this.serverAddress = serverAddress;
        this.myAddress = address;
    }

    @Override
    public synchronized void init() {
        // No initialization necessary
    }

    /* -------------------------------------------------------------------------
        Client Methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        // Your code here...
        if (command == null) return;

        mostRecentRequest = new Request(new AMOCommand(command, seqNum++, myAddress));
        mostRecentReply = null;
        send(mostRecentRequest, serverAddress);
        set(new ClientTimer(mostRecentRequest), ClientTimer.CLIENT_RETRY_MILLIS);
    }

    @Override
    public synchronized boolean hasResult() {
        // Your code here...
        return mostRecentReply != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        // Your code here...
        while (mostRecentReply == null) {
                wait();
        }
        return mostRecentReply.result().result();
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private synchronized void handleReply(Reply m, Address sender) {
        // Your code here...
        if (m.result().sequenceNum() == seqNum - 1) {
            mostRecentReply = m;
            notify();
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onClientTimer(ClientTimer t) {
        // Your code here...
        if (seqNum > 0 && Objects.equals(mostRecentRequest, t.request())
            && mostRecentReply == null) {
            send(mostRecentRequest, serverAddress);
            set(t, ClientTimer.CLIENT_RETRY_MILLIS);
        }
    }
}
