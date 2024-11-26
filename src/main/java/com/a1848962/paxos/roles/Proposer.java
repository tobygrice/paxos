package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

interface ProposerRole {
    void propose();
    void propose(String target);
    void silence();
    void unsilence();
    void handlePrepareReqResponse(Message response);
    void handleAcceptReqResponse(Message response);
    void handleRejectResponse(Message response);
    void shutdown();
}

/**
 * Proposer class to make propositions and orchestrate Paxos protocol. Implements proposer role.
 */
public class Proposer implements ProposerRole {
    private final Member member;

    // proposal variables
    private final AtomicInteger proposalCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Proposal> activeProposals = new ConcurrentHashMap<>();
    private String preferredLeader;
    private final int majority;

    // network variables
    private static final int RETRY_DELAY = 5000; // time to wait before retrying a proposal
    private static final int MAX_RETRIES = 3; // how many times to retry sending a LEARN message

    // utility variables
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final SimpleLogger log = new SimpleLogger("PROPOSER");
    private volatile boolean isShutDown = false;

    public Proposer(Member member, boolean listenStdin) {
        this.member = member;
        this.preferredLeader = member.config.memberID;
        this.majority = (member.config.networkInfo.size() / 2) + 1; // calculate majority required for consensus
        if (listenStdin) listenStdin();
    }

    /**
     * Starts Paxos protocol by broadcasting a prepare request. Node will attempt to propose itself as councillor.
     */
    @Override
    public void propose() {
        sendPrepareRequest();
    }

    /**
     * Starts Paxos protocol by broadcasting a prepare request. Node will attempt to propose specified target as councillor.
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
        if (isShutDown) return;
        int currentProposalNum = proposalCounter.incrementAndGet();
        log.info(member.config.memberID + ": Broadcasting PREPARE_REQ with proposal number " + currentProposalNum);

        // create a new PREPARE_REQ message and a Proposal object to store proposal data.
        Message prepare = Message.prepareRequest(currentProposalNum, member.config.memberID);
        Proposal proposal = new Proposal(currentProposalNum);
        activeProposals.put(currentProposalNum, proposal); // add proposal to active proposals hashmap

        // send PREPARE_REQ message to all acceptors in networkInfo
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                // use sendMessage function of Network to send message to a ServerSocket. Returns a
                // CompletableFuture<Message> object which is passed to handlePrepareReqResponse()
                if (isShutDown) return;
                prepare.send(memberInfo.address, memberInfo.port)
                        .thenAccept(this::handlePrepareReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.memberID + ": Communication failed for PREPARE_REQ to " + memberInfo.id
                                    + " for proposal " + prepare.proposalNumber
                                    + ", incrementing reject count");
                            // count failure to send/receive as a rejection
                            proposal.incrementRejectCount();
                            checkPhaseOneMajority(proposal);
                            return null;
                        });
            }
        }

        // schedule proposal to timeout and retry after RETRY_DELAY
        scheduler.schedule(() -> {
            if (!proposal.isCompleted()) {
                log.info(member.config.memberID + ": Proposal " + proposal.getProposalNumber() + " timed out. Starting new proposal");
                activeProposals.remove(proposal.getProposalNumber());
                sendPrepareRequest();
            }
        }, RETRY_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles responses to a PREPARE_REQ message (PROMISE or REJECT)
     *
     * @param response       The response Message.
     */
    @Override
    public void handlePrepareReqResponse(Message response) {
        if (isShutDown) return;

        // simulate Coorong/Sheoak delays
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);
        if (proposal == null) return;

