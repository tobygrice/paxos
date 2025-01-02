package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

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
    private String preferredLeader;
    private final int majority;

    // network variables
    private static final int RETRY_DELAY = 2000; // time to wait before retrying a proposal
    private static final int MAX_RETRIES = 3; // how many times to retry sending a LEARN message

    // utility variables
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final SimpleLogger log = new SimpleLogger("PROPOSER");

    public Proposer(Member member, boolean listenStdin) {
        this.member = member;
        this.preferredLeader = member.config.memberID; // default preferred leader is self
        this.majority = (member.config.networkInfo.size() / 2) + 1; // calculate majority required for consensus
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
        this.preferredLeader = target;
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
        Message prepare = Message.prepareRequest(currentProposalNum, member.config.memberID);
        activeProposal = new Proposal(currentProposalNum);

        // schedule proposal to timeout and retry after RETRY_DELAY
        scheduler.schedule(() -> {
            if (!activeProposal.isCompleted()) {
                log.info(member.config.memberID + ": Proposal " + activeProposal.getProposalNumber() + " timed out. Starting new proposal");
                sendPrepareRequest();
            }
        }, RETRY_DELAY, TimeUnit.MILLISECONDS);

        if (member.currentlyCoorong) return;
        log.info(member.config.memberID + ": Broadcasting PREPARE_REQ with proposal number " + currentProposalNum);

        // send PREPARE_REQ message to all acceptors in networkInfo
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                // use sendMessage function of Network to send message to a ServerSocket. Returns a
                // CompletableFuture<Message> object which is passed to handlePrepareReqResponse()
                prepare.send(memberInfo.address, memberInfo.port)
                        .thenAccept(this::handlePrepareReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.memberID + ": Communication failed for PREPARE_REQ to " + memberInfo.id
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
        if (member.currentlyCoorong) return;

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        // simulate node delays (includes changes due to coorong/sheoak)
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) return;
        if (activeProposal.getProposalNumber() != proposalNumber) return;

        if (member.currentlyCoorong) return;
        if (response.type.equals("PROMISE")) {
            activeProposal.addPromise(response);
            log.info(member.config.memberID + ": Received PROMISE from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority();
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            activeProposal.incrementRejectCount();
            // if node is rejecting because it has promised a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.highestPromisedProposal);
            }
            log.info(member.config.memberID + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority();
        } else {
            log.info(member.config.memberID + ": Unexpected response to PREPARE_REQ: " + response.type + " from " + response.senderID +
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
            log.info(member.config.memberID + ": Majority PROMISEs received for proposal " + activeProposal.getProposalNumber() + ". Sending ACCEPT_REQUEST");
            activeProposal.markPhaseOneCompleted();
            activeProposal.resetRejectCount(); // reset for next phase
            sendAcceptRequest();
        } else if (activeProposal.getRejectCount() >= majority) {
            log.info(member.config.memberID + ": Majority REJECTs received for proposal " + activeProposal.getProposalNumber() + " in phase one. Allowing scheduler to retry after timeout");
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
        if (member.currentlyCoorong) return;

        log.info(member.config.memberID + ": Broadcasting ACCEPT_REQUEST for proposal " + activeProposal.getProposalNumber());

        // assign value to proposal:
        int largestAcceptedProposal = -1;
        activeProposal.value = preferredLeader; // use preferred value (self, unless otherwise specified by user)
        for (Message response : activeProposal.getPromises()) {
            // if any node has previously accepted a proposal, use that previously accepted value
            if ((response.acceptedValue != null) && (response.highestPromisedProposal > largestAcceptedProposal)) {
                largestAcceptedProposal = response.highestPromisedProposal;
                activeProposal.value = response.acceptedValue;
            }
        }

        Message acceptRequest = Message.acceptRequest(activeProposal.getProposalNumber(), member.config.memberID, activeProposal.value);

        // send to all acceptors in the networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                acceptRequest.send(memberInfo.address, memberInfo.port)
                        .thenAccept(this::handleAcceptReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.memberID + ": Communication failed for ACCEPT_REQ to " + memberInfo.id
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
        if (member.currentlyCoorong) return;

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        // simulate node delays (includes changes due to coorong/sheoak)
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) return;
        if (activeProposal.getProposalNumber() != proposalNumber) return;

        if (response.type.equals("ACCEPT")) {
            activeProposal.addAccept(response);
            log.info(member.config.memberID + ": Received ACCEPT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseTwoMajority();
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            activeProposal.incrementRejectCount();
            // if node is rejecting because it has accepted a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                log.info(member.config.memberID + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                + " with higher promised value. Updating proposal ID for next round");
                proposalCounter.set(response.highestPromisedProposal);
            } else {
                log.info(member.config.memberID + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                        + " with promised ID: " + response.highestPromisedProposal);
            }
            checkPhaseTwoMajority();
        } else {
            log.info(member.config.memberID + ": Unexpected response to ACCEPT_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    /**
     * Check if ACCEPT majority has been received for activeProposal
     */
    private synchronized void checkPhaseTwoMajority() {
        if (activeProposal.isCompleted()) return;
        if (activeProposal.getAcceptCount() >= majority) {
            log.info(member.config.memberID + ": Majority ACCEPTs received for proposal " + activeProposal.getProposalNumber()
                    + ". Sending LEARN with value " + activeProposal.value);
            activeProposal.markCompleted(); // to prevent scheduler from retrying
            sendLearn(MAX_RETRIES);
            activeProposal = null;
        } else if (activeProposal.getRejectCount() >= majority) {
            log.info(member.config.memberID + ": Majority REJECTS received for proposal " + activeProposal.getProposalNumber() + " in phase two. Retrying");
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
        if (member.currentlyCoorong) return;

        // determine if REJECT is for PREPARE_REQ or ACCEPT_REQ
        int proposalNumber = response.proposalNumber;
        if (activeProposal == null) {
            log.info(member.config.memberID + ": Received incoming REJECT from " + response.senderID + " with no active proposal.");
        } else if (activeProposal.getProposalNumber() != proposalNumber) {
            log.info(member.config.memberID + ": Received incoming REJECT from " + response.senderID + " for expired proposal " + proposalNumber);
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
    private void sendLearnSingleNode(Message learn, MemberConfig.MemberInfo memberInfo, int retries) {
        if (member.currentlyCoorong) return;
        learn.send(memberInfo.address, memberInfo.port)
                .thenAccept(response -> {
                    if (response.type.equals("ACK")) {
                        log.info(member.config.memberID + ": Received ACK from " + response.senderID
                                + " for LEARN message with value " + activeProposal.value);
                    } else if (response.type.equals("NACK")) {
                        if (retries > 0) {
                            log.info(member.config.memberID + ": Received NACK from " + response.senderID
                                    + " for LEARN message with value " + activeProposal.value
                                    + ". Retrying " + retries + " more times");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            sendLearnSingleNode(learn, memberInfo, retries - 1);
                        } else {
                            log.info(member.config.memberID + ": Received too many NACKs from " + response.senderID
                                    + " for LEARN message with value " + activeProposal.value
                                    + ". Node has not learned value");
                        }
                    } else {
                        log.info(member.config.memberID + ": Received unexpected message type: " + response.type + " from "
                                + response.senderID + " for LEARN message with value " + activeProposal.value);
                    }
                })
                .exceptionally(ex -> {
                    if (retries > 0) {
                        log.info(member.config.memberID + ": No response to LEARN received from " + memberInfo.id
                                + " for proposal " + activeProposal.getProposalNumber()
                                + ". Retrying " + retries + " more times");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        sendLearnSingleNode(learn, memberInfo, retries - 1);
                    } else {
                        log.info(member.config.memberID + ": Received no response to LEARN from " + memberInfo.id
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
        Message learn = Message.learn(activeProposal.getProposalNumber(), member.config.memberID, activeProposal.value);
        // send to all learners in networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isLearner) {
                sendLearnSingleNode(learn, memberInfo, maxRetries);
            }
        }
    }

    /**
     * Listens for commands from user on STDIN
     */
    private void listenStdin() {

        System.out.println("Proposer accepting commands on STDIN. Usage:");
        System.out.println(" - `propose M<number>` to start Paxos protocol to elect indicated member");
        System.out.println(" - `propose` to start Paxos protocol to elect this node");
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
                            String value = parts[1]; // second part is the councillor to propose
                            this.preferredLeader = value.toUpperCase();
                            System.out.println("Proposing member " + value);
                        } else {
                            System.out.println("Proposing self");
                            this.preferredLeader = member.config.memberID;
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
                log.info(member.config.memberID + ": Error reading stdin: " + e.getMessage());
            }
        });
    }

    @Override
    public void shutdown() {
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        log.info(member.config.memberID + ": Proposer shutdown complete");
    }
}