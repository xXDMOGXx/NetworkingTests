import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


// TODO: Have a client exit (tell them to shut themselves off), and a force exit (server manually closes streams and references)
// TODO: Finish UI for Server and Client
// TODO: Create jar executable to send to friends


// Takes in the port as arguments. Creates a server on the specified port that allows clients to connect
public class Server implements Runnable {

    private final int port;
    private ExecutorService pool;
    private final ArrayList<ConnectionHandler> connections;
    private ServerSocket serverSocket;
    private final ArrayList<String> ids;
    private boolean done;
    private static final JFrame frame = new JFrame("Server");
    public static int width = 640;
    public static int height = 360;

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
            serverSocket = new ServerSocket(port);
            pool = Executors.newCachedThreadPool();
            // Read console input from a new thread
            Server.InputHandler inHandler = new InputHandler();
            Thread inputThread = new Thread(inHandler);
            inputThread.start();
            System.out.println("Server Started");
            // Loop accepting and adding clients
            while (!done) {
                Socket client = serverSocket.accept();
                // Create a new unused id for the new client
                String clientID = createID();
                // Create the handler for the client and run it in the thread pool
                ConnectionHandler handler = new ConnectionHandler(client, clientID);
                connections.add(handler);
                pool.execute(handler);
            }
        } catch (SocketException e) {
            System.out.println("Client Listener Closed");
        } catch (Exception e) {
            System.out.println("Error in Server runtime!");
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
        System.out.println("Server Shutdown Started");
        done = true;
        closeWindow();
        System.out.println("Server Window Closed");
        try {
            // Close server then loop through all clients and close them
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
            if (!serverSocket.isClosed()) {
                System.out.println("Server Closing");
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error shutting down Server!");
        }
        pool.shutdown();
    }

    // Takes in the client and their id as arguments. Handles all interactions with the client
    class ConnectionHandler implements Runnable {

        private final Socket client;
        private BufferedReader cin;
        private boolean inOpen = false;
        private PrintWriter cout;
        private boolean connected;
        private final String id;
        private String nickname;

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
                cout = new PrintWriter(client.getOutputStream(), true);
                cin = new BufferedReader(new InputStreamReader(client.getInputStream()));
                inOpen = true;
                // Send the client their id
                cout.println(id);
                System.out.println(id + " connected");
                // Ask client for their nickname and save it to a variable. Afterwords connected is set true
                cout.println("SYSTEM:Please enter a nickname: ");
                nickname = cin.readLine();
            } catch (IOException e) {
                System.out.println("Error in initialization and nickname for Client " + id + "!");
                shutdown();
            }
            connected = true;
            // Log connection, broadcast connection, and welcome new client (Broadcast includes client's id to prevent echoing to client)
            System.out.println(id + " joined as " + nickname);
            broadcast(id + ":" + nickname + " joined the chat!");
            cout.println("SYSTEM:Welcome to the chat " + nickname + "!");
            startChat();
        }

        // Loop reading messages until handler is closed or errored
        public void startChat() {
            String message;
            try {
                while (inOpen) {
                    if ((message = cin.readLine()) != null) {
                        // Check for special commands else broadcast message
                        if (message.startsWith("/nick ")) {
                            String[] messageSplit = message.split(" ", 2);
                            if (messageSplit.length == 2) {
                                broadcast(id + ":" + nickname + " renamed themselves to " + messageSplit[1]);
                                System.out.println(nickname + " renamed themselves to " + messageSplit[1]);
                                nickname = messageSplit[1];
                                cout.println("SYSTEM:Successfully changed nickname to " + nickname);
                            } else {
                                cout.println("SYSTEM:No nickname provided!");
                            }
                        } else if (message.startsWith("/quit")) {
                            // This command will close both the client on their end, and the handler on this end
                            System.out.println(id + " disconnected");
                            broadcast(id + ":" + nickname + " left the chat!");
                            shutdown();
                        } else if (message.startsWith("/id")) {
                            System.out.println(nickname + " requested their id");
                            cout.println("SYSTEM:Your id is: " + id);
                        } else {
                            broadcast(id + ":" + nickname + ": " + message);
                        }
                    }
                }
                System.out.println("Client " + id + " Closed");
            } catch (IOException e) {
                System.out.println("Error in chat for Client " + id + "!");
                shutdown();
            }
        }

        // Sends message to the client
        public void sendMessage(String message) {
            // Only send message to client if they are connected (Have entered their nickname)
            if (connected) {
                cout.println(message);
            }
        }

        // Shuts down the handler and client
        public void shutdown() {
            // Close all streams, remove references, then close the client
            System.out.println("Client " + id + " Shutdown Started");
            connected = false;
            inOpen = false;
            try {
                cin.close();
                cout.close();
                if (!done) {
                    connections.remove(this);
                    ids.remove(id);
                }
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Error shutting down Handler for Client " + id + "!");
            }
        }
    }

    // Reads the server's console and shuts the server down if an admin types /close
    class InputHandler implements Runnable {
        @Override
        public void run() {
            try {
                // Initialize stream for console input
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                // Runs when new thread starts
                boolean reading = true;
                while (reading) {
                    // Read console input. Shutdown if admin types /close
                    String message = inReader.readLine();
                    if (message.equals("/shutdown")) {
                        inReader.close();
                        reading = false;
                        shutdown();
                    }
                }
            } catch (IOException e) {
                // shutdown if error in stream
                e.printStackTrace();
                System.out.println("Error in InputHandler stream!");
                shutdown();
            }
        }
    }

    public void createWindow() {
        UI ui = new UI();
        ui.setPreferredSize(new Dimension(width, height));
        //frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.add(ui);
        frame.setResizable(false);
        frame.pack();
        WindowListener exitListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        };
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(exitListener);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void closeWindow() {
        frame.dispose();
    }

    static class UI extends JPanel implements ActionListener {
        protected JTextField textField;
        protected JTextArea textArea;
        private final static String newline = "\n";

        public UI() {
            super(new GridBagLayout());

            textField = new JTextField(20);
            textField.addActionListener(this);

            textArea = new JTextArea(5, 20);
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);

            //Add Components to this panel.
            GridBagConstraints c = new GridBagConstraints();
            c.gridwidth = GridBagConstraints.REMAINDER;

            c.fill = GridBagConstraints.HORIZONTAL;
            add(textField, c);

            c.fill = GridBagConstraints.BOTH;
            c.weightx = 1.0;
            c.weighty = 1.0;
            add(scrollPane, c);
        }

        public void actionPerformed(ActionEvent evt) {
            String text = textField.getText();
            textArea.append(text + newline);
            textField.selectAll();

            //Make sure the new text is visible, even if there
            //was a selection in the text area.
            textArea.setCaretPosition(textArea.getDocument().getLength());
        }
    }

    // Creates new instance of server and runs it when application is started
    public static void main(String[] args) {
        System.out.println("Server Starting");
        Server server = new Server(64882);
        server.createWindow();
        server.run();
        System.out.println("Server Closed");
        System.exit(0);
    }
}
