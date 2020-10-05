/**
 * CFS Command and Data Dictionary project database patch handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.STRUCT_CMD_ARG_REF;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.TYPE_ENUM;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddInputTypeHandler.InputTypeReference;
import CCDD.CcddInputTypeHandler.ReferenceCheckResults;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary project database patch handler class
 *************************************************************************************************/
public class CcddPatchHandler {
    private HashMap<String, PatchUtility> patchSet = new HashMap<String, PatchUtility>();
    private final CcddMain ccddMain;
    private final String PATCH_06012019 = "#06012019";
    private final String PATCH_11052018 = "#11052018";
    private final String PATCH_08292018 = "#08292018";
    private final String PATCH_07242018 = "#07242018";
    private final String PATCH_06212018 = "#06212018";

    // Patch 11052018 specific variables:
    // Flag that indicates that part 1 completed successfully, and part 2 should be performed
    private boolean patch11052018Continue;

    // Number of data fields that were renamed to prevent a duplicate with an inherited field
    private int patch11052018RenamedFields;

    /**********************************************************************************************
     * CFS Command and Data Dictionary project database patch handler class
     * constructor. The patch handler is used to integrate application changes that
     * require alteration of the project database schema. The alterations are meant
     * to be transparent to the user; however, once patched older versions of the
     * application are no longer guaranteed to function properly and may have
     * detrimental effects
     *
     * @param ccddMain main class
     *********************************************************************************************/
    CcddPatchHandler(CcddMain ccddMain) {
        this.ccddMain = ccddMain;
        // Add each of the patches here
        patchSet.clear();
        String patch_06012019_dialogMsg = "<html><b>Apply patch to update the command " + "tables?<br><br></b>"
                + "The command table types are altered by "
                + "removing any argument columns and adding "
                + "a command argument structure reference " + "column. Every command table is modified "
                + "as follows: Remove the argument columns " + "and add a command argument structure "
                + "reference column. For each individual "
                + "command an argument structure is created; "
                + "within this structure a variable is added "
                + "for each of the command's arguments. An "
                + "argument reference structure table type " + "definition is created from which an "
                + "argument reference structure is created "
                + "that has references for each of the newly "
                + "create argument structures (making these " + "argument structures children of the "
                + "argument reference structure). The updated " + "command table's argument structure "
                + "reference column is populated with a " + "reference to the argument reference "
                + "structure. <br><b><i>Older versions of " + "CCDD will be incompatible with this "
                + "project database after applying the patch";

        patchSet.put(PATCH_06012019,
                new PatchUtility(ccddMain, PATCH_06012019, patch_06012019_dialogMsg));
        
        String patch_11052018_dialogMsg = 
                "<html><b>Apply patch to update the data " + "fields table?<br><br></b>"
                + "Adds a column in the data fields " + "table to store field inheritance "
                + "status. A field is inherited if " + "defined in the table's type "
                + "definition. Fields are renamed to " + "prevent an instance of a fields "
                + "matching an inherited field when the " + "input types differ. Missing an "
                + "inherited fields are added to " + "tables<br><b><i>Older versions of "
                + "CCDD will be incompatible with this " + "project database after applying the patch";
        patchSet.put(this.PATCH_11052018, 
                new PatchUtility(ccddMain, this.PATCH_11052018, patch_11052018_dialogMsg));


        String patch_08292018_dialogMsg = 
                "<html><b>Apply patch to update the table type, data field, and "
                        + "application scheduler tables message " + "name and IDs?<br><br></b>Changes "
                        + "message ID name input type to 'Text' " + "and the message ID input type to "
                        + "'Message name & ID'. Combines the " + "application scheduler wake-up "
                        + "message name and ID pairs into a " + "single<br><b><i>Older versions of "
                        + "CCDD are incompatible with this " + "project database after applying the " + "patch";

        patchSet.put(this.PATCH_08292018,
                new PatchUtility(ccddMain, this.PATCH_08292018, patch_08292018_dialogMsg));

        String patch_07242018_dialogMsg= 
                "<html><b>Apply patch to update the database to support user access "
                        + "levels?<br><br></b>Changes the database " + "to support user access levels. <b>The "
                        + "current user is set as the creator/" + "administrator of the database!</b> "
                        + "Older versions of CCDD will remain " + "compatible with this project database "
                        + "after applying the patch";
        patchSet.put(this.PATCH_07242018,
                new PatchUtility(ccddMain, this.PATCH_07242018, patch_07242018_dialogMsg));

        String patch_06212018_dialogMsg = 
                "<html><b>Apply patch to update padding variable names?<br><br></b>"
                        + "Changes the padding variable format from "
                        + "'__pad#' to 'pad#__'.<br><b><i>If patch "
                        + "not applied the affected variables will " + "not be recognized as padding";
        patchSet.put(PATCH_06212018,
                new PatchUtility(ccddMain, PATCH_06212018, patch_06212018_dialogMsg));
    }

    /**********************************************************************************************
     * Apply patches based on the input flag
     *
     * @param isBeforeHandlerInit true if the patch must be implemented prior to
     *                            initializing the handler classes; false if the
     *                            patch must be implemented after initializing the
     *                            handler classes
     *
     * @throws CCDDException If the user elects to not install the patch or an error
     *                       occurs while applying the patch
     *********************************************************************************************/
    protected void applyPatches(boolean isBeforeHandlerInit) throws CCDDException {
        ///////////////////////////////////////////////////////////////////////////////////////////
        // *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE ***
        // Patches are removed after an appropriate amount of time has been given for the patch to
        // be applied. For now the code is left in place, though commented out, in the event the
        // patch is required for an older database
        ///////////////////////////////////////////////////////////////////////////////////////////

        // Check if only patches that must be performed prior to handler initialization
        // should be implemented
        if (isBeforeHandlerInit) {
            // Patch #11052018: PartA - Add a column, field_inherited, to the fields table that
            // indicates if the field is owned by a table and is inherited from the table's type
            updateFieldsPart1();

            // Patch #07112017: Update the database comment to include the project name with
            // capitalization intact
            // NOTE: This patch is no longer valid due to changes in the database opening sequence
            // where the lock status is set
            // updateDataBaseComment();

            // Patch #01262017: Rename the table types table and alter its content to include the
            // database name with capitalization intact
            // updateTableTypesTable();
        }
        // Only patches that must be performed after handler initialization should be implemented
        else {            
            // Patch #06012019 - Convert command tables to the new command table / command argument
            // structure table format
            updateCommandTables();

            // Patch #11052018: Part B - Add inherited fields to tables that don't already have the
            // field
            updateFieldsPart2();

            // Patch #06212018: Update the padding variable format from '__pad#' to 'pad#__'
            updatePaddingVariables();

            // Patch #07242018: Update the database to support user access levels
            updateUserAccess();

            // Patch #08292018: Change the message ID name input type to 'Text' and the  message ID
            // input type to 'Message name & ID' in the table type and data field tables
            updateMessageNamesAndIDs();

            // Patch #09272017: Update the data fields table applicability column to change
            // "Parents only" to "Roots only"
            // updateFieldApplicability();

            // Patch #07212017: Update the associations table to include a description column and
            // to change the table separator characters in the member_table column
            // updateAssociationsTable();

            // Patch #11132017: Update the associations table to include a name column
            // updateAssociationsTable2();
            
            // Patch #10022020: Add the ENUM table type to the internal table types table
            // and update the size column of the data types internal table to have a type
            // of text rather than int
            UpdateTableTypeAndDataTypeTables();
        }
    }

