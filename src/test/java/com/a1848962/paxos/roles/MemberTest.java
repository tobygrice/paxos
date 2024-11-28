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
 * Paxos test suite.
 */
public class MemberTest {
    private final Map<String, Member> members = new HashMap<>();;
    private final ExecutorService memberExecutor = Executors.newCachedThreadPool();
    private final Lock sequential = new ReentrantLock();
    private static final SimpleLogger log = new SimpleLogger("TEST");

    @BeforeEach
    void setup() {
        sequential.lock(); // ensure each test is run sequentially (only one server socket can listen on each port)
        String[] memberIDs = {"M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9"};

        // reset network delay variables
        Message.MAX_DELAY = 50;
        Message.LOSS_CHANCE = 0.15;

        // instantiate members and start each one in a new thread using memberExecutor
        for (String memberID : memberIDs) {
            MemberConfig thisConfig = new MemberConfig(memberID);
            Member member = new Member(thisConfig);

            members.put(memberID, member);
            memberExecutor.submit(() -> {
                member.start(); // do not start stdin listener during tests
            });
        }
        // wait briefly to ensure the network is up
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        members.get("M1").unsilence(); // unsilence logger for member objects
    }

    @AfterEach
    void teardownMembers() {
        log.info("Tearing down members");
        for (Member member : members.values()) {
            member.silence(); // silence members for teardown process so that result of test is still visible
            member.shutdown();
        }
        memberExecutor.shutdownNow();
        members.clear(); // clear members for next test
        sequential.unlock();
    }

    /**
     * Test case 1: Paxos implementation works when two councillors send voting proposals at the same time.
     */
    @Test
    @DisplayName("Two councillors send voting proposals at the same time")
    void testConcurrentProposals() throws InterruptedException {
        // extract proposers from members map
        HashMap<String, Member> proposers = new HashMap<>();
        for (Member m : members.values()) {
            if (m.config.isProposer) proposers.put(m.config.memberID, m);
        }

        assertTrue(proposers.size() >= 2, "Test requires two or more proposers");

        // each proposer sends a proposal simultaneously (AI suggested use of CountDownLatch)
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

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Proposers failed to send proposals");

        // allow Paxos protocol time to complete
        Thread.sleep(15000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check only one councillor was elected (could be any proposer)
        assertEquals(1, learnedValues.size(), "Learners reported varying learned values");
    }

    /**
     * Test case 2: Paxos implementation works in the case where all M1-M9 have immediate responses to voting queries.
     */
    @Test
    @DisplayName("All M1-M9 have immediate responses to voting queries")
    void testImmediateResponses() throws InterruptedException {
        // disable network delay simulation:
        Message.MAX_DELAY = 0;
        Message.LOSS_CHANCE = 0;

        // reduce simulated delay at all nodes to 0 (sheoak/coorong simulation disabled by default)
        for (Member m : members.values()) {
            m.config.maxDelay = 0;
        }

        Member proposer = members.get("M1"); // propose using M1
        proposer.getProposer().propose(); // M1 proposes self

        // allow Paxos protocol time to complete
        Thread.sleep(10000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check only one councillor was elected and councillor is M1
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M1", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    /**
     * Test case 3: Paxos implementation works when M1 – M9 have responses to voting queries suggested by several
     * profiles (immediate response, small delay, large delay and no response), including when M2 or M3 propose
     * and then go offline.
     */
    @Test
    @DisplayName("Members respond with delays and some proposer goes camping during proposal")
    void testVariableResponsesAndFailures() throws InterruptedException {
        // note that there is network delay/unreliability simulation in place. See Message.java:
        //  - random delay on sends from 0-50ms
        //  - 15% chance of packet loss on sends

        // as per README, all members take on unique traits (including response delays and reliability issues) parsed
        // from member.properties, that match the specification outlined in the assignment description. This is done
        // automatically for all members when the MemberConfig object is instantiated. These delays/no-reply
        // traits are simulated before handling any message.

        // The chance for a member to go to Sheoak cafe or camping in the Coorong is simulated every 1s according
        // to its sheoak/coorong value in member.properties. Call member.startSheoakCoorongSimulation() to start this.

        // While Sheoak cafe is true, member's maxDelay=0 and reliability=1
        // While camping Coorong is true, member's reliability=0.

        // members may randomly go to the Sheoak cafe or camping in the Coorong according to member.properties
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M3 = members.get("M3"); // propose using M3 (may randomly become unreachable)

        // M3 proposes then goes camping
        M3.getProposer().propose();
        // on my machine, 150ms is enough time to finish phase 1 and start phase 2, then go offline. Feel free to
        // play around with this to suit your machine.
        Thread.sleep(150);
        M3.forceCoorong(true, 3000);

        // allow Paxos protocol time to complete
        Thread.sleep(20000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check only one councillor was elected and councillor is M3
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M3", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    /*--------------------- Additional Testing: stress/edge testing ---------------------*/

    /**
     * Additional Testing 1: Very poor network reliability
     */
    @Test
    @DisplayName("Additional Stress Test: Consensus is reached even with severe packet loss and network delays")
    void testPoorNetwork() throws InterruptedException {
        // message sending can be delayed up to 500ms and has a loss chance of 40%
        Message.MAX_DELAY = 500;
        Message.LOSS_CHANCE = 0.4;

        // members M2/M3 may randomly go to the Sheoak cafe or camping in the Coorong
        for (Member m : members.values()) {
            m.startSheoakCoorongSimulation();
        }

        Member M1 = members.get("M1"); // propose using M1
        M1.getProposer().propose();

        Thread.sleep(20000); // sleep for a long time to allow for many rounds

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner)
                .map(m -> m.getLearner().getLearnedValue())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check only one councillor was elected and councillor is M1
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M1", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    @Test
    @DisplayName("Additional Stress Test: Consensus is reached when four members go offline during proposal")
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

        Thread.sleep(5); // allow proposal to start

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

        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check only one councillor was elected and councillor is M1
        assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        if (learnedValues.size() == 1) {
            String learnedValue = learnedValues.iterator().next();
            assertEquals("M1", learnedValue, "Consensus value does not match expected proposal");
        }
    }

    @Test
    @DisplayName("Additional Stress Test: Consensus is NOT reached when five members go offline during proposal")
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

        Thread.sleep(5); // allow proposal to start

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


        // log all values reported by learners
        for (String learnedValue : learnedValues) {
            log.info("Learned Value: " + learnedValue);
        }

        // check NO councillor was elected
        assertEquals(0, learnedValues.size(), "Consensus value/s detected");
    }
}
