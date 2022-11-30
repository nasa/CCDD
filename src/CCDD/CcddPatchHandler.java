/**************************************************************************************************
 * /** \file CcddPatchHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class used to contain code to update the project database when a schema change is made.
 * The code is written to execute only if the database has not already been updated.
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
import static CCDD.CcddConstants.STRUCT_CMD_ARG_REF;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.TYPE_ENUM;
import static CCDD.CcddConstants.COL_VALUE;
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
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddInputTypeHandler.InputTypeReference;
import CCDD.CcddInputTypeHandler.ReferenceCheckResults;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary project database patch handler class
 *************************************************************************************************/
public class CcddPatchHandler
{
    private HashMap<String, PatchUtility> patchSet = new HashMap<String, PatchUtility>();
    private final CcddMain ccddMain;
    private final String PATCH_06012019 = "#06012019";

    /**********************************************************************************************
     * CFS Command and Data Dictionary project database patch handler class constructor. The patch
     * handler is used to integrate application changes that require alteration of the project
     * database schema. The alterations are meant to be transparent to the user; however, once
     * patched older versions of the application are no longer guaranteed to function properly and
     * may have detrimental effects
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddPatchHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Add each of the patches here
        patchSet.clear();
        String patch_06012019_dialogMsg = "<html><b>Apply patch to update the command "
                                          + "tables?<br><br></b>"
                                          + "The command table types are altered by "
                                          + "removing any argument columns and adding "
                                          + "a command argument structure reference "
                                          + "column. Every command table is modified "
                                          + "as follows: Remove the argument columns "
                                          + "and add a command argument structure "
                                          + "reference column. For each individual "
                                          + "command an argument structure is created; "
                                          + "within this structure a variable is added "
                                          + "for each of the command's arguments. An "
                                          + "argument reference structure table type "
                                          + "definition is created from which an "
                                          + "argument reference structure is created "
                                          + "that has references for each of the newly "
                                          + "create argument structures (making these "
                                          + "argument structures children of the "
                                          + "argument reference structure). The updated "
                                          + "command table's argument structure "
                                          + "reference column is populated with a "
                                          + "reference to the argument reference "
                                          + "structure. <br><b><i>Older versions of "
                                          + "CCDD will be incompatible with this "
                                          + "project database after applying the patch";
        patchSet.put(PATCH_06012019, new PatchUtility(ccddMain, PATCH_06012019, patch_06012019_dialogMsg));
    }

    /**********************************************************************************************
     * Apply patches based on the input flag
     *
     * @param isBeforeHandlerInit True if the patch must be implemented prior to initializing the
     *                            handler classes; false if the patch must be implemented after
     *                            initializing the handler classes
     *
     * @throws CCDDException If the user elects to not install the patch or an error occurs while
     *                       applying the patch
     *********************************************************************************************/
    protected void applyPatches(boolean isBeforeHandlerInit) throws CCDDException
    {
        // *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE ***
        // Patches are removed after an appropriate amount of time has been given for the patch to
        // be applied. In the event that an older database needs to be updated the old patches can
        // be found in the MRs submitted on 3/11/2021 and on 11/28/2022

        // Check if only patches that must be performed prior to handler initialization should be
        // implemented
        if (isBeforeHandlerInit)
        {
            // No current patch meets this criteria
        }
        // Only patches that must be performed after handler initialization should be implemented
        else
        {
            // Patch #10022020: Add the ENUM table type to the internal table types table and
            // update the size column of the data types internal table to have a type of text
            // rather than int
            updateTableTypeAndDataTypeTables();

            // Patch #06012019: Convert command tables to the new command table / command argument
            // structure table format
            updateCommandTables();
        }
    }

