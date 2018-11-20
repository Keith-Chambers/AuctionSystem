package ie.keithchambers;

class ItemTimeoutCommand implements Command
{
    public ItemTimeoutCommand(ConnectionHandler connection, String winner)
    {
        this.connection = connection;
        this.winner = winner;
    }

    @Override
    public void execute()
    {
        // Can now use ConnectionHandler
    }

    private String winner;
    private ConnectionHandler connection;
}
