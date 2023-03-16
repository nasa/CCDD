/**************************************************************************************************
 * /** \file CcddDbControlHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class containing the methods for connecting to, creating, copying, renaming, and deleting
 * project databases.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.DATABASE;
import static CCDD.CcddConstants.DATABASE_ADMIN_SEPARATOR;
import static CCDD.CcddConstants.DATABASE_COMMENT_SEPARATOR;
import static CCDD.CcddConstants.DATABASE_DRIVER;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_HOST;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_PORT;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.MACRO_IDENTIFIER;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_HOST;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_PORT;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_SSL;
import static CCDD.CcddConstants.PROJECT_STRINGS;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.USER;
import static CCDD.CcddConstants.ConnectionType.NO_CONNECTION;
import static CCDD.CcddConstants.ConnectionType.TO_DATABASE;
import static CCDD.CcddConstants.ConnectionType.TO_SERVER_ONLY;
import static CCDD.CcddConstants.EventLogMessageType.COMMAND_MSG;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;
import static CCDD.CcddConstants.DB_PLACEHOLDER_NAME;
import static CCDD.CcddConstants.DB_PLACEHOLDER_LOCK;
import static CCDD.CcddConstants.DB_PLACEHOLDER_ADMINS;
import static CCDD.CcddConstants.DB_PLACEHOLDER_DESC;
import static CCDD.CcddConstants.USER_PLACEHOLDER_ADMIN_USER;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.tuple.ImmutablePair;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.ConnectionType;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DatabaseObject;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.UsersColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddConstants.ServerPropertyDialogType;

/**************************************************************************************************
 * CFS Command and Data Dictionary database control handler class
 *************************************************************************************************/
