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
    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        switch (message.type) {
            case "PREPARE_REQ":
                handlePrepareRequest(message, socketOut);
                break;
            case "ACCEPT_REQ":
                handleAcceptRequest(message, socketOut);
                break;
            case "LEARN":
                super.handleIncomingMessage(message, socketOut);
                break;
            default:
                logger.warn("{}: Acceptor node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void handlePrepareRequest(Message message, OutputStream socketOut) {
    /* If n is greater than any previous proposal number seen by the acceptor:
        - Acceptor returns a promise to ignore all future proposals with a number < n
        - If the acceptor accepted a proposal at some point in the past, it must include the previous proposal number + value in its response to the proposer
        - send **_prepare-ok_**
	   Otherwise, ignore
     */
    }

    private void handleAcceptRequest(Message message, OutputStream socketOut) {
        /*
        If an acceptor receives an Accept Request message for a proposal n, it must accept (send ***accept-ok***)
        - **if and only if** it has not already promised to only consider proposals having an identifier greater than n -> also implies acceptor considers proposer LEADER.
        - If it has, respond with **accept-reject**
         */
    }

    @Override
    public void start() {
        System.out.println("Performing acceptor role");
    }
}
