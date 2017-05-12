/**
 * CFS Command & Data Dictionary file I/O handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DATABASE_BACKUP_PATH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPT_DESCRIPTION_TAG;
import static CCDD.CcddConstants.TABLE_EXPORT_PATH;
import static CCDD.CcddConstants.USERS_GUIDE;

import java.awt.Component;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddImportExportInterface.ImportType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary file I/O handler class
 *****************************************************************************/
public class CcddFileIOHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddMacroHandler macroHandler;
    private CcddReservedMsgIDHandler rsvMsgIDHandler;
    private CcddTableEditorDialog tableEditorDlg;
    private final CcddEventLogDialog eventLog;

    // Flag indicating if table importing is canceled by user input
    private boolean cancelImport;

    /**************************************************************************
     * File I/O handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddFileIOHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();
    }

    /**************************************************************************
     * Set the references to the table type and macro handler classes
     *************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
    }

    /**************************************************************************
     * Extract the user's guide from the .jar file and display it. This command
     * is executed in a separate thread since it may take a noticeable amount
     * time to complete, and by using a separate thread the GUI is allowed to
     * continue to update
     *************************************************************************/
    protected void displayUsersGuide()
    {
        // Extract and display the help file in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Extract (if not already extracted) and display the help file
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Extract the help file name from the .jar package name
                String fileName = USERS_GUIDE.substring(USERS_GUIDE.lastIndexOf("/") + 1);

                InputStream is = null;

                try
                {
                    // Check if the Desktop class is not supported by the
                    // platform
                    if (!Desktop.isDesktopSupported())
                    {
                        // Set the error type message
                        throw new CCDDException("Desktop class unsupported");
                    }

                    // Create a path from the system's temporary file directory
                    // name and the user's guide file name
                    Path tempFile = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir")
                                                                     + "/"
                                                                     + fileName);

                    // Check if the file isn't already extracted to the
                    // temporary directory
                    if (!Files.exists(tempFile))
                    {
                        // Open an input stream using the user's guide within
                        // the .jar file
                        is = getClass().getResourceAsStream(USERS_GUIDE);

                        // Create a temporary file in the system's temporary
                        // file directory to which to copy the user's guide
                        tempFile = Files.createFile(tempFile);

                        // Delete the extracted file when the application exits
                        tempFile.toFile().deleteOnExit();

                        // Copy the user's guide to the temporary file
                        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Display the user's guide
                    Desktop.getDesktop().open(tempFile.toFile());
                }
                catch (Exception e)
                {
                    // Inform the user that an error occurred opening the
                    // user's guide
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>User's guide '"
                                                                  + fileName
                                                                  + "' cannot be opened; cause<br>'"
                                                                  + e.getMessage()
                                                                  + "'",
                                                              "File Error",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
                finally
                {
                    try
                    {
                        // Check if the input stream is open
                        if (is != null)
                        {
                            // Close the input stream
                            is.close();
                        }
                    }
                    catch (IOException ioe)
                    {
                        // Inform the user that the file cannot be closed
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot close user's guide file<br>'</b>"
                                                                      + fileName
                                                                      + "<b>'",
                                                                  "File Warning",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Backup the currently open project's database to a user-selected file
     * using the pg_dump utility. The backup data is stored in plain text
     * format
     * 
     * @param doInBackground
     *            true to perform the operation in a background process
     *************************************************************************/
    protected void backupDatabaseToFile(boolean doInBackground)
    {
        // Get the name of the currently open database
        String databaseName = dbControl.getDatabase();

        // Allow the user to select the backup file path + name
        File[] dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                 ccddMain.getMainFrame(),
                                                                 databaseName
                                                                     + FileExtension.DBU.getExtension(),
                                                                 null,
                                                                 new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                                                                                                                            FileExtension.DBU.getExtensionName())},

                                                                 false,
                                                                 false,
                                                                 "Backup Project "
                                                                     + databaseName,
                                                                 DATABASE_BACKUP_PATH,
                                                                 DialogOption.BACKUP_OPTION);

        // Check if a file was chosen
        if (dataFile != null && dataFile[0] != null)
        {
            boolean cancelBackup = false;

            // Check if the backup file exists
            if (dataFile[0].exists())
            {
                // Check if the existing file should be overwritten
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Overwrite existing backup file?",
                                                              "Overwrite File",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Check if the file can be deleted
                    if (!dataFile[0].delete())
                    {
                        // Inform the user that the existing backup file cannot
                        // be replaced
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot replace existing backup file<br>'</b>" +
                                                                      dataFile[0].getAbsolutePath()
                                                                      + "<b>'",
                                                                  "File Error",
                                                                  JOptionPane.ERROR_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                        cancelBackup = true;
                    }
                }
                // File should not be overwritten
                else
                {
                    // Cancel backing up the database
                    cancelBackup = true;
                }
            }

            // Check that no errors occurred and that the user didn't cancel
            // the backup
            if (!cancelBackup)
            {
                // Check if the operation should be performed in the background
                if (doInBackground)
                {
                    // Create a backup of the current database
                    dbControl.backupDatabaseInBackground(databaseName, dataFile[0]);
                }
                // Perform the operation in the foreground
                else
                {
                    // Create a backup of the current database
                    dbControl.backupDatabase(databaseName, dataFile[0]);
                }
            }
        }
    }

    /**************************************************************************
     * Restore a project's database from a user-selected backup file. The
     * backup is a plain text file containing the PostgreSQL commands necessary
     * to rebuild the database
     *************************************************************************/
    protected void restoreDatabaseFromFile()
    {
        // Allow the user to select the backup file path + name to load from
        File[] dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                 ccddMain.getMainFrame(),
                                                                 null,
                                                                 null,
                                                                 new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                                                                                                                            FileExtension.DBU.getExtensionName())},
                                                                 false,
                                                                 false,
                                                                 "Restore Project",
                                                                 DATABASE_BACKUP_PATH,
                                                                 DialogOption.RESTORE_OPTION);

        // Check if a file was chosen
        if (dataFile != null && dataFile[0] != null)
        {
            FileInputStream fis = null;
            FileChannel fc = null;

            try
            {
                // Check if the file doesn't exist
                if (!dataFile[0].exists())
                {
                    throw new CCDDException("Cannot locate backup file<br>'</b>"
                                            + dataFile[0].getAbsolutePath()
                                            + "'");
                }

                // Set up Charset and CharsetDecoder for ISO-8859-15
                Charset charset = Charset.forName("ISO-8859-15");
                CharsetDecoder decoder = charset.newDecoder();

                // Set up the pattern to match
                Pattern pattern = Pattern.compile("-- Name: ");

                // Pattern used to detect separate lines
                Pattern linePattern = Pattern.compile(".*\r?\n");

                boolean isFound = false;

                // Open the file and then get a channel from the stream
                fis = new FileInputStream(dataFile[0]);
                fc = fis.getChannel();

                // Get the file's size and then map it into memory
                MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY,
                                                     0,
                                                     fc.size());

                // Decode the file into a char buffer
                CharBuffer charBuffer = decoder.decode(byteBuffer);

                // Create the line and pattern matchers, then perform the
                // search
                Matcher lineMatch = linePattern.matcher(charBuffer);
                Matcher patternMatch = null;

                // For each line in the file
                while (lineMatch.find())
                {
                    // Get the line from the file
                    CharSequence charSeq = lineMatch.group();

                    // Check if this is the first pass through the loop for
                    // this file
                    if (patternMatch == null)
                    {
                        // Create a pattern matcher for the target pattern
                        // using the current line information
                        patternMatch = pattern.matcher(charSeq);
                    }
                    // Not the first pass
                    else
                    {
                        // Reset the matcher with the new line information
                        patternMatch.reset(charSeq);
                    }

                    // Check if the line contains the target pattern
                    if (patternMatch.find())
                    {
                        // Split the line read from the file in order to get
                        // the database name and owner
                        String[] parts = charSeq.toString().trim().split(";");

                        // Check if the necessary components of the comment
                        // exist
                        if (parts.length == 4)
                        {
                            // Extract the database name and owner
                            String[] databaseName = parts[0].split(":");
                            String[] databaseOwner = parts[3].split(":");

                            // Check that a name and owner exist
                            if (databaseName.length == 2
                                && databaseOwner.length == 2)
                            {
                                // Set the flag to indicate the database name
                                // and owner exist
                                isFound = true;

                                // Restore the database from the selected file
                                dbControl.restoreDatabase(databaseName[1].trim(),
                                                          databaseOwner[1].trim(),
                                                          dataFile[0]);

                                // Stop searching the file
                                break;
                            }
                        }
                    }

                    // Check if the end of the file has been reached
                    if (lineMatch.end() == charBuffer.limit())
                    {
                        // Exit the loop
                        break;
                    }
                }

                // Check if the database name and owner couldn't be located
                if (!isFound)
                {
                    throw new CCDDException("File<br>'</b>"
                                            + dataFile[0].getAbsolutePath()
                                            + "'<br><b> is not a backup file");
                }
            }
            catch (CCDDException ce)
            {
                // Inform the user that the backup file error occurred
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>"
                                                              + ce.getMessage(),
                                                          "File Error",
                                                          ce.getMessageType(),
                                                          DialogOption.OK_OPTION);
            }
            catch (Exception e)
            {
                // Inform the user that the backup file cannot be read
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Cannot read backup file<br>'</b>"
                                                              + dataFile[0].getAbsolutePath()
                                                              + "<b>'; cause '"
                                                              + e.getMessage()
                                                              + "'",
                                                          "File Error",
                                                          JOptionPane.ERROR_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
            finally
            {
                try
                {
                    // Check if the file channel is open
                    if (fc != null)
                    {
                        // Close the channel
                        fc.close();
                    }
                }
                catch (IOException ioe)
                {
                    // Inform the user that the file channel cannot be closed
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Cannot close backup file<br>'</b>"
                                                                  + dataFile[0].getAbsolutePath()
                                                                  + "<b>' (file channel)",
                                                              "File Warning",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
                finally
                {
                    try
                    {
                        // Check if the file stream is open
                        if (fis != null)
                        {
                            // Close the stream
                            fis.close();
                        }
                    }
                    catch (IOException ioe)
                    {
                        // Inform the user that the file input stream cannot be
                        // closed
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot close backup file<br>'</b>"
                                                                      + dataFile[0].getAbsolutePath()
                                                                      + "<b>' (file input stream)",
                                                                  "File Warning",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Import one or more files, creating new tables and optionally replacing
     * existing ones. The file(s) may contain definitions for more than one
     * table. This method is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     * 
     * @param dataFile
     *            array of files to import
     * 
     * @param backupFirst
     *            true to create a backup of the database before importing
     *            tables
     * 
     * @param replaceExisting
     *            true to replace a table that already exists in the database
     * 
     * @param appendExistingFields
     *            true to append the existing data fields for a table (if any)
     *            to the imported ones (if any). Only valid when
     *            replaceExisting is true
     * 
     * @param useExistingFields
     *            true to replace an existing data field with the imported ones
     *            if the field names match. Only valid when replaceExisting and
     *            appendExistingFields are true
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void importFile(final File[] dataFile,
                              final boolean backupFirst,
                              final boolean replaceExisting,
                              final boolean appendExistingFields,
                              final boolean useExistingFields,
                              final Component parent)
    {
        // Store the current table type, data type, macro, and reserved message
        // ID information in case it needs to be restored
        final List<String[]> originalDataTypes = dataTypeHandler.getDataTypeData();
        final List<String[]> originalMacros = macroHandler.getMacroData();
        final List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitions();
        final List<String[]> originalReservedMsgIDs = rsvMsgIDHandler.getReservedMsgIDData();

        // Execute the import operation in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            List<TableDefinition> allTableDefinitions = new ArrayList<TableDefinition>();
            List<String> duplicateDefinitions = new ArrayList<String>();

            boolean errorFlag = false;

            /******************************************************************
             * Import the selected table(s)
             *****************************************************************/
            @Override
            protected void execute()
            {
                CcddImportExportInterface ioHandler = null;

                // Create a reference to a table editor dialog
                tableEditorDlg = null;

                // Check if the user elected to back up the project before
                // importing tables
                if (backupFirst)
                {
                    // Back up the project database
                    backupDatabaseToFile(false);
                }

                // Step through each selected file
                for (File file : dataFile)
                {
                    try
                    {
                        // Check if the file doesn't exist
                        if (!file.exists())
                        {
                            throw new CCDDException("Cannot locate import file<br>'</b>"
                                                    + file.getAbsolutePath()
                                                    + "<b>'");
                        }

                        // Check if the file to import is in CSV format based
                        // on the extension
                        if (file.getAbsolutePath().endsWith(FileExtension.CSV.getExtension()))
                        {
                            // Create a CSV handler
                            ioHandler = new CcddCSVHandler(ccddMain, parent);
                        }
                        // Check if the file to import is in EDS XML format
                        // based on the extension
                        else if (file.getAbsolutePath().endsWith(FileExtension.EDS.getExtension()))
                        {
                            // Create an EDS handler
                            ioHandler = new CcddEDSHandler(ccddMain, parent);
                        }
                        // Check if the file to import is in JSON format based
                        // on the extension
                        else if (file.getAbsolutePath().endsWith(FileExtension.JSON.getExtension()))
                        {
                            // Create a JSON handler
                            ioHandler = new CcddJSONHandler(ccddMain, parent);
                        }
                        // Check if the file to import is in XTCE XML format
                        // based on the extension
                        else if (file.getAbsolutePath().endsWith(FileExtension.XTCE.getExtension()))
                        {
                            // Create an XTCE handler
                            ioHandler = new CcddXTCEHandler(ccddMain, parent);
                        }
                        // The file extension isn't recognized
                        else
                        {
                            throw new CCDDException("Cannot import file '"
                                                    + file.getAbsolutePath()
                                                    + "'; unrecognized file type");
                        }

                        // Check that no error occurred creating the format
                        // conversion handler
                        if (!ioHandler.getErrorStatus())
                        {
                            // Import the table definition(s) from the file
                            ioHandler.importFromFile(file, ImportType.IMPORT_ALL);

                            // Check if the user elected to append any new data
                            // fields to any existing ones for a table
                            if (appendExistingFields)
                            {
                                // Create a data field handler
                                CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain, null, parent);

                                // Step through each table definition
                                for (TableDefinition tableDefn : ioHandler.getTableDefinitions())
                                {
                                    // Build the field information for this
                                    // table
                                    fieldHandler.buildFieldInformation(tableDefn.getName());

                                    // Step through the imported data fields.
                                    // The order is reversed so that field
                                    // definitions can be removed if needed
                                    for (int index = tableDefn.getDataFields().size() - 1; index >= 0; index--)
                                    {
                                        String[] fieldDefn = tableDefn.getDataFields().get(index);

                                        // Get the reference to the data field
                                        // based on the table name and field
                                        // name
                                        FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()],
                                                                                                            fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]);

                                        // Check if the data field already
                                        // exists
                                        if (fieldInfo != null)
                                        {
                                            // Check if the original data field
                                            // information supersedes the
                                            // imported one
                                            if (useExistingFields)
                                            {
                                                // Remove the new data field
                                                // definition
                                                tableDefn.getDataFields().remove(index);
                                            }
                                            // The imported data field
                                            // information replaces the
                                            // original
                                            else
                                            {
                                                // Remove the original data
                                                // field definition
                                                fieldHandler.getFieldInformation().remove(fieldInfo);
                                            }
                                        }
                                    }

                                    // Combine the imported and existing data
                                    // fields
                                    tableDefn.getDataFields().addAll(fieldHandler.getFieldDefinitionList());
                                }
                            }

                            // Step through each table definition from the
                            // import file
                            for (TableDefinition newDefn : ioHandler.getTableDefinitions())
                            {
                                boolean isFound = false;

                                // Step through each table definition already
                                // in the list
                                for (TableDefinition existingDefn : allTableDefinitions)
                                {
                                    // Check if the table is already defined in
                                    // the list
                                    if (newDefn.getName().equals(existingDefn.getName()))
                                    {
                                        // Add the table name and associated
                                        // file name to the list of duplicates
                                        duplicateDefinitions.add(newDefn.getName()
                                                                 + " (file: "
                                                                 + file.getName()
                                                                 + ")");

                                        // Set the flag indicating the table
                                        // definition is a duplicate and stop
                                        // searching
                                        isFound = true;
                                        break;
                                    }
                                }

                                // Check if the table is not already defined
                                if (!isFound)
                                {
                                    // Add the table definition to the list
                                    allTableDefinitions.add(newDefn);
                                }
                            }
                        }
                        // An error occurred creating the format conversion
                        // handler
                        else
                        {
                            errorFlag = true;
                        }
                    }
                    catch (IOException ioe)
                    {
                        // Inform the user that the data file cannot be read
                        new CcddDialogHandler().showMessageDialog(parent,
                                                                  "<html><b>Cannot read import file<br>'</b>"
                                                                      + file.getAbsolutePath()
                                                                      + "<b>'",
                                                                  "File Error",
                                                                  JOptionPane.ERROR_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                        errorFlag = true;
                    }
                    catch (CCDDException ce)
                    {
                        // Check if an error message is provided
                        if (!ce.getMessage().isEmpty())
                        {
                            // Inform the user that an error occurred reading
                            // the import file
                            new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>"
                                                                          + ce.getMessage(),
                                                                      "File Error",
                                                                      ce.getMessageType(),
                                                                      DialogOption.OK_OPTION);
                        }

                        errorFlag = true;
                    }
                    catch (Exception e)
                    {
                        // Display a dialog providing details on the
                        // unanticipated error
                        CcddUtilities.displayException(e, parent);
                        errorFlag = true;
                    }
                }
            }

            /******************************************************************
             * Import table(s) command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if no errors occurred importing the table(s)
                if (!errorFlag)
                {
                    try
                    {
                        // Create the data tables from the imported table
                        // definitions from all files
                        createTablesFromDefinitions(allTableDefinitions,
                                                    replaceExisting,
                                                    parent);
                    }
                    catch (CCDDException ce)
                    {
                        // Check if an error message is provided
                        if (!ce.getMessage().isEmpty())
                        {
                            // Inform the user that an error occurred reading
                            // the import file
                            new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>"
                                                                          + ce.getMessage(),
                                                                      "File Error",
                                                                      ce.getMessageType(),
                                                                      DialogOption.OK_OPTION);
                        }

                        errorFlag = true;
                    }
                }

                // Check if no errors occurred importing and creating the
                // table(s)
                if (!errorFlag)
                {
                    // Store the data file path in the program preferences
                    // backing store
                    storePath(dataFile[0].getAbsolutePath(), true, TABLE_EXPORT_PATH);

                    // Update any open editor's data type columns to include
                    // the new table(s), if applicable
                    dbTable.updateDataTypeColumns(ccddMain.getMainFrame());

                    eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                      "Table import completed successfully");

                    // Check if any duplicate table definitions were detected
                    if (!duplicateDefinitions.isEmpty())
                    {
                        // Inform the user that one or more duplicate table
                        // definitions were detected
                        new CcddDialogHandler().showMessageDialog(parent,
                                                                  "<html><b>Ignored the following duplicate table definition(s):</b><br>"
                                                                      + dbTable.getShortenedTableNames(duplicateDefinitions.toArray(new String[0])),
                                                                  "Duplicate Table(s)",
                                                                  JOptionPane.INFORMATION_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }
                // An error occurred while importing the table(s)
                else
                {
                    // Restore the table types, data types, macros, and
                    // reserved message IDs to the values prior to the import
                    // operation
                    tableTypeHandler.setTypeDefinitions(originalTableTypes);
                    dataTypeHandler.setDataTypeData(originalDataTypes);
                    macroHandler.setMacroData(originalMacros);
                    rsvMsgIDHandler.setReservedMsgIDData(originalReservedMsgIDs);

                    eventLog.logFailEvent(parent,
                                          "Import Error",
                                          "Table import completed with errors",
                                          "<html><b>Table import completed with errors");
                }
            }
        });
    }

    /**************************************************************************
     * Create one or more data tables from the supplied table definitions
     * 
     * @param tableDefinitions
     *            list of table definitions for the table(s) to create
     * 
     * @param replaceExisting
     *            true to replace a table that already exists in the database
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    private void createTablesFromDefinitions(List<TableDefinition> tableDefinitions,
                                             boolean replaceExisting,
                                             final Component parent) throws CCDDException
    {
        cancelImport = false;
        boolean prototypesOnly = true;
        List<String> skippedTables = new ArrayList<String>();

        // Perform two passes; first to process prototype tables, and second to
        // process child tables
        for (int loop = 0; loop < 2 && !cancelImport; loop++)
        {
            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions)
            {
                // Check if the table import was canceled by the user
                if (cancelImport)
                {
                    // Add the table to the list of those skipped
                    skippedTables.add(tableDefn.getName());
                    continue;
                }

                // Check if cell data is provided in the import file. Creation
                // of empty tables is not allowed. Also check if this is a
                // prototype table and this is the first pass, or if this is a
                // child table and this is the second pass
                if (!tableDefn.getData().isEmpty()
                    && (!tableDefn.getName().contains(",") != !prototypesOnly))
                {
                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getType());

                    // Get the number of table columns
                    int numColumns = typeDefn.getColumnCountVisible();

                    // Create the table information for the new table
                    TableInformation tableInfo = new TableInformation(tableDefn.getType(),
                                                                      tableDefn.getName(),
                                                                      new String[0][0],
                                                                      tableTypeHandler.getDefaultColumnOrder(tableDefn.getType()),
                                                                      tableDefn.getDescription(),
                                                                      true,
                                                                      tableDefn.getDataFields().toArray(new Object[0][0]));

                    // Check if the new table is not a prototype
                    if (!tableInfo.isPrototype())
                    {
                        // Break the path into the individual structure
                        // variable references
                        String[] ancestors = tableInfo.getTablePath().split(",");

                        // Step through each structure table referenced in the
                        // path of the new table
                        for (int index = ancestors.length - 1; index >= 0 && !cancelImport; index--)
                        {
                            // Split the ancestor into the data type (i.e.,
                            // structure name) and variable name
                            String[] typeAndVar = ancestors[index].split("\\.");

                            // Check if the ancestor prototype table doesn't
                            // exist
                            if (!dbTable.isTableExists(typeAndVar[0].toLowerCase(),
                                                       ccddMain.getMainFrame()))
                            {
                                // Create the table information for the new
                                // prototype table
                                TableInformation descendantInfo = new TableInformation(tableDefn.getType(),
                                                                                       typeAndVar[0],
                                                                                       new String[0][0],
                                                                                       tableTypeHandler.getDefaultColumnOrder(tableDefn.getType()),
                                                                                       "",
                                                                                       true,
                                                                                       tableDefn.getDataFields().toArray(new Object[0][0]));

                                // Check if this is the child table and not one
                                // of its ancestors
                                if (index == ancestors.length - 1)
                                {
                                    // Create a list to store a copy of the
                                    // cell data
                                    List<String> protoData = new ArrayList<String>(tableDefn.getData());

                                    // Step through each row of the cell data
                                    for (int cellIndex = 0; cellIndex < tableDefn.getData().size(); cellIndex += numColumns)
                                    {
                                        // Step through each column in the row
                                        for (int colIndex = 0; colIndex < numColumns; colIndex++)
                                        {
                                            // Check if the column is not
                                            // protected
                                            if (!DefaultColumn.isProtectedColumn(typeDefn.getName(),
                                                                                 typeDefn.getColumnNamesVisible()[colIndex]))
                                            {
                                                // Replace the non-protected
                                                // column value with a blank
                                                protoData.set(cellIndex + colIndex, "");
                                            }
                                        }
                                    }

                                    // Create the prototype of the child table
                                    // and populate it with the protected
                                    // column data
                                    if (!createImportedTable(descendantInfo,
                                                             protoData,
                                                             numColumns,
                                                             replaceExisting,
                                                             "Cannot create prototype '"
                                                                 + descendantInfo.getPrototypeName()
                                                                 + "' of child table",
                                                             parent))
                                    {
                                        // Add the skipped table to the list
                                        skippedTables.add(descendantInfo.getProtoVariableName());
                                    }
                                }
                                // This is an ancestor of the child table
                                else
                                {
                                    // Split the descendant into the data type
                                    // (i.e., structure name) and variable name
                                    typeAndVar = ancestors[index + 1].split("\\.|$", -1);

                                    // Add the variable reference to the new
                                    // table
                                    String[] rowData = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(rowData, "");
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.VARIABLE))] = typeAndVar[1];
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.PRIM_AND_STRUCT))] = typeAndVar[0];

                                    // Create the prototype of the child table
                                    // and populate it with the protected
                                    // column data
                                    if (!createImportedTable(descendantInfo,
                                                             Arrays.asList(rowData),
                                                             numColumns,
                                                             replaceExisting,
                                                             "Cannot create prototype '"
                                                                 + descendantInfo.getPrototypeName()
                                                                 + "' of child table's ancestor",
                                                             parent))
                                    {
                                        // Add the skipped table to the list
                                        skippedTables.add(descendantInfo.getProtoVariableName());
                                    }
                                }
                            }
                        }

                        // Load the table's prototype data from the database
                        // and copy the prototype's data to the table
                        TableInformation protoInfo = dbTable.loadTableData(tableInfo.getPrototypeName(),
                                                                           true,
                                                                           true,
                                                                           false,
                                                                           false,
                                                                           ccddMain.getMainFrame());
                        tableInfo.setData(protoInfo.getData());
                    }

                    // Create a table from the imported information
                    if (!createImportedTable(tableInfo,
                                             tableDefn.getData(),
                                             numColumns,
                                             replaceExisting,
                                             "Cannot create prototype '"
                                                 + tableInfo.getPrototypeName()
                                                 + "'",
                                             parent))
                    {
                        // Add the skipped table to the list
                        skippedTables.add(tableInfo.getProtoVariableName());
                    }
                }
            }

            prototypesOnly = false;
        }

        // Check if any tables were skipped
        if (!skippedTables.isEmpty())
        {
            // Inform the user that one or more tables were not imported
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Table(s) not imported<br>'</b>" +
                                                          dbTable.getShortenedTableNames(skippedTables.toArray(new String[0]))
                                                          + "<b>';<br>table already exists",
                                                      "Import Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        // Store the table types
        dbTable.storeInformationTable(InternalTable.TABLE_TYPES,
                                      null,
                                      null,
                                      parent);

        // Store the data types
        dbTable.storeInformationTable(InternalTable.DATA_TYPES,
                                      CcddUtilities.removeArrayListColumn(dataTypeHandler.getDataTypeData(),
                                                                          DataTypesColumn.OID.ordinal()),
                                      null,
                                      parent);

        // Check if any macros are defined
        if (!macroHandler.getMacroData().isEmpty())
        {
            // Store the macros in the database
            dbTable.storeInformationTable(InternalTable.MACROS,
                                          CcddUtilities.removeArrayListColumn(macroHandler.getMacroData(),
                                                                              MacrosColumn.OID.ordinal()),
                                          null,
                                          parent);

        }

        // Check if any reserved message IDs are defined
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
        {
            // Store the reserved message IDs in the database
            dbTable.storeInformationTable(InternalTable.RESERVED_MSG_IDS,
                                          CcddUtilities.removeArrayListColumn(rsvMsgIDHandler.getReservedMsgIDData(),
                                                                              ReservedMsgIDsColumn.OID.ordinal()),
                                          null,
                                          parent);

        }
    }

    /**************************************************************************
     * Create a new data table or replace an existing one and paste the
     * supplied cell data into it
     * 
     * @param tableInfo
     *            table information for the table to create
     * 
     * @param cellData
     *            array containing the cell data
     * 
     * @param numColumns
     *            number of columns in the table
     * 
     * @param replaceExisting
     *            true to replace a table that already exists in the database
     * 
     * @param errorMsg
     *            error message prefix used in the event an error occurs
     * 
     * @return true if the table is successfully imported; false if the table
     *         is an existing prototype and the replaceExisting flag is not
     *         true
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return ImportResult.SUCCESSFUL if the table is imported,
     *         ImportResult.SKIPPED if the table exists and the flag is not set
     *         to replace existing tables, or ImportResult.CANCELED if an error
     *         occurs when pasting the data and the user selected the Cancel
     *         button
     *************************************************************************/
    private boolean createImportedTable(TableInformation tableInfo,
                                        List<String> cellData,
                                        int numColumns,
                                        boolean replaceExisting,
                                        String errorMsg,
                                        Component parent) throws CCDDException
    {
        boolean isImported = true;
        List<String[]> tableName = new ArrayList<String[]>();

        // Check if this table is a prototype
        if (tableInfo.isPrototype())
        {
            // Check if the table already exists in the project database
            if (dbTable.isTableExists(tableInfo.getPrototypeName().toLowerCase(),
                                      parent))
            {
                // Check if the user didn't elect to replace existing tables
                if (!replaceExisting)
                {
                    // Set the flag to indicate the prototype table wasn't
                    // created since it already exists and the user didn't
                    // elect to overwrite existing tables
                    isImported = false;
                }

                // Delete the existing table from the database
                if (dbTable.deleteTable(new String[] {tableInfo.getPrototypeName()},
                                        null,
                                        ccddMain.getMainFrame()))
                {
                    throw new CCDDException();
                }
            }

            // Create the table in the database
            if (dbTable.createTable(new String[] {tableInfo.getPrototypeName()},
                                    tableInfo.getDescription(),
                                    tableInfo.getType(),
                                    ccddMain.getMainFrame()))
            {
                throw new CCDDException();
            }

            // Add the prototype table name to the list of table editors to
            // close
            tableName.add(new String[] {tableInfo.getPrototypeName(), null});
        }
        // Not a prototype table
        else
        {
            // Add the parent and prototype table name to the list of table
            // editors to close
            tableName.add(new String[] {tableInfo.getParentTable(),
                                        tableInfo.getPrototypeName()});
        }

        // Check if the prototype was successfully created, or if the table
        // isn't a prototype
        if (isImported)
        {
            // Close any editors associated with this prototype table
            dbTable.closeDeletedTableEditors(tableName, ccddMain.getMainFrame());

            // Create a list to hold the table's information
            List<TableInformation> tableInformation = new ArrayList<TableInformation>();
            tableInformation.add(tableInfo);

            // Check if a table editor dialog has not already been created for
            // the added tables. All of the new tables are opened in this
            // editor dialog
            if (tableEditorDlg == null)
            {
                // Create a table editor dialog and open the new table editor
                // in it
                tableEditorDlg = new CcddTableEditorDialog(ccddMain, tableInformation);
                ccddMain.getTableEditorDialogs().add(tableEditorDlg);
            }
            // A table editor dialog is already created
            else
            {
                // Add the table editor to the existing editor dialog
                tableEditorDlg.addTablePanes(tableInformation);
            }

            // Get the reference to the table's editor
            CcddTableEditorHandler tableEditor = tableEditorDlg.getTableEditor();

            // Paste the data into the table; check if the user canceled
            // importing the table following a cell validation error
            if (tableEditor.getTable().pasteData(cellData.toArray(new String[0]),
                                                 numColumns,
                                                 false,
                                                 true,
                                                 true))
            {
                // Set the flags to indicate that importing should stop and
                // that this table is not imported
                cancelImport = true;
                isImported = false;
            }
            // The data was pasted without being canceled by the user
            else
            {
                // Build the addition, modification, and deletion command lists
                tableEditor.buildUpdates();

                // Perform the changes to the table in the database
                if (dbTable.modifyTableData(tableEditor.getTableInformation(),
                                            tableEditor.getAdditions(),
                                            tableEditor.getModifications(),
                                            tableEditor.getDeletions(),
                                            false,
                                            null,
                                            null,
                                            ccddMain.getMainFrame()))
                {
                    throw new CCDDException();
                }
            }
        }

        return isImported;
    }

    /**************************************************************************
     * Import the contents of a file selected by the user into the specified
     * existing table
     * 
     * @param tableHandler
     *            reference to the table handler for the table into which to
     *            import the data
     *************************************************************************/
    protected void importSelectedFileIntoTable(CcddTableEditorHandler tableHandler)
    {
        // Allow the user to select the data file path + name to import from
        File[] dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                 tableHandler.getOwner(),
                                                                 null,
                                                                 "export",
                                                                 new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.CSV.getDescription(),
                                                                                                                            FileExtension.CSV.getExtensionName()),
                                                                                                new FileNameExtensionFilter(FileExtension.EDS.getDescription(),
                                                                                                                            FileExtension.EDS.getExtensionName()),
                                                                                                new FileNameExtensionFilter(FileExtension.JSON.getDescription(),
                                                                                                                            FileExtension.JSON.getExtensionName()),
                                                                                                new FileNameExtensionFilter(FileExtension.XTCE.getDescription(),
                                                                                                                            FileExtension.XTCE.getExtensionName())},
                                                                 false,
                                                                 false,
                                                                 "Load Table Data",
                                                                 TABLE_EXPORT_PATH,
                                                                 DialogOption.LOAD_OPTION);

        // Check if a file was chosen
        if (dataFile != null && dataFile[0] != null)
        {
            try
            {
                List<TableDefinition> tableDefinitions = null;
                CcddImportExportInterface ioHandler = null;

                // Check if the file to import is in CSV format based on the
                // extension
                if (dataFile[0].getAbsolutePath().endsWith(FileExtension.CSV.getExtension()))
                {
                    // Create a CSV handler
                    ioHandler = new CcddCSVHandler(ccddMain, tableHandler.getOwner());
                }
                // Check if the file to import is in EDS XML format based on
                // the extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.EDS.getExtension()))
                {
                    // Create an EDS handler
                    ioHandler = new CcddEDSHandler(ccddMain, tableHandler.getOwner());
                }
                // Check if the file to import is in JSON format based on the
                // extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.JSON.getExtension()))
                {
                    // Create a JSON handler
                    ioHandler = new CcddJSONHandler(ccddMain, tableHandler.getOwner());
                }
                // Check if the file to import is in XTCE XML format based on
                // the extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.XTCE.getExtension()))
                {
                    // Create an XTCE handler
                    ioHandler = new CcddXTCEHandler(ccddMain, tableHandler.getOwner());
                }
                // The file extension isn't recognized
                else
                {
                    throw new CCDDException("Cannot import file '"
                                            + dataFile[0].getAbsolutePath()
                                            + "' into table; unrecognized file type");
                }

                // Check that no error occurred creating the format conversion
                // handler
                if (!ioHandler.getErrorStatus())
                {
                    // Store the current table type information so that it can
                    // be restored
                    List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitions();

                    // Import the data file into a table definition
                    ioHandler.importFromFile(dataFile[0], ImportType.FIRST_DATA_ONLY);
                    tableDefinitions = ioHandler.getTableDefinitions();

                    // Check if a table definition was successfully created
                    if (tableDefinitions != null && !tableDefinitions.isEmpty())
                    {
                        // Paste the data from the table definition into the
                        // specified table
                        pasteIntoTableFromDefinition(tableHandler,
                                                     tableDefinitions.get(0),
                                                     tableHandler.getOwner());

                        // Restore the table types to the values prior to the
                        // import operation
                        tableTypeHandler.setTypeDefinitions(originalTableTypes);

                        // Store the data file path in the program preferences
                        // backing store
                        storePath(dataFile[0].getAbsolutePath(), false, TABLE_EXPORT_PATH);
                    }
                }
            }
            catch (IOException ioe)
            {
                // Inform the user that the data file cannot be read
                new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                                          "<html><b>Cannot read import file<br>'</b>"
                                                              + dataFile[0].getAbsolutePath()
                                                              + "<b>'",
                                                          "File Error",
                                                          JOptionPane.ERROR_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
            catch (CCDDException ce)
            {
                // Check if an error message is provided
                if (!ce.getMessage().isEmpty())
                {
                    // Inform the user that an error occurred reading the
                    // import file
                    new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                                              "<html><b>"
                                                                  + ce.getMessage(),
                                                              "File Error",
                                                              ce.getMessageType(),
                                                              DialogOption.OK_OPTION);
                }
            }
            catch (Exception e)
            {
                // Display a dialog providing details on the unanticipated
                // error
                CcddUtilities.displayException(e, tableHandler.getOwner());
            }
        }
    }

    /**************************************************************************
     * Paste the data in a table definition into the specified table
     * 
     * @param tableHandler
     *            reference to the table handler for the table into which to
     *            import the data
     * 
     * @param tableDefn
     *            table definition containing the data to paste
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    private void pasteIntoTableFromDefinition(CcddTableEditorHandler tableHandler,
                                              TableDefinition tableDefn,
                                              final Component parent)
    {
        // Update the table description field in case the description changed
        tableHandler.setDescription(tableDefn.getDescription());

        // Update the field information in case the field values changed
        tableHandler.getDataFieldHandler().setFieldDefinitions(tableDefn.getDataFields());
        tableHandler.getDataFieldHandler().buildFieldInformation(tableDefn.getName());

        // Rebuild the table's editor panel which contains the data fields
        tableHandler.createDataFieldPanel(true);

        // Check if cell data is provided in the table definition
        if (tableDefn.getData() != null && !tableDefn.getData().isEmpty())
        {
            // Get the original number of rows in the table
            int numRows = tableHandler.getTableModel().getRowCount();

            // Paste the data into the table; check if the user hasn't canceled
            // importing the table following a cell validation error
            if (!tableHandler.getTable().pasteData(tableDefn.getData().toArray(new String[0]),
                                                   tableHandler.getTable().getColumnCount(),
                                                   true,
                                                   true,
                                                   true))
            {
                // Let the user know how many rows were added
                new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                                          "<html><b>"
                                                              + (tableHandler.getTableModel().getRowCount()
                                                              - numRows)
                                                              + " row(s) added",
                                                          "Paste Table Data",
                                                          JOptionPane.INFORMATION_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**************************************************************************
     * Export the contents of one or more tables selected by the user to one or
     * more files in the specified format. The export file names are based on
     * the table name if each table is stored in a separate file. The user
     * supplied file name is used if multiple tables are stored in a single
     * file. This method is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     * 
     * @param filePath
     *            path to the folder in which to store the exported tables.
     *            Includes the name if storing the tables to a single file
     * 
     * @param tblVarNames
     *            array of the combined table and variable name for the
     *            table(s) to load
     * 
     * @param tablePaths
     *            table path for each table to load
     * 
     * @param overwriteFile
     *            true to store overwrite an existing file; false skip
     *            exporting a table to a file that already exists
     * 
     * @param singleFile
     *            true to store multiple tables in a single file; false to
     *            store each table in a separate file
     * 
     * @param replaceMacros
     *            true to replace macros with their corresponding values; false
     *            to leave the macros intact
     * 
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table
     *            in the export file
     * 
     * @param fileExtn
     *            file extension type
     * 
     * @param system
     *            name of the data field containing the system name
     * 
     * @param version
     *            version attribute (XTCE only)
     * 
     * @param validationStatus
     *            validation status attribute (XTCE only)
     * 
     * @param classification1
     *            first level classification attribute (XTCE only)
     * 
     * @param classification2
     *            second level classification attribute (XTCE only)
     * 
     * @param classification3
     *            third level classification attribute (XTCE only)
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void exportSelectedTables(final String filePath,
                                        final String[] tblVarNames,
                                        final String[] tablePaths,
                                        final boolean overwriteFile,
                                        final boolean singleFile,
                                        final boolean replaceMacros,
                                        final boolean includeReservedMsgIDs,
                                        final FileExtension fileExtn,
                                        final String system,
                                        final String version,
                                        final String validationStatus,
                                        final String classification1,
                                        final String classification2,
                                        final String classification3,
                                        final Component parent)
    {
        // Execute the export operation in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            // Remove the trailing period if present
            String path = CcddUtilities.removeTrailer(filePath, ".");

            /******************************************************************
             * Export the selected table(s)
             *****************************************************************/
            @Override
            protected void execute()
            {
                File file = null;
                CcddImportExportInterface ioHandler = null;
                List<String> skippedTables = new ArrayList<String>();

                // Check if the user elected to store all tables in a single
                // file. The path must include a file name
                if (singleFile)
                {
                    // Check if the file name doesn't end with the expected
                    // extension
                    if (!path.endsWith(fileExtn.getExtension()))
                    {
                        // Append the extension to the file name
                        path += fileExtn.getExtension();
                    }

                    // Create the file using the supplied name
                    file = new File(path);
                }
                // The table(s) are to be stored in individual files, so
                // the path doesn't include a file name. Check if the path
                // doesn't terminate with a name separator character
                else if (!path.endsWith(File.separator))
                {
                    // Append the name separator character to the path
                    path += File.separator;
                }

                // Check if the output format is CSV
                if (fileExtn == FileExtension.CSV)
                {
                    // Create a CSV handler
                    ioHandler = new CcddCSVHandler(ccddMain, parent);
                }
                // Check if the output format is EDS XML
                else if (fileExtn == FileExtension.EDS)
                {
                    // Create an EDS handler
                    ioHandler = new CcddEDSHandler(ccddMain, parent);
                }
                // Check if the output format is JSON
                else if (fileExtn == FileExtension.JSON)
                {
                    // Create an JSON handler
                    ioHandler = new CcddJSONHandler(ccddMain, parent);
                }
                // Check if the output format is XTCE XML
                else if (fileExtn == FileExtension.XTCE)
                {
                    // Create an XTCE handler
                    ioHandler = new CcddXTCEHandler(ccddMain, parent);
                }

                // Check that no error occurred creating the format conversion
                // handler
                if (!ioHandler.getErrorStatus())
                {
                    // Check if the tables are to be exported to a single file
                    if (singleFile)
                    {
                        // Check if the file doesn't exist, or if it does and
                        // the user elects to overwrite it
                        if (isOverwriteExportFileIfExists(file,
                                                          overwriteFile,
                                                          parent))
                        {
                            // Export the formatted table data to the specified
                            // file
                            if (ioHandler.exportToFile(file,
                                                       tablePaths,
                                                       replaceMacros,
                                                       includeReservedMsgIDs,
                                                       system,
                                                       version,
                                                       validationStatus,
                                                       classification1,
                                                       classification2,
                                                       classification3))
                            {
                                errorFlag = true;
                            }
                        }
                        else
                        {
                            // Add the skipped table to the list
                            skippedTables.addAll(Arrays.asList(tablePaths));
                        }
                    }
                    // Export the table(s) to individual files
                    else
                    {
                        // Step through each table
                        for (String tblName : tblVarNames)
                        {
                            // Create the file using a name derived from the
                            // table name
                            file = new File(path
                                            + tblName.replaceAll("[\\[\\]]", "_")
                                            + fileExtn.getExtension());

                            // Check if the file doesn't exist, or if it does
                            // and the user elects to overwrite it
                            if (isOverwriteExportFileIfExists(file,
                                                              overwriteFile,
                                                              parent))
                            {
                                // Export the formatted table data; the file
                                // name is derived from the table name
                                if (ioHandler.exportToFile(file,
                                                           new String[] {tblName},
                                                           replaceMacros,
                                                           includeReservedMsgIDs,
                                                           system,
                                                           version,
                                                           validationStatus,
                                                           classification1,
                                                           classification2,
                                                           classification3))
                                {
                                    errorFlag = true;
                                }
                            }
                            // The table is skipped
                            else
                            {
                                // Add the skipped table to the list
                                skippedTables.add(tblName);
                            }
                        }
                    }

                    // Check if any tables were skipped
                    if (!skippedTables.isEmpty())
                    {
                        // Inform the user that one or more tables were not
                        // exported
                        new CcddDialogHandler().showMessageDialog(parent,
                                                                  "<html><b>Table(s) not exported<br>'</b>" +
                                                                      dbTable.getShortenedTableNames(skippedTables.toArray(new String[0]))
                                                                      + "<b>';<br>output file already exists or file I/O error",
                                                                  "Export Error",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Store the export file path in the program preferences
                    // backing store
                    storePath(filePath, false, TABLE_EXPORT_PATH);
                }
                // An error occurred creating the format conversion handler
                else
                {
                    errorFlag = true;
                }
            }

            /******************************************************************
             * Export selected table(s) command complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check if no errors occurred exporting the table(s)
                if (!errorFlag)
                {
                    eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                      "Table export completed successfully");
                }
                // An error occurred while exporting the table(s)
                else
                {
                    eventLog.logFailEvent(parent,
                                          "Export Error",
                                          "Table export completed with errors",
                                          "<html><b>Table export completed with errors");
                }
            }
        });
    }

    /**************************************************************************
     * Check if the specified data file exists and, if so, whether or not the
     * user elects to overwrite it
     * 
     * @param exportFile
     *            reference to the file
     * 
     * @param overwriteFile
     *            true to overwrite an existing file
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return true if the file doesn't exist, or if it does exist and the user
     *         elects to overwrite it; false if the file exists and the user
     *         elects not to overwrite it, or if an error occurs deleting the
     *         existing file or creating a new one
     *************************************************************************/
    private boolean isOverwriteExportFileIfExists(File exportFile,
                                                  boolean overwriteFile,
                                                  Component parent)
    {
        // Set the continue flag based on if the file exists
        boolean continueExport = !exportFile.exists();

        try
        {
            // Check if the data file exists and the user has elected to
            // overwriting existing files
            if (exportFile.exists() && overwriteFile)
            {
                // Check if the file can't be deleted
                if (!exportFile.delete())
                {
                    throw new CCDDException("Cannot replace");
                }

                // Check if the data file cannot be created
                if (!exportFile.createNewFile())
                {
                    throw new CCDDException("Cannot create");
                }

                // Enable exporting the table
                continueExport = true;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the data file cannot be created
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>"
                                                          + ce.getMessage()
                                                          + " export file<br>'</b>" +
                                                          exportFile.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be written to
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot write to export file<br>'</b>"
                                                          + exportFile.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return continueExport;
    }

    /**************************************************************************
     * Store the contents of the specified script file into the database
     * 
     * @param file
     *            reference to the file to store
     *************************************************************************/
    protected void storeScriptInDatabase(File file)
    {
        BufferedReader br = null;

        try
        {
            List<String[]> scriptData = new ArrayList<String[]>();
            int lineNum = 1;

            // Read the table data from the selected file
            br = new BufferedReader(new FileReader(file));

            // Read the first line in the file
            String line = br.readLine();

            // Initialize the script description text
            String description = "";

            // Continue to read the file until EOF is reached
            while (line != null)
            {
                // Check if no script description has been found
                if (description.isEmpty())
                {
                    // Get the index of the description tag (-1 if not found).
                    // Force to lower case to remove case sensitivity
                    int index = line.toLowerCase().indexOf(SCRIPT_DESCRIPTION_TAG);

                    // Check if the description tag is found
                    if (index != -1)
                    {
                        // Extract the description from the file line
                        description = line.substring(index
                                                     + SCRIPT_DESCRIPTION_TAG.length()).trim();
                    }
                }

                // Add the line from the script file
                scriptData.add(new String[] {String.valueOf(lineNum), line});

                // Read the next line in the file
                line = br.readLine();
                lineNum++;
            }

            // Store the script file in the project's database
            dbTable.storeInformationTableInBackground(InternalTable.SCRIPT,
                                                      scriptData,
                                                      file.getName() + "," + description,
                                                      ccddMain.getMainFrame());
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot read script file<br>'</b>" +
                                                          file.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
        finally
        {
            try
            {
                // Check if the buffered reader was opened
                if (br != null)
                {
                    // Close the file
                    br.close();
                }
            }
            catch (IOException ioe)
            {
                // Inform the user that the file cannot be closed
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Cannot close script file<br>'</b>"
                                                              + file.getAbsolutePath()
                                                              + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**************************************************************************
     * Retrieve the contents of the specified script from the database and save
     * it to a file
     * 
     * @param script
     *            name of the script to retrieve
     * 
     * @param file
     *            reference to the script file
     *************************************************************************/
    protected void retrieveScriptFromDatabase(String script, File file)
    {
        PrintWriter pw = null;

        try
        {
            // Get the script file contents from the database
            List<String[]> lines = dbTable.retrieveInformationTable(InternalTable.SCRIPT,
                                                                    false,
                                                                    script,
                                                                    ccddMain.getMainFrame());

            boolean cancelRetrieve = false;

            // Check if the data file exists
            if (file.exists())
            {
                // Check if the existing file should be overwritten
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Overwrite existing script file?",
                                                              "Overwrite File",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Check if the existing file can't be deleted
                    if (!file.delete())
                    {
                        throw new CCDDException("Cannot replace");
                    }
                }
                // File should not be overwritten
                else
                {
                    // Cancel retrieving the script
                    cancelRetrieve = true;
                }
            }

            // Check that the user didn't cancel the export
            if (!cancelRetrieve)
            {
                // Check if the script file can't be created
                if (!file.createNewFile())
                {
                    throw new CCDDException("Cannot create");
                }

                // Output the table data to the selected file
                pw = new PrintWriter(file);

                // Step through each row in the table
                for (String[] line : lines)
                {
                    // Output the line contents (ignore the line number)
                    pw.println(line[1]);
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the script file cannot be created
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>"
                                                          + ce.getMessage()
                                                          + " script file<br>'</b>"
                                                          + file.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (IOException ioe)
        {
            // Inform the user that the script file cannot be written to
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot write to script file<br>'</b>"
                                                          + file.getAbsolutePath()
                                                          + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
        finally
        {
            // Check if the PrintWriter was opened
            if (pw != null)
            {
                // Close the script file
                pw.close();
            }
        }
    }

    /**************************************************************************
     * Store the specified file path in the program preferences backing store
     * 
     * @param pathName
     *            file path
     * 
     * @param hasFileName
     *            true if the file path includes a file name. The file name is
     *            removed before storing the path
     * 
     * @param fileKey
     *            key to store the file path into the program preferences
     *            backing store
     ************************************************************************/
    protected void storePath(String pathName, boolean hasFileName, String fileKey)
    {
        // Check if the file path includes a file name
        if (hasFileName)
        {
            // Strip the file name from the path
            pathName = pathName.substring(0, pathName.lastIndexOf(File.separator));
        }

        // Check if the path name doesn't end with a period
        if (!pathName.endsWith("."))
        {
            // Append "/." (or "\.") to the path name so that next time the
            // file chooser will go all the way into the selected path
            pathName += File.separator + ".";
        }

        // Store the file path in the program preferences backing store
        ccddMain.getProgPrefs().put(fileKey, pathName);
    }

    /**************************************************************************
     * Open the specified file for writing
     * 
     * @param outputFileName
     *            output file path + name
     * 
     * @return PrintWriter object; null if the file could not be opened
     *************************************************************************/
    public PrintWriter openOutputFile(String outputFileName)
    {
        PrintWriter printWriter = null;

        try
        {
            // Create the file object
            File outputFile = new File(outputFileName);

            // Check if the file already exists, and if so that it is
            // successfully deleted
            if (outputFile.exists() && !outputFile.delete())
            {
                throw new CCDDException("Cannot replace");
            }
            // Check if the output file is successfully created
            else if (outputFile.createNewFile())
            {
                // Create the PrintWriter object
                printWriter = new PrintWriter(outputFile);
            }
            // The output file cannot be created
            else
            {
                throw new CCDDException("Cannot create");
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the output file cannot be created
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>"
                                                          + ce.getMessage()
                                                          + " output file<br>'</b>" +
                                                          outputFileName
                                                          + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (Exception e)
        {
            // Inform the user that the output file cannot be opened
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot open output file<br>'</b>" +
                                                          outputFileName
                                                          + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return printWriter;
    }

    /**************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     * 
     * @param printWriter
     *            output file PrintWriter object
     * 
     * @param text
     *            text to write to the output file
     *************************************************************************/
    public void writeToFile(PrintWriter printWriter, String text)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file
            printWriter.print(text);
        }
    }

    /**************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     * and append a line feed character
     * 
     * @param printWriter
     *            output file PrintWriter object
     * 
     * @param text
     *            text to write to the output file
     *************************************************************************/
    public void writeToFileLn(PrintWriter printWriter, String text)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file, followed by a line feed
            printWriter.println(text);
        }
    }

    /**************************************************************************
     * Write the supplied text in the indicated format to the specified output
     * file PrintWriter object
     * 
     * @param printWriter
     *            output file PrintWriter object
     * 
     * @param format
     *            print format
     * 
     * @param args
     *            arguments referenced by the format specifiers in the format
     *            string
     *************************************************************************/
    public void writeToFileFormat(PrintWriter printWriter,
                                  String format,
                                  Object... args)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file, followed by a line feed
            printWriter.printf(format, args);
        }
    }

    /**************************************************************************
     * Close the specified output file
     * 
     * @param printWriter
     *            output file PrintWriter object
     *************************************************************************/
    public void closeFile(PrintWriter printWriter)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Close the file
            printWriter.close();
        }
    }
}
