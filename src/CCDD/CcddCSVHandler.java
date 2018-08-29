/**
 * CFS Command & Data Dictionary CSV handler.
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
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary CSV handler class
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

    // GUI component over which to center any error dialog
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    /**********************************************************************************************
     * CSV data type tags
     *********************************************************************************************/
    private enum CSVTags
    {
        COLUMN_NAMES("_column_data_"),
        CELL_DATA(""),
        NAME_TYPE("_name_type_"),
        DESCRIPTION("_description_"),
        DATA_FIELD("_data_fields_"),
        MACRO("_macros_"),
        TABLE_TYPE("_table_type_"),
        TABLE_TYPE_DATA_FIELD("_table_type_data_fields_"),
        DATA_TYPE("_data_type_"),
        INPUT_TYPE("_input_type_"),
        RESERVED_MSG_IDS("_reserved_msg_ids_"),
        PROJECT_DATA_FIELDS("_project_data_fields_"),
        VARIABLE_PATHS("_variable_paths_");

        private final String tag;

        /******************************************************************************************
         * CSV data type tags constructor
         *
         * @param tag
         *            text describing the data
         *****************************************************************************************/
        CSVTags(String tag)
        {
            this.tag = tag;
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
    }

    /**********************************************************************************************
     * CSV handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddCSVHandler(CcddMain ccddMain, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
    }

    /**********************************************************************************************
     * Get the table definitions
     *
     * @return List of table definitions
     *********************************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
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
                               TypeDefinition targetTypeDefn) throws CCDDException,
                                                              IOException,
                                                              Exception
    {
        BufferedReader br = null;

        try
        {
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

                // Create a buffered reader to read the file
                br = new BufferedReader(new FileReader(importFile));

                // Flags indicating if importing should continue after an input error is detected
                boolean continueOnTableTypeError = false;
                boolean continueOnDataTypeError = false;
                boolean continueOnInputTypeError = false;
                boolean continueOnMacroError = false;
                boolean continueOnColumnError = false;
                boolean continueOnDataFieldError = false;
                boolean continueOnReservedMsgIDError = false;
                boolean continueOnProjectFieldError = false;
                boolean continueOnTableTypeFieldError = false;

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
                        // Remove any trailing commas, empty quotes, and leading/trailing white
                        // space characters from the row. If the CSV file is generated from a
                        // spreadsheet application then extra commas are appended to a row if
                        // needed for the number of columns to be equal with the other rows. These
                        // empty trailing columns are ignored
                        line = line.trim().replaceAll("(?:[,\\s*]|\"\\s*\",|,\"\\s*\")*$", "");

                        // Check that the row isn't empty and isn't a comment line (starts with a #
                        // character)
                        if (!line.isEmpty() && !line.startsWith("#"))
                        {
                            // Check if the line contains an odd number of double quotes
                            if (line.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                            {
                                String nextLine = null;

                                // Step through the subsequent lines in order to find the end of
                                // multi-line value
                                while ((nextLine = br.readLine()) != null)
                                {
                                    // Append the line to the preceding one, inserting the line
                                    // feed
                                    line += "\n" + nextLine;

                                    // Check if this is the line that ends the multi-line value
                                    // (i.e., it ends with one double quote)
                                    if (nextLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                                    {
                                        // Stop searching; the multi-line string has been
                                        // concatenated to the initial line
                                        break;
                                    }
                                }
                            }

                            // Parse the import data. The values are comma- separated; however,
                            // commas within quotes are ignored - this allows commas to be included
                            // in the data values
                            columnValues = CcddUtilities.splitAndRemoveQuotes(line);

                            // Remove any leading/trailing white space characters from the first
                            // column value
                            String firstColumn = columnValues[0].trim();

                            // Check if this is the table name and table type tag
                            if (firstColumn.equalsIgnoreCase(CSVTags.NAME_TYPE.getTag()))
                            {
                                // Set the import tag to look for the table name and table type
                                importTag = CSVTags.NAME_TYPE;

                                // Check if this is the third pass and if the name and type are
                                // already set; if so, this is the beginning of another table's
                                // information
                                if (loop == 3 && !tablePath.isEmpty())
                                {
                                    // Stop processing the file in order to create the table prior
                                    // to beginning another one
                                    break;
                                }
                            }
                            // Check if this is the table column name tag and that a table name and
                            // type are defined
                            else if (firstColumn.equalsIgnoreCase(CSVTags.COLUMN_NAMES.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the import tag to look for the table column names
                                importTag = CSVTags.COLUMN_NAMES;
                            }
                            // Check if this is the table description tag and that a table name and
                            // type are defined
                            else if (firstColumn.equalsIgnoreCase(CSVTags.DESCRIPTION.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the import tag to look for the table description
                                importTag = CSVTags.DESCRIPTION;
                            }
                            // Check if this is the data field tag and that a table name and type
                            // are defined
                            else if (firstColumn.equalsIgnoreCase(CSVTags.DATA_FIELD.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the import tag to look for the data field(s)
                                importTag = CSVTags.DATA_FIELD;
                            }
                            // Check if this is the table type tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.TABLE_TYPE.getTag()))
                            {
                                // Set the import tag to look for the table type definition
                                importTag = CSVTags.TABLE_TYPE;

                                // Set the flag so that the next row is treated as the table type
                                // name and description
                                isTypeName = true;
                            }
                            // Check if this is the table type data field tag and that a table type
                            // is defined
                            else if (firstColumn.equalsIgnoreCase(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())
                                     && tableTypeDefn != null)
                            {
                                // Set the import tag to look for the table type data field(s)
                                importTag = CSVTags.TABLE_TYPE_DATA_FIELD;
                            }
                            // Check if this is the data type tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.DATA_TYPE.getTag()))
                            {
                                // Set the import tag to look for the data type(s)
                                importTag = CSVTags.DATA_TYPE;
                            }
                            // Check if this is the input type tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.INPUT_TYPE.getTag()))
                            {
                                // Set the import tag to look for the input type(s)
                                importTag = CSVTags.INPUT_TYPE;
                            }
                            // Check if this is the macro tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.MACRO.getTag()))
                            {
                                // Set the import tag to look for the macro(s)
                                importTag = CSVTags.MACRO;
                            }
                            // Check if this is the reserved message IDs tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.RESERVED_MSG_IDS.getTag()))
                            {
                                // Set the import tag to look for the reserved IDs
                                importTag = CSVTags.RESERVED_MSG_IDS;
                            }
                            // Check if this is the project-level data fields tag
                            else if (firstColumn.equalsIgnoreCase(CSVTags.PROJECT_DATA_FIELDS.getTag()))
                            {
                                // Set the import tag to look for the project-level data fields
                                importTag = CSVTags.PROJECT_DATA_FIELDS;
                            }
                            // Not a tag (or no table name and type are defined); read in the
                            // information based on the last tag read
                            else
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
                                                    // Add the input type definition (add a blank
                                                    // to represent the OID)
                                                    inputTypeDefns.add(new String[] {columnValues[InputTypesColumn.NAME.ordinal()],
                                                                                     columnValues[InputTypesColumn.DESCRIPTION.ordinal()],
                                                                                     columnValues[InputTypesColumn.MATCH.ordinal()],
                                                                                     columnValues[InputTypesColumn.ITEMS.ordinal()],
                                                                                     columnValues[InputTypesColumn.FORMAT.ordinal()],
                                                                                     ""});
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
                                        case COLUMN_NAMES:
                                        case DATA_FIELD:
                                        case DATA_TYPE:
                                        case DESCRIPTION:
                                        case MACRO:
                                        case NAME_TYPE:
                                        case PROJECT_DATA_FIELDS:
                                        case RESERVED_MSG_IDS:
                                        case TABLE_TYPE:
                                        case TABLE_TYPE_DATA_FIELD:
                                        case VARIABLE_PATHS:
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
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() - 1],
                                                                                                                                      columnValues[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() - 1]},
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
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == FieldsColumn.values().length - 1
                                                    || columnValues.length == FieldsColumn.values().length - 2)
                                                {
                                                    // Append empty columns as needed to fill out
                                                    // the expected number of inputs
                                                    columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                    FieldsColumn.values().length
                                                                                                                  - 1
                                                                                                                  - columnValues.length);

                                                    // Add the data field definition, checking for
                                                    // (and if possible, correcting) errors
                                                    continueOnTableTypeFieldError = addImportedDataFieldDefinition(continueOnTableTypeFieldError,
                                                                                                                   tableTypeDefn,
                                                                                                                   new String[] {CcddFieldHandler.getFieldTypeName(tableTypeDefn.getTypeName()),
                                                                                                                                 columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                                 columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1]},
                                                                                                                   importFile.getAbsolutePath(),
                                                                                                                   inputTypeHandler,
                                                                                                                   parent);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnTableTypeFieldError = getErrorResponse(continueOnTableTypeFieldError,
                                                                                                     "<html><b>Table type '</b>"
                                                                                                                                    + tableTypeDefn.getTypeName()
                                                                                                                                    + "<b>' has missing or extra data field "
                                                                                                                                    + "input(s) in import file '</b>"
                                                                                                                                    + importFile.getAbsolutePath()
                                                                                                                                    + "<b>'; continue?",
                                                                                                     "Data Field Error",
                                                                                                     "Ignore this invalid data field",
                                                                                                     "Ignore this and any remaining invalid data fields",
                                                                                                     "Stop importing",
                                                                                                     parent);
                                                }
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
                                                    // Add the data type definition (add a blank to
                                                    // represent the OID)
                                                    dataTypeDefns.add(new String[] {columnValues[DataTypesColumn.USER_NAME.ordinal()],
                                                                                    columnValues[DataTypesColumn.C_NAME.ordinal()],
                                                                                    columnValues[DataTypesColumn.SIZE.ordinal()],
                                                                                    columnValues[DataTypesColumn.BASE_TYPE.ordinal()],
                                                                                    ""});
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
                                                    // Add the macro definition (add a blank to
                                                    // represent the OID)
                                                    macroDefns.add(new String[] {columnValues[0],
                                                                                 (columnValues.length == 2
                                                                                                           ? columnValues[1]
                                                                                                           : ""),
                                                                                 ""});
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

                                        case PROJECT_DATA_FIELDS:
                                            // Check if all definitions are to be loaded
                                            if (importType == ImportType.IMPORT_ALL)
                                            {
                                                // Check if the expected number of inputs is
                                                // present
                                                if (columnValues.length == FieldsColumn.values().length - 1
                                                    || columnValues.length == FieldsColumn.values().length - 2)
                                                {
                                                    // Append empty columns as needed to fill out
                                                    // the expected number of inputs
                                                    columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                    FieldsColumn.values().length
                                                                                                                  - 1
                                                                                                                  - columnValues.length);

                                                    // Add the data field definition, checking for
                                                    // (and if possible, correcting) errors
                                                    continueOnProjectFieldError = addImportedDataFieldDefinition(continueOnProjectFieldError,
                                                                                                                 projectDefn,
                                                                                                                 new String[] {CcddFieldHandler.getFieldProjectName(),
                                                                                                                               columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                               columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1]},
                                                                                                                 importFile.getAbsolutePath(),
                                                                                                                 inputTypeHandler,
                                                                                                                 parent);
                                                }
                                                // The number of inputs is incorrect
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnProjectFieldError = getErrorResponse(continueOnProjectFieldError,
                                                                                                   "<html><b>Project-level data field has missing "
                                                                                                                                + "or extra input(s) in import file '</b>"
                                                                                                                                + importFile.getAbsolutePath()
                                                                                                                                + "<b>'; continue?",
                                                                                                   "Data Field Error",
                                                                                                   "Ignore this invalid data field",
                                                                                                   "Ignore this and any remaining invalid data fields",
                                                                                                   "Stop importing",
                                                                                                   parent);
                                                }
                                            }

                                            break;

                                        case INPUT_TYPE:
                                        case CELL_DATA:
                                        case COLUMN_NAMES:
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
                                // This is the second pass
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

                                        case COLUMN_NAMES:
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
                                            // Check if the expected number of inputs is present
                                            if (columnValues.length == FieldsColumn.values().length - 1
                                                || columnValues.length == FieldsColumn.values().length - 2)
                                            {
                                                // Append empty columns as needed to fill out the
                                                // expected number of inputs
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                                                FieldsColumn.values().length
                                                                                                              - 1
                                                                                                              - columnValues.length);

                                                // Add the data field definition, checking for (and
                                                // if possible, correcting) errors
                                                continueOnDataFieldError = addImportedDataFieldDefinition(continueOnDataFieldError,
                                                                                                          tableDefn,
                                                                                                          new String[] {tableDefn.getName(),
                                                                                                                        columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                                                        columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1]},
                                                                                                          importFile.getAbsolutePath(),
                                                                                                          inputTypeHandler,
                                                                                                          parent);
                                            }
                                            // The number of inputs is incorrect
                                            else
                                            {
                                                // Check if the error should be ignored or the
                                                // import canceled
                                                continueOnDataFieldError = getErrorResponse(continueOnDataFieldError,
                                                                                            "<html><b>Table '</b>"
                                                                                                                      + tableDefn.getName()
                                                                                                                      + "<b>' has missing or extra data field "
                                                                                                                      + "input(s) in import file '</b>"
                                                                                                                      + importFile.getAbsolutePath()
                                                                                                                      + "<b>'; continue?",
                                                                                            "Data Field Error",
                                                                                            "Ignore this invalid data field",
                                                                                            "Ignore this and any remaining invalid data fields",
                                                                                            "Stop importing",
                                                                                            parent);
                                            }

                                            break;

                                        case DATA_TYPE:
                                        case INPUT_TYPE:
                                        case MACRO:
                                        case TABLE_TYPE:
                                        case TABLE_TYPE_DATA_FIELD:
                                        case RESERVED_MSG_IDS:
                                        case PROJECT_DATA_FIELDS:
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
                        // Add the table's definition to the list
                        tableDefinitions.add(tableDefn);

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
                        // name if the values are the same
                        macroHandler.updateMacros(macroDefns);

                        // Add the reserved message ID if it's new
                        rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);

                        // Build the imported project-level data fields, if any
                        buildProjectdataFields(ccddMain, projectDefn.getDataFields());
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
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding values
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table in the export file
     *
     * @param includeProjectFields
     *            true to include the project-level data field definitions in the export file
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
                             boolean replaceMacros,
                             boolean includeReservedMsgIDs,
                             boolean includeProjectFields,
                             boolean includeVariablePaths,
                             CcddVariableHandler variableHandler,
                             String[] separators,
                             Object... extraInfo) throws CCDDException, Exception
    {
        boolean addLineFeed = false;
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

            // Output the table data to the selected file. Multiple writers are needed in case
            // tables are appended to an existing file
            fw = new FileWriter(exportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

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
                    // Output the file creation information (for the first pass only)
                    pw.printf((!addLineFeed
                                            ? "# Created "
                                              + new Date().toString()
                                              + " : project = "
                                              + dbControl.getDatabaseName()
                                              + " : host = "
                                              + dbControl.getServer()
                                              + " : user = "
                                              + dbControl.getUser()
                                              + "\n"
                                            : "")
                              + "\n");

                    // Get the table type definition based on the type name
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                    // Check if this table type is not already output
                    if (!referencedTableTypes.contains(tableInfo.getType()))
                    {
                        // Add the table type to the list of those referenced
                        referencedTableTypes.add(tableInfo.getType());
                    }

                    // Step through each table type column input type
                    for (InputType inputType : typeDefn.getInputTypes())
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

                    // Step through each data field belonging to the table
                    for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(tblName))
                    {
                        // Check if if the input type is user-defined and this input type is
                        // not already output
                        if (fieldInfo.getInputType().isCustomInput()
                            && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                        {
                            // Add the input type to the list of those referenced
                            referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                        }
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
                    pw.printf(CSVTags.NAME_TYPE.getTag() + "\n%s\n",
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
                    pw.printf(CSVTags.COLUMN_NAMES.getTag() + "\n%s\n",
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

                            // Get the names of the macros referenced in the cell and add them to
                            // the list
                            referencedMacros.addAll(macroHandler.getReferencedMacros(tableInfo.getData()[row][column
                                                                                                              + NUM_HIDDEN_COLUMNS].toString()));

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

                    addLineFeed = true;
                }
            }

            // Check if any table types are referenced
            if (!referencedTableTypes.isEmpty())
            {
                // Step through each referenced table type
                for (String tableType : referencedTableTypes)
                {
                    // Get the table type definition based on the type name
                    TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(tableType);

                    // Output the table type tag, and the type name and description
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
                    List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(tableType);

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

            // Check if any primitive data types are referenced
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

            // Check if any custom input types are referenced
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

            // Check if any macros are referenced
            if (!referencedMacros.isEmpty())
            {
                // Output the macro marker
                pw.printf("\n" + CSVTags.MACRO.getTag() + "\n");

                // Step through each macro
                for (String[] macro : macroHandler.getMacroData())
                {
                    // Check if the macro is referenced in the table
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

            // Check if the user elected to store the project-level data fields and if there are
            // any project-level data fields defined
            if (includeProjectFields)
            {
                // Build the data field information for the project
                List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldProjectName());

                // Check if the project contains any data fields
                if (!fieldInformation.isEmpty())
                {
                    // Output the project data field marker
                    pw.printf("\n" + CSVTags.PROJECT_DATA_FIELDS.getTag() + "\n");

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
        catch (Exception e)
        {
            throw new Exception(e);
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
