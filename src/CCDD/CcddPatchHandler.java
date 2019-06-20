/**
 * CFS Command and Data Dictionary project database patch handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.io.File;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.DatabaseComment;
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
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary project database patch handler class
 *************************************************************************************************/
public class CcddPatchHandler
{
    private final CcddMain ccddMain;

    // Patch 11052018 specific variables:
    // Flag that indicates that part 1 completed successfully, and part 2 should be performed
    private boolean patch11052018Continue;

    // Number of data fields that were renamed to prevent a duplicate with an inherited field
    private int patch11052018RenamedFields;

    /**********************************************************************************************
     * CFS Command and Data Dictionary project database patch handler class constructor. The patch
     * handler is used to integrate application changes that require alteration of the project
     * database schema. The alterations are meant to be transparent to the user; however, once
     * patched older versions of the application are no longer guaranteed to function properly and
     * may have detrimental effects
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddPatchHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
    }

    /**********************************************************************************************
     * Apply patches based on the input flag
     *
     * @param isBeforeHandlerInit
     *            true if the patch must be implemented prior to initializing the handler classes;
     *            false if the patch must be implemented after initializing the handler classes
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    protected void applyPatches(boolean isBeforeHandlerInit) throws CCDDException
    {
        ///////////////////////////////////////////////////////////////////////////////////////////
        // *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE *** NOTE ***
        // Patches are removed after an appropriate amount of time has been given for the patch to
        // be applied. For now the code is left in place, though commented out, in the event the
        // patch is required for an older database
        ///////////////////////////////////////////////////////////////////////////////////////////

        // Check if only patches that must be performed prior to handler initialization should be
        // implemented
        if (isBeforeHandlerInit)
        {
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
        else
        {
            // Patch #11052018: Part B - Add inherited fields to tables that don't already have the
            // field
            updateFieldsPart2();

            // Patch #06212018: Update the padding variable format from '__pad#' to 'pad#__'
            updatePaddingVariables();

            // Patch #07242018: Update the database to support user access levels
            updateUserAccess();

            // Patch #08292018: Change the message ID name input type to 'Text' and the message ID
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
        }
    }

    /**********************************************************************************************
     * Backup the project database. Store the backup file in the folder specified in the program
     * preferences (or the CCDD start-up folder if no preference is specified). The file name is a
     * combination of the project's database name and the current date/time stamp
     *
     * @param dbControl
     *            reference to the database control class
     *
     * @throws CCDDException
     *             If the project database cannot be backed up and the user elects to not continue
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
                                         new FileEnvVar((ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath().isEmpty()
                                                                                                                     ? ""
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
     * Update the fields table to include a field_inherited column and set each field's default
     * status. Older versions of CCDD are not compatible with the project database after applying
     * this patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateFieldsPart1() throws CCDDException
    {
        patch11052018Continue = false;
        patch11052018RenamedFields = 0;
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

            // Create lists to contain the old and new fields table items
            List<String[]> tableData = new ArrayList<String[]>();

            // Read the contents of the fields table
            ResultSet fieldsData = dbCommand.executeDbQuery("SELECT * FROM "
                                                            + InternalTable.FIELDS.getTableName()
                                                            + " ORDER BY OID;",
                                                            ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (fieldsData.getMetaData().getColumnCount() == 8)
            {
                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the data "
                                                                                       + "fields table?<br><br></b>"
                                                                                       + "Adds a column in the data fields "
                                                                                       + "table to store field inheritance "
                                                                                       + "status. A field is inherited if "
                                                                                       + "defined in the table's type "
                                                                                       + "definition. Fields are renamed to "
                                                                                       + "prevent an instance of a fields "
                                                                                       + "matching an inherited field when the "
                                                                                       + "input types differ. Missing an "
                                                                                       + "inherited fields are added to "
                                                                                       + "tables<br><b><i>Older versions of "
                                                                                       + "CCDD will be incompatible with this "
                                                                                       + "project database after applying the patch",
                                                              "Apply Patch #11052018",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    fieldsData.close();
                    throw new CCDDException("User elected to not install patch (#11052018)");
                }

                // Step through each of the query results
                while (fieldsData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[9];

                    // Step through each column in the row
                    for (int column = 0; column < 8; column++)
                    {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0
                        columnValues[column] = fieldsData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
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
                if (tableData.size() != 0)
                {
                    int currentIndex = 0;

                    // Indicate in the log that the old data successfully loaded
                    eventLog.logEvent(SUCCESS_MSG,
                                      InternalTable.FIELDS.getTableName()
                                                   + " retrieved");

                    // Get the array containing the comments for all data tables
                    String[][] comments = dbTable.queryDataTableComments(ccddMain.getMainFrame());

                    // Step through the data field definitions
                    for (String[] fieldDefn : tableData)
                    {
                        // Check if the field is owned by a table
                        if (CcddFieldHandler.isTableField(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]))
                        {
                            // Get the table's type name from the table comment
                            String tableType = dbTable.getTableComment(TableInformation.getPrototypeName(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]),
                                                                       comments)[TableCommentIndex.TYPE.ordinal()];

                            // Step through the data field definitions
                            for (String[] otherFldDefn : tableData)
                            {
                                // Check if this is a data field belonging to the table's type
                                // definition and the field names match
                                if (CcddFieldHandler.getFieldTypeName(tableType).equals(otherFldDefn[FieldsColumn.OWNER_NAME.ordinal()])
                                    && fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].equals(otherFldDefn[FieldsColumn.FIELD_NAME.ordinal()]))
                                {
                                    // Check if the input types match
                                    if (fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].equals(otherFldDefn[FieldsColumn.FIELD_TYPE.ordinal()]))
                                    {
                                        // Set the flag that indicates this field is inherited
                                        fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = "true";

                                        // Step through the original field definition parts except
                                        // for field owner, name, and value (so as not to overwrite
                                        // the field value with that from the inherited field)
                                        for (int index = 2; index < 7; index++)
                                        {
                                            // Overwrite the instance of the field with the
                                            // inherited field
                                            fieldDefn[index] = otherFldDefn[index];
                                        }
                                    }
                                    // The names match but the input types don't match. These are
                                    // considered different fields
                                    else
                                    {
                                        boolean isNoMatch = true;
                                        patch11052018RenamedFields++;

                                        // Continue to update the field's name until there's no
                                        // match with one of its other fields
                                        while (isNoMatch)
                                        {
                                            int dupIndex = 0;
                                            isNoMatch = false;

                                            // Modify the field's name
                                            fieldDefn[FieldsColumn.FIELD_NAME.ordinal()] += "_";

                                            // Step through the data field definitions
                                            for (String[] dupFldDefn : tableData)
                                            {
                                                // Check if this isn't the field being changed
                                                if (currentIndex != dupIndex)
                                                {
                                                    // Check if the field name matches another of
                                                    // the table's fields or its default fields
                                                    if (fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].equals(dupFldDefn[FieldsColumn.FIELD_NAME.ordinal()])
                                                        && (fieldDefn[FieldsColumn.OWNER_NAME.ordinal()].equals(dupFldDefn[FieldsColumn.OWNER_NAME.ordinal()])
                                                            || dupFldDefn[FieldsColumn.OWNER_NAME.ordinal()].equals(CcddFieldHandler.getFieldTypeName(tableType))))
                                                    {
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
                dbTable.storeInformationTable(InternalTable.FIELDS,
                                              tableData,
                                              null,
                                              ccddMain.getMainFrame());

                // Set the flag to indicate that part 2 of this patch should be performed
                patch11052018Continue = true;

                // Inform the user that updating the database fields table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' data fields table conversion (part 1) complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the fields table failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' data fields table to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '</b>"
                                                                  + dbControl.getProjectName()
                                                                  + "<b>' data fields table to new format "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * After updating the fields table to include a field_inherited column add inherited fields to
     * those tables that don't already have the field. Older versions of CCDD are not compatible
     * with the project database after applying this patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateFieldsPart2() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
        CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();

        // Check if part 1 of this patch successfully completed
        if (patch11052018Continue)
        {
            try
            {
                int addedFields = 0;
                CcddFieldHandler fieldHandler = ccddMain.getFieldHandler();
                CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

                // Step through each table type
                for (String typeName : ccddMain.getTableTypeHandler().getTableTypeNames())
                {
                    // Get the list of names of all tables of the specified type
                    List<String> tableNamesList = dbTable.getAllTablesOfType(typeName,
                                                                             null,
                                                                             ccddMain.getMainFrame());

                    // Step through each of this table type's data fields
                    for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(typeName)))
                    {
                        // Check if this isn't a separator or break
                        if (!fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SEPARATOR))
                            && !fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.BREAK)))
                        {
                            // Step through each table of this type
                            for (String tableName : tableNamesList)
                            {
                                // Check if the data field meets the criteria of a new field for
                                // this table ...
                                if (
                                // ... the table doesn't have this data field
                                (fieldHandler.getFieldInformationByName(tableName,
                                                                        fieldInfo.getFieldName()) == null)

                                    // ... and the table isn't a child structure (all fields are
                                    // stored for prototypes, even if not displayed) or the field
                                    // is applicable to this table
                                    && (!tableName.contains(".")
                                        || fieldHandler.isFieldApplicable(tableName,
                                                                          fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                                          null)))
                                {
                                    // Add the data field to the table and set the flag indicating
                                    // a change has been made
                                    fieldHandler.getFieldInformation().add(new FieldInformation(tableName,
                                                                                                fieldInfo.getFieldName(),
                                                                                                fieldInfo.getDescription(),
                                                                                                fieldInfo.getInputType(),
                                                                                                fieldInfo.getSize(),
                                                                                                fieldInfo.isRequired(),
                                                                                                fieldInfo.getApplicabilityType(),
                                                                                                fieldInfo.getValue(),
                                                                                                true,
                                                                                                null,
                                                                                                -1));
                                    addedFields++;
                                }
                            }
                        }
                    }
                }

                // Check if any fields were added
                if (addedFields != 0)
                {
                    // Store the data fields
                    dbTable.storeInformationTable(InternalTable.FIELDS,
                                                  fieldHandler.getFieldDefnsFromInfo(),
                                                  null,
                                                  ccddMain.getMainFrame());
                }

                // Check if any fields were added or renamed
                if (addedFields != 0 || patch11052018RenamedFields != 0)
                {
                    // Inform the user how many inherited fields were added and renamed
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Number of inherited data fields added: </b>"
                                                                                       + addedFields
                                                                                       + "<br><b>Number of existing fields renamed: </b>"
                                                                                       + patch11052018RenamedFields,
                                                              "Patch #11052018 Data Field Updates",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                // Inform the user that updating the database fields table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' data fields table conversion (part 2) complete");
            }
            catch (Exception e)
            {
                // Inform the user that adding the inherited tables failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot convert project '"
                                                               + dbControl.getProjectName()
                                                               + "' data fields table to new format; cause '"
                                                               + e.getMessage()
                                                               + "'",
                                      "<html><b>Cannot convert project '</b>"
                                                                      + dbControl.getProjectName()
                                                                      + "<b>' data fields table to new format "
                                                                      + "(project database will be closed)");

                throw new CCDDException();
            }
        }
    }

    /**********************************************************************************************
     * Update the project database table type and data field table references to the message ID
     * name and message ID input types. The message name and ID have been combined into a single
     * input type, 'Message name &amp; ID'. Change the message ID name input type to 'Text' and the
     * message ID input type to 'Message name &amp; ID'. Note that the original message ID names
     * are no longer associated with the IDs; this must be done manually
     *
     * @throws CCDDException
     *             if an error occurs performing the update or the user elects to not install the
     *             patch
     *********************************************************************************************/
    private void updateMessageNamesAndIDs() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

