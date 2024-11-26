// PaxosTestSuite.java
package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.Message;
import com.a1848962.paxos.roles.Member;
import com.a1848962.paxos.utils.MemberConfig;
import com.a1848962.paxos.utils.Proposal;
import com.a1848962.paxos.utils.SimpleLogger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * JUnit test suite for Paxos implementation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MemberTest {
    private final Map<String, Member> members = new HashMap<>();
    private final ExecutorService memberExecutor = Executors.newCachedThreadPool();
    private final SimpleLogger log = new SimpleLogger("MEMBER-TEST");

    @BeforeEach
    void setup() {
        String[] memberIDs = {"M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9"};

        members.clear();
        for (String memberID : memberIDs) {
            MemberConfig thisConfig = new MemberConfig(memberID);
            Member member = new Member(thisConfig);
            members.put(memberID, member);
            memberExecutor.submit(() -> {
                member.start(); // Do not start stdin listener during tests
            });
            // wait briefly to ensure the network is up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // handle interruption
            }
        }
    }

    @AfterEach
    void teardownMembers() {
        // Shutdown all members
        for (Member member : members.values()) {
            member.silence();
            member.shutdown();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    void teardownAll() {
        memberExecutor.shutdownNow();
    }

    /**
     * Test case 1: Paxos works when two councillors send voting proposals at the same time.
     */
    @Test
    @DisplayName("Two proposers send proposals simultaneously")
    void testConcurrentProposals() throws InterruptedException {
        // Identify proposers based on member.properties (M1, M2, M3)

        HashMap<String, Member> proposers = new HashMap<>();
        for (Member m : members.values()) {
            if (m.config.isProposer) proposers.put(m.config.memberID, m);
        }

        assertTrue(proposers.size() >= 2, "At least two proposers are required for this test.");

        // Each proposer sends a proposal simultaneously
        CountDownLatch latch = new CountDownLatch(proposers.size());

        for (Member proposer : proposers.values()) {
            memberExecutor.submit(() -> {
                try {
                    // Propose different values to differentiate proposals
                    proposer.getProposer().propose();
                    log.info(proposer.config.memberID + " proposed");
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all proposals to be sent
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Proposers did not send proposals in time");

        // Allow some time for consensus to be reached
        Thread.sleep(20000);

        // Verify that a consensus was reached
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .collect(Collectors.toSet());

        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
    }

    /**
     * Test case 2: Paxos works when all M1-M9 have immediate responses to voting queries.
     */
    @Test
    @DisplayName("All M1-M9 respond immediately to voting queries")
    void testImmediateResponses() throws InterruptedException {
        // note that there is still network delay/unreliability simulation in place. See Message.java:
        //  - random delay on sends from 0-50ms
        //  - 15% chance of packet loss on sends
        // my implementation works with these in place

        // reduce simulated delay at all nodes to 0 (sheoak/coorong simulation disabled by default)
        //   - all nodes will reply instantly, only point of delay/failure will be the network
        for (Member m : members.values()) {
            m.config.maxDelay = 0;
        }

        Member proposer = members.get("M1"); // propose using M1

        // Proposer sends a proposal
        proposer.getProposer().propose("M9");

        // Allow some time for consensus to be reached
        Thread.sleep(10000);

        // Verify that a consensus was reached
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .collect(Collectors.toSet());

        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M9", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    /**
     * Test case 3: Paxos works when M1 – M9 have varied response behaviors, including delays and offline states.
     */
    @Test
    @DisplayName("Members respond with delays and some proposer goes camping during proposal")
    void testVariableResponsesAndFailures() throws InterruptedException {
        // Simulate varied response behaviors as per member.properties

        // members M1/M2 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member proposer1 = members.get("M2"); // propose using M2 and M3
        Member proposer2 = members.get("M3");
        proposer2.getProposer().propose("M9"); // M3 proposes M9
        Thread.sleep(100);
        proposer2.forceCoorong(true, 5000); // then goes camping (unavailable) for 5 seconds
        proposer1.getProposer().propose("M8"); // M2 proposes M8

        // M2 should handle proposal. If M3 had enough time to send ACCEPT_REQ, then M2 should propose M9.
        // Otherwise, M2 should propose its preferred value of M8.

        // Allow some time for consensus to be reached
        Thread.sleep(15000);

        // Verify that a consensus was reached
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .collect(Collectors.toSet());

        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
    }
}

// write similar test with proposer shutdown instead of visiting coorong