public class CcddDbControlHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private CcddEventLogDialog eventLog;

    // SQL server type and host
    private String serverPort;
    private String serverHost;

    // Currently open project name, database name, owner, user, and user password
    private String activeProject;
    private String activeDatabase;
    private String activeOwner;
    private String activeUser;
    private String activePassword;
    private AccessLevel accessLevel;

    // Flag indicating if the database connection attempt failed due to a missing password
    private boolean isAuthenticationFail;

    // Array of reserved words
    private String[] keyWords;

    // Current connection status (none, connected to the server (default database), or connected to
    // a database
    private ConnectionType connectionStatus;

    // SQL database connection
    private Connection connection;

    // Flag that indicates is an SSL connection is enabled
    private boolean isSSL;

    // File path and name to automatically backup the database to on first connection
    private String backupFileName;

    // PostgreSQL function parameters
    private final String[][] functionParameters;

    // Flag that indicates this is the first connection pass
    private boolean isFirstConnectionAttempt;

    // Temporary data storage table
    private static final String TEMP_TABLE_NAME = INTERNAL_TABLE_PREFIX + "temp_table";

    private boolean addDefaultDataTypes;

    /**********************************************************************************************
     * Input stream consumer class
     *********************************************************************************************/
    private class StreamConsumer extends Thread
    {
        private final InputStream inputStream;
        private final boolean storeOutput;
        private StringBuilder output;

        /******************************************************************************************
         * Input stream consumer class constructor
         *
         * @param inputStream Reference to the input stream to consume
         *
         * @param storeOutput True to store the contents of the stream; false to discard
         *****************************************************************************************/
        StreamConsumer(InputStream inputStream, boolean storeOutput)
        {
            this.inputStream = inputStream;
            this.storeOutput = storeOutput;
            output = null;
        }

        /******************************************************************************************
         * Get the streams' content
         *
         * @return The contents of the stream; empty string if the content was not configured to be
         *         stored
         *****************************************************************************************/
        protected String getOutput()
        {
            return output != null ? output.toString() : "";
        }

        /******************************************************************************************
         * Read the contents of the specified stream until it is empty
         *****************************************************************************************/
        @Override
        public void run()
        {
            try
            {
                String line;

                // Create a buffered reader for the input stream
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

                // Read each line in the stream
                while ((line = br.readLine()) != null)
                {
                    // Check if the flag is set to store the stream's contents
                    if (storeOutput)
                    {
                        // Check if this is the first line in the stream to be read
                        if (output == null)
                        {
                            output = new StringBuilder();
                        }
                        // This isn't the first line of the stream
                        else
                        {
                            // Append a semicolon to differentiate the lines
                            output.append("; ");
                        }

                        // Append the output text
                        output.append(line);
                    }
                }

                br.close();
            }
            catch (IOException ioe)
            {
            }
        }
    }

    /**********************************************************************************************
     * Database control handler class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddDbControlHandler(CcddMain ccddMain)
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
        accessLevel = AccessLevel.READ_ONLY;
        backupFileName = "";
        isSSL = false;
        isFirstConnectionAttempt = true;
        addDefaultDataTypes = true;

        // Reset the flag that indicates a connection failure occurred due to a missing or invalid
        // user name or password
        isAuthenticationFail = false;

        // Create the parameters for the the 'by name' and 'by index' postgreSQL functions
        functionParameters = new String[][] {{"name", DefaultColumn.VARIABLE_NAME.getDbName()},
                                             {"index", DefaultColumn.ROW_INDEX.getDbName()}};
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
     * Get the SQL database connection
     *
     * @return The SQL database connection
     *********************************************************************************************/
    protected Connection getConnection()
    {
        return connection;
    }

    /**********************************************************************************************
     * Get the server connection status
     *
     * @return true if a connection to a database exists (default or otherwise)
     *********************************************************************************************/
    protected boolean isServerConnected()
    {
        return connectionStatus != NO_CONNECTION;
    }

    /**********************************************************************************************
     * Get the database connection status
     *
     * @return true if a connection to a database other than the default exists
     *********************************************************************************************/
    protected boolean isDatabaseConnected()
    {
        return connectionStatus == TO_DATABASE;
    }

    /**********************************************************************************************
     * Convert the specified project name into its equivalent PostgreSQL database name. All upper
     * case letters are set to lower case. Non-alphanumeric characters are replaced with
     * underscores(_), and an underscore is prepended if the name begins with a numeral. The
     * database name is truncated if necessary to the maximum length allowed by PostgreSQL
     *
     * @param projectName Project name
     *
     * @return The specified project name converted to its equivalent PostgreSQL database name
     *********************************************************************************************/
    protected String convertProjectNameToDatabase(String projectName)
    {
        // Convert any upper case characters to lower case, prepend an underscore if the name
        // begins with a numeral, and replace all special characters with an underscore
        String databaseName = projectName.toLowerCase().replaceFirst("^([0-9])", "_$1")
                                                       .replaceAll("[^a-z0-9_]", "_");

        // Check if the database name is longer than allowed
        if (databaseName.length() >= MAX_SQL_NAME_LENGTH)
        {
            // Truncate the name to the maximum allowed length
            databaseName = databaseName.substring(0, MAX_SQL_NAME_LENGTH - 1);
        }

        return databaseName;
    }

    /**********************************************************************************************
     * Get the active database name
     *
     * @return The active database name
     *********************************************************************************************/
    protected String getDatabaseName()
    {
        return activeDatabase;
    }

    /**********************************************************************************************
     * Set the active project and database names based on the database name
     *
     * @param databaseName Database name
     *********************************************************************************************/
    protected void setDatabaseName(String databaseName)
    {
        activeProject = databaseName.equals(DEFAULT_DATABASE) ? databaseName
                                                              : getProjectName(databaseName);
        activeDatabase = databaseName;
    }

    /**********************************************************************************************
     * Get the active project name
     *
     * @return The active project name
     *********************************************************************************************/
    protected String getProjectName()
    {
        return activeProject;
    }

    /**********************************************************************************************
     * Get the project name for the specified database
     *
     * @param databaseName Database name
     *
     * @return The project name for the specified database; the database name is used if the
     *         project name can't be retrieved
     *********************************************************************************************/
    protected String getProjectName(String databaseName)
    {
        String projectName = databaseName;

        // Get the database comment
        String[] comment = getDatabaseComment(databaseName);

        // Check if a comment was successfully retrieved
        if (comment != null)
        {
            // Get the project name
            projectName = comment[DatabaseComment.PROJECT_NAME.ordinal()];
        }

        return projectName;
    }

    /**********************************************************************************************
     * Set the active project and database names based on the project name
     *
     * @param projectName Project name
     *********************************************************************************************/
    protected void setProjectName(String projectName)
    {
        activeProject = projectName;
        activeDatabase = convertProjectNameToDatabase(projectName);
    }

    /**********************************************************************************************
     * Get the PostgreSQL server host
     *
     * @return PostgreSQL server host
     *********************************************************************************************/
    protected String getHost()
    {
        return serverHost;
    }

    /**********************************************************************************************
     * Set the PostgreSQL server host
     *
     * @param host PostgreSQL server host
     *********************************************************************************************/
    protected void setHost(String host)
    {
        serverHost = host;
    }

    /**********************************************************************************************
     * Get the PostgreSQL server port
     *
     * @return PostgreSQL server port
     *********************************************************************************************/
    protected String getPort()
    {
        return serverPort;
    }

    /**********************************************************************************************
     * Set the PostgreSQL server port
     *
     * @param port PostgreSQL server port
     *********************************************************************************************/
    protected void setPort(String port)
    {
        serverPort = port;
    }

    /**********************************************************************************************
     * Set the PostgreSQL server information
     *
     * @return PostgreSQL server information
     *********************************************************************************************/
    protected String getServer()
    {
        return serverHost + (serverPort.isEmpty() ? "" : ":" + serverPort);
    }

    /**********************************************************************************************
     * Get the status of the flag that indicates if an SSL connection is enabled
     *
     * @return true if SSL is enabled
     *********************************************************************************************/
    protected boolean isSSL()
    {
        return isSSL;
    }

    /**********************************************************************************************
     * Set the status of the flag that indicates if an SSL connection is enabled
     *
     * @param enable True to enable an SSL connection
     *********************************************************************************************/
    protected void setSSL(boolean enable)
    {
        isSSL = enable;
    }

    /**********************************************************************************************
     * Get the server user name
     *
     * @return The server user name
     *********************************************************************************************/
    protected String getUser()
    {
        return activeUser;
    }

    /**********************************************************************************************
     * Set the database user
     *
     * @param user Database user
     *********************************************************************************************/
    protected void setUser(String user)
    {
        activeUser = user;
    }

    /**********************************************************************************************
     * Set the user access level from the user authorization table. If the user isn't found in the
     * table then the access level is set to read only
     *********************************************************************************************/
    private void setAccessLevel()
    {
        boolean isFound = false;
        accessLevel = AccessLevel.READ_ONLY;

        // Step through the user authorization table
        for (String[] userAccess : ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.USERS,
                                                                                                false,
                                                                                                ccddMain.getMainFrame()))
        {
            // Check if the user is found in the table
            if (activeUser.equals(userAccess[UsersColumn.USER_NAME.ordinal()]))
            {
                // Step through each access level
                for (AccessLevel level : AccessLevel.values())
                {
                    // Check if the access level matches that of the user
                    if (level.getDisplayName().equals(userAccess[UsersColumn.ACCESS_LEVEL.ordinal()]))
                    {
                        // Set the user's access level and stop searching
                        accessLevel = level;
                        isFound = true;
                        break;
                    }
                }

                // The user was found in the table; stop searching
                if (isFound)
                {
                    break;
                }
            }
        }
    }

    /**********************************************************************************************
     * Check if the user has administrative access
     *
     * @return true if the user has administrative access
     *********************************************************************************************/
    protected boolean isAccessAdmin()
    {
        return accessLevel == AccessLevel.ADMIN;
    }

    /**********************************************************************************************
     * Get the list of databases for which the user has administrative access
     *
     * @return List of databases for which the user has administrative access; empty list if the
     *         user has no administrative access
     *********************************************************************************************/
    protected List<String> getUserAdminAccess()
    {
        List<String> adminAccess = new ArrayList<String>();

        // Step through the array containing the database name, lock status, visible (project)
        // name, project administrator(s), and description for each project to which the current
        // user has access
        for (String userDbInfo : queryDatabaseByUserList(ccddMain.getMainFrame(), activeUser))
        {
            // Separate the information retrieved into the database name and its comment, then
            // parse the comment into its separate fields
            String[] nameAndComment = userDbInfo.split(DATABASE_COMMENT_SEPARATOR, 2);
            String commentFields[] = parseDatabaseComment(nameAndComment[0], nameAndComment[1]);

            // Check if a comment was successfully retrieved
            if (commentFields != null)
            {
                // Step through each administrator for the project
                for (String admin : commentFields[DatabaseComment.ADMINS.ordinal()].split(DATABASE_ADMIN_SEPARATOR))
                {
                    // Check if the current user's name matches the administrator name
                    if (activeUser.equals(admin))
                    {
                        // Add the database name to the list and stop searching
                        adminAccess.add(nameAndComment[0]);
                        break;
                    }
                }
            }
        }

        return adminAccess;
    }

    /**********************************************************************************************
     * Check if the user has read-write access
     *
     * @return true if the user has read-write access
     *********************************************************************************************/
    protected boolean isAccessReadWrite()
    {
        return accessLevel == AccessLevel.ADMIN || accessLevel == AccessLevel.READ_WRITE;
    }

    /**********************************************************************************************
     * Get the database owner name
     *
     * @return The database owner name
     *********************************************************************************************/
    protected String getOwner()
    {
        return activeOwner;
    }

    /**********************************************************************************************
     * Check if the user's database password is not a blank
     *
     * @return true if the password is not blank
     *********************************************************************************************/
    protected boolean isPasswordNonBlank()
    {
        return !activePassword.isEmpty();
    }

    /**********************************************************************************************
     * Set the user's database password
     *
     * @param password User's database password
     *********************************************************************************************/
    protected void setPassword(String password)
    {
        activePassword = password;
    }

    /**********************************************************************************************
     * Set the file path and name to automatically backup the database to on first connecting
     *
     * @param backupFileName Backup file path and name
     *********************************************************************************************/
    protected void setBackupFileName(String backupFileName)
    {
        this.backupFileName = backupFileName;
    }

    /**********************************************************************************************
     * Force the supplied table or column name to lower case. Bound the name with double quotes if
     * it matches a PostgreSQL reserved word
     *
     * @param name Table or column name
     *
     * @return Supplied table or column name in lower case, bounded with double quotes if it
     *         matches a PostgreSQL reserved word (case insensitive)
     *********************************************************************************************/
    protected String getQuotedName(String name)
    {
        // Force the name to lower case
        name = name.toLowerCase();

        // Step through the list of reserved words
        for (String keyWord : keyWords)
        {
            // Check if the table/column name matches the reserved word
            if (name.equalsIgnoreCase(keyWord))
            {
                // Bound the supplied name with double quotes and stop searching
                name = "\"" + name + "\"";
                break;
            }
        }

        return name;
    }

    /**********************************************************************************************
     * Check if the supplied table or column name is a PostgreSQL reserved word
     *
     * @param name Table or column name
     *
     * @return true if the supplied name matches a reserved word (case insensitive)
     *********************************************************************************************/
    protected boolean isKeyWord(String name)
    {
        boolean isKeyWord = false;

        // Step through the list of reserved words
        for (String keyWord : keyWords)
        {
            // Check if the table/column name matches the reserved word
            if (name.equalsIgnoreCase(keyWord))
            {
                // Set the flag to indicate the supplied name is a reserved word and stop searching
                isKeyWord = true;
                break;
            }
        }

        return isKeyWord;
    }

    /**********************************************************************************************
     * Get the PostgreSQL server version
     *
     * @return String containing the database server version in the format &lt;major
     *         version&gt;.&lt;minor version&gt;; returns '*not connected*' if not connected to the
     *         PostgreSQL server
     *********************************************************************************************/
    protected String getDatabaseVersion()
    {
        String databaseVersion = "*not connected*";

        // Check if the PostgreSQL server is connected
        if (isServerConnected())
        {
            try
            {
                // Get a reference to the database metadata
                DatabaseMetaData metaData = connection.getMetaData();

                // Get the database version number
                databaseVersion = String.valueOf(metaData.getDatabaseMajorVersion()) + "."
                                  + String.valueOf(metaData.getDatabaseMinorVersion());
            }
            catch (SQLException se)
            {
                // Inform the user that the database version number is unavailable
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot obtain database version number; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot obtain database version number");
            }
        }

        return databaseVersion;
    }

    /**********************************************************************************************
     * Get the PostgreSQL major version number
     *
     * @return PostgreSQL major version number; -1 if not connected to the PostgreSQL server
     *********************************************************************************************/
    protected int getPostgreSQLMajorVersion()
    {
        int version = -1;

        // Check if the PostgreSQL server is connected
        if (isServerConnected())
        {
            try
            {
                // Get a reference to the database metadata
                DatabaseMetaData metaData = connection.getMetaData();

                // Get the database version number
                version = metaData.getDatabaseMajorVersion();
            }
            catch (SQLException se)
            {
                // Inform the user that the database version number is unavailable
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot obtain database version number; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot obtain database version number");
            }
        }

        return version;
    }

    /**********************************************************************************************
     * Get the JDBC version number
     *
     * @return String containing the JDBC version number; returns '*not connected*' if if not
     *         connected to the PostgreSQL server
     *********************************************************************************************/
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
                jdbcVersion = String.valueOf(metaData.getDriverVersion()) + " (type "
                              + String.valueOf(metaData.getJDBCMajorVersion()) + ")";
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

    /**********************************************************************************************
     * Retrieve a list of all users registered on the server
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return String array containing the user names
     *********************************************************************************************/
    protected String[] queryUserList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.USERS, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of databases with active connections by user
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return String array containing the database names and connected user names
     *********************************************************************************************/
    protected String[] queryActiveList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.ACTIVE_BY_USER, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all roles registered on the server
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return String array containing the role names
     *********************************************************************************************/
    protected String[] queryRoleList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.ROLES, null, parent);
    }

    /**********************************************************************************************
     * Retrieve the name of the specified database's owner
     *
     * @param databaseName Name of the database for which the owner is requested
     *
     * @param parent       GUI component over which to center any error dialog
     *
     * @return String array containing the name of the specified database's owner
     *********************************************************************************************/
    protected String[] queryDatabaseOwner(String databaseName, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASE_OWNER,
                                 new String[][] {{"_db_name_", databaseName.toLowerCase()}},
                                 parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all databases registered on the server
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return Array containing the database names and descriptions; empty list if no CCDD
     *         databases exist in the server
     *********************************************************************************************/
    protected String[] queryDatabaseList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASES, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of the databases registered on the server for which the current user has
     * access
     *
     * @param parent   GUI component over which to center any error dialog
     *
     * @param userName User name
     *
     * @return Array containing the database names and descriptions for which the current user has
     *         access; empty list if there are no CCDD databases in the server accessible by the
     *         user
     *********************************************************************************************/
    protected String[] queryDatabaseByUserList(Component parent, String userName)
    {
        return dbCommand.getList(DatabaseListCommand.DATABASES_BY_USER,
                                 new String[][] {{"_user_", userName}},
                                 parent);
    }

    /**
     * Query the list of all databases and determine if one exists with the given name
     *
     * @param queryDatabaseName The Database name to find
     * @return a database with that name exists or does not
     */
    protected boolean isDatabaseNameInUse(String queryDatabaseName)
    {
        // Get the list of available databases
        String[] databases = queryDatabaseList(ccddMain.getMainFrame());

        for (String name : databases)
        {
            // Check if the user-supplied name matches an existing database name
            if (name.split(DATABASE_COMMENT_SEPARATOR, 2)[0].equals(queryDatabaseName))
            {
                // Inform the user that the name is already in use
                return true;
            }
        }
        return false;
    }

    /**********************************************************************************************
     * Get the server + database
     *
     * @param databaseName Database name
     *
     * @return server + database
     *********************************************************************************************/
    private String getServerAndDatabase(String databaseName)
    {
        return getServer() + "/" + databaseName;
    }

    /**********************************************************************************************
     * Get the database URL. If SSL is enabled the appropriate properties are set; certificate
     * validation is bypassed.
     *
     * @param databaseName Database name
     *
     * @return Database URL
     *********************************************************************************************/
    protected String getDatabaseURL(String databaseName)
    {
        return "jdbc:postgresql://"
               + getServerAndDatabase(databaseName)
               + (isSSL ? "?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory" : "");
    }

    /**********************************************************************************************
     * Get the array containing the database comment fields (without the CFS project identifier)
     *
     * @param databaseName Database name
     *
     * @return Array containing the database comment in the format &lt;lock status (0 or
     *         1)&gt;,&lt;visible project database name (with capitalization and special characters
     *         intact)&gt;,&lt;project description&gt;; null if the comment cannot be retrieved
     *********************************************************************************************/
    protected String[] getDatabaseComment(String databaseName)
    {
        String[] commentFields = null;

        try
        {
            // Get the comment for the database
            ResultSet resultSet = dbCommand.executeDbQuery(new StringBuilder("SELECT description FROM pg_shdescription JOIN pg_database "
                                                                             + "ON objoid = pg_database.oid WHERE datname = '")
                                           .append(databaseName)
                                           .append("';"),
                                           ccddMain.getMainFrame());
            resultSet.next();

            // Parse the comment, with the CFS project identifier removed, into its separate fields
            commentFields = parseDatabaseComment(databaseName,
                                                 resultSet.getString(1).substring(CCDD_PROJECT_IDENTIFIER.length()));

            resultSet.close();
        }
        catch (SQLException se)
        {
            // Inform the user that loading the database comment failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot obtain comment for project database '"
                                  + getServerAndDatabase(databaseName)
                                  + "'; Internal table does not contain the correct information; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot obtain comment for project database '</b>"
                                  + databaseName
                                  + "<b>'");
        }

        return commentFields;
    }

    /**********************************************************************************************
     * Parse the supplied database comment string into an array of the comment's separate fields
     *
     * @param databaseName Database name
     *
     * @param comment      Database comment
     *
     * @return Array containing the database comment in the format &lt;lock status (0 or
     *         1)&gt;,&lt;visible project database name (with capitalization and special characters
     *         intact)&gt;,&lt;project description&gt;; null if the comment cannot be retrieved
     *********************************************************************************************/
    protected String[] parseDatabaseComment(String databaseName, String comment)
    {
        // Create storage for the comment fields
        String[] commentFields = new String[DatabaseComment.values().length];

        // Separate the comment into the individual fields
        String[] commentParts = comment.split(DATABASE_COMMENT_SEPARATOR, DatabaseComment.values().length);

        // Check if at least 3 fields exist, the first field is either '0' or '1', and the second
        // field is the project name
        if (commentParts.length >= 2 && commentParts[0].matches("[01]")
            && databaseName.equals(convertProjectNameToDatabase(commentParts[1])))
        {
            // Check if all the expected fields exist and the third field meets the constraints for
            // one or more user names
            if (commentParts.length >= DatabaseComment.values().length
                && commentParts[2].matches("(?:"
                                           + DefaultInputType.ALPHANUMERIC.getInputMatch()
                                           + DATABASE_ADMIN_SEPARATOR
                                           + "?)+"))
            {
                // Comment is in the post patch #07242018 format (lock status;project name;project
                // creator;description). This check can be fooled if the description contains a
                // semi-colon and the text prior to the semi-colon matches an alphanumeric. Store
                // the comment fields
                commentFields = commentParts;
            }
            // The comment is in the post patch #07112017 format (lock status;project
            // name;description)
            else
            {
                // Get the lock status, project name, and description from the comment
                commentFields[DatabaseComment.LOCK_STATUS.ordinal()] = commentParts[DatabaseComment.LOCK_STATUS.ordinal()];
                commentFields[DatabaseComment.PROJECT_NAME.ordinal()] = commentParts[DatabaseComment.PROJECT_NAME.ordinal()];
                commentFields[DatabaseComment.DESCRIPTION.ordinal()] = commentParts[DatabaseComment.ADMINS.ordinal()];

                // Set the project creator to a blank to indicate it's unknown
                commentFields[DatabaseComment.ADMINS.ordinal()] = "";
            }
        }
        // Comment is in the original format (lock status;description)
        else
        {
            // Get the lock status and description from the comment
            commentFields[DatabaseComment.LOCK_STATUS.ordinal()] = commentParts[0].substring(0, 1);
            commentFields[DatabaseComment.DESCRIPTION.ordinal()] = comment.substring(1);

            // Set the project name to the database name and set the project creator to a blank to
            // indicate it's unknown
            commentFields[DatabaseComment.PROJECT_NAME.ordinal()] = databaseName;
            commentFields[DatabaseComment.ADMINS.ordinal()] = "";
        }

        return commentFields;
    }

    /**********************************************************************************************
     * Get the database lock status
     *
     * @param databaseName Database name
     *
     * @return true if the database is locked, false if not locked, or null if the comment cannot
     *         be retrieved
     *********************************************************************************************/
    protected Boolean getDatabaseLockStatus(String databaseName)
    {
        Boolean lockStatus = null;

        // Get the database comment
        String[] comment = getDatabaseComment(databaseName);

        // Check if a comment was successfully retrieved
        if (comment != null)
        {
            // Determine the database lock status
            lockStatus = comment[DatabaseComment.LOCK_STATUS.ordinal()].equals("1");
        }

        return lockStatus;
    }

    /**********************************************************************************************
     * Set the database lock status. If the GUI is hidden the lock status is unchanged
     *
     * @param projectName Project name
     *
     * @param lockStatus  True if the database is locked; false if unlocked
     *********************************************************************************************/
    protected void setDatabaseLockStatus(String projectName, boolean lockStatus)
    {
        // Check if the GUI is visible. If the application is started with the GUI hidden (via
        // command line command) then the project database lock status is not changed. This allows,
        // for example, web server access or script execution when another instance of CCDD has
        // opened the database
        if (!ccddMain.isGUIHidden())
        {
            // Convert the project name into its database form
            String databaseName = convertProjectNameToDatabase(projectName);

            // Get the database comment
            String[] comment = getDatabaseComment(databaseName);

            // Check if a comment was successfully retrieved
            if (comment != null)
            {
                try
                {
                    // Set the database comment with the specified lock status
                    dbCommand.executeDbUpdate(new StringBuilder(buildDatabaseCommentCommandAndUpdateInternalTable(projectName,
                                                                                                                  comment[DatabaseComment.ADMINS.ordinal()],
                                                                                                                  lockStatus,
                                                                                                                  comment[DatabaseComment.DESCRIPTION.ordinal()])),
                                              ccddMain.getMainFrame());

                    // Inform the user that the lock status update succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Project '").append(projectName)
                                                                    .append("' ")
                                                                    .append((lockStatus ? "locked"
                                                                                        : "unlocked")));
                }
                catch (SQLException se)
                {
                    // Inform the user that setting the database comment failed
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot set comment for project database '"
                                          + getServerAndDatabase(databaseName)
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                          "<html><b>Cannot set comment for project '</b>"
                                          + projectName
                                          + "<b>'");
                }
            }
        }
    }

    /**********************************************************************************************
     * Get the project database administrator user name(s)
     *
     * @param databaseName Database name
     *
     * @return The project database administrator user name(s), separated by a comma if more than
     *         one; null if no administrator name is present in the database comment
     *********************************************************************************************/
    protected String getDatabaseAdmins(String databaseName)
    {
        String admins = null;

        // Get the database comment
        String[] comment = getDatabaseComment(databaseName);

        // Check if a comment was successfully retrieved and that the project administrator is
        // present
        if (comment != null && !comment[DatabaseComment.ADMINS.ordinal()].isEmpty())
        {
            // Add the user to the string of database administrators
            admins = comment[DatabaseComment.ADMINS.ordinal()];
        }

        return admins;
    }

    /**********************************************************************************************
     * Set the project database administrator user name(s)
     *
     * @param admins String containing the names of the user(s) with administrative access,
     *               separated by a comma
     *
     * @param parent GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void setDatabaseAdmins(String admins, Component parent)
    {
        try
        {
            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Update the database administrator(s) in the database comment
            dbCommand.executeDbCommand(new StringBuilder(buildDatabaseCommentCommandAndUpdateInternalTable(activeProject,
                                                                                                           admins,
                                                                                                           true,
                                                                                                           getDatabaseDescription(activeDatabase))),
                                       parent);

            // Log that updating the database administrator(s) succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder("Project '").append(activeProject)
                                                            .append("' administrators updated"));
        }
        catch (SQLException se)
        {
            // Inform the user that the database administrator(s) cannot be updated
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot update project database '"
                                  + getServerAndDatabase(activeDatabase)
                                  + "' administrator(s); cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot update project '</b>"
                                  + activeProject
                                  + "<b>' administrator(s)");
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }
    }

    /**********************************************************************************************
     * Get the owner of the specified project
     *
     * @param projectName Project name
     *
     * @param parent      GUI component over which to center any error dialog
     *
     * @return Project's owner; null if the project doen't exist
     *********************************************************************************************/
    protected String getProjectOwner(String projectName, Component parent)
    {
        String projectOwner = null;

        // Convert the project name into its database form
        String databaseName = convertProjectNameToDatabase(projectName);

        try
        {
            // Get the owner for the database
            ResultSet resultSet = dbCommand.executeDbQuery(new StringBuilder("SELECT pg_catalog.pg_get_userbyid(d.datdba) AS \"Owner\" "
                                                                             + "FROM pg_catalog.pg_database d WHERE d.datname = '")
                                           .append(databaseName)
                                           .append("';"),
                                           parent);
            resultSet.next();

            // Get the database owner
            projectOwner = resultSet.getString(1);

            resultSet.close();
        }
        catch (SQLException se)
        {
            // Inform the user that loading the database comment failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot obtain owner for project database '"
                                  + getServerAndDatabase(databaseName)
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot obtain owner for project '</b>"
                                  + projectName
                                  + "<b>'");
        }

        return projectOwner;
    }

    /**********************************************************************************************
     * Change the owner of the specified project
     *
     * @param projectName  Project name
     *
     * @param currentOwner Current owner of the database
     *
     * @param newOwner     New owner of the database
     *
     * @param parent       GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void changeProjectOwner(String projectName,
                                      String currentOwner,
                                      String newOwner,
                                      Component parent)
    {
        // Convert the project name into its database form
        String databaseName = convertProjectNameToDatabase(projectName);

        try
        {
            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Change the database owner
            dbCommand.executeDbCommand(new StringBuilder("ALTER DATABASE ").append(databaseName)
                                                                           .append(" OWNER TO ")
                                                                           .append(newOwner)
                                                                           .append("; REASSIGN OWNED BY ")
                                                                           .append(currentOwner)
                                                                           .append(" TO ")
                                                                           .append(newOwner)
                                                                           .append(";"),
                                                                           parent);

            // Log that changing the database owner succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder("Project '").append(projectName)
                                                            .append("' ownership changed"));
        }
        catch (SQLException se)
        {
            // Inform the user that the database owner cannot be changed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot change project database '"
                                  + getServerAndDatabase(databaseName)
                                  + "' ownership; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot change project '</b>"
                                  + projectName
                                  + "<b>' ownership");
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }
    }

    /**********************************************************************************************
     * Get the database description
     *
     * @param databaseName Database name
     *
     * @return The database description; null if no description exists
     *********************************************************************************************/
    protected String getDatabaseDescription(String databaseName)
    {
        String description = null;

        // Get the database comment
        String[] comment = getDatabaseComment(databaseName);

        // The comment could not be obtained from the pg_shdescription field of the DB. This means
        // that somehow the field has not been updated or the DB otherwise failed. So, try to get
        // it with another mechanism as a last ditch effort
        if (comment == null)
        {
            String[] userDatabaseInformation = ccddMain.getDbControlHandler().queryDatabaseByUserList(ccddMain.getMainFrame(),
                                                                                                      ccddMain.getDbControlHandler().getUser());

            for (String userDbInfo : userDatabaseInformation)
            {
                String[] nameAndComment = userDbInfo.split(DATABASE_COMMENT_SEPARATOR, 2);

                if (convertProjectNameToDatabase(databaseName).equals(nameAndComment[0]))
                {
                    comment = ccddMain.getDbControlHandler().parseDatabaseComment(nameAndComment[0],
                                                                                  nameAndComment[1]);
                    break;
                }
            }
        }
        // Check if a comment was successfully retrieved
        if (comment != null)
        {
            // Bounds check
            if (DatabaseComment.DESCRIPTION.ordinal() < comment.length)
            {
                // Get the database description
                description = comment[DatabaseComment.DESCRIPTION.ordinal()];
            }
        }

        return description;
    }

    /**********************************************************************************************
     * Build the command to create the database comment and updated the internal table _dub_info.
     * Delimit the text so that special characters (e.g., single quotes) can be placed in the
     * description
     *
     * @param projectName   Project name (with case and special characters preserved)
     *
     * @param administrator Name of the user(s) with administrative access to the project database
     *
     * @param lockStatus    True if the database is locked; false if unlocked
     *
     * @param description   Database description
     *
     * @return Command to create the database comment
     *********************************************************************************************/
    protected String buildDatabaseCommentCommandAndUpdateInternalTable(String projectName,
                                                                       String administrator,
                                                                       boolean lockStatus,
                                                                       String description)
    {
        if (activeOwner != "")
        {
            // Update the internal table
            List<String[]> tableData = new ArrayList<String[]>();
            tableData.add(new String[] {(CCDD_PROJECT_IDENTIFIER + (lockStatus ? "1" : "0"))
                                        + ","
                                        + getQuotedName(convertProjectNameToDatabase(projectName))
                                        +","
                                        + administrator
                                        + ","
                                        + description});

            ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.DBU_INFO,
                                  tableData,
                                  null,
                                  ccddMain.getMainFrame());

        }

        // Return the command
        return "COMMENT ON DATABASE "
               + getQuotedName(convertProjectNameToDatabase(projectName))
               + " IS "
               + CcddDbTableCommandHandler.delimitText(CCDD_PROJECT_IDENTIFIER
                                                       + (lockStatus ? "1" : "0")
                                                       + DATABASE_COMMENT_SEPARATOR
                                                       + projectName
                                                       + DATABASE_COMMENT_SEPARATOR
                                                       + administrator
                                                       + DATABASE_COMMENT_SEPARATOR
                                                       + description)
               + "; ";
    }

    /**********************************************************************************************
     * Build the command to change a database object's owner and grant access privileges to the
     * currently active database owner
     *
     * @param object     DatabaseObjects (DATABASE, TABLE, or FUNCTION)
     *
     * @param objectName Name of the database object to alter
     *
     * @return Command to change a database object's owner and grant access privileges to the
     *         current group
     *********************************************************************************************/
    protected String buildOwnerCommand(DatabaseObject object, String objectName)
    {
        return buildOwnerCommand(activeOwner, object, objectName);
    }

    /**********************************************************************************************
     * Build the command to change a database object's owner and grant access privileges to the
     * specified database owner
     *
     * @param ownerName  Name of the role or user that owns the database and its objects
     *
     * @param object     DatabaseObjects (DATABASE, TABLE, or FUNCTION)
     *
     * @param objectName Name of the database object to alter
     *
     * @return Command to change a database object's owner and grant access privileges to the
     *         specified group
     *********************************************************************************************/
    private String buildOwnerCommand(String ownerName, DatabaseObject object, String objectName)
    {
        // Bound the owner and object names with double quotes if the name matches a PostgreSQL
        // reserved word
        objectName = object.toString() + " " + getQuotedName(objectName);
        ownerName = getQuotedName(ownerName);

        return "ALTER "
               + objectName
               + " OWNER TO "
               + ownerName
               + "; GRANT ALL PRIVILEGES ON "
               + objectName
               + " TO GROUP "
               + ownerName
               + "; ";
    }

    /**********************************************************************************************
     * Disable the database change auto-commit mode
     *********************************************************************************************/
    private void resetAutoCommit()
    {
        try
        {
            // Disable auto-commit for database changes
            connection.setAutoCommit(false);
        }
        catch (SQLException se)
        {
            // Inform the user that disabling the database auto-commit flag failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot disable auto-commit; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot disable auto-commit");
        }
    }

    /**********************************************************************************************
     * Create a project database
     *
     * @param projectName   Name of the project to create, with case and special characters
     *                      preserved
     *
     * @param ownerName     Name of the role or user that owns the database and its objects
     *
     * @param administrator Project administrator name(s) (comma-separated)
     *
     * @param description   Database description
     *
     * @return true if the command completes successfully; false otherwise
     *********************************************************************************************/
    protected boolean createDatabase(final String projectName,
                                     String ownerName,
                                     String administrator,
                                     String description)
    {
        boolean successFlag = true;
        String databaseName = null;
        StringBuilder command = new StringBuilder();

        try
        {
            // Convert the project name into its database form
            databaseName = convertProjectNameToDatabase(projectName);

            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Execute the command to create the project database
            command.append("CREATE DATABASE ")
                   .append(getQuotedName(databaseName))
                   .append(" ENCODING 'UTF8'; ")
                   .append(buildDatabaseCommentCommandAndUpdateInternalTable(projectName,
                                                                             administrator,
                                                                             false,
                                                                             description))
                   .append(buildOwnerCommand(ownerName, DatabaseObject.DATABASE, databaseName))
                   .append(dbCommand.executeDbUpdate(command, ccddMain.getMainFrame()));

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder("Project '").append(projectName).append("' created"));
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
                                  "<html><b>Cannot create project '</b>"
                                  + projectName
                                  + "<b>'");
            successFlag = false;
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
            successFlag = false;
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }

        return successFlag;
    }

    /**********************************************************************************************
     * Create a project database. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param projectName   Name of the project to create, with case preserved
     *
     * @param ownerName     Name of the role or user that owns the database and its objects
     *
     * @param administrator Project administrator name(s) (comma-separated)
     *
     * @param description   Database description
     *********************************************************************************************/
    protected void createDatabaseInBackground(final String projectName,
                                              final String ownerName,
                                              final String administrator,
                                              final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Database creation command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the database
                createDatabase(projectName, ownerName, administrator, description);
            }
        });
    }

    /**********************************************************************************************
     * Create the reusable database functions and default tables. This does not include the default
     * column functions
     *
     * @return true if an error occurs creating the database functions or tables
     *********************************************************************************************/
    private boolean createTablesAndFunctions()
    {
        boolean errorFlag = false;
        StringBuilder command = new StringBuilder();

        try
        {
            command.append("CREATE OR REPLACE FUNCTION make_plpgsql() RETURNS VOID LANGUAGE SQL AS $$ CREATE LANGUAGE plpgsql; $$; ")
                   .append("SELECT CASE WHEN EXISTS(SELECT 1 FROM pg_catalog.pg_language WHERE lanname = 'plpgsql') ")
                   .append("THEN NULL ELSE make_plpgsql() END; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION, "make_plpgsql()"))
                   .append("DROP FUNCTION make_plpgsql();");

            // Send command to create the procedural language in the database if it does not
            // already exists
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
            command.setLength(0);

            command.append("CREATE OR REPLACE FUNCTION delete_function(function_name text) RETURNS VOID AS $$ BEGIN EXECUTE ")
                   .append("(SELECT 'DROP FUNCTION ' || oid::regproc || '(' || pg_get_function_identity_arguments(oid) ")
                   .append("|| ');' || E'\n' FROM pg_proc WHERE proname = function_name AND pg_function_is_visible(oid)); ")
                   .append("END $$ LANGUAGE plpgsql; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION, "delete_function(function_name text)"));

            // Create function to delete functions whether or not the input parameters match
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
            command.setLength(0);

            // Step through each internal table type
            for (InternalTable intTable : InternalTable.values())
            {
                // Reset the command
                command.setLength(0);

                // Check that this isn't the script table type. The script table is a special type
                // for storing specific scripts
                if (intTable != InternalTable.SCRIPT)
                {
                    // Check if the internal table doesn't exist
                    if (!ccddMain.getDbTableCommandHandler().isTableExists(intTable.getTableName(),
                                                                           ccddMain.getMainFrame()))
                    {
                        if (intTable != InternalTable.DBU_INFO)
                        {
                            // Create the default internal table
                            command.append(buildInformationTableCommand(intTable));
                            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
                        }
                        else
                        {
                            command.setLength(0);
                            String[] comment = getDatabaseComment(activeDatabase);

                            command.append((buildInformationTableCommand(intTable).replace(DB_PLACEHOLDER_LOCK,
                                                                                           CCDD_PROJECT_IDENTIFIER + comment[0])
                                                                                  .replace(DB_PLACEHOLDER_NAME,
                                                                                           comment[1])).replace(DB_PLACEHOLDER_ADMINS,
                                                                                                                comment[2]).replace(DB_PLACEHOLDER_DESC,
                                                                                                                                    comment[3]));

                            // Create the default internal table
                            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
                        }
                    }
                }
            }

            // Create function to search all tables for the input text with case sensitivity
            // determined by an input flag. A second flag determines if the search string is
            // treated as a literal string or as a regular expression. If as a literal, then all
            // non-alphanumeric and non-space characters are escaped so that these characters can
            // be detected correctly. A third flag determines if all tables or only the data tables
            // (including entries in the custom values table) are searched. Returns a table giving
            // the unique schema, table, column name, table comment, and contents of the columns in
            // the table row where the text is found
            command.setLength(0);
            command.append(deleteFunction("search_tables"))
                   .append("CREATE OR REPLACE FUNCTION search_tables(search_text text, ")
                   .append("no_case boolean, allow_regex boolean, selected_tables text, columns name[] DEFAULT '{}', all_schema ")
                   .append("name[] DEFAULT '{public}') RETURNS table(schema_name text, table_name text, column_name text, ")
                   .append("table_description text, column_value text) AS $$ DECLARE search_text text := regexp_replace(search_text, ")
                   .append("E'([^a-zA-Z0-9 ])', E'\\\\\\\\\\\\1', 'g'); BEGIN FOR schema_name, table_name, table_description, column_name ")
                   .append("IN SELECT c.table_schema, c.table_name, coalesce(d.description,''), c.column_name FROM information_schema.columns ")
                   .append("c JOIN information_schema.tables AS t ON (t.table_name = c.table_name AND t.table_schema = c.table_schema), ")
                   .append("pg_description AS d RIGHT JOIN pg_class ON d.objoid = pg_class.oid RIGHT JOIN pg_namespace ON pg_class.relnamespace = ")
                   .append("pg_namespace.oid WHERE (selected_tables ~* '")
                   .append(SearchType.ALL.toString())
                   .append("' OR (selected_tables ~* '")
                   .append(SearchType.PROTO.toString())
                   .append("' AND c.table_name !~ E'^")
                   .append(INTERNAL_TABLE_PREFIX).append(".*$') OR ")
                   .append("(selected_tables ~* '")
                   .append(SearchType.DATA.toString())
                   .append("' AND c.table_name !~ E'^")
                   .append(INTERNAL_TABLE_PREFIX).append("((?!")
                   .append(InternalTable.VALUES.getTableName().replaceFirst("^" + INTERNAL_TABLE_PREFIX, ""))
                   .append(").)*$') OR ")
                   .append("(selected_tables ~* '")
                   .append(SearchType.INPUT.toString())
                   .append("' AND ((c.table_name ~* E'^")
                   .append(InternalTable.TABLE_TYPES.getTableName())
                   .append("') OR (c.table_name ~* E'^")
                   .append(InternalTable.FIELDS.getTableName())
                   .append("'))) OR (selected_tables ~* '")
                   .append(SearchType.SCRIPT.toString())
                   .append("' AND c.table_name ~ E'^")
                   .append(InternalTable.SCRIPT.getTableName())
                   .append(".*')) AND (array_length(columns, 1) IS NULL OR c.column_name = ")
                   .append("ANY(columns)) AND c.table_schema = ANY(all_schema) AND t.table_type = 'BASE TABLE' AND relname = t.table_name AND ")
                   .append("nspname = t.table_schema AND (d.objsubid = '0' OR d.objsubid IS NULL) LOOP DECLARE the_row RECORD; BEGIN FOR ")
                   .append("the_row IN EXECUTE 'SELECT * FROM ' || quote_ident(schema_name) || '.' || quote_ident(table_name) || ' WHERE (' || ")
                   .append("quote_nullable(allow_regex) || ' = ''false'' AND ((' || quote_nullable(no_case) || ' = ''true'' AND cast(' || ")
                   .append("quote_ident(column_name) || ' AS text) ~* ' || quote_nullable(search_text) || ') OR (' || quote_nullable(no_case) ")
                   .append("|| ' = ''false'' AND cast(' || quote_ident(column_name) || ' AS text) ~ ' || quote_nullable(search_text) || ")
                   .append("'))) OR (' || quote_nullable(allow_regex) || ' = ''true'' AND ((' || quote_nullable(no_case) || ' = ''true'' ")
                   .append("AND cast(' || quote_ident(column_name) || ' AS text) ~* E''' || search_text || ''') OR (' || quote_nullable(no_case) ")
                   .append("|| ' = ''false'' AND cast(' || quote_ident(column_name) || ' AS text) ~ E''' || search_text || ''')))' LOOP ")
                   .append("SELECT * FROM regexp_replace(the_row::text, E'^\\\\(|(\\\\)$)', '', 'g') INTO column_value; RETURN NEXT; END LOOP; END; ")
                   .append("END LOOP; END; $$ LANGUAGE plpgsql; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                             "search_tables(search_text "
                                             + "text, no_case boolean, "
                                             + "allow_regex boolean, "
                                             + "selected_tables text, "
                                             + "columns name[],"
                                             + "all_schema name[])"));
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
            command.setLength(0);

            // Create function to retrieve all table names and column values for the tables with
            // the specified column name currently in use (i.e., blank column values are ignored)
            // in the tables of the specified table type(s)
            command.append(deleteFunction("find_prototype_columns_by_name"))
                   .append("CREATE OR REPLACE FUNCTION find_prototype_columns_by_name(")
                   .append("column_name_db text, table_types text[]) RETURNS table(owner_name text, column_value text) AS $$ BEGIN DECLARE row ")
                   .append("record; BEGIN TRUNCATE ")
                   .append(TEMP_TABLE_NAME)
                   .append("; INSERT INTO ")
                   .append(TEMP_TABLE_NAME)
                   .append(" SELECT tbl_name ")
                   .append("FROM (SELECT split_part(obj_description, ',', 1) AS tbl_name, split_part(obj_description, ',', 2) AS tbl_type FROM (SELECT ")
                   .append("obj_description(oid) FROM pg_class WHERE relkind = 'r' AND obj_description(oid) != '') AS tbl_desc) AS temp_result WHERE ")
                   .append("table_types @> ARRAY[tbl_type] ORDER BY temp_result ASC; FOR row IN SELECT temp_result FROM ")
                   .append(TEMP_TABLE_NAME)
                   .append(" LOOP IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = lower(row.temp_result) AND column_name = E''")
                   .append(" || column_name_db || E'') THEN RETURN QUERY EXECUTE E'SELECT ''' || row.temp_result || '''::text, ' || column_name_db || ")
                   .append("E' FROM ' || row.temp_result || E' WHERE ' || column_name_db || E' != '''''; END IF; END LOOP; END; END; $$ LANGUAGE plpgsql; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                             "find_prototype_columns_by_name(column_name_db text, table_types text[])"));
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
            command.setLength(0);

            // Create function to retrieve all table names and column values for the tables with
            // the specified column name currently in use (i.e., blank column values are ignored)
            // in the tables of the specified table type(s). Include columns from both the
            // prototype and custom values tables. Use SELECT DISTINCT on the results to eliminate
            // duplicate table names and/or column values
            command.append(deleteFunction("find_columns_by_name"))
                   .append("CREATE OR REPLACE FUNCTION find_columns_by_name(column_name_user text, ")
                   .append("column_name_db text, table_types text[]) RETURNS table(owner_name text, column_value text) AS $$ BEGIN RETURN QUERY EXECUTE ")
                   .append("E'SELECT owner_name, column_value FROM (SELECT owner_name, column_value FROM find_prototype_columns_by_name(''' || column_name_db ")
                   .append("|| E''', ''' || table_types::text || E''') UNION ALL (SELECT ")
                   .append(ValuesColumn.TABLE_PATH.getColumnName())
                   .append(", ")
                   .append(ValuesColumn.VALUE.getColumnName())
                   .append(" FROM ")
                   .append(InternalTable.VALUES.getTableName())
                   .append(" WHERE column_name = ''' || column_name_user || E''')) AS name_and_value ORDER BY owner_name;'; END; $$ LANGUAGE plpgsql; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                             "find_columns_by_name(column_name_user text, column_name_db text, table_types text[])"));
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
            command.setLength(0);

            // Create function to reset the rate for a link that no longer has any member variables
            command.append(deleteFunction("reset_link_rate"))
                   .append("CREATE FUNCTION reset_link_rate() RETURNS VOID AS $$ BEGIN DECLARE row record;")
                   .append(" BEGIN TRUNCATE ")
                   .append(TEMP_TABLE_NAME)
                   .append("; INSERT INTO ")
                   .append(TEMP_TABLE_NAME)
                   .append(" SELECT ")
                   .append(LinksColumn.LINK_NAME.getColumnName())
                   .append(" AS temp_result FROM (SELECT ")
                   .append(LinksColumn.LINK_NAME.getColumnName())
                   .append(", regexp_replace(")
                   .append(LinksColumn.MEMBER.getColumnName())
                   .append(", E'^([0-9])*.*', E'\\\\1') AS rate FROM ")
                   .append(InternalTable.LINKS.getTableName())
                   .append(") AS result WHERE rate != '' AND rate != '0'; FOR row IN SELECT * FROM ")
                   .append(TEMP_TABLE_NAME)
                   .append(" LOOP IF EXISTS (SELECT * FROM (SELECT COUNT(1) FROM ")
                   .append(InternalTable.LINKS.getTableName())
                   .append(" WHERE ")
                   .append(LinksColumn.LINK_NAME.getColumnName())
                   .append(" = row.temp_result) AS alias1 WHERE count = '1') THEN ")
                   .append("EXECUTE E'UPDATE ")
                   .append(InternalTable.LINKS.getTableName())
                   .append(" SET ")
                   .append(LinksColumn.MEMBER.getColumnName())
                   .append(" = regexp_replace(")
                   .append(LinksColumn.MEMBER.getColumnName())
                   .append(", E''^\\\\\\\\d+'', ''0'') WHERE ")
                   .append(LinksColumn.LINK_NAME.getColumnName())
                   .append(" = ''' || row.temp_result || ''''; END IF; END LOOP; END; END; $$ ")
                   .append("LANGUAGE plpgsql; ")
                   .append(buildOwnerCommand(DatabaseObject.FUNCTION, "reset_link_rate()"));
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());

            // Inform the user that the database table function creation succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder("Database tables and functions created"));
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

    /**********************************************************************************************
     * Create a temporary table for storing the results returned by the database functions
     *
     * @throws SQLException If an error occurs when creating the temporary table
     *********************************************************************************************/
    private void createTemporaryTable() throws SQLException
    {
        dbCommand.executeDbCommand(new StringBuilder("DROP TABLE IF EXISTS ").append(TEMP_TABLE_NAME)
                                                                             .append("; CREATE TEMPORARY TABLE ")
                                                                             .append(TEMP_TABLE_NAME)
                                                                             .append(" (temp_result text); ")
                                                                             .append(buildOwnerCommand(DatabaseObject.TABLE,
                                                                                                       TEMP_TABLE_NAME)),
                                                                             ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Create the reusable database functions for obtaining structure table members and
     * structure-defining column values
     *
     * @return true if an error occurs creating the structure functions
     *********************************************************************************************/
    protected boolean createStructureColumnFunctions()
    {
        boolean errorFlag = false;

        // Check if connected to a project database
        if (isDatabaseConnected())
        {
            try
            {
                // Use the default structure column names for those columns required by a structure
                String dbVariableName = DefaultColumn.VARIABLE_NAME.getDbName();
                String dbDataType = DefaultColumn.DATA_TYPE.getDbName();
                String dbArraySize = DefaultColumn.ARRAY_SIZE.getDbName();
                String dbBitLength = DefaultColumn.BIT_LENGTH.getDbName();
                String dbIndex = DefaultColumn.ROW_INDEX.getDbName();

                // Create a string containing the partial command for determining if the columns
                // that are necessary to define a structure table are present in a table
                String defStructCols = "(column_name = '"
                                       + dbVariableName
                                       + "' OR "
                                       + "column_name = '"
                                       + dbDataType
                                       + "' OR "
                                       + "column_name = '"
                                       + dbArraySize
                                       + "' OR "
                                       + "column_name = '"
                                       + dbBitLength
                                       + "')";

                // Create functions for gathering the structure table member information sorted
                // alphabetically by name or numerically by row index
                for (String[] functionParm : functionParameters)
                {
                    // Create function to get the table name, data type, variable name, bit length,
                    // sample rate, and enumeration for all structure tables that contain at least
                    // one row, sorted by table name or index, and then by variable name. For
                    // arrays, only the members are retrieved; the array definitions are ignored
                    dbCommand.executeDbCommand(new StringBuilder(deleteFunction("get_table_members_by_"
                                                                                + functionParm[0])).append("CREATE FUNCTION get_table_members_by_")
                                                                                                   .append(functionParm[0])
                                                                                                   .append("() RETURNS TABLE(tbl_name text, data_type ")
                                                                                                   .append("text, variable_name text, bit_length text, ")
                                                                                                   .append("rate text, enumeration text) AS $$ BEGIN ")
                                                                                                   .append("DECLARE row record; BEGIN TRUNCATE ")
                                                                                                   .append(TEMP_TABLE_NAME)
                                                                                                   .append("; INSERT INTO ")
                                                                                                   .append(TEMP_TABLE_NAME)
                                                                                                   .append(" SELECT t.tablename AS temp_result FROM ")
                                                                                                   .append("pg_tables AS t WHERE t.schemaname = 'public' ")
                                                                                                   .append("AND substr(t.tablename, 1, 2) != '")
                                                                                                   .append(INTERNAL_TABLE_PREFIX)
                                                                                                   .append("' ORDER BY temp_result ASC; FOR row IN SELECT * FROM ")
                                                                                                   .append(TEMP_TABLE_NAME)
                                                                                                   .append(" LOOP IF EXISTS (SELECT * FROM ")
                                                                                                   .append("(SELECT COUNT(1) FROM information_schema.columns ")
                                                                                                   .append("WHERE table_name = row.temp_result AND (")
                                                                                                   .append(defStructCols)
                                                                                                   .append(")) AS alias1 WHERE count = '")
                                                                                                   .append(DefaultColumn.getTypeRequiredColumnCount(TYPE_STRUCTURE))
                                                                                                   .append("') THEN RETURN QUERY EXECUTE E'SELECT ''' || ")
                                                                                                   .append("row.temp_result || '''::text, ")
                                                                                                   .append(dbDataType)
                                                                                                   .append(", ")
                                                                                                   .append(dbVariableName)
                                                                                                   .append(", ")
                                                                                                   .append(dbBitLength)
                                                                                                   .append(", ")
                                                                                                   .append(DefaultColumn.RATE.getDbName())
                                                                                                   .append(", ")
                                                                                                   .append(DefaultColumn.ENUMERATION.getDbName())
                                                                                                   .append(" ")
                                                                                                   .append("FROM get_def_columns_by_")
                                                                                                   .append(functionParm[0])
                                                                                                   .append("(''' || row.temp_result || ''')'; END IF; ")
                                                                                                   .append("END LOOP; END; END; $$ LANGUAGE plpgsql; ")
                                                                                                   .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                                                                                                             "get_table_members_by_"
                                                                                                                             + functionParm[0]
                                                                                                                             + "()")),
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
                        // Get the rate column name (in its database form, quoted if needed to
                        // avoid a conflict with a PostgreSQL reserved word, and not quoted)
                        String rateColNameQuoted = ccddMain.getTableTypeHandler()
                                                           .convertVisibleToDatabase(rateInfo.getRateName(),
                                                                                     DefaultInputType.RATE.getInputName(),
                                                                                     true);
                        String rateColName = rateColNameQuoted.replaceAll("\"", "");

                        // Add detection for the rate column. If the column doesn't exist in the
                        // table then a blank is returned for that column's rate value
                        rateCol += "CASE WHEN "
                                   + rateColName
                                   + "_exists THEN "
                                   + rateColNameQuoted
                                   + "::text ELSE ''''::text END || '','' || ";
                        rateJoin += " CROSS JOIN (SELECT EXISTS (SELECT 1 FROM "
                                    + "pg_catalog.pg_attribute WHERE attrelid = ''' "
                                    + "|| name || '''::regclass AND attname  = ''"
                                    + rateColName
                                    + "'' "
                                    + "AND NOT attisdropped AND attnum > 0) AS "
                                    + rateColName
                                    + "_exists) "
                                    + rateColNameQuoted;
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
                    // Build the enumeration separator (triple backslashes) portion of the command
                    String enumSep = " || E''' || E'\\\\\\\\\\\\\\\\\\\\\\\\' || ''' || ";

                    // Step through each enumeration column name in its database form
                    for (String enumColName : enumColumns)
                    {
                        // Get the enumeration column name , quoted if needed to avoid a conflict
                        // with a PostgreSQL reserved word
                        String enumColNameQuoted = getQuotedName(enumColName);

                        // Add detection for the enumeration column. If the column doesn't exist in
                        // the table then a blank is returned for that column's enumeration value
                        enumCol += "CASE WHEN "
                                   + enumColName
                                   + "_exists THEN "
                                   + enumColNameQuoted
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
                                    + enumColNameQuoted;
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

                // Create functions for gathering the structure table member information sorted
                // alphabetically by name or numerically by row index
                for (String[] functionParm : functionParameters)
                {
                    // Create function to get the data type, variable name, bit length, rate, and
                    // enumeration column data for the specified table. For arrays, only the
                    // members are retrieved; the array definitions are ignored. When sorting by
                    // variable name the array members are kept in order based on the numeric
                    // value of the array index by using the table's index column
                    StringBuilder cmd = new StringBuilder(deleteFunction("get_def_columns_by_"
                                                                         + functionParm[0])).append("CREATE FUNCTION get_def_columns_by_")
                                                                                            .append(functionParm[0])
                                                                                            .append("(name text) RETURNS TABLE(_index_ int, ");

                    if (functionParm[1].contentEquals(dbVariableName))
                    {
                        cmd.append("sort_name text, ");
                    }

                    cmd.append("data_type ")
                       .append("text, variable_name text, bit_length text, ")
                       .append("rate text, enumeration text) AS $$ BEGIN RETURN QUERY EXECUTE 'SELECT ")
                       .append(dbIndex)
                       .append(", ");

                    if (functionParm[1].contentEquals(dbVariableName))
                    {
                        cmd.append("regexp_replace(")
                           .append(dbVariableName)
                           .append(", E''\\\\[\\\\d+\\\\]'', '''', ''g'') AS sort_name, ");
                    }

                    cmd.append(dbDataType)
                       .append(", ")
                       .append(dbVariableName)
                       .append(", ")
                       .append(dbBitLength)
                       .append(", ")
                       .append(rateCol)
                       .append(", ")
                       .append(enumCol)
                       .append(" FROM \"' || name || '\"")
                       .append(rateJoin)
                       .append(enumJoin)
                       .append(" WHERE ")
                       .append(dbArraySize)
                       .append(" = E'''' OR (array_size ~ E''^")
                       .append(MACRO_IDENTIFIER)
                       .append("'' AND (SELECT EXISTS (SELECT ")
                       .append(MacrosColumn.VALUE.getColumnName())
                       .append(" FROM ")
                       .append(InternalTable.MACROS.getTableName())
                       .append(" WHERE ")
                       .append(MacrosColumn.MACRO_NAME.getColumnName())
                       .append(" = replace('''' || array_size || '''', ''")
                       .append(MACRO_IDENTIFIER)
                       .append("'', '''') AND ")
                       .append(MacrosColumn.VALUE.getColumnName())
                       .append(" = ''''))) OR ")
                       .append(dbVariableName)
                       .append(" ~ E''^.+]'' ORDER BY ");

                    if (functionParm[1].contentEquals(dbVariableName))
                    {
                        cmd.append("sort_name ASC, ")
                           .append(dbIndex);

                    }
                    else
                    {
                        cmd.append(functionParm[1]);
                    }

                    cmd.append(" ASC'; END $$ LANGUAGE plpgsql; ")
                       .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                                 "get_def_columns_by_"
                                                 + functionParm[0]
                                                 + "(name text)"));
                    dbCommand.executeDbCommand(cmd, ccddMain.getMainFrame());
                }

                // Create the function to get the data type and variable name from tables of the
                // supplied table type(s) in the format tableName,dataType.variableName
                dbCommand.executeDbCommand(new StringBuilder(deleteFunction("find_command_arguments")).append("CREATE OR REPLACE FUNCTION find_command_arguments(")
                                                                                                      .append("table_types text[]) RETURNS table(var_path text) ")
                                                                                                      .append("AS $$ BEGIN DECLARE row record; BEGIN TRUNCATE ")
                                                                                                      .append(TEMP_TABLE_NAME)
                                                                                                      .append("; INSERT INTO ")
                                                                                                      .append(TEMP_TABLE_NAME)
                                                                                                      .append(" SELECT tbl_name FROM (SELECT split_part(")
                                                                                                      .append("obj_description, ',', 1) AS tbl_name, split_part(")
                                                                                                      .append("obj_description, ',', 2) AS tbl_type FROM (SELECT ")
                                                                                                      .append("obj_description(oid) FROM pg_class WHERE relkind = ")
                                                                                                      .append("'r' AND obj_description(oid) != '') AS tbl_desc) AS ")
                                                                                                      .append("temp_result WHERE table_types @> ARRAY[tbl_type] ORDER ")
                                                                                                      .append("BY temp_result ASC; FOR row IN SELECT temp_result FROM ")
                                                                                                      .append(TEMP_TABLE_NAME)
                                                                                                      .append(" LOOP IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = lower(")
                                                                                                      .append("row.temp_result)) THEN RETURN QUERY EXECUTE E'SELECT ")
                                                                                                      .append("''' || row.temp_result || '''::text UNION SELECT ''' ")
                                                                                                      .append("|| row.temp_result || '''::text || '','' || ")
                                                                                                      .append(dbDataType)
                                                                                                      .append(" || ''.'' || ")
                                                                                                      .append(dbVariableName)
                                                                                                      .append(" FROM ' || row.temp_result || E''; END IF; END LOOP; END; END; $$ LANGUAGE plpgsql; ")
                                                                                                      .append(buildOwnerCommand(DatabaseObject.FUNCTION,
                                                                                                                                "find_command_arguments(table_types text[])")),
                                                                                                      ccddMain.getMainFrame());

                // Inform the user that the database function creation succeeded
                eventLog.logEvent(SUCCESS_MSG, new StringBuilder("Database structure functions created"));
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

    /**********************************************************************************************
     * Build the command to delete a database function. This deletes the function whether or not
     * the input parameters match
     *
     * @param functionName Name of the function to delete
     *
     * @return Command to delete the specified database function
     *********************************************************************************************/
    private String deleteFunction(String functionName)
    {
        return "SELECT CASE WHEN EXISTS(SELECT * FROM pg_proc WHERE proname = '"
               + functionName
               + "' AND pg_function_is_visible(oid)) THEN delete_function('"
               + functionName
               + "') END; ";
    }

    /**********************************************************************************************
     * Build the command to create an internal table
     *
     * @param intTable Type of internal table to build
     *
     * @return Command to build the specified internal table
     *********************************************************************************************/
    protected String buildInformationTableCommand(InternalTable intTable)
    {
        // Get the internal table's column build command
        String columnCommand = intTable.getColumnCommand(true);

        // Check if this is the user authorization table
        if (intTable == InternalTable.USERS)
        {
            // Get the project creator's user name
            String creator = getDatabaseAdmins(activeDatabase);

            // Check if the creator name is present in the database comment
            if (creator != null)
            {
                // Update the column build command with the creator name
                columnCommand = columnCommand.replaceFirst(USER_PLACEHOLDER_ADMIN_USER, creator);
            }
        }

        return "CREATE TABLE "
               + intTable.getTableName()
               + " "
               + columnCommand
               + buildOwnerCommand(DatabaseObject.TABLE, intTable.getTableName());
    }

    /**********************************************************************************************
     * Authenticate the specified user credentials for the PostgreSQL server and the currently open
     * database
     *
     * @param userName User name
     *
     * @param password User password
     *
     * @return true if the user is allowed access to the currently open database
     *********************************************************************************************/
    protected boolean authenticateUser(String userName, String password)
    {
        boolean isAllowed = false;

        try
        {
            // Check if the user credentials are valid for the PostgreSQL server
            Connection validateConn = DriverManager.getConnection(getDatabaseURL(activeDatabase),
                                                                  userName,
                                                                  password);

            // Close the connection
            validateConn.close();

            // Check if the default database isn't selected
            if (!activeDatabase.equals(DEFAULT_DATABASE))
            {
                // Step through each database for which the user has access
                for (String database : queryDatabaseByUserList(ccddMain.getMainFrame(), userName))
                {
                    // Check if the database name is in the list, which indicates that the user has
                    // access to this database
                    if (activeDatabase.equalsIgnoreCase(database.split(DATABASE_COMMENT_SEPARATOR, 2)[0]))
                    {
                        // Set the flag indicating the user has access to the currently open
                        // database and stop searching
                        isAllowed = true;
                        break;
                    }
                }
            }
        }
        catch (SQLException se)
        {
            // Ignore any database error condition
        }

        return isAllowed;
    }

    /**********************************************************************************************
     * Connect to the server (default database)
     *
     * @return true if the connection attempt failed
     *********************************************************************************************/
    protected boolean connectToServer()
    {
        return connectToDatabase(DEFAULT_DATABASE, DEFAULT_DATABASE, false);
    }

    /**********************************************************************************************
     * Connect to a database
     *
     * @param projectName  Name of the project to open
     *
     * @param databaseName Name of the database to open
     *
     * @param isReconnect  True if this is an attempt to reconnect to the database following a
     *                     failed transaction
     *
     * @return true if the connection attempt failed
     *********************************************************************************************/
    protected boolean connectToDatabase(String projectName,
                                        String databaseName,
                                        boolean isReconnect)
    {
        boolean errorFlag = false;

        try
        {
            connectionStatus = NO_CONNECTION;

            // Set the time allowed for the connection to occur
            DriverManager.setLoginTimeout(ModifiableSizeInfo.POSTGRESQL_CONNECTION_TIMEOUT.getSize());
            Properties properties = new Properties();
            properties.put("connectTimeout",
                           String.valueOf(ModifiableSizeInfo.POSTGRESQL_CONNECTION_TIMEOUT.getSize() * 1000));

            // Connect the user to the database
            connection = DriverManager.getConnection(getDatabaseURL(databaseName), activeUser, activePassword);

            dbCommand.setStatement(connection.createStatement());

            // Reset the flag that indicates a connection failure occurred due to a missing or
            // invalid user name or password
            isAuthenticationFail = false;

            // Set the transaction isolation mode to serializable to prevent transaction collisions
            // if there are concurrent users of the database
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            // Disable automatic commit of database updates. This allows database commands to be
            // grouped prior to committing
            connection.setAutoCommit(false);

            // The connection to the server must exist in order to reach this point, so set the
            // connection status to indicate the server is connected. If connecting to a specific
            // project, once the connection is completed the flag is updated accordingly
            connectionStatus = TO_SERVER_ONLY;

            // Store the database connection in the database command handler
            dbCommand.setConnection(connection);

            // Set the query and network timeouts
            dbCommand.setNetworkAndQueryTimeouts(ccddMain.getMainFrame());

            // Save the name of the newly connected database
            setDatabaseName(databaseName);

            // Check if the reserved word list hasn't been retrieved or if a connection to the
            // server is made (the new server may be a different PostgreSQL version and have
            // different key words)
            if (keyWords == null || databaseName.equals(DEFAULT_DATABASE))
            {
                // Get the array of reserved words
                keyWords = dbCommand.getList(DatabaseListCommand.KEYWORDS, null, ccddMain.getMainFrame());
            }

            // Check if the default database is selected
            if (databaseName.equals(DEFAULT_DATABASE))
            {
                // Check if this is a reconnection attempt
                if (isReconnect)
                {
                    // Recreate a temporary table for storing the results returned by the database
                    // functions (this table is deleted by the server when the connection drops)
                    createTemporaryTable();
                }

                // Inform the user that the server connection succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder((isReconnect ? "Reconnected"
                                                                 : "Connected")).append(" to server as user '")
                                                                                .append(activeUser)
                                                                                .append("'"));
            }
            // A database other than the default is selected
            else
            {
                // Get the database lock status (note that the database isn't locked if the GUI is
                // hidden)
                Boolean isLocked = getDatabaseLockStatus(databaseName);

                // Check if an error occurred obtaining the lock status
                if (isLocked == null)
                {
                    // Set the error flag
                    throw new CCDDException("");
                }

                // Check if the database is locked and this isn't a reconnection attempt
                if (!isReconnect && isLocked)
                {
                    throw new SQLException("database is locked");
                }

                boolean isAllowed = false;

                // Step through each database for which the user has access
                for (String database : queryDatabaseByUserList(ccddMain.getMainFrame(), activeUser))
                {
                    // Check if the database name is in the list, which indicates that the user has
                    // access to this database
                    if (databaseName.equalsIgnoreCase(database.split(DATABASE_COMMENT_SEPARATOR, 2)[0]))
                    {
                        // Set the flag indicating the user has access and stop searching
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
                activeOwner = queryDatabaseOwner(databaseName, ccddMain.getMainFrame())[0];

                // Set the connection status to indicate a database is connected
                connectionStatus = TO_DATABASE;

                // Check if this is a reconnection attempt
                if (isReconnect)
                {
                    // Recreate a temporary table for storing the results returned by the database
                    // functions (this table is deleted by the server when the connection drops)
                    createTemporaryTable();
                }

                // Inform the user that the database connection succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder((isReconnect ? "Reconnected"
                                                                 : "Connected")).append(" to project '")
                                                                                .append(activeProject)
                                                                                .append("' as user '")
                                                                                .append(activeUser)
                                                                                .append("'"));
            }
        }
        catch (SQLException se)
        {
            // Check if the connection failed due to a missing or invalid password
            if ((se.getMessage().contains("authentication failed") || se.getMessage().contains("password"))
                && !ccddMain.isGUIHidden())
            {
                // Set the flag that indicates a connection failure occurred due to a missing or
                // invalid user name or password
                isAuthenticationFail = true;
            }
            // Connection failed for reason other than a missing password. Check if this isn't a
            // reconnection attempt (errors are suppressed here if a reconnection attempt fails)
            else if (!isReconnect)
            {
                // Inform the user that the database connection failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot connect to "
                                      + (activeDatabase.equals(DEFAULT_DATABASE) ? "server"
                                                                                 : "project database '"
                                      + getServerAndDatabase(databaseName)
                                      + "'")
                                      + " as user '"
                                      + activeUser
                                      + "'; cause '"

                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot connect to "
                                      + (activeDatabase.equals(DEFAULT_DATABASE) ? "server"
                                                                                 : "project '</b>"
                                      + projectName
                                      + "<b>'"));
            }

            errorFlag = true;
        }
        catch (CCDDException ce)
        {
            errorFlag = true;
        }

        // Check if a connection is established. Don't log the version information when
        // reconnecting
        if (!errorFlag && !isReconnect)
        {
            // Log the PostgreSQL and JDBC versions
            eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                              new StringBuilder("PostgreSQL: ").append(getDatabaseVersion())
                                                               .append("  *** JDBC: ")
                                                               .append(getJDBCVersion()));
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Reconnect to the active database
     *
     * @return true if the connection attempt failed
     *********************************************************************************************/
    protected boolean reconnectToDatabase()
    {
        return connectToDatabase(activeProject, activeDatabase, true);
    }

    /**********************************************************************************************
     * Open a database. Create the database functions
     *
     * @param projectName Name of the project to open
     *
     * @return true if an error occurred opening the database; false if the database successfully
     *         opened
     *********************************************************************************************/
    protected boolean openDatabase(final String projectName)
    {
        return openDatabase(projectName, true);
    }

    /**********************************************************************************************
     * Open a database using the current host, port, and SSL settings
     *
     * @param projectName         Name of the project to open
     *
     * @param createFunctions     True to create the database functions; false if reopening a
     *                            database (so the functions already exist)
     *
     * @return true if an error occurred opening the database; false if the database successfully
     *         opened
     *********************************************************************************************/
    protected boolean openDatabase(String projectName, boolean createFunctions)
    {
        return openDatabase(projectName, serverHost, serverPort, isSSL, createFunctions);
    }

    /**********************************************************************************************
     * Open a database using the current host, port, and SSL settings
     *
     * @param projectName         Name of the project to open
     *
     * @param createFunctions     True to create the database functions; false if reopening a
     *                            database (so the functions already exist)
     *
     * @param addDefaultDataTypes Should the default data type be added to the database? This is
     *                            normally false only when restoring a database from JSON or CSV
     *
     * @return true if an error occurred opening the database; false if the database successfully
     *         opened
     *********************************************************************************************/
    protected boolean openDatabase(String projectName,
                                   boolean createFunctions,
                                   boolean addDefaultDataTypes)
    {
        this.addDefaultDataTypes = addDefaultDataTypes;
        return openDatabase(projectName, serverHost, serverPort, isSSL, createFunctions);
    }

    /**********************************************************************************************
     * Open a database using the project name, host, port, and SSL settings provided
     *
     * @param projectName     Name of the project to open
     *
     * @param serverHost      PostgreSQL server host
     *
     * @param serverPort      PostgreSQL server port
     *
     * @param isSSL           True if an SSL connection is enabled
     *
     * @param createFunctions True to create the database functions; false if reopening a database
     *                        (so the functions already exist)
     *
     * @return true if an error occurred opening the database; false if the database successfully
     *         opened
     *********************************************************************************************/
    protected boolean openDatabase(String projectName,
                                   String serverHost,
                                   String serverPort,
                                   boolean isSSL,
                                   boolean createFunctions)
    {
        boolean errorFlag = false;

        // Check if closing the existing connection, if present, was successful
        if (closeDatabase())
        {
            // Convert the project name to its database equivalent
            String databaseName = convertProjectNameToDatabase(projectName);

            // Check if the required server inputs are available. A password may be needed; if so,
            // then it is requested after the connection attempt fails
            if (serverHost != null
                && activeUser != null
                && !serverHost.isEmpty()
                && !activeUser.isEmpty())
            {
                try
                {
                    // Store the host, port, and SSL settings as the new defaults
                    this.serverHost = serverHost;
                    this.serverPort = serverPort;
                    this.isSSL = isSSL;

                    // Register the JDBC driver
                    Class.forName(DATABASE_DRIVER);

                    // Check if the attempt to connect to the database fails
                    if (connectToDatabase(projectName, databaseName, false))
                    {
                        throw new CCDDException();
                    }

                    // Check that the connection is to a project database and not just the server
                    // (default database)
                    if (isDatabaseConnected())
                    {
                        // Create a temporary table for storing the results returned by the
                        // database functions
                        createTemporaryTable();

                        // Check if the database functions should be created; if so create the
                        // internal tables and database functions, and check if an error occurs
                        // creating them
                        if (createFunctions && createTablesAndFunctions())
                        {
                            throw new CCDDException();
                        }

                        // Perform any patches to update this project database to the latest schema
                        // that must be implemented prior to initializing the handler classes
                        CcddPatchHandler patchHandler = new CcddPatchHandler(ccddMain);
                        patchHandler.applyPatches(true);

                        // Create and set the project-specific handlers that must be created prior
                        // to creating the project-specific PostgreSQL functions
                        ccddMain.setPreFunctionDbSpecificHandlers();

                        // Check if the database functions should be created; if so create the
                        // database functions that collect structure table members and
                        // structure-defining column data, and check if an error occurred creating
                        // them
                        if (createFunctions && createStructureColumnFunctions())
                        {
                            throw new CCDDException();
                        }

                        // Perform any patches to update this project database to the latest schema
                        // that must be implemented after initializing the handler classes
                        patchHandler.applyPatches(false);

                        // Create and set the project-specific handlers that must be created after
                        // creating the project-specific PostgreSQL functions
                        ccddMain.setPostFunctionDbSpecificHandlers();

                        // Set the user's access level
                        setAccessLevel();

                        // Check if the GUI is visible
                        if (!ccddMain.isGUIHidden())
                        {
                            // Lock the database. If the application is started with the GUI hidden
                            // (for command line script execution or as a web server) then the
                            // project database is left unlocked
                            setDatabaseLockStatus(activeProject, true);

                            // Update the pre-loaded table members and build the table tree. This
                            // is done in the background as soon as a database is opened so that
                            // when a user performs an action that loads the table tree they do not
                            // have to wait for it to be built. Building the table tree is done
                            // after pre-loading so that the pre-loaded data is used
                            ccddMain.getDbTableCommandHandler().updatePreLoadedTableMembers();
                            ccddMain.buildGroupTableTreeHandler();
                            ccddMain.buildTypeTableTreeHandler();

                            // Update the recently opened projects list and store it in the program
                            // preferences, then update the command menu items
                            CcddUtilities.updateRememberedItemList(projectName,
                                                                   ccddMain.getRecentProjectNames(),
                                                                   ModifiableSizeInfo.NUM_REMEMBERED_PROJECTS.getSize());
                            ccddMain.getProgPrefs().put(PROJECT_STRINGS,
                                                        CcddUtilities.getRememberedItemListAsString(ccddMain.getRecentProjectNames()));
                            ccddMain.updateRecentProjectsMenu();
                        }

                        // Initialize the root structures. This is done here so that the pre-loaded
                        // default table tree can be used
                        ccddMain.getDbTableCommandHandler().initRootStructures();
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag indicating the connection attempt failed
                    errorFlag = true;

                    // Check that the database isn't the default database (server)
                    if (!databaseName.equals(DEFAULT_DATABASE))
                    {
                        // Close the database and attempt to connect to the default database
                        closeDatabase();
                        errorFlag = connectToServer();
                    }
                }
                catch (
                        LinkageError | ClassNotFoundException le)
                {
                    // Inform the user that registering the database driver failed
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
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, ccddMain.getMainFrame());
                    errorFlag = true;
                }

                // Check that no error occurred connecting to the database
                if (!errorFlag)
                {
                    try
                    {
                        // Store the project name, user name, server host, server port, and SSL
                        // state in the program preferences backing store
                        ccddMain.getProgPrefs().put(DATABASE, activeProject);
                        ccddMain.getProgPrefs().put(USER, activeUser);
                        ccddMain.getProgPrefs().put(POSTGRESQL_SERVER_HOST, serverHost);
                        ccddMain.getProgPrefs().put(POSTGRESQL_SERVER_PORT, serverPort);
                        ccddMain.getProgPrefs().putBoolean(POSTGRESQL_SERVER_SSL, isSSL);
                    }
                    catch (Exception e)
                    {
                        // Inform the user that there the program preferences can't be stored
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot store program preference values; cause '</b>"
                                                                  + e.getMessage()
                                                                  + "<b>'",
                                                                  "File Warning",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Check if an automatic backup was scheduled via the command line argument
                    boolean isBackupOperation = !backupFileName.isEmpty();
                    if (isBackupOperation)
                    {
                        // Check if the backup file name is missing the correct extension
                        if (!backupFileName.endsWith(FileExtension.DBU.getExtension()))
                        {
                            // Append the backup file extension to the file name
                            backupFileName += FileExtension.DBU.getExtension();
                        }

                        // Backup the database
                        backupDatabaseInBackground(activeProject, new FileEnvVar(backupFileName));

                        // Reset the backup file name to prevent another automatic backup
                        backupFileName = "";
                    }

                    // Check that successful connection was made to a project database and not just
                    // the server (default database)
                    if (isDatabaseConnected() && addDefaultDataTypes)
                    {
                        // Parse any command line commands that require a project database to be
                        // open
                        ccddMain.parseDbSpecificCommandLineCommands();
                    }
                }
            }
            // A required parameter is missing
            else
            {
                // Inform the user that one or more server connection parameters are missing
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

    /**********************************************************************************************
     * Open a database using the current host, port, and SSL settings provided. This command is
     * executed in a separate thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     *
     * @param projectName Name of the project to open
     *********************************************************************************************/
    protected void openDatabaseInBackground(final String projectName)
    {
        // Set the flag to indicate this in the first connection attempt for this server and
        // attempt to open the database
        isFirstConnectionAttempt = true;
        openDatabaseInBackground(projectName, serverHost, serverPort, isSSL);
    }

    /**********************************************************************************************
     * Open a database using the host, port, and SSL settings provided. This command is executed in
     * a separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database command completes execution
     *
     * @param projectName Name of the project to open
     *
     * @param serverHost  PostgreSQL server host
     *
     * @param serverPort  PostgreSQL server port
     *
     * @param isSSL       True if an SSL connection is enabled
     *********************************************************************************************/
    protected void openDatabaseInBackground(final String projectName,
                                            final String serverHost,
                                            final String serverPort,
                                            final boolean isSSL)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            private boolean errorFlag = false;

            /**************************************************************************************
             * Database open command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Open the new database
                errorFlag = openDatabase(projectName, serverHost, serverPort, isSSL, true);
            }

            /**************************************************************************************
             * Database open command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if an error occurred opening the database
                if (errorFlag)
                {
                    // Check if an authentication error occurred
                    if (isAuthenticationFail)
                    {
                        // Get the user and password. Indicate that the user name and/or password
                        // is invalid in the login dialog, unless this is the first connection
                        // attempt for this server and no password is supplied (so as to prevent
                        // the message before the user has a chance to enter a valid combination)
                        new CcddServerPropertyDialog(ccddMain,
                                                     !projectName.equals(DEFAULT_DATABASE),
                                                     ServerPropertyDialogType.LOGIN,
                                                     (!isFirstConnectionAttempt
                                                      || !activePassword.isEmpty() ? "Invalid user name or password"
                                                                                   : null));
                        isFirstConnectionAttempt = false;
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Rename a project and/or add/update the database description
     *
     * @param oldProject  Current name of the project
     *
     * @param newProject  New name of the project
     *
     * @param description Database description
     *********************************************************************************************/
    protected void renameDatabase(final String oldProject,
                                  final String newProject,
                                  final String description)
    {

        String currentDatabase = activeDatabase;

        // Convert the project names to their database equivalents
        String oldDatabase = convertProjectNameToDatabase(oldProject);
        String newDatabase = convertProjectNameToDatabase(newProject);

        try
        {
            // Get the database's administrator(s)
            String administrator = getDatabaseAdmins(oldDatabase);

            // Check if the old and new database names are identical; this implies only the project
            // name and/or description changed
            if (oldDatabase.equals(newDatabase))
            {
                // Update the database's description
                dbCommand.executeDbUpdate(new StringBuilder(buildDatabaseCommentCommandAndUpdateInternalTable(newProject,
                                                                                                              administrator,
                                                                                                              false,
                                                                                                              description)),
                                         ccddMain.getMainFrame());
            }
            // Check if the currently open database is not the one being renamed; otherwise, check
            // if the target database can be closed and the default opened (required in order to
            // make changes to the current database)
            else if (!oldDatabase.equals(currentDatabase) || !openDatabase(DEFAULT_DATABASE))
            {
                // Rename the database to the new name and update the description
                dbCommand.executeDbUpdate(new StringBuilder("ALTER DATABASE ").append(getQuotedName(oldDatabase))
                                                                              .append(" RENAME TO ")
                                                                              .append(getQuotedName(newDatabase))
                                                                              .append("; ")
                                                                              .append(buildDatabaseCommentCommandAndUpdateInternalTable(newProject,
                                                                                                                                        administrator,
                                                                                                                                        false,
                                                                                                                                        description)),
                                          ccddMain.getMainFrame());

                // Check if the currently open database is the one being renamed
                if (oldDatabase.equals(currentDatabase))
                {
                    // Close the default database and reopen the target
                    openDatabase(newProject, false);
                }

                // Log that the renaming the database succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder("Project database '").append(oldProject)
                                                                         .append("' renamed to '")
                                                                         .append(newProject)
                                                                         .append("'"));
            }
        }
        catch (SQLException se)
        {
            // Inform the user that the database cannot be renamed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot rename project database '"
                                  + getServerAndDatabase(oldDatabase)
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot rename project '</b>"
                                  + oldProject
                                  + "<b>'");

            // Check if the currently open database is the one that was attempted to be renamed
            if (oldProject.equals(currentDatabase))
            {
                // Close the default database and reopen the target
                openDatabase(currentDatabase, false);
            }
        }
    }

    /**********************************************************************************************
     * Rename a project and/or add/update the database description. This command is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database command completes execution
     *
     * @param oldProject  Current name of the project
     *
     * @param newProject  New name of the project
     *
     * @param description Database description
     *********************************************************************************************/
    protected void renameDatabaseInBackground(final String oldProject,
                                              final String newProject,
                                              final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Rename database command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                renameDatabase(oldProject, newProject, description);
            }
        });
    }

    /**********************************************************************************************
     * Copy a database. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param targetProject Name of the project to copy
     *
     * @param copyProject   Name of the project copy
     *
     * @param description   Database description
     *********************************************************************************************/
    protected void copyDatabaseInBackground(final String targetProject,
                                            final String copyProject,
                                            final String description)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Copy database command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                String currentDatabase = activeDatabase;

                // Convert the project names to their database equivalents
                String targetDatabase = convertProjectNameToDatabase(targetProject);
                String copyDatabase = convertProjectNameToDatabase(copyProject);

                try
                {
                    // Check if the currently open database is not the one being copied; otherwise,
                    // check if the target database can be closed and the default opened (required
                    // in order to make changes to the current database)
                    if (!targetDatabase.equals(currentDatabase) || !openDatabase(DEFAULT_DATABASE))
                    {
                        // Get the administrator(s) and owner of the database being copied; the
                        // copy will have the same administrator(s) and owner
                        String administrator = getDatabaseAdmins(targetDatabase);
                        String ownerName = targetDatabase.equals(currentDatabase) ? activeOwner
                                                                                  : queryDatabaseOwner(targetDatabase,
                                                                                                       ccddMain.getMainFrame())[0];

                        // Enable auto-commit for database changes
                        connection.setAutoCommit(true);

                        // Copy the database and transfer the comment
                        dbCommand.executeDbCommand(new StringBuilder("CREATE DATABASE ").append(getQuotedName(copyDatabase))
                                                                                        .append(" WITH TEMPLATE ")
                                                                                        .append(getQuotedName(targetDatabase))
                                                                                        .append("; ")
                                                                                        .append(buildDatabaseCommentCommandAndUpdateInternalTable(copyProject,
                                                                                                                                                  administrator,
                                                                                                                                                  false,
                                                                                                                                                  description))
                                                                                        .append(buildOwnerCommand(ownerName,
                                                                                                                  DatabaseObject.DATABASE,
                                                                                                                  copyDatabase)),
                                                   ccddMain.getMainFrame());

                        // Check if the currently open database is the one being copied
                        if (targetDatabase.equals(currentDatabase))
                        {
                            // Close the default database and reopen the database that was
                            // originally open
                            openDatabase(targetProject, false);
                        }

                        // Log that the copying the database succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          new StringBuilder("Project '").append(targetProject)
                                                                        .append("' copied"));
                    }
                }
                catch (SQLException se)
                {
                    // Inform the user that the database cannot be copied
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot copy project database '"
                                          + getServerAndDatabase(targetDatabase)
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                          "<html><b>Cannot copy project '</b>"
                                          + targetProject
                                          + "<b>'");

                    // Check if the currently open database is the one that was attempted to be
                    // copied
                    if (targetDatabase.equals(currentDatabase))
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

    /**********************************************************************************************
     * Delete the database
     *
     * @param projectName Name of the project to delete
     *
     * @return false if the specified database is successfully deleted
     *********************************************************************************************/
    protected boolean deleteDatabase(final String projectName)
    {
        boolean errorFlag = false;

        // Convert the project name to its database equivalent
        String databaseName = convertProjectNameToDatabase(projectName);

        try
        {
            // Enable auto-commit for database changes
            connection.setAutoCommit(true);

            // Delete the database
            dbCommand.executeDbUpdate(new StringBuilder("DROP DATABASE ").append(getQuotedName(databaseName))
                                                                         .append(";"),
                                                                         ccddMain.getMainFrame());

            // Log that the database deletion succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder("Project '").append(projectName)
                                                                         .append("' deleted"));
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
                                  "<html><b>Cannot delete project '</b>"
                                  + projectName
                                  + "<b>'");
            errorFlag = true;
        }
        finally
        {
            // Disable auto-commit for database changes
            resetAutoCommit();
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Delete the project database. This command is executed in a separate thread since it can take
     * a noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param projectName Name of the project to delete
     *********************************************************************************************/
    protected void deleteDatabaseInBackground(final String projectName)
    {
        // Have the user confirm deleting the selected project's database
        if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Delete project </b>"
                                                      + projectName
                                                      + "<b>?<br><i>Warning: This action cannot be undone!",
                                                      "Delete Project",
                                                      JOptionPane.QUESTION_MESSAGE,
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Execute the command in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                /**********************************************************************************
                 * Database delete command
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    // Delete the project's database
                    deleteDatabase(projectName);
                }
            });
        }
    }

    /**********************************************************************************************
     * Close the currently open database
     *
     * @return true if no database is connected
     *********************************************************************************************/
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
                    setDatabaseLockStatus(activeProject, false);

                    // Check if the web server is enabled
                    if (ccddMain.isWebServer())
                    {
                        // Stop the web server
                        ccddMain.getWebServer().stopServer();
                    }
                }

                // Close the database
                connection.close();

                // Inform the user that closing the database succeeded and update the connection
                // status
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder((activeDatabase.equals(DEFAULT_DATABASE) ? "Server connection"
                                                                                             : "Project database '"
                                                                                               + activeDatabase
                                                                                               + "'")).append(" closed"));
                connectionStatus = NO_CONNECTION;
            }
            catch (SQLException se)
            {
                // Inform the user that the database failed to close
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot close "
                                      + (activeDatabase.equals(DEFAULT_DATABASE) ? "server connection"
                                                                                 : "project database '"
                                      + getServerAndDatabase(activeDatabase)
                                      + "'")
                                      + "; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot close "
                                      + (activeDatabase.equals(DEFAULT_DATABASE) ? "server connection"
                                                                                 : "project '</b>"
                                      + activeProject
                                      + "<b>'"));
            }
        }

        return connectionStatus == NO_CONNECTION;
    }

    /**********************************************************************************************
     * Build the portion of the backup and restore commands that specifies the user name, server
     * host, and server port
     *
     * @return Portion of the backup and restore commands that specifies the user name, server
     *         host, and server port
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Backup a project database. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param projectName Project name
     *
     * @param backupFile  File to which to backup the database
     *********************************************************************************************/
    protected void backupDatabaseInBackground(final String projectName, final FileEnvVar backupFile)
    {

        // Try Acquire the semaphore here outside of the worker thread. This is because the program
        // may exit before the thread actually begins to run
        try
        {
            ImmutablePair<Semaphore, Integer> pair = ccddMain.getSemMap().get(CcddMain.BACKUP_KEY);

            if (!pair.left.tryAcquire(pair.right, TimeUnit.SECONDS))
            {
                ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                           "Backup Operation Failed: Cause - Failed to "
                                                           + "acquire the backup task semaphore",
                                                           "<html><b>Failed to acquire the backup task semaphore</b>");
                return;
            }
        }
        catch (InterruptedException e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Backup database command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Perform the backup operation
                backupDatabase(projectName, backupFile);

                // Release the semaphore indicating that this thread's work is finished
                ccddMain.getSemMap().get(CcddMain.BACKUP_KEY).left.release();
            }
        });
    }

    /**********************************************************************************************
     * Rename a project database, back it up and revert the rename. This can be used during a
     * backup operation to change the name of the backed up database to match the backup file name
     * (for instance).
     *
     * This will perform the following sequence of operations: 1: Rename an existing database to
     * the new name 2: Backup the newly renamed database (database name and backup file name will
     * match now) 3: Rename the database to its original name
     *
     * @param projectName    Current project name
     *
     * @param newProjectName New project name
     *
     * @param backupFile     File to which to backup the database
     *********************************************************************************************/
    protected void backupAndRenameDatabase(final String projectName,
                                           final String newProjectName,
                                           final FileEnvVar backupFile)
    {
        // Get the description (and pass in the database name to get the correct value)
        String description = getDatabaseDescription(convertProjectNameToDatabase(projectName));

        // If the description is incorrect, then throw an error in the log and fail
        if (description == null)
        {
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Database description is not valid '",
                                  "<html><b>Cannot rename project '</b>"
                                  + projectName
                                  + "<b>'");
            return;
        }

        // Rename the database to get the new name
        renameDatabase(projectName, newProjectName, description);

        // Perform the backup operation
        backupDatabase(newProjectName, backupFile);

        // Rename the database to its original name
        renameDatabase(newProjectName, projectName, description);

    }

    /**********************************************************************************************
     * Backup a project database. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param projectName    Current project name
     *
     * @param newProjectName New project name
     *
     * @param backupFile     File to which to backup the database
     *********************************************************************************************/
    protected void backupAndRenameDatabaseInBackground(final String projectName,
                                                       final String newProjectName,
                                                       final FileEnvVar backupFile)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Backup database command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // This will be coming from the GUI which will require that the only database that
                // can be backed up is the open one so close it first
                if (ccddMain.ignoreUncommittedChanges("Close Project",
                                                      "Discard changes?",
                                                      true,
                                                      null,
                                                      null))
                {
                    openDatabase(DEFAULT_DATABASE);
                }
                else
                {
                    // The user has chosen not to close the database so exit
                    return;
                }

                // Perform the sequence of operations here
                backupAndRenameDatabase(projectName, newProjectName, backupFile);

                // Open the original database again
                openDatabase(projectName);

            }
        });
    }

    /**********************************************************************************************
     * Backup a project database
     *
     * @param projectName Project name
     *
     * @param backupFile  File to which to backup the database
     *
     * @return true if an the database could not be backed up
     *********************************************************************************************/
    protected boolean backupDatabase(String projectName, FileEnvVar backupFile)
    {
        String errorType = "";

        // Convert the project name to its database equivalent
        String databaseName = convertProjectNameToDatabase(projectName);

        // Build the command to backup the database
        String command = "pg_dump "
                         + getUserHostAndPort()
                         + "--no-password --no-owner --file ";

        // Get the number of command line arguments. Since the backup file name may have spaces the
        // argument count must be made prior to appending it. The argument count is adjusted for
        // the addition of the database name as well
        int numArgs = command.split(" ").length + 2;

        // Append the backup file and database names. Surround the file name with quotes in case it
        // contains a space
        command += "\"" + backupFile.getAbsolutePath() + "\" " + databaseName;

        // Log the database backup command
        eventLog.logEvent(COMMAND_MSG, new StringBuilder(command));

        // Execute the backup command
        errorType = executeProcess(command, numArgs);

        // Check if no error occurred
        if (errorType.isEmpty())
        {
            // Store the backup file path in the program preferences backing store
            CcddFileIOHandler.storePath(ccddMain,
                                        backupFile.getAbsolutePathWithEnvVars(),
                                        true,
                                        ModifiablePathInfo.DATABASE_BACKUP_PATH);

            // Log that backing up the database succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder("Project '").append(projectName).append("' backed up"));
        }

        // An error occurred backing up the database
        else
        {
            // Inform the user that the database could not be backed up
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Project '"
                                  + projectName
                                  + "' backup failed;  cause '"
                                  + errorType
                                  + "'",
                                  "<html><b>Project '</b>"
                                  + projectName
                                  + "<b>' backup failed");
        }

        return !errorType.isEmpty();
    }

    /**********************************************************************************************
     * Restore a database. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param projectName       Name of the project to restore (preserving case and special
     *                          characters)
     *
     * @param ownerName         Name of the role or user that owns the database and its objects
     *
     * @param administrator     Project administrator name(s) (comma-separated)
     *
     * @param description       Project description
     *
     * @param restoreFile       File to restore the database from
     *
     * @param overwriteExisting True to overwrite the existing project database; false to create a
     *                          new database ion which to restore the backup file contents
     *********************************************************************************************/
    protected void restoreDatabaseInBackground(final String projectName,
                                               final String ownerName,
                                               final String administrator,
                                               final String description,
                                               final File restoreFile,
                                               final boolean overwriteExisting)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Restore database command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                restoreDatabase(projectName,
                                ownerName,
                                administrator,
                                description,
                                restoreFile,
                                overwriteExisting);
            }
        });
    }

    /**********************************************************************************************
     * Restore a database
     *
     * @param projectName       Name of the project to restore (preserving case and special
     *                          characters)
     *
     * @param ownerName         Name of the role or user that owns the database and its objects
     *
     * @param administrator     Project administrator name(s) (comma-separated)
     *
     * @param description       Project description
     *
     * @param restoreFile       File to restore the database from
     *
     * @param overwriteExisting True to overwrite the existing project database; false to create a
     *                          new database ion which to restore the backup file contents
     *********************************************************************************************/
    protected void restoreDatabase(String projectName,
                                   String ownerName,
                                   String administrator,
                                   String description,
                                   File restoreFile,
                                   boolean overwriteExisting)
    {
        String errorType = "";

        // Create the names for the restored project and database
        String restoreProjectName = projectName + (overwriteExisting ? "" : "_restored");
        String restoreDatabaseName = convertProjectNameToDatabase(projectName)
                                     + (overwriteExisting ? "" : "_restored");

        // Get the list of available databases
        String[] databases = queryDatabaseList(ccddMain.getMainFrame());

        boolean isMatch = !overwriteExisting;
        int seqNum = 0;
        String seqName = "";

        // Continue to check for name matches until the restored database name is unique
        while (isMatch)
        {
            isMatch = false;

            // Step through each existing database name
            for (String name : databases)
            {
                // Check if the name of the restored database name matches that of another database
                if ((restoreDatabaseName + seqName).equals(name.split(DATABASE_COMMENT_SEPARATOR, 2)[0]))
                {
                    // Increment the sequence number and set the flag to indicate a match was
                    // found. Repeat the process in case this amended name is also a match
                    seqNum++;
                    seqName = "_" + seqNum;
                    isMatch = true;
                    break;
                }
            }
        }

        // Check if a sequence number is needed to differentiate the database name
        if (!seqName.isEmpty())
        {
            // Add the sequence number to the names
            restoreProjectName += seqName;
            restoreDatabaseName += seqName;
        }

        // Check if an existing database is being overwritten; if not, create a new database to
        // which to restore the data and check that it is created successfully
        if (overwriteExisting || createDatabase(restoreProjectName,
                                                ownerName,
                                                administrator,
                                                description))
        {
            // Build the command to restore the database
            String command = "psql " + getUserHostAndPort() + "-d " + restoreDatabaseName
                             + " -v ON_ERROR_STOP=true -f ";

            // Get the number of command line arguments. Since the restore file name may have
            // spaces the argument count must be made prior to appending it
            int numArgs = command.split(" ").length + 1;

            // Append the file name, bounded by quotes in case it contains a space
            command += "\"" + restoreFile.getAbsolutePath() + "\"";

            // Log the database restore command
            eventLog.logEvent(COMMAND_MSG, new StringBuilder(command));

            // Execute the restore command
            errorType = executeProcess(command, numArgs);

            // If the overwriteExisting is true, then the database won't be created and the
            // internal table won't have been added already. This probably means that the database
            // has already been created and is probably coming from the command line functionality.
            // So, do it here
            if (overwriteExisting)
            {
                try
                {
                    dbCommand.executeDbUpdate(new StringBuilder(buildDatabaseCommentCommandAndUpdateInternalTable(projectName,
                                                                                                                  administrator,
                                                                                                                  false,
                                                                                                                  description)),
                                              ccddMain.getMainFrame());
                    // Log that the copying the database succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Updated the comment for Project '").append(projectName));
                }
                catch (SQLException se)
                {
                    // Inform the user that setting the database comment failed
                    eventLog.logFailEvent(ccddMain.getMainFrame(),
                                          "Cannot set comment for project database '"
                                          + getServerAndDatabase(projectName)
                                          + "'; cause '"
                                          + se.getMessage() + "'",
                                          "<html><b>Cannot set comment for project '</b>"
                                          + projectName
                                          + "<b>'");
                }
            }

            // Check if no error occurred
            if (errorType.isEmpty())
            {
                // Log that the database restoration succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder("Project '").append(projectName)
                                                                .append("' restored as '")
                                                                .append(restoreProjectName)
                                                                .append("'"));
            }
        }
        // Database restoration failed
        else
        {
            // Set the error type message
            errorType = "cannot create restore database";
        }

        // Check if an error occurred restoring the database
        if (errorType == null || !errorType.isEmpty())
        {
            // Inform the user that the database could not be restored
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Project '"
                                  + projectName
                                  + "' restore failed; cause '"
                                  + errorType
                                  + "'",
                                  "<html><b>Project '</b>"
                                  + projectName
                                  + "<b>' restore failed");
        }
    }

    /**********************************************************************************************
     * Execute an operating system command
     *
     * @param command Command to process
     *
     * @param numArgs Number of arguments in the command
     *
     * @return Error message text if an error occurs; empty string is the command completed
     *         successfully
     *********************************************************************************************/
    protected String executeProcess(String command, int numArgs)
    {
        String errorType = "";

        try
        {
            // Create a temporary file to contain the user's PostgreSQL password
            File file = File.createTempFile("pgpass", "conf");

            try
            {
                // Set the password file POSIX permissions as readable and writable by the user
                // only
                Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(file.toPath(), perms);
            }
            catch (Exception e)
            {
                // The operating system doesn't allow POSIX permissions; ignore. Linux, for
                // example, uses the POSIX permissions. The Windows OS doesn't, but stores the
                // password file in the user's folder path, so password security is still
                // maintained
            }

            // Write the user and password information to the password file
            Writer writer = new FileWriter(file);
            writer.write(String.format("*:*:*:%s:%s\n", activeUser, activePassword));
            writer.close();

            // Create the process builder to execute the command
            ProcessBuilder builder = new ProcessBuilder(CcddUtilities.splitAndRemoveQuotes(command,
                                                                                           " ",
                                                                                           numArgs,
                                                                                           true));

            // Assign the password file created above to the process builder
            builder.environment().put("PGPASSFILE", file.getAbsolutePath());

            // Execute the command
            Process process = builder.start();

            // Read the output from stdout and stderr until no more exists. This prevents filling
            // up the output buffer, which would cause the process to block indefinitely
            StreamConsumer outConsume = new StreamConsumer(process.getInputStream(), false);
            StreamConsumer errConsume = new StreamConsumer(process.getErrorStream(), true);
            errConsume.start();
            outConsume.start();

            // Wait for the command to complete and check if it failed to successfully complete
            if (process.waitFor() != 0)
            {
                errorType = errConsume.getOutput();
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
