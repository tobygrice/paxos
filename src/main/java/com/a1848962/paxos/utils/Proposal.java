package com.a1848962.paxos.utils;

import com.a1848962.paxos.network.*;

import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// this util class was written with the assistance of AI

/**
 * Class to provide object type representing a proposal.
 * Requires a proposal number and value to instantiate.
 */
public class Proposal {
    public String value;

    private final int proposalNumber;
    private final ConcurrentHashMap<String, Message> promises = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Message> accepts = new ConcurrentHashMap<>();
    private final AtomicInteger rejectCount = new AtomicInteger(0);

    public volatile boolean phaseOneCompleted = false;
    private volatile boolean phaseTwoCompleted = false;

    /**
     * Object representing a proposal.
     * @param proposalNumber    The proposalCounter when the proposal was created.
     */
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

    public boolean isCompleted() {
        return phaseOneCompleted && phaseTwoCompleted;
    }

    public void markCompleted() {
        phaseOneCompleted = phaseTwoCompleted = true;
    }
}