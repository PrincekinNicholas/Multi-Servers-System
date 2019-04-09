package activitystreamer.util;

import activitystreamer.server.Connection;
import org.json.simple.JSONObject;

import java.util.Set;

public class Message {

    public static String invalidMessage(String info){
        JSONObject obj = new JSONObject();
        obj.put("command", "INVALID_MESSAGE");
        obj.put("info", info);
        return obj.toString();
    }

    public static String noKeyInvalidMessage(String key){
        return invalidMessage("the message must contain non-null key " + key);
    }

    public static String wrongTypeInvalidMessage(String key, String type){
        return invalidMessage("the type of key "+key+" must be "+type);
    }

    public static String unknownCommandInvalidMessage(String command){
        return invalidMessage("unknown command: "+command);
    }

    public static String unauthenticatedServerInvalidMessage(String command){
        return invalidMessage("received " + command + " from an unauthenticated server");
    }

    public static String authenticationFail(String secret){
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATION_FAIL");
        obj.put("info", "the supplied secret is incorrect: " + secret);
        return obj.toString();
    }

    public static String authenticationFailActivityMessage(){
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATION_FAIL");
        obj.put("info","username and/or secret is incorrect");
        return obj.toString();
    }

    public static String authenticationFailNotLogin(){
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATION_FAIL");
        obj.put("info", "you have to log in first");
        return obj.toString();
    }

    public static String loginSuccess(String userName){
        JSONObject obj = new JSONObject();
        obj.put("command","LOGIN_SUCCESS");
        obj.put("info","logged in as user " + userName);
        return obj.toString();
    }

    public static String wrongSecretLoginFailed(){
        JSONObject obj = new JSONObject();
        obj.put("command","LOGIN_FAILED");
        obj.put("info","attempt to login with wrong secret");
        return obj.toString();
    }

    public static String noUserLoginFailed(String userName){
        JSONObject obj = new JSONObject();
        obj.put("command","LOGIN_FAILED");
        obj.put("info",userName+" is not registered with the system");
        return obj.toString();
    }

    public static String registerFailed(String userName){
        JSONObject obj = new JSONObject();
        obj.put("command","REGISTER_FAILED");
        obj.put("info",userName + " is already registered with the system");
        return obj.toString();
    }

    public static String registerSuccess(String userName){
        JSONObject obj = new JSONObject();
        obj.put("command","REGISTER_SUCCESS");
        obj.put("info","register success for " + userName);
        return obj.toString();
    }

    public static String serverAnnounce(String id, int load, Set<String> clients, Connection parentConnection){
        JSONObject obj = new JSONObject();
        obj.put("command", "SERVER_ANNOUNCE");
        obj.put("id", id);
        obj.put("load", load);
        obj.put("clients", clients.toString());
        if (parentConnection != null && parentConnection.isOpen())
            obj.put("parentConnection",recordServer(Settings.getHostnameFromId(parentConnection.getServerId()), Settings.getPortFromId(parentConnection.getServerId())));
        return obj.toString();
    }

    public static String anonymousLogin(){
        JSONObject obj = new JSONObject();
        obj.put("command", "LOGIN");
        obj.put("username", "anonymous");
        return obj.toString();
    }

    public static String register(String username, String secret){
        JSONObject obj = new JSONObject();
        obj.put("command", "REGISTER");
        obj.put("username", username);
        obj.put("secret", secret);
        return obj.toString();
    }

    public static String login(String username, String secret){
        JSONObject obj = new JSONObject();
        obj.put("command", "LOGIN");
        obj.put("username", username);
        obj.put("secret", secret);
        return obj.toString();
    }

    public static String authenticate(String id, String secret){
        JSONObject obj = new JSONObject();
        obj.put("command", "AUTHENTICATE");
        obj.put("id", id);
        obj.put("secret", secret);
        return obj.toString();
    }

    public static String redirect(String hostname, int portNumber){
        JSONObject obj = new JSONObject();
        obj.put("command","REDIRECT");
        obj.put("hostname",hostname);
        obj.put("port",portNumber);
        return obj.toString();
    }

    public static String activityBroadcast(JSONObject activity){
        JSONObject obj = new JSONObject();
        obj.put("command","ACTIVITY_BROADCAST");
        obj.put("activity",activity);
        return obj.toString();
    }

    public static String userMessage(String username, String message){
        JSONObject obj = new JSONObject();
        obj.put("command", "USER_MESSAGE");
        obj.put("username", username);
        obj.put("message", message);
        return obj.toString();
    }

    public static String userLogout(String username){
        JSONObject obj = new JSONObject();
        obj.put("command", "USER_LOGOUT");
        obj.put("username", username);
        return obj.toString();
    }

    public static String newUserRegistered(String username, String secret){
        JSONObject obj = new JSONObject();
        obj.put("command", "NEW_USER_REGISTERED");
        obj.put("username", username);
        obj.put("secret", secret);
        return obj.toString();
    }

    public static String newLogin(String serverId, String username) {
        JSONObject obj = new JSONObject();
        obj.put("command", "NEW_LOGIN");
        obj.put("serverId", serverId);
        obj.put("username", username);
        return obj.toString();
    }

    public static String clearRegisteredUsers(){
        JSONObject obj = new JSONObject();
        obj.put("command", "CLEAR_REGISTERED_USERS");
        return obj.toString();
    }


    public static JSONObject recordServer(String hostname, int portNumber){
        JSONObject obj = new JSONObject();
        obj.put("hostname", hostname);
        obj.put("port", portNumber);
        return obj;
    }

    public static String sendingParentServer(String hostname, int portNumber){
        JSONObject obj = new JSONObject();
        obj.put("command","sendingParentServer");
        obj.put("hostname", hostname);
        obj.put("port", portNumber);
        return obj.toString();
    }

    public static JSONObject requestParentServer(){
        JSONObject obj = new JSONObject();
        obj.put("command","requestParentServer");
        return obj;
    }

    public static JSONObject sendingBackupRoot(JSONObject backupRoot){
        JSONObject obj = new JSONObject();
        obj.put("command", "sendingBackupRoot");
        obj.put("backupRoot",backupRoot);
        return obj;
    }

}
