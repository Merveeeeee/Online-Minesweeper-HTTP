import java.io.*;
import java.net.*;

class Worker extends Thread
{
    Socket clientSocket;
    Worker(Socket clientSocket)
    {
        this.clientSocket = clientSocket;
    }
    
    @Override
    public void run()
    {
        try
        {
            MinesweeperServer.processClientRequests(clientSocket);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}
