package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import dslabs.atmostonce.AMOCommand;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

import static dslabs.paxos.Log.clientServer;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class PaxosClient extends Node implements Client {
    private final Address[] servers;

    // Your code here...
    Address myAddress = null;
    int seqNum = 0;
    PaxosRequest mostRecentRequest = null;
    PaxosReply mostRecentReply = null;
    Set<PaxosReply> receivedReply = new HashSet<>();


    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosClient(Address address, Address[] servers) {
        super(address);
        this.servers = servers;
        this.myAddress = address;
    }

    @Override
    public synchronized void init() {
        // No need to initialize
    }

    public Address getMyAddress() {
        return myAddress;
    }


    /* -------------------------------------------------------------------------
            Public methods
           -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        // Your code here...
        if (command == null) return;

        mostRecentRequest = new PaxosRequest(new AMOCommand(command, seqNum++, myAddress));
        mostRecentReply = null;
        for (Address server: servers) {
            send(mostRecentRequest, server);
        }
        set(new ClientTimer(mostRecentRequest), ClientTimer.CLIENT_RETRY_MILLIS);
//        System.out.println("(" + myAddress + ") is waiting for the reply for " + mostRecentRequest);
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
    private synchronized void handlePaxosReply(PaxosReply m, Address sender) {
        // Your code here...
        if (receivedReply.contains(m)) return;

        if (m.result().sequenceNum() == seqNum - 1) {
            if (clientServer) {
                Log.info(address() + " <- " + sender + " : "+ m);
            }
            receivedReply.add(m);
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
            for (Address server: servers) {
                send(mostRecentRequest, server);
            }
            set(t, ClientTimer.CLIENT_RETRY_MILLIS);
        }
    }
}
