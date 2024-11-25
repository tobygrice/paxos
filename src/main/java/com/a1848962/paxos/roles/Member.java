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
    // configuration variables
    protected MemberConfig config;

    // delay simulation variables
    //      while network delay simulation is in place, be careful not to set RETRY_DELAY below MAX_DELAY of Network class
    //      also consider coorong simulation, some nodes may take over 2000ms to respond
    protected static final int TIME_IN_SHEOAK = 10000; // time in ms for member to stay at coorong
    protected static final int TIME_IN_COORONG = 10000; // time in ms for member to stay at coorong
    protected static final int SIMULATION_FREQUENCY = 2000; // frequency in ms to simulate chance of Coorong/Sheoak state

    // state variables for delay simulation
    protected boolean currentlyCoorong = false;
    protected boolean currentlySheoak = false; // boolean variables to indicate coorong/sheoak status
    protected long coorongStartTime = 0;
    protected long sheoakStartTime = 0;

    // role variables
    private ProposerRole proposer;
    private AcceptorRole acceptor;
    private LearnerRole learner;

    // utility variables
    private Network network;
    protected final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final SimpleLogger log = new SimpleLogger("MEMBER");

    public Member(MemberConfig config) {
        this.config = config;
    }

    public void start() {
        start(false);
    }

    public void start(boolean proposerAcceptsStdin) {
        log.info("Starting Member");
        this.proposer = config.isProposer ? new Proposer(this, proposerAcceptsStdin) : null;
        this.acceptor = config.isAcceptor ? new Acceptor(this) : null;
        this.learner  = config.isLearner  ? new Learner(this)  : null;
        this.network = new Network(config.port, this);
        this.network.start();
        scheduler.scheduleAtFixedRate(this::simulateSheoakCoorong, SIMULATION_FREQUENCY, SIMULATION_FREQUENCY, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (network != null) network.shutdown();
        if (proposer != null) proposer.shutdown();
        scheduler.shutdownNow(); // shutdown scheduler
        log.info("Shutdown complete");
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

    /**
     * Method to simulate chance of a state change to Sheoak or Coorong. Called every SIMULATION_FREQUENCY ms
     *     This function was written with the assistance of AI
     */
    private void simulateSheoakCoorong() {

        long currentTime = System.currentTimeMillis();

        // simulate chance for member to go camping in the Coorong
        if (!currentlyCoorong && !currentlySheoak && random.nextDouble() < (config.chanceCoorong)) {
            // Member has gone camping in the Coorong, now unreachable
            log.info(config.memberID + " is camping in the Coorong. They are unreachable");
            currentlyCoorong = true;
            coorongStartTime = currentTime;

            // exit Coorong after TIME_IN_COORONG ms
            scheduler.schedule(() -> {
                synchronized (this) {
                    currentlyCoorong = false;
                    coorongStartTime = 0;
                    log.info(config.memberID + " has returned from the Coorong");
                }
            }, TIME_IN_COORONG, TimeUnit.MILLISECONDS);
        }

        // simulate chance for member to work at Sheoak cafe
        else if (!currentlyCoorong && !currentlySheoak && random.nextDouble() < config.chanceSheoak) {
            // Member has gone to Sheoak cafe, responses now instant
            log.info(config.memberID + " is at Sheoak cafe. Responses are now instant");
            currentlySheoak = true;
            sheoakStartTime = currentTime;

            // Schedule to exit Sheoak Café after TIME_IN_SHEOAK milliseconds
            scheduler.schedule(() -> {
                synchronized (this) {
                    currentlySheoak = false;
                    sheoakStartTime = 0;
                    log.info(config.memberID + " has left Sheoak cafe");
                }
            }, TIME_IN_SHEOAK, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Method to simulate delay (or lack thereof) for a proposer node according to Sheoak or Coorong status
     */
    protected long simulateNodeDelay() {
        long currentTime = System.currentTimeMillis();

        // calculate delay based on current state:
        long delay;
        if (currentlySheoak) {
            // instant response
            delay = 0;
        } else if (currentlyCoorong) {
            // delay until TIME_IN_COORONG has passed (since entering)
            long elapsed = currentTime - coorongStartTime;
            delay = TIME_IN_COORONG - elapsed;
            log.info("Currently Coorong, not responding for another " + delay + " ms");
        } else {
            // normal operation: random delay up to maxDelay
            delay = (long) (random.nextDouble() * config.maxDelay);
        }

        if (delay > 0) {
            return delay;
        } else {
            return 0;
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