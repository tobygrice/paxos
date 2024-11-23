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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Network infrastructure for communication across nodes */
// This infrastructure class was written with the assistance of AI
public class Network {
    private final int MAX_DELAY = 1000; // maximum delay in milliseconds
    private final double LOSS_CHANCE = 0.05; // 5% chance of message loss

    private static final Logger logger = LoggerFactory.getLogger(Network.class);
    private final Random random = new Random();

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
        start();
    }

    // method to establish a ServerSocket listening on listenPort
    private void start() {
        // use executor service to handle incoming connections
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(listenPort);
                logger.info("Network: member listening on port {}", listenPort);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executor.submit(() -> connectionHandler(clientSocket));
                    } catch (IOException e) {
                        logger.error("Network: error accepting connection - {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                logger.error("Network: error starting server on port {} - {}", listenPort, e.getMessage());
            }
        });
    }

    /**
     * Handles an incoming connection by reading the message and dispatching it.
     *
     * @param socket The socket connected to the client.
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
        } catch (IOException e) {
            logger.error("Network: Error handling incoming connection - {}", e.getMessage());
        }
    }

    /**
     * Sends a message asynchronously to the specified address and port.
     * Returns a CompletableFuture that completes with the response or exceptionally on failure.
     *
     * @param address The target address.
     * @param port The target port.
     * @param message The message to send.
     * @return CompletableFuture<String> with the response.
     */
    public CompletableFuture<Message> sendMessage(String address, int port, Message message) {
        return CompletableFuture.supplyAsync(() -> {
            /*
            // simulate network delay up to MAX_DELAY length
            try {
                int delay = random.nextInt(MAX_DELAY);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Message sending interrupted", e);
            }

            // simulate message loss with LOSS_CHANCE %
            if (random.nextDouble() < LOSS_CHANCE) {
                logger.warn("Message lost: {}", message);
                throw new RuntimeException("Message lost");
            }
            */

            try (Socket socket = new Socket(address, port)) {
                // send message
                OutputStream socketOut = socket.getOutputStream();
                String marshalledMessage = message.marshall() + "\n"; // newline as delimiter
                socketOut.write(marshalledMessage.getBytes());
                socketOut.flush();

                // read response, timeout after 2 seconds
                socket.setSoTimeout(2000); // 2 seconds timeout for response
                BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = socketIn.readLine();

                if (response == null) {
                    throw new RuntimeException("No response received");
                } else if (response.isEmpty()) {
                    throw new RuntimeException("Empty response received");
                }

                return Message.unmarshall(response);
            } catch (IOException e) {
                logger.error("Error sending message to {}:{} - {}", address, port, e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
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
        } catch (IOException | InterruptedException e) {
            logger.error("Network: Error during shutdown - {}", e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}