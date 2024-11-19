package com.a1848962.paxos.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/* class to store member configuration values parsed from member.properties */
public class MemberConfig {
    public int id;
    public String address;
    public int port;
    public boolean isProposer;
    public long maxDelay;
    public double reliability;
    public double chanceSheoak;
    public double chanceCoorong;

    // method to construct config object by parsing member.properties
    // this method partially written with the assistance of AI
    public MemberConfig(String memberID) {
        // confirm memberID follows expected format
        if (!memberID.matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected format: positive integer preceded by 'M' (e.g., M1, M2).");
        }

        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("member.properties")) {
            if (input == null) {
                throw new RuntimeException("member.properties not found in classpath.");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load member.properties.", e);
        }

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

        // parse properties
        this.id = Integer.parseInt(memberID.substring(1));
        this.address = properties.getProperty(memberID + ".address", properties.getProperty("address.default"));
        this.port = this.id + Integer.parseInt(properties.getProperty(memberID + ".base_port", properties.getProperty("base_port.default")));
        this.isProposer = Boolean.parseBoolean(properties.getProperty(memberID + ".proposer", properties.getProperty("proposer.default")));
        this.maxDelay = Long.parseLong(properties.getProperty(memberID + ".max_delay", properties.getProperty("max_delay.default")));
        this.reliability = Double.parseDouble(properties.getProperty(memberID + ".reliability", properties.getProperty("reliability.default")));
        this.chanceSheoak = Double.parseDouble(properties.getProperty(memberID + ".sheoak", properties.getProperty("sheoak.default")));
        this.chanceCoorong = Double.parseDouble(properties.getProperty(memberID + ".coorong", properties.getProperty("coorong.default")));
    }

    @Override
    public String toString() {
        return "MemberConfig{" +
                "id=" + id +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", isProposer=" + isProposer +
                ", maxDelay=" + maxDelay +
                ", reliability=" + reliability +
                ", chanceSheoak=" + chanceSheoak +
                ", chanceCoorong=" + chanceCoorong +
                '}';
    }
}