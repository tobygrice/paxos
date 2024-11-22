package com.a1848962.paxos.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemberConfigTest {

    @Test
    void testMemberWithDefaults() {
        // test for member with no specific overrides
        MemberConfig config = new MemberConfig("M4");

        assertEquals("localhost", config.address, "Default address should be 'localhost'");
        assertEquals(5004, config.port, "M4 port should be 5004");
        assertFalse(config.isProposer, "Node should not be PROPOSER by default");
        assertTrue(config.isAcceptor, "Node should be ACCEPTOR by default");
        assertTrue(config.isLearner, "Node should be LEARNER by default");
        assertEquals(100, config.maxDelay, "Default maxDelay should be 100ms");
        assertEquals(1, config.reliability,"Default reliability should be 1");
        assertEquals(0, config.chanceSheoak, "Default sheoak should be 0");
        assertEquals(0, config.chanceCoorong, "Default coorong should be 0");
    }

    @Test
    void testConnectionInfo() {
        // test for member with no specific overrides
        MemberConfig config = new MemberConfig("M4");

        assertEquals(5001, config.network.get("M1").port, "M1 port should be 5001");
        assertEquals("localhost", config.network.get("M1").address, "M1 address should be localhost");
        assertEquals(5009, config.network.get("M9").port, "M9 port should be 5009");
        assertEquals("localhost", config.network.get("M1").address, "M9 address should be localhost");
        assertNull(config.network.get("M4"), "Self should not be in network map");
    }

    @Test
    void testMemberWithOverrides() {
        // M2 has specific overrides
        MemberConfig config = new MemberConfig("M2");

        assertEquals("localhost", config.address, "Default address should be 'localhost'");
        assertEquals(5002, config.port, "M2 port should be 5002");
        assertTrue(config.isProposer, "Node should be specified PROPOSER");
        assertTrue(config.isAcceptor, "Node should be ACCEPTOR by default");
        assertTrue(config.isLearner, "Node should be LEARNER by default");
        assertEquals(200, config.maxDelay, "M2 maxDelay should be 200ms");
        assertEquals(0.8, config.reliability,"M2's reliability should be 0.8");
        assertEquals(0.2, config.chanceSheoak, "M2 sheoak should be 0.2");
        assertEquals(0, config.chanceCoorong, "Default coorong should be 0");
    }

    @Test
    void testInvalidMemberIdThrowsException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new MemberConfig("fail"); // unexpected memberID format
        });

        String expectedMessage = "Invalid memberID format. Expected format: positive integer preceded by 'M' (e.g., M1, M2).";
        String actualMessage = exception.getMessage();

        assertEquals(expectedMessage, actualMessage, "Exception message should indicate missing default property");
    }

    @Test
    void testToString() {
        MemberConfig config = new MemberConfig("M4");

        String expectedString = "MemberConfig{id=M4, address='localhost', port=5004, isProposer=false, isAcceptor=true, isLearner=true, maxDelay=100, reliability=1.0, chanceSheoak=0.0, chanceCoorong=0.0}";
        String actualString = config.toString();

        assertEquals(expectedString, actualString, "toString method should return the expected string");
    }

}