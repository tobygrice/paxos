package com.a1848962.paxos.utils;

import java.util.logging.*;

/**
 * Class to simplify logging across implementation. Uses a Java Logger to send log messages to the console (stdout).
 * Written with the assistance of AI.
 */
public class SimpleLogger {

    private final Logger logger;

    public SimpleLogger(String name) {
        logger = Logger.getLogger(name);

        // remove default handlers
        logger.setUseParentHandlers(false);

        // create and add a custom handler that writes to System.out
        Handler stdoutHandler = new StreamHandler(System.out, new SingleLineFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush(); // ensure each log message is flushed immediately
            }
        };
        stdoutHandler.setLevel(Level.ALL);
        logger.addHandler(stdoutHandler);

        // log captures all log messages
        logger.setLevel(Level.ALL);
    }

    public synchronized void info(String message) {
        logger.log(Level.INFO, message);
    }

    public synchronized void warn(String message) {
        logger.log(Level.WARNING, message);
    }

    public synchronized void error(String message) {
        logger.log(Level.SEVERE, message);
    }

    public synchronized void silence() {
        logger.setLevel(Level.OFF);
    }

    public synchronized void unsilence() {
        logger.setLevel(Level.ALL);
    }
}