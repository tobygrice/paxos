package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;
import com.a1848962.paxos.utils.SimpleLogger;

import java.io.IOException;
import java.io.OutputStream;

interface LearnerRole {
    String getLearnedValue();
    void handleLearn(Message message, OutputStream socketOut);
}

// All members are learners. For this assignment, all members are also acceptors,
// but this is not a requirement of Paxos. Therefore, I have seperated the learner/acceptor
// classes.
public class Learner implements LearnerRole {
    private final MemberConfig config;
    private final StringBuffer learnedValue;

    private final SimpleLogger log = new SimpleLogger("LEARNER");

    public Learner(MemberConfig config) {
        this.config = config;
        learnedValue = new StringBuffer();
    }

    public String getLearnedValue() {
        return learnedValue.toString();
    }

    @Override
    public void handleLearn(Message message, OutputStream socketOut) {
        log.info("Handling LEARN request from " + message.senderID);

        if (message.value != null) {
            learnedValue.setLength(0);
            learnedValue.append(message.value);
            log.info("Learned from " + message.senderID + " elected councillor: " + getLearnedValue());
            sendAck(socketOut);
        } else {
            log.warn("Learner node instructed to learn null value by " + message.senderID);
            sendNack(socketOut);
        }
    }

    private void sendAck(OutputStream socketOut) {
        Message ack = Message.ack(this.config.memberID);
        try {
            socketOut.write(ack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.error("Error sending ACK - " + ex.getMessage());
        }
    }

    private void sendNack(OutputStream socketOut) {
        Message nack = Message.nack(this.config.memberID);
        try {
            socketOut.write(nack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            log.error("Error sending NACK - " + ex.getMessage());
        }
    }
}
