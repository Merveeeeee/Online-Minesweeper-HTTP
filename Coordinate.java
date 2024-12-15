public class Coordinate
{
    public static final char BOMB = 'B';
    public static final char FLAG = 'F';
    public static final char UNREVEALED = '#';

    /**
     * Status of the coordinate.
     * @implNote Helps to determine if we should reveal the 
     * coordinate or not to the client.
     */
    public enum Status{ UNREVEALED, REVEALED, FLAGGED }
    private char value;
    private Status status;

    /**
     * Constructor for the Coordinate class.
     * @implNote Initializes the coordinate with an unrevealed status.
     */
    public Coordinate()
    {
        this.value = UNREVEALED;
        this.status = Status.UNREVEALED;
    }

    /**
     * Set the value of the coordinate.
     * @param value The value of the coordinate.
     */
    public void setValue(char value)
    {
        this.value = value;
    }

    /**
     * Set the status of the coordinate.
     * @param status The status of the coordinate.
     */
    public void setStatus(Status status)
    {
        this.status = status;
    }

    /**
     * Get the value of the coordinate.
     * @return The value of the coordinate.
     */
    public char getValue()
    {
        return value;
    }

    /**
     * Get the status of the coordinate.
     * @return The status of the coordinate.
     */
    public Status getStatus()
    {
        return status;
    }
}
