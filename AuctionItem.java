package ie.keithchambers;

// TODO: Enforce max timeout period

public class AuctionItem
{
    public AuctionItem(String name)
    {
        this.name = name;
        timeoutPeriod = DEFAULT_TIMEOUT_PERIOD;
        ID = nextID++;
    }

    public AuctionItem(AuctionItem item)
    {
        this.name = item.getName();
        this.description = item.getDescription();
        this.ID = item.getID();
    }

    public AuctionItem(String name, String description)
    {
        this.name = name;
        this.description = (description != null) ? description : DEFAULT_DESC;

        ID = nextID++;
        timeoutPeriod = DEFAULT_TIMEOUT_PERIOD;
    }

    public AuctionItem(String name, int timeoutPeriod)
    {
        this.name = name;
        this.timeoutPeriod = timeoutPeriod;

        this.description = DEFAULT_DESC;
        ID = nextID++;
    }

    public AuctionItem(String name, String description, int timeoutPeriod, double reserve)
    {
        this.name = name;
        this.timeoutPeriod = timeoutPeriod;

        /* Assign suitable defaults if invalid parameters are passed */
        this.description = (description != null) ? description : DEFAULT_DESC;
        this.timeoutPeriod = (timeoutPeriod != -1) ? timeoutPeriod : DEFAULT_TIMEOUT_PERIOD;
        this.reserve = reserve;

        ID = nextID++;
    }

    /* Getters */
    public int getTimeoutPeriod()
    {
        return timeoutPeriod;
    }

    public String getName()
    {
        return name;
    }

    public double getReserve()
    {
        return reserve;
    }

    public boolean hasReserve()
    {
        return (reserve > 0.0);
    }

    public String getDescription()
    {
        return description;
    }

    public int getID()
    {
        return ID;
    }

    /* Seconds */
    private int timeoutPeriod;
    private String name;
    private static int nextID = 0;
    private static final int DEFAULT_TIMEOUT_PERIOD = 30;
    private final int ID;
    private String DEFAULT_DESC = "No Description";
    private String description;
    private double reserve;
}
