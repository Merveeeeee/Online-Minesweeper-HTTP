import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;

class Worker extends Thread
{
    Socket clientSocket;
    String key;
    String session;
    Worker(Socket clientSocket, String key, String session)
    {
        this.clientSocket = clientSocket;
        this.key = key;
        this.session = session;
    }
    
    @Override
    public void run()
    {
        try
        {
            MinesweeperServer.processClientRequests(clientSocket, key, session);
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
            // Decrement the number of threads when the thread is done
            synchronized(MinesweeperServer.class)
            {
                MinesweeperServer.setMaxThreads(MinesweeperServer.getMaxThreads() + 1);
                System.out.println("Thread released, max threads: " + MinesweeperServer.getMaxThreads());
            }
        }
    }
}
