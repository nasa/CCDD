/**
 * CFS Command and Data Dictionary CSV handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.parser.JSONParser;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.OrderedJSONObject;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.JSONTags;
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
public class CcddCSVHandler extends CcddImportSupportHandler implements CcddImportExportInterface {
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDbTableCommandHandler dbTable;
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

    // List containing the imported table, table type, data type, and macro
    // definitions
    private List<TableDefinition> tableDefinitions;

    // List of original and new script associations
    private List<String[]> associations;

    /**********************************************************************************************
     * CSV data type tags
     *********************************************************************************************/
    private enum CSVTags {
        COLUMN_DATA("_column_data_", null), CELL_DATA("_table_cell_data_", null), // This is an internal tag and not
                                                                                  // used in the file
        NAME_TYPE("_name_type_", null), DESCRIPTION("_description_", null), DATA_FIELD("_data_field_", "_data_fields_"),
        MACRO("_macro_", "_macros_"), TABLE_TYPE("_table_type_", null),
        TABLE_TYPE_DATA_FIELD("_table_type_data_field_", "_table_type_data_fields_"),
        DATA_TYPE("_data_type_", "_data_types_"), INPUT_TYPE("_input_type_", "_input_types_"),
        RESERVED_MSG_IDS("_reserved_msg_id_", "_reserved_msg_ids_"),
        PROJECT_DATA_FIELD("_project_data_field_", "_project_data_fields_"),
        VARIABLE_PATHS("_variable_path_", "_variable_paths_"), GROUP("_group_", null),
        GROUP_DATA_FIELD("_group_data_field_", "_group_data_fields_"), SCRIPT_ASSOCIATION("_script_association_", null),
        APP_SCHEDULER("_app_sched_", null), TELEM_SCHEDULER("_telem_sched_", null), RATE_INFO("_rate_info_", null);

        private final String tag;
        private final String alternateTag;

        /******************************************************************************************
         * CSV data type tags constructor
         *
         * @param tag          text describing the data
         *
         * @param alternateTag alternate text describing the data; null if there is no
         *                     alternate name. This allows the same tag data type to
         *                     have two names, which is used for backwards
         *                     compatibility, due to a previous mixture of singular and
         *                     plural tag names
         *****************************************************************************************/
        CSVTags(String tag, String alternateTag) {
            this.tag = tag;
            this.alternateTag = alternateTag;
        }

        /******************************************************************************************
         * Get the data type tag
         *
         * @return Text describing the data
         *****************************************************************************************/
        protected String getTag() {
            return tag;
        }

        /**
         * @return the alternateTag
         */
        protected String getAlternateTag() {
            return alternateTag;
        }

        /******************************************************************************************
         * Check if the supplied text matches the data type tag name or alternate name
         *
         * @param text text describing the data
         *
         * @return true if the supplied text matches the tag name or alternate tag name,
         *         if one exists (case insensitive)
         *****************************************************************************************/
        protected boolean isTag(String text) {
            return tag.equalsIgnoreCase(text) || (alternateTag != null && alternateTag.equalsIgnoreCase(text));
        }
    }
    
    /**********************************************************************************************
     * Useful character sequences
     *********************************************************************************************/
    private enum Chars {
        NEW_LINE_CHAR("\n"), DOUBLE_QUOTE("\""), EMPTY_STRING(""), COMMA(","), COMMA_BETWEEN_QUOTES("\",\""),
        DOUBLE_QUOTE_AND_COMMA("\","), DOUBLE_NEW_LINE("\n\n"), QUOTE_NEW_LINE("\"\n");

        private final String value;

        /******************************************************************************************
         * Character sequence constructor
         *
         * @param value        text describing the character sequence
         *
         *****************************************************************************************/
        Chars(String value) {
            this.value = value;
        }

        /******************************************************************************************
         * Get the charcater sequence value
         *
         * @return Text representing the character sequence
         *****************************************************************************************/
        protected String getValue() {
            return value;
        }
    }


    /**********************************************************************************************
     * CSV handler class constructor
     *
     * @param ccddMain     main class reference
     *
     * @param groupHandler group handler reference
     *
     * @param parent       GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddCSVHandler(CcddMain ccddMain, CcddGroupHandler groupHandler, Component parent) {
        this.parent = parent;

        // Create references to shorten subsequent calls
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
        tableDefinitions = null;
        associations = null;
    }

    /**********************************************************************************************
     * Get the imported table definitions
     *
     * @return List of imported table definitions; an empty list if no table
     *         definitions exist in the import file
     *********************************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions() {
        return tableDefinitions;
    }

    /**********************************************************************************************
     * Get the list of original and new script associations
     *
     * @return List of original and new script associations; null if no new
     *         associations have been added
     *********************************************************************************************/
    @Override
    public List<String[]> getScriptAssociations() {
        return associations;
    }

    /**********************************************************************************************
     * Get the list of telemetry scheduler messages
     *
     * @return List of original and new telemetry scheduler messages; null if no new
     *         messages have been added
     *********************************************************************************************/
    @Override
    public List<String[]> getTlmSchedulerData() {
        return null;
    }

    /**********************************************************************************************
     * Get the list of original and new application scheduler data
     *
     * @return List of original and new application scheduler data; null if no new
     *         associations have been added
     *********************************************************************************************/
    @Override
    public List<String[]> getAppSchedulerData() {
        return null;
    }

    /**********************************************************************************************
     * Build the information from the internal table in the current file
     *
     * @param importFile   import file reference
     *
     * @param importType   ImportType.IMPORT_ALL to import the table type, data
     *                     type, and macro definitions, and the data from all the
     *                     table definitions; ImportType.FIRST_DATA_ONLY to load
     *                     only the data for the first table defined
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     * 
     * @param replaceExistingAssociations true to overwrite internal associations with
     *                                    those from the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importInternalTables(FileEnvVar importFile, ImportType importType, boolean ignoreErrors, boolean replaceExistingAssociations)
            throws CCDDException, IOException, Exception {
        try {
            /* Init local variables */
            String content = new String (Files.readAllBytes(Paths.get(importFile.getPath())));
            
            /********************* APPLICATION SCHEDULER *********************/
            if (content.contains(CSVTags.APP_SCHEDULER.getTag())) {
                /* Extract the application scheduler data */
                int beginIndex = content.indexOf(CSVTags.APP_SCHEDULER.getTag());
                int endIndex = content.indexOf("\n\n\n", beginIndex);
                String appData = "";
                if (endIndex == -1) {
                    appData = content.substring(beginIndex);
                } else {
                    appData = content.substring(beginIndex, endIndex);
                }
                
                appData = appData.replace(CSVTags.APP_SCHEDULER.getTag()+
                        Chars.NEW_LINE_CHAR.getValue(), Chars.EMPTY_STRING.getValue());
                /* Remove all double quotes */
                appData = appData.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                /* Strip any remaining new line characters */
                appData = appData.replace(Chars.NEW_LINE_CHAR.getValue(), Chars.EMPTY_STRING.getValue());
                /* Split the data into the individual columns */
                String[] Columns = appData.split(Chars.COMMA.getValue());
                /* Extract the information from each column */
                int maxMsgsPerTimeSlot = Integer.parseInt(Columns[0]);
                int maxMsgsPerSec = Integer.parseInt(Columns[1]);
                int maxMsgsPerCycle = Integer.parseInt(Columns[2]);
                int numberOfTimeSlots = Integer.parseInt(Columns[3]);
                
                /* Set and store the parameter values obtained from the CSV file */
                appHandler.setApplicationParameters(maxMsgsPerSec, maxMsgsPerCycle, maxMsgsPerTimeSlot,
                        numberOfTimeSlots, parent);
            }
            
            /********************* TELEMETRY SCHEDULER *********************/
            if (content.contains(CSVTags.TELEM_SCHEDULER.getTag())) {
                /* Extract the telemetry scheduler data */
                int beginIndex = content.indexOf(CSVTags.TELEM_SCHEDULER.getTag());
                int endIndex = content.indexOf("\n\n\n", beginIndex);
                String telemData = "";
                if (endIndex == -1) {
                    telemData = content.substring(beginIndex);
                } else {
                    telemData = content.substring(beginIndex, endIndex);
                }
                
                /* Remove the telem sched tag */
                telemData = telemData.replace(CSVTags.TELEM_SCHEDULER.getTag(), Chars.EMPTY_STRING.getValue());
                /* Remove the rate info tag */
                telemData = telemData.replace(CSVTags.RATE_INFO.getTag(), Chars.COMMA.getValue());
                /* Remove any new line characters */
                telemData = telemData.replace(Chars.NEW_LINE_CHAR.getValue(), Chars.EMPTY_STRING.getValue());
                /* Remove all double quotes */
                telemData = telemData.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                /* Split the data into the individual columns */
                String[] Columns = telemData.split(Chars.COMMA.getValue());
                /* Extract the information from each column */
                int maxSecPerMsg = Integer.parseInt(Columns[0]);
                int maxMsgsPerSec = Integer.parseInt(Columns[1]);
                boolean includeUneven = Boolean.valueOf(Columns[2]);                
                
                String[] rateDataStreamNames = new String[1];
                rateDataStreamNames[0] = Columns[4];
                
                int[] maximumMessagesPerCycle = new int[1];
                maximumMessagesPerCycle[0] = Integer.parseInt(Columns[5]);
                
                int[] maximumBytesPerSecond = new int[1];
                maximumBytesPerSecond[0] = Integer.parseInt(Columns[6]);
                
                /* Set and store the rate parameters obtained from the JSON file */
                /* TODO: three of these are blank strings. Not sure how to handle them yet */
                rateHandler.setRateParameters(maxSecPerMsg, maxMsgsPerSec, rateDataStreamNames,
                        maximumMessagesPerCycle, maximumBytesPerSecond, includeUneven, parent);
            }
            
            /********************* SCRIPT ASSOCIATIONS *********************/
            if (content.contains(CSVTags.SCRIPT_ASSOCIATION.getTag())) {
                /* Check if the associations haven't been loaded */
                if (associations == null) {
                    /* Get the script associations from the database */
                    associations = scriptHandler.getScriptAssociations(parent);
                }
                
                /* Extract the script association data */
                int beginIndex = content.indexOf(CSVTags.SCRIPT_ASSOCIATION.getTag());
                int endIndex = content.indexOf("\n\n\n", beginIndex);
                String scriptData = "";
                if (endIndex == -1) {
                    scriptData = content.substring(beginIndex);
                } else {
                    scriptData = content.substring(beginIndex, endIndex);
                }
                
                /* Extract the script association data */
                scriptData = scriptData.replace(CSVTags.SCRIPT_ASSOCIATION.getTag()+Chars.NEW_LINE_CHAR.getValue(),
                        Chars.EMPTY_STRING.getValue());
                /* Remove all double quotes */
                scriptData = scriptData.replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                /* Split the data into the individual definitions */
                String[] scriptAssocDefns = scriptData.split(Chars.NEW_LINE_CHAR.getValue());
                /* Step through each definition */
                for (int i = 0; i < scriptAssocDefns.length; i++) {
                    /* Split the data into the individual columns */
                    String[] Columns = scriptAssocDefns[i].split(Chars.COMMA.getValue());
                    
                    // The code appears to expect 4 columns from the above operation.
                    // If there are blank entries, there may be only 3 columns
                    // Handle this by creating the fourth column
                    String fourthCol = "";
                    if(Columns.length == 4){
                        fourthCol = Columns[3];
                    }

                    /* Add the script association, checking for errors */
                    ignoreErrors = addImportedScriptAssociation(ignoreErrors, replaceExistingAssociations, associations,
                            new String[] {Columns[0], Columns[1], Columns[2],
                                    CcddScriptHandler.convertAssociationMembersFormat(fourthCol, true)},
                            importFile.getAbsolutePath(), scriptHandler, parent);
                }
            }
        } catch (Exception pe) {
            /* Inform the user that the file cannot be parsed */
            /* TODO: Add more code here to handle exceptions */
            throw new CCDDException("1Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }

        return;
    }

    /**********************************************************************************************
     * Import the input types, table types, table type data fields and data types from the given file
     *
     * @param importFile   import file reference
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     *
     * @param replaceExistingMacros true to replace existing macros
     * 
     * @param replaceExistingTables true to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importTableInfo(FileEnvVar importFile, ImportType importType, boolean ignoreErrors,
            boolean replaceExistingMacros, boolean replaceExistingTables)
            throws CCDDException, IOException, Exception {
        try {
            /* Init local variables */
            List<TableTypeDefinition> NewTableTypeDefinitions = new ArrayList<TableTypeDefinition>();
            List<String[]> NewMacroDefns = new ArrayList<String[]>();
            String content = new String (Files.readAllBytes(Paths.get(importFile.getPath())));
            TableTypeDefinition TableTypeDefn = null;
            int ColumnNumber = 0;
            String DataFields = "";
            
            /********************* TABLE TYPES *********************/
            if (content.contains(CSVTags.TABLE_TYPE.getTag())) {
                /* Extract the data related to table types and table type data fields from 
                 * the file data.
                 */
                int beginIndex = content.indexOf(CSVTags.TABLE_TYPE.getTag());
                int endIndex = content.indexOf(CSVTags.DATA_TYPE.getTag());
                if (endIndex == -1) {
                    /* If data types were not included in the export than we look
                     * for the first occurrence of a triple newline character after
                     * the beginIndex
                     */
                    endIndex = content.indexOf("\n\n\n", beginIndex);
                }
                String Data = content.substring(beginIndex, endIndex);
                
                /* Divide the data into each individual table type definition, which may
                 * or may not have table type data fields included.
                 */
                String[] TableTypeDefns = Data.split(CSVTags.TABLE_TYPE.getTag()+
                        Chars.NEW_LINE_CHAR.getValue());

                /* Step through each definition */
                for (int i = 1; i < TableTypeDefns.length; i++) {
                    DataFields = "";
                    /* Check if any Table Type Data Fields exist for this entry */
                    if (TableTypeDefns[i].contains(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())) {
                        /* Divide the data into the table type definition and the table type 
                         * data fields definition.
                         */
                        DataFields = TableTypeDefns[i].split(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())[1];
                        TableTypeDefns[i] = TableTypeDefns[i].split(CSVTags.TABLE_TYPE_DATA_FIELD.getTag())[0];
                    }
                    
                    /* Each definition will have multiple rows of data */
                    String[] Rows  = TableTypeDefns[i].split(Chars.NEW_LINE_CHAR.getValue());
                    /* Extract the name and description of the table */
                    String TableTypeName = Rows[0].split(Chars.COMMA.getValue())[0].replace(Chars.DOUBLE_QUOTE.getValue(),
                            Chars.EMPTY_STRING.getValue());
                    String TableTypeDescription = Chars.EMPTY_STRING.getValue() + Rows[0].split(Chars.COMMA.getValue())[1]
                            .replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                    
                    boolean representsCmdArgument = Boolean.parseBoolean(Rows[0].split(Chars.COMMA.getValue())[2]
                            .replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue()));
                    
                    /* If this table type represents a command argument then append a 1 to the description.
                     * If not append a 0 
                     */
                    if (representsCmdArgument) {
                        TableTypeDescription = "1"+TableTypeDescription;
                    } else {
                        TableTypeDescription = "0"+TableTypeDescription;
                    }
                    
                    /* Check that both the name, description and rather or not the table represents a command is all defined. */
                    if (Rows[0].split(Chars.COMMA.getValue()).length == 3) {
                        /* Create a new table type definition */
                        TableTypeDefn = new TableTypeDefinition(TableTypeName, TableTypeDescription);
                    } else {
                        /* The number of inputs is incorrect. Check if the error should be ignored or
                         * the import canceled
                         */
                        ignoreErrors = getErrorResponse(ignoreErrors,
                                "<html><b>Missing table type name in import file '</b>"
                                        + importFile.getAbsolutePath() + "<b>'; continue?",
                                "Table Type Error", "Ignore this table type",
                                "Ignore this and any remaining invalid table types",
                                "Stop importing", parent);
                    }
                    
                    /* Reset the column counter for each entry */
                    ColumnNumber = 0;
                    
                    /* Add the table type column definition, checking for (and if possible,
                     * correcting) errors
                     */
                    for (int y = 1; y < Rows.length; y++) {
                        /* Split on each comma, but make sure it is one that has a double quote before
                         * and after it as a comma can be in a text string as well.
                         */
                        String[] Columns = Rows[y].split(Chars.COMMA_BETWEEN_QUOTES.getValue());
                        /* Strip all double quotes */
                        for (int x = 0; x < Columns.length; x++) {
                            Columns[x] = Columns[x].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                        }
                        
                        /* Check if the expected number of inputs is present */
                        if (Columns.length == (TableTypeEditorColumnInfo.values().length -1)) {
                            /* Add/Update the table type definition columns */
                            ignoreErrors = addImportedTableTypeColumnDefinition(ignoreErrors, TableTypeDefn,
                                new String[] {Integer.toString(ColumnNumber),
                                        Columns[TableTypeEditorColumnInfo.NAME.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.DESCRIPTION.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.UNIQUE.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.REQUIRED.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()-1],
                                        Columns[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()-1]},
                                importFile.getAbsolutePath(), inputTypeHandler, parent);
                            /* Increment the column number counter as each column is added */
                            ColumnNumber++;
                        } else {
                            /* The number of inputs is incorrect */
                            /* Check if the error should be ignored or the import canceled */
                            ignoreErrors = getErrorResponse(ignoreErrors,
                                    "<html><b>Table type '</b>" + TableTypeDefn.getTypeName()
                                            + "<b>' definition has missing or extra "
                                            + "input(s) in import file '</b>"
                                            + importFile.getAbsolutePath() + "<b>'; continue?",
                                    "Table Type Error", "Ignore this table type",
                                    "Ignore this and any remaining invalid table types",
                                    "Stop importing", parent);
                        }
                    }
                    
                    /********************* TABLE TYPE DATA FIELDS *********************/
                    if (DataFields != Chars.EMPTY_STRING.getValue()) {
                        /* Split the data into each individual data field definition */
                        String[] Fields =  DataFields.split(Chars.NEW_LINE_CHAR.getValue());
                        for (int x = 1; x < Fields.length; x++) {
                            /* Split on each comma, but make sure it is one that has a double quote before
                             * and after it as a comma can be in a text string as well.
                             */
                            String[] Columns = Fields[x].split(Chars.COMMA_BETWEEN_QUOTES.getValue());
                            /* Strip all double quotes */
                            for (int y = 0; y < Columns.length; y++) {
                                Columns[y] = Columns[y].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                            }
                            
                            /* Add the data field definition, checking for (and if possible,
                             * correcting) errors
                             */
                            ignoreErrors = addImportedDataFieldDefinition(ignoreErrors, replaceExistingTables, TableTypeDefn,
                                    new String[] {CcddFieldHandler.getFieldTypeName(TableTypeDefn.getTypeName()),
                                            Columns[FieldEditorColumnInfo.NAME.ordinal()],
                                            Columns[FieldEditorColumnInfo.DESCRIPTION.ordinal()],
                                            Columns[FieldEditorColumnInfo.CHAR_SIZE.ordinal()],
                                            Columns[FieldEditorColumnInfo.INPUT_TYPE.ordinal()],
                                            Columns[FieldEditorColumnInfo.REQUIRED.ordinal()],
                                            Columns[FieldEditorColumnInfo.APPLICABILITY.ordinal()],
                                            Columns[FieldEditorColumnInfo.VALUE.ordinal()],
                                            ""},
                                    importFile.getAbsolutePath(), inputTypeHandler, fieldHandler,
                                    parent);
                        }
                    }
                    
                    /* Add the table type definition */
                    NewTableTypeDefinitions.add(TableTypeDefn);
                }
                
                // Add the table type if it's new or update an existing one
                tableTypeHandler.updateTableTypes(NewTableTypeDefinitions);
            }
            
            /********************* MACROS *********************/
            // Check both the normal and alternative tag names for a match
            boolean isMacroContained = content.contains(CSVTags.MACRO.getTag()) || content.contains(CSVTags.MACRO.getAlternateTag());
            if (isMacroContained) {
                // Use the tag that exists (one of them must if it gets to here)
                String tagToUse = content.contains(CSVTags.MACRO.getTag()) ? CSVTags.MACRO.getTag() : CSVTags.MACRO.getAlternateTag();
                
                /* Extract the data related to macro definitions */
                /* Handle two formats that may exist in the input data */
                int beginIndex = content.indexOf(tagToUse);
                 /* find the next instance of three return characters */
                int endIndex = content.indexOf("\n\n\n", beginIndex);
                /* If that failed, then find two return chars */
                endIndex = endIndex == -1 ? content.indexOf("\n\n", beginIndex) : endIndex;
                String Data = "";
                if (endIndex == -1) {
                    Data = content.substring(beginIndex);
                } else {
                    Data = content.substring(beginIndex, endIndex);
                }

                String[] MacroDefns = Data.split(Chars.NEW_LINE_CHAR.getValue());
                for (int i = 1; i < MacroDefns.length; i++) {
                    /* Split on commas between quotes. It is done this way because it is
                     * valid to have commas inside of the macro definitions and this prevents
                     * the code from splitting a definition up if this is the case.
                     */
                    String[] Columns = MacroDefns[i].split(Chars.COMMA_BETWEEN_QUOTES.getValue());
                    /* Strip all double quotes */
                    for (int y = 0; y < Columns.length; y++) {
                        Columns[y] = Columns[y].replace(Chars.DOUBLE_QUOTE.getValue(),
                                Chars.EMPTY_STRING.getValue());
                    }

                    /* Check if the expected number of inputs is present */
                    if (Columns.length == 2 || Columns.length == 1) {
                        /* Build the macro definition */
                        String[] macroDefn = new String[] { Columns[0],
                                (Columns.length == 2 ? Columns[1] : Chars.EMPTY_STRING.getValue()),
                                Chars.EMPTY_STRING.getValue()};

                        /* Check if the macro definition is valid */
                        checkMacroDefinition(macroDefn);

                        /* Add the macro definition (add a blank to represent the OID) */
                        NewMacroDefns.add(macroDefn);
                    }
                    /* The number of inputs is incorrect */
                    else {
                        /* Check if the error should be ignored or the import canceled */
                        ignoreErrors = getErrorResponse(ignoreErrors,
                                "<html><b>Missing or extra macro definition "
                                        + "input(s) in import file '</b>"
                                        + importFile.getAbsolutePath() + "<b>'; continue?",
                                "Macro Error", "Ignore this macro",
                                "Ignore this and any remaining invalid macros",
                                "Stop importing", parent);
                    }
                }
                
                // Convert all of the new macro definitions to a set of unique entries
                // based on the macro name (the first array element)
                // A pair with a boolean indicating if the input set was unique and
                // the list of unique entries that were extracted
                Pair<Boolean, List<String[]>> uniqueResults = convertToUniqueList(NewMacroDefns);
                
                boolean isDupDetected = !uniqueResults.getLeft();
                if (isDupDetected) {
                    // Get the user's input
                    if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html> <b> Continue import and ignore the duplicate values?", "Duplicate Macros in Input File",
                            JOptionPane.QUESTION_MESSAGE,
                            DialogOption.OK_CANCEL_OPTION) != OK_BUTTON) {
                        throw new CCDDException("Duplicate macros detected in the input file, user Cancelled");
                    }
                }
                
                /* Add the macro if it's new or match it to an existing one with the same
                 * name. If the flag to replace existing macro values is false then get the
                 * list of macros names where the existing and import file values differ
                 */
                List<String> mismatchedMacros = macroHandler.updateMacros(uniqueResults.getRight(), replaceExistingMacros);
                
                /* Check if any existing and import file macro values differ ( the flag to
                 * replace existing macro values is false)
                 */
                if (!mismatchedMacros.isEmpty()) {
                    boolean continueOnError = false;

                    /* Check if the user elects to ignore the difference(s), keeping the
                     * existing macro values, or cancels the import operation
                     */
                    getErrorResponse(continueOnError,
                            "<html><b>The value for imported macro(s) '</b>"
                                    + CcddUtilities.convertArrayToStringTruncate(
                                            mismatchedMacros.toArray(new String[0]))
                                    + "<b>' doesn't match the existing definition(s) in import file '</b>"
                                    + importFile.getAbsolutePath() + "<b>'; continue?",
                            "Macro Value Mismatch", null,
                            "Ignore macro value difference(s) (keep existing value(s))", "Stop importing", true,
                            parent);
                }
            }
        } catch (Exception pe) {
            /* Inform the user that the file cannot be parsed */
            /* TODO: Add more code here to handle exceptions */
            throw new CCDDException("2Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }
        return;
    }

    /**********************************************************************************************
     * Build the information from the input and data type definition(s) in the
     * current file
     *
     * @param importFile   import file reference
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void importInputTypes(FileEnvVar importFile, ImportType importType, boolean ignoreErrors)
            throws CCDDException, IOException, Exception {
        /* Init local variables */
        List<String[]> newInputTypeDefns = new ArrayList<String[]>();
        List<String[]> newDataTypeDefns = new ArrayList<String[]>();
        String content = new String (Files.readAllBytes(Paths.get(importFile.getPath())));
        
        try {
            /********************* INPUT TYPES *********************/
            if (content.contains(CSVTags.INPUT_TYPE.getTag())) {
                /* Extract the input type definitions */
                int beginIndex = content.indexOf(CSVTags.INPUT_TYPE.getTag());
                int endIndex = content.indexOf(CSVTags.TABLE_TYPE.getTag());
                /* If there are no table type definitions in the file the data type tag
                 * will be used to mark the end of the input type definitions. If there
                 * is no data type input tag than the triple newline character sequence 
                 * will mark the end of the input type definitions.
                 */
                if (endIndex == -1) {
                    endIndex = content.indexOf(CSVTags.DATA_TYPE.getTag());
                    if (endIndex == -1) {
                        endIndex = content.indexOf("\n\n\n", beginIndex);
                    }
                }
                
                String InputData = content.substring(beginIndex, endIndex);
                InputData = InputData.replace(CSVTags.INPUT_TYPE.getTag()+Chars.NEW_LINE_CHAR.getValue(),
                        Chars.EMPTY_STRING.getValue());
                
                /* Replace any double end line characters */
                InputData = InputData.replace(Chars.DOUBLE_NEW_LINE.getValue(), Chars.NEW_LINE_CHAR.getValue());
                /* There are often new line characters within the text fields of the input types.
                 * In order to guard against these we split only on new line characters that are 
                 * directly after a double quote
                 */
                String[] inputTypeDefns = InputData.split(Chars.QUOTE_NEW_LINE.getValue());
                
                /* Step through each definition */
                for (int i = 0; i < inputTypeDefns.length; i++) {
                    /* Split on each comma, but make sure it is one that has a double quote before
                     * and after it as a comma can be in a text string as well.
                     */
                    String[] Columns = inputTypeDefns[i].split(Chars.COMMA_BETWEEN_QUOTES.getValue());
                    /* Strip all double quotes */
                    for (int x = 0; x < Columns.length; x++) {
                        Columns[x] = Columns[x].replace(Chars.DOUBLE_QUOTE.getValue(), Chars.EMPTY_STRING.getValue());
                    }
                    
                    /* Check that the number of inputs is correct */
                    if (Columns.length == InputTypesColumn.values().length - 1) {
                        /* Check if the input type definition is valid */
                        String[] inputTypeDefn = checkInputTypeDefinition(
                                new String[] { Columns[InputTypesColumn.NAME.ordinal()],
                                        Columns[InputTypesColumn.DESCRIPTION.ordinal()],
                                        Columns[InputTypesColumn.MATCH.ordinal()],
                                        Columns[InputTypesColumn.ITEMS.ordinal()],
                                        Columns[InputTypesColumn.FORMAT.ordinal()], "" });
    
                        /* Add the input type definition (add a blank to represent the OID) */
                        newInputTypeDefns.add(inputTypeDefn);
                    } else {
                        /* The number of inputs is incorrect. Check if the error should be ignored
                         * or the import canceled
                         */ 
                        ignoreErrors = getErrorResponse(ignoreErrors,
                                "<html><b>Missing or extra input type definition "
                                        + "input(s) in import file '</b>"
                                        + importFile.getAbsolutePath() + "<b>'; continue?",
                                "Input Type Error", "Ignore this input type",
                                "Ignore this and any remaining invalid input types",
                                "Stop importing", parent);
                    }
                }
                
                /* Add the input type if it's new or match it to an existing one with the same
                 * name if the type definitions are the same
                 */
                inputTypeHandler.updateInputTypes(newInputTypeDefns);
            }
            
            /********************* DATA TYPES *********************/
            if (content.contains(CSVTags.DATA_TYPE.getTag())) {
                /* Extract the data type definitions */
                int beginIndex = content.indexOf(CSVTags.DATA_TYPE.getTag());
                int endIndex = content.indexOf("\n\n\n", beginIndex);
                String dataTypeData = "";
                if (endIndex == -1) {
                    dataTypeData = content.substring(beginIndex);
                } else {
                    dataTypeData = content.substring(beginIndex, endIndex);
                }
                
                dataTypeData = dataTypeData.replace(CSVTags.DATA_TYPE.getTag()+Chars.NEW_LINE_CHAR.getValue(),
                        Chars.EMPTY_STRING.getValue());
                
                /* Split the data into the individual data type definitions */
                String[] dataTypeDefns = dataTypeData.split(Chars.NEW_LINE_CHAR.getValue());
                
                /* Step through each definition */
                for (int i = 0; i < dataTypeDefns.length; i++) {
                    String[] Columns = dataTypeDefns[i].split(Chars.COMMA.getValue());
                    /* Strip all double quotes */
                    for (int x = 0; x < Columns.length; x++) {
                        Columns[x] = Columns[x].replace(Chars.DOUBLE_QUOTE.getValue(),
                                Chars.EMPTY_STRING.getValue());
                    }
                    
                    /* Check if the expected number of inputs is present */
                    if (Columns.length == DataTypesColumn.values().length - 1) {
                        /* Build the data type definition */
                        String[] dataTypeDefn = new String[] {
                                Columns[DataTypesColumn.USER_NAME.ordinal()],
                                Columns[DataTypesColumn.C_NAME.ordinal()],
                                Columns[DataTypesColumn.SIZE.ordinal()],
                                Columns[DataTypesColumn.BASE_TYPE.ordinal()], "" };
    
                        /* Check if the data type definition is valid */
                        checkDataTypeDefinition(dataTypeDefn);
    
                        /* Add the data type definition (add a blank to represent the OID) */
                        newDataTypeDefns.add(dataTypeDefn);
                    } else {
                        /* The number of inputs is incorrect */
                        /* Check if the error should be ignored or the import canceled */
                        ignoreErrors = getErrorResponse(ignoreErrors,
                                "<html><b>Missing or extra data type definition "
                                        + "input(s) in import file '</b>"
                                        + importFile.getAbsolutePath() + "<b>'; continue?",
                                "Data Type Error", "Ignore this data type",
                                "Ignore this and any remaining invalid data types",
                                "Stop importing", parent);
                    }
                }
                
                /* Add the data type if it's new or match it to an existing one with the
                 * same name if the type definitions are the same
                 */
                dataTypeHandler.updateDataTypes(newDataTypeDefns);
            }
        } catch (Exception pe) {
            /* Inform the user that the file cannot be parsed */
            /* TODO: Add more code here to handle exceptions */
            throw new CCDDException("3Parsing error; cause '</b>" + pe.getMessage() + "<b>'");
        }
        return;
    }

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile            import file reference
     *
     * @param importType            ImportType.IMPORT_ALL to import the table type,
     *                              data type, and macro definitions, and the data
     *                              from all the table definitions;
     *                              ImportType.FIRST_DATA_ONLY to load only the data
     *                              for the first table defined
     *
     * @param targetTypeDefn        table type definition of the table in which to
     *                              import the data; ignored if importing all tables
     *
     * @param ignoreErrors          true to ignore all errors in the import file
     *
     * @param replaceExistingMacros true to replace the values for existing macros
     *
     * @param replaceExistingGroups true to replace existing group definitions
     * 
     * @param replaceExistingTables true to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile, ImportType importType, TypeDefinition targetTypeDefn,
            boolean ignoreErrors, boolean replaceExistingMacros, boolean replaceExistingGroups, boolean replaceExistingTables)
            throws CCDDException, IOException, Exception {
        BufferedReader br = null;

        try {
            ProjectDefinition projectDefn = new ProjectDefinition();
            List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();
            tableDefinitions = new ArrayList<TableDefinition>();

            /* Make two passes through the file, first to get the groups, project fields 
             * and reserved msgids and then a Second pass to read the table data and fields
             */
            for (int loop = 1; loop <= 2; loop++) {
                String groupDefnName = null;

                /* Create a buffered reader to read the file */
                br = new BufferedReader(new FileReader(importFile));

                /* Initialize the import tag */
                CSVTags importTag = null;

                /* Read first line in file */
                String line = br.readLine();

                /* Continue to read the file until EOF is reached or an error is detected. This
                 * outer while loop accounts for multiple table definitions within a single file
                 */
                while (line != null) {
                    /* Initialize the table information */
                    int numColumns = 0;
                    String tablePath = "";

                    /* Create empty table information and table type definition references */
                    TypeDefinition typeDefn = null;

                    /* Storage for column indices */
                    int[] columnIndex = null;

                    /* Initialize the number of matching columns and the cell data storage */
                    String[] columnValues = null;

                    /* Create a table definition to contain the table's information */
                    TableDefinition tableDefn = new TableDefinition();
                    
                    /* Continue to read the file until EOF is reached or an error is detected. This
                     * inner while loop reads the information for a single table in the file
                     */
                    while (line != null) {
                        /* Remove any leading/trailing white space characters from the row */
                        String trimmedLine = line.trim();

                        /* Check that the row isn't empty and isn't a comment line (starts with a #character) */
                        if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("#")) {
                            boolean isTag = false;
                            boolean isNextTable = false;

                            /* Check if the line contains an odd number of double quotes */
                            if (trimmedLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0) {
                                String nextLine = null;

                                /* Step through the subsequent lines in order to find the end of
                                 * multi-line value
                                 */
                                while ((nextLine = br.readLine()) != null) {
                                    /* Append the line to the preceding one, inserting the line
                                     * feed. The non-trimmed variable is used so that trailing
                                     * spaces within a quoted, multiple-line field aren't lost
                                     */
                                    line += "\n" + nextLine;

                                    /* Check if this is the line that ends the multi-line value
                                     * (i.e., it ends with one double quote)
                                     */
                                    if (nextLine.replaceAll("[^\"]*(\")?", "$1").length() % 2 != 0) {
                                        /* Stop searching; the multi-line string has been concatenated 
                                         * to the initial line
                                         */
                                        break;
                                    }
                                }

                                /* Remove any leading/trailing white space characters from the
                                 * combined multiple line row. This only removed white space
                                 * outside the quotes that bound the text
                                 */
                                trimmedLine = line.trim();
                            }

                            /* Remove any trailing commas and empty quotes from the row. If the CSV
                             * file is generated from a spreadsheet application then extra commas
                             * are appended to a row if needed for the number of columns to be
                             * equal with the other rows. These empty trailing columns are ignored
                             */
                            line = trimmedLine.replaceAll("(?:[,\\s*]|\"\\s*\",|,\"\\s*\")*$", "");

                            /* Parse the import data. The values are comma- separated; however,
                             * commas within quotes are ignored - this allows commas to be included
                             * in the data values
                             */
                            columnValues = CcddUtilities.splitAndRemoveQuotes(line);

                            /* Remove any leading/trailing white space characters from the first
                             * column value
                             */
                            String firstColumn = columnValues[0].trim();

                            /* Step through the import tags */
                            for (CSVTags csvTag : CSVTags.values()) {
                                /* Check if the first column value matches the tag name */
                                if (csvTag.isTag(firstColumn)) {
                                    isTag = true;

                                    /* Set the import tag and stop searching */
                                    importTag = csvTag;

                                    /* Check if this is the table name and table type tag */
                                    if (CSVTags.NAME_TYPE.isTag(firstColumn)) {
                                        /* Check if this is the second pass and if the name and type
                                         * are already set; if so, this is the beginning of another
                                         * table's information
                                         */
                                        if (loop == 2 && !tablePath.isEmpty()) {
                                            /* Set the flag to indicate that this is the beginning
                                             * of the next table definition
                                             */
                                            isNextTable = true;
                                        }
                                    }
                                    break;
                                }
                            }

                            /* Check if this is the beginning of the next table definition */
                            if (isNextTable) {
                                /* Stop processing the file in order to create the table
                                 * prior to beginning another one
                                 */
                                break;
                            }

                            /* Not a tag (or no table name and type are defined); read in the
                             * information based on the last tag read
                             */
                            if (!isTag) {
                                /* Check if this is the first pass */
                                if (loop == 1) {
                                    switch (importTag) {
                                    case RESERVED_MSG_IDS:
                                        /* Check if all definitions are to be loaded */
                                        if (importType == ImportType.IMPORT_ALL) {
                                            /* Check if the expected number of inputs is present */
                                            if (columnValues.length == 2 || columnValues.length == 1) {
                                                /* Append empty columns as needed to fill out
                                                 * the expected number of inputs
                                                 */
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                        2 - columnValues.length);

                                                /* Add the reserved message ID definition (add
                                                 * a blank to represent the OID)
                                                 */
                                                reservedMsgIDDefns.add(new String[] {
                                                        columnValues[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                        columnValues[ReservedMsgIDsColumn.DESCRIPTION.ordinal()], "" });
                                            } else {
                                                /* The number of inputs is incorrect
                                                 * Check if the error should be ignored or the
                                                 * import canceled
                                                 */
                                                ignoreErrors = getErrorResponse(ignoreErrors,
                                                        "<html><b>Missing or extra reserved message ID "
                                                                + "definition input(s) in import file '</b>"
                                                                + importFile.getAbsolutePath() + "<b>'; continue?",
                                                        "Reserved Message ID Error", "Ignore this data type",
                                                        "Ignore this and any remaining invalid reserved message IDs",
                                                        "Stop importing", parent);
                                            }
                                        }
                                        break;

                                    case PROJECT_DATA_FIELD:
                                        /* Check if all definitions are to be loaded */
                                        if (importType == ImportType.IMPORT_ALL) {
                                            /* Append empty columns as needed to fill out the
                                             * expected number of inputs
                                             */
                                            columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                    FieldsColumn.values().length - 1 - columnValues.length);

                                            /* Add the data field definition, checking for (and
                                             * if possible, correcting) errors
                                             */
                                            ignoreErrors = addImportedDataFieldDefinition(ignoreErrors, replaceExistingTables, projectDefn,
                                                    new String[] { CcddFieldHandler.getFieldProjectName(),
                                                            columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1] },
                                                    importFile.getAbsolutePath(), inputTypeHandler, fieldHandler,
                                                    parent);
                                        }

                                        break;

                                    case GROUP:
                                        /* Check if all definitions are to be loaded */
                                        if (importType == ImportType.IMPORT_ALL) {
                                            /* Check if the expected number of inputs is present */
                                            if (columnValues.length == GroupDefinitionColumn.values().length
                                                    || columnValues.length == GroupDefinitionColumn.values().length
                                                            - 1) {
                                                /* Append empty columns as needed to fill out
                                                 * the expected number of inputs
                                                 */
                                                columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                        GroupDefinitionColumn.values().length - columnValues.length);

                                                /* Store the group name */
                                                groupDefnName = columnValues[GroupDefinitionColumn.NAME.ordinal()];

                                                /* Add the group definition, checking for (and
                                                 * if possible, correcting) errors
                                                 */
                                                addImportedGroupDefinition(new String[] { groupDefnName,
                                                        columnValues[GroupDefinitionColumn.DESCRIPTION.ordinal()],
                                                        columnValues[GroupDefinitionColumn.IS_APPLICATION.ordinal()],
                                                        columnValues[GroupDefinitionColumn.MEMBERS.ordinal()] },
                                                        importFile.getAbsolutePath(), replaceExistingGroups,
                                                        groupHandler);
                                            }
                                            /* The number of inputs is incorrect */
                                            else {
                                                /* Check if the error should be ignored or the import canceled */
                                                ignoreErrors = getErrorResponse(ignoreErrors,
                                                        "<html><b>Group definition has missing "
                                                                + "or extra input(s) in import file '</b>"
                                                                + importFile.getAbsolutePath() + "<b>'; continue?",
                                                        "Group Error", "Ignore this invalid group",
                                                        "Ignore this and any remaining invalid group definitions",
                                                        "Stop importing", parent);
                                            }
                                        }
                                        break;

                                    case GROUP_DATA_FIELD:
                                        /* Check if all definitions are to be loaded */
                                        if (importType == ImportType.IMPORT_ALL) {
                                            /* Append empty columns as needed to fill out the
                                             * expected number of inputs
                                             */
                                            columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                    FieldsColumn.values().length - 1 - columnValues.length);

                                            /* Add the data field definition, checking for (and
                                             * if possible, correcting) errors
                                             */
                                            ignoreErrors = addImportedDataFieldDefinition(ignoreErrors, replaceExistingGroups, projectDefn,
                                                    new String[] { CcddFieldHandler.getFieldGroupName(groupDefnName),
                                                            columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                            columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1] },
                                                    importFile.getAbsolutePath(), inputTypeHandler, fieldHandler,
                                                    parent);
                                        }

                                        break;

                                    case SCRIPT_ASSOCIATION:
                                    case INPUT_TYPE:
                                    case CELL_DATA:
                                    case COLUMN_DATA:
                                    case DATA_FIELD:
                                    case DESCRIPTION:
                                    case NAME_TYPE:
                                        break;

                                    default:
                                        /* This table contained no usable information that could be imported */
                                    }
                                }
                                /* This is the second pass */
                                else {
                                    switch (importTag) {
                                    case NAME_TYPE:
                                        /* Check if the expected number of inputs is present (the
                                         * third value, the system name, is optional and not used)
                                         */
                                        if (columnValues.length == 2 || columnValues.length == 3) {
                                            /* Get the table's type definition. If importing into
                                             * an existing table then use its type definition
                                             */
                                            typeDefn = importType == ImportType.IMPORT_ALL
                                                    ? tableTypeHandler.getTypeDefinition(columnValues[1])
                                                    : targetTypeDefn;

                                            /* Check if the table type doesn't exist */
                                            if (typeDefn == null) {
                                                throw new CCDDException(
                                                        "Unknown table type '</b>" + columnValues[1] + "<b>'");
                                            }

                                            /* Use the table name (with path, if applicable) and type to build
                                             * the parent, path, and type for the table information class
                                             */
                                            tablePath = columnValues[0];
                                            tableDefn.setName(tablePath);
                                            tableDefn.setTypeName(columnValues[1]);

                                            /* Get the number of expected columns (the hidden columns, primary
                                             * key and row index, should not be included in the CSV file)
                                             */
                                            numColumns = typeDefn.getColumnCountVisible();
                                        }
                                        /* Incorrect number of inputs */
                                        else {
                                            throw new CCDDException("Too many/few table name and type inputs");
                                        }

                                        break;

                                    case DESCRIPTION:
                                        /* Store the table description */
                                        tableDefn.setDescription(columnValues[0]);
                                        break;

                                    case COLUMN_DATA:
                                        /* Check if any column names exist */
                                        if (columnValues.length != 0) {
                                            /* Number of columns in an import file that match the target table */
                                            int numValidColumns = 0;

                                            /* Create storage for the column indices */
                                            columnIndex = new int[columnValues.length];

                                            /* Step through each column name */
                                            for (int index = 0; index < columnValues.length; index++) {
                                                /* Get the index for this column name */
                                                columnIndex[index] = typeDefn
                                                        .getVisibleColumnIndexByUserName(columnValues[index]);

                                                /* Check if the column name in the file matches that of a column in the table */
                                                if (columnIndex[index] != -1) {
                                                    /* Increment the counter that tracks the number of matched columns */
                                                    numValidColumns++;
                                                }
                                                /* The number of inputs is incorrect */
                                                else {
                                                    /* Check if the error should be ignored or the import canceled */
                                                    ignoreErrors = getErrorResponse(ignoreErrors,
                                                            "<html><b>Table '</b>" + tableDefn.getName()
                                                                    + "<b>' column name '</b>" + columnValues[index]
                                                                    + "<b>' unrecognized in import file '</b>"
                                                                    + importFile.getAbsolutePath() + "<b>'; continue?",
                                                            "Column Error", "Ignore this invalid column name",
                                                            "Ignore this and any remaining invalid column names",
                                                            "Stop importing", parent);
                                                }
                                            }

                                            /* Check if no column names in the file match those in the table */
                                            if (numValidColumns == 0) {
                                                throw new CCDDException("No columns match those in the target table",
                                                        JOptionPane.WARNING_MESSAGE);
                                            }
                                        }
                                        /* The file contains no column data */
                                        else {
                                            throw new CCDDException("File format invalid");
                                        }

                                        /* Set the import tag to look for cell data */
                                        importTag = CSVTags.CELL_DATA;
                                        break;

                                    case CELL_DATA:
                                        /* Create storage for the row of cell data and initialize the values to nulls
                                         * (a null indicates that the pasted cell value won't overwrite the current
                                         * table value if overwriting; if inserting the pasted value is changed to a space)
                                         */
                                        String[] rowData = new String[numColumns];
                                        Arrays.fill(rowData, null);

                                        /* Step through each column in the row */
                                        for (int index = 0; index < columnValues.length; index++) {
                                            /* Check if the column exists */
                                            if (index < columnIndex.length && columnIndex[index] != -1) {
                                                /* Store the cell data in the column matching
                                                 * the one in the target table
                                                 */
                                                rowData[columnIndex[index]] = columnValues[index];
                                            }
                                        }

                                        /* Add the row of data read in from the file to the cell data list */
                                        tableDefn.addData(rowData);
                                        break;

                                    case DATA_FIELD:
                                        /* Append empty columns as needed to fill out the expected number of inputs */
                                        columnValues = CcddUtilities.appendArrayColumns(columnValues,
                                                FieldsColumn.values().length - 1 - columnValues.length);

                                        /* Add the data field definition, checking for (and if possible, correcting) errors */
                                        ignoreErrors = addImportedDataFieldDefinition(ignoreErrors, replaceExistingTables, tableDefn,
                                                new String[] { tableDefn.getName(),
                                                        columnValues[FieldsColumn.FIELD_NAME.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_DESC.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_SIZE.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_TYPE.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_REQUIRED.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_APPLICABILITY.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_VALUE.ordinal() - 1],
                                                        columnValues[FieldsColumn.FIELD_INHERITED.ordinal() - 1] },
                                                importFile.getAbsolutePath(), inputTypeHandler, fieldHandler, parent);

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
                                        /* No data to import from this file */
                                    }
                                }
                            }
                        }

                        /* Read next line in file */
                        line = br.readLine();
                    }

                    /* Check if this is the second pass */
                    if (loop == 2) {
                        /* Check if a table definition exists in the import file */
                        if (tableDefn.getName() != null) {
                            /* Add the table's definition to the list */
                            tableDefinitions.add(tableDefn);
                        }

                        /* Check if only the data from the first table is to be read */
                        if (importType == ImportType.FIRST_DATA_ONLY) {
                            /* Stop reading table definitions */
                            break;
                        }
                    }
                }

                /* Check if this is the first pass */
                if (loop == 1) {
                    /* Check if all definitions are to be loaded */
                    if (importType == ImportType.IMPORT_ALL) {
                        /* Add the reserved message ID if it's new */
                        rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);

                        /* Build the imported project-level and group data fields, if any */
                        buildProjectAndGroupDataFields(fieldHandler, projectDefn.getDataFields());
                    }
                }
            }
        } finally {
            try {
                /* Check that the buffered reader exists */
                if (br != null) {
                    /* Close the file */
                    br.close();
                }
            } catch (IOException ioe) {
                /* Inform the user that the file cannot be closed */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Cannot close import file '</b>" + importFile.getAbsolutePath() + "<b>'",
                        "File Warning", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Export the project tables in CSV format to the specified file
     *
     * @param exportFile              reference to the user-specified output file
     *
     * @param tableNames              array of table names to export
     *
     * @param includeBuildInformation true to include the CCDD version, project,
     *                                host, and user information
     *
     * @param replaceMacros           true to replace any embedded macros with their
     *                                corresponding values
     *
     * @param includeReservedMsgIDs   true to include the contents of the reserved
     *                                message ID table in the export file
     *
     * @param includeProjectFields    true to include the project-level data field
     *                                definitions in the export file
     *
     * @param includeVariablePaths    true to include the variable path for each
     *                                variable in a structure table, both in
     *                                application format and using the user-defined
     *                                separator characters
     *
     * @param variableHandler         variable handler class reference; null if
     *                                includeVariablePaths is false
     *
     * @param separators              string array containing the variable path
     *                                separator character(s), show/hide data types
     *                                flag ('true' or 'false'), and data
     *                                type/variable name separator character(s);
     *                                null if includeVariablePaths is false
     *
     * @param extraInfo               unused
     *
     * @throws CCDDException If a file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportTables(FileEnvVar exportFile, String[] tableNames, boolean includeBuildInformation,
            boolean replaceMacros, boolean includeReservedMsgIDs, boolean includeProjectFields,
            boolean includeVariablePaths, CcddVariableHandler variableHandler, String[] separators, String outputType,
            Object... extraInfo) throws CCDDException, Exception {
        /* Init local variables */
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        try {
            List<String> referencedTableTypes = new ArrayList<String>();
            List<String[]> variablePaths = new ArrayList<String[]>();
           /* Output the table data to the selected file. Multiple writers are needed in case
            * tables are appended to an existing file 
            */
           fw = new FileWriter(exportFile, false);
           bw = new BufferedWriter(fw);
           pw = new PrintWriter(bw);

            /* Check if all variable paths are to be exported. This is only possible if no tables
             * are specified; otherwise only those variables in the table are exported
             */
            if (includeVariablePaths && tableNames.length == 0) {
                /* Step through each structure and variable name */
                for (String variablePath : variableHandler.getAllVariableNames()) {
                    /* Add the path, in both application and user-defined formats, to the list to be output */
                    variablePaths.add(new String[] { variablePath, variableHandler.getFullVariableName(variablePath,
                            separators[0], Boolean.parseBoolean(separators[1]), separators[2]) });
                }
            }

            /* Step through each table */
            for (String tblName : tableNames) {
                /* Get the information from the database for the specified table */
                TableInformation tableInfo = dbTable.loadTableData(tblName, true, false, parent);

                /* Check if the table's data successfully loaded */
                if (!tableInfo.isErrorFlag()) {
                    /* Get the table type definition based on the type name */
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                    /* Check if this table type is not already output */
                    if (!referencedTableTypes.contains(tableInfo.getType())) {
                        /* Add the table type to the list of those referenced */
                        referencedTableTypes.add(tableInfo.getType());
                    }

                    /* Get the visible column names based on the table's type */
                    String[] columnNames = typeDefn.getColumnNamesVisible();

                    /* Check if the flag is set that indicates macros should be replaced */
                    if (replaceMacros) {
                        /* Replace all macro names with their corresponding values */
                        tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));
                    }

                    /* Output the table path (if applicable) and name, table type, and system path (if provided) */
                    pw.printf("\n" + CSVTags.NAME_TYPE.getTag() + "\n%s\n",
                            CcddUtilities.addEmbeddedQuotesAndCommas(tableInfo.getTablePath(), tableInfo.getType(),
                                    fieldHandler.getFieldValue(tblName, DefaultInputType.SYSTEM_PATH)));

                    /* Check if the table has a description */
                    if (!tableInfo.getDescription().isEmpty()) {
                        /* Output the table description tag and description */
                        pw.printf(CSVTags.DESCRIPTION.getTag() + "\n%s\n",
                                CcddUtilities.addEmbeddedQuotes(tableInfo.getDescription()));
                    }

                    /* Output the column data tag and column names */
                    pw.printf(CSVTags.COLUMN_DATA.getTag() + "\n%s\n",
                            CcddUtilities.addEmbeddedQuotesAndCommas(columnNames));

                    /* Step through each row in the table */
                    for (int row = 0; row < tableInfo.getData().length; row++) {
                        /* Output the table row data, skipping the hidden columns */
                        pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(Arrays.copyOfRange(
                                CcddUtilities.convertObjectToString(tableInfo.getData()[row]),
                                NUM_HIDDEN_COLUMNS, tableInfo.getData()[row].length)));

                        /* Step through each column in the row */
                        for (int column = 0; column < columnNames.length; column++) {
                            List<Integer> dataTypeColumns = new ArrayList<Integer>();

                            /* Get the column indices for all columns that can contain a primitive data type */
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIM_AND_STRUCT));
                            dataTypeColumns.addAll(typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIMITIVE));

                            /* Check if variable paths are to be output and if this table represents a structure */
                            if (includeVariablePaths && typeDefn.isStructure()) {
                                /* Get the variable path */
                                String variablePath = tableInfo.getTablePath() + "," + tableInfo.getData()[row][typeDefn
                                        .getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)] + "." +
                                        tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)];

                                /* Add the path, in both application and user-defined formats, to the list to be output */
                                variablePaths.add(new String[] { variablePath, variableHandler.getFullVariableName(variablePath,
                                        separators[0], Boolean.parseBoolean(separators[1]), separators[2]) });
                            }
                        }
                    }

                    /* Get the table's data field information */
                    List<FieldInformation> fieldInformation = tableInfo.getFieldInformation();

                    /* Check if the table contains any data fields */
                    if (!fieldInformation.isEmpty()) {
                        /* Output the data field marker */
                        pw.printf(CSVTags.DATA_FIELD.getTag() + "\n");

                        /* Step through each data field */
                        for (FieldInformation fieldInfo : fieldInformation) {
                            /* Output the field information */
                            pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                    fieldInfo.getDescription(), Integer.toString(fieldInfo.getSize()),
                                    fieldInfo.getInputType().getInputName(), Boolean.toString(fieldInfo.isRequired()),
                                    fieldInfo.getApplicabilityType().getApplicabilityName(), fieldInfo.getValue()));
                        }
                    }
                }
            }

            /* Check if the user elected to store the reserved message IDs and if there are any
             * reserved message IDs defined
             */
            if (includeReservedMsgIDs && !rsvMsgIDHandler.getReservedMsgIDData().isEmpty()) {
                /* Output the reserved message ID marker */
                pw.printf("\n" + CSVTags.RESERVED_MSG_IDS.getTag() + "\n");

                /* Step through each reserved message ID */
                for (String[] reservedMsgID : rsvMsgIDHandler.getReservedMsgIDData()) {
                    /* Output the reserved message ID definition */
                    pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(
                            reservedMsgID[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                            reservedMsgID[ReservedMsgIDsColumn.DESCRIPTION.ordinal()]));
                }
            }

            /* Check if the user elected to store the project-level data fields */
            if (includeProjectFields) {
                /* Build the data field information for the project */
                List<FieldInformation> fieldInformation = fieldHandler
                        .getFieldInformationByOwner(CcddFieldHandler.getFieldProjectName());

                /* Check if the project contains any data fields */
                if (!fieldInformation.isEmpty()) {
                    /* Output the project data field marker */
                    pw.printf("\n" + CSVTags.PROJECT_DATA_FIELD.getTag() + "\n");

                    /* Step through each data field */
                    for (FieldInformation fieldInfo : fieldInformation) {
                        /* Output the field information */
                        pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                fieldInfo.getDescription(), Integer.toString(fieldInfo.getSize()),
                                fieldInfo.getInputType().getInputName(), Boolean.toString(fieldInfo.isRequired()),
                                fieldInfo.getApplicabilityType().getApplicabilityName(), fieldInfo.getValue()));
                    }
                }
            }

            /* Check if variable paths are to be output and that any exist */
            if (includeVariablePaths && !variablePaths.isEmpty()) {
                /* Output the variable path marker */
                pw.printf("\n" + CSVTags.VARIABLE_PATHS.getTag() + "\n");

                /* Step through each variable path */
                for (String[] variablePath : variablePaths) {
                    /* Output the variable path in application and user-defined formats */
                    pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(variablePath[0], variablePath[1]));
                }
            }
            
            /* Add one last new line character to mark the end of this section of export data 
             * if exporting all data to a single file
             */
            if ((outputType.contentEquals("Single")) && (tableNames.length > 1)) {
                pw.printf("\n");
            }
        } catch (IOException ioe) {
            throw new CCDDException(ioe.getMessage());
        } finally {
            /* Check if the PrintWriter was opened */
            if (pw != null) {
                /* Close the file */
                pw.close();
            }

            try {
                /* Check if the BufferedWriter was opened */
                if (bw != null) {
                    /* Close the file */
                    bw.close();
                }

                /* Check if the FileWriter was opened */
                if (fw != null) {
                    /* Close the file */
                    fw.close();
                }
            } catch (IOException ioe) {
                /* Inform the user that the data file cannot be closed */
                new CcddDialogHandler().showMessageDialog(parent, "<html><b>Cannot close export file '</b>"
                    + exportFile.getAbsolutePath() + "<b>'","File Warning", JOptionPane.WARNING_MESSAGE,
                    DialogOption.OK_OPTION);
            }
        }
    }
    
    /**********************************************************************************************
     * Export script association data, group data, macro data, telemetry scheduler
     * data or application scheduler data to the specified folder
     *
     * @param dataType   the data type that is about to be exported
     * 
     * @param exportFile reference to the user-specified output file
     * 
     * @param outputType String representing rather the output is going to a single
     *                   file or multiple files. Should be "Single" or "Multiple"
     * 
     * @throws CCDDException If a file I/O or parsing error occurs
     * 
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void exportInternalCCDDData(boolean[] includes, CcddConstants.exportDataTypes[] dataTypes,
            FileEnvVar exportFile, String outputType) throws CCDDException, Exception {
        /* Initialize local variables */
        List<String> referencedInputTypes = new ArrayList<String>();
        List<String> referencedMacros = new ArrayList<String>();
        int counter = 0;
        boolean fwCreated = false;
        /* A JSON handler is used to retrieve all the required data for simplicity sake. The JSON strings that this handler
         * returns will be converted to a usable CSV format.
         */
        CcddJSONHandler JSONHandler = new CcddJSONHandler(ccddMain, new CcddGroupHandler(ccddMain, null, parent), parent);
        FileEnvVar FinalExportFile = null;
        
        for (CcddConstants.exportDataTypes dataType: dataTypes) {
            if (includes[counter] == true) {
                String FinalOutput = "";
        
                try {
                    /* Are we exporting this database to multiple files or a single file */
                    if (outputType == "Multiple") {
                        /* Multiple files */
                        switch (dataType) {
                            case GROUPS:
                                FinalExportFile = new FileEnvVar(exportFile + "/_group_info.csv");
                                break;
            
                            case MACROS:
                                FinalExportFile = new FileEnvVar(exportFile + "/_macros.csv");
                                break;
            
                            case ASSOCIATIONS:
                                FinalExportFile = new FileEnvVar(exportFile + "/_script_associations.csv");
                                break;
            
                            case TELEMSCHEDULER:
                                FinalExportFile = new FileEnvVar(exportFile + "/_tlm_scheduler.csv");
                                break;
            
                            case APPSCHEDULER:
                                FinalExportFile = new FileEnvVar(exportFile + "/_app_scheduler.csv");
                                break;
                        }
                    } else {
                        if (!fwCreated) {
                            /* Single file */
                            FinalExportFile = new FileEnvVar(exportFile.getPath());
                            fwCreated = true;
                        }
                    }
                
                    OrderedJSONObject outputJO = new OrderedJSONObject();
                    String CSVData = "";
                    
                    /* TODO: Some of the parsing below is terribly complicated. This is because JSON functions are being
                     * used to gather some of the required data, which then has to be converted to CSV format. Created an
                     * issue to come back and rework this later after more pressing matters have been addressed.
                     */
                    /* Gather the required data */
                    switch (dataType) {
                        case GROUPS:
                            String IsApplication = "";
                            /* Get the group information */
                            String groupInfo = JSONHandler.getGroupInformation("", false, referencedInputTypes);
            
                            /* Check if any groups exist */
                            if (groupInfo != null) {
                                /* Add the group information, if any, to the output */
                                JSONParser parser = new JSONParser();
                                outputJO.put(JSONTags.GROUP.getTag(), parser.parse(groupInfo));
                            }
                            
                            CSVData = outputJO.toString();
                            /* TODO: Maybe make this string manipulation a function */
                            String[] Groups = CSVData.replace("}]}", "").split("\\{\"Group Description\":");
                            
                            for (int index = 1; index < Groups.length; index++) {
                                FinalOutput += "\n" + CSVTags.GROUP.getTag() + "\n";
                                Groups[index] = Groups[index].replace("},", "");
                                try {
                                    IsApplication = Groups[index].split("}],")[1].split(":")[1];
                                    Groups[index] = Groups[index].split("}],")[0];
                                } catch (Exception e) {
                                    IsApplication = Groups[index].split("Group Data Field\":\\[],")[1].split(":")[1];
                                    Groups[index] = Groups[index].split("Group Data Field\":\\[],")[0];
                                }
                                String[] Temp = Groups[index].split(":\\[");
                                
                                /* Parse the table names in this group and the group name */
                                String[] Names = Temp[1].split("],\"Group Name");
                                /* Add the Group Name */
                                FinalOutput += Names[1].split(",")[0].split(":")[1] + ",";
                                /* Add the Description */
                                FinalOutput += Temp[0].split(",")[0] + ",";
                                /* Add the IsApplication bool */
                                FinalOutput += IsApplication;
                                
                                if (Temp.length >= 2) {
                                    /* Add the Table Names that are in this Group */
                                    if (!Names[0].isEmpty()) {
                                        FinalOutput += "," + Names[0].replace("\",\"", ";") +  "\n";
                                    } else {
                                        FinalOutput += "\n";
                                    }
                                    
                                    if (Temp.length == 3) {
                                        FinalOutput += CSVTags.GROUP_DATA_FIELD.getTag() + "\n";
                                    
                                        String[] Rows = Temp[2].split("\\{");
                                        
                                        for (int index2 = 1; index2 < Rows.length; index2++) {
                                            Rows[index2].replace(",\",", "\",");
                                            /* Order the entries for easier importing */
                                            String[] Columns = Rows[index2].split(",\"");
                                            /* TODO: make this more robust */
                                            /* Add the Field Names */
                                            FinalOutput += Columns[4].split(":")[1] + ",";
                                            /* Add the Description */
                                            FinalOutput += Columns[1].split(":")[1].replace("\\", "") + ",";
                                            /* Add the Width */
                                            FinalOutput += "\"" + Columns[6].split(":")[1] + "\",";
                                            /* Add the Input Type */
                                            FinalOutput += Columns[0].split(":")[1] + ",";
                                            /* Add the Required bool */
                                            FinalOutput += "\"" + Columns[2].split(":")[1] + "\",";
                                            /* Add Applicability */
                                            FinalOutput += Columns[5].split(":")[1] + ",";
                                            /* Add the Value */
                                            FinalOutput += Columns[3].split(":")[1] + "\n";
                                        }
                                    }
                                }
                            }
                            break;
            
                        case MACROS:
                            /* Get the macros information */
                            referencedMacros.addAll(macroHandler.getMacroNames());
                            
                            /* Check if any macros are referenced (or all are included) */
                            if (!referencedMacros.isEmpty()) {
                                /* Output the macro marker */
                                FinalOutput += "\n" + CSVTags.MACRO.getTag() + "\n";
        
                                /* Step through each macro */
                                for (String[] macro : macroHandler.getMacroData()) {
                                    /* Check if all macros are to be included or if the macro is referenced in the table */
                                    if (referencedMacros.contains(macro[MacrosColumn.MACRO_NAME.ordinal()])) {
                                        /* Output the macro definition */
                                        FinalOutput += CcddUtilities.addEmbeddedQuotesAndCommas(
                                                macro[MacrosColumn.MACRO_NAME.ordinal()],
                                                macro[MacrosColumn.VALUE.ordinal()]);
                                        FinalOutput = FinalOutput.replace("\"\"","\"\n\"");
                                        FinalOutput += "\n";
                                    }
                                }
                            }
                            break;
            
                        case ASSOCIATIONS:
                            /* Get the Script association data */
                            JSONHandler.getScriptAssociations(outputJO);
                            CSVData = outputJO.toString();
                            
                            FinalOutput += "\n" + CSVTags.SCRIPT_ASSOCIATION.getTag() + "\n";
                            CSVData = CSVData.replace("}]}", "").replace("{\"Script Association\":[", "");
                            CSVData = CSVData.replace("},", "").replace("\\", "");
                            String[] Entries = CSVData.split("\\{");
                            
                            for (int index = 1; index < Entries.length; index++) {
                                String[] Columns = Entries[index].split(",");
                                for (int index2 = 0; index2 < Columns.length; index2++) {
                                    String[] splitColumns = Columns[index2].split("\":");
                                    String valueToAdd = "";
                                    /* Make sure that the values exist */
                                    if(splitColumns.length > 1)
                                        valueToAdd = splitColumns[1];

                                    if (index2 != Columns.length -1) {
                                        FinalOutput += valueToAdd + ",";
                                    } else {
                                        FinalOutput += valueToAdd + "\n";
                                    }
                                }
                            }
                            break;
            
                        case TELEMSCHEDULER:
                            /* Get the telemetry scheduler data */
                            JSONHandler.getTlmSchedulerData(outputJO);
                            CSVData = outputJO.toString();
                            CSVData = CSVData.replace("{\"Telemetry Scheduler Comments\":[{", "");
                            CSVData = CSVData.replace("}]}", "");
                            
                            /* Separate the data into non-rate and rate information */
                            String[] data = CSVData.split("Rate Information");
                            String nonRateData = data[0].substring(0, data[0].length()-2);
                            String rateData = data[1];
                            
                            String[] telemData = nonRateData.split(",");
                            FinalOutput += "\n" + CSVTags.TELEM_SCHEDULER.getTag() + "\n";
                            for (int index = 0; index < telemData.length; index++) {
                                String[] splitTelem = telemData[index].split(":");
                                String valueToAdd = "";
                                /* Make sure that the values exist */
                                if(splitTelem.length > 1)
                                    valueToAdd = splitTelem[1];
                                
                                if (index != telemData.length -1) {
                                    FinalOutput += valueToAdd + ",";
                                } else {
                                    FinalOutput += valueToAdd;
                                }
                            }
                            
                            rateData = rateData.replace(":[{", "").replace("]}", "");
                            String[] rateInfoData = rateData.split(",");
                            
                            FinalOutput += "\n" + CSVTags.RATE_INFO.getTag() + "\n";
                            for (int index = 0; index < rateInfoData.length; index++) {
                                String[] splitTelem = rateInfoData[index].split(":");
                                String valueToAdd = "";
                                /* Make sure that the values exist */
                                if(splitTelem.length > 1)
                                    valueToAdd = splitTelem[1];
                                
                                if (index != rateInfoData.length -1) {
                                    FinalOutput += valueToAdd + ",";
                                } 
                                else {
                                    FinalOutput += valueToAdd + "\n";
                                }
                            }
                            
                            break;
            
                        case APPSCHEDULER:
                            /* Get the application scheduler data */
                            JSONHandler.getAppSchedulerData(outputJO);
                            CSVData = outputJO.toString();
                            CSVData = CSVData.replace("{\"Application Scheduler Comment\":[{", "");
                            CSVData = CSVData.replace("}]}", "");
                            String[] AppData = CSVData.split(",");
                            FinalOutput += "\n" + CSVTags.APP_SCHEDULER.getTag() + "\n";
                            for (int index = 0; index < AppData.length; index++) {
                                String[] splitAppData = AppData[index].split(":");
                                String valueToAdd = "";
                                /* Make sure that the values exist */
                                if(splitAppData.length > 1)
                                    valueToAdd = splitAppData[1];
                                
                                if (index != AppData.length -1) {
                                    FinalOutput += valueToAdd + ",";
                                } else {
                                    FinalOutput += valueToAdd + "\n";
                                }
                            }
                            break;
                    }
        
                    /* If there is any data than write it to the file */
                    if (!FinalOutput.equals("")) {
                        /* Add one last new line character to mark the end of this section of export data 
                         * if exporting all data to a single file
                         */
                        if (outputType.contentEquals("Single")) {
                            FinalOutput += "\n";
                        }
                        FileUtils.writeStringToFile(FinalExportFile, FinalOutput, (String) null, true);
                    }
                } catch (Exception e) {
                    throw new CCDDException(e.getMessage());
                    /* TODO: Add more code to handle the exception here */
                }
            }
            counter++;
        }
    }
    
    /**********************************************************************************************
     * Export table type definitions to the specified folder
     * 
     * @param exportFile        reference to the user-specified output file
     * 
     * @param includeTableTypes Boolean representing if the table types should be
     *                          included
     * 
     * @param includeInputTypes Boolean representing if the input types should be
     *                          included
     * 
     * @param includeDataTypes  Boolean representing if the data types should be
     *                          included
     * 
     * @param outputType        String representing rather the output is going to a
     *                          single file or multiple files. Should be "Single" or
     *                          "Multiple"
     * 
     * @throws CCDDException If a file I/O or parsing error occurs
     * 
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public void exportTableInfoDefinitions(FileEnvVar exportFile, boolean includeTableTypes,
            boolean includeInputTypes, boolean includeDataTypes, String outputType) throws CCDDException, Exception {
        /* Init local variables */
        List<String> referencedTableTypes = new ArrayList<String>();
        List<String> referencedInputTypes = new ArrayList<String>();
        List<String> referencedDataTypes = new ArrayList<String>();
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        FileEnvVar FinalExportFile = null;

        try {
            /* Determine the output type so that the correct naming convention can be used */
            if (outputType == "Multiple") {
                FinalExportFile = new FileEnvVar(exportFile + "/_table_Info.csv");
            } else {
                FinalExportFile = new FileEnvVar(exportFile.getPath());
            }
            
            /* Output the table data to the selected file. Multiple writers are needed in case
             * tables are appended to an existing file
             */
            fw = new FileWriter(FinalExportFile, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);

            /* Determine what data is to be included */
            if (includeTableTypes == true) {
                referencedInputTypes.addAll(Arrays.asList(inputTypeHandler.getNames(true)));
            }
            if (includeInputTypes == true) {
                referencedTableTypes.addAll(Arrays.asList(tableTypeHandler.getTableTypeNames()));
            }
            if (includeDataTypes == true) {
                referencedDataTypes.addAll(dataTypeHandler.getDataTypeNames());
            }
            
            /****************** EXPORT THE INPUT TYPES ******************/
            if (!referencedInputTypes.isEmpty()) {
                /* Output the input type marker */
                pw.printf("\n" + CSVTags.INPUT_TYPE.getTag() + "\n");

                /* Step through each referenced input type */
                for (String inputTypeName : referencedInputTypes) {
                    /* Get the input type definition */
                    InputType inputType = inputTypeHandler.getInputTypeByName(inputTypeName);

                    /* Output the input type definition */
                    pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(inputType.getInputName(),
                            inputType.getInputDescription(), inputType.getInputMatch(),
                            InputType.convertItemListToString(inputType.getInputItems()),
                            inputType.getInputFormat().getFormatName()));
                }
            }
            
            /****************** EXPORT THE TABLE TYPES ******************/
            for (String tableType : referencedTableTypes) {
                /* Get the table type definition based on the type name */
                TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(tableType);

                /* Output the table type tag, and the type name and description */
                pw.printf("\n" + CSVTags.TABLE_TYPE.getTag() + "\n%s\n", CcddUtilities
                        .addEmbeddedQuotesAndCommas(tableTypeDefn.getName(), tableTypeDefn.getDescription(), tableTypeDefn.representsCommandArg()));

                /* Step through each column defined for the table type, skipping the primary
                 * key and row index columns
                 */
                for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++) {
                    /* Output the column definition */
                    pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(tableTypeDefn.getColumnNamesUser()[column],
                            tableTypeDefn.getColumnToolTips()[column],
                            tableTypeDefn.getInputTypes()[column].getInputName(),
                            tableTypeDefn.isRowValueUnique()[column].toString(),
                            tableTypeDefn.isRequired()[column].toString(),
                            tableTypeDefn.isStructureAllowed()[column].toString(),
                            tableTypeDefn.isPointerAllowed()[column].toString()));
                }

                /* Build the data field information for this table type */
                List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationByOwner(
                        CcddFieldHandler.getFieldTypeName(tableType));

                /* Check if the table type contains any data fields */
                if (!fieldInformation.isEmpty()) {
                    /* Output the data field marker */
                    pw.printf(CSVTags.TABLE_TYPE_DATA_FIELD.getTag() + "\n");

                    /****************** EXPORT THE TABLE TYPE DATA FIELD ******************/
                    for (FieldInformation fieldInfo : fieldInformation) {
                        /* Output the field information */
                        pw.printf("%s\n", CcddUtilities.addEmbeddedQuotesAndCommas(fieldInfo.getFieldName(),
                                fieldInfo.getDescription(), Integer.toString(fieldInfo.getSize()),
                                fieldInfo.getInputType().getInputName(), Boolean.toString(fieldInfo.isRequired()),
                                fieldInfo.getApplicabilityType().getApplicabilityName(), fieldInfo.getValue()));
                    }
                }
            }
            
            /****************** EXPORT THE DATA TYPES ******************/
            if (!referencedDataTypes.isEmpty()) {
                /* Output the data type marker */
                pw.printf("\n" + CSVTags.DATA_TYPE.getTag() + "\n");

                /* Step through each data type */
                for (String[] dataType : dataTypeHandler.getDataTypeData()) {
                    /* Check if the data type is referenced in the table */
                    if (referencedDataTypes.contains(CcddDataTypeHandler.getDataTypeName(dataType))) {
                        /* Output the data type definition */
                        pw.printf("%s\n",
                                CcddUtilities.addEmbeddedQuotesAndCommas(dataType[DataTypesColumn.USER_NAME.ordinal()],
                                        dataType[DataTypesColumn.C_NAME.ordinal()],
                                        dataType[DataTypesColumn.SIZE.ordinal()],
                                        dataType[DataTypesColumn.BASE_TYPE.ordinal()]));
                    }
                }
            }
            
            /* Add one last new line character to mark the end of this section of export data 
             * if exporting all data to a single file
             */
            if (outputType.contentEquals("Single")) {
                pw.printf("\n");
            }
        } catch (Exception e) {
            throw new CCDDException(e.getMessage());
            /* TODO: Add more code to handle the exception here */
        } finally {
            /* Check if the PrintWriter was opened */
            if (pw != null) {
                /* Close the file */
                pw.close();
            }

            try {
                /* Check if the BufferedWriter was opened */
                if (bw != null) {
                    /* Close the file */
                    bw.close();
                }

                /* Check if the FileWriter was opened */
                if (fw != null) {
                    /* Close the file */
                    fw.close();
                }
            } catch (IOException ioe) {
                /* Inform the user that the data file cannot be closed */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Cannot close export file '</b>" + exportFile.getAbsolutePath() + "<b>'",
                        "File Warning", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
            }
        }
    }
}

