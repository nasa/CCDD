/**************************************************************************************************
 * /** \file CcddFileIOHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class containing file input and output methods (project database backup and restore,
 * table import and export, script storage and retrieval).
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

import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPT_DESCRIPTION_TAG;
import static CCDD.CcddConstants.USERS_GUIDE;
import static CCDD.CcddConstants.overwriteExistingCbIndex;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;
import static CCDD.CcddConstants.appendToExistingDataCbIndex;
import static CCDD.CcddConstants.ignoreErrorsCbIndex;
import static CCDD.CcddConstants.keepDataFieldsCbIndex;
import static CCDD.CcddConstants.SNAP_SHOT_FILE_PATH;
import static CCDD.CcddConstants.SNAP_SHOT_FILE_PATH_2;
import static CCDD.CcddConstants.DEFAULT_DATABASE_NAME;
import static CCDD.CcddConstants.DEFAULT_DATABASE_DESCRIPTION;
import static CCDD.CcddConstants.DEFAULT_DATABASE_USER;
import static CCDD.CcddConstants.EXPORT_MULTIPLE_FILES;
import static CCDD.CcddConstants.EXPORT_SINGLE_FILE;
import static CCDD.CcddConstants.C_STRUCT_TO_C_CONVERSION;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.JAXBException;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddCSVHandler.CSVTags;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.FileNames;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.JSONTags;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.InternalTable.ScriptColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddImportExportInterface.ImportType;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddConstants.exportDataTypes;
import CCDD.CcddBackupName;
import CCDD.CcddConvertCStructureToCSV;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**************************************************************************************************
 * CFS Command and Data Dictionary file I/O handler class
 *************************************************************************************************/
