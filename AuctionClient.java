package ie.keithchambers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.Socket;

public class AuctionClient
{
    AuctionClient(String host, int port)
    {
        this.host = host;
        this.port = port;
    }

    private boolean terminate = false;
    private String currentBidWinnerUsername = null;
    private Double currentBidWinnerAmount = null;
    private boolean itemHasBidWinner = false;
    private boolean currentBidWinnerIsMe = false;
    private String username = null;
    private AuctionItem[] items = null;
    private BufferedReader inputReader = null;
    private DataOutputStream out = null;
    private DataInputStream connectionInputStream = null;
    private Socket socket = null;
    private String host = null;
    private int port = -1;
    private long currItemTimeoutStart = -1;

    private boolean makeConnection()
    {
        if(host == null || port == -1)
        {
            System.out.println("Invalid host or port. Terminating");
            return false;
        }

        final int DEFAULT_INITIAL_DATA_SIZE = 2;

        try
        {
            inputReader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("Enter username(If blank username will be chosen for you): ");
            username = inputReader.readLine();

            System.out.println("Connection to " + host + " at port " + String.valueOf(port));

            /* Create a connection to host/port combo and create input and output streams to/from it */
            socket = new Socket(host, port);
    		out = new DataOutputStream(socket.getOutputStream());
            connectionInputStream = new DataInputStream(socket.getInputStream());

            if(username.equals(""))
            {
                /*  Preparing data load to send across network to server
                    Byte 1: Code for initial connectionInputStream
                    Byte 2: Length of username (0 means request from server) */
                byte[] dataContent = new byte[DEFAULT_INITIAL_DATA_SIZE];
                dataContent[0] = 3;
                dataContent[1] = 0;

                out.write(dataContent, 0, DEFAULT_INITIAL_DATA_SIZE);
                out.flush();
            }else
            {
                /*  Same as above only sending across a valid username too */
                byte usernameLength = (byte) username.length();
                byte[] dataContent = new byte[DEFAULT_INITIAL_DATA_SIZE + usernameLength];
                dataContent[0] = 3;
                dataContent[1] = usernameLength;

                int x = 0;
                for(int i = DEFAULT_INITIAL_DATA_SIZE; i < usernameLength + DEFAULT_INITIAL_DATA_SIZE; i++)
                    dataContent[i] = (byte) username.charAt(x++);

                out.write(dataContent, 0, DEFAULT_INITIAL_DATA_SIZE + usernameLength);
                out.flush();
            }

        } catch(Exception e){
            System.out.println("Error: " + e.toString());
            return false;
        }

        return true;
    }

