/**
 * CFS Command & Data Dictionary database table command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DB_SAVE_POINT_NAME;
import static CCDD.CcddConstants.ENUMERATION_SEPARATOR;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.LIST_TABLE_SEPARATOR;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_IDENT;
import static CCDD.CcddConstants.REPLACE_INDICATOR;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;
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
import CCDD.CcddClasses.ArrayListMultiple;
import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.NodeIndex;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableMembers;
import CCDD.CcddClasses.TableModification;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DatabaseObject;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.OrdersColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddConstants.TableMemberType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary database table command handler class
 *****************************************************************************/
public class CcddDbTableCommandHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private final CcddDbControlHandler dbControl;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddMacroHandler macroHandler;
    private CcddRateParameterHandler rateHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddLinkHandler addLinkHandler;

    // Flag that indicates a variable has been added to a link definition and
    // the links table should be updated
    private boolean updateLinks;

    // Character(s) separating table references in the script associations and
    // telemetry scheduler tables, with any special characters escaped so as to
    // be used in a PostgrSQL command
    private final String assnsSeparator;
    private final String tlmSchSeparator;

    /**************************************************************************
     * Database table command handler class constructor
     *
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddDbTableCommandHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        eventLog = ccddMain.getSessionEventLog();

        // Escape any special characters in the script associations and
        // telemetry scheduler table
        // separators
        assnsSeparator = CcddUtilities.escapePostgreSQLReservedChars(LIST_TABLE_SEPARATOR);
        tlmSchSeparator = CcddUtilities.escapePostgreSQLReservedChars(TLM_SCH_SEPARATOR);
    }

    /**************************************************************************
     * Set the references to the table type, macro, data type, and rate handler
     * classes
     *************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        rateHandler = ccddMain.getRateParameterHandler();
    }

    /**************************************************************************
     * Enclose database text string objects with a delimiter. The default
     * delimiter is single quotes. If the text contains single quotes or a
     * backslash then $$ is used instead, unless the text already contains $$,
     * in which case $_$ is used as the delimiter
     *
     * @param object
     *            object to be stored in the database
     *
     * @return Object enclosed by delimiters if the object is a text string
     *************************************************************************/
    protected Object delimitText(Object object)
    {
        // Check if this is a string
        if (object instanceof String)
        {
            // Use a single quote as the default delimiter
            String delim = "'";

            // Convert the item to a string
            String objectS = object.toString();

            // Check if the item contains a single quote or backslash
            // character
            if (objectS.contains("'") || objectS.contains("\\"))
            {
                // Check if the item contains $$
                if (objectS.contains("$$"))
                {
                    // Use $_$ as the delimiter
                    delim = "$_$";
                }
                // The item doesn't contain $$
                else
                {
                    // Use $$ as the delimiter
                    delim = "$$";
                }
            }

            // Enclose the item with the delimiter
            object = delim + objectS + delim;
        }

        return object;
    }

    /**************************************************************************
     * Check if a specified table exists in the database (case insensitive)
     *
     * @param tableName
     *            name of the table to check for in the database
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return true if the specified table exists in the database
     *************************************************************************/
    protected boolean isTableExists(String tableName, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SPECIFIC_TABLE,
                                 new String[][] {{"_table_name_", tableName}},
                                 parent).length != 0;
    }

    /**************************************************************************
     * Get the list of root (top level) structure tables
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List of root structure tables; empty list if there are no root
     *         tables
     *************************************************************************/
    protected List<String> getRootStructures(Component parent)
    {
        List<String> topLevelStructures = new ArrayList<String>();

        // Get the prototype tables and their member tables
        List<TableMembers> tableMembers = loadTableMembers(TableMemberType.TABLES_ONLY,
                                                           true,
                                                           parent);

        // Step through each table
        for (TableMembers member : tableMembers)
        {
            // Check if the member is a structure table type
            if (tableTypeHandler.getTypeDefinition(member.getTableType()).isStructure())
            {
                boolean isRootStructure = true;

                // Step through each table
                for (TableMembers otherMember : tableMembers)
                {
                    // Check if the current table has this table as a member,
                    // and that the table isn't referencing itself
                    if (otherMember.getDataTypes().contains(member.getTableName())
                        && !member.equals(otherMember))
                    {
                        // Clear the flag indicating this is a root table and
                        // stop searching
                        isRootStructure = false;
                        break;
                    }
                }

                // Check if this is a root structure
                if (isRootStructure)
                {
                    // Store the root structure's table name
                    topLevelStructures.add(member.getTableName());
                }
            }
        }

        return topLevelStructures;
    }

    /**************************************************************************
     * Build the specified table's comment command
     *
     * @param tableName
     *            table name
     *
     * @param comment
     *            table comment
     *
     * @return Command to update the table's comment
     *************************************************************************/
    private String buildTableComment(String tableName, String comment)
    {
        return "COMMENT ON TABLE "
               + tableName
               + " IS "
               + delimitText(comment)
               + "; ";
    }

    /**************************************************************************
     * Build the specified data table's comment command. The table's visible
     * table name (preserving case) and its type are stored as a comment on the
     * table
     *
     * @param tableName
     *            table name
     *
     * @param tableType
     *            table type
     *
     * @return Command to update the table's comment
     *************************************************************************/
    private String buildDataTableComment(String tableName,
                                         String tableType)
    {
        return buildTableComment(tableName.toLowerCase(),
                                 CcddConstants.TableCommentIndex.buildComment(tableName,
                                                                              tableType));
    }

    /**************************************************************************
     * Build the specified table instance's description update command. The
     * table's description is stored in the custom values table. Blank
     * descriptions aren't stored
     *
     * @param tablePath
     *            path to the table. For a structure table the format is:
     *            rootTable,dataType0.variableName0[,
     *            dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy. The path is the same as the root
     *            table name for non-structure tables
     *
     * @param description
     *            table description
     *
     * @return Command to update the specified table's description
     *************************************************************************/
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

    /**************************************************************************
     * Build the specified table instance's column order update command
     *
     * @param tablePath
     *            path to the table. For a structure table the format is:
     *            rootTable,dataType0.variableName0[,
     *            dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy. The path is the same as the root
     *            table name for non-structure tables
     *
     * @param columnOrder
     *            table column order
     *
     * @return Command to update the specified table's column order for the
     *         current user
     *************************************************************************/
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

    /**************************************************************************
     * Perform a query on the currently open database
     *
     * @param sqlCommand
     *            PostgreSQL-compatible database query statement
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Two-dimensional array representing the rows and columns of data
     *         returned by the database query; returns null if the query
     *         produces an error, or an empty array if there are no results
     *************************************************************************/
    protected List<String[]> queryDatabase(String sqlCommand,
                                           Component parent)
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
                    // Add the column value to the array. Note that the first
                    // column's index in the database is 1, not 0
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
                                  "Database query failed; cause '"
                                      + se.getMessage()
                                      + "'", "<html><b>Database query failed");
        }

        return queryResults;
    }

    /**************************************************************************
     * Retrieve the specified cell's value from the specified table
     *
     * @param table
     *            name database table name
     *
     * @param primaryKey
     *            value of the table's primary key column from which to extract
     *            the cell value
     *
     * @param columnName
     *            name of the column from which to extract the cell value
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Value of the cell in the table specified; blank if the query
     *         fails
     *************************************************************************/
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

    /**************************************************************************
     * Retrieve a list of all data tables in the database. Any non-data tables
     * are ignored
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the table names with their original
     *         capitalization
     *************************************************************************/
    protected String[] queryTableList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.DATA_TABLES,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of all data tables in the database along with the
     * table's type. Any non-data tables are ignored
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List array containing the table names and table types. The table
     *         names are provided in both the user-viewable form, which
     *         preserves capitalization, and the database form. An empty array
     *         is returned if no tables exist
     *************************************************************************/
    protected List<String[]> queryTableAndTypeList(Component parent)
    {
        List<String[]> typeAndTable = new ArrayList<String[]>(0);

        // Get the array containing the table types and names
        String[] tables = dbCommand.getList(DatabaseListCommand.DATA_TABLES_WITH_TYPE,
                                            null,
                                            parent);

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

    /**************************************************************************
     * Retrieve a list of all table types in the database. Any non-data tables
     * are ignored
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the table types in alphabetical order
     *************************************************************************/
    protected String[] queryTableTypesList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLE_TYPES,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of all tables of a specific type in the database. Any
     * non-data tables are ignored
     *
     * @param tableType
     *            table type by which to filter the table names. The table type
     *            is case insensitive
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the table names of the specified type
     *************************************************************************/
    protected String[] queryTablesOfTypeList(String tableType,
                                             Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLES_OF_TYPE,
                                 new String[][] {{"_type_", tableType.toLowerCase()}},
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of all tables that represent script files
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the script table names
     *************************************************************************/
    protected String[] queryScriptTables(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SCRIPTS,
                                 null,
                                 parent);
    }

    /**************************************************************************
     * Retrieve a list of comments for all data tables in the database. Any
     * non-data tables are ignored
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the table comment parameters and the
     *         table name as used in the database
     *************************************************************************/
    protected String[][] queryDataTableComments(Component parent)
    {
        // Get the array of comment strings for every data table
        String[] comments = dbCommand.getList(DatabaseListCommand.TABLE_COMMENTS,
                                              null,
                                              parent);

        // Create storage for the parsed comments
        String[][] parsedComments = new String[comments.length][];

        int index = 0;

        // Step through each comment
        for (String comment : comments)
        {
            // Parse the comment into its separate parameters
            parsedComments[index] = comment.split(",");
            index++;
        }

        return parsedComments;
    }

    /**************************************************************************
     * Retrieve the comment for the specified table
     *
     * @param tableName
     *            table name
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Array containing the separate elements of the table comment
     *************************************************************************/
    protected String[] queryTableComment(String tableName, Component parent)
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
                for (String part : text.split(","))
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

    /**************************************************************************
     * Retrieve the comment for the specified data table. The comment contains
     * the table's visible name (preserves case) and type
     *
     * @param tableName
     *            data table name
     *
     * @param parent
     *            dialog calling this component
     *
     * @return Array containing the separate elements of the data table comment
     *************************************************************************/
    private String[] queryDataTableComment(String tableName, Component parent)
    {
        // Get the table's comment, broken into its separate elements
        String comment[] = queryTableComment(tableName, parent);

        // Create an array to contain the data table comment elements and
        // initialize each to a blank
        String[] parts = new String[TableCommentIndex.values().length];
        Arrays.fill(parts, "");

        // Replace the empty element with that from the comment
        System.arraycopy(comment, 0, parts, 0, comment.length);

        return parts;
    }

    /**************************************************************************
     * Retrieve a list of all data table descriptions in the database. Any
     * non-data tables are ignored
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return String array containing the table names and their variable
     *         paths, if any, for those tables with descriptions
     *************************************************************************/
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

    /**************************************************************************
     * Retrieve the description for the specified table
     *
     * @param tablePath
     *            table path in the format
     *            rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
     *            The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top
     *            level structure table from which this table descends. The
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy
     *
     * @param tableName
     *            name of the queried table's prototype table; blank if none
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Table description; blank if the table has no description
     *************************************************************************/
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
                // Split the description data into its constituent parts and
                // copy these to the description
                description = descData.getString(1).trim();
            }

            descData.close();

            // Check if this is not a prototype table
            if (description.isEmpty() && tablePath.contains(","))
            {
                // Get the description of the prototype table for this table
                // instance and use it for the instance's description
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

    /**************************************************************************
     * Retrieve the column order for the specified table and the current user
     *
     * @param tablePath
     *            comma-separated variable names describing the path to this
     *            table, beginning with the root table; the names are in the
     *            order from highest level to lowest level, where the lowest
     *            level name is the <variable name> portion of the table name;
     *            blank if the queried table is a prototype
     *
     * @param tableType
     *            type of table being queried
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Table column order
     *************************************************************************/
    private String queryColumnOrder(String tablePath,
                                    String tableType,
                                    Component parent)
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

    /**************************************************************************
     * Return an array of table comment components for the specified table
     *
     * @param tableNameDb
     *            table name as used by the database
     *
     * @param comments
     *            array of table comments, with each comment divided into its
     *            component parts
     *
     * @return Array of comment components for the specified table
     *************************************************************************/
    protected String[] getTableComment(String tableNameDb, String[][] comments)
    {
        // Initialize the comment array
        String[] comment = new String[TableCommentIndex.values().length];
        Arrays.fill(comment, "");

        // Step through each table comment
        for (String[] cmt : comments)
        {
            // Check if the target name matches the table name in the comment
            if (tableNameDb.equals(cmt[TableCommentIndex.NAME.ordinal()].toLowerCase()))
            {
                // Store the elements of the comment that were retrieved for
                // the table and stop searching
                System.arraycopy(cmt, 0, comment, 0, Math.min(comment.length, cmt.length));
                break;
            }
        }

        return comment;
    }

    /**************************************************************************
     * Set the comment for the specified table
     *
     * @param tableName
     *            table name as used by the database
     *
     * @param comment
     *            table comment
     *
     * @param parent
     *            reference to the GUI component over which any error dialogs
     *            should be centered
     *************************************************************************/
    protected void setTableComment(String tableName,
                                   String comment,
                                   Component parent)
    {
        // Execute the database update
        try
        {
            // Build and execute the command to update the table's comment
            dbCommand.executeDbUpdate(buildTableComment(tableName, comment),
                                      parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Table '"
                                  + tableName
                                  + "' comment updated");
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

    /**************************************************************************
     * Build the command to copy the data fields from one table to another
     *
     * @param fieldTableName
     *            name of the table in the data field table from which to copy
     *
     * @param tableName
     *            name of the table receiving the data fields
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return Command to copy the data fields for one table to another
     *************************************************************************/
    private String copyDataFieldCommand(String fieldTableName,
                                        String tableName,
                                        Component parent)
    {
        String command = "";

        // Create a field handler to store the new table's data field
        // information (if any)
        CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                             fieldTableName,
                                                             parent);

        // Check if any fields exist
        if (!fieldHandler.getFieldInformation().isEmpty())
        {
            // Build the command to add the fields to the new table
            command = " "
                      + modifyFieldsCommand(tableName,
                                            fieldHandler.getFieldInformation());
        }

        return command;
    }

    /**************************************************************************
     * Create one or more tables of the specified type. This command is
     * executed in a separate thread since it can take a noticeable amount time
     * to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until
     * the database command completes execution
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
     *************************************************************************/
    protected void createTableInBackground(final String[] tableNames,
                                           final String description,
                                           final String tableType,
                                           final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /******************************************************************
             * Database table creation command
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Create the table(s)
                errorFlag = createTable(tableNames,
                                        description,
                                        tableType,
                                        tableDialog);
            }

            /******************************************************************
             * Create database table command complete
             *****************************************************************/
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

    /**************************************************************************
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
     * @param tableDialog
     *            reference to the table manager dialog
     *
     * @return true if an error occurred when creating a table
     *************************************************************************/
    protected boolean createTable(String[] tableNames,
                                  String description,
                                  String tableType,
                                  Component parent)
    {
        boolean errorFlag = false;

        // Convert the array of names into a single string, separated by commas
        String allNames = getShortenedTableNames(tableNames);

        try
        {
            String command = "";

            // Step through each new table name
            for (String tableName : tableNames)
            {
                // Add the command to create the table
                command += createTableCommand(tableName,
                                              description,
                                              tableType,
                                              parent);
            }

            // Execute the database update
            dbCommand.executeDbUpdate(command, parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Table(s) '"
                                  + allNames
                                  + "' created");
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
                                  "<html><b>Cannot create table(s) '</b>"
                                      + allNames
                                      + "<b>'");
            errorFlag = true;
        }

        return errorFlag;
    }

    /**************************************************************************
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
     *************************************************************************/
    private String createTableCommand(String tableName,
                                      String description,
                                      String tableType,
                                      Component parent)
    {
        StringBuilder command = new StringBuilder("");

        // Convert the table name to lower case. This is done automatically by
        // PostgreSQL, so this is done here to differentiate the table name
        // from the upper case database commands in the event log
        String dbTableName = tableName.toLowerCase();

        // Get the column names defined in the template file for this table
        // type
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

        // Build the table creation and description commands
        command.append("CREATE TABLE "
                       + dbTableName
                       + " (");

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

        // Remove the trailing comma and space, then append the closing portion
        // of the command, add the command to save the table comments, and add
        // the column descriptions as table column comments
        command = CcddUtilities.removeTrailer(command, ", ");
        command.append("); "
                       + buildDataTableComment(tableName, tableType)
                       + buildTableDescription(tableName, description)
                       + buildColumnOrder(tableName,
                                          tableTypeHandler.getDefaultColumnOrder(tableType)));

        // Copy the default fields for the new table's type to the new table
        // and set the table's owner
        command.append(copyDataFieldCommand(CcddFieldHandler.getFieldTypeName(tableType),
                                            tableName,
                                            parent)
                       + dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                     tableName));

        return command.toString();
    }

    /**************************************************************************
     * Change the name of a table. Only prototype tables can be renamed using
     * this method (instances are renamed by changing the variable name in the
     * prototype). This command is executed in a separate thread since it can
     * take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands,
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
     *************************************************************************/
    protected void renameTable(final String tableName,
                               final String newName,
                               final String newDescription,
                               final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /******************************************************************
             * Rename database table command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Convert the table names to lower case. This is done
                    // automatically by PostgreSQL, so this is done here to
                    // differentiate the table name from the upper case
                    // database commands in the event log
                    String dbTableName = tableName.toLowerCase();
                    String dbNewName = newName.toLowerCase();

                    // Get the table's comment so that it can be rebuilt with
                    // the new table name
                    String[] comment = queryDataTableComment(dbTableName,
                                                             tableDialog);

                    String command = "";

                    // Check that the old and new names differ in more than
                    // capitalization
                    if (!dbTableName.equals(dbNewName))
                    {
                        // Create the command to change the table's name and
                        // all references to it in the custom values, links,
                        // and groups tables
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

                    // Update the table comment to reflect the name with case
                    // intact
                    command += buildDataTableComment(newName,
                                                     comment[TableCommentIndex.TYPE.ordinal()]);

                    // Search for all occurrences of the old table name in
                    // those tables containing a data type column and replace
                    // the references with the new table name
                    command += "SELECT update_data_type_names('"
                               + tableName
                               + "', '"
                               + newName
                               + "');";

                    // Execute the command to change the table's name,
                    // including the table's original name (before conversion
                    // to all lower case) that's stored as a comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table '"
                                          + tableName
                                          + "' renamed to '"
                                          + newName
                                          + "'");
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
                                          "<html><b>Cannot rename table '</b>"
                                              + tableName
                                              + "<b>'");
                    errorFlag = true;
                }
            }

            /******************************************************************
             * Rename database table command complete
             *****************************************************************/
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

    /**************************************************************************
     * Create the command to rename a table reference in an internal table
     *
     * @param intType
     *            internal table type
     *
     * @param idColumnName
     *            name of the internal table's identifier column
     *
     * @param tableName
     *            current name of the table
     *
     * @param newName
     *            new name for this table
     *
     * @return Command to rename the table reference in the specified internal
     *         table
     *************************************************************************/
    private String renameInfoTableCommand(InternalTable intType,
                                          String idColumnName,
                                          String oldName,
                                          String newName)
    {
        // Get the table separator, escaping any special character(s)
        String separator = intType == InternalTable.ASSOCIATIONS
                                                                ? "|"
                                                                  + assnsSeparator
                                                                : "";

        return "UPDATE "
               + intType.getTableName()
               + " SET "
               + idColumnName
               + " = regexp_replace("
               + idColumnName
               + ", E'(^|,"
               + (intType == InternalTable.TLM_SCHEDULER
                                                        ? "|"
                                                          + tlmSchSeparator
                                                        : separator)
               + ")"
               + oldName
               + "(\\\\.|,"
               + separator
               + "|$)', E'\\\\1"
               + newName
               + "\\\\2'); ";
    }

    /**************************************************************************
     * Make a copy of an existing table. Only prototype tables can be copied
     * using this method (instances are copied by adding a new instance and
     * copying the values to it in the table editor). This command is executed
     * in a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue
     * to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
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
     *************************************************************************/
    protected void copyTable(final String tableName,
                             final String newName,
                             final String newDescription,
                             final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /******************************************************************
             * Copy table command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Convert the table names to lower case. This is done
                    // automatically by PostgreSQL, so this is done here to
                    // differentiate the table name from the upper case
                    // database commands in the event log
                    String dbTableName = tableName.toLowerCase();
                    String dbNewName = newName.toLowerCase();

                    // Get the existing table's comment, description, and
                    // column order so that these can be used for the copy
                    String[] comment = queryDataTableComment(dbTableName,
                                                             tableDialog);
                    String columnOrder = queryColumnOrder(tableName,
                                                          comment[1],
                                                          tableDialog);

                    String sequenceName = dbNewName
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

                    // Update the table comment to reflect the name with case
                    // intact, and create the commands to duplicate the
                    // original table's description and column order
                    command += buildDataTableComment(newName,
                                                     comment[TableCommentIndex.TYPE.ordinal()])
                               + buildTableDescription(newName,
                                                       newDescription)
                               + buildColumnOrder(newName,
                                                  columnOrder);

                    // Copy the table's data field entries for the new table
                    command += copyDataFieldCommand(tableName,
                                                    newName,
                                                    tableDialog);

                    // Execute the command to copy the table, including the
                    // table's original name (before conversion to all lower
                    // case) that's stored as a comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table '"
                                          + tableName
                                          + "' copied to '"
                                          + newName
                                          + "'");
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
                                          "<html><b>Cannot copy table '</b>"
                                              + tableName
                                              + "<b>'");
                    errorFlag = true;
                }
            }

            /**************************************************************
             * Copy database table command complete
             *************************************************************/
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

    /**************************************************************************
     * Delete one or more prototype or script tables. The selected tables(s)
     * are deleted from the database, all references to the table are deleted
     * from the custom values table, and any open editors for tables of this
     * prototype are closed. This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param dialog
     *            reference to the table manager dialog calling this method;
     *            null if not called by the table manager
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void deleteTableInBackground(final String[] tableNames,
                                           final CcddTableManagerDialog dialog,
                                           final Component parent)
    {
        // Convert the array of names into a single string
        final String names = getShortenedTableNames(tableNames);

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

                /**************************************************************
                 * Database table deletion command
                 *************************************************************/
                @Override
                protected void execute()
                {
                    // Delete the table(s)
                    errorFlag = deleteTable(tableNames, dialog, parent);
                }

                /**************************************************************
                 * Delete database table command complete
                 *************************************************************/
                @Override
                protected void complete()
                {
                    // Check if no error occurred deleting the table and if the
                    // table manager called this method
                    if (!errorFlag && dialog != null)
                    {
                        dialog.doTableOperationComplete();
                    }
                }
            });
        }
    }

    /**************************************************************************
     * Delete one or more prototype or script tables. The selected tables(s)
     * are deleted from the database, all references to the table are deleted
     * from the custom values table, and any open editors for tables of this
     * prototype are closed
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param dialog
     *            reference to the table manager dialog calling this method;
     *            null if not called by the table manager
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return true if an error occurred when deleting a table
     *************************************************************************/
    protected boolean deleteTable(String[] tableNames,
                                  CcddTableManagerDialog dialog,
                                  Component parent)
    {
        boolean errorFlag = false;

        // Convert the array of names into a single string
        String names = getShortenedTableNames(tableNames);

        try
        {
            // Build the command and delete the table(s). If the table manager
            // called this method (dialog isn't null) then these are data
            // tables
            dbCommand.executeDbUpdate(deleteTableCommand(tableNames,
                                                         dialog != null),
                                      parent);

            // Check if the deletion is for a data table. If the table manager
            // called this method (dialog isn't null) then these are data
            // tables
            if (dialog != null)
            {
                // Execute the command to reset the rate for links that no
                // longer contain any variables
                dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);
            }

            // Log that the table deletion succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Table(s) '"
                                  + names
                                  + "' deleted");
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
                                  "<html><b>Cannot delete table(s) '</b>"
                                      + names
                                      + "<b>'");
            errorFlag = true;
        }

        return errorFlag;
    }

    /**************************************************************************
     * Build the command to delete one or more prototype or script tables
     *
     * @param tableNames
     *            array of names of the tables to delete
     *
     * @param isDataTable
     *            true if the table(s) to be deleted are data tables; false if
     *            the tables are scripts or internal tables
     *************************************************************************/
    private String deleteTableCommand(String[] tableNames, boolean isDataTable)
    {
        // Build the table deletion commands
        String command = "DROP TABLE IF EXISTS ";

        // Step through the array of table names
        for (String name : tableNames)
        {
            // Add the table to the commands
            command += name + ", ";
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

                // Build the command to update the script associations table by
                // removing references to this table
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

            // Replace the column 1 name placeholder with the table's column 1
            // name, then add the update string to the command. In the
            // telemetry scheduler table the table names are preceded by the
            // rate value and a separator which must be skipped
            valuesDelCmd += infoCmd.replaceAll("col1",
                                               ValuesColumn.TABLE_PATH.getColumnName());
            linksDelCmd += infoCmd.replaceAll("col1",
                                              LinksColumn.MEMBER.getColumnName());
            tlmSchDelCmd += infoCmd.replaceAll("col1",
                                               TlmSchedulerColumn.MEMBER.getColumnName())
                                   .replaceAll("\\^",
                                               "^\\\\\\\\d+\\\\\\\\.\\\\\\\\d+"
                                                   + "\\\\\\\\\\\\\\"
                                                   + TLM_SCH_SEPARATOR);
            groupsDelCmd += infoCmd.replaceAll("col1",
                                               GroupsColumn.MEMBERS.getColumnName());
            fieldsDelCmd += infoCmd.replaceAll("col1",
                                               FieldsColumn.OWNER_NAME.getColumnName());
            ordersDelCmd += infoCmd.replaceAll("col1",
                                               OrdersColumn.TABLE_PATH.getColumnName());

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

    /**************************************************************************
     * Load the contents of a single database table and display the data in a
     * table editor. Do not load the table if it is already open for editing.
     * If the table is already open, then the editor for this table is brought
     * to the foreground. This command is executed in a separate thread since
     * it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the database command completes
     * execution
     *
     * @param tablePath
     *            table path in the format
     *            rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
     *            The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top
     *            level structure table from which this table descends. The
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy
     *
     * @param currentEditorDlg
     *            editor dialog to open the table in; null to open the table in
     *            a new editor dialog
     *
     * @return SwingWorker reference for this thread
     *************************************************************************/
    protected SwingWorker<?, ?> loadTableDataInBackground(String tablePath,
                                                          CcddTableEditorDialog currentEditorDlg)
    {
        return loadTableDataInBackground(new String[] {tablePath},
                                         currentEditorDlg);
    }

    /**************************************************************************
     * Load the contents of an array of database tables and display the data in
     * a table editor. Do not load tables that are already open for editing. If
     * no table is loaded, but one in the list is already open, then the editor
     * for this table is brought to the foreground. This command is executed in
     * a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue
     * to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param tablePaths
     *            array of paths of the tables to load in the format
     *            rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
     *            The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top
     *            level structure table from which this table descends. The
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy
     *
     * @param currentEditorDlg
     *            table editor dialog to open the table in; null to open the
     *            table in a new editor dialog
     *
     * @return SwingWorker reference for this thread
     *************************************************************************/
    protected SwingWorker<?, ?> loadTableDataInBackground(final String[] tablePaths,
                                                          final CcddTableEditorDialog currentEditorDlg)
    {
        // Execute the command in the background
        return CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            private final List<TableInformation> tableInformation = new ArrayList<TableInformation>();

            CcddTableEditorDialog tableEditorDlg = null;
            CcddTableEditorHandler tableEditor = null;

            // Get the component over which any dialogs need to be centered
            Component parent = (currentEditorDlg == null)
                                                         ? ccddMain.getMainFrame()
                                                         : currentEditorDlg;

            /******************************************************************
             * Load database table command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Get the list of root structure tables
                    List<String> rootStructure = getRootStructures(parent);

                    // Step through each table
                    for (int index = 0; index < tablePaths.length; index++)
                    {
                        boolean isOpen = false;

                        // Step through the open editor dialogs
                        for (CcddTableEditorDialog editorDlg : ccddMain.getTableEditorDialogs())
                        {
                            // Step through each individual editor in the
                            // editor dialog
                            for (CcddTableEditorHandler editor : editorDlg.getTableEditors())
                            {
                                // Check if the table path matches the editor
                                // table
                                if (tablePaths[index].equals(editor.getTableInformation().getTablePath()))
                                {
                                    // Store the editor dialog and editor for
                                    // the table, and stop searching
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
                            // Get the prototype + variable name
                            String protoVarName = TableInformation.getProtoVariableName(tablePaths[index]);

                            // Get the information from the database for the
                            // specified table
                            TableInformation tableInfo = loadTableData(tablePaths[index],
                                                                       rootStructure.contains(protoVarName),
                                                                       true,
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

                    // Check if at least one table was successfully loaded
                    if (!tableInformation.isEmpty())
                    {
                        // Check if no existing editor dialog was supplied
                        if (currentEditorDlg == null)
                        {
                            // Display the table contents in a new table editor
                            // dialog
                            ccddMain.getTableEditorDialogs().add(new CcddTableEditorDialog(ccddMain,
                                                                                           tableInformation));
                        }
                        // A table editor dialog is supplied
                        else
                        {
                            // Display the table contents in an existing table
                            // editor dialog
                            currentEditorDlg.addTablePanes(tableInformation);
                        }
                    }
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated
                    // error
                    CcddUtilities.displayException(e, parent);
                }
            }

            /******************************************************************
             * Load database table command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if no table data was loaded due to the table(s)
                // already being open in an editor
                if (tableInformation.isEmpty() && tableEditorDlg != null)
                {
                    // Bring the editor dialog to the foreground
                    tableEditorDlg.toFront();

                    // Step through each tab in the editor dialog
                    for (int index = 0; index < tableEditorDlg.getTabbedPane().getTabCount(); index++)
                    {
                        // Check if the tab name matches the last table name in
                        // the list of those to be opened
                        if (tableEditorDlg.getTabbedPane().getComponentAt(index).equals(tableEditor.getFieldPanel()))
                        {
                            // Bring the editor tab to the front and stop
                            // searching
                            tableEditorDlg.getTabbedPane().setSelectedIndex(index);
                            break;
                        }
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Perform the database query to load the contents of a database table. The
     * data is sorted in ascending numerical order based on the index (primary
     * key) column
     *
     * @param tablePath
     *            table path in the format
     *            rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
     *            The table path for a non-structure table is simply the root
     *            table name. For a structure table the root table is the top
     *            level structure table from which this table descends. The
     *            first data type/variable name pair is from the root table,
     *            with each succeeding pair coming from the next level down in
     *            the structure's hierarchy
     *
     * @param isRootStructure
     *            true if the table is a root table of type 'structure'
     *
     * @param loadDescription
     *            true to load the table's description
     *
     * @param loadColumnOrder
     *            true to load the table's column order
     *
     * @param loadFieldInfo
     *            true to retrieve the data field information to include with
     *            the table information; false to not load the field
     *            information
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return TableInformation class containing the table data from the
     *         database. If the error flag is set the an error occurred and the
     *         data is invalid
     *************************************************************************/
    protected TableInformation loadTableData(String tablePath,
                                             boolean isRootStructure,
                                             boolean loadDescription,
                                             boolean loadColumnOrder,
                                             boolean loadFieldInfo,
                                             Component parent)
    {
        // Create an empty table information class
        TableInformation tableInfo = new TableInformation(tablePath);

        // Strip the variable name, if present, from the table name
        String tableName = tableInfo.getPrototypeName();

        // Convert the table name to lower case. PostgreSQL automatically does
        // this, so it's done here just to differentiate the table name from
        // the database commands in the event log
        String dbTableName = tableName.toLowerCase();

        // Check if the table exists in the database
        if (isTableExists(dbTableName, parent))
        {
            try
            {
                // Get the table comment
                String[] comment = queryDataTableComment(tableName, parent);

                // Get the table type definition for this table
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(comment[TableCommentIndex.TYPE.ordinal()]);

                // Get a comma-separated list of the columns for this table's
                // type
                String columnNames = CcddUtilities.convertArrayToString(typeDefn.getColumnNamesDatabase());

                // Get the table's row information for the specified columns.
                // The table must have all of its table type's columns or else
                // it fails to load
                ResultSet rowData = dbCommand.executeDbQuery("SELECT "
                                                             + columnNames
                                                             + " FROM "
                                                             + dbTableName
                                                             + " ORDER BY "
                                                             + DefaultColumn.ROW_INDEX.getDbName()
                                                             + ";",
                                                             parent);

                // Create a list to contain the database table rows
                List<String[]> dbRows = new ArrayList<String[]>();

                // Step through each of the query results
                while (rowData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[typeDefn.getColumnCountDatabase()];

                    // Step through each column in the row
                    for (int column = 0; column < typeDefn.getColumnCountDatabase(); column++)
                    {
                        // Add the column value to the array. Note that the
                        // first column's index in the database is 1, not 0
                        columnValues[column] = rowData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Add the row data to the list
                    dbRows.add(columnValues);
                }

                rowData.close();

                // Create the table information handler for this table
                tableInfo = new TableInformation(comment[TableCommentIndex.TYPE.ordinal()],
                                                 tablePath,
                                                 dbRows.toArray(new String[0][0]),
                                                 (loadColumnOrder
                                                                 ? queryColumnOrder(tablePath,
                                                                                    comment[TableCommentIndex.TYPE.ordinal()],
                                                                                    parent)
                                                                 : ""),
                                                 (loadDescription
                                                                 ? queryTableDescription(tablePath,
                                                                                         parent)
                                                                 : ""),
                                                 isRootStructure,
                                                 (loadFieldInfo
                                                               ? retrieveInformationTable(InternalTable.FIELDS,
                                                                                          parent).toArray(new String[0][0])
                                                               : null));

                // Get the index of the variable name and data type columns
                int varNameIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                int dataTypeIndex = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);

                // Check if the variable name and data type columns exist, and
                // if the table has a path. If so it may have values in the
                // custom values table that must be loaded
                if (varNameIndex != -1
                    && dataTypeIndex != -1
                    && tablePath.contains(","))
                {
                    // Place double back slashes before each square brace
                    // character in an array index so that the brackets are
                    // interpreted correctly in the query's regular
                    // expression comparisons
                    tablePath = tablePath.replaceAll("\\[(\\d+)\\]",
                                                     "\\\\\\\\[$1\\\\\\\\]");

                    // Get the rows from the custom values table that match
                    // the specified parent table and variable path. These
                    // values replace those loaded for the prototype of
                    // this table
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
                        // Get the variable name that will have its value
                        // replaced
                        String variableName = rowData.getString(ValuesColumn.TABLE_PATH.getColumnName());

                        // Get the index of the last data type/variable name
                        // separator character (if present)
                        int varIndex = variableName.lastIndexOf(".");

                        // Check if a variable name exists
                        if (varIndex != -1)
                        {
                            // Get the row index for the referenced variable
                            int row = typeDefn.getRowIndexByColumnValue(tableInfo.getData(),
                                                                        variableName.substring(varIndex + 1),
                                                                        varNameIndex);

                            // Check if the table contains the variable and if
                            // the data type of the variable in the table
                            // matches the data type in the path from the
                            // custom values table
                            if (row != -1
                                && tableInfo.getData()[row][dataTypeIndex].equals(variableName.subSequence(variableName.lastIndexOf(",")
                                                                                                           + 1,
                                                                                                           varIndex)))
                            {
                                // Get the index of the column that will have
                                // its data replaced
                                int column = typeDefn.getColumnIndexByUserName(rowData.getString(ValuesColumn.COLUMN_NAME.getColumnName()));

                                // Check if the table contains the column
                                if (column != -1)
                                {
                                    // Replace the value in the table with the
                                    // one from the custom values table
                                    tableInfo.getData()[row][column] = rowData.getString(ValuesColumn.VALUE.getColumnName());
                                }
                            }
                        }
                    }

                    rowData.close();
                }
            }
            catch (SQLException se)
            {
                // Inform the user that loading the table failed
                eventLog.logFailEvent(parent,
                                      "Cannot load table '"
                                          + tableInfo.getProtoVariableName()
                                          + "'; cause '"
                                          + se.getMessage()
                                          + "'",
                                      "<html><b>Cannot load table '</b>"
                                          + tableInfo.getProtoVariableName()
                                          + "<b>'");
            }
            catch (Exception e)
            {
                // Display a dialog providing details on the unanticipated
                // error
                CcddUtilities.displayException(e, parent);
            }
        }

        return tableInfo;
    }

    /**************************************************************************
     * Perform the database query to load the rows from the custom values table
     * that match the specified column name and column value
     *
     * @param columnName
     *            name to match in the custom values table 'column name' column
     *
     * @param columnValue
     *            value to match in the custom values table 'value' column;
     *            null or blank to match any value
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List containing arrays with the row data (table path, column
     *         name, and value) from the custom values table for those rows
     *         that match the specified column name and column value
     *************************************************************************/
    protected List<String[]> getCustomValues(String columnName,
                                             String columnValue,
                                             Component parent)
    {
        List<String[]> customValues = new ArrayListMultiple();

        try
        {
            // Get the row data from the custom values table for all
            // columns
            // with a matching column name and column value
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

    /**************************************************************************
     * Store the rate parameters in the project database and update the
     * database structure-related functions
     *
     * @param parent
     *            component calling this method, used for positioning any error
     *            dialogs
     *************************************************************************/
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
            // Build the string containing the rate name and parameters. The
            // name is placed within double quotes
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
        setTableComment(InternalTable.TLM_SCHEDULER.getTableName(),
                        comment,
                        parent);
    }

    /**************************************************************************
     * Create a list of all prototype tables with their child tables
     * (prototypes and instances), and primitive variables (if specified). The
     * table must contain all of the protected columns defined for a structure
     * in order for its members to be determined. Non-structure tables are
     * included in the returned list, but by definition have no members
     *
     * @param memberType
     *            Type of table members to load: TABLES_ONLY to exclude
     *            primitive variables or INCLUDE_PRIMITIVES to include tables
     *            and primitive variables
     *
     * @param sortByName
     *            true to return the table members in alphabetical order (e.g.,
     *            for use in a tree); false to return the members sorted by row
     *            index (e.g., for use in determining the variable offsets in
     *            the structure)
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List containing the table member information. For structure
     *         tables the member tables are included, along with primitive
     *         variables (if specified), sorted by variable name or row index
     *         as specified
     *************************************************************************/
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

            // Get the table members of all structure tables by extracting the
            // values from the table's data type and variable name columns, if
            // present, sorted by variable name or table index. Non-structure
            // tables and structure tables with no rows are skipped
            ResultSet rowData = dbCommand.executeDbQuery("SELECT * FROM "
                                                         + (sortByName
                                                                      ? "get_table_members_by_name();"
                                                                      : "get_table_members_by_index();"),
                                                         parent);

            // Create a list to contain the database table member data types
            // variable names, rates, and bit lengths
            List<String> dataTypes;
            List<String> variableNames;
            List<String> bitLengths;
            List<String[]> rates;
            List<String[]> enumerations;

            // Set the flag based on if any data was returned by the database
            // query
            boolean doLoop = rowData.next();

            // Step through the query results
            while (doLoop)
            {
                // Initialize the data type, variable, bit length, rate(s), and
                // enumeration(s) lists
                dataTypes = new ArrayList<String>();
                variableNames = new ArrayList<String>();
                bitLengths = new ArrayList<String>();
                rates = new ArrayList<String[]>();
                enumerations = new ArrayList<String[]>();

                // Get the table name for this query table row
                String tableName = rowData.getString(1);

                do
                {
                    // Get the data type, variable name, bit length, rate(s),
                    // and enumeration(s) from this query row
                    String dataType = rowData.getString(2);
                    String variableName = rowData.getString(3);
                    String bitLength = rowData.getString(4);
                    String[] rate = rowData.getString(5).split(",", rateHandler.getNumRateColumns());
                    String[] enumeration = rowData.getString(6).split(Pattern.quote(ENUMERATION_SEPARATOR),
                                                                      tableTypeHandler.getStructEnumColNames(true).size());

                    // Check if a data type and variable name exist, and that
                    // the data type is not a primitive type (i.e., this is a
                    // structure) or if primitive types are to be included
                    if (dataType != null
                        && !dataType.isEmpty()
                        && variableName != null
                        && !variableName.isEmpty()
                        && (!dataTypeHandler.isPrimitive(dataType)
                        || memberType == TableMemberType.INCLUDE_PRIMITIVES))
                    {
                        // Get the number of variable names in the list
                        int addIndex = variableNames.size();

                        // Check if a variable name is already in the list and
                        // that the data is sorted by variable name
                        if (addIndex != 0 && sortByName)
                        {
                            // Get the name of the last variable added to the
                            // list
                            String lastVarName = variableNames.get(addIndex - 1);

                            // Check if the current and previous variables are
                            // members of the same array
                            if (ArrayVariable.isArrayMember(variableName)
                                && ArrayVariable.isArrayMember(lastVarName)
                                && ArrayVariable.removeArrayIndex(variableName)
                                                .equals(ArrayVariable.removeArrayIndex(lastVarName)))
                            {
                                // The PostgreSQL function that obtains the
                                // table members sorted by variable name treats
                                // array indices as strings, so array member
                                // [10] appears immediately after member [1]
                                // instead of after [9]. This code section
                                // determines the position in the list where
                                // the current array member should be placed
                                // relative to those members already in the
                                // list, sorted numerically by array dimension
                                // value(s)

                                boolean notFound = true;

                                do
                                {
                                    // Compare the two array members
                                    // numerically, by array dimension value(s)
                                    int result = ArrayVariable.compareTo(variableName,
                                                                         variableNames.get(addIndex - 1));

                                    // Check if the array member's dimension
                                    // value(s) places it before the comparison
                                    // array member
                                    if (result < 0)
                                    {
                                        // Decrement the variable insertion
                                        // index
                                        addIndex--;
                                    }
                                    // Check if the array member's dimension
                                    // value(s) places it after the comparison
                                    // array member
                                    else if (result > 0)
                                    {
                                        // Set the flag indicating that the
                                        // position of the current variable in
                                        // the list is located and stop
                                        // checking the dimension values
                                        notFound = false;
                                    }
                                } while (notFound && addIndex > 0);
                                // Continue to adjust the insertion index as
                                // long as the current variable's array indices
                                // places it prior to the that of the variable
                                // at the current insertion index. The test for
                                // addIndex > 0 accounts for the possibility
                                // that array members are missing in the table
                            }
                        }

                        // Add the data type, variable name, bit length,
                        // rate(s), and enumeration(s) to the lists for this
                        // table
                        dataTypes.add(addIndex, dataType);
                        variableNames.add(addIndex, variableName);
                        bitLengths.add(addIndex, bitLength);
                        rates.add(addIndex, rate);
                        enumerations.add(addIndex, enumeration);
                    }

                    // Go to the next row in the query results; set the flag to
                    // true if the row exists
                    doLoop = rowData.next();

                    // Continue to loop while rows exist to process in the
                    // query table and the table name for the new row remains
                    // the same
                } while (doLoop && tableName.equals(rowData.getString(1)));

                // Get the comment array for this table
                String[] comment = getTableComment(tableName, comments);

                // Add the table name, table type, and the table's member
                // information to the members list
                tableMembers.add(new TableMembers(comment[TableCommentIndex.NAME.ordinal()],
                                                  comment[TableCommentIndex.TYPE.ordinal()],
                                                  dataTypes,
                                                  variableNames,
                                                  bitLengths,
                                                  rates,
                                                  enumerations));
            }

            rowData.close();

            // Create storage for the tables not added above; i.e., for
            // non-structure tables and for structure tables containing no rows
            List<TableMembers> newMembers = new ArrayList<TableMembers>();

            // Step through the table names
            for (String tableName : tableNames)
            {
                boolean isFound = false;

                // Step through the structure table members
                for (TableMembers member : tableMembers)
                {
                    // Check if the table name matches the one in the member
                    // list
                    if (tableName.equals(member.getTableName()))
                    {
                        // Set the flag to indicate this table is already in
                        // the member list and stop searching
                        isFound = true;
                        break;
                    }
                }

                // Check if the table is not already in the member list
                if (!isFound)
                {
                    // Get the comment array for this table
                    String[] comment = getTableComment(tableName.toLowerCase(),
                                                       comments);

                    // Add the table to the member list with empty data type,
                    // variable name, bit length, and rate lists
                    newMembers.add(new TableMembers(tableName,
                                                    comment[TableCommentIndex.TYPE.ordinal()],
                                                    new ArrayList<String>(),
                                                    new ArrayList<String>(),
                                                    new ArrayList<String>(),
                                                    new ArrayList<String[]>(),
                                                    new ArrayList<String[]>()));
                }
            }

            // Add the new members to the member list. The list now contains a
            // reference to all tables
            tableMembers.addAll(newMembers);

            // Sort the table members by table name in ascending order
            Collections.sort(tableMembers, new Comparator<TableMembers>()
            {
                /**************************************************************
                 * Compare the table names of two member definitions. Force
                 * lower case to eliminate case differences in the comparison
                 *************************************************************/
                @Override
                public int compare(TableMembers mem1, TableMembers mem2)
                {
                    return mem1.getTableName().toLowerCase().compareTo(mem2.getTableName().toLowerCase());
                }
            });
        }
        catch (SQLException se)
        {
            // Inform the user that loading the table members failed
            eventLog.logFailEvent(parent,
                                  "Cannot load table members; cause '"
                                      + se.getMessage()
                                      + "'",
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

    /**************************************************************************
     * Add, modify, and/or delete data in a table. This command is executed in
     * a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue
     * to update. The GUI menu commands, however, are disabled until the
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
     *            true to make the changes to other tables; false to only make
     *            changes to tables other than the one in which the changes
     *            originally took place
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the
     *            internal tables. This is used during a data type update where
     *            only the data type name has changed in order to speed up the
     *            operation
     *
     * @param updateDescription
     *            true to update the table description from the table
     *            information; false to not change the table description
     *
     * @param updateColumnOrder
     *            true to update the table column order from the table
     *            information; false to not change the table column order
     *
     * @param updateFieldInfo
     *            true to update the table data fields from the table
     *            information; false to not change the table data fields
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a
     *            reference to the current data type handler) if the change
     *            does not originate from the data type editor
     *
     * @param newMacroHandler
     *            macro handler with macro modifications. null (or a reference
     *            to the current macro handler) if the change does not
     *            originate from the macro editor
     *
     * @param parent
     *            reference to the GUI component over which any error dialogs
     *            should be centered
     *************************************************************************/
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
                                               final CcddMacroHandler newMacroHandler,
                                               final Component parent)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Modify table data command
             *****************************************************************/
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
                                newMacroHandler,
                                parent);
            }
        });
    }

    /**************************************************************************
     * Add, modify, and/or delete data in a table. If the table is a prototype
     * then its database table is altered; if the table is an instance then the
     * changes are made to the custom values table
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
     *            true to make the changes to other data tables; false to only
     *            make changes to tables other than the one in which the
     *            changes originally took place
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the
     *            internal tables. This is used during a data type update where
     *            only the data type name has changed in order to speed up the
     *            operation
     *
     * @param updateDescription
     *            true to update the table description from the table
     *            information; false to not change the table description
     *
     * @param updateColumnOrder
     *            true to update the table column order from the table
     *            information; false to not change the table column order
     *
     * @param updateFieldInfo
     *            true to update the table data fields from the table
     *            information; false to not change the table data fields
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a
     *            reference to the current data type handler) if the change
     *            does not originate from the data type editor
     *
     * @param newMacroHandler
     *            macro handler with macro modifications. null (or a reference
     *            to the current macro handler) if the change does not
     *            originate from the macro editor
     *
     * @param parent
     *            reference to the GUI component over which any error dialogs
     *            should be centered
     *
     * @return true if an error occurs while updating the table
     *************************************************************************/
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
                                      CcddMacroHandler newMacroHandler,
                                      Component parent)
    {
        boolean errorFlag = false;
        List<Integer> newKeys = new ArrayList<Integer>();

        try
        {
            CcddTableTreeHandler tableTree = null;
            List<String> rootTables = null;
            ToolTipTreeNode orgTableNode = null;
            updateLinks = false;
            addLinkHandler = null;

            // Get the name of the table to modify and convert the table name
            // to lower case. PostgreSQL automatically does this, so it's done
            // here just to differentiate the table name from the database
            // commands in the event log
            String dbTableName = tableInfo.getPrototypeName().toLowerCase();

            // Get the table type definition
            TypeDefinition typeDefinition = tableTypeHandler.getTypeDefinition(tableInfo.getType());

            // Check if references in the internal tables are to be updated and
            // the table represents a structure
            if (!skipInternalTables && typeDefinition.isStructure())
            {
                // Create the table tree
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                     parent);

                // Check if there are any additions or modifications
                if (!additions.isEmpty() || !modifications.isEmpty())
                {
                    // Get the list of root tables
                    rootTables = tableTree.getTableTreePathList(null,
                                                                (ToolTipTreeNode) tableTree.getRootNode(),
                                                                tableTree.getHeaderNodeLevel());
                }

                // Check if the table is a prototype
                if (tableInfo.isPrototype())
                {
                    // Copy the table tree node for the prototype table. This
                    // preserves a copy of the table's variables before the
                    // changes are applied
                    orgTableNode = copyPrototypeTableTreeNode(tableInfo.getPrototypeName(),
                                                              tableTree);
                }
            }

            // Build the commands to add, modify, and delete table rows
            String command = buildAdditionCommand(tableInfo,
                                                  additions,
                                                  dbTableName,
                                                  typeDefinition,
                                                  rootTables,
                                                  skipInternalTables)
                             + buildModificationCommand(tableInfo,
                                                        modifications,
                                                        typeDefinition,
                                                        newDataTypeHandler,
                                                        newMacroHandler,
                                                        tableTree,
                                                        rootTables,
                                                        skipInternalTables)
                             + buildDeletionCommand(tableInfo,
                                                    deletions,
                                                    dbTableName,
                                                    typeDefinition,
                                                    tableTree,
                                                    skipInternalTables);

            // Get the table's description
            String description = tableInfo.getDescription();

            // Check if this table is an instance and that the description is
            // the same as the one for the prototype of this table
            if (!tableInfo.isPrototype()
                && description.equals(queryTableDescription(tableInfo.getPrototypeName(),
                                                            parent)))
            {
                // Set the description to a blank since it inherits it from the
                // prototype
                description = "";
            }

            // Combine the table, data fields table, table description, and
            // column order update commands, then execute the commands
            dbCommand.executeDbUpdate(command
                                      + (updateFieldInfo ?
                                                        modifyFieldsCommand(tableInfo.getTablePath(),
                                                                            tableInfo.getFieldHandler().getFieldInformation())
                                                        : "")
                                      + (updateDescription ?
                                                          buildTableDescription(tableInfo.getTablePath(),
                                                                                description)
                                                          : "")
                                      + (updateColumnOrder ?
                                                          buildColumnOrder(tableInfo.getTablePath(),
                                                                           tableInfo.getColumnOrder())
                                                          : ""),
                                      parent);

            // Check if references in the internal tables are to be updated
            if (!skipInternalTables && typeDefinition.isStructure())
            {
                // Check if the table is a structure prototype and that the
                // table had one or more variables to begin with
                if (tableInfo.isPrototype() && tableInfo.getData().length > 0)
                {
                    // Build the command delete bit-packed variable references
                    // in the links and telemetry scheduler tables that changed
                    // due to the table modifications
                    command = updateLinksAndTlmForPackingChange(tableTree,
                                                                orgTableNode,
                                                                parent);

                    // Check if there are any bit-packed variable references to
                    // delete
                    if (!command.isEmpty())
                    {
                        // Delete invalid bit-packed variable references
                        dbCommand.executeDbUpdate(command, parent);
                    }

                    // Check if the link definitions changed
                    if (updateLinks)
                    {
                        // Store the updated link definitions in the project
                        // database
                        storeInformationTable(InternalTable.LINKS,
                                              addLinkHandler.getLinkDefinitions(),
                                              null,
                                              parent);
                    }
                }

                // Execute the command to reset the rate for links that no
                // longer contain any variables
                dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);
            }

            // Check if this is a prototype table and that new rows were added.
            // New rows have database-generated primary key values that aren't
            // reflected in the table editor, so these must be extracted and
            // passed to the editor
            if (tableInfo.isPrototype() && !additions.isEmpty())
            {
                // Get the table's primary key values
                ResultSet keySet = dbCommand.executeDbQuery("SELECT "
                                                            + DefaultColumn.PRIMARY_KEY.getDbName()
                                                            + ", "
                                                            + DefaultColumn.ROW_INDEX.getDbName()
                                                            + " from "
                                                            + dbTableName
                                                            + " ORDER BY "
                                                            + DefaultColumn.ROW_INDEX.getDbName(),
                                                            parent);

                // Step through each row in the table
                while (keySet.next())
                {
                    // Store the row's primary key value
                    newKeys.add(keySet.getInt(1));
                }

                keySet.close();
            }

            // Log that inserting data into the table succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              "Table '"
                                  + tableInfo.getProtoVariableName()
                                  + "' data modified");
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
            // Make changes to any open table editors
            CcddTableEditorDialog.doTableModificationComplete(ccddMain,
                                                              newKeys,
                                                              tableInfo,
                                                              additions,
                                                              modifications,
                                                              deletions,
                                                              forceUpdate);
        }

        return errorFlag;
    }

    /**************************************************************************
     * Build the command to add table rows. Only prototype tables can have a
     * row added
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
     * @param typeDefinition
     *            table type definition
     *
     * @param rootTables
     *            list containing the names of the root tables
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the
     *            internal tables. This is used during a data type update where
     *            only the data type name has changed in order to speed up the
     *            operation
     *
     * @return Table row addition command
     *************************************************************************/
    private String buildAdditionCommand(TableInformation tableInfo,
                                        List<TableModification> additions,
                                        String dbTableName,
                                        TypeDefinition typeDefinition,
                                        List<String> rootTables,
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

            // Create the insert table data command. The array of column names
            // is converted to a string
            addCmd.append("INSERT INTO "
                          + dbTableName
                          + " ("
                          + CcddUtilities.convertArrayToString(typeDefinition.getColumnNamesDatabase())
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

                // Remove the ending comma and space, append the closing
                // parenthesis, and add the command to add this row
                addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
                addCmd.append("), ");

                // Check if internal tables are to be updated and the parent
                // table is a structure
                if (!skipInternalTables && typeDefinition.isStructure())
                {
                    // Get the variable name and data type for the variable in
                    // the new row
                    String variableName = add.getRowData()[add.getVariableColumn()].toString();
                    String dataType = add.getRowData()[add.getDataTypeColumn()].toString();

                    // Get the variable path
                    String newVariablePath = tableInfo.getTablePath()
                                             + ","
                                             + dataType
                                             + "."
                                             + variableName;

                    // Check if the data type isn't a primitive (i.e., it must
                    // be a structure table) and this structure is a root table
                    if (!dataTypeHandler.isPrimitive(dataType)
                        && rootTables.contains(dataType))
                    {
                        // If the structure chosen as the variable's data type
                        // is a root structure, then any custom values for this
                        // the root structure (which becomes a child structure)
                        // are transferred to its new parent structure.
                        // References in the other internal tables are also
                        // changed to the structure's new path as a child
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
                                            + "(,|$)', E'"
                                            + newVariablePath
                                            + "\\\\1'); ");
                        ordersAddCmd.append("UPDATE "
                                            + InternalTable.ORDERS.getTableName()
                                            + " SET "
                                            + OrdersColumn.TABLE_PATH.getColumnName()
                                            + " = regexp_replace("
                                            + OrdersColumn.TABLE_PATH.getColumnName()
                                            + ", E'^"
                                            + dataType
                                            + "(,|$)', E'"
                                            + newVariablePath
                                            + "\\\\1'); ");
                        String orgPathWithChildren = dataType
                                                     + "(,"
                                                     + PATH_IDENT
                                                     + ")?";
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

                        // References in the links and telemetry scheduler to
                        // the root structure and its children are not
                        // automatically amended to include the new parent
                        // structure path, but are instead removed
                        linksDelCmd = deleteLinkPathRef("^"
                                                        + dataType
                                                        + "(?:,|\\\\.|$)",
                                                        linksDelCmd);
                        tlmDelCmd = deleteTlmPathRef(dataType
                                                     + "(?:,|\\\\.|$)",
                                                     tlmDelCmd);
                    }

                    // Check if the added variable is a string array member
                    if (dataTypeHandler.isString(dataType)
                        && ArrayVariable.isArrayMember(variableName))
                    {
                        // Get the string array definition
                        String stringArrayDefn = ArrayVariable.removeStringSize(newVariablePath);

                        // Check if this string array hasn't already been
                        // processed
                        if (!stringArrays.contains(stringArrayDefn))
                        {
                            // Add the string array definition to the list so
                            // that it's only processed once (in the event
                            // multiple members of the array are added)
                            stringArrays.add(stringArrayDefn);

                            // Update the links definitions, if needed, with
                            // the new string array member
                            updateLinksForStringMemberAddition(tableInfo.getTablePath(),
                                                               dataType,
                                                               variableName);

                            // Remove all references to the string array from
                            // the telemetry scheduler table
                            tlmDelCmd = deleteTlmPathRef(stringArrayDefn
                                                         + "(?:,|:|$)",
                                                         tlmDelCmd);
                        }
                    }
                }
            }

            // Check if a change to the links table exists
            if (linksDelCmd.length() != 0)
            {
                // Terminate the command
                linksDelCmd.append("; ");
            }

            // Check if a change to the telemetry scheduler table exists
            if (tlmDelCmd.length() != 0)
            {
                // Terminate the command
                tlmDelCmd.append("; ");
            }

            // Remove the ending comma and space, and append the command's
            // closing semi-colon
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

    /**************************************************************************
     * Build the commands to modify the table
     *
     * @param tableInfo
     *            table information
     *
     * @param modifications
     *            list of row modification information
     *
     * @param typeDefinition
     *            table type definition
     *
     * @param newDataTypeHandler
     *            data type handler with data type modifications. null (or a
     *            reference to the current data type handler) if the change
     *            does not originate from the data type editor
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @param rootTables
     *            list containing the names of the root tables
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the
     *            internal tables. This is used during a data type update where
     *            only the data type name has changed in order to speed up the
     *            operation
     *
     * @return Table row modification command
     *************************************************************************/
    private String buildModificationCommand(TableInformation tableInfo,
                                            List<TableModification> modifications,
                                            TypeDefinition typeDefinition,
                                            CcddDataTypeHandler newDataTypeHandler,
                                            CcddMacroHandler newMacroHandler,
                                            CcddTableTreeHandler tableTree,
                                            List<String> rootTables,
                                            boolean skipInternalTables)
    {
        StringBuilder modCmd = new StringBuilder("");

        // Check that there are modifications
        if (!modifications.isEmpty())
        {
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");

            // Check if no updated data type handler is provided. This implies
            // the modifications are not due to an update in the data type
            // editor
            if (newDataTypeHandler == null)
            {
                // Set the updated data type handler to use the current handler
                newDataTypeHandler = dataTypeHandler;
            }

            // Check if no updated macro handler is provided. This implies the
            // modifications are not due to an update in the macro editor
            if (newMacroHandler == null)
            {
                // Set the updated macro handler to use the current handler
                newMacroHandler = macroHandler;
            }

            // Step through each modification
            for (TableModification mod : modifications)
            {
                // Check if this is a prototype table (modifications are made
                // to the table)
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
                    modCmd.append("UPDATE "
                                  + tableInfo.getProtoVariableName().toLowerCase()
                                  + " SET ");

                    // Step through each changed column
                    for (int column = 0; column < mod.getRowData().length; column++)
                    {
                        // Check if the column value changed
                        if (mod.getOriginalRowData()[column] == null
                            || !mod.getOriginalRowData()[column].equals(mod.getRowData()[column]))
                        {
                            // Build the command to change the column value
                            modCmd.append(typeDefinition.getColumnNamesDatabase()[column]
                                          + " = "
                                          + delimitText(mod.getRowData()[column])
                                          + ", ");
                        }
                    }

                    // Check if the internal tables are to be updated and the
                    // table represents a structure
                    if (!skipInternalTables && typeDefinition.isStructure())
                    {
                        // Get the original and current variable names, data
                        // types, array sizes, and bit lengths
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
                                // Set the flag to indicate that the rate
                                // changed and stop searching
                                rateChanged = true;
                                break;
                            }
                        }

                        // Set the flags to indicate if the variable name, data
                        // type, array size, and bit length values have changed
                        boolean variableChanged = !oldVariableName.equals(newVariableName);
                        boolean dataTypeChanged = !oldDataType.equals(newDataType);
                        boolean arraySizeChanged = !oldArraySize.equals(newArraySize);
                        boolean bitLengthChanged = !oldBitLength.equals(newBitLength);

                        // Check if the variable name, data type, array size,
                        // bit length, or rate column value(s) changed; this
                        // change must be propagated to the instances of this
                        // prototype and their entries in the internal tables
                        if (variableChanged
                            || dataTypeChanged
                            || arraySizeChanged
                            || bitLengthChanged
                            || rateChanged)
                        {
                            // Check if the data type has been changed, the new
                            // data type is a structure, and this structure is
                            // a root table
                            if (dataTypeChanged
                                && !newDataTypeHandler.isPrimitive(newDataType)
                                && rootTables.contains(newDataType))
                            {
                                // Get the variable path
                                String newVariablePath = tableInfo.getTablePath()
                                                         + ","
                                                         + newDataType
                                                         + "."
                                                         + newVariableName;

                                // If the structure chosen as the variable's
                                // data type is a root structure, then any
                                // custom values for this the root structure
                                // (which becomes a child structure) are
                                // transferred to its new parent structure.
                                // References in the other internal tables are
                                // also changed to the structure's new path as
                                // a child
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

                                // Build the command to copy the data fields
                                // from the table's prototype
                                fieldsModCmd.append("INSERT INTO "
                                                    + InternalTable.FIELDS.getTableName()
                                                    + " SELECT regexp_replace("
                                                    + FieldsColumn.OWNER_NAME.getColumnName()
                                                    + ", E'^"
                                                    + newDataType
                                                    + "(,|$)', E'"
                                                    + newVariablePath
                                                    + "\\\\1')");

                                // Step through each column in the data field
                                // table
                                for (FieldsColumn fldCol : FieldsColumn.values())
                                {
                                    // Check if this isn't the owner name
                                    // column
                                    if (!fldCol.equals(FieldsColumn.OWNER_NAME))
                                    {
                                        // Add the column name to those to be
                                        // copied
                                        fieldsModCmd.append(", "
                                                            + fldCol.getColumnName());
                                    }
                                }

                                // Complete the command to copy the prototype's
                                // fields to the child. Do not copy fields
                                // flagged as being applicable only to root
                                // tables
                                fieldsModCmd.append(" FROM "
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
                                                    + "(,|$)', E'"
                                                    + newVariablePath
                                                    + "\\\\1'); ");
                                String orgPathWithChildren = newDataType
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
                                                   + "\\\\1\\\\3', 'g'); ");

                                // References in the links and telemetry
                                // scheduler to the root structure and its
                                // children are not automatically amended to
                                // include the new parent structure path, but
                                // are instead removed
                                linksDelCmd = deleteLinkPathRef("^"
                                                                + newDataType
                                                                + "(?:,|\\\\.|$)",
                                                                linksDelCmd);
                                tlmDelCmd = deleteTlmPathRef(newDataType
                                                             + "(?:,|\\\\.|$)",
                                                             tlmDelCmd);
                            }

                            // Create a list of table path arrays that are
                            // instances of this prototype table
                            List<Object[]> tablePathList = tableTree.getTableTreePathArray(tableInfo.getPrototypeName());

                            // Step through each table path found
                            for (Object[] path : tablePathList)
                            {
                                boolean isDelLinksAndTlm = false;

                                // Get the variable name path from the table
                                // tree
                                String tablePath = tableTree.getFullVariablePath(path);

                                // Append the original/new data type and/or
                                // name of the variable that's being changed to
                                // the variable path. Escape any PostgreSQL
                                // reserved characters so that the original
                                // path can be used in a regular expression
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

                                // Check if the variable name changed, or if
                                // the data type changed from one primitive to
                                // another primitive. In either case, check
                                // that the array status (is or isn't) remains
                                // unchanged
                                if ((variableChanged

                                    || (dataTypeChanged
                                        && dataTypeHandler.isPrimitive(oldDataType)
                                        && newDataTypeHandler.isPrimitive(newDataType)))

                                    && !(arraySizeChanged
                                    && (oldArraySize.isEmpty()
                                    || newArraySize.isEmpty())))
                                {
                                    // Create the command to update the
                                    // internal tables for instances of
                                    // non-array member variables of the
                                    // prototype table
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
                                                       + "\\\\1\\\\3', 'g'); ");

                                    // Check if the data type, bit length, and
                                    // rate didn't also change (updates to the
                                    // links and telemetry scheduler tables due
                                    // to changes to any of these are handled
                                    // elsewhere)
                                    if (!dataTypeChanged
                                        && !bitLengthChanged
                                        && !rateChanged)
                                    {
                                        // Create the command to update the
                                        // links table for instances of
                                        // variables of the prototype table
                                        linksModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                    newVariablePath,
                                                                                    InternalTable.LINKS.getTableName(),
                                                                                    LinksColumn.MEMBER.getColumnName(),
                                                                                    "",
                                                                                    "",
                                                                                    true));
                                        // Since the variable still fits
                                        // within any message in the
                                        // telemetry scheduler table to
                                        // which it's assigned just change
                                        // all references to the variable
                                        tlmModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                  newVariablePath,
                                                                                  InternalTable.TLM_SCHEDULER.getTableName(),
                                                                                  TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                                  "(.*" + tlmSchSeparator + ")",
                                                                                  "\\\\1",
                                                                                  true));
                                    }
                                }

                                // Check if the bit length changed, but not the
                                // data type or rate
                                if (bitLengthChanged
                                    && !dataTypeChanged
                                    && !rateChanged)
                                {
                                    // Append the bit lengths to the variable
                                    // paths
                                    String orgVarPathEscBit = orgVarPathEsc
                                                              + (oldBitLength.isEmpty()
                                                                                       ? ""
                                                                                       : ":" + oldBitLength);
                                    String newVariablePathBit = newVariablePath
                                                                + (newBitLength.isEmpty()
                                                                                         ? ""
                                                                                         : ":" + newBitLength);

                                    // Create the command to update the
                                    // links and telemetry scheduler tables for
                                    // instances of variables of the prototype
                                    // table. If bit-packing changed due to the
                                    // bit length update then the affected
                                    // variables are subsequently removed from
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

                                // Check if the data type changed from a
                                // structure to either a primitive or another
                                // structure
                                if (dataTypeChanged
                                    && !dataTypeHandler.isPrimitive(oldDataType))
                                {
                                    // Create the command to delete references
                                    // to any children of the original
                                    // structure path and change the data type
                                    // for references to the structure itself
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

                                    // Build a regular expression for
                                    // locating references to the original
                                    // variable path
                                    String pathMatch = orgVarPathEsc + "(?:,|$)";

                                    // Remove all references to the
                                    // structure and its children
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
                                                       + "', E'', 'g'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'"
                                                       + assnsSeparator
                                                       + orgPathWithChildren
                                                       + "', E'', 'g'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'^"
                                                       + assnsSeparator
                                                       + "', E'', 'g'); ");

                                    // Check if the rate didn't change as well
                                    // (if the rate changed then updates to the
                                    // links and telemetry scheduler tables are
                                    // handled elsewhere)
                                    if (!rateChanged)
                                    {
                                        // Set the flag to indicate that
                                        // references to the variable and any
                                        // children should be removed from the
                                        // links and telemetry scheduler tables
                                        isDelLinksAndTlm = true;
                                    }
                                }

                                // Check if the variable changed to or from
                                // being an array (changes only to the array
                                // dimension value(s) are handled by the table
                                // row addition and deletion methods)
                                if (arraySizeChanged
                                    && (oldArraySize.isEmpty()
                                    || newArraySize.isEmpty()))
                                {
                                    // Remove all references to the structure's
                                    // children, but not the structure itself
                                    valuesModCmd.append("DELETE FROM "
                                                        + InternalTable.VALUES.getTableName()
                                                        + " WHERE "
                                                        + ValuesColumn.TABLE_PATH.getColumnName()
                                                        + " ~ E'^"
                                                        + orgVarPathEsc
                                                        + "(?:,|\\\\[)"
                                                        + "'; ");

                                    // Build a regular expression for locating
                                    // references to the original variable
                                    // path and any children
                                    String pathMatch = orgVarPathEsc + "(?:,|\\\\[|$)";

                                    // Remove all references to the structure
                                    // and its children
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
                                                                 + "(?:,|\\\\[\\d+\\\\])"
                                                                 + PATH_IDENT;
                                    assnsModCmd.append("UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'^"
                                                       + orgPathWithChildren
                                                       + "', E'', 'g'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'"
                                                       + assnsSeparator
                                                       + orgPathWithChildren
                                                       + "', E'', 'g'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'(?:^|"
                                                       + assnsSeparator
                                                       + ")"
                                                       + orgVarPathEsc
                                                       + "(?:"
                                                       + assnsSeparator
                                                       + "|$)', E'', 'g'); UPDATE "
                                                       + InternalTable.ASSOCIATIONS.getTableName()
                                                       + " SET "
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + " = regexp_replace("
                                                       + AssociationsColumn.MEMBERS.getColumnName()
                                                       + ", E'^"
                                                       + assnsSeparator
                                                       + "', E'', 'g'); ");

                                    // Check if the rate didn't change as well
                                    // (if the rate changed then updates to the
                                    // links and telemetry scheduler tables are
                                    // handled elsewhere)
                                    if (!rateChanged)
                                    {
                                        // Set the flag to indicate that
                                        // references to the variable and any
                                        // children should be removed from the
                                        // links and telemetry scheduler tables
                                        isDelLinksAndTlm = true;
                                    }
                                }

                                // Check if the rate changed or another change
                                // necessitates removal of the variable and any
                                // children from the links and telemetry
                                // scheduler tables
                                if (rateChanged || isDelLinksAndTlm)
                                {
                                    // Remove all references to the structure
                                    // and its children from the links and
                                    // telemetry scheduler tables
                                    linksDelCmd = deleteLinkPathRef("^"
                                                                    + orgVarPathEsc
                                                                    + "(?:,|:|$)",
                                                                    linksDelCmd);
                                    tlmDelCmd = deleteTlmPathRef(orgVarPathEsc
                                                                 + "(?:,|:|$)",
                                                                 tlmDelCmd);
                                }
                            }
                        }
                    }

                    // Remove the trailing comma and space, then add the
                    // condition based on the row's primary key, and add the
                    // commands to update the internal tables
                    modCmd = CcddUtilities.removeTrailer(modCmd, ", ");
                    modCmd.append(" WHERE "
                                  + typeDefinition.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()]
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
                // Not a prototype table, so modifications are made to the
                // custom values table if internal tables are to be updated,
                // and this table is a structure
                else if (!skipInternalTables && typeDefinition.isStructure())
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
                            // Build the command to delete the old value in the
                            // custom values table (in case it already exists),
                            // then insert the (new) value into the custom
                            // values table
                            modCmd.append("DELETE FROM "
                                          + InternalTable.VALUES.getTableName()
                                          + " WHERE "
                                          + ValuesColumn.TABLE_PATH.getColumnName()
                                          + " = '"
                                          + variablePath
                                          + "' AND "
                                          + ValuesColumn.COLUMN_NAME.getColumnName()
                                          + " = '"
                                          + typeDefinition.getColumnNamesUser()[column]
                                          + "';");

                            // Check if the new value does not begin with the
                            // flag that indicates the existing custom value
                            // should be removed
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
                                              + typeDefinition.getColumnNamesUser()[column]
                                              + "', '"
                                              + mod.getRowData()[column]
                                              + "'); ");
                            }
                        }
                    }

                    // Escape any PostgreSQL reserved characters so that the
                    // path can be used in a regular expression
                    variablePath = CcddUtilities.escapePostgreSQLReservedChars(variablePath);

                    // Step through each rate index
                    for (int rateIndex : mod.getRateColumn())
                    {
                        // Get the old and new rate values
                        String oldRate = mod.getOriginalRowData()[rateIndex].toString();
                        String newRate = mod.getRowData()[rateIndex].toString();

                        // Check if the rate changed
                        if (!oldRate.equals(newRate))
                        {
                            // Remove all references to the structure and its
                            // children from the links and telemetry scheduler
                            // tables
                            linksDelCmd = deleteLinkPathRef("^"
                                                            + variablePath
                                                            + "(?:,|:|$)",
                                                            linksDelCmd);
                            tlmDelCmd = deleteTlmPathRef(variablePath
                                                         + "(?:,|:|$)",
                                                         tlmDelCmd);
                            break;
                        }
                    }
                }
            }

            // Check if a change to the links table exists
            if (linksDelCmd.length() != 0)
            {
                // Terminate the links table command and add it to the
                // modification command
                modCmd.append(linksDelCmd.append("; "));
            }

            // Check if a change to the telemetry scheduler table exists
            if (tlmDelCmd.length() != 0)
            {
                // Terminate the telemetry scheduler table command and add it
                // to the modification command
                modCmd.append(tlmDelCmd.append("; "));
            }
        }

        return modCmd.toString();
    }

    /**************************************************************************
     * Build the command to delete a table row. Only prototype tables can have
     * a row deleted
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
     * @param typeDefinition
     *            table type definition
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @param skipInternalTables
     *            true to not build and execute the commands to update the
     *            internal tables. This is used during a data type update where
     *            only the data type name has changed in order to speed up the
     *            operation
     *
     * @return Table row deletion command
     *************************************************************************/
    private String buildDeletionCommand(TableInformation tableInfo,
                                        List<TableModification> deletions,
                                        String dbTableName,
                                        TypeDefinition typeDefinition,
                                        CcddTableTreeHandler tableTree,
                                        boolean skipInternalTables)
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
                delCmd.append((delCmd.length() == 0
                                                   ? "DELETE FROM "
                                                     + dbTableName
                                                     + " WHERE "
                                                   : " OR ")
                              + typeDefinition.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()]
                              + " = "
                              + del.getRowData()[DefaultColumn.PRIMARY_KEY.ordinal()]);

                // Check if the internal tables are to be updated and the
                // table represents a structure
                if (!skipInternalTables && typeDefinition.isStructure())
                {
                    // Get the variable name, data type, and bit length
                    String variableName = del.getRowData()[del.getVariableColumn()].toString();
                    String dataType = del.getRowData()[del.getDataTypeColumn()].toString();

                    // Create a list of table path arrays for instances of
                    // this prototype table name
                    List<Object[]> tablePathList = tableTree.getTableTreePathArray(tableInfo.getPrototypeName());

                    // Step through each table path found
                    for (Object[] path : tablePathList)
                    {
                        // Get the table path from the table tree
                        String variablePath = tableTree.getFullVariablePath(path);

                        // Append the name of the variable with data type to
                        // the path and escape any PostgreSQL reserved
                        // characters so that the path can be used in a regular
                        // expression
                        String variablePathEsc = CcddUtilities.escapePostgreSQLReservedChars(variablePath
                                                                                             + ","
                                                                                             + dataType
                                                                                             + "."
                                                                                             + variableName);

                        // Create or add to the command to update the custom
                        // values table for instances of variables of the
                        // prototype table
                        valuesDelCmd.append((valuesDelCmd.length() == 0
                                                                       ? "DELETE FROM "
                                                                         + InternalTable.VALUES.getTableName()
                                                                         + " WHERE"
                                                                       : " OR")
                                            + " "
                                            + ValuesColumn.TABLE_PATH.getColumnName()
                                            + " ~ E'^"
                                            + variablePathEsc
                                            + "(?:,|:|$)'");

                        // Create or add to the commands to update the links
                        // and telemetry scheduler tables for instances of
                        // variables of the prototype table
                        linksDelCmd = deleteLinkPathRef("^"
                                                        + variablePathEsc
                                                        + "(?:,|:|$)",
                                                        linksDelCmd);
                        tlmDelCmd = deleteTlmPathRef(variablePathEsc
                                                     + "(?:,|:|$)",
                                                     tlmDelCmd);

                        // Check if the data type represents a structure
                        if (!dataTypeHandler.isPrimitive(dataType))
                        {
                            // Create or add to the commands to update the
                            // internal tables for instances of variables of
                            // the prototype table
                            groupsDelCmd.append((groupsDelCmd.length() == 0
                                                                           ? "DELETE FROM "
                                                                             + InternalTable.GROUPS.getTableName()
                                                                             + " WHERE"
                                                                           : " OR")
                                                + " "
                                                + GroupsColumn.MEMBERS.getColumnName()
                                                + " ~ E'^"
                                                + variablePathEsc
                                                + "(?:,|$)'");
                            fieldsDelCmd.append((fieldsDelCmd.length() == 0
                                                                           ? "DELETE FROM "
                                                                             + InternalTable.FIELDS.getTableName()
                                                                             + " WHERE"
                                                                           : " OR")
                                                + " "
                                                + FieldsColumn.OWNER_NAME.getColumnName()
                                                + " ~ E'^"
                                                + variablePathEsc
                                                + "(?:,|$)'");
                            ordersDelCmd.append((ordersDelCmd.length() == 0
                                                                           ? "DELETE FROM "
                                                                             + InternalTable.ORDERS.getTableName()
                                                                             + " WHERE"
                                                                           : " OR")
                                                + " "
                                                + OrdersColumn.TABLE_PATH.getColumnName()
                                                + " ~ E'^"
                                                + variablePathEsc
                                                + "(?:,|$)'");
                            String pathWithChildren = variablePathEsc
                                                      + "(?:,"
                                                      + PATH_IDENT
                                                      + ")?";
                            assnsDelCmd.append("UPDATE "
                                               + InternalTable.ASSOCIATIONS.getTableName()
                                               + " SET "
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + " = regexp_replace("
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + ", E'^"
                                               + pathWithChildren
                                               + "', E'', 'g'); UPDATE "
                                               + InternalTable.ASSOCIATIONS.getTableName()
                                               + " SET "
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + " = regexp_replace("
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + ", E'"
                                               + assnsSeparator
                                               + pathWithChildren
                                               + "', E'', 'g'); UPDATE "
                                               + InternalTable.ASSOCIATIONS.getTableName()
                                               + " SET "
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + " = regexp_replace("
                                               + AssociationsColumn.MEMBERS.getColumnName()
                                               + ", E'^"
                                               + assnsSeparator
                                               + "', E'', 'g'); ");
                        }
                    }
                }
            }

            // Check if a change to the values table exists
            if (valuesDelCmd.length() != 0)
            {
                // Terminate the command
                valuesDelCmd.append("; ");
            }

            // Check if a change to the groups table exists
            if (groupsDelCmd.length() != 0)
            {
                // Terminate the command
                groupsDelCmd.append("; ");
            }

            // Check if a change to the fields table exists
            if (fieldsDelCmd.length() != 0)
            {
                // Terminate the command
                fieldsDelCmd.append("; ");
            }

            // Check if a change to the orders table exists
            if (ordersDelCmd.length() != 0)
            {
                // Terminate the command
                ordersDelCmd.append("; ");
            }

            // Check if a change to the links table exists
            if (linksDelCmd.length() != 0)
            {
                // Terminate the command
                linksDelCmd.append("; ");
            }

            // Check if a change to the telemetry scheduler table exists
            if (tlmDelCmd.length() != 0)
            {
                // Terminate the command
                tlmDelCmd.append("; ");
            }

            // Append the command's closing semi-colon and add the commands to
            // update the internal tables to the delete command
            delCmd.append("; "
                          + valuesDelCmd.toString()
                          + groupsDelCmd.toString()
                          + fieldsDelCmd.toString()
                          + ordersDelCmd.toString()
                          + assnsDelCmd.toString()
                          + linksDelCmd.toString()
                          + tlmDelCmd.toString());
        }

        return delCmd.toString();
    }

    /**************************************************************************
     * Build the command to update the variable name and/or the data type.
     * Combine updates to array members into a single command by using a
     * regular expression to match the array indices
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
     *            name of the column in the internal table that contains the
     *            variable path
     *
     * @param captureIn
     *            pattern match to capture any character(s) that precede(s) the
     *            variable path; blank if none
     *
     * @param captureOut
     *            pattern for replacing the captured character(s) specified in
     *            captureIn; blank if none
     *
     * @param includeChildren
     *            true to include child tables of the variable paths
     *
     * @return Command to update the variable name and/or the data type
     *************************************************************************/
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
            // Check if any of the array members in the path are not the first
            // array index ([0]) for that array. Only a path where all array
            // index references are for the first index need to be processed
            // since the command is tailored to account for all index values
            if (!orgVariablePath.matches(".+\\[[1-9].*"))
            {
                // Step through the original variable path for each first array
                // index reference
                while (orgVariablePath.contains("[0"))
                {
                    // Change the array index reference in the original path to
                    // generically search for and capture the array index
                    // value, and change the new path to insert the capture
                    // group value in the appropriate array index location.
                    // This allows a single command to cover a variable name or
                    // data type update for an entire array
                    orgVariablePath = orgVariablePath.replaceFirst("\\[0",
                                                                   "[(\\\\\\\\d+)");
                    newVariablePath = newVariablePath.replaceFirst("\\[0",
                                                                   "[\\\\\\\\"
                                                                       + captureGrp);
                    captureGrp++;
                }

                // Create the command to update the internal table for
                // instances of array variables of the prototype table
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
            // Create the command to update the custom values table for
            // instances of non-array member variables of the prototype table
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

    /**************************************************************************
     * Create a copy of the specified table's table tree node and immediate
     * child nodes
     *
     * @param tableName
     *            table name
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @return Copy of the specified table's table tree node and immediate
     *         child nodes (if any)
     *************************************************************************/
    private ToolTipTreeNode copyPrototypeTableTreeNode(String tableName,
                                                       CcddTableTreeHandler tableTree)
    {
        // Create a new node for the copy
        ToolTipTreeNode orgTableNode = new ToolTipTreeNode(tableName, "");

        // Get prototype table's tree node. If the table has no variables then
        // it doesn't appear in the table tree and this call returns null
        ToolTipTreeNode tableNode = tableTree.getNodeByNodeName(tableName);

        // Check if the table node exists
        if (tableNode != null)
        {
            // Step through each child node of the prototype table node; these
            // are the prototype's variables (data type and name)
            for (int childIndex = 0; childIndex < tableNode.getChildCount(); childIndex++)
            {
                // Copy the node (variable)
                orgTableNode.add(new ToolTipTreeNode(((ToolTipTreeNode) tableNode.getChildAt(childIndex)).getUserObject().toString(), ""));
            }
        }

        return orgTableNode;
    }

    /**************************************************************************
     * Build the commands to delete variable references in the links and
     * telemetry scheduler tables that are no longer valid due to changes in
     * bit-packing
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *            prior to applying any updates
     *
     * @param parent
     *            reference to the GUI component over which any error dialogs
     *            should be centered
     *
     * @return Commands to delete variable references in the links and
     *         telemetry scheduler tables that are no longer valid due to
     *         changes in bit-packing
     *************************************************************************/
    private String updateLinksAndTlmForPackingChange(CcddTableTreeHandler tableTree,
                                                     ToolTipTreeNode orgTableNode,
                                                     Component parent)
    {
        StringBuilder linksDelCmd = new StringBuilder("");
        StringBuilder tlmDelCmd = new StringBuilder("");
        List<String> removeMembers = new ArrayList<String>();
        List<List<String>> updatedPacking = new ArrayList<List<String>>();

        // Create the table tree after any changes have been applied
        tableTree = new CcddTableTreeHandler(ccddMain,
                                             TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                             parent);

        // Get the tree node for the updated prototype table
        ToolTipTreeNode updTableNode = tableTree.getNodeByNodeName(orgTableNode.getUserObject().toString());

        // ////////////////////////////////////////////////////////////////////
        // Create a list containing lists of each group of bit-packed variables
        // in the table as it exists after the updates are applied. Also create
        // a list containing every bit-packed variable; this list is pruned
        // later to remove those variables that haven't changed their
        // bit-packing
        // ////////////////////////////////////////////////////////////////////

        // Step through each variable in the table as it exists after the
        // updates
        for (int childIndex = 0; childIndex < updTableNode.getChildCount(); childIndex++)
        {
            // Get the indices of all variables bit-packed the variable at the
            // current child index
            NodeIndex nodeIndex = tableTree.getBitPackedVariables((ToolTipTreeNode) updTableNode.getChildAt(childIndex));

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

        // ////////////////////////////////////////////////////////////////////
        // Create lists of each group of bit-packed variables in the table as
        // it existed prior to the updates being applied. Also add the
        // variables to the list containing every bit-packed variable (if not
        // already in the list). Check if the group still exists in the updated
        // table (i.e. contains the same bit-packed members; order or position
        // is ignored). If the group does exist then remove its members from
        // the list of variables to be removed
        // ////////////////////////////////////////////////////////////////////

        // Step through each variable in the table as is existed prior to the
        // updates
        for (int childIndex = 0; childIndex < orgTableNode.getChildCount(); childIndex++)
        {
            // Get the indices of all variables bit-packed the variable at the
            // current child index
            NodeIndex nodeIndex = tableTree.getBitPackedVariables((ToolTipTreeNode) orgTableNode.getChildAt(childIndex));

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

                    // Check if the individual member list doesn't already
                    // contain this variable
                    if (!removeMembers.contains(variable))
                    {
                        // Add the variable to the individual members list
                        removeMembers.add(variable);
                    }
                }

                // ////////////////////////////////////////////////////////////
                // Compare the bit-packed group of variables in the table
                // originally to the groups in the table with the updates. If
                // the group still exists then remove its members from the list
                // of variables to remove. Once all of the bit-packed groups
                // are checks the removal list contains only the variables that
                // have changed their bit-packed status, and therefore need to
                // be removed from the links and telemetry scheduler tables
                // ////////////////////////////////////////////////////////////

                // Step through the list of bit-packed variables in the
                // updated table
                for (List<String> updatedPackMembers : updatedPacking)
                {
                    // Check if the bit-packed variables in the original table
                    // are still present in the updated table
                    if (CcddUtilities.isArraySetsEqual(packMembers.toArray(new String[0]),
                                                       updatedPackMembers.toArray(new String[0])))
                    {
                        // Since the bit-packed variables are unchanged, delete
                        // them from the list of variables that are to be
                        // removed from the links and telemetry scheduler
                        // tables, and stop searching
                        removeMembers.removeAll(packMembers);
                        break;
                    }
                }

                // Adjust the child index to skip these bit-packed variables
                childIndex = nodeIndex.getLastIndex();
            }
        }

        // Step through the list of variables that have changed their bit-pack
        // status (no longer bit-packed, or packed with different variables)
        for (String variable : removeMembers)
        {
            // Get the path of the variable
            String packPath = orgTableNode.getUserObject().toString()
                              + "(?:\\\\.[^,]*)?,"
                              + CcddUtilities.escapePostgreSQLReservedChars(variable)
                              + "(?::\\\\d+|$)";

            // Delete the variable from the links and telemetry scheduler
            // tables
            linksDelCmd = deleteLinkPathRef("(?:^|[^,]*,)"
                                            + packPath,
                                            linksDelCmd);
            tlmDelCmd = deleteTlmPathRef("(?:[^,]+,)*"
                                         + packPath,
                                         tlmDelCmd);
        }

        // Check if a change to the links table exists
        if (linksDelCmd.length() != 0)
        {
            // Terminate the command
            linksDelCmd.append("; ");
        }

        // Check if a change to the telemetry scheduler table exists
        if (tlmDelCmd.length() != 0)
        {
            // Terminate the command
            tlmDelCmd.append("; ");
        }

        return linksDelCmd.toString() + tlmDelCmd.toString();
    }

    /**************************************************************************
     * Update the link definitions when a string variable's size is increased
     * (i.e., a new member is added to the array)
     *
     * @param protoTable
     *            prototype table name to which the string variable belongs
     *
     * @param dataType
     *            string variable's data type name
     *
     * @param variableName
     *            string variable's name
     *************************************************************************/
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

        // Get the reference to the link definitions in order to shorten
        // subsequent calls
        List<String[]> linkDefns = addLinkHandler.getLinkDefinitions();

        // Get the variable name without the string dimension (leaving any
        // other array dimensions)
        String stringVarNameDefn = ArrayVariable.removeStringSize(variableName);

        // Extract the string dimension value from the variable name, then
        // decrement it by one - this is used to find the existing string array
        // member immediately preceding the new member
        int[] stringArrayDim = ArrayVariable.getArrayIndexFromSize(ArrayVariable.getVariableArrayIndex(variableName));
        int stringIndex = stringArrayDim[stringArrayDim.length - 1] - 1;

        // Step through each link definition
        for (int index = linkDefns.size() - 1; index >= 0; index--)
        {
            // Get the link member in order to shorten subsequent calls
            String linkMember = linkDefns.get(index)[LinksColumn.MEMBER.ordinal()];

            // Check if the link member contains a reference to the string
            // array
            if (linkMember.matches(protoTable
                                   + "(?:,|\\.[^,]+,)"
                                   + dataType
                                   + "\\."
                                   + stringVarNameDefn
                                   + "\\["
                                   + stringIndex
                                   + "\\]"))
            {
                // Insert the new string array member into the link definitions
                // list immediately after the preceding member
                addLinkHandler.getLinkDefinitions().add(index + 1,
                                                        new String[]
                                                        {linkDefns.get(index)[LinksColumn.RATE_NAME.ordinal()],
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

                // Set the flag to indicate that a change to the link
                // definitions was made
                updateLinks = true;
            }
        }
    }

    /**************************************************************************
     * Build the command to delete the specified table/variable references from
     * the links table
     *
     * @param linksPath
     *            table/variable path to remove from the links table. Leading
     *            or trailing regular expression constructs must surround the
     *            path, and any reserved PostgreSQL characters in the path must
     *            be escaped
     *
     * @param linksCmd
     *            StringBuilder containing the existing links table deletion
     *            command. If empty then a new command is initiated; otherwise
     *            the deletions are appended to the existing command
     *
     * @return StringBuilder containing the links table command. This must be
     *         terminated by a semi-colon prior to execution
     *************************************************************************/
    private StringBuilder deleteLinkPathRef(String linksPath,
                                            StringBuilder linksCmd)
    {
        linksCmd.append((linksCmd.length() == 0
                                               ? "DELETE FROM "
                                                 + InternalTable.LINKS.getTableName()
                                                 + " WHERE"
                                               : " OR")
                        + " "
                        + LinksColumn.MEMBER.getColumnName()
                        + " ~ E'"
                        + linksPath
                        + "'");

        return linksCmd;
    }

    /**************************************************************************
     * Build the commands to delete the specified table/variable references
     * from the telemetry scheduler table
     *
     * @param tlmPath
     *            table/variable path to remove from the telemetry scheduler
     *            table. Leading or trailing regular expression constructs must
     *            surround the path, and any reserved PostgreSQL characters in
     *            the path must be escaped
     *
     * @param tlmCmd
     *            StringBuilder containing the existing telemetry scheduler
     *            table deletion command. If empty then a new command is
     *            initiated; otherwise the deletions are appended to the
     *            existing command
     *
     * @return StringBuilder containing the telemetry scheduler table command.
     *         This must be terminated by a semi-colon prior to execution
     *************************************************************************/
    private StringBuilder deleteTlmPathRef(String tlmPath,
                                           StringBuilder tlmCmd)
    {
        tlmCmd.append((tlmCmd.length() == 0
                                           ? "DELETE FROM "
                                             + InternalTable.TLM_SCHEDULER.getTableName()
                                             + " WHERE"
                                           : " OR")
                      + " "
                      + TlmSchedulerColumn.MEMBER.getColumnName()
                      + " ~ E'^.*"
                      + tlmSchSeparator
                      + tlmPath
                      + "'");

        return tlmCmd;
    }

    /**************************************************************************
     * Build the command to delete the specified variable references from the
     * telemetry scheduler table
     *
     * @param variablePaths
     *            list of variable paths to remove from the telemetry scheduler
     *            table
     *
     * @return Command to delete the specified variable references from the
     *         telemetry scheduler table
     *************************************************************************/
    private String deleteTlmPathRefs(List<String> variablePaths)
    {
        StringBuilder tlmCommand = new StringBuilder("");

        // Check if any paths are supplied
        if (variablePaths != null)
        {
            // Step through each variable path
            for (String path : variablePaths)
            {
                // Add the command to delete all of the variable's references
                // from
                // the telemetry scheduler table
                tlmCommand = deleteTlmPathRef(CcddUtilities.escapePostgreSQLReservedChars(path),
                                              tlmCommand);
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

    /**************************************************************************
     * Remove all references for the specified rate column name in tables of
     * the changed table type in the links and telemetry scheduler tables
     *
     * @param rateColName
     *            rate column name
     *
     * @param linksCmd
     *            sub-command for references in the links table to tables of
     *            the target type
     *
     * @param tlmSchCmd
     *            sub-command for references in the telemetry scheduler table
     *            to tables of the target type
     *
     * @return
     *************************************************************************/
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

    /**************************************************************************
     * Remove all references to the specified rate column name in the links and
     * telemetry scheduler tables
     *
     * @param rateColName
     *            rate column name
     *
     * @return
     *************************************************************************/
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

    /**************************************************************************
     * Retrieve a list of internal table data from the database
     *
     * @param intTable
     *            type of internal table to retrieve
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List of the items in the internal table. An empty list is
     *         returned if the specified table is empty or doesn't exist
     *************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      Component parent)
    {
        return retrieveInformationTable(intTable, false, null, parent);
    }

    /**************************************************************************
     * Retrieve a list of internal table data from the database
     *
     * @param intTable
     *            type of internal table to retrieve
     *
     * @param includeOID
     *            true to read in the OID column in addition to the information
     *            table columns
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List of the items in the internal table. An empty list is
     *         returned if the specified table is empty or doesn't exist
     *************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      boolean includeOID,
                                                      Component parent)
    {
        return retrieveInformationTable(intTable, includeOID, null, parent);
    }

    /**************************************************************************
     * Retrieve a list of internal table data from the database
     *
     * @param intTable
     *            type of internal table to retrieve
     *
     * @param includeOID
     *            true to read in the OID column in addition to the information
     *            table columns
     *
     * @param scriptName
     *            script file name; ignored for non-script file information
     *            tables
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List of the items in the internal table. An empty list is
     *         returned if the specified table is empty or doesn't exist
     *************************************************************************/
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
                        // Add the column value to the array. Note that the
                        // first column's index in the database is 1, not 0
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

    /**************************************************************************
     * Store the internal table into the database. This command is executed in
     * a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue
     * to update. The GUI menu commands, however, are disabled until the
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
     *            GUI component calling this method
     *************************************************************************/
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

    /**************************************************************************
     * Store the internal table into the database. This command is executed in
     * a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue
     * to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            list containing the table data to store
     *
     * @param fieldInformationList
     *            list containing the list of data field information for each
     *            group with a data field update (only applicable to the groups
     *            table); null if none
     *
     * @param deletedGroups
     *            list containing the names of groups that have been deleted
     *
     * @param invalidLinkVars
     *            list containing the link member variables that should be
     *            removed from the telemetry scheduler due to the addition of
     *            one or more variables; null or an empty list if there are no
     *            invalid link members
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
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
            /******************************************************************
             * Database internal table store command
             *****************************************************************/
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

    /**************************************************************************
     * Store the internal table into the database
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
     *            GUI component calling this method
     *************************************************************************/
    protected void storeInformationTable(InternalTable intTable,
                                         List<String[]> tableData,
                                         String tableComment,
                                         Component parent)
    {
        storeInformationTable(intTable,
                              tableData,
                              null,
                              null,
                              null,
                              tableComment,
                              parent);
    }

    /**************************************************************************
     * Store the internal table into the database
     *
     * @param intTable
     *            type of internal table to store
     *
     * @param tableData
     *            list containing the table data to store; unused for the table
     *            types internal table
     *
     * @param fieldInformationList
     *            list containing the list of data field information for each
     *            group with a data field update (only applicable to the groups
     *            table); null if none
     *
     * @param deletedGroups
     *            list containing the names of groups that have been deleted
     *
     * @param invalidLinkVars
     *            list containing the link member variables that should be
     *            removed from the telemetry scheduler due to the addition of
     *            one or more variables; null or an empty list if there are no
     *            invalid link members
     *
     * @param tableComment
     *            table comment; null if unchanged
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
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
                    // Step through each deleted group
                    for (String groupName : deletedGroups)
                    {
                        // Build the command to delete the group's data fields
                        command += modifyFieldsCommand(CcddFieldHandler.getFieldGroupName(groupName),
                                                       null);
                    }

                    // Step through each group's data field information list
                    for (List<FieldInformation> fieldInformation : fieldInformationList)
                    {
                        // Build the command to modify the data fields for the
                        // group
                        command += modifyFieldsCommand(fieldInformation.get(0).getOwnerName(),
                                                       fieldInformation);
                    }

                case APP_SCHEDULER:
                case ASSOCIATIONS:
                case DATA_TYPES:
                case FIELDS:
                case MACROS:
                case ORDERS:
                case RESERVED_MSG_IDS:
                case SCRIPT:
                case TLM_SCHEDULER:
                    // Build the command for storing the script configurations,
                    // groups, links, etc. table
                    command += storeNonTableTypesInfoTableCommand(intTable,
                                                                  tableData,
                                                                  tableComment,
                                                                  parent);
                    break;

                case LINKS:
                    // Build the commands for storing the links table and for
                    // deleting any invalid references in the telemetry
                    // scheduler table
                    command += storeNonTableTypesInfoTableCommand(intTable,
                                                                  tableData,
                                                                  tableComment,
                                                                  parent)
                               + deleteTlmPathRefs(invalidLinkVars);

                    break;

                case TABLE_TYPES:
                    // Build the command for storing the table type definitions
                    // table
                    command = storeTableTypesInfoTableCommand();
                    break;

                case VALUES:
                    break;
            }

            // Execute the database update
            dbCommand.executeDbUpdate(command, parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              intTableName + " stored");
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

        switch (intTable)
        {
            case ASSOCIATIONS:
                // Check if the store request originated from the script
                // manager dialog
                if (parent instanceof CcddScriptManagerDialog)
                {
                    // Perform the script associations command completion steps
                    ((CcddScriptManagerDialog) parent).doAssnUpdatesComplete(errorFlag);
                }

                break;

            case GROUPS:
                // Check if the store request originated from the group manager
                // dialog
                if (parent instanceof CcddGroupManagerDialog)
                {
                    // Perform the groups store command completion steps
                    ((CcddGroupManagerDialog) parent).doGroupUpdatesComplete(errorFlag);
                }

                break;

            case LINKS:
                // Check if the store request originated from the link manager
                // dialog
                if (parent instanceof CcddLinkManagerDialog)
                {
                    // Perform the links store command completion steps
                    ((CcddLinkManagerDialog) parent).doLinkUpdatesComplete(errorFlag);
                }

                break;

            case APP_SCHEDULER:
            case TLM_SCHEDULER:
                // Check if the store request originated from the application
                // or telemetry scheduler dialogs
                if (parent instanceof CcddSchedulerDialogInterface)
                {
                    // Perform the scheduler store command completion steps
                    ((CcddSchedulerDialogInterface) parent).doSchedulerUpdatesComplete(errorFlag);
                }

                break;

            case RESERVED_MSG_IDS:
                // Check if the store request originated from the reserved
                // message ID editor dialog
                if (parent instanceof CcddReservedMsgIDEditorDialog)
                {
                    // Perform the reserved message ID store command completion
                    // steps
                    ((CcddReservedMsgIDEditorDialog) parent).doMsgIDUpdatesComplete(errorFlag);
                }

                break;

            case DATA_TYPES:
            case FIELDS:
            case MACROS:
            case ORDERS:
            case SCRIPT:
            case TABLE_TYPES:
            case VALUES:
                break;
        }
    }

    /**************************************************************************
     * Build the command for storing the groups, script associations, links
     * table, data fields, or script
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
     *            GUI component calling this method
     *
     * @return Command for building the specified table
     *************************************************************************/
    private String storeNonTableTypesInfoTableCommand(InternalTable intTable,
                                                      List<String[]> tableData,
                                                      String tableComment,
                                                      Component parent)
    {
        // Get the internal table's name
        String tableName = intTable.getTableName(tableComment);

        // Build the command to delete the information list table if it exists,
        // then the creation commands
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
                // Build the command to restore the comment after the table is
                // deleted and recreated
                command.append("COMMENT ON TABLE "
                               + tableName
                               + " IS "
                               + delimitText(tableComment)
                               + "; ");
            }
        }
        catch (SQLException se)
        {
            // Inform the user that loading the internal table comment
            // failed
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
                    // Add the item to the command enclosing it in an
                    // appropriate delimiter if the item is text
                    command.append(delimitText(column) + ", ");
                }

                // Remove the trailing comma and space, then terminate the
                // column
                command = CcddUtilities.removeTrailer(command, ", ");
                command.append("), ");
            }

            // Replace the trailing comma and space with a semicolon
            command = CcddUtilities.removeTrailer(command, ", ");
            command.append("; ");
        }

        return command.toString();
    }

    /**************************************************************************
     * Build the command for storing the table type definitions table
     *
     * @return Command for building the specified table
     *************************************************************************/
    protected String storeTableTypesInfoTableCommand()
    {
        // Build the command to delete the existing table type definitions
        // table and create the new one
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
            cmd = cmd.substring(0,
                                cmd.indexOf(DefaultColumn.getColumnDefinitions()));
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

    /**************************************************************************
     * Build the command for updating the data field definitions table for the
     * specified data table, table type, or group
     *
     * @param ownerName
     *            name of the table, including the path if this table
     *            represents a structure, table type, or group to which the
     *            field(s) belong
     *
     * @param fieldInformation
     *            field information; null to delete the fields for the
     *            specified table/table type/group
     *
     * @return Command for updating the table/table type/group fields in the
     *         data field table
     *************************************************************************/
    private String modifyFieldsCommand(String ownerName,
                                       List<FieldInformation> fieldInformation)
    {
        // Build the command to delete the existing field definitions for the
        // specified table/group
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
            command.append("INSERT INTO "
                           + InternalTable.FIELDS.getTableName()
                           + " VALUES ");

            // Step through each of the table's field definitions
            for (FieldInformation fieldInfo : fieldInformation)
            {
                // Add the command to insert the field information
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
                               + "), ");
            }

            // Replace the trailing comma with a semicolon
            command = CcddUtilities.removeTrailer(command, ", ").append("; ");
        }

        return command.toString();
    }

    /**************************************************************************
     * Change the name of a table type and update the existing tables of the
     * specified type. This command is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     *
     * @param typeName
     *            current name of the table type
     *
     * @param newName
     *            new name for this table type
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *************************************************************************/
    protected void renameTableType(final String typeName,
                                   final String newName,
                                   final CcddTableTypeManagerDialog typeDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;
            String tableName;

            /******************************************************************
             * Rename table type command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Create the command to update the table definitions table
                    StringBuilder command = new StringBuilder(storeTableTypesInfoTableCommand());

                    // Get an array containing tables of the specified type
                    String[] tableNames = queryTablesOfTypeList(typeName,
                                                                typeDialog);

                    // For each table of the specified type
                    for (String table : tableNames)
                    {
                        tableName = table;

                        // Get the table's comment so that it can be rebuilt
                        // with the new type name
                        String[] comment = queryDataTableComment(table,
                                                                 typeDialog);

                        // Update the table comment to reflect the new type
                        // name
                        command.append(buildDataTableComment(comment[TableCommentIndex.NAME.ordinal()],
                                                             newName));
                    }

                    // Add the command to rename any default data fields for
                    // this table type
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
                                          "Cannot rename type for table '"
                                              + tableName
                                              + "'; cause '"
                                              + se.getMessage()
                                              + "'",
                                          "<html><b>Cannot rename type for table '</b>"
                                              + tableName
                                              + "<b>'");
                    errorFlag = true;
                }
            }

            /******************************************************************
             * Rename table type command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                typeDialog.doTypeOperationComplete(errorFlag, null, null);
            }
        });
    }

    /**************************************************************************
     * Create a copy of an existing table type. This command is executed in a
     * separate thread since it can take a noticeable amount time to complete,
     * and by using a separate thread the GUI is allowed to continue to update.
     * The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param typeName
     *            name of the table type to copy
     *
     * @param copyName
     *            name for the copy of this table type
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *************************************************************************/
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

            /******************************************************************
             * Copy table type command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Create a data field handler and use it to extract the
                    // fields for the table type to be copied
                    CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                                         CcddFieldHandler.getFieldTypeName(typeName),
                                                                         typeDialog);

                    // Get the data field definitions that were loaded from the
                    // database
                    fieldDefinitions = fieldHandler.getFieldDefinitions();

                    // Add the fields under the new type name to the list of
                    // existing field definitions
                    fieldDefinitions.addAll(fieldHandler.renameFieldTable(CcddFieldHandler.getFieldTypeName(copyName)));

                    // Create the command to rebuild the table types and fields
                    // tables
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
            }

            /******************************************************************
             * Copy table type command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred when copying the table type and
                // if the type represents a structure
                if (!errorFlag
                    && tableTypeHandler.getTypeDefinition(typeName).isStructure())
                {
                    // Update the rate information, if applicable
                    rateHandler.setRateInformation();
                }

                typeDialog.doTypeOperationComplete(errorFlag,
                                                   fieldDefinitions,
                                                   null);
            }
        });
    }

    /**************************************************************************
     * Delete a table type and all tables of the deleted type. This command is
     * executed in a separate thread since it can take a noticeable amount time
     * to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until
     * the database command completes execution
     *
     * @param typeName
     *            type of table to delete
     *
     * @param isStructure
     *            true if the table type represents a structure
     *
     * @param typeDialog
     *            reference to the type manager dialog
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void deleteTableType(final String typeName,
                                   final boolean isStructure,
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

            /******************************************************************
             * Table type deletion command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Delete the table type definition from the table type
                    // handler
                    tableTypeHandler.getTypeDefinitions().remove(tableTypeHandler.getTypeDefinition(typeName));

                    // Create the command to update the table definitions table
                    String command = storeTableTypesInfoTableCommand();

                    // Get an array containing tables of the specified type
                    tableNames = queryTablesOfTypeList(typeName,
                                                       ccddMain.getMainFrame());

                    // Check if there are any associated tables to delete
                    if (tableNames.length != 0)
                    {
                        // Convert the array of tables names into a single
                        // string and
                        // shorten it if too long
                        names = " and table(s) '</b>"
                                + getShortenedTableNames(tableNames)
                                + "<b>'";

                        // Check if the user confirms deleting the affected
                        // table(s)
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
                            // Add the command to delete the tables and all
                            // their references in the internal tables
                            command += deleteTableCommand(tableNames, true) + " ";
                        }
                        // The user canceled the operation
                        else
                        {
                            // Set the error flag to restore the deleted type
                            // definition
                            errorFlag = true;
                        }
                    }

                    // Check if the user didn't cancel the operation
                    if (!errorFlag)
                    {
                        // Add the command to remove any default data fields
                        // for this table type
                        command += modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeName),
                                                       null);

                        // Step through the array of table names
                        for (String name : tableNames)
                        {
                            // Remove the table's data fields
                            command += modifyFieldsCommand(name, null);
                        }

                        // Delete the table(s)
                        dbCommand.executeDbUpdate(command, parent);

                        // Execute the command to reset the rate for links that
                        // no longer contain any variables
                        dbCommand.executeDbQuery("SELECT reset_link_rate();", parent);

                        // Log that the table deletion succeeded
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
                                          "<html><b>Cannot delete table type '"
                                              + typeName
                                              + "'</b>"
                                              + names
                                              + "<b>'");
                    errorFlag = true;
                }
            }

            /******************************************************************
             * Delete table type command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if no error occurred when deleting the table type
                if (!errorFlag)
                {
                    // Check if the deleted type represents a structure
                    if (isStructure)
                    {
                        // Update the database functions that collect structure
                        // table members and structure-defining column data
                        dbControl.createStructureColumnFunctions();
                    }

                    // Check if the number of rate columns changed due to the
                    // type deletion
                    if (rateHandler.setRateInformation())
                    {
                        // Store the rate parameters in the project database
                        storeRateParameters(ccddMain.getMainFrame());
                    }
                }

                // Perform any remaining steps, based on if deleting the type
                // was successful
                typeDialog.doTypeOperationComplete(errorFlag,
                                                   null,
                                                   tableNames);
            }
        });
    }

    /**************************************************************************
     * Add, rename, or delete table type column names in the table definitions
     * table and update the existing tables of the specified type. This command
     * is executed in a separate thread since it can take a noticeable amount
     * time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until
     * the database command completes execution
     *
     * @param tableType
     *            table type name to modify
     *
     * @param fieldInformation
     *            data field information list
     *
     * @param overwriteFields
     *            true if the content of existing fields should be overwritten
     *            by the default values; false to not overwrite existing values
     *
     * @param additions
     *            list of new columns to add to the tables. Each list item is a
     *            column name (user)
     *
     * @param modifications
     *            list of name changes of existing columns in the tables. Each
     *            list item is an array containing: [0] original column name
     *            (user), [1] new column name (user), [2] column input type
     *
     * @param deletions
     *            list of columns to remove from the tables. Each list item is
     *            an array containing: [0] column name (user), [1] column input
     *            type
     *
     * @param columnOrderChange
     *            true if the table type's column order changed
     *
     * @param originalDefn
     *            reference to the table type definition prior to making any
     *            changes
     *
     * @param editorDialog
     *            reference to the table type editor dialog
     *
     * @param editor
     *            reference to the table type editor where the change(s)
     *            occurred
     *************************************************************************/
    protected void modifyTableTypeInBackground(final String tableType,
                                               final List<FieldInformation> fieldInformation,
                                               final boolean overwriteFields,
                                               final List<String[]> additions,
                                               final List<String[]> modifications,
                                               final List<String[]> deletions,
                                               final boolean columnOrderChange,
                                               final TypeDefinition originalDefn,
                                               final CcddTableTypeEditorDialog editorDialog,
                                               final CcddTableTypeEditorHandler editor)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Modify table types command
             *****************************************************************/
            @Override
            protected void execute()
            {
                modifyTableType(tableType,
                                fieldInformation,
                                overwriteFields,
                                additions,
                                modifications,
                                deletions,
                                columnOrderChange,
                                originalDefn,
                                editorDialog,
                                editor);
            }
        });
    }

    /**************************************************************************
     * Add, rename, or delete table type column names in the table definitions
     * table and update the existing tables of the specified type
     *
     * @param typeName
     *            table type name to modify
     *
     * @param fieldInformation
     *            data field information list
     *
     * @param overwriteFields
     *            true if the content of existing fields should be overwritten
     *            by the default values; false to not overwrite existing values
     *
     * @param additions
     *            list of new columns to add to the tables. . Each list item is
     *            an array containing: [0] column name (user), [1] column input
     *            type
     *
     * @param modifications
     *            list of name changes of existing columns in the tables. Each
     *            list item is an array containing: [0] original column name
     *            (user), [1] new column name (user), [2] column input type
     *
     * @param deletions
     *            list of columns to remove from the tables. Each list item is
     *            an array containing: [0] column name (user), [1] column input
     *            type
     *
     * @param columnOrderChange
     *            true if the table type's column order changed
     *
     * @param originalDefn
     *            reference to the table type definition prior to making any
     *            changes; null if this is a new table type
     *
     * @param editorDialog
     *            reference to the table type editor dialog
     *
     * @param editor
     *            reference to the table type editor where the change(s)
     *            occurred
     *************************************************************************/
    protected void modifyTableType(String typeName,
                                   List<FieldInformation> fieldInformation,
                                   boolean overwriteFields,
                                   List<String[]> additions,
                                   List<String[]> modifications,
                                   List<String[]> deletions,
                                   boolean columnOrderChange,
                                   TypeDefinition originalDefn,
                                   CcddTableTypeEditorDialog editorDialog,
                                   CcddTableTypeEditorHandler editor)
    {
        try
        {
            boolean errorFlag = false;
            String[] tableNames = null;
            String names = "";

            // Set the flag that indicates if the table type definition
            // represents a structure prior to making the updates
            boolean wasStructure = originalDefn != null
                                                       ? originalDefn.isStructure()
                                                       : false;

            // Get the type definition based on the table type name
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(typeName);

            // Create the command to update the table definitions table
            StringBuilder command = new StringBuilder(storeTableTypesInfoTableCommand());

            // Check if this isn't a new table type
            if (originalDefn != null)
            {
                // Get an array containing all of the prototype tables of the
                // specified type
                String[] protoTableNames = queryTablesOfTypeList(typeName,
                                                                 editorDialog);

                String columnOrder = "";

                // Check if the column order changed or if any columns were
                // added or deleted
                if (columnOrderChange || !additions.isEmpty() || !deletions.isEmpty())
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

                // Create a list to store the names of all tables of the
                // specified type
                List<String> tableNamesList = new ArrayList<String>();

                // Build a table tree with all prototype and instance tables
                CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain,
                                                                          TableTreeType.TABLES,
                                                                          editorDialog);

                // ////////////////////////////////////////////////////////////
                // All prototype tables of the affected table type have the
                // columns added/renamed/deleted and the column order table
                // updated
                // ////////////////////////////////////////////////////////////

                // Step through each prototype table of the specified type
                for (String protoName : protoTableNames)
                {
                    // Create a list of table path arrays for instances of this
                    // prototype table name
                    tableNamesList.addAll(tableTree.getTableTreePathList(protoName));

                    // Get the database form of the table name
                    String dbTable = protoName.toLowerCase();

                    // Step through each addition
                    for (String add[] : additions)
                    {
                        // Get the input data type, and the column name in
                        // database form
                        InputDataType inputDataType = InputDataType.getInputTypeByName(add[1]);
                        String dbName = DefaultColumn.convertVisibleToDatabase(add[0],
                                                                               inputDataType);
                        // Append the add command
                        command.append("ALTER TABLE "
                                       + dbTable
                                       + " ADD COLUMN "
                                       + dbName
                                       + " text DEFAULT ''; ");
                    }

                    // Step through each modification
                    for (String[] mod : modifications)
                    {
                        // Get the old and new column names in database form
                        String oldDbName = DefaultColumn.convertVisibleToDatabase(mod[0],
                                                                                  InputDataType.getInputTypeByName(mod[2]));
                        String newDbName = DefaultColumn.convertVisibleToDatabase(mod[1],
                                                                                  InputDataType.getInputTypeByName(mod[3]));

                        // Check if the database form of the name changed
                        if (!oldDbName.equals(newDbName))
                        {
                            // Append the modify command
                            command.append("ALTER TABLE "
                                           + dbTable
                                           + " RENAME COLUMN "
                                           + oldDbName
                                           + " TO "
                                           + newDbName
                                           + "; ");
                        }
                    }

                    // Step through each deletion
                    for (String[] del : deletions)
                    {
                        // Get the input data type, and the column name in
                        // database form
                        InputDataType inputDataType = InputDataType.getInputTypeByName(del[1]);
                        String dbName = DefaultColumn.convertVisibleToDatabase(del[0],
                                                                               inputDataType);

                        // Append the delete command
                        command.append("ALTER TABLE "
                                       + dbTable
                                       + " DROP COLUMN "
                                       + dbName
                                       + "; ");
                    }

                    // Check if the column order changed
                    if (columnOrder.length() != 0)
                    {
                        // Replace the column order for all tables matching
                        // this table name. This resets the column order for
                        // all users
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

                // ////////////////////////////////////////////////////////////
                // A modification or deletion indicates that a column name
                // changed and/or that a rate column's input type changed
                // ////////////////////////////////////////////////////////////

                // Check if there are modifications or deletions, and if any
                // tables
                // of this type exist
                if ((!modifications.isEmpty() || !deletions.isEmpty())
                    && protoTableNames.length != 0)
                {
                    // ////////////////////////////////////////////////////////
                    // Build the sub-command required to detect references to
                    // tables of the affected type in the custom values, links,
                    // and telemetry scheduler tables
                    // ////////////////////////////////////////////////////////
                    StringBuilder valuesCmd = new StringBuilder("");
                    StringBuilder linksCmd = new StringBuilder("");
                    StringBuilder tlmSchCmd = new StringBuilder("");

                    // Step through each prototype table of the specified type
                    for (String protoName : protoTableNames)
                    {
                        // Build the table name comparison command for the
                        // custom values table
                        valuesCmd.append(ValuesColumn.TABLE_PATH.getColumnName()
                                         + " ~ E'[^,]+,"
                                         + protoName
                                         + "\\.[^,]+,[^,]+$' OR ");
                    }

                    // Remove the trailing 'OR' and prepend an 'AND' to
                    // complete the custom values command
                    valuesCmd = CcddUtilities.removeTrailer(valuesCmd, " OR ");
                    valuesCmd.insert(0, " AND (");
                    valuesCmd.append(")");

                    // Check if the table type represents a structure
                    if (typeDefn.isStructure() && wasStructure)
                    {
                        // Step through each prototype table of the specified
                        // type
                        for (String protoName : protoTableNames)
                        {
                            // Build the table name comparison command for the
                            // links and telemetry scheduler tables
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

                        // Remove the trailing 'OR' and prepend an 'AND' to
                        // complete the links and telemetry scheduler table
                        // commands
                        linksCmd = CcddUtilities.removeTrailer(linksCmd, " OR ");
                        linksCmd.insert(0, " AND (");
                        linksCmd.append(")");
                        tlmSchCmd = CcddUtilities.removeTrailer(tlmSchCmd, " OR ");
                        tlmSchCmd.insert(0, " AND (");
                        tlmSchCmd.append(")");
                    }

                    // ////////////////////////////////////////////////////////
                    // If the table type no longer represents a structure then
                    // all references in the links and telemetry scheduler
                    // tables to tables of the affected type are removed
                    // ////////////////////////////////////////////////////////

                    // Check if the table type changed from representing a
                    // structure to no longer doing so
                    if (!typeDefn.isStructure() && wasStructure)
                    {
                        boolean hasSharedRate = false;

                        // Step through each rate column in the table type
                        // definition
                        for (int column : originalDefn.getColumnIndicesByInputType(InputDataType.RATE))
                        {
                            // Get the rate column name (as seen by the user)
                            String rateName = originalDefn.getColumnNamesUser()[column];

                            // Check if this is the only table type using this
                            // rate column name
                            if (rateHandler.getRateInformationByRateName(rateName).getNumSharedTableTypes() == 1)
                            {
                                // Delete all entries in the links and
                                // telemetry scheduler tables that reference
                                // this rate name
                                command.append(deleteAllLinkAndTlmRateRefs(rateName));
                            }
                            // The rate is shared with another table type
                            else
                            {
                                // Set the flag indicating the table type has a
                                // shared rate
                                hasSharedRate = true;
                            }
                        }

                        // Check if the table type shares a rate column with
                        // another table type. If not then all references in
                        // the links and telemetry tables are eliminated by the
                        // command built above; otherwise, commands must be
                        // built to remove the specific table references in
                        // these internal tables
                        if (hasSharedRate)
                        {
                            // Remove all references to tables of the changed
                            // type in the links and telemetry scheduler tables
                            command.append(deleteLinkAndTlmRateRef(".+",
                                                                   linksCmd,
                                                                   tlmSchCmd));
                        }
                    }

                    // ////////////////////////////////////////////////////////
                    // For a column name change/deletion (of any input type),
                    // references to the column name in the custom values table
                    // are updated/removed
                    //
                    // The modifications and deletions must then be applied to
                    // the links and telemetry scheduler tables, but only if
                    // the table type remains a structure type after the change
                    // (if the table type no longer represents a structure then
                    // the code above addresses the links and telemetry
                    // scheduler tables, and if the table type becomes a
                    // structure no changes to these internal tables is
                    // needed). If a rate name is changed and it's not used by
                    // another table type then the rate name references are
                    // changed to the new name in the internal tables.
                    // Otherwise, a change to or removal of the rate name
                    // results in the references in the internal tables being
                    // removed (either just the affected table references or
                    // all references - tables as well as the link/rate
                    // definition rows - depending on if the rate name was/is
                    // unique to this table type)
                    // ////////////////////////////////////////////////////////

                    // Step through each modification
                    for (String[] mod : modifications)
                    {
                        // Check if the column name changed
                        if (!mod[0].equals(mod[1]))
                        {
                            // Append the modify command for the custom values
                            // table
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

                        // Check if the table type represents a structure and
                        // the column input type was a rate
                        if (typeDefn.isStructure() && wasStructure
                            && mod[2].equals(InputDataType.RATE.getInputName()))
                        {
                            // Check if the column changed from a rate column
                            // to not being a rate column
                            if (!mod[3].equals(InputDataType.RATE.getInputName()))
                            {
                                // Check if the rate name is used by another
                                // structure table type
                                if (rateHandler.getRateInformationByRateName(mod[0]).getNumSharedTableTypes() > 1)
                                {
                                    // Remove all references to tables of the
                                    // changed type in the links and telemetry
                                    // scheduler tables
                                    command.append(deleteLinkAndTlmRateRef(mod[0],
                                                                           linksCmd,
                                                                           tlmSchCmd));
                                }
                                // The rate name is unique to this table type
                                else
                                {
                                    // Remove all references to the original
                                    // rate column name in the links and
                                    // telemetry scheduler tables
                                    command.append(deleteAllLinkAndTlmRateRefs(mod[0]));
                                }
                            }
                            // Check if the rate column name changed
                            else if (!mod[0].equals(mod[1]))
                            {
                                // Check if the original rate name is used by
                                // another structure table type
                                if (rateHandler.getRateInformationByRateName(mod[0]).getNumSharedTableTypes() > 1)
                                {
                                    // Remove all references to tables of the
                                    // changed type in the links and telemetry
                                    // scheduler tables
                                    command.append(deleteLinkAndTlmRateRef(mod[0],
                                                                           linksCmd,
                                                                           tlmSchCmd));
                                }
                                // Check if the new rate name isn't used by
                                // another structure table type (i.e., the rate
                                // name is unique to this table type)
                                else if (rateHandler.getRateInformationByRateName(mod[1]) == null)
                                {
                                    // Append the modify command for the links
                                    // and telemetry scheduler table
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
                                    // Remove all references to the original
                                    // rate column name in the links and
                                    // telemetry scheduler tables
                                    command.append(deleteAllLinkAndTlmRateRefs(mod[0]));
                                }
                            }
                        }
                    }

                    // Step through each deletion
                    for (String[] del : deletions)
                    {
                        // Append the delete command for the custom values
                        // table
                        command.append("DELETE FROM "
                                       + InternalTable.VALUES.getTableName()
                                       + " WHERE "
                                       + ValuesColumn.COLUMN_NAME.getColumnName()
                                       + " = '"
                                       + del[0]
                                       + "'"
                                       + valuesCmd
                                       + "; ");

                        // Check if the table type represents a structure and a
                        // rate column is deleted
                        if (typeDefn.isStructure() && wasStructure
                            && del[1].equals(InputDataType.RATE.getInputName()))
                        {
                            // Check if the rate name is used by another
                            // structure table type
                            if (rateHandler.getRateInformationByRateName(del[0]).getNumSharedTableTypes() > 1)
                            {
                                // Remove all references to tables of the
                                // changed type in the links and telemetry
                                // scheduler tables
                                command.append(deleteLinkAndTlmRateRef(del[0],
                                                                       linksCmd,
                                                                       tlmSchCmd));
                            }
                            // The rate name is unique to this table type
                            else
                            {
                                // Remove all references to the original rate
                                // column name in the links and
                                // telemetry/scheduler tables
                                command.append(deleteAllLinkAndTlmRateRefs(del[0]));
                            }
                        }
                    }
                }

                List<String> rootStructures = null;

                // Check if the modified type represents a structure
                if (typeDefn.isStructure())
                {
                    // Get the list of root structure tables
                    rootStructures = getRootStructures(editorDialog);
                }

                // Create a field handler to store the data field information
                CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                                     null,
                                                                     editorDialog);

                // Step through each table of the specified type
                for (String tableName : tableNamesList)
                {
                    // Set the flag to indicate if the table is a root
                    // structure
                    boolean isRootStruct = typeDefn.isStructure()
                                           && rootStructures.contains(tableName);

                    // Get the existing data fields for this table
                    fieldHandler.buildFieldInformation(tableName, isRootStruct);

                    // Get the number of separator and line break fields
                    int numSep = fieldHandler.getFieldTypeCount(InputDataType.SEPARATOR);
                    int numBrk = fieldHandler.getFieldTypeCount(InputDataType.BREAK);

                    int sepCount = 0;
                    int brkCount = 0;
                    boolean isChanges = false;

                    // Step through the default data fields for this table type
                    for (FieldInformation fieldInfo : fieldInformation)
                    {
                        // Check if this is a separator
                        if (fieldInfo.getInputType().equals(InputDataType.SEPARATOR))
                        {
                            // Increment the separator counter
                            sepCount++;
                        }
                        // Check if this is a line break
                        else if (fieldInfo.getInputType().equals(InputDataType.BREAK))
                        {
                            // Increment the line break counter
                            brkCount++;
                        }

                        // Check if the data field meets the criteria of a new
                        // field for this table
                        if ((
                            // The table doesn't have this data field
                            (fieldHandler.getFieldInformationByName(tableName,
                                                                    fieldInfo.getFieldName()) == null))

                            // ... and the table isn't a child structure (all
                            // fields are stored for prototypes, even if not
                            // displayed) or the field is applicable to this
                            // table
                            && (!tableName.contains(".")
                            || fieldHandler.isFieldApplicable(tableName,
                                                              fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                              isRootStruct))

                            // ... or the field is a separator and the number
                            // of this type of separator in the type editor
                            // exceeds the number already in the table
                            || (fieldInfo.getInputType().equals(InputDataType.SEPARATOR)
                            && sepCount > numSep)
                            || (fieldInfo.getInputType().equals(InputDataType.BREAK)
                            && brkCount > numBrk))
                        {
                            // Add the data field to the table and set the flag
                            // indicating a change has been made
                            fieldHandler.addField(tableName,
                                                  fieldInfo.getFieldName(),
                                                  fieldInfo.getDescription(),
                                                  fieldInfo.getSize(),
                                                  fieldInfo.getInputType().getInputName(),
                                                  fieldInfo.isRequired(),
                                                  fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                  fieldInfo.getValue());
                            isChanges = true;
                        }
                        // The field exists; check if the existing field
                        // value(s) should be overwritten, and if the field
                        // value(s) changed
                        else if (overwriteFields
                                 && fieldHandler.updateField(new FieldInformation(tableName,
                                                                                  fieldInfo.getFieldName(),
                                                                                  fieldInfo.getDescription(),
                                                                                  fieldInfo.getSize(),
                                                                                  fieldInfo.getInputType().getInputName(),
                                                                                  fieldInfo.isRequired(),
                                                                                  fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                                  fieldInfo.getValue())))
                        {
                            // Set the flag indicating a change has been made
                            isChanges = true;
                        }
                    }

                    // Check if any fields were added
                    if (isChanges)
                    {
                        // Create the command to modify the table's data field
                        // entries
                        command.append(modifyFieldsCommand(tableName,
                                                           fieldHandler.getFieldInformation()));
                    }
                }

                // Convert the list of modified tables names to an array
                tableNames = tableNamesList.toArray(new String[0]);

                // Check if any table of this type exists
                if (tableNames.length != 0)
                {
                    // Convert the array of tables names into a single string
                    // and shorten it if too long
                    names = " and table(s) '</b>"
                            + getShortenedTableNames(tableNames)
                            + "<b>'";
                }

                // Build the command to update the data fields table and the
                // telemetry scheduler table comment (rate parameters)
                command.append(modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeName),
                                                   fieldInformation));
            }

            try
            {
                // Execute the command to change the table type and any table's
                // of this type
                dbCommand.executeDbCommand(command.toString(), editorDialog);

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

            // Check that no errors occurred and if the table type represents
            // or represented a structure
            if (!errorFlag && (typeDefn.isStructure() || wasStructure))
            {
                // Step through each column addition
                for (String add[] : additions)
                {
                    // Check if the column is a rate column
                    if (add[1].equals(InputDataType.RATE.getInputName()))
                    {
                        // Add the rate column to the rate information
                        rateHandler.addRateInformation(add[0]);
                    }
                }

                // Step through each column modification
                for (String[] mod : modifications)
                {
                    // Check if the column changed from a rate column to not
                    // being a rate column
                    if (mod[2].equals(InputDataType.RATE.getInputName())
                        && !mod[3].equals(InputDataType.RATE.getInputName()))
                    {
                        // Delete the rate column from the rate information
                        rateHandler.deleteRateInformation(mod[2]);
                    }
                    // Check if the column changed from not being a rate column
                    // to a rate column
                    else if (!mod[2].equals(InputDataType.RATE.getInputName())
                             && mod[3].equals(InputDataType.RATE.getInputName()))
                    {
                        // Add the rate column to the rate information
                        rateHandler.addRateInformation(mod[3]);
                    }
                    // Check if the column is (and was) a rate column (i.e.,
                    // the rate column name changed)
                    else if (mod[3].equals(InputDataType.RATE.getInputName()))
                    {
                        // Rename (or add if the rate column is shared with
                        // another table type) the rate column in the rate
                        // information
                        rateHandler.renameRateInformation(mod[0], mod[1]);
                    }
                }

                // Step through each column deletion
                for (String[] del : deletions)
                {
                    // Check if the column is a rate column
                    if (del[1].equals(InputDataType.RATE.getInputName()))
                    {
                        // Delete the rate column from the rate information
                        rateHandler.deleteRateInformation(del[0]);
                    }
                }

                // Update the rate column information and store it in the
                // project database
                rateHandler.setRateInformation();
                storeRateParameters(editorDialog);

                // Update the database functions that collect structure table
                // members and structure-defining column data
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

    /**************************************************************************
     * Convert the supplied array of table names into a single string with the
     * names separated by commas. If the length of the string exceeds a
     * specified maximum then shorten the string to the maximum, find the last
     * comma, truncate the string, and add an indication of how many other
     * tables are in the list
     *
     * @return The array of table names converted to a single, comma-separated
     *         string, and shortened if above a maximum length
     *************************************************************************/
    protected String getShortenedTableNames(String[] tableNames)
    {
        // Convert the array of names into a single string
        String names = CcddUtilities.convertArrayToString(tableNames);

        // Check if the length of the table name string exceeds the specified
        // maximum
        if (names.length() > ModifiableSizeInfo.MAX_DIALOG_MESSAGE_LENGTH.getSize())
        {
            // Shorten the name list to the maximum length and find the index
            // to the last comma, which separates the table names
            names = names.substring(0, ModifiableSizeInfo.MAX_DIALOG_MESSAGE_LENGTH.getSize());
            int index = names.lastIndexOf(",");

            // Check if a comma exists
            if (index != -1)
            {
                // Remove any partial name remaining after the truncation,
                // along with the last comma, and add text to indicate how many
                // other names are in the original list
                names = names.substring(0, index);
                names += " ... and "
                         + (tableNames.length - names.split(",").length)
                         + " others";
            }
        }

        return names;
    }

    /**************************************************************************
     * Close the table editors for the specified deleted tables
     *
     * @param tableNames
     *            list containing arrays of information on the table name(s) to
     *            close. For a prototype table each array in the list contains
     *            the prototype name and a null. If the deletion is due to a
     *            change in a variable's structure data type then the array
     *            contains the immediate parent table name and the original
     *            data type (i.e., structure table name) of the variable
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void closeDeletedTableEditors(List<String[]> tableNames,
                                            Component parent)
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

                // Step through each individual table editor in the editor
                // dialog
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Step through the list of deleted tables
                    for (String[] name : tableNames)
                    {
                        // Check if only the prototype name is provided. This
                        // is the case when a prototype table is deleted and
                        // the editors for all tables descending from this
                        // prototype must be closed
                        if (name[1] == null)
                        {
                            // Create the table data type pattern to match
                            // table editors for tables with a data type path
                            // in the format prototypeName[,...] or
                            // [__,]parent[.__],prototypeName.__[,...]
                            pattern = "^([^,]*,)*"
                                      + Pattern.quote(name[0])
                                      + "($|[,\\.].*)";
                        }
                        // The parent name and data type are provided. This is
                        // the case when a variable's data type is altered and
                        // all child tables descending from this variable must
                        // be closed
                        else
                        {
                            // Create the table data type pattern to match
                            // table editors for tables with a data type path
                            // in the format [__,]parent[.__],dataType.__[,...]
                            // if the variable name isn't specified, or
                            // [__,]parent[.__],dataType.varName[,...] is a
                            // variable name is provided
                            pattern = "^([^,]*,)*"
                                      + Pattern.quote(name[0])
                                      + "(\\.[^,]*,|,)"
                                      + Pattern.quote(name[1]);
                            // Check if the data type and variable name are
                            // provided
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

                        // Check if table's data path matches the pattern for
                        // those tables that must have their editors closed
                        if (editor.getTableInformation().getTablePath().matches(pattern))
                        {
                            // Add the table name to the list of tables to
                            // remove. The table editor can't be closed in this
                            // loop since it's iterating over the list of
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

            // Update the tables with Data Type columns
            updateDataTypeColumns(parent);
        }
    }

    /**************************************************************************
     * Update the data type columns in the open table editors
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
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
                    // Update the data type combo box list if the table
                    // contains one
                    editor.setUpDataTypeColumns(allStructureTables,
                                                newTableTree);

                    // Update the variable path column, if present, with the
                    // data type changes
                    editor.updateVariablePaths();

                    // Force the table to repaint to update the highlighting of
                    // the changed data types
                    editor.getTable().repaint();
                }
            }
        }
    }

    /**************************************************************************
     * Modify table data field values in the data fields table. This command is
     * executed in a separate thread since it can take a noticeable amount time
     * to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until
     * the database command completes execution
     *
     * @param modifications
     *            list of data field value modifications
     *
     * @param deletions
     *            list of data field value deletions
     *
     * @param editorWindow
     *            reference to the data field editor
     *************************************************************************/
    protected void modifyDataFields(final List<String[]> modifications,
                                    final List<String[]> deletions,
                                    final CcddFieldTableEditorDialog editorWindow)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, editorWindow, new BackgroundCommand()
        {
            boolean errorFlag = false;

            /******************************************************************
             * Modify table types command
             *****************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    StringBuilder command = new StringBuilder("");

                    // Step through each modification
                    for (String[] mod : modifications)
                    {
                        // Build the command to update the data field value in
                        // the data fields table
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
                        // Build the command to delete the data field from the
                        // data fields table
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
                    eventLog.logEvent(SUCCESS_MSG,
                                      "Table data fields updated");
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

            /******************************************************************
             * Modify data fields command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                editorWindow.doDataFieldUpdatesComplete(errorFlag);
            }
        });
    }

    /**************************************************************************
     * Modify all tables affected by changes to the user-defined data type
     * names, or macro names and/or macro values. This command is executed in a
     * separate thread since it can take a noticeable amount time to complete,
     * and by using a separate thread the GUI is allowed to continue to update.
     * The GUI menu commands, however, are disabled until the database command
     * completes execution
     *
     * @param modifications
     *            list of data type (macro) definition modifications
     *
     * @param updates
     *            list of string arrays reflecting the content of the data
     *            types (macros) after being changed in the data type (macro)
     *            editor
     *
     * @param dialog
     *            reference to the data type or macro editor dialog
     *************************************************************************/
    protected void modifyTablePerDataTypeOrMacroChanges(final List<TableModification> modifications,
                                                        final List<String[]> updates,
                                                        final CcddDialogHandler dialog)
    {
        // Set to true if the change is to a data type, and false if the change
        // is to a macro
        final boolean isDataType = dialog instanceof CcddDataTypeEditorDialog;
        final String changeName;

        final Object newHandler;

        // Check if this is a data type change
        if (isDataType)
        {
            // Create a new data type handler using the updates from the data
            // type editor
            newHandler = new CcddDataTypeHandler(updates);
            changeName = "Data types";
        }
        // This is a macro change
        else
        {
            // Create a new macro handler using the updates from the macro
            // editor
            newHandler = new CcddMacroHandler(updates);
            changeName = "Macros";
        }

        /**********************************************************************
         * Class for table information for those tables modified due to changes
         * in a data type (macro) definition
         *********************************************************************/
        class ModifiedTable
        {
            private final TableInformation tableInformation;
            private final CcddTableEditorHandler editor;

            /******************************************************************
             * Class constructor for table information for those tables
             * modified due to changes in a data type (macro) definition
             *
             * @param tablePath
             *            table path (if applicable) and name
             *****************************************************************/
            ModifiedTable(String tablePath)
            {
                // Load the table's information from the project database
                tableInformation = loadTableData(tablePath,
                                                 !tablePath.contains(","),
                                                 false,
                                                 true,
                                                 false,
                                                 dialog);

                // Check if this is a data type change
                if (isDataType)
                {
                    // Create a table editor handler using the updated data
                    // types, but without displaying the editor itself
                    editor = new CcddTableEditorHandler(ccddMain,
                                                        tableInformation,
                                                        (CcddDataTypeHandler) newHandler);
                }
                // This is a macro change
                else
                {
                    // Create a table editor handler using the updated macros,
                    // but without displaying the editor itself
                    editor = new CcddTableEditorHandler(ccddMain,
                                                        tableInformation,
                                                        (CcddMacroHandler) newHandler);
                }
            }

            /******************************************************************
             * Get the reference to the table information
             *
             * @return Reference to the table information
             *****************************************************************/
            protected TableInformation getTableInformation()
            {
                return tableInformation;
            }

            /******************************************************************
             * Get the reference to the table editor
             *
             * @return Reference to the table editor
             *****************************************************************/
            protected CcddTableEditorHandler getEditor()
            {
                return editor;
            }
        }

        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            boolean errorFlag = false;
            List<ModifiedTable> modifiedTables = new ArrayList<ModifiedTable>();

            /******************************************************************
             * Modify data types (macros) command
             *****************************************************************/
            @Override
            protected void execute()
            {
                TypeDefinition typeDefn = null;

                // Flag that indicates that only a data type (macro) name has
                // been altered, or a data type size where the new size is not
                // larger than the old size. If this remains true for all data
                // type (macro) updates then the project database internal
                // table update process is streamlined, making it much faster
                // in cases where the internal tables reference a large number
                // of variables and tables
                boolean nameChangeOnly = true;

                // Storage for the table's data. This is the table data as it
                // exists in the project database
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
                        // Get the original and updated user-defined data type
                        // names
                        oldName = CcddDataTypeHandler.getDataTypeName(mod.getOriginalRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                                      mod.getOriginalRowData()[DataTypesColumn.C_NAME.ordinal()].toString());
                        newName = CcddDataTypeHandler.getDataTypeName(mod.getRowData()[DataTypesColumn.USER_NAME.ordinal()].toString(),
                                                                      mod.getRowData()[DataTypesColumn.C_NAME.ordinal()].toString());

                        // Get the references to the updated data type
                        references = dataTypeHandler.getDataTypeReferences(oldName, dialog);

                        // Check if only a data type name has been changed thus
                        // far (or if this is the initial pass)
                        if (nameChangeOnly)
                        {
                            // Set to false if the size increased or the base
                            // type changed; keep equal to true if only the
                            // data type name has changed and the size is no
                            // larger
                            nameChangeOnly = Integer.valueOf(mod.getOriginalRowData()[DataTypesColumn.SIZE.ordinal()].toString()) >= Integer.valueOf(mod.getRowData()[DataTypesColumn.SIZE.ordinal()].toString())
                                             && mod.getOriginalRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString().equals(mod.getRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString());
                        }
                    }
                    // This is a macro change
                    else
                    {
                        // Get the original and updated user-defined macro
                        // names
                        oldName = mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString();
                        newName = mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString();

                        // Get the original and updated macro names with the
                        // macro delimiters
                        oldNameDelim = macroHandler.getFullMacroName(mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());
                        newNameDelim = macroHandler.getFullMacroName(mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());

                        // Get the references to the updated macro
                        references = macroHandler.getMacroReferences(oldNameDelim, dialog);

                        // Check if only a macro name has been changed thus far
                        // (or if this is the initial pass)
                        if (nameChangeOnly)
                        {
                            // Set to false if the macro value changes; keep
                            // equal to true if only the macro name has changed
                            nameChangeOnly = mod.getOriginalRowData()[MacrosColumn.VALUE.ordinal()].toString().equals(mod.getRowData()[MacrosColumn.VALUE.ordinal()].toString());
                        }
                    }

                    // Step through each table/column containing the
                    // modification
                    for (String ref : references)
                    {
                        String tableName = null;
                        String changeColumn = null;
                        String matchColumn = null;
                        ModifiedTable modifiedTable = null;

                        // Split the reference into table name, column name,
                        // table type, and context
                        String[] tblColDescAndCntxt = ref.split(TABLE_DESCRIPTION_SEPARATOR, 4);

                        // Create a reference to the search result's database
                        // table name and row data to shorten comparisons below
                        String refTableName = tblColDescAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()];
                        String[] refContext = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                        // Set to true if the referenced table is a prototype
                        // table and false if the reference is to the internal
                        // custom values table
                        boolean isPrototype = !refTableName.startsWith(INTERNAL_TABLE_PREFIX);

                        // Check if the referenced table is a prototype table
                        if (isPrototype)
                        {
                            // Set the viewable table name (with capitalization
                            // intact) and get the column name containing the
                            // data type (macro) reference. Use the primary key
                            // as the matching column criteria
                            tableName = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].split(",", 2)[0];
                            changeColumn = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];
                            matchColumn = refContext[DefaultColumn.PRIMARY_KEY.ordinal()];
                        }
                        // The reference is in the custom values table. Check
                        // if this is a macro change (data type changes are
                        // automatically propagated to the internal tables via
                        // the data table modification process)
                        else if (!isDataType)
                        {
                            // Get the table name from the variable path in the
                            // custom values table and get the column name
                            // containing the macro reference. Use the variable
                            // name as the matching column criteria
                            tableName = refContext[ValuesColumn.TABLE_PATH.ordinal()].replaceAll("(\"|\\s|,[^\\.]*\\.[^,]*$)", "");
                            changeColumn = refContext[ValuesColumn.COLUMN_NAME.ordinal()];
                            matchColumn = refContext[ValuesColumn.TABLE_PATH.ordinal()].replaceAll("(.*\\.|\")", "");
                        }

                        // Step through each table already loaded for
                        // modifications
                        for (ModifiedTable modTbl : modifiedTables)
                        {
                            // Check if the table has already been loaded
                            if (modTbl.getTableInformation().getProtoVariableName().equals(tableName))
                            {
                                // Store the reference to the modified table
                                // and stop searching
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

                        // Use the table's type to get the index for the table
                        // column containing the data type (macro) reference
                        typeDefn = tableTypeHandler.getTypeDefinition(modifiedTable.getEditor().getTableInformation().getType());
                        int changeColumnIndex = isPrototype
                                                           ? typeDefn.getColumnIndexByDbName(changeColumn)
                                                           : typeDefn.getColumnIndexByUserName(changeColumn);

                        // Check if a change was made to the user-defined data
                        // type (macro) name
                        if (!oldName.equals(newName))
                        {
                            // Get the table's data
                            tableData = table.getTableDataList(false);

                            // Step through each row
                            for (int row = 0; row < tableData.size(); row++)
                            {
                                // Check if this is the row in the table data
                                // with the data type (macro) reference. If not
                                // a prototype use the table's type to get the
                                // column index for the variable name
                                if (isPrototype
                                               ? matchColumn.equals(tableData.get(row)[DefaultColumn.PRIMARY_KEY.ordinal()])
                                               : matchColumn.equals(tableData.get(row)[typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE)].toString()))
                                {
                                    // Step through each column in the row,
                                    // skipping the primary key and row index
                                    // columns
                                    for (int column = NUM_HIDDEN_COLUMNS; column < tableData.get(row).length; column++)
                                    {
                                        // Check if this is the column that
                                        // contains the data type (macro)
                                        if (column == changeColumnIndex)
                                        {
                                            // Check if this is a data type
                                            // change
                                            if (isDataType)
                                            {
                                                // Replace the old data type
                                                // name with the new name
                                                table.getModel().setValueAt(newName,
                                                                            row,
                                                                            column);
                                            }
                                            // This is a macro change
                                            else
                                            {
                                                // Replace all instances of the
                                                // old macro name in this cell
                                                // with the new name
                                                table.getModel().setValueAt(macroHandler.replaceMacroName(oldNameDelim,
                                                                                                          newNameDelim,
                                                                                                          tableData.get(row)[column].toString()),
                                                                            row,
                                                                            column);
                                            }

                                            break;
                                        }
                                    }

                                    break;
                                }
                            }
                        }

                        // Check if a change was made to the data type size or
                        // base type, or macro value
                        if ((isDataType
                            && (dataTypeHandler.getSizeInBytes(oldName) != ((CcddDataTypeHandler) newHandler).getSizeInBytes(newName)
                            || !dataTypeHandler.getBaseDataType(oldName).equals(((CcddDataTypeHandler) newHandler).getBaseDataType(newName))))

                            || (!isDataType
                                && macroHandler.getMacroValue(oldName) != null
                                && !macroHandler.getMacroValue(oldName).equals(((CcddMacroHandler) newHandler).getMacroValue(newName))))
                        {
                            // Get the table's data (again if a name change
                            // occurred since changes were made)
                            tableData = table.getTableDataList(false);

                            // Step through each row
                            for (int row = 0; row < tableData.size(); row++)
                            {
                                // Check if this is the row in the table data
                                // with the macro reference. If not a prototype
                                // use the table's type to get the column index
                                // for the variable name
                                if (isPrototype
                                               ? matchColumn.equals(tableData.get(row)[DefaultColumn.PRIMARY_KEY.ordinal()])
                                               : matchColumn.equals(tableData.get(row)[typeDefn.getColumnIndicesByInputType(InputDataType.VARIABLE).get(0)].toString()))
                                {
                                    // Step through each column in the row,
                                    // skipping the primary key and row index
                                    // columns
                                    for (int column = NUM_HIDDEN_COLUMNS; column < tableData.get(row).length; column++)
                                    {
                                        // Check if this is the column that
                                        // contains a data type (macro)
                                        if (column == changeColumnIndex)
                                        {
                                            // Check if the cell value is
                                            // editable
                                            if (table.isCellEditable(row, table.convertColumnIndexToView(column)))
                                            {
                                                // Check if this is a data type
                                                // change
                                                if (isDataType)
                                                {
                                                    // Make the change to the
                                                    // cell, including any
                                                    // updates to changes in
                                                    // array size
                                                    table.validateCellContent(tableData,
                                                                              row,
                                                                              column,
                                                                              oldName,
                                                                              newName,
                                                                              false,
                                                                              true);
                                                }
                                                // This is a macro change
                                                else
                                                {
                                                    // Make the change to the
                                                    // cell, including any
                                                    // updates to changes in
                                                    // array size
                                                    table.validateCellContent(tableData,
                                                                              row,
                                                                              column,
                                                                              tableData.get(row)[column].toString(),
                                                                              macroHandler.replaceMacroName(oldNameDelim,
                                                                                                            newNameDelim,
                                                                                                            tableData.get(row)[column].toString()),
                                                                              false,
                                                                              true);
                                                }

                                                // Load the updated array of
                                                // data into the table
                                                table.loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), false);
                                            }

                                            // Stop searching the columns since
                                            // the target row was located and
                                            // processed
                                            break;
                                        }
                                    }

                                    // Stop searching the rows since the target
                                    // row was located and processed
                                    break;
                                }
                            }
                        }
                    }
                }

                try
                {
                    // Enable creation of a save point in case an error occurs
                    // while modifying a table. This prevents committing the
                    // changes to the database until after all tables are
                    // modified
                    dbCommand.setSavePointEnable(true);

                    // Check if only a change in data type name, data size
                    // (same size or smaller), or macro name occurred; if so
                    // then the internal table update process is simplified in
                    // order to speed it up
                    if (nameChangeOnly)
                    {
                        // Step through each modification in order to update
                        // the variable or macro references in the internal
                        // tables
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

                                // Execute the command to update the internal
                                // tables that reference variable paths
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
                                // Get the original and updated user-defined
                                // macro names (with the delimiters)
                                String oldName = macroHandler.getFullMacroName(mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());
                                String newName = macroHandler.getFullMacroName(mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());

                                // Execute the command to update the internal
                                // tables that reference variable and table
                                // paths
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
                        // Build the additions, modifications, and deletions to
                        // the table
                        modTbl.getEditor().buildUpdates();

                        // Make the table modifications to the project database
                        // and to any open table editors that contain the data
                        // type (macro) reference(s)
                        if (modifyTableData(modTbl.getTableInformation(),
                                            modTbl.getEditor().getAdditions(),
                                            modTbl.getEditor().getModifications(),
                                            modTbl.getEditor().getDeletions(),
                                            true,
                                            nameChangeOnly,
                                            false,
                                            false,
                                            false,
                                            (isDataType
                                                       ? (CcddDataTypeHandler) newHandler
                                                       : null),
                                            (isDataType
                                                       ? null
                                                       : (CcddMacroHandler) newHandler),
                                            dialog))
                        {
                            throw new SQLException("table modification error");
                        }
                    }

                    // Store the data type or macro table
                    dbCommand.executeDbUpdate(storeNonTableTypesInfoTableCommand((isDataType
                                                                                            ? InternalTable.DATA_TYPES
                                                                                            : InternalTable.MACROS),
                                                                                 CcddUtilities.removeArrayListColumn(updates,
                                                                                                                     (isDataType
                                                                                                                                ? DataTypesColumn.OID.ordinal()
                                                                                                                                : MacrosColumn.OID.ordinal())),
                                                                                 null,
                                                                                 dialog),
                                              dialog);

                    // Commit the change(s) to the database
                    dbCommand.getConnection().commit();

                    // Inform the user that the update succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      changeName
                                          + " and all affected tables updated");
                }
                catch (SQLException se)
                {
                    try
                    {
                        // Inform the user that updating the macros failed
                        eventLog.logFailEvent(dialog,
                                              "Cannot update "
                                                  + changeName.toLowerCase()
                                                  + "; cause '"
                                                  + se.getMessage()
                                                  + "'",
                                              "<html><b>Cannot update "
                                                  + changeName.toLowerCase());

                        // Revert the changes to the tables that were
                        // successfully updated prior the current table
                        dbCommand.executeDbCommand("ROLLBACK TO SAVEPOINT "
                                                   + DB_SAVE_POINT_NAME
                                                   + ";",
                                                   dialog);
                    }
                    catch (SQLException se2)
                    {
                        // Inform the user that the reversion to the save point
                        // failed
                        eventLog.logFailEvent(dialog,
                                              "Cannot revert changes to table(s); cause '"
                                                  + se.getMessage()
                                                  + "'",
                                              "<html><b>Cannot revert changes to table(s)");
                    }

                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated
                    // error
                    CcddUtilities.displayException(e, dialog);
                }
                finally
                {
                    // Reset the flag for creating a save point
                    dbCommand.setSavePointEnable(false);
                }
            }

            /******************************************************************
             * Modify data types or macros command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if this is a data type change
                if (isDataType)
                {
                    ((CcddDataTypeEditorDialog) dialog).doDataTypeUpdatesComplete(errorFlag);
                }
                // This is a macro change
                else
                {
                    ((CcddMacroEditorDialog) dialog).doMacroUpdatesComplete(errorFlag);
                }
            }
        });
    }

    /**************************************************************************
     * Get an array containing all prototype tables that represent the
     * specified type
     *
     * @param tableType
     *            TYPE_STRUCTURE to get all tables for any type that represents
     *            a structure, TYPE_COMMAND to get all tables for any type that
     *            represents a command, TYPE_OTHER to get all tables that are
     *            neither a structure or command table, or the table type name
     *            to get all tables for the specified type
     *
     * @return Array containing all prototype tables that represent the
     *         specified type. Returns an empty array if no tables of the
     *         specified type exists in the project database
     *************************************************************************/
    protected String[] getPrototypeTablesOfType(String tableType)
    {
        String[] tablesOfType = new String[0];

        // Step through each table type
        for (String type : tableTypeHandler.getTypes())
        {
            boolean isOfType;

            // Check if the table type matches the one specified. Note that all
            // structure types are handled as one, as are all command types and
            // all 'other' types
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
}
