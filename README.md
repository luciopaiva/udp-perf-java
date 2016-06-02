
# How to Run

To run it from the command line:

- Client


    java -cp "<PATH-TO-COMMONS-CLI>/commons-cli-1.3.1.jar:<PATH-TO-UDP-PERF-JAVA>/target/classes" Client -s 100 -t 180 -p 23456

- Server


    java -cp "<PATH-TO-COMMONS-CLI>/commons-cli-1.3.1.jar:<PATH-TO-UDP-PERF-JAVA>/target/classes" Server -s 1400 -p 23456

There's also two shell scripts to start both client and server, but they have to be edited to point to the correct directories first.

Parameters are:

* -s datagram size, in bytes
* -t how long should the test run, in seconds (*client only*)
* -p server port
