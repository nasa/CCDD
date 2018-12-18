/**
 * CFS Command and Data Dictionary search database tables, scripts, and event log dialog. Copyright
 * 2017 United States Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under Title 17, U.S.
 * Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;
import static CCDD.CcddConstants.TABLE_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
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
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.MultilineLabel;
import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.SearchResultsColumnInfo;
import CCDD.CcddConstants.SearchTarget;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddConstants.VariablePathTableColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary search database tables, scripts, and event log dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddJTableHandler resultsTable;
    private CcddEventLogDialog eventLog;
    private CcddTableTreeHandler tableTree;
    private CcddScriptTreeHandler scriptTree;
    private CcddSearchHandler searchHandler;

    // Components referenced from multiple methods
    private AutoCompleteTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox allowRegexCb;
    private JCheckBox dataTablesOnlyCb;
    private JCheckBox selectedColumnsCb;
    private JLabel numMatchesLbl;
    private MultilineLabel selectedColumnsLbl;

    // Pattern for matching search text in the table cells. The highlight pattern differs slightly
    // from the search pattern depending of the type of search. If calling the PostgreSQL
    // search_table() function the ignore case flag is provided as an option to the function
    // instead of being part of the matching string (the '?i' flag). If searching a text string
    // (e.g., a log entry) the '?i' flag is used in the matching string
    private Pattern searchPattern;
    private Pattern highlightPattern;

    // Selected tables or scripts used to filter the search results
    private String searchFilter;

    // Comparison search criteria used to determine if the criteria changed
    private String prevSearchText;
    private boolean prevIgnoreCase;
    private boolean prevAllowRegex;
    private String prevFilter;
    private String prevColumns;

    // String containing the names of columns, separated by commas, to which to constrain a table
    // search
    private String searchColumns;

    // Row index to match if this is an event log entry search on a table that displays only a
    // single log entry; null otherwise
    private final Long targetRow;

    // Search dialog type
    private final SearchDialogType searchDlgType;

    // Array to contain the search results
    private Object[][] matchData;

    // Wild card search character explanation label
    private static String WILD_CARD_LABEL = "? = character, * = string, \\ for literal ? or *";

    /**********************************************************************************************
     * Search database tables, scripts, and event log dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param searchDlgType
     *            search dialog type: TABLES, SCRIPTS, or LOG
     *
     * @param targetRow
     *            row index to match if this is an event log entry search on a table that displays
     *            only a single log entry; null otherwise
     *
     * @param eventLog
     *            event log to search; null if not searching a log
     *
     * @param parent
     *            GUI component over which to center the dialog
     *********************************************************************************************/
    CcddSearchDialog(CcddMain ccddMain,
                     SearchDialogType searchDlgType,
                     Long targetRow,
                     CcddEventLogDialog eventLog,
                     Component parent)
    {
        this.ccddMain = ccddMain;
        this.searchDlgType = searchDlgType;
        this.targetRow = targetRow;
        this.eventLog = eventLog;

        // Create reference to shorten subsequent calls
        tableTypeHandler = ccddMain.getTableTypeHandler();

        // Initialize the search results table contents
        matchData = new Object[0][0];

        // Create the database table search dialog
        initialize(parent);
    }

    /**********************************************************************************************
     * Search database tables and scripts class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param searchType
     *            search dialog type: TABLES or SCRIPTS
     *
     * @param parent
     *            GUI component over which to center the dialog
     *********************************************************************************************/
    CcddSearchDialog(CcddMain ccddMain, SearchDialogType searchType, Component parent)
    {
        this(ccddMain, searchType, null, null, parent);
    }

    /**********************************************************************************************
     * Set the reference to the event log to search and update the search dialog title
     *
     * @param eventLog
     *            reference to the event log to search
     *********************************************************************************************/
    protected void setEventLog(CcddEventLogDialog eventLog)
    {
        this.eventLog = eventLog;
        searchHandler.setEventLog(eventLog);
        setTitle(getLogTitle());
    }

    /**********************************************************************************************
     * Get the reference to the search results table
     *
     * @return Reference to the search results table
     *********************************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return resultsTable;
    }

    /**********************************************************************************************
     * Create the database table or scripts search dialog
     *
     * @param parent
     *            GUI component over which to center the dialog
     *********************************************************************************************/
    private void initialize(Component parent)
    {
        prevSearchText = null;
        searchFilter = "";
        searchColumns = "";

        // Create a search handler
        searchHandler = new CcddSearchHandler(ccddMain, searchDlgType, targetRow, eventLog);

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
                                                        new Insets(0,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   (searchDlgType == SearchDialogType.LOG
                                                                                                          ? 0
                                                                                                          : ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing())),
                                                        0,
                                                        0);

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        JPanel upperPnl = new JPanel(new GridBagLayout());
        JPanel inputPnl = new JPanel(new GridBagLayout());
        JPanel filterPnl = new JPanel(new GridBagLayout());
        JPanel lowerPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(emptyBorder);
        upperPnl.setBorder(emptyBorder);
        inputPnl.setBorder(emptyBorder);
        filterPnl.setBorder(emptyBorder);
        lowerPnl.setBorder(emptyBorder);

        // Create the search dialog labels and fields
        JLabel dlgLbl = new JLabel("Enter search text");
        dlgLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        inputPnl.add(dlgLbl, gbc);

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

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        gbc.gridy++;
        inputPnl.add(searchFld, gbc);

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

        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.left = 0;
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

        // Check if this is a table search
        if (searchDlgType == SearchDialogType.TABLES)
        {
            final ArrayListMultiple columns = new ArrayListMultiple();

            // Create a check box for ignoring matches within the internal tables
            dataTablesOnlyCb = new JCheckBox("Search data table cells only");
            dataTablesOnlyCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            dataTablesOnlyCb.setBorder(emptyBorder);
            dataTablesOnlyCb.setToolTipText(CcddUtilities.wrapText("Search only the cells in the data tables",
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.gridy++;
            inputPnl.add(dataTablesOnlyCb, gbc);

            // Step through each defined table type
            for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
            {
                // Step through each visible column in the table type
                for (int index = NUM_HIDDEN_COLUMNS; index < typeDefn.getColumnCountDatabase(); ++index)
                {
                    // Check if the column name isn't already in the list
                    if (!columns.contains(typeDefn.getColumnNamesUser()[index]))
                    {
                        // Add the visible column name, its corresponding database name, and the
                        // column description to the list
                        columns.add(new String[] {typeDefn.getColumnNamesUser()[index],
                                                  typeDefn.getColumnNamesDatabase()[index],
                                                  typeDefn.getColumnToolTips()[index]});
                    }
                }
            }

            // Check if any columns are defined
            if (columns.size() != 0)
            {
                final ArrayListMultiple columnNames = new ArrayListMultiple();

                // Sort the column names alphabetically
                columns.sort(ArrayListMultipleSortType.STRING);

                // Create the column selection check box
                selectedColumnsCb = new JCheckBox("Search selected columns");
                selectedColumnsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                selectedColumnsCb.setBorder(emptyBorder);
                selectedColumnsCb.setToolTipText(CcddUtilities.wrapText("Search only selected columns in the data tables",
                                                                        ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.insets.bottom = 0;
                gbc.gridy++;
                inputPnl.add(selectedColumnsCb, gbc);
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;

                // Create the selected columns label
                selectedColumnsLbl = new MultilineLabel();
                selectedColumnsLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
                selectedColumnsLbl.setMinimumSize(selectedColumnsLbl.getPreferredSize());
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3;
                gbc.gridy++;
                inputPnl.add(selectedColumnsLbl, gbc);

                // Create a panel for the column selection pop-up dialog
                final JPanel columnPnl = new JPanel(new GridBagLayout());
                columnPnl.setBorder(emptyBorder);

                // Step through each column
                for (String[] column : columns)
                {
                    // Add the visible name and description to the list used to create the check
                    // box panel
                    columnNames.add(new String[] {column[0], column[2]});
                }

                // Create the column name pop-up dialog
                final CcddDialogHandler columnDlg = new CcddDialogHandler();

                // Add the column name check boxes to the dialog
                columnDlg.addCheckBoxes(null,
                                        columnNames.toArray(new String[0][0]),
                                        null,
                                        "",
                                        false,
                                        columnPnl);

                // Check if more than one column name check box exists
                if (columnNames.size() > 1)
                {
                    // Create a Select All check box
                    final JCheckBox selectAllCb = new JCheckBox("Select all columns", false);
                    selectAllCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                    selectAllCb.setBorder(emptyBorder);

                    // Create a listener for changes to the Select All check box selection status
                    selectAllCb.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Handle a change to the Select All check box selection status
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Step through each column name check box
                            for (JCheckBox columnCb : columnDlg.getCheckBoxes())
                            {
                                // Set the check box selection status to match the Select All check
                                // box selection status
                                columnCb.setSelected(selectAllCb.isSelected());
                            }
                        }
                    });

                    // Add the Select All checkbox to the column name panel
                    gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                    gbc.gridy++;
                    columnPnl.add(selectAllCb, gbc);

                    // Step through each column name check box
                    for (JCheckBox columnCb : columnDlg.getCheckBoxes())
                    {
                        // Create a listener for changes to the column name check box selection
                        // status
                        columnCb.addActionListener(new ActionListener()
                        {
                            /**********************************************************************
                             * Handle a change to the column name check box selection status
                             *********************************************************************/
                            @Override
                            public void actionPerformed(ActionEvent ae)
                            {
                                int columnCount = 0;

                                // Step through each column name check box
                                for (int index = 0; index < columnDlg.getCheckBoxes().length; index++)
                                {
                                    // Check if the check box is selected
                                    if (columnDlg.getCheckBoxes()[index].isSelected())
                                    {
                                        // Increment the counter to track the number of selected
                                        // column name check boxes
                                        columnCount++;
                                    }
                                }

                                // Set the Select All check box status based on if all the column
                                // name check boxes are selected
                                selectAllCb.setSelected(columnCount == columnDlg.getCheckBoxes().length);
                            }
                        });
                    }
                }

                // Add a listener for column choice check box selection changes
                selectedColumnsCb.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a change in the selected columns check box state
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the column selection check box is selected
                        if (selectedColumnsCb.isSelected())
                        {
                            // Display a pop-up for choosing which table columns to search
                            if (columnDlg.showOptionsDialog(CcddSearchDialog.this,
                                                            columnPnl,
                                                            "Select Column(s)",
                                                            DialogOption.OK_CANCEL_OPTION,
                                                            true) == OK_BUTTON)
                            {
                                searchColumns = "";
                                String searchColVisible = "";

                                // Step through each column name check box
                                for (int index = 0; index < columnDlg.getCheckBoxes().length; index++)
                                {
                                    // Check if the check box is selected
                                    if (columnDlg.getCheckBoxes()[index].isSelected())
                                    {
                                        // Add the name of the column to the constraint string
                                        searchColumns += columns.get(index)[1] + ",";
                                        searchColVisible += columns.get(index)[0] + ",";
                                    }
                                }

                                searchColumns = CcddUtilities.removeTrailer(searchColumns, ",");
                                searchColVisible = CcddUtilities.removeTrailer(searchColVisible, ",");

                                // Set the selected column(s) label to display the selected
                                // column(s)
                                selectedColumnsLbl.setText(searchColVisible.replaceAll(",", ", "));
                            }

                            // Check if no column is selected
                            if (searchColumns.isEmpty())
                            {
                                // Deselect the selected columns check box and blank the selected
                                // column(s) text
                                selectedColumnsCb.setSelected(false);
                                selectedColumnsLbl.setText("");
                            }
                        }
                        // The column selection check box is not selected
                        else
                        {
                            // Blank the column constraint string and the selected column(s) text
                            searchColumns = "";
                            selectedColumnsLbl.setText("");
                        }

                        // Set the minimum width of the selected columns label so that the split
                        // pane can resize horizontally correctly
                        selectedColumnsLbl.setMinimumSize(new Dimension(1,
                                                                        selectedColumnsLbl.getPreferredSize().height));

                        // Adjust the dialog size to force the components to adjust to the new size
                        // of the selected columns label (in case it gets larger). Without this the
                        // upper panel can be sized taller than can be displayed
                        CcddSearchDialog.this.setSize(CcddSearchDialog.this.getSize().width,
                                                      CcddSearchDialog.this.getSize().height - 1);

                        // Create a runnable object to be executed
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            /**********************************************************************
                             * Allow the first setSize() to complete before setting the dialog back
                             * to its original size
                             *********************************************************************/
                            @Override
                            public void run()
                            {
                                // Set the dialog back to its original size
                                CcddSearchDialog.this.setSize(CcddSearchDialog.this.getSize().width,
                                                              CcddSearchDialog.this.getSize().height + 1);
                            }
                        });
                    }
                });
            }
        }

        // Add the inputs panel, containing the search field and check boxes, to the upper panel
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.fill = GridBagConstraints.BOTH;

        // Check if this is a table or script search
        if (searchDlgType == SearchDialogType.TABLES || searchDlgType == SearchDialogType.SCRIPTS)
        {
            // Add a dummy label to force positioning of the components
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.gridy++;
            inputPnl.add(new JLabel(), gbc);

            // Create the filter selection label and tree
            JLabel filterLbl = new JLabel("Stored Scripts");
            filterLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.weighty = 0.0;
            gbc.insets.top = 0;
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.gridy = 0;
            filterPnl.add(filterLbl, gbc);

            // Create the choices and selected lists and add them to the dialog
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.right = 0;
            gbc.weighty = 1.0;
            gbc.gridy++;

            // Check if this is a table search
            if (searchDlgType == SearchDialogType.TABLES)
            {
                // Set the filter table title
                filterLbl.setText("Filter by Table(s)");

                // Build the table tree showing both table prototypes and table instances; i.e.,
                // parent tables with their child tables (i.e., parents with children)
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     new CcddGroupHandler(ccddMain,
                                                                          null,
                                                                          parent),
                                                     TableTreeType.TABLES,
                                                     true,
                                                     false,
                                                     parent);

                // Add the table tree to the filter panel
                filterPnl.add(tableTree.createTreePanel(null,
                                                        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                        false,
                                                        parent),
                              gbc);
            }
            // This is a script search
            else
            {
                // Set the filter table title
                filterLbl.setText("Filter by Script(s)");

                // Build the script tree showing the names of the scripts stored in the project
                // database
                scriptTree = new CcddScriptTreeHandler(ccddMain, parent);

                // Add the script tree to the filter panel
                filterPnl.add(scriptTree.createTreePanel(null,
                                                         TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION),
                              gbc);
            }

            JSeparator inputSep = new JSeparator(SwingConstants.VERTICAL);
            inputSep.setForeground(dialogPnl.getBackground().darker());
            gbc.insets.left = 0;
            gbc.insets.bottom = 0;
            upperPnl.add(new CustomSplitPane(inputPnl,
                                             filterPnl,
                                             inputSep,
                                             JSplitPane.HORIZONTAL_SPLIT),
                         gbc);
        }

        // Create the results and number of results found labels
        JLabel resultsLbl = new JLabel("Search results");
        resultsLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        resultsLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        numMatchesLbl = new JLabel();
        numMatchesLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());

        // Add the results labels to the dialog
        JPanel resultsPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resultsPnl.add(resultsLbl);
        resultsPnl.add(numMatchesLbl);

        // Create the table to display the search results
        resultsTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow multiple line display in the specified columns, depending on search type
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return searchDlgType == SearchDialogType.TABLES
                       || (searchDlgType == SearchDialogType.LOG
                           && column == SearchResultsColumnInfo.CONTEXT.ordinal())
                       || (searchDlgType == SearchDialogType.SCRIPTS
                           && (column == SearchResultsColumnInfo.OWNER.ordinal()
                               || column == SearchResultsColumnInfo.CONTEXT.ordinal()));
            }

            /**************************************************************************************
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column)
            {
                return searchDlgType == SearchDialogType.TABLES
                       && (column == SearchResultsColumnInfo.OWNER.ordinal()
                           || column == SearchResultsColumnInfo.CONTEXT.ordinal());
            }

            /**************************************************************************************
             * Allow the specified column's cells to be displayed with the text highlighted
             *************************************************************************************/
            @Override
            protected boolean isColumnHighlight(int column)
            {
                return column == SearchResultsColumnInfo.CONTEXT.ordinal();
            }

            /**************************************************************************************
             * Load the search results data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(matchData,
                                            SearchResultsColumnInfo.getColumnNames(searchDlgType),
                                            null,
                                            SearchResultsColumnInfo.getToolTips(searchDlgType),
                                            true,
                                            true,
                                            true);
            }

            /**************************************************************************************
             * Override the table layout so that extra width is apportioned unequally between the
             * columns when the table is resized
             *************************************************************************************/
            @Override
            public void doLayout()
            {
                // Get a reference to the column being resized
                if (getTableHeader() != null
                    && getTableHeader().getResizingColumn() == null)
                {
                    // Get a reference to the event table's column model to shorten subsequent
                    // calls
                    TableColumnModel tcm = getColumnModel();

                    // Calculate the change in the search dialog's width
                    int delta = getParent().getWidth() - tcm.getTotalColumnWidth();

                    // Get the reference to the search results table columns
                    TableColumn tgtColumn = tcm.getColumn(SearchResultsColumnInfo.OWNER.ordinal());
                    TableColumn locColumn = tcm.getColumn(SearchResultsColumnInfo.LOCATION.ordinal());
                    TableColumn cntxtColumn = tcm.getColumn(SearchResultsColumnInfo.CONTEXT.ordinal());

                    // Set the columns' widths to its current width plus a percentage of the the
                    // extra width added to the dialog due to the resize
                    tgtColumn.setPreferredWidth(tgtColumn.getPreferredWidth()
                                                + (int) (delta * 0.25));
                    tgtColumn.setWidth(tgtColumn.getPreferredWidth());
                    locColumn.setPreferredWidth(locColumn.getPreferredWidth()
                                                + (int) (delta * 0.25));
                    locColumn.setWidth(locColumn.getPreferredWidth());
                    cntxtColumn.setPreferredWidth(cntxtColumn.getPreferredWidth()
                                                  + delta - (int) (delta * 0.25) * 2);
                    cntxtColumn.setWidth(cntxtColumn.getPreferredWidth());
                }
                // Table header or resize column not available
                else
                {
                    super.doLayout();
                }
            }

            /**************************************************************************************
             * Highlight the matching search text in the context column cells
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
                // Check if the column allows highlighting
                if (isColumnHighlight(column))
                {
                    // Highlight the matching text in the column
                    CcddUtilities.highlightSearchText(component,
                                                      text,
                                                      (isSelected
                                                                  ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                                  : ModifiableColorInfo.SEARCH_HIGHLIGHT.getColor()),
                                                      highlightPattern);
                }
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(resultsTable);

        // Set up the search results table parameters
        resultsTable.setFixedCharacteristics(scrollPane,
                                             false,
                                             ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                             TableSelectionMode.SELECT_BY_CELL,
                                             true,
                                             ModifiableColorInfo.TABLE_BACK.getColor(),
                                             false,
                                             true,
                                             ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                             true);

        // Define the panel to contain the table
        JPanel resultsTblPnl = new JPanel();
        resultsTblPnl.setLayout(new BoxLayout(resultsTblPnl, BoxLayout.X_AXIS));
        resultsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        resultsTblPnl.add(scrollPane);

        // Add the results table to the lower panel
        gbc.insets.set(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                       0,
                       0,
                       0);
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        gbc.gridy = 0;
        lowerPnl.add(resultsPnl, gbc);
        gbc.weighty = 1.0;
        gbc.gridy++;
        lowerPnl.add(resultsTblPnl, gbc);
        gbc.gridy = 0;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();

        // Check if this is an event log search
        if (searchDlgType == SearchDialogType.LOG)
        {
            // Add the input (search criteria) and lower (search results) panels to the dialog
            gbc.weighty = 0.0;
            dialogPnl.add(inputPnl, gbc);
            gbc.weighty = 1.0;
            gbc.gridy++;
            dialogPnl.add(lowerPnl, gbc);
        }
        // This is a table or script search
        else
        {
            // Add the upper (search criteria) and lower (search results) panels in a split pane to
            // the dialog
            JSeparator resultsSep = new JSeparator();
            resultsSep.setForeground(dialogPnl.getBackground().darker());
            dialogPnl.add(new CustomSplitPane(upperPnl,
                                              lowerPnl,
                                              resultsSep,
                                              JSplitPane.VERTICAL_SPLIT),
                          gbc);
        }

        // Search database tables button
        JButton btnSearch = CcddButtonPanelHandler.createButton("Search",
                                                                SEARCH_ICON,
                                                                KeyEvent.VK_O,
                                                                "Search the project database");

        // Add a listener for the Search button
        btnSearch.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Search the database tables and display the results
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                performSearch();
            }
        });

        JButton btnOpen = null;

        // Check if this is the table search dialog
        if (searchDlgType == SearchDialogType.TABLES)
        {
            // Create a table opener for the Open tables command
            final TableOpener opener = new TableOpener()
            {
                /**********************************************************************************
                 * Check if the search result is for a table or table data field
                 *
                 * @return true if the search result is for a table or table data field
                 *********************************************************************************/
                @Override
                protected boolean isApplicable(String tableName)
                {
                    return tableName.startsWith(SearchTarget.TABLE.getTargetName(true))
                           || tableName.startsWith(SearchTarget.TABLE_FIELD.getTargetName(true));
                }

                /**********************************************************************************
                 * Remove any HTML tags and the owner identifier from the table name
                 *********************************************************************************/
                @Override
                protected String cleanUpTableName(String tableName, int row)
                {
                    return CcddUtilities.removeHTMLTags(tableName).replaceFirst(".+:\\s", "");
                }
            };

            // Open table(s) button
            btnOpen = CcddButtonPanelHandler.createButton("Open",
                                                          TABLE_ICON,
                                                          KeyEvent.VK_O,
                                                          "Open the table(s) associated with the selected search result(s)");

            // Add a listener for the Open button
            btnOpen.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Open the selected table(s)
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    opener.openTables(resultsTable,
                                      VariablePathTableColumnInfo.APP_FORMAT.ordinal());
                }
            });
        }

        // Print inconsistencies button
        JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                               PRINT_ICON,
                                                               KeyEvent.VK_P,
                                                               "Print the search results list");

        // Add a listener for the Print button
        btnPrint.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Print the search results list
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                resultsTable.printTable("Search Results",
                                        null,
                                        CcddSearchDialog.this,
                                        PageFormat.LANDSCAPE);
            }
        });

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
                CcddSearchDialog.this.closeFrame();
            }
        });

        // Create a panel for the dialog buttons and add the buttons to the panel
        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnSearch);

        // Check if this is the table search dialog
        if (searchDlgType == SearchDialogType.TABLES)
        {
            buttonPnl.add(btnOpen);
        }

        buttonPnl.add(btnPrint);
        buttonPnl.add(btnClose);

        // Get the dialog title based on the search type
        String title = null;

        switch (searchDlgType)
        {
            case TABLES:
                title = "Search Tables";
                break;

            case SCRIPTS:
                title = "Search Scripts";
                break;

            case LOG:
                title = getLogTitle();
                break;
        }

        // Display the search dialog
        createFrame(parent, dialogPnl, buttonPnl, btnSearch, title, null);
    }

    /**********************************************************************************************
     * Search the tables/log/scripts for text matching the search criteria
     *********************************************************************************************/
    private void performSearch()
    {
        // Check if the search criteria changed
        if (!searchFld.getText().equals(prevSearchText)
            || ignoreCaseCb.isSelected() != prevIgnoreCase
            || allowRegexCb.isSelected() != prevAllowRegex
            || !searchFilter.equals(prevFilter)
            || !searchColumns.equals(prevColumns))
        {
            // Store the search criteria
            prevSearchText = searchFld.getText();
            prevIgnoreCase = ignoreCaseCb.isSelected();
            prevAllowRegex = allowRegexCb.isSelected();
            prevFilter = searchFilter;
            prevColumns = searchColumns;

            // Check if the search field is blank
            if (searchFld.getText().isEmpty())
            {
                // Inform the user that the input value is invalid
                new CcddDialogHandler().showMessageDialog(CcddSearchDialog.this,
                                                          "<html><b>Search text cannot be blank",
                                                          "Invalid Input",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
            // The search field contains text
            else
            {
                List<Object[]> resultsDataList = null;

                // Create the match patterns from the search criteria
                searchPattern = CcddSearchHandler.createSearchPattern(searchFld.getText(),
                                                                      (searchDlgType == SearchDialogType.LOG
                                                                                                             ? ignoreCaseCb.isSelected()
                                                                                                             : false),

                                                                      allowRegexCb.isSelected(),
                                                                      CcddSearchDialog.this);
                highlightPattern = searchDlgType == SearchDialogType.LOG
                                   || !ignoreCaseCb.isSelected()
                                                                 ? searchPattern
                                                                 : CcddSearchHandler.createSearchPattern(searchFld.getText(),
                                                                                                         ignoreCaseCb.isSelected(),
                                                                                                         allowRegexCb.isSelected(),
                                                                                                         CcddSearchDialog.this);

                // Check if the search pattern is valid
                if (searchPattern != null)
                {
                    // Update the search string list
                    searchFld.updateList(searchFld.getText());

                    // Store the search list in the program preferences
                    ccddMain.getProgPrefs().put(SEARCH_STRINGS, searchFld.getListAsString());

                    switch (searchDlgType)
                    {
                        case TABLES:
                        case SCRIPTS:
                            // Search the database tables or scripts and display the results
                            resultsDataList = searchHandler.searchTablesOrScripts(searchPattern.pattern(),
                                                                                  ignoreCaseCb.isSelected(),
                                                                                  (searchDlgType == SearchDialogType.TABLES
                                                                                                                            ? dataTablesOnlyCb.isSelected()
                                                                                                                            : false),
                                                                                  searchColumns);
                            break;

                        case LOG:
                            // Search the event log and display the results
                            resultsDataList = searchHandler.searchEventLogFile(searchPattern,
                                                                               targetRow);
                            break;
                    }

                    // Check if this is a table search
                    if (searchDlgType == SearchDialogType.TABLES)
                    {
                        List<Object[]> removeResults = new ArrayList<Object[]>();

                        // Get the list of selected tables
                        List<String> filterTables = tableTree.getSelectedTablesWithChildren();

                        // Add the ancestors (instances and prototype) of the selected tables to
                        // the list of filter tables
                        tableTree.addTableAncestors(filterTables, true);

                        // Store the selected table names as a single string. This is used to
                        // determine if the selection changes
                        searchFilter = Arrays.toString(filterTables.toArray(new String[0]));

                        // Check if tables were selected to filter the search results
                        if (!filterTables.isEmpty())
                        {
                            // Step through the search results
                            for (Object[] result : resultsDataList)
                            {
                                // Separate the target into the target type and owner
                                String[] typeAndOwner = CcddUtilities.removeHTMLTags(result[SearchResultsColumnInfo.OWNER.ordinal()].toString()).split(": ");

                                // Check if the target type isn't a table or table data field, and
                                // if owner isn't one of the selected tables or its prototype
                                if (!((typeAndOwner[0].equals(SearchTarget.TABLE.getTargetName(false))
                                       || typeAndOwner[0].equals(SearchTarget.TABLE_FIELD.getTargetName(false)))
                                      && filterTables.contains(typeAndOwner[1])))
                                {
                                    // Add the search result to the list of those to remove
                                    removeResults.add(result);
                                }

                                // Note: Since prototype tables are automatically added (needed
                                // since a child only returns matches in the values table), a false
                                // match can occur if the filter table is a child, the hit is in
                                // the child's prototype, and the child has overridden the
                                // prototype's value where the match occurs
                            }

                            // Remove the search results that aren't in the selected table(s)
                            resultsDataList.removeAll(removeResults);
                        }
                    }
                    // Check if this is a script search
                    else if (searchDlgType == SearchDialogType.SCRIPTS)
                    {
                        List<Object[]> removeResults = new ArrayList<Object[]>();
                        List<String> filterScripts = new ArrayList<String>();

                        // Step through each script selected in the script tree
                        for (TreePath path : scriptTree.getSelectedPaths())
                        {
                            // Add the script name to the filter list
                            filterScripts.add(path.getLastPathComponent().toString());
                        }

                        // Store the selected script names as a single string. This is used to
                        // determine if the selection changes
                        searchFilter = Arrays.toString(filterScripts.toArray(new String[0]));

                        // Check if scripts were selected to filter the search results
                        if (!filterScripts.isEmpty())
                        {
                            // Step through the search results
                            for (Object[] result : resultsDataList)
                            {
                                // Check if the script name isn't one of the ones selected
                                if (!filterScripts.contains(result[SearchResultsColumnInfo.OWNER.ordinal()].toString()))
                                {
                                    // Add the search result to the list of those to remove
                                    removeResults.add(result);
                                }
                            }

                            // Remove the search results that aren't in the selected scripts
                            resultsDataList.removeAll(removeResults);
                        }
                    }

                    // Convert the results list to an array and display the results in the dialog's
                    // search results table
                    matchData = resultsDataList.toArray(new Object[0][0]);
                    resultsTable.loadAndFormatData();
                }
            }

            // Update the number of matches found label
            numMatchesLbl.setText(matchData.length != 0
                                                        ? "  ("
                                                          + matchData.length
                                                          + (matchData.length == 1
                                                                                   ? " match"
                                                                                   : " matches")
                                                          + ")"
                                                        : "");
        }
    }

    /**********************************************************************************************
     * Get the text to display in the dialog's header for a log search
     *
     * @return Text to display in the dialog's header for a log search
     *********************************************************************************************/
    private String getLogTitle()
    {
        String title;

        // Check if the log to search is the session log
        if (ccddMain.getSessionEventLog().equals(eventLog))
        {
            title = "Search Session Event Log";
        }
        // Not searching the session log
        else
        {
            title = "Search " + eventLog.getTitle();
        }

        return title;
    }
}
