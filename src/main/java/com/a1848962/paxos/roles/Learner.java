package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.OutputStream;

// All members are learners. For this assignment, all members are also acceptors,
// but this is not a requirement of Paxos. Therefore, I have seperated the learner/acceptor
// classes.
public class Learner extends Member {

    public Learner(MemberConfig config) {
        super(config);
    }

    @Override
    public void start() {
        System.out.println("Performing learner role");
    }

    @Override
    public void handleMessage(Message message, OutputStream socketOut) {
        if (message.type.equals("LEARN")) {
            handleLearn(message, socketOut);
        } else {
            logger.warn("{}: Learner node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void handleLearn(Message message, OutputStream socketOut) {
    }

}
