/**************************************************************************************************
/** \file CcddFileIOHandler.java
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Class containing file input and output methods (project database backup and restore, table
*     import and export, script storage and retrieval).
*
*   \copyright
*     MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
*
*     Copyright (c) 2016-2021 United States Government as represented by the 
*     Administrator of the National Aeronautics and Space Administration.  All Rights Reserved.
*
*     This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
*     distributed and modified only pursuant to the terms of that agreement.  See the License for 
*     the specific language governing permissions and limitations under the
*     License at https://software.nasa.gov/.
*
*     Unless required by applicable law or agreed to in writing, software distributed under the
*     License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
*     either expressed or implied.
*
*   \par Limitations, Assumptions, External Events and Notes:
*     - TBD
*
**************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPT_DESCRIPTION_TAG;
import static CCDD.CcddConstants.USERS_GUIDE;
import static CCDD.CcddConstants.overwriteExistingCbIndex;
import static CCDD.CcddConstants.appendToExistingDataCbIndex;
import static CCDD.CcddConstants.ignoreErrorsCbIndex;
import static CCDD.CcddConstants.keepDataFieldsCbIndex;
import static CCDD.CcddConstants.SNAP_SHOT_FILE_PATH;
import static CCDD.CcddConstants.SNAP_SHOT_FILE_PATH_2;
import static CCDD.CcddConstants.SNAP_SHOT_FILE;
import static CCDD.CcddConstants.EXPORT_MULTIPLE_FILES;
import static CCDD.CcddConstants.EXPORT_SINGLE_FILE;

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
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
import CCDD.CcddClassesDataTable.TableInformation;
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
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
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
import CCDD.ConvertCStructureToCSV;

import org.apache.commons.io.FileUtils;

/**************************************************************************************************
 * CFS Command and Data Dictionary file I/O handler class
 *************************************************************************************************/
public class CcddFileIOHandler {
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
     * @param ccddMain main class
     *********************************************************************************************/
    CcddFileIOHandler(CcddMain ccddMain) {
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
    protected void setHandlers() {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
    }

    /**********************************************************************************************
     * Display the user's guide. The guide file must be located in the same folder
     * as the .jar file. This command is executed in a separate thread since it may
     * take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update
     *********************************************************************************************/
    protected void displayUsersGuide() {
        // Extract and display the help file in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand() {
            /**************************************************************************************
             * Extract (if not already extracted) and display the help file
             *************************************************************************************/
            @Override
            protected void execute() {
                try {
                    // Check if the Desktop class is not supported by the platform
                    if (!Desktop.isDesktopSupported()) {
                        // Set the error type message
                        throw new CCDDException("Desktop class unsupported");
                    }

                    // Get the path+name of the .jar file in a format acceptable to all OS's. The
                    // user's guide is expected to be found in the same folder as the .jar file
                    String path = URLDecoder.decode(
                            new File(CcddMain.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                                    .getAbsolutePath(),
                            "UTF-8");
                    
                    // Display the user's guide - replace the .jar file name with the user's guide name
                    Desktop.getDesktop()
                            .open(new File(path.substring(0, path.lastIndexOf(File.separator) + 1) + "docs/" + USERS_GUIDE));
                } catch (Exception e) {
                    // Inform the user that an error occurred opening the user's guide
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html><b>User's guide '</b>" + USERS_GUIDE + "<b>' cannot be opened; cause '</b>"
                                    + e.getMessage() + "<b>'",
                            "File Error", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
                }
            }
        });
    }

    /**********************************************************************************************
     * This function will accept a file or files with JSON/CSV format. It will look thorough the 
     * files and determine if they contain data that is not currently in the database. If so it
     * will call an import function on this data. If the files to be imported are in agreement 
     * with the current state of the database this will be reported and nothing will be imported
     * 
     * @param dataFiles                  The files being imported
     * 
     * @param importingEntireDatabase    Is an entire database being imported?
     * 
     * @param backupFirst                true to create a backup of the database
     *                                   before importing tables
     *
     * @param replaceExistingTables      true to replace a table that already exists
     *                                   in the database
     *
     * @param appendExistingFields       true to append the existing data fields for
     *                                   a table (if any) to the imported ones (if
     *                                   any). Only valid when replaceExisting is
     *                                   true
     *
     * @param useExistingFields          true to use an existing data field in place
     *                                   of the imported ones if the field names
     *                                   match. Only valid when replaceExisting and
     *                                   appendExistingFields are true
     *
     * @param openEditor                 true to open a table editor for each
     *                                   imported table
     *
     * @param ignoreErrors               true to ignore all errors in the import
     *                                   file
     *
     * @param replaceExistingMacros      true to replace the values for existing
     *                                   macros
     *                                   
     * @param replaceExistingAssociations true to overwrite internal associations with
     *                                    those from the import file
     * 
     * @param replaceExistingGroups      true to replace the values for existing
     *                                   groups
     * 
     * @param deleteNonExistingFiles     true to delete any tables from the database
     *                                   that were not part of the import
     *                                   
     * @param importFileType             The extension for the file type being imported
     *                                   
     * @param dialogType                 What type of file is being imported
     *
     * @param parent                     GUI component over which to center any
     *                                   error dialog
     *********************************************************************************************/
    protected boolean prepareJSONOrCSVImport(FileEnvVar[] dataFiles, final boolean importingEntireDatabase,
            final boolean backupFirst, final boolean replaceExistingTables, final boolean appendExistingFields,
            final boolean useExistingFields, final boolean openEditor, final boolean ignoreErrors,
            final boolean replaceExistingMacros, final boolean replaceExistingAssociations,
            final boolean replaceExistingGroups, final boolean deleteNonExistingFiles,
            FileExtension importFileType, ManagerDialogType dialogType, final Component parent) {
        /* Select all current tables in the database and prepare them for export. This
         * will be used later to resolve which import files are new/modified
         */
        CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain, TableTreeType.TABLES, null);
        tableTree.setSelectionInterval(0, tableTree.getRowCount());
        List<String> tablePaths = tableTree.getSelectedTablesWithChildren();
        boolean errorFlag = false;
        
        /* Create a FileEnvVar array for all files in the user-supplied directory. */
        List<FileEnvVar> importFiles = new ArrayList<>();

        /* Indicates if any changes were made to the database */
        boolean dataWasChanged = false;
        
        /* If this is a bunch of C header files then they need to be converted */
        if (importFileType == FileExtension.C_HEADER) {
            ConvertCStructureToCSV conversionHandler = new ConvertCStructureToCSV();
            dataFiles = conversionHandler.convertFile(dataFiles, ccddMain);
            dialogType = ManagerDialogType.IMPORT_CSV;
            importFileType = FileExtension.CSV;
        } 
        
        /* Are we importing a single large file that represents the whole database? */
        if ((importingEntireDatabase) && (dataFiles.length == 1)) {
            if (dialogType == ManagerDialogType.IMPORT_JSON) {
                dataFiles = processSingleJSONFileRepresentingDatabase(dataFiles[0], dialogType, parent);
            } else if (dialogType == ManagerDialogType.IMPORT_CSV) {
                dataFiles = processSingleCSVFileRepresentingDatabase(dataFiles[0], parent);
            } 
        } else if ((!importingEntireDatabase) && (dataFiles.length == 1) && (dialogType == ManagerDialogType.IMPORT_CSV)) {
            /* Create a buffered reader to read the file */
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader(dataFiles[0]));
                
                /* Read first line in file */
                String line = br.readLine();
                
