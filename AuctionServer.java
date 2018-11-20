package ie.keithchambers;

import java.util.concurrent.ArrayBlockingQueue;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.net.Socket;
import java.util.Iterator;
import java.io.DataInputStream;

/* Deals with high level commands and overall management of state */
public class AuctionServer
{

    public AuctionServer()
    {
        /* Lambda to allow adding generic commands to be executed */
        addCommandHook = (Command command) -> commandQueue.add(command);

        items = new ArrayBlockingQueue<AuctionItem>(ITEM_QUEUE_SIZE);
        commandQueue = new ArrayBlockingQueue<Command>(COMMAND_QUEUE_SIZE);
        activeConnections = new ArrayList<ConnectionHandler>(MAX_ACTIVE_CONNECTIONS);

        connectionHandlerCommandCallbacks = new ConnectionHandlerInterface()
        {
            @Override
            public void onClientRequest()
            {
                System.out.println("Processing onClientRequest in Server Object");
                try
                {
                    DataInputStream connectionInputStream = connection.getInputStream();
                    byte inputCode = connectionInputStream.readByte();

                    switch(inputCode)
                    {
                    /* Termination code */
                    case 0:
                        connection.terminate();
                        break;
                    /* List Items Code */
                    case 1:
                        System.out.println("Client requesting list items");
                        break;
                    /* Bid Code */
                    case 2:
                        Double bidAmount = connectionInputStream.readDouble();
                        System.out.println("Client bid £" + String.valueOf(bidAmount));
                        break;
                    default:
                        System.out.println("Invalid data from client");
                    }

                    // connectionInputStream.skipBytes(100);
                } catch(Exception e){ System.out.println("Error: " + e.toString()); }
            }

            @Override
            public void onAddCommand(Command command){ commandQueue.add(command); }
        };
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
        // Create command line listener
        commandLineListener = new CommandLineListener(new CommandLineInterface()
                {
                    @Override
                    public void onTerminate(){ terminate(); }

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
                    public void onAddCommand(Command command){ commandQueue.add(command); }
                });

        // Create connection listener
        ConnectionListenerInterface listenerInterface = (Socket connection) ->
        {

                if(activeConnections.size() == MAX_ACTIVE_CONNECTIONS)
                {
                    System.out.println("Failed to connect client: Currently handling max connections");
                    return;
                }

                ConnectionHandler conn = new ConnectionHandler(connectionHandlerCommandCallbacks, connection);
                /* Start the Thread */
                conn.start();
                activeConnections.add(conn);

        };

        connectionListener = new ConnectionListener(listenerInterface, DEFAULT_LISTENING_PORT);

        // Runs on a new thread
        connectionListener.start();
        commandLineListener.start();
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
                command = commandQueue.take();
                command.execute();

                } catch(Exception e){ System.out.println("Exception: " + e.toString() ); }
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

            try
            {
                // TODO: Decide if this should be here
                Thread.sleep(100); // Allow thread to sleep for a while

            }catch(Exception e)
            {
                System.out.println("Exception: " + e.toString() );
            }
        }
    }

    private AddCommandHook addCommandHook;
    private ArrayBlockingQueue<Command> commandQueue;
    private CommandLineListener commandLineListener = null;
    private ConnectionListener connectionListener = null;
    private ArrayBlockingQueue<AuctionItem> items;
    private ArrayList<ConnectionHandler> activeConnections;
    private ConnectionHandlerInterface connectionHandlerCommandCallbacks;

    private boolean terminate = false;

    private long currentItemCloseTimestamp;

    private final int ITEM_QUEUE_SIZE = 15;
    private final int COMMAND_QUEUE_SIZE = 20;
    private final int DEFAULT_LISTENING_PORT = 54321;
    private final int MAX_ACTIVE_CONNECTIONS = 5;
}
