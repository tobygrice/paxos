package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.OutputStream;

// All members are learners. For this assignment, all members are also acceptors,
// but this is not a requirement of Paxos. Therefore, I have seperated the learner/acceptor
// classes.
public class Learner extends Member {

    public Learner(MemberConfig config) {
        super(config);
        this.network = new Network(config.port, this);
    }

    @Override
    public void start() {
        System.out.println("Performing learner role");
    }

    @Override
    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        if (message.type.equals("LEARN")) {
            System.out.println("LEARNER: Incoming LEARN message from " + message.senderID);
            handleLearn(message, socketOut);
        } else {
            System.out.println("Received incompatible message type: " + message.type);
        }
    }

    private void handleLearn(Message message, OutputStream socketOut) {
        System.out.println("Handling LEARN message from " + message.senderID);
        if (message.value != null) {
            learnedValue = message.value;
            System.out.println("Learned " + learnedValue + " from " + message.senderID);
            sendAck(socketOut);
        } else {
            System.out.println("Learner node instructed to learn null value by " + message.senderID);
            sendNack(socketOut);
        }
    }
}
