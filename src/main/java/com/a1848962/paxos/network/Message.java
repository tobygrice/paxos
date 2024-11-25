package com.a1848962.paxos.network;

import com.a1848962.paxos.utils.SimpleLogger;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Message {
    // do not serialise:
    private static final Gson gson = new Gson();
    private static final int MAX_DELAY = 200; // maximum send delay in milliseconds
    private static final double LOSS_CHANCE = 0.15; // 15% chance of message loss
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final SimpleLogger log = new SimpleLogger("MESSAGE");
    private static final Random random = new Random();

    // serialise:
    public String type; // one of: PREPARE_REQ,PROMISE,ACCEPT_REQ,ACCEPT,REJECT,LEARN
    public int proposalNumber;
    public String senderID;
    public String value = null; // councillor to be elected
    public int highestPromisedProposal = -1;
    public String acceptedValue = null;

    /**
     * Simulates network delay and packet loss.
     *
     * @return True if the message was lost, else False
     */
    private boolean simulateDelayLoss() {
        // simulate networkInfo delay up to maxDelay length
        int delay = random.nextInt(MAX_DELAY);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            log.error("Error simulating delay loss - " + ex.getMessage());
        }

        // simulate message loss with LOSS_CHANCE %
        return (random.nextDouble() < LOSS_CHANCE); // return TRUE if lost else FALSE
    }

    /**
     * Sends a message asynchronously to the specified address and port.
     * Returns a CompletableFuture that completes with the response or exceptionally on failure.
     *
     * @param address The target address.
     * @param port The target port.
     * @return CompletableFuture<String> with the response.
     */
    public CompletableFuture<Message> send(String address, int port) {
        return CompletableFuture.supplyAsync(() -> {
            if (simulateDelayLoss()) return null;

            try (Socket socket = new Socket(address, port)) {
                // send message
                OutputStream socketOut = socket.getOutputStream();
                String marshalledMessage = marshall(); // newline as delimiter
                socketOut.write(marshalledMessage.getBytes());
                socketOut.flush();

                // read response, timeout after 4 seconds
                socket.setSoTimeout(4000); // 4 seconds timeout for response
                BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = socketIn.readLine();

                if (response == null || response.isEmpty()) {
                    log.warn("No response received from " + address + ":" + port);
                    return null;
                } else {
                    return Message.unmarshall(response);
                }
            } catch (Exception ex) {
                log.warn("Error communicating with " + address + ":" + port + " - " + ex.getMessage());
                return null;
            }
        }, executor);
    }

    /**
     * Creates a PREPARE_REQ message.
     *
     * @param proposalCounter   The proposal number.
     * @param memberID          The ID of the member sending the message.
     * @return A PREPARE_REQ type Message.
     */
    public static Message prepareRequest(int proposalCounter, String memberID) {
        Message message = new Message();
        message.type = "PREPARE_REQ";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates a PROMISE message containing information regarding a previously accepted proposal
     *
     * @param proposalCounter               The proposal number being promised.
     * @param memberID                      The ID of the member sending the message.
     * @param highestPromisedProposal       The highest proposal number already promised.
     * @param acceptedValue                 The value of the highest proposal accepted.
     * @return A PROMISE type Message.
     */
    public static Message promise(int proposalCounter, String memberID, int highestPromisedProposal, String acceptedValue) {
        Message message = promise(proposalCounter, memberID);
        message.highestPromisedProposal = highestPromisedProposal;
        message.acceptedValue = acceptedValue;
        return message;
    }

    /**
     * Creates a PROMISE message.
     *
     * @param proposalCounter   The proposal number being promised.
     * @param memberID          The ID of the member sending the message.
     * @return A PROMISE type Message.
     */
    public static Message promise(int proposalCounter, String memberID) {
        Message message = new Message();
        message.type = "PROMISE";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates an ACCEPT_REQ message.
     *
     * @param proposalCounter   The proposal number.
     * @param memberID          The ID of the member sending the message.
     * @param value             The value to accept.
     * @return An ACCEPT_REQ type Message.
     */
    public static Message acceptRequest(int proposalCounter, String memberID, String value) {
        Message message = new Message();
        message.type = "ACCEPT_REQ";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        message.value = value;
        return message;
    }

    /**
     * Creates an ACCEPT message.
     *
     * @param proposalCounter   The proposal number that was accepted.
     * @param memberID          The ID of the member sending the message.
     * @param value             The value that was accepted.
     * @return An ACCEPT type Message.
     */
    public static Message accept(int proposalCounter, String memberID, String value) {
        Message message = new Message();
        message.type = "ACCEPT";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        message.value = value;
        return message;
    }

    /**
     * Creates a REJECT message - used for rejecting a request when acceptor has already accepted a higher proposalID.
     *
     * @param proposalCounter               The proposal number that was rejected.
     * @param memberID                      The ID of the member sending the message.
     * @param highestPromisedProposal       The highest proposal number already promised.
     * @param acceptedValue                 The value of the highest proposal accepted.
     * @return A REJECTED type Message.
     */
    public static Message reject(int proposalCounter, String memberID, int highestPromisedProposal, String acceptedValue) {
        Message message = reject(proposalCounter, memberID, highestPromisedProposal);
        message.acceptedValue = acceptedValue;
        return message;
    }

    /**
     * Creates a REJECT message - used for rejecting a request when acceptor has already promised a higher
     * proposalID, and acceptor not accepted any values.
     *
     * @param proposalCounter               The proposal number that was rejected.
     * @param memberID                      The ID of the member sending the message.
     * @param highestPromisedProposal       The highest proposal number already promised.
     * @return A REJECTED type Message.
     */
    public static Message reject(int proposalCounter, String memberID, int highestPromisedProposal) {
        Message message = new Message();
        message.type = "REJECT";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        message.highestPromisedProposal = highestPromisedProposal;
        return message;
    }

    /**
     * Creates a LEARN message.
     *
     * @param proposalCounter   The proposal number that was learned.
     * @param memberID          The ID of the member sending the message.
     * @param value             The value that was learned.
     * @return A LEARN type Message.
     */
    public static Message learn(int proposalCounter, String memberID, String value) {
        Message message = new Message();
        message.type = "LEARN";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        message.value = value;
        return message;
    }

    /**
     * Creates an ACK message.
     *
     * @param memberID The ID of the member sending the acknowledgment.
     * @return An ACK type Message.
     */
    public static Message ack(String memberID) {
        Message message = new Message();
        message.type = "ACK";
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates an NACK message.
     *
     * @param memberID The ID of the member sending the acknowledgment.
     * @return An ACK type Message.
     */
    public static Message nack(String memberID) {
        Message message = new Message();
        message.type = "NACK";
        message.senderID = memberID;
        return message;
    }

    public String marshall() {
        return gson.toJson(this) + "\n";
    }

    public static Message unmarshall(String json) {
        return gson.fromJson(json, Message.class);
    }
}