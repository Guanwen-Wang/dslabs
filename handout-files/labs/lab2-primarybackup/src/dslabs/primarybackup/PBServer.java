package dslabs.primarybackup;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import dslabs.framework.Result;
import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOResult;
import java.util.Queue;
import java.util.ArrayDeque;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBServer extends Node {
    private final Address viewServer;

    // Your code here...
    Address myAddress = null;
    int currentViewNum = ViewServer.STARTUP_VIEWNUM;
    volatile View currentView = null;
    AMOApplication app;
    volatile boolean isForwarding = false;
    Request mostRecentRequest = null;
    Reply mostRecentReply = null;       // from backup
    Address mostRecentClient = null;
    volatile boolean appACKReceived = false;

    class Pair {
        Request m;
        Address clientAddr;
        Pair(Request m, Address clientAddr) {
            this.m = m;
            this.clientAddr = clientAddr;
        }
    }

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    PBServer(Address address, Address viewServer, Application app) {
        super(address);
        this.viewServer = viewServer;

        // Your code here...
        this.myAddress = address;

    }

    @Override
    public void init() {
        // should Ping the ViewServer with ViewServer.STARTUP_VIEWNUM
        // After that, they should Ping with the latest view number they've seen,
        // unless they're the primary for a view that has not yet started

        // Your code here...
        send(new Ping(ViewServer.STARTUP_VIEWNUM), viewServer);
        set(new PingTimer(), PingTimer.PING_MILLIS);
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handleRequest(Request m, Address sender) {
        // Your code here...
        if (myAddress.equals(currentView.primary())) {      // for primary server
            if (currentView.backup() == null) {
                AMOResult res = app.execute(m.command());
                send(new Reply(res), sender);
            } else {    // have backup now, should send request to backup
                synchronized (this) {
                    if (!isForwarding && appACKReceived) {
                        mostRecentRequest = m;
                        mostRecentClient = sender;

                        // forward this request to backup server
                        ForwardMessage fm = new ForwardMessage(sender, m);
                        send(fm, currentView.backup());
                        isForwarding = true;

                        // wait for ack from backup server
                        set(new ForwardTimer(fm), ForwardTimer.FORWARD_RETRY_MILLIS);
                    }
                }
            }
        }
    }

    private void handleViewReply(ViewReply m, Address sender) {
            if (currentView == null || m.view().viewNum() > currentView.viewNum()) {

                currentView = m.view();
                if (currentView.backup() != null) {
                    AppMessage apm = new AppMessage(app);
                    send(apm, currentView.backup());
                    set(new AppTimer(apm), AppTimer.APP_RETRY_MILLIS);
                }
            }
    }

    // Your code here...

    // for backup server
    private void handleAppMessage(AppMessage m, Address sender) {
        if (myAddress.equals(currentView.backup()) && sender.equals(currentView.primary())) {
            app = m.application();
            // send the AppACK back to sender
            send(new AppACK(true), sender);
            appACKReceived = false;
        } else {
            send(new GetView(), viewServer);
        }
    }

    private void handleAppACK(AppACK m, Address sender) { appACKReceived = true; }

    // for primary server
    private void handleReply(Reply m, Address sender) {
        mostRecentReply = m;
    }

    // for backup server
    private void handleForwardMessage(ForwardMessage m, Address sender) {
        if (myAddress.equals(currentView.primary())) return;
        if (m.request().command().senderAddr() == null) return;

        AMOResult res = app.execute(m.request().command());
        ForwardACK fack = new ForwardACK(m.clientAddr(), new Reply(res));
        send(fack, sender);
    }

    // for primary server
    private void handleForwardACK(ForwardACK m, Address sender) {
        if (mostRecentRequest != null && m.clientAddr().equals(mostRecentClient)) {
            // execute request and get result
            AMOResult res = app.execute(mostRecentRequest.command());

            // send reply to client
            send(new Reply(res), m.clientAddr());
            isForwarding = false;
            mostRecentRequest = null;
            mostRecentClient = null;

            if (!m.reply().equals(res) && currentView.backup() != null) {
                AppMessage apm = new AppMessage(app);
                send(apm, currentView.backup());
                set(new AppTimer(apm), AppTimer.APP_RETRY_MILLIS);
            }
        }
    }


    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private void onPingTimer(PingTimer t) {
        // Your code here...
        // check for most recent view in each timer period, ping the viewServer
        if (currentView == null) {
            send(new Ping(ViewServer.STARTUP_VIEWNUM), viewServer);
        } else {
            send(new Ping(currentView.viewNum()), viewServer);
        }
        set(t, PingTimer.PING_MILLIS);
    }

    // Your code here...
    private void onForwardTimer(ForwardTimer t) {
        if (isForwarding) {
            if (currentView.backup() != null) {
                send(t.forwardMessage(), currentView.backup());
            }
            set(t, ForwardTimer.FORWARD_RETRY_MILLIS);
        }
    }

    private void onAppTimer(AppTimer t) {
        if (!appACKReceived) {
            if (currentView.backup() != null) {
                send(t.appMessage(), currentView.backup());
            }
            set(t, AppTimer.APP_RETRY_MILLIS);
        }
    }


    /* -------------------------------------------------------------------------
        Utils
       -----------------------------------------------------------------------*/
    // Your code here...
}

// If that backup hasn't heard about view i+1, then it's not acting as primary yet.
// If the backup has heard about view i+1 and is acting as primary,
// it knows enough to reject the old primary's forwarded client requests.
