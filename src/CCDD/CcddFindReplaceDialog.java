/**
 * CFS Command and Data Dictionary find/replace text in a data or table type table dialog.
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.REPLACE_ALL_ICON;
import static CCDD.CcddConstants.REPLACE_FIND_ICON;
import static CCDD.CcddConstants.REPLACE_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.SEARCH_PREVIOUS_ICON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary find/replace text in a data or table type table dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddFindReplaceDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddFrameHandler editorDialog;
    private CcddJTableHandler table;

    // Components referenced from multiple methods
    private AutoCompleteTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JLabel numMatchesLbl;
    private JButton btnReplace;
    private JButton btnReplaceFind;
    private JButton btnReplaceAll;
    private JTextField replaceFld;

    // Pattern for matching search text in the table cells
    private Pattern searchPattern;

    // Comparison search criteria used to determine if the criteria changed
    private String prevSearchText;
    private boolean prevIgnoreCase;
    private boolean prevAllowRegex;

    // Flag that indicates if text was replaced in a table cell
    private boolean isReplaced;

    // Listener for table editor dialog focus events; used in conjunction with the find/replace
    // dialog
    private WindowFocusListener editorListener = null;

    // Wild card search character explanation label
    private static String WILD_CARD_LABEL = "? = character, * = string, \\ for literal ? or *";

    /**********************************************************************************************
     * Find/replace text in a data or table type table dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param editorDialog
     *            reference to the table or table type editor dialog
     *
     * @param table
     *            reference to the data table or table type table
     *********************************************************************************************/
    CcddFindReplaceDialog(CcddMain ccddMain,
                          CcddFrameHandler editorDialog,
                          CcddJTableHandler table)
    {
        this.ccddMain = ccddMain;
        this.editorDialog = editorDialog;
        this.table = table;

        // Create the table/table type editor find/replace dialog
        initialize();
    }

    /**********************************************************************************************
     * Set the active table or table type editor in the editor dialog
     *
     * @param table
     *            reference to the data table or table type table
     *********************************************************************************************/
    protected void setActiveEditor(CcddJTableHandler table)
    {
        // Check if the find/replace dialog is open already
        if (this.table != null)
        {
            // Remove the highlighting from previous table
            table.highlightSearchText(null);
        }

        this.table = table;
        table.highlightSearchText(searchPattern);
    }

    /**********************************************************************************************
     * Enable or disable the replace buttons
     *
     * @param enable
     *            true to enable the replace buttons
     *********************************************************************************************/
    private void setReplaceEnable(boolean enable)
    {
        btnReplace.setEnabled(enable);
        btnReplaceFind.setEnabled(enable);
        btnReplaceAll.setEnabled(enable);
    }

    /**********************************************************************************************
     * Create and display the table find/replace dialog
     *********************************************************************************************/
    private void initialize()
    {
        prevSearchText = null;

        // Set the flag so that the find/replace dialog is always on top of other windows
        setAlwaysOnTop(true);

        // Create a borders for the dialog components
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

        // Create the find/replace dialog labels
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

        // Create the replace dialog labels
        JLabel replaceLbl = new JLabel("Enter replace text");
        replaceLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.gridy++;
        inputPnl.add(replaceLbl, gbc);

        // Create the replace field and add it to the dialog panel
        replaceFld = new JTextField("", 25);
        replaceFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        replaceFld.setEditable(true);
        replaceFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        replaceFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        replaceFld.setBorder(border);
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.insets.bottom = 0;
        gbc.gridy++;
        inputPnl.add(replaceFld, gbc);

        // Find forward button
        JButton btnFindNext = CcddButtonPanelHandler.createButton("Find next",
                                                                  SEARCH_ICON,
                                                                  KeyEvent.VK_F,
                                                                  "Search forwards from the current cell for a cell containing a match");

        // Add a listener for the Find next button
        btnFindNext.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Highlight the search text in the table and go to the first match after to the
             * currently selected cell
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                searchTable();
                selectNextMatchingCell(1);
            }
        });

        // Find backward button
        JButton btnFindPrevious = CcddButtonPanelHandler.createButton("Find previous",
                                                                      SEARCH_PREVIOUS_ICON,
                                                                      KeyEvent.VK_P,
                                                                      "Search backwards from the current cell for a cell containing a match");

        // Add a listener for the Find previous button
        btnFindPrevious.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Highlight the search text in the table and go to the first match prior to the
             * currently selected cell
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                searchTable();
                selectNextMatchingCell(-1);
            }
        });

        // Replace/find matching text button
        btnReplaceFind = CcddButtonPanelHandler.createButton("Replace/find",
                                                             REPLACE_FIND_ICON,
                                                             KeyEvent.VK_L,
                                                             "Replace the matching text in the currently selected cell, "
                                                                            + "then select the next cell containing a match");

        // Add a listener for the Replace/find button
        btnReplaceFind.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Replace the matching text in the selected cell then select the next cell containing
             * a match
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                replaceSelected();
                selectNextMatchingCell(1);
            }
        });

        // Replace matching text button
        btnReplace = CcddButtonPanelHandler.createButton("Replace",
                                                         REPLACE_ICON,
                                                         KeyEvent.VK_R,
                                                         "Replace the matching text in the currently selected cell");

        // Add a listener for the Replace button
        btnReplace.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Replace the matching text in the selected cell
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                replaceSelected();
            }
        });

        // Replace all matching text button
        btnReplaceAll = CcddButtonPanelHandler.createButton("Replace all",
                                                            REPLACE_ALL_ICON,
                                                            KeyEvent.VK_A,
                                                            "Replace the matching text in all table cells");

        // Add a listener for the Replace all button
        btnReplaceAll.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Search for text matching the search criteria and replace the matching text in all
             * table cells
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                replaceAll();
            }
        });

        // Disable the previous and next buttons until a search is performed
        setReplaceEnable(false);

        // Close find/replace dialog button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the find/replace dialog");

        // Add a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the find/replace dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Remove the search text highlighting in the table
                table.highlightSearchText(null);
                table.repaint();

                // Remove the editor dialog's listener for find/replace dialog window focus
                // changes, then close the find/replace dialog
                editorDialog.removeWindowFocusListener(editorListener);
                CcddFindReplaceDialog.this.closeDialog();
                editorListener = null;
            }
        });

        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnFindNext);
        buttonPnl.add(btnFindPrevious);
        buttonPnl.add(btnReplace);
        buttonPnl.add(btnReplaceFind);
        buttonPnl.add(btnReplaceAll);
        buttonPnl.add(btnClose);
        setButtonRows(3);

        // Add a listener for find/replace dialog focus changes
        addWindowFocusListener(new WindowFocusListener()
        {
            /**************************************************************************************
             * Handle a find/replace dialog gain of focus
             *************************************************************************************/
            @Override
            public void windowGainedFocus(WindowEvent we)
            {
                // Check if the table editor dialog's window focus listener doesn't exist. This is
                // the case when the find/replace dialog is initially displayed
                if (editorListener == null)
                {
                    // Create a listener for window focus events to the table editor dialog
                    editorListener = new WindowFocusListener()
                    {
                        /**************************************************************************
                         * Handle a table editor dialog gain of focus
                         *************************************************************************/
                        @Override
                        public void windowGainedFocus(WindowEvent we)
                        {
                            // Check if the find/replace dialog is active
                            if (!CcddFindReplaceDialog.this.isShowing())
                            {
                                // Show the find/replace dialog
                                CcddFindReplaceDialog.this.setVisible(true);
                            }
                        }

                        /**************************************************************************
                         * Handle a table editor dialog loss of focus
                         *************************************************************************/
                        @Override
                        public void windowLostFocus(WindowEvent we)
                        {
                            // Check if the find/replace dialog is active
                            if (CcddFindReplaceDialog.this.isShowing())
                            {
                                // Create a runnable object to be executed
                                SwingUtilities.invokeLater(new Runnable()
                                {
                                    /**************************************************************
                                     * The change in focus to the receiver isn't immediate, so
                                     * invokeLater is used to ensure any receiver focus gain event
                                     * is completed prior to handling the focus lost event
                                     *************************************************************/
                                    @Override
                                    public void run()
                                    {
                                        // Check if the find/replace dialog doesn't have the focus
                                        if (!CcddFindReplaceDialog.this.isFocused())
                                        {
                                            // Hide the find/replace dialog
                                            CcddFindReplaceDialog.this.setVisible(false);
                                        }
                                    }
                                });
                            }
                        }
                    };

                    // Add a listener for table editor dialog focus changes
                    editorDialog.addWindowFocusListener(editorListener);
                }
            }

            /**************************************************************************************
             * Handle a find/replace dialog loss of focus
             *************************************************************************************/
            @Override
            public void windowLostFocus(WindowEvent we)
            {
                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /******************************************************************************
                     * The change in focus to the receiver isn't immediate, so invokeLater is used
                     * to ensure any receiver focus gain event is completed prior to handling the
                     * focus lost event
                     *****************************************************************************/
                    @Override
                    public void run()
                    {
                        // Check if the table editor dialog doesn't have the focus
                        if (!editorDialog.isFocused())
                        {
                            // Hide the find/replace dialog
                            CcddFindReplaceDialog.this.setVisible(false);
                        }
                    }
                });
            }
        });

        // Display the find/replace dialog
        createDialog(editorDialog,
                     inputPnl,
                     buttonPnl,
                     btnFindNext,
                     "Find/Replace",
                     null,
                     null,
                     true,
                     false);

        // Except for its initial appearance, don't set the focus to the find/replace dialog each
        // time it reappears due to the table editor dialog regaining focus
        setAutoRequestFocus(false);
    }

    /**********************************************************************************************
     * Search the table for text matching the search criteria
     *********************************************************************************************/
    private void searchTable()
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

            // Create the match pattern from the search criteria
            searchPattern = CcddSearchHandler.createSearchPattern(searchFld.getText(),
                                                                  ignoreCaseCb.isSelected(),
                                                                  allowRegexCb.isSelected(),
                                                                  CcddFindReplaceDialog.this);

            // Highlight the matching text in the table cells
            table.highlightSearchText(searchPattern);

            // Update the number of results found label
            int matchCount = updateMatchCount();

            // Enable/disable the previous and next buttons based on if search text is present
            setReplaceEnable(matchCount != 0);

            // Update the search string list
            searchFld.updateList(searchFld.getText());

            // Store the search list in the program preferences
            ccddMain.getProgPrefs().put(SEARCH_STRINGS, searchFld.getListAsString());
        }
    }

    /**********************************************************************************************
     * Replace all text in the selected cell matching the search criteria with the replacement text
     *********************************************************************************************/
    private void replaceSelected()
    {
        // Check if a search is in effect
        if (searchPattern != null)
        {
            // Get the coordinates of the first selected cell (if any)
            int row = table.getSelectedRow();
            int column = table.getSelectedColumn();

            // Check if a cell is selected
            if (row != -1 && column != -1)
            {
                isReplaced = false;

                // Get the table data array
                List<Object[]> tableData = table.getTableDataList(false);

                // Convert the cell view coordinates to model coordinates
                row = table.convertRowIndexToModel(row);
                column = table.convertColumnIndexToModel(column);

                // Replace the matching text in the selected cell
                replaceMatchInCell(tableData, row, column, true, false);

                // Update the table data if a replacement occurred
                updateTableData(tableData);
            }
        }
    }

    /**********************************************************************************************
     * Replace all text in the table matching the search criteria with the replacement text
     *********************************************************************************************/
    private void replaceAll()
    {
        // Check if a search is in effect
        if (searchPattern != null)
        {
            Boolean isContinue = true;
            isReplaced = false;

            // Get the table data array
            List<Object[]> tableData = table.getTableDataList(false);

            // Step through each row in the table
            for (int row = 0; row < table.getModel().getRowCount() && isContinue != null; row++)
            {
                // Step through each column in the table
                for (int column = 0; column < table.getModel().getColumnCount() && isContinue != null; column++)
                {
                    // Replace all matching text in the cell with the replace text
                    isContinue = replaceMatchInCell(tableData, row, column, isContinue, true);
                }
            }

            // Update the table data if a replacement occurred
            updateTableData(tableData);
        }
    }

    /**********************************************************************************************
     * Replace all text in the specified table cell matching the search criteria with the
     * replacement text
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param row
     *            table model row index
     *
     * @param column
     *            table model column index
     *
     * @param isContinue
     *            true to display the invalid input dialog, if applicable
     *
     * @param isMultiple
     *            true if this is one of multiple cells to be entered and checked; false if only a
     *            single input is being entered
     *
     * @return true to indicate that subsequent errors should be displayed; false if subsequent
     *         errors should not be displayed; null if the replace operation should be canceled
     *********************************************************************************************/
    private Boolean replaceMatchInCell(List<Object[]> tableData,
                                       int row,
                                       int column,
                                       Boolean isContinue,
                                       boolean isMultiple)
    {
        // Check if the column is visible and alterable
        if (!table.isColumnHidden(column)
            && table.isDataAlterable(tableData.get(row), row, column))
        {
            // Get the cell value prior to any changes
            Object oldValue = table.getModel().getValueAt(row, column).toString();

            // Create the pattern matcher from the search pattern
            Matcher matcher = searchPattern.matcher(oldValue.toString());

            // Check if there is a match in the cell value
            while (matcher.find())
            {
                // Replace all matching text in the cell and validate the cell contents. If invalid
                // the cell contents is automatically reverted to its previous value, which is
                // assumed to be valid, so the flag indicating the last cell is valid is set
                Object newValue = matcher.replaceFirst(replaceFld.getText());
                table.getModel().setValueAt(newValue, row, column);
                isContinue = table.validateCellContent(tableData,
                                                       row,
                                                       column,
                                                       oldValue,
                                                       newValue,
                                                       isContinue,
                                                       isMultiple);
                table.setLastCellValid(true);
                isReplaced = true;
            }
        }

        return isContinue;
    }

    /**********************************************************************************************
     * Select the next or previous cell matching the search text beginning at the currently
     * selected cell
     *
     * @param direction
     *            +1 to search forwards, -1 to search backwards from the currently selected cell
     *********************************************************************************************/
    private void selectNextMatchingCell(int direction)
    {
        // Check if a search is in effect
        if (searchPattern != null && table.getModel().getRowCount() > 0)
        {
            // Get the coordinates of the first selected cell (if any)
            int row = table.getSelectedRow();
            int column = table.getSelectedColumn();

            // Check if a cell is selected
            if (row != -1 && column != -1)
            {
                // Convert the cell view coordinates to model coordinates
                row = table.convertRowIndexToModel(row);
                column = table.convertColumnIndexToModel(column);
            }
            // No cell is selected
            else
            {
                // Check if this is a forward search
                if (direction == 1)
                {
                    // Set the starting cell as the last cell in the table
                    row = table.getModel().getRowCount() - 1;
                    column = table.getModel().getColumnCount() - 1;
                }
                // This is a backwards search
                else
                {
                    // Set the starting cell as the first cell in the table
                    row = 0;
                    column = 0;
                }
            }

            // Store the selected cell coordinates
            int selRow = row;
            int selColumn = column;

            do
            {
                // Go to the next column
                column += direction;

                // Check if the end of the row is reached
                if (column == table.getModel().getColumnCount())
                {
                    // Go to the first column on the next row
                    row++;
                    column = 0;
                }
                // Check if the beginning of the row is reached
                else if (column < 0)
                {
                    // Go to the last column on the previous row
                    row--;
                    column = table.getModel().getColumnCount() - 1;
                }

                // Check if the end of the table is reached
                if (row == table.getModel().getRowCount())
                {
                    // Go to the first column on the first row
                    row = 0;
                    column = 0;
                }
                // Check if the beginning of the table is reached
                else if (row < 0)
                {
                    // Go to the last column on the last row
                    row = table.getModel().getRowCount() - 1;
                    column = table.getModel().getColumnCount() - 1;
                }

                // Check if the column is visible
                if (!table.isColumnHidden(column))
                {
                    // Create the pattern matcher from the search pattern
                    Matcher matcher = searchPattern.matcher(table.getModel()
                                                                 .getValueAt(row,
                                                                             column)
                                                                 .toString());

                    // Check if there is a match in the cell value
                    if (matcher.find())
                    {
                        // Convert the model coordinates of the cell containing the match to view
                        // coordinates
                        selRow = table.convertRowIndexToView(row);
                        selColumn = table.convertColumnIndexToView(column);

                        // Check if the row is hidden
                        if (selRow == -1)
                        {
                            // Show all hidden rows in the table and get the row's model coordinate
                            showAllRows();
                            selRow = table.convertRowIndexToView(row);
                        }

                        // Check if the coordinates are valid (this should always be true by this
                        // point)
                        if (selRow != -1 && selColumn != -1)
                        {
                            // Highlight the selected cell and scroll the table so that it's
                            // visible
                            table.setSelectedCells(selRow, selRow, selColumn, selColumn);
                            table.setRowSelectionInterval(selRow, selRow);
                            table.setColumnSelectionInterval(selColumn, selColumn);
                            table.scrollToCell(selRow, selColumn);
                        }

                        break;
                    }
                }
            } while (!(row == selRow && column == selColumn));
            // Continue to search until a match is found of the search wraps around to the starting
            // cell
        }
    }

    /**********************************************************************************************
     * Update the table data if a replacement occurred
     *
     * @param tableData
     *            list containing the table data row arrays
     *********************************************************************************************/
    private void updateTableData(List<Object[]> tableData)
    {
        // Check if text was replaced in a table cell
        if (isReplaced)
        {
            // Load the array of data into the table
            table.loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);

            // Force the table to redraw in order for all changes to appear
            repaint();

            // Flag the end of the editing sequence for undo/redo purposes
            table.getUndoManager().endEditSequence();

            // Update the number of results found label
            updateMatchCount();
        }
    }

    /**********************************************************************************************
     * Update the match counter text in the search dialog
     *
     * @return Number of matches
     *********************************************************************************************/
    private int updateMatchCount()
    {
        int matchCount = 0;

        // Check if a search is in effect
        if (searchPattern != null)
        {
            // Step through each row in the table (including hidden ones)
            for (int row = 0; row < table.getModel().getRowCount(); row++)
            {
                // Step through each column in the table (including hidden ones)
                for (int column = 0; column < table.getModel().getColumnCount(); column++)
                {
                    // Check if the column is visible
                    if (!table.isColumnHidden(column))
                    {
                        // Create the pattern matcher from the pattern
                        Matcher matcher = searchPattern.matcher(table.getModel()
                                                                     .getValueAt(row,
                                                                                 column)
                                                                     .toString());

                        // Check if there is a match in the cell value
                        while (matcher.find())
                        {
                            // Update the match results counter
                            matchCount++;
                        }
                    }
                }
            }
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

        return matchCount;
    }

    /**********************************************************************************************
     * Placeholder for method to display all hidden rows
     *********************************************************************************************/
    protected void showAllRows()
    {
    }
}
