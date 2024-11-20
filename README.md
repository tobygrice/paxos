# DS Assignment 3 - Tobias Grice a1848962

A note on AI:
 - AI was used to create the file structure for this project, including suggestions for class structures and inheritance.
 - It was used in some utility files, such as Network.java, logback.xml and MemberConfig.java.
**Any AI assisted code is clearly identified in comments.**
 - It was used for feedback and debugging, including suggestions such as the use of SLF4J with Logback and using a 
Network class to simulate network delay / packet loss. 

## About this Implementation
I have attempted to make a robust and elegant implementation of Paxos that can be reused outside the specific set of
requirements for this assignment. As such, I have implemented a Learner role, even though all nodes in this assignment
are learners.

The member quirks outlined in the assignment specification are handled by parsing information from member.properties.

Where possible, all network infrastructure code is kept hidden from the Paxos implementation. This is done through the
use of a network class, which provides methods for sending and receiving messages. It also defines an interface to handle
received messages after they have been unmarshalled into a Message object. This interface is implemented polymorphically 
by each of the Member role classes. 

I have implemented each member role using polymorphism:
- Member is the abstract base class
- Learner extends Member. All instantiated member objects are learners.
- Acceptor extends Learner. All acceptors are also learners.
- Proposer extends Acceptor. All proposers are also acceptors and learners.
Each class implements the paxos handler interface declared in the Network class.

## Usage
Run member.java with the desired memberID as the only argument. The information for the provided member ID will be read 
from the member.properties file in the resources directory. The member will assume the appropriate role and start listening
for messages.