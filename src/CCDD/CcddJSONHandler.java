/**
 * CFS Command & Data Dictionary JSON handler. Copyright 2017 United States
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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JOptionPane;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.MacroEditorColumnInfo;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary JSON handler class
 *****************************************************************************/
public class CcddJSONHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddMacroHandler macroHandler;
    private TableInformation tableInfo;

    // GUI component instantiating this class
    private final Component parent;

    // Name of the data field containing the system name
    private String systemFieldName;

    // List containing the imported table, table type, data type, and macro
    // definitions
    private List<TableDefinition> tableDefinitions;

    /**************************************************************************
     * JSON data type tags
     *************************************************************************/
    protected enum JSONTags
    {
        FILE_DESCRIPTION("File Description"),
        DATA_TYPE_DEFN("Data Type Definition"),
        TABLE_TYPE_DEFN("Table Type Definition"),
        TABLE_TYPE_NAME("Table Type Name"),
        TABLE_TYPE_DESCRIPTION("Table Type Description"),
        TABLE_TYPE_COLUMN("Table Type Column"),
        MACRO_DEFN("Macro Definition"),
        TABLE_DEFN("Table Definition"),
        TABLE_NAME("Table Name"),
        TABLE_TYPE("Table Type"),
        TABLE_DESCRIPTION("Table Description"),
        TABLE_DATA("Table Data"),
        TABLE_SYSTEM("System Name"),
        DATA_FIELD("Data Field");

        private final String tag;

        /**********************************************************************
         * JSON data type tags constructor
         * 
         * @param tag
         *            text describing the data
         *********************************************************************/
        JSONTags(String tag)
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
     * JSON handler class constructor
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddJSONHandler(CcddMain ccddMain, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;
        this.dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
    }

    /**************************************************************************
     * Get the status of the conversion setup error flag
     * 
     * @return Always returns false for the JSON conversion
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
     * Get the string representation of an object; return a blank if the object
     * is null
     * 
     * @param obj
     *            object to convert
     * 
     * @return String representation of the supplied object; blank if the
     *         object is null
     *************************************************************************/
    private String getString(Object obj)
    {
        String str = "";

        // Check if the object isn't null
        if (obj != null)
        {
            // Get the string representation of the object
            str = obj.toString();
        }

        return str;
    }

    /**************************************************************************
     * Parse the supplied JSON array into a list of its constituent JSON
     * objects
     * 
     * @param arrayObj
     *            object representing a JSON array
     * 
     * @return List containing the parsed JSON objects in the supplied JSON
     *         array
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private List<JSONObject> parseJSONArray(Object arrayObj) throws ParseException
    {
        List<JSONObject> results = new ArrayList<JSONObject>();

        JSONArray objectJA = (JSONArray) arrayObj;

        // Step through each item in the JSON array
        for (int index = 0; index < objectJA.size(); index++)
        {
            // Check if the array member is a JSON object (i.e., in the format
            // {key, value})
            if (objectJA.get(index) instanceof JSONObject)
            {
                // Parse the JSON object and add it to the results list
                results.add(parseJSONObject((JSONObject) objectJA.get(index)));
            }
            // Not a JSON object; i.e., it's a string representing an array of
            // items (column names, for example)
            else
            {
                // Create a JSON object in which to store the string, then add
                // it to the results list
                JSONObject jo = new JSONObject();
                jo.put(index, objectJA.get(index));
                results.add(jo);
            }
        }

        return results;
    }

    /**************************************************************************
     * Parse the supplied JSON object
     * 
     * @param jsonObj
     *            JSON object
     * 
     * @return Parsed JSON object
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONObject parseJSONObject(JSONObject jsonObj) throws ParseException
    {
        JSONObject resultJO = new JSONObject();

        // Get the keys for the JSON map and the iterator for the mapped items
        Set<Object> set = jsonObj.keySet();
        Iterator<Object> iterator = set.iterator();

        // Step through each item in the JSON object
        while (iterator.hasNext())
        {
            Object obj = iterator.next();

            // Check if the item is a JSON array
            if (jsonObj.get(obj) instanceof JSONArray)
            {
                // Add the JSON array to the result (don't parse the array here
                resultJO.put(obj.toString(), jsonObj.get(obj));
            }
            // Check if the item is a JSON object
            else if (jsonObj.get(obj) instanceof JSONObject)
            {
                // Parse the JSON object and add it to the result
                resultJO.put(obj.toString(),
                             parseJSONObject((JSONObject) jsonObj.get(obj)));
            }
            // Item isn't a JSON array or object (i.e., it's a string)
            else
            {
                // Add the item to the result
                resultJO.put(obj.toString(), jsonObj.get(obj));
            }
        }

        return resultJO;
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

            // Flag indicating if importing should continue after an input
            // mismatch is detected
            boolean continueOnMismatch = false;

            // Create a JSON parser and use it to parse the import file
            // contents
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(importFile));

            // Get the table type definitions JSON object
            Object defn = jsonObject.get(JSONTags.TABLE_TYPE_DEFN.getTag());

            // Check if the table type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each table type definition
                for (JSONObject tableTypeJO : parseJSONArray(defn))
                {
                    // Get the table type definition components
                    String typeName = getString(tableTypeJO.get(JSONTags.TABLE_TYPE_NAME.getTag()));
                    String typeDesc = getString(tableTypeJO.get(JSONTags.TABLE_TYPE_DESCRIPTION.getTag()));
                    Object typeColumn = tableTypeJO.get(JSONTags.TABLE_TYPE_COLUMN.getTag());

                    // Check if the expected inputs are present
                    if (!typeName.isEmpty()
                        && typeColumn != null
                        && typeColumn instanceof JSONArray)
                    {
                        // Create a new table type definition
                        TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                                    typeDesc);

                        int columnNumber = 0;

                        // Step through each table type column definition
                        for (JSONObject typeJO : parseJSONArray(typeColumn))
                        {
                            // Get the table type column definition components
                            String name = getString(typeJO.get(TableTypeEditorColumnInfo.NAME.getColumnName()));
                            String description = getString(typeJO.get(TableTypeEditorColumnInfo.DESCRIPTION.getColumnName()));
                            String inputType = getString(typeJO.get(TableTypeEditorColumnInfo.INPUT_TYPE.getColumnName()));
                            String unique = getString(typeJO.get(TableTypeEditorColumnInfo.UNIQUE.getColumnName()));
                            String required = getString(typeJO.get(TableTypeEditorColumnInfo.REQUIRED.getColumnName()));
                            String structAllow = getString(typeJO.get(TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.getColumnName()));
                            String ptrAllow = getString(typeJO.get(TableTypeEditorColumnInfo.POINTER_ALLOWED.getColumnName()));

                            // Check if the expected input is present
                            if (!name.isEmpty())
                            {
                                // Check if the input type is empty
                                if (inputType.isEmpty())
                                {
                                    // Default to text
                                    inputType = InputDataType.TEXT.getInputName();
                                }

                                // Check if the 'unique' flag is empty
                                if (unique.isEmpty())
                                {
                                    // Default to 'false'
                                    unique = "false";
                                }

                                // Check if the 'structure allowed' flag is
                                // empty
                                if (structAllow.isEmpty())
                                {
                                    // Default to 'false'
                                    structAllow = "false";
                                }

                                // Check if the 'pointer allowed' flag is empty
                                if (ptrAllow.isEmpty())
                                {
                                    // Default to 'false'
                                    ptrAllow = "false";
                                }

                                // Add the table type column definition
                                tableTypeDefn.addColumn(new Object[] {columnNumber,
                                                                      name,
                                                                      description,
                                                                      inputType,
                                                                      Boolean.valueOf(unique),
                                                                      Boolean.valueOf(required),
                                                                      Boolean.valueOf(structAllow),
                                                                      Boolean.valueOf(ptrAllow)});
                                columnNumber++;
                            }
                            // Incorrect number of inputs. Check if the user
                            // hasn't already elected to ignore mismatches
                            else if (!continueOnMismatch)
                            {
                                // Get confirmation from the user to ignore the
                                // discrepancy
                                if (new CcddDialogHandler().showMessageDialog(parent,
                                                                              "<html><b>Missing table type name; continue?",
                                                                              "Table Type Mismatch",
                                                                              JOptionPane.QUESTION_MESSAGE,
                                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                {
                                    continueOnMismatch = true;
                                }
                                // The user chose to not ignore the discrepancy
                                else
                                {
                                    // No error message is provided since the
                                    // user chose this action
                                    throw new CCDDException("");
                                }
                            }
                        }

                        // Add the table type definition to the list
                        tableTypeDefinitions.add(tableTypeDefn);
                    }
                }
            }

            // Get the data type definitions JSON object
            defn = jsonObject.get(JSONTags.DATA_TYPE_DEFN.getTag());

            // Check if the data type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each data type definition
                for (JSONObject dataTypeJO : parseJSONArray(defn))
                {
                    // Get the data type definition components
                    String userName = getString(dataTypeJO.get(DataTypeEditorColumnInfo.USER_NAME.getColumnName()));
                    String cName = getString(dataTypeJO.get(DataTypeEditorColumnInfo.C_NAME.getColumnName()));
                    String size = getString(dataTypeJO.get(DataTypeEditorColumnInfo.SIZE.getColumnName()));
                    String baseType = getString(dataTypeJO.get(DataTypeEditorColumnInfo.BASE_TYPE.getColumnName()));

                    // Check if the expected inputs are present
                    if ((!userName.isEmpty() || !cName.isEmpty())
                        && !size.isEmpty()
                        && !baseType.isEmpty())
                    {
                        // Add the data type definition (add a blank to
                        // represent the OID)
                        dataTypeDefinitions.add(new String[] {userName,
                                                              cName,
                                                              size,
                                                              baseType,
                                                              ""});
                    }
                    // Incorrect number of inputs. Check if the user
                    // hasn't already elected to ignore mismatches
                    else if (!continueOnMismatch)
                    {
                        // Get confirmation from the user to ignore the
                        // discrepancy
                        if (new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>Missing data type input(s); continue?",
                                                                      "Data Type Mismatch",
                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            continueOnMismatch = true;
                        }
                        // The user chose to not ignore the discrepancy
                        else
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException("");
                        }
                    }
                }
            }

            // Get the macro definitions JSON object
            defn = jsonObject.get(JSONTags.MACRO_DEFN.getTag());

            // Check if the macro definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each macro definition
                for (JSONObject macroJO : parseJSONArray(defn))
                {
                    // Get the macro definition components
                    String name = getString(macroJO.get(MacroEditorColumnInfo.NAME.getColumnName()));
                    String value = getString(macroJO.get(MacroEditorColumnInfo.VALUE.getColumnName()));

                    // Check if the expected input is present
                    if (!name.isEmpty())
                    {
                        // Add the macro definition (add a blank to represent
                        // the OID)
                        macroDefinitions.add(new String[] {name, value, ""});
                    }
                    // Incorrect number of inputs. Check if the user
                    // hasn't already elected to ignore mismatches
                    else if (!continueOnMismatch)
                    {
                        // Get confirmation from the user to ignore the
                        // discrepancy
                        if (new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>Missing macro name; continue?",
                                                                      "Macro Mismatch",
                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            continueOnMismatch = true;
                        }
                        // The user chose to not ignore the discrepancy
                        else
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException("");
                        }
                    }
                }
            }

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

            // Get the table definitions JSON object
            defn = jsonObject.get(JSONTags.TABLE_DEFN.getTag());

            // Check if the table definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each table definition
                for (JSONObject tableJO : parseJSONArray(defn))
                {
                    // Get the table definition components
                    String tableName = getString(tableJO.get(JSONTags.TABLE_NAME.getTag()));
                    String tableType = getString(tableJO.get(JSONTags.TABLE_TYPE.getTag()));
                    String tableDesc = getString(tableJO.get(JSONTags.TABLE_DESCRIPTION.getTag()));
                    Object tableDataJA = tableJO.get(JSONTags.TABLE_DATA.getTag());
                    Object dataFieldsJA = tableJO.get(JSONTags.DATA_FIELD.getTag());

                    // Check if the expected inputs are present
                    if (!tableName.isEmpty()
                        && tableDataJA != null && tableDataJA instanceof JSONArray
                        && (dataFieldsJA == null || dataFieldsJA instanceof JSONArray))
                    {
                        // Create a new table type definition
                        TableDefinition tableDefn = new TableDefinition(tableName,
                                                                        tableDesc);

                        // Get the table's type definition
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

                        // Check if the table type doesn't exist
                        if (typeDefn == null)
                        {
                            throw new CCDDException("Unknown table type '"
                                                    + tableDefn.getType()
                                                    + "'");
                        }

                        // Get the number of expected columns (the hidden
                        // columns, primary key and row index, should not be
                        // included in the JSON file)
                        int numColumns = typeDefn.getColumnCountVisible();

                        // Create storage for the row of cell data
                        String[] rowData = new String[numColumns];

                        // Step through each row of data
                        for (JSONObject rowDataJO : parseJSONArray(tableDataJA))
                        {
                            // Step through each expected column
                            for (int column = 0; column < numColumns; column++)
                            {
                                // Get the value from the JSON input, if
                                // present; use a blank if a value for this
                                // column doesn't exist
                                rowData[column] = getString(rowDataJO.get(typeDefn.getColumnNamesVisible()[column]));
                            }

                            // Add the row of data read in from the file to the
                            // cell data list
                            tableDefn.addData(rowData);
                        }

                        // Check if any data fields are defined
                        if (dataFieldsJA != null)
                        {
                            // Step through each data field definition
                            for (JSONObject dataFieldJO : parseJSONArray(dataFieldsJA))
                            {
                                // Get the data field definition components
                                String name = getString(dataFieldJO.get(FieldEditorColumnInfo.NAME.getColumnName()));
                                String description = getString(dataFieldJO.get(FieldEditorColumnInfo.DESCRIPTION.getColumnName()));
                                String size = getString(dataFieldJO.get(FieldEditorColumnInfo.SIZE.getColumnName()));
                                String inputType = getString(dataFieldJO.get(FieldEditorColumnInfo.INPUT_TYPE.getColumnName()));
                                String required = getString(dataFieldJO.get(FieldEditorColumnInfo.REQUIRED.getColumnName()));
                                String applicability = getString(dataFieldJO.get(FieldEditorColumnInfo.APPLICABILITY.getColumnName()));
                                String value = getString(dataFieldJO.get(FieldEditorColumnInfo.VALUE.getColumnName()));

                                // Check if the expected input is present
                                if (!name.isEmpty() && !size.isEmpty())
                                {
                                    // Check if the field size is empty
                                    if (size.isEmpty())
                                    {
                                        // Default to ten characters
                                        size = "10";
                                    }

                                    // Check if the input type is empty
                                    if (inputType.isEmpty())
                                    {
                                        // Default to text
                                        inputType = InputDataType.TEXT.getInputName();
                                    }

                                    // Check if the 'required' flag is empty
                                    if (required.isEmpty())
                                    {
                                        // Default to 'false'
                                        required = "false";
                                    }

                                    // Check if applicability is empty
                                    if (applicability.isEmpty())
                                    {
                                        // Default to all tables
                                        applicability = ApplicabilityType.ALL.getApplicabilityName();
                                    }

                                    // Add the data field definition
                                    tableDefn.addDataField(new String[] {tableName,
                                                                         name,
                                                                         description,
                                                                         size,
                                                                         inputType,
                                                                         required,
                                                                         applicability,
                                                                         value});
                                }
                                // Incorrect number of inputs. Get confirmation
                                // from the user to ignore the discrepancy
                                else if (new CcddDialogHandler().showMessageDialog(parent,
                                                                                   "<html><b>Missing data field name in table<br>'</b>"
                                                                                       + tableName
                                                                                       + "<b>'; continue?",
                                                                                   "Data Field Mismatch",
                                                                                   JOptionPane.QUESTION_MESSAGE,
                                                                                   DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                {
                                    continueOnMismatch = true;
                                }
                                // The user chose to not ignore the discrepancy
                                else
                                {
                                    // No error message is provided since the
                                    // user chose this action
                                    throw new CCDDException("");
                                }
                            }
                        }

                        // Add the table's definition to the list
                        tableDefinitions.add(tableDefn);
                    }
                }
            }
        }
        catch (ParseException pe)
        {
            // Inform the user that the file cannot be closed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot parse import file<br>'</b>"
                                                          + importFile.getAbsolutePath()
                                                          + "<b>'; cause '"
                                                          + pe.getMessage()
                                                          + "'",
                                                      "File Warning",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
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
     * Export the project in JSON format to the specified file
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
    @SuppressWarnings("unchecked")
    @Override
    public boolean exportToFile(File exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                String... extraInfo)
    {
        boolean errorFlag = false;
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

            // Create the file creation comment
            JSONObject outputJO = new JSONObject();
            outputJO.put(JSONTags.FILE_DESCRIPTION.getTag(),
                         "Created "
                             + new Date().toString()
                             + " : project = "
                             + dbControl.getDatabase()
                             + " : host = "
                             + dbControl.getServer()
                             + " : user = "
                             + dbControl.getUser());

            if (tableNames.length != 0)
            {
                JSONArray tableJA = new JSONArray();

                // Create a data field handler
                CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                                     null,
                                                                     ccddMain.getMainFrame());

                // Step through each table
                for (String tblName : tableNames)
                {
                    // Get the table's information
                    JSONObject tableInfoJO = getTableInformation(tblName,
                                                                 fieldHandler,
                                                                 !replaceMacros);

                    // Check if the table's data successfully loaded
                    if (tableInfoJO != null)
                    {
                        // Add the wrapper for the table
                        tableJA.add(tableInfoJO);

                        // Get the table type definition based on the type name
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                        // Check if this table type is not already output
                        if (!referencedTableTypes.contains(tableInfo.getType()))
                        {
                            // Add the table type to the list of those
                            // referenced
                            referencedTableTypes.add(tableInfo.getType());
                        }

                        // Get the visible column names based on the table's
                        // type
                        String[] columnNames = typeDefn.getColumnNamesUser();

                        // Step through each row in the table
                        for (int row = -1; row < tableInfo.getData().length; row++)
                        {
                            // Step through each column in the row
                            for (int column = 0; column < columnNames.length; column++)
                            {
                                // Check if the column isn't the primary key or
                                // row index
                                if (column != DefaultColumn.PRIMARY_KEY.ordinal()
                                    && column != DefaultColumn.ROW_INDEX.ordinal())
                                {
                                    // Check if this is not the column name row
                                    if (row != -1)
                                    {
                                        List<Integer> dataTypeColumns = new ArrayList<Integer>();

                                        // Get the column indices for all
                                        // columns that can contain a primitive
                                        // data type
                                        dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(InputDataType.PRIM_AND_STRUCT));
                                        dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(InputDataType.PRIMITIVE));

                                        // Step through each data type column
                                        for (int dataTypeColumn : dataTypeColumns)
                                        {
                                            // Get the value column
                                            String dataTypeName = tableInfo.getData()[row][dataTypeColumn].toString();

                                            // Check if the data type is a
                                            // primitive and isn't already in
                                            // the list
                                            if (dataTypeHandler.isPrimitive(dataTypeName)
                                                && !referencedDataTypes.contains(dataTypeName))
                                            {
                                                // Add the data type name to
                                                // the list of references data
                                                // types
                                                referencedDataTypes.add(dataTypeName);
                                            }
                                        }

                                        // Get the names of the macros
                                        // referenced in the cell and add them
                                        // to the list
                                        referencedMacros.addAll(macroHandler.getReferencedMacros(tableInfo.getData()[row][column].toString()));
                                    }
                                }
                            }
                        }
                    }
                }

                if (tableJA != null)
                {
                    // Add the table information to the JSON output
                    outputJO.put(JSONTags.TABLE_DEFN.getTag(), tableJA);
                }
            }

            // Get the referenced table type definition(s)
            JSONArray defnJA = getTableTypeDefinitions(referencedTableTypes);

            // Check if a table type is referenced
            if (defnJA != null)
            {
                // Add the referenced table type definition(s) to the JSON
                // output
                outputJO.put(JSONTags.TABLE_TYPE_DEFN.getTag(), defnJA);
            }

            // Get the referenced data type definition(s)
            defnJA = getDataTypeDefinitions(referencedDataTypes);

            // Check if a data type is referenced
            if (defnJA != null)
            {
                // Add the referenced data type definition(s) to the JSON
                // output
                outputJO.put(JSONTags.DATA_TYPE_DEFN.getTag(), defnJA);
            }

            // Get the referenced macro definition(s)
            defnJA = getMacroDefinitions(referencedMacros);

            // Check if a macro is referenced
            if (defnJA != null)
            {
                // Add the referenced macro definition(s) to the JSON
                // output
                outputJO.put(JSONTags.MACRO_DEFN.getTag(), defnJA);
            }

            // Create a JavaScript engine for use in formatting the JSON output
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");

            // Output the formatted JSON object to the file
            scriptEngine.put("jsonString", outputJO.toString());
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");
            pw.println((String) scriptEngine.get("result"));
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
        catch (ScriptException se)
        {
            // Inform the user that formatting the JSON output failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot format JSON output using JavaScript; cause '"
                                                          + se.getMessage()
                                                          + "'",
                                                      "JavaScript Error",
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

    /**************************************************************************
     * Get the data for the specified data table
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
     * @return JSON array containing the specified table cell data; empty if a
     *         table name is specified and the table doesn't exist. Empty table
     *         cells are omitted
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONArray getTableData(String tableName,
                                   boolean getDescription,
                                   boolean showMacroNames)
    {
        JSONArray dataJA = new JSONArray();

        // Get the information from the database for the specified table
        tableInfo = dbTable.loadTableData(tableName,
                                          true,
                                          !getDescription,
                                          false,
                                          false,
                                          ccddMain.getMainFrame());

        // Check if the table exists and successfully loaded
        if (tableInfo != null && !tableInfo.isErrorFlag())
        {
            JSONObject columnJO = new JSONObject();

            // Get a reference to the table's data
            String[][] data = tableInfo.getData();

            // Check if the macro name should be replaced with the
            // corresponding macro values
            if (!showMacroNames)
            {
                // Step through each row
                for (int row = 0; row < data.length && !tableInfo.isErrorFlag(); row++)
                {
                    // Step through each column (skipping the primary key and
                    // row index)
                    for (int column = NUM_HIDDEN_COLUMNS; column < data[row].length; column++)
                    {
                        // Expand any embedded macros
                        data[row][column] = macroHandler.getMacroExpansion(data[row][column]);
                    }
                }
            }

            // Check if the table has any data
            if (data.length != 0)
            {
                // Get the column names for this table's type definition
                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());
                String[] columnNames = typeDefn.getColumnNamesUser();

                // Step through each table row
                for (int row = 0; row < data.length; row++)
                {
                    columnJO = new JSONObject();

                    // Step through each table column
                    for (int column = NUM_HIDDEN_COLUMNS; column < data[row].length; column++)
                    {
                        // Check if a cell isn't blank
                        if (!data[row][column].isEmpty())
                        {
                            // Add the column name and value to the cell object
                            columnJO.put(columnNames[column], data[row][column]);
                        }
                    }

                    // Add the column values to the data array. An array is
                    // used to preserve the order of the rows
                    dataJA.add(columnJO);
                }
            }
        }

        return dataJA;
    }

    /**************************************************************************
     * Get the data field information for the specified table
     * 
     * @param tableName
     *            table name and path in the format
     *            rootTable[,dataType1.variable1[,...]]. If blank then every
     *            data table's data fields are returned
     * 
     * @param fieldHandler
     *            data field handler
     * 
     * @return JSON array containing the specified table's data fields; empty
     *         if the table has no data fields
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONArray getTableFields(String tableName,
                                     CcddFieldHandler fieldHandler)
    {
        JSONArray dataFieldDefnJA = new JSONArray();

        // Get the existing data fields for the specified table
        fieldHandler.buildFieldInformation(tableName);

        // Check if the table has any fields
        if (!fieldHandler.getFieldInformation().isEmpty())
        {
            JSONObject fieldJO = new JSONObject();

            // Step through the data fields for this table
            for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
            {
                fieldJO = new JSONObject();

                // Add the data field column values to the output
                fieldJO.put(FieldEditorColumnInfo.NAME.getColumnName(),
                            fieldInfo.getFieldName());
                fieldJO.put(FieldEditorColumnInfo.DESCRIPTION.getColumnName(),
                            fieldInfo.getDescription());
                fieldJO.put(FieldEditorColumnInfo.SIZE.getColumnName(),
                            fieldInfo.getDescription());
                fieldJO.put(FieldEditorColumnInfo.INPUT_TYPE.getColumnName(),
                            fieldInfo.getInputType().getInputName());
                fieldJO.put(FieldEditorColumnInfo.REQUIRED.getColumnName(),
                            fieldInfo.isRequired());
                fieldJO.put(FieldEditorColumnInfo.APPLICABILITY.getColumnName(),
                            fieldInfo.getApplicabilityType().getApplicabilityName());
                fieldJO.put(FieldEditorColumnInfo.VALUE.getColumnName(),
                            fieldInfo.getValue());
                dataFieldDefnJA.add(fieldJO);
            }
        }

        return dataFieldDefnJA;
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
     *         null if a table name is specified and the table doesn't exist
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONObject getTableInformation(String tableName,
                                           CcddFieldHandler fieldHandler,
                                           boolean showMacroNames)
    {
        JSONObject tableInformation = null;

        // Get the table's data
        JSONArray data = getTableData(tableName, false, showMacroNames);

        // Check if the table exists
        if (!data.isEmpty())
        {
            // Store the table's name, type, and description
            tableInformation = new JSONObject();
            tableInformation.put(JSONTags.TABLE_NAME.getTag(),
                                 tableName);
            tableInformation.put(JSONTags.TABLE_TYPE.getTag(),
                                 tableInfo.getType());
            tableInformation.put(JSONTags.TABLE_DESCRIPTION.getTag(),
                                 tableInfo.getDescription());

            // Get the table's system from the system name data field, if it
            // exists
            FieldInformation systemField = fieldHandler.getFieldInformationByName(tableInfo.getTablePath(),
                                                                                  systemFieldName);

            // Check that the system data field exists and isn't empty
            if (systemField != null && !systemField.getValue().isEmpty())
            {
                // Store the system name
                tableInformation.put(JSONTags.TABLE_SYSTEM.getTag(),
                                     systemField.getValue());
            }

            // Store the table's data and data fields
            tableInformation.put(JSONTags.TABLE_DATA.getTag(), data);
            tableInformation.put(JSONTags.DATA_FIELD.getTag(),
                                 getTableFields(tableName, fieldHandler));
        }

        return tableInformation;
    }

    /**************************************************************************
     * Get the table type definitions
     * 
     * @param referencedDataTypes
     *            names of the table types referenced by the selected table(s)
     * 
     * @return JSON encoded string containing the table type definitions; null
     *         if the number of parameters or their formats are incorrect
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONArray getTableTypeDefinitions(List<String> referencedTableTypes)
    {
        JSONArray tableTypeJA = null;

        // Check if any table types are referenced
        if (!referencedTableTypes.isEmpty())
        {
            JSONArray typeDefnJA = new JSONArray();
            tableTypeJA = new JSONArray();

            // Step through each referenced table type
            for (String refTableType : referencedTableTypes)
            {
                // Get the table type definition
                TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(refTableType);

                // Check if the table type exists
                if (tableTypeDefn != null)
                {
                    JSONObject tableTypeJO;

                    // Step through each column definition in the table type,
                    // skipping the primary key and row index columns
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                    {
                        tableTypeJO = new JSONObject();
                        tableTypeJO.put(TableTypeEditorColumnInfo.NAME.getColumnName(),
                                        tableTypeDefn.getColumnNamesUser()[column]);
                        tableTypeJO.put(TableTypeEditorColumnInfo.DESCRIPTION.getColumnName(),
                                        tableTypeDefn.getColumnToolTips()[column]);
                        tableTypeJO.put(TableTypeEditorColumnInfo.INPUT_TYPE.getColumnName(),
                                        tableTypeDefn.getInputTypes()[column].getInputName());
                        tableTypeJO.put(TableTypeEditorColumnInfo.UNIQUE.getColumnName(),
                                        tableTypeDefn.isRowValueUnique()[column]);
                        tableTypeJO.put(TableTypeEditorColumnInfo.REQUIRED.getColumnName(),
                                        tableTypeDefn.isRequired()[column]);
                        tableTypeJO.put(TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.getColumnName(),
                                        tableTypeDefn.isStructureAllowed()[column]);
                        tableTypeJO.put(TableTypeEditorColumnInfo.POINTER_ALLOWED.getColumnName(),
                                        tableTypeDefn.isPointerAllowed()[column]);

                        // Add the table type definition to the array
                        typeDefnJA.add(tableTypeJO);
                    }

                    // Add the wrapper for the table type name
                    tableTypeJO = new JSONObject();
                    tableTypeJO.put(JSONTags.TABLE_TYPE_NAME.getTag(),
                                    refTableType);
                    tableTypeJO.put(JSONTags.TABLE_TYPE_DESCRIPTION.getTag(),
                                    tableTypeDefn.getDescription());
                    tableTypeJO.put(JSONTags.TABLE_TYPE_COLUMN.getTag(),
                                    typeDefnJA);
                    tableTypeJA.add(tableTypeJO);
                }
            }
        }

        return tableTypeJA;
    }

    /**************************************************************************
     * Get the data type definitions
     * 
     * @param referencedDataTypes
     *            names of the data types referenced by the selected table(s)
     * 
     * @return JSON encoded string containing the data type definitions; null
     *         if the number of parameters or their formats are incorrect
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONArray getDataTypeDefinitions(List<String> referencedDataTypes)
    {
        JSONArray dataTypeJA = null;

        // Check if any data types are referenced
        if (!referencedDataTypes.isEmpty())
        {
            dataTypeJA = new JSONArray();

            // Step through each referenced table type
            for (String refDataType : referencedDataTypes)
            {
                // Get the data type information
                String[] dataType = dataTypeHandler.getDataTypeInfo(refDataType);

                // Check if the data type exists
                if (dataType != null)
                {
                    // Store the data type user-defined name, C-language name,
                    // size, and base type
                    JSONObject dataTypeJO = new JSONObject();
                    dataTypeJO.put(DataTypeEditorColumnInfo.USER_NAME.getColumnName(),
                                   dataType[DataTypesColumn.USER_NAME.ordinal()]);
                    dataTypeJO.put(DataTypeEditorColumnInfo.C_NAME.getColumnName(),
                                   dataType[DataTypesColumn.C_NAME.ordinal()]);
                    dataTypeJO.put(DataTypeEditorColumnInfo.SIZE.getColumnName(),
                                   dataType[DataTypesColumn.SIZE.ordinal()]);
                    dataTypeJO.put(DataTypeEditorColumnInfo.BASE_TYPE.getColumnName(),
                                   dataType[DataTypesColumn.BASE_TYPE.ordinal()]);

                    // Add the data type definition to the array
                    dataTypeJA.add(dataTypeJO);
                }
            }
        }

        return dataTypeJA;
    }

    /**************************************************************************
     * Get the macro definitions
     * 
     * @param referencedMacros
     *            names of the macros referenced by the selected table(s)
     * 
     * @return JSON encoded string containing the macro definitions; null if
     *         the number of parameters or their formats are incorrect
     *************************************************************************/
    @SuppressWarnings("unchecked")
    private JSONArray getMacroDefinitions(List<String> referencedMacros)
    {
        JSONArray macroJA = null;

        // Check if any macros are referenced
        if (!referencedMacros.isEmpty())
        {
            macroJA = new JSONArray();

            // Step through each referenced macro
            for (String refMacro : referencedMacros)
            {
                // Get the macro value
                String value = macroHandler.getMacroValue(refMacro);

                // Check if the macro exists
                if (value != null)
                {
                    // Store the macro name and value
                    JSONObject macroJO = new JSONObject();
                    macroJO.put(MacroEditorColumnInfo.NAME.getColumnName(),
                                refMacro);
                    macroJO.put(MacroEditorColumnInfo.VALUE.getColumnName(),
                                value);

                    // Add the macro definition to the array
                    macroJA.add(macroJO);
                }
            }
        }

        return macroJA;
    }
}
