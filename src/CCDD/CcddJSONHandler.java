/**************************************************************************************************
 * /** \file CcddJSONHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling import and export of data tables in JSON format. This class implements
 * the CcddImportExportInterface class.
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

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.EXPORT_MULTIPLE_FILES;
import static CCDD.CcddConstants.EXPORT_SINGLE_FILE;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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

import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.OrderedJSONObject;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.AssociationsTableColumnInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.FileNames;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.InputTypeEditorColumnInfo;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.AppSchedulerComment;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerComments;
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
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDbControlHandler dbControl;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private final CcddGroupHandler groupHandler;
    private final CcddScriptHandler scriptHandler;
    private final CcddApplicationParameterHandler appHandler;
    private final CcddRateParameterHandler rateHandler;
    private TableInfo tableInfo;

    // GUI component over which to center any error dialog
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // List of original and new script associations
    private List<String[]> associations;

    private List<String[]> tlmSchedulerData;

    private List<String[]> appSchedulerData;

    /**********************************************************************************************
     * JSON handler class constructor
     *
     * @param ccddMain     Main class reference
     *
     * @param groupHandler Group handler reference
     *
     * @param parent       GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddJSONHandler(CcddMain ccddMain, CcddGroupHandler groupHandler, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        dbControl = ccddMain.getDbControlHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        scriptHandler = ccddMain.getScriptHandler();
        this.groupHandler = groupHandler;
        rateHandler = ccddMain.getRateParameterHandler();
        appHandler = ccddMain.getApplicationParameterHandler();
        setMacroHandler(macroHandler);

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
     * Get the list of original and new telemetry scheduler messages
     *
     * @return List of original and new telemetry scheduler messages; null if no telemetry
     *         scheduler messages have been added
     *
     *********************************************************************************************/
    @Override
    public List<String[]> getTlmSchedulerData()
    {
        return tlmSchedulerData;
    }

    /**********************************************************************************************
     * Get the list of original and new application scheduler data
     *
     * @return List of original and new application scheduler data; null if no new data has been
     *         added
     *********************************************************************************************/
    @Override
    public List<String[]> getAppSchedulerData()
    {
        return appSchedulerData;
    }

    /**********************************************************************************************
     * Get the JSON object referred to by the supplied key from the supplied JSON object
     *
     * @param jsonObj JSON object
     *
     * @param key     JSON object key
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
     * @param jsonObj JSON object
     *
     * @param key     JSON object key
     *
     * @return String representation of the supplied object; blank if the object is null
     *********************************************************************************************/
    String getString(JSONObject jsonObj, String key)
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
     * @param arrayObj Object representing a JSON array
     *
     * @return List containing the parsed JSON objects in the supplied JSON array
     *
     * @throws ParseException If an error occurs while attempting to parse the JSON object
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
    @SuppressWarnings("unchecked")
    List<JSONObject> parseJSONArray(Object arrayObj) throws ParseException
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
     * @param jsonObj JSON object
     *
     * @return Parsed JSON object
     *
     * @throws ParseException If an error occurs while attempting to parse the JSON object
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                resultJO.put(obj.toString(), parseJSONObject((JSONObject) jsonObj.get(obj)));
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
     * Build the information from the Application scheduler, Telemetry Scheduler and Script
     * Association data in the file
     *
     * @param importFile                  Import file reference
     *
     * @param importType                  ImportType.IMPORT_ALL to import the table type, data
     *                                    type, and macro definitions, and the data from all the
     *                                    table definitions; ImportType.FIRST_DATA_ONLY to load
     *                                    only the data for the first table defined
     *
     * @param ignoreErrors                True to ignore all errors in the import file
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importInternalTables(FileEnvVar importFile,
                                     ImportType importType,
                                     boolean ignoreErrors,
                                     boolean replaceExistingAssociations) throws CCDDException,
                                                                                 IOException,
                                                                                 Exception
    {
        BufferedReader br = null;

        try
        {
            // Detect the type of the parsed JSON file and only accept JSONObjects This will throw
            // an exception if it is incorrect
            verifyJSONObjectType(importFile);
            // Create a JSON parser and use it to parse the import file contents
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(importFile));

            /*************** APPLICATION SCHEDULER ***************/
            if (importType == ImportType.IMPORT_ALL)
            {
                Object defn = jsonObject.get(JSONTags.APP_SCHEDULER_COMMENT.getTag());

                // Check if the table comment exists
                if (defn != null && defn instanceof JSONArray)
                {

                    // Step through each parameter stored in the comment
                    for (JSONObject appSchJO : parseJSONArray(defn))
                    {
                        // Get each parameter value stored in the table comment
                        int maxMsgsPerTimeSlot = Integer.parseInt(getString(appSchJO,
                                                                            AppSchedulerComment.MAXIMUM_MESSAGES_PER_TIME_SLOT.getName()));
                        int maxMsgsPerSec = Integer.parseInt(getString(appSchJO,
                                                                       AppSchedulerComment.MAXIMUM_MESSAGES_PER_SECOND.getName()));
                        int maxMsgsPerCycle = Integer.parseInt(getString(appSchJO,
                                                                         AppSchedulerComment.MAXIMUM_MESSAGES_PER_CYCLE.getName()));
                        int numberOfTimeSlots = Integer.parseInt(getString(appSchJO,
                                                                           AppSchedulerComment.NUMBER_OF_TIME_SLOTS.getName()));

                        // Set and store the parameter values obtained from the JSON file
                        appHandler.setApplicationParameters(maxMsgsPerSec,
                                                            maxMsgsPerCycle,
                                                            maxMsgsPerTimeSlot,
                                                            numberOfTimeSlots,
                                                            parent);
                    }
                }

                // Get the application scheduler table
                defn = jsonObject.get(JSONTags.APP_SCHEDULER.getTag());

                // Check if the application scheduler data exists
                if (defn != null && defn instanceof JSONArray)
                {
                    List<String[]> appData = new ArrayList<String[]>();

                    // Step through each stored column
                    for (JSONObject appSchJO : parseJSONArray(defn))
                    {
                        // Add the data to the dummy list
                        appData.add(new String[] {getString(appSchJO, AppSchedulerColumn.APP_INFO.getColumnName()),
                                                  getString(appSchJO, AppSchedulerColumn.TIME_SLOT.getColumnName())});
                    }

                    // Store the data in the actual list
                    appSchedulerData = appData;
                }

                /*************** TELEMETRY SCHEDULER ***************/
                // Get the telemetry scheduler table
                defn = jsonObject.get(JSONTags.TLM_SCHEDULER.getTag());

                // Check if the telemetry messages exist
                if (defn != null && defn instanceof JSONArray)
                {
                    List<String[]> tlmData = new ArrayList<String[]>();

                    // Step through each message in the table
                    for (JSONObject tlmSchJO : parseJSONArray(defn))
                    {
                        // Add each message
                        tlmData.add(new String[] {getString(tlmSchJO, TlmSchedulerColumn.RATE_NAME.getColumnName()),
                                                  getString(tlmSchJO, TlmSchedulerColumn.MESSAGE_NAME.getColumnName()),
                                                  getString(tlmSchJO, TlmSchedulerColumn.MESSAGE_ID.getColumnName()),
                                                  getString(tlmSchJO, TlmSchedulerColumn.MEMBER.getColumnName())});
                    }

                    // Store the messages
                    tlmSchedulerData = tlmData;
                }

                // Get the telemetry scheduler table comment
                defn = jsonObject.get(JSONTags.TLM_SCHEDULER_COMMENT.getTag());

                // Check if the telemetry scheduler table comment exists
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through the rate parameters stored in the comment
                    for (JSONObject tlmSchComment : parseJSONArray(defn))
                    {
                        // Store the minimum rate parameters available
                        int maxMsgsPerSec = Integer.valueOf(getString(tlmSchComment, JSONTags.MAXIMUM_MESSAGES_PER_SECOND.getTag()));
                        int maxSecPerMsg = Integer.valueOf(getString(tlmSchComment, JSONTags.MAXIMUM_SECONDS_PER_MESSAGE.getTag()));
                        boolean includeUneven = Boolean.valueOf(getString(tlmSchComment, JSONTags.INCLUDE_UNEVEN_RATES.getTag()));

                        // Create new object to check if there are more parameters stored
                        Object rateInfo = getObject(tlmSchComment, JSONTags.RATE_INFORMATION.getTag());
                        int[] maxMsgsPerCycleArr = null;
                        int[] maxBytesPerSecArr = null;
                        String[] streamNameArr = null;

                        // Check if there are more rates stored
                        if (rateInfo != null && rateInfo instanceof JSONArray)
                        {
                            List<String> maxMsgPerCycleList = new ArrayList<String>();
                            List<String> maxBytesPerSecondList = new ArrayList<String>();
                            List<String> streamNameList = new ArrayList<String>();

                            // Step through each rate stored
                            for (JSONObject rates : parseJSONArray(rateInfo))
                            {
                                // Add the data stored for each rate variable
                                streamNameList.add(getString(rates,
                                                             TlmSchedulerComments.RATE_DATASTREAM_NAME.getName()));
                                maxMsgPerCycleList.add(getString(rates,
                                                                 TlmSchedulerComments.RATE_MAXIMUM_MESSAGES_PER_CYCLE.getName()));
                                maxBytesPerSecondList.add(getString(rates,
                                                                    TlmSchedulerComments.RATE_MAXIMUM_BYTES_PER_SECOND.getName()));
                            }

                            if (!maxMsgPerCycleList.isEmpty())
                            {
                                maxMsgsPerCycleArr = new int[maxMsgPerCycleList.size()];

                                // Store list values in an integer array
                                for (int index = 0; index < maxMsgsPerCycleArr.length; index++)
                                {
                                    maxMsgsPerCycleArr[index] = Integer.valueOf(maxMsgPerCycleList.get(index));
                                }
                            }

                            if (!maxBytesPerSecondList.isEmpty())
                            {
                                maxBytesPerSecArr = new int[maxBytesPerSecondList.size()];

                                // Store list values in an integer array
                                for (int index = 0; index < maxBytesPerSecArr.length; index++)
                                {
                                    maxBytesPerSecArr[index] = Integer.valueOf(maxBytesPerSecondList.get(index));
                                }
                            }

                            if (!streamNameList.isEmpty())
                            {
                                streamNameArr = new String[streamNameList.size()];

                                // Store list values in a String array
                                for (int index = 0; index < streamNameArr.length; index++)
                                {
                                    streamNameArr[index] = streamNameList.get(index);
                                }
                            }

                            // Set and store the rate parameters obtained from the JSON file
                            rateHandler.setRateParameters(maxSecPerMsg,
                                                          maxMsgsPerSec,
                                                          streamNameArr,
                                                          maxMsgsPerCycleArr,
                                                          maxBytesPerSecArr,
                                                          includeUneven,
                                                          parent);
                        }
                    }
                }

                /*************** SCRIPT ASSOCIATION ***************/
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
                        ignoreErrors = addImportedScriptAssociation(ignoreErrors,
                                                                    replaceExistingAssociations,
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
     * Verify that the input file will be of a JSONObject type
     *
     * @param importFile Import file reference
     *
     * @throws IOException    If an import file I/O error occurs
     *
     * @throws ParseException If parsing error occurs
     *
     * @throws CCDDException  If the parsed json object is not of type JSONObject
     *********************************************************************************************/
    protected void verifyJSONObjectType(FileEnvVar importFile) throws IOException,
                                                                      ParseException,
                                                                      CCDDException
    {
        // Create a JSON parser and use it to parse the import file contents
        JSONParser jsonParser = new JSONParser();
        FileReader jsonReader = new FileReader(importFile);

        // Detect the type of the parsed JSON file and only accept JSONObjects
        if (jsonParser.parse(jsonReader).getClass() != JSONObject.class)
        {
            throw new CCDDException(" The file contains an unexpected json format. Please ensure that the json"
                                    + " file contains data exported from CCDD. \nNote: json files generated through the CCDD"
                                    + " scripting functionality are not expected to be imported into CCDD");
        }

        return;
    }

    /**********************************************************************************************
     * Build the information from the table types (and table type data fields) and macros in the
     * file
     *
     * @param importFile              Import file reference
     *
     * @param ignoreErrors            True to ignore all errors in the import file
     *
     * @param importingEntireDatabase True to replace existing database internal tables
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importTableInfo(FileEnvVar importFile,
                                ImportType importType,
                                boolean ignoreErrors,
                                boolean replaceExistingMacros,
                                boolean replaceExistingTables,
                                boolean importingEntireDatabase) throws CCDDException,
                                                                        IOException,
                                                                        Exception
    {
        BufferedReader br = null;

        try
        {
            // Detect the type of the parsed JSON file and only accept JSONObjects This will throw
            // an exception if it is incorrect
            verifyJSONObjectType(importFile);

            // Create a JSON parser and use it to parse the import file contents
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(new FileReader(importFile));
            List<TableTypeDefinition> tableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            Object defn = jsonObject.get(JSONTags.TABLE_TYPE_DEFN.getTag());

            /*************** TABLE TYPES ***************/
            // Check if the table type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each table type definition
                for (JSONObject tableTypeJO : parseJSONArray(defn))
                {
                    // Get the table type definition components
                    String typeName = getString(tableTypeJO, JSONTags.TABLE_TYPE_NAME.getTag());
                    String typeDesc = getString(tableTypeJO, JSONTags.TABLE_TYPE_DESCRIPTION.getTag());

                    // If the table type represents a command argument then prepend a '1' to the
                    // front of the description. If not then prepend a '0'
                    String representsCommandArg = getString(tableTypeJO,
                                                            JSONTags.TABLE_REPRESENTS_COMMAND_ARG.getTag());

                    if (representsCommandArg.contentEquals("TRUE"))
                    {
                        typeDesc = "1" + typeDesc;
                    }
                    else
                    {
                        typeDesc = "0" + typeDesc;
                    }

                    Object typeColumn = getObject(tableTypeJO, JSONTags.TABLE_TYPE_COLUMN.getTag());

                    // Check if the expected inputs are present
                    if (!typeName.isEmpty() && typeColumn != null && typeColumn instanceof JSONArray)
                    {
                        int columnNumber = 0;

                        // Create a new table type definition
                        TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName, typeDesc);

                        // Step through each table type column definition
                        for (JSONObject typeJO : parseJSONArray(typeColumn))
                        {
                            // Check if the expected input is present
                            if (typeJO.keySet().size() == TableTypeEditorColumnInfo.values().length - 1)
                            {
                                // Add the table type column definition, checking for (and if
                                // possible, correcting) errors
                                ignoreErrors = addImportedTableTypeColumnDefinition(ignoreErrors,
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
                            else
                            {
                                // The number of inputs is incorrect Check if the error should be
                                // ignored or the import canceled
                                ignoreErrors = getErrorResponse(ignoreErrors,
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

                        /*************** TABLE TYPE DATA FIELDS ***************/
                        // Get the data fields for this table type
                        Object typeField = getObject(tableTypeJO, JSONTags.TABLE_TYPE_FIELD.getTag());

                        // Check if any data fields exists for this table type
                        if (typeField != null)
                        {
                            // Step through each table type data field definition
                            for (JSONObject typeJO : parseJSONArray(typeField))
                            {
                                // Add the data field definition, checking for (and if possible,
                                // correcting) errors
                                ignoreErrors = addImportedDataFieldDefinition(ignoreErrors,
                                                                              replaceExistingTables,
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
                                                                              inputTypeHandler, fieldHandler,
                                                                              parent);
                            }
                        }

                        // Add the table type definition to the list
                        tableTypeDefinitions.add(tableTypeDefn);
                    }
                }

                // Add the table type if it's new or update an existing one
                tableTypeHandler.updateTableTypes(tableTypeDefinitions);
            }

            /*************** MACROS ***************/
            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                List<String[]> macroDefns = new ArrayList<String[]>();

                // Get the macro definitions JSON object
                defn = jsonObject.get(JSONTags.MACRO_DEFN.getTag());

                // Check if the macro definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each macro definition
                    for (JSONObject macroJO : parseJSONArray(defn))
                    {
                        // Get the macro definition components
                        String name = getString(macroJO, MacroEditorColumnInfo.NAME.getColumnName());
                        String value = getString(macroJO, MacroEditorColumnInfo.VALUE.getColumnName());

                        // Check if the expected inputs are present
                        if (!name.isEmpty() && macroJO.keySet().size() < MacroEditorColumnInfo.values().length)
                        {
                            // Build the macro definition
                            String[] macroDefn = new String[] {name, value, ""};

                            // Check if the macro definition is valid
                            checkMacroDefinition(macroDefn);

                            // Add the macro definition (add a blank to represent the row number)
                            macroDefns.add(macroDefn);
                        }
                        else
                        {
                            // The number of inputs is incorrect Check if the error should be
                            // ignored or the import canceled
                            ignoreErrors = getErrorResponse(ignoreErrors,
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

                    // Convert all of the new macro definitions to a set of unique entries based on
                    // the macro name (the first array element) A pair with a boolean indicating if
                    // the input set was unique and the list of unique entries that were extracted
                    Pair<Boolean, List<String[]>> uniqueResults = convertToUniqueList(macroDefns);

                    boolean isDupDetected = !uniqueResults.getLeft();

                    if (isDupDetected)
                    {
                        // Get the user's input
                        if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html> <b> Continue import and ignore the duplicate values?",
                                                                      "Duplicate Macros in Input File",
                                                                      JOptionPane.QUESTION_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                        {
                            throw new CCDDException("Duplicate macros detected in the input file, user Cancelled");
                        }
                    }

                    // Add the macro if it's new or match it to an existing one with the same name.
                    // If the flag to replace existing macro values is false then get the list of
                    // macros names where the existing and import file values differ
                    List<String> mismatchedMacros = macroHandler.updateMacros(uniqueResults.getRight(),
                                                                              replaceExistingMacros);

                    // Check if any existing and import file macro values differ (the flag to
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
                                         "Ignore macro value difference(s) (keep existing value(s))", "Stop importing",
                                         true,
                                         parent);
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
     * Build the information from the input and data type definition(s) in the current file
     *
     * @param importFile               Import file reference
     *
     * @param ignoreErrors             True to ignore all errors in the import file
     *
     * @param replaceExistingDataTypes True to replace existing data types that share a name with
     *                                 an imported data type
     *
     * @param importingEntireDatabase  True to replace existing database internal tables
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importInputTypes(FileEnvVar importFile,
                                 ImportType importType,
                                 boolean ignoreErrors,
                                 boolean replaceExistingDataTypes,
                                 boolean importingEntireDatabase) throws CCDDException,
                                                                         IOException,
                                                                         Exception
    {
        BufferedReader br = null;

        try
        {
            // Detect the type of the parsed JSON file and only accept JSONObjects This will throw
            // an exception if it is incorrect
            verifyJSONObjectType(importFile);

            // Create a JSON parser and use it to parse the import file contents
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(importFile));
            List<String[]> inputTypeDefns = new ArrayList<String[]>();
            List<String[]> dataTypeDefns = new ArrayList<String[]>();

            /*************** INPUT TYPES ***************/
            // Get the input type definitions JSON object
            Object defn = jsonObject.get(JSONTags.INPUT_TYPE_DEFN.getTag());

            // Check if the input type definitions exist
            if (defn != null && defn instanceof JSONArray)
            {
                // Step through each input type definition
                for (JSONObject typeJO : parseJSONArray(defn))
                {
                    // Get the input type definition components
                    String name = getString(typeJO, InputTypeEditorColumnInfo.NAME.getColumnName());
                    String description = getString(typeJO, InputTypeEditorColumnInfo.DESCRIPTION.getColumnName());
                    String match = getString(typeJO, InputTypeEditorColumnInfo.MATCH.getColumnName());
                    String items = getString(typeJO, InputTypeEditorColumnInfo.ITEMS.getColumnName());
                    String format = getString(typeJO, InputTypeEditorColumnInfo.FORMAT.getColumnName());

                    // Check if the expected inputs are present
                    if (!name.isEmpty() && !match.isEmpty()
                        && typeJO.keySet().size() < InputTypeEditorColumnInfo.values().length)
                    {
                        // Check if the input type definition is valid
                        String[] inputTypeDefn = checkInputTypeDefinition(new String[] {name,
                                                                                        description,
                                                                                        match,
                                                                                        items,
                                                                                        format, ""});

                        // Add the input type definition (add a blank to represent the row number)
                        inputTypeDefns.add(inputTypeDefn);
                    }
                    else
                    {
                        // The number of inputs is incorrect Check if the error should be ignored
                        // or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
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

                // Add the input type if it's new or match it to an existing one with the same name
                // if the type definitions are the same
                inputTypeHandler.updateInputTypes(inputTypeDefns);
            }

            /*************** DATA TYPES ***************/
            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                // Get the data type definitions JSON object
                defn = jsonObject.get(JSONTags.DATA_TYPE_DEFN.getTag());

                // Check if the data type definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each data type definition
                    for (JSONObject typeJO : parseJSONArray(defn))
                    {
                        // Get the data type definition components
                        String userName = getString(typeJO, DataTypeEditorColumnInfo.USER_NAME.getColumnName());
                        String cName = getString(typeJO, DataTypeEditorColumnInfo.C_NAME.getColumnName());
                        String size = getString(typeJO, DataTypeEditorColumnInfo.SIZE.getColumnName());
                        String baseType = getString(typeJO, DataTypeEditorColumnInfo.BASE_TYPE.getColumnName());

                        // Check if the expected inputs are present
                        if ((!userName.isEmpty() || !cName.isEmpty())
                            && !size.isEmpty()
                            && !baseType.isEmpty()
                            && typeJO.keySet().size() < DataTypeEditorColumnInfo.values().length)
                        {
                            // Build the data type definition
                            String[] dataTypeDefn = new String[] {userName, cName, size, baseType, ""};

                            // Check if the data type definition is valid
                            checkDataTypeDefinition(dataTypeDefn);

                            // Add the data type definition (add a blank to represent the row number)
                            dataTypeDefns.add(dataTypeDefn);
                        }
                        else
                        {
                            // The number of inputs is incorrect Check if the error should be
                            // ignored or the import canceled
                            ignoreErrors = getErrorResponse(ignoreErrors,
                                                            "<html><b>Missing or extra data type definition "
                                                            + "input(s) in import file '</b>"
                                                            + importFile.getAbsolutePath()
                                                            + "<b>'; continue?",
                                                            "Data Type Error", "Ignore this data type",
                                                            "Ignore this and any remaining invalid data types",
                                                            "Stop importing",
                                                            parent);
                        }
                    }

                    if (importingEntireDatabase)
                    {
                        // Replace any existing data types with the new ones
                        dataTypeHandler.setDataTypeData(dataTypeDefns);
                    }
                    else
                    {
                        // Add the data type if it's new or match it to an existing one with the same
                        // name if the type definitions are the same
                        dataTypeHandler.updateDataTypes(dataTypeDefns, replaceExistingDataTypes);
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
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile              Import file reference
     *
     * @param importType              ImportType.IMPORT_ALL to import the table type, data type,
     *                                and macro definitions, and the data from all the table
     *                                definitions; ImportType.FIRST_DATA_ONLY to load only the data
     *                                for the first table defined
     *
     * @param targetTypeDefn          Table type definition of the table in which to import the
     *                                data; ignored if importing all tables
     *
     * @param ignoreErrors            True to ignore all errors in the import file
     *
     * @param replaceExistingMacros   True to replace the values for existing macros
     *
     * @param replaceExistingGroups   True to replace existing group definitions
     *
     * @param replaceExistingTables   True to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile,
                               ImportType importType,
                               TypeDefinition targetTypeDefn,
                               boolean ignoreErrors,
                               boolean replaceExistingMacros,
                               boolean replaceExistingGroups,
                               boolean replaceExistingTables) throws CCDDException,
                                                                     IOException,
                                                                     Exception
    {
        // Initialize a buffered reader
        BufferedReader br = null;

        try
        {
            // Detect the type of the parsed JSON file and only accept JSONObjects This will throw
            // an exception if it is incorrect
            verifyJSONObjectType(importFile);

            // Create a JSON parser and use it to parse the import file contents
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(new FileReader(importFile));
            ProjectDefinition projectDefn = new ProjectDefinition();
            tableDefinitions = new ArrayList<TableDefinition>();

            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                /*************** RESERVED MESSAGE IDS ***************/
                List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

                // Get the reserved message ID definitions JSON object
                Object defn = jsonObject.get(JSONTags.RESERVED_MSG_ID_DEFN.getTag());

                // Check if the reserved message ID definitions exist
                if (defn != null && defn instanceof JSONArray)
                {
                    // Step through each reserved message ID definition
                    for (JSONObject reservedMsgIDJO : parseJSONArray(defn))
                    {
                        // Get the reserved message ID definition components
                        String name = getString(reservedMsgIDJO, ReservedMsgIDEditorColumnInfo.MSG_ID.getColumnName());
                        String value = getString(reservedMsgIDJO,
                                                 ReservedMsgIDEditorColumnInfo.DESCRIPTION.getColumnName());

                        // Check if the expected inputs are present
                        if (!name.isEmpty()
                            && reservedMsgIDJO.keySet().size() < ReservedMsgIDEditorColumnInfo.values().length)
                        {
                            // Add the reserved message ID definition (add a blank to represent the
                            // row number)
                            reservedMsgIDDefns.add(new String[] {name, value, ""});
                        }
                        else
                        {
                            // The number of inputs is incorrect. Check if the error should be
                            // ignored or the import canceled
                            ignoreErrors = getErrorResponse(ignoreErrors,
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

                    // Overwrite the reserved message id data with the imported data
                    rsvMsgIDHandler.setReservedMsgIDData(reservedMsgIDDefns);
                }

                /*************** PROJECT FIELDS ***************/
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
                        ignoreErrors = addImportedDataFieldDefinition(ignoreErrors, false, projectDefn,
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

                    // Replace the old project fields with the new ones
                    fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldProjectName(),
                                                                fieldHandler.getFieldInformationFromDefinitions(projectDefn.getDataFields()));
                }

                /*************** GROUPS ***************/
                // Get a list of names for all of the groups currently defined within the database
                String[] currentGroupNames = groupHandler.getGroupNames(false);
                List<String> newGroupNames = new ArrayList<String>();

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
                        String description = getString(groupJO, JSONTags.GROUP_DESCRIPTION.getTag());
                        String isApplication = getString(groupJO, JSONTags.GROUP_IS_APPLICATION.getTag());
                        String members = "";

                        // Add the group name to the list
                        newGroupNames.add(name);

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
                                members += (isFirst ? "" : ";") + memberJO.get(index).toString();
                                index++;
                                isFirst = false;
                            }
                        }

                        // Check if the expected inputs are present
                        if (!name.isEmpty() && groupJO.keySet().size() <= GroupDefinitionColumn.values().length + 1)
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
                        else
                        {
                            // The number of inputs is incorrect. Check if the error should be
                            // ignored or the import canceled
                            ignoreErrors = getErrorResponse(ignoreErrors,
                                                            "<html><b>Missing or extra group definition "
                                                            + "input(s) in import file '</b>"
                                                            + importFile.getAbsolutePath()
                                                            + "<b>'; continue?",
                                                            "Group Error", "Ignore this group",
                                                            "Ignore this and any remaining invalid groups",
                                                            "Stop importing",
                                                            parent);
                        }

                        /*************** GROUP FIELDS ***************/
                        // Get the data fields for this group
                        Object groupField = getObject(groupJO, JSONTags.GROUP_FIELD.getTag());
                        List<FieldInformation> newFieldInformation = new ArrayList<FieldInformation>();

                        // Check if any data fields exists for this group
                        if (groupField != null)
                        {
                            // Step through each group data field definition
                            for (JSONObject grpFldJO : parseJSONArray(groupField))
                            {
                                InputType inputType = inputTypeHandler.getInputTypeByName(getString(grpFldJO,
                                                                                                    FieldEditorColumnInfo.INPUT_TYPE.getColumnName()));
                                FieldInformation newField = new FieldInformation("",
                                                                                 "",
                                                                                 "",
                                                                                 null,
                                                                                 0,
                                                                                 false,
                                                                                 null,
                                                                                 "",
                                                                                 false,
                                                                                 null,
                                                                                 0);

                                newField.setFieldName(getString(grpFldJO, FieldEditorColumnInfo.NAME.getColumnName()));
                                newField.setDescription(getString(grpFldJO,
                                                                  FieldEditorColumnInfo.DESCRIPTION.getColumnName()));
                                newField.setSize(Integer.parseInt(getString(grpFldJO,
                                                                  FieldEditorColumnInfo.CHAR_SIZE.getColumnName())));
                                newField.setInputType(inputType);
                                newField.setRequired(Boolean.parseBoolean(getString(grpFldJO,
                                                                                    FieldEditorColumnInfo.REQUIRED.getColumnName())));
                                newField.setApplicabilityType(ApplicabilityType.getApplicabilityByName(getString(grpFldJO,
                                                                                                                 FieldEditorColumnInfo.APPLICABILITY.getColumnName())));
                                newField.setValue(getString(grpFldJO, FieldEditorColumnInfo.VALUE.getColumnName()));
                                newField.setInherited(Boolean.parseBoolean(getString(grpFldJO,
                                                                                     FieldEditorColumnInfo.INHERITED.getColumnName())));

                                newFieldInformation.add(newField);
                            }

                            // Replace the old field information with the new field information
                            fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(name),
                                                                        newFieldInformation);
                        }
                    }

                    // Determine which groups have been deleted
                    List<String> deletedGroups = new ArrayList<String>();

                    // Search the list of new group names for every name in the current group names
                    // list. Each name that is not found in the new list will be added to the list
                    // of deleted groups
                    for (int index = 0; index < currentGroupNames.length; index++)
                    {
                        if (!newGroupNames.contains(currentGroupNames[index]))
                        {
                            deletedGroups.add(currentGroupNames[index]);

                            // Remove the group's information
                            groupHandler.removeGroupInformation(currentGroupNames[index]);
                        }
                    }

                    // Update internal groups table
                    dbTable.updateGroupsTable(fieldHandler.getGroupFieldInformationAsListOfArrays(),
                                              deletedGroups,
                                              ccddMain.getMainFrame());
                }

                /*************** DATABASE INFORMATION ***************/
                // Get the database information
                defn = jsonObject.get(JSONTags.DBU_INFO.getTag());

                // Check if the database information exists
                if (defn != null)
                {
                    for (JSONObject dbInfoJO : parseJSONArray(defn))
                    {
                        dbControl.renameDatabase(dbControl.getDatabaseName(),
                                                 dbControl.getDatabaseName(),
                                                 getString(dbInfoJO,
                                                           JSONTags.DB_DESCRIPTION.getTag()));
                    }
                }
            }

            /*************** TABLE DEFINITIONS ***************/
            // Get the table definitions JSON object
            Object defn = jsonObject.get(JSONTags.TABLE_DEFN.getTag());

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
                        && (tableDataJA == null || tableDataJA instanceof JSONArray)
                        && (dataFieldsJA == null || dataFieldsJA instanceof JSONArray))
                    {
                        // Create a new table type definition
                        TableDefinition tableDefn = new TableDefinition(tableName, tableDesc);

                        // Get the table's type definition. If importing into an existing table
                        // then use its type definition
                        TypeDefinition typeDefn = importType == ImportType.IMPORT_ALL ? tableTypeHandler.getTypeDefinition(tableType)
                                                                                      : targetTypeDefn;

                        // Check if the table type doesn't exist
                        if (typeDefn == null)
                        {
                            throw new CCDDException("Unknown table type '</b>" + tableType + "<b>'");
                        }

                        // Store the table's type name
                        tableDefn.setTypeName(tableType);

                        // Get the number of expected columns (the hidden columns, primary key and
                        // row index, should not be included in the JSON file)
                        int numColumns = typeDefn.getColumnCountVisible();

                        if (tableDataJA != null)
                        {
                            // Create storage for the row of cell data
                            String[] rowData = new String[numColumns];

                            // Step through each row of data in the table
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
                                        // Get the value from the JSON input, if present; use a
                                        // blank if a value for this column doesn't exist
                                        rowData[column] = getString(rowDataJO,
                                                                    typeDefn.getColumnNamesVisible()[column]);
                                    }
                                    else
                                    {
                                        // The number of inputs is incorrect. Check if the error
                                        // should be ignored or the import canceled
                                        ignoreErrors = getErrorResponse(ignoreErrors,
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
                        }

                        /*************** TABLE DEFINITION FIELDS ***************/
                        // Check if all definitions are to be loaded and if any data fields are
                        // defined
                        if (dataFieldsJA != null)
                        {
                            // Step through each data field definition
                            for (JSONObject dataFieldJO : parseJSONArray(dataFieldsJA))
                            {
                                // Add the data field definition, checking for (and if possible,
                                // correcting) errors
                                ignoreErrors = addImportedDataFieldDefinition(ignoreErrors,
                                                                              replaceExistingTables,
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
     * Export the project for a JSON export
     *
     * @param exportFile              Reference to the user-specified output file
     *
     * @param tableDefs               List of table definitions to convert
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param replaceMacros           True to replace any embedded macros with their corresponding
     *                                values
     *
     * @param includeVariablePaths    True to include the variable path for each variable in a
     *                                structure table, both in application format and using the
     *                                user-defined separator characters
     *
     * @param variableHandler         Variable handler class reference; null if
     *                                includeVariablePaths is false
     *
     * @param separators              String array containing the variable path separator
     *                                character(s), show/hide data types flag ('true' or 'false'),
     *                                and data type/variable name separator character(s); null if
     *                                includeVariablePaths is false
     *
     * @param addEOFMarker            Is this the last data to be added to the file?
     *
     * @param extraInfo               Unused
     *
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @SuppressWarnings({"unchecked"})
    @Override
    public void exportTables(FileEnvVar exportFile,
                             List<TableInfo> tableDefs,
                             boolean includeBuildInformation,
                             boolean replaceMacros,
                             boolean includeVariablePaths,
                             CcddVariableHandler variableHandler,
                             String[] separators,
                             boolean addEOFMarker,
                             String outputType,
                             Object... extraInfo) throws CCDDException, Exception
    {
        // Initialize local variables
        List<String[]> variablePaths = new ArrayList<String[]>();
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        try
        {
            OrderedJSONObject outputJO = new OrderedJSONObject();
            JSONArray tableJA = new JSONArray();

            // Check if the build information is to be output
            if (includeBuildInformation)
            {
                // Output the build information
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

                // If outputType equals SINGLE_FILE than set includeBuildInformation to false so
                // that it is not added multiple times
                if (outputType.contentEquals(EXPORT_SINGLE_FILE))
                {
                    includeBuildInformation = false;
                }
            }

            // Step through each table
            for (int counter = 0; counter < tableDefs.size() && counter < tableDefs.size(); counter++)
            {
                // Use of the JSONObject does not retain the order that the key:value pairs are
                // stored. This custom JSON object is used so that the stored order is reflected in
                // the output
                OrderedJSONObject tableInfoJO = new OrderedJSONObject();

                // Store the table's name, type, description, data, and data fields
                tableInfoJO.put(JSONTags.TABLE_NAME.getTag(), tableDefs.get(counter).getTablePath());
                tableInfoJO.put(JSONTags.TABLE_TYPE.getTag(), tableDefs.get(counter).getType());
                tableInfoJO.put(JSONTags.TABLE_DESCRIPTION.getTag(), tableDefs.get(counter).getDescription());

                // Store the table data in a JSONArray
                JSONArray data = (JSONArray) convertTableData(tableDefs.get(counter),
                                                              replaceMacros,
                                                              includeVariablePaths,
                                                              variableHandler,
                                                              separators).get(JSONTags.TABLE_DATA.getTag());
                tableInfoJO.put(JSONTags.TABLE_DATA.getTag(), data);

                // Grab the data field info
                tableInfoJO = getDataFields(tableDefs.get(counter).getTablePath(),
                                            JSONTags.TABLE_FIELD.getTag(),
                                            null,
                                            tableInfoJO);

                // Check if the table's data successfully loaded
                if (tableInfoJO != null && !tableInfoJO.isEmpty())
                {
                    // Add the wrapper for the table
                    tableJA.add(tableInfoJO);

                    // Get the table type definition based on the type name
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefs.get(counter).getType());

                    // Check if variable paths are to be output and if this table represents a
                    // structure
                    if (includeVariablePaths && typeDefn.isStructure())
                    {
                        // Step through each row in the table
                        for (int row = 0; row < tableDefs.get(counter).getData().size(); row++)
                        {
                            // Get the variable path
                            String variablePath = tableDefs.get(counter).getTablePath()
                                                  + ","
                                                  + tableDefs.get(counter).getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)]
                                                  + "."
                                                  + tableDefs.get(counter).getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

                            // Add the path, in both application and user-defined formats, to the
                            // list to be output
                            variablePaths.add(new String[] {variablePath,
                                                            variableHandler.getFullVariableName(variablePath,
                                                                                                separators[0],
                                                                                                Boolean.parseBoolean(separators[1]),
                                                                                                separators[2])});
                        }
                    }
                }
            }

            // Create a JavaScript engine for use in formatting the JSON output
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");

            // Output the ordered and formatted JSON object to the file
            StringWriter orderedOutput = new StringWriter();

            // Check if any tables were processed successfully
            if (tableJA != null)
            {
                // Add the table information to the JSON output
                outputJO.put(JSONTags.TABLE_DEFN.getTag(), tableJA);
            }

            // Check if variable paths are to be output
            if (includeVariablePaths)
            {
                // Add the variable paths, if any, to the output
                outputJO = getVariablePaths(variablePaths, outputJO);
            }

            JSONValue.writeJSONString(outputJO, orderedOutput);

            // Output the table data to the selected file. Multiple writers are needed in case
            // tables are appended to an existing file
            fw = new FileWriter(exportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            scriptEngine.put("jsonString", orderedOutput.toString());
            scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");

            if ((outputType.contentEquals(EXPORT_SINGLE_FILE)) && (!addEOFMarker))
            {
                int size = ((String) scriptEngine.get("result")).length() - 3;
                pw.println(((String) scriptEngine.get("result")).substring(0, size) + "],");
            }
            else
            {
                pw.println((String) scriptEngine.get("result"));
            }

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
            catch (Exception e)
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
        catch (IOException | ScriptException ioe)
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
            catch (Exception e)
            {
                // Inform the user that the data file cannot be closed
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Cannot export file '</b>"
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
     * @param tableName            Table name and path in the format
     *                             rootTable[,dataType1.variable1[,...]]. Blank to return the data
     *                             for all tables
     *
     * @param getDescription       True to get the table description when loading the table data
     *
     * @param replaceMacros        True to display the macro values in place of the corresponding
     *                             macro names; false to display the macro names
     *
     * @param includeVariablePaths True to include a column, 'Variable Path', showing the variable
     *                             path for each variable in a structure table using the
     *                             user-defined separator characters
     *
     * @param variableHandler      Variable handler class reference; null if isIncludePath is false
     *
     * @param separators           String array containing the variable path separator
     *                             character(s), show/hide data types flag ('true' or 'false'), and
     *                             data type/variable name separator character(s); null if
     *                             isIncludePath is false
     *
     * @param outputJO             JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the table data added (if any); null if the table
     *         doesn't exists or an error occurs when loading the data. Empty table cells are
     *         omitted
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
            if (tableInfo.getData().size() != 0)
            {
                // Get the column names for this table's type definition
                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());
                String[] columnNames = typeDefn.getColumnNamesUser();

                // Step through each table row
                for (int row = 0; row < tableInfo.getData().size(); row++)
                {
                    columnJO = new OrderedJSONObject();

                    // Step through each table column
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData().get(row).length; column++)
                    {
                        // Check if a cell isn't blank
                        if (!tableInfo.getData().get(row)[column].toString().isEmpty())
                        {
                            // Add the column name and value to the cell object
                            columnJO.put(columnNames[column], tableInfo.getData().get(row)[column]);

                            // Check if the table represents a structure, that the variable path
                            // column is to be included, and that a variable handler and path
                            // separators are supplied
                            if (typeDefn.isStructure()
                                && includeVariablePaths
                                && variableHandler != null
                                && separators != null)
                            {
                                // Get the variable's data type
                                String dataType = tableInfo.getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)].toString();

                                // Check if the data type is a primitive
                                if (dataTypeHandler.isPrimitive(dataType))
                                {
                                    // Build the variable's full path
                                    String variablePath = tableInfo.getTablePath()
                                                          + ","
                                                          + dataType
                                                          + "."
                                                          + tableInfo.getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

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
     * Convert the table data to JSONArray format
     *
     * @param tableInfo            The data for the table to be converted
     *
     * @param replaceMacros        True to display the macro values in place of the corresponding
     *                             macro names; false to display the macro names
     *
     * @param includeVariablePaths True to include a column, 'Variable Path', showing the variable
     *                             path for each variable in a structure table using the
     *                             user-defined separator characters
     *
     * @param variableHandler      Variable handler class reference; null if isIncludePath is false
     *
     * @param separators           String array containing the variable path separator
     *                             character(s), show/hide data types flag ('true' or 'false'), and
     *                             data type/variable name separator character(s); null if
     *                             isIncludePath is false
     *
     *
     * @return The table data in OrderedJSONObject format
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject convertTableData(TableInfo tableInfo,
                                                 boolean replaceMacros,
                                                 boolean includeVariablePaths,
                                                 CcddVariableHandler variableHandler,
                                                 String[] separators)
    {
        JSONArray tableDataJA = null;
        OrderedJSONObject outputJO = new OrderedJSONObject();

        // Check if the table exists and successfully loaded
        if (tableInfo != null)
        {
            OrderedJSONObject columnJO = new OrderedJSONObject();
            tableDataJA = new JSONArray();

            // Check if the macro names should be replaced with the corresponding macro values
            if (replaceMacros)
            {
                // Replace all macros in the table
                tableInfo = (macroHandler.replaceAllMacros(tableInfo));
            }

            // Check if the table has any data
            if (tableInfo.getData().size() != 0)
            {
                // Get the column names for this table's type definition
                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());
                String[] columnNames = typeDefn.getColumnNamesUser();
                List<Integer> booleanColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.BOOLEAN);

                // Step through each table row
                for (int row = 0; row < tableInfo.getData().size(); row++)
                {
                    columnJO = new OrderedJSONObject();

                    // Step through each table column
                    for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData().get(row).length; column++)
                    {
                        // Check if a cell isn't blank
                        if (!tableInfo.getData().get(row)[column].toString().isEmpty()
                            || booleanColumns.contains(column))
                        {
                            if (booleanColumns.contains(column))
                            {
                                // Add the column name and value to the cell object
                                columnJO.put(columnNames[column],
                                             Boolean.parseBoolean(tableInfo.getData().get(row)[column].toString()));
                            }
                            else
                            {
                                // Add the column name and value to the cell object
                                columnJO.put(columnNames[column], tableInfo.getData().get(row)[column]);
                            }

                            // Check if the table represents a structure, that the variable path
                            // column is to be included, and that a variable handler and path
                            // separators are supplied
                            if (typeDefn.isStructure() && includeVariablePaths && variableHandler != null
                                && separators != null)
                            {
                                // Get the variable's data type
                                String dataType = tableInfo.getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)].toString();

                                // Check if the data type is a primitive
                                if (dataTypeHandler.isPrimitive(dataType))
                                {
                                    // Build the variable's full path
                                    String variablePath = tableInfo.getTablePath()
                                                          + ","
                                                          + dataType
                                                          + "."
                                                          + tableInfo.getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

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
            else
            {
                tableDataJA = new JSONArray();
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
     * @param ownerName            Table name and path in the format
     *                             rootTable[,dataType1.variable1[,...]] (if blank then every data
     *                             table's data fields are returned), table type name in the format
     *                             tableTypeTag:tableTypepName, or group owner in the format
     *                             groupTag:groupName
     *
     * @param tagName              JSON tag name
     *
     * @param referencedInputTypes List of user-defined input types referenced; null if this list
     *                             isn't used by the caller. This list has any user-defined input
     *                             types not already in the list added
     *
     * @param outputJO             JSON object to which the data fields are added
     *
     * @return The supplied JSON object, with the data field(s) added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                if (referencedInputTypes != null && fieldInfo.getInputType().isCustomInput()
                    && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
                {
                    // Add the input type to the list of those referenced
                    referencedInputTypes.add(fieldInfo.getInputType().getInputName());
                }

                // Add the data field column values to the output
                fieldJO.put(FieldEditorColumnInfo.NAME.getColumnName(),
                            fieldInfo.getFieldName());
                fieldJO.put(FieldEditorColumnInfo.DESCRIPTION.getColumnName(),
                            fieldInfo.getDescription());
                fieldJO.put(FieldEditorColumnInfo.CHAR_SIZE.getColumnName(),
                            fieldInfo.getSize());
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
     * Grab the database name, description and users. Each piece of data will be separated by a
     * comma.
     *
     * @param outputJO JSON object from which to extract the information
     *
     * @return The database information separated by a comma.
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getDBUInfo(OrderedJSONObject outputJO)
    {
        JSONArray DBUInfoJA = new JSONArray();

        String[] comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());

        if (comment.length > 0)
        {
            OrderedJSONObject DBUInfoJO = new OrderedJSONObject();

            // Export the database name
            DBUInfoJO.put("Database Name", comment[1]);

            // Export the user access level information
            String[][] usersAndAcessLevel = dbTable.retrieveInformationTable(InternalTable.USERS,
                                                                             true,
                                                                             ccddMain.getMainFrame())
                                                   .toArray(new String[0][0]);
            String usersAndAcessLevelDataToExport = "";

            // Each user will be added with their individual access level in the format of
            // "user1:accessLevel,user2:accessLevel"
            for (int index = 0; index < usersAndAcessLevel.length; index++)
            {
                usersAndAcessLevelDataToExport += usersAndAcessLevel[index][0]
                                                  + ":"
                                                  + usersAndAcessLevel[index][1];

                if (index != usersAndAcessLevel.length - 1)
                {
                    usersAndAcessLevelDataToExport += ",";
                }
            }

            DBUInfoJO.put("Database Users", usersAndAcessLevelDataToExport);

            // Export the database description
            DBUInfoJO.put("Database Description", comment[3]);
            DBUInfoJA.add(DBUInfoJO);

            // Check if any data field exists
            if (!DBUInfoJA.isEmpty())
            {
                // Add the data field(s) to the JSON output
                outputJO.put(JSONTags.DBU_INFO.getTag(), DBUInfoJA);
            }
        }

        return outputJO;
    }

    /**********************************************************************************************
     * Export script association data, group data, macro data, telemetry scheduler data or
     * application scheduler data to the specified folder
     *
     * @param includes   The types of table to be included in the export
     *
     * @param dataTypes  The data types that are about to be exported
     *
     * @param exportFile Reference to the user-specified output file
     *
     * @param outputType String representing rather the output is going to a single file or
     *                   multiple files. Should be EXPORT_SINGLE_FILE or EXPORT_MULTIPLE_FILES
     *
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void exportInternalCCDDData(boolean[] includes,
                                       CcddConstants.exportDataTypes[] dataTypes,
                                       FileEnvVar exportFile,
                                       String outputType) throws CCDDException, Exception
    {
        // Create a set of writers for the output file(s)
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        boolean fwCreated = false;
        int counter = 0;
        List<String> referencedInputTypes = new ArrayList<String>();
        List<String> referencedMacros = new ArrayList<String>();
        OrderedJSONObject outputJO = new OrderedJSONObject();

        for (CcddConstants.exportDataTypes dataType : dataTypes)
        {
            if (includes[counter] == true)
            {
                try
                {
                    // Are we exporting this database to multiple files or a single file
                    if (outputType.contentEquals(EXPORT_SINGLE_FILE) && !fwCreated)
                    {
                        // Single file
                        fw = new FileWriter(exportFile, true);
                        bw = new BufferedWriter(fw);
                        pw = new PrintWriter(bw);

                        fwCreated = true;
                    }

                    // Multiple files
                    switch (dataType)
                    {
                        case GROUPS:
                            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                fw = new FileWriter(exportFile + "/" + FileNames.GROUPS.JSON(), false);
                                bw = new BufferedWriter(fw);
                                pw = new PrintWriter(bw);
                                outputJO = new OrderedJSONObject();
                            }

                            // Get the group information
                            String groupInfo = getGroupInformation("", false, referencedInputTypes);

                            // Check if any groups exist
                            if (groupInfo != null)
                            {
                                // Add the group information, if any, to the output
                                JSONParser parser = new JSONParser();
                                outputJO.put(JSONTags.GROUP.getTag(), parser.parse(groupInfo));
                            }

                            break;

                        case MACROS:
                            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                fw = new FileWriter(exportFile + "/" + FileNames.MACROS.JSON(), false);
                                bw = new BufferedWriter(fw);
                                pw = new PrintWriter(bw);
                                outputJO = new OrderedJSONObject();
                            }

                            // Get the macros information
                            referencedMacros.addAll(macroHandler.getMacroNames());
                            outputJO = getMacroDefinitions(referencedMacros, outputJO);
                            break;

                        case ASSOCIATIONS:
                            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                fw = new FileWriter(exportFile + "/" + FileNames.SCRIPT_ASSOCIATION.JSON(), false);
                                bw = new BufferedWriter(fw);
                                pw = new PrintWriter(bw);
                                outputJO = new OrderedJSONObject();
                            }

                            // Get the Script association data
                            getScriptAssociations(outputJO);
                            break;

                        case TELEMSCHEDULER:
                            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                fw = new FileWriter(exportFile + "/" + FileNames.TELEM_SCHEDULER.JSON(), false);
                                bw = new BufferedWriter(fw);
                                pw = new PrintWriter(bw);
                                outputJO = new OrderedJSONObject();
                            }

                            // Get the telemetry scheduler data
                            getTlmSchedulerData(outputJO);
                            break;

                        case APPSCHEDULER:
                            if (outputType .contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                fw = new FileWriter(exportFile + "/" + FileNames.APP_SCHEDULER.JSON(), false);
                                bw = new BufferedWriter(fw);
                                pw = new PrintWriter(bw);
                                outputJO = new OrderedJSONObject();
                            }

                            // Get the application scheduler data
                            getAppSchedulerData(outputJO);
                            break;

                        case RESERVED_MSG_ID:
                            if (!getReservedMsgIDDefinitions(outputJO).isEmpty())
                            {
                                if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                                {
                                    fw = new FileWriter(exportFile + "/" + FileNames.RESERVED_MSG_ID.JSON(), false);
                                    bw = new BufferedWriter(fw);
                                    pw = new PrintWriter(bw);
                                    outputJO = new OrderedJSONObject();
                                }

                                // Get the reserved message id data
                                outputJO = getReservedMsgIDDefinitions(outputJO);
                            }

                            break;

                        case PROJECT_FIELDS:
                            if (!getDataFields(CcddFieldHandler.getFieldProjectName(),
                                               JSONTags.PROJECT_FIELD.getTag(),
                                               referencedInputTypes,
                                               outputJO).isEmpty())
                            {
                                if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                                {
                                    fw = new FileWriter(exportFile + "/" + FileNames.PROJECT_DATA_FIELD.JSON(), false);
                                    bw = new BufferedWriter(fw);
                                    pw = new PrintWriter(bw);
                                    outputJO = new OrderedJSONObject();
                                }

                                // Get the project data fields data
                                outputJO = getDataFields(CcddFieldHandler.getFieldProjectName(),
                                                         JSONTags.PROJECT_FIELD.getTag(),
                                                         referencedInputTypes,
                                                         outputJO);
                            }

                            break;

                        case DBU_INFO:
                            if (!getDBUInfo(outputJO).isEmpty())
                            {
                                if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                                {
                                    fw = new FileWriter(exportFile + "/" + FileNames.DBU_INFO.JSON(), false);
                                    bw = new BufferedWriter(fw);
                                    pw = new PrintWriter(bw);
                                    outputJO = new OrderedJSONObject();
                                }

                                // Get the project data fields data
                                outputJO = getDBUInfo(outputJO);
                            }

                            break;
                    }

                    if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                    {
                        if (outputJO != null && !outputJO.isEmpty())
                        {
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
                    }
                }
                catch (IOException | ScriptException iose)
                {
                    throw new CCDDException(iose.getMessage());
                }
                finally
                {
                    if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
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

            counter++;
        }

        try
        {
            if (outputType.contentEquals(EXPORT_SINGLE_FILE))
            {
                if (outputJO != null && !outputJO.isEmpty())
                {
                    // Create a JavaScript engine for use in formatting the JSON output
                    ScriptEngineManager manager = new ScriptEngineManager();
                    ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");

                    // Output the ordered and formatted JSON object to the file
                    StringWriter orderedOutput = new StringWriter();
                    JSONValue.writeJSONString(outputJO, orderedOutput);
                    scriptEngine.put("jsonString", orderedOutput.toString());
                    scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");
                    pw.println(((String) scriptEngine.get("result")).substring(1));
                }
            }
        }
        catch (IOException | ScriptException iose)
        {
            throw new CCDDException(iose.getMessage());
        }
        finally
        {
            if (outputType.contentEquals(EXPORT_SINGLE_FILE))
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

    /**********************************************************************************************
     * Export table type, input type, and data types definitions to the specified folder
     *
     * @param exportFile        Reference to the user-specified output file
     *
     * @param includeTableTypes Boolean representing if the table types should be included
     *
     * @param includeInputTypes Boolean representing if the input types should be included
     *
     * @param includeDataTypes  Boolean representing if the data types should be included
     *
     * @param outputType        String representing rather the output is going to a single file or
     *                          multiple files. Should be EXPORT_SINGLE_FILE or
     *                          EXPORT_MULTIPLE_FILES
     *
     * @param addEOFMarker      Is this the last data to be added to the file?
     *
     * @param addSOFMarker      Is this the first data to be added to the file?
     *
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/

    public void exportTableInfoDefinitions(FileEnvVar exportFile,
                                           boolean includeTableTypes,
                                           boolean includeInputTypes,
                                           boolean includeDataTypes,
                                           String outputType,
                                           boolean addEOFMarker,
                                           boolean addSOFMarker) throws CCDDException, Exception
    {
        List<String> referencedTableTypes = new ArrayList<String>();
        List<String> referencedInputTypes = new ArrayList<String>();
        List<String> referencedDataTypes = new ArrayList<String>();

        // create a set of writers for the group output file
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        try
        {
            // Determine the output type so that the correct naming convention can be used
            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
            {
                fw = new FileWriter(exportFile + File.separator + FileNames.TABLE_INFO.JSON(), false);
            }
            else
            {
                fw = new FileWriter(exportFile, true);
            }

            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            OrderedJSONObject outputJO = new OrderedJSONObject();

            if (includeInputTypes == true)
            {
                referencedInputTypes.addAll(Arrays.asList(inputTypeHandler.getNames(true)));
                outputJO = getInputTypeDefinitions(referencedInputTypes, outputJO);
            }

            if (includeTableTypes == true)
            {
                referencedTableTypes.addAll(Arrays.asList(tableTypeHandler.getTableTypeNames()));
                outputJO = getTableTypeDefinitions(referencedTableTypes, referencedInputTypes, outputJO);
            }

            if (includeDataTypes == true)
            {
                referencedDataTypes.addAll(dataTypeHandler.getDataTypeNames());
                outputJO = getDataTypeDefinitions(referencedDataTypes, outputJO);
            }

            if (outputJO != null)
            {
                // Create a JavaScript engine for use in formatting the JSON output
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine scriptEngine = manager.getEngineByName("JavaScript");

                // Output the ordered and formatted JSON object to the file
                StringWriter orderedOutput = new StringWriter();
                JSONValue.writeJSONString(outputJO, orderedOutput);
                scriptEngine.put("jsonString", orderedOutput.toString());
                scriptEngine.eval("result = JSON.stringify(JSON.parse(jsonString), null, 2)");

                if (outputType.contentEquals(EXPORT_SINGLE_FILE) && !addEOFMarker)
                {
                    int size = ((String) scriptEngine.get("result")).length() - 3;
                    pw.println(((String) scriptEngine.get("result")).substring(1, size) + "],");
                }
                else if (!addSOFMarker)
                {
                    pw.println(((String) scriptEngine.get("result")).substring(1));
                }
                else
                {
                    pw.println((String) scriptEngine.get("result"));
                }
            }
        }
        catch (
                IOException | ScriptException iose)
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
     * Get the type, description, size, data, and data fields for the specified data table
     *
     * @param tableName               Table name and path in the format
     *                                rootTable[,dataType1.variable1[,...]]
     *
     * @param replaceMacros           True to display the macro values in place of the
     *                                corresponding macro names; false to display the macro names
     *
     * @param includeVariablePaths    True to include a column, 'Variable Path', showing the
     *                                variable path for each variable in a structure table using
     *                                the user-defined separator characters
     *
     * @param includeBuildInformation True to include the build information
     *
     * @param variableHandler         Variable handler class reference
     *
     * @param separators              String array containing the variable path separator
     *                                character(s), show/hide data types flag ('true' or 'false'),
     *                                and data type/variable name separator character(s)
     *
     * @return JSON encoded string containing the specified table information; null if the
     *         specified table doesn't exist or fails to load
     *********************************************************************************************/
    protected OrderedJSONObject getTableInformation(String tableName,
                                                    boolean replaceMacros,
                                                    boolean includeVariablePaths,
                                                    boolean includeBuildInformation,
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
        JSONArray data;

        // Check that the table loaded successfully
        if (tableData != null)
        {
            tableInformation = new OrderedJSONObject();

            // Check if the build information is to be output
            if (includeBuildInformation)
            {
                // Create the file creation comment
                tableInformation.put(JSONTags.FILE_DESCRIPTION.getTag(),
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

            // Store the table's name, type, description, data, and data fields
            tableInformation.put(JSONTags.TABLE_NAME.getTag(), tableName);
            tableInformation.put(JSONTags.TABLE_TYPE.getTag(), tableInfo.getType());
            tableInformation.put(JSONTags.TABLE_DESCRIPTION.getTag(), tableInfo.getDescription());

            if ((JSONArray) tableData.get(JSONTags.TABLE_DATA.getTag()) == null)
            {
                data = new JSONArray();
            }
            else
            {
                data = (JSONArray) tableData.get(JSONTags.TABLE_DATA.getTag());
            }

            tableInformation.put(JSONTags.TABLE_DATA.getTag(), data);
            tableInformation = getDataFields(tableName, JSONTags.TABLE_FIELD.getTag(), null, tableInformation);

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
     * @param tableTypeNames       Names of the table types to add; null to include all defined
     *                             table types
     *
     * @param referencedInputTypes List of user-defined input types referenced; null if this list
     *                             isn't used by the caller. This list has any user-defined input
     *                             types not already in the list added
     *
     * @param outputJO             JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the table type definitions added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                            if (inputType.isCustomInput() && !referencedInputTypes.contains(inputType.getInputName()))
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
                                && !referencedInputTypes.contains(fieldInfo.getInputType().getInputName()))
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
                    tableTypeJO.put(JSONTags.TABLE_TYPE_DESCRIPTION.getTag(), tableTypeDefn.getDescription());
                    tableTypeJO.put(JSONTags.TABLE_REPRESENTS_COMMAND_ARG.getTag(),
                                    tableTypeDefn.representsCommandArg());
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
     * @param dataTypeNames Names of the data types to add; null to include all defined data types
     *
     * @param outputJO      JSON object to which the data types are added
     *
     * @return The supplied JSON object, with the data type definitions added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getDataTypeDefinitions(List<String> dataTypeNames,
                                                       OrderedJSONObject outputJO)
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
     * @param inputTypeNames Names of the custom input types to add; null to include all defined
     *                       custom input types
     *
     * @param outputJO       JSON object to which the input types are added
     *
     * @return The supplied JSON object, with the custom input type definitions added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                    inputTypeJO.put(InputTypeEditorColumnInfo.MATCH.getColumnName(), inputType.getInputMatch());
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
     * @param macroNames Names of the macros to add; null to include all defined macros
     *
     * @param outputJO   JSON object to which the macros are added
     *
     * @return The supplied JSON object, with the macro definitions added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getMacroDefinitions(List<String> macroNames,
                                                    OrderedJSONObject outputJO)
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
     * @param outputJO JSON object to which the reserved message IDs are added
     *
     * @return The supplied JSON object, with the reserved message ID definitions added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
     * @param variablePaths List containing arrays of the variable path in both application and
     *                      user-defined formats
     *
     * @param outputJO      JSON object to which the variable paths are added
     *
     * @return The supplied JSON object, with the variable paths added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getVariablePaths(List<String[]> variablePaths, OrderedJSONObject outputJO)
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
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                msgIDJO.put(MsgIDTableColumnInfo.OWNER.getColumnName(), msgID[MsgIDListColumnIndex.OWNER.ordinal()]);
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
     * @param groupName       Group name. If blank then every group's (application's) descriptions
     *                        are returned
     *
     * @param applicationOnly True if only groups that represent applications should be processed
     *
     * @param includeNameTag  True to include the group name and description tags
     *
     * @return JSON encoded string containing the specified group's (application's) description;
     *         null if the specified group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application has no description
     *         or if all groups/applications are requested but none have a description
     *
     * @throws CCDDException If an error occurs while parsing the group description
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                        responseJA.add(parser.parse(getGroupDescription(name, applicationOnly, true)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing "
                                                + (applicationOnly ? "application" : "group")
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
                response = groupInfo.getDescription() != null ? groupInfo.getDescription() : "";

                // Check if the group name is to be included
                if (includeNameTag)
                {
                    // Add the group name and description to the output
                    groupNameAndDesc.put((applicationOnly ? JSONTags.APPLICATION_NAME.getTag()
                                                          : JSONTags.GROUP_NAME.getTag()),
                                         groupName);
                    groupNameAndDesc.put((applicationOnly ? JSONTags.APPLICATION_DESCRIPTION.getTag()
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
     * @param groupName       Group name. If blank then every group's (application's) descriptions
     *                        are returned
     *
     * @param applicationOnly True if only groups that represent applications should be processed
     *
     * @param includeNameTag  True to include the group name item
     *
     * @return JSON encoded string containing the specified group's (application's) table members;
     *         null if the specified group/application doesn't exist or the project has no
     *         groups/applications, or blank if the specified group/application has no table member
     *         or if all groups/applications are requested but none have a table member
     *
     * @throws CCDDException If an error occurs while parsing the group tables
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                        responseJA.add(parser.parse(getGroupTables(name, applicationOnly, true)));
                    }
                    catch (ParseException pe)
                    {
                        throw new CCDDException("Error parsing "
                                                + (applicationOnly ? "application" : "group")
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
            if (groupInfo != null && (!applicationOnly || groupInfo.isApplication()))
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
                    groupNameAndTable.put((applicationOnly ? JSONTags.APPLICATION_NAME.getTag()
                                                           : JSONTags.GROUP_NAME.getTag()),
                                          groupName);
                    groupNameAndTable.put((applicationOnly ? JSONTags.APPLICATION_TABLE.getTag()
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
     * @param groupName            Group name. If blank then every data table's data fields are
     *                             returned
     *
     * @param applicationOnly      True if only groups that represent applications should be
     *                             processed
     *
     * @param includeNameTag       True to include the group name item and data field tag
     *
     * @param referencedInputTypes List of user-defined input types referenced; null if this list
     *                             isn't used by the caller. This list has any user-defined input
     *                             types not already in the list added
     *
     * @return JSON encoded string containing the specified group's data fields; null if the group
     *         doesn't exist or if the project database contains no groups, or blank if the group
     *         contains no data fields
     *
     * @throws CCDDException If an error occurs while parsing the group data fields
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                                                + (applicationOnly ? "application" : "group")
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
                    groupNameAndFields.put((applicationOnly ? JSONTags.APPLICATION_NAME.getTag()
                                                            : JSONTags.GROUP_NAME.getTag()),
                                           groupName);
                    groupNameAndFields.put((applicationOnly ? JSONTags.APPLICATION_FIELD.getTag()
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
     * @param groupName            Group name. If blank then every group's information is returned
     *
     * @param applicationOnly      True if only groups that represent applications should be
     *                             processed
     *
     * @param referencedInputTypes List of user-defined input types referenced; null if this list
     *                             isn't used by the caller. This list has any user-defined input
     *                             types not already in the list added
     *
     * @return JSON encoded string containing the specified group/application information; null if
     *         a group name is specified and the group/application doesn't exist or if no
     *         groups/applications exist in the project database
     *
     * @throws CCDDException If an error occurs while parsing the group information
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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
                    groupInfoJO.put(descriptionTag, getGroupDescription(groupName,
                                                                        applicationOnly,
                                                                        false));
                    groupInfoJO.put(JSONTags.GROUP_IS_APPLICATION.getTag(),
                                    Boolean.toString(groupInfo.isApplication()));
                    groupInfoJO.put(tableTag, parser.parse(getGroupTables(groupName,
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
     * @param outputJO JSON object to which the script associations are added
     *
     * @return The supplied JSON object, with the script associations added (if any)
     *********************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
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

    /****************************************************************************************************
     * Add the telemetry scheduler data, including the table comments, to the specified JSON
     * object. If no data is stored, then nothing is added to the JSON object
     *
     * @param outputJO JSON object to which the telemetry scheduler data is added.
     *
     * @return The supplied JSON object, with the telemetry scheduler data in it (if any)
     ***************************************************************************************************/
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getTlmSchedulerData(OrderedJSONObject outputJO)
    {
        // Retrieve the information stored in the Telemetry Scheduler table
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.TLM_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        // Check if there was any data stored in the telemetry scheduler
        if (!storedData.isEmpty())
        {
            JSONArray tlmSchedulerJA = new JSONArray();

            // Step through each column in the table
            for (String[] data : storedData)
            {
                OrderedJSONObject tlmSchedulerJO = new OrderedJSONObject();

                // Add the table column values to the output
                tlmSchedulerJO.put(TlmSchedulerColumn.RATE_NAME.getColumnName(),
                                   data[TlmSchedulerColumn.RATE_NAME.ordinal()]);
                tlmSchedulerJO.put(TlmSchedulerColumn.MESSAGE_NAME.getColumnName(),
                                   data[TlmSchedulerColumn.MESSAGE_NAME.ordinal()]);
                tlmSchedulerJO.put(TlmSchedulerColumn.MESSAGE_ID.getColumnName(),
                                   data[TlmSchedulerColumn.MESSAGE_ID.ordinal()]);
                tlmSchedulerJO.put(TlmSchedulerColumn.MEMBER.getColumnName(),
                                   data[TlmSchedulerColumn.MEMBER.ordinal()]);

                tlmSchedulerJA.add(tlmSchedulerJO);
            }

            // Check if any data was retrieved from the table
            if (!tlmSchedulerJA.isEmpty())
            {
                // Add the table data to the JSON output
                outputJO.put(JSONTags.TLM_SCHEDULER.getTag(), tlmSchedulerJA);
            }
        }

        // Query the Telemetry Scheduler table to obtain the table comments
        String[] comments = dbTable.queryTableComment(InternalTable.TLM_SCHEDULER.getTableName(), 0, parent);

        // Check if there were any comments stored for the telemetry scheduler table
        if (!(comments.length == 0))
        {
            JSONArray tlmSchedulerCommentJA = new JSONArray();
            OrderedJSONObject tlmSchCommentJO = new OrderedJSONObject();

            // There are three parameters that will be required by each rate; add them to the
            // output
            tlmSchCommentJO.put(JSONTags.MAXIMUM_SECONDS_PER_MESSAGE.getTag(), comments[0]);
            tlmSchCommentJO.put(JSONTags.MAXIMUM_MESSAGES_PER_SECOND.getTag(), comments[1]);
            tlmSchCommentJO.put(JSONTags.INCLUDE_UNEVEN_RATES.getTag(), comments[2]);

            // Check if there is any rate information stored in the table comment
            if (comments.length > 3)
            {
                JSONArray rateInfoJA = new JSONArray();

                // Every rate will have four parameters. Check how many different rates there are
                int groups = (comments.length - 3) / 4;

                // Start of iteration, since we already stored the first three values of the array
                int count = 3;

                // Step through each group of rate information for each rate
                for (int index = 0; index < groups; index++)
                {
                    OrderedJSONObject rateInfoJO = new OrderedJSONObject();

                    // Step through each parameter for the current rate
                    for (int rateInfo = 0; rateInfo < 4; rateInfo++)
                    {
                        // Store each parameter with their corresponding value
                        switch (rateInfo)
                        {
                            case 0:
                                rateInfoJO.put(TlmSchedulerComments.RATE_COLUMN_NAME.getName(),
                                               comments[count].substring(1, comments[count].length() - 1));
                                count++;
                                break;
                            case 1:
                                rateInfoJO.put(TlmSchedulerComments.RATE_DATASTREAM_NAME.getName(),
                                               comments[count].substring(1, comments[count].length() - 1));
                                count++;
                                break;
                            case 2:
                                rateInfoJO.put(TlmSchedulerComments.RATE_MAXIMUM_MESSAGES_PER_CYCLE.getName(),
                                               comments[count]);
                                count++;
                                break;
                            case 3:
                                rateInfoJO.put(TlmSchedulerComments.RATE_MAXIMUM_BYTES_PER_SECOND.getName(),
                                               comments[count]);
                                count++;
                                break;
                        }
                    }
                    rateInfoJA.add(rateInfoJO);
                }
                tlmSchCommentJO.put(JSONTags.RATE_INFORMATION.getTag(), rateInfoJA);
            }

            tlmSchedulerCommentJA.add(tlmSchCommentJO);

            // Check if any data was retrieved from the telemetry scheduler table comment
            if (!tlmSchedulerCommentJA.isEmpty())
            {
                // Add the comment data to the table comment output
                outputJO.put(JSONTags.TLM_SCHEDULER_COMMENT.getTag(), tlmSchedulerCommentJA);
            }
        }
        return outputJO;
    }

    /****************************************************************************************************
     * Add the application scheduler data, including the table comments, to the specified JSON
     * object If no data is stored, then nothing is added to the JSON object
     *
     * @param outputJO JSON object to which the application scheduler data is added.
     *
     * @return The supplied JSON object, with the application scheduler data in it (if any)
     ***************************************************************************************************/
    // Suppress warnings is used because when this code is compiled some of it is not legal, but it
    // will be at runtime
    @SuppressWarnings("unchecked")
    protected OrderedJSONObject getAppSchedulerData(OrderedJSONObject outputJO)
    {

        // Retrieve the information stored at the app Scheduler table
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.APP_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        if (!storedData.isEmpty())
        {
            JSONArray appSchedulerJA = new JSONArray();

            for (String[] data : storedData)
            {
                OrderedJSONObject appSchedulerJO = new OrderedJSONObject();

                // Add the table column values to the output
                appSchedulerJO.put(AppSchedulerColumn.TIME_SLOT.getColumnName(),
                                   data[AppSchedulerColumn.TIME_SLOT.ordinal()]);
                appSchedulerJO.put(AppSchedulerColumn.APP_INFO.getColumnName(),
                                   data[AppSchedulerColumn.APP_INFO.ordinal()]);

                appSchedulerJA.add(appSchedulerJO);
            }

            if (!appSchedulerJA.isEmpty())
            {
                outputJO.put(JSONTags.APP_SCHEDULER.getTag(), appSchedulerJA);
            }
        }

        // Query the Application Scheduler table to obtain the table comments
        String[] comments = dbTable.queryTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                                      0,
                                                      parent);

        if (!(comments.length == 0))
        {
            JSONArray appCommentJA = new JSONArray();
            OrderedJSONObject appCommentJO = new OrderedJSONObject();

            for (int index = 0; index < comments.length; index++)
            {
                switch (index)
                {
                    case 0:
                        appCommentJO.put(AppSchedulerComment.MAXIMUM_MESSAGES_PER_TIME_SLOT.getName(),
                                         comments[index]);
                        break;

                    case 1:
                        appCommentJO.put(AppSchedulerComment.MAXIMUM_MESSAGES_PER_SECOND.getName(),
                                         comments[index]);
                        break;

                    case 2:
                        appCommentJO.put(AppSchedulerComment.MAXIMUM_MESSAGES_PER_CYCLE.getName(),
                                         comments[index]);
                        break;

                    case 3:
                        appCommentJO.put(AppSchedulerComment.NUMBER_OF_TIME_SLOTS.getName(),
                                         comments[index]);
                        break;
                }
            }
            appCommentJA.add(appCommentJO);

            if (!appCommentJA.isEmpty())
            {
                outputJO.put(JSONTags.APP_SCHEDULER_COMMENT.getTag(), appCommentJA);
            }
        }
        return outputJO;
    }

    /**********************************************************************************************
     * Retrieve various pieces of data from JSON files
     *
     * @param searchKey What to search for
     *
     * @param data      The string to be searched
     *
     * @return THe retrieved data based on the key
     *********************************************************************************************/
    public StringBuilder retrieveJSONData(String searchKey, StringBuilder data)
    {
        StringBuilder outData = new StringBuilder();

        if (searchKey == JSONTags.INPUT_TYPE_DEFN.getTag()
            || searchKey == JSONTags.TABLE_TYPE_DEFN.getTag()
            || searchKey == JSONTags.DATA_TYPE_DEFN.getTag()
            || searchKey == JSONTags.MACRO_DEFN.getTag()
            || searchKey == JSONTags.GROUP.getTag()
            || searchKey == JSONTags.SCRIPT_ASSOCIATION.getTag()
            || searchKey == JSONTags.TLM_SCHEDULER_COMMENT.getTag()
            || searchKey == JSONTags.APP_SCHEDULER_COMMENT.getTag()
            || searchKey == JSONTags.RESERVED_MSG_ID_DEFN.getTag()
            || searchKey == JSONTags.PROJECT_FIELD.getTag()
            || searchKey == JSONTags.TABLE_DEFN.getTag()
            || searchKey == JSONTags.DBU_INFO.getTag())
        {
            int beginIndex = data.toString().indexOf("\"" + searchKey + "\": [");

            if (beginIndex != -1)
            {
                int endIndex = data.toString().indexOf("\n  ],", beginIndex);

                if (endIndex == -1)
                {
                    endIndex = data.toString().indexOf("\n  ]", beginIndex);
                }

                if (endIndex != -1)
                {
                    outData = new StringBuilder(data.toString().substring(beginIndex, endIndex));
                }
            }
        }

        return outData;
    }
}
