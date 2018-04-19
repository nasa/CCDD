/**
 * CFS Command & Data Dictionary search data or table type table dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National Aeronautics and Space
 * Administration. No copyright is claimed in the United States under Title 17, U.S. Code. All
 * Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
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
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command & Data Dictionary search data or table type table dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchTableDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddFrameHandler editorDialog;
    private CcddJTableHandler activeEditor;

    // Components referenced from multiple methods
    private AutoCompleteTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JLabel numMatchesLbl;
    private JButton btnPrevious;
    private JButton btnNext;

    // Pattern for matching search text in the table cells
    private Pattern pattern;

    // Listener for table editor dialog focus events; used in conjunction with the search dialog
    private WindowFocusListener editorListener = null;

    // List of cell coordinates for cells with text matching the search criteria
    private List<Integer[]> matchCells;

    /**********************************************************************************************
     * Search data or table type table dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param editorDialog
     *            reference to the table or table type editor dialog
     *
     * @param editor
     *            reference to the active table or table type editor in the editor dialog
     *********************************************************************************************/
    CcddSearchTableDialog(CcddMain ccddMain,
                          CcddFrameHandler editorDialog,
                          CcddJTableHandler editor)
    {
        this.ccddMain = ccddMain;
        this.editorDialog = editorDialog;
        this.activeEditor = editor;

        // Create the database table search dialog
        initialize();
    }

    /**********************************************************************************************
     * Set the active table or table type editor in the editor dialog
     *
     * @param activeEditor
     *            reference to the active table or table type editor in the editor dialog
     *********************************************************************************************/
    protected void setActiveEditor(CcddJTableHandler activeEditor)
    {

        // Check if the search dialog is open already
        if (this.activeEditor != null)
        {
            // Remove the highlighting from previous table
            activeEditor.highlightSearchText(null);
        }

        this.activeEditor = activeEditor;
        activeEditor.highlightSearchText(pattern);
    }

    /**********************************************************************************************
     * Enable or disable the previous and next buttons
     *
     * @param enable
     *            true to enable the previous and next buttons
     *********************************************************************************************/
    private void setGoToEnable(boolean enable)
    {
        btnPrevious.setEnabled(enable);
        btnNext.setEnabled(enable);
    }

    /**********************************************************************************************
     * Create and display the table search dialog
     *********************************************************************************************/
    protected void initialize()
    {
        matchCells = new ArrayList<Integer[]>();

        // Set the flag so that the search dialog is always on top of other windows
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
                    // multiple search dialogs are open this allows them to 'share' the list rather
                    // than overwriting each other
                    List<String> searches = new ArrayList<String>(ModifiableSizeInfo.NUM_REMEMBERED_SEARCHES.getSize());
                    searches.addAll(Arrays.asList(ccddMain.getProgPrefs().get(SEARCH_STRINGS,
                                                                              "")
                                                          .split(STRING_LIST_TEXT_SEPARATOR)));
                    searchFld.setList(searches);
                }
            }
        });

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        inputPnl.add(searchFld, gbc);

        // Create a check box for ignoring the text case
        ignoreCaseCb = new JCheckBox("Ignore text case");
        ignoreCaseCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        ignoreCaseCb.setBorder(emptyBorder);
        ignoreCaseCb.setToolTipText(CcddUtilities.wrapText("Ignore case when matching the search string",
                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for check box selection changes
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
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.gridy++;
        inputPnl.add(ignoreCaseCb, gbc);

        // Create a check box for allow a regular expression in the search string
        allowRegexCb = new JCheckBox("Allow regular expression");
        allowRegexCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        allowRegexCb.setBorder(emptyBorder);
        allowRegexCb.setToolTipText(CcddUtilities.wrapText("Allow the search string to contain a regular expression",
                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        gbc.gridy++;
        inputPnl.add(allowRegexCb, gbc);

        // Search database tables button
        JButton btnSearch = CcddButtonPanelHandler.createButton("Search",
                                                                SEARCH_ICON,
                                                                KeyEvent.VK_O,
                                                                "Search the active table");

        // Add a listener for the Search button
        btnSearch.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Highlight the search text in the table
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Create the match pattern from the search criteria
                pattern = searchFld.getText().isEmpty()
                                                        ? null
                                                        : Pattern.compile("(?"
                                                                          + (ignoreCaseCb.isSelected()
                                                                                                       ? "i"
                                                                                                       : "")
                                                                          + ":"
                                                                          + (allowRegexCb.isSelected()
                                                                                                       ? searchFld.getText()
                                                                                                       : Pattern.quote(searchFld.getText()))
                                                                          + ")");

                // Highlight the matching text in the table cells
                activeEditor.highlightSearchText(pattern);

                // TODO TO GET THE # OF MATCHES WILL NEED TO SEARCH THE TABLE CELLS (SIMILAR TO THE
                // HIGHLIGHTING; CAN'T USE THE HIGHLIGHTING METHOD RESULTS SINCE ONLY VISIBLE CELLS
                // GET HIGHLIGHTED). THIS TYPE OF TABLE SEARCH IS ALSO NEEDED IF A REPLACE OPTION
                // IS ADDED AND FOR A 'GO TO NEXT HIGHLIGHT' FEATURE
                int matchCount = 0;

                // Check if a search is in effect
                if (pattern != null)
                {
                    matchCells.clear();

                    // Step through each row in the table (including hidden ones)
                    for (int row = 0; row < activeEditor.getModel().getRowCount(); row++)
                    {
                        // Step through each column in the table (including hidden ones)
                        for (int column = 0; column < activeEditor.getModel().getColumnCount(); column++)
                        {
                            // Check if the column is visible
                            if (!activeEditor.isColumnHidden(column))
                            {
                                // Create the pattern matcher from the pattern
                                Matcher matcher = pattern.matcher(activeEditor.getModel().getValueAt(row, column).toString());

                                // Check if there is a match in the cell value
                                while (matcher.find())
                                {
                                    Integer[] match = new Integer[] {row, column};

                                    // Check if the list doesn't already contain this cell (for
                                    // previous/next purposes only one match per cell is recorded)
                                    // TODO HOWEVER, A REPLACE FUNCTION WOULD NEED ALL MATCHES!
                                    if (!matchCells.contains(match))
                                    {
                                        // Store the cell coordinates
                                        matchCells.add(match);
                                    }

                                    // Update the match results counter
                                    matchCount++;
                                }
                            }
                        }
                    }

                    // Update the number of results found label
                    numMatchesLbl.setText("  (" + matchCount + " matches)");
                }
                // No search is in effect
                else
                {
                    // Blank the number of matches text
                    numMatchesLbl.setText("");
                }

                // Enable/disable the previous and next buttons based on if search text is present
                setGoToEnable(matchCount != 0);
            }
        });

        // Go to previous match button
        btnPrevious = CcddButtonPanelHandler.createButton("Previous",
                                                          LEFT_ICON,
                                                          KeyEvent.VK_P,
                                                          "Go to the previous cell containing a match");

        // Add a listener for the Previous button
        btnPrevious.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select the cell containing the matching search text prior to the currently selected
             * cell
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                selectMatchCell(true);
            }
        });

        // Go to next match button
        btnNext = CcddButtonPanelHandler.createButton("Next",
                                                      RIGHT_ICON,
                                                      KeyEvent.VK_P,
                                                      "Go to the next cell containing a match");

        // Add a listener for the Next button
        btnNext.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select the cell containing the matching search text after the currently selected
             * cell
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                selectMatchCell(false);
            }
        });

        // Disable the previous and next buttons until a search is performed
        setGoToEnable(false);

        // Close search dialog button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the search dialog");

        // Add a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the search dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Remove the search text highlighting in the table
                activeEditor.highlightSearchText(null);
                activeEditor.repaint();

                // Remove the editor dialog's listener for search dialog window focus changes, then
                // close the search dialog
                editorDialog.removeWindowFocusListener(editorListener);
                CcddSearchTableDialog.this.closeDialog();
                editorListener = null;
            }
        });

        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnPrevious);
        buttonPnl.add(btnNext);
        buttonPnl.add(btnSearch);
        buttonPnl.add(btnClose);
        setButtonRows(2);

        // Add a listener for search dialog focus changes
        addWindowFocusListener(new WindowFocusListener()
        {
            /**************************************************************************************
             * Handle a search dialog gain of focus
             *************************************************************************************/
            @Override
            public void windowGainedFocus(WindowEvent we)
            {
                // Check if the table editor dialog's window focus listener doesn't exist. This is
                // the case when the search dialog is initially displayed
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
                            // Check if the search dialog is active
                            if (!CcddSearchTableDialog.this.isShowing())
                            {
                                // Show the search dialog
                                CcddSearchTableDialog.this.setVisible(true);
                            }
                        }

                        /**************************************************************************
                         * Handle a table editor dialog loss of focus
                         *************************************************************************/
                        @Override
                        public void windowLostFocus(WindowEvent we)
                        {
                            // Check if the search dialog is active
                            if (CcddSearchTableDialog.this.isShowing())
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
                                        // Check if the search dialog doesn't have the focus
                                        if (!CcddSearchTableDialog.this.isFocused())
                                        {
                                            // Hide the search dialog
                                            CcddSearchTableDialog.this.setVisible(false);
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
             * Handle a search dialog loss of focus
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
                            // Hide the search dialog
                            CcddSearchTableDialog.this.setVisible(false);
                        }
                    }
                });
            }
        });

        // Display the search dialog
        createDialog(editorDialog,
                     inputPnl,
                     buttonPnl,
                     btnSearch,
                     "Search",
                     null,
                     null,
                     true,
                     false);

        // Except for its initial appearance, don't set the focus to the search dialog each time it
        // reappears due to the table editor dialog regaining focus
        setAutoRequestFocus(false);
    }

    /**********************************************************************************************
     * Select the next or previous cell matching the search text
     *
     * @param isPrevious
     *            true to select the previous match; false to select the next match
     *********************************************************************************************/
    private void selectMatchCell(boolean isPrevious)
    {
        int index = -1;

        // Get the coordinates of the first selected cell (if any)
        int row = activeEditor.getSelectedRow();
        int column = activeEditor.getSelectedColumn();

        // Check if a cell is selected
        if (row != -1 && column != -1)
        {
            // Convert the cell view coordinates to model coordinates
            row = activeEditor.convertRowIndexToModel(row);
            column = activeEditor.convertColumnIndexToModel(column);

            // Step through the pairs of search cell coordinates and stop searching once the match
            // coordinates are the same as or are past the currently selected cell
            for (Integer[] match : matchCells)
            {
                index++;

                // Check if the currently select cell contains a match
                if (match[0] == row && match[1] == column)
                {
                    break;
                }

                // Check if the cell containing a match is past (left and/or below) the currently
                // selected cell
                if (match[0] >= row && match[1] >= column)
                {
                    // Check if the next match is sought
                    if (!isPrevious)
                    {
                        // Back up the index to account for it being incremented below
                        index--;
                    }

                    break;
                }
            }
        }

        // Check if the previous match should be selected
        if (isPrevious)
        {
            // Adjust the index to the previous match, wrapping to the end of the table if
            // necessary
            index--;

            if (index < 0)
            {
                index = matchCells.size() - 1;
            }
        }
        // The next match should be selected
        else
        {
            // Adjust the index to the next match, wrapping to the beginning of the table if
            // necessary
            index++;

            if (index >= matchCells.size())
            {
                index = 0;
            }
        }

        // Convert the model coordinates of the cell containing the match to view coordinates
        row = activeEditor.convertRowIndexToView(matchCells.get(index)[0]);
        column = activeEditor.convertColumnIndexToView(matchCells.get(index)[1]);

        // Check if the row is hidden
        if (row == -1)
        {
            // Show all hidden rows in the table and get the row's model coordinate
            showAllRows();
            // TODO IT APPEARS THAT THE EXPANSION ISN'T COMPLETE BEFORE IT SCROLLS TO THE ROW
            // (BELOW). IT SCROLLS, BUT THE ROW WITH THE MATCH IS OUTSIDE THE SCROLL VIEWPORT.
            // TRIED PUTTING THE CODE THAT SELECTS THE CELLS & SCROLLS IN AN invokeLater() CALL BUT
            // THAT DIDN'T HELP. ONCE THE EXPANSION IS DONE IT SCROLLS TO THE ROW AS EXPECTED
            row = activeEditor.convertRowIndexToView(matchCells.get(index)[0]);
        }

        // Check if the coordinates are valid (this should always be true by this point)
        if (row != -1 && column != -1)
        {
            // Highlight the selected cell and scroll the table so that it's visible
            activeEditor.setSelectedCells(row, row, column, column);
            activeEditor.setRowSelectionInterval(row, row);
            activeEditor.setColumnSelectionInterval(column, column);
            activeEditor.scrollToRow(row);
        }
    }

    /**********************************************************************************************
     * Placeholder for method to display all hidden rows
     *********************************************************************************************/
    protected void showAllRows()
    {
    }
}
