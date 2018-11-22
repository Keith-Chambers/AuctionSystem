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
            System.out.println("Setting connection handler for interface");
            this.connection = connection;
            this.connectionInputStream = connection.getInputStream();
            return true;
        }else
        {
            return false;
        }
    }

    public void onClientRequest()
    {
        System.out.println("Processing onClientRequest in Server Object");
        connectionInputStream = connection.getInputStream();

        try
        {
            if(connectionInputStream == null)
            {
                System.out.println("connectionInputStream null");
                return;
            }

            if(connection == null)
            {
                System.out.println("connection null");
                return;
            }

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
                sendClientAuctionState();
                break;

            /* Bid Code */
            case 2:
                onNewBidRequest();
                break;

            /* Initial connection code */
            case 3:
                onUserConnectionRequest();
                break;
            default:
                System.out.println("Invalid data from client");
            }
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    /* Initial connection + username assignment */
    private void onUserConnectionRequest()
    {
        System.out.println("Entering initial connection code on server");

        String username = null;

        try
        {
            byte usernameLength = connectionInputStream.readByte();

            if(usernameLength == 0)
            {
                username = "DefaultUsername" + String.valueOf(connection.getID());
            }else
            {
                System.out.println("Username of length: " + String.valueOf(usernameLength));
                byte[] usernameByteString = new byte[usernameLength];
                connectionInputStream.read(usernameByteString, 0, usernameLength);
                username = new String(usernameByteString, "UTF-8");
            }

            server.addUsernameFor(username, connection.getID());

            System.out.println("User connected : " + username);

        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    public void onNewBid()
    {

    }

    private void onNewBidRequest()
    {
        try
        {
            double bidAmount = connectionInputStream.readDouble();
            System.out.println("Client bid £" + String.valueOf(bidAmount));

            if( ! currentItemHasBid || bidAmount > server.getCurrentWinningBidAmount())
            {
                server.newCurrentItemBid(connection.getID(), bidAmount);
            } else
            {
                onInvalidBid();
            }
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    private void onInvalidBid()
    {
        System.out.println("Invalid bit");
        sendClientMessage("Bid too low. Refresh data to get latest bid on current item");
    }

    private void sendClientMessage(String message)
    {
        try
        {
            DataOutputStream out = connection.getOutputStream();

            /* Code for server messages */
            out.writeByte(5);

            ByteBuffer intBuffer = ByteBuffer.allocate(4);
            intBuffer.putInt(0, message.length());
            out.write(intBuffer.array(), 0, 4);
            out.write(message.getBytes(), 0, message.length());

            out.flush();

        } catch(Exception e)
        {
            System.out.println("Warning: Failed to send message to client -> " + e.toString());
        }
    }

    private void sendClientErrorMessage(String message)
    {
        try
        {
            DataOutputStream out = connection.getOutputStream();

            /* Code for error messages */
            out.writeByte(4);

            ByteBuffer intBuffer = ByteBuffer.allocate(4);
            intBuffer.putInt(0, message.length());
            out.write(intBuffer.array(), 0, 4);
            out.write(message.getBytes(), 0, message.length());

            out.flush();

        } catch(Exception e)
        {
            System.out.println("Warning: Failed to send err message to client -> " + e.toString());
        }
    }

    public void onConnectionCreation()
    {
        sendClientAuctionState();
    }

    private void sendClientAuctionState()
    {
        System.out.println("Sending client auction state");

        try
        {
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
            if(! currentItemHasBid)
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
    }

    public void onAddCommand(Command command)
    {
        serverCommandQueue.add(command);
    }

    public void onItemTimedOut(AuctionItem item, String winnerUsername, double winnerBidAmount)
    {
        System.out.println("Time out code executed; amount -> " + String.valueOf(winnerBidAmount));
        System.out.println("Username : " + winnerUsername);

        DataOutputStream out = connection.getOutputStream();

        /* Temp array buffers for storing integers and doubles */
        ByteBuffer intBuffer = ByteBuffer.allocate(4);
        ByteBuffer doubleBuffer = ByteBuffer.allocate(8);

        /*  We use a username length of 0 to indicate no one won the bid
            So even if username has a value (Due to Java being a pain with pass by ref)
            we have to write a value of 0. Thus the new variable */
        int usernameLength = (currentItemHasBid) ? winnerUsername.length() : 0;
        intBuffer.putInt(0, usernameLength);
        doubleBuffer.putDouble(0, winnerBidAmount);

        try
        {
            /* Write item timeout code for client */
            out.write(2);

            /* Length of username */
            out.write(intBuffer.array(), 0, 4);

            /* Check if someone bid on the item. Only send username and amount if so */
            if(currentItemHasBid)
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

            currentItemHasBid = false;

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

    public void setCurrentItemHasBid(boolean hasBid)
    {
        if(hasBid)
            System.out.println("Current item has a bid");
        else
            System.out.println("Current item does not have a bid");
            
        currentItemHasBid = hasBid;
    }

    private AuctionServer server = null;
    private ConnectionHandler connection = null;
    private DataInputStream connectionInputStream = null;
    private ArrayBlockingQueue<Command> serverCommandQueue = null;
    private boolean currentItemHasBid = false;
}
