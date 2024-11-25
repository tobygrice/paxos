package com.a1848962.paxos.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.a1848962.paxos.utils.SimpleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Network infrastructure for communication across nodes */
// This infrastructure class was written with the assistance of AI
public class Network {

    private final SimpleLogger log = new SimpleLogger("NETWORK");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private final int listenPort;

    private final PaxosHandler handler;

    /**
     * Interface to handle received messages. This is my first attempt at using an interface.
     */
    public interface PaxosHandler {
        void handleIncomingMessage(Message message, OutputStream socketOut);
    }

    /**
     * Constructor to initialize the Network with a listening port and a message handler.
     *
     * @param listenPort The port on which to listen for incoming messages.
     * @param handler    The handler to process incoming messages.
     */
    public Network(int listenPort, PaxosHandler handler) {
        this.listenPort = listenPort;
        this.handler = handler;
    }

    // method to establish a ServerSocket listening on listenPort
    public void start() {
        // use executor service to handle incoming connections
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(listenPort);
                log.info("Member listening on port " + listenPort);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executor.submit(() -> connectionHandler(clientSocket));
                    } catch (IOException ex) {
                        log.error("Network: error accepting connection - " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                log.error("Network: error starting server on port " + listenPort + " - " + ex.getMessage());
            }
        });
    }

    /**
     * Unmarshalls incoming message and passes to handleIncomingMessage method of handler
     *
     * @param socket    The socket connected to the client.
     */
    private void connectionHandler(Socket socket) {
        try {
            BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream socketOut = socket.getOutputStream();

            String receivedString = socketIn.readLine();
            if (receivedString != null && !receivedString.isEmpty()) {
                Message receivedMessage = Message.unmarshall(receivedString);
                handler.handleIncomingMessage(receivedMessage, socketOut);
            }
        } catch (IOException ex) {
            log.error("Network: Error handling incoming connection - " + ex.getMessage());
        }
    }

    /**
     * Shuts down the executor service gracefully.
     */
    public void shutdown() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (Exception ex) {
            log.error("Network: Error during shutdown - " + ex.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}