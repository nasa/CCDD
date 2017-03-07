/**
 * CFS Command & Data Dictionary keyboard handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ArrowFocusOption.HANDLE_ALL_ARROWS;
import static CCDD.CcddConstants.ArrowFocusOption.HANDLE_DOWN_ARROW;
import static CCDD.CcddConstants.ArrowFocusOption.HANDLE_UP_AND_DOWN_ARROWS;
import static CCDD.CcddConstants.ArrowFocusOption.HANDLE_UP_ARROW;
import static CCDD.CcddConstants.ArrowFocusOption.IGNORE_UP_AND_DOWN_ARROWS;
import static CCDD.CcddConstants.ArrowFocusOption.USE_DEFAULT_HANDLER;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.AbstractButton;
import javax.swing.CellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import CCDD.CcddConstants.ArrowFocusOption;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddEditorPanelHandler.UndoableCheckBox;
import CCDD.CcddEditorPanelHandler.UndoableTextArea;
import CCDD.CcddEditorPanelHandler.UndoableTextField;

/******************************************************************************
 * CFS Command & Data Dictionary keyboard handler class
 *****************************************************************************/
public class CcddKeyboardHandler
{
    // Class reference
    private final CcddMain ccddMain;
    private CcddMacroHandler macroHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddUndoManager modalUndoManager;

