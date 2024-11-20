package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

// all proposers are acceptors
public class Proposer extends Acceptor {
    // define how long a proposer will stay at Sheoak cafe or the Coorong (in milliseconds)
    private static final int TIME_IN_SHEOAK = 2000; // 2 seconds at each place
    private static final int TIME_IN_COORONG = 2000;

    private int proposalCounter;
    private boolean currentlySheoak;

    public Proposer(MemberConfig config) {
        super(config);
        this.network = new Network(config.port, this);
        this.currentlySheoak = false;
        this.proposalCounter = 0;
    }

    @Override
    public void handleMessage(Message message, OutputStream socketOut) {
        switch (message.type) {
            case "PROMISE":
                handlePromise(message, socketOut);
                break;
            case "ACCEPT":
                handleAccepted(message, socketOut);
                break;
            case "LEARN":
                super.handleMessage(message, socketOut);
                break;
            default:
                logger.warn("{}: Proposer node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void handleAccepted(Message message, OutputStream socketOut) {
    }

    private void handlePromise(Message message, OutputStream socketOut) {
    }


    // method to set currentlySheoak to true and reset back to false after specified time (non-blocking)
    private void currentlySheoak() {
        this.currentlySheoak = true;
        scheduler.schedule(() -> {this.currentlySheoak = false;}, TIME_IN_SHEOAK, TimeUnit.MILLISECONDS);
    }

    @Override
    public void start() {
        while (true) {
            try {
                // simulate chance for member to go camping
                if (config.chanceCoorong > random.nextDouble()) {
                    // member has gone camping in the Coorong, now unreachable
                    System.out.println(memberID + " is camping in the Coorong. They are unreachable.");
                    logger.info("{} at Coorong", memberID);
                    Thread.sleep(TIME_IN_COORONG); // unreachable whilst camping
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

                // send proposal to all nodes in network
                for (MemberConfig.ConnectionInfo connectionInfo : this.config.network.values()) {
                    Message message = new Message();
                    network.sendMessage(connectionInfo.address, connectionInfo.port, message);
                }

            } catch (InterruptedException e) {
                System.out.println(memberID + " was interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
