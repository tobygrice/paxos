package com.tobygrice.paxos.roles;

import com.tobygrice.paxos.network.Message;
import com.tobygrice.paxos.utils.MemberConfig;
import com.tobygrice.paxos.utils.Proposal;
import com.tobygrice.paxos.utils.SimpleLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proposer class to make propositions and orchestrate Paxos protocol. Implements proposer role.
 */
public class Proposer implements Member.ProposerRole {
    private final Member member; // reference to parent member object

    // proposal variables
    private final AtomicInteger proposalCounter = new AtomicInteger(0);
    private Proposal activeProposal = null;
    private String preferredValue;
    private final int majority;

    // network variables
    private static final int RETRY_DELAY = 2000; // time to wait before retrying a proposal
    private static final int MAX_RETRIES = 3;    // how many times to retry sending a LEARN message

    // utility variables
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final SimpleLogger log = new SimpleLogger("PROPOSER");

    public Proposer(Member member, boolean listenStdin) {
        this.member = member;
        this.preferredValue = member.config.getMemberID(); // default preferred leader is self
        this.majority = (member.config.getNetworkInfo().size() / 2) + 1; // calculate majority required for consensus
        if (listenStdin) listenStdin();
    }

    /**
     * Starts Paxos protocol by broadcasting a prepare request. Unless previously overwritten, member will attempt to
     * propose itself as councillor.
     */
    @Override
    public void propose() {
        sendPrepareRequest();
    }

    /**
     * Starts Paxos protocol by broadcasting a prepare request. Proposer will attempt to propose specified target as
     * councillor.
     *
     * @param target    The member to propose for councillor.
     */
    @Override
    public void propose(String target) {
        this.preferredValue = target;
        sendPrepareRequest();
    }

    /**
     * Silences log output
     */
    @Override
    public void silence() {
        log.silence();
    }

    /**
     * Unsilences log output
     */
    @Override
    public void unsilence() {
        log.unsilence();
    }

