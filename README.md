# DS Assignment 3 - Tobias Grice a1848962

A note on AI:
 - AI was used for feedback and debugging.
 - AI was used to create the file structure for this project, including suggestions for class structures and inheritance.
 - It was used in some utility files, such as Proposal.java, MemberConfig.java, and SimpleLogger.java.
 - Any AI written code has been thoroughly understood and **is clearly identified in comments.**

## About this Implementation
I have attempted to make a robust and elegant implementation of Paxos that can be easily refactored for use outside the 
specific set of requirements for this assignment. As such, I have implemented a Learner role, even though all members in 
this assignment are learners.

The individual member properties outlined in the assignment specification are handled by parsing information from 
`src/main/resources/member.properties`

Where possible, all network infrastructure code is kept hidden from the Paxos implementation. This is done through the
use of a network package, which provides methods for sending and receiving messages.

## Testing
A comprehensive testing harness is provided in MemberTest.java. This provides evidence that all marking criteria is met,
and expectations for fault tolerance have been exceeded. 

I have run these tests countless times on my machine and I have never seen them fail once. If the tests are failing, it 
is most likely due to sleeping for too long / too little in certain tests. Your machine may be slower or faster than 
mine. Please consider manipulating the time slept in the failing tests based on what you can see in the logs 
(sent to stdout). Its very unlikely you will need to do this, but I am concerned it will be an issue.

## Usage
Two Makefiles are provided that use the Maven wrapper for compilation. **Please change the name of the appropriate 
Makefile for your OS to `Makefile`, and delete the other version.** You can then use the following Make commands:
`make`      - clean compile the project
`make test` - run all tests (see description above)
If you wish to play around with the system, you can manually run individual members (M1-M9) in a terminal. In a
different terminal for each member, run: `make M<number>` where number is an integer from 1-9. e.g. `make M1`. 
This will start running the member in the terminal. If the member is a proposer (M1,2, or 3),  proposals can be 
triggered manually from stdin. Usage instructions are provided to stdout when a proposer member is run.
