package ie.keithchambers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

interface ConnectionHandlerCommand
{
    public void operation();
}
 
public class ConnectionHandler extends Thread
{
    public ConnectionHandler(Socket connection)
    {
        ID = nextID++;
        this.connection = connection;
        connectionCommandQueue = new ArrayBlockingQueue<ConnectionHandlerCommand>(MAX_COMMAND_QUEUE_SIZE);
    }
    
    public void run()
    {
        String inputLine;
        
        try
        {
            connectionInputStream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }catch(Exception e)
        { 
            System.out.println("Fatal Exception: " + e.toString());
            return;
        }
        
        while(! terminated)
        {
            try
            {
                if(connectionInputStream.ready())
                {
                    inputLine = connectionInputStream.readLine();
                    System.out.println("Connection #" + String.valueOf(ID) + ":" + inputLine);
                }
            }catch(Exception e){ System.out.println("Exception: " + e.toString() ); }
        }
        
        System.out.println("Connection #" + String.valueOf(ID) + " terminating successfully");
    }
    
    public void terminate()
    {
        terminated = true;
    }
    
    public int getID()
    {
        return ID;
    }
    
    // Commands to be executed by all connection handlers go in here
    private ArrayBlockingQueue<ConnectionHandlerCommand> connectionCommandQueue;
    private Socket connection; 
    private BufferedReader connectionInputStream;
    private final int ID;
    private static int nextID = 0;
    private boolean terminated = false;
    private final int MAX_COMMAND_QUEUE_SIZE = 10;
}
