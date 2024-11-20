package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.OutputStream;

// all acceptors are learners
public class Acceptor extends Learner {

    public Acceptor(MemberConfig config) {
        super(config);
    }

    @Override
    public void handleMessage(Message message, OutputStream socketOut) {
        switch (message.type) {
            case "PREPARE":
                handlePrepare(message, socketOut);
                break;
            case "ACCEPT_REQUEST":
                handleAcceptRequest(message, socketOut);
                break;
            case "LEARN":
                super.handleMessage(message, socketOut);
                break;
            default:
                logger.warn("{}: Acceptor node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void handlePrepare(Message message, OutputStream socketOut) {
    }

    private void handleAcceptRequest(Message message, OutputStream socketOut) {
    }

    @Override
    public void start() {
        System.out.println("Performing acceptor role");
    }
}
