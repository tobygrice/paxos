package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Represents a member in the Paxos network. Can assume any combination of proposer, acceptor, and learner roles.
 */
public class Member implements Network.PaxosHandler {
    public interface LearnerRole {
        String getLearnedValue();
        void handleLearn(Message message, OutputStream socketOut);
        void silence();
        void unsilence();
    }

    public interface AcceptorRole {
        void handlePrepareRequest(Message message, OutputStream socketOut);
        void handleAcceptRequest(Message message, OutputStream socketOut);
        void silence();
        void unsilence();
    }

    public interface ProposerRole {
        void handlePrepareReqResponse(Message response);
        void handleAcceptReqResponse(Message response);
        void handleRejectResponse(Message response);
        void propose();
        void propose(String target);
        void silence();
        void unsilence();
        void shutdown();
    }

    // configuration variables
    public MemberConfig config;

    // delay simulation variables
    //      while network delay simulation is in place, be careful not to set RETRY_DELAY below MAX_DELAY of Network class
    //      also consider coorong simulation, some nodes may take over 2000ms to respond
    protected static final int TIME_IN_SHEOAK = 3000; // time in ms for member to stay at coorong
    protected static final int TIME_IN_COORONG = 3000; // time in ms for member to stay at coorong
    protected static final int SIMULATION_FREQUENCY = 1000; // frequency in ms to simulate chance of Coorong/Sheoak state

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
    private final ScheduledExecutorService simulationScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private static final SimpleLogger log = new SimpleLogger("MEMBER");

    public Member(MemberConfig config) {
        this.config = config;
    }

    /**
     * Mutes log output for Member, network, and all role objects
     */
    public void silence() {
        if (this.proposer != null) proposer.silence();
        if (this.acceptor != null) acceptor.silence();
        if (this.learner != null) learner.silence();
        if (this.network != null) network.silence();
        log.silence();
    }

    /**
     * Unmutes log output for Member, network, and all role objects
     */
    public void unsilence() {
        if (this.proposer != null) proposer.unsilence();
        if (this.acceptor != null) acceptor.unsilence();
        if (this.learner != null) learner.unsilence();
        if (this.network != null) network.unsilence();
        log.unsilence();
    }

    /**
     * Default start function for Member. Instantiates role objects as required and starts network to listen for
     * messages. By default, proposer nodes will not accept stdin, and the Coorong/Sheoak simulation will not be run.
     * To change this, use: start(boolean proposerAcceptsStdin, boolean simulateSheoakCoorong)
     */
    public void start() {
        start(false, false);
    }

    /**
     * Start function for Member. Instantiates role objects as required and starts network to listen for
     * messages.
     *
     * @param proposerAcceptsStdin      proposer nodes should accept `propose` commands from stdin
     * @param simulateSheoakCoorong     sheoak/coorong simulation should be run
     */
    public void start(boolean proposerAcceptsStdin, boolean simulateSheoakCoorong) {
        log.info(config.memberID + ": Starting Member");
        this.proposer = config.isProposer ? new Proposer(this, proposerAcceptsStdin) : null;
        this.acceptor = config.isAcceptor ? new Acceptor(this) : null;
        this.learner  = config.isLearner  ? new Learner(this)  : null;
        this.network = new Network(config.port, this);
        this.network.start();
        if (simulateSheoakCoorong) startSheoakCoorongSimulation();
    }

    /**
     * Starts sheoak/coorong simulation. Schedules simulateSheoakCoorong() to be executed every SIMULATION_FREQUENCY ms
     */
    public void startSheoakCoorongSimulation() {
        log.info(config.memberID + ": Starting Sheoak cafe / Coorong simulation");
        simulationScheduler.scheduleAtFixedRate(this::simulateSheoakCoorong, 0, SIMULATION_FREQUENCY, TimeUnit.MILLISECONDS);
    }

    public void stopSheoakCoorongSimulation() {
        log.info(config.memberID + ": Stopping Sheoak cafe / Coorong simulation");
        simulationScheduler.shutdownNow();
    }

    public LearnerRole getLearner() {
        return learner;
    }

    public AcceptorRole getAcceptor() {
        return acceptor;
    }

    public ProposerRole getProposer() {
        return proposer;
    }

