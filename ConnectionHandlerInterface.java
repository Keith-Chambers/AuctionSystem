package ie.keithchambers;

import java.util.concurrent.ArrayBlockingQueue;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class ConnectionHandlerInterface
{
    public ConnectionHandlerInterface(AuctionServer server, ConnectionHandler connection)
    {
        this.server = server;
        this.serverCommandQueue = server.getCommandQueue();
        this.connection = connection;
    }

    public ConnectionHandlerInterface(AuctionServer server)
    {
        this.server = server;
        this.serverCommandQueue = server.getCommandQueue();
    }

    /* True if successfully set, false otherwise */
    public boolean setConnectionHandlerOnce(ConnectionHandler connection)
    {
        if(this.connection == null)
        {
            this.connection = connection;
            return true;
        }else
        {
            return false;
        }
    }
    public void onClientRequest()
    {
        System.out.println("Processing onClientRequest in Server Object");
        try
        {
            DataInputStream connectionInputStream = connection.getInputStream();
            System.out.println("Hash: " + System.identityHashCode(connectionInputStream));

            if(connection.inputStreamEmpty())
            {
                System.out.println("Error: Input buffer empty");
                return;
            }

            System.out.print("Getting key byte(" + String.valueOf(connectionInputStream.available()) + ") : ");
            byte inputCode = connectionInputStream.readByte();
            System.out.println("success");

            switch(inputCode)
            {
            /* Termination code */
            case 0:
                System.out.println("Terminating connection in switch");
                connection.terminate();
                break;
            /* List Items Code */
            case 1:
                System.out.println("Client requesting list items");
                DataOutputStream out = connection.getOutputStream();
                // Order
                // Number of Items

                // For each item
                // Name, description, duration

                // Current item info:
                // Username size
                // Username
                // Bid amount

                break;
            /* Bid Code */
            case 2:
                Double bidAmount = connectionInputStream.readDouble();
                System.out.println("Client bid £" + String.valueOf(bidAmount));

                if(bidAmount > server.getCurrentWinningBidAmount())
                {
                    // Prepare byte array
                    // Size
                    // 1 for key
                    // 8 for double
                    // 2 for username string len
                    // n for username
                    System.out.println("Doing bid stuff..");
                } else
                {
                    System.out.println("Warning: Client send in a bid that is too low");
                }

                // Update all clients with this information if valid
                break;
            /* Initial connection + username assignment */
            case 3:
                System.out.println("Entering initial connection code on server");

                String username;
                byte usernameLength = connectionInputStream.readByte();

                if(usernameLength == 0)
                {
                    System.out.println("Possible error");
                    username = "DefaultUsername";
                    System.out.println("Nvm");
                }else
                {
                    System.out.println("Username of length: " + String.valueOf(usernameLength));
                    byte[] usernameByteString = new byte[usernameLength];
                    connectionInputStream.read(usernameByteString, 0, usernameLength);
                    username = new String(usernameByteString, "UTF-8");
                }

                System.out.println("Userconnected : " + username);
                break;
            default:
                System.out.println("Invalid data from client");
            }

            // TODO: Cleanup. Socket close and shit
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    public void onAddCommand(Command command)
    {
        serverCommandQueue.add(command);
    }

    private AuctionServer server;
    private ConnectionHandler connection;
    private ArrayBlockingQueue<Command> serverCommandQueue;
}
