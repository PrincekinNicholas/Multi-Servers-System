package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;


public class Connection extends Thread {
	private static final Logger log = LogManager.getLogger();
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean open = false;
	private Socket socket;
	private boolean term=false;
	private String username = null;//set Null if between server and server. Set specific usrName if between server and Client.
	private String secret = null;
    private String serverId = null;

	Connection(Socket socket) throws IOException{
		in = new DataInputStream(socket.getInputStream());
	    out = new DataOutputStream(socket.getOutputStream());
	    inreader = new BufferedReader( new InputStreamReader(in));
	    outwriter = new PrintWriter(out, true);
	    this.socket = socket;
	    open = true;
	    //address =
	    start();
	}
	
	/*
	 * returns true if the message was written, otherwise false
	 */
	public boolean writeMsg(String msg) {
		if(open){
            outwriter.println(msg);
            return outwriter.checkError();
		}
		return true;
	}

	public void flushBuffer(Queue<String> buffer) {
		if (buffer != null) {
			while (!buffer.isEmpty()) {
				if (!writeMsg(buffer.peek())) {
					buffer.remove();
				} else {
					break;
				}
			}
		}
	}
	
	public void closeCon(){
		if(open){
			log.info("closing connection "+Settings.socketAddress(socket));
			try {
				term=true;
				inreader.close();
				out.close();
			} catch (IOException e) {
				// already closed?
				log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
			}
		}
	}
	
	
	public void run() {
		try {
			String data;
			while (!term && (data = inreader.readLine())!=null) {
				term = Control.getInstance().process(this,data);
			}
			log.debug("connection closed to "+Settings.socketAddress(socket));
			Control.getInstance().connectionClosed(this);
			in.close();
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			Control.getInstance().connectionClosed(this);
		}
		open=false;
	}

    public Socket getSocket() {
		return socket;
	}
	
	public boolean isOpen() {
		return open;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
