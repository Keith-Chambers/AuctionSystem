package ie.keithchambers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class CommandLineListener extends Thread
{
    public CommandLineListener(CommandLineInterface commandLineInterface)
    {
        cli = commandLineInterface;

        inputReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print(COMMAND_PROMPT);
    }

    /* Does not support escaped quotes */
    public ArrayList<String> splitQuotedStringBySpace(String input)
    {
        boolean inQuotes = false;
        boolean betweenSegments = true;

        int currentSegment = 0;

        ArrayList<String> result = new ArrayList<String>(10);
        StringBuilder stringBuilder = new StringBuilder();

        for(int i = 0; i < input.length(); i++)
        {
            // If space is encountered while not in quotes, update currentSegment or skip if re-occurrance
            if(input.charAt(i) == ' ' && inQuotes == false)
            {
                if(! betweenSegments )
                {
                    betweenSegments = true;
                    currentSegment++;
                    result.add(stringBuilder.toString());
                    stringBuilder.delete(0, stringBuilder.length());
                }
                continue;
            }

            // Toggle inQuotes and continue
            if(input.charAt(i) == '\"')
            {
                inQuotes = ! inQuotes;
                continue;
            }

            betweenSegments = false;
            stringBuilder.append(input.charAt(i));
        }

        // Flush stringbuilder
        result.add(stringBuilder.toString());

        return result;
    }

    public void terminate()
    {
        terminate = true;
    }

    public void run()
    {
        String inputLine;
        while(! terminate)
        {

            try {
                if( inputReader.ready() )
                {
                    inputLine = inputReader.readLine();

                    if(inputLine.equals("q"))
                    {
                        cli.onAddCommand( () -> cli.onTerminate() );
                        continue;
                    }

                    String[] inputSegments = inputLine.split(" ");

                    if(inputSegments.length <= 0)
                    {
                        System.out.println("Err: Invalid input");
                    }else
                    {
                        switch(inputSegments[0])
                        {
                            case "clear":
                                System.out.print("\033[H\033[2J");
                                break;
                            case "additem":
                                ArrayList<String> parsedString = splitQuotedStringBySpace(inputLine);

                                /* Default invalid states */
                                String name = null;
                                String desc = null;
                                double reserve = 0.0;
                                int timeout = -1;

                                /* Requires 2 params, additem and <itemName> at minimum */
                                if(parsedString.size() < 2)
                                {
                                    System.out.println("Not enough parameters: Type help for usage");
                                    break;
                                }

                                /* We know the location of item name as it is required */
                                name = parsedString.get(1);

                                System.out.println("Parsed name : \"" + name + "\"");

                                /* Check the order of the optional parameters and parse */
                                for(int i = 2; i < parsedString.size(); i++)
                                {
                                    System.out.println(parsedString.get(i));
                                    if(parsedString.get(i).equals("-d"))
                                        desc = parsedString.get(i + 1);
                                    else if(parsedString.get(i).equals("-t"))
                                        timeout = Integer.parseInt(parsedString.get(i + 1));
                                    else if(parsedString.get(i).equals("-r"))
                                        reserve = Double.parseDouble(parsedString.get(i + 1));
                                }

                                /* Add command for the AuctionServer to process */
                                cli.onNewItem(name, desc, timeout, reserve);
                                break;
                            case "listitems":
                                if(inputSegments.length > 1 && inputSegments[1].equals("-v"))
                                    cli.onRequestItemsVerbose();
                                else
                                    cli.onRequestItems();
                                break;
                            case "startauction":
                                    if(auctionRunning == true)
                                        System.out.println("Auction already running");
                                    else
                                        auctionRunning = cli.onStartAuction();
                                break;
                            case "loaditems":
                                if(inputSegments.length < 2)
                                    System.out.println("Err: No file path specified");
                                else
                                    cli.onAddCommand( () -> cli.onLoadItemsFromFile(inputSegments[1]) );
                                break;
                            case "saveitems":
                                if(inputSegments.length < 2)
                                    System.out.println("Err: No file path specified");
                                else
                                    cli.onAddCommand( () ->cli.onSaveItemsToFile(inputSegments[1]) );
                                break;
                            default:
                                System.out.println("Invalid Argument : " + inputSegments[0]);
                            case "help":
                                System.out.println(usageString);
                                break;
                        }
                    }

                    System.out.print(COMMAND_PROMPT);
                }

                Thread.sleep(100);

            } catch (Exception e){ System.out.println("Exception: " + e.toString()); }
        }
    }

    private CommandLineInterface cli;
    private volatile  boolean terminate = false;
    private final String usageString =
    "Usage: command args..\n" +
    "  commands: \n" +
    "    startauction  -- starts the auction\n" +
    "    additem <\"itemname\"> [-d <\"itemdescription\">] [-t <timeoutperiod>] [-r <reserveprice>] -- Adds a new item at end of auction queue\n" +
    "    loaditems <filepath> -- Loads a list of items from binary file\n" +
    "    saveitems <filepath> -- Saves current items in a file to be loaded later\n" +
    "    listitems [-v] -- Lists all auction items in order (-v is for verbose output)\n" +
    "    q -- Quits application";

    private BufferedReader inputReader;
    private final String COMMAND_PROMPT = "AS>";
    private boolean auctionRunning = false;
}
