package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import activitystreamer.util.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean open = false;
    private JSONParser parser = new JSONParser();
    private String secret;

	public static ClientSkeleton getInstance(){
	    if (clientSolution == null) {
	        clientSolution = new ClientSkeleton();
	    }
	    return clientSolution;
	}
	
	public ClientSkeleton(){
	    try {
            // Create a stream socket and connect it to the server as specified in the Settings
            socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
            log.info("connection with server established");

            in = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream())));
            out = new PrintWriter(new DataOutputStream(socket.getOutputStream()), true);
        } catch (IOException e) {
            log.error("received exception creating stream socket " + e);
        }
        open = true;
	    if (Settings.getUsername().equals("anonymous")) {
	        out.println(Message.anonymousLogin());
        } else {
	        if (Settings.getSecret() == null) {
	            secret = Settings.nextSecret();
	            out.println(Message.register(Settings.getUsername(), secret));
	            log.info("registering user \"" + Settings.getUsername() + "\" with secret \"" + secret + "\"");
            } else {
	            out.println(Message.login(Settings.getUsername(), Settings.getSecret()));
            }
        }
        out.flush();
		textFrame = new TextFrame();
		start();
	}

	@SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj){
        if(open){
            out.println(activityObj.toString());
            out.flush();
        } else {
            log.error("connection closed");
        }
    }
	
	public void disconnect(boolean closeTextFrame){
        if(open){
            log.info("closing connection "+Settings.socketAddress(socket));
            try {
                // close the socket
                socket.close();

                open=false;
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
            }
            log.info("connection "+Settings.socketAddress(socket)+" closed");
        }
        // close textFrame and exit application
        if (closeTextFrame) {
            textFrame.setVisible(false);
            textFrame.dispose();
        }
    }

    public void run(){
        try {
            String msg = null;
            //Read messages from the server while the end of the stream is not reached
            while((msg = in.readLine()) != null) {
                try {
                    JSONObject obj = (JSONObject) parser.parse(msg);
                    textFrame.setOutputText(obj);
                    String command = obj.get("command").toString();
                    if (command.equals("INVALID_MESSAGE") ||
                            command.equals("LOGIN_FAILED") ||
                            command.equals("AUTHENTICATION_FAIL") ||
                            command.equals("REGISTER_FAILED")) {
                        disconnect(false);
                    } else {
                        if (command.equals("REDIRECT")){
                            disconnect(false);
                            Settings.setRemoteHostname(obj.get("hostname").toString());
                            Settings.setRemotePort(((Long) obj.get("port")).intValue());
                            try {
                                // Create a stream socket and connect it to the server as specified in the Settings
                                socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
                                log.info("connection with redirected server "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" established");

                                in = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream())));
                                out = new PrintWriter(new DataOutputStream(socket.getOutputStream()), true);
                            } catch (IOException e) {
                                log.error("received exception creating stream socket " + e);
                            }
                            open = true;
                            if (Settings.getSecret() != null) {
                                out.println(Message.login(Settings.getUsername(), Settings.getSecret()));
                            } else {
                                out.println(Message.login(Settings.getUsername(), secret));
                            }
                            out.flush();
                        }
                        else {
                            if (command.equals("REGISTER_SUCCESS")) {
                                out.println(Message.login(Settings.getUsername(), secret));
                                out.flush();
                            }
                        }
                    }
                } catch (ParseException e) {
                    log.error("invalid JSON object received from the server");
                }
            }
            //log.debug("connection closed to "+Settings.socketAddress(socket));
        } catch (IOException e) {
            log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
        }
    }
}
