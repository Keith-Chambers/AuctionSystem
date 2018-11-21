package ie.keithchambers;

import java.util.concurrent.ArrayBlockingQueue;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

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
                try
                {
                    System.out.println("Client requesting list items");
                    DataOutputStream out = connection.getOutputStream();

                    /* Write response code */
                    out.writeByte(3);

                    /* Temp array buffer for storing integers */
                    ByteBuffer intBuffer = ByteBuffer.allocate(4);

                    ByteBuffer longBuffer = ByteBuffer.allocate(8);

                    long currItemTimeoutStart = server.getCurrentItemTimeoutStart();
                    longBuffer.putLong(0, currItemTimeoutStart);
                    out.write(longBuffer.array(), 0, 8);

                    // Current item info:
                    // Username size (0 if no bids + skip next two fields)
                    // Username
                    // Bid amount
                    String currBidWinnerUsername = server.getCurrentWinningBidUsername();
                    if(currBidWinnerUsername.length() == 0)
                    {
                        // No bids made. output username length of 0
                        intBuffer.putInt(0, 0);
                        out.write(intBuffer.array(), 0, 4);
                    }else
                    {
                        intBuffer.putInt(0, currBidWinnerUsername.length());
                        out.write(intBuffer.array(), 0, 4);
                        out.write(currBidWinnerUsername.getBytes(), 0, currBidWinnerUsername.length());

                        Double currBidAmount = server.getCurrentWinningBidAmount();
                        ByteBuffer doubleBuffer = ByteBuffer.allocate(8);
                        doubleBuffer.putDouble(0, currBidAmount);

                        out.write(doubleBuffer.array(), 0, 8);
                    }

                    intBuffer.putInt(0, server.getNumItems());

                    /* Write number of Items */
                    out.write(intBuffer.array(), 0, 4);

                    for(int i = 0; i < server.getNumItems(); i++)
                    {
                        String itemName = server.getItem(i).getName();
                        String itemDesc = server.getItem(i).getDescription();
                        int itemTimeoutPeriod = server.getItem(i).getTimeoutPeriod();

                        /* Get and write length of item name */
                        intBuffer.putInt(0, itemName.length());
                        out.write(intBuffer.array(), 0, 4);

                        /* Write name string */
                        out.write(itemName.getBytes(Charset.forName("UTF-8")), 0, itemName.length());

                        /* Get and write length of item description */
                        intBuffer.putInt(0, itemDesc.length());
                        out.write(intBuffer.array(), 0, 4);

                        /* Write Description string */
                        out.write(itemDesc.getBytes(Charset.forName("UTF-8")), 0, itemDesc.length());

                        /* Write timeout period */
                        out.write(itemTimeoutPeriod);
                    }

                    out.flush();

                } catch(Exception e)
                {
                    System.out.println("Warning: Failed to handle list items request from client -> " + e.toString());
                }

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

    public void onItemTimedOut(AuctionItem item, String winnerUsername, double winnerBidAmount)
    {
        System.out.println("Time out code executed");
        DataOutputStream out = connection.getOutputStream();

        /* Temp array buffers for storing integers and doubles */
        ByteBuffer intBuffer = ByteBuffer.allocate(4);
        ByteBuffer doubleBuffer = ByteBuffer.allocate(8);

        intBuffer.putInt(0, winnerUsername.length());
        doubleBuffer.putDouble(0, winnerBidAmount);

        try
        {
            /* Write item timeout code for client */
            out.write(2);

            /* Length of username */
            out.write(intBuffer.array(), 0, 4);

            /* If username length = 0, then no one won the item so emit username + bid amount fields */
            if(winnerUsername.length() != 0)
            {
                out.write(winnerUsername.getBytes(), 0, winnerUsername.length());
                out.write(doubleBuffer.array(), 0, 8);
            }

            /* Get and write length of item name */
            intBuffer.putInt(0, item.getName().length());
            out.write(intBuffer.array(), 0, 4);

            /* Write name string */
            out.write(item.getName().getBytes(Charset.forName("UTF-8")), 0, item.getName().length());

            out.flush();

        } catch(Exception e){ System.out.println("Warning: Failed to send item timeout to client -> " + e.toString()); }
    }

    public void onTerminate()
    {
        DataOutputStream out = connection.getOutputStream();
        byte[] terminationCodeByteArray = new byte[1];
        terminationCodeByteArray[0] = (byte)0;

        try
        {
            out.write(terminationCodeByteArray, 0, 1);
            out.flush();
        } catch(Exception e){ System.out.println("Warning: Failed to write termination code to client -> " + e.toString()); }

    }

    private AuctionServer server;
    private ConnectionHandler connection;
    private ArrayBlockingQueue<Command> serverCommandQueue;
}
