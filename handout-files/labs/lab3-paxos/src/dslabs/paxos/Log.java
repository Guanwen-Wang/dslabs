package dslabs.paxos;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
    public static final boolean all = false;
    public static final boolean clientServer = false;
    public static final boolean p1aMessage = false;
    public static final boolean p1bMessage = false;
    public static final boolean p2aMessage = false;
    public static final boolean p2bMessage = false;
    public static final boolean proposal = false;
    public static final boolean decision = false;
    public static final boolean heartBeat = false;
    public static final boolean heartBeatReply = false;

    public static final boolean partitionMsg = false;

    public static final boolean status = false;
    public static final boolean slotStatus = false;
    public static final boolean majority = false;
    public static final boolean executed = false;


    public static void info(String message) {
        long time = System.currentTimeMillis();
        if (all) System.out.println("["  + time + "] " + message);
    }

}
