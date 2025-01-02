# Paxos Protocol - Tobias Grice a1848962

A note on AI:
 - AI was used for feedback and debugging.
 - AI was used to create the file structure for this project, including suggestions for class structures and inheritance.
 - It was used in some utility files, such as Proposal.java, MemberConfig.java, and SimpleLogger.java.
 - **Any AI written code is clearly identified in comments.**

## Testing
A comprehensive testing harness is provided in MemberTest.java.

## Usage
A Makefile is provided that uses the Maven wrapper for compilation. The following Make commands are available:
- `make`      - clean compile the project
- `make test` - run all tests (see description above)

If you wish to play around with the system, you can manually run individual members in a terminal. In a
different terminal for each member, run: `make M<number>` where number is an integer. e.g. `make M1`. 
This will start running the member in the terminal. If the member is a proposer (M1,2, or 3),  proposals can be 
triggered manually from stdin. Usage instructions are provided to stdout when a proposer member is run.
