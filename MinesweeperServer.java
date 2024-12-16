import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Base64;

public class MinesweeperServer
{
    public static final short MSG_SIZE = 1024;
    public static final short SERVER_PORT = 8013;
    private static final String QUIT_COMMAND = "QUIT";
    private static final String TRY_COMMAND = "TRY";
    private static final String FLAG_COMMAND = "FLAG";
    private static final String CHEAT_COMMAND = "CHEAT";
    private static final short GRID_SIZE = 7;
    private static final int INACTIVE_TIME_OUT = 60000;

    private static int maxThreads = 0;

    /**
     * Main method for the MinesweeperServer class.
     * @param args The command line arguments.
     * @throws IOException If an I/O error occurs.
     */
    public static void main(String[] args) throws IOException
    {
        // Get the number of threads from the command line
        if(args.length != 1)
        {
            System.out.println("Error: Invalid number of arguments.");
            System.out.println("Usage: java MinesweeperServer <number of threads>");
            System.exit(1);
        }
        MinesweeperServer.maxThreads = Integer.parseInt(args[0]);
        if(MinesweeperServer.maxThreads <= 0)
        {
            System.out.println("Number of threads must be greater than 0.");
            System.exit(1);
        }

        // Start the server
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line = reader.readLine();
                // Check if the client is using HTTP (Sending HTML page)
                if(line != null && line.startsWith("GET / HTTP/1.1"))
                {
                    System.out.println("Redirecting client " + clientSocket.getPort() + " to play.html");
                    redirectToPlayPage(clientSocket);
                    return;
                }
                else if(line != null && line.startsWith("GET /play.html HTTP/1.1"))
                {
                    System.out.println("Sending dynamic play.html page to client " + clientSocket.getPort());
                    sendDynamicHtmlPage(clientSocket);
                    return;
                }
                // Check if the client is using a WebSocket
                else
                {
                    boolean isWebSocketRequest = false;
                    String clientKey = null;
                    // Read the headers from the client
                    while ((line = reader.readLine()) != null && !line.isEmpty())
                    {
                        if (line.toLowerCase().contains("upgrade: websocket"))
                        {
                            isWebSocketRequest = true;
                        }
                        // Get the client key
                        else if (line.toLowerCase().contains("sec-websocket-key:"))
                        {
                            clientKey = line.split(":")[1].trim();
                        }
                    }
                
                    if (isWebSocketRequest)
                    {
                        System.out.println("WebSocket request detected. Starting WebSocket handshake...");
                        if (MinesweeperServer.getMaxThreads() <= 0)
                        {
                            System.out.println("No threads available.");
                            clientSocket.close();
                            return;
                        }
                        MinesweeperServer.setMaxThreads(MinesweeperServer.getMaxThreads() - 1);
                        System.out.println("Number of threads available: " + MinesweeperServer.getMaxThreads());
                
                        Worker worker = new Worker(clientSocket, clientKey);
                        worker.start();
                    }
                }
            }
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
     * @param webSocket The WebSocket object.
     * @throws IOException If an I/O error occurs.
     */
    public static void processClientRequests(Socket clientSocket, String key) throws IOException, NoSuchAlgorithmException
    {  
        // ============ Establish the handshake with the client ===============
        // DO NOT MODIFY THIS CODE
        OutputStream output = clientSocket.getOutputStream();
        System.out.println("Processing client requests for client " + clientSocket.getPort());
    
        String clientKey = key;
        if (clientKey != null)
        {
            // Compute the Sec-WebSocket-Accept key
            String magicString = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            String acceptKey = Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((clientKey + magicString).getBytes("UTF-8")));
    
            // Send the handshake response to the client
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                              "Upgrade: websocket\r\n" +
                              "Connection: Upgrade\r\n" +
                              "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            output.write(response.getBytes("UTF-8"));
            output.flush();
            System.out.println("WebSocket handshake successful.");
        }
        else
        {
            System.out.println("WebSocket handshake failed.");
            return;
        }
        //===================================================================
    
        // Create a new WebSocket object for the client
        WebSocket webSocket = new WebSocket(clientSocket);
        //sendHtmlPage(webSocket);

