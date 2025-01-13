# Paxos Protocol - Toby Grice

## Testing
A testing harness is provided in MemberTest.java. A Makefile is provided that uses the Maven wrapper for compilation. 
The following Make commands are available:
- `make`      - clean compile the project
- `make test` - run all tests

## Usage
To instantiate a Paxos member, you must provide it with a configuration. This configuration will contain a member ID,
roles, address, port number, and a map of all other members in the network. The constructor signatures of MemberConfig
and Member are as follows:

```public MemberConfig(String id, boolean isLearner, boolean isAcceptor, boolean isProposer, String address, int port)```

```public Member(MemberConfig config)```

Below is an example of a new proposer member being instantiated, started, and sending a proposal.
```
MemberConfig config = new MemberConfig("M1", true, true, true, "localhost", 5001);
config.addNetworkMember("M2", true, true, true, "localhost", 5002);
config.addNetworkMember("M3", true, true, false, "localhost", 5003);
config.addNetworkMember("M4", true, true, false, "localhost", 5004);
config.addNetworkMember("M5", true, false, false, "localhost", 5005);

Member m1 = new Member(config);
m1.start()
m1.propose("VALUE")
```