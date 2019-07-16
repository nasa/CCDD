/**
 * CFS Command and Data Dictionary file I/O handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CCDD_PROJECT_IDENTIFIER;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPT_DESCRIPTION_TAG;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.USERS_GUIDE;

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
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.JAXBException;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.ServerPropertyDialogType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddImportExportInterface.ImportType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

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
    private CcddFieldHandler fieldHandler;
    private CcddMacroHandler macroHandler;
    private CcddReservedMsgIDHandler rsvMsgIDHandler;
    private CcddInputTypeHandler inputTypeHandler;
    private List<CcddTableEditorDialog> tableEditorDlgs;
    private final CcddEventLogDialog eventLog;
    private CcddHaltDialog haltDlg;

    /**********************************************************************************************
     * File I/O handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddFileIOHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbCommand = ccddMain.getDbCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();
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
                    String path = URLDecoder.decode(new File(CcddMain.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath(),
                                                    "UTF-8");

                    // Display the user's guide - replace the .jar file name with the user's guide
                    // name
                    Desktop.getDesktop().open(new File(path.substring(0,
                                                                      path.lastIndexOf(File.separator) + 1)
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
     * Backup the currently open project's database to a user-selected file using the pg_dump
     * utility. The backup data is stored in plain text format
     *
     * @param doInBackground
     *            true to perform the operation in a background process
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
            String databaseName = dbControl.getDatabaseName();
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
                                                                       0,
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
                        timeStamp = "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());

                        // Append the date and time stamp to the file name
                        dlg.getFileNameField().setText(dlg.getFileNameField().getText().replaceFirst(Pattern.quote(FileExtension.DBU.getExtension()),
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
                            // Inform the user that the existing backup file cannot be replaced
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html><b>Cannot replace existing backup file '</b>"
                                                                                               + dataFile[0].getAbsolutePath()
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

                // Check that no errors occurred and that the user didn't cancel the backup
                if (!cancelBackup)
                {
                    // Check if the operation should be performed in the background
                    if (doInBackground)
                    {
                        // Create a backup of the current database
                        dbControl.backupDatabaseInBackground(projectName, dataFile[0]);
                    }
                    // Perform the operation in the foreground
                    else
                    {
                        // Create a backup of the current database
                        dbControl.backupDatabase(projectName, dataFile[0]);
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Restore a project's database from a user-selected backup file. The backup is a plain text
     * file containing the PostgreSQL commands necessary to rebuild the database
     *********************************************************************************************/
    protected void restoreDatabaseFromFile()
    {
        restoreDatabaseFromFile(null, null, null, null);
    }

    /**********************************************************************************************
     * Restore a project's database from a specified or user-selected backup file. The backup is a
     * plain text file containing the PostgreSQL commands necessary to rebuild the database
     *
     * @param restoreFileName
     *            name of the backup file to restore; null to allow the user to choose a file
     *
     * @param projectName
     *            name of the project to restore; null to extract the project name from the backup
     *            file's database comment
     *
     * @param projectOwner
     *            owner of the project to restore; null to use the current user as the owner
     *
     * @param projectDescription
     *            description of the project to restore; null to extract the description from the
     *            backup file's database comment
     *********************************************************************************************/
    protected void restoreDatabaseFromFile(String restoreFileName,
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
                boolean nameDescProvided = projectName != null && projectDescription != null;
                boolean commentFound = false;
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
                    boolean inUsers = false;
                    boolean isOwnerAdmin = false;
                    String oid = "";
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
                        if (!inComment)
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
                            else if (line.matches("COPY (?:[^\\.]+\\.)?"
                                                  + InternalTable.USERS.getTableName()
                                                  + " .+"))
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
                                if (line.matches("\\d+\\s+[^\\s]+\\s+.+"))
                                {
                                    // Get the OID for this table entry
                                    oid = line.replaceFirst("\\s.*", "");

                                    if (line.matches("\\d+\\s+" + projectOwner + "+\\s+.+"))
                                    {
                                        // Set the flag to indicate the project owner is already in
                                        // the user access level table
                                        isOwnerAdmin = true;

                                        // Make the project owner an administrator for the restored
                                        // project
                                        line.replaceFirst("(.+\\s+).+$",
                                                          "$1"
                                                                         + AccessLevel.ADMIN.getDisplayName());
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
                                        line = (Integer.valueOf(oid) + 1)
                                               + "\t"
                                               + projectOwner
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
                                line = line.replaceFirst("FROM .+;",
                                                         "FROM " + projectOwner + ";");
                            }
                            // Check if this line is a SQL command that grants permissions to an
                            // owner other than PUBLIC
                            else if (line.matches("GRANT .+ TO .+;\\s*")
                                     && !line.matches("GRANT .+ TO PUBLIC;\\s*"))
                            {
                                // Change the original owner to the current user
                                line = line.replaceFirst("TO .+;",
                                                         "TO " + projectOwner + ";");
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
                        }

                        // Check if this line is the beginning or a continuance of the database
                        // comment
                        if (inComment)
                        {
                            // Begin, or append the continuance of the database description, to the
                            // database comment command text
                            commentText += (commentText.isEmpty()
                                                                  ? ""
                                                                  : "\n")
                                           + line;

                            // Insert a comment indicator into the file so that this line isn't
                            // executed when the database is restored
                            line = "-- " + line;

                            // Set the flag to true if the comment continues to the next line
                            inComment = !line.endsWith(";");
                        }

                        // Write the line to the temporary file
                        bw.write(line + "\n");
                    }

                    // Check if the project name and description exist
                    if (commentFound || nameDescProvided)
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
                                                                         + CCDD_PROJECT_IDENTIFIER
                                                                         + ".+",
                                                                         "$1");
                            commentText = commentText.replaceAll("(?s)COMMENT ON DATABASE .+ IS '"
                                                                 + CCDD_PROJECT_IDENTIFIER
                                                                 + "(.+)';$",
                                                                 "$1");

                            // Split the line read from the file in order to get the project name
                            // and description
                            String[] comment = dbControl.parseDatabaseComment(databaseName,
                                                                              commentText);

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
                            projectAdministrator += (projectAdministrator.isEmpty()
                                                                                    ? ""
                                                                                    : ",")
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
                    // The project owner, name, and description don't exist
                    else
                    {
                        throw new CCDDException("File '</b>"
                                                + dataFile[0].getAbsolutePath()
                                                + "'<b> is not a backup file");
                    }
                }
                catch (CCDDException ce)
                {
                    // Inform the user that the backup file error occurred
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>" + ce.getMessage(),
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
     * Import one or more files, creating new tables and optionally replacing existing ones. The
     * file(s) may contain definitions for more than one table. This method is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     *
     * @param dataFile
     *            array of files to import
     *
     * @param backupFirst
     *            true to create a backup of the database before importing tables
     *
     * @param replaceExisting
     *            true to replace a table that already exists in the database
     *
     * @param appendExistingFields
     *            true to append the existing data fields for a table (if any) to the imported ones
     *            (if any). Only valid when replaceExisting is true
     *
     * @param useExistingFields
     *            true to use an existing data field in place of the imported ones if the field
     *            names match. Only valid when replaceExisting and appendExistingFields are true
     *
     * @param openEditor
     *            true to open a table editor for each imported table
     *
     * @param ignoreErrors
     *            true to ignore all errors in the import file
     *
     * @param replaceExistingMacros
     *            true to replace the values for existing macros
     *
     * @param replaceExistingGroups
     *            true to replace existing group definitions
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void importFileInBackground(final FileEnvVar[] dataFile,
                                          final boolean backupFirst,
                                          final boolean replaceExisting,
                                          final boolean appendExistingFields,
                                          final boolean useExistingFields,
                                          final boolean openEditor,
                                          final boolean ignoreErrors,
                                          final boolean replaceExistingMacros,
                                          final boolean replaceExistingGroups,
                                          final Component parent)
    {
        // Execute the import operation in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Import the selected data
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the import cancellation dialog
                haltDlg = new CcddHaltDialog("Import Data(s)",
                                             "Importing data",
                                             "import",
                                             100,
                                             dataFile.length,
                                             ccddMain.getMainFrame());

                // Import the selected table(s)
                importFile(dataFile,
                           backupFirst,
                           replaceExisting,
                           appendExistingFields,
                           useExistingFields,
                           openEditor,
                           ignoreErrors,
                           replaceExistingMacros,
                           replaceExistingGroups,
                           parent);
            }

            /**************************************************************************************
             * Import command complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if the user didn't cancel import
                if (!haltDlg.isHalted())
                {
                    // Close the cancellation dialog
                    haltDlg.closeDialog();
                }
                // Import was canceled
                else
                {
                    eventLog.logEvent(EventLogMessageType.STATUS_MSG, "Import terminated by user");
                }

                haltDlg = null;
            }
        });
    }

    /**********************************************************************************************
     * Import one or more files, creating new tables and optionally replacing existing ones. The
     * file(s) may contain definitions for more than one table. This method is executed in a
     * separate thread since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database method completes execution
     *
     * @param dataFiles
     *            array of files to import
     *
     * @param backupFirst
     *            true to create a backup of the database before importing tables
     *
     * @param replaceExistingTables
     *            true to replace a table that already exists in the database
     *
     * @param appendExistingFields
     *            true to append the existing data fields for a table (if any) to the imported ones
     *            (if any). Only valid when replaceExisting is true
     *
     * @param useExistingFields
     *            true to use an existing data field in place of the imported ones if the field
     *            names match. Only valid when replaceExisting and appendExistingFields are true
     *
     * @param openEditor
     *            true to open a table editor for each imported table
     *
     * @param ignoreErrors
     *            true to ignore all errors in the import file
     *
     * @param replaceExistingMacros
     *            true to replace the values for existing macros
     *
     * @param replaceExistingGroups
     *            true to replace existing group definitions
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true is the import operation completes successfully
     *********************************************************************************************/
    protected boolean importFile(FileEnvVar[] dataFiles,
                                 boolean backupFirst,
                                 boolean replaceExistingTables,
                                 boolean appendExistingFields,
                                 boolean useExistingFields,
                                 boolean openEditor,
                                 boolean ignoreErrors,
                                 boolean replaceExistingMacros,
                                 boolean replaceExistingGroups,
                                 Component parent)
    {
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
                          "Importing table(s) from '"
                                                          + CcddUtilities.convertArrayToString(fileNames.toArray(new String[0]))
                                                          + "'");

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

        try
        {
            int numFilesProcessed = 0;

            // Create a save point in case an error occurs while creating or modifying a table
            dbCommand.createSavePoint(parent);

            // Step through each selected file
            for (FileEnvVar file : dataFiles)
            {
                // Store the file path
                filePath = file.getAbsolutePath();

                // Check if the halt dialog is active (import operation is executed in the
                // background)
                if (haltDlg != null)
                {
                    // Check if the user canceled verification
                    if (haltDlg.isHalted())
                    {
                        throw new CCDDException();
                    }

                    // Update the progress bar
                    haltDlg.updateProgressBar("Reading import file " + file.getName(),
                                              haltDlg.getNumDivisionPerStep() * numFilesProcessed);
                    numFilesProcessed++;
                }

                // Check if the file doesn't exist
                if (!file.exists())
                {
                    throw new CCDDException("Cannot locate file");
                }

                // Check if the file to import is in CSV format based on the extension
                if (filePath.endsWith(FileExtension.CSV.getExtension()))
                {
                    // Create a CSV handler
                    ioHandler = new CcddCSVHandler(ccddMain, groupHandler, parent);
                }
                // Check if the file to import is in EDS format based on the extension
                else if (filePath.endsWith(FileExtension.EDS.getExtension()))
                {
                    // Create a EDS handler
                    ioHandler = new CcddEDSHandler(ccddMain, parent);
                }
                // Check if the file to import is in JSON format based on the extension
                else if (filePath.endsWith(FileExtension.JSON.getExtension()))
                {
                    // Create a JSON handler
                    ioHandler = new CcddJSONHandler(ccddMain, groupHandler, parent);
                }
                // Check if the file to import is in XTCE format based on the extension
                else if (filePath.endsWith(FileExtension.XTCE.getExtension()))
                {
                    // Create a XTCE handler
                    ioHandler = new CcddXTCEHandler(ccddMain, parent);
                }
                // The file extension isn't recognized
                else
                {
                    throw new CCDDException("Unrecognized file type");
                }

                // Import the table definition(s) from the file
                ioHandler.importFromFile(file,
                                         ImportType.IMPORT_ALL,
                                         null,
                                         ignoreErrors,
                                         replaceExistingMacros,
                                         replaceExistingGroups);

                // Check if the halt dialog is active (import operation is executed in the
                // background)
                if (haltDlg != null)
                {
                    // Check if the user canceled verification
                    if (haltDlg.isHalted())
                    {
                        throw new CCDDException();
                    }

                    // Update the progress bar
                    haltDlg.updateProgressBar("Importing data...",
                                              haltDlg.getProgressBar().getValue());
                }

                // Check if the user elected to append any new data fields to any existing ones for
                // a table
                if (appendExistingFields)
                {
                    // Step through each table definition
                    for (TableDefinition tableDefn : ioHandler.getTableDefinitions())
                    {
                        // Add the imported data field(s) to the table
                        addImportedDataField(tableDefn, tableDefn.getName(), useExistingFields);
                    }
                }

                // Step through each table definition from the import file
                for (TableDefinition newDefn : ioHandler.getTableDefinitions())
                {
                    boolean isFound = false;

                    // Step through each table definition already in the list
                    for (TableDefinition existingDefn : allTableDefinitions)
                    {
                        // Check if the table is already defined in the list
                        if (newDefn.getName().equals(existingDefn.getName()))
                        {
                            // Add the table name and associated file name to the list of
                            // duplicates
                            duplicateDefinitions.add(newDefn.getName()
                                                     + " (file: "
                                                     + file.getName()
                                                     + ")");

                            // Set the flag indicating the table definition is a duplicate and stop
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

            // Create the data tables from the imported table definitions from all files
            createTablesFromDefinitions(allTableDefinitions,
                                        replaceExistingTables,
                                        replaceExistingMacros,
                                        openEditor,
                                        groupHandler,
                                        parent);

            // Check if any new script associations have been added
            if (ioHandler.getScriptAssociations() != null)
            {
                // Step through each association
                for (int index = 0; index < ioHandler.getScriptAssociations().size(); index++)
                {
                    // Format the table members for storage in the database
                    String[] assn = ioHandler.getScriptAssociations().get(index);
                    assn[AssociationsColumn.MEMBERS.ordinal()] = CcddScriptHandler.convertAssociationMembersFormat(assn[AssociationsColumn.MEMBERS.ordinal()],
                                                                                                                   true);
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

            // Store the data file path in the program preferences backing store
            storePath(ccddMain,
                      dataFiles[0].getAbsolutePathWithEnvVars(),
                      true,
                      ModifiablePathInfo.TABLE_EXPORT_PATH);

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

            // Add event to log indicating that the import completed successfully
            eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                              "Table import completed successfully");

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

            // Step through each table editor dialog created during the import operation
            for (CcddTableEditorDialog tableEditorDlg : tableEditorDlgs)
            {
                // Enable the editor dialog's command menu items
                tableEditorDlg.setControlsEnabled(true);
            }
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
        catch (CCDDException | SQLException cse)
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
                                                          ((CCDDException) cse).getMessageType(),
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

        // Check if an error occurred
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
            eventLog.logEvent(EventLogMessageType.FAIL_MSG, "Table import failed to complete");
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Check the supplied list of tables in the order they are referenced for the specified
     * prototype structure table name. If not present add the table to the list at the current
     * insertion index. Update the insertion index to the immediately after the specified table
     *
     * @param protoStructName
     *            name of the prototype structure table
     *
     * @param orderedTableNames
     *            current list of table names in the order referenced (the prototype structure is
     *            added if not already in the list)
     *
     * @param insertionIndex
     *            index at which to insert the table name
     *
     * @return The updated insertion point
     *********************************************************************************************/
    private int getStructureInsertionIndex(String protoStructName,
                                           List<String> orderedTableNames,
                                           int insertionIndex)
    {
        // Get the index of the structure in the list of reference ordered table names
        int protoStructIndex = orderedTableNames.indexOf(protoStructName);

        // Check if the structure isn't in the list
        if (protoStructIndex == -1)
        {
            // Add the structure to the list at the current insertion point (the end of the list if
            // no insertion point has been set)
            orderedTableNames.add((insertionIndex == -1
                                                        ? orderedTableNames.size()
                                                        : insertionIndex),
                                  protoStructName);

            // Check if an insertion point has been set
            if (insertionIndex != -1)
            {
                // Increment the insertion point to account for a table being added to the list
                insertionIndex++;
            }
        }
        // Check if the current insertion point is prior to the prototype's place in the list
        else if (insertionIndex != -1 && protoStructIndex > insertionIndex)
        {
            // Set the insertion index to after this structure
            insertionIndex = protoStructIndex + 1;
        }

        return insertionIndex;
    }

    /**********************************************************************************************
     * Reorder the list of table definitions so that tables referenced as a data type or in a
     * sizeof() call appear in the list prior to the table that references them
     *
     * @param tableDefinitions
     *            list of table definitions for the table(s) to create
     *
     * @return The list of table definitions, reordered so that tables referenced as a data type or
     *         in a sizeof() call appear in the list prior to the table that references them
     *********************************************************************************************/
    private List<TableDefinition> orderTableDefinitionsByReference(List<TableDefinition> tableDefinitions)
    {
        // TODO THIS MAY CAN BE OPTIMIZED

        List<TableDefinition> orderedTableDefinitions = new ArrayList<TableDefinition>();
        List<String> orderedTableNames = new ArrayList<String>();

        // Sort the table definitions by the table path+names. This is to ensure a prototype isn't
        // created as an ancestor when a child already exists that defines the prototype (if the
        // actual prototype exists or the flag to replace existing tables is set then this isn't
        // necessary)
        Collections.sort(tableDefinitions, new Comparator<Object>()
        {
            /**************************************************************************************
             * Compare table names
             *
             * @param tblInfo1
             *            first table's information
             *
             * @param tblInfo2
             *            second table's information
             *
             * @return -1 if the first table's name is lexically less than the second table's name;
             *         0 if the two table names are the same; 1 if the first table's name is
             *         lexically greater than the second table's name
             *************************************************************************************/
            @Override
            public int compare(Object tblInfo1, Object tblInfo2)
            {
                return ((TableDefinition) tblInfo1).getName().compareTo(((TableDefinition) tblInfo2).getName());
            }
        });

        // Step through each table definition
        for (TableDefinition tableDefn : tableDefinitions)
        {
            int insertIndex = -1;

            // Check if the table is an instance (child)
            if (tableDefn.getName().contains(","))
            {
                // Get the index in the list for the table's prototype
                insertIndex = orderedTableNames.indexOf(TableInformation.getPrototypeName(tableDefn.getName()));
            }

            // Check if the table is a prototype or is a child whose prototype isn't in the list
            if (insertIndex == -1)
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
                            // Insert the referenced structure, if not already in the list, and
                            // update the insertion index
                            insertIndex = getStructureInsertionIndex(cellValue,
                                                                     orderedTableNames,
                                                                     insertIndex);
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
                                    // Insert the referenced structure, if not already in the list,
                                    // and update the insertion index
                                    insertIndex = getStructureInsertionIndex(structureName,
                                                                             orderedTableNames,
                                                                             insertIndex);
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

            // Check if the table hasn't been added to the list already
            if (!orderedTableNames.contains(tableDefn.getName()))
            {
                // Add the table name to the list, inserted so that any table it references is
                // prior to it in the list
                orderedTableNames.add((insertIndex == -1
                                                         ? orderedTableNames.size()
                                                         : insertIndex),
                                      tableDefn.getName());
            }
        }

        // Step through each table name in the order it's referenced
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
     * @param tableDefinitions
     *            list of table definitions for the table(s) to create
     *
     * @param replaceExistingTables
     *            true to replace a table that already exists in the database
     *
     * @param replaceExistingMacros
     *            true to replace the values for existing macros
     *
     * @param openEditor
     *            true to open a table editor for each imported table
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If the table path name is invalid or the table cannot be created from the table
     *             definition
     *********************************************************************************************/
    private void createTablesFromDefinitions(List<TableDefinition> tableDefinitions,
                                             boolean replaceExistingTables,
                                             boolean replaceExistingMacros,
                                             boolean openEditor,
                                             CcddGroupHandler groupHandler,
                                             final Component parent) throws CCDDException
    {
        boolean prototypesOnly = true;
        List<String> skippedTables = new ArrayList<String>();

        // Check if the user elected to enable replacement of existing macro values
        if (replaceExistingMacros)
        {
            // Verify that the new macro values are valid for the current instances of the
            // macros
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
                                                                  TableTreeType.TABLES,
                                                                  parent);
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
            // Step through each table definition
            for (TableDefinition tableDefn : tableDefinitions)
            {
                // Check if the table import was canceled by the user
                if (haltDlg != null && haltDlg.isHalted())
                {
                    throw new CCDDException();
                }

                // Check if cell data is provided in the import file. Creation of empty tables is
                // not allowed. Also check if this is a prototype table and this is the first pass,
                // or if this is a child table and this is the second pass
                if (!tableDefn.getData().isEmpty()
                    && (!tableDefn.getName().contains(".") != !prototypesOnly))
                {
                    // Check if the cancel import dialog is present
                    if (haltDlg != null)
                    {
                        // Update the progress bar
                        haltDlg.updateProgressBar(null, -1);
                    }

                    boolean isChildOfNonRoot = false;

                    // Get the table type definition for this table
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                    // Get the number of table columns
                    int numColumns = typeDefn.getColumnCountVisible();

                    // Create the table information for the new table
                    TableInformation tableInfo = new TableInformation(tableDefn.getTypeName(),
                                                                      tableDefn.getName(),
                                                                      new String[0][0],
                                                                      tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                                      tableDefn.getDescription(),
                                                                      fieldHandler.getFieldInformationFromDefinitions(tableDefn.getDataFields()));

                    // Check if the new table is not a prototype. The prototype for the table and
                    // each of its ancestors need to be created if these don't exist
                    if (!tableInfo.isPrototype())
                    {
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
                        if (isChildOfNonRoot
                            && !dbTable.isTableExists(rootTable, ccddMain.getMainFrame()))
                        {
                            boolean isFound = false;

                            // Step through each table definition
                            for (TableDefinition tblDefn : tableDefinitions)
                            {
                                // Check if this definition isn't the one currently being processed
                                // (so as not to it compare to itself) and if this table's path
                                // contains a reference to the current table's root
                                if (!tblDefn.equals(tableDefn)
                                    && rootTable.matches(".+," + tableDefn.getName() + "\\..+"))
                                {
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
                            if (!isFound)
                            {
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
                        for (int index = ancestors.length - 1; index >= 0; index--)
                        {
                            boolean isReplace = false;

                            // Split the ancestor into the data type (i.e., structure name) and
                            // variable name
                            String[] typeAndVar = ancestors[index].split("\\.");

                            // Check if the existing tables are to be replaced
                            if (replaceExistingTables)
                            {
                                isReplace = true;

                                // Step through each table definition
                                for (TableDefinition tblDefn : tableDefinitions)
                                {
                                    // Check if the ancestor's prototype matches a table in the
                                    // list of definitions
                                    if (typeAndVar[0].equals(tblDefn.getName()))
                                    {
                                        // The ancestor's prototype was created explicitly during
                                        // the first pass. Set the flag so that it won't be
                                        // recreated below, and stop searching
                                        isReplace = false;
                                        break;
                                    }
                                }
                            }

                            // Check if the ancestor prototype table should be replaced or doesn't
                            // exist
                            if (isReplace
                                || !dbTable.isTableExists(typeAndVar[0], ccddMain.getMainFrame()))
                            {
                                // Create the table information for the new prototype table
                                TableInformation ancestorInfo = new TableInformation(tableDefn.getTypeName(),
                                                                                     typeAndVar[0],
                                                                                     new String[0][0],
                                                                                     tableTypeHandler.getDefaultColumnOrder(tableDefn.getTypeName()),
                                                                                     "",
                                                                                     new ArrayList<FieldInformation>());

                                // Check if this is the child table and not one of its ancestors
                                if (index == ancestors.length - 1)
                                {
                                    // Create a list to store a copy of the cell data
                                    List<String> protoData = new ArrayList<String>(tableDefn.getData());

                                    // Check if this is the child of a non-root structure
                                    if (isChildOfNonRoot)
                                    {
                                        // Since this child defines its prototype, use all of the
                                        // table data
                                        protoData = tableDefn.getData();
                                    }
                                    // This is the child of a root structure (an instance of the
                                    // table). The instance will contain all of the imported data,
                                    // but the prototype that's created for the child will only get
                                    // the data for columns that are flagged as required. This
                                    // prevents successive children from overwriting the prototype
                                    // with instance-specific cell values
                                    else
                                    {
                                        // Step through each row of the cell data
                                        for (int cellIndex = 0; cellIndex < tableDefn.getData().size(); cellIndex += numColumns)
                                        {
                                            // Step through each column in the row
                                            for (int colIndex = 0; colIndex < numColumns; colIndex++)
                                            {
                                                // Check if the column is not required by the table
                                                // type
                                                if (!DefaultColumn.isTypeRequiredColumn((typeDefn.isStructure()
                                                                                                                ? TYPE_STRUCTURE
                                                                                                                : (typeDefn.isCommand()
                                                                                                                                        ? TYPE_COMMAND
                                                                                                                                        : TYPE_OTHER)),
                                                                                        inputTypeHandler,
                                                                                        typeDefn.getInputTypesVisible()[colIndex]))
                                                {
                                                    // Replace the non-required column value with a
                                                    // blank. The child's non-required values are
                                                    // therefore not inherited from the prototype
                                                    protoData.set(cellIndex + colIndex, "");
                                                }
                                            }
                                        }
                                    }

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo,
                                                             protoData,
                                                             numColumns,
                                                             replaceExistingTables,
                                                             openEditor,
                                                             allTables,
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
                                // This is an ancestor of the child table
                                else
                                {
                                    // Split the ancestor into the data type (i.e., structure name)
                                    // and variable name
                                    typeAndVar = ancestors[index + 1].split("\\.|$", -1);

                                    // Add the variable reference to the new table
                                    String[] rowData = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(rowData, "");
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.VARIABLE))] = typeAndVar[1];
                                    rowData[typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(DefaultInputType.PRIM_AND_STRUCT))] = typeAndVar[0];

                                    // Create the prototype of the child table and populate it with
                                    // the protected column data
                                    if (!createImportedTable(ancestorInfo,
                                                             Arrays.asList(rowData),
                                                             numColumns,
                                                             replaceExistingTables,
                                                             openEditor,
                                                             allTables,
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

                        // Check if this is the child of a root structure (an instance of the
                        // table). If this is a child of a non-root table then its prototype is
                        // created above and doesn't need to be created below
                        if (!isChildOfNonRoot)
                        {
                            // Load the table's prototype data from the database and copy the
                            // prototype's data to the table. The data from the import file is
                            // pasted over the prototype's
                            TableInformation protoInfo = dbTable.loadTableData(tableInfo.getPrototypeName(),
                                                                               false,
                                                                               false,
                                                                               ccddMain.getMainFrame());
                            tableInfo.setData(protoInfo.getData());
                        }
                    }

                    // Check if this is the child of a root structure (an instance of the table).
                    // If this is a child of a non-root table then its prototype is created above
                    // and doesn't need to be created here
                    if (!isChildOfNonRoot)
                    {
                        // Create a table from the imported information
                        if (!createImportedTable(tableInfo,
                                                 tableDefn.getData(),
                                                 numColumns,
                                                 replaceExistingTables,
                                                 openEditor,
                                                 allTables,
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
                }
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
                                                                          DataTypesColumn.OID.ordinal()),
                                      null,
                                      parent);

        // Store the input types
        dbTable.storeInformationTable(InternalTable.INPUT_TYPES,
                                      CcddUtilities.removeArrayListColumn(Arrays.asList(inputTypeHandler.getCustomInputTypeData()),
                                                                          InputTypesColumn.OID.ordinal()),
                                      null,
                                      parent);

        // Store the data fields
        dbTable.storeInformationTable(InternalTable.FIELDS,
                                      fieldHandler.getFieldDefnsFromInfo(),
                                      null,
                                      parent);

        // Store the groups
        ccddMain.getDbTableCommandHandler().storeInformationTable(InternalTable.GROUPS,
                                                                  groupHandler.getGroupDefnsFromInfo(),
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

    /**********************************************************************************************
     * Create a new data table or replace an existing one and paste the supplied cell data into it
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
     * @param openEditor
     *            true to open a table editor for each imported table
     *
     * @param allTables
     *            list containing the paths and names of all tables
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the table is successfully imported; false if the table exists and the
     *         replaceExisting flag is not true
     *
     * @throws CCDDException
     *             The existing table that the new table replaces cannot be removed, the new table
     *             cannot be created, or the data cannot be added to the newly created table
     *********************************************************************************************/
    private boolean createImportedTable(TableInformation tableInfo,
                                        List<String> cellData,
                                        int numColumns,
                                        boolean replaceExisting,
                                        boolean openEditor,
                                        List<String> allTables,
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
        // The table doesn't exist or the user elected to overwrite the existing table
        else
        {
            // Check if this table is a prototype
            if (tableInfo.isPrototype())
            {
                // Check if the table exists
                if (isExists)
                {
                    // Delete the existing table from the database
                    if (dbTable.deleteTable(new String[] {tableInfo.getPrototypeName()},
                                            null,
                                            ccddMain.getMainFrame()))
                    {
                        throw new CCDDException();
                    }

                    // Add the prototype table name to the list of table editors to close
                    tableName.add(new String[] {tableInfo.getPrototypeName(), null});
                }

                // Create the table in the database
                if (dbTable.createTable(new String[] {tableInfo.getPrototypeName()},
                                        tableInfo.getDescription(),
                                        tableInfo.getType(),
                                        parent))
                {
                    throw new CCDDException();
                }
            }
            // Not a prototype table. Check if the child structure table exists
            else if (isExists)
            {
                // Add the parent and prototype table name to the list of table editors to close
                tableName.add(new String[] {tableInfo.getParentTable(),
                                            tableInfo.getPrototypeName()});
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
                // table already has the field
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
                List<TableInformation> tableInformation = new ArrayList<TableInformation>();
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

                    // Force the dialog to the front
                    haltDlg.toFront();
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
                                                 false))
            {
                eventLog.logEvent(EventLogMessageType.STATUS_MSG, "Import canceled by user");
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
     * @param tableHandler
     *            reference to the table handler for the table into which to import the data
     *********************************************************************************************/
    protected void importSelectedFileIntoTable(CcddTableEditorHandler tableHandler)
    {
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
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Create an empty border
        Border emptyBorder = BorderFactory.createEmptyBorder();

        // Create overwrite check box
        JCheckBox overwriteChkBx = new JCheckBox("Overwrite existing cells");
        overwriteChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        overwriteChkBx.setBorder(emptyBorder);
        overwriteChkBx.setToolTipText(CcddUtilities.wrapText("Overwrite existing cell data; if unchecked then new "
                                                             + "rows are inserted to contain the imported data",
                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        overwriteChkBx.setSelected(false);

        // Create a check box for indicating existing tables can be replaced
        JCheckBox useExistingFieldsCb = new JCheckBox("Use existing field if duplicate");
        useExistingFieldsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        useExistingFieldsCb.setBorder(emptyBorder);
        useExistingFieldsCb.setToolTipText(CcddUtilities.wrapText("Use the existing data field definition if "
                                                                  + "a field with the same name is imported",
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        useExistingFieldsCb.setSelected(true);

        // Create a check box for indicating that all errors in the import file should be ignored
        JCheckBox ignoreErrorsCb = new JCheckBox("Ignore all import file errors");
        ignoreErrorsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        ignoreErrorsCb.setBorder(emptyBorder);
        ignoreErrorsCb.setToolTipText(CcddUtilities.wrapText("Ignore all import file errors",
                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Create a panel to contain the check boxes
        JPanel checkBoxPnl = new JPanel(new GridBagLayout());
        checkBoxPnl.setBorder(emptyBorder);
        checkBoxPnl.add(overwriteChkBx, gbc);
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        checkBoxPnl.add(useExistingFieldsCb, gbc);
        gbc.gridy++;
        checkBoxPnl.add(ignoreErrorsCb, gbc);

        // Allow the user to select the data file path + name to import from
        FileEnvVar[] dataFile = new CcddDialogHandler().choosePathFile(ccddMain,
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
                                                                       "Import Table Data",
                                                                       ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(), null),
                                                                       DialogOption.IMPORT_OPTION,
                                                                       checkBoxPnl);

        // Check if a file was chosen
        if (dataFile != null && dataFile[0] != null)
        {
            try
            {
                List<TableDefinition> tableDefinitions = null;
                CcddImportExportInterface ioHandler = null;

                // Check if the file to import is in CSV format based on the extension
                if (dataFile[0].getAbsolutePath().endsWith(FileExtension.CSV.getExtension()))
                {
                    // Create a CSV handler
                    ioHandler = new CcddCSVHandler(ccddMain, null, tableHandler.getOwner());
                }
                // Check if the file to import is in EDS XML format based on the extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.EDS.getExtension()))
                {
                    // Create an EDS handler
                    ioHandler = new CcddEDSHandler(ccddMain, tableHandler.getOwner());
                }
                // Check if the file to import is in JSON format based on the extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.JSON.getExtension()))
                {
                    // Create a JSON handler
                    ioHandler = new CcddJSONHandler(ccddMain, null, tableHandler.getOwner());
                }
                // Check if the file to import is in XTCE XML format based on the extension
                else if (dataFile[0].getAbsolutePath().endsWith(FileExtension.XTCE.getExtension()))
                {
                    // Create an XTCE handler
                    ioHandler = new CcddXTCEHandler(ccddMain, tableHandler.getOwner());
                }
                // The file extension isn't recognized
                else
                {
                    throw new IOException("Cannot import file '</b>"
                                          + dataFile[0].getAbsolutePath()
                                          + "<b>' into table; unrecognized file type");
                }

                // Store the current table type information so that it can be restored
                List<TypeDefinition> originalTableTypes = tableTypeHandler.getTypeDefinitions();

                // Import the data file into a table definition
                ioHandler.importFromFile(dataFile[0],
                                         ImportType.FIRST_DATA_ONLY,
                                         tableHandler.getTableTypeDefinition(),
                                         ignoreErrorsCb.isSelected(),
                                         false,
                                         false);
                tableDefinitions = ioHandler.getTableDefinitions();

                // Check if a table definition was successfully created
                if (tableDefinitions != null && !tableDefinitions.isEmpty())
                {
                    // Get a short-cut to the table definition to shorten subsequent calls
                    TableDefinition tableDefn = tableDefinitions.get(0);

                    // End any active edit sequence, then disable auto-ending so that the import
                    // operation can be handled as a single edit for undo/redo purposes
                    tableHandler.getTable().getUndoManager().endEditSequence();
                    tableHandler.getTable().getUndoHandler().setAutoEndEditSequence(false);

                    // Update the table description field in case the description changed
                    tableHandler.setDescription(tableDefn.getDescription());

                    // Add the imported data field(s) to the table
                    addImportedDataField(tableDefn,
                                         tableHandler.getTableInformation().getTablePath(),
                                         useExistingFieldsCb.isSelected());

                    // Rebuild the table's editor panel which contains the data fields
                    tableHandler.createDataFieldPanel(true,
                                                      fieldHandler.getFieldInformationFromData(tableDefn.getDataFields().toArray(new String[0][0]),
                                                                                               tableDefn.getName()));

                    // Check if cell data is provided in the table definition
                    if (tableDefn.getData() != null && !tableDefn.getData().isEmpty())
                    {
                        // Get the original number of rows in the table
                        int numRows = tableHandler.getTableModel().getRowCount();

                        // Paste the data into the table; check if the user hasn't canceled
                        // importing the table following a cell validation error
                        if (!tableHandler.getTable().pasteData(tableDefn.getData().toArray(new String[0]),
                                                               tableHandler.getTable().getColumnCount(),
                                                               !overwriteChkBx.isSelected(),
                                                               !overwriteChkBx.isSelected(),
                                                               true,
                                                               false,
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

                    // Restore the table types to the values prior to the import operation
                    tableTypeHandler.setTypeDefinitions(originalTableTypes);

                    // Re-enable auto-ending of the edit sequence and end the sequence. The
                    // imported data can be removed with a single undo if desired
                    tableHandler.getTable().getUndoHandler().setAutoEndEditSequence(true);
                    tableHandler.getTable().getUndoManager().endEditSequence();

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
                                                              "<html><b>" + ce.getMessage(),
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
    }

    /**********************************************************************************************
     * Import one or more data fields into a table, appending them to any existing fields. If the
     * imported field already exists then the input flag determines if the existing or imported
     * field is used
     *
     * @param tableDefn
     *            imported table definition
     *
     * @param ownerName
     *            data field owner name
     *
     * @param useExistingFields
     *            true to replace an existing data field with the imported ones if the field names
     *            match
     *********************************************************************************************/
    private void addImportedDataField(TableDefinition tableDefn,
                                      String ownerName,
                                      boolean useExistingFields)
    {
        // Step through the imported data fields. The order is reversed so that field definitions
        // can be removed if needed
        for (int index = tableDefn.getDataFields().size() - 1; index >= 0; index--)
        {
            // Get the reference to the data field definitions to shorten subsequent calls
            String[] fieldDefn = tableDefn.getDataFields().get(index);

            // Set the data field owner to the specified table (if importing entire tables this
            // isn't necessary, but is when importing into an existing table since the owner in the
            // import file may differ)
            fieldDefn[FieldsColumn.OWNER_NAME.ordinal()] = ownerName;

            // Get the reference to the data field based on the table name and field name
            FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerName,
                                                                                fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]);

            // Check if the data field already exists (name and input type must match) and the user
            // has elected to use existing fields over ones in the import file
            if (fieldInfo != null
                && useExistingFields
                && fieldInfo.getInputType().getInputName().equals(fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]))
            {
                // Remove the new data field definition and replace it with the existing definition
                tableDefn.getDataFields().remove(index);
                tableDefn.getDataFields().add(index,
                                              new String[] {ownerName,
                                                            fieldInfo.getFieldName(),
                                                            fieldInfo.getDescription(),
                                                            String.valueOf(fieldInfo.getSize()),
                                                            fieldInfo.getInputType().getInputName(),
                                                            String.valueOf(fieldInfo.isRequired()),
                                                            fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                            fieldInfo.getValue(),
                                                            "false"});
            }
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
     * @param filePath
     *            path to the folder in which to store the exported tables. Includes the name if
     *            storing the tables to a single file
     *
     * @param tablePaths
     *            table path for each table to load
     *
     * @param overwriteFile
     *            true to store overwrite an existing file; false skip exporting a table to a file
     *            that already exists
     *
     * @param singleFile
     *            true to store multiple tables in a single file; false to store each table in a
     *            separate file
     *
     * @param includeBuildInformation
     *            true to include the CCDD version, project, host, and user information
     *
     * @param replaceMacros
     *            true to replace macros with their corresponding values; false to leave the macros
     *            intact
     *
     * @param includeAllTableTypes
     *            true to include the all table type definitions in the export file
     *
     * @param includeAllDataTypes
     *            true to include the all data type definitions in the export file
     *
     * @param includeAllInputTypes
     *            true to include the all user-defined input type definitions in the export file
     *
     * @param includeAllMacros
     *            true to include the all macro definitions in the export file
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table in the export file
     *
     * @param includeProjectFields
     *            true to include the project-level data field definitions in the export file
     *
     * @param includeGroups
     *            true to include the groups and group data field definitions in the export file
     *
     * @param includeAssociations
     *            true to include the script associations in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a structure table, both in
     *            application format and using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *
     * @param fileExtn
     *            file extension type
     *
     * @param endianess
     *            EndianType.BIG_ENDIAN for big endian, EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian
     *            true if the telemetry and command headers are always big endian (e.g., as with
     *            CCSDS)
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
     * @param useExternal
     *            true to use external (script) methods in place of the internal ones (XTCE only)
     *
     * @param scriptFileName
     *            name of the script file containing the external (script) methods (XTCE only);
     *            ignored if useExternal is false
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void exportSelectedTablesInBackground(final String filePath,
                                                    final String[] tablePaths,
                                                    final boolean overwriteFile,
                                                    final boolean singleFile,
                                                    final boolean includeBuildInformation,
                                                    final boolean replaceMacros,
                                                    final boolean includeAllTableTypes,
                                                    final boolean includeAllDataTypes,
                                                    final boolean includeAllInputTypes,
                                                    final boolean includeAllMacros,
                                                    final boolean includeReservedMsgIDs,
                                                    final boolean includeProjectFields,
                                                    final boolean includeGroups,
                                                    final boolean includeAssociations,
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
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Export the selected table(s)
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Export the selected table(s)
                exportSelectedTables(filePath,
                                     tablePaths,
                                     overwriteFile,
                                     singleFile,
                                     includeBuildInformation,
                                     replaceMacros,
                                     includeAllTableTypes,
                                     includeAllDataTypes,
                                     includeAllInputTypes,
                                     includeAllMacros,
                                     includeReservedMsgIDs,
                                     includeProjectFields,
                                     includeGroups,
                                     includeAssociations,
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
    }

    /**********************************************************************************************
     * Export the contents of one or more tables selected by the user to one or more files in the
     * specified format. The export file names are based on the table name if each table is stored
     * in a separate file. The user supplied file name is used if multiple tables are stored in a
     * single file
     *
     * @param filePath
     *            path to the folder in which to store the exported tables. Includes the name if
     *            storing the tables to a single file
     *
     * @param tablePaths
     *            table path for each table to load
     *
     * @param overwriteFile
     *            true to store overwrite an existing file; false skip exporting a table to a file
     *            that already exists
     *
     * @param singleFile
     *            true to store multiple tables in a single file; false to store each table in a
     *            separate file
     *
     * @param includeBuildInformation
     *            true to include the CCDD version, project, host, and user information
     *
     * @param replaceMacros
     *            true to replace macros with their corresponding values; false to leave the macros
     *            intact
     *
     * @param includeAllTableTypes
     *            true to include the all table type definitions in the export file
     *
     * @param includeAllDataTypes
     *            true to include the all data type definitions in the export file
     *
     * @param includeAllInputTypes
     *            true to include the all user-defined input type definitions in the export file
     *
     * @param includeAllMacros
     *            true to include the all macro definitions in the export file
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table in the export file
     *
     * @param includeProjectFields
     *            true to include the project-level data field definitions in the export file
     *
     * @param includeGroups
     *            true to include the groups and group data field definitions in the export file
     *
     * @param includeAssociations
     *            true to include the script associations in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a structure table, both in
     *            application format and using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *
     * @param fileExtn
     *            file extension type
     *
     * @param endianess
     *            EndianType.BIG_ENDIAN for big endian, EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian
     *            true if the telemetry and command headers are always big endian (e.g., as with
     *            CCSDS)
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
     * @param useExternal
     *            true to use external (script) methods in place of the internal ones (XTCE only)
     *
     * @param scriptFileName
     *            name of the script file containing the external (script) methods (XTCE only);
     *            ignored if useExternal is false
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the export completes successfully
     *********************************************************************************************/
    protected boolean exportSelectedTables(final String filePath,
                                           final String[] tablePaths,
                                           final boolean overwriteFile,
                                           final boolean singleFile,
                                           final boolean includeBuildInformation,
                                           final boolean replaceMacros,
                                           final boolean includeAllTableTypes,
                                           final boolean includeAllDataTypes,
                                           final boolean includeAllInputTypes,
                                           final boolean includeAllMacros,
                                           final boolean includeReservedMsgIDs,
                                           final boolean includeProjectFields,
                                           final boolean includeGroups,
                                           final boolean includeAssociations,
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
        boolean errorFlag = false;
        FileEnvVar file = null;
        CcddImportExportInterface ioHandler = null;
        List<String> skippedTables = new ArrayList<String>();
        ScriptEngine scriptEngine = null;

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
                // but are these loaded if an access method requiring them is called

                // Get the script engine for the supplied script file name
                scriptEngine = ccddMain.getScriptHandler().getScriptEngine(scriptFileName,
                                                                           new TableInformation[0],
                                                                           null,
                                                                           null,
                                                                           null,
                                                                           parent);
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

            // Check if the output format is CSV
            if (fileExtn == FileExtension.CSV)
            {
                // Create a CSV handler
                ioHandler = new CcddCSVHandler(ccddMain,
                                               new CcddGroupHandler(ccddMain, null, parent),
                                               parent);
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
                ioHandler = new CcddJSONHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain, null, parent),
                                                parent);
            }
            // Check if the output format is XTCE XML
            else if (fileExtn == FileExtension.XTCE)
            {
                // Create an XTCE handler
                ioHandler = new CcddXTCEHandler(ccddMain, scriptEngine, parent);
            }

            // Check if the tables are to be exported to a single file
            if (singleFile)
            {
                // Check if the file doesn't exist, or if it does and the user elects to overwrite
                // it
                if (isOverwriteExportFileIfExists(file, overwriteFile, parent))
                {
                    // Export the formatted table data to the specified file
                    ioHandler.exportToFile(file,
                                           tablePaths,
                                           includeBuildInformation,
                                           replaceMacros,
                                           includeAllTableTypes,
                                           includeAllDataTypes,
                                           includeAllInputTypes,
                                           includeAllMacros,
                                           includeReservedMsgIDs,
                                           includeProjectFields,
                                           includeGroups,
                                           includeAssociations,
                                           includeVariablePaths,
                                           variableHandler,
                                           separators,
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
            // Export the table(s) to individual files
            else
            {
                // Step through each table
                for (String tablePath : tablePaths)
                {
                    // Create the file using a name derived from the table name
                    file = new FileEnvVar(path
                                          + tablePath.replaceAll("[,\\.\\[\\]]", "_")
                                          + fileExtn.getExtension());

                    // Check if the file doesn't exist, or if it does and the user elects to
                    // overwrite it
                    if (isOverwriteExportFileIfExists(file, overwriteFile, parent))
                    {
                        // Export the formatted table data; the file name is derived from the table
                        // name
                        ioHandler.exportToFile(file,
                                               new String[] {tablePath},
                                               includeBuildInformation,
                                               replaceMacros,
                                               includeAllTableTypes,
                                               includeAllDataTypes,
                                               includeAllInputTypes,
                                               includeAllMacros,
                                               includeReservedMsgIDs,
                                               includeProjectFields,
                                               includeGroups,
                                               includeAssociations,
                                               includeVariablePaths,
                                               variableHandler,
                                               separators,
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
                    // The table is skipped
                    else
                    {
                        // Add the skipped table to the list
                        skippedTables.add(tablePath);
                    }
                }
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

            // Store the export file path in the program preferences backing store
            storePath(ccddMain,
                      filePath,
                      singleFile,
                      ModifiablePathInfo.TABLE_EXPORT_PATH);

            // Check if no errors occurred exporting the table(s)
            if (!errorFlag)
            {
                eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                                  "Data export completed successfully");
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
                                                              + file.getAbsolutePath()
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
     * @param exportFile
     *            reference to the file
     *
     * @param overwriteFile
     *            true to overwrite an existing file
     *
     * @param parent
     *            GUI component over which to center any error dialog
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
     * @param file
     *            reference to the file to store
     *********************************************************************************************/
    protected void storeScriptInDatabase(FileEnvVar file)
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
                    // Get the index of the description tag (-1 if not found). Force to lower case
                    // to remove case sensitivity
                    int index = line.toLowerCase().indexOf(SCRIPT_DESCRIPTION_TAG);

                    // Check if the description tag is found
                    if (index != -1)
                    {
                        // Extract the description from the file line
                        description = line.substring(index + SCRIPT_DESCRIPTION_TAG.length())
                                          .trim();
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
     * @param script
     *            name of the script to retrieve
     *
     * @param file
     *            reference to the script file
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
                                                                               + " script file '</b>"
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
     * @param ccddMain
     *            main class reference
     *
     * @param pathName
     *            file path
     *
     * @param hasFileName
     *            true if the file path includes a file name. The file name is removed before
     *            storing the path
     *
     * @param modPath
     *            ModifiablePathInfo reference for the path being updated
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
     * Open the specified file for writing
     *
     * @param outputFileName
     *            output file path + name
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
                                                                               + outputFileName
                                                                               + "<b>'",
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
     * @param printWriter
     *            output file PrintWriter object
     *
     * @param text
     *            text to write to the output file
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
     * @param printWriter
     *            output file PrintWriter object
     *
     * @param text
     *            text to write to the output file
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
     * @param printWriter
     *            output file PrintWriter object
     *
     * @param format
     *            print format
     *
     * @param args
     *            arguments referenced by the format specifiers in the format string
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
     * @param printWriter
     *            output file PrintWriter object
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
}
