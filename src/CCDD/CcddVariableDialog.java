/**
 * CFS Command and Data Dictionary variable paths & names dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DEFAULT_HIDE_DATA_TYPE;
import static CCDD.CcddConstants.DEFAULT_TYPE_NAME_SEP;
import static CCDD.CcddConstants.DEFAULT_VARIABLE_PATH_SEP;
import static CCDD.CcddConstants.HIDE_DATA_TYPE;
import static CCDD.CcddConstants.SAVE_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;
import static CCDD.CcddConstants.TABLE_ICON;
import static CCDD.CcddConstants.TYPE_NAME_SEPARATOR;
import static CCDD.CcddConstants.VARIABLE_PATH_SEPARATOR;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddConstants.VariablePathTableColumnInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary variable paths and names dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddVariableDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddJTableHandler variableTable;
    private final CcddVariableHandler variableHandler;
    private CcddTableTreeHandler tableTree;

    // Components referenced from multiple methods
    private AutoCompleteTextField filterFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JTextField varPathSepFld;
    private JTextField typeNameSepFld;
    private JCheckBox hideDataTypeCb;
    private JLabel numVariablesLbl;

    // Variables table data
    private Object[][] tableData;

    // Pattern for matching filter text in the table cells
    private Pattern filterPattern;

    // Wild card filter character explanation label
    private static String WILD_CARD_LABEL = "? = character, * = string, \\ for literal ? or *";

    /**********************************************************************************************
     * Variable paths and names dialog class constructor
     *
     * @param ccddMain Main class reference
     *********************************************************************************************/
    CcddVariableDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        variableHandler = ccddMain.getVariableHandler();

        // Create the variable paths & names dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the variable paths and names dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the variable paths & names dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create a panel to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnUpdate;

            /**************************************************************************************
             * Build the variable paths and names dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create borders for the dialog components
                Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                   Color.LIGHT_GRAY,
                                                                                                   Color.GRAY),
                                                                   BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                   ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                   ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                   ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
                Border emptyBorder = BorderFactory.createEmptyBorder();

                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                0.0,
                                                                0.0,
                                                                GridBagConstraints.FIRST_LINE_START,
                                                                GridBagConstraints.NONE,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           0,
                                                                           ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           0),
                                                                0,
                                                                0);

                // Create panels to hold the components of the dialog
                JPanel upperPnl = new JPanel(new GridBagLayout());
                JPanel inputPnl = new JPanel(new GridBagLayout());
                dialogPnl.setBorder(emptyBorder);
                upperPnl.setBorder(emptyBorder);
                inputPnl.setBorder(emptyBorder);

                // Create the filter label
                JLabel filterLbl = new JLabel("Enter filter text");
                filterLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                inputPnl.add(filterLbl, gbc);

                // Create the auto-completion filter field and add it to the dialog panel. The search list
                // is initially empty as it is updated whenever a key is pressed
                filterFld = new AutoCompleteTextField(ModifiableSizeInfo.NUM_REMEMBERED_SEARCHES.getSize());
                filterFld.setCaseSensitive(true);
                filterFld.setText("");
                filterFld.setColumns(20);
                filterFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                filterFld.setEditable(true);
                filterFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                filterFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                filterFld.setBorder(border);

                // Add a listener for key press events
                filterFld.addKeyListener(new KeyAdapter()
                {
                    /**************************************************************************************
                     * Handle a key press event
                     *************************************************************************************/
                    @Override
                    public void keyPressed(KeyEvent ke)
                    {
                        // Check if this is a visible character
                        if (!ke.isActionKey()
                            && ke.getKeyCode() != KeyEvent.VK_ENTER
                            && !ke.isControlDown()
                            && !ke.isAltDown()
                            && !ke.isMetaDown()
                            && ModifiableFontInfo.INPUT_TEXT.getFont().canDisplay(ke.getKeyCode()))
                        {
                            // Get the list of remembered searches from the program preferences. This is
                            // done as a key press occurs so that the list is updated to the latest one. If
                            // multiple find/replace dialogs are open this allows them to 'share' the list
                            // rather than overwriting each other
                            List<String> searches = new ArrayList<String>(ModifiableSizeInfo.NUM_REMEMBERED_SEARCHES.getSize());
                            searches.addAll(Arrays.asList(ccddMain.getProgPrefs().get(SEARCH_STRINGS, "").split(STRING_LIST_TEXT_SEPARATOR)));
                            filterFld.setList(searches);
                        }
                    }
                });

                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.bottom = 0;
                gbc.gridy++;
                inputPnl.add(filterFld, gbc);

                // Add the wild card character explanation label
                final JLabel wildCardLbl = new JLabel(WILD_CARD_LABEL);
                wildCardLbl.setFont(ModifiableFontInfo.LABEL_ITALIC.getFont());
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
                gbc.insets.top = 0;
                gbc.gridy++;
                inputPnl.add(wildCardLbl, gbc);

                // Create a check box for ignoring the text case
                ignoreCaseCb = new JCheckBox("Ignore text case");
                ignoreCaseCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                ignoreCaseCb.setBorder(BorderFactory.createEmptyBorder());
                ignoreCaseCb.setToolTipText(CcddUtilities.wrapText("Ignore case when matching the filter string",
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

                // Add a listener for ignore case check box selection changes
                ignoreCaseCb.addActionListener(new ActionListener()
                {
                    /**************************************************************************************
                     * Handle a change in the ignore case check box state
                     *************************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Change the case sensitivity for the remembered searches to match the case
                        // sensitivity check box
                        filterFld.setCaseSensitive(!ignoreCaseCb.isSelected());
                    }
                });

                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.gridy++;
                inputPnl.add(ignoreCaseCb, gbc);

                // Create a check box to allow a regular expression in the filter string
                allowRegexCb = new JCheckBox("Allow regular expression");
                allowRegexCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                allowRegexCb.setBorder(BorderFactory.createEmptyBorder());
                allowRegexCb.setToolTipText(CcddUtilities.wrapText("Allow the filter string to contain a regular expression",
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

                // Add a listener for allow regular expression check box selection changes
                allowRegexCb.addActionListener(new ActionListener()
                {
                    /**************************************************************************************
                     * Handle a change in the allow regular expression check box state
                     *************************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Hide the wild card label if the allow regular expression check box is enabled
                        wildCardLbl.setText(allowRegexCb.isSelected() ? " " : WILD_CARD_LABEL);
                    }
                });

                gbc.gridy++;
                inputPnl.add(allowRegexCb, gbc);

                // Create the variable path separator label and input field, and add them to the
                // dialog panel
                JLabel varPathSepLbl = new JLabel("<html>Enter variable path<br>&#160;separator character(s)");
                varPathSepLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.insets.left = 0;
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.gridy++;
                inputPnl.add(varPathSepLbl, gbc);
                varPathSepFld = new JTextField(ccddMain.getProgPrefs().get(VARIABLE_PATH_SEPARATOR,
                                                                           DEFAULT_VARIABLE_PATH_SEP),
                                               5);
                varPathSepFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                varPathSepFld.setEditable(true);
                varPathSepFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                varPathSepFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                varPathSepFld.setBorder(border);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.gridy++;
                inputPnl.add(varPathSepFld, gbc);

                // Create the data type/variable name separator label and input field, and add them
                // to the dialog panel
                final JLabel typeNameSepLbl = new JLabel("<html>Enter data type/variable name<br>&#160;separator character(s)");
                typeNameSepLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.insets.left = 0;
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.gridy++;
                inputPnl.add(typeNameSepLbl, gbc);
                typeNameSepFld = new JTextField(ccddMain.getProgPrefs().get(TYPE_NAME_SEPARATOR, DEFAULT_TYPE_NAME_SEP),
                                                5);
                typeNameSepFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                typeNameSepFld.setEditable(true);
                typeNameSepFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                typeNameSepFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                typeNameSepFld.setBorder(border);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.gridy++;
                inputPnl.add(typeNameSepFld, gbc);

                // Create a check box for hiding data types
                hideDataTypeCb = new JCheckBox("Hide data types",
                                               Boolean.parseBoolean(ccddMain.getProgPrefs().get(HIDE_DATA_TYPE,
                                                                                                DEFAULT_HIDE_DATA_TYPE)));
                hideDataTypeCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                hideDataTypeCb.setBorder(emptyBorder);
                gbc.insets.left = 0;
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
                gbc.gridy++;
                inputPnl.add(hideDataTypeCb, gbc);

                // Add a listener for the hide data type check box
                hideDataTypeCb.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a change in the hide data type check box status
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Enable/disable the data type/variable name separator input label and
                        // field
                        typeNameSepLbl.setEnabled(!((JCheckBox) ae.getSource()).isSelected());
                        typeNameSepFld.setEnabled(!((JCheckBox) ae.getSource()).isSelected());

                        // Update the User Format variables
                        updateResultsAfterTableChange();
                    }
                });

                // Add the inputs panel, containing the separator characters fields and check box,
                // to the upper panel
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridy = 0;
                upperPnl.add(inputPnl, gbc);

                // Build the table tree showing both table prototypes and table instances; i.e.,
                // parent tables with their child tables (i.e., parents with children). The order
                // of the variables is preserved in the tree
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                        new CcddGroupHandler(ccddMain,
                                                                             null,
                                                                             ccddMain.getMainFrame()),
                                                        TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES,
                                                        ccddMain.getMainFrame());

                // Add the tree to the upper panel
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.fill = GridBagConstraints.BOTH;
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.weightx = 1.0;
                gbc.weighty = 1.0;
                gbc.gridx++;
                upperPnl.add(tableTree.createTreePanel("Structures & Variables",
                                                       TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                       true,
                                                       ccddMain.getMainFrame()),
                             gbc);
                gbc.gridwidth = 1;
                gbc.insets.right = 0;
                gbc.gridx = 0;
                gbc.fill = GridBagConstraints.BOTH;
                dialogPnl.add(upperPnl, gbc);

                // Create the variables and number of variables total labels
                JLabel variablesLbl = new JLabel("Variables");
                variablesLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                variablesLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                numVariablesLbl = new JLabel();
                numVariablesLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
                gbc.fill = GridBagConstraints.REMAINDER;

                // Add the variables labels to the dialog
                JPanel variablesPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                variablesPnl.add(variablesLbl);
                variablesPnl.add(numVariablesLbl);
                gbc.weighty = 0.0;
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.insets.bottom = 0;
                gbc.gridy++;
                dialogPnl.add(variablesPnl, gbc);

                // Define the variable paths & names JTable
                variableTable = new CcddJTableHandler()
                {
                    /******************************************************************************
                     * Allow multiple line display in all columns
                     *****************************************************************************/
                    @Override
                    protected boolean isColumnMultiLine(int column)
                    {
                        return true;
                    }

                    /**************************************************************************************
                     * Allow HTML-formatted text in the variable path column
                     *************************************************************************************/
                    @Override
                    protected boolean isColumnHTML(int column)
                    {
                        return true;
                    }

                    /**************************************************************************************
                     * Allow the variable path column to be displayed with the text highlighted
                     *************************************************************************************/
                    @Override
                    protected boolean isColumnHighlight(int column)
                    {
                        return true;
                    }

                    /******************************************************************************
                     * Load the structure table variables paths & names into the table and format
                     * the table cells
                     *****************************************************************************/
                    @Override
                    protected void loadAndFormatData()
                    {
                        // Place the data into the table model along with the column names, set up
                        // the editors and renderers for the table cells, set up the table grid
                        // lines, and calculate the minimum width required to display the table
                        // information
                        setUpdatableCharacteristics(tableData,
                                                    VariablePathTableColumnInfo.getColumnNames(),
                                                    null,
                                                    VariablePathTableColumnInfo.getToolTips(),
                                                    false,
                                                    true,
                                                    true);
                    }
                };

                // Get the project's variables
                tableData = getVariables();

                // Place the table into a scroll pane
                JScrollPane scrollPane = new JScrollPane(variableTable);

                // Set common table parameters and characteristics
                variableTable.setFixedCharacteristics(scrollPane,
                                                      false,
                                                      ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                                      TableSelectionMode.SELECT_BY_CELL,
                                                      true,
                                                      ModifiableColorInfo.TABLE_BACK.getColor(),
                                                      true,
                                                      false,
                                                      ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                                      true);

                // Define the panel to contain the table
                JPanel variablesTblPnl = new JPanel();
                variablesTblPnl.setLayout(new BoxLayout(variablesTblPnl, BoxLayout.X_AXIS));
                variablesTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                variablesTblPnl.add(scrollPane);

                // Add the table to the dialog
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = 1.0;
                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(variablesTblPnl, gbc);

                // Create a table opener for the Open tables command
                final TableOpener opener = new TableOpener()
                {
                    /******************************************************************************
                     * Clean up the table name
                     *****************************************************************************/
                    @Override
                    protected String cleanUpTableName(String tableName, int row)
                    {
                        // Remove the HTML tags from the table name
                        tableName = CcddUtilities.removeHTMLTags(tableName);

                        // Get the index of the last variable name in the path
                        int varIndex = tableName.lastIndexOf(",");

                        // Check if the path contains a variable name
                        if (varIndex != -1)
                        {
                            // Remove the last variable name from the path, leaving the table name
                            tableName = tableName.substring(0, varIndex);
                        }

                        return tableName;
                    }
                };

                // Update variables button
                btnUpdate = CcddButtonPanelHandler.createButton("Update",
                                                                SEARCH_ICON,
                                                                KeyEvent.VK_U,
                                                                "Update the list of project variables after applying any filtering");

                // Add a listener for the Update button
                btnUpdate.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Convert the variables and display the results
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Remove any excess white space
                        varPathSepFld.setText(varPathSepFld.getText().trim());
                        typeNameSepFld.setText(typeNameSepFld.getText().trim());

                        // Check if a separator field contains a character that cannot be used
                        if (varPathSepFld.getText().matches(".*[\\[\\]].*")
                            || (!hideDataTypeCb.isSelected()
                                && typeNameSepFld.getText().matches(".*[\\[\\]].*")))
                        {
                            // Inform the user that the input value is invalid
                            new CcddDialogHandler().showMessageDialog(CcddVariableDialog.this,
                                                                      "<html><b>Invalid character(s) in separator field(s)",
                                                                      "Invalid Input",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                        // The separator fields are valid
                        else
                        {
                            filterPattern = null;

                            // Check if the filter field isn't empty
                            if (!filterFld.getText().isEmpty())
                            {
                                // Create the match pattern from the filter criteria
                                filterPattern = CcddSearchHandler.createSearchPattern(filterFld.getText(),
                                                                                      ignoreCaseCb.isSelected(),
                                                                                      allowRegexCb.isSelected(),
                                                                                      CcddVariableDialog.this);
                            }

                            // Get the variables (matching the filtering tables, if applicable) and
                            // display them in the table
                            tableData = getVariables();
                            variableTable.loadAndFormatData();
                            variableTable.highlightSearchText(filterPattern);

                            // Highlight the table tree items that match the pattern
                            tableTree.setHighlightPattern(filterPattern);
                            tableTree.expandedHighlighted();
                        }
                    }
                });

                // Open table(s) button
                JButton btnOpen = CcddButtonPanelHandler.createButton("Open",
                                                                      TABLE_ICON,
                                                                      KeyEvent.VK_O,
                                                                      "Open the table(s) associated with the selected variable(s)");

                // Add a listener for the Open button
                btnOpen.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Open the selected table(s)
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        opener.openTables(variableTable, VariablePathTableColumnInfo.APP_FORMAT.ordinal());
                    }
                });

                // Save variable paths button
                JButton btnSave = CcddButtonPanelHandler.createButton("Save",
                                                                       SAVE_ICON,
                                                                       KeyEvent.VK_V,
                                                                       "Save the variable paths to a file");

                // Add a listener for the Save button
                btnSave.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Save the variables to a file
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Add the application format variables to the list
                        List<String> variables = new ArrayList<String>();
                        variables.add("Application variables:");

                        for (Object[] row : tableData)
                        {
                            variables.add(CcddUtilities.removeHTMLTags((String) row[VariablePathTableColumnInfo.APP_FORMAT.ordinal()]));
                        }

                        // Add the user-defined format variables to the list
                        variables.add("");
                        variables.add("User-defined variables:");

                        for (Object[] row : tableData)
                        {
                            variables.add((String) row[VariablePathTableColumnInfo.USER_FORMAT.ordinal()]);
                        }

                        // Allow the user to select the output file and output the variable list
                        ccddMain.getFileIOHandler().writeListToFile(variables, CcddVariableDialog.this);
                    }
                });

                // Store separators button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON, KeyEvent.VK_S,
                                                                       "Store the variable path separators and hide data types flag");
                btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Add a listener for the Store button
                btnStore.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Store the variable separators and hide data types flag
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Store the separator information in the program preferences
                        ccddMain.getProgPrefs().put(VARIABLE_PATH_SEPARATOR, varPathSepFld.getText());
                        ccddMain.getProgPrefs().put(TYPE_NAME_SEPARATOR, typeNameSepFld.getText());
                        ccddMain.getProgPrefs().put(HIDE_DATA_TYPE, String.valueOf(hideDataTypeCb.isSelected()));

                        // Step through the open editor dialogs
                        for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                        {
                            // Step through each individual editor
                            for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                            {
                                // Update the variable path column, if present
                                editor.updateVariablePaths();
                            }
                        }
                    }
                });

                // Close variables dialog button
                JButton btnCancel = CcddButtonPanelHandler.createButton("Close",
                                                                        CLOSE_ICON,
                                                                        KeyEvent.VK_C,
                                                                        "Close the variables dialog");

                // Add a listener for the Close button
                btnCancel.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Close the variables dialog
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Remove the converted variable name list(s) other than the one created
                        // using the separators stored in the program preferences
                        variableHandler.removeUnusedLists();

                        // Close the dialog
                        closeDialog(CANCEL_BUTTON);
                    }
                });

                // Add the buttons to the dialog's button panel
                buttonPnl.setBorder(emptyBorder);
                buttonPnl.add(btnUpdate);
                buttonPnl.add(btnOpen);
                buttonPnl.add(btnSave);
                buttonPnl.add(btnStore);
                buttonPnl.add(btnCancel);
            }

            /**************************************************************************************
             * Variable paths & names dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the show variable dialog
                createDialog(ccddMain.getMainFrame(),
                             dialogPnl,
                             buttonPnl,
                             btnUpdate,
                             "Variable Paths & Names",
                             null,
                             null,
                             true,
                             false);

            }
        });
    }

    /**********************************************************************************************
     * Get the array of variables. If the table tree has any selections use these to filter the
     * variable array
     *
     * @return Array of variables matching the filter tables, or all variables if no filter table
     *         is selected
     *********************************************************************************************/
    private Object[][] getVariables()
    {
        List<Object[]> variableList = new ArrayList<Object[]>();
        Matcher matcher = null;

        // Get the list of selected tables
        List<String> filterTables = tableTree.getSelectedTablesWithChildren();

        // Get the total number of variables
        int numVariables = variableHandler.getAllVariableNames().size();

        // Step through each variable in the project
        for (int index = 0; index < numVariables; index++)
        {
            // Get a reference to the variable name to shorten subsequent calls
            String variableName = variableHandler.getAllVariableNames().get(index);

            // Check if this variable is not an array definition
            if (!((index < numVariables - 1)
                  && variableHandler.getAllVariableNames().get(index + 1).endsWith("]")
                  && !variableName.endsWith("]")))
            {
                // Check if a filter is in place
                if (filterPattern != null)
                {
                    // Create the pattern matcher
                    matcher = filterPattern.matcher(variableName);
                }

                // Check if no filter is in place, or if one is that the variable name matches the
                // filter pattern
                if (matcher == null || matcher.find())
                {
                    // Check if no tables are selected for use as filters
                    if (filterTables.isEmpty())
                    {
                        // Add the variable to the list
                        variableList.add(new Object[] {CcddUtilities.highlightDataType(variableName),
                                                       variableHandler.getFullVariableName(variableName,
                                                                                           varPathSepFld.getText(),
                                                                                           hideDataTypeCb.isSelected(),
                                                                                           typeNameSepFld.getText())});
                    }
                    // One or more tables are selected for use as filters
                    else
                    {
                        String variablePath = variableName;

                        // Get the index of the last comma in the variable path, which defines the
                        // beginning of the variable (and the end of the variable's path)
                        int varIndex = variableName.lastIndexOf(",");

                        // Check if the variable has a path
                        if (varIndex != -1)
                        {
                            // Remove the variable from the path
                            variablePath = variablePath.substring(0, varIndex);
                        }

                        // Check if the table path is in the list of filter tables
                        if (filterTables.contains(variablePath))
                        {
                            // Add the variable to the list
                            variableList.add(new Object[] {CcddUtilities.highlightDataType(variableName),
                                                           variableHandler.getFullVariableName(variableName,
                                                                                               varPathSepFld.getText(),
                                                                                               hideDataTypeCb.isSelected(),
                                                                                               typeNameSepFld.getText())});
                        }
                    }
                }
            }
        }

        // Update the number of variables label
        numVariablesLbl.setText("  (" + variableList.size() + " total)");

        return variableList.toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Update the show variables dialog. Any tables selected as filters are cleared, but the filter
     * string is retained and the results filtered accordingly
     *********************************************************************************************/
    protected void updateResultsAfterTableChange()
    {
        tableData = getVariables();
        variableTable.loadAndFormatData();
        tableTree.buildTableTreeFromDatabase(ccddMain.getMainFrame());
    }
}
