/**
 * CFS Command and Data Dictionary JSON handler.
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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.OrderedJSONObject;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.AssociationsTableColumnInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.GroupDefinitionColumn;
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
 * CFS Command and Data Dictionary JSON handler class
 *************************************************************************************************/
public class CcddJSONHandler extends CcddImportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private final CcddGroupHandler groupHandler;
    private final CcddScriptHandler scriptHandler;
    private TableInformation tableInfo;

    // GUI component over which to center any error dialog
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // List of original and new script associations
    private List<String[]> associations;

    /**********************************************************************************************
     * JSON handler class constructor
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
    CcddJSONHandler(CcddMain ccddMain, CcddGroupHandler groupHandler, Component parent)
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
            ProjectDefinition projectDefn = new ProjectDefinition();
            List<TableTypeDefinition> tableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            tableDefinitions = new ArrayList<TableDefinition>();
            List<String[]> inputTypeDefns = new ArrayList<String[]>();

            // Flags indicating if importing should continue after an input error is detected
            boolean continueOnTableTypeError = ignoreErrors;
            boolean continueOnDataTypeError = ignoreErrors;
            boolean continueOnInputTypeError = ignoreErrors;
            boolean continueOnMacroError = ignoreErrors;
            boolean continueOnReservedMsgIDError = ignoreErrors;
            boolean continueOnProjectFieldError = ignoreErrors;
            boolean continueOnColumnError = ignoreErrors;
            boolean continueOnDataFieldError = ignoreErrors;
            boolean continueOnTableTypeFieldError = ignoreErrors;
            boolean continueOnGroupError = ignoreErrors;
            boolean continueOnAssociationError = ignoreErrors;

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
                        // Check if the input type definition is valid
                        String[] inputTypeDefn = checkInputTypeDefinition(new String[] {name,
                                                                                        description,
                                                                                        match,
                                                                                        items,
                                                                                        format,
                                                                                        ""});

                        // Add the input type definition (add a blank to represent the OID)
                        inputTypeDefns.add(inputTypeDefn);
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
                                                                                                                       FieldEditorColumnInfo.CHAR_SIZE.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.VALUE.getColumnName()),
                                                                                                             getString(typeJO,
                                                                                                                       FieldEditorColumnInfo.INHERITED.getColumnName())},
                                                                                               importFile.getAbsolutePath(),
                                                                                               inputTypeHandler,
                                                                                               fieldHandler,
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
            String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefinitions);

            // Check if a table type isn't new and doesn't match an existing one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("Imported table type '</b>"
                                        + badDefn
                                        + "<b>' doesn't match the existing definition");
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
                            // Build the data type definition
                            String[] dataTypeDefn = new String[] {userName,
                                                                  cName,
                                                                  size,
                                                                  baseType,
                                                                  ""};

                            // Check if the data type definition is valid
                            checkDataTypeDefinition(dataTypeDefn);

                            // Add the data type definition (add a blank to represent the OID)
                            dataTypeDefns.add(dataTypeDefn);
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
                            // Build the macro definition
                            String[] macroDefn = new String[] {name, value, ""};

                            // Check if the macro definition is valid
                            checkMacroDefinition(macroDefn);

                            // Add the macro definition (add a blank to represent the OID)
                            macroDefns.add(macroDefn);
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
                                                                                                             FieldEditorColumnInfo.CHAR_SIZE.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.VALUE.getColumnName()),
                                                                                                   getString(typeJO,
                                                                                                             FieldEditorColumnInfo.INHERITED.getColumnName())},
                                                                                     importFile.getAbsolutePath(),
                                                                                     inputTypeHandler,
                                                                                     fieldHandler,
                                                                                     parent);
                    }
                }

                // Get the group definitions
                defn = jsonObject.get(JSONTags.GROUP.getTag());

                // Check if the group definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each group definition
                    for (JSONObject groupJO : parseJSONArray(defn))
                    {
                        // Get the group definition components
                        String name = getString(groupJO, JSONTags.GROUP_NAME.getTag());
                        String description = getString(groupJO,
                                                       JSONTags.GROUP_DESCRIPTION.getTag());
                        String isApplication = getString(groupJO, JSONTags.GROUP_IS_APPLICATION.getTag());
                        String members = "";

                        // Get the table members for this group
                        Object groupMember = getObject(groupJO, JSONTags.GROUP_TABLE.getTag());

                        // Check if any members exists for this group
                        if (groupMember != null)
                        {
                            int index = 0;
                            boolean isFirst = true;

                            // Step through each member
                            for (JSONObject memberJO : parseJSONArray(groupMember))
                            {
                                // Add the table member
                                members += (isFirst
                                                    ? ""
                                                    : ";")
                                           + memberJO.get(index).toString();
                                index++;
                                isFirst = false;
                            }
                        }

                        // Check if the expected inputs are present
                        if (!name.isEmpty()
                            && groupJO.keySet().size() <= GroupDefinitionColumn.values().length + 1)
                        {
                            // Add the group data field definition, checking for (and if possible,
                            // correcting) errors
                            addImportedGroupDefinition(new String[] {name,
                                                                     description,
                                                                     isApplication,
                                                                     members},
                                                       importFile.getAbsolutePath(),
                                                       replaceExistingGroups,
                                                       groupHandler);
                        }
                        // The number of inputs is incorrect
                        else
                        {
                            // Check if the error should be ignored or the import canceled
                            continueOnGroupError = getErrorResponse(continueOnGroupError,
                                                                    "<html><b>Missing or extra group definition "
                                                                                          + "input(s) in import file '</b>"
                                                                                          + importFile.getAbsolutePath()
                                                                                          + "<b>'; continue?",
                                                                    "Group Error",
                                                                    "Ignore this group",
                                                                    "Ignore this and any remaining invalid groups",
                                                                    "Stop importing",
                                                                    parent);
                        }

                        // Get the data fields for this group
                        Object groupField = getObject(groupJO, JSONTags.GROUP_FIELD.getTag());

                        // Check if any data fields exists for this group
                        if (groupField != null)
                        {
                            // Step through each group data field definition
                            for (JSONObject grpFldJO : parseJSONArray(groupField))
                            {
                                // Add the data field definition, checking for (and if possible,
                                // correcting) errors
                                continueOnTableTypeFieldError = addImportedDataFieldDefinition(continueOnTableTypeFieldError,
                                                                                               projectDefn,
                                                                                               new String[] {CcddFieldHandler.getFieldGroupName(name),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.NAME.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.CHAR_SIZE.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.VALUE.getColumnName()),
                                                                                                             getString(grpFldJO,
                                                                                                                       FieldEditorColumnInfo.INHERITED.getColumnName())},
                                                                                               importFile.getAbsolutePath(),
                                                                                               inputTypeHandler,
                                                                                               fieldHandler,
                                                                                               parent);
                            }
                        }
                    }
                }

                // Get the script associations
                defn = jsonObject.get(JSONTags.SCRIPT_ASSOCIATION.getTag());

                // Check if the script associations exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Check if the associations haven't been loaded
                    if (associations == null)
                    {
                        // Get the script associations from the database
                        associations = scriptHandler.getScriptAssociations(parent);
                    }

                    // Step through each script association
                    for (JSONObject assnJO : parseJSONArray(defn))
                    {
                        // Add the script association, checking for errors
                        continueOnAssociationError = addImportedScriptAssociation(continueOnAssociationError,
                                                                                  associations,
                                                                                  new String[] {getString(assnJO,
                                                                                                          AssociationsTableColumnInfo.NAME.getColumnName()),
                                                                                                getString(assnJO,
                                                                                                          AssociationsTableColumnInfo.DESCRIPTION.getColumnName()),
                                                                                                getString(assnJO,
                                                                                                          AssociationsTableColumnInfo.SCRIPT_FILE.getColumnName()),
                                                                                                CcddScriptHandler.convertAssociationMembersFormat(getString(assnJO,
                                                                                                                                                            AssociationsTableColumnInfo.MEMBERS.getColumnName()),
                                                                                                                                                  true)},
                                                                                  importFile.getAbsolutePath(),
                                                                                  scriptHandler,
                                                                                  parent);
                    }
                }

                // Add the data type if it's new or match it to an existing one with the same name
                // if the type definitions are the same
                dataTypeHandler.updateDataTypes(dataTypeDefns);

                // Add the macro if it's new or match it to an existing one with the same
                // name. If the flag to replace existing macro values is false then get the
                // list of macros names where the existing and import file values differ
                List<String> mismatchedMacros = macroHandler.updateMacros(macroDefns,
                                                                          replaceExistingMacros);

                // Check if any existing and import file macro values differ ( the flag to replace
                // existing macro values is false)
                if (!mismatchedMacros.isEmpty())
                {
                    boolean continueOnError = false;

                    // Check if the user elects to ignore the difference(s), keeping the existing
                    // macro values, or cancels the import operation
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

                // Add the reserved message ID definition if it's new
                rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);

                // Build the imported project-level data fields, if any
                buildProjectAndGroupDataFields(fieldHandler, projectDefn.getDataFields());
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
                            throw new CCDDException("Unknown table type '</b>"
                                                    + tableType
                                                    + "<b>'");
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
                                                                                                                  FieldEditorColumnInfo.CHAR_SIZE.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.INPUT_TYPE.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.REQUIRED.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.APPLICABILITY.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.VALUE.getColumnName()),
                                                                                                        getString(dataFieldJO,
                                                                                                                  FieldEditorColumnInfo.INHERITED.getColumnName())},
                                                                                          importFile.getAbsolutePath(),
                                                                                          inputTypeHandler,
                                                                                          fieldHandler,
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
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
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
     * Export the project in JSON format to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to convert
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
     *             If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
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

            // Use of the JSONObject does not retain the order that the key:value pairs are stored.
            // This custom JSON object is used so that the stored order is reflected in the output
            OrderedJSONObject outputJO = new OrderedJSONObject();

            // Check if the build information is to be output
            if (includeBuildInformation)
            {
                // Create the file creation comment
                outputJO.put(JSONTags.FILE_DESCRIPTION.getTag(),
                             "Created "
                                                                 + new Date().toString()
                                                                 + " : CCDD version = "
                                                                 + ccddMain.getCCDDVersionInformation()
                                                                 + " : project = "
                                                                 + dbControl.getProjectName()
                                                                 + " : host = "
                                                                 + dbControl.getServer()
                                                                 + " : user = "
                                                                 + dbControl.getUser());
            }

            // Check if any tables are provided
            if (tableNames.length != 0)
            {
                JSONArray tableJA = new JSONArray();

                // Sort the array of table names alphabetically, accounting for array dimension
                // values within the table names. This causes the tables to be placed in the JSON
                // output in a predictable and reproducible order
                Arrays.sort(tableNames, new Comparator<String>()
                {
                    /******************************************************************************
                     * Compare the table names, ignoring case and accounting for array dimension
                     * values as integers and not as strings
                     *****************************************************************************/
                    @Override
                    public int compare(String tblName1, String tblName2)
                    {
                        int result = 0;

                        // Check if the table names are members of the same array
                        if (ArrayVariable.isArrayMember(tblName1)
                            && ArrayVariable.isArrayMember(tblName2)
                            && ArrayVariable.removeArrayIndex(tblName1)
                                            .equals(ArrayVariable.removeArrayIndex(tblName2)))
                        {
                            // Compare the two array names, accounting for the array dimension(s)
                            // as integers and not as strings
                            result = ArrayVariable.compareTo(tblName1, tblName2);
                        }
                        // The table names are not part of the same array
                        else
                        {
                            // Compare the two names as strings, ignoring case
                            result = tblName1.compareToIgnoreCase(tblName2);
                        }

                        return result;
                    }
                });

                // Step through each table
                for (String tblName : tableNames)
                {
                    // Get the table's information
                    OrderedJSONObject tableInfoJO = getTableInformation(tblName,
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

                                    // Step through each macro referenced in the cell
                                    for (String refMacro : macroHandler.getReferencedMacros(tableInfo.getData()[row][column].toString()))
                                    {
                                        // Get the name of the macro as stored in the internal
                                        // macros table
                                        String storedMacroName = macroHandler.getStoredMacroName(refMacro);

                                        // Check if the macro name isn't already in the list of
                                        // referenced macros
                                        if (!referencedMacros.contains(storedMacroName))
                                        {
                                            // Add the macro name to the list of referenced macros
                                            referencedMacros.add(storedMacroName);
                                        }
                                    }
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
            outputJO = getTableTypeDefinitions(referencedTableTypes,
                                               referencedInputTypes,
                                               outputJO);

            // Add the referenced data type definition(s), if any, to the output
            outputJO = getDataTypeDefinitions(referencedDataTypes, outputJO);

            // Add the referenced macro definition(s), if any, to the output
            outputJO = getMacroDefinitions(referencedMacros, outputJO);

            // Add the referenced input type definition(s), if any, to the output
            outputJO = getInputTypeDefinitions(referencedInputTypes, outputJO);

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
                                         referencedInputTypes,
                                         outputJO);
            }

            // Check if the user elected to store the groups
            if (includeGroups)
            {
                // Get the group information
                String groupInfo = getGroupInformation("", false, referencedInputTypes);

                // Check if any groups exist
                if (groupInfo != null)
                {
                    // Add the group information, if any, to the output
                    JSONParser parser = new JSONParser();
                    outputJO.put(JSONTags.GROUP.getTag(), parser.parse(groupInfo));
                }
            }

            // Check if the user elected to store the script associations
            if (includeAssociations)
            {
                // Add the script association(s), if any, to the output
                getScriptAssociations(outputJO);
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

            // Output the ordered and formatted JSON object to the file
            StringWriter orderedOutput = new StringWriter();
            JSONValue.writeJSONString(outputJO, orderedOutput);
            scriptEngine.put("jsonString", orderedOutput.toString());
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");
            pw.println((String) scriptEngine.get("result"));
        }
        catch (IOException | ScriptException iose)
        {
            throw new CCDDException(iose.getMessage());
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
    protected OrderedJSONObject getTableData(String tableName,
                                             boolean getDescription,
                                             boolean replaceMacros,
                                             boolean includeVariablePaths,
                                             CcddVariableHandler variableHandler,
                                             String[] separators,
                                             OrderedJSONObject outputJO)
    {
        JSONArray tableDataJA = null;

        // Get the information from the database for the specified table
        tableInfo = dbTable.loadTableData(tableName,
                                          !getDescription,
                                          false,
                                          ccddMain.getMainFrame());

        // Check if the table exists and successfully loaded
        if (tableInfo != null && !tableInfo.isErrorFlag())
        {
            OrderedJSONObject columnJO = new OrderedJSONObject();
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
                    columnJO = new OrderedJSONObject();

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
     * @param tagName
     *            JSON tag name
     *
     * @param referencedInputTypes
     *            list of user-defined input types referenced; null if this list isn't used by the
     *            caller. This list has any user-defined input types not already in the list added
     *
     * @param outputJO
     *            JSON object to which the data fields are added
     *
     * @return The supplied JSON object, with the data field(s) added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getDataFields(String ownerName,
                                              String tagName,
                                              List<String> referencedInputTypes,
                                              OrderedJSONObject outputJO)
    {
        JSONArray dataFieldDefnJA = new JSONArray();

        // Get the existing data fields for the specified owner
        List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(ownerName);

        // Check if the owner has any fields
        if (!fieldInformation.isEmpty())
        {
            OrderedJSONObject fieldJO = new OrderedJSONObject();

            // Step through the data fields for this owner
            for (FieldInformation fieldInfo : fieldInformation)
            {
                fieldJO = new OrderedJSONObject();

                // Check if if the input type is user-defined and this input type is not already
                // output
                if (referencedInputTypes != null
                    && fieldInfo.getInputType().isCustomInput()
                    && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                {
                    // Add the input type to the list of those referenced
                    referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                }

                // Add the data field column values to the output
                fieldJO.put(FieldEditorColumnInfo.NAME.getColumnName(), fieldInfo.getFieldName());
                fieldJO.put(FieldEditorColumnInfo.DESCRIPTION.getColumnName(),
                            fieldInfo.getDescription());
                fieldJO.put(FieldEditorColumnInfo.CHAR_SIZE.getColumnName(), fieldInfo.getSize());
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
    protected OrderedJSONObject getTableInformation(String tableName,
                                                    boolean replaceMacros,
                                                    boolean includeVariablePaths,
                                                    CcddVariableHandler variableHandler,
                                                    String[] separators)
    {
        OrderedJSONObject tableInformation = null;

        // Store the table's data
        OrderedJSONObject tableData = getTableData(tableName,
                                                   false,
                                                   replaceMacros,
                                                   includeVariablePaths,
                                                   variableHandler,
                                                   separators,
                                                   new OrderedJSONObject());

        // Check that the table loaded successfully
        if (tableData != null)
        {
            tableInformation = new OrderedJSONObject();

            // Store the table's name, type, description, data, and data fields
            tableInformation.put(JSONTags.TABLE_NAME.getTag(), tableName);
            tableInformation.put(JSONTags.TABLE_TYPE.getTag(), tableInfo.getType());
            tableInformation.put(JSONTags.TABLE_DESCRIPTION.getTag(), tableInfo.getDescription());
            tableInformation.put(JSONTags.TABLE_DATA.getTag(),
                                 tableData.get(JSONTags.TABLE_DATA.getTag()));
            tableInformation = getDataFields(tableName,
                                             JSONTags.TABLE_FIELD.getTag(),
                                             null,
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
     * @param referencedInputTypes
     *            list of user-defined input types referenced; null if this list isn't used by the
     *            caller. This list has any user-defined input types not already in the list added
     *
     * @param outputJO
     *            JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the table type definitions added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getTableTypeDefinitions(List<String> tableTypeNames,
                                                        List<String> referencedInputTypes,
                                                        OrderedJSONObject outputJO)
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
                    OrderedJSONObject tableTypeJO;

                    // CHeck if the referenced input types list should be updated
                    if (referencedInputTypes != null)
                    {
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
                            // Check if if the input type is user-defined and this input type is
                            // not already output
                            if (fieldInfo.getInputType().isCustomInput()
                                &&
                                !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                            {
                                // Add the input type to the list of those referenced
                                referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                            }
                        }
                    }

                    // Step through each column definition in the table type, skipping the primary
                    // key and row index columns
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                    {
                        tableTypeJO = new OrderedJSONObject();
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
                    tableTypeJO = new OrderedJSONObject();
                    tableTypeJO.put(JSONTags.TABLE_TYPE_NAME.getTag(), refTableType);
                    tableTypeJO.put(JSONTags.TABLE_TYPE_DESCRIPTION.getTag(),
                                    tableTypeDefn.getDescription());
                    tableTypeJO.put(JSONTags.TABLE_TYPE_COLUMN.getTag(), typeDefnJA);
                    tableTypeJO = getDataFields(CcddFieldHandler.getFieldTypeName(refTableType),
                                                JSONTags.TABLE_TYPE_FIELD.getTag(),
                                                referencedInputTypes,
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
    protected OrderedJSONObject getDataTypeDefinitions(List<String> dataTypeNames, OrderedJSONObject outputJO)
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
                    OrderedJSONObject dataTypeJO = new OrderedJSONObject();
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
    protected OrderedJSONObject getInputTypeDefinitions(List<String> inputTypeNames,
                                                        OrderedJSONObject outputJO)
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
                    OrderedJSONObject inputTypeJO = new OrderedJSONObject();
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
    protected OrderedJSONObject getMacroDefinitions(List<String> macroNames, OrderedJSONObject outputJO)
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
                    OrderedJSONObject macroJO = new OrderedJSONObject();
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
    protected OrderedJSONObject getReservedMsgIDDefinitions(OrderedJSONObject outputJO)
    {
        // Check if there are any reserved message IDs defined
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
        {
            JSONArray reservedMsgIDJA = new JSONArray();

            // Step through each reserved message ID definition
            for (String[] reservedMsgIDDefn : rsvMsgIDHandler.getReservedMsgIDData())
            {
                // Store the macro name and value
                OrderedJSONObject macroJO = new OrderedJSONObject();
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
    protected OrderedJSONObject getVariablePaths(List<String[]> variablePaths,
                                                 OrderedJSONObject outputJO)
    {
        // Check if there are any variable paths to output
        if (!variablePaths.isEmpty())
        {
            JSONArray variablePathJA = new JSONArray();

            // Step through each variable path
            for (String[] variablePath : variablePaths)
            {
                // Store the variable path in application and user-defined formats
                OrderedJSONObject pathJO = new OrderedJSONObject();
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
    protected OrderedJSONObject getMessageIDAndNames()
    {
        OrderedJSONObject outputJO = new OrderedJSONObject();

        // Get the list of message ID names and associated ID values
        List<String[]> msgIDs = ccddMain.getMessageIDHandler().getMessageOwnersNamesAndIDs(MessageIDSortOrder.BY_NAME,
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
                OrderedJSONObject msgIDJO = new OrderedJSONObject();
                msgIDJO.put(MsgIDTableColumnInfo.OWNER.getColumnName(),
                            msgID[MsgIDListColumnIndex.OWNER.ordinal()]);
                msgIDJO.put(MsgIDTableColumnInfo.MESSAGE_NAME.getColumnName(),
                            msgID[MsgIDListColumnIndex.MESSAGE_NAME.ordinal()]);
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

    /**********************************************************************************************
     * Get the description for the specified group or application, or all groups/applications with
     * a description if no group name is provided
     *
     * @param groupName
     *            group name. If blank then every group's (application's) descriptions are returned
     *
     * @param applicationOnly
     *            true if only groups that represent applications should be processed
     *
     * @param includeNameTag
     *            true to include the group name and description tags
     *
     * @return JSON encoded string containing the specified group's (application's) description;
     *         null if the specified group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application has no description
     *         or if all groups/applications are requested but none have a description
     *
     * @throws CCDDException
     *             If an error occurs while parsing the group description
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected String getGroupDescription(String groupName,
                                         boolean applicationOnly,
                                         boolean includeNameTag) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all groups/applications)
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
                        // Get the description for this group as a JSON string, then format it as a
                        // JSON object so that is can be added to the response array. This is
                        // needed to get the brackets and commas in the JSON formatted string
                        // correct
                        responseJA.add(parser.parse(getGroupDescription(name,
                                                                        applicationOnly,
                                                                        true)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing "
                                                + (applicationOnly
                                                                   ? "application"
                                                                   : "group")
                                                + " description");
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

            // Check if the group exists and that either all groups are requested or else an
            // application is requested and this group represents an application
            if (groupInfo != null && (!applicationOnly || groupInfo.isApplication()))
            {
                OrderedJSONObject groupNameAndDesc = new OrderedJSONObject();

                // Get the description. If no description exists then use a blank
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

    /**********************************************************************************************
     * Get the tables associated with the specified group or application, or for all
     * groups/applications if no group name is provided
     *
     * @param groupName
     *            group name. If blank then every group's (application's) descriptions are returned
     *
     * @param applicationOnly
     *            true if only groups that represent applications should be processed
     *
     * @param includeNameTag
     *            true to include the group name item
     *
     * @return JSON encoded string containing the specified group's (application's) table members;
     *         null if the specified group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application has no table member
     *         or if all groups/applications are requested but none have a table member
     *
     * @throws CCDDException
     *             If an error occurs while parsing the group tables
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected String getGroupTables(String groupName,
                                    boolean applicationOnly,
                                    boolean includeNameTag) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all groups/applications)
        if (groupName.isEmpty())
        {
            JSONArray responseJA = new JSONArray();
            JSONParser parser = new JSONParser();

            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                // Step through each group/application name
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the data for this group as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getGroupTables(name,
                                                                   applicationOnly,
                                                                   true)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing "
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

            // Check if the group exists and that either all groups are requested or else an
            // application is requested and this group represents an application
            if (groupInfo != null
                && (!applicationOnly
                    || groupInfo.isApplication()))
            {
                JSONArray dataJA = new JSONArray();

                // Get the list of the group's tables
                List<String> tables = groupInfo.getTablesAndAncestors();

                // Step through each table
                for (String table : tables)
                {
                    // Add the table to the array
                    dataJA.add(table);
                }

                // Add the group name and description to the list. An array is used to preserve the
                // order of the items
                OrderedJSONObject groupNameAndTable;

                // Add the group tables. If the group has no tables then the table data is blank
                groupNameAndTable = new OrderedJSONObject();

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

    /**********************************************************************************************
     * Get the data field information for the specified group or application, or for all
     * groups/applications if no group name is provided
     *
     * @param groupName
     *            group name. If blank then every data table's data fields are returned
     *
     * @param applicationOnly
     *            true if only groups that represent applications should be processed
     *
     * @param includeNameTag
     *            true to include the group name item and data field tag
     *
     * @param referencedInputTypes
     *            list of user-defined input types referenced; null if this list isn't used by the
     *            caller. This list has any user-defined input types not already in the list added
     *
     * @return JSON encoded string containing the specified group's data fields; null if the group
     *         doesn't exist or if the project database contains no groups, or blank if the group
     *         contains no data fields
     *
     * @throws CCDDException
     *             If an error occurs while parsing the group data fields
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected String getGroupFields(String groupName,
                                    boolean applicationOnly,
                                    boolean includeNameTag,
                                    List<String> referencedInputTypes) throws CCDDException
    {
        String response = null;

        // Check if no group name is provided (i.e., get the fields for all groups/applications)
        if (groupName.isEmpty())
        {
            // Get an array containing all group/application names
            String[] groupNames = groupHandler.getGroupNames(applicationOnly);

            // Check if any groups/applications exist
            if (groupNames.length != 0)
            {
                JSONArray responseJA = new JSONArray();
                JSONParser parser = new JSONParser();

                // Step through each group/application name
                for (String name : groupNames)
                {
                    try
                    {
                        // Get the fields for this group as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getGroupFields(name,
                                                                   applicationOnly,
                                                                   true,
                                                                   referencedInputTypes)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing "
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

            // Check if the group exists and that either all groups are requested or else an
            // application is requested and this group represents an application
            if (groupInfo != null && (!applicationOnly || groupInfo.isApplication()))
            {
                JSONArray groupFieldsJA = new JSONArray();

                // Check if the group has any fields
                if (!fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(groupName)).isEmpty())
                {
                    // Get the group data fields (extract the data field array from the table field
                    // tag)
                    OrderedJSONObject fieldsJO = getDataFields(CcddFieldHandler.getFieldGroupName(groupName),
                                                               JSONTags.GROUP_FIELD.getTag(),
                                                               referencedInputTypes,
                                                               new OrderedJSONObject());
                    groupFieldsJA = (JSONArray) fieldsJO.get(JSONTags.GROUP_FIELD.getTag());
                }

                // Check if the name tag is to be included
                if (includeNameTag)
                {
                    // Add the group name and group data fields to the output
                    OrderedJSONObject groupNameAndFields = new OrderedJSONObject();
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
                // Don't include the name and field tags
                else
                {
                    // Add the data fields to the output
                    response = groupFieldsJA.toString();
                }
            }
        }

        return response;
    };

    /**********************************************************************************************
     * Get the description, associated table(s), and data fields for the specified group or
     * application
     *
     * @param groupName
     *            group name. If blank then every group's information is returned
     *
     * @param applicationOnly
     *            true if only groups that represent applications should be processed
     *
     * @param referencedInputTypes
     *            list of user-defined input types referenced; null if this list isn't used by the
     *            caller. This list has any user-defined input types not already in the list added
     *
     * @return JSON encoded string containing the specified group/application information; null if
     *         a group name is specified and the group/application doesn't exist or if no
     *         groups/applications exist in the project database
     *
     * @throws CCDDException
     *             If an error occurs while parsing the group information
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected String getGroupInformation(String groupName,
                                         boolean applicationOnly,
                                         List<String> referencedInputTypes) throws CCDDException
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

        // Check if no group name is provided (i.e., get the fields for all groups/applications)
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
                        // Get the fields for this group as a JSON string, then format it as a JSON
                        // object so that is can be added to the response array. This is needed to
                        // get the brackets and commas in the JSON formatted string correct
                        responseJA.add(parser.parse(getGroupInformation(name,
                                                                        applicationOnly,
                                                                        referencedInputTypes)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing " + groupType + " information");
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

            // Check if the group exists and that either all groups are requested or else an
            // application is requested and this group represents an application
            if (groupInfo != null && (!applicationOnly || groupInfo.isApplication()))
            {
                try
                {
                    // Store the group's name, description, tables, and data fields
                    OrderedJSONObject groupInfoJO = new OrderedJSONObject();
                    groupInfoJO.put(nameTag, groupName);
                    groupInfoJO.put(descriptionTag,
                                    getGroupDescription(groupName,
                                                        applicationOnly,
                                                        false));
                    groupInfoJO.put(JSONTags.GROUP_IS_APPLICATION.getTag(),
                                    Boolean.toString(groupInfo.isApplication()));
                    groupInfoJO.put(tableTag,
                                    parser.parse(getGroupTables(groupName,
                                                                applicationOnly,
                                                                false)));
                    groupInfoJO.put(dataFieldTag,
                                    parser.parse(getGroupFields(groupName,
                                                                applicationOnly,
                                                                false,
                                                                referencedInputTypes)));

                    // Convert the response object to a JSON string
                    response = groupInfoJO.toString();
                }
                catch (ParseException pe)
                {
                    throw new CCDDException("Error parsing " + groupType + " information");
                }
            }
        }

        return response;
    };

    /**********************************************************************************************
     * Add the script association(s) to the specified JSON object. If no associations exist, then
     * nothing is added to the JSON object
     *
     * @param outputJO
     *            JSON object to which the script associations are added
     *
     * @return The supplied JSON object, with the script associations added (if any)
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getScriptAssociations(OrderedJSONObject outputJO)
    {
        JSONArray scriptAssnJA = new JSONArray();

        // Get the script association information
        associations = scriptHandler.getScriptAssociations(parent);

        // Check if any associations exist
        if (!associations.isEmpty())
        {
            OrderedJSONObject assnJO = new OrderedJSONObject();

            // Step through each script association
            for (String[] assn : associations)
            {
                assnJO = new OrderedJSONObject();

                // Add the association column values to the output
                assnJO.put(AssociationsTableColumnInfo.NAME.getColumnName(),
                           assn[AssociationsTableColumnInfo.NAME.ordinal()]);
                assnJO.put(AssociationsTableColumnInfo.DESCRIPTION.getColumnName(),
                           assn[AssociationsTableColumnInfo.DESCRIPTION.ordinal()]);
                assnJO.put(AssociationsTableColumnInfo.SCRIPT_FILE.getColumnName(),
                           assn[AssociationsTableColumnInfo.SCRIPT_FILE.ordinal()]);
                assnJO.put(AssociationsTableColumnInfo.MEMBERS.getColumnName(),
                           CcddScriptHandler.convertAssociationMembersFormat(assn[AssociationsTableColumnInfo.MEMBERS.ordinal()],
                                                                             false));
                scriptAssnJA.add(assnJO);
            }

            // Check if any script association exists
            if (!scriptAssnJA.isEmpty())
            {
                // Add the script association(s) to the JSON output
                outputJO.put(JSONTags.SCRIPT_ASSOCIATION.getTag(), scriptAssnJA);
            }
        }

        return outputJO;
    }
}
