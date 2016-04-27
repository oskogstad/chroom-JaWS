package jaws;

public class Logger {

    public static final int NONE = 0x00; // Disable printing. Use as loglevel, not as category.
    public static final int WS_IO = 0x01; // Sending and reciving websocket data
    public static final int JSON = 0x02; // Handling and parsing of JSON objects
    public static final int GENERAL = 0x04;
    public static final int WS_PARSE = 0x08; // Parsing of websocket byte arrays
    public static final int ALL = 0xFF; // Print all. Use as loglevel, not as category
    
    public static int logLevel = 0;

    public static void log(String message, int category) {
        if ((logLevel & category) != 0) {
            System.out.println(message);
        }
    }

    public static void logErr(String message, int category) {
        if ((logLevel & category) != 0) {
            System.err.println(message);
        }
    }
}
