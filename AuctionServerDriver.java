package ie.keithchambers;

public class AuctionServerDriver
{
    public static void main(String args[])
    {
        AuctionServer auctionServer = new AuctionServer();
        
        auctionServer.start();
        
        // System.out.println("Terminating application");
    }
}   
