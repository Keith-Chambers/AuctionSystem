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
    
    /* Does not support nested quotes */
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
            
            // Invert inQuotes and continue
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
        while(! terminate || Thread.currentThread().isInterrupted())
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
                            case "additem":
                                ArrayList<String> parsedString = splitQuotedStringBySpace(inputLine);
                                
                                /* Default invalid states */
                                String name = null;
                                String desc = null;
                                int timeout = -1;
                                
                                /* Requires 2 params, additem and <itemName> at minimum */
                                if(parsedString.size() < 2)
                                {
                                    System.out.println("Not enough parameters: Type help for usage");
                                    break;
                                }
                                
                                /* We know the location of item name as it is required */
                                name = parsedString.get(1);
                                
                                /* Check the order of the optional parameters and parse */
                                for(int i = 2; i < parsedString.size(); i++)
                                {
                                    System.out.println(parsedString.get(i));
                                    if(parsedString.get(i).equals("-d"))
                                        desc = parsedString.get(i + 1);
                                    else if(parsedString.get(i).equals("-t"))
                                        timeout = Integer.parseInt(parsedString.get(i + 1));
                                }
                                
                                /* Add command for the AuctionServer to process */
                                cli.onNewItem(name, desc, timeout);
                                break;
                            case "listitems":
                                cli.onRequestItems();
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
                
            } catch (Exception e){ System.out.println("Exception: " + e.toString()); }
            
            // Split input and create a command
        }        
    }

    private CommandLineInterface cli;
    private volatile  boolean terminate = false;
    private final String usageString =
    "Usage: command args..\n" +
    "  commands: \n" +
    "    additem <\"itemname\"> [-d <\"itemdescription\">] [-t <timeoutperiod>] -- Adds a new item at end of auction queue\n" +
    "    listitems -- Lists all auction items in order\n" +
    "    q -- Quits application";
    
    private BufferedReader inputReader;
    private final String COMMAND_PROMPT = "AS>";
}