    public void shutdown() {
        if (network != null) network.shutdown();
        if (proposer != null) proposer.shutdown();
        simulationScheduler.shutdownNow();
        scheduler.shutdownNow(); // shutdown scheduler
        log.info(config.memberID + ": Shutdown complete");
    }

    /**
     * Implements PaxosHandler interface. All messages to network object's ServerSocket are unmarshalled and passed
     * to this function.
     * @param message       the message object that has been received
     * @param socketOut     the socket out for response
     */
    @Override
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
                log.warn(config.memberID + ": Incoming incompatible message type: " + message.type);
        }
    }

    /**
     * Forces the member to go camping in the Coorong. While in the Coorong, no messages will be received by the member.
     *
     * @param value     the value currentlyCoorong should be set to
     * @param time      how long the member should stay camping in the Coorong (only accessed when `value` is true)
     */
    synchronized public void forceCoorong(boolean value, long time) {
        if (value) {
            log.info(config.memberID + " is camping in the Coorong. They are unreachable");

            currentlyCoorong = true;
            coorongStartTime = System.currentTimeMillis();

            // exit Coorong after `time` ms
            scheduler.schedule(() -> forceCoorong(false, 0), time, TimeUnit.MILLISECONDS);
        } else {
            currentlyCoorong = false;
            coorongStartTime = 0;
            log.info(config.memberID + " has returned from the Coorong");
        }
    }

    /**
     * Forces the member to work at Sheoak cafe. While at Sheoak cafe, member always responds and without delay.
     *
     * @param value     the value currentlySheoak should be set to
     * @param time      how long the member should stay at Sheoak cafe (only accessed when `value` is true)
     */
    synchronized public void forceSheoak(Boolean value, long time) {
        if (value) {
            log.info(config.memberID + " is at Sheoak cafe. Responses are now instant");

            currentlySheoak = true;
            sheoakStartTime = System.currentTimeMillis();

            // exit Sheoak after `time` milliseconds
            scheduler.schedule(() -> forceSheoak(false, 0), time, TimeUnit.MILLISECONDS);
        } else {
            currentlySheoak = false;
            sheoakStartTime = 0;
            log.info(config.memberID + " has left Sheoak cafe");
        }
    }


    /**
     * Simulates chance of a state change to Sheoak or Coorong. If sheoak/coorong simulation is active,
     * this is called every SIMULATION_FREQUENCY ms
     */
    private void simulateSheoakCoorong() {
        // simulate chance for member to go camping in the Coorong
        if (!currentlyCoorong && !currentlySheoak && random.nextDouble() < config.chanceCoorong) {
            forceCoorong(true, TIME_IN_COORONG);
        }

        // simulate chance for member to work at Sheoak cafe
        else if (!currentlyCoorong && !currentlySheoak && random.nextDouble() < config.chanceSheoak) {
            forceSheoak(true, TIME_IN_SHEOAK);
        }
    }

    /**
     * Simulates delay (or lack thereof) for member according to Sheoak or Coorong status
     *
     * @return      the time in milliseconds to sleep
     */
    protected long simulateNodeDelay() {
        long delay; // calculate delay based on current state
        if (currentlySheoak) delay = 0; // instant response
        else delay = (long) (random.nextDouble() * config.maxDelay); // normal operation: random delay up to maxDelay

        if (delay < 0) delay = 0;

        return delay;
    }

    /**
     * Simulates reliability of member according to value in config. If member is currentlyCoorong,
     * loss chance is overridden to 100%. If member is currentlySheoak, loss chance is overridden 0%.
     *
     * @return      true if the member should ignore a message, else false
     */
    protected boolean simulateNodeReliability() {
        double reliability = config.reliability;
        if (currentlyCoorong) {
            reliability = 0.0;
        } else if (currentlySheoak) {
            reliability = 1.0;
        }
        double lossChance = 1.0 - reliability;
        return (random.nextDouble() < lossChance);
    }

    /**
     * Takes memberID as a command line argument and starts member. Will automatically enable proposerAcceptsStdin.
     *
     * @param args  a single argument containing memberID in format M1, M2, etc
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected single argument containing memberID in format M1, M2, etc");
        } else if (!args[0].matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected positive integer preceded by 'M' (e.g., M1, M2).");
        }

        // create config object to parse role from member.properties
        MemberConfig config = new MemberConfig(args[0]);
        Member member = new Member(config);
        member.start(true, true);
    }
}