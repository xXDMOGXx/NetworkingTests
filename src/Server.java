import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Takes in the port as arguments. Creates a server on the specified port that allows clients to connect
public class Server implements Runnable {

    private final int port;
    private final ArrayList<ConnectionHandler> connections;
    private ServerSocket server;
    private final ArrayList<String> ids;
    private boolean done;

    // Initializes variables and port parameter
    public Server(int port) {
        this.port = port;
        connections = new ArrayList<>();
        ids = new ArrayList<>();
        done = false;
    }

    // Run to start new instanced server. Accepts clients and creates a threaded handler to manage them
    @Override
    public void run() {
        try {
            // Initialize socket settings with supplied port
            server = new ServerSocket(port);
            ExecutorService pool = Executors.newCachedThreadPool();
            // Read console input from a new thread
            Server.InputHandler inHandler = new Server.InputHandler();
            Thread t = new Thread(inHandler);
            t.start();
            // Loop accepting and adding clients
            while (!done) {
                Socket client = server.accept();
                // Create a new unused id for the new client
                String clientID = createID();
                // Create the handler for the client and run it in the thread pool
                ConnectionHandler handler = new ConnectionHandler(client, clientID);
                connections.add(handler);
                pool.execute(handler);
            }
        } catch (Exception e) {
            // shutdown if error in anything
            shutdown();
        }
    }

    // Creates a new unused id
    public String createID() {
        int min = 1;
        int max = 9999;
        // Loop until unused id found
        while (true) {
            // Create random id from min to max (Only integers used, but alphanumeric ids are supported)
            Random rand = new Random();
            String randomID = Integer.toString(rand.nextInt((max - min) + 1) + min);
            // Check the used ids for any conflicts
            boolean conflict = false;
            for (String takenID : ids) {
                if (randomID.equals(takenID)) {
                    conflict = true;
                    break;
                }
            }
            // If no conflicts, return id
            if (!conflict) {
                ids.add(randomID);
                return randomID;
            }
        }
    }

    // Sends message to all connected clients
    public void broadcast(String message) {
        // Loop through all clients and send them the message
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    // Shuts down the server
    public void shutdown() {
        try {
            // Close server then loop through all clients and close them
            done = true;
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // Takes in the client and their id as arguments. Handles all interactions with the client
    class ConnectionHandler implements Runnable {

        private final Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private boolean connected;
        private final String id;

        // Initializes parameters
        public ConnectionHandler(Socket client, String id) {
            this.client = client;
            this.id = id;
        }

        // Runs when thread pool executes it. Connects client and handles communication with them
        @Override
        public void run() {
            try {
                // Only when connected is true will the client be sent broadcasts
                connected = false;
                // Initialize streams
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                // Send the client their id
                out.println(id);
                // Ask client for their nickname and save it to a variable. Afterwords connected is set true
                out.println("SYSTEM:Please enter a nickname: ");
                String nickname = in.readLine();
                connected = true;
                // Log connection, broadcast connection, and welcome new client (Broadcast includes client's id to prevent echoing to client)
                System.out.println(nickname + " connected");
                broadcast(id + ":" + nickname + " joined the chat!");
                out.println("SYSTEM:Welcome to the chat " + nickname + "!");
                // Loop reading messages until handler is closed or errored
                String message;
                while ((message = in.readLine()) != null) {
                    // Check for special commands else broadcast message
                    if (message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            broadcast(id + ":" + nickname + " renamed themselves to " + messageSplit[1]);
                            System.out.println(nickname + " renamed themselves to " + messageSplit[1]);
                            nickname = messageSplit[1];
                            out.println("SYSTEM:Successfully changed nickname to " + nickname);
                        } else {
                            out.println("SYSTEM:No nickname provided!");
                        }
                    } else if (message.startsWith("/quit")) {
                        // This command will close both the client on their end, and the handler on this end
                        System.out.println(nickname + " disconnected");
                        broadcast(id + ":" + nickname + " left the chat!");
                        shutdown();
                    } else if (message.startsWith("/id")) {
                        System.out.println(nickname + " requested their id");
                        out.println("SYSTEM:Your id is: " + id);
                    } else {
                        broadcast(id + ":" + nickname + ": " + message);
                    }
                }
            } catch (IOException e) {
                // shutdown if error in streams
                shutdown();
            }
        }

        // Shuts down the handler and client
        public void shutdown() {
            try {
                // Close all streams, remove id from list of connected ids, then close the client
                in.close();
                out.close();
                ids.remove(id);
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                //ignore
            }
        }

        // Sends message to the client
        public void sendMessage(String message) {
            // Only send message to client if they are connected (Have entered their nickname)
            if (connected) {
                out.println(message);
            }
        }

    }

    // Reads the server's console and shuts the server down if an admin types /close
    class InputHandler implements Runnable{
        // Runs when new thread starts
        @Override
        public void run() {
            try {
                // Initialize stream for console input
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                while (!done) {
                    // Read console input. Shutdown if admin types /close
                    String message = inReader.readLine();
                    if (message.equals("/close")) {
                        inReader.close();
                        shutdown();
                    }
                }
            } catch (IOException e) {
                // shutdown if error in stream
                shutdown();
            }
        }
    }

    // Creates new instance of server and runs it when application is started
    public static void main(String[] args) {
        Server server = new Server(9999);
        server.run();
    }
}