            // Determine if the table type or data field tables reference a message ID name or
            // message ID input type, or if the application scheduler table has separate wake-up
            // names and IDs
            ResultSet msgData = dbCommand.executeDbQuery("SELECT 1 FROM "
                                                         + InternalTable.TABLE_TYPES.getTableName()
                                                         + " WHERE "
                                                         + TableTypesColumn.INPUT_TYPE.getColumnName()
                                                         + " = 'Message ID name' OR "
                                                         + TableTypesColumn.INPUT_TYPE.getColumnName()
                                                         + " = 'Message ID' UNION SELECT 1 FROM "
                                                         + InternalTable.FIELDS.getTableName()
                                                         + " WHERE "
                                                         + FieldsColumn.FIELD_TYPE.getColumnName()
                                                         + " = 'Message ID name' OR "
                                                         + FieldsColumn.FIELD_TYPE.getColumnName()
                                                         + " = 'Message ID' UNION SELECT 1 FROM "
                                                         + InternalTable.APP_SCHEDULER.getTableName()
                                                         + " WHERE "
                                                         + AppSchedulerColumn.APP_INFO.getColumnName()
                                                         + " ~E'[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,.*';",
                                                         ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (msgData.next())
            {
                msgData.close();

                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the table type, data field, and "
                                                                                       + "application scheduler tables message "
                                                                                       + "name and IDs?<br><br></b>Changes "
                                                                                       + "message ID name input type to 'Text' "
                                                                                       + "and the message ID input type to "
                                                                                       + "'Message name & ID'. Combines the "
                                                                                       + "application scheduler wake-up "
                                                                                       + "message name and ID pairs into a "
                                                                                       + "single<br><b><i>Older versions of "
                                                                                       + "CCDD are incompatible with this "
                                                                                       + "project database after applying the "
                                                                                       + "patch",
                                                              "Apply Patch #08292018",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    throw new CCDDException("User elected to not install patch (#08292018)");
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Update the table type and data field tables. The message ID fields are changed
                // to use the message name & ID type, and the message ID name fields are changed to
                // use the text input type
                dbCommand.executeDbCommand("UPDATE "
                                           + InternalTable.TABLE_TYPES.getTableName()
                                           + " SET "
                                           + TableTypesColumn.INPUT_TYPE.getColumnName()
                                           + " = '"
                                           + DefaultInputType.MESSAGE_NAME_AND_ID.getInputName()
                                           + "' WHERE "
                                           + TableTypesColumn.INPUT_TYPE.getColumnName()
                                           + " = 'Message ID'; UPDATE "
                                           + InternalTable.TABLE_TYPES.getTableName()
                                           + " SET "
                                           + TableTypesColumn.INPUT_TYPE.getColumnName()
                                           + " = '"
                                           + DefaultInputType.TEXT.getInputName()
                                           + "' WHERE "
                                           + TableTypesColumn.INPUT_TYPE.getColumnName()
                                           + " = 'Message ID name'; UPDATE "
                                           + InternalTable.FIELDS.getTableName()
                                           + " SET "
                                           + FieldsColumn.FIELD_TYPE.getColumnName()
                                           + " = '"
                                           + DefaultInputType.MESSAGE_NAME_AND_ID.getInputName()
                                           + "' WHERE "
                                           + FieldsColumn.FIELD_TYPE.getColumnName()
                                           + " = 'Message ID'; UPDATE "
                                           + InternalTable.FIELDS.getTableName()
                                           + " SET "
                                           + FieldsColumn.FIELD_TYPE.getColumnName()
                                           + " = '"
                                           + DefaultInputType.TEXT.getInputName()
                                           + "' WHERE "
                                           + FieldsColumn.FIELD_TYPE.getColumnName()
                                           + " = 'Message ID name'; UPDATE "
                                           + InternalTable.APP_SCHEDULER.getTableName()
                                           + " SET "
                                           + AppSchedulerColumn.APP_INFO.getColumnName()
                                           + " = regexp_replace("
                                           + AppSchedulerColumn.APP_INFO.getColumnName()
                                           + ", E'([^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*),([^,]*,[^,]*,[^,]*),([^,]*,.*)', '\\\\1 \\\\2 \\\\3', 'g') WHERE "
                                           + AppSchedulerColumn.APP_INFO.getColumnName()
                                           + " ~ E'[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,.*';",
                                           ccddMain.getMainFrame());

                // Inform the user that updating the database table type, data field, and
                // application scheduler tables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' table type, data field, and application "
                                                                   + "scheduler tables conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that adding access level support failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot update project '"
                                                           + dbControl.getProjectName()
                                                           + "' to change message name and IDs; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot update project '</b>"
                                                                  + dbControl.getProjectName()
                                                                  + "<b>' to change message name and IDs "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the project database so that user access levels are supported
     *
     * @throws CCDDException
     *             if an error occurs performing the update or the user elects to not install the
     *             patch
     *********************************************************************************************/
    private void updateUserAccess() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            // Check if the project administrator isn't in the database comment; this indicates the
            // project hasn't been updated to support user access levels
            if (dbControl.getDatabaseAdmins(dbControl.getDatabaseName()) == null)
            {
                CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the database to support user access "
                                                                                       + "support user access levels?<br><br></b>"
                                                                                       + "Changes the database to support user "
                                                                                       + "access levels. <b>The current user is set "
                                                                                       + "as the creator/administrator of "
                                                                                       + "the database!</b> Older versions of CCDD "
                                                                                       + "will remain compatible with this project "
                                                                                       + "database after applying the patch",
                                                              "Apply Patch #07242018",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    throw new CCDDException("User elected to not install patch (#07242018)");
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Get the database comment, separated into its individual parts
                String[] comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());

                // Update the database's comment, adding the current user as the project creator
                dbCommand.executeDbUpdate(dbControl.buildDatabaseCommentCommand(dbControl.getProjectName(),
                                                                                dbControl.getUser(),
                                                                                false,
                                                                                comment[DatabaseComment.DESCRIPTION.ordinal()]),
                                          ccddMain.getMainFrame());

                // Update the user access level table, setting the current user as the
                // administrator
                List<String[]> userData = new ArrayList<String[]>(1);
                userData.add(new String[] {dbControl.getUser(),
                                           AccessLevel.ADMIN.getDisplayName()});
                ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.USERS,
                                                                          userData,
                                                                          null,
                                                                          ccddMain.getMainFrame());

                // Inform the user that updating the database to support user access levels
                // completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' user access level conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that adding access level support failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot update project '"
                                                           + dbControl.getProjectName()
                                                           + "' to support user access levels; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot update project '</b>"
                                                                  + dbControl.getProjectName()
                                                                  + "<b>' to support user access levels "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the padding variable format from '__pad#' to 'pad#__'. This is to accommodate XML
     * exports that don't allow leading underscores in variable names (e.g., EDS)
     *
     * @throws CCDDException
     *             if an error occurs performing the update or the user elects to not install the
     *             patch
     *********************************************************************************************/
    private void updatePaddingVariables() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();

            String varColNames = "";

            // Step through each table type definition
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
            {
                // Check if the table type represents a structure
                if (typeDefn.isStructure())
                {
                    // Append the variable name column name
                    varColNames += typeDefn.getDbColumnNameByInputType(DefaultInputType.VARIABLE) + ",";
                }
            }

            varColNames = CcddUtilities.removeTrailer(varColNames, ",");

            // Search for pad variables using the old format in all prototype tables
            ResultSet padData = dbCommand.executeDbQuery("SELECT * FROM search_tables(E'__pad', false, "
                                                         + "false, 'PROTO', '{"
                                                         + varColNames
                                                         + "}');",
                                                         ccddMain.getMainFrame());

            // Check if there are any pad variables using the old format in any structure table
            if (padData.next())
            {
                padData.close();

                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update padding variable names?<br><br></b>"
                                                                                       + "Changes the padding variable format from "
                                                                                       + "'__pad#' to 'pad#__'.<br><b><i>If patch "
                                                                                       + "not applied the affected variables will "
                                                                                       + "not be recognized as padding",
                                                              "Apply Patch #06212018",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    throw new CCDDException("User elected to not install patch (#06212018)");
                }

                // Back up the project database before applying the patch
                backupDatabase(dbControl);

                // Step through each prototype structure table
                for (String protoStruct : dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE))
                {
                    // Get the table's comment
                    String[] comment = dbTable.queryDataTableComment(protoStruct,
                                                                     ccddMain.getMainFrame());

                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(comment[TableCommentIndex.TYPE.ordinal()]);

                    // Get the table's variable name column name
                    String variableNameColumn = typeDefn.getDbColumnNameByInputType(DefaultInputType.VARIABLE);

                    // Update the padding variable names to the new format
                    dbCommand.executeDbCommand("UPDATE "
                                               + dbControl.getQuotedName(protoStruct)
                                               + " SET "
                                               + dbControl.getQuotedName(variableNameColumn)
                                               + " = regexp_replace("
                                               + variableNameColumn
                                               + ", E'^__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E'pad\\\\1__\\\\2');",
                                               ccddMain.getMainFrame());
                }

