import java.io.*;
import java.net.*;
import java.util.Scanner;

public class MinesweeperClient
{
    public static void main(String[] args) 
    {
        Scanner scanner = new Scanner(System.in);
        Socket socket = null;
        OutputStream outputClient = null;
        InputStream inputServer = null;

        try
        {
            // Attempt to connect to the server
            socket = new Socket("localhost", 2346);

            if (socket.isConnected())
            {
                System.out.println("Client connected");
            }
            else
            {
                System.out.println("Client disconnected");
                System.exit(0);
            }

            // Sending and receiving data streams
            outputClient = socket.getOutputStream();
            inputServer = socket.getInputStream();
            byte msg[] = new byte[128];

            // Display menu
            System.out.println("--- MINESWEEPER MENU ---");
            System.out.println("TRY Reveal a cell");
            System.out.println("FLAG Flag a cell");
            System.out.println("CHEAT (view the full grid)");
            System.out.println("QUIT");
            System.out.print("Choose an option: ");

            // Communication loop with server
            while (true)
            {   
                try 
                {
                    // Read user input
                    String choice = scanner.nextLine();
                    choice = choice.concat("\r\n\r\n");
                    outputClient.write(choice.getBytes());
                    outputClient.flush();

                    // Read server response
                    int msgLength = inputServer.read(msg);
                    if (msgLength == -1) break;
                    System.out.println(new String(msg));
                    msg = new byte[128];
                } 
                catch (IOException e)
                {
                    System.out.println("Error sending or receiving data from server.");
                    e.printStackTrace();
                    break;
                }
            }

        }
        catch (UnknownHostException e)
        {
            System.out.println("Server not found: " + e.getMessage());
        } catch (IOException e)
        {
            System.out.println("I/O error: " + e.getMessage());
        }
        finally 
        {
            // Clean up resources in the finally block
            try
            {
                if (scanner != null) scanner.close();
                if (outputClient != null) outputClient.close();
                if (inputServer != null) inputServer.close();
                if (socket != null) socket.close();
            } 
            catch (IOException e) 
            {
                System.out.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}
