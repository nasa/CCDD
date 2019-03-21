/**
 * CFS Command and Data Dictionary web data access handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.TRUE_OR_FALSE;
import static CCDD.CcddConstants.TYPE_COMMAND;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.OrderedJSONObject;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.CopyTableEntry;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.JSONTags;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.SearchResultsColumnInfo;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary web data access handler class
 *************************************************************************************************/
public class CcddWebDataAccessHandler extends AbstractHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddRateParameterHandler rateHandler;
    private CcddVariableHandler variableHandler;
    private CcddLinkHandler linkHandler;
    private TableTreeType tableTreeType;
    private CcddJSONHandler jsonHandler;
    private CcddFieldHandler fieldHandler;
    private CcddGroupHandler groupHandler;

    // Flag that indicates if the macro name(s) in the table cells is to be replaced by the
    // corresponding macro values
    private boolean isReplaceMacro;

    // Flag that indicates if the variable paths are to be appended to structure table data
    private boolean isIncludePath;

    // Flag that indicates if the table tree path list should only include table names to a
    // specified level in the tree. This is used to get the root tables
    private boolean isMaxLevel;

    /**********************************************************************************************
     * Web data access handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddWebDataAccessHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();
    }

    /**********************************************************************************************
     * Set the reference to the rate parameter, data field, and JSON handler classes
     *********************************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        variableHandler = ccddMain.getVariableHandler();
        fieldHandler = ccddMain.getFieldHandler();
        groupHandler = new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame());
        jsonHandler = new CcddJSONHandler(ccddMain, groupHandler, ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Handle a web-based request
     *********************************************************************************************/
    @Override
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException
    {
        // Indicate that the request has been handled
        baseRequest.setHandled(true);

        // Get the request
        String query = request.getQueryString();

        // Check if the request exists
        if (query != null)
        {
            // Decode the query string into UTF-8
            query = URLDecoder.decode(query, "UTF-8");
        }
        // No query is provided
        else
        {
            // Set the query to a blank
            query = "";
        }

        // Process the request and get the information encoded as a JSON string. The leading '/' is
        // removed from the request path
        String jsonResponse = getQueryResults(target.replaceFirst("^/", "").trim(), query);

        // Check if the specified content was loaded successfully
        if (jsonResponse != null)
        {
            // Set the flag indicating the response is valid
            response.setStatus(HttpServletResponse.SC_OK);
        }
        // The request failed
        else
        {
            // Set the flag indicating the response is invalid and return an empty string
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            jsonResponse = "";
        }

        try
        {
            // Set the response type and length
            response.setContentType("text/json");
            response.setContentLength(jsonResponse.length());

            // Return the response to the requester
            response.getWriter().write(jsonResponse);
            response.flushBuffer();
        }
        catch (IOException ioe)
        {
            // Inform the user that processing the web server request failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Web Server Error",
                                  "Cannot respond to web server request; cause '"
                                                      + ioe.getMessage()
                                                      + "'",
                                  "<html><b>Cannot respond to web server request");
        }
    }

    /**********************************************************************************************
     * Extract the parts from the supplied text string, separating the string at the specified
     * separation character(s) and removing any leading and trailing white space characters from
     * each part
     *
     * @param text
     *            text string to separate
     *
     * @param separator
     *            separation character(s). This is padded with a check for white space characters
     *            in order to remove them
     *
     * @param limit
     *            maximum number of parts to separate the text into. This is the number of parts
     *            returned, with any missing parts returned as blanks
     *
     * @param removeQuotes
     *            true to remove excess double quotes from the individual array members
     *
     * @return Array containing the specified number of parts of the text string. A part is
     *         returned as blank if the supplied text does not contain the number of parts
     *         specified by the limit
     *********************************************************************************************/
    private String[] getParts(String text,
                              String separator,
                              int limit,
                              boolean removeQuotes)
    {
        // Extract the parts of the input string using the supplied separator and limit
        String[] splitText = CcddUtilities.splitAndRemoveQuotes(text.trim(),
                                                                "\\s*" + separator + "\\s*",
                                                                limit,
                                                                removeQuotes);

        // Check if a the number of parts is less than the specified limit
        if (splitText.length < limit)
        {
            // Fill out the array to the specified limit with blank columns
            splitText = CcddUtilities.appendArrayColumns(splitText, limit - splitText.length);
        }

        return splitText;
    }

    /**********************************************************************************************
     * Get the separator characters used to convert an application variable path to a user-defined
     * variable path
     *
     * @param parameters
     *            comma-separated string containing the variable path separator character(s),
     *            show/hide data types flag ('true' or 'false'), and data type/variable name
     *            separator character(s)
     *
     * @return String array containing the variable path separator character(s), show/hide data
     *         types flag ('true' or 'false'), and data type/variable name separator character(s);
     *         the default separators if the input parameters string is blank. An exception is
     *         thrown if the parameter string format is invalid
     *
     * @throws CCDDException
     *             If an invalid character is detected in the separator field(s)
     *********************************************************************************************/
    private String[] getVariablePathSeparators(String parameters) throws CCDDException
    {
        String[] separators = null;

        // Check if the parameter string is blank
        if (parameters.isEmpty())
        {
            // Use the default separators
            separators = new String[] {"_", "false", "_"};
        }
        // The parameters string isn't blank
        else
        {
            // Separate the input parameters
            separators = getParts(parameters, ",", 3, true);

            // Check if the flag to hide the data type isn't present
            if (separators[1].isEmpty())
            {
                // Default to showing the data type
                separators[1] = "false";
            }

            // Check if the separator characters and data type flag values are valid
            if (separators[0].matches(".*[\\[\\]].*")
                || !separators[1].matches(TRUE_OR_FALSE)
                || (separators[1].matches("(?i:false)")
                    && separators[2].matches(".*[\\[\\]].*")))
            {
                throw new CCDDException("Invalid character(s) in separator field(s)");
            }
        }

        return separators;
    }

    /**********************************************************************************************
     * Process the web query and return the results encoded as a JSON string. The query is in the
     * form [server]:[port]/[component]?[attribute][=name]
     *
     * @param component
     *            component for which to request data
     *
     * @param item
     *            item in the component
     *
     * @return Query results encoded as a JSON string
     *********************************************************************************************/
    private String getQueryResults(String component, String item)
    {
        String response = null;

        // Log the web server request. Mask the password for authentication requests (match an
        // expanded range in case the user mistypes the command)
        eventLog.logEvent(EventLogMessageType.SERVER_MSG,
                          "Request component '"
                                                          + component
                                                          + "' item '"
                                                          + (component.contains("authentic")
                                                                                             ? item.replaceFirst("=.*(?:;|$)",
                                                                                                                 "=*****")
                                                                                             : item)
                                                          + "'");

        try
        {
            String[] separators = null;

            // Separate the component/attribute/name from the other flag(s) (if present)
            String[] itemAndOther = getParts(item, ";", 2, false);

            // Set the default macro and variable path flag values
            isReplaceMacro = true;
            isIncludePath = false;

            // Step through the macro and/or variable path flags, if present
            for (String macroAndPath : getParts(itemAndOther[1], ";", 2, false))
            {
                // Split the macro/path option from any parameter values
                String[] parts = getParts(macroAndPath, ",", 2, false);

                switch (parts[0].toLowerCase())
                {
                    // Display macro names command
                    case "macro":
                    case "macros":
                        // Set the flag so that the macro names (in place of macro values) are
                        // displayed
                        isReplaceMacro = false;
                        break;

                    // Include variable paths command
                    case "path":
                    case "paths":
                        // Set the flag to include the variable paths and parse the variable path
                        // separators
                        isIncludePath = true;
                        separators = getVariablePathSeparators(parts[1]);
                        break;
                }
            }

            // Extract the item's attribute and name
            String[] attributeAndName = getParts(itemAndOther[0], "=", 2, false);

            // Check if this is a table-related request
            if (component.equals("table")
                || component.equals("proto_table")
                || component.equals("root_table")
                || component.equals("instance_table"))
            {
                // Set the tree type (instance, prototype, or both) based on the command
                tableTreeType = component.equals("table")
                                                          ? TableTreeType.TABLES
                                                          : (component.equals("proto_table")
                                                                                             ? TableTreeType.PROTOTYPE_TABLES
                                                                                             : TableTreeType.INSTANCE_TABLES);

                // Set the maximum level flag if only root table information is requested
                isMaxLevel = component.equals("root_table");

                // Use the attribute to determine the request
                switch (attributeAndName[0])
                {
                    case "all":
                    case "":
                        // Get the name, type, description, data, and data fields for the specified
                        // table (or all tables if no table name is specified)
                        response = getTableInformation(attributeAndName[1], separators);
                        break;

                    case "data":
                        // Get the data for the specified table (or all tables if no table name is
                        // specified)
                        response = getTableData(attributeAndName[1], true, separators);
                        break;

                    case "description":
                        // Get the description for the specified table (or all tables if no table
                        // name is specified)
                        response = getTableDescription(attributeAndName[1]);
                        break;

                    case "fields":
                        // Get a data field information for the specified table (or all tables if
                        // no table name is specified)
                        response = getTableFields(attributeAndName[1], true);
                        break;

                    case "names":
                        // Get the names of the data tables of the specified type (or all tables if
                        // no table name is specified)
                        response = getTableNames(attributeAndName[1]);
                        break;

                    case "size":
                        // Get the size of the specified structure data table (or all structure
                        // tables if no table name is specified)
                        response = getStructureSize(attributeAndName[1]);
                        break;

                    case "search":
                        // Get the results of the specified table search
                        response = getSearchResults(attributeAndName[1]);
                        break;

                    default:
                        throw new CCDDException("Unrecognized table attribute '"
                                                + attributeAndName[0]
                                                + "'");
                }
            }
            // Check if this is a group or application related request
            else if (component.equals("group") || component.equals("application"))
            {
                // Set the flag to true if this request only applies to groups that represent an
                // application
                boolean applicationOnly = component.equals("application");

                // Set the name based on if a group or application is requested
                String name = applicationOnly
                                              ? "application"
                                              : "group";

                // Use the attribute to determine the request
                switch (attributeAndName[0])
                {
                    case "all":
                    case "":
                        // Get the name, application status, description, and data fields for the
                        // specified group (or all groups if no group name is specified)
                        response = jsonHandler.getGroupInformation(attributeAndName[1],
                                                                   applicationOnly,
                                                                   null);
                        break;

                    case "tables":
                        // Get the tables for the specified group (or all groups if no group name
                        // is specified)
                        response = jsonHandler.getGroupTables(attributeAndName[1],
                                                              applicationOnly,
                                                              true);
                        break;

                    case "description":
                        // Get the description for the specified group (or all groups if no group
                        // name is specified)
                        response = jsonHandler.getGroupDescription(attributeAndName[1],
                                                                   applicationOnly,
                                                                   true);
                        break;

                    case "fields":
                        // Get a data field information for the specified group (or all groups if
                        // no group name is specified)
                        response = jsonHandler.getGroupFields(attributeAndName[1],
                                                              applicationOnly,
                                                              true,
                                                              null);
                        break;

                    case "names":
                        // Get all group names
                        response = getGroupNames(applicationOnly);
                        break;

                    default:
                        throw new CCDDException("Unrecognized "
                                                + name
                                                + " attribute '"
                                                + attributeAndName[0]
                                                + "'");
                }
            }
            // Check if this is a telemetry or application scheduler request
            else if (component.equals("scheduler"))
            {
                // Use the attribute to determine the request
                switch (attributeAndName[0])
                {
                    case "telemetry":
                        // Get the telemetry scheduler copy table
                        response = getTelemetrySchedulerData(attributeAndName[1]);
                        break;

                    case "application":
                        // Get the application scheduler table
                        response = getApplicationSchedulerData(attributeAndName[1]);
                        break;

                    default:
                        throw new CCDDException("Unrecognized scheduler attribute '"
                                                + attributeAndName[0]
                                                + "'");
                }
            }
            // Check if this is a variable names request
            else if (component.equals("variable"))
            {
                // Get the variable names
                response = getVariableNames(attributeAndName[0], attributeAndName[1]);
            }
            // Check if this is a telemetry parameter request
            else if (component.equals("telemetry"))
            {
                // Get the telemetered variable information
                response = getTelemetryInformation(attributeAndName[0]);
            }
            // Check if this is a command parameter request
            else if (component.equals("command"))
            {
                // Get the command information
                response = getCommandInformation(attributeAndName[0]);
            }
            // Check if this is a table type definition request
            else if (component.equals("table_type"))
            {
                // Get the table type definitions
                response = getTableTypeDefinitions();
            }
            // Check if this is a data type definition request
            else if (component.equals("data_type"))
            {
                // Get the data type definitions
                response = getDataTypeDefinitions();
            }
            // Check if this is a input type definition request
            else if (component.equals("input_type"))
            {
                // Get the input type definitions
                response = getInputTypeDefinitions();
            }
            // Check if this is a macro definition request
            else if (component.equals("macro"))
            {
                // Get the macro definitions
                response = getMacroDefinitions();
            }
            // Check if this is a message ID and name request
            else if (component.equals("message_id"))
            {
                // Get the message IDs and names
                response = getMessageIDAndNames();
            }
            // Check if this is a user authentication request
            else if (component.equals("authenticate"))
            {
                // Authenticate the user credentials
                response = authenticateUser(attributeAndName[0], attributeAndName[1]);
            }
            // Check if this is the project information request
            else if (component.equals("project_info"))
            {
                // Get the project information (name, description, etc.)
                response = getProjectInformation();
            }
            // Check if this is a web server shutdown request
            else if (component.equals("shutdown"))
            {
                // Execute the command to exit the application on a separate thread so that the
                // thread it's closing isn't the one that's running
                new Thread()
                {
                    /******************************************************************************
                     * Exit the application
                     *****************************************************************************/
                    @Override
                    public void run()
                    {
                        System.exit(0);
                    }
                }.start();
            }
            // The request type is unrecognized
            else
            {
                throw new CCDDException("Unrecognized request component '" + component + "'");
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the web server request is invalid
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Web Server Error",
                                  "Invalid web server request; cause '"
                                                      + ce.getMessage()
                                                      + "'",
                                  "<html><b>Invalid web server request");
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        // Check if a response to the request was made
        if (response != null)
        {
            // Remove the extraneous escape (\) characters that the JSON encoder inserts into the
            // string
            response = response.replaceAll("\\\\\\\\", "\\\\").replaceAll("\\\\/", "/");
        }

        return response;
    }

    /**********************************************************************************************
     * Authenticate the specified user credentials
     *
     * @param userName
     *            user name
     *
     * @param password
     *            user password
     *
     * @return The string "true" if the user name and password are valid for the currently open
     *         database; otherwise returns "false"
     *********************************************************************************************/
    private String authenticateUser(String userName, String password)
    {
        String response = "false";

        // Check if the user credentials are valid for the currently open database
        if (dbControl.authenticateUser(userName, password))
        {
            // Set the response to indicate the credentials are valid
            response = "true";
        }

        return response;
    }

    /**********************************************************************************************
     * Perform a search for the specified text in the data and internal tables
     *
     * @param searchCriteria
     *            attribute containing the search constraints in the format &lt;search
     *            text&gt;,&lt;ignore case (true or false)&gt;,&lt;allow regular expression (true
     *            or false)&gt;,&lt;data table cells only (true or false)&gt;&lt;,search table
     *            column names&gt;. The 'ignore case' and 'data table cells only' flags default to
     *            false if not provided. The last criterion is optional and allows the search to be
     *            constrained to specific columns in the data tables. The column names must be
     *            comma-separated (if more than one) and are case sensitive
     *
     * @return JSON encoded string containing the search results. An empty string if no matches are
     *         found, and null if the search parameters are missing or invalid
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getSearchResults(String searchCriteria)
    {
        String response = null;

        // Separate the search criteria string into the search text, ignore case flag, allow
        // regular expression, search data table cells only flag, and the column names in which to
        // search
        String[] parameter = getParts(searchCriteria, ",", 5, true);
        String searchText = parameter[0];
        String ignoreCase = parameter[1];
        String allowRegEx = parameter[2];
        String dataTablesOnly = parameter[3];
        String searchColumns = parameter[4];

        // Check if the flag to ignore case isn't present
        if (ignoreCase.isEmpty())
        {
            // Default to constraining matches by text case
            ignoreCase = "false";
        }

        // Check if the flag to search only the data tables isn't present
        if (dataTablesOnly.isEmpty())
        {
            // Default to searching the entire project database (data and internal table
            // references)
            dataTablesOnly = "false";
        }

        // Check if all the input parameters are present and that they're in the expected formats
        if (!searchText.isEmpty()
            && ignoreCase.matches(TRUE_OR_FALSE)
            && dataTablesOnly.matches(TRUE_OR_FALSE))
        {
            String dbColumns = "";
            JSONArray searchJA = new JSONArray();

            // Check if one or more column names to which the search is constrained is provided
            if (!searchColumns.isEmpty())
            {
                // Step through each column name
                for (String column : getParts(searchColumns, ",", -1, true))
                {
                    // Step through each defined table type
                    for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
                    {
                        // Step through each visible column in the table type
                        for (int index = NUM_HIDDEN_COLUMNS; index < typeDefn.getColumnCountDatabase(); ++index)
                        {
                            // Check if the column name matches the one in the table type
                            // definition
                            if (column.equals(typeDefn.getColumnNamesUser()[index]))
                            {
                                // Add the column's corresponding database name to the column
                                // constraints string. Stop searching since column names can't be
                                // duplicated within a table
                                dbColumns += typeDefn.getColumnNamesDatabase()[index] + ",";
                                break;
                            }
                        }

                        // Even if a match is found in one table type definition the search is
                        // allowed to continue in other definitions. Under certain circumstances
                        // involving structure table types it's possible for the database column
                        // name to be the same in two table types, but have different visible names
                    }

                    dbColumns = CcddUtilities.removeTrailer(dbColumns, ",");
                }
            }

            // Create a search handler to perform the search
            CcddSearchHandler searchHandler = new CcddSearchHandler(ccddMain,
                                                                    SearchDialogType.TABLES);

            // Create the match pattern from the search criteria
            Pattern searchPattern = CcddSearchHandler.createSearchPattern(searchText,
                                                                          false,
                                                                          Boolean.valueOf(allowRegEx),
                                                                          null);

            // Check if the search pattern is valid
            if (searchPattern != null)
            {
                // Perform the search and step through the results, if any
                for (Object[] searchResult : searchHandler.searchTablesOrScripts(searchPattern.pattern(),
                                                                                 Boolean.valueOf(ignoreCase),
                                                                                 Boolean.valueOf(dataTablesOnly),
                                                                                 dbColumns))
                {
                    // Store each search result in a JSON object and add it to the search results
                    // array
                    OrderedJSONObject searchJO = new OrderedJSONObject();
                    searchJO.put(SearchResultsColumnInfo.OWNER.getColumnName(SearchDialogType.TABLES),
                                 searchResult[SearchResultsColumnInfo.OWNER.ordinal()]);
                    searchJO.put(SearchResultsColumnInfo.LOCATION.getColumnName(SearchDialogType.TABLES),
                                 searchResult[SearchResultsColumnInfo.LOCATION.ordinal()]);
                    searchJO.put(SearchResultsColumnInfo.CONTEXT.getColumnName(SearchDialogType.TABLES),
                                 searchResult[SearchResultsColumnInfo.CONTEXT.ordinal()]);
                    searchJA.add(searchJO);
                }

                response = searchJA.toString();
            }
        }

        return response;
    }

    /**********************************************************************************************
     * Get the active project's information (name, description, lock status, and user)
     *
     * @return JSON encoded string containing active project's information
     *********************************************************************************************/
    private String getProjectInformation()
    {
        String user = "";
        OrderedJSONObject projectJO = new OrderedJSONObject();

        // Step through the list of databases and attached users
        for (String active : dbControl.queryActiveList(ccddMain.getMainFrame()))
        {
            // Separate the database name from the user name
            String[] databaseAndUser = active.split(",");

            // Check if the current database name matches the one in the list
            if (dbControl.getDatabaseName().equalsIgnoreCase(databaseAndUser[0]))
            {
                // Add the user to the string of users
                user += databaseAndUser[1] + ", ";
            }
        }

        // Store the project name, equivalent database name, description, lock status, user name,
        // owner, server, port, and data fields in the JSON object
        projectJO.put("Project", dbControl.getProjectName());
        projectJO.put("Database", dbControl.getDatabaseName());
        projectJO.put("Description", dbControl.getDatabaseDescription(dbControl.getDatabaseName()));
        projectJO.put("Status", dbControl.getDatabaseLockStatus(dbControl.getDatabaseName())
                                                                                             ? "locked"
                                                                                             : "unlocked");
        projectJO.put("User", CcddUtilities.removeTrailer(user, ", "));
        projectJO.put("Owner", dbControl.getOwner());
        projectJO.put("Server", dbControl.getHost());
        projectJO.put("Port", dbControl.getPort());
        projectJO = jsonHandler.getDataFields(CcddFieldHandler.getFieldProjectName(),
                                              JSONTags.PROJECT_FIELD.getTag(),
                                              null,
                                              projectJO);

        return projectJO.toString();
    }

    /**********************************************************************************************
     * Get a list containing the names and paths of every data table. The tree type (prototype only
     * or instances only) is determined by the server command
     *
     * @return List containing the names and paths of every data table
     *********************************************************************************************/
    private List<String> getTableList()
    {
        // Build the table tree, including the primitive variables
        CcddTableTreeHandler allTablesTree = new CcddTableTreeHandler(ccddMain,
                                                                      tableTreeType,
                                                                      ccddMain.getMainFrame());

        // Convert the table tree to a list of table paths
        return allTablesTree.getTableTreePathList(null,
                                                  allTablesTree.getRootNode(),
                                                  isMaxLevel
                                                             ? allTablesTree.getHeaderNodeLevel()
                                                             : -1);
    }

    /**********************************************************************************************
     * Get the data for the specified data table, or for all data tables if no table name is
     * provided
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]. Blank to
     *            return the data for all tables
     *
     * @param getDescription
     *            true to get the table description when loading the table data
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s)
     *
     * @return JSON encoded string containing the specified table cell data; null if a table name
     *         is specified and the table doesn't exist or if no data tables exist in the project
     *         database, or blank if the specified table has no data or none of the tables have
     *         data. Empty cells are included
     *
     * @throws CCDDException
     *             If an error occurs while parsing the table data
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableData(String tableName,
                                boolean getDescription,
                                String[] separators) throws CCDDException
    {
        String response = null;

        // Check if no table name is specified
        if (tableName.isEmpty())
        {
            // Get the list of all data table names
            List<String> tableNameList = getTableList();

            // Check that at least one table exists in the project database
            if (!tableNameList.isEmpty())
            {
                JSONArray responseJA = new JSONArray();
                JSONParser parser = new JSONParser();
                response = "";

                // Step through each table name
                for (String name : tableNameList)
                {
                    try
                    {
                        // Get the data for this table as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getTableData(name, true, separators)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing table data");
                    }
                }

                // Convert the response array to a JSON string
                response = responseJA.toString();
            }
        }
        // A table name is provided
        else
        {
            // Get the table data
            OrderedJSONObject tableNameAndData = jsonHandler.getTableData(tableName,
                                                                          getDescription,
                                                                          isReplaceMacro,
                                                                          isIncludePath,
                                                                          variableHandler,
                                                                          separators,
                                                                          new OrderedJSONObject());

            // Check if the table data loaded successfully
            if (tableNameAndData != null)
            {
                // Add the table name. If the table has no data then the table data shows empty
                tableNameAndData.put(JSONTags.TABLE_NAME.getTag(), tableName);
                response = tableNameAndData.toString();
            }
        }

        return response;
    }

    /**********************************************************************************************
     * Get the description for the specified table, or all tables with a description if no table
     * name is provided
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]. If blank
     *            then the description for every data table with a description is returned
     *
     * @return JSON encoded string containing the specified table's description; null if the
     *         specified table doesn't exist or the project has no data tables
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableDescription(String tableName)
    {
        String response = null;

        // Check if a table name is provided
        if (!tableName.isEmpty())
        {
            // Check if the table exists in the project database
            if (dbTable.isTableExists(tableName, ccddMain.getMainFrame()))
            {
                // Store the table name and description
                OrderedJSONObject tableNameAndDesc = new OrderedJSONObject();
                tableNameAndDesc.put(JSONTags.TABLE_NAME.getTag(), tableName);
                tableNameAndDesc.put(JSONTags.TABLE_DESCRIPTION.getTag(),
                                     dbTable.queryTableDescription(tableName,
                                                                   ccddMain.getMainFrame()));
                response = tableNameAndDesc.toString();
            }
        }
        // No table is specified; i.e., get the descriptions for all tables
        else
        {
            // Get the array of data table names
            String[] tableNames = dbTable.queryTableList(ccddMain.getMainFrame());

            // Check if the project database contains a data table
            if (tableNames.length != 0)
            {
                OrderedJSONObject tableNameAndDesc;
                JSONArray responseJA = new JSONArray();

                // Get the description for every table that has a description
                String[][] namePathAndDesc = dbTable.queryTableDescriptions(ccddMain.getMainFrame());

                // Step through each table name
                for (String name : tableNames)
                {
                    String description = "";

                    // Step through each table name in the array of tables with descriptions
                    for (int index = 0; index < namePathAndDesc.length; index++)
                    {
                        // Check if the name matches the name and path from the description array
                        if (name.equalsIgnoreCase(namePathAndDesc[index][0]))
                        {
                            // Store the description and stop searching
                            description = namePathAndDesc[index][1];
                            break;
                        }
                    }

                    // Store the table name and description, and add it to the array
                    tableNameAndDesc = new OrderedJSONObject();
                    tableNameAndDesc.put(JSONTags.TABLE_NAME.getTag(), name);
                    tableNameAndDesc.put(JSONTags.TABLE_DESCRIPTION.getTag(), description);
                    responseJA.add(tableNameAndDesc);
                }

                response = responseJA.toString();
            }
        }

        return response;
    }

    /**********************************************************************************************
     * Get the data field information for the specified table, or for all tables if no table name
     * is provided
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]. If blank
     *            then every data table's data fields are returned
     *
     * @param checkExists
     *            true to check if the specified table exists in the project database
     *
     * @return JSON encoded string containing the specified table's data fields; null if the table
     *         doesn't exist or if the project database contains no data tables
     *
     * @throws CCDDException
     *             If an error occurs while parsing the table data field data
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableFields(String tableName, boolean checkExists) throws CCDDException
    {
        String response = null;

        // Check if no table name is provided (i.e., get all tables' fields)
        if (tableName.isEmpty())
        {
            // Check if at least one table exists
            if (dbTable.queryTableList(ccddMain.getMainFrame()).length != 0)
            {
                List<String> tableNames = new ArrayList<String>();

                // Step through the data fields
                for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
                {
                    // Check if the table name isn't already in the list and that this is not a
                    // table type or group data field
                    if (!tableNames.contains(fieldInfo.getOwnerName())
                        && !CcddFieldHandler.isTableTypeField(fieldInfo.getOwnerName())
                        && !CcddFieldHandler.isGroupField(fieldInfo.getOwnerName()))
                    {
                        // Store the table name in the list
                        tableNames.add(fieldInfo.getOwnerName());
                    }
                }

                JSONArray responseJA = new JSONArray();
                JSONParser parser = new JSONParser();
                response = "";

                // Step through the list of tables with data fields
                for (String name : tableNames)
                {
                    try
                    {
                        // Get the fields for this table as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getTableFields(name, false)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing table data fields");
                    }
                }

                // Add the table fields to the response
                response = responseJA.toString();
            }
        }
        // A table name is provided. Check if the table existence should be ignored, or else if the
        // table exists in the database
        else if (!checkExists || dbTable.isTableExists(tableName, ccddMain.getMainFrame()))
        {
            // Add the table name and data field information to the output
            OrderedJSONObject tableNameAndFields = new OrderedJSONObject();
            tableNameAndFields.put(JSONTags.TABLE_NAME.getTag(), tableName);
            tableNameAndFields = jsonHandler.getDataFields(tableName,
                                                           JSONTags.TABLE_FIELD.getTag(),
                                                           null,
                                                           tableNameAndFields);
            response = tableNameAndFields.toString();
        }

        return response;
    }

    /**********************************************************************************************
     * Get the names of all tables of the specified table type, or all tables names and their types
     * if no table type is provided
     *
     * @param tableType
     *            table type. The type is case insensitive. If blank then every data table and its
     *            type is returned
     *
     * @return JSON encoded string containing all table names of the specified table type; blank if
     *         the type is valid but no tables of the type exist, and null if the specified table
     *         type doesn't exist or if no data tables exist in the project database
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableNames(String tableType)
    {
        String response = null;

        // Get the list of table names and their associated table type
        ArrayListMultiple protoNamesAndTableTypes = new ArrayListMultiple();
        protoNamesAndTableTypes.addAll(dbTable.queryTableAndTypeList(ccddMain.getMainFrame()));

        // Check that at least one data table exists
        if (protoNamesAndTableTypes.size() != 0)
        {
            JSONArray responseJA = new JSONArray();
            OrderedJSONObject responseJO = null;

            // Get the list of table types
            String[] tableTypes = dbTable.queryTableTypesList(ccddMain.getMainFrame());

            // Set the flag to indicate if only one table type is specified or exists
            boolean isSingle = !tableType.isEmpty() || tableTypes.length == 1;

            // Step through each valid table type
            for (String type : tableTypes)
            {
                // Check if no table type is specified (i.e., return all types) or if the type
                // matches the one specified
                if (tableType.isEmpty() || type.equalsIgnoreCase(tableType))
                {
                    // Check if this is the first valid table type match
                    if (response == null)
                    {
                        // Initialize the output; this causes a blank to be returned if the type is
                        // valid but no tables of the type exist
                        response = "";
                    }

                    responseJO = new OrderedJSONObject();
                    JSONArray namesJA = new JSONArray();

                    // Step through each table name
                    for (String tableName : getTableList())
                    {
                        // Locate the table's prototype in the list
                        int index = protoNamesAndTableTypes.indexOf(tableName.replaceFirst(",.*$",
                                                                                           ""));

                        // Check if the root table name matches that in the types list
                        if (type.equalsIgnoreCase(protoNamesAndTableTypes.get(index)[2]))
                        {
                            // Add the table to the list for this table type
                            namesJA.add(tableName);
                        }
                    }

                    // Store the table type and associated table name(s)
                    responseJO.put(JSONTags.TABLE_TYPE.getTag(), type);
                    responseJO.put(JSONTags.TABLE_NAMES.getTag(), namesJA);

                    // Check if only one table type is being processed
                    if (isSingle)
                    {
                        // Stop searching
                        break;
                    }

                    // More than one table is in the response; add the type and names to the array
                    responseJA.add(responseJO);
                }
            }

            // Check if the specified table type exists, or any type exists if none is specified
            if (response != null)
            {
                // Set the response based of if a single or multiple types are included in the
                // response. If single then the JSON object is used to prevent the extraneous
                // brackets from enclosing the response
                response = isSingle
                                    ? responseJO.toString()
                                    : responseJA.toString();
            }
        }

        return response;
    }

    /**********************************************************************************************
     * Get the number of bytes for the prototype of the specified structure table, or for all
     * prototype structure tables if no table is specified
     *
     * @param tableName
     *            structure table name or path
     *
     * @return JSON encoded string containing the structure table name(s) and corresponding size(s)
     *         in bytes; null if a table name is specified and the table doesn't exist or isn't a
     *         structure, or if no structure tables exist in the project database
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getStructureSize(String tableName)
    {
        String response = null;
        OrderedJSONObject responseJO = null;
        JSONArray responseJA = new JSONArray();

        // Get the list of table names and their associated table type
        ArrayListMultiple protoNamesAndTableTypes = new ArrayListMultiple();
        protoNamesAndTableTypes.addAll(dbTable.queryTableAndTypeList(ccddMain.getMainFrame()));

        // Get the specified table's prototype table name
        String prototypeName = TableInformation.getPrototypeName(tableName);

        // Flag indicating if a single table is requested
        boolean isSingle = !prototypeName.isEmpty();

        // Step through each prototype table name/type
        for (String[] namesAndType : protoNamesAndTableTypes)
        {
            // Check if all structure tables are requested or if the table name matches the
            // specified name
            if (!isSingle || prototypeName.equalsIgnoreCase(namesAndType[0]))
            {
                // Get the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(namesAndType[2]);

                // Check if the table represents a structure
                if (typeDefn.isStructure())
                {
                    responseJO = new OrderedJSONObject();

                    // Check if the link handler exists
                    if (linkHandler == null)
                    {
                        // Create a link handler
                        linkHandler = new CcddLinkHandler(ccddMain, ccddMain.getMainFrame());
                    }

                    // Store the table name and its size in bytes
                    responseJO.put(JSONTags.TABLE_NAME.getTag(),
                                   (isSingle
                                             ? tableName
                                             : namesAndType[0]));
                    responseJO.put(JSONTags.TABLE_BYTE_SIZE.getTag(),
                                   variableHandler.getDataTypeSizeInBytes(namesAndType[0]));

                    // Check if only one table is being processed
                    if (isSingle)
                    {
                        // Stop searching
                        break;
                    }

                    // More than one table is in the response; add the name and size to the array
                    responseJA.add(responseJO);
                }
            }
        }

        // Check if the specified table exists and is a structure, or any structure table exists if
        // none is specified
        if (responseJO != null)
        {
            // Set the response based of if a single or multiple types are included in the
            // response. If single then the JSON object is used to prevent the extraneous brackets
            // from enclosing the response
            response = isSingle
                                ? responseJO.toString()
                                : responseJA.toString();
        }

        return response;
    }

    /**********************************************************************************************
     * Get the type, description, size, data, and data fields for the specified data table
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]. Blank to
     *            return the data for all tables
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s)
     *
     * @return JSON encoded string containing the specified table information; null if a table name
     *         is specified and the table doesn't exist or if no data tables exist in the project
     *         database
     *
     * @throws CCDDException
     *             If an error occurs while parsing the table information
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableInformation(String tableName, String[] separators) throws CCDDException
    {
        JSONArray responseJA = new JSONArray();
        JSONParser parser = new JSONParser();
        String response = null;

        // Check if no table name is provided (i.e., get the information for all tables)
        if (tableName.isEmpty())
        {
            // Get the list of all data table names
            List<String> tableNameList = getTableList();

            // Check that at least one table exists in the project database
            if (!tableNameList.isEmpty())
            {
                response = "";

                // Step through each table name
                for (String name : tableNameList)
                {
                    try
                    {
                        // Get the fields for this table as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getTableInformation(name, separators)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing table information");
                    }
                }

                // Convert the response array to a JSON string
                response = responseJA.toString();
            }
        }
        // A table name is provided
        {
            // Get the tables information
            OrderedJSONObject tableInfoJO = jsonHandler.getTableInformation(tableName,
                                                                            isReplaceMacro,
                                                                            isIncludePath,
                                                                            variableHandler,
                                                                            separators);

            // Check if the table loaded successfully
            if (tableInfoJO != null)
            {
                // Add the table's information to the output
                response = tableInfoJO.toString();
            }
        }

        return response;
    }

    /**********************************************************************************************
     * Get the names of all groups or applications
     *
     * @param applicationOnly
     *            true if only groups that represent applications should be processed
     *
     * @return JSON encoded string containing the all group/application names; null if no
     *         groups/applications exist in the project database
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupNames(boolean applicationOnly)
    {
        String response = null;

        // Get an array containing all group/application names
        String[] groupNames = groupHandler.getGroupNames(applicationOnly);

        // Check if any groups/applications exist
        if (groupNames.length != 0)
        {
            response = "";
            OrderedJSONObject responseJO = new OrderedJSONObject();
            JSONArray namesJA = new JSONArray();

            // Step though each group/application
            for (String groupName : groupNames)
            {
                // Add the group name to the array
                namesJA.add(groupName);
            }

            // Store the group/application name(s)
            responseJO.put((applicationOnly
                                            ? JSONTags.APPLICATION_NAMES.getTag()
                                            : JSONTags.GROUP_NAMES.getTag()),
                           namesJA);
            response = responseJO.toString();
        }

        return response;
    };

    /**********************************************************************************************
     * Get the telemetry scheduler's copy table entries
     *
     * @param parameters
     *            comma-separated string containing the data stream name, header size (in bytes),
     *            message ID name data field name, and the optimize result flag ('true' or 'false')
     *
     * @return JSON encoded string containing the specified copy table entries; null if the number
     *         of parameters or their formats are incorrect
     *
     * @throws CCDDException
     *             If an error occurs while parsing the telemetry scheduler data
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTelemetrySchedulerData(String parameters) throws CCDDException
    {
        String response = null;

        // Separate the input parameters
        String[] parameter = getParts(parameters, ",", 4, true);

        // Check if all the input parameters are present and that they're in the expected formats
        if (parameter[1].matches("\\d+") && parameter[3].matches(TRUE_OR_FALSE))
        {
            JSONArray tableJA = new JSONArray();

            // Get the individual parameters and format them if needed
            String streamName = parameter[0];
            int headerSize = Integer.valueOf(parameter[1]);
            String messageIDNameField = parameter[2];
            boolean optimize = Boolean.valueOf(parameter[3]);

            // Get the rate information based on the supplied data stream name
            RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

            // Check if the rate information doesn't exist with this stream name
            if (rateInfo == null)
            {
                throw new CCDDException("Unknown data stream name");
            }

            // Create an instance of the copy table handler in order to read the information from
            // the database
            CcddCopyTableHandler copyHandler = new CcddCopyTableHandler(ccddMain);

            // Create the copy table entries based on the supplied parameters
            String[][] copyTable = copyHandler.createCopyTable(new CcddLinkHandler(ccddMain,
                                                                                   ccddMain.getMainFrame()),
                                                               rateInfo.getStreamName(),
                                                               headerSize,
                                                               messageIDNameField,
                                                               null,
                                                               optimize,
                                                               isReplaceMacro);

            // Check if there are any entries in the table
            if (copyTable.length != 0)
            {
                // Step through each row in the table
                for (String[] row : copyTable)
                {
                    OrderedJSONObject rowJO = new OrderedJSONObject();

                    // Step through each column in the row
                    for (int column = 0; column < row.length; column++)
                    {
                        // Add the copy table value to the array. An array is used to preserve the
                        // order of the items
                        rowJO.put(CopyTableEntry.values()[column].getColumnName(), row[column]);
                    }

                    // Add the row's copy table values to the table array
                    tableJA.add(rowJO);
                }
            }

            // Store the copy table information
            OrderedJSONObject copyJO = new OrderedJSONObject();
            copyJO.put(JSONTags.COPY_TABLE_STREAM.getTag(), streamName);
            copyJO.put(JSONTags.COPY_TABLE_HDR_SIZE.getTag(), String.valueOf(headerSize));
            copyJO.put(JSONTags.COPY_TABLE_OPTIMIZE.getTag(), String.valueOf(optimize));
            copyJO.put(JSONTags.COPY_TABLE_DATA.getTag(), tableJA);
            response = copyJO.toString();
        }
        // Invalid parameter format
        else
        {
            throw new CCDDException("Parameter type mismatch");
        }

        return response;
    }

    /**********************************************************************************************
     * *** NOTE: This provides a rudimentary schedule or message table entry output ***
     *
     * Get the application scheduler's schedule or message definition table entries
     *
     * @param appTableType
     *            application scheduler table entry type: schedule or message
     *
     * @return JSON encoded string containing the schedule or message definition table entries;
     *         null if the table type is unrecognized
     *
     * @throws CCDDException
     *             If the scheduler table type is unrecognized
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getApplicationSchedulerData(String appTableType) throws CCDDException
    {
        String response = null;
        JSONArray tableJA = new JSONArray();

        // Create an instance of the application scheduler table handler in order to read the
        // information from the database
        CcddApplicationSchedulerTableHandler appSchTable = new CcddApplicationSchedulerTableHandler(ccddMain);

        // Check if the schedule definition table data is requested
        if (appTableType.equals("schedule"))
        {
            // Get the number of time slots
            int numTimeSlots = appSchTable.getNumberOfTimeSlots();

            // Check if there are any time slots
            if (numTimeSlots != 0)
            {
                // Schedule definition table columns
                String[] columnNames = new String[] {"EnableState",
                                                     "Type",
                                                     "Frequency",
                                                     "Remainder",
                                                     "MessageIndex",
                                                     "GroupData"};

                // Step through each time slot
                for (int timeSlot = 0; timeSlot < numTimeSlots; timeSlot++)
                {
                    // Get the schedule definition table entries
                    String[][] sdtEntries = appSchTable.getScheduleDefinitionTableByRow(timeSlot);

                    // Step through each row in the table
                    for (String[] row : sdtEntries)
                    {
                        OrderedJSONObject rowJO = new OrderedJSONObject();

                        // Step through each column in the row
                        for (int column = 0; column < row.length; column++)
                        {
                            // Add the schedule definition table value to the array. An array is
                            // used to preserve the order of the items
                            rowJO.put(columnNames[column], row[column]);
                        }

                        // Add the row's schedule definition table values to the table array
                        tableJA.add(rowJO);
                    }
                }

                // Store the schedule definition table information
                OrderedJSONObject appJO = new OrderedJSONObject();
                appJO.put(JSONTags.APP_SCHED_SCHEDULE_TABLE.getTag(), tableJA);
                response = appJO.toString();
            }
        }
        // Check if the message definition table data is requested
        else if (appTableType.equals("message"))
        {
            // Get the message definition table entries
            String[] msgEntries = appSchTable.getMessageDefinitionTable();

            // Check if there are any entries
            if (msgEntries.length != 0)
            {
                // Step through each message definition table entry
                for (int row = 0; row < msgEntries.length; row++)
                {
                    OrderedJSONObject rowJO = new OrderedJSONObject();

                    // Add the message definition table value to the array. An array is used to
                    // preserve the order of the items
                    rowJO.put("Command ID #" + row, msgEntries[row]
                                                    + (!msgEntries[row].equals("SCH_UNUSED_MID")
                                                                                                 ? ", 0xC000, 0x0001, 0x0000"
                                                                                                 : ""));

                    // Add the row's message definition table values to the table array
                    tableJA.add(rowJO);
                }

                // Store the message definition table information
                OrderedJSONObject appJO = new OrderedJSONObject();
                appJO.put(JSONTags.APP_SCHED_MESSAGE_TABLE.getTag(), tableJA);
                response = appJO.toString();
            }
        }
        // Invalid table type
        else
        {
            throw new CCDDException("Invalid table type");
        }

        return response;
    }

    /**********************************************************************************************
     * Get the variable names, both in application format and altered based on the user-provided
     * parameters
     *
     * @param variablePath
     *            variable path + name for which to return the converted path; blank to provide the
     *            paths for all variables
     *
     * @param parameters
     *            comma-separated string containing the variable path separator character(s),
     *            show/hide data types flag ('true' or 'false'), and data type/variable name
     *            separator character(s); blank to use the default separators
     *
     * @return JSON encoded string containing the variable names; blank if the project doesn't
     *         contain any variables and null if any input parameter is invalid
     *
     * @throws CCDDException
     *             If an illegal separator character(s) or invalid show/hide data type flag value
     *             is detected
     *********************************************************************************************/
    private String getVariableNames(String variablePath, String parameters) throws CCDDException
    {
        String response = null;

        // Parse the variable path separators
        String[] separators = getVariablePathSeparators(parameters);

        // Check if all the input parameters are present and that they're in the expected formats
        if (separators != null)
        {
            OrderedJSONObject responseJO = new OrderedJSONObject();
            response = "";

            // Get the individual parameters and format them if needed
            String varPathSeparator = separators[0];
            boolean hideDataTypes = Boolean.valueOf(separators[1]);
            String typeNameSeparator = separators[2];

            // Check if a variable path is specified
            if (!variablePath.isEmpty())
            {
                // Store the variable path and name in the application and user-specified formats
                responseJO.put(variablePath,
                               variableHandler.getFullVariableName(variablePath,
                                                                   varPathSeparator,
                                                                   hideDataTypes,
                                                                   typeNameSeparator));
            }
            // Get the conversion for all variables
            else
            {
                // Step through each row in the variables table
                for (int row = 0; row < variableHandler.getAllVariableNames().size(); row++)
                {
                    // Store the variable paths and names in the application and user-specified
                    // formats
                    responseJO.put(variableHandler.getAllVariableNames().get(row).toString(),
                                   variableHandler.getFullVariableName(variableHandler.getAllVariableNames().get(row).toString(),
                                                                       varPathSeparator,
                                                                       hideDataTypes,
                                                                       typeNameSeparator));
                }
            }

            response = responseJO.toString();
        }
        // Illegal separator character(s) or invalid show/hide data type flag value
        else
        {
            throw new CCDDException("Illegal separator character(s) or invalid show/hide data type flag value");
        }

        return response;
    }

    /**********************************************************************************************
     * Get the path, data type, bit length, description, units, data stream information, and
     * enumeration information for each telemetered variable matching the specified filters
     *
     * @param telemetryFilter
     *            group (or application) name, data stream name, and/or rate value filter(s). A
     *            table must belong to the specified group in order for its telemetered variables
     *            to be returned; blank to get all telemetered variables (regardless of group). A
     *            variable must have a rate assigned for the specified data stream in order to be
     *            included; blank to include all data streams. A variable's rate must match the
     *            specified rate in order to be included; blank to include the variable regardless
     *            of the rate value
     *
     * @return JSON encoded string containing the path, data type, bit length, description, units,
     *         data stream name(s), and enumeration(s) for each telemetered variable matching the
     *         specified filters; empty array if no variables are telemetered
     *
     * @throws CCDDException
     *             If an invalid group name, data stream name, or rate value format is detected
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTelemetryInformation(String telemetryFilter) throws CCDDException
    {
        JSONArray telemetryJA = new JSONArray();
        TypeDefinition typeDefn = null;
        String groupFilter = "";
        String streamFilter = "";
        String rateFilter = "";
        int variableNameIndex = -1;
        int dataTypeIndex = -1;
        int bitLengthIndex = -1;
        List<Integer> rateIndices = null;
        List<Integer> enumerationIndices = null;
        int descriptionIndex = -1;
        int unitsIndex = -1;
        List<String> allTableNameList = new ArrayList<String>();

        // Table type name for the previous table type loaded
        String lastType = "";

        // Get the array of data stream names
        String[] dataStreamNames = rateHandler.getDataStreamNames();

        // Check if a filter is specified
        if (!telemetryFilter.isEmpty())
        {
            // Separate the filter parameters
            String[] filter = getParts(telemetryFilter, ",", 3, true);

            // Check that the number of filter parameters is correct, that the data stream name
            // filter is blank or matches an existing data stream, and that the rate value filter,
            // if present, is in the expected format
            if ((filter[1].isEmpty()
                 || Arrays.asList(dataStreamNames).contains(filter[1]))
                && (filter[2].isEmpty()
                    || filter[2].matches("\\d+(?:$|(?:\\.|\\s*/\\s*)\\d+)")))
            {
                // Store the group name, stream name, and rate filters
                groupFilter = filter[0];
                streamFilter = filter[1];
                rateFilter = filter[2];

                // Check if a group name filter is specified
                if (!groupFilter.isEmpty())
                {
                    // Extract the table names belonging to the group
                    GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupFilter);

                    // Check if the group doesn't exist
                    if (groupInfo == null)
                    {
                        throw new CCDDException("Unrecognized group name");
                    }

                    // Get the tables associated with the group
                    allTableNameList = groupInfo.getTablesAndAncestors();
                }
            }
            // Unrecognized data stream name or invalid rate value format
            else
            {
                throw new CCDDException("Unrecognized stream name or invalid rate value format");
            }
        }

        // Check if no group filter is in effect
        if (groupFilter.isEmpty())
        {
            // Get a list of all root and child tables
            tableTreeType = TableTreeType.INSTANCE_TABLES;
            allTableNameList = getTableList();
        }

        // Step through each structure table
        for (String table : allTableNameList)
        {
            // Get the information from the database for the specified table
            TableInformation tableInfo = dbTable.loadTableData(table,
                                                               false,
                                                               false,
                                                               ccddMain.getMainFrame());

            // Check if the table loaded successfully
            if (!tableInfo.isErrorFlag())
            {
                // Get the table's type definition
                typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                // Check if the table represents a structure
                if (typeDefn.isStructure())
                {
                    // Check if the table type changed. This accounts for multiple table types that
                    // represent structures, and prevents reloading the table type information for
                    // every table
                    if (!tableInfo.getType().equals(lastType))
                    {
                        String descColName;
                        String unitsColName;
                        descriptionIndex = -1;
                        unitsIndex = -1;

                        // Store the table type name
                        lastType = tableInfo.getType();

                        // Get the variable name column
                        variableNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);

                        // Get the data type column
                        dataTypeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);

                        // Get the bit length column
                        bitLengthIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);

                        // Check if a data stream filter is in effect
                        if (!streamFilter.isEmpty())
                        {
                            // Get the index of the rate column corresponding to the data stream
                            // filter
                            rateIndices = new ArrayList<Integer>();

                            // Get the index of the rate column with the corresponding data stream
                            // name
                            int rateIndex = typeDefn.getColumnIndexByUserName(rateHandler.getRateInformationByStreamName(streamFilter).getRateName());

                            // Check if the table has the specified rate column
                            if (rateIndex != -1)
                            {
                                // Add the rate column index to the list
                                rateIndices.add(typeDefn.getColumnIndexByUserName(rateHandler.getRateInformationByStreamName(streamFilter).getRateName()));
                            }
                        }
                        // Include all data streams
                        else
                        {
                            // Get the indices for all rate columns
                            rateIndices = typeDefn.getColumnIndicesByInputType(DefaultInputType.RATE);
                        }

                        // Check if a enumeration column exists
                        if ((descColName = typeDefn.getColumnNameByInputTypeFormat(InputTypeFormat.ENUMERATION)) != null)
                        {
                            // Get the enumeration column(s)
                            enumerationIndices = typeDefn.getColumnIndicesByInputTypeFormat(InputTypeFormat.ENUMERATION);
                        }

                        // Check if a description column exists
                        if ((descColName = typeDefn.getColumnNameByInputType(DefaultInputType.DESCRIPTION)) != null)
                        {
                            // Get the description column
                            descriptionIndex = typeDefn.getColumnIndexByUserName(descColName);
                        }

                        // Check if a units column exists
                        if ((unitsColName = typeDefn.getColumnNameByInputType(DefaultInputType.UNITS)) != null)
                        {
                            // Get the units column
                            unitsIndex = typeDefn.getColumnIndexByUserName(unitsColName);
                        }
                    }

                    // Check if the macro names should be replaced with the corresponding macro
                    // values
                    if (isReplaceMacro)
                    {
                        // Replace all macros in the table
                        tableInfo.setData(ccddMain.getMacroHandler().replaceAllMacros(tableInfo.getData()));
                    }

                    // Step through each variable in the structure table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        OrderedJSONObject structureJO = new OrderedJSONObject();
                        String cellValue;

                        // Check if the variable name is present. If not then all the variable data
                        // on this row is skipped
                        if (!(cellValue = tableInfo.getData()[row][variableNameIndex].toString()).isEmpty())
                        {
                            boolean hasRate = false;

                            // Step through the relevant rate columns
                            for (Integer rateIndex : rateIndices)
                            {
                                // Check that a rate value is present and if the rate matches the
                                // rate filter value (or no filter is in effect)
                                if (!tableInfo.getData()[row][rateIndex].toString().isEmpty()
                                    && (rateFilter.isEmpty()
                                        || tableInfo.getData()[row][rateIndex].equals(rateFilter)))
                                {
                                    hasRate = true;

                                    // Store the rate in the JSON output
                                    structureJO.put(typeDefn.getColumnNamesUser()[rateIndex],
                                                    tableInfo.getData()[row][rateIndex]);
                                }
                            }

                            // Check if a variable matching the rate filters exists
                            if (hasRate)
                            {
                                // Store the name of the structure table from which this variable
                                // is taken
                                structureJO.put("Structure Table Name", table);

                                // Store the variable name in the JSON output
                                structureJO.put(typeDefn.getColumnNamesUser()[variableNameIndex],
                                                cellValue);

                                // Check if the data type is present
                                if (!(cellValue = tableInfo.getData()[row][dataTypeIndex].toString()).isEmpty())
                                {
                                    // Store the data type in the JSON output
                                    structureJO.put(typeDefn.getColumnNamesUser()[dataTypeIndex],
                                                    cellValue);
                                }

                                // Check if the bit length is present
                                if (!(cellValue = tableInfo.getData()[row][bitLengthIndex].toString()).isEmpty())
                                {
                                    // Store the bit length in the JSON output
                                    structureJO.put(typeDefn.getColumnNamesUser()[bitLengthIndex],
                                                    cellValue);
                                }

                                // Step through each enumeration column
                                for (Integer enumerationIndex : enumerationIndices)
                                {
                                    // Check if the enumeration is present
                                    if (!(cellValue = tableInfo.getData()[row][enumerationIndex].toString()).isEmpty())
                                    {
                                        // Store the enumeration in the JSON output
                                        structureJO.put(typeDefn.getColumnNamesUser()[enumerationIndex],
                                                        cellValue);
                                    }
                                }

                                // Check if the description is present
                                if (descriptionIndex != -1
                                    && !(cellValue = tableInfo.getData()[row][descriptionIndex].toString()).isEmpty())
                                {
                                    // Store the description in the JSON output
                                    structureJO.put(typeDefn.getColumnNamesUser()[descriptionIndex],
                                                    cellValue);
                                }

                                // Check if the units is present
                                if (unitsIndex != -1
                                    && !(cellValue = tableInfo.getData()[row][unitsIndex].toString()).isEmpty())
                                {
                                    // Store the units in the JSON output
                                    structureJO.put(typeDefn.getColumnNamesUser()[descriptionIndex],
                                                    cellValue);
                                }

                                // Add the variable to the JSON array
                                telemetryJA.add(structureJO);
                            }
                        }
                    }
                }
            }
        }

        return telemetryJA.toString();
    }

    /**********************************************************************************************
     * Get the information for each command matching the specified filters
     *
     * @param groupFilter
     *            group (or application) name. A table must belong to the specified group in order
     *            for its telemetered variables to be returned; blank to get all telemetered
     *            variables (regardless of group)
     *
     * @return JSON encoded string containing information for each command matching the specified
     *         filters
     *
     * @throws CCDDException
     *             If the supplied group name is unrecognized
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private String getCommandInformation(String groupFilter) throws CCDDException
    {
        JSONArray commandsJA = new JSONArray();
        TypeDefinition typeDefn = null;
        int commandNameIndex = -1;
        int commandCodeIndex = -1;
        int commandDescriptionIndex = -1;
        List<AssociatedColumns> commandArguments = null;
        List<String> groupTables = null;

        // Table type name for the previous table type loaded
        String lastType = "";

        // Check if a group name filter is specified
        if (!groupFilter.isEmpty())
        {
            // Extract the table names belonging to the group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupFilter);

            // Check if the group doesn't exist
            if (groupInfo == null)
            {
                throw new CCDDException("Unrecognized group name");
            }

            // Get the tables associated with the group
            groupTables = groupInfo.getTablesAndAncestors();
        }

        // Step through each command table
        for (String commandTable : dbTable.getPrototypeTablesOfType(TYPE_COMMAND))
        {
            // Check if all commands are to be returned, or if a specific group's commands are
            // requested that the table is a member of the group
            if (groupFilter.isEmpty()
                || groupTables.contains(commandTable))
            {
                // Get the information from the database for the specified table
                TableInformation tableInfo = dbTable.loadTableData(commandTable,
                                                                   false,
                                                                   false,
                                                                   ccddMain.getMainFrame());

                // Check if the table loaded successfully
                if (!tableInfo.isErrorFlag())
                {
                    // Check if the table type changed. This accounts for multiple table types that
                    // represent commands, and prevents reloading the table type information for
                    // every table
                    if (!tableInfo.getType().equals(lastType))
                    {
                        String descColName;
                        commandDescriptionIndex = -1;

                        // Store the table type name
                        lastType = tableInfo.getType();

                        // Get the table's type definition
                        typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                        // Get the command name column
                        commandNameIndex = typeDefn.getColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.COMMAND_NAME));

                        // Get the command name column
                        commandCodeIndex = typeDefn.getColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.COMMAND_CODE));

                        // Check if a command description column exists
                        if ((descColName = typeDefn.getColumnNameByInputType(DefaultInputType.DESCRIPTION)) != null)
                        {
                            // Get the command description column
                            commandDescriptionIndex = typeDefn.getColumnIndexByUserName(descColName);
                        }

                        // Get the list containing command argument column indices for each
                        // argument grouping
                        commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);
                    }

                    // Check if the macro names should be replaced with the corresponding macro
                    // values
                    if (!isReplaceMacro)
                    {
                        // Replace all macros in the table
                        tableInfo.setData(ccddMain.getMacroHandler().replaceAllMacros(tableInfo.getData()));
                    }

                    // Step through each command in the command table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        OrderedJSONObject commandJO = new OrderedJSONObject();
                        String cellValue;

                        // Check if the command name is present. If not then all the command data
                        // on this row is skipped
                        if (!(cellValue = tableInfo.getData()[row][commandNameIndex].toString()).isEmpty())
                        {
                            JSONArray commandArgumentsJA = new JSONArray();

                            // Store the name of the command table from which this command is taken
                            commandJO.put("Command Table Name", commandTable);

                            // Store the command name in the JSON output
                            commandJO.put(typeDefn.getColumnNamesUser()[commandNameIndex],
                                          cellValue);

                            // Check if the command code is present
                            if (!(cellValue = tableInfo.getData()[row][commandCodeIndex].toString()).isEmpty())
                            {
                                // Store the command code in the JSON output
                                commandJO.put(typeDefn.getColumnNamesUser()[commandCodeIndex],
                                              cellValue);
                            }

                            // Check if the command description is present
                            if (commandDescriptionIndex != -1
                                && !(cellValue = tableInfo.getData()[row][commandDescriptionIndex].toString()).isEmpty())
                            {
                                // Store the command description in the JSON output
                                commandJO.put(typeDefn.getColumnNamesUser()[commandDescriptionIndex],
                                              cellValue);
                            }

                            // Step through each command argument associated with the current
                            // command row
                            for (AssociatedColumns cmdArgument : commandArguments)
                            {
                                OrderedJSONObject commandArgumentJO = new OrderedJSONObject();

                                // Check if the command argument name column has a value. If not,
                                // all associated argument values are skipped
                                if (!(cellValue = tableInfo.getData()[row][cmdArgument.getName()].toString()).isEmpty())
                                {
                                    // Store the command argument name in the JSON output
                                    commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getName()],
                                                          cellValue);

                                    // Check if the command argument data type column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getDataType()].toString()).isEmpty())
                                    {
                                        // Store the data type in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getDataType()],
                                                              cellValue);
                                    }

                                    // Check if the command argument array size column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getArraySize()].toString()).isEmpty())
                                    {
                                        // Store the array size in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getArraySize()],
                                                              cellValue);
                                    }

                                    // Check if the command argument bit length column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getBitLength()].toString()).isEmpty())
                                    {
                                        // Store the bit length in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getBitLength()],
                                                              cellValue);
                                    }

                                    // Check if the command argument enumeration column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getEnumeration()].toString()).isEmpty())
                                    {
                                        // Store the enumeration in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getEnumeration()],
                                                              cellValue);
                                    }

                                    // Check if the command argument minimum column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getMinimum()].toString()).isEmpty())
                                    {
                                        // Store the minimum value in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getMinimum()],
                                                              cellValue);
                                    }

                                    // Check if the command argument maximum column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getMaximum()].toString()).isEmpty())
                                    {
                                        // Store the maximum value in the JSON output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getMaximum()],
                                                              cellValue);
                                    }

                                    // Step through any other columns associated with this command
                                    // argument
                                    for (int otherArg : cmdArgument.getOther())
                                    {
                                        // Check if the other argument column has a value
                                        if (!(cellValue = tableInfo.getData()[row][otherArg].toString()).isEmpty())
                                        {
                                            // Store the value in the JSON output
                                            commandArgumentJO.put(typeDefn.getColumnNamesUser()[otherArg],
                                                                  cellValue);
                                        }
                                    }
                                }

                                // Store the command arguments in the JSON array
                                commandArgumentsJA.add(commandArgumentJO);
                            }

                            // Check if the command has an argument
                            if (!commandArgumentsJA.isEmpty())
                            {
                                // Store the command arguments in the JSON output
                                commandJO.put("Arguments", commandArgumentsJA);
                            }
                        }

                        // Add the command to the JSON array
                        commandsJA.add(commandJO);
                    }
                }
            }
        }

        return commandsJA.toString();
    }

    /**********************************************************************************************
     * Get the table type definitions
     *
     * @return JSON encoded string containing the table type definitions; an empty object if no
     *         table type definition exists
     *********************************************************************************************/
    private String getTableTypeDefinitions()
    {
        // Add the table type definitions to the output
        return jsonHandler.getTableTypeDefinitions(null,
                                                   null,
                                                   new OrderedJSONObject())
                          .toJSONString();
    }

    /**********************************************************************************************
     * Get the data type definitions
     *
     * @return JSON encoded string containing the data type definitions; an empty object if no data
     *         type definition exists
     *********************************************************************************************/
    private String getDataTypeDefinitions()
    {
        // Add the data type definitions to the output
        return jsonHandler.getDataTypeDefinitions(null, new OrderedJSONObject()).toJSONString();
    }

    /**********************************************************************************************
     * Get the input type definitions
     *
     * @return JSON encoded string containing the input type definitions; an empty object if no
     *         input type definition exists
     *********************************************************************************************/
    private String getInputTypeDefinitions()
    {
        // Add the input type definitions to the output
        return jsonHandler.getInputTypeDefinitions(null, new OrderedJSONObject()).toJSONString();
    }

    /**********************************************************************************************
     * Get the macro definitions
     *
     * @return JSON encoded string containing the macro definitions; an empty object if no macro
     *         definition exists
     *********************************************************************************************/
    private String getMacroDefinitions()
    {
        // Add the macro definitions to the output
        return jsonHandler.getMacroDefinitions(null, new OrderedJSONObject()).toJSONString();
    }

    /**********************************************************************************************
     * Get the message ID owners, names, and values
     *
     * @return JSON encoded string containing the message ID owners, names, and values; an empty
     *         string if no message IDs or names exist
     *********************************************************************************************/
    private String getMessageIDAndNames()
    {
        // Get the message ID owners, names, and values to the output
        return jsonHandler.getMessageIDAndNames().toJSONString();
    }
}
