package com.a1848962.paxos.utils;

import com.a1848962.paxos.network.*;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class to provide object type representing a proposal. Requires a proposal number and value to instantiate.
 * Written with the assistance of AI.
 */
public class Proposal {
    public String value;

    private final int proposalNumber;
    private final ConcurrentHashMap<String, Message> promises = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Message> accepts = new ConcurrentHashMap<>();
    private final AtomicInteger rejectCount = new AtomicInteger(0);

    private final AtomicBoolean phaseOneCompleted = new AtomicBoolean(false);
    private final AtomicBoolean phaseTwoCompleted = new AtomicBoolean(false);

    public Proposal(int proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public int getProposalNumber() {
        return proposalNumber;
    }

    public void addPromise(Message promise) {
        promises.put(promise.senderID, promise);
    }

    public void addAccept(Message accept) {
        accepts.put(accept.senderID, accept);
    }

    public Collection<Message> getPromises() {
        return promises.values();
    }

    public int getPromiseCount() {
        return promises.size();
    }

    public int getAcceptCount() {
        return accepts.size();
    }

    public void resetRejectCount() {
        rejectCount.set(0);
    }

    public void incrementRejectCount() {
        rejectCount.incrementAndGet();
    }

    public int getRejectCount() {
        return rejectCount.get();
    }

    public boolean isPhaseOneCompleted() {
        return phaseOneCompleted.get();
    }

    public boolean isCompleted() {
        return phaseTwoCompleted.get();
    }

    public void markPhaseOneCompleted() {
        phaseOneCompleted.set(true);
    }

    public void markCompleted() {
        phaseOneCompleted.set(true);
        phaseTwoCompleted.set(true);
    }
}