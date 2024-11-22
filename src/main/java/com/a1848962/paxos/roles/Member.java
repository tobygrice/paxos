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

    protected String learnedValue;

    // declare utility member variables
    protected static final Logger logger = LoggerFactory.getLogger(Member.class);
    protected final Random random = new Random();

    public abstract void start();

    public Member(MemberConfig config) {
        this.memberID = config.id;
        this.config = config;
        logger.info("Initialised member {}", memberID);
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
            logger.info("{}: Member shutdown complete.", memberID);
        } catch (Exception e) {
            logger.error("{}: Error during shutdown - {}", memberID, e.getMessage());
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
            logger.info("{} assigned Proposer class", config.id);
        } else if (config.isAcceptor) {
            member = new Acceptor(config);
            logger.info("{} assigned Acceptor class", config.id);
        } else {
            member = new Learner(config);
            logger.info("{} assigned Learner class", config.id);
        }

        member.start(); // polymorphic call to start method
    }
}