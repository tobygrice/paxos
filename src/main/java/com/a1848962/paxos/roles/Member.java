package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* class to simulate a member in the networkInfo. */
public class Member implements Network.PaxosHandler {

    // role variables
    private ProposerRole proposer;
    private AcceptorRole acceptor;
    private LearnerRole learner;

    // configuration variables
    private MemberConfig config;
    private Network network;

    // delay simulation variables


    // utility variables
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final SimpleLogger log = new SimpleLogger("MEMBER");

    public Member(MemberConfig config) {
        this.config = config;
    }

    public void start() {
        start(false);
    }

    public void start(boolean proposerAcceptsStdin) {
        this.proposer = config.isProposer ? new Proposer(config, proposerAcceptsStdin) : null;
        this.acceptor = config.isAcceptor ? new Acceptor(config) : null;
        this.learner  = config.isLearner  ? new Learner(config)  : null;
        this.network = new Network(config.port, this);
        this.network.start();
    }

    public void shutdown() {
        try {
            // shutdown networkInfo
            if (network != null) network.shutdown();
        } catch (Exception ex) {
            log.error("Error during shutdown - " + ex.getMessage());
        }
    }



    public void handleIncomingMessage(Message message, OutputStream socketOut) {

        switch (message.type) {
            // most of the time PROMISE/ACCEPT/REJECT messages will be sent as a response to an open socket, and so they
            // will not reach this handler. They are included here in case the sender needs to resend the message.
            case "PROMISE": // for proposer
                // promise message can only be in response to a prepare request
                if (proposer != null) proposer.handlePrepareReqResponse(message);
                break;
            case "ACCEPT": // for proposer
                // accept message can only be in response to an accept request
                if (proposer != null) proposer.handleAcceptReqResponse(message);
                break;
            case "REJECT": // for proposer
                // reject message could be in response to prepare or accept requests, find out which:
                if (proposer != null) proposer.handleRejectResponse(message);
                break;
            case "PREPARE_REQ": // for acceptor
                if (acceptor != null) acceptor.handlePrepareRequest(message, socketOut);
                break;
            case "ACCEPT_REQ": // for acceptor
                if (acceptor != null) acceptor.handleAcceptRequest(message, socketOut);
                break;
            case "LEARN": // for learner
                if (learner != null) learner.handleLearn(message, socketOut);
                break;
            default:
                log.warn("Incoming incompatible message type: " + message.type);
        }
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
        member.start(true);
    }
}