    /**************************************************************************
     * Keyboard handler class constructor
     * 
     * @param ccddMain
     *            reference to main class
     *************************************************************************/
    protected CcddKeyboardHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Set the keyboard handler
        setKeyboardHandler();
    }

    /**************************************************************************
     * Set the reference to the macro and data type handler classes
     *************************************************************************/
    protected void setHandlers()
    {
        macroHandler = ccddMain.getMacroHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
    }

    /**************************************************************************
     * Set the undo manager to that of an active modal dialog (e.g., the group
     * manager or macro dialogs). This must be done manually by the dialog
     * since it is modal, unlike the table and table type editors. The modal
     * undo manager must be set to null when the dialog closes so that the
     * table and table type editors undo managers can be checked
     * 
     * @param undoManager
     *            modal dialog undo manager; null to disable
     *************************************************************************/
    protected void setModalUndoManager(CcddUndoManager undoManager)
    {
        modalUndoManager = undoManager;
    }

    /**************************************************************************
     * Adjust the handling of Enter and space key inputs in order to activate
     * dialog controls, and keyboard focus changes in order to use the arrow
     * keys like Tab keys
     *************************************************************************/
    private void setKeyboardHandler()
    {
        // Get the keyboard focus manager
        final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        // Listen for key presses
        focusManager.addKeyEventDispatcher(new KeyEventDispatcher()
        {
            boolean isShowMacros = false;
            Timer releaseTimer = null;

            /******************************************************************
             * Alter the response to the Enter key to act like the Space key to
             * activate button and check box controls, and the arrow keys so as
             * to mimic the Tab and Shift+Tab keys, unless the key press
             * originates within a table or combo box. For a tabbed pane the
             * left/right arrows are left unchanged so that these are used for
             * traversing the tabbed panes, but the down and up arrows act like
             * Tab and Shift+Tab respectively
             *****************************************************************/
            @Override
            public boolean dispatchKeyEvent(final KeyEvent ke)
            {
                // Flag that indicates if the key event has been handled by
                // this method (true) or that it still needs to be processed
                // (false)
                boolean handled = false;

                // Get a reference to the component with the focus in order
                // to shorten the subsequent calls
                Component comp = ke.getComponent();

                // Check if this is a key press event, and the Ctrl, Shift, or
                // Alt key is not pressed
                if (ke.getID() == KeyEvent.KEY_PRESSED
                    && !ke.isControlDown()
                    && !ke.isShiftDown()
                    && !ke.isAltDown())
                {
                    // Check if the Enter key is pressed
                    if (ke.getKeyCode() == KeyEvent.VK_ENTER)
                    {
                        // Check if this is a button (including a color button,
                        // radio button, or check box
                        if (comp instanceof JButton
                            || comp instanceof JRadioButton
                            || comp instanceof JCheckBox)
                        {
                            // Activate the control (same as if Space key is
                            // pressed)
                            ((AbstractButton) comp).doClick();
                            handled = true;
                        }
                        // Check if this is a table
                        else if (comp instanceof CcddJTableHandler
                                 || (comp.getParent() instanceof CcddJTableHandler
                                 && !(comp instanceof JComboBox)))
                        {
                            // Handle the Enter key in the table
                            handled = tableEditCellHandler(comp);
                        }
                    }
                    // Check if the space key is pressed
                    else if (ke.getKeyCode() == KeyEvent.VK_SPACE)
                    {
                        // Check if this is a table
                        if (comp instanceof CcddJTableHandler)
                        {
                            // Handle the space key in the table
                            handled = tableEditCellHandler(comp);
                        }
                        // Check this is a combo box in a table
                        else if (comp.getParent() instanceof CcddJTableHandler
                                 && comp instanceof JComboBox)
                        {
                            // Ignore the key press
                            handled = true;
                        }
                    }
                    // Check if the key pressed is an "action" key (i.e., a key
                    // that doesn't produce a character and is not a modifier;
                    // this covers the arrow keys)
                    else if (ke.isActionKey())
                    {
                        // Assume that the default handling will be used with
                        // an arrow key
                        ArrowFocusOption arrowResponse = USE_DEFAULT_HANDLER;

                        // Check if the focus is on a tabbed pane's tab or on a
                        // slider
                        if (comp instanceof JTabbedPane
                            || comp instanceof JSlider)
                        {
                            // The left and right arrows traverse the tabs, and
                            // the up and down arrows behave like (Shift+)Tab
                            arrowResponse = HANDLE_UP_AND_DOWN_ARROWS;
                        }
                        // Check if the focus is in a text field within a table
                        else if (comp instanceof JTextField
                                 && comp.getParent() instanceof CcddJTableHandler)
                        {
                            // The up and down arrows are ignored. This
                            // prevents accidently exiting edit mode on a table
                            // cell and losing any changes
                            arrowResponse = IGNORE_UP_AND_DOWN_ARROWS;
                        }
                        // Check if the focus is on a button (including a color
                        // button), radio button, or check box
                        else if (comp instanceof JButton
                                 || comp instanceof JRadioButton
                                 || comp instanceof JCheckBox)
                        {
                            // The up and left arrow keys behave as Shift+Tab,
                            // and the down and right arrow keys behave as Tab
                            arrowResponse = HANDLE_ALL_ARROWS;
                        }
                        // Check if the focus is within a table
                        else if (comp instanceof CcddJTableHandler)
                        {
                            // Get the reference to the table with the focus
                            CcddJTableHandler table = (CcddJTableHandler) comp;

                            // Get the row containing the cell with the focus
                            // in order to shorten the subsequent calls
                            int row = table.getSelectedRow();

                            // Check if the table has no rows
                            if (row == -1)
                            {
                                // Treat the table as if it wasn't there; i.e.,
                                // the left and right arrows behave like the
                                // (Shift+)Tab key
                                arrowResponse = HANDLE_ALL_ARROWS;
                            }
                            // Check if the table has only a single row
                            else if (table.getRowCount() == 1)
                            {
                                // The up and down arrows behave like the
                                // (Shift+)Tab key
                                arrowResponse = HANDLE_UP_AND_DOWN_ARROWS;
                            }
                            // Check if the top row is selected
                            else if (row == 0)
                            {
                                // The up arrow behaves like the Shift+Tab key
                                arrowResponse = HANDLE_UP_ARROW;
                            }
                            // Check if the bottom row is selected
                            else if (row == table.getRowCount() - 1)
                            {
                                // The down arrow behaves like the Tab key
                                arrowResponse = HANDLE_DOWN_ARROW;
                            }
                        }

                        // Check if the key pressed is an arrow key and if so
                        // adjust its behavior
                        switch (ke.getKeyCode())
                        {
                            case KeyEvent.VK_LEFT:
                            case KeyEvent.VK_KP_LEFT:
                                // Check if the left arrow key should be
                                // handled
                                if (arrowResponse == HANDLE_ALL_ARROWS)
                                {
                                    // Treat the left arrow as a Shift+Tab key
                                    // and indicate that the key has been
                                    // handled
                                    focusManager.focusPreviousComponent();
                                    handled = true;
                                }

                                break;

                            case KeyEvent.VK_UP:
                            case KeyEvent.VK_KP_UP:
                                // Check if the up arrow key should be handled
                                if (arrowResponse == HANDLE_ALL_ARROWS
                                    || arrowResponse == HANDLE_UP_ARROW
                                    || arrowResponse == HANDLE_UP_AND_DOWN_ARROWS)
                                {
                                    // Treat the up arrow as a Shift+Tab key
                                    // and indicate that the key has been
                                    // handled
                                    focusManager.focusPreviousComponent();
                                    handled = true;
                                }
                                // Check if the up arrow should be ignored
                                else if (arrowResponse == IGNORE_UP_AND_DOWN_ARROWS)
                                {
                                    // Indicate that the key has been handled
                                    handled = true;
                                }

                                break;

                            case KeyEvent.VK_RIGHT:
                            case KeyEvent.VK_KP_RIGHT:
                                // Check if the right arrow key should be
                                // handled
                                if (arrowResponse == HANDLE_ALL_ARROWS)
                                {
                                    // Treat the right arrow as a Tab key and
                                    // indicate that the key has been handled
                                    focusManager.focusNextComponent();
                                    handled = true;
                                }

                                break;

                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_KP_DOWN:
                                // Check if the down arrow key should be
                                // handled
                                if (arrowResponse == HANDLE_ALL_ARROWS
                                    || arrowResponse == HANDLE_DOWN_ARROW
                                    || arrowResponse == HANDLE_UP_AND_DOWN_ARROWS)
                                {
                                    // Treat the down arrow as a Tab key and
                                    // indicate that the key has been handled
                                    focusManager.focusNextComponent();
                                    handled = true;
                                }
                                // Check if the down arrow should be ignored
                                else if (arrowResponse == IGNORE_UP_AND_DOWN_ARROWS)
                                {
                                    // Indicate that the key has been handled
                                    handled = true;
                                }

                                break;
                        }
                    }
                }
                // Check if the Ctrl key is pressed
                else if (ke.getID() == KeyEvent.KEY_PRESSED
                         && ke.isControlDown()
                         && !ke.isAltDown())
                {
                    // Check if the Ctrl+Z key is pressed
                    if (ke.getKeyCode() == KeyEvent.VK_Z)
                    {
                        // Get the currently active undo manager
                        final CcddUndoManager undoManager = getActiveUndoManager();

                        // Check if an active undo manager was found
                        if (undoManager != null)
                        {
                            // Create a runnable object to be executed
                            SwingUtilities.invokeLater(new Runnable()
                            {
                                /**********************************************
                                 * Execute after all pending Swing events are
                                 * finished so that the events occur in the
                                 * desired order
                                 *********************************************/
                                @Override
                                public void run()
                                {
                                    // Undo the previous edit action
                                    undoManager.undo();

                                    // Force the component to repaint so that
                                    // the change is visible
                                    ke.getComponent().repaint();
                                }
                            });

                            // Set the flag to indicate this key press was
                            // handled
                            handled = true;
                        }
                    }
                    // Check if the Ctrl+Y key is pressed
                    else if (ke.getKeyCode() == KeyEvent.VK_Y)
                    {
                        // Get the currently active undo manager
                        final CcddUndoManager undoManager = getActiveUndoManager();

                        // Check if an active undo manager was found
                        if (undoManager != null)
                        {
                            // Create a runnable object to be executed
                            SwingUtilities.invokeLater(new Runnable()
                            {
                                /**********************************************
                                 * Execute after all pending Swing events are
                                 * finished so that the events occur in the
                                 * desired order
                                 *********************************************/
                                @Override
                                public void run()
                                {
                                    // Redo the previous undo action
                                    undoManager.redo();

                                    // Force the component to repaint so that
                                    // the change is visible
                                    ke.getComponent().repaint();
                                }
                            });

                            // Set the flag to indicate this key press was
                            // handled
                            handled = true;
                        }
                    }
                    // Check if the Ctrl-S key is pressed while the main
                    // application window has the focus
                    else if (ke.getKeyCode() == KeyEvent.VK_S
                             && ccddMain.getMainFrame().isFocused())
                    {
                        // Open the event log search dialog
                        new CcddSearchDialog(ccddMain,
                                             SearchDialogType.LOG,
                                             null,
                                             ccddMain.getSessionEventLog());

                        // Set the flag to indicate this key press was handled
                        handled = true;
                    }
                    // Check if the Ctrl-S key is pressed in the data type
                    // editor. The following handles structure name insertion
                    else if (ke.getKeyCode() == KeyEvent.VK_S
                             && ccddMain.getDataTypeEditor() != null
                             && ccddMain.getDataTypeEditor().isFocused()
                             && ccddMain.getDataTypeEditor().getTable().isEditing()
                             && comp instanceof JTextField)
                    {
                        // Get the reference to the data type table in the
                        // dialog
                        CcddJTableHandler table = ccddMain.getDataTypeEditor().getTable();

                        // Get the row and column being edited in the table,
                        // and the contents of the edited row's base data type
                        int row = table.getEditingRow();
                        int column = table.getEditingColumn();
                        String baseType = table.getValueAt(row, DataTypeEditorColumnInfo.BASE_TYPE.ordinal()).toString();

                        // Check if the type name or C name columns are being
                        // edited and the base data type is empty or a pointer
                        if ((column == DataTypeEditorColumnInfo.USER_NAME.ordinal()
                            || column == DataTypeEditorColumnInfo.C_NAME.ordinal())
                            && (baseType.isEmpty()
                            || baseType.equals(BaseDataTypeInfo.POINTER.getName())))
                        {
                            // Insert the structure name chosen by the user
                            // into the text field at the current text
                            // insertion point
                            dataTypeHandler.insertStructureName((JTextField) comp);
                        }

                        // Set the flag to indicate this key press was handled
                        handled = true;
                    }
                    // Check if the Ctrl-M key is pressed. The following
                    // handles macro insertion and expansion
                    else if (ke.getKeyCode() == KeyEvent.VK_M)
                    {
                        // Check if the shift key is also pressed
                        // (Ctrl+Shift-M)
                        if (ke.isShiftDown())
                        {
                            // Check if the macros aren't already expanded
                            if (!isShowMacros)
                            {
                                // Get the table editor dialog with the focus
                                CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                                // Check if a table editor dialog has the focus
                                if (editorDialog != null)
                                {
                                    // Replace the macro names with their
                                    // corresponding values in the currently
                                    // selected table in this editor
                                    editorDialog.getTableEditor().expandMacros(true);
                                    isShowMacros = true;
                                }
                            }
                        }
                        // The shift key isn't pressed (Ctrl-M only). Check if
                        // this is a table cell
                        else if (comp.getParent() instanceof CcddJTableHandler)
                        {
                            // Get the table editor dialog with the focus
                            CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                            // Check if a table editor dialog has the focus and
                            // that the cell doesn't contain a combo box
                            if (editorDialog != null
                                && !(comp instanceof JComboBox))
                            {
                                // Get references to shorten subsequent calls
                                CcddTableEditorHandler editor = editorDialog.getTableEditor();
                                CcddJTableHandler table = editor.getTable();

                                // Check if a cell in the table is being edited
                                if (table.isEditing())
                                {
                                    // Get the index of the column being edited
                                    // in model coordinates
                                    int column = table.convertColumnIndexToModel(table.getEditingColumn());

                                    // Get the input type for the column being
                                    // edited
                                    InputDataType inputType = ccddMain.getTableTypeHandler().getTypeDefinition(editor.getTableInformation().getType()).getInputTypes()[column];

                                    // Insert the macro name chosen by the user
                                    // into the text field at the current
                                    // text insertion point
                                    macroHandler.insertMacroName((JTextField) comp, inputType);
                                }
                            }
                        }

                        // Set the flag to indicate this key press was handled
                        handled = true;
                    }
                    // Check if the Ctrl-E key is pressed while the focus is on
                    // a tree
                    else if (ke.getKeyCode() == KeyEvent.VK_E
                             && comp instanceof CcddCommonTreeHandler)
                    {
                        // Expand/collapse the selected node(s)
                        ((CcddCommonTreeHandler) comp).expandCollapseSelectedNodes();
                    }
                }

                // Check if the macros are currently expanded for a table and
                // that the expansion key sequence is no longer active
                if (isShowMacros
                    && ke.getID() == KeyEvent.KEY_RELEASED
                    && ke.getKeyCode() == KeyEvent.VK_M)
                {
                    // Check if the key release action timer doesn't exist
                    if (releaseTimer == null)
                    {
                        // Create the key release action timer. In Linux if a
                        // key is held it generates continuous key release
                        // events. This timer is used to ignore the key release
                        // events that are close together time-wise
                        releaseTimer = new Timer(75, new ActionListener()
                        {
                            /**************************************************
                             * Handle the key release action
                             *************************************************/
                            @Override
                            public void actionPerformed(ActionEvent ae)
                            {
                                // Get the table editor dialog with the focus
                                CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                                // Check if a table editor dialog has the focus
                                if (editorDialog != null)
                                {
                                    // Restore the macro names in the currently
                                    // selected table in this editor
                                    editorDialog.getTableEditor().expandMacros(false);
                                    isShowMacros = false;
                                }
                            }
                        });

                        // Allow the timer to send only a single expiration
                        // event
                        releaseTimer.setRepeats(false);
                    }

                    // (Re)start the key release action timer
                    releaseTimer.restart();

                    // Set the flag to indicate this key press was handled
                    handled = true;
                }

                return handled;
            }
        });
    }

    /**************************************************************************
     * Get the active table editor dialog
     * 
     * @return The active table editor dialog; null if no table editor dialog
     *         is active or no editor dialog has focus
     *************************************************************************/
    private CcddTableEditorDialog getFocusedTableEditorDialog()
    {
        CcddTableEditorDialog editor = null;

        // Step through each open table editor dialog
        for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
        {
            // Check if this editor dialog has the focus
            if (editorDialog.isFocused())
            {
                // Store the editor reference and stop searching
                editor = editorDialog;
                break;
            }
        }

        return editor;
    }

    /**************************************************************************
     * Get the active table editor, table type editor, data field editor, group
     * manager, or macro editor undo manager
     * 
     * @return The active undo manager; null if no undo manager is active or no
     *         editor has focus
     *************************************************************************/
    private CcddUndoManager getActiveUndoManager()
    {
        CellEditor cellEditor = null;
        CcddUndoManager undoManager = null;

        // Check if a modal dialog undo manager is in effect
        if (modalUndoManager != null)
        {
            // Set the undo manager to the modal dialog's undo manager
            undoManager = modalUndoManager;

            // Get a reference to the data type editor dialog to shorten
            // subsequent calls
            CcddDataTypeEditorDialog dataTypeEditor = ccddMain.getDataTypeEditor();

            // Check if the data type editor is open and the editor has focus
            if (dataTypeEditor != null && dataTypeEditor.isFocused())
            {
                // Get the cell editor for the data type editor
                cellEditor = dataTypeEditor.getTable().getCellEditor();
            }

            // Get a reference to the macro editor dialog to shorten subsequent
            // calls
            CcddMacroEditorDialog macroEditor = ccddMain.getMacroEditor();

            // Check if the macro editor is open and the editor has focus
            if (macroEditor != null && macroEditor.isFocused())
            {
                // Get the cell editor for the macro editor
                cellEditor = macroEditor.getTable().getCellEditor();
            }
        }
        // No modal undo manager is active
        else
        {
            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Check if the table's field editor is open and has the
                // keyboard focus
                if (editorDialog.getFieldEditorDialog() != null
                    && editorDialog.getFieldEditorDialog().isFocused())
                {
                    // Get a reference to the table's field editor undo manager
                    // and cell editor
                    undoManager = editorDialog.getFieldEditorDialog().getUndoManager();
                    cellEditor = editorDialog.getFieldEditorDialog().getTable().getCellEditor();
                }
                // Check if this editor dialog has the keyboard focus
                else if (editorDialog.isFocused())
                {
                    // Get the undo manager and cell editor for the active
                    // table editor
                    undoManager = editorDialog.getTableEditor().getEditPanelUndoManager();
                    cellEditor = editorDialog.getTableEditor().getTable().getCellEditor();
                }

                // Check if an undo manager is active
                if (undoManager != null)
                {
                    // Stop searching
                    break;
                }
            }

            // Get a reference to the type editor dialog to shorten subsequent
            // calls
            CcddTableTypeEditorDialog editorDialog = ccddMain.getTableTypeEditor();

            // Check if no table undo manager is applicable and the table type
            // editor is open
            if (undoManager == null && editorDialog != null)
            {
                // Check if the table type's field editor is open and has the
                // keyboard focus
                if (editorDialog.getFieldEditorDialog() != null
                    && editorDialog.getFieldEditorDialog().isFocused())
                {
                    // Get a reference to the table's field editor undo manager
                    // and cell editor
                    undoManager = editorDialog.getFieldEditorDialog().getUndoManager();
                    cellEditor = editorDialog.getFieldEditorDialog().getTable().getCellEditor();
                }
                // Check if the table type editor has the keyboard focus
                else if (editorDialog.isFocused())
                {
                    // Get the undo manager and cell editor for the active
                    // table type editor
                    undoManager = editorDialog.getTypeEditor().getEditPanelUndoManager();
                    cellEditor = editorDialog.getTypeEditor().getTable().getCellEditor();
                }
            }

            // Get a reference to the data field table editor dialog to shorten
            // subsequent calls
            CcddFieldTableEditorDialog fieldEditor = ccddMain.getFieldTableEditor();

            // Check if no table or table type undo manager is applicable,
            // the data field table editor is open, and the editor has focus
            if (undoManager == null
                && fieldEditor != null
                && fieldEditor.isFocused())
            {
                // Get the undo manager and cell editor for the data field
                // table editor
                undoManager = fieldEditor.getTable().getUndoManager();
                cellEditor = fieldEditor.getTable().getCellEditor();
            }
        }

        // Check if a table cell is actively being edited
        if (cellEditor != null)
        {
            // Incorporate any cell changes and terminate editing
            cellEditor.stopCellEditing();
        }
        // No table cell is being edited
        else
        {
            // Get the current owner of the keyboard focus
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

            // Check if the focus is in an edit panel's description or data
            // field
            if (focusOwner != null &&
                (focusOwner instanceof UndoableTextField
                 || focusOwner instanceof UndoableTextArea
                 || focusOwner instanceof UndoableCheckBox))
            {
                // Clear the keyboard focus so that the current data field
                // value is registered as an edit
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
            }
        }

        return undoManager;
    }

    /**************************************************************************
     * Handle Enter and space key press events in a table in order to activate
     * check box controls and initiate editing for editable fields. The space
     * key would do these without this method; it is included in order to
     * eliminate the cell background color 'flash' that occurs when a check box
     * is toggled
     * 
     * @param table
     *            reference to the table where the key press event occurred
     * 
     * @return true if the key press event is handled by this method; false
     *         otherwise
     *************************************************************************/
    private boolean tableEditCellHandler(Component comp)
    {
        boolean handled = false;
        CcddJTableHandler table;

        // Check if this is a table
        if (comp instanceof CcddJTableHandler)
        {
            table = (CcddJTableHandler) comp;

            // Get the row and column in the table with the focus
            int row = table.getSelectedRow() + table.getSelectedRowCount() - 1;
            int column = table.getSelectedColumn() + table.getSelectedColumnCount() - 1;

            // Check that a cell is selected
            if (row != -2 && column != -2)
            {
                // Get the contents of the selected cell
                Object cellObject = table.getValueAt(row, column);

                // Check if this is not a check box within a table cell
                if (!(cellObject instanceof Boolean))
                {
                    // Initiate editing on the cell and indicate that the event
                    // has been handled. Note that this has no effect on
                    // non-editable cells
                    table.editCellAt(row, column);
                }
                // This cell displays a check box; check if it's editable
                else if (table.isCellEditable(row, column))
                {
                    // Invert the cell value (true <-> false)
                    table.setValueAt(!((Boolean) cellObject), row, column);
                    table.getUndoManager().endEditSequence();
                }

                // Set the flag to indicate that the event has been handled
                handled = true;
            }
            // No cell is selected; check if the table contains at least one
            // row so that a row can be selected
            else if (table.getRowCount() > 0)
            {
                // Assume the top row will be selected
                row = 0;

                // Get the table's scroll pane viewport
                JViewport vp = table.getViewport();

                // Verify that the table has a scroll pane
                if (vp != null)
                {
                    // Set the row to select to the topmost visible one
                    row = table.rowAtPoint(vp.getViewPosition());
                }

                // Select the topmost visible row and set the focus to the
                // first column in the selected row
                table.setRowSelectionInterval(row, 0);
                table.requestFocusInWindow();
                table.changeSelection(row, 0, false, false);
            }
        }
        // This is a text field cell in a table being that's being edited
        else
        {
            table = (CcddJTableHandler) comp.getParent();

            // Get the indices of the cell being edited
            int row = table.getEditingRow();
            int col = table.getEditingColumn();

            // Terminate editing in this cell
            table.getCellEditor().stopCellEditing();

            // Select the next editable cell. If the last column in the last
            // row is reached then terminate editing, leaving the cell selected
            do
            {
                // Check if this is not the last column
                if (col < table.getColumnCount() - 1)
                {
                    // Go to the next column
                    col++;
                }
                // At the last column
                else
                {
                    // Go to the first column of the next row
                    row++;
                    col = 0;
                }
            } while (row != table.getRowCount()
                     && !table.isCellEditable(row, col));

            // Initiate editing on the new cell, if valid
            if (table.editCellAt(row, col))
            {
                // Editing initiated on the new cell; change the focus to this
                // cell
                table.changeSelection(row, col, false, false);
                comp.requestFocus();
            }

            // Set the flag to indicate that the event has been handled
            handled = true;
        }

        return handled;
    }
}
