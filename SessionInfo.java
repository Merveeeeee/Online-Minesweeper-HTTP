public class SessionInfo 
{
    private long timestamp;
    private Grid currentGame;
    private String playerName;

    public SessionInfo(long timestamp, Grid currentGame)
    {
        this.timestamp = timestamp;
        this.currentGame = currentGame;
        // Default player name
        playerName = "Anonymous";
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public Grid getCurrentGame()
    {
        return currentGame;
    }

    public String getPlayerName()
    {
        return playerName;
    }

    public void setPlayerName(String playerName)
    {
        this.playerName = playerName;
    }

    public void setCurrentGame(Grid currentGame)
    {
        this.currentGame = currentGame;
    }
}
