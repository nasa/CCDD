/**
 * CFS Command & Data Dictionary project database patch handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.BACKUP_FILE_EXTENSION;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.io.File;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary project database patch handler class
 *****************************************************************************/
public class CcddPatchHandler
{
    private final CcddMain ccddMain;

    /**************************************************************************
     * CFS Command & Data Dictionary project database patch handler class
     * constructor. THe patch handler is used to integrate application changes
     * that require alteration of the project database schema. The alterations
     * are meant to be transparent to the user; however, once patched older
     * versions of the application are no longer guaranteed to function
     * properly and may have detrimental effects
     * 
     * * @param ccddMain main class
     *************************************************************************/
    CcddPatchHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Patch #01262017: Rename the table types table and alter its content
        updateTableTypesTable();
    }

    /**************************************************************************
     * Update the internal table __types to the new name __table_types, delete
     * the primitive_only column, and add the structure allowed and pointer
     * allowed columns. If successful, the original table (__types) is renamed,
     * preserving the original information and preventing subsequent conversion
     * attempts. The project database is first backed up to the file
     * <projectName>_<timeStamp>.dbu
     *************************************************************************/
    private void updateTableTypesTable()
    {
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        // Check if the old table exists
        if (dbTable.isTableExists("__types", ccddMain.getMainFrame()))
        {
            CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();
            CcddDbCommandHandler dbCommand = ccddMain.getDbCommandHandler();
            CcddEventLogDialog eventLog = ccddMain.getSessionEventLog();
            CcddTableTypeHandler tableTypeHandler = ccddMain.getTableTypeHandler();

            try
            {
                // Back up the project database before applying the patch
                dbControl.backupDatabase(dbControl.getDatabase(),
                                         new File(dbControl.getDatabase()
                                                  + "_"
                                                  + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime())
                                                  + "."
                                                  + BACKUP_FILE_EXTENSION));

                // Create lists to contain the old and new table types table
                // items
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
                        // Add the column value to the array. Note that the
                        // first column's index in the database is 1, not 0
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

                    // Step through each of the old columns (the new table has
                    // one extra column)
                    for (int index = 0; index < TableTypesColumn.values().length - 1; index++)
                    {
                        // Copy the old columns definition to the new column
                        // definition
                        newColumnDefn[index] = oldColumnDefn[index];
                    }

                    // Get the default type definition for this table type name
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(oldColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);

                    // Check if the type exists in the default definitions
                    if (typeDefn != null)
                    {
                        // Get the index of the column
                        int column = typeDefn.getColumnIndexByDbName(oldColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()]);

                        // Check if the column exists in the default type
                        // definition
                        if (column != -1)
                        {
                            // Use the default definition to set the structure
                            // and pointer allowed flags
                            newColumnDefn[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()] = typeDefn.isStructureAllowed()[column]
                                                                                                                               ? "t"
                                                                                                                               : "f";
                            newColumnDefn[TableTypesColumn.POINTER_ALLOWED.ordinal()] = typeDefn.isPointerAllowed()[column]
                                                                                                                           ? "t"
                                                                                                                           : "f";
                            isFound = true;
                        }
                    }

                    // Check if this column isn't in the default column
                    // definitions
                    if (!isFound)
                    {
                        // Assume that this column is valid for a structures
                        // and pointers
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
                        // Create the type and add it to the list. THis creates
                        // the primary key and row index columns
                        tableTypeHandler.createTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()],
                                                              new String[0][0],
                                                              newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()]);
                        typeDefn = tableTypeHandler.getTypeDefinition(newColumnDefn[TableTypesColumn.TYPE_NAME.ordinal()]);
                    }

                    // Check if this column definition isn't for the primary
                    // key or row index since these were created previously
                    if (!newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.PRIMARY_KEY.getDbName())
                        && !newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].equals(DefaultColumn.ROW_INDEX.getDbName()))
                    {
                        // Add the column names, description, input type, and
                        // flags to the type definition
                        typeDefn.addColumn(Integer.parseInt(newColumnDefn[TableTypesColumn.INDEX.ordinal()].toString()),
                                           newColumnDefn[TableTypesColumn.COLUMN_NAME_DB.ordinal()].toString(),
                                           newColumnDefn[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()].toString(),
                                           newColumnDefn[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()].toString(),
                                           InputDataType.getInputTypeByName(newColumnDefn[TableTypesColumn.INPUT_TYPE.ordinal()].toString()),
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

                // Store the updated table type definitions in the project
                // database
                dbTable.storeInformationTable(InternalTable.TABLE_TYPES,
                                              null,
                                              null,
                                              ccddMain.getMainFrame());

                // Change the old table types table name so that the conversion
                // doesn't take place again
                dbCommand.executeDbCommand("ALTER TABLE __types RENAME TO __types_backup;",
                                           ccddMain.getMainFrame());

                // Inform the user that converting the table types completed
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Table types conversion complete");
            }
            catch (Exception e)
            {
                // Inform the user that converting the table types table failed
                eventLog.logFailEvent(ccddMain.getMainFrame(),
                                      "Cannot convert table types table to new format; cause '"
                                          + e.getMessage()
                                          + "'",
                                      "<html><b>Cannot convert table types table to new format (project database will be closed)");
                dbControl.closeDatabase();
            }
        }
    }
}
