/**************************************************************************************************
 * /** \file CcddCSVHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling import and export of data tables in CSV format. This class implements
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
import static CCDD.CcddConstants.EXPORT_SINGLE_FILE;
import static CCDD.CcddConstants.EXPORT_MULTIPLE_FILES;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.FileNames;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
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
    private final CcddApplicationParameterHandler appHandler;
    private final CcddRateParameterHandler rateHandler;

    // GUI component over which to center any error dialog
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // List of original and new script associations
    private List<String[]> associations;

    /**********************************************************************************************
     * CSV data type tags
     *********************************************************************************************/
    public enum CSVTags
    {
        COLUMN_DATA("_column_data_", null),
        CELL_DATA("_table_cell_data_", null),
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
        DBU_INFO("_dbu_info_", null),
        GROUP_DATA_FIELD("_group_data_field_", "_group_data_fields_"),
        SCRIPT_ASSOCIATION("_script_association_", null),
        APP_SCHEDULER_OLD("_app_sched_", null),
        APP_SCHEDULER_DATA("_app_sched_data_", null),
        APP_SCHEDULER_COMMENTS("_app_sched_comments_", null),
        TELEM_SCHEDULER_OLD("_telem_sched_", null),
        TELEM_SCHEDULER_COMMENTS("_telem_sched_comments_", null),
        TELEM_SCHEDULER_DATA("_telem_sched_data_", null),
        RATE_INFO("_rate_info_", null),
        FILE_DESCRIPTION("_file_dDescription", null);

        private final String tag;
        private final String alternateTag;

        /******************************************************************************************
         * CSV data type tags constructor
         *
         * @param tag          Text describing the data
         *
         * @param alternateTag Alternate text describing the data; null if there is no alternate
         *                     name. This allows the same tag data type to have two names, which is
         *                     used for backwards compatibility, due to a previous mixture of
         *                     singular and plural tag names
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
         * Get the alternate data type tag
         *
         * @return Text describing the data
         *****************************************************************************************/
        protected String getAlternateTag()
        {
            return alternateTag;
        }

        /******************************************************************************************
         * Check if the supplied text matches the data type tag name or alternate name
         *
         * @param text Text describing the data
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
     * Useful character sequences
     *********************************************************************************************/
    private enum Chars
    {
        NEW_LINE("\n"),
        DOUBLE_QUOTE("\""),
        EMPTY_STRING(""),
        COMMA(","),
        PERIOD("."),
        COMMA_BETWEEN_QUOTES("\",\""),
        DOUBLE_QUOTE_AND_COMMA("\","),
        DOUBLE_NEW_LINE("\n\n"),
        QUOTE_NEW_LINE("\"\n"),
        COLON(":");

        private final String value;

        /******************************************************************************************
         * Character sequence constructor
         *
         * @param value Text describing the character sequence
         *
         *****************************************************************************************/
        Chars(String value)
        {
            this.value = value;
        }

        /******************************************************************************************
         * Get the character sequence value
         *
         * @return Text representing the character sequence
         *****************************************************************************************/
        protected String getValue()
        {
            return value;
        }
    }

    /**********************************************************************************************
     * CSV handler class constructor
     *
     * @param ccddMain     Main class reference
     *
     * @param groupHandler Group handler reference
     *
     * @param parent       GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddCSVHandler(CcddMain ccddMain, CcddGroupHandler groupHandler, Component parent)
    {
        // Create references to shorten subsequent calls
        this.parent = parent;
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        scriptHandler = ccddMain.getScriptHandler();
        this.groupHandler = groupHandler;
        rateHandler = ccddMain.getRateParameterHandler();
        appHandler = ccddMain.getApplicationParameterHandler();
        dbControl = ccddMain.getDbControlHandler();
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
     * Get the list of telemetry scheduler messages
     *
     * @return List of original and new telemetry scheduler messages; null if no new messages have
     *         been added
     *********************************************************************************************/
    @Override
    public List<String[]> getTlmSchedulerData()
    {
        return null;
    }

    /**********************************************************************************************
     * Get the list of original and new application scheduler data
     *
     * @return List of original and new application scheduler data; null if no new associations
     *         have been added
     *********************************************************************************************/
    @Override
    public List<String[]> getAppSchedulerData()
    {
        return null;
    }

    /**********************************************************************************************
     * Build the information from the internal table in the current file
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
        try
        {
            // Initialize local variables
            StringBuilder content = new StringBuilder(new String(Files.readAllBytes(Paths.get(importFile.getPath()))));

            /********************* APPLICATION SCHEDULER *********************/
            StringBuilder incomingData = retrieveCSVData(CSVTags.APP_SCHEDULER_COMMENTS.getTag(),
                                                         content);

            if (incomingData.length() != 0)
            {
                String data = incomingData.toString();
                if (data.contains(CSVTags.APP_SCHEDULER_COMMENTS.getTag()))
                {
                    data = data.replace(CSVTags.APP_SCHEDULER_COMMENTS.getTag()
                                        + Chars.NEW_LINE.getValue(),
                                        Chars.EMPTY_STRING.getValue());
                }
                else
                {
                    data = data.replace(CSVTags.APP_SCHEDULER_OLD.getTag()
                                        + Chars.NEW_LINE.getValue(),
                                        Chars.EMPTY_STRING.getValue());
                }

                // Remove all double quotes
                data = data.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());

                // Strip any remaining new line characters
                data = data.replace(Chars.NEW_LINE.getValue(), Chars.EMPTY_STRING.getValue());

                // Split the data into the individual columns
                String[] Columns = data.split(Chars.COMMA.getValue());

                // Extract the information from each column
                int maxMsgsPerTimeSlot = Integer.parseInt(Columns[0]);
                int maxMsgsPerSec = Integer.parseInt(Columns[1]);
                int maxMsgsPerCycle = Integer.parseInt(Columns[2]);
                int numberOfTimeSlots = Integer.parseInt(Columns[3]);

                // Set and store the parameter values obtained from the CSV file
                appHandler.setApplicationParameters(maxMsgsPerSec,
                                                    maxMsgsPerCycle,
                                                    maxMsgsPerTimeSlot,
                                                    numberOfTimeSlots, parent);
            }

            /********************* TELEMETRY SCHEDULER *********************/
            incomingData = retrieveCSVData(CSVTags.TELEM_SCHEDULER_COMMENTS.getTag(), content);

            if (incomingData.length() != 0)
            {
                String data = incomingData.toString();

                // Remove the telem sched tag
                if (data.contains(CSVTags.TELEM_SCHEDULER_COMMENTS.getTag()))
                {
                    data = data.replace(CSVTags.TELEM_SCHEDULER_COMMENTS.getTag(),
                                        Chars.EMPTY_STRING.getValue());
                }
                else
                {
                    data = data.replace(CSVTags.TELEM_SCHEDULER_OLD.getTag(),
                                        Chars.EMPTY_STRING.getValue());
                }

                // Remove the rate info tag
                data = data.replace(CSVTags.RATE_INFO.getTag(), Chars.COMMA.getValue());

                // Remove any new line characters
                data = data.replace(Chars.NEW_LINE.getValue(), Chars.EMPTY_STRING.getValue());

                // Remove all double quotes
                data = data.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());

                // Split the data into the individual columns
                String[] Columns = data.split(Chars.COMMA.getValue());

                // Extract the information from each column
                int maxSecPerMsg = Integer.parseInt(Columns[0]);
                int maxMsgsPerSec = Integer.parseInt(Columns[1]);
                boolean includeUneven = Boolean.valueOf(Columns[2]);

                // Check if the rate information is present
                if (Columns.length > 3)
                {
                    String[] rateDataStreamNames = new String[1];
                    rateDataStreamNames[0] = Columns[4];

                    int[] maximumMessagesPerCycle = new int[1];
                    maximumMessagesPerCycle[0] = Integer.parseInt(Columns[5]);

                    int[] maximumBytesPerSecond = new int[1];
                    maximumBytesPerSecond[0] = Integer.parseInt(Columns[6]);

                    // Set and store the rate parameters obtained from the file
                    rateHandler.setRateParameters(maxSecPerMsg,
                                                  maxMsgsPerSec,
                                                  rateDataStreamNames,
                                                  maximumMessagesPerCycle,
                                                  maximumBytesPerSecond,
                                                  includeUneven,
                                                  parent);
                }
            }

            /********************* SCRIPT ASSOCIATIONS *********************/
            incomingData = retrieveCSVData(CSVTags.SCRIPT_ASSOCIATION.getTag(), content);

            if (incomingData.length() != 0)
            {
                String data = incomingData.toString();

                // Check if the associations haven't been loaded
                if (associations == null)
                {
                    // Get the script associations from the database
                    associations = scriptHandler.getScriptAssociations(parent);
                }

                // Extract the script association data
                data = data.replace(CSVTags.SCRIPT_ASSOCIATION.getTag()
                                    + Chars.NEW_LINE.getValue(),
                                    Chars.EMPTY_STRING.getValue());

                // Remove all double quotes
                data = data.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());

                // Split the data into the individual definitions
                String[] scriptAssocDefns = data.split(Chars.NEW_LINE.getValue());

                // Step through each definition
                for (int i = 0; i < scriptAssocDefns.length; i++)
                {
                    // Split the data into the individual columns
                    String[] Columns = scriptAssocDefns[i].split(Chars.COMMA.getValue());

                    // The code appears to expect 4 columns from the above operation. If there are
                    // blank entries, there may be only 3 columns Handle this by creating the
                    // fourth column
                    String fourthCol = "";
                    if (Columns.length == 4)
                    {
                        fourthCol = Columns[3];
                    }

                    // Add the script association, checking for errors
                    ignoreErrors = addImportedScriptAssociation(ignoreErrors,
                                                                replaceExistingAssociations,
                                                                associations,
                                                                new String[] {Columns[0],
                                                                              Columns[1],
                                                                              Columns[2],
                                                                              CcddScriptHandler.convertAssociationMembersFormat(fourthCol,
                                                                                                                                true)},
                                                                importFile.getAbsolutePath(),
                                                                scriptHandler,
                                                                parent);
                }
            }
        }
        catch (Exception pe)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }

        return;
    }

    /**********************************************************************************************
     * Import the table types (and table type data fields) and macros from the given file
     *
     * @param importFile              Import file reference
     *
     * @param ignoreErrors            True to ignore all errors in the import file
     *
     * @param replaceExistingMacros   True to replace existing macros
     *
     * @param replaceExistingTables   True to replace existing tables or table fields
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
        try
        {
            // Initialize local variables
            List<TableTypeDefinition> NewTableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            List<String[]> NewMacroDefns = new ArrayList<String[]>();
            StringBuilder content = new StringBuilder(new String(Files.readAllBytes(Paths.get(importFile.getPath()))));
            TableTypeDefinition TableTypeDefn = null;
            int ColumnNumber = 0;
            String DataFields = "";

            /********************* TABLE TYPES *********************/
            StringBuilder incomingData = retrieveCSVData(CSVTags.TABLE_TYPE.getTag(), content);

            if (incomingData.length() != 0)
            {
                // Divide the data into each individual table type definition, which may or may not
                // have table type data fields included
                String[] TableTypeDefns = incomingData.toString().split(CSVTags.TABLE_TYPE.getTag()
                                                                        + Chars.NEW_LINE.getValue());

                // Step through each definition
                for (int i = 1; i < TableTypeDefns.length; i++)
                {
                    DataFields = "";

                    // Check if any Table Type Data Fields exist for this entry
                    if (TableTypeDefns[i].contains(CSVTags.TABLE_TYPE_DATA_FIELD.getTag()))
                    {
                        // Divide the data into the table type definition and the table type data
                        // fields definition
                        DataFields = TableTypeDefns[i].split(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())[1];
                        TableTypeDefns[i] = TableTypeDefns[i].split(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())[0];
                    }

                    // Replace any double end line characters
                    TableTypeDefns[i] = TableTypeDefns[i].replace(Chars.DOUBLE_NEW_LINE.getValue(), Chars.NEW_LINE.getValue());

                    // There can be new line characters within the table type description. In order
                    // to guard against this we split only on new line characters that are directly
                    // after a double quote
                    String[] Rows = TableTypeDefns[i].split(Chars.QUOTE_NEW_LINE.getValue());

                    // Separate the first row into the name, description, and command argument
                    // flags
                    String[] defnRow = Rows[0].split(Chars.COMMA_BETWEEN_QUOTES.getValue());

                    // Check that the name, description, and whether or not the table
                    // represents a command are all defined
                    if (defnRow.length == 3)
                    {
                        // Extract the table name, description, and command argument flag
                        String TableTypeName = defnRow[0].replace(Chars.DOUBLE_QUOTE.getValue(),
                                                                  Chars.EMPTY_STRING.getValue());
                        String TableTypeDescription = Chars.EMPTY_STRING.getValue()
                                                      + defnRow[1].replace(Chars.DOUBLE_QUOTE.getValue(),
                                                                           Chars.EMPTY_STRING.getValue());
                        boolean representsCmdArgument = Boolean.parseBoolean(defnRow[2].replace(Chars.DOUBLE_QUOTE.getValue(),
                                                                                                Chars.EMPTY_STRING.getValue()));

                        // If this table type represents a command argument then append a 1 to the
                        // description. If not append a 0
                        if (representsCmdArgument)
                        {
                            TableTypeDescription = "1" + TableTypeDescription;
                        }
                        else
                        {
                            TableTypeDescription = "0" + TableTypeDescription;
                        }

                        // Create a new table type definition
                        TableTypeDefn = new TableTypeDefinition(TableTypeName, TableTypeDescription);

                        // Reset the column counter for each entry
                        ColumnNumber = 0;

                        // Add the table type column definition, checking for (and if possible,
                        // correcting) errors
                        for (int y = 1; y < Rows.length; y++)
                        {
                            // Split on each comma, but make sure it is one that has a double quote
                            // before and after it as a comma can be in a text string as well
                            String[] Columns = Rows[y].split(Chars.COMMA_BETWEEN_QUOTES.getValue());

                            // Strip all double quotes
                            for (int x = 0; x < Columns.length; x++)
                            {
                                Columns[x] = Columns[x].replace(Chars.DOUBLE_QUOTE.getValue(),
                                                                Chars.EMPTY_STRING.getValue());
                            }

                            // Check if the expected number of inputs is present
                            if (Columns.length == (TableTypeEditorColumnInfo.values().length - 1))
                            {
                                // Add/Update the table type definition columns
                                ignoreErrors = addImportedTableTypeColumnDefinition(ignoreErrors,
                                                                                    TableTypeDefn,
                                                                                    new String[] {Integer.toString(ColumnNumber),
                                                                                                  Columns[TableTypeEditorColumnInfo.NAME.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.DESCRIPTION.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.UNIQUE.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.REQUIRED.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() - 1],
                                                                                                  Columns[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() - 1]},
                                                                                    importFile.getAbsolutePath(),
                                                                                    inputTypeHandler,
                                                                                    parent);

                                // Increment the column number counter as each column is added
                                ColumnNumber++;
                            }
                            else
                            {
                                // The number of inputs is incorrect Check if the error should be
                                // ignored or the import canceled
                                ignoreErrors = getErrorResponse(ignoreErrors,
                                                                "<html><b>Table type '</b>"
                                                                + TableTypeDefn.getTypeName()
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
                    // Incorrect number of items in the definition
                    else
                    {
                        // The number of inputs is incorrect. Check if the error should be ignored
                        // or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
                                                        "<html><b>Missing table type name in import file '</b>"
                                                        + importFile.getAbsolutePath()
                                                        + "<b>'; continue?",
                                                        "Table Type Error",
                                                        "Ignore this table type",
                                                        "Ignore this and any remaining invalid table types",
                                                        "Stop importing",
                                                        parent);
                    }

                    /********************* TABLE TYPE DATA FIELDS *********************/
                    if (DataFields != Chars.EMPTY_STRING.getValue())
                    {
                        // Split the data into each individual data field definition
                        String[] Fields = DataFields.split(Chars.NEW_LINE.getValue());

                        for (int x = 1; x < Fields.length; x++)
                        {
                            // Split on each comma, but make sure it is one that has a double quote
                            // before and after it as a comma can be in a text string as well
                            String[] Columns = Fields[x].split(Chars.COMMA_BETWEEN_QUOTES.getValue());

                            // Strip all double quotes
                            for (int y = 0; y < Columns.length; y++)
                            {
                                Columns[y] = Columns[y].replace(Chars.DOUBLE_QUOTE.getValue(),
                                                                Chars.EMPTY_STRING.getValue());
                            }

                            // Add the data field definition, checking for (and if possible,
                            // correcting) errors
                            ignoreErrors = addImportedDataFieldDefinition(ignoreErrors,
                                                                          replaceExistingTables,
                                                                          TableTypeDefn,
                                                                          new String[] {CcddFieldHandler.getFieldTypeName(TableTypeDefn.getTypeName()),
                                                                                        Columns[FieldEditorColumnInfo.NAME.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.DESCRIPTION.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.CHAR_SIZE.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.INPUT_TYPE.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.REQUIRED.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.APPLICABILITY.ordinal()],
                                                                                        Columns[FieldEditorColumnInfo.VALUE.ordinal()],
                                                                                        ""},
                                                                          importFile.getAbsolutePath(),
                                                                          inputTypeHandler,
                                                                          fieldHandler,
                                                                          parent);
                        }
                    }

                    // Add the table type definition
                    NewTableTypeDefinitions.add(TableTypeDefn);
                }

                // Add the table type if it's new or update an existing one
                tableTypeHandler.updateTableTypes(NewTableTypeDefinitions);
            }

            /********************* MACROS *********************/
            incomingData = retrieveCSVData(CSVTags.MACRO.getTag(), content);

            if (incomingData.length() != 0)
            {
                String[] MacroDefns = incomingData.toString().split(Chars.NEW_LINE.getValue());

                for (int i = 1; i < MacroDefns.length; i++)
                {
                    // Split on commas between quotes. It is done this way because it is valid to
                    // have commas inside of the macro definitions and this prevents the code from
                    // splitting a definition up if this is the case
                    String[] Columns = MacroDefns[i].split(Chars.COMMA_BETWEEN_QUOTES.getValue());

                    // Strip all double quotes
                    for (int y = 0; y < Columns.length; y++)
                    {
                        Columns[y] = Columns[y].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                    }

                    // Check if the expected number of inputs is present
                    if (Columns.length == 2 || Columns.length == 1)
                    {
                        // Build the macro definition
                        String[] macroDefn = new String[] {Columns[0],
                                                           (Columns.length == 2 ? Columns[1]
                                                                                : Chars.EMPTY_STRING.getValue()),
                                                           Chars.EMPTY_STRING.getValue()};

                        // Check if the macro definition is valid
                        boolean importMacro = checkMacroDefinition(macroDefn);

                        if (importMacro)
                        {
                            // Add the macro definition (add a blank to represent the row number)
                            NewMacroDefns.add(macroDefn);
                        }
                    }
                    // The number of inputs is incorrect
                    else
                    {
                        // Check if the error should be ignored or the import canceled
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

                if (NewMacroDefns.size() > 0)
                {
                    // Convert all of the new macro definitions to a set of unique entries based on
                    // the macro name (the first array element) A pair with a boolean indicating if
                    // the input set was unique and the list of unique entries that were extracted
                    Pair<Boolean, List<String[]>> uniqueResults = convertToUniqueList(NewMacroDefns);
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
                }
            }
        }
        catch (Exception pe)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }

        return;
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
        StringBuilder content = new StringBuilder(new String(Files.readAllBytes(Paths.get(importFile.getPath()))));

        try
        {
            /********************* INPUT TYPES *********************/
            StringBuilder incomingData = retrieveCSVData(CSVTags.INPUT_TYPE.getTag(), content);

            if (incomingData.length() != 0)
            {
                List<String[]> newInputTypeDefns = new ArrayList<String[]>();
                String data = incomingData.toString();
                data = data.replace(CSVTags.INPUT_TYPE.getTag() + Chars.NEW_LINE.getValue(),
                                    Chars.EMPTY_STRING.getValue());

                // Replace any double end line characters
                data = data.replace(Chars.DOUBLE_NEW_LINE.getValue(), Chars.NEW_LINE.getValue());

                // There are often new line characters within the text fields of the input types.
                // In order to guard against these we split only on new line characters that are
                // directly after a double quote
                String[] inputTypeDefns = data.split(Chars.QUOTE_NEW_LINE.getValue());

                // Step through each definition
                for (int i = 0; i < inputTypeDefns.length; i++)
                {
                    // Split on each comma, but make sure it is one that has a double quote before
                    // and after it as a comma can be in a text string as well
                    String[] Columns = inputTypeDefns[i].split(Chars.COMMA_BETWEEN_QUOTES.getValue());

                    // Strip all double quotes
                    for (int x = 0; x < Columns.length; x++)
                    {
                        Columns[x] = Columns[x].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                    }

                    // Check that the number of inputs is correct
                    if (Columns.length == InputTypesColumn.values().length - 1)
                    {
                        // Check if the input type definition is valid
                        String[] inputTypeDefn = checkInputTypeDefinition(new String[] {Columns[InputTypesColumn.NAME.ordinal()],
                                                                                        Columns[InputTypesColumn.DESCRIPTION.ordinal()],
                                                                                        Columns[InputTypesColumn.MATCH.ordinal()],
                                                                                        Columns[InputTypesColumn.ITEMS.ordinal()],
                                                                                        Columns[InputTypesColumn.FORMAT.ordinal()],
                                                                                        ""});

                        // Add the input type definition (add a blank to represent the row number)
                        newInputTypeDefns.add(inputTypeDefn);
                    }
                    else
                    {
                        // The number of inputs is incorrect. Check if the error should be ignored
                        // or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
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

                // Add the input type if it's new or match it to an existing one with the same name
                // if the type definitions are the same
                inputTypeHandler.updateInputTypes(newInputTypeDefns);
            }

            /********************* DATA TYPES *********************/
            incomingData = retrieveCSVData(CSVTags.DATA_TYPE.getTag(), content);

            if (incomingData.length() != 0)
            {
                List<String[]> newDataTypeDefns = new ArrayList<String[]>();

                String data = incomingData.toString();

                data = data.replace(CSVTags.DATA_TYPE.getTag() + Chars.NEW_LINE.getValue(),
                                    Chars.EMPTY_STRING.getValue());

                // Split the data into the individual data type definitions
                String[] dataTypeDefns = data.split(Chars.NEW_LINE.getValue());

                // Step through each definition
                for (int defnIndex = 0; defnIndex < dataTypeDefns.length; defnIndex++)
                {
                    String[] Columns = dataTypeDefns[defnIndex].split(Chars.COMMA.getValue());

                    // Strip all double quotes
                    for (int col = 0; col < Columns.length; col++)
                    {
                        Columns[col] = Columns[col].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                    }

                    // Check if the expected number of inputs is present
                    if (Columns.length == DataTypesColumn.values().length - 1)
                    {
                        // Build the data type definition
                        String[] dataTypeDefn = new String[] {Columns[DataTypesColumn.USER_NAME.ordinal()],
                                                              Columns[DataTypesColumn.C_NAME.ordinal()],
                                                              Columns[DataTypesColumn.SIZE.ordinal()],
                                                              Columns[DataTypesColumn.BASE_TYPE.ordinal()],
                                                              Chars.EMPTY_STRING.getValue()};

                        // Check if the data type definition is valid
                        checkDataTypeDefinition(dataTypeDefn);

                        // Add the data type definition (add a blank to represent the row number)
                        newDataTypeDefns.add(dataTypeDefn);
                    }
                    else
                    {
                        // The number of inputs is incorrect. Check if the error should be ignored
                        // or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
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

                if (importingEntireDatabase)
                {
                    // Replace any existing data types with the new ones
                    dataTypeHandler.setDataTypeData(newDataTypeDefns);
                }
                else
                {
                    // Add the data type if it's new or match it to an existing one with the same
                    // name if the type definitions are the same
                    dataTypeHandler.updateDataTypes(newDataTypeDefns, replaceExistingDataTypes);
                }
            }
        }
        catch (Exception pe)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }

        return;
    }

    /**********************************************************************************************
     * Import group information from a provided csv file
     *
     * @param br                    A buffered reader variable
     *
     * @param importFile            Import file reference
     *
     * @param ignoreErrors          Should errors be ignored during import
     *
     * @param projectDefn           The project definition
     *
     * @param replaceExistingGroups Should existing groups be replaced
     *********************************************************************************************/
    public void importGroupData(BufferedReader br,
                                FileEnvVar importFile,
                                boolean ignoreErrors,
                                ProjectDefinition projectDefn,
                                boolean replaceExistingGroups)
    {
        // Initialize the number of matching columns and the cell data storage
        Boolean readingGroupData = false;
        Boolean readingGroupDataField = false;
        String[] columnValues = null;
        String groupDefnName = null;

        try
        {
            // Get a list of names for all of the groups currently defined within the database
            String[] currentGroupNames = groupHandler.getGroupNames(false);
            List<String> newGroupNames = new ArrayList<String>();
            List<FieldInformation> newFieldInformation = new ArrayList<FieldInformation>();
            String currentGroupName = Chars.EMPTY_STRING.getValue();

            // Read first line in file
            String line = br.readLine();

            // Continue to read the file until EOF is reached or an error is detected
            while (line != null)
            {
                // Remove any leading/trailing white space characters from the row
                line = line.trim();

                // Check if we are about to read group data
                if (line.equals(CSVTags.GROUP.getTag()))
                {
                    line = br.readLine();

                    if (line != null)
                    {
                        readingGroupDataField = false;
                        readingGroupData = true;
                        line = line.trim();
                    }
                }
                // Check if we are about to read group data fields
                else if (line.equals(CSVTags.GROUP_DATA_FIELD.getTag()))
                {
                    line = br.readLine();

                    if (line != null)
                    {
                        readingGroupDataField = true;
                        readingGroupData = false;
                        line = line.trim();
                    }
                }

                // Check if all definitions are to be loaded
                if (readingGroupData == true && !line.isEmpty())
                {
                    // Divide the string into an array of strings
                    columnValues = trimLine(line, br);

                    // Reset newFieldInformation
                    if (newFieldInformation.size() > 0)
                    {
                        // Replace the old field information with the new field information
                        fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(currentGroupName),
                                                                    newFieldInformation);
                    }

                    newFieldInformation = new ArrayList<FieldInformation>();

                    // Check if the expected number of inputs is present
                    if ((columnValues.length == GroupDefinitionColumn.values().length)
                        || (columnValues.length == GroupDefinitionColumn.values().length - 1))
                    {
                        // Append empty columns as needed to fill out the expected number of inputs
                        columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                        GroupDefinitionColumn.values().length
                                                                        - columnValues.length);

                        // Store the group name
                        groupDefnName = columnValues[GroupDefinitionColumn.NAME.ordinal()];
                        currentGroupName = groupDefnName;

                        // Add the group definition, checking for (and if possible, correcting)
                        // errors
                        addImportedGroupDefinition(new String[] {groupDefnName,
                                                                 columnValues[GroupDefinitionColumn.DESCRIPTION.ordinal()],
                                                                 columnValues[GroupDefinitionColumn.IS_APPLICATION.ordinal()],
                                                                 columnValues[GroupDefinitionColumn.MEMBERS.ordinal()]},
                                                   importFile.getAbsolutePath(),
                                                   replaceExistingGroups,
                                                   groupHandler);

                        // Add the group name to the list
                        newGroupNames.add(groupDefnName);
                    }
                    // The number of inputs is incorrect
                    else
                    {
                        // Check if the error should be ignored or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
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
                else if (readingGroupDataField == true && !line.isEmpty())
                {
                    // Continue to read the file until EOF is reached or an error is detected
                    while (line != null && !line.isEmpty())
                    {
                        // Divide the string into an array of strings
                        columnValues = trimLine(line, br);

                        // Append empty columns as needed to fill out the expected number of inputs
                        columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                        FieldsColumn.values().length
                                                                        - 1
                                                                        - columnValues.length);

                        InputType inputType = inputTypeHandler.getInputTypeByName(columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1]);
                        FieldInformation newField = new FieldInformation(CcddFieldHandler.getFieldGroupName(currentGroupName),
                                                                         columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                                         columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                                         inputType,
                                                                         Integer.parseInt(columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1]),
                                                                         Boolean.parseBoolean(columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1]),
                                                                         ApplicabilityType.getApplicabilityByName(columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1]),
                                                                         columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                                         Boolean.parseBoolean(columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1]),
                                                                         null,
                                                                         0);

                        newFieldInformation.add(newField);
                        line = br.readLine();

                        if (line != null)
                        {
                            line = line.trim();
                        }
                    }
                }

                readingGroupDataField = false;
                readingGroupData = false;
                line = br.readLine();
            }

            // Determine which groups have been deleted
            List<String> deletedGroups = new ArrayList<String>();

            // Search the list of new group names for every name in the current group names list.
            // Each name that is not found in the new list will be added to the list of deleted
            // groups
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
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Import reserved message id information from a provided csv file
     *
     * @param br           A buffered reader variable
     *
     * @param importFile   Import file reference
     *
     * @param ignoreErrors Should errors be ignored during import
     *********************************************************************************************/
    public void importReservedMsgIDData(BufferedReader br,
                                        FileEnvVar importFile,
                                        boolean ignoreErrors)
    {
        String[] columnValues = null;
        List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

        try
        {
            // Read first line in file
            String line = br.readLine();

            while (line != null)
            {
                // Check that this is not a blank line
                if (!line.isEmpty() && !line.equals(CSVTags.RESERVED_MSG_IDS.getTag()))
                {
                    columnValues = trimLine(line, br);

                    // Check if the expected number of inputs is present
                    if (columnValues.length == 2 || columnValues.length == 1)
                    {
                        // Append empty columns as needed to fill out the expected number of inputs
                        columnValues = CcddUtilities.appendArrayColumns(columnValues, 2 - columnValues.length);

                        // Add the reserved message ID definition (add a blank to represent the ID)
                        reservedMsgIDDefns.add(new String[] {columnValues[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                             columnValues[ReservedMsgIDsColumn.DESCRIPTION.ordinal()],
                                                             Chars.EMPTY_STRING.getValue()});
                    }
                    else
                    {
                        // The number of inputs is incorrect Check if the error should be ignored
                        // or the import canceled
                        ignoreErrors = getErrorResponse(ignoreErrors,
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

                line = br.readLine();
                if (line != null)
                {
                    // Remove any leading/trailing white space characters from the row
                    line = line.trim();
                }
            }

            if (!reservedMsgIDDefns.isEmpty())
            {
                // Overwrite the reserved message id data with the imported data
                rsvMsgIDHandler.setReservedMsgIDData(reservedMsgIDDefns);
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Import project data field information from a provided csv file
     *
     * @param br                    A buffered reader variable
     *
     * @param importFile            Import file reference
     *
     * @param ignoreErrors          Should errors be ignored during import
     *
     * @param projectDefn           The project definition
     *
     * @param replaceExistingTables Should existing tables be replaced
     *********************************************************************************************/
    public void importProjectDataFields(BufferedReader br,
                                        FileEnvVar importFile,
                                        boolean ignoreErrors,
                                        boolean replaceExistingTables,
                                        ProjectDefinition projectDefn)
    {
        String[] columnValues = null;

        try
        {
            // Read first line in file
            String line = br.readLine();

            columnValues = trimLine(line, br);

            while (line != null)
            {
                if (!line.isEmpty() && !line.contentEquals(CSVTags.PROJECT_DATA_FIELD.getTag()))
                {
                    // Append empty columns as needed to fill out the expected number of inputs
                    columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                    FieldsColumn.values().length
                                                                    - 1
                                                                    - columnValues.length);

                    // Add the data field definition, checking for (and if possible, correcting)
                    // errors
                    ignoreErrors = addImportedDataFieldDefinition(ignoreErrors,
                                                                  replaceExistingTables,
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

                line = br.readLine();
                columnValues = trimLine(line, br);
            }

            // Replace the old project fields with the new ones
            fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldProjectName(),
                                                        fieldHandler.getFieldInformationFromDefinitions(projectDefn.getDataFields()));
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Import project database information from a provided csv file
     *
     * @param br A buffered reader variable
     *********************************************************************************************/
    public void importDatabaseInformation(BufferedReader br)
    {
        String[] columnValues = null;

        try
        {
            // Read first line in file
            String line = br.readLine();

            while (line != null)
            {
                if (!line.isEmpty() && !line.contentEquals(CSVTags.DBU_INFO.getTag()))
                {
                    columnValues = trimLine(line, br);

                    if (columnValues.length == 3)
                    {
                        dbControl.renameDatabase(dbControl.getDatabaseName(),
                                                 dbControl.getDatabaseName(),
                                                 columnValues[2]);
                    }
                }

                line = br.readLine();
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Grab the database name, description and users. Each piece of data will be separated by a
     * comma.
     *
     * @return The database information separated by a comma.
     *********************************************************************************************/
    protected String getDBUInfo()
    {
        String FinalOutput = Chars.EMPTY_STRING.getValue();

        try
        {
            // Grab the database comment
            String[] comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());

            if (comment.length > 0)
            {
                FinalOutput += Chars.NEW_LINE.getValue()
                               + CSVTags.DBU_INFO.getTag()
                               + Chars.NEW_LINE.getValue();

                // Add the database name
                FinalOutput += Chars.DOUBLE_QUOTE.getValue()
                               + comment[1]
                                       + Chars.DOUBLE_QUOTE_AND_COMMA.getValue();

                // Add the user access level information
                String[][] usersAndAcessLevel = dbTable.retrieveInformationTable(InternalTable.USERS,
                                                                                 true,
                                                                                 ccddMain.getMainFrame()).toArray(new String[0][0]);

                String usersAndAcessLevelDataToExport = Chars.EMPTY_STRING.getValue();

                // Each user will be added with their individual access level in the format of
                // "user1:accessLevel,user2:accessLevel"
                for (int index = 0; index < usersAndAcessLevel.length; index++)
                {
                    usersAndAcessLevelDataToExport += usersAndAcessLevel[index][0]
                                                      + Chars.COLON.getValue()
                                                      + usersAndAcessLevel[index][1];
                    if (index != usersAndAcessLevel.length - 1)
                    {
                        usersAndAcessLevelDataToExport += Chars.COMMA.getValue();
                    }
                }

                // Add the database users
                FinalOutput += Chars.DOUBLE_QUOTE.getValue()
                               + usersAndAcessLevelDataToExport
                               + Chars.DOUBLE_QUOTE_AND_COMMA.getValue();

                // Add the database description
                FinalOutput += Chars.DOUBLE_QUOTE.getValue()
                               + comment[3]
                               + Chars.DOUBLE_QUOTE.getValue();
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        return FinalOutput;
    }

    /**********************************************************************************************
     * Import table definitions from a provided csv file
     *
     * @param br                    A buffered reader variable
     *
     * @param importFile            Import file reference
     *
     * @param ignoreErrors          Should errors be ignored during import
     *
     * @param replaceExistingTables Should existing tables be replaced
     *
     * @param importType            ImportType.IMPORT_ALL to import the table type, data type, and
     *                              macro definitions, and the data from all the table definitions;
     *                              ImportType.FIRST_DATA_ONLY to load only the data for the first
     *                              table defined
     *
     * @param targetTypeDefn        Table type definition of the table in which to import the data;
     *                              ignored if importing all tables
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importTableDefinitions(BufferedReader br,
                                       FileEnvVar importFile,
                                       boolean ignoreErrors,
                                       boolean replaceExistingTables,
                                       ImportType importType,
                                       TypeDefinition targetTypeDefn) throws CCDDException,
                                                                             Exception
    {
        String[] columnValues = null;

        // Initialize the number of matching columns and the cell data storage
        Boolean readingNameType = false;
        Boolean readingColumnData = false;
        Boolean readingDataField = false;

        // Initialize the table information
        int numColumns = 0;

        // Create empty table information and table type definition references
        TypeDefinition typeDefn = null;

        // Initialize the table information
        String tablePath = Chars.EMPTY_STRING.getValue();

        boolean columnsCounted = false;

        // Storage for column indices
        int[] columnIndex = null;

        // Create a table definition to contain the table's information
        TableDefinition tableDefn = new TableDefinition();

        // Read first line in file
        String line = br.readLine();

        // Remove any leading/trailing white space characters from the row
        line = line.trim();

        // Continue to read the file until EOF is reached or an error is detected
        while (line != null)
        {
            if (!line.isEmpty())
            {
                if (line.replace(Chars.COMMA.getValue(), Chars.EMPTY_STRING.getValue())
                        .equals(CSVTags.NAME_TYPE.getTag()))
                {
                    readingNameType = true;
                    readingColumnData = false;
                    readingDataField = false;
                    line = br.readLine();
                }
                else if (line.replace(Chars.COMMA.getValue(), Chars.EMPTY_STRING.getValue())
                             .equals(CSVTags.COLUMN_DATA.getTag()))
                {
                    readingNameType = false;
                    readingColumnData = true;
                    readingDataField = false;
                    columnsCounted = false;
                    line = br.readLine();
                }
                else if (line.replace(Chars.COMMA.getValue(), Chars.EMPTY_STRING.getValue())
                             .equals(CSVTags.DATA_FIELD.getTag()))
                {
                    readingNameType = false;
                    readingColumnData = false;
                    readingDataField = true;
                    line = br.readLine();

                    // If this is the line that lists the name of the columns then skip it
                    if (line.contains(FieldsColumn.FIELD_NAME.getColumnName()))
                    {
                        line = br.readLine();
                    }
                }

                if (line.replace(Chars.COMMA.getValue(), Chars.EMPTY_STRING.getValue())
                        .equals(CSVTags.DESCRIPTION.getTag()))
                {
                    line = br.readLine();

                    if (line != null && !line.isEmpty())
                    {
                        columnValues = trimLine(line, br);

                        // Store the table description
                        tableDefn.setDescription(columnValues[0]);
                    }
                }
                else
                {
                    columnValues = trimLine(line, br);

                    if (readingNameType == true)
                    {
                        // Check if the expected number of inputs is present (the third value, the
                        // system name, is optional and not used)
                        if (columnValues.length == 2 || columnValues.length == 3)
                        {
                            // Get the table's type definition. If importing into an existing table
                            // then use its type definition
                            typeDefn = importType == ImportType.IMPORT_ALL ? tableTypeHandler.getTypeDefinition(columnValues[1])
                                                                           : targetTypeDefn;

                            // Check if the table type doesn't exist
                            if (typeDefn == null)
                            {
                                throw new CCDDException("Unknown table type '</b>" + columnValues[1] + "<b>'");
                            }

                            // Use the table name (with path, if applicable) and type to build the
                            // parent, path, and type for the table information class
                            tablePath = columnValues[0];
                            tableDefn.setName(tablePath);
                            tableDefn.setTypeName(columnValues[1]);

                            // Get the number of expected columns (the hidden columns, primary key
                            // and row index, should not be included in the CSV file)
                            numColumns = typeDefn.getColumnCountVisible();
                        }
                        // Incorrect number of inputs
                        else
                        {
                            throw new CCDDException("Too many/few table name and type inputs");
                        }
                    }
                    else if (readingColumnData == true)
                    {
                        // Check if any column names exist
                        if (columnValues.length != 0)
                        {
                            // Number of columns in an import file that match the target table
                            int numValidColumns = 0;

                            if (columnsCounted == false)
                            {
                                // Create storage for the column indices
                                columnIndex = new int[columnValues.length];

                                // Step through each column name
                                for (int index = 0; index < columnValues.length; index++)
                                {
                                    // Get the index for this column name
                                    columnIndex[index] = typeDefn.getVisibleColumnIndexByUserName(columnValues[index]);

                                    // Check if the column name in the file matches that of a
                                    // column in the table
                                    if (columnIndex[index] != -1)
                                    {
                                        // Increment the counter that tracks the number of matched
                                        // columns
                                        numValidColumns++;
                                    }
                                    // The number of inputs is incorrect
                                    else
                                    {
                                        // Check if the error should be ignored or the import
                                        // canceled
                                        ignoreErrors = getErrorResponse(ignoreErrors,
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
                                                                        "Stop importing", parent);
                                    }
                                }

                                // Check if no column names in the file match those in the table
                                if (numValidColumns == 0)
                                {
                                    throw new CCDDException("No columns match those in the target table",
                                                            JOptionPane.WARNING_MESSAGE);
                                }

                                columnsCounted = true;
                            }
                            else
                            {
                                // Create storage for the row of cell data and initialize the
                                // values to nulls (a null indicates that the pasted cell value
                                // won't overwrite the current table value if overwriting; if
                                // inserting the pasted value is changed to a space)
                                String[] rowData = new String[numColumns];
                                Arrays.fill(rowData, Chars.EMPTY_STRING.getValue());

                                // Step through each column in the row
                                for (int rowIndex = 0; rowIndex < columnValues.length; rowIndex++)
                                {
                                    // Check if the column exists
                                    if (rowIndex < columnIndex.length && columnIndex[rowIndex] != -1)
                                    {
                                        // Store the cell data in the column matching the one in
                                        // the target table
                                        rowData[columnIndex[rowIndex]] = columnValues[rowIndex];
                                    }
                                }

                                // Add the row of data read in from the file to the cell data
                                // list
                                tableDefn.addData(rowData);
                            }
                        }
                        // The file contains no column data
                        else
                        {
                            throw new CCDDException("File format invalid");
                        }
                    }
                    else if (readingDataField == true)
                    {
                        // Append empty columns as needed to fill out the expected number of inputs
                        columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                                        FieldsColumn.values().length - 1 - columnValues.length);

                        // Add the data field definition, checking for (and if possible,
                        // correcting) errors
                        ignoreErrors = addImportedDataFieldDefinition(ignoreErrors,
                                                                      replaceExistingTables,
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
                    }
                }
            }

            line = br.readLine();
        }

        // Check if a table definition exists in the import file
        if (tableDefn.getName() != null)
        {
            // Add the table's definition to the list
            tableDefinitions.add(tableDefn);
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
        BufferedReader br = null;

        try
        {
            ProjectDefinition projectDefn = new ProjectDefinition();
            tableDefinitions = new ArrayList<TableDefinition>();

            // Create a buffered reader to read the file
            br = new BufferedReader(new FileReader(importFile));

            /*************** GROUPS ***************/
            if (importFile.getName().equals(FileNames.GROUPS.CSV()))
            {
                if (importType == ImportType.IMPORT_ALL)
                {
                    importGroupData(br,
                                    importFile,
                                    ignoreErrors,
                                    projectDefn,
                                    replaceExistingGroups);
                }
            }
            /*************** RESERVED MSG IDS ***************/
            else if (importFile.getName().equals(FileNames.RESERVED_MSG_ID.CSV()))
            {
                if (importType == ImportType.IMPORT_ALL)
                {
                    importReservedMsgIDData(br, importFile, ignoreErrors);
                }
            }
            /*************** PROJECT DATA FIELDS ***************/
            else if (importFile.getName().equals(FileNames.PROJECT_DATA_FIELD.CSV()))
            {
                if (importType == ImportType.IMPORT_ALL)
                {
                    importProjectDataFields(br,
                                            importFile,
                                            ignoreErrors,
                                            replaceExistingTables,
                                            projectDefn);
                }
            }
            /*************** DATABASE INFORMATION ***************/
            else if (importFile.getName().equals(FileNames.DBU_INFO.CSV()))
            {
                importDatabaseInformation(br);
            }
            /*************** TABLE DEFINITIONS ***************/
            else if (!importFile.getName().equals(FileNames.TABLE_INFO.CSV())
                     && !importFile.getName().equals(FileNames.MACROS.CSV())
                     && !importFile.getName().equals(FileNames.SCRIPT_ASSOCIATION.CSV())
                     && !importFile.getName().equals(FileNames.TELEM_SCHEDULER.CSV())
                     && !importFile.getName().equals(FileNames.APP_SCHEDULER.CSV())
                     && !importFile.getName().equals(FileNames.DBU_INFO.CSV()))
            {
                importTableDefinitions(br,
                                       importFile,
                                       ignoreErrors,
                                       replaceExistingTables,
                                       importType,
                                       targetTypeDefn);
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
     * Export the project tables
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
     * @throws CCDDException If a file I/O or parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
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

        // Step through each table
        for (int counter = 0; counter < tableDefs.size() && counter < tableDefs.size(); counter++)
        {
            try
            {
                // Output the table data to the selected file. Multiple writers are needed in case
                // tables are appended to an existing file
                fw = new FileWriter(exportFile, true);
                bw = new BufferedWriter(fw);
                pw = new PrintWriter(bw);

                // outputData represents the data that will be written to the file
                StringBuilder outputData = new StringBuilder();

                if (tableDefs.get(counter)
                             .getTablePath()
                             .compareToIgnoreCase(tableDefs.get(counter).getTablePath()) == 0)
                {
                    // Check if the build information is to be output
                    if (includeBuildInformation)
                    {
                        // Output the build information
                        outputData.append(CSVTags.FILE_DESCRIPTION.getTag())
                                  .append(Chars.NEW_LINE.getValue())
                                  .append("Created ")
                                  .append(new Date().toString())
                                  .append(" : CCDD Version = ")
                                  .append(ccddMain.getCCDDVersionInformation())
                                  .append(" : project = " + dbControl.getProjectName())
                                  .append(" : host = ")
                                  .append(dbControl.getServer())
                                  .append(" : user = ")
                                  .append(dbControl.getUser())
                                  .append(Chars.NEW_LINE.getValue());

                        // If outputType equals SINGLE_FILE than set includeBuildInformation to
                        // false so that it is not added multiple times
                        if (outputType.contentEquals(EXPORT_SINGLE_FILE))
                        {
                            includeBuildInformation = false;
                        }
                    }

                    // Store the table's name, type, description, data, and data fields
                    outputData.append(Chars.NEW_LINE.getValue())
                              .append(CSVTags.NAME_TYPE.getTag())
                              .append(Chars.NEW_LINE.getValue())
                              .append(CcddUtilities.addEmbeddedQuotesAndCommas(tableDefs.get(counter).getTablePath(),
                                                                               tableDefs.get(counter).getType(),
                                                                               fieldHandler.getFieldValue(tableDefs.get(counter).getTablePath(),
                                                                                                          DefaultInputType.SYSTEM_PATH)));

                    // Check if the table has a description
                    if (!tableDefs.get(counter).getDescription().isEmpty())
                    {
                        // Output the table description tag and description
                        outputData.append(Chars.NEW_LINE.getValue())
                                  .append(CSVTags.DESCRIPTION.getTag())
                                  .append(Chars.NEW_LINE.getValue())
                                  .append(CcddUtilities.addEmbeddedQuotes(tableDefs.get(counter).getDescription()));
                    }

                    // Get the column names for this table's type definition
                    TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableDefs.get(counter).getType());
                    String[] columnNames = Arrays.copyOfRange(typeDefn.getColumnNamesUser(), 2,
                                                              typeDefn.getColumnNamesUser().length);
                    List<Integer> booleanColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.BOOLEAN);

                    // Output the column data tag and column names
                    outputData.append(Chars.NEW_LINE.getValue())
                              .append(CSVTags.COLUMN_DATA.getTag())
                              .append(Chars.NEW_LINE.getValue())
                              .append(CcddUtilities.addEmbeddedQuotesAndCommas(columnNames));

                    // Step through each row in the table
                    for (int row = 0; row < tableDefs.get(counter).getData().size(); row++)
                    {
                        // If this column represents a boolean and it is blank a 'false' will need
                        // to be added
                        for (int index = 0; index < booleanColumns.size(); index++)
                        {
                            if (tableDefs.get(counter)
                                         .getData()
                                         .get(row)[booleanColumns.get(index)]
                                         .toString()
                                         .contentEquals(""))
                            {
                                tableDefs.get(counter).getData().get(row)[booleanColumns.get(index)] = "false";
                            }
                        }

                        // Output the table row data, skipping the hidden columns
                        outputData.append(Chars.NEW_LINE.getValue()).append(CcddUtilities.addEmbeddedQuotesAndCommas(Arrays.copyOfRange(CcddUtilities.convertObjectToString(tableDefs.get(counter).getData().get(row)),
                                                                                                                                        NUM_HIDDEN_COLUMNS,
                                                                                                                                        tableDefs.get(counter).getData().get(row).length)));

                        // Step through each column in the row
                        for (int column = 0; column < columnNames.length; column++)
                        {
                            List<Integer> dataTypeColumns = new ArrayList<Integer>();

                            // Get the column indices for all columns that can contain a primitive
                            // data type
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIM_AND_STRUCT));
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIMITIVE));

                            // Check if variable paths are to be output and if this table
                            // represents a structure
                            if (includeVariablePaths && typeDefn.isStructure())
                            {
                                // Get the variable path
                                String variablePath = tableDefs.get(counter).getTablePath()
                                                      + Chars.COMMA.getValue()
                                                      + tableDefs.get(counter).getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)]
                                                      + Chars.PERIOD.getValue()
                                                      + tableDefs.get(counter).getData().get(row)[typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

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

                    // Grab the data field info
                    List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(tableDefs.get(counter).getTablePath());

                    // Get the visible column names based on the table's type
                    String[] fieldColumnNames = new String[FieldsColumn.values().length - 2];
                    fieldColumnNames[0] = FieldsColumn.FIELD_NAME.getColumnName();
                    fieldColumnNames[1] = FieldsColumn.FIELD_DESC.getColumnName();
                    fieldColumnNames[2] = FieldsColumn.FIELD_SIZE.getColumnName();
                    fieldColumnNames[3] = FieldsColumn.FIELD_TYPE.getColumnName();
                    fieldColumnNames[4] = FieldsColumn.FIELD_REQUIRED.getColumnName();
                    fieldColumnNames[5] = FieldsColumn.FIELD_APPLICABILITY.getColumnName();
                    fieldColumnNames[6] = FieldsColumn.FIELD_VALUE.getColumnName();

                    // Check if the table contains any data fields
                    if (!fieldInformation.isEmpty())
                    {
                        // Output the data field marker
                        outputData.append(Chars.NEW_LINE.getValue())
                                  .append(CSVTags.DATA_FIELD.getTag())
                                  .append(Chars.NEW_LINE.getValue())
                                  .append(CcddUtilities.addEmbeddedQuotesAndCommas(fieldColumnNames));

                        // Step through each data field
                        for (FieldInformation fieldInfo : fieldInformation)
                        {
                            // Output the field information
                            outputData.append(Chars.NEW_LINE.getValue())
                                      .append(CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
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
                    outputData.append(Chars.NEW_LINE.getValue())
                              .append(CSVTags.VARIABLE_PATHS.getTag())
                              .append(Chars.NEW_LINE.getValue());

                    // Step through each variable path
                    for (String[] variablePath : variablePaths)
                    {
                        // Output the variable path in application and user-defined formats
                        outputData.append("%s")
                                  .append(Chars.NEW_LINE.getValue())
                                  .append(CcddUtilities.addEmbeddedQuotesAndCommas(variablePath[0],
                                                                                   variablePath[1]));
                    }
                }

                // Add one last new line character to mark the end of this section of export data
                // if exporting all data to a single file
                if ((outputType.contentEquals(EXPORT_SINGLE_FILE)) && (tableDefs.size() > 1))
                {
                    outputData.append(Chars.NEW_LINE.getValue());
                }

                // Output the data to the file
                if (!outputData.toString().isEmpty())
                {
                    pw.printf("%s", outputData.toString());
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
                    break;
                }
            }
            catch (Exception ioe)
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
                    break;
                }
            }
        }
    }

    /****************************************************************************************************
     * Retrieve the telemetry scheduler data, including the table comments. If no data is stored,
     * then nothing is returned
     *
     * @return String representing the telemetry scheduler data (if any)
     ***************************************************************************************************/
    protected String getTlmSchedulerDataAndComments()
    {
        StringBuilder dataToReturn = new StringBuilder();
        dataToReturn.append(Chars.NEW_LINE.getValue());

        // Retrieve the information stored in the Telemetry Scheduler table
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.TLM_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        // Check if there was any data stored in the telemetry scheduler
        if (!storedData.isEmpty())
        {
            dataToReturn.append(CSVTags.TELEM_SCHEDULER_DATA.getTag()).append(Chars.NEW_LINE.getValue());

            // Step through each column in the table
            for (String[] data : storedData)
            {
                // Add the table column values to the output
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[TlmSchedulerColumn.RATE_NAME.ordinal()])
                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[TlmSchedulerColumn.MESSAGE_NAME.ordinal()])
                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[TlmSchedulerColumn.MESSAGE_ID.ordinal()])
                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[TlmSchedulerColumn.MEMBER.ordinal()])
                            .append(Chars.DOUBLE_QUOTE.getValue())
                            .append(Chars.NEW_LINE.getValue());
            }
        }

        // Query the Telemetry Scheduler table to obtain the table comments
        String[] comments = dbTable.queryTableComment(InternalTable.TLM_SCHEDULER.getTableName(),
                                                      0,
                                                      parent);

        // Check if there were any comments stored for the telemetry scheduler table
        if (!(comments.length == 0))
        {
            // There are three parameters that will be required by each rate; add them to the
            // output
            dataToReturn.append(CSVTags.TELEM_SCHEDULER_COMMENTS.getTag())
                        .append(Chars.NEW_LINE.getValue());
            dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                        .append(comments[0])
                        .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
            dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                        .append(comments[1])
                        .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
            dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                        .append(comments[2])
                        .append(Chars.DOUBLE_QUOTE.getValue());

            // Check if there is any rate information stored in the table comment
            if (comments.length > 3)
            {
                // Every rate will have four parameters. Check how many different rates there are
                int groups = (comments.length - 3) / 4;

                // Start of iteration, since we already stored the first three values of the array
                int count = 3;

                dataToReturn.append(Chars.NEW_LINE.getValue())
                            .append(CSVTags.RATE_INFO.getTag())
                            .append(Chars.NEW_LINE.getValue());

                // Step through each group of rate information for each rate
                for (int index = 0; index < groups; index++)
                {

                    // Step through each parameter for the current rate
                    for (int rateInfo = 0; rateInfo < 4; rateInfo++)
                    {
                        // Store each parameter with their corresponding value
                        switch (rateInfo)
                        {
                            case 0:
                                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                            .append(comments[count].substring(1, comments[count].length() - 1))
                                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                                count++;
                                break;
                            case 1:
                                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                            .append(comments[count].substring(1, comments[count].length() - 1))
                                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                                count++;
                                break;
                            case 2:
                                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                            .append(comments[count])
                                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                                count++;
                                break;
                            case 3:
                                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                            .append(comments[count])
                                            .append(Chars.DOUBLE_QUOTE.getValue())
                                            .append(Chars.NEW_LINE.getValue());
                                count++;
                                break;
                        }
                    }
                }
            }
        }
        return dataToReturn.toString();
    }

    /****************************************************************************************************
     * Return the application scheduler data, including the table comments
     *
     * @return The application scheduler data, if any, as a string
     ***************************************************************************************************/
    protected String getAppSchedulerDataAndComments()
    {
        StringBuilder dataToReturn = new StringBuilder();
        dataToReturn.append(Chars.NEW_LINE.getValue());

        // Retrieve the information stored at the app Scheduler table
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.APP_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        if (!storedData.isEmpty())
        {
            dataToReturn.append(CSVTags.APP_SCHEDULER_DATA.getTag()).append(Chars.NEW_LINE.getValue());

            for (String[] data : storedData)
            {
                // Add the table column values to the output
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[AppSchedulerColumn.TIME_SLOT.ordinal()])
                            .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                            .append(data[AppSchedulerColumn.APP_INFO.ordinal()])
                            .append(Chars.DOUBLE_QUOTE.getValue())
                            .append(Chars.NEW_LINE.getValue());
            }
        }

        // Query the Application Scheduler table to obtain the table comments
        String[] comments = dbTable.queryTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                                      0,
                                                      parent);

        if (!(comments.length == 0))
        {
            dataToReturn.append(CSVTags.APP_SCHEDULER_COMMENTS.getTag()).append(Chars.NEW_LINE.getValue());

            for (int index = 0; index < comments.length; index++)
            {
                if (index != comments.length - 1)
                {
                    dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                .append(comments[index])
                                .append(Chars.DOUBLE_QUOTE_AND_COMMA.getValue());
                }
                else
                {
                    dataToReturn.append(Chars.DOUBLE_QUOTE.getValue())
                                .append(comments[index])
                                .append(Chars.DOUBLE_QUOTE.getValue())
                                .append(Chars.NEW_LINE.getValue());
                }
            }
        }

        return dataToReturn.toString();
    }

    /**********************************************************************************************
     * Export script association data, group data, macro data, telemetry scheduler data or
     * application scheduler data to the specified folder
     *
     * @param includes   Array indicating internal file types to include
     *
     * @param dataTypes  The data type that is about to be exported
     *
     * @param exportFile Reference to the user-specified output file
     *
     * @param outputType String representing rather the output is going to a single file or
     *                   multiple files. Should be EXPORT_SINGLE_FILE or EXPORT_MULTIPLE_FILES
     *
     * @throws CCDDException If a file I/O or parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void exportInternalCCDDData(boolean[] includes,
                                       CcddConstants.exportDataTypes[] dataTypes,
                                       FileEnvVar exportFile,
                                       String outputType) throws CCDDException, Exception
    {
        // Initialize local variables
        List<String> referencedMacros = new ArrayList<String>();
        int counter = 0;
        boolean fwCreated = false;

        FileEnvVar FinalExportFile = null;

        for (CcddConstants.exportDataTypes dataType : dataTypes)
        {
            if (includes[counter] == true)
            {
                StringBuilder finalOutput = new StringBuilder();

                try
                {
                    // Are we exporting this database to multiple files or a single file
                    if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                    {
                        // Multiple files
                        switch (dataType)
                        {
                            case GROUPS:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.GROUPS.CSV());
                                break;

                            case MACROS:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.MACROS.CSV());
                                break;

                            case ASSOCIATIONS:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.SCRIPT_ASSOCIATION.CSV());
                                break;

                            case TELEMSCHEDULER:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.TELEM_SCHEDULER.CSV());
                                break;

                            case APPSCHEDULER:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.APP_SCHEDULER.CSV());
                                break;

                            case RESERVED_MSG_ID:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.RESERVED_MSG_ID.CSV());
                                break;

                            case PROJECT_FIELDS:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.PROJECT_DATA_FIELD.CSV());
                                break;

                            case DBU_INFO:
                                FinalExportFile = new FileEnvVar(exportFile
                                                                 + File.separator
                                                                 + FileNames.DBU_INFO.CSV());
                                break;
                        }
                    }
                    else
                    {
                        if (!fwCreated)
                        {
                            // Single file
                            FinalExportFile = new FileEnvVar(exportFile.getPath());
                            fwCreated = true;
                        }
                    }

                    // Gather the required data
                    switch (dataType)
                    {
                        case GROUPS:
                            // Get the group information
                            List<GroupInformation> groupInfoList = groupHandler.getGroupInformation();

                            // Check if any groups exist
                            if (groupInfoList != null && groupInfoList.size() > 0)
                            {
                                StringBuilder groupData = new StringBuilder();

                                // Step though each entry in the list
                                for (GroupInformation groupInfo : groupInfoList)
                                {
                                    // Append the label for this group
                                    groupData.append("\n").append(CSVTags.GROUP.getTag()).append("\n");

                                    // Append the group name, description and rather or not it
                                    // represents an application
                                    groupData.append("\"")
                                             .append(groupInfo.getName())
                                             .append("\",\"")
                                             .append(groupInfo.getDescription())
                                             .append("\",\"")
                                             .append(groupInfo.isApplication())
                                             .append("\"");

                                    // Check rather or not this group contains any members
                                    List<String> tableMembers = groupInfo.getTablesAndAncestors();

                                    if (tableMembers != null && tableMembers.size() > 0)
                                    {
                                        boolean firstMember = true;

                                        for (String member : tableMembers)
                                        {
                                            // If this is the first member add a comma rather than
                                            // a semicolon
                                            if (firstMember)
                                            {
                                                groupData.append(",\"").append(member);
                                                firstMember = false;
                                            }
                                            else
                                            {
                                                groupData.append(";").append(member);
                                            }
                                        }

                                        groupData.append("\"");
                                    }

                                    // Retrieve this groups data fields
                                    List<FieldInformation> groupFieldInfo = fieldHandler.getFieldInformationByOwner("Group:"
                                                                                                                    + groupInfo.getName());

                                    // Does this group have any data fields?
                                    if (groupFieldInfo != null && groupFieldInfo.size() > 0)
                                    {
                                        // Append the label for this groups data fields
                                        groupData.append("\n").append(CSVTags.GROUP_DATA_FIELD.getTag()).append("\n");

                                        // Step through each data field
                                        for (FieldInformation fieldInfo : groupFieldInfo)
                                        {
                                            // Add all of the fields info
                                            groupData.append("\"")
                                                     .append(fieldInfo.getFieldName())
                                                     .append("\",\"")
                                                     .append(fieldInfo.getDescription())
                                                     .append("\",\"")
                                                     .append(fieldInfo.getSize())
                                                     .append("\",\"")
                                                     .append(fieldInfo.getInputType().getInputName())
                                                     .append("\",\"")
                                                     .append(fieldInfo.isRequired())
                                                     .append("\",\"")
                                                     .append(fieldInfo.getApplicabilityType().getApplicabilityName())
                                                     .append("\",\"")
                                                     .append(fieldInfo.getValue())
                                                     .append("\"\n");
                                        }
                                    }
                                    else
                                    {
                                        groupData.append("\n");
                                    }
                                }

                                finalOutput.append(groupData.toString());
                            }

                            break;

                        case MACROS:
                            // Get the macros information
                            referencedMacros.addAll(macroHandler.getMacroNames());

                            // Check if any macros are referenced (or all are included)
                            if (!referencedMacros.isEmpty())
                            {
                                // Output the macro marker
                                finalOutput.append("\n").append(CSVTags.MACRO.getTag()).append("\n");

                                // Step through each macro
                                for (String[] macro : macroHandler.getMacroData())
                                {
                                    // Check if all macros are to be included or if the macro is
                                    // referenced in the table
                                    if (referencedMacros.contains(macro[MacrosColumn.MACRO_NAME.ordinal()]))
                                    {
                                        // Output the macro definition
                                        finalOutput.append(CcddUtilities.addEmbeddedQuotesAndCommas(macro[MacrosColumn.MACRO_NAME.ordinal()],
                                                                                                    macro[MacrosColumn.VALUE.ordinal()]));
                                        finalOutput.append("\n");
                                    }
                                }
                            }

                            break;

                        case ASSOCIATIONS:
                            // Get the Script association data
                            List<String[]> scriptAssociations = scriptHandler.getScriptAssociations(parent);
                            StringBuilder associationData = new StringBuilder();

                            if (scriptAssociations != null && scriptAssociations.size() > 0)
                            {
                                associationData.append("\n").append(CSVTags.SCRIPT_ASSOCIATION.getTag()).append("\n");

                                for (String[] association : scriptAssociations)
                                {
                                    String member = association[AssociationsColumn.MEMBERS.ordinal()].replace(";\n", " + ");
                                    associationData.append("\"")
                                                   .append(association[AssociationsColumn.NAME.ordinal()])
                                                   .append("\",")
                                                   .append("\"")
                                                   .append(association[AssociationsColumn.DESCRIPTION.ordinal()])
                                                   .append("\",")
                                                   .append("\"")
                                                   .append(association[AssociationsColumn.SCRIPT_FILE.ordinal()])
                                                   .append("\",")
                                                   .append("\"")
                                                   .append(member)
                                                   .append("\"\n");
                                }
                            }

                            finalOutput.append(associationData.toString());

                            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                            {
                                finalOutput.append("\n\n");
                            }

                            break;

                        case TELEMSCHEDULER:
                            // Get the telemetry scheduler data
                            finalOutput.append(getTlmSchedulerDataAndComments());
                            break;

                        case APPSCHEDULER:
                            // Get the application scheduler data
                            finalOutput.append(getAppSchedulerDataAndComments());
                            break;

                        case RESERVED_MSG_ID:
                            // Check if the user elected to store the reserved message IDs and if
                            // there are any reserved message IDs defined
                            if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
                            {
                                // Output the reserved message ID marker
                                finalOutput.append("\n").append(CSVTags.RESERVED_MSG_IDS.getTag()).append("\n");

                                // Step through each reserved message ID
                                for (String[] reservedMsgID : rsvMsgIDHandler.getReservedMsgIDData())
                                {
                                    // Output the reserved message ID definition
                                    finalOutput.append(CcddUtilities.addEmbeddedQuotesAndCommas(reservedMsgID[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                                                                reservedMsgID[ReservedMsgIDsColumn.DESCRIPTION.ordinal()]))
                                               .append("\n");
                                }
                            }

                            break;

                        case PROJECT_FIELDS:
                            // Build the data field information for the project
                            List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldProjectName());

                            // Check if the project contains any data fields
                            if (!fieldInformation.isEmpty())
                            {
                                // Output the project data field marker
                                finalOutput.append("\n").append(CSVTags.PROJECT_DATA_FIELD.getTag()).append("\n");

                                // Step through each data field
                                for (FieldInformation fieldInfo : fieldInformation)
                                {
                                    // Output the field information
                                    finalOutput.append(CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                                                                                fieldInfo.getDescription(),
                                                                                                Integer.toString(fieldInfo.getSize()),
                                                                                                fieldInfo.getInputType().getInputName(),
                                                                                                Boolean.toString(fieldInfo.isRequired()),
                                                                                                fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                                                fieldInfo.getValue())).append("\n");
                                }
                            }

                            break;

                        case DBU_INFO:
                            // Output the DBU Info
                            finalOutput.append(getDBUInfo());
                            break;
                    }

                    // If there is any data then write it to the file
                    if (!finalOutput.toString().equals(""))
                    {
                        // Add one last new line character to mark the end of this section of
                        // export data if exporting all data to a single file
                        if (outputType.contentEquals(EXPORT_SINGLE_FILE))
                        {
                            finalOutput.append("\n");
                            FileUtils.writeStringToFile(FinalExportFile,
                                                        finalOutput.toString(),
                                                        (String) null,
                                                        true);
                        }
                        else
                        {
                            FileUtils.writeStringToFile(FinalExportFile,
                                                        finalOutput.toString(),
                                                        (String) null,
                                                        false);
                        }
                    }
                }
                catch (Exception e)
                {
                    // Inform the user that the export failed
                    throw new CCDDException("Export error; cause '</b>" + e.getMessage() + "<b>'");
                }
            }

            counter++;
        }
    }

    /**********************************************************************************************
     * Export table type definitions to the specified folder
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
     * @throws CCDDException If a file I/O or parsing error occurs
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
        // Init local variables
        List<String> referencedTableTypes = new ArrayList<String>();
        List<String> referencedInputTypes = new ArrayList<String>();
        List<String> referencedDataTypes = new ArrayList<String>();
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        FileEnvVar FinalExportFile = null;

        try
        {
            // Determine the output type so that the correct naming convention can be used
            if (outputType.contentEquals(EXPORT_MULTIPLE_FILES))
            {
                FinalExportFile = new FileEnvVar(exportFile
                                                 + File.separator
                                                 + FileNames.TABLE_INFO.CSV());
            }
            else
            {
                FinalExportFile = new FileEnvVar(exportFile.getPath());
            }

            // Output the table data to the selected file. Multiple writers are needed in case
            // tables are appended to an existing file
            fw = new FileWriter(FinalExportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            // Determine what data is to be included
            if (includeInputTypes == true)
            {
                referencedInputTypes.addAll(Arrays.asList(inputTypeHandler.getNames(true)));
            }

            if (includeTableTypes == true)
            {
                referencedTableTypes.addAll(Arrays.asList(tableTypeHandler.getTableTypeNames()));
            }

            if (includeDataTypes == true)
            {
                referencedDataTypes.addAll(dataTypeHandler.getDataTypeNames());
            }

            /****************** EXPORT THE INPUT TYPES ******************/
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

            /****************** EXPORT THE TABLE TYPES ******************/
            for (String tableType : referencedTableTypes)
            {
                // Get the table type definition based on the type name
                TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(tableType);

                // Output the table type tag, and the type name and description
                pw.printf("\n" + CSVTags.TABLE_TYPE.getTag() + "\n%s\n",
                          CcddUtilities.addEmbeddedQuotesAndCommas(tableTypeDefn.getName(),
                                                                   tableTypeDefn.getDescription(),
                                                                   tableTypeDefn.representsCommandArg()));

                // Step through each column defined for the table type, skipping the primary key
                // and row index columns
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

                    /****************** EXPORT THE TABLE TYPE DATA FIELD ******************/
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

            /****************** EXPORT THE DATA TYPES ******************/
            if (!referencedDataTypes.isEmpty())
            {
                // Output the data type marker
                pw.printf("\n" + CSVTags.DATA_TYPE.getTag() + "\n");

                // Step through each data type
                for (String dataTypeName : referencedDataTypes)
                {
                    String[] dataType = dataTypeHandler.getDataTypeByName(dataTypeName);

                    // Output the data type definition
                    pw.printf("%s\n",
                              CcddUtilities.addEmbeddedQuotesAndCommas(dataType[DataTypesColumn.USER_NAME.ordinal()],
                                                                       dataType[DataTypesColumn.C_NAME.ordinal()],
                                                                       dataType[DataTypesColumn.SIZE.ordinal()],
                                                                       dataType[DataTypesColumn.BASE_TYPE.ordinal()]));
                }
            }

            // Add one last new line character to mark the end of this section of export data if
            // exporting all data to a single file
            if (outputType.contentEquals(EXPORT_SINGLE_FILE))
            {
                pw.printf("\n");
            }
        }
        catch (Exception e)
        {
            // Inform the user that the export failed
            throw new CCDDException("Export error; cause '</b>" + e.getMessage() + "<b>'");
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
     * Retrieve various pieces of data from CSV files
     *
     * @param searchKey What to search for
     *
     * @param data      The string to be searched
     *
     * @return Portion of CSV data indicated by the key
     *********************************************************************************************/
    public StringBuilder retrieveCSVData(String searchKey, StringBuilder data)
    {
        StringBuilder outData = new StringBuilder();

        if (searchKey == CSVTags.INPUT_TYPE.getTag()
                || searchKey == CSVTags.TABLE_TYPE.getTag()
                || searchKey == CSVTags.DATA_TYPE.getTag()
                || searchKey == CSVTags.MACRO.getTag()
                || searchKey == CSVTags.GROUP.getTag()
                || searchKey == CSVTags.SCRIPT_ASSOCIATION.getTag()
                || searchKey == CSVTags.TELEM_SCHEDULER_OLD.getTag()
                || searchKey == CSVTags.TELEM_SCHEDULER_COMMENTS.getTag()
                || searchKey == CSVTags.APP_SCHEDULER_COMMENTS.getTag()
                || searchKey == CSVTags.RESERVED_MSG_IDS.getTag()
                || searchKey == CSVTags.PROJECT_DATA_FIELD.getTag()
                || searchKey == CSVTags.NAME_TYPE.getTag()
                || searchKey == CSVTags.DBU_INFO.getTag())
        {
            int beginIndex = data.toString().indexOf(searchKey + "\n");

            if (beginIndex != -1)
            {
                int endIndex = data.toString().indexOf("\n_", beginIndex);

                // Next tag found
                if (endIndex != -1)
                {
                    if (searchKey == CSVTags.NAME_TYPE.getTag())
                    {
                        int lastIndex = data.toString().lastIndexOf(CSVTags.NAME_TYPE.getTag()
                                                                    + "\n");

                        if (lastIndex != -1)
                        {
                            endIndex = data.toString().indexOf("\n_", lastIndex);
                        }

                        int nextIndex = data.toString().indexOf(CSVTags.DESCRIPTION.getTag() + "\n",
                                                                endIndex + 1);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }

                        nextIndex = data.toString().indexOf(CSVTags.COLUMN_DATA.getTag() + "\n",
                                                            endIndex);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }

                        nextIndex = data.toString().indexOf(CSVTags.DATA_FIELD.getTag() + "\n",
                                                            endIndex);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }
                    }
                    else if (searchKey == CSVTags.TABLE_TYPE.getTag())
                    {
                        int lastIndex = data.toString().lastIndexOf(CSVTags.TABLE_TYPE.getTag() + "\n");

                        if (lastIndex != -1)
                        {
                            endIndex = data.toString().indexOf("\n_", lastIndex);
                        }

                        int nextIndex = data.toString().indexOf(CSVTags.TABLE_TYPE_DATA_FIELD.getTag()
                                                                + "\n",
                                                                endIndex + 1);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }
                    }
                    else if (searchKey == CSVTags.GROUP.getTag())
                    {
                        int lastIndex = data.toString().lastIndexOf(CSVTags.GROUP.getTag() + "\n");

                        if (lastIndex != -1)
                        {
                            endIndex = data.toString().indexOf("\n_", lastIndex);
                        }

                        int nextIndex = data.toString().indexOf(CSVTags.GROUP_DATA_FIELD.getTag()
                                                                + "\n",
                                                                endIndex + 1);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }
                    }
                    else if (searchKey == CSVTags.TELEM_SCHEDULER_COMMENTS.getTag()
                             || searchKey == CSVTags.TELEM_SCHEDULER_OLD.getTag())
                    {
                        int nextIndex = data.toString().indexOf(CSVTags.RATE_INFO.getTag() + "\n",
                                                                endIndex + 1);

                        if (nextIndex != -1 && nextIndex == endIndex + 1)
                        {
                            endIndex = data.toString().indexOf("\n_", nextIndex);
                        }
                    }

                    if (endIndex != -1)
                    {
                        outData = new StringBuilder(data.toString().substring(beginIndex,
                                                                              endIndex));
                    }
                    // End of file reached
                    else
                    {
                        outData = new StringBuilder(data.toString().substring(beginIndex));
                    }
                }
                // End of file reached
                else
                {
                    outData = new StringBuilder(data.toString().substring(beginIndex));
                }
            }
        }

        return outData;
    }

    /**********************************************************************************************
     * Trim the provided line, remove special characters and divide it into an array
     *
     * @param line The line of text to work with
     *
     * @param br   Buffered reader variable
     *
     * @return An array of strings representing the data provided in the line of text
     *********************************************************************************************/
    public String[] trimLine(String line, BufferedReader br)
    {
        // Initialize the number of matching columns and the cell data storage
        String[] columnValues = null;

        try
        {
            // Check that the line is not null
            if (line != null)
            {
                while (line.startsWith("#"))
                {
                    line = br.readLine();

                    if (line == null)
                    {
                        line = "";
                    }
                }

                // Remove any leading/trailing white space characters from the row
                String trimmedLine = line.trim();

                // Check that the row isn't empty and isn't a comment line (starts with a
                // #character)
                if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#"))
                {
                    // Check if the line contains an odd number of double quotes
                    if (trimmedLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                    {
                        String nextLine = null;

                        // Step through the subsequent lines in order to find the end of multi-line
                        // value
                        while ((nextLine = br.readLine()) != null)
                        {
                            // Append the line to the preceding one, inserting the line feed. The
                            // non-trimmed variable is used so that trailing spaces within a
                            // quoted, multiple-line field aren't lost
                            line += "\n" + nextLine;

                            // Check if this is the line that ends the multi-line value (i.e., it
                            // ends with one double quote)
                            if (nextLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0)
                            {
                                // Stop searching; the multi-line string has been concatenated to
                                // the initial line
                                break;
                            }
                        }

                        // Remove any leading/trailing white space characters from the combined
                        // multiple line row. This only removed white space outside the quotes that
                        // bound the text
                        trimmedLine = line.trim();
                    }

                    // Remove any trailing commas and empty quotes from the row. If the CSV file is
                    // generated from a spreadsheet application then extra commas are appended to a
                    // row if needed for the number of columns to be equal with the other rows.
                    // These empty trailing columns are ignored
                    line = trimmedLine.replaceAll("(?:[,\\s*]|\"\\s*\",|,\"\\s*\")*$", "");

                    // Parse the import data. The values are comma- separated; however, commas
                    // within quotes are ignored - this allows commas to be included in the data
                    // values
                    columnValues = CcddUtilities.splitAndRemoveQuotes(line);
                }
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        return columnValues;
    }
}
