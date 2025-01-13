package com.tobygrice.paxos.utils;

import java.util.HashMap;

/**
 * Class to store member configuration values parsed from member.properties. Also stores a map containing information
 * of all other members in the network.
 */
public class MemberConfig {
    private final String memberID;
    private final String address;
    private final int port;
    private final boolean isLearner;
    private final boolean isAcceptor;
    private final boolean isProposer;

    // map to hold connection info of all members
    private final HashMap<String, MemberConfig> networkInfo;

    public MemberConfig(String id, boolean isLearner, boolean isAcceptor, boolean isProposer, String address, int port) {
        this.memberID = id;
        this.isLearner = isLearner;
        this.isAcceptor = isAcceptor;
        this.isProposer = isProposer;
        this.address = address;
        this.port = port;
        this.networkInfo = new HashMap<>();
    }

    @Override
    public String toString() {
        return "MemberConfig{" +
                "memberID=" + memberID +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", isProposer=" + isProposer +
                ", isAcceptor=" + isAcceptor +
                ", isLearner=" + isLearner +
                '}';
    }

    // getters and setters:
    public String getMemberID() {
        return memberID;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isLearner() {
        return isLearner;
    }

    public boolean isAcceptor() {
        return isAcceptor;
    }

    public boolean isProposer() {
        return isProposer;
    }

    public HashMap<String, MemberConfig> getNetworkInfo() {
        return networkInfo;
    }

    public void addNetworkMember(MemberConfig memberConfig) {
        networkInfo.put(memberConfig.getMemberID(), memberConfig);
    }

    public void addNetworkMember(String id, boolean isLearner, boolean isAcceptor, boolean isProposer, String address, int port) {
        MemberConfig newMember = new MemberConfig(id, isLearner, isAcceptor, isProposer, address, port);
        networkInfo.put(id, newMember);
    }
}