# DS Assignment 3 - Tobias Grice a1848962

A note on AI:
 - AI was used for feedback and debugging.
 - AI was used to create the file structure for this project, including suggestions for class structures and inheritance.
 - It was used in some utility files, such as Network.java, Proposal.java and MemberConfig.java.
**Any AI assisted code is clearly identified in comments.**

## About this Implementation
I have attempted to make a robust and elegant implementation of Paxos that can be easily refactored for use outside the 
specific set of requirements for this assignment. As such, I have implemented a Learner role, even though all nodes in 
this assignment are learners.

The individual member properties outlined in the assignment specification are handled by parsing information from 
src/main/resources/member.properties

Where possible, all network infrastructure code is kept hidden from the Paxos implementation. This is done through the
use of a network class, which provides methods for sending and receiving messages. It also defines an interface to handle
received messages after they have been unmarshalled into a Message object. This interface is implemented polymorphically 
by each of the Member role classes. 

I have implemented each member role using polymorphism:
- Member is the abstract base class
- Learner extends Member. All defined member objects are learners.
- Acceptor extends Learner. All acceptors are also learners.
- Proposer extends Acceptor. All proposers are also acceptors and learners.
Each class implements the paxos handler interface declared in the Network class.


MARKING:
10 points - Paxos implementation works when two councillors send voting proposals at the same time
30 points – Paxos implementation works in the case where all M1-M9 have immediate responses to voting queries
30 points – Paxos implementation works when M1 – M9 have responses to voting queries suggested by several profiles 
(immediate response, small delay, large delay and no response), including when M2 or M3 propose and then go offline
20 points – Testing harness for the above scenarios + evidence that they work (in the form of printouts)

TO-DO:
- Create a single program that initialises all 9 members as objects so it can manipulate them individually. Will require
refactoring of roles. Will also need to add memberID to the start of every stdout for identification.
- Go through and ensure all AI code is identified
- Reimplement unreliable network simulation and test
- Rigorous testing of sheoak/coorong behaviours
- Finalise README (especially usage instructions)

## Usage
Run member.java with the desired memberID as the only argument. The information for the provided member ID will be read 
from the member.properties file in the resources directory. The member will assume the appropriate role and start listening
for messages.