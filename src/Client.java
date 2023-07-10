import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client implements Runnable{

    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private boolean done;

    // Run to start new instanced client. Prints any messages from server, and sends any messages from client
    @Override
    public void run() {
        try {
            // Initialize variables and socket settings
            client = new Socket("127.0.0.1", 9999);
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            // Read console input from a new thread
            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            // First message from server will be this client's id
            String id = in.readLine();
            // Next message will be asking for the client's desired username (Messages from now on will be in format of id:message)
            String inMessage = in.readLine();
            String[] messageSplit = inMessage.split(":", 2);
            System.out.print(messageSplit[1]);

            // Loop reading messages until client is closed or errored
            while ((inMessage = in.readLine()) != null) {
                messageSplit = inMessage.split(":", 2);
                // Ignores message if it matches the client's id to prevent echoing
                if (!messageSplit[0].equals(id)){
                    System.out.println(messageSplit[1]);
                }
            }
        } catch (IOException e) {
            // shutdown if error in streams
            shutdown();
        }
    }

    // Shuts down the client
    public void shutdown() {
        done = true;
        try {
            // Close all streams then the client
            in.close();
            out.close();
            if (!client.isClosed()) {
                client.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // Reads the client's console and sends their messages to the server. Shuts the client down if the client types /quit
    class InputHandler implements Runnable{
        // Runs when new thread starts
        @Override
        public void run() {
            try {
                // Initialize stream for console input
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                while (!done) {
                    // Read console input and send to server. Shutdown if client types /quit
                    String message = inReader.readLine();
                    out.println(message);
                    if (message.startsWith("/quit")) {
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

    // Creates new instance of client and runs it when application is started
    public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