    /**********************************************************************************************
     * Backup the project database. Store the backup file in the folder specified in the program
     * preferences (or the CCDD start-up folder if no preference is specified). The file name is a
     * combination of the project's database name and the current date/time stamp
     *
     * @param dbControl Reference to the database control class
     *
     * @throws CCDDException If the project database cannot be backed up and the user elects to not
     *                       continue
     *********************************************************************************************/
    private void backupDatabase(CcddDbControlHandler dbControl) throws CCDDException
    {
        // Set the flag if the current user's password is non-blank. Depending on the
        // authentication set-up and operating system, the password may still be required by the
        // pg_dump command even if the authentication method is 'trust'
        boolean isPasswordSet = dbControl.isPasswordNonBlank();

        // Check if no password is set
        if (!isPasswordSet)
        {
            // Display the password dialog and obtain the password. Note that the user can enter a
            // blank password (which may be valid)
            CcddServerPropertyDialog dialog = new CcddServerPropertyDialog(ccddMain,
                                                                           ServerPropertyDialogType.PASSWORD);

            // Set the flag if the user selected the Okay button in the password dialog
            isPasswordSet = dialog.isPasswordSet();
        }

        // Check if the user's database password is set (either non-blank or explicitly set to
        // blank)
        if (isPasswordSet)
        {
            // Check if backing up the project database failed
            if (dbControl.backupDatabase(dbControl.getProjectName(),
                                         new FileEnvVar((ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath().isEmpty() ? ""
                                                                                                                     : ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                         + File.separator)
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension())))
            {
                // Inform the user that the backup failed and check if the user elects to not
                // continue the patch operation
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Unable to back up project database, continue?",
                                                              "Backup Project",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    throw new CCDDException("Unable to back up project database");
                }
            }
        }
    }

    /**********************************************************************************************
     * The internal data types table is modified so that the 'size' column has a type of 'text'
     * rather than a size of 'int'. The internal table types table it updated with a new table type
     * called 'ENUM'
     *********************************************************************************************/
    private void updateTableTypeAndDataTypeTables()
    {
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        // Check if the table types internal table exists. If it doesn't then the database has been
        // created but never opened. When it is opened the ENUM table type and its data field are
        // automatically created. If the table types table does exist then it is checked for the
        // ENUM table type, which is added if not present
        if (dbTable.isTableExists(InternalTable.TABLE_TYPES.getTableName(), ccddMain.getMainFrame()))
        {
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
            CcddFieldHandler fieldHandler = ccddMain.getFieldHandler();
            List<FieldInformation> fieldInfo = fieldHandler.getFieldInformation();

            // Get all existing type definitions
            List<TypeDefinition> typeDefinitions = tableTypeHandler.getTypeDefinitions();
            boolean enumFound = false;
            boolean valueFound = false;
            boolean fieldFound = false;

            // Step through each table type definition
            for (TypeDefinition typeDefn : typeDefinitions)
            {
                if (typeDefn.getName().equals(TYPE_ENUM))
                {
                    enumFound = true;

                    // Check to see if the Value column exists
                    int valueColumn = typeDefn.getColumnIndexByDbName(COL_VALUE.toLowerCase());

                    if (valueColumn != -1)
                    {
                        // if not set enumFound to false and store the current type definition
                        valueFound = true;
                    }

                    for (FieldInformation currField : fieldInfo)
                    {
                        if (currField.getOwnerName().contentEquals(CcddFieldHandler.getFieldTypeName(TYPE_ENUM))
                            && currField.getFieldName().contentEquals("Size (Bytes):"))
                        {
                            fieldFound = true;
                        }
                    }

                    break;
                }
            }

            // If the enum table type was not found, or exists but is missing the Value column
            // and/or Size data field then add what's missing
            if (!enumFound || !valueFound || !fieldFound)
            {
                List<String[]> typeAdditions = new ArrayList<String[]>(0);

                // Set the handler references in the database table command handler. This is
                // required when modifying the table type
                dbTable.setHandlers();

                if (!fieldFound)
                {
                    // Create the new field for size and add it to a list
                    FieldInformation sizeField = new FieldInformation(CcddFieldHandler.getFieldTypeName(TYPE_ENUM),
                                                                      "Size (Bytes):",
                                                                      "Size of the enumeration in bytes",
                                                                      ccddMain.getInputTypeHandler().getInputTypeByDefaultType(DefaultInputType.INTEGER),
                                                                      2,
                                                                      true,
                                                                      ApplicabilityType.ALL,
                                                                      "",
                                                                      true,
                                                                      null,
                                                                      -1);
                    fieldInfo.add(sizeField);

                    // Update the fieldHandler
                    fieldHandler.setFieldInformation(fieldInfo);

                    // Store the updated fields table
                    dbTable.storeInformationTable(InternalTable.FIELDS,
                                                  fieldHandler.getFieldDefnsFromInfo(),
                                                  null,
                                                  ccddMain.getMainFrame());
                }

                // ENUM type already exists, but the Value column is missing
                if (!valueFound)
                {
                    // Define the new data type column and add it to the typeAdditions list
                    String[] newValueColumn = {COL_VALUE, DefaultInputType.INTEGER.getInputName()};
                    typeAdditions.add(newValueColumn);
                }

                // Check if the ENUM type was not found
                if (!enumFound || !valueFound)
                {
                    // Add the table type definition, 0 added as it is not a command argument
                    tableTypeHandler.createReplaceTypeDefinition(TYPE_ENUM,
                                                                 "0ENUM table",
                                                                 DefaultColumn.getDefaultColumnDefinitions(TYPE_ENUM,
                                                                                                           false));

                    // Update the table type handler for dbTable before calling modifyTableType
                    dbTable.setTableTypeHandler();
                }

                // Add the new table type to the project database
                dbTable.modifyTableType(TYPE_ENUM,
                                        fieldInfo,
                                        OverwriteFieldValueType.NONE,
                                        typeAdditions,
                                        new ArrayList<String[]>(0),
                                        new ArrayList<String[]>(0),
                                        false,
                                        null,
                                        null,
                                        null,
                                        null);
            }
        }
    }

    /**********************************************************************************************
     * The command table types are altered by removing any argument columns and adding a command
     * argument structure reference column. Every command table is modified as follows: Remove the
     * argument columns and add a command argument structure reference column. For each individual
     * command an argument structure is created; within this structure a variable is added for each
     * of the command's arguments. An argument reference structure table type definition is created
     * from which an argument reference structure is created that has references for each of the
     * newly create argument structures (making these argument structures children of the argument
     * reference structure). The updated command table's argument structure reference column is
     * populated with a reference to the argument reference structure. Older versions of CCDD are
     * not compatible with the project database after applying this patch
     *
     * @throws CCDDException If the user elects to not install the patch or an error occurs while
     *                       applying the patch
     *********************************************************************************************/
    private void updateCommandTables() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        try
        {
            boolean isPatched = false;
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
            CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
            {
                // Check if the table represents a command
                if (typeDefn.isCommand())
                {
                    // Stop searching since the database is already updated
                    isPatched = true;
                    break;
                }
            }

            // Check if the patch hasn't been applied
            if (!isPatched)
            {

                if (patchSet.get(PATCH_06012019).confirmPatchApplication() == false)
                {
                    throw new CCDDException(patchSet.get(PATCH_06012019).getUserCancelledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Create a save point in case an error occurs while applying the patch
                dbCommand.createSavePoint(ccddMain.getMainFrame());

                // Simple table to store temporary information about the database table type
                class DbTableType
                {
                    DbTableType(String name, String basedUpon, boolean isRate)
                    {
                        this.name = name;
                        this.basedUpon = basedUpon;
                        this.isRateEnabled = isRate;
                        // The 1 is appended to reflect that the table is a command argument
                        this.description = "1Command argument structure reference table definition";
                    }

                    DbTableType(String name, boolean isRate)
                    {
                        this(name, name, isRate);
                    }

                    public String name; // Name of the structure
                    public String basedUpon; // Structure to base upon (copy)
                    public boolean isRateEnabled; // Is the rate enabled for this structure?
                    public String description; // The description
                };

                // These pairs contain either a conversion from one type to another or the creation
                // of a new type
                List<DbTableType> structures = new ArrayList<>();

                // Add Structure: Cmd Arg Ref and base it off of the Structure table. Don't include
                // the rate variable
                structures.add(new DbTableType(CcddConstants.STRUCT_CMD_ARG_REF,
                                               CcddConstants.TYPE_STRUCTURE,
                                               false));

                // Add the Command, do not include the rate
                structures.add(new DbTableType(CcddConstants.TYPE_COMMAND, false));

                for (DbTableType struct : structures)
                {

                    boolean isNamedCommand = struct.name == CcddConstants.TYPE_COMMAND;
                    TypeDefinition tble = tableTypeHandler.getTypeDefinition(struct.name);
                    boolean isInDbAlready = tble != null;
                    boolean isCommandAlreadyThere = isNamedCommand && isInDbAlready;

                    // Some databases will already have this defined so do not attempt to add it
                    // again Other databases will need the command type to be added or modified
                    if (isCommandAlreadyThere)
                    {
                        continue;
                    }

                    // Pull out the column definitions for this named entry
                    Object[][] td = DefaultColumn.getDefaultColumnDefinitions(struct.basedUpon,
                                                                              struct.isRateEnabled);

                    // Remove this structure (it if exists)
                    tableTypeHandler.removeTypeDefinition(struct.name);

                    // Add it (this will ensure that the type is correct and up to date)
                    TypeDefinition cmdArgTableType = tableTypeHandler.addTypeDefinition(struct.name,
                                                                                        struct.description,
                                                                                        td);

                    StringBuilder cmdRefInpTypeCmd = new StringBuilder("");
                    List<TypeDefinition> newCommandArgTypes = new ArrayList<TypeDefinition>();
                    int cmdArgStructSeq = 1;

                    // Get the references in the table type and data field internal tables that use
                    // the command reference input type. If a command name, code, or argument is
                    // changed or deleted then the tables and fields may require updating
                    ReferenceCheckResults cmdRefChkResults = inputTypeHandler.getInputTypeReferences(DefaultInputType.COMMAND_REFERENCE,
                                                                                                     ccddMain.getMainFrame());

                    // Get the number of table type definitions before any new ones are added
                    int numTypes = tableTypeHandler.getTypeDefinitions().size();

                    // Step through each existing table type definition
                    for (int typeIndex = 0; typeIndex < numTypes; typeIndex++)
                    {
                        // Get the reference to the existing table type definition
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinitions().get(typeIndex);

                        // Check if this isn't the command argument structure reference table type
                        // just added
                        if (!typeDefn.getName().equals(STRUCT_CMD_ARG_REF))
                        {
                            // Update the type's definition to include the flag that indicates if
                            // the type represents a command argument structure
                            typeDefn.setDescription("0" + typeDefn.getColumnToolTips()[0]);
                        }

                        // Get the column indices for the command name and code
                        int cmdNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
                        int cmdCodeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);

                        // Check if the type represents a command (old format; minus the command
                        // argument structure column)
                        if (cmdNameIndex != -1 && cmdCodeIndex != -1 && !typeDefn.isStructure())
                        {
                            List<Integer[]> cmdArgGroups = new ArrayList<Integer[]>();
                            List<Integer> commandColumns = new ArrayList<Integer>();
                            List<String> commandTableNames = new ArrayList<String>();
                            List<String> commandTableDescriptions = new ArrayList<String>();
                            List<String> commands = new ArrayList<String>();
                            String command = "";

                            // Read the contents of the command table type
                            ResultSet typesData = dbCommand.executeDbQuery(new StringBuilder("SELECT * FROM ").append(InternalTable.TABLE_TYPES.getTableName())
                                                                                                              .append(" WHERE ")
                                                                                                              .append(TableTypesColumn.TYPE_NAME.getColumnName())
                                                                                                              .append(" = '")
                                                                                                              .append(typeDefn.getName())
                                                                                                              .append("' ORDER BY ")
                                                                                                              .append(TableTypesColumn.INDEX.getColumnName())
                                                                                                              .append(";"),
                                                                                                              ccddMain.getMainFrame());

                            int colIndex = -1;
                            int cmdIndex = 0;
                            List<Object[]> cmdColDefnData = new ArrayList<Object[]>();
                            boolean keepGoing = true;
                            boolean readNext = true;

                            // Step through each of the query results
                            while (keepGoing)
                            {
                                // Check if the next result entry should be read (if the last entry
                                // read below is an argument name then the current entry applies)
                                if (readNext)
                                {
                                    // Get the next entry
                                    keepGoing = typesData.next();

                                    // Check if this is the last result entry
                                    if (!keepGoing)
                                    {
                                        // Stop processing this command table type
                                        break;
                                    }

                                    // Increment the grouped column index
                                    colIndex++;
                                }

                                // Set the flag for the next loop
                                readNext = true;

                                // Check if this is not the primary key or index columns
                                if (typesData.getInt(TableTypesColumn.INDEX.ordinal() + 1) >= NUM_HIDDEN_COLUMNS)
                                {
                                    // Check if the column expects a command argument name
                                    if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1).equals("Argument name"))
                                    {
                                        List<Integer> argColGroup = new ArrayList<Integer>();

                                        // Change the input type for the argument name to that for
                                        // a variable so that it's recognized
                                        typeDefn.getInputTypesList()
                                                .set(colIndex,
                                                     inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.VARIABLE_NAME.getInputType()));

                                        // Add the argument name column index to the list of
                                        // argument columns
                                        argColGroup.add(colIndex);

                                        // Get the column input types
                                        InputType[] inputTypes = typeDefn.getInputTypes();

                                        // Step through the remaining columns defined for this
                                        // table's type
                                        for (colIndex++; colIndex < typeDefn.getColumnCountDatabase(); colIndex++)
                                        {
                                            typesData.next();

                                            // Check if the column expects a command argument name
                                            if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1).equals("Argument name"))
                                            {
                                                // Change the input type for the argument name to
                                                // that for a variable so that it's recognized
                                                typeDefn.getInputTypesList()
                                                        .set(colIndex,
                                                             inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.VARIABLE_NAME.getInputType()));
                                                readNext = false;
                                                break;
                                            }

                                            // Check if the column expects a primitive data type
                                            if (typesData.getString(TableTypesColumn.INPUT_TYPE.ordinal() + 1).equals(DefaultInputType.PRIMITIVE.getInputName()))
                                            {
                                                // Change the input type for the primitive data
                                                // type to that for a primitive & structure data
                                                // type
                                                typeDefn.getInputTypesList()
                                                        .set(colIndex,
                                                             inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.DATA_TYPE.getInputType()));
                                            }
                                            // Check if the column expects a command argument name
                                            else if (typesData.getString(TableTypesColumn.COLUMN_NAME_DB.ordinal() + 1)
                                                              .contains("description"))
                                            {
                                                // Change the input type for the argument name to
                                                // that for a variable so that it's recognized
                                                typeDefn.getInputTypesList().set(colIndex,
                                                                                 inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.DESCRIPTION_STRUCT.getInputType()));
                                            }
                                            else if (typesData.getString(TableTypesColumn.COLUMN_NAME_DB.ordinal() + 1).contains("units"))
                                            {
                                                // Change the input type for the argument name to
                                                // that for a variable so that it's recognized
                                                typeDefn.getInputTypesList().set(colIndex,
                                                                                 inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.UNITS.getInputType()));
                                            }

                                            // Check that this isn't the command name or command
                                            // code column (these are never part of an argument
                                            // grouping)
                                            if (!inputTypes[colIndex].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.COMMAND_NAME))
                                                && !inputTypes[colIndex].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.COMMAND_CODE)))
                                            {
                                                // Add the command argument column index to the
                                                // argument list
                                                argColGroup.add(colIndex);
                                            }
                                            // This column belongs to the command itself and not an
                                            // argument
                                            else
                                            {
                                                cmdColDefnData.add(typeDefn.getData()[cmdIndex]);
                                                commandColumns.add(colIndex);
                                                cmdIndex++;
                                            }
                                        }

                                        // Add the argument column group to the list
                                        cmdArgGroups.add(argColGroup.toArray(new Integer[0]));
                                    }
                                    // This column belongs to the command itself
                                    else
                                    {
                                        cmdColDefnData.add(typeDefn.getData()[cmdIndex]);
                                        commandColumns.add(colIndex);
                                        cmdIndex++;
                                    }
                                }
                            }

                            typesData.close();

                            // If it is a new command, these fields may need to be added
                            if (!isCommandAlreadyThere)
                            {
                                boolean isCommandNameDefined = false;
                                for (Object[] v : cmdColDefnData)
                                {
                                    if (v[1].equals(DefaultColumn.COMMAND_NAME.getName()))
                                        isCommandNameDefined = true;
                                }
                                // The command name test has failed so add the below three fields
                                if (!isCommandNameDefined)
                                {
                                    // Add the new command name structure column definition
                                    cmdColDefnData.add(new Object[] {0,
                                                                     DefaultColumn.COMMAND_NAME.getName(),
                                                                     DefaultColumn.COMMAND_NAME.getDescription(),
                                                                     DefaultColumn.COMMAND_NAME.getInputType().getInputName(),
                                                                     DefaultColumn.COMMAND_NAME.isRowValueUnique(),
                                                                     DefaultColumn.COMMAND_NAME.isProtected(),
                                                                     DefaultColumn.COMMAND_NAME.isStructureAllowed(),
                                                                     DefaultColumn.COMMAND_NAME.isPointerAllowed()});

                                    // Add the new command code structure column definition
                                    cmdColDefnData.add(new Object[] {0,
                                                                     DefaultColumn.COMMAND_CODE.getName(),
                                                                     DefaultColumn.COMMAND_CODE.getDescription(),
                                                                     DefaultColumn.COMMAND_CODE.getInputType().getInputName(),
                                                                     DefaultColumn.COMMAND_CODE.isRowValueUnique(),
                                                                     DefaultColumn.COMMAND_CODE.isProtected(),
                                                                     DefaultColumn.COMMAND_CODE.isStructureAllowed(),
                                                                     DefaultColumn.COMMAND_CODE.isPointerAllowed()});

                                    // Add the new command description structure column definition
                                    cmdColDefnData.add(new Object[] {0,
                                                                     DefaultColumn.DESCRIPTION_CMD.getName(),
                                                                     DefaultColumn.DESCRIPTION_CMD.getDescription(),
                                                                     DefaultColumn.DESCRIPTION_CMD.getInputType().getInputName(),
                                                                     DefaultColumn.DESCRIPTION_CMD.isRowValueUnique(),
                                                                     DefaultColumn.DESCRIPTION_CMD.isProtected(),
                                                                     DefaultColumn.DESCRIPTION_CMD.isStructureAllowed(),
                                                                     DefaultColumn.DESCRIPTION_CMD.isPointerAllowed()});
                                }
                            }
                            // Always add this field Add the new command argument structure column
                            // definition
                            cmdColDefnData.add(new Object[] {0,
                                                             DefaultColumn.COMMAND_ARGUMENT.getName(),
                                                             DefaultColumn.COMMAND_ARGUMENT.getDescription(),
                                                             DefaultColumn.COMMAND_ARGUMENT.getInputType().getInputName(),
                                                             DefaultColumn.COMMAND_ARGUMENT.isRowValueUnique(),
                                                             DefaultColumn.COMMAND_ARGUMENT.isProtected(),
                                                             DefaultColumn.COMMAND_ARGUMENT.isStructureAllowed(),
                                                             DefaultColumn.COMMAND_ARGUMENT.isPointerAllowed()});

                            // Create the command table type with the new command argument column
                            // and without the individual argument columns. Adjust the name so
                            // there is no conflict; the name is restored when the original table
                            // type is replaced by this one
                            TypeDefinition newCmdTypeDefn = tableTypeHandler.createTypeDefinition(typeDefn.getName()
                                                                                                  + "@",
                                                                                                  typeDefn.getColumnToolTips()[0],
                                                                                                  cmdColDefnData.toArray(new Object[0][0]));

                            List<InputType> defaultInputTypes = new ArrayList<InputType>();

                            // Step through the default columns
                            for (DefaultColumn defCol : DefaultColumn.values())
                            {
                                // Check if the current column's type is for a structure (excluding
                                // the rate column)
                                if (defCol.getTableType().equals(TYPE_STRUCTURE)
                                    && !defCol.getInputType().equals(DefaultInputType.RATE))
                                {
                                    // Add the column input type to the list
                                    defaultInputTypes.add(inputTypeHandler.getInputTypeByDefaultType(defCol.getInputType()));
                                }
                            }

                            int counter = 0;
                            // Step through each table of this command type
                            for (String commandTableName : dbTable.getAllTablesOfType(typeDefn.getName(),
                                                                                      null,
                                                                                      ccddMain.getMainFrame()))
                            {
                                // Load the command table's information
                                TableInfo cmdTableInfo = dbTable.loadTableData(commandTableName,
                                                                               true, // Load description?
                                                                               false, // Load columnOrder?
                                                                               false,
                                                                               ccddMain.getMainFrame());

                                // Check if an error occurred loading the command table
                                if (cmdTableInfo.isErrorFlag())
                                {
                                    throw new CCDDException("Cannot load command table " + commandTableName);
                                }

                                TypeDefinition newCmdArgStructType = null;
                                List<Object[]> typeData = new ArrayList<Object[]>();
                                List<String> argStructRefs = new ArrayList<String>();

                                // Store the command table name and description
                                commandTableNames.add(commandTableName);
                                commandTableDescriptions.add(cmdTableInfo.getDescription());

                                // Check if the table has commands defined
                                if (cmdTableInfo.getData().size() != 0)
                                {
                                    List<String[]> argNamesAndNumber = new ArrayList<String[]>();

                                    // Create the command argument structure reference table used
                                    // by all commands in this command table
                                    List<Object[]> argRefTableData = new ArrayList<Object[]>();
                                    String cmdArgRefTableName = "CmdArgRef_" + commandTableName;

                                    // Create a command argument structure table based on the newly
                                    // create type. All of the command arguments are stored in this
                                    // table
                                    if (dbTable.createTable(new String[] {cmdArgRefTableName},
                                                            "Command "
                                                            + commandTableName
                                                            + " argument structure references",
                                                            STRUCT_CMD_ARG_REF,
                                                            true,
                                                            (counter == dbTable.getAllTablesOfType(typeDefn.getName(),
                                                                                                   null,
                                                                                                   ccddMain.getMainFrame()).size() - 1),
                                                            ccddMain.getMainFrame()))
                                    {
                                        throw new CCDDException("Cannot create command argument structure reference table");
                                    }

                                    // Load the command argument structure table
                                    TableInfo cmdArgTableInfo = dbTable.loadTableData(cmdArgRefTableName,
                                                                                      false,
                                                                                      false,
                                                                                      false,
                                                                                      ccddMain.getMainFrame());

                                    // Check if an error occurred loading the command argument
                                    // structure table
                                    if (cmdArgTableInfo.isErrorFlag())
                                    {
                                        throw new CCDDException("Cannot load command argument structure table");
                                    }

                                    // Create the command argument structure table type used by
                                    // all commands in this command table

                                    // Add the default structure column definitions to the list
                                    // (minus the rate column)
                                    typeData.addAll(Arrays.asList(DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE,
                                                                                                            false)));
                                    int typeDataIndex = typeData.size();

                                    // Step through each command argument column grouping
                                    for (Integer[] cmdArgGroup : cmdArgGroups)
                                    {
                                        int numEnum = 0;
                                        int numMin = 0;
                                        int numMax = 0;

                                        // Step through each column in this argument group
                                        for (Integer argCol : cmdArgGroup)
                                        {
                                            boolean isAdd = false;
                                            String columnName = null;
                                            String description = null;
                                            boolean isRowValueUnique = false;
                                            boolean isRequired = false;
                                            boolean isStructureAllowed = false;
                                            boolean isPointerAllowed = false;
                                            DefaultColumn defCol = null;

                                            // Get the input type reference to shorten subsequent
                                            // calls
                                            InputType inputType = typeDefn.getInputTypes()[argCol];

                                            // Check if this is the enumeration input type
                                            if (inputType.getInputName().equals(DefaultInputType.ENUMERATION.getInputName()))
                                            {
                                                numEnum++;

                                                // Check if this isn't the first enumeration column
                                                // (the first is a default)
                                                if (numEnum > 1)
                                                {
                                                    isAdd = true;
                                                    defCol = DefaultColumn.ENUMERATION;
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                }
                                            }
                                            // Check if this is the minimum input type
                                            else if (inputType.getInputName().equals(DefaultInputType.MINIMUM.getInputName()))
                                            {
                                                numMin++;

                                                // Check if this isn't the first minimum column
                                                // (the first is a default)
                                                if (numMin > 1)
                                                {
                                                    isAdd = true;
                                                    defCol = DefaultColumn.MINIMUM;
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                }
                                            }
                                            // Check if this is the maximum input type
                                            else if (inputType.getInputName().equals(DefaultInputType.MAXIMUM.getInputName()))
                                            {
                                                numMax++;

                                                // Check if this isn't the first maximum column
                                                // (the first is a default)
                                                if (numMax > 1)
                                                {
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
                                                     && !typeDefn.getColumnNamesDatabase()[argCol].contains("units"))
                                            {
                                                isAdd = true;
                                                columnName = typeDefn.getColumnNamesUser()[argCol];
                                                description = typeDefn.getColumnToolTips()[argCol];
                                                isRowValueUnique = typeDefn.isRowValueUnique()[argCol];
                                                isRequired = typeDefn.isRequired()[argCol];
                                                isStructureAllowed = typeDefn.isStructureAllowed()[argCol];
                                                isPointerAllowed = typeDefn.isPointerAllowed()[argCol];

                                                // If the name is "Arg 1 Access", "Arg 1 Default
                                                // Value" or "Arg 1 Verification Test Num" it will
                                                // be changed to "Access", "Default Value" and
                                                // "Verification Test Num". If it is Arg 2 or above
                                                // it will not be added to the new structure at
                                                // all. Each argument is now placed in its own
                                                // instance of a Structure: Cmd Arg table so you
                                                // will never need an Arg 2 or above value in any
                                                // given table
                                                if (columnName.equals("Arg 1 Access"))
                                                {
                                                    columnName = "Access";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;
                                                }
                                                else if (columnName.equals("Arg 1 Default Value"))
                                                {
                                                    columnName = "Default Value";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;
                                                }
                                                else if (columnName.equals("Arg 1 Verification Test Num"))
                                                {
                                                    columnName = "Verification Test Num";
                                                    defCol = DefaultColumn.COMMAND_ARGUMENT;

                                                    // remove the argument number from the
                                                    // description
                                                    description = description.substring(0, 17)
                                                                  + description.substring(18);
                                                }
                                                else if (columnName.substring(0, 3).equals("Arg"))
                                                {
                                                    // If it is Arg 2 or above it will not be added
                                                    // to the new structure at all
                                                    if (Integer.parseInt(columnName.substring(4, 5)) > 1)
                                                    {
                                                        isAdd = false;
                                                    }
                                                }
                                            }

                                            // Check if a column needs to be added
                                            if (isAdd)
                                            {
                                                // Check if the column has the same definition as a
                                                // default column
                                                if (defCol != null)
                                                {
                                                    // Check if no column name is set
                                                    if (columnName == null)
                                                    {
                                                        // Use the default column name
                                                        columnName = defCol.getName();
                                                    }

                                                    // Check if no column description is set
                                                    if (description == null)
                                                    {
                                                        // Use the default column description
                                                        description = defCol.getDescription();
                                                    }

                                                    // Use the default column flags
                                                    isRowValueUnique = defCol.isRowValueUnique();
                                                    isStructureAllowed = defCol.isStructureAllowed();
                                                    isPointerAllowed = defCol.isPointerAllowed();
                                                }
                                                // The column doesn't have the same definition as a
                                                // default column
                                                else
                                                {
                                                    // Use the column definition from the command
                                                    // table type
                                                    columnName = typeDefn.getColumnNamesUser()[argCol];
                                                    description = typeDefn.getColumnToolTips()[argCol];
                                                    isRowValueUnique = typeDefn.isRowValueUnique()[argCol];
                                                    isRequired = typeDefn.isRequired()[argCol];
                                                    isStructureAllowed = typeDefn.isStructureAllowed()[argCol];
                                                    isPointerAllowed = typeDefn.isPointerAllowed()[argCol];
                                                }

                                                // Add the command argument structure column
                                                // definition
                                                typeData.add(new Object[] {typeDataIndex,
                                                                           columnName,
                                                                           description,
                                                                           inputType.getInputName(),
                                                                           isRowValueUnique,
                                                                           isRequired,
                                                                           isStructureAllowed,
                                                                           isPointerAllowed});
                                                typeDataIndex++;
                                            }
                                        }
                                    }

                                    // Determine if a command argument structure table type already
                                    // exists that matches the one for this command. If so, then
                                    // use it for defining the command argument structures;
                                    // otherwise create a new table type and use it
                                    newCmdArgStructType = null;

                                    for (TypeDefinition cmdArgStructType : newCommandArgTypes)
                                    {
                                        // Check if the type definitions have the same number of
                                        // columns
                                        if (cmdArgStructType.getColumnCountVisible() == typeData.size())
                                        {
                                            boolean isFound = false;

                                            // Step through each column definition in the new
                                            // command argument structure table type
                                            for (Object[] newColDefn : typeData)
                                            {
                                                isFound = false;

                                                // Step through each column definition in the
                                                // existing command argument structure table type
                                                for (Object[] colDefn : cmdArgStructType.getData())
                                                {
                                                    // Check if the column definitions match
                                                    if (CcddUtilities.isArraySetsEqual(colDefn, newColDefn))
                                                    {
                                                        // Set the flag to indicate a matching
                                                        // column exists and stop searching
                                                        isFound = true;
                                                        break;
                                                    }
                                                }

                                                // Check if no matching column exists
                                                if (!isFound)
                                                {
                                                    // This type definition isn't a match; stop
                                                    // searching
                                                    break;
                                                }
                                            }

                                            // Check if the column definitions for this type
                                            // definition match the new type
                                            if (isFound)
                                            {
                                                // Store the reference to the existing type
                                                // definition and stop searching
                                                newCmdArgStructType = cmdArgStructType;
                                                break;
                                            }
                                        }
                                    }

                                    // Check if no matching type definition exists
                                    if (newCmdArgStructType == null)
                                    {
                                        // Create a new command argument structure table type for
                                        // this command table
                                        newCmdArgStructType = tableTypeHandler.createReplaceTypeDefinition("Structure: Cmd Arg " + cmdArgStructSeq,
                                                                                                           "1Command argument structure table definition",
                                                                                                           typeData.toArray(new Object[0][0]));
                                        newCommandArgTypes.add(newCmdArgStructType);
                                        cmdArgStructSeq++;
                                    }

                                    int missingNameSeq = 1;

                                    // Step through each command defined in this command table
                                    for (int cmdRow = 0; cmdRow < cmdTableInfo.getData().size(); cmdRow++)
                                    {
                                        String argNameString = "";

                                        // Create the command argument structure table for this
                                        // command
                                        Object[] cmdArgRef = new Object[cmdArgTableType.getColumnCountDatabase()];
                                        Arrays.fill(cmdArgRef, "");

                                        // Get the command name
                                        String cmdName = cmdTableInfo.getData().get(cmdRow)[cmdNameIndex].toString();

                                        // Check if no command name is defined
                                        if (cmdName.isEmpty())
                                        {
                                            // Create a command name
                                            cmdName = commandTableName + "_missing_cmd_name_" + missingNameSeq;
                                            cmdTableInfo.getData().get(cmdRow)[cmdNameIndex] = cmdName;
                                            missingNameSeq++;
                                        }

                                        // Create a prototype for the command argument structure
                                        // table
                                        if (dbTable.createTable(new String[] {cmdName},
                                                                "Command argument structure",
                                                                newCmdArgStructType.getName(),
                                                                true,
                                                                (cmdRow == cmdTableInfo.getData().size() - 1),
                                                                ccddMain.getMainFrame()))
                                        {
                                            throw new Exception("Cannot create command argument structure table");
                                        }

                                        // Build the path to the argument's structure variable. The
                                        // path is in the format root,dataType.variableName where
                                        // root = the command argument reference structure table
                                        // name, dataType = the command argument structure
                                        // prototype table name, and variableName is the command
                                        // name
                                        String argStructRef = cmdArgTableInfo.getRootTable()
                                                              + ","
                                                              + cmdName
                                                              + "."
                                                              + cmdName;

                                        // Add the argument structure reference to the list
                                        argStructRefs.add(argStructRef);

                                        // Add the argument variable and structure to the argument
                                        // structure reference table
                                        cmdArgRef[cmdArgTableType.getColumnIndexByInputType(DefaultInputType.VARIABLE)] = cmdName;
                                        cmdArgRef[cmdArgTableType.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)] = cmdName;

                                        // Load the command argument structure table's information
                                        TableInfo argTableInfo = dbTable.loadTableData(argStructRef,
                                                                                       false,
                                                                                       false,
                                                                                       false,
                                                                                       ccddMain.getMainFrame());

                                        // Check if an error occurred loading the command argument
                                        // structure table
                                        if (argTableInfo.isErrorFlag())
                                        {
                                            throw new CCDDException("Cannot load command argument structure table");
                                        }

                                        List<Object[]> argTableData = new ArrayList<Object[]>();

                                        // Step through each command argument column grouping
                                        for (Integer[] cmdArgGroup : cmdArgGroups)
                                        {
                                            Object[] cmdArg = new Object[newCmdArgStructType.getColumnCountDatabase()];
                                            Arrays.fill(cmdArg, "");

                                            // Step through each column in this argument group
                                            for (Integer argCol : cmdArgGroup)
                                            {
                                                // Copy the command table's argument column value
                                                // into the new command argument structure table
                                                // row. The input type determines the column to
                                                // which the value belongs
                                                try
                                                {
                                                    cmdArg[newCmdArgStructType.getColumnIndexByInputType(typeDefn.getInputTypes()[argCol])] = cmdTableInfo.getData().get(cmdRow)[argCol];
                                                }
                                                catch (Exception e)
                                                {
                                                    // this input type no longer exists and the
                                                    // function returns a -1. This will catch the
                                                    // -1 and allow the function to continue
                                                }

                                                // Check if this is the argument variable name and
                                                // isn't blank
                                                if (!cmdTableInfo.getData().get(cmdRow)[argCol].toString().isEmpty()
                                                    && typeDefn.getInputTypes()[argCol].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.VARIABLE)))
                                                {
                                                    // Add the argument variable name to the string
                                                    argNameString += cmdTableInfo.getData().get(cmdRow)[argCol] + ", ";
                                                }
                                            }

                                            boolean hasData = false;

                                            // Step through each cell in the row
                                            for (Object cell : cmdArg)
                                            {
                                                // Check if the cell isn't empty
                                                if (!cell.toString().isEmpty())
                                                {
                                                    // Set the flag to indicate a non-empty cell
                                                    // and stop searching
                                                    hasData = true;
                                                    break;
                                                }
                                            }

                                            // Check if the row isn't empty
                                            if (hasData)
                                            {
                                                argTableData.add(cmdArg);
                                            }

                                            // If a command has an array size greater than 1 then
                                            // add all array indices
                                            if (!cmdArg[DefaultColumn.ARRAY_SIZE.ordinal()].toString().isEmpty())
                                            {
                                                Object[] cmdArgArrayMember;
                                                for (int i = 0; i < Integer.parseInt(cmdArg[DefaultColumn.ARRAY_SIZE.ordinal()].toString()); i++)
                                                {
                                                    cmdArgArrayMember = cmdArg.clone();
                                                    cmdArgArrayMember[DefaultColumn.VARIABLE_NAME.ordinal()] = cmdArgArrayMember[DefaultColumn.VARIABLE_NAME.ordinal()].toString()
                                                                                                               + "["
                                                                                                               + Integer.toString(i)
                                                                                                               + "]";
                                                    argTableData.add(cmdArgArrayMember);
                                                }
                                            }
                                        }

                                        // Create the command argument structure table

                                        // Add the command argument name string and number to the
                                        // list
                                        argNamesAndNumber.add(new String[] {CcddUtilities.removeTrailer(argNameString, ", "),
                                                                            String.valueOf(argTableData.size())});

                                        // Set the argument structure's table data
                                        argTableInfo.setData(argTableData.toArray(new Object[0][0]));

                                        // Build the string of the argument structure table's
                                        // column names
                                        String argColumnNames = "";

                                        // Step through each column name in the argument structure
                                        // table
                                        for (String argColumnName : newCmdArgStructType.getColumnNamesDatabaseQuoted())
                                        {
                                            // Add the column name
                                            argColumnNames += argColumnName + ", ";
                                        }

                                        argColumnNames = CcddUtilities.removeTrailer(argColumnNames, ", ");
                                        String argCommand = "";

                                        // Step through each row in the argument structure table's
                                        // data
                                        for (int argRow = 0; argRow < argTableInfo.getData().size(); argRow++)
                                        {
                                            // Begin building the command to populate the argument
                                            // structure table
                                            argCommand += "INSERT INTO "
                                                          + cmdName.toLowerCase()
                                                          + " ("
                                                          + argColumnNames
                                                          + ") VALUES ("
                                                          + (argRow + 1)
                                                          + ", "
                                                          + (argRow + 1)
                                                          + ", ";

                                            // Step through each column in the argument structure
                                            // table's data
                                            for (int argColumn = NUM_HIDDEN_COLUMNS; argColumn < argTableInfo.getData().get(argRow).length; argColumn++)
                                            {
                                                // Store the argument structure value in the
                                                // command
                                                argCommand += "'"
                                                              + argTableInfo.getData().get(argRow)[argColumn]
                                                              + "', ";
                                            }

                                            argCommand = CcddUtilities.removeTrailer(argCommand, ", ") + "); ";
                                        }

                                        // Store the data into the argument structure database
                                        // table
                                        dbCommand.executeDbCommand(new StringBuilder(argCommand),
                                                                   ccddMain.getMainFrame());

                                        // Check if the row contains data
                                        if (!cmdArgRef[cmdArgTableType.getColumnIndexByInputType(DefaultInputType.VARIABLE)].toString().isEmpty())
                                        {
                                            argRefTableData.add(cmdArgRef);
                                        }
                                    }

                                    // Update the command table

                                    // Build the string of the updated command table's column names
                                    String cmdColumnNames = "";

                                    // Step through each column name in the updated command table
                                    for (String cmdColumnName : newCmdTypeDefn.getColumnNamesDatabaseQuoted())
                                    {
                                        // Add the column name
                                        cmdColumnNames += cmdColumnName + ", ";
                                    }

                                    cmdColumnNames = CcddUtilities.removeTrailer(cmdColumnNames, ", ");

                                    // Step through each row in the original command table's data
                                    for (int cmdRow = 0; cmdRow < cmdTableInfo.getData().size(); cmdRow++)
                                    {
                                        // Begin building the command to populate the command table
                                        command += "INSERT INTO "
                                                   + commandTableName.toLowerCase()
                                                   + " ("
                                                   + cmdColumnNames + ") VALUES ("
                                                   + cmdTableInfo.getData().get(cmdRow)[DefaultColumn.PRIMARY_KEY.ordinal()]
                                                   + ", " + cmdTableInfo.getData().get(cmdRow)[DefaultColumn.ROW_INDEX.ordinal()]
                                                   + ", ";

                                        // Step through each column in the original command table's
                                        // data
                                        for (int cmdColumn = NUM_HIDDEN_COLUMNS; cmdColumn < cmdTableInfo.getData().get(cmdRow).length; cmdColumn++)
                                        {
                                            // Check if this column belongs to the command versus
                                            // to an argument
                                            if (commandColumns.contains(cmdColumn))
                                            {
                                                // Store the command value in the command
                                                command += CcddDbTableCommandHandler.delimitText(cmdTableInfo.getData().get(cmdRow)[cmdColumn])
                                                           + ", ";
                                            }
                                        }

                                        // Store the variable path for the command
                                        command += "'" + argStructRefs.get(cmdRow) + "'); ";

                                    }

                                    // Build the command argument reference table

                                    // Set the argument structure reference's table data
                                    cmdArgTableInfo.setData(argRefTableData.toArray(new Object[0][0]));

                                    // Build the string of the argument structure reference table's
                                    // column names
                                    String argRefColumnNames = "";

                                    // Step through each column name in the argument structure
                                    // reference table
                                    for (String argRefColumnName : cmdArgTableType.getColumnNamesDatabaseQuoted())
                                    {
                                        // Add the column name
                                        argRefColumnNames += argRefColumnName + ", ";
                                    }

                                    argRefColumnNames = CcddUtilities.removeTrailer(argRefColumnNames, ", ");
                                    String argRefCommand = "";

                                    // Step through each row in the argument structure table's data
                                    for (int argRefRow = 0; argRefRow < cmdArgTableInfo.getData().size(); argRefRow++)
                                    {
                                        // Begin building the command to populate the argument
                                        // structure reference table
                                        argRefCommand += "INSERT INTO "
                                                         + cmdArgTableInfo.getRootTable().toLowerCase()
                                                         + " ("
                                                         + argRefColumnNames
                                                         + ") VALUES ("
                                                         + (argRefRow + 1)
                                                         + ", "
                                                         + (argRefRow + 1)
                                                         + ", ";

                                        // Step through each column in the argument structure
                                        // reference table's data
                                        for (int argRefColumn = NUM_HIDDEN_COLUMNS; argRefColumn < cmdArgTableInfo.getData().get(argRefRow).length; argRefColumn++)
                                        {
                                            // Store the argument structure reference value in the
                                            // command
                                            argRefCommand += "'"
                                                             + cmdArgTableInfo.getData().get(argRefRow)[argRefColumn]
                                                             + "', ";
                                        }

                                        argRefCommand = CcddUtilities.removeTrailer(argRefCommand, ", ") + "); ";
                                    }

                                    // Store the data into the argument structure reference table
                                    dbCommand.executeDbCommand(new StringBuilder(argRefCommand),
                                                               ccddMain.getMainFrame());

                                    // Update any table cells or data fields with the command

                                    // Step through each row in the command table's data
                                    for (int cmdRow = 0; cmdRow < argNamesAndNumber.size(); cmdRow++)
                                    {
                                        // Build the original and new command references
                                        String oldCmdRef = cmdTableInfo.getData().get(cmdRow)[cmdNameIndex]
                                                           + " (code: "
                                                           + cmdTableInfo.getData().get(cmdRow)[cmdCodeIndex]
                                                           + ", owner: "
                                                           + cmdTableInfo.getTablePath()
                                                           + ", args: "
                                                           + argNamesAndNumber.get(cmdRow)[1]
                                                                   + ")";
                                        String newCmdRef = cmdTableInfo.getData().get(cmdRow)[cmdNameIndex]
                                                           + " (code: "
                                                           + cmdTableInfo.getData().get(cmdRow)[cmdCodeIndex]
                                                           + ", owner: "
                                                           + cmdTableInfo.getTablePath()
                                                           + ", arg: "
                                                           + argNamesAndNumber.get(cmdRow)[0]
                                                           + ")";

                                        // Step through each table type command reference input
                                        // type reference
                                        for (InputTypeReference cmdRef : cmdRefChkResults.getReferences())
                                        {
                                            // Step through each table of this table type
                                            for (String table : cmdRef.getTables())
                                            {
                                                // Update references to the command reference from
                                                // the table
                                                cmdRefInpTypeCmd.append("UPDATE "
                                                                        + dbControl.getQuotedName(table)
                                                                        + " SET "
                                                                        + cmdRef.getColumnDb()
                                                                        + " = '"
                                                                        + newCmdRef
                                                                        + "' WHERE "
                                                                        + cmdRef.getColumnDb()
                                                                        + " = E'"
                                                                        + oldCmdRef
                                                                        + "'; ");
                                            }
                                        }

                                        // Check if a data field has the command reference input
                                        // type
                                        if (cmdRefChkResults.isFieldUsesType())
                                        {
                                            // Update the data field value if the command path
                                            // matches
                                            cmdRefInpTypeCmd.append("UPDATE "
                                                                    + InternalTable.FIELDS.getTableName()
                                                                    + " SET "
                                                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                                                    + " = E'"
                                                                    + newCmdRef
                                                                    + "' WHERE "
                                                                    + FieldsColumn.FIELD_TYPE.getColumnName()
                                                                    + " = E'"
                                                                    + DefaultInputType.COMMAND_REFERENCE.getInputName()
                                                                    + "' AND "
                                                                    + FieldsColumn.FIELD_VALUE.getColumnName()
                                                                    + " = E'"
                                                                    + oldCmdRef
                                                                    + "'; ");
                                        }
                                    }
                                }
                                commands.add(command);
                                command = "";
                                counter++;
                            }

                            // Replace the original command table type with the new one
                            newCmdTypeDefn.setName(typeDefn.getName());
                            tableTypeHandler.getTypeDefinitions().set(typeIndex, newCmdTypeDefn);

                            // Delete the original command tables. References to this command table
                            // in the internal tables, including data fields, are preserved
                            if (dbTable.deleteTable(commandTableNames.toArray(new String[0]),
                                                    true,
                                                    ccddMain.getMainFrame()))
                            {
                                throw new CCDDException();
                            }

                            // Step through each command table of the current table type. These are
                            // created one at a time so that each can have its description restored
                            for (int index = 0; index < commandTableNames.size(); index++)
                            {
                                // Create the updated command table
                                if (dbTable.createTable(new String[] {commandTableNames.get(index)},
                                                        commandTableDescriptions.get(index),
                                                        typeDefn.getName(),
                                                        false,
                                                        (index == commandTableNames.size() - 1),
                                                        ccddMain.getMainFrame()))
                                {
                                    throw new CCDDException();
                                }
                            }

                            // Update the command table by replacing the original contents with the
                            // updated values
                            for (int index = 0; index < commands.size(); index++)
                            {
                                dbCommand.executeDbCommand(new StringBuilder(commands.get(index)),
                                                           ccddMain.getMainFrame());
                            }

                            // Update the internal __orders table
                            int columnCount = tableTypeHandler.getTypeDefinition("Command").getColumnCountDatabase();
                            String columnOrder = "'";

                            for (int index = 0; index < columnCount; index++)
                            {
                                columnOrder += Integer.toString(index);
                                if (index != columnCount - 1)
                                {
                                    columnOrder += ":";
                                }
                                else
                                {
                                    columnOrder += "'";
                                }
                            }

                            for (int index = 0; index < commandTableNames.size(); index++)
                            {
                                dbCommand.executeDbCommand(new StringBuilder("UPDATE __orders SET column_order = ").append(columnOrder)
                                                                                                                   .append(" WHERE table_path = '")
                                                                                                                   .append(commandTableNames.get(index))
                                                                                                                   .append("';"),
                                                           ccddMain.getMainFrame());
                            }
                        }
                    }

                    // Update the table types table in the database
                    dbTable.storeTableTypesInfoTable(ccddMain.getMainFrame());

                    // Clean up the lists to reflect the changes to the database
                    dbTable.updateListsAndReferences(ccddMain.getMainFrame());

                    // Update the command reference input type cells and fields
                    dbCommand.executeDbCommand(cmdRefInpTypeCmd, ccddMain.getMainFrame());
                }
                // Release the save point. This must be done within a transaction block, so it must
                // be done prior to the commit below
                dbCommand.releaseSavePoint(ccddMain.getMainFrame());

                // Commit the change(s) to the database
                dbControl.getConnection().commit();

                // Inform the user that updating the database command tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  new StringBuilder("Project '").append(dbControl.getProjectName())
                                                                .append("' command tables conversion  complete"));
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the command tables failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                  + dbControl.getProjectName()
                                  + "' command tables to new format; cause '"
                                  + e.getMessage()
                                  + "'",
                                  "<html><b>Cannot convert project '</b>"
                                  + dbControl.getProjectName()
                                  + "<b>' command tables to new format "
                                  + "(project database will be closed)");

            try
            {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(ccddMain.getMainFrame());
            }
            catch (SQLException se)
            {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot revert changes to project; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot revert changes to project");
            }

            throw new CCDDException();
        }
    }

    class PatchUtility
    {
        final String patchId;
        String htmlDialogMessage;
        CcddMain context;

        public PatchUtility(CcddMain context, String patchId, String htmlDialogMessage)
        {
            if (context == null || patchId == null || htmlDialogMessage == null)
            {
                throw new NullPointerException();
            }

            this.patchId = patchId;
            this.htmlDialogMessage = htmlDialogMessage;
            this.context = context;
        }

        private String getAutoPatchMessage()
        {
            return "CCDD: "
                   + CcddUtilities.removeHTMLTags(htmlDialogMessage)
                   + System.lineSeparator()
                   + "CCDD: Automatically applying patch "
                   + patchId;
        }

        public boolean confirmPatchApplication() throws CCDDException
        {
            boolean isNotAutoPatch = !context.isAutoPatch();

            if (isNotAutoPatch)
            {
                if (context.isGUIHidden())
                {
                    // The GUI is hidden and we are not automatically patching so ... we can't
                    // proceed
                    throw new CCDDException("Invalid command line combination: Please re-run with the -patch flag or with the GUI enabled ("
                                            + patchId
                                            + ")");
                }

                return !this.generateQuestionDialog();
            }

            System.out.println(getAutoPatchMessage());
            return true;
        }

        private boolean generateQuestionDialog() throws CCDDException
        {
            // Check if the user elects to not apply the patch
            return new CcddDialogHandler().showMessageDialog(context.getMainFrame(),
                                                             htmlDialogMessage,
                                                             "Apply Patch " + patchId,
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) != OK_BUTTON;
        }

        public String getUserCancelledMessage()
        {
            return "User elected to not install patch (" + patchId + ")";
        }
    }
}
