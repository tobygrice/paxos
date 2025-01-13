package com.tobygrice.paxos.roles;

import com.tobygrice.paxos.network.Message;
import com.tobygrice.paxos.utils.MemberConfig;
import com.tobygrice.paxos.utils.SimpleLogger;
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
        MemberConfig[] memberConfigs = {
                new MemberConfig("M1", true, true,  true,  "localhost", 5001),
                new MemberConfig("M2", true, true,  true,  "localhost", 5002),
                new MemberConfig("M3", true, true,  true,  "localhost", 5003),
                new MemberConfig("M4", true, true,  false, "localhost", 5004),
                new MemberConfig("M5", true, true,  false, "localhost", 5005),
                new MemberConfig("M6", true, true,  false, "localhost", 5006),
                new MemberConfig("M7", true, true,  false, "localhost", 5007),
                new MemberConfig("M8", true, true, false, "localhost", 5008),
                new MemberConfig("M9", true, true, false, "localhost", 5009)
        };

        // reset network delay variables
        Message.MAX_DELAY = 50;
        Message.LOSS_CHANCE = 0.15;

        // instantiate members and start each one in a new thread using memberExecutor
        for (MemberConfig thisConfig : memberConfigs) {
            // add all other members of network to current config
            for (MemberConfig memberConfig : memberConfigs) {
                thisConfig.addNetworkMember(memberConfig);
            }

            Member member = new Member(thisConfig);

            members.put(member.config.getMemberID(), member);

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
            if (m.config.isProposer()) proposers.put(m.config.getMemberID(), m);
        }

        assertTrue(proposers.size() >= 2, "Test requires two or more proposers");

        // each proposer sends a proposal simultaneously (AI suggested use of CountDownLatch)
        CountDownLatch latch = new CountDownLatch(proposers.size());

        for (Member proposer : proposers.values()) {
            memberExecutor.submit(() -> {
                try {
                    // Propose different values to differentiate proposals
                    proposer.getProposer().propose();
                    log.info(proposer.config.getMemberID() + " proposed");
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
                .filter(m -> m.config.isLearner())
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

        Member proposer = members.get("M1"); // propose using M1
        proposer.getProposer().propose(); // M1 proposes self

        // allow Paxos protocol time to complete
        Thread.sleep(10000);

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner())
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
     * Test case 3: Very poor network reliability
     */
    @Test
    @DisplayName("Additional Stress Test: Consensus is reached even with severe packet loss and network delays")
    void testPoorNetwork() throws InterruptedException {
        // message sending can be delayed up to 500ms and has a loss chance of 40%
        Message.MAX_DELAY = 500;
        Message.LOSS_CHANCE = 0.4;


        Member M1 = members.get("M1"); // propose using M1
        M1.getProposer().propose();

        Thread.sleep(20000); // sleep for a long time to allow for many rounds

        // store all learned values in a set (this line written with AI)
        Set<String> learnedValues = members.values().stream()
                .filter(m -> m.config.isLearner())
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
}
