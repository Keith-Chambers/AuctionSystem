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

        String currentBidWinnerUsername = null;
        Double currentBidWinnerAmount = null;
        boolean itemHasBidWinner = false;
        String username = null;
        AuctionItem[] items = null;

        System.out.println("Client Started");

        if(args.length < 2)
        {
            System.out.println("Usage: client host port");
            return;
        }

        BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Enter username(If blank username will be chosen for you): ");
        // TODO: enforce username length < 256
        username = inputReader.readLine();

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
                        case "listitems":
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
                        {
                            System.out.println("Item timed out");

                            String winnerUsername = null;
                            String itemName = null;
                            double winnerBidAmount = -1.0;

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
                                System.out.println("No one won " + itemName + ". Returned to back of auction item queue");
                            else
                                System.out.println(itemName + " was won by " + winnerUsername + " for £" + String.valueOf(winnerBidAmount));

                            if(connectionInputStream.available() > 0)
                                System.out.println("Warning: Bytes still available in input buffer after item timeout response");

                            break;
                        }
                        /* Auction state */
                        case 3:
                            System.out.println("Receiving Auction state");

                            long currItemTimeoutStart = connectionInputStream.readLong();
                            System.out.println("Boop");

                            // Get username length
                            int winnerUsernameLength = connectionInputStream.readInt();
                            System.out.println("Winner Username Length : " + String.valueOf(winnerUsernameLength));

                            // If 0, no winner and skip next two
                            // Get username
                            // Get bid
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

                                System.out.println("Winner Username : " + currentBidWinnerUsername);
                                System.out.println("Bid Amount : " + String.valueOf(currentBidWinnerAmount));
                            }

                            int numberOfItems = connectionInputStream.readInt();
                            items = new AuctionItem[numberOfItems];

                            System.out.println("Number of items on Auction : " + String.valueOf(numberOfItems));

                            for(int i = 0; i < numberOfItems; i++)
                            {
                                int itemNameLength = connectionInputStream.readInt();
                                byte[] itemNameByteArray = new byte[itemNameLength];
                                connectionInputStream.read(itemNameByteArray, 0, itemNameLength);

                                int itemDescLength = connectionInputStream.readInt();
                                byte[] itemDescByteArray = new byte[itemDescLength];
                                connectionInputStream.read(itemDescByteArray, 0, itemDescLength);

                                int timeoutPeriod = (int) connectionInputStream.readByte();

                                System.out.println("Item #" + String.valueOf(i + 1));


                                items[i] = new AuctionItem( new String(itemNameByteArray, "UTF-8"),
                                                            new String(itemDescByteArray, "UTF-8"),
                                                            timeoutPeriod);

                                System.out.println("    Name : " + items[i].getName());
                                System.out.println("    Desc : " + items[i].getDescription());
                                System.out.println("    Timeout Seconds : " + String.valueOf(items[i].getTimeoutPeriod()));
                            }

                            System.out.println("State updated from server successfully");
                            if(connectionInputStream.available() > 0)
                                System.out.println("Warning: Bytes still left in input buffer after getting state from server");

                            break;
                        default:
                        System.out.println("Invalid key code recieved from server");
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
