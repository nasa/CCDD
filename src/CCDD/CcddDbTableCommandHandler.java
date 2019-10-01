/**
 * CFS Command and Data Dictionary database table command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR;
import static CCDD.CcddConstants.DEFAULT_INSTANCE_NODE_NAME;
import static CCDD.CcddConstants.DEFAULT_PROTOTYPE_NODE_NAME;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_IDENT;
import static CCDD.CcddConstants.REPLACE_INDICATOR;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;
import static CCDD.CcddConstants.TABLE_STRINGS;
import static CCDD.CcddConstants.TLM_SCH_SEPARATOR;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.awt.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.BitPackNodeIndex;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableMembers;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DatabaseObject;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.OrdersColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddConstants.TableMemberType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddInputTypeHandler.InputTypeReference;
import CCDD.CcddInputTypeHandler.ReferenceCheckResults;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary database table command handler class
 *************************************************************************************************/
public class CcddDbTableCommandHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private final CcddDbControlHandler dbControl;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddFieldHandler fieldHandler;
    private CcddMacroHandler macroHandler;
    private CcddRateParameterHandler rateHandler;
    private CcddLinkHandler addLinkHandler;
    private CcddVariableHandler variableHandler;
    private CcddCommandHandler commandHandler;
    private CcddInputTypeHandler inputTypeHandler;

    // Flag that indicates a variable has been added to a link definition and the links table
    // should be updated
    private boolean updateLinks;

    // Flag that indicates that a variable has been added or deleted, or an existing variable's
    // name, data type, array size, or bit length has changed. If this flag is true then the
    // variable paths and offsets lists needs to be rebuilt
    private boolean isVariablePathChange;

    // Flag that indicates that a command has been added or deleted, or an existing command's name,
    // code, or argument name has changed. If this flag is true then the command list needs to be
    // rebuilt
    private boolean isCommandChange;

    // Flag that indicates that a message name or ID has been added, modified, or deleted. If this
    // flag is true then the message name & ID list needs to be rebuilt
    private boolean isMsgIDChange;

    // Character(s) separating table references in the script associations and telemetry scheduler
    // tables, with any special characters escaped so as to be used in a PostgrSQL command
    private final String assnsSeparator;
    private final String tlmSchSeparator;

    // List of arrays scheduled for removal from the links and telemetry scheduler tables
    private List<String> deletedArrayDefns;

    // List of root structure tables. In order to reduce traffic with the database, a running list,
    // updated as needed, is used in place of querying for the list as needed
    private List<String> rootStructures;

    // Characters used to create a unique delimiter for literal strings stored in the database
    private final static String DELIMITER_CHARACTERS = "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**********************************************************************************************
     * Database table command handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddDbTableCommandHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        eventLog = ccddMain.getSessionEventLog();

        // Escape any special characters in the script associations and telemetry scheduler table
        // separators
        assnsSeparator = CcddUtilities.escapePostgreSQLReservedChars(ASSN_TABLE_SEPARATOR);
        tlmSchSeparator = CcddUtilities.escapePostgreSQLReservedChars(TLM_SCH_SEPARATOR);
    }

    /**********************************************************************************************
     * Set the references to the table type, macro, data type, and rate handler classes
     *********************************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        variableHandler = ccddMain.getVariableHandler();
        commandHandler = ccddMain.getCommandHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();

        // Get the list of root structure tables
        rootStructures = getRootStructures(ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Enclose database text string objects with a delimiter. The default delimiter is single
     * quotes. If the text contains single quotes or a backslash then $$ is used instead, unless
     * the text already contains $$ or ends with $, in which case $_$ is used as the delimiter
     *
     * @param object
     *            object to be stored in the database
     *
     * @return Object enclosed by delimiters if the object is a text string
     *********************************************************************************************/
    protected static Object delimitText(Object object)
    {
        // Check if this is a string
        if (object instanceof String)
        {
            // Use a single quote as the default delimiter
            String delim = "'";

            // Convert the item to a string
            String objectS = object.toString();

            // Check if the item contains a single quote or backslash character
            if (objectS.contains("'") || objectS.contains("\\"))
            {
                // Check if the item doesn't contain $$ or end with $
                if (!objectS.contains("$$") && !objectS.endsWith("$"))
                {
                    // Use $$ as the delimiter
                    delim = "$$";
                }
                // The item does contain $$. A delimiter that doesn't conflict with the text must
                // be created. This delimiter is in the form $_$ where _ is a single underscore or
                // upper/lower case letter, or one or more underscores followed by a single upper
                // or lower case letter
                else
                {
                    String delimInit = "";
                    int index = 0;

                    do
                    {
                        do
                        {
                            // Use $_$ as the delimiter where '_' is an underscore or letter
                            delim = "$" + delimInit + DELIMITER_CHARACTERS.charAt(index) + "$";
                            index++;
                        } while (objectS.contains(delim) && index < DELIMITER_CHARACTERS.length());
                        // Continue to change the delimiter character until no matching string is
                        // found in the supplied text, or until all legal characters are exhausted

                        // Check if a match exists in the supplied text using all of the legal
                        // characters in the delimiter
                        if (index == DELIMITER_CHARACTERS.length())
                        {
                            // Insert an(other) underscore into the delimiter and reset the
                            // character index and try again to find a non-interfering delimiter
                            delimInit += "_";
                            index = 0;
                        }
                    } while (index == 0);
                    // Continue to try potential delimiters until one is found that doesn't have a
                    // match within the supplied text
                }
            }

            // Enclose the item with the delimiter
            object = delim + objectS + delim;
        }

        return object;
    }

    /**********************************************************************************************
     * Check if a specified table exists in the database (case insensitive)
     *
     * @param tableName
     *            name of the table to check for in the database
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the specified table exists in the database
     *********************************************************************************************/
    protected boolean isTableExists(String tableName, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SPECIFIC_TABLE,
                                 new String[][] {{"_table_name_", tableName.toLowerCase()}},
                                 parent).length != 0;
    }

    /**********************************************************************************************
     * Check if the supplied table name is a root structure table
     *
     * @param tableName
     *            name of the table to check
     *
     * @return true if the supplied table name is a root structure
     *********************************************************************************************/
    protected boolean isRootStructure(String tableName)
    {
        return rootStructures.contains(tableName);
    }

    /**********************************************************************************************
     * Get the list of root (top level) structure tables from the project database
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List of root structure tables; empty list if there are no root tables
     *********************************************************************************************/
    private List<String> getRootStructures(Component parent)
    {
        return getRootTables(true, parent);
    }

    /**********************************************************************************************
     * Get the list of root (top level) tables
     *
     * @param structuresOnly
     *            true to only include structure tables; false to include tables of all types
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List of root tables; empty list if there are no root tables
     *********************************************************************************************/
    protected List<String> getRootTables(boolean structuresOnly, Component parent)
    {
        List<String> rootTables = new ArrayList<String>();

        // Get the prototype tables and their member tables
        List<TableMembers> tableMembers = loadTableMembers(TableMemberType.TABLES_ONLY,
                                                           true,
                                                           parent);

        // Step through each table
        for (TableMembers member : tableMembers)
        {
            // Check if all table types should be included, or if only structures are to be
            // included that the member is a structure table type
            if (!structuresOnly
                || tableTypeHandler.getTypeDefinition(member.getTableType()).isStructure())
            {
                boolean isRootTable = true;

                // Step through each table
                for (TableMembers otherMember : tableMembers)
                {
                    // Check if the current table has this table as a member, and that the table
                    // isn't referencing itself
                    if (otherMember.getDataTypes().contains(member.getTableName())
                        && !member.equals(otherMember))
                    {
                        // Clear the flag indicating this is a root table and stop searching
                        isRootTable = false;
                        break;
                    }
                }

                // Check if this is a root table
                if (isRootTable)
                {
                    // Store the root table's name
                    rootTables.add(member.getTableName());
                }
            }
        }

        return rootTables;
    }

    /**********************************************************************************************
     * Build the specified table's comment command
     *
     * @param tableName
     *            table name
     *
     * @param comment
     *            table comment
     *
     * @return Command to update the table's comment
     *********************************************************************************************/
    private String buildTableComment(String tableName, String comment)
    {
        return "COMMENT ON TABLE " + tableName + " IS " + delimitText(comment) + "; ";
    }

    /**********************************************************************************************
     * Build the specified data table's comment command. The table's visible table name (preserving
     * case) and its type are stored as a comment on the table
     *
     * @param tableName
     *            table name
     *
     * @param tableType
     *            table type
     *
     * @return Command to update the table's comment
     *********************************************************************************************/
    private String buildDataTableComment(String tableName, String tableType)
    {
        return buildTableComment(dbControl.getQuotedName(tableName),
                                 CcddConstants.TableCommentIndex.buildComment(tableName,
                                                                              tableType));
    }

    /**********************************************************************************************
     * Build the specified table instance's description update command. The table's description is
     * stored in the custom values table. Blank descriptions aren't stored
     *
     * @param tablePath
     *            path to the table. For a structure table the format is:
     *            rootTable,dataType0.variableName0[,
     *            dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the first data
     *            type/variable name pair is from the root table, with each succeeding pair coming
     *            from the next level down in the structure's hierarchy. The path is the same as
     *            the root table name for non-structure tables
     *
     * @param description
     *            table description
     *
     * @return Command to update the specified table's description
     *********************************************************************************************/
    private String buildTableDescription(String tablePath, String description)
    {
        String command = "DELETE FROM "
                         + InternalTable.VALUES.getTableName()
                         + " WHERE "
                         + ValuesColumn.TABLE_PATH.getColumnName()
                         + " = '"
                         + tablePath
                         + "' AND "
                         + ValuesColumn.COLUMN_NAME.getColumnName()
                         + " = ''; ";

        // Check if the description isn't blank
        if (!description.isEmpty())
        {
            command += "INSERT INTO "
                       + InternalTable.VALUES.getTableName()
                       + " ("
                       + ValuesColumn.TABLE_PATH.getColumnName()
                       + ", "
                       + ValuesColumn.COLUMN_NAME.getColumnName()
                       + ", "
                       + ValuesColumn.VALUE.getColumnName()
                       + ") VALUES ('"
                       + tablePath
                       + "', '', "
                       + delimitText(description)
                       + "); ";
        }

        return command;
    }

    /**********************************************************************************************
     * Build the specified table instance's column order update command
     *
     * @param tablePath
     *            path to the table. For a structure table the format is:
     *            rootTable,dataType0.variableName0[,
     *            dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the first data
     *            type/variable name pair is from the root table, with each succeeding pair coming
     *            from the next level down in the structure's hierarchy. The path is the same as
     *            the root table name for non-structure tables
     *
     * @param columnOrder
     *            table column order
     *
     * @return Command to update the specified table's column order for the current user
     *********************************************************************************************/
    private String buildColumnOrder(String tablePath, String columnOrder)
    {
        return "DELETE FROM "
               + InternalTable.ORDERS.getTableName()
               + " WHERE "
               + OrdersColumn.USER_NAME.getColumnName()
               + " = '"
               + dbControl.getUser()
               + "' AND "
               + OrdersColumn.TABLE_PATH.getColumnName()
               + " = '"
               + tablePath
               + "'; INSERT INTO "
               + InternalTable.ORDERS.getTableName()
               + " ("
               + OrdersColumn.USER_NAME.getColumnName()
               + ", "
               + OrdersColumn.TABLE_PATH.getColumnName()
               + ", "
               + OrdersColumn.COLUMN_ORDER.getColumnName()
               + ") VALUES ('"
               + dbControl.getUser()
               + "', '"
               + tablePath
               + "', '"
               + columnOrder
               + "'); ";
    }

    /**********************************************************************************************
     * Perform a query on the currently open database
     *
     * @param sqlCommand
     *            PostgreSQL-compatible database query statement
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Two-dimensional array representing the rows and columns of data returned by the
     *         database query; returns null if the query produces an error, or an empty array if
     *         there are no results
     *********************************************************************************************/
    protected List<String[]> queryDatabase(String sqlCommand, Component parent)
    {
        List<String[]> queryResults = null;

        try
        {
            // Execute the query command
            ResultSet results = dbCommand.executeDbQuery(sqlCommand, parent);

            // Create a list to contain the row information
            List<String[]> tableData = new ArrayList<String[]>();

            // Step through each of the query results
            while (results.next())
            {
                // Create an array to contain the column values
                String[] columnValues = new String[results.getMetaData().getColumnCount()];

                // Step through each column in the row
                for (int column = 0; column < results.getMetaData().getColumnCount(); column++)
                {
                    // Add the column value to the array. Note that the first column's index in the
                    // database is 1, not 0
                    columnValues[column] = results.getString(column + 1);
                }

                // Add the row data to the list
                tableData.add(columnValues);
            }

            results.close();

            // Store the list
            queryResults = tableData;
        }
        catch (SQLException se)
        {
            // Inform the user that the query failed
            eventLog.logFailEvent(parent,
                                  "Database query failed; cause '" + se.getMessage() + "'",
                                  "<html><b>Database query failed");
        }

        return queryResults;
    }

    /**********************************************************************************************
     * Retrieve the specified cell's value from the specified table
     *
     * @param tableName
     *            name database table name
     *
     * @param primaryKey
     *            value of the table's primary key column from which to extract the cell value
     *
     * @param columnName
     *            name of the column from which to extract the cell value
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Value of the cell in the table specified; blank if the query fails
     *********************************************************************************************/
    protected String queryTableCellValue(String tableName,
                                         String primaryKey,
                                         String columnName,
                                         Component parent)
    {
        String cellValue = "";

        // Get the cell value from the table
        List<String[]> results = queryDatabase("SELECT "
                                               + columnName
                                               + " FROM "
                                               + tableName
                                               + " WHERE "
                                               + DefaultColumn.PRIMARY_KEY.getDbName()
                                               + " = "
                                               + primaryKey,
                                               parent);

        // Check if the query succeeded
        if (results != null)
        {
            // Get the cell value from the query results
            cellValue = results.get(0)[0];
        }

        return cellValue;
    }

    /**********************************************************************************************
     * Retrieve a list of all data tables in the database with the names in the user-viewable form.
     * Any non-data tables are ignored
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the table names with their original capitalization
     *********************************************************************************************/
    protected String[] queryTableList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATA_TABLES, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all data tables in the database along with the table's type. Any non-data
     * tables are ignored
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List array containing the table names and table types. The table names are provided
     *         in both the user-viewable form, which preserves capitalization, and the database
     *         form. An empty array is returned if no tables exist
     *********************************************************************************************/
    protected List<String[]> queryTableAndTypeList(Component parent)
    {
        List<String[]> typeAndTable = new ArrayList<String[]>(0);

        // Get the array containing the table types and names
        String[] tables = dbCommand.getList(DatabaseListCommand.DATA_TABLES_WITH_TYPE, null, parent);

        // Check if any tables exist
        if (tables.length != 0)
        {
            // Step through each table
            for (String table : tables)
            {
                // Separate the names and type
                typeAndTable.add(table.split(",", 3));
            }
        }

        return typeAndTable;
    }

    /**********************************************************************************************
     * Retrieve a list of all table types in the database. Any non-data tables are ignored
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the table types in alphabetical order
     *********************************************************************************************/
    protected String[] queryTableTypesList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLE_TYPES, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all tables of a specific type in the database. Any non-data tables are
     * ignored
     *
     * @param tableType
     *            table type by which to filter the table names. The table type is case insensitive
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the table names of the specified type
     *********************************************************************************************/
    protected String[] queryTablesOfTypeList(String tableType, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLES_OF_TYPE,
                                 new String[][] {{"_type_", tableType.toLowerCase()}},
                                 parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all tables that represent script files
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the script table names
     *********************************************************************************************/
    protected String[] queryScriptTables(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SCRIPTS, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of comments for all data tables in the database. Any non-data tables are
     * ignored
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the table comment parameters and the table name as used in
     *         the database
     *********************************************************************************************/
    protected String[][] queryDataTableComments(Component parent)
    {
        // Get the array of comment strings for every data table
        String[] comments = dbCommand.getList(DatabaseListCommand.TABLE_COMMENTS, null, parent);

        // Create storage for the parsed comments
        String[][] parsedComments = new String[comments.length][];

        int index = 0;

        // Step through each comment
        for (String comment : comments)
        {
            // Parse the comment into its separate parameters
            parsedComments[index] = comment.split(",", TableCommentIndex.values().length);
            index++;
        }

        return parsedComments;
    }

    /**********************************************************************************************
     * Retrieve the comment for the specified table
     *
     * @param tableName
     *            table name
     *
     * @param numParts
     *            number of parts into which to split the comment
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Array containing the separate elements of the table comment
     *********************************************************************************************/
    protected String[] queryTableComment(String tableName, int numParts, Component parent)
    {
        // Create an list to contain the comment elements
        List<String> parts = new ArrayList<String>();

        try
        {
            // Get the table's comment
            ResultSet comment = dbCommand.executeDbQuery("SELECT obj_description('public."
                                                         + tableName.toLowerCase()
                                                         + "'::regclass, 'pg_class');",
                                                         parent);

            // Get the comment string from the result set
            comment.next();
            String text = comment.getString(1);
            comment.close();

            // Check if the comment exists
            if (text != null)
            {
                // Step through each element of the comment
                for (String part : text.split(",", numParts))
                {
                    // Store the element in the comment array
                    parts.add(part);
                }
            }
        }
        catch (SQLException se)
        {
            // Inform the user that loading the table comment failed
            eventLog.logFailEvent(parent,
                                  "Cannot obtain comment for table '"
                                          + tableName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot obtain comment for table '</b>"
                                                 + tableName
                                                 + "<b>'");
        }

        return parts.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Retrieve the comment for the specified data table. The comment contains the table's visible
     * name (preserves case) and type
     *
     * @param tableName
     *            data table name
     *
     * @param parent
     *            dialog calling this component
     *
     * @return Array containing the separate elements of the data table comment
     *********************************************************************************************/
    protected String[] queryDataTableComment(String tableName, Component parent)
    {
        // Get the table's comment, broken into its separate elements
        String comment[] = queryTableComment(tableName, TableCommentIndex.values().length, parent);

        // Create an array to contain the data table comment elements and initialize each to a
        // blank
        String[] parts = new String[TableCommentIndex.values().length];
        Arrays.fill(parts, "");

        // Replace the empty element with that from the comment
        System.arraycopy(comment, 0, parts, 0, comment.length);

        return parts;
    }

    /**********************************************************************************************
     * Retrieve a list of all data table descriptions in the database. Any non-data tables are
     * ignored
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return String array containing the table names and their variable paths, if any, for those
     *         tables with descriptions
     *********************************************************************************************/
    protected String[][] queryTableDescriptions(Component parent)
    {
        String[][] tableDescriptions;

        // Get the array containing the table descriptions and names
        String[] descriptions = dbCommand.getList(DatabaseListCommand.TABLE_DESCRIPTIONS,
                                                  null,
                                                  parent);

        // Check that table descriptions were loaded
        if (descriptions.length != 0)
        {
            tableDescriptions = new String[descriptions.length][3];

            // Step through each description
            for (int index = 0; index < descriptions.length; index++)
            {
                // Split the description into the table path and description
                tableDescriptions[index] = descriptions[index].split(Pattern.quote(TABLE_DESCRIPTION_SEPARATOR),
                                                                     2);
            }
        }
        // No descriptions were loaded
        else
        {
            // Create an empty array
            tableDescriptions = new String[0][0];
        }

        return tableDescriptions;
    }

    /**********************************************************************************************
     * Retrieve the description for the specified table
     *
     * @param tablePath
     *            table path in the format rootTable[,dataType1.variable1[,dataType2
     *            .variable2[,...]]]. The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top level structure table
     *            from which this table descends. The first data type/variable name pair is from
     *            the root table, with each succeeding pair coming from the next level down in the
     *            structure's hierarchy
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Table description; blank if the table has no description
     *********************************************************************************************/
    protected String queryTableDescription(String tablePath, Component parent)
    {
        // Set the initial description to an empty string
        String description = "";

        try
        {
            // Get the description for the table
            ResultSet descData = dbCommand.executeDbQuery("SELECT "
                                                          + ValuesColumn.VALUE.getColumnName()
                                                          + " FROM "
                                                          + InternalTable.VALUES.getTableName()
                                                          + " WHERE "
                                                          + ValuesColumn.TABLE_PATH.getColumnName()
                                                          + " = '"
                                                          + tablePath
                                                          + "' AND "
                                                          + ValuesColumn.COLUMN_NAME.getColumnName()
                                                          + " = '';",
                                                          parent);

            // Check if the description exists for this table
            if (descData.next())
            {
                // Split the description data into its constituent parts and copy these to the
                // description
                description = descData.getString(1).trim();
            }

            descData.close();

            // Check if this is not a prototype table
            if (description.isEmpty() && tablePath.contains(","))
            {
                // Get the description of the prototype table for this table instance and use it
                // for the instance's description
                description = queryTableDescription(TableInformation.getPrototypeName(tablePath),
                                                    parent);
            }
        }
        catch (SQLException se)
        {
            // Inform the user that loading the table description failed
            eventLog.logFailEvent(parent,
                                  "Cannot obtain description for table '"
                                          + tablePath
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot obtain description for table '</b>"
                                                 + tablePath
                                                 + "<b>'");
        }

        return description;
    }

    /**********************************************************************************************
     * Retrieve the column order for the specified table and the current user
     *
     * @param tablePath
     *            comma-separated variable names describing the path to this table, beginning with
     *            the root table; the names are in the order from highest level to lowest level,
     *            where the lowest level name is the &lt;variable name&gt; portion of the table
     *            name; blank if the queried table is a prototype
     *
     * @param tableType
     *            type of table being queried
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Table column order
     *********************************************************************************************/
    private String queryColumnOrder(String tablePath, String tableType, Component parent)
    {
        // Set the column order to the default for this table type
        String columnOrder = tableTypeHandler.getDefaultColumnOrder(tableType);

        try
        {
            // Get the column order for the table
            ResultSet orderData = dbCommand.executeDbQuery("SELECT "
                                                           + OrdersColumn.COLUMN_ORDER.getColumnName()
                                                           + " FROM "
                                                           + InternalTable.ORDERS.getTableName()
                                                           + " WHERE "
                                                           + OrdersColumn.USER_NAME.getColumnName()
                                                           + " = '"
                                                           + dbControl.getUser()
                                                           + "' AND "
                                                           + OrdersColumn.TABLE_PATH.getColumnName()
                                                           + " = '"
                                                           + tablePath
                                                           + "';",
                                                           parent);

            // Check if the column order exists for this table
            if (orderData.next())
            {
                // Get the table column order
                columnOrder = orderData.getString(1);
            }

            orderData.close();
        }
        catch (SQLException se)
        {
            // Inform the user that loading the table column order failed
            eventLog.logFailEvent(parent,
                                  "Cannot obtain column order for table '"
                                          + tablePath
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot obtain column order for table '</b>"
                                                 + tablePath
                                                 + "<b>'");
        }

        return columnOrder;
    }

    /**********************************************************************************************
     * Return an array of table comment components for the specified table
     *
     * @param dbTableName
     *            table name as used by the database
     *
     * @param comments
     *            array of table comments, with each comment divided into its component parts
     *
     * @return Array of comment components for the specified table
     *********************************************************************************************/
    protected String[] getTableComment(String dbTableName, String[][] comments)
    {
        // Initialize the comment array
        String[] comment = new String[TableCommentIndex.values().length];
        Arrays.fill(comment, "");

        // Step through each table comment
        for (String[] cmt : comments)
        {
            // Check if the target name matches the table name in the comment
            if (dbTableName.equalsIgnoreCase(cmt[TableCommentIndex.NAME.ordinal()]))
            {
                // Store the elements of the comment that were retrieved for the table and stop
                // searching
                System.arraycopy(cmt, 0, comment, 0, Math.min(comment.length, cmt.length));
                break;
            }
        }

        return comment;
    }

    /**********************************************************************************************
     * Set the comment for the specified table
     *
     * @param tableName
     *            table name as used by the database
     *
     * @param comment
     *            table comment
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void setTableComment(String tableName, String comment, Component parent)
    {
        // Execute the database update
        try
        {
            // Build and execute the command to update the table's comment
            dbCommand.executeDbUpdate(buildTableComment(tableName, comment), parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, "Table '" + tableName + "' comment updated");
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot update comment for table '"
                                          + tableName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot update comment for table '</b>"
                                                 + tableName
                                                 + "<b>'");
        }
    }

    /**********************************************************************************************
     * Build the command to copy the data fields from one table to another
     *
     * @param fieldTableName
     *            name of the table in the data field table from which to copy
     *
     * @param tableName
     *            name of the table receiving the data fields
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Command to copy the data fields for one table to another
     *********************************************************************************************/
    private String copyDataFieldCommand(String fieldTableName, String tableName, Component parent)
    {
        String command = "";

        // Get the field information from the owner of the fields
        List<FieldInformation> fieldInfo = fieldHandler.getFieldInformationByOwner(fieldTableName);

        // Check if any fields exist
        if (!fieldInfo.isEmpty())
        {
            // Build the command to add the fields to the new table
            command = " " + modifyFieldsCommand(tableName, fieldInfo);
        }

        return command;
    }

    /**********************************************************************************************
     * Update the root structure table, variable path and offset, and command lists, and the
     * variable, command, and message ID references. This is needed once one or more tables are
     * created, copied, renamed, or deleted
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void updateListsAndReferences(Component parent)
    {
        rootStructures = getRootStructures(parent);
        variableHandler.buildPathAndOffsetLists();
        commandHandler.buildCommandList();
        inputTypeHandler.updateMessageReferences(parent);
    }

    /**********************************************************************************************
     * Create one or more tables of the specified type. This command is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands, however, are
     * disabled until the database command completes execution
     *
     * @param tableNames
     *            array of names of the tables to create
     *
     * @param description
     *            description for the new table(s)
     *
     * @param tableType
     *            type of table to create (e.g., Structure or Command)
     *
     * @param tableDialog
     *            reference to the table manager dialog
     *********************************************************************************************/
    protected void createTableInBackground(final String[] tableNames,
                                           final String description,
                                           final String tableType,
                                           final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /**************************************************************************************
             * Database table creation command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the table(s)
                errorFlag = createTable(tableNames, description, tableType, tableDialog);
            }

            /**************************************************************************************
             * Create database table command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred creating the table
                if (!errorFlag)
                {
                    tableDialog.doTableOperationComplete();
                }
            }
        });
    }

    /**********************************************************************************************
     * Create one or more tables of the specified type
     *
     * @param tableNames
     *            array of names of the tables to create
     *
     * @param description
     *            description for the new table(s)
     *
     * @param tableType
     *            type of table to create (e.g., Structure or Command)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if an error occurred when creating a table
     *********************************************************************************************/
    protected boolean createTable(String[] tableNames,
                                  String description,
                                  String tableType,
                                  Component parent)
    {
        boolean errorFlag = false;

        // Convert the array of names into a single string, separated by commas
        String allNames = CcddUtilities.convertArrayToStringTruncate(tableNames);

        try
        {
            String command = "";

            // Step through each new table name
            for (String tableName : tableNames)
            {
                // Add the command to create the table
                command += createTableCommand(tableName, description, tableType, parent);
            }

            // Execute the database update
            dbCommand.executeDbUpdate(command, parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, "Table(s) '" + allNames + "' created");
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot create table(s) '"
                                          + allNames
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot create table(s) '</b>" + allNames + "<b>'");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Build the command to create a table of the specified type
     *
     * @param tableName
     *            name of the table to create
     *
     * @param description
     *            description for the new table
     *
     * @param tableType
     *            type of table to create (e.g., Structure or Command)
     *
     * @param parent
     *            GUI component over which to center any dialogs
     *
     * @return The command to create the specified table
     *********************************************************************************************/
    private String createTableCommand(String tableName,
                                      String description,
                                      String tableType,
                                      Component parent)
    {
        StringBuilder command = new StringBuilder("");

        // Convert the table name to lower case and bound it with double quotes if it matches a
        // PostgreSQL reserved word. PostgreSQL automatically assumes lower case (unless the name
        // is quoted), so forcing the name to lower case is done here to differentiate the table
        // name from the upper case database commands in the event log
        String dbTableName = dbControl.getQuotedName(tableName);

        // Get the column names defined in the template file for this table type
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

        // Build the table creation and description commands
        command.append("CREATE TABLE " + dbTableName + " (");

        // Step through each column name
        for (int column = 0; column < typeDefn.getColumnNamesDatabase().length; column++)
        {
            // Add the column identifier and column data type to the command
            command.append("\""
                           + typeDefn.getColumnNamesDatabase()[column]
                           + "\" "
                           + DefaultColumn.getColumnDbType(column)
                           + ", ");
        }

        // Remove the trailing comma and space, then append the closing portion of the command, add
        // the command to save the table comments, and add the column descriptions as table column
        // comments
        command = CcddUtilities.removeTrailer(command, ", ");
        command.append("); "
                       + buildDataTableComment(tableName, tableType)
                       + buildTableDescription(tableName, description)
                       + buildColumnOrder(tableName,
                                          tableTypeHandler.getDefaultColumnOrder(tableType)));

        // Copy the default fields for the new table's type to the new table and set the table's
        // owner
        command.append(copyDataFieldCommand(CcddFieldHandler.getFieldTypeName(tableType),
                                            tableName,
                                            parent)
                       + dbControl.buildOwnerCommand(DatabaseObject.TABLE, tableName));

        return command.toString();
    }

    /**********************************************************************************************
     * Change the name of a table. Only prototype tables can be renamed using this method
     * (instances are renamed by changing the variable name in the prototype). This command is
     * executed in a separate thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     *
     * @param tableName
     *            current name of the table
     *
     * @param newName
     *            new name for this table
     *
     * @param newDescription
     *            new description for this table
     *
     * @param tableDialog
     *            reference to the table manager dialog
     *********************************************************************************************/
    protected void renameTable(final String tableName,
                               final String newName,
                               final String newDescription,
                               final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /**************************************************************************************
             * Rename database table command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    List<Integer> dataTypeColumns = new ArrayList<Integer>();
                    String command = "";

                    // Convert each of the table names to lower case and bound it with double
                    // quotes if it matches a PostgreSQL reserved word. PostgreSQL automatically
                    // assumes lower case (unless the name is quoted), so forcing the name to lower
                    // case is done here to differentiate the table name from the upper case
                    // database commands in the event log
                    String dbTableName = dbControl.getQuotedName(tableName);
                    String dbNewName = dbControl.getQuotedName(newName);

                    // Get the comments for all data tables
                    String[][] comments = queryDataTableComments(tableDialog);

                    // Get the table's comment so that it can be rebuilt with the new table name
                    String[] comment = getTableComment(tableName.toLowerCase(), comments);

                    // Step through each root table
                    for (String rootTable : getRootTables(false, tableDialog))
                    {
                        // Get the root table's comment and from it get the table's type
                        String[] rootComment = getTableComment(rootTable.toLowerCase(), comments);
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(rootComment[TableCommentIndex.TYPE.ordinal()]);
                        dataTypeColumns.clear();

                        // Step through each default input type
                        for (DefaultInputType inputType : DefaultInputType.values())
                        {
                            // Check if the input type represents a data type
                            if (inputType.getInputFormat().equals(InputTypeFormat.DATA_TYPE))
                            {
                                // Add all of the column indices of this input type to the list of
                                // data type column indices
                                dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(inputType));
                            }
                        }

                        // Step through each data type column index
                        for (Integer column : dataTypeColumns)
                        {
                            // Get the database name of the data type column
                            String dataTypeColName = typeDefn.getColumnNamesDatabase()[column];

                            // Append the command to change the references in the root table to the
                            // original renamed table's name to the new name
                            command += "UPDATE "
                                       + dbControl.getQuotedName(rootTable)
                                       + " SET "
                                       + dataTypeColName
                                       + " = '"
                                       + newName
                                       + "' WHERE "
                                       + dataTypeColName
                                       + " = '"
                                       + tableName + "'; ";
                        }
                    }

                    // Check if the old and new names differ in more than capitalization
                    if (!dbTableName.equals(dbNewName))
                    {
                        // Create the command to change the table's name and all references to it
                        // in the custom values, links, and groups tables
                        command = "ALTER TABLE "
                                  + dbTableName
                                  + " RENAME TO "
                                  + dbNewName
                                  + "; "
                                  + buildTableDescription(newName,
                                                          newDescription)
                                  + renameInfoTableCommand(InternalTable.VALUES,
                                                           ValuesColumn.TABLE_PATH.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.LINKS,
                                                           LinksColumn.MEMBER.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.TLM_SCHEDULER,
                                                           TlmSchedulerColumn.MEMBER.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.GROUPS,
                                                           GroupsColumn.MEMBERS.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.FIELDS,
                                                           FieldsColumn.OWNER_NAME.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.ORDERS,
                                                           OrdersColumn.TABLE_PATH.getColumnName(),
                                                           tableName,
                                                           newName)
                                  + renameInfoTableCommand(InternalTable.ASSOCIATIONS,
                                                           AssociationsColumn.MEMBERS.getColumnName(),
                                                           tableName,
                                                           newName);
                    }

                    // Update the table comment to reflect the name with case intact
                    command += buildDataTableComment(newName,
                                                     comment[TableCommentIndex.TYPE.ordinal()]);

                    // Execute the command to change the table's name, including the table's
                    // original name (before conversion to all lower case) that's stored as a
                    // comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table '" + tableName + "' renamed to '" + newName + "'");
                }
                catch (SQLException se)
                {
                    // Inform the user that renaming the table failed
                    eventLog.logFailEvent(tableDialog,
                                          "Cannot rename table '"
                                                       + tableName
                                                       + "'; cause '"
                                                       + se.getMessage()
                                                       + "'",
                                          "<html><b>Cannot rename table '</b>" + tableName + "<b>'");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, tableDialog);
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Rename database table command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred renaming the table
                if (!errorFlag)
                {
                    tableDialog.doTableOperationComplete();
                }
            }
        });
    }

    /**********************************************************************************************
     * Create the command to rename a table reference in an internal table
     *
     * @param intType
     *            internal table type
     *
     * @param idColumnName
     *            name of the internal table's identifier column
     *
     * @param oldName
     *            current name of the table
     *
     * @param newName
     *            new name for this table
     *
     * @return Command to rename the table reference in the specified internal table
     *********************************************************************************************/
    private String renameInfoTableCommand(InternalTable intType,
                                          String idColumnName,
                                          String oldName,
                                          String newName)
    {
        // Get the table separator, escaping any special character(s)
        String separator = intType == InternalTable.ASSOCIATIONS
                                                                 ? "|" + assnsSeparator
                                                                 : "";

        return "UPDATE "
               + intType.getTableName()
               + " SET "
               + idColumnName
               + " = regexp_replace("
               + idColumnName
               + ", E'(^|,"
               + (intType == InternalTable.TLM_SCHEDULER
                                                         ? "|" + tlmSchSeparator
                                                         : separator)
               + ")"
               + oldName
               + "(\\\\.|,"
               + separator
               + "|$)', E'\\\\1"
               + newName
               + "\\\\2'); ";
    }

    /**********************************************************************************************
     * Make a copy of an existing table. Only prototype tables can be copied using this method
     * (instances are copied by adding a new instance and copying the values to it in the table
     * editor). This command is executed in a separate thread since it can take a noticeable amount
     * time to complete, and by using a separate thread the GUI is allowed to continue to update.
     * The GUI menu commands, however, are disabled until the database command completes execution
     *
     * @param tableName
     *            name of the table to copy
     *
     * @param newName
     *            name of the table copy
     *
     * @param newDescription
     *            new description for this table
     *
     * @param tableDialog
     *            reference to the table manager dialog
     *********************************************************************************************/
    protected void copyTable(final String tableName,
                             final String newName,
                             final String newDescription,
                             final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /**************************************************************************************
             * Copy table command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Convert each of the table names to lower case and bound it with double
                    // quotes if it matches a PostgreSQL reserved word. PostgreSQL automatically
                    // assumes lower case (unless the name is quoted), so forcing the name to lower
                    // case is done here to differentiate the table name from the upper case
                    // database commands in the event log
                    String dbTableName = dbControl.getQuotedName(tableName);
                    String dbNewName = dbControl.getQuotedName(newName);

                    // Get the existing table's comment, description, and column order so that
                    // these can be used for the copy
                    String[] comment = queryDataTableComment(tableName, tableDialog);
                    String columnOrder = queryColumnOrder(tableName, comment[1], tableDialog);

                    String sequenceName = newName.toLowerCase()
                                          + "_"
                                          + DefaultColumn.PRIMARY_KEY.getDbName()
                                          + "_seq";

                    // Create the command to copy the original table
                    String command = "CREATE TABLE "
                                     + dbNewName
                                     + " (LIKE "
                                     + dbTableName
                                     + " INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES); ALTER TABLE "
                                     + dbNewName
                                     + " ALTER "
                                     + DefaultColumn.PRIMARY_KEY.getDbName()
                                     + " DROP DEFAULT; CREATE SEQUENCE "
                                     + sequenceName
                                     + "; INSERT INTO "
                                     + dbNewName
                                     + " SELECT * FROM "
                                     + dbTableName
                                     + "; ALTER SEQUENCE "
                                     + sequenceName
                                     + " OWNED BY "
                                     + dbNewName
                                     + "."
                                     + DefaultColumn.PRIMARY_KEY.getDbName()
                                     + "; SELECT setval('"
                                     + sequenceName
                                     + "', (SELECT max("
                                     + DefaultColumn.PRIMARY_KEY.getDbName()
                                     + ") FROM "
                                     + dbTableName
                                     + "), true); ALTER TABLE "
                                     + dbNewName
                                     + " ALTER "
                                     + DefaultColumn.PRIMARY_KEY.getDbName()
                                     + " SET DEFAULT nextval('"
                                     + sequenceName
                                     + "'); "
                                     + dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                                   dbNewName)
                                     + dbControl.buildOwnerCommand(DatabaseObject.SEQUENCE,
                                                                   sequenceName);

                    // Update the table comment to reflect the name with case intact, and create
                    // the commands to duplicate the original table's description and column order
                    command += buildDataTableComment(newName,
                                                     comment[TableCommentIndex.TYPE.ordinal()])
                               + buildTableDescription(newName, newDescription)
                               + buildColumnOrder(newName, columnOrder);

                    // Copy the table's data field entries for the new table
                    command += copyDataFieldCommand(tableName, newName, tableDialog);

                    // Execute the command to copy the table, including the table's original name
                    // (before conversion to all lower case) that's stored as a comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table '" + tableName + "' copied to '" + newName + "'");
                }
                catch (SQLException se)
                {
                    // Inform the user that copying the table failed
                    eventLog.logFailEvent(tableDialog,
                                          "Cannot copy table '"
                                                       + tableName
                                                       + "'; cause '"
                                                       + se.getMessage()
                                                       + "'",
                                          "<html><b>Cannot copy table '</b>" + tableName + "<b>'");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, tableDialog);
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Copy database table command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred copying the table
                if (!errorFlag)
                {
                    tableDialog.doTableOperationComplete();
                }
            }
        });
    }

    /**********************************************************************************************
     * Delete one or more prototype or script tables. The selected tables(s) are deleted from the
     * database, all references to the table are deleted from the custom values table, and any open
     * editors for tables of this prototype are closed. This command is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands, however, are
     * disabled until the database command completes execution
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param dialog
     *            reference to the table manager dialog calling this method; null if not called by
     *            the table manager
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void deleteTableInBackground(final String[] tableNames,
                                           final CcddTableManagerDialog dialog,
                                           final Component parent)
    {
        // Convert the array of names into a single string
        final String names = CcddUtilities.convertArrayToStringTruncate(tableNames);

        // Have the user confirm deleting the selected table(s)
        if (new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Delete table(s) '</b>"
                                                              + names
                                                              + "<b>'?<br><br><i>Warning: This action cannot be undone!",
                                                      "Delete Table(s)",
                                                      JOptionPane.QUESTION_MESSAGE,
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Execute the command in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                boolean errorFlag = false;

                /**********************************************************************************
                 * Database table deletion command
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    // Delete the table(s)
                    errorFlag = deleteTable(tableNames, dialog, parent);
                }

                /**********************************************************************************
                 * Delete database table command complete
                 *********************************************************************************/
                @Override
                protected void complete()
                {
                    // Check if no error occurred deleting the table and if the table manager
                    // called this method
                    if (!errorFlag && dialog != null)
                    {
                        dialog.doTableOperationComplete();
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Delete one or more prototype or script tables. The selected tables(s) are deleted from the
     * database, all references to the table are deleted from the custom values table, and any open
     * editors for tables of this prototype are closed
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param dialog
     *            reference to the table manager dialog calling this method; null if not called by
     *            the table manager
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if an error occurred when deleting a table
     *********************************************************************************************/
    protected boolean deleteTable(String[] tableNames,
                                  CcddTableManagerDialog dialog,
                                  Component parent)
    {
        boolean errorFlag = false;

        // Convert the array of names into a single string
        String names = CcddUtilities.convertArrayToStringTruncate(tableNames);

        try
        {
            // Build the command and delete the table(s). If the table manager called this method
            // (dialog isn't null) then these are data tables
            dbCommand.executeDbUpdate(deleteTableCommand(tableNames, dialog != null), parent);

            // Check if the deletion is for a data table. If the table manager called this method
            // (dialog isn't null) then these are data tables
            if (dialog != null)
            {
                // Execute the command to reset the rate for links that no longer contain any
                // variables
                dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);
            }

            // Log that the table deletion succeeded
            eventLog.logEvent(SUCCESS_MSG, "Table(s) '" + names + "' deleted");
        }
        catch (SQLException se)
        {
            // Inform the user that the table deletion failed
            eventLog.logFailEvent(parent,
                                  "Cannot delete table(s) '"
                                          + names
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot delete table(s) '</b>" + names + "<b>'");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Build the command to delete one or more prototype or script tables
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param isDataTable
     *            true if the table(s) to be deleted are data tables; false if the tables are
     *            scripts or internal tables
     *
     * @return Command to delete one or more prototype or script tables
     *********************************************************************************************/
    private String deleteTableCommand(String[] tableNames, boolean isDataTable)
    {
        // Build the table deletion commands
        String command = "DROP TABLE IF EXISTS ";

        // Step through the array of table names
        for (String name : tableNames)
        {
            // Add the table to the commands
            command += dbControl.getQuotedName(name) + ", ";
        }

        command = CcddUtilities.removeTrailer(command, ", ") + " CASCADE";

        // Check if these are data tables
        if (isDataTable)
        {
            String valuesDelCmd = "; DELETE FROM "
                                  + InternalTable.VALUES.getTableName()
                                  + " WHERE ";
            String linksDelCmd = "; DELETE FROM "
                                 + InternalTable.LINKS.getTableName()
                                 + " WHERE ";
            String tlmSchDelCmd = "; DELETE FROM "
                                  + InternalTable.TLM_SCHEDULER.getTableName()
                                  + " WHERE ";
            String groupsDelCmd = "; DELETE FROM "
                                  + InternalTable.GROUPS.getTableName()
                                  + " WHERE ";
            String fieldsDelCmd = "; DELETE FROM "
                                  + InternalTable.FIELDS.getTableName()
                                  + " WHERE ";
            String ordersDelCmd = "; DELETE FROM "
                                  + InternalTable.ORDERS.getTableName()
                                  + " WHERE ";
            String infoCmd = "";
            String assnsUpdCmd = "";

            // Step through the array of table names
            for (String name : tableNames)
            {
                // Add the table to the commands
                infoCmd += "col1 ~ E'^"
                           + name
                           + "$' OR col1 ~ E'^"
                           + name
                           + ",' OR col1 ~ E',"
                           + name
                           + "\\\\.' OR ";

                // Build the command to update the script associations table by removing references
                // to this table
                assnsUpdCmd += "; UPDATE "
                               + InternalTable.ASSOCIATIONS.getTableName()
                               + " SET "
                               + AssociationsColumn.MEMBERS.getColumnName()
                               + " = regexp_replace("
                               + AssociationsColumn.MEMBERS.getColumnName()
                               + ", E'(?:(?:^|(?:"
                               + PATH_IDENT
                               + ",))"
                               + name
                               + "(?:(?:,|\\\\.)"
                               + PATH_IDENT
                               + ")?"
                               + assnsSeparator
                               + "|"
                               + assnsSeparator
                               + "(?:^| \\+ |"
                               + PATH_IDENT
                               + ",)?"
                               + name
                               + "(?:(?:,|\\\\.)"
                               + PATH_IDENT
                               + ")?)', E'', 'g'); UPDATE "
                               + InternalTable.ASSOCIATIONS.getTableName()
                               + " SET "
                               + AssociationsColumn.MEMBERS.getColumnName()
                               + " = '' WHERE "
                               + AssociationsColumn.MEMBERS.getColumnName()
                               + " ~ E'^(?:"
                               + PATH_IDENT
                               + ",)?"
                               + name
                               + "(?:(?:,|\\\\.)"
                               + PATH_IDENT
                               + ")?$'";
            }

            // Replace the column 1 name placeholder with the table's column 1 name, then add the
            // update string to the command. In the telemetry scheduler table the table names are
            // preceded by the rate value and a separator which must be skipped
            valuesDelCmd += infoCmd.replaceAll("col1", ValuesColumn.TABLE_PATH.getColumnName());
            linksDelCmd += infoCmd.replaceAll("col1", LinksColumn.MEMBER.getColumnName());
            tlmSchDelCmd += infoCmd.replaceAll("col1", TlmSchedulerColumn.MEMBER.getColumnName())
                                   .replaceAll("\\^",
                                               "^\\\\\\\\d+\\\\\\\\.\\\\\\\\d+"
                                                      + "\\\\\\\\\\\\\\"
                                                      + TLM_SCH_SEPARATOR);
            groupsDelCmd += infoCmd.replaceAll("col1", GroupsColumn.MEMBERS.getColumnName());
            fieldsDelCmd += infoCmd.replaceAll("col1", FieldsColumn.OWNER_NAME.getColumnName());
            ordersDelCmd += infoCmd.replaceAll("col1", OrdersColumn.TABLE_PATH.getColumnName());

            // Add the internal table commands to the table deletion command
            command += CcddUtilities.removeTrailer(valuesDelCmd, " OR ")
                       + CcddUtilities.removeTrailer(linksDelCmd, " OR ")
                       + CcddUtilities.removeTrailer(tlmSchDelCmd, " OR ")
                       + CcddUtilities.removeTrailer(groupsDelCmd, " OR ")
                       + CcddUtilities.removeTrailer(fieldsDelCmd, " OR ")
                       + CcddUtilities.removeTrailer(ordersDelCmd, " OR ")
                       + assnsUpdCmd;
        }

        return command + ";";
    }

    /**********************************************************************************************
     * Load the contents of a single database table and display the data in a table editor. Do not
     * load the table if it is already open for editing. If the table is already open, then the
     * editor for this table is brought to the foreground. This command is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands, however, are
     * disabled until the database command completes execution
     *
     * @param tablePath
     *            table path in the format rootTable[,dataType1.variable1[,dataType2
     *            .variable2[,...]]]. The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top level structure table
     *            from which this table descends. The first data type/variable name pair is from
     *            the root table, with each succeeding pair coming from the next level down in the
     *            structure's hierarchy
     *
     * @param currentEditorDlg
     *            editor dialog to open the table in; null to open the table in a new editor dialog
     *
     * @return SwingWorker reference for this thread
     *********************************************************************************************/
    protected SwingWorker<?, ?> loadTableDataInBackground(String tablePath,
                                                          CcddTableEditorDialog currentEditorDlg)
    {
        return loadTableDataInBackground(new String[] {tablePath}, currentEditorDlg);
    }

    /**********************************************************************************************
     * Load the contents of an array of database tables and display the data in a table editor. Do
     * not load tables that are already open for editing. If no table is loaded, but one in the
     * list is already open, then the editor for this table is brought to the foreground. This
     * command is executed in a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue to update. The GUI
     * menu commands, however, are disabled until the database command completes execution
     *
     * @param tablePaths
     *            array of paths of the tables to load in the format
     *            rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]]. The table path for
     *            a non-structure table is simply the root table name. For a structure table the
     *            root table is the top level structure table from which this table descends. The
     *            first data type/variable name pair is from the root table, with each succeeding
     *            pair coming from the next level down in the structure's hierarchy
     *
     * @param callingEditorDlg
     *            table editor dialog in which to open the table; null to open the table in a new
     *            editor dialog
     *
     * @return SwingWorker reference for this thread
     *********************************************************************************************/
    protected SwingWorker<?, ?> loadTableDataInBackground(final String[] tablePaths,
                                                          final CcddTableEditorDialog callingEditorDlg)
    {
        // Execute the command in the background
        return CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            private final List<TableInformation> tableInformation = new ArrayList<TableInformation>();

            CcddTableEditorDialog tableEditorDlg = null;
            CcddTableEditorHandler tableEditor = null;

            // Get the component over which any dialogs need to be centered
            Component parent = (callingEditorDlg == null)
                                                          ? ccddMain.getMainFrame()
                                                          : callingEditorDlg;

            /**************************************************************************************
             * Load database table command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Step through each table
                    for (int index = 0; index < tablePaths.length; index++)
                    {
                        boolean isOpen = false;

                        // Step through the open editor dialogs
                        for (CcddTableEditorDialog editorDlg : ccddMain.getTableEditorDialogs())
                        {
                            // Step through each individual editor in the editor dialog
                            for (CcddTableEditorHandler editor : editorDlg.getTableEditors())
                            {
                                // Check if the table path matches the editor table
                                if (tablePaths[index].equals(editor.getTableInformation().getTablePath()))
                                {
                                    // Store the editor dialog and editor for the table, and stop
                                    // searching
                                    tableEditorDlg = editorDlg;
                                    tableEditor = editor;
                                    isOpen = true;
                                    break;
                                }
                            }

                            // Check if the table editor was found
                            if (tableEditor != null)
                            {
                                // Stop searching
                                break;
                            }
                        }

                        // Check if the table is not already open in an editor
                        if (!isOpen)
                        {
                            // Get the information from the database for the specified table
                            TableInformation tableInfo = loadTableData(tablePaths[index],
                                                                       true,
                                                                       true,
                                                                       parent);

                            // Check if the table failed to load successfully
                            if (!tableInfo.isErrorFlag())
                            {
                                // Store the table information in the list
                                tableInformation.add(tableInfo);
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, parent);
                }
            }

            /**************************************************************************************
             * Load database table command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if at least one table was successfully loaded
                if (!tableInformation.isEmpty())
                {
                    // Check if no existing editor dialog was supplied
                    if (callingEditorDlg == null)
                    {
                        // Display the table contents in a new table editor dialog
                        ccddMain.getTableEditorDialogs().add(new CcddTableEditorDialog(ccddMain,
                                                                                       tableInformation));
                    }
                    // A table editor dialog is supplied
                    else
                    {
                        // Display the table contents in an existing table editor dialog
                        callingEditorDlg.addTablePanes(tableInformation);
                    }
                }
                // Check if no table data was loaded due to the table(s) already being open in an
                // editor
                else if (tableEditorDlg != null)
                {
                    // Bring the editor dialog to the foreground
                    tableEditorDlg.toFront();

                    // Step through each tab in the editor dialog
                    for (int index = 0; index < tableEditorDlg.getTabbedPane().getTabCount(); index++)
                    {
                        // Check if the tab name matches the last table name in the list of those
                        // to be opened
                        if (tableEditorDlg.getTabbedPane().getComponentAt(index).equals(tableEditor.getFieldPanel()))
                        {
                            // Bring the editor tab to the front and stop searching
                            tableEditorDlg.getTabbedPane().setSelectedIndex(index);
                            break;
                        }
                    }
                }

                // Step through each table opened
                for (String tableName : tablePaths)
                {
                    // Update the recently opened tables list and store it in the program
                    // preferences
                    CcddUtilities.updateRememberedItemList(tableName,
                                                           ccddMain.getRecentTableNames(),
                                                           ModifiableSizeInfo.NUM_REMEMBERED_TABLES.getSize());
                    ccddMain.getProgPrefs().put(TABLE_STRINGS,
                                                CcddUtilities.getRememberedItemListAsString(ccddMain.getRecentTableNames()));
                }

                // Update the command menu items in the main window and table editor dialogs
                ccddMain.updateRecentTablesMenu();
            }
        });
    }

    /**********************************************************************************************
     * Perform the database query to load the contents of a database table. The data is sorted in
     * ascending numerical order based on the index (primary key) column
     *
     * @param tablePath
     *            table path in the format rootTable[,dataType1.variable1[,dataType2
     *            .variable2[,...]]]. The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top level structure table
     *            from which this table descends. The first data type/variable name pair is from
     *            the root table, with each succeeding pair coming from the next level down in the
     *            structure's hierarchy
     *
     * @param loadDescription
     *            true to load the table's description
     *
     * @param loadColumnOrder
     *            true to load the table's column order
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return TableInformation class containing the table data from the database. If the error
     *         flag is set the an error occurred and the data is invalid
     *********************************************************************************************/
    protected TableInformation loadTableData(String tablePath,
                                             boolean loadDescription,
                                             boolean loadColumnOrder,
                                             Component parent)
    {
        // Create an empty table information class
        TableInformation tableInfo = new TableInformation(tablePath);

        try
        {
            // Strip the variable name, if present, from the table name to get the table's
            // prototype table name
            String tableName = tableInfo.getPrototypeName();

            // Check if the table doesn't exist in the variable list (if it's a structure table) or
            // in the database (if it's another table type)
            if (!variableHandler.getStructureAndVariablePaths().contains(tablePath)
                && !isTableExists(tableName, parent))
            {
                throw new CCDDException("Table doesn't exist");
            }

            // Convert the table name to lower case and bound it with double quotes if it matches a
            // PostgreSQL reserved word. PostgreSQL automatically assumes lower case (unless the
            // name is quoted), so forcing the name to lower case is done here to differentiate the
            // table name from the upper case database commands in the event log
            String dbTableName = dbControl.getQuotedName(tableName);

            // Get the table comment
            String[] comment = queryDataTableComment(tableName, parent);

            // Get the table type definition for this table
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(comment[TableCommentIndex.TYPE.ordinal()]);

            // Check if the table type definition is valid
            if (typeDefn == null)
            {
                throw new CCDDException("Invalid table type");
            }

            // Get a comma-separated list of the columns for this table's type
            String columnNames = CcddUtilities.convertArrayToString(typeDefn.getColumnNamesDatabaseQuoted());

            // Get the table's row information for the specified columns. The table must have all
            // of its table type's columns or else it fails to load
            ResultSet rowData = dbCommand.executeDbQuery("SELECT "
                                                         + columnNames
                                                         + " FROM "
                                                         + dbTableName
                                                         + " ORDER BY "
                                                         + DefaultColumn.ROW_INDEX.getDbName()
                                                         + ";",
                                                         parent);

            // Create a list to contain the database table rows
            List<Object[]> dbRows = new ArrayList<Object[]>();

            // Step through each of the query results
            while (rowData.next())
            {
                // Create an array to contain the column values
                Object[] columnValues = new Object[typeDefn.getColumnCountDatabase()];

                // Step through each column in the row
                for (int column = 0; column < typeDefn.getColumnCountDatabase(); column++)
                {
                    // Add the column value to the array. Note that the first column's index in
                    // the database is 1, not 0
                    columnValues[column] = rowData.getString(column + 1);

                    // Check if the value is null
                    if (columnValues[column] == null)
                    {
                        // Replace the null with a blank
                        columnValues[column] = "";
                    }
                    // Check if the input type for this column is a boolean
                    else if (typeDefn.getInputTypes()[column].getInputFormat() == InputTypeFormat.BOOLEAN)
                    {
                        // Store the column value as a boolean
                        columnValues[column] = columnValues[column].toString().equalsIgnoreCase("true")
                                                                                                        ? true
                                                                                                        : false;
                    }
                }

                // Add the row data to the list
                dbRows.add(columnValues);
            }

            rowData.close();

            // Create the table information handler for this table
            tableInfo = new TableInformation(comment[TableCommentIndex.TYPE.ordinal()],
                                             tablePath,
                                             dbRows.toArray(new Object[0][0]),
                                             (loadColumnOrder
                                                              ? queryColumnOrder(tablePath,
                                                                                 comment[TableCommentIndex.TYPE.ordinal()],
                                                                                 parent)
                                                              : ""),
                                             (loadDescription
                                                              ? queryTableDescription(tablePath,
                                                                                      parent)
                                                              : ""),
                                             fieldHandler.getFieldInformationByOwnerCopy(tablePath));

            // Get the index of the variable name and data type columns
            int varNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
            int dataTypeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);

            // Check if the variable name and data type columns exist, and if the table has a path
            // (i.e., it's a child table). If so it may have values in the custom values table that
            // must be loaded
            if (varNameIndex != -1 && dataTypeIndex != -1 && tablePath.contains(","))
            {
                // Get the column index for the variable path
                int varPathIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE_PATH);

                // Check if the variable path column is present
                if (varPathIndex != -1)
                {
                    // Step through each row in the table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        // Blank the variable path. This prevents the child table from inheriting a
                        // user-defined variable path from the prototype
                        tableInfo.getData()[row][varPathIndex] = "";
                    }
                }

                // Place double back slashes before each square brace character in an array index
                // so that the brackets are interpreted correctly in the query's regular expression
                // comparisons
                tablePath = tablePath.replaceAll("\\[(\\d+)\\]", "\\\\\\\\[$1\\\\\\\\]");

                // Get the rows from the custom values table that match the specified parent table
                // and variable path. These values replace those loaded for the prototype of this
                // table
                rowData = dbCommand.executeDbQuery("SELECT * FROM "
                                                   + InternalTable.VALUES.getTableName()
                                                   + " WHERE "
                                                   + ValuesColumn.TABLE_PATH.getColumnName()
                                                   + " ~ E'^"
                                                   + tablePath
                                                   + ",[^,]+$' AND "
                                                   + ValuesColumn.COLUMN_NAME.getColumnName()
                                                   + " != '';",
                                                   parent);

                // Step through each of the query results
                while (rowData.next())
                {
                    // Get the variable name that will have its value replaced
                    String variableName = rowData.getString(ValuesColumn.TABLE_PATH.getColumnName());

                    // Get the index of the last data type/variable name separator character (if
                    // present)
                    int varIndex = variableName.lastIndexOf(".");

                    // Check if a variable name exists
                    if (varIndex != -1)
                    {
                        // Get the row index for the referenced variable
                        int row = typeDefn.getRowIndexByColumnValue(tableInfo.getData(),
                                                                    variableName.substring(varIndex + 1),
                                                                    varNameIndex);

                        // Check if the table contains the variable and if the data type of the
                        // variable in the table matches the data type in the path from the custom
                        // values table
                        if (row != -1
                            && tableInfo.getData()[row][dataTypeIndex].equals(variableName.subSequence(variableName.lastIndexOf(",")
                                                                                                       + 1,
                                                                                                       varIndex)))
                        {
                            // Get the index of the column that will have its data replaced
                            int column = typeDefn.getColumnIndexByUserName(rowData.getString(ValuesColumn.COLUMN_NAME.getColumnName()));

                            // Check if the table contains the column
                            if (column != -1)
                            {
                                // Check if the input type for this column is a boolean
                                if (typeDefn.getInputTypes()[column].getInputFormat() == InputTypeFormat.BOOLEAN)
                                {
                                    // Store the column value as a boolean
                                    tableInfo.getData()[row][column] = rowData.getString(ValuesColumn.VALUE.getColumnName()).equalsIgnoreCase("true")
                                                                                                                                                      ? true
                                                                                                                                                      : false;
                                }
                                // Not a boolean
                                else
                                {
                                    // Replace the value in the table with the one from the custom
                                    // values table
                                    tableInfo.getData()[row][column] = rowData.getString(ValuesColumn.VALUE.getColumnName());
                                }
                            }
                        }
                    }
                }

                rowData.close();
            }
        }
        catch (SQLException | CCDDException se)
        {
            // Inform the user that loading the table failed
            eventLog.logFailEvent(parent,
                                  "Cannot load table '"
                                          + tablePath
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot load table '</b>"
                                                 + tablePath
                                                 + "<b>'");
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
        }

        return tableInfo;
    }

    /**********************************************************************************************
     * Perform the database query to load the rows from the custom values table that match the
     * specified column name and column value
     *
     * @param columnName
     *            name to match in the custom values table 'column name' column
     *
     * @param columnValue
     *            value to match in the custom values table 'value' column; null or blank to match
     *            any value
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing arrays with the row data (table path, column name, and value) from
     *         the custom values table for those rows that match the specified column name and
     *         column value
     *********************************************************************************************/
    protected List<String[]> getCustomValues(String columnName,
                                             String columnValue,
                                             Component parent)
    {
        List<String[]> customValues = new ArrayListMultiple();

        try
        {
            // Get the row data from the custom values table for all columns with a matching column
            // name and column value
            ResultSet rowData = dbCommand.executeDbQuery("SELECT * FROM "
                                                         + InternalTable.VALUES.getTableName()
                                                         + " WHERE "
                                                         + ValuesColumn.COLUMN_NAME.getColumnName()
                                                         + " = '"
                                                         + columnName
                                                         + "'"
                                                         + (columnValue == null
                                                            || columnValue.isEmpty()
                                                                                     ? ""
                                                                                     : " AND "
                                                                                       + ValuesColumn.VALUE.getColumnName()
                                                                                       + " = '"
                                                                                       + columnValue
                                                                                       + "'")
                                                         + ";",
                                                         parent);

            // Step through each of the query results
            while (rowData.next())
            {
                // Add the row data from the matching row to the array list
                customValues.add(new String[] {rowData.getString(ValuesColumn.TABLE_PATH.getColumnName()),
                                               rowData.getString(ValuesColumn.COLUMN_NAME.getColumnName()),
                                               rowData.getString(ValuesColumn.VALUE.getColumnName())});
            }

            rowData.close();
        }
        catch (SQLException se)
        {
            // Inform the user that loading the custom values failed
            eventLog.logFailEvent(parent,
                                  "Cannot load data from the custom values table; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot load data from the custom values table");
        }

        return customValues;
    }

    /**********************************************************************************************
     * Store the rate parameters in the project database and update the database structure-related
     * functions
     *
     * @param parent
     *            component calling this method, used for positioning any error dialogs
     *********************************************************************************************/
    protected void storeRateParameters(Component parent)
    {
        // Build the string containing the rate parameters
        String comment = rateHandler.getMaxSecondsPerMsg()
                         + ","
                         + rateHandler.getMaxMsgsPerSecond()
                         + ","
                         + rateHandler.isIncludeUneven();

        // Step through each stream
        for (RateInformation rateInfo : rateHandler.getRateInformation())
        {
            // Build the string containing the rate name and parameters. The name is placed within
            // double quotes
            comment += ",\""
                       + rateInfo.getRateName()
                       + "\",\""
                       + rateInfo.getStreamName()
                       + "\","
                       + rateInfo.getMaxMsgsPerCycle()
                       + ","
                       + rateInfo.getMaxBytesPerSec();
        }

        // Update the the stored rate parameters
        setTableComment(InternalTable.TLM_SCHEDULER.getTableName(), comment, parent);
    }

    /**********************************************************************************************
     * Create a list of all prototype tables with their child tables (prototypes and instances),
     * and primitive variables (if specified). The table must contain all of the protected columns
     * defined for a structure in order for its members to be determined. Non-structure tables are
     * included in the returned list, but by definition have no members
     *
     * @param memberType
     *            Type of table members to load: TABLES_ONLY to exclude primitive variables or
     *            INCLUDE_PRIMITIVES to include tables and primitive variables
     *
     * @param sortByName
     *            true to return the table members in alphabetical order (e.g., for use in a tree);
     *            false to return the members sorted by row index (e.g., for use in determining the
     *            variable offsets in the structure)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing the table member information. For structure tables the member tables
     *         are included, along with primitive variables (if specified), sorted by variable name
     *         or row index as specified
     *********************************************************************************************/
    protected List<TableMembers> loadTableMembers(TableMemberType memberType,
                                                  boolean sortByName,
                                                  final Component parent)
    {
        List<TableMembers> tableMembers = new ArrayList<TableMembers>();

        try
        {
            // Get the list of prototype tables in alphabetical order
            String[] tableNames = queryTableList(parent);

            // Get the comments for all data tables
            String[][] comments = queryDataTableComments(parent);

            // Get the table members of all structure tables by extracting the values from the
            // table's data type and variable name columns, if present, sorted by variable name or
            // table index. Non-structure tables and structure tables with no rows are skipped. The
            // information returned by the PostgreSQL function also returns the bit length,
            // rate(s), and enumeration(s) for each member; the enumeration information currently
            // isn't used
            ResultSet rowData = dbCommand.executeDbQuery("SELECT * FROM "
                                                         + (sortByName
                                                                       ? "get_table_members_by_name();"
                                                                       : "get_table_members_by_index();"),
                                                         parent);

            // Create a list to contain the database table member data types, variable names, bit
            // lengths, and rates
            List<String> dataTypes;
            List<String> variableNames;
            List<String> bitLengths;
            List<String[]> rates;

            // Set the flag based on if any data was returned by the database query
            boolean doLoop = rowData.next();

            // Step through the query results
            while (doLoop)
            {
                // Initialize the data type, variable, bit length, rate(s), and enumeration(s)
                // lists
                dataTypes = new ArrayList<String>();
                variableNames = new ArrayList<String>();
                bitLengths = new ArrayList<String>();
                rates = new ArrayList<String[]>();

                // Get the table name for this query table row
                String tableName = rowData.getString(1);

                do
                {
                    // Get the data type, variable name, bit length, and rate(s) from this query
                    // row
                    String dataType = rowData.getString(2);
                    String variableName = rowData.getString(3);
                    String bitLength = rowData.getString(4);
                    String[] rate = rowData.getString(5).split(",", rateHandler.getNumRateColumns());

                    // Check if a data type and variable name exist, and that the data type is not
                    // a primitive type (i.e., this is a structure) or if primitive types are to be
                    // included
                    if (dataType != null
                        && !dataType.isEmpty()
                        && variableName != null
                        && !variableName.isEmpty()
                        && (!dataTypeHandler.isPrimitive(dataType)
                            || memberType == TableMemberType.INCLUDE_PRIMITIVES))
                    {
                        // Get the number of variable names in the list
                        int addIndex = variableNames.size();

                        // Check if a variable name is already in the list and that the data is
                        // sorted by variable name
                        if (addIndex != 0 && sortByName)
                        {
                            // Get the name of the last variable added to the list
                            String lastVarName = variableNames.get(addIndex - 1);

                            // Check if the current and previous variables are members of the same
                            // array
                            if (ArrayVariable.isArrayMember(variableName)
                                && ArrayVariable.isArrayMember(lastVarName)
                                && ArrayVariable.removeArrayIndex(variableName)
                                                .equals(ArrayVariable.removeArrayIndex(lastVarName)))
                            {
                                // The PostgreSQL function that obtains the table members sorted by
                                // variable name treats array indices as strings, so array member
                                // [10] appears immediately after member [1] instead of after [9].
                                // This code section determines the position in the list where the
                                // current array member should be placed relative to those members
                                // already in the list, sorted numerically by array dimension
                                // value(s)

                                boolean notFound = true;

                                do
                                {
                                    // Compare the two array members numerically, by array
                                    // dimension value(s)
                                    int result = ArrayVariable.compareTo(variableName,
                                                                         variableNames.get(addIndex - 1));

                                    // Check if the array member's dimension value(s) places it
                                    // before the comparison array member
                                    if (result < 0)
                                    {
                                        // Decrement the variable insertion index
                                        addIndex--;
                                    }
                                    // Check if the array member's dimension value(s) places it
                                    // after the comparison array member
                                    else if (result > 0)
                                    {
                                        // Set the flag indicating that the position of the current
                                        // variable in the list is located and stop checking the
                                        // dimension values
                                        notFound = false;
                                    }
                                } while (notFound && addIndex > 0);
                                // Continue to adjust the insertion index as long as the current
                                // variable's array indices places it prior to the that of the
                                // variable at the current insertion index. The test for addIndex >
                                // 0 accounts for the possibility that array members are missing in
                                // the table
                            }
                        }

                        // Add the data type, variable name, bit length, rate(s), and
                        // enumeration(s) to the lists for this table
                        dataTypes.add(addIndex, dataType);
                        variableNames.add(addIndex, variableName);
                        bitLengths.add(addIndex, bitLength);
                        rates.add(addIndex, rate);
                    }

                    // Go to the next row in the query results; set the flag to true if the row
                    // exists
                    doLoop = rowData.next();

                    // Continue to loop while rows exist to process in the query table and the
                    // table name for the new row remains the same
                } while (doLoop && tableName.equals(rowData.getString(1)));

                // Get the comment array for this table
                String[] comment = getTableComment(tableName, comments);

                // Add the table name, table type, and the table's member information to the
                // members list
                tableMembers.add(new TableMembers(comment[TableCommentIndex.NAME.ordinal()],
                                                  comment[TableCommentIndex.TYPE.ordinal()],
                                                  dataTypes,
                                                  variableNames,
                                                  bitLengths,
                                                  rates));
            }

            rowData.close();

            // Create storage for the tables not added above; i.e., for non-structure tables and
            // for structure tables containing no rows
            List<TableMembers> newMembers = new ArrayList<TableMembers>();

            // Step through the table names
            for (String tableName : tableNames)
            {
                boolean isFound = false;

                // Step through the structure table members
                for (TableMembers member : tableMembers)
                {
                    // Check if the table name matches the one in the member list
                    if (tableName.equals(member.getTableName()))
                    {
                        // Set the flag to indicate this table is already in the member list and
                        // stop searching
                        isFound = true;
                        break;
                    }
                }

                // Check if the table is not already in the member list
                if (!isFound)
                {
                    // Get the comment array for this table
                    String[] comment = getTableComment(tableName, comments);

                    // Add the table to the member list with empty data type, variable name, bit
                    // length, and rate lists
                    newMembers.add(new TableMembers(tableName,
                                                    comment[TableCommentIndex.TYPE.ordinal()],
                                                    new ArrayList<String>(),
                                                    new ArrayList<String>(),
                                                    new ArrayList<String>(),
                                                    new ArrayList<String[]>()));
                }
            }

            // Add the new members to the member list. The list now contains a reference to all
            // tables
            tableMembers.addAll(newMembers);

            // Sort the table members by table name in ascending order
            Collections.sort(tableMembers, new Comparator<TableMembers>()
            {
                /**********************************************************************************
                 * Compare the table names of two member definitions, ignoring case
                 *********************************************************************************/
                @Override
                public int compare(TableMembers mem1, TableMembers mem2)
                {
                    return mem1.getTableName().compareToIgnoreCase(mem2.getTableName());
                }
            });
        }
        catch (SQLException se)
        {
            // Inform the user that loading the table members failed
            eventLog.logFailEvent(parent,
                                  "Cannot load table members; cause '" + se.getMessage() + "'",
                                  "<html><b>Cannot load table members");
            tableMembers = null;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
        }

        return tableMembers;
    }

    /**********************************************************************************************
     * Add, modify, and/or delete data in a table. This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param tableInfo
     *            table information
     *
     * @param additions
     *            list of row addition information
     *
     * @param modifications
     *            list of row update information
     *
     * @param deletions
     *            list of row deletion information
     *
     * @param forceUpdate
     *            true to make the changes to other tables; false to only make changes to tables
     *            other than the one in which the changes originally took place
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the internal tables. This is
     *            used during a data type update where only the data type name has changed in order
     *            to speed up the operation
     *
     * @param updateDescription
     *            true to update the table description from the table information; false to not
     *            change the table description
     *
     * @param updateColumnOrder
     *            true to update the table column order from the table information; false to not
     *            change the table column order
     *
     * @param updateFieldInfo
     *            true to update the table data fields from the table information; false to not
     *            change the table data fields
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a reference to the
     *            current data type handler) if the change does not originate from the data type
     *            editor
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void modifyTableDataInBackground(final TableInformation tableInfo,
                                               final List<TableModification> additions,
                                               final List<TableModification> modifications,
                                               final List<TableModification> deletions,
                                               final boolean forceUpdate,
                                               final boolean skipInternalTables,
                                               final boolean updateDescription,
                                               final boolean updateColumnOrder,
                                               final boolean updateFieldInfo,
                                               final CcddDataTypeHandler newDataTypeHandler,
                                               final Component parent)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Modify table data command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                modifyTableData(tableInfo,
                                additions,
                                modifications,
                                deletions,
                                forceUpdate,
                                skipInternalTables,
                                updateDescription,
                                updateColumnOrder,
                                updateFieldInfo,
                                newDataTypeHandler,
                                parent);
            }
        });
    }

    /**********************************************************************************************
     * Add, modify, and/or delete data in a table. If the table is a prototype then its database
     * table is altered; if the table is an instance then the changes are made to the custom values
     * table
     *
     * @param tableInfo
     *            table information
     *
     * @param additions
     *            list of row addition information
     *
     * @param modifications
     *            list of row update information
     *
     * @param deletions
     *            list of row deletion information
     *
     * @param forceUpdate
     *            true to make the changes to other data tables; false to only make changes to
     *            tables other than the one in which the changes originally took place
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the internal tables. This is
     *            used during a data type update where only the data type name has changed in order
     *            to speed up the operation
     *
     * @param updateDescription
     *            true to update the table description from the table information; false to not
     *            change the table description
     *
     * @param updateColumnOrder
     *            true to update the table column order from the table information; false to not
     *            change the table column order
     *
     * @param updateFieldInfo
     *            true to update the table data fields from the table information; false to not
     *            change the table data fields
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a reference to the
     *            current data type handler) if the change does not originate from the data type
     *            editor
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if an error occurs while updating the table
     *********************************************************************************************/
    protected boolean modifyTableData(TableInformation tableInfo,
                                      List<TableModification> additions,
                                      List<TableModification> modifications,
                                      List<TableModification> deletions,
                                      boolean forceUpdate,
                                      boolean skipInternalTables,
                                      boolean updateDescription,
                                      boolean updateColumnOrder,
                                      boolean updateFieldInfo,
                                      CcddDataTypeHandler newDataTypeHandler,
                                      Component parent)
    {
        boolean errorFlag = false;
        ReferenceCheckResults msgIDRefChk = null;
        boolean isRefFieldChange = false;

        try
        {
            CcddTableTreeHandler tableTree = null;
            ToolTipTreeNode orgTableNode = null;
            updateLinks = false;
            addLinkHandler = null;
            isVariablePathChange = false;
            isCommandChange = false;
            isMsgIDChange = false;
            ReferenceCheckResults varRefChk = null;
            ReferenceCheckResults cmdRefChk = null;

            // Get the name of the table to modify and convert the table name to lower case.
            // PostgreSQL automatically does this, so it's done here just to differentiate the
            // table name from the database commands in the event log
            String dbTableName = dbControl.getQuotedName(tableInfo.getPrototypeName());

            // Get the table type definition
            TypeDefinition typeDefinition = tableTypeHandler.getTypeDefinition(tableInfo.getType());

            // Check if references in the internal tables are to be updated and the table
            // represents a structure
            if (!skipInternalTables && typeDefinition.isStructure())
            {
                deletedArrayDefns = new ArrayList<String>();

                // Create the table tree. Suppress any warning messages when creating this tree
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                     true,
                                                     parent);

                // Check if the table is a prototype
                if (tableInfo.isPrototype())
                {
                    // Copy the table tree node for the prototype table. This preserves a copy of
                    // the table's variables before the changes are applied
                    orgTableNode = copyPrototypeTableTreeNode(tableInfo.getPrototypeName(),
                                                              tableTree);
                }

                // Check if there are any modifications or deletions (additions won't change the
                // values in table cells or data fields with the variable reference input type)
                if (!modifications.isEmpty() || !deletions.isEmpty())
                {
                    // Get the references in the table type and data field internal tables that use
                    // the variable reference input type. If a variable name is changed or deleted
                    // then the tables and fields may require updating
                    varRefChk = inputTypeHandler.getInputTypeReferences(DefaultInputType.VARIABLE_REFERENCE,
                                                                        parent);
                    isRefFieldChange |= varRefChk.isFieldUsesType();
                }
            }
            // Check if the table represents a command and if there are any modifications or
            // deletions (additions won't change the values in table cells or data fields with the
            // command reference input type)
            else if (typeDefinition.isCommand()
                     && (!modifications.isEmpty() || !deletions.isEmpty()))
            {
                // Get the references in the table type and data field internal tables that use the
                // command reference input type. If a command name, code, or argument is changed or
                // deleted then the tables and fields may require updating
                cmdRefChk = inputTypeHandler.getInputTypeReferences(DefaultInputType.COMMAND_REFERENCE,
                                                                    parent);
                isRefFieldChange |= cmdRefChk.isFieldUsesType();
            }

            // Get the references in the table type and data field internal tables that use the
            // message name & ID input type. If a message name or ID is added, changed, or deleted
            // then the tables and fields may require updating
            msgIDRefChk = inputTypeHandler.getInputTypeReferences(DefaultInputType.MESSAGE_REFERENCE,
                                                                  parent);
            isRefFieldChange |= msgIDRefChk.isFieldUsesType();

            // Get the table's description
            String description = tableInfo.getDescription();

            // Check if this table is an instance and that the description is the same as the one
            // for the prototype of this table
            if (!tableInfo.isPrototype()
                && description.equals(queryTableDescription(tableInfo.getPrototypeName(), parent)))
            {
                // Set the description to a blank since it inherits it from the prototype
                description = "";
            }

            // Combine the table, data fields table, table description, and column order update
            // commands. If the flag is set to update the data fields then the entire fields table
            // is rewritten. This must precede applying the table updates since these makes further
            // changes to the fields table
            StringBuilder command = new StringBuilder((updateFieldInfo
                                                                       ? modifyFieldsCommand(tableInfo.getTablePath(),
                                                                                             tableInfo.getFieldInformation())
                                                                       : "")
                                                      + (updateDescription
                                                                           ? buildTableDescription(tableInfo.getTablePath(),
                                                                                                   description)
                                                                           : "")
                                                      + (updateColumnOrder
                                                                           ? buildColumnOrder(tableInfo.getTablePath(),
                                                                                              tableInfo.getColumnOrder())
                                                                           : ""));

            // Build the commands to add, modify, and delete table rows, and to update any table
            // cells or data fields that have the message name & ID input type if a message name or
            // ID value is changed
            command.append(buildAdditionCommand(tableInfo,
                                                additions,
                                                dbTableName,
                                                typeDefinition,
                                                skipInternalTables)
                           + buildModificationCommand(tableInfo,
                                                      modifications,
                                                      dbTableName,
                                                      typeDefinition,
                                                      newDataTypeHandler,
                                                      tableTree,
                                                      skipInternalTables,
                                                      varRefChk,
                                                      cmdRefChk)
                           + buildDeletionCommand(tableInfo,
                                                  deletions,
                                                  dbTableName,
                                                  typeDefinition,
                                                  skipInternalTables,
                                                  varRefChk,
                                                  cmdRefChk)
                           + updateMessageNameAndIDReference(tableInfo,
                                                             typeDefinition,
                                                             modifications,
                                                             deletions,
                                                             msgIDRefChk));

            // Check if a command was generated (e.g., the additions, modifications, and deletions
            // lists aren't empty)
            if (command.length() != 0)
            {
                // Execute the commands
                dbCommand.executeDbUpdate(command.toString(), parent);

                // Check if references in the internal tables are to be updated
                if (!skipInternalTables && typeDefinition.isStructure())
                {
                    // Check if the table is a structure prototype and that the table had one or
                    // more variables to begin with
                    if (tableInfo.isPrototype() && tableInfo.getData().length > 0)
                    {
                        // Build the command to delete bit-packed variable references in the links
                        // and telemetry scheduler tables that changed due to the table
                        // modifications
                        command = new StringBuilder(updateLinksAndTlmForPackingChange(orgTableNode,
                                                                                      parent));

                        // Check if there are any bit-packed variable references to delete
                        if (command.length() != 0)
                        {
                            // Delete invalid bit-packed variable references
                            dbCommand.executeDbUpdate(command.toString(), parent);
                        }

                        // Check if the link definitions changed
                        if (updateLinks)
                        {
                            // Store the updated link definitions in the project database
                            storeInformationTable(InternalTable.LINKS,
                                                  addLinkHandler.getLinkDefinitions(),
                                                  null,
                                                  parent);
                        }
                    }

                    // Execute the command to reset the rate for links that no longer contain any
                    // variables
                    dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);
                }

                // Check if the table type is a structure
                if (typeDefinition.isStructure())
                {
                    // Update the list of root structure tables
                    rootStructures = getRootStructures(parent);
                }

                // Log that inserting data into the table succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  "Table '"
                                               + tableInfo.getProtoVariableName()
                                               + "' data modified");
            }
        }
        catch (SQLException se)
        {
            // Inform the user that updating the table failed
            eventLog.logFailEvent(parent,
                                  "Cannot modify data in table '"
                                          + tableInfo.getProtoVariableName()
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot modify data in table '</b>"
                                                 + tableInfo.getProtoVariableName()
                                                 + "<b>'");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        // Check that no error occurred
        if (!errorFlag)
        {
            // Check if a variable has been added or deleted, or an existing variable's name, data
            // type, array size, or bit length has changed
            if (isVariablePathChange)
            {
                // Rebuild the variable paths and offsets lists
                variableHandler.buildPathAndOffsetLists();
            }
            // Check if a data field exists that uses the variable reference, command reference, or
            // message name & ID input type
            if (isRefFieldChange)
            {
                // Rebuild the data field information from the the field definitions stored in the
                // database
                fieldHandler.buildFieldInformation(parent);
            }
            // Check if the table's data fields were updated
            else if (updateFieldInfo)
            {
                // Update the table's data field information in the field handler
                fieldHandler.replaceFieldInformationByOwner(tableInfo.getTablePath(),
                                                            tableInfo.getFieldInformation());
            }

            // Check if a command has been added or deleted, or an existing command's name, code,
            // or argument(s) has changed
            if (isCommandChange)
            {
                // Rebuild the command list
                commandHandler.buildCommandList();
            }

            // Check if a data field exists that references the message name & ID input type
            if (isMsgIDChange)
            {
                // Update the message name & ID input type list
                inputTypeHandler.updateMessageReferences(parent);
            }

            // Make changes to any open table editors
            CcddTableEditorDialog.doTableModificationComplete(ccddMain,
                                                              tableInfo,
                                                              modifications,
                                                              deletions,
                                                              forceUpdate,
                                                              isRefFieldChange,
                                                              isMsgIDChange);
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Build the command to add table rows. Only prototype tables can have a row added
     *
     * @param tableInfo
     *            table information
     *
     * @param additions
     *            list of row addition information
     *
     * @param dbTableName
     *            name of the table to which to add rows
     *
     * @param typeDefn
     *            table type definition
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the internal tables. This is
     *            used during a data type update where only the data type name has changed in order
     *            to speed up the operation
     *
     * @return Table row addition command
     *********************************************************************************************/
    private String buildAdditionCommand(TableInformation tableInfo,
                                        List<TableModification> additions,
                                        String dbTableName,
                                        TypeDefinition typeDefn,
                                        boolean skipInternalTables)
    {
        StringBuilder addCmd = new StringBuilder("");

        // Check if there are any table additions
        if (!additions.isEmpty())
        {
            List<String> stringArrays = new ArrayList<String>();
            StringBuilder valuesAddCmd = new StringBuilder("");
            StringBuilder groupsAddCmd = new StringBuilder("");
            StringBuilder fieldsAddCmd = new StringBuilder("");
            StringBuilder ordersAddCmd = new StringBuilder("");
            StringBuilder assnsAddCmd = new StringBuilder("");
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");

            // Create the insert table data command. The array of column names is converted to a
            // string
            addCmd.append("INSERT INTO "
                          + dbTableName
                          + " ("
                          + CcddUtilities.convertArrayToString(typeDefn.getColumnNamesDatabaseQuoted())
                          + ") VALUES ");

            // Step through each addition
            for (TableModification add : additions)
            {
                addCmd.append("(DEFAULT, ");

                // For each column in the matching row
                for (int column = 0; column < add.getRowData().length; column++)
                {
                    // Check that this isn't the primary key column
                    if (column != DefaultColumn.PRIMARY_KEY.ordinal())
                    {
                        // Append the column value
                        addCmd.append(delimitText(add.getRowData()[column]) + ", ");
                    }
                }

                // Remove the ending comma and space, append the closing parenthesis, and add the
                // command to add this row
                addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
                addCmd.append("), ");

                // Check if internal tables are to be updated and the parent table is a structure
                if (!skipInternalTables && typeDefn.isStructure())
                {
                    // Since a variable has been added set the flag to force the variable paths and
                    // offsets lists to be rebuilt
                    isVariablePathChange = true;

                    // Get the variable name and data type for the variable in the new row
                    String variableName = add.getRowData()[add.getVariableColumn()].toString();
                    String dataType = add.getRowData()[add.getDataTypeColumn()].toString();

                    // Get the variable path
                    String newVariablePath = tableInfo.getTablePath()
                                             + ","
                                             + dataType
                                             + "."
                                             + variableName;

                    // Check if the new variable's data type is a structure table
                    if (!dataTypeHandler.isPrimitive(dataType))
                    {
                        // Copy all data fields with 'all' or 'child only' applicability from the
                        // new child table's prototype to the child table
                        fieldsAddCmd.append("INSERT INTO "
                                            + InternalTable.FIELDS.getTableName()
                                            + " SELECT regexp_replace("
                                            + FieldsColumn.OWNER_NAME.getColumnName()
                                            + ", E'^"
                                            + dataType
                                            + "$', E'"
                                            + newVariablePath
                                            + "'), "
                                            + FieldsColumn.FIELD_NAME.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_DESC.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_SIZE.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_TYPE.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_REQUIRED.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_VALUE.getColumnName()
                                            + ", "
                                            + FieldsColumn.FIELD_INHERITED.getColumnName()
                                            + " FROM "
                                            + InternalTable.FIELDS.getTableName()
                                            + " WHERE "
                                            + FieldsColumn.OWNER_NAME.getColumnName()
                                            + " = '"
                                            + dataType
                                            + "' AND "
                                            + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                            + " != '"
                                            + ApplicabilityType.ROOT_ONLY.getApplicabilityName()
                                            + "'; ");

                        // Check if this structure data type is currently a root table (i.e., it's
                        // changing from a root to a child)
                        if (rootStructures.contains(dataType))
                        {
                            // Check if the new parent is a root structure. This prevents updating
                            // the path such that it creates a reference to a child of a non-root
                            // table, which is invalid
                            if (rootStructures.contains(tableInfo.getRootTable()))
                            {
                                // If the structure chosen as the variable's data type is a root
                                // structure, then any custom values for this the root structure
                                // (which becomes a child structure) are transferred to its new
                                // parent structure. References in the other internal tables are
                                // also changed to the structure's new path as a child
                                valuesAddCmd.append("UPDATE "
                                                    + InternalTable.VALUES.getTableName()
                                                    + " SET "
                                                    + ValuesColumn.TABLE_PATH.getColumnName()
                                                    + " = regexp_replace("
                                                    + ValuesColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); ");
                                groupsAddCmd.append("UPDATE "
                                                    + InternalTable.GROUPS.getTableName()
                                                    + " SET "
                                                    + GroupsColumn.MEMBERS.getColumnName()
                                                    + " = regexp_replace("
                                                    + GroupsColumn.MEMBERS.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + "(,|$)', E'"
                                                    + newVariablePath
                                                    + "\\\\1'); ");
                                fieldsAddCmd.append("UPDATE "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SET "
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + " = regexp_replace("
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); INSERT INTO "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SELECT regexp_replace("
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + "', E'"
                                                    + newVariablePath
                                                    + "'), "
                                                    + FieldsColumn.FIELD_NAME.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_DESC.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_SIZE.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_TYPE.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_REQUIRED.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                                    + " FROM "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " WHERE "
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + " = '"
                                                    + dataType
                                                    + "' AND "
                                                    + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                                    + " != '"
                                                    + ApplicabilityType.ROOT_ONLY.getApplicabilityName()
                                                    + "'; ");
                                ordersAddCmd.append("UPDATE "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " SET "
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + " = regexp_replace("
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); INSERT INTO "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " SELECT "
                                                    + OrdersColumn.USER_NAME.getColumnName()
                                                    + ", regexp_replace("
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + dataType
                                                    + "', E'"
                                                    + newVariablePath
                                                    + "'), "
                                                    + OrdersColumn.COLUMN_ORDER.getColumnName()
                                                    + " FROM "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " WHERE "
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + " = '"
                                                    + dataType
                                                    + "'; ");
                                String orgPathWithChildren = dataType + "(," + PATH_IDENT + ")?";
                                assnsAddCmd.append("UPDATE "
                                                   + InternalTable.ASSOCIATIONS.getTableName()
                                                   + " SET "
                                                   + AssociationsColumn.MEMBERS.getColumnName()
                                                   + " = regexp_replace("
                                                   + AssociationsColumn.MEMBERS.getColumnName()
                                                   + ", E'(?:^"
                                                   + orgPathWithChildren
                                                   + "|("
                                                   + assnsSeparator
                                                   + ")"
                                                   + orgPathWithChildren
                                                   + ")', E'\\\\2"
                                                   + newVariablePath
                                                   + "\\\\1\\\\3', 'g'); ");
                            }

                            // References in the links and telemetry scheduler to the root
                            // structure and its children are not automatically amended to include
                            // the new parent structure path, but are instead removed
                            deleteLinkPathRef("^" + dataType + "(?:,|\\\\.|$)", linksDelCmd);
                            deleteTlmPathRef(dataType + "(?:,|\\\\.|$)", tlmDelCmd);
                        }
                    }

                    // Check if the added variable is a string array member
                    if (dataTypeHandler.isString(dataType)
                        && ArrayVariable.isArrayMember(variableName))
                    {
                        // Get the string array definition
                        String stringArrayDefn = ArrayVariable.removeStringSize(newVariablePath);

                        // Check if this string array hasn't already been processed
                        if (!stringArrays.contains(stringArrayDefn))
                        {
                            // Add the string array definition to the list so that it's only
                            // processed once (in the event multiple members of the array are
                            // added)
                            stringArrays.add(stringArrayDefn);

                            // Update the links definitions, if needed, with the new string array
                            // member
                            updateLinksForStringMemberAddition(tableInfo.getTablePath(),
                                                               dataType,
                                                               variableName);

                            // Remove all references to the string array from the telemetry
                            // scheduler table
                            deleteTlmPathRef(stringArrayDefn + "(?:,|:|$)", tlmDelCmd);
                        }
                    }
                }
            }

            // Remove the ending comma and space, and append the command's closing semi-colon
            addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
            addCmd.append("; "
                          + valuesAddCmd.toString()
                          + groupsAddCmd.toString()
                          + fieldsAddCmd.toString()
                          + ordersAddCmd.toString()
                          + assnsAddCmd.toString()
                          + linksDelCmd.toString()
                          + tlmDelCmd.toString());
        }

        return addCmd.toString();
    }

    /**********************************************************************************************
     * Build the commands to modify the table
     *
     * @param tableInfo
     *            table information
     *
     * @param modifications
     *            list of row modification information
     *
     * @param dbTableName
     *            name of the table's prototype to which to modify rows
     *
     * @param typeDefn
     *            table type definition
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a reference to the
     *            current data type handler) if the change does not originate from the data type
     *            editor
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the internal tables. This is
     *            used during a data type update where only the data type name has changed in order
     *            to speed up the operation
     *
     * @param varRefChkResults
     *            results of the variable reference input type search
     *
     * @param cmdRefChkResults
     *            results of the command reference input type search
     *
     * @return Table row modification command
     *********************************************************************************************/
    private String buildModificationCommand(TableInformation tableInfo,
                                            List<TableModification> modifications,
                                            String dbTableName,
                                            TypeDefinition typeDefn,
                                            CcddDataTypeHandler newDataTypeHandler,
                                            CcddTableTreeHandler tableTree,
                                            boolean skipInternalTables,
                                            ReferenceCheckResults varRefChkResults,
                                            ReferenceCheckResults cmdRefChkResults)
    {
        StringBuilder modCmd = new StringBuilder("");
        List<Object[]> tablePathList = null;

        // Check that there are modifications
        if (!modifications.isEmpty())
        {
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");

            // Check if no updated data type handler is provided. This implies the modifications
            // are not due to an update in the data type editor
            if (newDataTypeHandler == null)
            {
                // Set the updated data type handler to use the current handler
                newDataTypeHandler = dataTypeHandler;
            }

            // Step through each modification
            for (TableModification mod : modifications)
            {
                // Check if this is a prototype table (modifications are made to the table)
                if (tableInfo.isPrototype())
                {
                    StringBuilder valuesModCmd = new StringBuilder("");
                    StringBuilder linksModCmd = new StringBuilder("");
                    StringBuilder tlmModCmd = new StringBuilder("");
                    StringBuilder groupsModCmd = new StringBuilder("");
                    StringBuilder fieldsModCmd = new StringBuilder("");
                    StringBuilder ordersModCmd = new StringBuilder("");
                    StringBuilder assnsModCmd = new StringBuilder("");

                    // Build the update command
                    modCmd.append("UPDATE " + dbTableName + " SET ");

                    // Step through each changed column
                    for (int column = 0; column < mod.getRowData().length; column++)
                    {
                        // Check if the column value changed
                        if (mod.getOriginalRowData()[column] == null
                            || !mod.getOriginalRowData()[column].equals(mod.getRowData()[column]))
                        {
                            // Build the command to change the column value
                            modCmd.append(typeDefn.getColumnNamesDatabaseQuoted()[column]
                                          + " = "
                                          + delimitText(mod.getRowData()[column])
                                          + ", ");
                        }
                    }

                    // Check if the internal tables are to be updated and the table represents a
                    // structure
                    if (!skipInternalTables && typeDefn.isStructure())
                    {
                        // Get the original and current variable names, data types, array sizes,
                        // and bit lengths
                        String oldVariableName = mod.getOriginalRowData()[mod.getVariableColumn()].toString();
                        String newVariableName = mod.getRowData()[mod.getVariableColumn()].toString();
                        String oldDataType = mod.getOriginalRowData()[mod.getDataTypeColumn()].toString();
                        String newDataType = mod.getRowData()[mod.getDataTypeColumn()].toString();
                        String oldArraySize = mod.getOriginalRowData()[mod.getArraySizeColumn()].toString();
                        String newArraySize = mod.getRowData()[mod.getArraySizeColumn()].toString();
                        String oldBitLength = mod.getOriginalRowData()[mod.getBitLengthColumn()].toString();
                        String newBitLength = mod.getRowData()[mod.getBitLengthColumn()].toString();

                        // Initialize the flag to indicate no rate has changed
                        boolean rateChanged = false;

                        // Step through each rate index
                        for (int rateIndex : mod.getRateColumn())
                        {
                            // Get the old and new rate values
                            String oldRate = mod.getOriginalRowData()[rateIndex].toString();
                            String newRate = mod.getRowData()[rateIndex].toString();

                            // Check if the rate changed
                            if (!oldRate.equals(newRate))
                            {
                                // Set the flag to indicate that the rate changed and stop
                                // searching
                                rateChanged = true;
                                break;
                            }
                        }

                        // Set the flags to indicate if the variable name, data type, array size,
                        // and bit length values have changed
                        boolean variableChanged = !oldVariableName.equals(newVariableName);
                        boolean dataTypeChanged = !oldDataType.equals(newDataType);
                        boolean arraySizeChanged = !oldArraySize.equals(newArraySize);
                        boolean bitLengthChanged = !oldBitLength.equals(newBitLength);

                        // Check if the variable name, data type, array size, bit length, or rate
                        // column value(s) changed; this change must be propagated to the instances
                        // of this prototype and their entries in the internal tables
                        if (variableChanged
                            || dataTypeChanged
                            || arraySizeChanged
                            || bitLengthChanged
                            || rateChanged)
                        {
                            // Check if the variable's name, data type, array size, or bit length
                            // has changed
                            if (variableChanged
                                || dataTypeChanged
                                || arraySizeChanged
                                || bitLengthChanged)
                            {
                                // Set the flag to force the variable paths and offsets lists to be
                                // rebuilt
                                isVariablePathChange = true;
                            }

                            // Check if the data type has been changed, the new data type is a
                            // structure, and this structure is a root table
                            if (dataTypeChanged
                                && !newDataTypeHandler.isPrimitive(newDataType)
                                && rootStructures.contains(newDataType))
                            {
                                // Get the variable path
                                String newVariablePath = tableInfo.getTablePath()
                                                         + ","
                                                         + newDataType
                                                         + "."
                                                         + newVariableName;

                                // If the structure chosen as the variable's data type is a root
                                // structure, then any references in the internal tables are
                                // changed to the structure's new path as a child
                                valuesModCmd.append("UPDATE "
                                                    + InternalTable.VALUES.getTableName()
                                                    + " SET "
                                                    + ValuesColumn.TABLE_PATH.getColumnName()
                                                    + " = regexp_replace("
                                                    + ValuesColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); ");
                                groupsModCmd.append("UPDATE "
                                                    + InternalTable.GROUPS.getTableName()
                                                    + " SET "
                                                    + GroupsColumn.MEMBERS.getColumnName()
                                                    + " = regexp_replace("
                                                    + GroupsColumn.MEMBERS.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + "(,|$)', E'"
                                                    + newVariablePath
                                                    + "\\\\1'); ");
                                fieldsModCmd.append("UPDATE "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SET "
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + " = regexp_replace("
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); INSERT INTO "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SELECT regexp_replace("
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + "', E'"
                                                    + newVariablePath
                                                    + "'), "
                                                    + FieldsColumn.FIELD_NAME.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_DESC.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_SIZE.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_TYPE.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_REQUIRED.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                                    + ", "
                                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                                    + " FROM "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " WHERE "
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + " = '"
                                                    + newDataType
                                                    + "' AND "
                                                    + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                                    + " != '"
                                                    + ApplicabilityType.ROOT_ONLY.getApplicabilityName()
                                                    + "'; ");
                                ordersModCmd.append("UPDATE "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " SET "
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + " = regexp_replace("
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + ",', E'"
                                                    + newVariablePath
                                                    + ",'); INSERT INTO "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " SELECT "
                                                    + OrdersColumn.USER_NAME.getColumnName()
                                                    + ", regexp_replace("
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + "', E'"
                                                    + newVariablePath
                                                    + "'), "
                                                    + OrdersColumn.COLUMN_ORDER.getColumnName()
                                                    + " FROM "
                                                    + InternalTable.ORDERS.getTableName()
                                                    + " WHERE "
                                                    + OrdersColumn.TABLE_PATH.getColumnName()
                                                    + " = '"
                                                    + newDataType
                                                    + "'; ");
                                String orgPathWithChildren = newDataType + "(," + PATH_IDENT + ")?";
                                assnsModCmd.append("UPDATE "
                                                   + InternalTable.ASSOCIATIONS.getTableName()
                                                   + " SET "
                                                   + AssociationsColumn.MEMBERS.getColumnName()
                                                   + " = regexp_replace("
                                                   + AssociationsColumn.MEMBERS.getColumnName()
                                                   + ", E'(?:^"
                                                   + orgPathWithChildren
                                                   + "|("
                                                   + assnsSeparator
                                                   + ")"
                                                   + orgPathWithChildren
                                                   + ")', E'\\\\2"
                                                   + newVariablePath
                                                   + "\\\\1\\\\3', 'ng'); ");

                                // References in the links and telemetry scheduler to the root
                                // structure and its children are not automatically amended to
                                // include the new parent structure path, but are instead removed
                                deleteLinkPathRef("^" + newDataType + "(?:,|\\\\.|$)", linksDelCmd);
                                deleteTlmPathRef(newDataType + "(?:,|\\\\.|$)", tlmDelCmd);
                            }

                            // Check if the table path list doesn't exist. The list only needs to
                            // be created once per table since it references the prototype, which
                            // doesn't change even if multiple modifications are made to the table
                            if (tablePathList == null)
                            {
                                // Create a list of table path arrays that are instances of this
                                // prototype table
                                tablePathList = tableTree.getTableTreePathArray(tableInfo.getPrototypeName(),
                                                                                tableTree.getNodeByNodeName(DEFAULT_INSTANCE_NODE_NAME),
                                                                                -1);

                                // Check if the data type changed from a structure to either a
                                // primitive or another structure
                                if (dataTypeChanged && !dataTypeHandler.isPrimitive(oldDataType))
                                {
                                    // Add the paths for references to the prototype in non-root
                                    // prototype tables
                                    tablePathList.addAll(tableTree.getTableTreePathArray(tableInfo.getPrototypeName(),
                                                                                         tableTree.getNodeByNodeName(DEFAULT_PROTOTYPE_NODE_NAME),
                                                                                         -1));
                                }
                            }

                            // Step through each table path found
                            for (Object[] path : tablePathList)
                            {
                                boolean isDelLinksAndTlm = false;

                                // Get the variable name path from the table tree
                                String tablePath = tableTree.getFullVariablePath(path);

                                // Append the original/new data type and/or name of the variable
                                // that's being changed to the variable path. Escape any PostgreSQL
                                // reserved characters so that the original path can be used in a
                                // regular expression
                                String orgVariablePath = tablePath
                                                         + ","
                                                         + oldDataType
                                                         + "."
                                                         + oldVariableName;
                                String orgVarPathEsc = CcddUtilities.escapePostgreSQLReservedChars(orgVariablePath);
                                String newVariablePath = tablePath
                                                         + ","
                                                         + newDataType
                                                         + "."
                                                         + newVariableName;

                                // Check if the variable name changed, or if the data type changed
                                // from one primitive to another primitive. In either case, check
                                // that the array status (is or isn't) remains unchanged
                                if ((variableChanged

                                     || (dataTypeChanged
                                         && dataTypeHandler.isPrimitive(oldDataType)
                                         && newDataTypeHandler.isPrimitive(newDataType)))

                                    && !(arraySizeChanged
                                         && (oldArraySize.isEmpty()
                                             || newArraySize.isEmpty())))
                                {
                                    // Create the commands to update the internal tables for
                                    // instances of non-array member variables of the prototype
                                    // table
                                    valuesModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                 newVariablePath,
                                                                                 InternalTable.VALUES.getTableName(),
                                                                                 ValuesColumn.TABLE_PATH.getColumnName(),
                                                                                 "",
                                                                                 "",
                                                                                 true));
                                    groupsModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                 newVariablePath,
                                                                                 InternalTable.GROUPS.getTableName(),
                                                                                 GroupsColumn.MEMBERS.getColumnName(),
                                                                                 "",
                                                                                 "",
                                                                                 true));
                                    fieldsModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                 newVariablePath,
                                                                                 InternalTable.FIELDS.getTableName(),
                                                                                 FieldsColumn.OWNER_NAME.getColumnName(),
                                                                                 "",
                                                                                 "",
                                                                                 true));
                                    ordersModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                 newVariablePath,
                                                                                 InternalTable.ORDERS.getTableName(),
                                                                                 OrdersColumn.TABLE_PATH.getColumnName(),
                                                                                 "",
                                                                                 "",
                                                                                 true));
                                    String orgPathWithChildren = orgVarPathEsc
                                                                 + "(,"
                                                                 + PATH_IDENT
                                                                 + ")?";
                                    assnsModCmd.append("UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'(?:^"
                                                       + orgPathWithChildren
                                                       + "|("
                                                       + assnsSeparator
                                                       + ")"
                                                       + orgPathWithChildren
                                                       + ")', E'\\\\2"
                                                       + newVariablePath
                                                       + "\\\\1\\\\3', 'ng'); ");

                                    // Check if the data type, bit length, and rate didn't also
                                    // change (updates to these attributes result in deletion of
                                    // any references to the variable and any children in the links
                                    // and telemetry scheduler tables, which is handled elsewhere)
                                    if (!dataTypeChanged && !bitLengthChanged && !rateChanged)
                                    {
                                        // Create the command to update the links table for
                                        // instances of variables of the prototype table
                                        linksModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                    newVariablePath,
                                                                                    InternalTable.LINKS.getTableName(),
                                                                                    LinksColumn.MEMBER.getColumnName(),
                                                                                    "",
                                                                                    "",
                                                                                    true));

                                        // Since the variable still fits within any message in the
                                        // telemetry scheduler table to which it's assigned just
                                        // change all references to the variable
                                        tlmModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                  newVariablePath,
                                                                                  InternalTable.TLM_SCHEDULER.getTableName(),
                                                                                  TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                                  "(.*" + tlmSchSeparator + ")",
                                                                                  "\\\\1",
                                                                                  true));
                                    }
                                    // The data type, bit length, or rate changed
                                    else
                                    {
                                        // Set the flag to indicate that references to the variable
                                        // and any children should be removed from the links and
                                        // telemetry scheduler tables
                                        isDelLinksAndTlm = true;
                                    }
                                }

                                // Check if the bit length changed, but not the data type or rate
                                if (bitLengthChanged && !dataTypeChanged && !rateChanged)
                                {
                                    // Append the bit lengths to the variable paths
                                    String orgVarPathEscBit = orgVarPathEsc
                                                              + (oldBitLength.isEmpty()
                                                                                        ? ""
                                                                                        : ":" + oldBitLength);
                                    String newVariablePathBit = newVariablePath
                                                                + (newBitLength.isEmpty()
                                                                                          ? ""
                                                                                          : ":" + newBitLength);

                                    // Create the command to update the links and telemetry
                                    // scheduler tables for instances of variables of the prototype
                                    // table. If bit-packing changed due to the bit length update
                                    // then the affected variables are subsequently removed from
                                    // the links and telemetry scheduler tables
                                    linksModCmd.append(updateVarNameAndDataType(orgVarPathEscBit,
                                                                                newVariablePathBit,
                                                                                InternalTable.LINKS.getTableName(),
                                                                                LinksColumn.MEMBER.getColumnName(),
                                                                                "",
                                                                                "",
                                                                                true));
                                    tlmModCmd.append(updateVarNameAndDataType(orgVarPathEscBit,
                                                                              newVariablePathBit,
                                                                              InternalTable.TLM_SCHEDULER.getTableName(),
                                                                              TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                              "(.*" + tlmSchSeparator + ")",
                                                                              "\\\\1",
                                                                              true));
                                }

                                // Check if the data type changed from a structure to either a
                                // primitive or another structure
                                if (dataTypeChanged && !dataTypeHandler.isPrimitive(oldDataType))
                                {
                                    // Create the command to delete references to any children of
                                    // the original structure path and change the data type for
                                    // references to the structure itself
                                    valuesModCmd.append("DELETE FROM "
                                                        + InternalTable.VALUES.getTableName()
                                                        + " WHERE "
                                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                                        + " ~ E'^"
                                                        + orgVarPathEsc
                                                        + ",'; "
                                                        + updateVarNameAndDataType(orgVarPathEsc,
                                                                                   newVariablePath,
                                                                                   InternalTable.VALUES.getTableName(),
                                                                                   ValuesColumn.TABLE_PATH.getColumnName(),
                                                                                   "",
                                                                                   "",
                                                                                   false));

                                    // Build a regular expression for locating references to the
                                    // original variable path
                                    String pathMatch = orgVarPathEsc + "(?:,|$)";

                                    // Remove all references to the structure and its children
                                    groupsModCmd.append("DELETE FROM "
                                                        + InternalTable.GROUPS.getTableName()
                                                        + " WHERE "
                                                        + GroupsColumn.MEMBERS.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");
                                    fieldsModCmd.append("DELETE FROM "
                                                        + InternalTable.FIELDS.getTableName()
                                                        + " WHERE "
                                                        + FieldsColumn.OWNER_NAME.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");
                                    ordersModCmd.append("DELETE FROM "
                                                        + InternalTable.ORDERS.getTableName()
                                                        + " WHERE "
                                                        + OrdersColumn.TABLE_PATH.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");
                                    String orgPathWithChildren = orgVarPathEsc
                                                                 + "(?:,"
                                                                 + PATH_IDENT
                                                                 + ")?";
                                    assnsModCmd.append("UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'^"
                                                       + orgPathWithChildren
                                                       + "', E'', 'ng'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'"
                                                       + assnsSeparator
                                                       + orgPathWithChildren
                                                       + "', E'', 'ng'); ");

                                    // Check if the rate didn't change as well (if the rate changed
                                    // then updates to the links and telemetry scheduler tables are
                                    // handled elsewhere)
                                    if (!rateChanged)
                                    {
                                        // Set the flag to indicate that references to the variable
                                        // and any children should be removed from the links and
                                        // telemetry scheduler tables
                                        isDelLinksAndTlm = true;
                                    }
                                }

                                // Check if the variable changed to or from being an array (changes
                                // only to the array dimension value(s) are handled by the table
                                // row addition and deletion methods)
                                if (arraySizeChanged
                                    && (oldArraySize.isEmpty() || newArraySize.isEmpty()))
                                {
                                    // Remove all references to the structure's children, but not
                                    // the structure itself
                                    valuesModCmd.append("DELETE FROM "
                                                        + InternalTable.VALUES.getTableName()
                                                        + " WHERE "
                                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                                        + " ~ E'^"
                                                        + orgVarPathEsc
                                                        + "(?:,|\\\\[)"
                                                        + "'; ");

                                    // Build a regular expression for locating references to the
                                    // original variable path and any children
                                    String pathMatch = orgVarPathEsc + "(?:,|\\\\[|$)";

                                    // Remove all references to the structure and its children in
                                    // the internal tables (other than the custom values table)
                                    groupsModCmd.append("DELETE FROM "
                                                        + InternalTable.GROUPS.getTableName()
                                                        + " WHERE "
                                                        + GroupsColumn.MEMBERS.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");
                                    fieldsModCmd.append("DELETE FROM "
                                                        + InternalTable.FIELDS.getTableName()
                                                        + " WHERE "
                                                        + FieldsColumn.OWNER_NAME.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");
                                    ordersModCmd.append("DELETE FROM "
                                                        + InternalTable.ORDERS.getTableName()
                                                        + " WHERE "
                                                        + OrdersColumn.TABLE_PATH.getColumnName()
                                                        + " ~ E'^"
                                                        + pathMatch
                                                        + "'; ");

                                    // Check if the variable changed from not an array to an array
                                    if (!newArraySize.isEmpty())
                                    {
                                        // Remove all non-array references to the structure and its
                                        // children in the script associations. If the variable
                                        // changed from an array to not an array then updates to
                                        // the associations are handled by the row deletion method
                                        assnsModCmd.append("UPDATE "
                                                           + InternalTable.ASSOCIATIONS.getTableName()
                                                           + " SET "
                                                           + AssociationsColumn.MEMBERS.getColumnName()
                                                           + " = regexp_replace("
                                                           + AssociationsColumn.MEMBERS.getColumnName()
                                                           + ", E'^"
                                                           + orgVarPathEsc
                                                           + "(?:"
                                                           + assnsSeparator
                                                           + "|,"
                                                           + PATH_IDENT
                                                           + "|$)', E'', 'ng'); ");
                                    }

                                    // Check if the rate didn't change as well (if the rate changed
                                    // then updates to the links and telemetry scheduler tables are
                                    // handled elsewhere)
                                    if (!rateChanged)
                                    {
                                        // Set the flag to indicate that references to the variable
                                        // and any children should be removed from the links and
                                        // telemetry scheduler tables
                                        isDelLinksAndTlm = true;
                                    }
                                }

                                // Check if the rate changed or another change necessitates removal
                                // of the variable and any children from the links and telemetry
                                // scheduler tables
                                if (rateChanged || isDelLinksAndTlm)
                                {
                                    // Create the commands to delete the variable from the link and
                                    // telemetry scheduler tables
                                    deleteLinkAndTlmPathRef(oldArraySize,
                                                            oldVariableName,
                                                            orgVariablePath,
                                                            orgVarPathEsc,
                                                            linksDelCmd,
                                                            tlmDelCmd);
                                }
                            }

                            // Check if the variable's name or array size has changed (this is for
                            // adjusting cells and fields having a variable reference input type;
                            // data type doesn't affect the variable reference)
                            if (variableChanged || arraySizeChanged)
                            {
                                // Update any table cells or data fields with the variable
                                // reference input type containing the renamed variable
                                modifyVariableReference(tableInfo,
                                                        oldVariableName,
                                                        newVariableName,
                                                        oldDataType,
                                                        newDataType,
                                                        varRefChkResults,
                                                        valuesModCmd,
                                                        fieldsModCmd);
                            }
                        }
                    }
                    // Check if this is a command table
                    else if (typeDefn.isCommand())
                    {
                        // Get the column indices for the command name and code
                        int cmdNameCol = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
                        int cmdCodeCol = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);

                        // Get the original and modified command name and code
                        String oldCommandName = mod.getOriginalRowData()[cmdNameCol].toString();
                        String newCommandName = mod.getRowData()[cmdNameCol].toString();
                        String oldCommandCode = mod.getOriginalRowData()[cmdCodeCol].toString();
                        String newCommandCode = mod.getRowData()[cmdCodeCol].toString();

                        int oldNumArgs = 0;
                        int newNumArgs = 0;

                        // Set the flag in the command name or code has changed
                        boolean isCmdChange = !oldCommandName.equals(newCommandName)
                                              || !oldCommandCode.equals(newCommandCode);

                        // Step through each command argument
                        for (Integer cmdArgCol : typeDefn.getColumnIndicesByInputType(DefaultInputType.ARGUMENT_NAME))
                        {
                            // Check if the original argument name isn't blank
                            if (!mod.getOriginalRowData()[cmdArgCol].toString().isEmpty())
                            {
                                // Increment the original number of arguments counter
                                oldNumArgs++;
                            }

                            // Check if the modified argument name isn't blank
                            if (!mod.getRowData()[cmdArgCol].toString().isEmpty())
                            {
                                // Increment the modified number of arguments counter
                                newNumArgs++;
                            }

                            // Check if the argument name has changed
                            if (!mod.getOriginalRowData()[cmdArgCol].toString().equals(mod.getRowData()[cmdArgCol].toString()))
                            {
                                // Set the flag to indicate an argument name changed
                                isCmdChange = true;
                            }
                        }

                        // Check if a command name, code, or argument name changed
                        if (isCmdChange)
                        {
                            // Set the flag to force the command list to be rebuilt
                            isCommandChange = true;

                            // Update any table cells or data fields with the command reference
                            // input type containing the command with a name, code, or number of
                            // arguments change
                            modifyCommandReference(tableInfo,
                                                   oldCommandName,
                                                   newCommandName,
                                                   oldCommandCode,
                                                   newCommandCode,
                                                   oldNumArgs,
                                                   newNumArgs,
                                                   cmdRefChkResults,
                                                   valuesModCmd,
                                                   fieldsModCmd);
                        }
                    }

                    // Check is a command was created to change the associations table
                    if (assnsModCmd.length() != 0)
                    {
                        // Clean up any associations that no longer have a table referenced
                        assnsModCmd.append("UPDATE "
                                           + InternalTable.ASSOCIATIONS.getTableName()
                                           + " SET "
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + " = regexp_replace("
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + ", E'^"
                                           + assnsSeparator
                                           + "', E'', 'ng'); UPDATE "
                                           + InternalTable.ASSOCIATIONS.getTableName()
                                           + " SET "
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + " = regexp_replace("
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + ", E'"
                                           + assnsSeparator
                                           + "$', E'', 'ng'); ");
                    }

                    // Remove the trailing comma and space, then add the condition based on the
                    // row's primary key, and add the commands to update the internal tables
                    modCmd = CcddUtilities.removeTrailer(modCmd, ", ");
                    modCmd.append(" WHERE "
                                  + typeDefn.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()]
                                  + " = "
                                  + mod.getRowData()[DefaultColumn.PRIMARY_KEY.ordinal()]
                                  + "; "
                                  + valuesModCmd.toString()
                                  + groupsModCmd.toString()
                                  + fieldsModCmd.toString()
                                  + ordersModCmd.toString()
                                  + assnsModCmd.toString()
                                  + linksModCmd.toString()
                                  + tlmModCmd.toString());
                }
                // Not a prototype table, so modifications are made to the custom values table if
                // internal tables are to be updated and this table is a structure
                else if (!skipInternalTables && typeDefn.isStructure())
                {
                    // Get the variable path, including its name and data type
                    String variablePath = tableInfo.getTablePath()
                                          + ","
                                          + mod.getRowData()[mod.getDataTypeColumn()].toString()
                                          + "."
                                          + mod.getRowData()[mod.getVariableColumn()].toString();

                    // Step through each changed column
                    for (int column = 0; column < mod.getRowData().length; column++)
                    {
                        // Check if the column value changed
                        if (!mod.getOriginalRowData()[column].equals(mod.getRowData()[column]))
                        {
                            // Build the command to delete the old value in the custom values table
                            // (in case it already exists), then insert the (new) value into the
                            // custom values table
                            modCmd.append("DELETE FROM "
                                          + InternalTable.VALUES.getTableName()
                                          + " WHERE "
                                          + ValuesColumn.TABLE_PATH.getColumnName()
                                          + " = '"
                                          + variablePath
                                          + "' AND "
                                          + ValuesColumn.COLUMN_NAME.getColumnName()
                                          + " = '"
                                          + typeDefn.getColumnNamesUser()[column]
                                          + "';");

                            // Check if the new value does not begin with the flag that indicates
                            // the existing custom value should be removed
                            if (!mod.getRowData()[column].toString().startsWith(REPLACE_INDICATOR))
                            {
                                modCmd.append(" INSERT INTO "
                                              + InternalTable.VALUES.getTableName()
                                              + " ("
                                              + ValuesColumn.TABLE_PATH.getColumnName()
                                              + ", "
                                              + ValuesColumn.COLUMN_NAME.getColumnName()
                                              + ", "
                                              + ValuesColumn.VALUE.getColumnName()
                                              + ") VALUES ('"
                                              + variablePath
                                              + "', '"
                                              + typeDefn.getColumnNamesUser()[column]
                                              + "', "
                                              + delimitText(mod.getRowData()[column])
                                              + "); ");
                            }
                        }
                    }

                    // Escape any PostgreSQL reserved characters so that the path can be used in a
                    // regular expression
                    String variablePathEsc = CcddUtilities.escapePostgreSQLReservedChars(variablePath);

                    // Step through each rate index
                    for (int rateIndex : mod.getRateColumn())
                    {
                        // Get the old and new rate values
                        String oldRate = mod.getOriginalRowData()[rateIndex].toString();
                        String newRate = mod.getRowData()[rateIndex].toString();

                        // Check if the rate changed
                        if (!oldRate.equals(newRate))
                        {
                            // Create the commands to delete the variable from the link and
                            // telemetry scheduler tables
                            deleteLinkAndTlmPathRef(mod.getRowData()[mod.getArraySizeColumn()].toString(),
                                                    mod.getRowData()[mod.getVariableColumn()].toString(),
                                                    variablePath,
                                                    variablePathEsc,
                                                    linksDelCmd,
                                                    tlmDelCmd);
                            break;
                        }
                    }
                }
            }

            // Check if a deletion to the links table exists
            if (linksDelCmd.length() != 0)
            {
                // Terminate the links table command and add it to the modification command
                modCmd.append(linksDelCmd.toString());
            }

            // Check if a deletion to the telemetry scheduler table exists
            if (tlmDelCmd.length() != 0)
            {
                // Terminate the telemetry scheduler table command and add it to the modification
                // command
                modCmd.append(tlmDelCmd.toString());
            }
        }

        return modCmd.toString();
    }

    /**********************************************************************************************
     * Build the command to delete a table row. Only prototype tables can have a row deleted
     *
     * @param tableInfo
     *            table information
     *
     * @param deletions
     *            list of row deletion information
     *
     * @param dbTableName
     *            name of the table to which to delete rows
     *
     * @param typeDefn
     *            table type definition
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the internal tables. This is
     *            used during a data type update where only the data type name has changed in order
     *            to speed up the operation
     *
     * @param varRefChkResults
     *            results of the variable reference input type search
     *
     * @param cmdRefChkResults
     *            results of the command reference input type search
     *
     * @return Table row deletion command
     *********************************************************************************************/
    private String buildDeletionCommand(TableInformation tableInfo,
                                        List<TableModification> deletions,
                                        String dbTableName,
                                        TypeDefinition typeDefn,
                                        boolean skipInternalTables,
                                        ReferenceCheckResults varRefChkResults,
                                        ReferenceCheckResults cmdRefChkResults)
    {
        StringBuilder delCmd = new StringBuilder("");

        // Check if there are any table deletions
        if (!deletions.isEmpty())
        {
            StringBuilder valuesDelCmd = new StringBuilder("");
            StringBuilder groupsDelCmd = new StringBuilder("");
            StringBuilder fieldsDelCmd = new StringBuilder("");
            StringBuilder ordersDelCmd = new StringBuilder("");
            StringBuilder assnsDelCmd = new StringBuilder("");
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");

            // Step through each deletion
            for (TableModification del : deletions)
            {
                // Add the table row deletion command
                delCmd.append("DELETE FROM "
                              + dbTableName
                              + " WHERE "
                              + typeDefn.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()]
                              + " = "
                              + del.getRowData()[DefaultColumn.PRIMARY_KEY.ordinal()]
                              + "; ");

                // Check if the internal tables are to be updated and the table represents a
                // structure
                if (!skipInternalTables && typeDefn.isStructure())
                {
                    // Since a variable has been deleted set the flag to force the variable paths
                    // and offsets lists to be rebuilt
                    isVariablePathChange = true;

                    // Get the variable name, data type, and bit length
                    String variableName = del.getRowData()[del.getVariableColumn()].toString();
                    String dataType = del.getRowData()[del.getDataTypeColumn()].toString();

                    // Build the variable path
                    String variablePath = tableInfo.getPrototypeName()
                                          + ","
                                          + dataType
                                          + "."
                                          + variableName;

                    // Build the regular expression that matches the variable path in the table's
                    // prototype
                    String protoVarPathEsc = CcddUtilities.escapePostgreSQLReservedChars(variablePath);

                    // Build the regular expression that matches the variable path in any instance
                    // of the table's prototype
                    String instanceVarPathEsc = ".+,"
                                                + tableInfo.getPrototypeName()
                                                + "\\\\.[^,]*,"
                                                + dataType
                                                + "\\\\."
                                                + CcddUtilities.escapePostgreSQLReservedChars(variableName);

                    // Append the command to delete any instance of the variable from the custom
                    // values table
                    valuesDelCmd.append("DELETE FROM "
                                        + InternalTable.VALUES.getTableName()
                                        + " WHERE "
                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                        + " ~ E'^"
                                        + protoVarPathEsc
                                        + "(?:,|:|$)' OR "
                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                        + " ~ E'^"
                                        + instanceVarPathEsc
                                        + "(?:,|:|$)'; ");

                    // Append the command to delete the variable from the link and telemetry
                    // scheduler tables for references to the variable in the prototype table
                    deleteLinkAndTlmPathRef(del.getRowData()[del.getArraySizeColumn()].toString(),
                                            variableName,
                                            variablePath,
                                            protoVarPathEsc,
                                            linksDelCmd,
                                            tlmDelCmd);

                    // Append the command to delete the variable from the link and telemetry
                    // scheduler tables for all references to the variable in instances of the
                    // prototype table
                    deleteLinkAndTlmPathRef(del.getRowData()[del.getArraySizeColumn()].toString(),
                                            variableName,
                                            variablePath,
                                            instanceVarPathEsc,
                                            linksDelCmd,
                                            tlmDelCmd);

                    // Check if the data type represents a structure
                    if (!dataTypeHandler.isPrimitive(dataType))
                    {
                        // Append the commands to update the internal tables for references to the
                        // variable in the prototype table and all references to the variable in
                        // instances of the prototype table
                        groupsDelCmd.append("DELETE FROM "
                                            + InternalTable.GROUPS.getTableName()
                                            + " WHERE "
                                            + GroupsColumn.MEMBERS.getColumnName()
                                            + " ~ E'^"
                                            + protoVarPathEsc
                                            + "(?:,|$)' OR "
                                            + GroupsColumn.MEMBERS.getColumnName()
                                            + " ~ E'^"
                                            + instanceVarPathEsc
                                            + "(?:,|$)'; ");
                        fieldsDelCmd.append("DELETE FROM "
                                            + InternalTable.FIELDS.getTableName()
                                            + " WHERE "
                                            + FieldsColumn.OWNER_NAME.getColumnName()
                                            + " ~ E'^"
                                            + protoVarPathEsc
                                            + "(?:,|$)' OR "
                                            + FieldsColumn.OWNER_NAME.getColumnName()
                                            + " ~ E'^"
                                            + instanceVarPathEsc
                                            + "(?:,|$)'; ");
                        ordersDelCmd.append("DELETE FROM "
                                            + InternalTable.ORDERS.getTableName()
                                            + " WHERE "
                                            + OrdersColumn.TABLE_PATH.getColumnName()
                                            + " ~ E'^"
                                            + protoVarPathEsc
                                            + "(?:,|$)' OR "
                                            + OrdersColumn.TABLE_PATH.getColumnName()
                                            + " ~ E'^"
                                            + instanceVarPathEsc
                                            + "(?:,|$)'; ");
                        String protoPathWithChildren = protoVarPathEsc + "(?:," + PATH_IDENT + ")?";
                        String instancePathWithChildren = instanceVarPathEsc + "(?:," + PATH_IDENT + ")?";
                        assnsDelCmd.append("UPDATE "
                                           + InternalTable.ASSOCIATIONS.getTableName()
                                           + " SET "
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + " = regexp_replace("
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + ", E'^"
                                           + protoPathWithChildren
                                           + "', E'', 'ng'); UPDATE "
                                           + InternalTable.ASSOCIATIONS.getTableName()
                                           + " SET "
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + " = regexp_replace("
                                           + AssociationsColumn.MEMBERS.getColumnName()
                                           + ", E'^"
                                           + instancePathWithChildren
                                           + "', E'', 'ng'); ");
                    }

                    // Blank any table cells or data fields with the variable reference input type
                    // containing the deleted variable
                    deleteVariableReference(tableInfo,
                                            variableName,
                                            varRefChkResults,
                                            valuesDelCmd,
                                            fieldsDelCmd);
                }
                // Check if this is a command table
                else if (typeDefn.isCommand())
                {
                    // Since a command has been deleted set the flag to force the command list to
                    // be rebuilt
                    isCommandChange = true;

                    // Blank any table cells or data fields with the command reference input type
                    // containing the deleted command
                    deleteCommandReference(tableInfo,
                                           del.getRowData()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME)].toString(),
                                           cmdRefChkResults,
                                           valuesDelCmd,
                                           fieldsDelCmd);
                }
            }

            // Check is a command was created to change the associations table
            if (assnsDelCmd.length() != 0)
            {
                // Clean up any associations that no longer have a table referenced
                assnsDelCmd.append("UPDATE "
                                   + InternalTable.ASSOCIATIONS.getTableName()
                                   + " SET "
                                   + AssociationsColumn.MEMBERS.getColumnName()
                                   + " = regexp_replace("
                                   + AssociationsColumn.MEMBERS.getColumnName()
                                   + ", E'^"
                                   + assnsSeparator
                                   + "', E'', 'ng'); UPDATE "
                                   + InternalTable.ASSOCIATIONS.getTableName()
                                   + " SET "
                                   + AssociationsColumn.MEMBERS.getColumnName()
                                   + " = regexp_replace("
                                   + AssociationsColumn.MEMBERS.getColumnName()
                                   + ", E'"
                                   + assnsSeparator
                                   + "$', E'', 'ng'); ");
            }

            // Append the command's closing semi-colon and add the commands to update the internal
            // tables to the delete command
            delCmd.append(valuesDelCmd.toString()
                          + groupsDelCmd.toString()
                          + fieldsDelCmd.toString()
                          + ordersDelCmd.toString()
                          + assnsDelCmd.toString()
                          + linksDelCmd.toString()
                          + tlmDelCmd.toString());
        }

        return delCmd.toString();
    }

    /**********************************************************************************************
     * Build the command to update the variable name and/or the data type. Combine updates to array
     * members into a single command by using a regular expression to match the array indices
     *
     * @param orgVariablePath
     *            original variable path
     *
     * @param newVariablePath
     *            new variable path
     *
     * @param tableName
     *            name of the internal table to update
     *
     * @param columnName
     *            name of the column in the internal table that contains the variable path
     *
     * @param captureIn
     *            pattern match to capture any character(s) that precede(s) the variable path;
     *            blank if none
     *
     * @param captureOut
     *            pattern for replacing the captured character(s) specified in captureIn; blank if
     *            none
     *
     * @param includeChildren
     *            true to include child tables of the variable paths
     *
     * @return Command to update the variable name and/or the data type
     *********************************************************************************************/
    private String updateVarNameAndDataType(String orgVariablePath,
                                            String newVariablePath,
                                            String tableName,
                                            String columnName,
                                            String captureIn,
                                            String captureOut,
                                            boolean includeChildren)
    {
        String command = "";

        // Initialize the regular expression capture group index
        int captureGrp = captureIn.isEmpty() ? 1 : 2;

        // Check if the path contains an array member
        if (orgVariablePath.contains("["))
        {
            // Check if any of the array members in the path are not the first array index ([0])
            // for that array. Only a path where all array index references are for the first index
            // need to be processed since the command is tailored to account for all index values
            if (!orgVariablePath.matches(".+\\[[1-9].*"))
            {
                // Step through the original variable path for each first array index reference
                while (orgVariablePath.contains("[0"))
                {
                    // Change the array index reference in the original path to generically search
                    // for and capture the array index value, and change the new path to insert the
                    // capture group value in the appropriate array index location. This allows a
                    // single command to cover a variable name or data type update for an entire
                    // array
                    orgVariablePath = orgVariablePath.replaceFirst("\\[0", "[(\\\\\\\\d+)");
                    newVariablePath = newVariablePath.replaceFirst("\\[0",
                                                                   "[\\\\\\\\" + captureGrp);
                    captureGrp++;
                }

                // Create the command to update the internal table for instances of array variables
                // of the prototype table
                command = "UPDATE "
                          + tableName
                          + " SET "
                          + columnName
                          + " = regexp_replace("
                          + columnName
                          + ", E'^"
                          + captureIn
                          + orgVariablePath
                          + (includeChildren
                                             ? "(,.*|$)"
                                             : "$")
                          + "', E'"
                          + captureOut
                          + newVariablePath
                          + (includeChildren
                                             ? "\\\\"
                                               + captureGrp
                                             : "")
                          + "'); ";
            }
        }
        // The path doesn't contain an array member
        else
        {
            // Create the command to update the custom values table for instances of non-array
            // member variables of the prototype table
            command = "UPDATE "
                      + tableName
                      + " SET "
                      + columnName
                      + " = regexp_replace("
                      + columnName
                      + ", E'^"
                      + captureIn
                      + orgVariablePath
                      + (includeChildren
                                         ? "(,.*|:\\\\d+|$)"
                                         : "$")
                      + "', E'"
                      + captureOut
                      + newVariablePath
                      + (includeChildren
                                         ? "\\\\"
                                           + captureGrp
                                         : "")
                      + "'); ";
        }

        return command;
    }

    /**********************************************************************************************
     * Create a copy of the specified table's table tree node and immediate child nodes
     *
     * @param tableName
     *            table name
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @return Copy of the specified table's table tree node and immediate child nodes (if any)
     *********************************************************************************************/
    private ToolTipTreeNode copyPrototypeTableTreeNode(String tableName,
                                                       CcddTableTreeHandler tableTree)
    {
        // Create a new node for the copy
        ToolTipTreeNode orgTableNode = new ToolTipTreeNode(tableName, "");

        // Get prototype table's tree node. If the table has no variables then it doesn't appear in
        // the table tree and this call returns null
        ToolTipTreeNode tableNode = tableTree.getNodeByNodeName(tableName);

        // Check if the table node exists
        if (tableNode != null)
        {
            // Step through each child node of the prototype table node; these are the prototype's
            // variables (data type and name)
            for (int childIndex = 0; childIndex < tableNode.getChildCount(); childIndex++)
            {
                // Copy the node (variable)
                orgTableNode.add(new ToolTipTreeNode(((ToolTipTreeNode) tableNode.getChildAt(childIndex)).getUserObject().toString(), ""));
            }
        }

        return orgTableNode;
    }

    /**********************************************************************************************
     * Build the commands to delete variable references in the links and telemetry scheduler tables
     * that are no longer valid due to changes in bit-packing
     *
     * @param orgTableNode
     *            reference to the node prior to the update
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Commands to delete variable references in the links and telemetry scheduler tables
     *         that are no longer valid due to changes in bit-packing
     *********************************************************************************************/
    private String updateLinksAndTlmForPackingChange(ToolTipTreeNode orgTableNode,
                                                     Component parent)
    {
        StringBuilder linksDelCmd = new StringBuilder("");
        StringBuilder tlmDelCmd = new StringBuilder("");
        List<String> removeMembers = new ArrayList<String>();
        List<List<String>> updatedPacking = new ArrayList<List<String>>();

        // Create the table tree after any changes have been applied
        CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain,
                                                                  TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                                  parent);

        // Get the tree node for the updated prototype table
        ToolTipTreeNode updTableNode = tableTree.getNodeByNodeName(orgTableNode.getUserObject().toString());

        // Check if the table node exists in the updated tree. If all variables are removed from a
        // table then in no longer appears in the tree
        if (updTableNode != null)
        {
            // ////////////////////////////////////////////////////////////////////////////////////
            // Create a list containing lists of each group of bit-packed variables in the table as
            // it exists after the updates are applied. Also create a list containing every
            // bit-packed variable; this list is pruned later to remove those variables that
            // haven't changed their bit-packing
            // ////////////////////////////////////////////////////////////////////////////////////
            // Step through each variable in the table as it exists after the updates
            for (int childIndex = 0; childIndex < updTableNode.getChildCount(); childIndex++)
            {
                // Get the indices of all variables bit-packed the variable at the current child
                // index
                BitPackNodeIndex nodeIndex = tableTree.getBitPackedVariables((ToolTipTreeNode) updTableNode.getChildAt(childIndex));

                // Check if any variables are bit-packed with the current variable
                if (nodeIndex.getFirstIndex() != nodeIndex.getLastIndex())
                {
                    List<String> packMembers = new ArrayList<String>();

                    // Step through the bit-packed variables
                    for (int packIndex = nodeIndex.getFirstIndex(); packIndex <= nodeIndex.getLastIndex(); packIndex++)
                    {
                        // Add the variable to the pack member list
                        packMembers.add(((ToolTipTreeNode) updTableNode.getChildAt(packIndex)).getUserObject().toString().replaceFirst(":\\d+", ""));
                    }

                    // Store the pack member list and the individual members
                    updatedPacking.add(packMembers);
                    removeMembers.addAll(packMembers);

                    // Adjust the child index to skip these bit-packed variables
                    childIndex = nodeIndex.getLastIndex();
                }
            }
        }

        // ////////////////////////////////////////////////////////////////////////////////////////
        // Create lists of each group of bit-packed variables in the table as it existed prior to
        // the updates being applied. Also add the variables to the list containing every
        // bit-packed variable (if not already in the list). Check if the group still exists in the
        // updated table (i.e. contains the same bit-packed members; order or position is ignored).
        // If the group does exist then remove its members from the list of variables to be removed
        // ////////////////////////////////////////////////////////////////////////////////////////
        // Step through each variable in the table as is existed prior to the updates
        for (int childIndex = 0; childIndex < orgTableNode.getChildCount(); childIndex++)
        {
            // Get the indices of all variables bit-packed the variable at the current child index
            BitPackNodeIndex nodeIndex = tableTree.getBitPackedVariables((ToolTipTreeNode) orgTableNode.getChildAt(childIndex));

            // Check if any variables are bit-packed with the current variable
            if (nodeIndex.getFirstIndex() != nodeIndex.getLastIndex())
            {
                List<String> packMembers = new ArrayList<String>();

                // Step through the bit-packed variables
                for (int packIndex = nodeIndex.getFirstIndex(); packIndex <= nodeIndex.getLastIndex(); packIndex++)
                {
                    // Get the variable name minus the bit length
                    String variable = ((ToolTipTreeNode) orgTableNode.getChildAt(packIndex)).getUserObject().toString().replaceFirst(":\\d+", "");
                    packMembers.add(variable);

                    // Check if the individual member list doesn't already contain this variable
                    if (!removeMembers.contains(variable))
                    {
                        // Add the variable to the individual members list
                        removeMembers.add(variable);
                    }
                }

                // ////////////////////////////////////////////////////////////////////////////////
                // Compare the bit-packed group of variables in the table originally to the groups
                // in the table with the updates. If the group still exists then remove its members
                // from the list of variables to remove. Once all of the bit-packed groups are
                // checks the removal list contains only the variables that have changed their
                // bit-packed status, and therefore need to be removed from the links and telemetry
                // scheduler tables
                // ////////////////////////////////////////////////////////////////////////////////
                // Step through the list of bit-packed variables in the updated table
                for (List<String> updatedPackMembers : updatedPacking)
                {
                    // Check if the bit-packed variables in the original table are still present in
                    // the updated table
                    if (CcddUtilities.isArraySetsEqual(packMembers.toArray(new String[0]),
                                                       updatedPackMembers.toArray(new String[0])))
                    {
                        // Since the bit-packed variables are unchanged, delete them from the list
                        // of variables that are to be removed from the links and telemetry
                        // scheduler tables, and stop searching
                        removeMembers.removeAll(packMembers);
                        break;
                    }
                }

                // Adjust the child index to skip these bit-packed variables
                childIndex = nodeIndex.getLastIndex();
            }
        }

        // Step through the list of variables that have changed their bit-pack status (no longer
        // bit-packed, or packed with different variables)
        for (String variable : removeMembers)
        {
            // Get the path of the variable
            String packPath = orgTableNode.getUserObject().toString()
                              + "(?:\\\\.[^,]*)?,"
                              + CcddUtilities.escapePostgreSQLReservedChars(variable)
                              + "(?::\\\\d+|$)";

            // Delete the variable from the links and telemetry scheduler tables
            deleteLinkPathRef("(?:^|[^,]*,)" + packPath, linksDelCmd);
            deleteTlmPathRef("(?:[^,]+,)*" + packPath, tlmDelCmd);
        }

        return linksDelCmd.toString() + tlmDelCmd.toString();
    }

    /**********************************************************************************************
     * Update the link definitions when a string variable's size is increased (i.e., a new member
     * is added to the array)
     *
     * @param protoTable
     *            prototype table name to which the string variable belongs
     *
     * @param dataType
     *            string variable's data type name
     *
     * @param variableName
     *            string variable's name
     *********************************************************************************************/
    private void updateLinksForStringMemberAddition(String protoTable,
                                                    String dataType,
                                                    String variableName)
    {
        // Check if no link handler is already created
        if (addLinkHandler == null)
        {
            // Create a link handler
            addLinkHandler = new CcddLinkHandler(ccddMain, ccddMain.getMainFrame());
        }

        // Get the reference to the link definitions in order to shorten subsequent calls
        List<String[]> linkDefns = addLinkHandler.getLinkDefinitions();

        // Get the variable name without the string dimension (leaving any other array dimensions)
        String stringVarNameDefn = ArrayVariable.removeStringSize(variableName);

        // Extract the string dimension value from the variable name, then decrement it by one -
        // this is used to find the existing string array member immediately preceding the new
        // member
        int[] stringArrayDim = ArrayVariable.getArrayIndexFromSize(ArrayVariable.getVariableArrayIndex(variableName));
        int stringIndex = stringArrayDim[stringArrayDim.length - 1] - 1;

        // Step through each link definition
        for (int index = linkDefns.size() - 1; index >= 0; index--)
        {
            // Get the link member in order to shorten subsequent calls
            String linkMember = linkDefns.get(index)[LinksColumn.MEMBER.ordinal()];

            // Check if the link member contains a reference to the string array
            if (linkMember.matches(protoTable
                                   + "(?:,|\\.[^,]+,)"
                                   + dataType
                                   + "\\."
                                   + stringVarNameDefn
                                   + "\\["
                                   + stringIndex
                                   + "\\]"))
            {
                // Insert the new string array member into the link definitions list immediately
                // after the preceding member
                addLinkHandler.getLinkDefinitions().add(index + 1,
                                                        new String[] {linkDefns.get(index)[LinksColumn.RATE_NAME.ordinal()],
                                                                      linkDefns.get(index)[LinksColumn.LINK_NAME.ordinal()],
                                                                      linkMember.replaceFirst("("
                                                                                              + protoTable
                                                                                              + "(?:,|\\.[^,]+,)"
                                                                                              + dataType
                                                                                              + "\\."
                                                                                              + stringVarNameDefn
                                                                                              + "\\[)"
                                                                                              + stringIndex
                                                                                              + "(\\])",
                                                                                              "$1"
                                                                                                         + (stringIndex + 1)
                                                                                                         + "$2")});

                // Set the flag to indicate that a change to the link definitions was made
                updateLinks = true;
            }
        }
    }

    /**********************************************************************************************
     * Build the command to delete the specified table/variable references from the links table
     *
     * @param linksPath
     *            table/variable path to remove from the links table. Leading or trailing regular
     *            expression constructs must surround the path, and any reserved PostgreSQL
     *            characters in the path must be escaped
     *
     * @param linksCmd
     *            StringBuilder containing the existing links table deletion command. If empty then
     *            a new command is initiated; otherwise the deletions are appended to the existing
     *            command
     *********************************************************************************************/
    private void deleteLinkPathRef(String linksPath, StringBuilder linksCmd)
    {
        linksCmd.append("DELETE FROM "
                        + InternalTable.LINKS.getTableName()
                        + " WHERE "
                        + LinksColumn.MEMBER.getColumnName()
                        + " ~ E'"
                        + linksPath
                        + "'; ");
    }

    /**********************************************************************************************
     * Build the commands to delete the specified table/variable references from the telemetry
     * scheduler table
     *
     * @param tlmPath
     *            table/variable path to remove from the telemetry scheduler table. Leading or
     *            trailing regular expression constructs must surround the path, and any reserved
     *            PostgreSQL characters in the path must be escaped
     *
     * @param tlmCmd
     *            StringBuilder containing the existing telemetry scheduler table deletion command.
     *            If empty then a new command is initiated; otherwise the deletions are appended to
     *            the existing command
     *********************************************************************************************/
    private void deleteTlmPathRef(String tlmPath, StringBuilder tlmCmd)
    {
        tlmCmd.append("DELETE FROM "
                      + InternalTable.TLM_SCHEDULER.getTableName()
                      + " WHERE "
                      + TlmSchedulerColumn.MEMBER.getColumnName()
                      + " ~ E'^.*"
                      + tlmSchSeparator
                      + tlmPath
                      + "'; ");
    }

    /**********************************************************************************************
     * Build the command to delete the specified variable references from the telemetry scheduler
     * table
     *
     * @param variablePaths
     *            list of variable paths to remove from the telemetry scheduler table
     *
     * @return Command to delete the specified variable references from the telemetry scheduler
     *         table
     *********************************************************************************************/
    private String deleteTlmPathRefs(List<String> variablePaths)
    {
        StringBuilder tlmCommand = new StringBuilder("");

        // Check if any paths are supplied
        if (variablePaths != null)
        {
            // Step through each variable path
            for (String path : variablePaths)
            {
                // Add the command to delete all of the variable's references from the telemetry
                // scheduler table
                deleteTlmPathRef(CcddUtilities.escapePostgreSQLReservedChars(path), tlmCommand);
            }

            // Check if a change to the telemetry scheduler table exists
            if (tlmCommand.length() != 0)
            {
                // Terminate the command
                tlmCommand.append("; ");
            }
        }

        return tlmCommand.toString();
    }

    /**********************************************************************************************
     * Remove all references to the specified variable paths in the links and telemetry scheduler
     * tables. If the variable is an array definition then amend the regular expression to match
     * all members of the array; skip any subsequent members of the array passed to this method
     *
     * @param arraySize
     *            variable array size
     *
     * @param variableName
     *            variable name
     *
     * @param variablePath
     *            variable path
     *
     * @param variablePathEsc
     *            variable path with any PostgreSQL reserved characters escaped so that the path
     *            can be used in a regular expression
     *
     * @param linksDelCmd
     *            StringBuilder containing the existing links table deletion command
     *
     * @param tlmDelCmd
     *            StringBuilder containing the existing telemetry scheduler table deletion command
     *********************************************************************************************/
    private void deleteLinkAndTlmPathRef(String arraySize,
                                         String variableName,
                                         String variablePath,
                                         String variablePathEsc,
                                         StringBuilder linksDelCmd,
                                         StringBuilder tlmDelCmd)
    {
        boolean isOkayToDelete = true;

        // Set the default regular expression for matching the path
        String pathEndRegEx = "(?:,|:|$)";

        // Check if this is an array variable
        if (!arraySize.isEmpty())
        {
            // Check if this is the array definition
            if (!ArrayVariable.isArrayMember(variableName))
            {
                // Add the array definition to the list of those to delete
                deletedArrayDefns.add(variablePath);

                // Set the regular expression for matching the path to include all array members
                pathEndRegEx = "(?:\\\\[[0-9]+\\\\],|:|\\\\[[0-9]+\\\\]$)";
            }
            // This is an array member
            else
            {
                // Get the variable path without the array index (this is what appears as the array
                // definition's path)
                String pathWithoutArrayIndex = variablePath.replaceFirst("(?:\\[[0-9]+\\])?$", "");

                // Step through the list of deleted array definitions
                for (String deletedArray : deletedArrayDefns)
                {
                    // Check if this array member is a member of an entirely deleted array
                    if (deletedArray.startsWith(pathWithoutArrayIndex))
                    {
                        // Set the flag so that deletion of the array member is skipped (it was
                        // handled by the array definition deletion) and stop searching
                        isOkayToDelete = false;
                        break;
                    }
                }
            }
        }

        // Check if the variable can be deleted (i.e., this is not an array variable, is an array
        // definition, or is an array member that doesn't have its definition already set to delete
        if (isOkayToDelete)
        {
            // Remove all references to the structure and its children from the links and telemetry
            // scheduler tables
            deleteLinkPathRef("^" + variablePathEsc + pathEndRegEx, linksDelCmd);
            deleteTlmPathRef(variablePathEsc + pathEndRegEx, tlmDelCmd);
        }
    }

    /**********************************************************************************************
     * Build the command to remove all references for the specified rate column name in tables of
     * the changed table type in the links and telemetry scheduler tables
     *
     * @param rateColName
     *            rate column name
     *
     * @param linksCmd
     *            sub-command for references in the links table to tables of the target type
     *
     * @param tlmSchCmd
     *            sub-command for references in the telemetry scheduler table to tables of the
     *            target type
     *
     * @return Command to remove all references for the specified rate column name in tables of the
     *         changed table type in the links and telemetry scheduler tables
     *********************************************************************************************/
    private String deleteLinkAndTlmRateRef(String rateColName,
                                           StringBuilder linksCmd,
                                           StringBuilder tlmSchCmd)
    {
        return "DELETE FROM "
               + InternalTable.LINKS.getTableName()
               + " WHERE "
               + LinksColumn.RATE_NAME.getColumnName()
               + " = E'"
               + rateColName
               + "'"
               + linksCmd
               + "; DELETE FROM "
               + InternalTable.TLM_SCHEDULER.getTableName()
               + " WHERE "
               + TlmSchedulerColumn.RATE_NAME.getColumnName()
               + " = '"
               + rateColName
               + "'"
               + tlmSchCmd
               + "; ";
    }

    /**********************************************************************************************
     * Build the command to remove all references to the specified rate column name in the links
     * and telemetry scheduler tables
     *
     * @param rateColName
     *            rate column name
     *
     * @return Command to remove all references to the specified rate column name in the links and
     *         telemetry scheduler tables
     *********************************************************************************************/
    private String deleteAllLinkAndTlmRateRefs(String rateColName)
    {
        return "DELETE FROM "
               + InternalTable.LINKS.getTableName()
               + " WHERE "
               + LinksColumn.RATE_NAME.getColumnName()
               + " = '"
               + rateColName
               + "'; DELETE FROM "
               + InternalTable.TLM_SCHEDULER.getTableName()
               + " WHERE "
               + TlmSchedulerColumn.RATE_NAME.getColumnName()
               + " = '"
               + rateColName
               + "'; ";
    }

    /**********************************************************************************************
     * Update any references to a variable that had its name changed in every table cell and data
     * field with a variable reference input type
     *
     * @param tableInfo
     *            reference to variable's table information
     *
     * @param oldVariableName
     *            original variable name
     *
     * @param newVariableName
     *            new variable name
     *
     * @param oldDataType
     *            original data type
     *
     * @param newDataType
     *            new data type
     *
     * @param varRefChkResults
     *            results of the variable reference input type search
     *
     * @param valuesModCmd
     *            StringBuilder containing the existing values table modification command
     *
     * @param fieldsModCmd
     *            StringBuilder containing the existing fields table modification command
     *********************************************************************************************/
    private void modifyVariableReference(TableInformation tableInfo,
                                         String oldVariableName,
                                         String newVariableName,
                                         String oldDataType,
                                         String newDataType,
                                         ReferenceCheckResults varRefChkResults,
                                         StringBuilder valuesModCmd,
                                         StringBuilder fieldsModCmd)
    {
        ArrayListMultiple targetVars = new ArrayListMultiple();

        // Build the variable name matching regular expression
        String match = "(^|.+,"
                       + tableInfo.getPrototypeName()
                       + ".[^,]+,)"
                       + oldDataType
                       + "\\."
                       + CcddUtilities.escapePostgreSQLReservedChars(oldVariableName)
                       + "(,.+|$)";

        // Check if the table with the variable name that changed is a root structure
        if (rootStructures.contains(tableInfo.getProtoVariableName()))
        {
            targetVars.add(new String[] {CcddUtilities.escapePostgreSQLReservedChars(tableInfo.getPrototypeName()
                                                                                     + ","
                                                                                     + oldVariableName),
                                         tableInfo.getPrototypeName()
                                                                                                         + ","
                                                                                                         + newVariableName});
        }
        // The table isn't a root structure
        else
        {
            // Step through every variable name
            for (String variable : variableHandler.getAllVariableNames())
            {
                // Check if the variable matches the pattern for the changed variable name
                if (variable.matches(match))
                {
                    // Remove the data type(s) from the variable path (the variable reference input
                    // type only displays the variable name portion of the path)
                    String targetVar = CcddUtilities.escapePostgreSQLReservedChars(variableHandler.removeDataTypeFromVariablePath(variable));

                    // Check if the variable isn't already in the list
                    if (!targetVars.contains(targetVar))
                    {
                        // Add the before and after variable names to the list for use in building
                        // the modification commands
                        targetVars.add(new String[] {targetVar,
                                                     variableHandler.removeDataTypeFromVariablePath(variable.replaceFirst(match,
                                                                                                                          "$1"
                                                                                                                                 + newDataType
                                                                                                                                 + "."
                                                                                                                                 + newVariableName
                                                                                                                                 + "$2"))});
                    }
                }
            }
        }

        // Step through each table type variable reference input type reference
        for (InputTypeReference varRef : varRefChkResults.getReferences())
        {
            // Step through each table of this table type
            for (String table : varRef.getTables())
            {
                // Step through each variable affected by the name change
                for (String[] targetVar : targetVars)
                {
                    // Update references to the variable reference from the prototype table
                    valuesModCmd.append("UPDATE "
                                        + dbControl.getQuotedName(table)
                                        + " SET "
                                        + varRef.getColumnDb()
                                        + " = '"
                                        + targetVar[1]
                                        + "' WHERE "
                                        + varRef.getColumnDb()
                                        + " = E'"
                                        + targetVar[0]
                                        + "'; ");

                    // Check if the table isn't a root structure
                    if (!rootStructures.contains(table))
                    {
                        // Update references to the variable reference from the instances
                        valuesModCmd.append("UPDATE "
                                            + InternalTable.VALUES.getTableName()
                                            + " SET "
                                            + ValuesColumn.VALUE.getColumnName()
                                            + " = '"
                                            + targetVar[1]
                                            + "' WHERE "
                                            + ValuesColumn.TABLE_PATH.getColumnName()
                                            + " ~ E'^.+,"
                                            + table
                                            + "\\\\..*$' AND "
                                            + ValuesColumn.COLUMN_NAME.getColumnName()
                                            + " = '"
                                            + varRef.getColumnVisible()
                                            + "' AND "
                                            + ValuesColumn.VALUE.getColumnName()
                                            + " = E'"
                                            + targetVar[0]
                                            + "'; ");
                    }
                }
            }
        }

        // Check if a data field has the variable reference input type
        if (varRefChkResults.isFieldUsesType())
        {
            // Step through each variable affected by the name change
            for (String targetVar[] : targetVars)
            {
                // Update the data field value if the variable path matches
                fieldsModCmd.append("UPDATE "
                                    + InternalTable.FIELDS.getTableName()
                                    + " SET "
                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                    + " = E'"
                                    + targetVar[1]
                                    + "' WHERE "
                                    + FieldsColumn.FIELD_TYPE.getColumnName()
                                    + " = E'"
                                    + DefaultInputType.VARIABLE_REFERENCE.getInputName()
                                    + "' AND "
                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                    + " = E'"
                                    + targetVar[0]
                                    + "'; ");
            }
        }
    }

    /**********************************************************************************************
     * Blank any references to a variable that has been deleted in every table cell and data field
     * with a variable reference input type
     *
     * @param tableInfo
     *            reference to variable's table information
     *
     * @param variableName
     *            name of the deleted variable
     *
     * @param varRefChkResults
     *            results of the variable reference input type search
     *
     * @param valuesDelCmd
     *            StringBuilder containing the existing values table deletion command
     *
     * @param fieldsDelCmd
     *            StringBuilder containing the existing fields table deletion command
     *********************************************************************************************/
    private void deleteVariableReference(TableInformation tableInfo,
                                         String variableName,
                                         ReferenceCheckResults varRefChkResults,
                                         StringBuilder valuesDelCmd,
                                         StringBuilder fieldsDelCmd)
    {
        // Get the path without the data type(s)
        String instanceVarPathEscNoDt = "(?:^|.+,)"
                                        + tableInfo.getPrototypeName()
                                        + ","
                                        + CcddUtilities.escapePostgreSQLReservedChars(variableName)
                                        + "(?:,|$)";

        // Step through each table type variable reference input type reference
        for (InputTypeReference varRef : varRefChkResults.getReferences())
        {
            // Step through each table of this table type
            for (String table : varRef.getTables())
            {
                // Remove references to the variable reference from the prototype table
                valuesDelCmd.append("UPDATE "
                                    + dbControl.getQuotedName(table)
                                    + " SET "
                                    + varRef.getColumnDb()
                                    + " = '' WHERE "
                                    + varRef.getColumnDb()
                                    + " ~ E'"
                                    + instanceVarPathEscNoDt
                                    + "'; ");

                // Check if the table isn't a root structure
                if (!rootStructures.contains(table))
                {
                    // Remove references to the variable reference from the instances
                    valuesDelCmd.append("UPDATE "
                                        + InternalTable.VALUES.getTableName()
                                        + " SET "
                                        + ValuesColumn.VALUE.getColumnName()
                                        + " = '' WHERE "
                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                        + " ~ E'^.+,"
                                        + table
                                        + "\\\\..*$' AND "
                                        + ValuesColumn.COLUMN_NAME.getColumnName()
                                        + " = '"
                                        + varRef.getColumnVisible()
                                        + "' AND "
                                        + ValuesColumn.VALUE.getColumnName()
                                        + " ~ E'"
                                        + instanceVarPathEscNoDt
                                        + "'; ");
                }
            }
        }

        // Check if a data field has the variable reference input type
        if (varRefChkResults.isFieldUsesType())
        {
            // Blank the data field value if the variable path matches
            fieldsDelCmd.append("UPDATE "
                                + InternalTable.FIELDS.getTableName()
                                + " SET "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " = '' WHERE "
                                + FieldsColumn.FIELD_TYPE.getColumnName()
                                + " = E'"
                                + DefaultInputType.VARIABLE_REFERENCE.getInputName()
                                + "' AND "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " ~ E'"
                                + instanceVarPathEscNoDt
                                + "'; ");
        }
    }

    /**********************************************************************************************
     * Update any references to a command that had its name changed in every table cell and data
     * field with a command reference input type
     *
     * @param tableInfo
     *            reference to command's table information
     *
     * @param oldCommandName
     *            original command name
     *
     * @param newCommandName
     *            new command name
     *
     * @param oldCommandCode
     *            original command code
     *
     * @param newCommandCode
     *            new command code
     *
     * @param oldNumArgs
     *            originals number of arguments
     *
     * @param newNumArgs
     *            new number of arguments
     *
     * @param cmdRefChkResults
     *            results of the command reference input type search
     *
     * @param valuesModCmd
     *            StringBuilder containing the existing values table modification command
     *
     * @param fieldsModCmd
     *            StringBuilder containing the existing fields table modification command
     *********************************************************************************************/
    private void modifyCommandReference(TableInformation tableInfo,
                                        String oldCommandName,
                                        String newCommandName,
                                        String oldCommandCode,
                                        String newCommandCode,
                                        int oldNumArgs,
                                        int newNumArgs,
                                        ReferenceCheckResults cmdRefChkResults,
                                        StringBuilder valuesModCmd,
                                        StringBuilder fieldsModCmd)
    {
        // Build the original and new command references
        String oldCmdRef = commandHandler.buildCommandReference(oldCommandName,
                                                                oldCommandCode,
                                                                tableInfo.getTablePath(),
                                                                oldNumArgs);
        String newCmdRef = commandHandler.buildCommandReference(newCommandName,
                                                                newCommandCode,
                                                                tableInfo.getTablePath(),
                                                                newNumArgs);

        // Step through each table type command reference input type reference
        for (InputTypeReference cmdRef : cmdRefChkResults.getReferences())
        {
            // Step through each table of this table type
            for (String table : cmdRef.getTables())
            {
                // Update references to the command reference from the table
                valuesModCmd.append("UPDATE "
                                    + dbControl.getQuotedName(table)
                                    + " SET "
                                    + cmdRef.getColumnDb()
                                    + " = '"
                                    + newCmdRef
                                    + "' WHERE "
                                    + cmdRef.getColumnDb()
                                    + " = E'"
                                    + oldCmdRef
                                    + "'; ");
            }
        }

        // Check if a data field has the command reference input type
        if (cmdRefChkResults.isFieldUsesType())
        {
            // Update the data field value if the command path matches
            fieldsModCmd.append("UPDATE "
                                + InternalTable.FIELDS.getTableName()
                                + " SET "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " = E'"
                                + newCmdRef
                                + "' WHERE "
                                + FieldsColumn.FIELD_TYPE.getColumnName()
                                + " = E'"
                                + DefaultInputType.COMMAND_REFERENCE.getInputName()
                                + "' AND "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " = E'"
                                + oldCmdRef
                                + "'; ");
        }
    }

    /**********************************************************************************************
     * Blank any references to a command that has been deleted in every table cell and data field
     * with a command reference input type
     *
     * @param tableInfo
     *            reference to command's table information
     *
     * @param commandName
     *            name of the deleted command
     *
     * @param cmdRefChkResults
     *            results of the command reference input type search
     *
     * @param valuesDelCmd
     *            StringBuilder containing the existing values table deletion command
     *
     * @param fieldsDelCmd
     *            StringBuilder containing the existing fields table deletion command
     *********************************************************************************************/
    private void deleteCommandReference(TableInformation tableInfo,
                                        String commandName,
                                        ReferenceCheckResults cmdRefChkResults,
                                        StringBuilder valuesDelCmd,
                                        StringBuilder fieldsDelCmd)
    {
        // Append the regular expression needed to include the command code, table, and number of
        // arguments
        commandName += " \\(.*";

        // Step through each table type command reference input type reference
        for (InputTypeReference cmdRef : cmdRefChkResults.getReferences())
        {
            // Step through each table of this table type
            for (String table : cmdRef.getTables())
            {
                // Remove references to the command reference from the table
                valuesDelCmd.append("UPDATE "
                                    + dbControl.getQuotedName(table)
                                    + " SET "
                                    + cmdRef.getColumnDb()
                                    + " = '' WHERE "
                                    + cmdRef.getColumnDb()
                                    + " ~ E'"
                                    + commandName
                                    + "'; ");
            }
        }

        // Check if a data field has the command reference input type
        if (cmdRefChkResults.isFieldUsesType())
        {
            // Blank the data field value if the command matches
            fieldsDelCmd.append("UPDATE "
                                + InternalTable.FIELDS.getTableName()
                                + " SET "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " = '' WHERE "
                                + FieldsColumn.FIELD_TYPE.getColumnName()
                                + " = E'"
                                + DefaultInputType.COMMAND_REFERENCE.getInputName()
                                + "' AND "
                                + FieldsColumn.FIELD_VALUE.getColumnName()
                                + " ~ E'"
                                + commandName
                                + "'; ");
        }
    }

    /**********************************************************************************************
     * Build the commands to update any references to a message name or ID that has been modified
     * or deleted in every table cell and data field with a message name and ID reference input
     * type
     *
     * @param tableInfo
     *            table information
     *
     * @param typeDefn
     *            table's type definition
     *
     * @param modifications
     *            list of row update information
     *
     * @param deletions
     *            list of row deletion information
     *
     * @param msgIDRefChkResults
     *            results of the message name and ID reference input type search
     *
     * @return Message name and ID cells and fields update command
     *********************************************************************************************/
    private String updateMessageNameAndIDReference(TableInformation tableInfo,
                                                   TypeDefinition typeDefn,
                                                   List<TableModification> modifications,
                                                   List<TableModification> deletions,
                                                   ReferenceCheckResults msgIDRefChkResults)
    {
        StringBuilder msgIDCmd = new StringBuilder("");

        // Check if any table column or data field uses the message name & ID input type
        if (!msgIDRefChkResults.getReferences().isEmpty() || msgIDRefChkResults.isFieldUsesType())
        {
            // Create storage for the lists of names and IDs before and after the change is applied
            List<String> msgNameIDs = new ArrayList<String>();
            List<String> newMsgNameIDs = new ArrayList<String>();

            // Get the indices for columns using the message name and ID input type
            List<Integer> msgColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.MESSAGE_NAME_AND_ID);

            // Step through each row of data in the table
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                boolean isModDel = false;

                // Step through each row modification
                for (TableModification mod : modifications)
                {
                    // Check if the modified row's index matches the current row
                    if (row == Integer.valueOf(mod.getRowData()[DefaultColumn.ROW_INDEX.ordinal()].toString()) - 1)
                    {
                        // Step through each message name and ID column
                        for (Integer nameCol : msgColumns)
                        {
                            // Store the original and (possibly) updated message name
                            msgNameIDs.add(mod.getOriginalRowData()[nameCol].toString());
                            newMsgNameIDs.add(mod.getRowData()[nameCol].toString());
                        }

                        // Set the flag to indicate a modification was found and stop searching
                        isModDel = true;
                        break;
                    }
                }

                // Check if the current row wasn't modified
                if (!isModDel)
                {
                    // Step through each row deletion
                    for (TableModification del : deletions)
                    {
                        // Check if the deleted row's index matches the current row
                        if (row == Integer.valueOf(del.getRowData()[DefaultColumn.ROW_INDEX.ordinal()].toString()) - 1)
                        {
                            // Step through each message name and ID column
                            for (Integer nameCol : msgColumns)
                            {
                                // Store the original message name and a blank as the updated name
                                msgNameIDs.add(del.getRowData()[nameCol].toString());
                                newMsgNameIDs.add("");
                            }

                            // Set the flag to indicate a deletion was found and stop searching
                            isModDel = true;
                            break;
                        }
                    }
                }

                // Check if the current row wasn't modified or deleted
                if (!isModDel)
                {
                    // Step through each message name and ID column
                    for (Integer nameCol : msgColumns)
                    {
                        // Store the message name as both original and updated (no change)
                        msgNameIDs.add(tableInfo.getData()[row][nameCol].toString());
                        newMsgNameIDs.add(tableInfo.getData()[row][nameCol].toString());
                    }
                }
            }

            // Get the reference to the message name and ID input type
            InputType nameType = inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID);

            // Step through each of the table's data fields
            for (FieldInformation fieldInfo : tableInfo.getFieldInformation())
            {
                // Check if the field's input type is for a message name
                if (fieldInfo.getInputType().equals(nameType))
                {
                    // Get the reference to the field in the field handler. This has the field's
                    // original value
                    FieldInformation fldInfo = fieldHandler.getFieldInformationByInputType(fieldInfo.getOwnerName(),
                                                                                           nameType);

                    // Store the original and (possibly) updated message name
                    msgNameIDs.add(fldInfo != null
                                                   ? fldInfo.getValue()
                                                   : "");
                    newMsgNameIDs.add(fldInfo != null
                                                      ? fieldInfo.getValue()
                                                      : "");
                }
            }

            // Step through the lists of message names and IDs
            for (int index = 0; index < msgNameIDs.size(); index++)
            {
                // Check if the message name and ID isn't blank
                if (!msgNameIDs.get(index).isEmpty())
                {
                    // Separate the message name and ID in the original and updated name/ID string
                    String[] nameAndID = CcddMessageIDHandler.getMessageNameAndID(msgNameIDs.get(index));
                    String[] newNameAndID = CcddMessageIDHandler.getMessageNameAndID(newMsgNameIDs.get(index));

                    // Build the original and updated name, ID, and owner reference
                    String msgNameIDOwner = nameAndID[0]
                                            + " (ID: "
                                            + nameAndID[1]
                                            + ", owner: "
                                            + tableInfo.getTablePath()
                                            + ")";
                    String newMsgNameIDOwner = newMsgNameIDs.get(index).isEmpty()
                                                                                  ? ""
                                                                                  : newNameAndID[0]
                                                                                    + " (ID: "
                                                                                    + newNameAndID[1]
                                                                                    + ", owner: "
                                                                                    + tableInfo.getTablePath()
                                                                                    + ")";

                    // Step through each table type message name & ID reference input type
                    // reference
                    for (InputTypeReference msgIDRef : msgIDRefChkResults.getReferences())
                    {
                        // Step through each table of this table type
                        for (String table : msgIDRef.getTables())
                        {
                            // Update references to the message name & ID reference from the table
                            msgIDCmd.append("UPDATE "
                                            + dbControl.getQuotedName(table)
                                            + " SET "
                                            + msgIDRef.getColumnDb()
                                            + " = '"
                                            + newMsgNameIDOwner
                                            + "' WHERE "
                                            + msgIDRef.getColumnDb()
                                            + " = E'"
                                            + msgNameIDOwner
                                            + "'; ");
                        }
                    }

                    // Check if a data field has the message name & ID reference input type
                    if (msgIDRefChkResults.isFieldUsesType())
                    {
                        // Update the data field value if the message name & ID matches
                        msgIDCmd.append("UPDATE "
                                        + InternalTable.FIELDS.getTableName()
                                        + " SET "
                                        + FieldsColumn.FIELD_VALUE.getColumnName()
                                        + " = '"
                                        + newMsgNameIDOwner
                                        + "' WHERE "
                                        + FieldsColumn.FIELD_TYPE.getColumnName()
                                        + " = E'"
                                        + DefaultInputType.MESSAGE_REFERENCE.getInputName()
                                        + "' AND "
                                        + FieldsColumn.FIELD_VALUE.getColumnName()
                                        + " = E'"
                                        + msgNameIDOwner
                                        + "'; ");
                    }
                }
            }

            // Set the flag to indicate if a message name & ID reference may have changed
            isMsgIDChange = msgIDCmd.length() != 0;
        }

        return msgIDCmd.toString();
    }

    /**********************************************************************************************
     * Retrieve a list of internal table data from the database, ignoring script file information
     * tables
     *
     * @param intTable
     *            type of internal table to retrieve
     *
     * @param includeOID
     *            true to read in the OID column in addition to the information table columns
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List of the items in the internal table. An empty list is returned if the specified
     *         table is empty or doesn't exist
     *********************************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      boolean includeOID,
                                                      Component parent)
    {
        return retrieveInformationTable(intTable, includeOID, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of internal table data from the database
     *
     * @param intTable
     *            type of internal table to retrieve
     *
     * @param includeOID
     *            true to read in the OID column in addition to the information table columns
     *
     * @param scriptName
     *            script file name; ignored for non-script file information tables
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List of the items in the internal table. An empty list is returned if the specified
     *         table is empty or doesn't exist
     *********************************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      boolean includeOID,
                                                      String scriptName,
                                                      Component parent)
    {
        // Create a list to contain the internal table items
        List<String[]> tableData = new ArrayList<String[]>();

        // Get the internal table name
        String intTableName = intTable.getTableName(scriptName);

        try
        {
            // Check that the internal table exists in the database
            if (isTableExists(intTableName, parent))
            {
                // Get the internal table
                ResultSet infoData = dbCommand.executeDbQuery("SELECT *"
                                                              + (includeOID
                                                                            ? ", OID"
                                                                            : "")
                                                              + " FROM "
                                                              + intTableName
                                                              + " ORDER BY OID;",
                                                              parent);

                // Step through each of the query results
                while (infoData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[infoData.getMetaData().getColumnCount()];

                    // Step through each column in the row
                    for (int column = 0; column < infoData.getMetaData().getColumnCount(); column++)
                    {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0
                        columnValues[column] = infoData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Add the row data to the list
                    tableData.add(columnValues);
                }

                infoData.close();
            }
        }
        catch (SQLException se)
        {
            // Inform the user that loading the internal table failed
            eventLog.logFailEvent(parent,
                                  "Cannot load internal table '"
                                          + intTableName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot load internal table '</b>"
                                                 + intTableName
                                                 + "<b>'");
        }

        return tableData;
    }

    /**********************************************************************************************
     * Store the internal table into the database. This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            array containing the table data to store
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void storeInformationTableInBackground(final InternalTable intTable,
                                                     final List<String[]> tableData,
                                                     final String tableComment,
                                                     final Component parent)
    {
        storeInformationTableInBackground(intTable,
                                          tableData,
                                          null,
                                          null,
                                          null,
                                          tableComment,
                                          parent);
    }

    /**********************************************************************************************
     * Store the internal table into the database. This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            list containing the table data to store
     *
     * @param fieldInformationList
     *            list containing the list of data field information for each group with a data
     *            field update (only applicable to the groups table); null if none
     *
     * @param deletedGroups
     *            list containing the names of groups that have been deleted
     *
     * @param invalidLinkVars
     *            list containing the link member variables that should be removed from the
     *            telemetry scheduler due to the addition of one or more variables; null or an
     *            empty list if there are no invalid link members
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void storeInformationTableInBackground(final InternalTable intTable,
                                                     final List<String[]> tableData,
                                                     final List<List<FieldInformation>> fieldInformationList,
                                                     final List<String> deletedGroups,
                                                     final List<String> invalidLinkVars,
                                                     final String tableComment,
                                                     final Component parent)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
        {
            /**************************************************************************************
             * Database internal table store command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                storeInformationTable(intTable,
                                      tableData,
                                      fieldInformationList,
                                      deletedGroups,
                                      invalidLinkVars,
                                      tableComment,
                                      parent);
            }
        });
    }

    /**********************************************************************************************
     * Store the internal table into the database
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            array containing the table data to store; unused for the table types internal
     *            table
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void storeInformationTable(InternalTable intTable,
                                         List<String[]> tableData,
                                         String tableComment,
                                         Component parent)
    {
        storeInformationTable(intTable, tableData, null, null, null, tableComment, parent);
    }

    /**********************************************************************************************
     * Store the internal table into the database
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            list containing the table data to store; unused for the table types internal
     *            table
     *
     * @param fieldInformationList
     *            list containing the list of data field information for each group with a data
     *            field update (only applicable to the groups table); null if none
     *
     * @param deletedGroups
     *            list containing the names of groups that have been deleted
     *
     * @param invalidLinkVars
     *            list containing the link member variables that should be removed from the
     *            telemetry scheduler due to the addition of one or more variables; null or an
     *            empty list if there are no invalid link members
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void storeInformationTable(InternalTable intTable,
                                         List<String[]> tableData,
                                         List<List<FieldInformation>> fieldInformationList,
                                         List<String> deletedGroups,
                                         List<String> invalidLinkVars,
                                         String tableComment,
                                         Component parent)
    {
        boolean errorFlag = false;

        // Get the internal table name
        String intTableName = intTable.getTableName(tableComment);

        try
        {
            String command = "";

            switch (intTable)
            {
                case GROUPS:
                    // Check if a list of deleted groups is provided
                    if (deletedGroups != null)
                    {
                        // Step through each deleted group
                        for (String groupName : deletedGroups)
                        {
                            // Build the command to delete the group's data fields
                            command += modifyFieldsCommand(CcddFieldHandler.getFieldGroupName(groupName),
                                                           null);
                        }
                    }

                    // Check if a list of group data fields is provided
                    if (fieldInformationList != null)
                    {
                        // Step through each group's data field information list
                        for (List<FieldInformation> fieldInformation : fieldInformationList)
                        {
                            // Build the command to modify the data fields for the group
                            command += modifyFieldsCommand(fieldInformation.get(0).getOwnerName(),
                                                           fieldInformation);
                        }
                    }

                case APP_SCHEDULER:
                case ASSOCIATIONS:
                case DATA_TYPES:
                case FIELDS:
                case INPUT_TYPES:
                case MACROS:
                case ORDERS:
                case RESERVED_MSG_IDS:
                case SCRIPT:
                case TLM_SCHEDULER:
                case USERS:
                    // Build the command for storing the script configurations, groups, links, etc.
                    // table
                    command += storeNonTableTypesInfoTableCommand(intTable,
                                                                  tableData,
                                                                  tableComment,
                                                                  parent);
                    break;

                case LINKS:
                    // Build the commands for storing the links table and for deleting any invalid
                    // references in the telemetry scheduler table
                    command += storeNonTableTypesInfoTableCommand(intTable,
                                                                  tableData,
                                                                  tableComment,
                                                                  parent)
                               + deleteTlmPathRefs(invalidLinkVars);

                    break;

                case TABLE_TYPES:
                    // Build the command for storing the table type definitions table
                    command = storeTableTypesInfoTableCommand();
                    break;

                case VALUES:
                    break;
            }

            // Execute the database update
            dbCommand.executeDbUpdate(command, parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, intTableName + " stored");
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot store internal table '"
                                          + intTableName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot store internal table '</b>"
                                                 + intTableName
                                                 + "<b>'");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        switch (intTable)
        {
            case ASSOCIATIONS:
                // Check if the store request originated from the script manager dialog
                if (parent instanceof CcddScriptManagerDialog)
                {
                    // Perform the script associations command completion steps
                    ((CcddScriptManagerDialog) parent).doAssnUpdatesComplete(errorFlag);
                }

                break;

            case GROUPS:
                // Check if the store request originated from the group manager dialog
                if (parent instanceof CcddGroupManagerDialog)
                {
                    // Perform the groups store command completion steps
                    ((CcddGroupManagerDialog) parent).doGroupUpdatesComplete(errorFlag);
                }

                break;

            case LINKS:
                // Check if the store request originated from the link manager dialog
                if (parent instanceof CcddLinkManagerDialog)
                {
                    // Perform the links store command completion steps
                    ((CcddLinkManagerDialog) parent).doLinkUpdatesComplete(errorFlag);
                }

                break;

            case APP_SCHEDULER:
            case TLM_SCHEDULER:
                // Check if the store request originated from the application or telemetry
                // scheduler dialogs
                if (parent instanceof CcddSchedulerDialogInterface)
                {
                    // Perform the scheduler store command completion steps
                    ((CcddSchedulerDialogInterface) parent).doSchedulerUpdatesComplete(errorFlag);
                }

                break;

            case RESERVED_MSG_IDS:
                // Check if the store request originated from the reserved message ID editor dialog
                if (parent instanceof CcddReservedMsgIDEditorDialog)
                {
                    // Perform the reserved message ID store command completion steps
                    ((CcddReservedMsgIDEditorDialog) parent).doMsgIDUpdatesComplete(errorFlag);
                }

                break;

            case USERS:
                // Check if the store request originated from the user access level editor dialog
                if (parent instanceof CcddDbManagerDialog)
                {
                    // Perform the user access level store command completion steps
                    ((CcddDbManagerDialog) parent).doAccessUpdatesComplete(errorFlag);
                }

                break;

            case DATA_TYPES:
            case FIELDS:
            case INPUT_TYPES:
            case MACROS:
            case ORDERS:
            case SCRIPT:
            case TABLE_TYPES:
            case VALUES:
                break;
        }
    }

    /**********************************************************************************************
     * Build the command for storing the groups, script associations, links table, data fields, or
     * script
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            array containing the table data to store
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Command for building the specified table
     *********************************************************************************************/
    private String storeNonTableTypesInfoTableCommand(InternalTable intTable,
                                                      List<String[]> tableData,
                                                      String tableComment,
                                                      Component parent)
    {
        // Get the internal table's name
        String tableName = intTable.getTableName(tableComment);

        // Build the command to delete the information list table if it exists, then the creation
        // commands
        StringBuilder command = new StringBuilder("DROP TABLE IF EXISTS "
                                                  + tableName
                                                  + "; CREATE TABLE "
                                                  + tableName
                                                  + " "
                                                  + intTable.getColumnCommand(false)
                                                  + dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                                                tableName));

        try
        {
            // Check if no comment is provided
            if (tableComment == null)
            {
                // Get the comment for the internal table
                ResultSet comment = dbCommand.executeDbQuery("SELECT obj_description('public."
                                                             + tableName
                                                             + "'::regclass, 'pg_class');",
                                                             parent);

                // Check if a comment exists
                if (comment.next() && comment.getString(1) != null)
                {
                    // Store the table's original comment
                    tableComment = comment.getString(1);
                }

                comment.close();
            }

            // Check if a comment exists
            if (tableComment != null)
            {
                // Build the command to restore the comment after the table is deleted and
                // recreated
                command.append("COMMENT ON TABLE "
                               + tableName
                               + " IS "
                               + delimitText(tableComment)
                               + "; ");
            }
        }
        catch (SQLException se)
        {
            // Inform the user that loading the internal table comment failed
            eventLog.logFailEvent(parent,
                                  "Cannot obtain comment for internal table '"
                                          + tableName
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                  "<html><b>Cannot obtain comment for internal table '</b>"
                                                 + tableName
                                                 + "<b>'");
        }

        // Check if the internal table contains any items
        if (!tableData.isEmpty())
        {
            // Append the insert value command to add the items
            command.append("INSERT INTO " + tableName + " VALUES ");

            // Step through each row in the table data
            for (Object row[] : tableData)
            {
                // Add the column initiator
                command.append("(");

                // Step through each column in the table
                for (Object column : row)
                {
                    // Add the item to the command enclosing it in an appropriate delimiter if the
                    // item is text
                    command.append(delimitText(column) + ", ");
                }

                // Remove the trailing comma and space, then terminate the column
                command = CcddUtilities.removeTrailer(command, ", ");
                command.append("), ");
            }

            // Replace the trailing comma and space with a semicolon
            command = CcddUtilities.removeTrailer(command, ", ");
            command.append("; ");
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Build the command for storing the table type definitions table
     *
     * @return Command for building the specified table
     *********************************************************************************************/
    protected String storeTableTypesInfoTableCommand()
    {
        // Build the command to delete the existing table type definitions table and create the new
        // one
        String cmd = "DROP TABLE IF EXISTS "
                     + InternalTable.TABLE_TYPES.getTableName()
                     + "; CREATE TABLE "
                     + InternalTable.TABLE_TYPES.getTableName()
                     + " "
                     + InternalTable.TABLE_TYPES.getColumnCommand(!tableTypeHandler.getTypeDefinitions().isEmpty());

        // Get the index of the default column definitions command (if present)
        int cmdIndex = cmd.indexOf(DefaultColumn.getColumnDefinitions());

        // Check if the default column definitions command is present
        if (cmdIndex != -1)
        {
            // Remove the default column definitions from the command
            cmd = cmd.substring(0, cmd.indexOf(DefaultColumn.getColumnDefinitions()));
        }

        // Convert the command to a StringBuilder for efficiency
        StringBuilder command = new StringBuilder(cmd);

        // Step through each table type definition
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Step through each column definition
            for (int index = 0; index < typeDefn.getColumnNamesDatabase().length; index++)
            {
                // Add the command to create the column
                command.append("("
                               + delimitText(typeDefn.getName())
                               + ", "
                               + index
                               + ", '"
                               + typeDefn.getColumnNamesDatabase()[index]
                               + "', '"
                               + typeDefn.getColumnNamesUser()[index]
                               + "', "
                               + delimitText(typeDefn.getColumnToolTips()[index])
                               + ", '"
                               + typeDefn.getInputTypes()[index].getInputName()
                               + "', "
                               + typeDefn.isRowValueUnique()[index]
                               + ", "
                               + typeDefn.isRequired()[index]
                               + ", "
                               + typeDefn.isStructureAllowed()[index]
                               + ", "
                               + typeDefn.isPointerAllowed()[index]
                               + "), ");
            }
        }

        // Replace the trailing comma with a semicolon
        command = CcddUtilities.removeTrailer(command, ", ")
                               .append("; ")
                               .append(dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                                   InternalTable.TABLE_TYPES.getTableName()));

        return command.toString();
    }

    /**********************************************************************************************
     * Update the project description and data fields in the database
     *
     * @param description
     *            project database description
     *
     * @param fieldInformation
     *            project data field information; null to delete the fields for the specified
     *            project
     *
     * @param editorDialog
     *            reference to the project field editor dialog
     *********************************************************************************************/
    protected void modifyProjectFields(String description,
                                       List<FieldInformation> fieldInformation,
                                       CcddProjectFieldDialog editorDialog)
    {
        boolean errorFlag = false;

        try
        {
            // Update the project's description
            dbCommand.executeDbUpdate(dbControl.buildDatabaseCommentCommand(dbControl.getProjectName(),
                                                                            dbControl.getDatabaseAdmins(dbControl.getDatabaseName()),
                                                                            true,
                                                                            description),
                                      editorDialog);

            // Replace the project data fields with the ones provided
            dbCommand.executeDbUpdate(modifyFieldsCommand(CcddFieldHandler.getFieldProjectName(),
                                                          fieldInformation),
                                      editorDialog);
        }
        catch (SQLException se)
        {
            // Inform the user that updating the project data fields failed
            eventLog.logFailEvent(editorDialog,
                                  "Cannot modify project data field(s); cause '"
                                                + se.getMessage()
                                                + "'",
                                  "<html><b>Cannot modify project data field(s)");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, editorDialog);
            errorFlag = true;
        }

        // Check that no error occurred
        if (!errorFlag)
        {
            // Perform the project field modification clean-up steps
            editorDialog.doProjectFieldModificationComplete(errorFlag);
        }
    }

    /**********************************************************************************************
     * Build the command for updating the data field definitions table for the specified data
     * table, table type, group, or project
     *
     * @param ownerName
     *            name of the table, including the path if this table represents a structure, table
     *            type, group, or project to which the field(s) belong
     *
     * @param fieldInformation
     *            field information; null to delete the fields for the specified table/table
     *            type/group
     *
     * @return Command for updating the table/table type/group fields in the data field table
     *********************************************************************************************/
    private String modifyFieldsCommand(String ownerName, List<FieldInformation> fieldInformation)
    {
        // Build the command to delete the existing field definitions for the specified table/group
        StringBuilder command = new StringBuilder("DELETE FROM "
                                                  + InternalTable.FIELDS.getTableName()
                                                  + " WHERE "
                                                  + FieldsColumn.OWNER_NAME.getColumnName()
                                                  + " = '"
                                                  + ownerName
                                                  + "'; ");

        // Check if any fields exist
        if (fieldInformation != null && !fieldInformation.isEmpty())
        {
            // Append the command to insert the field definitions
            command.append("INSERT INTO " + InternalTable.FIELDS.getTableName() + " VALUES ");

            // Step through each of the table's field definitions
            for (FieldInformation fieldInfo : fieldInformation)
            {
                // Add the command to insert the field information. If the owner of the fields to
                // be copied is a table type and the target owner isn't a table type then force the
                // inheritance flag to true
                command.append("('"
                               + ownerName
                               + "', "
                               + delimitText(fieldInfo.getFieldName())
                               + ", "
                               + delimitText(fieldInfo.getDescription())
                               + ", "
                               + fieldInfo.getSize()
                               + ", "
                               + delimitText(fieldInfo.getInputType().getInputName())
                               + ", "
                               + String.valueOf(fieldInfo.isRequired())
                               + ", "
                               + delimitText(fieldInfo.getApplicabilityType().getApplicabilityName())
                               + ", "
                               + delimitText(fieldInfo.getValue())
                               + ", "
                               + String.valueOf(fieldInfo.isInherited()
                                                || (CcddFieldHandler.isTableTypeField(fieldInfo.getOwnerName())
                                                    && !CcddFieldHandler.isTableTypeField(ownerName)))
                               + "), ");
            }

            command = CcddUtilities.removeTrailer(command, ", ").append("; ");
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Change the name of a table type and update the existing tables of the specified type. This
     * command is executed in a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue to update. The GUI
     * menu commands, however, are disabled until the database command completes execution
     *
     * @param typeName
     *            current name of the table type
     *
     * @param newName
     *            new name for this table type
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *********************************************************************************************/
    protected void renameTableType(final String typeName,
                                   final String newName,
                                   final CcddTableTypeManagerDialog typeDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;
            String tableName;
            String[] tableNames = null;

            /**************************************************************************************
             * Rename table type command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Create the command to update the table definitions table
                    StringBuilder command = new StringBuilder(storeTableTypesInfoTableCommand());

                    // Get an array containing tables of the specified type
                    tableNames = queryTablesOfTypeList(typeName, typeDialog);

                    // For each table of the specified type
                    for (String table : tableNames)
                    {
                        tableName = table;

                        // Get the table's comment so that it can be rebuilt with the new type name
                        String[] comment = queryDataTableComment(table, typeDialog);

                        // Update the table comment to reflect the new type name
                        command.append(buildDataTableComment(comment[TableCommentIndex.NAME.ordinal()],
                                                             newName));
                    }

                    // Add the command to rename any default data fields for this table type
                    command.append("UPDATE "
                                   + InternalTable.FIELDS.getTableName()
                                   + " SET "
                                   + FieldsColumn.OWNER_NAME.getColumnName()
                                   + " = "
                                   + delimitText(CcddFieldHandler.getFieldTypeName(newName))
                                   + " WHERE "
                                   + FieldsColumn.OWNER_NAME.getColumnName()
                                   + " = "
                                   + delimitText(CcddFieldHandler.getFieldTypeName(typeName))
                                   + "; ");

                    // Execute the command to change the table's type name
                    dbCommand.executeDbCommand(command.toString(), typeDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table '"
                                                   + tableName
                                                   + "' type renamed to '"
                                                   + newName
                                                   + "'");
                }
                catch (SQLException se)
                {
                    // Inform the user that renaming the table type failed
                    eventLog.logFailEvent(typeDialog,
                                          "Cannot rename table type for table '"
                                                      + tableName
                                                      + "'; cause '"
                                                      + se.getMessage()
                                                      + "'",
                                          "<html><b>Cannot rename table type for table '</b>"
                                                             + tableName
                                                             + "<b>'");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, typeDialog);
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Rename table type command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Perform the type modification clean-up steps
                typeDialog.doTypeOperationComplete(errorFlag, null, tableNames);
            }
        });
    }

    /**********************************************************************************************
     * Create a copy of an existing table type. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param typeName
     *            name of the table type to copy
     *
     * @param copyName
     *            name for the copy of this table type
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *********************************************************************************************/
    protected void copyTableType(final String typeName,
                                 final String copyName,
                                 final CcddTableTypeManagerDialog typeDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            // List of field definitions
            List<String[]> fieldDefinitions = null;

            /**************************************************************************************
             * Copy table type command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Copy the target table type's data fields, set the owner of the copied fields
                    // to the new owner, and get the complete list of field definitions
                    fieldHandler.copyFields(CcddFieldHandler.getFieldTypeName(typeName),
                                            CcddFieldHandler.getFieldTypeName(copyName));
                    fieldDefinitions = fieldHandler.getFieldDefnsFromInfo();

                    // Create the command to rebuild the table types and fields tables
                    String command = storeTableTypesInfoTableCommand()
                                     + storeNonTableTypesInfoTableCommand(InternalTable.FIELDS,
                                                                          fieldDefinitions,
                                                                          null,
                                                                          typeDialog);

                    // Execute the command to change the table's type name
                    dbCommand.executeDbCommand(command, typeDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table type '"
                                                   + typeName
                                                   + "' copied to '"
                                                   + copyName
                                                   + "'");
                }
                catch (SQLException se)
                {
                    // Inform the user that copying the table type failed
                    eventLog.logFailEvent(typeDialog,
                                          "Cannot copy table type '"
                                                      + typeName
                                                      + "'; cause '"
                                                      + se.getMessage()
                                                      + "'",
                                          "<html><b>Cannot copy table type '</b>"
                                                             + typeName
                                                             + "<b>'");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, typeDialog);
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Copy table type command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred when copying the table type and if the type
                // represents a structure
                if (!errorFlag && tableTypeHandler.getTypeDefinition(typeName).isStructure())
                {
                    // Update the rate information, if applicable
                    rateHandler.setRateInformation();
                }

                typeDialog.doTypeOperationComplete(errorFlag, fieldDefinitions, null);
            }
        });
    }

    /**********************************************************************************************
     * Delete a table type and all tables of the deleted type. This command is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database command completes execution
     *
     * @param typeName
     *            type of table to delete
     *
     * @param isStructure
     *            true if the table type represents a structure
     *
     * @param isCommand
     *            true if the table type represents a command
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void deleteTableType(final String typeName,
                                   final boolean isStructure,
                                   final boolean isCommand,
                                   final CcddTableTypeManagerDialog typeDialog,
                                   final Component parent)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;
            boolean isStructure = false;
            String[] tableNames = null;
            String names = "";

            /**************************************************************************************
             * Table type deletion command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Get the reference to the type definition
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(typeName);

                    // Set the flag to indicate if the type represents a structure
                    boolean isStructure = typeDefn.isStructure();

                    // Delete the table type definition from the table type handler
                    tableTypeHandler.getTypeDefinitions().remove(typeDefn);

                    // Create the command to update the table definitions table
                    String command = storeTableTypesInfoTableCommand();

                    // Get an array containing tables of the specified type
                    tableNames = queryTablesOfTypeList(typeName, ccddMain.getMainFrame());

                    // Check if there are any associated tables to delete
                    if (tableNames.length != 0)
                    {
                        // Convert the array of tables names into a single string and shorten it if
                        // too long
                        names = CcddUtilities.convertArrayToStringTruncate(tableNames);

                        // Check if the user confirms deleting the affected table(s)
                        if (new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>Delete table(s) '</b>"
                                                                              + names
                                                                              + "<b>' of type '</b>"
                                                                              + typeName
                                                                              + "<b>'?<br><br><i>Warning: This action cannot be undone!",
                                                                      "Delete Table(s)",
                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Add the command to delete the tables and all their references in the
                            // internal tables
                            command += deleteTableCommand(tableNames, true) + " ";
                        }
                        // The user canceled the operation
                        else
                        {
                            // Set the error flag to restore the deleted type definition
                            errorFlag = true;
                        }
                    }

                    // Check if the user didn't cancel the operation
                    if (!errorFlag)
                    {
                        // Add the command to remove any default data fields for this table type
                        command += modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeName),
                                                       null);

                        // Step through the array of table names
                        for (String name : tableNames)
                        {
                            // Remove the table's data fields
                            command += modifyFieldsCommand(name, null);
                        }

                        // Modify the (shortened) names string for logging messages
                        names = " and table(s) '</b>" + names + "<b>'";

                        // Delete the table(s)
                        dbCommand.executeDbUpdate(command, parent);

                        // Execute the command to reset the rate for links that no longer contain
                        // any variables
                        dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);

                        // Check if the the deleted type represented a structure
                        if (isStructure)
                        {
                            // Update the list of root structure tables
                            rootStructures = getRootStructures(parent);

                            // Rebuild the variable paths and offsets lists
                            variableHandler.buildPathAndOffsetLists();
                        }
                        // Check if the the deleted type represented a command
                        else if (isCommand)
                        {
                            // Rebuild the command list
                            commandHandler.buildCommandList();
                        }

                        // Log that table deletion succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          "Table type '"
                                                       + typeName
                                                       + "'"
                                                       + CcddUtilities.removeHTMLTags(names)
                                                       + " deleted");
                    }
                }
                catch (SQLException se)
                {
                    // Inform the user that the table deletion failed
                    eventLog.logFailEvent(parent,
                                          "Cannot delete table type '"
                                                  + typeName
                                                  + "'"
                                                  + CcddUtilities.removeHTMLTags(names)
                                                  + "; cause '"
                                                  + se.getMessage()
                                                  + "'",
                                          "<html><b>Cannot delete table type '</b>"
                                                         + typeName
                                                         + "<b>'</b>"
                                                         + names
                                                         + "<b>'");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, parent);
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Delete table type command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred when deleting the table type
                if (!errorFlag)
                {
                    // Check if the deleted type represents a structure
                    if (isStructure)
                    {
                        // Update the database functions that collect structure table members and
                        // structure-defining column data
                        dbControl.createStructureColumnFunctions();
                    }

                    // Check if the number of rate columns changed due to the type deletion
                    if (rateHandler.setRateInformation())
                    {
                        // Store the rate parameters in the project database
                        storeRateParameters(ccddMain.getMainFrame());
                    }
                }

                // Perform any remaining steps, based on if deleting the type was successful
                typeDialog.doTypeOperationComplete(errorFlag, null, tableNames);
            }
        });
    }

    /**********************************************************************************************
     * Add, rename, or delete table type column names in the table definitions table and update the
     * existing tables of the specified type. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param tableType
     *            table type name to modify
     *
     * @param fieldInformation
     *            data field information list
     *
     * @param overwriteFields
     *            OverwriteFieldValueType: ALL to overwrite all field values, SAME to overwrite
     *            only those fields with a matching value, EMPTY to overwrite only fields with
     *            blank values, or NONE to not overwrite any field values
     *
     * @param typeAdditions
     *            list of new columns to add to the tables. Each list item is an array containing:
     *            [0] column name (user), [1] column input type
     *
     * @param typeModifications
     *            list of name changes of existing columns in the tables. Each list item is an
     *            array containing: [0] original column name (user), [1] new column name (user),
     *            [2] column input type
     *
     * @param typeDeletions
     *            list of columns to remove from the tables. Each list item is an array containing:
     *            [0] column name (user), [1] column input type
     *
     * @param columnOrderChange
     *            true if the table type's column order changed
     *
     * @param originalDefn
     *            reference to the table type definition prior to making any changes
     *
     * @param fieldAdditions
     *            list of new fields to add to tables of this type
     *
     * @param fieldModifications
     *            list of changes to existing fields in tables of this type
     *
     * @param fieldDeletions
     *            list of fields to remove from tables of this type
     *
     * @param editorDialog
     *            reference to the table type editor dialog
     *
     * @param editor
     *            reference to the table type editor where the change(s) occurred
     *********************************************************************************************/
    protected void modifyTableTypeInBackground(final String tableType,
                                               final List<FieldInformation> fieldInformation,
                                               final OverwriteFieldValueType overwriteFields,
                                               final List<String[]> typeAdditions,
                                               final List<String[]> typeModifications,
                                               final List<String[]> typeDeletions,
                                               final boolean columnOrderChange,
                                               final TypeDefinition originalDefn,
                                               final List<TableModification> fieldAdditions,
                                               final List<TableModification> fieldModifications,
                                               final List<TableModification> fieldDeletions,
                                               final CcddTableTypeEditorDialog editorDialog,
                                               final CcddTableTypeEditorHandler editor)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Modify table types command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                modifyTableType(tableType,
                                fieldInformation,
                                overwriteFields,
                                typeAdditions,
                                typeModifications,
                                typeDeletions,
                                columnOrderChange,
                                originalDefn,
                                fieldAdditions,
                                fieldModifications,
                                fieldDeletions,
                                editorDialog,
                                editor);
            }
        });
    }

    /**********************************************************************************************
     * Add, rename, or delete table type column names in the table definitions table and update the
     * existing tables of the specified type
     *
     * @param typeName
     *            table type name to modify
     *
     * @param fieldInformation
     *            data field information list
     *
     * @param overwriteFields
     *            OverwriteFieldValueType: ALL to overwrite all field values, SAME to overwrite
     *            only those fields with a matching value, EMPTY to overwrite only fields with
     *            blank values, or NONE to not overwrite any field values
     *
     * @param typeAdditions
     *            list of new columns to add to the tables. Each list item is an array containing:
     *            [0] column name (user), [1] column input type
     *
     * @param typeModifications
     *            list of name changes of existing columns in the tables. Each list item is an
     *            array containing: [0] original column name (user), [1] new column name (user),
     *            [2] column input type
     *
     * @param typeDeletions
     *            list of columns to remove from the tables. Each list item is an array containing:
     *            [0] column name (user), [1] column input type
     *
     * @param columnOrderChange
     *            true if the table type's column order changed
     *
     * @param originalDefn
     *            reference to the table type definition prior to making any changes; null if this
     *            is a new table type
     *
     * @param fieldAdditions
     *            list of new fields to add to tables of this type
     *
     * @param fieldModifications
     *            list of changes to existing fields in tables of this type
     *
     * @param fieldDeletions
     *            list of fields to remove from the tables of this type
     *
     * @param editorDialog
     *            reference to the table type editor dialog
     *
     * @param editor
     *            reference to the table type editor where the change(s) occurred
     *********************************************************************************************/
    protected void modifyTableType(String typeName,
                                   List<FieldInformation> fieldInformation,
                                   OverwriteFieldValueType overwriteFields,
                                   List<String[]> typeAdditions,
                                   List<String[]> typeModifications,
                                   List<String[]> typeDeletions,
                                   boolean columnOrderChange,
                                   TypeDefinition originalDefn,
                                   List<TableModification> fieldAdditions,
                                   List<TableModification> fieldModifications,
                                   List<TableModification> fieldDeletions,
                                   CcddTableTypeEditorDialog editorDialog,
                                   CcddTableTypeEditorHandler editor)
    {
        try
        {
            boolean errorFlag = false;
            String[] tableNames = null;
            String names = "";

            // Get the type definition based on the table type name
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(typeName);

            // Set the flags that indicates if the table type definition represents a structure
            // prior to and after making the updates
            boolean wasStructure = originalDefn != null
                                                        ? originalDefn.isStructure()
                                                        : false;
            boolean isStructure = typeDefn.isStructure();

            // Set the flags that indicates if the table type definition represents a command
            // prior to and after making the updates
            boolean wasCommand = originalDefn != null
                                                      ? originalDefn.isCommand()
                                                      : false;
            boolean isCommand = typeDefn.isCommand();

            // Create the command to update the table type definitions table
            StringBuilder command = new StringBuilder(storeTableTypesInfoTableCommand());

            // Check if this isn't a new table type
            if (originalDefn != null)
            {
                String fieldValue = null;
                String columnOrder = "";

                // Get an array containing all of the prototype tables of the specified type
                String[] protoTableNames = queryTablesOfTypeList(typeName, editorDialog);

                // Check if the column order changed or if any columns were added or deleted
                if (columnOrderChange || !typeAdditions.isEmpty() || !typeDeletions.isEmpty())
                {
                    // Step through each column in the table type
                    for (int index = 0; index < typeDefn.getColumnNamesDatabase().length; index++)
                    {
                        // Add the column index and a separator
                        columnOrder += index + ":";
                    }

                    // Remove the trailing separator
                    columnOrder = CcddUtilities.removeTrailer(columnOrder, ":");
                }

                // ////////////////////////////////////////////////////////////////////////////////
                // All prototype tables of the affected table type have the columns
                // added/renamed/deleted and the column order table updated
                // ////////////////////////////////////////////////////////////////////////////////
                // Step through each prototype table of the specified type
                for (String protoName : protoTableNames)
                {
                    // Get the database form of the table name
                    String dbTable = dbControl.getQuotedName(protoName);

                    // Step through each addition
                    for (String add[] : typeAdditions)
                    {
                        // Get the column name in database form
                        String dbColumn = tableTypeHandler.convertVisibleToDatabase(add[0],
                                                                                    add[1],
                                                                                    isStructure);

                        // Append the add command
                        command.append("ALTER TABLE "
                                       + dbTable
                                       + " ADD COLUMN "
                                       + dbColumn
                                       + " text DEFAULT ''; ");
                    }

                    // Step through each modification
                    for (String[] mod : typeModifications)
                    {
                        // Get the old and new column names in database form
                        String oldDbColumn = tableTypeHandler.convertVisibleToDatabase(mod[0],
                                                                                       mod[2],
                                                                                       isStructure);
                        String newDbColumn = tableTypeHandler.convertVisibleToDatabase(mod[1],
                                                                                       mod[3],
                                                                                       isStructure);

                        // Check if the database form of the name changed
                        if (!oldDbColumn.equals(newDbColumn))
                        {
                            // Append the modify command
                            command.append("ALTER TABLE "
                                           + dbTable
                                           + " RENAME COLUMN "
                                           + oldDbColumn
                                           + " TO "
                                           + newDbColumn
                                           + "; ");
                        }

                        // Check if the input type changed, but not to a text type (a text type
                        // accepts anything, so there's no need to adjust the field content)
                        if (!mod[2].equals(mod[3])
                            && inputTypeHandler.getInputTypeByName(mod[3]).getInputFormat() != InputTypeFormat.TEXT)
                        {
                            // Append the command to blank any cells containing a value that
                            // doesn't conform to the new input type format. The input type format
                            // must be modified to conform to a regular expressions acceptable to
                            // PostgreSQL (which is more constraining than Java's)
                            command.append("UPDATE "
                                           + dbTable
                                           + " SET "
                                           + newDbColumn
                                           + " = '' WHERE "
                                           + newDbColumn
                                           + " != '' AND "
                                           + newDbColumn
                                           + " !~ E'^"
                                           + inputTypeHandler.getInputTypeByName(mod[3])
                                                             .getInputMatch()
                                                             .replaceFirst("\\^", "")
                                                             .replaceFirst("\\$$", "")
                                                             .replaceAll("\\?\\?", "?")
                                                             .replaceAll("\\\\",
                                                                         "\\\\\\\\\\\\")
                                           + "$'; ");
                        }
                    }

                    // Step through each deletion
                    for (String[] del : typeDeletions)
                    {
                        // Get the column name in database form
                        String dbColumn = tableTypeHandler.convertVisibleToDatabase(del[0],
                                                                                    del[1],
                                                                                    isStructure);

                        // Append the delete command
                        command.append("ALTER TABLE "
                                       + dbTable
                                       + " DROP COLUMN "
                                       + dbColumn
                                       + "; ");
                    }

                    // Check if the column order changed
                    if (columnOrder.length() != 0)
                    {
                        // Replace the column order for all tables matching this table name. This
                        // resets the column order for all users
                        command.append("UPDATE "
                                       + InternalTable.ORDERS.getTableName()
                                       + " SET "
                                       + OrdersColumn.COLUMN_ORDER.getColumnName()
                                       + " = '"
                                       + columnOrder
                                       + "' WHERE "
                                       + OrdersColumn.TABLE_PATH.getColumnName()
                                       + " ~ E'^"
                                       + protoName
                                       + "(,|$)'; ");
                    }
                }

                // ////////////////////////////////////////////////////////////////////////////////
                // A modification or deletion indicates that a column name changed and/or that a
                // rate column's input type changed
                // ////////////////////////////////////////////////////////////////////////////////
                // Check if there are modifications or deletions, and if any tables of this type
                // exist
                if ((!typeModifications.isEmpty() || !typeDeletions.isEmpty())
                    && protoTableNames.length != 0)
                {
                    // ////////////////////////////////////////////////////////////////////////////
                    // Build the sub-command required to detect references to tables of the
                    // affected type in the custom values, links, and telemetry scheduler tables
                    // ////////////////////////////////////////////////////////////////////////////
                    StringBuilder valuesCmd = new StringBuilder("");
                    StringBuilder linksCmd = new StringBuilder("");
                    StringBuilder tlmSchCmd = new StringBuilder("");

                    // Step through each prototype table of the specified type
                    for (String protoName : protoTableNames)
                    {
                        // Build the table name comparison command for the custom values table
                        valuesCmd.append(ValuesColumn.TABLE_PATH.getColumnName()
                                         + " ~ E'[^,]+,"
                                         + protoName
                                         + "\\.[^,]+,[^,]+$' OR ");
                    }

                    // Remove the trailing 'OR' and prepend an 'AND' to complete the custom values
                    // command
                    valuesCmd = CcddUtilities.removeTrailer(valuesCmd, " OR ");
                    valuesCmd.insert(0, " AND (");
                    valuesCmd.append(")");

                    // Check if the table type represents a structure
                    if (isStructure && wasStructure)
                    {
                        // Step through each prototype table of the specified type
                        for (String protoName : protoTableNames)
                        {
                            // Build the table name comparison command for the links and telemetry
                            // scheduler tables
                            linksCmd.append(LinksColumn.MEMBER.getColumnName()
                                            + " ~ E'(?:"
                                            + protoName
                                            + ",|[^,]+,"
                                            + protoName
                                            + "\\.[^,]+,[^,]+$)' OR ");
                            tlmSchCmd.append(TlmSchedulerColumn.MEMBER.getColumnName()
                                             + " ~ E'(?:"
                                             + tlmSchSeparator
                                             + protoName
                                             + ",|[^,]+,"
                                             + protoName
                                             + "\\.[^,]+,[^,]+$)' OR ");
                        }

                        // Remove the trailing 'OR' and prepend an 'AND' to complete the links and
                        // telemetry scheduler table commands
                        linksCmd = CcddUtilities.removeTrailer(linksCmd, " OR ");
                        linksCmd.insert(0, " AND (");
                        linksCmd.append(")");
                        tlmSchCmd = CcddUtilities.removeTrailer(tlmSchCmd, " OR ");
                        tlmSchCmd.insert(0, " AND (");
                        tlmSchCmd.append(")");
                    }

                    // ////////////////////////////////////////////////////////////////////////////
                    // If the table type no longer represents a structure then all references in
                    // the links and telemetry scheduler tables to tables of the affected type are
                    // removed
                    // ////////////////////////////////////////////////////////////////////////////
                    // Check if the table type changed from representing a structure to no longer
                    // doing so
                    if (!isStructure && wasStructure)
                    {
                        boolean hasSharedRate = false;

                        // Step through each rate column in the table type definition
                        for (int column : originalDefn.getColumnIndicesByInputType(DefaultInputType.RATE))
                        {
                            // Get the rate column name (as seen by the user)
                            String rateName = originalDefn.getColumnNamesUser()[column];

                            // Check if this is the only table type using this rate column name
                            if (rateHandler.getRateInformationByRateName(rateName).getNumSharedTableTypes() == 1)
                            {
                                // Delete all entries in the links and telemetry scheduler tables
                                // that reference this rate name
                                command.append(deleteAllLinkAndTlmRateRefs(rateName));
                            }
                            // The rate is shared with another table type
                            else
                            {
                                // Set the flag indicating the table type has a shared rate
                                hasSharedRate = true;
                            }
                        }

                        // Check if the table type shares a rate column with another table type. If
                        // not then all references in the links and telemetry tables are eliminated
                        // by the command built above; otherwise, commands must be built to remove
                        // the specific table references in these internal tables
                        if (hasSharedRate)
                        {
                            // Remove all references to tables of the changed type in the links and
                            // telemetry scheduler tables
                            command.append(deleteLinkAndTlmRateRef(".+", linksCmd, tlmSchCmd));
                        }
                    }

                    // ////////////////////////////////////////////////////////////////////////////
                    // For a column name change/deletion (of any input type), references to the
                    // column name in the custom values table are updated/removed The modifications
                    // and deletions must then be applied to the links and telemetry scheduler
                    // tables, but only if the table type remains a structure type after the change
                    // (if the table type no longer represents a structure then the code above
                    // addresses the links and telemetry scheduler tables, and if the table type
                    // becomes a structure no changes to these internal tables is needed). If a
                    // rate name is changed and it's not used by another table type then the rate
                    // name references are changed to the new name in the internal tables.
                    // Otherwise, a change to or removal of the rate name results in the references
                    // in the internal tables being removed (either just the affected table
                    // references or all references - tables as well as the link/rate definition
                    // rows - depending on if the rate name was/is unique to this table type)
                    // ////////////////////////////////////////////////////////////////////////////
                    // Step through each modification
                    for (String[] mod : typeModifications)
                    {
                        // Check if the column name changed
                        if (!mod[0].equals(mod[1]))
                        {
                            // Append the modify command for the custom values table
                            command.append("UPDATE "
                                           + InternalTable.VALUES.getTableName()
                                           + " SET "
                                           + ValuesColumn.COLUMN_NAME.getColumnName()
                                           + " = '"
                                           + mod[1]
                                           + "' WHERE "
                                           + ValuesColumn.COLUMN_NAME.getColumnName()
                                           + " = '"
                                           + mod[0]
                                           + "'"
                                           + valuesCmd
                                           + "; ");
                        }

                        // Check if the table type represents a structure and the column input type
                        // was a rate
                        if (isStructure && wasStructure
                            && mod[2].equals(DefaultInputType.RATE.getInputName()))
                        {
                            // Check if the column changed from a rate column to not being a rate
                            // column
                            if (!mod[3].equals(DefaultInputType.RATE.getInputName()))
                            {
                                // Check if the rate name is used by another structure table type
                                if (rateHandler.getRateInformationByRateName(mod[0]).getNumSharedTableTypes() > 1)
                                {
                                    // Remove all references to tables of the changed type in the
                                    // links and telemetry scheduler tables
                                    command.append(deleteLinkAndTlmRateRef(mod[0],
                                                                           linksCmd,
                                                                           tlmSchCmd));
                                }
                                // The rate name is unique to this table type
                                else
                                {
                                    // Remove all references to the original rate column name in
                                    // the links and telemetry scheduler tables
                                    command.append(deleteAllLinkAndTlmRateRefs(mod[0]));
                                }
                            }
                            // Check if the rate column name changed
                            else if (!mod[0].equals(mod[1]))
                            {
                                // Check if the original rate name is used by another structure
                                // table type
                                if (rateHandler.getRateInformationByRateName(mod[0]).getNumSharedTableTypes() > 1)
                                {
                                    // Remove all references to tables of the changed type in the
                                    // links and telemetry scheduler tables
                                    command.append(deleteLinkAndTlmRateRef(mod[0],
                                                                           linksCmd,
                                                                           tlmSchCmd));
                                }
                                // Check if the new rate name isn't used by another structure table
                                // type (i.e., the rate name is unique to this table type)
                                else if (rateHandler.getRateInformationByRateName(mod[1]) == null)
                                {
                                    // Append the modify command for the links and telemetry
                                    // scheduler table
                                    command.append("UPDATE "
                                                   + InternalTable.LINKS.getTableName()
                                                   + " SET "
                                                   + LinksColumn.RATE_NAME.getColumnName()
                                                   + " = '"
                                                   + mod[1]
                                                   + "' WHERE "
                                                   + LinksColumn.RATE_NAME.getColumnName()
                                                   + " = '"
                                                   + mod[0]
                                                   + "'; UPDATE "
                                                   + InternalTable.TLM_SCHEDULER.getTableName()
                                                   + " SET "
                                                   + TlmSchedulerColumn.RATE_NAME.getColumnName()
                                                   + " = '"
                                                   + mod[1]
                                                   + "' WHERE "
                                                   + TlmSchedulerColumn.RATE_NAME.getColumnName()
                                                   + " = '"
                                                   + mod[0]
                                                   + "'; ");
                                }
                                // The new rate name is already in use
                                else
                                {
                                    // Remove all references to the original rate column name in
                                    // the links and telemetry scheduler tables
                                    command.append(deleteAllLinkAndTlmRateRefs(mod[0]));
                                }
                            }
                        }
                    }

                    // Step through each deletion
                    for (String[] del : typeDeletions)
                    {
                        // Append the delete command for the custom values table
                        command.append("DELETE FROM "
                                       + InternalTable.VALUES.getTableName()
                                       + " WHERE "
                                       + ValuesColumn.COLUMN_NAME.getColumnName()
                                       + " = '"
                                       + del[0]
                                       + "'"
                                       + valuesCmd
                                       + "; ");

                        // Check if the table type represents a structure and a rate column is
                        // deleted
                        if (isStructure && wasStructure
                            && del[1].equals(DefaultInputType.RATE.getInputName()))
                        {
                            // Check if the rate name is used by another structure table type
                            if (rateHandler.getRateInformationByRateName(del[0]).getNumSharedTableTypes() > 1)
                            {
                                // Remove all references to tables of the changed type in the links
                                // and telemetry scheduler tables
                                command.append(deleteLinkAndTlmRateRef(del[0],
                                                                       linksCmd,
                                                                       tlmSchCmd));
                            }
                            // The rate name is unique to this table type
                            else
                            {
                                // Remove all references to the original rate column name in the
                                // links and telemetry/scheduler tables
                                command.append(deleteAllLinkAndTlmRateRefs(del[0]));
                            }
                        }
                    }
                }

                // Get the list of names of all tables of the specified type
                List<String> tableNamesList = getAllTablesOfType(typeName,
                                                                 protoTableNames,
                                                                 editorDialog);

                // Step through each table of the specified type
                for (String tableName : tableNamesList)
                {
                    // Set the flag to indicate if the table is a root structure
                    boolean isRootStruct = isStructure && rootStructures.contains(tableName);

                    // Step through each field addition
                    for (TableModification add : fieldAdditions)
                    {
                        // Check if the table isn't a child structure (all fields are stored for
                        // prototypes, even if not displayed) or the field is applicable to this
                        // child table
                        if (!tableName.contains(".")
                            || fieldHandler.isFieldApplicable(tableName,
                                                              add.getRowData()[FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString(),
                                                              isRootStruct))
                        {
                            // Check if the table already has a field by this name and, if so,
                            // alter the existing field's name in order to prevent a duplicate
                            CcddFieldHandler.alterFieldName(fieldHandler.getFieldInformation(),
                                                            tableName,
                                                            add.getRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString());

                            // Add the data field to the table and set the flag indicating a change
                            // has been made
                            fieldHandler.getFieldInformation().add(new FieldInformation(tableName,
                                                                                        add.getRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString(),
                                                                                        add.getRowData()[FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                                                        inputTypeHandler.getInputTypeByName(add.getRowData()[FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                                                                                        Integer.parseInt(add.getRowData()[FieldEditorColumnInfo.CHAR_SIZE.ordinal()].toString()),
                                                                                        Boolean.parseBoolean(add.getRowData()[FieldEditorColumnInfo.REQUIRED.ordinal()].toString()),
                                                                                        ApplicabilityType.getApplicabilityByName(add.getRowData()[FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()),
                                                                                        add.getRowData()[FieldEditorColumnInfo.VALUE.ordinal()].toString(),
                                                                                        true,
                                                                                        null,
                                                                                        -1));
                        }
                    }

                    // Step through each field modification
                    for (TableModification mod : fieldModifications)
                    {
                        // Get the reference to the modified field
                        FieldInformation modifiedField = fieldHandler.getFieldInformationByName(tableName,
                                                                                                mod.getOriginalRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString());

                        // Check if the field exists
                        if (modifiedField != null)
                        {
                            // Check if the field's name changed
                            if (!mod.getOriginalRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString().equals(mod.getRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString()))
                            {
                                // Check if the table already has a field by the new name and, if
                                // so, alter the existing field's name in order to prevent a
                                // duplicate
                                CcddFieldHandler.alterFieldName(fieldHandler.getFieldInformation(),
                                                                tableName,
                                                                mod.getRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString());
                            }

                            // Set the table field's value based on the overwrite type
                            switch (overwriteFields)
                            {
                                case ALL:
                                    // Overwrite the table field's value with the inheritable
                                    // field's value
                                    fieldValue = mod.getRowData()[FieldEditorColumnInfo.VALUE.ordinal()].toString();
                                    break;

                                case SAME:
                                    // Only overwrite the table field's value if it matches the
                                    // inheritable field's original value
                                    fieldValue = modifiedField.getValue().equals(mod.getOriginalRowData()[FieldEditorColumnInfo.VALUE.ordinal()].toString())
                                                                                                                                                             ? mod.getRowData()[FieldEditorColumnInfo.VALUE.ordinal()].toString()
                                                                                                                                                             : modifiedField.getValue();
                                    break;

                                case EMPTY:
                                    // Only overwrite the table field's value if it's blank
                                    fieldValue = modifiedField.getValue().isEmpty()
                                                                                    ? mod.getRowData()[FieldEditorColumnInfo.VALUE.ordinal()].toString()
                                                                                    : modifiedField.getValue();
                                    break;

                                case NONE:
                                    // Keep the table field's current value
                                    fieldValue = modifiedField.getValue();
                                    break;
                            }

                            // Replace the existing field with the modified one
                            fieldHandler.getFieldInformation().set(fieldHandler.getFieldInformation().indexOf(modifiedField),
                                                                   new FieldInformation(tableName,
                                                                                        mod.getRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString(),
                                                                                        mod.getRowData()[FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                                                        inputTypeHandler.getInputTypeByName(mod.getRowData()[FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                                                                                        Integer.parseInt(mod.getRowData()[FieldEditorColumnInfo.CHAR_SIZE.ordinal()].toString()),
                                                                                        Boolean.parseBoolean(mod.getRowData()[FieldEditorColumnInfo.REQUIRED.ordinal()].toString()),
                                                                                        ApplicabilityType.getApplicabilityByName(mod.getRowData()[FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()),
                                                                                        fieldValue,
                                                                                        true,
                                                                                        modifiedField.getInputFld(),
                                                                                        -1));
                        }
                    }

                    // Step through each field deletion
                    for (TableModification del : fieldDeletions)
                    {
                        // Get the reference to the deleted field
                        FieldInformation deletedField = fieldHandler.getFieldInformationByName(tableName,
                                                                                               del.getOriginalRowData()[FieldEditorColumnInfo.NAME.ordinal()].toString());

                        // Check if the field exists
                        if (deletedField != null)
                        {
                            // Delete the field
                            fieldHandler.getFieldInformation().remove(deletedField);
                        }
                    }

                    // Check if any fields were added, modified, or deleted
                    if (!fieldAdditions.isEmpty()
                        || !fieldModifications.isEmpty()
                        || !fieldDeletions.isEmpty())
                    {
                        // Create the command to modify the table's data field entries
                        command.append(modifyFieldsCommand(tableName,
                                                           fieldHandler.getFieldInformationByOwner(tableName)));
                    }
                }

                // Convert the list of modified tables names to an array
                tableNames = tableNamesList.toArray(new String[0]);

                // Check if any table of this type exists
                if (tableNames.length != 0)
                {
                    // Convert the array of tables names into a single string and shorten it if too
                    // long
                    names = " and table(s) '</b>"
                            + CcddUtilities.convertArrayToStringTruncate(tableNames)
                            + "<b>'";
                }

                // Build the command to update the data fields table
                command.append(modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeName),
                                                   fieldInformation));
            }

            try
            {
                // Execute the command to change the table type and any table's of this type
                dbCommand.executeDbCommand(command.toString(), editorDialog);

                // Check if the type changed to or from being a structure
                if (isStructure != wasStructure)
                {
                    // Update the list of root structure tables
                    rootStructures = getRootStructures(editorDialog);

                    // Rebuild the variable paths and offsets lists
                    variableHandler.buildPathAndOffsetLists();
                }
                // Check if the type changed to or from being a command
                else if (isCommand != wasCommand)
                {
                    // Rebuild the command list
                    commandHandler.buildCommandList();
                }

                // Log that updating the table type succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  "Table type '"
                                               + typeName
                                               + "'"
                                               + CcddUtilities.removeHTMLTags(names)
                                               + " updated");
            }
            catch (SQLException se)
            {
                // Inform the user that updating the tables failed
                eventLog.logFailEvent(editorDialog,
                                      "Cannot update table type '"
                                                    + typeName
                                                    + "'"
                                                    + CcddUtilities.removeHTMLTags(names)
                                                    + "; cause '"
                                                    + se.getMessage()
                                                    + "'",
                                      "<html><b>Cannot update table type '</b>"
                                                           + typeName
                                                           + "<b>'"
                                                           + names);
                errorFlag = true;
            }
            catch (Exception e)
            {
                // Display a dialog providing details on the unanticipated error
                CcddUtilities.displayException(e, editorDialog);
                errorFlag = true;
            }

            // Check that no errors occurred and the table type represents or represented a
            // structure
            if (!errorFlag && (isStructure || wasStructure))
            {
                // Step through each column addition
                for (String add[] : typeAdditions)
                {
                    // Check if the column is a rate column
                    if (add[1].equals(DefaultInputType.RATE.getInputName()))
                    {
                        // Add the rate column to the rate information
                        rateHandler.addRateInformation(add[0]);
                    }
                }

                // Step through each column modification
                for (String[] mod : typeModifications)
                {
                    // Check if the column changed from a rate column to not being a rate column
                    if (mod[2].equals(DefaultInputType.RATE.getInputName())
                        && !mod[3].equals(DefaultInputType.RATE.getInputName()))
                    {
                        // Delete the rate column from the rate information
                        rateHandler.deleteRateInformation(mod[2]);
                    }
                    // Check if the column changed from not being a rate column to a rate column
                    else if (!mod[2].equals(DefaultInputType.RATE.getInputName())
                             && mod[3].equals(DefaultInputType.RATE.getInputName()))
                    {
                        // Add the rate column to the rate information
                        rateHandler.addRateInformation(mod[3]);
                    }
                    // Check if the column is (and was) a rate column (i.e., the rate column name
                    // changed)
                    else if (mod[3].equals(DefaultInputType.RATE.getInputName()))
                    {
                        // Rename (or add if the rate column is shared with another table type) the
                        // rate column in the rate information
                        rateHandler.renameRateInformation(mod[0], mod[1]);
                    }
                }

                // Step through each column deletion
                for (String[] del : typeDeletions)
                {
                    // Check if the column is a rate column
                    if (del[1].equals(DefaultInputType.RATE.getInputName()))
                    {
                        // Delete the rate column from the rate information
                        rateHandler.deleteRateInformation(del[0]);
                    }
                }

                // Update the rate column information and store it in the project database
                rateHandler.setRateInformation();
                storeRateParameters(editorDialog);

                // Update the database functions that collect structure table members and
                // structure-defining column data
                dbControl.createStructureColumnFunctions();
            }

            // Perform the type modification clean-up steps
            editorDialog.doTypeModificationComplete(errorFlag, editor, tableNames);
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, editorDialog);
        }
    }

    /**********************************************************************************************
     * Close the table editors for the specified deleted tables
     *
     * @param tableNames
     *            list containing arrays of information on the table name(s) to close. For a
     *            prototype table each array in the list contains the prototype name and a null. If
     *            the deletion is due to a change in a variable's structure data type then the
     *            array contains the immediate parent table name and the original data type (i.e.,
     *            structure table name) of the variable
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void closeDeletedTableEditors(List<String[]> tableNames, Component parent)
    {
        // Check that a table was deleted and if any editor windows are open
        if (tableNames != null
            && !tableNames.isEmpty()
            && !ccddMain.getTableEditorDialogs().isEmpty())
        {
            String pattern;

            // Step through each open editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Create storage for the name(s) of the table(s) to remove
                List<String> removeNames = new ArrayList<String>();

                // Step through each individual table editor in the editor dialog
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Step through the list of deleted tables
                    for (String[] name : tableNames)
                    {
                        // Check if only the prototype name is provided. This is the case when a
                        // prototype table is deleted and the editors for all tables descending
                        // from this prototype must be closed
                        if (name[1] == null)
                        {
                            // Create the table data type pattern to match table editors for tables
                            // with a data type path in the format prototypeName[,...] or
                            // [__,]parent[.__],prototypeName.__[,...]
                            pattern = "^([^,]*,)*" + Pattern.quote(name[0]) + "($|[,\\.].*)";
                        }
                        // The parent name and data type are provided. This is the case when a
                        // variable's data type is altered and all child tables descending from
                        // this variable must be closed
                        else
                        {
                            // Create the table data type pattern to match table editors for tables
                            // with a data type path in the format
                            // [__,]parent[.__],dataType.__[,...] if the variable name isn't
                            // specified, or [__,]parent[.__],dataType.varName[,...] is a variable
                            // name is provided
                            pattern = "^([^,]*,)*"
                                      + Pattern.quote(name[0])
                                      + "(\\.[^,]*,|,)"
                                      + Pattern.quote(name[1]);
                            // Check if the data type and variable name are provided
                            if (name[1].contains("."))
                            {
                                pattern += "(?:,.+|$)";
                            }
                            // Only the data type is provided
                            else
                            {
                                pattern += "\\..+";
                            }
                        }

                        // Check if table's data path matches the pattern for those tables that
                        // must have their editors closed
                        if (editor.getTableInformation().getTablePath().matches(pattern))
                        {
                            // Add the table name to the list of tables to remove. The table editor
                            // can't be closed in this loop since it's iterating over the list of
                            // editors
                            removeNames.add(editor.getOwnerName());
                        }
                    }
                }

                // Step through the list of the names of the tables to remove
                for (String name : removeNames)
                {
                    // Close the table's editor
                    editorDialog.closeTableEditor(name);
                }
            }
        }

        // Update the tables with data type columns
        updateDataTypeColumns(parent);
    }

    /**********************************************************************************************
     * Update the data type columns in the open table editors
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void updateDataTypeColumns(Component parent)
    {
        // Check if any editor dialogs are open
        if (!ccddMain.getTableEditorDialogs().isEmpty())
        {
            // Build the structure table array and table tree
            String[] allStructureTables = getPrototypeTablesOfType(TYPE_STRUCTURE);
            CcddTableTreeHandler newTableTree = new CcddTableTreeHandler(ccddMain,
                                                                         TableTreeType.INSTANCE_TABLES,
                                                                         parent);

            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Step through each individual editor
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Update the data type combo box list if the table contains one
                    editor.setUpDataTypeColumns(allStructureTables, newTableTree);

                    // Update the variable path column, if present, with the data type changes
                    editor.updateVariablePaths();

                    // Force the table to repaint to update the highlighting of the changed data
                    // types
                    editor.getTable().repaint();
                }
            }
        }
    }

    /**********************************************************************************************
     * Update the input type columns in the open table editors
     *
     * @param inputTypeNames
     *            list of the input type names, before and after the changes; null if none of the
     *            input type names changed
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void updateInputTypeColumns(List<String[]> inputTypeNames, Component parent)
    {
        // Step through each open table editor dialog
        for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
        {
            // Step through each individual editor
            for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
            {
                // Update the table editor for the input type change
                editor.updateForInputTypeChange(inputTypeNames);

                // Force the table to repaint to update the highlighting of the changed input types
                editor.getTable().repaint();
            }
        }

        // Check if the table type editor dialog is open
        if (ccddMain.getTableTypeEditor() != null && ccddMain.getTableTypeEditor().isShowing())
        {
            // Step through each table type editor
            for (CcddTableTypeEditorHandler editor : ccddMain.getTableTypeEditor().getTypeEditors())
            {
                // Update the table type editor for the input type change
                editor.updateForInputTypeChange(inputTypeNames);
            }
        }
    }

    /**********************************************************************************************
     * Modify table data field values in the data fields table. This command is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database command completes execution
     *
     * @param modifications
     *            list of data field value modifications
     *
     * @param deletions
     *            list of data field value deletions
     *
     * @param editorWindow
     *            reference to the data field editor
     *********************************************************************************************/
    protected void modifyDataFieldValues(final List<String[]> modifications,
                                         final List<String[]> deletions,
                                         final CcddFieldTableEditorDialog editorWindow)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, editorWindow, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /**************************************************************************************
             * Modify table types command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    StringBuilder command = new StringBuilder("");

                    // Step through each modification
                    for (String[] mod : modifications)
                    {
                        // Build the command to update the data field value in the data fields
                        // table
                        command.append("UPDATE "
                                       + InternalTable.FIELDS.getTableName()
                                       + " SET "
                                       + FieldsColumn.FIELD_VALUE.getColumnName()
                                       + " = "
                                       + delimitText(mod[2])
                                       + " WHERE "
                                       + FieldsColumn.OWNER_NAME.getColumnName()
                                       + " = '"
                                       + mod[0]
                                       + "' AND "
                                       + FieldsColumn.FIELD_NAME.getColumnName()
                                       + " = '"
                                       + mod[1]
                                       + "'; ");
                    }

                    // Step through each deletion
                    for (String[] del : deletions)
                    {
                        // Build the command to delete the data field from the data fields table
                        command.append("DELETE FROM "
                                       + InternalTable.FIELDS.getTableName()
                                       + " WHERE "
                                       + FieldsColumn.OWNER_NAME.getColumnName()
                                       + " = '"
                                       + del[0]
                                       + "' AND "
                                       + FieldsColumn.FIELD_NAME.getColumnName()
                                       + " = '"
                                       + del[1]
                                       + "'; ");
                    }

                    // Execute the command to change the data fields
                    dbCommand.executeDbCommand(command.toString(), editorWindow);

                    // Log that updating the data fields succeeded
                    eventLog.logEvent(SUCCESS_MSG, "Table data fields updated");
                }
                catch (SQLException se)
                {
                    // Inform the user that updating the data fields failed
                    eventLog.logFailEvent(editorWindow,
                                          "Cannot update data fields; cause '"
                                                        + se.getMessage()
                                                        + "'",
                                          "<html><b>Cannot update data fields");
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Modify data fields command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                editorWindow.doDataFieldUpdatesComplete(errorFlag);
            }
        });
    }

    /**********************************************************************************************
     * Modify all tables affected by changes to the user-defined data type names, or macro names
     * and/or macro values. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param modifications
     *            list of data type (macro) definition modifications
     *
     * @param updates
     *            list of string arrays reflecting the content of the data types (macros) after
     *            being changed in the data type (macro) editor
     *
     * @param dialog
     *            reference to the data type or macro editor dialog
     *********************************************************************************************/
    protected void modifyTablesPerDataTypeOrMacroChangesInBackground(final List<TableModification> modifications,
                                                                     final List<String[]> updates,
                                                                     final CcddDialogHandler dialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /**************************************************************************************
             * Modify data types (macros)
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Modify the data type (macro) references in the tables
                errorFlag = modifyTablesPerDataTypeOrMacroChanges(modifications,
                                                                  updates,
                                                                  dialog);
            }

            /**************************************************************************************
             * Modify data types or macros complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if this is a data type change
                if (dialog instanceof CcddDataTypeEditorDialog)
                {
                    ((CcddDataTypeEditorDialog) dialog).doDataTypeUpdatesComplete(errorFlag);
                }
                // This is a macro change
                else
                {
                    ((CcddMacroEditorDialog) dialog).doMacroUpdatesComplete(errorFlag);
                }

                // Rebuild the variable paths and offsets
                variableHandler.buildPathAndOffsetLists();
            }
        });
    }

    /**********************************************************************************************
     * Modify all tables affected by changes to the user-defined data type names, or macro names
     * and/or macro values
     *
     * @param modifications
     *            list of data type (macro) definition modifications
     *
     * @param updates
     *            list of string arrays reflecting the content of the data types (macros) after
     *            being changed in the data type (macro) editor
     *
     * @param dialog
     *            reference to the data type or macro editor dialog
     *
     * @return true if an error occurs while updating the data types (macros)
     *********************************************************************************************/
    protected boolean modifyTablesPerDataTypeOrMacroChanges(final List<TableModification> modifications,
                                                            final List<String[]> updates,
                                                            final Component dialog)
    {
        boolean errorFlag = false;
        TypeDefinition typeDefn = null;
        final CcddDataTypeHandler newDataTypeHandler;
        final CcddMacroHandler newMacroHandler;
        String changeName;

        // Set to true if the change is to a data type, and false if the change is to a macro
        boolean isDataType = dialog instanceof CcddDataTypeEditorDialog;

        // Check if this is a data type change
        if (isDataType)
        {
            // Create new data type, macro, and variable size handlers using the updates from the
            // data type editor
            newDataTypeHandler = new CcddDataTypeHandler(updates);
            newMacroHandler = new CcddMacroHandler(ccddMain,
                                                   ccddMain.getMacroHandler().getMacroData());
            changeName = "Data types";
        }
        // This is a macro change
        else
        {
            // Create new macro and variable size handlers using the updates from the macro editor
            newDataTypeHandler = dataTypeHandler;
            newMacroHandler = new CcddMacroHandler(ccddMain, updates);
            changeName = "Macros";
        }

        // Create a variable size handler accounting for the updates, then build the variable paths
        // and offsets lists
        final CcddVariableHandler newVariableHandler = new CcddVariableHandler(ccddMain,
                                                                               newDataTypeHandler,
                                                                               newMacroHandler);
        newMacroHandler.setHandlers(newVariableHandler, newDataTypeHandler);
        newVariableHandler.buildPathAndOffsetLists();

        /******************************************************************************************
         * Class for table information for those tables modified due to changes in a data type
         * (macro) definition
         *****************************************************************************************/
        class ModifiedTable
        {
            private final TableInformation tableInformation;
            private final CcddTableEditorHandler editor;

            /**************************************************************************************
             * Class constructor for table information for those tables modified due to changes in
             * a data type (macro) definition
             *
             * @param tablePath
             *            table path (if applicable) and name
             *************************************************************************************/
            private ModifiedTable(String tablePath)
            {
                // Load the table's information from the project database
                tableInformation = loadTableData(tablePath, false, true, dialog);

                // Create a table editor handler using the updated data types and/or macros, but
                // without displaying the editor itself
                editor = new CcddTableEditorHandler(ccddMain,
                                                    tableInformation,
                                                    newDataTypeHandler,
                                                    newMacroHandler,
                                                    dialog);
            }

            /**************************************************************************************
             * Get the reference to the table information
             *
             * @return Reference to the table information
             *************************************************************************************/
            protected TableInformation getTableInformation()
            {
                return tableInformation;
            }

            /**************************************************************************************
             * Get the reference to the table editor
             *
             * @return Reference to the table editor
             *************************************************************************************/
            protected CcddTableEditorHandler getEditor()
            {
                return editor;
            }
        }

        List<ModifiedTable> modifiedTables = new ArrayList<ModifiedTable>();

        // Flag that indicates that only a data type (macro) name has been altered, or a data type
        // size where the new size is not larger than the old size. If this remains true for all
        // data type (macro) updates then the project database internal table update process is
        // streamlined, making it much faster in cases where the internal tables reference a large
        // number of variables and tables
        boolean nameChangeOnly = true;

        // Storage for the table's data. This is the table data as it exists in the project
        // database
        List<Object[]> tableData;

        // Step through each modification
        for (TableModification mod : modifications)
        {
            String oldName;
            String newName;
            String oldNameDelim = null;
            String newNameDelim = null;
            String[] references;

            // Check if this is a data type change
            if (isDataType)
            {
                // Get the original and updated user-defined data type names
                oldName = CcddDataTypeHandler.getDataTypeName(mod.getOriginalRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                              mod.getOriginalRowData()[DataTypesColumn.C_NAME.ordinal()].toString());
                newName = CcddDataTypeHandler.getDataTypeName(mod.getRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                              mod.getRowData()[DataTypesColumn.C_NAME.ordinal()].toString());

                // Get the references to the updated data type
                references = dataTypeHandler.searchDataTypeReferences(oldName, dialog);

                // Check if only a data type name has been changed thus far (or if this is the
                // initial pass)
                if (nameChangeOnly)
                {
                    // Set to false if the size increased or the base type changed; keep equal to
                    // true if only the data type name has changed and the size is no larger
                    nameChangeOnly = Integer.valueOf(mod.getOriginalRowData()[DataTypesColumn.SIZE.ordinal()].toString()) >= Integer.valueOf(mod.getRowData()[DataTypesColumn.SIZE.ordinal()].toString())
                                     && mod.getOriginalRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString().equals(mod.getRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString());
                }

                // Replace all instances 'sizeof(oldName)' with 'sizeof(newName)' in the macros
                newMacroHandler.replaceDataTypeReferences(oldName, newName);

                // Reset the stored expanded values
                newMacroHandler.clearStoredValues();
            }
            // This is a macro change
            else
            {
                // Get the original and updated user-defined macro names
                oldName = mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString();
                newName = mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString();

                // Get the original and updated macro names with the macro delimiters
                oldNameDelim = CcddMacroHandler.getFullMacroName(mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());
                newNameDelim = CcddMacroHandler.getFullMacroName(mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());

                // Get the references to the updated macro
                references = macroHandler.getMacroReferences(oldName, dialog).getReferences();

                // Check if only a macro name has been changed thus far (or if this is the initial
                // pass)
                if (nameChangeOnly)
                {
                    // Set to false if the macro value changes; keep equal to true if only the
                    // macro name has changed
                    nameChangeOnly = macroHandler.getMacroExpansion(mod.getOriginalRowData()[MacrosColumn.VALUE.ordinal()].toString())
                                                 .equals(newMacroHandler.getMacroExpansion(mod.getRowData()[MacrosColumn.VALUE.ordinal()].toString()));
                }
            }

            // Step through each table/column containing the modification
            for (String ref : references)
            {
                String tableName = null;
                String changeColumn = null;
                String matchColumn = null;
                ModifiedTable modifiedTable = null;

                // Split the reference into table name, column name, table type, and context
                String[] tblColDescAndCntxt = ref.split(TABLE_DESCRIPTION_SEPARATOR, 4);

                // Create a reference to the search result's database table name and row data to
                // shorten comparisons below
                String refTableName = tblColDescAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()];
                String[] refContext = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                // Set to true if the referenced table is a prototype table and false if the
                // reference is to the internal custom values table
                boolean isPrototype = !refTableName.startsWith(INTERNAL_TABLE_PREFIX);

                // Check if the referenced table is a prototype table
                if (isPrototype)
                {
                    // Set the viewable table name (with capitalization intact) and get the column
                    // name containing the data type (macro) reference. Use the primary key as the
                    // matching column criteria
                    tableName = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].split(",", 2)[0];
                    changeColumn = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];
                    matchColumn = refContext[DefaultColumn.PRIMARY_KEY.ordinal()];
                }
                // The reference is in the custom values table. Check if this is a macro change
                else if (!isDataType)
                {
                    // Get the table name from the variable path in the custom values table and get
                    // the column name containing the macro reference. Use the variable name as the
                    // matching column criteria
                    tableName = refContext[ValuesColumn.TABLE_PATH.ordinal()].replaceAll("(\"|\\s|,[^\\.]*\\.[^,]*$)", "");
                    changeColumn = refContext[ValuesColumn.COLUMN_NAME.ordinal()];
                    matchColumn = refContext[ValuesColumn.TABLE_PATH.ordinal()].replaceAll("(.*\\.|\")", "");
                }
                // This is a data type change and the reference is in the custom values table
                else
                {
                    // Skip this reference since data type changes are automatically propagated to
                    // the internal tables via the data table modification process
                    continue;
                }

                // Step through each table already loaded for modifications
                for (ModifiedTable modTbl : modifiedTables)
                {
                    // Check if the table has already been loaded
                    if (modTbl.getTableInformation().getProtoVariableName().equals(tableName))
                    {
                        // Store the reference to the modified table and stop searching
                        modifiedTable = modTbl;
                        break;
                    }
                }

                // Check if the table isn't already loaded
                if (modifiedTable == null)
                {
                    // Load the table and add it to the list
                    modifiedTable = new ModifiedTable(tableName);
                    modifiedTables.add(modifiedTable);

                    // Check if the table arrays aren't expanded
                    if (!modifiedTable.getEditor().isExpanded())
                    {
                        // Expand the table arrays
                        modifiedTable.getEditor().showHideArrayMembers();
                    }
                }

                // Get the reference to the table to shorten subsequent calls
                CcddJTableHandler table = modifiedTable.getEditor().getTable();

                // Use the table's type to get the index for the table column containing the data
                // type (macro) reference
                typeDefn = modifiedTable.getEditor().getTableTypeDefinition();
                int changeColumnIndex = isPrototype
                                                    ? typeDefn.getColumnIndexByDbName(changeColumn)
                                                    : typeDefn.getColumnIndexByUserName(changeColumn);

                String macroValue = null;

                // Check is a change was made to a macro
                if (!isDataType)
                {
                    // Get the original value of the macro
                    macroValue = macroHandler.getMacroValue(oldName);
                }

                // Set the flags that indicate if a name or value changed
                boolean isNameChange = !oldName.equals(newName);
                boolean isValueChange = (isDataType
                                         && (dataTypeHandler.getSizeInBytes(oldName) != newDataTypeHandler.getSizeInBytes(newName)
                                             || !dataTypeHandler.getBaseDataType(oldName).equals(newDataTypeHandler.getBaseDataType(newName))))
                                        || (!isDataType
                                            && macroValue != null
                                            && !macroValue.equals(newMacroHandler.getMacroValue(newName)));

                // Check if the data type or macro name changed, or the data type size or base
                // type, or macro value changed
                if (isNameChange || isValueChange)
                {
                    // Get the table's data (again if a name change occurred since changes were
                    // made)
                    tableData = table.getTableDataList(false);

                    // Step through each row
                    for (int row = 0; row < tableData.size(); row++)
                    {
                        // Check if this is the row in the table data with the data type/macro
                        // reference. If not a prototype use the table's type to get the column
                        // index for the variable name
                        if (isPrototype
                                        ? matchColumn.equals(tableData.get(row)[DefaultColumn.PRIMARY_KEY.ordinal()])
                                        : matchColumn.equals(tableData.get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)].toString()))
                        {
                            // Step through each column in the row, skipping the primary key and
                            // row index columns
                            for (int column = NUM_HIDDEN_COLUMNS; column < tableData.get(row).length; column++)
                            {
                                // Check if this is the column that contains a data type or macro
                                if (column == changeColumnIndex)
                                {
                                    // Check if the cell value is editable
                                    if (table.isCellEditable(table.convertRowIndexToView(row),
                                                             table.convertColumnIndexToView(column)))
                                    {
                                        // Get the contents of the cell containing the data type or
                                        // macro reference
                                        String oldValue = tableData.get(row)[column].toString();
                                        String newValue = oldValue;

                                        // Check if the data type or macro name changed
                                        if (isNameChange)
                                        {
                                            // Check if this is a data type change
                                            if (isDataType)
                                            {
                                                // Check if the cell doesn't contain only the data
                                                // type name
                                                if (!oldValue.equals(oldName))
                                                {
                                                    // Check if the cell doesn't contain a sizeof()
                                                    // call for the data type
                                                    if (!CcddVariableHandler.hasSizeof(oldValue,
                                                                                       oldName,
                                                                                       macroHandler))
                                                    {
                                                        // Skip this reference. The data type match
                                                        // is coincidental
                                                        continue;
                                                    }

                                                    // Continue to step through the string,
                                                    // replacing each sizeof() instance
                                                    while (CcddVariableHandler.hasSizeof(newValue,
                                                                                         oldName,
                                                                                         newMacroHandler))
                                                    {
                                                        // Replace the data type in the sizeof()
                                                        // call with the new name
                                                        newValue = newValue.replaceFirst(CcddVariableHandler.getSizeofDataTypeMatch(oldName,
                                                                                                                                    macroHandler),
                                                                                         "sizeof(" + newName + ")");
                                                    }
                                                }
                                                // The cell contains only the data type name
                                                else
                                                {
                                                    // Set the new cell value to the new data type
                                                    // name
                                                    newValue = newName;
                                                }
                                            }
                                            // This is a macro change
                                            else
                                            {
                                                // Replace all instances of the old macro name in
                                                // the table cell with the new name
                                                newValue = macroHandler.replaceMacroName(oldNameDelim,
                                                                                         newNameDelim,
                                                                                         oldValue);
                                            }
                                        }

                                        // Check if this a change was made to the data type size or
                                        // base type
                                        if (isDataType && isValueChange)
                                        {
                                            // Check if the data type reference isn't an exact
                                            // match (stand-alone or within a sizeof() call)
                                            if (!newValue.equals(newName)
                                                && !CcddVariableHandler.hasSizeof(newValue)
                                                && !CcddMacroHandler.hasMacro(newValue))
                                            {
                                                // Skip this reference - the data type name match
                                                // is coincidental
                                                continue;
                                            }
                                        }

                                        // Store the change in the table data
                                        tableData.get(row)[column] = newValue;

                                        // Make the change to the cell, including any updates to
                                        // changes in array size
                                        table.validateCellContent(tableData,
                                                                  row,
                                                                  column,
                                                                  oldValue,
                                                                  newValue,
                                                                  false,
                                                                  true);

                                        // Load the updated array of data into the table
                                        table.loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                                                     false);
                                    }

                                    // Stop searching the columns since the target row was located
                                    // and processed
                                    break;
                                }
                            }

                            // Stop searching the rows since the target row was located and
                            // processed
                            break;
                        }
                    }
                }
            }
        }

        try
        {
            // Create a save point in case an error occurs while modifying a table
            dbCommand.createSavePoint(dialog);

            // Check if only a change in data type name, data size (same size or smaller), or macro
            // name occurred; if so then the internal table update process is simplified in order
            // to speed it up
            if (nameChangeOnly)
            {
                // Step through each modification in order to update the variable or macro
                // references in the internal tables
                for (TableModification mod : modifications)
                {
                    // Check if this is a data type change
                    if (isDataType)
                    {
                        // Get the old and new data type name
                        String oldName = CcddDataTypeHandler.getDataTypeName(mod.getOriginalRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                                             mod.getOriginalRowData()[DataTypesColumn.C_NAME.ordinal()].toString());
                        String newName = CcddDataTypeHandler.getDataTypeName(mod.getRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                                             mod.getRowData()[DataTypesColumn.C_NAME.ordinal()].toString());

                        // Execute the command to update the internal tables that reference
                        // variable paths
                        dbCommand.executeDbUpdate("UPDATE "
                                                  + InternalTable.LINKS.getTableName()
                                                  + " SET "
                                                  + LinksColumn.MEMBER.getColumnName()
                                                  + " = regexp_replace("
                                                  + LinksColumn.MEMBER.getColumnName()
                                                  + ", E'(.*,)"
                                                  + oldName
                                                  + "(\\..*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.TLM_SCHEDULER.getTableName()
                                                  + " SET "
                                                  + TlmSchedulerColumn.MEMBER.getColumnName()
                                                  + " = regexp_replace("
                                                  + TlmSchedulerColumn.MEMBER.getColumnName()
                                                  + ", E'(.*,)"
                                                  + oldName
                                                  + "(\\..*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.VALUES.getTableName()
                                                  + " SET "
                                                  + ValuesColumn.TABLE_PATH.getColumnName()
                                                  + " = regexp_replace("
                                                  + ValuesColumn.TABLE_PATH.getColumnName()
                                                  + ", E'(.*,)"
                                                  + oldName
                                                  + "(\\..*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); ",
                                                  dialog);
                    }
                    // This is a macro change
                    else
                    {
                        // Get the original and updated user-defined macro names (with the
                        // delimiters)
                        String oldName = CcddMacroHandler.getFullMacroName(mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString())
                                                         .replaceAll("([\\(\\)])", "\\\\\\\\$1");
                        String newName = CcddMacroHandler.getFullMacroName(mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());

                        // Execute the command to update the internal tables that reference
                        // variable and table paths
                        dbCommand.executeDbUpdate("UPDATE "
                                                  + InternalTable.ASSOCIATIONS.getTableName()
                                                  + " SET "
                                                  + AssociationsColumn.MEMBERS.getColumnName()
                                                  + " = regexp_replace("
                                                  + AssociationsColumn.MEMBERS.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.FIELDS.getTableName()
                                                  + " SET "
                                                  + FieldsColumn.OWNER_NAME.getColumnName()
                                                  + " = regexp_replace("
                                                  + FieldsColumn.OWNER_NAME.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.GROUPS.getTableName()
                                                  + " SET "
                                                  + GroupsColumn.MEMBERS.getColumnName()
                                                  + " = regexp_replace("
                                                  + GroupsColumn.MEMBERS.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.LINKS.getTableName()
                                                  + " SET "
                                                  + LinksColumn.MEMBER.getColumnName()
                                                  + " = regexp_replace("
                                                  + LinksColumn.MEMBER.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.TLM_SCHEDULER.getTableName()
                                                  + " SET "
                                                  + TlmSchedulerColumn.MEMBER.getColumnName()
                                                  + " = regexp_replace("
                                                  + TlmSchedulerColumn.MEMBER.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); UPDATE "
                                                  + InternalTable.VALUES.getTableName()
                                                  + " SET "
                                                  + ValuesColumn.TABLE_PATH.getColumnName()
                                                  + " = regexp_replace("
                                                  + ValuesColumn.TABLE_PATH.getColumnName()
                                                  + ", E'(.*)"
                                                  + oldName
                                                  + "(.*)'"
                                                  + ", E'\\\\1"
                                                  + newName
                                                  + "\\\\2'); ",
                                                  dialog);
                    }
                }
            }

            // Step through each modified table
            for (ModifiedTable modTbl : modifiedTables)
            {
                // Build the additions, modifications, and deletions to the table
                modTbl.getEditor().buildUpdates();

                // Make the table modifications to the project database and to any open table
                // editors that contain the data type (macro) reference(s)
                if (modifyTableData(modTbl.getTableInformation(),
                                    modTbl.getEditor().getAdditions(),
                                    modTbl.getEditor().getModifications(),
                                    modTbl.getEditor().getDeletions(),
                                    true,
                                    nameChangeOnly,
                                    false,
                                    false,
                                    false,
                                    newDataTypeHandler,
                                    dialog))
                {
                    throw new CCDDException("table modification error");
                }
            }

            // Check if this is a macro change and table modifications resulted
            if (!isDataType && !modifiedTables.isEmpty())
            {
                // Rebuild the data field information from the database in the event field changes
                // resulted from the macro change (e.g., an array size increased causing fields to
                // be inherited)
                fieldHandler.buildFieldInformation(dialog);
            }

            // Check if this is a data type change
            if (isDataType)
            {
                // Store the data type and macro tables
                dbCommand.executeDbUpdate(storeNonTableTypesInfoTableCommand(InternalTable.DATA_TYPES,
                                                                             CcddUtilities.removeArrayListColumn(updates,
                                                                                                                 DataTypesColumn.OID.ordinal()),
                                                                             null,
                                                                             dialog),
                                          dialog);
                dbCommand.executeDbUpdate(storeNonTableTypesInfoTableCommand(InternalTable.MACROS,
                                                                             CcddUtilities.removeArrayListColumn(newMacroHandler.getMacroData(),
                                                                                                                 MacrosColumn.OID.ordinal()),
                                                                             null,
                                                                             dialog),
                                          dialog);
            }
            // This is a macro change
            else
            {
                // Store the macro table
                dbCommand.executeDbUpdate(storeNonTableTypesInfoTableCommand(InternalTable.MACROS,
                                                                             CcddUtilities.removeArrayListColumn(updates,
                                                                                                                 MacrosColumn.OID.ordinal()),
                                                                             null,
                                                                             dialog),
                                          dialog);
            }

            // Release the save point. This must be done within a transaction block, so it must be
            // done prior to the commit below
            dbCommand.releaseSavePoint(dialog);

            // Commit the change(s) to the database
            dbControl.getConnection().commit();

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, changeName + " and all affected tables updated");
        }
        catch (CCDDException | SQLException cse)
        {
            // Inform the user that updating the data types (macros) failed
            eventLog.logFailEvent(dialog,
                                  "Cannot update "
                                          + changeName.toLowerCase()
                                          + "; cause '"
                                          + cse.getMessage()
                                          + "'",
                                  "<html><b>Cannot update " + changeName.toLowerCase());

            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, dialog);
        }

        // Check if an error occurred
        if (errorFlag)
        {
            try
            {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(dialog);
            }
            catch (SQLException se)
            {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(dialog,
                                      "Cannot revert changes to project; cause '"
                                              + se.getMessage()
                                              + "'",
                                      "<html><b>Cannot revert changes to project");
            }
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Modify all tables affected by changes to the user-defined input types. This command is
     * executed in a separate thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     *
     * @param modifications
     *            list of input type definition modifications
     *
     * @param updates
     *            array of string arrays reflecting the content of the input types after being
     *            changed in the input type editor
     *
     * @param dialog
     *            reference to the input type editor dialog
     *********************************************************************************************/
    protected void modifyTablesPerInputTypeChanges(final List<TableModification> modifications,
                                                   final String[][] updates,
                                                   final CcddInputTypeEditorDialog dialog)
    {
        // Create new input and table type handlers based on the input type changes
        final CcddInputTypeHandler newInputTypeHandler = new CcddInputTypeHandler(updates);
        final CcddTableTypeHandler newTableTypeHandler = new CcddTableTypeHandler(ccddMain,
                                                                                  newInputTypeHandler);

        /******************************************************************************************
         * Class for table information for those tables modified due to changes in an input type
         * definition
         *****************************************************************************************/
        class ModifiedTable
        {
            private final TableInformation tableInformation;
            private final CcddTableEditorHandler editor;

            /**************************************************************************************
             * Class constructor for table information for those tables modified due to changes in
             * an input type definition
             *
             * @param tablePath
             *            table path (if applicable) and name
             *************************************************************************************/
            ModifiedTable(String tablePath)
            {
                // Load the table's information from the project database
                tableInformation = loadTableData(tablePath, false, true, dialog);

                // Create a table editor handler using the updated input types, but without
                // displaying the editor itself
                editor = new CcddTableEditorHandler(ccddMain,
                                                    tableInformation,
                                                    newTableTypeHandler,
                                                    newInputTypeHandler,
                                                    dialog);
            }

            /**************************************************************************************
             * Get the reference to the table information
             *
             * @return Reference to the table information
             *************************************************************************************/
            protected TableInformation getTableInformation()
            {
                return tableInformation;
            }

            /**************************************************************************************
             * Get the reference to the table editor
             *
             * @return Reference to the table editor
             *************************************************************************************/
            protected CcddTableEditorHandler getEditor()
            {
                return editor;
            }
        }

        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            boolean errorFlag = false;
            List<String[]> inputTypeNames = new ArrayList<String[]>();

            /**************************************************************************************
             * Modify input types
             *************************************************************************************/
            @Override
            protected void execute()
            {
                List<ModifiedTable> modifiedTables = new ArrayList<ModifiedTable>();

                try
                {
                    String tableTypeCommand = "";
                    String fieldCommand = "";

                    // Step through each input type table modification
                    for (TableModification mod : modifications)
                    {
                        // Get the original and (possibly) updated input type names to shorten
                        // subsequent calls
                        String oldName = mod.getOriginalRowData()[InputTypesColumn.NAME.ordinal()].toString();
                        String newName = mod.getRowData()[InputTypesColumn.NAME.ordinal()].toString();

                        // Store the input type name before and after the update, in case the name
                        // changed. Input types are generally fetched from the handler based on the
                        // name, so a name change makes this no longer possible. Providing a name
                        // translation list allows locating the correct input type
                        inputTypeNames.add(new String[] {oldName, newName});

                        // Check if the input type name changed
                        if (!oldName.equals(newName))
                        {
                            // Append the commands to update the input type name in the table types
                            // and data fields tables
                            tableTypeCommand += "UPDATE "
                                                + InternalTable.TABLE_TYPES.getTableName()
                                                + " SET "
                                                + TableTypesColumn.INPUT_TYPE.getColumnName()
                                                + " = "
                                                + delimitText(newName)
                                                + " WHERE "
                                                + TableTypesColumn.INPUT_TYPE.getColumnName()
                                                + " = "
                                                + delimitText(oldName)
                                                + "; ";
                            fieldCommand += "UPDATE "
                                            + InternalTable.FIELDS.getTableName()
                                            + " SET "
                                            + FieldsColumn.FIELD_TYPE.getColumnName()
                                            + " = "
                                            + delimitText(newName)
                                            + " WHERE "
                                            + FieldsColumn.FIELD_TYPE.getColumnName()
                                            + " = "
                                            + delimitText(oldName)
                                            + "; ";
                        }

                        // Check if the input type regular expression match string or selection
                        // item list changed
                        if (!mod.getOriginalRowData()[InputTypesColumn.MATCH.ordinal()].equals(mod.getRowData()[InputTypesColumn.MATCH.ordinal()])
                            || !mod.getOriginalRowData()[InputTypesColumn.ITEMS.ordinal()].equals(mod.getRowData()[InputTypesColumn.ITEMS.ordinal()]))
                        {
                            List<String> tableTypes = new ArrayList<String>();
                            List<String[]> fields = new ArrayList<String[]>();

                            // Get the original and new input type definition references
                            InputType inputType = inputTypeHandler.getInputTypeByName(oldName);

                            // Step through each reference to the input type name
                            for (String inputTypeRef : inputTypeHandler.searchInputTypeReferences(oldName, dialog))
                            {
                                // Split the reference into table name, column name, comment, and
                                // context
                                String[] tblColCmtAndCntxt = inputTypeRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);

                                // Extract the context from the reference
                                String[] refColumns = CcddUtilities.splitAndRemoveQuotes(tblColCmtAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                                // Check if the context is in a table type definition
                                if (tblColCmtAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()].equals(InternalTable.TABLE_TYPES.getTableName()))
                                {
                                    // Check if the table type name hasn't already been added to
                                    // the list
                                    if (!tableTypes.contains(refColumns[TableTypesColumn.TYPE_NAME.ordinal()]))
                                    {
                                        // Add the table type name to the list of those using the
                                        // input type
                                        tableTypes.add(refColumns[TableTypesColumn.TYPE_NAME.ordinal()]);
                                    }
                                }
                                // Check if the context is in a data field definition
                                else if (tblColCmtAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()].equals(InternalTable.FIELDS.getTableName()))
                                {
                                    // Add the data field definition to the list of those using the
                                    // input type
                                    fields.add(refColumns);
                                }
                            }

                            // Step through each change in the table type definitions
                            for (String tableType : tableTypes)
                            {
                                // Get the names of the prototype tables of this table type
                                String[] tables = getPrototypeTablesOfType(tableType);

                                // Check if any tables of this type exist
                                if (tables.length != 0)
                                {
                                    Boolean showMessage = true;

                                    // Get the original table type definition
                                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

                                    // Get the column(s) in the table type of this input type
                                    List<Integer> columns = typeDefn.getColumnIndicesByInputType(inputType);

                                    // Step through each prototype table of this table type
                                    for (String tableName : tables)
                                    {
                                        ModifiedTable modifiedTable = null;

                                        // Step through each table already loaded for modifications
                                        for (ModifiedTable modTbl : modifiedTables)
                                        {
                                            // Check if the table has already been loaded
                                            if (modTbl.getTableInformation().getTablePath().equals(tableName))
                                            {
                                                // Store the reference to the modified table and
                                                // stop searching
                                                modifiedTable = modTbl;
                                                break;
                                            }
                                        }

                                        // Check if the table isn't already loaded
                                        if (modifiedTable == null)
                                        {
                                            // Load the table and add it to the list
                                            modifiedTable = new ModifiedTable(tableName);
                                            modifiedTables.add(modifiedTable);

                                            // Check if the table arrays aren't expanded
                                            if (!modifiedTable.getEditor().isExpanded())
                                            {
                                                // Expand the table arrays
                                                modifiedTable.getEditor().showHideArrayMembers();
                                            }
                                        }

                                        // Get the reference to the table to shorten subsequent
                                        // calls
                                        CcddJTableHandler table = modifiedTable.getEditor().getTable();

                                        // Get the table's data (again if a name change occurred
                                        // since changes were made)
                                        List<Object[]> tableData = table.getTableDataList(false);

                                        // Step through each row
                                        for (int row = 0; row < tableData.size(); row++)
                                        {
                                            // Step through each column with the modified input
                                            // type
                                            for (Integer column : columns)
                                            {
                                                // Make the change to the cell
                                                showMessage = table.validateCellContent(tableData,
                                                                                        row,
                                                                                        column,
                                                                                        "",
                                                                                        tableData.get(row)[column],
                                                                                        showMessage,
                                                                                        true);

                                                // Load the updated array of data into the table
                                                table.loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                                                             false);
                                            }
                                        }
                                    }
                                }
                            }

                            // Step through each change in the data field definitions
                            for (String[] field : fields)
                            {
                                // Check if the field's value no longer matches the regular
                                // expression
                                if (!field[FieldsColumn.FIELD_VALUE.ordinal()].matches(mod.getRowData()[InputTypesColumn.MATCH.ordinal()].toString()))
                                {
                                    // Build the command to set the field value to blank
                                    fieldCommand += "UPDATE "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SET "
                                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                                    + " = '' WHERE "
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + " = "
                                                    + delimitText(field[FieldsColumn.OWNER_NAME.ordinal()])
                                                    + " AND "
                                                    + FieldsColumn.FIELD_NAME.getColumnName()
                                                    + " = "
                                                    + delimitText(field[FieldsColumn.FIELD_NAME.ordinal()])
                                                    + "; ";
                                }
                            }
                        }
                    }

                    // Create a save point in case an error occurs while modifying a table
                    dbCommand.createSavePoint(dialog);

                    // Step through each modified table
                    for (ModifiedTable modTbl : modifiedTables)
                    {
                        // Build the additions, modifications, and deletions to the table
                        modTbl.getEditor().buildUpdates();

                        // Make the table modifications to the project database and to any open
                        // table editors that contain the input type reference(s)
                        if (modifyTableData(modTbl.getTableInformation(),
                                            modTbl.getEditor().getAdditions(),
                                            modTbl.getEditor().getModifications(),
                                            modTbl.getEditor().getDeletions(),
                                            true,
                                            false,
                                            false,
                                            false,
                                            false,
                                            null,
                                            dialog))
                        {
                            throw new SQLException("table modification error");
                        }
                    }

                    // Check if any input type name changes were made
                    if (!tableTypeCommand.isEmpty() || !fieldCommand.isEmpty())
                    {
                        // Execute the command to update the input type name in the internal tables
                        dbCommand.executeDbUpdate(tableTypeCommand + fieldCommand, dialog);
                    }

                    // Store the updated input types table
                    dbCommand.executeDbUpdate(storeNonTableTypesInfoTableCommand(InternalTable.INPUT_TYPES,
                                                                                 CcddUtilities.removeArrayListColumn(Arrays.asList(dialog.getUpdatedData()),
                                                                                                                     InputTypesColumn.OID.ordinal()),
                                                                                 null,
                                                                                 dialog),
                                              dialog);

                    // Release the save point. This must be done within a transaction block, so it
                    // must be done prior to the commit below
                    dbCommand.releaseSavePoint(dialog);

                    // Commit the change(s) to the database
                    dbControl.getConnection().commit();

                    // Inform the user that the update succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Input types and all affected tables and fields updated");
                }
                catch (SQLException se)
                {
                    // Inform the user that updating the macros failed
                    eventLog.logFailEvent(dialog,
                                          "Cannot update input types; cause '"
                                                  + se.getMessage()
                                                  + "'",
                                          "<html><b>Cannot update input types");

                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, dialog);
                }
            }

            /**************************************************************************************
             * Modify input types complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Perform the input types command completion steps
                dialog.doInputTypeUpdatesComplete(errorFlag, inputTypeNames);
            }
        });
    }

    /**********************************************************************************************
     * Get an array containing all prototype tables that are of the specified table type (combining
     * all structure and all command tables)
     *
     * @param tableType
     *            TYPE_STRUCTURE to get all tables for any type that represents a structure,
     *            TYPE_COMMAND to get all tables for any type that represents a command, TYPE_OTHER
     *            to get all tables that are neither a structure or command table, or the table
     *            type name to get all tables for the specified type
     *
     * @return Array containing all prototype tables that represent the specified type. Returns an
     *         empty array if no table of the specified type exists in the project database
     *********************************************************************************************/
    protected String[] getPrototypeTablesOfType(String tableType)
    {
        String[] tablesOfType = new String[0];

        // Step through each table type
        for (String type : tableTypeHandler.getTableTypeNames())
        {
            boolean isOfType;

            // Check if the table type matches the one specified. Note that all structure types are
            // handled as one, as are all command types and all 'other' types
            switch (tableType)
            {
                case TYPE_STRUCTURE:
                    isOfType = tableTypeHandler.getTypeDefinition(type).isStructure();
                    break;

                case TYPE_COMMAND:
                    isOfType = tableTypeHandler.getTypeDefinition(type).isCommand();
                    break;

                case TYPE_OTHER:
                    isOfType = !tableTypeHandler.getTypeDefinition(type).isStructure()
                               && !tableTypeHandler.getTypeDefinition(type).isCommand();
                    break;

                default:
                    isOfType = tableType.equals(type);
                    break;
            }

            // Check if the table type matches the one specified
            if (isOfType)
            {
                // Append the table name(s) of this type to the array of names
                tablesOfType = CcddUtilities.concatenateArrays(tablesOfType,
                                                               queryTablesOfTypeList(type,
                                                                                     ccddMain.getMainFrame()));
            }
        }

        return tablesOfType;
    }

    /**********************************************************************************************
     * Get a list containing all tables that are of the specified table type
     *
     * @param typeName
     *            table type name
     *
     * @param protoTableNames
     *            names of the prototype tables of the specified table type; null to load the list
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing all tables that are of the specified table type. Returns an empty
     *         list if no table of the specified type exists in the project database
     *********************************************************************************************/
    protected List<String> getAllTablesOfType(String typeName,
                                              String[] protoTableNames,
                                              Component parent)
    {
        // Create a list to store the names of all tables of the specified type
        List<String> tableNamesList = new ArrayList<String>();

        // Check if the prototype name list isn't provided
        if (protoTableNames == null)
        {
            // Get an array containing all of the prototype tables of the specified type
            protoTableNames = queryTablesOfTypeList(typeName, parent);
        }

        // Check if a table of this type exists
        if (protoTableNames.length != 0)
        {
            // Build a table tree with all prototype and instance tables
            CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain,
                                                                      TableTreeType.TABLES,
                                                                      parent);

            // Step through each prototype table of the specified type
            for (String protoName : protoTableNames)
            {
                // Create a list of table path arrays for instances of this prototype table name
                tableNamesList.addAll(tableTree.getTableTreePathList(protoName));
            }
        }

        return tableNamesList;
    }
}
