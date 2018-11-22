package ie.keithchambers;

public interface CommandLineInterface
{
    public void onTerminate();
    public void onNewItem(String name, String desc, int timeoutPeriod, double reserve);
    public void onRequestItems();
    public void onRequestItemsVerbose();
    public boolean onStartAuction();
    public void onLoadItemsFromFile(String filePath);
    public void onSaveItemsToFile(String filePath);
    //public onRequestClients();
    //public onTerminateClient(int ID);
    public void onAddCommand(Command command);
}
