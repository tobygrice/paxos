package com.a1848962.paxos.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemberConfigTest {

    @Test
    void testMemberWithDefaults() {
        // Assuming M4 has no specific overrides
        MemberConfig config = new MemberConfig("M4");

        assertEquals("localhost", config.address, "Default address should be 'localhost'");
        assertEquals(5004, config.port, "M4 port should be 5004");
        assertFalse(config.isProposer, "M4 should not be a proposer");
        assertEquals(0.1, config.maxDelay, "Default maxDelay should be 0.1");
        assertEquals(1, config.reliability,"Default reliability should be 1");
        assertEquals(0, config.chanceSheoak, "Default sheoak should be 0");
        assertEquals(0, config.chanceCoorong, "Default coorong should be 0");
    }

    @Test
    void testMemberWithOverrides() {
        // M2 has specific overrides
        MemberConfig config = new MemberConfig("M2");

        assertEquals("localhost", config.address, "Default address should be 'localhost'");
        assertEquals(5002, config.port, "M2 port should be 5002");
        assertTrue(config.isProposer, "M2 should be a proposer");
        assertEquals(0.2, config.maxDelay, 0.0001, "M2 maxDelay should be 0.2");
        assertEquals(0.7, config.reliability,"Default reliability should be 1");
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

        String expectedString = "MemberConfig{id=4, address='localhost', port=5004, isProposer=false, maxDelay=0.1, reliability=1.0, chanceSheoak=0.0, chanceCoorong=0.0}";
        String actualString = config.toString();

        assertEquals(expectedString, actualString, "toString method should return the expected string");
    }

}