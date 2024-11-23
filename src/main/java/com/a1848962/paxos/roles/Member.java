package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/* class to simulate a member in the network. */
public abstract class Member implements Network.PaxosHandler {
    // declare member variables
    protected volatile String memberID;
    protected volatile MemberConfig config;
    protected volatile Network network;

    protected volatile String learnedValue;

    // declare utility member variables
    protected final Random random = new Random();

    public abstract void start();

    public Member(MemberConfig config) {
        this.memberID = config.id;
        this.config = config;
    }

    @Override
    public abstract void handleIncomingMessage(Message message, OutputStream socketOut);

    /**
     * Shuts down the member gracefully by closing network connections and executor services.
     */
    public void shutdown() {
        try {
            // shutdown network
            if (network != null) network.shutdown();
        } catch (Exception ex) {
            System.out.println("Error during shutdown - " + ex.getMessage());
        }
    }

    public boolean sendAck(OutputStream socketOut) {
        Message ack = Message.ack(this.memberID);
        try {
            socketOut.write(ack.marshall().getBytes());
            socketOut.flush();
            return true;
        } catch (IOException ex) {
            System.out.println("Error sending ACK - " + ex.getMessage());
            return false;
        }
    }

    public boolean sendNack(OutputStream socketOut) {
        Message nack = Message.nack(this.memberID);
        try {
            socketOut.write(nack.marshall().getBytes());
            socketOut.flush();
            return true;
        } catch (IOException ex) {
            System.out.println("Error sending NACK - " + ex.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected single argument containing memberID in format M1, M2, etc");
        } else if (!args[0].matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected positive integer preceded by 'M' (e.g., M1, M2).");
        }

        // create config object to parse role from member.properties
        MemberConfig config = new MemberConfig(args[0]);
        Member member; // declare abstract member class

        // assign subclass based on role:
        if (config.isProposer) {
            member = new Proposer(config);
        } else if (config.isAcceptor) {
            member = new Acceptor(config);
        } else {
            member = new Learner(config);
        }

        member.start(); // polymorphic call to start method
    }
}