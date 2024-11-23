package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// all proposers are acceptors
public class Proposer extends Acceptor {
    // define how long a proposer will stay at Sheoak cafe or the Coorong (in milliseconds)
    private static final int TIME_IN_SHEOAK = 2000; // 2 seconds at each place
    private static final int TIME_IN_COORONG = 2000;
    private static final int RETRY_DELAY = 2000; // retry proposal after 2 seconds

    private boolean currentlySheoak;
    private final AtomicInteger proposalCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Proposal> activeProposals = new ConcurrentHashMap<>();
    private final int majority;

    private String preferredLeader;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Proposer(MemberConfig config) {
        super(config);
        this.preferredLeader = memberID;
        this.currentlySheoak = false;
        this.majority = (config.network.size() / 2) + 1; // calculate majority required for consensus
    }

    @Override
    public void handleIncomingMessage(Message message, OutputStream socketOut) {

        // simulate chance for member to go camping
        if (config.chanceCoorong > random.nextDouble()) {
            // member has gone camping in the Coorong, now unreachable
            System.out.println(memberID + " is camping in the Coorong. They are unreachable.");
            try {
                Thread.sleep(TIME_IN_COORONG); // unreachable whilst camping
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // simulate chance for member to go to Sheoak cafe
        if (!currentlySheoak && config.chanceSheoak > random.nextDouble()) {
            // member has gone to Sheoak cafe, responses now instant
            System.out.println(memberID + " is at Sheoak Café. Responses are instant.");
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
                handlePrepareResponse(message);
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
                        handlePrepareResponse(message);
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

    private void sendPrepare() {
        int currentProposalNum = proposalCounter.incrementAndGet();
        System.out.println("Broadcasting PREPARE_REQ with proposal number " + currentProposalNum);

        Message prepare = Message.prepareRequest(currentProposalNum, memberID);
        Proposal proposal = new Proposal(currentProposalNum);
        activeProposals.put(currentProposalNum, proposal);

        // send to all acceptors in network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, prepare)
                        .thenAccept(this::handlePrepareResponse)
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
                sendPrepare();
            }
        }, RETRY_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles incoming responses for PREPARE messages.
     *
     * @param response       The response Message.
     */
    private void handlePrepareResponse(Message response) {
        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);

        if (proposal == null) {
            System.out.println("Handle PREPARE_REQ method received " + response.type + " response from "
                    + response.senderID + " for unknown proposal number " + proposalNumber);
            return;
        }

        if (response.type.equals("PROMISE")) {
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


    private void sendAcceptRequest(Proposal proposal) {
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

        Message acceptRequest = Message.acceptRequest(proposal.getProposalNumber(), memberID, proposal.value);

        // send to all acceptors in the network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
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

    private void handleAcceptReqResponse(Message response) {
        int proposalNumber = response.proposalNumber;
        Proposal proposal = activeProposals.get(proposalNumber);

        if (proposal == null) {
            System.out.println("Handle ACCEPT_REQ method received " + response.type + " response from "
                    + response.senderID + " for unknown proposal number " + proposalNumber);
            return;
        }

        if (response.type.equals("ACCEPT")) {
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

    private void sendLearn(Proposal proposal) {
        Message learn = Message.learn(proposal.getProposalNumber(), memberID, proposal.value);
        // send to all acceptors in network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
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
    public void start() {
        if (this.config.isAcceptor) {
            super.start();
        }

        System.out.println("Performing proposer role");

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
                            this.preferredLeader = memberID;
                        }
                        // send proposal to all nodes in network:
                        sendPrepare();
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

    @Override
    public void shutdown() {
        super.shutdown();
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        System.out.println("Shutdown complete.");
        System.exit(0);
    }
}
