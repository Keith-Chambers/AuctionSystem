package ie.keithchambers;

import java.util.concurrent.ArrayBlockingQueue;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.net.Socket;
import java.util.Iterator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/* Deals with high level commands and overall management of state */
public class AuctionServer
{
    public AuctionServer()
    {
        items = new ArrayBlockingQueue<AuctionItem>(ITEM_QUEUE_SIZE);
        commandQueue = new ArrayBlockingQueue<Command>(COMMAND_QUEUE_SIZE);
        activeConnections = new ArrayList<ConnectionHandler>(MAX_ACTIVE_CONNECTIONS);

        // Create command line listener
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
                            System.out.println("Auction successfully started");
                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onNewItem(String name, String desc, int timeoutPeriod)
                    {
                        items.add(new AuctionItem(name, desc, timeoutPeriod));
                        System.out.println("New Item Added");
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
                    }

                    @Override
                    public void loadItemsFromFile(String filePath)
                    {
                        loadItemsFromFile(filePath);
                    }

                    @Override
                    public void saveItemsToFile(String filePath)
                    {
                        saveItemsToFile(filePath);
                    }

                    @Override
                    public void onAddCommand(Command command){ commandQueue.add(command); }
        });

        commandLineListener.start();

        // TODO: Remove
        //items.add(new AuctionItem("item1", "desc1", 20));
        //items.add(new AuctionItem("item2", "desc2", 15));

        //saveItemsToFile("./blah.bin");

        //items.clear();

        //loadItemsFromFile("./blah.bin");

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
        System.out.println("Saving items to file");
        if(items.size() == 0)
            return;

        FileOutputStream fileOutput = null;
        int bytesNeeded = 0;

        AuctionItem[] itemArray = items.toArray(new AuctionItem[0]);

        try
        {
            fileOutput = new FileOutputStream(filePath);

            /* Temp array buffer for storing integers */
            ByteBuffer intBuffer = ByteBuffer.allocate(4);
            intBuffer.mark(); // TODO: Remove with reset()

            intBuffer.putInt(0, itemArray.length);

            // TODO: Test and remove
            intBuffer.reset();
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

                // TODO: Remove. Just for debugging
                if(itemDesc.getBytes(Charset.forName("UTF-8")).length != itemDesc.length())
                    System.out.println("Warning: Convertion to byte has changed size of string");

                /* Write Description string */
                fileOutput.write(itemDesc.getBytes(Charset.forName("UTF-8")), 0, itemDesc.length());

                /* Write timeout period */
                fileOutput.write(itemTimeoutPeriod);
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
        System.out.println("Loading items from file");

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

            System.out.println("File of size " + String.valueOf(fileSize) + " bytes");


            /* Temp/convenience byte array to hold integers */
            byte[] tempIntByteArray = new byte[4];

            /* Parse file contents
               Forced to use array due to FileInputStream api */
            if(fileInput.read(tempIntByteArray, 0, 4) != 4)
                System.out.println("Warning: Failed to read 4 bytes from file");

            int numberOfItems = ByteBuffer.wrap(tempIntByteArray).getInt();

            System.out.println(String.valueOf(numberOfItems) + " items found in file");

            /* For each item described in the file */
            for(byte i = 0; i < numberOfItems; i++)
            {


                /* Parse Item Name */
                fileInput.read(tempIntByteArray, 0, 4);
                int itemNameSize = ByteBuffer.wrap(tempIntByteArray).getInt();
                //System.out.println("Item #" + String.valueOf((int)i) + " name size -> " + String.valueOf(itemNameSize));
                byte[] nameByteArray = new byte[itemNameSize];
                fileInput.read(nameByteArray, 0, itemNameSize);

                /* Parse Item Description */
                fileInput.read(tempIntByteArray, 0, 4);
                int itemDescSize = ByteBuffer.wrap(tempIntByteArray).getInt();
                //System.out.println("Item #" + String.valueOf((int)i) + " desc size -> " + String.valueOf(itemDescSize));
                byte[] descByteArray = new byte[itemDescSize];
                fileInput.read(descByteArray, 0, itemDescSize);

                /* Parse Item Timeout Period
                   Forced to use an array due to FileInputStream api */
                byte[] timeoutPeriod = new byte[1];
                fileInput.read(timeoutPeriod, 0, 1);

                /* Ensure valid timeout period */
                timeoutPeriod[0] = (timeoutPeriod[0] > 30 || timeoutPeriod[0] < 1) ? 30 : timeoutPeriod[0];

                // TODO: Remove
                //String tmpName = new String(nameByteArray, "UTF-8");
                //String tmpDesc = new String(descByteArray, "UTF-8");

                //System.out.println("Name -> " + tmpName);
                //System.out.println("Desc -> " + tmpDesc);

                /* Add item to queue */
                items.add(new AuctionItem(new String(nameByteArray, "UTF-8"), new String(descByteArray, "UTF-8"), (int) timeoutPeriod[0]));


            }

        } catch(Exception e)
        {
            System.out.println("Error: " + e.toString());
        }finally
        {
            /* Close file if error occurred */
            try {
                if(fileInput != null)
                    fileInput.close();
            } catch(Exception e) { System.out.println(e.toString()); }
        }

        System.out.println("File successfully loaded");

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
        // Create connection listener
        ConnectionListenerInterface listenerInterface = (Socket connection) ->
        {

                if(activeConnections.size() == MAX_ACTIVE_CONNECTIONS)
                {
                    System.out.println("Failed to connect client: Currently handling max connections");
                    return;
                }

                ConnectionHandler conn = new ConnectionHandler(new ConnectionHandlerInterface(this), connection);
                /* Start the Thread */
                conn.start();
                activeConnections.add(conn);

        };

        connectionListener = new ConnectionListener(listenerInterface, DEFAULT_LISTENING_PORT);

        // Runs on a new thread
        // connectionListener.start();
        // commandLineListener.start();
    }

    public void terminate()
    {
        terminate = true;
        System.out.println("Shutting down Auction Server");
    }

    private void run()
    {
        Command command;
        while(! terminate )
        {
            if(commandQueue.size() > 0)
            {
                try {
                    // TODO: Remove
                    int startSize = commandQueue.size();
                    command = commandQueue.take();
                    command.execute();
                    int endSize = commandQueue.size();

                    if(startSize != endSize + 1)
                    {
                        System.out.println("Error: Command was not taken from queue in server");
                    }

                } catch(Exception e){
                    System.out.println("Exception: " + e.toString() );
                    /* Take out bad command */
                    try { commandQueue.take(); } catch(Exception e_){ System.out.println("Second Exception failed : " + e_.toString()); }
                }
            }

            /* Check if auction item needs to be closed */
            long currentTimestamp = System.currentTimeMillis();
            if(currentItemCloseTimestamp >= currentTimestamp)
            {
                /* TODO: Get winner, setup next item */
                for(ConnectionHandler client : activeConnections)
                {
                    client.addCommand(new ItemTimeoutCommand(client, "DEFAULT WINNER"));
                }
            }

        }

        System.out.println("Server loop terminated");
    }

    public ArrayBlockingQueue<Command> getCommandQueue()
    {
        return commandQueue;
    }

    public double getCurrentWinningBidAmount()
    {
        return currentItemBidAmount;
    }

    private ArrayBlockingQueue<Command> commandQueue;
    private CommandLineListener commandLineListener = null;
    private ConnectionListener connectionListener = null;
    private ArrayBlockingQueue<AuctionItem> items;
    private ArrayList<ConnectionHandler> activeConnections;

    private boolean terminate = false;

    private long currentItemCloseTimestamp = -1;
    private Double currentItemBidAmount;
    private String currentItemBidWinnerUsername;

    private final int ITEM_QUEUE_SIZE = 15;
    private final int COMMAND_QUEUE_SIZE = 20;
    private final int DEFAULT_LISTENING_PORT = 54321;
    private final int MAX_ACTIVE_CONNECTIONS = 5;
    private final int MIN_ITEMS = 5;
}
