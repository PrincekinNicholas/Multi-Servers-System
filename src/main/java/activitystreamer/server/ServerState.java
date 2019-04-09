package activitystreamer.server;

public class ServerState {
    private int load;
    private Connection connection;

    public ServerState(int load, Connection connection) {
        this.load = load;
        this.connection = connection;
    }

    public void update(int load, Connection connection) {
        this.load = load;
        this.connection = connection;
    }

    public int getLoad() {
        return load;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}
