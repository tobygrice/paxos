package com.tobygrice.paxos.roles;

import com.tobygrice.paxos.network.Message;
import com.tobygrice.paxos.network.NetworkListener;
import com.tobygrice.paxos.utils.MemberConfig;
import com.tobygrice.paxos.utils.SimpleLogger;

import java.io.OutputStream;
import java.util.Random;

/**
 * Represents a member in the Paxos networkListener. Can assume any combination of proposer, acceptor, and learner roles.
 */
public class Member implements NetworkListener.PaxosHandler {
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

    // simulation variables
    private final double LOSS_CHANCE = 0.0;

    // configuration variables
    public MemberConfig config;

    // role variables
    private ProposerRole proposer;
    private AcceptorRole acceptor;
    private LearnerRole learner;

    // utility variables
    private NetworkListener networkListener;
    protected final Random random = new Random();
    private static final SimpleLogger log = new SimpleLogger("MEMBER");

    public Member(MemberConfig config) {
        this.config = config;
    }

    /**
     * Mutes log output for Member, networkListener, and all role objects
     */
    public void silence() {
        if (this.proposer != null) proposer.silence();
        if (this.acceptor != null) acceptor.silence();
        if (this.learner != null) learner.silence();
        if (this.networkListener != null) networkListener.silence();
        log.silence();
    }

    /**
     * Unmutes log output for Member, networkListener, and all role objects
     */
    public void unsilence() {
        if (this.proposer != null) proposer.unsilence();
        if (this.acceptor != null) acceptor.unsilence();
        if (this.learner != null) learner.unsilence();
        if (this.networkListener != null) networkListener.unsilence();
        log.unsilence();
    }

    /**
     * Default start function for Member. Instantiates role objects as required and starts networkListener to listen for
     * messages. By default, proposer nodes will not accept stdin. To specify, use start(boolean proposerAcceptsStdin)
     */
    public void start() {
        start(false);
    }

    /**
     * Start function for Member. Instantiates role objects as required and starts networkListener to listen for
     * messages.
     *
     * @param proposerAcceptsStdin      proposer nodes should accept `propose` commands from stdin
     */
    public void start(boolean proposerAcceptsStdin) {
        log.info(config.getMemberID() + ": Starting Member");
        this.proposer = config.isProposer() ? new Proposer(this, proposerAcceptsStdin) : null;
        this.acceptor = config.isAcceptor() ? new Acceptor(this) : null;
        this.learner  = config.isLearner() ? new Learner(this)  : null;
        this.networkListener = new NetworkListener(config.getPort(), this);
        this.networkListener.start();
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
        if (networkListener != null) networkListener.shutdown();
        if (proposer != null) proposer.shutdown();
        log.info(config.getMemberID() + ": Shutdown complete");
    }

    /**
     * Implements PaxosHandler interface. All messages to networkListener object's ServerSocket are unmarshalled and passed
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
                log.warn(config.getMemberID() + ": Incoming incompatible message type: " + message.type);
        }
    }

    /**
     * Simulates reliability issues.
     *
     * @return      true if the member should ignore a message, else false
     */
    protected boolean simulateNodeReliability() {
        return (random.nextDouble() < LOSS_CHANCE);
    }

}