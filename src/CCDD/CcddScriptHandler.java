/**
 * CFS Command & Data Dictionary script handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.HIDE_SCRIPT_PATH;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.BoxLayout;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.ArrayListMultiple;
import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.AssociationsTableColumnInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddUndoHandler.UndoableTableModel;

/******************************************************************************
 * CFS Command & Data Dictionary script handler class. This class handles
 * execution of the data output scripts
 *****************************************************************************/
public class CcddScriptHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddJTableHandler assnsTable;

    // Component referenced by multiple methods
    private JCheckBox hideScriptFilePath;

    // List of script engine factories that are available on this platform
    private final List<ScriptEngineFactory> scriptFactories;

    // Global storage for the data obtained in the recursive table data reading
    // method
    private String[][] combinedData;

    // Array to indicate if a script association has a problem that prevents
    // its execution
    private boolean[] isBad;

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
     * Retrieve the script associations stored in the database and from these
     * build the array for display and selection of the script associations
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
     * @return Object array containing the script associations
     *************************************************************************/
    private Object[][] getScriptAssociationData(boolean allowSelectDisabled,
                                                Component parent)
    {
        List<Object[]> associationsData = new ArrayList<Object[]>();

        // Read the stored script associations from the database
        List<String[]> committedAssociations = dbTable.retrieveInformationTable(InternalTable.ASSOCIATIONS,
                                                                                parent);

        // Get the list of table names and their associated table type
        ArrayListMultiple protoNamesAndTableTypes = new ArrayListMultiple();
        protoNamesAndTableTypes.addAll(dbTable.queryTableAndTypeList(parent));

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain,
                                                             null,
                                                             parent);

        // Step through each script association
        for (String[] assn : committedAssociations)
        {
            boolean isAvailable = true;

            // Get the reference to the association's script file
            File file = new File(assn[AssociationsColumn.SCRIPT_FILE.ordinal()]);

            try
            {
                // Check if the script file doesn't exist
                if (!file.exists())
                {
                    throw new CCDDException();
                }

                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler);

                // Check if at least one table is assigned to this script
                // association
                if (!tablePaths.isEmpty())
                {
                    // Step through each table referenced in this association
                    for (String tablePath : tablePaths)
                    {
                        String parentTable = "";

                        // Step through each data type and variable name pair
                        for (String variable : tablePath.split(","))
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
                // flag to indicate the association isn't available
                isAvailable = false;
            }

            // Add the association to the script associations list
            associationsData.add(new Object[] {assn[AssociationsColumn.DESCRIPTION.ordinal()],
                                               assn[AssociationsColumn.SCRIPT_FILE.ordinal()],
                                               assn[AssociationsColumn.MEMBERS.ordinal()],
                                               isAvailable});
        }

        return associationsData.toArray(new Object[0][0]);
    }

    /**************************************************************************
     * Get a reference to the script associations table
     *************************************************************************/
    protected CcddJTableHandler getAssociationsTable()
    {
        return assnsTable;
    }

    /**************************************************************************
     * Create the panel containing the script associations table
     *
     * @param title
     *            text to display above the script associations table; null or
     *            blank if no text is to be displayed
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
     * @return Reference to the JPanel containing the script associations table
     *************************************************************************/
    @SuppressWarnings("serial")
    protected JPanel getAssociationsPanel(String title,
                                          final boolean allowSelectDisabled,
                                          final Component parent)
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   0,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Define the panel to contain the table
        JPanel assnsPnl = new JPanel(new GridBagLayout());

        // Check if a table title is provided
        if (title != null && !title.isEmpty())
        {
            // Create the script associations label
            JLabel assnsLbl = new JLabel(title);
            assnsLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            assnsLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
            assnsPnl.add(assnsLbl, gbc);
            gbc.gridy++;
        }

        // Create the table to display the search results
        assnsTable = new CcddJTableHandler()
        {
            /******************************************************************
             * Allow multiple line display in all columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /******************************************************************
             * Allow editing the description in the script manager's
             * associations table
             *****************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return column == convertColumnIndexToModel(AssociationsTableColumnInfo.DESCRIPTION.ordinal())
                       && allowSelectDisabled;
            }

            /******************************************************************
             * Load the script associations data into the table and format the
             * table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(getScriptAssociationData(allowSelectDisabled, parent),
                                            AssociationsTableColumnInfo.getColumnNames(),
                                            null,
                                            new Integer[] {AssociationsTableColumnInfo.AVAILABLE.ordinal()},
                                            null,
                                            AssociationsTableColumnInfo.getToolTips(),
                                            true,
                                            true,
                                            true,
                                            true);
            }

            /******************************************************************
             * Highlight the matching search text in the context column cells
             *
             * @param component
             *            reference to the table cell renderer component
             *
             * @param value
             *            cell value
             *
             * @param isSelected
             *            true if the cell is to be rendered with the selection
             *            highlighted
             *
             * @param int row cell row, view coordinates
             *
             * @param column
             *            cell column, view coordinates
             *****************************************************************/
            @Override
            protected void doSpecialRendering(Component component,
                                              String text,
                                              boolean isSelected,
                                              int row,
                                              int column)
            {
                // Check if the association on the specified row is flagged as
                // unavailable
                if (!isAssociationAvailable(convertRowIndexToModel(row)))
                {
                    ((JTextComponent) component).setForeground(Color.GRAY);

                    // Check if selection of disabled associations isn't
                    // allowed
                    if (!allowSelectDisabled)
                    {
                        ((JTextComponent) component).setBackground(ModifiableColorInfo.TABLE_BACK.getColor());
                    }
                }

                // Check if this is the script file column and the script file
                // path should not be displayed
                if (column == convertColumnIndexToView(AssociationsTableColumnInfo.SCRIPT_FILE.ordinal())
                    && hideScriptFilePath.isSelected())
                {
                    // Remove the path, leaving only the script file name
                    ((JTextComponent) component).setText(((JTextComponent) component).getText().replaceFirst(".*"
                                                                                                             + Pattern.quote(File.separator),
                                                                                                             ""));
                }
            }

            /**************************************************************
             * Override the method that sets the row sorter so that special
             * sorting can be performed on the script file column
             *************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Get a reference to the sorter
                @SuppressWarnings("unchecked")
                TableRowSorter<UndoableTableModel> sorter = (TableRowSorter<UndoableTableModel>) getRowSorter();

                // Check if the sorter exists. The sorter doesn't exist (is
                // null) if there are no rows in the table
                if (sorter != null)
                {
                    // Add a sort comparator for the script file column
                    sorter.setComparator(AssociationsTableColumnInfo.SCRIPT_FILE.ordinal(), new Comparator<String>()
                    {
                        /**************************************************
                         * Override the comparison when sorting the script file
                         * column to ignore the script file paths if these are
                         * currently hidden
                         *************************************************/
                        @Override
                        public int compare(String filePath1, String filePath2)
                        {
                            return (hideScriptFilePath.isSelected()
                                                                   ? filePath1.replaceFirst(".*"
                                                                                            + Pattern.quote(File.separator),
                                                                                            "")
                                                                   : filePath1).compareTo(hideScriptFilePath.isSelected()
                                                                                                                         ? filePath2.replaceFirst(".*"
                                                                                                                                                  + Pattern.quote(File.separator),
                                                                                                                                                  "")
                                                                                                                         : filePath2);
                        }
                    });
                }
            }
        };

        // Set the list selection model in order to detect table rows that
        // aren't allowed to be selected
        assnsTable.setSelectionModel(new DefaultListSelectionModel()
        {
            /******************************************************************
             * Check if the script association table item is selected, ignoring
             * associations that are flagged as unavailable
             *****************************************************************/
            @Override
            public boolean isSelectedIndex(int row)
            {
                return allowSelectDisabled
                       || isAssociationAvailable(assnsTable.convertRowIndexToModel(row))
                                                                                        ? super.isSelectedIndex(row)
                                                                                        : false;
            }
        });

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(assnsTable);

        // Set up the search results table parameters
        assnsTable.setFixedCharacteristics(scrollPane,
                                           false,
                                           ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                           TableSelectionMode.SELECT_BY_ROW,
                                           true,
                                           ModifiableColorInfo.TABLE_BACK.getColor(),
                                           true,
                                           true,
                                           ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                           true);

        // Define the panel to contain the table and add it to the dialog
        JPanel assnsTblPnl = new JPanel();
        assnsTblPnl.setLayout(new BoxLayout(assnsTblPnl, BoxLayout.X_AXIS));
        assnsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        assnsTblPnl.add(scrollPane);
        gbc.weighty = 1.0;
        assnsPnl.add(assnsTblPnl, gbc);

        // Create the check box for hiding/showing the file paths in the
        // associations table script file column
        hideScriptFilePath = new JCheckBox("Hide script file path",
                                           ccddMain.getProgPrefs().getBoolean(HIDE_SCRIPT_PATH, false));
        hideScriptFilePath.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        hideScriptFilePath.setBorder(BorderFactory.createEmptyBorder());
        hideScriptFilePath.setToolTipText(CcddUtilities.wrapText("Remove the file paths from the script file column",
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for check box selection changes
        hideScriptFilePath.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Handle a change in the hide script file path check box state
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                assnsTable.repaint();
                ccddMain.getProgPrefs().putBoolean(HIDE_SCRIPT_PATH, hideScriptFilePath.isSelected());
            }
        });

        gbc.weighty = 0.0;
        gbc.gridy++;
        assnsPnl.add(hideScriptFilePath, gbc);
        return assnsPnl;
    }

    /**************************************************************************
     * Check if the script association on the specified row in the associations
     * table is available. An association is unavailable if the script or
     * tables is not present
     *
     * @param row
     *            table row (model coordinates)
     *
     * @return true if the script association on the specified row is available
     *************************************************************************/
    private boolean isAssociationAvailable(int row)
    {
        return Boolean.parseBoolean(assnsTable.getModel().getValueAt(row,
                                                                     AssociationsTableColumnInfo.AVAILABLE.ordinal())
                                              .toString());
    }

    /**************************************************************************
     * Get the list of a script association's member table paths. If a group is
     * referenced then its member tables are included
     *
     * @param associationMembers
     *            association members as a single string (as stored in the
     *            database)
     *
     * @param groupHandler
     *            group handler reference
     *
     * @return List containing the tables (path+name) from the association
     *         member string
     *************************************************************************/
    private List<String> getAssociationTablePaths(String associationMembers,
                                                  CcddGroupHandler groupHandler)
    {
        List<String> tablePaths = new ArrayList<String>();

        // Separate the individual table path+names
        String[] members = associationMembers.split(Pattern.quote(LIST_TABLE_SEPARATOR));

        // Step through each table path+name or group
        for (String member : members)
        {
            // Check if this is a reference to a group
            if (member.startsWith(GROUP_DATA_FIELD_IDENT))
            {
                // Extract the group name and use it to get the group's
                // information reference
                GroupInformation groupInfo = groupHandler.getGroupInformationByName(member.substring(GROUP_DATA_FIELD_IDENT.length()));

                // Check if the group exists
                if (groupInfo != null)
                {
                    // Add the group member table(s) to the list
                    tablePaths.addAll(groupInfo.getTablesAndAncestors());
                }
                // The group doesn't exist
                else
                {
                    // Add an invalid table so that he association is flagged
                    // as unavailable
                    tablePaths.add(" ");
                }
            }
            // Check if the table path isn't blank
            else if (!member.trim().isEmpty())
            {
                // Add the table path
                tablePaths.add(member);
            }
        }

        return tablePaths;
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
            engineInfo.add(CcddUtilities.colorHTMLText(name + ": ",
                                                       ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor())
                           + factory.getLanguageVersion()
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
     *            reference to the script dialog (manager or executive) calling
     *            this method
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @param executeAll
     *            true to execute all of the available script associations;
     *            false to execute only the selected, available associations
     *************************************************************************/
    protected void executeScriptAssociations(CcddFrameHandler dialog,
                                             CcddTableTreeHandler tableTree,
                                             boolean executeAll)
    {
        List<Object[]> selectedAssn;

        // Get the current associations table data
        List<Object[]> assnsData = assnsTable.getTableDataList(true);

        // Check if the flag is set to execute all valid associations
        if (executeAll)
        {
            // Set the data to all of the table data
            selectedAssn = assnsData;

            // Step through each association
            for (int row = selectedAssn.size() - 1; row >= 0; row--)
            {
                // Check if the association is unavailable; i.e., that the
                // script file and/or table(s) are not present
                if (!isAssociationAvailable(assnsTable.convertRowIndexToModel(row)))
                {
                    // Remove the association form the list of those to execute
                    selectedAssn.remove(row);
                }
            }
        }
        // Only execute the selected associations
        else
        {
            selectedAssn = new ArrayList<Object[]>();

            // Step through each selected row in the associations table
            for (int row : assnsTable.getSelectedRows())
            {
                // Convert the row index to model coordinates in case the table
                // is sorted by one of the columns
                row = assnsTable.convertRowIndexToModel(row);

                // Check if the association is available; i.e., that the script
                // file and table(s) are present
                if (isAssociationAvailable(row))
                {
                    // Add the association to the list of those to execute
                    selectedAssn.add(assnsData.get(row));
                }
            }
        }

        // Check that at least one association is to be executed
        if (selectedAssn.size() != 0)
        {
            // Execute the script script association(s)
            getDataAndExecuteScriptInBackground(dialog,
                                                tableTree,
                                                selectedAssn);
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
     *            reference to the script dialog (manager or executive) calling
     *            this method
     *
     * @param tree
     *            table tree of the table instances (parent tables with their
     *            child tables); null if the tree should be loaded
     *
     * @param associations
     *            list of script association to execute
     *************************************************************************/
    private void getDataAndExecuteScriptInBackground(final CcddFrameHandler dialog,
                                                     final CcddTableTreeHandler tree,
                                                     final List<Object[]> associations)
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
                                                            "<html><b>Script execution in progress...<br><br>"
                                                                + CcddUtilities.colorHTMLText("*** Press </i>Halt<i> "
                                                                                              + "to terminate script execution ***",
                                                                                              Color.RED),
                                                            "Script Executing",
                                                            JOptionPane.ERROR_MESSAGE,
                                                            DialogOption.HALT_OPTION);

                // Check if the script execution was terminated by the user and
                // that the script is still executing
                if (option == OK_BUTTON && scriptThread.isAlive())
                {
                    // Forcibly stop script execution. Note: this method is
                    // deprecated due to inherent issues that can occur when a
                    // thread is abruptly stopped. However, the stop method is
                    // the only manner in which the script can be terminated
                    // (without additional code within the script itself, which
                    // cannot be assumed since the scripts are user-generated).
                    // There shouldn't be a potential for object corruption in
                    // the application since it doesn't reference any objects
                    // created by a script
                    scriptThread.stop();

                    // Set the execution status(es) to indicate the scripts
                    // didn't complete
                    isBad = new boolean[associations.size()];
                    Arrays.fill(isBad, true);
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
     * @param dialog
     *            reference to the script dialog (manager or executive) calling
     *            this method; set to null if executing the script from the
     *            command line
     *
     * @param tree
     *            table tree of the table instances (parent tables with their
     *            child tables); null if the tree should be loaded
     *
     * @param associations
     *            list of script associations to execute
     *
     * @return Array containing flags that indicate, for each association, if
     *         the association did not complete successfully
     *************************************************************************/
    protected boolean[] getDataAndExecuteScript(Component component,
                                                CcddTableTreeHandler tree,
                                                List<Object[]> associations)
    {
        int assnIndex = 0;

        CcddTableTreeHandler tableTree = tree;

        // Create an array to indicate if an association has a problem that
        // prevents its execution
        boolean[] isBad = new boolean[associations.size()];

        // Check if no table tree was provided
        if (tableTree == null)
        {
            // Build the table tree
            tableTree = new CcddTableTreeHandler(ccddMain,
                                                 TableTreeType.INSTANCE_TABLES,
                                                 component);
        }

        // Create storage for the individual tables' data and table path+names
        List<TableInformation> tableInformation = new ArrayList<TableInformation>();
        List<String> loadedTablePaths = new ArrayList<String>();

        // Get the link assignment information, if any
        CcddLinkHandler linkHandler = new CcddLinkHandler(ccddMain, component);

        // Load the data field information from the database
        CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                             null,
                                                             component);

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain,
                                                             null,
                                                             component);

        // To reduce database access and speed script execution when executing
        // multiple associations, first load all of the associated tables,
        // making sure each is loaded only once. Step through each script
        // association definition
        for (Object[] assn : associations)
        {
            try
            {
                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler);

                // Check if at least one table is assigned to this script
                // association
                if (!tablePaths.isEmpty())
                {
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
                               assn[AssociationsColumn.SCRIPT_FILE.ordinal()].toString(),
                               assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
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

            assnIndex++;
        }

        assnIndex = 0;

        // Once all table information is loaded then gather the data for each
        // association and execute it. Step through each script association
        // definition
        for (Object[] assn : associations)
        {
            // Check that an error didn't occur loading the data for this
            // association
            if (!isBad[assnIndex])
            {
                TableInformation[] combinedTableInfo = null;
                List<String> groupNames = new ArrayList<String>();

                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler);

                String[] members = assn[AssociationsColumn.MEMBERS.ordinal()].toString().split(Pattern.quote(LIST_TABLE_SEPARATOR));

                // Step through each table path+name or group
                for (String member : members)
                {
                    // Check if this is a reference to a group
                    if (member.startsWith(GROUP_DATA_FIELD_IDENT))
                    {
                        // Add the group name to the list of referenced groups
                        groupNames.add(member.substring(GROUP_DATA_FIELD_IDENT.length()));
                    }
                }

                // Check if at least one table is assigned to this script
                // association
                if (!tablePaths.isEmpty())
                {
                    // Create storage for the table types used by this script
                    // association
                    List<String> tableTypes = new ArrayList<String>();

                    // Create a list of the table types referenced by this
                    // association. This is used to create the storage for the
                    // combined tables. Step through each table information
                    // instance
                    for (TableInformation tableInfo : tableInformation)
                    {
                        // Check if this table is a member of the association
                        if (tablePaths.contains(tableInfo.getTablePath()))
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
                            if (tablePaths.contains(tableInfo.getTablePath()))
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
                                                                new String[0][0],
                                                                null,
                                                                null,
                                                                false,
                                                                new Object[0][0]);
                }

                try
                {
                    // Execute the script using the indicated table data
                    executeScript(component,
                                  assn[AssociationsColumn.SCRIPT_FILE.ordinal()].toString(),
                                  combinedTableInfo,
                                  groupNames,
                                  linkHandler,
                                  fieldHandler,
                                  groupHandler);
                }
                catch (CCDDException ce)
                {
                    // Inform the user that script execution failed
                    logScriptError(component,
                                   assn[AssociationsColumn.SCRIPT_FILE.ordinal()].toString(),
                                   assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
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

            assnIndex++;
        }

        return isBad;
    }

    /**************************************************************************
     * Log the result of the script association execution(s)
     *
     * @param associations
     *            list of script association executed
     *
     * @param isBad
     *            Array containing flags that indicate, for each association,
     *            if the association did not complete successfully
     *************************************************************************/
    protected void logScriptCompletionStatus(List<Object[]> associations,
                                             boolean[] isBad)
    {
        int assnIndex = 0;

        // Initialize the success/fail flags and log messages
        boolean isSuccess = false;
        boolean isFail = false;
        String successMessage = "Following script(s) completed execution: ";
        String failMessage = "Following script(s) failed to execute: ";

        // Step through each script association
        for (Object[] assn : associations)
        {
            // Check if the script executed successfully
            if (isBad != null && !isBad[assnIndex])
            {
                // Append the script name and table(s) to the success
                // message
                successMessage += " '"
                                  + assn[AssociationsColumn.SCRIPT_FILE.ordinal()]
                                  + " : "
                                  + assn[AssociationsColumn.MEMBERS.ordinal()]
                                  + "',";
                isSuccess = true;
            }
            // The script failed to execute
            else
            {
                // Append the script name and table(s) to the fail
                // message
                failMessage += " '"
                               + assn[AssociationsColumn.SCRIPT_FILE.ordinal()]
                               + " : "
                               + assn[AssociationsColumn.MEMBERS.ordinal()]
                               + "',";
                isFail = true;
            }

            assnIndex++;
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
     * @param groupNames
     *            list containing the names of any groups referenced in the
     *            script association
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
                               List<String> groupNames,
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
                                                                       groupNames,
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
}
