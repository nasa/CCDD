/**
 * CFS Command and Data Dictionary custom Swing table handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.REPLACE_INDICATOR;

import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.ServiceUI;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesComponent.CellSelectionHandler;
import CCDD.CcddClassesComponent.ModifiableColor;
import CCDD.CcddClassesComponent.SelectedCell;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableInsertionPoint;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddUndoHandler.UndoableTableColumnModel;
import CCDD.CcddUndoHandler.UndoableTableModel;
import javax.swing.JDialog;

/**************************************************************************************************
 * CFS Command and Data Dictionary custom Swing table handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public abstract class CcddJTableHandler extends JTable
{
    // Class references
    private final CcddJTableHandler table;
    private final CcddUndoManager undoManager;
    private final CcddUndoHandler undoHandler;

    // Table model
    private UndoableTableModel tableModel;

    // Table scroll pane
    private JScrollPane scrollPane;

    // Column header tool tip text
    private String[] toolTips;

    // Table cell selection mode
    private TableSelectionMode cellSelection;

    // Flag that indicates if row should remain highlighted when the table loses focus
    private boolean selectWithoutFocus;

    // Flag that indicates if the grid lines between cells is visible
    private boolean showGrid;

    // Flag that indicates if the rows can be sorted by selecting a column header
    private boolean allowSort;

    // Table cell border with padding
    private final Border cellBorder;

    // Table cell border while editing
    private final Border editBorder;

    // Minimum pixel widths needed to display the column headers and column contents
    private int[] minHeaderWidth;
    private int[] minDataWidth;

    // Table cell font
    private Font cellFont;

    // Array containing the table column names
    private String[] columnNames;

    // List containing the column information for any columns removed from the view
    private List<TableColumn> hiddenColumns;

    // Number of rows to display initially
    private final int initialViewableRows;

    // Flag that indicates that a table model data update is in progress. Table change events are
    // generated during a model data update; until the update completes the table characteristics
    // can be incorrect (e.g., column count)
    private boolean isReloadData;

    // Flag that indicates that a table layout change is in progress
    private boolean inLayout;

    // Lists to contain row indices and colors for rows that are displayed in special colors
    private List<Integer> rowColorIndex;
    private List<Color> rowColor;

    // Cell selection container
    private final CellSelectionHandler selectedCells;

    // Flags indicating the state of the control and shift modifier keys
    private boolean controlKey;
    private boolean shiftKey;

    // Row and column indices, in view coordinates, of the cell with the focus
    private int focusRow;
    private int focusColumn;

    // Flag to indicate is the contents of the last cell edited is valid
    private boolean lastCellValid;

    // Start and end position of the selected text within the last text cell edited
    private int lastSelectionStart;
    private int lastSelectionEnd;

    // Mouse listener used to resize a column when the right column header border is double clicked
    private ResizeColumnListener resizeColumnListener;

    // Sort keys for the last sort via column header. Used to restore the sort order after
    // reloading the table data
    private List<? extends SortKey> lastSortKeys;

    // Pattern to use when searching cells for matching text
    private Pattern pattern;

    // Place holder text used when pasting data from the clipboard
    private static String EMBEDDED_QUOTE = "@~quote~@";
    private static String EMBEDDED_NEW_LINE = "@~newline~@";

    /**********************************************************************************************
     * Custom Swing table handler constructor
     *
     * @param initialViewableRows
     *            initial number of rows to size the table
     *********************************************************************************************/
    CcddJTableHandler(int initialViewableRows)
    {
        this.initialViewableRows = initialViewableRows;

        // Store a reference to the table
        table = this;

        // Add an undo edit manager and add it as a listener for undo/redo changes
        undoManager = new CcddUndoManager()
        {
            /**************************************************************************************
             * Perform any steps necessary following a table content change
             *************************************************************************************/
            @Override
            protected void ownerHasChanged()
            {
                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /******************************************************************************
                     * Execute after all pending Swing events are finished
                     *****************************************************************************/
                    @Override
                    public void run()
                    {
                        // Perform any table-specific steps following the table content change
                        processTableContentChange();
                    }
                });

                // Update the row heights for the visible rows
                tableChanged(null);
            }
        };

        // Create the undo handler for the components with undoable actions
        undoHandler = new CcddUndoHandler(undoManager);
        undoHandler.setTable(table);

        // Set the table column model so that column changes can be undone/redone
        setColumnModel(undoHandler.new UndoableTableColumnModel());

        // Create the borders used to pad the table cells
        cellBorder = BorderFactory.createEmptyBorder(ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing(),
                                                     ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing(),
                                                     ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing(),
                                                     ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing());
        editBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ModifiableColorInfo.FOCUS_BACK.getColor()),
                                                        BorderFactory.createEmptyBorder(ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing() - 1,
                                                                                        ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing() - 1,
                                                                                        ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing() - 3,
                                                                                        ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing() - 1));

        // Create the cell selection container
        selectedCells = new CellSelectionHandler();
        focusRow = -1;
        focusColumn = -1;

        controlKey = false;
        shiftKey = false;
        lastCellValid = true;
        lastSelectionStart = -1;
        lastSelectionEnd = -1;
        pattern = null;
        inLayout = false;
    }

    /**********************************************************************************************
     * Custom Swing table handler constructor
     *********************************************************************************************/
    CcddJTableHandler()
    {
        // Set the number of rows to initially display to the default value
        this(ModifiableSizeInfo.INIT_VIEWABLE_TABLE_ROWS.getSize());
    }

    /**********************************************************************************************
     * Get the start position of the selected text in the last cell edited
     *
     * @return Start position of the selected text in the last cell edited; -1 if no text cell
     *         being edited when the method is called
     *********************************************************************************************/
    protected int getLastSelectionStart()
    {
        return lastSelectionStart;
    }

    /**********************************************************************************************
     * Get the end position of the selected text in the last cell edited
     *
     * @return End position of the selected text in the last cell edited; -1 if no text cell being
     *         edited when the method is called
     *********************************************************************************************/
    protected int getLastSelectionEnd()
    {
        return lastSelectionEnd;
    }

    /**********************************************************************************************
     * Override the method so that the a selected cell's row and column coordinates can be stored
     *********************************************************************************************/
    @Override
    public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend)
    {
        // Get the cell's current selection state
        boolean isSel = isCellSelected(rowIndex, columnIndex);

        // Update cell's the selection state
        super.changeSelection(rowIndex, columnIndex, toggle, extend);

        // Set this cell as having the focus
        setFocusCell(rowIndex, columnIndex);

        // Check if the cell selection state should be toggled
        if (toggle)
        {
            // Check if the cell isn't already selected
            if (!isSel)
            {
                // Add the cell's coordinates to the list of selected cells
                selectedCells.add(rowIndex, columnIndex);
            }
            // The cell is already selected
            else
            {
                // Remove the cell's coordinates from the list of selected cells
                selectedCells.remove(rowIndex, columnIndex);

                // Indicate that no cell has the focus
                setFocusCell(-1, -1);
            }
        }
        // The selection state should not be toggled
        else
        {
            // Check if the selection should be extended
            if (extend)
            {
                // Check if the control key (and no other modifier key) is pressed. This indicates
                // that the single cell should be added
                if (controlKey)
                {
                    // Add the cell's coordinates to the list of selected cells
                    selectedCells.add(rowIndex, columnIndex);
                }
                // The control key isn't pressed; add the range of cells to the selected cell list
                else
                {
                    // Clear any currently selected cells from the list
                    selectedCells.clear();

                    // Get the first selected row and column
                    int selRow = getSelectedRow();
                    int selColumn = getSelectedColumn();

                    // Row and column start and end selection indices
                    int startRow;
                    int endRow;
                    int startColumn;
                    int endColumn;

                    // Check if the previously selected row is the same or before the newly
                    // selected row
                    if (selRow <= rowIndex)
                    {
                        startRow = selRow;
                        endRow = rowIndex;
                    }
                    // The previously selected row is after the newly selected row
                    else
                    {
                        startRow = rowIndex;
                        endRow = selRow;
                    }

                    // Check if the previously selected column is the same or before the newly
                    // selected column
                    if (selColumn <= columnIndex)
                    {
                        startColumn = selColumn;
                        endColumn = columnIndex;
                    }
                    // The previously selected column is after the newly selected column
                    else
                    {
                        startColumn = columnIndex;
                        endColumn = selColumn;
                    }

                    // Step through the selected row(s)
                    for (int row = startRow; row <= endRow; row++)
                    {
                        // Step through the selected column(s)
                        for (int column = startColumn; column <= endColumn; column++)
                        {
                            // Add the cell to the selected cells list
                            selectedCells.add(row, column);
                        }
                    }
                }
            }
            // The selection shouldn't be extended; i.e., start a new selection
            else
            {
                // Replace any coordinates in the selected cell list with the currently selected
                // cell
                selectedCells.reset(rowIndex, columnIndex);

                // Force the table to be redrawn to prevent deselected cells from remaining
                // highlighted
                repaint();
            }
        }
    }

    /**********************************************************************************************
     * Override the method so that the cell selection set values can be checked to determine if a
     * cell is selected
     *********************************************************************************************/
    @Override
    public boolean isCellSelected(int row, int column)
    {
        boolean isSelected;

        // Check if one or no cell is currently selected, and that the control or shift key is not
        // pressed
        if (selectedCells.isOneOrNone() && !controlKey && !shiftKey)
        {
            // Use the default method to indicate cell selection
            isSelected = super.isCellSelected(row, column);
        }
        // More that one cell is currently selected, or the shift or control key is pressed
        else
        {
            // Set the flag indicating the cell is selected if the row and column coordinates match
            // that of one of the selected cells
            isSelected = selectedCells.contains(row, column);
        }

        return isSelected;
    }

    /**********************************************************************************************
     * Override the method so that the all of the cells are deselected
     *********************************************************************************************/
    @Override
    public void clearSelection()
    {
        selectedCells.clear();
        super.clearSelection();
    }

    /**********************************************************************************************
     * Select all of the cells in the specified range
     *
     * @param startRow
     *            row index in view coordinates at which to begin selection (inclusive)
     *
     * @param endRow
     *            row index in view coordinates at which to end selection (inclusive)
     *
     * @param startColumn
     *            column index in view coordinates at which to begin selection (inclusive)
     *
     * @param endColumn
     *            column index in view coordinates at which to end selection (inclusive)
     *********************************************************************************************/
    protected void setSelectedCells(int startRow, int endRow, int startColumn, int endColumn)
    {
        // Remove any currently selected cells
        selectedCells.clear();

        // Check if the start row is after the end row
        if (startRow > endRow)
        {
            // Swap the start and end rows
            int tempRow = startRow;
            startRow = endRow;
            endRow = tempRow;
        }

        // Check if the start column is after the end column
        if (startColumn > endColumn)
        {
            // Swap the start and end columns
            int tempColumn = startColumn;
            startColumn = endColumn;
            endColumn = tempColumn;
        }

        // Step through each row in the range
        for (int row = startRow; row <= endRow; row++)
        {
            // Step through each column in the range
            for (int column = startColumn; column <= endColumn; column++)
            {
                // Select the cell
                selectedCells.add(row, column);
            }
        }
    }

    /**********************************************************************************************
     * Adjust the selected cells
     *
     * @param rowDelta
     *            -1 if the cells were moved up, +1 if the cells were moved down, 0 if the cell row
     *            position is unchanged
     *
     * @param columnDelta
     *            -1 if the cells were moved left, +1 if the cells were moved right, 0 if the cell
     *            column position is unchanged
     *********************************************************************************************/
    private void adjustSelectedCells(int rowDelta, int columnDelta)
    {
        // Step through each selected cell
        for (SelectedCell cell : selectedCells.getSelectedCells())
        {
            // Adjust the selection coordinates based on the supplied delta values
            cell.setRow(cell.getRow() + rowDelta);
            cell.setColumn(cell.getColumn() + columnDelta);
        }

        // Adjust the cell focus coordinates
        focusRow += rowDelta;
        focusColumn += columnDelta;
    }

    /**********************************************************************************************
     * Set the cell with the focus
     *
     * @param row
     *            cell row in view coordinates
     *
     * @param column
     *            cell column in view coordinates
     *********************************************************************************************/
    private void setFocusCell(int row, int column)
    {
        focusRow = row;
        focusColumn = column;

        // Since the focus has changed reset the flag that indicates if the last edited cell's
        // contents was invalid
        lastCellValid = true;
    }

    /**********************************************************************************************
     * Determine if the cell specified by the supplied coordinates has the focus
     *
     * @param row
     *            cell row in view coordinates
     *
     * @param column
     *            cell column in view coordinates
     *
     * @return true if the cell has the focus
     *********************************************************************************************/
    private boolean isFocusCell(int row, int column)
    {
        return row == focusRow && column == focusColumn;
    }

    /**********************************************************************************************
     * Return the viewport of the scroll pane in which the table resides
     *
     * @return The table's scroll pane viewport (null if the table isn't in a scroll pane)
     *********************************************************************************************/
    protected JViewport getViewport()
    {
        return scrollPane.getViewport();
    }

    /**********************************************************************************************
     * Get the undo manager
     *
     * @return Reference to the undo manager
     *********************************************************************************************/
    protected CcddUndoManager getUndoManager()
    {
        return undoManager;
    }

    /**********************************************************************************************
     * Get the undo handler
     *
     * @return Reference to the undo handler
     *********************************************************************************************/
    protected CcddUndoHandler getUndoHandler()
    {
        return undoHandler;
    }

    /**********************************************************************************************
     * Get the cell values from the table model in a list. Since the values are based on the model
     * instead of the view, repositioning a column doesn't result in a change to the returned
     * array. Empty rows are included or excluded depending on the input flag
     *
     * @param excludeEmptyRows
     *            true if rows containing only empty cells are to be ignored
     *
     * @return List containing the table model data
     *********************************************************************************************/
    protected List<Object[]> getTableDataList(boolean excludeEmptyRows)
    {
        // Create storage for the data
        List<Object[]> tableData = new ArrayList<Object[]>();

        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Check if empty rows are to be skipped
            if (excludeEmptyRows)
            {
                // Skip rows in the table that have no data
                row = getNextPopulatedRowNumber(row);
            }

            // Check if the end of the table hasn't been reached
            if (row < tableModel.getRowCount())
            {
                Object[] rowData = new Object[tableModel.getColumnCount()];

                // Step through each column in the table
                for (int column = 0; column < tableModel.getColumnCount(); column++)
                {
                    // Store the table value in the data array
                    rowData[column] = tableModel.getValueAt(row, column);
                }

                // Store the data array in the list
                tableData.add(rowData);
            }
        }

        return tableData;
    }

    /**********************************************************************************************
     * Get the cell values from the table model in an array. Since the values are based on the
     * model instead of the view, repositioning a column doesn't result in a change to the returned
     * array. Empty rows are ignored
     *
     * @param excludeEmptyRows
     *            true if rows containing only empty cells are to be ignored
     *
     * @return Array containing the table model data
     *********************************************************************************************/
    protected Object[][] getTableData(boolean excludeEmptyRows)
    {
        return getTableDataList(excludeEmptyRows).toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Get the next non-empty table row number starting at the supplied index. Rows are empty if
     * the cell values match that returned by the getEmptyRow() method. Hidden columns are ignored
     *
     * @param tableRow
     *            next table row to check for data, model coordinates
     *
     * @return The next row number containing data
     *********************************************************************************************/
    protected int getNextPopulatedRowNumber(int tableRow)
    {
        boolean hasData = false;

        // Get an empty row for the table for comparison purposes
        Object[] emptyRow = getEmptyRow();

        // Check if the row's cells are empty, and that the end of the table hasn't been reached
        while (!hasData && tableRow < tableModel.getRowCount())
        {
            // Step through each column in the row
            for (int column = 0; column < tableModel.getColumnCount(); column++)
            {
                // Check if the column is visible and if the cell contents doesn't match the
                // default value (usually empty, though other default values, (e.g., for check
                // boxes) are possible)
                if (convertColumnIndexToView(column) != -1
                    && !emptyRow[column].equals(tableModel.getValueAt(tableRow, column)))
                {
                    // Set the flag indicating data exists
                    hasData = true;
                    break;
                }
            }

            // Empty row; increment the index to ignore this row
            if (!hasData)
            {
                tableRow++;
            }
        }

        return tableRow;
    }

    /**********************************************************************************************
     * Assign OIDs to newly added table rows. Some tables use OIDs, when building the table updates
     * used to generate the SQL commands to adjust the database, to match up a row that has had one
     * or more column values or its position in the table changed with the same row prior to the
     * change(s). New rows are initialized with a blank OID (the OID is assigned when the table is
     * written to the database). If a subsequent table store operation is performed then rows with
     * blank OIDs can't be matched, so assigning a temporary OID allows matching.
     *
     * @param tempOID
     *            Starting number for the temporary OID. This should be negative so as not to
     *            duplicate an actual OIDs in another table row
     *
     * @param oidColumn
     *            table column containing the OID, model coordinates
     *
     * @return The updated temporary OID value
     *********************************************************************************************/
    protected int assignOIDsToNewRows(int tempOID, int oidColumn)
    {
        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Skip rows in the table that have no data
            row = getNextPopulatedRowNumber(row);

            // Check if the end of the table hasn't been reached and the OID is blank (i.e., this
            // is a newly added row)
            if (row < tableModel.getRowCount()
                && tableModel.getValueAt(row, oidColumn).toString().isEmpty())
            {
                // Provide a temporary OID value so that the row can be matched when building
                // updates
                tableModel.setValueAt(String.valueOf(tempOID), row, oidColumn, false);
                tempOID--;
            }
        }

        return tempOID;
    }

    /**********************************************************************************************
     * Determine if any changes have been made compared to the most recently committed table data
     *
     * @param previousData
     *            current database values for the table
     *
     * @return true if any cell in the table has been changed
     *********************************************************************************************/
    protected boolean isTableChanged(Object[][] previousData)
    {
        return isTableChanged(previousData, null);
    }

    /**********************************************************************************************
     * Determine if any changes have been made compared to the most recently committed table data,
     * ignoring the specified columns
     *
     * @param previousData
     *            current database values for the table
     *
     * @param ignoreColumns
     *            list containing indices of columns to ignore when checking for changes; null or
     *            an empty list if no columns are to be ignored
     *
     * @return true if any cell in the table has been changed
     *********************************************************************************************/
    protected boolean isTableChanged(Object[][] previousData, List<Integer> ignoreColumns)
    {
        // Get the data for the type as it exists in the database and the table editor
        Object[][] currentData = getTableData(true);

        // Set the change flag if the number of rows has changed
        boolean hasChanges = previousData.length != currentData.length;

        // Check if the previous and current data size is the same
        if (!hasChanges)
        {
            // Step through each row in the table
            for (int row = 0; row < currentData.length && !hasChanges; row++)
            {
                // Step through each column in the table
                for (int column = 0; column < currentData[row].length && !hasChanges; column++)
                {
                    // Check if the table value doesn't match the database value and the column
                    // isn't in the list of those to ignore (if present)
                    if (!currentData[row][column].equals(previousData[row][column])
                        && (ignoreColumns == null || !ignoreColumns.contains(column)))
                    {
                        // Set the flag indicating a change exists
                        hasChanges = true;
                    }
                }
            }
        }

        return hasChanges;
    }

    /**********************************************************************************************
     * Placeholder for method to allow resizing of the table columns. Override this method to
     * prevent resizing one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to allow resizing of the specified column; false otherwise
     *********************************************************************************************/
    protected boolean isColumnResizable(int column)
    {
        return true;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a column's cells can be displayed as multiple lines.
     * Override this method to allow multiple line display of one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to allow display of multiple lines in the specified column's cells; false
     *         otherwise
     *********************************************************************************************/
    protected boolean isColumnMultiLine(int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a column's cells can be displayed with HTML
     * formatting and multiple lines. Override this method to allow HTML-formatted, multiple line
     * display of one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to allow display of HTML-formatted, multiple lines in the specified column's
     *         cells; false otherwise
     *********************************************************************************************/
    protected boolean isColumnHTML(int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a column's cells can be displayed with the text
     * highlighted. Override this method to allow highlighting of the text in one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to allow display of highlighted text in the specified column's cells; false
     *         otherwise
     *********************************************************************************************/
    protected boolean isColumnHighlight(int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a column should be hidden. Override this method to
     * allow hiding one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to hide the specified column; false otherwise
     *********************************************************************************************/
    protected boolean isColumnHidden(int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a column should be displayed as a check box. Override
     * this method to allow boolean input for one or more columns
     *
     * @param column
     *            column index; model coordinate
     *
     * @return true to display the specified column as a check box; false otherwise
     *********************************************************************************************/
    protected boolean isColumnBoolean(int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a table cell can be edited. Override this method to
     * allow editing the contents of the cell at the specified row and column
     *
     * @param row
     *            table row index in view coordinates
     *
     * @param column
     *            table column index in view coordinates
     *
     * @return true if the cell contents can be edited; false otherwise
     *********************************************************************************************/
    @Override
    public boolean isCellEditable(int row, int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a table cell can be changed. Override this method to
     * allow changing the contents of the cell at the specified row and column
     *
     * @param rowData
     *            array containing the table row data
     *
     * @param row
     *            table row index in model coordinates
     *
     * @param column
     *            table column index in model coordinates
     *
     * @return true if the cell contents can be changed; false otherwise
     *********************************************************************************************/
    protected boolean isDataAlterable(Object[] rowData, int row, int column)
    {
        return false;
    }

    /**********************************************************************************************
     * Placeholder for method to determine if a table cell can be blanked. Override this method to
     * allow deleting the contents of the cell at the specified row and column
     *
     * @param row
     *            table row index in view coordinates
     *
     * @param column
     *            table column index in view coordinates
     *
     * @return true if the cell contents can be deleted; false otherwise
     *********************************************************************************************/
    protected boolean isCellBlankable(int row, int column)
    {
        return true;
    }

    /**********************************************************************************************
     * Placeholder for method to validate changes to the editable table cells
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
     *            true if this is one of multiple cells to be entered and checked; false if only a
     *            single input is being entered
     *
     * @return true to indicate that subsequent errors should be displayed; false if subsequent
     *         errors should not be displayed; null if the operation should be canceled
     *********************************************************************************************/
    protected Boolean validateCellContent(List<Object[]> tableData,
                                          int row,
                                          int column,
                                          Object oldValue,
                                          Object newValue,
                                          Boolean showMessage,
                                          boolean isMultiple)
    {
        return true;
    }

    /**********************************************************************************************
     * Get the status of the flag that indicates if the contents of the last cell edited is valid
     *
     * @return true if the contents of the last cell edited is valid; false if the contents is
     *         invalid
     *********************************************************************************************/
    protected boolean isLastCellValid()
    {
        // Check if a cell in the table is currently being edited
        if (getCellEditor() != null)
        {
            // Force editing to end
            getCellEditor().stopCellEditing();
        }

        // Check if the contents of the last cell edited in the specified table is validated
        return lastCellValid;
    }

    /**********************************************************************************************
     * Set the status of the flag that indicates if the contents of the last cell edited is valid
     *
     * @param isValid
     *            true to reset the flag; false to indicate the last cell's contents is invalid
     *********************************************************************************************/
    protected void setLastCellValid(boolean isValid)
    {
        lastCellValid = isValid;
    }

    /**********************************************************************************************
     * Placeholder for method to load and format the table data
     *********************************************************************************************/
    protected abstract void loadAndFormatData();

    /**********************************************************************************************
     * Get the default table header object
     *********************************************************************************************/
    @Override
    protected JTableHeader createDefaultTableHeader()
    {
        return new JTableHeader(columnModel)
        {
            /**************************************************************************************
             * Provide the tool tip text for the column headers
             *************************************************************************************/
            @Override
            public String getToolTipText(MouseEvent me)
            {
                String text = null;

                // Get the index of the column underneath the mouse pointer
                int column = columnModel.getColumn(columnModel.getColumnIndexAtX(me.getPoint().x)).getModelIndex();

                // Check if tool tip exists
                if (toolTips != null
                    && column < toolTips.length
                    && toolTips[column] != null
                    && !toolTips[column].isEmpty())
                {
                    // Get the tool tip for the column header
                    text = toolTips[column];
                }

                return text;
            }

            /**************************************************************************************
             * Override the header preferred size to add the vertical padding
             *************************************************************************************/
            @Override
            public Dimension getPreferredSize()
            {
                // Get the header's preferred size and add the vertical padding
                Dimension headerSize = super.getPreferredSize();
                headerSize.height += ModifiableSpacingInfo.HEADER_VERTICAL_PADDING.getSpacing();

                return headerSize;
            }

            @Override
            /**************************************************************************************
             * Override the column drag to end the edit sequence
             *************************************************************************************/
            public void setDraggedColumn(TableColumn column)
            {
                // Set the flag indicating if the column move sequence is complete
                boolean finished = getDraggedColumn() != null && column == null;

                // Perform the column drag operation
                super.setDraggedColumn(column);

                // Check if the column drag operation is complete
                if (finished)
                {
                    // End the edit sequence and flag that a change occurred
                    undoManager.endEditSequence();
                    undoManager.ownerHasChanged();
                }
            }
        };
    }

    /**********************************************************************************************
     * Format the tool tip text using HTML to wrap at a specified number of characters
     *********************************************************************************************/
    private void formatToolTipText()
    {
        // Check if any tool tip text exists
        if (toolTips != null)
        {
            // Step through each tool tip
            for (int index = 0; index < toolTips.length; index++)
            {
                // Wrap the text at the specified number of characters
                toolTips[index] = CcddUtilities.wrapText(toolTips[index],
                                                         ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize());
            }
        }
    }

    /**********************************************************************************************
     * Set common table parameters and characteristics. These only need to be set when the table is
     * initially created and do not require to be updated if the table is changed while it is
     * visible
     *
     * @param scrollPane
     *            the scroll pane in which the table resides. Set to null if the table isn't in a
     *            scroll pane
     *
     * @param isRowsAlterable
     *            true if the table's rows are allowed to be changed (e.g., inserted, deleted,
     *            moved) via keyboard commands from the user
     *
     * @param intervalSelection
     *            ListSelectionModel selection mode used by the row and column selection models
     *
     * @param cellSelection
     *            table cell selection mode that determines the cell or cells selected when a cell
     *            has focus: SELECT_BY_ROW to select the entire row occupied the cell with focus;
     *            SELECT_BY_COLUMN to select the entire column containing the cell with focus;
     *            SELECT_BY_CELL to select only the cell with focus
     *
     * @param columnDragAllowed
     *            true if a column can be positioned by selecting the header via the mouse and
     *            dragging the column
     *
     * @param background
     *            table background color (when row not selected)
     *
     * @param selectWithoutFocus
     *            true to ignore if the table has focus when determining the row colors for
     *            selected rows
     *
     * @param allowUndo
     *            true to allow changes to the table to be undone and redone
     *
     * @param cellFont
     *            font to use for displaying the cell contents
     *
     * @param allowSort
     *            true to enable the rows to be sorted by selecting a column header; false to
     *            disable sorting
     ********************************************************************************************/
    protected void setFixedCharacteristics(final JScrollPane scrollPane,
                                           boolean isRowsAlterable,
                                           int intervalSelection,
                                           TableSelectionMode cellSelection,
                                           boolean columnDragAllowed,
                                           ModifiableColor background,
                                           boolean selectWithoutFocus,
                                           boolean allowUndo,
                                           Font cellFont,
                                           boolean allowSort)
    {
        // Disable storage of edit operations during table creation
        undoHandler.setAllowUndo(false);

        // Set the table's scroll pane
        this.scrollPane = scrollPane;

        // Set the table's non-selected background color
        setBackground(background);

        // Set to true to keep cell(s) highlighted when the table loses focus
        this.selectWithoutFocus = selectWithoutFocus;

        // Set the cell content font
        this.cellFont = cellFont;

        // Set to true to allow the rows to be sorted by selecting the column header
        this.allowSort = allowSort;

        // Initialize the special row color lists. These lists remain empty if no rows are assigned
        // special colors
        rowColorIndex = new ArrayList<Integer>();
        rowColor = new ArrayList<Color>();

        // Set the selection mode (single, contiguous, or multiple)
        setSelectionMode(intervalSelection);

        // Set row and column selection modes. If both are true then selection is by single cell
        this.cellSelection = cellSelection;
        setRowSelectionAllowed(cellSelection == TableSelectionMode.SELECT_BY_ROW
                               || cellSelection == TableSelectionMode.SELECT_BY_CELL);
        setColumnSelectionAllowed(cellSelection == TableSelectionMode.SELECT_BY_COLUMN
                                  || cellSelection == TableSelectionMode.SELECT_BY_CELL);

        // Set if a column can be moved by selecting the header with the mouse and dragging it
        getTableHeader().setReorderingAllowed(columnDragAllowed);

        // Remove the table border
        setBorder(BorderFactory.createEmptyBorder());

        // Set the focus to a cell if the keyboard is used to select it. Needed under some
        // circumstances to make text cursor appear in table cell when editing
        setSurrendersFocusOnKeystroke(true);

        // Set so that the columns aren't automatically resized when the table is resized; this is
        // handled manually below
        setAutoResizeMode(AUTO_RESIZE_OFF);

        // Set the font for the table cells
        setFont(cellFont);

        // Replace the table's header mouse listener with a version that captures double clicks on
        // the column header borders in order to implement automatic resizing
        resizeColumnListener = new ResizeColumnListener();

        // Listen for changes made by the user to the table's cells
        new TableCellListener();

        // Change TAB/SHIFT-TAB behavior so that focus jumps between the tables and the buttons
        setTableKeyboardTraversal();

        // Create the table model. The data and column headers are added later in case these need
        // to be adjusted
        tableModel = undoHandler.new UndoableTableModel()
        {
            /**************************************************************************************
             * Override the cell clean-up method
             *************************************************************************************/
            @Override
            protected Object cleanUpCellValue(Object value, int row, int column)
            {
                return table.cleanUpCellValue(value, row, column);
            }
        };

        setModel(tableModel);

        // Exit the cell's editor, if active, when the cell loses focus
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Add a listener for table focus changes
        addFocusListener(new FocusListener()
        {
            /**************************************************************************************
             * Handle loss of keyboard focus for the table
             *************************************************************************************/
            @Override
            public void focusLost(FocusEvent fe)
            {
                // Force a repaint so that any highlighted rows are unhighlighted when the table
                // loses focus
                repaint();
            }

            /**************************************************************************************
             * Handle gain of keyboard focus for the table
             *************************************************************************************/
            @Override
            public void focusGained(FocusEvent fe)
            {
                // Force a repaint so that all cells are highlighted in a row when the table row
                // gains focus
                repaint();
            }
        });

        // Row height, used to set the viewport size, needs to be determined prior to loading the
        // data, which can alter the height returned; and loading the data must be done prior to
        // setting the viewport size in order for the initial viewport size to be set correctly
        int rowHeight = getRowHeight();

        // Load the table data and format the table cells
        loadAndFormatData();

        // Set the initial number of rows to display in the table
        setPreferredScrollableViewportSize(new Dimension(getPreferredSize().width,
                                                         initialViewableRows
                                                                                   * (rowHeight
                                                                                      + getRowMargin() * 2
                                                                                      + 4)));

        // Make the table fill the dialog
        setFillsViewportHeight(true);

        // Set the keys that initiate table cell editing and the keys to be ignored when initiating
        // editing. If the table cells and rows are editable then set the keys for these actions
        setEditorKeys(isRowsAlterable);

        // Add a listener for scroll bar slider position changes
        scrollPane.getViewport().addChangeListener(new ChangeListener()
        {
            /**************************************************************************************
             * Handle a scroll bar slider position change for the table
             *************************************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                // Update the row heights for the visible rows
                tableChanged(null);
            }
        });

        // Set to true to allow changes to the table to be undone/redone
        undoHandler.setAllowUndo(allowUndo);
    }

    /**********************************************************************************************
     * Clean-up of the cell value by removing any leading and trailing white space characters
     *
     * @param value
     *            new cell value
     *
     * @param row
     *            table row, model coordinates
     *
     * @param column
     *            table column, model coordinates
     *
     * @return Cell value following removal of any leading and trailing white space characters
     *********************************************************************************************/
    protected Object cleanUpCellValue(Object value, int row, int column)
    {
        // Check if the cell isn't being actively edited and if the cell value represents a string
        // (i.e., it isn't boolean, etc.)
        if (!table.isEditing() && value instanceof String)
        {
            // Remove any leading and trailing white space characters
            value = value.toString().trim();
        }

        return value;
    }

    /**********************************************************************************************
     * Placeholder for any steps to perform when a change to the table's content occurs
     *********************************************************************************************/
    protected void processTableContentChange()
    {
    }

    /**********************************************************************************************
     * Set common table characteristics. This must be called again if the table changes while it is
     * visible; e.g., the look and feel is changed or new table data is loaded
     *
     * @param tableData
     *            two-dimensional array of data to be placed into the table
     *
     * @param columnNames
     *            array of HTML-formatted names for the table columns
     *
     * @param columnOrder
     *            string of colon-separated column indices
     *
     * @param toolTips
     *            tool tip text to display when hovering over the column headers; null if no column
     *            has tool tip text
     *
     * @param centerText
     *            true to center the text within the cells
     *
     * @param calcTotalWidth
     *            true to calculate the total width of the table
     *
     * @param showGrid
     *            true to show the table grid
     *
     * @return Combined width of all columns in pixels
     *********************************************************************************************/
    protected int setUpdatableCharacteristics(Object[][] tableData,
                                              String[] columnNames,
                                              String columnOrder,
                                              String[] toolTips,
                                              boolean centerText,
                                              boolean calcTotalWidth,
                                              boolean showGrid)
    {
        // Initialize the hidden column list. This list remains empty if there is no hidden column
        hiddenColumns = new ArrayList<TableColumn>();

        // Initialize the total table pixel width
        int totalWidth = 0;

        // Store the tool tip text
        this.toolTips = toolTips;

        // Store the flag indicating if the table grid is visible
        this.showGrid = showGrid;

        // Store the array of column names
        this.columnNames = columnNames;

        // Format the tool tip text
        formatToolTipText();

        // Load the array of data into the table
        loadDataArrayIntoTable(tableData, false);

        // Check if this is the first pass through for this table (note that this relies on calls
        // to this method to set calcTotalWidth to false for subsequent passes)
        if (calcTotalWidth)
        {
            // Create the table's columns
            createDefaultColumnsFromModel();
        }

        // Arrange the columns in the order specified
        arrangeColumns(columnOrder);

        // Set up the table grid lines
        setTableGrid();

        // Do not display columns that are flagged as hidden
        updateHiddenColumns();

        // Set up the renderers for the table header and cells
        setHeaderRenderer();
        setCellRenderers(centerText);

        // Set the editor characteristics for the editable cells
        setCellEditors();

        // Check if the width should be calculated
        if (calcTotalWidth)
        {
            // Calculate and set the widths of the table columns and get the minimum width needed
            // to display the table
            totalWidth = calcAndSetColumnWidths();
        }

        return totalWidth;
    }

    /**********************************************************************************************
     * Display or hide columns. Note that a column, when no longer hidden, is shown at the right of
     * the table
     *********************************************************************************************/
    protected void updateHiddenColumns()
    {
        List<TableColumn> wasHidden = new ArrayList<TableColumn>();

        // Step through each column in the table
        for (int column = 0; column < tableModel.getColumnCount(); column++)
        {
            boolean isAlreadyHidden = false;

            // Set the flag if this column should not be displayed
            boolean shouldBeHidden = isColumnHidden(column);

            // Step through the columns currently hidden
            for (TableColumn hiddenColumn : hiddenColumns)
            {
                // Check if the column should be displayed but is currently hidden
                if (!shouldBeHidden && column == hiddenColumn.getModelIndex())
                {
                    // Add the column to the list of those that were hidden but should now be
                    // showing, and add it to the view
                    wasHidden.add(hiddenColumn);
                    addColumn(hiddenColumn);
                }
                // Check is the column should not be displayed but is already hidden
                else if (shouldBeHidden && column == hiddenColumn.getModelIndex())
                {
                    // Set the flag indicating this column is already hidden
                    isAlreadyHidden = true;
                    break;
                }
            }

            // Check if the column isn't already hidden, but should be
            if (!isAlreadyHidden && shouldBeHidden)
            {
                // Get the column information. Adjust the column index based on the number of
                // columns already hidden
                TableColumn tableColumn = getColumnModel().getColumn(column - hiddenColumns.size());

                // Add the column to the hidden columns list and remove it from the view
                hiddenColumns.add(tableColumn);
                removeColumn(tableColumn);
            }
        }

        // Remove the columns that were hidden but are now showing (if any) from the list of hidden
        // columns
        hiddenColumns.removeAll(wasHidden);
    }

    /**********************************************************************************************
     * Load an array of data into the table
     *
     * @param tableData
     *            two-dimensional array of data to be placed into the table
     *
     * @param undoable
     *            true if the data update can be undone
     *********************************************************************************************/
    protected void loadDataArrayIntoTable(Object[][] tableData, boolean undoable)
    {
        // Check if the number of table rows or columns changed
        if (tableData.length != tableModel.getRowCount()
            || (tableData.length != 0 && tableData[0].length != tableModel.getColumnCount()))
        {
            // Initialize the row sorter to null in order to prevent an error when the table is
            // empty and to remove the effects of any row filtering
            setRowSorter(null);
        }

        // Set the flag indicating that a model data update is in progress
        isReloadData = true;

        // Place the data into the table model along with the column names
        tableModel.setDataVector(tableData, columnNames, undoable);

        // Reset the model update in progress flag
        isReloadData = false;

        // Enable/disable table sort capability based on if any rows exist
        setTableSortable();

        // Check if the table was sorted prior to loading the table data
        if (allowSort
            && tableModel.getRowCount() != 0
            && getRowSorter() != null
            && lastSortKeys != null)
        {
            // Restore the sort order
            ((TableRowSorter<?>) getRowSorter()).setSortKeys(lastSortKeys);
            ((TableRowSorter<?>) getRowSorter()).sort();
        }

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Execute after all pending Swing events are finished. This allows the number of
             * viewable columns to catch up with the column model when a column is removed
             *************************************************************************************/
            @Override
            public void run()
            {
                // Issue data and structural change events to ensure the table is redrawn
                // correctly
                tableModel.fireTableDataChanged();
                tableModel.fireTableStructureChanged();
            }
        });
    }

    /**********************************************************************************************
     * Enable (if rows exist) or disable (if no rows exist) the capability to sort the table
     *********************************************************************************************/
    protected void setTableSortable()
    {
        // Check if sorting is enabled, if any rows exist in the table, and no sorter is set
        if (allowSort && tableModel.getRowCount() != 0 && getRowSorter() == null)
        {
            // Set up sorter to allow sorting the table rows by clicking the column header
            TableRowSorter<UndoableTableModel> sorter = new TableRowSorter<UndoableTableModel>(tableModel)
            {
                int lastColumn = -1;

                /**********************************************************************************
                 * Override the sort toggle method to include showing the column unsorted and to
                 * allow undo/redo operations
                 *********************************************************************************/
                @Override
                public void toggleSortOrder(int column)
                {
                    // Get the reference to the sort keys. The list size is zero if the rows aren't
                    // sorted
                    List<? extends SortKey> sortKeys = getSortKeys();

                    // Check if this is the same column as for the previous sort and that the
                    // current sort order is descending
                    if (column == lastColumn
                        && sortKeys.size() > 0
                        && sortKeys.get(0).getSortOrder() == SortOrder.DESCENDING)
                    {
                        // Set the sort keys to null to remove the column sort
                        setSortKeys(null);
                    }
                    // Not the same column as the previous sort or current sort order is not
                    // descending
                    else
                    {
                        // Toggle the sort order
                        super.toggleSortOrder(column);

                        // Update the row heights for the visible rows
                        tableChanged(null);
                    }

                    // Store the row sort change in the undo manager
                    tableModel.sortRows(sortKeys, getSortKeys());

                    // Store the sort keys in case the sort order needs to be restored
                    lastSortKeys = getSortKeys();

                    // Flag the end of the editing sequence for undo/redo purposes
                    undoManager.endEditSequence();

                    // Store the column sorted
                    lastColumn = column;
                }
            };

            setRowSorter(sorter);
        }
        // The table is empty and a sorter exists; disable sorting to prevent an error that would
        // occur if sorting was attempted
        else if (tableModel.getRowCount() == 0 && getRowSorter() != null)
        {
            setRowSorter(null);
        }
    }

    /**********************************************************************************************
     * Change TAB/SHIFT-TAB behavior so that focus jumps between the table and any other components
     * (i.e., the buttons in the dialog containing the table)
     *********************************************************************************************/
    private void setTableKeyboardTraversal()
    {
        Set<AWTKeyStroke> forward = new HashSet<AWTKeyStroke>(getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        forward.add(KeyStroke.getKeyStroke("TAB"));
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forward);
        Set<AWTKeyStroke> backward = new HashSet<AWTKeyStroke>(getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        backward.add(KeyStroke.getKeyStroke("shift TAB"));
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backward);
    }

    /**********************************************************************************************
     * Table header renderer class for adjusting the font by column
     *********************************************************************************************/
    private class HeaderFontRenderer extends DefaultTableCellRenderer
    {
        private final DefaultTableCellRenderer renderer;

        /******************************************************************************************
         * Table header renderer class constructor
         *****************************************************************************************/
        HeaderFontRenderer()
        {
            // Get the default table header renderer. This allows manipulation of look & feels that
            // have specialized header renderers
            renderer = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        }

        /******************************************************************************************
         * Override the table header renderer so that the font can be set based on the column name
         * content
         *****************************************************************************************/
        @Override
        public Component getTableCellRendererComponent(JTable jtable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            // Get the column index in model coordinates
            int modelColumn = table.convertColumnIndexToModel(column);

            // Check if the column name contains an HTML line break tag
            if (columnNames[modelColumn].contains("<br>"))
            {
                // Adjust the font smaller
                getTableHeader().setFont(ModifiableFontInfo.TABLE_HEADER.getFont()
                                                                        .deriveFont(Math.max(ModifiableFontInfo.TABLE_HEADER.getFont().getSize() - 3,
                                                                                             9f)));
            }
            // The column name appears on a single line
            else
            {
                // Set the column name font
                getTableHeader().setFont(ModifiableFontInfo.TABLE_HEADER.getFont());
            }

            return renderer.getTableCellRendererComponent(jtable,
                                                          value,
                                                          isSelected,
                                                          hasFocus,
                                                          row,
                                                          column);
        }
    }

    /**********************************************************************************************
     * Set the table header renderer
     *********************************************************************************************/
    private void setHeaderRenderer()
    {
        // Get the reference to the default header renderer to shorten subsequent calls
    	DefaultTableCellRenderer headerRenderer = new HeaderFontRenderer();

        // Center the table column header text
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        // Set the table's column size
        for (int column = 0; column < getColumnCount(); column++)
        {
            // Get the table column to shorten the calls below
            TableColumn tableColumn = getColumnModel().getColumn(column);

            // Set the header renderer
            tableColumn.setHeaderRenderer(headerRenderer);
        }
    }

    /**********************************************************************************************
     * Create a cell renderer that can display text only on a single line. This allows the cell
     * border to be set to provide padding around the cell contents. HTML-formatted text is
     * supported by this renderer
     *********************************************************************************************/
    private class SingleLineCellRenderer extends DefaultTableCellRenderer
    {
        /******************************************************************************************
         * Table cell renderer constructor
         *
         * @param centerText
         *            true to center the text within the cells
         *****************************************************************************************/
        SingleLineCellRenderer(boolean centerText)
        {
            // Set the font
            setFont(cellFont);

            // Add inset space around the cell's perimeter to provide padding between it and the
            // cell's contents
            setBorder(cellBorder);

            // Set the alignment of the text in the cell
            setHorizontalAlignment(centerText
                                              ? SwingConstants.CENTER
                                              : SwingConstants.LEFT);

            // Set to paint every pixel within the cell. This is needed to prevent a border
            // appearing around the cell for some look & feels
            setOpaque(true);
        }

        /******************************************************************************************
         * Override this method so that the cell border is set
         *****************************************************************************************/
        @Override
        public Border getBorder()
        {
            return cellBorder;
        }

        /******************************************************************************************
         * Override this method so that the text is displayed in the cell
         *****************************************************************************************/
        @Override
        public Component getTableCellRendererComponent(JTable jtable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            // Set the cell to the string representation of the value
            setText(value == null ? "" : value.toString());

            return this;
        }
    }

    /**********************************************************************************************
     * Create a cell renderer for cells that can display text on more than one line. This allows
     * adjusting the row height to fit the text and to set the cell border to provide padding
     * around the cell contents
     *********************************************************************************************/
    private class MultiLineCellRenderer extends JTextArea implements TableCellRenderer
    {
        /******************************************************************************************
         * Multi-line table cell renderer constructor
         *****************************************************************************************/
        MultiLineCellRenderer()
        {
            // Set the cell to be non-editable and for the text to automatically wrap
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);

            // Set the font
            setFont(cellFont);

            // Add inset space around the cell's perimeter to provide padding between it and the
            // cell's contents
            setBorder(cellBorder);

            // Set to paint every pixel within the cell. This is needed to prevent a border
            // appearing around the cell for some look & feels
            setOpaque(true);
        }

        /******************************************************************************************
         * Override this method so that the cell border is set
         *****************************************************************************************/
        @Override
        public Border getBorder()
        {
            return cellBorder;
        }

        /******************************************************************************************
         * Override this method so that the text is displayed in the cell and the row height can be
         * adjusted to fit the text
         *****************************************************************************************/
        @Override
        public Component getTableCellRendererComponent(JTable jtable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            // Set the cell to the string representation of the value. Replace any line feed
            // place-holders with line feed characters
            setText(value == null
                                  ? ""
                                  : value.toString());

            // Check if the row and column indices are valid
            if (row < table.getRowCount() && column < table.getColumnCount())
            {
                // Set the text area's width to the table column width. This causes the JTextArea
                // to calculate the height required to show all of the cell's text. Subtract 1 from
                // the width so that cell text matching the exact width doesn't have text truncated
                setSize(jtable.getColumnModel().getColumn(column).getWidth() - 1,
                        jtable.getRowHeight(row));
            }

            return this;
        }
    }

    /**********************************************************************************************
     * Create a cell renderer that can display boolean values as check boxes
     *********************************************************************************************/
    private class BooleanCellRenderer extends DefaultTableCellRenderer
    {
        private final JCheckBox checkBox;

        /******************************************************************************************
         * Table cell renderer constructor. Note that there is a single check box shared by all
         * users of this cell renderer for which attempts to change individual cells must account
         *****************************************************************************************/
        BooleanCellRenderer()
        {
            // Create a check box in which the cell renderer can display boolean values. If the
            // default boolean renderer is used the cell background flashes when the check box is
            // toggled
            checkBox = new JCheckBox();

            // Center the check box within the cell
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);

            // Set the opaque flag for the check box so that the background color of the cell is
            // displayed correctly for alternating rows
            checkBox.setOpaque(true);

            // Add inset space around the cell's perimeter to provide padding between it and the
            // cell's contents
            setBorder(cellBorder);
        }

        /******************************************************************************************
         * Override this method so that a check box is displayed in the cell
         *****************************************************************************************/
        @Override
        public Component getTableCellRendererComponent(JTable jtable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            // Check if the value represents a boolean value
            if (value instanceof Boolean)
            {
                // Set the flag to indicate if the cell value can be changed
                boolean isEditable = isCellEditable(row, column);

                // Check if the check box's enable state differs from the edit state
                if (checkBox.isEnabled() != isEditable)
                {
                    // Set the check box enable state to match the edit state for this cell
                    checkBox.setEnabled(isEditable);
                }

                // Check if the cell's current state doesn't match the check box state
                if (!(table.getValueAt(row, column) instanceof Boolean)
                    || (Boolean) table.getValueAt(row, column) != checkBox.isSelected())
                {
                    // Set the check box state to the new state
                    checkBox.setSelected((Boolean) value);
                }
            }

            return checkBox;
        }
    }

    /**********************************************************************************************
     * Create a cell renderer for cells that can display HTML-formatted text on more than one line.
     * This allows adjusting the row height to fit the text and to set the cell border to provide
     * padding around the cell contents
     *********************************************************************************************/
    private class HTMLCellRenderer extends JTextPane implements TableCellRenderer
    {
        /******************************************************************************************
         * HTML-formatted multi-line table cell renderer constructor
         *****************************************************************************************/
        HTMLCellRenderer()
        {
            // Set the cell to be non-editable
            setEditable(false);

            // Set the font
            setFont(cellFont);

            // Add inset space around the cell's perimeter to provide padding between it and the
            // cell's contents
            setBorder(cellBorder);

            // Set the property so that the font selection applies to HTML text
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);

            // Set to paint every pixel within the cell. This is needed to prevent a border
            // appearing around the cell for some look & feels
            setOpaque(true);
        }

        /******************************************************************************************
         * Override this method so that the cell border is set
         *****************************************************************************************/
        @Override
        public Border getBorder()
        {
            return cellBorder;
        }

        /******************************************************************************************
         * Override this method so that the text is displayed in the cell and the row height can be
         * adjusted to fit the text
         *****************************************************************************************/
        @Override
        public Component getTableCellRendererComponent(JTable jtable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            // Check if this is the first row and column in the table and the cell is not already
            // formatted for HTML
            if (row == 0 && column == 0 && !value.toString().startsWith("<html>"))
            {
                // Convert the cell's content to HTML. If the first row in the table formatted for
                // HTML is beyond row 13 and a previous row contains a line feed then the row with
                // the line feed isn't displayed correctly (unless the table is scrolled to the row
                // containing the HTML text). If more than one column is 'forced' to be HTML then
                // the initial rows aren't displayed correctly. Forcing the first row's column to
                // be HTML avoids the condition
                value = CcddUtilities.convertToHTML(value.toString());
            }

            // Check if the cell value is HTML-formatted
            if (value != null && value.toString().startsWith("<html>"))
            {
                // Set the cell's content type to display HTML and remove the HTML tag
                setContentType("text/html");
                value = value.toString().substring("<html>".length());
            }
            // The cell value isn't HTML-formatted
            else
            {
                // Set the cell's content type to plain text
                setContentType("text/plain");
            }

            // Set the cell to the string representation of the value. Replace any line feed
            // place-holders with line feed characters
            setText(value == null
                                  ? ""
                                  : value.toString());

            // Check if the row and column indices are valid
            if (row < table.getRowCount() && column < table.getColumnCount())
            {
                // Set the text pane's width to the table column width. This causes the JTextPane
                // to calculate the height required to show all of the cell's text. Subtract 1 from
                // the width so that cell text matching the exact width doesn't have text truncated
                setSize(jtable.getColumnModel().getColumn(column).getWidth() - 1,
                        jtable.getRowHeight(row));
            }

            return this;
        }
    }

    /**********************************************************************************************
     * Set the table cell renderers
     *
     * @param centerText
     *            true to center the text within the cells
     *********************************************************************************************/
    private void setCellRenderers(boolean centerText)
    {
        // Set the table's column size
        for (int column = 0; column < getColumnCount(); column++)
        {
            // Get the table column to shorten the calls below
            TableColumn tableColumn = getColumnModel().getColumn(column);

            // Get the column index in model coordinates
            int columnModel = convertColumnIndexToModel(column);

            // Check if the column doesn't display a check box
            if (!isColumnBoolean(columnModel))
            {
                // Set the cell renderer based on if the column supports HTML-formatted text, is
                // displayed in multiple lines, or is displayed in a single line. HTML supports
                // multiple lines, so takes precedence over the multiple line setting
                tableColumn.setCellRenderer(isColumnHTML(columnModel)
                                                                      ? new HTMLCellRenderer()
                                                                      : (isColumnMultiLine(columnModel)
                                                                                                        ? new MultiLineCellRenderer()
                                                                                                        : new SingleLineCellRenderer(centerText)));
            }
            // This column displays check boxes
            else
            {
                // Set the cell renderer to display a check box
                tableColumn.setCellRenderer(new BooleanCellRenderer());
            }
        }
    }

    /**********************************************************************************************
     * Create a cell editor for cells that can display text on more than one line. This allows
     * adjusting the row height to fit the text as it is added or deleted
     *********************************************************************************************/
    private class MultiLineCellEditor extends DefaultCellEditor
    {
        private final JTextArea textArea;

        /******************************************************************************************
         * Multi-line table cell editor constructor
         *
         * @param textArea
         *            reference to the JTextArea to be used as the cell editor
         *****************************************************************************************/
        MultiLineCellEditor(final JTextArea textArea)
        {
            // This is a dummy action required by the interface. The check box is never used
            super(new JCheckBox());

            this.textArea = textArea;

            // Set the click count so that a double click is required to initiate editing on the
            // cell
            clickCountToStart = 2;

            // Set the cell to be editable and for the text to automatically wrap
            textArea.setEditable(true);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            // Add a listener for keyboard inputs
            textArea.addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Handle a key press event so that the table's content can be updated. This causes
                 * the cell's row height to be expanded/contracted automatically as text is
                 * added/removed. This also allows the cell's owner to be informed of a change in
                 * text, such as to update the table container's change indicator
                 *********************************************************************************/
                @Override
                public void keyPressed(final KeyEvent ke)
                {
                    // Store the row and column indices for the cell being edited
                    final int row = table.getEditingRow();
                    final int column = table.getEditingColumn();

                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Since the cell text change involves a GUI update use invokeLater to
                         * execute the call on the event dispatch thread following any pending
                         * events
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Check if the key produces a change in the text (i.e., a character,
                            // backspace, or delete key, but not an arrow, shift, or control key)
                            if (ke.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
                            {
                                // Update the table cell value to match the text entered
                                table.setValueAt(textArea.getText(), row, column);
                            }
                        }
                    });
                }
            });
        }

        /******************************************************************************************
         * Override the method to get the cell editor's component
         *****************************************************************************************/
        @Override
        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column)
        {
            // Set the cell to the string representation of the value. Replace any line feed
            // place-holders with line feed characters
            textArea.setText(value == null ? "" : value.toString());

            return textArea;
        }

        /******************************************************************************************
         * Override the method to get the cell editor's content
         *****************************************************************************************/
        @Override
        public Object getCellEditorValue()
        {
            return textArea.getText();
        }
    }

    /**********************************************************************************************
     * Set the editors for the editable cells based on the column type
     *********************************************************************************************/
    private void setCellEditors()
    {
        // Create a focus listener to track the text cursor position when the cell loses focus
        FocusListener focusListener = new FocusListener()
        {
            /**************************************************************************************
             * Handle loss of keyboard focus for the cell
             *************************************************************************************/
            @Override
            public void focusLost(FocusEvent fe)
            {
                // Check if editing is active in the cell
                if (table.isEditing())
                {
                    // Store the start and end positions of the selected text
                    lastSelectionStart = ((JTextComponent) fe.getComponent()).getSelectionStart();
                    lastSelectionEnd = ((JTextComponent) fe.getComponent()).getSelectionEnd();
                }
                // Editing is inactive
                else
                {
                    // Reset the text selection positions
                    lastSelectionStart = -1;
                    lastSelectionEnd = -1;
                }
            }

            /**************************************************************************************
             * Handle gain of keyboard focus for the cell
             *************************************************************************************/
            @Override
            public void focusGained(FocusEvent fe)
            {
            }
        };

        // Create a text area so that its properties can be set and then used to create a
        // multi-line editor for cells containing one or more lines of text
        JTextArea textFieldMulti = new JTextArea();
        textFieldMulti.setFont(cellFont);
        textFieldMulti.setBorder(editBorder);

        // Add a listener for cell focus changes
        textFieldMulti.addFocusListener(focusListener);

        // Create the cell editor for multi-line cells
        MultiLineCellEditor dceMulti = new MultiLineCellEditor(textFieldMulti);

        // Create a a cell editor for single line cells. The padding differs from the multi-line
        // cell; using this editor for single line cells prevents the text from changing vertical
        // alignment when editing is initiated
        JTextField textFieldSingle = new JTextField();
        textFieldSingle.setFont(cellFont);
        textFieldSingle.setBorder(editBorder);

        // Add a listener for cell focus changes
        textFieldSingle.addFocusListener(focusListener);

        // Create the cell editor
        DefaultCellEditor dceSingle = new DefaultCellEditor(textFieldSingle);

        // Step through each column in the table
        for (int column = 0; column < getColumnCount(); column++)
        {
            // Get the column index in model coordinates
            int columnModel = convertColumnIndexToModel(column);

            // Check if the column's contents is not displayed as a check box
            if (!isColumnBoolean(columnModel))
            {
                // Set the editor so that the contents can be modified within the table cell. Use
                // the editor appropriate for the number of cell display lines
                getColumnModel().getColumn(column).setCellEditor(isColumnMultiLine(columnModel)
                                                                                                ? dceMulti
                                                                                                : dceSingle);
            }
        }
    }

    /**********************************************************************************************
     * Calculate and then set the minimum width for each visible column and return the combined
     * width of all of the columns
     *
     * @return Combined width of all visible columns in pixels
     *********************************************************************************************/
    private int calcAndSetColumnWidths()
    {
        // Create storage for the minimum header and data column widths
        minHeaderWidth = new int[getColumnCount()];
        minDataWidth = new int[getColumnCount()];

        // Set the table's column sizes
        for (int column = 0; column < getColumnCount(); column++)
        {
            // Calculate and set the specified column's width
            calcAndSetColumnWidth(column);
        }

        // Return the total width of the table
        return getPreferredSize().width;
    }

    /**********************************************************************************************
     * Calculate and then set the minimum width for the specified column
     *
     * @param column
     *            index of the column to size, view coordinates
     *********************************************************************************************/
    private void calcAndSetColumnWidth(int column)
    {
        // Get the table column to shorten the calls below
        TableColumn tableColumn = getColumnModel().getColumn(column);

        // Get the component used to draw the table header cell
        Component comp = tableColumn.getHeaderRenderer().getTableCellRendererComponent(table,
                                                                                       getColumnName(column),
                                                                                       false,
                                                                                       false,
                                                                                       -1,
                                                                                       column);

        // Remove any padding added to the column name (to force its width larger when setting the
        // initial column width)
        tableColumn.setHeaderValue(getColumnName(column).trim());

        // Save the width required to hold the header text and use it to initialize the data cell
        // width
        minHeaderWidth[column] = comp.getPreferredSize().width
                                 + ModifiableSpacingInfo.HEADER_HORIZONTAL_PADDING.getSpacing();
        minDataWidth[column] = minHeaderWidth[column];

        // Step through each row in the table
        for (int row = 0; row < getRowCount(); row++)
        {
            // Get the component used to draw the table cell from the cell's renderer
            comp = super.prepareRenderer(getDefaultRenderer(String.class),
                                         row,
                                         column);

            // Compare the width of the cell to the largest width found so far and store it if it's
            // larger, but no larger than a specified maximum starting width
            minDataWidth[column] = Math.min(Math.max(comp.getPreferredSize().width
                                                     + ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing() * 2,
                                                     minDataWidth[column]),
                                            ModifiableSizeInfo.MAX_INIT_CELL_WIDTH.getSize());
        }

        // Get the column index in model coordinates
        int modelColumn = convertColumnIndexToModel(column);

        // Set whether or not the column can be resized based on the caller's input
        tableColumn.setResizable(isColumnResizable(modelColumn));

        // Check if the column is resizable
        if (!isColumnResizable(modelColumn))
        {
            // Set the maximum size of the column to prevent it from being resized
            tableColumn.setMaxWidth(minDataWidth[column]);
        }

        // Set the current and preferred width of the table column
        tableColumn.setPreferredWidth(minDataWidth[column]);
        tableColumn.setWidth(minDataWidth[column]);
    }

    /**********************************************************************************************
     * Get the array of minimum widths of the visible table columns. Since the minimum cell width
     * is initialized to the minimum header width the cell width represents the overall minimum
     * width
     *
     * @return Array of minimum widths of the visible table columns
     *********************************************************************************************/
    protected int[] getColumnWidths()
    {
        return minDataWidth;
    }

    /**********************************************************************************************
     * Get the column order as a colon-separated string of column indices
     *
     * @return The column indices, separated by colons
     *********************************************************************************************/
    protected String getColumnOrder()
    {
        String columnOrder = "";
        int numHiddenColumns = 0;

        // Step through each column in the model
        for (int column = 0; column < tableModel.getColumnCount(); column++)
        {
            // Check if the column is visible
            if (convertColumnIndexToView(column) != -1)
            {
                // Convert the column's visible index to model coordinates (taking into account any
                // intervening hidden columns) and store this as the order index
                columnOrder += convertColumnIndexToModel(column
                                                         - numHiddenColumns)
                               + ":";
            }
            // The column is hidden
            else
            {
                // Add the hidden column to the order and increment the hidden column counter
                columnOrder += column + ":";
                numHiddenColumns++;
            }
        }

        // Remove the trailing separator
        return CcddUtilities.removeTrailer(columnOrder, ":");
    }

    /**********************************************************************************************
     * Arrange the column order based on a colon-separated string of column indices
     *
     * @param columnOrder
     *            string of colon-separated column indices
     *********************************************************************************************/
    protected void arrangeColumns(String columnOrder)
    {
        // Check that the column order string was provided. If not, the order of the columns is
        // unchanged
        if (columnOrder != null
            && !columnOrder.isEmpty()
            && !columnOrder.equals(getColumnOrder()))
        {
            int numHiddenColumns = 0;

            // Separate the model column indices into an array
            String[] columnIndex = columnOrder.split(":");

            // Step through each model column index
            for (int column = 0; column < columnIndex.length; column++)
            {
                // Check if the column isn't hidden
                if (convertColumnIndexToView(column) != -1)
                {
                    // Get the index of the column in view coordinates
                    int viewColumn = convertColumnIndexToView(Integer.parseInt(columnIndex[column]));

                    // Check if the column is visible and that its position changed
                    if (viewColumn != -1 && viewColumn != column - numHiddenColumns)
                    {
                        // Move the column to its location in the new order, skipping any hidden
                        // column(s)
                        ((UndoableTableColumnModel) getColumnModel()).moveColumn(viewColumn,
                                                                                 column - numHiddenColumns);
                    }
                }
                // The column is hidden
                else
                {
                    // Increment the hidden column counter
                    numHiddenColumns++;
                }
            }
        }
    }

    /**********************************************************************************************
     * Update the flags indicating the state of the control and shift modifier keys. The modifier's
     * flag is set to true if the key is pressed while no other modifier keys are pressed
     *
     * @param ke
     *            key event
     *********************************************************************************************/
    private void updateModifierKeyStates(KeyEvent ke)
    {
        // Set the flag to true if the shift key is pressed, and the control and alt keys are not
        // pressed
        shiftKey = ke.isShiftDown() && !ke.isControlDown() && !ke.isAltDown();

        // Set the flag to true if the control key is pressed, and the shift and alt keys are not
        // pressed
        controlKey = ke.isControlDown() && !ke.isAltDown() && !ke.isShiftDown();
    }

    /**********************************************************************************************
     * Set the keys that initiate editing the cell with the focus and which keys do not. Handle
     * keyboard commands
     *
     * @param isRowsAlterable
     *            true if the table's rows can be altered by the user via key presses (e.g., row
     *            insertion)
     *********************************************************************************************/
    private void setEditorKeys(final boolean isRowsAlterable)
    {
        // Add a key press listener for the table cells to filter out invalid editor initiation
        // keys and to listen for user keyboard commands
        addKeyListener(new KeyAdapter()
        {
            /**************************************************************************************
             * Handle key press events
             *************************************************************************************/
            @Override
            public void keyPressed(KeyEvent ke)
            {
                // Set the flags indicating the state of the modifier keys
                updateModifierKeyStates(ke);

                // Check that the Alt key isn't pressed. If the Alt key is pressed this allows the
                // keyboard mnemonic keys to activate their respective controls
                if (!ke.isAltDown())
                {
                    switch (ke.getKeyCode())
                    {
                        case KeyEvent.VK_SPACE:
                        case KeyEvent.VK_HOME:
                        case KeyEvent.VK_END:
                        case KeyEvent.VK_PAGE_UP:
                        case KeyEvent.VK_PAGE_DOWN:
                        case KeyEvent.VK_LEFT:
                        case KeyEvent.VK_RIGHT:
                        case KeyEvent.VK_KP_LEFT:
                        case KeyEvent.VK_KP_RIGHT:
                            // Allow the space key to initiate editing as usual. Also keep the
                            // traversal keys active; these won't initiate editing in the cell
                            break;

                        case KeyEvent.VK_UP:
                        case KeyEvent.VK_DOWN:
                        case KeyEvent.VK_KP_UP:
                        case KeyEvent.VK_KP_DOWN:
                            // Check if the Ctrl key is pressed
                            if (ke.isControlDown())
                            {
                                // Ignore Ctrl+Up/Down arrow key sequences. If the Ctrl key is not
                                // pressed the Up and Down arrow keys are handled as traversal keys
                                ke.consume();
                            }

                            break;

                        case KeyEvent.VK_A:
                            // Check if the Ctrl key is pressed
                            if (ke.isControlDown())
                            {
                                // Select every visible cell in the table
                                setSelectedCells(0,
                                                 getRowCount() - 1,
                                                 0,
                                                 getColumnCount() - 1);
                                break;
                            }

                        case KeyEvent.VK_C:
                            // Check if the Ctrl key is pressed
                            if (ke.isControlDown())
                            {
                                // Allow Ctrl+C key sequence to remain active for copying table
                                // text to the clipboard
                                break;
                            }

                        case KeyEvent.VK_V:
                            // Check if the control key is pressed
                            if (ke.isControlDown())
                            {
                                // Paste the clipboard contents into the table, overwriting
                                // existing cells
                                pasteClipboardData(false, isRowsAlterable);
                            }

                            break;

                        case KeyEvent.VK_DELETE:
                            // Check if the control key is not pressed
                            if (!ke.isControlDown())
                            {
                                // Delete the contents of the currently selected cell(s). If the
                                // shift key is also pressed then flag the cell for deletion of the
                                // custom values table entry so that its prototype's value is used
                                ke.consume();
                                deleteCell(ke.isShiftDown()
                                                            ? true
                                                            : false);
                            }

                            break;

                        default:
                            // Ignore all other keys (alpha-numeric, function, punctuation, etc.)
                            ke.consume();
                            break;
                    }

                    // Check if the table rows can be altered
                    if (isRowsAlterable)
                    {
                        switch (ke.getKeyCode())
                        {
                            case KeyEvent.VK_I:
                                // Check if the control key is pressed
                                if (ke.isControlDown())
                                {
                                    // Paste the clipboard contents into the table, inserting new
                                    // rows to contain the data
                                    pasteClipboardData(true, isRowsAlterable);
                                }

                                break;

                            case KeyEvent.VK_INSERT:
                                // Insert a row into the table at the selected location
                                insertEmptyRow(true);
                                break;

                            case KeyEvent.VK_X:
                                // Check if the control key is not pressed
                                if (!ke.isControlDown())
                                {
                                    break;
                                }

                            case KeyEvent.VK_DELETE:
                                // Check if the control key is pressed
                                if (ke.isControlDown())
                                {
                                    // Delete the selected row(s) from the table
                                    deleteRow(true);
                                }

                                break;

                            case KeyEvent.VK_UP:
                            case KeyEvent.VK_KP_UP:
                                // Check if the control key is pressed
                                if (ke.isControlDown())
                                {
                                    // Move the selected row(s) up
                                    moveRowUp();
                                }

                                break;

                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_KP_DOWN:
                                // Check if the control key is pressed
                                if (ke.isControlDown())
                                {
                                    // Move the selected row(s) down
                                    moveRowDown();
                                }

                                break;

                            default:
                                break;
                        }
                    }
                }
            }

            /**************************************************************************************
             * Handle key release events
             *************************************************************************************/
            @Override
            public void keyReleased(KeyEvent ke)
            {
                // Set the flags indicating the state of the modifier keys
                updateModifierKeyStates(ke);
            }
        });
    }

    /**********************************************************************************************
     * Move cell selection class
     *********************************************************************************************/
    protected class MoveCellSelection
    {
        // Start and end selected row(s) (model coordinates) and column(s) (view coordinates)
        private final int startRow;
        private final int endRow;
        private final int startColumn;
        private final int endColumn;

        /******************************************************************************************
         * Move cell selection class constructor. Determine the bounds of the currently selected
         * table cells
         *****************************************************************************************/
        MoveCellSelection()
        {
            // Get the cell coordinates of the first selected cell
            int selRow = getSelectedRow();
            int selCol = getSelectedColumn();

            // Check if no cell is selected
            if (selRow == -1 || selCol == -1)
            {
                startRow = -1;
                endRow = -1;
                startColumn = -1;
                endColumn = -1;
            }
            // A cell is selected
            else
            {
                // Get the start and end row indices in model coordinates, and the start and end
                // column indices in view coordinates
                startRow = convertRowIndexToModel(selRow);
                endRow = convertRowIndexToModel(getSelectionModel().getMaxSelectionIndex());
                startColumn = selCol;
                endColumn = getColumnModel().getSelectionModel().getMaxSelectionIndex();
            }
        }

        /******************************************************************************************
         * Return the first selected row
         *
         * @return First selected row, model coordinates
         *****************************************************************************************/
        protected int getStartRow()
        {
            return startRow;
        }

        /******************************************************************************************
         * Return the last selected row
         *
         * @return Last selected row, model coordinates
         *****************************************************************************************/
        protected int getEndRow()
        {
            return endRow;
        }

        /******************************************************************************************
         * Return the first selected column
         *
         * @return First selected column, model coordinates
         *****************************************************************************************/
        protected int getStartColumn()
        {
            return startColumn;
        }

        /******************************************************************************************
         * Return the last selected column
         *
         * @return Last selected column, model coordinates
         *****************************************************************************************/
        protected int getEndColumn()
        {
            return endColumn;
        }

        /******************************************************************************************
         * Update the selected cells based on the direction the cells were moved. The cells that
         * are highlighted are those at the intersection of the selected rows and columns
         *
         * @param rowDelta
         *            Negative value equal to the number of rows the selected row or rows are moved
         *            up, positive value equal to the number of rows the selected row or rows are
         *            moved down, 0 if the cell row position is unchanged
         *
         * @param columnDelta
         *            Negative value equal to the number of columns the selected column or columns
         *            are moved up, positive value equal to the number of columns the selected
         *            column or columns are moved down, 0 if the cell column position is unchanged
         *****************************************************************************************/
        protected void moveCellSelection(int rowDelta, int columnDelta)
        {
            // Check if a valid row is selected
            if (startRow != -1)
            {
                // Check if the row(s) moved and the table is sorted
                if (rowDelta != 0
                    && allowSort
                    && tableModel.getRowCount() != 0
                    && getRowSorter() != null
                    && lastSortKeys != null)
                {
                    // Remove the sort since row movement would appear erratic with a sort active,
                    // and reset the table's sort capability
                    setRowSorter(null);
                    setTableSortable();
                }

                // Set the row selection
                setRowSelectionInterval(convertRowIndexToView(startRow + rowDelta),
                                        convertRowIndexToView(endRow + rowDelta));
            }

            // Check if a valid column is selected
            if (startColumn != -1)
            {
                // Set the column selection
                setColumnSelectionInterval(startColumn + columnDelta, endColumn + columnDelta);
            }

            // Adjust the selected cells based on the move directions
            adjustSelectedCells(rowDelta, columnDelta);
        }
    }

    /**********************************************************************************************
     * Insert an empty row into the table at the selection point
     *
     * @param endEdit
     *            true to end the editing sequence at the end of the insert for undo/redo purposes
     *********************************************************************************************/
    protected void insertEmptyRow(boolean endEdit)
    {
        insertRow(endEdit, TableInsertionPoint.SELECTION, null);
    }

    /**********************************************************************************************
     * Insert a row of data into the table at the selection point
     *
     * @param endEdit
     *            true to end the editing sequence at the end of the insert for undo/redo purposes
     *
     * @param insertionPoint
     *            START to insert the data as the new first row in the table; END to force
     *            insertion of the new data at the end of the table; SELECTION to insert the data
     *            below the currently selected row(s)
     *
     * @param data
     *            data with which to populate the inserted row; null to insert an empty row
     *********************************************************************************************/
    protected void insertRow(boolean endEdit, TableInsertionPoint insertionPoint, Object[] data)
    {
        int modelRow;

        // Check if the end of the edit sequence should be flagged
        if (endEdit)
        {
            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();
        }

        // Set the row index to the last row selected by the user (if any)
        int viewRow = getSelectedRow() + getSelectedRowCount() - 1;

        // Check if the new row is to be inserted at the start of the table
        if (insertionPoint == TableInsertionPoint.START)
        {
            // Insert the new row at the start of the table
            modelRow = -1;
        }
        // Check if no row is selected or if the flag is set to add the row at the end of the table
        else if (viewRow < 0 || insertionPoint == TableInsertionPoint.END)
        {
            // Insert the new row at the end of the table
            modelRow = tableModel.getRowCount() - 1;
        }
        // One or more rows is selected
        else
        {
            // Convert the row index to model coordinates
            modelRow = convertRowIndexToModel(viewRow);

            // Deselect the originally selected row(s)
            removeRowSelectionInterval(getSelectedRow(), viewRow);
        }

        // Insert the data into a new row below the selected row, and select and scroll to the
        // inserted row
        setSelectedRow(insertRowData(modelRow, data));

        // Check if the end of the edit sequence should be flagged
        if (endEdit)
        {
            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();
        }
    }

    /**********************************************************************************************
     * Select the specified row and scroll the table so that it is visible
     *
     * @param viewRow
     *            row index to select and scroll to, view coordinates
     *********************************************************************************************/
    protected void setSelectedRow(int viewRow)
    {
        // Check if the new row is visible (row filters can make the row invisible)
        if (viewRow != -1 && viewRow < getRowCount())
        {
            // Select the new row
            setRowSelectionInterval(viewRow, viewRow);

            // Adjust the cell focus to the row
            setFocusCell(viewRow, focusColumn);

            // Scroll the window to keep the inserted row visible
            scrollToRow(viewRow);
        }

        // Set the table sort capability in case this is the table's only row
        setTableSortable();
    }

    /**********************************************************************************************
     * Scroll the table so that the specified row is visible
     *
     * @param row
     *            row index to which to scroll, view coordinates
     *********************************************************************************************/
    protected void scrollToRow(final int row)
    {
        scrollToCell(row, 0);
    }

    /**********************************************************************************************
     * Scroll the table so that the specified cell is visible
     *
     * @param row
     *            row index to which to scroll, view coordinates
     *
     * @param column
     *            column index to which to scroll, view coordinates
     *********************************************************************************************/
    protected void scrollToCell(final int row, final int column)
    {
        // Scroll the window to keep the specified cell visible. This is called twice; immediately
        // and then in the invokeLater() call below. If only performed once the specified cell may
        // not be visible since the rectangle returned by getCellRect() can differ; the second call
        // ensures the cell is visible
        scrollRectToVisible(getCellRect(row, column, true));

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Execute after all pending Swing events are finished
             *************************************************************************************/
            @Override
            public void run()
            {
                // Scroll the window to keep the specified cell visible
                scrollRectToVisible(getCellRect(row, column, true));
            }
        });
    }

    /**********************************************************************************************
     * Insert data into a new row inserted below the specified row
     *
     * @param targetRow
     *            index of the row in model coordinates below which to insert the new row
     *
     * @param data
     *            data to place in the inserted row; null to insert an empty row
     *
     * @return The index of the newly inserted row in view coordinates; -1 if the specified row
     *         isn't visible
     *********************************************************************************************/
    protected int insertRowData(int targetRow, Object[] data)
    {
        // Adjust the row index to the next row
        targetRow++;

        // Insert the new row at the indicated index. If data is provided for the new row then put
        // the data in the new row's columns it; otherwise fill the new row's columns with the
        // default row contents
        tableModel.insertRow(targetRow,
                             (data == null
                                           ? getEmptyRow()
                                           : data));

        // Initialize the view row index
        int viewRow = -1;

        // Check if any rows are visible (filtering may prevent any rows from being displayed)
        if (getRowCount() != 0)
        {
            // Convert the model row index to view coordinates
            viewRow = convertRowIndexToView(targetRow);
        }

        return viewRow;
    }

    /**********************************************************************************************
     * Delete the selected row(s) from the table
     *
     * @param endEdit
     *            true to end the editing sequence at the end of the delete for undo/redo purposes
     *********************************************************************************************/
    protected void deleteRow(boolean endEdit)
    {
        // Check if at least one row is selected
        if (getSelectedRow() != -1)
        {
            // Check if the end of the edit sequence should be flagged
            if (endEdit)
            {
                // Flag the end of the editing sequence for undo/redo purposes
                undoManager.endEditSequence();
            }

            // Remove the selected rows
            removeRows(getSelectedRows());

            // Check if the end of the edit sequence should be flagged
            if (endEdit)
            {
                // Flag the end of the editing sequence for undo/redo purposes
                undoManager.endEditSequence();
            }
        }
    }

    /**********************************************************************************************
     * Get the character(s) used to replace a cell's contents when deleting the cell. Override to
     * substitute character(s) in place of the default blank
     *
     * @param row
     *            cell row index in model coordinates
     *
     * @param column
     *            cell column index in model coordinates
     *
     * @return Cell replacement character(s) (default is a blank)
     *********************************************************************************************/
    protected String getSpecialReplacement(int row, int column)
    {
        return "";
    }

    /**********************************************************************************************
     * Delete the contents of the selected cell(s)
     *
     * @param isReplaceSpecial
     *            false to replace the cell value with a blank; true to replace the cell contents
     *            with one or more special replacement characters
     *********************************************************************************************/
    @SuppressWarnings("unchecked")
    protected void deleteCell(boolean isReplaceSpecial)
    {
        // Check if any cells are currently selected
        if (!selectedCells.getSelectedCells().isEmpty())
        {
            boolean isChange = false;

            // Get the table data array
            List<Object[]> tableData = getTableDataList(false);

            // Copy the selected cell information. The deletion process clears the global
            // selectedCells list so copy the selected cell information in case cells in multiple
            // rows are selected for deletion
            List<SelectedCell> sc = new ArrayList<SelectedCell>();
            sc.addAll(selectedCells.getSelectedCells());

            // Step through each selected cell in reverse order. This is necessary in case arrays
            // are deleted (removing the array members changes the row indices)
            for (int index = sc.size() - 1; index >= 0; index--)
            {
                // Get the reference to the selected cell to make subsequent calls shorter
                SelectedCell cell = sc.get(index);

                // Check if the cell's contents are allowed to be changed to a blank
                if (isCellEditable(cell.getRow(), cell.getColumn())
                    && isCellBlankable(cell.getRow(), cell.getColumn()))
                {
                    Object replaceChars;

                    // Convert the row and column indices to model coordinates
                    int modelRow = convertRowIndexToModel(cell.getRow());
                    int modelColumn = convertColumnIndexToModel(cell.getColumn());

                    // Get the current cell value
                    Object oldValue = tableData.get(modelRow)[modelColumn];

                    // Get the cell's renderer component
                    Component comp = getCellRenderer(cell.getRow(),
                                                     cell.getColumn()).getTableCellRendererComponent(table,
                                                                                                     getValueAt(cell.getRow(),
                                                                                                                cell.getColumn()),
                                                                                                     true,
                                                                                                     false,
                                                                                                     cell.getRow(),
                                                                                                     cell.getColumn());

                    // Check if the cell contains a check box
                    if (comp instanceof JCheckBox)
                    {
                        // Deselect the check box
                        replaceChars = false;
                    }
                    // The cell doesn't contain a check box (it's a text field, combo box, etc.)
                    else
                    {
                        // Set the default replacement character when deleting a cell's contents
                        replaceChars = "";

                        // Check if special replacement characters should be used in place of the
                        // default blank
                        if (isReplaceSpecial)
                        {
                            // Get the special replacement character(s)
                            replaceChars = getSpecialReplacement(modelRow, modelColumn);
                        }
                    }

                    // Check if the cell value changes due to the deletion operation
                    if (!replaceChars.equals(oldValue))
                    {
                        // Check if the cell contains a combo box
                        if (comp instanceof JComboBox)
                        {
                            // Check if special replacement characters should be used in place of
                            // the default blank
                            if (isReplaceSpecial)
                            {
                                // Temporarily add the item with the replace flag prepended. This
                                // allows the combo box item get methods to 'find' the flagged
                                // item. The flagged item is removed if the cell is later edited
                                ((JComboBox<String>) comp).insertItemAt(REPLACE_INDICATOR
                                                                        + replaceChars,
                                                                        0);
                            }
                            // Use no special character replacement
                            else
                            {
                                // Set the selection so that the cell is blank
                                ((JComboBox<?>) comp).setSelectedIndex(-1);
                            }
                        }

                        // Insert the value into the cell
                        tableData.get(modelRow)[modelColumn] = replaceChars;

                        // Handle changes to the cell contents
                        validateCellContent(tableData,
                                            modelRow,
                                            modelColumn,
                                            oldValue,
                                            replaceChars,
                                            true,
                                            false);

                        // Check if special replacement characters are used
                        if (isReplaceSpecial)
                        {
                            // Prepend the indicator to the cell's value to flag it for special
                            // handling
                            tableData.get(modelRow)[modelColumn] = REPLACE_INDICATOR
                                                                   + replaceChars;
                        }

                        // Set the flag indicating a cell value changed
                        isChange = true;
                    }
                }
            }

            // Check if a cell value changed
            if (isChange)
            {
                // Load the array of data into the table
                loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);

                // Force the table to redraw in order for all changes to appear
                repaint();

                // Flag the end of the editing sequence for undo/redo purposes
                undoManager.endEditSequence();
            }
        }
    }

    /**********************************************************************************************
     * Create an array of cell values containing empty strings to fill an inserted row. This method
     * can be overridden to account for cells with non-string content
     *
     * @return Array containing the column values for an empty row
     *********************************************************************************************/
    protected Object[] getEmptyRow()
    {
        // Create an array of objects to insert as a row into the table
        Object[] emptyRow = new Object[tableModel.getColumnCount()];

        // Set each object in the array to an empty string
        Arrays.fill(emptyRow, "");

        return emptyRow;
    }

    /**********************************************************************************************
     * Paste the contents of the clipboard into the table. The expected format is cell values in a
     * row are separated by tab characters and rows are terminated by new line characters
     *
     * @param isInsert
     *            true to add new rows to contain the pasted data; false to overwrite existing
     *            cells in the paste range and only add rows if needed
     *
     * @param isAddIfNeeded
     *            true to add new rows if the pasted data doesn't fit; false to discard excess rows
     *********************************************************************************************/
    private void pasteClipboardData(boolean isInsert, boolean isAddIfNeeded)
    {
        try
        {
            // Get a reference to the system clipboard
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            // Check if there is data in the clipboard
            if (!clipboard.getContents(null).equals(null)
                && clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor))
            {
                // Flag the end of the editing sequence for undo/redo purposes
                undoManager.endEditSequence();

                // Get the values from the clipboard. Each cell value is a string with a tab
                // character delineating separate cells in a row and a new line character
                // indicating the end of the row
                String data = (String) clipboard.getData(DataFlavor.stringFlavor);

                try
                {
                    // Check if the pasted text ends with a new line character
                    if (data.endsWith("\n"))
                    {
                        // Remove the trailing new line. This prevents pasting rows from, for
                        // example, a spreadsheet from adding an extra row since each pasted row,
                        // including the final one, is automatically terminated by a new line
                        data = data.substring(0, data.length() - 1);
                    }

                    // Replace all pairs of consecutive double quotes with a place holder string
                    data = data.replaceAll("\"\"", EMBEDDED_QUOTE);

                    // Embedded new lines are indicated by being in a cell bounded by double
                    // quotes. Replace all embedded new line characters with a place holder string
                    data = data.replaceAll("\n(?!(([^\"]*\"){2})*[^\"]*$)", EMBEDDED_NEW_LINE);

                    // Replace the tabs with a tab+space so that empty cells at the end of a line
                    // aren't discarded by the split commands. The extra space is removed later
                    data = data.replaceAll("\t", "\t ");

                    // Count the number of rows and columns in the pasted data. The remaining new
                    // line characters indicate the end of a row of cells, so the number of new
                    // lines equals the number of rows. A space must be appended when counting the
                    // rows this way in case the string ends with a new line character. The number
                    // of columns is determined by counting the number of tab characters (which
                    // separate the cells in a row) for the first row of the data
                    int numRows = (data + " ").split("\n").length;
                    int numColumns = data.split("\n")[0].split("\t").length;

                    // Replace the new line characters that terminate each row with a tab
                    // character; the string can be split into cell values based on the tabs (cells
                    // in a row are already tab-separated)
                    data = data.replaceAll("\n", "\t ");

                    // Restore the double quotes that are part of the cell contents by replacing
                    // each place holder with a double quote
                    data = data.replaceAll(EMBEDDED_QUOTE, "\"");

                    // Break the data string into the individual cells. The size of the array is
                    // specified to prevent the split command from discarding any empty trailing
                    // cells
                    String[] cellData = data.split("\t ", numRows * numColumns);

                    // Step through each cell
                    for (int index = 0; index < cellData.length; index++)
                    {
                        // Check if the cell contains an embedded new line character place holder
                        if (cellData[index].contains(EMBEDDED_NEW_LINE))
                        {
                            // Replace the place holder with a new line character, then remove the
                            // leading and trailing double quote characters
                            cellData[index] = cellData[index].replaceAll(EMBEDDED_NEW_LINE, " ");
                            cellData[index] = cellData[index].substring(1,
                                                                        cellData[index].length()
                                                                           - 1);
                        }
                    }

                    // Paste the data from the clipboard into the table
                    pasteData(cellData, numColumns, isInsert, isAddIfNeeded, false, true, true);
                }
                catch (Exception e)
                {
                    CcddUtilities.displayException(e, table);
                }
            }
        }
        catch (Exception e)
        {
            // Inform the user that an error occurred retrieving the clipboard values
            new CcddDialogHandler().showMessageDialog(table,
                                                      "<html><b>Cannot retrieve clipboard values; cause '</b>"
                                                             + e.getMessage()
                                                             + "<b>'",
                                                      "Clipboard Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**********************************************************************************************
     * Paste the contents of the supplied cell data array into the table. Depending on the input
     * flag values, current rows are either overwritten, excess rows are discarded, or new rows are
     * inserted below the currently selected row to contain the pasted data. The currently selected
     * column determines the column alignment for the pasted data. Pasted column data beyond the
     * table boundaries are discarded
     *
     * @param cellData
     *            array of cell values to be inserted
     *
     * @param numColumns
     *            number of columns represented by the cell data array
     *
     * @param isInsert
     *            true to add new rows to contain the pasted data; false to overwrite existing
     *            cells in the paste range and only add rows if needed
     *
     * @param isAddIfNeeded
     *            true to add new rows if the pasted data doesn't fit; false to discard excess rows
     *
     * @param startFirstColumn
     *            true is pasting of the data begins in the first column; false to begin pasting at
     *            the currently cell with the focus
     *
     * @param combineAsSingleEdit
     *            true to combine the pasted data as a single edit; false to not alter the undo
     *            manager's handling of the edit sequence (this allows handling externally so that
     *            other edits may be grouped with the paste operation)
     *
     * @param highlightPastedData
     *            true to highlight the cells containing the pasted data
     *
     * @return true if the user elected to cancel pasting the data following a cell validation
     *         error
     *********************************************************************************************/
    protected boolean pasteData(Object[] cellData,
                                int numColumns,
                                boolean isInsert,
                                boolean isAddIfNeeded,
                                boolean startFirstColumn,
                                boolean combineAsSingleEdit,
                                boolean highlightPastedData)
    {
        Boolean showMessage = true;
        int modelRow = 0;

        // Check if the pasted data should be combined into a single edit operation
        if (combineAsSingleEdit)
        {
            // End any active edit sequence, then disable auto-ending so that the paste operation
            // can be handled as a single edit for undo/redo purposes
            undoManager.endEditSequence();
            undoHandler.setAutoEndEditSequence(false);
        }

        // Get the table data array
        List<Object[]> tableData = getTableDataList(false);

        // Calculate the number of row to be inserted
        int numRows = cellData.length / numColumns;

        // Check if no row is selected
        if (getSelectedRow() == -1)
        {
            // Clear the column selection. The column selection can remain in effect after an undo
            // action that clears the row selection
            getColumnModel().getSelectionModel().clearSelection();
        }

        // Determine the starting column and ending column for pasting the data. If no column is
        // selected then default to the first column. Data pasted outside of the column range is
        // ignored
        int startColumn = startFirstColumn
                                           ? 0
                                           : Math.max(Math.max(getSelectedColumn(), 0),
                                                      getSelectedColumn() + getSelectedColumnCount() - 1);
        int endColumn = startColumn + numColumns - 1;
        int endColumnSelect = Math.min(endColumn, getColumnCount() - 1);

        // Determine the starting row for pasting the data. If no row is selected then default to
        // the first row
        int startRow = Math.max(getSelectedRow(),
                                getSelectedRow() + getSelectedRowCount() - 1);

        // Check if no row is selected or if the table contains no rows
        if (startRow == -1)
        {
            // Set the start row to the first row
            startRow = 0;
        }

        // Check if the table contains any rows
        if (tableModel.getRowCount() != 0)
        {
            // Get the row index in model coordinates
            modelRow = convertRowIndexToModel(startRow);
        }

        // Check if the data is to be inserted versus overwriting existing cells
        if (isInsert)
        {
            // Adjust the starting row index to the one after the selected row
            startRow = startRow + 1;
            modelRow++;
        }

        // Determine the ending row for pasting the data
        int endRow = startRow + numRows;

        // Clear the cell selection
        clearSelection();

        // Calculate the difference between the row's view and model coordinates
        int rowModelDelta = modelRow - startRow;

        // Step through each new row
        for (int index = 0, row = startRow; row < endRow
                                            && showMessage != null; row++, modelRow++)
        {
            // Check if inserting is in effect and the cell value is null
            if (isInsert && index < cellData.length && cellData[index] == null)
            {
                // Replace the null with a blank
                cellData[index] = "";
            }

            // Check if a row needs to be inserted to contain the cell data
            if (isInsert
                || (isAddIfNeeded && modelRow == tableData.size()))
            {
                // Insert a row at the selection point
                tableData.add(modelRow, getEmptyRow());
            }

            // Store the index into the array of data to be pasted
            int indexSave = index;

            // If pasting values over existing ones it's possible that the check for a cell being
            // alterable will return false due to other cells in the row that haven't yet been
            // pasted over. To overcome this two passes for each row are made; first cells
            // containing blanks in the pasted data are pasted, then the cells that are not empty
            // are pasted
            for (int pass = 1; pass <= 2; pass++)
            {
                // Check if this is the second pass through the row's columns
                if (pass == 2)
                {
                    // Reset the index into the array of data to be pasted so that the non-blank
                    // cells can be processed
                    index = indexSave;
                }

                // Step through the columns, beginning at the one with the focus
                for (int column = startColumn; column <= endColumn
                                               && showMessage != null; column++)
                {
                    // Convert the view column index to model coordinates
                    int modelColumn = convertColumnIndexToModel(column);

                    // Get the old cell value
                    Object oldValue = tableData.get(modelRow)[modelColumn];
                    Object newValue;

                    // Check if the cell is displayed as a check box (boolean value)
                    if (oldValue instanceof Boolean)
                    {
                        // Get the new cell value as a boolean. Use a blank for any pasted value
                        // not equal to "true" or "false". If the number of cells to be filled
                        // exceeds the stored values then use "false" as the default. A null paste
                        // value indicates that the current cell's value won't be overwritten
                        newValue = index < cellData.length
                                                           ? (cellData[index] != null
                                                                                      ? (cellData[index].equals("true")
                                                                                                                        ? true
                                                                                                                        : (cellData[index].equals("false")
                                                                                                                                                           ? false
                                                                                                                                                           : ""))
                                                                                      : (isInsert
                                                                                                  ? false
                                                                                                  : null))
                                                           : false;
                    }
                    // The cell displays text
                    else
                    {
                        // Get the new cell value as text, cleaning up the value if needed. If the
                        // number of cells to be filled exceeds the stored values then use a blank
                        // as the default. A null paste value indicates that the current cell's
                        // value won't be overwritten
                        newValue = index < cellData.length
                                                           ? (cellData[index] != null
                                                                                      ? cleanUpCellValue(cellData[index],
                                                                                                         modelRow,
                                                                                                         modelColumn)
                                                                                      : (isInsert
                                                                                                  ? ""
                                                                                                  : null))
                                                           : "";
                    }

                    // Check if the paste value isn't null (a null value indicates that the current
                    // cell's value won't be overwritten). For the first pass through this row's
                    // column process only blank cells; for the second pass process only non-blank
                    // cells. If one of these criteria is met then check if the column index is
                    // within the table boundaries and if the cell is alterable
                    if (newValue != null
                        && ((pass == 1 && newValue.toString().isEmpty())
                            || (pass == 2 && !newValue.toString().isEmpty()))
                        && modelColumn < tableModel.getColumnCount()
                        && isDataAlterable(tableData.get(modelRow), modelRow, modelColumn))
                    {
                        // Check if the value has changed and, if this values are being inserted,
                        // that the value isn't blank
                        if (!oldValue.equals(newValue)
                            && !(isInsert && newValue.toString().isEmpty()))
                        {
                            // Insert the value into the cell
                            tableData.get(modelRow)[modelColumn] = newValue;

                            // Validate the new cell contents
                            showMessage = validateCellContent(tableData,
                                                              modelRow,
                                                              modelColumn,
                                                              oldValue,
                                                              newValue,
                                                              showMessage,
                                                              cellData.length > 1);

                            // Check if the user selected the Cancel button following an invalid
                            // input
                            if (showMessage == null)
                            {
                                // Stop pasting data
                                continue;
                            }
                        }
                    }

                    // Increment the index to the next value to paste
                    index++;
                }
            }
        }

        // Check if the user hasn't selected the Cancel button following an invalid input
        if (showMessage != null)
        {
            // Load the array of data into the table
            loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);

            // Check if automatic edit sequence ending is in effect
            if (getUndoHandler().isAutoEndEditSequence())
            {
                // Flag the end of the editing sequence for undo/redo purposes
                undoManager.endEditSequence();
            }

            // Check if the pasted data should be highlighted
            if (highlightPastedData)
            {
                // Select all of the rows and columns into which the data was pasted
                setRowSelectionInterval(startRow + rowModelDelta, endRow - 1 + rowModelDelta);
                setColumnSelectionInterval(startColumn, endColumnSelect);

                // Select the pasted cells and force the table to be redrawn so that the changes
                // are displayed
                setSelectedCells(startRow + rowModelDelta,
                                 endRow - 1 + rowModelDelta,
                                 startColumn,
                                 endColumnSelect);
                repaint();
            }
        }

        // Set the flag that indicates the last edited cell's content is valid
        setLastCellValid(true);

        // Check if the pasted data should be combined into a single edit operation
        if (combineAsSingleEdit)
        {
            // Re-enable auto-ending of the edit sequence and end the sequence. The pasted data can
            // be removed with a single undo if desired
            undoHandler.setAutoEndEditSequence(true);
            undoManager.endEditSequence();
        }

        return showMessage == null;
    }

    /**********************************************************************************************
     * Move the rows in the specified direction and update the cell selection
     *
     * @param startRow
     *            selected starting row, in model coordinates
     *
     * @param endRow
     *            selected ending row, in model coordinates
     *
     * @param toRow
     *            target row to move the selected row(s) to, in model coordinates
     *
     * @param selected
     *            cell selection class
     *
     * @param rowDelta
     *            row move direction and magnitude
     *********************************************************************************************/
    protected void performRowMove(int startRow,
                                  int endRow,
                                  int toRow,
                                  MoveCellSelection selected,
                                  int rowDelta)
    {
        // Flag the end of the editing sequence for undo/redo purposes
        undoManager.endEditSequence();

        // Move the selected row(s) up or down one row
        tableModel.moveRow(startRow, endRow, toRow);

        // Update the cell selection
        selected.moveCellSelection(rowDelta, 0);

        // Scroll the window to keep the moved row(s) visible
        scrollToRow(convertRowIndexToView(startRow + rowDelta));

        // Flag the end of the editing sequence for undo/redo purposes
        undoManager.endEditSequence();
    }

    /**********************************************************************************************
     * Move the selected row(s) up one row
     *********************************************************************************************/
    protected void moveRowUp()
    {
        // Get the selected cells
        MoveCellSelection selected = new MoveCellSelection();

        // Check if at least one row is selected and it doesn't include the topmost row
        if (selected.getStartRow() > 0)
        {
            // Move the selected row(s) up
            performRowMove(convertRowIndexToModel(selected.getStartRow()),
                           convertRowIndexToModel(selected.getEndRow()),
                           convertRowIndexToModel(selected.getStartRow() - 1),
                           selected,
                           -1);
        }
    }

    /**********************************************************************************************
     * Move the selected row(s) down one row
     *********************************************************************************************/
    protected void moveRowDown()
    {
        // Get the selected cells
        MoveCellSelection selected = new MoveCellSelection();

        // Check if at least one row is selected and it doesn't include the bottom row
        if (selected.getStartRow() != -1
            && selected.getEndRow() < getRowCount() - 1)
        {
            // Move the selected row(s) down
            performRowMove(convertRowIndexToModel(selected.getStartRow()),
                           convertRowIndexToModel(selected.getEndRow()),
                           convertRowIndexToModel(selected.getStartRow() + 1),
                           selected,
                           1);
        }
    }

    /**********************************************************************************************
     * Remove one or more rows from the table
     *
     * @param rows
     *            array of indices of the rows to remove, view coordinates
     *********************************************************************************************/
    protected void removeRows(int[] rows)
    {
        // Get the table data array
        List<Object[]> tableData = getTableDataList(false);

        // Get the index of the first row to remove
        int nextRow = rows[rows.length - 1];

        // Step through the rows to remove in reverse order
        for (int row = rows.length - 1; row >= 0; row--)
        {
            // Check if the row to remove hasn't been removed already
            if (rows[row] <= nextRow)
            {
                // Delete the row and get the index of the next row to remove
                nextRow = removeRow(tableData, convertRowIndexToModel(rows[row]));
            }
        }

        // Load the array of data, with the selected row(s) removed, into the table
        loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);
    }

    /**********************************************************************************************
     * Remove a row from the table
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param modelRow
     *            row to remove (model coordinates)
     *
     * @return The index of the row prior to the deleted row's index
     *********************************************************************************************/
    protected int removeRow(List<Object[]> tableData, int modelRow)
    {
        tableData.remove(modelRow);
        return --modelRow;
    }

    /**********************************************************************************************
     * Move the selected column(s) left one column
     *********************************************************************************************/
    protected void moveColumnLeft()
    {
        // Get the selected cells
        MoveCellSelection selected = new MoveCellSelection();

        // Check if a valid column is selected: can't move the first column to the left
        if (selected.getStartColumn() != -1 && selected.getStartColumn() != 0)
        {
            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();

            // Step through each selected column
            for (int column = selected.getStartColumn(); column <= selected.getEndColumn(); column++)
            {
                // Move the column to the left
                getColumnModel().moveColumn(column, column - 1);
            }

            // Update the cell selection
            selected.moveCellSelection(0, -1);

            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();
        }
    }

    /**********************************************************************************************
     * Move the selected column(s) right one column
     *********************************************************************************************/
    protected void moveColumnRight()
    {
        // Get the selected cells
        MoveCellSelection selected = new MoveCellSelection();

        // Check if a valid column is selected: can't move the last column further to the right
        if (selected.getEndColumn() != -1 && selected.getEndColumn() < getColumnCount() - 1)
        {
            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();

            // Step through each selected column in reverse order
            for (int column = selected.getEndColumn(); column >= selected.getStartColumn(); column--)
            {
                // Move the column to the right
                getColumnModel().moveColumn(column, column + 1);
            }

            // Update the cell selection
            selected.moveCellSelection(0, 1);

            // Flag the end of the editing sequence for undo/redo purposes
            undoManager.endEditSequence();
        }
    }

    /**********************************************************************************************
     * Set the text color for the specified row
     *
     * @param row
     *            row index
     *
     * @param color
     *            row color; null to reset this row to the normal color scheme
     *********************************************************************************************/
    protected void setRowTextColor(int row, Color color)
    {
        // Get the index of the specified row in the lists
        int index = rowColorIndex.indexOf(row);

        // Check if the row already is in the lists
        if (index != -1)
        {
            // Check if no color is specified, or if the color is the normal text color
            if (color == null || color.equals(ModifiableColorInfo.TABLE_TEXT.getColor()))
            {
                // Remove this row's entries from the list
                rowColorIndex.remove(index);
                rowColor.remove(index);
            }
            // A special color is specified
            else
            {
                // Update the row's color in the list
                rowColor.set(index, color);
            }
        }
        // The row isn't already in the lists. Check if a color is specified and is not the normal
        // text color
        else if (color != null && !color.equals(ModifiableColorInfo.TABLE_TEXT.getColor()))
        {
            // Add the row and color to the lists
            rowColorIndex.add(row);
            rowColor.add(color);
        }

        // Force the table to redraw so that the color change takes effect
        repaint();
    }

    /**********************************************************************************************
     * Override prepareRenderer to allow adjusting the foreground and background colors of the
     * table's cells
     *********************************************************************************************/
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
    {
        JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

        // Get the index for this row's special text color, if any
        int index = rowColorIndex.indexOf(row);

        // Flag that indicates that the cell is in a selected row and that all of the columns in a
        // row should be selected if any cell in the row is selected
        boolean isSelectedRow = cellSelection == TableSelectionMode.SELECT_BY_ROW
                                && isRowSelected(row);

        // Check if this is one of the selected rows (and columns, if column selection is enabled
        // for this table) and, unless selection doesn't depend on having the focus, that the table
        // has the focus; or if the cell is in a selected row and all columns should be selected
        if (((isFocusOwner() || selectWithoutFocus)
             && isCellSelected(row, column))
            || isSelectedRow)
        {
            // Set the text (foreground) color for the selected cell(s)
            comp.setForeground(index == -1
                                           ? ModifiableColorInfo.SELECTED_TEXT.getColor()
                                           : rowColor.get(index));

            // Check if this cell has the focus (last cell selected) and the only this cell should
            // be highlighted
            if (isFocusCell(row, column) && !isSelectedRow)
            {
                // Color the cell background to indicate it has the focus
                comp.setBackground(ModifiableColorInfo.FOCUS_BACK.getColor());
            }
            // This cell doesn't have the focus or the entire row should be highlighted
            else
            {
                // Set the cells' background color to show it is selected
                comp.setBackground(ModifiableColorInfo.SELECTED_BACK.getColor());
            }
        }
        // Row is not selected and/or the table does not have the focus
        else
        {
            // Set the fore- and background colors for the non-selected row. Alternate the row
            // background colors every other row
            comp.setForeground(index == -1
                                           ? ModifiableColorInfo.TABLE_TEXT.getColor()
                                           : rowColor.get(index));
            comp.setBackground(row % 2 == 0
                                            ? getBackground()
                                            : ModifiableColorInfo.ALTERNATE_BACK.getColor());
        }

        // Check if this cell displays a text component
        if (renderer instanceof JTextComponent)
        {
            // Perform any special rendering on this cell
            doSpecialRendering(comp,
                               getValueAt(row, column).toString(),
                               isCellSelected(row, column),
                               row,
                               column);
        }

        return comp;
    }

    /**********************************************************************************************
     * Performing any special cell text rendering in the table cells. The default highlights the
     * text in cells matching the search criteria
     *
     * @param component
     *            reference to the table cell renderer component
     *
     * @param text
     *            cell text
     *
     * @param isSelected
     *            true if the cell is to be rendered with the selection highlighted
     *
     * @param row
     *            cell row, view coordinates
     *
     * @param column
     *            cell column, view coordinates
     *********************************************************************************************/
    protected void doSpecialRendering(Component component,
                                      String text,
                                      boolean isSelected,
                                      int row,
                                      int column)
    {
        // Check if the column allows highlighting
        if (isColumnHighlight(column))
        {
            // Highlight the search text instances
            CcddUtilities.highlightSearchText(component,
                                              text,
                                              isSelected
                                                         ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                         : ModifiableColorInfo.SEARCH_HIGHLIGHT.getColor(),
                                              pattern);
        }
    }

    /**********************************************************************************************
     * Highlight text in the table cells matching the supplied pattern
     *
     * @param pattern
     *            pattern based on the search criteria; null to remove matching search text
     *            highlighting
     *********************************************************************************************/
    protected void highlightSearchText(Pattern pattern)
    {
        this.pattern = pattern;
        repaint();
    }

    /**********************************************************************************************
     * Enable the table grid and set the spacing and color
     *********************************************************************************************/
    protected void setTableGrid()
    {
        // Set up the table grid lines
        setShowGrid(showGrid);
        setIntercellSpacing(new Dimension(1, 1));
        setGridColor(ModifiableColorInfo.TABLE_GRID.getColor());
    }

    /**********************************************************************************************
     * Handle a TableModelEvent event
     *********************************************************************************************/
    @Override
    public void tableChanged(final TableModelEvent tme)
    {
        // Check if a model data reload is not in progress, the table model is valid, and the table
        // has rows to display. The columns are not correct until the model data reload is complete
        if (!isReloadData && tableModel != null && getRowCount() != 0)
        {
            // Flag to indicate if the changed row indices are valid and the row heights need to be
            // updated. Initialize to true if the table is showing on the screen, or false if the
            // table isn't displayed. If the table (or its container) isn't showing yet then a null
            // exception can infrequently occur in updateRowHeights() due to what appears to be a
            // race condition or Swing bug. However, the default tableChanged() must be called
            // under certain conditions even when the table isn't showing so that the conversion of
            // view to/from model coordinates can be made
            boolean isValid = table.isShowing();

            // First and last rows changed, in view coordinates
            int firstRow = 0;
            int lastRow = 0;

            // Check if this is the header row or if the row isn't specified
            if (tme == null || tme.getFirstRow() == TableModelEvent.HEADER_ROW)
            {
                // Check if the table if the table is showing on the screen
                if (isValid)
                {
                    // Determine first and last rows that are currently visible. Get the
                    // coordinates of the table's visible rectangle
                    Rectangle visRect = getVisibleRect();

                    // Determine the first (upper) row. If a column is being resized or the table
                    // has no rows then set to 0 (the first table row), otherwise set to the first
                    // visible row
                    firstRow = inLayout
                                        ? 0
                                        : Math.max(rowAtPoint(visRect.getLocation()), 0);

                    // Move the rectangle down the height of the table, then back 1 pixel to remain
                    // within the table boundaries
                    visRect.translate(0, visRect.height - 1);

                    // Get the last visible row plus 1 (as required by updateRowHeights). Defaults
                    // to 0 if the table has no rows
                    lastRow = rowAtPoint(visRect.getLocation()) + 1;

                    // Check if the calculated last row is greater than the total number of rows in
                    // the table
                    if (lastRow == 0)
                    {
                        // Set the last row to the table's visible row count
                        lastRow = getRowCount();
                    }
                }
            }
            // A specific row or rows changed
            else
            {
                // Call the baseline method. This is required in order for the session event log to
                // display and to allow conversion of view to/from model coordinates
                super.tableChanged(tme);

                // Check if the table if the table is showing on the screen
                if (isValid)
                {
                    // Check if the row indices are invalid
                    if (tme.getFirstRow() >= tableModel.getRowCount()
                        || tme.getLastRow() == Integer.MAX_VALUE)
                    {
                        isValid = false;
                    }
                    // The row indices are valid
                    else
                    {
                        // Get the index of the first changed row in view coordinates
                        firstRow = convertRowIndexToView(tme.getFirstRow());

                        // Check if the row is invisible
                        if (firstRow == -1)
                        {
                            // Set the row index to the first row
                            firstRow = 0;
                        }

                        // Set the row index to the last changed row plus one
                        lastRow = convertRowIndexToView(tme.getLastRow()) + 1;
                    }
                }
            }

            // Check if the row indices are valid
            if (isValid)
            {
                // Update the affected row(s) height(s)
                updateRowHeights(firstRow, lastRow);
            }
        }
    }

    /**********************************************************************************************
     * Update the table's row heights. Each row's height is based on the contents of the cells in
     * that row; the cell with the greatest height determines the height for the entire row
     *
     * @param first
     *            row with which to begin height check
     *
     * @param last
     *            row with which to end height check
     *********************************************************************************************/
    private void updateRowHeights(int first, int last)
    {
        // Step through the specified rows
        for (int row = first; row < last; row++)
        {
            // Initialize a minimum row height
            int minRowHeight = 5;

            // Step through each visible column in the row
            for (int column = 0; column < getColumnCount(); column++)
            {
                // Use the prepareRenderer() to calculate the height required to display the cell's
                // contents
                Component comp = super.prepareRenderer(getCellRenderer(row, column),
                                                       row,
                                                       column);

                // Store the largest minimum height found
                minRowHeight = Math.max(minRowHeight, comp.getPreferredSize().height);
            }

            // Check if the new row height differs from the current height
            if (minRowHeight != getRowHeight(row))
            {
                // Check if this is the table's only row
                if (row == 0 && getRowCount() == 1)
                {
                    // Set the row height for all of the table's rows. If just this row's height is
                    // set then the height update isn't implemented
                    setRowHeight(minRowHeight);
                }
                // Not the only row
                else
                {
                    // Update the row's height
                    setRowHeight(row, minRowHeight);
                }
            }
        }
    }

    /**********************************************************************************************
     * Determine if the width of the table needs to be resized to fit the dialog containing it
     *
     * @return true if the width of the table needs to be adjusted to fit the dialog containing it
     *********************************************************************************************/
    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return getPreferredSize().width < getParent().getWidth();
    }

    /**********************************************************************************************
     * Layout the table's columns and rows
     *********************************************************************************************/
    @Override
    public void doLayout()
    {
        // Check if the table width is less than the dialog's
        if (getPreferredSize().width < getParent().getWidth())
        {
            // Set the flag to instruct the super.doLayout() method to add extra width to a
            // subsequent column when the user is adjusting a specific column's width. Note that
            // this does not affect the distribution of extra width if the table's container is
            // resized
            autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
        }

        // Set the flag to indicate that the table layout is in progress, perform the layout, then
        // reset the flag. This prevents columnMarginChanged() from interfering with the layout
        // resizing
        inLayout = true;

        // Lay out the table columns and rows
        super.doLayout();

        // Turn off the layout flag
        inLayout = false;

        // Reset the flag to prevent automatic resizing of the table's columns
        autoResizeMode = AUTO_RESIZE_OFF;
    }

    /**********************************************************************************************
     * Handle a column resize event
     *********************************************************************************************/
    @Override
    public void columnMarginChanged(ChangeEvent ce)
    {
        // Get the column being resized
        TableColumn resizingColumn = getTableHeader().getResizingColumn();

        // Check if a specific column is referenced (versus the container for the table) and that a
        // table layout update is not in progress
        if (resizingColumn != null && !inLayout)
        {
            // Set the column's preferred width to its current width
            resizingColumn.setPreferredWidth(resizingColumn.getWidth());

            // Redraw the table
            resizeAndRepaint();
        }

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Execute after all pending Swing events are finished
             *************************************************************************************/
            @Override
            public void run()
            {
                // Set the flag so that it instructs tableChanged() to recalculate the heights of
                // all rows prior to the last one displayed (true) or only for those currently
                // displayed (false). Recalculating back to the beginning is needed for the scroll
                // bar slider to resize as needed, based on the number of rows displayed and the
                // total number of rows. As the number of rows in the table increases the
                // calculation takes a proportionally longer time to complete. The slider size
                // ceases to change once it reaches a minimum so calculating past this point is
                // unnecessary; for this case the recalculation is skipped
                inLayout = table.getRowCount() < 300;

                // Update the table row heights
                tableChanged(null);

                inLayout = false;
            }
        });
    }

    /**********************************************************************************************
     * Listen for changes made to individual table cells via the TableCellEditor. When editing
     * begins, the cell's value is saved, and when editing ends the cell contents is checked
     * against the saved value to see if it changed. If so, the new contents is validated and if
     * found to be invalid then the original value is restored. Also, if the user exits editing via
     * the ESC key the cell contents is replaced by the original value
     *********************************************************************************************/
    private class TableCellListener implements PropertyChangeListener, Runnable
    {
        private int editRow;
        private int editColumn;
        private Object oldValue;
        private Object newValue;

        // Flag to indicate the editing steps in run() are complete
        private boolean wasEditing = false;

        /******************************************************************************************
         * TableCellListener class constructor
         *****************************************************************************************/
        TableCellListener()
        {
            // Add a property change listener
            addPropertyChangeListener(this);
        }

        /******************************************************************************************
         * Create a TableCellListener with a copy of all the data relevant to the change of data
         * for a given cell
         *
         * @param row
         *            cell row index, model coordinates
         *
         * @param column
         *            cell column index, model coordinates
         *
         * @param oldValue
         *            previous contents of the cell
         *
         * @param newValue
         *            new contents for the cell
         *****************************************************************************************/
        TableCellListener(int row, int column, Object oldValue, Object newValue)
        {
            this.editRow = row;
            this.editColumn = column;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        /******************************************************************************************
         * Listen for property changes
         *****************************************************************************************/
        @Override
        public void propertyChange(PropertyChangeEvent pce)
        {
            // Check if the property being changed is from the cell editor
            if (pce.getPropertyName().equals("tableCellEditor"))
            {
                // Check if editing has initiated on the cell
                if (isEditing())
                {
                    // The invokeLater call is necessary since the row and column being edited are
                    // not available when the "tableCellEditor" PropertyChangeEvent is fired. This
                    // call results in the "run" method being invoked asynchronously, which allows
                    // the row and column to be determined
                    SwingUtilities.invokeLater(this);
                }
                // Editing has completed on this cell
                else if (wasEditing)
                {
                    // Reset the flag that indicates editing completed
                    wasEditing = false;

                    // Get the cell value from the table
                    newValue = tableModel.getValueAt(editRow, editColumn);

                    // Check if this cell isn't a check box
                    if (!isColumnBoolean(editColumn))
                    {
                        // Store the value back in the table; this causes any clean-up steps
                        // dictated by the table model to be performed (e.g., removal of leading
                        // and trailing white space characters)
                        tableModel.setValueAt(newValue,
                                              editRow,
                                              editColumn,
                                              false);

                        // Reload the value with any clean-up performed on it
                        Object newValueCleaned = tableModel.getValueAt(editRow, editColumn);

                        // Check if the clean-up resulted in a change to the value that restored it
                        // to the old value
                        if (!newValue.equals(newValueCleaned) && newValueCleaned.equals(oldValue))
                        {
                            // Perform any steps to be done if the cell value changed. The
                            // pre-clean-up value can result in a change being detected in the
                            // table value, but with the clean-up the cell is restored to the
                            // original value (for example, a space is entered which is trimmed
                            // when the cell is exited). For this case the owner change steps
                            // aren't automatically performed so it's called explicitly here
                            undoManager.ownerHasChanged();
                        }

                        // Store the cleaned-up value
                        newValue = newValueCleaned;
                    }

                    // Check if the data has changed
                    if (!newValue.equals(oldValue))
                    {
                        // Check if the cell is flagged for special handling
                        if (newValue.toString().startsWith(REPLACE_INDICATOR))
                        {
                            // Remove the flag and store the 'cleaned' value back in the table
                            newValue = newValue.toString().replaceFirst("^"
                                                                        + REPLACE_INDICATOR,
                                                                        "");
                            tableModel.setValueAt(newValue, editRow, editColumn, false);
                        }

                        // Make a copy of the data in case another cell starts editing while
                        // processing this cell's change
                        new TableCellListener(editRow, editColumn, oldValue, newValue);

                        // Get the table data as a list
                        List<Object[]> tableData = table.getTableDataList(false);

                        // Handle changes to the cell contents
                        validateCellContent(tableData,
                                            editRow,
                                            editColumn,
                                            oldValue,
                                            newValue,
                                            true,
                                            false);

                        // Check that the cell change was accepted
                        if (!oldValue.equals(tableData.get(editRow)[editColumn]))
                        {
                            // Load the array of data into the table to reflect the updates made
                            // during validation
                            loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);

                            // Flag the end of the editing sequence for undo/redo purposes
                            undoManager.endEditSequence();

                            // Check if the cell is flagged for special handling
                            if (oldValue.toString().startsWith(REPLACE_INDICATOR))
                            {
                                // Get this cell's editor component
                                Component comp = ((DefaultCellEditor) getCellEditor(table.convertRowIndexToView(editRow),
                                                                                    table.convertColumnIndexToView(editColumn))).getComponent();

                                // Check if the cell contains a combo box
                                if (comp instanceof JComboBox)
                                {
                                    // Remove the specially flagged item from the combo box list
                                    // and select the identical item minus the flag
                                    ((JComboBox<?>) comp).removeItem(oldValue);
                                    oldValue = oldValue.toString().replaceFirst("^"
                                                                                + REPLACE_INDICATOR,
                                                                                "");
                                    ((JComboBox<?>) comp).setSelectedItem(oldValue);
                                }
                            }
                        }
                        // Check if the model (displayed) value matches the one as altered
                        // (potentially) by the cell content validation
                        else if (!tableData.get(editRow)[editColumn].equals(tableModel.getValueAt(editRow,
                                                                                                  editColumn)))
                        {
                            // Load the altered value into the model so that the display matches
                            // the altered value
                            tableModel.setValueAt(oldValue, editRow, editColumn, false);
                        }
                    }
                }
            }
        }

        /******************************************************************************************
         * Get the selected row and column, initiate editing (if needed), convert the row index to
         * the table model, and store the cell original cell value
         *****************************************************************************************/
        @Override
        public void run()
        {
            // Check if the cell is actively being edited
            if (isEditing())
            {
                // Get the cell row and column that's being edited
                editRow = table.getEditingRow();
                editColumn = table.getEditingColumn();
            }
            // Editing is not active
            else
            {
                // Get the cell row and column that has the focus
                editRow = getSelectedRow() + getSelectedRowCount() - 1;
                editColumn = getSelectedColumn() + getSelectedColumnCount() - 1;

                // Initiate editing on the selected cell. This covers a specific case where the
                // editor doesn't engage following resizing (smaller) a single table column
                editCellAt(editRow, editColumn);
            }

            // Check that the row and column are valid
            if (editRow >= 0 && editColumn >= 0)
            {
                // Set the flag that indicates editing completed
                wasEditing = true;

                // Convert the row and column indices to the table model index, in case the rows
                // have been sorted or the columns moved
                editRow = convertRowIndexToModel(editRow);
                editColumn = convertColumnIndexToModel(editColumn);

                // Store the cell's original contents so that the cell can be restored if the new
                // value is invalid or abandoned (by the user pressing the ESC key)
                oldValue = tableModel.getValueAt(editRow, editColumn);
            }
        }
    }

    /**********************************************************************************************
     * Resize-capturing mouse listener class. This replaces the table header mouse listener so that
     * a double-click on column header's right border causes the column to be resized based on the
     * header and cell content
     *********************************************************************************************/
    private class ResizeColumnListener implements MouseListener
    {
        private MouseListener headerListener;
        private final JTableHeader header;

        /******************************************************************************************
         * Resize-capturing mouse listener class constructor
         *****************************************************************************************/
        ResizeColumnListener()
        {
            // Get the reference to the table's header
            header = getTableHeader();

            // Step through each mouse listener registered with the table
            for (MouseListener ml : header.getMouseListeners())
            {
                // Check if the class name references the header
                if (ml.getClass().toString().contains("BasicTableHeaderUI"))
                {
                    // Store the mouse listener for the table header
                    headerListener = ml;

                    // Replace the mouse listener with the resize-capturing listener
                    header.removeMouseListener(ml);
                    header.addMouseListener(this);
                }
            }
        }

        /******************************************************************************************
         * Override in order to capture double click of the mouse button so that a column can be
         * resized
         *****************************************************************************************/
        @Override
        public void mouseClicked(MouseEvent me)
        {
            // Check if the cursor isn't the one for resizing (indicates that the mouse is outside
            // of the "resize zone")
            if (header.getCursor() != Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
            {
                // Perform the default action
                headerListener.mouseClicked(me);
            }
            // Mouse pointer is in the "resize zone"; check if the mouse button was double clicked
            else if (me.getClickCount() == 2)
            {
                // Adjust the mouse x-coordinate to get the correct column (the one to the left of
                // the pointer). Resizing is active when the pointer is +/-3 pixels from the border
                // between columns
                Point p = me.getPoint();
                p.x -= 3;

                // Get the index of the selected column
                int column = columnAtPoint(p);

                // Check if the column is resizable (it has to be if the resize cursor appears)
                if (isColumnResizable(convertColumnIndexToModel(column)))
                {
                    // Calculate and set the specified column's width
                    calcAndSetColumnWidth(column);

                    // Force table to redraw
                    tableModel.fireTableDataChanged();
                    tableModel.fireTableStructureChanged();
                }

                // Generate event to reset the cursor
                header.dispatchEvent(new MouseEvent(header,
                                                    MouseEvent.MOUSE_MOVED,
                                                    me.getWhen(),
                                                    me.getModifiers(),
                                                    me.getX(),
                                                    me.getY(),
                                                    0,
                                                    false));
            }
        }

        /******************************************************************************************
         * Perform the default action
         *****************************************************************************************/
        @Override
        public void mouseEntered(MouseEvent me)
        {
            headerListener.mouseEntered(me);
        }

        /******************************************************************************************
         * Perform the default action
         *****************************************************************************************/
        @Override
        public void mouseExited(MouseEvent me)
        {
            headerListener.mouseExited(me);
        }

        /******************************************************************************************
         * Perform the default action
         *****************************************************************************************/
        @Override
        public void mousePressed(MouseEvent me)
        {
            headerListener.mousePressed(me);
        }

        /******************************************************************************************
         * Perform the default action
         *****************************************************************************************/
        @Override
        public void mouseReleased(MouseEvent me)
        {
            headerListener.mouseReleased(me);
        }
    }

    /**********************************************************************************************
     * Reset the table header renderer and the mouse listener for column resizing (via mouse button
     * double click) following a change to the look and feel
     *********************************************************************************************/
    protected void resetHeaderOnLaFChange()
    {
        // Remove the current table header mouse listener for column resizing and replace it with
        // the version that captures double clicks in order to implement automatic resizing. This
        // is necessary since the header UI, which responds to mouse inputs, is 'damaged' during a
        // look & feel change
        getTableHeader().removeMouseListener(resizeColumnListener);
        resizeColumnListener = new ResizeColumnListener();

        // Reset the table header renderer so that the header is drawn to match the new look & feel
        setHeaderRenderer();
    }

    /**********************************************************************************************
     * Output the table to the user-selected printer (or file)
     *
     * @param tableName
     *            table name; displayed at the top of each printed page
     *
     * @param fieldInformation
     *            data field information for the fields associated with this table; null if no data
     *            fields are associated with the table
     *
     * @param parent
     *            parent window for this table
     *
     * @param orientation
     *            page orientation; e.g., PageFormat.LANDSCAPE or PageFormat.PORTRAIT
     *********************************************************************************************/
    protected void printTable(String tableName,
                              List<FieldInformation> fieldInformation,
                              Component parent,
                              int orientation)
    {
        try
        {
            GraphicsConfiguration gc;

            // Create a printer job
            PrinterJob printerJob = PrinterJob.getPrinterJob();

            // The native print dialog does not allow simple positioning on the screen relative to
            // another component. However, the ServiceUI.printDialog() method, which calls
            // PrinterJob.printDialog(), does allow setting the dialog's x and y coordinates. The
            // dimensions of the print dialog must be known in order to center it over its parent,
            // but the size is unknown until the dialog is instantiated. Therefore, a dummy dialog
            // is created using the same call within ServiceUI.printDialog() and the dialog's size
            // is taken from it. The dialog's x, y coordinates can then be determined
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            DocFlavor flavor = null;
            PrintService[] services = PrintServiceLookup.lookupPrintServices(flavor,
                                                                             attributes);
            PrintService defaultService = printerJob.getPrintService();

            // Get the dialog/frame that contains the table
            Component comp = table.getTopLevelAncestor();

            // Create a dummy dialog in order to obtain the print dialog's dimensions
            JDialog dialog = new JDialog();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(false);
            Rectangle newDlgSize = dialog.getBounds();
            dialog.dispose();
            
            // Get the array of graphics devices (this accounts for multiple screens)
            GraphicsDevice[] gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();

            // Check if more than one screen exists
            if (gd.length > 1)
            {
                // Get the graphics configuration for the screen on which the component resides
                gc = gd[0].getDefaultConfiguration();
            }
            // Only one screen is present
            else
            {
                // Get the component's graphics configuration
                gc = comp.getGraphicsConfiguration();
            }

            // Now that the dialog's size is known the print dialog's position can be calculated so
            // as to center it over calling component, adjusting the location so that the dialog
            // appears fully on the screen in which the component resides
            Dimension compSize = comp.getSize();
            Point adjLocation = CcddDialogHandler.adjustDialogLocationForScreen(new Rectangle(comp.getX()
                                                                                              + ((compSize.width
                                                                                                  - newDlgSize.width)
                                                                                                 / 2),
                                                                                              comp.getY()
                                                                                                       + ((compSize.height
                                                                                                           - newDlgSize.height)
                                                                                                          / 2),
                                                                                              newDlgSize.width,
                                                                                              newDlgSize.height));

            // Display a printer dialog to obtain the desired destination and output to the
            // selected printer
            if (ServiceUI.printDialog(gc,
                                      adjLocation.x,
                                      adjLocation.y,
                                      services,
                                      defaultService,
                                      flavor,
                                      attributes) != null)
            {
                // Set the page format
                PageFormat pageFormat = new PageFormat();
                pageFormat.setOrientation(orientation);

                // Create a book object for the table and data fields (if applicable)
                Book book = new Book();

                // Determine the number of pages to print the table. The printable object is
                // altered during the page counting process, so it cannot be reused when creating
                // the page wrapper below
                int tblPages = getNumberOfPages(getPrintable(JTable.PrintMode.FIT_WIDTH,
                                                             new MessageFormat(tableName),
                                                             new MessageFormat("page {0}")),
                                                pageFormat);

                // Add the table to the book object
                book.append(new PageWrapper(getPrintable(JTable.PrintMode.FIT_WIDTH,
                                                         new MessageFormat(tableName),
                                                         new MessageFormat("page {0}")),
                                            0),
                            pageFormat,
                            tblPages);

                // Check if data fields are provided
                if (fieldInformation != null && !fieldInformation.isEmpty())
                {
                    String fields = "";

                    // Step through each data field
                    for (FieldInformation fieldInfo : fieldInformation)
                    {
                        // Append the field name and value to the output string
                        fields += "   "
                                  + fieldInfo.getFieldName()
                                  + ":  "
                                  + fieldInfo.getValue()
                                  + "\n";
                    }

                    // Place the field information into a text area
                    JTextArea fldTxtArea = new JTextArea(fields);

                    // Get the printable object for the text area
                    Printable fldPrintable = fldTxtArea.getPrintable(new MessageFormat("Data Fields for "
                                                                                       + tableName),
                                                                     new MessageFormat("page {0}"));

                    // Add the fields to the book object
                    book.append(new PageWrapper(fldPrintable, tblPages),
                                pageFormat,
                                getNumberOfPages(fldPrintable, pageFormat));
                }

                // Output the book object to the selected printer or file
                printerJob.setPageable(book);
                printerJob.print();
            }
        }
        catch (PrinterException pe)
        {
            // Inform the user that printing failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Table '</b>"
                                                              + tableName
                                                              + "<b>' printing failed; cause '</b>"
                                                              + pe.getMessage()
                                                              + "<b>'",
                                                      "Print Fail",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**********************************************************************************************
     * Determine the number of pages required to print a printable object
     *
     * @param delegate
     *            printable object
     *
     * @param pageFormat
     *            page format
     *
     * @return Number of pages that would be output when for printing the printable object
     *
     * @throws PrinterException
     *             If the print job is terminated
     *********************************************************************************************/
    private int getNumberOfPages(Printable delegate,
                                 PageFormat pageFormat) throws PrinterException
    {
        int numPages = 0;

        // Create a graphics image
        Graphics g = new BufferedImage(1,
                                       1,
                                       BufferedImage.TYPE_INT_RGB).createGraphics();

        // Continue to perform while pages are found
        while (true)
        {
            // Check if no more pages remain to be output
            if (delegate.print(g, pageFormat, numPages) == Printable.NO_SUCH_PAGE)
            {
                // Exit the loop
                break;
            }

            // Increment the page count
            ++numPages;
        }

        return numPages;
    }

    /**********************************************************************************************
     * Printable wrapper class. This allows multiple printable objects to be output for the same
     * print job
     *********************************************************************************************/
    private class PageWrapper implements Printable
    {
        private final Printable delegate;
        private final int offset;

        /******************************************************************************************
         * Printable wrapper class constructor
         *
         * @param delegate
         *            printable object
         *
         * @param offset
         *            page number offset
         *****************************************************************************************/
        PageWrapper(Printable delegate, int offset)
        {
            this.offset = offset;
            this.delegate = delegate;
        }

        /******************************************************************************************
         * Override the print method to include the page offset
         *****************************************************************************************/
        @Override
        public int print(Graphics graphics,
                         PageFormat pageFormat,
                         int pageIndex) throws PrinterException
        {
            return delegate.print(graphics, pageFormat, pageIndex - offset);
        }
    }
}
