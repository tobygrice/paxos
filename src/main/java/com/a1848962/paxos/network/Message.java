package com.a1848962.paxos.network;

import com.google.gson.Gson;

// constructor methods of this class were written with the assistance of AI
public class Message {
    private static final Gson gson = new Gson();

    public String type; // one of: PREPARE_REQ,PROMISE,ACCEPT_REQ,ACCEPT,REJECT,LEARN
    public int proposalNumber;
    public String senderID;
    public String value = null; // councillor to be elected
    public int highestPromisedProposal = -1;
    public String acceptedValue = null;

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
     * @param value             The value that was accepted.
     * @param memberID          The ID of the member sending the message.
     * @return An ACCEPT type Message.
     */
    public static Message accept(int proposalCounter, String value, String memberID) {
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