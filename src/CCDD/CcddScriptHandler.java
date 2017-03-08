/**
 * CFS Command & Data Dictionary script handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.LIST_TABLE_DESC_SEPARATOR;
import static CCDD.CcddConstants.LIST_TABLE_SEPARATOR;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.FAIL_MSG;
import static CCDD.CcddConstants.EventLogMessageType.STATUS_MSG;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.ArrayListMultiple;
import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary script handler class. This class handles
 * execution of the data output scripts
 *****************************************************************************/
public class CcddScriptHandler
{
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;

    // Components that need to be accessed by other classes or listeners
    private DefaultListModel<String> associationsModel;
    private DefaultListModel<String> associationsLongModel;
    private JList<String> associationsList;
    private List<String[]> committedAssociationsList;

    // List of script engine factories that are available on this platform
    private final List<ScriptEngineFactory> scriptFactories;

    // Global storage for the data obtained in the recursive table data reading
    // method
    private String[][] combinedData;

    // Flag that indicates is disabled associations are selectable in the
    // associations list
    private boolean isSelectDisabled;

    // Array to indicate if a script association has a problem that prevents
    // its execution
    boolean[] isBad;

    /**************************************************************************
     * Script handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddScriptHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();

        // Get the available script engines
        scriptFactories = new ScriptEngineManager().getEngineFactories();
    }

    /**************************************************************************
     * Set the references to the table type and data type handler classes
     *************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
    }

    /**************************************************************************
     * Get an array of all script file extensions supported by the available
     * script engines
     * 
     * @return Array of all script file extensions supported by the available
     *         script engines
     *************************************************************************/
    protected FileNameExtensionFilter[] getExtensions()
    {
        List<FileNameExtensionFilter> filters = new ArrayList<FileNameExtensionFilter>();

        // Step through each engine factory
        for (ScriptEngineFactory factory : scriptFactories)
        {
            // Get the engine language name
            String name = factory.getLanguageName();

            // Check if the name begins with "ECMA"
            if (name.toLowerCase().startsWith("ecma"))
            {
                // Use "JavaScript" in place of "ECMAScript"
                name = "JavaScript";
            }
            // Not JavaScript
            else
            {
                // Capitalize the first letter of the engine name
                name = Character.toString(name.charAt(0)).toUpperCase()
                       + name.substring(1);
            }

            // Add the engine extension to the list
            filters.add(new FileNameExtensionFilter(name + " files",
                                                    factory.getExtensions().toArray(new String[0])));
        }

        // Sort the engine extensions by extension description
        Collections.sort(filters, new Comparator<FileNameExtensionFilter>()
        {
            /******************************************************************
             * Compare the descriptions of two engine extensions. Force lower
             * case to eliminate case differences in the comparison
             *****************************************************************/
            @Override
            public int compare(FileNameExtensionFilter ext1, FileNameExtensionFilter ext2)
            {
                return ext1.getDescription().toLowerCase().compareTo(ext2.getDescription().toLowerCase());
            }
        });

        return filters.toArray(new FileNameExtensionFilter[0]);
    }

    /**************************************************************************
     * Get the string containing the available script engines and version
     * numbers
     * 
     * @return String containing the available script engine names and version
     *         numbers appropriate for display in the Help | About dialog;
     *         returns "none" if no scripting languages are installed
     *************************************************************************/
    protected String getEngineInformation()
    {
        List<String> engineInfo = new ArrayList<String>();

        // Step through each engine factory
        for (ScriptEngineFactory factory : scriptFactories)
        {
            // Get the engine language name
            String name = factory.getLanguageName();

            // Check if the name begins with "ECMA"
            if (name.toLowerCase().startsWith("ecma"))
            {
                // Use "JavaScript" in place of "ECMAScript"
                name = "JavaScript";
            }
            // Not JavaScript
            else
            {
                // Capitalize the first letter of the engine name
                name = Character.toString(name.charAt(0)).toUpperCase()
                       + name.substring(1);
            }

            // Add the information for this engine to the list
            engineInfo.add(name
                           + ": "
                           + factory.getLanguageVersion().replaceAll("[a-zA-Z ]",
                                                                     "")
                           + " ("
                           + factory.getEngineName()
                           + " "
                           + factory.getEngineVersion()
                           + ")");

        }

        // Sort the engines in alphabetical order
        Collections.sort(engineInfo);

        String engineOutput = "";

        // Step through each engine's information
        for (String engine : engineInfo)
        {
            // Append the information to the output string
            engineOutput += "<br>&#160;&#160;&#160;"
                            + engine;
        }

        // Check if no engines exist
        if (engineOutput.isEmpty())
        {
            // Set the string to indicate no engines are available
            engineOutput = "none";
        }

        return engineOutput;
    }

