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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Proposer(MemberConfig config) {
        super(config);
        this.network = new Network(config.port, this);
        this.currentlySheoak = false;
        this.majority = (config.network.size() / 2) + 1; // calculate majority required for consensus
    }

    @Override
    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        // simulate chance for member to go camping
        if (config.chanceCoorong > random.nextDouble()) {
            // member has gone camping in the Coorong, now unreachable
            System.out.println(memberID + " is camping in the Coorong. They are unreachable.");
            logger.info("{} at Coorong", memberID);
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
            logger.info("{} at Sheoak cafe", memberID);
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
                    logger.info("{}: Received incoming reject message for unknown proposal number {}", memberID, proposalNumber);
                } else {
                    if (!proposal.phaseOneCompleted) {
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
            case "LEARN":
                // message is for learners
                if (this.config.isLearner) super.handleIncomingMessage(message, socketOut);
                break;
            default:
                logger.warn("{}: Proposer node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void sendPrepare() {
        int currentProposalNum = proposalCounter.incrementAndGet();
        logger.info("{}: Broadcasting PREPARE_REQ with proposal number {}", memberID, currentProposalNum);

        Message prepare = Message.prepareRequest(currentProposalNum, memberID);
        Proposal proposal = new Proposal(currentProposalNum);
        activeProposals.put(currentProposalNum, proposal);

        // send to all acceptors in network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, prepare)
                        .thenAccept(this::handlePrepareResponse)
                        .exceptionally(ex -> {
                            logger.error("{}: Failed to send PREPARE_REQ to {} - {}",
                                    memberID, memberInfo.id, ex.getMessage());
                            proposal.incrementRejectCount();
                            checkPhaseOneMajority(proposal);
                            return null;
                        });
            }
        }

        // schedule proposal to timeout and retry after RETRY_DELAY
        scheduler.schedule(() -> {
            if (!proposal.isCompleted()) {
                logger.warn("{}: Proposal {} timed out. Starting new proposal.",
                        memberID, currentProposalNum);
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
            logger.info("{}: Received {} response for unknown proposal number {}", memberID, response.type, proposalNumber);
            return;
        }

        if (response.type.equals("PROMISE")) {
            proposal.addPromise(response);
            logger.info("{}: Received PROMISE from {} for proposal {}",
                    memberID, response.senderID, proposalNumber);
            checkPhaseOneMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
            // if node is rejecting because it has accepted a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.previouslyAcceptedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.previouslyAcceptedProposal);
            }
            logger.info("{}: Received REJECT from {} for proposal {}",
                    memberID, response.senderID, proposalNumber);
            checkPhaseOneMajority(proposal);
        } else {
            logger.warn("{}: Unexpected response type '{}' from {} for proposal {}",
                    memberID, response.type, response.senderID,  proposalNumber);
        }
    }

    /**
     * Checks the status of a proposal to determine if a majority has been reached.
     *
     * @param proposal The Proposal object to check.
     */
    private void checkPhaseOneMajority(Proposal proposal) {
        if (proposal.phaseOneCompleted) {
            // majority has already been reached and algorithm has progressed, just return
            return;
        }

        if (proposal.getPromiseCount() >= majority) {
            logger.info("{}: Majority PROMISEs received for proposal {}. Sending ACCEPT_REQUEST.",
                    memberID, proposal.getProposalNumber());
            proposal.phaseOneCompleted = true;
            proposal.resetRejectCount(); // reset for next phase
            sendAcceptRequest(proposal);
        } else if (proposal.getRejectCount() >= majority) {
            logger.warn("{}: Majority REJECTs received for proposal {}. Abandoning proposal.",
                    memberID, proposal.getProposalNumber());
            proposal.phaseOneCompleted = true;
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
        proposal.value = memberID; // vote for self by default
        for (Message response : proposal.getPromises()) {
            // if any node has previously accepted a proposal, use that previously accepted value
            if ((response.previouslyAcceptedValue != null) && (response.previouslyAcceptedProposal > largestAcceptedProposal)) {
                largestAcceptedProposal = response.previouslyAcceptedProposal;
                proposal.value = response.previouslyAcceptedValue;
            }
        }

        Message acceptRequest = Message.acceptRequest(proposal.getProposalNumber(), proposal.value, memberID);

        // send to all acceptors in the network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, acceptRequest)
                        .thenAccept(this::handleAcceptReqResponse)
                        .exceptionally(ex -> {
                            logger.error("{}: Failed to send ACCEPT_REQ to {} - {}",
                                    memberID, memberInfo.id, ex.getMessage());
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
            logger.info("{}: Received {} response for unknown proposal number {}", memberID, response.type, proposalNumber);
            return;
        }

        if (response.type.equals("ACCEPT")) {
            proposal.addAccept(response);
            logger.info("{}: Received ACCEPT from {} for proposal {}",
                    memberID, response.senderID, proposalNumber);
            checkPhaseTwoMajority(proposal);
        } else if (response.type.equalsIgnoreCase("REJECT")) {
            proposal.incrementRejectCount();
            // if node is rejecting because it has accepted a proposal with a greater ID, update proposal counter to
            // match to ensure next prepare message will have a current ID:
            if (response.previouslyAcceptedProposal > this.proposalCounter.get()) {
                proposalCounter.set(response.previouslyAcceptedProposal);
            }
            logger.info("{}: Received REJECT in phase two from {} for proposal {}",
                    memberID, response.senderID, proposalNumber);
            checkPhaseTwoMajority(proposal);
        } else {
            logger.warn("{}: Unexpected response type '{}' from {} for proposal {}",
                    memberID, response.type, response.senderID,  proposalNumber);
        }
    }

    private void checkPhaseTwoMajority(Proposal proposal) {
        if (proposal.isCompleted()) {
            return;
        }

        if (proposal.getAcceptCount() >= majority) {
            logger.info("{}: Majority of nodes accepted proposal {}. Sending LEARN.",
                    memberID, proposal.getProposalNumber());
            sendLearn(proposal);
            proposal.markCompleted(); // to prevent scheduler from retrying
            activeProposals.remove(proposal.getProposalNumber());
        } else if (proposal.getRejectCount() >= majority) {
            logger.warn("{}: Received too many REJECTs for proposal {} in phase two. Retrying.",
                    memberID, proposal.getProposalNumber());
            proposal.markCompleted(); // to prevent scheduler from retrying
            activeProposals.remove(proposal.getProposalNumber());
            sendPrepare();
        }
    }

    private void sendLearn(Proposal proposal) {
        Message learn = Message.learn(proposal.getProposalNumber(), proposal.value, memberID);
        // send to all acceptors in network:
        for (MemberConfig.MemberInfo memberInfo : this.config.network.values()) {
            if (memberInfo.isAcceptor) {
                network.sendMessage(memberInfo.address, memberInfo.port, learn)
                        .thenAccept(response -> {
                            if (response.type.equals("ACK")) {
                                logger.debug("{}: Member {} ACKed LEARN message", memberID, memberInfo.id);
                            } else if (response.type.equals("NACK")) {
                                logger.warn("{}: Member {} NACKed LEARN message", memberID, memberInfo.id);
                            } else {
                                logger.error("{}: Member {} sent unexpected message type {} in response to LEARN",
                                        memberID, memberInfo.id, response.type);
                            }
                        })
                        .exceptionally(ex -> {
                            logger.error("{}: Failed to send LEARN to {} - {}",
                                    memberID, memberInfo.id, ex.getMessage());
                            return null;
                        });
            }
        }
    }


    @Override
    public void start() {
        // start listening for commands on stdin in a separate thread
        executor.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String command = line.trim().toLowerCase();
                    if (command.equals("propose")) {
                        // send proposal to all nodes in network
                        sendPrepare();
                    } else if (command.equals("exit")) {
                        logger.info("{}: 'exit' command received. Shutting down.", memberID);
                        shutdown();
                    } else {
                        System.out.println("Unknown command: " + command);
                        System.out.println("Usage:");
                        System.out.println(" - `propose` to broadcast a prepare message");
                        System.out.println(" - `exit` to shut down node");
                    }
                }
            } catch (IOException e) {
                logger.error("{}: Error reading stdin: {}", memberID, e.getMessage());
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        logger.info("{}: Proposer shutdown complete.", memberID);
        System.exit(0);
    }
}
