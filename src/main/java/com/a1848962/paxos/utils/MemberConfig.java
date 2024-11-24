package com.a1848962.paxos.utils;

import com.a1848962.paxos.network.Network;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.HashMap;

/* class to store member configuration values parsed from member.properties */
public class MemberConfig {
    public volatile String memberID;
    public final String address;
    public final int port;
    public final boolean isLearner;
    public final boolean isAcceptor;
    public final boolean isProposer;
    public final long maxDelay;
    public final double reliability;
    public final double chanceSheoak;
    public final double chanceCoorong;

    // map to hold connection info of all members: key = memberID, value = MemberInfo
    public HashMap<String, MemberInfo> networkInfo;

    public static class MemberInfo {
        public final String id;
        public final boolean isLearner;
        public final boolean isAcceptor;
        public final boolean isProposer;
        public final String address;
        public final int port;

        public MemberInfo(String id, boolean isLearner, boolean isAcceptor, boolean isProposer, String address, int port) {
            this.id = id;
            this.isLearner = isLearner;
            this.isAcceptor = isAcceptor;
            this.isProposer = isProposer;
            this.address = address;
            this.port = port;
        }
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        try (InputStream input = MemberConfig.class.getClassLoader().getResourceAsStream("member.properties")) {
            if (input == null) {
                throw new RuntimeException("member.properties not found in classpath.");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load member.properties.", e);
        }
        return properties;
    }

    // method to construct config object by parsing member.properties
    // this method partially written with the assistance of AI
    public MemberConfig(String memberID) {
        // confirm memberID follows expected format
        if (!memberID.matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected format: positive integer preceded by 'M' (e.g., M1, M2).");
        }

        Properties properties = getProperties();

        // check all default properties are provided
        String[] defaultKeys = {
                "address.default",
                "base_port.default",
                "proposer.default",
                "max_delay.default",
                "reliability.default",
                "sheoak.default",
                "coorong.default"
        };
        for (String key : defaultKeys) {
            if (!properties.containsKey(key)) {
                throw new RuntimeException("Missing default value for " + key);
            }
        }

        // Load members list
        String membersStr = properties.getProperty("members");
        if (membersStr == null || membersStr.trim().isEmpty()) {
            throw new RuntimeException("'members' property is missing or empty.");
        }

        this.networkInfo = new HashMap<>();
        String[] members = membersStr.split(",");
        for (String m : members) {
            String thisMember = m.trim();
            // if (!thisMember.equalsIgnoreCase(memberID)) {
                boolean tempLearner = false, tempAcceptor = false, tempProposer = false;
                if (Boolean.parseBoolean(properties.getProperty(thisMember + ".proposer", properties.getProperty("proposer.default")))) {
                    tempProposer = true;
                }
                if (Boolean.parseBoolean(properties.getProperty(thisMember + ".acceptor", properties.getProperty("acceptor.default")))) {
                    tempAcceptor = true;
                }
                if (Boolean.parseBoolean(properties.getProperty(thisMember + ".learner", properties.getProperty("learner.default")))) {
                    tempLearner = true;
                }
                String tempAddress = properties.getProperty(thisMember + ".address", properties.getProperty("address.default"));
                int tempPort = Integer.parseInt(thisMember.substring(1)) + Integer.parseInt(properties.getProperty(thisMember + ".base_port", properties.getProperty("base_port.default")));
                this.networkInfo.put(thisMember, new MemberInfo(thisMember, tempLearner, tempAcceptor, tempProposer, tempAddress, tempPort));
            // }
        }

        // parse properties
        this.memberID = memberID;
        this.address = properties.getProperty(memberID + ".address", properties.getProperty("address.default"));
        this.port = Integer.parseInt(this.memberID.substring(1)) + Integer.parseInt(properties.getProperty(memberID + ".base_port", properties.getProperty("base_port.default")));
        this.maxDelay = Long.parseLong(properties.getProperty(memberID + ".max_delay", properties.getProperty("max_delay.default")));
        this.reliability = Double.parseDouble(properties.getProperty(memberID + ".reliability", properties.getProperty("reliability.default")));
        this.chanceSheoak = Double.parseDouble(properties.getProperty(memberID + ".sheoak", properties.getProperty("sheoak.default")));
        this.chanceCoorong = Double.parseDouble(properties.getProperty(memberID + ".coorong", properties.getProperty("coorong.default")));
        this.isProposer = Boolean.parseBoolean(properties.getProperty(memberID + ".proposer", properties.getProperty("proposer.default")));
        this.isAcceptor = Boolean.parseBoolean(properties.getProperty(memberID + ".acceptor", properties.getProperty("acceptor.default")));
        this.isLearner = Boolean.parseBoolean(properties.getProperty(memberID + ".learner", properties.getProperty("learner.default")));
        if (!this.isLearner && !this.isAcceptor && !this.isProposer) {
            throw new RuntimeException("Member " + memberID + " has no role.");
        }
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
                ", maxDelay=" + maxDelay +
                ", reliability=" + reliability +
                ", chanceSheoak=" + chanceSheoak +
                ", chanceCoorong=" + chanceCoorong +
                '}';
    }
}