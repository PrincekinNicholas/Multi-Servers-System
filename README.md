# Multi-Servers-Communication-System

A simple multi-server system for broadcasting activity objects between a number of clients.

The multi-server system will:
- load balance client requests over the servers, using a redirection mechanism to ask clients
to reconnect to another server.
- allow clients to register a username and secret, that can act as an authentication
mechanism. Clients can login and logout as either anonymous or using a username/secret
pair.
- allow clients to broadcast an activity object to all other clients connected at the time


### HOW TO COMPILE SOURCE CODE?
Open terminal under "skeleton" folder, use command "mvn package" to compile source code into .jar files. You can find the target .jar files under the directory skeleton/target.

### HOW TO START CLIENT AND SERVER?
Using commandLine command to access the current folder that store the .jar files. In the next step, enter command "java -jar .\Server.jar" with some parameters to initialize a server. The command "java -jar .\Server.jar --help" will help you to write correct parameters. As for Client, the steps are similar. The only difference is to change the name of file to Client.jar in the commandLine.

### HOW TO SET UP CONNECTION BETWEEN SERVERS?
Under the circumstance that the first server is initialized. In the next step, the user of the system would like add another servers into the system. To do so, remote hostname, remote port number, and the secret that is used as authentication method between servers have to be entered when servers are set to make connections with each other. If remote hostname is not provided, the system will think the user of the system attempts to start a new network system. To clarify on the secret that is used by the servers, it is set up at the point when the first sever is established and the secret will be printed in the terminal.

### HOW TO USE ACTIVITY_MESSAGE?
As for ACTIVITY_MESSAGE, all the Keys "command", "username", "secret", "activity" have to be entered into the message sent from client to server when publishing an activity object.

### EXPLAINATION ON REDIRECT
It is noteworthy that, for REDIRECT function, our GUI will only display the latest message received and clean up previous message displayed. That is to say, the message during processing the action will not be displayed to end-users of GUI. According to project specification document, if REDIRECT happened, we will close the connection with the client and automatically make a new connection to the assigned server  in the system as specified in the REDIRECT message. As such, client will receive only one login successful or fail message rather than Redirect message. But we will print information about redirect in the terminal.

