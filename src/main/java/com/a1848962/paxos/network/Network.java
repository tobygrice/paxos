package com.a1848962.paxos.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.*;

import com.a1848962.paxos.utils.SimpleLogger;

/**
 *  Network infrastructure class to listen for incoming messages, unmarshall them and pass them to the parent member
 */
public class Network {

    private ServerSocket serverSocket;
    private final int listenPort;

    private final PaxosHandler handler;

    private static final SimpleLogger log = new SimpleLogger("NETWORK");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Silences log output
     */
    public void silence() {
        log.silence();
    }

    /**
     * Unsilences log output
     */
    public void unsilence() {
        log.unsilence();
    }

    /**
     * Interface to handle received messages. This is my first attempt at using an interface.
     */
    public interface PaxosHandler {
        void handleIncomingMessage(Message message, OutputStream socketOut);
    }

    public Network(int listenPort, PaxosHandler handler) {
        this.listenPort = listenPort;
        this.handler = handler;
    }

    /**
     * Establish a ServerSocket listening on listenPort
     */
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
     * Unmarshall incoming messages and pass them to handleIncomingMessage
     *
     * @param socket    client socket
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
     * Shuts down ServerSocket listener. This function was written with the assistance of AI.
     */
    public void shutdown() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in the specified time.");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during executor shutdown.");
            Thread.currentThread().interrupt();
        }
        log.info("Network shutdown complete");
    }
}