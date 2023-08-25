import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client implements Runnable{

    private Socket client;
    private BufferedReader in;
    private boolean inOpen = false;
    private InputHandler inHandler;
    private PrintWriter out;

    // Run to start new instanced client. Prints any messages from server, and sends any messages from client
    @Override
    public void run() {
        try {
            // Initialize variables and socket settings
            client = new Socket(getServerIP(), 64882);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            inOpen = true;
            // Read console input from a new thread
            inHandler = new InputHandler();
            Thread inputThread = new Thread(inHandler);
            inputThread.start();

            // First message from server will be this client's id
            String id = in.readLine();
            // Next message will be asking for the client's desired username (Messages from now on will be in format of id:message)
            String inMessage = in.readLine();
            String[] messageSplit = inMessage.split(":", 2);
            System.out.print(messageSplit[1]);

            // Loop reading messages until client is closed or errored
            while (inOpen) {
                if ((inMessage = in.readLine()) != null) {
                    messageSplit = inMessage.split(":", 2);
                    // Ignores message if it matches the client's id to prevent echoing
                    if (!messageSplit[0].equals(id)) {
                        System.out.println(messageSplit[1]);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error in Client stream!");
            shutdown();
        }
    }

    public String getServerIP() {
        String ip;
        try {
            String localHost = String.valueOf(InetAddress.getLocalHost());
            String[] hostSplit = localHost.split("/", 2);
            ip = hostSplit[1];
            return ip;
        } catch (UnknownHostException e) {
            shutdown();
            return "";
        }
    }

    // Shuts down the client
    public void shutdown() {
        try {
            // Close all streams then the client
            inOpen = false;
            in.close();
            inHandler.inReader.close();
            out.close();
            if (!client.isClosed()) {
                client.close();
            }
        } catch (IOException e) {
            System.out.println("Error shutting down Client!");
        }
    }

    // Reads the client's console and sends their messages to the server. Shuts the client down if the client types /quit
    class InputHandler implements Runnable{
        BufferedReader inReader;
        // Runs when new thread starts
        @Override
        public void run() {
            // Initialize stream for console input
            inReader = new BufferedReader(new InputStreamReader(System.in));
            String message = "";
            boolean reading = true;
            while (reading) {
                // Read console input and send to server. Shutdown if client types /quit
                try {
                    message = inReader.readLine();
                } catch (IOException e) {
                    System.out.println("Error reading client input!");
                    shutdown();
                }
                out.println(message);
                if (message.startsWith("/quit")) {
                    try {
                        inReader.close();
                    } catch (IOException e) {
                        System.out.println("Error closing input stream!");
                        shutdown();
                    }
                    reading = false;
                    shutdown();
                }
            }
            System.out.println("You Disconnected");
        }
    }

    // Creates new instance of client and runs it when application is started
    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
