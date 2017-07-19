/**
 * CFS Command & Data Dictionary. Copyright 2017 United States Government as
 * represented by the Administrator of the National Aeronautics and Space
 * Administration. No copyright is claimed in the United States under Title 17,
 * U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CCDD_AUTHOR;
import static CCDD.CcddConstants.CCDD_ICON;
import static CCDD.CcddConstants.DATABASE;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_HOST;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_PORT;
import static CCDD.CcddConstants.DEFAULT_SERVER;
import static CCDD.CcddConstants.INIT_WINDOW_HEIGHT;
import static CCDD.CcddConstants.INIT_WINDOW_WIDTH;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LOOK_AND_FEEL;
import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_HOST;
import static CCDD.CcddConstants.POSTGRESQL_SERVER_PORT;
import static CCDD.CcddConstants.TOOL_TIP_TEXT_COLOR;
import static CCDD.CcddConstants.USER;
import static CCDD.CcddConstants.WEB_SERVER_PORT;
import static CCDD.CcddConstants.setLaFAdjustments;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import CCDD.CcddConstants.DbManagerDialogType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ScriptIOType;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.ServerPropertyDialogType;

/******************************************************************************
 * CFS Command & Data Dictionary main class
 *****************************************************************************/
public class CcddMain
{
    // Class references
    private final CcddDbCommandHandler dbCommand;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddTableTypeEditorDialog tableTypeEditorDialog;
    private CcddDataTypeEditorDialog dataTypeEditorDialog;
    private CcddMacroEditorDialog macroEditorDialog;
    private CcddReservedMsgIDEditorDialog msgIDEditorDialog;
    private CcddGroupManagerDialog groupManagerDialog;
    private final CcddFileIOHandler fileIOHandler;
    private final CcddScriptHandler scriptHandler;
    private CcddFieldTableEditorDialog fieldTblEditorDialog;
    private CcddRateParameterHandler rateHandler;
    private CcddApplicationParameterHandler appHandler;
    private final CcddKeyboardHandler keyboardHandler;
    private CcddMacroHandler macroHandler;
    private CcddReservedMsgIDHandler rsvMsgIDHandler;
    private CcddWebServer webServer;

    // References to the various search dialogs
    private CcddSearchDialog searchLogDlg;
    private CcddSearchDialog searchTableDlg;
    private CcddSearchDialog searchScriptDlg;

    // List of open log files
    private final List<CcddEventLogDialog> eventLogs;

    // List of open table editor dialogs
    private final List<CcddTableEditorDialog> tableEditorDialogs;

    // Components that need to be accessed by other classes or listeners
    private JFrame frameCCDD;
    private JMenuItem mntmUser;
    private JMenuItem mntmDbServer;
    private JMenuItem mntmReadLog;
    private JMenuItem mntmPrintLog;
    private JMenuItem mntmSearchLog;
    private JCheckBoxMenuItem mntmEnableWebServer;
    private JMenuItem mntmWebServerPort;
    private JMenuItem mntmOpenDb;
    private JMenuItem mntmCloseDb;
    private JMenuItem mntmNewDb;
    private JMenuItem mntmRenameDb;
    private JMenuItem mntmCopyDb;
    private JMenuItem mntmDeleteDb;
    private JMenuItem mntmBackupDb;
    private JMenuItem mntmRestoreDb;
    private JMenuItem mntmUnlock;
    private JMenuItem mntmVerifyDatabase;
    private JMenuItem mntmNewTable;
    private JMenuItem mntmEditTable;
    private JMenuItem mntmRenameTable;
    private JMenuItem mntmCopyTable;
    private JMenuItem mntmDeleteTable;
    private JMenuItem mntmImportTable;
    private JMenuItem mntmExportCSV;
    private JMenuItem mntmExportEDS;
    private JMenuItem mntmExportJSON;
    private JMenuItem mntmExportXTCE;
    private JMenuItem mntmManageDataTypes;
    private JMenuItem mntmManageTableTypes;
    private JMenuItem mntmManageGroups;
    private JMenuItem mntmManageMacros;
    private JMenuItem mntmAssignMsgID;
    private JMenuItem mntmReserveMsgID;
    private JMenuItem mntmDuplicateMsgID;
    private JMenuItem mntmEditDataField;
    private JMenuItem mntmShowVariables;
    private JMenuItem mntmSearchTable;
    private JMenuItem mntmManageLinks;
    private JMenuItem mntmManageTlm;
    private JMenuItem mntmManageApps;
    private JMenuItem mntmRateParameters;
    private JMenuItem mntmAppParameters;
    private JMenuItem mntmManageScripts;
    private JMenuItem mntmExecuteScripts;
    private JMenuItem mntmStoreScripts;
    private JMenuItem mntmRetrieveScripts;
    private JMenuItem mntmDeleteScripts;
    private JMenuItem mntmSearchScripts;
    private JMenuItem mntmExit;

    // Build information
    private String ccddVersion;
    private String buildDate;

    // Label containing the currently open database name for display in the
    // main application window above the session event log
    private JLabel currentDatabase;

    // Program preferences backing store node
    private final Preferences progPrefs;

    // File path to where the session log is stored
    private String logFilePath;

    // Look and feel currently selected by the user
    private String selectedLaF;

    // Flag indicating if dialog messages should be displayed on the command
    // line instead of in dialog boxes
    boolean isHideGUI;

    /**************************************************************************
     * Create the application
     *************************************************************************/
    private CcddMain(String[] args)
    {
        // Create the shutdown handler so that the database and event log file
        // are closed
        createShutdownHook();

        // Clear the flags so that dialog messages appear in dialog boxes and
        // the web service is not enabled
        isHideGUI = false;
        webServer = null;

        // Create lists to store references to open event logs and table editor
        // dialogs
        eventLogs = new ArrayList<CcddEventLogDialog>();
        tableEditorDialogs = new ArrayList<CcddTableEditorDialog>();

        // Get the backing store node for storing the program preference keys
        // and values. These are stored by user so that different users can
        // have their own preferences
        progPrefs = Preferences.userNodeForPackage(this.getClass());

        // Create the command line handler
        CcddCommandLineHandler cmdLnHandler = new CcddCommandLineHandler(CcddMain.this, args);

        // Check if the command that sets the session event log file path is
        // present, and if so set the path
        cmdLnHandler.parseCommand(true);

        // Create the database command and control handler classes
        dbCommand = new CcddDbCommandHandler(CcddMain.this);
        dbControl = new CcddDbControlHandler(CcddMain.this);

        // Create the session event log and set it in the command and control
        // classes
        eventLogs.add(new CcddEventLogDialog(CcddMain.this, true));
        dbCommand.setEventLog();
        dbControl.setEventLog();

        // Create the handler classes for database table commands, file I/O,
        // scripts, and application parameters
        dbTable = new CcddDbTableCommandHandler(CcddMain.this);
        fileIOHandler = new CcddFileIOHandler(CcddMain.this);
        scriptHandler = new CcddScriptHandler(CcddMain.this);

        // Build the main window
        initialize();

        // Create a keyboard handler to adjust the response to the Enter key to
        // act like the Space key to activate certain control types and to
        // adjust the response to the arrow keys based on the component with
        // the keyboard focus. Also handle table undo/redo actions
        keyboardHandler = new CcddKeyboardHandler(CcddMain.this);

        // Get the program preferences
        getProgramPreferences();

        // Set the selected look & feel
        setLookAndFeel(null);

        // Read the command line arguments and make adjustments as needed
        cmdLnHandler.parseCommand(false);

        // Make the main application window visible if the GUI set to be active
        frameCCDD.setVisible(!isGUIHidden());

        // Force tool tip pop-ups to effectively remain visible until the mouse
        // pointer moves away from the object
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        // Check if a database, user, and host are specified
        if (!dbControl.getDatabase().isEmpty()
            && !dbControl.getUser().isEmpty()
            && !dbControl.getHost().isEmpty())
        {
            // Attempt to connect to the database that was open at the
            // termination of the previous session using the parameters from
            // the command line and/or backing store. If a password is
            // required, but not provided, then request the password and
            // attempt to connect. If this connection attempt fails then
            // attempt to connect to the default database
            dbControl.openDatabaseInBackground(dbControl.getDatabase());
        }
    }