        if (response.type.equals("PROMISE")) {
            proposal.addPromise(response);
            log.info(member.config.memberID + ": Received PROMISE from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
            // if node is rejecting because it has promised a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.highestPromisedProposal);
            }
            log.info(member.config.memberID + ": Received REJECT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority(proposal);
        } else {
            log.info(member.config.memberID + ": Unexpected response to PREPARE_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    /**
     * Checks the status of a proposal to determine if a majority has been reached.
     *
     * @param proposal The Proposal object to check.
     */
    private synchronized void checkPhaseOneMajority(Proposal proposal) {
        if (proposal.isPhaseOneCompleted()) {
            // majority has already been reached and algorithm has progressed, just return
            return;
        }

        if (proposal.getPromiseCount() >= majority) {
            log.info(member.config.memberID + ": Majority PROMISEs received for proposal " + proposal.getProposalNumber() + ". Sending ACCEPT_REQUEST");
            proposal.markPhaseOneCompleted();
            proposal.resetRejectCount(); // reset for next phase
            sendAcceptRequest(proposal);
        } else if (proposal.getRejectCount() >= majority) {
            log.info(member.config.memberID + ": Majority REJECTs received for proposal " + proposal.getProposalNumber() + " in phase one. Allowing scheduler to retry after timeout");
            proposal.markPhaseOneCompleted();
            proposal.resetRejectCount();
            // allow scheduler to retry prepare phase after proposal times out, to prevent livelock
        }
    }

    private void sendAcceptRequest(Proposal proposal) {
        if (isShutDown) return;

        /*
        If a proposer receives enough PROMISEs from a majority, it needs to set a value to its proposal
        - If any acceptors had sent a value and proposal number to the proposer, then proposer sets the value of its proposal to the **value associated with the highest proposal number** reported by the acceptors
        - If none of the acceptors had accepted a proposal up to this point, then the proposer may choose any value for its proposal
        - The Proposer sends an Accept Request message to all nodes with the chosen value for its proposal.
         */
        log.info(member.config.memberID + ": Broadcasting ACCEPT_REQUEST for proposal " + proposal.getProposalNumber());

        // assign value to proposal:
        int largestAcceptedProposal = -1;
        proposal.value = preferredLeader; // use preferred value (self, unless otherwise specified by user)
        for (Message response : proposal.getPromises()) {
            // if any node has previously accepted a proposal, use that previously accepted value
            if ((response.acceptedValue != null) && (response.highestPromisedProposal > largestAcceptedProposal)) {
                largestAcceptedProposal = response.highestPromisedProposal;
                proposal.value = response.acceptedValue;
            }
        }

        Message acceptRequest = Message.acceptRequest(proposal.getProposalNumber(), member.config.memberID, proposal.value);

        // send to all acceptors in the networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                if (isShutDown) return;
                acceptRequest.send(memberInfo.address, memberInfo.port)
                        .thenAccept(this::handleAcceptReqResponse)
                        .exceptionally(ex -> {
                            log.info(member.config.memberID + ": Communication failed for ACCEPT_REQ to " + memberInfo.id
                                    + " for proposal " + proposal.getProposalNumber()
                                    + ", incrementing reject count");
                            proposal.incrementRejectCount();
                            checkPhaseTwoMajority(proposal);
                            return null;
                        });
            }
        }
    }

    @Override
    public void handleAcceptReqResponse(Message response) {
        if (isShutDown) return;

        // simulate Coorong/Sheoak delays
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);

        if (proposal == null) {
            return;
        }

        if (response.type.equals("ACCEPT")) {
            proposal.addAccept(response);
            log.info(member.config.memberID + ": Received ACCEPT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseTwoMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
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
            checkPhaseTwoMajority(proposal);
        } else {
            log.info(member.config.memberID + ": Unexpected response to ACCEPT_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    private synchronized void checkPhaseTwoMajority(Proposal proposal) {
        if (proposal.isCompleted()) {
            return;
        }
        if (proposal.getAcceptCount() >= majority) {
            log.info(member.config.memberID + ": Majority ACCEPTs received for proposal " + proposal.getProposalNumber()
                    + ". Sending LEARN with value " + proposal.value);
            proposal.markCompleted(); // to prevent scheduler from retrying
            sendLearn(proposal, MAX_RETRIES);
            activeProposals.remove(proposal.getProposalNumber());
        } else if (proposal.getRejectCount() >= majority) {
            log.info(member.config.memberID + ": Majority REJECTS received for proposal " + proposal.getProposalNumber() + " in phase two. Retrying");
            // wait for scheduler to retry
        }
    }

    @Override
    public void handleRejectResponse(Message response) {
        if (isShutDown) return;
        // determine if REJECT is for PREPARE_REQ or ACCEPT_REQ
        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);
        if (proposal == null) {
            log.info(member.config.memberID + ": Received incoming REJECT from " + response.senderID + " for expired proposal " + proposalNumber);
        } else if (!proposal.isPhaseOneCompleted()) {
            // proposal is active and phase one is incomplete, REJECT is in response to prepare request
            handlePrepareReqResponse(response);
        } else if (!proposal.isCompleted()) {
            // proposal is active and phase two is incomplete, REJECT is in response to accept request
            handleAcceptReqResponse(response);
        }
    }

    private void sendLearnSingleNode(Message learn, MemberConfig.MemberInfo memberInfo, Proposal proposal, int retries) {
        if (isShutDown) return;
        learn.send(memberInfo.address, memberInfo.port)
                .thenAccept(response -> {
                    if (response.type.equals("ACK")) {
                        log.info(member.config.memberID + ": Received ACK from " + response.senderID
                                + " for LEARN message with value " + proposal.value);
                    } else if (response.type.equals("NACK")) {
                        if (retries > 0) {
                            log.info(member.config.memberID + ": Received NACK from " + response.senderID
                                    + " for LEARN message with value " + proposal.value
                                    + ". Retrying " + retries + " more times");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            sendLearnSingleNode(learn, memberInfo, proposal, retries - 1);
                        } else {
                            log.info(member.config.memberID + ": Received too many NACKs from " + response.senderID
                                    + " for LEARN message with value " + proposal.value
                                    + ". Node has not learned value");
                        }
                    } else {
                        log.info(member.config.memberID + ": Received unexpected message type: " + response.type + " from "
                                + response.senderID + " for LEARN message with value " + proposal.value);
                    }
                })
                .exceptionally(ex -> {
                    if (retries > 0) {
                        log.info(member.config.memberID + ": No response to LEARN received from " + memberInfo.id
                                + " for proposal " + proposal.getProposalNumber()
                                + ". Retrying " + retries + " more times");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        sendLearnSingleNode(learn, memberInfo, proposal, retries - 1);
                    } else {
                        log.info(member.config.memberID + ": Received no response to LEARN from " + memberInfo.id
                                + " for proposal " + proposal.getProposalNumber()
                                + " too many times. Cannot confirm node has learned value");
                    }
                    return null;
                });
    }

    private void sendLearn(Proposal proposal, int maxRetries) {
        Message learn = Message.learn(proposal.getProposalNumber(), member.config.memberID, proposal.value);
        // send to all acceptors in networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.member.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                sendLearnSingleNode(learn, memberInfo, proposal, maxRetries);
            }
        }
    }

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
        this.isShutDown = true;
        log.info(member.config.memberID + ": Proposer shutdown complete");
    }
}