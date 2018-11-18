package ie.keithchambers;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

interface ConnectionListenerInterface
{
    public void newConnection(Socket connection);
}

public class ConnectionListener extends Thread
{
    public ConnectionListener(ConnectionListenerInterface listenerInterface) 
    {
        this.listenerInterface = listenerInterface;
        LISTENING_PORT = 54321;
    }
    
    public ConnectionListener(ConnectionListenerInterface listenerInterface, int portNumber)
    {
        this.listenerInterface = listenerInterface;
        LISTENING_PORT = portNumber;
    }
    
    public void terminate()
    {
        System.out.println("Terminating ConnectionListener");
        terminated = true;
        /* ServerSocket needs to be forcefully closed to end blocking method ServerSocket.accept() */
        if(listeningServer != null && ! listeningServer.isClosed())
        {
            try{ listeningServer.close(); }
            catch(IOException e){ 
                System.out.println("Fatal IOException in ConnectionListener.terminate() : " + e.toString()); 
            }
        }
    }

    public void run()
    {
        Socket connection = null;
    
        try 
        {
            listeningServer = new ServerSocket(LISTENING_PORT);
            
            while((connection = listeningServer.accept()) != null && ! terminated)
            {
                listenerInterface.newConnection(connection);
            }
            
        }catch(Exception e)
        {
            System.out.println("Warning in ConnectionListener: " + e.toString());
        }
        
        /* De-allocate resources */
        if(listeningServer != null && ! listeningServer.isClosed())
        {
            try{ listeningServer.close(); }
            catch(IOException e){ 
                System.out.println("Fatal IOException on exiting ConnectionListener.run() : " + e.toString()); 
            }
        }
        
        System.out.println("Connection Listener Terminated");
    }
    
    /* Default Port Number */
    private final int LISTENING_PORT;
    private ServerSocket listeningServer = null;
    private boolean terminated = false;
    private ConnectionListenerInterface listenerInterface;
}
