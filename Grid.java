public class Grid
{
    private short gridSize = 7;
    private short numberMines = gridSize;
    private short numberTurnsPlayed = 0;
    private Coordinate[][] currentGrid;

    /**
     * Constructor for the Grid class.
     * @param gridSize The size of the grid.
     * @implNote The number of mines is equal to the size of the grid.
     */
    public Grid(short gridSize)
    {
        if(gridSize < 1)
        {
            System.out.println("Invalid grid size.");
            return;
        }
        this.gridSize = gridSize;
        this.numberMines = gridSize;
        numberTurnsPlayed = 0;
        createInitialBoard();
    }

    /**
     * Get the size of the board.
     * @return The size of the board.
     */
    public short getBoardSize()
    {
        return gridSize;
    }

    /**
     * Create the initial board with all cells hidden.
     * @implNote The board is represented as a 2D array of characters.
     */
    private void createInitialBoard()
    {
        Coordinate[][] board = new Coordinate[gridSize][gridSize];
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                board[i][j] = new Coordinate();
            }
        }
        currentGrid = board;
    }

    /**
     * Compute the final grid with the number of mines in the neighborhood of each cell.
     * @implNote The final grid is computed after the first move.
     */
    public void computeFinalGrid()
    {
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                if(currentGrid[i][j].getValue() != Coordinate.BOMB)
                {
                    currentGrid[i][j].setValue
                        ((char)(getNumberOfAdjacentMines(i, j) + '0'));
                }
            }
        }
    }

    /**
     * Flag a cell on the board.
     * @param x The x coordinate of the cell.
     * @param y The y coordinate of the cell.
     * @implNote Used for the FLAG command.
     */
    public void flagCell(int x, int y)
    {
        if(x < 0 || x >= gridSize || y < 0 || y >= gridSize)
        {
            System.out.println("Invalid coordinates.");
            return;
        }
        // If the cell is already flagged, unflag it
        if(currentGrid[x][y].getStatus() == Coordinate.Status.FLAGGED)
        {
            currentGrid[x][y].setStatus(Coordinate.Status.UNREVEALED);
        }
        // If the cell is unrevealed, flag it
        else if(currentGrid[x][y].getStatus() == Coordinate.Status.UNREVEALED)
        {
            currentGrid[x][y].setStatus(Coordinate.Status.FLAGGED);
        }
        // If the cell is revealed, do nothing
    }

    /**
     * Reveal a cell on the board.
     * @param x The x coordinate of the cell.
     * @param y The y coordinate of the cell.
     * @implNote Used for the TRY command.
     */
    public void revealCell(int x, int y)
    {
        if(x < 0 || x >= gridSize || y < 0 || y >= gridSize)
        {
            System.out.println("Invalid coordinates.");
            return;
        }
        // If this is the first turn, place mines and compute the final grid
        if(numberTurnsPlayed == 0)
        {
            placeMines(x, y);
            computeFinalGrid();
        }
        // If the cell is already revealed, do nothing
        else if(currentGrid[x][y].getStatus() == Coordinate.Status.REVEALED)
        {
            return;
        }
        // If the cell is a bomb, game over
        else if(currentGrid[x][y].getValue() == Coordinate.BOMB)
        {
            currentGrid[x][y].setStatus(Coordinate.Status.REVEALED);
            return;
        }
        numberTurnsPlayed++;
        // If the cell is empty, reveal all adjacent cells
        propagateReveal(x, y);
    }

    /**
     * Propagate the reveal operation to all adjacent cells.
     * @param x The x coordinate of the cell.
     * @param y The y coordinate of the cell.
     * @implNote Used after revealing an empty cell.
     */
    private void propagateReveal(int x, int y)
    {
        if(x < 0 || x >= gridSize || y < 0 || y >= gridSize)
        {
            System.out.println("Invalid coordinates.");
            return;
        }
        if(currentGrid[x][y].getValue() == Coordinate.BOMB)
        {
            return;
        }
        if (currentGrid[x][y].getStatus() == Coordinate.Status.REVEALED)
        {
            return;
        }
        currentGrid[x][y].setStatus(Coordinate.Status.REVEALED);
        // If the cell is empty, reveal all adjacent cells
        if(currentGrid[x][y].getValue() == '0')
        {
            for(int i = x - 1; i <= x + 1; i++)
            {
                for(int j = y - 1; j <= y + 1; j++)
                {
                    if(i >= 0 && i < gridSize && j >= 0 && j < gridSize)
                    {
                        if(i != x || j != y)
                        {
                            propagateReveal(i, j);
                        }
                    }
                }
            }
        }
    }

    /**
     * Convert the grid to a string.
     * @param forceReveal If true, reveal all cells. (CHEAT)
     * @return The grid as a string.
     */
    public String convertGridToProtocol(boolean forceReveal)
    {
        // Check if the game is won or lost => force reveal
        boolean isWin = isWin();
        boolean isLose = isLose();
        if(isWin || isLose)
        {
            forceReveal = true;
        }

        // Convert the grid to a string
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                // If we want to force reveal the grid, show all cells (CHEAT)
                if(forceReveal)
                {
                    sb.append(currentGrid[i][j].getValue());
                }
                // If the cell is unrevealed, show the unrevealed character
                else if(currentGrid[i][j].getStatus() == Coordinate.Status.UNREVEALED)
                {
                    sb.append(Coordinate.UNREVEALED);
                }
                // If the cell is revealed, show the value
                else if(currentGrid[i][j].getStatus() == Coordinate.Status.REVEALED)
                {
                    sb.append(currentGrid[i][j].getValue());
                }
                // If the cell is flagged, show the flag character
                else
                {
                    sb.append(Coordinate.FLAG);
                }
            }
            sb.append("\r\n");
        }

        // Append the game status if game over
        if(isWin)
        {
            sb.append("GAME WON" + "\r\n");
        }
        else if(isLose)
        {
            sb.append("GAME LOST" + "\r\n");
        }

        sb.append("\r\n");
        return sb.toString();
    }

    /**
     * Reveal all cells on the board. (CHEAT)
     * @return The grid as a string (following the protocol).
     * @implNote This method does not set the current grid.
     */
    public String revealAllCells()
    {
        if(numberTurnsPlayed == 0)
        {
            return new String("GAME NOT STARTED\r\n\r\n");
        }
        return convertGridToProtocol(true);
    }

    /**
     * Get the number of mines in the neighborhood of a cell.
     * @param x The x coordinate of the cell.
     * @param y The y coordinate of the cell.
     * @return The number of mines in the neighborhood of the cell.
     */
    private int getNumberOfAdjacentMines(int x, int y)
    {
        int numMines = 0;
        for(int i = x - 1; i <= x + 1; i++)
        {
            for(int j = y - 1; j <= y + 1; j++)
            {
                if(i >= 0 && i < gridSize && j >= 0 && j < gridSize)
                {
                    if(i == x && j == y)
                    {
                        continue;
                    }
                    if(currentGrid[i][j].getValue() == Coordinate.BOMB)
                    {
                        numMines++;
                    }
                }
            }
        }
        return numMines;
    }

    /**
     * Place mines on the board.
     * @implNote The mines are placed randomly on the board.
     */
    private void placeMines(int xAvoid, int yAvoid)
    {
        for(int i = 0; i < numberMines; i++)
        {
            int x = (int)(Math.random() * gridSize);
            int y = (int)(Math.random() * gridSize);
            // If there is already a mine at this location or coordinate to avoid, try again
            if(currentGrid[x][y].getValue() == Coordinate.BOMB 
                || (x == xAvoid && y == yAvoid))
            {
                i--;
            }
            else
            {
                currentGrid[x][y].setValue(Coordinate.BOMB);
            }
        }
    }

    /**
     * Print the board to the console.
     * @implNote Used for debugging.
     */
    public void printBoard()
    {
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                System.out.print(currentGrid[i][j].getValue() + " ");
            }
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Is the current board a win?
     * @return True if the current board is a win, false otherwise.
     */
    public boolean isWin()
    {
        short numUnrevealed = 0;
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                // Count the number of unrevealed/flagged cells
                if(currentGrid[i][j].getStatus() 
                    == Coordinate.Status.UNREVEALED ||
                    currentGrid[i][j].getStatus() 
                    == Coordinate.Status.FLAGGED)
                {
                    numUnrevealed++;
                }
            }
        }

        // If the number of unrevealed cells is equal to the number of mines, the game is won
        return numUnrevealed == numberMines;
    }

    /**
     * Is the current board a loss?
     * @return True if the current board is a loss, false otherwise.
     */
    public boolean isLose()
    {
        for(int i = 0; i < gridSize; i++)
        {
            for(int j = 0; j < gridSize; j++)
            {
                if(currentGrid[i][j].getValue() == Coordinate.BOMB)
                {
                    // If a bomb is revealed, the game is lost
                    if(currentGrid[i][j].getStatus() 
                        == Coordinate.Status.REVEALED)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
