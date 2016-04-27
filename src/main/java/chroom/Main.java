package chroom;

import java.io.*;
import java.util.ArrayList;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.*;

import jaws.*;

public class Main implements WebSocketEventHandler {
    private JaWS jaws;
    private JsonParser jsonParser;
    private StringEscapeUtils stringEscape;
    private int numberOfConnections;
    private ArrayList<String> chatlogArray;
    private File chatlog;
    private BufferedReader reader;

    public Main() {
        jaws = new JaWS(40506);
        jaws.setEventHandler(this);
        jaws.start();
        jsonParser = new JsonParser();
        stringEscape = new StringEscapeUtils();
        numberOfConnections = 0;

        // Read chatlog.txt to chatlogArray
        chatlogArray = new ArrayList();
        try {
            chatlog = new File("chatlog.txt");
            if(!chatlog.exists()) {
                chatlog.createNewFile();
            }
            reader = new BufferedReader(new FileReader(chatlog));
            String text = null;
            while ((text = reader.readLine()) != null) {
                if(text.length() > 0) {
                    chatlogArray.add(text);
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }

        // ShutdownHook, catches any interrupt signal and closes all threads
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                jaws.close();
            }
        }));
    }

    @Override
    public void onMessage(Connection con, String message) {
        try {
            JsonElement jsonElem = jsonParser.parse(message);

            if (jsonElem instanceof JsonObject) {
                JsonObject json = (JsonObject)jsonElem;

                // Escape all text from client
                json.addProperty("name", StringEscapeUtils.escapeHtml4(json.get("name").getAsString()));
                json.addProperty("msg", StringEscapeUtils.escapeHtml4(json.get("msg").getAsString()));
                json.addProperty("timestamp", StringEscapeUtils.escapeHtml4(json.get("timestamp").getAsString()));

                // Send to all clients
                jaws.broadcast(json.toString());
                writeToChatlog(json.toString());
            }
        }
        catch(JsonParseException e) {
            Logger.logErr("Failed to parse message "+message+" as JSON", Logger.JSON);
            return;
        }


    }

    @Override
    public void onConnect(Connection con) {
        Logger.log("New connection", Logger.GENERAL);
        numberOfConnections++;

        // Broadcast JSON with new numberOfConnections
        JsonObject json = new JsonObject();
        json.addProperty("numberOfCon", numberOfConnections);
        jaws.broadcast(json.toString());
        Logger.log("Number of connections: " + numberOfConnections, Logger.GENERAL);

        // send chatlog to the new connection
        for (int i=(int)Math.max(0, chatlogArray.size()-15); i<chatlogArray.size(); i++) {
            con.send(chatlogArray.get(i));
        }

    }

    @Override
    public void onDisconnect(Connection con) {
        Logger.log("Connection disconnected", Logger.GENERAL);
        numberOfConnections--;

        // Broadcast JSON with new numberOfConnections
        JsonObject json = new JsonObject();
        json.addProperty("numberOfCon", numberOfConnections);
        jaws.broadcast(json.toString());

        Logger.log("Number of connections: " + numberOfConnections, Logger.GENERAL);
    }

    @Override
    public void onPong(Connection con) {

    }

    public void writeToChatlog(String text) {
        // make new thread, write to array and file
        chatlogArray.add(text);
        new Thread() {
            @Override
            public void run() {
                synchronized(chatlog) {
                    try(BufferedWriter writer = new BufferedWriter(new FileWriter(chatlog, true))) {
                        writer.write(text+"\n");
                    }
                    catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public static void main(String[] args) {
        Logger.logLevel = Logger.GENERAL;

        new Main();
    }
}
