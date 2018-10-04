/**
 * CFS Command and Data Dictionary search variable tree dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;

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
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary search variable tree dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchVariablesDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddTableTreeHandler variableTree;

    // Components referenced from multiple methods
    private AutoCompleteTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JLabel numMatchesLbl;
    // private JList<String> variableList;
    private CcddJTableHandler variableTable;

    // Pattern for matching search text in the table cells
    private Pattern searchPattern;

    // Comparison search criteria used to determine if the criteria changed
    private String prevSearchText;
    private boolean prevIgnoreCase;
    private boolean prevAllowRegex;

    // List of variable paths matching the search criteria
    private List<String[]> variablePaths;

    // Wild card search character explanation label
    private static String WILD_CARD_LABEL = "? = character, * = string, \\ for literal ? or *";

    /**********************************************************************************************
     * Search variable tree dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddSearchVariablesDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        variablePaths = new ArrayList<String[]>(0);

        // Create the variable search dialog
        initialize();
    }

    /**********************************************************************************************
     * Create and display the table variable search dialog
     *********************************************************************************************/
    private void initialize()
    {
        prevSearchText = null;

        // Create a borders for the dialog components
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
        final Border emptyBorder = BorderFactory.createEmptyBorder();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.FIRST_LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create panels to hold the components of the dialog
        JPanel inputPnl = new JPanel(new GridBagLayout());
        JPanel labelPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inputPnl.setBorder(emptyBorder);
        labelPnl.setBorder(emptyBorder);

        // Create the search dialog labels
        JLabel searchLbl = new JLabel("Enter search text");
        searchLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        labelPnl.add(searchLbl);
        numMatchesLbl = new JLabel();
        numMatchesLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        labelPnl.add(numMatchesLbl);
        inputPnl.add(labelPnl, gbc);

        // Create the auto-completion search field and add it to the dialog panel. The search list
        // is initially empty as it is updated whenever a key is pressed
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
                    searches.addAll(Arrays.asList(ccddMain.getProgPrefs().get(SEARCH_STRINGS,
                                                                              "")
                                                          .split(STRING_LIST_TEXT_SEPARATOR)));
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
        ignoreCaseCb.setBorder(emptyBorder);
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

        // Create a check box for allow a regular expression in the search string
        allowRegexCb = new JCheckBox("Allow regular expression");
        allowRegexCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        allowRegexCb.setBorder(emptyBorder);
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
                wildCardLbl.setText(allowRegexCb.isSelected()
                                                              ? " "
                                                              : WILD_CARD_LABEL);
            }
        });

        gbc.gridy++;
        inputPnl.add(allowRegexCb, gbc);

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
                                            new String[] {"Matching Variables"},
                                            null,
                                            null,
                                            true,
                                            true,
                                            true);
            }
        };

        // Place the table in a scroll pane
        JScrollPane scrollPane = new JScrollPane(variableTable);
        scrollPane.setBorder(border);

        // Set common table parameters and characteristics
        variableTable.setFixedCharacteristics(scrollPane,
                                              false,
                                              ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                              TableSelectionMode.SELECT_BY_CELL,
                                              false,
                                              ModifiableColorInfo.TABLE_BACK.getColor(),
                                              false,
                                              false,
                                              ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                              true);

        // Create a variable tree to display the matching variables
        variableTree = new CcddTableTreeHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain,
                                                                     null,
                                                                     ccddMain.getMainFrame()),
                                                TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES,
                                                true,
                                                false,
                                                ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Rebuild the variable tree, retaining only those nodes that contain a match with the
             * search criteria or are ancestors to a matching node
             *************************************************************************************/
            @Override
            protected void buildTableTree(Boolean isExpanded,
                                          String rateName,
                                          String rateFilter,
                                          Component parent)
            {
                int matchCount = 0;

                super.buildTableTree(isExpanded, null, null, parent);

                // Check if the user provided search criteria
                if (searchPattern != null)
                {
                    // Expand the nodes in the variable tree containing a match
                    List<String> variables = variableTree.pruneTreeToSearchCriteria(".*"
                                                                                    + searchPattern.toString()
                                                                                    + ".*");

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

                    // Highlight the matching text in the variable paths
                    variableTable.highlightSearchText(searchPattern);

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
                numMatchesLbl.setText(matchCount != 0
                                                      ? "  ("
                                                        + matchCount
                                                        + (matchCount == 1
                                                                           ? " match"
                                                                           : " matches")
                                                        + ")"
                                                      : "");
            }
        };

        // Clear the variable tree initially
        variableTree.removeAllNodes();

        // Create a split pane containing the variable tree in the top pane and the variable table
        // in the lower pane, and add it to the panel
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weighty = 1.0;
        gbc.gridy++;
        inputPnl.add(new CustomSplitPane(variableTree.createTreePanel("Matching Variables",
                                                                      TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                                      true,
                                                                      ccddMain.getMainFrame()),
                                         scrollPane,
                                         null,
                                         JSplitPane.VERTICAL_SPLIT),
                     gbc);

        // Search button
        JButton btnSearch = CcddButtonPanelHandler.createButton("Search",
                                                                SEARCH_ICON,
                                                                KeyEvent.VK_S,
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
                searchVariableTree();
            }
        });

        // Close variable search dialog button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
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

        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnSearch);
        buttonPnl.add(btnClose);

        // Display the variable search dialog
        createDialog(ccddMain.getMainFrame(),
                     inputPnl,
                     buttonPnl,
                     btnSearch,
                     "Variable Search",
                     null,
                     null,
                     true,
                     true);
    }

    /**********************************************************************************************
     * Search the variables for text matching the search criteria
     *********************************************************************************************/
    private void searchVariableTree()
    {
        // Check if the search criteria changed
        if (!searchFld.getText().equals(prevSearchText)
            || ignoreCaseCb.isSelected() != prevIgnoreCase
            || allowRegexCb.isSelected() != prevAllowRegex)
        {
            // Store the search criteria
            prevSearchText = searchFld.getText();
            prevIgnoreCase = ignoreCaseCb.isSelected();
            prevAllowRegex = allowRegexCb.isSelected();

            // Check if the search field is blank
            if (searchFld.getText().isEmpty())
            {
                // Inform the user that the input value is invalid
                new CcddDialogHandler().showMessageDialog(CcddSearchVariablesDialog.this,
                                                          "<html><b>Search text cannot be blank",
                                                          "Invalid Input",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
            // The search field contains text
            else
            {
                // Create the match pattern from the search criteria
                searchPattern = CcddSearchHandler.createSearchPattern(searchFld.getText(),
                                                                      ignoreCaseCb.isSelected(),
                                                                      allowRegexCb.isSelected(),
                                                                      CcddSearchVariablesDialog.this);

                // Check if the search pattern is valid
                if (searchPattern != null)
                {
                    // Set the search pattern in the variable tree so that the matching text in the
                    // nodes is highlighted
                    variableTree.setHighlightPattern(searchPattern);

                    // Rebuild the table tree, retaining only those nodes that contain a match with
                    // the search pattern or are ancestors to a matching node
                    variableTree.buildTableTree(false, null, null, CcddSearchVariablesDialog.this);
                }
            }
        }
    }
}