    /**************************************************************************
     * Execute one or more scripts based on the script associations in the
     * script associations list
     * 
     * @param dialog
     *            GUI component calling this method
     * 
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *************************************************************************/
    protected void executeScriptAssociations(CcddDialogHandler dialog,
                                             CcddTableTreeHandler tableTree)
    {
        int assnIndex = 0;

        // Store the status of the flag that determines if disabled list
        // associations can be selected and set the flag to false in order to
        // prevent attempting to execute the disabled association
        boolean originalSelectStatus = isSelectDisabled;
        isSelectDisabled = false;

        // Get the array of selected script association indices
        int[] assnIndices = associationsList.getSelectedIndices();

        // Restore the flag to its original status
        isSelectDisabled = originalSelectStatus;

        // Check that at least one association is selected
        if (assnIndices.length != 0)
        {
            // Create storage for the script association definitions
            String[][] associations = new String[assnIndices.length][2];

            // Step through each selected script association definition
            for (int index : assnIndices)
            {
                // Split the script association into the script name and table
                // names
                associations[assnIndex] = associationsLongModel.get(index).split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR));
                assnIndex++;
            }

            // Execute the selected script script association(s)
            getDataAndExecuteScriptInBackground(dialog,
                                                tableTree,
                                                associations);
        }
    }

    /**************************************************************************
     * Get the table information array from the table data used by the script
     * script association(s), then execute the script(s). This command is
     * executed in a separate thread since it can take a noticeable amount time
     * to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The script execution command, however, is disabled
     * until the this command completes execution
     * 
     * @param dialog
     *            GUI component calling this method
     * 
     * @param tree
     *            table tree of the table instances (parent tables with their
     *            child tables); null if the tree should be loaded
     * 
     * @param associations
     *            array of script association definitions
     *************************************************************************/
    private void getDataAndExecuteScriptInBackground(final CcddDialogHandler dialog,
                                                     final CcddTableTreeHandler tree,
                                                     final String[][] associations)
    {
        final CcddDialogHandler cancelDialog = new CcddDialogHandler();

        // Create a thread to execute the script in the background
        final Thread scriptThread = new Thread(new Runnable()
        {
            /******************************************************************
             * Execute script association(s)
             *****************************************************************/
            @Override
            public void run()
            {
                // Disable the calling dialog's controls
                dialog.setControlsEnabled(false);

                // Execute the script association(s) and obtain the completion
                // status(es)
                isBad = getDataAndExecuteScript(dialog, tree, associations);

                // Close the script cancellation dialog. This also logs the
                // association completion status and re-enables the calling
                // dialog's- controls
                cancelDialog.closeDialog(CANCEL_BUTTON);
            }
        });

        // Display the script cancellation dialog in a background thread
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            /******************************************************************
             * Display the cancellation dialog
             *****************************************************************/
            @SuppressWarnings("deprecation")
            @Override
            protected void execute()
            {
                // Display the dialog and wait for the close action (the user
                // selects the Okay button or the script execution completes
                // and a Cancel button is issued)
                int option = cancelDialog.showMessageDialog(dialog,
                                                            "<html><b>Halt script execution<br><br>"
                                                                + "<font color=#ff0000><i>*** CAUTION ***<br>"
                                                                + "Restarting the application is "
                                                                + "</i><u>strongly</u><i><br>recommended "
                                                                + "after halting a script!",
                                                            "Stop Script",
                                                            JOptionPane.ERROR_MESSAGE,
                                                            DialogOption.OK_OPTION);

                // Check if the script execution was terminated by the user and
                // that the script is still executing
                if (option == OK_BUTTON && scriptThread.isAlive())
                {
                    // Stop script execution. Note: this method is deprecated
                    // due to inherent issues that can occur when a thread is
                    // abruptly stopped. However, simply interrupting the
                    // script thread leaves the script executing in the
                    // background. The stop method is the only manner in which
                    // the script itself can be terminated. Due to the
                    // potential for object corruption the application should
                    // be restarted once the stop method is performed
                    scriptThread.stop();

                    // Set the execution status(es) to indicate the scripts
                    // didn't complete
                    isBad = new boolean[associations.length];
                }
            }

            /******************************************************************
             * Perform cancellation dialog complete steps
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Log the result of the script execution(s)
                logScriptCompletionStatus(associations, isBad);

                // Enable the calling dialog's controls
                dialog.setControlsEnabled(true);
            }
        });

        // Execute the script in a background thread
        scriptThread.start();
    }

    /**************************************************************************
     * Get the table information array from the table data used by the script
     * script association(s), then execute the script(s)
     * 
     * @param component
     *            GUI component calling this method; set to null if executing
     *            the script from the command line
     * 
     * @param tree
     *            table tree of the table instances (parent tables with their
     *            child tables); null if the tree should be loaded
     * 
     * @param associations
     *            array of script association definitions
     * 
     * @return Array containing flags that indicate, for each association, if
     *         the association did not complete successfully
     *************************************************************************/
    protected boolean[] getDataAndExecuteScript(Component component,
                                                CcddTableTreeHandler tree,
                                                String[][] associations)
    {
        CcddTableTreeHandler tableTree = tree;

        // Create an array to indicate if an association has a problem that
        // prevents its execution
        boolean[] isBad = new boolean[associations.length];

        // Check if no table tree was provided
        if (tableTree == null)
        {
            // Build the table tree
            tableTree = new CcddTableTreeHandler(ccddMain,
                                                 TableTreeType.INSTANCE_ONLY,
                                                 component);
        }

        // Create storage for the individual tables' data and table path+names
        List<TableInformation> tableInformation = new ArrayList<TableInformation>();
        List<String> loadedTablePaths = new ArrayList<String>();

        // To reduce database access and speed script execution when executing
        // multiple associations, first load all of the associated tables,
        // making sure each is loaded only once. Step through each script
        // association definition
        for (int assnIndex = 0; assnIndex < associations.length; assnIndex++)
        {
            try
            {
                // Remove any leading or trailing white space characters
                associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()] = associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()].trim();

                // Check if at least one table is assigned to this script
                // association
                if (!associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()].isEmpty())
                {
                    // Separate the individual table path+names
                    String[] tablePaths = associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()].split(Pattern.quote(LIST_TABLE_SEPARATOR));

                    // Step through each table path+name
                    for (String tablePath : tablePaths)
                    {
                        // Check if the table is not already stored in the list
                        if (!loadedTablePaths.contains(tablePath))
                        {
                            // Add the table to the list. This is used to
                            // prevent reloading a table that's referenced in
                            // more than one association
                            loadedTablePaths.add(tablePath);

                            // Initialize the array for each of the tables to
                            // load from the database
                            combinedData = new String[0][0];

                            // Read the table and child table data from the
                            // database
                            tableInformation.add(readTable(tablePath, component));

                            // Get a reference to the last table information
                            // read
                            TableInformation tableInfo = tableInformation.get(tableInformation.size() - 1);

                            // Check if an error occurred loading the table
                            // data
                            if (tableInfo.isErrorFlag())
                            {
                                throw new CCDDException("table '"
                                                        + tableInfo.getProtoVariableName()
                                                        + "' failed to load");
                            }
                            // The table loaded successfully
                            else
                            {
                                // Store the data for the table and its child
                                // table
                                tableInfo.setData(combinedData);

                                // Get the type definition based on the table
                                // type name
                                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                                // Check if the type exists
                                if (typeDefn != null)
                                {
                                    // All structure table types are combined
                                    // and are referenced by the type name
                                    // "Structure", and all command table types
                                    // are combined and are referenced by the
                                    // type name "Command". The table type is
                                    // converted to the generic type
                                    // ("Structure" or "Command") if the
                                    // specified type is a representative of
                                    // the generic type. The original type name
                                    // is preserved in each row of the table's
                                    // data in an appended column

                                    // Check if this table represents a
                                    // structure
                                    if (typeDefn.isStructure())
                                    {
                                        // Set the table type to indicate a
                                        // structure
                                        tableInfo.setType(TYPE_STRUCTURE);
                                    }
                                    // Check if this table represents a command
                                    // table
                                    else if (typeDefn.isCommand())
                                    {
                                        // Set the table type to indicate a
                                        // command
                                        // table
                                        tableInfo.setType(TYPE_COMMAND);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (CCDDException ce)
            {
                // Inform the user that script execution failed
                logScriptError(component,
                               associations[assnIndex][AssociationsColumn.SCRIPT_FILE.ordinal()],
                               associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()],
                               ce.getMessage());

                // Set the flag for this association indicating it can't be
                // executed
                isBad[assnIndex] = true;
            }
            catch (Exception e)
            {
                // Display a dialog providing details on the unanticipated
                // error
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }
        }

        // Get the link assignment information, if any
        CcddLinkHandler linkHandler = new CcddLinkHandler(ccddMain,
                                                          component);

        // Load the data field information from the database
        CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                             null,
                                                             component);

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain,
                                                             component);
        // Once all table information is loaded then gather the data for each
        // association and execute it. Step through each script association
        // definition
        for (int assnIndex = 0; assnIndex < associations.length; assnIndex++)
        {
            // Check that an error didn't occur loading the data for this
            // association
            if (!isBad[assnIndex])
            {
                TableInformation[] combinedTableInfo = null;

                // Check if at least one table is assigned to this script
                // association
                if (!associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()].isEmpty())
                {
                    // Create storage for the table types used by this script
                    // association
                    List<String> tableTypes = new ArrayList<String>();

                    // Separate the individual table names
                    List<String> tableNames = Arrays.asList(associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()].split(Pattern.quote(LIST_TABLE_SEPARATOR)));

                    // Create a list of the table types referenced by this
                    // association. This is used to create the storage for the
                    // combined tables. Step through each table information
                    // instance
                    for (TableInformation tableInfo : tableInformation)
                    {
                        // Check if this table is a member of the association
                        if (tableNames.contains(tableInfo.getTablePath()))
                        {
                            // Check if the type for this table is not
                            // already in the list
                            if (!tableTypes.contains(tableInfo.getType()))
                            {
                                // Add the table type to the list
                                tableTypes.add(tableInfo.getType());
                            }
                        }
                    }

                    // Create storage for the combined table data
                    combinedTableInfo = new TableInformation[tableTypes.size()];

                    // Gather the table data, by table type, for each
                    // associated table. Step through each table type
                    // represented in this
                    // association
                    for (int typeIndex = 0; typeIndex < tableTypes.size(); typeIndex++)
                    {
                        String tableName = "";
                        String[][] allTableData = new String[0][0];

                        // Step through each table information instance
                        for (TableInformation tableInfo : tableInformation)
                        {
                            // Check if this table is a member of the
                            // association
                            if (tableNames.contains(tableInfo.getTablePath()))
                            {
                                // Check if the table types match
                                if (tableTypes.get(typeIndex).equals(tableInfo.getType()))
                                {
                                    // Check if the name hasn't been stored
                                    if (tableName.isEmpty())
                                    {
                                        // Assign the name of the first table
                                        // of this type as this type's table
                                        // name
                                        tableName += tableInfo.getTablePath();
                                    }

                                    // Append the table data to the combined
                                    // data array
                                    allTableData = CcddUtilities.concatenateArrays(allTableData,
                                                                                   tableInfo.getData());
                                }
                            }
                        }

                        // Create the table information from the table data
                        // obtained from the database
                        combinedTableInfo[typeIndex] = new TableInformation(tableTypes.get(typeIndex),
                                                                            tableName,
                                                                            allTableData,
                                                                            null,
                                                                            null,
                                                                            false,
                                                                            new Object[0][0]);
                    }
                }
                // No table is assigned to this script association
                else
                {
                    // Create a table information class in order to load and
                    // parse the data fields, and to allow access to the field
                    // methods
                    combinedTableInfo = new TableInformation[1];
                    combinedTableInfo[0] = new TableInformation("",
                                                                "",
                                                                null,
                                                                null,
                                                                null,
                                                                false,
                                                                new Object[0][0]);
                }

                try
                {
                    // Execute the script using the indicated table data
                    executeScript(component,
                                  associations[assnIndex][AssociationsColumn.SCRIPT_FILE.ordinal()],
                                  combinedTableInfo,
                                  linkHandler,
                                  fieldHandler,
                                  groupHandler);
                }
                catch (CCDDException ce)
                {
                    // Inform the user that script execution failed
                    logScriptError(component,
                                   associations[assnIndex][AssociationsColumn.SCRIPT_FILE.ordinal()],
                                   associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()],
                                   ce.getMessage());

                    // Set the flag for this association indicating it can't be
                    // executed
                    isBad[assnIndex] = true;
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated
                    // error
                    CcddUtilities.displayException(e, ccddMain.getMainFrame());
                }
            }
        }

        return isBad;
    }

    /**************************************************************************
     * Log the result of the script association execution(s)
     * 
     * @param associations
     *            array of script association definitions
     * 
     * @param isBad
     *            Array containing flags that indicate, for each association,
     *            if the association did not complete successfully
     *************************************************************************/
    protected void logScriptCompletionStatus(String[][] associations,
                                             boolean[] isBad)
    {
        // Initialize the success/fail flags and log messages
        boolean isSuccess = false;
        boolean isFail = false;
        String successMessage = "Following script(s) completed execution: ";
        String failMessage = "Following script(s) failed to execute: ";

        // Step through each script association
        for (int assnIndex = 0; assnIndex < associations.length; assnIndex++)
        {
            // Check if the script executed successfully
            if (isBad != null && !isBad[assnIndex])
            {
                // Append the script name and table(s) to the success
                // message
                successMessage += " '"
                                  + associations[assnIndex][AssociationsColumn.SCRIPT_FILE.ordinal()]
                                  + " : "
                                  + associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()]
                                  + "',";
                isSuccess = true;
            }
            // The script failed to execute
            else
            {
                // Append the script name and table(s) to the fail
                // message
                failMessage += " '"
                               + associations[assnIndex][AssociationsColumn.SCRIPT_FILE.ordinal()]
                               + " : "
                               + associations[assnIndex][AssociationsColumn.MEMBERS.ordinal()]
                               + "',";
                isFail = true;
            }
        }

        // Remove the trailing commas
        successMessage = CcddUtilities.removeTrailer(successMessage, ",");
        failMessage = CcddUtilities.removeTrailer(failMessage, ",");

        // Check if any script executed successfully
        if (isSuccess)
        {
            // Update the event log
            eventLog.logEvent(STATUS_MSG, successMessage);
        }

        // Check if any script failed to be executed
        if (isFail)
        {
            // Update the event log
            eventLog.logEvent(FAIL_MSG, failMessage);
        }
    }

    /**************************************************************************
     * Log a script execution error
     * 
     * @param component
     *            GUI component calling this method
     * 
     * @param scriptFileName
     *            script file name
     * 
     * @param tables
     *            tables associated with the script
     * 
     * @param cause
     *            cause of the execution error
     *************************************************************************/
    private void logScriptError(Component component,
                                String scriptFileName,
                                String tables,
                                String cause)
    {
        // Inform the user that the script can't be executed
        eventLog.logFailEvent(component,
                              "Script Error",
                              "Cannot execute script '"
                                  + scriptFileName
                                  + "' using table(s) '"
                                  + tables
                                  + "'; cause '"
                                  + cause
                                  + "'",
                              "<html><b>Cannot execute script '</b>"
                                  + scriptFileName
                                  + "<b>' using table(s) '</b>"
                                  + tables
                                  + "<b>'");
    }

    /**************************************************************************
     * Execute a script
     * 
     * @param component
     *            GUI component calling this method
     * 
     * @param scriptFileName
     *            script file name. The file extension is used to determine the
     *            script engine and therefore must conform to standard
     *            extension usage
     * 
     * @param tableInformation
     *            array of table information
     * 
     * @param linkHandler
     *            link handler reference
     * 
     * @param fieldHandler
     *            field handler reference
     * 
     * @param groupHandler
     *            group handler reference
     * 
     * @return true if an error occurs during script execution
     *************************************************************************/
    private void executeScript(Component component,
                               String scriptFileName,
                               TableInformation[] tableInformation,
                               CcddLinkHandler linkHandler,
                               CcddFieldHandler fieldHandler,
                               CcddGroupHandler groupHandler) throws CCDDException
    {
        // Check if the script file doesn't exist
        if (!new File(scriptFileName).isFile())
        {
            // Inform the user that the selected file is missing
            throw new CCDDException("cannot locate script file '"
                                    + scriptFileName
                                    + "'");
        }

        // Get the location of the file extension indicator
        int extensionStart = scriptFileName.lastIndexOf(".");

        // Check if the file name has no extension (i.e., "fileName.___")
        if (!(extensionStart > 0 && extensionStart != scriptFileName.length() - 1))
        {
            // Inform the user that the selected file is missing the file
            // extension
            throw new CCDDException("script file '"
                                    + scriptFileName
                                    + "' has no file extension");

        }

        // Extract the file extension from the file name
        String extension = scriptFileName.substring(extensionStart + 1);

        // Flag that indicates if a script engine is found that matches
        // the script file extension
        boolean isValidExt = false;

        // Step through each engine factory
        for (ScriptEngineFactory factory : scriptFactories)
        {
            // Check if this script engine is applicable to the script
            // file's extension
            if (factory.getExtensions().contains(extension))
            {
                // Set the flag that indicates a script engine is found
                // that matches the extension
                isValidExt = true;

                try
                {
                    // Get the script engine
                    ScriptEngine scriptEngine = factory.getScriptEngine();

                    // Bind an instance of the script data access handler class
                    // (named 'ccdd') to the script context so that the
                    // handler's public access methods can be accessed by the
                    // script. This is required for JavaScript engine 'Rhino'
                    // (Java 7 and earlier) or when using 'Nashorn'
                    // compatibility (Java 8 and later)
                    Bindings scriptBindings = scriptEngine.createBindings();
                    scriptBindings.put("ccdd",
                                       new CcddScriptDataAccessHandler(ccddMain,
                                                                       tableInformation,
                                                                       linkHandler,
                                                                       fieldHandler,
                                                                       groupHandler,
                                                                       scriptFileName,
                                                                       component));
                    scriptEngine.setBindings(scriptBindings,
                                             ScriptContext.ENGINE_SCOPE);

                    // Execute the script
                    scriptEngine.eval(new FileReader(scriptFileName));
                }
                catch (FileNotFoundException fnfe)
                {
                    // Inform the user that the selected file cannot be read
                    throw new CCDDException("cannot read script file '"
                                            + scriptFileName
                                            + "'");
                }
                catch (ScriptException se)
                {
                    // Inform the user that the script encountered an error
                    throw new CCDDException("script file '"
                                            + scriptFileName
                                            + "' error '"
                                            + se.getMessage()
                                            + "'");
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated
                    // error
                    CcddUtilities.displayException(e, ccddMain.getMainFrame());
                }

                // Stop searching since a match was found
                break;
            }
        }

        // Check if the file extension doesn't match one supported by any of
        // the available script engines
        if (!isValidExt)
        {
            // Inform the user that the selected file's extension isn't
            // recognized
            throw new CCDDException("script file '"
                                    + scriptFileName
                                    + "' extension is unsupported");
        }
    }

    /**************************************************************************
     * Recursive method to load a table, and all the tables referenced within
     * it and its child tables. The data is combined into a single array
     * 
     * @param tablePath
     *            table path
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return A TableDataHandler for the parent table. The error flag for the
     *         table data handler is set if an error occurred loading the data
     *************************************************************************/
    private TableInformation readTable(String tablePath, Component parent)
    {
        // Read the table's data from the database
        TableInformation tableInfo = dbTable.loadTableData(tablePath,
                                                           false,
                                                           false,
                                                           false,
                                                           false,
                                                           parent);

        // Check that the data was successfully loaded from the database and
        // that the table isn't empty
        if (!tableInfo.isErrorFlag() && tableInfo.getData().length != 0)
        {
            // Get the table's type definition
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

            // Get the data and place it in an array for reference below. Add
            // columns to contain the table type and path
            String[][] data = CcddUtilities.appendArrayColumns(tableInfo.getData(), 2);
            int typeColumn = data[0].length - TYPE_COLUMN_DELTA;
            int pathColumn = data[0].length - PATH_COLUMN_DELTA;

            // Get the index of the column containing the data type for this
            // table if it has one
            int dataTypeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);

            // Step through each row
            for (int row = 0; row < data.length && !tableInfo.isErrorFlag(); row++)
            {
                // Use the index column to store the table path and type for
                // reference during script execution
                data[row][typeColumn] = tableInfo.getType();
                data[row][pathColumn] = tablePath;

                // Store the data from the table in the combined storage array
                combinedData = CcddUtilities.concatenateArrays(combinedData,
                                                               new String[][] {data[row]});

                // Check if this is a table reference (a data type column was
                // found and it does not contain a primitive data type)
                if (dataTypeColumn != -1
                    && !dataTypeHandler.isPrimitive(data[row][dataTypeColumn]))
                {
                    // Get the column containing the variable name for this
                    // table
                    int varNameColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);

                    // Check that a variable name column was found
                    if (varNameColumn != -1)
                    {
                        // Get the column containing the array size for this
                        // table
                        int arraySizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);

                        // Check if the data type or variable name isn't blank,
                        // and if an array size column doesn't exist or that
                        // the row doesn't reference an array definition. This
                        // is necessary to prevent appending the prototype
                        // information for this data type structure
                        if ((!data[row][dataTypeColumn].isEmpty()
                            || !data[row][varNameColumn].isEmpty())
                            && (arraySizeColumn == -1
                                || data[row][arraySizeColumn].isEmpty()
                                || ArrayVariable.isArrayMember(data[row][varNameColumn])))
                        {
                            // Get the variable in the format
                            // dataType.variableName, prepend a comma to
                            // separate the new variable from the preceding
                            // variable path, then break down the child table
                            readTable(tablePath
                                      + ","
                                      + data[row][dataTypeColumn]
                                      + "."
                                      + data[row][varNameColumn],
                                      parent);
                        }
                    }
                    // Table has no variable name column
                    else
                    {
                        tableInfo.setErrorFlag();
                        break;
                    }
                }
            }
        }

        return tableInfo;
    }

    /**************************************************************************
     * Get the script associations list model
     * 
     * @return Script associations list model
     *************************************************************************/
    protected DefaultListModel<String> getAssociationsModel()
    {
        return associationsModel;
    }

    /**************************************************************************
     * Get the script associations list model that contains extra
     * (non-displayed) information
     * 
     * @return Script associations list long model
     *************************************************************************/
    protected DefaultListModel<String> getAssociationsLongModel()
    {
        return associationsLongModel;
    }

    /**************************************************************************
     * Get the script associations list object
     * 
     * @return Script associations list object
     *************************************************************************/
    protected JList<String> getAssociationsList()
    {
        return associationsList;
    }

    /**************************************************************************
     * Get the list of script association that are stored in the database
     * 
     * @return List of script associations that are stored in the database
     *************************************************************************/
    protected List<String[]> getCommittedAssociations()
    {
        return committedAssociationsList;
    }

    /**************************************************************************
     * Set the list of script association that are stored in the database
     * 
     * @param assns
     *            list of script associations that are stored in the database
     *************************************************************************/
    protected void setCommittedAssociations(List<String[]> assns)
    {
        committedAssociationsList = assns;
    }

    /**************************************************************************
     * Retrieve the script associations stored in the database and from these
     * build a list for display and selection of the script associations
     * 
     * @param scriptText
     *            text to display above the script association list
     * 
     * @param numDisplayRows
     *            number of row in the list to initially display
     * 
     * @param allowSelectDisabled
     *            true if disabled associations can be selected; false if not.
     *            In the script manager disabled associations are selectable so
     *            that these can be deleted if desired. Scripts that are
     *            selected and disabled are ignored when executing scripts
     * 
     * @param parent
     *            GUI component calling this method
     * 
     * @return List object for display and selection of the script associations
     *************************************************************************/
    @SuppressWarnings("serial")
    protected JPanel createScriptAssociationPanel(String scriptText,
                                                  int numDisplayRows,
                                                  boolean allowSelectDisabled,
                                                  Component parent)
    {
        associationsModel = new DefaultListModel<String>();
        associationsLongModel = new DefaultListModel<String>();

        // Store the flag that allows selection of disabled scripts
        isSelectDisabled = allowSelectDisabled;

        // Read the stored script associations from the database
        committedAssociationsList = dbTable.retrieveInformationTable(InternalTable.ASSOCIATIONS,
                                                                     parent);

        // Get the list of table names and their associated table type
        ArrayListMultiple protoNamesAndTableTypes = new ArrayListMultiple();
        protoNamesAndTableTypes.addAll(dbTable.queryTableAndTypeList(parent));

        // Step through each script association
        for (String[] association : committedAssociationsList)
        {
            String textColor = "";

            // Get the reference to the association's script file
            File file = new File(association[AssociationsColumn.SCRIPT_FILE.ordinal()]);

            try
            {
                // Check if the script file doesn't exist
                if (!file.exists())
                {
                    throw new CCDDException();
                }

                // Step through each table referenced in this association
                for (String tableName : association[AssociationsColumn.MEMBERS.ordinal()].trim().split(Pattern.quote(LIST_TABLE_SEPARATOR)))
                {
                    // Check that at least one table is referenced
                    if (!tableName.isEmpty())
                    {
                        String parentTable = "";

                        // Step through each data type and variable name pair
                        for (String variable : tableName.split(","))
                        {
                            // Split the variable reference into the data type
                            // and variable name
                            String[] typeAndVar = variable.split(Pattern.quote("."));

                            // Locate the table's prototype in the list
                            int index = protoNamesAndTableTypes.indexOf(typeAndVar[0]);

                            // Check if the prototype table doesn't exist
                            if (index == -1)
                            {
                                throw new CCDDException();
                            }

                            // Check if a variable name is present (the first
                            // pass is for the root table, so there is no
                            // variable name)
                            if (typeAndVar.length == 2)
                            {
                                // Get the table's type definition
                                TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(protoNamesAndTableTypes.get(index)[2]);

                                // Check if the table doesn't represent a
                                // structure
                                if (!typeDefn.isStructure())
                                {
                                    throw new CCDDException();
                                }

                                // Get the name of the column that represents
                                // the variable name
                                String varColumn = typeDefn.getDbColumnNameByInputType(InputDataType.VARIABLE);

                                // Search for the variable name in the parent
                                // table
                                List<String[]> result = dbTable.queryDatabase("SELECT "
                                                                                 + varColumn
                                                                                 + " FROM "
                                                                                 + parentTable
                                                                                 + " WHERE "
                                                                                 + varColumn
                                                                                 + " = '"
                                                                                 + typeAndVar[1]
                                                                                 + "';",
                                                                                 parent);

                                // Check if no variable by this name exists in
                                // the parent table
                                if (result == null || result.size() == 0)
                                {
                                    throw new CCDDException();
                                }
                            }

                            // Store the data type, which is the parent for the
                            // next variable (if any)
                            parentTable = typeAndVar[0];
                        }
                    }
                }
            }
            catch (CCDDException ce)
            {
                // The script file or associated table doesn't exist; set the
                // text color to indicate the association isn't available
                textColor = DISABLED_TEXT_COLOR;
            }

            // Add the association to the script associations lists
            associationsModel.addElement(textColor
                                         + file.getName()
                                         + LIST_TABLE_DESC_SEPARATOR
                                         + association[AssociationsColumn.MEMBERS.ordinal()]);
            associationsLongModel.addElement(association[AssociationsColumn.SCRIPT_FILE.ordinal()]
                                             + LIST_TABLE_DESC_SEPARATOR
                                             + association[AssociationsColumn.MEMBERS.ordinal()]);
        }

        // Create the script associations JList
        associationsList = new JList<String>(associationsModel);

        // Set the list selection model in order to detect list items that
        // aren't allowed to be selected
        associationsList.setSelectionModel(new DefaultListSelectionModel()
        {
            /******************************************************************
             * Check if the list item is selected, ignoring items that begin
             * with an HTML tag
             *****************************************************************/
            @Override
            public boolean isSelectedIndex(int index)
            {
                // Return false if the list item begins with an HTML tag
                // (unless selection of disabled associations is allowed)
                return associationsList.getModel().getElementAt(index).startsWith("<")
                       && !isSelectDisabled
                                           ? false
                                           : super.isSelectedIndex(index);
            }
        });

        // Get the pixel height for each list row
        int itemHeight = (int) associationsList.getFontMetrics(LABEL_FONT_PLAIN).getStringBounds("X",
                                                                                                 associationsList.getGraphics()).getHeight()
                         + LABEL_VERTICAL_SPACING / 2 + 1;

        // Create an empty border for use with the list dialog components
        Border emptyBorder = BorderFactory.createEmptyBorder();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   0,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Create a panel to contain the lists and buttons
        JPanel listPnl = new JPanel(new GridBagLayout());
        listPnl.setBorder(emptyBorder);

        // Set the list item text height and row count
        numDisplayRows = Math.max(Math.min(numDisplayRows, 15), 3);
        associationsList.setVisibleRowCount(numDisplayRows);
        associationsList.setFixedCellHeight(itemHeight);

        // Set the list font
        associationsList.setFont(LABEL_FONT_PLAIN);

        // Set the borders around the list components
        associationsList.setBorder(emptyBorder);
        listPnl.setBorder(emptyBorder);

        // Create a scroll pane and add the list to it
        JScrollPane scrollPane = new JScrollPane(associationsList);

        // Set the scroll pane's border
        scrollPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                             Color.LIGHT_GRAY,
                                                             Color.GRAY));

        // Set the scroll pane initial size
        scrollPane.setPreferredSize(new Dimension(150,
                                                  scrollPane.getPreferredSize().height));
        scrollPane.setMinimumSize(scrollPane.getPreferredSize());

        // Check if the list label text is supplied
        if (scriptText != null)
        {
            // Create the list label
            JLabel listLbl = new JLabel(scriptText);
            listLbl.setForeground(LABEL_TEXT_COLOR);
            listLbl.setFont(LABEL_FONT_BOLD);
            listPnl.add(listLbl, gbc);
            gbc.gridy++;
        }

        // Add the list components to the list panel
        gbc.weighty = 1.0;
        listPnl.add(scrollPane, gbc);

        return listPnl;
    }
}
