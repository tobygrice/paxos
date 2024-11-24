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
    protected MemberConfig config;
    protected volatile Network network;

    // delay simulation variables
    protected boolean currentlyCoorong, currentlySheoak;
    protected static final int TIME_IN_SHEOAK = 2000; // 2 seconds at each place
    protected static final int TIME_IN_COORONG = 2000;

    // utility variables
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    protected final Random random = new Random();

    public Member(MemberConfig config) {
        this.config = config;
        this.network = new Network(config.port, this);
        this.currentlySheoak = false;
    }

    public void start() {
        this.proposer = config.isProposer ? new Proposer(config) : null;
        this.acceptor = config.isAcceptor ? new Acceptor(config) : null;
        this.learner  = config.isLearner  ? new Learner(config)  : null;
        this.network.start();
    }

    public void start(boolean proposerAcceptsStdin) {
        this.start();
        if (proposer != null && proposerAcceptsStdin) proposer.listenStdin();
    }

    public void shutdown() {
        try {
            // shutdown networkInfo
            if (network != null) network.shutdown();
        } catch (Exception ex) {
            System.out.println("Error during shutdown - " + ex.getMessage());
        }
    }

    protected void simulateNodeDelay() {
        currentlyCoorong = false;

        // simulate chance for member to go camping
        if (config.chanceCoorong > random.nextDouble()) {
            // member has gone camping in the Coorong, now unreachable
            System.out.println(config.memberID + " is camping in the Coorong. They are unreachable.");
            currentlyCoorong = true;
        }

        // simulate chance for member to go to Sheoak cafe
        if (!currentlySheoak && config.chanceSheoak > random.nextDouble()) {
            // member has gone to Sheoak cafe, responses now instant
            System.out.println(config.memberID + " is at Sheoak Café. Responses are instant.");
            this.currentlySheoak = true; // update currentlySheoak boolean and start reset timer using scheduler
            scheduler.schedule(() -> {this.currentlySheoak = false;}, TIME_IN_SHEOAK, TimeUnit.MILLISECONDS);
        }

        // simulate random response delay up to max delay value
        long currentMinDelay = currentlyCoorong ? TIME_IN_COORONG : 0; // minDelay = TIME_IN_COORONG if currentlyCoorong
        long currentMaxDelay = currentlySheoak  ? 0 : config.maxDelay; // maxDelay = 0 if currentlySheoak==true
        long delay = (long)(random.nextDouble() * currentMaxDelay) + currentMinDelay;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.println("Error during sleeping for delay simulation - " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        // simulateNodeDelay();

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
                System.out.println("Incoming incompatible message type: " + message.type);
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

        if (member.config.isProposer) member.proposer.propose("M4"); // propose M4
        if (member.config.isProposer) member.proposer.propose(); // propose self

        if (member.config.isLearner) member.learner.getLearnedValue();
    }
}