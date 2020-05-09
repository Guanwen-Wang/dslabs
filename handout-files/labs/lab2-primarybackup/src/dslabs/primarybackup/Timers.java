package dslabs.primarybackup;

import dslabs.framework.Timer;
import lombok.Data;

import dslabs.atmostonce.AMOApplication;

@Data
final class PingCheckTimer implements Timer {
    static final int PING_CHECK_MILLIS = 100;
}

@Data
final class PingTimer implements Timer {
    static final int PING_MILLIS = 25;
}

@Data
final class ClientTimer implements Timer {
    static final int CLIENT_RETRY_MILLIS = 100;

    // Your code here...
    private final Request request;
}

// Your code here...
@Data
final class ForwardTimer implements Timer {
    static final int FORWARD_RETRY_MILLIS = 100;

    private final ForwardMessage forwardMessage;
}

@Data
final class AppTimer implements Timer {
    static final int APP_RETRY_MILLIS = 100;

    private final AppMessage appMessage;
}
