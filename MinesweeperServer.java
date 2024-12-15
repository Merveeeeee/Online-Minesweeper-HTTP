import java.io.*;
import java.net.*;

public class MinesweeperServer
{
    public static final short MSG_SIZE = 1024;
    public static final short SERVER_PORT = 2377;
    private static final String QUIT_COMMAND = "QUIT";
    private static final String TRY_COMMAND = "TRY";
    private static final String FLAG_COMMAND = "FLAG";
    private static final String CHEAT_COMMAND = "CHEAT";
    private static final short GRID_SIZE = 7;
    private static final int INACTIVE_TIME_OUT = 60000;

    /**
     * Main method for the MinesweeperServer class.
     * @param args The command line arguments.
     * @throws IOException If an I/O error occurs.
     */
    public static void main(String[] args) throws IOException
    {
        try(ServerSocket serverSocket = new ServerSocket(SERVER_PORT))
        {
            System.out.println("New server socket started on port " + SERVER_PORT);
            while (true)
            {
                handleClientConnection(serverSocket);
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Handle the connection between the server and the client.
     * @param server The MinesweeperServer object.
     * @param serverSocket The server socket.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleClientConnection(ServerSocket serverSocket)
    {
        try
        {
            // Check if the client is connected
            Socket clientSocket = serverSocket.accept();
            if(clientSocket.isConnected())
            {
                System.out.println("Client " + clientSocket.getPort() + " connected.");
            }
            else
            {
                System.out.println("Client failed to connect.");
                clientSocket.close();
                return;
            }
            // Create a new worker thread for the client to process the client's requests
            Worker worker = new Worker(clientSocket);
            worker.start();
        } 
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Process the client's requests.
     * @param server The MinesweeperServer object.
     * @param clientSocket The client socket.
     * @throws IOException If an I/O error occurs.
     */
    public static void processClientRequests(Socket clientSocket) throws IOException
    {
        Grid grid = new Grid(GRID_SIZE);
        OutputStream outputServer = clientSocket.getOutputStream();
        // Read the input from the client
        BufferedReader reader = 
            new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String receivedMessage = null;
        try
        {
            // Set the timeout for the client socket
            clientSocket.setSoTimeout(INACTIVE_TIME_OUT);
    
            // Loop until the client sends a "QUIT" command
            while(true)
            {    
                String line = reader.readLine();
                if(line != null)
                {
                    line = line.trim();
                    // If the client sends an empty line, process the command 
                    //(following the protocol)
                    if(line.isEmpty())
                    {
                        // If the client sends an empty line and the received message is not null,
                        // process the command (following the protocol)
                        if(receivedMessage != null)
                        {
                            processCommand
                                (receivedMessage, grid, outputServer, clientSocket);
                            receivedMessage = null;
                        }
                        // If the client sends an empty line and the received message is null,
                        // the client does not follow the protocol
                        else
                        {
                            handleWrongCommand(clientSocket, outputServer);
                        }
                        continue;
                    }
                }
                receivedMessage = line;
            }
        }
        catch(SocketTimeoutException e)
        {
            System.out.println("Client " + clientSocket.getPort() + " timed out.");
            clientSocket.close();
        }
        catch(IOException e)
        {
            System.out.println("Client " + clientSocket.getPort() + " disconnected.");
            clientSocket.close();
        }
    }

    /**
     * Process the command from the client.
     * @param receivedMessage The message received from the client.
     * @param grid The grid object.
     * @param outputServer The output stream to the client.
     * @param clientSocket The client socket.
     * @throws IOException If an I/O error occurs.
     */
    private static void processCommand(String receivedMessage, 
        Grid grid, OutputStream outputServer, Socket clientSocket) throws IOException
    {
        // Verify the command from the client
        if(isQuitCommand(receivedMessage))
        {
            handleQuitCommand(clientSocket, outputServer);
        } 
        else if(isCheatCommand(receivedMessage))
        {
            handleCheatCommand(grid, outputServer);
        } 
        else if(isFlagCommand(receivedMessage))
        {
            handleFlagCommand(outputServer, receivedMessage, grid);
        } 
        else if(isTryCommand(receivedMessage))
        {
            boolean isOver = handleTryCommand(outputServer, receivedMessage, grid);
            if(isOver)
            {
                System.out.println("Game over for client " 
                    + clientSocket.getPort() + " => disconnecting.");
                clientSocket.close();
            }
        } 
        else 
        {
            handleWrongCommand(clientSocket, outputServer);
        }
    }

    /**
     * Handle the "QUIT" command from the client.
     * @param clientSocket The client socket.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleQuitCommand(Socket clientSocket, OutputStream outputServer)
        throws IOException
    {
        printDisconnectedMessage(clientSocket);
        clientSocket.close();
    }
    
    /**
     * Handle the "CHEAT" command from the client.
     * @param server The MinesweeperServer object.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleCheatCommand(Grid grid, OutputStream outputServer)
        throws IOException
    {
        outputServer.write(grid.revealAllCells().getBytes());
        outputServer.flush();
    }
    
    /**
     * Handle the "FLAG" command from the client.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleFlagCommand(OutputStream outputServer, String input, Grid grid) 
        throws IOException
    {
        // Write the updated grid to the client if the coordinates are valid
        if(areCorrectCoordinates(grid, input))
        {
            if(!areCoordinatesInRange(input))
            {
                outputServer.write("INVALID RANGE\r\n\r\n".getBytes());
                outputServer.flush();
                return;
            }
            grid.flagCell(getXCoordinate(input), getYCoordinate(input));
            outputServer.write(grid.convertGridToProtocol(false).getBytes());
            outputServer.flush();
        }
        else
        {
            outputServer.write("WRONG\r\n\r\n".getBytes());
            outputServer.flush();
        }
    }
    
    /**
     * Handle the "TRY" command from the client.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static boolean handleTryCommand(OutputStream outputServer, String input, Grid grid) throws IOException
    {
        boolean isOver = false;
        // Write the updated grid to the client if the coordinates are valid
        if(areCorrectCoordinates(grid, input))
        {
            if(!areCoordinatesInRange(input))
            {
                outputServer.write("INVALID RANGE\r\n\r\n".getBytes());
                outputServer.flush();
                return false;
            }
            grid.revealCell(getXCoordinate(input), getYCoordinate(input));
            // Check if the game is over
            if(grid.isWin() || grid.isLose())
            {
                isOver = true;
            }
            else
            {
                isOver = false;
            }

            // Send the updated grid to the client
            outputServer.write(grid.convertGridToProtocol(false).getBytes());
            outputServer.flush();
        }
        else
        {
            // Client sent invalid coordinates
            outputServer.write("WRONG\r\n\r\n".getBytes());
            outputServer.flush();
            isOver = false;
        }
        return isOver;
    }
    
    /**
     * Handle an invalid command from the client.
     * @param clientSocket The client socket.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleWrongCommand(Socket clientSocket, OutputStream outputServer) 
        throws IOException
    {
        outputServer.write("WRONG\r\n\r\n".getBytes());
        outputServer.flush();
        printWrongInputMessage(clientSocket);
    }

    /**
     * Print a message to the console indicating that the client sent an invalid command.
     * @param clientSocket The client socket that sent the invalid command.
     * @implNote Debugging purposes only.
     */
    private static void printWrongInputMessage(Socket clientSocket)
    {
        System.out.println("Client " + clientSocket.getPort() + " sent an invalid command.");
    }

    /**
     * Print a message to the console indicating that the client disconnected.
     * @param clientSocket The client socket that disconnected.
     * @implNote Debugging purposes only.
     */
    private static void printDisconnectedMessage(Socket clientSocket)
    {
        System.out.println("Client " + clientSocket.getPort() + " disconnected.");
    }

    /**
     * Check if the input is a TRY command.
     * @param input The input from the client.
     * @param outputServer The output stream to the client.
     * @return True if the input is a valid command, false otherwise.
     */
    private static boolean isTryCommand(String input)
    {
        return input.startsWith(TRY_COMMAND);
    }

    /**
     * Check if the input is a FLAG command.
     * @param input The input from the client.
     * @param outputServer The output stream to the client.
     * @return True if the input is a valid command, false otherwise.
     */
    private static boolean isFlagCommand(String input)
    {        
        return input.startsWith(FLAG_COMMAND);
    }

    /**
     * Check if the coordinates from the client are valid.
     * @param input The input from the client.
     * @param outputServer The output stream to the client.
     * @return True if the input is a valid command, false otherwise.
     */
    private static boolean areCorrectCoordinates(Grid grid, String input)
    {
        // Divide the input into three parts: the command,
        // the x coordinate, and the y coordinate
        String[] parts = input.split(" ");
        final int X = 1;
        final int Y = 2;
        if (parts.length == 3)
        {
            if(!isNumeric(parts[X]) || !isNumeric(parts[Y]))
            {
                return false;
            }
        }
        else
        {
            return false;
        }
        return true;
    }

    /**
     * Check if the coordinates from the client are in range.
     * @param input The input from the client.
     * @return True if the coordinates are in range, false otherwise.
     */
    static private boolean areCoordinatesInRange(String input)
    {
        int x = getXCoordinate(input);
        int y = getYCoordinate(input);
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    /**
     * Get the x coordinate from the input.
     * @param input The input from the client.
     * @return The x coordinate.
     * @implNote The string must be in the format "TRY x y".
     */
    private static int getXCoordinate(String input)
    {
        String[] parts = input.split(" ");
        return Integer.parseInt(parts[1]);
    }

    /**
     * Get the y coordinate from the input.
     * @param input The input from the client.
     * @return The y coordinate.
     * @implNote The string must be in the format "TRY x y".
     */
    private static int getYCoordinate(String input)
    {
        String[] parts = input.split(" ");
        return Integer.parseInt(parts[2]);
    }

    /**
     * Check if the input string is a number.
     * @param str The input string.
     * @return True if the input is a number, false otherwise.
     */
    private static boolean isNumeric(String str)
    {
        for(char c : str.toCharArray())
        {
            if(!Character.isDigit(c))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the input from the client is a valid command.
     * @param input The input from the client.
     * @return True if the input is a valid command, false otherwise.
     */
    private static boolean isQuitCommand(String input)
    {
        return input.equals(QUIT_COMMAND);
    }

    /**
     * Check if the input from the client is a valid command.
     * @param input The input from the client.
     * @return True if the input is a valid command, false otherwise.
     */
    private static boolean isCheatCommand(String input)
    {
        return input.equals(CHEAT_COMMAND);
    }
}
