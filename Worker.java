import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;

class Worker extends Thread
{
    Socket clientSocket;
    String key;
    Worker(Socket clientSocket, String key)
    {
        this.clientSocket = clientSocket;
        this.key = key;
        // Create a new WebSocket object for the client
    }
    
    @Override
    public void run()
    {
        try
        {
            MinesweeperServer.processClientRequests(clientSocket, key);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        finally
        {
            synchronized(MinesweeperServer.class)
            {
                MinesweeperServer.setMaxThreads(MinesweeperServer.getMaxThreads() + 1);
                System.out.println("Thread released, max threads: " + MinesweeperServer.getMaxThreads());
            }
        }
    }
}
