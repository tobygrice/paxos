package com.a1848962.paxos.utils;

import com.a1848962.paxos.messages.Message;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Network {
    private static final Logger logger = LoggerFactory.getLogger(Network.class);
    private final Random random = new Random();

    public void sendMessage(String recipientId, Message message) {
        // Simulate network delay
        int delay = random.nextInt(3000); // 0-3 seconds
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            logger.error("Interrupted during message send", e);
        }
        // Simulate message loss with a small probability
        if (random.nextDouble() < 0.05) { // 5% chance to lose the message
            logger.warn("Message lost: {}", message);
            return;
        }
        // Deliver the message
        // Implement actual delivery mechanism (e.g., via sockets or in-memory queues)
    }
}