    /**************************************************************************
     * Launch the application
     * 
     * @param args
     *            array of command line arguments
     *************************************************************************/
    public static void main(final String[] args)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            /******************************************************************
             * Execute the main class
             *****************************************************************/
            @Override
            public void run()
            {
                try
                {
                    new CcddMain(args);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    /**************************************************************************
     * Get the main application frame
     * 
     * @return Main application frame; null if the flag is set to hide the GUI
     *************************************************************************/
    protected JFrame getMainFrame()
    {
        return isGUIHidden() ? null : frameCCDD;
    }

    /**************************************************************************
     * Set the flag that indicates if the GUI is hidden. If so, dialog messages
     * are sent to the command line or a dialog box. Any dialogs requiring user
     * input, including question message dialogs, are displayed in dialog boxes
     * 
     * @param isHideGUI
     *            true if dialog messages should appear on the command line;
     *            false to display the message in a dialog box
     *************************************************************************/
    protected void setGUIHidden(boolean isHideGUI)
    {
        this.isHideGUI = isHideGUI;
    }

    /**************************************************************************
     * Get the status of the flag that indicates if the GUI is hidden
     * 
     * @return true if the GUI is not visible
     *************************************************************************/
    protected boolean isGUIHidden()
    {
        return isHideGUI;
    }

    /**************************************************************************
     * Get the session event log file path
     * 
     * @return Session event log file path; if no log file path is set then
     *         return blank to point to the folder in which the application
     *         starts
     *************************************************************************/
    protected String getLogPath()
    {
        return logFilePath == null ? "" : logFilePath;
    }

    /**************************************************************************
     * Set the session event log file path
     * 
     * @param logFilePath
     *            session event log file path
     *************************************************************************/
    protected void setLogPath(String logFilePath)
    {
        this.logFilePath = logFilePath;
    }

    /**************************************************************************
     * Start the web server
     * 
     * @param gui
     *            'gui' if the graphical user interface should be displayed;
     *            any other text to not show the user interface and to direct
     *            dialog messages to the command line
     *************************************************************************/
    protected void setWebServer(String gui)
    {
        // Create the web server
        webServer = new CcddWebServer(this);

        // Enable the web server check box menu item
        mntmEnableWebServer.setSelected(true);

        // Enable the server log message filter
        CcddMain.this.getSessionEventLog().setServerFilterEnable(true);

        // Check if the user interface shouldn't be displayed
        if (!gui.equals("gui"))
        {
            // Set the flag to indicate that dialog messages should be sent to
            // the command line
            setGUIHidden(true);
        }
    }

    /**************************************************************************
     * Get the reference to the web server
     * 
     * @return Reference to the web server
     *************************************************************************/
    protected CcddWebServer getWebServer()
    {
        return webServer;
    }

    /**************************************************************************
     * Get the status of the flag that indicates if the web server is enabled
     * 
     * @return true if the web server is enabled
     *************************************************************************/
    protected boolean isWebServer()
    {
        return mntmEnableWebServer.isSelected();
    }

    /**************************************************************************
     * Set the web server port
     * 
     * @param port
     *            web server port
     *************************************************************************/
    protected void setWebServerPort(String port)
    {
        progPrefs.put(WEB_SERVER_PORT, port);
    }

    /**************************************************************************
     * Get the program preferences
     * 
     * @return Program preferences
     *************************************************************************/
    protected Preferences getProgPrefs()
    {
        return progPrefs;
    }

    /**************************************************************************
     * Get the session event log
     * 
     * @return Session event log
     *************************************************************************/
    protected CcddEventLogDialog getSessionEventLog()
    {
        return eventLogs.get(0);
    }

    /**************************************************************************
     * Get the database control handler
     * 
     * @return Database control handler
     *************************************************************************/
    protected CcddDbControlHandler getDbControlHandler()
    {
        return dbControl;
    }

    /**************************************************************************
     * Get the database command handler
     * 
     * @return Database command handler
     *************************************************************************/
    protected CcddDbCommandHandler getDbCommandHandler()
    {
        return dbCommand;
    }

    /**************************************************************************
     * Get the table command handler
     * 
     * @return Database table command handler
     *************************************************************************/
    protected CcddDbTableCommandHandler getDbTableCommandHandler()
    {
        return dbTable;
    }

    /**************************************************************************
     * Create the handler classes that rely on a successful connection to a
     * project database (other than the default): table type, macro, and rate
     * parameter handlers. Set the references to these handlers in the
     * persistent classes that use them
     *************************************************************************/
    protected void setDbSpecificHandlers()
    {
        // Read the table type definitions from the database
        tableTypeHandler = new CcddTableTypeHandler(CcddMain.this);

        // Read the data types definitions from the database
        dataTypeHandler = new CcddDataTypeHandler(CcddMain.this);

        // Read the macro definitions from the database
        macroHandler = new CcddMacroHandler(CcddMain.this);

        // Read the rate parameters from the project database
        rateHandler = new CcddRateParameterHandler(CcddMain.this);

        // Read the application parameters from the project database
        appHandler = new CcddApplicationParameterHandler(CcddMain.this);

        // Read the reserved message IDs from the project database
        rsvMsgIDHandler = new CcddReservedMsgIDHandler(CcddMain.this);

        // Now that the handlers exist, store its reference in the other
        // persistent classes that use them
        CcddClasses.setHandlers(dataTypeHandler, macroHandler);
        dbTable.setHandlers();
        fileIOHandler.setHandlers();
        scriptHandler.setHandlers();
        keyboardHandler.setHandlers();

        // Check if the web server is activated
        if (webServer != null)
        {
            webServer.getWebAccessHandler().setHandlers();
        }
    }

    /**************************************************************************
     * Get the type handler
     * 
     * @return Type handler
     *************************************************************************/
    protected CcddTableTypeHandler getTableTypeHandler()
    {
        return tableTypeHandler;
    }

    /**************************************************************************
     * Get the macro handler
     * 
     * @return Macro handler
     *************************************************************************/
    protected CcddMacroHandler getMacroHandler()
    {
        return macroHandler;
    }

    /**************************************************************************
     * Get the data type handler
     * 
     * @return Data type handler
     *************************************************************************/
    protected CcddDataTypeHandler getDataTypeHandler()
    {
        return dataTypeHandler;
    }

    /**************************************************************************
     * Get the file handler
     * 
     * @return File handler
     *************************************************************************/
    protected CcddFileIOHandler getFileIOHandler()
    {
        return fileIOHandler;
    }

    /**************************************************************************
     * Get the reserved message ID handler
     * 
     * @return Reserved message ID handler
     *************************************************************************/
    protected CcddReservedMsgIDHandler getReservedMsgIDHandler()
    {
        return rsvMsgIDHandler;
    }

    /**************************************************************************
     * Get the script handler
     * 
     * @return Script handler
     *************************************************************************/
    protected CcddScriptHandler getScriptHandler()
    {
        return scriptHandler;
    }

    /**************************************************************************
     * Get the rate parameter handler
     * 
     * @return Rate parameter handler
     *************************************************************************/
    protected CcddRateParameterHandler getRateParameterHandler()
    {
        return rateHandler;
    }

    /**************************************************************************
     * Get the application parameter handler
     * 
     * @return Rate parameter handler
     *************************************************************************/
    protected CcddApplicationParameterHandler getApplicationParameterHandler()
    {
        return appHandler;
    }

    /**************************************************************************
     * Get the keyboard handler
     * 
     * @return Keyboard handler reference
     *************************************************************************/
    protected CcddKeyboardHandler getKeyboardHandler()
    {
        return keyboardHandler;
    }

    /**************************************************************************
     * Get the reference to the table type editor dialog
     * 
     * @return Reference to the table type editor dialog
     *************************************************************************/
    protected CcddTableTypeEditorDialog getTableTypeEditor()
    {
        return tableTypeEditorDialog;
    }

    /**************************************************************************
     * Get the reference to the data type editor dialog
     * 
     * @return Reference to the data type editor dialog
     *************************************************************************/
    protected CcddDataTypeEditorDialog getDataTypeEditor()
    {
        return dataTypeEditorDialog;
    }

    /**************************************************************************
     * Set the reference to the data type editor dialog
     * 
     * @param dataTypeEditorDialog
     *            reference to the data type editor dialog
     *************************************************************************/
    protected void setDataTypeEditor(CcddDataTypeEditorDialog dataTypeEditorDialog)
    {
        this.dataTypeEditorDialog = dataTypeEditorDialog;
    }

    /**************************************************************************
     * Get the reference to the macro editor dialog
     * 
     * @return Reference to the macro editor dialog
     *************************************************************************/
    protected CcddMacroEditorDialog getMacroEditor()
    {
        return macroEditorDialog;
    }

    /**************************************************************************
     * Set the reference to the macro editor dialog
     * 
     * @param macroEditorDialog
     *            reference to the macro editor dialog
     *************************************************************************/
    protected void setMacroEditor(CcddMacroEditorDialog macroEditorDialog)
    {
        this.macroEditorDialog = macroEditorDialog;
    }

    /**************************************************************************
     * Get the data field table editor
     * 
     * @return Data field table editor
     *************************************************************************/
    protected CcddFieldTableEditorDialog getFieldTableEditor()
    {
        return fieldTblEditorDialog;
    }

    /**************************************************************************
     * Get the reference to the reserved message ID editor dialog
     * 
     * @return Reference to the reserved message ID editor dialog
     *************************************************************************/
    protected CcddReservedMsgIDEditorDialog getReservedMsgIDEditor()
    {
        return msgIDEditorDialog;
    }

    /**************************************************************************
     * Set the reference to the reserved message ID editor dialog
     * 
     * @param msgIDEditorDialog
     *            reference to the reserved message ID editor dialog
     *************************************************************************/
    protected void setReservedMsgIDEditor(CcddReservedMsgIDEditorDialog msgIDEditorDialog)
    {
        this.msgIDEditorDialog = msgIDEditorDialog;
    }

    /**************************************************************************
     * Get the reference to the group manager dialog
     * 
     * @return Reference to the group manager dialog
     *************************************************************************/
    protected CcddGroupManagerDialog getGroupManager()
    {
        return groupManagerDialog;
    }

    /**************************************************************************
     * Set the reference to the group manager dialog
     * 
     * @param groupManagerDialog
     *            reference to the group manager dialog
     *************************************************************************/
    protected void setGroupManager(CcddGroupManagerDialog groupManagerDialog)
    {
        this.groupManagerDialog = groupManagerDialog;
    }

    /**************************************************************************
     * Display the search dialog for searching database tables or scripts
     * 
     * @param searchType
     *            search dialog type: TABLES or SCRIPTS
     *************************************************************************/
    protected void showSearchDialog(SearchDialogType searchType)
    {
        showSearchDialog(searchType, null, null);
    }

    /**************************************************************************
     * Display the search dialog for searching database tables, scripts, and
     * event logs
     * 
     * @param searchType
     *            search dialog type: TABLES, SCRIPTS, or LOG
     * 
     * @param targetRow
     *            row index to match if this is an event log entry search on a
     *            table that displays only a single log entry; null otherwise
     * 
     * @param eventLog
     *            event log to search; null if not searching a log
     *************************************************************************/
    protected void showSearchDialog(SearchDialogType searchType,
                                    Long targetRow,
                                    CcddEventLogDialog eventLog)
    {
        CcddSearchDialog searchDialog = null;

        // Get the reference to the specified search dialog
        switch (searchType)
        {
            case TABLES:
                searchDialog = searchTableDlg;
                break;

            case SCRIPTS:
                searchDialog = searchScriptDlg;
                break;

            case LOG:
                searchDialog = searchLogDlg;
                break;
        }

        // Check if the dialog hasn't already been opened, or if it has that it
        // isn't showing
        if (searchDialog == null || !searchDialog.isShowing())
        {
            // Open the specified search dialog
            switch (searchType)
            {
                case TABLES:
                    searchTableDlg = new CcddSearchDialog(CcddMain.this,
                                                          SearchDialogType.TABLES,
                                                          null,
                                                          null);
                    break;

                case SCRIPTS:
                    searchScriptDlg = new CcddSearchDialog(CcddMain.this,
                                                           SearchDialogType.SCRIPTS,
                                                           null,
                                                           null);
                    break;

                case LOG:
                    searchLogDlg = new CcddSearchDialog(CcddMain.this,
                                                        SearchDialogType.LOG,
                                                        targetRow,
                                                        getSessionEventLog());
                    break;
            }
        }
        // The specified search dialog is already open
        else
        {
            // Bring the search dialog to the front
            searchDialog.toFront();
            searchDialog.repaint();
        }
    }

    /**************************************************************************
     * Activate/deactivate the main menu by setting the component enable flags
     * appropriately. While disabled these components are grayed out and do not
     * respond to inputs
     *************************************************************************/
    protected void setGUIActivated(boolean activate)
    {
        // Step through the menu bar items
        for (int index = 0; index < frameCCDD.getJMenuBar().getComponentCount(); index++)
        {
            // Enable/disable the item based on the input flag
            frameCCDD.getJMenuBar().getMenu(index).setEnabled(activate);
        }

        // Update the current database label
        setCurrentDatabaseLabel();

        // Enable/disable the menu items based on the server, database,
        // template, and file definition statuses
        mntmOpenDb.setEnabled(dbControl.isServerConnected());
        mntmCloseDb.setEnabled(dbControl.isDatabaseConnected());
        mntmNewDb.setEnabled(dbControl.isServerConnected());
        mntmRenameDb.setEnabled(dbControl.isServerConnected());
        mntmCopyDb.setEnabled(dbControl.isServerConnected());
        mntmDeleteDb.setEnabled(dbControl.isServerConnected());
        mntmBackupDb.setEnabled(dbControl.isDatabaseConnected());
        mntmRestoreDb.setEnabled(dbControl.isServerConnected());
        mntmNewTable.setEnabled(dbControl.isDatabaseConnected()
                                && tableTypeHandler != null
                                && tableTypeHandler.getTypes() != null);
        mntmEditTable.setEnabled(dbControl.isDatabaseConnected());
        mntmRenameTable.setEnabled(dbControl.isDatabaseConnected());
        mntmCopyTable.setEnabled(dbControl.isDatabaseConnected());
        mntmDeleteTable.setEnabled(dbControl.isDatabaseConnected());
        mntmImportTable.setEnabled(dbControl.isDatabaseConnected());
        mntmExportCSV.setEnabled(dbControl.isDatabaseConnected());
        mntmExportEDS.setEnabled(dbControl.isDatabaseConnected());
        mntmExportJSON.setEnabled(dbControl.isDatabaseConnected());
        mntmExportXTCE.setEnabled(dbControl.isDatabaseConnected());
        mntmUnlock.setEnabled(dbControl.isServerConnected());
        mntmVerifyDatabase.setEnabled(dbControl.isDatabaseConnected());
        mntmManageGroups.setEnabled(dbControl.isDatabaseConnected());
        mntmManageTableTypes.setEnabled(dbControl.isDatabaseConnected());
        mntmManageDataTypes.setEnabled(dbControl.isDatabaseConnected());
        mntmManageMacros.setEnabled(dbControl.isDatabaseConnected());
        mntmAssignMsgID.setEnabled(dbControl.isDatabaseConnected());
        mntmReserveMsgID.setEnabled(dbControl.isDatabaseConnected());
        mntmDuplicateMsgID.setEnabled(dbControl.isDatabaseConnected());
        mntmEditDataField.setEnabled(dbControl.isDatabaseConnected());
        mntmShowVariables.setEnabled(dbControl.isDatabaseConnected());
        mntmSearchTable.setEnabled(dbControl.isDatabaseConnected());
        mntmManageLinks.setEnabled(dbControl.isDatabaseConnected());
        mntmManageTlm.setEnabled(dbControl.isDatabaseConnected());
        mntmManageApps.setEnabled(dbControl.isDatabaseConnected());
        mntmRateParameters.setEnabled(dbControl.isDatabaseConnected());
        mntmAppParameters.setEnabled(dbControl.isDatabaseConnected());
        mntmManageScripts.setEnabled(dbControl.isDatabaseConnected());
        mntmExecuteScripts.setEnabled(dbControl.isDatabaseConnected());
        mntmStoreScripts.setEnabled(dbControl.isDatabaseConnected());
        mntmRetrieveScripts.setEnabled(dbControl.isDatabaseConnected());
        mntmDeleteScripts.setEnabled(dbControl.isDatabaseConnected());
        mntmSearchScripts.setEnabled(dbControl.isDatabaseConnected());

        // Step through each open table editor dialog
        for (CcddTableEditorDialog editorDialog : tableEditorDialogs)
        {
            // Enable/disable the editor dialog controls
            editorDialog.setControlsEnabled(activate);
        }

        // Check if the type editor is open
        if (tableTypeEditorDialog != null)
        {
            // Enable/disable the table type editor dialog controls
            tableTypeEditorDialog.setControlsEnabled(activate);
        }
    }

    /**************************************************************************
     * Set the label displaying the currently open database
     *************************************************************************/
    private void setCurrentDatabaseLabel()
    {
        currentDatabase.setText("<html>Project:<b> "
                                + (dbControl.isDatabaseConnected()
                                                                  ? dbControl.getDatabase()
                                                                  : "<i>not connected"));
    }

    /**************************************************************************
     * Get the list of currently open event logs
     * 
     * @return List of open event logs
     *************************************************************************/
    protected List<CcddEventLogDialog> getEventLogs()
    {
        return eventLogs;
    }

    /**************************************************************************
     * Get the list of currently open table editor dialogs
     * 
     * @return List of open table editor dialogs
     *************************************************************************/
    protected List<CcddTableEditorDialog> getTableEditorDialogs()
    {
        return tableEditorDialogs;
    }

    /**************************************************************************
     * Create a menu and add it to a menu bar
     * 
     * @param menuBar
     *            menu bar to add the menu to
     * 
     * @param name
     *            menu name
     * 
     * @param key
     *            key mnemonic for the menu
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     * 
     * @param toolTip
     *            tool tip text
     * 
     * @return Menu created
     *************************************************************************/
    protected JMenu createMenu(JMenuBar menuBar,
                               String name,
                               int key,
                               int occurrence,
                               String toolTip)
    {
        JMenu menu = new JMenu(name);
        menu.setFont(LABEL_FONT_PLAIN);
        setMnemonic(menu, name, key, occurrence);
        menu.setToolTipText(toolTip);
        menuBar.add(menu);
        return menu;
    }

    /**************************************************************************
     * Create a sub-menu and add it to another menu
     * 
     * @param menu
     *            menu to add the sub-menu to
     * 
     * @param name
     *            sub-menu name
     * 
     * @param key
     *            key mnemonic for the sub-menu
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     * 
     * @param toolTip
     *            tool tip text
     * 
     * @return Sub-menu created
     *************************************************************************/
    protected JMenu createSubMenu(JMenu menu,
                                  String name,
                                  int key,
                                  int occurrence,
                                  String toolTip)
    {
        JMenu subMenu = new JMenu(name);
        subMenu.setFont(LABEL_FONT_PLAIN);
        setMnemonic(subMenu, name, key, occurrence);
        subMenu.setToolTipText(toolTip);
        menu.add(subMenu);
        return subMenu;
    }

    /**************************************************************************
     * Create a menu item and add it to a menu. Specify the occurrence of the
     * key character in the menu item name to highlight
     * 
     * @param menu
     *            menu to add the item to
     * 
     * @param name
     *            menu item name
     * 
     * @param key
     *            key mnemonic for the menu item
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     * 
     * @param toolTip
     *            tool tip text
     * 
     * @return Menu item created
     *************************************************************************/
    protected JMenuItem createMenuItem(JMenu menu,
                                       String name,
                                       int key,
                                       int occurrence,
                                       String toolTip)
    {
        JMenuItem menuItem = new JMenuItem(name);
        menuItem.setFont(LABEL_FONT_PLAIN);
        setMnemonic(menuItem, name, key, occurrence);
        menuItem.setToolTipText(toolTip);
        menu.add(menuItem);
        return menuItem;
    }

    /**************************************************************************
     * Create a check box menu item and add it to a menu. Specify the
     * occurrence of the key character in the menu item name to highlight
     * 
     * @param menu
     *            menu to add the item to
     * 
     * @param name
     *            menu item name
     * 
     * @param key
     *            key mnemonic for the menu item
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     * 
     * @param toolTip
     *            tool tip text
     * 
     * @param isChecked
     *            true to have the check box selected initially
     * 
     * @return Check box menu item created
     *************************************************************************/
    protected JCheckBoxMenuItem createCheckBoxMenuItem(JMenu menu,
                                                       String name,
                                                       int key,
                                                       int occurrence,
                                                       String toolTip,
                                                       boolean isSelected)
    {
        JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(name);
        menuItem.setFont(LABEL_FONT_PLAIN);
        setMnemonic(menuItem, name, key, occurrence);
        menuItem.setToolTipText(toolTip);
        menuItem.setSelected(isSelected);
        menu.add(menuItem);
        return menuItem;
    }

    /**************************************************************************
     * Create a radio button menu item and add it to a menu. Specify the
     * occurrence of the key character in the menu item name to highlight
     * 
     * @param menu
     *            menu to add the item to
     * 
     * @param name
     *            menu item name
     * 
     * @param key
     *            key mnemonic for the menu item
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     * 
     * @param toolTip
     *            tool tip text
     * 
     * @param isSelected
     *            true to have the radio button selected initially
     * 
     * @return Radio button menu item created
     *************************************************************************/
    protected JRadioButtonMenuItem createRadioButtonMenuItem(JMenu menu,
                                                             String name,
                                                             int key,
                                                             int occurrence,
                                                             String toolTip,
                                                             boolean isSelected)
    {
        JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(name);
        menuItem.setFont(LABEL_FONT_PLAIN);
        setMnemonic(menuItem, name, key, occurrence);
        menuItem.setToolTipText(toolTip);
        menuItem.setSelected(isSelected);
        menu.add(menuItem);
        return menuItem;
    }

    /**************************************************************************
     * Set the key mnemonic for the supplied menu item. Specify the occurrence
     * of the key character in the menu item name to highlight
     * 
     * @param menuItem
     *            menu item for which to set the mnemonic
     * 
     * @param name
     *            menu item name
     * 
     * @param key
     *            key mnemonic for the menu item
     * 
     * @param occurrence
     *            specifies which occurrence of the character in the item name
     *            to highlight; set to < 2 to use the first occurrence
     *************************************************************************/
    private void setMnemonic(AbstractButton menuItem,
                             String name,
                             int key,
                             int occurrence)
    {
        // Convert the menu item name and key character to lower case to make
        // the match case insensitive
        name = name.toLowerCase();
        String keyChar = KeyEvent.getKeyText(key).toLowerCase();

        // Set the mnemonic key. This automatically highlights the first
        // occurrence of the key character (if present) in the menu item name
        menuItem.setMnemonic(key);

        int position = -1;

        do
        {
            // Get the position of the key character in the item name starting
            // at the beginning of the name (first pass) or the last occurrence
            // of the key character (subsequent passes)
            position = name.indexOf(keyChar, position + 1);

            // Decrement the occurrence counter each pass
            occurrence--;
        } while (occurrence > 0 && position != -1);
        // Continue until the specified occurrence is reached or if the key
        // character can't be found in the remaining portion of the name

        // Check if the specified occurrence of the key character was located
        if (position != -1)
        {
            // Highlight the specified occurrence of the key character in the
            // menu item name
            menuItem.setDisplayedMnemonicIndex(position);
        }
    }

    /**************************************************************************
     * Initialize the contents of the main application frame
     *************************************************************************/
    private void initialize()
    {
        // Set the font and background color for all tool tip pop-ups
        UIDefaults uiDefs = UIManager.getDefaults();
        uiDefs.put("ToolTip.font", LABEL_FONT_PLAIN);
        uiDefs.put("ToolTip.background", TOOL_TIP_TEXT_COLOR);

        ccddVersion = null;
        buildDate = null;

        try
        {
            // Get the path+name of the .jar file in a format acceptable to all
            // OS's
            String jarFileName = URLDecoder.decode(new File(CcddMain.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath(),
                                                   "UTF-8");

            // Check if the .jar file name exists. This is false if the
            // application is executed from within the IDE
            if (jarFileName != null && jarFileName.endsWith(".jar"))
            {
                // Get the manifest in the .jar file
                JarFile jar = new JarFile(jarFileName);
                Manifest manifest = jar.getManifest();

                // Check if the manifest exists
                if (manifest != null)
                {
                    // Get the version number, build number, and build date
                    // from the manifest
                    Attributes attributes = manifest.getMainAttributes();
                    ccddVersion = attributes.getValue("CCDD-Version");
                    buildDate = attributes.getValue("Build-Date");
                }

                jar.close();
            }
        }
        catch (Exception e)
        {
            // Ignore the exception if an I/O exception occurs accessing the
            // manifest in the .jar file
        }

        // Check if no version number or build date was found in the manifest
        if (ccddVersion == null || buildDate == null)
        {
            try
            {
                // Read the version and number from the build property files
                // and set the date to today's date. This is for when the
                // application is executed from within the IDE
                Properties properties = new Properties();
                properties.load(new FileInputStream("." + File.separator + "ccdd.build.version"));
                ccddVersion = properties.getProperty("build.version");
                properties.load(new FileInputStream("." + File.separator + "ccdd.build.number"));
                ccddVersion += "." + properties.getProperty("build.number");
                buildDate = new SimpleDateFormat("M-d-yyyy").format(Calendar.getInstance().getTime());
            }
            catch (Exception e)
            {
                // Ignore the exception if the version number and build date
                // can't be obtained from the build property files
            }

            // Check if no version number was found in the manifest or the
            // build property files
            if (ccddVersion == null)
            {
                // Set the version number and build date to indicate this
                // information isn't available
                ccddVersion = "*unknown*";
                buildDate = "*unknown*";
            }
        }

        // Create the main application frame and set its characteristics
        frameCCDD = new JFrame();
        frameCCDD.setTitle("CFS Command & Data Dictionary  "
                           + ccddVersion);
        frameCCDD.setBounds(100, 100, INIT_WINDOW_WIDTH, INIT_WINDOW_HEIGHT);
        frameCCDD.setMinimumSize(new Dimension(MIN_WINDOW_WIDTH,
                                               MIN_WINDOW_HEIGHT));

        // Set the default close operation so that the main window frame's
        // close button doesn't automatically exit the program. Instead, if
        // this close button is pressed a 'click' event is sent to the File |
        // Exit command
        frameCCDD.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Add a listener for main window events
        frameCCDD.addWindowListener(new WindowAdapter()
        {
            /******************************************************************
             * Handle the main window frame close button press event
             *****************************************************************/
            @Override
            public void windowClosing(WindowEvent we)
            {
                // Send a 'click' event for the main window frame's File | Exit
                // menu item. This allows the frame's close button to perform
                // the same actions as the File | Exit command; i.e., asks the
                // user to confirm exiting the program
                mntmExit.doClick();
            }
        });

        // Create the application event log window
        JPanel sessionPanel = new JPanel(new BorderLayout());
        currentDatabase = new JLabel();
        currentDatabase.setFont(LABEL_FONT_PLAIN);
        currentDatabase.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setCurrentDatabaseLabel();
        sessionPanel.add(currentDatabase, BorderLayout.PAGE_START);
        sessionPanel.add(getSessionEventLog().getEventPanel(),
                         BorderLayout.CENTER);
        frameCCDD.getContentPane().add(sessionPanel);

        // Create the main menu bar
        JMenuBar menuBar = new JMenuBar();
        frameCCDD.setJMenuBar(menuBar);

        // Create the File menu and menu items
        JMenu mnFile = createMenu(menuBar, "File", KeyEvent.VK_F, 1, null);
        mntmUser = createMenuItem(mnFile, "Select user", KeyEvent.VK_U, 1, "Change user name and/or password");
        mntmDbServer = createMenuItem(mnFile, "Database server", KeyEvent.VK_D, 1, "Change PostgreSQL database server address and port");
        mnFile.addSeparator();
        mntmReadLog = createMenuItem(mnFile, "Read log", KeyEvent.VK_R, 1, "Open an event log");
        mntmPrintLog = createMenuItem(mnFile, "Print log", KeyEvent.VK_P, 1, "Print the current session event log");
        mntmSearchLog = createMenuItem(mnFile, "Search log", KeyEvent.VK_S, 1, "Search the current session event log");
        mnFile.addSeparator();
        JMenu mnWebServer = createSubMenu(mnFile, "Web server", KeyEvent.VK_W, 1, null);
        mntmEnableWebServer = createCheckBoxMenuItem(mnWebServer, "Enable server", KeyEvent.VK_E, 1, "Start or stop the web server", false);
        mntmWebServerPort = createMenuItem(mnWebServer, "Select port", KeyEvent.VK_O, 1, "Select the web server port");
        mnFile.addSeparator();
        JMenuItem mntmAppearance = createMenuItem(mnFile, "Appearance", KeyEvent.VK_A, 1, "Change the application look & feel");
        mntmExit = createMenuItem(mnFile, "Exit", KeyEvent.VK_X, 1, "Exit the application");

        // Create the Project menu and menu items
        JMenu mnProject = createMenu(menuBar, "Project", KeyEvent.VK_P, 1, null);
        mntmOpenDb = createMenuItem(mnProject, "Open", KeyEvent.VK_O, 1, "Open an existing project database");
        mntmCloseDb = createMenuItem(mnProject, "Close", KeyEvent.VK_C, 1, "Close the currently open project database");
        mnProject.addSeparator();
        mntmNewDb = createMenuItem(mnProject, "New", KeyEvent.VK_N, 1, "Create a new project database");
        mntmRenameDb = createMenuItem(mnProject, "Rename", KeyEvent.VK_R, 1, "Rename an existing project database");
        mntmCopyDb = createMenuItem(mnProject, "Copy", KeyEvent.VK_Y, 1, "Copy an existing project database");
        mntmDeleteDb = createMenuItem(mnProject, "Delete", KeyEvent.VK_L, 1, "Delete an existing project database");
        mnProject.addSeparator();
        mntmBackupDb = createMenuItem(mnProject, "Backup", KeyEvent.VK_B, 1, "Backup the currently open project database");
        mntmRestoreDb = createMenuItem(mnProject, "Restore", KeyEvent.VK_S, 1, "Restore a previously backed-up project database");
        mnProject.addSeparator();
        mntmUnlock = createMenuItem(mnProject, "Unlock", KeyEvent.VK_U, 1, "Unlock project database(s)");
        mnProject.addSeparator();
        mntmVerifyDatabase = createMenuItem(mnProject, "Verify", KeyEvent.VK_V, 1, "Perform a project database consistency check");

        // Create the Data menu and menu items
        JMenu mnData = createMenu(menuBar, "Data", KeyEvent.VK_D, 1, null);
        mntmNewTable = createMenuItem(mnData, "New table(s)", KeyEvent.VK_N, 1, "Create new data table(s)");
        mntmEditTable = createMenuItem(mnData, "Edit table(s)", KeyEvent.VK_E, 1, "Edit selected table(s)");
        mntmRenameTable = createMenuItem(mnData, "Rename table", KeyEvent.VK_R, 1, "Rename selected data table");
        mntmCopyTable = createMenuItem(mnData, "Copy table", KeyEvent.VK_C, 1, "Copy selected data table");
        mntmDeleteTable = createMenuItem(mnData, "Delete table(s)", KeyEvent.VK_L, 1, "Delete selected data table(s)");
        mnData.addSeparator();
        mntmImportTable = createMenuItem(mnData, "Import table(s)", KeyEvent.VK_I, 1, "Import selected CSV or XTCE/EDS XML file(s) to data table(s)");
        JMenu mnExport = createSubMenu(mnData, "Export table(s)", KeyEvent.VK_X, 1, null);
        mntmExportCSV = createMenuItem(mnExport, "CSV", KeyEvent.VK_C, 1, "Export selected data table(s) in CSV format");
        mntmExportEDS = createMenuItem(mnExport, "EDS", KeyEvent.VK_E, 1, "Export selected data table(s) in EDS XML format");
        mntmExportJSON = createMenuItem(mnExport, "JSON", KeyEvent.VK_J, 1, "Export selected data table(s) in JSON format");
        mntmExportXTCE = createMenuItem(mnExport, "XTCE", KeyEvent.VK_X, 1, "Export selected data table(s) in XTCE XML format");
        mnData.addSeparator();
        mntmManageGroups = createMenuItem(mnData, "Manage groups", KeyEvent.VK_G, 2, "Open the table group manager");
        mntmManageTableTypes = createMenuItem(mnData, "Manage table types", KeyEvent.VK_T, 1, "Open the table type manager");
        mntmManageDataTypes = createMenuItem(mnData, "Manage data types", KeyEvent.VK_D, 1, "Open the data type manager");
        mntmManageMacros = createMenuItem(mnData, "Manage macros", KeyEvent.VK_O, 1, "Open the macro editor");
        mnData.addSeparator();
        JMenu mnMessageID = createSubMenu(mnData, "Message IDs", KeyEvent.VK_M, 1, null);
        mntmAssignMsgID = createMenuItem(mnMessageID, "Assign IDs", KeyEvent.VK_A, 1, "Auto-assign message ID numbers");
        mntmReserveMsgID = createMenuItem(mnMessageID, "Reserve IDs", KeyEvent.VK_R, 1, "Reserve message ID numbers");
        mntmDuplicateMsgID = createMenuItem(mnMessageID, "Find duplicates", KeyEvent.VK_F, 1, "Detect duplicate message ID numbers");
        mntmEditDataField = createMenuItem(mnData, "Show/edit fields", KeyEvent.VK_F, 1, "Open the data field table editor");
        mnData.addSeparator();
        mntmShowVariables = createMenuItem(mnData, "Show variables", KeyEvent.VK_V, 1, "Display all of the variable paths + names in various formats");
        mnData.addSeparator();
        mntmSearchTable = createMenuItem(mnData, "Search tables", KeyEvent.VK_S, 1, "Search the project database tables");

        // Create the Scheduling menu and menu items
        JMenu mnScheduling = createMenu(menuBar, "Scheduling", KeyEvent.VK_C, 1, null);
        mntmManageLinks = createMenuItem(mnScheduling, "Manage links", KeyEvent.VK_L, 1, "Open the variable link manager");
        mntmManageTlm = createMenuItem(mnScheduling, "Telemetry", KeyEvent.VK_T, 1, "Open the telemetry message scheduler");
        mntmManageApps = createMenuItem(mnScheduling, "Applications", KeyEvent.VK_A, 1, "Open the application scheduler");
        mnScheduling.addSeparator();
        mntmRateParameters = createMenuItem(mnScheduling, "Rate parameters", KeyEvent.VK_R, 1, "Change telemetry rate parameters");
        mntmAppParameters = createMenuItem(mnScheduling, "App parameters", KeyEvent.VK_P, 1, "Change application scheduler parameters");

        // Create the Script menu and menu items
        JMenu mnScript = createMenu(menuBar, "Script", KeyEvent.VK_S, 1, null);
        mntmManageScripts = createMenuItem(mnScript, "Manage", KeyEvent.VK_M, 1, "Open the script association manager");
        mntmExecuteScripts = createMenuItem(mnScript, "Execute", KeyEvent.VK_E, 1, "Open the script association executive");
        mnScript.addSeparator();
        mntmStoreScripts = createMenuItem(mnScript, "Store", KeyEvent.VK_T, 1, "Store selected script(s) in the project database");
        mntmRetrieveScripts = createMenuItem(mnScript, "Retrieve", KeyEvent.VK_R, 1, "Retrieve selected script(s) from the project database");
        mntmDeleteScripts = createMenuItem(mnScript, "Delete", KeyEvent.VK_D, 1, "Delete selected script(s) from the project database");
        mnScript.addSeparator();
        mntmSearchScripts = createMenuItem(mnScript, "Search", KeyEvent.VK_S, 1, "Search the scripts stored in the project database");

        // Create the Help menu and menu items
        JMenu mnHelp = createMenu(menuBar, "Help", KeyEvent.VK_H, 1, null);
        JMenuItem mntmGuide = createMenuItem(mnHelp, "Guide", KeyEvent.VK_G, 1, "Open the application user's guide");
        JMenuItem mntmAbout = createMenuItem(mnHelp, "About", KeyEvent.VK_A, 1, null);

        // Add a listener for the Select user menu item
        mntmUser.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the select user dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get confirmation to discard uncommitted changes, if
                // applicable
                if (ignoreUncommittedChanges("Select User",
                                             "Discard changes?",
                                             true,
                                             null,
                                             frameCCDD))
                {
                    // Display the user parameter dialog
                    new CcddServerPropertyDialog(CcddMain.this, ServerPropertyDialogType.LOGIN);
                }
            }
        });

        // Add a listener for the Database server menu item
        mntmDbServer.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the database server dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get confirmation to discard uncommitted changes, if
                // applicable
                if (ignoreUncommittedChanges("Database Server",
                                             "Discard changes?",
                                             true,
                                             null,
                                             frameCCDD))
                {
                    // Display the database server dialog
                    new CcddServerPropertyDialog(CcddMain.this, ServerPropertyDialogType.DB_SERVER);
                }
            }
        });

        // Add a listener for the Read log menu item
        mntmReadLog.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the Read log dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Open an existing event log
                eventLogs.add(new CcddEventLogDialog(CcddMain.this, false));
            }
        });

        // Add a listener for the Print log menu item
        mntmPrintLog.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Print the current session's event log
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                getSessionEventLog().printEventLog();
            }
        });

        // Enable the log search menu command only if the session's event log
        // file successfully opened and can be read
        mntmSearchLog.setEnabled(getSessionEventLog().getEventLogFile().canRead());

        // Add a listener for the Search log menu item
        mntmSearchLog.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Search the current session's event log
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                showSearchDialog(SearchDialogType.LOG,
                                 null,
                                 getSessionEventLog());
            }
        });

        // Add a listener for the Enable server check box menu item
        mntmEnableWebServer.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Start or stop the web server
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the check box is selected
                if (mntmEnableWebServer.isSelected())
                {
                    // Check if the web server hasn't been created
                    if (webServer == null)
                    {
                        // Create the web server
                        setWebServer("gui");
                    }

                    // Check if connected to a project database
                    if (dbControl.isDatabaseConnected())
                    {
                        // Start the web server
                        webServer.startServer();

                        // Set the rate and macro handlers
                        webServer.getWebAccessHandler().setHandlers();
                    }
                }
                // Check if a web server exists
                else if (webServer != null)
                {
                    // Stop the web server
                    webServer.stopServer();
                }
            }
        });

        // Add a listener for the Select port menu item
        mntmWebServerPort.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the web server port select dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddServerPropertyDialog(CcddMain.this,
                                             ServerPropertyDialogType.WEB_SERVER);
            }
        });

        // Add a listener for the Open Project menu item
        mntmOpenDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the project database selection dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.OPEN);
            }
        });

        // Add a listener for the Close Project menu item
        mntmCloseDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Close the currently open project database and open the default
             * database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if there are uncommitted changes and if so, confirm
                // discarding the changes before proceeding
                if (ignoreUncommittedChanges("Restore Project",
                                             "Discard changes?",
                                             true,
                                             null,
                                             frameCCDD))
                {
                    dbControl.openDatabaseInBackground(DEFAULT_DATABASE);
                }
            }
        });

        // Add a listener for the New Project menu item
        mntmNewDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the project database creation dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.CREATE);
            }
        });

        // Add a listener for the Rename Project menu item
        mntmRenameDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the rename project database dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.RENAME);
            }
        });

        // Add a listener for the Copy Project menu item
        mntmCopyDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the copy project database dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.COPY);
            }
        });

        // Add a listener for the Delete Project menu item
        mntmDeleteDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the project database deletion dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.DELETE);
            }
        });

        // Add a listener for the Backup Project menu item
        mntmBackupDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Backup a project database to a file
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                fileIOHandler.backupDatabaseToFile(true);
            }
        });

        // Add a listener for the Restore Project menu item
        mntmRestoreDb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Restore a project database from a backup file
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if there are uncommitted changes and if so, confirm
                // discarding the changes before proceeding
                if (ignoreUncommittedChanges("Restore Project",
                                             "Discard changes?",
                                             true,
                                             null,
                                             frameCCDD))
                {
                    fileIOHandler.restoreDatabaseFromFile();
                }
            }
        });

        // Add a listener for the Unlock Project menu item
        mntmUnlock.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the unlock project database dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbManagerDialog(CcddMain.this,
                                        DbManagerDialogType.UNLOCK);
            }
        });

        // Add a listener for the Verify menu item
        mntmVerifyDatabase.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Perform a database consistency check
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddDbVerificationHandler(CcddMain.this);
            }
        });

        // Add a listener for the New Data menu item
        mntmNewTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to create a new table
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this, ManagerDialogType.NEW);
            }
        });

        // Add a listener for the Edit Data menu item
        mntmEditTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select a table to edit
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this, ManagerDialogType.EDIT);
            }
        });

        // Add a listener for the Rename Data menu item
        mntmRenameTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select a table to
             * rename
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.RENAME);
            }
        });

        // Add a listener for the Copy Data menu item
        mntmCopyTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select a table to copy
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.COPY);
            }
        });

        // Add a listener for the Delete Data menu item
        mntmDeleteTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select a table to
             * delete
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.DELETE);
            }
        });

        // Add a listener for the Import Data menu item
        mntmImportTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Select one or more files to import
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.IMPORT);
            }
        });

        // Add a listener for the Export Table(s) - CSV menu item
        mntmExportCSV.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select one or more
             * tables to export in CSV format
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.EXPORT_CSV);
            }
        });

        // Add a listener for the Export Table(s) - EDS menu item
        mntmExportEDS.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select one or more
             * tables to export in EDS format
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.EXPORT_EDS);
            }
        });

        // Add a listener for the Export Table(s) - JSON menu item
        mntmExportJSON.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select one or more
             * tables to export in JSON format
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.EXPORT_JSON);
            }
        });

        // Add a listener for the Export Table(s) - XTCE menu item
        mntmExportXTCE.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data table management dialog to select one or more
             * tables to export in XTCE format
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTableManagerDialog(CcddMain.this,
                                           ManagerDialogType.EXPORT_XTCE);
            }
        });

        // Add a listener for the Manage Groups Table menu item
        mntmManageGroups.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the table group management dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Open the group manager dialog
                new CcddGroupManagerDialog(CcddMain.this);
            }
        });

        // Add a listener for the Manage Table Types menu item
        mntmManageTableTypes.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the table type editor dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the table type editor isn't already open
                if (tableTypeEditorDialog == null || !tableTypeEditorDialog.isShowing())
                {
                    // Open the table type editor
                    tableTypeEditorDialog = new CcddTableTypeEditorDialog(CcddMain.this,
                                                                          tableTypeHandler.getTypes());
                }
                // The table type editor is already open
                else
                {
                    // Deiconify the editor (if iconfied) and bring it to the
                    // front
                    tableTypeEditorDialog.setExtendedState(Frame.NORMAL);
                    tableTypeEditorDialog.toFront();
                    tableTypeEditorDialog.repaint();
                }
            }
        });

        // Add a listener for the Manage Data Types menu item
        mntmManageDataTypes.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data type editor dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Open the data type editor
                new CcddDataTypeEditorDialog(CcddMain.this);
            }
        });

        // Add a listener for the Manage Macros menu item
        mntmManageMacros.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the macro editor dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Open the macro editor
                new CcddMacroEditorDialog(CcddMain.this);
            }
        });

        // Add a listener for the Manage Links Table menu item
        mntmManageLinks.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the link management dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddLinkManagerDialog(CcddMain.this);
            }
        });

        // Add a listener for the telemetry scheduler menu item
        mntmManageTlm.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the telemetry scheduler dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddTelemetrySchedulerDialog(CcddMain.this);
            }
        });

        // Add a listener for the application scheduler menu item
        mntmManageApps.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the application scheduler dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddApplicationSchedulerDialog(CcddMain.this);
            }
        });

        // Add a listener for the Rate Parameters menu item
        mntmRateParameters.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the rate parameter change dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddRateParameterDialog(CcddMain.this);
            }
        });

        // Add a listener for the Application Parameters menu item
        mntmAppParameters.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the application parameter change dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddApplicationParameterDialog(CcddMain.this);
            }
        });

        // Add a listener for the Assign IDs menu item
        mntmAssignMsgID.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the message ID assignment dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddAssignMessageIDDialog(CcddMain.this);
            }
        });

        // Add a listener for the Reserve IDs menu item
        mntmReserveMsgID.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the message ID reservation dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddReservedMsgIDEditorDialog(CcddMain.this);
            }
        });

        // Add a listener for the Find Duplicates menu item
        mntmDuplicateMsgID.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the duplicate message ID dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddMessageIDHandler(CcddMain.this).displayDuplicates(getMainFrame());
            }
        });

        // Add a listener for the Edit Data Fields menu item
        mntmEditDataField.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the data field editor dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the data field editor dialog is not already
                // displayed
                if (fieldTblEditorDialog == null || !fieldTblEditorDialog.isShowing())
                {
                    // Open the data field editor
                    fieldTblEditorDialog = new CcddFieldTableEditorDialog(CcddMain.this);
                }
                // The editor is currently displayed
                else
                {
                    // Deiconify the editor (if iconfied) and bring it to the
                    // front
                    fieldTblEditorDialog.setExtendedState(Frame.NORMAL);
                    fieldTblEditorDialog.toFront();
                    fieldTblEditorDialog.repaint();
                }
            }
        });

        // Add a listener for the Show Variables command
        mntmShowVariables.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display a dialog showing all of the variable paths + names. The
             * application format and ITOS record formats are shown
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddVariablesDialog(CcddMain.this);
            }
        });

        // Add a listener for the Search tables menu item
        mntmSearchTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the search tables dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                showSearchDialog(SearchDialogType.TABLES);
            }
        });

        // Add a listener for the Manage script associations menu item
        mntmManageScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the manage script associations dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddScriptManagerDialog(CcddMain.this);
            }
        });

        // Add a listener for the Execute script associations menu item
        mntmExecuteScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Execute the table and script associations stored in the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the list of script association definitions stored in the
                // database
                String[][] associations = dbTable.retrieveInformationTable(InternalTable.ASSOCIATIONS,
                                                                           frameCCDD).toArray(new String[0][0]);

                // Check that at least one script association definition exists
                if (associations.length != 0)
                {
                    // Open the script association executive dialog
                    new CcddScriptExecutiveDialog(CcddMain.this);
                }
                // No script association definitions are stored in the database
                else
                {
                    // Inform the user that no scripts exist
                    new CcddDialogHandler().showMessageDialog(frameCCDD,
                                                              "<html><b>No script association exists in the database",
                                                              "File Generation",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
            }
        });

        // Add a listener for the Store scripts menu item
        mntmStoreScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Store script(s) in the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddScriptStorageDialog(CcddMain.this, ScriptIOType.STORE);
            }
        });

        // Add a listener for the Retrieve scripts menu item
        mntmRetrieveScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Retrieve script(s) from the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddScriptStorageDialog(CcddMain.this,
                                            ScriptIOType.RETRIEVE);
            }
        });

        // Add a listener for the Delete scripts menu item
        mntmDeleteScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Delete script(s) from the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddScriptStorageDialog(CcddMain.this,
                                            ScriptIOType.DELETE);
            }
        });

        // Add a listener for the Search scripts menu item
        mntmSearchScripts.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the search scripts dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                showSearchDialog(SearchDialogType.SCRIPTS);
            }
        });

        // Add a listener for the program Appearance menu item
        mntmAppearance.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Show the program appearance dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                new CcddAppearanceDialog(CcddMain.this);

                // Store the selected look & feel in the program preferences
                // backing store
                progPrefs.put(LOOK_AND_FEEL, selectedLaF);
            }
        });

        // Add a listener for the Exit menu item
        mntmExit.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Exit the program
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                exitApplication(true, 0);
            }
        });

        // Add a listener for the Guide menu item
        mntmGuide.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Extract the user's guide from the .jar file and display it
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                fileIOHandler.displayUsersGuide();
            }
        });

        // Add a listener for the About menu item
        mntmAbout.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the help About dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Create the icon to display in the dialog by scaling the CCDD
                // logo
                int iconWidth = 250;
                ImageIcon icon = new
                    ImageIcon(getClass().getResource(CCDD_ICON));
                Image image = icon.getImage().getScaledInstance(iconWidth,
                                                                icon.getIconHeight()
                                                                    * iconWidth
                                                                    / icon.getIconWidth(),
                                                                Image.SCALE_SMOOTH);
                icon = new ImageIcon(image);

                // Display the application name, author, and version
                new CcddDialogHandler().showMessageDialog(frameCCDD,
                                                          "<html><b>Core Flight System<br>Command & Data Dictionary</b><br>"
                                                              + CCDD_AUTHOR
                                                              + "<br>"
                                                              + CcddUtilities.colorHTMLText("Version: ",
                                                                                            LABEL_TEXT_COLOR)
                                                              + ccddVersion
                                                              + "&#160;&#160;&#160;"
                                                              + buildDate
                                                              + "<br><br><b>Supporting software versions:</b><br>&#160;&#160;&#160;"
                                                              + CcddUtilities.colorHTMLText("Java: ",
                                                                                            LABEL_TEXT_COLOR)
                                                              + System.getProperty("java.version")
                                                              + " ("
                                                              + System.getProperty("sun.arch.data.model")
                                                              + "-bit)<br>&#160;&#160;&#160;"
                                                              + CcddUtilities.colorHTMLText(DEFAULT_SERVER + ": ",
                                                                                            LABEL_TEXT_COLOR)
                                                              + dbControl.getDatabaseVersion()
                                                              + "<br>&#160;&#160;&#160;"
                                                              + CcddUtilities.colorHTMLText("JDBC: ",
                                                                                            LABEL_TEXT_COLOR)
                                                              + dbControl.getJDBCVersion()
                                                              + "<br>&#160;&#160;&#160;"
                                                              + CcddUtilities.colorHTMLText("Jetty: ",
                                                                                            LABEL_TEXT_COLOR)
                                                              + org.eclipse.jetty.util.Jetty.VERSION
                                                              + "<br><br><b>Scripting language versions:</b>"
                                                              + scriptHandler.getEngineInformation()
                                                              + "<br><br>Copyright 2017 United States Government "
                                                              + "as represented by the<br>Administrator of the "
                                                              + "National Aeronautics and Space Administration.<br>"
                                                              + "No copyright is claimed in the United States "
                                                              + "under Title 17, U.S. Code.<br>All Other Rights Reserved.",
                                                          "About CCDD",
                                                          DialogOption.OK_OPTION,
                                                          icon);
            }
        });
    }

    /**************************************************************************
     * Create a shutdown hook so that normal and abnormal termination of the
     * application can close the database and event log file
     *************************************************************************/
    private void createShutdownHook()
    {
        // Add a hook to trap a shutdown event
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            /******************************************************************
             * Handle the shutdown event
             *****************************************************************/
            @Override
            public void run()
            {
                try
                {
                    // Check if the database control handler exists
                    if (dbControl != null)
                    {
                        // Close the database connection
                        dbControl.closeDatabase();
                    }

                    // Check if the session event log exists
                    if (!eventLogs.isEmpty())
                    {
                        // Close the event log
                        getSessionEventLog().closeEventLogFile();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    /**************************************************************************
     * Exit the CCDD application
     * 
     * @param withConfirm
     *            true to ask for confirmation to exit; false to exit without
     *            confirmation
     * 
     * @param status
     *            exit status value: 0 for normal exit, non-zero for abnormal
     *************************************************************************/
    protected void exitApplication(boolean withConfirm, int status)
    {
        // Ask the user to verify if exiting is okay, then check if there are
        // editors open with uncommitted changes; if so then get confirmation
        // to discard the changes before exiting
        if (!withConfirm
            || (new CcddDialogHandler().showMessageDialog(frameCCDD,
                                                          "<html><b>Exit application?",
                                                          "Exit CCDD",
                                                          JOptionPane.QUESTION_MESSAGE,
                                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
            && ignoreUncommittedChanges("Exit application",
                                        "Discard changes?",
                                        true,
                                        null,
                                        frameCCDD)))
        {
            // Exit the program
            System.exit(status);
        }
    }

    /**************************************************************************
     * Get the application look & feel
     * 
     * @return name of the selected look & feel
     *************************************************************************/
    protected String getLookAndFeel()
    {
        return selectedLaF;
    }

    /**************************************************************************
     * Set the application look & feel
     * 
     * @param laf
     *            name of the selected look & feel
     * 
     * @return true if an error occurred
     *************************************************************************/
    protected void setLookAndFeel(String laf)
    {
        // Set the default to the cross-platform look & feel
        selectedLaF = "Metal";
        String lafClass = UIManager.getCrossPlatformLookAndFeelClassName();

        // Check if the look & feel was not provided
        if (laf == null)
        {
            // Get the look & feel from the program preferences backing store
            laf = progPrefs.get(LOOK_AND_FEEL, selectedLaF);
        }

        // Step through the look & feels available
        for (LookAndFeelInfo lafInfo : UIManager.getInstalledLookAndFeels())
        {
            // Check if the desired look & feel matches one of the available
            // ones
            if (laf.equals(lafInfo.getName()))
            {
                // Store the selected look & feel
                selectedLaF = laf;
                lafClass = lafInfo.getClassName();
                break;
            }
        }

        try
        {
            // Set the selected look & feel of the GUI
            UIManager.setLookAndFeel(lafClass);

            // Save the selected L&F in the program preferences backing store
            progPrefs.put(LOOK_AND_FEEL, selectedLaF);

            // Set the adjustments to the selected look & feel
            setLaFAdjustments(selectedLaF);

            // Force the application to redraw
            SwingUtilities.updateComponentTreeUI(frameCCDD);

            boolean firstPass = true;

            // Step through each open event log
            for (CcddEventLogDialog eLog : eventLogs)
            {
                // Check if this isn't the first event log, which is the log
                // for the current session and so exists within the main window
                if (!firstPass)
                {
                    // Update the log content table to the new look & feel
                    SwingUtilities.updateComponentTreeUI(eLog);
                    eLog.setButtonWidth();
                    eLog.validate();
                }

                // Restore the log grid lines
                eLog.getEventTable().setTableGrid();

                firstPass = false;
            }

            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : tableEditorDialogs)
            {
                // Update the table editor dialog to the new look & feel
                SwingUtilities.updateComponentTreeUI(editorDialog);
                editorDialog.setButtonWidth();
                editorDialog.getTableEditor().getTable().setTableGrid();
                editorDialog.validate();
            }

            // Check if the table type editor dialog is open
            if (tableTypeEditorDialog != null && tableTypeEditorDialog.isShowing())
            {
                // Update the type editor dialog to the new look & feel
                SwingUtilities.updateComponentTreeUI(tableTypeEditorDialog);
                tableTypeEditorDialog.setButtonWidth();
                tableTypeEditorDialog.getTypeEditor().getTable().setTableGrid();
                tableTypeEditorDialog.validate();
            }
        }

        // Look & feel failed to load
        catch (Exception e)
        {
            // Inform the user that there was an error setting the look & feel
            new CcddDialogHandler().showMessageDialog(frameCCDD,
                                                      "<html><b>Problem occurred when setting the look & feel to </b>"
                                                          + selectedLaF,
                                                      "L&F Warning",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**************************************************************************
     * Get the programs preferences from the backing store
     *************************************************************************/
    private void getProgramPreferences()
    {
        try
        {
            // Attempt to store a test preference value to determine if access
            // to the backing store is available
            progPrefs.putBoolean("PreferenceStorageAvailable", true);
            progPrefs.remove("PreferenceStorageAvailable");
            progPrefs.flush();
        }
        catch (BackingStoreException bse)
        {
            // Inform the user that there the program preferences can't be
            // stored
            new CcddDialogHandler().showMessageDialog(frameCCDD,
                                                      "<html><b>Cannot store program preference values",
                                                      "File Warning",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        // Retrieve the preferences from the backing store
        dbControl.setHost(progPrefs.get(POSTGRESQL_SERVER_HOST,
                                        DEFAULT_POSTGRESQL_HOST));
        dbControl.setPort(progPrefs.get(POSTGRESQL_SERVER_PORT,
                                        DEFAULT_POSTGRESQL_PORT));
        dbControl.setDatabase(progPrefs.get(DATABASE, DEFAULT_DATABASE));
        dbControl.setUser(progPrefs.get(USER, System.getProperty("user.name")));
    }

    /**************************************************************************
     * Check if any of the open editors has uncommitted changes, and if so, ask
     * the user if the operation should continue
     * 
     * @param dialogType
     *            text to display in the dialog's header
     * 
     * @param dialogMessage
     *            message to display in the dialog box
     * 
     * @param closeEditors
     *            true to close any open table editors
     * 
     * @param tableTypes
     *            list of tables types that are being changed; null or an empty
     *            list if the caller is not the table type editor
     * 
     * @param parent
     *            GUI component over which to center the confirmation dialog
     * 
     * @return true if there are no uncommitted changes or if the user elects
     *         to ignore the changes and continue with the operation; false if
     *         changes exist and the user cancels the operation
     *************************************************************************/
    protected boolean ignoreUncommittedChanges(String dialogType,
                                               String dialogMessage,
                                               boolean closeEditors,
                                               List<String> tableTypes,
                                               Component parent)
    {
        // Assume that it's okay to continue with uncommitted changes
        boolean canContinue = true;

        // Assume there are no uncommitted changes
        boolean isChanged = false;

        // Step through the open table editor dialogs
        for (CcddTableEditorDialog editorDialog : tableEditorDialogs)
        {
            // Check if any of the editor dialog's tables contain uncommitted
            // changes
            if (editorDialog.isTablesChanged()
                && (tableTypes == null
                || tableTypes.contains(editorDialog.getTableEditor().getTableInformation().getType())))
            {
                // Set the flag to indicate that there are uncommitted changes
                // and stop searching
                isChanged = true;
                break;
            }
        }

        // Check if there are unsaved table type or data field table editor
        // changes. If the list of changed table types is not empty then the
        // caller is the table type editor; ignore table type changes in this
        // instance
        if (!isChanged
            && (((tableTypes == null || tableTypes.isEmpty())
                 && tableTypeEditorDialog != null
                 && tableTypeEditorDialog.isShowing()
                 && tableTypeEditorDialog.isTypesChanged())
                 || (fieldTblEditorDialog != null
                     && fieldTblEditorDialog.isShowing()
                     && fieldTblEditorDialog.isFieldTableChanged())))
        {
            // Set the flag to indicate that there are uncommitted changes
            isChanged = true;
        }

        // Check if there are uncommitted changes and if the user confirms that
        // the changes should be ignored
        if (isChanged && new CcddDialogHandler().showMessageDialog(parent,
                                                                   "<html><b>"
                                                                       + dialogMessage,
                                                                   dialogType,
                                                                   JOptionPane.QUESTION_MESSAGE,
                                                                   DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
        {
            // Clear the flag to indicate that the operation should be
            // terminated
            canContinue = false;
        }

        // Check if the open editor dialogs should be closed and that the user
        // confirmed discarding the uncommitted changes
        if (closeEditors && canContinue)
        {
            // Step through the open table editor dialogs
            for (CcddTableEditorDialog editorDialog : tableEditorDialogs)
            {
                // Close the editor dialog
                editorDialog.closeFrame();
            }

            // Check if the type editor dialog is open
            if (tableTypeEditorDialog != null && tableTypeEditorDialog.isShowing())
            {
                // Close the editor dialog
                tableTypeEditorDialog.closeFrame();
            }

            // Check if the data field table editor dialog is open
            if (fieldTblEditorDialog != null && fieldTblEditorDialog.isShowing())
            {
                // Close the editor dialog
                fieldTblEditorDialog.closeFrame();
            }
        }

        return canContinue;
    }
}
