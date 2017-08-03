/**
 * CFS Command & Data Dictionary database control handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.DATABASE;
import static CCDD.CcddConstants.DATABASE_BACKUP_PATH;
import static CCDD.CcddConstants.DATABASE_COMMENT_SEPARATOR;
import static CCDD.CcddConstants.DATABASE_DRIVER;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_HOST;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_PORT;
import static CCDD.CcddConstants.ENUMERATION_SEPARATOR;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.MACRO_IDENTIFIER;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_HOST;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_PORT;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.USER;
import static CCDD.CcddConstants.ConnectionType.NO_CONNECTION;
import static CCDD.CcddConstants.ConnectionType.TO_DATABASE;
import static CCDD.CcddConstants.ConnectionType.TO_SERVER_ONLY;
import static CCDD.CcddConstants.EventLogMessageType.COMMAND_MSG;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddConstants.ConnectionType;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DatabaseObject;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary database control handler class
 *****************************************************************************/
public class CcddDbControlHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private CcddEventLogDialog eventLog;

    // SQL server type and host
    private String serverPort;
    private String serverHost;

    // Currently open database name, owner, user, and user password
    private String activeDatabase;
    private String activeOwner;
    private String activeUser;
    private String activePassword;

    // Flag indicating if the database connection attempt failed due to a
    // missing password
    private boolean isMissingPassword;

    // Array of reserved words
    private String[] keyWords;

    // Current connection status (none, connected to the server (default
    // database), or connected to a database
    private ConnectionType connectionStatus;

    // SQL database connection
    private Connection connection;

    // File path and name to automatically backup the database to on first
    // connection
    private String backupFileName;

    // PostgreSQL function parameters
    private final String[][] functionParameters;

    // Temporary data storage table
    private static final String TEMP_TABLES = INTERNAL_TABLE_PREFIX + "temp_table";

    /**************************************************************************
     * Database control handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddDbControlHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create reference to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();

        // Initialize the database connection parameters
        connectionStatus = NO_CONNECTION;
        serverHost = DEFAULT_POSTGRESQL_HOST;
        serverPort = DEFAULT_POSTGRESQL_PORT;
        activeDatabase = "";
        activeOwner = "";
        activeUser = "";
        activePassword = "";
        backupFileName = "";

        // Reset the flag that indicates a connection failure occurred due to a
        // missing password
        isMissingPassword = false;

        // Create the parameters for the the 'by name' and 'by index'
        // postgreSQL functions
        functionParameters = new String[][] { {"name",
                                               DefaultColumn.VARIABLE_NAME.getDbName()},
                                             {"index",
                                              DefaultColumn.ROW_INDEX.getDbName()}};
    }

    /**************************************************************************
     * Create a reference to the session event log. This can't be done
     * initially since the event log hasn't been created when this class is
     * instantiated
     *************************************************************************/
    protected void setEventLog()
    {
        eventLog = ccddMain.getSessionEventLog();
    }

    /**************************************************************************
     * Get the server connection status
     * 
     * @return true if a connection to a database exists
     *************************************************************************/
    protected boolean isServerConnected()
    {
        return connectionStatus != NO_CONNECTION;
    }

    /**************************************************************************
     * Get the database connection status
     * 
     * @return true if a connection to a database other than the default exists
     *************************************************************************/
    protected boolean isDatabaseConnected()
    {
        return connectionStatus == TO_DATABASE;
    }

    /**************************************************************************
     * Get the database name
     * 
     * @return The database name
     *************************************************************************/
    protected String getDatabase()
    {
        return activeDatabase;
    }

    /**************************************************************************
     * Set the database name
     * 
     * @param database
     *            database name
     *************************************************************************/
    protected void setDatabase(String database)
    {
        activeDatabase = database;
    }

    /**************************************************************************
     * Get the PostgreSQL project database name
     * 
     * @return Name of the active PostgreSQL project database; returns
     *         '*server*' if no project database is open, but a connection to
     *         the PostgreSQL server is active, or '*none*' if no connection to
     *         the server is active
     *************************************************************************/
    protected String getProject()
    {
        return isDatabaseConnected()
                                    ? getDatabase()
                                    : (isServerConnected()
                                                          ? "*server*"
                                                          : "*none*");
    }

    /**************************************************************************
     * Get the PostgreSQL server host
     * 
     * @return PostgreSQL server host
     *************************************************************************/
    protected String getHost()
    {
        return serverHost;
    }

    /**************************************************************************
     * Set the SQL server host
     * 
     * @param host
     *            SQL server host
     *************************************************************************/
    protected void setHost(String host)
    {
        serverHost = host;
    }

    /**************************************************************************
     * Get the PostgreSQL server port
     * 
     * @return PostgreSQL server port
     *************************************************************************/
    protected String getPort()
    {
        return serverPort;
    }

    /**************************************************************************
     * Set the PostgreSQL server port
     * 
     * @param port
     *            PostgreSQL server port
     *************************************************************************/
    protected void setPort(String port)
    {
        serverPort = port;
    }

    /**************************************************************************
     * Set the PostgreSQL server information
     * 
     * @return PostgreSQL server information
     *************************************************************************/
    protected String getServer()
    {
        return serverHost
               + (serverPort.isEmpty()
                                      ? ""
                                      : ":" + serverPort);
    }

    /**************************************************************************
     * Get the server user name
     * 
     * @return The server user name
     *************************************************************************/
    protected String getUser()
    {
        return activeUser;
    }

    /**************************************************************************
     * Set the database user
     * 
     * @param user
     *            database user
     *************************************************************************/
    protected void setUser(String user)
    {
        activeUser = user;
    }

    /**************************************************************************
     * Get the database owner name
     * 
     * @return The database owner name
     *************************************************************************/
    protected String getOwner()
    {
        return activeOwner;
    }

    /**************************************************************************
     * Set the database password
     * 
     * @param password
     *            database password
     *************************************************************************/
    protected void setPassword(String password)
    {
        activePassword = password;
    }

    /**************************************************************************
     * Set the file path and name to automatically backup the database to on
     * first connecting
     * 
     * @param backupFileName
     *            backup file path and name
     *************************************************************************/
    protected void setBackupFileName(String backupFileName)
    {
        this.backupFileName = backupFileName;
    }

    /**************************************************************************
     * Get the list of database reserved words
     * 
     * @return String array containing the reserved words
     *************************************************************************/
    protected String[] getKeyWords()
    {
        return keyWords;
    }

    /**************************************************************************
     * Get the database server version number
     * 
     * @return String containing the database server version number; returns
     *         '*not connected*' if not connected to the PostgreSQL server
     *************************************************************************/
    protected String getDatabaseVersion()
    {
        String databaseVersion = "*not connected*";

        // Check if the database is connected
        if (isServerConnected())
        {
            try
            {
                // Get a reference to the database metadata
                DatabaseMetaData metaData = connection.getMetaData();

                // Get the database version number
                databaseVersion = String.valueOf(metaData.getDatabaseMajorVersion())
                                  + "."
                                  + String.valueOf(metaData.getDatabaseMinorVersion());
            }
            catch (SQLException se)
            {
                // Inform the user that the database version number is
                // unavailable
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot obtain database version number; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot obtain database version number");
            }
        }

        return databaseVersion;
    }

    /**************************************************************************
     * Get the JDBC version number
     * 
     * @return String containing the JDBC version number; returns '*not
     *         connected*' if if not connected to the PostgreSQL server
     *************************************************************************/
    protected String getJDBCVersion()
    {
        String jdbcVersion = "*not connected*";

        // Check if the database is connected
        if (isServerConnected())
        {
            try
            {
                // Get a reference to the database metadata
                DatabaseMetaData metaData = connection.getMetaData();

                // Get the JDBC version number
                jdbcVersion = String.valueOf(metaData.getDriverVersion())
                              + " (type "
                              + String.valueOf(metaData.getJDBCMajorVersion())
                              + ")";
            }
            catch (SQLException se)
            {
                // Inform the user that the JDBC version number is unavailable
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot obtain JDBC version number; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot obtain JDBC version number");
            }
        }

        return jdbcVersion;
    }

    /**************************************************************************
     * Retrieve a list of all users registered on the server
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return String array containing the user names
     *************************************************************************/
    protected String[] queryUserList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.USERS,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of databases with active connections by user
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return String array containing the database names and connected user
     *         names
     *************************************************************************/
    protected String[] queryActiveList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.ACTIVE_BY_USER,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of all roles registered on the server
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return String array containing the role names
     *************************************************************************/
    protected String[] queryRoleList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.ROLES,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve the name of the specified database's owner
     * 
     * @param databaseName
     *            name of the database for which the owner is requested
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return String array containing the name of the specified database's
     *         owner
     *************************************************************************/
    protected String[] queryDatabaseOwner(String databaseName,
                                          Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASE_OWNER,
                                 new String[][] {{"__", databaseName.toLowerCase()}},
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of all databases registered on the server
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return Array containing the database names and descriptions
     *************************************************************************/
    protected String[] queryDatabaseList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASES,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of the databases registered on the server for which the
     * current user has access
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @param userName
     *            user name
     * 
     * @return Array containing the database names and descriptions for which
     *         the current user has access
     *************************************************************************/
    protected String[] queryDatabaseByUserList(Component parent, String userName)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASES_BY_USER,
                                 new String[][] {{"_user_", userName}},
                                 parent);
    }

    /**************************************************************************
     * Get the server + database
     * 
     * @param databaseName
     *            database name
     * 
     * @return server + database
     *************************************************************************/
    private String getServerAndDatabase(String databaseName)
    {
        return getServer()
               + "/"
               + databaseName.toLowerCase();
    }

    /**************************************************************************
     * Get the database URL
     * 
     * @param databaseName
     *            database name
     * 
     * @return database URL
     *************************************************************************/
    protected String getDatabaseURL(String databaseName)
    {
        return "jdbc:postgresql://" + getServerAndDatabase(databaseName);
    }

    /**************************************************************************
     * Get the database comment without the CFS project identifier
     * 
     * @param databaseName
     *            database name
     * 
     * @return Database comment in the format <lock status (0 or 1)>,<visible
     *         project database name (with capitalization intact)>,<project
     *         description>; null if the comment cannot be retrieved
     *************************************************************************/
    protected String getDatabaseComment(String databaseName)
    {
        String comment = null;

        try
        {
            // Get the comment for the database
            ResultSet resultSet = dbCommand.executeDbQuery("SELECT description FROM pg_shdescription "
                                                           + "JOIN pg_database ON objoid = "
                                                           + "pg_database.oid WHERE datname = '"
                                                           + databaseName.toLowerCase()
                                                           + "';",
                                                           ccddMain.getMainFrame());
            resultSet.next();

            // Split the comment to remove the CFS project identifier, leaving
            // the lock status, project name, and description
            comment = resultSet.getString(1).substring(CCDD_PROJECT_IDENTIFIER.length());

            resultSet.close();
        }
        catch (SQLException se)
        {
            // Inform the user that loading the database comment failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot obtain comment for project database '"
                                      + databaseName
                                      + "'; cause '"
                                      + se.getMessage()
                                      + "'",
                                  "<html><b>Cannot obtain comment for project database '</b>"
                                      + databaseName
                                      + "<b>'");
        }

        return comment;
    }

    /**************************************************************************
     * Get the database lock status
     * 
     * @param databaseName
     *            database name
     * 
     * @return true if the database is locked, false if not locked, or null if
     *         the comment cannot be retrieved
     *************************************************************************/
    protected Boolean getDatabaseLockStatus(String databaseName)
    {
        Boolean lockStatus = null;

        // Get the database comment
        String comment = getDatabaseComment(databaseName);

        // Check if a comment was successfully retrieved
        if (comment != null)
        {
            // Determine the database lock status
            lockStatus = comment.startsWith("1");
        }

        return lockStatus;
    }

    /**************************************************************************
     * Set the database lock status
     * 
     * @param databaseName
     *            database name
     * 
     * @param lockStatus
     *            true if the database is locked; false if unlocked
     *************************************************************************/
    protected void setDatabaseLockStatus(String databaseName, boolean lockStatus)
    {
        // Get the database description
        String description = getDatabaseDescription(databaseName);

        // Check if a comment was successfully retrieved
        if (description != null)
        {
            try
            {
                // Set the database comment with the specified lock status
                dbCommand.executeDbUpdate(buildDatabaseCommentCommand(databaseName,
                                                                      lockStatus,
                                                                      description),
                                          ccddMain.getMainFrame());

                // Inform the user that the lock status update succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  "Project database '"
                                      + databaseName
                                      + "' "
                                      + (lockStatus
                                                   ? "locked"
                                                   : "unlocked"));
            }
            catch (SQLException se)
            {
                // Inform the user that setting the database comment failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot set comment for project database '"
                                          + databaseName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot set comment for project database '</b>"
                                          + databaseName
                                          + "<b>'");
            }
        }
    }

    /**************************************************************************
     * Get the database description
     * 
     * @param databaseName
     *            database name
     * 
     * @return The database description; null if no description exists
     *************************************************************************/
    protected String getDatabaseDescription(String databaseName)
    {
        String description = null;

        // Get the database comment
        String comment = getDatabaseComment(databaseName);

        // Check if a comment was successfully retrieved
        if (comment != null)
        {
            // Store the database description
            description = comment.split(DATABASE_COMMENT_SEPARATOR, 3)[2];
        }

        return description;
    }

    /**************************************************************************
     * Build the command to create the database comment. Delimit the text so
     * that special characters (e.g., single quotes) can be placed in the
     * description
     * 
     * @param databaseName
     *            database name
     * 
     * @param lockStatus
     *            true if the database is locked; false if unlocked
     * 
     * @param description
     *            database description
     * 
     * @return Command to create the database comment
     *************************************************************************/
    private String buildDatabaseCommentCommand(String databaseName,
                                               boolean lockStatus,
                                               String description)
    {
        return "COMMENT ON DATABASE "
               + databaseName.toLowerCase()
               + " IS "
               + ccddMain.getDbTableCommandHandler().delimitText(CCDD_PROJECT_IDENTIFIER
                                                                 + (lockStatus ? "1" : "0")
                                                                 + ";"
                                                                 + databaseName
                                                                 + ";"
                                                                 + description)
               + "; ";
    }

    /**************************************************************************
     * Build the command to change a database object's owner and grant access
     * privileges to the currently active database owner
     * 
     * @param object
     *            DatabaseObjects (DATABASE, TABLE, or FUNCTION)
     * 
     * @param objectName
     *            name of the database object to alter
     * 
     * @return Command to change a database object's owner and grant access
     *         privileges to the current group
     *************************************************************************/
    protected String buildOwnerCommand(DatabaseObject object,
                                       String objectName)
    {
        return buildOwnerCommand(activeOwner, object, objectName);
    }

    /**************************************************************************
     * Build the command to change a database object's owner and grant access
     * privileges to the specified database owner
     * 
     * @param ownerName
     *            name of the role or user that owns the database and its
     *            objects
     * 
     * @param object
     *            DatabaseObjects (DATABASE, TABLE, or FUNCTION)
     * 
     * @param objectName
     *            name of the database object to alter
     * 
     * @return Command to change a database object's owner and grant access
     *         privileges to the specified group
     *************************************************************************/
    private String buildOwnerCommand(String ownerName,
                                     DatabaseObject object,
                                     String objectName)
    {
        return "ALTER "
               + object.toString()
               + " "
               + objectName
               + " OWNER TO "
               + ownerName
               + "; GRANT ALL PRIVILEGES ON "
               + object.toString()
               + " "
               + objectName
               + " TO GROUP "
               + ownerName
               + "; ";
    }

    /**************************************************************************
     * Disable the database change auto-commit mode
     *************************************************************************/
    private void resetAutoCommit()
    {
        try
        {
            // Disable auto-commit for database changes
            connection.setAutoCommit(false);
        }
        catch (SQLException se)
        {
            // Inform the user that disabling the database auto-commit flag
            // failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot disable auto-commit; cause '"
                                      + se.getMessage()
                                      + "'",
                                  "<html><b>Cannot disable auto-commit");
        }
    }

    /**************************************************************************
     * Create a database
     * 
     * @param databaseName
     *            name of the database to create
     * 
     * @param ownerName
     *            name of the role or user that owns the database and its
     *            objects
     * 
     * @param description
     *            database description
     * 
     * @return true if the command completes successfully; false otherwise
     *************************************************************************/
    private boolean createDatabase(final String databaseName,
                                   String ownerName,
                                   String description)
    {
        boolean successFlag = true;

        try
        {
            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Execute the database update
            dbCommand.executeDbUpdate("CREATE DATABASE "
                                      + databaseName.toLowerCase()
                                      + "; "
                                      + buildDatabaseCommentCommand(databaseName,
                                                                    false,
                                                                    description)
                                      + buildOwnerCommand(ownerName,
                                                          DatabaseObject.DATABASE,
                                                          databaseName),
                                      ccddMain.getMainFrame());

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Project database '"
                                  + databaseName
                                  + "' created");
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot create project database '"
                                      + getServerAndDatabase(databaseName)
                                      + "'; cause '"
                                      + se.getMessage()
                                      + "'",
                                  "<html><b>Cannot create project database '</b>"
                                      + databaseName
                                      + "<b>'");
            successFlag = false;
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }

        return successFlag;
    }

    /**************************************************************************
     * Create a database. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     * 
     * @param databaseName
     *            name of the database to create
     * 
     * @param ownerName
     *            name of the role or user that owns the database and its
     *            objects
     * 
     * @param description
     *            database description
     *************************************************************************/
    protected void createDatabaseInBackground(final String databaseName,
                                              final String ownerName,
                                              final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Database creation command
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Create the database
                createDatabase(databaseName, ownerName, description);
            }
        });
    }

    /**************************************************************************
     * Create the reusable database functions and default tables. This does not
     * include the default column functions
     * 
     * @return true if an error occurs creating the database functions or
     *         tables
     *************************************************************************/
    private boolean createTablesAndFunctions()
    {
        boolean errorFlag = false;

        try
        {
            // Send command to create the procedural language in the database
            // if it does not already exists
            dbCommand.executeDbCommand("CREATE OR REPLACE FUNCTION make_plpgsql() "
                                       + "RETURNS VOID LANGUAGE SQL AS $$ "
                                       + "CREATE LANGUAGE plpgsql; $$; "
                                       + "SELECT CASE WHEN EXISTS(SELECT 1 "
                                       + "FROM pg_catalog.pg_language "
                                       + "WHERE lanname = 'plpgsql') "
                                       + "THEN NULL ELSE make_plpgsql() END; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "make_plpgsql()")
                                       + "DROP FUNCTION make_plpgsql();",
                                       ccddMain.getMainFrame());

            // Create function to delete functions whether or not the input
            // parameters match
            dbCommand.executeDbCommand("CREATE OR REPLACE FUNCTION delete_function("
                                       + "function_name text) RETURNS VOID AS $$ "
                                       + "BEGIN EXECUTE (SELECT 'DROP FUNCTION ' "
                                       + "|| oid::regproc || '(' || "
                                       + "pg_get_function_identity_arguments(oid) "
                                       + "|| ');' || E'\n' FROM pg_proc WHERE "
                                       + "proname = function_name AND "
                                       + "pg_function_is_visible(oid)); END $$ LANGUAGE plpgsql; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "delete_function(function_name text)"),
                                       ccddMain.getMainFrame());

            // Step through each internal table type
            for (InternalTable intTable : InternalTable.values())
            {
                // Check that this isn't the script table type. The script
                // table is a special type for storing specific scripts
                if (intTable != InternalTable.SCRIPT)
                {
                    // Check if the internal table doesn't exist
                    if (!ccddMain.getDbTableCommandHandler().isTableExists(intTable.getTableName(),
                                                                           ccddMain.getMainFrame()))
                    {
                        // Create the default internal table
                        dbCommand.executeDbCommand(buildInformationTableCommand(intTable),
                                                   ccddMain.getMainFrame());
                    }
                }
            }

            // Create function to search all tables for the input text with
            // case sensitivity determined by an input flag. A second flag
            // determines if the search string is treated as a literal string
            // or as a regular expression. If as a literal, then all
            // non-alphanumeric and non-space characters are escaped so that
            // these characters can be detected correctly. A third flag
            // determines if all tables or only the data tables (including
            // entries in the custom values table) are searched. Returns a
            // table giving the unique schema, table, column name, table
            // comment, and contents of the columns in the table row where the
            // text is found
            dbCommand.executeDbCommand(deleteFunction("search_tables")
                                       + "CREATE OR REPLACE FUNCTION search_tables("
                                       + "search_text text, no_case boolean, "
                                       + "allow_regex boolean, selected_tables text, "
                                       + "columns name[] DEFAULT '{}', all_schema "
                                       + "name[] DEFAULT '{public}') RETURNS table("
                                       + "schema_name text, table_name text, column_name "
                                       + "text, table_description text, column_value "
                                       + "text) AS $$ DECLARE search_text text := "
                                       + "regexp_replace(search_text, E'([^a-zA-Z0-9 ])', "
                                       + "E'\\\\\\\\\\\\1'); BEGIN FOR schema_name, "
                                       + "table_name, table_description, column_name IN "
                                       + "SELECT c.table_schema, c.table_name, "
                                       + "coalesce(d.description,''), c.column_name "
                                       + "FROM information_schema.columns c JOIN "
                                       + "information_schema.tables AS t ON "
                                       + "(t.table_name = c.table_name AND "
                                       + "t.table_schema = c.table_schema), "
                                       + "pg_description AS d RIGHT JOIN pg_class "
                                       + "ON d.objoid = pg_class.oid RIGHT JOIN "
                                       + "pg_namespace ON pg_class.relnamespace = "
                                       + "pg_namespace.oid WHERE (selected_tables ~* '"
                                       + SearchType.ALL.toString()
                                       + "' OR (selected_tables ~* '"
                                       + SearchType.PROTO.toString()
                                       + "' AND c.table_name !~ E'^"
                                       + INTERNAL_TABLE_PREFIX
                                       + ".*$') OR (selected_tables ~* '"
                                       + SearchType.DATA.toString()
                                       + "' AND c.table_name !~ E'^"
                                       + INTERNAL_TABLE_PREFIX
                                       + "((?!"
                                       + InternalTable.VALUES.getTableName().replaceFirst("^"
                                                                                          + INTERNAL_TABLE_PREFIX, "")
                                       + ").)*$') OR (selected_tables ~* '"
                                       + SearchType.SCRIPT.toString()
                                       + "' AND c.table_name ~ E'^"
                                       + InternalTable.SCRIPT.getTableName()
                                       + ".*')) AND (array_length(columns, 1) IS NULL "
                                       + "OR c.column_name = ANY(columns)) AND "
                                       + "c.table_schema = ANY(all_schema) AND "
                                       + "t.table_type = 'BASE TABLE' AND relname = "
                                       + "t.table_name AND nspname = t.table_schema "
                                       + "AND (d.objsubid = '0' OR d.objsubid IS "
                                       + "NULL) LOOP DECLARE the_row RECORD; BEGIN "
                                       + "FOR the_row IN EXECUTE 'SELECT * FROM ' || "
                                       + "quote_ident(schema_name) || '.' || "
                                       + "quote_ident(table_name) || ' WHERE (' || "
                                       + "quote_nullable(allow_regex) || ' = ''false'' "
                                       + "AND ((' || quote_nullable(no_case) || ' = "
                                       + "''true'' AND cast(' || quote_ident("
                                       + "column_name) || ' AS text) ~* ' || "
                                       + "quote_nullable(search_text) || ') OR (' || "
                                       + "quote_nullable(no_case) || ' = ''false'' AND "
                                       + "cast(' || quote_ident(column_name) || ' AS "
                                       + "text) ~ ' || quote_nullable(search_text) || "
                                       + "'))) OR (' || quote_nullable(allow_regex) || "
                                       + "' = ''true'' AND ((' || quote_nullable("
                                       + "no_case) || ' = ''true'' AND cast(' || "
                                       + "quote_ident(column_name) || ' AS text) ~* "
                                       + "E''' || search_text || ''') OR (' || "
                                       + "quote_nullable(no_case) || ' = ''false'' AND "
                                       + "cast(' || quote_ident(column_name) || ' AS "
                                       + "text) ~ E''' || search_text || ''')))' LOOP "
                                       + "SELECT * FROM regexp_replace(the_row::text, "
                                       + "E'^\\\\(|(\\\\)$)', '', 'g') INTO "
                                       + "column_value; RETURN NEXT; END LOOP; END; "
                                       + "END LOOP; END; $$ LANGUAGE plpgsql; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "search_tables(search_text "
                                                               + "text, no_case boolean, "
                                                               + "allow_regex boolean, "
                                                               + "selected_tables text, "
                                                               + "columns name[],"
                                                               + "all_schema name[])"),
                                       ccddMain.getMainFrame());

            // Create function to retrieve all table names and column values
            // for the tables with the specified column name currently in use
            // (i.e., blank column values are ignored) in the tables of the
            // specified table type(s)
            dbCommand.executeDbCommand(deleteFunction("find_prototype_columns_by_name")
                                       + "CREATE OR REPLACE FUNCTION find_prototype_columns_by_name("
                                       + "column_name_db text, table_types text[]) RETURNS "
                                       + "table(owner_name text, column_value text) AS $$ "
                                       + "BEGIN DECLARE row record; BEGIN DROP TABLE IF EXISTS "
                                       + TEMP_TABLES
                                       + "; CREATE TEMP TABLE "
                                       + TEMP_TABLES
                                       + " AS SELECT tbl_name FROM (SELECT split_part("
                                       + "obj_description, ',', 1) AS tbl_name, split_part("
                                       + "obj_description, ',', 2) AS tbl_type FROM (SELECT "
                                       + "obj_description(oid) FROM pg_class WHERE relkind = "
                                       + "'r' AND obj_description(oid) != '') AS tbl_desc) AS "
                                       + "tbl_name WHERE table_types @> ARRAY[tbl_type] ORDER "
                                       + "BY tbl_name ASC; FOR row IN SELECT tbl_name FROM "
                                       + TEMP_TABLES
                                       + " LOOP IF EXISTS (SELECT 1 FROM "
                                       + "information_schema.columns WHERE table_name = "
                                       + "lower(row.tbl_name) AND column_name = E'' || "
                                       + "column_name_db || E'') THEN RETURN QUERY EXECUTE "
                                       + "E'SELECT ''' || row.tbl_name || '''::text, ' || "
                                       + "column_name_db || E' FROM ' || row.tbl_name || "
                                       + "E' WHERE ' || column_name_db || E' != '''''; "
                                       + "END IF; END LOOP; END; END; $$ LANGUAGE plpgsql; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "find_prototype_columns_by_name(column_name_db "
                                                               + "text, table_types text[])"),
                                       ccddMain.getMainFrame());

            // Create function to retrieve all table names and column values
            // for the tables with the specified column name currently in use
            // (i.e., blank column values are ignored) in the tables of the
            // specified table type(s). Include columns from both the prototype
            // and custom values tables. Use SELECT DISTINCT on the results to
            // eliminate duplicate table names and/or column values
            dbCommand.executeDbCommand(deleteFunction("find_columns_by_name")
                                       + "CREATE OR REPLACE FUNCTION find_columns_by_name("
                                       + "column_name_user text, column_name_db text, "
                                       + "table_types text[]) RETURNS table(owner_name "
                                       + "text, column_value text) AS $$ BEGIN RETURN "
                                       + "QUERY EXECUTE E'SELECT owner_name, column_value "
                                       + "FROM (SELECT owner_name, column_value FROM "
                                       + "find_prototype_columns_by_name(''' || "
                                       + "column_name_db || E''', ''' || "
                                       + "table_types::text || E''') UNION ALL (SELECT "
                                       + ValuesColumn.TABLE_PATH.getColumnName()
                                       + ", "
                                       + ValuesColumn.VALUE.getColumnName()
                                       + " FROM "
                                       + InternalTable.VALUES.getTableName()
                                       + " WHERE column_name = ''' || column_name_user || "
                                       + "E''')) AS name_and_value ORDER BY owner_name;'; END; $$ "
                                       + "LANGUAGE plpgsql; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "find_columns_by_name(column_name_user "
                                                               + "text, column_name_db text, "
                                                               + "table_types text[])"),
                                       ccddMain.getMainFrame());

            // Create function to reset the rate for a link that no longer has
            // any member variables
            dbCommand.executeDbCommand(deleteFunction("reset_link_rate")
                                       + "CREATE FUNCTION reset_link_rate() RETURNS VOID AS "
                                       + "$$ BEGIN DECLARE row record; BEGIN DROP TABLE IF EXISTS "
                                       + TEMP_TABLES
                                       + "; CREATE TEMP TABLE "
                                       + TEMP_TABLES
                                       + " AS SELECT "
                                       + LinksColumn.LINK_NAME.getColumnName()
                                       + " AS link_defn FROM (SELECT "
                                       + LinksColumn.LINK_NAME.getColumnName()
                                       + ", regexp_replace("
                                       + LinksColumn.MEMBER.getColumnName()
                                       + ", E'^([0-9])*.*', E'\\\\1') AS rate FROM "
                                       + InternalTable.LINKS.getTableName()
                                       + ") AS result WHERE rate != '' AND "
                                       + "rate != '0'; FOR row IN SELECT * FROM "
                                       + TEMP_TABLES
                                       + " LOOP IF EXISTS (SELECT * FROM (SELECT COUNT(*) FROM "
                                       + InternalTable.LINKS.getTableName()
                                       + " WHERE "
                                       + LinksColumn.LINK_NAME.getColumnName()
                                       + " = row.link_defn ) AS alias1 WHERE "
                                       + "count = '1') THEN EXECUTE E'UPDATE "
                                       + InternalTable.LINKS.getTableName()
                                       + " SET "
                                       + LinksColumn.MEMBER.getColumnName()
                                       + " = regexp_replace("
                                       + LinksColumn.MEMBER.getColumnName()
                                       + ", E''^\\\\\\\\d+'', ''0'') WHERE "
                                       + LinksColumn.LINK_NAME.getColumnName()
                                       + " = ''' || row.link_defn || ''''; END IF; "
                                       + "END LOOP; END; END; $$ LANGUAGE plpgsql; "
                                       + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                           "reset_link_rate()"),
                                       ccddMain.getMainFrame());

            // Inform the user that the database table function creation
            // succeeded
            eventLog.logEvent(SUCCESS_MSG, "Database tables and functions created");
        }
        catch (SQLException se)
        {
            // Inform the user that creating the database functions failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot create tables and functions in project database '"
                                      + activeDatabase
                                      + "' as user '"
                                      + activeUser
                                      + "'; cause '"
                                      + se.getMessage()
                                      + "'",
                                  "<html><b>Cannot create tables and functions in project database '</b>"
                                      + activeDatabase
                                      + "<b>'");
            errorFlag = true;
        }

        return errorFlag;
    }

    /**************************************************************************
     * Create the reusable database functions for obtaining structure table
     * members and structure-defining column values
     * 
     * @return true if an error occurs creating the structure functions
     *************************************************************************/
    protected boolean createStructureColumnFunctions()
    {
        boolean errorFlag = false;

        // Check if connected to a project database
        if (isDatabaseConnected())
        {
            try
            {
                // Structure-defining column names, as used by the database
                String dbVariableName = null;
                String dbDataType = null;
                String dbArraySize = null;
                String dbBitLength = null;
                String dbRate = null;
                String dbEnumeration = null;

                String compareColumns = "";

                // Use the default structure column names for certain default
                // columns
                dbVariableName = DefaultColumn.VARIABLE_NAME.getDbName();
                dbDataType = DefaultColumn.DATA_TYPE.getDbName();
                dbArraySize = DefaultColumn.ARRAY_SIZE.getDbName();
                dbBitLength = DefaultColumn.BIT_LENGTH.getDbName();

                // Create a string containing the partial command for
                // determining if the columns that are necessary to define a
                // structure table are present in a table
                String defStructCols = "(column_name = '" + dbVariableName + "' OR "
                                       + "column_name = '" + dbDataType + "' OR "
                                       + "column_name = '" + dbArraySize + "' OR "
                                       + "column_name = '" + dbBitLength + "' OR ";

                List<String> rateAndEnums = new ArrayList<String>();

                // Step through each table type definition
                for (TypeDefinition typeDefn : ccddMain.getTableTypeHandler().getTypeDefinitions())
                {
                    // Check if the type represents a structure
                    if (typeDefn.isStructure())
                    {
                        // Get this type's first rate and enumeration column
                        // names
                        dbRate = typeDefn.getDbColumnNameByInputType(InputDataType.RATE);
                        dbEnumeration = typeDefn.getDbColumnNameByInputType(InputDataType.ENUMERATION);

                        // Create the portion of the command comparing the
                        // column name to the first rate and enumeration column
                        // names
                        String rateAndEnum = "column_name = '" + dbRate + "' OR "
                                             + "column_name = '" + dbEnumeration + "') OR ";

                        // Check if this pair of rate and enumeration columns
                        // names doesn't already exist in the comparison
                        if (!rateAndEnums.contains(rateAndEnum))
                        {
                            // Add the rate and enumeration name pair so that
                            // it won't be added again
                            rateAndEnums.add(rateAndEnum);

                            // Create a string containing the partial command
                            // for determining if the columns that are
                            // necessary to define a structure table are
                            // present in a table
                            compareColumns += defStructCols
                                              + "column_name = '" + dbRate + "' OR "
                                              + "column_name = '" + dbEnumeration + "') OR ";
                        }
                    }
                }

                // Check if no structure table type exists
                if (compareColumns.isEmpty())
                {
                    // Get the default rate and enumeration column names
                    dbRate = DefaultColumn.RATE.getDbName();
                    dbEnumeration = DefaultColumn.ENUMERATION.getDbName();

                    // Create a string containing the partial command for
                    // determining if the columns that are necessary to define
                    // a structure table are present in a table
                    compareColumns = defStructCols
                                     + "column_name = '" + dbRate + "' OR "
                                     + "column_name = '" + dbEnumeration + "')";
                }
                // At least one structure table type exists
                else
                {
                    compareColumns = CcddUtilities.removeTrailer(compareColumns, " OR ");
                }

                // Create functions for gathering the structure table member
                // information sorted alphabetically by name or numerically by
                // row index
                for (String[] functionParm : functionParameters)
                {
                    // Create function to get the table name, data type,
                    // variable name, bit length, sample rate, and enumeration
                    // for all structure tables that contain at least one row,
                    // sorted by table name or index, and then by variable
                    // name. For arrays, only the members are retrieved; the
                    // array definitions are ignored
                    dbCommand.executeDbCommand(deleteFunction("get_table_members_by_"
                                                              + functionParm[0])
                                               + "CREATE FUNCTION get_table_members_by_"
                                               + functionParm[0]
                                               + "() RETURNS TABLE(tbl_name text, data_type "
                                               + "text, variable_name text, bit_length text, "
                                               + "rate text, enumeration text) AS $$ BEGIN "
                                               + "DECLARE row record; BEGIN DROP TABLE IF EXISTS "
                                               + TEMP_TABLES
                                               + "; CREATE TEMP TABLE "
                                               + TEMP_TABLES
                                               + " AS SELECT t.tablename AS real_name FROM "
                                               + "pg_tables AS t WHERE t.schemaname = 'public' "
                                               + "AND substr(t.tablename, 1, 2) != '"
                                               + INTERNAL_TABLE_PREFIX
                                               + "' ORDER BY real_name ASC; FOR row IN SELECT * FROM "
                                               + TEMP_TABLES
                                               + " LOOP IF EXISTS (SELECT * FROM "
                                               + "(SELECT COUNT(*) FROM information_schema.columns "
                                               + "WHERE table_name = row.real_name AND ("
                                               + compareColumns
                                               + ")) AS alias1 WHERE count = '"
                                               + DefaultColumn.getProtectedColumnCount(TYPE_STRUCTURE)
                                               + "') THEN RETURN QUERY EXECUTE E'SELECT ''' || "
                                               + "row.real_name || '''::text, * FROM get_def_columns_by_"
                                               + functionParm[0]
                                               + "(''' || row.real_name || ''')'; END IF; "
                                               + "END LOOP; END; END; $$ LANGUAGE plpgsql; "
                                               + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                                   "get_table_members_by_"
                                                                       + functionParm[0]
                                                                       + "()"),
                                               ccddMain.getMainFrame());
                }

                String rateCol = "";
                String rateJoin = "";

                // Check if any rate columns are defined
                if (ccddMain.getRateParameterHandler().getNumRateColumns() != 0)
                {
                    // Step through each data stream
                    for (RateInformation rateInfo : ccddMain.getRateParameterHandler().getRateInformation())
                    {
                        // Get the rate column name (in its database form)
                        String rateColName = DefaultColumn.convertVisibleToDatabase(rateInfo.getRateName(),
                                                                                    InputDataType.RATE);

                        // Add detection for the rate column. If the column
                        // doesn't exist in the table then a blank is returned
                        // for that column's rate value
                        rateCol += "CASE WHEN "
                                   + rateColName
                                   + "_exists THEN "
                                   + rateColName
                                   + "::text ELSE ''''::text END || '','' || ";
                        rateJoin += " CROSS JOIN (SELECT EXISTS (SELECT 1 FROM "
                                    + "pg_catalog.pg_attribute WHERE attrelid = ''' "
                                    + "|| name || '''::regclass AND attname  = ''"
                                    + rateColName
                                    + "'' "
                                    + "AND NOT attisdropped AND attnum > 0) AS "
                                    + rateColName
                                    + "_exists) "
                                    + rateColName;
                    }

                    // Remove the trailing separator text
                    rateCol = CcddUtilities.removeTrailer(rateCol, " || '','' || ");
                }
                // No rate columns exist
                else
                {
                    // Return a blank for the rate column value
                    rateCol = "''''::text";
                }

                String enumCol = "";
                String enumJoin = "";

                // Get the unique structure enumeration column names
                List<String> enumColumns = ccddMain.getTableTypeHandler().getStructEnumColNames(true);

                // Check if any enumeration columns are defined
                if (enumColumns.size() != 0)
                {
                    // Build the enumeration separator portion of the command.
                    // Account for any backslashes in the separator by doubling
                    // them
                    String enumSep = " || E''' || E'"
                                     + ENUMERATION_SEPARATOR.replaceAll("\\\\",
                                                                        "\\\\\\\\\\\\\\\\")
                                     + "' || ''' || ";

                    // Step through each enumeration column name (in its
                    // database form)
                    for (String enumColName : enumColumns)
                    {
                        // Add detection for the enumeration column. If the
                        // column doesn't exist in the table then a blank is
                        // returned for that column's enumeration value
                        enumCol += "CASE WHEN "
                                   + enumColName
                                   + "_exists THEN "
                                   + enumColName
                                   + "::text ELSE ''''::text END"
                                   + enumSep;
                        enumJoin += " CROSS JOIN (SELECT EXISTS (SELECT 1 FROM "
                                    + "pg_catalog.pg_attribute WHERE attrelid = ''' "
                                    + "|| name || '''::regclass AND attname  = ''"
                                    + enumColName
                                    + "'' "
                                    + "AND NOT attisdropped AND attnum > 0) AS "
                                    + enumColName
                                    + "_exists) "
                                    + enumColName;
                    }

                    // Remove the trailing separator text
                    enumCol = CcddUtilities.removeTrailer(enumCol, enumSep);
                }
                // No enumeration columns exist
                else
                {
                    // Return a blank for the enumeration column value
                    enumCol += "''''::text";
                }

                // Create functions for gathering the structure table member
                // information sorted alphabetically by name or numerically by
                // row index
                for (String[] functionParm : functionParameters)
                {
                    // Create function to get the data type and variable name
                    // column data for the specified table, sorted by variable
                    // name. For arrays, only the members are retrieved; the
                    // array definitions are ignored
                    dbCommand.executeDbCommand(deleteFunction("get_def_columns_by_"
                                                              + functionParm[0])
                                               + "CREATE FUNCTION get_def_columns_by_"
                                               + functionParm[0]
                                               + "(name text) RETURNS TABLE(data_type "
                                               + "text, variable_name text, bit_length text, "
                                               + "rate text, enumeration text) AS $$ "
                                               + "BEGIN RETURN QUERY EXECUTE 'SELECT "
                                               + dbDataType
                                               + ", "
                                               + dbVariableName
                                               + ", "
                                               + dbBitLength
                                               + ", "
                                               + rateCol
                                               + ", "
                                               + enumCol
                                               + " FROM ' || name || '"
                                               + rateJoin
                                               + enumJoin
                                               + " WHERE "
                                               + dbArraySize
                                               + " = E'''' OR (array_size ~ E''^"
                                               + MACRO_IDENTIFIER
                                               + "'' AND (SELECT EXISTS (SELECT "
                                               + MacrosColumn.VALUE.getColumnName()
                                               + " FROM "
                                               + InternalTable.MACROS.getTableName()
                                               + " WHERE "
                                               + MacrosColumn.MACRO_NAME.getColumnName()
                                               + " = replace('''' || array_size || '''', ''"
                                               + MACRO_IDENTIFIER
                                               + "'', '''') AND "
                                               + MacrosColumn.VALUE.getColumnName()
                                               + " = ''''))) OR "
                                               + dbVariableName
                                               + " ~ E''^.+]'' ORDER BY "
                                               + functionParm[1]
                                               + " ASC'; END $$ LANGUAGE plpgsql; "
                                               + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                                   "get_def_columns_by_"
                                                                       + functionParm[0]
                                                                       + "(name text)"),
                                               ccddMain.getMainFrame());
                }

                // Database function to search for all tables containing a data
                // type column, and replace a target value with a new value
                dbCommand.executeDbCommand(deleteFunction("update_data_type_names")
                                           + "CREATE FUNCTION update_data_type_names(oldType text, "
                                           + "newType text) RETURNS VOID AS $$ BEGIN DECLARE row "
                                           + "record; BEGIN DROP TABLE IF EXISTS "
                                           + TEMP_TABLES
                                           + "; CREATE TEMP TABLE "
                                           + TEMP_TABLES
                                           + " AS SELECT t.tablename AS real_name "
                                           + "FROM pg_tables AS t WHERE t.schemaname = 'public' "
                                           + "AND substr(t.tablename, 1, "
                                           + INTERNAL_TABLE_PREFIX.length()
                                           + ") != '"
                                           + INTERNAL_TABLE_PREFIX
                                           + "'; FOR row IN SELECT * FROM "
                                           + TEMP_TABLES
                                           + " LOOP IF EXISTS (SELECT 1 FROM "
                                           + "information_schema.columns WHERE table_name = "
                                           + "row.real_name AND column_name = '"
                                           + dbDataType
                                           + "') THEN EXECUTE E'UPDATE ' || row.real_name || E' SET "
                                           + dbDataType
                                           + " = ''' || newType || E''' WHERE "
                                           + dbDataType
                                           + " = ''' || oldType || E''''; END IF; "
                                           + "END LOOP; END; END; $$ LANGUAGE plpgsql; "
                                           + buildOwnerCommand(DatabaseObject.FUNCTION,
                                                               "update_data_type_names(oldType text,"
                                                                   + " newType text)"),
                                           ccddMain.getMainFrame());

                // Inform the user that the database function creation
                // succeeded
                eventLog.logEvent(SUCCESS_MSG, "Database structure functions created");
            }
            catch (SQLException se)
            {
                // Inform the user that creating the database functions failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot create structure functions in project database '"
                                          + activeDatabase
                                          + "' as user '"
                                          + activeUser
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot create structure functions in project database '</b>"
                                          + activeDatabase
                                          + "<b>'");
                errorFlag = true;
            }
        }

        return errorFlag;
    }

    /**************************************************************************
     * Build the command to delete a database function. This deletes the
     * function whether or not the input parameters match
     * 
     * @param functionName
     *            name of the function to delete
     * 
     * @return Command to delete the specified database function
     *************************************************************************/
    private String deleteFunction(String functionName)
    {
        return "SELECT CASE WHEN EXISTS(SELECT * FROM pg_proc WHERE proname = '"
               + functionName
               + "' AND pg_function_is_visible(oid)) THEN delete_function('"
               + functionName
               + "') END; ";
    }

    /**************************************************************************
     * Build the command to create an internal table
     * 
     * @param intTable
     *            type of internal table to build
     * 
     * @return Command to build the specified internal table
     *************************************************************************/
    protected String buildInformationTableCommand(InternalTable intTable)
    {
        return "CREATE TABLE "
               + intTable.getTableName()
               + " "
               + intTable.getColumnCommand(true)
               + buildOwnerCommand(DatabaseObject.TABLE,
                                   intTable.getTableName());
    }

    /**************************************************************************
     * Authenticate the specified user credentials for the PostgreSQL server
     * and the currently open database
     * 
     * @param userName
     *            user name
     * 
     * @param password
     *            user password
     * 
     * @return true if the user is allowed access to the currently open
     *         database
     *************************************************************************/
    protected boolean authenticateUser(String userName, String password)
    {
        boolean isAllowed = false;

        try
        {
            // Check if the user credentials are valid for the PostgreSQL
            // server
            Connection validateConn = DriverManager.getConnection(getDatabaseURL(activeDatabase),
                                                                  userName,
                                                                  password);

            // Close the connection
            validateConn.close();

            // Check if the default database isn't selected
            if (!activeDatabase.equals(DEFAULT_DATABASE))
            {
                // Step through each database for which the user has access
                for (String database : queryDatabaseByUserList(ccddMain.getMainFrame(),
                                                               userName))
                {
                    // Check if the database name is in the list, which
                    // indicates that the user has access to this database
                    if (activeDatabase.equalsIgnoreCase(database.split(",", 2)[0]))
                    {
                        // Set the flag indicating the user has access to the
                        // currently open database and stop searching
                        isAllowed = true;
                        break;
                    }
                }
            }
        }
        catch (SQLException se)
        {
        }

        return isAllowed;
    }

    /**************************************************************************
     * Connect to a database
     * 
     * @param databaseName
     *            name of the database to open
     * 
     * @return true if the connection attempt failed
     *************************************************************************/
    private boolean connectToDatabase(String databaseName)
    {
        boolean errorFlag = false;

        try
        {
            connectionStatus = NO_CONNECTION;

            // Connect the user to the database
            connection = DriverManager.getConnection(getDatabaseURL(databaseName),
                                                     activeUser,
                                                     activePassword);
            dbCommand.setStatement(connection.createStatement());

            // Reset the flag that indicates a connection failure occurred due
            // to a missing password
            isMissingPassword = false;

            // Set the transaction isolation mode to serializable to prevent
            // transaction collisions if there are concurrent users of the
            // database
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            // Disable automatic commit of database updates. This allows
            // database commands to be grouped prior to committing
            connection.setAutoCommit(false);

            // Store the database connection
            dbCommand.setConnection(connection);

            // Save the name of the newly connected database
            activeDatabase = databaseName;

            // Check if the default database is selected
            if (databaseName.equals(DEFAULT_DATABASE))
            {
                // Set the connection status to indicate the default database
                // is connected
                connectionStatus = TO_SERVER_ONLY;

                // Inform the user that the server connection succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  "Connected to server as user '"
                                      + activeUser
                                      + "'");
            }
            // A database other than the default is selected
            else
            {
                // The connection to the server must exist in order to reach
                // this point, so set the connection status to indicate the
                // server is connected. Once the project connection is
                // completed the flag is updated accordingly
                connectionStatus = TO_SERVER_ONLY;

                // Check if the GUI is visible. If the application is started
                // with the GUI hidden (for command line script execution or as
                // a web server) then the project database lock status is
                // ignored
                if (!ccddMain.isGUIHidden())
                {
                    // Get the database lock status
                    Boolean isLocked = getDatabaseLockStatus(databaseName);

                    // Check if an error occurred obtaining the lock status
                    if (isLocked == null)
                    {
                        // Set the error flag
                        throw new CCDDException("");
                    }

                    // Check if the database is locked
                    if (isLocked)
                    {
                        throw new SQLException("database is locked");
                    }
                }

                boolean isAllowed = false;

                // Step through each database
                for (String database : queryDatabaseByUserList(ccddMain.getMainFrame(),
                                                               activeUser))
                {
                    // Check if the database name is in the list, which
                    // indicates that the user has access to this database
                    if (databaseName.equalsIgnoreCase(database.split(DATABASE_COMMENT_SEPARATOR, 2)[0]))
                    {
                        // Set the flag indicating the user has access and stop
                        // searching
                        isAllowed = true;
                        break;
                    }
                }

                // Check if the user has access to this database
                if (!isAllowed)
                {
                    // Set the error flag
                    throw new CCDDException("");
                }

                // Get the database owner
                activeOwner = queryDatabaseOwner(databaseName,
                                                 ccddMain.getMainFrame())[0];

                // Set the connection status to indicate a database is
                // connected
                connectionStatus = TO_DATABASE;

                // Check if an automatic backup was scheduled via the command
                // line argument
                if (!backupFileName.isEmpty())
                {
                    // Check if the backup file name is missing the correct
                    // extension
                    if (!backupFileName.endsWith(FileExtension.DBU.getExtension()))
                    {
                        // Append the backup file extension to the file name
                        backupFileName += FileExtension.DBU.getExtension();
                    }

                    // Backup the database
                    backupDatabaseInBackground(databaseName, new File(backupFileName));

                    // Reset the backup file name to prevent another automatic
                    // backup
                    backupFileName = "";
                }

                // Inform the user that the database connection succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  "Connected to project database '"
                                      + databaseName
                                      + "' as user '"
                                      + activeUser
                                      + "'");
            }
        }
        catch (SQLException se)
        {
            // Check if the connection failed due to a missing password
            if (activePassword.isEmpty()
                && se.getMessage().contains("password authentication failed")
                && !ccddMain.isGUIHidden())
            {
                // Set the flag that indicates a connection failure occurred
                // due to a missing password
                isMissingPassword = true;
            }
            // Connection failed for reason other than a missing password
            else
            {
                // Inform the user that the database connection failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot connect to "
                                          + (activeDatabase.equals(DEFAULT_DATABASE)
                                                                                    ? "server"
                                                                                    : "project database '"
                                                                                      + getServerAndDatabase(databaseName)
                                                                                      + "'")
                                          + " as user '"
                                          + activeUser
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot connect to "
                                          + (activeDatabase.equals(DEFAULT_DATABASE)
                                                                                    ? "server"
                                                                                    : "project database '</b>"
                                                                                      + getServerAndDatabase(databaseName)
                                                                                      + "<b>'"));
            }

            errorFlag = true;
        }
        catch (CCDDException ce)
        {
            errorFlag = true;
        }

        return errorFlag;
    }

    /**************************************************************************
     * Open a database. Create the database functions
     * 
     * @param databaseName
     *            name of the database to open
     * 
     * @return true if an error occurred opening the database; false if the
     *         database successfully opened
     *************************************************************************/
    protected boolean openDatabase(final String databaseName)
    {
        return openDatabase(databaseName, true);
    }

    /**************************************************************************
     * Open a database
     * 
     * @param databaseName
     *            name of the database to open
     * 
     * @param createFunctions
     *            true to create the database functions; false if reopening a
     *            database (so the functions already exist)
     * 
     * @return true if an error occurred opening the database; false if the
     *         database successfully opened
     *************************************************************************/
    protected boolean openDatabase(final String databaseName,
                                   boolean createFunctions)
    {
        boolean errorFlag = false;

        // Check if closing the existing connection, if present, was successful
        if (closeDatabase())
        {
            // Check if the required server inputs are available. A password
            // may be needed; if so, then it is requested after the connection
            // attempt fails
            if (serverHost != null
                && activeUser != null
                && !serverHost.isEmpty()
                && !activeUser.isEmpty())
            {
                try
                {
                    // Register the JDBC driver
                    Class.forName(DATABASE_DRIVER);

                    // Check if the attempt to connect to the database fails
                    if (connectToDatabase(databaseName))
                    {
                        throw new CCDDException();
                    }

                    // Check that the connection is to a project database and
                    // not just the server (default database)
                    if (isDatabaseConnected())
                    {
                        // Check if the database functions should be created;
                        // if so create the internal tables and database
                        // functions, and check if an error occurs creating
                        // them
                        if (createFunctions && createTablesAndFunctions())
                        {
                            throw new CCDDException();
                        }

                        // Read the table types, macros, and rate parameters
                        // from the database
                        ccddMain.setDbSpecificHandlers();

                        // Check if the database functions should be created;
                        // if so create the database functions that collect
                        // structure table members and structure-defining
                        // column data, and check if an error occurred creating
                        // them
                        if (createFunctions && createStructureColumnFunctions())
                        {
                            throw new CCDDException();
                        }

                        // Check if the web server is enabled
                        if (ccddMain.isWebServer())
                        {
                            // Start the web server
                            ccddMain.getWebServer().startServer();
                        }

                        // Perform any patches to update this project database
                        // to the latest schema
                        new CcddPatchHandler(ccddMain);

                        // Check if the GUI is visible. If the application is
                        // started with the GUI hidden (for command line script
                        // execution or as a web server) then the project
                        // database is left unlocked
                        if (!ccddMain.isGUIHidden())
                        {
                            // Lock the database
                            setDatabaseLockStatus(activeDatabase, true);
                        }

                        // Check if the reserved word list hasn't been
                        // retrieved
                        if (keyWords == null)
                        {
                            // Get the array of reserved words
                            keyWords = dbCommand.getList(DatabaseListCommand.KEYWORDS,
                                                         null,
                                                         ccddMain.getMainFrame());
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag indicating the connection attempt failed
                    errorFlag = true;

                    // Check that the database isn't the default database
                    // (server)
                    if (!databaseName.equals(DEFAULT_DATABASE))
                    {
                        // Attempt to connect to the default database
                        errorFlag = connectToDatabase(DEFAULT_DATABASE);
                    }
                }
                catch (LinkageError | ClassNotFoundException le)
                {
                    // Inform the user that registering the database driver
                    // failed
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot register database driver '"
                                              + DATABASE_DRIVER
                                              + "'; cause '"
                                              + le.getMessage()
                                              + "'",
                                          "<html><b>Cannot register database driver");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated
                    // error
                    CcddUtilities.displayException(e, ccddMain.getMainFrame());
                    errorFlag = true;
                }

                // Check that no error occurred connecting to the database
                if (!errorFlag)
                {
                    try
                    {
                        // Store the database name, user name, server host, and
                        // server port in the program preferences backing store
                        ccddMain.getProgPrefs().put(DATABASE, activeDatabase);
                        ccddMain.getProgPrefs().put(USER, activeUser);
                        ccddMain.getProgPrefs().put(POSTGRESQL_SERVER_HOST,
                                                    serverHost);
                        ccddMain.getProgPrefs().put(POSTGRESQL_SERVER_PORT,
                                                    serverPort);
                    }
                    catch (Exception e)
                    {
                        // Inform the user that there the program preferences
                        // can't be stored
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot store program preference values; cause '"
                                                                      + e.getMessage()
                                                                      + "'",
                                                                  "File Warning",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }
            }
            // A required parameter is missing
            else
            {
                // Inform the user that one or more server connection
                // parameters are missing
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Database connection parameter(s) missing",
                                                          "Connection Error",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
                errorFlag = true;
            }
        }

        return errorFlag;
    }

    /**************************************************************************
     * Open a database. This command is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     * 
     * @param databaseName
     *            name of the database to open
     *************************************************************************/
    protected void openDatabaseInBackground(final String databaseName)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            private boolean errorFlag = false;

            /******************************************************************
             * Database open command
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Open the new database
                errorFlag = openDatabase(databaseName);
            }

            /******************************************************************
             * Database open command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if an error occurred due to a missing password
                if (errorFlag && isMissingPassword)
                {
                    // Get the user and password
                    new CcddServerPropertyDialog(ccddMain,
                                                 ServerPropertyDialogType.LOGIN);
                }
            }
        });
    }

    /**************************************************************************
     * Rename a database and/or add/update the database description. This
     * command is executed in a separate thread since it can take a noticeable
     * amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are
     * disabled until the database command completes execution
     * 
     * @param oldName
     *            current name of the database
     * 
     * @param newName
     *            new name of the database
     * 
     * @param description
     *            database description
     *************************************************************************/
    protected void renameDatabaseInBackground(final String oldName,
                                              final String newName,
                                              final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Rename database command
             *****************************************************************/
            @Override
            protected void execute()
            {
                String currentDatabase = activeDatabase;

                try
                {
                    // Check if the old and new names are identical; this
                    // implies only the description changed
                    if (oldName.equals(newName))
                    {
                        // Rename the database to the new name
                        dbCommand.executeDbUpdate(buildDatabaseCommentCommand(oldName,
                                                                              false,
                                                                              description),
                                                  ccddMain.getMainFrame());
                    }
                    // Check if the currently open database is not the one
                    // being renamed; otherwise, check if the target database
                    // can be closed and the default opened (required in order
                    // to make changes to the current database)
                    else if (!oldName.equals(currentDatabase)
                             || !openDatabase(DEFAULT_DATABASE))
                    {
                        // Rename the database to the new name and update the
                        // description
                        dbCommand.executeDbUpdate("ALTER DATABASE "
                                                  + oldName.toLowerCase()
                                                  + " RENAME TO "
                                                  + newName.toLowerCase()
                                                  + "; "
                                                  + buildDatabaseCommentCommand(newName,
                                                                                false,
                                                                                description),
                                                  ccddMain.getMainFrame());

                        // Check if the currently open database is the one
                        // being renamed
                        if (oldName.equals(currentDatabase))
                        {
                            // Close the default database and reopen the target
                            openDatabase(newName, false);
                        }

                        // Log that the renaming the database succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          "Project database '"
                                              + oldName
                                              + "' renamed to '"
                                              + newName
                                              + "'");
                    }
                }
                catch (SQLException se)
                {
                    // Inform the user that the database cannot be renamed
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot rename project database '"
                                              + getServerAndDatabase(oldName)
                                              + "'; cause '"
                                              + se.getMessage()
                                              + "'",
                                          "<html><b>Cannot rename project database '</b>"
                                              + oldName
                                              + "<b>'");

                    // Check if the currently open database is the one that was
                    // attempted to be renamed
                    if (oldName.equals(currentDatabase))
                    {
                        // Close the default database and reopen the target
                        openDatabase(currentDatabase, false);
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Copy a database. This command is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     * 
     * @param targetName
     *            name of the database to copy
     * 
     * @param copyName
     *            name of the database copy
     * 
     * @param description
     *            database description
     *************************************************************************/
    protected void copyDatabaseInBackground(final String targetName,
                                            final String copyName,
                                            final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Copy database command
             *****************************************************************/
            @Override
            protected void execute()
            {
                String currentDatabase = activeDatabase;

                try
                {
                    // Check if the currently open database is not the one
                    // being copied; otherwise, check if the target database
                    // can be closed and the default opened (required in order
                    // to make changes to the current database)
                    if (!targetName.equals(currentDatabase)
                        || !openDatabase(DEFAULT_DATABASE))
                    {
                        // Get the owner of the database being copied; the copy
                        // will have the same owner
                        String ownerName = targetName.equals(currentDatabase)
                                                                             ? activeOwner
                                                                             : queryDatabaseOwner(targetName,
                                                                                                  ccddMain.getMainFrame())[0];

                        // Enable auto-commit for database changes
                        connection.setAutoCommit(true);

                        // Copy the database and transfer the comment
                        dbCommand.executeDbCommand("CREATE DATABASE "
                                                   + copyName.toLowerCase()
                                                   + " WITH TEMPLATE "
                                                   + targetName.toLowerCase()
                                                   + "; "
                                                   + buildDatabaseCommentCommand(copyName,
                                                                                 false,
                                                                                 description)
                                                   + buildOwnerCommand(ownerName,
                                                                       DatabaseObject.DATABASE,
                                                                       copyName),
                                                   ccddMain.getMainFrame());

                        // Check if the currently open database is the one
                        // being copied
                        if (targetName.equals(currentDatabase))
                        {
                            // Close the default database and reopen the
                            // database that was originally open
                            openDatabase(targetName, false);
                        }

                        // Log that the copying the database succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          "Project database '"
                                              + targetName
                                              + "' copied");
                    }
                }
                catch (SQLException se)
                {
                    // Inform the user that the database cannot be copied
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot copy project database '"
                                              + getServerAndDatabase(targetName)
                                              + "'; cause '"
                                              + se.getMessage()
                                              + "'",
                                          "<html><b>Cannot copy project database '</b>"
                                              + targetName
                                              + "<b>'");

                    // Check if the currently open database is the one that was
                    // attempted to be copied
                    if (targetName.equals(currentDatabase))
                    {
                        // Close the default database and reopen the target
                        openDatabase(currentDatabase, false);
                    }
                }
                finally
                {
                    // Disable auto-commit for database changes
                    resetAutoCommit();
                }
            }
        });
    }

    /**************************************************************************
     * Delete the database
     * 
     * @param databaseName
     *            name of the database to delete
     *************************************************************************/
    private void deleteDatabase(final String databaseName)
    {
        try
        {
            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Delete the database
            dbCommand.executeDbUpdate("DROP DATABASE "
                                      + databaseName.toLowerCase()
                                      + ";",
                                      ccddMain.getMainFrame());

            // Log that the database deletion succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Project database '"
                                  + databaseName
                                  + "' deleted");
        }
        catch (SQLException se)
        {
            // Inform the user that the database deletion failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot delete project database '"
                                      + getServerAndDatabase(databaseName)
                                      + "'; cause '"
                                      + se.getMessage()
                                      + "'",
                                  "<html><b>Cannot delete project database '</b>"
                                      + databaseName
                                      + "<b>'");
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }
    }

    /**************************************************************************
     * Delete the database. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     * 
     * @param databaseName
     *            name of the database to delete
     *************************************************************************/
    protected void deleteDatabaseInBackground(final String databaseName)
    {
        // Have the user confirm deleting the selected database
        if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Delete database </b>"
                                                          + databaseName
                                                          + "<b>?<br><i>Warning: This action cannot be undone!",
                                                      "Delete Database",
                                                      JOptionPane.QUESTION_MESSAGE,
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Execute the command in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                /**************************************************************
                 * Database delete command
                 *************************************************************/
                @Override
                protected void execute()
                {
                    // Delete the database
                    deleteDatabase(databaseName);
                }
            });
        }
    }

    /**************************************************************************
     * Close the currently open database
     * 
     * @return true if no database is connected
     *************************************************************************/
    protected boolean closeDatabase()
    {
        // Check if a database (including the default) is open
        if (isServerConnected())
        {
            try
            {
                // Check that the database isn't the default database (server)
                if (isDatabaseConnected())
                {
                    // Unlock the database
                    setDatabaseLockStatus(activeDatabase, false);

                    // Check if the web server is enabled
                    if (ccddMain.isWebServer())
                    {
                        // Stop the web server
                        ccddMain.getWebServer().stopServer();
                    }
                }

                // Close the database
                connection.close();

                // Inform the user that closing the database succeeded and
                // update the connection status
                eventLog.logEvent(SUCCESS_MSG,
                                  (activeDatabase.equals(DEFAULT_DATABASE)
                                                                          ? "Server connection"
                                                                          : "Project database '"
                                                                            + activeDatabase
                                                                            + "'")
                                      + " closed");
                connectionStatus = NO_CONNECTION;
            }
            catch (SQLException se)
            {
                // Inform the user that the database failed to close
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot close "
                                          + (activeDatabase.equals(DEFAULT_DATABASE)
                                                                                    ? "server connection"
                                                                                    : "project database '"
                                                                                      + getServerAndDatabase(activeDatabase)
                                                                                      + "'")
                                          + "; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot close "
                                          + (activeDatabase.equals(DEFAULT_DATABASE)
                                                                                    ? "server connection"
                                                                                    : "project database '</b>"
                                                                                      + activeDatabase
                                                                                      + "<b>'"));
            }
        }

        return connectionStatus == NO_CONNECTION;
    }

    /**************************************************************************
     * Build the portion of the backup and restore commands that specifies the
     * user name, server host, and server port
     * 
     * @return Portion of the backup and restore commands that specifies the
     *         user name, server host, and server port
     *************************************************************************/
    private String getUserHostAndPort()
    {
        // Create the user name portion of the command
        String userHostPort = "-U " + activeUser + " ";

        // Check if a server host other than the default is specified
        if (!serverHost.equals(DEFAULT_POSTGRESQL_HOST))
        {
            // Create the server host portion of the command
            userHostPort += "-h " + serverHost + " ";
        }

        // Check if a server port other than the default is specified
        if (!serverPort.equals(DEFAULT_POSTGRESQL_PORT))
        {
            // Create the server port portion of the command
            userHostPort += "-p " + serverPort + " ";
        }

        return userHostPort;
    }

    /**************************************************************************
     * Backup a database. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     * 
     * @param databaseName
     *            name of the database to backup
     * 
     * @param backupFile
     *            file to which to backup the database
     *************************************************************************/
    protected void backupDatabaseInBackground(final String databaseName,
                                              final File backupFile)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Backup database command
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Perform the backup operation
                backupDatabase(databaseName, backupFile);
            }
        });
    }

    /**************************************************************************
     * Backup a database
     * 
     * @param databaseName
     *            name of the database to backup
     * 
     * @param backupFile
     *            file to which to backup the database
     *************************************************************************/
    protected void backupDatabase(final String databaseName,
                                  final File backupFile)
    {
        String errorType = "";

        // Build the command to backup the database
        String command = "pg_dump "
                         + getUserHostAndPort()
                         + databaseName.toLowerCase()
                         + " -o -f ";

        // Get the number of command line arguments
        int numArgs = command.split(" ").length + 1;

        // Append the file name. Since it may have spaces the argument count
        // must be made without it and the name is placed within quotes
        command += backupFile.getAbsolutePath();

        // Log the database backup command
        eventLog.logEvent(COMMAND_MSG, command);

        // Execute the backup command
        errorType = executeProcess(command, numArgs);

        // Check if no error occurred
        if (errorType.isEmpty())
        {
            // Store the backup file path in the program preferences backing
            // store
            ccddMain.getFileIOHandler().storePath(backupFile.getAbsolutePath(),
                                                  true,
                                                  DATABASE_BACKUP_PATH);

            // Log that backing up the database succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "project database '"
                                  + databaseName
                                  + "' backed up");
        }

        // An error occurred backing up the database
        else
        {
            // Inform the user that the database could not be backed up
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Project database '"
                                      + databaseName
                                      + "' backup failed;  cause '"
                                      + errorType
                                      + "'",
                                  "<html><b>Project database '</b>"
                                      + databaseName
                                      + "<b>' backup failed");
        }
    }

    /**************************************************************************
     * Restore a database. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     * 
     * @param databaseName
     *            name of the database to restore
     * 
     * @param ownerName
     *            name of the role or user that owns the database and its
     *            objects
     * 
     * @param restoreFile
     *            file to restore the database from
     *************************************************************************/
    protected void restoreDatabase(final String databaseName,
                                   final String ownerName,
                                   final File restoreFile)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            String errorType = "";

            /******************************************************************
             * Restore database command
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Create the name for the restored database
                String restoreName = databaseName.toLowerCase() + "_restored";

                // Get the list of available databases
                String[] databases = queryDatabaseList(ccddMain.getMainFrame());

                boolean isMatch = true;
                int seqNum = 0;
                String seqName = "";

                // Continue to check for name matches until the restored
                // database name is unique
                while (isMatch)
                {
                    isMatch = false;

                    // Step through each existing database name
                    for (String name : databases)
                    {
                        // Check if the name of the restored database name
                        // matches that of another database
                        if ((restoreName + seqName).equals(name.split(",", 2)[0]))
                        {
                            // Increment the sequence number and set the flag
                            // to indicate a match was found. Repeat the
                            // process in case this amended name is also a
                            // match
                            seqNum++;
                            seqName = "_" + seqNum;
                            isMatch = true;
                            break;
                        }
                    }
                }

                // Check if a sequence number is needed to differentiate the
                // database name
                if (!seqName.isEmpty())
                {
                    // Add the sequence number to the name
                    restoreName += seqName;
                }

                // Create a new database to which to restore the data
                if (createDatabase(restoreName, ownerName, ""))
                {
                    // Build the command to restore the database
                    String command = "psql "
                                     + getUserHostAndPort()
                                     + "-d "
                                     + restoreName
                                     + " -v ON_ERROR_STOP=true -f ";

                    // Get the number of command line arguments
                    int numArgs = command.split(" ").length + 1;

                    // Append the file name. Since it may have spaces the
                    // argument count must be made without it and the name is
                    // placed within quotes
                    command += restoreFile.getAbsolutePath();

                    // Log the database restore command
                    eventLog.logEvent(COMMAND_MSG, command);

                    // Execute the restore command
                    errorType = executeProcess(command, numArgs);

                    // Check if no error occurred
                    if (errorType.isEmpty())
                    {
                        // Log that the database restoration succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          "Project database '"
                                              + databaseName
                                              + "' restored as '"
                                              + restoreName
                                              + "'");
                    }
                }
                // Database restoration failed
                else
                {
                    // Set the error type message
                    errorType = "cannot create restore database";
                }
            }

            /******************************************************************
             * Restore database command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if an error occurred restoring the database
                if (!errorType.isEmpty())
                {
                    // Inform the user that the database could not be restored
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Project database '"
                                              + databaseName
                                              + "' restore failed; cause '"
                                              + errorType
                                              + "'",
                                          "<html><b>Project database '</b>"
                                              + databaseName
                                              + "<b>' restore failed");
                }
            }
        });
    }

    /**************************************************************************
     * Execute an operating system command
     * 
     * @param command
     *            command to process
     * 
     * @param numArgs
     *            number of arguments in the command
     * 
     * @return Error message text if an error occurs; empty string is the
     *         command completed successfully
     *************************************************************************/
    private String executeProcess(String command, int numArgs)
    {
        String errorType = "";

        try
        {
            // Create a list to contain the arguments for the command and add
            // the arguments to the list
            List<String> cmd = new ArrayList<String>();
            Collections.addAll(cmd, command.split(" ", numArgs));

            // Create the process builder to execute the command
            ProcessBuilder builder = new ProcessBuilder(cmd);

            // Get the environment variables, then add another for the user's
            // password
            Map<String, String> environ = builder.environment();
            environ.put("PGPASSWORD", activePassword);

            // Execute the restore command
            Process process = builder.start();

            // Create a reader for stdout
            BufferedReader inReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Read the output from stdout until no more exists. This prevents
            // filling up the output buffer, which would cause the process to
            // block indefinitely
            while (inReader.readLine() != null);

            // Wait for the command to complete and check if it failed to
            // successfully complete
            if (process.waitFor() != 0)
            {
                // Create a reader for the error stream to get the cause of the
                // error
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;

                // Step through each line in the error stream
                while ((line = br.readLine()) != null)
                {
                    // Check if this isn't the first line of the error stream
                    if (!errorType.isEmpty())
                    {
                        // Append a semicolon to differentiate the lines
                        errorType += "; ";
                    }

                    // Append the error text
                    errorType += line;
                }
            }
        }
        catch (Exception e)
        {
            // Get the cause of the error for display in the event log
            errorType = e.getMessage();
        }

        return errorType;
    }
}