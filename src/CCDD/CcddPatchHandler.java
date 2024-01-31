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

import static CCDD.CcddConstants.COL_VALUE;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.ROW_NUM_COLUMN_NAME;
import static CCDD.CcddConstants.ROW_NUM_COLUMN_TYPE;
import static CCDD.CcddConstants.TYPE_ENUM;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary project database patch handler class
 *************************************************************************************************/
public class CcddPatchHandler
{
    private HashMap<String, PatchUtility> patchSet = new HashMap<String, PatchUtility>();
    private final CcddMain ccddMain;
    private final String PATCH_01112023 = "#01112023";
    private final String PATCH_05032023 = "#05032023";

    /**********************************************************************************************
     * CFS Command and Data Dictionary project database patch handler class constructor. The patch
     * handler is used to integrate application changes that require alteration of the project
     * database schema. The alterations are meant to be transparent to the user; however, once
     * patched older versions of the application are no longer guaranteed to function properly and
     * may have detrimental effects
     *
     * @param ccddMain    Main class
     *********************************************************************************************/
    CcddPatchHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        patchSet.clear();

        // Add each of the patches here
        String patch_01112023_dialogMsg = "<html><b>Apply patch to update the project to "
                                          + "eliminate OIDs?<br><br></b>"
                                          + "The internal tables are altered by "
                                          + "removing the existing OID (object identifier) "
                                          + "column and adding a new row number column. <br><b><i>"
                                          + "Project databases created by CCDD versions prior to "
                                          + "2.1.0 will be incompatible with this project "
                                          + "database after applying the patch";
        patchSet.put(PATCH_01112023, new PatchUtility(PATCH_01112023, patch_01112023_dialogMsg));

