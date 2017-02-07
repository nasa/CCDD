/**
 * CFS Command & Data Dictionary CSV handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableTypeDefinition;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary CSV handler class
 *****************************************************************************/
public class CcddCSVHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDbControlHandler dbControl;
    private final CcddMacroHandler macroHandler;

    // GUI component instantiating this class
    private final Component parent;

    // Name of the data field containing the system name
    private String systemFieldName;

    // List containing the imported table, table type, data type, and macro
    // definitions
    private List<TableDefinition> tableDefinitions;

    /**************************************************************************
     * CSV data type tags
     *************************************************************************/
    protected enum CSVTags
    {
        COLUMN_NAMES("_column_data_"),
        CELL_DATA(""),
        NAME_TYPE("_name_type_"),
        DESCRIPTION("_description_"),
        DATA_FIELD("_data_fields_"),
        MACRO("_macros_"),
        TABLE_TYPE("_table_type_"),
        DATA_TYPE("_data_type_");

        private final String tag;

        /**********************************************************************
         * CSV data type tags constructor
         * 
         * @param tag
         *            text describing the data
         *********************************************************************/
        CSVTags(String tag)
        {
            this.tag = tag;
        }

        /**********************************************************************
         * Get the data type tag
         * 
         * @return Text describing the data
         *********************************************************************/
        protected String getTag()
        {
            return tag;
        }
    }

    /**************************************************************************
     * CSV handler class constructor
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddCSVHandler(CcddMain ccddMain, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
    }

    /**************************************************************************
     * Get the status of the conversion setup error flag
     * 
     * @return Always returns false for the CSV conversion
     *************************************************************************/
    @Override
    public boolean getErrorStatus()
    {
        return false;
    }

    /**************************************************************************
     * Get the table definitions
     * 
     * @return List of table definitions
     *************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
    }

    /**************************************************************************
     * Build the information from the table definition(s) in the current file
     * 
     * @param importFile
     *            import file reference
     *************************************************************************/
    @Override
    public void importFromFile(File importFile) throws CCDDException, IOException
    {
        BufferedReader br = null;

        try
        {
            List<TableTypeDefinition> tableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            List<String[]> dataTypeDefinitions = new ArrayList<String[]>();
            List<String[]> macroDefinitions = new ArrayList<String[]>();
            tableDefinitions = new ArrayList<TableDefinition>();

            // Make two passes through the file, first to get the table types,
            // data types, and macros, then a second pass to read the table
            // data and fields
            for (int loop = 1; loop <= 2; loop++)
            {
                int columnNumber = 0;

                // Create a buffered reader to read the file
                br = new BufferedReader(new FileReader(importFile));

                // Flag indicating if importing should continue after an input
                // mismatch is detected
                boolean continueOnMismatch = false;

                // Initialize the input type
                CSVTags importType = null;

                // Read first line in file
                String line = br.readLine();

                // Continue to read the file until EOF is reached or an error
                // is detected. This outer while loop accounts for multiple
                // table definitions within a single file
                while (line != null)
                {
                    TableTypeDefinition tableTypeDefn = null;

                    // Initialize the table information
                    int numColumns = 0;
                    String tablePath = "";

                    // Create empty table information and table type
                    // definition references
                    TypeDefinition typeDefn = null;

                    // Storage for column indices
                    int[] columnIndex = null;

                    // Initialize the number of matching columns and the cell
                    // data storage
                    String[] columnValues = null;

                    // Create a table definition to contain the table's
                    // information
                    TableDefinition tableDefn = new TableDefinition();

                    // Flag that indicates if a table type row is the type name
                    // and description or a column definition
                    boolean isTypeName = false;

                    // Continue to read the file until EOF is reached or an
                    // error is detected. This inner while loop reads the
                    // information for a single table in the file
                    while (line != null)
                    {
                        // Check that the row isn't empty (commas only) and
                        // isn't a comment line (starts with a # character)
                        if (!line.replace(",", "").trim().isEmpty()
                            && !line.startsWith("#"))
                        {
                            // Parse table data. The values are
                            // comma-separated; however, commas within quotes
                            // are ignored - this allows commas to be included
                            // in the data values
                            columnValues = CcddUtilities.splitAndRemoveQuotes(line);

                            // Check if this is the table name and table type
                            // tag
                            if (columnValues[0].equalsIgnoreCase(CSVTags.NAME_TYPE.getTag()))
                            {
                                // Set the input type to look for the table
                                // name and table type
                                importType = CSVTags.NAME_TYPE;

                                // Check if this is the second pass and if the
                                // name and type are already set; if so, this
                                // is the beginning of another table's
                                // information
                                if (loop == 2 && !tablePath.isEmpty())
                                {
                                    // Stop processing the file in order to
                                    // create the table prior to beginning
                                    // another one
                                    break;
                                }
                            }
                            // Check if this is the table column name tag and
                            // that a table name and type are defined
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.COLUMN_NAMES.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the input type to look for the table
                                // column names
                                importType = CSVTags.COLUMN_NAMES;
                            }
                            // Check if this is the table description tag and
                            // that a table name and type are defined
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.DESCRIPTION.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the input type to look for the table
                                // description
                                importType = CSVTags.DESCRIPTION;
                            }
                            // Check if this is the data field tag and that a
                            // table name and type are defined
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.DATA_FIELD.getTag())
                                     && !tablePath.isEmpty())
                            {
                                // Set the input type to look for the data
                                // field(s)
                                importType = CSVTags.DATA_FIELD;
                            }
                            // Check if this is the table type tag
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.TABLE_TYPE.getTag()))
                            {
                                // Set the input type to look for the table
                                // type definition
                                importType = CSVTags.TABLE_TYPE;

                                // Set the flag so that the next row is treated
                                // as the table type name and description
                                isTypeName = true;
                            }
                            // Check if this is the data type tag
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.DATA_TYPE.getTag()))
                            {
                                // Set the input type to look for the data
                                // type(s)
                                importType = CSVTags.DATA_TYPE;
                            }
                            // Check if this is the macro tag
                            else if (columnValues[0].equalsIgnoreCase(CSVTags.MACRO.getTag()))
                            {
                                // Set the input type to look for the macro(s)
                                importType = CSVTags.MACRO;
                            }
                            // Not a tag (or no table name and type are
                            // defined); read in the information based on the
                            // last tag read
                            else
                            {
                                // Check if this is the first pass
                                if (loop == 1)
                                {
                                    switch (importType)
                                    {
                                        case TABLE_TYPE:
                                            // Check if this is the table type
                                            // name and description
                                            if (isTypeName)
                                            {
                                                // Reset the flag so that
                                                // subsequent rows are treated
                                                // as column definitions
                                                isTypeName = false;
                                                columnNumber = NUM_HIDDEN_COLUMNS;

                                                // Check if the expected number
                                                // of inputs is present
                                                if (columnValues.length == 2)
                                                {
                                                    // Add the table type
                                                    // definition
                                                    tableTypeDefn = new TableTypeDefinition(columnValues[0],
                                                                                            columnValues[1]);

                                                    tableTypeDefinitions.add(tableTypeDefn);
                                                }
                                                // Incorrect number of inputs
                                                else
                                                {
                                                    throw new CCDDException("Too many/few table type name and description inputs");
                                                }
                                            }
                                            // This is a column definition
                                            else
                                            {
                                                // Check if the expected number
                                                // of inputs is present
                                                if (columnValues.length == TableTypeEditorColumnInfo.values().length - 1)
                                                {
                                                    // Add the table type
                                                    // column definition
                                                    tableTypeDefn.addColumn(new Object[] {columnNumber,
                                                                                          columnValues[TableTypeEditorColumnInfo.NAME.ordinal() - 1],
                                                                                          columnValues[TableTypeEditorColumnInfo.DESCRIPTION.ordinal() - 1],
                                                                                          columnValues[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal() - 1],
                                                                                          Boolean.valueOf(columnValues[TableTypeEditorColumnInfo.UNIQUE.ordinal() - 1]),
                                                                                          Boolean.valueOf(columnValues[TableTypeEditorColumnInfo.REQUIRED.ordinal() - 1]),
                                                                                          Boolean.valueOf(columnValues[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() - 1]),
                                                                                          Boolean.valueOf(columnValues[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() - 1])});
                                                    columnNumber++;
                                                }
                                                // Incorrect number of inputs
                                                else
                                                {
                                                    throw new CCDDException("Too many/few table type column inputs");
                                                }
                                            }

                                            break;

                                        case DATA_TYPE:
                                            // Check if the expected number of
                                            // inputs is present
                                            if (columnValues.length == 4)
                                            {
                                                // Add the data type definition
                                                // (add a blank to represent
                                                // the OID)
                                                dataTypeDefinitions.add(new String[] {columnValues[DataTypesColumn.USER_NAME.ordinal()],
                                                                                      columnValues[DataTypesColumn.C_NAME.ordinal()],
                                                                                      columnValues[DataTypesColumn.SIZE.ordinal()],
                                                                                      columnValues[DataTypesColumn.BASE_TYPE.ordinal()],
                                                                                      ""});
                                            }
                                            // Incorrect number of inputs.
                                            // Check if the user hasn't already
                                            // elected to ignore mismatches
                                            else if (!continueOnMismatch)
                                            {
                                                // Get confirmation from the
                                                // user to ignore the
                                                // discrepancy
                                                if (new CcddDialogHandler().showMessageDialog(parent,
                                                                                              "<html><b>Too many/few data type inputs; continue?",
                                                                                              "Data Type Mismatch",
                                                                                              JOptionPane.QUESTION_MESSAGE,
                                                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                                {
                                                    continueOnMismatch = true;
                                                }
                                                // The user chose to not ignore
                                                // the discrepancy
                                                else
                                                {
                                                    // No error message is
                                                    // provided since the user
                                                    // chose this action
                                                    throw new CCDDException("");
                                                }
                                            }

                                            break;

                                        case MACRO:
                                            // Check if the expected number of
                                            // inputs is present
                                            if (columnValues.length == 2)

                                            {
                                                // Add the macro definition
                                                // (add a blank to represent
                                                // the OID)
                                                macroDefinitions.add(new String[] {columnValues[0],
                                                                                   columnValues[1],
                                                                                   ""});
                                            }
                                            // Incorrect number of inputs.
                                            // Check if the user hasn't already
                                            // elected to ignore mismatches
                                            else if (!continueOnMismatch)
                                            {
                                                // Get confirmation from the
                                                // user to ignore the
                                                // discrepancy
                                                if (new CcddDialogHandler().showMessageDialog(parent,
                                                                                              "<html><b>Too many/few macro inputs; continue?",
                                                                                              "Macro Mismatch",
                                                                                              JOptionPane.QUESTION_MESSAGE,
                                                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                                {
                                                    continueOnMismatch = true;
                                                }
                                                // The user chose to not ignore
                                                // the discrepancy
                                                else
                                                {
                                                    // No error message is
                                                    // provided since the user
                                                    // chose this action
                                                    throw new CCDDException("");
                                                }
                                            }

                                            break;

                                        case CELL_DATA:
                                        case COLUMN_NAMES:
                                        case DATA_FIELD:
                                        case DESCRIPTION:
                                        case NAME_TYPE:
                                            break;

                                        default:
                                            // Inform the user that no tag
                                            // appears in the file before other
                                            // data
                                            throw new CCDDException("Import file <br>'</b>"
                                                                    + importFile.getAbsolutePath()
                                                                    + "<b>' information missing");
                                    }
                                }
                                // This is the second pass
                                else
                                {
                                    switch (importType)
                                    {
                                        case NAME_TYPE:
                                            // Check if the expected number of
                                            // inputs is present (the third
                                            // value, the system name, is
                                            // optional and not used)
                                            if (columnValues.length == 2
                                                || columnValues.length == 3)
                                            {
                                                // Use the table name (with
                                                // path, if applicable) and
                                                // type to build the parent,
                                                // path, and type for the table
                                                // information class
                                                tablePath = columnValues[0];
                                                tableDefn.setName(tablePath);
                                                tableDefn.setType(columnValues[1]);

                                                // Get the table's type
                                                // definition
                                                typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getType());

                                                // Check if the table type
                                                // doesn't exist
                                                if (typeDefn == null)
                                                {
                                                    throw new CCDDException("Unknown table type '"
                                                                            + tableDefn.getType()
                                                                            + "'");
                                                }

                                                // Get the number of expected
                                                // columns (the hidden columns,
                                                // primary key and row index,
                                                // should not be included in
                                                // the CSV file)
                                                numColumns = typeDefn.getColumnCountVisible();
                                            }
                                            // Incorrect number of inputs
                                            else
                                            {
                                                throw new CCDDException("Too many/few table name and type inputs");
                                            }

                                            break;

                                        case COLUMN_NAMES:
                                            // Check if any column names exist
                                            if (columnValues.length != 0)
                                            {
                                                // Number of columns in an
                                                // import file that match the
                                                // target table
                                                int numValidColumns = 0;

                                                // Create storage for the
                                                // column indices
                                                columnIndex = new int[columnValues.length];

                                                // Step through each column
                                                // name
                                                for (int index = 0; index < columnValues.length; index++)
                                                {
                                                    // Get the index for this
                                                    // column name
                                                    columnIndex[index] = typeDefn.getVisibleColumnIndexByUserName(columnValues[index]);

                                                    // Check if the column name
                                                    // in the file matches that
                                                    // of a column in the table
                                                    if (columnIndex[index] != -1)
                                                    {
                                                        // Increment the
                                                        // counter that tracks
                                                        // the number of
                                                        // matched columns
                                                        numValidColumns++;
                                                    }

                                                    // Check if the user hasn't
                                                    // elected to ignore any
                                                    // column discrepancies,
                                                    // and if the number of
                                                    // columns differ or the
                                                    // column name doesn't
                                                    // exist for this table
                                                    // type
                                                    if (!continueOnMismatch
                                                        && (columnValues.length != numColumns
                                                        || columnIndex[index] == -1))
                                                    {
                                                        // Get confirmation
                                                        // from the user to
                                                        // ignore the
                                                        // discrepancy
                                                        if (new CcddDialogHandler().showMessageDialog(parent,
                                                                                                      "<html><b>"
                                                                                                          + (columnIndex[index] == -1
                                                                                                                                     ? "Unknown column name '</b>"
                                                                                                                                       + columnValues[index]
                                                                                                                                       + "<b>'"
                                                                                                                                     : "Number of columns differ")
                                                                                                          + " in table '</b>"
                                                                                                          + tableDefn.getName()
                                                                                                          + "<b>'; continue?",
                                                                                                      "Column Mismatch",
                                                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                                        {
                                                            continueOnMismatch = true;
                                                        }
                                                        // The user chose to
                                                        // not ignore the
                                                        // discrepancy
                                                        else
                                                        {
                                                            // No error message
                                                            // is provided
                                                            // since the user
                                                            // chose this
                                                            // action
                                                            throw new CCDDException("");
                                                        }
                                                    }
                                                }

                                                // Check if no column names in
                                                // the file match those in the
                                                // table
                                                if (numValidColumns == 0)
                                                {
                                                    throw new CCDDException("No columns in import file<br>'</b>"
                                                                            + importFile.getAbsolutePath()
                                                                            + "<b>' match those in the target table",
                                                                            JOptionPane.WARNING_MESSAGE);
                                                }
                                            }
                                            // The file contains no column data
                                            else
                                            {
                                                throw new CCDDException("Format invalid for import file<br>'</b>"
                                                                        + importFile.getAbsolutePath()
                                                                        + "<b>'");
                                            }

                                            // Set the input type to look for
                                            // cell data
                                            importType = CSVTags.CELL_DATA;
                                            break;

                                        case CELL_DATA:
                                            // Create storage for the row of
                                            // cell data and initialize the
                                            // values to blanks
                                            String[] rowData = new String[numColumns];
                                            Arrays.fill(rowData, "");

                                            // Step through each column in the
                                            // row
                                            for (int index = 0; index < columnValues.length; index++)
                                            {
                                                // Check if the column exists
                                                if (columnIndex[index] != -1)
                                                {
                                                    // Store the cell data in
                                                    // the column matching the
                                                    // one in the target table.
                                                    // Remove any surrounding
                                                    // double quotes
                                                    rowData[columnIndex[index]] = columnValues[index].replaceAll("^\"(.*)\"$", "$1");
                                                }
                                            }

                                            // Add the row of data read in from
                                            // the file to the cell data list
                                            tableDefn.addData(rowData);
                                            break;

                                        case DESCRIPTION:
                                            // Store the table description
                                            tableDefn.setDescription(columnValues[0]);
                                            break;

                                        case DATA_FIELD:
                                            // Check if the expected number of
                                            // inputs is present
                                            if (columnValues.length == FieldsColumn.values().length - 1)
                                            {
                                                // Add the data field
                                                // definition
                                                tableDefn.addDataField(new String[] {tablePath,
                                                                                     columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                                                     columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1]});
                                            }
                                            // Incorrect number of inputs.
                                            // Check if the user hasn't already
                                            // elected to ignore mismatches
                                            else if (!continueOnMismatch)
                                            {
                                                // Get confirmation from the
                                                // user to ignore the
                                                // discrepancy
                                                if (new CcddDialogHandler().showMessageDialog(parent,
                                                                                              "<html><b>Too many/few data field inputs; continue?",
                                                                                              "Data Field Mismatch",
                                                                                              JOptionPane.QUESTION_MESSAGE,
                                                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                                {
                                                    continueOnMismatch = true;
                                                }
                                                // The user chose to not ignore
                                                // the discrepancy
                                                else
                                                {
                                                    // No error message is
                                                    // provided since the user
                                                    // chose this action
                                                    throw new CCDDException("");
                                                }
                                            }

                                            break;

                                        case DATA_TYPE:
                                        case MACRO:
                                        case TABLE_TYPE:
                                            break;

                                        default:
                                            // Inform the user that no tag
                                            // appears in the file before other
                                            // data
                                            throw new CCDDException("Import file <br>'</b>"
                                                                    + importFile.getAbsolutePath()
                                                                    + "<b>' information missing");
                                    }
                                }
                            }
                        }

                        // Read next line in file
                        line = br.readLine();
                    }

                    // Check if this is the second pass
                    if (loop == 2)
                    {
                        // Add the table's definition to the list
                        tableDefinitions.add(tableDefn);
                    }
                }

                // Check if this is the first pass
                if (loop == 1)
                {
                    // Add the table type if it's new or match it to an
                    // existing one with the same name if the type definitions
                    // are the same
                    String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefinitions);

                    // Check if a table type isn't new and doesn't match an
                    // existing one with the same name
                    if (badDefn != null)
                    {
                        throw new CCDDException("Imported table type '"
                                                + badDefn
                                                + "' doesn't match the existing definition");
                    }

                    // Add the data type if it's new or match it to an existing
                    // one with the same name if the type definitions are the
                    // same
                    badDefn = dataTypeHandler.updateDataTypes(dataTypeDefinitions);

                    // Check if a data type isn't new and doesn't match an
                    // existing one with the same name
                    if (badDefn != null)
                    {
                        throw new CCDDException("Imported data type '"
                                                + badDefn
                                                + "' doesn't match the existing definition");
                    }

                    // Add the macro if it's new or match it to an existing one
                    // with the same name if the values are the same
                    badDefn = macroHandler.updateMacros(macroDefinitions);

                    // Check if a macro isn't new and doesn't match an existing
                    // one with the same name
                    if (badDefn != null)
                    {
                        throw new CCDDException("Imported macro '"
                                                + badDefn
                                                + "' doesn't match the existing definition");
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
                                                          "<html><b>Cannot close import file<br>'</b>"
                                                              + importFile.getAbsolutePath()
                                                              + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**************************************************************************
     * Export the project in CSV format to the specified file
     * 
     * @param exportFile
     *            reference to the user-specified output file
     * 
     * @param tableNames
     *            array of table names to convert
     * 
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding
     *            values
     * 
     * @param extraInfo
     *            [0] name of the data field containing the system name
     * 
     * @return true if an error occurred preventing exporting the project to
     *         the file
     *************************************************************************/
    @Override
    public boolean exportToFile(File exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                String... extraInfo)
    {
        boolean errorFlag = false;
        boolean addLineFeed = false;
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        systemFieldName = extraInfo[0];

        try
        {
            List<String> referencedTableTypes = new ArrayList<String>();
            List<String> referencedDataTypes = new ArrayList<String>();
            List<String> referencedMacros = new ArrayList<String>();

            // Output the table data to the selected file. Multiple writers are
            // needed in case tables are appended to an existing file
            fw = new FileWriter(exportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            // Step through each table
            for (String tblName : tableNames)
            {
                // Get the information from the database for the specified
                // table
                TableInformation tableInfo = ccddMain.getDbTableCommandHandler().loadTableData(tblName,
                                                                                               false,
                                                                                               true,
                                                                                               false,
                                                                                               true,
                                                                                               parent);

                // Check if the table's data successfully loaded
                if (!tableInfo.isErrorFlag())
                {
                    // Output the file creation information (for the first pass
                    // only)
                    pw.printf((!addLineFeed
                                           ? "# Created "
                                             + new Date().toString()
                                             + " : project = "
                                             + dbControl.getDatabase()
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

                    // Get the visible column names based on the table's type
                    String[] columnNames = typeDefn.getColumnNamesUser();

                    // Check if the flag is set that indicates macros should be
                    // replaced
                    if (replaceMacros)
                    {
                        // Replace all macro names with their corresponding
                        // values
                        tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));
                    }

                    String systemName = "";

                    // Get the table's system from the system name data field,
                    // if it exists
                    FieldInformation systemField = tableInfo.getFieldHandler().getFieldInformationByName(tableInfo.getTablePath(),
                                                                                                         systemFieldName);

                    // Check that the system data field exists and isn't empty
                    if (systemField != null && !systemField.getValue().isEmpty())
                    {
                        // Store the system name
                        systemName = ",\"" + systemField.getValue() + "\"";
                    }

                    // Output the table path (if applicable) and name, and
                    // table type
                    pw.printf(CSVTags.NAME_TYPE.getTag()
                              + "\n\"%s\",\"%s\"\n"
                              + CSVTags.COLUMN_NAMES.getTag()
                              + systemName
                              + "\n",
                              tableInfo.getTablePath(),
                              tableInfo.getType(),
                              systemName);

                    // Step through each row in the table
                    for (int row = -1; row < tableInfo.getData().length; row++)
                    {
                        boolean firstPass = true;

                        // Step through each column in the row
                        for (int column = 0; column < columnNames.length; column++)
                        {
                            // Check if the column isn't the primary key or row
                            // index
                            if (column != DefaultColumn.PRIMARY_KEY.ordinal()
                                && column != DefaultColumn.ROW_INDEX.ordinal())
                            {
                                // Check if this isn't the first column
                                if (!firstPass)
                                {
                                    // Append a comma to separate the data
                                    // values
                                    pw.printf(",");
                                }

                                // Set the flag for subsequent columns
                                firstPass = false;

                                // Store the column name (if the first row) or
                                // data value (subsequent rows). Surround the
                                // text with quotes; during file import the
                                // quotes are necessary in the event the text
                                // contains a comma
                                pw.printf("\"%s\"",
                                          (row == -1)
                                                     ? columnNames[column]
                                                     : tableInfo.getData()[row][column]);

                                // Check if this is not the column name row
                                if (row != -1)
                                {
                                    List<Integer> dataTypeColumns = new ArrayList<Integer>();

                                    // Get the column indices for all columns
                                    // that can contain a primitive data type
                                    dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(InputDataType.PRIM_AND_STRUCT));
                                    dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(InputDataType.PRIMITIVE));

                                    // Step through each data type column
                                    for (int dataTypeColumn : dataTypeColumns)
                                    {
                                        // Get the value in the data type
                                        // column
                                        String dataTypeName = tableInfo.getData()[row][dataTypeColumn].toString();

                                        // Check if the data type is a
                                        // primitive and isn't already in the
                                        // list
                                        if (dataTypeHandler.isPrimitive(dataTypeName)
                                            && !referencedDataTypes.contains(dataTypeName))
                                        {
                                            // Add the data type name to the
                                            // list of references data types
                                            referencedDataTypes.add(dataTypeName);
                                        }
                                    }

                                    // Get the names of the macros referenced
                                    // in the cell and add them to the list
                                    referencedMacros.addAll(macroHandler.getReferencedMacros(tableInfo.getData()[row][column].toString()));
                                }
                            }
                        }

                        // Append a line feed to end the row
                        pw.println();
                    }

                    // Check if the table has a description
                    if (!tableInfo.getDescription().isEmpty())
                    {
                        // Output the table description header and description
                        pw.printf(CSVTags.DESCRIPTION.getTag() + "\n\"%s\"\n",
                                  tableInfo.getDescription());
                    }

                    // Get the table's data field information
                    List<FieldInformation> fieldInformation = tableInfo.getFieldHandler().getFieldInformation();

                    // Check if the table contains any data fields
                    if (!fieldInformation.isEmpty())
                    {
                        // Output the data field marker
                        pw.printf(CSVTags.DATA_FIELD.getTag() + "\n");

                        // Step through each data field
                        for (FieldInformation fieldInfo : fieldInformation)
                        {
                            // Output the field information
                            pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                      fieldInfo.getFieldName(),
                                      fieldInfo.getDescription(),
                                      fieldInfo.getSize(),
                                      fieldInfo.getInputType().getInputName(),
                                      fieldInfo.isRequired(),
                                      fieldInfo.getApplicabilityType(),
                                      fieldInfo.getValue());
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

                    // Output the table type tag, and the type name and
                    // description
                    pw.printf("\n" + CSVTags.TABLE_TYPE.getTag() + "\n\"%s\",\"%s\"\n",
                              tableTypeDefn.getName(),
                              tableTypeDefn.getDescription());

                    // Step through each column defined for the table type,
                    // skipping the primary key and row index columns
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                    {
                        // Output the column definition
                        pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                  tableTypeDefn.getColumnNamesUser()[column],
                                  tableTypeDefn.getColumnToolTips()[column],
                                  tableTypeDefn.getInputTypes()[column].getInputName(),
                                  tableTypeDefn.isRowValueUnique()[column],
                                  tableTypeDefn.isRequired()[column],
                                  tableTypeDefn.isStructureAllowed()[column],
                                  tableTypeDefn.isPointerAllowed()[column]);
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
                        pw.printf("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                  dataType[DataTypesColumn.USER_NAME.ordinal()],
                                  dataType[DataTypesColumn.C_NAME.ordinal()],
                                  dataType[DataTypesColumn.SIZE.ordinal()],
                                  dataType[DataTypesColumn.BASE_TYPE.ordinal()]);

                    }
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
                        pw.printf("\"%s\",\"%s\"\n", macro[0], macro[1]);
                    }
                }
            }
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be written to
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot write to export file<br>'</b>"
                                                          + exportFile.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
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
                                                          "<html><b>Cannot close export file<br>'</b>"
                                                              + exportFile.getAbsolutePath()
                                                              + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }

        return errorFlag;
    }
}
