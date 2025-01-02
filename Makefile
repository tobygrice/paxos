compile:
	./mvnw clean compile

test:
	./mvnw test

M1:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M1"

M2:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M2"

M3:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M3"

M4:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M4"

M5:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M5"

M6:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M6"

M7:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M7"

M8:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M8"

M9:
	./mvnw exec:java -Dexec.mainClass=com.a1848962.paxos.roles.Member -Dexec.args="M9"