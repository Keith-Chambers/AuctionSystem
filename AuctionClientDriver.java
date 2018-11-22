package ie.keithchambers;

public class AuctionClientDriver
{
    public static void main(String[] args)
    {
        if(args.length < 2)
        {
            System.out.println("Usage: client host port");
            return;
        }

        AuctionClient client = new AuctionClient(args[0], Integer.valueOf(args[1]));
        client.run();
    }
}
