/**
 * CFS Command and Data Dictionary CSV handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary CSV handler class
 *************************************************************************************************/
public class CcddCSVHandler extends CcddImportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddMacroHandler macroHandler;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddGroupHandler groupHandler;
    private final CcddScriptHandler scriptHandler;

    // GUI component over which to center any error dialog
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // List of original and new script associations
    private List<String[]> associations;

    /**********************************************************************************************
     * CSV data type tags
     *********************************************************************************************/
    private enum CSVTags
    {
        COLUMN_DATA("_column_data_", null),
        CELL_DATA("_table_cell_data_", null), // This is an internal tag and not used in the file
        NAME_TYPE("_name_type_", null),
        DESCRIPTION("_description_", null),
        DATA_FIELD("_data_field_", "_data_fields_"),
        MACRO("_macro_", "_macros_"),
        TABLE_TYPE("_table_type_", null),
        TABLE_TYPE_DATA_FIELD("_table_type_data_field_", "_table_type_data_fields_"),
        DATA_TYPE("_data_type_", "_data_types_"),
        INPUT_TYPE("_input_type_", "_input_types_"),
        RESERVED_MSG_IDS("_reserved_msg_id_", "_reserved_msg_ids_"),
        PROJECT_DATA_FIELD("_project_data_field_", "_project_data_fields_"),
        VARIABLE_PATHS("_variable_path_", "_variable_paths_"),
        GROUP("_group_", null),
        GROUP_DATA_FIELD("_group_data_field_", "_group_data_fields_"),
        SCRIPT_ASSOCIATION("_script_association_", null);

        private final String tag;
        private final String alternateTag;

        /******************************************************************************************
         * CSV data type tags constructor
         *
         * @param tag
         *            text describing the data
         *
         * @param alternateTag
         *            alternate text describing the data; null if there is no alternate name. This
         *            allows the same tag data type to have two names, which is used for backwards
         *            compatibility, due to a previous mixture of singular and plural tag names
         *****************************************************************************************/
        CSVTags(String tag, String alternateTag)
        {
            this.tag = tag;
            this.alternateTag = alternateTag;
        }

        /******************************************************************************************
         * Get the data type tag
         *
         * @return Text describing the data
         *****************************************************************************************/
        protected String getTag()
        {
            return tag;
        }

        /******************************************************************************************
         * Check if the supplied text matches the data type tag name or alternate name
         *
         * @param text
         *            text describing the data
         *
         * @return true if the supplied text matches the tag name or alternate tag name, if one
         *         exists (case insensitive)
         *****************************************************************************************/
        protected boolean isTag(String text)
        {
            return tag.equalsIgnoreCase(text)
                   || (alternateTag != null && alternateTag.equalsIgnoreCase(text));
        }
    }

    /**********************************************************************************************
     * CSV handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddCSVHandler(CcddMain ccddMain, CcddGroupHandler groupHandler, Component parent)
    {
        this.parent = parent;

        // Create references to shorten subsequent calls
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        scriptHandler = ccddMain.getScriptHandler();
        this.groupHandler = groupHandler;

        tableDefinitions = null;
        associations = null;
    }

    /**********************************************************************************************
     * Get the imported table definitions
     *
     * @return List of imported table definitions; an empty list if no table definitions exist in
     *         the import file
     *********************************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
    }

    /**********************************************************************************************
     * Get the list of original and new script associations
     *
     * @return List of original and new script associations; null if no new associations have been
     *         added
     *********************************************************************************************/
    @Override
    public List<String[]> getScriptAssociations()
    {
        return associations;
    }

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile
     *            import file reference
     *
     * @param importType
     *            ImportType.IMPORT_ALL to import the table type, data type, and macro definitions,
     *            and the data from all the table definitions; ImportType.FIRST_DATA_ONLY to load
     *            only the data for the first table defined
     *
     * @param targetTypeDefn
     *            table type definition of the table in which to import the data; ignored if
     *            importing all tables
     *
     * @param ignoreErrors
     *            true to ignore all errors in the import file
     *
     * @param replaceExistingMacros
     *            true to replace the values for existing macros
     *
     * @param replaceExistingGroups
     *            true to replace existing group definitions
     *
     * @throws CCDDException
     *             If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException
     *             If an import file I/O error occurs
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile,
                               ImportType importType,
                               TypeDefinition targetTypeDefn,
                               boolean ignoreErrors,
                               boolean replaceExistingMacros,
                               boolean replaceExistingGroups) throws CCDDException,
                                                              IOException,
                                                              Exception
    {
        BufferedReader br = null;

        try
        {
            // Flags indicating if importing should continue after an input error is detected
            boolean continueOnTableTypeError = ignoreErrors;
            boolean continueOnTableTypeFieldError = ignoreErrors;
            boolean continueOnDataTypeError = ignoreErrors;
            boolean continueOnInputTypeError = ignoreErrors;
            boolean continueOnMacroError = ignoreErrors;
            boolean continueOnColumnError = ignoreErrors;
            boolean continueOnDataFieldError = ignoreErrors;
            boolean continueOnReservedMsgIDError = ignoreErrors;
            boolean continueOnProjectFieldError = ignoreErrors;
            boolean continueOnGroupError = ignoreErrors;
            boolean continueOnGroupFieldError = ignoreErrors;
            boolean continueOnAssociationError = ignoreErrors;

            ProjectDefinition projectDefn = new ProjectDefinition();
            List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>();
            List<String[]> dataTypeDefns = new ArrayList<String[]>();
            List<String[]> inputTypeDefns = new ArrayList<String[]>();
            List<String[]> macroDefns = new ArrayList<String[]>();
            List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();
            tableDefinitions = new ArrayList<TableDefinition>();

            // Make three passes through the file, first to get the input types (which must be
            // processed prior to adding a table type), second to get the table types, input types,
            // data types, and macros, and then a third pass to read the table data and fields
            for (int loop = 1; loop <= 3; loop++)
            {
                int columnNumber = 0;
                String groupDefnName = null;

                // Create a buffered reader to read the file
                br = new BufferedReader(new FileReader(importFile));

                // Initialize the import tag
                CSVTags importTag = null;

                // Read first line in file
                String line = br.readLine();

                // Continue to read the file until EOF is reached or an error is detected. This
                // outer while loop accounts for multiple table definitions within a single file
                while (line != null)
                {
                    TableTypeDefinition tableTypeDefn = null;

                    // Initialize the table information
                    int numColumns = 0;
                    String tablePath = "";

                    // Create empty table information and table type definition references
                    TypeDefinition typeDefn = null;

                    // Storage for column indices
                    int[] columnIndex = null;

                    // Initialize the number of matching columns and the cell data storage
                    String[] columnValues = null;

                    // Create a table definition to contain the table's information
                    TableDefinition tableDefn = new TableDefinition();

                    // Flag that indicates if a table type row is the type name and description or
                    // a column definition
                    boolean isTypeName = false;

                    // Continue to read the file until EOF is reached or an error is detected. This
                    // inner while loop reads the information for a single table in the file
                    while (line != null)
                    {
                        // Remove any leading/trailing white space characters from the row
                        String trimmedLine = line.trim();

                        // Check that the row isn't empty and isn't a comment line (starts with a #
                        // character)
                        if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#"))
                        {
                            boolean isTag = false;
                            boolean isNextTable = false;

                            // Check if the line contains an odd number of double quotes
                            if (trimmedLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                            {
                                String nextLine = null;

                                // Step through the subsequent lines in order to find the end of
                                // multi-line value
                                while ((nextLine = br.readLine()) != null)
                                {
                                    // Append the line to the preceding one, inserting the line
                                    // feed. The non-trimmed variable is used so that trailing
                                    // spaces within a quoted, multiple-line field aren't lost
                                    line += "\n" + nextLine;

                                    // Check if this is the line that ends the multi-line value
                                    // (i.e., it ends with one double quote)
                                    if (nextLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                                    {
                                        // Stop searching; the multi-line string
                                        // has been
                                        // concatenated to the initial line
                                        break;
                                    }
                                }

                                // Remove any leading/trailing white space characters from the
                                // combined multiple line row. This only removed white space
                                // outside the quotes that bound the text
                                trimmedLine = line.trim();
                            }

                            // Remove any trailing commas and empty quotes from the row. If the CSV
                            // file is generated from a spreadsheet application then extra commas
                            // are appended to a row if needed for the number of columns to be
                            // equal with the other rows. These empty trailing columns are ignored
                            line = trimmedLine.replaceAll("(?:[,\\s*]|\"\\s*\",|,\"\\s*\")*$", "");

                            // Parse the import data. The values are comma- separated; however,
                            // commas within quotes are ignored - this allows commas to be included
                            // in the data values
                            columnValues = CcddUtilities.splitAndRemoveQuotes(line);

                            // Remove any leading/trailing white space characters from the first
                            // column value
                            String firstColumn = columnValues[0].trim();

                            // Step through the import tags
                            for (CSVTags csvTag : CSVTags.values())
                            {
                                // Check if the first column value matches the tag name
                                if (csvTag.isTag(firstColumn))
                                {
                                    isTag = true;

                                    // Set the import tag and stop searching
                                    importTag = csvTag;

                                    // Check if this is the table name and table type tag
                                    if (CSVTags.NAME_TYPE.isTag(firstColumn))
                                    {
                                        // Check if this is the third pass and if the name and type
                                        // are already set; if so, this is the beginning of another
                                        // table's information
                                        if (loop == 3 && !tablePath.isEmpty())
                                        {
                                            // Set the flag to indicate that this is the beginning
                                            // of the next table definition
                                            isNextTable = true;
                                        }
                                    }
                                    // Check if this is the table type tag
                                    else if (CSVTags.TABLE_TYPE.isTag(firstColumn))
                                    {
                                        // Set the flag so that the next row is treated as the
                                        // table
                                        // type name and description
                                        isTypeName = true;
                                    }

                                    break;
                                }
                            }

                            // Check if this is the beginning of the next table definition
                            if (isNextTable)
                            {
                                // Stop processing the file in order to create the table
                                // prior to beginning another one
                                break;
                            }

                            // Not a tag (or no table name and type are defined); read in the
                            // information based on the last tag read
                            if (!isTag)
                            {
                                // Check if this is the first pass
                                if (loop == 1)
                                {
                                    switch (importTag)
                                    {
                                        case INPUT_TYPE:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == InputTypesColumn.values().length - 1)
                                                {
                                                    // Check if the input type definition is valid
                                                    String[] inputTypeDefn = checkInputTypeDefinition(new String[] {columnValues[InputTypesColumn.NAME.ordinal()],
                                                                                                                    columnValues[InputTypesColumn.DESCRIPTION.ordinal()],
                                                                                                                    columnValues[InputTypesColumn.MATCH.ordinal()],
                                                                                                                    columnValues[InputTypesColumn.ITEMS.ordinal()],
                                                                                                                    columnValues[InputTypesColumn.FORMAT.ordinal()],
                                                                                                                    ""});

                                                    // Add the input type definition (add a blank
                                                    // to represent the OID)
                                                    inputTypeDefns.add(inputTypeDefn);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnInputTypeError = getErrorResponse(continueOnInputTypeError,
                                                                                                "<html><b>Missing or extra input type definition "
                                                                                                                          + "input(s) in import file '</b>"
                                                                                                                          + importFile.getAbsolutePath()
                                                                                                                          + "<b>'; continue?",
                                                                                                "Input Type Error",
                                                                                                "Ignore this input type",
                                                                                                "Ignore this and any remaining invalid input types",
                                                                                                "Stop importing",
                                                                                                parent);
                                                }
                                            }

                                            break;

                                        case CELL_DATA:
                                        case COLUMN_DATA:
                                        case DATA_FIELD:
                                        case DATA_TYPE:
                                        case DESCRIPTION:
                                        case MACRO:
                                        case NAME_TYPE:
                                        case PROJECT_DATA_FIELD:
                                        case RESERVED_MSG_IDS:
                                        case TABLE_TYPE:
                                        case TABLE_TYPE_DATA_FIELD:
                                        case GROUP:
                                        case GROUP_DATA_FIELD:
                                        case VARIABLE_PATHS:
                                        case SCRIPT_ASSOCIATION:
                                            break;
                                    }
                                }
                                // Check if this is the second pass
                                else if (loop == 2)
                                {
                                    switch (importTag)
                                    {
                                        case TABLE_TYPE:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if this is the table type name and
                                                // description
                                                if (isTypeName)
                                                {
                                                    // Reset the flag so that subsequent rows are
                                                    // treated as column definitions
                                                    isTypeName = false;
                                                    columnNumber = NUM_HIDDEN_COLUMNS;

                                                    // Check if the expected number of inputs is
                                                    // present
                                                    if (columnValues.length == 2
                                                        || columnValues.length == 1)
                                                    {
                                                        // Add the table type definition
                                                        tableTypeDefn = new TableTypeDefinition(columnValues[0],
                                                                                                (columnValues.length == 2
                                                                                                                          ? columnValues[1]
                                                                                                                          : ""));
                                                        tableTypeDefns.add(tableTypeDefn);
                                                    }
                                                    // The number of inputs is incorrect
                                                    else
                                                    {
                                                        // Check if the error should be ignored or
                                                        // the import canceled
                                                        continueOnTableTypeError = getErrorResponse(continueOnTableTypeError,
                                                                                                    "<html><b>Missing table type name in import file '</b>"
                                                                                                                              + importFile.getAbsolutePath()
                                                                                                                              + "<b>'; continue?",
                                                                                                    "Table Type Error",
                                                                                                    "Ignore this table type",
                                                                                                    "Ignore this and any remaining invalid table types",
                                                                                                    "Stop importing",
                                                                                                    parent);
                                                    }
                                                }
                                                // This is a column definition
                                                else
                                                {
                                                    // Check if the expected number of inputs is
                                                    // present
                                                    if (columnValues.length == TableTypeEditorColumnInfo.values().length - 1)
                                                    {
                                                        // Add the table type column definition,
                                                        // checking for (and if possible,
                                                        // correcting) errors
                                                        continueOnTableTypeError = addImportedTableTypeColumnDefinition(continueOnTableTypeError,
                                                                                                                        tableTypeDefn,
                                                                                                                        new String[] {String.valueOf(columnNumber),
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.NAME.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.DESCRIPTION.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.UNIQUE.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.REQUIRED.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()
                                                                                                                                                   - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()
                                                                                                                                                   - 1]},
                                                                                                                        importFile.getAbsolutePath(),
                                                                                                                        inputTypeHandler,
                                                                                                                        parent);

                                                        // Update the column index number for the
                                                        // next column definition
                                                        columnNumber++;
                                                    }
                                                    // The number of inputs is incorrect
                                                    else
                                                    {
                                                        // Check if the error should be ignored or
                                                        // the import canceled
                                                        continueOnTableTypeError = getErrorResponse(continueOnTableTypeError,
                                                                                                    "<html><b>Table type '</b>"
                                                                                                                              + tableTypeDefn.getTypeName()
                                                                                                                              + "<b>' definition has missing or extra "
                                                                                                                              + "input(s) in import file '</b>"
                                                                                                                              + importFile.getAbsolutePath()
                                                                                                                              + "<b>'; continue?",
                                                                                                    "Table Type Error",
                                                                                                    "Ignore this table type",
                                                                                                    "Ignore this and any remaining invalid table types",
                                                                                                    "Stop importing",
                                                                                                    parent);
                                                    }
                                                }
                                            }

                                            break;

                                        case TABLE_TYPE_DATA_FIELD:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Append empty columns as needed to fill out the
                                                // expected number of inputs
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                FieldsColumn.values().length
                                                                                                              - 1
                                                                                                              - columnValues.length);

                                                // Add the data field definition, checking for (and
                                                // if possible, correcting) errors
                                                continueOnTableTypeFieldError = addImportedDataFieldDefinition(continueOnTableTypeFieldError,
                                                                                                               tableTypeDefn,
                                                                                                               new String[] {CcddFieldHandler.getFieldTypeName(tableTypeDefn.getTypeName()),
                                                                                                                             columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                                                                                             columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1]},
                                                                                                               importFile.getAbsolutePath(),
                                                                                                               inputTypeHandler,
                                                                                                               fieldHandler,
                                                                                                               parent);
                                            }

                                            break;

                                        case DATA_TYPE:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == DataTypesColumn.values().length - 1)
                                                {
                                                    // Build the data type definition
                                                    String[] dataTypeDefn = new String[] {columnValues[DataTypesColumn.USER_NAME.ordinal()],
                                                                                          columnValues[DataTypesColumn.C_NAME.ordinal()],
                                                                                          columnValues[DataTypesColumn.SIZE.ordinal()],
                                                                                          columnValues[DataTypesColumn.BASE_TYPE.ordinal()],
                                                                                          ""};

                                                    // Check if the data type definition is valid
                                                    checkDataTypeDefinition(dataTypeDefn);

                                                    // Add the data type definition (add a blank to
                                                    // represent the OID)
                                                    dataTypeDefns.add(dataTypeDefn);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnDataTypeError = getErrorResponse(continueOnDataTypeError,
                                                                                               "<html><b>Missing or extra data type definition "
                                                                                                                        + "input(s) in import file '</b>"
                                                                                                                        + importFile.getAbsolutePath()
                                                                                                                        + "<b>'; continue?",
                                                                                               "Data Type Error",
                                                                                               "Ignore this data type",
                                                                                               "Ignore this and any remaining invalid data types",
                                                                                               "Stop importing",
                                                                                               parent);
                                                }
                                            }

                                            break;

                                        case MACRO:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == 2
                                                    || columnValues.length == 1)
                                                {
                                                    // Build the macro definition
                                                    String[] macroDefn = new String[] {columnValues[0],
                                                                                       (columnValues.length == 2
                                                                                                                 ? columnValues[1]
                                                                                                                 : ""),
                                                                                       ""};

                                                    // Check if the macro definition is valid
                                                    checkMacroDefinition(macroDefn);

                                                    // Add the macro definition (add a blank to
                                                    // represent the OID)
                                                    macroDefns.add(macroDefn);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnMacroError = getErrorResponse(continueOnMacroError,
                                                                                            "<html><b>Missing or extra macro definition "
                                                                                                                  + "input(s) in import file '</b>"
                                                                                                                  + importFile.getAbsolutePath()
                                                                                                                  + "<b>'; continue?",
                                                                                            "Macro Error",
                                                                                            "Ignore this macro",
                                                                                            "Ignore this and any remaining invalid macros",
                                                                                            "Stop importing",
                                                                                            parent);
                                                }
                                            }

                                            break;

                                        case RESERVED_MSG_IDS:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == 2
                                                    || columnValues.length == 1)
                                                {
                                                    // Append empty columns as needed to fill out
                                                    // the expected number of inputs
                                                    columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                    2 - columnValues.length);

                                                    // Add the reserved message ID definition (add
                                                    // a blank to represent the OID)
                                                    reservedMsgIDDefns.add(new String[] {columnValues[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                                                         columnValues[ReservedMsgIDsColumn.DESCRIPTION.ordinal()],
                                                                                         ""});
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnReservedMsgIDError = getErrorResponse(continueOnReservedMsgIDError,
                                                                                                    "<html><b>Missing or extra reserved message ID "
                                                                                                                                  + "definition input(s) in import file '</b>"
                                                                                                                                  + importFile.getAbsolutePath()
                                                                                                                                  + "<b>'; continue?",
                                                                                                    "Reserved Message ID Error",
                                                                                                    "Ignore this data type",
                                                                                                    "Ignore this and any remaining invalid reserved message IDs",
                                                                                                    "Stop importing",
                                                                                                    parent);
                                                }
                                            }

                                            break;

                                        case PROJECT_DATA_FIELD:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Append empty columns as needed to fill out the
                                                // expected number of inputs
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                FieldsColumn.values().length
                                                                                                              - 1
                                                                                                              - columnValues.length);

                                                // Add the data field definition, checking for (and
                                                // if possible, correcting) errors
                                                continueOnProjectFieldError = addImportedDataFieldDefinition(continueOnProjectFieldError,
                                                                                                             projectDefn,
                                                                                                             new String[] {CcddFieldHandler.getFieldProjectName(),
                                                                                                                           columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                                                                                           columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1]},
                                                                                                             importFile.getAbsolutePath(),
                                                                                                             inputTypeHandler,
                                                                                                             fieldHandler,
                                                                                                             parent);
                                            }

                                            break;

                                        case GROUP:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == GroupDefinitionColumn.values().length
                                                    || columnValues.length == GroupDefinitionColumn.values().length - 1)
                                                {
                                                    // Append empty columns as needed to fill out
                                                    // the expected number of inputs
                                                    columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                    GroupDefinitionColumn.values().length
                                                                                                                  - columnValues.length);

                                                    // Store the group name
                                                    groupDefnName = columnValues[GroupDefinitionColumn.NAME.ordinal()];

                                                    // Add the group definition, checking for (and
                                                    // if possible, correcting) errors
                                                    addImportedGroupDefinition(new String[] {groupDefnName,
                                                                                             columnValues[GroupDefinitionColumn.DESCRIPTION.ordinal()],
                                                                                             columnValues[GroupDefinitionColumn.IS_APPLICATION.ordinal()],
                                                                                             columnValues[GroupDefinitionColumn.MEMBERS.ordinal()]},
                                                                               importFile.getAbsolutePath(),
                                                                               replaceExistingGroups,
                                                                               groupHandler);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnGroupError = getErrorResponse(continueOnGroupError,
                                                                                            "<html><b>Group definition has missing "
                                                                                                                  + "or extra input(s) in import file '</b>"
                                                                                                                  + importFile.getAbsolutePath()
                                                                                                                  + "<b>'; continue?",
                                                                                            "Group Error",
                                                                                            "Ignore this invalid group",
                                                                                            "Ignore this and any remaining invalid group definitions",
                                                                                            "Stop importing",
                                                                                            parent);
                                                }
                                            }

                                            break;

                                        case GROUP_DATA_FIELD:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Append empty columns as needed to fill out the
                                                // expected number of inputs
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                FieldsColumn.values().length
                                                                                                              - 1
                                                                                                              - columnValues.length);

                                                // Add the data field definition, checking for (and
                                                // if possible, correcting) errors
                                                continueOnGroupFieldError = addImportedDataFieldDefinition(continueOnGroupFieldError,
                                                                                                           projectDefn,
                                                                                                           new String[] {CcddFieldHandler.getFieldGroupName(groupDefnName),
                                                                                                                         columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                                                                                         columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1]},
                                                                                                           importFile.getAbsolutePath(),
                                                                                                           inputTypeHandler,
                                                                                                           fieldHandler,
                                                                                                           parent);
                                            }

                                            break;

                                        case SCRIPT_ASSOCIATION:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == AssociationsColumn.values().length)
                                                {
                                                    // Check if the associations haven't been
                                                    // loaded
                                                    if (associations == null)
                                                    {
                                                        // Get the script associations from the
                                                        // database
                                                        associations = scriptHandler.getScriptAssociations(parent);
                                                    }

                                                    // Add the script association, checking for
                                                    // errors
                                                    continueOnAssociationError = addImportedScriptAssociation(continueOnAssociationError,
                                                                                                              associations,
                                                                                                              new String[] {columnValues[AssociationsColumn.NAME.ordinal()],
                                                                                                                            columnValues[AssociationsColumn.DESCRIPTION.ordinal()],
                                                                                                                            columnValues[AssociationsColumn.SCRIPT_FILE.ordinal()],
                                                                                                                            CcddScriptHandler.convertAssociationMembersFormat(columnValues[AssociationsColumn.MEMBERS.ordinal()],
                                                                                                                                                                              true)},
                                                                                                              importFile.getAbsolutePath(),
                                                                                                              scriptHandler,
                                                                                                              parent);
                                                }
                                            }

                                            break;

                                        case INPUT_TYPE:
                                        case CELL_DATA:
                                        case COLUMN_DATA:
                                        case DATA_FIELD:
                                        case DESCRIPTION:
                                        case NAME_TYPE:
                                            break;

                                        default:
                                            // Inform the user that no tag appears in the file
                                            // before other data
                                            throw new CCDDException("Tag information missing");
                                    }
                                }
                                // This is the third pass
                                else
                                {
                                    switch (importTag)
                                    {
                                        case NAME_TYPE:
                                            // Check if the expected number of inputs is present
                                            // (the third value, the system name, is optional and
                                            // not used)
                                            if (columnValues.length == 2 || columnValues.length == 3)
                                            {
                                                // Get the table's type definition. If importing
                                                // into an existing table then use its type
                                                // definition
                                                typeDefn = importType == ImportType.IMPORT_ALL
                                                                                               ? tableTypeHandler.getTypeDefinition(columnValues[1])
                                                                                               : targetTypeDefn;

                                                // Check if the table type doesn't exist
                                                if (typeDefn == null)
                                                {
                                                    throw new CCDDException("Unknown table type '</b>"
                                                                            + columnValues[1]
                                                                            + "<b>'");
                                                }

                                                // Use the table name (with path, if applicable)
                                                // and type to build the parent, path, and type for
                                                // the table information class
                                                tablePath = columnValues[0];
                                                tableDefn.setName(tablePath);
                                                tableDefn.setTypeName(columnValues[1]);

                                                // Get the number of expected columns (the hidden
                                                // columns, primary key and row index, should not
                                                // be included in the CSV file)
                                                numColumns = typeDefn.getColumnCountVisible();
                                            }
                                            // Incorrect number of inputs
                                            else
                                            {
                                                throw new CCDDException("Too many/few table name and type inputs");
                                            }

                                            break;

                                        case DESCRIPTION:
                                            // Store the table description
                                            tableDefn.setDescription(columnValues[0]);
                                            break;

                                        case COLUMN_DATA:
                                            // Check if any column names exist
                                            if (columnValues.length != 0)
                                            {
                                                // Number of columns in an import file that match
                                                // the target table
                                                int numValidColumns = 0;

                                                // Create storage for the column indices
                                                columnIndex = new int[columnValues.length];

                                                // Step through each column name
                                                for (int index = 0; index < columnValues.length; index++)
                                                {
                                                    // Get the index for this column name
                                                    columnIndex[index] = typeDefn.getVisibleColumnIndexByUserName(columnValues[index]);

                                                    // Check if the column name in the file matches
                                                    // that of a column in the table
                                                    if (columnIndex[index] != -1)
                                                    {
                                                        // Increment the counter that tracks the
                                                        // number of matched columns
                                                        numValidColumns++;
                                                    }
                                                    // The number of inputs is incorrect
                                                    else
                                                    {
                                                        // Check if the error should be ignored or
                                                        // the import canceled
                                                        continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                                                 "<html><b>Table '</b>"
                                                                                                                        + tableDefn.getName()
                                                                                                                        + "<b>' column name '</b>"
                                                                                                                        + columnValues[index]
                                                                                                                        + "<b>' unrecognized in import file '</b>"
                                                                                                                        + importFile.getAbsolutePath()
                                                                                                                        + "<b>'; continue?",
                                                                                                 "Column Error",
                                                                                                 "Ignore this invalid column name",
                                                                                                 "Ignore this and any remaining invalid column names",
                                                                                                 "Stop importing",
                                                                                                 parent);
                                                    }
                                                }

                                                // Check if no column names in the file match those
                                                // in the table
                                                if (numValidColumns == 0)
                                                {
                                                    throw new CCDDException("No columns match those in the target table",
                                                                            JOptionPane.WARNING_MESSAGE);
                                                }
                                            }
                                            // The file contains no column data
                                            else
                                            {
                                                throw new CCDDException("File format invalid");
                                            }

                                            // Set the import tag to look for cell data
                                            importTag = CSVTags.CELL_DATA;
                                            break;

                                        case CELL_DATA:
                                            // Create storage for the row of cell data and
                                            // initialize the values to nulls (a null indicates
                                            // that the pasted cell value won't overwrite the
                                            // current table value if overwriting; if inserting the
                                            // pasted value is changed to a space)
                                            String[] rowData = new String[numColumns];
                                            Arrays.fill(rowData, null);

                                            // Step through each column in the row
                                            for (int index = 0; index < columnValues.length; index++)
                                            {
                                                // Check if the column exists
                                                if (index < columnIndex.length
                                                    && columnIndex[index] != -1)
                                                {
                                                    // Store the cell data in the column matching
                                                    // the one in the target table
                                                    rowData[columnIndex[index]] = columnValues[index];
                                                }
                                            }

                                            // Add the row of data read in from the file to the
                                            // cell data list
                                            tableDefn.addData(rowData);
                                            break;

                                        case DATA_FIELD:
                                            // Append empty columns as needed to fill out the
                                            // expected number of inputs
                                            columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                            FieldsColumn.values().length
                                                                                                          - 1
                                                                                                          - columnValues.length);

                                            // Add the data field definition, checking for (and if
                                            // possible, correcting) errors
                                            continueOnDataFieldError = addImportedDataFieldDefinition(continueOnDataFieldError,
                                                                                                      tableDefn,
                                                                                                      new String[] {tableDefn.getName(),
                                                                                                                    columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                                                                                    columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1]},
                                                                                                      importFile.getAbsolutePath(),
                                                                                                      inputTypeHandler,
                                                                                                      fieldHandler,
                                                                                                      parent);

                                            break;

                                        case DATA_TYPE:
                                        case INPUT_TYPE:
                                        case MACRO:
                                        case TABLE_TYPE:
                                        case TABLE_TYPE_DATA_FIELD:
                                        case RESERVED_MSG_IDS:
                                        case PROJECT_DATA_FIELD:
                                        case GROUP:
                                        case GROUP_DATA_FIELD:
                                        case SCRIPT_ASSOCIATION:
                                            break;

                                        default:
                                            // Inform the user that no tag appears in the file
                                            // before other data
                                            throw new CCDDException("Tag information missing");
                                    }
                                }
                            }
                        }

                        // Read next line in file
                        line = br.readLine();
                    }

                    // Check if this is the third pass
                    if (loop == 3)
                    {
                        // Check if a table definition exists in the import file
                        if (tableDefn.getName() != null)
                        {
                            // Add the table's definition to the list
                            tableDefinitions.add(tableDefn);
                        }

                        // Check if only the data from the first table is to be read
                        if (importType == ImportType.FIRST_DATA_ONLY)
                        {
                            // Stop reading table definitions
                            break;
                        }
                    }
                }

                // Check if this is the first pass
                if (loop == 1)
                {
                    // Add the input type if it's new or match it to an existing one with the same
                    // name if the type definitions are the same
                    inputTypeHandler.updateInputTypes(inputTypeDefns);
                }
                // Check if this is the second pass
                else if (loop == 2)
                {
                    // Add the table type if it's new or match it to an existing one with the same
                    // name if the type definitions are the same
                    String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefns);

                    // Check if a table type isn't new and doesn't match an existing one with the
                    // same name
                    if (badDefn != null)
                    {
                        throw new CCDDException("Imported table type '</b>"
                                                + badDefn
                                                + "<b>' doesn't match the existing definition");
                    }

                    // Check if all definitions are to be loaded
                    if (importType == ImportType.IMPORT_ALL)
                    {
                        // Add the data type if it's new or match it to an existing one with the
                        // same name if the type definitions are the same
                        dataTypeHandler.updateDataTypes(dataTypeDefns);

                        // Add the macro if it's new or match it to an existing one with the same
                        // name. If the flag to replace existing macro values is false then get the
                        // list of macros names where the existing and import file values differ
                        List<String> mismatchedMacros = macroHandler.updateMacros(macroDefns,
                                                                                  replaceExistingMacros);

                        // Check if any existing and import file macro values differ ( the flag to
                        // replace existing macro values is false)
                        if (!mismatchedMacros.isEmpty())
                        {
                            boolean continueOnError = false;

                            // Check if the user elects to ignore the difference(s), keeping the
                            // existing macro values, or cancels the import operation
                            getErrorResponse(continueOnError,
                                             "<html><b>The value for imported macro(s) '</b>"
                                                              + CcddUtilities.convertArrayToStringTruncate(mismatchedMacros.toArray(new String[0]))
                                                              + "<b>' doesn't match the existing definition(s) in import file '</b>"
                                                              + importFile.getAbsolutePath()
                                                              + "<b>'; continue?",
                                             "Macro Value Mismatch",
                                             null,
                                             "Ignore macro value difference(s) (keep existing value(s))",
                                             "Stop importing",
                                             true,
                                             parent);
                        }

                        // Add the reserved message ID if it's new
                        rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);

                        // Build the imported project-level and group data fields, if any
                        buildProjectAndGroupDataFields(fieldHandler, projectDefn.getDataFields());
                    }
                }
            }
        }
        finally
        {
            try
            {
                // Check that the buffered reader exists
                if (br != null)
                {
                    // Close the file
                    br.close();
                }
            }
            catch (IOException ioe)
            {
                // Inform the user that the file cannot be closed
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Cannot close import file '</b>"
                                                                  + importFile.getAbsolutePath()
                                                                  + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Export the project in CSV format to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to export
     *
     * @param includeBuildInformation
     *            true to include the CCDD version, project, host, and user information
     *
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding values
     *
     * @param includeAllTableTypes
     *            true to include the all table type definitions in the export file
     *
     * @param includeAllDataTypes
     *            true to include the all data type definitions in the export file
     *
     * @param includeAllInputTypes
     *            true to include the all user-defined input type definitions in the export file
     *
     * @param includeAllMacros
     *            true to include the all macro definitions in the export file
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table in the export file
     *
     * @param includeProjectFields
     *            true to include the project-level data field definitions in the export file
     *
     * @param includeGroups
     *            true to include the groups and group data field definitions in the export file
     *
     * @param includeAssociations
     *            true to include the script associations in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a structure table, both in
     *            application format and using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *
     * @param extraInfo
     *            unused
     *
     * @throws CCDDException
     *             If a file I/O error occurs
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportToFile(FileEnvVar exportFile,
                             String[] tableNames,
                             boolean includeBuildInformation,
                             boolean replaceMacros,
                             boolean includeAllTableTypes,
                             boolean includeAllDataTypes,
                             boolean includeAllInputTypes,
                             boolean includeAllMacros,
                             boolean includeReservedMsgIDs,
                             boolean includeProjectFields,
                             boolean includeGroups,
                             boolean includeAssociations,
                             boolean includeVariablePaths,
                             CcddVariableHandler variableHandler,
                             String[] separators,
                             Object... extraInfo) throws CCDDException, Exception
    {
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        try
        {
            List<String> referencedTableTypes = new ArrayList<String>();
            List<String> referencedDataTypes = new ArrayList<String>();
            List<String> referencedInputTypes = new ArrayList<String>();
            List<String> referencedMacros = new ArrayList<String>();
            List<String[]> variablePaths = new ArrayList<String[]>();

            // Check if all table type definitions are to be exported
            if (includeAllTableTypes)
            {
                // Add all table type definitions to the referenced table types list
                referencedTableTypes.addAll(Arrays.asList(tableTypeHandler.getTableTypeNames()));
            }

            // Check if all data type definitions are to be exported
            if (includeAllDataTypes)
            {
                // Add all data type definitions to the referenced table types list
                referencedDataTypes.addAll(dataTypeHandler.getDataTypeNames());
            }

            // Check if all input type definitions are to be exported
            if (includeAllInputTypes)
            {
                // Add all input type definitions to the referenced table types list
                referencedInputTypes.addAll(Arrays.asList(inputTypeHandler.getNames(true)));
            }

            // Check if all macro definitions are to be exported
            if (includeAllMacros)
            {
                // Add all macro definitions to the referenced table types list
                referencedMacros.addAll(macroHandler.getMacroNames());
            }

            // Check if all variable paths are to be exported. This is only possible if no tables
            // are specified; otherwise only those variables in the table are exported
            if (includeVariablePaths && tableNames.length == 0)
            {
                // Step through each structure and variable name
                for (String variablePath : variableHandler.getAllVariableNames())
                {
                    // Add the path, in both application and user-defined formats, to the list to
                    // be output
                    variablePaths.add(new String[] {variablePath,
                                                    variableHandler.getFullVariableName(variablePath,
                                                                                        separators[0],
                                                                                        Boolean.parseBoolean(separators[1]),
                                                                                        separators[2])});
                }
            }

            // Output the table data to the selected file. Multiple writers are needed in case
            // tables are appended to an existing file
            fw = new FileWriter(exportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            // Check if the build information is to be output
            if (includeBuildInformation)
            {
                // Output the file creation information
                pw.printf("# Created "
                          + new Date().toString()
                          + " : CCDD version = "
                          + ccddMain.getCCDDVersionInformation()
                          + " : project = "
                          + dbControl.getProjectName()
                          + " : host = "
                          + dbControl.getServer()
                          + " : user = "
                          + dbControl.getUser()
                          + "\n");
            }

            // Step through each table
            for (String tblName : tableNames)
            {
                // Get the information from the database for the specified table
                TableInformation tableInfo = dbTable.loadTableData(tblName,
                                                                   true,
                                                                   false,
                                                                   parent);

                // Check if the table's data successfully loaded
                if (!tableInfo.isErrorFlag())
                {
                    // Get the table type definition based on the type name
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                    // Check if this table type is not already output
                    if (!referencedTableTypes.contains(tableInfo.getType()))
                    {
                        // Add the table type to the list of those referenced
                        referencedTableTypes.add(tableInfo.getType());
                    }

                    // Get the visible column names based on the table's type
                    String[] columnNames = typeDefn.getColumnNamesVisible();

                    // Check if the flag is set that indicates macros should be replaced
                    if (replaceMacros)
                    {
                        // Replace all macro names with their corresponding values
                        tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));
                    }

                    // Output the table path (if applicable) and name, table type, and system path
                    // (if provided)
                    pw.printf("\n" + CSVTags.NAME_TYPE.getTag() + "\n%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(tableInfo.getTablePath(),
                                                                       tableInfo.getType(),
                                                                       fieldHandler.getFieldValue(tblName,
                                                                                                  DefaultInputType.SYSTEM_PATH)));

                    // Check if the table has a description
                    if (!tableInfo.getDescription().isEmpty())
                    {
                        // Output the table description tag and description
                        pw.printf(CSVTags.DESCRIPTION.getTag() + "\n%s\n",
                                  CcddUtilities.addEmbeddedQuotes(tableInfo.getDescription()));
                    }

                    // Output the column data tag and column names
                    pw.printf(CSVTags.COLUMN_DATA.getTag() + "\n%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(columnNames));

                    // Step through each row in the table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        // Output the table row data, skipping the hidden columns
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(Arrays.copyOfRange(CcddUtilities.convertObjectToString(tableInfo.getData()[row]),
                                                                                              NUM_HIDDEN_COLUMNS,
                                                                                              tableInfo.getData()[row].length)));

                        // Step through each column in the row
                        for (int column = 0; column < columnNames.length; column++)
                        {
                            List<Integer> dataTypeColumns = new ArrayList<Integer>();

                            // Get the column indices for all columns that can contain a primitive
                            // data type
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIM_AND_STRUCT));
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIMITIVE));

                            // Step through each data type column
                            for (int dataTypeColumn : dataTypeColumns)
                            {
                                // Get the value in the data type column
                                String dataTypeName = tableInfo.getData()[row][dataTypeColumn].toString();

                                // Check if the data type is a primitive and isn't already in the
                                // list
                                if (dataTypeHandler.isPrimitive(dataTypeName)
                                    && !referencedDataTypes.contains(dataTypeName))
                                {
                                    // Add the data type name to the list of references data types
                                    referencedDataTypes.add(dataTypeName);
                                }
                            }

                            // Step through each macro referenced in the cell
                            for (String refMacro : macroHandler.getReferencedMacros(tableInfo.getData()[row][column
                                                                                                             + NUM_HIDDEN_COLUMNS].toString()))
                            {
                                // Get the name of the macro as stored in the internal
                                // macros table
                                String storedMacroName = macroHandler.getStoredMacroName(refMacro);

                                // Check if the macro name isn't already in the list of referenced
                                // macros
                                if (!referencedMacros.contains(storedMacroName))
                                {
                                    // Add the macro name to the list of referenced macros
                                    referencedMacros.add(storedMacroName);
                                }
                            }

                            // Check if variable paths are to be output and if this table
                            // represents a structure
                            if (includeVariablePaths && typeDefn.isStructure())
                            {
                                // Get the variable path
                                String variablePath = tableInfo.getTablePath()
                                                      + ","
                                                      + tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)]
                                                      + "."
                                                      + tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

                                // Add the path, in both application and user-defined formats, to
                                // the list to be output
                                variablePaths.add(new String[] {variablePath,
                                                                variableHandler.getFullVariableName(variablePath,
                                                                                                    separators[0],
                                                                                                    Boolean.parseBoolean(separators[1]),
                                                                                                    separators[2])});
                            }
                        }
                    }

                    // Get the table's data field information
                    List<FieldInformation> fieldInformation = tableInfo.getFieldInformation();

                    // Check if the table contains any data fields
                    if (!fieldInformation.isEmpty())
                    {
                        // Output the data field marker
                        pw.printf(CSVTags.DATA_FIELD.getTag() + "\n");

                        // Step through each data field
                        for (FieldInformation fieldInfo : fieldInformation)
                        {
                            // Output the field information
                            pw.printf("%s\n",
                                      CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                                                               fieldInfo.getDescription(),
                                                                               Integer.toString(fieldInfo.getSize()),
                                                                               fieldInfo.getInputType().getInputName(),
                                                                               Boolean.toString(fieldInfo.isRequired()),
                                                                               fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                               fieldInfo.getValue()));
                        }
                    }
                }
            }

            // Check if any table types are referenced (or all are included)
            if (!referencedTableTypes.isEmpty())
            {
                // Step through each referenced table type
                for (String tableType : referencedTableTypes)
                {
                    // Get the table type definition based on the type name
                    TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(tableType);

                    // Step through each table type column input type
                    for (InputType inputType : tableTypeDefn.getInputTypes())
                    {
                        // Check if the input type is user-defined and this input type is not
                        // already output
                        if (inputType.isCustomInput()
                            && !referencedInputTypes.contains(inputType.getInputName()))
                        {
                            // Add the input type to the list of those referenced
                            referencedInputTypes.add(inputType.getInputName());
                        }
                    }

                    // Step through each data field belonging to the table type
                    for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(tableTypeDefn.getName())))
                    {
                        // Check if if the input type is user-defined and this input type is not
                        // already output
                        if (fieldInfo.getInputType().isCustomInput()
                            && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                        {
                            // Add the input type to the list of those referenced
                            referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                        }
                    }

                    // Output the table type tag, and the type name and
                    // description
                    pw.printf("\n" + CSVTags.TABLE_TYPE.getTag() + "\n%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(tableTypeDefn.getName(),
                                                                       tableTypeDefn.getDescription()));

                    // Step through each column defined for the table type, skipping the primary
                    // key and row index columns
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                    {
                        // Output the column definition
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(tableTypeDefn.getColumnNamesUser()[column],
                                                                           tableTypeDefn.getColumnToolTips()[column],
                                                                           tableTypeDefn.getInputTypes()[column].getInputName(),
                                                                           tableTypeDefn.isRowValueUnique()[column].toString(),
                                                                           tableTypeDefn.isRequired()[column].toString(),
                                                                           tableTypeDefn.isStructureAllowed()[column].toString(),
                                                                           tableTypeDefn.isPointerAllowed()[column].toString()));
                    }

                    // Build the data field information for this table type
                    List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(tableType));

                    // Check if the table type contains any data fields
                    if (!fieldInformation.isEmpty())
                    {
                        // Output the data field marker
                        pw.printf(CSVTags.TABLE_TYPE_DATA_FIELD.getTag() + "\n");

                        // Step through each data field
                        for (FieldInformation fieldInfo : fieldInformation)
                        {
                            // Output the field information
                            pw.printf("%s\n",
                                      CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                                                               fieldInfo.getDescription(),
                                                                               Integer.toString(fieldInfo.getSize()),
                                                                               fieldInfo.getInputType().getInputName(),
                                                                               Boolean.toString(fieldInfo.isRequired()),
                                                                               fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                               fieldInfo.getValue()));
                        }
                    }
                }
            }

            // Check if any primitive data types are referenced (or all are included)
            if (!referencedDataTypes.isEmpty())
            {
                // Output the data type marker
                pw.printf("\n" + CSVTags.DATA_TYPE.getTag() + "\n");

                // Step through each data type
                for (String[] dataType : dataTypeHandler.getDataTypeData())
                {
                    // Check if the data type is referenced in the table
                    if (referencedDataTypes.contains(CcddDataTypeHandler.getDataTypeName(dataType)))
                    {
                        // Output the data type definition
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(dataType[DataTypesColumn.USER_NAME.ordinal()],
                                                                           dataType[DataTypesColumn.C_NAME.ordinal()],
                                                                           dataType[DataTypesColumn.SIZE.ordinal()],
                                                                           dataType[DataTypesColumn.BASE_TYPE.ordinal()]));
                    }
                }
            }

            // Check if any macros are referenced (or all are included)
            if (!referencedMacros.isEmpty())
            {
                // Output the macro marker
                pw.printf("\n" + CSVTags.MACRO.getTag() + "\n");

                // Step through each macro
                for (String[] macro : macroHandler.getMacroData())
                {
                    // Check if all macros are to be included or if the macro is referenced in the
                    // table
                    if (referencedMacros.contains(macro[MacrosColumn.MACRO_NAME.ordinal()]))
                    {
                        // Output the macro definition
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(macro[MacrosColumn.MACRO_NAME.ordinal()],
                                                                           macro[MacrosColumn.VALUE.ordinal()]));
                    }
                }
            }

            // Check if the user elected to store the reserved message IDs and if there are any
            // reserved message IDs defined
            if (includeReservedMsgIDs && !rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
            {
                // Output the reserved message ID marker
                pw.printf("\n" + CSVTags.RESERVED_MSG_IDS.getTag() + "\n");

                // Step through each reserved message ID
                for (String[] reservedMsgID : rsvMsgIDHandler.getReservedMsgIDData())
                {
                    // Output the reserved message ID definition
                    pw.printf("%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(reservedMsgID[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                                       reservedMsgID[ReservedMsgIDsColumn.DESCRIPTION.ordinal()]));
                }
            }

            // Check if the user elected to store the project-level data fields
            if (includeProjectFields)
            {
                // Build the data field information for the project
                List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldProjectName());

                // Check if the project contains any data fields
                if (!fieldInformation.isEmpty())
                {
                    // Output the project data field marker
                    pw.printf("\n" + CSVTags.PROJECT_DATA_FIELD.getTag() + "\n");

                    // Step through each data field
                    for (FieldInformation fieldInfo : fieldInformation)
                    {
                        // Check if if the input type is user-defined and this input type is not
                        // already output
                        if (fieldInfo.getInputType().isCustomInput()
                            && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                        {
                            // Add the input type to the list of those referenced
                            referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                        }

                        // Output the field information
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                                                           fieldInfo.getDescription(),
                                                                           Integer.toString(fieldInfo.getSize()),
                                                                           fieldInfo.getInputType().getInputName(),
                                                                           Boolean.toString(fieldInfo.isRequired()),
                                                                           fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                           fieldInfo.getValue()));
                    }
                }
            }

            // Check if the user elected to store the groups
            if (includeGroups)
            {
                // Get the group's information for the project
                List<GroupInformation> groupInformation = groupHandler.getGroupInformation();

                // Check if the project contains any groups
                if (!groupInformation.isEmpty())
                {
                    // Step through each group's information
                    for (GroupInformation groupInfo : groupInformation)
                    {
                        boolean isFirst = true;

                        // Output the group marker and the group information
                        pw.printf("\n" + CSVTags.GROUP.getTag() + "\n%s,\"",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(groupInfo.getName(),
                                                                           groupInfo.getDescription(),
                                                                           Boolean.toString(groupInfo.isApplication())));

                        // Step through each group table member
                        for (String member : groupInfo.getTablesAndAncestors())
                        {
                            // Output the table member
                            pw.printf((isFirst
                                               ? ""
                                               : ";")
                                      + member);

                            isFirst = false;
                        }

                        pw.printf("\"\n");

                        // Build the data field information for the group
                        List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(groupInfo.getName()));

                        // Check if the group contains any data fields
                        if (!fieldInformation.isEmpty())
                        {
                            // Output the group data field marker
                            pw.printf(CSVTags.GROUP_DATA_FIELD.getTag() + "\n");

                            // Step through each data field
                            for (FieldInformation fieldInfo : fieldInformation)
                            {
                                // Check if if the input type is user-defined and this input type
                                // is not already output
                                if (fieldInfo.getInputType().isCustomInput()
                                    && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                                {
                                    // Add the input type to the list of those referenced
                                    referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                                }

                                // Output the field information
                                pw.printf("%s\n",
                                          CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                                                                   fieldInfo.getDescription(),
                                                                                   Integer.toString(fieldInfo.getSize()),
                                                                                   fieldInfo.getInputType().getInputName(),
                                                                                   Boolean.toString(fieldInfo.isRequired()),
                                                                                   fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                                   fieldInfo.getValue()));
                            }
                        }
                    }
                }
            }

            // Check if the user elected to store the script associations
            if (includeAssociations)
            {
                // Get the script association information
                associations = scriptHandler.getScriptAssociations(parent);

                // Check if any associations exist
                if (!associations.isEmpty())
                {
                    // Output the script association marker
                    pw.printf("\n" + CSVTags.SCRIPT_ASSOCIATION.getTag() + "\n");

                    // Step through each script association
                    for (String[] assn : associations)
                    {
                        // Output the association information
                        pw.printf("%s\n",
                                  CcddUtilities.addEmbeddedQuotesAndCommas(assn[AssociationsColumn.NAME.ordinal()],
                                                                           assn[AssociationsColumn.DESCRIPTION.ordinal()],
                                                                           assn[AssociationsColumn.SCRIPT_FILE.ordinal()],
                                                                           CcddScriptHandler.convertAssociationMembersFormat(assn[AssociationsColumn.MEMBERS.ordinal()],
                                                                                                                             false)));
                    }
                }
            }

            // Check if any custom input types are referenced (or all are included)
            if (!referencedInputTypes.isEmpty())
            {
                // Output the input type marker
                pw.printf("\n" + CSVTags.INPUT_TYPE.getTag() + "\n");

                // Step through each referenced input type
                for (String inputTypeName : referencedInputTypes)
                {
                    // Get the input type definition
                    InputType inputType = inputTypeHandler.getInputTypeByName(inputTypeName);

                    // Output the input type definition
                    pw.printf("%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(inputType.getInputName(),
                                                                       inputType.getInputDescription(),
                                                                       inputType.getInputMatch(),
                                                                       InputType.convertItemListToString(inputType.getInputItems()),
                                                                       inputType.getInputFormat().getFormatName()));
                }
            }

            // Check if variable paths are to be output and that any exist
            if (includeVariablePaths && !variablePaths.isEmpty())
            {
                // Output the variable path marker
                pw.printf("\n" + CSVTags.VARIABLE_PATHS.getTag() + "\n");

                // Step through each variable path
                for (String[] variablePath : variablePaths)
                {
                    // Output the variable path in application and user-defined formats
                    pw.printf("%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(variablePath[0],
                                                                       variablePath[1]));
                }
            }
        }
        catch (IOException ioe)
        {
            throw new CCDDException(ioe.getMessage());
        }
        finally
        {
            // Check if the PrintWriter was opened
            if (pw != null)
            {
                // Close the file
                pw.close();
            }

            try
            {
                // Check if the BufferedWriter was opened
                if (bw != null)
                {
                    // Close the file
                    bw.close();
                }

                // Check if the FileWriter was opened
                if (fw != null)
                {
                    // Close the file
                    fw.close();
                }
            }
            catch (IOException ioe)
            {
                // Inform the user that the data file cannot be closed
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Cannot close export file '</b>"
                                                                  + exportFile.getAbsolutePath()
                                                                  + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }
}