                // Update the padding variable names in the custom values table to the new format
                dbCommand.executeDbCommand("UPDATE "
                                           + InternalTable.VALUES.getTableName()
                                           + " SET "
                                           + ValuesColumn.TABLE_PATH.getColumnName()
                                           + " = regexp_replace("
                                           + ValuesColumn.TABLE_PATH.getColumnName()
                                           + ", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');",
                                           ccddMain.getMainFrame());

                // Update the padding variable names in the links table to the new format
                dbCommand.executeDbCommand("UPDATE "
                                           + InternalTable.LINKS.getTableName()
                                           + " SET "
                                           + LinksColumn.MEMBER.getColumnName()
                                           + " = regexp_replace("
                                           + LinksColumn.MEMBER.getColumnName()
                                           + ", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');",
                                           ccddMain.getMainFrame());

                // Update the padding variable names in the telemetry scheduler table to the new
                // format
                dbCommand.executeDbCommand("UPDATE "
                                           + InternalTable.TLM_SCHEDULER.getTableName()
                                           + " SET "
                                           + TlmSchedulerColumn.MEMBER.getColumnName()
                                           + " = regexp_replace("
                                           + TlmSchedulerColumn.MEMBER.getColumnName()
                                           + ", E',__pad([0-9]+)(\\\\[[0-9]+\\\\])?$', E',pad\\\\1__\\\\2');",
                                           ccddMain.getMainFrame());

