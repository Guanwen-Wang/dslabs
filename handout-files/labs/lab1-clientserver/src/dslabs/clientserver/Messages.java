package dslabs.clientserver;

import dslabs.framework.Message;
import lombok.Data;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;

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
