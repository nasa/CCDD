/**
 * CFS Command and Data Dictionary database command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.EventLogMessageType.COMMAND_MSG;

import java.awt.Component;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DbCommandType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableSizeInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary database command handler class
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

    // Save point for transaction roll backs
    private Savepoint savePoint;

    /**********************************************************************************************
     * Database command handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    protected CcddDbCommandHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Initialize the save point
        savePoint = null;
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
     *         there are no results
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
    private Object executeDbStatement(DbCommandType commandType,
                                      String command,
                                      Component component) throws SQLException
    {
        Object result = null;

        // Log the command
        eventLog.logEvent(COMMAND_MSG, command);

        // Check if no valid database connection exists
        if (!ccddMain.getDbControlHandler().isServerConnected())
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
            if (connection.getAutoCommit() == false && savePoint == null)
            {
                // Commit the change to the database
                connection.commit();
            }
        }
        catch (SQLException se)
        {
            try
            {
                // Check if auto-commit is disabled. Roll-backs aren't allowed if auto-commit
                // is enabled. Auto-commit is usually disabled, but there are instances where
                // it's enabled so this check is required to prevent an exception
                if (connection.getAutoCommit() == false)
                {
                    try
                    {
                        // Check if no save point exists
                        if (savePoint == null)
                        {
                            // Revert the change to the database to before the last uncommitted
                            // transaction
                            connection.rollback();
                        }
                        // The save point exists
                        else
                        {
                            // Revert any changes to the database to the save point
                            connection.rollback(savePoint);
                        }
                    }
                    catch (SQLException se2)
                    {
                        // Inform the user that rolling back the changes failed
                        eventLog.logFailEvent(component,
                                              "Cannot revert changes to project; cause '"
                                                         + se2.getMessage()
                                                         + "'",
                                              "<html><b>Cannot revert changes to project");
                    }
                    finally
                    {
                        savePoint = null;
                    }
                }

                // Re-throw the exception so that the caller can handle it
                throw new SQLException("Invalid SQL command; " + se.getMessage());
            }
            catch (SQLException se3)
            {
                // Check if the server is no longer connected
                if (!connection.isValid(ModifiableSizeInfo.POSTGRESQL_CONNECTION_TIMEOUT.getSize()))
                {
                    // Execute at least once; continue to execute as long as the user elects to
                    // attempt to reconnect
                    while (true)
                    {
                        // Check if the attempt to reconnect to the server is successful
                        if (!ccddMain.getDbControlHandler().reconnectToDatabase())
                        {
                            // Send the command again
                            return executeDbStatement(commandType, command, component);
                        }
                        // The connection attempt failed. Check if the user elects to try
                        // reconnecting again
                        else if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                           "<html><b>Server connection lost and "
                                                                                                    + "reconnection attempt failed; try again?",
                                                                           "Server Connection Lost",
                                                                           JOptionPane.QUESTION_MESSAGE,
                                                                           DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                        {
                            throw new SQLException("Connection to server lost");
                        }
                    }
                }
                // The server is connected. Shouldn't be able to get to this
                else
                {
                    throw new SQLException(se3.getMessage());
                }
            }
        }

        return result;
    }

    /**********************************************************************************************
     * Revert any changes to the database back to the save point, if it exists
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @throws SQLException
     *             If an error occurs rolling back to the save point
     *********************************************************************************************/
    protected void rollbackToSavePoint(Component component) throws SQLException
    {
        // Check if the save point exists
        if (savePoint != null)
        {
            try
            {
                // Revert any changes to the database to the save point
                connection.rollback(savePoint);
            }
            catch (SQLException se)
            {
                throw new SQLException(se);
            }
            finally
            {
                // Release the save point
                releaseSavePoint(component);
            }
        }
    }

    /**********************************************************************************************
     * Create the save point if it doesn't exist
     *
     * @param component
     *            GUI component over which to center any error dialog
     *
     * @throws SQLException
     *             If an error occurs creating the save point
     *********************************************************************************************/
    protected void createSavePoint(Component component) throws SQLException
    {
        // Check if the save point doesn't already exist
        if (savePoint == null)
        {
            // Create the save point
            savePoint = connection.setSavepoint();
        }
    }

    /**********************************************************************************************
     * Release the save point if it exists
     *
     * @param component
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void releaseSavePoint(Component component)
    {
        // Check if the save point exists
        if (savePoint != null)
        {
            try
            {
                // Release the save point. Each save point takes up shared memory, so releasing it
                // prevents a memory 'leak'
                connection.releaseSavepoint(savePoint);
            }
            catch (SQLException se)
            {
                // Inform the user that the save point can't be released
                eventLog.logFailEvent(component,
                                      "Cannot release save point; cause '"
                                                 + se.getMessage()
                                                 + "'",
                                      "<html><b>Cannot release save point");
            }
            finally
            {
                // Reset the save point whether or not the release is successful
                savePoint = null;
            }
        }
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
     * @param parent
     *            GUI component over which to center any error dialog
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

            // Check if the query failed
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
                        // Get the result
                        String result = resultSet.getString(1);

                        // Check if the result is valid
                        if (result != null)
                        {
                            // Get the result to the specified list
                            list.add(result.trim());
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