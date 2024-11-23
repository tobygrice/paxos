package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

// all acceptors are learners
public class Acceptor extends Learner {
    // thread-safe data types to store the highest promised proposal, highest accepted proposal and its associated value
    private final AtomicInteger highestPromise = new AtomicInteger();
    StringBuffer acceptedValue = new StringBuffer();

    public Acceptor(MemberConfig config) {
        super(config);
        this.network = new Network(config.port, this);
    }

    @Override
    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        System.out.println("ACCEPTOR: Incoming " + message.type + " message from " + message.senderID);
        switch (message.type) {
            case "PROMISE":
            case "ACCEPT":
            case "REJECT":
                // message for proposer node, send NACK
                System.out.println("Acceptor node received incompatible " + message.type +
                        " message from " + message.senderID + ". Sending NACK.");
                sendNack(socketOut);
                break;
            case "PREPARE_REQ":
                handlePrepareRequest(message, socketOut);
                break;
            case "ACCEPT_REQ":
                handleAcceptRequest(message, socketOut);
                break;
            case "LEARN":
                super.handleIncomingMessage(message, socketOut);
                break;
            default:
                System.out.println("Received incompatible message type: " + message.type);
        }
    }

    private void handlePrepareRequest(Message message, OutputStream socketOut) {
        /* If n is greater than any previous proposal number seen by the acceptor:
            - Acceptor returns a promise to ignore all future proposals with a number < n
            - If the acceptor accepted a proposal at some point in the past, it must include the previous proposal number + value in its response to the proposer
            - send **_prepare-ok_**
           Otherwise, ignore
         */
        System.out.println("Handling PREPARE request from " + message.senderID);

        Message response;
        if (highestPromise.get() < message.proposalNumber) {
            // acceptor has not promised to ignore, respond with promise
            this.highestPromise.set(message.proposalNumber);
            if (acceptedValue.length() > 0) {
                // acceptor has accepted a value in the past, include in response
                response = Message.promise(message.proposalNumber, memberID, highestPromise.get(), acceptedValue.toString());
                System.out.println("Sending PROMISE for proposal " + message.proposalNumber
                        + " with previously accepted value " + acceptedValue + " from proposal " + highestPromise.get());
            } else {
                response = Message.promise(message.proposalNumber, memberID);
                System.out.println("Sending PROMISE for proposal " + message.proposalNumber + " with no previously accepted value");
            }
        } else if (this.acceptedValue.length() > 0) {
            // else acceptor has promised to ignore this proposalID, and has accepted a value in the past.
            // reply with reject message containing accepted ID/value
            System.out.println("Rejecting PREPARE_REQ from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + this.highestPromise.get()
                    + ". Including previously accepted value " + this.acceptedValue);
            response = Message.reject(message.proposalNumber, memberID, highestPromise.get(), acceptedValue.toString());
        } else {
            // reject with the highest accepted promise
            System.out.println("Rejecting PREPARE_REQ from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + this.highestPromise.get()
                    + ". No previously accepted value to include.");
            response = Message.reject(message.proposalNumber, memberID, highestPromise.get());
        }

        // send response
        try {
            socketOut.write(response.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            System.out.println("Error writing response to PREPARE_REQ: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private void handleAcceptRequest(Message message, OutputStream socketOut) {
        /*
        If an acceptor receives an Accept Request message for a proposal n, it must accept (send ***accept-ok***)
        - **if and only if** it has not already promised to only consider proposals having an identifier greater than n -> also implies acceptor considers proposer LEADER.
        - If it has, respond with **accept-reject**
         */

        Message response;
        if (highestPromise.get() < message.proposalNumber) {
            // acceptor has not promised to ignore, respond with accept
            // overwrite highest promised ID and accepted value:
            this.highestPromise.set(message.proposalNumber);
            this.acceptedValue.setLength(0);
            this.acceptedValue.append(message.value);
            response = Message.accept(message.proposalNumber, memberID, message.value);
            System.out.println("Sending ACCEPT for proposal " + message.proposalNumber);
        } else if (this.acceptedValue.length() > 0) {
            // else acceptor has promised to ignore this proposalID, and has accepted a value in the past.
            // reply with reject message containing accepted ID/value
            response = Message.reject(message.proposalNumber, memberID, highestPromise.get(), acceptedValue.toString());
            System.out.println("Rejecting ACCEPT_REQ from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + this.highestPromise.get()
                    + ". Including previously accepted value " + this.acceptedValue);
        } else {
            // reject with the highest accepted promise
            response = Message.reject(message.proposalNumber, memberID, highestPromise.get());
            System.out.println("Rejecting ACCEPT_REQ from " + message.senderID
                    + " for proposal " + message.proposalNumber
                    + " due to already promising proposal " + this.highestPromise.get()
                    + ". No previously accepted value to include.");
        }

        // send response
        try {
            socketOut.write(response.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            System.out.println("Error writing response to ACCEPT_REQ: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void start() {
        if (this.config.isLearner) {
            super.start();
        }

        System.out.println("Performing acceptor role");
    }
}
