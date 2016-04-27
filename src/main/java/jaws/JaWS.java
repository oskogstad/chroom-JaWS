package jaws;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.io.*;
import java.util.*;
import java.security.*;

public class JaWS extends Thread {
    private final int PORT;
    private final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private ServerSocket socketServer;
    private Base64.Encoder b64encoder;
    private MessageDigest sha1digester;
    private ArrayList<Connection> connections;
    private WebSocketEventHandler eventHandler;

    private volatile boolean running = true;

    public JaWS(int port) {
        this.PORT = port;
        socketServer = null;
        try {
            socketServer = new ServerSocket(PORT);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        connections = new ArrayList<Connection>();

        // Utilities
        b64encoder = Base64.getEncoder();
        try {
            sha1digester = MessageDigest.getInstance("SHA-1");
        }
        catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    synchronized void onMessage(Connection con, String message) {
        if(eventHandler != null) {
            eventHandler.onMessage(con, message);
        }
    }

    synchronized void onConnect(Connection con) {
        if(eventHandler != null) {
            eventHandler.onConnect(con);
        }
    }

    synchronized void onDisconnect(Connection con) {
        connections.remove(con);
        if(eventHandler != null) {
            eventHandler.onDisconnect(con);
        }
    }

    synchronized void onPong(Connection con) {
        if (eventHandler != null) {
            eventHandler.onPong(con);
        }
    }

    public synchronized void close() {
        try {
            running = false;

            // Close all threads
            synchronized(connections) {
                for (int i=0; i<connections.size(); i++) {
                    connections.get(i).close("Server shutting down");
                }
            }
            socketServer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void broadcast(String message) {
        synchronized(connections) {
            for (Connection c : connections) {
                c.send(message);
            }
        }
    }

    @Override
    public void run() {
        if (socketServer == null) return;
        Logger.log("Server now listening on port " + PORT, Logger.GENERAL);

        while(running) {
            try {
                // Waiting for connections
                Socket socket = socketServer.accept();
				Logger.log("Incomming connection ...", Logger.GENERAL);

				ArrayList<String> httpReq = new ArrayList<String>();

                BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));

                PrintWriter out = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                // Adding httpReq to string array
                String s;
                while((s=in.readLine()) != null) {
                    if(s.isEmpty()) {
                        break;
                    }
                    httpReq.add(s);
                }

                String upgrade = null;
                String connection = null;
                String wsKey = null;
                for (String line : httpReq) {
                    String[] parts = line.split(": ");
                    if (parts.length == 1) {
                        // Ignore the 'GET...' line
                    }
                    else {
                        String key = parts[0];
                        String val = parts[1];

                        if(key.toLowerCase().contains("upgrade")) {
                            upgrade = val;
                        }
                        else if(key.equalsIgnoreCase("connection")) {
                            connection = val;
                        }
                        else if(key.equalsIgnoreCase("sec-websocket-key")) {
                            wsKey = val;
                        }
                    }
                }

                if (
                    upgrade != null && upgrade.equalsIgnoreCase("websocket") &&
                    connection != null && connection.toLowerCase().contains("upgrade") &&
                    wsKey != null)
                {
                    // Send handshake response
                    String acceptKey = b64encoder.encodeToString(
                            sha1digester.digest((wsKey+GUID).getBytes()));
                    out.write(
                        "HTTP/1.1 101 Switching Protocols\r\n"+
                        "Upgrade: websocket\r\n"+
                        "Connection: Upgrade\r\n"+
                        "Sec-WebSocket-Accept: "+acceptKey+
                        "\r\n\r\n");
                    out.flush();

                    Logger.log("Handshake sent, creating connection", Logger.GENERAL);
                    Connection con= new Connection(this, socket);
                    connections.add(con);
                    con.start();

                    if (eventHandler != null) {
                        eventHandler.onConnect(con);
                    }
                }
                else {
                    out.write("HTTPS/1.1 400 Bad Request\r\n"+"\r\n\r\n");
                    out.flush();
                }
            }
            catch(SocketException e) {
                if(!socketServer.isClosed()) {
                    e.printStackTrace();
                }
                // Else ignore. The program is terminating. All is well
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setEventHandler(WebSocketEventHandler eh) {
        this.eventHandler = eh;
    }
}