    private void handleNewBid()
    {
        try
        {
            int usernameStringSize = connectionInputStream.readInt();
            byte[] usernameByteArray = new byte[usernameStringSize];

            connectionInputStream.read(usernameByteArray, 0, usernameStringSize);

            currentBidWinnerUsername = new String(usernameByteArray);
            currentBidWinnerAmount = connectionInputStream.readDouble();

            if(username.equals(currentBidWinnerUsername))
                currentBidWinnerIsMe = true;
            else
                currentBidWinnerIsMe = false;

            System.out.println("New bid made by " + currentBidWinnerUsername + " for £" + String.valueOf(currentBidWinnerAmount));
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    private void handleItemBidTimeout()
    {
        String winnerUsername = null;
        String itemName = null;
        double winnerBidAmount = -1.0;

        try
        {
            int usernameLength = connectionInputStream.readInt();

            if(usernameLength != 0)
            {
                byte[] winnerUsernameByteArray = new byte[usernameLength];
                connectionInputStream.read(winnerUsernameByteArray, 0, usernameLength);
                winnerUsername = new String(winnerUsernameByteArray);
                winnerBidAmount = connectionInputStream.readDouble();
            }

            int itemNameLength = connectionInputStream.readInt();
            byte[] itemNameByteArray = new byte[itemNameLength];
            connectionInputStream.read(itemNameByteArray, 0, itemNameLength);

            itemName = new String(itemNameByteArray, "UTF-8");

            if(usernameLength == 0)
                System.out.println("No one bought \"" + itemName + "\" this time");
            else
            {
                System.out.println("\"" + itemName + "\" was bought by " + winnerUsername + " for £" + String.valueOf(winnerBidAmount));
            }

            if(connectionInputStream.available() > 0)
                System.out.println("Warning: Bytes still available in input buffer after item timeout response");
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    private void handleAuctionStateRefresh()
    {
        try
        {
            /* When the item started auctioning */
            currItemTimeoutStart = connectionInputStream.readLong();

            /* Get username length */
            int winnerUsernameLength = connectionInputStream.readInt();

            /*  If username length = 0 (I.e There is no 'bid winner' on current item)
                Then skip bidWinnerUsername and bidWinnerAmount fields */
            if(winnerUsernameLength == 0)
            {
                itemHasBidWinner = false;
                currentBidWinnerUsername = "";
                currentBidWinnerAmount = -1.0;
            }else
            {
                byte[] currentWinnerUsernameByteArray = new byte[winnerUsernameLength];
                connectionInputStream.read(currentWinnerUsernameByteArray, 0, winnerUsernameLength);
                currentBidWinnerUsername = new String(currentWinnerUsernameByteArray, "UTF-8");
                currentBidWinnerAmount = connectionInputStream.readDouble();
            }

            int numberOfItems = connectionInputStream.readInt();
            items = new AuctionItem[numberOfItems];

            for(int i = 0; i < numberOfItems; i++)
            {
                int itemNameLength = connectionInputStream.readInt();
                byte[] itemNameByteArray = new byte[itemNameLength];
                connectionInputStream.read(itemNameByteArray, 0, itemNameLength);

                int itemDescLength = connectionInputStream.readInt();
                byte[] itemDescByteArray = new byte[itemDescLength];
                connectionInputStream.read(itemDescByteArray, 0, itemDescLength);

                int timeoutPeriod = (int) connectionInputStream.readByte();
                double reserve = connectionInputStream.readDouble();

                items[i] = new AuctionItem( new String(itemNameByteArray, "UTF-8"),
                                            new String(itemDescByteArray, "UTF-8"),
                                            timeoutPeriod, reserve);
            }

            System.out.println("Successfully refreshed " + String.valueOf(numberOfItems) + " from auction server");

            if(connectionInputStream.available() > 0)
                System.out.println("Warning: Bytes still left in input buffer after getting state from server");
        } catch(Exception e){ System.out.println("Error: " + e.toString()); }
    }

    private void displayCurrentItemDetails()
    {
        if(items.length == 0)
        {
            System.out.println("No current item");
            return;
        }

        long elapsedmilli = System.currentTimeMillis() - currItemTimeoutStart;
        double timeLeftSeconds = ((items[0].getTimeoutPeriod() * 1000) - elapsedmilli) / 1000;

        System.out.println("    Item name: " + items[0].getName());
        System.out.println("    Reserve: " + String.valueOf(items[0].getReserve()));
        System.out.println("");
        System.out.println("    Current Bid Winner: " + ((itemHasBidWinner) ? currentBidWinnerUsername : "None"));
        System.out.println("    Highest Bid: " + ((itemHasBidWinner) ? String.valueOf(currentBidWinnerAmount) : "N/A"));
        System.out.println("    Time left : " + String.valueOf(timeLeftSeconds) + " seconds");
        System.out.println("");
    }

    private void displayAllItems()
    {
        for(int i = 0; i < items.length; i++)
        {
            System.out.println("    Name : " + items[i].getName());
            System.out.println("    Desc : " + items[i].getDescription());
            System.out.println("    Timeout Seconds : " + String.valueOf(items[i].getTimeoutPeriod()));
            System.out.println("    Reserve: " + String.valueOf(items[i].getReserve()));
            System.out.println("");
        }
    }

    public void run()
    {
        if(! makeConnection())
            return;

        try
        {
            while(! terminate )
            {
                String inputLine;
                String[] splitInput;

                if(inputReader.ready())
                {
                    inputLine = inputReader.readLine();
                    splitInput = inputLine.split(" ");

                    switch(splitInput[0])
                    {
                        case "quit":
                        case "q":
                            System.out.println("Terminating..");
                            terminate = true;
                            out.writeByte(0);
                            break;
                        case "refresh":
                            out.writeByte(1);
                            break;
                        case "listcurrent":
                            displayCurrentItemDetails();
                            break;
                        case "listitems":
                            displayAllItems();
                            break;
                        case "bid":
                            if(splitInput.length < 2)
                                System.out.println("You must specify a bid amount");
                            else
                            {
                                System.out.println("Bidding " + String.valueOf(splitInput[1]));
                                out.writeByte(2);
                                out.writeDouble(Double.valueOf(splitInput[1]));
                            }
                            break;
                        default:
                            System.out.println("Usage: options\n q -- Quit\nlistitems -- Lists Auction Items\nbid <amount> -- Bid on Current Item");
                    }

                    out.flush();

                }

                /* There is data in the input buffer */
                if(connectionInputStream.available() > 0)
                {
                    byte keyCode = connectionInputStream.readByte();

                    switch(keyCode)
                    {
                        /* Connection terminated */
                        case 0:
                            System.out.println("Connection terminated by server");
                            terminate = true;
                            break;
                        /* New Bid */
                        case 1:
                            handleNewBid();
                            break;
                        /* Item timeout */
                        case 2:
                        {
                            handleItemBidTimeout();
                            break;
                        }
                        /* Refresh auction state */
                        case 3:
                            handleAuctionStateRefresh();
                            break;
                        default:
                        System.out.println("Invalid key code recieved from server");
                    }
                }
            }

            connectionInputStream.close();
            out.close();

        } catch(Exception e)
        {
            System.out.println("Error: " + e.toString());
        }
    }
}
