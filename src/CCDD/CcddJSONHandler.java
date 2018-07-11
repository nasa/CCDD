/**
 * CFS Command & Data Dictionary JSON handler.
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

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputTypeEditorColumnInfo;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.JSONTags;
import CCDD.CcddConstants.MacroEditorColumnInfo;
import CCDD.CcddConstants.MessageIDSortOrder;
import CCDD.CcddConstants.MsgIDListColumnIndex;
import CCDD.CcddConstants.MsgIDTableColumnInfo;
import CCDD.CcddConstants.ReservedMsgIDEditorColumnInfo;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary JSON handler class
 *************************************************************************************************/
public class CcddJSONHandler extends CcddImportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private TableInformation tableInfo;
    private final CcddFieldHandler fieldHandler;

    // GUI component instantiating this class
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    /**********************************************************************************************
     * JSON handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param fieldHandler
     *            reference to a data field handler
     *
     * @param parent
     *            GUI component instantiating this class
     *********************************************************************************************/
    CcddJSONHandler(CcddMain ccddMain, CcddFieldHandler fieldHandler, Component parent)
    {
        this.ccddMain = ccddMain;
        this.fieldHandler = fieldHandler;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
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
     * Get the JSON object referred to by the supplied key from the supplied JSON object
     *
     * @param jsonObj
     *            JSON object
     *
     * @param key
     *            JSON object key
     *
     * @return JSON object referred to by the supplied key; null if the JSON object with the
     *         supplied key does not exist
     *********************************************************************************************/
    private Object getObject(JSONObject jsonObj, String key)
    {
        return jsonObj.get(key);
    }

    /**********************************************************************************************
     * Get the string representation of an object; return a blank if the object is null
     *
     * @param jsonObj
     *            JSON object
     *
     * @param key
     *            JSON object key
     *
     * @return String representation of the supplied object; blank if the object is null
     *********************************************************************************************/
    private String getString(JSONObject jsonObj, String key)
    {
        String str = "";

        // Get the JSON object referred to by the supplied key
        Object obj = getObject(jsonObj, key);

        // Check if the object exists
        if (obj != null)
        {
            // Get the string representation of the object
            str = obj.toString();
        }

        return str;
    }

    /**********************************************************************************************
     * Parse the supplied JSON array into a list of its constituent JSON objects
     *
     * @param arrayObj
     *            object representing a JSON array
     *
     * @return List containing the parsed JSON objects in the supplied JSON array
     *
     * @throws ParseException
     *             If an error occurs while attempting to parse the JSON object
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    private List<JSONObject> parseJSONArray(Object arrayObj) throws ParseException
    {
        List<JSONObject> results = new ArrayList<JSONObject>();

        JSONArray objectJA = (JSONArray) arrayObj;

        // Step through each item in the JSON array
        for (int index = 0; index < objectJA.size(); index++)
        {
            // Check if the array member is a JSON object (i.e., in the format {key, value})
            if (objectJA.get(index) instanceof JSONObject)
            {
                // Parse the JSON object and add it to the results list
                results.add(parseJSONObject((JSONObject) objectJA.get(index)));
            }
            // Not a JSON object; i.e., it's a string representing an array of items (column names,
            // for example)
            else
            {
                // Create a JSON object in which to store the string, then add it to the results
                // list
                JSONObject jo = new JSONObject();
                jo.put(index, objectJA.get(index));
                results.add(jo);
            }
        }

        return results;
    }

    /**********************************************************************************************
     * Parse the supplied JSON object
     *
     * @param jsonObj
     *            JSON object
     *
     * @return Parsed JSON object
     *
     * @throws ParseException
     *             If an error occurs while attempting to parse the JSON object
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile
     *            import file reference
     *
     * @param importAll
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
            List<TableTypeDefinition> tableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            tableDefinitions = new ArrayList<TableDefinition>();
            List<String[]> inputTypeDefns = new ArrayList<String[]>();

            // Flags indicating if importing should continue after an input error is detected
            boolean continueOnTableTypeError = false;
            boolean continueOnDataTypeError = false;
            boolean continueOnInputTypeError = false;
            boolean continueOnMacroError = false;
            boolean continueOnReservedMsgIDError = false;
            boolean continueOnProjectFieldError = false;
            boolean continueOnColumnError = false;
            boolean continueOnDataFieldError = false;
            boolean continueOnTableTypeFieldError = false;

            // Create a JSON parser and use it to parse the import file contents
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(importFile));

            // Get the input type definitions JSON object
            Object defn = jsonObject.get(JSONTags.INPUT_TYPE_DEFN.getTag());

            // Check if the input type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each input type definition
                for (JSONObject typeJO : parseJSONArray(defn))
                {
                    // Get the input type definition components
                    String name = getString(typeJO,
                                            InputTypeEditorColumnInfo.NAME.getColumnName());
                    String description = getString(typeJO,
                                                   InputTypeEditorColumnInfo.DESCRIPTION.getColumnName());
                    String match = getString(typeJO,
                                             InputTypeEditorColumnInfo.MATCH.getColumnName());
                    String items = getString(typeJO,
                                             InputTypeEditorColumnInfo.ITEMS.getColumnName());
                    String format = getString(typeJO,
                                              InputTypeEditorColumnInfo.FORMAT.getColumnName());

                    // Check if the expected inputs are present
                    if (!name.isEmpty()
                        && !match.isEmpty()
                        && typeJO.keySet().size() < InputTypeEditorColumnInfo.values().length)
                    {
                        // Add the input type definition (add a blank to represent the OID)
                        inputTypeDefns.add(new String[] {name,
                                                         description,
                                                         match,
                                                         items,
                                                         format,
                                                         ""});
                    }
                    // The number of inputs is incorrect
                    else
                    {
                        // Check if the error should be ignored or the import canceled
                        continueOnInputTypeError = getErrorResponse(continueOnInputTypeError,
                                                                    "<html><b>Missing or extra input type definition "
                                                                                              + "input(s) in import file '</b>"
                                                                                              + importFile.getAbsolutePath()
                                                                                              + "<b>'; continue?",
                                                                    "Input Type Error",
                                                                    "Ignore this data type",
                                                                    "Ignore this and any remaining invalid input types",
                                                                    "Stop importing",
                                                                    parent);
                    }
                }
            }

            // Add the input type if it's new or match it to an existing one with the same name if
            // the type definitions are the same
            inputTypeHandler.updateInputTypes(inputTypeDefns);

            // Get the table type definitions JSON object
            defn = jsonObject.get(JSONTags.TABLE_TYPE_DEFN.getTag());

            // Check if the table type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each table type definition
                for (JSONObject tableTypeJO : parseJSONArray(defn))
                {
                    // Get the table type definition components
                    String typeName = getString(tableTypeJO, JSONTags.TABLE_TYPE_NAME.getTag());
                    String typeDesc = getString(tableTypeJO,
                                                JSONTags.TABLE_TYPE_DESCRIPTION.getTag());
                    Object typeColumn = getObject(tableTypeJO,
                                                  JSONTags.TABLE_TYPE_COLUMN.getTag());

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
                            // Check if the expected input is present
                            if (typeJO.keySet().size() == TableTypeEditorColumnInfo.values().length - 1)
                            {
                                // Add the table type column definition, checking for (and if
                                // possible, correcting) errors
                                continueOnTableTypeError = addImportedTableTypeColumnDefinition(continueOnTableTypeError,
                                                                                                tableTypeDefn,
                                                                                                new String[] {String.valueOf(columnNumber),
                                                                                                              getString(typeJO,
                                                                                                                        TableTypeEditorColumnInfo.NAME.getColumnName()),
                                                                                                              getString(typeJO,
                                                                                                                        TableTypeEditorColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                              getString(typeJO,
                                                                                                                        TableTypeEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                              getString(typeJO,
                                                                                                                        TableTypeEditorColumnInfo.UNIQUE.getColumnName()),
                                                                                                              getString(typeJO,
                                                                                                                        TableTypeEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                              getString(typeJO,
                                                                                                                        CcddUtilities.removeHTMLTags(TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.getColumnName())),
                                                                                                              getString(typeJO,
                                                                                                                        CcddUtilities.removeHTMLTags(TableTypeEditorColumnInfo.POINTER_ALLOWED.getColumnName()))},
                                                                                                importFile.getAbsolutePath(),
                                                                                                inputTypeHandler,
                                                                                                parent);

                                // Update the column index number for the next column definition
                                columnNumber++;
                            }
                            // The number of inputs is incorrect
                            else
                            {
                                // Check if the error should be ignored or the import canceled
                                continueOnTableTypeError = getErrorResponse(continueOnTableTypeError,
                                                                            "<html><b>Table type '</b>"
                                                                                                      + typeName
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

                        // Get the data fields for this table type
                        Object typeField = getObject(tableTypeJO,
                                                     JSONTags.TABLE_TYPE_FIELD.getTag());

                        // Check if any data fields exists for this table type
                        if (typeField != null)
                        {
                            // Step through each table type data field definition
                            for (JSONObject typeJO : parseJSONArray(typeField))
                            {
                                // Add the data field definition, checking for (and if possible,
                                // correcting) errors
                                continueOnTableTypeFieldError = addImportedDataFieldDefinition(continueOnTableTypeFieldError,
                                                                                               tableTypeDefn,
                                                                                               new String[] {CcddFieldHandler.getFieldTypeName(tableTypeDefn.getTypeName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.NAME.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.SIZE.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.VALUE.getColumnName())},
                                                                                               importFile.getAbsolutePath(),
                                                                                               inputTypeHandler,
                                                                                               parent);
                            }
                        }

                        // Add the table type definition to the list
                        tableTypeDefinitions.add(tableTypeDefn);
                    }
                }
            }

            // Add the table type if it's new or match it to an existing one with the same name if
            // the type definitions are the same
            String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefinitions, fieldHandler);

            // Check if a table type isn't new and doesn't match an existing one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("Imported table type '"
                                        + badDefn
                                        + "' doesn't match the existing definition");
            }

            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                List<String[]> dataTypeDefns = new ArrayList<String[]>();
                List<String[]> macroDefns = new ArrayList<String[]>();
                List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

                // Get the data type definitions JSON object
                defn = jsonObject.get(JSONTags.DATA_TYPE_DEFN.getTag());

                // Check if the data type definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each data type definition
                    for (JSONObject typeJO : parseJSONArray(defn))
                    {
                        // Get the data type definition components
                        String userName = getString(typeJO,
                                                    DataTypeEditorColumnInfo.USER_NAME.getColumnName());
                        String cName = getString(typeJO,
                                                 DataTypeEditorColumnInfo.C_NAME.getColumnName());
                        String size = getString(typeJO,
                                                DataTypeEditorColumnInfo.SIZE.getColumnName());
                        String baseType = getString(typeJO,
                                                    DataTypeEditorColumnInfo.BASE_TYPE.getColumnName());

                        // Check if the expected inputs are present
                        if ((!userName.isEmpty() || !cName.isEmpty())
                            && !size.isEmpty()
                            && !baseType.isEmpty()
                            && typeJO.keySet().size() < DataTypeEditorColumnInfo.values().length)
                        {
                            // Add the data type definition (add a blank to represent the OID)
                            dataTypeDefns.add(new String[] {userName, cName, size, baseType, ""});
                        }
                        // The number of inputs is incorrect
                        else
                        {
                            // Check if the error should be ignored or the import canceled
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
                        String name = getString(macroJO,
                                                MacroEditorColumnInfo.NAME.getColumnName());
                        String value = getString(macroJO,
                                                 MacroEditorColumnInfo.VALUE.getColumnName());

                        // Check if the expected inputs are present
                        if (!name.isEmpty()
                            && macroJO.keySet().size() < MacroEditorColumnInfo.values().length)
                        {
                            // Add the macro definition (add a blank to represent the OID)
                            macroDefns.add(new String[] {name, value, ""});
                        }
                        // The number of inputs is incorrect
                        else
                        {
                            // Check if the error should be ignored or the import canceled
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
                }

                // Get the reserved message ID definitions JSON object
                defn = jsonObject.get(JSONTags.RESERVED_MSG_ID_DEFN.getTag());

                // Check if the reserved message ID definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each reserved message ID definition
                    for (JSONObject reservedMsgIDJO : parseJSONArray(defn))
                    {
                        // Get the reserved message ID definition components
                        String name = getString(reservedMsgIDJO,
                                                ReservedMsgIDEditorColumnInfo.MSG_ID.getColumnName());
                        String value = getString(reservedMsgIDJO,
                                                 ReservedMsgIDEditorColumnInfo.DESCRIPTION.getColumnName());

                        // Check if the expected inputs are present
                        if (!name.isEmpty()
                            && reservedMsgIDJO.keySet().size() < ReservedMsgIDEditorColumnInfo.values().length)
                        {
                            // Add the reserved message ID definition (add a blank to represent the
                            // OID)
                            reservedMsgIDDefns.add(new String[] {name, value, ""});
                        }
                        // The number of inputs is incorrect
                        else
                        {
                            // Check if the error should be ignored or the import canceled
                            continueOnReservedMsgIDError = getErrorResponse(continueOnReservedMsgIDError,
                                                                            "<html><b>Missing or extra reserved message ID "
                                                                                                          + "definition input(s) in import file '</b>"
                                                                                                          + importFile.getAbsolutePath()
                                                                                                          + "<b>'; continue?",
                                                                            "Reserved Message ID Error",
                                                                            "Ignore this reserved message ID",
                                                                            "Ignore this and any remaining invalid reserved message IDs",
                                                                            "Stop importing",
                                                                            parent);
                        }
                    }
                }

                // Get the data fields for this project
                defn = jsonObject.get(JSONTags.PROJECT_FIELD.getTag());

                // Check if any data fields exist for this project
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each project-level data field definition
                    for (JSONObject typeJO : parseJSONArray(defn))
                    {
                        // Add the data field definition, checking for (and if possible,
                        // correcting) errors
                        continueOnProjectFieldError = addImportedDataFieldDefinition(continueOnProjectFieldError,
                                                                                     projectDefn,
                                                                                     new String[] {CcddFieldHandler.getFieldProjectName(),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.NAME.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.SIZE.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.VALUE.getColumnName())},
                                                                                     importFile.getAbsolutePath(),
                                                                                     inputTypeHandler,
                                                                                     parent);
                    }
                }

                // Add the data type if it's new or match it to an existing one with the same name
                // if the type definitions are the same
                dataTypeHandler.updateDataTypes(dataTypeDefns);

                // Add the macro if it's new or match it to an existing one with the same name if
                // the values are the same
                macroHandler.updateMacros(macroDefns);

                // Add the reserved message ID definition if it's new
                rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);

                // Build the imported project-level data fields, if any
                buildProjectdataFields(ccddMain, fieldHandler, projectDefn.getDataFields());
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
                    String tableName = getString(tableJO, JSONTags.TABLE_NAME.getTag());
                    String tableType = getString(tableJO, JSONTags.TABLE_TYPE.getTag());
                    String tableDesc = getString(tableJO, JSONTags.TABLE_DESCRIPTION.getTag());
                    Object tableDataJA = getObject(tableJO, JSONTags.TABLE_DATA.getTag());
                    Object dataFieldsJA = getObject(tableJO, JSONTags.TABLE_FIELD.getTag());

                    // Check if the expected inputs are present
                    if (!tableName.isEmpty()
                        && tableDataJA != null && tableDataJA instanceof JSONArray
                        && (dataFieldsJA == null || dataFieldsJA instanceof JSONArray))
                    {
                        // Create a new table type definition
                        TableDefinition tableDefn = new TableDefinition(tableName, tableDesc);

                        // Get the table's type definition. If importing into an existing table
                        // then use its type definition
                        TypeDefinition typeDefn = importType == ImportType.IMPORT_ALL
                                                                                      ? tableTypeHandler.getTypeDefinition(tableType)
                                                                                      : targetTypeDefn;

                        // Check if the table type doesn't exist
                        if (typeDefn == null)
                        {
                            throw new CCDDException("Unknown table type '" + tableType + "'");
                        }

                        // Store the table's type name
                        tableDefn.setTypeName(tableType);

                        // Get the number of expected columns (the hidden columns, primary key and
                        // row index, should not be included in the JSON file)
                        int numColumns = typeDefn.getColumnCountVisible();

                        // Create storage for the row of cell data
                        String[] rowData = new String[numColumns];

                        // Step through each row of data
                        for (JSONObject rowDataJO : parseJSONArray(tableDataJA))
                        {
                            // Initialize the column values to blanks
                            Arrays.fill(rowData, null);

                            // Step through each key (column name)
                            for (Object columnName : rowDataJO.keySet())
                            {
                                // Get the column index based on the column name
                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName.toString());

                                // Check if a column by this name exists
                                if (column != -1)
                                {
                                    // Get the value from the JSON input, if present; use a blank
                                    // if a value for this column doesn't exist
                                    rowData[column] = getString(rowDataJO,
                                                                typeDefn.getColumnNamesVisible()[column]);
                                }
                                // The number of inputs is incorrect
                                else
                                {
                                    // Check if the error should be ignored or the import canceled
                                    continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                             "<html><b>Table '</b>"
                                                                                                    + tableName
                                                                                                    + "<b>' column name '</b>"
                                                                                                    + columnName
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

                            // Add the row of data read in from the file to the cell data list
                            tableDefn.addData(rowData);
                        }

                        // Check if all definitions are to be loaded and if any data fields are
                        // defined
                        if (dataFieldsJA != null)
                        {
                            // Step through each data field definition
                            for (JSONObject dataFieldJO : parseJSONArray(dataFieldsJA))
                            {
                                // Add the data field definition, checking for (and if possible,
                                // correcting) errors
                                continueOnDataFieldError = addImportedDataFieldDefinition(continueOnDataFieldError,
                                                                                          tableDefn,
                                                                                          new String[] {tableName,
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.NAME.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.SIZE.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.VALUE.getColumnName())},
                                                                                          importFile.getAbsolutePath(),
                                                                                          inputTypeHandler,
                                                                                          parent);
                            }
                        }

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
        }
        catch (ParseException pe)
        {
            // Inform the user that the file cannot be closed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot parse import file<br>'</b>"
                                                              + importFile.getAbsolutePath()
                                                              + "<b>'; cause '</b>"
                                                              + pe.getMessage()
                                                              + "<b>'",
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

    /**********************************************************************************************
     * Export the project in JSON format to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to convert
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
     *             If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    @Override
    public void exportToFile(FileEnvVar exportFile,
                             String[] tableNames,
                             boolean replaceMacros,
                             boolean includeReservedMsgIDs,
                             boolean includeProjectFields,
                             boolean includeVariablePaths,
                             CcddVariableSizeAndConversionHandler variableHandler,
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

            // Output the table data to the selected file. Multiple writers are needed in case
            // tables are appended to an existing file
            fw = new FileWriter(exportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            // Create the file creation comment
            JSONObject outputJO = new JSONObject();
            outputJO.put(JSONTags.FILE_DESCRIPTION.getTag(),
                         "Created "
                                                             + new Date().toString()
                                                             + " : project = "
                                                             + dbControl.getDatabaseName()
                                                             + " : host = "
                                                             + dbControl.getServer()
                                                             + " : user = "
                                                             + dbControl.getUser());

            // Check if any tables are provided
            if (tableNames.length != 0)
            {
                JSONArray tableJA = new JSONArray();

                // Step through each table
                for (String tblName : tableNames)
                {
                    // Get the table's information
                    JSONObject tableInfoJO = getTableInformation(tblName,
                                                                 replaceMacros,
                                                                 includeVariablePaths,
                                                                 variableHandler,
                                                                 separators);

                    // Check if the table's data successfully loaded
                    if (tableInfoJO != null && !tableInfoJO.isEmpty())
                    {
                        // Add the wrapper for the table
                        tableJA.add(tableInfoJO);

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

                        // Build the data field information for the table
                        fieldHandler.buildFieldInformation(tblName);

                        // Step through each data field belonging to the table
                        for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
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
                        String[] columnNames = typeDefn.getColumnNamesUser();

                        // Step through each row in the table
                        for (int row = 0; row < tableInfo.getData().length; row++)
                        {
                            // Step through each column in the row
                            for (int column = 0; column < columnNames.length; column++)
                            {
                                // Check if the column isn't the primary key or row index
                                if (column != DefaultColumn.PRIMARY_KEY.ordinal()
                                    && column != DefaultColumn.ROW_INDEX.ordinal())
                                {
                                    List<Integer> dataTypeColumns = new ArrayList<Integer>();

                                    // Get the column indices for all columns that can contain a
                                    // primitive data type
                                    dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIM_AND_STRUCT));
                                    dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIMITIVE));

                                    // Step through each data type column
                                    for (int dataTypeColumn : dataTypeColumns)
                                    {
                                        // Get the value column
                                        String dataTypeName = tableInfo.getData()[row][dataTypeColumn].toString();

                                        // Check if the data type is a primitive and isn't already
                                        // in the list
                                        if (dataTypeHandler.isPrimitive(dataTypeName)
                                            && !referencedDataTypes.contains(dataTypeName))
                                        {
                                            // Add the data type name to the list of references
                                            // data types
                                            referencedDataTypes.add(dataTypeName);
                                        }
                                    }

                                    // Get the names of the macros referenced in the cell and add
                                    // them to the list
                                    referencedMacros.addAll(macroHandler.getReferencedMacros(tableInfo.getData()[row][column].toString()));
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
                }

                // Check if any tables were processed successfully
                if (tableJA != null)
                {
                    // Add the table information to the JSON output
                    outputJO.put(JSONTags.TABLE_DEFN.getTag(), tableJA);
                }
            }

            // Add the referenced table type definition(s), if any, to the output
            outputJO = getTableTypeDefinitions(referencedTableTypes, outputJO);

            // Add the referenced data type definition(s), if any, to the output
            outputJO = getDataTypeDefinitions(referencedDataTypes, outputJO);

            // Add the referenced input type definition(s), if any, to the output
            outputJO = getInputTypeDefinitions(referencedInputTypes, outputJO);

            // Add the referenced macro definition(s), if any, to the output
            outputJO = getMacroDefinitions(referencedMacros, outputJO);

            // Check if the user elected to store the reserved message IDs
            if (includeReservedMsgIDs)
            {
                // Add the reserved message ID definition(s), if any, to the output
                outputJO = getReservedMsgIDDefinitions(outputJO);
            }

            // Check if the user elected to store the project-level data fields
            if (includeProjectFields)
            {
                // Add the project-level data field(s), if any, to the output
                outputJO = getDataFields(CcddFieldHandler.getFieldProjectName(),
                                         JSONTags.PROJECT_FIELD.getTag(),
                                         outputJO);
            }

            // Check if variable paths are to be output
            if (includeVariablePaths)
            {
                // Add the variable paths, if any, to the output
                outputJO = getVariablePaths(variablePaths, outputJO);
            }

            // Create a JavaScript engine for use in formatting the JSON output
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");

            // Output the formatted JSON object to the file
            scriptEngine.put("jsonString", outputJO.toString());
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");
            pw.println((String) scriptEngine.get("result"));
        }
        catch (IOException | ScriptException e)
        {
            throw new CCDDException(e.getMessage());
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
                                                          "<html><b>Cannot close export file<br>'</b>"
                                                                  + exportFile.getAbsolutePath()
                                                                  + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Get the data for the specified data table
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]. Blank to
     *            return the data for all tables
     *
     * @param getDescription
     *            true to get the table description when loading the table data
     *
     * @param replaceMacros
     *            true to display the macro values in place of the corresponding macro names; false
     *            to display the macro names
     *
     * @param includeVariablePaths
     *            true to include a column, 'Variable Path', showing the variable path for each
     *            variable in a structure table using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if isIncludePath is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if isIncludePath is false
     *
     * @param outputJO
     *            JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the table data added (if any); null if the table
     *         doesn't exists or an error occurs when loading the data. Empty table cells are
     *         omitted
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getTableData(String tableName,
                                      boolean getDescription,
                                      boolean replaceMacros,
                                      boolean includeVariablePaths,
                                      CcddVariableSizeAndConversionHandler variableHandler,
                                      String[] separators,
                                      JSONObject outputJO)
    {
        JSONArray tableDataJA = null;

        // Get the information from the database for the specified table
        tableInfo = dbTable.loadTableData(tableName,
                                          !getDescription,
                                          false,
                                          false,
                                          ccddMain.getMainFrame());

        // Check if the table exists and successfully loaded
        if (tableInfo != null && !tableInfo.isErrorFlag())
        {
            JSONObject columnJO = new JSONObject();
            tableDataJA = new JSONArray();

            // Check if the macro names should be replaced with the corresponding macro values
            if (replaceMacros)
            {
                // Replace all macros in the table
                tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));
            }

            // Check if the table has any data
            if (tableInfo.getData().length != 0)
            {
                // Get the column names for this table's type definition
                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());
                String[] columnNames = typeDefn.getColumnNamesUser();

                // Step through each table row
                for (int row = 0; row < tableInfo.getData().length; row++)
                {
                    columnJO = new JSONObject();

                    // Step through each table column
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData()[row].length; column++)
                    {
                        // Check if a cell isn't blank
                        if (!tableInfo.getData()[row][column].toString().isEmpty())
                        {
                            // Add the column name and value to the cell object
                            columnJO.put(columnNames[column], tableInfo.getData()[row][column]);

                            // Check if the table represents a structure, that the variable path
                            // column is to be included, and that a variable handler and path
                            // separators are supplied
                            if (typeDefn.isStructure()
                                && includeVariablePaths
                                && variableHandler != null
                                && separators != null)
                            {
                                // Get the variable's data type
                                String dataType = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)].toString();

                                // Check if the data type is a primitive
                                if (dataTypeHandler.isPrimitive(dataType))
                                {
                                    // Build the variable's full path
                                    String variablePath = tableInfo.getTablePath()
                                                          + ","
                                                          + dataType
                                                          + "."
                                                          + tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

                                    // Add the formatted path in the 'Variable Path' column
                                    columnJO.put("Variable Path",
                                                 variableHandler.getFullVariableName(variablePath,
                                                                                     separators[0],
                                                                                     Boolean.valueOf(separators[1]),
                                                                                     separators[2]));
                                }
                            }
                        }
                    }

                    // Add the column values to the data array. An array is used to preserve the
                    // order of the rows
                    tableDataJA.add(columnJO);
                }
            }

            // Check if any table data was loaded
            if (!tableDataJA.isEmpty())
            {
                // Add the table data to the JSON output
                outputJO.put(JSONTags.TABLE_DATA.getTag(), tableDataJA);
            }
        }
        // The table failed to load (database error or the table doesn't exist)
        else
        {
            // Set the output to null in order to indicate the error
            outputJO = null;
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Get the data field information for the specified owner (table, table type, or group)
     *
     * @param ownerName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]] (if blank
     *            then every data table's data fields are returned), table type name in the format
     *            tableTypeTag:tableTypepName, or group owner in the format groupTag:groupName
     *
     * @param outputJO
     *            JSON object to which the data fields are added
     *
     * @return The supplied JSON object, with the data field(s) added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getDataFields(String ownerName, String tagName, JSONObject outputJO)
    {
        JSONArray dataFieldDefnJA = new JSONArray();

        // Get the existing data fields for the specified owner
        fieldHandler.buildFieldInformation(ownerName);

        // Check if the owner has any fields
        if (!fieldHandler.getFieldInformation().isEmpty())
        {
            JSONObject fieldJO = new JSONObject();

            // Step through the data fields for this owner
            for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
            {
                fieldJO = new JSONObject();

                // Add the data field column values to the output
                fieldJO.put(FieldEditorColumnInfo.NAME.getColumnName(), fieldInfo.getFieldName());
                fieldJO.put(FieldEditorColumnInfo.DESCRIPTION.getColumnName(),
                            fieldInfo.getDescription());
                fieldJO.put(FieldEditorColumnInfo.SIZE.getColumnName(), fieldInfo.getSize());
                fieldJO.put(FieldEditorColumnInfo.INPUT_TYPE.getColumnName(),
                            fieldInfo.getInputType().getInputName());
                fieldJO.put(FieldEditorColumnInfo.REQUIRED.getColumnName(),
                            fieldInfo.isRequired());
                fieldJO.put(FieldEditorColumnInfo.APPLICABILITY.getColumnName(),
                            fieldInfo.getApplicabilityType().getApplicabilityName());
                fieldJO.put(FieldEditorColumnInfo.VALUE.getColumnName(), fieldInfo.getValue());
                dataFieldDefnJA.add(fieldJO);
            }

            // Check if any data field exists
            if (!dataFieldDefnJA.isEmpty())
            {
                // Add the data field(s) to the JSON output
                outputJO.put(tagName, dataFieldDefnJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Get the type, description, size, data, and data fields for the specified data table
     *
     * @param tableName
     *            table name and path in the format rootTable[,dataType1.variable1[,...]]
     *
     * @param replaceMacros
     *            true to display the macro values in place of the corresponding macro names; false
     *            to display the macro names
     *
     * @param includeVariablePaths
     *            true to include a column, 'Variable Path', showing the variable path for each
     *            variable in a structure table using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s)
     *
     * @return JSON encoded string containing the specified table information; null if the
     *         specified table doesn't exist or fails to load
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getTableInformation(String tableName,
                                             boolean replaceMacros,
                                             boolean includeVariablePaths,
                                             CcddVariableSizeAndConversionHandler variableHandler,
                                             String[] separators)
    {
        // Store the table's data
        JSONObject tableInformation = getTableData(tableName,
                                                   false,
                                                   replaceMacros,
                                                   includeVariablePaths,
                                                   variableHandler,
                                                   separators,
                                                   new JSONObject());

        // Check that the table loaded successfully
        if (tableInformation != null)
        {
            // Store the table's name, type, description, and data fields
            tableInformation.put(JSONTags.TABLE_NAME.getTag(), tableName);
            tableInformation.put(JSONTags.TABLE_TYPE.getTag(), tableInfo.getType());
            tableInformation.put(JSONTags.TABLE_DESCRIPTION.getTag(), tableInfo.getDescription());
            tableInformation = getDataFields(tableName,
                                             JSONTags.TABLE_FIELD.getTag(),
                                             tableInformation);

            // Get the system name
            String systemName = fieldHandler.getFieldValue(tableName, DefaultInputType.SYSTEM_PATH);

            // Check if the system name exists
            if (systemName != null && !systemName.isEmpty())
            {
                // Store the system name
                tableInformation.put(JSONTags.TABLE_SYSTEM.getTag(), systemName);
            }
        }

        return tableInformation;
    }

    /**********************************************************************************************
     * Add the table type definition(s) corresponding to the supplied table type name(s) to the
     * specified JSON object. If no table type is provided, or if none are recognized, then nothing
     * is added to the JSON object
     *
     * @param tableTypeNames
     *            names of the table types to add; null to include all defined table types
     *
     * @param outputJO
     *            JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the table type definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getTableTypeDefinitions(List<String> tableTypeNames, JSONObject outputJO)
    {
        // Check if the table type name list is null, in which case all defined table types are
        // included
        if (tableTypeNames == null)
        {
            tableTypeNames = new ArrayList<String>();

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
            {
                // Add the table type name to the list
                tableTypeNames.add(typeDefn.getName());
            }
        }

        // Check if any table types are referenced
        if (!tableTypeNames.isEmpty())
        {
            JSONArray tableTypeJA = new JSONArray();

            // Step through each referenced table type
            for (String refTableType : tableTypeNames)
            {
                // Get the table type definition
                TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(refTableType);

                // Check if the table type exists
                if (tableTypeDefn != null)
                {
                    JSONArray typeDefnJA = new JSONArray();
                    JSONObject tableTypeJO;

                    // Step through each column definition in the table type, skipping the primary
                    // key and row index columns
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
                        tableTypeJO.put(CcddUtilities.removeHTMLTags(TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.getColumnName()),
                                        tableTypeDefn.isStructureAllowed()[column]);
                        tableTypeJO.put(CcddUtilities.removeHTMLTags(TableTypeEditorColumnInfo.POINTER_ALLOWED.getColumnName()),
                                        tableTypeDefn.isPointerAllowed()[column]);

                        // Add the table type definition to the array
                        typeDefnJA.add(tableTypeJO);
                    }

                    // Add the wrapper for the table type name and put the table type description,
                    // column definitions, and data fields (if any) in it
                    tableTypeJO = new JSONObject();
                    tableTypeJO.put(JSONTags.TABLE_TYPE_NAME.getTag(), refTableType);
                    tableTypeJO.put(JSONTags.TABLE_TYPE_DESCRIPTION.getTag(),
                                    tableTypeDefn.getDescription());
                    tableTypeJO.put(JSONTags.TABLE_TYPE_COLUMN.getTag(), typeDefnJA);
                    tableTypeJO = getDataFields(CcddFieldHandler.getFieldTypeName(refTableType),
                                                JSONTags.TABLE_TYPE_FIELD.getTag(),
                                                tableTypeJO);

                    tableTypeJA.add(tableTypeJO);
                }
            }

            // Check if a table type definition was recognized
            if (!tableTypeJA.isEmpty())
            {
                // Add the table type definition(s) to the JSON output
                outputJO.put(JSONTags.TABLE_TYPE_DEFN.getTag(), tableTypeJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Add the data type definition(s) corresponding to the supplied data type name(s) to the
     * specified JSON object. If no data type is provided, or if none are recognized, then nothing
     * is added to the JSON object
     *
     * @param dataTypeNames
     *            names of the data types to add; null to include all defined data types
     *
     * @param outputJO
     *            JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the data type definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getDataTypeDefinitions(List<String> dataTypeNames, JSONObject outputJO)
    {
        // Check if the data type name list is null, in which case all defined data types are
        // included
        if (dataTypeNames == null)
        {
            dataTypeNames = new ArrayList<String>();

            // Step through each data type definition
            for (String[] dataType : dataTypeHandler.getDataTypeData())
            {
                // Add the data type name to the list
                dataTypeNames.add(CcddDataTypeHandler.getDataTypeName(dataType));
            }
        }

        // Check if any data types are referenced
        if (!dataTypeNames.isEmpty())
        {
            JSONArray dataTypeJA = new JSONArray();

            // Step through each referenced table type
            for (String refDataType : dataTypeNames)
            {
                // Get the data type information
                String[] dataType = dataTypeHandler.getDataTypeByName(refDataType);

                // Check if the data type exists
                if (dataType != null)
                {
                    // Store the data type user-defined name, C-language name, size, and base type
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

            // Check if a data type definition was recognized
            if (!dataTypeJA.isEmpty())
            {
                // Add the data type definition(s) to the JSON output
                outputJO.put(JSONTags.DATA_TYPE_DEFN.getTag(), dataTypeJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Add the custom input type definition(s) corresponding to the supplied input type name(s) to
     * the specified JSON object. If no input type is provided, or if none are recognized, then
     * nothing is added to the JSON object
     *
     * @param inputTypeNames
     *            names of the custom input types to add; null to include all defined custom input
     *            types
     *
     * @param outputJO
     *            JSON object to which the input types are added
     *
     * @return The supplied JSON object, with the custom input type definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getInputTypeDefinitions(List<String> inputTypeNames, JSONObject outputJO)
    {
        // Check if the input type name list is null, in which case all defined input types are
        // included
        if (inputTypeNames == null)
        {
            inputTypeNames = new ArrayList<String>();

            // Step through each input type definition
            for (String[] inputType : inputTypeHandler.getCustomInputTypeData())
            {
                // Add the input type name to the list
                inputTypeNames.add(inputType[InputTypesColumn.NAME.ordinal()]);
            }
        }

        // Check if there are any input types to process
        if (!inputTypeNames.isEmpty())
        {
            JSONArray inputTypeJA = new JSONArray();

            // Step through each referenced input type
            for (String inputTypeName : inputTypeNames)
            {
                // Get the input type value
                InputType inputType = inputTypeHandler.getInputTypeByName(inputTypeName);

                // Check if the input type exists
                if (inputType != null)
                {
                    // Store the input type name and value
                    JSONObject inputTypeJO = new JSONObject();
                    inputTypeJO.put(InputTypeEditorColumnInfo.NAME.getColumnName(), inputTypeName);
                    inputTypeJO.put(InputTypeEditorColumnInfo.DESCRIPTION.getColumnName(),
                                    inputType.getInputDescription());
                    inputTypeJO.put(InputTypeEditorColumnInfo.MATCH.getColumnName(),
                                    inputType.getInputMatch());
                    inputTypeJO.put(InputTypeEditorColumnInfo.ITEMS.getColumnName(),
                                    InputType.convertItemListToString(inputType.getInputItems()));
                    inputTypeJO.put(InputTypeEditorColumnInfo.FORMAT.getColumnName(),
                                    inputType.getInputFormat().getFormatName());

                    // Add the input type definition to the array
                    inputTypeJA.add(inputTypeJO);
                }
            }

            // Check if a input type was recognized
            if (!inputTypeJA.isEmpty())
            {
                // Add the input type definition(s) to the JSON output
                outputJO.put(JSONTags.INPUT_TYPE_DEFN.getTag(), inputTypeJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Add the macro definition(s) corresponding to the supplied macro name(s) to the specified
     * JSON object. If no macro is provided, or if none are recognized, then nothing is added to
     * the JSON object
     *
     * @param macroNames
     *            names of the macros to add; null to include all defined macros
     *
     * @param outputJO
     *            JSON object to which the macros are added
     *
     * @return The supplied JSON object, with the macro definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getMacroDefinitions(List<String> macroNames, JSONObject outputJO)
    {
        // Check if the macro name list is null, in which case all defined macros are included
        if (macroNames == null)
        {
            macroNames = new ArrayList<String>();

            // Step through each macro definition
            for (String[] macro : macroHandler.getMacroData())
            {
                // Add the macro name to the list
                macroNames.add(macro[MacrosColumn.MACRO_NAME.ordinal()]);
            }
        }

        // Check if there are any macros to process
        if (!macroNames.isEmpty())
        {
            JSONArray macroJA = new JSONArray();

            // Step through each referenced macro
            for (String macroName : macroNames)
            {
                // Get the macro value
                String macroValue = macroHandler.getMacroValue(macroName);

                // Check if the macro exists
                if (macroValue != null)
                {
                    // Store the macro name and value
                    JSONObject macroJO = new JSONObject();
                    macroJO.put(MacroEditorColumnInfo.NAME.getColumnName(), macroName);
                    macroJO.put(MacroEditorColumnInfo.VALUE.getColumnName(), macroValue);

                    // Add the macro definition to the array
                    macroJA.add(macroJO);
                }
            }

            // Check if a macro was recognized
            if (!macroJA.isEmpty())
            {
                // Add the macro definition(s) to the JSON output
                outputJO.put(JSONTags.MACRO_DEFN.getTag(), macroJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Add the reserved message ID definition(s)
     *
     * @param outputJO
     *            JSON object to which the reserved message IDs are added
     *
     * @return The supplied JSON object, with the reserved message ID definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getReservedMsgIDDefinitions(JSONObject outputJO)
    {
        // Check if there are any reserved message IDs defined
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
        {
            JSONArray reservedMsgIDJA = new JSONArray();

            // Step through each reserved message ID definition
            for (String[] reservedMsgIDDefn : rsvMsgIDHandler.getReservedMsgIDData())
            {
                // Store the macro name and value
                JSONObject macroJO = new JSONObject();
                macroJO.put(ReservedMsgIDEditorColumnInfo.MSG_ID.getColumnName(),
                            reservedMsgIDDefn[ReservedMsgIDEditorColumnInfo.MSG_ID.ordinal()]);
                macroJO.put(ReservedMsgIDEditorColumnInfo.DESCRIPTION.getColumnName(),
                            reservedMsgIDDefn[ReservedMsgIDEditorColumnInfo.DESCRIPTION.ordinal()]);

                // Add the reserved message ID definition to the array
                reservedMsgIDJA.add(macroJO);
            }

            // Check if a reserved message ID is defined
            if (!reservedMsgIDJA.isEmpty())
            {
                // Add the reserved message ID definition(s) to the JSON output
                outputJO.put(JSONTags.RESERVED_MSG_ID_DEFN.getTag(), reservedMsgIDJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Add the variable paths
     *
     * @param variablePaths
     *            list containing arrays of the variable path in both application and user0defined
     *            formats
     *
     * @param outputJO
     *            JSON object to which the variable paths are added
     *
     * @return The supplied JSON object, with the variable paths added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getVariablePaths(List<String[]> variablePaths, JSONObject outputJO)
    {
        // Check if there are any variable paths to output
        if (!variablePaths.isEmpty())
        {
            JSONArray variablePathJA = new JSONArray();

            // Step through each variable path
            for (String[] variablePath : variablePaths)
            {
                // Store the variable path in application and user-defined formats
                JSONObject pathJO = new JSONObject();
                pathJO.put(variablePath[0], variablePath[1]);

                // Add the variable path to the array
                variablePathJA.add(pathJO);
            }

            // Check if a variable path exists
            if (!variablePathJA.isEmpty())
            {
                // Add the variable paths to the JSON output
                outputJO.put(JSONTags.VARIABLE_PATH.getTag(), variablePathJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Get a JSON object containing the message ID owners, names, and values
     *
     * @return JSON object containing the message ID owners, names, and values; an empty object if
     *         no message IDs or names exist
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected JSONObject getMessageIDAndNames()
    {
        JSONObject outputJO = new JSONObject();

        // Create a message ID handler and get the list of message ID names and associated ID
        // values
        CcddMessageIDHandler msgIDHandler = new CcddMessageIDHandler(ccddMain, false);
        List<String[]> msgIDs = msgIDHandler.getMessageIDsAndNames(MessageIDSortOrder.BY_NAME,
                                                                   true,
                                                                   parent);

        // Check if there are any message IDs or names to output
        if (!msgIDs.isEmpty())
        {
            JSONArray msgIDJA = new JSONArray();

            // Step through each variable path
            for (String[] msgID : msgIDs)
            {
                // Store the message ID owners, names, and values
                JSONObject msgIDJO = new JSONObject();
                msgIDJO.put(MsgIDTableColumnInfo.OWNER.getColumnName(),
                            msgID[MsgIDListColumnIndex.OWNER.ordinal()]);
                msgIDJO.put(MsgIDTableColumnInfo.MESSAGE_ID_NAME.getColumnName(),
                            msgID[MsgIDListColumnIndex.MESSAGE_ID_NAME.ordinal()]);
                msgIDJO.put(MsgIDTableColumnInfo.MESSAGE_ID.getColumnName(),
                            msgID[MsgIDListColumnIndex.MESSAGE_ID.ordinal()]);

                // Add the message ID owner, name, and value to the array
                msgIDJA.add(msgIDJO);
            }

            // Check if a message ID owner, name, and value exists
            if (!msgIDJA.isEmpty())
            {
                // Add the message ID owners, names, and values to the JSON output
                outputJO.put(JSONTags.MESSAGE_ID.getTag(), msgIDJA);
            }
        }

        return outputJO;
    }
}
