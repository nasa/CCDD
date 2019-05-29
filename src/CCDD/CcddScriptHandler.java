/**
 * CFS Command and Data Dictionary script handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ALL_TABLES_GROUP_NODE_NAME;
import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR;
import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR_CMD_LN;
import static CCDD.CcddConstants.DEFAULT_HIDE_DATA_TYPE;
import static CCDD.CcddConstants.DEFAULT_PROTOTYPE_NODE_NAME;
import static CCDD.CcddConstants.DEFAULT_TYPE_NAME_SEP;
import static CCDD.CcddConstants.DEFAULT_VARIABLE_PATH_SEP;
import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.HIDE_DATA_TYPE;
import static CCDD.CcddConstants.HIDE_SCRIPT_PATH;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_NAME_SEPARATOR;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.VARIABLE_PATH_SEPARATOR;
import static CCDD.CcddConstants.EventLogMessageType.FAIL_MSG;
import static CCDD.CcddConstants.EventLogMessageType.STATUS_MSG;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.AssociationsTableColumnInfo;
import CCDD.CcddConstants.AvailabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddUndoHandler.UndoableTableModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary script handler class. This class handles execution of the data
 * output scripts
 *************************************************************************************************/
public class CcddScriptHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddEventLogDialog eventLog;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddVariableHandler variableHandler;
    private CcddJTableHandler assnsTable;
    private CcddFrameHandler scriptDialog = null;

    // Components referenced by multiple methods
    private JCheckBox hideScriptFilePath;
    private JCheckBox hideUnavailableAssns;
    private JTextField envVarOverrideFld;
    private static CcddHaltDialog haltDlg;

    // List of script engine factories that are available on this platform
    private final List<ScriptEngineFactory> scriptFactories;

    // Global storage for the data obtained in the recursive table data reading method
    private Object[][] combinedData;

    // List containing the table paths for the tables loaded for a script association. Used to
    // prevent loading the same table more than once
    private List<String> loadedTablePaths;

    // Array to indicate if a script association has a problem that prevents its execution
    private boolean[] isBad;

    // Environment variable map
    private Map<String, String> envVarMap;

    // Variable path separators and flag to show/hide the data type
    private String varPathSeparator;
    private String typeNameSeparator;
    private boolean excludeDataTypes;

    // Row filter, used to show/hide unavailable associations
    private RowFilter<TableModel, Object> rowFilter;

    /**********************************************************************************************
     * Script handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddScriptHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        eventLog = ccddMain.getSessionEventLog();

        // Get the available script engines
        scriptFactories = new ScriptEngineManager().getEngineFactories();

        scriptDialog = null;
    }

    /**********************************************************************************************
     * Set the references to the table type and data type handler classes
     *********************************************************************************************/
    protected void setHandlers()
    {
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        variableHandler = ccddMain.getVariableHandler();
    }

    /**********************************************************************************************
     * Set the reference to the active script manager or executive dialog. This should be null when
     * the script manager or executive isn't open
     *
     * @param scriptDialog
     *            reference to the active script manager or executive dialog
     *********************************************************************************************/
    protected void setScriptDialog(CcddFrameHandler scriptDialog)
    {
        this.scriptDialog = scriptDialog;
    }

    /**********************************************************************************************
     * Retrieve the script associations stored in the database
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing the script associations
     *********************************************************************************************/
    protected List<String[]> getScriptAssociations(Component parent)
    {
        // Read the stored script associations from the database
        List<String[]> associations = dbTable.retrieveInformationTable(InternalTable.ASSOCIATIONS,
                                                                       false,
                                                                       parent);

        return associations;
    }

    /**********************************************************************************************
     * Get a script association based on the association name
     *
     * @param assnName
     *            script association name
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Array containing the script association having the same name; null if no association
     *         has the specified name
     *********************************************************************************************/
    protected String[] getScriptAssociationByName(String assnName, Component parent)
    {
        String[] association = null;

        // Step through each association stored in the project database
        for (String[] assn : getScriptAssociations(parent))
        {
            // Check if the association's name matches the one supplied
            if (assnName.equals(assn[AssociationsColumn.NAME.ordinal()]))
            {
                // Store the association array and stop searching
                association = assn;
                break;
            }
        }

        return association;
    }

    /**********************************************************************************************
     * Retrieve the script associations stored in the database and from these build the array for
     * display and selection of the script associations
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Object array containing the script associations
     *********************************************************************************************/
    private Object[][] getScriptAssociationTableData(Component parent)
    {
        List<Object[]> associationsData = new ArrayList<Object[]>();

        // Read the stored script associations from the database
        List<String[]> committedAssociations = getScriptAssociations(parent);

        // Get the list of table names and their associated table type
        ArrayListMultiple protoNamesAndTableTypes = new ArrayListMultiple();
        protoNamesAndTableTypes.addAll(dbTable.queryTableAndTypeList(parent));

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, parent);

        // Create a list to contain the variables (dataType.variableName) that have been verified
        // to exist. This reduces the number of database transactions in the event the same
        // variable is used in multiple associations
        List<String> verifiedVars = new ArrayList<String>();

        // Step through each script association
        for (String[] assn : committedAssociations)
        {
            AvailabilityType availableStatus = AvailabilityType.AVAILABLE;

            try
            {
                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler,
                                                                   false,
                                                                   parent);

                // Step through each table referenced in this association
                for (String tablePath : tablePaths)
                {
                    // Check if the table hasn't already been verified to exist. Structure tables
                    // and their children are found in the structure and variable paths list.
                    // Command and other table types must be checked individually
                    if (!variableHandler.getStructureAndVariablePaths().contains(tablePath)
                        && !verifiedVars.contains(tablePath))
                    {
                        // Check if the table is a child table (which would have been found in the
                        // structure and variable paths list) or if it doesn't exist in the project
                        // database
                        if (tablePath.contains(",") || !dbTable.isTableExists(tablePath, parent))
                        {
                            throw new CCDDException();
                        }

                        // Add the table to the list of those verified
                        verifiedVars.add(tablePath);
                    }
                }
            }
            catch (CCDDException ce)
            {
                // The script file or associated table doesn't exist; set the flag to indicate the
                // association isn't available
                availableStatus = AvailabilityType.TABLE_MISSING;
            }

            // Add the association to the script associations list
            associationsData.add(new Object[] {assn[AssociationsColumn.NAME.ordinal()],
                                               assn[AssociationsColumn.DESCRIPTION.ordinal()],
                                               assn[AssociationsColumn.SCRIPT_FILE.ordinal()],
                                               CcddUtilities.highlightDataType(assn[AssociationsColumn.MEMBERS.ordinal()]),
                                               availableStatus});
        }

        return associationsData.toArray(new Object[0][0]);

    }

    /**********************************************************************************************
     * Get a reference to the script associations table
     *
     * @return Reference to the script associations table
     *********************************************************************************************/
    protected CcddJTableHandler getAssociationsTable()
    {
        return assnsTable;
    }

    /**********************************************************************************************
     * Create the panel containing the script associations table
     *
     * @param title
     *            text to display above the script associations table; null or blank if no text is
     *            to be displayed
     *
     * @param allowSelectDisabled
     *            true if disabled associations can be selected; false if not. In the script
     *            manager disabled associations are selectable so that these can be deleted if
     *            desired. Scripts that are selected and disabled are ignored when executing
     *            scripts
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Reference to the JPanel containing the script associations table
     *********************************************************************************************/
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

        // Create the check box for hiding/showing the rows with unavailable associations (due to a
        // missing script or table). This must be created before the row filter is created, which
        // in turn must be created before the associations table is created
        hideUnavailableAssns = new JCheckBox("Hide unavailable script associations", false);
        hideUnavailableAssns.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        hideUnavailableAssns.setBorder(BorderFactory.createEmptyBorder());
        hideUnavailableAssns.setToolTipText(CcddUtilities.wrapText("Remove associations that cannot be executed (due to a "
                                                                   + "missing script or table) from the associations table",
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for check box selection changes
        hideUnavailableAssns.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change in the hide unavailable associations check box state
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Issue a table change event so the unavailable association rows are shown/hidden
                ((UndoableTableModel) assnsTable.getModel()).fireTableDataChanged();
                ((UndoableTableModel) assnsTable.getModel()).fireTableStructureChanged();
            }
        });

        // Create a row filter for displaying the unavailable associations
        rowFilter = new RowFilter<TableModel, Object>()
        {
            /**************************************************************************************
             * Determine if the row should be displayed
             *************************************************************************************/
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Object> entry)
            {
                // Hide the row if it represents an unavailable association member and the hide
                // check box is selected; otherwise display the row
                return !(hideUnavailableAssns.isSelected()
                         && !isAssociationAvailable((Integer) entry.getIdentifier()));
            }
        };

        // Create the table to display the search results
        assnsTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow multiple line display in all columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /**************************************************************************************
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column)
            {
                return column == AssociationsTableColumnInfo.MEMBERS.ordinal();
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == AssociationsTableColumnInfo.AVAILABLE.ordinal();
            }

            /**************************************************************************************
             * Allow editing the description in the script manager's associations table
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return (column == convertColumnIndexToModel(AssociationsTableColumnInfo.NAME.ordinal())
                        || column == convertColumnIndexToModel(AssociationsTableColumnInfo.DESCRIPTION.ordinal()))
                       && allowSelectDisabled;
            }

            /**************************************************************************************
             * Validate changes to the editable cells
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param row
             *            table model row number
             *
             * @param column
             *            table model column number
             *
             * @param oldValue
             *            original cell contents
             *
             * @param newValue
             *            new cell contents
             *
             * @param showMessage
             *            true to display the invalid input dialog, if applicable
             *
             * @param isMultiple
             *            true if this is one of multiple cells to be entered and checked; false if
             *            only a single input is being entered
             *
             * @return Always returns false
             ************************************************************************************/
            @Override
            protected Boolean validateCellContent(List<Object[]> tableData,
                                                  int row,
                                                  int column,
                                                  Object oldValue,
                                                  Object newValue,
                                                  Boolean showMessage,
                                                  boolean isMultiple)
            {
                // Reset the flag that indicates the last edited cell's content is invalid
                setLastCellValid(true);

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the association name has been changed and if the name isn't
                        // blank
                        if (column == AssociationsTableColumnInfo.NAME.ordinal())
                        {
                            // Check if the association name does not match the alphanumeric input
                            // type
                            if (!newValueS.matches(DefaultInputType.ALPHANUMERIC.getInputMatch()))
                            {
                                throw new CCDDException("Illegal character(s) in association name");
                            }

                            // Compare this association name to the others in the table in order to
                            // avoid creating a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being edited, and if the
                                // association name matches the one being added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(tableData.get(otherRow)[column].toString()))
                                {
                                    throw new CCDDException("Association name already in use");
                                }
                            }
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(parent,
                                                                  "<html><b>"
                                                                          + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /**************************************************************************************
             * Load the script associations data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                int totalWidth = setUpdatableCharacteristics(getScriptAssociationTableData(parent),
                                                             AssociationsTableColumnInfo.getColumnNames(),
                                                             null,
                                                             AssociationsTableColumnInfo.getToolTips(),
                                                             true,
                                                             true,
                                                             true);

                // Check if the script manager or executive is active
                if (scriptDialog != null)
                {
                    // Set the script manager or executive width to the associations table width
                    scriptDialog.setTableWidth(totalWidth
                                               + LAF_SCROLL_BAR_WIDTH
                                               + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2);
                }
            }

            /**************************************************************************************
             * Alter the association table cell color or contents
             *
             * @param component
             *            reference to the table cell renderer component
             *
             * @param value
             *            cell value
             *
             * @param isSelected
             *            true if the cell is to be rendered with the selection highlighted
             *
             * @param int
             *            row cell row, view coordinates
             *
             * @param column
             *            cell column, view coordinates
             *************************************************************************************/
            @Override
            protected void doSpecialRendering(Component component,
                                              String text,
                                              boolean isSelected,
                                              int row,
                                              int column)
            {
                // Check if the association on the specified row is flagged as unavailable
                if (!isAssociationAvailable(convertRowIndexToModel(row)))
                {
                    // Check if the text is HTML-formatted
                    if (text.startsWith("<html>"))
                    {
                        // Remove any font color tags from the text. This is needed so that all of
                        // the text is grayed out
                        ((JTextComponent) component).setText(text.replaceAll("<font color[^>]*>",
                                                                             ""));
                    }

                    // Set the text color for this row to indicate it's not available
                    ((JTextComponent) component).setForeground(Color.GRAY);

                    // Check if selection of disabled associations isn't allowed
                    if (!allowSelectDisabled)
                    {
                        // Set the background color to indicate the row isn't selectable
                        ((JTextComponent) component).setBackground(ModifiableColorInfo.TABLE_BACK.getColor());
                    }
                }

                // Check if this is the script file column and the script file path should not be
                // displayed
                if (column == convertColumnIndexToView(AssociationsTableColumnInfo.SCRIPT_FILE.ordinal())
                    && hideScriptFilePath.isSelected())
                {
                    // Remove the path, leaving only the script file name
                    ((JTextComponent) component).setText(((JTextComponent) component).getText().replaceFirst(".*"
                                                                                                             + Pattern.quote(File.separator),
                                                                                                             ""));
                }
            }

            /**************************************************************************************
             * Override the method that sets the row sorter so that special sorting can be
             * performed on the script file column
             *************************************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Get a reference to the sorter
                @SuppressWarnings("unchecked")
                TableRowSorter<UndoableTableModel> sorter = (TableRowSorter<UndoableTableModel>) getRowSorter();

                // Check if the sorter exists. The sorter doesn't exist (is null) if there are no
                // rows in the table
                if (sorter != null)
                {
                    // Check if the row filter hasn't been set and that there is an unavailable
                    // association row filter
                    if (sorter.getRowFilter() == null && rowFilter != null)
                    {
                        // Apply the row filter that shows/hides the array members
                        sorter.setRowFilter(rowFilter);
                    }

                    // Add a sort comparator for the script file column
                    sorter.setComparator(AssociationsTableColumnInfo.SCRIPT_FILE.ordinal(), new Comparator<String>()
                    {
                        /**************************************************************************
                         * Override the comparison when sorting the script file column to ignore
                         * the script file paths if these are currently hidden
                         *************************************************************************/
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

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Check if the reference to the script manager is set (i.e., the script
                // associations manager dialog is open)
                if (scriptDialog != null && scriptDialog instanceof CcddScriptManagerDialog)
                {
                    // Update the script associations manager change indicator
                    ((CcddScriptManagerDialog) scriptDialog).updateChangeIndicator();
                }
            }
        };

        // Set the list selection model in order to detect table rows that aren't allowed to be
        // selected
        assnsTable.setSelectionModel(new DefaultListSelectionModel()
        {
            /**************************************************************************************
             * Check if the script association table item is selected, ignoring associations that
             * are flagged as unavailable
             *************************************************************************************/
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

        // Create the check box for hiding/showing the file paths in the associations table script
        // file column
        hideScriptFilePath = new JCheckBox("Hide script file path",
                                           ccddMain.getProgPrefs().getBoolean(HIDE_SCRIPT_PATH,
                                                                              false));
        hideScriptFilePath.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        hideScriptFilePath.setBorder(BorderFactory.createEmptyBorder());
        hideScriptFilePath.setToolTipText(CcddUtilities.wrapText("Remove the file paths from the script file column",
                                                                 ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for check box selection changes
        hideScriptFilePath.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change in the hide script file path check box state
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                assnsTable.repaint();
                ccddMain.getProgPrefs().putBoolean(HIDE_SCRIPT_PATH,
                                                   hideScriptFilePath.isSelected());
            }
        });

        gbc.weighty = 0.0;
        gbc.gridy++;
        assnsPnl.add(hideScriptFilePath, gbc);

        // Add the check box for showing/hiding unavailable associations
        gbc.gridy++;
        assnsPnl.add(hideUnavailableAssns, gbc);

        // Create a panel to contain the environment variable override label and field
        JPanel envVarOverridePnl = new JPanel(new GridBagLayout());
        JLabel envVarOverrideLbl = new JLabel("Environment variable override");
        envVarOverrideLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.weightx = 0.0;
        gbc.gridy++;
        envVarOverridePnl.add(envVarOverrideLbl, gbc);
        envVarOverrideFld = new JTextField(ModifiableOtherSettingInfo.ENV_VAR_OVERRIDE.getValue());
        envVarOverrideFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        envVarOverrideFld.setEditable(true);
        envVarOverrideFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        envVarOverrideFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        envVarOverrideFld.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                       Color.LIGHT_GRAY,
                                                                                                       Color.GRAY),
                                                                       BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                       ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                       ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                       ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));
        // Add a listener for focus changes on the environment variable override field
        envVarOverrideFld.addFocusListener(new FocusAdapter()
        {
            /**************************************************************************************
             * Handle a loss of focus
             *************************************************************************************/
            @Override
            public void focusLost(FocusEvent fe)
            {
                // Update the environment variable map and association availability
                getEnvironmentVariableMap(parent);
            }
        });

        gbc.insets.right = 0;
        gbc.weightx = 1.0;
        gbc.gridx++;
        envVarOverridePnl.add(envVarOverrideFld, gbc);
        gbc.gridx = 0;
        assnsPnl.add(envVarOverridePnl, gbc);

        // Initialize the environment variable map
        getEnvironmentVariableMap(parent);

        return assnsPnl;
    }

    /**********************************************************************************************
     * Check if the script association on the specified row in the associations table is available.
     * An association is unavailable if the script or tables is not present
     *
     * @param row
     *            table row (model coordinates)
     *
     * @return true if the script association on the specified row is available
     *********************************************************************************************/
    private boolean isAssociationAvailable(int row)
    {
        return assnsTable.getModel().getValueAt(row,
                                                AssociationsTableColumnInfo.AVAILABLE.ordinal()) == AvailabilityType.AVAILABLE;
    }

    /**********************************************************************************************
     * Get the list of a script association's member table paths. If a group is referenced then its
     * member tables are included. This method is also used to parse a string of table names when
     * exporting via the command line export command
     *
     * @param associationMembers
     *            association members as a single string (as stored in the database)
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param isForExport
     *            true when using this method to build the list of tables to export via the command
     *            line export command. This flag should be set to false when using this method for
     *            obtaining the tables for script associations
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List containing the tables (path+name) from the association member string
     *********************************************************************************************/
    protected List<String> getAssociationTablePaths(String associationMembers,
                                                    CcddGroupHandler groupHandler,
                                                    boolean isForExport,
                                                    Component parent)
    {
        List<String> tablePaths = new ArrayList<String>();
        CcddTableTreeHandler tableTree = null;

        // Check if the table list is to be used when exporting
        if (isForExport)
        {
            // Build a table tree with all tables
            tableTree = new CcddTableTreeHandler(ccddMain, TableTreeType.TABLES, parent);
        }

        // Separate the individual table path+names
        String[] members = associationMembers.split(Pattern.quote(ASSN_TABLE_SEPARATOR));

        // Step through each table path+name or group
        for (String member : members)
        {
            // Check if this is a reference to a group
            if (member.startsWith(GROUP_DATA_FIELD_IDENT))
            {
                // Extract the group name
                String groupName = member.substring(GROUP_DATA_FIELD_IDENT.length());

                // Check if this is the pseudo-group containing all tables
                if (groupName.equals(ALL_TABLES_GROUP_NODE_NAME))
                {
                    // Check if the table tree hasn't been created
                    if (tableTree == null)
                    {
                        // Build a table tree with all tables
                        tableTree = new CcddTableTreeHandler(ccddMain,
                                                             TableTreeType.TABLES,
                                                             parent);
                    }

                    // Get a list containing all prototype table names
                    tablePaths = tableTree.getTablesWithoutChildren(tableTree.getNodeByNodeName(DEFAULT_PROTOTYPE_NODE_NAME));

                    // Check if the table list is to be used when exporting
                    if (isForExport)
                    {
                        // Add the children of the root tables to the list
                        tablePaths = tableTree.getTablesWithChildren(tablePaths);
                    }
                }
                // This is a user-defined group
                else
                {
                    // Extract the group name and use it to get the group's information reference
                    GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

                    // Check if the group exists
                    if (groupInfo != null)
                    {
                        // Add the group member table(s) to the list
                        tablePaths.addAll(groupInfo.getTablesAndAncestors());
                    }
                    // The group doesn't exist
                    else
                    {
                        // Add an invalid table so that he association is flagged as unavailable
                        tablePaths.add(" ");
                    }
                }
            }
            // Check if the table path isn't blank
            else if (!member.trim().isEmpty())
            {
                // Add the table path
                tablePaths.add(member);
            }
        }

        // Check if the table list is to be used when exporting
        if (isForExport)
        {
            // Add the ancestors of the tables to the list
            tableTree.addTableAncestors(tablePaths, false);
        }

        return tablePaths;
    }

    /**********************************************************************************************
     * Get an array of all script file extensions supported by the available script engines
     *
     * @return Array of all script file extensions supported by the available script engines
     *********************************************************************************************/
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
            /**************************************************************************************
             * Compare the descriptions of two engine extensions, ignoring case
             *************************************************************************************/
            @Override
            public int compare(FileNameExtensionFilter ext1, FileNameExtensionFilter ext2)
            {
                return ext1.getDescription().compareToIgnoreCase(ext2.getDescription());
            }
        });

        return filters.toArray(new FileNameExtensionFilter[0]);
    }

    /**********************************************************************************************
     * Get the string containing the available script engines and version numbers
     *
     * @return String containing the available script engine names and version numbers appropriate
     *         for display in the Help | About dialog; returns "none" if no scripting languages are
     *         installed
     *********************************************************************************************/
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
            engineOutput += "<br>&#160;&#160;&#160;" + engine;
        }

        // Check if no engines exist
        if (engineOutput.isEmpty())
        {
            // Set the string to indicate no engines are available
            engineOutput = "none";
        }

        return engineOutput;
    }

    /**********************************************************************************************
     * Update the environment variable map with any variables specified by the user. Update the
     * association availability based on if the script exists, using the current environment
     * variables to expand its path
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the each key has a corresponding value; false if a value is missing
     *********************************************************************************************/
    private boolean getEnvironmentVariableMap(Component parent)
    {
        boolean isValid = true;

        // Get the current system environment variable map
        envVarMap = new HashMap<String, String>(System.getenv());

        // Step through the overrides, if any
        for (String envVarDefn : envVarOverrideFld.getText().split("\\s*,\\s*"))
        {
            // Check that the definition isn't blank
            if (!envVarDefn.isEmpty())
            {
                // Split the override into a key and value
                String[] keyAndValue = CcddUtilities.splitAndRemoveQuotes(envVarDefn.trim(),
                                                                          "\\s*=\\s*",
                                                                          2,
                                                                          true);

                // Check if the key and value are present
                if (keyAndValue.length == 2)
                {
                    // Add the key and value to the map if the key doesn't already exist; otherwise
                    // replace the value for the key
                    envVarMap.put(keyAndValue[0].trim().replaceAll("^\\$\\s*", ""),
                                  keyAndValue[1].trim());
                }
                // Insufficient parameters
                else
                {
                    // Inform the user that script association execution can't continue due to an
                    // invalid input
                    new CcddDialogHandler().showMessageDialog(parent,
                                                              "<html><b>Environment variable override key '</b>"
                                                                      + keyAndValue[0]
                                                                      + "<b>' has no corresponding value",
                                                              "Invalid Input",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                    isValid = false;
                    break;
                }
            }
        }

        // Check if every key has a value
        if (isValid)
        {
            // Update the environment variable override preferences
            ModifiableOtherSettingInfo.ENV_VAR_OVERRIDE.setValue(envVarOverrideFld.getText().trim(),
                                                                 ccddMain.getProgPrefs());

            // Step through each script association
            for (int row = 0; row < assnsTable.getRowCount(); row++)
            {
                // Check if the association isn't unavailable due to a missing table
                if (assnsTable.getModel().getValueAt(row,
                                                     AssociationsTableColumnInfo.AVAILABLE.ordinal()) != AvailabilityType.TABLE_MISSING)
                {
                    // Get the reference to the association's script file
                    FileEnvVar file = new FileEnvVar(FileEnvVar.expandEnvVars(assnsTable.getValueAt(row,
                                                                                                    AssociationsTableColumnInfo.SCRIPT_FILE.ordinal())
                                                                                        .toString(),
                                                                              envVarMap));

                    // Set the availability status based on if the script file exists
                    ((UndoableTableModel) assnsTable.getModel()).setValueAt((file.exists()
                                                                                           ? AvailabilityType.AVAILABLE
                                                                                           : AvailabilityType.SCRIPT_MISSING),
                                                                            row,
                                                                            AssociationsTableColumnInfo.AVAILABLE.ordinal(),
                                                                            false);
                }
            }

            // Force the table to redraw so that a change in an association's availability status
            // is reflected
            ((UndoableTableModel) assnsTable.getModel()).fireTableDataChanged();
            ((UndoableTableModel) assnsTable.getModel()).fireTableStructureChanged();
        }

        return isValid;
    }

    /**********************************************************************************************
     * Get the index from the supplied script association list for the association with the same
     * script file name and members
     *
     * @param associations
     *            list of script associations to which to compare
     *
     * @param scriptName
     *            script file path + name
     *
     * @param tables
     *            array of tables referenced by the script association
     *
     * @param ignoreRow
     *            row to ignore when checking for an identical, existing association (as is
     *            possible when replacing an association, if no changes are made); -1 to prevent a
     *            duplicate association (as when adding an association)
     *
     * @return The index number in the supplied list where the matching association occurs; -1 if
     *         no match is found
     *********************************************************************************************/
    protected static int getMatchingAssociation(List<String[]> associations,
                                                String scriptName,
                                                String[] tables,
                                                int ignoreRow)
    {
        int matchingIndex = -1;

        // Step through the committed script associations
        for (int row = 0; row < associations.size(); row++)
        {
            // Get the association members (single string format) with any HTML tags removed
            String members = CcddUtilities.removeHTMLTags(associations.get(row)[AssociationsColumn.MEMBERS.ordinal()].toString());

            // Check if this isn't the current association being added (if applicable), and the
            // script and tables match between the two script associations
            if (row != ignoreRow
                && scriptName.equals(associations.get(row)[AssociationsColumn.SCRIPT_FILE.ordinal()].toString())
                && CcddUtilities.isArraySetsEqual(tables,
                                                  members.isEmpty()
                                                                    ? new String[] {}
                                                                    : members.split(Pattern.quote(ASSN_TABLE_SEPARATOR))))
            {
                // Store the matching row number and stop searching
                matchingIndex = row;
                break;
            }
        }

        return matchingIndex;
    }

    /**********************************************************************************************
     * Execute one or more scripts based on the script associations in the script associations list
     *
     * @param tableTree
     *            CcddTableTreeHandler reference describing the table tree
     *
     * @param dialog
     *            reference to the script dialog (manager or executive) calling this method
     *********************************************************************************************/
    protected void executeScriptAssociations(CcddTableTreeHandler tableTree,
                                             CcddFrameHandler dialog)
    {
        List<Object[]> selectedAssn;

        // Get the current associations table data
        List<Object[]> assnsData = assnsTable.getTableDataList(true);

        selectedAssn = new ArrayList<Object[]>();

        // Step through each selected row in the associations table
        for (int row : assnsTable.getSelectedRows())
        {
            // Convert the row index to model coordinates in case the table is sorted by one of the
            // columns
            row = assnsTable.convertRowIndexToModel(row);

            // Check if the association is available; i.e., that the script file and table(s) are
            // present
            if (isAssociationAvailable(row))
            {
                // Remove any HTML tags from the member column; convert HTML breaks to line feeds
                assnsData.get(row)[AssociationsTableColumnInfo.MEMBERS.ordinal()] = CcddUtilities.removeHTMLTags(assnsData.get(row)[AssociationsTableColumnInfo.MEMBERS.ordinal()].toString(),
                                                                                                                 true);

                // Add the association to the list of those to execute
                selectedAssn.add(assnsData.get(row));
            }
        }

        // Check that at least one association is to be executed
        if (selectedAssn.size() != 0)
        {
            // Execute the script script association(s)
            getDataAndExecuteScriptInBackground(tableTree, selectedAssn, dialog);
        }
    }

    /**********************************************************************************************
     * Get the table information array from the table data used by the script script
     * association(s), then execute the script(s). This command is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The script execution command, however, is disabled
     * until the this command completes execution
     *
     * @param tree
     *            table tree of the table instances (parent tables with their child tables); null
     *            if the tree should be loaded
     *
     * @param associations
     *            list of script association to execute
     *
     * @param dialog
     *            reference to the entity calling this method: the script manager or executive
     *            dialog, or to the main window (if invoked from the command line)
     *********************************************************************************************/
    protected void getDataAndExecuteScriptInBackground(final CcddTableTreeHandler tree,
                                                       final List<Object[]> associations,
                                                       final Component dialog)
    {
        // Create the script execution progress/cancellation dialog
        haltDlg = new CcddHaltDialog(true);
        haltDlg.setItemsPerStep(associations.size());

        // Create a thread to execute the script in the background
        final Thread scriptThread = new Thread(new Runnable()
        {
            /**************************************************************************************
             * Execute script association(s)
             *************************************************************************************/
            @Override
            public void run()
            {
                // Check if the script was executed via the script manager or executive dialogs
                // (and not from the command line)
                if (dialog instanceof CcddFrameHandler)
                {
                    // Disable the script manager or executive dialog's controls
                    ((CcddFrameHandler) dialog).setControlsEnabled(false);
                }

                // Execute the script association(s) and obtain the completion status(es)
                isBad = getDataAndExecuteScript(tree, associations, dialog);

                // Remove the converted variable name list(s) other than the one created using the
                // separators stored in the program preferences
                variableHandler.removeUnusedLists();

                // Check if the user didn't cancel script execution
                if (!haltDlg.isHalted())
                {
                    // Close the cancellation dialog
                    haltDlg.closeDialog();
                }
                // Script execution was canceled
                else
                {
                    ccddMain.getSessionEventLog().logEvent(EventLogMessageType.STATUS_MSG,
                                                           "Script execution terminated by user");
                }
            }
        });

        // Display the script cancellation dialog in a background thread
        CcddBackgroundCommand.executeInBackground(ccddMain, dialog, new BackgroundCommand()
        {
            /**************************************************************************************
             * Display the cancellation dialog
             *************************************************************************************/
            @SuppressWarnings("deprecation")
            @Override
            protected void execute()
            {
                // Display the script execution progress/cancellation dialog
                int option = haltDlg.initialize("Script Executing",
                                                "Script execution in progress...",
                                                "script execution",
                                                100,
                                                1,
                                                true,
                                                dialog);

                // Check if the script execution was terminated by the user and that the script is
                // still executing
                if (option == OK_BUTTON && scriptThread.isAlive())
                {
                    // Forcibly stop script execution. Note: this method is deprecated due to
                    // inherent issues that can occur when a thread is abruptly stopped. However,
                    // the stop method is the only manner in which the script can be terminated
                    // (without additional code within the script itself, which cannot be assumed
                    // since the scripts are user-generated). There shouldn't be a potential for
                    // object corruption in the application since it doesn't reference any objects
                    // created by a script
                    scriptThread.stop();

                    // Set the execution status(es) to indicate the scripts didn't complete
                    isBad = new boolean[associations.size()];
                    Arrays.fill(isBad, true);
                }
            }

            /**************************************************************************************
             * Perform cancellation dialog complete steps
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Log the result of the script execution(s)
                logScriptCompletionStatus(associations, isBad);

                // Check if the script was executed via the script manager or executive dialogs
                if (dialog instanceof CcddFrameHandler)
                {
                    // Enable the script manager or executive dialog's controls
                    ((CcddFrameHandler) dialog).setControlsEnabled(true);
                }
                // The script was executed via the command line
                else
                {
                    // Restore the script output path to what it was at program start-up (in case
                    // it was altered by a command line command)
                    ccddMain.restoreScriptOutputPath();
                }
            }
        });

        // Execute the script in a background thread
        scriptThread.start();
    }

    /**********************************************************************************************
     * Get the table information array from the table data used by the script script
     * association(s), then execute the script(s)
     *
     * @param tree
     *            table tree of the table instances (parent tables with their child tables); null
     *            if the tree should be loaded
     *
     * @param associations
     *            list of script associations to execute
     *
     * @param parent
     *            GUI component over which to center any error dialog; null if none (e.g., if
     *            called via the command line)
     *
     * @return Array containing flags that indicate, for each association, if the association did
     *         not complete successfully
     *********************************************************************************************/
    protected boolean[] getDataAndExecuteScript(CcddTableTreeHandler tree,
                                                List<Object[]> associations,
                                                Component parent)
    {
        int assnIndex = 0;
        int step = 0;
        CcddTableTreeHandler tableTree = tree;

        // Create an array to indicate if an association has a problem that prevents its execution
        boolean[] isBad = new boolean[associations.size()];

        // Get the variable path separators and the show/hide data type flag from the program
        // preferences
        varPathSeparator = ccddMain.getProgPrefs().get(VARIABLE_PATH_SEPARATOR,
                                                       DEFAULT_VARIABLE_PATH_SEP);
        typeNameSeparator = ccddMain.getProgPrefs().get(TYPE_NAME_SEPARATOR,
                                                        DEFAULT_TYPE_NAME_SEP);
        excludeDataTypes = Boolean.parseBoolean(ccddMain.getProgPrefs().get(HIDE_DATA_TYPE,
                                                                            DEFAULT_HIDE_DATA_TYPE));

        // Check if the script execution was initiated via command line command (and not from the
        // script manager or executive dialog)
        if (!(parent instanceof CcddFrameHandler))
        {
            // Get the system environment variables map
            envVarMap = new HashMap<String, String>(System.getenv());
        }

        // Check if no table tree was provided
        if (tableTree == null)
        {
            // Build the table tree
            tableTree = new CcddTableTreeHandler(ccddMain, TableTreeType.INSTANCE_TABLES, parent);
        }

        // Create storage for the individual tables' data and table path+names
        List<TableInformation> tableInformation = new ArrayList<TableInformation>();
        loadedTablePaths = new ArrayList<String>();

        // Get the link assignment information, if any
        CcddLinkHandler linkHandler = new CcddLinkHandler(ccddMain, parent);

        // Load the group information from the database
        CcddGroupHandler groupHandler = new CcddGroupHandler(ccddMain, null, parent);

        // Get the list of the table paths in the order of appearance in the table tree. This
        // is used to sort the association table paths
        final List<String> allTablePaths = tableTree.getTableTreePathList(null);

        // To reduce database access and speed script execution when executing multiple
        // associations, first load all of the associated tables, making sure each is loaded only
        // once. Step through each script association definition
        for (Object[] assn : associations)
        {
            // Check if script execution is canceled
            if (haltDlg != null && haltDlg.isHalted())
            {
                break;
            }

            try
            {
                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler,
                                                                   false,
                                                                   parent);
                // Check if at least one table is assigned to this script association
                if (!tablePaths.isEmpty())
                {
                    // Sort the table paths. Sorting the tables based on their position in the
                    // table tree ensures that a child table's data is read as part of a parent (if
                    // the parent is in the association), and not separately from the parent
                    Collections.sort(tablePaths, new Comparator<String>()
                    {
                        /**************************************************************************
                         * Sort the table paths so that the root tables are in alphabetical order
                         * and the child tables appear in the order defined by their table type
                         * definition
                         *************************************************************************/
                        @Override
                        public int compare(String path1, String path2)
                        {
                            int result = 0;

                            // Get the indices of the two paths within the table tree
                            int index1 = allTablePaths.indexOf(path1);
                            int index2 = allTablePaths.indexOf(path2);

                            // Compare the indices and set the result so that they are sorted with
                            // the lowest index first
                            if (index1 > index2)
                            {
                                result = 1;
                            }
                            else if (index2 > index1)
                            {
                                result = -1;
                            }

                            return result;
                        }
                    });

                    // Step through each table path+name
                    for (String tablePath : tablePaths)
                    {
                        // Initialize the array for each of the tables to load from the database
                        combinedData = new Object[0][0];

                        // Read the table and child table data from the database and store the
                        // results from the last table loaded. This builds the combined data with
                        // the data from the table and all of its child tables
                        TableInformation tableInfo = readTable(tablePath, parent);

                        // Check if the table hasn't already been loaded
                        if (tableInfo != null)
                        {
                            // Store the table and child table information
                            tableInformation.add(tableInfo);

                            // Check if an error occurred loading the table data
                            if (tableInfo.isErrorFlag())
                            {
                                throw new CCDDException("Table '"
                                                        + tableInfo.getProtoVariableName()
                                                        + "' (or one of its children) failed to load");
                            }
                            // The table loaded successfully
                            else
                            {
                                // Store the data for the table and its child table(s)
                                tableInfo.setData(combinedData);

                                // Get the type definition based on the table type name
                                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                                // Check if the type exists
                                if (typeDefn != null)
                                {
                                    // All structure table types are combined and are referenced by
                                    // the type name "Structure", and all command table types are
                                    // combined and are referenced by the type name "Command". The
                                    // table type is converted to the generic type ("Structure" or
                                    // "Command") if the specified type is a representative of the
                                    // generic type. The original type name is preserved in each
                                    // row of the table's data in an appended column

                                    // Check if this table represents a structure
                                    if (typeDefn.isStructure())
                                    {
                                        // Set the table type to indicate a structure
                                        tableInfo.setType(TYPE_STRUCTURE);
                                    }
                                    // Check if this table represents a command table
                                    else if (typeDefn.isCommand())
                                    {
                                        // Set the table type to indicate a command table
                                        tableInfo.setType(TYPE_COMMAND);
                                    }
                                }
                                // The table's type is invalid
                                else
                                {
                                    throw new CCDDException("Table '"
                                                            + tableInfo.getProtoVariableName()
                                                            + "' has unknown type '"
                                                            + tableInfo.getType()
                                                            + "'");
                                }
                            }
                        }
                    }
                }
            }
            catch (CCDDException ce)
            {
                // Inform the user that script execution failed
                logScriptError(FileEnvVar.expandEnvVars(assn[AssociationsColumn.SCRIPT_FILE.ordinal()].toString(),
                                                        envVarMap),
                               assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                               ce.getMessage(),
                               parent);

                // Set the flag for this association indicating it can't be executed
                isBad[assnIndex] = true;
            }
            catch (Exception e)
            {
                // Display a dialog providing details on the unanticipated error
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }

            assnIndex++;
        }

        assnIndex = 0;

        // Once all table information is loaded then gather the data for each association and
        // execute it. Step through each script association definition
        for (Object[] assn : associations)
        {
            // Check if script execution is canceled
            if (haltDlg != null && haltDlg.isHalted())
            {
                break;
            }

            // Check that an error didn't occur loading the data for this association
            if (!isBad[assnIndex])
            {
                TableInformation[] combinedTableInfo = null;
                List<String> groupNames = new ArrayList<String>();

                // Get the list of association table paths
                List<String> tablePaths = getAssociationTablePaths(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   groupHandler,
                                                                   false,
                                                                   parent);

                String[] members = assn[AssociationsColumn.MEMBERS.ordinal()].toString().split(Pattern.quote(ASSN_TABLE_SEPARATOR));

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

                // Check if at least one table is assigned to this script association
                if (!tablePaths.isEmpty())
                {
                    // Create storage for the table types used by this script association
                    List<String> tableTypes = new ArrayList<String>();

                    // Create a list of the table types referenced by this association. This is
                    // used to create the storage for the combined tables. Step through each table
                    // information instance
                    for (TableInformation tableInfo : tableInformation)
                    {
                        // Check if this table is a member of the association
                        if (tablePaths.contains(tableInfo.getTablePath()))
                        {
                            // Check if the type for this table is not already in the list
                            if (!tableTypes.contains(tableInfo.getType()))
                            {
                                // Add the table type to the list
                                tableTypes.add(tableInfo.getType());
                            }
                        }
                    }

                    // Create storage for the combined table data
                    combinedTableInfo = new TableInformation[tableTypes.size()];

                    // Gather the table data, by table type, for each associated table. Step
                    // through each table type represented in this association
                    for (int typeIndex = 0; typeIndex < tableTypes.size(); typeIndex++)
                    {
                        String tableName = "";
                        Object[][] allTableData = new Object[0][0];

                        // Step through each associated table. This combines the table data for a
                        // given table type in the order that the table appears in the association
                        for (String tablePath : tablePaths)
                        {
                            // Step through each table information instance
                            for (TableInformation tableInfo : tableInformation)
                            {
                                // Check if the path for the table described by the table
                                // information matches the path of the associated table
                                if (tablePath.equals(tableInfo.getTablePath()))
                                {
                                    // Check if the table types match
                                    if (tableTypes.get(typeIndex).equals(tableInfo.getType()))
                                    {
                                        // Check if the name hasn't been stored
                                        if (tableName.isEmpty())
                                        {
                                            // Assign the name of the first table of this type as
                                            // this type's table name
                                            tableName = tablePath;
                                        }

                                        // Append the table data to the combined data array
                                        allTableData = CcddUtilities.concatenateArrays(allTableData,
                                                                                       tableInfo.getData());
                                    }

                                    // Stop searching the table information list since since the
                                    // matching table's information was found
                                    break;
                                }
                            }
                        }

                        // Create the table information from the table data obtained from the
                        // database
                        combinedTableInfo[typeIndex] = new TableInformation(tableTypes.get(typeIndex),
                                                                            tableName,
                                                                            allTableData,
                                                                            null,
                                                                            null,
                                                                            new ArrayList<FieldInformation>(0));
                    }
                }
                // No table is assigned to this script association
                else
                {
                    // Create a table information class in order to load and parse the data fields,
                    // and to allow access to the field methods
                    combinedTableInfo = new TableInformation[1];
                    combinedTableInfo[0] = new TableInformation("",
                                                                "",
                                                                new String[0][0],
                                                                null,
                                                                null,
                                                                new ArrayList<FieldInformation>(0));
                }

                // Get the script file name with any environment variables expanded
                String scriptFileName = FileEnvVar.expandEnvVars(assn[AssociationsColumn.SCRIPT_FILE.ordinal()].toString(),
                                                                 envVarMap);

                try
                {
                    // Check if the cancellation dialog is displayed
                    if (haltDlg != null)
                    {
                        // Update the progress bar. Display the association name in the progress
                        // bar; if the name is blank then display the script (with full path)
                        haltDlg.updateProgressBar((!assn[AssociationsColumn.NAME.ordinal()].toString().isEmpty()
                                                                                                                 ? assn[AssociationsColumn.NAME.ordinal()].toString()
                                                                                                                 : scriptFileName),
                                                  haltDlg.getNumDivisionPerStep() * step);
                        step++;
                    }

                    // Execute the script using the indicated table data
                    executeScript(scriptFileName,
                                  combinedTableInfo,
                                  groupNames,
                                  linkHandler,
                                  groupHandler,
                                  parent);
                }
                catch (CCDDException ce)
                {
                    // Inform the user that script execution failed
                    logScriptError(scriptFileName,
                                   assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                   ce.getMessage(),
                                   parent);

                    // Set the flag for this association indicating it can't be executed
                    isBad[assnIndex] = true;
                }
                catch (Exception e)
                {
                    // Check if script execution wasn't canceled by the user (halting a running
                    // script can generate errors; these 'explained' errors are ignored)
                    if (haltDlg == null || !haltDlg.isHalted())
                    {
                        // Display a dialog providing details on the unanticipated error
                        CcddUtilities.displayException(e, ccddMain.getMainFrame());
                    }
                }
            }

            assnIndex++;
        }

        return isBad;
    }

    /**********************************************************************************************
     * Convert the supplied string containing the association members from the internal table to
     * the viewable format, or vice versa, depending on the input flag
     *
     * @param assnMembers
     *            tables and/or groups associated with the script, in either the internal or
     *            viewable format
     *
     * @param toInternal
     *            true to convert the member string to the internal format; false to convert to the
     *            viewable format
     *
     * @return Supplied string containing the association members converted from the internal table
     *         to the viewable format, or vice versa
     *********************************************************************************************/
    protected static String convertAssociationMembersFormat(String assnMembers, boolean toInternal)
    {
        return assnMembers.replaceAll((toInternal
                                                  ? "\\s*"
                                                    + Pattern.quote(ASSN_TABLE_SEPARATOR_CMD_LN)
                                                    + "\\s*"
                                                  : Pattern.quote(ASSN_TABLE_SEPARATOR)),
                                      (toInternal
                                                  ? ASSN_TABLE_SEPARATOR
                                                  : " " + ASSN_TABLE_SEPARATOR_CMD_LN + " "))
                          .trim();
    }

    /**********************************************************************************************
     * Log the result of the script association execution(s)
     *
     * @param associations
     *            list of script association executed
     *
     * @param isBad
     *            Array containing flags that indicate, for each association, if the association
     *            did not complete successfully
     *********************************************************************************************/
    protected void logScriptCompletionStatus(List<Object[]> associations, boolean[] isBad)
    {
        int assnIndex = 0;

        // Initialize the success/fail flags and log messages
        boolean isSuccess = false;
        boolean isFail = false;
        String successMessage = "Following script(s) completed execution: '";
        String failMessage = "Following script(s) failed to execute: '";

        // Step through each script association
        for (Object[] assn : associations)
        {
            // Get the association (script name and members) in the viewable format
            String association = assn[AssociationsColumn.SCRIPT_FILE.ordinal()]
                                 + " : "
                                 + convertAssociationMembersFormat(assn[AssociationsColumn.MEMBERS.ordinal()].toString(),
                                                                   false)
                                 + ";";

            // Check if the script executed successfully
            if (isBad != null && !isBad[assnIndex])
            {
                // Append the association to the success message
                successMessage += association;
                isSuccess = true;
            }
            // The script failed to execute
            else
            {
                // Append the association to the fail message
                failMessage += association;
                isFail = true;
            }

            assnIndex++;
        }

        // Remove the trailing commas
        successMessage = CcddUtilities.removeTrailer(successMessage, ";") + "'";
        failMessage = CcddUtilities.removeTrailer(failMessage, ";" + "'");

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

    /**********************************************************************************************
     * Log a script execution error
     *
     * @param scriptFileName
     *            script file name
     *
     * @param members
     *            tables and/or groups associated with the script, in the internal format
     *
     * @param cause
     *            cause of the execution error
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void logScriptError(String scriptFileName,
                                String members,
                                String cause,
                                Component parent)
    {
        // Convert the member list from the internal to the viewable format
        members = convertAssociationMembersFormat(members, false);

        // Inform the user that the script can't be executed
        eventLog.logFailEvent(parent,
                              "Script Error",
                              "Cannot execute script '"
                                              + scriptFileName
                                              + "' using table(s) '"
                                              + members
                                              + "'; cause '"
                                              + cause.replaceAll("\\n", " ")
                                              + "'",
                              "<html><b>Cannot execute script '</b>"
                                                     + scriptFileName
                                                     + "<b>' using table(s) '</b>"
                                                     + members
                                                     + "<b>'");
    }

    /**********************************************************************************************
     * Create a script engine for the supplied script file name and table information. Non-static
     * and static script data access handlers are bound to the engine so that the public access
     * methods can be utilized
     *
     * @param scriptFileName
     *            script file name. The file extension is used to determine the script engine and
     *            therefore must conform to standard extension usage
     *
     * @param tableInformation
     *            array of table information
     *
     * @param groupNames
     *            list containing the names of any groups referenced in the script association
     *
     * @param linkHandler
     *            link handler reference
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Reference to the script engine; null if an error occurs
     *
     * @throws CCDDException
     *             If an error occurs while attempting to access the script file
     *********************************************************************************************/
    protected ScriptEngine getScriptEngine(String scriptFileName,
                                           TableInformation[] tableInformation,
                                           CcddLinkHandler linkHandler,
                                           CcddGroupHandler groupHandler,
                                           List<String> groupNames,
                                           Component parent) throws CCDDException
    {
        ScriptEngine scriptEngine = null;

        // Create the script file
        FileEnvVar scriptFile = new FileEnvVar(scriptFileName);

        // Check if the script file doesn't exist
        if (!scriptFile.isFile())
        {
            // Inform the user that the selected file is missing
            throw new CCDDException("Cannot locate script file '" + scriptFileName + "'");
        }

        // Check if the script file can't be read
        if (!scriptFile.canRead())
        {
            // Inform the user that the selected file can't be read
            throw new CCDDException("Cannot read script file '" + scriptFileName + "'");
        }

        // Get the location of the file extension indicator
        int extensionStart = scriptFileName.lastIndexOf(".");

        // Check if the file name has no extension (i.e., "fileName.___")
        if (!(extensionStart > 0 && extensionStart != scriptFileName.length() - 1))
        {
            // Inform the user that the selected file is missing the file extension
            throw new CCDDException("Script file '" + scriptFileName + "' has no file extension");
        }

        // Extract the file extension from the file name
        String extension = scriptFileName.substring(extensionStart + 1);

        // Flag that indicates if a script engine is found that matches the script file extension
        boolean isValidExt = false;

        // Step through each engine factory
        for (ScriptEngineFactory factory : scriptFactories)
        {
            // Check if this script engine is applicable to the script file's extension
            if (factory.getExtensions().contains(extension))
            {
                // Set the flag that indicates a script engine is found that matches the extension
                isValidExt = true;

                // Get the script engine
                scriptEngine = factory.getScriptEngine();

                // Create an instance of the script data access handler, then use this as a
                // reference for the version of the access handler class that contains static
                // method calls to the non-static version. Some scripting languages work with
                // either the non-static or static version (Python, Groovy), but others only work
                // with the non-static (JavaScript, Ruby) or static version (Scala) (this can be
                // Java version dependent as well).
                CcddScriptDataAccessHandler accessHandler = new CcddScriptDataAccessHandler(ccddMain,
                                                                                            scriptEngine,
                                                                                            tableInformation,
                                                                                            linkHandler,
                                                                                            groupHandler,
                                                                                            scriptFileName,
                                                                                            groupNames,
                                                                                            parent);
                CcddScriptDataAccessHandlerStatic staticHandler = new CcddScriptDataAccessHandlerStatic(accessHandler);

                // Bind the script data access handlers (non-static and static versions) to the
                // script context so that the handlers' public access methods can be accessed by
                // the script using the binding names ('ccdd' or 'ccdds')
                Bindings scriptBindings = scriptEngine.createBindings();
                scriptBindings.put("ccdd", accessHandler);
                scriptBindings.put("ccdds", staticHandler);
                scriptEngine.setBindings(scriptBindings, ScriptContext.ENGINE_SCOPE);

                // Stop searching since a match was found
                break;
            }
        }

        // Check if the file extension doesn't match one supported by any of the available script
        // engines
        if (!isValidExt)
        {
            // Inform the user that the selected file's extension isn't recognized
            throw new CCDDException("Script file '"
                                    + scriptFileName
                                    + "' extension is unsupported");
        }

        return scriptEngine;
    }

    /**********************************************************************************************
     * Execute a script
     *
     * @param scriptFileName
     *            script file name. The file extension is used to determine the script engine and
     *            therefore must conform to standard extension usage
     *
     * @param tableInformation
     *            array of table information
     *
     * @param groupNames
     *            list containing the names of any groups referenced in the script association
     *
     * @param linkHandler
     *            link handler reference
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If an error occurs while attempting to access the script file
     *********************************************************************************************/
    private void executeScript(String scriptFileName,
                               TableInformation[] tableInformation,
                               List<String> groupNames,
                               CcddLinkHandler linkHandler,
                               CcddGroupHandler groupHandler,
                               Component parent) throws CCDDException
    {
        // Get the script engine for the supplied script file name and table information
        ScriptEngine scriptEngine = getScriptEngine(scriptFileName,
                                                    tableInformation,
                                                    linkHandler,
                                                    groupHandler,
                                                    groupNames,
                                                    parent);

        try
        {
            // Execute the script
            scriptEngine.eval(new FileReader(scriptFileName));
        }
        catch (Exception e)
        {
            // Inform the user that the script encountered an error
            throw new CCDDException("Script file '"
                                    + scriptFileName
                                    + "' error '"
                                    + e.getMessage()
                                    + "'");
        }
    }

    /**********************************************************************************************
     * Recursive method to load a table, and all the tables referenced within it and its child
     * tables. The data is combined into a single array
     *
     * @param tablePath
     *            table path
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return A reference to the TableInformation for the parent table; null if the table has
     *         already been loaded. The error flag for the table data handler is set if an error
     *         occurred loading the data
     *********************************************************************************************/
    private TableInformation readTable(String tablePath, Component parent)
    {
        TableInformation tableInfo = null;

        // Check if the table is not already stored in the list
        if (!loadedTablePaths.contains(tablePath))
        {
            // Add the table path to the list so that it is not reloaded
            loadedTablePaths.add(tablePath);

            // Read the table's data from the database
            tableInfo = dbTable.loadTableData(tablePath, false, false, parent);

            // Check that the data was successfully loaded from the database and that the table
            // isn't empty
            if (!tableInfo.isErrorFlag() && tableInfo.getData().length != 0)
            {
                boolean isStructure = false;
                int variableNameColumn = -1;
                int dataTypeColumn = -1;
                int arraySizeColumn = -1;
                int variablePathColumn = -1;

                // Get the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                // Check if the table represents a structure
                if (typeDefn.isStructure())
                {
                    isStructure = true;

                    // Get the variable name, data type, array size, and path column indices
                    variableNameColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
                    dataTypeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
                    arraySizeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
                    variablePathColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE_PATH);
                }

                // Get the data and place it in an array for reference below. Add columns to
                // contain the table type and path
                String[][] data = CcddUtilities.appendArrayColumns(tableInfo.getData(), 2);
                int typeColumn = data[0].length - TYPE_COLUMN_DELTA;
                int pathColumn = data[0].length - PATH_COLUMN_DELTA;

                // Step through each row
                for (int row = 0; row < data.length && !tableInfo.isErrorFlag(); row++)
                {
                    // Use the index column to store the table path and type for reference during
                    // script execution
                    data[row][typeColumn] = tableInfo.getType();
                    data[row][pathColumn] = tablePath;

                    // Check if the table represents a structure, contains a variable path column,
                    // and that the variable name and data type aren't blank
                    if (isStructure
                        && variablePathColumn != -1
                        && !data[row][variableNameColumn].isEmpty()
                        && !data[row][dataTypeColumn].isEmpty())
                    {
                        // Get the variable path and store it in the table data. The variable path
                        // isn't stored in the database, but instead is constructed on-the-fly. The
                        // path separators used are those currently stored in the program
                        // preferences
                        data[row][variablePathColumn] = variableHandler.getVariablePath(tableInfo.getTablePath(),
                                                                                        data[row][variableNameColumn],
                                                                                        data[row][dataTypeColumn],
                                                                                        varPathSeparator,
                                                                                        excludeDataTypes,
                                                                                        typeNameSeparator,
                                                                                        true);

                    }

                    // Store the data from the table in the combined storage array
                    combinedData = CcddUtilities.concatenateArrays(combinedData,
                                                                   new Object[][] {data[row]});

                    // Check if this is a structure table reference
                    if (isStructure && !dataTypeHandler.isPrimitive(data[row][dataTypeColumn]))
                    {
                        // Check if the data type or variable name isn't blank, and if an array
                        // size column doesn't exist or that the row doesn't reference an array
                        // definition. This is necessary to prevent appending the prototype
                        // information for this data type structure
                        if ((!data[row][dataTypeColumn].isEmpty()
                             || !data[row][variableNameColumn].isEmpty())
                            && (data[row][arraySizeColumn].isEmpty()
                                || ArrayVariable.isArrayMember(data[row][variableNameColumn])))
                        {
                            // Get the variable in the format dataType.variableName, prepend a
                            // comma to separate the new variable from the preceding variable path,
                            // then break down the child table
                            TableInformation childInfo = readTable(tablePath
                                                                   + ","
                                                                   + data[row][dataTypeColumn]
                                                                   + "."
                                                                   + data[row][variableNameColumn],
                                                                   parent);

                            // Check if an error occurred loading the child table
                            if (childInfo != null && childInfo.isErrorFlag())
                            {
                                // Set the error flag and stop processing this table
                                tableInfo.setErrorFlag();
                                break;
                            }
                        }
                    }
                }
            }
        }

        return tableInfo;
    }
}
