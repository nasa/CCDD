/**************************************************************************************************
 * /** \file CcddDbTableCommandHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class containing the methods for creating, altering, copying, renaming, and deleting the
 * database tables.
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

import static CCDD.CcddConstants.ROW_NUM_COLUMN_NAME;
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
import static CCDD.CcddConstants.TYPE_ENUM;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;
import static CCDD.CcddConstants.TableMemberType.TABLES_ONLY;

import java.awt.Component;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.BitPackNodeIndex;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.TableInfo;
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

    // Pre-loaded table members used to speed up filter by group
    private List<TableMembers> preLoadedTableMembers;

    // Table tree
    CcddTableTreeHandler tableTree;

    // Characters used to create a unique delimiter for literal strings stored in the database
    private final static String DELIMITER_CHARACTERS = "_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**********************************************************************************************
     * Database table command handler class constructor
     *
     * @param ccddMain Main class
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

        // Set the table tree to null
        tableTree = null;
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
    }

    /**********************************************************************************************
     * Set the table type handler
     *********************************************************************************************/
    protected void setTableTypeHandler()
    {
        // set the table type handler
        tableTypeHandler = ccddMain.getTableTypeHandler();
    }

    /**********************************************************************************************
     * Enclose database text string objects with a delimiter. The default delimiter is single
     * quotes. If the text contains single quotes or a backslash then $$ is used instead, unless
     * the text already contains $$ or ends with $, in which case $_$ is used as the delimiter
     *
     * @param object Object to be stored in the database
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
     * @param tableName Name of the table to check for in the database
     *
     * @param parent    GUI component over which to center any error dialog
     *
     * @return true if the specified table exists in the database
     *********************************************************************************************/
    protected boolean isTableExists(String tableName, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.SPECIFIC_TABLE,
                                 new String[][] {{"_table_name_", tableName.toLowerCase()}}, parent).length != 0;
    }

    /**********************************************************************************************
     * Check if the supplied table name is a root structure table
     *
     * @param tableName Name of the table to check
     *
     * @return true if the supplied table name is a root structure
     *********************************************************************************************/
    protected boolean isRootStructure(String tableName)
    {
        return rootStructures.contains(tableName);
    }

    /**********************************************************************************************
     * Initialize the root structures
     *********************************************************************************************/
    protected void initRootStructures()
    {
        // Get the list of root structure tables
        rootStructures = getRootStructures(ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Get the list of root (top level) structure tables from the project database
     *
     * @param parent GUI component over which to center any error dialog
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
     * @param structuresOnly True to only include structure tables; false to include tables of all
     *                       types
     *
     * @param parent         GUI component over which to center any error dialog
     *
     * @return List of root tables; empty list if there are no root tables
     *********************************************************************************************/
    protected List<String> getRootTables(boolean structuresOnly, Component parent)
    {
        List<String> rootTables = new ArrayList<String>();
        List<TableMembers> tableMembers;

        // Get the prototype tables and their member tables
        tableMembers = getPreLoadedTableMembers();

        // Step through each table
        for (TableMembers member : tableMembers)
        {
            // Check if all table types should be included, or if only structures are to be
            // included that the member is a structure table type
            if (!structuresOnly || tableTypeHandler.getTypeDefinition(member.getTableType()).isStructure())
            {
                boolean isRootTable = true;

                // Step through each table
                for (TableMembers otherMember : tableMembers)
                {
                    // Check if the current table has this table as a member, and that the table
                    // isn't referencing itself
                    if (otherMember.getDataTypes().contains(member.getTableName()) && !member.equals(otherMember))
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
     * @param tableName Table name
     *
     * @param comment   Table comment
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
     * @param tableName Table name
     *
     * @param tableType Table type
     *
     * @return Command to update the table's comment
     *********************************************************************************************/
    private String buildDataTableComment(String tableName, String tableType)
    {
        return buildTableComment(dbControl.getQuotedName(tableName),
                                 CcddConstants.TableCommentIndex.buildComment(tableName, tableType));
    }

    /**********************************************************************************************
     * Build the specified table instance's description update command. The table's description is
     * stored in the custom values table. Blank descriptions aren't stored
     *
     * @param tablePath   Path to the table. For a structure table the format is:
     *                    rootTable,dataType0.variableName0[,
     *                    dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the first data
     *                    type/variable name pair is from the root table, with each succeeding pair
     *                    coming from the next level down in the structure's hierarchy. The path is
     *                    the same as the root table name for non-structure tables
     *
     * @param description Table description
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
     * @param tablePath   Path to the table. For a structure table the format is:
     *                    rootTable,dataType0.variableName0[,
     *                    dataType1.variableName1[,...[,dataTypeN,variableNameN]]]; the first data
     *                    type/variable name pair is from the root table, with each succeeding pair
     *                    coming from the next level down in the structure's hierarchy. The path is
     *                    the same as the root table name for non-structure tables
     *
     * @param columnOrder Table column order
     *
     * @return Command to update the specified table's column order for the current user
     *********************************************************************************************/
    private String buildColumnOrder(String tablePath, String columnOrder)
    {
        return "DELETE FROM "
               + InternalTable.ORDERS.getTableName()
               + " WHERE "
               + OrdersColumn.USER_NAME.getColumnName()
               + " = '" + dbControl.getUser()
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
     * @param sqlCommand PostgreSQL-compatible database query statement
     *
     * @param parent     GUI component over which to center any error dialog
     *
     * @return Two-dimensional array representing the rows and columns of data returned by the
     *         database query; returns null if the query produces an error, or an empty array if
     *         there are no results
     *********************************************************************************************/
    protected List<String[]> queryDatabase(StringBuilder sqlCommand, Component parent)
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
                                  "Database query failed; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Database query failed");
        }

        return queryResults;
    }

    /**********************************************************************************************
     * Retrieve the specified cell's value from the specified table
     *
     * @param tableName  Name database table name
     *
     * @param primaryKey Value of the table's primary key column from which to extract the cell
     *                   value
     *
     * @param columnName Name of the column from which to extract the cell value
     *
     * @param parent     GUI component over which to center any error dialog
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
        List<String[]> results = queryDatabase(new StringBuilder("SELECT ").append(columnName)
                                                                           .append(" FROM ")
                                                                           .append(tableName)
                                                                           .append(" WHERE ")
                                                                           .append(DefaultColumn.PRIMARY_KEY.getDbName())
                                                                           .append(" = ")
                                                                           .append(primaryKey),
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
     * @param parent GUI component over which to center any error dialog
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
     * @param parent GUI component over which to center any error dialog
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
     * @param parent GUI component over which to center any error dialog
     *
     * @return String array containing the table types in alphabetical order
     *********************************************************************************************/
    protected String[] queryTableTypesList(Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLE_TYPES, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of table type data from the database.
     *
     * @param typeName Table type name
     *
     * @param parent   GUI component over which to center any error dialog
     *
     * @return String array containing the table types in alphabetical order
     *********************************************************************************************/
    protected String[][] queryTableTypeDataList(String typeName, Component parent)
    {
        return dbCommand.get2DList(DatabaseListCommand.TABLE_TYPE_DATA, typeName, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all table types names and their descriptions
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return 2D String array containing the table type names and their descriptions
     *********************************************************************************************/
    protected String[][] queryTableTypeNamesAndDescriptions(Component parent)
    {
        return dbCommand.get2DList(DatabaseListCommand.TABLE_TYPE_DESCRIPTIONS, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all tables of a specific type in the database. Any non-data tables are
     * ignored
     *
     * @param tableType Table type by which to filter the table names. The table type is case
     *                  insensitive
     *
     * @param parent    GUI component over which to center any error dialog
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
     * Retrieve an array containing the table name, data type, and variable names for the specified
     * structure table types in the format tableName&lt;,dataType.variableName&gt;
     *
     * @param tableTypes Comma-separated string of table types
     *
     * @param parent     GUI component over which to center any error dialog
     *
     * @return Array containing the table name, data type, and variable names for the specified
     *         table type(s)
     *********************************************************************************************/
    protected String[] queryDataTypeAndVariablesTypeList(String tableTypes, Component parent)
    {
        return dbCommand.getList(DatabaseListCommand.TABLE_DATA_VAR_NAMES,
                                 new String[][] {{"_table_types_", tableTypes}},
                                 parent);
    }

    /**********************************************************************************************
     * Retrieve a list of all tables that represent script files
     *
     * @param parent GUI component over which to center any error dialog
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
     * @param parent GUI component over which to center any error dialog
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
     * @param tableName Table name
     *
     * @param numParts  Number of parts into which to split the comment
     *
     * @param parent    GUI component over which to center any error dialog
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
            ResultSet comment = dbCommand.executeDbQuery(new StringBuilder("SELECT obj_description('public."
                                                         + tableName.toLowerCase()
                                                         + "'::regclass, 'pg_class');"),
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
     * @param tableName Data table name
     *
     * @param parent    Dialog calling this component
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
     * @param parent GUI component over which to center any error dialog
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
                tableDescriptions[index] = descriptions[index].split(Pattern.quote(TABLE_DESCRIPTION_SEPARATOR), 2);
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
     * @param tablePath Table path in the format rootTable[,dataType1.variable1[,dataType2
     *                  .variable2[,...]]]. The table path for a non-structure table is simply the
     *                  root table name. For a structure table the root table is the top level
     *                  structure table from which this table descends. The first data
     *                  type/variable name pair is from the root table, with each succeeding pair
     *                  coming from the next level down in the structure's hierarchy
     *
     * @param parent    GUI component over which to center any error dialog
     *
     * @return Table description; blank if the table has no description
     *********************************************************************************************/
    protected String queryTableDescription(String tablePath, Component parent)
    {
        // Set the initial description to an empty string
        String description = "";
        StringBuilder command = new StringBuilder();

        try
        {
            command.append("SELECT ")
                   .append(ValuesColumn.VALUE.getColumnName())
                   .append(" FROM ")
                   .append(InternalTable.VALUES.getTableName())
                   .append(" WHERE ")
                   .append(ValuesColumn.TABLE_PATH.getColumnName())
                   .append(" = '")
                   .append(tablePath)
                   .append("' AND ")
                   .append(ValuesColumn.COLUMN_NAME.getColumnName())
                   .append(" = '';");

            // Get the description for the table
            ResultSet descData = dbCommand.executeDbQuery(command, parent);

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
                description = queryTableDescription(TableInfo.getPrototypeName(tablePath), parent);
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
     * @param tablePath Comma-separated variable names describing the path to this table, beginning
     *                  with the root table; the names are in the order from highest level to
     *                  lowest level, where the lowest level name is the &lt;variable name&gt;
     *                  portion of the table name; blank if the queried table is a prototype
     *
     * @param tableType Type of table being queried
     *
     * @param parent    GUI component over which to center any error dialog
     *
     * @return Table column order
     *********************************************************************************************/
    private String queryColumnOrder(String tablePath, String tableType, Component parent)
    {
        // Set the column order to the default for this table type
        String columnOrder = tableTypeHandler.getDefaultColumnOrder(tableType);
        StringBuilder command = new StringBuilder();

        try
        {
            // Get the column order for the table
            command.append("SELECT ")
                   .append(OrdersColumn.COLUMN_ORDER.getColumnName())
                   .append(" FROM ")
                   .append(InternalTable.ORDERS.getTableName())
                   .append(" WHERE ")
                   .append(OrdersColumn.USER_NAME.getColumnName())
                   .append(" = '")
                   .append(dbControl.getUser())
                   .append("' AND ")
                   .append(OrdersColumn.TABLE_PATH.getColumnName())
                   .append(" = '")
                   .append(tablePath)
                   .append("';");

            ResultSet orderData = dbCommand.executeDbQuery(command, parent);

            // Check if the column order exists for this table
            if (orderData.next())
            {
                // Get the table column order
                if (orderData.getString(1).length() == columnOrder.length())
                {
                    columnOrder = orderData.getString(1);
                }
                else
                {
                    columnOrder = orderData.getString(1).concat(columnOrder).substring(orderData.getString(1).length());
                }
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
     * @param dbTableName Table name as used by the database
     *
     * @param comments    Array of table comments, with each comment divided into its component
     *                    parts
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
     * @param tableName Table name as used by the database
     *
     * @param comment   Table comment
     *
     * @param parent    GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void setTableComment(String tableName, String comment, Component parent)
    {
        // Execute the database update
        try
        {
            // Build and execute the command to update the table's comment
            dbCommand.executeDbUpdate(new StringBuilder(buildTableComment(tableName, comment)), parent);

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder("Table '" + tableName + "' comment updated"));
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
     * @param fieldTableName Name of the table in the data field table from which to copy
     *
     * @param tableName      Name of the table receiving the data fields
     *
     * @param parent         GUI component over which to center any error dialog
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
     * @param parent GUI component over which to center any error dialog
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
     * @param tableNames  Array of names of the tables to create
     *
     * @param description Description for the new table(s)
     *
     * @param tableType   Type of table to create (e.g., Structure or Command)
     *
     * @param tableDialog Reference to the table manager dialog
     *********************************************************************************************/
    protected void createTableInBackground(final String[] tableNames,
                                           final String description,
                                           final String tableType,
                                           final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, tableDialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Database table creation command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the table(s)
                boolean errorFlag = createTable(tableNames, description, tableType, true, true, tableDialog);

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
     * @param tableNames          Array of names of the tables to create
     *
     * @param description         Description for the new table(s)
     *
     * @param tableType           Type of table to create (e.g., Structure or Command)
     *
     * @param defaultFields       Copy the default fields for the new table's type to the new
     *                            table?
     *
     * @param updatePreloadedInfo true if the pre-loaded table tree information should be updated
     *
     * @param parent              GUI component over which to center any error dialog
     *
     * @return true if an error occurred when creating a table
     *********************************************************************************************/
    protected boolean createTable(String[] tableNames,
                                  String description,
                                  String tableType,
                                  boolean defaultFields,
                                  boolean updatePreloadedInfo,
                                  Component parent)
    {
        boolean errorFlag = false;
        String tableText = tableNames.length == 1 ? "table" : "tables";

        // Convert the array of names into a single string, separated by commas
        String allNames = CcddUtilities.convertArrayToStringTruncate(tableNames);

        try
        {
            StringBuilder command = new StringBuilder();

            // Create a save point in case an error occurs while creating a table
            dbCommand.createSavePoint(parent);

            // Step through each new table name
            for (String tableName : tableNames)
            {
                // Add the command to create the table
                command.append(createTableCommand(tableName,
                                                  description,
                                                  tableType,
                                                  defaultFields,
                                                  parent));

                // Check if the length of the command string has reached the limit
                if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    // Execute the database update
                    dbCommand.executeDbUpdate(command, parent);
                    command = new StringBuilder("");
                }
            }

            if (command.length() != 0)
            {
                // Execute the database update
                dbCommand.executeDbUpdate(command, parent);
            }

            // Release the save point. This must be done within a transaction block, so it must
            // be done prior to the commit below
            dbCommand.releaseSavePoint(parent);

            // Commit the change(s) to the database
            dbControl.getConnection().commit();

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder("'"
                                                + tableType
                                                + "' "
                                                + tableText
                                                + " '"
                                                + allNames
                                                + "' created"));

            // Check if the table tree information should be updated
            if (updatePreloadedInfo)
            {
                updateTableTree();
            }
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot create '"
                                  + tableType
                                  + "' "
                                  + tableText
                                  + " '"
                                  + allNames
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot create '"
                                  + tableType
                                  + "' "
                                  + tableText
                                  + " '</b>"
                                  + allNames
                                  + "<b>'");
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        // An error occurred while creating the table(s)
        if (errorFlag)
        {
            try
            {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(parent);
            }
            catch (SQLException se)
            {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(parent,
                                      "Cannot revert changes to project; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot revert changes to project");
            }
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Build the command to create a table of the specified type
     *
     * @param tableName     Name of the table to create
     *
     * @param description   Description for the new table
     *
     * @param tableType     Type of table to create (e.g., Structure or Command)
     *
     * @param defaultFields Copy the default fields for the new table's type to the new table?
     *
     * @param parent        GUI component over which to center any dialogs
     *
     * @return The command to create the specified table
     *********************************************************************************************/
    private String createTableCommand(String tableName,
                                      String description,
                                      String tableType,
                                      boolean defaultFields,
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
        if (defaultFields)
        {
            command.append(copyDataFieldCommand(CcddFieldHandler.getFieldTypeName(tableType),
                                                tableName,
                                                parent)
                           + dbControl.buildOwnerCommand(DatabaseObject.TABLE, tableName));
        }
        else
        {
            command.append(dbControl.buildOwnerCommand(DatabaseObject.TABLE, tableName));
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Change the name of a table. Only prototype tables can be renamed using this method
     * (instances are renamed by changing the variable name in the prototype). This command is
     * executed in a separate thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the database command completes execution
     *
     * @param tableName      Current name of the table
     *
     * @param newName        New name for this table
     *
     * @param newDescription New description for this table
     *
     * @param tableDialog    Reference to the table manager dialog
     *********************************************************************************************/
    protected void renameTable(final String tableName,
                               final String newName,
                               final String newDescription,
                               final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, tableDialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Rename database table command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;

                try
                {
                    StringBuilder command = new StringBuilder();

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

                    // Create a save point in case an error occurs while renaming a table
                    dbCommand.createSavePoint(tableDialog);

                    // Step through each root table
                    for (String rootTable : getRootTables(false, tableDialog))
                    {
                        // Get the root table's comment and from it get the table's type
                        String[] rootComment = getTableComment(rootTable.toLowerCase(), comments);
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(rootComment[TableCommentIndex.TYPE.ordinal()]);

                        // Bound the root table name in quotes for use below, in case it matches a
                        // reserved word
                        rootTable = dbControl.getQuotedName(rootTable);

                        // Step through each input type used in the table type
                        for (InputType inputType : typeDefn.getInputTypesList())
                        {
                            // Check if the input type represents a data type
                            if (inputType.getInputFormat().equals(InputTypeFormat.DATA_TYPE))
                            {
                                // Step through each data type column index
                                for (Integer column : typeDefn.getColumnIndicesByInputType(inputType))
                                {
                                    // Get the database name of the data type column, quoted in
                                    // case it matches a reserved word
                                    String dataTypeColName = typeDefn.getColumnNamesDatabaseQuoted()[column];

                                    // Append the command to change the references in the root
                                    // table to the original renamed table's name to the new name
                                    command.append("UPDATE ")
                                           .append(rootTable)
                                           .append(" SET ")
                                           .append(dataTypeColName)
                                           .append(" = '")
                                           .append(newName)
                                           .append("' WHERE ")
                                           .append(dataTypeColName)
                                           .append(" = '")
                                           .append(tableName)
                                           .append("'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(command, tableDialog);
                                        command = new StringBuilder("");
                                    }
                                }
                            }

                            // Check if the input type represents a variable reference
                            if (inputType.getInputFormat().equals(InputTypeFormat.VARIABLE_REF))
                            {
                                // Step through each variable reference column index
                                for (Integer column : typeDefn.getColumnIndicesByInputType(inputType))
                                {
                                    // Get the variable reference column name, quoted in case it
                                    // matches a reserved word
                                    String varRefColName = typeDefn.getColumnNamesDatabaseQuoted()[column];

                                    // Append the command to change the references to the renamed
                                    // table in the variable reference to the new name
                                    command.append("UPDATE ")
                                           .append(rootTable)
                                           .append(" SET ")
                                           .append(varRefColName)
                                           .append(" = regexp_replace(")
                                           .append(varRefColName)
                                           .append(", E'(^|,)")
                                           .append(tableName)
                                           .append("(\\\\.|,|$)', E'\\\\1")
                                           .append(newName)
                                           .append("\\\\2'); ");

                                    // Check if the length of the command string has reached the limit
                                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(command, tableDialog);
                                        command = new StringBuilder("");
                                    }
                               }
                            }
                        }
                    }

                    // Check if the old and new names differ in more than capitalization
                    if (!dbTableName.equals(dbNewName))
                    {
                        // Create the command to change the table's name
                        command.append("ALTER TABLE ")
                               .append(dbTableName)
                               .append(" RENAME TO ")
                               .append(dbNewName)
                               .append("; ");
                    }

                    // Update the table comment to reflect the name with case intact
                    command.append(buildDataTableComment(newName, comment[TableCommentIndex.TYPE.ordinal()]));

                    // Create the command to change the table's name in all references to it in the
                    // internal tables
                    command.append(buildTableDescription(newName, newDescription))
                           .append(renameInfoTableCommand(InternalTable.VALUES,
                                                          ValuesColumn.TABLE_PATH.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.LINKS,
                                                          LinksColumn.MEMBER.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.TLM_SCHEDULER,
                                                          TlmSchedulerColumn.MEMBER.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.GROUPS,
                                                          GroupsColumn.MEMBERS.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.FIELDS,
                                                          FieldsColumn.OWNER_NAME.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.ORDERS,
                                                          OrdersColumn.TABLE_PATH.getColumnName(),
                                                          tableName,
                                                          newName))
                           .append(renameInfoTableCommand(InternalTable.ASSOCIATIONS,
                                                          AssociationsColumn.MEMBERS.getColumnName(),
                                                          tableName,
                                                          newName));

                    // Execute the command to change the table's name, including the table's
                    // original name (before conversion to all lower case) that's stored as a
                    // comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Release the save point. This must be done within a transaction block, so it must
                    // be done prior to the commit below
                    dbCommand.releaseSavePoint(tableDialog);

                    // Commit the change(s) to the database
                    dbControl.getConnection().commit();

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Table '"
                                                        + tableName
                                                        + "' renamed to '"
                                                        + newName
                                                        + "'"));

                    // Update the table trees
                    updateTableTree();
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
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, tableDialog);
                    errorFlag = true;
                }

                // Check if no error occurred renaming the table
                if (!errorFlag)
                {
                    tableDialog.doTableOperationComplete();
                }
                // An error occurred while renaming the table
                else
                {
                    try
                    {
                        // Revert any changes made to the database
                        dbCommand.rollbackToSavePoint(tableDialog);
                    }
                    catch (SQLException se)
                    {
                        // Inform the user that rolling back the changes failed
                        eventLog.logFailEvent(tableDialog,
                                              "Cannot revert changes to project; cause '"
                                              + se.getMessage()
                                              + "'",
                                              "<html><b>Cannot revert changes to project");
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Create the command to rename a table reference in an internal table
     *
     * @param intType      Internal table type
     *
     * @param idColumnName Name of the internal table's identifier column
     *
     * @param oldName      Current name of the table
     *
     * @param newName      New name for this table
     *
     * @return Command to rename the table reference in the specified internal table
     *********************************************************************************************/
    private String renameInfoTableCommand(InternalTable intType,
                                          String idColumnName,
                                          String oldName,
                                          String newName)
    {
        // Get the table separator, escaping any special character(s)
        String separator = intType == InternalTable.ASSOCIATIONS ? "|" + assnsSeparator : "";

        return "UPDATE "
               + intType.getTableName()
               + " SET " + idColumnName
               + " = regexp_replace("
               + idColumnName
               + ", E'(^|,"
               + (intType == InternalTable.TLM_SCHEDULER ? "|" + tlmSchSeparator
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
     * @param tableName      Name of the table to copy
     *
     * @param newName        Name of the table copy
     *
     * @param newDescription New description for this table
     *
     * @param tableDialog    Reference to the table manager dialog
     *********************************************************************************************/
    protected void copyTable(final String tableName,
                             final String newName,
                             final String newDescription,
                             final CcddTableManagerDialog tableDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, tableDialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Copy table command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;

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
                                          + "_" + DefaultColumn.PRIMARY_KEY.getDbName()
                                          + "_seq";

                    // Create the command to copy the original table
                    StringBuilder command = new StringBuilder();

                    command.append("CREATE TABLE ")
                           .append(dbNewName)
                           .append(" (LIKE ")
                           .append(dbTableName)
                           .append(" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES); ALTER TABLE ")
                           .append(dbNewName)
                           .append(" ALTER ")
                           .append(DefaultColumn.PRIMARY_KEY.getDbName())
                           .append(" DROP DEFAULT; CREATE SEQUENCE ")
                           .append(sequenceName)
                           .append("; INSERT INTO ")
                           .append(dbNewName)
                           .append(" SELECT * FROM ")
                           .append(dbTableName)
                           .append("; ALTER SEQUENCE ")
                           .append(sequenceName)
                           .append(" OWNED BY ")
                           .append(dbNewName)
                           .append(".")
                           .append(DefaultColumn.PRIMARY_KEY.getDbName())
                           .append("; SELECT setval('")
                           .append(sequenceName)
                           .append("', (SELECT max(")
                           .append(DefaultColumn.PRIMARY_KEY.getDbName())
                           .append(") FROM ")
                           .append(dbTableName)
                           .append("), true); ALTER TABLE ")
                           .append(dbNewName)
                           .append(" ALTER ")
                           .append(DefaultColumn.PRIMARY_KEY.getDbName())
                           .append(" SET DEFAULT nextval('")
                           .append(sequenceName)
                           .append("'); ")
                           .append(dbControl.buildOwnerCommand(DatabaseObject.TABLE, dbNewName))
                           .append(dbControl.buildOwnerCommand(DatabaseObject.SEQUENCE, sequenceName));

                    // Update the table comment to reflect the name with case intact, and create
                    // the commands to duplicate the original table's description and column order
                    command.append(buildDataTableComment(newName, comment[TableCommentIndex.TYPE.ordinal()]))
                           .append(buildTableDescription(newName, newDescription))
                           .append(buildColumnOrder(newName, columnOrder));

                    // Copy the table's data field entries for the new table
                    command.append(copyDataFieldCommand(tableName, newName, tableDialog));

                    // Create a save point in case an error occurs while copying the table
                    dbCommand.createSavePoint(tableDialog);

                    // Execute the command to copy the table, including the table's original name
                    // (before conversion to all lower case) that's stored as a comment
                    dbCommand.executeDbCommand(command, tableDialog);

                    // Release the save point. This must be done within a transaction block, so it
                    // must be done prior to the commit below
                    dbCommand.releaseSavePoint(tableDialog);

                    // Commit the change(s) to the database
                    dbControl.getConnection().commit();

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Table '"
                                                        + tableName
                                                        + "' copied to '"
                                                        + newName
                                                        + "'"));

                    // Update the table trees
                    updateTableTree();
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
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, tableDialog);
                    errorFlag = true;
                }

                // Check if no error occurred copying the table
                if (!errorFlag)
                {
                    tableDialog.doTableOperationComplete();
                }
                // An error occurred while copying the table
                else
                {
                    try
                    {
                        // Revert any changes made to the database
                        dbCommand.rollbackToSavePoint(tableDialog);
                    }
                    catch (SQLException se)
                    {
                        // Inform the user that rolling back the changes failed
                        eventLog.logFailEvent(tableDialog,
                                              "Cannot revert changes to project; cause '"
                                              + se.getMessage()
                                              + "'",
                                              "<html><b>Cannot revert changes to project");
                    }
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
     * @param tableNames Array of names of the tables to delete
     *
     * @param dialog     Reference to the table manager dialog calling this method; null if not
     *                   called by the table manager
     *
     * @param parent     GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void deleteTableInBackground(final String[] tableNames,
                                           final CcddTableManagerDialog dialog,
                                           final Component parent)
    {
        String tableText = tableNames.length == 1 ? "Table" : "Tables";

        // Convert the array of names into a single string
        final String names = CcddUtilities.convertArrayToStringTruncate(tableNames);

        // Have the user confirm deleting the selected table(s)
        if (new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Delete "
                                                      + tableText
                                                      + " '</b>"
                                                      + names
                                                      + "<b>'?<br><br><i>Warning: This action cannot be undone!",
                                                      "Delete Table(s)",
                                                      JOptionPane.QUESTION_MESSAGE,
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Execute the command in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
            {
                /**********************************************************************************
                 * Database table deletion command
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    // Delete the table(s)
                    deleteTable(tableNames, dialog != null, parent);

                    // Check if the table manager called this method
                    if (dialog != null)
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
     * editors for tables of this prototype are closed. If an error occurs, one or more tables may
     * have been deleted; a list of the undeleted tables is displayed and logged
     *
     * @param tableNames  Array of names of the tables to delete
     *
     * @param isDataTable true is the tables to be deleted are data tables; false if the tables
     *                    are scripts or internal tables
     *
     * @param parent      GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void deleteTable(String[] tableNames, boolean isDataTable, Component parent)
    {
        String latestTable = null;

        // Convert the array of names into a single string
        String names = CcddUtilities.convertArrayToStringTruncate(tableNames);

        String tableText = tableNames.length == 1 ? "Table" : "Tables";

        try
        {
            // Step through the array of table names
            for (String name : tableNames)
            {
                latestTable = name;

                // Build the command and delete the table
                dbCommand.executeDbUpdate(new StringBuilder(deleteTableCommand(name, isDataTable)),
                                          parent);
            }

            latestTable = null;

            // Check if the deletion is for a data table
            if (isDataTable)
            {
                // Execute the command to reset the rate for links that no longer contain any
                // variables
                dbCommand.executeDbQuery(new StringBuilder("SELECT reset_link_rate();"), parent);
            }

            // Log that the table deletion succeeded
            eventLog.logEvent(SUCCESS_MSG,
                              new StringBuilder(tableText + " '" + names + "' deleted"));
        }
        catch (SQLException se)
        {
            // Check if the error occurred when attempting to delete a table
            if (latestTable != null)
            {
                // Check if the table that failed to delete is not the first in the list
                if (!latestTable.equals(tableNames[0]))
                {
                    // Get the names of the tables that failed to be deleted
                    List<String> tables = Arrays.asList(tableNames);
                    int index = tables.indexOf(latestTable);
                    tables.subList(0, index).clear();
                    names = CcddUtilities.convertArrayToStringTruncate(tables.toArray(new String[0]));
                }

                // Inform the user that deleting the table(s) failed
                eventLog.logFailEvent(parent,
                                      "Cannot delete "
                                      + tableText.toLowerCase()
                                      + " '"
                                      + names
                                      + "'"
                                      + "; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot delete "
                                      + tableText.toLowerCase()
                                      + " '</b>"
                                      + names
                                      + "<b>'");
            }
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
        }

        // Check if any table was deleted
        if (latestTable == null || !latestTable.equals(tableNames[0]))
        {
            // Update the table tree information
            updateTableTree();
        }
    }

    /**********************************************************************************************
     * Build the command to delete a prototype or script table
     *
     * @param tableName   Name of the table to delete
     *
     * @param isDataTable true if the table(s) to be deleted are data tables; false if the tables
     *                    are scripts or internal tables
     *
     * @return Command to delete one or more prototype or script tables
     *********************************************************************************************/
    private String deleteTableCommand(String tableName, boolean isDataTable)
    {
        // Build the table deletion commands
        StringBuilder command = new StringBuilder("DROP TABLE IF EXISTS ").append(dbControl.getQuotedName(tableName))
                                                                          .append(" CASCADE");

        // Check if this is a data table
        if (isDataTable)
        {
            StringBuilder valuesDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.VALUES.getTableName())
                                                                            .append(" WHERE ");
            StringBuilder linksDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.LINKS.getTableName())
                                                                           .append(" WHERE ");
            StringBuilder tlmSchDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.TLM_SCHEDULER.getTableName())
                                                                            .append(" WHERE ");
            StringBuilder groupsDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.GROUPS.getTableName())
                                                                            .append(" WHERE ");
            StringBuilder fieldsDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.FIELDS.getTableName())
                                                                            .append(" WHERE ");
            StringBuilder ordersDelCmd = new StringBuilder("; DELETE FROM ").append(InternalTable.ORDERS.getTableName())
                                                                            .append(" WHERE ");
            StringBuilder infoCmd = new StringBuilder();
            StringBuilder assnsUpdCmd = new StringBuilder();

            // Add the table to the commands
            infoCmd.append("col1 ~ E'^")
                   .append(tableName)
                   .append("$' OR col1 ~ E'^")
                   .append(tableName)
                   .append(",' OR col1 ~ E',")
                   .append(tableName)
                   .append("\\\\.' OR ");

            // Build the command to update the script associations table by removing references
            // to this table
            assnsUpdCmd.append("; UPDATE ")
                       .append(InternalTable.ASSOCIATIONS.getTableName())
                       .append(" SET ")
                       .append(AssociationsColumn.MEMBERS.getColumnName())
                       .append(" = regexp_replace(")
                       .append(AssociationsColumn.MEMBERS.getColumnName())
                       .append(", E'(?:(?:^|(?:")
                       .append(PATH_IDENT)
                       .append(",))")
                       .append(tableName)
                       .append("(?:(?:,|\\\\.)")
                       .append(PATH_IDENT)
                       .append(")?")
                       .append(assnsSeparator)
                       .append("|")
                       .append(assnsSeparator)
                       .append("(?:^| \\+ |")
                       .append(PATH_IDENT)
                       .append(",)?")
                       .append(tableName)
                       .append("(?:(?:,|\\\\.)")
                       .append(PATH_IDENT)
                       .append(")?)', E'', 'g'); UPDATE ")
                       .append(InternalTable.ASSOCIATIONS.getTableName())
                       .append(" SET ")
                       .append(AssociationsColumn.MEMBERS.getColumnName())
                       .append(" = '' WHERE ")
                       .append(AssociationsColumn.MEMBERS.getColumnName())
                       .append(" ~ E'^(?:")
                       .append(PATH_IDENT)
                       .append(",)?")
                       .append(tableName)
                       .append("(?:(?:,|\\\\.)")
                       .append(PATH_IDENT)
                       .append(")?$'");

            // Replace the column 1 name placeholder with the table's column 1 name, then add the
            // update string to the command. In the telemetry scheduler table the table names are
            // preceded by the rate value and a separator which must be skipped
            valuesDelCmd.append(infoCmd.toString().replaceAll("col1", ValuesColumn.TABLE_PATH.getColumnName()));
            linksDelCmd.append(infoCmd.toString().replaceAll("col1", LinksColumn.MEMBER.getColumnName()));
            tlmSchDelCmd.append(infoCmd.toString().replaceAll("col1", TlmSchedulerColumn.MEMBER.getColumnName())
                                                  .replaceAll("\\^",
                                                              "^\\\\\\\\d+\\\\\\\\.\\\\\\\\d+"
                                                              + "\\\\\\\\\\\\\\"
                                                              + TLM_SCH_SEPARATOR));
            groupsDelCmd.append(infoCmd.toString().replaceAll("col1", GroupsColumn.MEMBERS.getColumnName()));
            fieldsDelCmd.append(infoCmd.toString().replaceAll("col1", FieldsColumn.OWNER_NAME.getColumnName()));
            ordersDelCmd.append(infoCmd.toString().replaceAll("col1", OrdersColumn.TABLE_PATH.getColumnName()));

            // Add the internal table commands to the table deletion command
            command.append(CcddUtilities.removeTrailer(valuesDelCmd, " OR "))
                   .append(CcddUtilities.removeTrailer(linksDelCmd, " OR "))
                   .append(CcddUtilities.removeTrailer(tlmSchDelCmd, " OR "))
                   .append(CcddUtilities.removeTrailer(groupsDelCmd, " OR "))
                   .append(CcddUtilities.removeTrailer(fieldsDelCmd, " OR "))
                   .append(CcddUtilities.removeTrailer(ordersDelCmd, " OR "))
                   .append(assnsUpdCmd);
        }

        command.append(";");
        return command.toString();
    }

    /**********************************************************************************************
     * Load the contents of a single database table and display the data in a table editor. Do not
     * load the table if it is already open for editing. If the table is already open, then the
     * editor for this table is brought to the foreground. This command is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by using a separate
     * thread the GUI is allowed to continue to update. The GUI menu commands, however, are
     * disabled until the database command completes execution
     *
     * @param tablePath        Table path in the format rootTable[,dataType1.variable1[,dataType2
     *                         .variable2[,...]]]. The table path for a non-structure table is
     *                         simply the root table name. For a structure table the root table is
     *                         the top level structure table from which this table descends. The
     *                         first data type/variable name pair is from the root table, with each
     *                         succeeding pair coming from the next level down in the structure's
     *                         hierarchy
     *
     * @param currentEditorDlg Editor dialog to open the table in; null to open the table in a new
     *                         editor dialog
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
     * @param tablePaths       Array of paths of the tables to load in the format
     *                         rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]]. The
     *                         table path for a non-structure table is simply the root table name.
     *                         For a structure table the root table is the top level structure
     *                         table from which this table descends. The first data type/variable
     *                         name pair is from the root table, with each succeeding pair coming
     *                         from the next level down in the structure's hierarchy
     *
     * @param callingEditorDlg Table editor dialog in which to open the table; null to open the
     *                         table in a new editor dialog
     *
     * @return SwingWorker reference for this thread
     *********************************************************************************************/
    protected SwingWorker<?, ?> loadTableDataInBackground(final String[] tablePaths,
                                                          final CcddTableEditorDialog callingEditorDlg)
    {
        // Execute the command in the background
        return CcddBackgroundCommand.executeInBackground(ccddMain, callingEditorDlg, new BackgroundCommand()
        {
            private final List<TableInfo> tableInformation = new ArrayList<TableInfo>();

            CcddTableEditorDialog tableEditorDlg = null;
            CcddTableEditorHandler tableEditor = null;

            // Get the component over which any dialogs need to be centered
            Component parent = (callingEditorDlg == null) ? ccddMain.getMainFrame() : callingEditorDlg;

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
                            TableInfo tableInfo = loadTableData(tablePaths[index], true, true, false, parent);

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
     * @param tablePath       Table path in the format rootTable[,dataType1.variable1[,dataType2
     *                        .variable2[,...]]]. The table path for a non-structure table is
     *                        simply the root table name. For a structure table the root table is
     *                        the top level structure table from which this table descends. The
     *                        first data type/variable name pair is from the root table, with each
     *                        succeeding pair coming from the next level down in the structure's
     *                        hierarchy
     *
     * @param loadDescription True to load the table's description
     *
     * @param loadColumnOrder True to load the table's column order
     *
     * @param ignoreErrors    Should errors be ignored
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return TableInformation class containing the table data from the database. If the error
     *         flag is set the an error occurred and the data is invalid
     *********************************************************************************************/
    protected TableInfo loadTableData(String tablePath,
                                      boolean loadDescription,
                                      boolean loadColumnOrder,
                                      boolean ignoreErrors,
                                      Component parent)
    {
        // Create an empty table information class
        TableInfo tableInfo = new TableInfo(tablePath);

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

            // Get the column names and number
            List<String> columnNames = Arrays.asList(typeDefn.getColumnNamesDatabase());
            int numColumns = typeDefn.getColumnCountDatabase();

            // Get the table's row information for the specified columns. The table must have all
            // of its table type's columns or else it fails to load
            StringBuilder command = new StringBuilder();
            command.append("SELECT * FROM ")
                   .append(dbTableName)
                   .append(" ORDER BY ")
                   .append(DefaultColumn.ROW_INDEX.getDbName())
                   .append(";");
            ResultSet rowData = dbCommand.executeDbQuery(command, parent);

            // Create a list to contain the database table rows
            List<Object[]> dbRows = new ArrayList<Object[]>();

            // Step through each of the query results
            while (rowData.next())
            {
                // Create an array to contain the column values
                Object[] columnValues = new Object[numColumns];

                ResultSetMetaData metaData = rowData.getMetaData();

                // Step through each column in the row
                for (int index = 0; index < numColumns; index++)
                {
                    // Check which column should be updated as they may not be in the expected
                    // order
                    int column = columnNames.indexOf(metaData.getColumnName(index + 1));

                    // Add the column value to the array. Note that the first column's index in the
                    // database is 1, not 0
                    columnValues[column] = rowData.getString(index + 1);

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
                        columnValues[column] = columnValues[column].toString().equalsIgnoreCase("true") ? true : false;
                    }
                }

                // Add the row data to the list
                dbRows.add(columnValues);
            }

            rowData.close();

            // Create the table information handler for this table
            tableInfo = new TableInfo(comment[TableCommentIndex.TYPE.ordinal()],
                                      tablePath,
                                      dbRows.toArray(new Object[0][0]),
                                      (loadColumnOrder ? queryColumnOrder(tablePath,
                                                                          comment[TableCommentIndex.TYPE.ordinal()],
                                                                          parent)
                                                       : ""),
                                      (loadDescription ? queryTableDescription(tablePath, parent) : ""),
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
                    for (int row = 0; row < tableInfo.getData().size(); row++)
                    {
                        // Blank the variable path. This prevents the child table from inheriting a
                        // user-defined variable path from the prototype
                        tableInfo.getData().get(row)[varPathIndex] = "";
                    }
                }

                // Place double back slashes before each square brace character in an array index
                // so that the brackets are interpreted correctly in the query's regular expression
                // comparisons
                tablePath = tablePath.replaceAll("\\[(\\d+)\\]", "\\\\\\\\[$1\\\\\\\\]");

                // Get the rows from the custom values table that match the specified parent table
                // and variable path. These values replace those loaded for the prototype of this
                // table
                command = new StringBuilder();
                command.append("SELECT ")
                       .append(ValuesColumn.TABLE_PATH.getColumnName())
                       .append(", ")
                       .append(ValuesColumn.COLUMN_NAME.getColumnName())
                       .append(", ")
                       .append(ValuesColumn.VALUE.getColumnName())
                       .append(" FROM ")
                       .append(InternalTable.VALUES.getTableName())
                       .append(" WHERE ")
                       .append(ValuesColumn.TABLE_PATH.getColumnName())
                       .append(" ~ E'^")
                       .append(tablePath)
                       .append(",[^,]+$' AND ")
                       .append(ValuesColumn.COLUMN_NAME.getColumnName())
                       .append(" != '';");
                rowData = dbCommand.executeDbQuery(command, parent);

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
                                && tableInfo.getData().get(row)[dataTypeIndex].equals(variableName.subSequence(variableName.lastIndexOf(",") + 1, varIndex)))
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
                                    tableInfo.getData().get(row)[column] = rowData.getString(ValuesColumn.VALUE.getColumnName()).equalsIgnoreCase("true") ? true : false;
                                }
                                // Not a boolean
                                else
                                {
                                    // Replace the value in the table with the one from the custom
                                    // values table
                                    tableInfo.getData().get(row)[column] = rowData.getString(ValuesColumn.VALUE.getColumnName());
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
            if (!ignoreErrors)
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
     * @param columnName  Name to match in the custom values table 'column name' column
     *
     * @param columnValue Value to match in the custom values table 'value' column; null or blank
     *                    to match any value
     *
     * @param parent      GUI component over which to center any error dialog
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
            StringBuilder command = new StringBuilder();
            command.append("SELECT ")
                   .append(ValuesColumn.TABLE_PATH.getColumnName())
                   .append(", ")
                   .append(ValuesColumn.COLUMN_NAME.getColumnName())
                   .append(", ")
                   .append(ValuesColumn.VALUE.getColumnName())
                   .append(" FROM ")
                   .append(InternalTable.VALUES.getTableName())
                   .append(" WHERE ")
                   .append(ValuesColumn.COLUMN_NAME.getColumnName())
                   .append(" = '")
                   .append(columnName)
                   .append("'")
                   .append((columnValue == null || columnValue.isEmpty() ? ""
                                                                         : " AND "
                                                                           + ValuesColumn.VALUE.getColumnName()
                                                                           + " = '"
                                                                           + columnValue
                                                                           + "'"))
                   .append(";");
            ResultSet rowData = dbCommand.executeDbQuery(command, parent);

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
     * @param parent Component calling this method, used for positioning any error dialogs
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
     * Clear the pre-loaded table members list
     *********************************************************************************************/
    protected void clearPreLoadedTableMembers()
    {
        preLoadedTableMembers = null;
    }

    /**********************************************************************************************
     * Update the pre-loaded table members
     *********************************************************************************************/
    protected void updatePreLoadedTableMembers()
    {
        preLoadedTableMembers = loadTableMembers(TABLES_ONLY, true, ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Retrieve the pre-loaded table members
     *
     * @return A list of table members
     *********************************************************************************************/
    protected List<TableMembers> getPreLoadedTableMembers()
    {
        // Load the pre-loaded table member list if it isn't uninitialized
        if (preLoadedTableMembers == null)
        {
            updatePreLoadedTableMembers();
        }

        return preLoadedTableMembers;
    }

    /**********************************************************************************************
     * Create a list of all prototype tables with their child tables (prototypes and instances),
     * and primitive variables (if specified). The table must contain all of the protected columns
     * defined for a structure in order for its members to be determined. Non-structure tables are
     * included in the returned list, but by definition have no members
     *
     * @param memberType Type of table members to load: TABLES_ONLY to exclude primitive variables
     *                   or INCLUDE_PRIMITIVES to include tables and primitive variables
     *
     * @param sortByName True to return the table members in alphabetical order (e.g., for use in a
     *                   tree); false to return the members sorted by row index (e.g., for use in
     *                   determining the variable offsets in the structure)
     *
     * @param parent     GUI component over which to center any error dialog
     *
     * @return List containing the table member information. For structure tables the member tables
     *         are included, along with primitive variables (if specified), sorted by variable name
     *         or row index as specified. If an error occurs the null is returned
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
            // table index (note: when sorted by name the array members, if any, are in the correct
            // numerical order). Non-structure tables and structure tables with no rows are
            // skipped. The information returned by the PostgreSQL function also returns the bit
            // length, rate(s), and enumeration(s) for each member; the enumeration information
            // currently isn't used
            ResultSet rowData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM ").append(sortByName ? "get_table_members_by_name();"
                                                                                                               : "get_table_members_by_index();"),
                                                         parent);

            // Create a list to contain the database table member data types, variable names, bit
            // lengths, and rates
            List<String> dataTypes;
            List<String> variableNames;
            List<String> bitLengths;
            List<String[]> rates;

            int numRateColumns = rateHandler.getNumRateColumns();

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
                    String[] rate = rowData.getString(5).split(",", numRateColumns);

                    // Check if a data type and variable name exist, and that the data type is not
                    // a primitive type (i.e., this is a structure) or if primitive types are to be
                    // included
                    if (dataType != null
                        && !dataType.isEmpty()
                        && variableName != null
                        && !variableName.isEmpty()
                        && (!dataTypeHandler.isPrimitive(dataType) || memberType == TableMemberType.INCLUDE_PRIMITIVES))
                    {
                        // Add the data type, variable name, bit length, rate(s), and
                        // enumeration(s) to the lists for this table
                        dataTypes.add(dataType);
                        variableNames.add(variableName);
                        bitLengths.add(bitLength);
                        rates.add(rate);
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
                    // Grab the table info
                    TableInfo tableInfo = loadTableData(tableName.toLowerCase(),
                                                        false,
                                                        true,
                                                        false,
                                                        parent);

                    // CHek if the table data cannot be loaded
                    if (tableInfo == null)
                    {
                        throw new CCDDException("table " + tableName + " data not found");
                    }

                    // Check if this table is of type ENUM
                    if (tableInfo.getType().equals(TYPE_ENUM) && memberType == TableMemberType.INCLUDE_PRIMITIVES)
                    {
                        // If this table is of type enum the var names must be added to a list
                        // before adding this table to the newMembers list. For each var name a
                        // blank entry is added to data type, rates and bit lengths. They do not
                        // apply to enums, but the lists need to be the same size regardless
                        List<String> EnumBitLenths = new ArrayList<String>();
                        List<String> EnumDataTypes = new ArrayList<String>();
                        List<String[]> EnumRates = new ArrayList<String[]>();
                        List<String> EnumVarNames = new ArrayList<String>();

                        // Get the type definition
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(TYPE_ENUM);

                        // Step through each row of data and populate the lists
                        for (int index = 0; index < tableInfo.getData().size(); index++)
                        {
                            EnumVarNames.add(tableInfo.getData().get(index)[typeDefn.getColumnIndexByUserName(DefaultColumn.ENUM_NAME.getName())].toString());
                            EnumDataTypes.add("");
                            EnumBitLenths.add("");
                            String[] newRate = {""};
                            EnumRates.add(newRate);
                        }

                        // Get the comment array for this table
                        String[] comment = getTableComment(tableName, comments);

                        // Add the table to the member list with empty data type, variable name,
                        // bit length, and rate lists
                        newMembers.add(new TableMembers(tableName,
                                                        comment[TableCommentIndex.TYPE.ordinal()],
                                                        EnumDataTypes,
                                                        EnumVarNames,
                                                        EnumBitLenths,
                                                        EnumRates));
                    }
                    else
                    {
                        // Get the comment array for this table
                        String[] comment = getTableComment(tableName, comments);

                        // Add the table to the member list with empty data type, variable name,
                        // bit length, and rate lists
                        newMembers.add(new TableMembers(tableName,
                                                        comment[TableCommentIndex.TYPE.ordinal()],
                                                        new ArrayList<String>(),
                                                        new ArrayList<String>(),
                                                        new ArrayList<String>(),
                                                        new ArrayList<String[]>()));
                    }
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
        catch (SQLException | CCDDException se)
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

    /**********************************************************************************************
     * Add, modify, and/or delete data in a table. This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however, are disabled until the
     * database command completes execution
     *
     * @param tableInfo          Table information
     *
     * @param additions          List of row addition information
     *
     * @param modifications      List of row update information
     *
     * @param deletions          List of row deletion information
     *
     * @param forceUpdate        True to make the changes to other tables; false to only make
     *                           changes to tables other than the one in which the changes
     *                           originally took place
     *
     * @param skipInternalTables True to not build and execute the commands to update the internal
     *                           tables. This is used during a data type update where only the data
     *                           type name has changed in order to speed up the operation
     *
     * @param updateDescription  True to update the table description from the table information;
     *                           false to not change the table description
     *
     * @param updateColumnOrder  True to update the table column order from the table information;
     *                           false to not change the table column order
     *
     * @param updateFieldInfo    True to update the table data fields from the table information;
     *                           false to not change the table data fields
     *
     * @param newDataTypeHandler Data type handler with data type modifications. null (or a
     *                           reference to the current data type handler) if the change does not
     *                           originate from the data type editor
     *
     * @param parent             GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void modifyTableDataInBackground(final TableInfo tableInfo,
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
        CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
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
                                true,
                                true,
                                false,
                                parent);

                // Update the table tree
                updateTableTree();
            }
        });
    }

    /**********************************************************************************************
     * Add, modify, and/or delete data in a table. If the table is a prototype then its database
     * table is altered; if the table is an instance then the changes are made to the custom values
     * table
     *
     * @param tableInfo          Table information
     *
     * @param additions          List of row addition information
     *
     * @param modifications      List of row update information
     *
     * @param deletions          List of row deletion information
     *
     * @param forceUpdate        True to make the changes to other data tables; false to only make
     *                           changes to tables other than the one in which the changes
     *                           originally took place
     *
     * @param skipInternalTables True to not build and execute the commands to update the internal
     *                           tables. This is used during a data type update where only the data
     *                           type name has changed in order to speed up the operation
     *
     * @param updateDescription  True to update the table description from the table information;
     *                           false to not change the table description
     *
     * @param updateColumnOrder  True to update the table column order from the table information;
     *                           false to not change the table column order
     *
     * @param updateFieldInfo    True to update the table data fields from the table information;
     *                           false to not change the table data fields
     *
     * @param newDataTypeHandler Data type handler with data type modifications. null (or a
     *                           reference to the current data type handler) if the change does not
     *                           originate from the data type editor
     *
     * @param updateListsAndRefs Should lists and references be updated?
     *
     * @param isRootTable        Does the tableInfo belong to a root table? If 'updateListsAndRefs'
     *                           is true then it does not matter what value this is set to.
     *
     * @param ignoreErrors       Should errors be ignored
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return true if an error occurs while updating the table
     *********************************************************************************************/
    protected boolean modifyTableData(TableInfo tableInfo,
                                      List<TableModification> additions,
                                      List<TableModification> modifications,
                                      List<TableModification> deletions,
                                      boolean forceUpdate,
                                      boolean skipInternalTables,
                                      boolean updateDescription,
                                      boolean updateColumnOrder,
                                      boolean updateFieldInfo,
                                      CcddDataTypeHandler newDataTypeHandler,
                                      boolean updateListsAndRefs,
                                      boolean isRootTable,
                                      boolean ignoreErrors,
                                      Component parent)
    {
        boolean errorFlag = false;
        ReferenceCheckResults msgIDRefChk = null;
        boolean isRefFieldChange = false;
        boolean isStructure = false;

        try
        {
            ToolTipTreeNode orgTableNode = null;
            updateLinks = false;
            addLinkHandler = null;
            isMsgIDChange = false;
            ReferenceCheckResults varRefChk = null;
            ReferenceCheckResults cmdRefChk = null;

            // Check if the current field information differs from that stored in the database
            if (fieldHandler.isFieldInformationChanged())
            {
                // Store the data fields
                storeInformationTable(InternalTable.FIELDS,
                                      fieldHandler.getFieldDefnsFromInfo(),
                                      null,
                                      parent);
            }

            // Build the command list for every command defined in the database
            commandHandler.buildCommandList();

            if (updateListsAndRefs)
            {
                // Update all lists and references
                rootStructures = getRootStructures(parent);
                variableHandler.buildPathAndOffsetLists();
                inputTypeHandler.updateMessageReferences(parent);

                // Create the table tree. Suppress any warning messages when creating this tree
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                     true,
                                                     parent);
            }
            else if (isRootTable)
            {
                // Update the root structures list
                rootStructures = getRootStructures(parent);
            }

            // Get the name of the table to modify and convert the table name to lower case.
            // PostgreSQL automatically does this, so it's done here just to differentiate the
            // table name from the database commands in the event log
            String dbTableName = dbControl.getQuotedName(tableInfo.getPrototypeName());

            // Get the table type definition
            TypeDefinition typeDefinition = tableTypeHandler.getTypeDefinition(tableInfo.getType());
            isStructure = typeDefinition.isStructure();

            // Check if references in the internal tables are to be updated and the table
            // represents a structure
            if (!skipInternalTables && isStructure)
            {
                deletedArrayDefns = new ArrayList<String>();

                if (tableTree == null)
                {
                    // Create the table tree. Suppress any warning messages when creating this tree
                    tableTree = new CcddTableTreeHandler(ccddMain,
                                                         TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                         true,
                                                         parent);
                }

                // Check if the table is a prototype
                if (tableInfo.isPrototype())
                {
                    // Copy the table tree node for the prototype table. This preserves a copy of
                    // the table's variables before the changes are applied
                    orgTableNode = copyPrototypeTableTreeNode(tableInfo.getPrototypeName(), tableTree);
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
            else if (typeDefinition.isCommand() && (!modifications.isEmpty() || !deletions.isEmpty()))
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
                && description.equals(queryTableDescription(tableInfo.getPrototypeName(),
                                                            parent)))
            {
                // Set the description to a blank since it inherits it from the prototype
                description = "";
            }

            // Combine the table, data fields table, table description, and column order update
            // commands. If the flag is set to update the data fields then the entire fields table
            // is rewritten. This must precede applying the table updates since these makes further
            // changes to the fields table
            boolean commandExecuted = false;

            // Create a save point in case an error occurs while modifying a table
            dbCommand.createSavePoint(parent);

            StringBuilder command = new StringBuilder((updateFieldInfo ? modifyFieldsCommand(tableInfo.getTablePath(),
                                                                                             tableInfo.getFieldInformation())
                                                                       : "")
                                                      + (updateDescription ? buildTableDescription(tableInfo.getTablePath(),
                                                                                                   description)
                                                                           : "")
                                                      + (updateColumnOrder ? buildColumnOrder(tableInfo.getTablePath(),
                                                                                              tableInfo.getColumnOrder())
                                                                           : "")
                                                      + modifyInternalFieldsTable(additions));

            if (command.length() != 0)
            {
                dbCommand.executeDbUpdate(command, parent);
                commandExecuted = true;
                command = null;
            }

            // Build the commands to add, modify, and delete table rows, and to update any table
            // cells or data fields that have the message name & ID input type if a message name or
            // ID value is changed
            if (!deletions.isEmpty())
            {
                if (buildAndExecuteDeletionCommand(tableInfo,
                                                   deletions,
                                                   dbTableName,
                                                   typeDefinition,
                                                   skipInternalTables,
                                                   varRefChk,
                                                   cmdRefChk,
                                                   parent))
                {
                    commandExecuted = true;
                }
            }

            if (!modifications.isEmpty())
            {
                if (buildAndExecuteModificationCommand(tableInfo,
                                                       modifications,
                                                       dbTableName,
                                                       typeDefinition,
                                                       newDataTypeHandler,
                                                       tableTree,
                                                       skipInternalTables,
                                                       varRefChk,
                                                       cmdRefChk, parent))
                {
                    commandExecuted = true;
                }
            }

            if (!additions.isEmpty())
            {
                if (buildAndExecuteAdditionCommand(tableInfo,
                                                   additions,
                                                   dbTableName,
                                                   typeDefinition,
                                                   skipInternalTables,
                                                   ignoreErrors,
                                                   parent))
                {
                    commandExecuted = true;
                }
            }

            if (updateMessageNameAndIDReference(tableInfo,
                                                typeDefinition,
                                                modifications,
                                                deletions,
                                                msgIDRefChk,
                                                parent))
            {
                commandExecuted = true;
            }

            // Check if a command was generated (e.g., the additions, modifications, and deletions
            // lists aren't empty)
            if (commandExecuted)
            {
                // Check if references in the internal tables are to be updated
                if (!skipInternalTables && isStructure)
                {
                    // Check if the table is a structure prototype and that the table had one or
                    // more variables to begin with
                    if (tableInfo.isPrototype() && tableInfo.getData().size() > 0)
                    {
                        // Build the command to delete bit-packed variable references in the links
                        // and telemetry scheduler tables that changed due to the table
                        // modifications
                        command = new StringBuilder(updateLinksAndTlmForPackingChange(orgTableNode, parent));

                        // Check if there are any bit-packed variable references to delete
                        if (command.length() != 0)
                        {
                            // Delete invalid bit-packed variable references
                            dbCommand.executeDbUpdate(command, parent);
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
                    dbCommand.executeDbQuery(new StringBuilder("SELECT reset_link_rate();"), parent);
                }

                // Release the save point. This must be done within a transaction block, so it must
                // be done prior to the commit below
                dbCommand.releaseSavePoint(parent);

                // Commit the change(s) to the database
                dbControl.getConnection().commit();

                // Check if the table type is a structure
                if (isStructure)
                {
                    // Update the list of root structure tables
                    rootStructures = getRootStructures(parent);
                }

                // Log that inserting data into the table succeeded
                eventLog.logEvent(SUCCESS_MSG,
                                  new StringBuilder("Table '"
                                                    + tableInfo.getProtoVariableName()
                                                    + "' data modified"));
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

        if (!modifications.isEmpty())
        {
            try
            {
                // Delete the index on the custom values table (if one was created)
                dbCommand.executeDbUpdate(new StringBuilder("DROP INDEX IF EXISTS "
                                                             + InternalTable.VALUES.getTableName()
                                                             + "_temp_index;"),
                                          parent);
            }
            catch (SQLException se)
            {
                eventLog.logFailEvent(parent,
                                      "Cannot delete index table for table '"
                                      + tableInfo.getProtoVariableName()
                                      + "'; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot delete index table for table '</b>"
                                      + tableInfo.getProtoVariableName()
                                      + "<b>'");
            }
        }

        // Update the field information based on what is stored in the database
        fieldHandler.buildFieldInformation(parent);

        // Check that no error occurred while applying the modifications
        if (!errorFlag)
        {
            // Check if the table's data fields were updated
            if (updateFieldInfo)
            {
                // Update the table's data field information in the field handler
                fieldHandler.replaceFieldInformationByOwner(tableInfo.getTablePath(),
                                                            tableInfo.getFieldInformation());
            }

            // Make changes to any open table editors
            CcddTableEditorDialog.doTableModificationComplete(ccddMain,
                                                              tableInfo,
                                                              modifications,
                                                              deletions,
                                                              forceUpdate,
                                                              isRefFieldChange,
                                                              isMsgIDChange);

            if (isStructure)
            {
                // Update the show variables dialog
                ccddMain.updateShowVariablesDialog();
            }
        }
        // An error occurred while applying the modifications
        else
        {
            try
            {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(ccddMain.getMainFrame());
            }
            catch (SQLException se)
            {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot revert changes to project; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot revert changes to project");
            }
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Update the data fields of any sub-tables that were altered due to the modified tables
     *
     * @param structDataType Data type of the table modification
     *
     * @param variablePath   The path of the variable that was modified
     *
     * @param tableMod       Data for the table modification
     *
     * @param ignoreErrors   true if errors should be ignored
     *
     * @param parent         GUI component over which to center any error dialog
     *
     * @return String representing the command that was built to modify the data fields
     *********************************************************************************************/
    private String UpdateSubTableDataFields(String structDataType,
                                            String variablePath,
                                            TableModification tableMod,
                                            boolean ignoreErrors,
                                            Component parent)
    {
        StringBuilder command = new StringBuilder();

        if (structDataType != null && !structDataType.isEmpty())
        {
            // Load the structure table
            TableInfo subTableInfo = loadTableData(structDataType, false, false, true, parent);

            // Check if the table failed to load (i.e., doesn't exist)
            if (subTableInfo.isErrorFlag())
            {
                if (!ignoreErrors)
                {
                    // Inform the user that the structure data type is undefined
                    eventLog.logFailEvent(parent,
                                          "Structure data type '"
                                          + structDataType
                                          + "' in table '"
                                          + TableInfo.getParentTable(variablePath)
                                          + "' is undefined",
                                          "<html><b>Structure data type '</b>"
                                          + structDataType
                                          + "<b>' in table '</b>"
                                          + TableInfo.getParentTable(variablePath)
                                          + "<b>' is undefined");
                }
            }
            else
            {
                List<Object[]> subTableData = subTableInfo.getData();

                if (!subTableInfo.getType().contentEquals(TYPE_ENUM))
                {
                    // Step through all of the subTableData
                    for (int index = 0; index < subTableData.size(); index++)
                    {
                        // Check that this variable is either a non-array member or if it is an
                        // array member that it is not the definition
                        if ((subTableData.get(index)[tableMod.getArraySizeColumn()].toString().isEmpty())
                            || (!subTableData.get(index)[tableMod.getArraySizeColumn()].toString().isEmpty()
                                && subTableData.get(index)[tableMod.getVariableColumn()].toString().endsWith("]")))
                        {
                            // Get the data type
                            String subDataType = subTableData.get(index)[tableMod.getDataTypeColumn()].toString();

                            // Check if the data type represents a structure
                            if (!dataTypeHandler.isPrimitive(subDataType))
                            {
                                // Get the variable name
                                String subVariableName = subTableData.get(index)[tableMod.getVariableColumn()].toString();

                                // Get the variable path
                                String subVariablePath = variablePath + "," + subDataType + "." + subVariableName;

                                // Check to see if this is an array definition. If so do not add
                                // its data to the internal fields table
                                if (tableMod.getRowData()[tableMod.getArraySizeColumn()].equals("")
                                    || variablePath.endsWith("]"))
                                {
                                    // Now build the command to update this table's data fields
                                    command.append("INSERT INTO ")
                                           .append(InternalTable.FIELDS.getTableName())
                                           .append(" SELECT regexp_replace(")
                                           .append(FieldsColumn.OWNER_NAME.getColumnName())
                                           .append(", E'^")
                                           .append(structDataType)
                                           .append("$', E'")
                                           .append(subVariablePath)
                                           .append("'), ")
                                           .append(FieldsColumn.FIELD_NAME.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_DESC.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_SIZE.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_REQUIRED.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                           .append(", ")
                                           .append(FieldsColumn.FIELD_INHERITED.getColumnName())
                                           .append(" FROM ")
                                           .append(InternalTable.FIELDS.getTableName())
                                           .append(" WHERE ")
                                           .append(FieldsColumn.OWNER_NAME.getColumnName())
                                           .append(" = '")
                                           .append(structDataType)
                                           .append("' AND ")
                                           .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                                           .append(" != '")
                                           .append(ApplicabilityType.ROOT_ONLY.getApplicabilityName())
                                           .append("' ORDER BY ")
                                           .append(ROW_NUM_COLUMN_NAME)
                                           .append("; ");
                                }
                            }
                        }
                    }
                }
            }
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Update the data fields of any sub-tables that were altered due to the modified tables
     *
     * @param tableInfo       Table information for the table that is being altered
     *
     * @param mod             The table modification data for the variable that is being altered
     *
     * @param dataTypeChanged Data type was changed from one primitive to another
     *
     * @param ignoreErrors    true if errors should be ignored
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return String representing the command that was built to modify the data fields
     *********************************************************************************************/
    private String BuildReferencedVariablesDataFieldsCmd(TableInfo tableInfo,
                                                         TableModification mod,
                                                         boolean dataTypeChanged,
                                                         boolean ignoreErrors,
                                                         Component parent)
    {
        StringBuilder command = new StringBuilder();

        if ((mod.getArraySizeColumn() != -1)
            && (mod.getDataTypeColumn() != -1)
            && (mod.getVariableColumn() != -1))
        {
            // If this mod represents a new variable that is being added to the table then we need
            // to assign the appropriate data fields
            boolean found = false;

            for (int index = 0; index < tableInfo.getData().size(); index++)
            {
                if (tableInfo.getData()
                             .get(index)[mod.getVariableColumn()]
                             .equals(mod.getRowData()[mod.getVariableColumn()]))
                {
                    found = true;
                    break;
                }
            }

            // Check if a match was found meaning this variable already existed in the table
            if ((found == false) || (dataTypeChanged == true))
            {
                // Get the variable path, including its name and data type
                String dataType = mod.getRowData()[mod.getDataTypeColumn()].toString();
                String variableName = mod.getRowData()[mod.getVariableColumn()].toString();
                String variablePath = tableInfo.getTablePath() + "," + dataType + "." + variableName;

                // Check to see if this is an array definition. If so do not add its data to the
                // internal fields table. If not update any tables that reference this prototype
                if (((mod.getRowData()[mod.getArraySizeColumn()].equals("")) || (variablePath.endsWith("]"))))
                {
                    // Get all variable names currently stored in the database
                    List<String> variableNames = variableHandler.getAllVariableNames();
                    List<String> newVariablePaths = new ArrayList<String>();

                    // Check if this is an addition to a prototype table
                    if (tableInfo.isPrototype())
                    {
                        // Since this was a change to a prototype table we will need to check all
                        // variable names within the database to find all locations where this
                        // table is referenced

                        // Check if any variable names reference this prototype table
                        for (int index = 0; index < variableNames.size(); index++)
                        {
                            String path = variableNames.get(index);
                            String searchString = "," + tableInfo.getTablePath() + ".";

                            if (path.contains(searchString))
                            {
                                // This variable name references the prototype table. Pull out the
                                // path of the variable that has a data type equal to the prototype
                                // table
                                int position = path.indexOf(searchString);
                                position = path.indexOf(",", position + 1);

                                if (position != -1)
                                {
                                    path = path.substring(0, position);
                                }

                                // Add the variable path to the list. Do not add duplicates
                                if (!newVariablePaths.contains(path))
                                {
                                    newVariablePaths.add(path);
                                }
                            }
                        }

                        // Remove any array definitions
                        for (int index = 0; index < newVariablePaths.size() - 1; index++)
                        {
                            String currRow = newVariablePaths.get(index);
                            String nextRow = newVariablePaths.get(index + 1).replaceAll("\\[[0-9]+]", "");
                            if (currRow.equals(nextRow))
                            {
                                newVariablePaths.remove(index);
                            }
                        }

                        // Append the data type and name of the row that was added to the prototype
                        // table
                        for (int index = 0; index < newVariablePaths.size(); index++)
                        {
                            newVariablePaths.set(index,
                                                 newVariablePaths.get(index)
                                                 + ","
                                                 + dataType
                                                 + "."
                                                 + variableName);
                        }
                    }

                    // Check if the new variable is an array definition. If so do not add any data
                    // fields. Check if the new parent is a root structure. This prevents updating
                    // the path such that it creates a reference to a child of a non-root table,
                    // which is invalid
                    if (rootStructures.contains(tableInfo.getRootTable()))
                    {
                        if ((mod.getRowData()[mod.getArraySizeColumn()].equals(""))
                            || (!mod.getRowData()[mod.getArraySizeColumn()].equals("")
                                && mod.getRowData()[mod.getVariableColumn()].toString().endsWith("]")))
                        {
                            newVariablePaths.add(variablePath);
                        }
                    }

                    // Check if the new variable's data type is a structure table
                    if (!dataTypeHandler.isPrimitive(dataType))
                    {
                        // Update the data fields of any sub-tables that were altered due to the
                        // modified tables
                        command.append(UpdateSubTableDataFields(dataType,
                                                                variablePath,
                                                                mod,
                                                                ignoreErrors,
                                                                parent));
                    }

                    for (int index = 0; index < newVariablePaths.size(); index++)
                    {
                        // Copy all data fields with 'all' or 'child only' applicability from the
                        // new child table's prototype to the child table
                        command.append("INSERT INTO ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" SELECT regexp_replace(")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(", E'^")
                               .append(dataType)
                               .append("$', E'")
                               .append(newVariablePaths.get(index))
                               .append("'), ")
                               .append(FieldsColumn.FIELD_NAME.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_DESC.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_SIZE.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_TYPE.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_REQUIRED.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_VALUE.getColumnName())
                               .append(", ")
                               .append(FieldsColumn.FIELD_INHERITED.getColumnName())
                               .append(" FROM ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" WHERE ")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(" = '")
                               .append(dataType)
                               .append("' AND ")
                               .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                               .append(" != '")
                               .append(ApplicabilityType.ROOT_ONLY.getApplicabilityName())
                               .append("' ORDER BY ")
                               .append(ROW_NUM_COLUMN_NAME)
                               .append("; ");
                    }
                }
            }
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Build and execute the commands to add table rows. Only prototype tables can have a row added
     *
     * @param tableInfo          Table information
     *
     * @param additions          List of row addition information
     *
     * @param dbTableName        Name of the table to which to add rows
     *
     * @param typeDefn           Table type definition
     *
     * @param skipInternalTables True to not build and execute the commands to update the internal
     *                           tables. This is used during a data type update where only the data
     *                           type name has changed in order to speed up the operation
     *
     * @param ignoreErrors       Should errors be ignored
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return True if no errors are encountered building and executing the addition commands
     *********************************************************************************************/
    private boolean buildAndExecuteAdditionCommand(TableInfo tableInfo,
                                                   List<TableModification> additions,
                                                   String dbTableName,
                                                   TypeDefinition typeDefn,
                                                   boolean skipInternalTables,
                                                   boolean ignoreErrors,
                                                   Component parent)
    {
        boolean result = false;

        try
        {
            // Various local variables
            ResultSet queryResult;
            int nextKeyValue = 1;
            List<String> stringArrays = new ArrayList<String>();
            StringBuilder addCmd = new StringBuilder("");
            StringBuilder valuesAddCmd = new StringBuilder("");
            StringBuilder groupsAddCmd = new StringBuilder("");
            StringBuilder fieldsAddCmd = new StringBuilder("");
            StringBuilder ordersAddCmd = new StringBuilder("");
            StringBuilder assnsAddCmd = new StringBuilder("");
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");
            List<String> removedDataTypes = new ArrayList<String>();

            // Retrieve the largest key value in the database for this particular table and then add 1
            // to it. This will be the value used for the next insertion to keep them unique
            StringBuilder queryCmd = new StringBuilder();
            queryCmd.append("SELECT _key_ FROM ")
                    .append(dbTableName)
                    .append(" WHERE _key_ = (SELECT MAX(_key_) FROM ")
                    .append(dbTableName)
                    .append(");");
            queryResult = dbCommand.executeDbQuery(queryCmd, ccddMain.getMainFrame());

            if (queryResult.next())
            {
                nextKeyValue = queryResult.getInt(1) + 1;
            }

            // Create the insert table data command. The array of column names is converted to a string
            String insertCmd = "INSERT INTO "
                               + dbTableName
                               + " ("
                               +CcddUtilities.convertArrayToString(typeDefn.getColumnNamesDatabaseQuoted())
                               + ") VALUES ";
            addCmd.append(insertCmd);

            // Step through each addition
            for (TableModification add : additions)
            {
                // Check if the length of the command string has reached the limit
                if (addCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    // Terminate the command string and send it, then start a new one
                    addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
                    addCmd.append("; ");
                    dbCommand.executeDbUpdate(addCmd, parent);
                    addCmd = new StringBuilder("");

                    // Create the insert table data command
                    addCmd.append(insertCmd);
                }

                addCmd.append("(").append(Integer.toString(nextKeyValue)).append(", ");
                nextKeyValue++;

                // For each column in the matching row
                for (int column = 0; column < add.getRowData().length; column++)
                {
                    // Check that this isn't the primary key column
                    if (column != DefaultColumn.PRIMARY_KEY.ordinal())
                    {
                        // Append the column value
                        addCmd.append(delimitText(add.getRowData()[column])).append(", ");
                    }
                }

                // Remove the ending comma and space, append the closing parenthesis, and add the
                // command to add this row
                addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
                addCmd.append("), ");

                // Check if internal tables are to be updated and the parent table is a structure
                if (!skipInternalTables && typeDefn.isStructure())
                {
                    // Get the variable name and data type for the variable in the new row
                    String variableName = add.getRowData()[add.getVariableColumn()].toString();
                    String dataType = add.getRowData()[add.getDataTypeColumn()].toString();

                    // Get the variable path
                    String newVariablePath = tableInfo.getTablePath() + "," + dataType + "." + variableName;

                    // Check if the new variable's data type is a structure table
                    if (!dataTypeHandler.isPrimitive(dataType))
                    {
                        // Check to see if this is an array definition. If so do not add its data to
                        // the internal fields table. If not update any tables that reference this
                        // prototype
                        if ((add.getRowData()[add.getArraySizeColumn()].equals("")) || (newVariablePath.endsWith("]")))
                        {
                            fieldsAddCmd.append(BuildReferencedVariablesDataFieldsCmd(tableInfo,
                                                                                      add,
                                                                                      false,
                                                                                      ignoreErrors,
                                                                                      parent));
                        }

                        // Check if this structure data type is currently a root table (i.e., it's
                        // changing from a root to a child)
                        if (rootStructures.contains(dataType))
                        {
                            // Do not update if this row represents an array definition
                            if (rootStructures.contains(tableInfo.getRootTable()))
                            {
                                if ((add.getRowData()[add.getArraySizeColumn()].equals(""))
                                    || (!add.getRowData()[add.getArraySizeColumn()].equals("")
                                        && add.getRowData()[add.getVariableColumn()].toString().endsWith("]")))
                                {
                                    // If the structure chosen as the variable's data type is a root
                                    // structure, then any custom values for this the root structure
                                    // (which becomes a child structure) are transferred to its new
                                    // parent structure. References in the other internal tables are
                                    // also changed to the structure's new path as a child
                                    valuesAddCmd.append("UPDATE ")
                                                .append(InternalTable.VALUES.getTableName())
                                                .append(" SET ")
                                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                .append(" = regexp_replace(")
                                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                .append(", E'^")
                                                .append(dataType)
                                                .append(",', E'")
                                                .append(newVariablePath)
                                                .append(",') WHERE ")
                                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                .append(" ~ E'^")
                                                .append(dataType)
                                                .append(",'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (valuesAddCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(valuesAddCmd, parent);
                                        valuesAddCmd = new StringBuilder("");
                                    }

                                    groupsAddCmd.append("UPDATE ")
                                                .append(InternalTable.GROUPS.getTableName())
                                                .append(" SET ")
                                                .append(GroupsColumn.MEMBERS.getColumnName())
                                                .append(" = regexp_replace(")
                                                .append(GroupsColumn.MEMBERS.getColumnName())
                                                .append(", E'^")
                                                .append(dataType)
                                                .append("(,|$)', E'")
                                                .append(newVariablePath)
                                                .append("\\\\1') WHERE ")
                                                .append(GroupsColumn.MEMBERS.getColumnName())
                                                .append(" ~ E'^")
                                                .append(dataType)
                                                .append("(,|$)'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (groupsAddCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(groupsAddCmd, parent);
                                        groupsAddCmd = new StringBuilder("");
                                    }

                                    fieldsAddCmd.append("UPDATE ")
                                                .append(InternalTable.FIELDS.getTableName())
                                                .append(" SET ")
                                                .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                .append(" = regexp_replace(")
                                                .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                .append(", E'^")
                                                .append(dataType)
                                                .append(",', E'")
                                                .append(newVariablePath)
                                                .append(",') WHERE ")
                                                .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                .append(" ~ E'^")
                                                .append(dataType)
                                                .append(",'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (fieldsAddCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(fieldsAddCmd, parent);
                                        fieldsAddCmd = new StringBuilder("");
                                    }

                                    ordersAddCmd.append("UPDATE ")
                                                .append(InternalTable.ORDERS.getTableName())
                                                .append(" SET ")
                                                .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                .append(" = regexp_replace(")
                                                .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                .append(", E'^")
                                                .append(dataType)
                                                .append(",', E'")
                                                .append(newVariablePath)
                                                .append(",'); INSERT INTO ")
                                                .append(InternalTable.ORDERS.getTableName())
                                                .append(" SELECT ")
                                                .append(OrdersColumn.USER_NAME.getColumnName())
                                                .append(", regexp_replace(")
                                                .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                .append(", E'^")
                                                .append(dataType)
                                                .append("', E'")
                                                .append(newVariablePath)
                                                .append("'), ")
                                                .append(OrdersColumn.COLUMN_ORDER.getColumnName())
                                                .append(" FROM ")
                                                .append(InternalTable.ORDERS.getTableName())
                                                .append(" WHERE ")
                                                .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                .append(" = '")
                                                .append(dataType)
                                                .append("'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (ordersAddCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(ordersAddCmd, parent);
                                        ordersAddCmd = new StringBuilder("");
                                    }

                                    String orgPathWithChildren = dataType + "(," + PATH_IDENT + ")?";
                                    assnsAddCmd.append("UPDATE ")
                                               .append(InternalTable.ASSOCIATIONS.getTableName())
                                               .append(" SET ")
                                               .append(AssociationsColumn.MEMBERS.getColumnName())
                                               .append(" = regexp_replace(")
                                               .append(AssociationsColumn.MEMBERS.getColumnName())
                                               .append(", E'(?:^")
                                               .append(orgPathWithChildren)
                                               .append("|(")
                                               .append(assnsSeparator)
                                               .append(")")
                                               .append(orgPathWithChildren)
                                               .append(")', E'\\\\2")
                                               .append(newVariablePath)
                                               .append("\\\\1\\\\3', 'g'); ");

                                    // Check if the length of the command string has reached the limit
                                    if (assnsAddCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(assnsAddCmd, parent);
                                        assnsAddCmd = new StringBuilder("");
                                    }
                                }
                            }

                            // Check if any data types were added to the list
                            if (!removedDataTypes.contains(dataType))
                            {
                                // References in the links and telemetry scheduler to the root
                                // structure and its children are not automatically amended to
                                // include the new parent structure path, but are instead removed
                                deleteLinkPathRef("^" + dataType + "(?:,|\\\\.|$)", linksDelCmd);
                                deleteTlmPathRef(dataType + "(?:,|\\\\.|$)", tlmDelCmd);
                                removedDataTypes.add(dataType);
                            }
                        }
                    }

                    // Check if the added variable is a string array member
                    if (dataTypeHandler.isString(dataType) && ArrayVariable.isArrayMember(variableName))
                    {
                        // Get the string array definition
                        String stringArrayDefn = ArrayVariable.removeStringSize(newVariablePath);

                        // Check if this string array hasn't already been processed
                        if (!stringArrays.contains(stringArrayDefn))
                        {
                            // Add the string array definition to the list so that it's only processed
                            // once (in the event multiple members of the array are added)
                            stringArrays.add(stringArrayDefn);

                            // Update the links definitions, if needed, with the new string array
                            // member
                            updateLinksForStringMemberAddition(tableInfo.getTablePath(), dataType, variableName);

                            // Remove all references to the string array from the telemetry scheduler
                            // table
                            deleteTlmPathRef(stringArrayDefn + "(?:,|:|$)", tlmDelCmd);
                        }
                    }
                }
            }

            // Send the remaining commands
            if (addCmd.toString().endsWith("), "))
            {
                // Remove the ending comma and space, and append the command's closing semicolon
                addCmd = CcddUtilities.removeTrailer(addCmd, ", ");
                addCmd.append("; ");
                dbCommand.executeDbUpdate(addCmd, parent);
            }

            if (valuesAddCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(valuesAddCmd, parent);
            }

            if (groupsAddCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(groupsAddCmd, parent);
            }

            if (fieldsAddCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(fieldsAddCmd, parent);
            }

            if (ordersAddCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(ordersAddCmd, parent);
            }

            if (assnsAddCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(assnsAddCmd, parent);
            }

            if (linksDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(linksDelCmd, parent);
            }

            if (tlmDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(tlmDelCmd, parent);
            }

            result = true;
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "SQL command failed cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>SQL command failed</b>");
        }

        return result;
    }

    /**********************************************************************************************
     * Build and execute the commands to modify the table
     *
     * @param tableInfo          Table information
     *
     * @param modifications      List of row modification information
     *
     * @param dbTableName        Name of the table's prototype to which to modify rows
     *
     * @param typeDefn           Table type definition
     *
     * @param newDataTypeHandler Data type handler with data type modifications. null (or a
     *                           reference to the current data type handler) if the change does not
     *                           originate from the data type editor
     *
     * @param tableTree          CcddTableTreeHandler reference describing the table tree
     *
     * @param skipInternalTables True to not build and execute the commands to update the internal
     *                           tables. This is used during a data type update where only the data
     *                           type name has changed in order to speed up the operation
     *
     * @param varRefChkResults   Results of the variable reference input type search
     *
     * @param cmdRefChkResults   Results of the command reference input type search
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return True if no errors are encountered building and executing the modification commands
     *********************************************************************************************/
    private boolean buildAndExecuteModificationCommand(TableInfo tableInfo,
                                                       List<TableModification> modifications,
                                                       String dbTableName,
                                                       TypeDefinition typeDefn,
                                                       CcddDataTypeHandler newDataTypeHandler,
                                                       CcddTableTreeHandler tableTree,
                                                       boolean skipInternalTables,
                                                       ReferenceCheckResults varRefChkResults,
                                                       ReferenceCheckResults cmdRefChkResults,
                                                       Component parent)
    {
        boolean result = false;

        try
        {
            // Various string builders for commands
            StringBuilder modCmd = new StringBuilder("");
            StringBuilder valuesModCmd = new StringBuilder("");
            StringBuilder linksModCmd = new StringBuilder("");
            StringBuilder tlmModCmd = new StringBuilder("");
            StringBuilder groupsModCmd = new StringBuilder("");
            StringBuilder fieldsModCmd = new StringBuilder("");
            StringBuilder ordersModCmd = new StringBuilder("");
            StringBuilder assnsModCmd = new StringBuilder("");
            StringBuilder linksDelCmd = new StringBuilder("");
            StringBuilder tlmDelCmd = new StringBuilder("");
            List<Object[]> tablePathList = null;
            List<String> changedDataTypes = new ArrayList<String>();
            List<StringBuilder> valDelCmd = new ArrayList<StringBuilder>();
            List<StringBuilder> valInsCmd = new ArrayList<StringBuilder>();

            valDelCmd.add(new StringBuilder(""));
            String insertCmd = "INSERT INTO "
                               + InternalTable.VALUES.getTableName()
                               + " ("
                               + ValuesColumn.TABLE_PATH.getColumnName()
                               + ", "
                               + ValuesColumn.COLUMN_NAME.getColumnName()
                               + ", "
                               + ValuesColumn.VALUE.getColumnName()
                               + ") VALUES ";
            valInsCmd.add(new StringBuilder(insertCmd));

            // Place double back slashes before each square brace character in an array index so
            // that the brackets are interpreted correctly in the query's regular expression
            // comparisons
            String tablePath = CcddUtilities.escapePostgreSQLReservedChars(tableInfo.getTablePath());

            // Count the number of rows in the custom values table for the table being modified
            // (skipping the table description row, if present). If no entries are present then the
            // commands to delete existing entries can be skipped
            ResultSet countResult = dbCommand.executeDbQuery(new StringBuilder("SELECT "
                                                                               + ValuesColumn.TABLE_PATH.getColumnName()
                                                                               + " FROM "
                                                                               + InternalTable.VALUES.getTableName()
                                                                               + " WHERE "
                                                                               + ValuesColumn.TABLE_PATH.getColumnName()
                                                                               + " ~ E'^"
                                                                               + tablePath
                                                                               +"' AND "
                                                                               + ValuesColumn.COLUMN_NAME.getColumnName()
                                                                               +" != '';"),
                                                             parent);
            boolean needDelete = countResult.next();
            countResult.close();

            // If one or more entries is found for the table in the custom values table then create
            // an index of the table's entries (table path and affected columns). This is used to
            // speed up locating the entries
            if (needDelete)
            {
                dbCommand.executeDbUpdate(new StringBuilder("DROP INDEX IF EXISTS "
                                                            + InternalTable.VALUES.getTableName()
                                                            + "_temp_index; CREATE INDEX "
                                                            + InternalTable.VALUES.getTableName()
                                                            + "_temp_index ON "
                                                            + InternalTable.VALUES.getTableName()
                                                            + " ("
                                                            + ValuesColumn.TABLE_PATH.getColumnName()
                                                            + ", "
                                                            + ValuesColumn.COLUMN_NAME.getColumnName()
                                                            + ") WHERE "
                                                            + ValuesColumn.TABLE_PATH.getColumnName()
                                                            + " ~ '"
                                                            + tableInfo.getTablePath()
                                                            +"';"),
                                          parent);
            }

            // Retrieve the internal associations table data
            List<String[]> assnsData = retrieveInformationTable(InternalTable.ASSOCIATIONS, false, parent);

            // Create an array of all of the member tables
            String[] assnsMemberTables = new String[assnsData.size()];

            for (int index = 0; index < assnsMemberTables.length; index++)
            {
                assnsMemberTables[index] = assnsData.get(index)[AssociationsColumn.MEMBERS.ordinal()];
            }

            // Check if no updated data type handler is provided. This implies the modifications are
            // not due to an update in the data type editor
            if (newDataTypeHandler == null)
            {
                // Set the updated data type handler to use the current handler
                newDataTypeHandler = dataTypeHandler;
            }

            // Step through each modification
            for (TableModification mod : modifications)
            {
                boolean wasShifted = false;

                // Check if this is a prototype table (modifications are made to the table)
                if (tableInfo.isPrototype())
                {
                    // Build the update command
                    modCmd.append("UPDATE ").append(dbTableName).append(" SET ");

                    // Step through each changed column
                    for (int column = 0; column < mod.getRowData().length; column++)
                    {
                        // Check if the column value changed
                        if (mod.getOriginalRowData()[column] == null
                            || !mod.getOriginalRowData()[column].equals(mod.getRowData()[column]))
                        {
                            // Build the command to change the column value
                            modCmd.append(typeDefn.getColumnNamesDatabaseQuoted()[column])
                                  .append(" = ")
                                  .append(delimitText(mod.getRowData()[column]))
                                  .append(", ");
                        }
                    }

                    // Check if the internal tables are to be updated and the table represents a
                    // structure
                    if (!skipInternalTables && typeDefn.isStructure())
                    {
                        // Get the original and current variable names, data types, array sizes, and
                        // bit lengths
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
                                // Set the flag to indicate that the rate changed and stop searching
                                rateChanged = true;
                                break;
                            }
                        }

                        // Set the flags to indicate if the variable name, data type, array size, and
                        // bit length values have changed
                        boolean variableChanged = !oldVariableName.equals(newVariableName);
                        boolean dataTypeChanged = !oldDataType.equals(newDataType);
                        boolean arraySizeChanged = !oldArraySize.equals(newArraySize);
                        boolean bitLengthChanged = !oldBitLength.equals(newBitLength);

                        // If the data type changed then add it to the list
                        if (dataTypeChanged)
                        {
                            changedDataTypes.add(oldDataType);
                        }

                        // Check if the variable name, data type, array size, bit length, or rate
                        // column value(s) changed; this change must be propagated to the instances of
                        // this prototype and their entries in the internal tables
                        if (variableChanged || dataTypeChanged || arraySizeChanged || bitLengthChanged || rateChanged)
                        {
                            // Get the variable path
                            String newVariablePath = tableInfo.getTablePath() + "," + newDataType + "." + newVariableName;

                            // Check if the data type has been changed, the new data type is a
                            // structure, and this structure is a root table
                            if (dataTypeChanged
                                && !newDataTypeHandler.isPrimitive(newDataType)
                                && rootStructures.contains(newDataType))
                            {
                                // If the structure chosen as the variable's data type is a root
                                // structure, then any references in the internal tables are changed to
                                // the structure's new path as a child
                                valuesModCmd.append("UPDATE ")
                                            .append(InternalTable.VALUES.getTableName())
                                            .append(" SET ")
                                            .append(ValuesColumn.TABLE_PATH.getColumnName())
                                            .append(" = regexp_replace(")
                                            .append(ValuesColumn.TABLE_PATH.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append(",', E'")
                                            .append(newVariablePath)
                                            .append(",') WHERE ")
                                            .append(ValuesColumn.TABLE_PATH.getColumnName())
                                            .append(" ~ E'^")
                                            .append(newDataType)
                                            .append(",'; ");

                                // Check if the length of the command string has reached the limit
                                if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(valuesModCmd, parent);
                                    valuesModCmd = new StringBuilder("");
                                }

                                groupsModCmd.append("UPDATE ")
                                            .append(InternalTable.GROUPS.getTableName())
                                            .append(" SET ")
                                            .append(GroupsColumn.MEMBERS.getColumnName())
                                            .append(" = regexp_replace(")
                                            .append(GroupsColumn.MEMBERS.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append("(,|$)', E'")
                                            .append(newVariablePath)
                                            .append("\\\\1') WHERE ")
                                            .append(GroupsColumn.MEMBERS.getColumnName())
                                            .append(" ~ E'^")
                                            .append(newDataType)
                                            .append("(,|$)'; ");

                                // Check if the length of the command string has reached the limit
                                if (groupsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(groupsModCmd, parent);
                                    groupsModCmd = new StringBuilder("");
                                }

                                fieldsModCmd.append("UPDATE ")
                                            .append(InternalTable.FIELDS.getTableName())
                                            .append(" SET ")
                                            .append(FieldsColumn.OWNER_NAME.getColumnName())
                                            .append(" = regexp_replace(")
                                            .append(FieldsColumn.OWNER_NAME.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append(",', E'")
                                            .append(newVariablePath)
                                            .append(",'); INSERT INTO ")
                                            .append(InternalTable.FIELDS.getTableName())
                                            .append(" SELECT regexp_replace(")
                                            .append(FieldsColumn.OWNER_NAME.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append("', E'")
                                            .append(newVariablePath)
                                            .append("'), ")
                                            .append(FieldsColumn.FIELD_NAME.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_DESC.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_SIZE.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_REQUIRED.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                            .append(", ")
                                            .append(FieldsColumn.FIELD_INHERITED)
                                            .append(" FROM ")
                                            .append(InternalTable.FIELDS.getTableName())
                                            .append(" WHERE ")
                                            .append(FieldsColumn.OWNER_NAME.getColumnName())
                                            .append(" = '")
                                            .append(newDataType)
                                            .append("' AND ")
                                            .append(FieldsColumn.FIELD_APPLICABILITY.getColumnName())
                                            .append(" != '")
                                            .append(ApplicabilityType.ROOT_ONLY.getApplicabilityName())
                                            .append("'; ");

                                // Check if the length of the command string has reached the limit
                                if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                    fieldsModCmd = new StringBuilder("");
                                }

                                ordersModCmd.append("UPDATE ")
                                            .append(InternalTable.ORDERS.getTableName())
                                            .append(" SET ")
                                            .append(OrdersColumn.TABLE_PATH.getColumnName())
                                            .append(" = regexp_replace(")
                                            .append(OrdersColumn.TABLE_PATH.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append(",', E'")
                                            .append(newVariablePath)
                                            .append(",'); INSERT INTO ")
                                            .append(InternalTable.ORDERS.getTableName())
                                            .append(" SELECT ")
                                            .append(OrdersColumn.USER_NAME.getColumnName())
                                            .append(", regexp_replace(")
                                            .append(OrdersColumn.TABLE_PATH.getColumnName())
                                            .append(", E'^")
                                            .append(newDataType)
                                            .append("', E'")
                                            .append(newVariablePath)
                                            .append("'), ")
                                            .append(OrdersColumn.COLUMN_ORDER.getColumnName())
                                            .append(" FROM ")
                                            .append(InternalTable.ORDERS.getTableName())
                                            .append(" WHERE ")
                                            .append(OrdersColumn.TABLE_PATH.getColumnName())
                                            .append(" = '")
                                            .append(newDataType)
                                            .append("'; ");

                                // Check if the length of the command string has reached the limit
                                if (ordersModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(ordersModCmd, parent);
                                    ordersModCmd = new StringBuilder("");
                                }

                                String orgPathWithChildren = newDataType + "(," + PATH_IDENT + ")?";
                                assnsModCmd.append("UPDATE ")
                                           .append(InternalTable.ASSOCIATIONS.getTableName())
                                           .append(" SET ")
                                           .append(AssociationsColumn.MEMBERS.getColumnName())
                                           .append(" = regexp_replace(")
                                           .append(AssociationsColumn.MEMBERS.getColumnName())
                                           .append(", E'(?:^")
                                           .append(orgPathWithChildren)
                                           .append("|(")
                                           .append(assnsSeparator)
                                           .append(")")
                                           .append(orgPathWithChildren)
                                           .append(")', E'\\\\2")
                                           .append(newVariablePath)
                                           .append("\\\\1\\\\3', 'ng'); ");

                                // References in the links and telemetry scheduler to the root
                                // structure and its children are not automatically amended to include
                                // the new parent structure path, but are instead removed
                                deleteLinkPathRef("^" + newDataType + "(?:,|\\\\.|$)", linksDelCmd);
                                deleteTlmPathRef(newDataType + "(?:,|\\\\.|$)", tlmDelCmd);

                                // Check if the length of the command string has reached the limit
                                if (linksDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(linksDelCmd, parent);
                                    linksDelCmd = new StringBuilder("");
                                }

                                // Check if the length of the command string has reached the limit
                                if (tlmDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(tlmDelCmd, parent);
                                    tlmDelCmd = new StringBuilder("");
                                }
                            }

                            // Check if the table path list doesn't exist. The list only needs to be
                            // created once per table since it references the prototype, which doesn't
                            // change even if multiple modifications are made to the table
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

                            // Before making all the changes below make sure that we are not
                            // dealing with a shift due to a new row of data being added
                            boolean variableStillExists = false;

                            for (int i = 0; i < modifications.size(); i++)
                            {
                                Object[] rowData = modifications.get(i).getRowData();
                                String variableName = (String) rowData[(int) modifications.get(i).getVariableColumn()];
                                if (variableName.contentEquals(oldVariableName))
                                {
                                    variableStillExists = true;

                                    // We now know that the variable name is the same so check to
                                    // see if the data type and array size is the same
                                    String dataType = (String) rowData[(int) modifications.get(i).getDataTypeColumn()];
                                    String arraySize = (String) rowData[(int) modifications.get(i).getArraySizeColumn()];

                                    if (dataType.contentEquals(oldDataType) && arraySize.contentEquals(oldArraySize))
                                    {
                                        wasShifted = true;
                                        break;
                                    }
                                }
                            }

                            if (!wasShifted)
                            {
                                for (Object[] path : tablePathList)
                                {
                                    boolean isDelLinksAndTlm = false;

                                    // Get the variable name path from the table tree
                                    tablePath = tableTree.getFullVariablePath(path);

                                    // Append the original/new data type and/or name of the
                                    // variable that's being changed to the variable path. Escape
                                    // any PostgreSQL reserved characters so that the original path
                                    // can be used in a regular expression
                                    String orgVariablePath = tablePath + "," + oldDataType + "." + oldVariableName;
                                    String orgVarPathEsc = CcddUtilities.escapePostgreSQLReservedChars(orgVariablePath);
                                    newVariablePath = tablePath + "," + newDataType + "." + newVariableName;

                                    // Check if the variable name changed, or if the data type
                                    // changed from one primitive to another primitive. In either
                                    // case, check that the array status (is or isn't) remains
                                    // unchanged
                                    if ((variableChanged ||
                                         (dataTypeChanged
                                          && dataTypeHandler.isPrimitive(oldDataType)
                                          && newDataTypeHandler.isPrimitive(newDataType)))
                                        && !(arraySizeChanged
                                             && (oldArraySize.isEmpty() || newArraySize.isEmpty())))
                                    {
                                        // Create the commands to update the internal tables for
                                        // instances of non-array member variables of the prototype
                                        // table
                                        if (!orgVarPathEsc.contains("[") || orgVarPathEsc.contains("[0"))
                                        {
                                            valuesModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                         newVariablePath,
                                                                                         InternalTable.VALUES.getTableName(),
                                                                                         ValuesColumn.TABLE_PATH.getColumnName(),
                                                                                         "",
                                                                                         "",
                                                                                         true));

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(valuesModCmd, parent);
                                                valuesModCmd = new StringBuilder("");
                                            }

                                            groupsModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                         newVariablePath,
                                                                                         InternalTable.GROUPS.getTableName(),
                                                                                         GroupsColumn.MEMBERS.getColumnName(),
                                                                                         "",
                                                                                         "",
                                                                                         true));

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (groupsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(groupsModCmd, parent);
                                                groupsModCmd = new StringBuilder("");
                                            }

                                            ordersModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                         newVariablePath,
                                                                                         InternalTable.ORDERS.getTableName(),
                                                                                         OrdersColumn.TABLE_PATH.getColumnName(),
                                                                                         "",
                                                                                         "",
                                                                                         true));

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (ordersModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(ordersModCmd, parent);
                                                ordersModCmd = new StringBuilder("");
                                            }
                                       }

                                        String orgPathWithChildren = orgVarPathEsc + "(," + PATH_IDENT + ")?";

                                        // The associations table should only be updated if it
                                        // contains a reference to the table path that is being
                                        // modified
                                        for (int index = 0; index < assnsMemberTables.length; index++)
                                        {
                                            if (assnsMemberTables[index].contains((String) path[path.length - 1]))
                                            {
                                                assnsModCmd.append("UPDATE ")
                                                           .append(InternalTable.ASSOCIATIONS.getTableName())
                                                           .append(" SET ")
                                                           .append(AssociationsColumn.MEMBERS.getColumnName())
                                                           .append(" = regexp_replace(")
                                                           .append(AssociationsColumn.MEMBERS.getColumnName())
                                                           .append(", E'(?:^")
                                                           .append(orgPathWithChildren)
                                                           .append("|(")
                                                           .append(assnsSeparator)
                                                           .append(")")
                                                           .append(orgPathWithChildren)
                                                           .append(")', E'\\\\2")
                                                           .append(newVariablePath)
                                                           .append("\\\\1\\\\3', 'ng'); ");

                                                // Check if the length of the command string has
                                                // reached the limit
                                                if (assnsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                                {
                                                    dbCommand.executeDbUpdate(assnsModCmd, parent);
                                                    assnsModCmd = new StringBuilder("");
                                                }

                                                break;
                                            }
                                        }

                                        // Check if the data type, bit length, and rate didn't also
                                        // change (updates to these attributes result in deletion
                                        // of any references to the variable and any children in
                                        // the links and telemetry scheduler tables, which is
                                        // handled elsewhere)
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

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (linksModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(linksModCmd, parent);
                                                linksModCmd = new StringBuilder("");
                                            }

                                            // Since the variable still fits within any message in
                                            // the telemetry scheduler table to which it's assigned
                                            // just change all references to the variable
                                            tlmModCmd.append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                      newVariablePath,
                                                                                      InternalTable.TLM_SCHEDULER.getTableName(),
                                                                                      TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                                      "(.*" + tlmSchSeparator + ")",
                                                                                      "\\\\1",
                                                                                      true));

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (tlmModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(tlmModCmd, parent);
                                                tlmModCmd = new StringBuilder("");
                                            }
                                        }
                                        // The data type, bit length, or rate changed
                                        else
                                        {
                                            // Set the flag to indicate that references to the
                                            // variable and any children should be removed from the
                                            // links and telemetry scheduler tables
                                            isDelLinksAndTlm = true;
                                        }
                                    }

                                    // Check if the bit length changed, but not the data type or
                                    // rate
                                    if (bitLengthChanged && !dataTypeChanged && !rateChanged)
                                    {
                                        // Append the bit lengths to the variable paths
                                        String orgVarPathEscBit = orgVarPathEsc
                                                                  + (oldBitLength.isEmpty() ? ""
                                                                                            : ":" + oldBitLength);
                                        String newVariablePathBit = newVariablePath
                                                                    + (newBitLength.isEmpty() ? ""
                                                                                              : ":" + newBitLength);

                                        // Create the command to update the links and telemetry
                                        // scheduler tables for instances of variables of the
                                        // prototype table. If bit-packing changed due to the bit
                                        // length update then the affected variables are
                                        // subsequently removed from the links and telemetry
                                        // scheduler tables
                                        linksModCmd.append(updateVarNameAndDataType(orgVarPathEscBit,
                                                                                    newVariablePathBit,
                                                                                    InternalTable.LINKS.getTableName(),
                                                                                    LinksColumn.MEMBER.getColumnName(),
                                                                                    "",
                                                                                    "",
                                                                                    true));

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (linksModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(linksModCmd, parent);
                                            linksModCmd = new StringBuilder("");
                                        }

                                        tlmModCmd.append(updateVarNameAndDataType(orgVarPathEscBit,
                                                                                  newVariablePathBit,
                                                                                  InternalTable.TLM_SCHEDULER.getTableName(),
                                                                                  TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                                  "(.*" + tlmSchSeparator + ")",
                                                                                  "\\\\1",
                                                                                  true));

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (tlmModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(tlmModCmd, parent);
                                            tlmModCmd = new StringBuilder("");
                                        }
                                    }

                                    // Check if the data type changed from a structure to either a
                                    // primitive or another structure
                                    if (dataTypeChanged && variableStillExists
                                        && !dataTypeHandler.isPrimitive(oldDataType))
                                    {
                                        // Create the command to delete references to any children
                                        // of the original structure path and change the data type
                                        // for references to the structure itself
                                        valuesModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.VALUES.getTableName())
                                                    .append(" WHERE ")
                                                    .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(orgVarPathEsc)
                                                    .append(",'; ")
                                                    .append(updateVarNameAndDataType(orgVarPathEsc,
                                                                                     newVariablePath,
                                                                                     InternalTable.VALUES.getTableName(),
                                                                                     ValuesColumn.TABLE_PATH.getColumnName(),
                                                                                     "",
                                                                                     "",
                                                                                     false));

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(valuesModCmd, parent);
                                            valuesModCmd = new StringBuilder("");
                                        }

                                        // Build a regular expression for locating references to
                                        // the original variable path
                                        String pathMatch = orgVarPathEsc + "(?:,|$)";

                                        // Remove all references to the structure and its children
                                        groupsModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.GROUPS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(GroupsColumn.MEMBERS.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (groupsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(groupsModCmd, parent);
                                            groupsModCmd = new StringBuilder("");
                                        }

                                        fieldsModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.FIELDS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                            fieldsModCmd = new StringBuilder("");
                                        }

                                        // Check to see if this is an array definition. If so do
                                        // not add its data to the internal fields table. If not
                                        // update any tables that reference this prototype
                                        if (((mod.getRowData()[mod.getArraySizeColumn()].equals(""))
                                             || (newVariablePath.endsWith("]")))
                                            && !rootStructures.contains(newDataType))
                                        {
                                            fieldsModCmd.append(BuildReferencedVariablesDataFieldsCmd(tableInfo,
                                                                                                      mod,
                                                                                                      true,
                                                                                                      false,
                                                                                                      parent));

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                                fieldsModCmd = new StringBuilder("");
                                            }
                                        }

                                        ordersModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.ORDERS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (ordersModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(ordersModCmd, parent);
                                            ordersModCmd = new StringBuilder("");
                                        }

                                        String orgPathWithChildren = orgVarPathEsc + "(?:," + PATH_IDENT + ")?";
                                        assnsModCmd.append("UPDATE ")
                                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                                   .append(" SET ")
                                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                                   .append(" = regexp_replace(")
                                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                                   .append(", E'^" + orgPathWithChildren)
                                                   .append("', E'', 'ng'); UPDATE ")
                                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                                   .append(" SET ")
                                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                                   .append(" = regexp_replace(")
                                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                                   .append(", E'")
                                                   .append(assnsSeparator)
                                                   .append(orgPathWithChildren + "', E'', 'ng'); ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (assnsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(assnsModCmd, parent);
                                            assnsModCmd = new StringBuilder("");
                                        }

                                        // Check if the rate didn't change as well (if the rate
                                        // changed then updates to the links and telemetry
                                        // scheduler tables are handled elsewhere)
                                        if (!rateChanged)
                                        {
                                            // Set the flag to indicate that references to the
                                            // variable and any children should be removed from the
                                            // links and telemetry scheduler tables
                                            isDelLinksAndTlm = true;
                                        }
                                    }

                                    // Check if the variable changed to or from being an array
                                    // (changes only to the array dimension value(s) are handled by
                                    // the table row addition and deletion methods)
                                    if (arraySizeChanged && !variableStillExists
                                        && (oldArraySize.isEmpty() || newArraySize.isEmpty()))
                                    {
                                        // Remove all references to the structure's children, but
                                        // not the structure itself
                                        valuesModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.VALUES.getTableName())
                                                    .append(" WHERE ")
                                                    .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(orgVarPathEsc)
                                                    .append("(?:,|\\\\[)")
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(valuesModCmd, parent);
                                            valuesModCmd = new StringBuilder("");
                                        }

                                        // Build a regular expression for locating references to
                                        // the original variable path and any children
                                        String pathMatch = orgVarPathEsc + "(?:,|\\\\[|$)";

                                        // Remove all references to the structure and its children
                                        // in the internal tables (other than the custom values
                                        // table)
                                        groupsModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.GROUPS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(GroupsColumn.MEMBERS.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (groupsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(groupsModCmd, parent);
                                            groupsModCmd = new StringBuilder("");
                                        }

                                        fieldsModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.FIELDS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                            fieldsModCmd = new StringBuilder("");
                                        }

                                        ordersModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.ORDERS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(OrdersColumn.TABLE_PATH.getColumnName())
                                                    .append(" ~ E'^")
                                                    .append(pathMatch)
                                                    .append("'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (ordersModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(ordersModCmd, parent);
                                            ordersModCmd = new StringBuilder("");
                                        }

                                        // Check if the variable changed from not an array to an
                                        // array
                                        if (!newArraySize.isEmpty())
                                        {
                                            // Remove all non-array references to the structure and
                                            // its children in the script associations. If the
                                            // variable changed from an array to not an array then
                                            // updates to the associations are handled by the row
                                            // deletion method
                                            assnsModCmd.append("UPDATE ")
                                                       .append(InternalTable.ASSOCIATIONS.getTableName())
                                                       .append(" SET ")
                                                       .append(AssociationsColumn.MEMBERS.getColumnName())
                                                       .append(" = regexp_replace(")
                                                       .append(AssociationsColumn.MEMBERS.getColumnName())
                                                       .append(", E'^")
                                                       .append(orgVarPathEsc)
                                                       .append("(?:")
                                                       .append(assnsSeparator)
                                                       .append("|,")
                                                       .append(PATH_IDENT)
                                                       .append("|$)', E'', 'ng'); ");

                                            // Check if the length of the command string has
                                            // reached the limit
                                            if (assnsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                            {
                                                dbCommand.executeDbUpdate(assnsModCmd, parent);
                                                assnsModCmd = new StringBuilder("");
                                            }
                                        }

                                        // Check if the rate didn't change as well (if the rate
                                        // changed then updates to the links and telemetry
                                        // scheduler tables are handled elsewhere)
                                        if (!rateChanged)
                                        {
                                            // Set the flag to indicate that references to the
                                            // variable and any children should be removed from the
                                            // links and telemetry scheduler tables
                                            isDelLinksAndTlm = true;
                                        }
                                    }

                                    // Check if the rate changed or another change necessitates
                                    // removal of the variable and any children from the links and
                                    // telemetry scheduler tables
                                    if (rateChanged || isDelLinksAndTlm)
                                    {
                                        // Create the commands to delete the variable from the link
                                        // and telemetry scheduler tables
                                        deleteLinkAndTlmPathRef(oldArraySize,
                                                                oldVariableName,
                                                                orgVariablePath,
                                                                orgVarPathEsc,
                                                                linksDelCmd,
                                                                tlmDelCmd);

                                        // Check if the length of the command string has
                                        // reached the limit
                                        if (linksDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(linksDelCmd, parent);
                                            linksDelCmd = new StringBuilder("");
                                        }

                                        // Check if the length of the command string has
                                        // reached the limit
                                        if (tlmDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(tlmDelCmd, parent);
                                            tlmDelCmd = new StringBuilder("");
                                        }
                                    }

                                    // If the variable was completely removed from the table then
                                    // we need to clean up the internal fields table
                                    if (variableStillExists == false)
                                    {
                                        fieldsModCmd.append("DELETE FROM ")
                                                    .append(InternalTable.FIELDS.getTableName())
                                                    .append(" WHERE ")
                                                    .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                    .append(" LIKE '%")
                                                    .append(orgVariablePath)
                                                    .append("%'; ");

                                        // Check if the length of the command string has reached
                                        // the limit
                                        if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                        {
                                            dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                            fieldsModCmd = new StringBuilder("");
                                        }
                                    }
                                }
                            }

                            // Check if the variable's name or array size has changed (this is for
                            // adjusting cells and fields having a variable reference input type; data
                            // type doesn't affect the variable reference)
                            if (variableChanged || arraySizeChanged)
                            {
                                // Update any table cells or data fields with the variable reference
                                // input type containing the renamed variable
                                modifyVariableReference(tableInfo,
                                                        oldVariableName,
                                                        newVariableName,
                                                        oldDataType,
                                                        newDataType,
                                                        varRefChkResults,
                                                        valuesModCmd,
                                                        fieldsModCmd);

                                // Check if the length of the command string has reached the limit
                                if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(valuesModCmd, parent);
                                    valuesModCmd = new StringBuilder("");
                                }

                                // Check if the length of the command string has reached the limit
                                if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                    fieldsModCmd = new StringBuilder("");
                                }
                            }
                        }
                    }
                    // Check if this is a command table
                    else if (typeDefn.isCommand())
                    {
                        // Get the column indices for the command name and code
                        int cmdNameCol = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
                        int cmdCodeCol = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);
                        int cmdArgCol = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_ARGUMENT);

                        // Get the original and modified command name, code, and argument path
                        String oldCommandName = mod.getOriginalRowData()[cmdNameCol].toString();
                        String newCommandName = mod.getRowData()[cmdNameCol].toString();
                        String oldCommandCode = mod.getOriginalRowData()[cmdCodeCol].toString();
                        String newCommandCode = mod.getRowData()[cmdCodeCol].toString();
                        String oldCommandArg = mod.getOriginalRowData()[cmdArgCol].toString();
                        String newCommandArg = mod.getRowData()[cmdArgCol].toString();

                        // Check if a command name, code, or argument structure changed
                        if (!oldCommandName.equals(newCommandName)
                            || !oldCommandCode.equals(newCommandCode)
                            || !oldCommandArg.equals(newCommandArg))
                        {
                            // Update any table cells or data fields with the command reference input
                            // type containing the command with a name, code, or names of arguments
                            // change
                            modifyCommandReference(tableInfo.getTablePath(),
                                                   oldCommandName,
                                                   newCommandName,
                                                   oldCommandCode,
                                                   newCommandCode,
                                                   oldCommandArg,
                                                   newCommandArg,
                                                   cmdRefChkResults,
                                                   valuesModCmd,
                                                   fieldsModCmd);

                            // Check if the length of the command string has reached the limit
                            if (valuesModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(valuesModCmd, parent);
                                valuesModCmd = new StringBuilder("");
                            }

                            // Check if the length of the command string has reached the limit
                            if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(fieldsModCmd, parent);
                                fieldsModCmd = new StringBuilder("");
                            }
                        }
                    }

                    // Check is a command was created to change the associations table
                    if (assnsModCmd.length() != 0)
                    {
                        // Clean up any associations that no longer have a table referenced
                        assnsModCmd.append("UPDATE ")
                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                   .append(" SET ")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(" = regexp_replace(")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(", E'^")
                                   .append(assnsSeparator)
                                   .append("', E'', 'ng'); UPDATE ")
                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                   .append(" SET ")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(" = regexp_replace(")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(", E'")
                                   .append(assnsSeparator)
                                   .append("$', E'', 'ng'); ");

                        // Check if the length of the command string has reached the limit
                        if (assnsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            dbCommand.executeDbUpdate(assnsModCmd, parent);
                            assnsModCmd = new StringBuilder("");
                        }
                   }

                    // Remove the trailing comma and space, then add the condition based on the row's
                    // primary key, and add the commands to update the internal tables
                    modCmd = CcddUtilities.removeTrailer(modCmd, ", ").append(" WHERE ")
                                                                      .append(typeDefn.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()])
                                                                      .append(" = ")
                                                                      .append(mod.getRowData()[DefaultColumn.PRIMARY_KEY.ordinal()])
                                                                      .append("; ");

                    // Check if the length of the command string has reached the limit
                    if (modCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(modCmd, parent);
                        modCmd = new StringBuilder("");
                    }
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
                            // Check if deleting existing entries is required. If no entries exist
                            // for this table then this step is skipped
                            if (needDelete)
                            {
                                // Build the command to delete the existing entry in the custom
                                // values table (in case it already exists)
                                int lastIndex = valDelCmd.size() - 1;
                                valDelCmd.get(lastIndex).append("DELETE FROM ")
                                                        .append(InternalTable.VALUES.getTableName())
                                                        .append(" WHERE ")
                                                        .append(ValuesColumn.TABLE_PATH.getColumnName())
                                                        .append(" = '")
                                                        .append(variablePath)
                                                        .append("' AND ")
                                                        .append(ValuesColumn.COLUMN_NAME.getColumnName())
                                                        .append(" = '")
                                                        .append(typeDefn.getColumnNamesUser()[column])
                                                        .append("'; ");

                                // Check if the length of the command string has reached the limit
                                if (valDelCmd.get(lastIndex).length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    // Start a new deletion command
                                    valDelCmd.add(new StringBuilder(""));
                                }
                            }

                            // Check if the new value does not begin with the flag that indicates
                            // the existing custom value should be removed
                            if (!mod.getRowData()[column].toString().startsWith(REPLACE_INDICATOR))
                            {
                                // Build the insertion command for this column's value
                                int lastIndex = valInsCmd.size() - 1;
                                valInsCmd.get(lastIndex).append("('")
                                                        .append(variablePath)
                                                        .append("', '")
                                                        .append(typeDefn.getColumnNamesUser()[column])
                                                        .append("', ")
                                                        .append(delimitText(mod.getRowData()[column]))
                                                        .append("), ");

                                // Check if the length of the command string has reached the limit
                                if (valInsCmd.get(lastIndex).length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    // Start a new insertion command
                                    valInsCmd.set(lastIndex,
                                                  CcddUtilities.removeTrailer(valInsCmd.get(lastIndex),
                                                                              ", "));
                                    valInsCmd.get(lastIndex).append("; ");
                                    valInsCmd.add(new StringBuilder(insertCmd));
                                }
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
                            // Create the commands to delete the variable from the link and telemetry
                            // scheduler tables
                            deleteLinkAndTlmPathRef(mod.getRowData()[mod.getArraySizeColumn()].toString(),
                                                    mod.getRowData()[mod.getVariableColumn()].toString(),
                                                    variablePath,
                                                    variablePathEsc,
                                                    linksDelCmd,
                                                    tlmDelCmd);

                            // Check if the length of the command string has reached the limit
                            if (linksDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(linksDelCmd, parent);
                                linksDelCmd = new StringBuilder("");
                            }

                            // Check if the length of the command string has reached the limit
                            if (tlmDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(tlmDelCmd, parent);
                                tlmDelCmd = new StringBuilder("");
                            }

                            break;
                        }
                    }
                }

                // Update the data fields
                fieldsModCmd.append(BuildReferencedVariablesDataFieldsCmd(tableInfo,
                                                                          mod,
                                                                          false,
                                                                          false,
                                                                          parent));

                // Check if the length of the command string has reached the limit
                if (fieldsModCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    dbCommand.executeDbUpdate(fieldsModCmd, parent);
                    fieldsModCmd = new StringBuilder("");
                }
            }

            // Execute the commands to modify the internal tables
            for (StringBuilder delCmd : valDelCmd)
            {
                if (delCmd.length() != 0)
                {
                    dbCommand.executeDbUpdate(delCmd, parent);
                }
            }

            // Check if the last custom values table insertion command contains any insertion data
            if (valInsCmd.get(valInsCmd.size() - 1).toString().endsWith(", "))
            {
                // Update the termination characters for the insertion command
                valInsCmd.set(valInsCmd.size() - 1, CcddUtilities.removeTrailer(valInsCmd.get(valInsCmd.size() - 1), ", "));
                valInsCmd.get(valInsCmd.size() - 1).append("; ");
            }

            for (StringBuilder insCmd : valInsCmd)
            {
                if (insCmd.toString().endsWith("; "))
                {
                    dbCommand.executeDbUpdate(insCmd, parent);
                }
            }

            if (modCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(modCmd, parent);
            }

            if (valuesModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(valuesModCmd, parent);
            }

            if (groupsModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(groupsModCmd, parent);
            }

            if (fieldsModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(fieldsModCmd, parent);
            }

            if (ordersModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(ordersModCmd, parent);
            }

            if (assnsModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(assnsModCmd, parent);
            }

            if (linksModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(linksModCmd, parent);
            }

            if (tlmModCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(tlmModCmd, parent);
            }

            if (linksDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(linksDelCmd, parent);
            }

            if (tlmDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(tlmDelCmd, parent);
            }

            // Check to see if this is a child table that is being converted to a root table
            if (changedDataTypes.size() != 0)
            {
                result = updateTablesRecentlyConvertedToRoot(changedDataTypes, parent);
            }

            result = true;
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "SQL command failed cause "
                                  + se.getMessage()
                                  + ".",
                                  "<html><b>SQL command failed</b>");
        }

        return result;
    }

    /**********************************************************************************************
     * Update the data fields of a table that is being converted from a non-root to a root
     *
     * @param updatedDataTypes List of tables that were converted from non-root to root
     *
     * @param parent           GUI component over which to center any error dialog
     *
     * @return result Did the update succeed
     *********************************************************************************************/
    private boolean updateTablesRecentlyConvertedToRoot(List<String> updatedDataTypes,
                                                        Component parent)
    {
        StringBuilder modCmd = new StringBuilder("");
        boolean result = false;

        // Update the root structures
        rootStructures = getRootStructures(parent);

        // Step through each data type
        for (int i = 0; i < updatedDataTypes.size(); i++)
        {
            // Check to see if this data type represents a root table
            if (rootStructures.contains(updatedDataTypes.get(i)))
            {
                // Update the new root table
                modCmd.append(updateTableFields(updatedDataTypes.get(i), "", "", parent, modCmd));
            }
        }

        try
        {
            if (modCmd.length() != 0)
            {
                // Execute the commands
                dbCommand.executeDbUpdate(modCmd, parent);
                result = true;
            }
        }
        catch (SQLException e)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "SQL command failed cause "
                                  + e.getMessage()
                                  + ".",
                                  "<html><b>SQL command failed</b>");
        }

        return result;
    }

    /**********************************************************************************************
     * Update the data fields of the new root table or members of a root table
     *
     * @param dataType    What is the data type of the table being updated
     *
     * @param parentPath  If this data type is used to define a member of a root table then what is
     *                    the path to the root table?
     *
     * @param currVarName If this data type is used to define a member of a root table then what is
     *                    the name of the member?
     *
     * @param parent      GUI component over which to center any error dialog
     *
     * @param modCmd      PSQL command to update the tables
     *
     * @return modCmd PSQL command to update the tables
     *********************************************************************************************/
    private StringBuilder updateTableFields(String dataType,
                                            String parentPath,
                                            String currVarName,
                                            Component parent,
                                            StringBuilder modCmd)
    {
        // Grab the data for the table being updated
        TableInfo currTableInfo = loadTableData(dataType, true, true, false, parent);
        List<Object[]> tableData = currTableInfo.getData();

        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(currTableInfo.getType());

        int dataTypeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
        int variableNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);

        if (dataTypeIndex != -1 && variableNameIndex != -1)
        {
            // Step through each row of data
            for (int x = 0; x < tableData.size(); x++)
            {
                // Check if the data type for this row is a primitive
                if (!dataTypeHandler.isPrimitive(tableData.get(x)[dataTypeIndex].toString()))
                {
                    // If it is a primitive type then construct the name used to reference its CCDD
                    // table
                    String childTableDbName = dataType
                                              + ","
                                              + tableData.get(x)[dataTypeIndex].toString()
                                              + "."
                                              + tableData.get(x)[variableNameIndex].toString();

                    // Check if the parent path is empty
                    if (parentPath != "")
                    {
                        // If not append the data to childTableDbName
                        childTableDbName = parentPath + "," + childTableDbName;
                    }

                    // Create the command used to update the data fields
                    modCmd.append(copyDataFieldCommand(CcddFieldHandler.getFieldTypeName(currTableInfo.getType()),
                                                       childTableDbName,
                                                       parent));

                    // Grab the table data for this non-primitive data type
                    List<Object[]> childTableData = loadTableData(tableData.get(x)[dataTypeIndex].toString(),
                                                                  true,
                                                                  true,
                                                                  false,
                                                                  parent).getData();

                    // Step through each row of table data
                    for (int i = 0; i < childTableData.size(); i++)
                    {
                        // Check if the data type for this row is a primitive
                        if (!dataTypeHandler.isPrimitive(childTableData.get(i)[dataTypeIndex].toString()))
                        {
                            // If not then recursively call this function
                            modCmd.append(updateTableFields(childTableData.get(i)[dataTypeIndex].toString(),
                                                            childTableDbName,
                                                            childTableData.get(i)[variableNameIndex].toString(),
                                                            parent,
                                                            modCmd));
                        }
                    }
                }
            }
        }

        // If the parentPath and currVarName is not empty then this table needs to be updated
        if (!parentPath.contentEquals("") && !currVarName.contentEquals(""))
        {
            // Construct the name used to reference its CCDD table
            String childTableDbName = parentPath + "," + dataType + "." + currVarName;

            // Create the command used to update the data fields
            modCmd.append(copyDataFieldCommand(CcddFieldHandler.getFieldTypeName(currTableInfo.getType()),
                                               childTableDbName,
                                               parent));
        }

        return modCmd;
    }

    /**********************************************************************************************
     * Build and execute the commands to delete a table row. Only prototype tables can have a row
     * deleted
     *
     * @param tableInfo          Table information
     *
     * @param deletions          List of row deletion information
     *
     * @param dbTableName        Name of the table to which to delete rows
     *
     * @param typeDefn           Table type definition
     *
     * @param skipInternalTables True to not build and execute the commands to update the internal
     *                           tables. This is used during a data type update where only the data
     *                           type name has changed in order to speed up the operation
     *
     * @param varRefChkResults   Results of the variable reference input type search
     *
     * @param cmdRefChkResults   Results of the command reference input type search
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return True if no errors are encountered building and executing the deletion commands
     *********************************************************************************************/
    private boolean buildAndExecuteDeletionCommand(TableInfo tableInfo,
                                                   List<TableModification> deletions,
                                                   String dbTableName,
                                                   TypeDefinition typeDefn,
                                                   boolean skipInternalTables,
                                                   ReferenceCheckResults varRefChkResults,
                                                   ReferenceCheckResults cmdRefChkResults,
                                                   Component parent)
    {
        boolean result = false;

        try
        {
            // StringBuilders used to hold the values of the various commands being built
            StringBuilder delCmd = new StringBuilder("");
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
                delCmd.append("DELETE FROM ")
                      .append(dbTableName)
                      .append(" WHERE ")
                      .append(typeDefn.getColumnNamesDatabase()[DefaultColumn.PRIMARY_KEY.ordinal()])
                      .append(" = ")
                      .append(del.getRowData()[DefaultColumn.PRIMARY_KEY.ordinal()])
                      .append("; ");

                // Check if the length of the command string has reached the limit
                if (delCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    dbCommand.executeDbUpdate(delCmd, parent);
                    delCmd = new StringBuilder("");
                }

                // Check if the internal tables are to be updated and the table represents a structure
                if (!skipInternalTables && typeDefn.isStructure())
                {
                    // Get the variable name, data type, and bit length
                    String variableName = del.getRowData()[del.getVariableColumn()].toString();
                    String dataType = del.getRowData()[del.getDataTypeColumn()].toString();

                    // Build the variable path
                    String variablePath = tableInfo.getPrototypeName() + "," + dataType + "." + variableName;

                    // Build the regular expression that matches the variable path in the table's
                    // prototype
                    String protoVarPathEsc = CcddUtilities.escapePostgreSQLReservedChars(variablePath);

                    // Build the regular expression that matches the variable path in any instance of
                    // the table's prototype
                    String instanceVarPathEsc = ".+,"
                                                + tableInfo.getPrototypeName()
                                                + "\\\\.[^,]*,"
                                                + dataType
                                                + "\\\\."
                                                + CcddUtilities.escapePostgreSQLReservedChars(variableName);

                    // Append the command to delete any instance of the variable from the custom values
                    // table
                    valuesDelCmd.append("DELETE FROM ")
                                .append(InternalTable.VALUES.getTableName())
                                .append(" WHERE ")
                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                .append(" ~ E'^")
                                .append(protoVarPathEsc)
                                .append("(?:,|:|$)' OR ")
                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                .append(" ~ E'^")
                                .append(instanceVarPathEsc)
                                .append("(?:,|:|$)'; ");

                    // Check if the length of the command string has reached the limit
                    if (valuesDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(valuesDelCmd, parent);
                        valuesDelCmd = new StringBuilder("");
                    }

                    // Append the command to delete the variable from the link and telemetry scheduler
                    // tables for references to the variable in the prototype table
                    deleteLinkAndTlmPathRef(del.getRowData()[del.getArraySizeColumn()].toString(),
                                            variableName,
                                            variablePath,
                                            protoVarPathEsc,
                                            linksDelCmd,
                                            tlmDelCmd);

                    // Append the command to delete the variable from the link and telemetry scheduler
                    // tables for all references to the variable in instances of the prototype table
                    deleteLinkAndTlmPathRef(del.getRowData()[del.getArraySizeColumn()].toString(),
                                            variableName,
                                            variablePath,
                                            instanceVarPathEsc,
                                            linksDelCmd,
                                            tlmDelCmd);

                    // Check if the length of the command string has reached the limit
                    if (linksDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(linksDelCmd, parent);
                        linksDelCmd = new StringBuilder("");
                    }

                    // Check if the length of the command string has reached the limit
                    if (tlmDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(tlmDelCmd, parent);
                        tlmDelCmd = new StringBuilder("");
                    }

                    // Check if the data type represents a structure
                    if (!dataTypeHandler.isPrimitive(dataType))
                    {
                        // Append the commands to update the internal tables for references to the
                        // variable in the prototype table and all references to the variable in
                        // instances of the prototype table
                        groupsDelCmd.append("DELETE FROM ")
                                    .append(InternalTable.GROUPS.getTableName())
                                    .append(" WHERE ")
                                    .append(GroupsColumn.MEMBERS.getColumnName())
                                    .append(" ~ E'^")
                                    .append(protoVarPathEsc)
                                    .append("(?:,|$)' OR ")
                                    .append(GroupsColumn.MEMBERS.getColumnName())
                                    .append(" ~ E'^")
                                    .append(instanceVarPathEsc)
                                    .append("(?:,|$)'; ");

                        // Check if the length of the command string has reached the limit
                        if (groupsDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            dbCommand.executeDbUpdate(groupsDelCmd, parent);
                            groupsDelCmd = new StringBuilder("");
                        }

                        fieldsDelCmd.append("DELETE FROM ")
                                    .append(InternalTable.FIELDS.getTableName())
                                    .append(" WHERE ")
                                    .append(FieldsColumn.OWNER_NAME.getColumnName())
                                    .append(" ~ E'^")
                                    .append(protoVarPathEsc)
                                    .append("(?:,|$)' OR ")
                                    .append(FieldsColumn.OWNER_NAME.getColumnName())
                                    .append(" ~ E'^")
                                    .append(instanceVarPathEsc)
                                    .append("(?:,|$)'; ");

                        // Check if the length of the command string has reached the limit
                        if (fieldsDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            dbCommand.executeDbUpdate(fieldsDelCmd, parent);
                            fieldsDelCmd = new StringBuilder("");
                        }

                        ordersDelCmd.append("DELETE FROM ")
                                    .append(InternalTable.ORDERS.getTableName())
                                    .append(" WHERE ")
                                    .append(OrdersColumn.TABLE_PATH.getColumnName())
                                    .append(" ~ E'^")
                                    .append(protoVarPathEsc)
                                    .append("(?:,|$)' OR ")
                                    .append(OrdersColumn.TABLE_PATH.getColumnName())
                                    .append(" ~ E'^")
                                    .append(instanceVarPathEsc)
                                    .append("(?:,|$)'; ");

                        // Check if the length of the command string has reached the limit
                        if (ordersDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            dbCommand.executeDbUpdate(ordersDelCmd, parent);
                            ordersDelCmd = new StringBuilder("");
                        }

                        String protoPathWithChildren = protoVarPathEsc + "(?:," + PATH_IDENT + ")?";
                        String instancePathWithChildren = instanceVarPathEsc + "(?:," + PATH_IDENT + ")?";
                        assnsDelCmd.append("UPDATE ")
                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                   .append(" SET ")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(" = regexp_replace(")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(", E'^")
                                   .append(protoPathWithChildren)
                                   .append("', E'', 'ng'); UPDATE ")
                                   .append(InternalTable.ASSOCIATIONS.getTableName())
                                   .append(" SET ")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(" = regexp_replace(")
                                   .append(AssociationsColumn.MEMBERS.getColumnName())
                                   .append(", E'^")
                                   .append(instancePathWithChildren)
                                   .append("', E'', 'ng'); ");

                        // Check if the length of the command string has reached the limit
                        if (assnsDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            dbCommand.executeDbUpdate(assnsDelCmd, parent);
                            assnsDelCmd = new StringBuilder("");
                        }
                    }

                    // Blank any table cells or data fields with the variable reference input type
                    // containing the deleted variable
                    deleteVariableReference(tableInfo.getPrototypeName(),
                                            variableName,
                                            varRefChkResults,
                                            valuesDelCmd,
                                            fieldsDelCmd);

                    // Check if the length of the command string has reached the limit
                    if (valuesDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(valuesDelCmd, parent);
                        valuesDelCmd = new StringBuilder("");
                    }

                    // Check if the length of the command string has reached the limit
                    if (fieldsDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(fieldsDelCmd, parent);
                        fieldsDelCmd = new StringBuilder("");
                    }
              }
                // Check if this is a command table
                else if (typeDefn.isCommand())
                {
                    // Blank any table cells or data fields with the command reference input type
                    // containing the deleted command
                    deleteCommandReference(del.getRowData()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME)].toString(),
                                           cmdRefChkResults,
                                           valuesDelCmd,
                                           fieldsDelCmd);

                    // Check if the length of the command string has reached the limit
                    if (valuesDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(valuesDelCmd, parent);
                        valuesDelCmd = new StringBuilder("");
                    }

                    // Check if the length of the command string has reached the limit
                    if (fieldsDelCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(fieldsDelCmd, parent);
                        fieldsDelCmd = new StringBuilder("");
                    }
                }
            }

            // Check is a command was created to change the associations table
            if (assnsDelCmd.length() != 0)
            {
                // Clean up any associations that no longer have a table referenced
                assnsDelCmd.append("UPDATE ")
                           .append(InternalTable.ASSOCIATIONS.getTableName())
                           .append(" SET ")
                           .append(AssociationsColumn.MEMBERS.getColumnName())
                           .append(" = regexp_replace(")
                           .append(AssociationsColumn.MEMBERS.getColumnName())
                           .append(", E'^")
                           .append(assnsSeparator)
                           .append("', E'', 'ng'); UPDATE ")
                           .append(InternalTable.ASSOCIATIONS.getTableName())
                           .append(" SET ")
                           .append(AssociationsColumn.MEMBERS.getColumnName())
                           .append(" = regexp_replace(")
                           .append(AssociationsColumn.MEMBERS.getColumnName())
                           .append(", E'")
                           .append(assnsSeparator)
                           .append("$', E'', 'ng'); ");
            }

            // Send the remaining commands
            if (delCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(delCmd, parent);
            }

            if (valuesDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(valuesDelCmd, parent);
            }

            if (groupsDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(groupsDelCmd, parent);
            }

            if (fieldsDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(fieldsDelCmd, parent);
            }

            if (ordersDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(ordersDelCmd, parent);
            }

            if (assnsDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(assnsDelCmd, parent);
            }

            if (linksDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(linksDelCmd, parent);
            }

            if (tlmDelCmd.length() != 0)
            {
                dbCommand.executeDbUpdate(tlmDelCmd, parent);
            }

            result = true;
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "SQL command failed cause "
                                  + se.getMessage()
                                  + ".",
                                  "<html><b>SQL command failed</b>");
        }

        return result;
    }

    /**********************************************************************************************
     * Build the command to update the variable name and/or the data type. Combine updates to array
     * members into a single command by using a regular expression to match the array indices
     *
     * @param orgVariablePath Original variable path
     *
     * @param newVariablePath New variable path
     *
     * @param tableName       Name of the internal table to update
     *
     * @param columnName      Name of the column in the internal table that contains the variable
     *                        path
     *
     * @param captureIn       Pattern match to capture any character(s) that precede(s) the
     *                        variable path; blank if none
     *
     * @param captureOut      Pattern for replacing the captured character(s) specified in
     *                        captureIn; blank if none
     *
     * @param includeChildren True to include child tables of the variable paths
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
        StringBuilder command = new StringBuilder();

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
                    newVariablePath = newVariablePath.replaceFirst("\\[0", "[\\\\\\\\" + captureGrp);
                    captureGrp++;
                }

                // Create the command to update the internal table for instances of array variables
                // of the prototype table
                String columnPattern = "E'^"
                                       + captureIn
                                       + orgVariablePath
                                       + (includeChildren ? "(,.*|$)" : "$")
                                       + "'";
                command.append("UPDATE ")
                       .append(tableName)
                       .append(" SET ")
                       .append(columnName)
                       .append(" = regexp_replace(")
                       .append(columnName)
                       .append(", ")
                       .append(columnPattern)
                       .append(", E'")
                       .append(captureOut)
                       .append(newVariablePath)
                       .append((includeChildren ? "\\\\" + captureGrp : ""))
                       .append("') WHERE ")
                       .append(columnName)
                       .append(" ~ ")
                       .append(columnPattern)
                       .append("; ");
            }
        }
        // The path doesn't contain an array member
        else
        {
            // Create the command to update the custom values table for instances of non-array
            // member variables of the prototype table
            String columnPattern = "E'^"
                    + captureIn
                    + orgVariablePath
                    + (includeChildren ? "(,.*|:\\\\d+|$)" : "$")
                    + "'";
            command.append("UPDATE ")
                   .append(tableName)
                   .append(" SET ")
                   .append(columnName)
                   .append(" = regexp_replace(")
                   .append(columnName)
                   .append(", ")
                   .append(columnPattern)
                   .append(", E'")
                   .append(captureOut)
                   .append(newVariablePath)
                   .append((includeChildren ? "\\\\" + captureGrp : ""))
                   .append("') WHERE ")
                   .append(columnName)
                   .append(" ~ ")
                   .append(columnPattern)
                   .append("; ");
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Create a copy of the specified table's table tree node and immediate child nodes
     *
     * @param tableName Table name
     *
     * @param tableTree CcddTableTreeHandler reference describing the table tree
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
     * @param orgTableNode Reference to the node prior to the update
     *
     * @param parent       GUI component over which to center any error dialog
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
     * @param protoTable            Prototype table name to which the string variable belongs
     *
     * @param dataType              String variable's data type name
     *
     * @param variableNameWithIndex String variable's name, including array index
     *********************************************************************************************/
    private void updateLinksForStringMemberAddition(String protoTable,
                                                    String dataType,
                                                    String variableNameWithIndex)
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
        String stringVariableName = ArrayVariable.removeStringSize(variableNameWithIndex);

        // Extract the string dimension value from the variable name, then decrement it by one -
        // this is used to find the existing string array member immediately preceding the new
        // member
        int[] stringArrayDim = ArrayVariable.getArrayIndexFromSize(ArrayVariable.getVariableArrayIndex(variableNameWithIndex));
        int stringIndex = stringArrayDim[stringArrayDim.length - 1] - 1;

        // Step through each link definition
        for (int index = linkDefns.size() - 1; index >= 0; index--)
        {
            // Get the link member in order to shorten subsequent calls
            String linkMember = linkDefns.get(index)[LinksColumn.MEMBER.ordinal()];

            // Check if the link member contains a reference to the string array
            if (linkMember.matches(protoTable
                                   + "(?:,|\\.[^,]+,)"
                                   + dataType + "\\."
                                   + stringVariableName
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
                                                                                              + stringVariableName
                                                                                              + "\\[)"
                                                                                              + stringIndex
                                                                                              + "(\\])",
                                                                                              "$1" + (stringIndex + 1) + "$2")});

                // Set the flag to indicate that a change to the link definitions was made
                updateLinks = true;
            }
        }
    }

    /**********************************************************************************************
     * Build the command to delete the specified table/variable references from the links table
     *
     * @param linksPath Table/variable path to remove from the links table. Leading or trailing
     *                  regular expression constructs must surround the path, and any reserved
     *                  PostgreSQL characters in the path must be escaped
     *
     * @param linksCmd  StringBuilder containing the existing links table deletion command. If
     *                  empty then a new command is initiated; otherwise the deletions are appended
     *                  to the existing command
     *********************************************************************************************/
    private void deleteLinkPathRef(String linksPath, StringBuilder linksCmd)
    {
        linksCmd.append("DELETE FROM ")
                .append(InternalTable.LINKS.getTableName())
                .append(" WHERE ")
                .append(LinksColumn.MEMBER.getColumnName())
                .append(" ~ E'")
                .append(linksPath)
                .append("'; ");
    }

    /**********************************************************************************************
     * Build the commands to delete the specified table/variable references from the telemetry
     * scheduler table
     *
     * @param tlmPath Table/variable path to remove from the telemetry scheduler table. Leading or
     *                trailing regular expression constructs must surround the path, and any
     *                reserved PostgreSQL characters in the path must be escaped
     *
     * @param tlmCmd  StringBuilder containing the existing telemetry scheduler table deletion
     *                command. If empty then a new command is initiated; otherwise the deletions
     *                are appended to the existing command
     *********************************************************************************************/
    private void deleteTlmPathRef(String tlmPath, StringBuilder tlmCmd)
    {
        tlmCmd.append("DELETE FROM ")
              .append(InternalTable.TLM_SCHEDULER.getTableName())
              .append(" WHERE ")
              .append(TlmSchedulerColumn.MEMBER.getColumnName())
              .append(" ~ E'^.*")
              .append(tlmSchSeparator)
              .append(tlmPath)
              .append("'; ");
    }

    /**********************************************************************************************
     * Build the command to delete the specified variable references from the telemetry scheduler
     * table
     *
     * @param variablePaths List of variable paths to remove from the telemetry scheduler table
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
     * @param arraySize       Variable array size
     *
     * @param variableName    Variable name
     *
     * @param variablePath    Variable path
     *
     * @param variablePathEsc Variable path with any PostgreSQL reserved characters escaped so that
     *                        the path can be used in a regular expression
     *
     * @param linksDelCmd     StringBuilder containing the existing links table deletion command
     *
     * @param tlmDelCmd       StringBuilder containing the existing telemetry scheduler table
     *                        deletion command
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
     * @param rateColName Rate column name
     *
     * @param linksCmd    Sub-command for references in the links table to tables of the target
     *                    type
     *
     * @param tlmSchCmd   Sub-command for references in the telemetry scheduler table to tables of
     *                    the target type
     *
     * @return Command to remove all references for the specified rate column name in tables of the
     *         changed table type in the links and telemetry scheduler tables
     *********************************************************************************************/
    private String deleteLinkAndTlmRateRef(String rateColName,
                                           StringBuilder linksCmd,
                                           StringBuilder tlmSchCmd)
    {
        StringBuilder command = new StringBuilder();
        command.append("DELETE FROM ")
               .append(InternalTable.LINKS.getTableName())
               .append(" WHERE ")
               .append(LinksColumn.RATE_NAME.getColumnName())
               .append(" = E'")
               .append(rateColName)
               .append("'")
               .append(linksCmd)
               .append("; DELETE FROM ")
               .append(InternalTable.TLM_SCHEDULER.getTableName())
               .append(" WHERE ")
               .append(TlmSchedulerColumn.RATE_NAME.getColumnName())
               .append(" = '")
               .append(rateColName)
               .append("'")
               .append(tlmSchCmd)
               .append("; ");

        return command.toString();
    }

    /**********************************************************************************************
     * Build the command to remove all references to the specified rate column name in the links
     * and telemetry scheduler tables
     *
     * @param rateColName Rate column name
     *
     * @return Command to remove all references to the specified rate column name in the links and
     *         telemetry scheduler tables
     *********************************************************************************************/
    private String deleteAllLinkAndTlmRateRefs(String rateColName)
    {
        StringBuilder command = new StringBuilder();
        command.append("DELETE FROM ")
               .append(InternalTable.LINKS.getTableName())
               .append(" WHERE ")
               .append(LinksColumn.RATE_NAME.getColumnName())
               .append(" = '")
               .append(rateColName)
               .append("'; DELETE FROM ")
               .append(InternalTable.TLM_SCHEDULER.getTableName())
               .append(" WHERE ")
               .append(TlmSchedulerColumn.RATE_NAME.getColumnName())
               .append(" = '")
               .append(rateColName)
               .append("'; ");

        return command.toString();
    }

    /**********************************************************************************************
     * Update any references to a variable that had its name changed in every table cell and data
     * field with a variable reference input type
     *
     * @param tableInfo        Reference to variable's table information
     *
     * @param oldVariableName  Original variable name
     *
     * @param newVariableName  New variable name
     *
     * @param oldDataType      Original data type
     *
     * @param newDataType      New data type
     *
     * @param varRefChkResults Results of the variable reference input type search
     *
     * @param valuesModCmd     StringBuilder containing the existing values table modification
     *                         command
     *
     * @param fieldsModCmd     StringBuilder containing the existing fields table modification
     *                         command
     *********************************************************************************************/
    private void modifyVariableReference(TableInfo tableInfo,
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
        String escapedOldVariableName = CcddUtilities.escapePostgreSQLReservedChars(oldVariableName);
        String protoTypeName = tableInfo.getPrototypeName();
        String match = "(^|.+," + protoTypeName + ".[^,]+,)" + oldDataType + "\\." + escapedOldVariableName + "(,.+|$)";

        // Check if the table with the variable name that changed is a root structure
        if (rootStructures.contains(tableInfo.getProtoVariableName()))
        {
            targetVars.add(new String[] {CcddUtilities.escapePostgreSQLReservedChars(tableInfo.getPrototypeName()
                                                                                     + ","
                                                                                     + oldVariableName),
                                         tableInfo.getPrototypeName() + "," + newVariableName});
        }
        // The table isn't a root structure
        else
        {
            // Step through every variable name
            for (String entry : variableHandler.getAllVariableNames())
            {
                // Check if the variable matches the pattern for the changed variable name
                if (entry.contains(protoTypeName))
                {
                    if (entry.contains(escapedOldVariableName))
                    {
                        if (entry.matches(match))
                        {
                            // Remove the data type(s) from the variable path (the variable
                            // reference input type only displays the variable name portion of the
                            // path)
                            String targetVar = CcddUtilities.escapePostgreSQLReservedChars(variableHandler.removeDataTypeFromVariablePath(entry));

                            // Check if the variable isn't already in the list
                            if (!targetVars.contains((Object) targetVar))
                            {
                                // Add the before and after variable names to the list for use in
                                // building the modification commands
                                targetVars.add(new String[] {targetVar,
                                                             variableHandler.removeDataTypeFromVariablePath(entry.replaceFirst(match,
                                                                                                                               "$1"
                                                                                                                               + newDataType
                                                                                                                               + "."
                                                                                                                               + newVariableName
                                                                                                                               + "$2"))});
                            }
                        }
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
                    valuesModCmd.append("UPDATE ")
                                .append(dbControl.getQuotedName(table))
                                .append(" SET ")
                                .append(varRef.getColumnDb())
                                .append(" = '")
                                .append(targetVar[1])
                                .append("' WHERE ")
                                .append(varRef.getColumnDb())
                                .append(" = E'")
                                .append(targetVar[0])
                                .append("'; ");

                    // Check if the table isn't a root structure
                    if (!rootStructures.contains(table))
                    {
                        // Update references to the variable reference from the instances
                        valuesModCmd.append("UPDATE ")
                                    .append(InternalTable.VALUES.getTableName())
                                    .append(" SET ")
                                    .append(ValuesColumn.VALUE.getColumnName())
                                    .append(" = '")
                                    .append(targetVar[1])
                                    .append("' WHERE ")
                                    .append(ValuesColumn.TABLE_PATH.getColumnName())
                                    .append(" ~ E'^.+,")
                                    .append(table + "\\\\..*$' AND ")
                                    .append(ValuesColumn.COLUMN_NAME.getColumnName())
                                    .append(" = '")
                                    .append(varRef.getColumnVisible())
                                    .append("' AND ")
                                    .append(ValuesColumn.VALUE.getColumnName())
                                    .append(" = E'")
                                    .append(targetVar[0])
                                    .append("'; ");
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
                fieldsModCmd.append("UPDATE ")
                            .append(InternalTable.FIELDS.getTableName())
                            .append(" SET ")
                            .append(FieldsColumn.FIELD_VALUE.getColumnName())
                            .append(" = E'")
                            .append(targetVar[1])
                            .append("' WHERE ")
                            .append(FieldsColumn.FIELD_TYPE.getColumnName())
                            .append(" = E'")
                            .append(DefaultInputType.VARIABLE_REFERENCE.getInputName())
                            .append("' AND ")
                            .append(FieldsColumn.FIELD_VALUE.getColumnName())
                            .append(" = E'")
                            .append(targetVar[0])
                            .append("'; ");
            }
        }
    }

    /**********************************************************************************************
     * Blank any references to a variable that has been deleted in every table cell and data field
     * with a variable reference input type
     *
     * @param protoTableName   Name of the prototype table to update
     *
     * @param variableName     Name of the deleted variable
     *
     * @param varRefChkResults Results of the variable reference input type search
     *
     * @param valuesDelCmd     StringBuilder containing the existing values table deletion command
     *
     * @param fieldsDelCmd     StringBuilder containing the existing fields table deletion command
     *********************************************************************************************/
    private void deleteVariableReference(String protoTableName,
                                         String variableName,
                                         ReferenceCheckResults varRefChkResults,
                                         StringBuilder valuesDelCmd,
                                         StringBuilder fieldsDelCmd)
    {
        // Get the path without the data type(s)
        String instanceVarPathEscNoDt = "(?:^|.+,)"
                                        + protoTableName
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
                valuesDelCmd.append("UPDATE ")
                            .append(dbControl.getQuotedName(table))
                            .append(" SET ")
                            .append(varRef.getColumnDb())
                            .append(" = '' WHERE ")
                            .append(varRef.getColumnDb())
                            .append(" ~ E'")
                            .append(instanceVarPathEscNoDt)
                            .append("'; ");

                // Check if the table isn't a root structure
                if (!rootStructures.contains(table))
                {
                    // Remove references to the variable reference from the instances
                    valuesDelCmd.append("UPDATE ")
                                .append(InternalTable.VALUES.getTableName())
                                .append(" SET ")
                                .append(ValuesColumn.VALUE.getColumnName())
                                .append(" = '' WHERE ")
                                .append(ValuesColumn.TABLE_PATH.getColumnName())
                                .append(" ~ E'^.+,")
                                .append(table)
                                .append("\\\\..*$' AND ")
                                .append(ValuesColumn.COLUMN_NAME.getColumnName())
                                .append(" = '")
                                .append(varRef.getColumnVisible())
                                .append("' AND ")
                                .append(ValuesColumn.VALUE.getColumnName())
                                .append(" ~ E'")
                                .append(instanceVarPathEscNoDt)
                                .append("'; ");
                }
            }
        }

        // Check if a data field has the variable reference input type
        if (varRefChkResults.isFieldUsesType())
        {
            // Blank the data field value if the variable path matches
            fieldsDelCmd.append("UPDATE ")
                        .append(InternalTable.FIELDS.getTableName())
                        .append(" SET ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" = '' WHERE ")
                        .append(FieldsColumn.FIELD_TYPE.getColumnName())
                        .append(" = E'")
                        .append(DefaultInputType.VARIABLE_REFERENCE.getInputName())
                        .append("' AND ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" ~ E'")
                        .append(instanceVarPathEscNoDt)
                        .append("'; ");
        }
    }

    /**********************************************************************************************
     * Update any references to a command that had its name, code, or argument path changed in
     * every table cell and data field with a command reference input type
     *
     * @param tablePath        Path for the table being updated
     *
     * @param oldCommandName   Original command name
     *
     * @param newCommandName   New command name
     *
     * @param oldCommandCode   Original command code
     *
     * @param newCommandCode   New command code
     *
     * @param oldCommandArg    Original command argument variable
     *
     * @param newCommandArg    New command argument variable
     *
     * @param cmdRefChkResults Results of the command reference input type search
     *
     * @param valuesModCmd     StringBuilder containing the existing values table modification
     *                         command
     *
     * @param fieldsModCmd     StringBuilder containing the existing fields table modification
     *                         command
     *********************************************************************************************/
    private void modifyCommandReference(String tablePath,
                                        String oldCommandName,
                                        String newCommandName,
                                        String oldCommandCode,
                                        String newCommandCode,
                                        String oldCommandArg,
                                        String newCommandArg,
                                        ReferenceCheckResults cmdRefChkResults,
                                        StringBuilder valuesModCmd,
                                        StringBuilder fieldsModCmd)
    {
        // Build the original and new command references
        String oldCmdRef = commandHandler.buildCommandReference(oldCommandName,
                                                                oldCommandCode,
                                                                oldCommandArg,
                                                                tablePath);
        String newCmdRef = commandHandler.buildCommandReference(newCommandName,
                                                                newCommandCode,
                                                                newCommandArg,
                                                                tablePath);

        // Step through each table type command reference input type reference
        for (InputTypeReference cmdRef : cmdRefChkResults.getReferences())
        {
            // Step through each table of this table type
            for (String table : cmdRef.getTables())
            {
                // Update references to the command reference from the table
                valuesModCmd.append("UPDATE ")
                            .append(dbControl.getQuotedName(table))
                            .append(" SET ")
                            .append(cmdRef.getColumnDb())
                            .append(" = '")
                            .append(newCmdRef)
                            .append("' WHERE ")
                            .append(cmdRef.getColumnDb())
                            .append(" = E'")
                            .append(oldCmdRef)
                            .append("'; ");
            }
        }

        // Check if a data field has the command reference input type
        if (cmdRefChkResults.isFieldUsesType())
        {
            // Update the data field value if the command path matches
            fieldsModCmd.append("UPDATE ")
                        .append(InternalTable.FIELDS.getTableName())
                        .append(" SET ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" = E'")
                        .append(newCmdRef)
                        .append("' WHERE ")
                        .append(FieldsColumn.FIELD_TYPE.getColumnName())
                        .append(" = E'")
                        .append(DefaultInputType.COMMAND_REFERENCE.getInputName())
                        .append("' AND ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" = E'")
                        .append(oldCmdRef)
                        .append("'; ");
        }
    }

    /**********************************************************************************************
     * Blank any references to a command that has been deleted in every table cell and data field
     * with a command reference input type
     *
     * @param commandName      Name of the deleted command
     *
     * @param cmdRefChkResults Results of the command reference input type search
     *
     * @param valuesDelCmd     StringBuilder containing the existing values table deletion command
     *
     * @param fieldsDelCmd     StringBuilder containing the existing fields table deletion command
     *********************************************************************************************/
    private void deleteCommandReference(String commandName,
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
                valuesDelCmd.append("UPDATE ")
                            .append(dbControl.getQuotedName(table))
                            .append(" SET ")
                            .append(cmdRef.getColumnDb())
                            .append(" = '' WHERE ")
                            .append(cmdRef.getColumnDb())
                            .append(" ~ E'")
                            .append(commandName)
                            .append("'; ");
            }
        }

        // Check if a data field has the command reference input type
        if (cmdRefChkResults.isFieldUsesType())
        {
            // Blank the data field value if the command matches
            fieldsDelCmd.append("UPDATE ")
                        .append(InternalTable.FIELDS.getTableName())
                        .append(" SET ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" = '' WHERE ")
                        .append(FieldsColumn.FIELD_TYPE.getColumnName())
                        .append(" = E'")
                        .append(DefaultInputType.COMMAND_REFERENCE.getInputName())
                        .append("' AND ")
                        .append(FieldsColumn.FIELD_VALUE.getColumnName())
                        .append(" ~ E'")
                        .append(commandName)
                        .append("'; ");
        }
    }

    /**********************************************************************************************
     * Build the commands to update any references to a message name or ID that has been modified
     * or deleted in every table cell and data field with a message name and ID reference input
     * type
     *
     * @param tableInfo          Table information
     *
     * @param typeDefn           Table's type definition
     *
     * @param modifications      List of row update information
     *
     * @param deletions          List of row deletion information
     *
     * @param msgIDRefChkResults Results of the message name and ID reference input type search
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return Message name and ID cells and fields update command
     *********************************************************************************************/
    private boolean updateMessageNameAndIDReference(TableInfo tableInfo,
                                                    TypeDefinition typeDefn,
                                                    List<TableModification> modifications,
                                                    List<TableModification> deletions,
                                                    ReferenceCheckResults msgIDRefChkResults,
                                                    Component parent)
    {
        boolean result = false;

        try
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
                for (int row = 0; row < tableInfo.getData().size(); row++)
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
                            msgNameIDs.add(tableInfo.getData().get(row)[nameCol].toString());
                            newMsgNameIDs.add(tableInfo.getData().get(row)[nameCol].toString());
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
                        msgNameIDs.add(fldInfo != null ? fldInfo.getValue() : "");
                        newMsgNameIDs.add(fldInfo != null ? fieldInfo.getValue() : "");
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
                        String newMsgNameIDOwner = newMsgNameIDs.get(index).isEmpty() ? ""
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
                                msgIDCmd.append("UPDATE ")
                                        .append(dbControl.getQuotedName(table))
                                        .append(" SET ")
                                        .append(msgIDRef.getColumnDb())
                                        .append(" = '")
                                        .append(newMsgNameIDOwner)
                                        .append("' WHERE ")
                                        .append(msgIDRef.getColumnDb())
                                        .append(" = E'")
                                        .append(msgNameIDOwner)
                                        .append("'; ");

                                // Check if the length of the command string has reached the limit
                                if (msgIDCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    dbCommand.executeDbUpdate(msgIDCmd, parent);
                                    msgIDCmd = new StringBuilder("");
                                }
                            }
                        }

                        // Check if a data field has the message name & ID reference input type
                        if (msgIDRefChkResults.isFieldUsesType())
                        {
                            // Update the data field value if the message name & ID matches
                            msgIDCmd.append("UPDATE ")
                                    .append(InternalTable.FIELDS.getTableName())
                                    .append(" SET ")
                                    .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                    .append(" = '")
                                    .append(newMsgNameIDOwner)
                                    .append("' WHERE ")
                                    .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                    .append(" = E'")
                                    .append(DefaultInputType.MESSAGE_REFERENCE.getInputName())
                                    .append("' AND ")
                                    .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                    .append(" = E'")
                                    .append(msgNameIDOwner)
                                    .append("'; ");

                            // Check if the length of the command string has reached the limit
                            if (msgIDCmd.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(msgIDCmd, parent);
                                msgIDCmd = new StringBuilder("");
                            }
                        }
                    }
                }

                // Set the flag to indicate if a message name & ID reference may have changed
                isMsgIDChange = msgIDCmd.length() != 0;
            }

            if (msgIDCmd.length() != 0)
            {
                // Execute the commands
                dbCommand.executeDbUpdate(msgIDCmd, parent);
                result = true;
            }
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "SQL command failed cause "
                                  + se.getMessage()
                                  + ".",
                                  "<html><b>SQL command failed</b>");
        }
        return result;
    }

    /**********************************************************************************************
     * Retrieve a list of internal table data from the database, ignoring script file information
     * tables
     *
     * @param intTable        Type of internal table to retrieve
     *
     * @param isIncludeRowNum True to read in the row number column in addition to the information
     *                        table columns
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return List of the items in the internal table. An empty list is returned if the specified
     *         table is empty or doesn't exist
     *********************************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      boolean isIncludeRowNum,
                                                      Component parent)
    {
        return retrieveInformationTable(intTable, isIncludeRowNum, null, parent);
    }

    /**********************************************************************************************
     * Retrieve a list of internal table data from the database
     *
     * @param intTable        Type of internal table to retrieve
     *
     * @param isIncludeRowNum True to read in the row number column in addition to the information
     *                        table columns
     *
     * @param scriptName      Script file name; ignored for non-script file information tables
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return List of the items in the internal table. An empty list is returned if the specified
     *         table is empty or doesn't exist
     *********************************************************************************************/
    protected List<String[]> retrieveInformationTable(InternalTable intTable,
                                                      boolean isIncludeRowNum,
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
                ResultSet infoData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM ").append(intTableName)
                                                                                                 .append(" ORDER BY ")
                                                                                                 .append(ROW_NUM_COLUMN_NAME)
                                                                                                 .append(";"),
                                                              parent);

                // Step through each of the query results
                while (infoData.next())
                {
                    int columnCount = infoData.getMetaData().getColumnCount();

                    if (!isIncludeRowNum)
                    {
                        --columnCount;
                    }

                    // Create an array to contain the column values
                    String[] columnValues = new String[columnCount];

                    // Step through each column in the row
                    for (int column = 0; column < columnCount; column++)
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
     * @param intTable     Type of internal table to store
     *
     * @param tableData    Array containing the table data to store
     *
     * @param tableComment Table comment; null if unchanged
     *
     * @param parent       GUI component over which to center any error dialog
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
     * @param intTable             Type of internal table to store
     *
     * @param tableData            List containing the table data to store
     *
     * @param fieldInformationList List containing the list of data field information for each
     *                             group with a data field update (only applicable to the groups
     *                             table); null if none
     *
     * @param deletedGroups        List containing the names of groups that have been deleted
     *
     * @param invalidLinkVars      List containing the link member variables that should be removed
     *                             from the telemetry scheduler due to the addition of one or more
     *                             variables; null or an empty list if there are no invalid link
     *                             members
     *
     * @param tableComment         Table comment; null if unchanged
     *
     * @param parent               GUI component over which to center any error dialog
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
     * @param intTable     Type of internal table to store
     *
     * @param tableData    Array containing the table data to store; unused for the table types
     *                     internal table
     *
     * @param tableComment Table comment; null if unchanged
     *
     * @param parent       GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void storeInformationTable(InternalTable intTable,
                                         List<String[]> tableData,
                                         String tableComment,
                                         Component parent)
    {
        storeInformationTable(intTable, tableData, null, null, null, tableComment, parent);

        // After storing the new information in the internal table make sure that the field handler
        // is in sync with the current data in the internal table
        if (intTable == InternalTable.FIELDS)
        {
            fieldHandler.buildFieldInformation(parent);
        }
    }

    /**********************************************************************************************
     * Store the internal table into the database
     *
     * @param intTable             Type of internal table to store
     *
     * @param tableData            List containing the table data to store; unused for the table
     *                             types internal table
     *
     * @param fieldInformationList List containing the list of data field information for each
     *                             group with a data field update (only applicable to the groups
     *                             table); null if none
     *
     * @param deletedGroups        List containing the names of groups that have been deleted
     *
     * @param invalidLinkVars      List containing the link member variables that should be removed
     *                             from the telemetry scheduler due to the addition of one or more
     *                             variables; null or an empty list if there are no invalid link
     *                             members
     *
     * @param tableComment         Table comment; null if unchanged
     *
     * @param parent               GUI component over which to center any error dialog
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
            StringBuilder command = new StringBuilder();

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
                            command.append(modifyFieldsCommand(CcddFieldHandler.getFieldGroupName(groupName),
                                                               null));
                        }
                    }

                    // Check if a list of group data fields is provided
                    if (fieldInformationList != null)
                    {
                        // Step through each group's data field information list
                        for (List<FieldInformation> fieldInformation : fieldInformationList)
                        {
                            // Build the command to modify the data fields for the group
                            command.append(modifyFieldsCommand(fieldInformation.get(0).getOwnerName(),
                                                               fieldInformation));
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
                    storeNonTableTypesInfoTable(intTable, tableData, tableComment, parent);
                    break;

                case LINKS:
                    // Build the commands for storing the links table and for deleting any invalid
                    // references in the telemetry scheduler table
                    storeNonTableTypesInfoTable(intTable, tableData, tableComment, parent);
                    command.append(deleteTlmPathRefs(invalidLinkVars));

                    break;

                case TABLE_TYPES:
                    // Build the command for storing the table type definitions table
                    storeTableTypesInfoTable(parent);
                    break;

                case VALUES:
                    break;

                case DBU_INFO:
                    break;
            }

            if (command.length() != 0)
            {
                // Execute the database update
                dbCommand.executeDbUpdate(command, parent);
            }

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder(intTableName).append(" stored"));

            // Update the table tree information
            if (intTable == InternalTable.GROUPS || intTable == InternalTable.TABLE_TYPES)
            {
                updateTableTree();
            }
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
            case DBU_INFO:
            case VALUES:
                break;
        }
    }

    /**********************************************************************************************
     * Build and send the command for storing the groups, script associations, links table, data
     * fields, or scripts
     *
     * @param intTable     Type of internal table to store
     *
     * @param tableData    Array containing the table data to store
     *
     * @param tableComment Table comment; null if unchanged
     *
     * @param parent       GUI component over which to center any error dialog
     *
     * @return true if an error occurred building the specified table
     *********************************************************************************************/
    boolean storeNonTableTypesInfoTable(InternalTable intTable,
                                        List<String[]> tableData,
                                        String tableComment,
                                        Component parent)
    {
        boolean errorFlag = false;

        // Get the internal table's name
        String tableName = intTable.getTableName(tableComment);
        StringBuilder command = new StringBuilder();

        // Delete the table contents if the table exists
        if (isTableExists(tableName, parent))
        {
            command.append("TRUNCATE ")
                   .append(tableName)
                   .append("; ");
        }
        // The table doesn't exist
        else
        {
        // Build the command to create the table
            command.append("CREATE TABLE ")
                   .append(tableName)
                   .append(" ")
                   .append(intTable.getColumnCommand(false))
                   .append(dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                       tableName));
        }

        try
        {
            // Check if no comment is provided
            if (tableComment == null)
            {
                // Get the comment for the internal table
                ResultSet comment = dbCommand.executeDbQuery(new StringBuilder("SELECT obj_description('public.").append(tableName)
                                                                                                                 .append("'::regclass, 'pg_class');"),
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
                command.append("COMMENT ON TABLE ")
                       .append(tableName)
                       .append(" IS ")
                       .append(delimitText(tableComment))
                       .append("; ");
            }

            // Check if the internal table contains any items
            if (!tableData.isEmpty())
            {
                // Append the insert value command to add the items
                command.append("INSERT INTO ").append(tableName).append(" VALUES ");

                // Initialize the row index column value
                long rowIndex = 1;

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
                        command.append(delimitText(column)).append(", ");
                    }

                    // Insert the row index column value
                    command.append(rowIndex);
                    ++rowIndex;

                    // Terminate the column
                    command.append("), ");
                }

                // Replace the trailing comma and space with a semicolon
                command = CcddUtilities.removeTrailer(command, ", ");
                command.append("; ");

                // Check if the length of the command string has reached the limit
                if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    // Terminate the command string and send it, then start a new one
                    dbCommand.executeDbUpdate(command, parent);
                    command = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES ");
                }
            }

            // Send any remaining commands
            if (command.toString().endsWith("; "))
            {
                dbCommand.executeDbUpdate(command, parent);
            }
        }
        catch (SQLException se)
        {
            // Inform the user that storing the internal table comment failed
            eventLog.logFailEvent(parent,
                                  "Failed to store internal table '"
                                  + tableName
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Failed to store internal table '</b>"
                                  + tableName
                                  + "<b>'");
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Build and send the command for storing the table type definitions table
     *
     * @param parent GUI component over which to center any error dialog
     *
     * @return true if an error occurred building the tables types table
     *********************************************************************************************/
    protected boolean storeTableTypesInfoTable(Component parent)
    {
        boolean errorFlag = false;

        try
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

            // Initialize the row index column value
            long rowIndex = 1;

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
            {
                // Step through each column definition
                for (int index = 0; index < typeDefn.getColumnNamesDatabase().length; index++)
                {
                    // Add the command to create the column
                    command.append("(")
                           .append(delimitText(typeDefn.getName()))
                           .append(", ")
                           .append(index)
                           .append(", '")
                           .append(typeDefn.getColumnNamesDatabase()[index])
                           .append("', '")
                           .append(typeDefn.getColumnNamesUser()[index])
                           .append("', ")
                           .append(delimitText(typeDefn.getColumnToolTips()[index]))
                           .append(", '")
                           .append(typeDefn.getInputTypes()[index].getInputName())
                           .append("', ")
                           .append(typeDefn.isRowValueUnique()[index])
                           .append(", ")
                           .append(typeDefn.isRequired()[index])
                           .append(", ")
                           .append(typeDefn.isStructureAllowed()[index])
                           .append(", ")
                           .append(typeDefn.isPointerAllowed()[index])
                           .append(", ")
                           .append(rowIndex)
                           .append("), ");

                    ++rowIndex;
                }

                // Check if the length of the command string has reached the limit
                if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                {
                    // Terminate the command, send it, and start a new one
                    command = CcddUtilities.removeTrailer(command, ", ").append(";");
                    dbCommand.executeDbUpdate(command, parent);
                    command = new StringBuilder("INSERT INTO ").append(InternalTable.TABLE_TYPES.getTableName())
                                                               .append(" VALUES ");
                }
            }

            // Terminate the command, add the table owner command, then send it
            command = CcddUtilities.removeTrailer(command, ", ").append("; ")
                                                                .append(dbControl.buildOwnerCommand(DatabaseObject.TABLE,
                                                                                                    InternalTable.TABLE_TYPES.getTableName()));
            dbCommand.executeDbUpdate(command, parent);
        }
        catch (SQLException se)
        {
            // Inform the user that storing the internal table comment failed
            eventLog.logFailEvent(parent,
                                  "Failed to store internal table '"
                                  + InternalTable.TABLE_TYPES.getTableName()
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Failed to store internal table '</b>"
                                  + InternalTable.TABLE_TYPES.getTableName()
                                  + "<b>'");
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Store the internal groups table into the database
     *
     * @param fieldInformationList List containing the list of data field information for each
     *                             group with a data field update (only applicable to the groups
     *                             table); null if none
     *
     * @param deletedGroups        List containing the names of groups that have been deleted
     *
     * @param parent               GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void updateGroupsTable(List<List<FieldInformation>> fieldInformationList,
                                     List<String> deletedGroups,
                                     Component parent)
    {
        try
        {
            StringBuilder command = new StringBuilder();

            // Check if a list of group data fields is provided
            if (fieldInformationList != null)
            {
                // Step through each group's data field information list
                for (List<FieldInformation> fieldInformation : fieldInformationList)
                {
                    // Build the command to modify the data fields for the group
                    command.append(modifyFieldsCommand(fieldInformation.get(0).getOwnerName(),
                                                       fieldInformation));

                    // Check if the length of the command string has reached the limit
                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(command, parent);
                        command = new StringBuilder("");
                    }
                }
            }

            // Check if a list of deleted groups is provided
            if (deletedGroups != null)
            {
                // Step through each deleted group
                for (String groupName : deletedGroups)
                {
                    // Build the command to delete the group's data fields
                    command.append(modifyFieldsCommand(CcddFieldHandler.getFieldGroupName(groupName),
                                                       null));

                    // Check if the length of the command string has reached the limit
                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbUpdate(command, parent);
                        command = new StringBuilder("");
                    }
                }
            }

            // Send any remaining commands
            if (command.length() != 0)
            {
                dbCommand.executeDbUpdate(command, parent);
            }

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder(InternalTable.GROUPS.getTableName()).append(" stored"));

            // Update the table tree information
            updateTableTree();
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot store internal table '"
                                  + InternalTable.GROUPS.getTableName()
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot store internal table '</b>"
                                  + InternalTable.GROUPS.getTableName()
                                  + "<b>'");
        }
    }

    /**********************************************************************************************
     * Remove unused entries from the internal groups table
     *
     * @param parent GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void cleanGroupsTable(Component parent)
    {
        // Get the list of table and variable paths and names, retaining any macros and bit lengths
        try
        {
            List<String> allTableAndVariableList = (new CcddTableTreeHandler(ccddMain,
                                                                             TableTreeType.TABLES_WITH_PRIMITIVES,
                                                                             ccddMain.getMainFrame())).getTableTreePathList(null);

            // Get all members of the internal groups table
            List<String[]> members = queryDatabase(new StringBuilder("SELECT ").append(GroupsColumn.MEMBERS.getColumnName())
                                                                               .append(" FROM ")
                                                                               .append(InternalTable.GROUPS.getTableName())
                                                                               .append(";"),
                                                   ccddMain.getMainFrame());

            // Initialize the command that will be used to update the internal groups table
            StringBuilder command = new StringBuilder();

            for (String[] member : members)
            {
                // Check if the table isn't in the list of valid names, but do not delete any
                // members of the groups table that start with a '0' or a '1' as these are rows
                // within the internal groups table that contain the description of the group and
                // rather or not the group represents a CFS application. Groups that represent a
                // CFS application will have this row start with a 0, and those that do not will
                // have this row start with a 1
                if (!allTableAndVariableList.contains(member[0])
                    && !member[0].substring(0, 2).contentEquals("0,")
                    && !member[0].substring(0, 2).contentEquals("1,"))
                {
                    // Group table member reference is invalid
                    command.append("DELETE FROM ")
                           .append(InternalTable.GROUPS.getTableName())
                           .append(" WHERE ")
                           .append(GroupsColumn.MEMBERS.getColumnName())
                           .append(" = ")
                           .append(CcddDbTableCommandHandler.delimitText(member[0]))
                           .append("; ");

                    // Check if the length of the command string has reached the limit
                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                    {
                        dbCommand.executeDbCommand(command, parent);
                        command = new StringBuilder("");
                    }
                }
            }

            if (command.length() != 0)
            {
                // Execute the command
                dbCommand.executeDbCommand(command, parent);
            }
        }
        catch (SQLException se)
        {
            // Inform the user that the database command failed
            eventLog.logFailEvent(parent,
                                  "Cannot store internal table '"
                                  + InternalTable.GROUPS.getTableName()
                                  + "'; cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot store internal table '</b>"
                                  + InternalTable.GROUPS.getTableName()
                                  + "<b>'");
        }
    }

    /**********************************************************************************************
     * Update the project description and data fields in the database
     *
     * @param description      Project database description
     *
     * @param fieldInformation Project data field information; null to delete the fields for the
     *                         specified project
     *
     * @param parent           GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void modifyProjectFields(String description,
                                       List<FieldInformation> fieldInformation,
                                       final Component parent)
    {
        boolean errorFlag = false;

        try
        {
            // Update the project's description
            dbCommand.executeDbUpdate(new StringBuilder(dbControl.buildDatabaseCommentCommandAndUpdateInternalTable(dbControl.getProjectName(),
                                                                                                                    dbControl.getDatabaseAdmins(dbControl.getDatabaseName()),
                                                                                                                    true,
                                                                                                                    description)),
                                      parent);

            // Replace the project data fields with the ones provided
            dbCommand.executeDbUpdate(new StringBuilder(modifyFieldsCommand(CcddFieldHandler.getFieldProjectName(),
                                                                            fieldInformation)),
                                      parent);
        }
        catch (SQLException se)
        {
            // Inform the user that updating the project data fields failed
            eventLog.logFailEvent(parent,
                                  "Cannot modify project data field(s); cause '"
                                  + se.getMessage()
                                  + "'",
                                  "<html><b>Cannot modify project data field(s)");
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
            if (parent instanceof CcddProjectFieldDialog)
            {
                CcddProjectFieldDialog editorDialog = (CcddProjectFieldDialog) parent;

                // Perform the project field modification clean-up steps
                editorDialog.doProjectFieldModificationComplete(errorFlag);
            }
        }
    }

    /**********************************************************************************************
     * Build the command for updating the data field definitions table for the specified data
     * table, table type, group, or project
     *
     * @param ownerName        Name of the table, including the path if this table represents a
     *                         structure, table type, group, or project to which the field(s)
     *                         belong
     *
     * @param fieldInformation Field information; null to delete the fields for the specified
     *                         table/table type/group
     *
     * @return Command for updating the table/table type/group fields in the data field table
     *********************************************************************************************/
    private String modifyFieldsCommand(String ownerName, List<FieldInformation> fieldInformation)
    {
        // Build the command to delete the existing field definitions for the specified table/group
        StringBuilder command = new StringBuilder("DELETE FROM ").append(InternalTable.FIELDS.getTableName())
                                                                 .append(" WHERE ")
                                                                 .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                                 .append(" = '")
                                                                 .append(ownerName)
                                                                 .append("'; ");

        // Check if any fields exist
        if (fieldInformation != null && !fieldInformation.isEmpty())
        {
            // Append the command to insert the field definitions
            command.append("INSERT INTO ").append(InternalTable.FIELDS.getTableName()).append(" VALUES ");

            // Step through each of the table's field definitions
            for (FieldInformation fieldInfo : fieldInformation)
            {
                // Add the command to insert the field information. If the owner of the fields to
                // be copied is a table type and the target owner isn't a table type then force the
                // inheritance flag to true
                command.append("('")
                       .append(ownerName)
                       .append("', ")
                       .append(delimitText(fieldInfo.getFieldName()))
                       .append(", ")
                       .append(delimitText(fieldInfo.getDescription()))
                       .append(", ")
                       .append(fieldInfo.getSize())
                       .append(", ")
                       .append(delimitText(fieldInfo.getInputType().getInputName()))
                       .append(", ")
                       .append(String.valueOf(fieldInfo.isRequired()))
                       .append(", ")
                       .append(delimitText(fieldInfo.getApplicabilityType().getApplicabilityName()))
                       .append(", ")
                       .append(delimitText(fieldInfo.getValue()))
                       .append(", ")
                       .append(String.valueOf(fieldInfo.isInherited()
                                              || (CcddFieldHandler.isTableTypeField(fieldInfo.getOwnerName())
                                                  && !CcddFieldHandler.isTableTypeField(ownerName))))
                       .append("), ");
            }

            command = CcddUtilities.removeTrailer(command, ", ").append("; ");
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Build the command for updating the internal fields table when a root table is modified to be
     * the child of a new table
     *
     * @param additions All of the tables that are being added to the database
     *
     * @return Command for updating the internal fields table
     *********************************************************************************************/
    private String modifyInternalFieldsTable(List<TableModification> additions)
    {
        StringBuilder command = new StringBuilder();
        ArrayList<String> processedDataTypes = new ArrayList<String>();

        for (TableModification add : additions)
        {
            if (add.getDataTypeColumn() != -1)
            {
                // Get the data type for this addition
                String dataType = (String) add.getRowData()[add.getDataTypeColumn()];

                // Check if this addition represents a root table
                if (rootStructures.contains(dataType))
                {
                    // Ensure that this root table has not already been processed
                    if (!processedDataTypes.contains(dataType))
                    {
                        command.append("DELETE from ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" WHERE owner_name LIKE '")
                               .append(dataType)
                               .append(",%'; ");

                        // Add this data type to the list of processed data types
                        processedDataTypes.add(dataType);
                    }
                }
            }
        }

        return command.toString();
    }

    /**********************************************************************************************
     * Change the name of a table type and update the existing tables of the specified type. This
     * command is executed in a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue to update. The GUI
     * menu commands, however, are disabled until the database command completes execution
     *
     * @param typeName   Current name of the table type
     *
     * @param newName    New name for this table type
     *
     * @param typeDialog Reference to the type manager dialog
     *********************************************************************************************/
    protected void renameTableType(final String typeName,
                                   final String newName,
                                   final CcddTableTypeManagerDialog typeDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, typeDialog, new BackgroundCommand()
        {
            String tableName;

            /**************************************************************************************
             * Rename table type command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;
                String[] tableNames = null;

                try
                {
                    // Store the table definitions table
                    storeTableTypesInfoTable(typeDialog);

                    // Create the command to update the table definitions table
                    StringBuilder command = new StringBuilder("");

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
                    command.append("UPDATE ")
                           .append(InternalTable.FIELDS.getTableName())
                           .append(" SET ")
                           .append(FieldsColumn.OWNER_NAME.getColumnName())
                           .append(" = ")
                           .append(delimitText(CcddFieldHandler.getFieldTypeName(newName)))
                           .append(" WHERE ")
                           .append(FieldsColumn.OWNER_NAME.getColumnName())
                           .append(" = ")
                           .append(delimitText(CcddFieldHandler.getFieldTypeName(typeName)))
                           .append("; ");

                    // Execute the command to change the table's type name
                    dbCommand.executeDbCommand(command, typeDialog);

                    // Log that renaming the table succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Table '").append(tableName)
                                                                  .append("' type renamed to '")
                                                                  .append(newName)
                                                                  .append("'"));
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
     * @param typeName   Name of the table type to copy
     *
     * @param copyName   Name for the copy of this table type
     *
     * @param typeDialog Reference to the type manager dialog
     *********************************************************************************************/
    protected void copyTableType(final String typeName,
                                 final String copyName,
                                 final CcddTableTypeManagerDialog typeDialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, typeDialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Copy table type command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;

                // List of field definitions
                List<String[]> fieldDefinitions = null;

                // Copy the target table type's data fields, set the owner of the copied fields
                // to the new owner, and get the complete list of field definitions
                fieldHandler.copyFields(CcddFieldHandler.getFieldTypeName(typeName),
                                        CcddFieldHandler.getFieldTypeName(copyName));
                fieldDefinitions = fieldHandler.getFieldDefnsFromInfo();

                // Send the command to rebuild the table types and fields tables
                errorFlag = storeTableTypesInfoTable(typeDialog);

                if (!errorFlag)
                {
                    errorFlag = storeNonTableTypesInfoTable(InternalTable.FIELDS,
                                                                   fieldDefinitions,
                                                                   null,
                                                                   typeDialog);

                    if (!errorFlag)
                    {
                        // Log that renaming the table succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          new StringBuilder("Table type '").append(typeName)
                                                                           .append("' copied to '")
                                                                           .append(copyName)
                                                                           .append("'"));
                    }
                }

                // Check if no error occurred when copying the table type and if the type
                // represents a telemetry structure
                if (!errorFlag && tableTypeHandler.getTypeDefinition(typeName).isTelemetryStructure())
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
     * @param typeDefn   Type definition to delete
     *
     * @param typeDialog Reference to the type manager dialog
     *
     * @param parent     GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void deleteTableType(final TypeDefinition typeDefn,
                                   final CcddTableTypeManagerDialog typeDialog,
                                   final Component parent)
    {
        // Set the flags to indicate if the type represents a structure and a telemetry structure
        final boolean isStructure = typeDefn.isStructure();
        final boolean isTlmStructure = typeDefn.isTelemetryStructure();

        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
        {
            /**************************************************************************************
             * Table type deletion command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;
                String[] tableNames = null;
                String names = "";

                try
                {
                    // Delete the table type definition from the table type handler
                    tableTypeHandler.getTypeDefinitions().remove(typeDefn);

                    // Store the table types table
                    storeTableTypesInfoTable(parent);

                    // Create the command to update the table definitions table
                    StringBuilder command = new StringBuilder("");

                    // Get an array containing tables of the specified type
                    tableNames = queryTablesOfTypeList(typeDefn.getName(), ccddMain.getMainFrame());

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
                                                                      + typeDefn.getName()
                                                                      + "<b>'?<br><br><i>Warning: This action cannot be undone!",
                                                                      "Delete Table(s)",
                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            for (String name : tableNames)
                            {
                                // Add the commands to delete the tables and all their references
                                // in the internal tables
                                command.append(deleteTableCommand(name, true)).append(" ");
                            }
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
                        command.append(modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeDefn.getName()),
                                                           null));

                        // Step through the array of table names
                        for (String name : tableNames)
                        {
                            // Remove the table's data fields
                            command.append(modifyFieldsCommand(name, null));
                        }

                        // Modify the (shortened) names string for logging messages
                        names = " and table(s) '</b>" + names + "<b>'";

                        // Delete the table(s)
                        dbCommand.executeDbUpdate(command, parent);

                        // Execute the command to reset the rate for links that no longer contain
                        // any variables
                        dbCommand.executeDbQuery(new StringBuilder("SELECT reset_link_rate();"), parent);

                        // Check if the the deleted type represented a structure
                        if (isStructure)
                        {
                            // Update the list of root structure tables
                            rootStructures = getRootStructures(parent);

                            // Rebuild the variable paths and offsets lists
                            variableHandler.buildPathAndOffsetLists();
                        }
                        // Check if the the deleted type represented a command
                        else if (typeDefn.isCommand())
                        {
                            // Rebuild the command list
                            commandHandler.buildCommandList();
                        }

                        // Log that table deletion succeeded
                        eventLog.logEvent(SUCCESS_MSG,
                                          new StringBuilder("Table type '").append(typeDefn.getName())
                                                                           .append("'")
                                                                           .append(CcddUtilities.removeHTMLTags(names))
                                                                           .append(" deleted"));
                    }
                }
                catch (SQLException se)
                {
                    // Inform the user that the table deletion failed
                    eventLog.logFailEvent(parent,
                                          "Cannot delete table type '"
                                          + typeDefn.getName()
                                          + "'"
                                          + CcddUtilities.removeHTMLTags(names)
                                          + "; cause '"
                                          + se.getMessage()
                                          + "'",
                                          "<html><b>Cannot delete table type '</b>"
                                          + typeDefn.getName()
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

                // Check if no error occurred when deleting the table type
                if (!errorFlag)
                {
                    // Check if the deleted type represents a structure
                    if (isStructure)
                    {
                        // Update the database functions that collect structure table members and
                        // structure-defining column data
                        dbControl.createStructureColumnFunctions();

                        // Check if the the deleted type represents a telemetry structure and the
                        // number of rate columns changed due to the type deletion
                        if (isTlmStructure && rateHandler.setRateInformation())
                        {
                            // Store the rate parameters in the project database
                            storeRateParameters(ccddMain.getMainFrame());
                        }
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
     * @param tableType          Table type name to modify
     *
     * @param fieldInformation   Data field information list
     *
     * @param overwriteFields    OverwriteFieldValueType: ALL to overwrite all field values, SAME
     *                           to overwrite only those fields with a matching value, EMPTY to
     *                           overwrite only fields with blank values, or NONE to not overwrite
     *                           any field values
     *
     * @param typeAdditions      List of new columns to add to the tables. Each list item is an
     *                           array containing: [0] column name (user), [1] column input type
     *
     * @param typeModifications  List of name changes of existing columns in the tables. Each list
     *                           item is an array containing: [0] original column name (user), [1]
     *                           new column name (user), [2] column input type
     *
     * @param typeDeletions      List of columns to remove from the tables. Each list item is an
     *                           array containing: [0] column name (user), [1] column input type
     *
     * @param columnOrderChange  True if the table type's column order changed
     *
     * @param originalDefn       Reference to the table type definition prior to making any changes
     *
     * @param newDataFields      Reference to the new data fields. This is only needed when no
     *                           editor is provided
     *
     * @param editorDialog       Reference to the table type editor dialog
     *
     * @param editor             Reference to the table type editor where the change(s) occurred
     *********************************************************************************************/
    protected void modifyTableTypeInBackground(final String tableType,
                                               final List<FieldInformation> fieldInformation,
                                               final OverwriteFieldValueType overwriteFields,
                                               final List<String[]> typeAdditions,
                                               final List<String[]> typeModifications,
                                               final List<String[]> typeDeletions,
                                               final boolean columnOrderChange,
                                               final TypeDefinition originalDefn,
                                               final List<String[]> newDataFields,
                                               final CcddTableTypeEditorDialog editorDialog,
                                               final CcddTableTypeEditorHandler editor)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, editorDialog, new BackgroundCommand()
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
                                newDataFields,
                                editorDialog,
                                editor);
            }
        });
    }

    /**********************************************************************************************
     * Add, rename, or delete table type column names in the table definitions table and update the
     * existing tables of the specified type
     *
     * @param typeName          Table type name to modify
     *
     * @param fieldInformation  Data field information list
     *
     * @param overwriteFields   OverwriteFieldValueType: ALL to overwrite all field values, SAME to
     *                          overwrite only those fields with a matching value, EMPTY to
     *                          overwrite only fields with blank values, or NONE to not overwrite
     *                          any field values
     *
     * @param typeAdditions     List of new columns to add to the tables. Each list item is an
     *                          array containing: [0] column name (user), [1] column input type
     *
     * @param typeModifications List of name changes of existing columns in the tables. Each list
     *                          item is an array containing: [0] original column name (user), [1]
     *                          new column name (user), [2] column input type
     *
     * @param typeDeletions     List of columns to remove from the tables. Each list item is an
     *                          array containing: [0] column name (user), [1] column input type
     *
     * @param columnOrderChange True if the table type's column order changed
     *
     * @param originalDefn      Reference to the table type definition prior to making any changes;
     *                          null if this is a new table type
     *
     * @param newDataFields     Reference to the new data fields. This is only needed when no
     *                          editor is provided
     *
     * @param editorDialog      Reference to the table type editor dialog
     *
     * @param editor            Reference to the table type editor where the change(s) occurred
     *********************************************************************************************/
    protected void modifyTableType(String typeName,
                                   List<FieldInformation> fieldInformation,
                                   OverwriteFieldValueType overwriteFields,
                                   List<String[]> typeAdditions,
                                   List<String[]> typeModifications,
                                   List<String[]> typeDeletions,
                                   boolean columnOrderChange,
                                   TypeDefinition originalDefn,
                                   List<String[]> newDataFields,
                                   CcddTableTypeEditorDialog editorDialog,
                                   CcddTableTypeEditorHandler editor)
    {
        try
        {
            boolean errorFlag = false;
            String[] tableNames = null;
            String names = "";
            List<StringBuilder> commands = new ArrayList<StringBuilder>();

            // Get the type definition based on the table type name
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(typeName);

            // Set the flags that indicates if the table type definition represents a structure
            // prior to and after making the updates
            boolean wasStructure = originalDefn != null ? originalDefn.isStructure() : false;
            boolean isStructure = typeDefn.isStructure();

            // Set the flags that indicates if the table type definition represents a command prior
            // to and after making the updates
            boolean wasCommand = originalDefn != null ? originalDefn.isCommand() : false;
            boolean isCommand = typeDefn.isCommand();

            // Store the table types table
            storeTableTypesInfoTable(editorDialog);

            // Create the command to update the table type definitions table
            StringBuilder command = new StringBuilder("");

            // Check if this isn't a new table type
            if (originalDefn != null)
            {
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
                        command.append("ALTER TABLE ")
                               .append(dbTable)
                               .append(" ADD COLUMN ")
                               .append(dbColumn)
                               .append(" text DEFAULT ''; ");

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
                        }
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
                            command.append("ALTER TABLE ")
                                   .append(dbTable)
                                   .append(" RENAME COLUMN ")
                                   .append(oldDbColumn)
                                   .append(" TO ")
                                   .append(newDbColumn + "; ");
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
                            command.append("UPDATE ")
                                   .append(dbTable)
                                   .append(" SET ")
                                   .append(newDbColumn)
                                   .append(" = '' WHERE ")
                                   .append(newDbColumn)
                                   .append(" != '' AND ")
                                   .append(newDbColumn)
                                   .append(" !~ E'^")
                                   .append(inputTypeHandler.getInputTypeByName(mod[3])
                                                           .getInputMatch()
                                                           .replaceFirst("\\^", "")
                                                           .replaceFirst("\\$$", "")
                                                           .replaceAll("\\?\\?", "?")
                                                           .replaceAll("\\\\", "\\\\\\\\\\\\"))
                                   .append("$'; ");
                        }

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
                        }
                    }

                    // Step through each deletion
                    for (String[] del : typeDeletions)
                    {
                        // Get the column name in database form
                        String dbColumn = tableTypeHandler.convertVisibleToDatabase(del[0], del[1], isStructure);

                        // Append the delete command
                        command.append("ALTER TABLE ")
                               .append(dbTable)
                               .append(" DROP COLUMN ")
                               .append(dbColumn)
                               .append("; ");

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
                        }
                    }

                    // Check if the column order changed
                    if (columnOrder.length() != 0)
                    {
                        // Replace the column order for all tables matching this table name. This
                        // resets the column order for all users
                        command.append("UPDATE ")
                               .append(InternalTable.ORDERS.getTableName())
                               .append(" SET ")
                               .append(OrdersColumn.COLUMN_ORDER.getColumnName())
                               .append(" = '")
                               .append(columnOrder)
                               .append("' WHERE ")
                               .append(OrdersColumn.TABLE_PATH.getColumnName())
                               .append(" ~ E'^")
                               .append(protoName)
                               .append("(,|$)'; ");

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
                        }
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
                        valuesCmd.append(ValuesColumn.TABLE_PATH.getColumnName())
                                 .append(" ~ E'[^,]+,")
                                 .append(protoName)
                                 .append("\\.[^,]+,[^,]+$' OR ");
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
                            linksCmd.append(LinksColumn.MEMBER.getColumnName())
                                    .append(" ~ E'(?:")
                                    .append(protoName)
                                    .append(",|[^,]+,")
                                    .append(protoName)
                                    .append("\\.[^,]+,[^,]+$)' OR ");
                            tlmSchCmd.append(TlmSchedulerColumn.MEMBER.getColumnName())
                                     .append(" ~ E'(?:")
                                     .append(tlmSchSeparator)
                                     .append(protoName)
                                     .append(",|[^,]+,")
                                     .append(protoName)
                                     .append("\\.[^,]+,[^,]+$)' OR ");
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

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
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
                            command.append("UPDATE ")
                                   .append(InternalTable.VALUES.getTableName())
                                   .append(" SET ")
                                   .append(ValuesColumn.COLUMN_NAME.getColumnName())
                                   .append(" = '")
                                   .append(mod[1])
                                   .append("' WHERE ")
                                   .append(ValuesColumn.COLUMN_NAME.getColumnName())
                                   .append(" = '")
                                   .append(mod[0])
                                   .append("'")
                                   .append(valuesCmd)
                                   .append("; ");

                            // Check if the length of the command string has reached the limit
                            if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                commands.add(command);
                                command = new StringBuilder("");
                            }
                        }

                        // Check if the table type represents a structure and the column input type
                        // was a rate
                        if (isStructure && wasStructure && mod[2].equals(DefaultInputType.RATE.getInputName()))
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
                                    command.append(deleteLinkAndTlmRateRef(mod[0], linksCmd, tlmSchCmd));
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
                                    command.append(deleteLinkAndTlmRateRef(mod[0], linksCmd, tlmSchCmd));
                                }
                                // Check if the new rate name isn't used by another structure table
                                // type (i.e., the rate name is unique to this table type)
                                else if (rateHandler.getRateInformationByRateName(mod[1]) == null)
                                {
                                    // Append the modify command for the links and telemetry
                                    // scheduler table
                                    command.append("UPDATE ")
                                           .append(InternalTable.LINKS.getTableName())
                                           .append(" SET ")
                                           .append(LinksColumn.RATE_NAME.getColumnName())
                                           .append(" = '")
                                           .append(mod[1])
                                           .append("' WHERE ")
                                           .append(LinksColumn.RATE_NAME.getColumnName())
                                           .append(" = '")
                                           .append(mod[0])
                                           .append("'; UPDATE ")
                                           .append(InternalTable.TLM_SCHEDULER.getTableName())
                                           .append(" SET ")
                                           .append(TlmSchedulerColumn.RATE_NAME.getColumnName())
                                           .append(" = '")
                                           .append(mod[1])
                                           .append("' WHERE ")
                                           .append(TlmSchedulerColumn.RATE_NAME.getColumnName())
                                           .append(" = '")
                                           .append(mod[0])
                                           .append("'; ");

                                    // Check if the length of the command string has reached the limit
                                    if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        commands.add(command);
                                        command = new StringBuilder("");
                                    }
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
                        command.append("DELETE FROM ")
                               .append(InternalTable.VALUES.getTableName())
                               .append(" WHERE ")
                               .append(ValuesColumn.COLUMN_NAME.getColumnName())
                               .append(" = '")
                               .append(del[0])
                               .append("'")
                               .append(valuesCmd)
                               .append("; ");

                        // Check if the length of the command string has reached the limit
                        if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                        {
                            commands.add(command);
                            command = new StringBuilder("");
                        }

                        // Check if the table type represents a structure and a rate column is
                        // deleted
                        if (isStructure && wasStructure && del[1].equals(DefaultInputType.RATE.getInputName()))
                        {
                            // Check if the rate name is used by another structure table type
                            if (rateHandler.getRateInformationByRateName(del[0]).getNumSharedTableTypes() > 1)
                            {
                                // Remove all references to tables of the changed type in the links
                                // and telemetry scheduler tables
                                command.append(deleteLinkAndTlmRateRef(del[0], linksCmd, tlmSchCmd));
                            }
                            // The rate name is unique to this table type
                            else
                            {
                                // Remove all references to the original rate column name in the
                                // links and telemetry/scheduler tables
                                command.append(deleteAllLinkAndTlmRateRefs(del[0]));
                            }

                            // Check if the length of the command string has reached the limit
                            if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                commands.add(command);
                                command = new StringBuilder("");
                            }
                        }
                    }
                }

                // ////////////////////////////////////////////////////////////////////////////////
                // UPDATE DATA FIELDS
                // ////////////////////////////////////////////////////////////////////////////////

                if (newDataFields != null)
                {
                    // Get the list of names of all tables of the specified type. Do not clear
                    // the pre-loaded table members list - the table type has been updated at
                    // this point, but the tables of the specified type have not, so attempting
                    // to load the tables' members would result in an error
                    List<String> tableNamesList = getAllTablesOfType(typeName,
                                                                     protoTableNames,
                                                                     false,
                                                                     editorDialog);
                    List<FieldInformation> tableTypeFields = fieldHandler.getFieldInformationByTableType(typeName);
                    boolean fieldsUpdated = false;
                    Object[][] newFields = null;

                    if (editor != null)
                    {
                        newFields = CcddFieldHandler.getFieldEditorDefinition(editor.getPanelFieldInformation());
                    }
                    else if (newDataFields.size() != 0)
                    {
                        newFields = new Object[newDataFields.size()][newDataFields.get(0).length];
                        newFields = newDataFields.toArray(newFields);

                        // The new fields are not in the expected order and need to be rearranged
                        for (int index1 = 0; index1 < newFields.length; index1++)
                        {
                            for (int index2 = 0; index2 < newFields[0].length - 1; index2++)
                            {
                                newFields[index1][index2] = newFields[index1][index2 + 1];
                            }

                            newFields[index1][newFields[0].length - 1] = Integer.toString(-1);
                        }
                    }

                    if (newFields != null)
                    {
                        // Step through each table of the specified type
                        for (String tableName : tableNamesList)
                        {
                            List<FieldInformation> currentFields = fieldHandler.getFieldInformationByOwner(tableName);

                            // Delete any fields that no longer exist
                            for (int currIndex = 0; currIndex < currentFields.size(); currIndex++)
                            {
                                boolean fieldMatched = false;

                                // Check to see if the existing field can be found in the list of
                                // new fields
                                for (int newIndex = 0; newIndex < newFields.length; newIndex++)
                                {
                                    if (newFields[newIndex][0].equals(currentFields.get(currIndex).getFieldName()))
                                    {
                                        fieldMatched = true;
                                        break;
                                    }
                                }

                                // If the existing field was not found that means it was deleted
                                if (fieldMatched == false)
                                {
                                    // Before deleting the field it is important to check if this
                                    // field is a custom field that was not inherited from the
                                    // table type, but rather added directly to this individual
                                    // table by a user. If no field with the same name is found in
                                    // the tableTypeFields then that means it is custom and should
                                    // not be deleted
                                    boolean isCustomField = true;

                                    for (int index = 0; index < tableTypeFields.size(); index++)
                                    {
                                        if (tableTypeFields.get(index).getFieldName().equals(currentFields.get(currIndex).getFieldName()))
                                        {
                                            isCustomField = false;
                                        }
                                    }

                                    if (isCustomField == false)
                                    {
                                        // Delete the field
                                        fieldHandler.getFieldInformation().remove(currentFields.get(currIndex));
                                        fieldsUpdated = true;
                                    }
                                }
                            }

                            // Now check to see which fields need to be updated and added
                            for (int newIndex = 0; newIndex < newFields.length; newIndex++)
                            {
                                boolean fieldMatched = false;

                                for (int currIndex = 0; currIndex < currentFields.size(); currIndex++)
                                {
                                    // If a match is found then replace it
                                    if (newFields[newIndex][FieldEditorColumnInfo.NAME.ordinal()].equals(currentFields.get(currIndex).getFieldName()))
                                    {
                                        String fieldValue = "";

                                        // Set the table field's value based on the overwrite type
                                        switch (overwriteFields)
                                        {
                                            case ALL:
                                                // Overwrite the table field's value with the
                                                // inheritable field's value
                                                fieldValue = newFields[newIndex][FieldEditorColumnInfo.VALUE.ordinal()].toString();
                                                break;

                                            case SAME:
                                                for (int index = 0; index < tableTypeFields.size(); index++)
                                                {
                                                    if (tableTypeFields.get(index).getFieldName().equals(currentFields.get(currIndex).getFieldName()))
                                                    {
                                                        fieldValue = newFields[newIndex][FieldEditorColumnInfo.VALUE.ordinal()].toString();
                                                        break;
                                                    }
                                                }

                                                break;

                                            case EMPTY:
                                                // Only overwrite the table field's value if it's
                                                // blank
                                                if (currentFields.get(currIndex).getValue().equals(""))
                                                {
                                                    fieldValue = newFields[newIndex][FieldEditorColumnInfo.VALUE.ordinal()].toString();
                                                }

                                                break;

                                            case NONE:
                                                // Keep the table field's current value
                                                fieldValue = currentFields.get(currIndex).getValue();
                                                break;

                                            default:
                                                // Keep the table field's current value
                                                fieldValue = currentFields.get(currIndex).getValue();
                                                break;
                                        }

                                        // Replace the existing field with the new one
                                        fieldHandler.getFieldInformation()
                                                    .set(fieldHandler.getFieldInformation().indexOf(currentFields.get(currIndex)),
                                                         new FieldInformation(tableName,
                                                                              newFields[newIndex][FieldEditorColumnInfo.NAME.ordinal()].toString(),
                                                                              newFields[newIndex][FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                                              inputTypeHandler.getInputTypeByName(newFields[newIndex][FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                                                                              Integer.parseInt(newFields[newIndex][FieldEditorColumnInfo.CHAR_SIZE.ordinal()].toString()),
                                                                              Boolean.parseBoolean(newFields[newIndex][FieldEditorColumnInfo.REQUIRED.ordinal()].toString()),
                                                                              ApplicabilityType.getApplicabilityByName(newFields[newIndex][FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()),
                                                                              fieldValue,
                                                                              true,
                                                                              currentFields.get(currIndex).getInputFld(),
                                                                              -1));

                                        fieldMatched = true;
                                        fieldsUpdated = true;
                                        break;
                                    }
                                }

                                boolean addField = false;

                                // If no match was found that means this is a new field
                                if (fieldMatched == false)
                                {
                                    // Check that the field is applicable to this table before
                                    // adding
                                    if (newFields[newIndex][FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()
                                                                                                          .equals(ApplicabilityType.ROOT_ONLY.getApplicabilityName())
                                        && isRootStructure(tableName))
                                    {
                                        addField = true;
                                    }
                                    else if (newFields[newIndex][FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()
                                                                                                               .equals(ApplicabilityType.CHILD_ONLY.getApplicabilityName())
                                             && !isRootStructure(tableName))
                                    {
                                        addField = true;
                                    }
                                    else if (newFields[newIndex][FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()
                                                                                                               .equals(ApplicabilityType.ALL.getApplicabilityName()))
                                    {
                                        addField = true;
                                    }

                                    if (addField == true)
                                    {
                                        // Add the data field to the table and set the flag
                                        // indicating a change has been made
                                        fieldHandler.getFieldInformation()
                                                    .add(new FieldInformation(tableName,
                                                                              newFields[newIndex][FieldEditorColumnInfo.NAME.ordinal()].toString(),
                                                                              newFields[newIndex][FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                                              inputTypeHandler.getInputTypeByName(newFields[newIndex][FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                                                                              Integer.parseInt(newFields[newIndex][FieldEditorColumnInfo.CHAR_SIZE.ordinal()].toString()),
                                                                              Boolean.parseBoolean(newFields[newIndex][FieldEditorColumnInfo.REQUIRED.ordinal()].toString()),
                                                                              ApplicabilityType.getApplicabilityByName(newFields[newIndex][FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()),
                                                                              newFields[newIndex][FieldEditorColumnInfo.VALUE.ordinal()].toString(),
                                                                              true,
                                                                              null,
                                                                              -1));
                                        fieldsUpdated = true;
                                    }
                                }
                            }

                            // Check if any fields were added, modified, or deleted
                            if (fieldsUpdated == true)
                            {
                                // Create the command to modify the table's data field entries
                                command.append(modifyFieldsCommand(tableName,
                                                                   fieldHandler.getFieldInformationByOwner(tableName)));

                                // Check if the length of the command string has reached the limit
                                if (command.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                {
                                    commands.add(command);
                                    command = new StringBuilder("");
                                }
                            }
                        }
                    }

                    // Convert the list of modified tables names to an array
                    tableNames = tableNamesList.toArray(new String[0]);

                    // Check if any table of this type exists
                    if (tableNames.length != 0)
                    {
                        // Convert the array of tables names into a single string and shorten it if
                        // too long
                        names = " and table(s) '</b>" + CcddUtilities.convertArrayToStringTruncate(tableNames) + "<b>'";
                    }
                }

                // Build the command to update the data fields table
                command.append(modifyFieldsCommand(CcddFieldHandler.getFieldTypeName(typeName),
                                                   fieldInformation));
            }

            try
            {
                if (command.length() != 0)
                {
                    commands.add(command);
                }

                for (StringBuilder cmd : commands)
                {
                    // Execute the command to change the table type and any tables of this type
                    dbCommand.executeDbCommand(cmd, editorDialog);
                }

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
                                  new StringBuilder("Table type '").append(typeName)
                                                                   .append("'")
                                                                   .append(CcddUtilities.removeHTMLTags(names))
                                                                   .append(" updated"));
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

            // Check that no errors occurred and if the table type represents or represented a
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
            if (editor != null)
            {
                editorDialog.doTypeModificationComplete(errorFlag, editor, tableNames);
            }

            // Check that no errors occurred
            if (!errorFlag)
            {
                // Update the table tree
                updateTableTree();
            }
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
     * @param tableNames List containing arrays of information on the table name(s) to close. For a
     *                   prototype table each array in the list contains the prototype name and a
     *                   null. If the deletion is due to a change in a variable's structure data
     *                   type then the array contains the immediate parent table name and the
     *                   original data type (i.e., structure table name) of the variable
     *
     * @param parent     GUI component over which to center any error dialog
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
     * @param parent GUI component over which to center any error dialog
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
     * @param inputTypeNames List of the input type names, before and after the changes; null if
     *                       none of the input type names changed
     *
     * @param parent         GUI component over which to center any error dialog
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
     * @param modifications List of data field value modifications
     *
     * @param deletions     List of data field value deletions
     *
     * @param editorWindow  Reference to the data field editor
     *********************************************************************************************/
    protected void modifyDataFieldValues(final List<String[]> modifications,
                                         final List<String[]> deletions,
                                         final CcddFieldTableEditorDialog editorWindow)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, editorWindow, new BackgroundCommand()
        {
            /**************************************************************************************
             * Modify table types command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;

                try
                {
                    StringBuilder command = new StringBuilder("");

                    // Step through each modification
                    for (String[] mod : modifications)
                    {
                        // Build the command to update the data field value in the data fields
                        // table
                        command.append("UPDATE ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" SET ")
                               .append(FieldsColumn.FIELD_VALUE.getColumnName())
                               .append(" = ")
                               .append(delimitText(mod[2]))
                               .append(" WHERE ")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(" = '")
                               .append(mod[0])
                               .append("' AND ")
                               .append(FieldsColumn.FIELD_NAME.getColumnName())
                               .append(" = '")
                               .append(mod[1])
                               .append("'; ");
                    }

                    // Step through each deletion
                    for (String[] del : deletions)
                    {
                        // Build the command to delete the data field from the data fields table
                        command.append("DELETE FROM ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" WHERE ")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(" = '")
                               .append(del[0])
                               .append("' AND ")
                               .append(FieldsColumn.FIELD_NAME.getColumnName())
                               .append(" = '")
                               .append(del[1])
                               .append("'; ");
                    }

                    // Execute the command to change the data fields
                    dbCommand.executeDbCommand(command, editorWindow);

                    // Log that updating the data fields succeeded
                    eventLog.logEvent(SUCCESS_MSG, new StringBuilder("Table data fields updated"));
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
     * @param modifications List of data type (macro) definition modifications
     *
     * @param updates       List of string arrays reflecting the content of the data types (macros)
     *                      after being changed in the data type (macro) editor
     *
     * @param dialog        Reference to the data type or macro editor dialog
     *********************************************************************************************/
    protected void modifyTablesPerDataTypeOrMacroChangesInBackground(final List<TableModification> modifications,
                                                                     final List<String[]> updates,
                                                                     final CcddDialogHandler dialog)
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Modify data types (macros)
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Modify the data type (macro) references in the tables
                boolean errorFlag = modifyTablesPerDataTypeOrMacroChanges(modifications, updates, dialog);

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
     * @param modifications List of data type (macro) definition modifications
     *
     * @param updates       List of string arrays reflecting the content of the data types (macros)
     *                      after being changed in the data type (macro) editor
     *
     * @param dialog        Reference to the data type or macro editor dialog
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
            newDataTypeHandler = new CcddDataTypeHandler(updates, ccddMain);
            newMacroHandler = new CcddMacroHandler(ccddMain, ccddMain.getMacroHandler().getMacroData());
            newDataTypeHandler.setMacroHandler(newMacroHandler);
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
            private final TableInfo tableInformation;
            private final CcddTableEditorHandler editor;

            /**************************************************************************************
             * Class constructor for table information for those tables modified due to changes in
             * a data type (macro) definition
             *
             * @param tablePath Table path (if applicable) and name
             *************************************************************************************/
            private ModifiedTable(String tablePath)
            {
                // Load the table's information from the project database
                tableInformation = loadTableData(tablePath, false, true, false, dialog);

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
            protected TableInfo getTableInformation()
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
                    String originalSize = mod.getOriginalRowData()[DataTypesColumn.SIZE.ordinal()].toString();
                    String newSize = mod.getRowData()[DataTypesColumn.SIZE.ordinal()].toString();

                    // Check to see if a macro was used to define the size of this data type
                    if (originalSize.contains("##"))
                    {
                        originalSize = newMacroHandler.getMacroExpansion(originalSize);
                    }
                    if (newSize.contains("##"))
                    {
                        newSize = newMacroHandler.getMacroExpansion(newSize);
                    }

                    // Set to false if the size increased or the base type changed; keep equal to
                    // true if only the data type name has changed and the size is no larger
                    nameChangeOnly = Integer.valueOf(originalSize) >= Integer.valueOf(newSize)
                                     && mod.getOriginalRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString()
                                           .equals(mod.getRowData()[DataTypesColumn.BASE_TYPE.ordinal()].toString());
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
                    tableName = refContext[ValuesColumn.TABLE_PATH.ordinal()].replaceAll("(\"|\\s|,[^\\.]*\\.[^,]*$)",
                                                                                         "");
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
                int changeColumnIndex = isPrototype ? typeDefn.getColumnIndexByDbName(changeColumn)
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
                                        || (!isDataType && macroValue != null
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
                        if (isPrototype ? matchColumn.equals(tableData.get(row)[DefaultColumn.PRIMARY_KEY.ordinal()])
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
                                                newValue = macroHandler.replaceMacroName(oldNameDelim, newNameDelim,
                                                                                         oldValue);
                                            }
                                        }

                                        // Check if this a change was made to the data type size or
                                        // base type
                                        if (isDataType && isValueChange)
                                        {
                                            // Check if the data type reference isn't an exact
                                            // match (stand-alone or within a sizeof() call)
                                            if (!newValue.equals(newName) && !CcddVariableHandler.hasSizeof(newValue)
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
                                        table.validateCellContent(tableData, row, column, oldValue, newValue, false,
                                                                  true);

                                        // Load the updated array of data into the table
                                        table.loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), false);
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

                        // Create a list of all of the entries to be updated
                        ArrayList<ImmutableTriple<String, String, String>> vals = new ArrayList<ImmutableTriple<String, String, String>>();

                        // Add each triple that is to be updated
                        vals.add(new ImmutableTriple<String, String, String>(InternalTable.LINKS.getTableName(),
                                                                             LinksColumn.MEMBER.getColumnName(),
                                                                             LinksColumn.MEMBER.getColumnName()));
                        vals.add(new ImmutableTriple<String, String, String>(InternalTable.TLM_SCHEDULER.getTableName(),
                                                                             TlmSchedulerColumn.MEMBER.getColumnName(),
                                                                             TlmSchedulerColumn.MEMBER.getColumnName()));
                        vals.add(new ImmutableTriple<String, String, String>(InternalTable.VALUES.getTableName(),
                                                                             ValuesColumn.TABLE_PATH.getColumnName(),
                                                                             ValuesColumn.TABLE_PATH.getColumnName()));

                        // Generate the SQL command to replace the name
                        String replaceCommand = ", E'(.*,)"
                                                + oldName
                                                + "(\\..*)'"
                                                + ", E'\\\\1"
                                                + newName
                                                + "\\\\2'); ";

                        StringBuilder sqlCommand = new StringBuilder();

                        // Go through the list and generate the SQL command for each item
                        for (ImmutableTriple<String, String, String> v : vals)
                        {
                            sqlCommand.append("UPDATE ")
                                      .append(v.left)
                                      .append(" SET ")
                                      .append(v.middle)
                                      .append(" = regexp_replace(")
                                      .append(v.right)
                                      .append(replaceCommand)
                                      .append(" ");
                        }

                        // Execute the command
                        dbCommand.executeDbUpdate(sqlCommand, dialog);

                    }
                    // This is a macro change
                    else
                    {
                        // Get the original and updated user-defined macro names (with the
                        // delimiters)
                        String oldName = CcddMacroHandler.getFullMacroName(mod.getOriginalRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString()).replaceAll("([\\(\\)])", "\\\\\\\\$1");
                        String newName = CcddMacroHandler.getFullMacroName(mod.getRowData()[MacrosColumn.MACRO_NAME.ordinal()].toString());

                        // Execute the command to update the internal tables that reference
                        // variable and table paths
                        StringBuilder command = new StringBuilder();
                        command.append("UPDATE ")
                               .append(InternalTable.ASSOCIATIONS.getTableName())
                               .append(" SET ")
                               .append(AssociationsColumn.MEMBERS.getColumnName())
                               .append(" = regexp_replace(")
                               .append(AssociationsColumn.MEMBERS.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(AssociationsColumn.MEMBERS.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("'; UPDATE ")
                               .append(InternalTable.FIELDS.getTableName())
                               .append(" SET ")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(" = regexp_replace(")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("'; UPDATE ")
                               .append(InternalTable.GROUPS.getTableName())
                               .append(" SET ")
                               .append(GroupsColumn.MEMBERS.getColumnName())
                               .append(" = regexp_replace(")
                               .append(GroupsColumn.MEMBERS.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(GroupsColumn.MEMBERS.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("'; UPDATE ")
                               .append(InternalTable.LINKS.getTableName())
                               .append(" SET ")
                               .append(LinksColumn.MEMBER.getColumnName())
                               .append(" = regexp_replace(")
                               .append(LinksColumn.MEMBER.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(LinksColumn.MEMBER.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("'; UPDATE ")
                               .append(InternalTable.TLM_SCHEDULER.getTableName())
                               .append(" SET ")
                               .append(TlmSchedulerColumn.MEMBER.getColumnName())
                               .append(" = regexp_replace(")
                               .append(TlmSchedulerColumn.MEMBER.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(TlmSchedulerColumn.MEMBER.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("'; UPDATE ")
                               .append(InternalTable.VALUES.getTableName())
                               .append(" SET ")
                               .append(ValuesColumn.TABLE_PATH.getColumnName())
                               .append(" = regexp_replace(")
                               .append(ValuesColumn.TABLE_PATH.getColumnName())
                               .append(", E'(.*)")
                               .append(oldName)
                               .append("(.*)'")
                               .append(", E'\\\\1")
                               .append(newName)
                               .append("\\\\2') WHERE ")
                               .append(ValuesColumn.TABLE_PATH.getColumnName())
                               .append(" ~ E'")
                               .append(oldName)
                               .append("';");

                        dbCommand.executeDbUpdate(command, dialog);
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
                                    true,
                                    true,
                                    false,
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
                storeNonTableTypesInfoTable(InternalTable.DATA_TYPES,
                                                   CcddUtilities.removeArrayListColumn(updates,
                                                                                       DataTypesColumn.ROW_NUM.ordinal()),
                                                   null,
                                                   dialog);
                storeNonTableTypesInfoTable(InternalTable.MACROS,
                                                   CcddUtilities.removeArrayListColumn(newMacroHandler.getMacroData(),
                                                                                       MacrosColumn.ROW_NUM.ordinal()),
                                                   null,
                                                   dialog);
            }
            // This is a macro change
            else
            {
                // Store the macro table
                storeNonTableTypesInfoTable(InternalTable.MACROS,
                                                   CcddUtilities.removeArrayListColumn(updates,
                                                                                       MacrosColumn.ROW_NUM.ordinal()),
                                                   null,
                                                   dialog);
            }

            // Release the save point. This must be done within a transaction block, so it must be
            // done prior to the commit below
            dbCommand.releaseSavePoint(dialog);

            // Commit the change(s) to the database
            dbControl.getConnection().commit();

            // Update the table tree
            updateTableTree();

            // Inform the user that the update succeeded
            eventLog.logEvent(SUCCESS_MSG, new StringBuilder(changeName).append(" and all affected tables updated"));
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
                                  "<html><b>Cannot update "
                                  + changeName.toLowerCase());
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, dialog);
            errorFlag = true;
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
                                      + se.getMessage() + "'",
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
     * @param modifications List of input type definition modifications
     *
     * @param updates       Array of string arrays reflecting the content of the input types after
     *                      being changed in the input type editor
     *
     * @param dialog        Reference to the input type editor dialog
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
            private final TableInfo tableInformation;
            private final CcddTableEditorHandler editor;

            /**************************************************************************************
             * Class constructor for table information for those tables modified due to changes in
             * an input type definition
             *
             * @param tablePath Table path (if applicable) and name
             *************************************************************************************/
            ModifiedTable(String tablePath)
            {
                // Load the table's information from the project database
                tableInformation = loadTableData(tablePath, false, true, false, dialog);

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
            protected TableInfo getTableInformation()
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
            /**************************************************************************************
             * Modify input types
             *************************************************************************************/
            @Override
            protected void execute()
            {
                boolean errorFlag = false;
                List<String[]> inputTypeNames = new ArrayList<String[]>();
                List<ModifiedTable> modifiedTables = new ArrayList<ModifiedTable>();

                try
                {
                    StringBuilder tableTypeCommand = new StringBuilder();
                    StringBuilder fieldCommand = new StringBuilder();

                    // Create a save point in case an error occurs while modifying a table
                    dbCommand.createSavePoint(dialog);

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
                            tableTypeCommand.append("UPDATE ")
                                            .append(InternalTable.TABLE_TYPES.getTableName())
                                            .append(" SET ")
                                            .append(TableTypesColumn.INPUT_TYPE.getColumnName())
                                            .append(" = ")
                                            .append(delimitText(newName))
                                            .append(" WHERE ")
                                            .append(TableTypesColumn.INPUT_TYPE.getColumnName())
                                            .append(" = ")
                                            .append(delimitText(oldName))
                                            .append("; ");

                            // Check if the length of the command string has reached the limit
                            if (tableTypeCommand.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(tableTypeCommand, dialog);
                                tableTypeCommand = new StringBuilder("");
                            }

                            fieldCommand.append("UPDATE ")
                                        .append(InternalTable.FIELDS.getTableName())
                                        .append(" SET ")
                                        .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                        .append(" = ")
                                        .append(delimitText(newName))
                                        .append(" WHERE ")
                                        .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                        .append(" = ")
                                        .append(delimitText(oldName))
                                        .append("; ");

                            // Check if the length of the command string has reached the limit
                            if (fieldCommand.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                            {
                                dbCommand.executeDbUpdate(fieldCommand, dialog);
                                fieldCommand = new StringBuilder("");
                            }
                        }

                        // Check if the input type regular expression match string or selection
                        // item list changed
                        if (!mod.getOriginalRowData()[InputTypesColumn.MATCH.ordinal()]
                                .equals(mod.getRowData()[InputTypesColumn.MATCH.ordinal()])
                            || !mod.getOriginalRowData()[InputTypesColumn.ITEMS.ordinal()]
                                   .equals(mod.getRowData()[InputTypesColumn.ITEMS.ordinal()]))
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
                                    fieldCommand.append("UPDATE ")
                                                .append(InternalTable.FIELDS.getTableName())
                                                .append(" SET ")
                                                .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                                .append(" = '' WHERE ")
                                                .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                .append(" = ")
                                                .append(delimitText(field[FieldsColumn.OWNER_NAME.ordinal()]))
                                                .append(" AND ")
                                                .append(FieldsColumn.FIELD_NAME.getColumnName())
                                                .append(" = ")
                                                .append(delimitText(field[FieldsColumn.FIELD_NAME.ordinal()]))
                                                .append("; ");

                                    // Check if the length of the command string has reached the
                                    // limit
                                    if (fieldCommand.length() >= ModifiableSizeInfo.MAX_SQL_COMMAND_LENGTH.getSize())
                                    {
                                        dbCommand.executeDbUpdate(fieldCommand, dialog);
                                        fieldCommand = new StringBuilder("");
                                    }
                                }
                            }
                        }
                    }

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
                                            true,
                                            true,
                                            false,
                                            dialog))
                        {
                            throw new SQLException("table modification error");
                        }
                    }

                    // Check if any input type name changes were made
                    if (tableTypeCommand.length() != 0 || fieldCommand.length() != 0)
                    {
                        // Execute the command to update the input type name in the internal tables
                        dbCommand.executeDbUpdate(tableTypeCommand.append(fieldCommand), dialog);
                    }

                    // Store the updated input types table
                    storeNonTableTypesInfoTable(InternalTable.INPUT_TYPES,
                                                       CcddUtilities.removeArrayListColumn(Arrays.asList(dialog.getUpdatedData()),
                                                                                           InputTypesColumn.ROW_NUM.ordinal()),
                                                       null,
                                                       dialog);

                    // Release the save point. This must be done within a transaction block, so it
                    // must be done prior to the commit below
                    dbCommand.releaseSavePoint(dialog);

                    // Commit the change(s) to the database
                    dbControl.getConnection().commit();

                    // Inform the user that the update succeeded
                    eventLog.logEvent(SUCCESS_MSG,
                                      new StringBuilder("Input types and all affected tables and fields updated"));
                }
                catch (SQLException se)
                {
                    // Inform the user that updating the macros failed
                    eventLog.logFailEvent(dialog, "Cannot update input types; cause '"
                                          + se.getMessage()
                                          + "'",
                                          "<html><b>Cannot update input types");
                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, dialog);
                    errorFlag = true;
                }

                // Check if no error occurred when making the update(s)
                if (!errorFlag)
                {
                    // Perform the input types command completion steps
                    dialog.doInputTypeUpdatesComplete(errorFlag, inputTypeNames);
                }
                // An  error occurred when making the update(s)
                else
                {
                    try
                    {
                        // Revert any changes made to the database
                        dbCommand.rollbackToSavePoint(ccddMain.getMainFrame());
                    }
                    catch (SQLException se)
                    {
                        // Inform the user that rolling back the changes failed
                        eventLog.logFailEvent(ccddMain.getMainFrame(),
                                              "Cannot revert changes to project; cause '"
                                              + se.getMessage()
                                              + "'",
                                              "<html><b>Cannot revert changes to project");
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Get an array containing all prototype tables that are of the specified table type (combining
     * all structure and all command tables)
     *
     * @param tableType TYPE_STRUCTURE to get all tables for any type that represents a structure,
     *                  TYPE_COMMAND to get all tables for any type that represents a command,
     *                  TYPE_OTHER to get all tables that are neither a structure or command table,
     *                  or the table type name to get all tables for the specified type
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

                case TYPE_ENUM:
                    isOfType = type.equals(tableType);
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
     * Get a list containing all tables that are of the specified table type. Rebuild the preloaded
     * table members list
     *
     * @param typeName        Table type name
     *
     * @param protoTableNames Names of the prototype tables of the specified table type; null to
     *                        load the list
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return List containing all tables that are of the specified table type. Returns an empty
     *         list if no table of the specified type exists in the project database
     *********************************************************************************************/
    protected List<String> getAllTablesOfType(String typeName,
                                              String[] protoTableNames,
                                              Component parent)
    {
        return getAllTablesOfType(typeName,
                                  protoTableNames,
                                  true,
                                  parent);
    }

    /**********************************************************************************************
     * Get a list containing all tables that are of the specified table type
     *
     * @param typeName        Table type name
     *
     * @param protoTableNames Names of the prototype tables of the specified table type; null to
     *                        load the list
     *
     *@param clearPreLoad     true to clear the preloaded table members
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return List containing all tables that are of the specified table type. Returns an empty
     *         list if no table of the specified type exists in the project database
     *********************************************************************************************/
    protected List<String> getAllTablesOfType(String typeName,
                                              String[] protoTableNames,
                                              boolean clearPreLoad,
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
            if (clearPreLoad)
            {
                // Clear the pre-loaded table members
                clearPreLoadedTableMembers();
            }

            // Build a table tree with all prototype and instance tables
            CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain, TableTreeType.TABLES, parent);

            // Step through each prototype table of the specified type
            for (String protoName : protoTableNames)
            {
                // Create a list of table path arrays for instances of this prototype table name
                tableNamesList.addAll(tableTree.getTableTreePathList(protoName));
            }
        }

        return tableNamesList;
    }

    /**********************************************************************************************
     * Update the table tree information, including the pre-loaded table member information
     *********************************************************************************************/
    private void updateTableTree()
    {
        // Update the pre-loaded table members list, and the group and type trees
        updatePreLoadedTableMembers();
        ccddMain.buildGroupTableTreeHandler();
        ccddMain.buildTypeTableTreeHandler();
    }
}
