package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;
import com.a1848962.paxos.utils.SimpleLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

interface AcceptorRole {
    void handlePrepareRequest(Message message, OutputStream socketOut);
    void handleAcceptRequest(Message message, OutputStream socketOut);
}

public class Acceptor implements AcceptorRole {
    private final Member member;

    // thread-safe data types to store the highest promised proposal, highest accepted proposal and its associated value
    private final AtomicInteger highestPromise = new AtomicInteger();
    private final StringBuilder highestPromiseProposerID = new StringBuilder("M0"); // Initialized to "M0" as default
    private final StringBuilder acceptedValue = new StringBuilder();

    private final Object promiseLock = new Object(); // lock to ensure atomicity
    private static final SimpleLogger log = new SimpleLogger("ACCEPTOR");

    public Acceptor(Member member) {
        this.member = member;
    }

    @Override
    public void handlePrepareRequest(Message message, OutputStream socketOut) {
        /* If n is greater than any previous proposal number seen by the acceptor:
            - Acceptor returns a promise to ignore all future proposals with a number < n
            - If the acceptor accepted a proposal at some point in the past, it must include the previous proposal number + value in its response to the proposer
            - send **_prepare-ok_**
           Otherwise, ignore
         */

        // simulate Coorong/Sheoak delays
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling PREPARE request from " + message.senderID);

        Message response = null; // initialise response message
        // parse incoming senderID and current promised ID to integer for comparison
        int incomingProposerID = Integer.parseInt(message.senderID.substring(1));
        int currentPromisedProposerID = Integer.parseInt(highestPromiseProposerID.substring(1));

        synchronized (promiseLock) { // Ensure atomicity between highestPromise and highestPromiseProposerID
            if (highestPromise.get() < message.proposalNumber) {
                // new highest proposalID, send promise
                int previousHighestPromise = highestPromise.getAndSet(message.proposalNumber);
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);

                response = createPromiseMessage(message, previousHighestPromise);
            } else if (highestPromise.get() == message.proposalNumber && incomingProposerID < currentPromisedProposerID) {
                // same proposalID, but incoming proposer has a lower memberID (higher priority)
                // send promise:
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);

                response = createPromiseMessage(message, highestPromise.get());
            }

            if (response == null) {
                // Either proposalNumber < highestPromise or equal but higher memberID
                response = createRejectMessage(message);
            }

            sendResponse(response, socketOut);
        }
    }

    @Override
    public void handleAcceptRequest(Message message, OutputStream socketOut) {
        /*
        If an acceptor receives an Accept Request message for a proposal n, it must accept (send ***accept-ok***)
        - **if and only if** it has not already promised to only consider proposals having an identifier greater than n -> also implies acceptor considers proposer LEADER.
        - If it has, respond with **accept-reject**
         */

        // simulate Coorong/Sheoak delays
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling ACCEPT request from " + message.senderID);

        Message response = null;
        int incomingProposerID = Integer.parseInt(message.senderID.substring(1));
        int currentPromisedProposerID = Integer.parseInt(highestPromiseProposerID.substring(1));

        synchronized (promiseLock) { // ensure atomicity between highestPromise and highestPromiseProposerID
            if (highestPromise.get() <= message.proposalNumber) {
                // new highest proposalNumber
                // send accept and update highest promise:
                int previousHighestPromise = highestPromise.getAndSet(message.proposalNumber);
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);
                acceptedValue.setLength(0);
                acceptedValue.append(message.value);

                response = Message.accept(message.proposalNumber, member.config.memberID, message.value);
                log.info(member.config.memberID + ": Sending ACCEPT for proposal " + message.proposalNumber);
            } else if (highestPromise.get() == message.proposalNumber && incomingProposerID < currentPromisedProposerID) {
                // same proposalID and is the original proposer, or incoming proposer has a lower memberID (higher priority)
                // send accept:
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);
                acceptedValue.setLength(0);
                acceptedValue.append(message.value);

                response = Message.accept(message.proposalNumber, member.config.memberID, message.value);
                log.info(member.config.memberID + ": Sending ACCEPT for proposal " + message.proposalNumber + " from higher priority proposer " + message.senderID);
            } else {
                // either proposalNumber < highestPromise or same proposalNumber but higher proposerID
                // send reject:
                response = createRejectMessage(message);
            }

            sendResponse(response, socketOut);
        }
    }

    private Message createPromiseMessage(Message message, int previousHighestPromise) {
        if (acceptedValue.length() > 0) {
            log.info(member.config.memberID + ": Sending PROMISE for proposal " + message.proposalNumber
                    + " with previously accepted value '" + acceptedValue + "' from proposal " + previousHighestPromise);
            return Message.promise(message.proposalNumber, member.config.memberID, previousHighestPromise, acceptedValue.toString());
        } else {
            log.info(member.config.memberID + ": Sending PROMISE for proposal " + message.proposalNumber + " with no previously accepted value");
            return Message.promise(message.proposalNumber, member.config.memberID);
        }
    }

    private Message createRejectMessage(Message message) {
        if (acceptedValue.length() > 0) {
            log.info(member.config.memberID + ": Rejecting " + message.type + " from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + highestPromise.get()
                    + ". Including previously accepted value '" + acceptedValue + "'");
            return Message.reject(message.proposalNumber, member.config.memberID, highestPromise.get(), acceptedValue.toString());
        } else {
            log.info(member.config.memberID + ": Rejecting " + message.type + " from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + highestPromise.get()
                    + ". No previously accepted value to include");
            return Message.reject(message.proposalNumber, member.config.memberID, highestPromise.get());
        }
    }

    private void sendResponse(Message response, OutputStream socketOut) {
        try {
            socketOut.write(response.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.info(member.config.memberID + ": Error writing response: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
