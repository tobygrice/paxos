package com.a1848962.paxos.network;

import com.a1848962.paxos.utils.SimpleLogger;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Message class to represent a message between members. Allows message sending using message.send(address, port).
 * Some parts of this class were written with the assistance of AI, as specified.
 */
public class Message {
    // do not serialise:
    private static final Gson gson = new Gson();
    public static int MAX_DELAY = 50; // maximum send delay in milliseconds
    public static double LOSS_CHANCE = 0.15; // 15% chance of message loss
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
     * Simulate network delay and packet loss according to MAX_DELAY / LOSS_CHANCE values
     *
     * @return  true if the message was lost, else false
     */
    private boolean simulateDelayLoss() {
        // simulate networkInfo delay up to maxDelay length
        int delay;
        if (MAX_DELAY > 0) delay = random.nextInt(MAX_DELAY);
        else delay = 0;

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            log.error("Error simulating delay loss - " + ex.getMessage());
        }

        // simulate message loss with LOSS_CHANCE %
        return (random.nextDouble() < LOSS_CHANCE); // return TRUE if lost else FALSE
    }

    /**
     * Converts object to JSON object using gson
     * @return     a serialisable JSON string
     */
    public String marshall() {
        return gson.toJson(this) + "\n";
    }

    /**
     * Converts JSON object back to Message object
     * @return     a message object from JSON string
     */
    public static Message unmarshall(String json) {
        return gson.fromJson(json, Message.class);
    }

    /**
     * Send this message object to the specified address/port. Returns a CompletableFuture that completes with the
     * response or exceptionally on failure. This function written with the assistance of AI.
     *
     * @param address   address of recipient
     * @param port      port of recipient
     * @return          CompletableFuture<String> containing response
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
     * Creates a PREPARE_REQ message
     *
     * @param proposalCounter   proposal number
     * @param memberID          member ID of sender
     * @return                  PREPARE_REQ type message
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
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param highestPromisedProposal       highest proposal number already promised or accepted
     * @param acceptedValue                 associated value of highest accepted proposal
     * @return                              PROMISE type message
     */
    public static Message promise(int proposalCounter, String memberID, int highestPromisedProposal, String acceptedValue) {
        Message message = promise(proposalCounter, memberID);
        message.highestPromisedProposal = highestPromisedProposal;
        message.acceptedValue = acceptedValue;
        return message;
    }

    /**
     * Creates a PROMISE message
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @return                              PROMISE type message
     */
    public static Message promise(int proposalCounter, String memberID) {
        Message message = new Message();
        message.type = "PROMISE";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates an ACCEPT_REQ message
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param value                         value to be accepted
     * @return                              ACCEPT_REQ type message
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
     * Creates an ACCEPT message
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param value                         value being accepted
     * @return                              ACCEPT type message
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
     * Creates a REJECT message - used for rejecting a request when acceptor has already accepted a higher proposalID
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param highestPromisedProposal       highest proposal number already promised or accepted
     * @param acceptedValue                 associated value of highest accepted proposal
     * @return                              REJECT type message
     */
    public static Message reject(int proposalCounter, String memberID, int highestPromisedProposal, String acceptedValue) {
        Message message = reject(proposalCounter, memberID, highestPromisedProposal);
        message.acceptedValue = acceptedValue;
        return message;
    }

    /**
     * Creates a REJECT message - used for rejecting a request when acceptor has already promised a higher
     * proposalID but not accepted any values
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param highestPromisedProposal       highest proposal number already promised or accepted
     * @return                              REJECT type message
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
     * Creates a LEARN message
     *
     * @param proposalCounter               proposal number
     * @param memberID                      member ID of sender
     * @param value                         value to be learned
     * @return                              LEARN type message
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
     * Creates an ACK message
     *
     * @param memberID                      member ID of sender
     * @return                              ACK type message
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
     * @param memberID                      member ID of sender
     * @return                              NACK type message
     */
    public static Message nack(String memberID) {
        Message message = new Message();
        message.type = "NACK";
        message.senderID = memberID;
        return message;
    }
}