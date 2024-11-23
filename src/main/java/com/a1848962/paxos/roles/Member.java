package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/* class to simulate a member in the networkInfo. */
public class Member implements Network.PaxosHandler {
    // declare role variables
    private final ProposerRole proposer;
    private final AcceptorRole acceptor;
    private final LearnerRole learner;

    // declare member variables
    private volatile MemberConfig config;

    // declare utility member variables
    protected final Random random = new Random();

    public void start() {
        // Initialize roles if needed
        System.out.println("Starting Node");
    }

    public void shutdown() {
        try {
            // shutdown networkInfo
            if (config.networkTools != null) config.networkTools.shutdown();
        } catch (Exception ex) {
            System.out.println("Error during shutdown - " + ex.getMessage());
        }
    }

    public Member(MemberConfig config) {
        this.config = config;
        this.proposer = config.isProposer ? new Proposer(config) : null;
        this.acceptor = config.isAcceptor ? new Acceptor(config) : null;
        this.learner  = config.isLearner  ? new Learner(config)  : null;
    }

    public void handleIncomingMessage(Message message, OutputStream socketOut) {

    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected single argument containing memberID in format M1, M2, etc");
        } else if (!args[0].matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected positive integer preceded by 'M' (e.g., M1, M2).");
        }

        // create config object to parse role from member.properties
        MemberConfig config = new MemberConfig(args[0]);
        Member member = new Member(config);
        member.start();
    }
}


/* MAKE SINGLE FUNCTION */
/* PROPOSER */
public void handleIncomingMessage(Message message, OutputStream socketOut) {
    // simulate chance for member to go camping
    if (config.chanceCoorong > random.nextDouble()) {
        // member has gone camping in the Coorong, now unreachable
        System.out.println(config.memberID + " is camping in the Coorong. They are unreachable.");
        try {
            Thread.sleep(TIME_IN_COORONG); // unreachable whilst camping
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // simulate chance for member to go to Sheoak cafe
    if (!currentlySheoak && config.chanceSheoak > random.nextDouble()) {
        // member has gone to Sheoak cafe, responses now instant
        System.out.println(config.memberID + " is at Sheoak Café. Responses are instant.");
        this.currentlySheoak = true; // update currentlySheoak boolean and start reset timer using scheduler
        scheduler.schedule(() -> {this.currentlySheoak = false;}, TIME_IN_SHEOAK, TimeUnit.MILLISECONDS);
    }

    // simulate random response delay up to max delay value
    long currentMaxDelay = currentlySheoak ? 0 : config.maxDelay; // delay = 0 if currentlySheoak==true
    long delay = (long)(random.nextDouble() * currentMaxDelay);
    try {
        Thread.sleep(delay);
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    }

    switch (message.type) {
        // most of the time PROMISE/ACCEPT/REJECT messages will be sent as a response to an open socket, and so they
        // will not reach this handler. They are included here in case the sender needs to resend the message.
        case "PROMISE":
            // promise message can only be in response to a prepare request
            handlePrepareReqResponse(message);
            break;
        case "ACCEPT":
            // accept message can only be in response to an accept request
            handleAcceptReqResponse(message);
            break;
        case "REJECT":
            // reject message could be in response to prepare or accept requests, find out which:
            int proposalNumber = message.proposalNumber;
            Proposal proposal = activeProposals.get(proposalNumber);
            if (proposal == null) {
                System.out.println("PROPOSER: Received incoming REJECT from " + message.senderID + " for unknown proposal number " + proposalNumber);
            } else {
                if (!proposal.isPhaseOneCompleted()) {
                    // proposal is active and phase one is incomplete, REJECT is in response to prepare request
                    handlePrepareReqResponse(message);
                } else if (!proposal.isCompleted()) {
                    // proposal is active and phase two is incomplete, REJECT is in response to accept request
                    handleAcceptReqResponse(message);
                }
            }
        case "PREPARE_REQ":
        case "ACCEPT_REQ":
            // message is for acceptors (in this assignment, all proposers will also be acceptors & learners so
            // these role checking if-statements are redundant, I have included them for robustness)
            if (this.config.isAcceptor) super.handleIncomingMessage(message, socketOut);
            break;
        case "LEARN":
            // message is for learners
            if (this.config.isLearner) super.handleIncomingMessage(message, socketOut);
            break;
        default:
            System.out.println("PROPOSER: incoming incompatible message type: " + message.type);
    }
}

/* ACCEPTOR */
public void handleIncomingMessage(Message message, OutputStream socketOut) {
    switch (message.type) {
        case "PROMISE":
        case "ACCEPT":
        case "REJECT":
            // message for proposer node
            break;
        case "PREPARE_REQ":
            System.out.println("ACCEPTOR: Incoming PREPARE_REQ message from " + message.senderID);
            handlePrepareRequest(message, socketOut);
            break;
        case "ACCEPT_REQ":
            System.out.println("ACCEPTOR: Incoming ACCEPT_REQ message from " + message.senderID);
            handleAcceptRequest(message, socketOut);
            break;
        case "LEARN":
            super.handleIncomingMessage(message, socketOut);
            break;
        default:
            System.out.println("ACCEPTOR: Incoming incompatible message type: " + message.type);
    }
}

/* LEARNER */
public void handleIncomingMessage(Message message, OutputStream socketOut) {
    if (message.type.equals("LEARN")) {
        System.out.println("LEARNER: Incoming LEARN message from " + message.senderID);
        handleLearn(message, socketOut);
    } else {
        System.out.println("Received incompatible message type: " + message.type);
    }
}