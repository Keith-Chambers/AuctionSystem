package ie.keithchambers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class AuctionClient
{
    public static void main(String[] args) throws IOException
    {
        boolean terminate = false;

        System.out.println("Client Started");

        if(args.length < 2)
        {
            System.out.println("Usage: client host port");
            return;
        }

        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
        String host = args[0];
        int port = Integer.valueOf(args[1]);

        System.out.println("Connection to " + host + " at port " + String.valueOf(port));

        Socket socket = new Socket(host, port);
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        while(! terminate )
        {
            String inputLine;
            String[] splitInput;

            try
            {
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
                        case "list-items":
                            out.writeByte(1);
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
                            System.out.println("Usage: options\n q -- Quit\nlist-items -- Lists Auction Items\nbid <amount> -- Bid on Current Item");
                    }

                    out.flush();

                }//else
                // Thread.sleep();
            } catch(Exception e)
            {
                System.out.println("Error: " + e.toString());
            }
        }

        out.close();
    }
}
