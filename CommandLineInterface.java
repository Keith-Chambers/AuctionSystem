package ie.keithchambers;

public interface CommandLineInterface
{
    public void onTerminate();
    public void onNewItem(String name, String desc, int timeoutPeriod);
    public void onRequestItems();
    public boolean onStartAuction();
    public void loadItemsFromFile(String filePath);
    public void saveItemsToFile(String filePath);
    //public onRequestClients();
    //public onTerminateClient(int ID);
    public void onAddCommand(Command command);
}
