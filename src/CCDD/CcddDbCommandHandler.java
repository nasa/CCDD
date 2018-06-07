/**
 * CFS Command & Data Dictionary database command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DB_SAVE_POINT_NAME;
import static CCDD.CcddConstants.EventLogMessageType.COMMAND_MSG;

import java.awt.Component;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DbCommandType;

/**************************************************************************************************
 * CFS Command & Data Dictionary database command handler class
 *************************************************************************************************/
public class CcddDbCommandHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddEventLogDialog eventLog;

    // PostgreSQL database connection
    private Connection connection;

    // PostgreSQL database statement
    private Statement statement;

    // Command to create a save point
    private static final String SAVE_POINT_COMMAND = "SAVEPOINT " + DB_SAVE_POINT_NAME + ";";

    // Flag to enable/disable creating a save point prior to a transaction
    private boolean savePointEnabled;

    // Flag indicating that a save point has been created for this transaction block
    private boolean isSavePointCreated;

    /**********************************************************************************************
     * Database command handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    protected CcddDbCommandHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Initialize the save point flags
        setSavePointEnable(false);
    }

    /**********************************************************************************************
     * Create a reference to the session event log. This can't be done initially since the event
     * log hasn't been created when this class is instantiated
     *********************************************************************************************/
    protected void setEventLog()
    {
        eventLog = ccddMain.getSessionEventLog();
    }

    /**********************************************************************************************
     * Set the database connection
     *
     * @param connection
     *            database connection
     *********************************************************************************************/
    protected void setConnection(Connection connection)
    {
        this.connection = connection;
    }

    /**********************************************************************************************
     * Get the database connection
     *
     * @return The database connection
     *********************************************************************************************/
    protected Connection getConnection()
    {
        return connection;
    }

    /**********************************************************************************************
     * Set the connection statement
     *
     * @param statement
     *            connection statement
     *********************************************************************************************/
    protected void setStatement(Statement statement)
    {
        this.statement = statement;
    }

    /**********************************************************************************************
     * Execute a database query command and log the command to the session log
     *
     * @param command
     *            SQL query command to execute
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @return Command results; returns null if no connection exists to the server
     *
     * @throws SQLException
     *             If no connection exists to the server
     *********************************************************************************************/
    protected ResultSet executeDbQuery(String command, Component component) throws SQLException
    {
        return (ResultSet) executeDbStatement(DbCommandType.QUERY, command, component);
    }

    /**********************************************************************************************
     * Execute a database update command and log the command to the session log
     *
     * @param command
     *            SQL update command to execute
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @return Command result row count; returns null if no connection exists to the server
     *
     * @throws SQLException
     *             If no connection exists to the server
     *********************************************************************************************/
    protected int executeDbUpdate(String command, Component component) throws SQLException
    {
        return (Integer) executeDbStatement(DbCommandType.UPDATE, command, component);
    }

    /**********************************************************************************************
     * Execute a database command and log the command to the session log
     *
     * @param command
     *            SQL update command to execute
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @return true if the first result is a ResultSet object, or false if it is an update count or
     *         there are no results; returns null if no connection exists to the server
     *
     * @throws SQLException
     *             If no connection exists to the server
     *********************************************************************************************/
    protected boolean executeDbCommand(String command, Component component) throws SQLException
    {
        return (Boolean) executeDbStatement(DbCommandType.COMMAND, command, component);
    }

    /**********************************************************************************************
     * Execute a database update statement and log the command to the session log
     *
     * @param commandType
     *            command type (DbCommandType)
     *
     * @param command
     *            SQL update command to execute
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @return Command result (content is dependent on the command type); returns null if no
     *         connection exists to the server
     *
     * @throws SQLException
     *             If no connection exists to the server
     *********************************************************************************************/
    protected Object executeDbStatement(DbCommandType commandType,
                                        String command,
                                        Component component) throws SQLException
    {
        Object result = null;

        // Check if creation of a save point is enabled and a save point hasn't already been
        // created
        if (savePointEnabled && !isSavePointCreated)
        {
            // Execute the command to create a save point
            statement.execute(SAVE_POINT_COMMAND);

            // Log the save point command
            eventLog.logEvent(COMMAND_MSG, SAVE_POINT_COMMAND);

            // Set the flag to indicate the save point command has been created
            isSavePointCreated = true;
        }

        // Log the command
        eventLog.logEvent(COMMAND_MSG, command);

        // Check if no valid database connection exists
        if (statement == null)
        {
            throw new SQLException("no database connection");
        }

        try
        {
            switch (commandType)
            {
                case QUERY:
                    // Execute the query command
                    result = statement.executeQuery(command);
                    break;

                case COMMAND:
                    // Execute the command
                    result = statement.execute(command);
                    break;

                case UPDATE:
                    // Execute the update command
                    result = statement.executeUpdate(command);
                    break;
            }

            // Check if auto-commit is disabled and a save point isn't established
            if (connection.getAutoCommit() == false && !savePointEnabled)
            {
                // Commit the change to the database
                connection.commit();
            }
        }
        catch (SQLException se)
        {
            // Check if auto-commit is disabled and a save point isn't established
            if (connection.getAutoCommit() == false && !savePointEnabled)
            {
                try
                {
                    // The command failed to complete successfully; revert the change to the
                    // database
                    connection.rollback();
                }
                catch (SQLException se2)
                {
                    // Inform the user that rolling back the changes failed
                    eventLog.logFailEvent(component,
                                          "Cannot revert changes project; cause '"
                                                     + se2.getMessage()
                                                     + "'",
                                          "<html><b>Cannot revert changes to project");
                }
            }

            // Re-throw the exception so that the caller can handle it
            throw new SQLException(se.getMessage());
        }

        return result;
    }

    /**********************************************************************************************
     * Get the save point status
     *
     * @return true if a save point is enabled and exists
     *********************************************************************************************/
    protected boolean getSavePointEnable()
    {
        return savePointEnabled && isSavePointCreated;
    }

    /**********************************************************************************************
     * Enable or disable creation of a save point prior to a transaction
     *
     * @param enable
     *            true to enable creating a save point
     *********************************************************************************************/
    protected void setSavePointEnable(boolean enable)
    {
        savePointEnabled = enable;
        isSavePointCreated = false;
    }

    /**********************************************************************************************
     * Retrieve a list from the server or database. The command strings are set up to explicitly
     * sort the list alphabetically, without regard to capitalization
     *
     * @param listType
     *            type of list to be retrieved
     *
     * @param listOption
     *            array containing replacement text within a command; null if none is needed
     *
     * @param dialog
     *            GUI component calling this method
     *
     * @return String array containing the requested list items in alphabetical order; an empty
     *         array if no items exist
     *********************************************************************************************/
    protected String[] getList(DatabaseListCommand listType,
                               String[][] listOption,
                               Component parent)
    {
        // Create a list to contain the query results
        List<String> list = new ArrayList<String>();

        try
        {
            // Execute the command and obtain the results
            ResultSet resultSet = executeDbQuery(listType.getListCommand(listOption), parent);

            // TODO ADDED NULL CHECK FOR TROUBLESHOOTING ascended121's ISSUE
            if (resultSet == null)
            {
                throw new SQLException("list query returned null ResultSet");
            }

            // Step through each of the results
            while (resultSet.next())
            {
                switch (listType)
                {
                    case KEYWORDS:
                        // Add the keyword to the list
                        list.add(resultSet.getString("WORD"));
                        break;

                    default:
                        // TODO ADDED NULL CHECK FOR TROUBLESHOOTING ascended121's ISSUE
                        // Get the result
                        String result = resultSet.getString(1);

                        // Check if the result is valid
                        if (result != null)
                        {
                            // Get the result to the specified list
                            list.add(result.trim());
                        }
                        else
                        {
                            System.out.println("ResultSet string is null"); // TODO
                        }

                        break;
                }
            }

            resultSet.close();
        }
        catch (SQLException se)
        {
            // Inform the user that the list retrieval failed
            eventLog.logFailEvent(parent,
                                  "Cannot retrieve "
                                          + listType
                                          + " list; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot retrieve " + listType + " list");
        }

        return list.toArray(new String[0]);
    }
}