package dslabs.primarybackup;

import dslabs.framework.Address;
import dslabs.framework.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import static dslabs.primarybackup.PingCheckTimer.PING_CHECK_MILLIS;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class ViewServer extends Node {
    static final int STARTUP_VIEWNUM = 0;
    private static final int INITIAL_VIEWNUM = 1;

    // Your code here...
    View currentView = new View(STARTUP_VIEWNUM, null, null);
//    Address mostRecentPingedServer = null;
    Address primary = null;
    Address backup = null;
    boolean isACK = false;
    Map<Address, Integer> map = new HashMap<>();  // key = server address, value = num of miss
    List<Address> allServers = new ArrayList<>();
    List<Address> idles = new LinkedList<>();

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public ViewServer(Address address) {
        super(address);
    }

    @Override
    public void init() {
        set(new PingCheckTimer(), PING_CHECK_MILLIS);
        // Your code here...
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handlePing(Ping m, Address sender) {
        // Your code here...

        // first time ping, initial primary
        if (m.viewNum() == STARTUP_VIEWNUM) {
            if (primary == null) {
                currentView = new View(INITIAL_VIEWNUM, sender, null);
                send(new ViewReply(currentView), sender);
                primary = sender;
                return;
            }
        }

        // receive ack from primary
        if (sender.equals(primary) && m.viewNum() == currentView.viewNum()) {
//            System.out.println("receive ack from " + sender + " and current viewNum is " + currentView.viewNum());
            isACK = true;
        }

        // add this server into idle poll
        if (!sender.equals(primary) && !sender.equals(backup) && !idles.contains(sender)) {
            idles.add(sender);
        }

        if (isACK) {
            if (backup == null && idles.size() > 0) { // case2 increment current viewNum
                backup = idles.remove(0);
                currentView = new View(currentView.viewNum() + 1, primary, backup);
                isACK = false;
            }
        }

        send(new ViewReply(currentView), sender);

        if (!map.containsKey(sender)) {
            allServers.add(sender);
        }
        map.put(sender, 0);
    }

    private void handleGetView(GetView m, Address sender) {
        // Your code here...

        send(new ViewReply(currentView), sender);
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private void onPingCheckTimer(PingCheckTimer t) {
        // Your code here...
        List<Address> toRemove = new ArrayList<>();
        for (Address server: allServers) {
            int curMiss = map.get(server);
            if (curMiss >= 1) {
                if (server.equals(primary)) {
                    if (isACK) {    // case1 increment current viewNum
                        primary = backup;
                        if (idles.size() > 0) {
                            backup = idles.remove(0);
                        } else {
                            backup = null;
                        }
                        currentView = new View(currentView.viewNum() + 1, primary, backup);
                        isACK = false;
                    }
                } else if (server.equals(backup)) {
                    if (isACK) {
                        if (idles.size() > 0) {
                            backup = idles.remove(0);
                        } else {
                            backup = null;
                        }
                        currentView = new View(currentView.viewNum() + 1, primary, backup);
                        isACK = false;
                    }
                }

                map.remove(server);
                toRemove.add(server);
            } else {
                map.put(server, curMiss + 1);
            }
        }

        // remove all dead servers
        for (Address deadServer: toRemove) {
            allServers.remove(deadServer);
            idles.remove(deadServer);
        }

        set(t, PING_CHECK_MILLIS);
    }

    /* -------------------------------------------------------------------------
        Utils
       -----------------------------------------------------------------------*/
    // Your code here...
}
