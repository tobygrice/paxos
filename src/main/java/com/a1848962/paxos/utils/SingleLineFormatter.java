package com.a1848962.paxos.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Custom formatter to output log records in a single line.
 * Written with the assistance of AI.
 */
public class SingleLineFormatter extends Formatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        // format timestamp
        sb.append(TIME_FORMATTER.format(Instant.ofEpochMilli(record.getMillis())));
        sb.append(" ");

        // append logger name
        sb.append(record.getLoggerName());
        sb.append(" ");

        // append log level
        sb.append(record.getLevel().getName());
        sb.append(": ");

        // append log message
        sb.append(formatMessage(record));
        sb.append(System.lineSeparator());

        return sb.toString();
    }
}