package ie.keithchambers;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.ArrayList;
import java.net.Socket;
import java.util.Iterator;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;

/* Deals with high level commands and overall management of state */
public class AuctionServer
{
    public AuctionServer()
    {
        items = new ArrayBlockingQueue<AuctionItem>(ITEM_QUEUE_SIZE);
        commandQueue = new ArrayBlockingQueue<Command>(COMMAND_QUEUE_SIZE);
        activeConnections = new ArrayList<ConnectionHandler>(MAX_ACTIVE_CONNECTIONS);
        usernameToConnectionHandlerIDMap = new HashMap<Integer, String>();

        start();
    }

    public boolean canStartAuction()
    {
        if(items.size() < 2)
        {
            System.out.println("Need more items to start auction");
            return false;
        }

        return true;
    }

    public void saveItemsToFile(String filePath)
    {
        if(items.size() == 0)
        {
            System.out.println("No items to save");
            return;
        }

        System.out.println("Saving items to file");

        FileOutputStream fileOutput = null;
        int bytesNeeded = 0;

        AuctionItem[] itemArray = items.toArray(new AuctionItem[0]);

        try
        {
            fileOutput = new FileOutputStream(filePath);

            /* Temp array buffer for storing integers */
            ByteBuffer intBuffer = ByteBuffer.allocate(4);
            ByteBuffer doubleBuffer = ByteBuffer.allocate(8);

            intBuffer.putInt(0, itemArray.length);
            System.out.println("Putting num items as int : " + intBuffer.getInt());

            /* Write number of Items */
            fileOutput.write(intBuffer.array(), 0, 4);
            bytesNeeded += 4;

            for(int i = 0; i < itemArray.length; i++)
            {
                String itemName = itemArray[i].getName();
                bytesNeeded += itemName.length();
                String itemDesc = itemArray[i].getDescription();
                bytesNeeded += itemDesc.length();
                int itemTimeoutPeriod = itemArray[i].getTimeoutPeriod();
                bytesNeeded += 1;
                double reserve = itemArray[i].getReserve();

                /* Get and write length of item name */
                intBuffer.putInt(0, itemName.length());
                bytesNeeded += 4;
                fileOutput.write(intBuffer.array(), 0, 4);

                /* Write name string */
                fileOutput.write(itemName.getBytes(Charset.forName("UTF-8")), 0, itemName.length());

                /* Get and write length of item description */
                intBuffer.putInt(0, itemDesc.length());
                fileOutput.write(intBuffer.array(), 0, 4);
                bytesNeeded += 4;

                /* Write Description string */
                fileOutput.write(itemDesc.getBytes(Charset.forName("UTF-8")), 0, itemDesc.length());

                /* Write timeout period */
                fileOutput.write(itemTimeoutPeriod);

                /* Write reserve */
                doubleBuffer.putDouble(0, reserve);
                fileOutput.write(doubleBuffer.array(), 0, 8);
            }

            System.out.println(String.valueOf(bytesNeeded) + " bytes witten to file");
            fileOutput.flush();

        } catch(Exception e)
        {
            System.out.println("Error: " + e.toString());
        } finally
        {
            /* Close file stream */
            try {
                if(fileOutput != null)
                    fileOutput.close();
            } catch(Exception e) { System.out.println(e.toString()); }
        }

    }