                /* Check to see if this is a conversion file */
                if (line.contains("c_struct_to_csv_conversion")) {
                    /* Process the converted file into individual files that can be imported */
                    dataFiles = processCSVConversionFile(dataFiles[0], parent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            /* If there is only 1 file than that means that we are looking at a super file that
             * contains the information of an entire database or the user is only importing a single
             * file. Regardless there is no need for the snapshot directory or checking to see what 
             * files were deleted and such.
             */
            if (dataFiles.length != 0) {
                List<File> snapshotFiles = compareToSnapshotDirectory(tablePaths.toArray(new String[0]),
                        true, importFileType, false, parent);
                
                for (FileEnvVar dataFile : dataFiles) {
                    /* Only import files that end with the correct file extension */
                    if (dataFile.getName().endsWith(importFileType.getExtension())) {
                        importFiles.add(dataFile);
                    }
                }
    
                List<File> deletedFiles = new ArrayList<>(snapshotFiles);
                
                // First try to compare files using the linux diff command. If it fails use the file utils compare that ignore the EOL char
                boolean compareFilesUsingLinuxDiffCommand = true;
    
                /* Step through each file in the deleted files array */
                for (int index = 0; index < deletedFiles.size(); index++) {
                    for (int index2 = 0; index2 < importFiles.size(); index2++) {
                        /* Do the filenames equal each other? Or, is the deletedFile element in question
                         * not of the correct extension?
                         */
                        if (deletedFiles.get(index).getName().equals(importFiles.get(index2).getName())) {
                            /* This file exists. Determine if it has been modified or not before deletion */
                            try {
                                // First try to compare files using the linux diff command.
                                if (compareFilesUsingLinuxDiffCommand) {
                                    try {
                                        Process p;
                                        
                                        // Build the command
                                        String[] command = new String[] {"diff", "-I", JSONTags.FILE_DESCRIPTION.getTag(),
                                                importFiles.get(index2).getPath(), deletedFiles.get(index).getPath()};
                                        
                                        // Execute the command
                                        p = Runtime.getRuntime().exec(command);
                                        
                                        // Wait for the command to finish
                                        p.waitFor();
                                        final int exitValue = p.waitFor();
                                        
                                        // Check the exit value
                                        if (exitValue == 0) {
                                            /* The files are the same so nothing will be done with this file. Remove it from
                                             * the importFiles list
                                             */
                                            importFiles.remove(index2);
                                        }
                                        
                                        // Destroy the process
                                        p.destroy();
                                        
                                    } catch (Exception e) {
                                        // An error occurred. Stop trying to compare using the linux diff command. Most often this is due
                                        // to running CCDD on a windows platform
                                        compareFilesUsingLinuxDiffCommand = false;
                                    }
                                }
                                
                                // If comparing files using the linux diff command failed then compare them using the FileUtils function
                                if (!compareFilesUsingLinuxDiffCommand) {
                                    if (FileUtils.contentEqualsIgnoreEOL(new File(importFiles.get(index2).getPath()),
                                            new File(deletedFiles.get(index).getPath()), null)) {
                                        /* The files are the same so nothing will be done with this file. Remove it from
                                         * the importFiles list
                                         */
                                        importFiles.remove(index2);
                                    }
                                }
                                
                                /* The file exists so remove it from the deletedFiles list */
                                deletedFiles.remove(index);
                                index--;
    
                            } catch (IOException ioe) {
                                CcddUtilities.displayException(ioe, parent);
                                ioe.printStackTrace();
                                errorFlag = true;
                            }
                            break;
                        }
                    }
                }
                
                /* Check to see if the files that are going to be deleted are the reserved message ids and project fields.
                 * If so then ignore them
                 */
                for (int index = 0; index < deletedFiles.size(); index++) {
                    if ( deletedFiles.get(index).getPath().endsWith(FileNames.PROJECT_DATA_FIELD.JSON()) ||
                            deletedFiles.get(index).getPath().endsWith(FileNames.RESERVED_MSG_ID.JSON()) ||
                            deletedFiles.get(index).getPath().endsWith(FileNames.PROJECT_DATA_FIELD.CSV()) ||
                            deletedFiles.get(index).getPath().endsWith(FileNames.RESERVED_MSG_ID.CSV())) {
                        /* Remove it from the deletedFiles list */
                        deletedFiles.remove(index);
                        index--;
                    }
                }
                /* End of checking for deleted files */
    
                /* Does the user want non-existing files to be deleted? */
                if (deleteNonExistingFiles == true) {
                    /* Is there anything to delete? */
                    if (deletedFiles.size() != 0) {
                        /* Inform the user that there are tables scheduled to be deleted. */
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                "<html><b>Some tables are going to be deleted.</b>", "Deletion Warning",
                                JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
    
                        /* Here, deal with the differing formats in how the filenames are stored vs. how
                         * table paths are stored.
                         */
                        List<String> pathList = tableTree.getTableTreePathList(null);
                        List<String> deletePathList = new ArrayList<String>();
    
                        /* Check if there are paths in the table tree that correspond to the deleted
                         * filenames. Add them to a list.
                         */
                        for (String dir : pathList) {
                            String pathReplace = dir.replaceAll("\\,", "_").replaceAll("\\.", "_");
                            for (File fileToDelete : deletedFiles) {
                                if ((fileToDelete.getName().replace(importFileType.getExtension(), "")).equals(pathReplace)) {
                                    deletePathList.add(dir);
                                }
                            }
                        }
    
                        /* Now that we have a list of the table paths for the deleted tables, turn it
                         * into an array and delete them.
                         */
                        String[] deleteTableNames = deletePathList.toArray(new String[deletePathList.size()]);
                        dbTable.deleteTable(deleteTableNames, null, null);
    
                        /* If tables were deleted then that means the database has been altered */
                        dataWasChanged = true;
                    }
                }
            } else {
                /* Only import files that end with the correct file extension */
                if (dataFiles[0].getName().endsWith(importFileType.getExtension())) {
                    importFiles.add(new FileEnvVar(dataFiles[0].getPath()));
                }
            }

            /* Do any files remain after trimming the duplicate files? Import them here. */
            if (importFiles.size() > 0) {
                /* Create the import cancellation dialog */
                haltDlg = new CcddHaltDialog("Import Data(s)", "Importing data", "import", 100, importFiles.size(),
                        ccddMain.getMainFrame());

                /* Import file into database */
                importFiles(importFiles, backupFirst, replaceExistingTables, appendExistingFields, useExistingFields,
                        openEditor, ignoreErrors, replaceExistingMacros, replaceExistingAssociations, replaceExistingGroups,
                        dialogType, parent);

                dataWasChanged = true;
            }

            /* If we reach here, that means there's nothing to add and nothing to delete. */
            if (dataWasChanged == false) {
                /* Inform the user that there are no perceptible changes to the files relative
                 * to current db state.
                 */
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                        "<html><b>The selected folder/file does not contain any updates.</b>",
                        "No Changes Made", JOptionPane.INFORMATION_MESSAGE, DialogOption.OK_OPTION);
            }

            /* If the snapshot directory exists than delete it */
            if (Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH))) {
                /* Step through each file in the existent .snapshot directory */
                for (File file : new File(SNAP_SHOT_FILE_PATH).listFiles()) {
                    /* Delete each file */
                    file.delete();
                }
            }
        } catch (NullPointerException e) {
            CcddUtilities.displayException(e, parent);
            e.printStackTrace();
            errorFlag = true;
        }
        return errorFlag;
    }

    /**********************************************************************************************
     * Backup the currently open project's database to a user-selected file using
     * the pg_dump utility. The backup data is stored in plain text format
     *
     * @param doInBackground true to perform the operation in a background process
     *********************************************************************************************/
    protected void backupDatabaseToFile(boolean doInBackground) {
        // Set the flag if the current user's password is non-blank. Depending on the
        // authentication set-up and operating system, the password may still be
        // required by the
        // pg_dump command even if the authentication method is 'trust'
        boolean isPasswordSet = dbControl.isPasswordNonBlank();

        // Check if no password is set
        if (!isPasswordSet) {
            // Display the password dialog and obtain the password. Note that the user can
            // enter a
            // blank password (which may be valid)
            CcddServerPropertyDialog dialog = new CcddServerPropertyDialog(ccddMain, ServerPropertyDialogType.PASSWORD);

            // Set the flag if the user selected the Okay button in the password dialog
            isPasswordSet = dialog.isPasswordSet();
        }

        // Check if the user's database password is set (either non-blank or explicitly
        // set to blank)
        if (isPasswordSet) {
            // Get the name of the currently open database
            final String databaseName = dbControl.getDatabaseName();
            String projectName = dbControl.getProjectName();

            // Set the initial layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                    GridBagConstraints.BOTH, new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                            ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2, 0, 0),
                    0, 0);

            // Create the backup file choice dialog
            final CcddDialogHandler dlg = new CcddDialogHandler();

            // Create a date and time stamp check box
            JCheckBox stampChkBx = new JCheckBox("Append date and time to file name");
            stampChkBx.setBorder(BorderFactory.createEmptyBorder());
            stampChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            stampChkBx.setSelected(false);
            final String dateTimeFormat = "yyyyMMdd_HHmmss";

            // Create a listener for check box selection actions
            stampChkBx.addActionListener(new ActionListener() {
                String timeStamp = "";

                /**************************************************************************************
                 * Handle check box selection actions
                 *************************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae) {
                    // Check if the data and time stamp check box is selected
                    if (((JCheckBox) ae.getSource()).isSelected()) {
                        // Get the current date and time stamp
                        timeStamp = "_"
                                + new SimpleDateFormat(dateTimeFormat).format(Calendar.getInstance().getTime());

                        // Append the date and time stamp to the file name
                        dlg.getFileNameField()
                                .setText(dlg.getFileNameField().getText().replaceFirst(
                                        Pattern.quote(FileExtension.DBU.getExtension()),
                                        timeStamp + FileExtension.DBU.getExtension()));
                    }
                    // The check box is not selected
                    else {
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
            FileEnvVar[] dataFile = dlg.choosePathFile(ccddMain, ccddMain.getMainFrame(),
                    databaseName + FileExtension.DBU.getExtension(), null,
                    new FileNameExtensionFilter[] { new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                            FileExtension.DBU.getExtensionName()) },
                    false, false, "Backup Project " + projectName,
                    ccddMain.getProgPrefs().get(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPreferenceKey(), null),
                    DialogOption.BACKUP_OPTION, stampPnl);

            // File name with the white space stripped
            String compliantFileName = dlg.getFileNameField().getText().replaceAll("\\s", "");
            
            FileEnvVar chosenBackupPath = CcddBackupName.reconstructBackupFilePath(dataFile, compliantFileName);
            
            // Check if a file was chosen
            if (chosenBackupPath != null) {
                boolean cancelBackup = false;

                // Check if the backup file exists
                if (chosenBackupPath.exists()) {
                    // Check if the existing file should be overwritten
                    if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html><b>Overwrite existing backup file?", "Overwrite File", JOptionPane.QUESTION_MESSAGE,
                            DialogOption.OK_CANCEL_OPTION) == OK_BUTTON) {
                        // Check if the file can be deleted
                        if (!chosenBackupPath.delete()) {
                            // Inform the user that the existing backup file cannot be replaced
                            new CcddDialogHandler().showMessageDialog(
                                    ccddMain.getMainFrame(), "<html><b>Cannot replace existing backup file '</b>"
                                            + chosenBackupPath.getAbsolutePath() + "<b>'",
                                    "File Error", JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
                            cancelBackup = true;
                        }
                    }
                    // File should not be overwritten
                    else {
                        // Cancel backing up the database
                        cancelBackup = true;
                    }
                }

                // Strip away the file name extension and the data and time (if any)
                String backupName = CcddBackupName.removeExtensionTimeStamp(
                                FileExtension.DBU.getExtension(), 
                                dateTimeFormat,
                                compliantFileName, 
                                stampChkBx.isSelected());
                
                // Is the new database name different from the current one?
                boolean isDifferentName = !databaseName.equals(backupName);
                
                // Check to see if the new name already exists (and is not the same name)
                if(isDifferentName && dbControl.isDatabaseNameInUse(dbControl.convertProjectNameToDatabase(backupName))){
                    new CcddDialogHandler().showMessageDialog(
                            ccddMain.getMainFrame(), "<html><b>Cannot backup project '</b>"
                                    + backupName + "<b>', project already exists in workspace.",
                            "Backup Error", JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
                        cancelBackup = true;
                }

                // Check that no errors occurred and that the user didn't cancel the backup
                if (!cancelBackup) {
                    // Check if the operation should be performed in the background
                    if (doInBackground) 
                    {
                        if(isDifferentName)
                        {
                            // Create a backup of the current database
                            dbControl.backupAndRenameDatabaseInBackground(projectName, backupName, chosenBackupPath);
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
                        if(isDifferentName)
                        {
                            // This will be coming from the GUI which will require that the only database that can be
                            // backed up is the open one so close it first
                            if (ccddMain.ignoreUncommittedChanges("Close Project", "Discard changes?", true, null, null)) 
                            {
                                dbControl.openDatabase(DEFAULT_DATABASE);
                            } 
                            else 
                            {
                                    // The user has chosen not to close the database so exit
                                    return;
                            }
                        
                            // Perform the sequence of operations here
                            dbControl.backupAndRenameDatabase(projectName, backupName, chosenBackupPath);
                            
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
     * Restore a project's database from a user-selected backup file. The backup is
     * a plain text file containing the PostgreSQL commands necessary to rebuild the
     * database
     *********************************************************************************************/
    protected void restoreDatabaseFromDBU() {
        restoreDatabaseFromDBU(null, null, null, null);
    }

    /**********************************************************************************************
     * Restore a project's database from a specified or user-selected backup file.
     * The backup is a plain text file containing the PostgreSQL commands necessary
     * to rebuild the database
     *
     * @param restoreFileName    name of the backup file to restore; null to allow
     *                           the user to choose a file
     *
     * @param projectName        name of the project to restore; null to extract the
     *                           project name from the backup file's database
     *                           comment
     *
     * @param projectOwner       owner of the project to restore; null to use the
     *                           current user as the owner
     *
     * @param projectDescription description of the project to restore; null to
     *                           extract the description from the backup file's
     *                           database comment
     *********************************************************************************************/
    protected void restoreDatabaseFromDBU(String restoreFileName, String projectName, String projectOwner,
            String projectDescription) {
        // Check if a blank backup file name is provided
        if (restoreFileName != null && restoreFileName.isEmpty()) {
            // Set the file name to null to allow easier comparisons below
            restoreFileName = null;
        }

        // Set the flag if the current user's password is non-blank. Depending on the
        // authentication set-up and operating system, the password may still be
        // required by the
        // psql command even if the authentication method is 'trust'. If the restore is
        // initiated
        // from the command line with the GUI hidden then the password is assumed to be
        // already set
        boolean isPasswordSet = dbControl.isPasswordNonBlank() || (ccddMain.isGUIHidden() && restoreFileName != null);

        // Check if no password is set
        if (!isPasswordSet) {
            // Display the password dialog and obtain the password. Note that the user can
            // enter a
            // blank password (which may be valid)
            CcddServerPropertyDialog dialog = new CcddServerPropertyDialog(ccddMain, ServerPropertyDialogType.PASSWORD);

            // Set the flag if the user selected the Okay button in the password dialog
            isPasswordSet = dialog.isPasswordSet();
        }

        // Check if the user's database password is set (either non-blank or explicitly
        // set to
        // blank)
        if (isPasswordSet) {
            File tempFile = null;
            FileEnvVar[] dataFile = null;

            // Check if the name of the backup file name to restore isn't provided
            if (restoreFileName == null) {
                // Allow the user to select the backup file path + name to load from
                dataFile = new CcddDialogHandler().choosePathFile(ccddMain, ccddMain.getMainFrame(), null, null,
                        new FileNameExtensionFilter[] { new FileNameExtensionFilter(FileExtension.DBU.getDescription(),
                                FileExtension.DBU.getExtensionName()) },
                                false, "Restore Project",
                                ccddMain.getProgPrefs().get(ModifiablePathInfo.DATABASE_BACKUP_PATH.getPreferenceKey(), null),
                                DialogOption.RESTORE_OPTION);
            }
            // The name of the backup file to restore is provided
            else {
                // Create the file class
                dataFile = new FileEnvVar[] { new FileEnvVar(restoreFileName) };
            }

            // Check if a file was chosen
            if (dataFile != null && dataFile[0] != null) {
                boolean isProjectNameValid = projectName != null && !projectName.isEmpty();
                boolean isProjectDescValid  = projectDescription != null && !projectDescription.isEmpty();
                boolean nameDescProvided = isProjectNameValid && isProjectDescValid;
                boolean commentFound = false;
                boolean backupDBUInfoFound = false;
                String backupDBUInfo = "";
                BufferedReader br = null;
                BufferedWriter bw = null;

                try {
                    // Check if the file doesn't exist
                    if (!dataFile[0].exists()) {
                        throw new CCDDException(
                                "Cannot locate backup file '</b>" + dataFile[0].getAbsolutePath() + "'");
                    }

                    boolean inComment = false;
                    boolean inBackupDatabaseInfoTable = false;
                    boolean inUsers = false;
                    boolean isOwnerAdmin = false;
                    String oid = "";
                    String line = null;
                    String commentText = "";

                    // Check if no project owner is provided
                    if (projectOwner == null) {
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
                    while ((line = br.readLine()) != null) {
                        // Check if this line is not a continuance of the database comment
                        if (!inComment && !inBackupDatabaseInfoTable) {
                            // Check if this line creates the plpgsql language
                            if (line.equals("CREATE PROCEDURAL LANGUAGE plpgsql;")
                                    || line.equals("CREATE OR REPLACE PROCEDURAL LANGUAGE plpgsql;")) {
                                // Add the command to first drop the language. This allows backups
                                // created from PostgreSQL versions 8.4 and earlier to be restored
                                // in version 9.0 and subsequent without generating an error
                                line = "DROP LANGUAGE IF EXISTS plpgsql;\nCREATE PROCEDURAL LANGUAGE plpgsql;";

                                // Check if the PostgeSQL version is 9 or higher
                                if (dbControl.getPostgreSQLMajorVersion() > 8) {
                                    // Add the command to drop the language extension; this is
                                    // necessary in order for the language to be dropped in
                                    // PostgreSQL 9+
                                    line = "DROP EXTENSION plpgsql;\n" + line;
                                }
                            }
                            // Check if this line adds a comment to the plpgsql language
                            else if (line.startsWith("COMMENT ON EXTENSION plpgsql")) {
                                // Ignore the comment; this can cause an ownership error and the
                                // comment isn't needed
                                line = "";
                            }
                            // Check if this is the beginning of the command to populate the user
                            // access level table
                            else if (line
                                    .matches("COPY (?:[^\\.]+\\.)?" + InternalTable.USERS.getTableName() + " .+")) {
                                // Set the flag to indicate the following lines contain the user
                                // access level information
                                inUsers = true;
                            }
                            // Check if this line follows the command to populate the user access
                            // level table
                            else if (inUsers) {
                                // Check if this line defines a user's access level
                                if (line.matches("\\d+\\s+[^\\s]+\\s+.+")) {
                                    // Get the OID for this table entry
                                    oid = line.replaceFirst("\\s.*", "");

                                    if (line.matches("\\d+\\s+" + projectOwner + "+\\s+.+")) {
                                        // Set the flag to indicate the project owner is already in
                                        // the user access level table
                                        isOwnerAdmin = true;

                                        // Make the project owner an administrator for the restored
                                        // project
                                        line.replaceFirst("(.+\\s+).+$", "$1" + AccessLevel.ADMIN.getDisplayName());
                                    }
                                }
                                // The last user access level definition was reached
                                else {
                                    // Set the flag so that subsequent lines are not considered a
                                    // user access level definition
                                    inUsers = false;

                                    // Check if the owner for the restored project is not in the
                                    // user access level table
                                    if (!isOwnerAdmin) {
                                        // Add the project owner as an administrator for the
                                        // restored project
                                        line = (Integer.valueOf(oid) + 1) + "\t" + projectOwner + "\t"
                                                + AccessLevel.ADMIN.getDisplayName() + "\n" + line;
                                    }
                                }
                            }
                            // Check if this line is a SQL command that revokes permissions for an
                            // owner other than PUBLIC
                            else if (line.matches("REVOKE .+ FROM .+;\\s*")
                                    && !line.matches("REVOKE .+ FROM PUBLIC;\\s*")) {
                                // Change the original owner to the current user
                                line = line.replaceFirst("FROM .+;", "FROM " + projectOwner + ";");
                            }
                            // Check if this line is a SQL command that grants permissions to an
                            // owner other than PUBLIC
                            else if (line.matches("GRANT .+ TO .+;\\s*") && !line.matches("GRANT .+ TO PUBLIC;\\s*")) {
                                // Change the original owner to the current user
                                line = line.replaceFirst("TO .+;", "TO " + projectOwner + ";");
                            }

                            // Check if the database comment hasn't been found already and that the
                            // line contains the comment information
                            if (!commentFound
                                    && line.matches("COMMENT ON DATABASE .+ IS '" + CCDD_PROJECT_IDENTIFIER + ".+")) {
                                commentFound = true;
                                inComment = true;
                            } else if (!commentFound && line.matches("COPY public.__dbu_info .+")) {
                                /* Here is where I look for dbu info */
                                inBackupDatabaseInfoTable = true;
                            }
                        }
                        
                        if (inBackupDatabaseInfoTable && !line.matches("COPY public.__dbu_info .+")) {
                            backupDBUInfo = line;
                            inBackupDatabaseInfoTable = false;
                            backupDBUInfoFound = true;
                        }

                        // Check if this line is the beginning or a continuance of the database
                        // comment
                        if (inComment) {
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
                        if((!line.matches("SET lock_timeout .+"))
                                && (!line.matches("-- Dumped .+"))
                                && (!line.matches("SET idle_in_transaction_session_timeout .+"))
                                && (!line.matches("SET xmloption.+"))
                                && (!line.matches("SET row_security .+"))
                                && (!line.matches("    AS integer"))) {
                            bw.write(line + "\n");
                        }
                    }

                    // Check if the project name and description exist
                    if (commentFound || nameDescProvided || backupDBUInfoFound) {
                        String projectAdministrator = "";

                        // Flush the output file buffer so that none of the contents are lost
                        bw.flush();

                        // Check if the database name and description aren't provided explicitly,
                        // and a comment was extracted from the backup file
                        if (!nameDescProvided && commentFound) {
                            // Extract the database name from the comment
                            String databaseName = commentText.replaceAll(
                                    "(?s)COMMENT ON DATABASE (.+) IS '" + CCDD_PROJECT_IDENTIFIER + ".+", "$1");
                            commentText = commentText.replaceAll(
                                    "(?s)COMMENT ON DATABASE .+ IS '" + CCDD_PROJECT_IDENTIFIER + "(.+)';$", "$1");

                            // Split the line read from the file in order to get the project name
                            // and description
                            String[] comment = dbControl.parseDatabaseComment(databaseName, commentText);

                            // Extract the project name (with case preserved) and description, and
                            // set the flag indicating the comment is located
                            projectName = comment[DatabaseComment.PROJECT_NAME.ordinal()];
                            projectAdministrator = comment[DatabaseComment.ADMINS.ordinal()];
                            projectDescription = comment[DatabaseComment.DESCRIPTION.ordinal()];
                        } else if (!nameDescProvided && backupDBUInfoFound) {
                            /* Split the line read from the file in order to get the project information */
                            String[] comment = new String[4];
                            String[] temp = backupDBUInfo.replace("\n", "").split("\t");
                            System.arraycopy(temp, 1, comment, 0, 4);
                            comment[0] = comment[0].substring(comment[0].length()-1);
                            // Extract the project name (with case preserved) and description, and
                            // set the flag indicating the comment is located
                            projectName = comment[DatabaseComment.PROJECT_NAME.ordinal()];
                            projectAdministrator = comment[DatabaseComment.ADMINS.ordinal()];
                            projectDescription = comment[DatabaseComment.DESCRIPTION.ordinal()];
                        } 

                        // Check if the project owner isn't in the administrator list embedded in
                        // the database comment
                        if (!projectAdministrator.matches("(?:^|,)" + projectOwner + "(?:,|$)")) {
                            // Add the project owner as an administrator
                            projectAdministrator += (projectAdministrator.isEmpty() ? "" : ",") + projectOwner;
                        }

                        // Check if the backup file is restored via the command line
                        if (restoreFileName != null) {
                            // Restore the database from the temporary file. This file has the line
                            // that disables creation of the database comment, which is handled
                            // when the restored database is created
                            dbControl.restoreDatabase(projectName, projectOwner, projectAdministrator,
                                    projectDescription, tempFile, true);
                        }
                        // The the backup file is restored via the GUI
                        else {
                            // Restore the database from the temporary file as a background
                            // process. This file has the line that disables creation of the
                            // database comment, which is handled when the restored database is
                            // created
                            dbControl.restoreDatabaseInBackground(projectName, projectOwner, projectAdministrator,
                                    projectDescription, tempFile, false);
                        }

                        // Store the data file path in the program preferences backing store
                        storePath(ccddMain, dataFile[0].getAbsolutePathWithEnvVars(), true,
                                ModifiablePathInfo.DATABASE_BACKUP_PATH);
                    } else {
                        // The project owner, name, and description don't exist
                        throw new CCDDException(
                                "File '</b>" + dataFile[0].getAbsolutePath() + "'<b> is not a backup file");
                    }
                } catch (CCDDException ce) {
                    // Inform the user that the backup file error occurred
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(), "<html><b>" + ce.getMessage(),
                            "File Error", ce.getMessageType(), DialogOption.OK_OPTION);
                } catch (Exception e) {
                    // Inform the user that the backup file cannot be read
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html><b>Cannot read backup file '</b>" + dataFile[0].getAbsolutePath()
                                    + "<b>'; cause '</b>" + e.getMessage() + "<b>'",
                            "File Error", JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
                } finally {
                    try {
                        // Check if the input file is open
                        if (br != null) {
                            // Close the input file
                            br.close();
                        }
                    } catch (IOException ioe) {
                        // Inform the user that the input file cannot be closed
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                "<html><b>Cannot close backup file '</b>" + dataFile[0].getAbsolutePath() + "<b>'",
                                "File Warning", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
                    } finally {
                        try {
                            // Check if the output file is open
                            if (bw != null) {
                                // Close the output file
                                bw.close();
                            }
                        } catch (IOException ioe) {
                            // Inform the user that the output file cannot be closed
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                    "<html><b>Cannot close backup file '</b>" + tempFile.getAbsolutePath() + "<b>'",
                                    "File Warning", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Import one or more files, creating new tables and optionally replacing
     * existing ones. The file(s) may contain definitions for more than one table.
     * This method is executed in a separate thread since it can take a noticeable
     * amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the
     * database method completes execution
     *
     * @param dataFile                   array of files to import
     * 
     * @param importingEntireDatabase    Is an entire database being imported?
     *
     * @param backupFirst                true to create a backup of the database
     *                                   before importing tables
     *
     * @param replaceExisting            true to replace a table that already exists
     *                                   in the database
     *
     * @param appendExistingFields       true to append the existing data fields for
     *                                   a table (if any) to the imported ones (if
     *                                   any). Only valid when replaceExisting is
     *                                   true
     *
     * @param useExistingFields          true to use an existing data field in place
     *                                   of the imported ones if the field names
     *                                   match. Only valid when replaceExisting and
     *                                   appendExistingFields are true
     *
     * @param openEditor                 true to open a table editor for each
     *                                   imported table
     *
     * @param ignoreErrors               true to ignore all errors in the import
     *                                   file
     *
     * @param replaceExistingMacros      true to replace the values for existing
     *                                   macros
     *
     * @param replaceExistingGroups      true to replace existing group definitions
     * 
     * @param replaceExistingAssociations true to overwrite internal associations with
     *                                    those from the import file
     * 
     * @param deleteNonExistingFiles     true to delete any tables from the database
     *                                   that were not part of the import
     *                                   
     * @param importFileType             The extension for what type of file is being imported
     * 
     * @param dialogType                 The type of file import being performed
     *
     * @param parent                     GUI component over which to center any
     *                                   error dialog
     *********************************************************************************************/
    protected boolean importFileInBackground(final FileEnvVar[] dataFile, final boolean importingEntireDatabase,
            final boolean backupFirst, final boolean replaceExisting, final boolean appendExistingFields,
            final boolean useExistingFields, final boolean openEditor, final boolean ignoreErrors,
            final boolean replaceExistingMacros, final boolean replaceExistingGroups, final boolean replaceExistingAssociations,
            final boolean deleteNonExistingFiles, final FileExtension importFileType, final ManagerDialogType dialogType,
            final Component parent) {
        /* Execute the import operation in the background */
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand() {
            /**************************************************************************************
             * Import the selected data
             *************************************************************************************/
            @Override
            protected void execute() {
                createSnapshotDirectory(parent);
                
                if ((importFileType == FileExtension.JSON) || (importFileType == FileExtension.CSV) ||
                        (importFileType == FileExtension.C_HEADER)) {
                    errorFlag = prepareJSONOrCSVImport(dataFile, importingEntireDatabase, backupFirst, replaceExisting, appendExistingFields,
                            useExistingFields, openEditor, ignoreErrors, replaceExistingMacros, replaceExistingAssociations,
                            replaceExistingGroups, deleteNonExistingFiles, importFileType, dialogType, parent);
                } else {
                    List<FileEnvVar> dataFiles = Arrays.asList(dataFile);
                    ManagerDialogType importType = null;
                    if (importFileType == FileExtension.EDS) {
                        importType = ManagerDialogType.IMPORT_EDS;
                    } else {
                        importType = ManagerDialogType.IMPORT_XTCE;
                    }
                    /* Import the selected table(s) */
                    importFiles(dataFiles, backupFirst, replaceExisting, appendExistingFields, useExistingFields,
                           openEditor, ignoreErrors, replaceExistingMacros, replaceExistingAssociations, replaceExistingGroups,
                           importType, parent);
                }
                
                deleteSnapshotDirectories(parent);
            }

            /**************************************************************************************
             * Import command complete
             *************************************************************************************/
            @Override
            protected void complete() {
                /* Check if the user didn't cancel import */
                try {
                    if (!haltDlg.isHalted()) {
                        /* Close the cancellation dialog */
                        haltDlg.closeDialog();
                    }
                    /* Import was canceled */
                    else {
                        eventLog.logEvent(EventLogMessageType.STATUS_MSG, new StringBuilder("Import terminated by user"));
                    }

                    haltDlg = null;
                } catch (NullPointerException e) {
                    /*
                     * There is a null pointer that occurs when no import is completed due to there
                     * being no change in the files. This will ignore that
                     */
                }
            }
        });
        return errorFlag;
    }

    /**********************************************************************************************
     * Import one or more files, creating new tables and optionally replacing
     * existing ones. The file(s) may contain definitions for more than one table.
     * This method is executed in a separate thread since it can take a noticeable
     * amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the
     * database method completes execution
     *
     * @param dataFiles             array of files to import
     *
     * @param backupFirst           true to create a backup of the database before
     *                              importing tables
     *
     * @param replaceExistingTables true to replace a table that already exists in
     *                              the database
     *
     * @param appendExistingFields  true to append the existing data fields for a
     *                              table (if any) to the imported ones (if any).
     *                              Only valid when replaceExisting is true
     *
     * @param useExistingFields     true to use an existing data field in place of
     *                              the imported ones if the field names match. Only
     *                              valid when replaceExisting and
     *                              appendExistingFields are true
     *
     * @param openEditor            true to open a table editor for each imported
     *                              table
     *
     * @param ignoreErrors          true to ignore all errors in the import file
     *
     * @param replaceExistingMacros true to replace the values for existing macros
     * 
     * @param replaceExistingAssociations true to overwrite internal associations with
     *                                    those from the import file
     *
     * @param replaceExistingGroups true to replace existing group definitions
     *
     * @param parent                GUI component over which to center any error
     *                              dialog
     *
     * @return true if the import operation completes successfully
     *********************************************************************************************/
    protected boolean importFiles(List<FileEnvVar> dataFiles, boolean backupFirst, boolean replaceExistingTables,
            boolean appendExistingFields, boolean useExistingFields, boolean openEditor, boolean ignoreErrors,
            boolean replaceExistingMacros, boolean replaceExistingAssociations, boolean replaceExistingGroups, CcddConstants.ManagerDialogType fileType,
            Component parent) {
        /* Initialize any needed variables */
        boolean errorFlag = false;
        String filePath = null;
        CcddImportExportInterface ioHandler = null;
        List<TableDefinition> allTableDefinitions = new ArrayList<TableDefinition>();
        List<String> duplicateDefinitions = new ArrayList<String>();
        List<String> fileNames = new ArrayList<String>();

        /* Store the current table type, data type, macro, reserved message ID, and data
         * field information in case it needs to be restored
         */
        List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitionsCopy();
        List<String[]> originalDataTypes = CcddUtilities.copyListOfStringArrays(dataTypeHandler.getDataTypeData());
        String[][] originalInputTypes = CcddUtilities.copyArrayOfStringArrays(inputTypeHandler.getCustomInputTypeData());
        List<String[]> originalMacros = CcddUtilities.copyListOfStringArrays(macroHandler.getMacroData());
        List<String[]> originalReservedMsgIDs = CcddUtilities.copyListOfStringArrays(rsvMsgIDHandler.getReservedMsgIDData());
        List<String[]> originalDataFields = fieldHandler.getFieldDefnsFromInfo();

        /* Initialize the macro update lists */
        macroHandler.initializeMacroUpdates();

        /* Step through each data file to import */
        for (FileEnvVar dataFile : dataFiles) {
            /* Add the file name, with path, to the list */
            fileNames.add(dataFile.getAbsolutePath());
        }

        /* Add event to log indicating that importing has begun */
        eventLog.logEvent(EventLogMessageType.STATUS_MSG, new StringBuilder("Importing table(s) from '")
                .append(CcddUtilities.convertArrayToString(fileNames.toArray(new String[0]))).append("'"));

        /* Load the group information from the database */
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, parent);

        /* Create a reference to a table editor dialog list */
        tableEditorDlgs = new ArrayList<CcddTableEditorDialog>();

        /* Check if the user elected to back up the project before importing tables */
        if (backupFirst) {
            /* Back up the project database */
            backupDatabaseToFile(false);
        }

        /* Process the files to be imported */
        try {
            int numFilesProcessed = 0;

            /* Create a save point in case an error occurs while creating or modifying a
             * table
             */
            dbCommand.createSavePoint(parent);

            /* Based on what type of import the user selected, create an appropriate handler
             */
            if (fileType == ManagerDialogType.IMPORT_CSV) {
                ioHandler = new CcddCSVHandler(ccddMain, groupHandler, parent);
            } else if (fileType == ManagerDialogType.IMPORT_EDS) {
                ioHandler = new CcddEDSHandler(ccddMain, parent);
            } else if (fileType == ManagerDialogType.IMPORT_JSON) {
                ioHandler = new CcddJSONHandler(ccddMain, groupHandler, parent);
            } else if (fileType == ManagerDialogType.IMPORT_XTCE) {
                ioHandler = new CcddXTCEHandler(ccddMain, parent);
            } else {
                /* Should never reach this point */
                throw new CCDDException("Unrecognized file type selection");
            }

            /* Step through each file checking for any input type definition available */
            for (FileEnvVar file : dataFiles) {
                /* Check if the file exist */
                if (!file.exists()) {
                    throw new CCDDException("Cannot locate file");
                }
                
                /* Get the file path */
                filePath = file.getAbsolutePath();
                
                /* Check if the files being imported are JSON/CSV files */
                if ((filePath.endsWith(FileExtension.JSON.getExtension())) || (filePath.endsWith(FileExtension.CSV.getExtension()))) {
                    String fileName = filePath.substring(filePath.lastIndexOf("/")+1).split("\\.")[0];
                    
                    /* Import any macro/table type definition found */
                    if (fileName.equals(FileNames.MACROS.No_Extension())) {
                        ioHandler.importTableInfo(file, ImportType.IMPORT_ALL, ignoreErrors, replaceExistingMacros, replaceExistingTables);
                        
                        /* Import any input/data type definitions found */
                    } else if (fileName.equals(FileNames.TABLE_INFO.No_Extension())) {
                        ioHandler.importTableInfo(file, ImportType.IMPORT_ALL, ignoreErrors, replaceExistingMacros, replaceExistingTables);
                        ioHandler.importInputTypes(file, ImportType.IMPORT_ALL, ignoreErrors);
                        
                        /* Import any internal table found */
                    } else if (fileName.equals(FileNames.TELEM_SCHEDULER.No_Extension()) ||
                            fileName.equals(FileNames.APP_SCHEDULER.No_Extension()) ||
                            fileName.equals(FileNames.SCRIPT_ASSOCIATION.No_Extension())) {
                        ioHandler.importInternalTables(file, ImportType.IMPORT_ALL, ignoreErrors, replaceExistingAssociations);
                    } else {
                        /* Import the table definition(s) from the file */
                        ioHandler.importFromFile(file, ImportType.IMPORT_ALL, null, ignoreErrors, replaceExistingMacros,
                                replaceExistingGroups, replaceExistingTables);
                    }
                } else {
                    /* Import the table definition(s) from the file */
                    ioHandler.importFromFile(file, ImportType.IMPORT_ALL, null, ignoreErrors, replaceExistingMacros,
                            replaceExistingGroups, replaceExistingTables);
                }
                
                if (ioHandler.getTableDefinitions() != null) {
                    /* Step through each table definition from the import file. This for loop also guards against trying to import
                     * the same file more than once.
                     */
                    for (TableDefinition tableDefn : ioHandler.getTableDefinitions()) {
                        /* Indicates if this file has already been imported */
                        boolean isDuplicate = false;
                        
                        /* Check if the user elected to append any new data fields to any existing ones for a table */
                        if (appendExistingFields) {
                            /* Add the imported data field(s) to the table */
                            addImportedDataField(tableDefn, tableDefn.getName(), useExistingFields);
                        }
                        
                        /* Step through each table definition that has already been imported and added to the list 
                         * and compare it to the table definition that is about to be imported to ensure no duplicates 
                         * are added */
                        for (TableDefinition existingDefn : allTableDefinitions) {
                            /* Check if the table is already defined in the list */
                            if (tableDefn.getName().equals(existingDefn.getName())) {
                                /* Add the table name and associated file name to the list of duplicates */
                                duplicateDefinitions.add(tableDefn.getName() + " (file: " + file.getName() + ")");
    
                                /* Set the flag indicating the table definition is a duplicate and stop searching */
                                isDuplicate = true;
                                break;
                            }
                        }
                        
                        /* Check if the table is not already defined */
                        if (!isDuplicate) {
                            /* Add the table definition to the list */
                            allTableDefinitions.add(tableDefn);
                        }
                    }
                }

                /* Check if the halt dialog is active (import operation is executed in the background) */
                if (haltDlg != null) {
                    /* Check if the user canceled verification */
                    if (haltDlg.isHalted()) {
                        throw new CCDDException();
                    }
                    
                    /* Update the progress bar */
                    haltDlg.updateProgressBar("Reading import file " + file.getName(),
                            haltDlg.getNumDivisionPerStep() * numFilesProcessed);
                    numFilesProcessed++;
                }
            }

            /* Create the data tables from the imported table definitions from all files */
            createTablesFromDefinitions(allTableDefinitions, replaceExistingTables, replaceExistingMacros, openEditor,
                    groupHandler, parent);

            /* Check if any new application scheduler data has been added */
            if (ioHandler.getAppSchedulerData() != null) {
                /* Store the application scheduler data */
                dbTable.storeInformationTable(InternalTable.APP_SCHEDULER, ioHandler.getAppSchedulerData(), null,
                        parent);
            }

            /* Check if any new telemetry messages have been added */
            if (ioHandler.getTlmSchedulerData() != null) {
                /* Store the telemetry messages */
                dbTable.storeInformationTable(InternalTable.TLM_SCHEDULER, ioHandler.getTlmSchedulerData(), null,
                        parent);
            }
            
            /* Check if any new script associations are being added */
            if (ioHandler.getScriptAssociations() != null) {
                /* Step through each association */
                for (int index = 0; index < ioHandler.getScriptAssociations().size(); index++) {
                    /* Format the table members for storage in the database */
                    String[] assn = ioHandler.getScriptAssociations().get(index);
                    assn[AssociationsColumn.MEMBERS.ordinal()] = CcddScriptHandler
                            .convertAssociationMembersFormat(assn[AssociationsColumn.MEMBERS.ordinal()], true);
                    ioHandler.getScriptAssociations().set(index, assn);
                }

                /* Store the script associations */
                dbTable.storeInformationTable(InternalTable.ASSOCIATIONS, ioHandler.getScriptAssociations(), null, parent);
            }

            /* Release the save point. This must be done within a transaction block, so it
             * must be done prior to the commit below
             */
            dbCommand.releaseSavePoint(parent);

            /* Commit the change(s) to the database */
            dbControl.getConnection().commit();

            /* Update any open editor's data type columns to include the new table(s), if applicable */
            dbTable.updateDataTypeColumns(parent);

            /* Restore the root structure table, variable path and offset, and command
             * lists, and the variable, command, and message ID references in case any input
             * types were added
             */
            dbTable.updateListsAndReferences(parent);

            /* Update the table type handler with the input type changes */
            tableTypeHandler.updateInputTypes(null);

            /* If open, update the table type editor's input type column combo box lists to
             * include the new input type(s), if applicable
             */
            dbTable.updateInputTypeColumns(null, parent);

            /* Add event to log indicating that the import completed successfully */
            eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Table import completed successfully"));

            /* Check if any duplicate table definitions were detected */
            if (!duplicateDefinitions.isEmpty()) {
                /* Inform the user that one or more duplicate table definitions were detected */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Ignored the following duplicate table definition(s):</b><br>" + CcddUtilities
                                .convertArrayToStringTruncate(duplicateDefinitions.toArray(new String[0])),
                        "Duplicate Table(s)", JOptionPane.INFORMATION_MESSAGE, DialogOption.OK_OPTION);
            }

            /* Step through each table editor dialog created during the import operation */
            for (CcddTableEditorDialog tableEditorDlg : tableEditorDlgs) {
                /* Enable the editor dialog's command menu items */
                tableEditorDlg.setControlsEnabled(true);
            }
        } catch (IOException ioe) {
            /* Inform the user that the data file cannot be read */
            new CcddDialogHandler().showMessageDialog(parent,
                    "<html><b>Cannot read import file '</b>" + filePath + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
            errorFlag = true;
        } catch (CCDDException cse) {
            /* Check if this is an internally generated exception and that an error message is provided */
            if (cse instanceof CCDDException && !cse.getMessage().isEmpty()) {
                /* Inform the user that an error occurred importing the table(s) */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Cannot import from file '</b>" + filePath + "<b>': " + cse.getMessage(),
                        "Import Error", ((CCDDException) cse).getMessageType(), DialogOption.OK_OPTION);
            }
            errorFlag = true;
        } catch (Exception e) {
            /* Display a dialog providing details on the unanticipated error */
            CcddUtilities.displayException(e, parent);
            e.printStackTrace();
            errorFlag = true;
        }

        /* Check if an error occurred */
        if (errorFlag) {
            try {
                /* Revert any changes made to the database */
                dbCommand.rollbackToSavePoint(parent);
            } catch (SQLException se) {
                /* Inform the user that rolling back the changes failed */
                eventLog.logFailEvent(parent, "Cannot revert changes to project; cause '" + se.getMessage() + "'",
                        "<html><b>Cannot revert changes to project");
            }

            /* Step through each table editor dialog created during the import operation */
            for (CcddTableEditorDialog tableEditorDlg : tableEditorDlgs) {
                /* Close the editor dialog */
                tableEditorDlg.closeFrame();
            }

            /* Restore the table types, data types, macros, reserved message IDs, and data
             * fields to the values prior to the import operation.
             */
            tableTypeHandler.setTypeDefinitions(originalTableTypes);
            dataTypeHandler.setDataTypeData(originalDataTypes);
            inputTypeHandler.setInputTypeData(originalInputTypes);
            fieldHandler.setFieldInformationFromDefinitions(originalDataFields);
            macroHandler.setMacroData(originalMacros);
            rsvMsgIDHandler.setReservedMsgIDData(originalReservedMsgIDs);
            dbTable.updateListsAndReferences(parent);

            /* Check if the table type editor is open */
            if (ccddMain.getTableTypeEditor() != null && ccddMain.getTableTypeEditor().isShowing()) {
                /* Remove any table types that were created during the import process, since
                 * these are now invalid
                 */
                ccddMain.getTableTypeEditor().removeInvalidTabs();
            }
            /* Add event to log indicating that the import did not complete successfully */
            eventLog.logEvent(EventLogMessageType.FAIL_MSG, new StringBuilder("Table import failed to complete"));
        }
        return errorFlag;
    }

    /**********************************************************************************************
     * Check the supplied list of tables in the order they are referenced for the
     * specified prototype structure table name. If not present add the table to the
     * list at the current insertion index. Update the insertion index to the
     * immediately after the specified table
     *
     * @param protoStructName   name of the prototype structure table
     *
     * @param orderedTableNames current list of table names in the order referenced
     *                          (the prototype structure is added if not already in
     *                          the list)
     *
     * @param insertionIndex    index at which to insert the table name
     *
     * @return The updated insertion point
     *********************************************************************************************/
    private int getStructureInsertionIndex(String protoStructName, List<String> orderedTableNames, int insertionIndex) {
        // Get the index of the structure in the list of reference ordered table names
        int protoStructIndex = orderedTableNames.indexOf(protoStructName);

        // Check if the structure isn't in the list
        if (protoStructIndex == -1) {
            // Add the structure to the list at the current insertion point (the end of the
            // list if
            // no insertion point has been set)
            orderedTableNames.add((insertionIndex == -1 ? orderedTableNames.size() : insertionIndex), protoStructName);

            // Check if an insertion point has been set
            if (insertionIndex != -1) {
                // Increment the insertion point to account for a table being added to the list
                insertionIndex++;
            }
        }
        // Check if the current insertion point is prior to the prototype's place in the
        // list
        else if (insertionIndex != -1 && protoStructIndex > insertionIndex) {
            // Set the insertion index to after this structure
            insertionIndex = protoStructIndex + 1;
        }

        return insertionIndex;
    }

    /**********************************************************************************************
     * Reorder the list of table definitions so that tables referenced as a data
     * type or in a sizeof() call appear in the list prior to the table that
     * references them
     *
     * @param tableDefinitions list of table definitions for the table(s) to create
     *
     * @return The list of table definitions, reordered so that tables referenced as
     *         a data type or in a sizeof() call appear in the list prior to the
     *         table that references them
     *********************************************************************************************/
    private List<TableDefinition> orderTableDefinitionsByReference(List<TableDefinition> tableDefinitions) {
        List<TableDefinition> orderedTableDefinitions = new ArrayList<TableDefinition>();
        List<String> orderedTableNames = new ArrayList<String>();

        // Sort the table definitions by the table path+names. This is to ensure a prototype isn't
        // created as an ancestor when a child already exists that defines the prototype (if the
        // actual prototype exists or the flag to replace existing tables is set then his isn't necessary)
        Collections.sort(tableDefinitions, new Comparator<Object>() {
            /**************************************************************************************
             * Compare table names
             *
             * @param tblInfo1 first table's information
             *
             * @param tblInfo2 second table's information
             *
             * @return -1 if the first table's name is lexically less than the second
             *         table's name; 0 if the two table names are the same; 1 if the first
             *         table's name is lexically greater than the second table's name
             *************************************************************************************/
            @Override
            public int compare(Object tblInfo1, Object tblInfo2) {
                return ((TableDefinition) tblInfo1).getName().compareTo(((TableDefinition) tblInfo2).getName());
            }
        });

        // Step through each table definition
        for (TableDefinition tableDefn : tableDefinitions) {
            int insertIndex = -1;

            // Check if the table is an instance (child)
            if (tableDefn.getName().contains(",")) {
                // Get the index in the list for the table's prototype
                insertIndex = orderedTableNames.indexOf(TableInformation.getPrototypeName(tableDefn.getName()));
            }

            // Check if the table is a prototype or is a child whose prototype isn't in the
            // list
            if (insertIndex == -1) {
                int column = 0;

                // Get the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                // Step through each cell value in the table
                for (String cellValue : tableDefn.getData()) {
                    // Check if the cell value is present
                    if (cellValue != null && !cellValue.isEmpty()) {
                        // Check if this is a structure reference in the data type column
                        if (column == CcddTableTypeHandler.getVisibleColumnIndex(
                                typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT))
                                && !dataTypeHandler.isPrimitive(cellValue)) {
                            // Insert the referenced structure, if not already in the list, and
                            // update the insertion index
                            insertIndex = getStructureInsertionIndex(cellValue, orderedTableNames, insertIndex);
                        }
                        // This isn't a structure reference in the data type column
                        else {
                            // Step through each macro referenced in the cell value
                            for (String macroName : macroHandler.getReferencedMacros(cellValue)) {
                                // Step through each structure referenced by the macro
                                for (String structureName : macroHandler.getStructureReferences(macroName)) {
                                    // Insert the referenced structure, if not already in the list,
                                    // and update the insertion index
                                    insertIndex = getStructureInsertionIndex(structureName, orderedTableNames,
                                            insertIndex);
                                }
                            }
                        }
                    }

                    // Update the table data column index. When the end of the row is reached then
                    // reset to the first column
                    column++;

                    if (column == typeDefn.getColumnCountVisible()) {
                        column = 0;
                    }
                }
            }

            // Check if the table hasn't been added to the list already
            if (!orderedTableNames.contains(tableDefn.getName())) {
                // Add the table name to the list, inserted so that any table it references is
                // prior to it in the list
                orderedTableNames.add((insertIndex == -1 ? orderedTableNames.size() : insertIndex),
                        tableDefn.getName());
            }
        }

        // Step through each table name in the order it's referenced
        for (String tableName : orderedTableNames) {
            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions) {
                // Check if the table names match
                if (tableName.equals(tableDefn.getName())) {
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
     * @param tableDefinitions      list of table definitions for the table(s) to
     *                              create
     *
     * @param replaceExistingTables true to replace a table that already exists in
     *                              the database
     *
     * @param replaceExistingMacros true to replace the values for existing macros
     *
     * @param openEditor            true to open a table editor for each imported
     *                              table
     *
     * @param groupHandler          group handler reference
     *
     * @param parent                GUI component over which to center any error
     *                              dialog
     *
     * @throws CCDDException If the table path name is invalid or the table cannot
     *                       be created from the table definition
     *********************************************************************************************/
    private void createTablesFromDefinitions(List<TableDefinition> tableDefinitions, boolean replaceExistingTables,
            boolean replaceExistingMacros, boolean openEditor, CcddGroupHandler groupHandler, final Component parent)
            throws CCDDException {
        boolean prototypesOnly = true;
        List<String> skippedTables = new ArrayList<String>();

        // Store the current group information
        ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.GROUPS,
                groupHandler.getGroupDefnsFromInfo(), null, parent);

        // Check if the user elected to enable replacement of existing macro values
        if (replaceExistingMacros) {
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
        CcddTableTreeHandler tableTree = new CcddTableTreeHandler(ccddMain, TableTreeType.TABLES, parent);
        List<String> allTables = tableTree.getTableTreePathList(null);

        // Check if the cancel import dialog is present
        if (haltDlg != null) {
            // Initialize the progress bar within-step total
            haltDlg.setItemsPerStep(tableDefinitions.size());
        }

        // Perform two passes; first to process prototype tables, and second to process child tables
        for (int loop = 1; loop <= 2; loop++) {
            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions) {
                // Check if the table import was canceled by the user
                if (haltDlg != null && haltDlg.isHalted()) {
                    throw new CCDDException();
                }

                // Check if this is a prototype table and this is the first pass,
                // or if this is a child table and this is the second pass
                if (tableDefn.getName().contains(".") != prototypesOnly) {
                    // Check if the cancel import dialog is present
                    if (haltDlg != null) {
                        // Update the progress bar
                        haltDlg.updateProgressBar(null, -1);
                    }

                    boolean isChildOfNonRoot = false;

                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                    // Get the number of table columns
                    int numColumns = typeDefn.getColumnCountVisible();

                    TableInformation tableInfo;

                    // Load the old data, not the imported data, so that it can be used for comparison
                    if (dbTable.isTableExists(tableDefn.getName(), parent)) {
                        tableInfo = dbTable.loadTableData(tableDefn.getName(), true, true, ccddMain.getMainFrame());
                    } else if (tableDefn.getName().contains(".")) {
                        /* If the table that has been modified is an instance of a prototype then there is no table in the postgres
                         * database that shares its name and stores its data. Instead the information that varies from the prototype
                         * table is stored in the internal _values table. In this situation we can actually just pull the information
                         * of the prototype table and use it to create the commands required to update the database.
                         */
                        /* Load the information of the prototype */
                        int index = tableDefn.getName().lastIndexOf(",")+1;
                        String ancestor = tableDefn.getName().substring(index).split("\\.")[0];
                        if (dbTable.isTableExists(ancestor, parent)) {
                            tableInfo = dbTable.loadTableData(ancestor, true, true, ccddMain.getMainFrame());
                        } else {
                            // Create the table information for the new table
                            tableInfo = new TableInformation(tableDefn.getTypeName(), tableDefn.getName(),
                                                             new String[0][0], tableTypeHandler.getDefaultColumnOrder(
                                                             tableDefn.getTypeName()), tableDefn.getDescription(),
                                                             fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));
                        }
                        /* Keep the name of the instance even though we are going to use the info of the prototype.
                         * The prototype info is only needed for comparison purposes and we do not want to make
                         * any unwanted changes to it as it would update all instances of this prototype within 
                         * the database.
                         */
                        tableInfo.setTablePath(tableDefn.getName());
                    } else {
                        // Create the table information for the new table, but with no data
                        tableInfo = new TableInformation(tableDefn.getTypeName(), tableDefn.getName(),
                                new String[0][0], tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                tableDefn.getDescription(),
                                fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));

                    }
                    // Add the fields from the table to be created, old fields are dropped
                    tableInfo.setFieldInformation(fieldHandler.getFieldInformationFromDefinitions(
                            tableDefn.getDataFields()));
                    // Add the description from the table to be created, old description is dropped 
                    tableInfo.setDescription(tableDefn.getDescription());

                    // Check if the new table is not a prototype. The prototype for the table and
                    // each of its ancestors need to be created if these don't exist already
                    if (!tableInfo.isPrototype()) {
                        // Get the table's root table name
                        String rootTable = tableInfo.getRootTable();

                        // Set the flag to indicate if this is a child of an existing (in the
                        // database) prototype structure that isn't also a root structure; i.e., it
                        // only defines the prototype for other instances of the child's prototype,
                        // and isn't a legitimate instance itself
                        isChildOfNonRoot = !dbTable.isRootStructure(rootTable);

                        // Check if the table wasn't recognized as a root table because it doesn't
                        // exist in the database. It's root status must be determined by it being
                        // referenced in the imported table definitions
                        if (isChildOfNonRoot && !dbTable.isTableExists(rootTable, ccddMain.getMainFrame())) {
                            boolean isFound = false;

                            // Step through each table definition
                            for (TableDefinition tblDefn : tableDefinitions) {
                                // Check if this definition isn't the one currently being processed
                                // (so as not to it compare to itself) and if this table's path
                                // contains a reference to the current table's root
                                if (!tblDefn.equals(tableDefn)
                                        && rootTable.matches(".+," + tableDefn.getName() + "\\..+")) {
                                    // Set the flag to indicate that the table is referenced within
                                    // another table definitions's path, making the current table a
                                    // child of a prototype (but not a root) table, and stop
                                    // searching
                                    isFound = true;
                                    break;
                                }
                            }

                            // Check if no reference to the table's root exists in the table
                            // definitions
                            if (!isFound) {
                                // Since no reference to the table's root was found in the other
                                // table definitions then the root is a true root table, and not
                                // just a prototype
                                isChildOfNonRoot = false;
                            }
                        }

                        // Break the path into the individual structure variable references
                        String[] ancestors = tableInfo.getTablePath().split(",");

                        // Step through each structure table referenced in the path of the new
                        // table
                        for (int index = ancestors.length - 1; index >= 0; index--) {
                            // Split the ancestor into the data type (i.e., structure name) and
                            // variable name
                            String[] typeAndVar = ancestors[index].split("\\.");

                            // Check if the ancestor prototype table doesn't exist.
                            if (!dbTable.isTableExists(typeAndVar[0], ccddMain.getMainFrame())) {
                                TableInformation ancestorInfo;
                                
                                if (!dbTable.isTableExists(typeAndVar[0], ccddMain.getMainFrame())) {
                                    // Create the table information for the new prototype table
                                    ancestorInfo = new TableInformation(tableDefn.getTypeName(),
                                                                        typeAndVar[0], new String[0][0],
                                                                        tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                                        "", new ArrayList<FieldInformation>());
                                } else {
                                    TableInformation ancestorPreInfo = dbTable.loadTableData(typeAndVar[0], true, false,
                                            ccddMain.getMainFrame());
                                    
                                    // Create the table information for the new prototype table
                                    ancestorInfo = new TableInformation(ancestorPreInfo.getType(),
                                            ancestorPreInfo.getTablePath(), new String[0][0],
                                            tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                            ancestorPreInfo.getDescription(), new ArrayList<FieldInformation>());
                                }

                                // Check if this is the child table and not one of its ancestors
                                if (index == ancestors.length - 1) {
                                    // Create a list to store a copy of the cell data
                                    List<String> protoData = new ArrayList<String>(tableDefn.getData());

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo, protoData, numColumns,
                                            replaceExistingTables, openEditor, allTables, parent)) {
                                        // Add the skipped table to the list
                                        skippedTables.add(ancestorInfo.getTablePath());
                                    }
                                    // The table was created
                                    else {
                                        // Add the table name to the list of existing tables
                                        allTables.add(ancestorInfo.getTablePath());
                                    }
                                }
                                // This is an ancestor of the child table
                                else {
                                    // Split the ancestor into the data type (i.e., structure name)
                                    // and variable name
                                    typeAndVar = ancestors[index + 1].split("\\.|$", -1);

                                    // Add the variable reference to the new table
                                    String[] rowData = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(rowData, "");
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn
                                            .getColumnNameByInputType(DefaultInputType.VARIABLE))] = typeAndVar[1];
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(
                                            DefaultInputType.PRIM_AND_STRUCT))] = typeAndVar[0];

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo, Arrays.asList(rowData), numColumns,
                                            replaceExistingTables, openEditor, allTables, parent)) {
                                        // Add the skipped table to the list
                                        skippedTables.add(ancestorInfo.getTablePath());
                                    }
                                    // The table was created
                                    else {
                                        // Add the table name to the list of existing tables
                                        allTables.add(ancestorInfo.getTablePath());
                                    }
                                }
                            }
                        }

                        // Check if this is the child of a root structure (an instance of the
                        // table). If this is a child of a non-root table then its prototype is
                        // created above and doesn't need to be created below
                        if (!isChildOfNonRoot) {
                            // Load the table's prototype data from the database and copy the
                            // prototype's data to the table. The data from the import file is
                            // pasted over the prototype's
                            TableInformation protoInfo = dbTable.loadTableData(tableInfo.getPrototypeName(), false,
                                    false, ccddMain.getMainFrame());
                            tableInfo.setData(protoInfo.getData());
                        }
                    }

                    // Check if this is the child of a root structure (an instance of the table).
                    // If this is a child of a non-root table then its prototype is created above
                    // and doesn't need to be created here
                    if (!isChildOfNonRoot) {
                        // Create a table from the imported information
                        if (!createImportedTable(tableInfo, tableDefn.getData(), numColumns, replaceExistingTables,
                                openEditor, allTables, parent)) {
                            // Add the skipped table to the list
                            skippedTables.add(tableInfo.getTablePath());
                        }
                        // The table was created
                        else {
                            // Add the table name to the list of existing tables
                            allTables.add(tableInfo.getTablePath());
                        }
                    }
                }
            }

            prototypesOnly = false;
        }

        // Check if the cancel import dialog is present
        if (haltDlg != null) {
            // Update the progress bar
            haltDlg.updateProgressBar("Updating internal tables...", -1);
        }

        // Check if any tables were skipped
        if (!skippedTables.isEmpty()) {
            // Inform the user that one or more tables were not imported
            new CcddDialogHandler().showMessageDialog(parent,
                    "<html><b>Table(s) not imported '</b>"
                            + CcddUtilities.convertArrayToStringTruncate(skippedTables.toArray(new String[0]))
                            + "<b>';<br>table already exists",
                    "Import Warning", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
        }

        // Store the table types
        dbTable.storeInformationTable(InternalTable.TABLE_TYPES, null, null, parent);

        // Store the data types
        dbTable.storeInformationTable(InternalTable.DATA_TYPES,
                CcddUtilities.removeArrayListColumn(dataTypeHandler.getDataTypeData(), DataTypesColumn.OID.ordinal()),
                null, parent);
        
        // Store the input types
        dbTable.storeInformationTable(InternalTable.INPUT_TYPES,
                CcddUtilities.removeArrayListColumn(Arrays.asList(inputTypeHandler.getCustomInputTypeData()),
                        InputTypesColumn.OID.ordinal()),
                null, parent);
        
        // Store the data fields
        dbTable.storeInformationTable(InternalTable.FIELDS, fieldHandler.getFieldDefnsFromInfo(), null, parent);
        
        // Check to see if something caused the group data to change. Example: If a table contains an array whose
        // data type is defined by another table in CCDD and the size is based on a macro there is an edge case
        // that occurs if the macro size is changed during an import. If the macro value is reduced and this table
        // is included in a group than the array members that were removed from the table need to be removed from
        // the internal groups table as well.
        
        // Get the list of table and variable paths and names, retaining any macros and bit lengths
        try {
            List<String> allTableAndVariableList = (new CcddTableTreeHandler(ccddMain,
                    TableTreeType.TABLES_WITH_PRIMITIVES, ccddMain.getMainFrame())).getTableTreePathList(null);
            
            // Get all members of the internal groups table
            List<String[]> members = dbTable.queryDatabase(new StringBuilder("SELECT ").append(GroupsColumn.MEMBERS.getColumnName())
                    .append(" FROM ").append(InternalTable.GROUPS.getTableName()), ccddMain.getMainFrame());
            
            // Initialize the command that will be used to update the internal groups table
            StringBuilder command = new StringBuilder();
            
            for (String[] member : members) {
                // Check if the table isn't in the list of valid names, but do not delete any members of the groups table that start
                // with a '0' or a '1' as these are rows within the internal groups table that contain the description of the group
                // and rather or not the group represents a CFS application. Groups that represent a CFS application will have this
                // row start with a 0, and those that do not will have this row start with a 1.
                if (!allTableAndVariableList.contains(member[0]) && !member[0].substring(0, 2).contentEquals("0,") &&
                        !member[0].substring(0, 2).contentEquals("1,")) {
                    // Group table member reference is invalid
                    command.append("DELETE FROM ").append(InternalTable.GROUPS.getTableName()).append(" WHERE ").append(
                            GroupsColumn.MEMBERS.getColumnName()).append(" = ").append(CcddDbTableCommandHandler.delimitText(
                                    member[0])).append("; ");
                }
            }
            
            // Execute the command
            dbCommand.executeDbCommand(command, ccddMain.getMainFrame());
        } catch (Exception e) {
            System.out.println("Internal _groups table could not be updated.");
        }
        
        // Retrieve the updated group information
        List <String[]> updatedGroupData = dbTable.retrieveInformationTable(InternalTable.GROUPS, false, parent);
        ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.GROUPS,
                updatedGroupData, null, parent);

        // Check if any macros are defined
        if (!macroHandler.getMacroData().isEmpty()) {
            // Store the macros in the database
            dbTable.storeInformationTable(InternalTable.MACROS,
                    CcddUtilities.removeArrayListColumn(macroHandler.getMacroData(), MacrosColumn.OID.ordinal()), null,
                    parent);
        }

        // Check if any reserved message IDs are defined
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty()) {
            // Store the reserved message IDs in the database
            dbTable.storeInformationTable(InternalTable.RESERVED_MSG_IDS, CcddUtilities.removeArrayListColumn(
                    rsvMsgIDHandler.getReservedMsgIDData(), ReservedMsgIDsColumn.OID.ordinal()), null, parent);
        }
    }

    /**********************************************************************************************
     * Create a new data table or replace an existing one and paste the supplied
     * cell data into it
     *
     * @param tableInfo       table information for the table to create
     *
     * @param cellData        array containing the cell data
     *
     * @param numColumns      number of columns in the table
     *
     * @param replaceExisting true to replace a table that already exists in the
     *                        database
     *
     * @param openEditor      true to open a table editor for each imported table
     *
     * @param allTables       list containing the paths and names of all tables
     *
     * @param parent          GUI component over which to center any error dialog
     *
     * @return true if the table is successfully imported; false if the table exists
     *         and the replaceExisting flag is not true
     *
     * @throws CCDDException The existing table that the new table replaces cannot
     *                       be removed, the new table cannot be created, or the
     *                       data cannot be added to the newly created table
     *********************************************************************************************/
    private boolean createImportedTable(TableInformation tableInfo, List<String> cellData, int numColumns,
            boolean replaceExisting, boolean openEditor, List<String> allTables, Component parent)
            throws CCDDException {
        boolean isImported = true;
        List<String[]> tableName = new ArrayList<String[]>();

        // Set the flag if the table already is present in the database
        boolean isExists = allTables.contains(tableInfo.getTablePath());

        // Check if the table already exists and if the user didn't elect to replace existing tables
        if (isExists && !replaceExisting) {
            // Set the flag to indicate the table wasn't imported
            isImported = false;
        } else {
            if (!tableInfo.isPrototype()) {
                isExists = allTables.contains(tableInfo.getPrototypeName());
            }
            
            // Check if the child structure table exists
            if (isExists) {
                // Add the parent and prototype table name to the list of table editors to close
                tableName.add(new String[] { tableInfo.getParentTable(), tableInfo.getPrototypeName() });
            } else {
                // Create the table in the database
                if (dbTable.createTable(new String[] { tableInfo.getPrototypeName() }, tableInfo.getDescription(),
                        tableInfo.getType(), true, true, parent)) {
                    throw new CCDDException();
                }

            }
        }

        // Check if the prototype was successfully created, or if the table isn't a prototype and doesn't already exist
        if (isImported) {
            CcddTableEditorHandler tableEditor;

            // Step through the data fields assigned to this table's type definition (i.e., the table's default data
            // fields)
            for (FieldInformation typeFldinfo : fieldHandler
                    .getFieldInformationByOwnerCopy(CcddFieldHandler.getFieldTypeName(tableInfo.getType()))) {
                // Add or update the table type field to the table, depending on whether or not the table already
                // has the field
                fieldHandler.addUpdateInheritedField(tableInfo.getFieldInformation(), tableInfo.getTablePath(),
                        typeFldinfo);
            }

            // Close any editors associated with this prototype table
            dbTable.closeDeletedTableEditors(tableName, ccddMain.getMainFrame());

            // Check if an editor is to be opened for the imported table(s)
            if (openEditor) {
                final CcddTableEditorDialog tableEditorDlg;

                // Create a list to hold the table's information
                List<TableInformation> tableInformation = new ArrayList<TableInformation>();
                tableInformation.add(tableInfo);

                // Check if a table editor dialog has not already been created for the added tables, or if
                // the number of tables opened in the editor has reached the maximum allowed
                if (tableEditorDlgs.isEmpty()
                        || tableEditorDlgs.get(tableEditorDlgs.size() - 1).getTabbedPane().getTabRunCount()
                                % ModifiableSizeInfo.MAX_IMPORTED_TAB_ROWS.getSize() == 0) {
                    // Create a table editor dialog and open the new table editor in it
                    tableEditorDlg = new CcddTableEditorDialog(ccddMain, tableInformation);
                    ccddMain.getTableEditorDialogs().add(tableEditorDlg);
                    ccddMain.updateRecentTablesMenu();
                    tableEditorDlg.setControlsEnabled(false);
                    tableEditorDlgs.add(tableEditorDlg);

                    // Force the dialog to the front
                    haltDlg.toFront();
                }
                // A table editor dialog is already created and hasn't reached the maximum number of tabs
                else {
                    // Get the reference to the last editor dialog created
                    tableEditorDlg = tableEditorDlgs.get(tableEditorDlgs.size() - 1);

                    // Add the table editor to the existing editor dialog
                    tableEditorDlg.addTablePanes(tableInformation);
                }

                // Get the reference to the table's editor
                tableEditor = tableEditorDlg.getTableEditor();

                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable() {
                    /******************************************************************************
                     * Since this involves a GUI update use invokeLater to execute the call on the
                     * event dispatch thread
                     *****************************************************************************/
                    @Override
                    public void run() {
                        // (Re)draw the halt and the table editor dialogs
                        haltDlg.update(haltDlg.getGraphics());
                        tableEditorDlg.update(tableEditorDlg.getGraphics());
                    }
                });
            }
            // Create the table without opening an editor
            else {
                // Create the table editor handler without displaying the table
                tableEditor = new CcddTableEditorHandler(ccddMain, tableInfo, null);
            }
            
            /* Check all indexes of cellData and for any that are empty set them to null */
            for (int index = 0; index < cellData.size(); index++) {
                if ((cellData.get(index) != null) && cellData.get(index).isEmpty()) {
                    cellData.set(index, null);
                }
            }

            // Paste the data into the table; check if the user canceled importing the table
            // following a cell validation error
            if (tableEditor.getTable().pasteData(cellData.toArray(new String[0]), numColumns, false, true, true, true,
                    false, false)) {
                eventLog.logEvent(EventLogMessageType.STATUS_MSG, new StringBuilder("Import canceled by user"));
                throw new CCDDException();
            }

            // Build the addition, modification, and deletion command lists
            tableEditor.buildUpdates();

            // Perform the changes to the table in the database
            if (dbTable.modifyTableData(tableEditor.getTableInformation(), tableEditor.getAdditions(),
                    tableEditor.getModifications(), tableEditor.getDeletions(), false, false, true, true, true, null,
                    parent)) {
                throw new CCDDException();
            }
        }

        return isImported;
    }

    /**********************************************************************************************
     * Import the contents of a file selected by the user into the specified
     * existing table
     *
     * @param tableHandler reference to the table handler for the table into which
     *                     to import the data
     *********************************************************************************************/
    protected void importSelectedFileIntoTable(ManagerDialogType dialogType, JPanel dialogPanel, 
            CcddTableEditorHandler tableHandler) {
        // Init variables
        List<TableDefinition> tableDefinitions = null;
        CcddImportExportInterface ioHandler = null;
        FileExtension importFileType;
        FileEnvVar[] dataFile = {};
        
        try {
            // Check what type of extension is needed
            FileNameExtensionFilter[] extFilter = new FileNameExtensionFilter [1];
            CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame());
            if (dialogType == ManagerDialogType.IMPORT_JSON) {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.JSON.getDescription(),
                        FileExtension.JSON.getExtensionName());
                // Create a JSON handler
                ioHandler = new CcddJSONHandler(ccddMain, groupHandler, tableHandler.getOwner());
                importFileType = FileExtension.JSON;
            } else if (dialogType == ManagerDialogType.IMPORT_CSV) {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.CSV.getDescription(),
                        FileExtension.CSV.getExtensionName());
                // Create a CSV handler
                ioHandler = new CcddCSVHandler(ccddMain, groupHandler, tableHandler.getOwner());
                importFileType = FileExtension.CSV;
            } else if (dialogType == ManagerDialogType.IMPORT_XTCE) {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.XTCE.getDescription(),
                        FileExtension.XTCE.getExtensionName());
                // Create an XTCE handler
                ioHandler = new CcddXTCEHandler(ccddMain, tableHandler.getOwner());
                importFileType = FileExtension.XTCE;
            } else {
                extFilter[0] = new FileNameExtensionFilter(FileExtension.EDS.getDescription(),
                        FileExtension.EDS.getExtensionName());
                // Create an EDS handler
                ioHandler = new CcddEDSHandler(ccddMain, tableHandler.getOwner());
                importFileType = FileExtension.EDS;
            }
            
            // Allow the user to select the data file path + name to import from
            dataFile = new CcddDialogHandler().choosePathFile(ccddMain, tableHandler.getOwner(), null, "export",
                    extFilter, false, false, "Import Table Data",
                    ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(), null),
                    DialogOption.IMPORT_OPTION, dialogPanel);
            
            // Get the state of the check-boxes for later use
            Boolean overwriteChkBxSelected = ((JCheckBox) dialogPanel.getComponent(overwriteExistingCbIndex)).isSelected();
            Boolean appendToExistingDataCbSelected = ((JCheckBox) dialogPanel.getComponent(appendToExistingDataCbIndex)).isSelected();
            Boolean ignoreErrorsCbSelected = ((JCheckBox) dialogPanel.getComponent(ignoreErrorsCbIndex)).isSelected();
            Boolean keepDataFieldsCbSelected = ((JCheckBox) dialogPanel.getComponent(keepDataFieldsCbIndex)).isSelected();
    
            // Check if a file was chosen
            if (dataFile != null && dataFile[0] != null) {
                // Store the current table type information so that it can be restored
                List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitions();
                
                // Place the table name in an array so that it can be easily passed to the compareToSnapshotDirectory
                // function.
                String[] tableName = {dataFile[0].getName().split("\\.")[0]};
                
                // Current data field information belonging to the active table editor
                List<FieldInformation> currentDataFieldInfo = fieldHandler.getFieldInformationByOwner(tableName[0]);
                
                // Check to see if we are importing a JSON or CSV file
                if (importFileType == FileExtension.JSON || importFileType == FileExtension.CSV) {
                    // The snapshot directory will be created and populated with a file that represents the current 
                    // contents of the table in which this imported file is to be imported into. This allows an easy 
                    // comparison of the two files to see if they are identical.
                    List<File> snapshotFiles = compareToSnapshotDirectory(tableName, false, importFileType,
                            false, ccddMain.getMainFrame());
                    
                    // Check to see if the files are identical
                    if (!snapshotFiles.isEmpty()) {
                        if (!FileUtils.contentEqualsIgnoreEOL(new File(dataFile[0].getPath()),
                                    new File(snapshotFiles.get(0).getPath()), null)) {
                            // Import the data file into a table definition
                            ioHandler.importFromFile(dataFile[0], ImportType.IMPORT_ALL, tableHandler.getTableTypeDefinition(),
                                    ignoreErrorsCbSelected, false, false, overwriteChkBxSelected);
                            tableDefinitions = ioHandler.getTableDefinitions();
                        }
                    }
                } else {
                    // Import the data file into a table definition
                    ioHandler.importFromFile(dataFile[0], ImportType.IMPORT_ALL, tableHandler.getTableTypeDefinition(),
                            ignoreErrorsCbSelected, false, false, overwriteChkBxSelected);
                    tableDefinitions = ioHandler.getTableDefinitions();
                    
                    // Change the owner of the table to the table that is being modified in the table editor
                    if (tableDefinitions != null && !tableDefinitions.isEmpty()) {
                        tableDefinitions.get(0).setName(tableHandler.getOwnerName());
                    }
                }
                    
                // Check to see if the files are identical. If so no other work needs to be done.
                if (tableDefinitions != null && !tableDefinitions.isEmpty()) {
                    // Get a short-cut to the table definition to shorten subsequent calls
                    TableDefinition tableDefn = tableDefinitions.get(0);
                    
                    // Check if any data fields were provided and if we are appending
                    if ((appendToExistingDataCbSelected && (dialogType == ManagerDialogType.IMPORT_JSON || dialogType == ManagerDialogType.IMPORT_JSON)) ||
                            (keepDataFieldsCbSelected)) {
                        List<String[]> importedDataFieldInfo = new ArrayList<String[]>(tableDefn.getDataFields());
                        
                        tableDefn.removeDataFields();
                        
                        for (int index = 0; index < currentDataFieldInfo.size(); index++) {
                            tableDefn.addDataField(currentDataFieldInfo.get(index));
                        }
                        
                        for (int index = 0; index < importedDataFieldInfo.size(); index++) {
                            // If the imported field does not already exist then add it 
                            if (fieldHandler.getFieldInformationByName(tableName[0], importedDataFieldInfo.get(index)[
                                    FieldsColumn.FIELD_NAME.ordinal()]) == null) {
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
                    addImportedDataField(tableDefn, tableHandler.getTableInformation().getTablePath(),
                            appendToExistingDataCbSelected);

                    // Rebuild the table's editor panel which contains the data fields
                    tableHandler.createDataFieldPanel(true, fieldHandler.getFieldInformationFromData(
                            tableDefn.getDataFields().toArray(new String[0][0]), ""), true);

                    // Check if cell data is provided in the table definition
                    if (tableDefn.getData() != null && !tableDefn.getData().isEmpty()) {
                        // Get the original number of rows in the table
                        int numRows = tableHandler.getTableModel().getRowCount();
                        
                        // Get the column count
                        int columnCount = 0;
                        if (importFileType == FileExtension.JSON || importFileType == FileExtension.CSV) {
                            columnCount = tableHandler.getTable().getColumnCount();
                        } else {
                            TypeDefinition type = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());
                            columnCount = type.getColumnCountVisible();
                        }

                        // Paste the data into the table; check if the user hasn't canceled
                        // importing the table following a cell validation error
                        if (!tableHandler.getTable().pasteData(tableDefn.getData().toArray(new String[0]), columnCount,
                                !overwriteChkBxSelected, !overwriteChkBxSelected, overwriteChkBxSelected,
                                false, true, false)) {
                            // Let the user know how many rows were added
                            new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                                    "<html><b>" + (tableHandler.getTableModel().getRowCount() - numRows)
                                            + " row(s) added",
                                    "Paste Table Data", JOptionPane.INFORMATION_MESSAGE, DialogOption.OK_OPTION);
                        }
                    }

                    // Restore the table types to the values prior to the import operation
                    tableTypeHandler.setTypeDefinitions(originalTableTypes);

                    // Re-enable auto-ending of the edit sequence and end the sequence. The
                    // imported data can be removed with a single undo if desired
                    tableHandler.getTable().getUndoHandler().setAutoEndEditSequence(true);
                    tableHandler.getTable().getUndoManager().endEditSequence();

                    // Store the data file path in the program preferences backing store
                    storePath(ccddMain, dataFile[0].getAbsolutePathWithEnvVars(), true,
                            ModifiablePathInfo.TABLE_EXPORT_PATH);
                } else {
                    /* Inform the user that there are no perceptible changes to the files relative
                     * to current db state.
                     */
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                            "<html><b>The selected folder/file does not contain any updates.</b>",
                            "No Changes Made", JOptionPane.INFORMATION_MESSAGE, DialogOption.OK_OPTION);
                }
            }
        } catch (IOException ioe) {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(),
                    "<html><b>Cannot read import file '</b>" + dataFile[0].getAbsolutePath() + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
        } catch (CCDDException ce) {
            // Check if an error message is provided
            if (!ce.getMessage().isEmpty()) {
                // Inform the user that an error occurred reading the import file
                new CcddDialogHandler().showMessageDialog(tableHandler.getOwner(), "<html><b>" + ce.getMessage(),
                        "Import Error", ce.getMessageType(), DialogOption.OK_OPTION);
            }
        } catch (Exception e) {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, tableHandler.getOwner());
        }
    }

    /**********************************************************************************************
     * Import one or more data fields into a table, appending them to any existing
     * fields. If the imported field already exists then the input flag determines
     * if the existing or imported field is used
     *
     * @param tableDefn         imported table definition
     *
     * @param ownerName         data field owner name
     *
     * @param useExistingFields true to replace an existing data field with the
     *                          imported ones if the field names match
     *********************************************************************************************/
    private void addImportedDataField(TableDefinition tableDefn, String ownerName, boolean useExistingFields) {
        /* Grab the current data fields */
        List<String[]> currentFieldInfo = CcddFieldHandler.getFieldDefnsAsListOfStrings(
                fieldHandler.getFieldInformationByOwner(ownerName));
        /* Grab the new data fields */
        List<String[]> newFieldInfo = tableDefn.getDataFields();
        
        boolean fieldExists;
        int atIndex;
        
        /* Step through the imported data fields. The order is reversed so that field definitions
         * can be removed if needed
         */
        for (int index = 0; index < newFieldInfo.size(); index++) {
            fieldExists = false;
            atIndex = -1;
            
            /* Set the data field owner to the specified table (if importing entire tables this
             * isn't necessary, but is when importing into an existing table since the owner in the
             * import file may differ)
             */
            newFieldInfo.get(index)[FieldsColumn.OWNER_NAME.ordinal()] = ownerName;
            
            /* Check to see if the field already exists */
            for (int index2 = 0; index2 < currentFieldInfo.size(); index2++) {
                if (currentFieldInfo.get(index2)[FieldsColumn.FIELD_NAME.ordinal()].contentEquals(
                        newFieldInfo.get(index)[FieldsColumn.FIELD_NAME.ordinal()])) {
                    if (currentFieldInfo.get(index2)[FieldsColumn.FIELD_TYPE.ordinal()].contentEquals(
                        newFieldInfo.get(index)[FieldsColumn.FIELD_TYPE.ordinal()])) {
                        /* If the field does exists then set the flag and save the index */
                        fieldExists = true;
                        atIndex = index2;
                        break;
                    }
                }
            }
            
            /* Does the field exists? */
            if (fieldExists) {
                /* Did the user decide to use existing fields? */
                if (useExistingFields) {
                    /* Remove the new data field definition and replace it with the existing definition */
                    newFieldInfo.remove(index);
                    newFieldInfo.add(index, currentFieldInfo.get(atIndex));
                }
                
                /* Remove the field from the list of current fields */
                currentFieldInfo.remove(atIndex);
            }
        }
        
        /* If there are any values left in the currentFieldInfo list they will need to be added */
        for (int index = 0; index < currentFieldInfo.size(); index++) {
            tableDefn.getDataFields().add(currentFieldInfo.get(index));
        }
    }

    /**********************************************************************************************
     * Export the contents of one or more tables selected by the user to one or more
     * files in the specified format. The export file names are based on the table
     * name if each table is stored in a separate file. The user supplied file name
     * is used if multiple tables are stored in a single file. This method is
     * executed in a separate thread since it can take a noticeable amount time to
     * complete, and by using a separate thread the GUI is allowed to continue to
     * update. The GUI menu commands, however, are disabled until the database
     * method completes execution
     *
     * @param filePath                path to the folder in which to store the
     *                                exported tables. Includes the name if storing
     *                                the tables to a single file
     *
     * @param tablePaths              table path for each table to load
     *
     * @param overwriteFile           true to store overwrite an existing file;
     *                                false skip exporting a table to a file that
     *                                already exists
     *
     * @param singleFile              true to store multiple tables in a single
     *                                file; false to store each table in a separate
     *                                file
     *
     * @param includeBuildInformation true to include the CCDD version, project,
     *                                host, and user information
     *
     * @param replaceMacros           true to replace macros with their
     *                                corresponding values; false to leave the
     *                                macros intact
     *
     * @param includeAllTableTypes    true to include the all table type definitions
     *                                in the export file
     *
     * @param includeAllDataTypes     true to include the all data type definitions
     *                                in the export file
     *
     * @param includeAllInputTypes    true to include the all user-defined input
     *                                type definitions in the export file
     *
     * @param includeAllMacros        true to include the all macro definitions in
     *                                the export file
     *
     * @param includeReservedMsgIDs   true to include the contents of the reserved
     *                                message ID table in the export file
     *
     * @param includeProjectFields    true to include the project-level data field
     *                                definitions in the export file
     *
     * @param includeGroups           true to include the groups and group data
     *                                field definitions in the export file
     *
     * @param includeAssociations     true to include the script associations in the
     *                                export file
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
     * @param fileExtn                file extension type
     *
     * @param endianess               EndianType.BIG_ENDIAN for big endian,
     *                                EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian       true if the telemetry and command headers are
     *                                always big endian (e.g., as with CCSDS)
     *
     * @param version                 version attribute (XTCE only)
     *
     * @param validationStatus        validation status attribute (XTCE only)
     *
     * @param classification1         first level classification attribute (XTCE
     *                                only)
     *
     * @param classification2         second level classification attribute (XTCE
     *                                only)
     *
     * @param classification3         third level classification attribute (XTCE
     *                                only)
     *
     * @param useExternal             true to use external (script) methods in place
     *                                of the internal ones (XTCE only)
     *
     * @param scriptFileName          name of the script file containing the
     *                                external (script) methods (XTCE only); ignored
     *                                if useExternal is false
     *
     * @param parent                  GUI component over which to center any error
     *                                dialog
     *********************************************************************************************/
    protected boolean exportSelectedTablesInBackground(final String filePath, final String[] tablePaths,
            final boolean overwriteFile, final boolean singleFile, final boolean includeBuildInformation,
            final boolean replaceMacros, final boolean deleteTargetDirectory, final boolean includeAllTableTypes,
            final boolean includeAllDataTypes, final boolean includeAllInputTypes, final boolean includeAllMacros,
            final boolean includeReservedMsgIDs, final boolean includeProjectFields, final boolean includeGroups,
            final boolean includeAssociations, final boolean includeTlmSched, final boolean includeAppSched,
            final boolean includeVariablePaths, final CcddVariableHandler variableHandler, final String[] separators,
            final FileExtension fileExtn, final EndianType endianess, final boolean isHeaderBigEndian,
            final String version, final String validationStatus, final String classification1, final String classification2,
            final String classification3, final boolean useExternal, final String scriptFileName, final Component parent) {
                /* Execute the export operation in the background */
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand() {
            /**************************************************************************************
             * Export the selected table(s)
             *************************************************************************************/
            @Override
            protected void execute() {
                /* Export the selected table(s) */
                errorFlag = exportSelectedTables(filePath, tablePaths, overwriteFile, singleFile,
                        includeBuildInformation, replaceMacros, deleteTargetDirectory, includeAllTableTypes,
                        includeAllDataTypes, includeAllInputTypes, includeAllMacros, includeReservedMsgIDs,
                        includeProjectFields, includeGroups, includeAssociations, includeTlmSched, includeAppSched,
                        includeVariablePaths, variableHandler, separators, fileExtn, endianess, isHeaderBigEndian,
                        version, validationStatus, classification1, classification2, classification3, useExternal,
                        scriptFileName, parent);
            }

            /**************************************************************************************
             * Perform export selected table(s) complete steps
             *************************************************************************************/
            @Override
            protected void complete() {
                /* Check if the export was executed from the command line */
                if (!(parent instanceof CcddDialogHandler)) {
                    /* Restore the table export path to what it was at program start-up */
                    ccddMain.restoreTableExportPath();
                }
            }
        });
        return errorFlag;
    }

    /**********************************************************************************************
     * Export the contents of one or more tables selected by the user to one or more
     * files in the specified format. The export file names are based on the table
     * name if each table is stored in a separate file. The user supplied file name
     * is used if multiple tables are stored in a single file
     *
     * @param filePath                path to the folder in which to store the
     *                                exported tables. Includes the name if storing
     *                                the tables to a single file
     *
     * @param tablePaths              table path for each table to load
     *
     * @param overwriteFile           true to store overwrite an existing file;
     *                                false skip exporting a table to a file that
     *                                already exists
     *
     * @param singleFile              true to store multiple tables in a single
     *                                file; false to store each table in a separate
     *                                file
     *
     * @param includeBuildInformation true to include the CCDD version, project,
     *                                host, and user information
     *
     * @param replaceMacros           true to replace macros with their
     *                                corresponding values; false to leave the
     *                                macros intact
     *
     * @param includeAllTableTypes    true to include the all table type definitions
     *                                in the export file
     *
     * @param includeAllDataTypes     true to include the all data type definitions
     *                                in the export file
     *
     * @param includeAllInputTypes    true to include the all user-defined input
     *                                type definitions in the export file
     *
     * @param includeAllMacros        true to include the all macro definitions in
     *                                the export file
     *
     * @param includeReservedMsgIDs   true to include the contents of the reserved
     *                                message ID table in the export file
     *
     * @param includeProjectFields    true to include the project-level data field
     *                                definitions in the export file
     *
     * @param includeGroups           true to include the groups and group data
     *                                field definitions in the export file
     *
     * @param includeAssociations     true to include the script associations in the
     *                                export file
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
     * @param fileExtn                file extension type
     *
     * @param endianess               EndianType.BIG_ENDIAN for big endian,
     *                                EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian       true if the telemetry and command headers are
     *                                always big endian (e.g., as with CCSDS)
     *
     * @param version                 version attribute (XTCE only)
     *
     * @param validationStatus        validation status attribute (XTCE only)
     *
     * @param classification1         first level classification attribute (XTCE
     *                                only)
     *
     * @param classification2         second level classification attribute (XTCE
     *                                only)
     *
     * @param classification3         third level classification attribute (XTCE
     *                                only)
     *
     * @param useExternal             true to use external (script) methods in place
     *                                of the internal ones (XTCE only)
     *
     * @param scriptFileName          name of the script file containing the
     *                                external (script) methods (XTCE only); ignored
     *                                if useExternal is false
     *
     * @param parent                  GUI component over which to center any error
     *                                dialog
     *
     * @return true if the export completes successfully
     *********************************************************************************************/
    protected boolean exportSelectedTables(final String OriginalFilePath, final String[] tablePaths,
            final boolean overwriteFile, final boolean singleFile, final boolean includeBuildInformation,
            final boolean replaceMacros,final boolean deleteTargetDirectory, final boolean includeAllTableTypes,
            final boolean includeAllDataTypes, final boolean includeAllInputTypes, final boolean includeAllMacros,
            final boolean includeReservedMsgIDs, final boolean includeProjectFields, final boolean includeGroups,
            final boolean includeAssociations, final boolean includeTlmSched, final boolean includeAppSched,
            final boolean includeVariablePaths, final CcddVariableHandler variableHandler, final String[] separators,
            final FileExtension fileExtn, final EndianType endianess, final boolean isHeaderBigEndian, final String version,
            final String validationStatus, final String classification1, final String classification2,
            final String classification3, final boolean useExternal, final String scriptFileName,
            final Component parent) {
        /* Initialize local variables */
        boolean errorFlag = false;
        FileEnvVar file = null;
        CcddImportExportInterface ioHandler = null;
        List<String> skippedTables = new ArrayList<String>();
        ScriptEngine scriptEngine = null;

        /* Are we writing to a single mega file or multiple files?  */
        String outputType = "";
        if (singleFile) {
            outputType = EXPORT_SINGLE_FILE;
        } else {
            outputType = EXPORT_MULTIPLE_FILES;
        }
        
        /* Converting the file path to a FileEnvVar object will expand any environment variables within the path */
        FileEnvVar modifiedFilePath = new FileEnvVar(OriginalFilePath);
        String filePath = modifiedFilePath.getAbsolutePath();

        /* Remove the trailing period if present */
        String path = CcddUtilities.removeTrailer(filePath, ".");

        /* Check if external (script) methods are to be used */
        if (useExternal) {
            try {
                /* The table information isn't provided to the script data access handler when
                 * creating the script engine below. Therefore, not all script data access
                 * methods are available (i.e., those that refer to the table names, rows, data,
                 * etc.). Barring an extensive rewrite of the export methods, in order to
                 * provide the information the tables would have to be read twice, once to
                 * create the script handler format (where tables of the same type are combined)
                 * and again in the XTCE handler. Performing the export operation via a script
                 * association does allow access to all of the methods (and entails loading each
                 * table twice). The link and group handlers aren't provided in the script
                 * engine call below either, but these are loaded if an access method requiring
                 * them is called
                 */

                /* Get the script engine for the supplied script file name */
                scriptEngine = ccddMain.getScriptHandler().getScriptEngine(scriptFileName, new TableInformation[0],
                        null, null, null, parent);
            } catch (CCDDException ce) {
                /* Inform the user that an error occurred accessing the script file */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Cannot use external methods - using " + "internal methods instead; cause '</b>"
                                + ce.getMessage() + "<b>'",
                        "Export Error", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
            }
        }

        try {
            /* Check if the user elected to store all tables in a single file. The path must
             * include a file name
             */
            if (singleFile) {
                /* Check if the file name doesn't end with the expected extension */
                if (!path.endsWith(fileExtn.getExtension())) {
                    /* Append the extension to the file name */
                    path += fileExtn.getExtension();
                }

                /* Create the file using the supplied name */
                file = new FileEnvVar(path);
            }
            /* The table(s) are to be stored in individual files, so the path doesn't
             * include a file name. Check if the path doesn't terminate with a name
             * separator character
             */
            else if (!path.endsWith(File.separator)) {
                /* Append the name separator character to the path */
                path += File.separator;
            }

            /* Create a handler based on the file extension */
            if (fileExtn == FileExtension.CSV) {
                ioHandler = new CcddCSVHandler(ccddMain, new CcddGroupHandler(ccddMain, null, parent), parent);
            } else if (fileExtn == FileExtension.EDS) {
                ioHandler = new CcddEDSHandler(ccddMain, parent);
            } else if (fileExtn == FileExtension.JSON) {
                ioHandler = new CcddJSONHandler(ccddMain, new CcddGroupHandler(ccddMain, null, parent), parent);
            } else if (fileExtn == FileExtension.XTCE) {
                ioHandler = new CcddXTCEHandler(ccddMain, scriptEngine, parent);
            }

            /* Delete the contents of the directory */
            if (deleteTargetDirectory) {
                CleanExportDirectory(fileExtn, filePath, singleFile, parent);
            }

            /* Check if the tables are to be exported to a single file or multiple files */
            if (singleFile) {
                /* Exporting all tables to a single file. Check if the file already exist, 
                 * if not one will be created. If it does than did the user elects to overwrite it?
                 */
                if (isOverwriteExportFileIfExists(file, overwriteFile, parent)) {
                    /* Export the formatted table data to the specified file */
                    ioHandler.exportTables(file, tablePaths, includeBuildInformation, replaceMacros,
                            includeVariablePaths, variableHandler, separators, outputType, endianess,
                            isHeaderBigEndian, version, validationStatus, classification1,
                            classification2, classification3);

                    /* Check if the file is empty following the export. This occurs if an error
                     * halts output to the file
                     */
                    if (file.length() == 0) {
                        /* Delete the empty file */
                        file.delete();
                    }
                } else {
                    /* Add the skipped table to the list */
                    skippedTables.addAll(Arrays.asList(tablePaths));
                }
            } else {
                /* Export each table to its own file */
                for (String tablePath : tablePaths) {
                    /* Create the file using a name derived from the table name */
                    file = new FileEnvVar(path + tablePath.replaceAll("[,\\.\\[\\]]", "_") + fileExtn.getExtension());

                    /* Check if a file exist for this table. If it doesn't one will be created. If
                     * it does did the user elect to overwrite it?
                     */
                    if (isOverwriteExportFileIfExists(file, overwriteFile, parent)) {
                        /* Export the formatted table data. The file name is derived from the table name */
                        ioHandler.exportTables(file, new String[] { tablePath }, includeBuildInformation, replaceMacros,
                                includeVariablePaths, variableHandler, separators, outputType, version, validationStatus,
                                classification1, classification2, classification3);

                        /* Check if the file is empty following the export. This occurs if an error
                         * halts output to the file
                         */
                        if (file.length() == 0) {
                            /* Delete the empty file */
                            file.delete();
                        }
                    }
                    /* The table is skipped */
                    else {
                        /* Add the skipped table to the list */
                        skippedTables.add(tablePath);
                    }
                }
            }

            /* All of the exports below apply only to the JSON/CSV export */
            if ((fileExtn == FileExtension.JSON) || (fileExtn == FileExtension.CSV)) {        
                /* Get directory where all the individual files will be exported or the 
                 * individual file that all data will be exported to 
                 */
                FileEnvVar exportDirectoryOrFile = new FileEnvVar(filePath);

                /* Export table info if needed */
                if (includeAllTableTypes || includeAllInputTypes || includeAllDataTypes) {
                    ioHandler.exportTableInfoDefinitions(exportDirectoryOrFile, includeAllTableTypes, includeAllInputTypes,
                            includeAllDataTypes, outputType);
                }
                
                boolean[] includes = {includeGroups, includeAllMacros, includeAssociations, includeTlmSched, includeAppSched,
                        includeReservedMsgIDs, includeProjectFields};
                exportDataTypes[] dataTypes = {exportDataTypes.GROUPS, exportDataTypes.MACROS, exportDataTypes.ASSOCIATIONS,
                        exportDataTypes.TELEMSCHEDULER, exportDataTypes.APPSCHEDULER, exportDataTypes.RESERVED_MSG_ID,
                        exportDataTypes.PROJECT_FIELDS};
                
                ioHandler.exportInternalCCDDData(includes, dataTypes, exportDirectoryOrFile, outputType);
            }

            /* Check if any tables were skipped */
            if (!skippedTables.isEmpty()) {
                /* Inform the user that one or more tables were not exported */
                new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Table(s) not exported '</b>"
                                + CcddUtilities.convertArrayToStringTruncate(skippedTables.toArray(new String[0]))
                                + "<b>';<br>output file already exists or file I/O error",
                        "Export Error", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
            }

            /* Store the export file path in the program preferences backing store */
            if (!filePath.contains(SNAP_SHOT_FILE)) {
                storePath(ccddMain, filePath, singleFile, ModifiablePathInfo.TABLE_EXPORT_PATH);
            }

            /* Check if no errors occurred exporting the table(s) */
            if (!errorFlag) {
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG, new StringBuilder("Data export completed successfully"));
            }
            /* An error occurred while exporting the table(s) */
            else {
                eventLog.logFailEvent(parent, "Export Error", "Data export completed with errors",
                        "<html><b>Data export completed with errors");
            }
        } catch (JAXBException | CCDDException jce) {
            /* Inform the user that the export operation failed */
            new CcddDialogHandler().showMessageDialog(parent,
                    "<html><b>Cannot export to file '</b>" + file.getAbsolutePath() + "<b>': " + jce.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
            errorFlag = true;
        } catch (Exception e) {
            /* Display a dialog providing details on the unanticipated error */
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }
        return errorFlag;
    }

    /**********************************************************************************************
     * Check if the specified data file exists and isn't empty, and if so, whether
     * or not the user elects to overwrite it
     *
     * @param exportFile    reference to the file
     *
     * @param overwriteFile true to overwrite an existing file
     *
     * @param parent        GUI component over which to center any error dialog
     *
     * @return true if the file doesn't exist, or if it does exist and the user
     *         elects to overwrite it; false if the file exists and the user elects
     *         not to overwrite it, or if an error occurs deleting the existing file
     *         or creating a new one
     *********************************************************************************************/
    private boolean isOverwriteExportFileIfExists(FileEnvVar exportFile, boolean overwriteFile, Component parent) {
        // Set the continue flag based on if the file exists and isn't empty
        boolean continueExport = !(exportFile.exists() && exportFile.length() != 0);

        try {
            // Check if the data file exists and isn't empty
            if (!continueExport) {
                // Check if the user elects to overwrite existing files
                if (overwriteFile || new CcddDialogHandler().showMessageDialog(parent,
                        "<html><b>Overwrite existing file '</b>\n" + exportFile.getAbsolutePath() + "<b>'?",
                        "Overwrite File", JOptionPane.QUESTION_MESSAGE, DialogOption.OK_CANCEL_OPTION) == OK_BUTTON) {
                    // Check if the file can't be deleted
                    if (!exportFile.delete()) {
                        throw new CCDDException("Cannot replace");
                    }

                    // Check if the data file cannot be created
                    if (!exportFile.createNewFile()) {
                        throw new CCDDException("Cannot create");
                    }

                    // Enable exporting the table
                    continueExport = true;
                }
            }
        } catch (CCDDException ce) {
            // Inform the user that the data file cannot be created
            new CcddDialogHandler().showMessageDialog(parent,
                    "<html><b>" + ce.getMessage() + " export file '</b>" + exportFile.getAbsolutePath() + "<b>'",
                    "File Error", ce.getMessageType(), DialogOption.OK_OPTION);
        } catch (IOException ioe) {
            // Inform the user that the data file cannot be written to
            new CcddDialogHandler().showMessageDialog(parent,
                    "<html><b>Cannot write to export file '</b>" + exportFile.getAbsolutePath() + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
        }

        return continueExport;
    }

    /**********************************************************************************************
     * Store the contents of the specified script file into the database
     *
     * @param file reference to the file to store
     *********************************************************************************************/
    protected void storeScriptInDatabase(FileEnvVar file) {
        BufferedReader br = null;

        try {
            List<String[]> scriptData = new ArrayList<String[]>();
            int lineNum = 1;

            // Read the table data from the selected file
            br = new BufferedReader(new FileReader(file));

            // Read the first line in the file
            String line = br.readLine();

            // Initialize the script description text
            String description = "";

            // Continue to read the file until EOF is reached
            while (line != null) {
                // Check if no script description has been found
                if (description.isEmpty()) {
                    // Get the index of the description tag (-1 if not found). Force to lower case
                    // to remove case sensitivity
                    int index = line.toLowerCase().indexOf(SCRIPT_DESCRIPTION_TAG);

                    // Check if the description tag is found
                    if (index != -1) {
                        // Extract the description from the file line
                        description = line.substring(index + SCRIPT_DESCRIPTION_TAG.length()).trim();
                    }
                }

                // Add the line from the script file
                scriptData.add(new String[] { String.valueOf(lineNum), line });

                // Read the next line in the file
                line = br.readLine();
                lineNum++;
            }

            // Store the script file in the project's database
            dbTable.storeInformationTableInBackground(InternalTable.SCRIPT, scriptData,
                    file.getName() + "," + description, ccddMain.getMainFrame());
        } catch (IOException ioe) {
            // Inform the user that the data file cannot be read
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                    "<html><b>Cannot read script file '</b>" + file.getAbsolutePath() + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
        } finally {
            try {
                // Check if the buffered reader was opened
                if (br != null) {
                    // Close the file
                    br.close();
                }
            } catch (IOException ioe) {
                // Inform the user that the file cannot be closed
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                        "<html><b>Cannot close script file '</b>" + file.getAbsolutePath() + "<b>'", "File Warning",
                        JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Retrieve the contents of the specified script from the database and save it
     * to a file
     *
     * @param script name of the script to retrieve
     *
     * @param file   reference to the script file
     *********************************************************************************************/
    protected void retrieveScriptFromDatabase(String script, File file) {
        PrintWriter pw = null;

        try {
            // Get the script file contents from the database
            List<String[]> lines = dbTable.retrieveInformationTable(InternalTable.SCRIPT, false, script,
                    ccddMain.getMainFrame());

            boolean cancelRetrieve = false;

            // Check if the data file exists
            if (file.exists()) {
                // Check if the existing file should be overwritten
                if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                        "<html><b>Overwrite existing script file '</b>" + file.getAbsolutePath() + "<b>'?",
                        "Overwrite File", JOptionPane.QUESTION_MESSAGE, DialogOption.OK_CANCEL_OPTION) == OK_BUTTON) {
                    // Check if the existing file can't be deleted
                    if (!file.delete()) {
                        throw new CCDDException("Cannot replace");
                    }
                }
                // File should not be overwritten
                else {
                    // Cancel retrieving the script
                    cancelRetrieve = true;
                }
            }

            // Check that the user didn't cancel the export
            if (!cancelRetrieve) {
                // Check if the script file can't be created
                if (!file.createNewFile()) {
                    throw new CCDDException("Cannot create");
                }

                // Output the table data to the selected file
                pw = new PrintWriter(file);

                // Step through each row in the table
                for (String[] line : lines) {
                    // Output the line contents (ignore the line number)
                    pw.println(line[1]);
                }
            }
        } catch (CCDDException ce) {
            // Inform the user that the script file cannot be created
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                    "<html><b>" + ce.getMessage() + " script file '</b>" + file.getAbsolutePath() + "<b>'",
                    "File Error", ce.getMessageType(), DialogOption.OK_OPTION);
        } catch (IOException ioe) {
            // Inform the user that the script file cannot be written to
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                    "<html><b>Cannot write to script file '</b>" + file.getAbsolutePath() + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
        } finally {
            // Check if the PrintWriter was opened
            if (pw != null) {
                // Close the script file
                pw.close();
            }
        }
    }

    /**********************************************************************************************
     * Store the specified file path in the program preferences backing store
     *
     * @param ccddMain    main class reference
     *
     * @param pathName    file path
     *
     * @param hasFileName true if the file path includes a file name. The file name
     *                    is removed before storing the path
     *
     * @param modPath     ModifiablePathInfo reference for the path being updated
     ********************************************************************************************/
    protected static void storePath(CcddMain ccddMain, String pathName, boolean hasFileName,
            ModifiablePathInfo modPath) {
        // Check if the file path includes a file name
        if (hasFileName && pathName.contains(File.separator)) {
            // Strip the file name from the path
            pathName = pathName.substring(0, pathName.lastIndexOf(File.separator));
        }

        // Check if the path name ends with a period
        if (pathName.endsWith(".")) {
            // Remove file separator (if present) and the period at the end of the path
            pathName = pathName.replaceFirst(File.separator + "?\\.", "");
        }

        // Store the file path
        modPath.setPath(ccddMain, pathName);
    }

    /**********************************************************************************************
     * Open the specified file for writing
     *
     * @param outputFileName output file path + name
     *
     * @return PrintWriter object; null if the file could not be opened
     *********************************************************************************************/
    public PrintWriter openOutputFile(String outputFileName) {
        PrintWriter printWriter = null;

        try {
            // Create the file object
            FileEnvVar outputFile = new FileEnvVar(outputFileName);

            // Check if the file already exists, and if so that it is successfully deleted
            if (outputFile.exists() && !outputFile.delete()) {
                throw new CCDDException("Cannot replace");
            }
            // Check if the output file is successfully created
            else if (outputFile.createNewFile()) {
                // Create the PrintWriter object
                printWriter = new PrintWriter(outputFile);
            }
            // The output file cannot be created
            else {
                throw new CCDDException("Cannot create");
            }
        } catch (CCDDException ce) {
            // Inform the user that the output file cannot be created
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                    "<html><b>" + ce.getMessage() + " output file '</b>" + outputFileName + "<b>'", "File Error",
                    ce.getMessageType(), DialogOption.OK_OPTION);
        } catch (Exception e) {
            // Inform the user that the output file cannot be opened
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                    "<html><b>Cannot open output file '</b>" + outputFileName + "<b>'", "File Error",
                    JOptionPane.ERROR_MESSAGE, DialogOption.OK_OPTION);
        }

        return printWriter;
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     *
     * @param printWriter output file PrintWriter object
     *
     * @param text        text to write to the output file
     *********************************************************************************************/
    public void writeToFile(PrintWriter printWriter, String text) {
        // Check if the PrintWriter object exists
        if (printWriter != null) {
            // Output the text to the file
            printWriter.print(text);
        }
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object and
     * append a line feed character
     *
     * @param printWriter output file PrintWriter object
     *
     * @param text        text to write to the output file
     *********************************************************************************************/
    public void writeToFileLn(PrintWriter printWriter, String text) {
        // Check if the PrintWriter object exists
        if (printWriter != null) {
            // Output the text to the file, followed by a line feed
            printWriter.println(text);
        }
    }

    /**********************************************************************************************
     * Write the supplied text in the indicated format to the specified output file
     * PrintWriter object
     *
     * @param printWriter output file PrintWriter object
     *
     * @param format      print format
     *
     * @param args        arguments referenced by the format specifiers in the
     *                    format string
     *********************************************************************************************/
    public void writeToFileFormat(PrintWriter printWriter, String format, Object... args) {
        // Check if the PrintWriter object exists
        if (printWriter != null) {
            // Output the text to the file, followed by a line feed
            printWriter.printf(format, args);
        }
    }

    /**********************************************************************************************
     * Close the specified output file
     *
     * @param printWriter output file PrintWriter object
     *********************************************************************************************/
    public void closeFile(PrintWriter printWriter) {
        // Check if the PrintWriter object exists
        if (printWriter != null) {
            // Close the file
            printWriter.close();
        }
    }
    
    /**********************************************************************************************
     * Create the snapshot directory which can be used as a temporary export location for file 
     * comparisons
     *
     * @param printWriter output file PrintWriter object
     *********************************************************************************************/
    public List<File> compareToSnapshotDirectory(String[] tablePaths, boolean exportEntireDatabase,
            FileExtension importFileType, boolean singleFile, Component parent) {
        /* Check if the .snapshot directory exists */
        if (!Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH))) {
            try {
                /* Create the .snapshot directory */
                Files.createDirectory(Paths.get(SNAP_SHOT_FILE_PATH));
            }
            /* Catch any possible exception while creating the .snapshot directory */
            catch (IOException e) {
                CcddUtilities.displayException(e, parent);
                e.printStackTrace();
            }
        }
        /* If the .snapshot directory exists, delete its contents */
        else {
            try {
                File directory = new File(SNAP_SHOT_FILE_PATH);
                FileUtils.cleanDirectory(directory);
            } catch (IOException ioe) {
                CcddUtilities.displayException(ioe, parent);
                ioe.printStackTrace();
                errorFlag = true;
            }
        }
        
        /* Backup current database state to .snapshot directory before import. */
        exportSelectedTables(SNAP_SHOT_FILE_PATH,  /* filePath */
                tablePaths,                        /* tablePaths */
                true,                              /* overwriteFile */
                false,                             /* singleFile */
                false,                             /* includeBuildInformation */
                false,                             /* replaceMacros */
                false,                             /* deleteTargetDirectory */
                exportEntireDatabase,              /* includeAllTableTypes */
                exportEntireDatabase,              /* includeAllDataTypes */
                exportEntireDatabase,              /* includeAllInputTypes */
                exportEntireDatabase,              /* includeAllMacros */
                true,                              /* includeReservedMsgIDs */
                true,                              /* includeProjectFields */
                exportEntireDatabase,              /* includeGroups */
                exportEntireDatabase,              /* includeAssociations */
                exportEntireDatabase,              /* includeTlmSched */
                exportEntireDatabase,              /* includeAppSched */
                false,                             /* includeVariablePaths */
                ccddMain.getVariableHandler(),     /* variableHandler */
                null,                              /* separators */
                importFileType,                    /* fileExtn */
                null,                              /* endianess */
                false,                             /* isHeaderBigEndian */
                null,                              /* version */
                null,                              /* validationStatus */
                null,                              /* classification1 */
                null,                              /* classification2 */
                null,                              /* classification3 */
                false,                             /* useExternal */
                null,                              /* scriptFileName */
                null);                             /* parent */
        
        return new ArrayList<>(Arrays.asList(new File(SNAP_SHOT_FILE_PATH).listFiles()));
    }

    /**********************************************************************************************
     * Create the snapshot2 directory which can be used as a temporary export location for files
     * that are spawned when a single file that represents an entire database is imported.
     *
     * @param parent CCDD component that called this function
     *********************************************************************************************/
    public void createSnapshotDirectory(Component parent) {
        /* Check if the .snapshot2 directory exists */
        if (!Files.isDirectory(Paths.get(SNAP_SHOT_FILE_PATH_2))) {
            try {
                /* Create the .snapshot2 directory */
                Files.createDirectory(Paths.get(SNAP_SHOT_FILE_PATH_2));
            }
            /* Catch any possible exception while creating the .snapshot directory */
            catch (IOException e) {
                CcddUtilities.displayException(e, parent);
                e.printStackTrace();
            }
        }
        /* If the .snapshot2 directory exists, delete its contents */
        else {
            try {
                File directory = new File(SNAP_SHOT_FILE_PATH_2);
                FileUtils.cleanDirectory(directory);
            } catch (IOException ioe) {
                CcddUtilities.displayException(ioe, parent);
                ioe.printStackTrace();
                errorFlag = true;
            }
        }
    }
    
    /**********************************************************************************************
     * Check if the snapshot directories exist and if so delete them
     *
     * @param parent CCDD component that called this function
     *********************************************************************************************/
    public void deleteSnapshotDirectories(Component parent) {
        /* If the .snapshot directories exists, delete them */
        try {
            File directory = new File(SNAP_SHOT_FILE_PATH);
            FileUtils.cleanDirectory(directory);
            directory.delete();
            
            directory = new File(SNAP_SHOT_FILE_PATH_2);
            FileUtils.cleanDirectory(directory);
            directory.delete();
        } catch (IOException ioe) {
            CcddUtilities.displayException(ioe, parent);
            ioe.printStackTrace();
            errorFlag = true;
        }
    }
    
    /**********************************************************************************************
     * Process the single file, which represents an entire database, that is being imported and 
     * output the data to multiple files.
     *
     * @param dataFile Path to the file being imported
     * 
     * @param dialogType What format is the file? JSON/CSV
     * 
     * @param parent Component that called this function
     * 
     * @return FileEnvVar[] that represents all of the files created from the data found in the 
     *         JSON file.
     *********************************************************************************************/
    public FileEnvVar[] processSingleJSONFileRepresentingDatabase(FileEnvVar dataFile, ManagerDialogType dialogType, final Component parent) {
        /* Create a list to hold all the files */
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();
        
        String filePath = "";
        String nameType = "";
        String tableName = "";
                
        /* Detect the type of the parsed JSON file and only accept JSONObjects */
        /* This will throw an exception if it is incorrect */
        try {
            /* Read the file */
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));
            
            /*************** INPUT TYPES, TABLE TYPES AND DATA TYPES ***************/
            String outputData = jsonHandler.retrieveJSONData(JSONTags.INPUT_TYPE_DEFN.getTag(), content).toString();
            outputData += jsonHandler.retrieveJSONData(JSONTags.TABLE_TYPE_DEFN.getTag(), content).toString();
            outputData += jsonHandler.retrieveJSONData(JSONTags.DATA_TYPE_DEFN.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.TABLE_INFO.JSON();
            FileEnvVar file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            
            /*************** MACROS ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.MACRO_DEFN.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.MACROS.JSON();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);

            /*************** GROUPS ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.GROUP.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.GROUPS.JSON();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "\n  ]\n}\n", filePath);
            
            /*************** SCRIPT ASSOCIATIONS ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.SCRIPT_ASSOCIATION.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.SCRIPT_ASSOCIATION.JSON();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            
            /*************** TLM SCHEDULER ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.TLM_SCHEDULER_COMMENT.getTag(), content).toString();
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.TELEM_SCHEDULER.JSON();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            
            /*************** APP SCHEDULER ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.APP_SCHEDULER_COMMENT.getTag(), content).toString();
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.APP_SCHEDULER.JSON();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            
            /*************** RESERVED MESSAGE IDS ***************/
            if (!jsonHandler.retrieveJSONData(JSONTags.RESERVED_MSG_ID_DEFN.getTag(), content).toString().isEmpty()) {
                outputData = jsonHandler.retrieveJSONData(JSONTags.RESERVED_MSG_ID_DEFN.getTag(), content).toString();
                filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.RESERVED_MSG_ID.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            }
            
            /*************** PROJECT FIELDS ***************/
            if (!jsonHandler.retrieveJSONData(JSONTags.PROJECT_FIELD.getTag(), content).toString().isEmpty()) {
                outputData = jsonHandler.retrieveJSONData(JSONTags.PROJECT_FIELD.getTag(), content).toString();
                filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.PROJECT_DATA_FIELD.JSON();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                writeToFile("{\n  \"" + outputData + "]\n}\n", filePath);
            }
            
            /*************** TABLE DEFINITIONS ***************/
            outputData = jsonHandler.retrieveJSONData(JSONTags.TABLE_DEFN.getTag(), content).toString();
            
            String[] data = outputData.split("]\n    },\n");
            
            for (int i = 0; i < data.length; i++) {
                nameType = data[i].split(",\n")[0];
                if (i == 0) {
                    // Check if this line contains the file description. Will change how data is parsed
                    if (nameType.contains(JSONTags.FILE_DESCRIPTION.getTag())) {
                        nameType = data[i].split(",\n")[1];
                        // Grab the table name
                        tableName = nameType.split(": ")[1].replace("\"", "");
                    } else {
                        tableName = nameType.split(": ")[2].replace("\"", "");
                    }
                } else {
                    tableName = nameType.split(": ")[1].replace("\"", "");
                }
                tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");
                
                filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".json";
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                if (i == 0) {
                    writeToFile("{\n  \"" + data[i] + "]\n    }\n  ]\n}\n", filePath);
                } else if (i == data.length - 1) {
                    writeToFile("{\n  \"" + JSONTags.TABLE_DEFN.getTag() + "\": [\n" + data[i] + "  ]\n}\n", filePath);
                } else {
                    writeToFile("{\n  \"" + JSONTags.TABLE_DEFN.getTag() + "\": [\n" + data[i] + "]\n    }\n  ]\n}\n", filePath);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return dataFiles.toArray(new FileEnvVar[0]);
    }
    
    /**********************************************************************************************
     * Process the conversion file, which represents data pulled from a C header file, that is
     * being imported and output the data to multiple files to help with the import process
     *
     * @param dataFile Path to the file being imported
     * 
     * @param parent Component that called this function
     * 
     * @return FileEnvVar[] that represents all of the files created from the data found in the 
     *         conversion file.
     *********************************************************************************************/
    public FileEnvVar[] processCSVConversionFile(FileEnvVar dataFile, final Component parent) {
        /* Create a list to hold all the files */
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();
        
        String filePath = "";
        
        /* This will throw an exception if it is incorrect */
        try {
            /* Read the file */
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));
            
            /*************** MACROS ***************/
            String outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.MACRO.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.MACROS.CSV();
            FileEnvVar file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData, filePath);
            
            /*************** TABLE DEFINITIONS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.NAME_TYPE.getTag(), content).toString();
            
            /* Divide the data up into the individual definitions */
            String[] data = outputData.split("\n\n");
            
            /* Step through each definition */
            for (int i = 0; i < data.length; i++) {
                /* Parse the name */
                String nameType = data[i].split("\n")[1];
                String tableName = nameType.split("\",\"")[0].replace("\"", "");
                tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");
                
                /* Create the file path and use the path to create a new file */
                filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".csv";
                file = new FileEnvVar(filePath);
                
                /* Add this new file to the list of files */
                dataFiles.add(file);
                
                /* Write the data to the new file */
                writeToFile("\n" + data[i], filePath);
                outputData = "";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return dataFiles.toArray(new FileEnvVar[0]);
    }
    
    /**********************************************************************************************
     * Process the single file, which represents an entire database, that is being imported and 
     * output the data to multiple files.
     *
     * @param dataFile Path to the file being imported
     * 
     * @param parent Component that called this function
     * 
     * @return FileEnvVar[] that represents all of the files created from the data found in the 
     *         CSV file.
     *********************************************************************************************/
    public FileEnvVar[] processSingleCSVFileRepresentingDatabase(FileEnvVar dataFile, final Component parent) {
        /* Create a list to hold all the files */
        List<FileEnvVar> dataFiles = new ArrayList<FileEnvVar>();
        
        String filePath = "";
        
        /* This will throw an exception if it is incorrect */
        try {
            /* Read the file */
            StringBuilder content = new StringBuilder(new String((Files.readAllBytes(Paths.get(dataFile.getPath())))));
            
            /*************** INPUT TYPES, TABLE TYPES AND DATA TYPES ***************/
            String outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.INPUT_TYPE.getTag(), content);
            outputData += csvHandler.retrieveCSVData(CSVTags.TABLE_TYPE.getTag(), content);
            outputData += csvHandler.retrieveCSVData(CSVTags.DATA_TYPE.getTag(), content);
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.TABLE_INFO.CSV();
            FileEnvVar file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);
            
            /*************** MACROS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.MACRO.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.MACROS.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);

            /*************** GROUPS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.GROUP.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.GROUPS.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);
            
            /*************** SCRIPT ASSOCIATIONS ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.SCRIPT_ASSOCIATION.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.SCRIPT_ASSOCIATION.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);
            
            /*************** TLM SCHEDULER ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.TELEM_SCHEDULER.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.TELEM_SCHEDULER.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);
            
            /*************** APP SCHEDULER ***************/
            outputData = "\n" + csvHandler.retrieveCSVData(CSVTags.APP_SCHEDULER.getTag(), content).toString();
            
            filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.APP_SCHEDULER.CSV();
            file = new FileEnvVar(filePath);
            dataFiles.add(file);
            
            writeToFile(outputData + "\n", filePath);
            
            /*************** RESERVED MESSAGE IDS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.RESERVED_MSG_IDS.getTag(), content).toString();
            
            if (!outputData.isEmpty()) {
                outputData = "\n" + outputData; 
                
                filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.RESERVED_MSG_ID.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                writeToFile(outputData + "\n", filePath);
            }
            
            /*************** PROJECT FIELDS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.PROJECT_DATA_FIELD.getTag(), content).toString();
            
            if (!outputData.isEmpty()) {
                outputData = "\n" + outputData; 
                
                filePath = SNAP_SHOT_FILE_PATH_2 + "/" + FileNames.PROJECT_DATA_FIELD.CSV();
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                writeToFile(outputData + "\n", filePath);
            }
            
            /*************** TABLE DEFINITIONS ***************/
            outputData = csvHandler.retrieveCSVData(CSVTags.NAME_TYPE.getTag(), content).toString();
            
            String[] data = outputData.split("\n\n");
            
            for (int i = 0; i < data.length; i++) {
                String nameType = data[i].split("\n")[1];
                String tableName = nameType.split("\",\"")[0].replace("\"", "");
                tableName = tableName.replaceAll("[,\\.\\[\\]]", "_");
                
                filePath = SNAP_SHOT_FILE_PATH_2 + tableName + ".csv";
                file = new FileEnvVar(filePath);
                dataFiles.add(file);
                
                if (i == data.length -1) {
                    writeToFile("\n" + data[i], filePath);
                } else {
                    writeToFile("\n" + data[i] + "\n", filePath);
                }
                
                outputData = "";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return dataFiles.toArray(new FileEnvVar[0]);
    }
    
    /**********************************************************************************************
     * Write the JSON data to the specified file
     *
     * @param outputJO Data to be written
     * 
     * @param filePath Path to the file
     *********************************************************************************************/
    public void writeToFile(Object outPut, String filePath) {
        /* Create a set of writers for the output file. */
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        
        try {
            if (outPut != null) {
                fw = new FileWriter(filePath, false);
                bw = new BufferedWriter(fw);
                pw = new PrintWriter(bw);
                try {
                    pw.print((String) outPut);
                } catch (Exception e) {
                    
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // Check if the PrintWriter was opened
                if (pw != null) {
                    // Close the file
                    pw.close();
                }
                
                // Check if the BufferedWriter was opened
                if (bw != null) {
                    // Close the file
                    bw.close();
                }
    
                // Check if the FileWriter was opened
                if (fw != null) {
                    // Close the file
                    fw.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    
    /**********************************************************************************************
     * Write the JSON data to the specified file
     *
     * @param fileExt The extension type of the files that are going to be exported
     * 
     * @param directoryPath Path to the directory that is going to be cleaned
     *********************************************************************************************/
    public void CleanExportDirectory(FileExtension fileExt, String directoryPath, boolean singleFile,
            Component parent) {
        /* Delete the specified contents of the directory */
        try {
            if (!singleFile) {
                /* Clear the directory */
                File directory = new File(directoryPath);
                File fList[] = directory.listFiles();
                
                for (int index = 0; index < fList.length; index++) {
                    String currentFile = fList[index].getName();
                    if (currentFile.endsWith(fileExt.getExtension())) {
                        fList[index].delete();
                    }
                }
            } else if (!directoryPath.contentEquals(SNAP_SHOT_FILE_PATH)){
                /* Trim the filename off */
                String[] temp = directoryPath.split("/");
                String name = temp[temp.length-1];
                String tempPath = directoryPath.replace(name, "");
                
                /* Clear the directory */
                File directory = new File(tempPath);
                File fList[] = directory.listFiles();
                
                for (int index = 0; index < fList.length; index++) {
                    String currentFile = fList[index].getName();
                    if (currentFile.endsWith(fileExt.getExtension())) {
                        fList[index].delete();
                    }
                }
            }
        } catch (Exception e) {
            CcddUtilities.displayException(e, parent);
            e.printStackTrace();
            errorFlag = true;
        }
    }
}
