package com.a1848962.paxos.network;

import com.google.gson.Gson;

public class Message {
    private static final Gson gson = new Gson();

    public String type; // one of: PREPARE,PROMISE,ACCEPT_REQUEST,ACCEPT,REJECT
    public int proposalNumber;
    public String senderID;
    public String value;
    public int acceptedProposal;
    public String acceptedValue;
    public String reason; // optional, used in REJECT messages

    public String marshall() {
        return gson.toJson(this);
    }

    public static Message unmarshall(String json) {
        return gson.fromJson(json, Message.class);
    }
}