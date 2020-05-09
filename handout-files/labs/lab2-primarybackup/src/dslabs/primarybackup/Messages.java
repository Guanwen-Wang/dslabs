package dslabs.primarybackup;

import dslabs.framework.Message;
import lombok.Data;

import dslabs.framework.Result;
import dslabs.framework.Command;
import dslabs.framework.Address;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import dslabs.atmostonce.AMOApplication;

/* -------------------------------------------------------------------------
    ViewServer Messages
   -----------------------------------------------------------------------*/
@Data
class Ping implements Message {
    private final int viewNum;
}

@Data
class GetView implements Message {
}

@Data
class ViewReply implements Message {
    private final View view;
}

/* -------------------------------------------------------------------------
    Primary-Backup Messages
   -----------------------------------------------------------------------*/
@Data
class Request implements Message {
    // Your code here...
    private final AMOCommand command;
}

@Data
class Reply implements Message {
    // Your code here...
    private final AMOResult result;
}

// Your code here...
@Data
class AppMessage implements Message {
    private final AMOApplication application;
}

@Data
class AppACK implements Message {
    private final boolean ack;
}

@Data
class ForwardMessage implements Message {
    private final Address clientAddr;
    private final Request request;
}

@Data
class ForwardACK implements Message {
    private final Address clientAddr;
    private final Reply reply;
}
