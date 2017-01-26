/**
 * CFS Command & Data Dictionary search database tables and scripts dialog.
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.TABLE_BACK_COLOR;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;
import static CCDD.CcddConstants.TEXT_HIGHLIGHT_COLOR;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.JTextComponent;

import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventColumns;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.InternalTable.ScriptColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.SearchResultsColumnInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary search database tables, scripts, and event log
 * dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddSearchDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final CcddDbCommandHandler dbCommand;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddJTableHandler resultsTable;
    private final CcddEventLogDialog eventLog;

    // Components referenced from multiple methods
    private JTextField searchFld;
    private JCheckBox ignoreCaseCb;
    private JCheckBox dataTablesOnlyCb;
    private JTextPane resultsPane;

    // Search dialog type
    private final SearchDialogType searchDlgType;

    // Array to contain the search results
    private Object[][] resultsData;

    /**************************************************************************
     * Search database tables, scripts, and event log class constructor
     * 
     * @param ccddMain
     *            main class
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
    CcddSearchDialog(CcddMain ccddMain,
                     SearchDialogType searchType,
                     Long targetRow,
                     CcddEventLogDialog eventLog)
    {
        this.ccddMain = ccddMain;
        this.searchDlgType = searchType;
        this.eventLog = eventLog;

        // Create references to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();

        // initialize the search results table contents
        resultsData = new Object[0][0];

        // Create the database table search dialog
        initialize(targetRow);
    }

    /**************************************************************************
     * Search database tables and scripts class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param searchType
     *            search dialog type: TABLES or SCRIPTS
     *************************************************************************/
    CcddSearchDialog(CcddMain ccddMain, SearchDialogType searchType)
    {
        this(ccddMain, searchType, null, null);
    }

    /**************************************************************************
     * Create the database table or scripts search dialog
     * 
     * @param targetRow
     *            row index to match if this is an event log entry search on a
     *            table that displays only a single log entry; null otherwise
     *************************************************************************/
    private void initialize(final Long targetRow)
    {
        // Create a border for the dialog components
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING,
                                                                   0,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the search dialog labels and fields
        JLabel dlgLbl = new JLabel("Enter search text");
        dlgLbl.setFont(LABEL_FONT_BOLD);
        dialogPnl.add(dlgLbl, gbc);

        // Create the search field and add it to the dialog panel
        searchFld = new JTextField("", 20);
        searchFld.setFont(LABEL_FONT_PLAIN);
        searchFld.setEditable(true);
        searchFld.setForeground(Color.BLACK);
        searchFld.setBackground(Color.WHITE);
        searchFld.setBorder(border);
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING * 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        dialogPnl.add(searchFld, gbc);

        // Create a check box for ignoring the text case
        ignoreCaseCb = new JCheckBox("Ignore text case");
        ignoreCaseCb.setFont(LABEL_FONT_BOLD);
        ignoreCaseCb.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridy++;
        dialogPnl.add(ignoreCaseCb, gbc);

        // Check if this is a table search
        if (searchDlgType == SearchDialogType.TABLES)
        {
            // Create a check box for ignoring matches within the internal
            // tables
            dataTablesOnlyCb = new JCheckBox("Search data table cells only");
            dataTablesOnlyCb.setFont(LABEL_FONT_BOLD);
            dataTablesOnlyCb.setBorder(BorderFactory.createEmptyBorder());
            dataTablesOnlyCb.setToolTipText("Search only the cells in the data tables");
            gbc.gridy++;
            dialogPnl.add(dataTablesOnlyCb, gbc);
        }

        // Create the search dialog labels and fields
        JLabel resultLbl = new JLabel("Search results");
        resultLbl.setFont(LABEL_FONT_BOLD);
        resultLbl.setForeground(LABEL_TEXT_COLOR);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = 0;
        gbc.gridy++;
        dialogPnl.add(resultLbl, gbc);

        // Create the table to display the search results
        resultsTable = new CcddJTableHandler()
        {
            /******************************************************************
             * Allow multiple line display in the specified columns, depending
             * on search type
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return searchDlgType == SearchDialogType.TABLES
                       || (searchDlgType == SearchDialogType.LOG
                       && column == SearchResultsColumnInfo.CONTEXT.ordinal())
                       || (searchDlgType == SearchDialogType.SCRIPTS
                       && (column == SearchResultsColumnInfo.TARGET.ordinal()
                       || column == SearchResultsColumnInfo.CONTEXT.ordinal()));
            }

            /******************************************************************
             * Allow the specified column's cells to be displayed with the text
             * highlighted
             *****************************************************************/
            @Override
            protected boolean isColumnHighlight(int column)
            {
                return column == SearchResultsColumnInfo.CONTEXT.ordinal();
            }

            /******************************************************************
             * Load the data field data into the table and format the table
             * cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(resultsData,
                                            SearchResultsColumnInfo.getColumnNames(searchDlgType),
                                            null,
                                            new Integer[0],
                                            SearchResultsColumnInfo.getToolTips(searchDlgType),
                                            true,
                                            true,
                                            true,
                                            true);
            }

            /******************************************************************
             * Override the table layout so that extra width is apportioned
             * unequally between the columns when the table is resized
             *****************************************************************/
            @Override
            public void doLayout()
            {
                // Get a reference to the column being resized
                if (getTableHeader() != null
                    && getTableHeader().getResizingColumn() == null)
                {
                    // Get a reference to the event table's column model to
                    // shorten subsequent calls
                    TableColumnModel tcm = getColumnModel();

                    // Calculate the change in the search dialog's width
                    int delta = getParent().getWidth() - tcm.getTotalColumnWidth();

                    // Get the reference to the search results table columns
                    TableColumn tgtColumn = tcm.getColumn(SearchResultsColumnInfo.TARGET.ordinal());
                    TableColumn locColumn = tcm.getColumn(SearchResultsColumnInfo.LOCATION.ordinal());
                    TableColumn cntxtColumn = tcm.getColumn(SearchResultsColumnInfo.CONTEXT.ordinal());

                    // Set the columns' widths to its current width plus a
                    // percentage of the the extra width added to the dialog
                    // due to the resize
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
                // Check if highlighting is enabled and if the column allows
                // text highlighting
                if (isColumnHighlight(column))
                {
                    Pattern pattern;

                    // Create a highlighter painter
                    DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(isSelected
                                                                                                               ? Color.BLACK
                                                                                                               : TEXT_HIGHLIGHT_COLOR);

                    // Check if case is to be ignored
                    if (ignoreCaseCb.isSelected())
                    {
                        // Create the match pattern with case ignored
                        pattern = Pattern.compile(Pattern.quote(searchFld.getText()),
                                                  Pattern.CASE_INSENSITIVE);
                    }
                    // Only highlight matches with the same case
                    else
                    {
                        // Create the match pattern, preserving case
                        pattern = Pattern.compile(Pattern.quote(searchFld.getText()));
                    }

                    // Create the pattern matcher from the pattern
                    Matcher matcher = pattern.matcher(text);

                    // Find each match in the text string
                    while (matcher.find())
                    {
                        try
                        {
                            // Highlight the matching text. Adjust the
                            // highlight color to account for the cell
                            // selection highlighting so that the search text
                            // is easily readable
                            ((JTextComponent) component).getHighlighter().addHighlight(matcher.start(),
                                                                                       matcher.end(),
                                                                                       painter);
                        }
                        catch (BadLocationException ble)
                        {
                            // Ignore highlighting failure
                        }
                    }
                }
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(resultsTable);

        // Set up the field table parameters
        resultsTable.setFixedCharacteristics(scrollPane,
                                             false,
                                             ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                             TableSelectionMode.SELECT_BY_CELL,
                                             true,
                                             TABLE_BACK_COLOR,
                                             false,
                                             true,
                                             LABEL_FONT_PLAIN,
                                             null,
                                             true);

        // Define the panel to contain the table
        JPanel resultsTblPnl = new JPanel();
        resultsTblPnl.setLayout(new BoxLayout(resultsTblPnl, BoxLayout.X_AXIS));
        resultsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        resultsTblPnl.add(scrollPane);

        // Add the table to the dialog
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(resultsTblPnl, gbc);

        // Search database tables button
        JButton btnSearch = CcddButtonPanelHandler.createButton("Search",
                                                                SEARCH_ICON,
                                                                KeyEvent.VK_O,
                                                                "Search the project database");

        // Add a listener for the Search button
        btnSearch.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Search the database tables and display the results
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
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
                    switch (searchDlgType)
                    {
                        case TABLES:
                        case SCRIPTS:
                            // Search the database tables or scripts and
                            // display the
                            // results
                            searchTablesOrScripts();
                            break;

                        case LOG:
                            // Search the event log and display the results
                            searchEventLogFile(targetRow);
                            break;
                    }
                }
            }
        });

        // Print inconsistencies button
        JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                               PRINT_ICON,
                                                               KeyEvent.VK_P,
                                                               "Print the search results list");

        // Add a listener for the Print button
        btnPrint.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Print the search results list
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                printSearchResults();
            }
        });

        // Close search dialog button
        JButton btnCancel = CcddButtonPanelHandler.createButton("Close",
                                                                CLOSE_ICON,
                                                                KeyEvent.VK_C,
                                                                "Close the search dialog");

        // Add a listener for the Close button
        btnCancel.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Close the search dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                closeDialog(CANCEL_BUTTON);
            }
        });

        // Create a panel for the dialog buttons and add the buttons to the
        // panel
        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnSearch);
        buttonPnl.add(btnPrint);
        buttonPnl.add(btnCancel);

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
                title = "Search Event Log";
                break;
        }

        // Display the search dialog
        showOptionsDialog(ccddMain.getMainFrame(),
                          dialogPnl,
                          buttonPnl,
                          title,
                          true);
    }

    /**************************************************************************
     * Search for occurrences of a string in the tables or scripts
     *************************************************************************/
    private void searchTablesOrScripts()
    {
        // Initialize the list to contain the search results
        List<Object[]> resultsDataList = new ArrayList<Object[]>();

        // Set the search type based on the dialog type and, for a table
        // search, the state of the 'data tables only' check box
        String searchType = searchDlgType == SearchDialogType.TABLES
                                                                    ? (dataTablesOnlyCb.isSelected()
                                                                                                    ? SearchType.DATA.toString()
                                                                                                    : SearchType.ALL.toString())
                                                                    : SearchType.SCRIPT.toString();

        // Search the database for the text
        String[] hits = dbCommand.getList(DatabaseListCommand.SEARCH,
                                          new String[][] { {"_search_text_",
                                                            Pattern.quote(searchFld.getText())},
                                                          {"_case_insensitive_",
                                                           String.valueOf(ignoreCaseCb.isSelected())},
                                                          {"_selected_tables_",
                                                           searchType}},
                                          CcddSearchDialog.this);

        // Step through each table/column containing the search text
        for (String hit : hits)
        {
            // Split the found item into table, column, description, and
            // context
            String[] tblColDescAndCntxt = hit.split(TABLE_DESCRIPTION_SEPARATOR, 4);

            // Create a reference to the search result's column name to shorten
            // comparisons below
            String hitColumnName = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];

            // Check that the column isn't the primary key or row index,
            // and isn't in a stored script (unless the search scripts check
            // box is selected)
            if (!hitColumnName.equals(DefaultColumn.PRIMARY_KEY.getDbName())
                && !hitColumnName.equals(DefaultColumn.ROW_INDEX.getDbName()))
            {
                // Create references to the the remaining search result columns
                // to shorten comparisons below
                String hitTableName = tblColDescAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()];
                String hitTableComment = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];
                String hitContext = tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()];

                // Separate the table comment into the viewable table name and
                // table type, or for scripts the script name and description
                String[] nameAndType = hitTableComment.split(",");

                // Split the row in which the match is found into its separate
                // columns, accounting for quotes around the comma separated
                // column values (i.e., ignore commas within quotes)
                String[] columnValue = CcddUtilities.splitAndRemoveQuotes(hitContext);

                String target = null;
                String location = null;
                String context = null;

                // Check if this is a table search
                if (searchDlgType == SearchDialogType.TABLES)
                {
                    // The reference is to a prototype table
                    if (!hitTableName.startsWith(INTERNAL_TABLE_PREFIX))
                    {
                        // Get the table's type definition based on its table
                        // type
                        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(nameAndType[1]);

                        // Get the index of the column where the match exists
                        int colIndex = typeDefn.getColumnIndexByDbName(hitColumnName);

                        // Set the row number for the row location if the
                        // variable name or command name aren't present
                        String row = "row "
                                     + columnValue[DefaultColumn.ROW_INDEX.ordinal()];

                        // Check if this is a structure table
                        if (typeDefn.isStructure())
                        {
                            // Get the variable name column index
                            int index = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);

                            // Check that a variable name exists
                            if (index != -1 && !columnValue[index].isEmpty())
                            {
                                // Set the row location to the variable name
                                row = "variable '"
                                      + columnValue[index]
                                      + "'";
                            }
                        }
                        // Check if this is a command table
                        else if (typeDefn.isCommand())
                        {
                            // Get the command name column index
                            int index = typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME);

                            // Check that a command name exists
                            if (index != -1 && !columnValue[index].isEmpty())
                            {
                                // Set the row location to the command name
                                row = "command '"
                                      + columnValue[index]
                                      + "'";
                            }
                        }

                        // Set the search result table values
                        target = hitTableName;
                        location = "Column '"
                                   + typeDefn.getColumnNamesUser()[colIndex]
                                   + "', "
                                   + row;
                        context = columnValue[colIndex];
                    }
                    // Check if the match is in the custom values internal
                    // table
                    else if (hitTableName.equals(InternalTable.VALUES.getTableName()))
                    {
                        // Check if the match is in the value column
                        if (hitColumnName.equals(ValuesColumn.VALUE.getColumnName()))
                        {
                            // Get the column values from the row in which the
                            // match occurs
                            String tablePath = columnValue[ValuesColumn.TABLE_PATH.ordinal()];
                            String columnName = columnValue[ValuesColumn.COLUMN_NAME.ordinal()];
                            String value = columnValue[ValuesColumn.VALUE.ordinal()];

                            // Check if this is a table definition entry in the
                            // values table
                            if (columnName.isEmpty())
                            {
                                // Set the location
                                location = "Table description";
                            }
                            // Column value from a child table stored in the
                            // internal values table. Since this isn't a table
                            // description the reference must be to a structure
                            // table (for other table types the match would be
                            // in the table prototype)
                            else
                            {
                                // Set the location
                                location = "Column '"
                                           + columnName
                                           + "'";

                                // Initialize the variable name and get the
                                // index where the last variable name begins
                                int index = tablePath.lastIndexOf(',');

                                // Check if a variable name exists
                                if (index != -1)
                                {
                                    // Extract the variable from the path, then
                                    // remove it from the variable path
                                    location += "', variable '"
                                                + tablePath.substring(index + 1).replaceFirst("^.+\\.", "")
                                                + "'";
                                    tablePath = tablePath.substring(0, index).replaceFirst(",", ":");
                                }
                            }

                            // Set the search result table values
                            target = tablePath;
                            context = value;
                        }
                    }
                    // Check if the match is in the data types internal table
                    else if (hitTableName.equals(InternalTable.DATA_TYPES.getTableName()))
                    {
                        target = "Data type";
                        location = "Data type '"
                                   + CcddDataTypeHandler.getDataTypeName(columnValue[DataTypesColumn.USER_NAME.ordinal()],
                                                                         columnValue[DataTypesColumn.C_NAME.ordinal()])
                                   + "' ";

                        // Check if the match is with the user-defined name
                        if (hitColumnName.equals(DataTypesColumn.USER_NAME.getColumnName()))
                        {
                            location += "user-defined name";
                            context = columnValue[DataTypesColumn.USER_NAME.ordinal()];
                        }
                        // Check if the match is with the C-language name
                        else if (hitColumnName.equals(DataTypesColumn.C_NAME.getColumnName()))
                        {
                            location += "C-language name";
                            context = columnValue[DataTypesColumn.C_NAME.ordinal()];
                        }
                        // Check if the match is with the data type size
                        else if (hitColumnName.equals(DataTypesColumn.SIZE.getColumnName()))
                        {
                            location += "data type size";
                            context = columnValue[DataTypesColumn.SIZE.ordinal()];
                        }
                        // Check if the match is with the base type
                        else if (hitColumnName.equals(DataTypesColumn.BASE_TYPE.getColumnName()))
                        {
                            location += "base data type";
                            context = columnValue[DataTypesColumn.BASE_TYPE.ordinal()];
                        }
                    }
                    // Check if the match is in the groups table
                    else if (hitTableName.equals(InternalTable.GROUPS.getTableName()))
                    {
                        target = "Group";
                        location = "Group '"
                                   + columnValue[GroupsColumn.GROUP_NAME.ordinal()]
                                   + "' ";

                        // Check if the match is with the group name
                        if (hitColumnName.equals(GroupsColumn.GROUP_NAME.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[GroupsColumn.GROUP_NAME.ordinal()];
                        }
                        // The match is with a group definition or member
                        else
                        {
                            // Check if the column begins with a number; this
                            // is the group definition
                            if (columnValue[GroupsColumn.MEMBERS.ordinal()].matches("^\\d+"))
                            {
                                // Get the group description (remove the dummy
                                // number and comma that flags this as a group
                                // definition)
                                context = columnValue[GroupsColumn.MEMBERS.ordinal()].split(",")[1];

                                // Check if the description contains the search
                                // text (i.e., the dummy number and comma
                                // aren't part of the match)
                                if (context.toLowerCase().contains(searchFld.getText().toLowerCase()))
                                {
                                    location += "description";
                                }
                                // The match includes the dummy number and
                                // comma; ignore
                                else
                                {
                                    target = null;
                                }
                            }
                            // This is a group member
                            else
                            {
                                location += "member table";
                                context = columnValue[GroupsColumn.MEMBERS.ordinal()];
                            }
                        }
                    }
                    // Check if the match is in the fields internal table
                    else if (hitTableName.equals(InternalTable.FIELDS.getTableName()))
                    {
                        location = "Data field '"
                                   + columnValue[FieldsColumn.FIELD_NAME.ordinal()]
                                   + "' ";

                        // Check if this is a default data field
                        if ((columnValue[FieldsColumn.OWNER_NAME.ordinal()] + ":").startsWith(CcddFieldHandler.getFieldTypeName("")))
                        {
                            target = "Default data field";
                        }
                        // Check if this is a group data field
                        else if ((columnValue[FieldsColumn.OWNER_NAME.ordinal()] + ":").startsWith(CcddFieldHandler.getFieldGroupName("")))
                        {
                            target = "Group data field";
                        }
                        // Table data field
                        else
                        {
                            target = columnValue[FieldsColumn.OWNER_NAME.ordinal()].replaceFirst(",", ":");
                        }

                        // Check if the match is with the field owner name
                        if (hitColumnName.equals(FieldsColumn.OWNER_NAME.getColumnName()))
                        {
                            location += "owner (table or group)";
                            context = columnValue[FieldsColumn.OWNER_NAME.ordinal()];
                        }
                        // Check if the match is with the field name
                        else if (hitColumnName.equals(FieldsColumn.FIELD_NAME.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[FieldsColumn.FIELD_NAME.ordinal()];
                        }
                        // Check if the match is with the field description
                        else if (hitColumnName.equals(FieldsColumn.FIELD_DESC.getColumnName()))
                        {
                            location += "description";
                            context = columnValue[FieldsColumn.FIELD_DESC.ordinal()];
                        }
                        // Check if the match is with the field size
                        else if (hitColumnName.equals(FieldsColumn.FIELD_SIZE.getColumnName()))
                        {
                            location += "size";
                            context = columnValue[FieldsColumn.FIELD_SIZE.ordinal()];
                        }
                        // Check if the match is with the field input type
                        else if (hitColumnName.equals(FieldsColumn.FIELD_TYPE.getColumnName()))
                        {
                            location += "input type";
                            context = columnValue[FieldsColumn.FIELD_TYPE.ordinal()];
                        }
                        // Check if the match is with the field
                        // applicability
                        else if (hitColumnName.equals(FieldsColumn.FIELD_APPLICABILITY.getColumnName()))
                        {
                            location += "applicability";
                            context = columnValue[FieldsColumn.FIELD_APPLICABILITY.ordinal()];
                        }
                        // Check if the match is with the field value
                        else if (hitColumnName.equals(FieldsColumn.FIELD_VALUE.getColumnName()))
                        {
                            location += "value";
                            context = columnValue[FieldsColumn.FIELD_VALUE.ordinal()];
                        }
                        // Check if the match is with the field required flag
                        else if (hitColumnName.equals(FieldsColumn.FIELD_REQUIRED.getColumnName()))
                        {
                            location += "required flag";
                            context = columnValue[FieldsColumn.FIELD_REQUIRED.ordinal()];
                        }
                    }
                    // Check if the match is in the associations internal table
                    else if (hitTableName.equals(InternalTable.ASSOCIATIONS.getTableName()))
                    {
                        target = "Script association";
                        location = "Script '"
                                   + columnValue[AssociationsColumn.SCRIPT_FILE.ordinal()]
                                   + "' association ";

                        // Check if the match is with the script file path
                        // and/or name
                        if (hitColumnName.equals(AssociationsColumn.SCRIPT_FILE.getColumnName()))
                        {
                            location += "file path and name";
                            context = columnValue[AssociationsColumn.SCRIPT_FILE.ordinal()];
                        }
                        // The match is with a script association member
                        else
                        {
                            location += "member table";
                            context = columnValue[AssociationsColumn.MEMBERS.ordinal()];
                        }
                    }
                    // Check if the match is in the telemetry scheduler
                    // internal table
                    else if (hitTableName.equals(InternalTable.TLM_SCHEDULER.getTableName()))
                    {
                        target = "Telemetry message";
                        location = "Message '"
                                   + columnValue[TlmSchedulerColumn.MESSAGE_NAME.ordinal()]
                                   + "' ";

                        // Check if the match is with the message name
                        if (hitColumnName.equals(TlmSchedulerColumn.MESSAGE_NAME.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[TlmSchedulerColumn.MESSAGE_NAME.ordinal()];
                        }
                        // Check if the match is with the message rate name
                        else if (hitColumnName.equals(TlmSchedulerColumn.RATE_NAME.getColumnName()))
                        {
                            location += "rate name";
                            context = columnValue[TlmSchedulerColumn.RATE_NAME.ordinal()];
                        }
                        // Check if the match is with the message ID
                        else if (hitColumnName.equals(TlmSchedulerColumn.MESSAGE_ID.getColumnName()))
                        {
                            location += "ID";
                            context = columnValue[TlmSchedulerColumn.MESSAGE_ID.ordinal()];

                        }
                        // The match is with a message definition or member
                        else
                        {
                            context = columnValue[TlmSchedulerColumn.MEMBER.ordinal()];

                            // Check if the column begins with a number; this
                            // is the message definition
                            if (columnValue[TlmSchedulerColumn.MEMBER.ordinal()].matches("^\\d+"))
                            {
                                location += "rate and description";
                            }
                            // This is a message member
                            else
                            {
                                location += "member rate, table, and variable";
                            }
                        }
                    }
                    // Check if the match is in the links internal table
                    else if (hitTableName.equals(InternalTable.LINKS.getTableName()))
                    {
                        target = "Telemetry link";
                        location = "Link '"
                                   + columnValue[LinksColumn.LINK_NAME.ordinal()]
                                   + "' ";

                        // Check if the match is with the link name
                        if (hitColumnName.equals(LinksColumn.LINK_NAME.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[LinksColumn.LINK_NAME.ordinal()];
                        }
                        // Check if the match is with the link rate name
                        else if (hitColumnName.equals(LinksColumn.RATE_NAME.getColumnName()))
                        {
                            location += "rate name";
                            context = columnValue[LinksColumn.RATE_NAME.ordinal()];
                        }
                        // The match is with a link definition or member
                        else
                        {
                            context = columnValue[LinksColumn.MEMBER.ordinal()];

                            // Check if the column begins with a number; this
                            // is the link definition
                            if (columnValue[1].matches("^\\d+"))
                            {
                                location += "rate and description";
                            }
                            // This is a link member
                            else
                            {
                                location += "member table and variable";
                            }
                        }
                    }
                    // Check if the match is in the table types internal table
                    else if (hitTableName.equals(InternalTable.TABLE_TYPES.getTableName()))
                    {
                        target = "Table type";
                        location = "Table type '"
                                   + columnValue[TableTypesColumn.TYPE_NAME.ordinal()]
                                   + "' ";

                        // Check if the match is with the column name
                        if (hitColumnName.equals(TableTypesColumn.COLUMN_NAME_VISIBLE.getColumnName()))
                        {
                            location += "column name";
                            context = columnValue[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()];
                        }
                        // Check if the match is with the column description
                        else if (hitColumnName.equals(TableTypesColumn.COLUMN_DESCRIPTION.getColumnName()))
                        {
                            location += "column description";
                            context = columnValue[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()];
                        }
                        // Check if the match is with the column input type
                        else if (hitColumnName.equals(TableTypesColumn.INPUT_TYPE.getColumnName()))
                        {
                            location += "column input type";
                            context = columnValue[TableTypesColumn.INPUT_TYPE.ordinal()];
                        }
                        // Check if the match is with the column required flag
                        else if (hitColumnName.equals(TableTypesColumn.COLUMN_REQUIRED.getColumnName()))
                        {
                            location += "column required flag";
                            context = columnValue[TableTypesColumn.COLUMN_REQUIRED.ordinal()];
                        }
                        // Check if the match is with the row value unique flag
                        else if (hitColumnName.equals(TableTypesColumn.ROW_VALUE_UNIQUE.getColumnName()))
                        {
                            location += "row value unique flag";
                            context = columnValue[TableTypesColumn.ROW_VALUE_UNIQUE.ordinal()];
                        }
                        // Match is in one of the remaining table type columns
                        else
                        {
                            // Ignore this match
                            target = null;
                        }
                    }
                    // Check if the match is in the application scheduler
                    // internal table
                    else if (hitTableName.equals(InternalTable.APP_SCHEDULER.getTableName()))
                    {
                        target = "Scheduler";
                        location = "Application '"
                                   + columnValue[AppSchedulerColumn.TIME_SLOT.ordinal()]
                                   + "' ";

                        // Check if the match is with the application name
                        if (hitColumnName.equals(AppSchedulerColumn.TIME_SLOT.getColumnName()))
                        {
                            location += "name";
                            context = columnValue[AppSchedulerColumn.TIME_SLOT.ordinal()];
                        }
                        // The match is with a scheduler member
                        else
                        {
                            context = columnValue[AppSchedulerColumn.APP_INFO.ordinal()];
                            location += "member information";
                        }
                    }
                }
                // This is a script search and the match is in a stored script
                else
                {
                    // Set the search result table values
                    target = nameAndType[0];
                    location = columnValue[ScriptColumn.LINE_NUM.ordinal()];
                    context = columnValue[ScriptColumn.LINE_TEXT.ordinal()];
                }

                // Check if a search result exists
                if (target != null)
                {
                    // Add the search result to the list
                    resultsDataList.add(new Object[] {target,
                                                      location,
                                                      context.trim()});
                }
            }
        }

        // Display the search results
        displaySearchResults(resultsDataList);
    }

    /**************************************************************************
     * Search for occurrences of a string in the event log file (session log or
     * other log file)
     * 
     * @param targetRow
     *            row index to match if this is an event log entry search on a
     *            table that displays only a single log entry; null otherwise
     *************************************************************************/
    private void searchEventLogFile(Long targetRow)
    {
        Pattern pattern;

        // Initialize the list to contain the search results
        List<Object[]> resultsDataList = new ArrayList<Object[]>();

        // Check if case is to be ignored
        if (ignoreCaseCb.isSelected())
        {
            // Create the match pattern with case ignored
            pattern = Pattern.compile(Pattern.quote(searchFld.getText()),
                                      Pattern.CASE_INSENSITIVE);
        }
        // Only match if the same case
        else
        {
            // Create the match pattern, preserving case
            pattern = Pattern.compile(Pattern.quote(searchFld.getText()));
        }

        // Set up Charset and CharsetDecoder for ISO-8859-15
        Charset charset = Charset.forName("ISO-8859-15");
        CharsetDecoder decoder = charset.newDecoder();

        // Pattern used to detect separate lines
        Pattern linePattern = Pattern.compile(".*\r?\n");

        try
        {
            // Open a file stream on the event log file and then get a channel
            // from the stream
            FileInputStream fis = new FileInputStream(eventLog.getEventLogFile());
            FileChannel fc = fis.getChannel();

            // Get the file's size and then map it into memory
            MappedByteBuffer byteBuffer = fc.map(FileChannel.MapMode.READ_ONLY,
                                                 0,
                                                 fc.size());

            // Decode the file into a char buffer
            CharBuffer charBuffer = decoder.decode(byteBuffer);

            // Create the line and pattern matchers, then perform the search
            Matcher lineMatch = linePattern.matcher(charBuffer);

            long row = 1;

            // For each line in the file
            while (lineMatch.find())
            {
                // Check if no target row is provided ,or if one is that it
                // matches this log entry's row
                if (targetRow == null || row == targetRow)
                {
                    // Get the line from the file and strip any leading or
                    // trailing whitespace
                    String line = lineMatch.group().toString().trim();

                    // Break the input line into its separate columns
                    String[] parts = line.split("[|]", EventColumns.values().length - 1);

                    // Step through each log entry column
                    for (int column = 0; column < parts.length; column++)
                    {
                        // Create the pattern matcher from the pattern. Ignore
                        // any HTML tags in the log entry column text
                        Matcher matcher = pattern.matcher(CcddUtilities.removeHTMLTags(parts[column].toString()));

                        // Check if a match exists in the text string
                        if (matcher.find())
                        {
                            // Add the search result to the list
                            resultsDataList.add(new Object[] {row,
                                                              eventLog.getEventTable().getColumnName(column + 1),
                                                              parts[column]});
                        }
                    }

                    // Check if the end of the file has been reached or if this
                    // is a single log entry row search
                    if (lineMatch.end() == charBuffer.limit()
                        || targetRow != null)
                    {
                        // Exit the loop
                        break;
                    }
                }

                row++;
            }

            // Close the channel and the stream
            fc.close();
            fis.close();
        }
        catch (IOException ioe)
        {
            // Inform the user that an error occurred reading the log
            new CcddDialogHandler().showMessageDialog(this,
                                                      "<html><b>Cannot read event log file",
                                                      "Log Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        // Display the search results
        displaySearchResults(resultsDataList);
    }

    /**************************************************************************
     * Display the search results
     * 
     * @param resultsDataList
     *            list containing the search results to display
     *************************************************************************/
    private void displaySearchResults(List<Object[]> resultsDataList)
    {
        // Convert the results list to an array
        resultsData = resultsDataList.toArray(new Object[0][0]);

        // Sort the results by target, then by location, ignoring case
        Arrays.sort(resultsData, new Comparator<Object[]>()
        {
            /******************************************************************
             * Compare the target names of two search result rows. If the same
             * compare the locations. Move the tables to the top. Ignore case
             * when comparing
             *****************************************************************/
            @Override
            public int compare(Object[] entry1, Object[] entry2)
            {
                int result = 0;

                switch (searchDlgType)
                {
                    case TABLES:
                    case SCRIPTS:
                        // Compare the first column as strings, ignoring case
                        result = entry1[0].toString().toLowerCase().compareTo(entry2[0].toString().toLowerCase());
                        break;

                    case LOG:
                        // Compare the first column as integers
                        result = Long.valueOf(entry1[0].toString()).compareTo(Long.valueOf(entry2[0].toString()));
                        break;
                }

                // Check if the column values are the same
                if (result == 0)
                {
                    // Compare the second column
                    result = entry1[1].toString().toLowerCase().compareTo(entry2[1].toString().toLowerCase());
                }

                return result;
            }
        });

        // Display the results in the dialog search results table
        resultsTable.loadAndFormatData();
    }

    /**************************************************************************
     * Output the search results to the user-selected printer
     *************************************************************************/
    private void printSearchResults()
    {
        try
        {
            resultsPane.print(new MessageFormat("Project '"
                                                + dbControl.getDatabase()
                                                + " Inconsistencies"),
                              new MessageFormat("Page - {0}"),
                              true,
                              null,
                              null,
                              true);
        }
        catch (PrinterException pe)
        {
            // Inform the user that printing the search results failed
            new CcddDialogHandler().showMessageDialog(this,
                                                      "<html><b>Cannot print search results; cause '"
                                                          + pe.getMessage()
                                                          + "'",
                                                      "Print Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }
}
