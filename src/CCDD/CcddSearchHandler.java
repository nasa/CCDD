/**
 * CFS Command and Data Dictionary search database tables and scripts handler. Copyright 2017
 * United States Government as represented by the Administrator of the National Aeronautics and
 * Space Administration. No copyright is claimed in the United States under Title 17, U.S. Code.
 * All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;

import java.awt.Component;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JOptionPane;

import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventColumns;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.ScriptColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.SearchTarget;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary search database tables, scripts, and event log handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchHandler extends CcddDialogHandler
{
    // Class references
    private final CcddDbCommandHandler dbCommand;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddEventLogDialog eventLog;

    // Search dialog type
    private final SearchDialogType searchDlgType;

    // Temporary marker for special characters in a search string
    protected static final String WILD_CARD_MARKER = "@~wildcard~@";

    /**********************************************************************************************
     * Search database tables, scripts, and event log handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param searchDlgType
     *            search dialog type: TABLES, SCRIPTS, or LOG
     *
     * @param targetRow
     *            row index to match if this is an event log entry search on a table that displays
     *            only a single log entry; null otherwise
     *
     * @param eventLog
     *            event log to search; null if not searching a log
     *********************************************************************************************/
    CcddSearchHandler(CcddMain ccddMain,
                      SearchDialogType searchDlgType,
                      Long targetRow,
                      CcddEventLogDialog eventLog)
    {
        this.searchDlgType = searchDlgType;
        this.eventLog = eventLog;

        // Create references to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
    }

    /**********************************************************************************************
     * Search database tables and scripts class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param searchType
     *            search dialog type: TABLES or SCRIPTS
     *********************************************************************************************/
    CcddSearchHandler(CcddMain ccddMain, SearchDialogType searchType)
    {
        this(ccddMain, searchType, null, null);
    }

    /**********************************************************************************************
     * Set the reference to the event log to search
     *
     * @param eventLog
     *            reference to the event log to search
     *********************************************************************************************/
    protected void setEventLog(CcddEventLogDialog eventLog)
    {
        this.eventLog = eventLog;
    }

    /**********************************************************************************************
     * Search for occurrences of a string in the tables or scripts
     *
     * @param searchText
     *            text string to search for in the database
     *
     * @param ignoreCase
     *            true to ignore case when looking for matching text
     *
     * @param dataTablesOnly
     *            true if only the data tables, and not references in the internal tables, are to
     *            be searched
     *
     * @param searchColumns
     *            string containing the names of columns, separated by commas, to which to
     *            constrain a table search
     *
     * @return Search results list containing object arrays providing each match's location in the
     *         database tables or event log, the column within the location, and an extract for the
     *         located match showing its context
     *********************************************************************************************/
    protected List<Object[]> searchTablesOrScripts(String searchText,
                                                   boolean ignoreCase,
                                                   boolean dataTablesOnly,
                                                   String searchColumns)
    {
        // Initialize the list to contain the search results
        List<Object[]> resultsDataList = new ArrayList<Object[]>();

        // Set the search type based on the dialog type and, for a table search, the state of the
        // 'data tables only' check box
        String searchType = searchDlgType == SearchDialogType.TABLES
                                                                     ? (dataTablesOnly
                                                                                       ? SearchType.DATA.toString()
                                                                                       : SearchType.ALL.toString())
                                                                     : SearchType.SCRIPT.toString();

        // Search the database for the text
        String[] hits = dbCommand.getList(DatabaseListCommand.SEARCH,
                                          new String[][] {{"_search_text_",
                                                           searchText},
                                                          {"_case_insensitive_",
                                                           String.valueOf(ignoreCase)},
                                                          {"_allow_regex_",
                                                           "true"},
                                                          {"_selected_tables_",
                                                           searchType},
                                                          {"_columns_",
                                                           searchColumns}},
                                          CcddSearchHandler.this);

        // Step through each table/column containing the search text
        for (String hit : hits)
        {
            // Split the found item into table, column, description, and context
            String[] tblColDescAndCntxt = hit.split(TABLE_DESCRIPTION_SEPARATOR, 4);

            // Create a reference to the search result's column name to shorten comparisons below
            String hitColumnName = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];

            // Check that the column isn't the primary key or row index
            if (!hitColumnName.equals(DefaultColumn.PRIMARY_KEY.getDbName())
                && !hitColumnName.equals(DefaultColumn.ROW_INDEX.getDbName()))
            {
                // Create references to the the remaining search result columns to shorten
                // comparisons below
                String hitTableName = tblColDescAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()];
                String hitTableComment = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];
                String hitContext = tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()];

                // Separate the table comment into the viewable table name and table type, or for
                // scripts the script name and description
                String[] nameAndType = hitTableComment.split(",");

                // Split the row in which the match is found into its separate columns, accounting
                // for quotes around the comma separated column values (i.e., ignore commas within
                // quotes)
                String[] columnValue = CcddUtilities.splitAndRemoveQuotes(hitContext);

                String target = null;
                String location = null;
                String context = null;

                // Check if this is a table search
                if (searchDlgType == SearchDialogType.TABLES)
                {
                    // The reference is to a prototype table
                    if (!hitTableName.startsWith(INTERNAL_TABLE_PREFIX))
                    {
                        // Get the table's type definition based on its table type
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(nameAndType[1]);

                        // Get the index of the column where the match exists
                        int colIndex = typeDefn.getColumnIndexByDbName(hitColumnName);

                        // Set the row number for the row location if the variable name or command
                        // name aren't present
                        String row = "row "
                                     + columnValue[DefaultColumn.ROW_INDEX.ordinal()];

                        // Check if this is a structure table
                        if (typeDefn.isStructure())
                        {
                            // Get the variable name column index
                            int index = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);

                            // Check that a variable name exists
                            if (index != -1 && !columnValue[index].isEmpty())
                            {
                                // Set the row location to the variable name
                                row = "variable '" + columnValue[index] + "'";
                            }
                        }
                        // Check if this is a command table
                        else if (typeDefn.isCommand())
                        {
                            // Get the command name column index
                            int index = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);

                            // Check that a command name exists
                            if (index != -1 && !columnValue[index].isEmpty())
                            {
                                // Set the row location to the command name
                                row = "command '" + columnValue[index] + "'";
                            }
                        }

                        // Set the search result table values
                        target = SearchTarget.TABLE.getTargetName(true) + nameAndType[0];
                        location = "Column '"
                                   + typeDefn.getColumnNamesUser()[colIndex]
                                   + "', "
                                   + row;
                        context = columnValue[colIndex];
                    }
                    // Check if the match is in the custom values internal table
                    else if (hitTableName.equals(InternalTable.VALUES.getTableName()))
                    {
                        // Check if the match is in the value column
                        if (hitColumnName.equals(ValuesColumn.VALUE.getColumnName()))
                        {
                            // Get the column values from the row in which the match occurs
                            String tablePath = columnValue[ValuesColumn.TABLE_PATH.ordinal()];
                            String columnName = columnValue[ValuesColumn.COLUMN_NAME.ordinal()];
                            String value = columnValue[ValuesColumn.VALUE.ordinal()];

                            // Check if this is a table definition entry in the values table
                            if (columnName.isEmpty())
                            {
                                // Set the location
                                location = "Table description";
                            }
                            // Column value from a child table stored in the internal values table.
                            // Since this isn't a table description the reference must be to a
                            // structure table (for other table types the match would be in the
                            // table prototype)
                            else
                            {
                                // Set the location
                                location = "Column '" + columnName + "'";

                                // Initialize the variable name and get the index where the last
                                // variable name begins
                                int index = tablePath.lastIndexOf(',');

                                // Check if a variable name exists
                                if (index != -1)
                                {
                                    // Extract the variable from the path, then remove it from the
                                    // variable path
                                    location += ", variable '"
                                                + tablePath.substring(index + 1).replaceFirst("^.+\\.", "")
                                                + "'";
                                }
                            }

                            // Set the search result table values
                            target = CcddUtilities.highlightDataType(SearchTarget.TABLE.getTargetName(true)
                                                                     + ": "
                                                                     + tablePath);
                            context = value;
                        }
                    }
                    // Check if the match is in the data types internal table
                    else if (hitTableName.equals(InternalTable.DATA_TYPES.getTableName()))
                    {
                        target = SearchTarget.DATA_TYPE.getTargetName(true)
                                 + CcddDataTypeHandler.getDataTypeName(columnValue[DataTypesColumn.USER_NAME.ordinal()],
                                                                       columnValue[DataTypesColumn.C_NAME.ordinal()]);

                        // Check if the match is with the user-defined name
                        if (hitColumnName.equals(DataTypesColumn.USER_NAME.getColumnName()))
                        {
                            location = "User-defined name";
                            context = columnValue[DataTypesColumn.USER_NAME.ordinal()];
                        }
                        // Check if the match is with the C-language name
                        else if (hitColumnName.equals(DataTypesColumn.C_NAME.getColumnName()))
                        {
                            location = "C-language name";
                            context = columnValue[DataTypesColumn.C_NAME.ordinal()];
                        }
                        // Check if the match is with the data type size
                        else if (hitColumnName.equals(DataTypesColumn.SIZE.getColumnName()))
                        {
                            location = "Data type size";
                            context = columnValue[DataTypesColumn.SIZE.ordinal()];
                        }
                        // Check if the match is with the base type
                        else if (hitColumnName.equals(DataTypesColumn.BASE_TYPE.getColumnName()))
                        {
                            location += "Base data type";
                            context = columnValue[DataTypesColumn.BASE_TYPE.ordinal()];
                        }
                    }
                    // Check if the match is in the groups table
                    else if (hitTableName.equals(InternalTable.GROUPS.getTableName()))
                    {
                        target = SearchTarget.GROUP.getTargetName(true)
                                 + columnValue[GroupsColumn.GROUP_NAME.ordinal()];

                        // Check if the match is with the group name
                        if (hitColumnName.equals(GroupsColumn.GROUP_NAME.getColumnName()))
                        {
                            location = "Name";
                            context = columnValue[GroupsColumn.GROUP_NAME.ordinal()];
                        }
                        // The match is with a group definition or member
                        else
                        {
                            // Check if the column begins with a number; this is the group
                            // definition
                            if (columnValue[GroupsColumn.MEMBERS.ordinal()].matches("^\\d+"))
                            {
                                // Get the group description (remove the dummy number and comma
                                // that flags this as a group definition)
                                context = columnValue[GroupsColumn.MEMBERS.ordinal()].split(",")[1];

                                // Check if the description contains the search text (i.e., the
                                // dummy number and comma aren't part of the match)
                                if (context.toLowerCase().contains(searchText.toLowerCase()))
                                {
                                    location = "Description";
                                }
                                // The match includes the dummy number and comma; ignore
                                else
                                {
                                    target = null;
                                }
                            }
                            // This is a group member
                            else
                            {
                                location = "Member table";
                                context = CcddUtilities.highlightDataType(columnValue[GroupsColumn.MEMBERS.ordinal()]);
                            }
                        }
                    }
                    // Check if the match is in the fields internal table
                    else if (hitTableName.equals(InternalTable.FIELDS.getTableName()))
                    {
                        // Check if this is a project data field
                        if ((columnValue[FieldsColumn.OWNER_NAME.ordinal()] + ":").startsWith(CcddFieldHandler.getFieldProjectName()))
                        {
                            target = SearchTarget.PROJECT_FIELD.getTargetName(true)
                                     + columnValue[FieldsColumn.OWNER_NAME.ordinal()].replaceFirst("^.*:", "");
                        }
                        // Check if this is a default data field
                        else if ((columnValue[FieldsColumn.OWNER_NAME.ordinal()] + ":").startsWith(CcddFieldHandler.getFieldTypeName("")))
                        {
                            target = SearchTarget.DEFAULT_FIELD.getTargetName(true)
                                     + columnValue[FieldsColumn.OWNER_NAME.ordinal()].replaceFirst("^.*:", "");
                        }
                        // Check if this is a group data field
                        else if ((columnValue[FieldsColumn.OWNER_NAME.ordinal()] + ":").startsWith(CcddFieldHandler.getFieldGroupName("")))
                        {
                            target = SearchTarget.GROUP_FIELD.getTargetName(true)
                                     + columnValue[FieldsColumn.OWNER_NAME.ordinal()].replaceFirst("^.*:", "");
                        }
                        // This is a table data field
                        else
                        {
                            target = CcddUtilities.highlightDataType(SearchTarget.TABLE_FIELD.getTargetName(true)
                                                                     + columnValue[FieldsColumn.OWNER_NAME.ordinal()]);
                        }

                        location = "Field name '"
                                   + columnValue[FieldsColumn.FIELD_NAME.ordinal()]
                                   + "' ";

                        // Check if the match is with the field owner name
                        if (hitColumnName.equals(FieldsColumn.OWNER_NAME.getColumnName()))
                        {
                            location += "owner";
                            context = columnValue[FieldsColumn.OWNER_NAME.ordinal()];

                            // Check if this is a table data field
                            if (!context.startsWith(CcddFieldHandler.getFieldProjectName())
                                && !context.startsWith(CcddFieldHandler.getFieldTypeName(""))
                                && !context.startsWith(CcddFieldHandler.getFieldGroupName("")))
                            {
                                context = CcddUtilities.highlightDataType(context);
                            }
                        }
                        // Check if the match is with the field name
                        else if (hitColumnName.equals(FieldsColumn.FIELD_NAME.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[FieldsColumn.FIELD_NAME.ordinal()];
                        }
                        // Check if the match is with the field description
                        else if (hitColumnName.equals(FieldsColumn.FIELD_DESC.getColumnName()))
                        {
                            location += "description";
                            context = columnValue[FieldsColumn.FIELD_DESC.ordinal()];
                        }
                        // Check if the match is with the field size
                        else if (hitColumnName.equals(FieldsColumn.FIELD_SIZE.getColumnName()))
                        {
                            location += "size";
                            context = columnValue[FieldsColumn.FIELD_SIZE.ordinal()];
                        }
                        // Check if the match is with the field input type
                        else if (hitColumnName.equals(FieldsColumn.FIELD_TYPE.getColumnName()))
                        {
                            location += "input type";
                            context = columnValue[FieldsColumn.FIELD_TYPE.ordinal()];
                        }
                        // Check if the match is with the field applicability
                        else if (hitColumnName.equals(FieldsColumn.FIELD_APPLICABILITY.getColumnName()))
                        {
                            location += "applicability";
                            context = columnValue[FieldsColumn.FIELD_APPLICABILITY.ordinal()];
                        }
                        // Check if the match is with the field value
                        else if (hitColumnName.equals(FieldsColumn.FIELD_VALUE.getColumnName()))
                        {
                            location += "value";
                            context = columnValue[FieldsColumn.FIELD_VALUE.ordinal()];
                        }
                        // Check if the match is with the field required flag
                        else if (hitColumnName.equals(FieldsColumn.FIELD_REQUIRED.getColumnName()))
                        {
                            location += "required flag";
                            context = columnValue[FieldsColumn.FIELD_REQUIRED.ordinal()];
                        }
                    }
                    // Check if the match is in the associations internal table
                    else if (hitTableName.equals(InternalTable.ASSOCIATIONS.getTableName()))
                    {
                        target = SearchTarget.SCRIPT_ASSN.getTargetName(true)
                                 + columnValue[AssociationsColumn.SCRIPT_FILE.ordinal()];

                        // Check if the match is with the script file path and/or name
                        if (hitColumnName.equals(AssociationsColumn.SCRIPT_FILE.getColumnName()))
                        {
                            location = "File path and name";
                            context = columnValue[AssociationsColumn.SCRIPT_FILE.ordinal()];
                        }
                        // The match is with a script association member
                        else
                        {
                            location = "Member table";
                            context = columnValue[AssociationsColumn.MEMBERS.ordinal()];

                            // Check if this is a table data field
                            if (!context.startsWith(CcddFieldHandler.getFieldProjectName())
                                && !context.startsWith(CcddFieldHandler.getFieldTypeName(""))
                                && !context.startsWith(CcddFieldHandler.getFieldGroupName("")))
                            {
                                context = CcddUtilities.highlightDataType(context);
                            }
                        }
                    }
                    // Check if the match is in the telemetry scheduler internal table
                    else if (hitTableName.equals(InternalTable.TLM_SCHEDULER.getTableName()))
                    {
                        target = SearchTarget.TLM_MESSAGE.getTargetName(true)
                                 + columnValue[TlmSchedulerColumn.MESSAGE_NAME.ordinal()];

                        // Check if the match is with the message name
                        if (hitColumnName.equals(TlmSchedulerColumn.MESSAGE_NAME.getColumnName()))
                        {
                            location = "Name";
                            context = columnValue[TlmSchedulerColumn.MESSAGE_NAME.ordinal()];
                        }
                        // Check if the match is with the message rate name
                        else if (hitColumnName.equals(TlmSchedulerColumn.RATE_NAME.getColumnName()))
                        {
                            location = "Rate name";
                            context = columnValue[TlmSchedulerColumn.RATE_NAME.ordinal()];
                        }
                        // Check if the match is with the message ID
                        else if (hitColumnName.equals(TlmSchedulerColumn.MESSAGE_ID.getColumnName()))
                        {
                            location = "ID";
                            context = columnValue[TlmSchedulerColumn.MESSAGE_ID.ordinal()];
                        }
                        // The match is with a message definition or member
                        else
                        {
                            // Check if the column begins with a number; this is the message
                            // definition
                            if (columnValue[TlmSchedulerColumn.MEMBER.ordinal()].matches("^\\d+"))
                            {
                                location = "Rate and description";
                                context = columnValue[TlmSchedulerColumn.MEMBER.ordinal()];
                            }
                            // This is a message member
                            else
                            {
                                location = "Member rate, table, and variable";
                                context = CcddUtilities.highlightDataType(columnValue[TlmSchedulerColumn.MEMBER.ordinal()]);
                            }
                        }
                    }
                    // Check if the match is in the links internal table
                    else if (hitTableName.equals(InternalTable.LINKS.getTableName()))
                    {
                        target = SearchTarget.TLM_LINK.getTargetName(true)
                                 + columnValue[LinksColumn.LINK_NAME.ordinal()];

                        // Check if the match is with the link name
                        if (hitColumnName.equals(LinksColumn.LINK_NAME.getColumnName()))
                        {
                            location = "Name";
                            context = columnValue[LinksColumn.LINK_NAME.ordinal()];
                        }
                        // Check if the match is with the link rate name
                        else if (hitColumnName.equals(LinksColumn.RATE_NAME.getColumnName()))
                        {
                            location = "Rate name";
                            context = columnValue[LinksColumn.RATE_NAME.ordinal()];
                        }
                        // The match is with a link definition or member
                        else
                        {
                            // Check if the column begins with a number; this is the link
                            // definition
                            if (columnValue[1].matches("^\\d+"))
                            {
                                location = "Rate and description";
                                context = columnValue[LinksColumn.MEMBER.ordinal()];
                            }
                            // This is a link member
                            else
                            {
                                location = "Member table and variable";
                                context = CcddUtilities.highlightDataType(columnValue[LinksColumn.MEMBER.ordinal()]);
                            }
                        }
                    }
                    // Check if the match is in the table types internal table
                    else if (hitTableName.equals(InternalTable.TABLE_TYPES.getTableName()))
                    {
                        target = SearchTarget.TABLE_TYPE.getTargetName(true)
                                 + columnValue[TableTypesColumn.TYPE_NAME.ordinal()];

                        // Check if the match is with the column name
                        if (hitColumnName.equals(TableTypesColumn.COLUMN_NAME_VISIBLE.getColumnName()))
                        {
                            location = "Column name";
                            context = columnValue[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()];
                        }
                        // Check if the match is with the column description
                        else if (hitColumnName.equals(TableTypesColumn.COLUMN_DESCRIPTION.getColumnName()))
                        {
                            location = "Column description";
                            context = columnValue[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()];
                        }
                        // Check if the match is with the column input type
                        else if (hitColumnName.equals(TableTypesColumn.INPUT_TYPE.getColumnName()))
                        {
                            location = "Column input type";
                            context = columnValue[TableTypesColumn.INPUT_TYPE.ordinal()];
                        }
                        // Check if the match is with the column required flag
                        else if (hitColumnName.equals(TableTypesColumn.COLUMN_REQUIRED.getColumnName()))
                        {
                            location = "Column required flag";
                            context = columnValue[TableTypesColumn.COLUMN_REQUIRED.ordinal()];
                        }
                        // Check if the match is with the row value unique flag
                        else if (hitColumnName.equals(TableTypesColumn.ROW_VALUE_UNIQUE.getColumnName()))
                        {
                            location = "Row value unique flag";
                            context = columnValue[TableTypesColumn.ROW_VALUE_UNIQUE.ordinal()];
                        }
                        // Match is in one of the remaining table type columns
                        else
                        {
                            // Ignore this match
                            target = null;
                        }
                    }
                    // Check if the match is in the application scheduler internal table
                    else if (hitTableName.equals(InternalTable.APP_SCHEDULER.getTableName()))
                    {
                        target = SearchTarget.APP_SCHEDULER.getTargetName(true);
                        location = "Time slot '"
                                   + columnValue[AppSchedulerColumn.TIME_SLOT.ordinal()]
                                   + "' ";

                        // Check if the match is with the application name
                        if (hitColumnName.equals(AppSchedulerColumn.TIME_SLOT.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[AppSchedulerColumn.TIME_SLOT.ordinal()];
                        }
                        // The match is with a scheduler member
                        else
                        {
                            context = columnValue[AppSchedulerColumn.APP_INFO.ordinal()];
                            location += "member information";
                        }
                    }
                }
                // This is a script search and the match is in a stored script
                else
                {
                    // Set the search result table values
                    target = nameAndType[0];
                    location = columnValue[ScriptColumn.LINE_NUM.ordinal()];
                    context = columnValue[ScriptColumn.LINE_TEXT.ordinal()];
                }

                // Check if a search result exists
                if (target != null)
                {
                    // Add the search result to the list
                    resultsDataList.add(new Object[] {target, location, context});
                }
            }
        }

        // Display the search results
        return sortSearchResults(resultsDataList);
    }

    /**********************************************************************************************
     * Search for occurrences of a string in the event log file (session log or other log file)
     *
     * @param searchPattern
     *            regular expression search pattern
     *
     * @param targetRow
     *            row index to match if this is an event log entry search on a table that displays
     *            only a single log entry; null otherwise
     *
     * @return Search results list containing object arrays providing each match's location in the
     *         database tables or event log, the column within the location, and an extract for the
     *         located match showing its context
     *********************************************************************************************/
    protected List<Object[]> searchEventLogFile(Pattern searchPattern, Long targetRow)
    {
        // Initialize the list to contain the search results
        List<Object[]> matchDataList = new ArrayList<Object[]>();

        // Set up Charset and CharsetDecoder for ISO-8859-15
        Charset charset = Charset.forName("ISO-8859-15");
        CharsetDecoder decoder = charset.newDecoder();

        // Pattern used to detect separate lines
        Pattern linePattern = Pattern.compile(".*\r?\n");

        try
        {
            // Open a file stream on the event log file and then get a channel from the stream
            FileInputStream fis = new FileInputStream(eventLog.getEventLogFile());
            FileChannel fc = fis.getChannel();

            // Get the file's size and then map it into memory
            MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            // Decode the file into a char buffer
            CharBuffer charBuffer = decoder.decode(byteBuffer);

            // Create the line and pattern matchers, then perform the search
            Matcher lineMatch = linePattern.matcher(charBuffer);

            long row = 1;

            // For each line in the file
            while (lineMatch.find())
            {
                // Check if no target row is provided ,or if one is that it matches this log
                // entry's row
                if (targetRow == null || row == targetRow)
                {
                    // Get the line from the file and strip any leading or trailing white space
                    String line = lineMatch.group().toString();

                    // Break the input line into its separate columns
                    String[] parts = line.split("\\|", EventColumns.values().length - 1);

                    // Step through each log entry column
                    for (int column = 0; column < parts.length; column++)
                    {
                        // Create the pattern matcher from the pattern. Ignore any HTML tags in the
                        // log entry column text
                        Matcher matcher = searchPattern.matcher(CcddUtilities.removeHTMLTags(parts[column].toString()));

                        // Check if a match exists in the text string
                        if (matcher.find())
                        {
                            // Add the search result to the list
                            matchDataList.add(new Object[] {row,
                                                            eventLog.getEventTable().getColumnName(column + 1),
                                                            parts[column]});
                        }
                    }

                    // Check if the end of the file has been reached or if this is a single log
                    // entry row search
                    if (lineMatch.end() == charBuffer.limit()
                        || targetRow != null)
                    {
                        // Exit the loop
                        break;
                    }
                }

                row++;
            }

            // Close the channel and the stream
            fc.close();
            fis.close();
        }
        catch (IOException ioe)
        {
            // Inform the user that an error occurred reading the log
            new CcddDialogHandler().showMessageDialog(this,
                                                      "<html><b>Cannot read event log file",
                                                      "Log Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        // Display the search results
        return sortSearchResults(matchDataList);
    }

    /**********************************************************************************************
     * Sort the search results by the first (target) column, and if the same then by second
     * (location) column. Array variable member references in the location column are arranged by
     * array dimension value
     *
     * @param resultsDataList
     *            list containing the sorted search results
     *
     * @return Search results sorted by the first (target) column, and if the same then by second
     *         (location) column. Array variable member references in the location column are
     *         arranged by array dimension value
     *********************************************************************************************/
    private List<Object[]> sortSearchResults(List<Object[]> resultsDataList)
    {
        // Sort the results by target, then by location, ignoring case
        Collections.sort(resultsDataList, new Comparator<Object[]>()
        {
            /**************************************************************************************
             * Compare the target names of two search result rows. If the same compare the
             * locations. Move the tables to the top. Ignore case when comparing
             *************************************************************************************/
            @Override
            public int compare(Object[] entry1, Object[] entry2)
            {
                int result = 0;

                switch (searchDlgType)
                {
                    case TABLES:
                    case SCRIPTS:
                        // Compare the first column as strings, ignoring case
                        result = entry1[0].toString().compareToIgnoreCase(entry2[0].toString());
                        break;

                    case LOG:
                        // Compare the first column as integers
                        result = Long.valueOf(entry1[0].toString()).compareTo(Long.valueOf(entry2[0].toString()));
                        break;
                }

                // Check if the first column values are the same
                if (result == 0)
                {
                    // Check if the second column values are both references to array variable
                    // members. The compareTo() method sorts the array members alphabetically, not
                    // numerically by array dimension (for example, a[10] will be placed
                    // immediately after a[1]). The following code sorts the array members
                    // numerically by array dimension
                    if (entry1[1].toString().matches("Column '.*', variable '.*\\]'")
                        && entry2[1].toString().matches("Column '.*', variable '.*\\]'"))
                    {
                        // Get the array variable references from the second column values
                        String arrayVariable1 = entry1[1].toString()
                                                         .replaceFirst("Column '.*', variable '(.*\\])'", "$1");
                        String arrayVariable2 = entry2[1].toString()
                                                         .replaceFirst("Column '.*', variable '(.*\\])'", "$1");

                        // Check if the variables are members of the same array
                        if (ArrayVariable.removeArrayIndex(arrayVariable1)
                                         .equals(ArrayVariable.removeArrayIndex(arrayVariable2)))
                        {
                            // Compare the two array members by dimension value(s)
                            result = ArrayVariable.compareTo(arrayVariable1, arrayVariable2);
                        }
                        // The second column values are not references to the same array variable
                        else
                        {
                            // Compare the second column, ignoring case
                            result = entry1[1].toString().compareToIgnoreCase(entry2[1].toString());
                        }
                    }
                    // The second column values are not both references to array variable members
                    else
                    {
                        // Compare the second column, ignoring case
                        result = entry1[1].toString().compareToIgnoreCase(entry2[1].toString());
                    }
                }

                return result;
            }
        });

        return resultsDataList;
    }

    /**********************************************************************************************
     * Remove data type or macro search references where the match occurs in an array size column
     * of an array member (the reference in the array's definition is all that's needed)
     *
     * @param matches
     *            list containing the search results for the data type or macro reference
     *
     * @param tblTypeHndlr
     *            reference to the table type handler
     *********************************************************************************************/
    protected static void removeArrayMemberReferences(List<String> matches,
                                                      CcddTableTypeHandler tblTypeHndlr)
    {
        // Step through each match (in reverse since an entry in the list may need to be removed)
        for (int index = matches.size() - 1; index >= 0; index--)
        {
            // Separate the match components
            String[] tblColDescAndCntxt = matches.get(index).split(TABLE_DESCRIPTION_SEPARATOR, 4);

            // Check if the comment portion isn't empty. The comment contains the table's visible
            // name and type for a data table, but is empty for a custom values table reference
            if (!tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].isEmpty())
            {
                // Separate the user-viewable table name and table type
                String[] tableAndType = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].split(",", 2);

                // Get the table's type definition
                TypeDefinition typeDefn = tblTypeHndlr.getTypeDefinition(tableAndType[1]);

                // Check if the reference is in an array size column
                if (typeDefn.getDbColumnNameByInputType(DefaultInputType.ARRAY_INDEX).equals(tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()]))
                {
                    // Separate the location into the individual columns. Commas between double
                    // quotes are ignored so that an erroneous column separation doesn't occur
                    String[] columns = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                    // Get the index of the variable name column
                    int varNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);

                    // Check if the variable name is an array member
                    if (varNameIndex != -1 && ArrayVariable.isArrayMember(columns[varNameIndex]))
                    {
                        // Remove the match
                        matches.remove(index);
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Create the match pattern from the search criteria. If the allow regular flag is set then the
     * search string is used as is. If the allow regular expression flag isn't set then a wild card
     * match is enabled. A custom matching system is used: a question mark matches a single
     * character and an asterisk matches one or more characters. This is turned into a regular
     * expression to perform the actual match
     *
     * Get the regular expression search pattern based on the input criteria
     *
     * @param searchText
     *            reference to the table cell renderer component
     *
     * @param ignoreCase
     *            true if the search is case insensitive and the search pattern is not used by the
     *            PostgreSQL search_atables() function (must be false for this case; the case
     *            insensitive flag is provided directly to the function instead of being included
     *            in the matching string)
     *
     * @param allowRegEx
     *            true if the search text can contain a regular expression
     *
     * @param parent
     *            GUI component over which to center any error dialog; null to not display an error
     *            dialog if the search pattern is invalid
     *
     * @return Compiled regular expression search pattern; null if the regular expression is
     *         invalid
     *********************************************************************************************/
    protected static Pattern createSearchPattern(String searchText,
                                                 boolean ignoreCase,
                                                 boolean allowRegEx,
                                                 Component parent)
    {
        Pattern searchPattern = null;

        try
        {
            // Create the match pattern from the search criteria. First the reserved regular
            // expression characters are escaped, other than the asterisk and question mark; these
            // are then replaced with their corresponding regular expression (while protecting any
            // escaped instances of the asterisks and question marks by temporarily replacing these
            // with a marker)
            searchPattern = Pattern.compile("(?"
                                            + (ignoreCase
                                                          ? "i"
                                                          : "")
                                            + ":"
                                            + (allowRegEx
                                                          ? searchText.replaceAll("\\\\", "\\\\\\\\\\\\")
                                                          : searchText.replaceAll("([\\[\\]\\(\\)\\{\\}\\.\\+\\^\\$\\|\\-\\\\])",
                                                                                  "\\\\\\\\\\\\$1")
                                                                      .replaceAll("\\\\\\?", WILD_CARD_MARKER)
                                                                      .replaceAll("\\?", ".")
                                                                      .replaceAll(WILD_CARD_MARKER, "\\\\?")
                                                                      .replaceAll("\\\\\\*", WILD_CARD_MARKER)
                                                                      .replaceAll("\\*", ".*?")
                                                                      .replaceAll(WILD_CARD_MARKER, "\\\\*"))
                                            + ")");
        }
        catch (PatternSyntaxException pse)
        {
            // Check if an error dialog should be displayed
            if (parent != null)
            {
                // Inform the user that the regular expression is invalid
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Invalid regular expression; cause '</b>"
                                                                  + pse.getMessage()
                                                                  + "'<b>",
                                                          "Invalid Input",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, parent);
        }

        return searchPattern;
    }
}
