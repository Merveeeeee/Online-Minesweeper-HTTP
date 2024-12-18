public class SessionInfo 
{
    private long timestamp;
    private Grid currentGame;

    public SessionInfo(long timestamp, Grid currentGame) {
        this.timestamp = timestamp;
        this.currentGame = currentGame;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Grid getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(Grid currentGame) {
        this.currentGame = currentGame;
    }
}