                // Inform the user that updating the padding variables completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' padding variable conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the padding variable names failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' padding variable names to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '</b>"
                                                                  + dbControl.getProjectName()
                                                                  + "<b>' padding variable names to new format");
        }
    }

    // /**********************************************************************************************
    // * Update the associations table to include a name column. Older versions of CCDD are not
    // * compatible with the project database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while applying
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
    // * Update the data fields table applicability column to change "Parents only" to "Roots
    // only".
    // * Older versions of CCDD are not compatible with the project database after applying this
    // * patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while applying
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
    // * Update the associations table to include a description column and to change the table
    // * separator characters in the member_table column. Older versions of CCDD are not compatible
    // * with the project database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while applying
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
    // * Update the project database comments to include the database name with capitalization and
    // * special characters intact. The project database is first backed up to the file
    // * <projectName>_<timeStamp>.dbu. The new format for the comment is <CCDD project identifier
    // * string><lock status, 0 or 1>;<project name with capitalization intact>;<project
    // * description>. Older versions of CCDD are compatible with the project database after
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
    // * Update the internal table __types to the new name __table_types, delete the primitive_only
    // * column, and add the structure allowed and pointer allowed columns. If successful, the
    // * original table (__types) is renamed, preserving the original information and preventing
    // * subsequent conversion attempts. The project database is first backed up to the file
    // * <projectName>_<timeStamp>.dbu. Older versions of CCDD are not compatible with the project
    // * database after applying this patch
    // *
    // * @throws CCDDException
    // * If the user elects to not install the patch or an error occurs while applying
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
    // ResultSet infoData = dbCommand.executeDbQuery("SELECT * FROM __types ORDER BY OID;",
    // ccddMain.getMainFrame());
    //
    // // Step through each of the query results
    // while (infoData.next())
    // {
    // // Create an array to contain the column values
    // String[] columnValues = new String[infoData.getMetaData().getColumnCount()];
    //
    // // Step through each column in the row
    // for (int column = 0; column < infoData.getMetaData().getColumnCount(); column++)
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
    // String[] newColumnDefn = new String[InternalTable.TABLE_TYPES.getNumColumns()];
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
    // // Check if this column definition isn't for the primary key or row index since
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
    // // Store the updated table type definitions in the project database and change the
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
}
