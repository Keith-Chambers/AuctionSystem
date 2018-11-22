package ie.keithchambers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

public class ConnectionHandler extends Thread
{
    public ConnectionHandler(ConnectionHandlerInterface serverCallbacks, Socket connection)
    {
        ID = nextID++;
        this.connection = connection;
        serverInterface = serverCallbacks;
        if(! serverInterface.setConnectionHandlerOnce(this))
            System.out.println("Warning: Failed to pass connection handler to server interface obj");

        connectionCommandQueue = new ArrayBlockingQueue<Command>(MAX_COMMAND_QUEUE_SIZE);

        try
        {
            connectionInputStream = new DataInputStream(connection.getInputStream());
            connectionOutputStream = new DataOutputStream(connection.getOutputStream());

        }catch(Exception e)
        {
            System.out.println("Fatal Exception: " + e.toString());
            return;
        }

        System.out.println("New Connection made");
    }

    // TODO: Implement
    public boolean inputStreamEmpty()
    {
        try {
            return (connectionInputStream.available() <= 0);
        } catch (Exception e){
            System.out.println("Error in inputStreamEmpty");
            return true;
        }
    }

    public DataInputStream getInputStream()
    {
        return connectionInputStream;
    }

    public DataOutputStream getOutputStream()
    {
        return connectionOutputStream;
    }

    public void run()
    {
        /* Send current Auction state to client */
        serverInterface.onConnectionCreation();

        while(! terminated)
        {
            try
            {
                /* Check if there is data in input stream. Non blocking. */
                if(connectionInputStream.available() > 0)
                {
                    /* Inform the server that a client request has been received */
                    System.out.println("Data available in connection input buffer");
                    serverInterface.onAddCommand( () -> { serverInterface.onClientRequest(); } );
                    Thread.sleep(500);
                }
            }catch(Exception e){ System.out.println("Exception: " + e.toString() ); }

            /* Keep executing commands from command queue until it's empty */
            while(connectionCommandQueue.size() > 0)
            {
                try {
                    Command command = connectionCommandQueue.take();
                    command.execute();
                } catch(Exception e){
                    System.out.println("Failed to get ConnectionHandler command from queue");
                }
            }
        }

        /* Sends termination code to client so they know connection has been closed */
        serverInterface.onTerminate();

        /* Clean up resources */
        try
        {
            connectionInputStream.close();
            connectionOutputStream.close();
            connection.close();
        } catch(Exception e){ System.out.println("Warning: Failed to close connection resources -> " + e.toString()); }

        System.out.println("Connection #" + String.valueOf(ID) + " terminated successfully");
    }

    public void addCommand(Command command)
    {
        connectionCommandQueue.add(command);
    }

    public void terminate()
    {
        terminated = true;
    }

    public int getID()
    {
        return ID;
    }

    public ConnectionHandlerInterface getCommandInterface()
    {
        return serverInterface;
    }

    /* Private Command Queue for connection */
    private ArrayBlockingQueue<Command> connectionCommandQueue;
    private ConnectionHandlerInterface serverInterface;
    private Socket connection;
    private DataInputStream connectionInputStream;
    private DataOutputStream connectionOutputStream;
    private final int ID;
    private static int nextID = 0;
    private static final int BUFFER_SIZE = 200;
    private boolean terminated = false;
    private final int MAX_COMMAND_QUEUE_SIZE = 10;
}