        String patch_05032023_dialogMsg = "<html><b>Apply patch to update the primary key column "
                                          + "input type to 'Non-negative integer' in the table "
                                          + "type definitions?<br><br></b>"
                                          + "The internal table __table_types is altered by "
                                          + "changing the input type for all _key_ columns from "
                                          + "'Positive integer' to 'Non-negative integer'. This "
                                          + "allows primary key values to be equal to zero. <br><b><i>"
                                          + "Project databases created by CCDD versions prior to "
                                          + "2.1.3 will be incompatible with this project "
                                          + "database after applying the patch";
        patchSet.put(PATCH_05032023, new PatchUtility(PATCH_05032023, patch_05032023_dialogMsg));
    }

    /**********************************************************************************************
     * Apply patches based on the input flag
     *
     * @param stage 1 for patches that must be implemented prior to initializing the handler
     *              classes, 2 for patches that must be implemented after initializing the handler
     *              classes, and 3 for patches that must be implemented after creating the project-
     *              specific PostgreSQL functions
     *
     * @throws CCDDException If the user elects to not install the patch or an error occurs while
     *                       applying the patch
     *********************************************************************************************/
    protected void applyPatches(int stage) throws CCDDException
    {
        // *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE ***
        // Patches are removed after an appropriate amount of time has been given for the patch to
        // be applied. In the event that an older database needs to be updated the old patches can
        // be found in the MRs submitted on 3/11/2021 and on 11/28/2022

        switch(stage)
        {
            // Perform any patches to update this project database to the latest schema that must
            // be implemented prior to initializing the handler classes
            case 1:
                // Patch #01112023: Convert the project to eliminate the OID columns in the
                //internal tables
                eliminateOIDs();

                // Patch #05022023: Update the primary key column input type to 'Non-negative
                // integer' in the table type definitions
                updatePrimaryKeyInputType();

                break;

            // Perform any patches to update this project database to the latest schema that must
            // be implemented after initializing the handler classes
            case 2:
                break;

            // Perform any patches to update this project database to the latest schema that must
            // be implemented after creating the project-specific PostgreSQL functions
            case 3:
                // Patch #10022020: Add the ENUM table type to the internal table types table and
                // update the size column of the data types internal table to have a type of text
                // rather than int
                updateTableTypeAndDataTypeTables();

                break;
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
     * Update the primary key column input type to 'Non-negative integer' in the table type
     * definitions
     *
     * @throws CCDDException If the user elects to not install the patch or an error occurs while
     *                       applying the patch
     *********************************************************************************************/
    private void updatePrimaryKeyInputType() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

        try
        {
            // Execute the command to get the number of primary key columns with the 'Positive
            // integer' input type
            ResultSet result = dbCommand.executeDbQuery(new StringBuilder("SELECT COUNT(*) FROM ").append(InternalTable.TABLE_TYPES.getTableName())
                                                                                                  .append(" WHERE ")
                                                                                                  .append(TableTypesColumn.COLUMN_NAME_DB.getColumnName())
                                                                                                  .append(" = '")
                                                                                                  .append(DefaultColumn.PRIMARY_KEY.getDbName())
                                                                                                  .append("' AND ")
                                                                                                  .append(TableTypesColumn.INPUT_TYPE.getColumnName())
                                                                                                  .append(" = '")
                                                                                                  .append(DefaultInputType.INT_POSITIVE.getInputName())
                                                                                                  .append("';"),
                                                        ccddMain.getMainFrame());

            // Get the number of table columns from the result set
            result.next();
            int numPosIntTypes = result.getInt(1);
            result.close();

            // Check if the table types table hasn't been patched
            if (numPosIntTypes != 0)
            {
                // Check if the user cancels installing the patch
                if (!patchSet.get(PATCH_05032023).confirmPatchApplication())
                {
                    throw new CCDDException(patchSet.get(PATCH_05032023).getUserCanceledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Create a save point in case an error occurs while applying the patch
                dbCommand.createSavePoint(ccddMain.getMainFrame());

                // Change the primary key column input types
                dbCommand.executeDbCommand(new StringBuilder("UPDATE ").append(InternalTable.TABLE_TYPES.getTableName())
                                                                       .append(" SET ")
                                                                       .append(TableTypesColumn.INPUT_TYPE.getColumnName())
                                                                       .append(" = '")
                                                                       .append(DefaultInputType.INT_NON_NEGATIVE.getInputName())
                                                                       .append("' WHERE ")
                                                                       .append(TableTypesColumn.COLUMN_NAME_DB.getColumnName())
                                                                       .append(" = '")
                                                                       .append(DefaultColumn.PRIMARY_KEY.getDbName())
                                                                       .append("';"),
                                           ccddMain.getMainFrame());

                // Release the save point. This must be done within a transaction block, so it must
                // be done prior to the commit below
                dbCommand.releaseSavePoint(ccddMain.getMainFrame());

                // Commit the change(s) to the database
                dbControl.getConnection().commit();

                // Inform the user that updating the database tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  new StringBuilder("Project '").append(dbControl.getProjectName())
                                                                .append("' primary key input type update complete"));
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the command tables failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot update primary key input types in project '"
                                  + dbControl.getProjectName()
                                  + "'; cause '"
                                  + e.getMessage()
                                  + "'",
                                  "<html><b>Cannot update primary key input types in project '</b>"
                                  + dbControl.getProjectName()
                                  + "<b>' (project database will be closed)");

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

    /**********************************************************************************************
     * Convert the project to eliminate OIDs in the internal tables
     *
     * @throws CCDDException If the user elects to not install the patch or an error occurs while
     *                       applying the patch
     *********************************************************************************************/
    private void eliminateOIDs() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        try
        {
            boolean isPatched = false;

            // Execute the command to get the number of columns in one of the affected internal
            // tables
            ResultSet result = dbCommand.executeDbQuery(new StringBuilder("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '").append(InternalTable.DATA_TYPES.getTableName())
                                                                                                                                                 .append("';"),
                                                        ccddMain.getMainFrame());

            // Get the number of table columns from the result set
            result.next();
            int numColumns = result.getInt(1);
            result.close();

            // Check if the table has the expected number of columns
            if (numColumns == InternalTable.DATA_TYPES.getNumColumns())
            {
                isPatched = true;
            }

            // Check if the patch hasn't been applied
            if (!isPatched)
            {
                // Check if the user cancels installing the patch
                if (!patchSet.get(PATCH_01112023).confirmPatchApplication())
                {
                    throw new CCDDException(patchSet.get(PATCH_01112023).getUserCanceledMessage());
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Create a save point in case an error occurs while applying the patch
                dbCommand.createSavePoint(ccddMain.getMainFrame());

                // Step through each internal table type
                for (InternalTable intTable : InternalTable.values())
                {
                    // Only update internal tables that use the OID column
                    if (intTable != InternalTable.VALUES)
                    {
                        // Check if the table isn't a script
                        if (intTable != InternalTable.SCRIPT)
                        {
                            eliminateOIDs(dbCommand, dbTable, intTable, intTable.getTableName(), null);
                        }
                        // Script file
                        else
                        {
                            // Get the list of script files from the database
                            String[] scripts = dbTable.queryScriptTables(ccddMain.getMainFrame());

                            // Step through each script file
                            for (String script : scripts)
                            {
                                eliminateOIDs(dbCommand, dbTable, intTable, InternalTable.SCRIPT.getTableName(script), script);
                            }
                        }
                    }
                }

                // Release the save point. This must be done within a transaction block, so it must
                // be done prior to the commit below
                dbCommand.releaseSavePoint(ccddMain.getMainFrame());

                // Commit the change(s) to the database
                dbControl.getConnection().commit();

                // Inform the user that updating the database tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  new StringBuilder("Project '").append(dbControl.getProjectName())
                                                                .append("' OID replacement complete"));
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the command tables failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot remove OIDs from project '"
                                  + dbControl.getProjectName()
                                  + "'; cause '"
                                  + e.getMessage()
                                  + "'",
                                  "<html><b>Cannot remove OIDs from project '</b>"
                                  + dbControl.getProjectName()
                                  + "<b>' (project database will be closed)");

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

    /**********************************************************************************************
     * Eliminate OIDs in the specified internal table
     *
     * @param dbCommand  Reference to the database command handler
     *
     * @param dbTable    Reference to the database table command handler
     *
     * @param intTable   Internal table type
     *
     * @param tableName  Table name
     *
     * @param scriptName Script name and description; null if not a script table
     *
     * @throws SQLException If an error occurs retrieving or updating the table data
     *********************************************************************************************/
    private void eliminateOIDs(CcddDbCommandHandler dbCommand,
                               CcddDbTableCommandHandler dbTable,
                               InternalTable intTable,
                               String tableName,
                               String scriptName) throws SQLException
    {
        // Get the internal table contents, sorted by OID
        List<String[]> tableData = dbTable.queryDatabase(new StringBuilder("SELECT * FROM "
                                                                           + tableName
                                                                           + " ORDER BY OID;"),
                                                         ccddMain.getMainFrame());

        // Remove the OID column and add the new row index column
        dbCommand.executeDbCommand(new StringBuilder("ALTER TABLE ").append(tableName)
                                                                    .append(" SET WITHOUT OIDS; ALTER TABLE ")
                                                                    .append(tableName)
                                                                    .append(" ADD COLUMN ")
                                                                    .append(ROW_NUM_COLUMN_NAME)
                                                                    .append(" ")
                                                                    .append(ROW_NUM_COLUMN_TYPE)
                                                                    .append(";"),
                                   ccddMain.getMainFrame());

        // Check if the table is a script
        if (intTable == InternalTable.SCRIPT)
        {
            // Remove the line number column
            tableData = CcddUtilities.removeArrayListColumn(tableData, 0);

            dbCommand.executeDbCommand(new StringBuilder("ALTER TABLE ").append(tableName)
                                                                        .append(" DROP COLUMN line_number;"),
                                       ccddMain.getMainFrame());
        }

        // Rewrite the internal table with the row numbers
        dbTable.storeNonTableTypesInfoTable(intTable,
                                            tableData,
                                            scriptName,
                                            ccddMain.getMainFrame());
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
     * Patch utility class
     *********************************************************************************************/
    class PatchUtility
    {
        // Patch identifier
        final String patchId;

        // Dialog message to display requesting user intervention
        String htmlDialogMessage;

        /******************************************************************************************
         * Patch utility class constructor
         *
         * @param patchId           Patch identifier
         *
         * @param htmlDialogMessage Dialog message to display requesting user intervention
         *****************************************************************************************/
        public PatchUtility(String patchId, String htmlDialogMessage)
        {
            if (patchId == null || htmlDialogMessage == null)
            {
                throw new NullPointerException();
            }

            this.patchId = patchId;
            this.htmlDialogMessage = htmlDialogMessage;
        }

        /******************************************************************************************
         * Determine if the patch should be installed
         *
         * @return True if the user confirmed applying the patch, or is automatic patching is
         *         enabled
         *
         * @throws CCDDException If the patch is required and the GUI is not displayed
         *****************************************************************************************/
        public boolean confirmPatchApplication() throws CCDDException
        {
            boolean isConfirmed = false;

            // Check if automatic patching is enabled
            if (ccddMain.isAutoPatch())
            {
                System.out.println("CCDD: "
                                   + CcddUtilities.removeHTMLTags(htmlDialogMessage)
                                   + System.lineSeparator()
                                   + "CCDD: Automatically applying patch "
                                   + patchId);
                isConfirmed = true;
            }
            // Automatic patching is disabled
            else
            {
                // Check if the user interface is not displayed
                if (ccddMain.isGUIHidden())
                {
                    // The GUI is hidden and automatically patching is disabled, so do not proceed
                    throw new CCDDException("Invalid command line combination: Please re-run with "
                                            + "the -patch flag or with the GUI enabled ("
                                            + patchId
                                            + ")");
                }

                // Get the user to confirm the patch installation
                isConfirmed = new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                        htmlDialogMessage,
                                                                        "Apply Patch " + patchId,
                                                                        JOptionPane.QUESTION_MESSAGE,
                                                                        DialogOption.OK_CANCEL_OPTION) == OK_BUTTON;
            }

            return isConfirmed;
        }

        /******************************************************************************************
         * Get text to display when the user cancels installation of a patch
         *
         * @return Canceled patch message
         *****************************************************************************************/
        public String getUserCanceledMessage()
        {
            return "User elected to not install patch " + patchId;
        }
    }
}
