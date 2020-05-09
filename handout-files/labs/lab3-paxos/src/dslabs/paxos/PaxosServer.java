package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Node;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.Serializable;

import static dslabs.paxos.Log.clientServer;
import static dslabs.paxos.Log.decision;
import static dslabs.paxos.Log.executed;
import static dslabs.paxos.Log.heartBeat;
import static dslabs.paxos.Log.heartBeatReply;
import static dslabs.paxos.Log.p1aMessage;
import static dslabs.paxos.Log.p1bMessage;
import static dslabs.paxos.Log.p2aMessage;
import static dslabs.paxos.Log.p2bMessage;
import static dslabs.paxos.Log.proposal;
import static dslabs.paxos.Log.status;

@EqualsAndHashCode
class Ballot implements Serializable, Comparable<Ballot>  {
    int ballotNum;
    Address leaderAddress;
    public Ballot(int ballotNum, Address leaderAddress) {
        this.ballotNum = ballotNum;
        this.leaderAddress = leaderAddress;
    }

    @Override
    public int compareTo(Ballot another) {
        if (this.ballotNum == another.ballotNum) {
            return leaderAddress.compareTo(another.leaderAddress);
        }
        return this.ballotNum < another.ballotNum ? -1 : 1;
    }

    @Override
    public String toString() {
        return String.format("[ballotNum=" + ballotNum + ", leaderAddress=" + leaderAddress + "]");
    }
}

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PaxosServer extends Node {
    private final Address[] servers;

    // Your code here...
    // common fields
    boolean isLeader = false;
    boolean electionFinished = false;
    Ballot currentBallot = null;
    Address myAddress = null;
    int totalServerNum = 0;
    int currentVotes = 0;

    Map<Integer, PaxosLogSlotStatus> slotStatusMap = new HashMap<>();
    Map<Integer, AMOCommand> slotCommandMap = new HashMap<>();

    Map<Integer, AMOCommand> proposals = new HashMap<>();
    Map<Integer, AMOCommand> decisions = new HashMap<>();
    Set<Address> p1bSet = new HashSet<>();

    // Replica fields
    AMOApplication app;
    Address leaderAddress = null;
    int slot_firstNonCleared = 1;   // the index of the next slot in which it is the first slot that is not garbage-collected
    int slot_in = 1;    // the index of the next slot in which the replica has not yet proposed any command
    int slot_out = 1;   // the index of the next slot for which it needs to learn a decision before it can update its copy of the application state
    int heartBeatMiss = 0;
    int lastExecutedSlot = 0;


    // Leader fields
    Map<Integer, Set<Address>> garbageMap = new HashMap<>();   // key=slotNum, value=set of replica recved the decision about this slot
    Map<Integer, Set<Address>> voteCount = new HashMap<>();   // key=slotNum, value=number of votes on this slot
    Map<Integer, TreeMap<Ballot, AMOCommand>> acceptedCommands = new HashMap<>(); // slotNum=<Ballot=Command>


    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PaxosServer(Address address, Address[] servers, Application app) {
        super(address);
        this.servers = servers;

        // Your code here...
        this.app = new AMOApplication<>(app);
        this.myAddress = address;
        this.totalServerNum = servers.length;
    }


    @Override
    public void init() {
        // Your code here...
        slotStatusMap.put(slot_in, PaxosLogSlotStatus.EMPTY);

        startMyElection(0);
    }

    /* -------------------------------------------------------------------------
        Interface Methods

        Be sure to implement the following methods correctly. The test code uses
        them to check correctness more efficiently.
       -----------------------------------------------------------------------*/

    /**
     * Return the status of a given slot in the servers's local log.
     *
     * Log slots are numbered starting with 1.
     *
     * @param logSlotNum
     *         the index of the log slot
     * @return the slot's status
     */
    public PaxosLogSlotStatus status(int logSlotNum) {
        // Your code here...
        if (logSlotNum < slot_firstNonCleared) return PaxosLogSlotStatus.CLEARED;
        if (decisions.containsKey(logSlotNum)) return PaxosLogSlotStatus.CHOSEN;
        if (acceptedCommands.containsKey(logSlotNum)) return PaxosLogSlotStatus.ACCEPTED;
        return PaxosLogSlotStatus.EMPTY;
    }

    /**
     * Return the command associated with a given slot in the server's local
     * log. If the slot has status {@link PaxosLogSlotStatus#CLEARED} or {@link
     * PaxosLogSlotStatus#EMPTY}, this method should return {@code null}. If
     * clients wrapped commands in {@link dslabs.atmostonce.AMOCommand}, this
     * method should unwrap them before returning.
     *
     * Log slots are numbered starting with 1.
     *
     * @param logSlotNum
     *         the index of the log slot
     * @return the slot's contents or {@code null}
     */
    public Command command(int logSlotNum) {
        // Your code here...
        if (logSlotNum < slot_firstNonCleared) return null;
        if (decisions.containsKey(logSlotNum)) {
            return decisions.get(logSlotNum).command();
        }
        if (acceptedCommands.containsKey(logSlotNum)) return acceptedCommands.get(logSlotNum).lastEntry().getValue().command();
        return null;
    }

    /**
     * Return the index of the first non-cleared slot in the server's local
     * log.
     *
     * Log slots are numbered starting with 1.
     *
     * @return the index in the log
     */
    public int firstNonCleared() {
        // Your code here...
        return slot_firstNonCleared;
    }

    /**
     * Return the index of the last non-empty slot in the server's local log. If
     * there are no non-empty slots in the log, this method should return 0.
     *
     * Log slots are numbered starting with 1.
     *
     * @return the index in the log
     */
    public int lastNonEmpty() {
        // Your code here...
        if (acceptedCommands.isEmpty() && decisions.isEmpty()) return slot_firstNonCleared - 1;
        if (acceptedCommands.isEmpty()) return Collections.max(decisions.keySet());;
        if (decisions.isEmpty()) return Collections.max(acceptedCommands.keySet());
        int maxAcceptedSlot = Collections.max(acceptedCommands.keySet());
        int maxDecisionSlot = Collections.max(decisions.keySet());
        return Math.max(maxAcceptedSlot, maxDecisionSlot);
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    // For replica
    private void handlePaxosRequest(PaxosRequest m, Address sender) {
        // Your code here...
        if (clientServer) Log.info(address() + " <- " + sender + " : " + m);

        if (app.alreadyExecuted(m.command())) {
            PaxosReply reply = new PaxosReply(app.execute(m.command()));
            send(reply, sender);
            if (clientServer) Log.info(address() + " -> " + sender + " : " + reply);
            return;
        }

        if (decisions.containsValue(m.command())) {
            Log.info(address() + " : " + m.command() + " already in decisions.");
            return;
        }

        if (proposals.containsValue(m.command())) {
            Log.info(address() + " : " + m.command() + " already in proposals.");
            return;
        }

        // propose
        while (proposals.containsKey(slot_in) || decisions.containsKey(slot_in)) {
            slot_in++;
        }
        proposals.put(slot_in, m.command());
        Proposal newProposal = new Proposal(currentBallot, slot_in++, m.command());

        if (myAddress.equals(leaderAddress)) {
            handleProposal(newProposal, leaderAddress);
        } else {
            send(newProposal, leaderAddress);
        }

        set(new ProposalDecisionTimer(newProposal), ProposalDecisionTimer.PROPOSAL_RETRY_MILLIS);
        if (proposal) Log.info(address() + " -> " + leaderAddress + " : " + newProposal);
    }

    // Your code here...

    // For leader
    private void handleProposal(Proposal m, Address sender) {
        if (proposal) Log.info(address() + " <- " + sender + " : " + m + " (recv proposal before isLeader)");

        if (isLeader) {
            if (decisions.containsKey(m.slot_num())) {
                if (proposal) Log.info(address() + " <- " + sender + " : " + m + ", this proposal slot has already in leader decisions");
                return;
            }

            if (proposal) Log.info(address() + " <- " + sender + " : " + m);

            if (m.slot_num() >= slot_firstNonCleared) {
                if (!proposals.containsKey(m.slot_num())) {
                    proposals.put(m.slot_num(), m.command());
                }

                voteCount.put(m.slot_num(), new HashSet<>());
                voteCount.get(m.slot_num()).add(address());

                P2aMessage newP2aMessage = new P2aMessage(currentBallot, m.slot_num(), proposals.get(m.slot_num()));
                for (Address server : servers) {
                    if (server.equals(myAddress)) handleP2aMessage(newP2aMessage, myAddress);
                    else send(newP2aMessage, server);

                    if (p2aMessage) Log.info(address() + " -> "+ server + " : " + newP2aMessage);
                }
                set(new P2aTimer(newP2aMessage), P2aTimer.P2A_RETRY_MILLIS);
            }
        }
    }

    // For replica (P2A)
    private void handleP2aMessage(P2aMessage m, Address sender) {
        if (p2aMessage) Log.info(myAddress + " <- " + sender + " : " + m);

        if (currentBallot.compareTo(m.ballot()) > 0) {
            if (p2aMessage) Log.info(myAddress + " <- " + sender + " : current ballot is " + currentBallot + " which is larger than P2aMessage.ballot=" + m.ballot());
            return;
        } else if (currentBallot.compareTo(m.ballot()) < 0){
            isLeader = m.ballot().leaderAddress.equals(myAddress);
            currentBallot = m.ballot();
            leaderAddress = m.ballot().leaderAddress;
            if (status) Log.info(address() + " is replica in " + currentBallot + " (in handleP2a)");

            set(new HeartBeatCheckTimer(currentBallot), HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);
        }

        // currentBallot equals m.ballot()
        // add to acceptedCommands, change slotStatus to ACCEPTED, add to slotCommandMap, send back P2bMessage
        if (m.slot_num() >= slot_firstNonCleared) {
            if (!acceptedCommands.containsKey(m.slot_num())){
                acceptedCommands.put(m.slot_num(), new TreeMap<>());
            }
            acceptedCommands.get(m.slot_num()).put(currentBallot, m.amoCommand());
            slotStatusMap.put(m.slot_num(), PaxosLogSlotStatus.ACCEPTED);
            slotCommandMap.put(m.slot_num(), m.amoCommand());

            P2bMessage newP2bMessage = new P2bMessage(currentBallot, m.slot_num(),m.amoCommand());
            if (!sender.equals(myAddress)) send(newP2bMessage, sender);
            else handleP2bMessage(newP2bMessage, myAddress);

            if (p2bMessage) Log.info(myAddress + " -> " + sender + " : " + newP2bMessage);
        }
    }

    // For leader (P2B)
    private void handleP2bMessage(P2bMessage m, Address sender) {
        if (isLeader) {
            if (m.slot_num() < slot_firstNonCleared) return;
            if (p2bMessage) Log.info(address() + " <- " + sender + " : " + m);

            if (m.ballot().compareTo(currentBallot) < 0) {
                return;
            }

            if (voteCount.get(m.slot_num()) == null){
                voteCount.put(m.slot_num(), new HashSet<>());
                voteCount.get(m.slot_num()).add(myAddress);
            }
            voteCount.get(m.slot_num()).add(sender);

            // get the majority P2bMessage
            if (voteCount.get(m.slot_num()).size() > this.servers.length / 2) {
                if (status(m.slot_num()) != PaxosLogSlotStatus.CHOSEN && status(m.slot_num()) != PaxosLogSlotStatus.CLEARED){
                    AMOCommand decidedCommand = m.amoCommand();
                    Decision newDecision = new Decision(m.slot_num(), decidedCommand);
                    slotStatusMap.put(m.slot_num(), PaxosLogSlotStatus.CHOSEN);
                    slotCommandMap.put(m.slot_num(), decidedCommand);

                    decisions.put(m.slot_num(), decidedCommand);
                    proposals.remove(m.slot_num());

                    executeCommands();

                    for (Address server : servers) {
                        if (server.equals(myAddress)) handleDecision(newDecision, myAddress);
                        else send(newDecision, server);

                        if (decision) Log.info(address() + " -> " + server + " : " + newDecision);
                    }
                }
            }
        }
    }

    // For replica
    private void handleDecision(Decision m, Address sender) {
        if (!sender.equals(currentBallot.leaderAddress)) return;

        if (decision) Log.info(address() + " <- " + sender + " : " + m);

        if (m.slot_num() >= slot_firstNonCleared) {
            decisions.put(m.slot_num(), m.command());
            slotStatusMap.put(m.slot_num(), PaxosLogSlotStatus.CHOSEN);
            slotCommandMap.put(m.slot_num(), m.command());
            proposals.remove(m.slot_num());

            executeCommands();
        }
    }

    // For replica
    private void handleHeartBeat(HeartBeat m, Address sender) {
        if (heartBeat) Log.info(address() + " <- " + sender + " : HeartBeat(" + m.ballot() + ")");

        if (currentBallot.compareTo(m.ballot()) > 0) {
            return;
        } else if (currentBallot.compareTo(m.ballot()) < 0) {
            isLeader = m.ballot().leaderAddress.equals(myAddress);
            currentBallot = m.ballot();
            leaderAddress = m.ballot().leaderAddress;
            if (status) Log.info(address() + " is replica in " + currentBallot + " (in handleHeartBeat)");

            heartBeatMiss = 0;
            set(new HeartBeatCheckTimer(currentBallot), HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);
        }

        // update decisions
        Iterator<Entry<Integer, AMOCommand>> iterator = m.decisions().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, AMOCommand> entry = iterator.next();
            if (entry.getKey() >= slot_firstNonCleared) {
                slotStatusMap.put(entry.getKey(), PaxosLogSlotStatus.CHOSEN);
                slotCommandMap.put(entry.getKey(), entry.getValue());
                decisions.put(entry.getKey(), entry.getValue());
                proposals.remove(entry.getKey());
            }
        }

        // execute application
        executeCommands();

        // garbage collection for replica
        while (slot_firstNonCleared < m.smallest()) {
            slotStatusMap.put(slot_firstNonCleared, PaxosLogSlotStatus.CLEARED);
            slotCommandMap.remove(slot_firstNonCleared);
            decisions.remove(slot_firstNonCleared);
            proposals.remove(slot_firstNonCleared);
            acceptedCommands.remove(slot_firstNonCleared);
            voteCount.remove(slot_firstNonCleared);

            slot_firstNonCleared++;
        }
        if (status) Log.info(address() + " clear log before slot " + slot_firstNonCleared);

        // reply heart beat
        heartBeatMiss = 0;
        HeartBeatReply reply = new HeartBeatReply(lastExecutedSlot, currentBallot);
        if(address().equals(sender)) handleHeartBeatReply(reply, myAddress);
        else send(reply, leaderAddress);
    }

    // For leader
    private void handleHeartBeatReply(HeartBeatReply m, Address sender) {
        if (heartBeatReply) Log.info(address() + " <- " + sender + " : " + m);

        if (currentBallot.compareTo(m.ballot()) < 0) {
            currentBallot = m.ballot();
            leaderAddress = m.ballot().leaderAddress;
            if (status) Log.info(address() + " is replica in " + currentBallot + " (in handleHeartBeatReply)");

            heartBeatMiss = 0;
            set(new HeartBeatCheckTimer(currentBallot), HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);
        }

        if (isLeader) {
            garbageMap.putIfAbsent(m.replicaLastExecutedSlot(), new HashSet<>());
            garbageMap.get(m.replicaLastExecutedSlot()).add(sender);

            if (garbageMap.get(m.replicaLastExecutedSlot()).size() == totalServerNum) {
                for (int i = 1; i <= m.replicaLastExecutedSlot(); i++) {
                    slotStatusMap.put(i, PaxosLogSlotStatus.CLEARED);
                    slotCommandMap.remove(i);
                    decisions.remove(i);
                    proposals.remove(i);
                    acceptedCommands.remove(i);
                    voteCount.remove(i);
                }

                slot_firstNonCleared = Math.max(slot_firstNonCleared, m.replicaLastExecutedSlot() + 1);
                if (status) Log.info(address() + " clear log before slot " + slot_firstNonCleared);
            }

        }
    }


    /** Handle phase 1 messages => for leader election */

    private void handleP1aMessage(P1aMessage m, Address sender) {
        if (p1aMessage) Log.info(address() + " <- " + sender + " : " + m);

        if (currentBallot.compareTo(m.ballot()) < 0) {
            currentBallot = m.ballot();
            leaderAddress = m.ballot().leaderAddress;
            isLeader = leaderAddress.equals(myAddress);
            electionFinished = true;

            if (status) Log.info(address() + " is replica in " + currentBallot + " (in handleP1a)");

            heartBeatMiss = 0;
            set(new HeartBeatCheckTimer(currentBallot), HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);
        }

        // send p1b back to sender
        P1bMessage newP1bMessage = new P1bMessage(currentBallot, decisions, acceptedCommands);
        if (myAddress.equals(leaderAddress)) handleP1bMessage(newP1bMessage, leaderAddress);
        else send(newP1bMessage, leaderAddress);
        if (p1bMessage) Log.info(myAddress + " -> " + leaderAddress + " : " + newP1bMessage);
    }

    private void handleP1bMessage(P1bMessage m, Address sender) {
        if (p1bMessage) Log.info(address() + " <- " + sender + " : " + m);

        if (currentBallot.compareTo(m.ballot()) < 0){
            isLeader = m.ballot().leaderAddress.equals(myAddress);
            currentBallot = m.ballot();
            leaderAddress = m.ballot().leaderAddress;
            if (status) Log.info(address() + " is replica in " + currentBallot + " (in handleP1b)");

            heartBeatMiss = 0;
            set(new HeartBeatCheckTimer(currentBallot), HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);

        } else if (currentBallot.compareTo(m.ballot()) == 0 && myAddress.equals(currentBallot.leaderAddress)) {
            // update proposals and acceptedCommands
            Iterator<Map.Entry<Integer, TreeMap<Ballot, AMOCommand>>> iterator = m.acceptedCommands().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, TreeMap<Ballot, AMOCommand>> entry = iterator.next(); //slot=<b,c>
                int slotNumber = entry.getKey();
                Map.Entry<Ballot, AMOCommand> largestEntryInSlot = entry.getValue().lastEntry();
                Ballot senderLargestBallot = largestEntryInSlot.getKey();
                AMOCommand curCommand = largestEntryInSlot.getValue();
                if (slotNumber >= slot_firstNonCleared) {
                    if (acceptedCommands.get(slotNumber) == null) {
                        acceptedCommands.putIfAbsent(slotNumber, new TreeMap<>());
                        acceptedCommands.get(slotNumber).put(senderLargestBallot, curCommand);
                        slotStatusMap.put(slotNumber, PaxosLogSlotStatus.ACCEPTED);
                        slotCommandMap.put(slotNumber, curCommand);
                        proposals.put(slotNumber, curCommand);
                    } else {
                        Ballot myBallot = acceptedCommands.get(slotNumber).lastEntry().getKey();
                        if (myBallot.compareTo(senderLargestBallot) <= 0) {
                            acceptedCommands.get(slotNumber).put(senderLargestBallot, curCommand);
                            slotStatusMap.put(slotNumber, PaxosLogSlotStatus.ACCEPTED);
                            slotCommandMap.put(slotNumber, curCommand);
                            proposals.put(slotNumber, curCommand);
                        }
                    }
                }
            }

            // update decisions
            Iterator<Map.Entry<Integer, AMOCommand>> it = m.decisions().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, AMOCommand> entry = it.next(); // s=c
                if (entry.getKey() >= slot_firstNonCleared) {
                    decisions.put(entry.getKey(), entry.getValue());
                    slotStatusMap.put(entry.getKey(), PaxosLogSlotStatus.CHOSEN);
                    slotCommandMap.put(entry.getKey(), entry.getValue());
                    proposals.remove(entry.getKey());
                    executeCommands();
                }
            }

            // check if get the majority P1bMessage
            if (p1bSet.add(sender)) {
                if (p1bSet.size() > servers.length / 2) {  // get the majority election messages
                    if (status) Log.info(address() + " is active leader for " + currentBallot);
                    isLeader = true;
                    electionFinished = true;
                    p1bSet.clear();
                    triggerHeartBeat();

                    // send p2a for every proposal
                    Iterator<Map.Entry<Integer, AMOCommand>> entryIterator = proposals.entrySet().iterator();
                    while (entryIterator.hasNext()) {
                        Map.Entry<Integer, AMOCommand> entry = entryIterator.next(); //s=c
                        P2aMessage newP2aMessage = new P2aMessage(currentBallot, entry.getKey(), entry.getValue());
                        for (Address server: servers){
                            if (server.equals(address())) handleP2aMessage(newP2aMessage, address());
                            else send(newP2aMessage, server);
                            if (p2aMessage) Log.info(myAddress + " -> " + server + " : (in handleP1b) " + newP2aMessage);
                        }
                    }
                }
            }
        }
    }



    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    // Your code here...
    private void onP1aTimer(P1aTimer t) {
        if (!isLeader && t.p1aMessage().ballot().equals(currentBallot)) {
            for (Address server: servers) {
                if (server.equals(myAddress)) handleP1aMessage(t.p1aMessage(), address());
                else send(t.p1aMessage(), server);

                if (p1aMessage) Log.info(myAddress + " -> " + server + " : (onP1a Timer) " + t.p1aMessage());
            }
            set(t, P1aTimer.P1A_RETRY_MILLIS);
        }
    }

    private void onHeartBeatTimer(HeartBeatTimer t) {
        if (isLeader) {
            HeartBeat hb = new HeartBeat(currentBallot, decisions, slot_firstNonCleared);
            for (Address server: servers) {
                if (myAddress.equals(server)) handleHeartBeat(hb, myAddress);
                else send(hb, server);
                if (heartBeat) Log.info(myAddress + " -> " + server + " : (HeartBeat Timer) " + hb.ballot());
            }
            set(new HeartBeatTimer(), HeartBeatTimer.HEARTBEAT_MILLIS);
        }
    }

    private void onProposalDecisionTimer(ProposalDecisionTimer t) {
        if (status(t.proposal().slot_num()) != PaxosLogSlotStatus.CHOSEN && status(t.proposal().slot_num()) != PaxosLogSlotStatus.CLEARED) {
            Proposal newProposal = new Proposal(currentBallot, t.proposal().slot_num(), t.proposal().command());
            send(newProposal, leaderAddress);

            if (proposal) Log.info(address() + " -> " + leaderAddress + " : (onProposal Timer) "+ newProposal);

            set(new ProposalDecisionTimer(newProposal), ProposalDecisionTimer.PROPOSAL_RETRY_MILLIS);
        }
    }

    private void onHeartBeatCheckTimer(HeartBeatCheckTimer t) {
        if (t.ballot().compareTo(currentBallot) == 0) {
            heartBeatMiss++;
            if (heartBeatMiss == 2) {
                if (status) Log.info(address() + " : " + currentBallot.leaderAddress + " time out (" + currentBallot + ") ; compete for leader");

                heartBeatMiss = 0;
                p1bSet.clear();
                startMyElection(currentBallot.ballotNum + 1);
            } else {
                set(t, HeartBeatCheckTimer.HEARTBEAT_RETRY_MILLIS);
            }
        }
    }

    private void onP2aTimer(P2aTimer t) {
        if (!currentBallot.leaderAddress.equals(myAddress)) return;

        if (!decisions.containsKey(t.p2aMessage().slot_num()) && t.p2aMessage().slot_num() >= slot_firstNonCleared) {       /// > executedSlot
            P2aMessage newP2aMessage = new P2aMessage(currentBallot, t.p2aMessage().slot_num(), t.p2aMessage().amoCommand());

            for(Address server : servers) {
                if (server.equals(myAddress)) handleP2aMessage(newP2aMessage, myAddress);
                else send(newP2aMessage, server);

                if (p2aMessage) Log.info(address() + " -> " + server + " : (onP2a Timer) " + newP2aMessage);
            }

            set(new P2aTimer(newP2aMessage), P2aTimer.P2A_RETRY_MILLIS);
        }
    }


    /* -------------------------------------------------------------------------
        Utils
       -----------------------------------------------------------------------*/
    // Your code here...
    private void startMyElection(int newBallotNum) {
        isLeader = false;
        electionFinished = false;
        currentBallot = new Ballot(newBallotNum, myAddress);
        leaderAddress = myAddress;
        P1aMessage p1amsg = new P1aMessage(currentBallot);
        for (Address server: servers) {     // broadcast election message to all servers
            if (server.equals(myAddress)) handleP1aMessage(p1amsg, address());
            else send(p1amsg, server);
            if (p1aMessage) Log.info(myAddress + " -> " + server + " : " + p1amsg);
        }

        p1bSet.clear();

        set(new P1aTimer(p1amsg), P1aTimer.P1A_RETRY_MILLIS);
    }

    // Leader
    private void triggerHeartBeat() {
        HeartBeat hb = new HeartBeat(currentBallot, decisions, slot_firstNonCleared);
        for (Address server : servers) {
            if (myAddress.equals(server)) {
                handleHeartBeat(hb, server);
            } else {
                send(hb, server);
            }

            if (heartBeat) Log.info(myAddress + " -> " + server + " : (HeartBeat) " + hb.ballot());
        }
        set(new HeartBeatTimer(), HeartBeatTimer.HEARTBEAT_MILLIS);
    }

    private void executeCommands() {
        while (decisions.containsKey(lastExecutedSlot + 1)) {
            AMOCommand amoCommand = decisions.get(lastExecutedSlot + 1);
            AMOResult res = app.execute(amoCommand);
            send(new PaxosReply(res), amoCommand.senderAddr());
            lastExecutedSlot++;

            if (executed) Log.info(address() + " executed " + lastExecutedSlot);
            if (clientServer) Log.info(address() + " -> " + amoCommand.senderAddr() + " : " + res);
        }
        slot_in = lastExecutedSlot + 1;
    }

}
