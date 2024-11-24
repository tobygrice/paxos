package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.IOException;
import java.io.OutputStream;

interface LearnerRole {
    String getLearnedValue();
    void handleLearn(Message message, OutputStream socketOut);
}

// All members are learners. For this assignment, all members are also acceptors,
// but this is not a requirement of Paxos. Therefore, I have seperated the learner/acceptor
// classes.
public class Learner extends Member implements LearnerRole {

    private final StringBuffer learnedValue;

    public Learner(MemberConfig config) {
        super(config);
        learnedValue = new StringBuffer();
    }

    public String getLearnedValue() {
        return learnedValue.toString();
    }

    @Override
    public void handleLearn(Message message, OutputStream socketOut) {
        System.out.println("Handling LEARN message from " + message.senderID);
        if (message.value != null) {
            learnedValue.setLength(0);
            learnedValue.append(message.value);
            System.out.println("Learned elected councillor: " + getLearnedValue() + " from " + message.senderID);
            sendAck(socketOut);
        } else {
            System.out.println("Learner node instructed to learn null value by " + message.senderID);
            sendNack(socketOut);
        }
    }

    private void sendAck(OutputStream socketOut) {
        Message ack = Message.ack(this.config.memberID);
        try {
            socketOut.write(ack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            System.out.println("Error sending ACK - " + ex.getMessage());
        }
    }

    private void sendNack(OutputStream socketOut) {
        Message nack = Message.nack(this.config.memberID);
        try {
            socketOut.write(nack.marshall().getBytes());
            socketOut.flush();
        } catch (IOException ex) {
            System.out.println("Error sending NACK - " + ex.getMessage());
        }
    }
}