    /**
     * Broadcasts a PREPARE_REQ message to all nodes (including self). Response is handled by handlePrepareReqResponse.
     */
    private void sendPrepareRequest() {
        // create a new PREPARE_REQ message and a Proposal object to store proposal data.
        int currentProposalNum = proposalCounter.incrementAndGet();
        Message prepare = Message.prepareRequest(currentProposalNum, member.config.getMemberID());
        activeProposal = new Proposal(currentProposalNum);

        // schedule proposal to timeout and retry after RETRY_DELAY
        scheduler.schedule(() -> {
            if (!activeProposal.isCompleted()) {
                log.info(member.config.getMemberID() + ": Proposal " + activeProposal.getProposalNumber() + " timed out. Starting new proposal");
                sendPrepareRequest();
            }
        }, RETRY_DELAY, TimeUnit.MILLISECONDS);

        log.info(member.config.getMemberID() + ": Broadcasting PREPARE_REQ with proposal number " + currentProposalNum);

        // send PREPARE_REQ message to all acceptors in networkInfo
        for (MemberConfig memberInfo : this.member.config.getNetworkInfo().values()) {
            if (memberInfo.isAcceptor()) {
                // use sendMessage function of NetworkListener to send message to a ServerSocket. Returns a
                // CompletableFuture<Message> object which is passed to handlePrepareReqResponse()
                prepare.send(memberInfo.getAddress(), memberInfo.getPort())
                        .thenAccept(this::handlePrepareReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.getMemberID() + ": Communication failed for PREPARE_REQ to " + memberInfo.getMemberID()
                                    + " for proposal " + prepare.proposalNumber
                                    + ", incrementing reject count");
                            // count failure to send/receive as a rejection
                            activeProposal.incrementRejectCount();
                            checkPhaseOneMajority();
                            return null;
                        });
            }
        }
    }

    /**
     * Handles responses to a PREPARE_REQ message (PROMISE or REJECT)
     *
     * @param response       The response Message.
     */
    @Override
    public void handlePrepareReqResponse(Message response) {

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) return;
        if (activeProposal.getProposalNumber() != proposalNumber) return;

        if (response.type.equals("PROMISE")) {
            activeProposal.addPromise(response);
            log.info(member.config.getMemberID() + ": Received PROMISE from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority();
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            activeProposal.incrementRejectCount();
            // if node is rejecting because it has promised a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.highestPromisedProposal);
            }
            log.info(member.config.getMemberID() + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority();
        } else {
            log.info(member.config.getMemberID() + ": Unexpected response to PREPARE_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    /**
     * Check if PROMISE majority has been reached for activeProposal
     */
    private synchronized void checkPhaseOneMajority() {
        // majority has already been reached and algorithm has progressed, just return:
        if (activeProposal.isPhaseOneCompleted()) return;

        if (activeProposal.getPromiseCount() >= majority) {
            log.info(member.config.getMemberID() + ": Majority PROMISEs received for proposal " + activeProposal.getProposalNumber() + ". Sending ACCEPT_REQUEST");
            activeProposal.markPhaseOneCompleted();
            activeProposal.resetRejectCount(); // reset for next phase
            sendAcceptRequest();
        } else if (activeProposal.getRejectCount() >= majority) {
            log.info(member.config.getMemberID() + ": Majority REJECTs received for proposal " + activeProposal.getProposalNumber() + " in phase one. Allowing scheduler to retry after timeout");
            activeProposal.markPhaseOneCompleted();
            activeProposal.resetRejectCount();
            // allow scheduler to retry prepare phase after proposal times out, to prevent livelock
        }
    }

    /**
     * Determine proposal value and broadcast an ACCEPT_REQ type message for activeProposal.
     *  - If any acceptors sent a value/proposal number to the proposer, then proposer sets the value of its proposal to
     *    the value associated with the highest proposal number reported by the acceptors
     *  - If none of the acceptors had accepted a proposal up to this point, then the proposer may choose any value for
     *    its proposal - in this case, preferredValue
     */
    private void sendAcceptRequest() {
        log.info(member.config.getMemberID() + ": Broadcasting ACCEPT_REQUEST for proposal " + activeProposal.getProposalNumber());

        // assign value to proposal:
        int largestAcceptedProposal = -1;
        activeProposal.value = preferredValue; // use preferred value (self, unless otherwise specified by user)
        for (Message response : activeProposal.getPromises()) {
            // if any node has previously accepted a proposal, use that previously accepted value
            if ((response.acceptedValue != null) && (response.highestPromisedProposal > largestAcceptedProposal)) {
                largestAcceptedProposal = response.highestPromisedProposal;
                activeProposal.value = response.acceptedValue;
            }
        }

        Message acceptRequest = Message.acceptRequest(activeProposal.getProposalNumber(), member.config.getMemberID(), activeProposal.value);

        // send to all acceptors in the networkInfo:
        for (MemberConfig memberInfo : this.member.config.getNetworkInfo().values()) {
            if (memberInfo.isAcceptor()) {
                acceptRequest.send(memberInfo.getAddress(), memberInfo.getPort())
                        .thenAccept(this::handleAcceptReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.getMemberID() + ": Communication failed for ACCEPT_REQ to " + memberInfo.getMemberID()
                                    + " for proposal " + activeProposal.getProposalNumber()
                                    + ", incrementing reject count");
                            activeProposal.incrementRejectCount();
                            checkPhaseTwoMajority();
                            return null;
                        });
            }
        }
    }

    /**
     * Handles incoming responses to an ACCEPT_REQ broadcast (ACCEPT or REJECT)
     *
     * @param response  the incoming message
     */
    @Override
    public void handleAcceptReqResponse(Message response) {

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) return;
        if (activeProposal.getProposalNumber() != proposalNumber) return;

        if (response.type.equals("ACCEPT")) {
            activeProposal.addAccept(response);
            log.info(member.config.getMemberID() + ": Received ACCEPT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseTwoMajority();
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            activeProposal.incrementRejectCount();
            // if node is rejecting because it has accepted a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                log.info(member.config.getMemberID() + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                + " with higher promised value. Updating proposal ID for next round");
                proposalCounter.set(response.highestPromisedProposal);
            } else {
                log.info(member.config.getMemberID() + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                        + " with promised ID: " + response.highestPromisedProposal);
            }
            checkPhaseTwoMajority();
        } else {
            log.info(member.config.getMemberID() + ": Unexpected response to ACCEPT_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    /**
     * Check if ACCEPT majority has been received for activeProposal
     */
    private synchronized void checkPhaseTwoMajority() {
        if (activeProposal.isCompleted()) return;
        if (activeProposal.getAcceptCount() >= majority) {
            log.info(member.config.getMemberID() + ": Majority ACCEPTs received for proposal " + activeProposal.getProposalNumber()
                    + ". Sending LEARN with value " + activeProposal.value);
            activeProposal.markCompleted(); // to prevent scheduler from retrying
            sendLearn(MAX_RETRIES);
            activeProposal = null;
        } else if (activeProposal.getRejectCount() >= majority) {
            log.info(member.config.getMemberID() + ": Majority REJECTS received for proposal " + activeProposal.getProposalNumber() + " in phase two. Retrying");
            // wait for scheduler to retry
        }
    }

    /**
     * Handle incoming REJECT message and dispatch to the appropriate handler
     *
     * @param response      the incoming REJECT message
     */
    @Override
    public void handleRejectResponse(Message response) {

        // determine if REJECT is for PREPARE_REQ or ACCEPT_REQ
        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) {
            log.info(member.config.getMemberID() + ": Received incoming REJECT from " + response.senderID + " with no active proposal.");
        } else if (activeProposal.getProposalNumber() != proposalNumber) {
            log.info(member.config.getMemberID() + ": Received incoming REJECT from " + response.senderID + " for expired proposal " + proposalNumber);
        } else if (!activeProposal.isPhaseOneCompleted()) {
            // proposal is active and phase one is incomplete, REJECT is in response to prepare request
            handlePrepareReqResponse(response);
        } else if (!activeProposal.isCompleted()) {
            // proposal is active and phase two is incomplete, REJECT is in response to accept request
            handleAcceptReqResponse(response);
        }
    }

    /**
     * Recursive function for sending LEARN messages. Sends learn to a single node, if send fails and `retries`
     * is greater than zero, it will call itself again with retries-1.
     *
     * @param learn         the LEARN message to be sent
     * @param memberInfo    the network information of the recipient
     * @param retries       the remaining retry attempts
     */
    private void sendLearnSingleNode(Message learn, MemberConfig memberInfo, int retries) {
        learn.send(memberInfo.getAddress(), memberInfo.getPort())
                .thenAccept(response -> {
                    if (response.type.equals("ACK")) {
                        log.info(member.config.getMemberID() + ": Received ACK from " + response.senderID
                                + " for LEARN message with value " + activeProposal.value);
                    } else if (response.type.equals("NACK")) {
                        if (retries > 0) {
                            log.info(member.config.getMemberID() + ": Received NACK from " + response.senderID
                                    + " for LEARN message with value " + activeProposal.value
                                    + ". Retrying " + retries + " more times");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            sendLearnSingleNode(learn, memberInfo, retries - 1);
                        } else {
                            log.info(member.config.getMemberID() + ": Received too many NACKs from " + response.senderID
                                    + " for LEARN message with value " + activeProposal.value
                                    + ". Node has not learned value");
                        }
                    } else {
                        log.info(member.config.getMemberID() + ": Received unexpected message type: " + response.type + " from "
                                + response.senderID + " for LEARN message with value " + activeProposal.value);
                    }
                })
                .exceptionally(ex -> {
                    if (retries > 0) {
                        log.info(member.config.getMemberID() + ": No response to LEARN received from " + memberInfo.getMemberID()
                                + " for proposal " + activeProposal.getProposalNumber()
                                + ". Retrying " + retries + " more times");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        sendLearnSingleNode(learn, memberInfo, retries - 1);
                    } else {
                        log.info(member.config.getMemberID() + ": Received no response to LEARN from " + memberInfo.getMemberID()
                                + " for proposal " + activeProposal.getProposalNumber()
                                + " too many times. Cannot confirm node has learned value");
                    }
                    return null;
                });
    }

    /**
     * Broadcasts a LEARN message to all learners for activeProposal. Uses sendLearnSingleNode to recursively retry
     * attempts.
     *
     * @param maxRetries    how many times a failed send should be retried before giving up
     */
    private void sendLearn(int maxRetries) {
        Message learn = Message.learn(activeProposal.getProposalNumber(), member.config.getMemberID(), activeProposal.value);
        // send to all learners in networkInfo:
        for (MemberConfig memberInfo : this.member.config.getNetworkInfo().values()) {
            if (memberInfo.isLearner()) {
                sendLearnSingleNode(learn, memberInfo, maxRetries);
            }
        }
    }

    /**
     * Listens for commands from user on STDIN
     */
    private void listenStdin() {

        System.out.println("Proposer accepting commands on STDIN. Usage:");
        System.out.println(" - `propose <value>` to start Paxos protocol for specified value");
        System.out.println(" - `exit` to shut down node");

        // start listening for commands on stdin in a separate thread
        executor.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String command = line.trim().toUpperCase();
                    if (command.startsWith("PROPOSE")) {
                        String[] parts = command.split(" ");
                        if (parts.length > 1) {
                            String value = parts[1];
                            this.preferredValue = value.toUpperCase();
                            System.out.println("Proposing " + value);
                        } else {
                            System.out.println("Please provide a value to propose.");
                        }
                        // send proposal to all nodes in networkInfo:
                        sendPrepareRequest();
                    } else if (command.equals("EXIT")) {
                        System.out.println("Shutting down...");
                        shutdown();
                    } else {
                        System.out.println("Unknown command: " + command);
                        System.out.println("Usage:");
                        System.out.println(" - `propose` to broadcast a prepare message");
                        System.out.println(" - `exit` to shut down node");
                    }
                }
            } catch (IOException e) {
                log.info(member.config.getMemberID() + ": Error reading stdin: " + e.getMessage());
            }
        });
    }

    @Override
    public void shutdown() {
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        log.info(member.config.getMemberID() + ": Proposer shutdown complete");
    }
}