public class CcddFileIOHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddCSVHandler csvHandler;
    private CcddJSONHandler jsonHandler;
    private CcddFieldHandler fieldHandler;
    private CcddMacroHandler macroHandler;
    private CcddReservedMsgIDHandler rsvMsgIDHandler;
    private CcddInputTypeHandler inputTypeHandler;
    private List<CcddTableEditorDialog> tableEditorDlgs;
    private final CcddEventLogDialog eventLog;
    private CcddHaltDialog haltDlg;
    private boolean errorFlag;

    /**********************************************************************************************
     * File I/O handler class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddFileIOHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();
        csvHandler = new CcddCSVHandler(ccddMain, null, ccddMain.getMainFrame());
        jsonHandler = new CcddJSONHandler(ccddMain, null, ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Set the references to the table type and macro handler classes
     *********************************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
    }

    /**********************************************************************************************
     * Display the user's guide. The guide file must be located in the same folder as the .jar
     * file. This command is executed in a separate thread since it may take a noticeable amount
     * time to complete, and by using a separate thread the GUI is allowed to continue to update
     *********************************************************************************************/
    protected void displayUsersGuide()
    {
        // Extract and display the help file in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Extract (if not already extracted) and display the help file
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Check if the Desktop class is not supported by the platform
                    if (!Desktop.isDesktopSupported())
                    {
                        // Set the error type message
                        throw new CCDDException("Desktop class unsupported");
                    }

                    // Get the path+name of the .jar file in a format acceptable to all OS's. The
                    // user's guide is expected to be found in the same folder as the .jar file
                    String path = URLDecoder.decode(new File(CcddMain.class.getProtectionDomain()
                                                                           .getCodeSource()
                                                                           .getLocation()
                                                                           .getPath()).getAbsolutePath(), "UTF-8");

                    // Display the user's guide - replace the .jar file name with the user's guide
                    // name
                    Desktop.getDesktop().open(new File(path.substring(0,
                                                                      path.lastIndexOf(File.separator) + 1)
                                                       + "Docs/"
                                                       + USERS_GUIDE));
                }
                catch (Exception e)
                {
                    // Inform the user that an error occurred opening the user's guide
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>User's guide '</b>"
                                                              + USERS_GUIDE
                                                              + "<b>' cannot be opened; cause '</b>"
                                                              + e.getMessage()
                                                              + "<b>'",
                                                              "File Error",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
            }
        });
    }

    /**********************************************************************************************
     * Compare the contents of two files. If the comparison lines contain an optionally supplied
     * string then these lines are not compared
     *
     * @param path1            Path to the first file to compare
     *
     * @param path2            Path to the second file to compare
     *
     * @param ignoreIfContains Text that, if contained in both of the lines being compared, causes
     *                         the lines to be skipped
     *
     *@throws IOException Error occurred reading file(s)
     *
     * @return true if the files match, false otherwise
     *********************************************************************************************/
    public static boolean compareFiles(Path path1,
                                       Path path2,
                                       String ignoreIfContains) throws IOException
    {
        try (BufferedReader bf1 = Files.newBufferedReader(path1); BufferedReader bf2 = Files.newBufferedReader(path2))
        {

            boolean isSame = true;
            String line1 = "";
            String line2 = "";

            // Continue to read lines from the first file until the end is reached
            while ((line1 = bf1.readLine()) != null)
            {
                // Read in the line from the second file
                line2 = bf2.readLine();

                // Check if the second file has fewer lines than the first file or if the lines
                // don't match. If an ignore string is supplied then do not test for a match if the
                // lines contain the string
                if (line2 == null || (!line1.equals(line2)
                                      && (ignoreIfContains.isEmpty()
                                          || !(line1.contains(ignoreIfContains)
                                               && line2.contains(ignoreIfContains)))))
                {
                    isSame = false;
                    break;
                }
            }

            // Check if the second file has more lines than the first file
            if (isSame && bf2.readLine() != null)
            {
                isSame = false;
            }

            bf1.close();
            bf2.close();
            return isSame;
        }
    }

    /**********************************************************************************************
     * This function will accept a file or files with JSON/CSV format. It will look thorough the
     * files and determine if they contain data that is not currently in the database. If so it
     * will call an import function on this data. If the files to be imported are in agreement with
     * the current state of the database this will be reported and nothing will be imported
     *
     * @param dataFiles                   The files being imported
     *
     * @param importingEntireDatabase     Is an entire database being imported?
     *
     * @param backupFirst                 True to create a backup of the database before importing
     *                                    tables
     *
     * @param replaceExistingTables       True to replace a table that already exists in the
     *                                    database
     *
     * @param appendExistingFields        True to append the existing data fields for a table (if
     *                                    any) to the imported ones (if any). Only valid when
     *                                    replaceExisting is true
     *
     * @param useExistingFields           True to use an existing data field in place of the
     *                                    imported ones if the field names match. Only valid when
     *                                    replaceExisting and appendExistingFields are true
     *
     * @param openEditor                  True to open a table editor for each imported table
     *
     * @param ignoreErrors                True to ignore all errors in the import file
     *
     * @param replaceExistingMacros       True to replace the values for existing macros
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @param replaceExistingGroups       True to replace the values for existing groups
     *
     * @param replaceExistingDataTypes    True to replace existing data types that share a name
     *                                    with an imported data type
     *
     * @param deleteNonExistingTables     True to delete any tables from the database that were not
     *                                    part of the import
     *
     * @param importFileType              The extension for the file type being imported
     *
     * @param dialogType                  What type of file is being imported
     *
     * @param parent                      GUI component over which to center any error dialog
     *
     * @return true if an error occurred importing
     *********************************************************************************************/
    protected boolean prepareJSONOrCSVImport(FileEnvVar[] dataFiles,
                                             final boolean importingEntireDatabase,
                                             final boolean backupFirst,
                                             final boolean replaceExistingTables,
                                             final boolean appendExistingFields,
                                             final boolean useExistingFields,
                                             final boolean openEditor,
                                             final boolean ignoreErrors,
                                             final boolean replaceExistingMacros,
                                             final boolean replaceExistingAssociations,
                                             final boolean replaceExistingGroups,
                                             final boolean replaceExistingDataTypes,
                                             final boolean deleteNonExistingTables,
                                             FileExtension importFileType,
                                             ManagerDialogType dialogType,
                                             final Component parent)
    {
        // Add event to log indicating that JSON/CSV import preparation has begun
        eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                          new StringBuilder("Preparing "
                                            + importFileType.getExtension()
                                            + " table(s) for import"));

        // Select all current tables in the database and prepare them for export. This will be used
        // later to resolve which import files are new/modified
        CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain,
                                                                  TableTreeType.TABLES,
                                                                  null);
        tableTree.setSelectionInterval(0, tableTree.getRowCount());
        List<String> tablePaths = tableTree.getSelectedTablesWithChildren();
        boolean errorFlag = false;

        // Create a FileEnvVar array for all files in the user-supplied directory
        List<FileEnvVar> importFiles = new ArrayList<>();

        // Indicates if any changes were made to the database
        boolean dataWasChanged = false;

        // If this is a bunch of C header files then they need to be converted
        if (importFileType == FileExtension.C_HEADER)
        {
            CcddConvertCStructureToCSV conversionHandler = new CcddConvertCStructureToCSV();
            dataFiles = conversionHandler.convertFile(dataFiles, ccddMain);
            dialogType = ManagerDialogType.IMPORT_CSV;
            importFileType = FileExtension.CSV;
        }

        try
        {
            // Check that data files are selected for import. If a C header import is selected, but
            // an error occurs, the data file list is cleared
            if (dataFiles.length != 0)
            {
                // Are we importing a single large file that represents the whole database?
                if (importingEntireDatabase && (dataFiles.length == 1))
                {
                    if (dialogType == ManagerDialogType.IMPORT_JSON)
                    {
                        dataFiles = processSingleJSONFileRepresentingDatabase(dataFiles[0],
                                                                              dialogType,
                                                                              parent);
                    }
                    else if (dialogType == ManagerDialogType.IMPORT_CSV)
                    {
                        dataFiles = processSingleCSVFileRepresentingDatabase(dataFiles[0], parent);
                    }
                }
                else if (!importingEntireDatabase
                         && (dataFiles.length == 1)
                         && (dialogType == ManagerDialogType.IMPORT_CSV))
                {
                    // Create a buffered reader to read the file
                    BufferedReader br = new BufferedReader(new FileReader(dataFiles[0]));

                    // Read first line in file
                    String line = br.readLine();

                    // Check to see if this is a conversion file
                    if (line.contains(C_STRUCT_TO_C_CONVERSION))
                    {
                        // Process the converted file into individual files that can be imported
                        dataFiles = processCSVConversionFile(dataFiles[0], parent);
                    }

                    br.close();
                }

                // If there is only 1 file then that means that we are looking at a super file that
                // contains the information of an entire database or the user is only importing a
                // single file. Regardless there is no need for the snapshot directory or checking
                // to see what files were deleted and such
                if (dataFiles.length == 1)
                {
                    // Only import files that end with the correct file extension
                    if (dataFiles[0].getName().endsWith(importFileType.getExtension()))
                    {
                        importFiles.add(new FileEnvVar(dataFiles[0].getPath()));
                    }
                }
                // There are multiple data files to be imported
                else
                {
                    List<File> snapshotFiles = compareToSnapshotDirectory(tablePaths.toArray(new String[0]),
                                                                          true,
                                                                          importFileType,
                                                                          false,
                                                                          parent);

                    for (FileEnvVar dataFile : dataFiles)
                    {
                        // Only import files that end with the correct file extension
                        if (dataFile.getName().endsWith(importFileType.getExtension()))
                        {
                            importFiles.add(dataFile);
                        }
                    }

                    List<File> deletedFiles = new ArrayList<>(snapshotFiles);

                    // Ignore any line in a JSON files that contains the build information. This
                    // information contains the file's build time stamp, so every file would show a
                    // change even though the remaining content is identical
                    String ignoreIfContains = dialogType == ManagerDialogType.IMPORT_JSON ? "\""
                                                                                            + JSONTags.FILE_DESCRIPTION.getTag()
                                                                                            + "\":"
                                                                                          : "";

                    // Step through each file in the deleted files array
                    for (int index = 0; index < deletedFiles.size(); index++)
                    {
                        for (int index2 = 0; index2 < importFiles.size(); index2++)
                        {
                            // Do the filenames equal each other? Or, is the deletedFile element in
                            // question not of the correct extension?
                            if (deletedFiles.get(index).getName().equals(importFiles.get(index2).getName()))
                            {
                                // Compare the two files
                                if (compareFiles(importFiles.get(index2).toPath(),
                                                 deletedFiles.get(index).toPath(),
                                                 ignoreIfContains))
                                {
                                    // The files are the same so nothing will be done with this
                                    // file. Remove it from the importFiles list
                                    importFiles.remove(index2);
                                }

                                // The file exists so remove it from the deletedFiles list and
                                // adjust the index since the list is now shorter
                                deletedFiles.remove(index);
                                index--;

                                break;
                            }
                        }
                    }

                    // Check to see if the files that are going to be deleted are the reserved
                    // message IDs and project fields. If so then ignore them
                    for (int index = 0; index < deletedFiles.size(); index++)
                    {
                        if (deletedFiles.get(index).getPath().endsWith(FileNames.PROJECT_DATA_FIELD.JSON())
                            || deletedFiles.get(index).getPath().endsWith(FileNames.RESERVED_MSG_ID.JSON())
                            || deletedFiles.get(index).getPath().endsWith(FileNames.PROJECT_DATA_FIELD.CSV())
                            || deletedFiles.get(index).getPath().endsWith(FileNames.RESERVED_MSG_ID.CSV()))
                        {
                            // Remove it from the deletedFiles list
                            deletedFiles.remove(index);
                            index--;
                        }
                    }
                    // End of checking for deleted files

                    // Check if non-existing tables are to be deleted
                    if (deleteNonExistingTables == true)
                    {
                        // Is there anything to delete?
                        if (deletedFiles.size() != 0)
                        {
                            // Inform the user that there are tables scheduled to be deleted
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html><b>Some tables are going to be deleted</b>",
                                                                      "Deletion Warning",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);

                            // Here, deal with the differing formats in how the filenames are
                            // stored vs. how table paths are stored
                            List<String> pathList = tableTree.getTableTreePathList(null);
                            List<String> deletePathList = new ArrayList<String>();

                            // Check if there are paths in the table tree that correspond to the
                            // deleted filenames. Add them to a list
                            for (String dir : pathList)
                            {
                                String pathReplace = dir.replaceAll("\\,", "_").replaceAll("\\.", "_");

                                for (File fileToDelete : deletedFiles)
                                {
                                    if ((fileToDelete.getName().replace(importFileType.getExtension(), "")).equals(pathReplace))
                                    {
                                        deletePathList.add(dir);
                                    }
                                }
                            }

                            // Now that we have a list of the table paths for the deleted tables, turn
                            // it into an array and delete them
                            String[] deleteTableNames = deletePathList.toArray(new String[deletePathList.size()]);
                            dbTable.deleteTable(deleteTableNames, true, null);

                            // If tables were deleted then that means the database has been altered
                            dataWasChanged = true;
                        }
                    }
                }

                // Do any files remain after trimming the duplicate files? Import them here
                if (importFiles.size() > 0)
                {
                    // Import file into database
                    importFiles(importFiles,
                                backupFirst,
                                importingEntireDatabase,
                                replaceExistingTables,
                                appendExistingFields,
                                useExistingFields,
                                openEditor,
                                ignoreErrors,
                                replaceExistingMacros,
                                replaceExistingAssociations,
                                replaceExistingGroups,
                                replaceExistingDataTypes,
                                dialogType,
                                parent);

                    dataWasChanged = true;
                }

                // If we reach here, that means there's nothing to add and nothing to delete
                if (dataWasChanged == false)
                {
                    // Inform the user that there are no perceptible changes to the files relative to
                    // current database state
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>The selected folder/file does "
                                                              + "not contain any updates</b>",
                                                              "No Changes Made",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
            }
        }
        catch (NullPointerException | IOException npe)
        {
            // Inform the user that file import preparation failed
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Error preparing "
                                                      + importFileType.getExtension()
                                                      + " table(s) for import",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }
        finally
        {
            // If the snapshot directory exists than delete it
            if (Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH)))
            {
                // Step through each file in the extant .snapshot directory
                for (File file : new File(SNAP_SHOT_FILE_PATH).listFiles())
                {
                    // Delete each file
                    file.delete();
                }
            }
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Backup the currently open project's database to a user-selected file using the pg_dump
     * utility. The backup data is stored in plain text format
     *
     * @param doInBackground True to perform the operation in a background process
     *********************************************************************************************/
    protected void backupDatabaseToFile(boolean doInBackground)
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
            // Get the name of the currently open database
            final String databaseName = dbControl.getDatabaseName();
            String projectName = dbControl.getProjectName();

            // Set the initial layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0,
                                                            0,
                                                            1,
                                                            1,
                                                            1.0,
                                                            0.0,
                                                            GridBagConstraints.LINE_START,
                                                            GridBagConstraints.BOTH,
                                                            new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                       ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       0),
                                                            0,
                                                            0);

            // Create the backup file choice dialog
            final CcddDialogHandler dlg = new CcddDialogHandler();

            // Create a date and time stamp check box
            JCheckBox stampChkBx = new JCheckBox("Append date and time to file name");
            stampChkBx.setBorder(BorderFactory.createEmptyBorder());
            stampChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            stampChkBx.setSelected(false);
            final String dateTimeFormat = "yyyyMMdd_HHmmss";

            // Create a listener for check box selection actions
            stampChkBx.addActionListener(new ActionListener()
            {
                String timeStamp = "";

                /**************************************************************************************
                 * Handle check box selection actions
                 *************************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if the data and time stamp check box is selected
                    if (((JCheckBox) ae.getSource()).isSelected())
                    {
                        // Get the current date and time stamp
                        timeStamp = "_" + new SimpleDateFormat(dateTimeFormat).format(Calendar.getInstance().getTime());

                        // Append the date and time stamp to the file name
                        dlg.getFileNameField().setText(dlg.getFileNameField()
                                                          .getText()
                                                          .replaceFirst(Pattern.quote(FileExtension.DBU.getExtension()),
                                                                        timeStamp + FileExtension.DBU.getExtension()));
                    }
                    // The check box is not selected
                    else
                    {
                        // Remove the date and time stamp
                        dlg.getFileNameField().setText(dlg.getFileNameField().getText().replaceFirst(timeStamp, ""));
                    }
                }
            });

            // Create a panel to contain the date and time stamp check box
            JPanel stampPnl = new JPanel(new GridBagLayout());
            stampPnl.setBorder(BorderFactory.createEmptyBorder());
            stampPnl.add(stampChkBx, gbc);

            // Allow the user to select the backup file path + name
            FileEnvVar[] dataFile = dlg.choosePathFile(ccddMain,
                                                       ccddMain.getMainFrame(),
                                                       databaseName + FileExtension.DBU.getExtension(),
                                                       null,
                                                       new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                                                                                                                  FileExtension.DBU.getExtensionName())},
                                                       false,
                                                       false,
                                                       "Backup Project " + projectName,
                                                       ccddMain.getProgPrefs().get(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPreferenceKey(), null),
                                                       DialogOption.BACKUP_OPTION,
                                                       stampPnl);

            // File name with the white space stripped
            String compliantFileName = dlg.getFileNameField().getText().replaceAll("\\s", "");

            FileEnvVar chosenBackupPath = CcddBackupName.reconstructBackupFilePath(dataFile, compliantFileName);

            // Check if a file was chosen
            if (chosenBackupPath != null)
            {
                boolean cancelBackup = false;

                // Check if the backup file exists
                if (chosenBackupPath.exists())
                {
                    // Check if the existing file should be overwritten
                    if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Overwrite existing backup file?",
                                                                  "Overwrite File",
                                                                  JOptionPane.QUESTION_MESSAGE,
                                                                  DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                    {
                        // Check if the file can be deleted
                        if (!chosenBackupPath.delete())
                        {
                            // Inform the user that the existing backup file cannot be replaced
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html><b>Cannot replace existing backup file '</b>"
                                                                      + chosenBackupPath.getAbsolutePath()
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

                // Strip away the file name extension and the data and time (if any)
                String backupName = CcddBackupName.removeExtensionTimeStamp(FileExtension.DBU.getExtension(),
                                                                            dateTimeFormat,
                                                                            compliantFileName,
                                                                            stampChkBx.isSelected());

                // Is the new database name different from the current one?
                boolean isDifferentName = !databaseName.equals(backupName);

                // Check to see if the new name already exists (and is not the same name)
                if (isDifferentName
                    && dbControl.isDatabaseNameInUse(dbControl.convertProjectNameToDatabase(backupName)))
                {
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Cannot backup project '</b>"
                                                              + backupName
                                                              + "<b>', project already exists in workspace.",
                                                              "Backup Error",
                                                              JOptionPane.ERROR_MESSAGE,
                                                              DialogOption.OK_OPTION);
                    cancelBackup = true;
                }

                // Check that no errors occurred and that the user didn't cancel the backup
                if (!cancelBackup)
                {
                    // Check if the operation should be performed in the background
                    if (doInBackground)
                    {
                        if (isDifferentName)
                        {
                            // Create a backup of the current database
                            dbControl.backupAndRenameDatabaseInBackground(projectName,
                                                                          backupName,
                                                                          chosenBackupPath);
                        }
                        else
                        {
                            dbControl.backupDatabaseInBackground(projectName, chosenBackupPath);
                        }

                    }
                    // Perform the operation in the foreground
                    else
                    {
                        // Check for a different name
                        if (isDifferentName)
                        {
                            // This will be coming from the GUI which will require that the only
                            // database that can be backed up is the open one so close it first
                            if (ccddMain.ignoreUncommittedChanges("Close Project",
                                                                  "Discard changes?",
                                                                  true,
                                                                  null,
                                                                  null))
                            {
                                dbControl.openDatabase(DEFAULT_DATABASE);
                            }
                            else
                            {
                                // The user has chosen not to close the database so exit
                                return;
                            }

                            // Perform the sequence of operations here
                            dbControl.backupAndRenameDatabase(projectName,
                                                              backupName,
                                                              chosenBackupPath);

                            // Open the original database again
                            dbControl.openDatabase(projectName);
                        }
                        // It is the same name so just do the backup
                        else
                        {
                            dbControl.backupDatabase(projectName, chosenBackupPath);
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Restore a project's database from a user-selected backup file. The backup is a plain text
     * file containing the PostgreSQL commands necessary to rebuild the database
     *********************************************************************************************/
    protected void restoreDatabaseFromDBU()
    {
        restoreDatabaseFromDBU(null, null, null, null);
    }

    /**********************************************************************************************
     * Restore a project's database from a specified or user-selected backup file. The backup is a
     * plain text file containing the PostgreSQL commands necessary to rebuild the database
     *
     * @param restoreFileName    Name of the backup file to restore; null to allow the user to
     *                           choose a file
     *
     * @param projectName        Name of the project to restore; null to extract the project name
     *                           from the backup file's database comment
     *
     * @param projectOwner       Owner of the project to restore; null to use the current user as
     *                           the owner
     *
     * @param projectDescription Description of the project to restore; null to extract the
     *                           description from the backup file's database comment
     *********************************************************************************************/
    protected void restoreDatabaseFromDBU(String restoreFileName,
                                          String projectName,
                                          String projectOwner,
                                          String projectDescription)
    {
        // Check if a blank backup file name is provided
        if (restoreFileName != null && restoreFileName.isEmpty())
        {
            // Set the file name to null to allow easier comparisons below
            restoreFileName = null;
        }

        // Set the flag if the current user's password is non-blank. Depending on the
        // authentication set-up and operating system, the password may still be required by the
        // psql command even if the authentication method is 'trust'. If the restore is initiated
        // from the command line with the GUI hidden then the password is assumed to be already set
        boolean isPasswordSet = dbControl.isPasswordNonBlank()
                                || (ccddMain.isGUIHidden() && restoreFileName != null);

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
            File tempFile = null;
            FileEnvVar[] dataFile = null;

            // Check if the name of the backup file name to restore isn't provided
            if (restoreFileName == null)
            {
                // Allow the user to select the backup file path + name to load from
                dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                  ccddMain.getMainFrame(),
                                                                  null,
                                                                  null,
                                                                  new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                                                                                                                             FileExtension.DBU.getExtensionName())},
                                                                  false,
                                                                  "Restore Project",
                                                                  ccddMain.getProgPrefs().get(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPreferenceKey(), null),
                                                                  DialogOption.RESTORE_OPTION);
            }
            // The name of the backup file to restore is provided
            else
            {
                // Create the file class
                dataFile = new FileEnvVar[] {new FileEnvVar(restoreFileName)};
            }

            // Check if a file was chosen
            if (dataFile != null && dataFile[0] != null)
            {
                boolean isProjectNameValid = projectName != null && !projectName.isEmpty();
                boolean isProjectDescValid = projectDescription != null && !projectDescription.isEmpty();
                boolean nameDescProvided = isProjectNameValid && isProjectDescValid;
                boolean commentFound = false;
                boolean backupDBUInfoFound = false;
                String backupDBUInfo = "";
                BufferedReader br = null;
                BufferedWriter bw = null;

                try
                {
                    // Check if the file doesn't exist
                    if (!dataFile[0].exists())
                    {
                        throw new CCDDException("Cannot locate backup file '</b>"
                                                + dataFile[0].getAbsolutePath()
                                                + "'");
                    }

                    boolean inComment = false;
                    boolean inBackupDatabaseInfoTable = false;
                    boolean inUsers = false;
                    boolean isOwnerAdmin = false;
                    String line = null;
                    String commentText = "";

                    // Check if no project owner is provided
                    if (projectOwner == null)
                    {
                        // Set the current user as the owner of the restored project
                        projectOwner = dbControl.getUser();
                    }

                    // Create a temporary file in which to copy the backup file contents
                    tempFile = File.createTempFile(dataFile[0].getName(), "");

                    // Create a buffered reader to read the file and a buffered writer to write the
                    // file
                    br = new BufferedReader(new FileReader(dataFile[0]));
                    bw = new BufferedWriter(new FileWriter(tempFile));

                    // Read each line in the backup file
                    while ((line = br.readLine()) != null)
                    {
                        // Check if this line is not a continuance of the database comment
                        if (!inComment && !inBackupDatabaseInfoTable)
                        {
                            // Check if this line creates the plpgsql language
                            if (line.equals("CREATE PROCEDURAL LANGUAGE plpgsql;")
                                || line.equals("CREATE OR REPLACE PROCEDURAL LANGUAGE plpgsql;"))
                            {
                                // Add the command to first drop the language. This allows backups
                                // created from PostgreSQL versions 8.4 and earlier to be restored
                                // in version 9.0 and subsequent without generating an error
                                line = "DROP LANGUAGE IF EXISTS plpgsql;\nCREATE PROCEDURAL LANGUAGE plpgsql;";

                                // Check if the PostgeSQL version is 9 or higher
                                if (dbControl.getPostgreSQLMajorVersion() > 8)
                                {
                                    // Add the command to drop the language extension; this is
                                    // necessary in order for the language to be dropped in
                                    // PostgreSQL 9+
                                    line = "DROP EXTENSION plpgsql;\n" + line;
                                }
                            }
                            // Check if this line adds a comment to the plpgsql language
                            else if (line.startsWith("COMMENT ON EXTENSION plpgsql"))
                            {
                                // Ignore the comment; this can cause an ownership error and the
                                // comment isn't needed
                                line = "";
                            }
                            // Check if this is the beginning of the command to populate the user
                            // access level table
                            else if (line.matches("COPY (?:[^\\.]+\\.)?" + InternalTable.USERS.getTableName() + " .+"))
                            {
                                // Set the flag to indicate the following lines contain the user
                                // access level information
                                inUsers = true;
                            }
                            // Check if this line follows the command to populate the user access
                            // level table
                            else if (inUsers)
                            {
                                // Check if this line defines a user's access level
                                if (line.matches("[^\\s]+\\s+.+"))
                                {
                                    if (line.matches(projectOwner + "+\\s+.+"))
                                    {
                                        // Set the flag to indicate the project owner is already in
                                        // the user access level table
                                        isOwnerAdmin = true;

                                        // Make the project owner an administrator for the restored
                                        // project
                                        line.replaceFirst("(.+\\s+).+$", "$1" + AccessLevel.ADMIN.getDisplayName());
                                    }
                                }
                                // The last user access level definition was reached
                                else
                                {
                                    // Set the flag so that subsequent lines are not considered a
                                    // user access level definition
                                    inUsers = false;

                                    // Check if the owner for the restored project is not in the
                                    // user access level table
                                    if (!isOwnerAdmin)
                                    {
                                        // Add the project owner as an administrator for the
                                        // restored project
                                        line = projectOwner
                                               + "\t"
                                               + AccessLevel.ADMIN.getDisplayName()
                                               + "\n"
                                               + line;
                                    }
                                }
                            }
                            // Check if this line is a SQL command that revokes permissions for an
                            // owner other than PUBLIC
                            else if (line.matches("REVOKE .+ FROM .+;\\s*")
                                     && !line.matches("REVOKE .+ FROM PUBLIC;\\s*"))
                            {
                                // Change the original owner to the current user
                                line = line.replaceFirst("FROM .+;", "FROM " + projectOwner + ";");
                            }
                            // Check if this line is a SQL command that grants permissions to an
                            // owner other than PUBLIC
                            else if (line.matches("GRANT .+ TO .+;\\s*")
                                     && !line.matches("GRANT .+ TO PUBLIC;\\s*"))
                            {
                                // Change the original owner to the current user
                                line = line.replaceFirst("TO .+;", "TO " + projectOwner + ";");
                            }

                            // Check if the database comment hasn't been found already and that the
                            // line contains the comment information
                            if (!commentFound
                                && line.matches("COMMENT ON DATABASE .+ IS '"
                                                + CCDD_PROJECT_IDENTIFIER
                                                + ".+"))
                            {
                                commentFound = true;
                                inComment = true;
                            }
                            else if (!commentFound && line.matches("COPY public.__dbu_info .+"))
                            {
                                // Here is where I look for dbu info
                                inBackupDatabaseInfoTable = true;
                            }
                        }

                        if (inBackupDatabaseInfoTable && !line.matches("COPY public.__dbu_info .+"))
                        {
                            backupDBUInfo = line;
                            inBackupDatabaseInfoTable = false;
                            backupDBUInfoFound = true;
                        }

                        // Check if this line is the beginning or a continuance of the database
                        // comment
                        if (inComment)
                        {
                            // Begin, or append the continuance of the database description, to the
                            // database comment command text
                            commentText += (commentText.isEmpty() ? "" : "\n") + line;

                            // Insert a comment indicator into the file so that this line isn't
                            // executed when the database is restored
                            line = "-- " + line;

                            // Set the flag to true if the comment continues to the next line
                            inComment = !line.endsWith(";");
                        }

                        // Write the line to the temporary file
                        if ((!line.matches("SET lock_timeout .+"))
                            && (!line.matches("-- Dumped .+"))
                            && (!line.matches("SET idle_in_transaction_session_timeout .+"))
                            && (!line.matches("SET xmloption.+"))
                            && (!line.matches("SET row_security .+"))
                            && (!line.matches("    AS integer")))
                        {
                            bw.write(line + "\n");
                        }
                    }

                    // Check if the project name and description exist
                    if (commentFound || nameDescProvided || backupDBUInfoFound)
                    {
                        String projectAdministrator = "";

                        // Flush the output file buffer so that none of the contents are lost
                        bw.flush();

                        // Check if the database name and description aren't provided explicitly,
                        // and a comment was extracted from the backup file
                        if (!nameDescProvided && commentFound)
                        {
                            // Extract the database name from the comment
                            String databaseName = commentText.replaceAll("(?s)COMMENT ON DATABASE (.+) IS '"
                                                                         + CCDD_PROJECT_IDENTIFIER + ".+",
                                                                         "$1");
                            commentText = commentText.replaceAll("(?s)COMMENT ON DATABASE .+ IS '"
                                                                 + CCDD_PROJECT_IDENTIFIER + "(.+)';$",
                                                                 "$1");

                            // Split the line read from the file in order to get the project name
                            // and description
                            String[] comment = dbControl.parseDatabaseComment(databaseName, commentText);

                            // Extract the project name (with case preserved) and description, and
                            // set the flag indicating the comment is located
                            projectName = comment[DatabaseComment.PROJECT_NAME.ordinal()];
                            projectAdministrator = comment[DatabaseComment.ADMINS.ordinal()];
                            projectDescription = comment[DatabaseComment.DESCRIPTION.ordinal()];
                        }
                        else if (!nameDescProvided && backupDBUInfoFound)
                        {
                            // Split the line read from the file in order to get the project
                            // information
                            String[] comment = new String[4];
                            String[] temp = backupDBUInfo.replace("\n", "").split("\t");
                            System.arraycopy(temp, 0, comment, 0, 4);

                            // Extract the project name (with case preserved) and description, and
                            // set the flag indicating the comment is located
                            projectName = comment[DatabaseComment.PROJECT_NAME.ordinal()];
                            projectAdministrator = comment[DatabaseComment.ADMINS.ordinal()];
                            projectDescription = comment[DatabaseComment.DESCRIPTION.ordinal()];
                        }

                        // Check if the project owner isn't in the administrator list embedded in
                        // the database comment
                        if (!projectAdministrator.matches("(?:^|,)" + projectOwner + "(?:,|$)"))
                        {
                            // Add the project owner as an administrator
                            projectAdministrator += (projectAdministrator.isEmpty() ? "" : ",")
                                                    + projectOwner;
                        }

                        // Check if the backup file is restored via the command line
                        if (restoreFileName != null)
                        {
                            // Restore the database from the temporary file. This file has the line
                            // that disables creation of the database comment, which is handled
                            // when the restored database is created
                            dbControl.restoreDatabase(projectName,
                                                      projectOwner,
                                                      projectAdministrator,
                                                      projectDescription,
                                                      tempFile,
                                                      true);
                        }
                        // The the backup file is restored via the GUI
                        else
                        {
                            // Restore the database from the temporary file as a background
                            // process. This file has the line that disables creation of the
                            // database comment, which is handled when the restored database is
                            // created
                            dbControl.restoreDatabaseInBackground(projectName,
                                                                  projectOwner,
                                                                  projectAdministrator,
                                                                  projectDescription,
                                                                  tempFile,
                                                                  false);
                        }

                        // Store the data file path in the program preferences backing store
                        storePath(ccddMain,
                                  dataFile[0].getAbsolutePathWithEnvVars(),
                                  true,
                                  ModifiablePathInfo.DATABASE_BACKUP_PATH);
                    }
                    else
                    {
                        // The project owner, name, and description don't exist
                        throw new CCDDException("File '</b>"
                                                + dataFile[0].getAbsolutePath()
                                                + "'<b> is not a backup file");
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
                                                              "<html><b>Cannot read backup file '</b>"
                                                              + dataFile[0].getAbsolutePath()
                                                              + "<b>'; cause '</b>"
                                                              + e.getMessage()
                                                              + "<b>'",
                                                              "File Error",
                                                              JOptionPane.ERROR_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
                finally
                {
                    try
                    {
                        // Check if the input file is open
                        if (br != null)
                        {
                            // Close the input file
                            br.close();
                        }
                    }
                    catch (IOException ioe)
                    {
                        // Inform the user that the input file cannot be closed
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot close backup file '</b>"
                                                                  + dataFile[0].getAbsolutePath()
                                                                  + "<b>'",
                                                                  "File Warning",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                    finally
                    {
                        try
                        {
                            // Check if the output file is open
                            if (bw != null)
                            {
                                // Close the output file
                                bw.close();
                            }
                        }
                        catch (IOException ioe)
                        {
                            // Inform the user that the output file cannot be closed
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                       "<html><b>Cannot close backup file '</b>"
                                                       + tempFile.getAbsolutePath()
                                                       + "<b>'",
                                                       "File Warning",
                                                       JOptionPane.WARNING_MESSAGE,
                                                       DialogOption.OK_OPTION);
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Display a CCDD dialog message that informs the user that no dbu_info file was provided for
     * the restore process. They can then decide if they would like to cancel the restore or use
     * the provided default values
     *
     * @return boolean representing the users decision
     *********************************************************************************************/
    protected boolean displayDbuInfoWarningDialog()
    {
        return (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>No DBU_Info file found. Using defaults "
                                                          + "found below <br><br>Database Name: "
                                                          + DEFAULT_DATABASE_NAME
                                                          + "<br>Database Owner: "
                                                          + DEFAULT_DATABASE_USER
                                                          + "<br>Database Description: "
                                                          + DEFAULT_DATABASE_DESCRIPTION
                                                          + "</b></html>",
                                                          "Warning!",
                                                          JOptionPane.QUESTION_MESSAGE,
                                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON);
    }

    /**********************************************************************************************
     * Restore a project's database from a specified or user-selected JSON or CSV file(s)
     *
     * @param restoreFileType   Restore file type (JSON or CSV)
     *
     * @param createName        Project name
     *
     * @param createOwner       Project owner
     *
     * @param createDescription Project description
     *
     * @param createRestore     Backup file name
     *********************************************************************************************/
    protected void restoreDatabaseFromJSONOrCSV(ManagerDialogType restoreFileType,
                                                String createName,
                                                String createOwner,
                                                String createDescription,
                                                String createRestore)
    {
        try
        {
            CcddJSONHandler jsonHandler = null;
            CcddCSVHandler csvHandler = null;
            FileEnvVar dbuInfoFile = null;
            FileEnvVar[] dataFile = null;
            String fileName = "";
            String fileDescription = "";
            FileExtension fileExtension = null;
            List<String[]> usersAndAccessLevel = new ArrayList<String[]>();
            boolean cancelExecution = false;
            boolean useDefaultValues = false;
            String dbuInfoName = "";
            String dbuInfoDescription = "";

            // Create the correct handler and file variables
            if (restoreFileType == ManagerDialogType.IMPORT_JSON)
            {
                jsonHandler = new CcddJSONHandler(ccddMain, null, ccddMain.getMainFrame());
                fileName = FileNames.DBU_INFO.JSON();
                fileDescription = FileExtension.JSON.getDescription();
                fileExtension = FileExtension.JSON;
            }
            else
            {
                csvHandler = new CcddCSVHandler(ccddMain, null, ccddMain.getMainFrame());
                fileName = FileNames.DBU_INFO.CSV();
                fileDescription = FileExtension.CSV.getDescription();
                fileExtension = FileExtension.CSV;
            }

            // Set the flag if the current user's password is non-blank. Depending on the
            // authentication set-up and operating system, the password may still be required by
            // the psql command even if the authentication method is 'trust'. If the restore is
            // initiated from the command line with the GUI hidden then the password is assumed to
            // be already set
            boolean isPasswordSet = dbControl.isPasswordNonBlank() || (ccddMain.isGUIHidden());

            // Check if no password is set
            if (!isPasswordSet)
            {
                // Display the password dialog and obtain the password. Note that the user can
                // enter a blank password (which may be valid)
                CcddServerPropertyDialog dialog = new CcddServerPropertyDialog(ccddMain,
                                                                               ServerPropertyDialogType.PASSWORD);

                // Set the flag if the user selected the Okay button in the password dialog
                isPasswordSet = dialog.isPasswordSet();
                cancelExecution = !isPasswordSet;
            }

            // Check if the user's database password is set (either non-blank or explicitly set to
            // blank)
            if (isPasswordSet)
            {
                // Allow the user to select the backup file path + name to load from
                dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                  ccddMain.getMainFrame(),
                                                                  null,
                                                                  null,
                                                                  new FileNameExtensionFilter[] {new FileNameExtensionFilter(fileDescription,
                                                                                                                             fileExtension.getExtensionName())},
                                                                  true,
                                                                  "Restore Project",
                                                                  ccddMain.getProgPrefs().get(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPreferenceKey(),
                                                                                              null),
                                                                  DialogOption.RESTORE_OPTION);

                // Check that the GUI is not in headless state
                if (!ccddMain.isGUIHidden())
                {
                    if (dataFile != null)
                    {
                        // Store the restore file path in the backing store
                        CcddFileIOHandler.storePath(ccddMain,
                                                    dataFile[0].getAbsolutePath(),
                                                    true,
                                                    ModifiablePathInfo.DATABASE_BACKUP_PATH);

                        // Multi-file import
                        if (dataFile.length > 1)
                        {
                            // Look for the dbu_info file
                            for (int index = 0; index < dataFile.length; index++)
                            {
                                if (dataFile[index].getName().contentEquals(fileName))
                                {
                                    // Indicate that the file was found and break out of the loop
                                    dbuInfoFile = dataFile[index];
                                    break;
                                }
                            }
                            // Single file import
                        }
                        else if (dataFile.length == 1)
                        {
                            dbuInfoFile = dataFile[0];
                        }
                    }
                    else
                    {
                        cancelExecution = true;
                    }
                }
                else
                {
                    // GUI is in headless state
                    FileEnvVar path = new FileEnvVar(createRestore);

                    // Check if the provided path belongs to a directory
                    if (path.isDirectory())
                    {
                        // If we are working with a directory then place all of the files within
                        // the directory within a list of type FileEnvVar
                        File[] files = path.listFiles();
                        FileEnvVar[] tempFiles = new FileEnvVar[files.length];

                        // Cast all of the files to type FileEnvVar
                        for (int index = 0; index < files.length; index++)
                        {
                            tempFiles[index] = new FileEnvVar(files[index].getAbsolutePath());

                            if (tempFiles[index].getName().contentEquals(fileName))
                            {
                                dbuInfoFile = tempFiles[index];
                            }
                        }

                        dataFile = tempFiles;
                    }
                    else
                    {
                        dbuInfoFile = new FileEnvVar(createRestore);
                        dataFile = new FileEnvVar[] {dbuInfoFile};
                    }
                }
            }

            if (!cancelExecution)
            {
                // If CCDD is not in headless state, let the user know that no DBU_Info data was
                // found
                if (dbuInfoFile == null && !ccddMain.isGUIHidden())
                {
                    if (displayDbuInfoWarningDialog())
                    {
                        useDefaultValues = true;
                    }
                    else
                    {
                        cancelExecution = true;
                    }
                }

                if ((dbuInfoFile != null && cancelExecution == false) || useDefaultValues)
                {
                    String projectName = "";
                    String projectDescription = "";

                    if (!useDefaultValues)
                    {
                        if (restoreFileType == ManagerDialogType.IMPORT_JSON)
                        {
                            // Detect the type of the parsed JSON file and only accept JSONObjects.
                            // This will throw an exception if it is incorrect
                            jsonHandler.verifyJSONObjectType(dbuInfoFile);

                            // Create a JSON parser and use it to parse the import file contents
                            JSONObject jsonObject = (JSONObject) new JSONParser().parse(new FileReader(dbuInfoFile));
                            Object defn = jsonObject.get(JSONTags.DBU_INFO.getTag());

                            // Let the user know that no DBU_Info data was found unless we are
                            // running in headless state
                            if (defn == null && !ccddMain.isGUIHidden())
                            {
                                if (displayDbuInfoWarningDialog())
                                {
                                    useDefaultValues = true;
                                }
                                else
                                {
                                    cancelExecution = true;
                                }
                            }

                            // Check to see that the user has not cancelled the process
                            if (!cancelExecution)
                            {
                                // Check if the DBU info exist
                                if (defn != null && defn instanceof JSONArray)
                                {
                                    JSONObject dbuInfoJO = jsonHandler.parseJSONArray(defn).get(0);

                                    // Get the project description
                                    dbuInfoDescription = jsonHandler.getString(dbuInfoJO,
                                                                               JSONTags.DB_DESCRIPTION.getTag());

                                    // Get the project name
                                    dbuInfoName = jsonHandler.getString(dbuInfoJO, JSONTags.DB_NAME.getTag());

                                    // Get the project users
                                    String[] projectUsers = jsonHandler.getString(dbuInfoJO,
                                                                                  JSONTags.DB_USERS.getTag()).split(",");

                                    for (int index = 0; index < projectUsers.length; index++)
                                    {
                                        usersAndAccessLevel.add(projectUsers[index].split(":"));
                                    }
                                }
                            }
                        }
                        else
                        {
                            boolean dbuInfoFound = false;

                            // Create a buffered reader to read the file
                            BufferedReader br = new BufferedReader(new FileReader(dbuInfoFile));

                            // Read first line in file
                            String line = br.readLine();

                            // Step though the file looking for dbu_info
                            while (line != null)
                            {
                                if (line.contentEquals(CSVTags.DBU_INFO.getTag()))
                                {
                                    line = br.readLine();
                                    dbuInfoFound = true;
                                    break;
                                }

                                line = br.readLine();
                            }

                            // Let the user know that no DBU_Info data was found unless we are
                            // running in headless state
                            if (dbuInfoFound == false && !ccddMain.isGUIHidden())
                            {
                                if (displayDbuInfoWarningDialog())
                                {
                                    useDefaultValues = true;
                                }
                                else
                                {
                                    cancelExecution = true;
                                }
                            }

                            // Check to see that the user has not cancelled the process
                            if (!cancelExecution)
                            {
                                String[] columnValues = csvHandler.trimLine(line, br);

                                // Check if the DBU info exist
                                if (columnValues != null && columnValues.length == 3)
                                {
                                    // Get the project description
                                    dbuInfoDescription = columnValues[2];

                                    // Get the project name
                                    dbuInfoName = columnValues[0];

                                    // Get the project users
                                    String[] projectUsers = columnValues[1].split(",");

                                    for (int index = 0; index < projectUsers.length; index++)
                                    {
                                        usersAndAccessLevel.add(projectUsers[index].split(":"));
                                    }
                                }
                            }
                        }
                    }

                    // Check to see that the user has not canceled the process
                    if (!cancelExecution)
                    {
                        // Check to see if the default values should be used when creating the
                        // database
                        if (useDefaultValues)
                        {
                            // Set the default values
                            createName = DEFAULT_DATABASE_NAME;
                            createOwner = dbControl.getUser();
                            createDescription = DEFAULT_DATABASE_DESCRIPTION;
                        }

                        // set the project description
                        if (createDescription == null)
                        {
                            if (!dbuInfoDescription.isEmpty())
                            {
                                projectDescription = dbuInfoDescription;
                            }
                            else
                            {
                                projectDescription = DEFAULT_DATABASE_DESCRIPTION;
                            }
                        }
                        else
                        {
                            projectDescription = createDescription;
                        }

                        // Set the project name
                        if (createName == null)
                        {
                            if (!dbuInfoName.isEmpty())
                            {
                                projectName = dbuInfoName;
                            }
                            else
                            {
                                projectName = DEFAULT_DATABASE_NAME;
                            }
                        }
                        else
                        {
                            projectName = createName;
                        }

                        // Create a new project
                        if (createOwner == null)
                        {
                            dbControl.createDatabase(projectName,
                                                     dbControl.getUser(),
                                                     dbControl.getUser(),
                                                     projectDescription);
                        }
                        else
                        {
                            dbControl.createDatabase(projectName,
                                                     createOwner,
                                                     createOwner,
                                                     projectDescription);
                        }

                        dbControl.openDatabase(projectName, true, false);

                        // Since the database is being restored, none of the existing data types,
                        // if any, are retained
                        dataTypeHandler.setDataTypeData(new ArrayList<String[]>());

                        // Import data
                        if (createRestore == null)
                        {
                            importFileInBackground(dataFile,
                                                   true,
                                                   false,
                                                   true,
                                                   false,
                                                   false,
                                                   false,
                                                   true,
                                                   true,
                                                   true,
                                                   true,
                                                   false,
                                                   true,
                                                   fileExtension,
                                                   restoreFileType,
                                                   usersAndAccessLevel,
                                                   ccddMain.getMainFrame());
                        }
                        else
                        {
                            createSnapshotDirectory(ccddMain.getMainFrame());

                            // Step through the array of files and ensure that all files starting
                            // with '_' are placed at the start of the list. The order of the other
                            // files does not matter
                            int numOfModifications = 0;

                            for (int index = 0; index < dataFile.length; index++)
                            {
                                if (dataFile[index].getName().startsWith("_") && index != 0)
                                {
                                    FileEnvVar tempFile = dataFile[numOfModifications];
                                    dataFile[numOfModifications] = dataFile[index];
                                    dataFile[index] = tempFile;
                                    numOfModifications++;
                                }
                            }

                            errorFlag = prepareJSONOrCSVImport(dataFile,
                                                               true,
                                                               false,
                                                               true,
                                                               false,
                                                               false,
                                                               false,
                                                               true,
                                                               true,
                                                               true,
                                                               true,
                                                               true,
                                                               false,
                                                               fileExtension,
                                                               restoreFileType,
                                                               ccddMain.getMainFrame());

                            if (usersAndAccessLevel != null && usersAndAccessLevel.size() > 0)
                            {
                                // Execute the database update for users and access levels
                                dbTable.storeNonTableTypesInfoTable(InternalTable.USERS,
                                                                    usersAndAccessLevel,
                                                                    null,
                                                                    ccddMain.getMainFrame());
                            }

                            deleteSnapshotDirectories(ccddMain.getMainFrame());
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Import one or more files, creating new tables and optionally replacing existing ones. The
     * file(s) may contain definitions for more than one table. This method is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     *
     * @param dataFile                    Array of files to import
     *
     * @param importingEntireDatabase     Is an entire database being imported?
     *
     * @param backupFirst                 True to create a backup of the database before importing
     *                                    tables
     *
     * @param replaceExisting             True to replace a table that already exists in the
     *                                    database
     *
     * @param appendExistingFields        True to append the existing data fields for a table (if
     *                                    any) to the imported ones (if any). Only valid when
     *                                    replaceExisting is true
     *
     * @param useExistingFields           True to use an existing data field in place of the
     *                                    imported ones if the field names match. Only valid when
     *                                    replaceExisting and appendExistingFields are true
     *
     * @param openEditor                  True to open a table editor for each imported table
     *
     * @param ignoreErrors                True to ignore all errors in the import file
     *
     * @param replaceExistingMacros       True to replace the values for existing macros
     *
     * @param replaceExistingGroups       True to replace existing group definitions
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @param deleteNonExistingTables     True to delete any tables from the database that were not
     *                                    part of the import
     *
     * @param replaceExistingDataTypes    True to replace existing data types that share a name
     *                                    with an imported data type
     *
     * @param importFileType              The extension for what type of file is being imported
     *
     * @param dialogType                  The type of file import being performed
     *
     * @param parent                      GUI component over which to center any error dialog
     *
     * @param usersAndAccessLevel         List of users and their access levels
     *
     * @return true if an error occurred while importing
     *********************************************************************************************/
    protected boolean importFileInBackground(final FileEnvVar[] dataFile,
                                             final boolean importingEntireDatabase,
                                             final boolean backupFirst,
                                             final boolean replaceExisting,
                                             final boolean appendExistingFields,
                                             final boolean useExistingFields,
                                             final boolean openEditor,
                                             final boolean ignoreErrors,
                                             final boolean replaceExistingMacros,
                                             final boolean replaceExistingGroups,
                                             final boolean replaceExistingAssociations,
                                             final boolean deleteNonExistingTables,
                                             final boolean replaceExistingDataTypes,
                                             final FileExtension importFileType,
                                             final ManagerDialogType dialogType,
                                             final List<String[]> usersAndAccessLevel,
                                             final Component parent)
    {
        // Execute the import operation in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
        {
            /**************************************************************************************
             * Import the selected data
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the import cancellation dialog
                haltDlg = new CcddHaltDialog("Import Data",
                                             "Importing data",
                                             "import",
                                             100,
                                             dataFile.length,
                                             ccddMain.getMainFrame());

                haltDlg.updateProgressBar("Create snapshot", -1);
                createSnapshotDirectory(parent);

                if ((importFileType == FileExtension.JSON)
                    || (importFileType == FileExtension.CSV)
                    || (importFileType == FileExtension.C_HEADER))
                {
                    haltDlg.updateProgressBar("Preparing import", -1);
                    errorFlag = prepareJSONOrCSVImport(dataFile,
                                                       importingEntireDatabase,
                                                       backupFirst,
                                                       replaceExisting,
                                                       appendExistingFields,
                                                       useExistingFields,
                                                       openEditor,
                                                       ignoreErrors,
                                                       replaceExistingMacros,
                                                       replaceExistingAssociations,
                                                       replaceExistingGroups,
                                                       replaceExistingDataTypes,
                                                       deleteNonExistingTables,
                                                       importFileType,
                                                       dialogType,
                                                       parent);

                    if (usersAndAccessLevel != null && usersAndAccessLevel.size() > 0)
                    {
                        // Execute the database update for users and access levels
                        dbTable.storeNonTableTypesInfoTable(InternalTable.USERS,
                                                            usersAndAccessLevel,
                                                            null,
                                                            ccddMain.getMainFrame());
                    }
                }
                else
                {
                    List<FileEnvVar> dataFiles = Arrays.asList(dataFile);
                    ManagerDialogType importType = null;

                    if (importFileType == FileExtension.EDS)
                    {
                        importType = ManagerDialogType.IMPORT_EDS;
                    }
                    else
                    {
                        importType = ManagerDialogType.IMPORT_XTCE;
                    }

                    // Import the selected table(s)
                    importFiles(dataFiles,
                                backupFirst,
                                importingEntireDatabase,
                                replaceExisting,
                                appendExistingFields,
                                useExistingFields,
                                openEditor,
                                ignoreErrors,
                                replaceExistingMacros,
                                replaceExistingAssociations,
                                replaceExistingGroups,
                                replaceExistingDataTypes,
                                importType,
                                parent);
                }

                deleteSnapshotDirectories(parent);
            }

            /**************************************************************************************
             * Import command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if the user didn't cancel import
                try
                {
                    if (!haltDlg.isHalted())
                    {
                        // Close the cancellation dialog
                        haltDlg.closeDialog();
                    }
                    // Import was canceled
                    else
                    {
                        eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                                          new StringBuilder("Import terminated by user"));
                    }
                }
                catch (NullPointerException e)
                {
                    // There is a null pointer that occurs when no import is completed due to there
                    // being no change in the files
                }
            }
        });

        haltDlg = null;
        return errorFlag;
    }

    /**********************************************************************************************
     * Import one or more files, creating new tables and optionally replacing existing ones. The
     * file(s) may contain definitions for more than one table. This method is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     *
     * @param dataFiles                   Array of files to import
     *
     * @param backupFirst                 True to create a backup of the database before importing
     *                                    tables
     *
     * @param importingEntireDatabase     True if the entire database is being imported
     *
     * @param replaceExistingTables       True to replace a table that already exists in the
     *                                    database
     *
     * @param appendExistingFields        True to append the existing data fields for a table (if
     *                                    any) to the imported ones (if any). Only valid when
     *                                    replaceExisting is true
     *
     * @param useExistingFields           True to use an existing data field in place of the
     *                                    imported ones if the field names match. Only valid when
     *                                    replaceExisting and appendExistingFields are true
     *
     * @param openEditor                  True to open a table editor for each imported table
     *
     * @param ignoreErrors                True to ignore all errors in the import file
     *
     * @param replaceExistingMacros       True to replace the values for existing macros
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @param replaceExistingGroups       True to replace existing group definitions
     *
     * @param replaceExistingDataTypes    True to replace existing data types that share a name
     *                                    with an imported data type
     *
     * @param fileType                    Import file type: IMPORT_CSV, IMPORT_EDS,IMPORT_JSON, or
     *                                    IMPORT_XTCE
     *
     * @param parent                      GUI component over which to center any error dialog
     *
     * @return true if the import operation completes successfully
     *********************************************************************************************/
    protected boolean importFiles(List<FileEnvVar> dataFiles,
                                  boolean backupFirst,
                                  boolean importingEntireDatabase,
                                  boolean replaceExistingTables,
                                  boolean appendExistingFields,
                                  boolean useExistingFields,
                                  boolean openEditor,
                                  boolean ignoreErrors,
                                  boolean replaceExistingMacros,
                                  boolean replaceExistingAssociations,
                                  boolean replaceExistingGroups,
                                  boolean replaceExistingDataTypes,
                                  CcddConstants.ManagerDialogType fileType,
                                  Component parent)
    {
        // Initialize any needed variables
        boolean errorFlag = false;
        String filePath = null;
        CcddImportExportInterface ioHandler = null;
        List<TableDefinition> allTableDefinitions = new ArrayList<TableDefinition>();
        List<String> duplicateDefinitions = new ArrayList<String>();
        List<String> fileNames = new ArrayList<String>();

        // Store the current table type, data type, macro, reserved message ID, and data field
        // information in case it needs to be restored
        List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitionsCopy();
        List<String[]> originalDataTypes = CcddUtilities.copyListOfStringArrays(dataTypeHandler.getDataTypeData());
        String[][] originalInputTypes = CcddUtilities.copyArrayOfStringArrays(inputTypeHandler.getCustomInputTypeData());
        List<String[]> originalMacros = CcddUtilities.copyListOfStringArrays(macroHandler.getMacroData());
        List<String[]> originalReservedMsgIDs = CcddUtilities.copyListOfStringArrays(rsvMsgIDHandler.getReservedMsgIDData());
        List<String[]> originalDataFields = fieldHandler.getFieldDefnsFromInfo();

        // Initialize the macro update lists
        macroHandler.initializeMacroUpdates();

        // Step through each data file to import
        for (FileEnvVar dataFile : dataFiles)
        {
            // Add the file name, with path, to the list
            fileNames.add(dataFile.getAbsolutePath());
        }

        // Add event to log indicating that importing has begun
        eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                          new StringBuilder("Importing table(s) from '").append(CcddUtilities.convertArrayToString(fileNames.toArray(new String[0])))
                                                                        .append("'"));

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, parent);

        // Create a reference to a table editor dialog list
        tableEditorDlgs = new ArrayList<CcddTableEditorDialog>();

        // Check if the user elected to back up the project before importing tables
        if (backupFirst)
        {
            // Back up the project database
            backupDatabaseToFile(false);
        }

        // Process the files to be imported
        try
        {
            int numFilesProcessed = 0;

            // Create a save point in case an error occurs while creating or modifying a table
            dbCommand.createSavePoint(parent);

            // Based on what type of import the user selected, create an appropriate handler
            if (fileType == ManagerDialogType.IMPORT_CSV)
            {
                ioHandler = new CcddCSVHandler(ccddMain, groupHandler, parent);
            }
            else if (fileType == ManagerDialogType.IMPORT_EDS)
            {
                ioHandler = new CcddEDSHandler(ccddMain, parent);
            }
            else if (fileType == ManagerDialogType.IMPORT_JSON)
            {
                ioHandler = new CcddJSONHandler(ccddMain, groupHandler, parent);
            }
            else if (fileType == ManagerDialogType.IMPORT_XTCE)
            {
                ioHandler = new CcddXTCEHandler(ccddMain, parent);
            }
            else
            {
                // Should never reach this point
                throw new CCDDException("Unrecognized file type selection");
            }

            // Step through each file checking for any input type definition available
            for (FileEnvVar file : dataFiles)
            {
                // Check if the file exist
                if (!file.exists())
                {
                    throw new CCDDException("Cannot locate file");
                }

                // Get the file path
                filePath = file.getAbsolutePath();

                // Check if the files being imported are JSON/CSV files
                if ((filePath.endsWith(FileExtension.JSON.getExtension()))
                    || (filePath.endsWith(FileExtension.CSV.getExtension())))
                {
                    String fileName = filePath.substring(filePath.lastIndexOf(File.separator) + 1).split("\\.")[0];

                    if (fileName.equals(FileNames.MACROS.No_Extension()))
                    {
                        // Import any macro type definition found
                        ioHandler.importTableInfo(file,
                                                  ImportType.IMPORT_ALL,
                                                  ignoreErrors,
                                                  replaceExistingMacros,
                                                  replaceExistingTables,
                                                  importingEntireDatabase);
                    }
                    else if (fileName.equals(FileNames.TABLE_INFO.No_Extension()))
                    {
                        // Import any input/data/table type definitions found
                        ioHandler.importInputTypes(file,
                                                   ImportType.IMPORT_ALL,
                                                   ignoreErrors,
                                                   replaceExistingDataTypes,
                                                   importingEntireDatabase);
                        ioHandler.importTableInfo(file,
                                                  ImportType.IMPORT_ALL,
                                                  ignoreErrors,
                                                  replaceExistingMacros,
                                                  replaceExistingTables,
                                                  importingEntireDatabase);
                    }
                    else if (fileName.equals(FileNames.TELEM_SCHEDULER.No_Extension())
                            || fileName.equals(FileNames.APP_SCHEDULER.No_Extension())
                            || fileName.equals(FileNames.SCRIPT_ASSOCIATION.No_Extension()))
                    {
                        // Import any internal table found
                        ioHandler.importInternalTables(file,
                                                       ImportType.IMPORT_ALL,
                                                       ignoreErrors,
                                                       replaceExistingAssociations);
                    }
                }

                // Check if the halt dialog is active (import operation is executed in the
                // background)
                if (haltDlg != null)
                {
                    // Check if the user canceled importing
                    if (haltDlg.isHalted())
                    {
                        throw new CCDDException();
                    }

                    // Update the progress bar
                    haltDlg.updateProgressBar("Reading import file " + file.getName(),
                                              haltDlg.getNumDivisionPerStep() * numFilesProcessed);
                    numFilesProcessed++;
                }

                // Import the table definition(s) from the file
                ioHandler.importFromFile(file,
                                         ImportType.IMPORT_ALL,
                                         null,
                                         ignoreErrors,
                                         replaceExistingMacros,
                                         replaceExistingGroups,
                                         replaceExistingTables);

                if (ioHandler.getTableDefinitions() != null)
                {
                    // Step through each table definition from the import file. This for loop also
                    // guards against trying to import the same file more than once
                    for (TableDefinition tableDefn : ioHandler.getTableDefinitions())
                    {
                        // Indicates if this file has already been imported
                        boolean isDuplicate = false;

                        // Check if the user elected to append any new data fields to any existing
                        // ones for a table
                        if (appendExistingFields)
                        {
                            // Add the imported data field(s) to the table
                            addImportedDataField(tableDefn, tableDefn.getName(), useExistingFields);
                        }

                        // Step through each table definition that has already been imported and
                        // added to the list and compare it to the table definition that is about
                        // to be imported to ensure no duplicates are added
                        for (TableDefinition existingDefn : allTableDefinitions)
                        {
                            // Check if the table is already defined in the list
                            if (tableDefn.getName().equals(existingDefn.getName()))
                            {
                                // Add the table name and associated file name to the list of
                                // duplicates
                                duplicateDefinitions.add(tableDefn.getName()
                                                         + " (file: "
                                                         + file.getName()
                                                         + ")");

                                // Set the flag indicating the table definition is a duplicate and
                                // stop searching
                                isDuplicate = true;
                                break;
                            }
                        }

                        // Check if the table is not already defined
                        if (!isDuplicate)
                        {
                            // Add the table definition to the list
                            allTableDefinitions.add(tableDefn);
                        }
                    }
                }
            }

            // Create the data tables from the imported table definitions from all files
            createTablesFromDefinitions(allTableDefinitions,
                                        replaceExistingTables,
                                        replaceExistingMacros,
                                        openEditor,
                                        groupHandler,
                                        ignoreErrors,
                                        parent);

            // Check if any new application scheduler data has been added
            if (ioHandler.getAppSchedulerData() != null)
            {
                // Store the application scheduler data
                dbTable.storeInformationTable(InternalTable.APP_SCHEDULER,
                                              ioHandler.getAppSchedulerData(),
                                              null,
                                              parent);
            }

            // Check if any new telemetry messages have been added
            if (ioHandler.getTlmSchedulerData() != null)
            {
                // Store the telemetry messages
                dbTable.storeInformationTable(InternalTable.TLM_SCHEDULER,
                                              ioHandler.getTlmSchedulerData(),
                                              null,
                                              parent);
            }

            // Check if any new script associations are being added
            if (ioHandler.getScriptAssociations() != null)
            {
                // Step through each association
                for (int index = 0; index < ioHandler.getScriptAssociations().size(); index++)
                {
                    // Format the table members for storage in the database
                    String[] assn = ioHandler.getScriptAssociations().get(index);
                    assn[AssociationsColumn.MEMBERS.ordinal()] = CcddScriptHandler.convertAssociationMembersFormat(assn[AssociationsColumn.MEMBERS.ordinal()], true);
                    ioHandler.getScriptAssociations().set(index, assn);
                }

                // Store the script associations
                dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
                                              ioHandler.getScriptAssociations(),
                                              null,
                                              parent);
            }

            // Release the save point. This must be done within a transaction block, so it must be
            // done prior to the commit below
            dbCommand.releaseSavePoint(parent);

            // Commit the change(s) to the database
            dbControl.getConnection().commit();

            // Update any open editor's data type columns to include the new table(s), if
            // applicable
            dbTable.updateDataTypeColumns(parent);

            // Restore the root structure table, variable path and offset, and command lists, and
            // the variable, command, and message ID references in case any input types were added
            dbTable.updateListsAndReferences(parent);

            // Update the table type handler with the input type changes
            tableTypeHandler.updateInputTypes(null);

            // If open, update the table type editor's input type column combo box lists to include
            // the new input type(s), if applicable
            dbTable.updateInputTypeColumns(null, parent);

            // Check if any duplicate table definitions were detected
            if (!duplicateDefinitions.isEmpty())
            {
                // Inform the user that one or more duplicate table definitions were detected
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Ignored the following duplicate table definition(s):</b><br>"
                                                          + CcddUtilities.convertArrayToStringTruncate(duplicateDefinitions.toArray(new String[0])),
                                                          "Duplicate Table(s)",
                                                          JOptionPane.INFORMATION_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }

            // Add event to log indicating that the import completed successfully
            eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                              new StringBuilder("Table import completed successfully"));
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot read import file '</b>"
                                                      + filePath
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
        catch (CCDDException cse)
        {
            // Check if this is an internally generated exception and that an error message is
            // provided
            if (cse instanceof CCDDException && !cse.getMessage().isEmpty())
            {
                // Inform the user that an error occurred importing the table(s)
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Cannot import from file '</b>"
                                                          + filePath
                                                          + "<b>': "
                                                          + cse.getMessage(),
                                                          "Import Error",
                                                          cse.getMessageType(),
                                                          DialogOption.OK_OPTION);
            }

            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        // Check if an error occurred during the import operation
        if (errorFlag)
        {
            try
            {
                // Revert any changes made to the database
                dbCommand.rollbackToSavePoint(parent);
            }
            catch (SQLException se)
            {
                // Inform the user that rolling back the changes failed
                eventLog.logFailEvent(parent,
                                      "Cannot revert changes to project; cause '"
                                      + se.getMessage()
                                      + "'",
                                      "<html><b>Cannot revert changes to project");
            }

            // Step through each table editor dialog created during the import operation
            for (CcddTableEditorDialog tableEditorDlg : tableEditorDlgs)
            {
                // Close the editor dialog
                tableEditorDlg.closeFrame();
            }

            // Restore the table types, data types, macros, reserved message IDs, and data fields
            // to the values prior to the import operation
            tableTypeHandler.setTypeDefinitions(originalTableTypes);
            dataTypeHandler.setDataTypeData(originalDataTypes);
            inputTypeHandler.setInputTypeData(originalInputTypes);
            fieldHandler.setFieldInformationFromDefinitions(originalDataFields);
            macroHandler.setMacroData(originalMacros);
            rsvMsgIDHandler.setReservedMsgIDData(originalReservedMsgIDs);
            dbTable.updateListsAndReferences(parent);

            // Check if the table type editor is open
            if (ccddMain.getTableTypeEditor() != null && ccddMain.getTableTypeEditor().isShowing())
            {
                // Remove any table types that were created during the import process, since these
                // are now invalid
                ccddMain.getTableTypeEditor().removeInvalidTabs();
            }

            // Add event to log indicating that the import did not complete successfully
            eventLog.logEvent(EventLogMessageType.FAIL_MSG,
                              new StringBuilder("Table import failed to complete"));
        }

        // Step through each table editor dialog created during the import operation
        for (CcddTableEditorDialog tableEditorDlg : tableEditorDlgs)
        {
            // Enable the editor dialog's command menu items
            tableEditorDlg.setControlsEnabled(true);
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Reorder the list of table definitions so that tables referenced as a data type or in a
     * sizeof() call appear in the list prior to the table that references them
     *
     * @param tableDefinitions List of table definitions for the table(s) to create
     *
     * @return The list of table definitions, reordered so that tables referenced as a data type or
     *         in a sizeof() call appear in the list prior to the table that references them
     *********************************************************************************************/
    private List<TableDefinition> orderTableDefinitionsByReference(List<TableDefinition> tableDefinitions)
    {
        List<TableDefinition> orderedTableDefinitions = new ArrayList<TableDefinition>();
        List<String> orderedTableNames = new ArrayList<String>();

        // Add the table names with paths to a names-only list
        for (TableDefinition tableDefn : tableDefinitions)
        {
            orderedTableNames.add(tableDefn.getName());
        }

        // Step through each table definition
        for (TableDefinition tableDefn : tableDefinitions)
        {
            int column = 0;

            // Get the table's type definition
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

            // Step through each cell value in the table
            for (String cellValue : tableDefn.getData())
            {
                // Check if the cell value is present
                if (cellValue != null && !cellValue.isEmpty())
                {
                    // Check if this is a structure reference in the data type column
                    if (column == CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT))
                        && !dataTypeHandler.isPrimitive(cellValue))
                    {
                        // Add the structure name to the list if not already present
                        if (!orderedTableNames.contains(cellValue))
                        {
                            orderedTableNames.add(cellValue);
                        }
                    }
                    // This isn't a structure reference in the data type column
                    else
                    {
                        // Step through each macro referenced in the cell value
                        for (String macroName : macroHandler.getReferencedMacros(cellValue))
                        {
                            // Step through each structure referenced by the macro
                            for (String structureName : macroHandler.getStructureReferences(macroName))
                            {
                                // Add the structure name to the list if not already present
                                if (!orderedTableNames.contains(structureName))
                                {
                                    orderedTableNames.add(structureName);
                                }
                            }
                        }
                    }
                }

                // Update the table data column index. When the end of the row is reached then
                // reset to the first column
                column++;

                if (column == typeDefn.getColumnCountVisible())
                {
                    column = 0;
                }
            }
        }

        // At this point the list contains all the structure names selected or referenced. Next
        // sort the table definitions alphabetically by the table path+names and by the number of
        // path members
        Collections.sort(tableDefinitions, new Comparator<Object>()
        {
            /**************************************************************************************
             * Compare table names
             *
             * @param tblInfo1 First table's information
             *
             * @param tblInfo2 Second table's information
             *
             * @return -1 if the first table's name is lexically less than the second or has fewer
             *         commas table's name; 0 if the two table names are the same or have the same
             *         number of commas; 1 if the first table's name is lexically greater than the
             *         second table's name or has more commas
             *************************************************************************************/
            @Override
            public int compare(Object tblInfo1, Object tblInfo2)
            {
                int sortOrder = 0;
                int numCommas1 = StringUtils.countMatches(((TableDefinition) tblInfo1).getName(), ",");
                int numCommas2 = StringUtils.countMatches(((TableDefinition) tblInfo2).getName(), ",");

                if (numCommas1 < numCommas2)
                {
                    sortOrder = -1;
                }
                else if (numCommas1 > numCommas2)
                {
                    sortOrder = 1;
                }
                else
                {
                    sortOrder = ((TableDefinition) tblInfo1).getName().compareTo(((TableDefinition) tblInfo2).getName());
                }

                return sortOrder;
            }
        });

        // Create a list containing only the prototype table names
        List<String> protoTableNames = new ArrayList<String>();

        for (String tableName : orderedTableNames)
        {
            if (!tableName.contains(","))
            {
                protoTableNames.add(tableName);
            }
        }

        // Step through the prototype tables, reordering them as needed to ensure a reference
        // prototype appears in the list ahead of the table that referenced it. This process must
        // be repeated until no changes are made to the list since a prototype can be referenced by
        // multiple prototypes
        boolean changed = true;

        while (changed)
        {
            changed = false;

            for (String tableName : protoTableNames)
            {
                int protoIndex = orderedTableNames.indexOf(tableName);

                for (TableDefinition tableDefn : tableDefinitions)
                {
                    // Check if the table names match
                    if (tableName.equals(tableDefn.getName()))
                    {
                        int column = 0;

                        // Get the table's type definition
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                        // Step through each cell value in the table
                        for (String cellValue : tableDefn.getData())
                        {
                            // Check if the cell value is present
                            if (cellValue != null && !cellValue.isEmpty())
                            {
                                // Check if this is a structure reference in the data type column
                                if (column == CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT))
                                    && !dataTypeHandler.isPrimitive(cellValue))
                                {
                                    // Check if the referenced prototype appears below the calling
                                    // table in the ordered list
                                    int refIndex = orderedTableNames.indexOf(cellValue);

                                    if (refIndex > protoIndex)
                                    {
                                        // Move the referenced prototype ahead of the calling table
                                        orderedTableNames.remove(refIndex);
                                        orderedTableNames.add(protoIndex, cellValue);
                                        changed = true;
                                    }
                                }
                            }

                            // Update the table data column index. When the end of the row is
                            // reached then reset to the first column
                            column++;

                            if (column == typeDefn.getColumnCountVisible())
                            {
                                column = 0;
                            }
                        }

                        break;
                    }
                }
            }
        }

        // The names are now in the correct reference order. Use the name list to set the order of
        // the table definitions
        for (String tableName : orderedTableNames)
        {
            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions)
            {
                // Check if the table names match
                if (tableName.equals(tableDefn.getName()))
                {
                    // Add the table definition to the list in the order it's referenced and stop
                    // searching
                    orderedTableDefinitions.add(tableDefn);
                    break;
                }
            }
        }

        return orderedTableDefinitions;
    }

    /**********************************************************************************************
     * Create one or more data tables from the supplied table definitions
     *
     * @param tableDefinitions      List of table definitions for the table(s) to create
     *
     * @param replaceExistingTables True to replace a table that already exists in the database
     *
     * @param replaceExistingMacros True to replace the values for existing macros
     *
     * @param openEditor            True to open a table editor for each imported table
     *
     * @param groupHandler          Group handler reference
     *
     * @param ignoreErrors          Should errors be ignored
     *
     * @param parent                GUI component over which to center any error dialog
     *
     * @throws CCDDException If the table path name is invalid or the table cannot be created from
     *                       the table definition
     *********************************************************************************************/
    private void createTablesFromDefinitions(List<TableDefinition> tableDefinitions,
                                             boolean replaceExistingTables,
                                             boolean replaceExistingMacros,
                                             boolean openEditor,
                                             CcddGroupHandler groupHandler,
                                             boolean ignoreErrors,
                                             final Component parent) throws CCDDException
    {
        boolean prototypesOnly = true;
        List<String> skippedTables = new ArrayList<String>();
        int numTablesProcessed = 0;


        // Store the current group information
        ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.GROUPS,
                                                                  groupHandler.getGroupDefnsFromInfo(),
                                                                  null,
                                                                  parent);

        // Check if the user elected to enable replacement of existing macro values
        if (replaceExistingMacros)
        {
            // Verify that the new macro values are valid for the current instances of the macros
            macroHandler.validateMacroUsage(parent);

            // Update the usage of the macros in the tables
            macroHandler.updateExistingMacroUsage(parent);
        }

        // Set the macro data to the updated macro list
        macroHandler.setMacroData();

        // Reorder the table definitions so that those referenced by a table as a data type or in a
        // sizeof() call appear in the list before the table
        tableDefinitions = orderTableDefinitionsByReference(tableDefinitions);

        // Get the list of all tables, including the paths for child structure tables
        CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain,
                                                                  TableTreeType.TABLES, parent);
        List<String> allTables = tableTree.getTableTreePathList(null);

        // Check if the cancel import dialog is present
        if (haltDlg != null)
        {
            // Initialize the progress bar within-step total
            haltDlg.setItemsPerStep(tableDefinitions.size());
        }

        // Perform two passes; first to process prototype tables, and second to process child
        // tables
        for (int loop = 1; loop <= 2; loop++)
        {
            int tableDefnIndex = 0;
            boolean lastTableDefn = false;

            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions)
            {
                // Check if the halt dialog is active (import operation is executed in the
                // background)
                if (haltDlg != null)
                {
                    // Check if the user canceled importing
                    if (haltDlg.isHalted())
                    {
                        throw new CCDDException();
                    }

                    // Update the progress bar
                    haltDlg.updateProgressBar("Creating table " + tableDefn.getName(),
                                              haltDlg.getNumDivisionPerStep() * numTablesProcessed);
                    numTablesProcessed++;
                }

                // Is this the last/only table definition?
                if (tableDefnIndex == tableDefinitions.size() - 1)
                {
                    lastTableDefn = true;
                }

                // Check if this is a prototype table and this is the first pass, or if this is a
                // child table and this is the second pass
                if (tableDefn.getName().contains(".") != prototypesOnly)
                {
                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                    // Get the number of table columns
                    int numColumns = typeDefn.getColumnCountVisible();

                    // Attempt to load the table's data from the database. This succeeds if the
                    // table already exists, and sets the error flag if the table is new
                    TableInfo tableInfo = dbTable.loadTableData(tableDefn.getName(),
                                                                true,
                                                                true,
                                                                true,
                                                                ccddMain.getMainFrame());

                    // Check if the table failed to load (either doesn't already exist (it's new)
                    // or an error occurred
                    if (tableInfo.isErrorFlag())
                    {
                        // Check if this is a child (instance) table
                        if (tableDefn.getName().contains("."))
                        {
                            // If the table that has been modified is an instance of a prototype
                            // then there is no table in the postgres database that shares its name
                            // and stores its data. Instead the information that varies from the
                            // prototype table is stored in the internal values table. In this
                            // situation we can actually just pull the information of the prototype
                            // table and use it to create the commands required to update the
                            // database. Load the information of the prototype
                            int index = tableDefn.getName().lastIndexOf(",") + 1;
                            String ancestor = tableDefn.getName().substring(index).split("\\.")[0];

                            // Check if the prototype exists
                            if (dbTable.isTableExists(ancestor, parent))
                            {
                                // Use the prototype to populate the table information for the new
                                // table
                                tableInfo = dbTable.loadTableData(ancestor,
                                                                  true,
                                                                  true,
                                                                  false,
                                                                  ccddMain.getMainFrame());
                            }
                            else
                            {
                                // Create the table information for the new table, but with no data
                                tableInfo = new TableInfo(tableDefn.getTypeName(),
                                                          tableDefn.getName(),
                                                          new String[0][0],
                                                          tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                          tableDefn.getDescription(),
                                                          fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));
                            }

                            // Keep the name of the instance even though we are going to use the
                            // info of the prototype. The prototype info is only needed for
                            // comparison purposes and we do not want to make any unwanted changes
                            // to it as it would update all instances of this prototype within the
                            // database
                            tableInfo.setTablePath(tableDefn.getName());
                        }
                        // This is a new prototype table
                        else
                        {
                            // Create the table information for the new table, but with no data
                            tableInfo = new TableInfo(tableDefn.getTypeName(),
                                                      tableDefn.getName(),
                                                      new String[0][0],
                                                      tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                      tableDefn.getDescription(),
                                                      fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));
                        }
                    }

                    // Add the fields from the table to be created, old fields are dropped
                    tableInfo.setFieldInformation(fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));

                    // Add the description from the table to be created, old description is dropped
                    tableInfo.setDescription(tableDefn.getDescription());

                    // Get the table's root table name
                    String rootTable = tableInfo.getRootTable();
                    boolean isRootTable = false;

                    // Are we working with a root table?
                    if (rootTable.contentEquals(tableInfo.getTablePath()))
                    {
                        isRootTable = true;
                    }

                    // Check if the new table is not a prototype. The prototype for the table and
                    // each of its ancestors need to be created if these don't exist already
                    if (!tableInfo.isPrototype())
                    {
                        // Break the path into the individual structure variable references
                        String[] ancestors = tableInfo.getTablePath().split(",");

                        // Step through each structure table referenced in the path of the new
                        // table, starting with the current child table and working back through
                        // its ancestor tables. The purpose is to determine if all of the
                        // prototypes exist for the ancestors of this table; if not, a prototype is
                        // created based on the values in this child table
                        for (int index = ancestors.length - 1; index >= 0; index--)
                        {
                            // Split the ancestor reference into the data type (i.e., structure
                            // name) and variable name
                            String[] typeAndVar = ancestors[index].split("\\.");

                            // Check if the ancestor prototype table doesn't exist
                            if (!dbTable.isTableExists(typeAndVar[0], ccddMain.getMainFrame()))
                            {
                                // Create the table information for the new prototype table
                                TableInfo ancestorInfo = new TableInfo(tableDefn.getTypeName(),
                                                                       typeAndVar[0],
                                                                       new String[0][0],
                                                                       tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                                       "",
                                                                       new ArrayList<FieldInformation>());

                                // Check if this is the child table's prototype, and not an
                                // ancestor's prototype
                                if (index == ancestors.length - 1)
                                {
                                    // Create a list to store a copy of the cell data
                                    List<String> protoData = new ArrayList<String>(tableDefn.getData());

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo,
                                                             protoData,
                                                             numColumns,
                                                             replaceExistingTables,
                                                             openEditor,
                                                             allTables,
                                                             lastTableDefn,
                                                             isRootTable,
                                                             false,
                                                             parent))
                                    {
                                        // Add the skipped table to the list
                                        skippedTables.add(ancestorInfo.getTablePath());
                                    }
                                    // The table was created
                                    else
                                    {
                                        // Add the table name to the list of existing tables
                                        allTables.add(ancestorInfo.getTablePath());
                                    }
                                }
                                // This is an ancestor prototype of the child table
                                else
                                {
                                    // Split the ancestor into the data type (i.e., structure name)
                                    // and variable name
                                    typeAndVar = ancestors[index + 1].split("\\.|$", -1);

                                    // Add the variable reference to the new table
                                    String[] rowData = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(rowData, "");
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.PRIM_AND_STRUCT))] = typeAndVar[0];
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.VARIABLE))] = typeAndVar[1];

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo,
                                                             Arrays.asList(rowData),
                                                             numColumns,
                                                             replaceExistingTables,
                                                             openEditor,
                                                             allTables,
                                                             lastTableDefn,
                                                             isRootTable,
                                                             false,
                                                             parent))
                                    {
                                        // Add the skipped table to the list
                                        skippedTables.add(ancestorInfo.getTablePath());
                                    }
                                    // The table was created
                                    else
                                    {
                                        // Add the table name to the list of existing tables
                                        allTables.add(ancestorInfo.getTablePath());
                                    }
                                }
                            }
                        }
                    }

                    // Create a table from the imported information
                    if (!createImportedTable(tableInfo,
                                             tableDefn.getData(),
                                             numColumns,
                                             replaceExistingTables,
                                             openEditor,
                                             allTables,
                                             lastTableDefn,
                                             isRootTable,
                                             ignoreErrors,
                                             parent))
                    {
                        // Add the skipped table to the list
                        skippedTables.add(tableInfo.getTablePath());
                    }
                    // The table was created
                    else
                    {
                        // Add the table name to the list of existing tables
                        allTables.add(tableInfo.getTablePath());
                    }
                }

                tableDefnIndex++;
            }

            prototypesOnly = false;
        }

        // Check if the cancel import dialog is present
        if (haltDlg != null)
        {
            // Update the progress bar
            haltDlg.updateProgressBar("Updating internal tables...", -1);
        }

        // Check if any tables were skipped
        if (!skippedTables.isEmpty())
        {
            // Inform the user that one or more tables were not imported
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Table(s) not imported '</b>"
                                                      + CcddUtilities.convertArrayToStringTruncate(skippedTables.toArray(new String[0]))
                                                      + "<b>';<br>table already exists",
                                                      "Import Warning",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        // Store the table types
        dbTable.storeInformationTable(InternalTable.TABLE_TYPES, null, null, parent);

        // Store the data types
        dbTable.storeInformationTable(InternalTable.DATA_TYPES,
                                      CcddUtilities.removeArrayListColumn(dataTypeHandler.getDataTypeData(),
                                                                          DataTypesColumn.ROW_NUM.ordinal()),
                                      null,
                                      parent);

        // Store the input types
        dbTable.storeInformationTable(InternalTable.INPUT_TYPES,
                                      CcddUtilities.removeArrayListColumn(Arrays.asList(inputTypeHandler.getCustomInputTypeData()),
                                                                          InputTypesColumn.ROW_NUM.ordinal()),
                                      null,
                                      parent);

        // Store the data fields
        dbTable.storeInformationTable(InternalTable.FIELDS, fieldHandler.getFieldDefnsFromInfo(), null, parent);

        // Check to see if something caused the group data to change. Example: If a table contains
        // an array whose data type is defined by another table in CCDD and the size is based on a
        // macro there is an edge case that occurs if the macro size is changed during an import.
        // If the macro value is reduced and this table is included in a group than the array
        // members that were removed from the table need to be removed from the internal groups
        // table as well
        dbTable.cleanGroupsTable(parent);

        // Retrieve the updated group information
        List<String[]> updatedGroupData = dbTable.retrieveInformationTable(InternalTable.GROUPS, false, parent);
        ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.GROUPS, updatedGroupData, null, parent);

        // Check if any macros are defined
        if (!macroHandler.getMacroData().isEmpty())
        {
            // Store the macros in the database
            dbTable.storeInformationTable(InternalTable.MACROS,
                                          CcddUtilities.removeArrayListColumn(macroHandler.getMacroData(),
                                                                              MacrosColumn.ROW_NUM.ordinal()),
                                          null, parent);
        }

        // Check if any reserved message IDs are defined
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
        {
            // Store the reserved message IDs in the database
            dbTable.storeInformationTable(InternalTable.RESERVED_MSG_IDS,
                                          CcddUtilities.removeArrayListColumn(rsvMsgIDHandler.getReservedMsgIDData(),
                                                                              ReservedMsgIDsColumn.ROW_NUM.ordinal()),
                                          null, parent);
        }
    }

    /**********************************************************************************************
     * Create a new data table or replace an existing one and paste the supplied cell data into it
     *
     * @param tableInfo       Table information for the table to create
     *
     * @param cellData        Array containing the cell data
     *
     * @param numColumns      Number of columns in the table
     *
     * @param replaceExisting True to replace a table that already exists in the database
     *
     * @param openEditor      True to open a table editor for each imported table
     *
     * @param allTables       List containing the paths and names of all tables
     *
     * @param lastTableDefn   Is this the last table definition to be imported?
     *
     * @param isRootTable     Does the tableInfo belong to a root table? If 'lastTableDefn' is true
     *                        the value of 'isRootTable' does not matter.
     *
     * @param ignoreErrors    Should errors be ignored
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return true if the table is successfully imported; false if the table exists and the
     *         replaceExisting flag is not true
     *
     * @throws CCDDException The existing table that the new table replaces cannot be removed, the
     *                       new table cannot be created, or the data cannot be added to the newly
     *                       created table
     *********************************************************************************************/
    private boolean createImportedTable(TableInfo tableInfo,
                                        List<String> cellData,
                                        int numColumns,
                                        boolean replaceExisting,
                                        boolean openEditor,
                                        List<String> allTables,
                                        boolean lastTableDefn,
                                        boolean isRootTable,
                                        boolean ignoreErrors,
                                        Component parent) throws CCDDException
    {
        boolean isImported = true;
        List<String[]> tableName = new ArrayList<String[]>();

        // Set the flag if the table already is present in the database
        boolean isExists = allTables.contains(tableInfo.getTablePath());

        // Check if the table already exists and if the user didn't elect to replace existing
        // tables
        if (isExists && !replaceExisting)
        {
            // Set the flag to indicate the table wasn't imported
            isImported = false;
        }
        // Table either doesn't exist, or else it does exist and is being replaced
        else
        {
            if (!tableInfo.isPrototype())
            {
                isExists = allTables.contains(tableInfo.getPrototypeName());
            }

            // Check if the child structure table exists
            if (isExists)
            {
                // Add the parent and prototype table name to the list of table editors to close
                tableName.add(new String[] {tableInfo.getParentTable(), tableInfo.getPrototypeName()});
            }
            else
            {
                // Create the table in the database
                if (dbTable.createTable(new String[] {tableInfo.getPrototypeName()},
                                        tableInfo.getDescription(),
                                        tableInfo.getType(),
                                        true,
                                        true,
                                        parent))
                {
                    throw new CCDDException();
                }
            }
        }

        // Check if the prototype was successfully created, or if the table isn't a prototype and
        // doesn't already exist
        if (isImported)
        {
            CcddTableEditorHandler tableEditor;

            // Step through the data fields assigned to this table's type definition (i.e., the
            // table's default data fields)
            for (FieldInformation typeFldinfo : fieldHandler.getFieldInformationByOwnerCopy(CcddFieldHandler.getFieldTypeName(tableInfo.getType())))
            {
                // Add or update the table type field to the table, depending on whether or not the
                // has the field
                fieldHandler.addUpdateInheritedField(tableInfo.getFieldInformation(),
                                                     tableInfo.getTablePath(),
                                                     typeFldinfo);
            }

            // Close any editors associated with this prototype table
            dbTable.closeDeletedTableEditors(tableName, ccddMain.getMainFrame());

            // Check if an editor is to be opened for the imported table(s)
            if (openEditor)
            {
                final CcddTableEditorDialog tableEditorDlg;

                // Create a list to hold the table's information
                List<TableInfo> tableInformation = new ArrayList<TableInfo>();
                tableInformation.add(tableInfo);

                // Check if a table editor dialog has not already been created for the added
                // tables, or if the number of tables opened in the editor has reached the maximum
                // allowed
                if (tableEditorDlgs.isEmpty()
                    || tableEditorDlgs.get(tableEditorDlgs.size() - 1).getTabbedPane().getTabRunCount()
                       % ModifiableSizeInfo.MAX_IMPORTED_TAB_ROWS.getSize() == 0)
                {
                    // Create a table editor dialog and open the new table editor in it
                    tableEditorDlg = new CcddTableEditorDialog(ccddMain, tableInformation);
                    ccddMain.getTableEditorDialogs().add(tableEditorDlg);
                    ccddMain.updateRecentTablesMenu();
                    tableEditorDlg.setControlsEnabled(false);
                    tableEditorDlgs.add(tableEditorDlg);

                    if (haltDlg != null)
                    {
                        // Force the dialog to the front
                        haltDlg.toFront();
                    }
                }
                // A table editor dialog is already created and hasn't reached the maximum number
                // of tabs
                else
                {
                    // Get the reference to the last editor dialog created
                    tableEditorDlg = tableEditorDlgs.get(tableEditorDlgs.size() - 1);

                    // Add the table editor to the existing editor dialog
                    tableEditorDlg.addTablePanes(tableInformation);
                }

                // Get the reference to the table's editor
                tableEditor = tableEditorDlg.getTableEditor();

                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /******************************************************************************
                     * Since this involves a GUI update use invokeLater to execute the call on the
                     * event dispatch thread
                     *****************************************************************************/
                    @Override
                    public void run()
                    {
                        // (Re)draw the halt and the table editor dialogs
                        haltDlg.update(haltDlg.getGraphics());
                        tableEditorDlg.update(tableEditorDlg.getGraphics());
                    }
                });
            }
            // Create the table without opening an editor
            else
            {
                // Create the table editor handler without displaying the table
                tableEditor = new CcddTableEditorHandler(ccddMain, tableInfo, null);
            }

            // Paste the data into the table; check if the user canceled importing the table
            // following a cell validation error
            if (tableEditor.getTable().pasteData(cellData.toArray(new String[0]),
                                                 numColumns,
                                                 false,
                                                 true,
                                                 true,
                                                 true,
                                                 false,
                                                 false,
                                                 tableInfo.isPrototype())) // It's okay to overwrite existing 'unalterable'
                                                                           // cells if it's a proto/root (or not a structure)
            {
                eventLog.logEvent(EventLogMessageType.STATUS_MSG, new StringBuilder("Import canceled by user"));
                throw new CCDDException();
            }

            // Build the addition, modification, and deletion command lists
            tableEditor.buildUpdates();

            // Perform the changes to the table in the database
            if (dbTable.modifyTableData(tableEditor.getTableInformation(),
                                        tableEditor.getAdditions(),
                                        tableEditor.getModifications(),
                                        tableEditor.getDeletions(),
                                        false,
                                        false,
                                        true,
                                        true,
                                        true,
                                        null,
                                        lastTableDefn,
                                        isRootTable,
                                        ignoreErrors,
                                        parent))
            {
                throw new CCDDException();
            }
        }

        return isImported;
    }

    /**********************************************************************************************
     * Import the contents of a file selected by the user into the specified existing table
     *
     * @param dialogType   Import file type: IMPORT_CSV, IMPORT_EDS,IMPORT_JSON, or IMPORT_XTCE
     *
     * @param dialogPanel  Import dialog panel
     *
     * @param tableHandler Reference to the table handler for the table into which to import the
     *                     data
     *********************************************************************************************/
    protected void importSelectedFileIntoTable(ManagerDialogType dialogType,
                                               JPanel dialogPanel,
                                               CcddTableEditorHandler tableHandler)
    {
        // Initialize variables
        List<TableDefinition> tableDefinitions = null;
        CcddImportExportInterface ioHandler = null;
        FileExtension importFileType;
        FileEnvVar[] dataFile = {};

        try
        {
            // Check what type of extension is needed
            FileNameExtensionFilter[] extFilter = new FileNameExtensionFilter[1];
            CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame());
            if (dialogType == ManagerDialogType.IMPORT_JSON)
            {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.JSON.getDescription(),
                                                           FileExtension.JSON.getExtensionName());

                // Create a JSON handler
                ioHandler = new CcddJSONHandler(ccddMain, groupHandler, tableHandler.getOwner());
                importFileType = FileExtension.JSON;
            }
            else if (dialogType == ManagerDialogType.IMPORT_CSV)
            {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.CSV.getDescription(),
                                                           FileExtension.CSV.getExtensionName());

                // Create a CSV handler
                ioHandler = new CcddCSVHandler(ccddMain, groupHandler, tableHandler.getOwner());
                importFileType = FileExtension.CSV;
            }
            else if (dialogType == ManagerDialogType.IMPORT_XTCE)
            {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.XTCE.getDescription(),
                                                           FileExtension.XTCE.getExtensionName());

                // Create an XTCE handler
                ioHandler = new CcddXTCEHandler(ccddMain, tableHandler.getOwner());
                importFileType = FileExtension.XTCE;
            }
            else
            {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.EDS.getDescription(),
                                                           FileExtension.EDS.getExtensionName());

                // Create an EDS handler
                ioHandler = new CcddEDSHandler(ccddMain, tableHandler.getOwner());
                importFileType = FileExtension.EDS;
            }

            // Allow the user to select the data file path + name to import from
            dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                              tableHandler.getOwner(),
                                                              null,
                                                              "export",
                                                              extFilter,
                                                              false,
                                                              false,
                                                              "Import Table Data",
                                                              ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(),
                                                                                          null),
                                                              DialogOption.IMPORT_OPTION,
                                                              dialogPanel);

            // Get the state of the check-boxes for later use
            Boolean overwriteChkBxSelected = ((JCheckBox) dialogPanel.getComponent(overwriteExistingCbIndex)).isSelected();
            Boolean appendToExistingDataCbSelected = ((JCheckBox) dialogPanel.getComponent(appendToExistingDataCbIndex)).isSelected();
            Boolean ignoreErrorsCbSelected = ((JCheckBox) dialogPanel.getComponent(ignoreErrorsCbIndex)).isSelected();
            Boolean keepDataFieldsCbSelected = ((JCheckBox) dialogPanel.getComponent(keepDataFieldsCbIndex)).isSelected();

            // Check if a file was chosen
            if (dataFile != null && dataFile[0] != null)
            {
                // Store the current table type information so that it can be restored
                List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitions();

                // Place the table name in an array so that it can be easily passed to the
                // compareToSnapshotDirectory function
                String[] tableName = {dataFile[0].getName().split("\\.")[0]};

                // Current data field information belonging to the active table editor
                List<FieldInformation> currentDataFieldInfo = fieldHandler.getFieldInformationByOwner(tableName[0]);

                // Check to see if we are importing a JSON or CSV file
                if (importFileType == FileExtension.JSON || importFileType == FileExtension.CSV)
                {
                    // The snapshot directory will be created and populated with a file that
                    // represents the current contents of the table in which this imported file is
                    // to be imported into. This allows an easy comparison of the two files to see
                    // if they are identical
                    List<File> snapshotFiles = compareToSnapshotDirectory(tableName,
                                                                          false,
                                                                          importFileType,
                                                                          false,
                                                                          ccddMain.getMainFrame());

                    // Check to see if the files are identical
                    if (!snapshotFiles.isEmpty())
                    {
                        if (!FileUtils.contentEqualsIgnoreEOL(new File(dataFile[0].getPath()),
                                                              new File(snapshotFiles.get(0).getPath()),
                                                              null))
                        {
                            // Import the data file into a table definition
                            ioHandler.importFromFile(dataFile[0], ImportType.IMPORT_ALL,
                                                     tableHandler.getTableTypeDefinition(),
                                                     ignoreErrorsCbSelected,
                                                     false,
                                                     false,
                                                     overwriteChkBxSelected);
                            tableDefinitions = ioHandler.getTableDefinitions();
                        }
                    }
                }
                else
                {
                    // Import the data file into a table definition
                    ioHandler.importFromFile(dataFile[0],
                                             ImportType.IMPORT_ALL,
                                             tableHandler.getTableTypeDefinition(),
                                             ignoreErrorsCbSelected,
                                             false,
                                             false,
                                             overwriteChkBxSelected);
                    tableDefinitions = ioHandler.getTableDefinitions();

                    // Change the owner of the table to the table that is being modified in the
                    // table editor
                    if (tableDefinitions != null && !tableDefinitions.isEmpty())
                    {
                        tableDefinitions.get(0).setName(tableHandler.getOwnerName());
                    }
                }

                // Check to see if the files are identical. If so no other work needs to be done
                if (tableDefinitions != null && !tableDefinitions.isEmpty())
                {
                    // Get a short-cut to the table definition to shorten subsequent calls
                    TableDefinition tableDefn = tableDefinitions.get(0);

                    // Check if any data fields were provided and if we are appending
                    if ((appendToExistingDataCbSelected
                         && (dialogType == ManagerDialogType.IMPORT_JSON
                             || dialogType == ManagerDialogType.IMPORT_JSON))
                        || (keepDataFieldsCbSelected))
                    {
                        List<String[]> importedDataFieldInfo = new ArrayList<String[]>(tableDefn.getDataFields());

                        tableDefn.removeDataFields();

                        for (int index = 0; index < currentDataFieldInfo.size(); index++)
                        {
                            tableDefn.addDataField(currentDataFieldInfo.get(index));
                        }

                        for (int index = 0; index < importedDataFieldInfo.size(); index++)
                        {
                            // If the imported field does not already exist then add it
                            if (fieldHandler.getFieldInformationByName(tableName[0],
                                                                       importedDataFieldInfo.get(index)[FieldsColumn.FIELD_NAME.ordinal()]) == null)
                            {
                                tableDefn.addDataField(importedDataFieldInfo.get(index));
                            }
                        }
                    }

                    // End any active edit sequence, then disable auto-ending so that the import
                    // operation can be handled as a single edit for undo/redo purposes
                    tableHandler.getTable().getUndoManager().endEditSequence();
                    tableHandler.getTable().getUndoHandler().setAutoEndEditSequence(false);

                    // Update the table description field in case the description changed
                    tableHandler.setDescription(tableDefn.getDescription());

                    // Add the imported data field(s) to the table
                    addImportedDataField(tableDefn,
                                         tableHandler.getTableInformation().getTablePath(),
                                         appendToExistingDataCbSelected);

                    // Rebuild the table's editor panel which contains the data fields
                    tableHandler.createDataFieldPanel(true,
                                                      fieldHandler.getFieldInformationFromData(tableDefn.getDataFields().toArray(new String[0][0]), ""),
                                                      true);

                    // Check if cell data is provided in the table definition
                    if (tableDefn.getData() != null && !tableDefn.getData().isEmpty())
                    {
                        // Get the original number of rows in the table
                        int numRows = tableHandler.getTableModel().getRowCount();

                        // Get the column count
                        int columnCount = 0;
                        if (importFileType == FileExtension.JSON || importFileType == FileExtension.CSV)
                        {
                            columnCount = tableHandler.getTable().getColumnCount();
                        }
                        else
                        {
                            TypeDefinition type = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());
                            columnCount = type.getColumnCountVisible();
                        }

                        // Paste the data into the table; check if the user hasn't canceled
                        // importing the table following a cell validation error
                        if (!tableHandler.getTable().pasteData(tableDefn.getData().toArray(new String[0]),
                                                               columnCount,
                                                               !overwriteChkBxSelected,
                                                               !overwriteChkBxSelected,
                                                               overwriteChkBxSelected,
                                                               false,
                                                               true,
                                                               false,
                                                               false))
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

                    // Restore the table types to the values prior to the import operation
                    tableTypeHandler.setTypeDefinitions(originalTableTypes);

                    // Re-enable auto-ending of the edit sequence and end the sequence. The
                    // imported data can be removed with a single undo if desired
                    tableHandler.getTable().getUndoHandler().setAutoEndEditSequence(true);
                    tableHandler.getTable().getUndoManager().endEditSequence();
                }
                else
                {
                    // Inform the user that there are no perceptible changes to the files relative
                    // to current db state
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>The selected folder/file does "
                                                              + "not contain any updates</b>",
                                                              "No Changes Made",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                // Store the data file path in the program preferences backing store
                storePath(ccddMain,
                          dataFile[0].getAbsolutePathWithEnvVars(),
                          true,
                          ModifiablePathInfo.TABLE_EXPORT_PATH);
            }
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                                      "<html><b>Cannot read import file '</b>"
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
                // Inform the user that an error occurred reading the import file
                new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                                          "<html><b>"
                                                          + ce.getMessage(),
                                                          "Import Error",
                                                          ce.getMessageType(),
                                                          DialogOption.OK_OPTION);
            }
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, tableHandler.getOwner());
        }
    }

    /**********************************************************************************************
     * Import one or more data fields into a table, appending them to any existing fields. If the
     * imported field already exists then the input flag determines if the existing or imported
     * field is used
     *
     * @param tableDefn         Imported table definition
     *
     * @param ownerName         Data field owner name
     *
     * @param useExistingFields True to replace an existing data field with the imported ones if
     *                          the field names match
     *********************************************************************************************/
    private void addImportedDataField(TableDefinition tableDefn,
                                      String ownerName,
                                      boolean useExistingFields)
    {
        // Grab the current data fields
        List<String[]> currentFieldInfo = CcddFieldHandler.getFieldDefnsAsListOfStrings(fieldHandler.getFieldInformationByOwner(ownerName));

        // Grab the new data fields
        List<String[]> newFieldInfo = tableDefn.getDataFields();

        boolean fieldExists;
        int atIndex;

        // Step through the imported data fields. The order is reversed so that field definitions
        // can be removed if needed
        for (int index = 0; index < newFieldInfo.size(); index++)
        {
            fieldExists = false;
            atIndex = -1;

            // Set the data field owner to the specified table (if importing entire tables this
            // isn't necessary, but is when importing into an existing table since the owner in the
            // import file may differ)
            newFieldInfo.get(index)[FieldsColumn.OWNER_NAME.ordinal()] = ownerName;

            // Check to see if the field already exists
            for (int index2 = 0; index2 < currentFieldInfo.size(); index2++)
            {
                if (currentFieldInfo.get(index2)[FieldsColumn.FIELD_NAME.ordinal()]
                                    .contentEquals(newFieldInfo.get(index)[FieldsColumn.FIELD_NAME.ordinal()]))
                {
                    if (currentFieldInfo.get(index2)[FieldsColumn.FIELD_TYPE.ordinal()]
                                        .contentEquals(newFieldInfo.get(index)[FieldsColumn.FIELD_TYPE.ordinal()]))
                    {
                        // If the field does exists then set the flag and save the index
                        fieldExists = true;
                        atIndex = index2;
                        break;
                    }
                }
            }

            // Does the field exist?
            if (fieldExists)
            {
                // Did the user decide to use existing fields?
                if (useExistingFields)
                {
                    // Remove the new data field definition and replace it with the existing
                    // definition
                    newFieldInfo.remove(index);
                    newFieldInfo.add(index, currentFieldInfo.get(atIndex));
                }

                // Remove the field from the list of current fields
                currentFieldInfo.remove(atIndex);
            }
        }

        // If there are any values left in the currentFieldInfo list they will need to be added
        for (int index = 0; index < currentFieldInfo.size(); index++)
        {
            tableDefn.getDataFields().add(currentFieldInfo.get(index));
        }
    }

    /**********************************************************************************************
     * Export the contents of one or more tables selected by the user to one or more files in the
     * specified format. The export file names are based on the table name if each table is stored
     * in a separate file. The user supplied file name is used if multiple tables are stored in a
     * single file. This method is executed in a separate thread since it can take a noticeable
     * amount time to complete, and by using a separate thread the GUI is allowed to continue to
     * update. The GUI menu commands, however, are disabled until the database method completes
     * execution
     *
     * @param filePath                Path to the folder in which to store the exported tables.
     *                                Includes the name if storing the tables to a single file
     *
     * @param tablePaths              Table path for each table to load
     *
     * @param overwriteFile           True to store overwrite an existing file; false skip
     *                                exporting a table to a file that already exists
     *
     * @param singleFile              True to store multiple tables in a single file; false to
     *                                store each table in a separate file
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param replaceMacros           True to replace macros with their corresponding values; false
     *                                to leave the macros intact
     *
     * @param deleteTargetDirectory   True to delete the target directory contents
     *
     * @param includeAllTableTypes    True to include the all table type definitions in the export
     *                                file
     *
     * @param includeAllDataTypes     True to include the all data type definitions in the export
     *                                file
     *
     * @param includeAllInputTypes    True to include the all user-defined input type definitions
     *                                in the export file
     *
     * @param includeAllMacros        True to include the all macro definitions in the export file
     *
     * @param includeReservedMsgIDs   True to include the contents of the reserved message ID table
     *                                in the export file
     *
     * @param includeProjectFields    True to include the project-level data field definitions in
     *                                the export file
     *
     * @param includeGroups           True to include the groups and group data field definitions
     *                                in the export file
     *
     * @param includeAssociations     True to include the script associations in the export file
     *
     * @param includeTlmSched         True to include the telemetry scheduler entries in the export
     *                                file
     *
     * @param includeAppSched         True to include the application scheduler entries in the
     *                                export file
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
     * @param fileExtn                File extension type
     *
     * @param endianess               EndianType.BIG_ENDIAN for big endian,
     *                                EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian       True if the telemetry and command headers are always big
     *                                endian (e.g., as with CCSDS)
     *
     * @param version                 Version attribute (XTCE only)
     *
     * @param validationStatus        Validation status attribute (XTCE only)
     *
     * @param classification1         First level classification attribute (XTCE only)
     *
     * @param classification2         Second level classification attribute (XTCE only)
     *
     * @param classification3         Third level classification attribute (XTCE only)
     *
     * @param useExternal             True to use external (script) methods in place of the
     *                                internal ones (XTCE only)
     *
     * @param scriptFileName          Name of the script file containing the external (script)
     *                                methods (XTCE only); ignored if useExternal is false
     *
     * @param parent                  GUI component over which to center any error dialog
     *
     * @return true if an error occurred while exporting
     *********************************************************************************************/
    protected boolean exportSelectedTablesInBackground(final String filePath,
                                                       final String[] tablePaths,
                                                       final boolean overwriteFile,
                                                       final boolean singleFile,
                                                       final boolean includeBuildInformation,
                                                       final boolean replaceMacros,
                                                       final boolean deleteTargetDirectory,
                                                       final boolean includeAllTableTypes,
                                                       final boolean includeAllDataTypes,
                                                       final boolean includeAllInputTypes,
                                                       final boolean includeAllMacros,
                                                       final boolean includeReservedMsgIDs,
                                                       final boolean includeProjectFields,
                                                       final boolean includeGroups,
                                                       final boolean includeAssociations,
                                                       final boolean includeTlmSched,
                                                       final boolean includeAppSched,
                                                       final boolean includeVariablePaths,
                                                       final CcddVariableHandler variableHandler,
                                                       final String[] separators,
                                                       final FileExtension fileExtn,
                                                       final EndianType endianess,
                                                       final boolean isHeaderBigEndian,
                                                       final String version,
                                                       final String validationStatus,
                                                       final String classification1,
                                                       final String classification2,
                                                       final String classification3,
                                                       final boolean useExternal,
                                                       final String scriptFileName,
                                                       final Component parent)
    {
        // Execute the export operation in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, parent, new BackgroundCommand()
        {
            /**************************************************************************************
             * Export the selected table(s)
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Export the selected table(s)
                errorFlag = exportSelectedTables(filePath,
                                                 tablePaths,
                                                 overwriteFile,
                                                 singleFile,
                                                 includeBuildInformation,
                                                 replaceMacros,
                                                 deleteTargetDirectory,
                                                 includeAllTableTypes,
                                                 includeAllDataTypes,
                                                 includeAllInputTypes,
                                                 includeAllMacros,
                                                 includeReservedMsgIDs,
                                                 includeProjectFields,
                                                 includeGroups,
                                                 includeAssociations,
                                                 includeTlmSched,
                                                 includeAppSched,
                                                 includeVariablePaths,
                                                 variableHandler,
                                                 separators,
                                                 fileExtn,
                                                 endianess,
                                                 isHeaderBigEndian,
                                                 version,
                                                 validationStatus,
                                                 classification1,
                                                 classification2,
                                                 classification3,
                                                 useExternal,
                                                 scriptFileName,
                                                 parent);
            }

            /**************************************************************************************
             * Perform export selected table(s) complete steps
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if the export was executed from the command line
                if (!(parent instanceof CcddDialogHandler))
                {
                    // Restore the table export path to what it was at program start-up
                    ccddMain.restoreTableExportPath();
                }
            }
        });

        return errorFlag;
    }

    /**********************************************************************************************
     * Export the contents of one or more tables selected by the user to one or more files in the
     * specified format. The export file names are based on the table name if each table is stored
     * in a separate file. The user supplied file name is used if multiple tables are stored in a
     * single file
     *
     * @param originalFilePath        Original file path
     *
     * @param tablePaths              Table path for each table to load
     *
     * @param overwriteFile           True to store overwrite an existing file; false skip
     *                                exporting a table to a file that already exists
     *
     * @param singleFile              True to store multiple tables in a single file; false to
     *                                store each table in a separate file
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param replaceMacros           True to replace macros with their corresponding values; false
     *                                to leave the macros intact
     *
     * @param deleteTargetDirectory   True to delete the target directory contents
     *
     * @param includeAllTableTypes    True to include the all table type definitions in the export
     *                                file
     *
     * @param includeAllDataTypes     True to include the all data type definitions in the export
     *                                file
     *
     * @param includeAllInputTypes    True to include the all user-defined input type definitions
     *                                in the export file
     *
     * @param includeAllMacros        True to include the all macro definitions in the export file
     *
     * @param includeReservedMsgIDs   True to include the contents of the reserved message ID table
     *                                in the export file
     *
     * @param includeProjectFields    True to include the project-level data field definitions in
     *                                the export file
     *
     * @param includeGroups           True to include the groups and group data field definitions
     *                                in the export file
     *
     * @param includeAssociations     True to include the script associations in the export file
     *
     * @param includeTlmSched         True to include the telemetry scheduler entries in the export
     *                                file
     *
     * @param includeAppSched         True to include the application scheduler entries in the
     *                                export file
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
     * @param fileExtn                File extension type
     *
     * @param endianess               EndianType.BIG_ENDIAN for big endian,
     *                                EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian       True if the telemetry and command headers are always big
     *                                endian (e.g., as with CCSDS)
     *
     * @param version                 Version attribute (XTCE only)
     *
     * @param validationStatus        Validation status attribute (XTCE only)
     *
     * @param classification1         First level classification attribute (XTCE only)
     *
     * @param classification2         Second level classification attribute (XTCE only)
     *
     * @param classification3         Third level classification attribute (XTCE only)
     *
     * @param useExternal             True to use external (script) methods in place of the
     *                                internal ones (XTCE only)
     *
     * @param scriptFileName          Name of the script file containing the external (script)
     *                                methods (XTCE only); ignored if useExternal is false
     *
     * @param parent                  GUI component over which to center any error dialog
     *
     * @return true if the export completes successfully
     *********************************************************************************************/
    protected boolean exportSelectedTables(final String originalFilePath,
                                           final String[] tablePaths,
                                           final boolean overwriteFile,
                                           final boolean singleFile,
                                           final boolean includeBuildInformation,
                                           final boolean replaceMacros,
                                           final boolean deleteTargetDirectory,
                                           final boolean includeAllTableTypes,
                                           final boolean includeAllDataTypes,
                                           final boolean includeAllInputTypes,
                                           final boolean includeAllMacros,
                                           final boolean includeReservedMsgIDs,
                                           final boolean includeProjectFields,
                                           final boolean includeGroups,
                                           final boolean includeAssociations,
                                           final boolean includeTlmSched,
                                           final boolean includeAppSched,
                                           final boolean includeVariablePaths,
                                           final CcddVariableHandler variableHandler,
                                           final String[] separators,
                                           final FileExtension fileExtn,
                                           final EndianType endianess,
                                           final boolean isHeaderBigEndian,
                                           final String version,
                                           final String validationStatus,
                                           final String classification1,
                                           final String classification2,
                                           final String classification3,
                                           final boolean useExternal,
                                           final String scriptFileName,
                                           final Component parent)
    {
        // Initialize local variables
        boolean errorFlag = false;
        FileEnvVar file = null;
        CcddImportExportInterface ioHandler = null;
        List<String> skippedTables = new ArrayList<String>();
        ScriptEngine scriptEngine = null;
        boolean addEOFMarker = true;
        boolean addSOFMarker = true;

        // Are we writing to a single file or multiple files?
        String outputType = "";

        if (singleFile)
        {
            outputType = EXPORT_SINGLE_FILE;
        }
        else
        {
            outputType = EXPORT_MULTIPLE_FILES;
        }

        // Converting the file path to a FileEnvVar object will expand any environment variables
        // within the path
        FileEnvVar modifiedFilePath = new FileEnvVar(originalFilePath);
        String filePath = modifiedFilePath.getAbsolutePath();

        // Remove the trailing period if present
        String path = CcddUtilities.removeTrailer(filePath, ".");

        // Check if external (script) methods are to be used
        if (useExternal)
        {
            try
            {
                // The table information isn't provided to the script data access handler when
                // creating the script engine below. Therefore, not all script data access methods
                // are available (i.e., those that refer to the table names, rows, data, etc.).
                // Barring an extensive rewrite of the export methods, in order to provide the
                // information the tables would have to be read twice, once to create the script
                // handler format (where tables of the same type are combined) and again in the
                // XTCE handler. Performing the export operation via a script association does
                // allow access to all of the methods (and entails loading each table twice). The
                // link and group handlers aren't provided in the script engine call below either,
                // but these are loaded if an access method requiring them is called

                // Get the script engine for the supplied script file name
                scriptEngine = ccddMain.getScriptHandler().getScriptEngine(scriptFileName,
                                                                           new TableInfo[0],
                                                                           null,
                                                                           null,
                                                                           null, parent);
            }
            catch (CCDDException ce)
            {
                // Inform the user that an error occurred accessing the script file
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Cannot use external methods - using "
                                                          + "internal methods instead; cause '</b>"
                                                          + ce.getMessage()
                                                          + "<b>'",
                                                          "Export Error",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }

        try
        {
            // Check if the user elected to store all tables in a single file. The path must
            // include a file name
            if (singleFile)
            {
                // Check if the file name doesn't end with the expected extension
                if (!path.endsWith(fileExtn.getExtension()))
                {
                    // Append the extension to the file name
                    path += fileExtn.getExtension();
                }

                // Create the file using the supplied name
                file = new FileEnvVar(path);
            }
            // The table(s) are to be stored in individual files, so the path doesn't include a
            // file name. Check if the path doesn't terminate with a name separator character
            else if (!path.endsWith(File.separator))
            {
                // Append the name separator character to the path
                path += File.separator;
            }

            // Create a handler based on the file extension
            if (fileExtn == FileExtension.CSV)
            {
                ioHandler = new CcddCSVHandler(ccddMain,
                                               new CcddGroupHandler(ccddMain, null, parent),
                                               parent);
            }
            else if (fileExtn == FileExtension.EDS)
            {
                ioHandler = new CcddEDSHandler(ccddMain, parent);
            }
            else if (fileExtn == FileExtension.JSON)
            {
                ioHandler = new CcddJSONHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain, null, parent),
                                                parent);
            }
            else if (fileExtn == FileExtension.XTCE)
            {
                ioHandler = new CcddXTCEHandler(ccddMain, scriptEngine, parent);
            }

            // Delete the contents of the directory
            if (deleteTargetDirectory)
            {
                CleanExportDirectory(fileExtn, filePath, singleFile, parent);
            }

            if (tablePaths.length != 0)
            {
                // Check if the tables are to be exported to a single file or multiple files
                if (singleFile)
                {
                    // Exporting all tables to a single file. Check if the file already exists, if
                    // not one will be created. If it does then did the user elect to overwrite it?
                    if (isOverwriteExportFileIfExists(file, overwriteFile, parent))
                    {
                        // If more data is going to be added later the do not add a brace at the
                        // end of the file (Only applies to JSON)
                        if (includeAllTableTypes
                            || includeAllDataTypes
                            || includeAllInputTypes
                            || includeAllMacros
                            || includeReservedMsgIDs
                            || includeProjectFields
                            || includeGroups
                            || includeAssociations
                            || includeTlmSched
                            || includeAppSched
                            || includeVariablePaths)
                        {
                            addEOFMarker = false;
                        }

                        List<TableInfo> tableDefs = null;

                        if ((fileExtn == FileExtension.JSON) || (fileExtn == FileExtension.CSV))
                        {
                            // Export the formatted JSON or EDS table data to the specified file
                            tableDefs = prepareJSONOrCSVExport(tablePaths,
                                                               includeVariablePaths,
                                                               variableHandler,
                                                               separators,
                                                               parent);
                        }
                        else
                        {
                            tableDefs = new ArrayList<TableInfo>(tablePaths.length);

                            for (String tablePath : tablePaths)
                            {
                                tableDefs.add(new TableInfo(tablePath));
                            }
                        }

                        // Export the files
                        ioHandler.exportTables(file,
                                               tableDefs,
                                               includeBuildInformation,
                                               replaceMacros,
                                               includeVariablePaths,
                                               variableHandler,
                                               separators,
                                               addEOFMarker,
                                               outputType,
                                               endianess,
                                               isHeaderBigEndian,
                                               version,
                                               validationStatus,
                                               classification1,
                                               classification2,
                                               classification3);

                        // Check if the file is empty following the export. This occurs if an error
                        // halts output to the file
                        if (file.length() == 0)
                        {
                            // Delete the empty file
                            file.delete();
                        }
                    }
                    else
                    {
                        // Add the skipped table to the list
                        skippedTables.addAll(Arrays.asList(tablePaths));
                    }
                }
                else
                {
                    List<TableInfo> tableDefs = null;

                    if ((fileExtn == FileExtension.JSON) || (fileExtn == FileExtension.CSV))
                    {
                        // Export the formatted tables
                        tableDefs = prepareJSONOrCSVExport(tablePaths,
                                                           includeVariablePaths,
                                                           variableHandler,
                                                           separators,
                                                           parent);
                    }
                    else
                    {
                        tableDefs = new ArrayList<TableInfo>(tablePaths.length);

                        for (String tablePath : tablePaths)
                        {
                            tableDefs.add(new TableInfo(tablePath));
                        }
                    }

                    // Export each table to its own file
                    for (TableInfo tableDef : tableDefs)
                    {
                        // Create the file using a name derived from the table name
                        file = new FileEnvVar(path
                                              + tableDef.getTablePath().replaceAll("[,\\.\\[\\]]", "_")
                                              + fileExtn.getExtension());

                        // Check if a file exists for this table. If it doesn't one will be
                        // created. If it does did the user elect to overwrite it?
                        if (isOverwriteExportFileIfExists(file, overwriteFile, parent))
                        {
                            List<TableInfo> singleDef = new ArrayList<TableInfo>(1);
                            singleDef.add(tableDef);

                            // Export the formatted table data. The file name is derived from the
                            // table name
                            ioHandler.exportTables(file,
                                                   singleDef,
                                                   includeBuildInformation,
                                                   replaceMacros,
                                                   includeVariablePaths,
                                                   variableHandler,
                                                   separators,
                                                   true,
                                                   outputType,
                                                   version,
                                                   validationStatus,
                                                   classification1,
                                                   classification2,
                                                   classification3);

                            // Check if the file is empty following the export. This occurs if an
                            // error halts output to the file
                            if (file.length() == 0)
                            {
                                // Delete the empty file
                                file.delete();
                            }
                        }
                        // The table is skipped
                        else
                        {
                            // Add the skipped table to the list
                            skippedTables.add(tableDef.getTablePath());
                        }
                    }
                }
            }

            // All of the exports below apply only to the JSON/CSV export
            if ((fileExtn == FileExtension.JSON) || (fileExtn == FileExtension.CSV))
            {
                // Get directory where all the individual files will be exported or the individual
                // file that all data will be exported to
                FileEnvVar exportDirectoryOrFile = new FileEnvVar(filePath);

                // Export table info if needed
                if (includeAllTableTypes || includeAllInputTypes || includeAllDataTypes)
                {
                    // If more data is going to be added later then do not add a brace at the end
                    // of the file (Only applies to JSON)
                    if (includeGroups
                        || includeAllMacros
                        || includeAssociations
                        || includeTlmSched
                        || includeAppSched
                        || includeReservedMsgIDs
                        || includeProjectFields)
                    {
                        addEOFMarker = false;
                    }
                    else
                    {
                        addEOFMarker = true;
                    }

                    // If data already exists then do not add a brace at the start of the file
                    // (Only applies to JSON)
                    if (tablePaths.length == 0 || outputType.contentEquals(EXPORT_MULTIPLE_FILES))
                    {
                        addSOFMarker = true;
                    }
                    else
                    {
                        addSOFMarker = false;
                    }

                    ioHandler.exportTableInfoDefinitions(exportDirectoryOrFile,
                                                         includeAllTableTypes,
                                                         includeAllInputTypes,
                                                         includeAllDataTypes,
                                                         outputType,
                                                         addEOFMarker,
                                                         addSOFMarker);
                }

                boolean[] includes = {includeGroups,
                                      includeAllMacros,
                                      includeAssociations,
                                      includeTlmSched,
                                      includeAppSched,
                                      includeReservedMsgIDs,
                                      includeProjectFields,
                                      true};
                exportDataTypes[] dataTypes = {exportDataTypes.GROUPS,
                                               exportDataTypes.MACROS,
                                               exportDataTypes.ASSOCIATIONS,
                                               exportDataTypes.TELEMSCHEDULER,
                                               exportDataTypes.APPSCHEDULER,
                                               exportDataTypes.RESERVED_MSG_ID,
                                               exportDataTypes.PROJECT_FIELDS,
                                               exportDataTypes.DBU_INFO};

                ioHandler.exportInternalCCDDData(includes,
                                                 dataTypes,
                                                 exportDirectoryOrFile,
                                                 outputType);
            }

            // Check if any tables were skipped
            if (!skippedTables.isEmpty())
            {
                // Inform the user that one or more tables were not exported
                new CcddDialogHandler().showMessageDialog(parent,
                                                          "<html><b>Table(s) not exported '</b>"
                                                          + CcddUtilities.convertArrayToStringTruncate(skippedTables.toArray(new String[0]))
                                                          + "<b>';<br>output file already exists or file I/O error",
                                                          "Export Error",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }

            // Check if no errors occurred exporting the table(s)
            if (!errorFlag)
            {
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  new StringBuilder("Data export completed successfully"));
            }
            // An error occurred while exporting the table(s)
            else
            {
                eventLog.logFailEvent(parent,
                                      "Export Error",
                                      "Data export completed with errors",
                                      "<html><b>Data export completed with errors");
            }
        }
        catch (JAXBException | CCDDException jce)
        {
            // Inform the user that the export operation failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot export to file '</b>"
                                                      + (file != null ? file.getAbsolutePath()
                                                                      : filePath)
                                                      + "<b>': "
                                                      + jce.getMessage(),
                                                      "Export Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Check if the specified data file exists and isn't empty, and if so, whether or not the user
     * elects to overwrite it
     *
     * @param exportFile    Reference to the file
     *
     * @param overwriteFile True to overwrite an existing file
     *
     * @param parent        GUI component over which to center any error dialog
     *
     * @return true if the file doesn't exist, or if it does exist and the user elects to overwrite
     *         it; false if the file exists and the user elects not to overwrite it, or if an error
     *         occurs deleting the existing file or creating a new one
     *********************************************************************************************/
    private boolean isOverwriteExportFileIfExists(FileEnvVar exportFile,
                                                  boolean overwriteFile,
                                                  Component parent)
    {
        // Set the continue flag based on if the file exists and isn't empty
        boolean continueExport = !(exportFile.exists() && exportFile.length() != 0);

        try
        {
            // Check if the data file exists and isn't empty
            if (!continueExport)
            {
                // Check if the user elects to overwrite existing files
                if (overwriteFile
                    || new CcddDialogHandler().showMessageDialog(parent,
                                                                 "<html><b>Overwrite existing file '</b>\n"
                                                                 + exportFile.getAbsolutePath()
                                                                 + "<b>'?",
                                                                 "Overwrite File",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
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
        }
        catch (CCDDException ce)
        {
            // Inform the user that the data file cannot be created
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>"
                                                      + ce.getMessage()
                                                      + " export file '</b>"
                                                      + exportFile.getAbsolutePath()
                                                      + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be written to
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot write to export file '</b>"
                                                      + exportFile.getAbsolutePath()
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return continueExport;
    }

    /**********************************************************************************************
     * Store the contents of the specified script file into the database
     *
     * @param file Reference to the file to store
     *********************************************************************************************/
    protected void storeScriptInDatabase(FileEnvVar file)
    {
        BufferedReader br = null;

        try
        {
            List<String[]> scriptData = new ArrayList<String[]>();

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
                    // Get the index of the description tag (-1 if not found). Force to lower case
                    // to remove case sensitivity
                    int index = line.toLowerCase().indexOf(SCRIPT_DESCRIPTION_TAG);

                    // Check if the description tag is found
                    if (index != -1)
                    {
                        // Extract the description from the file line
                        description = line.substring(index + SCRIPT_DESCRIPTION_TAG.length()).trim();
                    }
                }

                // Add the line from the script file
                scriptData.add(new String[] {line});

                // Read the next line in the file
                line = br.readLine();
            }

            // Store the script file in the project's database
            dbTable.storeInformationTableInBackground(InternalTable.SCRIPT,
                                                      scriptData,
                                                      file.getName()
                                                      + ","
                                                      + description,
                                                      ccddMain.getMainFrame());
        }
        catch (IOException ioe)
        {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot read script file '</b>"
                                                      + file.getAbsolutePath()
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
                                                          "<html><b>Cannot close script file '</b>"
                                                          + file.getAbsolutePath()
                                                          + "<b>'",
                                                          "File Warning",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Retrieve the contents of the specified script from the database and save it to a file
     *
     * @param script Name of the script to retrieve
     *
     * @param file   Reference to the script file
     *********************************************************************************************/
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
                                                              "<html><b>Overwrite existing script file '</b>"
                                                              + file.getAbsolutePath()
                                                              + "<b>'?",
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
                    // Output the line contents
                    pw.println(line[ScriptColumn.LINE_TEXT.ordinal()]);
                }

                // Inform the user that the script retrieval succeeded
                eventLog.logEvent(SUCCESS_MSG, new StringBuilder(script).append(" retrieved"));
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the script file cannot be created
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>" + ce.getMessage()
                                                      + " script file '</b>"
                                                      + file.getAbsolutePath() + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (IOException ioe)
        {
            // Inform the user that the script file cannot be written to
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot write to script file '</b>"
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

    /**********************************************************************************************
     * Store the specified file path in the program preferences backing store
     *
     * @param ccddMain    Main class reference
     *
     * @param pathName    File path
     *
     * @param hasFileName True if the file path includes a file name. The file name is removed
     *                    before storing the path
     *
     * @param modPath     ModifiablePathInfo reference for the path being updated
     ********************************************************************************************/
    protected static void storePath(CcddMain ccddMain,
                                    String pathName,
                                    boolean hasFileName,
                                    ModifiablePathInfo modPath)
    {
        // Check if the file path includes a file name
        if (hasFileName && pathName.contains(File.separator))
        {
            // Strip the file name from the path
            pathName = pathName.substring(0, pathName.lastIndexOf(File.separator));
        }
        // Check if the path name ends with a period
        if (pathName.endsWith("."))
        {
            // Remove file separator (if present) and the period at the end of the path
            pathName = pathName.replaceFirst(File.separator + "?\\.", "");
        }

        // Store the file path
        modPath.setPath(ccddMain, pathName);
    }

    /**********************************************************************************************
     * Write the specified string list to the selected file
     *
     * @param list   List containing the strings to write to the selected file
     *
     * @param parent GUI component over which to center the dialog
     ********************************************************************************************/
    protected void writeListToFile(List<String> list, Component parent)
    {
        // Create the file choice dialog
        final CcddDialogHandler dlg = new CcddDialogHandler();


        // Allow the user to select the backup file path + name
        FileEnvVar[] dataFile = dlg.choosePathFile(ccddMain,
                                                   parent,
                                                   "",
                                                   null,
                                                   null,
                                                   false,
                                                   false,
                                                   "Save to File",
                                                   ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(), null),
                                                   DialogOption.STORE_OPTION,
                                                   null);

        // File name with the white space stripped
        String compliantFileName = dlg.getFileNameField().getText().replaceAll("\\s", "");

        FileEnvVar chosenFilePath = CcddBackupName.reconstructBackupFilePath(dataFile, compliantFileName);

        // Check if a file was chosen
        if (chosenFilePath != null)
        {
            boolean cancelBackup = false;

            // Check if the backup file exists
            if (chosenFilePath.exists())
            {
                // Check if the existing file should be overwritten
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Overwrite existing file?",
                                                              "Overwrite File",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Check if the file can be deleted
                    if (!chosenFilePath.delete())
                    {
                        // Inform the user that the existing file cannot be replaced
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Cannot replace existing file '</b>"
                                                                  + chosenFilePath.getAbsolutePath()
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
                    // Cancel saving the file
                    cancelBackup = true;
                }
            }

            // Check that no errors occurred and that the user didn't cancel saving the file
            if (!cancelBackup)
            {
                // Open the selected file
                PrintWriter printWriter =  openOutputFile(chosenFilePath.getAbsolutePath());

                // Write the list contents to the file
                for (String line : list)
                {
                    writeToFileLn(printWriter, line);
                }

                // Close the file
                closeFile(printWriter);
            }
        }
    }

    /**********************************************************************************************
     * Open the specified file for writing
     *
     * @param outputFileName Output file path + name
     *
     * @return PrintWriter object; null if the file could not be opened
     *********************************************************************************************/
    public PrintWriter openOutputFile(String outputFileName)
    {
        PrintWriter printWriter = null;

        try
        {
            // Create the file object
            FileEnvVar outputFile = new FileEnvVar(outputFileName);

            // Check if the file already exists, and if so that it is successfully deleted
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
                                                      + " output file '</b>"
                                                      + outputFileName + "<b>'",
                                                      "File Error",
                                                      ce.getMessageType(),
                                                      DialogOption.OK_OPTION);
        }
        catch (Exception e)
        {
            // Inform the user that the output file cannot be opened
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot open output file '</b>"
                                                      + outputFileName
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return printWriter;
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     *
     * @param printWriter Output file PrintWriter object
     *
     * @param text        Text to write to the output file
     *********************************************************************************************/
    public void writeToFile(PrintWriter printWriter, String text)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file
            printWriter.print(text);
        }
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object and append a line
     * feed character
     *
     * @param printWriter Output file PrintWriter object
     *
     * @param text        Text to write to the output file
     *********************************************************************************************/
    public void writeToFileLn(PrintWriter printWriter, String text)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file, followed by a line feed
            printWriter.println(text);
        }
    }

    /**********************************************************************************************
     * Write the supplied text in the indicated format to the specified output file PrintWriter
     * object
     *
     * @param printWriter Output file PrintWriter object
     *
     * @param format      Print format
     *
     * @param args        Arguments referenced by the format specifiers in the format string
     *********************************************************************************************/
    public void writeToFileFormat(PrintWriter printWriter, String format, Object... args)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Output the text to the file, followed by a line feed
            printWriter.printf(format, args);
        }
    }

    /**********************************************************************************************
     * Close the specified output file
     *
     * @param printWriter Output file PrintWriter object
     *********************************************************************************************/
    public void closeFile(PrintWriter printWriter)
    {
        // Check if the PrintWriter object exists
        if (printWriter != null)
        {
            // Close the file
            printWriter.close();
        }
    }

    /**********************************************************************************************
     * Create the snapshot directory which can be used as a temporary export location for file
     * comparisons
     *
     * @param tablePaths           Table path for each table to load
     *
     * @param exportEntireDatabase true if exporting the entire database
     *
     * @param importFileType       Import file type
     *
     * @param singleFile           true if importing to a single file
     *
     * @param parent               GUI component over which to center any error dialog
     *
     * @return List of files in snapshot directory
     *********************************************************************************************/
    public List<File> compareToSnapshotDirectory(String[] tablePaths,
                                                 boolean exportEntireDatabase,
                                                 FileExtension importFileType,
                                                 boolean singleFile,
                                                 Component parent)
    {
        try
        {
            // Check if the .snapshot directory exists
            if (!Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH)))
            {
                // Create the .snapshot directory
                Files.createDirectory(Paths.get(SNAP_SHOT_FILE_PATH));
            }
            // If the .snapshot directory exists, delete its contents
            else
            {
                File directory = new File(SNAP_SHOT_FILE_PATH);
                FileUtils.cleanDirectory(directory);
            }
        }
        catch (IOException ioe)
        {
            // Inform the user that the .snapshot directory cannot be created/emptied
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot create/empty temporary directory '</b>"
                                                      + SNAP_SHOT_FILE_PATH
                                                      + "<b>'; cause '</b>"
                                                      + ioe.getMessage()
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);

            errorFlag = true;
        }

        // Backup current database state to .snapshot directory before import
        exportSelectedTables(SNAP_SHOT_FILE_PATH, // filePath
                             tablePaths, // tablePaths
                             true, // overwriteFile
                             false, // singleFile
                             false, // includeBuildInformation
                             false, // replaceMacros
                             false, // deleteTargetDirectory
                             exportEntireDatabase, // includeAllTableTypes
                             exportEntireDatabase, // includeAllDataTypes
                             exportEntireDatabase, // includeAllInputTypes
                             exportEntireDatabase, // includeAllMacros
                             true, // includeReservedMsgIDs
                             true, // includeProjectFields
                             exportEntireDatabase, // includeGroups
                             exportEntireDatabase, // includeAssociations
                             exportEntireDatabase, // includeTlmSched
                             exportEntireDatabase, // includeAppSched
                             false, // includeVariablePaths
                             ccddMain.getVariableHandler(), // variableHandler
                             null, // separators
                             importFileType, // fileExtn
                             null, // endianess
                             false, // isHeaderBigEndian
                             null, // version
                             null, // validationStatus
                             null, // classification1
                             null, // classification2
                             null, // classification3
                             false, // useExternal
                             null, // scriptFileName
                             null); // parent

        return new ArrayList<>(Arrays.asList(new File(SNAP_SHOT_FILE_PATH).listFiles()));
    }

    /**********************************************************************************************
     * Create the snapshot2 directory which can be used as a temporary export location for files
     * that are spawned when a single file that represents an entire database is imported.
     *
     * @param parent CCDD component that called this function
     *********************************************************************************************/
    public void createSnapshotDirectory(Component parent)
    {
        try
        {
            // Check if the .snapshot2 directory exists
            if (!Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH_2)))
            {
                // Create the .snapshot2 directory
                Files.createDirectory(Paths.get(SNAP_SHOT_FILE_PATH_2));
            }
            // If the .snapshot2 directory exists, delete its contents
            else
            {
                File directory = new File(SNAP_SHOT_FILE_PATH_2);
                FileUtils.cleanDirectory(directory);
            }
        }
        catch (IOException ioe)
        {
            // Inform the user that the .snapshot directory cannot be created/emptied
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot create/empty temporary directory '</b>"
                                                      + SNAP_SHOT_FILE_PATH_2
                                                      + "<b>'; cause '</b>"
                                                      + ioe.getMessage()
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);

            errorFlag = true;
        }
    }

    /**********************************************************************************************
     * Check if the snapshot directories exist and if so delete them
     *
     * @param parent CCDD component that called this function
     *********************************************************************************************/
    public void deleteSnapshotDirectories(Component parent)
    {
        // If the .snapshot directories exists, delete them
        try
        {
            File directory = new File(SNAP_SHOT_FILE_PATH);
            FileUtils.cleanDirectory(directory);
            directory.delete();

            directory = new File(SNAP_SHOT_FILE_PATH_2);
            FileUtils.cleanDirectory(directory);
            directory.delete();
        }
        catch (IOException ioe)
        {
            // Inform the user that the .snapshot directory cannot be created/emptied
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot create/empty temporary directories '</b>"
                                                      + SNAP_SHOT_FILE_PATH
                                                      + "<b>' and '</b>"
                                                      + SNAP_SHOT_FILE_PATH_2
                                                      + "<b>'; cause '</b>"
                                                      + ioe.getMessage()
                                                      + "<b>'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);

            errorFlag = true;
        }
    }

    /**********************************************************************************************
     * Process the single file, which represents an entire database, that is being imported and
     * output the data to multiple files.
     *
     * @param dataFile   Path to the file being imported
     *
     * @param dialogType What format is the file? JSON/CSV
     *
     * @param parent     Component that called this function
     *
     * @return FileEnvVar[] that represents all of the files created from the data found in the
     *         JSON file.
     *********************************************************************************************/
    public FileEnvVar[] processSingleJSONFileRepresentingDatabase(FileEnvVar dataFile,
                                                                  ManagerDialogType dialogType,
                                                                  final Component parent)
    {
        // Create a list to hold all the files
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();

        // Detect the type of the parsed JSON file and only accept JSONObjects. This will throw an
        // exception if it is incorrect
        try
        {
            FileEnvVar file;
            String filePath = "";
            String nameType = "";
            String tableName = "";
            String outputData = "";

            // Read the file
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));

            /*************** INPUT TYPES, TABLE TYPES AND DATA TYPES ***************/
            String tagData = jsonHandler.retrieveJSONData(JSONTags.INPUT_TYPE_DEFN.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = tagData + "\n]";
            }

            tagData = jsonHandler.retrieveJSONData(JSONTags.TABLE_TYPE_DEFN.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += ",\n";
                }

                outputData += tagData + "\n]";
            }

            tagData = jsonHandler.retrieveJSONData(JSONTags.DATA_TYPE_DEFN.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += ",\n";
                }

                outputData += tagData + "\n]";
            }

            if (!outputData.isEmpty())
            {
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.TABLE_INFO.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile("{\n  " + outputData + "\n}\n", filePath);
            }

            /*************** MACROS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.MACRO_DEFN.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.MACROS.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** GROUPS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.GROUP.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.GROUPS.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** SCRIPT ASSOCIATIONS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.SCRIPT_ASSOCIATION.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.SCRIPT_ASSOCIATION.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** TLM SCHEDULER ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.TLM_SCHEDULER_COMMENT.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.TELEM_SCHEDULER.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** APP SCHEDULER ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.APP_SCHEDULER_COMMENT.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.APP_SCHEDULER.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** RESERVED MESSAGE IDS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.RESERVED_MSG_ID_DEFN.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.RESERVED_MSG_ID.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** PROJECT FIELDS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.PROJECT_FIELD.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.PROJECT_DATA_FIELD.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** DATABASE INFORMATION ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.DBU_INFO.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = "{\n  " + tagData + "]\n}\n";
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.DBU_INFO.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** TABLE DEFINITIONS ***************/
            tagData = jsonHandler.retrieveJSONData(JSONTags.TABLE_DEFN.getTag(), content).toString();

            String[] data = tagData.split("]\n    },\n");

            for (int i = 0; i < data.length; i++)
            {
                nameType = data[i].split(",\n")[0];

                if (i == 0)
                {
                    // Check if this line contains the file description. Will change how data is
                    // parsed
                    if (nameType.contains(JSONTags.FILE_DESCRIPTION.getTag()))
                    {
                        nameType = data[i].split(",\n")[1];

                        // Grab the table name
                        tableName = nameType.split(": ")[1].replace("\"", "");
                    }
                    else
                    {
                        tableName = nameType.split(": ")[2].replace("\"", "");
                    }
                }
                else
                {
                    tableName = nameType.split(": ")[1].replace("\"", "");
                }

                tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");
                filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".json";
                file = new FileEnvVar(filePath);
                dataFiles.add(file);

                if (i == 0)
                {
                    if (i == data.length - 1)
                    {
                        writeToJSONFile("{\n  " + data[i] + "\n  ]\n}\n", filePath);
                    }
                    else
                    {
                        writeToJSONFile("{\n  " + data[i] + "]\n    }\n  ]\n}\n", filePath);
                    }
                }
                else if (i == data.length - 1)
                {
                    writeToJSONFile("{\n  \""
                                + JSONTags.TABLE_DEFN.getTag()
                                + "\": [\n"
                                + data[i]
                                + "  ]\n}\n",
                                filePath);
                }
                else
                {
                    writeToJSONFile("{\n  \""
                                + JSONTags.TABLE_DEFN.getTag()
                                + "\": [\n"
                                + data[i]
                                + "]\n    }\n  ]\n}\n",
                                filePath);
                }
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        return dataFiles.toArray(new FileEnvVar[0]);
    }

    /**********************************************************************************************
     * Process the conversion file, which represents data pulled from a C header file, that is
     * being imported and output the data to multiple files to help with the import process
     *
     * @param dataFile Path to the file being imported
     *
     * @param parent   Component that called this function
     *
     * @return FileEnvVar[] that represents all of the files created from the data found in the
     *         conversion file.
     *********************************************************************************************/
    public FileEnvVar[] processCSVConversionFile(FileEnvVar dataFile, final Component parent)
    {
        // Create a list to hold all the files
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();


        // This will throw an exception if it is incorrect
        try
        {
            String outputData = "";
            FileEnvVar file;
            String filePath = "";

            // Read the file
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));

            /*************** TABLE DEFINITIONS ***************/
            String tagData = csvHandler.retrieveCSVData(CSVTags.INPUT_TYPE.getTag(),
                                                        content).toString();

            if (!tagData.isEmpty())
            {
                outputData = tagData + "\n";
            }

            tagData = csvHandler.retrieveCSVData(CSVTags.TABLE_TYPE.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += "\n";
                }

                outputData += tagData + "\n";
            }

            tagData = csvHandler.retrieveCSVData(CSVTags.DATA_TYPE.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += "\n";
                }

                outputData += tagData + "\n";
            }

            if (!outputData.isEmpty())
            {
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.TABLE_INFO.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** MACROS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.MACRO.getTag(), content).toString();

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.MACROS.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            writeToJSONFile(outputData, filePath);

            /*************** TABLE DEFINITIONS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.NAME_TYPE.getTag(), content).toString();

            // Divide the data up into the individual definitions
            String[] data = outputData.split("\n\n");

            // Step through each definition
            for (int i = 0; i < data.length; i++)
            {
                if (!data[i].isEmpty())
                {
                    // Parse the name
                    String nameType = data[i].split("\n")[1];
                    String tableName = nameType.split("\",\"")[0].replace("\"", "");
                    tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");

                    // Create the file path and use the path to create a new file
                    filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".csv";
                    file = new FileEnvVar(filePath);

                    // Add this new file to the list of files
                    dataFiles.add(file);

                    // Write the data to the new file
                    writeToJSONFile("\n" + data[i], filePath);
                    outputData = "";
                }
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        return dataFiles.toArray(new FileEnvVar[0]);
    }

    /**********************************************************************************************
     * Process the single file, which represents an entire database, that is being imported and
     * output the data to multiple files.
     *
     * @param dataFile Path to the file being imported
     *
     * @param parent   Component that called this function
     *
     * @return FileEnvVar[] that represents all of the files created from the data found in the CSV
     *         file.
     *********************************************************************************************/
    public FileEnvVar[] processSingleCSVFileRepresentingDatabase(FileEnvVar dataFile,
                                                                 final Component parent)
    {
        // Create a list to hold all the files
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();

        // This will throw an exception if it is incorrect
        try
        {
            // Read the file
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));
            String outputData = "";
            FileEnvVar file;
            String filePath = "";

            /*************** INPUT TYPES, TABLE TYPES AND DATA TYPES ***************/
            String tagData = csvHandler.retrieveCSVData(CSVTags.INPUT_TYPE.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                outputData = tagData + "\n";
            }

            tagData = csvHandler.retrieveCSVData(CSVTags.TABLE_TYPE.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += "\n";
                }

                outputData += tagData + "\n";
            }

            tagData = csvHandler.retrieveCSVData(CSVTags.DATA_TYPE.getTag(), content).toString();

            if (!tagData.isEmpty())
            {
                if (!outputData.isEmpty())
                {
                    outputData += "\n";
                }

                outputData += tagData + "\n";
            }

            if (!outputData.isEmpty())
            {
                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.TABLE_INFO.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                writeToJSONFile(outputData, filePath);
            }

            /*************** MACROS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.MACRO.getTag(), content).toString();

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.MACROS.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);

            writeToJSONFile(outputData + "\n", filePath);

            /*************** GROUPS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.GROUP.getTag(), content).toString();

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.GROUPS.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);

            writeToJSONFile(outputData + "\n", filePath);

            /*************** SCRIPT ASSOCIATIONS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.SCRIPT_ASSOCIATION.getTag(),
                                                           content).toString();

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.SCRIPT_ASSOCIATION.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);

            writeToJSONFile(outputData + "\n", filePath);

            /*************** TLM SCHEDULER ***************/
            if (outputData.contains(CSVTags.TELEM_SCHEDULER_OLD.getTag()))
            {
                outputData = "\n"
                             + csvHandler.retrieveCSVData(CSVTags.TELEM_SCHEDULER_OLD.getTag(),
                                                          content).toString();
            }
            else
            {
                outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.TELEM_SCHEDULER_COMMENTS.getTag(),
                                                               content).toString();
            }

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.TELEM_SCHEDULER.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);

            writeToJSONFile(outputData + "\n", filePath);

            /*************** APP SCHEDULER ***************/
            if (outputData.contains(CSVTags.APP_SCHEDULER_OLD.getTag()))
            {
                outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.APP_SCHEDULER_OLD.getTag(),
                                                               content).toString();
            }
            else
            {
                outputData = "\n"
                             + csvHandler.retrieveCSVData(CSVTags.APP_SCHEDULER_COMMENTS.getTag(),
                                                          content).toString();
            }

            filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.APP_SCHEDULER.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);

            writeToJSONFile(outputData + "\n", filePath);

            /*************** RESERVED MESSAGE IDS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.RESERVED_MSG_IDS.getTag(),
                                                    content).toString();

            if (!outputData.isEmpty())
            {
                outputData = "\n" + outputData;

                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.RESERVED_MSG_ID.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);

                writeToJSONFile(outputData + "\n", filePath);
            }

            /*************** PROJECT FIELDS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.PROJECT_DATA_FIELD.getTag(),
                                                    content).toString();

            if (!outputData.isEmpty())
            {
                outputData = "\n" + outputData;

                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.PROJECT_DATA_FIELD.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);

                writeToJSONFile(outputData + "\n", filePath);
            }

            /*************** TABLE DEFINITIONS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.NAME_TYPE.getTag(), content).toString();
            String[] data = outputData.split(CSVTags.NAME_TYPE.getTag() + "\n");

            for (int tableIndex = 0; tableIndex < data.length; tableIndex++)
            {
                // Ensure there is data for this section (for example, the split can create a blank
                // one for the first index)
                if (!data[tableIndex].trim().isEmpty())
                {
                    String nameType = data[tableIndex].split("\n")[0];
                    String tableName = nameType.split("\",\"")[0].replace("\"", "");
                    tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");

                    filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".csv";
                    file = new FileEnvVar(filePath);
                    dataFiles.add(file);

                    if (tableIndex == data.length - 1)
                    {
                        writeToJSONFile("\n" + CSVTags.NAME_TYPE.getTag() + "\n" + data[tableIndex],
                                    filePath);
                    }
                    else
                    {
                        writeToJSONFile("\n" + CSVTags.NAME_TYPE.getTag() + "\n" + data[tableIndex] + "\n",
                                    filePath);
                    }
                }
            }

            /*************** DATABASE INFORMATION ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.DBU_INFO.getTag(), content).toString();

            if (!outputData.isEmpty())
            {
                outputData = "\n" + outputData;

                filePath = SNAP_SHOT_FILE_PATH_2 + File.separator + FileNames.DBU_INFO.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);

                writeToJSONFile(outputData + "\n", filePath);
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        return dataFiles.toArray(new FileEnvVar[0]);
    }

    /**********************************************************************************************
     * Write the JSON data to the specified file
     *
     * @param output   Data to be written
     *
     * @param filePath Path to the file
     *********************************************************************************************/
    public void writeToJSONFile(Object output, String filePath)
    {
        // Create a set of writers for the output file
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;

        try
        {
            if (output != null)
            {
                fw = new FileWriter(filePath, false);
                bw = new BufferedWriter(fw);
                pw = new PrintWriter(bw);
                pw.print((String) output);
            }
        }
        catch (Exception e)
        {
            // Inform the user that the output file cannot be written to
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                       "<html><b>Cannot write to JSON output file '</b>"
                                       + filePath
                                       + "<b>'; cause '<\b>"
                                       + e.getMessage()
                                       + "<b>'",
                                       "File Warning",
                                       JOptionPane.WARNING_MESSAGE,
                                       DialogOption.OK_OPTION);
        }
        finally
        {
            try
            {
                // Check if the PrintWriter was opened
                if (pw != null)
                {
                    // Close the file
                    pw.close();
                }

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
                // Inform the user that the output file cannot be closed
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                           "<html><b>Cannot close JSON output file '</b>"
                                           + filePath
                                           + "<b>'; cause '<\b>"
                                           + ioe.getMessage()
                                           + "<b>'",
                                           "File Warning",
                                           JOptionPane.WARNING_MESSAGE,
                                           DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Write the JSON data to the specified file
     *
     * @param fileExt       The extension type of the files that are going to be exported
     *
     * @param directoryPath Path to the directory that is going to be cleaned
     *
     * @param singleFile    true if exporting to a single file
     *
     * @param parent        GUI component over which to center any error dialog
     *********************************************************************************************/
    public void CleanExportDirectory(FileExtension fileExt,
                                     String directoryPath,
                                     boolean singleFile,
                                     Component parent)
    {
        // Delete the specified contents of the directory
        try
        {
            if (!singleFile)
            {
                // Clear the directory
                File directory = new File(directoryPath);
                File fList[] = directory.listFiles();

                for (int index = 0; index < fList.length; index++)
                {
                    String currentFile = fList[index].getName();
                    if (currentFile.endsWith(fileExt.getExtension()))
                    {
                        fList[index].delete();
                    }
                }
            }
            else if (!directoryPath.contentEquals(SNAP_SHOT_FILE_PATH))
            {
                // Trim the filename off
                String[] temp = directoryPath.split(File.separator);
                String name = temp[temp.length - 1];
                String tempPath = directoryPath.replace(name, "");

                // Clear the directory
                File directory = new File(tempPath);
                File fList[] = directory.listFiles();

                for (int index = 0; index < fList.length; index++)
                {
                    String currentFile = fList[index].getName();
                    if (currentFile.endsWith(fileExt.getExtension()))
                    {
                        fList[index].delete();
                    }
                }
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }
    }

    /**********************************************************************************************
     * Prepare the project for a JSON or CSV export
     *
     * @param tableNames              Array of table names to convert
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
     * @param parent                  GUI component over which to center any error dialog
     *
     * @return List containing the definition(s) of the table(s) to export
     *
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    public List<TableInfo> prepareJSONOrCSVExport(String[] tableNames,
                                                  boolean includeVariablePaths,
                                                  CcddVariableHandler variableHandler,
                                                  String[] separators,
                                                  Component parent) throws CCDDException, Exception
    {

        // Add the prototype of each supplied table name (if not already in the array of supplied
        // table names)
        List<String> tableNamesWithProtos = new ArrayList<String>();

        for (int index = 0; index < tableNames.length; index++)
        {
            tableNamesWithProtos.add(tableNames[index]);
            String protoName = TableInfo.getPrototypeName(tableNames[index]);

            if (!tableNamesWithProtos.contains(protoName))
            {
                // Put the prototypes at the head of the list so that they're processed first below
                tableNamesWithProtos.add(0, protoName);
            }
        }

        // Initialize local variables
        List<String[]> variablePaths = new ArrayList<String[]>();
        List<TableInfo> tableDefs = new ArrayList<TableInfo>(tableNamesWithProtos.size());
        StringBuilder tableDataCommand = new StringBuilder();
        StringBuilder tableValuesCommand = new StringBuilder();
        StringBuilder tableDescriptionCommand = new StringBuilder();
        StringBuilder tableTypeCommand = new StringBuilder();
        TableInfo[] tableInfos = new TableInfo[tableNamesWithProtos.size()];
        int index = 0;

        // Create an empty hash map. The key will be the name of the prototype table. The value
        // will be a String[] with values [table description, table type, location of this tables
        // information within the tableInfos array, column names]
        HashMap<String, String[]> ProtoTypeMap = new HashMap<>();
        HashMap<String, String[]> ProtoTypeColumnsMap = new HashMap<>();
        HashMap<String, String> tableNamesMap = new HashMap<>();

        for (index = 0; index < tableNamesWithProtos.size(); index++)
        {
            tableNamesMap.put(tableNamesWithProtos.get(index), tableNamesWithProtos.get(index));
        }

        // Check if all variable paths are to be exported. This is only possible if no tables are
        // specified; otherwise only those variables in the table are exported
        if (includeVariablePaths && tableNamesWithProtos.size() == 0)
        {
            // Step through each structure and variable name
            for (String variablePath : variableHandler.getAllVariableNames())
            {
                // Add the path, in both application and user-defined formats, to the list to be
                // output
                variablePaths.add(new String[] {variablePath,
                                                variableHandler.getFullVariableName(variablePath,
                                                                                    separators[0],
                                                                                    Boolean.parseBoolean(separators[1]),
                                                                                    separators[2])});
            }
        }

        // Check if any tables are provided
        if (tableNamesWithProtos.size() != 0)
        {
            index = 0;

            // Build the commands using the name of each table
            for (String tblName : tableNamesWithProtos)
            {
                tableInfos[index] = new TableInfo(tblName);
                tableInfos[index].setTablePath(tblName); // This sets the table's isPrototype flag

                // Is this table a prototype table
                boolean isProtoTypeTable = tableInfos[index].isPrototype();
                String protoTypeTable = tableInfos[index].getPrototypeName();

                // Only add this table to the table type command if it is a prototype table
                if (isProtoTypeTable)
                {
                    tableTypeCommand.append("SELECT obj_description('public.")
                                    .append(protoTypeTable)
                                    .append("'::regclass, 'pg_class'); ");
                }
                else
                {
                    // If so it may have values in the custom values table that must be loaded

                    // Get the rows from the custom values table that match the specified parent
                    // table and variable path. These values replace those loaded for the prototype
                    // of this table
                    tableValuesCommand.append("SELECT * FROM ")
                                      .append(InternalTable.VALUES.getTableName())
                                      .append(" WHERE ")
                                      .append(ValuesColumn.TABLE_PATH.getColumnName())
                                      .append(" LIKE '")
                                      .append(tblName)
                                      .append("%';");
                }

                tableDescriptionCommand.append("SELECT ")
                                       .append(ValuesColumn.VALUE.getColumnName())
                                       .append(" FROM ")
                                       .append(InternalTable.VALUES.getTableName())
                                       .append(" WHERE ")
                                       .append(ValuesColumn.TABLE_PATH.getColumnName())
                                       .append(" = '")
                                       .append(tblName)
                                       .append("' AND column_name = ''; ");
                index++;
            }

            // Execute the tableDescriptionCommand as a prepared statement as it may return
            // multiple result sets
            PreparedStatement tableDescriptionStatement = dbCommand.executePreparedStatement(tableDescriptionCommand,
                                                                                             parent);

            // Check that the command returned something
            if (tableDescriptionStatement != null)
            {
                boolean execute = true;
                index = 0;

                // While there is at least one result set left and index is less than the total
                // number of tables; continue to process data
                while (execute && index < tableNamesWithProtos.size())
                {
                    // Grab the next result set
                    ResultSet rs = tableDescriptionStatement.getResultSet();

                    // Step though each entry of this result set
                    if (rs.next())
                    {
                        // Get the table description
                        tableInfos[index].setDescription(rs.getString(1).trim());
                    }

                    // Close the current result set
                    rs.close();

                    // Check if this description is null
                    if (tableInfos[index].getDescription() == null)
                    {
                        // If this table is not a prototype and has no description then assign it
                        // the description of its prototype. If it is a prototype then set it to an
                        // empty string
                        if (!tableInfos[index].isPrototype())
                        {
                            tableInfos[index].setDescription(ProtoTypeMap.get(tableInfos[index].getPrototypeName())[0]);
                        }
                        else
                        {
                            tableInfos[index].setDescription("");
                        }
                    }

                    // If this table is a prototype then add this table and its description to the
                    // prototype hash map
                    if (tableInfos[index].isPrototype())
                    {
                        ProtoTypeMap.put(tableInfos[index].getPrototypeName(),
                                         new String[] {tableInfos[index].getDescription(), "", ""});
                    }

                    tableInfos[index].setDescription(tableInfos[index].getDescription());

                    // Check if there are more result sets and increment the index
                    execute = tableDescriptionStatement.getMoreResults();
                    index++;
                }
            }

            // Execute the tableTypeCommand as a prepared statement as it may return multiple
            // result sets
            PreparedStatement tableTypeStatement = dbCommand.executePreparedStatement(tableTypeCommand, parent);

            // Check that the command returned something
            if (tableTypeStatement != null)
            {
                boolean execute = true;
                index = 0;

                // Step through each table name
                while (index < tableNamesWithProtos.size())
                {
                    // Check if there is another result set to process and if it belongs to a
                    // prototype table
                    if (tableInfos[index].isPrototype() && execute)
                    {
                        // Grab the result set
                        ResultSet rs = tableTypeStatement.getResultSet();

                        // Step though each entry in this result
                        if (rs.next())
                        {
                            // Get the table type
                            tableInfos[index].setType(rs.getString(1).split(",")[1]);
                        }

                        // Close the current result set
                        rs.close();

                        // Check if there are any more result sets to process
                        execute = tableTypeStatement.getMoreResults();

                        // Add this table type to the correct location within the hash map while
                        // preserving the current info for this key
                        ProtoTypeMap.put(tableInfos[index].getPrototypeName(),
                                         new String[] {ProtoTypeMap.get(tableInfos[index].getPrototypeName())[0],
                                                       tableInfos[index].getType(), ""});
                        TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfos[index].getType());
                        String[] columnNames = typeDefn.getColumnNamesDatabase();

                        ProtoTypeColumnsMap.put(tableNamesWithProtos.get(index), columnNames);

                        // Create a command that grabs the data of each column in the correct order
                        if (columnNames.length > 0)
                        {
                            tableDataCommand.append("SELECT ");

                            for (int column = 0; column < columnNames.length; column++)
                            {
                                if (column == 0)
                                {
                                    tableDataCommand.append(" ")
                                                    .append(dbControl.getQuotedName(columnNames[column]));
                                }
                                else
                                {
                                    tableDataCommand.append(", ")
                                                    .append(dbControl.getQuotedName(columnNames[column]));
                                }
                            }

                            tableDataCommand.append(" FROM ")
                                            .append(tableInfos[index].getPrototypeName())
                                            .append(" ORDER BY _index_; ");
                        }
                    }
                    else if (index < tableNamesWithProtos.size())
                    {
                        // Get the table type
                        tableInfos[index].setType(ProtoTypeMap.get(tableInfos[index].getPrototypeName())[1]);
                    }

                    index++;
                }
            }

            // Execute the table data command as a prepared statement as it will have multiple
            // result sets
            PreparedStatement tableDataStatement = dbCommand.executePreparedStatement(tableDataCommand, parent);

            // Check that the command returned something
            if (tableDataStatement != null)
            {
                List<TableInfo> protoTypeTableObjects = new ArrayList<TableInfo>();
                PreparedStatement tableValuesStatement = null;
                boolean executeProto = true;
                boolean executeInstance = false;
                index = 0;

                // If instance tables are included then execute the values table command
                if (tableValuesCommand.toString().length() != 0)
                {
                    tableValuesStatement = dbCommand.executePreparedStatement(tableValuesCommand, parent);

                    if (tableValuesStatement != null)
                    {
                        executeInstance = true;
                    }
                }

                // Step through each table name
                while (index < tableNamesWithProtos.size() && (executeProto || executeInstance))
                {
                    // If there is another result set to process and this table is a prototype
                    // table
                    if (executeProto && tableInfos[index].isPrototype())
                    {
                        List<Object[]> tableData = new ArrayList<Object[]>(1000);

                        // Grab the result set
                        ResultSet rs = tableDataStatement.getResultSet();

                        // Step though each entry in the current result set
                        while (rs.next())
                        {
                            // Create an array to contain the data for this row
                            String[] currRowData = new String[rs.getMetaData().getColumnCount()];

                            // Step through each column in the table
                            for (int column = 0; column < rs.getMetaData().getColumnCount(); column++)
                            {
                                // Add the column value to the array. Note that the first column's
                                // index in the database is 1, not 0
                                currRowData[column] = rs.getString(column + 1);

                                if (currRowData[column] == null)
                                {
                                    currRowData[column] = "";
                                }
                            }

                            tableData.add(currRowData);
                        }

                        // Create a new tableData Object that will contain all the information for
                        // this table
                        TableInfo tableInfo = new TableInfo(tableNamesWithProtos.get(index),
                                                            tableInfos[index].getType(),
                                                            tableInfos[index].getDescription(),
                                                            tableData);

                        // Add the object to the protoTypeTableObjects list and tableDefs list
                        protoTypeTableObjects.add(tableInfo);
                        tableDefs.add(tableInfo);

                        // Determine which entry will need to be updated
                        String[] entryToUpdate = ProtoTypeMap.get(tableInfos[index].getPrototypeName());

                        // Update the correct entry
                        ProtoTypeMap.put(tableInfos[index].getPrototypeName(),
                                         new String[] {entryToUpdate[0], entryToUpdate[1],
                                                       Integer.toString(protoTypeTableObjects.size() - 1)});

                        // Close the current result set
                        rs.close();

                        // Check if there are more result sets to process
                        executeProto = tableDataStatement.getMoreResults();
                    }
                    else if (executeInstance && index < tableNamesWithProtos.size())
                    {
                        // Grab the index that represents this tables information within the
                        // protoTypeTableObjects list from the hash map
                        int location = Integer.parseInt(ProtoTypeMap.get(tableInfos[index].getPrototypeName())[2]);

                        // Grab the information
                        List<Object[]> data = cloneList(protoTypeTableObjects.get(location).getData());

                        // Grab the result set
                        ResultSet rs = tableValuesStatement.getResultSet();

                        // Grab the column names
                        List<String> columnNames = Arrays.asList(ProtoTypeColumnsMap.get(tableInfos[index].getPrototypeName()));

                        HashMap<String, Integer> rowNamesMap = new HashMap<>();
                        boolean firstRun = true;

                        // Step though each entry in the current result set
                        while (rs.next())
                        {
                            String rsEntryTableName = rs.getString(1).substring(0, rs.getString(1).lastIndexOf(","));

                            if (!rs.getString(2).isEmpty())
                            {
                                if (tableNamesMap.get(rsEntryTableName) != null
                                    && rsEntryTableName.contentEquals(tableNamesWithProtos.get(index)))
                                {
                                    if (firstRun)
                                    {
                                        firstRun = false;

                                        for (int i = 0; i < data.size(); i++)
                                        {
                                            rowNamesMap.put(data.get(i)[2].toString(), i);
                                        }
                                    }

                                    int currRow = -1;
                                    String currRowVarName = rs.getString(1).substring(rs.getString(1).lastIndexOf(".") + 1);

                                    if (rowNamesMap.get(currRowVarName) != null)
                                    {
                                        currRow = rowNamesMap.get(currRowVarName);

                                        // Get the location of this column
                                        int columnLocation = columnNames.indexOf(rs.getString(2));

                                        if (columnLocation != -1)
                                        {
                                            data.get(currRow)[columnLocation] = rs.getString(3);
                                        }
                                    }
                                }
                            }
                        }

                        // Add this information to the tableDefs list
                        tableDefs.add(new TableInfo(tableNamesWithProtos.get(index),
                                                    tableInfos[index].getType(),
                                                    tableInfos[index].getDescription(),
                                                    data));
                        executeInstance = tableValuesStatement.getMoreResults();
                    }

                    index++;
                }
            }
        }

        return tableDefs;
    }

    /**********************************************************************************************
     * Return the clone of a list
     *
     * @param list The list to be cloned
     *
     * @return Cloned list
     *********************************************************************************************/
    public static List<Object[]> cloneList(List<Object[]> list)
    {
        List<Object[]> clone = new ArrayList<Object[]>(list.size());

        for (Object[] item : list)
        {
            clone.add(item.clone());
        }

        return clone;
    }
}
