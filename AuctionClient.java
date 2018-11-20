package ie.keithchambers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.DataInputStream;
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

        System.out.println("Enter username(If blank username will be chosen for you): ");
        // TODO: enforce username length < 256
        String username = inputReader.readLine();

        String host = args[0];
        int port = Integer.valueOf(args[1]);

        System.out.println("Connection to " + host + " at port " + String.valueOf(port));


        Socket socket = new Socket(host, port);
		DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream connectionInputStream = new DataInputStream(socket.getInputStream());

        if(username == "")
        {
            int DEFAULT_INITIAL_DATA_SIZE = 2;
            // Prepare byte array
            byte[] dataContent = new byte[DEFAULT_INITIAL_DATA_SIZE];
            // key code
            dataContent[0] = 3;
            // username length
            dataContent[1] = 0;

            out.write(dataContent, 0, DEFAULT_INITIAL_DATA_SIZE);
            out.flush();
        }else
        {
            int DEFAULT_INITIAL_DATA_SIZE = 2;
            byte usernameLength = (byte) username.length();
            // Prepare byte array
            byte[] dataContent = new byte[DEFAULT_INITIAL_DATA_SIZE + usernameLength];
            // key code
            dataContent[0] = 3;
            // username length
            dataContent[1] = usernameLength;

            int x = 0;
            for(int i = DEFAULT_INITIAL_DATA_SIZE; i < usernameLength + DEFAULT_INITIAL_DATA_SIZE; i++)
            {
                dataContent[i] = (byte) username.charAt(x++);
            }

            out.write(dataContent, 0, DEFAULT_INITIAL_DATA_SIZE + usernameLength);
            out.flush();
        }

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
                            int usernameStringSize = connectionInputStream.readInt();
                            byte[] usernameByteArray = new byte[usernameStringSize];
                            connectionInputStream.read(usernameByteArray, 0, usernameStringSize);

                            String bidderUsername = new String(usernameByteArray);
                            Double amount = connectionInputStream.readDouble();

                            System.out.println("New bid made by " + bidderUsername + " for £" + String.valueOf(amount));
                            break;
                        /* Item timeout */
                        case 2:
                            System.out.println("Item has timed out");
                            break;
                        /* Auction state */
                        case 3:
                            System.out.println("Auction state received");
                            break;
                    }
                }

            } catch(Exception e)
            {
                System.out.println("Error: " + e.toString());
            }
        }

        connectionInputStream.close();
        out.close();
    }
}
