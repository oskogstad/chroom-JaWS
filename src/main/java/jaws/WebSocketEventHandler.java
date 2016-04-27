package jaws;

public interface WebSocketEventHandler {
    public void onConnect(Connection con);
    public void onMessage(Connection con, String message);
    public void onDisconnect(Connection con);
    public void onPong(Connection con);
}

