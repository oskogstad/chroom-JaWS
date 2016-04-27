package jaws;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class Connection extends Thread {

    final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    final Base64.Decoder b64decoder = Base64.getDecoder();
    final JaWS jaws;

    private volatile StringBuilder stringBuilder; // For assembeling fragmented messages

    Connection(JaWS jaws, Socket socket) throws IOException {
        this.jaws = jaws;
        this.socket = socket;

        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        while(!Thread.interrupted()) {
            try {
                Frame f = new Frame(input);
                switch(f.opcode) {
                    case PING:
                        output.write(Frame.getPongFrame(f.messageBytes));
                        break;
                    case PONG:
                        jaws.onPong(this);
                        break;
                    case CONNECTION_CLOSE:
                        this.close(null);
                        break;
                    case TEXT:
                        if(f.fin) {
                            jaws.onMessage(this, f.message);
                        }
                        else {
                            // Begin fragmented message.
                            stringBuilder = new StringBuilder();
                            stringBuilder.append(f.message);
                        }
                        break;
                    case CONTINUATION:
                        if (f.fin) {
                            if(stringBuilder != null) {
                                stringBuilder.append(f.message);
                                jaws.onMessage(this, stringBuilder.toString());
                            }
                        }
                        else {
                            if(stringBuilder != null) {
                                stringBuilder.append(f.message);
                            }
                        }
                        break;
                    default:
                        Logger.log("Unhandled message with opcode "+f.opcode, Logger.WS_IO);
                        this.close("Server has not implemented opcode "+f.opcode);
                        break;
                }
            }
            catch(IOException e) {
                if(!socket.isClosed()) {
                    e.printStackTrace();
                    this.close("Internal server error");
                }
                // else ignore. The connection is closed. All is well
            }
        }
    }

    public void send(String message) {
        new Thread() {

            @Override
            public void run() {
                Frame f = new Frame(message);
                try {
                    Logger.log(f.message, Logger.WS_IO);
                    output.write(f.frameBytes);
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }

        }.start();
    }

    public void ping() {
        new Thread() {

            @Override
            public void run() {
                try {
                    Logger.log("Pinging client", Logger.WS_IO);
                    output.write(Frame.PING_FRAME);
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }

        }.start();
    }

    public void close(String reason) {
        try {
            if(reason != null){
                try{
                    output.write(Frame.getCloseFrame(reason));
                }
                catch(IOException e){
                    // AH...we tried
                }
            }
            input.close();
            output.close();
            socket.close();

            this.interrupt();
            jaws.onDisconnect(this);
        }
        catch(IOException e) {
            Logger.logErr("Exception thrown while closing connection!", Logger.WS_IO);
            e.printStackTrace();
        }
    }
}

