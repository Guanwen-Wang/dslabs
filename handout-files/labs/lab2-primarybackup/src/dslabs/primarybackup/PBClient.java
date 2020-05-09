package dslabs.primarybackup;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Objects;
import dslabs.atmostonce.AMOCommand;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBClient extends Node implements Client {
    private final Address viewServer;

    // Your code here...
    View currentView = null;
    Address primary = null;
    Request mostRecentRequest = null;
    Reply mostRecentReply = null;
    int seqNum = 0;
    int primaryMiss = 0;
    boolean needResend = false;


    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PBClient(Address address, Address viewServer) {
        super(address);
        this.viewServer = viewServer;
    }

    @Override
    public synchronized void init() {
        // Your code here...
        send(new GetView(), viewServer);
    }

    /* -------------------------------------------------------------------------
        Client Methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        // Your code here...
        if (command == null) return;

        mostRecentRequest = new Request(new AMOCommand(command, seqNum++, address()));
        mostRecentReply = null;
        primaryMiss = 0;
        if (primary != null) {
            send(mostRecentRequest, primary);
        }
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
            primaryMiss = 0;
            notify();
        }
    }

    private synchronized void handleViewReply(ViewReply m, Address sender) {
        // Your code here...
        currentView = m.view();
        primary = m.view().primary();
        primaryMiss = 0;
        if (needResend && mostRecentReply == null) {
            //            System.out.println("in resend");
            if (primary != null)
                send(mostRecentRequest, primary);
            set(new ClientTimer(mostRecentRequest), ClientTimer.CLIENT_RETRY_MILLIS);
            needResend = false;
        }
    }

    // Your code here...

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onClientTimer(ClientTimer t) {
        // Your code here...
        // check whether primary is dead
        // if primary is dead, send GetView to viewServer to get the most recent view

        if (seqNum > 0 && Objects.equals(mostRecentRequest, t.request())
                && mostRecentReply == null) {
            primaryMiss++;
            //            System.out.println("in client timer");
            if (primaryMiss >= 2) {     // primary is dead
                //                System.out.println("need get new view");
                send(new GetView(), viewServer);
                needResend = true;
            } else {    // primary not dead, miss one period
                if (primary != null)
                    send(mostRecentRequest, primary);
                //                System.out.println("primarry miss one!");
                //                set(t, ClientTimer.CLIENT_RETRY_MILLIS);
            }
            set(t, ClientTimer.CLIENT_RETRY_MILLIS);
        }
    }
}

// not talk to viewServer for every operations
// instead, cache the current view and only talk to viewServer by sending the GetView message
// when on intial startup, when primary dead, when receive error
