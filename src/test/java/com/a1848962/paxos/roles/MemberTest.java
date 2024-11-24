package com.a1848962.paxos.roles;

import com.a1848962.paxos.utils.MemberConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemberTest {
    @Test
    void mainTest() {
        String[] memberIDs = MemberConfig.getProperties().getProperty("members").trim().split(",");
        Member[] members = new Member[memberIDs.length];

        for (int i=0; i<memberIDs.length; i++) {
            MemberConfig thisConfig = new MemberConfig(memberIDs[i]);
            members[i] = new Member(thisConfig);
            members[i].start();
        }
    }
}
