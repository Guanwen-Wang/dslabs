package dslabs.atmostonce;

import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Map;
import java.util.HashMap;
import dslabs.kvstore.KVStore;
import dslabs.framework.Address;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public final class AMOApplication<T extends Application>
        implements Application {
    @Getter @NonNull private final T application;

    // Your code here...
    public Map<Address, AMOResult> map = new HashMap<>();
    
    @Override
    public AMOResult execute(Command command) {
        if (!(command instanceof AMOCommand)) {
            throw new IllegalArgumentException();
        }

        AMOCommand amoCommand = (AMOCommand) command;

        // Your code here...
	    if (alreadyExecuted(amoCommand)) {
	        return map.get(amoCommand.senderAddr());
        }
        Result res = application.execute(amoCommand.command());
        AMOResult amoResult = new AMOResult(res, amoCommand.sequenceNum());
	    map.put(amoCommand.senderAddr(), amoResult);
        return amoResult;
    }

    public Result executeReadOnly(Command command) {
        if (!command.readOnly()) {
            throw new IllegalArgumentException();
        }

        if (command instanceof AMOCommand) {
            return execute(command);
        }

        return application.execute(command);
    }

    public boolean alreadyExecuted(AMOCommand amoCommand) {
        // Your code here...
	    return map.containsKey(amoCommand.senderAddr()) &&
                map.get(amoCommand.senderAddr()).sequenceNum() >= amoCommand.sequenceNum();
    }
}
