/**
 * CFS Command & Data Dictionary web data access handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_DATA_FIELD_IDENT;

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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import CCDD.CcddClasses.ArrayListMultiple;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableMembers;
import CCDD.CcddConstants.CopyTableEntry;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.JSONTags;
import CCDD.CcddConstants.TableMemberType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary web data access handler class
 *****************************************************************************/
public class CcddWebDataAccessHandler extends AbstractHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddEventLogDialog eventLog;
    private CcddRateParameterHandler rateHandler;
    private CcddLinkHandler linkHandler;
    private TableTreeType tableTreeType;
    private final CcddJSONHandler jsonHandler;

    // List containing the table member information
    private List<TableMembers> tableMembers;

    // List for storing telemetered variable path and name pairs
    private List<String[]> telemetryInformation;

    // List for storing rate information from the custom values table
    private List<String[]> rateValues;

    // List for storing enumeration information from the custom values table
    private List<String[]> enumerationValues;

    // List of unique enumeration column names
    private List<String> enumColumnNames;

    // List of table names belonging to a group (or application)
    private List<String> groupTables;

    // Telemetry command output
    private JSONArray telemetryJA;

    /**************************************************************************
     * Web data access handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddWebDataAccessHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();
        jsonHandler = new CcddJSONHandler(ccddMain, ccddMain.getMainFrame());
    }

    /**************************************************************************
     * Set the reference to the rate parameter handler class
     *************************************************************************/
    protected void setHandlers()
    {
        rateHandler = ccddMain.getRateParameterHandler();
    }

    /**************************************************************************
     * Handle a web-based request
     *************************************************************************/
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

        // Process the request and get the information encoded as a JSON
        // string. The leading '/' is removed from the request path
        String jsonResponse = getQueryResults(target.replaceFirst("^/", ""),
                                              query);

        // Check if the specified content was loaded successfully
        if (jsonResponse != null)
        {
            // Set the flag indicating the response is valid
            response.setStatus(HttpServletResponse.SC_OK);
        }
        // The request failed
        else
        {
            // Set the flag indicating the response is invalid and return an
            // empty string
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
                                  "Cannot respond to web server request; cause '"
                                      + ioe.getMessage()
                                      + "'",
                                  "<html><b>Cannot respond to web server request");
        }
    }

    /**************************************************************************
     * Extract the parts from the supplied text string, separating the string
     * at the specified separation character(s) and removing any leading and
     * trailing white space characters from each part
     * 
     * @param text
     *            text string to separate
     * 
     * @param separator
     *            separation character(s). The is padded with a check for white
     *            space characters in order to remove them
     * 
     * @param limit
     *            maximum number of parts to separate the text into. This is
     *            the number of parts returned, with any missing parts returned
     *            as blanks
     * 
     * @return Array containing the specified number of parts of the text
     *         string. A part is returned as blank if the supplied text does
     *         not contain the separator
     *************************************************************************/
    private String[] getParts(String text, String separator, int limit)
    {
        int index = 0;

        // Create storage for the array and initialize the contents to blanks
        String[] parts = new String[limit];
        Arrays.fill(parts, "");

        // Extract the parts of the input string using the supplied
        // separator and limit, then step through each part
        for (String part : text.trim().split("\\s*"
                                             + Pattern.quote(separator)
                                             + "\\s*",
                                             limit))
        {
            // Store the value in the array
            parts[index] = part;
            index++;
        }

        return parts;
    }

    /**************************************************************************
     * Process the web query and return the results encoded as a JSON string.
     * The query is in the form [server]:[port]/[component]?[attribute][=name]
     * 
     * @param component
     *            component for which to request data
     * 
     * @param item
     *            item in the component
     * 
     * @return Query results encoded as a JSON string
     *************************************************************************/
    private String getQueryResults(String component, String item)
    {
        String response = null;

        // Log the web server request
        eventLog.logEvent(EventLogMessageType.SERVER_MSG,
                          "Request component '"
                              + component
                              + "' item '"
                              + item
                              + "'");

        try
        {
            // Extract the item's attribute and name
            String[] attributeAndName = getParts(item, "=", 2);

            // Check if this is a table-related request
            if (component.equals("table")
                || component.equals("proto_table")
                || component.equals("instance_table"))
            {
                // Set the tree type (instance, prototype, or both) based on
                // the command
                tableTreeType = component.equals("table")
                                                         ? TableTreeType.PROTOTYPE_AND_INSTANCE
                                                         : (component.equals("proto_table")
                                                                                           ? TableTreeType.PROTOTYPE_ONLY
                                                                                           : TableTreeType.INSTANCE_ONLY);

                // Use the attribute to determine the request
                switch (attributeAndName[0])
                {
                    case "all":
                    case "":
                        // Get the name, type, description, data, and data
                        // fields for the specified table (or all tables if no
                        // table name is specified)
                        response = getTableInformation(attributeAndName[1],
                                                       new CcddFieldHandler(ccddMain,
                                                                            null,
                                                                            ccddMain.getMainFrame()));
                        break;

                    case "data":
                        // Get the data for the specified table (or all tables
                        // if no table name is specified)
                        response = getTableData(attributeAndName[1], true);
                        break;

                    case "description":
                        // Get the description for the specified table (or all
                        // tables if no table name is specified)
                        response = getTableDescription(attributeAndName[1]);
                        break;

                    case "fields":
                        // Get a data field information for the specified table
                        // (or all tables if no table name is specified)
                        response = getTableFields(attributeAndName[1],
                                                  true,
                                                  new CcddFieldHandler(ccddMain,
                                                                       null,
                                                                       ccddMain.getMainFrame()));
                        break;

                    case "names":
                        // Get the names of the data tables of the specified
                        // type (or all tables if no table name is specified)
                        response = getTableNames(attributeAndName[1]);
                        break;

                    case "size":
                        // Get the size of the specified structure data table
                        // (or all structure tables if no table name is
                        // specified)
                        response = getStructureSize(attributeAndName[1]);
                        break;

                    default:
                        throw new CCDDException("unrecognized table attribute '"
                                                + attributeAndName[0]
                                                + "'");
                }
            }
            // Check if this is a group or application related request
            else if (component.equals("group")
                     || component.equals("application"))
            {
                // Set the flag to true if this request only applies to groups
                // that represent an application
                boolean applicationOnly = component.equals("application");

                // Set the name based on if a group or application is requested
                String name = applicationOnly ? "application" : "group";

                // Use the attribute to determine the request
                switch (attributeAndName[0])
                {
                    case "all":
                    case "":
                        // Get the name, application status, description, and
                        // data fields for the specified group (or all groups
                        // if no group name is specified)
                        response = getGroupInformation(attributeAndName[1],
                                                       applicationOnly,
                                                       new CcddGroupHandler(ccddMain,
                                                                            ccddMain.getMainFrame()),
                                                       new CcddFieldHandler(ccddMain,
                                                                            null,
                                                                            ccddMain.getMainFrame()));
                        break;

                    case "tables":
                        // Get the tables for the specified group (or all
                        // groups if no group name is specified)
                        response = getGroupTables(attributeAndName[1],
                                                  applicationOnly,
                                                  true,
                                                  new CcddGroupHandler(ccddMain,
                                                                       ccddMain.getMainFrame()));
                        break;

                    case "description":
                        // Get the description for the specified group (or all
                        // groups if no group name is specified)
                        response = getGroupDescription(attributeAndName[1],
                                                       applicationOnly,
                                                       true,
                                                       new CcddGroupHandler(ccddMain,
                                                                            ccddMain.getMainFrame()));
                        break;

                    case "fields":
                        // Get a data field information for the specified group
                        // (or all groups if no group name is specified)
                        response = getGroupFields(attributeAndName[1],
                                                  applicationOnly,
                                                  true,
                                                  new CcddGroupHandler(ccddMain,
                                                                       ccddMain.getMainFrame()),
                                                  new CcddFieldHandler(ccddMain,
                                                                       null,
                                                                       ccddMain.getMainFrame()));
                        break;

                    case "names":
                        // Get all group names
                        response = getGroupNames(applicationOnly,
                                                 new CcddGroupHandler(ccddMain,
                                                                      ccddMain.getMainFrame()));
                        break;

                    default:
                        throw new CCDDException("unrecognized "
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
                        response = getApplicationSchedulerData();
                        break;

                    default:
                        throw new CCDDException("unrecognized scheduler attribute '"
                                                + attributeAndName[0]
                                                + "'");
                }
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
            // Check if this is a macro definition request
            else if (component.equals("macro"))
            {
                // Get the macro definitions
                response = getMacroDefinitions();
            }
            // Check if this is a web server shutdown request
            else if (component.equals("shutdown"))
            {
                // Execute the command to exit the application on a separate
                // thread so that the thread it's closing isn't the one that's
                // running
                new Thread()
                {
                    /**********************************************************
                     * Exit the application
                     *********************************************************/
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
                throw new CCDDException("unrecognized request component '"
                                        + component
                                        + "'");
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the web server request is invalid
            eventLog.logFailEvent(ccddMain.getMainFrame(),
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
            // Remove the extraneous escape (\) characters that the JSON
            // encoder inserts into the string
            response = response.replaceAll("\\\\\\\\",
                                           "\\\\").replaceAll("\\\\/", "/");
        }

        return response;
    }

    /**************************************************************************
     * Get a list containing the names and paths of every data table. The tree
     * type (prototype only or instances only) is determined by the command
     *
     * @return List containing the names and paths of every data table
     *************************************************************************/
    private List<String> getTableList()
    {
        // Build the table tree, including the primitive variables
        CcddTableTreeHandler allTableTree = new CcddTableTreeHandler(ccddMain,
                                                                     tableTreeType,
                                                                     ccddMain.getMainFrame());

        // Convert the table tree to a list of table paths
        return allTableTree.getTableTreePathList(null);
    }

    /**************************************************************************
     * Get the table name and show macros flag status
     * 
     * @param tableName
     *            table name (blank if all tables are included), optionally
     *            followed by the macro indicator in the format [table
     *            name][;indicator]
     * 
     * @return String array where the first member is the table name (blank if
     *         all tables are included) and the second member is the status of
     *         the show macro names flag ('true' or 'false')
     *************************************************************************/
    private String[] getTableNameAndShowMacroStatus(String tableName)
    {
        // Initialize the flag that indicates if macro names should be
        // displayed in place of the corresponding values
        boolean showMacroNames = false;

        // Separate the table name from the macro indicator
        String[] nameAndMacro = tableName.split(";");

        // Check if the macro indicator is present
        if (nameAndMacro.length == 2)
        {
            // Remove any leading or trailing white space characters
            nameAndMacro[1] = nameAndMacro[1].trim();

            // Set the flag if the text indicates the macro names should be
            // displayed
            showMacroNames = nameAndMacro[1].equalsIgnoreCase("macro")
                             || nameAndMacro[1].equalsIgnoreCase("macros");
        }

        return new String[] {nameAndMacro[0], String.valueOf(showMacroNames)};
    }

    /**************************************************************************
     * Get the data for the specified data table, or for all data tables if no
     * table name is provided. Extract the indicator for replacing the macro
     * names with the corresponding macro values
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. Blank to return the
     *            data for all tables
     * 
     * @param getDescription
     *            true to get the table description when loading the table data
     * 
     * @return JSON encoded string containing the specified table cell data;
     *         null if a table name is specified and the table doesn't exist or
     *         if no data tables exist in the project database, or blank if the
     *         specified table has no data or none of the tables have data.
     *         Empty cells are included
     *************************************************************************/
    private String getTableData(String tableName,
                                boolean getDescription) throws CCDDException
    {
        // Separate the show macros flag from the table name
        String[] nameAndMacro = getTableNameAndShowMacroStatus(tableName);

        return getTableData(nameAndMacro[0],
                            getDescription,
                            Boolean.valueOf(nameAndMacro[1]));
    }

    /**************************************************************************
     * Get the data for the specified data table, or for all data tables if no
     * table name is provided
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. Blank to return the
     *            data for all tables
     * 
     * @param getDescription
     *            true to get the table description when loading the table data
     * 
     * @param showMacroNames
     *            false to display the macro values in place of the
     *            corresponding macro names; true to display the macro names
     * 
     * @return JSON encoded string containing the specified table cell data;
     *         null if a table name is specified and the table doesn't exist or
     *         if no data tables exist in the project database, or blank if the
     *         specified table has no data or none of the tables have data.
     *         Empty cells are included
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableData(String tableName,
                                boolean getDescription,
                                boolean showMacroNames) throws CCDDException
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
                        // Get the data for this table as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getTableData(name,
                                                                 true,
                                                                 showMacroNames)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing table data");
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
            JSONObject tableNameAndData = jsonHandler.getTableData(tableName,
                                                                   getDescription,
                                                                   showMacroNames,
                                                                   new JSONObject());

            // Check if the table data loaded successfully
            if (tableNameAndData != null)
            {
                // Add the table name. If the table has no data then the table
                // data shows empty
                tableNameAndData.put(JSONTags.TABLE_NAME.getTag(), tableName);
                response = tableNameAndData.toString();
            }
        }

        return response;
    }

    /**************************************************************************
     * Get the description for the specified table, or all tables with a
     * description if no table name is provided
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. If blank then the
     *            description for every data table with a description is
     *            returned
     * 
     * @return JSON encoded string containing the specified table's
     *         description; null if the specified table doesn't exist or the
     *         project has no data tables
     *************************************************************************/
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
                JSONObject tableNameAndDesc = new JSONObject();
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
                JSONObject tableNameAndDesc;
                JSONArray responseJA = new JSONArray();

                // Get the description for every table that has a description
                String[][] namePathAndDesc = dbTable.queryTableDescriptions(ccddMain.getMainFrame());

                // Step through each table name
                for (String name : tableNames)
                {
                    String description = "";

                    // Step through each table name in the array of tables with
                    // descriptions
                    for (int index = 0; index < namePathAndDesc.length; index++)
                    {
                        // Check if the name matches the name and path from the
                        // description array
                        if (name.equalsIgnoreCase(namePathAndDesc[index][0]))
                        {
                            // Store the description and stop searching
                            description = namePathAndDesc[index][1];
                            break;
                        }
                    }

                    // Store the table name and description, and add it to the
                    // array
                    tableNameAndDesc = new JSONObject();
                    tableNameAndDesc.put(JSONTags.TABLE_NAME.getTag(), name);
                    tableNameAndDesc.put(JSONTags.TABLE_DESCRIPTION.getTag(),
                                         description);
                    responseJA.add(tableNameAndDesc);
                }

                response = responseJA.toString();
            }
        }

        return response;
    }

    /**************************************************************************
     * Get the data field information for the specified table, or for all
     * tables if no table name is provided
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. If blank then every
     *            data table's data fields are returned
     * 
     * @param checkExists
     *            true to check if the specified table exists in the project
     *            database
     * 
     * @param fieldHandler
     *            data field handler
     * 
     * @return JSON encoded string containing the specified table's data
     *         fields; null if the table doesn't exist or if the project
     *         database contains no data tables
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableFields(String tableName,
                                  boolean checkExists,
                                  CcddFieldHandler fieldHandler) throws CCDDException
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
                    // Check if the table name isn't already in the list and
                    // that this is not a table type or group data field
                    if (!tableNames.contains(fieldInfo.getOwnerName())
                        && !fieldInfo.getOwnerName().startsWith(TYPE_DATA_FIELD_IDENT)
                        && !fieldInfo.getOwnerName().startsWith(GROUP_DATA_FIELD_IDENT))
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
                        // Get the fields for this table as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getTableFields(name,
                                                                   false,
                                                                   fieldHandler)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing table data fields");
                    }
                }

                // Add the table fields to the response
                response = responseJA.toString();
            }
        }
        // A table name is provided. Check if the table existence should be
        // ignored, or else if the table exists in the database
        else if (!checkExists
                 || dbTable.isTableExists(tableName, ccddMain.getMainFrame()))
        {
            // Add the table name and data field information to the output
            JSONObject tableNameAndFields = new JSONObject();
            tableNameAndFields.put(JSONTags.TABLE_NAME.getTag(), tableName);
            tableNameAndFields = jsonHandler.getTableFields(tableName,
                                                            fieldHandler,
                                                            tableNameAndFields);
            response = tableNameAndFields.toString();
        }

        return response;
    }

    /**************************************************************************
     * Get the names of all tables of the specified table type, or all tables
     * names and their types if no table type is provided
     * 
     * @param tableType
     *            table type. The type is case insensitive. If blank then every
     *            data table and its type is returned
     * 
     * @return JSON encoded string containing all table names of the specified
     *         table type; blank if the type is valid but no tables of the type
     *         exist, and null if the specified table type doesn't exist or if
     *         no data tables exist in the project database
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableNames(String tableType)
    {
        String response = null;

        // Get an array containing the data types and names for all prototype
        // tables
        String[][] tableTypesAndProtoNames = dbTable.queryTableAndTypeList(ccddMain.getMainFrame());
        // TODO SHOULD THIS COMBINE STRUCTURE TYPES (AND COMMAND TYPES)? IF SO,
        // USE dbTable.getTablesOfType(tableType) TO GET THE TABLE NAMES. WATCH
        // FOR OTHER IMPACTS IF THIS IS DONE

        // Check that at least one data table exists, and that either all
        // tables are requested or that the specified table type exists
        if (tableTypesAndProtoNames.length != 0)
        {
            JSONArray responseJA = new JSONArray();
            JSONObject responseJO = null;

            // Get the list of table types
            String[] tableTypes = dbTable.queryTableTypesList(ccddMain.getMainFrame());

            // Set the flag to indicate if only one table type is specified or
            // exists
            boolean isSingle = !tableType.isEmpty() || tableTypes.length == 1;

            // Step through each valid table type
            for (String type : tableTypes)
            {
                // Check if no table type is specified (i.e., return all types)
                // or if the type matches the one specified
                if (tableType.isEmpty() || type.equalsIgnoreCase(tableType))
                {
                    // Check if this is the first valid table type match
                    if (response == null)
                    {
                        // Initialize the output; this causes a blank to be
                        // returned if the type is valid but no tables of the
                        // type exist
                        response = "";
                    }

                    responseJO = new JSONObject();
                    JSONArray namesJA = new JSONArray();

                    // Step through each table name
                    for (String tableName : getTableList())
                    {
                        // Step through each prototype table name/type
                        for (int index = 0; index < tableTypesAndProtoNames.length; index++)
                        {
                            // Check if the root table name matches that in the
                            // types list
                            if (type.equalsIgnoreCase(tableTypesAndProtoNames[index][0])
                                && tableName.matches("^"
                                                     + tableTypesAndProtoNames[index][1]
                                                     + "(,.*|$)"))
                            {
                                // Add the table to the list for this table
                                // type
                                namesJA.add(tableName);
                            }
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

                    // More than one table is in the response; add the type and
                    // names to the array
                    responseJA.add(responseJO);
                }
            }

            // Check if the specified table type exists, or any type exists if
            // none is specified
            if (response != null)
            {
                // Set the response based of if a single or multiple types are
                // included in the response. If single then the JSON object is
                // used to prevent the extraneous brackets from enclosing the
                // response
                response = (isSingle)
                                     ? responseJO.toString()
                                     : responseJA.toString();
            }
        }

        return response;
    }

    /**************************************************************************
     * Get the number of bytes for the specified structure table, or for all
     * structure tables if no table is specified
     * 
     * @param tableName
     *            structure table name
     * 
     * @return JSON encoded string containing the structure table name(s) and
     *         corresponding size(s) in bytes; null if a table name is
     *         specified and the table doesn't exist or isn't a structure, or
     *         if no structure tables exist in the project database
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getStructureSize(String tableName)
    {
        String response = null;
        JSONObject responseJO = null;
        JSONArray responseJA = new JSONArray();

        // Get an array containing the data types and names for all prototype
        // tables
        String[][] tableTypesAndProtoNames = dbTable.queryTableAndTypeList(ccddMain.getMainFrame());

        // Flag indicating if a single table is requested
        boolean isSingle = !tableName.isEmpty();

        // Step through each prototype table
        for (String[] table : tableTypesAndProtoNames)
        {
            // Check if all structure tables are requested or if the table name
            // matches the specified name
            if (tableName.isEmpty() || tableName.equalsIgnoreCase(table[1]))
            {
                // Get the table's type definition
                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(table[0]);

                // Check if the table represents a structure
                if (typeDefn.isStructure())
                {
                    responseJO = new JSONObject();

                    // Check if the link handler exists
                    if (linkHandler == null)
                    {
                        // Create a link handler
                        linkHandler = new CcddLinkHandler(ccddMain, ccddMain.getMainFrame());
                    }

                    // Store the table name and its size in bytes
                    responseJO.put(JSONTags.TABLE_NAME.getTag(), table[1]);
                    responseJO.put(JSONTags.TABLE_BYTE_SIZE.getTag(),
                                   linkHandler.getDataTypeSizeInBytes(table[1]));

                    // Check if only one table is being processed
                    if (isSingle)
                    {
                        // Stop searching
                        break;
                    }

                    // More than one table is in the response; add the name and
                    // size to the array
                    responseJA.add(responseJO);
                }
            }
        }

        // Check if the specified table exists and is a structure, or any
        // structure table exists if none is specified
        if (responseJO != null)
        {
            // Set the response based of if a single or multiple types are
            // included in the response. If single then the JSON object is
            // used to prevent the extraneous brackets from enclosing the
            // response
            response = (isSingle)
                                 ? responseJO.toString()
                                 : responseJA.toString();
        }

        return response;
    }

    /**************************************************************************
     * Get the type, description, size, data, and data fields for the specified
     * data table. Extract the indicator for replacing the macro names with the
     * corresponding macro values
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. Blank to return the
     *            data for all tables
     * 
     * @param fieldHandler
     *            data field handler
     * 
     * @return JSON encoded string containing the specified table information;
     *         null if a table name is specified and the table doesn't exist or
     *         if no data tables exist in the project database
     *************************************************************************/
    private String getTableInformation(String tableName,
                                       CcddFieldHandler fieldHandler) throws CCDDException
    {
        // Separate the show macros flag from the table name
        String[] nameAndMacro = getTableNameAndShowMacroStatus(tableName);

        return getTableInformation(nameAndMacro[0],
                                   fieldHandler,
                                   Boolean.valueOf(nameAndMacro[1]));
    }

    /**************************************************************************
     * Get the type, description, size, data, and data fields for the specified
     * data table
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. Blank to return the
     *            data for all tables
     * 
     * @param fieldHandler
     *            data field handler
     * 
     * @param showMacroNames
     *            false to display the macro values in place of the
     *            corresponding macro names; true to display the macro names
     * 
     * @return JSON encoded string containing the specified table information;
     *         null if a table name is specified and the table doesn't exist or
     *         if no data tables exist in the project database
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTableInformation(String tableName,
                                       CcddFieldHandler fieldHandler,
                                       boolean showMacroNames) throws CCDDException
    {
        JSONArray responseJA = new JSONArray();
        JSONParser parser = new JSONParser();
        String response = null;

        // Check if no table name is provided (i.e., get the information for
        // all tables)
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
                        // Get the fields for this table as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getTableInformation(name,
                                                                        fieldHandler,
                                                                        showMacroNames)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing table information");
                    }
                }

                // Convert the response array to a JSON string
                response = responseJA.toString();
            }
        }
        // A table name is provided
        {
            // Get the tables information
            JSONObject tableInfoJO = jsonHandler.getTableInformation(tableName,
                                                                     fieldHandler,
                                                                     showMacroNames);

            // Check if the table loaded successfully
            if (tableInfoJO != null)
            {
                // Add the table's information to the output
                response = tableInfoJO.toString();
            }
        }

        return response;
    }

    /**************************************************************************
     * Get the tables associated with the specified group or application, or
     * for all groups/applications if no group name is provided
     * 
     * @param groupName
     *            group name. If blank then every group's (application's)
     *            descriptions are returned
     * 
     * @param applicationOnly
     *            true if only groups that represent applications should be
     *            processed
     * 
     * @param includeNameTag
     *            true to include the group name item
     * 
     * @param groupHandler
     *            group handler
     * 
     * @return JSON encoded string containing the specified group's
     *         (application's) table members; null if the specified
     *         group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application
     *         has no table member or if all groups/applications are requested
     *         but none have a table member
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupTables(String groupName,
                                  boolean applicationOnly,
                                  boolean includeNameTag,
                                  CcddGroupHandler groupHandler) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all
        // groups/applications)
        if (groupName.isEmpty())
        {
            JSONArray responseJA = new JSONArray();
            JSONParser parser = new JSONParser();

            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                response = "";

                // Step through each group/application name
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the data for this group as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getGroupTables(name,
                                                                   applicationOnly,
                                                                   true,
                                                                   groupHandler)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing "
                                                + (applicationOnly
                                                                  ? "application"
                                                                  : "group")
                                                + " tables");
                    }
                }

                // Convert the response array to a JSON string
                response = responseJA.toString();
            }
        }
        // A group name is provided
        else
        {
            // Get the group information for the specified group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

            // Check if the group exists and that either all groups are
            // requested or else an application is requested and this group
            // represents an application
            if (groupInfo != null
                && (!applicationOnly
                || groupInfo.isApplication()))
            {
                JSONArray dataJA = new JSONArray();

                // Get the list of the group's tables
                List<String> tables = groupInfo.getTables();

                // Step through each table
                for (String table : tables)
                {
                    // Add the table to the array
                    dataJA.add(table);
                }

                // Add the group name and description to the list. An array is
                // used to preserve the order of the items
                JSONObject groupNameAndTable;

                // Add the group tables. If the group has no tables then the
                // table data is blank
                groupNameAndTable = new JSONObject();

                // Check if the group name is to be included
                if (includeNameTag)
                {
                    // Add the group name and tables to the output
                    groupNameAndTable.put((applicationOnly
                                                          ? JSONTags.APPLICATION_NAME.getTag()
                                                          : JSONTags.GROUP_NAME.getTag()),
                                          groupName);
                    groupNameAndTable.put((applicationOnly
                                                          ? JSONTags.APPLICATION_TABLE.getTag()
                                                          : JSONTags.GROUP_TABLE.getTag()),
                                          dataJA);
                    response = groupNameAndTable.toString();
                }
                // Don't include the name and table tags
                else
                {
                    // Add the tables to the output
                    response = dataJA.toString();
                }
            }
        }

        return response;
    };

    /**************************************************************************
     * Get the description for the specified group or application, or all
     * groups/applications with a description if no group name is provided
     * 
     * @param groupName
     *            group name. If blank then every group's (application's)
     *            descriptions are returned
     * 
     * @param applicationOnly
     *            true if only groups that represent applications should be
     *            processed
     * 
     * @param groupHandler
     *            group handler
     * 
     * @return JSON encoded string containing the specified group's
     *         (application's) description; null if the specified
     *         group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application
     *         has no description or if all groups/applications are requested
     *         but none have a description
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupDescription(String groupName,
                                       boolean applicationOnly,
                                       boolean includeNameTag,
                                       CcddGroupHandler groupHandler) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all
        // groups/applications)
        if (groupName.isEmpty())
        {
            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                JSONArray responseJA = new JSONArray();
                JSONParser parser = new JSONParser();
                response = "";

                // Step through each group/application name
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the description for this group as a JSON string,
                        // then format it as a JSON object so that is can be
                        // added to the response array. This is needed to get
                        // the brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getGroupDescription(name,
                                                                        applicationOnly,
                                                                        true,
                                                                        groupHandler)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing "
                                                + (applicationOnly
                                                                  ? "application"
                                                                  : "group")
                                                + " description");
                    }

                    // Convert the response array to a JSON string
                    response = responseJA.toString();
                }
            }
        }
        // A group name is provided
        else
        {
            // Get the group information for the specified group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

            // Check if the group exists and that either all groups are
            // requested or else an application is requested and this group
            // represents an application
            if (groupInfo != null
                && (!applicationOnly
                || groupInfo.isApplication()))
            {
                JSONObject groupNameAndDesc = new JSONObject();

                // Get the description. If no description exists then use a
                // blank
                response = groupInfo.getDescription() != null
                                                             ? groupInfo.getDescription()
                                                             : "";

                // Check if the group name is to be included
                if (includeNameTag)
                {
                    // Add the group name and description to the output
                    groupNameAndDesc.put((applicationOnly
                                                         ? JSONTags.APPLICATION_NAME.getTag()
                                                         : JSONTags.GROUP_NAME.getTag()),
                                         groupName);
                    groupNameAndDesc.put((applicationOnly
                                                         ? JSONTags.APPLICATION_DESCRIPTION.getTag()
                                                         : JSONTags.GROUP_DESCRIPTION.getTag()),
                                         response);
                    response = groupNameAndDesc.toString();
                }
            }
        }

        return response;
    };

    /**************************************************************************
     * Get the data field information for the specified group or application,
     * or for all groups/applications if no group name is provided
     * 
     * @param groupName
     *            group name. If blank then every data table's data fields are
     *            returned
     * 
     * @param applicationOnly
     *            true if only groups that represent applications should be
     *            processed
     * 
     * @param includeNameTag
     *            true to include the group name item and data field tag
     * 
     * @param groupHandler
     *            group handler
     * 
     * @param fieldHandler
     *            data field handler
     * 
     * @return JSON encoded string containing the specified group's data
     *         fields; null if the group doesn't exist or if the project
     *         database contains no groups
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupFields(String groupName,
                                  boolean applicationOnly,
                                  boolean includeNameTag,
                                  CcddGroupHandler groupHandler,
                                  CcddFieldHandler fieldHandler) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all
        // groups/applications)
        if (groupName.isEmpty())
        {
            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                JSONArray responseJA = new JSONArray();
                JSONParser parser = new JSONParser();
                response = "";

                // Step through each group/application name
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the fields for this group as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getGroupFields(name,
                                                                   applicationOnly,
                                                                   true,
                                                                   groupHandler,
                                                                   fieldHandler)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing "
                                                + (applicationOnly
                                                                  ? "application"
                                                                  : "group")
                                                + " data fields");
                    }
                }

                // Add the table fields to the response
                response = responseJA.toString();
            }
        }
        // A group name is provided
        else
        {
            // Get the group information for the specified group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

            // Check if the group exists and that either all groups are
            // requested or else an application is requested and this group
            // represents an application
            if (groupInfo != null
                && (!applicationOnly
                || groupInfo.isApplication()))
            {
                // Build the field information list for this group
                fieldHandler.buildFieldInformation(CcddFieldHandler.getFieldGroupName(groupName));

                // Check if the group has any fields
                if (!fieldHandler.getFieldInformation().isEmpty())
                {
                    // Get the group data fields (extract the data field array
                    // from the table field tag)
                    JSONObject fieldsJO = jsonHandler.getTableFields(CcddFieldHandler.getFieldGroupName(groupName),
                                                                     fieldHandler,
                                                                     new JSONObject());
                    JSONArray groupFieldsJA = (JSONArray) fieldsJO.get(JSONTags.TABLE_FIELD.getTag());

                    // Check if the name tag is to be included
                    if (includeNameTag)
                    {
                        // Add the group name and group data fields to the
                        // output
                        JSONObject groupNameAndFields = new JSONObject();
                        groupNameAndFields.put((applicationOnly
                                                               ? JSONTags.APPLICATION_NAME.getTag()
                                                               : JSONTags.GROUP_NAME.getTag()),
                                               groupName);
                        groupNameAndFields.put((applicationOnly
                                                               ? JSONTags.APPLICATION_FIELD.getTag()
                                                               : JSONTags.GROUP_FIELD.getTag()),
                                               groupFieldsJA);
                        response = groupNameAndFields.toString();
                    }
                    // Don't include the name tag
                    else
                    {
                        // Add the data fields to the output
                        response = groupFieldsJA.toString();
                    }
                }
            }
        }

        return response;
    };

    /**************************************************************************
     * Get the names of all groups or applications
     * 
     * @param applicationOnly
     *            true if only groups that represent applications should be
     *            processed
     * 
     * @param groupHandler
     *            group handler
     * 
     * @return JSON encoded string containing the all group/application names;
     *         null if no groups/applications exist in the project database
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupNames(boolean applicationOnly,
                                 CcddGroupHandler groupHandler)
    {
        String response = null;

        // Get an array containing all group/application names
        String[] groupNames = groupHandler.getGroupNames(applicationOnly);

        // Check if any groups/applications exist
        if (groupNames.length != 0)
        {
            response = "";
            JSONObject responseJO = new JSONObject();
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

    /**************************************************************************
     * Get the description, associated table(s), and data fields for the
     * specified group or application
     * 
     * @param groupName
     *            group name. If blank then every data table's data fields are
     *            returned
     * 
     * @param applicationOnly
     *            true if only groups that represent applications should be
     *            processed
     * 
     * @param groupHandler
     *            group handler
     * 
     * @param fieldHandler
     *            field handler
     * 
     * @return JSON encoded string containing the specified group/application
     *         information; null if a group name is specified and the
     *         group/application doesn't exist or if no groups/applications
     *         exist in the project database
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getGroupInformation(String groupName,
                                       boolean applicationOnly,
                                       CcddGroupHandler groupHandler,
                                       CcddFieldHandler fieldHandler) throws CCDDException
    {
        JSONArray responseJA = new JSONArray();
        JSONParser parser = new JSONParser();
        String response = null;
        String groupType;
        String nameTag;
        String descriptionTag;
        String dataFieldTag;
        String tableTag;

        // Check if only groups that represent applications are to be processed
        if (applicationOnly)
        {
            groupType = "application";
            nameTag = JSONTags.APPLICATION_NAME.getTag();
            descriptionTag = JSONTags.APPLICATION_DESCRIPTION.getTag();
            dataFieldTag = JSONTags.APPLICATION_FIELD.getTag();
            tableTag = JSONTags.APPLICATION_TABLE.getTag();
        }
        // Process groups of any type
        else
        {
            groupType = "group";
            nameTag = JSONTags.GROUP_NAME.getTag();
            descriptionTag = JSONTags.GROUP_DESCRIPTION.getTag();
            dataFieldTag = JSONTags.GROUP_FIELD.getTag();
            tableTag = JSONTags.GROUP_TABLE.getTag();
        }

        // Check if no group name is provided (i.e., get the fields for all
        // groups/applications)
        if (groupName.isEmpty())
        {
            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                response = "";

                // Step though each group/application
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the fields for this group as a JSON string, then
                        // format it as a JSON object so that is can be added
                        // to the response array. This is needed to get the
                        // brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getGroupInformation(name,
                                                                        applicationOnly,
                                                                        groupHandler,
                                                                        fieldHandler)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing "
                                                + groupType
                                                + " information");
                    }
                }

                // Convert the response array to a JSON string
                response = responseJA.toString();
            }
        }
        // A group name is provided
        else
        {
            // Get the group information for the specified group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

            // Check if the group exists and that either all groups are
            // requested or else an application is requested and this group
            // represents an application
            if (groupInfo != null
                && (!applicationOnly
                || groupInfo.isApplication()))
            {
                // Get the groups' table members
                String tables = getGroupTables(groupName,
                                               applicationOnly,
                                               false,
                                               groupHandler);

                // Check if the group/application exists
                if (tables != null)
                {
                    try
                    {
                        // Store the group's name, description, tables, and
                        // data fields
                        JSONObject groupInformation = new JSONObject();
                        groupInformation.put(nameTag, groupName);
                        groupInformation.put(descriptionTag,
                                             getGroupDescription(groupName,
                                                                 applicationOnly,
                                                                 false,
                                                                 groupHandler));
                        groupInformation.put(tableTag, parser.parse(tables));
                        groupInformation.put(dataFieldTag,
                                             parser.parse(getGroupFields(groupName,
                                                                         applicationOnly,
                                                                         false,
                                                                         groupHandler,
                                                                         fieldHandler)));

                        // Convert the response object to a JSON string
                        response = groupInformation.toString();
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("error parsing "
                                                + groupType
                                                + " information");
                    }
                }
            }
        }

        return response;
    };

    /**************************************************************************
     * Get the telemetry scheduler's copy table entries
     * 
     * @param parameters
     *            comma-separated string containing the data stream name,
     *            header size (in bytes), message ID name data field name, and
     *            the optimize result flag ('true' or 'false')
     * 
     * @return JSON encoded string containing the specified copy table entries;
     *         null if the number of parameters or their formats are incorrect
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTelemetrySchedulerData(String parameters) throws CCDDException
    {
        String response = null;

        // Separate the input parameters
        String[] parameter = getParts(parameters, ",", 4);

        // Check if all the input parameters are present and that they're in
        // the expected formats
        if (parameter[1].matches("\\d+") && parameter[3].matches("(true|false)"))
        {
            JSONArray tableJA = new JSONArray();

            // Get the individual parameters and format them if needed
            String streamName = parameter[0];
            int headerSize = Integer.valueOf(parameter[1]);
            String messageIDNameField = parameter[2];
            boolean optimize = Boolean.valueOf(parameter[3]);

            // Get the rate information based on the supplied data stream name
            RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

            // Check if the rate information doesn't exist with this stream
            // name
            if (rateInfo == null)
            {
                throw new CCDDException("unknown data stream name");
            }

            // Create an instance of the copy table handler in order to read
            // the information from the database
            CcddCopyTableHandler copyHandler = new CcddCopyTableHandler(ccddMain);

            // Create the copy table entries based on the supplied parameters
            String[][] copyTable = copyHandler.createCopyTable(new CcddFieldHandler(ccddMain,
                                                                                    null,
                                                                                    ccddMain.getMainFrame()),
                                                               new CcddLinkHandler(ccddMain,
                                                                                   ccddMain.getMainFrame()),
                                                               rateInfo.getRateName(),
                                                               headerSize,
                                                               messageIDNameField,
                                                               optimize);

            // Check if there are any entries in the table
            if (copyTable.length != 0)
            {
                // Step through each row in the table
                for (String[] row : copyTable)
                {
                    JSONObject rowJO = new JSONObject();

                    // Step through each column in the row
                    for (int column = 0; column < row.length; column++)
                    {
                        // Add the copy table value to the array. An array is
                        // used to preserve the order of the items
                        rowJO.put(CopyTableEntry.values()[column].getColumnName(),
                                  row[column]);
                    }

                    // Add the row's copy table values to the table array
                    tableJA.add(rowJO);
                }
            }

            // Store the copy table information
            JSONObject copyJO = new JSONObject();
            copyJO.put(JSONTags.COPY_TABLE_STREAM.getTag(), streamName);
            copyJO.put(JSONTags.COPY_TABLE_HDR_SIZE.getTag(),
                       String.valueOf(headerSize));
            copyJO.put(JSONTags.COPY_TABLE_OPTIMIZE.getTag(),
                       String.valueOf(optimize));
            copyJO.put(JSONTags.COPY_TABLE_DATA.getTag(), tableJA);
            response = copyJO.toString();
        }
        // Invalid parameter format
        else
        {
            throw new CCDDException("parameter type mismatch");
        }

        return response;
    }

    /**************************************************************************
     * Get the application scheduler's schedule table entries
     * 
     * @return JSON encoded string containing the scheduler entries; null if
     *         the number of parameters or their formats are incorrect
     *************************************************************************/
    private String getApplicationSchedulerData()
    {
        String response = null;

        // TODO Need to get the application scheduler working before
        // addressing this

        return response;
    }

    /**************************************************************************
     * Get the path, ITOS mnemonic, data type, bit length, data stream
     * information, and enumeration information for each telemetered variable
     * matching the specified filters
     * 
     * @param telemetryFilter
     *            group (or application) name, data stream name, and/or rate
     *            value filter(s). A table must belong to the specified group
     *            in order for its telemetered variables to be returned; blank
     *            to get all telemetered variables (regardless of group). A
     *            variable must have a rate assigned for the specified data
     *            stream in order to be included; blank to include all data
     *            streams. A variable's rate must match the specified rate in
     *            order to be included; blank to include the variable
     *            regardless of the rate value
     * 
     * @return JSON encoded string containing the path, ITOS mnemonic, data
     *         type, bit length, data stream name(s), and enumeration(s) for
     *         each telemetered variable matching the specified filters; empty
     *         array if no variables are telemetered
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getTelemetryInformation(String telemetryFilter) throws CCDDException
    {
        telemetryJA = new JSONArray();
        telemetryInformation = new ArrayList<String[]>();
        rateValues = new ArrayListMultiple();
        enumerationValues = new ArrayListMultiple();
        String groupFilter = "";
        String streamFilter = "";
        String rateFilter = "";

        // Get the array of data stream names
        String[] dataStreamNames = rateHandler.getDataStreamNames();

        // Check if a filter is specified
        if (!telemetryFilter.isEmpty())
        {
            // Separate the filter parameters
            String[] filter = getParts(telemetryFilter, ",", 3);

            // Check that the number of filter parameters is correct, that the
            // data stream name filter is blank or matches an existing data
            // stream, and that the rate value filter, if present, is in the
            // expected format
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
                    // Create a group handler and extract the table names
                    // belonging to the group
                    CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain,
                                                                         ccddMain.getMainFrame());
                    GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupFilter);

                    // Check if the group doesn't exist
                    if (groupInfo == null)
                    {
                        throw new CCDDException("unrecognized group or stream name, "
                                                + "or invalid rate value format");
                    }

                    // Get the tables associated with the group
                    groupTables = groupInfo.getTables();
                }
            }
            // Incorrect number of filter parameters, unrecognized data stream
            // name, or invalid rate value format
            else
            {
                throw new CCDDException("too many parameters, unrecognized group or "
                                        + "stream name, or invalid rate value format");
            }
        }

        // Step through each data stream
        for (String streamName : dataStreamNames)
        {
            // Check if all data streams are to be loaded, of if a stream
            // filter is supplied that the stream names match
            if (streamFilter.isEmpty() || streamName.equals(streamFilter))
            {
                // Load all references to rate column values from the custom
                // values table that match the rate column name associated with
                // the data stream name
                rateValues.addAll(dbTable.getCustomValues(rateHandler.getRateInformationByStreamName(streamName).getRateName(),
                                                          rateFilter,
                                                          ccddMain.getMainFrame()));
            }
        }

        // Get the list of enumeration column names
        enumColumnNames = ccddMain.getTableTypeHandler().getStructEnumColNames(false);

        // Step through each enumeration column
        for (String enumeration : enumColumnNames)
        {
            // Load all references to enumeration column values from the custom
            // values table that match the enumeration column name
            enumerationValues.addAll(dbTable.getCustomValues(enumeration,
                                                             null,
                                                             ccddMain.getMainFrame()));
        }

        // Get the table member information from the database
        tableMembers = dbTable.loadTableMembers(TableMemberType.INCLUDE_PRIMITIVES,
                                                true,
                                                ccddMain.getMainFrame());

        // Step through each table
        for (TableMembers member : tableMembers)
        {
            // Check if this is a structure type table
            if (ccddMain.getTableTypeHandler().getTypeDefinition(member.getTableType()).isStructure())
            {
                // Get the telemetry variables for this table and its member
                // tables
                getTelemetry(member,
                             member.getTableName(),
                             groupFilter,
                             streamFilter,
                             rateFilter);
            }
        }

        // Create the variable name conversion list
        CcddVariableConversionHandler variableHandler = new CcddVariableConversionHandler(telemetryInformation,
                                                                                          ccddMain.getMacroHandler());

        // Step through each telemetered variable
        for (String[] tlmInfo : telemetryInformation)
        {
            // Get the full variable name, removing the data types and using
            // underscores to separate the variable path members
            String path = variableHandler.getFullVariableName(tlmInfo[0].replaceAll(",[^\\.]*\\.", ","),
                                                              tlmInfo[1].replaceAll("[^\\.]*\\.", ""),
                                                              "_");

            // Step through each telemetered variable JSON object
            for (Object obj : telemetryJA)
            {
                JSONObject telemetryJO = (JSONObject) obj;

                // Check if the JSON object matches the current variable
                if (telemetryJO.get("Structure Table Name").toString().equals(tlmInfo[0])
                    && (telemetryJO.get("Data Type").toString()
                        + "."
                        + telemetryJO.get("Variable Name").toString()).equals(tlmInfo[1]))
                {
                    // Replace the original JSON object with one that includes
                    // the ITOS mnemonic, and stop searching
                    telemetryJA.remove(telemetryJO);
                    telemetryJO.put("ITOS Mnemonic", path);
                    telemetryJA.add(telemetryJO);
                    break;
                }
            }
        }

        return telemetryJA.toString();
    }

    /**************************************************************************
     * Get the JSON array containing the JSON objects that describe each
     * telemetered variable. This is a recursive method
     * 
     * @param thisMember
     *            TableMember class
     * 
     * @param fullTablePath
     *            table path in the format
     *            rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2[,...]]]
     * 
     * @param groupFilter
     *            group (or application) name. A table must belong to the
     *            specified group in order for its telemetered variables to be
     *            returned; blank to get all telemetered variables (regardless
     *            of group)
     * 
     * @param streamFilter
     *            data stream filter name - a variable is output only if it is
     *            in the specified stream; blank to include any stream name
     * 
     * @param rateFilter
     *            sample rate value - - a variable is output only if it has the
     *            specified sample rate; blank to include any sample rate
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private void getTelemetry(TableMembers thisMember,
                              String fullTablePath,
                              String groupFilter,
                              String streamFilter,
                              String rateFilter)
    {
        // Step through each data type referenced by the table member
        for (int memberIndex = 0; memberIndex < thisMember.getDataTypes().size(); memberIndex++)
        {
            // Check if the data type is a primitive
            if (ccddMain.getDataTypeHandler().isPrimitive(thisMember.getDataTypes().get(memberIndex)))
            {
                // Check if all tables' telemetered variables are to be
                // returned, or if a specific group's tables are requested that
                // the table is a member of the group
                if (groupFilter.isEmpty()
                    || groupTables.contains(thisMember.getTableName()))
                {
                    JSONArray streamsJA = new JSONArray();

                    // Step through each data stream
                    for (int rateIndex = 0; rateIndex < thisMember.getRates().get(memberIndex).length; rateIndex++)
                    {
                        // Check if no stream filter is in effect, of if one is
                        // that the stream names match
                        if (streamFilter.isEmpty()
                            || rateHandler.getRateInformation().get(rateIndex).getStreamName().equals(streamFilter))
                        {
                            JSONObject rateJO = new JSONObject();

                            // Get the rate for this parameter from the
                            // prototype table
                            String rate = thisMember.getRates().get(memberIndex)[rateIndex];

                            // Check if this is a child table
                            if (!fullTablePath.isEmpty())
                            {
                                int valuesIndex = rateValues.indexOf(fullTablePath
                                                                     + ","
                                                                     + thisMember.getFullVariableName(memberIndex));

                                // Check if a custom rate value exists for this
                                // variable
                                if (valuesIndex != -1)
                                {
                                    // Get the custom rate value for this
                                    // variable
                                    rate = rateValues.get(valuesIndex)[ValuesColumn.VALUE.ordinal()];
                                }
                            }

                            // Check if the variable has a sample rate for this
                            // stream and, if a filter is in effect, that the
                            // rates match
                            if (!rate.isEmpty()
                                && (rateFilter.isEmpty()
                                || rate.equals(rateFilter)))
                            {
                                // Add the rate information to the JSON output
                                rateJO.put("Stream Name",
                                           rateHandler.getRateInformation().get(rateIndex).getStreamName());
                                rateJO.put("Sample Rate", rate);
                                streamsJA.add(rateJO);
                            }
                        }
                    }

                    // Check if the variable belongs to any stream (i.e., it's
                    // telemetered)
                    if (!streamsJA.isEmpty())
                    {
                        JSONObject telemetryJO = new JSONObject();
                        JSONArray enumerationsJA = new JSONArray();

                        // Add the variable path and name to the list of
                        // telemetered variable information
                        telemetryInformation.add(new String[] {fullTablePath,
                                                               thisMember.getFullVariableName(memberIndex)});

                        // Store the structure table name, variable name, data
                        // type, and stream(s) in the JSON output
                        telemetryJO.put("Structure Table Name", fullTablePath);
                        telemetryJO.put("Variable Name",
                                        thisMember.getVariableNames().get(memberIndex));
                        telemetryJO.put("Data Type", thisMember.getDataTypes().get(memberIndex));
                        telemetryJO.put("Data Streams", streamsJA);

                        // Step through each enumeration
                        for (int enumIndex = 0; enumIndex < thisMember.getEnumerations().get(memberIndex).length; enumIndex++)
                        {
                            JSONObject enumerationJO = new JSONObject();

                            // Get the enumeration for this parameter from the
                            // prototype table
                            String enumeration = thisMember.getEnumerations().get(memberIndex)[enumIndex];

                            // Check if this is a child table
                            if (!fullTablePath.isEmpty())
                            {
                                int valuesIndex = enumerationValues.indexOf(fullTablePath
                                                                            + ","
                                                                            + thisMember.getFullVariableName(memberIndex));

                                // Check if a custom enumeration value exists
                                // for this variable
                                if (valuesIndex != -1)
                                {
                                    // Get the custom enumeration value for
                                    // this variable
                                    enumeration = enumerationValues.get(valuesIndex)[ValuesColumn.VALUE.ordinal()];
                                }
                            }

                            // Check if the variable has an enumeration
                            if (!enumeration.isEmpty())
                            {
                                // Add the enumeration information to the JSON
                                // output
                                enumerationJO.put("Enumeration Name",
                                                  enumColumnNames.get(enumIndex));
                                enumerationJO.put("Enumeration Value", enumeration);
                                enumerationsJA.add(enumerationJO);
                            }
                        }

                        // Check if the variable has an enumeration
                        if (!enumerationsJA.isEmpty())
                        {
                            // Store the enumerations in the JSON output
                            telemetryJO.put("Enumerations", enumerationsJA);
                        }

                        // Add the variable to the JSON array
                        telemetryJA.add(telemetryJO);
                    }
                }
            }
            // Data type is not a primitive (i.e., it's a structure)
            else
            {
                // Step through the other tables
                for (TableMembers member : tableMembers)
                {
                    // Check if the table is a member of the target table
                    if (thisMember.getDataTypes().get(memberIndex).equals(member.getTableName()))
                    {
                        // Get the variables for this structure's members
                        getTelemetry(member,
                                     fullTablePath
                                         + ","
                                         + thisMember.getDataTypes().get(memberIndex)
                                         + "."
                                         + thisMember.getVariableNames().get(memberIndex),
                                     groupFilter,
                                     streamFilter,
                                     rateFilter);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Get the information for each command matching the specified filters
     * 
     * @param groupFilter
     *            group (or application) name. A table must belong to the
     *            specified group in order for its telemetered variables to be
     *            returned; blank to get all telemetered variables (regardless
     *            of group)
     * 
     * @return JSON encoded string containing information for each command
     *         matching the specified filters
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private String getCommandInformation(String groupFilter) throws CCDDException
    {
        JSONArray commandsJA = new JSONArray();
        TypeDefinition typeDefn = null;
        int commandNameIndex = -1;
        int commandCodeIndex = -1;
        int commandDescriptionIndex = -1;
        List<AssociatedColumns> commandArguments = null;

        // Table type name for the previous table type loaded
        String lastType = "";

        // Check if a group name filter is specified
        if (!groupFilter.isEmpty())
        {
            // Create a group handler and extract the table names belonging to
            // the group
            CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain,
                                                                 ccddMain.getMainFrame());
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupFilter);

            // Check if the group doesn't exist
            if (groupInfo == null)
            {
                throw new CCDDException("unrecognized group name");
            }

            // Get the tables associated with the group
            groupTables = groupInfo.getTables();
        }

        // Step through each command table
        for (String commandTable : dbTable.getTablesOfType(TYPE_COMMAND))
        {
            // Check if all commands are to be returned, or if a specific
            // group's commands are requested that the table is a member of the
            // group
            if (groupFilter.isEmpty()
                || groupTables.contains(commandTable))
            {
                // Get the information from the database for the specified
                // table
                TableInformation tableInfo = dbTable.loadTableData(commandTable,
                                                                   false,
                                                                   false,
                                                                   false,
                                                                   false,
                                                                   ccddMain.getMainFrame());

                // Check if the table loaded successfully
                if (!tableInfo.isErrorFlag())
                {
                    // Check if the table type changed. This accounts for
                    // multiple table types that represent commands, and
                    // prevents reloading the table type information for every
                    // table
                    if (!tableInfo.getType().equals(lastType))
                    {
                        String descColName;
                        commandDescriptionIndex = -1;

                        // Store the table type name
                        lastType = tableInfo.getType();

                        // Get the table's type definition
                        typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());

                        // Get the command name column
                        commandNameIndex = typeDefn.getColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME));

                        // Get the command name column
                        commandCodeIndex = typeDefn.getColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_CODE));

                        // Check if a command description column exists
                        if ((descColName = typeDefn.getColumnNameByInputType(InputDataType.DESCRIPTION)) != null)
                        {
                            // Get the command description column
                            commandDescriptionIndex = typeDefn.getColumnIndexByUserName(descColName);
                        }

                        // Get the list containing command argument column
                        // indices for each argument grouping
                        commandArguments = typeDefn.getAssociatedCommandColumns(false);
                    }

                    // Step through each command in the command table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        JSONObject commandJO = new JSONObject();
                        String cellValue;

                        // Check if the command name is present. If not then
                        // all the command data on this row is skipped
                        if (!(cellValue = tableInfo.getData()[row][commandNameIndex]).isEmpty())
                        {
                            JSONArray commandArgumentsJA = new JSONArray();

                            // Store the name of the command table from which
                            // this command is taken
                            commandJO.put("Command Table Name", commandTable);

                            // Store the command name in the JSON output
                            commandJO.put(typeDefn.getColumnNamesUser()[commandNameIndex],
                                          cellValue);

                            // Check if the command code is present
                            if (!(cellValue = tableInfo.getData()[row][commandCodeIndex]).isEmpty())
                            {
                                // Store the command code in the JSON output
                                commandJO.put(typeDefn.getColumnNamesUser()[commandCodeIndex],
                                              cellValue);
                            }

                            // Check if the command description is present
                            if (commandDescriptionIndex != -1
                                && !(cellValue = tableInfo.getData()[row][commandDescriptionIndex]).isEmpty())
                            {
                                // Store the command description in the JSON
                                // output
                                commandJO.put(typeDefn.getColumnNamesUser()[commandDescriptionIndex],
                                              cellValue);
                            }

                            // Step through each command argument associated
                            // with the current command row
                            for (AssociatedColumns cmdArgument : commandArguments)
                            {
                                JSONObject commandArgumentJO = new JSONObject();

                                // Check if the command argument name column
                                // has a value. If not, all associated argument
                                // values are skipped
                                if (!(cellValue = tableInfo.getData()[row][cmdArgument.getName()]).isEmpty())
                                {
                                    // Store the command argument name in the
                                    // JSON output
                                    commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getName()],
                                                          cellValue);

                                    // Check if the command argument data type
                                    // column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getDataType()]).isEmpty())
                                    {
                                        // Store the data type in the JSON
                                        // output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getDataType()],
                                                              cellValue);
                                    }

                                    // Check if the command argument
                                    // enumeration column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getEnumeration()]).isEmpty())
                                    {
                                        // Store the enumeration in the JSON
                                        // output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getEnumeration()],
                                                              cellValue);
                                    }

                                    // Check if the command argument minimum
                                    // column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getMinimum()]).isEmpty())
                                    {
                                        // Store the minimum value in the JSON
                                        // output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getMinimum()],
                                                              cellValue);
                                    }

                                    // Check if the command argument maximum
                                    // column has a value
                                    if (!(cellValue = tableInfo.getData()[row][cmdArgument.getMaximum()]).isEmpty())
                                    {
                                        // Store the maximum value in the JSON
                                        // output
                                        commandArgumentJO.put(typeDefn.getColumnNamesUser()[cmdArgument.getMaximum()],
                                                              cellValue);
                                    }

                                    // Step through any other columns
                                    // associated with this command argument
                                    for (Integer otherArg : cmdArgument.getOther())
                                    {
                                        // Check if the other argument column
                                        // has a value
                                        if (!(cellValue = tableInfo.getData()[row][otherArg]).isEmpty())
                                        {
                                            // Store the value in the JSON
                                            // output
                                            commandArgumentJO.put(typeDefn.getColumnNamesUser()[otherArg],
                                                                  cellValue);
                                        }
                                    }
                                }

                                // Store the command arguments in the JSON
                                // array
                                commandArgumentsJA.add(commandArgumentJO);
                            }

                            // Check if the command has an argument
                            if (!commandArgumentsJA.isEmpty())
                            {
                                // Store the command arguments in the JSON
                                // output
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

    /**************************************************************************
     * Get the table type definitions
     * 
     * @return JSON encoded string containing the table type definitions; an
     *         empty list if no table type definition exists
     *************************************************************************/
    private String getTableTypeDefinitions()
    {
        // Add the table type definitions to the output
        return jsonHandler.getTableTypeDefinitions(null,
                                                   new JSONObject()).toJSONString();
    }

    /**************************************************************************
     * Get the data type definitions
     * 
     * @return JSON encoded string containing the data type definitions; an
     *         empty list if no data type definition exists
     *************************************************************************/
    private String getDataTypeDefinitions()
    {
        // Add the data type definitions to the output
        return jsonHandler.getDataTypeDefinitions(null,
                                                  new JSONObject()).toJSONString();
    }

    /**************************************************************************
     * Get the macro definitions
     * 
     * @return JSON encoded string containing the macro definitions; an empty
     *         list if no macro definition exists
     *************************************************************************/
    private String getMacroDefinitions()
    {
        // Add the macro definitions to the output
        return jsonHandler.getMacroDefinitions(null,
                                               new JSONObject()).toJSONString();
    }
}