    public void loadItemsFromFile(String filePath)
    {
        FileInputStream fileInput = null;
        System.out.print("Loading items from file: ");

        try
        {
            fileInput = new FileInputStream(filePath);
            long fileSize = fileInput.getChannel().size();

            /* Check to make sure long can be converted to int */
            if(fileSize > 2147483647)
            {
                System.out.println("Error: File is too big");
                return;
            }

            /* Temp/convenience byte array to hold integers */
            byte[] tempIntByteArray = new byte[4];
            byte[] tempDoubleByteArray = new byte[8];

            /* Parse file contents
               Forced to use array due to FileInputStream api */
            if(fileInput.read(tempIntByteArray, 0, 4) != 4)
                System.out.println("Warning: Failed to read 4 bytes from file");

            int numberOfItems = ByteBuffer.wrap(tempIntByteArray).getInt();

            /* For each item described in the file */
            for(byte i = 0; i < numberOfItems; i++)
            {
                /* Parse Item Name */
                fileInput.read(tempIntByteArray, 0, 4);
                int itemNameSize = ByteBuffer.wrap(tempIntByteArray).getInt();
                byte[] nameByteArray = new byte[itemNameSize];
                fileInput.read(nameByteArray, 0, itemNameSize);

                /* Parse Item Description */
                fileInput.read(tempIntByteArray, 0, 4);
                int itemDescSize = ByteBuffer.wrap(tempIntByteArray).getInt();
                byte[] descByteArray = new byte[itemDescSize];
                fileInput.read(descByteArray, 0, itemDescSize);

                /* Parse Item Timeout Period
                   Forced to use an array due to FileInputStream api */
                byte[] timeoutPeriod = new byte[1];
                fileInput.read(timeoutPeriod, 0, 1);

                // TODO: Do this in constructor
                /* Ensure valid timeout period */
                timeoutPeriod[0] = (timeoutPeriod[0] > 30 || timeoutPeriod[0] < 1) ? 30 : timeoutPeriod[0];

                fileInput.read(tempDoubleByteArray, 0, 8);
                double reserve = ByteBuffer.wrap(tempDoubleByteArray).getDouble();

                AuctionItem item = new AuctionItem(new String(nameByteArray, "UTF-8"), new String(descByteArray, "UTF-8"), (int) timeoutPeriod[0], reserve);

                /* Add item to queue if unique*/
                if(! itemExists(item))
                    items.add(item);
                else
                    System.out.println("Warning: " + item.getName() + " already exists");
            }

            System.out.println("Success (" + String.valueOf(numberOfItems) + " items / " + String.valueOf(fileSize) + " bytes)");

        } catch(Exception e)
        {
            System.out.println("Error failed to load items from file: " + e.toString());
        }finally
        {
            /* Close file if error occurred */
            try {
                if(fileInput != null)
                    fileInput.close();
            } catch(Exception e) { System.out.println(e.toString()); }
        }
    }

    public void start()
    {
        init();
        run();
        cleanup();
    }

    private void cleanup()
    {
        commandLineListener.terminate();

        if(connectionListener != null)
            connectionListener.terminate();

        for(ConnectionHandler conn : activeConnections)
            conn.terminate();
    }

