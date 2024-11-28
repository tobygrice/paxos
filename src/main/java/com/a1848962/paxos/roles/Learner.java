package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.SimpleLogger;

import java.io.IOException;
import java.io.OutputStream;

public class Learner implements Member.LearnerRole {
    private final Member member; // reference to parent member object

    private final StringBuffer learnedValue;

    private static final SimpleLogger log = new SimpleLogger("LEARNER");

    public Learner(Member member) {
        this.member = member;
        learnedValue = new StringBuffer();
    }

    public String getLearnedValue() {
        if (learnedValue.length() > 0) return learnedValue.toString();
        else return null; // return null if nothing has been learned, rather than an empty string
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
     * Handles incoming learn requests.
     *
     * @param message       the incoming LEARN type message
     * @param socketOut     the socket for response
     */
    @Override
    public void handleLearn(Message message, OutputStream socketOut) {
        // simulate node reliability (includes changes due to coorong/sheoak)
        if (member.simulateNodeReliability()) return;

        // simulate node delays (includes changes due to coorong/sheoak)
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling LEARN request from " + message.senderID);

        if (message.value != null) {
            learnedValue.setLength(0); // overwrite any previously learned value
            learnedValue.append(message.value);
            log.info(member.config.memberID + ": Learned from " + message.senderID + " elected councillor: " + getLearnedValue());
            sendAck(socketOut); // send ack to confirm value has been learned
        } else {
            log.info(member.config.memberID + ": Learner node instructed to learn null value by " + message.senderID);
            sendNack(socketOut); // send nack
        }
    }

    /**
     * Creates an ACK type message and sends it to socketOut
     *
     * @param socketOut     the socket to deliver the ACK to
     */
    private void sendAck(OutputStream socketOut) {
        Message ack = Message.ack(this.member.config.memberID);
        try {
            socketOut.write(ack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.info(member.config.memberID + ": Error sending ACK - " + ex.getMessage());
        }
    }

    /**
     * Creates an NACK type message and sends it to socketOut
     *
     * @param socketOut     the socket to deliver the NACK to
     */
    private void sendNack(OutputStream socketOut) {
        Message nack = Message.nack(this.member.config.memberID);
        try {
            socketOut.write(nack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.info(member.config.memberID + ": Error sending NACK - " + ex.getMessage());
        }
    }
}
