package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.*;

import java.io.OutputStream;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/* class to simulate a member in the network. */
public abstract class Member implements Network.PaxosHandler {
    // declare member variables
    protected String memberID;
    protected MemberConfig config;
    protected Network network;

    // declare utility member variables
    protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    protected static final Logger logger = LoggerFactory.getLogger(Member.class);
    protected final Random random = new Random();

    public abstract void start();

    public Member(MemberConfig config) {
        this.memberID = config.id;
        this.config = config;
        logger.info("Initialised member {}", memberID);
    }

    @Override
    public abstract void handleMessage(Message message, OutputStream socketOut);


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
        switch (config.role) {
            case "PROPOSER":
                member = new Proposer(config);
                logger.info("{} assigned Proposer class", config.id);
                break;
            case "ACCEPTOR":
                member = new Acceptor(config);
                logger.info("{} assigned Acceptor class", config.id);
                break;
            case "LEARNER":
                member = new Learner(config);
                logger.info("{} assigned Learner class", config.id);
                break;
            default:
                throw new IllegalArgumentException("Invalid role: " + config.role);
        }

        member.start(); // polymorphic call to start method
    }
}