        // Read the input from the client
        String receivedMessage = null;
        // Create a new grid object
        Grid grid = new Grid(GRID_SIZE);
        // Set the timeout for the client socket
        clientSocket.setSoTimeout(INACTIVE_TIME_OUT);
        try
        {
            // Loop until the client sends a "QUIT" command
            while(true)
            {   
                try
                { 
                    // Receive the message from the client
                    receivedMessage = webSocket.receive();
                    if (receivedMessage == null || receivedMessage.isEmpty())
                    {
                        System.out.println("empty message");
                        continue;
                    }

                    System.out.println("Received message: " + receivedMessage);
                    webSocket.send("{\"message\": \"" + receivedMessage + "\"}");
                    //processCommand
                    //    (receivedMessage, grid, outputServer, clientSocket);
                }
                catch (IOException e) 
                {
                    System.out.println("Client " + clientSocket.getPort() + " timed out.");
                    break;
                }
            }
        }
        finally
        {
            // Close the client socket
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
            System.out.println("Invalid input: "+receivedMessage);
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

    public static int getMaxThreads()
    {
        return maxThreads;
    }

    public static void setMaxThreads(int maxThreads)
    {
        MinesweeperServer.maxThreads = maxThreads;
    }

    private static void sendDynamicHtmlPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
    
        // === En-têtes HTTP ===
        String httpResponse = "HTTP/1.1 200 OK\r\n" +
                              "Content-Type: text/html\r\n" +
                              "Connection: close\r\n" +
                              "\r\n";
    
        // === Contenu HTML, CSS et JavaScript ===
        String htmlContent = "<!DOCTYPE html>\n" +
                             "<html lang=\"en\">\n" +
                             "<head>\n" +
                             "    <meta charset=\"UTF-8\">\n" +
                             "    <title>Play Minesweeper</title>\n" +
                             "    <style>\n" +
                             "        body {\n" +
                             "            font-family: Arial, sans-serif;\n" +
                             "            margin: 0;\n" +
                             "            padding: 0;\n" +
                             "            display: flex;\n" +
                             "            justify-content: center;\n" +
                             "            align-items: center;\n" +
                             "            height: 100vh;\n" +
                             "            flex-direction: column;\n" +
                             "        }\n" +
                             "        .container {\n" +
                             "            display: flex;\n" +
                             "            margin: 50px;\n" +
                             "            flex-direction: column;\n" +
                             "            justify-content: center;\n" +
                             "            align-items: center;\n" +
                             "            border: 1px solid #000000;\n" +
                             "            border-radius: 5px;\n" +
                             "        }\n" +
                             "        h1 {\n" +
                             "            background-color: #333;\n" +
                             "            color: #fff;\n" +
                             "            margin: 0;\n" +
                             "            padding: 10px;\n" +
                             "            text-align: center;\n" +
                             "            width: 100%;\n" +
                             "        }\n" +
                             "        #grid {\n" +
                             "            display: grid;\n" +
                             "            grid-template-columns: repeat(7, 40px);\n" +
                             "            grid-gap: 5px;\n" +
                             "            justify-content: center;\n" +
                             "        }\n" +
                             "        .cell {\n" +
                             "            width: 40px;\n" +
                             "            height: 40px;\n" +
                             "            border: 1px solid #ccc;\n" +
                             "            display: flex;\n" +
                             "            align-items: center;\n" +
                             "            justify-content: center;\n" +
                             "            background-color: #ddd;\n" +
                             "            cursor: pointer;\n" +
                             "        }\n" +
                             "        .cell:hover {\n" +
                             "            background-color: #eee;\n" +
                             "        }\n" +
                             "    </style>\n" +
                             "</head>\n" +
                             "<body>\n" +
                             "    <h1>Minesweeper</h1>\n" +
                             "    <div class=\"container\">\n" +
                             "        <form>\n" +
                             "            <input type=\"text\" name=\"playerName\" placeholder=\"Your name\" required />\n" +
                             "            <input type=\"submit\" value=\"Submit Name\" />\n" +
                             "        </form>\n" +
                             "    </div>\n" +
                             "    <div id=\"grid\"></div>\n" +
                             "    <p id=\"status\"></p>\n" +
                             "    <script>\n" +
                             "        const ws = new WebSocket(\"ws://localhost:8013/ws\");\n" +
                             "        const grid = document.getElementById('grid');\n" +
                             "        const status = document.getElementById('status');\n" +
                             "\n" +
                             "        ws.onopen = () => {\n" +
                             "            status.textContent = \"Connected to the game server.\";\n" +
                             "            ws.send(\"Hello\");\n" +
                             "        };\n" +
                             "\n" +
                             "        ws.onmessage = (event) => {\n" +
                             "            console.log(\"Received:\", event.data);\n" +
                             "        };\n" +
                             "\n" +
                             "        ws.onerror = (error) => {\n" +
                             "            status.textContent = \"WebSocket error: \" + error.message;\n" +
                             "        };\n" +
                             "\n" +
                             "        ws.onclose = () => {\n" +
                             "            status.textContent = \"Disconnected from the server.\";\n" +
                             "        };\n" +
                             "\n" +
                             "        const rows = 7, cols = 7;\n" +
                             "        for (let i = 0; i < rows; i++) {\n" +
                             "            for (let j = 0; j < cols; j++) {\n" +
                             "                const cell = document.createElement(\"div\");\n" +
                             "                cell.classList.add(\"cell\");\n" +
                             "                grid.appendChild(cell);\n" +
                             "                cell.addEventListener(\"click\", () => ws.send(`TRY ${i} ${j}`));\n" +
                             "                cell.addEventListener(\"contextmenu\", (e) => {\n" +
                             "                    e.preventDefault();\n" +
                             "                    ws.send(`FLAG ${i} ${j}`);\n" +
                             "                });\n" +
                             "            }\n" +
                             "        }\n" +
                             "    </script>\n" +
                             "</body>\n" +
                             "</html>";
    
        // === Envoyer la réponse HTTP ===
        output.write(httpResponse.getBytes());
        output.write(htmlContent.getBytes());
        output.flush();
        output.close();
    }

    private static void redirectToPlayPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
        
        // Réponse HTTP avec le code 303 et l'en-tête Location
        String httpResponse = "HTTP/1.1 303 See Other\r\n" +
                              "Location: /play.html\r\n" + // URL cible
                              "Connection: close\r\n" +
                              "\r\n";
        output.write(httpResponse.getBytes("UTF-8"));
        output.flush();
        clientSocket.close();
    }
    
}
