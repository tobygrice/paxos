package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;
import com.a1848962.paxos.utils.SimpleLogger;

import java.io.IOException;
import java.io.OutputStream;

interface LearnerRole {
    String getLearnedValue();
    void handleLearn(Message message, OutputStream socketOut);
    void silence();
    void unsilence();
}

// All members are learners. For this assignment, all members are also acceptors,
// but this is not a requirement of Paxos. Therefore, I have seperated the learner/acceptor
// classes.
public class Learner implements LearnerRole {
    private final Member member;

    private final StringBuffer learnedValue;

    private static final SimpleLogger log = new SimpleLogger("LEARNER");

    public Learner(Member member) {
        this.member = member;
        learnedValue = new StringBuffer();
    }

    public String getLearnedValue() {
        return learnedValue.toString();
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
    @Override
    public void handleLearn(Message message, OutputStream socketOut) {
        // simulate Coorong/Sheoak delays
        try {
            Thread.sleep(member.simulateNodeDelay());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info(member.config.memberID + ": Handling LEARN request from " + message.senderID);

        if (message.value != null) {
            learnedValue.setLength(0);
            learnedValue.append(message.value);
            log.info(member.config.memberID + ": Learned from " + message.senderID + " elected councillor: " + getLearnedValue());
            sendAck(socketOut);
        } else {
            log.info(member.config.memberID + ": Learner node instructed to learn null value by " + message.senderID);
            sendNack(socketOut);
        }
    }

    private void sendAck(OutputStream socketOut) {
        Message ack = Message.ack(this.member.config.memberID);
        try {
            socketOut.write(ack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.info(member.config.memberID + ": Error sending ACK - " + ex.getMessage());
        }
    }

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
