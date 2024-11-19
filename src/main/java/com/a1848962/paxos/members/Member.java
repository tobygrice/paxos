package com.a1848962.paxos.members;

import com.a1848962.paxos.utils.*;

import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* class to simulate a member in the network. */
public class Member {
    // define how long a member will stay at Sheoak cafe or the Coorong (seconds)
    private static final int TIME_IN_SHEOAK = 2;
    private static final int TIME_IN_COORONG = 2;

    // declare member variables
    public String memberID;
    public MemberConfig config;
    private boolean currentlySheoak;

    // declare utility member variables
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(Network.class);
    private final Random random = new Random();

    public Member(String memberID) {
        this.memberID = memberID;
        this.config = new MemberConfig(memberID);
        this.currentlySheoak = false;
        logger.info("Initialised member {}", memberID);
    }

    public void start() {
        while (true) {
            try {
                // simulate chance for member to go camping
                if (config.chanceCoorong > random.nextDouble()) {
                    // member has gone camping in the Coorong, now unreachable
                    System.out.println(memberID + " is camping in the Coorong. They are unreachable.");
                    logger.info("{} at Coorong", memberID);
                    Thread.sleep(TIME_IN_COORONG * 1000); // unreachable whilst camping
                }

                // simulate chance for member to go to Sheoak cafe
                if (!currentlySheoak && config.chanceSheoak > random.nextDouble()) {
                    // member has gone to Sheoak cafe, responses now instant
                    System.out.println(memberID + " is at Sheoak Café. Responses are instant.");
                    logger.info("{} at Sheoak cafe", memberID);
                    currentlySheoak(); // update currentlySheoak boolean and start reset timer
                }

                // simulate random response delay up to max delay value
                long currentMaxDelay = currentlySheoak ? 0 : config.maxDelay; // delay = 0 if currentlySheoak==true
                long delay = (long)(random.nextDouble() * currentMaxDelay);
                Thread.sleep(delay);

                // TO-DO: handle reliability issues config.reliability (reliability = 1 if currentlySheoak==true)

                // TO-DO: handle roles

            } catch (InterruptedException e) {
                System.out.println(memberID + " was interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // method to set currentlySheoak to true and reset back to false after specified time (non-blocking)
    private void currentlySheoak() {
        this.currentlySheoak = true;
        scheduler.schedule(() -> {this.currentlySheoak = false;}, TIME_IN_SHEOAK, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected single argument containing memberID in format M1, M2, etc");
        } else if (!args[0].matches("M\\d+")) {
            throw new IllegalArgumentException("Invalid memberID format. Expected positive integer preceded by 'M' (e.g., M1, M2).");
        }

        Member member = new Member(args[0]);
        member.start();
    }
}