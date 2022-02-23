/**************************************************************************************************
/** \file
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Dialog for the user to perform variable searches of the project database data tables.
*     The dialog is built on the CcddDialogHandler class.
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

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DEFAULT_INSTANCE_NODE_NAME;
import static CCDD.CcddConstants.DEFAULT_PROTOTYPE_NODE_NAME;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;
import static CCDD.CcddConstants.TABLE_ICON;

import java.awt.Color;
import java.awt.Component;
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
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddConstants.VariablePathTableColumnInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary search variable tree dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchVariablesDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddTableTreeHandler variableTree;
    private final CcddVariableHandler variableHandler;
    // Components referenced from multiple methods
    private AutoCompleteTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JCheckBox showAllVarCB;
    private JCheckBox searchViaTableTreeCB;
    private JLabel numMatchesLbl;
    private JPanel variablesPnl;
    private JPanel variablesTblPnl;
    private JPanel upperPnl;
    private JPanel inputPnl;
    private CcddJTableHandler variableTable;
    CustomSplitPane tableTreePane;
    GridBagConstraints tableTreeGbc;
    private JLabel numVariablesLbl;
    private Border border;

    // List of table data
    private Object[][] tableData;

    // Pattern for matching search text in the table cells
    private Pattern searchPattern;

    // List of variable paths matching the search criteria
    private List<String[]> variablePaths;

    // Wild card search character explanation label
    private static String WILD_CARD_LABEL = "? = character, * = string, \\ for literal ? or *";

    /**********************************************************************************************
     * Search variable tree dialog class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddSearchVariablesDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        variablePaths = new ArrayList<String[]>(0);
        variableHandler = ccddMain.getVariableHandler();

        // Create the variable search dialog
        initialize();
    }

    /**********************************************************************************************
     * Create and display the table variable search dialog
     *********************************************************************************************/
    private void initialize()
    {
        // Create a borders for the dialog components
        border = BorderFactory
                .createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.LIGHT_GRAY,
                                                                      Color.GRAY),
                                      BorderFactory
                                              .createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING
                                                                         .getSpacing()));

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING
                                                                .getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING
                                                                           .getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING
                                                                           .getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING
                                                                           .getSpacing()),
                                                        0, 0);

        // Create panels to hold the components of the dialog
        inputPnl = new JPanel(new GridBagLayout());
        JPanel labelPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inputPnl.setBorder(BorderFactory.createEmptyBorder());
        labelPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the search dialog labels
        JLabel searchLbl = new JLabel("Enter search text");
        searchLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        labelPnl.add(searchLbl);
        numMatchesLbl = new JLabel();
        numMatchesLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        labelPnl.add(numMatchesLbl);
        inputPnl.add(labelPnl, gbc);

        // Create the auto-completion search field and add it to the dialog panel. The
        // search list is initially empty as it is updated whenever a key is pressed
        searchFld = new AutoCompleteTextField(ModifiableSizeInfo.NUM_REMEMBERED_SEARCHES.getSize());
        searchFld.setCaseSensitive(true);
        searchFld.setText("");
        searchFld.setColumns(25);
        searchFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        searchFld.setEditable(true);
        searchFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        searchFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        searchFld.setBorder(border);

        // Add a listener for key press events
        searchFld.addKeyListener(new KeyAdapter()
        {
            /**************************************************************************************
             * Handle a key press event
             *************************************************************************************/
            @Override
            public void keyPressed(KeyEvent ke)
            {
                // Check if this is a visible character
                if (!ke.isActionKey() && ke.getKeyCode() != KeyEvent.VK_ENTER && !ke.isControlDown() && !ke.isAltDown()
                    && !ke.isMetaDown() && ModifiableFontInfo.INPUT_TEXT.getFont().canDisplay(ke.getKeyCode()))
                {
                    // Get the list of remembered searches from the program preferences. This is
                    // done as a key press occurs so that the list is updated to the latest one. If
                    // multiple find/replace dialogs are open this allows them to 'share' the list
                    // rather than overwriting each other
                    List<String> searches = new ArrayList<String>(ModifiableSizeInfo.NUM_REMEMBERED_SEARCHES.getSize());
                    searches.addAll(Arrays
                            .asList(ccddMain.getProgPrefs().get(SEARCH_STRINGS, "").split(STRING_LIST_TEXT_SEPARATOR)));
                    searchFld.setList(searches);
                }
            }
        });

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.insets.bottom = 0;
        gbc.gridy++;
        inputPnl.add(searchFld, gbc);

        // Add the wild card character explanation label
        final JLabel wildCardLbl = new JLabel(WILD_CARD_LABEL);
        wildCardLbl.setFont(ModifiableFontInfo.LABEL_ITALIC.getFont());
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3;
        gbc.insets.top = 0;
        gbc.gridy++;
        inputPnl.add(wildCardLbl, gbc);

        // Create a check box for ignoring the text case
        ignoreCaseCb = new JCheckBox("Ignore text case");
        ignoreCaseCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        ignoreCaseCb.setBorder(BorderFactory.createEmptyBorder());
        ignoreCaseCb.setToolTipText(CcddUtilities.wrapText("Ignore case when matching the search string",
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
                searchFld.setCaseSensitive(!ignoreCaseCb.isSelected());
            }
        });

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.gridy++;
        inputPnl.add(ignoreCaseCb, gbc);

        // Create a check box to allow a regular expression in the search string
        allowRegexCb = new JCheckBox("Allow regular expression");
        allowRegexCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        allowRegexCb.setBorder(BorderFactory.createEmptyBorder());
        allowRegexCb.setToolTipText(CcddUtilities.wrapText("Allow the search string to contain a regular expression",
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

        // Create a check box that allows the user to display all variables in the database
        showAllVarCB = new JCheckBox("Show all variables");
        showAllVarCB.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        showAllVarCB.setBorder(BorderFactory.createEmptyBorder());
        showAllVarCB.setToolTipText(CcddUtilities.wrapText("Show all varaibles within the database",
                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for allow regular expression check box selection changes
        showAllVarCB.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change in the allow regular expression check box state
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Disable the other check boxes along with the search field if
                // the show all variables checkbox has been selected
                allowRegexCb.setEnabled(!showAllVarCB.isSelected());
                allowRegexCb.setSelected(showAllVarCB.isSelected());
                ignoreCaseCb.setEnabled(!showAllVarCB.isSelected());
                searchLbl.setEnabled(!showAllVarCB.isSelected());
                searchFld.setEnabled(!showAllVarCB.isSelected());
                searchViaTableTreeCB.setEnabled(!showAllVarCB.isSelected());

                // If the show all variables check-box was just clicked than search the variable tree
                if (showAllVarCB.isSelected())
                {
                    searchVariableTree();
                }
            }
        });

        gbc.gridy++;
        inputPnl.add(showAllVarCB, gbc);

        // Create a check box that allows the user to search variables via the table tree
        searchViaTableTreeCB = new JCheckBox("Search via table tree");
        searchViaTableTreeCB.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        searchViaTableTreeCB.setBorder(BorderFactory.createEmptyBorder());
        searchViaTableTreeCB
                .setToolTipText(CcddUtilities.wrapText("Select tables from the table tree to show their variables",
                                                       ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for allow regular expression check box selection changes
        searchViaTableTreeCB.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change in the allow regular expression check box state
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Disable the other check boxes along with the search field if the
                // search via table tree checkbox has been selected
                allowRegexCb.setEnabled(!searchViaTableTreeCB.isSelected());
                allowRegexCb.setSelected(searchViaTableTreeCB.isSelected());
                ignoreCaseCb.setEnabled(!searchViaTableTreeCB.isSelected());
                searchLbl.setEnabled(!searchViaTableTreeCB.isSelected());
                searchFld.setEnabled(!searchViaTableTreeCB.isSelected());
                showAllVarCB.setEnabled(!searchViaTableTreeCB.isSelected());

                // If the search via table tree check box was just selected than build the table tree so that all
                // tables
                // are available for selection. If not build the table tree so that only the tables that are
                // identifed by
                // the search term will be displayed
                if (searchViaTableTreeCB.isSelected())
                {
                    buildTableTreeForSelection();
                }
                else
                {
                    buildTableTreeForSearch(true, tableTreeGbc);
                }
            }
        });

        gbc.gridy++;
        inputPnl.add(searchViaTableTreeCB, gbc);

        // Create a variable tree to display the matching variables
        buildTableTreeForSearch(false, gbc);

        // Search button
        JButton btnSearch = CcddButtonPanelHandler.createButton("Search", SEARCH_ICON, KeyEvent.VK_S,
                                                                "Search the variable tree for matches");

        // Add a listener for the Search button
        btnSearch.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Highlight the search text in the tree
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // If the search via table tree check box is not selected than search the variable tree
                // for the search term. If it is selected than search the selected tables within the table
                // tree for all variables
                if (!searchViaTableTreeCB.isSelected())
                {
                    searchVariableTree();
                }
                else
                {
                    // Get the variables (matching the filtering tables, if applicable) and
                    // display them in the table
                    tableData = getVariables();
                    variableTable.loadAndFormatData();
                }
            }
        });

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

        // Open table(s) button
        JButton btnOpen = CcddButtonPanelHandler
                .createButton("Open", TABLE_ICON, KeyEvent.VK_O,
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

        // Close variable search dialog button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close", CLOSE_ICON, KeyEvent.VK_C,
                                                               "Close the variable search dialog");

        // Add a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the variable search dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                closeDialog();
            }
        });

        // Create the button panel that will contain the search, open and close buttons
        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnSearch);
        buttonPnl.add(btnOpen);
        buttonPnl.add(btnClose);

        // Display the variable search dialog
        createDialog(ccddMain.getMainFrame(), inputPnl, buttonPnl, btnSearch, "Variable Search", null, null, true,
                     true);
    }

    /**********************************************************************************************
     * Search the variables for text matching the search criteria
     *********************************************************************************************/
    private void searchVariableTree()
    {
        String searchFieldTxt = "";

        // If the show all variables button has been selected than use a regular expression to search the
        // variable tree for all variables. If it is not selected than search the variable tree using the
        // search term located in the search fld
        if (showAllVarCB.isSelected())
        {
            searchFieldTxt = ".*";
        }
        else
        {
            searchFieldTxt = searchFld.getText();
        }

        // Check if the search field is blank
        if (searchFieldTxt.isEmpty())
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddSearchVariablesDialog.this,
                                                      "<html><b>Search text cannot be blank", "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
        }
        // The search field contains text
        else
        {
            // Create the match pattern from the search criteria
            searchPattern = CcddSearchHandler.createSearchPattern(searchFieldTxt, ignoreCaseCb.isSelected(),
                                                                  allowRegexCb.isSelected(),
                                                                  CcddSearchVariablesDialog.this);

            // Check if the search pattern is valid
            if (searchPattern != null)
            {
                // Set the search pattern in the variable tree so that the matching text in the
                // nodes is highlighted unless we are searching for all varaibles
                if (!showAllVarCB.isSelected())
                {
                    variableTree.setHighlightPattern(searchPattern);
                }

                // Rebuild the table tree, retaining only those nodes that contain a match with
                // the search pattern or are ancestors to a matching node
                variableTree.buildTableTree(false, null, null, false, CcddSearchVariablesDialog.this);
            }
        }
    }

    // This function will build the table tree in a manner that allows the user to use the search field
    // to search for
    // variables. When the user selects the "Search" button after typing in a search term only the
    // tables that contain
    // the term will be displayed
    private void buildTableTreeForSearch(boolean delete, GridBagConstraints gbc)
    {
        // If needed, delete any panels from the dialog that are going to be replaced
        if (delete)
        {
            inputPnl.remove(variablesTblPnl);
            inputPnl.remove(variablesPnl);
            inputPnl.remove(upperPnl);
        }

        // Create a borders for the dialog components
        Border border = BorderFactory
                .createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, Color.LIGHT_GRAY,
                                                                      Color.GRAY),
                                      BorderFactory
                                              .createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING
                                                                         .getSpacing()));

        // Create a table to display the matching variables
        variableTable = new CcddJTableHandler(5)
        {
            /**************************************************************************************
             * Allow multiple line display in the variable path column
             *************************************************************************************/
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

            /**************************************************************************************
             * Load the variable path data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column name, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(variablePaths.toArray(new String[0][0]),
                                            new String[] {"Matching Variables"}, null, null, true, true, true);
            }
        };

        // Place the table in a scroll pane
        JScrollPane scrollPane = new JScrollPane(variableTable);
        scrollPane.setBorder(border);

        // Set common table parameters and characteristics
        variableTable.setFixedCharacteristics(scrollPane, false, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                              TableSelectionMode.SELECT_BY_CELL, false,
                                              ModifiableColorInfo.TABLE_BACK.getColor(), false, false,
                                              ModifiableFontInfo.OTHER_TABLE_CELL.getFont(), true);

        // Create a variable tree to display the matching variables
        variableTree = new CcddTableTreeHandler(ccddMain, new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame()),
                                                TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES, true, false, false,
                                                ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Rebuild the variable tree, retaining only those nodes that contain a match with the search
             * criteria or are ancestors to a matching node
             *************************************************************************************/
            @Override
            protected void buildTableTree(Boolean isExpanded, String rateName, String rateFilter,
                                          boolean isByGroupChanged, Component parent)
            {
                int matchCount = 0;

                super.buildTableTree(isExpanded, null, null, false, parent);

                // Check if the user provided search criteria
                if (searchPattern != null)
                {
                    // Expand the nodes in the variable tree containing a match
                    List<String> variables = variableTree
                            .pruneTreeToSearchCriteria(".*" + searchPattern.toString() + ".*");

                    // Create storage for the variable table data array
                    variablePaths = new ArrayList<String[]>(variables.size());

                    // Store the number of matches detected
                    matchCount = variables.size();

                    // Step through each matching variable
                    for (String variable : variables)
                    {
                        // Highlight the variable's data types and place it in an array (for use in
                        // the variable table)
                        variablePaths.add(new String[] {CcddUtilities.highlightDataType(variable)});
                    }

                    // Load the variable table with the matching variables
                    variableTable.loadAndFormatData();

                    // Highlight the matching text in the variable paths unless we are searching
                    // for all variables
                    if (!showAllVarCB.isSelected())
                    {
                        variableTable.highlightSearchText(searchPattern);
                    }

                    // Update the search string list
                    searchFld.updateList(searchFld.getText());

                    // Store the search list in the program preferences
                    ccddMain.getProgPrefs().put(SEARCH_STRINGS, searchFld.getListAsString());
                }
                // No search criteria are provided; reset the search dialog
                else
                {
                    // Clear the variable tree and variable path table
                    removeAllNodes();
                    variablePaths = new ArrayList<String[]>();
                    variableTable.loadAndFormatData();
                }

                // Update the number of matches found label
                numMatchesLbl
                        .setText(matchCount != 0 ? "  (" + matchCount + (matchCount == 1 ? " match" : " matches") + ")"
                                                 : "");
            }
        };

        // Clear the variable tree initially
        variableTree.removeAllNodes();

        // If we are not deleting any components then tableTreeGbc will need to be set. This indicates
        // where the table tree will always be placed when repainting the dialog
        if (!delete)
        {
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.bottom = 0;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.weighty = 1.0;
            gbc.gridy++;
            tableTreeGbc = gbc;
        }

        // Create a tree panel using the variable tree and place it in a custom split pane before adding it
        // to the inputPnl
        tableTreePane = new CustomSplitPane(variableTree
                .createTreePanel("Table Tree", TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, true,
                                 ccddMain.getMainFrame()), scrollPane, null, JSplitPane.VERTICAL_SPLIT);

        inputPnl.add(tableTreePane, tableTreeGbc);
        inputPnl.revalidate();
        inputPnl.repaint();
    }

    // This function will build the table tree in a manner that allows the user to select tables from
    // the table tree. Once
    // the user has selected the desired tables they may select the "Search" button and all variables
    // within the selected
    // tables will be displayed
    public void buildTableTreeForSelection()
    {
        GridBagConstraints gbc = tableTreeGbc;
        // Create panels to hold the components of the dialog
        upperPnl = new JPanel(new GridBagLayout());
        upperPnl.setBorder(BorderFactory.createEmptyBorder());

        // Remove the table tree pane as it is going to be replaced
        inputPnl.remove(tableTreePane);

        // Build the table tree showing both table prototypes and table instances; i.e.,
        // parent tables with their child tables (i.e., parents with children)
        variableTree = new CcddTableTreeHandler(ccddMain, new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame()),
                                                TableTreeType.STRUCTURE_TABLES, DEFAULT_PROTOTYPE_NODE_NAME,
                                                DEFAULT_INSTANCE_NODE_NAME, ccddMain.getMainFrame());

        // Add the tree to the upper panel
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridx++;

        // Create a tree panel using the varaible tree and add it to the upperPnl
        upperPnl.add(variableTree.createTreePanel("Structure Tables", TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                  false, ccddMain.getMainFrame()),
                     gbc);
        gbc.gridwidth = 1;
        gbc.insets.right = 0;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        inputPnl.add(upperPnl, gbc);

        gbc = tableTreeGbc;

        // Create the variables and number of variables total labels
        JLabel variablesLbl = new JLabel("Variables");
        variablesLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        variablesLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        numVariablesLbl = new JLabel();
        numVariablesLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        gbc.fill = GridBagConstraints.REMAINDER;

        // Add the variables labels to the dialog
        variablesPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        variablesPnl.add(variablesLbl);
        variablesPnl.add(numVariablesLbl);
        gbc.weighty = 0.0;
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        gbc.gridy++;
        inputPnl.add(variablesPnl, gbc);

        // Define the variable paths & names JTable
        variableTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
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
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column)
            {
                return column == 0;
            }

            /******************************************************************************
             * Load the structure table variables paths & names into the table and format the table cells
             *****************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up
                // the editors and renderers for the table cells, set up the table grid
                // lines, and calculate the minimum width required to display the table
                // information
                setUpdatableCharacteristics(tableData, VariablePathTableColumnInfo.getColumnNames(), null,
                                            VariablePathTableColumnInfo.getToolTips(), false, true, true);
            }
        };

        // Get the project's variables
        tableData = getVariables();

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(variableTable);

        // Set common table parameters and characteristics
        variableTable.setFixedCharacteristics(scrollPane, false, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                              TableSelectionMode.SELECT_BY_CELL, true,
                                              ModifiableColorInfo.TABLE_BACK.getColor(), true, false,
                                              ModifiableFontInfo.DATA_TABLE_CELL.getFont(), true);

        // Define the panel to contain the table
        variablesTblPnl = new JPanel();
        variablesTblPnl.setLayout(new BoxLayout(variablesTblPnl, BoxLayout.X_AXIS));
        variablesTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        variablesTblPnl.add(scrollPane);

        // Add the table to the dialog
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(variablesTblPnl, gbc);

        inputPnl.revalidate();
        inputPnl.repaint();
    }

    /**********************************************************************************************
     * Get the array of variables. If the table tree has any selections use these to filter the variable
     * array
     *
     * @return Array of variables matching the filter tables, or all variables if no filter table is
     *         selected
     *********************************************************************************************/
    private Object[][] getVariables()
    {
        List<Object[]> variableList = new ArrayList<Object[]>();

        // Get the list of selected tables
        List<String> filterTables = variableTree.getSelectedTablesWithChildren();

        // Step through each variable in the project
        for (String variableName : variableHandler.getAllVariableNames())
        {
            // Check if no tables are selected for use as filters
            if (filterTables.isEmpty())
            {
                // Add the variable to the list
                variableList.add(new Object[] {CcddUtilities.highlightDataType(variableName),
                                               variableHandler.getFullVariableName(variableName, "_", false, "_")});
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
                                                   variableHandler.getFullVariableName(variableName, "_", false, "_")});
                }
            }
        }

        // Update the number of variables label
        numVariablesLbl.setText("  (" + variableList.size() + " total)");

        return variableList.toArray(new Object[0][0]);
    }
}
