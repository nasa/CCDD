/**
 * CFS Command & Data Dictionary project database patch handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR;
import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.DATABASE_COMMENT_SEPARATOR;
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
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary project database patch handler class
 *************************************************************************************************/
public class CcddPatchHandler
{
    private final CcddMain ccddMain;

    /**********************************************************************************************
     * CFS Command & Data Dictionary project database patch handler class constructor. THe patch
     * handler is used to integrate application changes that require alteration of the project
     * database schema. The alterations are meant to be transparent to the user; however, once
     * patched older versions of the application are no longer guaranteed to function properly and
     * may have detrimental effects
     *
     * @param ccddMain
     *            main class
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    CcddPatchHandler(CcddMain ccddMain) throws CCDDException
    {
        this.ccddMain = ccddMain;

        // Patch #01262017: Rename the table types table and alter its content to include the
        // database name with capitalization intact
        updateTableTypesTable();

        // Patch #07112017: Update the database comment to include the project name with
        // capitalization intact
        // NOTE: This patch is no longer valid due to changes in the database opening sequence
        // where the lock status is set
        updateDataBaseComment();

        // Patch #0712017: Update the associations table to include a description column and to
        // change the table separator characters in the member_table column
        updateAssociationsTable();

        // Patch #09272017: Update the data fields table applicability column to change "Parents
        // only" to "Roots only"
        updateFieldApplicability();

        // Patch #11132017: Update the associations table to include a name column
        updateAssociationsTable2();

        // Patch #06212018: Update the padding variable format from '__pad#' to 'pad#__'
        updatePaddingVariables();
    }

    /**********************************************************************************************
     * Update the padding variable format from '__pad#' to 'pad#__'. This is to accommodate XML
     * exports that don't allow leading underscores in variable names (e.g., EDS)
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
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
                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update padding variable names??<br><br></b>"
                                                                                       + "Changes the padding variable format from "
                                                                                       + "'__pad#' to 'pad#__'.<br><b><i>If patch "
                                                                                       + "not applied the affected variables will "
                                                                                       + "no longer be recognized as padding",
                                                              "Apply Patch #06212018",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    padData.close();
                    throw new CCDDException("user elected to not install patch (#06212018)");
                }

                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

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
                                               + protoStruct
                                               + " SET "
                                               + variableNameColumn
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

            padData.close();
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
                                  "<html><b>Cannot convert project '"
                                                                  + dbControl.getProjectName()
                                                                  + "' padding variable names to new format "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the associations table to include a name column. Older versions of CCDD are not
     * compatible with the project database after applying this patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateAssociationsTable2() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

            // Create lists to contain the old and new associations table items
            List<String[]> tableData = new ArrayList<String[]>();

            // Read the contents of the associations table
            ResultSet assnsData = dbCommand.executeDbQuery("SELECT * FROM "
                                                           + InternalTable.ASSOCIATIONS.getTableName()
                                                           + " ORDER BY OID;",
                                                           ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (assnsData.getMetaData().getColumnCount() == 3)
            {
                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the script "
                                                                                       + "associations table?<br><br></b>"
                                                                                       + "Incorporates a name column in the "
                                                                                       + "script associations table.<br><b><i>Older "
                                                                                       + "versions of CCDD will be incompatible "
                                                                                       + "with this project database after "
                                                                                       + "applying the patch",
                                                              "Apply Patch #11132017",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    assnsData.close();
                    throw new CCDDException("user elected to not install patch (#11132017)");
                }

                // Step through each of the query results
                while (assnsData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[4];

                    // Step through each column in the row
                    for (int column = 0; column < 3; column++)
                    {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0. Also, shift the old data over one column to
                        // make room for the name
                        columnValues[column + 1] = assnsData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Add the row data to the list
                    tableData.add(columnValues);
                }

                assnsData.close();

                // Check if there are any associations in the table
                if (tableData.size() != 0)
                {
                    // Indicate in the log that the old data successfully loaded
                    eventLog.logEvent(SUCCESS_MSG,
                                      InternalTable.ASSOCIATIONS.getTableName()
                                                   + " retrieved");
                }

                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

                // Store the updated associations table
                dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
                                              tableData,
                                              null,
                                              ccddMain.getMainFrame());

                // Inform the user that updating the database associations table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' associations table conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the associations table failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' associations table to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '"
                                                                  + dbControl.getProjectName()
                                                                  + "' associations table to new format "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the data fields table applicability column to change "Parents only" to "Roots only".
     * Older versions of CCDD are not compatible with the project database after applying this
     * patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateFieldApplicability() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

            // Read the contents of the data field table's applicability column
            ResultSet fieldData = dbCommand.executeDbQuery("SELECT * FROM "
                                                           + InternalTable.FIELDS.getTableName()
                                                           + " WHERE "
                                                           + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                                           + " = 'Parents only';",
                                                           ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (fieldData.next())
            {
                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the data "
                                                                                       + "fields table?<br><br></b>Changes "
                                                                                       + "data field applicability 'Parents "
                                                                                       + "only' to 'Roots only'.<br><b><i>Older "
                                                                                       + "versions of CCDD are imcompatible "
                                                                                       + "with this project database after "
                                                                                       + "applying the patch",
                                                              "Apply Patch #09272017",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    fieldData.close();
                    throw new CCDDException("user elected to not install patch (#09272017)");
                }

                fieldData.close();

                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

                // Update the data fields table
                dbCommand.executeDbCommand("UPDATE "
                                           + InternalTable.FIELDS.getTableName()
                                           + " SET "
                                           + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                           + " = '"
                                           + ApplicabilityType.ROOT_ONLY.getApplicabilityName()
                                           + "' WHERE "
                                           + FieldsColumn.FIELD_APPLICABILITY.getColumnName()
                                           + " = 'Parents only';",
                                           ccddMain.getMainFrame());

                // Inform the user that updating the database data fields table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' data fields table conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the data fields table failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' data fields table to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '"
                                                                  + dbControl.getProjectName()
                                                                  + "' data fields table to new format "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the associations table to include a description column and to change the table
     * separator characters in the member_table column. Older versions of CCDD are not compatible
     * with the project database after applying this patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateAssociationsTable() throws CCDDException
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

            // Create lists to contain the old and new associations table items
            List<String[]> tableData = new ArrayList<String[]>();

            // Read the contents of the associations table
            ResultSet assnsData = dbCommand.executeDbQuery("SELECT * FROM "
                                                           + InternalTable.ASSOCIATIONS.getTableName()
                                                           + " ORDER BY OID;",
                                                           ccddMain.getMainFrame());

            // Check if the patch hasn't already been applied
            if (assnsData.getMetaData().getColumnCount() == 2)
            {
                // Check if the user elects to not apply the patch
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Apply patch to update the script "
                                                                                       + "associations table?<br><br></b>"
                                                                                       + "Incorporates a description column in the "
                                                                                       + "script associations table.<br><b><i>Older "
                                                                                       + "versions of CCDD will be incompatible "
                                                                                       + "with this project database after "
                                                                                       + "applying the patch",
                                                              "Apply Patch #07212017",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
                {
                    assnsData.close();
                    throw new CCDDException("user elected to not install patch (#0712017)");
                }

                // Step through each of the query results
                while (assnsData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[3];

                    // Step through each column in the row
                    for (int column = 0; column < 2; column++)
                    {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0. Also, shift the old data over one column to
                        // make room for the description
                        columnValues[column + 1] = assnsData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Add the row data to the list
                    tableData.add(columnValues);
                }

                assnsData.close();

                // Check if there are any associations in the table
                if (tableData.size() != 0)
                {
                    // Indicate in the log that the old data successfully loaded
                    eventLog.logEvent(SUCCESS_MSG,
                                      InternalTable.ASSOCIATIONS.getTableName()
                                                   + " retrieved");

                    // Step through each script association
                    for (int row = 0; row < tableData.size(); row++)
                    {
                        // Set the description to a blank and replace the table name separator
                        // characters with the new ones
                        tableData.set(row, new String[] {"",
                                                         tableData.get(row)[1],
                                                         tableData.get(row)[2].replaceAll(" \\+ ",
                                                                                          ASSN_TABLE_SEPARATOR)});
                    }
                }

                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

                // Store the updated associations table
                dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
                                              tableData,
                                              null,
                                              ccddMain.getMainFrame());

                // Inform the user that updating the database associations table completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' associations table conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the associations table failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' associations table to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '"
                                                                  + dbControl.getProjectName()
                                                                  + "' associations table to new format "
                                                                  + "(project database will be closed)");

            throw new CCDDException();
        }
    }

    /**********************************************************************************************
     * Update the project database comments to include the database name with capitalization and
     * special characters intact. The project database is first backed up to the file
     * <projectName>_<timeStamp>.dbu. The new format for the comment is <CCDD project identifier
     * string><lock status, 0 or 1>;<project name with capitalization intact>;<project
     * description>. Older versions of CCDD are compatible with the project database after applying
     * this patch
     *
     * @throws CCDDException
     *             If an error occurs while applying the patch
     *********************************************************************************************/
    private void updateDataBaseComment()
    {
        CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
        CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

        try
        {
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();

            // Get the comment for the currently open database
            String comment = dbControl.getDatabaseComment(dbControl.getDatabaseName());

            // Divide the comment into the lock status, visible name, and description
            String[] nameAndDesc = comment.split(DATABASE_COMMENT_SEPARATOR, 3);

            // Check if the comment isn't in the new format
            if (nameAndDesc.length < 3
                || !dbControl.getProjectName().equalsIgnoreCase(nameAndDesc[DatabaseComment.PROJECT_NAME.ordinal()]))
            {
                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

                // Update the project database comment to the new format
                dbCommand.executeDbCommand("COMMENT ON DATABASE "
                                           + dbControl.getDatabaseName()
                                           + " IS "
                                           + CcddDbTableCommandHandler.delimitText(CCDD_PROJECT_IDENTIFIER
                                                                                   + comment.substring(0, 1)
                                                                                   + DATABASE_COMMENT_SEPARATOR
                                                                                   + dbControl.getProjectName()
                                                                                   + DATABASE_COMMENT_SEPARATOR
                                                                                   + nameAndDesc[0].substring(1))
                                           + "; ",
                                           ccddMain.getMainFrame());

                // Inform the user that updating the database comment completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Project '"
                                                                   + dbControl.getProjectName()
                                                                   + "' comment conversion complete");
            }
        }
        catch (Exception e)
        {
            // Inform the user that converting the database comments failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Cannot convert project '"
                                                           + dbControl.getProjectName()
                                                           + "' comment to new format; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                  "<html><b>Cannot convert project '"
                                                                  + dbControl.getProjectName()
                                                                  + "' comment to new format");
        }
    }

    /**********************************************************************************************
     * Update the internal table __types to the new name __table_types, delete the primitive_only
     * column, and add the structure allowed and pointer allowed columns. If successful, the
     * original table (__types) is renamed, preserving the original information and preventing
     * subsequent conversion attempts. The project database is first backed up to the file
     * <projectName>_<timeStamp>.dbu. Older versions of CCDD are not compatible with the project
     * database after applying this patch
     *
     * @throws CCDDException
     *             If the user elects to not install the patch or an error occurs while applying
     *             the patch
     *********************************************************************************************/
    private void updateTableTypesTable() throws CCDDException
    {
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        // Check if the old table exists
        if (dbTable.isTableExists("__types", ccddMain.getMainFrame()))
        {
            // Check if the user elects to not apply the patch
            if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Apply patch to update the table types "
                                                                                   + "table?<br><br></b>Creates the new "
                                                                                   + "__table_types table from the old __types "
                                                                                   + "table.<br><b><i>Older versions of CCDD "
                                                                                   + "will be incompatible with this project "
                                                                                   + "database after applying the patch",
                                                          "Apply Patch #01262017",
                                                          JOptionPane.QUESTION_MESSAGE,
                                                          DialogOption.OK_CANCEL_OPTION) != OK_BUTTON)
            {
                throw new CCDDException("user elected to not install patch (#01262017)");
            }

            CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();
            CcddInputTypeHandler inputTypeHandler = ccddMain.getInputTypeHandler();

            try
            {
                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabaseName(),
                                         new FileEnvVar(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPath()
                                                        + File.separator
                                                        + dbControl.getDatabaseName()
                                                        + "_"
                                                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                        + FileExtension.DBU.getExtension()));

                // Create lists to contain the old and new table types table items
                List<String[]> oldTableData = new ArrayList<String[]>();
                List<String[]> newTableData = new ArrayList<String[]>();

                // Read the contents of the old table types table
                ResultSet infoData = dbCommand.executeDbQuery("SELECT * FROM __types ORDER BY OID;",
                                                              ccddMain.getMainFrame());

                // Step through each of the query results
                while (infoData.next())
                {
                    // Create an array to contain the column values
                    String[] columnValues = new String[infoData.getMetaData().getColumnCount()];

                    // Step through each column in the row
                    for (int column = 0; column < infoData.getMetaData().getColumnCount(); column++)
                    {
                        // Add the column value to the array. Note that the first column's index in
                        // the database is 1, not 0
                        columnValues[column] = infoData.getString(column + 1);

                        // Check if the value is null
                        if (columnValues[column] == null)
                        {
                            // Replace the null with a blank
                            columnValues[column] = "";
                        }
                    }

                    // Add the row data to the list
                    oldTableData.add(columnValues);
                }

                infoData.close();

                // Indicate in the log that the old data successfully loaded
                eventLog.logEvent(SUCCESS_MSG, "__types retrieved");

                // Step through the old table types column definitions
                for (String[] oldColumnDefn : oldTableData)
                {
                    boolean isFound = false;

                    // Create storage for the new column definition
                    String[] newColumnDefn = new String[InternalTable.TABLE_TYPES.getNumColumns()];

                    // Step through each of the old columns (the new table has one extra column)
                    for (int index = 0; index < TableTypesColumn.values().length - 1; index++)
                    {
                        // Copy the old columns definition to the new column definition
                        newColumnDefn[index] = oldColumnDefn[index];
                    }

                    // Get the default type definition for this table type name
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(oldColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);

                    // Check if the type exists in the default definitions
                    if (typeDefn != null)
                    {
                        // Get the index of the column
                        int column = typeDefn.getColumnIndexByDbName(oldColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()]);

                        // Check if the column exists in the default type definition
                        if (column != -1)
                        {
                            // Use the default definition to set the structure and pointer allowed
                            // flags
                            newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()] = typeDefn.isStructureAllowed()[column]
                                                                                                                                ? "t"
                                                                                                                                : "f";
                            newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()] = typeDefn.isPointerAllowed()[column]
                                                                                                                            ? "t"
                                                                                                                            : "f";
                            isFound = true;
                        }
                    }

                    // Check if this column isn't in the default column definitions
                    if (!isFound)
                    {
                        // Assume that this column is valid for a structures and pointers
                        newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()] = "t";
                        newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()] = "t";
                    }

                    // Add the column definition to the list
                    newTableData.add(newColumnDefn);
                }

                // Delete the default column definitions
                tableTypeHandler.getTypeDefinitions().clear();

                // Step through the updated table types column definitions
                for (String[] newColumnDefn : newTableData)
                {
                    // Get the type definition associated with this column
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);

                    // Check if the type is not defined
                    if (typeDefn == null)
                    {
                        // Create the type and add it to the list. THis creates the primary key and
                        // row index columns
                        typeDefn = tableTypeHandler.createTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()],
                                                                         new String[0][0],
                                                                         newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()]);
                    }

                    // Check if this column definition isn't for the primary key or row index since
                    // these were created previously
                    if (!newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.PRIMARY_KEY.getDbName())
                        && !newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.ROW_INDEX.getDbName()))
                    {
                        // Add the column names, description, input type, and flags to the type
                        // definition
                        typeDefn.addColumn(Integer.parseInt(newColumnDefn[TableTypesColumn.INDEX.ordinal()].toString()),
                                           newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].toString(),
                                           newColumnDefn[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()].toString(),
                                           newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()].toString(),
                                           inputTypeHandler.getInputTypeByName(newColumnDefn[TableTypesColumn.INPUT_TYPE.ordinal()].toString()),
                                           newColumnDefn[TableTypesColumn.ROW_VALUE_UNIQUE.ordinal()].equals("t")
                                                                                                                  ? true
                                                                                                                  : false,
                                           newColumnDefn[TableTypesColumn.COLUMN_REQUIRED.ordinal()].equals("t")
                                                                                                                 ? true
                                                                                                                 : false,
                                           newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()].equals("t")
                                                                                                                   ? true
                                                                                                                   : false,
                                           newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()].equals("t")
                                                                                                                 ? true
                                                                                                                 : false);
                    }
                }

                // Store the updated table type definitions in the project database and change the
                // old table types table name so that the conversion doesn't take place again
                dbCommand.executeDbCommand(dbTable.storeTableTypesInfoTableCommand()
                                           + "ALTER TABLE __types RENAME TO __types_backup;",
                                           ccddMain.getMainFrame());

                // Inform the user that converting the table types completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Table types conversion complete");

                // Reopen the database
                dbControl.openDatabase(dbControl.getProjectName());
            }
            catch (Exception e)
            {
                // Inform the user that converting the table types table failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot convert table types table to new format; cause '"
                                                               + e.getMessage()
                                                               + "'",
                                      "<html><b>Cannot convert table types table to new "
                                                                      + "format (project database will be closed)");
                throw new CCDDException();
            }
        }
    }
}
