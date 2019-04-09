package activitystreamer.server;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;
import activitystreamer.util.Message;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    // open connections
    private static ArrayList<Connection> connections;
    // list of connection of all connected (or crashed) clients
    private static ArrayList<Connection> clientConnections;
    // set of username of all connected (not crashed) clients
    private static Set<String> clients;
    private static int clientNum;
    private static String id;
    private static String serverAuthSecret = "2vbfh3uu10nbcsbnh9s1b7inko";
    private static boolean term = false;
    private static Listener listener;
    // map users' username to secret, including some of registered users
    private static Map<String, String> users;
    // map ids of all connected (or crashed) servers in the network to load
    private static Map<String, ServerState> servers;
    // map username of active users on this server to the user's buffer
    private static Map<String, Queue<String>> activeUserBuffer;
    // map server connection to username of all active users at that direction
    private static Map<Connection, Set<String>> serverConnections;
    private JSONParser parser = new JSONParser();

    // nic
    private static Connection parentConnection = null;
    private static int reconnectParentCounter = 0;
    private static final int maxReconnectionTimes = 2;
    private static JSONObject grandparentServer = null;
    // work with parentConnection == null together by root server
    private static boolean settingRootBackup = true;


    protected static Control control = null;

    private void updateServerState(String id, int load, Connection con) {
        if (servers.get(id) != null) {
            servers.get(id).update(load, con);
        } else {
            servers.put(id, new ServerState(load, con));
        }
    }

    public static Control getInstance() {
        if(control==null){
            control=new Control();
        }
        return control;
    }

    public Control() {
        // initialize the connections array
        connections = new ArrayList<>();
        clientConnections = new ArrayList<>();
        clients = new HashSet<>();
        clientNum = 0;
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: "+e1);
            System.exit(-1);
        }
        users = new HashMap<>();
        servers = new HashMap<>();
        activeUserBuffer = new HashMap<>();
        serverConnections = new HashMap<>();
        id = Settings.getLocalHostname()+":"+Settings.getLocalPort();
        if (Settings.getRemoteHostname() == null) {
            log.info("setting up server "+
                    Settings.getLocalHostname()+":"+Settings.getLocalPort()+
                    " with secret: "+serverAuthSecret);
        } else {
            serverAuthSecret = Settings.getSecret();
            initiateConnection();
        }
        start();
    }

    public void initiateConnection(){
        // make a connection to another server if remote hostname is supplied
        if(Settings.getRemoteHostname()!=null){
            try {
                outgoingConnection(new Socket(Settings.getRemoteHostname(),
                        Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to "+
                        Settings.getRemoteHostname()+":"+
                        Settings.getRemotePort()+" :"+e);
                System.exit(-1);
            }
        }
    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con,String msg){
        try{
            JSONObject obj = (JSONObject) parser.parse(msg);
            if (!obj.containsKey("command")) {
                con.writeMsg(Message.noKeyInvalidMessage("command"));
                return true;
            }
            if (!(obj.get("command") instanceof String)) {
                con.writeMsg(Message.wrongTypeInvalidMessage("command", "String"));
                return true;
            }
            String command = obj.get("command").toString();
            log.info(command);
            log.info(msg);
            switch (command){
                case "AUTHENTICATE":
                    return authenticateHandler(con,obj);
                case "AUTHENTICATION_FAIL":
                    log.error("authentication failed connecting to "+
                            Settings.getRemoteHostname()+":"+
                            Settings.getRemotePort());
                    return true;
                case "REGISTER":
                    return registerHandler(con,obj);
                case "CLEAR_REGISTERED_USERS":
                    return clearRegisteredUsersHandler();
                case "NEW_USER_REGISTERED":
                    return newUserRegisteredHandler(con,obj,msg);
                case "NEW_LOGIN":
                    return newLoginHandler(con,obj,msg);
                case "LOGOUT":
                    return logoutHandler(con);
                case "LOGIN":
                    return loginHandler(con,obj);
                case "ACTIVITY_MESSAGE":
                    return activityMessageHandler(con, obj);
                case "ACTIVITY_BROADCAST":
                    return activityBroadcastHandler(con, obj, msg);
                case "SERVER_ANNOUNCE":
                    return serverAnnounceHandler(con, obj);
                case "USER_MESSAGE":
                    return userMessageHandler(obj);
                case "USER_LOGOUT":
                    return userLogoutHandler(con, obj, msg);
                case "sendingParentServer":
                    return parentServerHandler(obj);
                case "requestParentServer":
                    return parentServerHandler(obj);
                case "sendingBackupRoot":
                    return backupRootServerHandler(obj);
                default:
                    con.writeMsg(Message.unknownCommandInvalidMessage(command));
                    return true;
            }
        } catch (ParseException e) {
            log.error("received parse exception processing message: " + e);
            con.writeMsg(Message.invalidMessage("JSON parse error while parsing message"));
            return true;
        }
    }

    private void serverBroadcast(String msg) {
        for (Connection serverConnection : serverConnections.keySet()) {
            serverConnection.writeMsg(msg);
        }
    }

    private void serverBroadcastExceptOrigin(Connection origin, String msg) {
        for (Connection serverConnection : serverConnections.keySet()) {
            if (serverConnection.getSocket() != origin.getSocket()) {
                serverConnection.writeMsg(msg);
            }
        }
    }

    private void removeClientConnectionByUsername(String username) {
        Connection clientConnectionToRemove = null;
        for (Connection clientConnection : clientConnections) {
            if (clientConnection.getUsername().equals(username)) {
                clientConnectionToRemove = clientConnection;
                break;
            }
        }
        clientConnections.remove(clientConnectionToRemove);
    }

    private boolean authenticateHandler(Connection con, JSONObject obj){
        if (!obj.containsKey("secret")) {
            con.writeMsg(Message.noKeyInvalidMessage("secret"));
            return true;
        }
        if (!(obj.get("secret") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("secret", "String"));
            return true;
        }
        String tmpId = obj.get("id").toString();
        String secret = obj.get("secret").toString();
        if (secret.equals(serverAuthSecret)) {
            // add the authenticated connection into serverConnections
            con.setServerId(tmpId);
            updateServerState(tmpId, 0, con);
            serverConnections.put(con, new HashSet<>());

            // give the list of registered users on the network to the new server
            con.writeMsg(Message.clearRegisteredUsers());
            for (String username : users.keySet()) {
                con.writeMsg(Message.newUserRegistered(username, users.get(username)));
            }

            //sending my parent server to my each child servers as their grandparentServer
            if (Settings.getRemoteHostname() != null){
                con.writeMsg(Message.sendingParentServer(Settings.getRemoteHostname(),Settings.getRemotePort()));
            }
            else{   //remoteHostname is null iff it is root server
                if (parentConnection == null && settingRootBackup){
                    try{
                        //setting the backupRoot server
                        grandparentServer = Message.recordServer(Settings.getHostnameFromId(obj.get("id").toString()), Settings.getPortFromId(obj.get("id").toString()));
//                        log.debug("Testing!!!!!!!!! con.portnumber: " + grandparentServer.get("port"));
                        settingRootBackup = false;
                    }catch (Exception e){
                        log.error("Something wrong here in authenticateHandler approach: " + e);
                    }
                }
                //sending the backupRoot to each server which is connected with current root server.
//                if (parentConnection == null && grandparentServer != null){
                if (Settings.getRemoteHostname() == null){
                    if (parentConnection != null) {
//                    con.writeMsg(Message.sendingBackupRoot(grandparentServer).toString());
                        con.writeMsg(Message.sendingBackupRoot(
                                Message.recordServer(
                                        Settings.getHostnameFromId(parentConnection.getServerId()),
                                        Settings.getPortFromId(parentConnection.getServerId())
                                )).toString());
                    }
                }
            }
            return false;
        }
        con.writeMsg(Message.authenticationFail(secret));
        return true;
    }

    private boolean registerHandler(Connection con, JSONObject obj){
        if (!obj.containsKey("username")) {
            con.writeMsg(Message.noKeyInvalidMessage("username"));
            return true;
        }
        if (!obj.containsKey("secret")) {
            con.writeMsg(Message.noKeyInvalidMessage("secret"));
            return true;
        }
        if (!(obj.get("username") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("username", "String"));
            return true;
        }
        if (!(obj.get("secret") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("secret", "String"));
            return true;
        }
        //if user has logged in
        if (clientConnections.contains(con)) {
            con.writeMsg(Message.invalidMessage("received REGISTER from a client that has already logged in as " + con.getUsername()));
            return true;
        }
        String username = obj.get("username").toString();
        String secret = obj.get("secret").toString();
        //check users table itself first
        if (users.containsKey(username)) {
            //Register_failed,local
            con.writeMsg(Message.registerFailed(username));
            return true;
        }
        users.put(username, secret);
        serverBroadcast(Message.newUserRegistered(username, secret));
        con.writeMsg(Message.registerSuccess(username));
        return false;
    }

    private boolean clearRegisteredUsersHandler() {
        users.clear();
        return false;
    }

    private boolean newUserRegisteredHandler(Connection con, JSONObject obj, String msg){
        if (!serverConnections.containsKey(con)) {
            con.writeMsg(Message.unauthenticatedServerInvalidMessage("NEW_USER_REGISTERED"));
            return true;
        }
        users.put(obj.get("username").toString(), obj.get("secret").toString());
        serverBroadcastExceptOrigin(con, msg);
        return false;
    }

    private boolean newLoginHandler(Connection con, JSONObject obj, String msg){
        if (!serverConnections.containsKey(con)) {
            con.writeMsg(Message.unauthenticatedServerInvalidMessage("NEW_LOGIN"));
            return true;
        }
        String serverId = obj.get("serverId").toString();
        String username = obj.get("username").toString();

        // if one client logged in at another server, log it out at this server
        if (clients.contains(username)) {
            log.info("User " + username + " logged in at Server " + serverId);
            log.info("Logging " + username + " out...");
            clients.remove(username);
            clientNum --;
        } else {

            for (Connection serverConnection : serverConnections.keySet()) {
                if (serverConnection.getSocket() != con.getSocket()) {
                    serverConnections.get(serverConnection).remove(username);
                }
            }

        }

        if (activeUserBuffer.containsKey(username)) {
            Queue<String> tmpBuffer = activeUserBuffer.get(username);
            if (!tmpBuffer.isEmpty()) {
                con.writeMsg(Message.userMessage(username, tmpBuffer.remove()));
            }
            activeUserBuffer.remove(username);
        }
        serverBroadcastExceptOrigin(con, msg);

        removeClientConnectionByUsername(username);
        return false;
    }

    private boolean loginHandler(Connection con, JSONObject obj){
        String username = obj.get("username").toString();
        if (username.equals("anonymous")) {
            con.writeMsg(Message.loginSuccess(username));
            clientConnections.add(con);
            clientNum ++;
            return false;
        }
        String secret = obj.get("secret").toString();
        if (users.containsKey(username)) {
            if (users.get(username).equals(secret)) {
                // if a crashed client logged back, remove previous connection
                removeClientConnectionByUsername(username);

                con.writeMsg(Message.loginSuccess(username));
                con.setUsername(username);
                con.setSecret(secret);
                clientConnections.add(con);
                clientNum ++;
                clients.add(username);
                if (activeUserBuffer.containsKey(username)) {
                    Queue<String> tmpBuffer = activeUserBuffer.get(username);
                    while (!tmpBuffer.isEmpty()) {
                        con.writeMsg(tmpBuffer.remove());
                    }
                } else {
                    activeUserBuffer.put(username, new LinkedList<>());
                }
                serverBroadcast(Message.newLogin(id, username));
                // remove the user from the user group from other servers
                for (Connection serverConnection : serverConnections.keySet()) {
                    serverConnections.get(serverConnection).remove(username);
                }
                return false;
            }
            con.writeMsg(Message.wrongSecretLoginFailed());
            return true;
        }
        con.writeMsg(Message.noUserLoginFailed(username));
        return true;
    }

    private boolean logoutHandler(Connection con) {
        clientConnections.remove(con);
        clientNum --;
        // if not anonymous user
        String username = con.getUsername();
        if (username != null) {
            activeUserBuffer.remove(username);
            clients.remove(username);
            serverBroadcast(Message.userLogout(username));
        }
        return true;
    }

    private static boolean activityMessageHandler(Connection con, JSONObject obj){
        if (!obj.containsKey("username")){
            con.writeMsg(Message.noKeyInvalidMessage("username"));
            return true;
        }
        if (!obj.containsKey("secret")){
            con.writeMsg(Message.noKeyInvalidMessage("secret"));
            return true;
        }
        if (!obj.containsKey("activity")){
            con.writeMsg(Message.noKeyInvalidMessage("activity"));
            return true;
        }
        if (!(obj.get("username") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("username", "String"));
            return true;
        }
        if (!(obj.get("secret") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("secret", "String"));
            return true;
        }
        if (!(obj.get("activity") instanceof JSONObject)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("activity", "JSONObject"));
            return true;
        }
        // if the user hasn't logged in, the user can't broadcast
        if(!clientConnections.contains(con)){
            con.writeMsg(Message.authenticationFailNotLogin());
            return true;
        }
        String username = obj.get("username").toString();
        String secret = obj.get("secret").toString();
        JSONObject activity = ((JSONObject) obj.get("activity"));
        if (!username.equals("anonymous") &&
                (!(con.getUsername().equals(username) &&
                        con.getSecret().equals(secret)))){
            con.writeMsg(Message.authenticationFailActivityMessage());
            return true;
        }
        activity.put("authenticated_user", username);
        activity.put("id", id);
        String broadcastMsg = Message.activityBroadcast(activity);

        for (Connection serverConnection : serverConnections.keySet()) {
            // if encountered error writing message
            if (serverConnection.writeMsg(broadcastMsg)) {
                for (String tmpUsername : serverConnections.get(serverConnection)) {
                    if (tmpUsername != null) {
                        activeUserBuffer.putIfAbsent(tmpUsername, new LinkedList<>());
                        activeUserBuffer.get(tmpUsername).add(broadcastMsg);
                    }
                }
            }
        }

        for (Connection clientConnection: clientConnections) {
            String tmpUsername = clientConnection.getUsername();
            if (tmpUsername == null) {
                clientConnection.writeMsg(broadcastMsg);
            } else {
                activeUserBuffer.putIfAbsent(tmpUsername, new LinkedList<>());
                activeUserBuffer.get(tmpUsername).add(broadcastMsg);
            }
        }

        return false;
    }

    private boolean activityBroadcastHandler(Connection con, JSONObject obj, String msg){
        if (!obj.containsKey("activity")){
            con.writeMsg(Message.noKeyInvalidMessage("activity"));
            return true;
        }
        if (!(obj.get("activity") instanceof JSONObject)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("activity", "JSONObject"));
            return true;
        }
        if (!serverConnections.containsKey(con)) {
            con.writeMsg(Message.unauthenticatedServerInvalidMessage("ACTIVITY_BROADCAST"));
            return true;
        }
        serverBroadcastExceptOrigin(con, msg);
        for (Connection clientConnection: clientConnections) {
            clientConnection.writeMsg(msg);
        }
        return false;
    }

    private boolean serverAnnounceHandler(Connection con, JSONObject obj) {
        if (!obj.containsKey("id")) {
            con.writeMsg(Message.noKeyInvalidMessage("id"));
            return true;
        }
        if (!obj.containsKey("load")) {
            con.writeMsg(Message.noKeyInvalidMessage("load"));
            return true;
        }
        if (!(obj.get("id") instanceof String)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("id", "String"));
            return true;
        }
        if (!(obj.get("load") instanceof Long)) {
            con.writeMsg(Message.wrongTypeInvalidMessage("load", "int"));
            return true;
        }

        //if you are my parent, i have to record your parentServer as my grandPa Server.
        if (obj.containsKey("parentConnection")){
            if (parentConnection.getServerId().equals(obj.get("id").toString())){
//                log.debug("there's no bug in the comparison between parent and id in announce handler approach");
                grandparentServer = (JSONObject) obj.get("parentConnection");
            }
        }

        if (serverConnections.containsKey(con)) {
            String tmpId = obj.get("id").toString();
            int tmpLoad = ((Long) obj.get("load")).intValue();
            HashSet<String> tmpClients = Settings.stringToHashSet(obj.get("clients").toString());

            // redirect to achieve load balance
            while (clientNum - tmpLoad > 1) {
                Connection clientConnection = null;
                for (Connection tmpClientConnection : clientConnections) {
                    if (clients.contains(tmpClientConnection.getUsername())) {
                        clientConnection = tmpClientConnection;
                        break;
                    }
                }
                String clientUsername = clientConnection.getUsername();
                tmpClients.add(clientUsername);
                tmpLoad ++;
                activeUserBuffer.remove(clientUsername);
                clients.remove(clientUsername);
                clientNum --;
                clientConnection.writeMsg(Message.redirect(Settings.getHostnameFromId(tmpId),
                        Settings.getPortFromId(tmpId)));
                clientConnections.remove(clientConnection);
            }

            updateServerState(tmpId, tmpLoad, con);
            serverConnections.get(con).addAll(tmpClients);
            obj.put("load", tmpLoad);
            obj.put("clients", tmpClients.toString());
            serverBroadcastExceptOrigin(con, obj.toString());
            return false;
        } else {
            con.writeMsg(Message.unauthenticatedServerInvalidMessage("SERVER_ANNOUNCE"));
            return true;
        }
    }

    private boolean userMessageHandler(JSONObject obj) {
        String username = obj.get("username").toString();
        String message = obj.get("message").toString();
        for (Connection clientConnection : clientConnections) {
            if (clientConnection.getUsername().equals(username)) {
                clientConnection.writeMsg(message);
                return false;
            }
        }
        if (!activeUserBuffer.containsKey(username) && username != null) {
            activeUserBuffer.put(username, new LinkedList<>());
        }
        activeUserBuffer.get(username).add(message);
        return false;
    }

    private boolean userLogoutHandler(Connection con, JSONObject obj, String msg) {
        String username = obj.get("username").toString();
        Set<String> userSet = serverConnections.get(con);
        if (userSet != null) {
            userSet.remove(username);
        } else {
            return true;
        }
        serverBroadcastExceptOrigin(con, msg);
        return false;
    }

    // nic
    private boolean parentServerHandler(JSONObject obj){
//        log.debug(obj.toString());
//        log.debug("hostName: "+tempHostname + "---- portNUmber: "+ tempPort);
        if (obj != null){
            try{
                String tempHostname = obj.get("hostname").toString();
                int tempPort = Integer.parseInt(obj.get("port").toString());
//                int tempPort = ((Long)obj.get("port")).intValue();
                grandparentServer = Message.recordServer(tempHostname, tempPort);
                log.debug("after updating grandparentServer -----: "+ grandparentServer.toString());
//                log.debug("---------------------------\nGrandPa.PortNUmber: " + grandparentServer.get("port").toString());
            }catch (Exception e){
                log.error("Exception here in parentServerHandler approach: "+ e.getStackTrace());
            }
        }
        return false;
    }

    private boolean backupRootServerHandler (JSONObject obj){
        if (obj != null){
            try{
                grandparentServer = (JSONObject) obj.get("backupRoot");
                log.debug("after updating BackupRootServer -----: "+ grandparentServer.toString());
            }catch (Exception e){
                log.error("Exception here in backupRootServerHandler approach: "+ e.getStackTrace());
            }
        }
        return false;
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con){
        if(!term){
            connections.remove(con);
        }
    }

    /*
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException{
        log.debug("incoming connection: "+Settings.socketAddress(s));
        log.debug("incoming connection: "+s.getLocalSocketAddress());
        Connection c = new Connection(s);
        connections.add(c);
        return c;
    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException{
        log.debug("outgoing connection: "+Settings.socketAddress(s));
        log.debug("outgoing connection: "+s.getLocalSocketAddress());
        Connection c = new Connection(s);
        c.setServerId(Settings.getRemoteHostname()+":"+Settings.getRemotePort());
        connections.add(c);
        serverConnections.put(c, new HashSet<>());
        parentConnection = c;
        c.writeMsg(Message.authenticate(id, serverAuthSecret));
        return c;

    }

    @Override
    public void run(){
        log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
        while(!term){
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if(!term){
                term=doActivity();
                for (Connection clientConnection : clientConnections) {
                    String tmpUsername = clientConnection.getUsername();
                    if (tmpUsername != null) {
                        activeUserBuffer.putIfAbsent(tmpUsername, new LinkedList<>());
                        Queue<String> tmpQueue = activeUserBuffer.get(tmpUsername);
                        if (tmpQueue != null) {
                            while (!tmpQueue.isEmpty()) {
                                if (!clientConnection.writeMsg(tmpQueue.peek())) {
                                    tmpQueue.remove();
                                } else {
                                    clients.remove(tmpUsername);
                                    clientNum--;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        log.info("closing "+connections.size()+" connections");
        // clean up
        for(Connection connection : connections){
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    // every 5 seconds
    public boolean doActivity(){
        // remove closed server connection without any clients
        for (Connection serverConnection : serverConnections.keySet()) {
            if (!serverConnection.isOpen() && serverConnections.get(serverConnection).size() == 0) {
                serverConnections.remove(serverConnection);
            }
        }

        log.info("------------------------------DO ACTIVITY LOG------------------------------");
        log.info("the number of all connected (or crashed) servers in the network: " + servers.size());
        log.info("the number of connected (or crashed) servers: " + serverConnections.size());
        log.info("the number of all registered users in the network: " + users.size());
        log.info("the number of connected (or crashed) clients: " + clientConnections.size());
        log.info("the number of connected (running) clients with username: " + clients.size());
        log.info("clients' username: " + clients);

        for (String username : activeUserBuffer.keySet()) {
            log.info(username + "'s buffer size: " + activeUserBuffer.get(username).size());
        }

        for (Connection con : serverConnections.keySet()) {
            log.info(Settings.socketAddress(con.getSocket()) + ": " + serverConnections.get(con));
        }

        log.info("---------------------------------------------------------------------------");

        serverBroadcast(Message.serverAnnounce(id, clientNum, clients, parentConnection));

        // nic
        if (parentConnection != null)
            log.info("parent connection:" + parentConnection.getServerId().toString());
        if (grandparentServer != null)
            log.info("grandPaServer Port: "+ grandparentServer.toString());
        if (Settings.getRemoteHostname() == null){
            grandparentServer = Message.recordServer(Settings.getLocalHostname(), Settings.getLocalPort());
            log.info("Settings.getRemoteHostname is null!!");
            for (Connection con: serverConnections.keySet()){//确保设置可用的root的childServer作为大儿子
                if (con.isOpen()){
                    log.debug("con.isOpen is TRUE!");
                    parentConnection = con;
                    log.debug("con.isOpen is TRUE! and ParentConnection: " + parentConnection.getServerId().toString());
                    break;
                }
                else{
                    log.debug("con.isOpen is FALSE!");
                }
            }
        }
        if(Settings.getRemoteHostname()!=null) {
            log.info("Settings.getRemoteHostname is : " + Settings.getRemoteHostname());
            if (!parentConnection.isOpen()) {
//                log.debug("\n\n\n\n");
                try {
                    parentConnection = new Connection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                    parentConnection.setServerId(Settings.getRemoteHostname()+":"+Settings.getRemotePort());
                    connections.add(parentConnection);
                    serverConnections.put(parentConnection, new HashSet<>());
                    parentConnection.writeMsg(Message.authenticate(id, serverAuthSecret));
                    reconnectParentCounter = 0;
                } catch (IOException e) {
                    reconnectParentCounter ++;
                    log.error("failed to re-establish connection to parent connection "+
                            Settings.getRemoteHostname()+":"+
                            Settings.getRemotePort()+" :"+e);
                }
                if (reconnectParentCounter >= maxReconnectionTimes) {
                    reconnectParentCounter = 0;
//                    log.debug("111: "+ grandparentServer);
                    try{
                        Settings.setRemoteHostname(grandparentServer.get("hostname").toString());
//                        Settings.setRemotePort(((Long)grandparentServer.get("port")).intValue());
                        Settings.setRemotePort(Integer.parseInt(grandparentServer.get("port").toString()));
//                        log.debug("222:" + grandparentServer);
                        if (!Settings.getRemoteHostname().equals(Settings.getLocalHostname()) || Settings.getRemotePort() != Settings.getLocalPort()){    //do not reconnect with itself
                            log.debug("Remote port: 1111111:  " + Settings.getRemotePort());
                            initiateConnection();
                            log.debug("Remote port: 222222:  " + Settings.getRemotePort());
                        }
                        if (Settings.getRemoteHostname() != null){
                            log.debug("\nin outside\nlp: " + Settings.getLocalPort() +" ;rp: "+Settings.getRemotePort() + " ; lh: "+ Settings.getLocalHostname() + " ; rh: " + Settings.getRemoteHostname());
                            if (Settings.getRemoteHostname().equals(Settings.getLocalHostname()) && Settings.getRemotePort() == Settings.getLocalPort()){
                                //make ensure root.p = root's first available child.
                                log.debug("\nComing inside\nlp: " + Settings.getLocalPort() +" ;rp: "+Settings.getRemotePort()+ " ; lh: "+ Settings.getLocalHostname() + " ; rh: " + Settings.getRemoteHostname());
                                for (Connection con: serverConnections.keySet()){
                                    if (con.isOpen()){
                                        parentConnection = con;
                                    }
                                }
                                //make ensure root.g = root
                                grandparentServer = Message.recordServer(Settings.getLocalHostname(), Settings.getLocalPort());
                                Settings.setRemoteHostname(null);   //setting root's remote hostname as null
                            }
                        }
                        //update parentServer and grandparentServer
//                        parentConnection = new Connection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                        log.debug("Remote port: 333333:  " + Settings.getRemotePort());
//                        parentConnection.writeMsg(Message.requestParentServer().toString());    //sending request to update grandparentServer

                        //update backupRootServer
                        if (!parentConnection.isOpen()){
                            //update information of new root
                            Settings.setRemoteHostname(null);
                            //find the new backupRootServer in the servers which is connected with current new root server for update backupRootServer
                            for (Connection conn: serverConnections.keySet()){
                                boolean temp = false;
                                if (conn.isOpen()){
                                    //setting the new backupRoot server in the current root server.
                                    //formula: Server.grandpa = Server.parent.parent
//                                    grandparentServer = Message.recordServer(
//                                            Settings.getHostnameFromId(conn.getServerId()),
//                                            Settings.getPortFromId(conn.getServerId()));
                                    parentConnection = conn;
                                    temp = true;
                                }
                                if (temp)
                                    break;
                            }
                            //sending the new grandparentServer to each servers(except crashed servers)
                            for (Connection conn : serverConnections.keySet()) {
                                if (conn.isOpen()){
                                    conn.writeMsg(Message.serverAnnounce(id,clientNum,clients, parentConnection));
                                }
                            }
                        }


                    } catch (Exception e){
                        log.error("failed to re-establish connection to parent connection "+
                                Settings.getRemoteHostname()+":"+
                                Settings.getRemotePort()+" :"+e);
                    }
                }
            }
        }
        return false;
    }

    public final void setTerm(boolean t){
        term=t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }
}
