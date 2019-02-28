/**
 * CFS Command and Data Dictionary keyboard handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PopUpComboBox;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddConstants.ArrowFocusOption;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.MacroEditorColumnInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddUndoHandler.UndoableCheckBox;
import CCDD.CcddUndoHandler.UndoableTextArea;
import CCDD.CcddUndoHandler.UndoableTextField;

/**************************************************************************************************
 * CFS Command and Data Dictionary keyboard handler class
 *************************************************************************************************/
public class CcddKeyboardHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddMacroHandler macroHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddUndoManager modalUndoManager;
    private CcddJTableHandler modalTable;
    private CcddInputFieldPanelHandler editPnlHandler;
    private CcddInputTypeHandler inputTypeHandler;
    private KeyboardFocusManager focusManager;

    /**********************************************************************************************
     * Keyboard handler class constructor
     *
     * @param ccddMain
     *            reference to main class
     *********************************************************************************************/
    CcddKeyboardHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Set the keyboard handler
        setKeyboardHandler();
    }

    /**********************************************************************************************
     * Set the reference to the macro and data type handler classes
     *********************************************************************************************/
    protected void setHandlers()
    {
        macroHandler = ccddMain.getMacroHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
    }

    /**********************************************************************************************
     * Set the references for the undo manager and table (if applicable) to those of the active
     * modal dialog (e.g., the group manager or macro editor dialogs). Since the modal dialogs lock
     * the user interface while active a reference to them cannot be set for the code to access as
     * it can with the non-modal dialogs (i.e., the table editors and table type editor). The modal
     * undo manager must be set to null after the dialog closes so that the non-modal dialogs
     * (e.g., table and table type editors) undo managers can handle undo/redo operations
     *
     * @param undoManager
     *            modal dialog undo manager; null to disable
     *
     * @param table
     *            modal dialog table reference; null if the modal dialog has no table
     *********************************************************************************************/
    protected void setModalDialogReference(CcddUndoManager undoManager,
                                           CcddJTableHandler table)
    {
        modalUndoManager = undoManager;
        modalTable = table;
    }

    /**********************************************************************************************
     * Adjust the handling of Enter and space key inputs in order to activate dialog controls, and
     * keyboard focus changes in order to use the arrow keys like Tab keys
     *********************************************************************************************/
    private void setKeyboardHandler()
    {
        // Get the keyboard focus manager
        focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

        // Listen for key presses
        focusManager.addKeyEventDispatcher(new KeyEventDispatcher()
        {
            boolean isShowMacros = false;
            Timer releaseTimer = null;

            /**************************************************************************************
             * Alter the response to the Enter key to act like the Space key to activate button and
             * check box controls, and the arrow keys so as to mimic the Tab and Shift+Tab keys,
             * unless the key press originates within a table or combo box. For a tabbed pane the
             * left/right arrows are left unchanged so that these are used for traversing the
             * tabbed panes, but the down and up arrows act like Tab and Shift+Tab respectively
             *************************************************************************************/
            @Override
            public boolean dispatchKeyEvent(final KeyEvent ke)
            {
                // Flag that indicates if the key event has been handled by this method (true) or
                // that it still needs to be processed (false)
                boolean handled = false;

                // Get a reference to the component with the focus in order to shorten the
                // subsequent calls
                Component comp = ke.getComponent();

                // Check if this is a key press event, and the Ctrl, Shift, or Alt key is not
                // pressed
                if (ke.getID() == KeyEvent.KEY_PRESSED
                    && !ke.isControlDown()
                    && !ke.isShiftDown()
                    && !ke.isAltDown())
                {
                    // Check if the Enter key is pressed
                    if (ke.getKeyCode() == KeyEvent.VK_ENTER)
                    {
                        // Check if this is a button
                        if (comp instanceof JButton)
                        {
                            // Activate the control (same as if Space key is pressed)
                            ((AbstractButton) comp).doClick();
                            handled = true;
                        }
                        // Not a button; check if this is a table (or a cell in a table)
                        else
                        {
                            boolean isTable = false;
                            Component parentComp = comp;

                            // Start with the component and work through its parent component(s)
                            // until a table is found or the last parent is reached
                            do
                            {
                                // Check if the component is a table
                                if (parentComp instanceof CcddJTableHandler)
                                {
                                    // Set the flag to indicate the component is a table or a cell
                                    // in a table
                                    isTable = true;
                                }
                                // The component isn't a table
                                else
                                {
                                    // Get the component's parent component
                                    parentComp = parentComp.getParent();
                                }
                            } while (!isTable && parentComp != null);

                            // Check if the component is an editor for a table cell
                            if (isTable)
                            {
                                // Handle the Enter key in the table
                                handled = tableEditCellHandler(comp);
                            }
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
                    // Check if the key pressed is an "action" key (i.e., a key that doesn't
                    // produce a character and is not a modifier; this covers the arrow keys)
                    else if (ke.isActionKey())
                    {
                        // Assume that the default handling will be used with an arrow key
                        ArrowFocusOption arrowResponse = USE_DEFAULT_HANDLER;

                        // Check if the focus is on a tabbed pane's tab or on a slider
                        if (comp instanceof JTabbedPane || comp instanceof JSlider)
                        {
                            // The left and right arrows traverse the tabs, and the up and down
                            // arrows behave like (Shift+)Tab
                            arrowResponse = HANDLE_UP_AND_DOWN_ARROWS;
                        }
                        // Check if the focus is on a text field in a combo box
                        else if (comp instanceof JTextField
                                 && comp.getParent() instanceof JComboBox)
                        {
                            arrowResponse = USE_DEFAULT_HANDLER;
                        }
                        // Check if the focus is in a text field within a table
                        else if ((comp instanceof JTextField
                                  || comp instanceof JTextArea)
                                 && comp.getParent() instanceof CcddJTableHandler)
                        {
                            // The up and down arrows are ignored. This prevents accidently exiting
                            // edit mode on a table cell and losing any changes
                            arrowResponse = IGNORE_UP_AND_DOWN_ARROWS;
                        }
                        // Check if the focus is on a button (including a color button), radio
                        // button, or check box
                        else if (comp instanceof JButton
                                 || comp instanceof JRadioButton
                                 || comp instanceof JCheckBox)
                        {
                            // The up and left arrow keys behave as Shift+Tab, and the down and
                            // right arrow keys behave as Tab
                            arrowResponse = HANDLE_ALL_ARROWS;
                        }
                        // Check if the focus is within a table
                        else if (comp instanceof CcddJTableHandler)
                        {
                            // Get the reference to the table with the focus
                            CcddJTableHandler table = (CcddJTableHandler) comp;

                            // Get the row containing the cell with the focus in order to shorten
                            // the subsequent calls
                            int row = table.getSelectedRow();

                            // Check if the table has no rows
                            if (row == -1)
                            {
                                // Treat the table as if it wasn't there; i.e., the left and right
                                // arrows behave like the (Shift+)Tab key
                                arrowResponse = HANDLE_ALL_ARROWS;
                            }
                            // Check if the table has only a single row
                            else if (table.getRowCount() == 1)
                            {
                                // The up and down arrows behave like the (Shift+)Tab key
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

                        // Check if the key pressed is an arrow key and if so adjust its behavior
                        switch (ke.getKeyCode())
                        {
                            case KeyEvent.VK_LEFT:
                            case KeyEvent.VK_KP_LEFT:
                                // Check if the left arrow key should be handled
                                if (arrowResponse == HANDLE_ALL_ARROWS)
                                {
                                    // Treat the left arrow as a Shift+Tab key and indicate that
                                    // the key has been handled
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
                                    // Treat the up arrow as a Shift+Tab key and indicate that the
                                    // key has been handled
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
                                // Check if the right arrow key should be handled
                                if (arrowResponse == HANDLE_ALL_ARROWS)
                                {
                                    // Treat the right arrow as a Tab key and indicate that the key
                                    // has been handled
                                    focusManager.focusNextComponent();
                                    handled = true;
                                }

                                break;

                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_KP_DOWN:
                                // Check if the down arrow key should be handled
                                if (arrowResponse == HANDLE_ALL_ARROWS
                                    || arrowResponse == HANDLE_DOWN_ARROW
                                    || arrowResponse == HANDLE_UP_AND_DOWN_ARROWS)
                                {
                                    // Treat the down arrow as a Tab key and indicate that the key
                                    // has been handled
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
                    // Check if the Ctrl-Z key is pressed
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
                                /******************************************************************
                                 * Execute after all pending Swing events are finished so that the
                                 * events occur in the desired order
                                 *****************************************************************/
                                @Override
                                public void run()
                                {
                                    // Undo the previous edit action
                                    undoManager.undo();

                                    // Check if an edit panel handler (i.e., data fields) is
                                    // associated with the component
                                    if (editPnlHandler != null)
                                    {
                                        // Update the data field background colors
                                        editPnlHandler.setFieldBackgound();
                                    }

                                    // Force the component to repaint so that the change is visible
                                    ke.getComponent().repaint();
                                }
                            });

                            // Set the flag to indicate this key press was handled
                            handled = true;
                        }
                    }
                    // Check if the Ctrl-Y key is pressed
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
                                /******************************************************************
                                 * Execute after all pending Swing events are finished so that the
                                 * events occur in the desired order
                                 *****************************************************************/
                                @Override
                                public void run()
                                {
                                    // Redo the previous undo action
                                    undoManager.redo();

                                    // Check if an edit panel handler (i.e., data fields) is
                                    // associated with the component
                                    if (editPnlHandler != null)
                                    {
                                        // Update the data field background colors
                                        editPnlHandler.setFieldBackgound();
                                    }

                                    // Force the component to repaint so that the change is visible
                                    ke.getComponent().repaint();
                                }
                            });

                            // Set the flag to indicate this key press was handled
                            handled = true;
                        }
                    }
                    // Check if the Ctrl-F key is pressed
                    else if (ke.getKeyCode() == KeyEvent.VK_F)
                    {
                        // Check if the main application window has the focus
                        if (ccddMain.getMainFrame().isFocused())
                        {
                            // Open the event log search dialog
                            ccddMain.showSearchDialog(SearchDialogType.LOG,
                                                      null,
                                                      ccddMain.getSessionEventLog(),
                                                      ccddMain.getMainFrame());

                            // Set the flag to indicate this key press was handled
                            handled = true;
                        }
                        // Check if the table type editor is open and has the focus
                        else if (ccddMain.getTableTypeEditor() != null
                                 && ccddMain.getTableTypeEditor().isFocused())
                        {
                            // Open the table search dialog for the table type editor dialog
                            ccddMain.getTableTypeEditor().findReplace();
                        }
                        // The main application window and the table type editor don't have the
                        // focus
                        else
                        {
                            // Get the table editor dialog with the focus
                            CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                            // Check if a table editor dialog has the focus
                            if (editorDialog != null)
                            {
                                // Open the table search dialog for the table editor dialog
                                editorDialog.findReplace();
                            }
                        }
                    }
                    // Check if the Ctrl-S key is pressed. The following handles structure name
                    // insertion
                    else if (ke.getKeyCode() == KeyEvent.VK_S)
                    {
                        // Get the table editor dialog with the focus
                        CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                        // Check if a table editor dialog has the focus and that the cell doesn't
                        // contain a combo box or check box
                        if (editorDialog != null
                            && comp.getParent() instanceof CcddJTableHandler
                            && !(comp instanceof JComboBox)
                            && !(comp instanceof JCheckBox))
                        {
                            // Check if a cell in the table is being edited
                            if (editorDialog.getTableEditor().getTable().isEditing())
                            {
                                // Insert the structure name chosen by the user into the text field
                                // at the current text insertion point
                                new PopUpComboBox(editorDialog,
                                                  (JTextComponent) comp,
                                                  editorDialog.getTableEditor().getValidStructureDataTypes(),
                                                  ModifiableFontInfo.DATA_TABLE_CELL.getFont());
                            }
                        }
                        // Check if this is a description or data field
                        else if (comp instanceof JTextField || comp instanceof JTextArea)
                        {
                            // Display a pop-up combo box with the data type selection items for
                            // the specified input type. Insert the item chosen by the user into
                            // the text component at the current text insertion point
                            new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                              (JTextComponent) comp,
                                              dataTypeHandler.getDataTypePopUpItems(false),
                                              ModifiableFontInfo.INPUT_TEXT.getFont());
                        }
                        // Check if editing is active in the data type editor
                        else if (SwingUtilities.getWindowAncestor(comp) instanceof CcddDataTypeEditorDialog
                                 && modalTable.isEditing())
                        {
                            // Get the row and column being edited in the table, and the contents
                            // of the edited row's base data type
                            int row = modalTable.getEditingRow();
                            int column = modalTable.convertColumnIndexToModel(modalTable.getEditingColumn());
                            String baseType = modalTable.getValueAt(row,
                                                                    DataTypeEditorColumnInfo.BASE_TYPE.ordinal())
                                                        .toString();

                            // Check if the type name or C name columns are being edited and the
                            // base data type is empty or a pointer
                            if ((column == DataTypeEditorColumnInfo.USER_NAME.ordinal()
                                 || column == DataTypeEditorColumnInfo.C_NAME.ordinal())
                                && (baseType.isEmpty()
                                    || baseType.equals(BaseDataTypeInfo.POINTER.getName())))
                            {
                                // Insert the structure name chosen by the user into the text field
                                // at the current text insertion point
                                new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                                  (JTextComponent) comp,
                                                  dataTypeHandler.getDataTypePopUpItems(false),
                                                  ModifiableFontInfo.DATA_TABLE_CELL.getFont());
                            }
                        }
                        // Check if editing is active in the the macro editor
                        else if (SwingUtilities.getWindowAncestor(comp) instanceof CcddMacroEditorDialog
                                 && modalTable.isEditing())
                        {
                            // Insert the structure name chosen by the user into the text field at
                            // the current text insertion point
                            new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                              (JTextComponent) comp,
                                              dataTypeHandler.getDataTypePopUpItems(true),
                                              ModifiableFontInfo.DATA_TABLE_CELL.getFont());
                        }

                        // Set the flag to indicate this key press was handled
                        handled = true;
                    }
                    // Check if the Ctrl-M key is pressed. The following handles macro insertion
                    // and expansion
                    else if (ke.getKeyCode() == KeyEvent.VK_M)
                    {
                        // Check if the shift key is also pressed (Ctrl-Shift-M)
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
                                    // Replace the macro names with their corresponding values in
                                    // the currently selected table in this editor
                                    editorDialog.getTableEditor().expandMacros(true, true);
                                    isShowMacros = true;
                                }
                                // Check if this is the macro editor
                                else if (SwingUtilities.getWindowAncestor(comp) instanceof CcddMacroEditorDialog)
                                {
                                    // Expand the macros in the macro value column
                                    ((CcddMacroEditorDialog) SwingUtilities.getWindowAncestor(comp)).expandMacros(true);
                                    isShowMacros = true;
                                }
                            }
                        }
                        // The shift key isn't pressed (Ctrl-M only). Check if this is a table cell
                        // that doesn't contain a combo box or check box
                        else if (comp.getParent() instanceof CcddJTableHandler
                                 && !(comp instanceof JComboBox)
                                 && !(comp instanceof JCheckBox))
                        {
                            // Get the table editor dialog with the focus
                            CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                            // Check if a table editor dialog has the focus
                            if (editorDialog != null)
                            {
                                // Get references to shorten subsequent calls
                                CcddTableEditorHandler editor = editorDialog.getTableEditor();
                                CcddJTableHandler table = editor.getTable();

                                // Check if a cell in the table is being edited
                                if (table.isEditing())
                                {
                                    // Get the index of the column being edited in model
                                    // coordinates
                                    int column = table.convertColumnIndexToModel(table.getEditingColumn());

                                    // Get the input type for the column being edited
                                    InputType inputType = editor.getTableTypeDefinition().getInputTypes()[column];

                                    // Insert the macro name chosen by the user into the text
                                    // component at the current text insertion point
                                    new PopUpComboBox(editorDialog,
                                                      (JTextComponent) comp,
                                                      macroHandler.getMacroPopUpItems((JTextComponent) comp,
                                                                                      inputType,
                                                                                      editor.getValidStructureDataTypes()),
                                                      macroHandler.getMacroPopUpToolTips(),
                                                      ModifiableFontInfo.DATA_TABLE_CELL.getFont())
                                    {
                                        /**********************************************************
                                         * Enclose the selected macro name in the macro identifier
                                         * character(s)
                                         *********************************************************/
                                        @Override
                                        protected String alterText(String selectedItem)
                                        {
                                            return CcddMacroHandler.getFullMacroName(selectedItem);
                                        }
                                    };
                                }
                            }
                            // Check if this is the macro editor, editing is active, and the values
                            // column is being edited
                            else if (SwingUtilities.getWindowAncestor(comp) instanceof CcddMacroEditorDialog
                                     && modalTable.isEditing()
                                     && modalTable.convertColumnIndexToModel(modalTable.getEditingColumn()) == MacroEditorColumnInfo.VALUE.ordinal())
                            {
                                // Insert the macro name chosen by the user into the text component
                                // at the current text insertion point
                                new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                                  (JTextComponent) comp,
                                                  macroHandler.getMacroPopUpItems((JTextComponent) comp,
                                                                                  inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT),
                                                                                  null),
                                                  macroHandler.getMacroPopUpToolTips(),
                                                  ModifiableFontInfo.DATA_TABLE_CELL.getFont())
                                {
                                    /**************************************************************
                                     * Enclose the selected macro name in the macro identifier
                                     * character(s)
                                     *************************************************************/
                                    @Override
                                    protected String alterText(String selectedItem)
                                    {
                                        return CcddMacroHandler.getFullMacroName(selectedItem);
                                    }
                                };
                            }
                        }
                        // Check if this is a description or data field
                        else if (comp instanceof JTextField || comp instanceof JTextArea)
                        {
                            // Display a pop-up combo box with the macro selection items for the
                            // specified input type. Insert the item chosen by the user into the
                            // text component at the current text insertion point
                            new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                              (JTextComponent) comp,
                                              macroHandler.getMacroPopUpItems((JTextComponent) comp,
                                                                              inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT),
                                                                              null),
                                              macroHandler.getMacroPopUpToolTips(),
                                              ModifiableFontInfo.INPUT_TEXT.getFont())
                            {
                                /******************************************************************
                                 * Replace the macro name with the corresponding value
                                 *****************************************************************/
                                @Override
                                protected String alterText(String selectedItem)
                                {
                                    return macroHandler.getMacroValue(selectedItem);
                                }
                            };
                        }

                        // Set the flag to indicate this key press was handled
                        handled = true;
                    }
                    // Check if the Ctrl-E key is pressed while the focus is on a tree
                    else if (ke.getKeyCode() == KeyEvent.VK_E
                             && comp instanceof CcddCommonTreeHandler)
                    {
                        // Expand/collapse the selected node(s)
                        ((CcddCommonTreeHandler) comp).expandCollapseSelectedNodes();
                    }
                }
                // Check if Alt-Shift-V, Alt-Shift-C, or Alt-Shift-M is pressed (but not with the
                // Ctrl key)
                else if (ke.getID() == KeyEvent.KEY_PRESSED
                         && ke.isAltDown()
                         && ke.isShiftDown()
                         && !ke.isControlDown()
                         && (ke.getKeyCode() == KeyEvent.VK_V
                             || ke.getKeyCode() == KeyEvent.VK_C
                             || ke.getKeyCode() == KeyEvent.VK_M))
                {
                    // Get the input type based on the key pressed: variable reference for Alt-V,
                    // command reference for Alt-C, and message reference for Alt-M
                    DefaultInputType refInputType = ke.getKeyCode() == KeyEvent.VK_V
                                                                                     ? DefaultInputType.VARIABLE_REFERENCE
                                                                                     : (ke.getKeyCode() == KeyEvent.VK_C
                                                                                                                         ? DefaultInputType.COMMAND_REFERENCE
                                                                                                                         : DefaultInputType.MESSAGE_REFERENCE);
                    // Check if this is a table cell
                    if (comp.getParent() instanceof CcddJTableHandler)
                    {
                        // Get the table editor dialog with the focus
                        CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                        // Check if a table editor dialog has the focus and that the cell doesn't
                        // contain a combo box
                        if (editorDialog != null && !(comp instanceof JComboBox))
                        {
                            // Get references to shorten subsequent calls
                            CcddTableEditorHandler editor = editorDialog.getTableEditor();
                            CcddJTableHandler table = editor.getTable();

                            // Check if a cell in the table is being edited
                            if (table.isEditing())
                            {
                                // Display a pop-up combo box with the selection items for the
                                // specified input type. Insert the item chosen by the user into
                                // the text component at the current text insertion point
                                new PopUpComboBox(editorDialog,
                                                  (JTextComponent) comp,
                                                  inputTypeHandler.getInputTypeByDefaultType(refInputType).getInputItems(),
                                                  ModifiableFontInfo.DATA_TABLE_CELL.getFont());
                            }
                        }
                    }
                    // Check if this is a description or data field
                    else if (comp instanceof JTextField || comp instanceof JTextArea)
                    {
                        // Display a pop-up combo box with the selection items for the specified
                        // input type. Insert the item chosen by the user into the text component
                        // at the current text insertion point
                        new PopUpComboBox(SwingUtilities.getWindowAncestor(comp),
                                          (JTextComponent) comp,
                                          inputTypeHandler.getInputTypeByDefaultType(refInputType).getInputItems(),
                                          ModifiableFontInfo.INPUT_TEXT.getFont());
                    }

                    // Set the flag to indicate this key press was handled
                    handled = true;
                }
                // Check if the Alt-Enter keys are pressed in a table cell that displays multi-line
                // text
                else if (ke.getID() == KeyEvent.KEY_PRESSED
                         && ke.isAltDown()
                         && !ke.isControlDown()
                         && ke.getKeyCode() == KeyEvent.VK_ENTER
                         && comp.getParent() instanceof CcddJTableHandler
                         && comp instanceof JTextArea)
                {
                    // Get the table editor dialog with the focus
                    CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                    // Check if a table editor dialog has the focus and a cell in the table is
                    // being edited, or if editing is active in the input type editor
                    if ((editorDialog != null
                         && editorDialog.getTableEditor().getTable().isEditing())
                        || (SwingUtilities.getWindowAncestor(comp) instanceof CcddInputTypeEditorDialog
                            && modalTable.isEditing()))
                    {
                        JTextComponent textComp = (JTextComponent) comp;

                        // Get the cell's current value
                        String cellValue = textComp.getText();

                        // Get the starting position of the selected text
                        int caretPosn = textComp.getSelectionStart();

                        // Replace the currently selected text with a line feed
                        textComp.setText(cellValue.substring(0, caretPosn)
                                         + "\n"
                                         + cellValue.substring(textComp.getSelectionEnd()));

                        // Position the cursor after the newly inserted line feed
                        textComp.setCaretPosition(caretPosn + 1);
                    }
                }

                // Check if the macros are currently expanded for a table and that the expansion
                // key sequence is no longer active
                if (isShowMacros
                    && ke.getID() == KeyEvent.KEY_RELEASED
                    && ke.getKeyCode() == KeyEvent.VK_M)
                {
                    // Check if the key release action timer doesn't exist
                    if (releaseTimer == null)
                    {
                        // Create the key release action timer. In Linux if a key is held it
                        // generates continuous key release events. This timer is used to ignore
                        // the key release events that are close together time-wise
                        releaseTimer = new Timer(75, new ActionListener()
                        {
                            /**********************************************************************
                             * Handle the key release action
                             *********************************************************************/
                            @Override
                            public void actionPerformed(ActionEvent ae)
                            {
                                // Get the table editor dialog with the focus
                                CcddTableEditorDialog editorDialog = getFocusedTableEditorDialog();

                                // Check if a table editor dialog has the focus
                                if (editorDialog != null)
                                {
                                    // Restore the macro names in the currently selected table in
                                    // this editor
                                    editorDialog.getTableEditor().expandMacros(false, true);
                                    isShowMacros = false;
                                }
                                // Check if this is the macro editor
                                else if (SwingUtilities.getWindowAncestor(modalTable) instanceof CcddMacroEditorDialog)
                                {
                                    // Expand the macros in the macro value column
                                    ((CcddMacroEditorDialog) SwingUtilities.getWindowAncestor(modalTable)).expandMacros(false);
                                    isShowMacros = false;
                                }
                            }
                        });

                        // Allow the timer to send only a single expiration event
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

    /**********************************************************************************************
     * Get the active table editor dialog
     *
     * @return The active table editor dialog; null if no table editor dialog is active or no
     *         editor dialog has focus
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Get the active component's undo manager
     *
     * @return The active undo manager; null if no undo manager is active or no editor has focus
     *********************************************************************************************/
    private CcddUndoManager getActiveUndoManager()
    {
        CellEditor cellEditor = null;
        CcddUndoManager undoManager = null;
        editPnlHandler = null;

        // Check if a modal dialog is active
        if (modalUndoManager != null)
        {
            // Set the undo manager to the modal dialog's undo manager
            undoManager = modalUndoManager;

            // Check if a modal table is active
            if (modalTable != null)
            {
                // Get the cell editor for the modal table
                cellEditor = modalTable.getCellEditor();
            }

            // Get a reference to the group manager dialog to shorten subsequent calls
            CcddGroupManagerDialog groupManager = ccddMain.getGroupManager();

            // Check if the group manager is open and has focus
            if (groupManager != null && groupManager.isFocused())
            {
                // Get the group manager's editor panel handler
                editPnlHandler = groupManager.getEditorPanelHandler();
            }
        }
        // No modal undo manager is active
        else
        {
            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Check if this editor dialog has the keyboard focus
                if (editorDialog.isFocused())
                {
                    // Get the undo manager, cell editor, and editor panel handler for the active
                    // table editor
                    undoManager = editorDialog.getTableEditor().getFieldPanelUndoManager();
                    cellEditor = editorDialog.getTableEditor().getTable().getCellEditor();
                    editPnlHandler = editorDialog.getTableEditor().getInputFieldPanelHandler();
                }

                // Check if an undo manager is active
                if (undoManager != null)
                {
                    // Stop searching
                    break;
                }
            }

            // Get a reference to the type editor dialog to shorten subsequent calls
            CcddTableTypeEditorDialog editorDialog = ccddMain.getTableTypeEditor();

            // Check if no table undo manager is applicable and the table type editor is open
            if (undoManager == null && editorDialog != null)
            {
                // Check if the table type editor has the keyboard focus
                if (editorDialog.isFocused())
                {
                    // Get the undo manager, cell editor, and editor panel handler for the active
                    // table type editor
                    undoManager = editorDialog.getTypeEditor().getFieldPanelUndoManager();
                    cellEditor = editorDialog.getTypeEditor().getTable().getCellEditor();
                    editPnlHandler = editorDialog.getTypeEditor().getInputFieldPanelHandler();
                }
            }

            // Get a reference to the data field table editor dialog to shorten subsequent calls
            CcddFieldTableEditorDialog fieldEditor = ccddMain.getFieldTableEditor();

            // Check if no table or table type undo manager is applicable, the data field table
            // editor is open, and the editor has focus
            if (undoManager == null && fieldEditor != null && fieldEditor.isFocused())
            {
                // Get the undo manager and cell editor for the data field table editor
                undoManager = fieldEditor.getTable().getUndoManager();
                cellEditor = fieldEditor.getTable().getCellEditor();
            }

            // Get a reference to the script manager dialog to shorten subsequent calls
            CcddScriptManagerDialog scriptManager = ccddMain.getScriptManager();

            // Check if no table or table type undo manager is applicable, the script manager is
            // open, and the editor has focus
            if (undoManager == null && scriptManager != null && scriptManager.isFocused())
            {
                // Get the undo manager and cell editor for the script manager
                undoManager = ccddMain.getScriptHandler().getAssociationsTable().getUndoManager();
                cellEditor = ccddMain.getScriptHandler().getAssociationsTable().getCellEditor();
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
            Component focusOwner = focusManager.getFocusOwner();

            // Check if the focus is in an edit panel's description or data field
            if (focusOwner != null
                && (focusOwner instanceof UndoableTextField
                    || focusOwner instanceof UndoableTextArea
                    || focusOwner instanceof UndoableCheckBox))
            {
                // Check if the focus owner is a text field data field
                if (focusOwner instanceof UndoableTextField)
                {
                    // Force the text to update so that an undo command starts with this field
                    ((JTextField) focusOwner).setText(((JTextField) focusOwner).getText());
                }

                // Clear the keyboard focus so that the current data field value is registered as
                // an edit
                focusManager.clearGlobalFocusOwner();
            }
        }

        return undoManager;
    }

    /**********************************************************************************************
     * Handle Enter and space key press events in a table in order to activate check box controls
     * and initiate editing for editable fields. The space key would do these without this method;
     * it is included in order to eliminate the cell background color 'flash' that occurs when a
     * check box is toggled
     *
     * @param comp
     *            reference to the component where the key press event occurred
     *
     * @return true if the key press event is handled by this method; false otherwise
     *********************************************************************************************/
    private boolean tableEditCellHandler(Component comp)
    {
        boolean handled = false;
        CcddJTableHandler table = null;

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
                    // Initiate editing on the cell and indicate that the event has been handled.
                    // Note that this has no effect on non-editable cells
                    table.editCellAt(row, column);
                }
                // This cell displays a check box; check if it's editable
                else if (table.isCellEditable(row, column))
                {
                    // Initiate editing on the new cell, if valid
                    if (table.editCellAt(row, column))
                    {
                        // Toggle the cell's check box in order to generate the action event for
                        // any listeners
                        ((JCheckBox) table.getEditorComponent()).doClick();
                    }

                    // Invert the cell value (true <-> false)
                    table.setValueAt(!((Boolean) cellObject), row, column);
                    table.getUndoManager().endEditSequence();
                }

                // Set the flag to indicate that the event has been handled
                handled = true;
            }
            // No cell is selected; check if the table contains at least one row so that a row can
            // be selected
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

                // Select the topmost visible row and set the focus to the first column in the
                // selected row
                table.setRowSelectionInterval(row, 0);
                table.requestFocusInWindow();
                table.changeSelection(row, 0, false, false);
            }
        }
        // This is a text field cell in a table being that's being edited
        else
        {
            // Get the component's parent
            Component parentComp = comp.getParent();

            // Determine the table to which the component belongs. Depending on the cell editor
            // this can be the component's parent, the parent's parent, etc.
            do
            {
                // Check if this is the parent table
                if (parentComp instanceof CcddJTableHandler)
                {
                    // Set the table reference
                    table = (CcddJTableHandler) parentComp;
                }
                // This isn't the parent table
                else
                {
                    // Get the parent of this component
                    parentComp = parentComp.getParent();
                }
            } while (table == null);

            // Get the indices of the cell being edited
            int row = table.getEditingRow();
            int column = table.getEditingColumn();

            // Check if the table cell contains an item matching combo box
            if (table.getCellEditor() instanceof ComboBoxCellEditor)
            {
                // Set the combo box selection to the currently highlighted list item and reenable
                // stopCellEditing() so that the cell is updated to the selected item
                ((ComboBoxCellEditor) table.getCellEditor()).updateSelectedItem();
                ((ComboBoxCellEditor) table.getCellEditor()).allowCellEdit(true);
            }

            // Terminate editing in this cell
            table.getCellEditor().stopCellEditing();

            // Select the next editable cell. If the last column in the last row is reached then
            // terminate editing, leaving the cell selected
            do
            {
                // Check if this is not the last column
                if (column < table.getColumnCount() - 1)
                {
                    // Go to the next column
                    column++;
                }
                // At the last column
                else
                {
                    // Go to the first column of the next row
                    row++;
                    column = 0;
                }
            } while (row != table.getRowCount() && !table.isCellEditable(row, column));

            // Initiate editing on the new cell, if valid
            if (table.editCellAt(row, column))
            {
                // Editing initiated on the new cell; change the focus to this cell
                table.changeSelection(row, column, false, false);
                table.getEditorComponent().requestFocus();
            }

            // Set the flag to indicate that the event has been handled
            handled = true;
        }

        // Check if the event was handled above
        if (handled)
        {
            // Deselect the default button since it's no longer valid after editing is initiated
            table.getRootPane().setDefaultButton(null);
        }

        return handled;
    }
}
