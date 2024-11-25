// PaxosTestSuite.java
package com.a1848962.paxos.roles;

import com.a1848962.paxos.network.Message;
import com.a1848962.paxos.roles.Member;
import com.a1848962.paxos.utils.MemberConfig;
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
    private final List<Member> members = new ArrayList<>();
    private final ExecutorService memberExecutor = Executors.newCachedThreadPool();
    private final SimpleLogger log = new SimpleLogger("MEMBER-TEST");

    @BeforeEach
    void setup() {
        String[] memberIDs = {"M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9"};

        members.clear();
        for (String memberID : memberIDs) {
            MemberConfig thisConfig = new MemberConfig(memberID);
            Member member = new Member(thisConfig);
            members.add(member);
            memberExecutor.submit(() -> {
                member.start(false); // Do not start stdin listener during tests
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
        for (Member member : members) {
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
    @Nested
    @DisplayName("Concurrent Proposals Test")
    class ConcurrentProposalsTest {

        @Test
        @DisplayName("Two proposers send proposals simultaneously")
        void testConcurrentProposals() throws InterruptedException {
            // Identify proposers based on member.properties (M1, M2, M3)
            List<Member> proposers = members.stream()
                    .filter(m -> m.config.isProposer)
                    .collect(Collectors.toList());

            assertTrue(proposers.size() >= 2, "At least two proposers are required for this test.");

            // Each proposer sends a proposal simultaneously
            CountDownLatch latch = new CountDownLatch(proposers.size());

            for (Member proposer : proposers) {
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
            Thread.sleep(10000);

            // Verify that a consensus was reached
            Set<String> learnedValues = members.stream()
                    .filter(m -> m.config.isLearner)
                    .map(m -> m.getLearner().getLearnedValue())
                    .collect(Collectors.toSet());

            for (String learnedValue : learnedValues) {
                log.info("Learned Value: " + learnedValue);
            }
            assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
        }
    }

    /**
     * Test case 2: Paxos works when all M1-M9 have immediate responses to voting queries.
     */
    @Nested
    @DisplayName("Immediate Responses Test")
    class ImmediateResponsesTest {

        @Test
        @DisplayName("All M1-M9 respond immediately to voting queries")
        void testImmediateResponses() throws InterruptedException {
            // Adjust maxDelay for all members to 0 for immediate responses
            for (Member member : members) {
                member.config.maxDelay = 0;
            }

            // Identify the primary proposer (e.g., M1)
            Optional<Member> optionalProposer = members.stream()
                    .filter(m -> m.config.isProposer)
                    .findFirst();

            assertTrue(optionalProposer.isPresent(), "No proposer found for the test.");

            Member proposer = optionalProposer.get();

            // Proposer sends a proposal
            proposer.getProposer().propose("ImmediateConsensus");

            // Allow some time for consensus to be reached
            Thread.sleep(2000);

            // Verify that a consensus was reached
            Set<String> learnedValues = members.stream()
                    .filter(m -> m.config.isLearner)
                    .map(m -> m.getLearner().getLearnedValue())
                    .collect(Collectors.toSet());

            assertEquals(1, learnedValues.size(), "Multiple consensus values detected");
            String learnedValue = learnedValues.iterator().next();
            System.out.println("Learned Value: " + learnedValue);
            assertEquals("ImmediateConsensus", learnedValue, "Consensus value mismatch");
        }
    }

    /**
     * Test case 3: Paxos works when M1 – M9 have varied response behaviors, including delays and offline states.
     */
    @Nested
    @DisplayName("Variable Responses and Failures Test")
    class VariableResponsesTest {

        @Test
        @DisplayName("Members respond with delays and some proposers go offline during proposals")
        void testVariableResponsesAndFailures() throws InterruptedException {
            // Simulate varied response behaviors as per member.properties
            // M2 has reliability=0.8, chanceSheoak=0.2 (may have delays or become unreachable)
            // M3 has coorong=0.1 (may become completely inaccessible)

            // Identify proposers (M1, M2, M3)
            List<Member> proposers = members.stream()
                    .filter(m -> m.config.isProposer)
                    .collect(Collectors.toList());

            assertFalse(proposers.isEmpty(), "No proposers found for the test.");

            // Each proposer sends a proposal
            CountDownLatch latch = new CountDownLatch(proposers.size());

            for (Member proposer : proposers) {
                memberExecutor.submit(() -> {
                    try {
                        String proposalValue = proposer.config.memberID + "_VariedValue";
                        proposer.getProposer().propose(proposalValue);
                        System.out.println(proposer.config.memberID + " proposed " + proposalValue);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all proposals to be sent
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Proposers did not send proposals in time");

            // Allow ample time for consensus to be reached considering delays and potential failures
            Thread.sleep(7000);

            // Verify that a consensus was reached among active members
            Set<String> learnedValues = members.stream()
                    .filter(m -> m.config.isLearner)
                    .map(m -> m.getLearner().getLearnedValue())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            assertTrue(learnedValues.size() <= proposers.size(), "Unexpected number of consensus values");

            for (String value : learnedValues) {
                System.out.println("Learned Value: " + value);
                assertTrue(
                        members.stream().anyMatch(m -> m.config.memberID.equals(value)),
                        "Learned value is not a valid member ID: " + value
                );
            }
        }
    }
}