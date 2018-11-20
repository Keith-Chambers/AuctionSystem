package ie.keithchambers;

public abstract class ConnectionHandlerInterface
{
    public ConnectionHandlerInterface(ConnectionHandler connection)
    {
        this.connection = connection;
    }

    public ConnectionHandlerInterface()
    {
    }

    /* True if successfully set, false otherwise */
    public boolean setConnectionHandlerOnce(ConnectionHandler connection)
    {
        if(this.connection == null)
        {
            this.connection = connection;
            return true;
        }else
        {
            return false;
        }
    }
    public abstract void onClientRequest();
    public abstract void onAddCommand(Command command);

    public ConnectionHandler connection;
}
