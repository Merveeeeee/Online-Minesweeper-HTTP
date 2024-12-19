import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.awt.image.*;

import javax.imageio.ImageIO;

public class MinesweeperServer
{
    public static final short MSG_SIZE = 1024;
    public static final short SERVER_PORT = 8013;
    private static final String QUIT_COMMAND = "QUIT";
    private static final String TRY_COMMAND = "TRY";
    private static final String FLAG_COMMAND = "FLAG";
    private static final String CHEAT_COMMAND = "CHEAT";
    private static final short GRID_SIZE = 7;
    private static final int INACTIVE_TIME_OUT = 600000;

    // Map to store the players' names and their scores (will be used for the leaderboard)
    private static Map<String, Long> playersClassement = new ConcurrentHashMap<>();
    // Map to store the active sessions (cookie ID, session info)
    private static Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    private static int maxThreads = 3;

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
                // Redirect the client to the play.html page if requested
                if(line != null && line.startsWith("GET / HTTP/1.1"))
                {
                    System.out.println("Redirecting client " + clientSocket.getPort() + " to play.html");
                    redirectToPlayPage(clientSocket);
                    return;
                }
                // Send the dynamic play.html page to the client if requested
                else if(line != null && line.startsWith("GET /play.html HTTP/1.1"))
                {
                    System.out.println("Sending dynamic play.html page to client " + clientSocket.getPort());
                    sendPlayHtmlPage(clientSocket);
                    return;
                }
                else if(line != null && line.startsWith("GET /leaderboard.html HTTP/1.1"))
                {
                    System.out.println("Sending dynamic leaderboard.html page to client " + clientSocket.getPort());
                    sendLeaderboardHtmlPage(clientSocket);
                    return;
                }
                else if (line != null && line.startsWith("POST /submitName HTTP/1.1")) 
                {
                    System.out.println("Name submission detected.");
                    handleNameSubmission(clientSocket, reader);
                    redirectToPlayPage(clientSocket);
                    return;
                }
                else if (line != null && line.startsWith("POST /leaderboard HTTP/1.1")) 
                {
                    System.out.println("Leaderboard submission detected.");
                    redirectToLeaderboardPage(clientSocket);
                    return;
                }
                // Check if the client is using a WebSocket
                else
                {
                    boolean isWebSocketRequest = false;
                    String clientKey = null;
                    String sessionId = null;

                    // Read the headers from the client
                    while ((line = reader.readLine()) != null && !line.isEmpty())
                    {
                        //System.out.println(line);
                        if (line.toLowerCase().contains("upgrade: websocket"))
                        {
                            isWebSocketRequest = true;
                        }
                        // Get the client key
                        else if (line.toLowerCase().contains("sec-websocket-key:"))
                        {
                            clientKey = line.split(":")[1].trim();
                        }
                        else if (line.toLowerCase().contains("cookie:"))
                        {
                            // Extraire l'ID de session du cookie
                            String[] cookies = line.split(":")[1].trim().split(";");
                            for (String cookie : cookies)
                            {
                                if (cookie.trim().startsWith("SESSID="))
                                {
                                    sessionId = cookie.split("=")[1];
                                }
                            }
                        }
                    }

                    // If websocket request, start the handshake
                    if (isWebSocketRequest)
                    {
                        if (MinesweeperServer.getMaxThreads() <= 0)
                        {
                            System.out.println("No threads available.");
                            clientSocket.close();
                            return;
                        }
                        MinesweeperServer.setMaxThreads(MinesweeperServer.getMaxThreads() - 1);
                
                        Worker worker = new Worker(clientSocket, clientKey, sessionId);
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
    public static void processClientRequests(Socket clientSocket, String key, String session) throws IOException, NoSuchAlgorithmException
    {  
        // ============ Establish the handshake with the client ===============
        // DO NOT MODIFY THIS CODE
        String clientKey = key;
        if (clientKey != null)
        {
            session = upgradeToWebSocket(clientSocket, clientKey, session);
        }
        else
        {
            System.out.println("WebSocket handshake failed.");
            return;
        }
        //===================================================================
    
        // Create a new WebSocket object for the client
        WebSocket webSocket = new WebSocket(clientSocket);
        // Send the images to the client
        SendImages(webSocket);
        // Read the input from the client
        String receivedMessage = null;
        // Set the timeout for the client socket
        clientSocket.setSoTimeout(INACTIVE_TIME_OUT);
        // Get the grid object from the active sessions map (should be initialized in the handshake)
        Grid grid = activeSessions.get(session).getCurrentGame();
        webSocket.send(grid.convertGridToProtocol(false));
        // Send the leaderboard to the client (should be read for leaderboard.html)
        webSocket.send(generateJsonClassement(playersClassement));

        Long initialTimer = System.currentTimeMillis();

        // Timer must start here
        try
        {
            // Loop until the client sends a "QUIT" command
            // The game starts here
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
                    processCommand(receivedMessage, grid, clientSocket, webSocket);
                    // Check if the game is over, if so, remove the session
                    if(grid.isWin() || grid.isLose())
                    {
                        Long endTimer = System.currentTimeMillis() - initialTimer;
                        if(grid.isWin())
                        {
                            String playerName = activeSessions.get(session).getPlayerName();
                            playersClassement.put(playerName, endTimer);
                            System.out.println(playerName + " finished in " + endTimer);
                        }
                        activeSessions.remove(session);
                        System.out.println("Game over for client " 
                            + clientSocket.getPort() + " session removed.");
                    }
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

    private static String generateJsonClassement(Map<String, Long> playersClassement) 
    {
        // First, we need to sort the playersClassement map by value
        Map<String, Long> sortedPlayersClassement = playersClassement.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"LEADERBOARD\": [\n");
        for (Map.Entry<String, Long> entry : sortedPlayersClassement.entrySet()) {
            json.append("    {\n");
            json.append("      \"name\": \"").append(entry.getKey()).append("\",\n");
            json.append("      \"time\": ").append(entry.getValue()).append("\n");
            json.append("    },\n");
        }
        // Remove the last comma
        if (!sortedPlayersClassement.isEmpty()) {
            json.setLength(json.length() - 2);
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * Send the images to the client.
     * @param webSocket The WebSocket object.
     * @throws IOException If an I/O error occurs.
     */
    private static void SendImages(WebSocket webSocket) throws IOException {
        try {
            // Load Bomb Image
            File bombFile = new File("bomb.png");
            if (bombFile.exists()) {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                RenderedImage image = ImageIO.read(bombFile);
                ImageIO.write(image, "png", os);
                webSocket.send("Bomb:" + Base64.getEncoder().encodeToString(os.toByteArray()));
            } else {
                System.out.println("Bomb image not found.");
            }
    
            // Load Flag Image
            File flagFile = new File("flag.png");
            if (flagFile.exists()) {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                RenderedImage image = ImageIO.read(flagFile);
                ImageIO.write(image, "png", os);
                webSocket.send("Flag:" + Base64.getEncoder().encodeToString(os.toByteArray()));
            } else {
                System.out.println("Flag image not found.");
            }
        } catch (IOException e) {
            System.err.println("Error reading or sending images: " + e.getMessage());
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
        Grid grid, Socket clientSocket, WebSocket webSocket) throws IOException
    {
        // Verify the command from the client
        if(isQuitCommand(receivedMessage))
        {
            System.out.println("QUIT command received.");
            handleQuitCommand(clientSocket);
        } 
        else if(isCheatCommand(receivedMessage))
        {
            System.out.println("CHEAT command received.");
            handleCheatCommand(grid, webSocket);
        } 
        else if(isFlagCommand(receivedMessage))
        {
            System.out.println("FLAG command received.");
            handleFlagCommand(receivedMessage, grid, webSocket);
        } 
        else if(isTryCommand(receivedMessage))
        {
            System.out.println("TRY command received.");
            handleTryCommand(receivedMessage, grid, webSocket);
        } 
        else 
        {
            handleWrongCommand(clientSocket);
            System.out.println("Invalid input: "+receivedMessage);
        }
    }

    /**
     * Handle the "QUIT" command from the client.
     * @param clientSocket The client socket.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleQuitCommand(Socket clientSocket)
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
    private static void handleCheatCommand(Grid grid, WebSocket webSocket)
        throws IOException
    {
        webSocket.send(grid.revealAllCells());
    }
    
    /**
     * Handle the "FLAG" command from the client.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void handleFlagCommand(String input, Grid grid, WebSocket webSocket) 
        throws IOException
    {
        // Write the updated grid to the client if the coordinates are valid
        if(areCorrectCoordinates(grid, input))
        {
            if(!areCoordinatesInRange(input))
            {
                //outputServer.write("INVALID RANGE\r\n\r\n".getBytes());
                //outputServer.flush();
                return;
            }
            grid.flagCell(getXCoordinate(input), getYCoordinate(input));
            webSocket.send(grid.convertGridToProtocol(false));
        }
        else
        {
            //outputServer.write("WRONG\r\n\r\n".getBytes());
            //outputServer.flush();
        }
    }
    
    /**
     * Handle the "TRY" command from the client.
     * @param outputServer The output stream to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static boolean handleTryCommand(String input, Grid grid, WebSocket webSocket) throws IOException
    {
        boolean isOver = false;
        // Write the updated grid to the client if the coordinates are valid
        if(areCorrectCoordinates(grid, input))
        {
            if(!areCoordinatesInRange(input))
            {
                //outputServer.write("INVALID RANGE\r\n\r\n".getBytes());
                //outputServer.flush();
                return false;
            }
            grid.revealCell(getXCoordinate(input), getYCoordinate(input));
            // Check if the game is over
            isOver = grid.isWin() || grid.isLose();

            // Send the updated grid to the client (JSON format)
            webSocket.send(grid.convertGridToProtocol(false));
        }
        else
        {
            // Client sent invalid coordinates
            //outputServer.write("WRONG\r\n\r\n".getBytes());
            //outputServer.flush();
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
    private static void handleWrongCommand(Socket clientSocket) throws IOException
    {
        //outputServer.write("WRONG\r\n\r\n".getBytes());
        //outputServer.flush();
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

    /**
     * Get the maximum number of threads.
     * @return The maximum number of threads.
     */
    public static int getMaxThreads()
    {
        return maxThreads;
    }

    /**
     * Set the maximum number of threads.
     * @param maxThreads The maximum number of threads.
     */
    public static void setMaxThreads(int maxThreads)
    {
        MinesweeperServer.maxThreads = maxThreads;
    }

    private static void redirectToPlayPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
        String httpResponse = "HTTP/1.1 303 See Other\r\n" +
                              "Location: /play.html\r\n" +
                              "Connection: close\r\n" +
                              "\r\n";
        output.write(httpResponse.getBytes("UTF-8"));
        output.flush();
        clientSocket.close();
    }

    private static void redirectToLeaderboardPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
        String httpResponse = "HTTP/1.1 303 See Other\r\n" +
                              "Location: /leaderboard.html\r\n" +
                              "Connection: close\r\n" +
                              "\r\n";
        output.write(httpResponse.getBytes("UTF-8"));
        output.flush();
        clientSocket.close();
    }

    /**
     * Upgrade the client connection to a WebSocket connection. The session cookie is returned.
     * @param clientSocket The client socket.
     * @param clientKey The client key.
     * @param clientSession The client session.
     */
    private static String upgradeToWebSocket(Socket clientSocket, String clientKey, String clientSession) throws IOException, NoSuchAlgorithmException
    {
        OutputStream output = clientSocket.getOutputStream();
        String magicString = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String acceptKey = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((clientKey + magicString).getBytes("UTF-8")));
    
        boolean sendCookie = false;
        refreshSessions();
        // Check if the session is valid, should erase expired sessions
        if (clientSession == null || !isSessionValid(clientSession))
        {
            System.out.println("No session found: " + clientSession);
            sendCookie = true;
            clientSession = generateSessionCookie(clientSocket);
        } 
        else
        {
            System.out.println("Valid session found: " + clientSession);
        }
    
        // Build the WebSocket handshake response
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 101 Switching Protocols\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n");
    
        // Only set a new cookie if required
        if (sendCookie)
        {
            response.append("Set-Cookie: SESSID=").append(clientSession)
                    .append("; Max-Age=600; Path=/; Domain=localhost; HttpOnly\r\n");
            System.out.println("Sending new session cookie: " + clientSession);
        }
    
        response.append("\r\n");
        output.write(response.toString().getBytes());
        output.flush();
    
        System.out.println("WebSocket handshake completed.");
        return clientSession;
    }

    /*
     * Generate a session cookie for the client. The cookie is stored in the activeSessions map.
     */
    private static String generateSessionCookie(Socket clientSocket) throws IOException
    {
        String sessionId = UUID.randomUUID().toString();
        // Add the session to the active sessions map
        activeSessions.put(sessionId, new SessionInfo(System.currentTimeMillis(), new Grid(GRID_SIZE)));
        return sessionId;
    }

    /**
     * Check if the session is still valid. (Not expired and in the active sessions map)
     * @param sessionId The session ID.
     * @return True if the session is valid, false otherwise.
     */
    private static boolean isSessionValid(String sessionId)
    {
        if (sessionId == null) return false;
        // Verify if the session is still active (is in the map and not expired)
        return activeSessions.containsKey(sessionId) && 
               (System.currentTimeMillis() - activeSessions.get(sessionId).getTimestamp()) < 600000;
    }

    /**
     * Refresh the active sessions. (Remove expired sessions)
     */
    private static void refreshSessions()
    {
        // Remove expired sessions
        activeSessions.entrySet().removeIf(entry -> 
            (System.currentTimeMillis() - entry.getValue().getTimestamp()) >= 600000);
    }

    private static String handleNameSubmission(Socket clientSocket, BufferedReader reader) throws IOException
    {
        String line;
        String sessionId = null;
        String playerName = null;
    
        // Read headers
        while ((line = reader.readLine()) != null && !line.isEmpty())
        {
            // Look for the Cookie header
            if (line.toLowerCase().startsWith("cookie:"))
            {
                // Skip the "Cookie: " prefix
                String[] cookies = line.substring(7).trim().split(";");
                for (String cookie : cookies)
                {
                    String[] cookieParts = cookie.trim().split("=");
                    if (cookieParts[0].equals("SESSID"))
                    {
                        sessionId = cookieParts[1];
                        break;
                    }
                }
            }
        }
    
        // Read the body of the POST request
        char[] body = new char[1024];
        int length = reader.read(body);
        String requestBody = new String(body, 0, length);
    
        // Parse the request body to get the player name
        String[] params = requestBody.split("&");
        for (String param : params)
        {
            if (param.startsWith("playerName="))
            {
                playerName = URLDecoder.decode(param.split("=")[1], "UTF-8");
                break;
            }
        }
    
        // Associate the name with the session
        if (sessionId != null && activeSessions.containsKey(sessionId))
        {
            System.out.println("Session ID: " + sessionId + ", Name set: " + playerName);
            activeSessions.get(sessionId).setPlayerName(playerName);
        } 
        else
        {
            System.out.println("Session not found for player name submission.");
        }
    
        return playerName;
    }
    
    private static void sendLeaderboardHtmlPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
    
        String httpResponse = "HTTP/1.1 200 OK\r\n" +
        "Content-Type: text/html\r\n" +
        "Transfer-Encoding: chunked\r\n" +
        "Connection: close\r\n" +
        "\r\n";
        output.write(httpResponse.getBytes());
        output.flush();

        String html = "<!DOCTYPE html>\n"
        + "<html lang=\"en\">\n"
        + "<head>\n"
        + "    <meta charset=\"UTF-8\">\n"
        + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
        + "    <title>Leaderboard</title>\n"
        + "    <style>\n"
        + "        body {\n"
        + "            font-family: Arial, sans-serif;\n"
        + "            display: flex;\n"
        + "            justify-content: center;\n"
        + "            align-items: center;\n"
        + "            flex-direction: column;\n"
        + "            height: 100vh;\n"
        + "            margin: 0;\n"
        + "            background-color: #f4f4f4;\n"
        + "        }\n"
        + "        table {\n"
        + "            border-collapse: collapse;\n"
        + "            width: 60%;\n"
        + "            box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);\n"
        + "            background-color: #fff;\n"
        + "        }\n"
        + "        th, td {\n"
        + "            border: 1px solid #ddd;\n"
        + "            text-align: center;\n"
        + "            padding: 10px;\n"
        + "        }\n"
        + "        th {\n"
        + "            background-color: #333;\n"
        + "            color: white;\n"
        + "        }\n"
        + "        tr:nth-child(even) {\n"
        + "            background-color: #f9f9f9;\n"
        + "        }\n"
        + "        tr:hover {\n"
        + "            background-color: #f1f1f1;\n"
        + "        }\n"
        + "    </style>\n"
        + "</head>\n"
        + "<body>\n"
        + "    <h1>Leaderboard</h1>\n"
        + "    <table>\n"
        + "        <thead>\n"
        + "            <tr>\n"
        + "                <th>Rank</th>\n"
        + "                <th>Name</th>\n"
        + "                <th>Score</th>\n"
        + "            </tr>\n"
        + "        </thead>\n"
        + "        <tbody>\n"
        + "        </tbody>\n"
        + "    </table>\n"
        + "    <script>\n"
        + "        // Connect to the WebSocket server\n"
        + "        const ws = new WebSocket(\"ws://localhost:8013/ws\");\n"
        + "\n"
        + "        // WebSocket event listeners\n"
        + "        ws.onopen = function(event) {\n"
        + "            status.textContent = \"Connected to the game server.\";\n"
        + "            console.log(\"Connected to the game server.\");\n"
        + "        };\n"
        + "\n"
        + "        ws.onmessage = (event) => {\n"
        + "            const data = event.data;\n"
        + "            if (data.includes(\"LEADERBOARD\")) {\n"
        + "                // Parse the JSON data\n"
        + "                const jsonData = JSON.parse(data);\n"
        + "                const leaderboard = jsonData.LEADERBOARD;\n"
        + "\n"
        + "                const tbody = document.querySelector(\"tbody\");\n"
        + "                tbody.innerHTML = \"\";\n"
        + "                leaderboard.forEach((player, index) => {\n"
        + "                    const tr = document.createElement(\"tr\");\n"
        + "                    tr.innerHTML = `\n"
        + "                        <td>${index + 1}</td>\n"
        + "                        <td>${player.name}</td>\n"
        + "                        <td>${player.time}</td>\n"
        + "                    `;\n"
        + "                    tbody.appendChild(tr);\n"
        + "                });\n"
        + "            }\n"
        + "        };"
        + "    </script>\n"
        + "</body>\n"
        + "</html>\n";
    
        sendChunkedResponse(output, html);
        sendFinalChunk(output);
        output.close();
    }

    private static void sendPlayHtmlPage(Socket clientSocket) throws IOException
    {
        OutputStream output = clientSocket.getOutputStream();
    
        String httpResponse = "HTTP/1.1 200 OK\r\n" +
        "Content-Type: text/html\r\n" +
        "Transfer-Encoding: chunked\r\n" +
        "Connection: close\r\n" +
        "\r\n";
        output.write(httpResponse.getBytes());
        output.flush();

        String script = "<script>\n" +
        "    // Connect to the WebSocket server\n" +
        "    const ws = new WebSocket(\"ws://localhost:8013/ws\");\n" +
        "    let bombImage = \"\";\n" +
        "    let flagImage = \"\";\n" +
        "\n" +
        "    // WebSocket event listeners\n" +
        "    ws.onopen = function(event) {\n" +
        "        status.textContent = \"Connected to the game server.\";\n" +
        "        console.log(\"Connected to the game server.\");\n" +
        "    };\n" +
        "\n" +
        "    ws.onmessage = (event) => {\n" +
        "        const gridData = event.data;\n" +
        "        console.log(\"Received:\", gridData);\n" +
        "        if(gridData.includes(\"GAME LOST\")) {\n" +
        "            status.textContent = \"GAME LOST\";\n" +
        "            updateGrid(gridData);\n" +
        "        } else if(gridData.includes(\"GAME WON\")) {\n" +
        "            status.textContent = \"GAME WON\";\n" +
        "            updateGrid(gridData);\n" +
        "        } else if(gridData.includes(\"GAME NOT STARTED\")) {\n" +
        "            status.textContent = \"GAME NOT STARTED\";\n" +
        "        } else if (gridData.includes(\"Bomb:\")) {\n" +
        "            bombImage = \"data:image/png;base64,\" + gridData.replace(\"Bomb:\", \"\");\n" +
        "            console.log(\"Bomb Image Data:\", bombImage);\n" +
        "        } else if (gridData.includes(\"Flag:\")) {\n" +
        "            flagImage = \"data:image/png;base64,\" + gridData.replace(\"Flag:\", \"\");\n" +
        "            console.log(\"Flag Image Data:\", flagImage);\n" +
        "        } else {\n" +
        "            updateGrid(gridData);\n" +
        "        }\n" +
        "    };\n" +
        "\n" +
        "    ws.onerror = (error) => {\n" +
        "        status.textContent = \"WebSocket error: \" + error.message;\n" +
        "        console.error(\"WebSocket error: \", error);\n" +
        "    };\n" +
        "\n" +
        "    ws.onclose = () => {\n" +
        "        status.textContent = \"Disconnected from the server.\";\n" +
        "        console.log(\"Disconnected from the server.\");\n" +
        "    };\n" +
        "\n" +
        "    // Elements\n" +
        "    const grid = document.getElementById(\"grid\");\n" +
        "    const status = document.getElementById(\"status\");\n" +
        "    const cheatButton = document.getElementById(\"cheat\");\n" +
        "    cheatButton.addEventListener(\"click\", () => {\n" +
        "        ws.send(\"CHEAT\");\n" +
        "    });\n" +
        "\n" +
        "    // Initialize the grid\n" +
        "    const rows = 7, cols = 7;\n" +
        "    const cells = [];\n" +
        "    for (let i = 0; i < rows; i++) {\n" +
        "        for (let j = 0; j < cols; j++) {\n" +
        "            const cell = document.createElement(\"div\");\n" +
        "            cell.classList.add(\"cell\");\n" +
        "            cell.dataset.row = i;\n" +
        "            cell.dataset.col = j;\n" +
        "            grid.appendChild(cell);\n" +
        "            cells.push(cell);\n" +
        "            cell.addEventListener(\"click\", () => ws.send(`TRY ${i} ${j}`));\n" +
        "            cell.addEventListener(\"contextmenu\", (e) => {\n" +
        "                e.preventDefault();\n" +
        "                ws.send(`FLAG ${i} ${j}`);\n" +
        "            });\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Update the grid based on server data\n" +
        "    function updateGrid(gridData) {\n" +
        "        const rows = gridData.split(\"\\r\\n\").filter(line => line.trim() !== \"\");\n" +
        "        for (let i = 0; i < rows.length; i++) {\n" +
        "            for (let j = 0; j < rows[i].length; j++) {\n" +
        "                const cell = cells[i * cols + j];\n" +
        "                const state = rows[i][j];\n" +
        "                cell.innerHTML = \"\";\n" +
        "                cell.className = \"cell\";\n" +
        "                \n" +
        "                if (state === \"#\") {\n" +
        "                    cell.textContent = \"\";\n" +
        "                } else if (!isNaN(state) && state !== \" \") {\n" +
        "                    cell.textContent = state;\n" +
        "                    cell.classList.add(`number-${state}`);\n" +
        "                    cell.classList.add(\"revealed\");\n" +
        "                } else if (state === \"B\" && bombImage) {\n" +
        "                    const img = document.createElement(\"img\");\n" +
        "                    img.src = bombImage;\n" +
        "                    img.alt = \"Bomb\";\n" +
        "                    img.style.width = \"100%\";\n" +
        "                    img.style.height = \"100%\";\n" +
        "                    cell.appendChild(img);\n" +
        "                    cell.classList.add(\"revealed\");\n" +
        "                } else if (state === \"F\" && flagImage) {\n" +
        "                    const img = document.createElement(\"img\");\n" +
        "                    img.src = flagImage;\n" +
        "                    img.alt = \"Flag\";\n" +
        "                    img.style.width = \"100%\";\n" +
        "                    img.style.height = \"100%\";\n" +
        "                    cell.appendChild(img);\n" +
        "                    cell.classList.add(\"flagged\");\n" +
        "                }\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "</script>";        
                              
        String style = "<style>\n" +
        "    body {\n" +
        "        font-family: Arial, sans-serif;\n" +
        "        margin: 0;\n" +
        "        padding: 0;\n" +
        "        display: absolute;\n" +
        "        justify-content: center;\n" +
        "        align-items: center;\n" +
        "        height: 100vh;\n" +
        "        flex-direction: column;\n" +
        "    }\n" +
        "    .container {\n" +
        "        display: flex;\n" +
        "        margin: 50px;\n" +
        "        flex-direction: column;\n" +
        "        justify-content: center;\n" +
        "        align-items: center;\n" +
        "        border: 1px solid #000000;\n" +
        "        border-radius: 5px;\n" +
        "    }\n" +
        "    h1 {\n" +
        "        background-color: #333;\n" +
        "        color: #fff;\n" +
        "        margin: 0;\n" +
        "        padding: 10px;\n" +
        "        text-align: center;\n" +
        "        width: 100%;\n" +
        "    }\n" +
        "    p {\n" +
        "        text-align: center;\n" +
        "        margin: 5px;\n" +
        "    }\n" +
        "    form {\n" +
        "        margin-top: 20px;\n" +
        "        display: flex;\n" +
        "        flex-direction: column;\n" +
        "        align-items: center;\n" +
        "    }\n" +
        "    input[type=\"text\"] {\n" +
        "        padding: 10px;\n" +
        "        margin: 5px;\n" +
        "        font-size: 16px;\n" +
        "        text-align: center;\n" +
        "        border: 1px solid #ccc;\n" +
        "        border-radius: 4px;\n" +
        "        width: 200px;\n" +
        "    }\n" +
        "    input[type=\"submit\"] {\n" +
        "        padding: 10px 20px;\n" +
        "        font-size: 16px;\n" +
        "        color: #fff;\n" +
        "        background-color: #333;\n" +
        "        border: none;\n" +
        "        border-radius: 4px;\n" +
        "        cursor: pointer;\n" +
        "    }\n" +
        "    input[type=\"submit\"]:hover {\n" +
        "        background-color: #555;\n" +
        "    }\n" +
        "    #grid {\n" +
        "        display: grid;\n" +
        "        grid-template-columns: repeat(7, 40px);\n" +
        "        grid-gap: 5px;\n" +
        "        justify-content: center;\n" +
        "    }\n" +
        "    .cell {\n" +
        "        width: 40px;\n" +
        "        height: 40px;\n" +
        "        border: 1px solid #ccc;\n" +
        "        display: flex;\n" +
        "        align-items: center;\n" +
        "        justify-content: center;\n" +
        "        background-color: #757575;\n" +
        "        cursor: pointer;\n" +
        "    }\n" +
        "    .cell:hover {\n" +
        "        background-color: #eee;\n" +
        "    }\n" +
        "    .cell.number-1 { color: blue; }\n" +
        "    .cell.number-2 { color: green; }\n" +
        "    .cell.number-3 { color: red; }\n" +
        "    .cell.number-4 { color: darkblue; }\n" +
        "    .cell.number-5 { color: brown; }\n" +
        "    .cell.number-6 { color: cyan; }\n" +
        "    .cell.number-7 { color: black; }\n" +
        "    .cell.number-8 { color: gray; }\n" +
        "    .revealed {\n" +
        "        background-color: #fff;\n" +
        "    }\n" +
        "    .flagged {\n" +
        "        background-color: #ffa;\n" +
        "    }\n" +
        "</style>";                                            

        String htmlContent = "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "    <meta charset=\"UTF-8\">\n" +
        "    <title>Play Minesweeper</title>\n" +
            style + "\n" +
        "</head>\n" +
        "<body>\n" +
        "    <h1>Minesweeper</h1>\n" +
        "    <div class=\"container\">\n" +
        "        <form method=\"POST\" action=\"/submitName\">\n" +
        "            <input type=\"text\" name=\"playerName\" placeholder=\"Your name\" required />\n" +
        "            <input type=\"submit\" value=\"Submit Name\" />\n" +
        "        </form>\n" +
        "    </div>\n" +
        "    <div id=\"grid\"></div>\n" +
        "    <p id=\"status\"></p>\n" +
        "    <form method=\"POST\" action=\"\">\n" +
        "        <input type=\"submit\" value=\"CHEAT\" id=\"cheat\"/>\n" +
        "    </form>\n" +
        "    <form method=\"POST\" action=\"/leaderboard\">\n" +
        "        <input type=\"submit\" value=\"LEADERBOARD\"\"/>\n" +
        "    </form>\n" +
            script + "\n" +
        "</body>\n" +
        "</html>";
        
        sendChunkedResponse(output, htmlContent);
        sendFinalChunk(output);
        output.close();
    }

    /**
     * Send a chunked response to the client.
     * @param output The output stream to the client.
     * @param content The content to send.
     * @throws IOException
     */
    private static void sendChunkedResponse(OutputStream output, String content) throws IOException {
        int maxChunkSize = 128; // Maximum chunk size in bytes
        byte[] contentBytes = content.getBytes();
        int contentLength = contentBytes.length;
    
        for (int i = 0; i < contentLength; i += maxChunkSize) {
            int chunkSize = Math.min(maxChunkSize, contentLength - i);
            String chunkSizeHex = Integer.toHexString(chunkSize) + "\r\n";
            output.write(chunkSizeHex.getBytes());
            output.write(contentBytes, i, chunkSize);
            output.write("\r\n".getBytes());
            output.flush();
        }
    }
    
    /**
     * Send the final chunk to the client.
     * @param output The output stream to the client.
     * @throws IOException
     */
    private static void sendFinalChunk(OutputStream output) throws IOException {
        output.write("0\r\n\r\n".getBytes());
        output.flush();
    }
}
