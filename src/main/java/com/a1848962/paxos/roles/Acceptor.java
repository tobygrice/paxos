package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.SimpleLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class Acceptor implements Member.AcceptorRole {
    private final Member member; // reference to parent member object

    // thread-safe data types to store the highest promised proposal, highest accepted proposal and its associated value
    private final AtomicInteger highestPromise = new AtomicInteger();
    private final StringBuilder highestPromiseProposerID = new StringBuilder("M0");
    private final StringBuilder acceptedValue = new StringBuilder();

    // utility variables
    private final Object promiseLock = new Object(); // lock to ensure atomicity
    private static final SimpleLogger log = new SimpleLogger("ACCEPTOR");

    public Acceptor(Member member) {
        this.member = member;
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
     * Handles an incoming PREPARE_REQ type message. If incoming proposal number is greater than any previous proposal
     * number seen by the acceptor:
     *  - Acceptor responds with a PROMISE to ignore all future proposals with a number < n
     *  - If the acceptor accepted a proposal at some point in the past, it must include the previous proposal number
     *    and value in its response to the proposer
     * Otherwise, send REJECT.
     *
     * @param message       incoming PREPARE_REQ type message
     * @param socketOut     socket for response
     */
    @Override
    public void handlePrepareRequest(Message message, OutputStream socketOut) {

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        // simulate node delays (includes changes due to coorong/sheoak)
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling PREPARE request from " + message.senderID);

        Message response; // declare response message

        // parse incoming senderID and current promised ID to integers for comparison
        int incomingProposerID = Integer.parseInt(message.senderID.substring(1));
        int currentPromisedProposerID = Integer.parseInt(highestPromiseProposerID.substring(1));

        synchronized (promiseLock) { // ensure atomicity between highestPromise and highestPromiseProposerID
            if (highestPromise.get() < message.proposalNumber) {
                // new highest proposalID, send promise
                int previousHighestPromise = highestPromise.getAndSet(message.proposalNumber);
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);

                response = createPromiseMessage(message.proposalNumber, previousHighestPromise);
            } else if (highestPromise.get() == message.proposalNumber && incomingProposerID < currentPromisedProposerID) {
                // same proposalID, but incoming proposer has a lower memberID (higher priority)
                // send promise:
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);

                response = createPromiseMessage(message.proposalNumber, highestPromise.get());
            } else {
                // criteria for a promise response not met
                response = createRejectMessage(message);
            }

            sendResponse(response, socketOut);
        }
    }


    /**
     * Handles an incoming ACCEPT_REQ type message. Sends ACCEPT iff acceptor has not already sent a PROMISE to a
     * greater proposal number. If it has, responds with REJECT
     *
     * @param message       incoming ACCEPT_REQ type message
     * @param socketOut     socket for response
     */
    @Override
    public void handleAcceptRequest(Message message, OutputStream socketOut) {

        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        // simulate node delays (includes changes due to coorong/sheoak)
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling ACCEPT request from " + message.senderID);

        Message response;

        // parse incoming senderID and current promised ID to integers for comparison
        int incomingProposerID = Integer.parseInt(message.senderID.substring(1));
        int currentPromisedProposerID = Integer.parseInt(highestPromiseProposerID.substring(1));

        synchronized (promiseLock) { // ensure atomicity between highestPromise and highestPromiseProposerID
            if (highestPromise.get() <= message.proposalNumber) {
                // new highest proposalNumber
                // send accept and update highest promise:
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);
                acceptedValue.setLength(0);
                acceptedValue.append(message.value);

                response = Message.accept(message.proposalNumber, member.config.memberID, message.value);
                log.info(member.config.memberID + ": Sending ACCEPT for proposal " + message.proposalNumber);
            } else if (highestPromise.get() == message.proposalNumber && incomingProposerID < currentPromisedProposerID) {
                // same proposalID and is the original proposer, and incoming proposer has a lower memberID (higher priority)
                // send accept:
                highestPromiseProposerID.setLength(0);
                highestPromiseProposerID.append(message.senderID);
                acceptedValue.setLength(0);
                acceptedValue.append(message.value);

                response = Message.accept(message.proposalNumber, member.config.memberID, message.value);
                log.info(member.config.memberID + ": Sending ACCEPT for proposal " + message.proposalNumber + " from higher priority proposer " + message.senderID);
            } else {
                // criteria for an accept response not met
                // send reject:
                response = createRejectMessage(message);
            }

            sendResponse(response, socketOut);
        }
    }

    /**
     * Creates a promise type message containing any previously accepted value.
     *
     * @param proposalNumber            proposal number of proposal being promised
     * @param previousHighestPromise    previous highest promised proposal number
     * @return                          a PROMISE type Message
     */
    private Message createPromiseMessage(int proposalNumber, int previousHighestPromise) {
        if (acceptedValue.length() > 0) {
            log.info(member.config.memberID + ": Sending PROMISE for proposal " + proposalNumber
                    + " with previously accepted value '" + acceptedValue + "' from proposal " + previousHighestPromise);
            return Message.promise(proposalNumber, member.config.memberID, previousHighestPromise, acceptedValue.toString());
        } else {
            log.info(member.config.memberID + ": Sending PROMISE for proposal " + proposalNumber + " with no previously accepted value");
            return Message.promise(proposalNumber, member.config.memberID);
        }
    }

    /**
     * Creates a reject type message any previously accepted values.
     *
     * @param message       the message being rejected
     * @return              a REJECT type message
     */
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

    /**
     * Sends response to socket out
     *
     * @param response      the message to send
     * @param socketOut     the socket to send the message to
     */
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
