package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.*;
import com.a1848962.paxos.utils.MemberConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// all proposers are acceptors
public class Proposer extends Acceptor {
    // define how long a proposer will stay at Sheoak cafe or the Coorong (in milliseconds)
    private static final int TIME_IN_SHEOAK = 2000; // 2 seconds at each place
    private static final int TIME_IN_COORONG = 2000;

    private int proposalCounter;
    private boolean currentlySheoak;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(); // for Sheoak cafe
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public Proposer(MemberConfig config) {
        super(config);
        this.network = new Network(config.port, this);
        this.currentlySheoak = false;
        this.proposalCounter = 0;
    }

    @Override
    public void handleIncomingMessage(Message message, OutputStream socketOut) {
        // simulate chance for member to go camping
        if (config.chanceCoorong > random.nextDouble()) {
            // member has gone camping in the Coorong, now unreachable
            System.out.println(memberID + " is camping in the Coorong. They are unreachable.");
            logger.info("{} at Coorong", memberID);
            try {
                Thread.sleep(TIME_IN_COORONG); // unreachable whilst camping
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // simulate chance for member to go to Sheoak cafe
        if (!currentlySheoak && config.chanceSheoak > random.nextDouble()) {
            // member has gone to Sheoak cafe, responses now instant
            System.out.println(memberID + " is at Sheoak Café. Responses are instant.");
            logger.info("{} at Sheoak cafe", memberID);
            this.currentlySheoak = true; // update currentlySheoak boolean and start reset timer using scheduler
            scheduler.schedule(() -> {this.currentlySheoak = false;}, TIME_IN_SHEOAK, TimeUnit.MILLISECONDS);
        }

        // simulate random response delay up to max delay value
        long currentMaxDelay = currentlySheoak ? 0 : config.maxDelay; // delay = 0 if currentlySheoak==true
        long delay = (long)(random.nextDouble() * currentMaxDelay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        switch (message.type) {
            case "PROMISE":
                handlePromise(message, socketOut);
                break;
            case "ACCEPT":
                handleAccept(message, socketOut);
                break;
            case "REJECT":
                handleReject(message, socketOut);
                break;
            case "LEARN":
                super.handleIncomingMessage(message, socketOut);
                break;
            default:
                logger.warn("{}: Proposer node received message type it cannot handle: {}", memberID, message.type);
        }
    }

    private void handlePromise(Message message, OutputStream socketOut) {
    }

    private void handleAccept(Message message, OutputStream socketOut) {
    }

    private void handleReject(Message message, OutputStream socketOut) {
    }


    @Override
    public void start() {
        // start listening for commands on stdin in a separate thread
        executor.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String command = line.trim().toLowerCase();
                    if (command.equals("propose")) {
                        // send proposal to all nodes in network
                        for (MemberConfig.ConnectionInfo connectionInfo : this.config.network.values()) {
                            Message message = new Message();
                            network.sendMessage(connectionInfo.address, connectionInfo.port, message);
                        }
                    } else if (command.equals("exit")) {
                        logger.info("{}: 'exit' command received. Shutting down.", memberID);
                        shutdown();
                    } else {
                        System.out.println("Unknown command: " + command);
                        System.out.println("Usage:");
                        System.out.println(" - `propose` to broadcast a prepare message");
                        System.out.println(" - `exit` to shut down node");
                    }
                }
            } catch (IOException e) {
                logger.error("{}: Error reading stdin: {}", memberID, e.getMessage());
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        executor.shutdownNow(); // shutdown executor
        scheduler.shutdownNow(); // shutdown scheduler
        logger.info("{}: Proposer shutdown complete.", memberID);
        System.exit(0);
    }
}
