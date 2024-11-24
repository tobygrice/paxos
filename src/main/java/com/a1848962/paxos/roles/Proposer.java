package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

interface ProposerRole {
    void propose();
    void propose(String target);
    void listenStdin();
    void sendPrepareRequest();
    void handlePrepareReqResponse(Message response);
    void sendAcceptRequest(Proposal proposal);
    void handleAcceptReqResponse(Message response);
    void handleRejectResponse(Message response);
    void sendLearn(Proposal proposal);
}

public class Proposer extends Member implements ProposerRole {
    private static final int RETRY_DELAY = 2000; // retry proposal after 2 seconds

    private final AtomicInteger proposalCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Proposal> activeProposals = new ConcurrentHashMap<>();
    private final int majority;

    private String preferredLeader;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Proposer(MemberConfig config) {
        super(config);
        this.preferredLeader = config.memberID;
        this.majority = (config.networkInfo.size() / 2) + 1; // calculate majority required for consensus
    }

    public void propose() {
        sendPrepareRequest();
    }

    public void propose(String target) {
        this.preferredLeader = target;
        sendPrepareRequest();
    }

    @Override
    public void sendPrepareRequest() {

        int currentProposalNum = proposalCounter.incrementAndGet();
        System.out.println("Broadcasting PREPARE_REQ with proposal number " + currentProposalNum);

        Message prepare = Message.prepareRequest(currentProposalNum, config.memberID);
        Proposal proposal = new Proposal(currentProposalNum);
        activeProposals.put(currentProposalNum, proposal);

        // send to all acceptors in networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, prepare)
                        .thenAccept(this::handlePrepareReqResponse)
                        .exceptionally(ex -> {
                            if (ex.getCause().getCause() instanceof java.net.SocketTimeoutException) {
                                System.out.println("No response to PREPARE_REQ received from " + memberInfo.id
                                        + " (timed out) for proposal " + proposal.getProposalNumber()
                                        + ", incrementing reject count.");
                            } else {
                                System.out.println("Failed to send PREPARE_REQ to " + memberInfo.id
                                        + " for proposal " + proposal.getProposalNumber()
                                        + ", incrementing reject count - " + ex.getMessage());
                            }
                            proposal.incrementRejectCount();
                            checkPhaseOneMajority(proposal);
                            return null;
                        });
            }
        }


        // schedule proposal to timeout and retry after RETRY_DELAY
        scheduler.schedule(() -> {
            if (!proposal.isCompleted()) {
                System.out.println("Proposal " + proposal.getProposalNumber() + " timed out. Starting new proposal.");
                activeProposals.remove(proposal.getProposalNumber());
                sendPrepareRequest();
            }
        }, RETRY_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles incoming responses for PREPARE messages.
     *
     * @param response       The response Message.
     */
    @Override
    public void handlePrepareReqResponse(Message response) {
        simulateNodeDelay();

        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);

        if (proposal == null) {
            /* System.out.println("Handle PREPARE_REQ method received " + response.type + " response from "
                    + response.senderID + " for unknown proposal number " + proposalNumber); */
        } else if (response.type.equals("PROMISE")) {
            proposal.addPromise(response);
            System.out.println("Received PROMISE from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
            // if node is rejecting because it has promised a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.highestPromisedProposal);
            }
            System.out.println("Received REJECT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseOneMajority(proposal);
        } else {
            System.out.println("Unexpected response to PREPARE_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    /**
     * Checks the status of a proposal to determine if a majority has been reached.
     *
     * @param proposal The Proposal object to check.
     */
    private void checkPhaseOneMajority(Proposal proposal) {
        if (proposal.isPhaseOneCompleted()) {
            // majority has already been reached and algorithm has progressed, just return
            return;
        }

        if (proposal.getPromiseCount() >= majority) {
            System.out.println("Majority PROMISEs received for proposal " + proposal.getProposalNumber() + ". Sending ACCEPT_REQUEST.");
            proposal.markPhaseOneCompleted();
            proposal.resetRejectCount(); // reset for next phase
            sendAcceptRequest(proposal);
        } else if (proposal.getRejectCount() >= majority) {
            System.out.println("Majority REJECTs received for proposal " + proposal.getProposalNumber() + " in phase one. Allowing scheduler to retry after timeout.");
            proposal.markPhaseOneCompleted();
            // allow scheduler to retry prepare phase after proposal times out, to prevent livelock
        }
    }

    @Override
    public void sendAcceptRequest(Proposal proposal) {
        /*
        If a proposer receives enough PROMISEs from a majority, it needs to set a value to its proposal
        - If any acceptors had sent a value and proposal number to the proposer, then proposer sets the value of its proposal to the **value associated with the highest proposal number** reported by the acceptors
        - If none of the acceptors had accepted a proposal up to this point, then the proposer may choose any value for its proposal
        - The Proposer sends an Accept Request message to all nodes with the chosen value for its proposal.
         */

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

        Message acceptRequest = Message.acceptRequest(proposal.getProposalNumber(), config.memberID, proposal.value);

        // send to all acceptors in the networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, acceptRequest)
                        .thenAccept(this::handleAcceptReqResponse)
                        .exceptionally(ex -> {
                            if (ex.getCause() instanceof java.net.SocketTimeoutException) {
                                System.out.println("No response to ACCEPT_REQ received from " + memberInfo.id
                                        + " (timed out) for proposal " + proposal.getProposalNumber()
                                        + ", incrementing reject count.");
                            } else {
                                System.out.println("Failed to send ACCEPT_REQ to " + memberInfo.id
                                        + " for proposal " + proposal.getProposalNumber()
                                        + ", incrementing reject count.");
                            }
                            System.out.println("Failed to send ACCEPT_REQ to " + memberInfo.id
                                    + " for proposal " + proposal.getProposalNumber() + ". Incrementing reject count. "
                                    + ex.getMessage());
                            proposal.incrementRejectCount();
                            checkPhaseTwoMajority(proposal);
                            return null;
                        });
            }
        }
    }

    @Override
    public void handleAcceptReqResponse(Message response) {
        simulateNodeDelay();

        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);

        if (proposal == null) {
            /* System.out.println("Handle ACCEPT_REQ method received " + response.type + " response from "
                    + response.senderID + " for unknown proposal number " + proposalNumber); */
        } else if (response.type.equals("ACCEPT")) {
            proposal.addAccept(response);
            System.out.println("Received ACCEPT from " + response.senderID + " for proposal " + proposalNumber);
            checkPhaseTwoMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
            // if node is rejecting because it has accepted a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.highestPromisedProposal > this.proposalCounter.get()) {
                System.out.println("Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                + " with higher promised value. Updating proposal ID for next round.");
                proposalCounter.set(response.highestPromisedProposal);
            } else {
                System.out.println("Received REJECT from " + response.senderID + " for proposal " + proposalNumber
                        + " with promised ID: " + response.highestPromisedProposal);
            }
            checkPhaseTwoMajority(proposal);
        } else {
            System.out.println("Unexpected response to ACCEPT_REQ: " + response.type + " from " + response.senderID +
                    " for proposal " + proposalNumber);
        }
    }

    private void checkPhaseTwoMajority(Proposal proposal) {
        if (proposal.isCompleted()) {
            return;
        }
        if (proposal.getAcceptCount() >= majority) {
            System.out.println("Majority ACCEPTs received for proposal " + proposal.getProposalNumber()
                    + ". Sending LEARN with value " + proposal.value);
            proposal.markCompleted(); // to prevent scheduler from retrying
            sendLearn(proposal);
            activeProposals.remove(proposal.getProposalNumber());
        } else if (proposal.getRejectCount() >= majority) {
            System.out.println("Majority REJECTS received for proposal " + proposal.getProposalNumber() + " in phase two. Retrying.");
            // wait for scheduler to retry
        }
    }

    @Override
    public void handleRejectResponse(Message response) {
        // determine if REJECT is for PREPARE_REQ or ACCEPT_REQ
        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);
        if (proposal == null) {
            System.out.println("Received incoming REJECT from " + response.senderID + " for expired proposal " + proposalNumber);
        } else if (!proposal.isPhaseOneCompleted()) {
            // proposal is active and phase one is incomplete, REJECT is in response to prepare request
            handlePrepareReqResponse(response);
        } else if (!proposal.isCompleted()) {
            // proposal is active and phase two is incomplete, REJECT is in response to accept request
            handleAcceptReqResponse(response);
        }
    }

    @Override
    public void sendLearn(Proposal proposal) {
        Message learn = Message.learn(proposal.getProposalNumber(), config.memberID, proposal.value);
        // send to all acceptors in networkInfo:
        for (MemberConfig.MemberInfo memberInfo : this.config.networkInfo.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, learn)
                        .thenAccept(response -> {
                            if (response.type.equals("ACK")) {
                                System.out.println("Received ACK from " + response.senderID
                                        + " for LEARN message with value " + proposal.value);
                            } else if (response.type.equals("NACK")) {
                                System.out.println("Received NACK from " + response.senderID
                                        + " for LEARN message with value " + proposal.value);
                            } else {
                                System.out.println("Received unexpected message type: " + response.type + " from "
                                        + response.senderID + " for LEARN message with value " + proposal.value);
                            }
                        })
                        .exceptionally(ex -> {
                            if (ex.getCause() instanceof java.net.SocketTimeoutException) {
                                System.out.println("No response to LEARN received from " + memberInfo.id
                                        + " (timed out) for proposal " + proposal.getProposalNumber());
                            } else {
                                System.out.println("Failed to send LEARN to " + memberInfo.id
                                        + " for proposal " + proposal.getProposalNumber());
                            }
                            return null;
                        });
            }
        }
    }

    @Override
    public void listenStdin() {

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
                            this.preferredLeader = config.memberID;
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
                System.out.println("Error reading stdin: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        System.out.println("Shutdown complete.");
        System.exit(0);
    }
}
