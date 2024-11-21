package com.a1848962.paxos.network;

import com.google.gson.Gson;

// constructor methods of this class were written with the assistance of AI
public class Message {
    private static final Gson gson = new Gson();

    public String type; // one of: PREPARE,PROMISE,ACCEPT_REQUEST,ACCEPTED,REJECTED
    public int proposalNumber;
    public String senderID;
    public String value; // councillor to be elected
    public int acceptedProposal;
    public String acceptedValue;
    public String reason; // optional, used in REJECT messages

    /**
     * Creates a PREPARE message.
     *
     * @param proposalCounter The proposal number.
     * @param memberID        The ID of the member sending the message.
     * @return A PREPARE type Message.
     */
    public static Message prepare(int proposalCounter, String memberID) {
        Message message = new Message();
        message.type = "PREPARE";
        message.proposalNumber = proposalCounter;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates a PROMISE message.
     *
     * @param proposalCounter    The proposal number being promised.
     * @param acceptedProposal   The highest proposal number already accepted.
     * @param acceptedValue      The value of the highest proposal accepted.
     * @param memberID           The ID of the member sending the message.
     * @return A PROMISE type Message.
     */
    public static Message promise(int proposalCounter, int acceptedProposal, String acceptedValue, String memberID) {
        Message message = new Message();
        message.type = "PROMISE";
        message.proposalNumber = proposalCounter;
        message.acceptedProposal = acceptedProposal;
        message.acceptedValue = acceptedValue;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates an ACCEPT_REQUEST message.
     *
     * @param proposalCounter The proposal number.
     * @param value           The value to accept.
     * @param memberID        The ID of the member sending the message.
     * @return An ACCEPT_REQUEST type Message.
     */
    public static Message acceptRequest(int proposalCounter, String value, String memberID) {
        Message message = new Message();
        message.type = "ACCEPT_REQUEST";
        message.proposalNumber = proposalCounter;
        message.value = value;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates an ACCEPTED message.
     *
     * @param proposalCounter The proposal number that was accepted.
     * @param value           The value that was accepted.
     * @param memberID        The ID of the member sending the message.
     * @return An ACCEPTED type Message.
     */
    public static Message accepted(int proposalCounter, String value, String memberID) {
        Message message = new Message();
        message.type = "ACCEPTED";
        message.proposalNumber = proposalCounter;
        message.value = value;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates a REJECT message.
     *
     * @param proposalCounter The proposal number that was rejected.
     * @param reason          The reason for rejection.
     * @param memberID        The ID of the member sending the message.
     * @return A REJECTED type Message.
     */
    public static Message rejected(int proposalCounter, String reason, String memberID) {
        Message message = new Message();
        message.type = "REJECTED";
        message.proposalNumber = proposalCounter;
        message.reason = reason;
        message.senderID = memberID;
        return message;
    }

    /**
     * Creates a LEARN message.
     *
     * @param proposalCounter The proposal number that was learned.
     * @param value           The value that was learned.
     * @param memberID        The ID of the member sending the message.
     * @return A LEARN type Message.
     */
    public static Message learn(int proposalCounter, String value, String memberID) {
        Message message = new Message();
        message.type = "LEARN";
        message.proposalNumber = proposalCounter;
        message.value = value;
        message.senderID = memberID;
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

    public String marshall() {
        return gson.toJson(this);
    }

    public static Message unmarshall(String json) {
        return gson.fromJson(json, Message.class);
    }
}