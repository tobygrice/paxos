package com.a1848962.paxos.utils;

import com.a1848962.paxos.messages.Message;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Network {
    private final int MAX_DELAY = 1; // maximum delay in seconds
    private final double LOSS_CHANCE = 5; // percentage chance of message loss

    private static final Logger logger = LoggerFactory.getLogger(Network.class);
    private final Random random = new Random();

    public void sendMessage(String recipientId, Message message) {
        // simulate network delay
        int delay = random.nextInt(1000 * MAX_DELAY);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            logger.error("Interrupted during message send", e);
        }

        // simulate message loss
        if (random.nextDouble() < (LOSS_CHANCE / 100)) { // if message lost
            logger.warn("Message lost: {}", message);
            return;
        }

        // deliver message
        // TO-DO: implement delivery mechanism using sockets
    }
}