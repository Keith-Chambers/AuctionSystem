package ie.keithchambers;
 
public interface CommandLineInterface
{
    public void onTerminate();
    public void onNewItem(String name, String desc, int timeoutPeriod);
    public void onRequestItems();
    //public onRequestClients();
    //public onTerminateClient(int ID);
    public void onAddCommand(Command command);
}