    /**********************************************************************************************
     * Backup the project database. Store the backup file in the folder specified in
     * the program preferences (or the CCDD start-up folder if no preference is
     * specified). The file name is a combination of the project's database name and
     * the current date/time stamp
     *
     * @param dbControl reference to the database control class
     *
     * @throws CCDDException If the project database cannot be backed up and the
     *                       user elects to not continue
     *********************************************************************************************/
    private void backupDatabase(CcddDbControlHandler dbControl) throws CCDDException {
        // Set the flag if the current user's password is non-blank. Depending on the
        // authentication set-up and operating system, the password may still be required
        // by the pg_dump command even if the authentication method is 'trust'
        boolean isPasswordSet = dbControl.isPasswordNonBlank();

        // Check if no password is set
        if (!isPasswordSet) {
            // Display the password dialog and obtain the password. Note that the user can
            // enter a blank password (which may be valid)
            CcddServerPropertyDialog dialog = new CcddServerPropertyDialog(ccddMain, ServerPropertyDialogType.PASSWORD);

            // Set the flag if the user selected the Okay button in the password dialog
            isPasswordSet = dialog.isPasswordSet();
        }

        // Check if the user's database password is set (either non-blank or explicitly set to blank)
        if (isPasswordSet) {
            // Check if backing up the project database failed
            if (dbControl.backupDatabase(dbControl.getProjectName(),
                    new FileEnvVar((ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath().isEmpty() ? ""
                            : ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath() + File.separator)
                            + dbControl.getDatabaseName() + "_"
                            + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                            + FileExtension.DBU.getExtension()))) {
                // Inform the user that the backup failed and check if the user elects to not
                // continue the patch operation
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                        "<html><b>Unable to back up project database, continue?", "Backup Project",
                        JOptionPane.QUESTION_MESSAGE, DialogOption.OK_CANCEL_OPTION) != OK_BUTTON) {
                    throw new CCDDException("Unable to back up project database");
                }
            }
        }
    }
    
    /**********************************************************************************************
     * The internal data types table is modified so that the 'size' column has a type of 'text'
     * rather than a size of 'int'. The internal table types table it updated with a new table
     * type called 'ENUM'
     *
     * @throws CCDDException If the user elects to not install the patch or an error
     *                       occurs while applying the patch
     *********************************************************************************************/
    private void UpdateTableTypeAndDataTypeTables() {
        CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
        CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

        // Get all exisiting type definitions
        List<TypeDefinition> typeDefinitions = tableTypeHandler.getTypeDefinitions();
        boolean enumFound = false;

        // Step through each table type definition
        for (TypeDefinition typeDefn : typeDefinitions) {
            if (typeDefn.getName().equals(TYPE_ENUM)) {
                enumFound = true;
            }
        }
        
        // If an enum table type was not found then add it
        if (!enumFound) {
            // Add the table type definition, 0 added as it is not a command argument
            tableTypeHandler.createReplaceTypeDefinition(TYPE_ENUM, "0ENUM table",
                    DefaultColumn.getDefaultColumnDefinitions(TYPE_ENUM, false));
            
            // Add the new table type to the project database
            dbTable.modifyTableType(TYPE_ENUM, null, OverwriteFieldValueType.NONE,
                    new ArrayList<String[]>(0), new ArrayList<String[]>(0), new ArrayList<String[]>(0), false, null,
                    new ArrayList<TableModification>(0), new ArrayList<TableModification>(0),
                    new ArrayList<TableModification>(0), null, null);
        }
        
        // Build the command to modify the internal data type table
        StringBuilder command = new StringBuilder("ALTER TABLE " + InternalTable.DATA_TYPES.getTableName() +
                " ALTER COLUMN size TYPE text USING size::text;");
        
        // Make the changes to the table(s) in the database
        try {
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
        } catch (Exception e) {
            System.out.println("Failed to update the __data_types table during patch.");
        }
    }

    /**********************************************************************************************
     * The command table types are altered by removing any argument columns and
     * adding a command argument structure reference column. Every command table is
     * modified as follows: Remove the argument columns and add a command argument
     * structure reference column. For each individual command an argument structure
     * is created; within this structure a variable is added for each of the
     * command's arguments. An argument reference structure table type definition is
     * created from which an argument reference structure is created that has
     * references for each of the newly create argument structures (making these
     * argument structures children of the argument reference structure). The
     * updated command table's argument structure reference column is populated with
     * a reference to the argument reference structure. Older versions of CCDD are
     * not compatible with the project database after applying this patch
     *
     * @throws CCDDException If the user elects to not install the patch or an error
     *                       occurs while applying the patch
     *********************************************************************************************/
    /* TODO: This function is 1000 lines long....... refactor if time permits */
    private void updateCommandTables() throws CCDDException {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        try {
            boolean isPatched = false;
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
            CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions()) {
                // Check if the table represents a command
                if (typeDefn.isCommand()) {
                    // Stop searching since the database is already updated
                    isPatched = true;
                    break;
                }
            }
            
            // Check if the patch hasn't been applied
            if (!isPatched) {
                
                if(this.patchSet.get(this.PATCH_06012019).confirmPatchApplication() == false){
                        throw new CCDDException(this.patchSet.get(this.PATCH_06012019).getUserCancelledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Create a save point in case an error occurs while applying the patch
                dbCommand.createSavePoint(ccddMain.getMainFrame());
		
                // Simple table to store temporary information about the database table type. 
                class DbTableType{
                    DbTableType(String name, String basedUpon, boolean isRate){
                        this.name = name;
                        this.basedUpon = basedUpon;
                        this.isRateEnabled = isRate;
                        // The 1 is appended to reflect that the table is a command argument
                        this.description = "1Command argument structure reference table definition";
                    }
                    DbTableType(String name, boolean isRate){
                        this(name,name,isRate);
                    }

                    public String name; // Name of the structure
                    public String basedUpon; // Structure to base upon (copy)
                    public boolean isRateEnabled; // Is the rate enabled for this structure?
                    public String description; // The description
                };
		
		// These pairs contain either a conversion from one type to another or the creation of a new type
                List<DbTableType> structures = new ArrayList<>();
                // Add Structure: Cmd Arg Ref and base it off of the Structure table. Don't include the rate variable
                structures.add(new DbTableType(CcddConstants.STRUCT_CMD_ARG_REF, CcddConstants.TYPE_STRUCTURE, false));
                // Add the Command, do not include the rate
                structures.add(new DbTableType(CcddConstants.TYPE_COMMAND, false));

                for (DbTableType struct : structures) {

                    boolean isNamedCommand = struct.name == CcddConstants.TYPE_COMMAND;
                    TypeDefinition tble = tableTypeHandler.getTypeDefinition(struct.name);
                    boolean isInDbAlready = tble != null;
                    boolean isCommandAlreadyThere = isNamedCommand && isInDbAlready;

                    // Some databases will already have this defined so do not attempt to add it again
                    // Other databases will need the command type to be added or modified
                    if(isCommandAlreadyThere){
                        continue;
                    }

                    // Pull out the column definitions for this named entry
                    Object[][] td = DefaultColumn.getDefaultColumnDefinitions(struct.basedUpon, struct.isRateEnabled);

                    // Remove this structure (it if exists)
                    tableTypeHandler.removeTypeDefinition(struct.name);

                    // Add it (this will ensure that the type is correct and up to date)
                    TypeDefinition cmdArgTableType = tableTypeHandler.addTypeDefinition(
                    struct.name,
                    struct.description,
                    td);

                    StringBuilder cmdRefInpTypeCmd = new StringBuilder("");
                    List<TypeDefinition> newCommandArgTypes = new ArrayList<TypeDefinition>();
                    int cmdArgStructSeq = 1;

                    // Get the references in the table type and data field internal tables that use the
                    // command reference input type. If a command name, code, or argument is changed or
                    // deleted then the tables and fields may require updating
                    ReferenceCheckResults cmdRefChkResults = inputTypeHandler
                            .getInputTypeReferences(DefaultInputType.COMMAND_REFERENCE, ccddMain.getMainFrame());

                    // Get the number of table type definitions before any new ones are added
                    int numTypes = tableTypeHandler.getTypeDefinitions().size();

                    // Step through each existing table type definition
                    for (int typeIndex = 0; typeIndex < numTypes; typeIndex++) {
                        // Get the reference to the existing table type definition
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinitions().get(typeIndex);

                        // Check if this isn't the command argument structure reference table type just added
                        if (!typeDefn.getName().equals(STRUCT_CMD_ARG_REF)) {
                            // Update the type's definition to include the flag that indicates if the
                            // type represents a command argument structure
                            typeDefn.setDescription("0" + typeDefn.getColumnToolTips()[0]);
                        }

                        // Get the column indices for the command name and code
                        int cmdNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
                        int cmdCodeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);

                        // Check if the type represents a command (old format; minus the command
                        // argument structure column)
                        if (cmdNameIndex != -1 && cmdCodeIndex != -1 && !typeDefn.isStructure()) {
                            List<Integer[]> cmdArgGroups = new ArrayList<Integer[]>();
                            List<Integer> commandColumns = new ArrayList<Integer>();
                            List<String> commandTableNames = new ArrayList<String>();
                            List<String> commandTableDescriptions = new ArrayList<String>();
                            List<String> commands = new ArrayList<String>();
                            String command = "";

                            // Read the contents of the command table type
                            ResultSet typesData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM ")
                                    .append(InternalTable.TABLE_TYPES.getTableName()).append(" WHERE ")
                                    .append(TableTypesColumn.TYPE_NAME.getColumnName()).append(" = '")
                                    .append(typeDefn.getName()).append("' ORDER BY ").append(TableTypesColumn.INDEX.getColumnName())
                                    .append(";"), ccddMain.getMainFrame());

                            int colIndex = -1;
                            int cmdIndex = 0;
                            List<Object[]> cmdColDefnData = new ArrayList<Object[]>();
                            boolean keepGoing = true;
                            boolean readNext = true;

                            // Step through each of the query results
                            while (keepGoing) {
                                // Check if the next result entry should be read (if the last entry
                                // read below is an argument name then the current entry applies)
                                if (readNext) {
                                    // Get the next entry
                                    keepGoing = typesData.next();

                                    // Check if this is the last result entry
                                    if (!keepGoing) {
                                        // Stop processing this command table type
                                        break;
                                    }

                                    // Increment the grouped column index
                                    colIndex++;
                                }

                                // Set the flag for the next loop
                                readNext = true;

                                // Check if this is not the primary key or index columns
                                if (typesData.getInt(TableTypesColumn.INDEX.ordinal() + 1) >= NUM_HIDDEN_COLUMNS) {
                                    // Check if the column expects a command argument name
                                    if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1)
                                            .equals("Argument name")) {
                                        List<Integer> argColGroup = new ArrayList<Integer>();

                                        // Change the input type for the argument name to that for a
                                        // variable so that it's recognized
                                        typeDefn.getInputTypesList().set(colIndex, inputTypeHandler
                                                .getInputTypeByDefaultType(DefaultColumn.VARIABLE_NAME.getInputType()));

                                        // Add the argument name column index to the list of argument columns
                                        argColGroup.add(colIndex);

                                        // Get the column input types
                                        InputType[] inputTypes = typeDefn.getInputTypes();

                                        // Step through the remaining columns defined for this table's type
                                        for (colIndex++; colIndex < typeDefn.getColumnCountDatabase(); colIndex++) {
                                            typesData.next();

                                            // Check if the column expects a command argument name
                                            if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1)
                                                    .equals("Argument name")) {
                                                // Change the input type for the argument name to that
                                                // for a variable so that it's recognized
                                                typeDefn.getInputTypesList().set(colIndex,
                                                        inputTypeHandler.getInputTypeByDefaultType(
                                                                DefaultColumn.VARIABLE_NAME.getInputType()));
                                                readNext = false;
                                                break;
                                            }

                                            // Check if the column expects a primitive data type
                                            if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1)
                                                    .equals(DefaultInputType.PRIMITIVE.getInputName())) {
                                                // Change the input type for the primitive data type to
                                                // that for a primitive & structure data type
                                                typeDefn.getInputTypesList().set(colIndex, inputTypeHandler
                                                        .getInputTypeByDefaultType(DefaultColumn.DATA_TYPE.getInputType()));
                                            }
                                            // Check if the column expects a command argument name
                                            else if (typesData.getString(TableTypesColumn.COLUMN_NAME_DB.ordinal() + 1)
                                                    .contains("description")) {
                                                // Change the input type for the argument name to that
                                                // for a variable so that it's recognized
                                                typeDefn.getInputTypesList().set(colIndex,
                                                        inputTypeHandler.getInputTypeByDefaultType(
                                                                DefaultColumn.DESCRIPTION_STRUCT.getInputType()));
                                            } else if (typesData.getString(TableTypesColumn.COLUMN_NAME_DB.ordinal() + 1)
                                                    .contains("units")) {
                                                // Change the input type for the argument name to that
                                                // for a variable so that it's recognized
                                                typeDefn.getInputTypesList().set(colIndex, inputTypeHandler
                                                        .getInputTypeByDefaultType(DefaultColumn.UNITS.getInputType()));
                                            }

                                            // Check that this isn't the command name or command code
                                            // column (these are never part of an argument grouping)
                                            if (!inputTypes[colIndex].equals(inputTypeHandler
                                                    .getInputTypeByDefaultType(DefaultInputType.COMMAND_NAME))
                                                    && !inputTypes[colIndex].equals(inputTypeHandler
                                                            .getInputTypeByDefaultType(DefaultInputType.COMMAND_CODE))) {
                                                // Add the command argument column index to the argument list
                                                argColGroup.add(colIndex);
                                            }
                                            // This column belongs to the command itself and not an argument
                                            else {
                                                cmdColDefnData.add(typeDefn.getData()[cmdIndex]);
                                                commandColumns.add(colIndex);
                                                cmdIndex++;
                                            }
                                        }

                                        // Add the argument column group to the list
                                        cmdArgGroups.add(argColGroup.toArray(new Integer[0]));
                                    }
                                    // This column belongs to the command itself
                                    else {
                                        cmdColDefnData.add(typeDefn.getData()[cmdIndex]);
                                        commandColumns.add(colIndex);
                                        cmdIndex++;
                                    }
                                }
                            }

                            typesData.close();
                            
                            // If it is a new command, these fields may need to be added
                            if(!isCommandAlreadyThere)
                            {
                                boolean isCommandNameDefined = false;
                                for(Object[] v:cmdColDefnData){
                                    if( v[1].equals(DefaultColumn.COMMAND_NAME.getName()))
                                        isCommandNameDefined = true;
                                }
                                // The command name test has failed so add the below three fields
                                if(!isCommandNameDefined){
                                    // Add the new command name structure column definition
                                    cmdColDefnData.add(new Object[] { 0, DefaultColumn.COMMAND_NAME.getName(),
                                            DefaultColumn.COMMAND_NAME.getDescription(),
                                            DefaultColumn.COMMAND_NAME.getInputType().getInputName(),
                                            DefaultColumn.COMMAND_NAME.isRowValueUnique(),
                                            DefaultColumn.COMMAND_NAME.isProtected(),
                                            DefaultColumn.COMMAND_NAME.isStructureAllowed(),
                                            DefaultColumn.COMMAND_NAME.isPointerAllowed() });
                                       
                                    // Add the new command code structure column definition
                                    cmdColDefnData.add(new Object[] { 0, DefaultColumn.COMMAND_CODE.getName(),
                                            DefaultColumn.COMMAND_CODE.getDescription(),
                                            DefaultColumn.COMMAND_CODE.getInputType().getInputName(),
                                            DefaultColumn.COMMAND_CODE.isRowValueUnique(),
                                            DefaultColumn.COMMAND_CODE.isProtected(),
                                            DefaultColumn.COMMAND_CODE.isStructureAllowed(),
                                            DefaultColumn.COMMAND_CODE.isPointerAllowed() });
                                    
                                    // Add the new command description structure column definition
                                    cmdColDefnData.add(new Object[] { 0, DefaultColumn.DESCRIPTION_CMD.getName(),
                                            DefaultColumn.DESCRIPTION_CMD.getDescription(),
                                            DefaultColumn.DESCRIPTION_CMD.getInputType().getInputName(),
                                            DefaultColumn.DESCRIPTION_CMD.isRowValueUnique(),
                                            DefaultColumn.DESCRIPTION_CMD.isProtected(),
                                            DefaultColumn.DESCRIPTION_CMD.isStructureAllowed(),
                                            DefaultColumn.DESCRIPTION_CMD.isPointerAllowed() });
                                }
                            }
                            // Always add this field
                            // Add the new command argument structure column definition
                            cmdColDefnData.add(new Object[] { 0, DefaultColumn.COMMAND_ARGUMENT.getName(),
                                    DefaultColumn.COMMAND_ARGUMENT.getDescription(),
                                    DefaultColumn.COMMAND_ARGUMENT.getInputType().getInputName(),
                                    DefaultColumn.COMMAND_ARGUMENT.isRowValueUnique(),
                                    DefaultColumn.COMMAND_ARGUMENT.isProtected(),
                                    DefaultColumn.COMMAND_ARGUMENT.isStructureAllowed(),
                                    DefaultColumn.COMMAND_ARGUMENT.isPointerAllowed() });

                            // Create the command table type with the new command argument column and without
                            // the individual argument columns. Adjust the name so there is no conflict; the
                            // name is restored when the original table type is replaced by this one
                            TypeDefinition newCmdTypeDefn = tableTypeHandler.createTypeDefinition(typeDefn.getName() + "@",
                                    typeDefn.getColumnToolTips()[0], cmdColDefnData.toArray(new Object[0][0]));

                            List<InputType> defaultInputTypes = new ArrayList<InputType>();

                            // Step through the default columns
                            for (DefaultColumn defCol : DefaultColumn.values()) {
                                // Check if the current column's type is for a structure (excluding the rate column)
                                if (defCol.getTableType().equals(TYPE_STRUCTURE)
                                        && !defCol.getInputType().equals(DefaultInputType.RATE)) {
                                    // Add the column input type to the list
                                    defaultInputTypes
                                            .add(inputTypeHandler.getInputTypeByDefaultType(defCol.getInputType()));
                                }
                            }

                            // Step through each table of this command type
                            for (String commandTableName : dbTable.getAllTablesOfType(typeDefn.getName(), null,
                                    ccddMain.getMainFrame())) {
                                // Load the command table's information
                                TableInformation cmdTableInfo = dbTable.loadTableData(commandTableName,
                                        true, /* Load description? */
                                        false, /* Load columnOrder? */
                                        ccddMain.getMainFrame());

                                // Check if an error occurred loading the command table
                                if (cmdTableInfo.isErrorFlag()) {
                                    throw new CCDDException("Cannot load command table " + commandTableName);
                                }

                                TypeDefinition newCmdArgStructType = null;
                                List<Object[]> typeData = new ArrayList<Object[]>();
                                List<String> argStructRefs = new ArrayList<String>();

                                // Store the command table name and description
                                commandTableNames.add(commandTableName);
                                commandTableDescriptions.add(cmdTableInfo.getDescription());

                                // Check if the table has commands defined
                                if (cmdTableInfo.getData().length != 0) {
                                    List<String[]> argNamesAndNumber = new ArrayList<String[]>();

                                    ///////////////////////////////////////////////////////////////////
                                    // Create the command argument structure reference table used by
                                    // all commands in this command table
                                    ///////////////////////////////////////////////////////////////////
                                    List<Object[]> argRefTableData = new ArrayList<Object[]>();
                                    String cmdArgRefTableName = "CmdArgRef_" + commandTableName;

                                    // Create a command argument structure table based on the newly create
                                    // type. All of the command arguments are stored in this table
                                    if (dbTable.createTable(new String[] { cmdArgRefTableName },
                                            "Command " + commandTableName + " argument structure references",
                                            STRUCT_CMD_ARG_REF, true, ccddMain.getMainFrame())) {
                                        throw new CCDDException("Cannot create command argument structure reference table");
                                    }

                                    // Load the command argument structure table
                                    TableInformation cmdArgTableInfo = dbTable.loadTableData(cmdArgRefTableName, false,
                                            false, ccddMain.getMainFrame());

                                    // Check if an error occurred loading the command argument structure table
                                    if (cmdArgTableInfo.isErrorFlag()) {
                                        throw new CCDDException("Cannot load command argument structure table");
                                    }

                                    ///////////////////////////////////////////////////////////////////
                                    // Create the command argument structure table type used by all
                                    // commands in this command table
                                    ///////////////////////////////////////////////////////////////////

                                    // Add the default structure column definitions to the list (minus the rate column)
                                    typeData.addAll(Arrays.asList(DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE, false)));
                                    int typeDataIndex = typeData.size();

                                    // Step through each command argument column grouping
                                    for (Integer[] cmdArgGroup : cmdArgGroups) {
                                        int numEnum = 0;
                                        int numMin = 0;
                                        int numMax = 0;

                                        // Step through each column in this argument group
                                        for (Integer argCol : cmdArgGroup) {
                                            boolean isAdd = false;
                                            String columnName = null;
                                            String description = null;
                                            boolean isRowValueUnique = false;
                                            boolean isRequired = false;
                                            boolean isStructureAllowed = false;
                                            boolean isPointerAllowed = false;
                                            DefaultColumn defCol = null;

                                            // Get the input type reference to shorten subsequent calls
                                            InputType inputType = typeDefn.getInputTypes()[argCol];

                                            // Check if this is the enumeration input type
                                            if (inputType.getInputName().equals(DefaultInputType.ENUMERATION.getInputName())) {
                                                numEnum++;

                                                // Check if this isn't the first enumeration column
                                                // (the first is a default)
                                                if (numEnum > 1) {
                                                    isAdd = true;
                                                    defCol = DefaultColumn.ENUMERATION;
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                }
                                            }
                                            // Check if this is the minimum input type
                                            else if (inputType.getInputName().equals(DefaultInputType.MINIMUM.getInputName())) {
                                                numMin++;

                                                // Check if this isn't the first minimum column (the first is a default)
                                                if (numMin > 1) {
                                                    isAdd = true;
                                                    defCol = DefaultColumn.MINIMUM;
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                }
                                            }
                                            // Check if this is the maximum input type
                                            else if (inputType.getInputName().equals(DefaultInputType.MAXIMUM.getInputName())) {
                                                numMax++;

                                                // Check if this isn't the first maximum column (the
                                                // first is a default)
                                                if (numMax > 1) {
                                                    isAdd = true;
                                                    defCol = DefaultColumn.MAXIMUM;
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                }
                                            }
                                            // Check if the column type is not a default column
                                            else if (!inputType.getInputName().equals(DefaultInputType.VARIABLE.getInputName())
                                                    && !inputType.getInputName().equals(DefaultInputType.PRIM_AND_STRUCT.getInputName())
                                                    && !inputType.getInputName().equals(DefaultInputType.PRIMITIVE.getInputName())
                                                    && !inputType.getInputName().equals(DefaultInputType.ARRAY_INDEX.getInputName())
                                                    && !inputType.getInputName().equals(DefaultInputType.BIT_LENGTH.getInputName())
                                                    && !typeDefn.getColumnNamesDatabase()[argCol].contains("description")
                                                    && !typeDefn.getColumnNamesDatabase()[argCol].contains("units")) {
                                                isAdd = true;
                                                columnName = typeDefn.getColumnNamesUser()[argCol];
                                                description = typeDefn.getColumnToolTips()[argCol];
                                                isRowValueUnique = typeDefn.isRowValueUnique()[argCol];
                                                isRequired = typeDefn.isRequired()[argCol];
                                                isStructureAllowed = typeDefn.isStructureAllowed()[argCol];
                                                isPointerAllowed = typeDefn.isPointerAllowed()[argCol];

                                                /* If the name is "Arg 1 Access", "Arg 1 Default Value" or
                                                 * "Arg 1 Verification Test Num" it will be changed to "Access",
                                                 * "Default Value" and "Verification Test Num". If it is Arg 2 or above it
                                                 * will not be added to the new structure at all. Each argument is now
                                                 * placed in its own instance of a Structure: Cmd Arg table so you will
                                                 * never need an Arg 2 or above value in any given table.
                                                 */
                                                if (columnName.equals("Arg 1 Access")) {
                                                    columnName = "Access";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;
                                                } else if (columnName.equals("Arg 1 Default Value")) {
                                                    columnName = "Default Value";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;
                                                } else if (columnName.equals("Arg 1 Verification Test Num")) {
                                                    columnName = "Verification Test Num";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;

                                                    /* remove the argument number from the description */
                                                    description = description.substring(0, 17) + description.substring(18);
                                                } else if (columnName.substring(0, 3).equals("Arg")) {
                                                    /* If it is Arg 2 or above it will not be added to the new structure at all */
                                                    if (Integer.parseInt(columnName.substring(4, 5)) > 1) {
                                                        isAdd = false;
                                                    }
                                                }
                                            }

                                            // Check if a column needs to be added
                                            if (isAdd) {
                                                // Check if the column has the same definition as a default column
                                                if (defCol != null) {
                                                    // Check if no column name is set
                                                    if (columnName == null) {
                                                        // Use the default column name
                                                        columnName = defCol.getName();
                                                    }

                                                    // Check if no column description is set
                                                    if (description == null) {
                                                        // Use the default column description
                                                        description = defCol.getDescription();
                                                    }

                                                    // Use the default column flags
                                                    isRowValueUnique = defCol.isRowValueUnique();
                                                    isStructureAllowed = defCol.isStructureAllowed();
                                                    isPointerAllowed = defCol.isPointerAllowed();
                                                }
                                                // The column doesn't have the same definition as a default column
                                                else {
                                                    // Use the column definition from the command table type
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                    isRowValueUnique = typeDefn.isRowValueUnique()[argCol];
                                                    isRequired = typeDefn.isRequired()[argCol];
                                                    isStructureAllowed = typeDefn.isStructureAllowed()[argCol];
                                                    isPointerAllowed = typeDefn.isPointerAllowed()[argCol];
                                                }

                                                // Add the command argument structure column definition
                                                typeData.add(new Object[] { typeDataIndex, columnName, description,
                                                        inputType.getInputName(), isRowValueUnique, isRequired,
                                                        isStructureAllowed, isPointerAllowed });
                                                typeDataIndex++;
                                            }
                                        }
                                    }

                                    ///////////////////////////////////////////////////////////////////
                                    // Determine if a command argument structure table type already
                                    // exists that matches the one for this command. If so, then use it
                                    // for defining the command argument structures; otherwise create a
                                    // new table type and use it
                                    ///////////////////////////////////////////////////////////////////
                                    newCmdArgStructType = null;

                                    for (TypeDefinition cmdArgStructType : newCommandArgTypes) {
                                        // Check if the type definitions have the same number of columns
                                        if (cmdArgStructType.getColumnCountVisible() == typeData.size()) {
                                            boolean isFound = false;

                                            // Step through each column definition in the new command
                                            // argument structure table type
                                            for (Object[] newColDefn : typeData) {
                                                isFound = false;

                                                // Step through each column definition in the existing
                                                // command argument structure table type
                                                for (Object[] colDefn : cmdArgStructType.getData()) {
                                                    // Check if the column definitions match
                                                    if (CcddUtilities.isArraySetsEqual(colDefn, newColDefn)) {
                                                        // Set the flag to indicate a matching column
                                                        // exists and stop searching
                                                        isFound = true;
                                                        break;
                                                    }
                                                }

                                                // Check if no matching column exists
                                                if (!isFound) {
                                                    // This type definition isn't a match; stop searching
                                                    break;
                                                }
                                            }

                                            // Check if the column definitions for this type definition
                                            // match the new type
                                            if (isFound) {
                                                // Store the reference to the existing type definition
                                                // and stop searching
                                                newCmdArgStructType = cmdArgStructType;
                                                break;
                                            }
                                        }
                                    }

                                    // Check if no matching type definition exists
                                    if (newCmdArgStructType == null) {
                                        // Create a new command argument structure table type for this command table
                                        newCmdArgStructType = tableTypeHandler.createReplaceTypeDefinition(
                                                "Structure: Cmd Arg " + cmdArgStructSeq,
                                                "1Command argument structure table definition",
                                                typeData.toArray(new Object[0][0]));
                                        newCommandArgTypes.add(newCmdArgStructType);
                                        cmdArgStructSeq++;
                                    }

                                    int missingNameSeq = 1;

                                    // Step through each command defined in this command table
                                    for (int cmdRow = 0; cmdRow < cmdTableInfo.getData().length; cmdRow++) {
                                        String argNameString = "";

                                        ///////////////////////////////////////////////////////////////
                                        // Create the command argument structure table for this command
                                        ///////////////////////////////////////////////////////////////
                                        Object[] cmdArgRef = new Object[cmdArgTableType.getColumnCountDatabase()];
                                        Arrays.fill(cmdArgRef, "");

                                        // Get the command name
                                        String cmdName = cmdTableInfo.getData()[cmdRow][cmdNameIndex].toString();

                                        // Check if no command name is defined
                                        if (cmdName.isEmpty()) {
                                            // Create a command name
                                            cmdName = commandTableName + "_missing_cmd_name_" + missingNameSeq;
                                            cmdTableInfo.getData()[cmdRow][cmdNameIndex] = cmdName;
                                            missingNameSeq++;
                                        }

                                        // Create a prototype for the command argument structure table
                                        if (dbTable.createTable(new String[] { cmdName }, "Command argument structure",
                                                newCmdArgStructType.getName(), true, ccddMain.getMainFrame())) {
                                            throw new Exception("Cannot create command argument structure table");
                                        }

                                        // Build the path to the argument's structure variable. The
                                        // path is in the format root,dataType.variableName where root
                                        // = the command argument reference structure table name,
                                        // dataType = the command argument structure prototype table
                                        // name, and variableName is the command name
                                        String argStructRef = cmdArgTableInfo.getRootTable() + "," + cmdName + "."
                                                + cmdName;

                                        // Add the argument structure reference to the list
                                        argStructRefs.add(argStructRef);

                                        // Add the argument variable and structure to the argument
                                        // structure reference table
                                        cmdArgRef[cmdArgTableType
                                                .getColumnIndexByInputType(DefaultInputType.VARIABLE)] = cmdName;
                                        cmdArgRef[cmdArgTableType
                                                .getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)] = cmdName;

                                        // Load the command argument structure table's information
                                        TableInformation argTableInfo = dbTable.loadTableData(argStructRef, false, false,
                                                ccddMain.getMainFrame());

                                        // Check if an error occurred loading the command argument
                                        // structure table
                                        if (argTableInfo.isErrorFlag()) {
                                            throw new CCDDException("Cannot load command argument structure table");
                                        }

                                        List<Object[]> argTableData = new ArrayList<Object[]>();

                                        // Step through each command argument column grouping
                                        for (Integer[] cmdArgGroup : cmdArgGroups) {
                                            Object[] cmdArg = new Object[newCmdArgStructType.getColumnCountDatabase()];
                                            Arrays.fill(cmdArg, "");

                                            // Step through each column in this argument group
                                            for (Integer argCol : cmdArgGroup) {
                                                // Copy the command table's argument column value into
                                                // the new command argument structure table row. The
                                                // input type determines the column to which the value
                                                // belongs
                                                try {
                                                    cmdArg[newCmdArgStructType.getColumnIndexByInputType(
                                                            typeDefn.getInputTypes()[argCol])] = cmdTableInfo
                                                                    .getData()[cmdRow][argCol];
                                                } catch (Exception e) {
                                                    /* this input type no longer exists and the function returns a -1. This
                                                     * will catch the -1 and allow the function to continue
                                                     */
                                                }

                                                // Check if this is the argument variable name and isn't blank
                                                if (!cmdTableInfo.getData()[cmdRow][argCol].toString().isEmpty()
                                                        && typeDefn.getInputTypes()[argCol].equals(inputTypeHandler
                                                                .getInputTypeByDefaultType(DefaultInputType.VARIABLE))) {
                                                    // Add the argument variable name to the string
                                                    argNameString += cmdTableInfo.getData()[cmdRow][argCol] + ", ";
                                                }
                                            }

                                            boolean hasData = false;

                                            // Step through each cell in the row
                                            for (Object cell : cmdArg) {
                                                // Check if the cell isn't empty
                                                if (!cell.toString().isEmpty()) {
                                                    // Set the flag to indicate a non-empty cell and
                                                    // stop searching
                                                    hasData = true;
                                                    break;
                                                }
                                            }

                                            // Check if the row isn't empty
                                            if (hasData) {
                                                argTableData.add(cmdArg);
                                            }
                                            
                                            /* If a command has an array size greater than 1 then add all array indices */
                                            if (!cmdArg[DefaultColumn.ARRAY_SIZE.ordinal()].toString().isEmpty()) {
                                                Object[] cmdArgArrayMember;
                                                for (int i = 0; i < Integer.parseInt(cmdArg[DefaultColumn.ARRAY_SIZE.ordinal()].toString()); i++) {
                                                    cmdArgArrayMember = cmdArg.clone();
                                                    cmdArgArrayMember[DefaultColumn.VARIABLE_NAME.ordinal()] = cmdArgArrayMember[
                                                                           DefaultColumn.VARIABLE_NAME.ordinal()].toString() + "[" + Integer.toString(i) + "]";
                                                    argTableData.add(cmdArgArrayMember);
                                                }
                                            }
                                        }

                                        ///////////////////////////////////////////////////////////////
                                        // Create the command argument structure table
                                        ///////////////////////////////////////////////////////////////

                                        // Add the command argument name string and number to the list
                                        argNamesAndNumber
                                                .add(new String[] { CcddUtilities.removeTrailer(argNameString, ", "),
                                                        String.valueOf(argTableData.size()) });

                                        // Set the argument structure's table data
                                        argTableInfo.setData(argTableData.toArray(new Object[0][0]));

                                        // Build the string of the argument structure table's column names
                                        String argColumnNames = "";

                                        // Step through each column name in the argument structure table
                                        for (String argColumnName : newCmdArgStructType.getColumnNamesDatabaseQuoted()) {
                                            // Add the column name
                                            argColumnNames += argColumnName + ", ";
                                        }

                                        argColumnNames = CcddUtilities.removeTrailer(argColumnNames, ", ");
                                        String argCommand = "";

                                        // Step through each row in the argument structure table's data
                                        for (int argRow = 0; argRow < argTableInfo.getData().length; argRow++) {
                                            // Begin building the command to populate the argument structure table
                                            argCommand += "INSERT INTO " + cmdName.toLowerCase() + " (" + argColumnNames
                                                    + ") VALUES (" + (argRow + 1) + ", " + (argRow + 1) + ", ";

                                            // Step through each column in the argument structure table's data
                                            for (int argColumn = NUM_HIDDEN_COLUMNS; argColumn < argTableInfo
                                                    .getData()[argRow].length; argColumn++) {
                                                // Store the argument structure value in the command
                                                argCommand += "'" + argTableInfo.getData()[argRow][argColumn] + "', ";
                                            }

                                            argCommand = CcddUtilities.removeTrailer(argCommand, ", ") + "); ";
                                        }

                                        // Store the data into the argument structure database table
                                        dbCommand.executeDbCommand(new StringBuilder(argCommand), ccddMain.getMainFrame());

                                        // Check if the row contains data
                                        if (!cmdArgRef[cmdArgTableType.getColumnIndexByInputType(DefaultInputType.VARIABLE)]
                                                .toString().isEmpty()) {
                                            argRefTableData.add(cmdArgRef);
                                        }
                                    }

                                    ///////////////////////////////////////////////////////////////////
                                    // Update the command table
                                    ///////////////////////////////////////////////////////////////////

                                    // Build the string of the updated command table's column names
                                    String cmdColumnNames = "";

                                    // Step through each column name in the updated command table
                                    for (String cmdColumnName : newCmdTypeDefn.getColumnNamesDatabaseQuoted()) {
                                        // Add the column name
                                        cmdColumnNames += cmdColumnName + ", ";
                                    }

                                    cmdColumnNames = CcddUtilities.removeTrailer(cmdColumnNames, ", ");

                                    // Step through each row in the original command table's data
                                    for (int cmdRow = 0; cmdRow < cmdTableInfo.getData().length; cmdRow++) {
                                        // Begin building the command to populate the command table
                                        command += "INSERT INTO " + commandTableName.toLowerCase() + " (" + cmdColumnNames
                                                + ") VALUES ("
                                                + cmdTableInfo.getData()[cmdRow][DefaultColumn.PRIMARY_KEY.ordinal()] + ", "
                                                + cmdTableInfo.getData()[cmdRow][DefaultColumn.ROW_INDEX.ordinal()] + ", ";

                                        // Step through each column in the original command table's data
                                        for (int cmdColumn = NUM_HIDDEN_COLUMNS; cmdColumn < cmdTableInfo
                                                .getData()[cmdRow].length; cmdColumn++) {
                                            // Check if this column belongs to the command versus to an argument
                                            if (commandColumns.contains(cmdColumn)) {
                                                // Store the command value in the command
                                                command += CcddDbTableCommandHandler
                                                        .delimitText(cmdTableInfo.getData()[cmdRow][cmdColumn]) + ", ";
                                            }
                                        }

                                        // Store the variable path for the command
                                        command += "'" + argStructRefs.get(cmdRow) + "'); ";

                                    }

                                    ///////////////////////////////////////////////////////////////////
                                    // Build the command argument reference table
                                    ///////////////////////////////////////////////////////////////////

                                    // Set the argument structure reference's table data
                                    cmdArgTableInfo.setData(argRefTableData.toArray(new Object[0][0]));

                                    // Build the string of the argument structure reference table's
                                    // column names
                                    String argRefColumnNames = "";

                                    // Step through each column name in the argument structure
                                    // reference table
                                    for (String argRefColumnName : cmdArgTableType.getColumnNamesDatabaseQuoted()) {
                                        // Add the column name
                                        argRefColumnNames += argRefColumnName + ", ";
                                    }

                                    argRefColumnNames = CcddUtilities.removeTrailer(argRefColumnNames, ", ");
                                    String argRefCommand = "";

                                    // Step through each row in the argument structure table's data
                                    for (int argRefRow = 0; argRefRow < cmdArgTableInfo.getData().length; argRefRow++) {
                                        // Begin building the command to populate the argument
                                        // structure reference table
                                        argRefCommand += "INSERT INTO " + cmdArgTableInfo.getRootTable().toLowerCase()
                                                + " (" + argRefColumnNames + ") VALUES (" + (argRefRow + 1) + ", "
                                                + (argRefRow + 1) + ", ";

                                        // Step through each column in the argument structure reference
                                        // table's data
                                        for (int argRefColumn = NUM_HIDDEN_COLUMNS; argRefColumn < cmdArgTableInfo
                                                .getData()[argRefRow].length; argRefColumn++) {
                                            // Store the argument structure reference value in the
                                            // command
                                            argRefCommand += "'" + cmdArgTableInfo.getData()[argRefRow][argRefColumn]
                                                    + "', ";
                                        }

                                        argRefCommand = CcddUtilities.removeTrailer(argRefCommand, ", ") + "); ";
                                    }

                                    // Store the data into the argument structure reference table
                                    dbCommand.executeDbCommand(new StringBuilder(argRefCommand), ccddMain.getMainFrame());

                                    ///////////////////////////////////////////////////////////////////
                                    // Update any table cells or data fields with the command reference
                                    // input type
                                    ///////////////////////////////////////////////////////////////////

                                    // Step through each row in the command table's data
                                    for (int cmdRow = 0; cmdRow < argNamesAndNumber.size(); cmdRow++) {
                                        // Build the original and new command references
                                        String oldCmdRef = cmdTableInfo.getData()[cmdRow][cmdNameIndex] + " (code: "
                                                + cmdTableInfo.getData()[cmdRow][cmdCodeIndex] + ", owner: "
                                                + cmdTableInfo.getTablePath() + ", args: "
                                                + argNamesAndNumber.get(cmdRow)[1] + ")";
                                        String newCmdRef = cmdTableInfo.getData()[cmdRow][cmdNameIndex] + " (code: "
                                                + cmdTableInfo.getData()[cmdRow][cmdCodeIndex] + ", owner: "
                                                + cmdTableInfo.getTablePath() + ", arg: " + argNamesAndNumber.get(cmdRow)[0]
                                                + ")";

                                        // Step through each table type command reference input type
                                        // reference
                                        for (InputTypeReference cmdRef : cmdRefChkResults.getReferences()) {
                                            // Step through each table of this table type
                                            for (String table : cmdRef.getTables()) {
                                                // Update references to the command reference from the
                                                // table
                                                cmdRefInpTypeCmd.append("UPDATE " + dbControl.getQuotedName(table) + " SET "
                                                        + cmdRef.getColumnDb() + " = '" + newCmdRef + "' WHERE "
                                                        + cmdRef.getColumnDb() + " = E'" + oldCmdRef + "'; ");
                                            }
                                        }

                                        // Check if a data field has the command reference input type
                                        if (cmdRefChkResults.isFieldUsesType()) {
                                            // Update the data field value if the command path matches
                                            cmdRefInpTypeCmd.append("UPDATE " + InternalTable.FIELDS.getTableName()
                                                    + " SET " + FieldsColumn.FIELD_VALUE.getColumnName() + " = E'"
                                                    + newCmdRef + "' WHERE " + FieldsColumn.FIELD_TYPE.getColumnName()
                                                    + " = E'" + DefaultInputType.COMMAND_REFERENCE.getInputName() + "' AND "
                                                    + FieldsColumn.FIELD_VALUE.getColumnName() + " = E'" + oldCmdRef
                                                    + "'; ");
                                        }
                                    }
                                }
                                commands.add(command);
                                command = "";
                            }

                            // Replace the original command table type with the new one
                            newCmdTypeDefn.setName(typeDefn.getName());
                            tableTypeHandler.getTypeDefinitions().set(typeIndex, newCmdTypeDefn);

                            // Delete the original command tables. References to this command table in
                            // the internal tables, including data fields, are preserved
                            if (dbTable.deleteTable(commandTableNames.toArray(new String[0]), null,
                                    ccddMain.getMainFrame())) {
                                throw new CCDDException();
                            }

                            // Step through each command table of the current table type. These are
                            // created one at a time so that each can have its description restored
                            for (int index = 0; index < commandTableNames.size(); index++) {
                                // Create the updated command table
                                if (dbTable.createTable(new String[] { commandTableNames.get(index) },
                                        commandTableDescriptions.get(index), typeDefn.getName(), false, ccddMain.getMainFrame())) {
                                    throw new CCDDException();
                                }
                            }

                            // Update the command table by replacing the original contents with the
                            // updated values
                            for (int index = 0; index < commands.size(); index++) {
                                dbCommand.executeDbCommand(new StringBuilder(commands.get(index)), ccddMain.getMainFrame());
                            }
                            
                            /* Update the internal __orders table */
                            int columnCount = tableTypeHandler.getTypeDefinition("Command").getColumnCountDatabase();
                            String columnOrder = "'";
                            for (int index = 0; index < columnCount; index++) {
                                columnOrder += Integer.toString(index);
                                if (index != columnCount-1) {
                                    columnOrder += ":";
                                } else {
                                    columnOrder += "'";
                                }
                            }
                                                    
                            for (int index = 0; index < commandTableNames.size(); index++) {
                                dbCommand.executeDbCommand(new StringBuilder("UPDATE __orders SET column_order = ").append(columnOrder)
                                        .append(" WHERE table_path = '").append(commandTableNames.get(index)).append("';"), ccddMain.getMainFrame());
                            }
                        }
                    }

                    // Update the table types table in the database
                dbCommand.executeDbCommand(new StringBuilder(dbTable.storeTableTypesInfoTableCommand()), ccddMain.getMainFrame());
    
                    // Clean up the lists to reflect the changes to the database
                    dbTable.updateListsAndReferences(ccddMain.getMainFrame());

                    // Update the command reference input type cells and fields
                    dbCommand.executeDbCommand(cmdRefInpTypeCmd, ccddMain.getMainFrame());
                }
                // Release the save point. This must be done within a transaction block, so it
                // must
                // be done prior to the commit below
                dbCommand.releaseSavePoint(ccddMain.getMainFrame());

                // Commit the change(s) to the database
                dbControl.getConnection().commit();

                // Inform the user that updating the database command tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '").append(dbControl.getProjectName())
                        .append("' command tables conversion  complete"));
            }
        } catch (Exception e) {
            // Inform the user that converting the command tables failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                    "Cannot convert project '" + dbControl.getProjectName() + "' command tables to new format; cause '"
                            + e.getMessage() + "'",
                    "<html><b>Cannot convert project '</b>" + dbControl.getProjectName()
                            + "<b>' command tables to new format " + "(project database will be closed)");

            try {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(ccddMain.getMainFrame());
            } catch (SQLException se) {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                        "Cannot revert changes to project; cause '" + se.getMessage() + "'",
                        "<html><b>Cannot revert changes to project");
            }

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the fields table to include a field_inherited column and set each
     * field's default status. Older versions of CCDD are not compatible with the
     * project database after applying this patch
     *
     * @throws CCDDException If the user elects to not install the patch or an error
     *                       occurs while applying the patch
     *********************************************************************************************/
    private void updateFieldsPart1() throws CCDDException {
        patch11052018Continue = false;
        patch11052018RenamedFields = 0;
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

            // Create lists to contain the old and new fields table items
            List<String[]> tableData = new ArrayList<String[]>();

            // Read the contents of the fields table
            ResultSet fieldsData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM ")
                    .append(InternalTable.FIELDS.getTableName()).append(" ORDER BY OID;"), ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (fieldsData.getMetaData().getColumnCount() == 8) {

                if(this.patchSet.get(this.PATCH_11052018).confirmPatchApplication() == false){
                    fieldsData.close();
                    throw new CCDDException(this.patchSet.get(this.PATCH_11052018).getUserCancelledMessage());
                }

                // Step through each of the query results
                while (fieldsData.next()) {
                    // Create an array to contain the column values
                    String[] columnValues = new String[9];

                    // Step through each column in the row
                    for (int column = 0; column < 8; column++) {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0
                        columnValues[column] = fieldsData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null) {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Set the field default flag to indicate it isn't inherited from a table type
                    columnValues[8] = "false";

                    // Add the row data to the list
                    tableData.add(columnValues);
                }

                fieldsData.close();

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Check if there are any fields in the table
                if (tableData.size() != 0) {
                    int currentIndex = 0;

                    // Indicate in the log that the old data successfully loaded
                    eventLog.logEvent(SUCCESS_MSG, new StringBuilder(InternalTable.FIELDS.getTableName()).append(" retrieved"));

                    // Get the array containing the comments for all data tables
                    String[][] comments = dbTable.queryDataTableComments(ccddMain.getMainFrame());

                    // Step through the data field definitions
                    for (String[] fieldDefn : tableData) {
                        // Check if the field is owned by a table
                        if (CcddFieldHandler.isTableField(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()])) {
                            // Get the table's type name from the table comment
                            String tableType = dbTable.getTableComment(
                                    TableInformation.getPrototypeName(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]),
                                    comments)[TableCommentIndex.TYPE.ordinal()];

                            // Step through the data field definitions
                            for (String[] otherFldDefn : tableData) {
                                // Check if this is a data field belonging to the table's type
                                // definition and the field names match
                                if (CcddFieldHandler.getFieldTypeName(tableType)
                                        .equals(otherFldDefn[FieldsColumn.OWNER_NAME.ordinal()])
                                        && fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                .equals(otherFldDefn[FieldsColumn.FIELD_NAME.ordinal()])) {
                                    // Check if the input types match
                                    if (fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]
                                            .equals(otherFldDefn[FieldsColumn.FIELD_TYPE.ordinal()])) {
                                        // Set the flag that indicates this field is inherited
                                        fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = "true";

                                        // Step through the original field definition parts except
                                        // for field owner, name, and value (so as not to overwrite
                                        // the field value with that from the inherited field)
                                        for (int index = 2; index < 7; index++) {
                                            // Overwrite the instance of the field with the
                                            // inherited field
                                            fieldDefn[index] = otherFldDefn[index];
                                        }
                                    }
                                    // The names match but the input types don't match. These are
                                    // considered different fields
                                    else {
                                        boolean isNoMatch = true;
                                        patch11052018RenamedFields++;

                                        // Continue to update the field's name until there's no
                                        // match with one of its other fields
                                        while (isNoMatch) {
                                            int dupIndex = 0;
                                            isNoMatch = false;

                                            // Modify the field's name
                                            fieldDefn[FieldsColumn.FIELD_NAME.ordinal()] += "_";

                                            // Step through the data field definitions
                                            for (String[] dupFldDefn : tableData) {
                                                // Check if this isn't the field being changed
                                                if (currentIndex != dupIndex) {
                                                    // Check if the field name matches another of
                                                    // the table's fields or its default fields
                                                    if (fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                            .equals(dupFldDefn[FieldsColumn.FIELD_NAME.ordinal()])
                                                            && (fieldDefn[FieldsColumn.OWNER_NAME.ordinal()].equals(
                                                                    dupFldDefn[FieldsColumn.OWNER_NAME.ordinal()])
                                                                    || dupFldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                                                            .equals(CcddFieldHandler
                                                                                    .getFieldTypeName(tableType)))) {
                                                        // Set the flag to indicate this name is in
                                                        // use and stop searching
                                                        isNoMatch = true;
                                                        break;
                                                    }
                                                }

                                                dupIndex++;
                                            }
                                        }
                                    }

                                    // Stop searching since a matching field name was found
                                    break;
                                }
                            }

                            // Update the data field definition to set the default status and
                            // update the field name (if needed)
                            tableData.set(currentIndex, fieldDefn);
                        }

                        currentIndex++;
                    }
                }

                // Store the updated fields table
                dbTable.storeInformationTable(InternalTable.FIELDS, tableData, null, ccddMain.getMainFrame());

                // Set the flag to indicate that part 2 of this patch should be performed
                patch11052018Continue = true;

                // Inform the user that updating the database fields table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '").append(dbControl.getProjectName())
                        .append("' data fields table conversion (part 1) complete"));
            }
        } catch (Exception e) {
            // Inform the user that converting the fields table failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                    "Cannot convert project '" + dbControl.getProjectName()
                            + "' data fields table to new format; cause '" + e.getMessage() + "'",
                    "<html><b>Cannot convert project '</b>" + dbControl.getProjectName()
                            + "<b>' data fields table to new format " + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * After updating the fields table to include a field_inherited column add
     * inherited fields to those tables that don't already have the field. Older
     * versions of CCDD are not compatible with the project database after applying
     * this patch
     *
     * @throws CCDDException If the user elects to not install the patch or an error
     *                       occurs while applying the patch
     *********************************************************************************************/
    private void updateFieldsPart2() throws CCDDException {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();

        // Check if part 1 of this patch successfully completed
        if (patch11052018Continue) {
            try {
                int addedFields = 0;
                CcddFieldHandler fieldHandler = ccddMain.getFieldHandler();
                CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

                // Step through each table type
                for (String typeName : ccddMain.getTableTypeHandler().getTableTypeNames()) {
                    // Get the list of names of all tables of the specified type
                    List<String> tableNamesList = dbTable.getAllTablesOfType(typeName, null, ccddMain.getMainFrame());

                    // Step through each of this table type's data fields
                    for (FieldInformation fieldInfo : fieldHandler
                            .getFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(typeName))) {
                        // Check if this isn't a separator or break
                        if (!fieldInfo.getInputType()
                                .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SEPARATOR))
                                && !fieldInfo.getInputType()
                                        .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.BREAK))) {
                            // Step through each table of this type
                            for (String tableName : tableNamesList) {
                                // Check if the data field meets the criteria of a new field for
                                // this table ...
                                if (
                                // ... the table doesn't have this data field
                                (fieldHandler.getFieldInformationByName(tableName, fieldInfo.getFieldName()) == null)

                                        // ... and the table isn't a child structure (all fields are
                                        // stored for prototypes, even if not displayed) or the field
                                        // is applicable to this table
                                        && (!tableName.contains(".") || fieldHandler.isFieldApplicable(tableName,
                                                fieldInfo.getApplicabilityType().getApplicabilityName(), null))) {
                                    // Add the data field to the table and set the flag indicating
                                    // a change has been made
                                    fieldHandler.getFieldInformation().add(new FieldInformation(tableName,
                                            fieldInfo.getFieldName(), fieldInfo.getDescription(),
                                            fieldInfo.getInputType(), fieldInfo.getSize(), fieldInfo.isRequired(),
                                            fieldInfo.getApplicabilityType(), fieldInfo.getValue(), true, null, -1));
                                    addedFields++;
                                }
                            }
                        }
                    }
                }

                // Check if any fields were added
                if (addedFields != 0) {
                    // Store the data fields
                    dbTable.storeInformationTable(InternalTable.FIELDS, fieldHandler.getFieldDefnsFromInfo(), null,
                            ccddMain.getMainFrame());
                }

                // Check if any fields were added or renamed
                if (addedFields != 0 || patch11052018RenamedFields != 0) {
                    // Inform the user how many inherited fields were added and renamed
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html><b>Number of inherited data fields added: </b>" + addedFields
                                    + "<br><b>Number of existing fields renamed: </b>" + patch11052018RenamedFields,
                            "Patch #11052018 Data Field Updates", JOptionPane.INFORMATION_MESSAGE,
                            DialogOption.OK_OPTION);
                }

                // Inform the user that updating the database fields table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '").append(dbControl.getProjectName())
                        .append("' data fields table conversion (part 2) complete"));
            } catch (Exception e) {
                // Inform the user that adding the inherited tables failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                        "Cannot convert project '" + dbControl.getProjectName()
                                + "' data fields table to new format; cause '" + e.getMessage() + "'",
                        "<html><b>Cannot convert project '</b>" + dbControl.getProjectName()
                                + "<b>' data fields table to new format " + "(project database will be closed)");

                throw new CCDDException();
            }
        }
    }

    /**********************************************************************************************
     * Update the project database table type and data field table references to the
     * message ID name and message ID input types. The message name and ID have been
     * combined into a single input type, 'Message name &amp; ID'. Change the
     * message ID name input type to 'Text' and the message ID input type to
     * 'Message name &amp; ID'. Note that the original message ID names are no
     * longer associated with the IDs; this must be done manually
     *
     * @throws CCDDException if an error occurs performing the update or the user
     *                       elects to not install the patch
     *********************************************************************************************/
    private void updateMessageNamesAndIDs() throws CCDDException {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

            // Determine if the table type or data field tables reference a message ID name
            // or
            // message ID input type, or if the application scheduler table has separate
            // wake-up
            // names and IDs
            ResultSet msgData = dbCommand.executeDbQuery(new StringBuilder("SELECT 1 FROM ")
                    .append(InternalTable.TABLE_TYPES.getTableName()).append(" WHERE ")
                    .append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = 'Message ID name' OR ")
                    .append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = 'Message ID' UNION SELECT 1 FROM ")
                    .append(InternalTable.FIELDS.getTableName()).append(" WHERE ").append(FieldsColumn.FIELD_TYPE.getColumnName())
                    .append(" = 'Message ID name' OR ").append(FieldsColumn.FIELD_TYPE.getColumnName())
                    .append(" = 'Message ID' UNION SELECT 1 FROM ").append(InternalTable.APP_SCHEDULER.getTableName())
                    .append(" WHERE ").append(AppSchedulerColumn.APP_INFO.getColumnName())
                    .append(" ~E'[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,.*';"),
                    ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (msgData.next()) {
                msgData.close();

                if(this.patchSet.get(this.PATCH_08292018).confirmPatchApplication() == false){
                    throw new CCDDException(this.patchSet.get(this.PATCH_08292018).getUserCancelledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Update the table type and data field tables. The message ID fields are
                // changed
                // to use the message name & ID type, and the message ID name fields are changed
                // to
                // use the text input type
                dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(InternalTable.TABLE_TYPES.getTableName())
                         .append(" SET ").append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = '")
                         .append(DefaultInputType.MESSAGE_NAME_AND_ID.getInputName()).append("' WHERE ")
                         .append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = 'Message ID'; UPDATE ")
                         .append(InternalTable.TABLE_TYPES.getTableName()).append(" SET ")
                         .append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = '")
                         .append(DefaultInputType.TEXT.getInputName()).append("' WHERE ")
                         .append(TableTypesColumn.INPUT_TYPE.getColumnName()).append(" = 'Message ID name'; UPDATE ")
                         .append(InternalTable.FIELDS.getTableName()).append(" SET ")
                         .append(FieldsColumn.FIELD_TYPE.getColumnName()).append(" = '")
                         .append(DefaultInputType.MESSAGE_NAME_AND_ID.getInputName()).append("' WHERE ")
                         .append(FieldsColumn.FIELD_TYPE.getColumnName()).append(" = 'Message ID'; UPDATE ")
                         .append(InternalTable.FIELDS.getTableName()).append(" SET ").append(FieldsColumn.FIELD_TYPE.getColumnName())
                         .append(" = '").append(DefaultInputType.TEXT.getInputName()).append("' WHERE ")
                         .append(FieldsColumn.FIELD_TYPE.getColumnName()).append(" = 'Message ID name'; UPDATE ")
                         .append(InternalTable.APP_SCHEDULER.getTableName()).append(" SET ")
                         .append(AppSchedulerColumn.APP_INFO.getColumnName()).append(" = regexp_replace(")
                         .append(AppSchedulerColumn.APP_INFO.getColumnName())
                         .append(", E'([^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*),([^,]*,[^,]*,[^,]*),([^,]*,.*)', '\\\\1 \\\\2 \\\\3', 'g') WHERE ")
                         .append(AppSchedulerColumn.APP_INFO.getColumnName())
                         .append(" ~ E'[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,.*';"),
                        ccddMain.getMainFrame());

                // Inform the user that updating the database table type, data field, and
                // application scheduler tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '").append(dbControl.getProjectName())
                        .append("' table type, data field, and application scheduler tables conversion complete"));
            }
        } catch (Exception e) {
            // Inform the user that adding access level support failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                    "Cannot update project '" + dbControl.getProjectName() + "' to change message name and IDs; cause '"
                            + e.getMessage() + "'",
                    "<html><b>Cannot update project '</b>" + dbControl.getProjectName()
                            + "<b>' to change message name and IDs " + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the project database so that user access levels are supported
     *
     * @throws CCDDException if an error occurs performing the update or the user
     *                       elects to not install the patch
     *********************************************************************************************/
    private void updateUserAccess() throws CCDDException {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try {
            // Check if the project administrator isn't in the database comment; this
            // indicates the
            // project hasn't been updated to support user access levels
            if (dbControl.getDatabaseAdmins(dbControl.getDatabaseName()) == null) {
                CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

                if(this.patchSet.get(this.PATCH_07242018).confirmPatchApplication() == false){
                    throw new CCDDException(this.patchSet.get(this.PATCH_07242018).getUserCancelledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Get the database comment, separated into its individual parts
                String[] comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());

                // Update the database's comment, adding the current user as the project creator
                dbCommand.executeDbUpdate(new StringBuilder(dbControl.buildDatabaseCommentCommandAndUpdateInternalTable(
                        dbControl.getProjectName(), dbControl.getUser(), false, comment[DatabaseComment.DESCRIPTION.ordinal()])),
                                ccddMain.getMainFrame());

                // Update the user access level table, setting the current user as the
                // administrator
                List<String[]> userData = new ArrayList<String[]>(1);
                userData.add(new String[] { dbControl.getUser(), AccessLevel.ADMIN.getDisplayName() });
                ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.USERS, userData, null,
                        ccddMain.getMainFrame());

                // Inform the user that updating the database to support user access levels
                // completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '")
                        .append(dbControl.getProjectName()).append("' user access level conversion complete"));
            }
        } catch (Exception e) {
            // Inform the user that adding access level support failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                    "Cannot update project '" + dbControl.getProjectName() + "' to support user access levels; cause '"
                            + e.getMessage() + "'",
                    "<html><b>Cannot update project '</b>" + dbControl.getProjectName()
                            + "<b>' to support user access levels " + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the padding variable format from '__pad#' to 'pad#__'. This is to
     * accommodate XML exports that don't allow leading underscores in variable
     * names (e.g., EDS)
     *
     * @throws CCDDException if an error occurs performing the update or the user
     *                       elects to not install the patch
     *********************************************************************************************/
    private void updatePaddingVariables() throws CCDDException {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();

            String varColNames = "";

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions()) {
                // Check if the table type represents a structure
                if (typeDefn.isStructure()) {
                    // Append the variable name column name
                    varColNames += typeDefn.getDbColumnNameByInputType(DefaultInputType.VARIABLE) + ",";
                }
            }

            varColNames = CcddUtilities.removeTrailer(varColNames, ",");

            // Search for pad variables using the old format in all prototype tables
            ResultSet padData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM search_tables(E'__pad', false, ")
                    .append("false, 'PROTO', '{").append(varColNames).append("}');"),
                    ccddMain.getMainFrame());

            // Check if there are any pad variables using the old format in any structure
            // table
            if (padData.next()) {
                padData.close();
                
                if(this.patchSet.get(PATCH_06212018).confirmPatchApplication() == false){
                    throw new CCDDException(this.patchSet.get(PATCH_06212018).getUserCancelledMessage()); 
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Step through each prototype structure table
                for (String protoStruct : dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE)) {
                    // Get the table's comment
                    String[] comment = dbTable.queryDataTableComment(protoStruct, ccddMain.getMainFrame());

                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler
                            .getTypeDefinition(comment[TableCommentIndex.TYPE.ordinal()]);

                    // Get the table's variable name column name
                    String variableNameColumn = typeDefn.getDbColumnNameByInputType(DefaultInputType.VARIABLE);

                    // Update the padding variable names to the new format
                    dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(dbControl.getQuotedName(protoStruct)).append(" SET ")
                             .append(dbControl.getQuotedName(variableNameColumn)).append(" = regexp_replace(").append(variableNameColumn)
                             .append(", E'^__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E'pad\\\\1__\\\\2');"), ccddMain.getMainFrame());
                }

                // Update the padding variable names in the custom values table to the new
                // format
                dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(InternalTable.VALUES.getTableName())
                         .append(" SET ").append(ValuesColumn.TABLE_PATH.getColumnName()).append(" = regexp_replace(")
                         .append(ValuesColumn.TABLE_PATH.getColumnName())
                         .append(", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');"),
                        ccddMain.getMainFrame());

                // Update the padding variable names in the links table to the new format
                dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(InternalTable.LINKS.getTableName())
                         .append(" SET ").append(LinksColumn.MEMBER.getColumnName()).append(" = regexp_replace(")
                         .append(LinksColumn.MEMBER.getColumnName()).append(", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');"),
                         ccddMain.getMainFrame());

                // Update the padding variable names in the telemetry scheduler table to the new
                // format
                dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(InternalTable.TLM_SCHEDULER.getTableName())
                         .append(" SET ").append(TlmSchedulerColumn.MEMBER.getColumnName()).append(" = regexp_replace(")
                         .append(TlmSchedulerColumn.MEMBER.getColumnName())
                         .append(", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');"),
                         ccddMain.getMainFrame());

                // Inform the user that updating the padding variables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Project '").append(dbControl.getProjectName())
                        .append("' padding variable conversion complete"));
            }
        } catch (Exception e) {
            // Inform the user that converting the padding variable names failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                    "Cannot convert project '" + dbControl.getProjectName()
                            + "' padding variable names to new format; cause '" + e.getMessage() + "'",
                    "<html><b>Cannot convert project '</b>" + dbControl.getProjectName()
                            + "<b>' padding variable names to new format");
        }
    }

    // /**********************************************************************************************
    // * Update the associations table to include a name column. Older versions of
    // CCDD are not
    // * compatible with the project database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while
    // applying
    // * the patch
    // *********************************************************************************************/
    // private void updateAssociationsTable2() throws CCDDException
    // {
    // CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
    // CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
    //
    // try
    // {
    // CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
    // CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
    //
    // // Create lists to contain the old and new associations table items
    // List<String[]> tableData = new ArrayList<String[]>();
    //
    // // Read the contents of the associations table
    // ResultSet assnsData = dbCommand.executeDbQuery("SELECT * FROM "
    // + InternalTable.ASSOCIATIONS.getTableName()
    // + " ORDER BY OID;",
    // ccddMain.getMainFrame());
    //
    // // Check if the patch hasn't already been applied
    // if (assnsData.getMetaData().getColumnCount() == 3)
    // {
    // // Check if the user elects to not apply the patch
    // if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
    // "<html><b>Apply patch to update the script "
    // + "associations table?<br><br></b>"
    // + "Incorporates a name column in the "
    // + "script associations table.<br><b><i>Older "
    // + "versions of CCDD will be incompatible "
    // + "with this project database after "
    // + "applying the patch",
    // "Apply Patch #11132017",
    // JOptionPane.QUESTION_MESSAGE,
    // DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
    // {
    // assnsData.close();
    // throw new CCDDException("User elected to not install patch (#11132017)");
    // }
    //
    // // Step through each of the query results
    // while (assnsData.next())
    // {
    // // Create an array to contain the column values
    // String[] columnValues = new String[4];
    //
    // // Step through each column in the row
    // for (int column = 0; column < 3; column++)
    // {
    // // Add the column value to the array. Note that the first column's index in
    // // the database is 1, not 0. Also, shift the old data over one column to
    // // make room for the name
    // columnValues[column + 1] = assnsData.getString(column + 1);
    //
    // // Check if the value is null
    // if (columnValues[column] == null)
    // {
    // // Replace the null with a blank
    // columnValues[column] = "";
    // }
    // }
    //
    // // Add the row data to the list
    // tableData.add(columnValues);
    // }
    //
    // assnsData.close();
    //
    // // Check if there are any associations in the table
    // if (tableData.size() != 0)
    // {
    // // Indicate in the log that the old data successfully loaded
    // eventLog.logEvent(SUCCESS_MSG,
    // InternalTable.ASSOCIATIONS.getTableName()
    // + " retrieved");
    // }
    //
    // // Back up the project database before applying the patch
    // backupDatabase(dbControl);
    //
    // // Store the updated associations table
    // dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
    // tableData,
    // null,
    // ccddMain.getMainFrame());
    //
    // // Inform the user that updating the database associations table completed
    // eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
    // "Project '"
    // + dbControl.getProjectName()
    // + "' associations table conversion complete");
    // }
    // }
    // catch (Exception e)
    // {
    // // Inform the user that converting the associations table failed
    // eventLog.logFailEvent(ccddMain.getMainFrame(),
    // "Cannot convert project '"
    // + dbControl.getProjectName()
    // + "' associations table to new format; cause '"
    // + e.getMessage()
    // + "'",
    // "<html><b>Cannot convert project '</b>"
    // + dbControl.getProjectName()
    // + "<b>' associations table to new format "
    // + "(project database will be closed)");
    //
    // throw new CCDDException();
    // }
    // }
    //
    // /**********************************************************************************************
    // * Update the data fields table applicability column to change "Parents only"
    // to "Roots
    // only".
    // * Older versions of CCDD are not compatible with the project database after
    // applying this
    // * patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while
    // applying
    // * the patch
    // *********************************************************************************************/
    // private void updateFieldApplicability() throws CCDDException
    // {
    // CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
    // CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
    //
    // try
    // {
    // CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
    //
    // // Read the contents of the data field table's applicability column
    // ResultSet fieldData = dbCommand.executeDbQuery("SELECT * FROM "
    // + InternalTable.FIELDS.getTableName()
    // + " WHERE "
    // + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
    // + " = 'Parents only';",
    // ccddMain.getMainFrame());
    //
    // // Check if the patch hasn't already been applied
    // if (fieldData.next())
    // {
    // // Check if the user elects to not apply the patch
    // if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
    // "<html><b>Apply patch to update the data "
    // + "fields table?<br><br></b>Changes "
    // + "data field applicability 'Parents "
    // + "only' to 'Roots only'.<br><b><i>Older "
    // + "versions of CCDD are incompatible "
    // + "with this project database after "
    // + "applying the patch",
    // "Apply Patch #09272017",
    // JOptionPane.QUESTION_MESSAGE,
    // DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
    // {
    // fieldData.close();
    // throw new CCDDException("User elected to not install patch (#09272017)");
    // }
    //
    // fieldData.close();
    //
    // // Back up the project database before applying the patch
    // backupDatabase(dbControl);
    //
    // // Update the data fields table
    // dbCommand.executeDbCommand("UPDATE "
    // + InternalTable.FIELDS.getTableName()
    // + " SET "
    // + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
    // + " = '"
    // + ApplicabilityType.ROOT_ONLY.getApplicabilityName()
    // + "' WHERE "
    // + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
    // + " = 'Parents only';",
    // ccddMain.getMainFrame());
    //
    // // Inform the user that updating the database data fields table completed
    // eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
    // "Project '"
    // + dbControl.getProjectName()
    // + "' data fields table conversion complete");
    // }
    // }
    // catch (Exception e)
    // {
    // // Inform the user that converting the data fields table failed
    // eventLog.logFailEvent(ccddMain.getMainFrame(),
    // "Cannot convert project '"
    // + dbControl.getProjectName()
    // + "' data fields table to new format; cause '"
    // + e.getMessage()
    // + "'",
    // "<html><b>Cannot convert project '</b>"
    // + dbControl.getProjectName()
    // + "<b>' data fields table to new format "
    // + "(project database will be closed)");
    //
    // throw new CCDDException();
    // }
    // }
    //
    // /**********************************************************************************************
    // * Update the associations table to include a description column and to change
    // the table
    // * separator characters in the member_table column. Older versions of CCDD are
    // not compatible
    // * with the project database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while
    // applying
    // * the patch
    // *********************************************************************************************/
    // private void updateAssociationsTable() throws CCDDException
    // {
    // CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
    // CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
    //
    // try
    // {
    // CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
    // CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
    //
    // // Create lists to contain the old and new associations table items
    // List<String[]> tableData = new ArrayList<String[]>();
    //
    // // Read the contents of the associations table
    // ResultSet assnsData = dbCommand.executeDbQuery("SELECT * FROM "
    // + InternalTable.ASSOCIATIONS.getTableName()
    // + " ORDER BY OID;",
    // ccddMain.getMainFrame());
    //
    // // Check if the patch hasn't already been applied
    // if (assnsData.getMetaData().getColumnCount() == 2)
    // {
    // // Check if the user elects to not apply the patch
    // if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
    // "<html><b>Apply patch to update the script "
    // + "associations table?<br><br></b>"
    // + "Incorporates a description column in the "
    // + "script associations table.<br><b><i>Older "
    // + "versions of CCDD will be incompatible "
    // + "with this project database after "
    // + "applying the patch",
    // "Apply Patch #07212017",
    // JOptionPane.QUESTION_MESSAGE,
    // DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
    // {
    // assnsData.close();
    // throw new CCDDException("User elected to not install patch (#0712017)");
    // }
    //
    // // Step through each of the query results
    // while (assnsData.next())
    // {
    // // Create an array to contain the column values
    // String[] columnValues = new String[3];
    //
    // // Step through each column in the row
    // for (int column = 0; column < 2; column++)
    // {
    // // Add the column value to the array. Note that the first column's index in
    // // the database is 1, not 0. Also, shift the old data over one column to
    // // make room for the description
    // columnValues[column + 1] = assnsData.getString(column + 1);
    //
    // // Check if the value is null
    // if (columnValues[column] == null)
    // {
    // // Replace the null with a blank
    // columnValues[column] = "";
    // }
    // }
    //
    // // Add the row data to the list
    // tableData.add(columnValues);
    // }
    //
    // assnsData.close();
    //
    // // Check if there are any associations in the table
    // if (tableData.size() != 0)
    // {
    // // Indicate in the log that the old data successfully loaded
    // eventLog.logEvent(SUCCESS_MSG,
    // InternalTable.ASSOCIATIONS.getTableName()
    // + " retrieved");
    //
    // // Step through each script association
    // for (int row = 0; row < tableData.size(); row++)
    // {
    // // Set the description to a blank and replace the table name separator
    // // characters with the new ones
    // tableData.set(row, new String[] {"",
    // tableData.get(row)[1],
    // tableData.get(row)[2].replaceAll(" \\+ ",
    // ASSN_TABLE_SEPARATOR)});
    // }
    // }
    //
    // // Back up the project database before applying the patch
    // backupDatabase(dbControl);
    //
    // // Store the updated associations table
    // dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
    // tableData,
    // null,
    // ccddMain.getMainFrame());
    //
    // // Inform the user that updating the database associations table completed
    // eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
    // "Project '"
    // + dbControl.getProjectName()
    // + "' associations table conversion complete");
    // }
    // }
    // catch (Exception e)
    // {
    // // Inform the user that converting the associations table failed
    // eventLog.logFailEvent(ccddMain.getMainFrame(),
    // "Cannot convert project '"
    // + dbControl.getProjectName()
    // + "' associations table to new format; cause '"
    // + e.getMessage()
    // + "'",
    // "<html><b>Cannot convert project '</b>"
    // + dbControl.getProjectName()
    // + "<b>' associations table to new format "
    // + "(project database will be closed)");
    //
    // throw new CCDDException();
    // }
    // }
    //
    // /**********************************************************************************************
    // * Update the project database comments to include the database name with
    // capitalization and
    // * special characters intact. The project database is first backed up to the
    // file
    // * <projectName>_<timeStamp>.dbu. The new format for the comment is <CCDD
    // project identifier
    // * string><lock status, 0 or 1>;<project name with capitalization
    // intact>;<project
    // * description>. Older versions of CCDD are compatible with the project
    // database after
    // applying
    // * this patch
    // *
    // * @throws CCDDException
    // * If an error occurs while applying the patch
    // *********************************************************************************************/
    // private void updateDataBaseComment()
    // {
    // CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
    // CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
    //
    // try
    // {
    // CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
    //
    // // Get the comment for the currently open database
    // String comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());
    //
    // // Divide the comment into the lock status, visible name, and description
    // String[] nameAndDesc = comment.split(DATABASE_COMMENT_SEPARATOR, 3);
    //
    // // Check if the comment isn't in the new format
    // if (nameAndDesc.length < 3
    // ||
    // !dbControl.getProjectName().equalsIgnoreCase(nameAndDesc[DatabaseComment.PROJECT_NAME.ordinal()]))
    // {
    // // Back up the project database before applying the patch
    // backupDatabase(dbControl);
    //
    // // Update the project database comment to the new format
    // dbCommand.executeDbCommand("COMMENT ON DATABASE "
    // + dbControl.getDatabaseName()
    // + " IS "
    // + CcddDbTableCommandHandler.delimitText(CCDD_PROJECT_IDENTIFIER
    // + comment.substring(0, 1)
    // + DATABASE_COMMENT_SEPARATOR
    // + dbControl.getProjectName()
    // + DATABASE_COMMENT_SEPARATOR
    // + nameAndDesc[0].substring(1))
    // + "; ",
    // ccddMain.getMainFrame());
    //
    // // Inform the user that updating the database comment completed
    // eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
    // "Project '"
    // + dbControl.getProjectName()
    // + "' comment conversion complete");
    // }
    // }
    // catch (Exception e)
    // {
    // // Inform the user that converting the database comments failed
    // eventLog.logFailEvent(ccddMain.getMainFrame(),
    // "Cannot convert project '"
    // + dbControl.getProjectName()
    // + "' comment to new format; cause '"
    // + e.getMessage()
    // + "'",
    // "<html><b>Cannot convert project '</b>"
    // + dbControl.getProjectName()
    // + "<b>' comment to new format");
    // }
    // }
    //
    // /**********************************************************************************************
    // * Update the internal table __types to the new name __table_types, delete the
    // primitive_only
    // * column, and add the structure allowed and pointer allowed columns. If
    // successful, the
    // * original table (__types) is renamed, preserving the original information
    // and preventing
    // * subsequent conversion attempts. The project database is first backed up to
    // the file
    // * <projectName>_<timeStamp>.dbu. Older versions of CCDD are not compatible
    // with the project
    // * database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while
    // applying
    // * the patch
    // *********************************************************************************************/
    // private void updateTableTypesTable() throws CCDDException
    // {
    // CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
    //
    // // Check if the old table exists
    // if (dbTable.isTableExists("__types", ccddMain.getMainFrame()))
    // {
    // // Check if the user elects to not apply the patch
    // if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
    // "<html><b>Apply patch to update the table types "
    // + "table?<br><br></b>Creates the new "
    // + "__table_types table from the old __types "
    // + "table.<br><b><i>Older versions of CCDD "
    // + "will be incompatible with this project "
    // + "database after applying the patch",
    // "Apply Patch #01262017",
    // JOptionPane.QUESTION_MESSAGE,
    // DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
    // {
    // throw new CCDDException("User elected to not install patch (#01262017)");
    // }
    //
    // CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
    // CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
    // CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
    // CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
    // CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();
    //
    // try
    // {
    // // Back up the project database before applying the patch
    // backupDatabase(dbControl);
    //
    // // Create lists to contain the old and new table types table items
    // List<String[]> oldTableData = new ArrayList<String[]>();
    // List<String[]> newTableData = new ArrayList<String[]>();
    //
    // // Read the contents of the old table types table
    // ResultSet infoData = dbCommand.executeDbQuery("SELECT * FROM __types ORDER BY
    // OID;",
    // ccddMain.getMainFrame());
    //
    // // Step through each of the query results
    // while (infoData.next())
    // {
    // // Create an array to contain the column values
    // String[] columnValues = new String[infoData.getMetaData().getColumnCount()];
    //
    // // Step through each column in the row
    // for (int column = 0; column < infoData.getMetaData().getColumnCount();
    // column++)
    // {
    // // Add the column value to the array. Note that the first column's index in
    // // the database is 1, not 0
    // columnValues[column] = infoData.getString(column + 1);
    //
    // // Check if the value is null
    // if (columnValues[column] == null)
    // {
    // // Replace the null with a blank
    // columnValues[column] = "";
    // }
    // }
    //
    // // Add the row data to the list
    // oldTableData.add(columnValues);
    // }
    //
    // infoData.close();
    //
    // // Indicate in the log that the old data successfully loaded
    // eventLog.logEvent(SUCCESS_MSG, "__types retrieved");
    //
    // // Step through the old table types column definitions
    // for (String[] oldColumnDefn : oldTableData)
    // {
    // boolean isFound = false;
    //
    // // Create storage for the new column definition
    // String[] newColumnDefn = new
    // String[InternalTable.TABLE_TYPES.getNumColumns()];
    //
    // // Step through each of the old columns (the new table has one extra column)
    // for (int index = 0; index < TableTypesColumn.values().length - 1; index++)
    // {
    // // Copy the old columns definition to the new column definition
    // newColumnDefn[index] = oldColumnDefn[index];
    // }
    //
    // // Get the default type definition for this table type name
    // TypeDefinition typeDefn =
    // tableTypeHandler.getTypeDefinition(oldColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);
    //
    // // Check if the type exists in the default definitions
    // if (typeDefn != null)
    // {
    // // Get the index of the column
    // int column =
    // typeDefn.getColumnIndexByDbName(oldColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()]);
    //
    // // Check if the column exists in the default type definition
    // if (column != -1)
    // {
    // // Use the default definition to set the structure and pointer allowed
    // // flags
    // newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()] =
    // typeDefn.isStructureAllowed()[column]
    // ? "t"
    // : "f";
    // newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()] =
    // typeDefn.isPointerAllowed()[column]
    // ? "t"
    // : "f";
    // isFound = true;
    // }
    // }
    //
    // // Check if this column isn't in the default column definitions
    // if (!isFound)
    // {
    // // Assume that this column is valid for a structures and pointers
    // newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()] = "t";
    // newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()] = "t";
    // }
    //
    // // Add the column definition to the list
    // newTableData.add(newColumnDefn);
    // }
    //
    // // Delete the default column definitions
    // tableTypeHandler.getTypeDefinitions().clear();
    //
    // // Step through the updated table types column definitions
    // for (String[] newColumnDefn : newTableData)
    // {
    // // Get the type definition associated with this column
    // TypeDefinition typeDefn =
    // tableTypeHandler.getTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);
    //
    // // Check if the type is not defined
    // if (typeDefn == null)
    // {
    // // Create the type and add it to the list. THis creates the primary key and
    // // row index columns
    // typeDefn =
    // tableTypeHandler.createTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()],
    // newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()],,
    // new String[0][0]);
    // }
    //
    // // Check if this column definition isn't for the primary key or row index
    // since
    // // these were created previously
    // if
    // (!newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.PRIMARY_KEY.getDbName())
    // &&
    // !newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.ROW_INDEX.getDbName()))
    // {
    // // Add the column names, description, input type, and flags to the type
    // // definition
    // typeDefn.addColumn(Integer.parseInt(newColumnDefn[TableTypesColumn.INDEX.ordinal()].toString()),
    // newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].toString(),
    // newColumnDefn[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()].toString(),
    // newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()].toString(),
    // inputTypeHandler.getInputTypeByName(newColumnDefn[TableTypesColumn.INPUT_TYPE.ordinal()].toString()),
    // newColumnDefn[TableTypesColumn.ROW_VALUE_UNIQUE.ordinal()].equals("t")
    // ? true
    // : false,
    // newColumnDefn[TableTypesColumn.COLUMN_REQUIRED.ordinal()].equals("t")
    // ? true
    // : false,
    // newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()].equals("t")
    // ? true
    // : false,
    // newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()].equals("t")
    // ? true
    // : false);
    // }
    // }
    //
    // // Store the updated table type definitions in the project database and
    // change the
    // // old table types table name so that the conversion doesn't take place again
    // dbCommand.executeDbCommand(dbTable.storeTableTypesInfoTableCommand()
    // + "ALTER TABLE __types RENAME TO __types_backup;",
    // ccddMain.getMainFrame());
    //
    // // Inform the user that converting the table types completed
    // eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
    // "Table types conversion complete");
    //
    // // Reopen the database
    // dbControl.openDatabase(dbControl.getProjectName());
    // }
    // catch (Exception e)
    // {
    // // Inform the user that converting the table types table failed
    // eventLog.logFailEvent(ccddMain.getMainFrame(),
    // "Cannot convert table types table to new format; cause '"
    // + e.getMessage()
    // + "'",
    // "<html><b>Cannot convert table types table to new "
    // + "format (project database will be closed)");
    // throw new CCDDException();
    // }
    // }
    // }
    
    class PatchUtility{
        final String patchId;
        String htmlDialogMessage;
        CcddMain context;
        
        public PatchUtility(CcddMain context, String patchId, String htmlDialogMessage){
            if(context == null || patchId == null || htmlDialogMessage == null){
                throw new NullPointerException();
            }
            
            this.patchId = patchId;
            this.htmlDialogMessage = htmlDialogMessage;
            this.context = context;
        }
        
        private String getAutoPatchMessage(){
            return "CCDD: " + CcddUtilities.removeHTMLTags(htmlDialogMessage) + System.lineSeparator() +
                    "CCDD: Automatically applying patch " + patchId;
        }
        
        public boolean confirmPatchApplication() throws CCDDException{
            boolean isNotAutoPatch = !context.isAutoPatch();
            if(isNotAutoPatch){

                if(context.isGUIHidden()){
                    // The GUI is hidden and we are not automatically patching so ... we can't proceed
                    throw new CCDDException("Invalid command line combination: Please re-run with the -patch flag or with the GUI enabled (" + patchId + ")");
                }
                
                return !this.generateQuestionDialog();
            }
            System.out.println(this.getAutoPatchMessage());
            return true;
        }
        
        private boolean generateQuestionDialog() throws CCDDException{
            // Check if the user elects to not apply the patch
            return new CcddDialogHandler().showMessageDialog(context.getMainFrame(),
                    htmlDialogMessage,
                    "Apply Patch " + this.patchId, JOptionPane.QUESTION_MESSAGE,
                    DialogOption.OK_CANCEL_OPTION) != OK_BUTTON;
        }
        
        public String getUserCancelledMessage() {
            return "User elected to not install patch (" + this.patchId + ")";
        }
    }
}