    private void init()
    {
        /* Initialize command line listener with desired overrides for CommandLineListener interface */
        commandLineListener = new CommandLineListener(new CommandLineInterface()
                {
                    @Override
                    public void onTerminate(){ terminate(); }

                    @Override
                    public boolean onStartAuction()
                    {
                        if(canStartAuction())
                        {
                            /* Start connection listener thread to allow for connections */
                            connectionListener.start();

                            currentItemTimeoutStart = System.currentTimeMillis();
                            /*  Calculate timeout period for current item being auctioned.
                                Convert timeoutPeriod to milliseconds */
                            currentItemTimeoutEnd = currentItemTimeoutStart + items.peek().getTimeoutPeriod() * 1000;

                            System.out.println("Auction successfully started");
                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onNewItem(String name, String desc, int timeoutPeriod, double reserve)
                    {
                        AuctionItem item = new AuctionItem(name, desc, timeoutPeriod, reserve);

                        if(! itemExists(item))
                        {
                            items.add(new AuctionItem(name, desc, timeoutPeriod, reserve));
                            System.out.println("\"" + name + "\" Added");
                        } else
                        {
                            System.out.println(item.getName() + " already exists");
                        }
                    }

                    @Override
                    public void onRequestItems()
                    {
                        Iterator<AuctionItem> itr = items.iterator();
                        int i = 1;
                        while(itr.hasNext())
                        {
                            System.out.println(String.valueOf(i) + ": " + itr.next().getName());
                            i++;
                        }

                        if(i == 1)
                            System.out.println("No Items");
                    }

                    @Override
                    public void onRequestItemsVerbose()
                    {
                        Iterator<AuctionItem> itr = items.iterator();
                        int i = 1;
                        while(itr.hasNext())
                        {
                            AuctionItem item = itr.next();
                            System.out.println(String.valueOf(i) + ": " + item.getName());
                            System.out.println("    Desc:    " + item.getDescription());
                            System.out.println("    Timeout: " + String.valueOf(item.getTimeoutPeriod()) + " seconds");
                            System.out.println("    Reserve: " + String.valueOf((item.hasReserve()) ? item.getReserve() : 0.0));
                            i++;
                        }

                        if(i == 1)
                            System.out.println("No Items Found");
                    }

                    @Override
                    public void onLoadItemsFromFile(String filePath)
                    {
                        loadItemsFromFile(filePath);
                    }

                    @Override
                    public void onSaveItemsToFile(String filePath)
                    {
                        saveItemsToFile(filePath);
                    }

                    @Override
                    public void onAddCommand(Command command){ commandQueue.add(command); }
        });

        /* Start Command Line Listener on new thread */
        commandLineListener.start();

        /* Define lambda to override ConnectionListenerInterface */
        ConnectionListenerInterface listenerInterface = (Socket connection) ->
        {

                if(activeConnections.size() == MAX_ACTIVE_CONNECTIONS)
                {
                    System.out.println("Failed to connect client: Currently handling max connections");
                    return;
                }

                ConnectionHandler conn = new ConnectionHandler(new ConnectionHandlerInterface(this), connection);
                /* Start the Thread & add instance to active connection array */
                conn.start();
                activeConnections.add(conn);

        };

        connectionListener = new ConnectionListener(listenerInterface, DEFAULT_LISTENING_PORT);
    }

    public void terminate()
    {
        terminate = true;
        System.out.println("Shutting down Auction Server");
    }

    public boolean itemExists(AuctionItem item)
    {
        Iterator<AuctionItem> itr = items.iterator();

        while(itr.hasNext())
        {
            AuctionItem currItem = itr.next();
            if(currItem.getName().equals(item.getName()))
                return true;
        }

        return false;
    }

    public double getCurrentItemReserve()
    {
        return items.peek().getReserve();
    }

    public boolean getCurrentItemHasReserve()
    {
        return items.peek().hasReserve();
    }

    private void run()
    {
        Command command;
        while(! terminate )
        {
            if(commandQueue.size() > 0)
            {
                try {
                    command = commandQueue.take();
                    command.execute();
                } catch(Exception e){
                    System.out.println("Exception: " + e.toString() );
                }
            }

            /* Check if auction item needs to be closed */
            long currentTimestamp = System.currentTimeMillis();
            if(currentTimestamp >= currentItemTimeoutEnd && currentItemTimeoutEnd != -1)
            {
                if(! currentItemHasBid)
                    System.out.println(items.peek().getName() + " finished auctioning unbought.");
                else
                    System.out.println(items.peek().getName() + " was sold to " + currentItemBidWinnerUsername + " for £" + String.valueOf(currentItemBidAmount));

                /* Loop through each client connection and inform them the current item has finished auctioning */
                for(ConnectionHandler client : activeConnections)
                {
                    client.getCommandInterface().setCurrentItemHasBid(currentItemHasBid);

                    /* Lambdas in Java use pass by reference so we need to create a new cloned object */
                    AuctionItem currItem = new AuctionItem(items.peek());
                    client.addCommand( () -> client.getCommandInterface().onItemTimedOut(currItem, currentItemBidWinnerUsername, currentItemBidAmount) );
                }

                AuctionItem item = null;

                try
                {
                    item = items.take();
                } catch(Exception e){ System.out.println("Error: Failed to take item from queue -> " + e.toString()); }

                /* Make sure item successfully got a value */
                if(item == null)
                    break;

                /* No one won the item, put at end of queue */
                if(! currentItemHasBid)
                    items.add(item);

                /*  If there are still items left to auction, setup for next item
                    Otherwise stop the auction and terminate server */
                if(items.size() > 0)
                {
                    currentItemTimeoutStart = currentItemTimeoutEnd;
                    currentItemTimeoutEnd = currentItemTimeoutEnd + items.peek().getTimeoutPeriod() * 1000;
                }else
                {
                    System.out.println("All items auctioned. Shutting down server");
                    try { Thread.sleep(200); } catch(Exception e) { System.out.println("Error: " + e.toString()); }
                    terminate = true;
                }

                currentItemHasBid = false;
            }
        }
    }

    public ArrayBlockingQueue<Command> getCommandQueue()
    {
        return commandQueue;
    }

    public double getCurrentWinningBidAmount()
    {
        return currentItemBidAmount;
    }

    public void setCurrentWinningBidAmount(double amount)
    {
        currentItemBidAmount = amount;
    }

    public String getCurrentWinningBidUsername()
    {
        return currentItemBidWinnerUsername;
    }

    public void setCurrentWinningBidUser(int ID)
    {
        currentItemBidWinnerUsername = getUsernameFor(ID);
    }

    public void newCurrentItemBid(int userID, double newBid)
    {
        setCurrentWinningBidUser(userID);
        setCurrentWinningBidAmount(newBid);

        /* Reset items timeout info */
        currentItemTimeoutStart = System.currentTimeMillis();
        currentItemTimeoutEnd = currentItemTimeoutStart + (items.peek().getTimeoutPeriod() * 1000);

        currentItemHasBid = true;
    }

    public long getCurrentItemTimeoutStart()
    {
        return currentItemTimeoutStart;
    }

    public AuctionItem getItem(int i)
    {
        return items.toArray(new AuctionItem[0])[i];
    }

    public int getNumItems()
    {
        return items.size();
    }

    public ConnectionHandler getActiveConnection(int i)
    {
        return activeConnections.get(i);
    }

    public int getNumActiveConnections()
    {
        return activeConnections.size();
    }

    public boolean addUsernameFor(String username, int ID)
    {
        if(usernameToConnectionHandlerIDMap.containsKey(ID) || usernameToConnectionHandlerIDMap.containsValue(username))
        {
            System.out.println("Warning: Attempt at duplicate connectionHandler ID or username into hashmap");
            return false;
        }

        usernameToConnectionHandlerIDMap.put(ID, username);

        return true;
    }

    public boolean getCurrentItemHasBid()
    {
        return currentItemHasBid;
    }

    public AuctionItem getCurrentItem()
    {
        if(items.size() > 0)
            return items.peek();
        else
            return null;
     }

    public String getUsernameFor(int ID)
    {
        return usernameToConnectionHandlerIDMap.get(ID);
    }

    private ArrayBlockingQueue<Command> commandQueue;
    private CommandLineListener commandLineListener = null;
    private ConnectionListener connectionListener = null;
    private ArrayBlockingQueue<AuctionItem> items;
    private ArrayList<ConnectionHandler> activeConnections;

    private boolean terminate = false;

    private long currentItemTimeoutEnd = -1;
    private Double currentItemBidAmount = -1.0;
    private String currentItemBidWinnerUsername = "";
    private long currentItemTimeoutStart;
    private boolean currentItemHasBid = false;
    private HashMap<Integer, String> usernameToConnectionHandlerIDMap;

    private final int ITEM_QUEUE_SIZE = 15;
    private final int COMMAND_QUEUE_SIZE = 20;
    private final int DEFAULT_LISTENING_PORT = 54321;
    private final int MAX_ACTIVE_CONNECTIONS = 5;
    private final int MIN_ITEMS = 5;
}
