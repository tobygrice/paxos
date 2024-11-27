// PaxosTestSuite.java
package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.Message;
import com.a1848962.paxos.utils.MemberConfig;
import com.a1848962.paxos.utils.SimpleLogger;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * JUnit test suite for Paxos implementation. PLEASE RUN TESTS INDIVIDUALLY
 */
@TestMethodOrder(MethodOrderer.Random.class)
public class MemberTest {
    private Map<String, Member> members;
    private ExecutorService memberExecutor;
    private static final SimpleLogger log = new SimpleLogger("MEMBER-TEST");

    private final Lock sequential = new ReentrantLock();

    @BeforeEach
    void setup() {
        String[] memberIDs = {"M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9"};
        members = new HashMap<>();
        memberExecutor = Executors.newCachedThreadPool();

        // reset static variables
        Message.MAX_DELAY = 50;
        Message.LOSS_CHANCE = 0.15;

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
                Thread.currentThread().interrupt();
                fail("Setup interrupted: " + e.getMessage());
            }
            members.get("M1").unsilence(); // unsilence logger
        }
        sequential.lock();
    }

    @AfterEach
    void teardownMembers() {
        log.info("Tearing down members");
        for (Member member : members.values()) {
            member.silence();
            member.shutdown();
        }
        memberExecutor.shutdownNow();
        members.clear();
        sequential.unlock();
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

        // allow Paxos protocol time to complete
        Thread.sleep(15000);

        // store all learned values in a set (this line written with AI)
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

        // allow Paxos protocol time to complete
        Thread.sleep(10000);

        // store all learned values in a set (this line written with AI)
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
        // note that there is network delay/unreliability simulation in place. See Message.java:
        //  - random delay on sends from 0-50ms
        //  - 15% chance of packet loss on sends

        // members M2/M3 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M2 = members.get("M2"); // propose using M2 and M3
        Member M3 = members.get("M3");
        M3.getProposer().propose(); // M3 proposes self
        Thread.sleep(500);
        M3.forceCoorong(true, 5000); // then goes camping (unavailable) for 5 seconds
        M2.getProposer().propose(); // M2 proposes self

        // If M3 had enough time to send ACCEPT_REQ, then M2 should propose M3.
        // Otherwise, M2 should propose itself.

        // allow Paxos protocol time to complete
        Thread.sleep(10000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .collect(Collectors.toSet());

        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
    }

    /*--------------------- Additional Testing: stress/edge testing ---------------------*/

    /**
     * Additional Testing 1: Very poor network reliability
     */
    @Test
    @DisplayName("Consensus is reached even with severe packet loss and network delays")
    void testPoorNetwork() throws InterruptedException {
        // message sending can be delayed up to 500ms and has a loss chance of 40%
        Message.MAX_DELAY = 500;
        Message.LOSS_CHANCE = 0.4;

        // members M2/M3 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M3 = members.get("M3"); // propose using M3 (may go to the coorong at any time)
        M3.getProposer().propose();

        Thread.sleep(20000); // sleep for a long time to allow for many rounds

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // check consensus
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M3", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    @Test
    @DisplayName("Consensus is reached when four members go offline during proposal")
    void testFourMembersGoOffline() throws InterruptedException {
        // disable message loss simulation for this test, as a single lost message will cause proposal round to be
        // rejected. Paxos would still eventually reach consensus, but the test may need to run for quite some time.
        // Easier just to disable packet loss for this test.
        Message.LOSS_CHANCE = 0;

        // members M2/M3 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M1 = members.get("M1"); // propose using M1
        M1.getProposer().propose();

        Thread.sleep(100); // allow proposal to start

        // four members go offline during proposal (majority still available so consensus should be achieved)
        String[] remove = {"M3", "M5", "M7", "M9"};
        for (String m : remove) {
            members.get(m).shutdown();
        }

        // allow Paxos protocol time to complete
        Thread.sleep(8000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // check consensus
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M1", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    @Test
    @DisplayName("Consensus is NOT reached when five members go offline during proposal")
    void testFiveMembersGoOffline() throws InterruptedException {
        // disable message loss simulation for this test, see reasoning in testFourMembersGoOffline
        // also disabling message delay to ensure as many proposal rounds as possible can be executed
        Message.LOSS_CHANCE = 0;
        Message.MAX_DELAY = 0;

        // members M2/M3 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M1 = members.get("M1"); // propose using M1
        M1.getProposer().propose();

        Thread.sleep(100); // allow proposal to start

        // five members go offline during proposal (majority offline so consensus should NOT be achieved)
        String[] remove = {"M2", "M3", "M5", "M7", "M9"};
        for (String m : remove) {
            members.get(m).shutdown();
        }

        // allow sufficient time to prove a consensus cannot be reached
        Thread.sleep(20000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // check consensus
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }
        assertEquals(0, learnedValues.size(), "Consensus value/s detected");
    }
}
