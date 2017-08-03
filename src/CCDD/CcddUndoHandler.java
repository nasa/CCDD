/**
 * CFS Command & Data Dictionary undoable components handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowSorter.SortKey;
import javax.swing.SwingUtilities;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import CCDD.CcddClasses.CellSelectionHandler;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddUndoHandler.UndoableCheckBox.UndoableToggleButtonModel;

/******************************************************************************
 * CFS Command & Data Dictionary undoable components handler class
 *****************************************************************************/
public class CcddUndoHandler
{
    // Class references
    private final CcddUndoManager undoManager;
    private CcddFieldHandler fieldHandler;
    private CcddJTableHandler table;
    private CellSelectionHandler selectedCells;
    private CcddCommonTreeHandler tree;

    // Flag that indicates if actions can be undone/redone
    private boolean isAllowUndo;

    // Flag that indicates if an edit sequence should be ended when a new value
    // is entered. This is not used in the undoable table model and table
    // column model classes
    private boolean isAutoEndEditSequence;

    // Row and column edit types
    private static enum TableEditType
    {
        INSERT,
        DELETE,
        MOVE
    }

    /**************************************************************************
     * Undoable components handler constructor
     * 
     * @param undoManager
     *            reference to the undo manager
     *************************************************************************/
    CcddUndoHandler(CcddUndoManager undoManager)
    {
        this.undoManager = undoManager;
        this.fieldHandler = null;
        table = null;
        selectedCells = null;
        tree = null;
        isAllowUndo = true;
        isAutoEndEditSequence = true;
    }

    /**************************************************************************
     * Set the reference to the data field handler. This allows changes to the
     * contents of a data field that is subsequently deleted, then restored via
     * an undo, to retain the undo capability for the content changes
     * 
     * @param fieldHandler
     *            reference to the data field handler; null to not retain data
     *            field content changes following deletion and restoration of
     *            the field
     *************************************************************************/
    protected void setFieldHandler(CcddFieldHandler fieldHandler)
    {
        this.fieldHandler = fieldHandler;
    }

    /**************************************************************************
     * Set the reference to the table for which node selection changes are to
     * be tracked
     * 
     * @param table
     *            reference to the table for which node selection changes are
     *            to be tracked
     *************************************************************************/
    protected void setTable(CcddJTableHandler table)
    {
        this.table = table;
    }

    /**************************************************************************
     * Set the reference to the table cell selection container for which
     * selection changes are to be tracked
     * 
     * @param selectedCells
     *            reference to the cell selection container for which selection
     *            changes are to be tracked
     *************************************************************************/
    protected void setSelectedCells(CellSelectionHandler selectedCells)
    {
        this.selectedCells = selectedCells;
    }

    /**************************************************************************
     * Set the reference to the tree for which node selection changes are to be
     * tracked
     * 
     * @param tree
     *            reference to the tree for which node selection changes are to
     *            be tracked
     *************************************************************************/
    protected void setTree(CcddCommonTreeHandler tree)
    {
        this.tree = tree;
    }

    /**************************************************************************
     * Set the flag that allows registering an edit with the undo manager
     * 
     * @param allow
     *            true to allow edits to be stored for possible undo action;
     *            false to perform the edit, but without storing it
     *************************************************************************/
    protected void setAllowUndo(boolean allow)
    {
        isAllowUndo = allow;
    }

    /**************************************************************************
     * Set the flag that automatically ends an edit sequence. This flag is used
     * by the data field editor panel to inhibit adding edit sequences so that
     * if all of the fields are cleared it can be stored as a single edit
     * rather than a number of individual edits. This is not used in the
     * undoable table model and table column model classes
     * 
     * @param autoEndEdit
     *            true to automatically end an edit sequence when a new value
     *            is entered
     *************************************************************************/
    protected void setAutoEndEditSequence(boolean autoEndEdit)
    {
        isAutoEndEditSequence = autoEndEdit;
    }

    /**************************************************************************
     * Check box value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableCheckBox extends JCheckBox
    {
        /**********************************************************************
         * Toggle button model undo/redo class
         *********************************************************************/
        protected class UndoableToggleButtonModel extends ToggleButtonModel
        {
            /******************************************************************
             * Toggle button model undo/redo class constructor
             * 
             * @param select
             *            initial selection state of the check box
             *****************************************************************/
            UndoableToggleButtonModel(boolean select)
            {
                super();

                // Register the undo manager as an edit listener for this class
                listenerList.add(UndoableEditListener.class, undoManager);

                // Set the check box's initial state
                setSelected(select, false);
            }

            /******************************************************************
             * Override the default method with a method that includes a flag
             * to store the edit in the undo stack
             *****************************************************************/
            @Override
            public void setSelected(boolean select)
            {
                setSelected(select, true);
            }

            /******************************************************************
             * Change the value of a check box
             * 
             * @param select
             *            check box state; true to select
             * 
             * @param undoable
             *            true if the change can be undone
             *****************************************************************/
            public void setSelected(boolean select, boolean undoable)
            {
                // Store the current check box selection state
                boolean oldValue = isSelected();

                super.setSelected(select);

                // Check if undoing is enabled, if the edit is undoable, and if
                // the check box selection state changed
                if (isAllowUndo && undoable && oldValue != isSelected())
                {
                    // Get the listeners for this event
                    UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                    // Check if there is an edit listener registered
                    if (listeners.length != 0)
                    {
                        // Create the edit event to be passed to the listeners
                        UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                            new CheckBoxEdit(UndoableCheckBox.this,
                                                                                             oldValue,
                                                                                             isSelected()));

                        // Step through the registered listeners
                        for (UndoableEditListener listener : listeners)
                        {
                            // Inform the listener that an update occurred
                            listener.undoableEditHappened(editEvent);
                        }
                    }

                    // Check if the flag is set that allows automatically
                    // ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }
                }
            }
        }

        /**********************************************************************
         * Check box value undo/redo class constructor
         *********************************************************************/
        UndoableCheckBox()
        {
            // Create the check box
            super();

            // Set the model, and the edit and focus listeners
            setModelAndListeners(false);
        }

        /**********************************************************************
         * Check box value undo/redo class constructor
         * 
         * @param text
         *            text to display beside the check box
         * 
         * @param value
         *            initial check box selection state
         *********************************************************************/
        UndoableCheckBox(String text, boolean selected)
        {
            // Create the check box
            super(text, selected);

            // Set the model, and the edit and focus listeners
            setModelAndListeners(selected);
        }

        /**********************************************************************
         * Add a the undo manager to the edit listener list, set the check box
         * model to be undoable, and add a focus change listener to the check
         * box
         * 
         * @param select
         *            initial selection state of the check box
         *********************************************************************/
        private void setModelAndListeners(boolean select)
        {
            // Set the model so that check box edits can be undone/redone
            setModel(new UndoableToggleButtonModel(select));

            // Add a listener for check box focus changes
            addFocusListener(new FocusAdapter()
            {
                /**************************************************************
                 * Handle a focus gained event
                 *************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            });
        }
    }

    /**************************************************************************
     * Check box edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class CheckBoxEdit extends AbstractUndoableEdit
    {
        private UndoableCheckBox checkBox;
        private final boolean oldValue;
        private final boolean newValue;

        /**********************************************************************
         * Check box edit event handler constructor
         * 
         * @param checkBox
         *            reference to the check box being edited
         * 
         * @param oldValue
         *            previous check box selection state
         * 
         * @param newValue
         *            new check box selection state
         *********************************************************************/
        private CheckBoxEdit(UndoableCheckBox checkBox,
                             boolean oldValue,
                             boolean newValue)
        {
            this.checkBox = checkBox;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the check box edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current check box selection state with the old state
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the check box where the change was undone
            setSelectedCheckBox(oldValue);
        }

        /**********************************************************************
         * Replace the current check box selection state with the new state
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the check box where the change was redone
            setSelectedCheckBox(newValue);
        }

        /**********************************************************************
         * Set the selected check box. In order for this to select the text
         * area it must be scheduled to execute after other pending events
         * 
         * @param selected
         *            check box selection state
         *********************************************************************/
        private void setSelectedCheckBox(boolean selected)
        {
            // Check if a field handler exists and if the check box's name has
            // been set. A data field's check box name is set when the data
            // field is created
            if (fieldHandler != null && checkBox.getName() != null)
            {
                // Divide the check box's name into the owner and field name
                String[] ownerAndName = checkBox.getName().split(",");

                // Check if the check box name has the expected format (two
                // parts: owner name and field name)
                if (ownerAndName.length == 2)
                {
                    // Search the data fields for the field with the specified
                    // owner and field name
                    FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerAndName[0],
                                                                                        ownerAndName[1]);

                    // Check if the field with the owner and field name exists
                    if (fieldInfo != null)
                    {
                        // Set the check box reference to that associated with
                        // the specified data field. When a data field is
                        // altered the entire set of associated fields are
                        // recreated and therefore the check box reference
                        // stored in this edit no longer points to an existing
                        // field. Since the owner and field names in the data
                        // field information do still match, this step directs
                        // the undo/redo operation to the correct check box
                        checkBox = (UndoableCheckBox) fieldInfo.getInputFld();
                    }
                }
            }

            // Update the check box selection state
            ((UndoableToggleButtonModel) checkBox.getModel()).setSelected(selected,
                                                                          false);

            // Request a focus change to the check box that was changed
            setComponentFocus(checkBox);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CheckBox";
        }
    }

    /**************************************************************************
     * Text field value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextField extends JTextField
    {
        private String oldValue = "";

        /**********************************************************************
         * Text field value undo/redo class constructor
         *********************************************************************/
        UndoableTextField()
        {
            // Create the text field
            super();

            // Add a listener for text field focus changes
            setListeners("");
        }

        /**********************************************************************
         * Text field value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text field
         *********************************************************************/
        UndoableTextField(String text)
        {
            // Create the text field
            super(text);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /**********************************************************************
         * Text field value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text field
         * 
         * @param columns
         *            the number of columns to use to calculate the preferred
         *            width of the text field
         *********************************************************************/
        UndoableTextField(String text, int columns)
        {
            // Create the text field
            super(text, columns);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /**********************************************************************
         * Add the undo manager to the edit listeners and add a focus change
         * listener to the text field
         *
         * @param text
         *            text to display in the text field
         *********************************************************************/
        private void setListeners(String text)
        {
            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);

            // This is initially false to prevent storing the initial values as
            // undoable edits. Set the flag to true so that subsequent edits
            // can be undone
            isAllowUndo = true;

            // Initialize the text field's original value
            oldValue = text;

            // Add a listener for text field focus changes
            addFocusListener(new FocusAdapter()
            {
                /**************************************************************
                 * Handle a focus gained event
                 *************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();

                    // Store the current text field value
                    oldValue = getText();
                }
            });
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the edit in the undo stack
         *********************************************************************/
        @Override
        public void setText(String text)
        {
            setText(text, true);
        }

        /**********************************************************************
         * Change the value of a text field
         * 
         * @param text
         *            new text field value
         * 
         * @param undoable
         *            true if the change can be undone
         *********************************************************************/
        protected void setText(String text, boolean undoable)
        {
            super.setText(text);

            // Check if undoing is enabled, the edit is undoable, and if the
            // text field text changed
            if (isAllowUndo && undoable && !text.equals(oldValue))
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new TextFieldEdit(UndoableTextField.this,
                                                                                          oldValue,
                                                                                          text));

                    // Store the value as the old value for the next edit
                    oldValue = text;

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }
    }

    /**************************************************************************
     * Text field edit event handler class. If the data field handler is set
     * then the undo/redo operation compares the text field's name to the data
     * field owner and field names to determine if these match, and if so the
     * text field specified in the data field information is the one on which
     * the undo/redo operation is performed
     *************************************************************************/
    @SuppressWarnings("serial")
    private class TextFieldEdit extends AbstractUndoableEdit
    {
        private UndoableTextField textField;
        private final String oldValue;
        private final String newValue;

        /**********************************************************************
         * Text field edit event handler constructor
         * 
         * @param textField
         *            reference to the text field being edited
         * 
         * @param oldValue
         *            previous text field value
         * 
         * @param newValue
         *            new text field value
         *********************************************************************/
        private TextFieldEdit(UndoableTextField textField,
                              String oldValue,
                              String newValue)
        {
            this.textField = textField;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the text field edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current text field value with the old value
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text field where the change was undone
            setSelectedTextField(oldValue);
        }

        /**********************************************************************
         * Replace the current text field value with the new value
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text field where the change was redone
            setSelectedTextField(newValue);
        }

        /**********************************************************************
         * Set the selected text field. In order for this to select the text
         * field it must be scheduled to execute after other pending events
         * 
         * @param value
         *            text to place in the text field
         *********************************************************************/
        private void setSelectedTextField(final String value)
        {
            // Check if a field handler exists and if the text field's name has
            // been set. A data field's text field name is set when the data
            // field is created
            if (fieldHandler != null && textField.getName() != null)
            {
                // Divide the text field's name into the owner and field name
                String[] ownerAndName = textField.getName().split(",");

                // Check if the text field name has the expected format (two
                // parts: owner name and field name)
                if (ownerAndName.length == 2)
                {
                    // Search the data fields for the field with the specified
                    // owner and field name
                    FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerAndName[0],
                                                                                        ownerAndName[1]);

                    // Check if the field with the owner and field name exists
                    if (fieldInfo != null)
                    {
                        // Set the text field reference to that associated with
                        // the specified data field. When a data field is
                        // altered the entire set of associated fields are
                        // recreated and therefore the text field reference
                        // stored in this edit no longer points to an existing
                        // field. Since the owner and field names in the data
                        // field information do still match, this step directs
                        // the undo/redo operation to the correct text field
                        textField = (UndoableTextField) fieldInfo.getInputFld();
                    }
                }
            }

            // Update the text field value
            textField.setText(value, false);

            // Request a focus change to the text field that was changed
            setComponentFocus(textField);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextField";
        }
    }

    /**************************************************************************
     * Text area value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextArea extends JTextArea
    {
        private String oldValue;
        private boolean undoable;

        /**********************************************************************
         * Text area value undo/redo class constructor
         *********************************************************************/
        UndoableTextArea()
        {
            // Create the text area
            super();

            // Set the edit and focus listeners
            setListeners("");
        }

        /**********************************************************************
         * Text area value undo/redo class constructor
         *
         * @param text
         *            text to display in the text area
         *********************************************************************/
        UndoableTextArea(String text)
        {
            // Create the text area
            super(text);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /**********************************************************************
         * Text area value undo/redo class constructor
         *
         * @param text
         *            text to display in the text area
         *
         * @param rows
         *            number of rows
         *
         * @param columns
         *            the number of columns to use to calculate the preferred
         *            width of the text area
         *********************************************************************/
        UndoableTextArea(String text, int rows, int columns)
        {
            // Create the text area
            super(text, rows, columns);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /**********************************************************************
         * Add the undo manager to the edit listener list and add a focus
         * change listener to the text area
         *
         * @param text
         *            text to display in the text area
         *********************************************************************/
        private void setListeners(String text)
        {
            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);

            // Initialize the text area's original value
            oldValue = text;

            // Initialize the flag that allows undo/redo operations on this
            // text area to be stored. The text area must gain focus to be
            // enabled
            undoable = false;

            // Add a listener for text area focus changes
            addFocusListener(new FocusListener()
            {
                /**************************************************************
                 * Handle a focus gained event
                 *************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();

                    // Store the current text area value
                    oldValue = getText();

                    // Set the flag to enable undo/redo operations in the text
                    // area
                    undoable = true;
                }

                /**************************************************************
                 * Handle a focus lost event
                 *************************************************************/
                @Override
                public void focusLost(FocusEvent fe)
                {
                    // Update the text area and store the edit in the undo/redo
                    // stack
                    updateText();
                }
            });
        }

        /**********************************************************************
         * Check if an edit to the text area should be stored on the undo/redo
         * stack, and if so add it to the stack
         *********************************************************************/
        protected void updateText()
        {
            // Get the text area's new value
            String newValue = getText();

            // Check if undoing is enabled and if the text area value changed
            if (isAllowUndo && undoable && !oldValue.equals(newValue))
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new TextAreaEdit(UndoableTextArea.this,
                                                                                         oldValue,
                                                                                         newValue));

                    // Store the new text area value for the next edit
                    oldValue = newValue;

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Reset the flag so that any further edits must be preceded by
            // another focus gained event in order to be stored on the
            // undo/redo stack
            undoable = false;
        }
    }

    /**************************************************************************
     * Text area edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class TextAreaEdit extends AbstractUndoableEdit
    {
        private final UndoableTextArea textArea;
        private final String oldValue;
        private final String newValue;

        /**********************************************************************
         * Text area edit event handler constructor
         * 
         * @param textArea
         *            reference to the text area being edited
         * 
         * @param oldValue
         *            previous text area value
         * 
         * @param newValue
         *            new text area value
         *********************************************************************/
        private TextAreaEdit(UndoableTextArea textArea,
                             String oldValue,
                             String newValue)
        {
            this.textArea = textArea;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the text area edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current text area value with the old value
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text area where the change was undone
            setSelectedTextArea(oldValue);
        }

        /**********************************************************************
         * Replace the current text area value with the new value
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text area where the change was redone
            setSelectedTextArea(newValue);
        }

        /**********************************************************************
         * Set the text and focus to the text area
         * 
         * @param value
         *            text to place in the text area
         *********************************************************************/
        private void setSelectedTextArea(String value)
        {
            // Update the text area value
            textArea.setText(value);

            // Request a focus change to the text area that was changed
            setComponentFocus(textArea);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextArea";
        }
    }

    /**************************************************************************
     * Set the focus to the specified component, without generating a new edit
     * event
     * 
     * @param comp
     *            JComponent to which to set the focus
     *************************************************************************/
    private void setComponentFocus(JComponent comp)
    {
        // Check that the component is still visible
        if (comp.isVisible())
        {
            // Disable input verification since this sets the component value,
            // creating an edit event
            comp.setVerifyInputWhenFocusTarget(false);

            // Request a focus change to the component that was changed
            comp.requestFocusInWindow();

            // Re-enable input verification
            comp.setVerifyInputWhenFocusTarget(true);
        }
    }

    /**************************************************************************
     * Table model class for handling cell undo/redo edits
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTableModel extends DefaultTableModel
    {
        /**********************************************************************
         * Undoable table model class constructor
         *********************************************************************/
        UndoableTableModel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Return the class of the column object
         *********************************************************************/
        @Override
        public Class<?> getColumnClass(int column)
        {
            Class<?> returnClass = null;

            // Check if the table has at least one row
            if (getRowCount() != 0 && getValueAt(0, column) != null)
            {
                // Return the class of the object in the target column
                returnClass = getValueAt(0, column).getClass();
            }

            return returnClass;
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the edit in the undo stack
         *********************************************************************/
        @Override
        public void setValueAt(Object value, int row, int column)
        {
            // Set the value in the cell and add this edit to the undo stack
            setValueAt(value, row, column, true);
        }

        /**********************************************************************
         * Change the value of a cell
         * 
         * @param value
         *            new cell value
         * 
         * @param row
         *            table row
         * 
         * @param column
         *            table column
         * 
         * @param undoable
         *            true if the change can be undone
         *********************************************************************/
        protected void setValueAt(Object value,
                                  int row,
                                  int column,
                                  boolean undoable)
        {
            // Check if the value is text. For check boxes the value is boolean
            if (value instanceof String)
            {
                // Remove any leading and trailing whitespace
                value = value.toString().trim();
            }

            // Get the cell's current value
            Object oldValue = getValueAt(row, column);

            // Check if the cell value has changed. Not processing duplicate
            // updates prevents the undo stack from registering the edit twice
            if (!oldValue.equals(value))
            {
                // Update the cell's value
                super.setValueAt(value, row, column);

                // Check if this edit is undoable
                if (isAllowUndo && undoable)
                {
                    // Get the listeners for this event
                    UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                    // Check if there is an edit listener registered
                    if (listeners.length != 0)
                    {
                        // Create the edit event to be passed to the listeners
                        UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                            new CellEdit(this,
                                                                                         oldValue,
                                                                                         value,
                                                                                         row,
                                                                                         column));

                        // Step through the registered listeners
                        for (UndoableEditListener listener : listeners)
                        {
                            // Inform the listener that an update occurred
                            listener.undoableEditHappened(editEvent);
                        }
                    }
                }
            }
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the table data change in the undo stack
         *********************************************************************/
        @Override
        public void setDataVector(Object[][] dataVector,
                                  Object[] columnIdentifiers)
        {
            // Set the table data and add this update to the undo stack
            setDataVector(dataVector, columnIdentifiers, true);
        }

        /**********************************************************************
         * Change the table data array
         * 
         * @param dataVector
         *            array of new table data
         * 
         * @param columnIdentifiers
         *            array containing the table column names
         * 
         * @param undoable
         *            true if the change can be undone
         *********************************************************************/
        protected void setDataVector(Object[][] dataVector,
                                     Object[] columnIdentifiers,
                                     boolean undoable)
        {
            // Check if this data vector update is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new DataVectorEdit(table.getTableData(false),
                                                                                           dataVector));
                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }
            }

            super.setDataVector(dataVector, columnIdentifiers);
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the row inserted in the undo stack
         *********************************************************************/
        @Override
        public void insertRow(int row, Object[] values)
        {
            insertRow(row, values, true);
        }

        /**********************************************************************
         * Insert a row into the table
         * 
         * @param row
         *            row to insert
         * 
         * @param undoable
         *            true if the row deletion can be undone
         *********************************************************************/
        protected void insertRow(int row, Object values[], boolean undoable)
        {
            // Check if this row deletion is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new RowEdit(this,
                                                                                    values,
                                                                                    row,
                                                                                    0,
                                                                                    0,
                                                                                    TableEditType.INSERT));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }
            }

            super.insertRow(row, values);

            // Set the table sort capability in case the table had no rows
            // prior to the insert
            table.setTableSortable();
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the row removed in the undo stack
         *********************************************************************/
        @Override
        public void removeRow(int row)
        {
            removeRow(row, true);
        }

        /**********************************************************************
         * Remove a row from the table
         * 
         * @param row
         *            row to remove
         * 
         * @param undoable
         *            true if the row deletion can be undone
         *********************************************************************/
        protected void removeRow(int row, boolean undoable)
        {
            // Check if this row deletion is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    Object[] values = new Object[table.getModel().getColumnCount()];

                    // Step through each column of the row to be deleted
                    for (int column = 0; column < table.getModel().getColumnCount(); column++)
                    {
                        // Store the cell value
                        values[column] = getValueAt(row, column);
                    }

                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new RowEdit(this,
                                                                                    values,
                                                                                    row,
                                                                                    0,
                                                                                    0,
                                                                                    TableEditType.DELETE));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }
            }

            // Disable the row sorter in case the last table row is removed and
            // a row filter is active
            table.setRowSorter(null);

            super.removeRow(row);

            // Set the table sort capability in case the table still has rows
            table.setTableSortable();
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the row moved in the undo stack
         *********************************************************************/
        @Override
        public void moveRow(int start, int end, int to)
        {
            moveRow(start, end, to, true);
        }

        /**********************************************************************
         * Move a row or rows within the table
         * 
         * @param start
         *            starting row index
         * 
         * @param end
         *            ending row index
         * 
         * @param to
         *            row index to which to move the rows between start and end
         * 
         * @param undoable
         *            true if the row movement can be undone
         *********************************************************************/
        protected void moveRow(int start, int end, int to, boolean undoable)
        {
            // Check if this row movement is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered and that the
                // target row isn't the same as the starting row
                if (listeners.length != 0 && start != to)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new RowEdit(this,
                                                                                    null,
                                                                                    to,
                                                                                    start,
                                                                                    end,
                                                                                    TableEditType.MOVE));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }
            }

            super.moveRow(start, end, to);

            // Get the table's row sorter
            TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();

            // Check if the table row sorter exists and if a row filter is in
            // effect
            if (sorter != null && sorter.getRowFilter() != null)
            {
                // Issue data and structural change events to ensure the table
                // is redrawn correctly. If this is done for all cases it can
                // cause the table to scroll to an unexpected position
                ((UndoableTableModel) table.getModel()).fireTableDataChanged();
                ((UndoableTableModel) table.getModel()).fireTableStructureChanged();
            }
        }

        /**********************************************************************
         * Sort the table rows
         * 
         * @param oldSortKeys
         *            sort keys prior to sorting the rows
         * 
         * @param newSortKeys
         *            sort keys after sorting the rows
         *********************************************************************/
        protected void sortRows(List<? extends SortKey> oldSortKeys,
                                List<? extends SortKey> newSortKeys)
        {
            // Check if this row sort is undoable
            if (isAllowUndo)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent sortEvent = new UndoableEditEvent(this,
                                                                        new RowSort(oldSortKeys,
                                                                                    newSortKeys));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(sortEvent);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Table data array update event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class DataVectorEdit extends AbstractUndoableEdit
    {
        private final Object[][] oldDataVector;
        private final Object[][] newDataVector;

        /**********************************************************************
         * Table data array update event handler class constructor
         * 
         * @param oldDataVector
         *            table data prior to the update
         * 
         * @param newDataVector
         *            table data after the update
         *********************************************************************/
        private DataVectorEdit(Object[][] oldDataVector,
                               Object[][] newDataVector)
        {
            this.oldDataVector = oldDataVector;
            this.newDataVector = newDataVector;

            // Add the table data update to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Undo setting the table data
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Set the table data to the old values
            table.loadDataArrayIntoTable(oldDataVector, false);
        }

        /**********************************************************************
         * Redo setting the table data
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Set the table data back to the new values
            table.loadDataArrayIntoTable(newDataVector, false);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "DataVector";
        }
    }

    /**************************************************************************
     * Row sort event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class RowSort extends AbstractUndoableEdit
    {
        private final List<? extends SortKey> oldSortKeys;
        private final List<? extends SortKey> newSortKeys;

        /**********************************************************************
         * Row sort event handler class constructor
         * 
         * @param oldSortKeys
         *            sort keys prior to sorting the rows
         * 
         * @param newSortKeys
         *            sort keys after sorting the rows
         *********************************************************************/
        private RowSort(List<? extends SortKey> oldSortKeys,
                        List<? extends SortKey> newSortKeys)
        {
            this.oldSortKeys = oldSortKeys;
            this.newSortKeys = newSortKeys;

            // Add the row sort to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Undo sorting rows
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Undo the previous row sort
            table.getRowSorter().setSortKeys(oldSortKeys);
        }

        /**********************************************************************
         * Redo sorting rows
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Perform the row sort again
            table.getRowSorter().setSortKeys(newSortKeys);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "RowSort";
        }
    }

    /**************************************************************************
     * Row edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class RowEdit extends AbstractUndoableEdit
    {
        private final UndoableTableModel tableModel;
        private final Object[] values;
        private final int row;
        private final int start;
        private final int end;
        private final TableEditType type;

        /**********************************************************************
         * Row edit event handler constructor
         * 
         * @param tableModel
         *            table model
         * 
         * @param values
         *            array of column values for the row
         * 
         * @param row
         *            table row
         * 
         * @param start
         *            start index for a row move
         * 
         * @param end
         *            end index for a row move
         * 
         * @param type
         *            TableEditType.INSERT if inserting a row;
         *            TableEditType.DELETE if removing a row;
         *            TableEditType.MOVE if moving a row or rows
         *********************************************************************/
        private RowEdit(UndoableTableModel tableModel,
                        Object[] values,
                        int row,
                        int start,
                        int end,
                        TableEditType type)
        {
            this.tableModel = tableModel;
            this.values = (values == null)
                                          ? null
                                          : Arrays.copyOf(values, values.length);
            this.row = row;
            this.start = start;
            this.end = end;
            this.type = type;

            // Add the row edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Undo inserting, deleting, or moving a row
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            switch (type)
            {
                case INSERT:
                    // Undo the row insertion by deleting it
                    tableModel.removeRow(row, false);
                    break;

                case DELETE:
                    // Undo the row deletion by inserting it
                    tableModel.insertRow(row, values, false);
                    break;

                case MOVE:
                    // Undo the row move by moving it back
                    tableModel.moveRow(row, row + end - start, start, false);
                    break;
            }
        }

        /**********************************************************************
         * Redo inserting, deleting, or moving a row
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            switch (type)
            {
                case INSERT:
                    // Perform the row insertion again
                    tableModel.insertRow(row, values, false);
                    break;

                case DELETE:
                    // Perform the row deletion again
                    tableModel.removeRow(row, false);
                    break;

                case MOVE:
                    // Perform the row movement again
                    tableModel.moveRow(start, end, row, false);
                    break;
            }
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "RowEdit";
        }
    }

    /**************************************************************************
     * Cell edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class CellEdit extends AbstractUndoableEdit
    {
        private final UndoableTableModel tableModel;
        private final Object oldValue;
        private final Object newValue;
        private final int row;
        private final int column;

        /**********************************************************************
         * Cell edit event handler constructor
         * 
         * @param tableModel
         *            table model
         * 
         * @param oldValue
         *            previous cell value
         * 
         * @param newValue
         *            new cell value
         * 
         * @param row
         *            table row in model coordinates
         * 
         * @param column
         *            table column in model coordinates
         *********************************************************************/
        private CellEdit(UndoableTableModel tableModel,
                         Object oldValue,
                         Object newValue,
                         int row,
                         int column)
        {
            this.tableModel = tableModel;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.row = row;
            this.column = column;

            // Add the cell edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current cell value with the old value
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Update the table without generating an undo/redo event
            tableModel.setValueAt(oldValue, row, column, false);

            // Select the cell where the change was undone
            setSelectedCell();
        }

        /**********************************************************************
         * Replace the current cell value with the new value
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Update the table without generating an undo/redo event
            tableModel.setValueAt(newValue, row, column, false);

            // Select the cell where the change was redone
            setSelectedCell();
        }

        /**********************************************************************
         * Set the selected cell in the table. In order for this to select the
         * cell it must be scheduled to execute after other pending events
         *********************************************************************/
        private void setSelectedCell()
        {
            // Create a runnable object to be executed
            SwingUtilities.invokeLater(new Runnable()
            {
                /**************************************************************
                 * Execute after all pending Swing events are finished
                 *************************************************************/
                @Override
                public void run()
                {
                    // Request focus in the table and select the specified cell
                    table.requestFocusInWindow();
                    table.changeSelection(table.convertRowIndexToView(row),
                                          table.convertColumnIndexToView(column),
                                          false,
                                          false);
                }
            });
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CellEdit";
        }
    }

    /**************************************************************************
     * Table column model class for handling column move undo/redo
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTableColumnModel extends DefaultTableColumnModel
    {
        /**********************************************************************
         * Undoable table column model class constructor
         *********************************************************************/
        UndoableTableColumnModel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the column moved in the undo stack
         *********************************************************************/
        @Override
        public void moveColumn(int columnIndex, int newIndex)
        {
            moveColumn(columnIndex, newIndex, true);
        }

        /**********************************************************************
         * Move a column within the table
         * 
         * @param columnIndex
         *            starting column index
         * 
         * @param newIndex
         *            ending column index
         * 
         * @param undoable
         *            true if the row deletion can be undone
         *********************************************************************/
        private void moveColumn(int columnIndex,
                                int newIndex,
                                boolean undoable)
        {
            // Check if this column movement is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered and that the
                // column position has changed
                if (listeners.length != 0 && columnIndex != newIndex)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new ColumnEdit(this,
                                                                                       columnIndex,
                                                                                       newIndex,
                                                                                       TableEditType.MOVE));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }
            }

            super.moveColumn(columnIndex, newIndex);
        }
    }

    /**************************************************************************
     * Column edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class ColumnEdit extends AbstractUndoableEdit
    {
        private final UndoableTableColumnModel tableColumnModel;
        private final int start;
        private final int end;
        private final TableEditType type;

        /**********************************************************************
         * Column edit event handler constructor
         * 
         * @param tableColumnModel
         *            table column model
         * 
         * @param column
         *            table column
         * 
         * @param start
         *            start index for a column move
         * 
         * @param end
         *            end index for a column move
         * 
         * @param type
         *            TableEditType.INSERT if inserting a column;
         *            TableEditType.DELETE if removing a column;
         *            TableEditType.MOVE if moving a column
         *********************************************************************/
        private ColumnEdit(UndoableTableColumnModel tableColumnModel,
                           int start,
                           int end,
                           TableEditType type)
        {
            this.tableColumnModel = tableColumnModel;
            this.start = start;
            this.end = end;
            this.type = type;

            // Add the column edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Undo inserting, deleting, or moving a column
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            switch (type)
            {
                case MOVE:
                    // Undo the column move by moving it back
                    tableColumnModel.moveColumn(end, start, false);
                    break;

                case INSERT:
                    break;

                case DELETE:
                    break;
            }
        }

        /**********************************************************************
         * Redo inserting, deleting, or moving a column
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            switch (type)
            {
                case MOVE:
                    // Perform the column movement again
                    tableColumnModel.moveColumn(start, end, false);
                    break;

                case INSERT:
                    break;

                case DELETE:
                    break;
            }
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "ColumnEdit";
        }
    }

    /**************************************************************************
     * Undoable table cell selection class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableCellSelection extends JComponent
    {
        /**********************************************************************
         * Undoable table cell selection class constructor
         *********************************************************************/
        UndoableCellSelection()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Add or remove a table cell from the selection list
         * 
         * @param row
         *            table row, view coordinates
         * 
         * @param column
         *            table column, view coordinates
         *********************************************************************/
        protected void toggleCellSelection(int row, int column)
        {
            // Flag to indicate if a selection or deselection occurred
            boolean isSelected;

            // Check if the cell wasn't already selected
            if (!selectedCells.contains(row, column))
            {
                // Flag the data field represented by these coordinates for
                // removal
                selectedCells.add(row, column);

                isSelected = true;
            }
            // The cell was already selected
            else
            {
                // Remove the data field represented by these coordinates from
                // the list
                selectedCells.remove(row, column);

                isSelected = false;
            }

            // Update the undo manager with this event. Get the listeners for
            // this event
            UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

            // Check if there is an edit listener registered
            if (listeners != null)
            {
                // Create the edit event to be passed to the listeners
                UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                    new CellSelectEdit(row,
                                                                                       column,
                                                                                       isSelected));

                // Step through the registered listeners
                for (UndoableEditListener listener : listeners)
                {
                    // Inform the listener that an update occurred
                    listener.undoableEditHappened(editEvent);
                }
            }
        }
    }

    /**************************************************************************
     * Cell selection edit event handler class. This handles undo and redo of
     * cell selection events related to removing data field represented by the
     * selected cell
     *************************************************************************/
    @SuppressWarnings("serial")
    private class CellSelectEdit extends AbstractUndoableEdit
    {
        private final int row;
        private final int column;
        private final boolean isSelected;

        /**********************************************************************
         * Cell selection edit event handler constructor
         * 
         * @param row
         *            select cell row index
         * 
         * @param column
         *            select cell column index
         * 
         * @param isSelected
         *            true if the cell is selected
         *********************************************************************/
        private CellSelectEdit(int row,
                               int column,
                               boolean isSelected)
        {
            this.row = row;
            this.column = column;
            this.isSelected = isSelected;

            // Add the cell selection edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current cell selection state with the old state
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the cell where the change was undone
            setSelectedCell(!isSelected);
        }

        /**********************************************************************
         * Replace the current cell selection state with the new state
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the cell where the change was redone
            setSelectedCell(isSelected);
        }

        /**********************************************************************
         * Set the selected cell
         * 
         * @param selectState
         *            state to which the cell should be set; true to show the
         *            cell selected and false for deselected
         *********************************************************************/
        private void setSelectedCell(boolean selectState)
        {
            // Check if the cell wasn't already selected
            if (selectState)
            {
                // Flag the data field represented by these coordinates for
                // removal
                selectedCells.add(row, column);
            }
            // The cell was already selected
            else
            {
                // Remove the data field represented by these coordinates from
                // the list
                selectedCells.remove(row, column);
            }

            // Force the table to redraw so that the selection state is
            // displayed correctly
            table.repaint();
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CellSelectEdit";
        }
    }

    /**************************************************************************
     * Undoable tree node selection class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTreeNodeSelection extends JComponent
    {
        private ToolTipTreeNode[] oldNodes = null;

        /**********************************************************************
         * Undoable tree node selection class constructor
         *********************************************************************/
        UndoableTreeNodeSelection()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Add the tree node selection edit to the undo/redo stack
         * 
         * @param nodes
         *            array of selected tree nodes
         *********************************************************************/
        protected void selectTreeNode(ToolTipTreeNode[] nodes)
        {
            // Check if undoing is enabled
            if (isAllowUndo)
            {
                // Update the undo manager with this event. Get the listeners
                // for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners != null)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new TreeNodeSelectEdit(oldNodes,
                                                                                               nodes));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Create a copy of the node selection for use in the next node
            // selection edit
            oldNodes = Arrays.copyOf(nodes, nodes.length);
        }
    }

    /**************************************************************************
     * Tree node selection edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class TreeNodeSelectEdit extends AbstractUndoableEdit
    {
        private final ToolTipTreeNode[] oldNodes;
        private final ToolTipTreeNode[] newNodes;

        /**********************************************************************
         * Tree node selection edit event handler class constructor
         * 
         * @param oldNodes
         *            array of previous tree node(s) selected
         * 
         * @param newNodes
         *            array of new tree node(s) selected
         *********************************************************************/
        private TreeNodeSelectEdit(ToolTipTreeNode[] oldNodes,
                                   ToolTipTreeNode[] newNodes)
        {
            this.oldNodes = oldNodes;
            this.newNodes = newNodes;

            // Add the cell selection edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Undo the tree node selection
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the old nodes
            changeTreeNodeSelection(oldNodes);
        }

        /**********************************************************************
         * Redo the tree node selection
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the new nodes
            changeTreeNodeSelection(newNodes);
        }

        /**********************************************************************
         * Change the selected tree nodes to the ones provided
         * 
         * @param nodes
         *            array of tree nodes to select
         *********************************************************************/
        private void changeTreeNodeSelection(ToolTipTreeNode[] nodes)
        {
            // Set the flag so that changes to the tree caused by the redo
            // operation aren't placed on the edit stack
            tree.setIgnoreSelectionChange(true);

            // Check if a node is to be selected
            if (nodes != null)
            {
                TreePath[] paths = new TreePath[nodes.length];

                // Step through each node to select
                for (int index = 0; index < nodes.length; index++)
                {
                    // Convert the node to a tree path and store it
                    paths[index] = (CcddCommonTreeHandler.getPathFromNode(nodes[index]));
                }

                // Set the node selection in the tree to the selected node(s)
                tree.setSelectionPaths(paths);
            }
            // No node was selected after to the current one
            else
            {
                // Deselect all nodes in the tree
                tree.clearSelection();
            }

            // Reset the flag so that changes to the tree are handled by the
            // undo/redo manager
            tree.setIgnoreSelectionChange(false);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TreeNodeSelectEdit";
        }
    }

    /**************************************************************************
     * Tree model undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTreeModel extends DefaultTreeModel
    {
        /**********************************************************************
         * Tree model undo/redo class constructor. Creates a tree model in
         * which any node can have children
         * 
         * @param root
         *            tree root node
         *********************************************************************/
        protected UndoableTreeModel(TreeNode root)
        {
            super(root);

            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Tree model undo/redo class constructor. Creates a tree model
         * specifying whether any node can have children, or whether only
         * certain nodes can have children
         * 
         * @param root
         *            tree root node
         * 
         * @param asksAllowsChildren
         *            false if any node can have children, true if each node is
         *            asked to see if it can have children
         *********************************************************************/
        protected UndoableTreeModel(TreeNode root, boolean asksAllowsChildren)
        {
            super(root, asksAllowsChildren);

            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Add the specified child node to the specified parent node at the
         * given position
         * 
         * @param child
         *            node to add
         * 
         * @param parent
         *            node to which to add the new node
         * 
         * @param index
         *            position in the existing node at which to place the new
         *            node
         *********************************************************************/
        @Override
        public void insertNodeInto(MutableTreeNode child,
                                   MutableTreeNode parent,
                                   int index)
        {
            super.insertNodeInto(child, parent, index);

            // Check if undoing is enabled
            if (isAllowUndo)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new UndoableNodeAddEdit(parent,
                                                                                                child,
                                                                                                index));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }

        /**********************************************************************
         * Change the user object value for the specified tree path
         * 
         * @param path
         *            tree path for which to change the user object value
         * 
         * @param newValue
         *            new value for the user object
         *********************************************************************/
        @Override
        public void valueForPathChanged(TreePath path, Object newValue)
        {
            // Check if undoing is enabled
            if (isAllowUndo)
            {
                // Save the old value of the node being changed
                MutableTreeNode node = (MutableTreeNode) path.getLastPathComponent();
                Object oldValue = ((DefaultMutableTreeNode) node).getUserObject();

                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new UndoableNodeChangeEdit(path,
                                                                                                   oldValue,
                                                                                                   newValue));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            super.valueForPathChanged(path, newValue);
        }

        /**********************************************************************
         * Remove the specified node from its parent node
         * 
         * @param child
         *            node to remove
         *********************************************************************/
        @Override
        public void removeNodeFromParent(MutableTreeNode child)
        {
            // First, get a reference to the child's index and parent
            MutableTreeNode parent = (MutableTreeNode) child.getParent();
            int index = (parent != null) ? parent.getIndex(child) : -1;

            super.removeNodeFromParent(child);

            // Check if undoing is enabled
            if (isAllowUndo)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new UndoableNodeRemoveEdit(parent,
                                                                                                   child,
                                                                                                   index));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }

        /**********************************************************************
         * Tree node add edit event handler class
         *********************************************************************/
        private class UndoableNodeAddEdit extends AbstractUndoableEdit
        {
            private final MutableTreeNode child;
            private final MutableTreeNode parent;
            private final int index;

            /******************************************************************
             * Tree node add edit event handler constructor
             * 
             * @param parent
             *            parent node
             * 
             * @param child
             *            child node
             * 
             * @param index
             *            node index at which to insert the child in the parent
             *****************************************************************/
            public UndoableNodeAddEdit(MutableTreeNode parent,
                                       MutableTreeNode child,
                                       int index)
            {
                this.child = child;
                this.parent = parent;
                this.index = index;

                // Add the tree node add edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /******************************************************************
             * Remove the child node from the parent node
             *****************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                super.undo();

                // Remove the child from the parent
                parent.remove(index);

                // Notify any listeners that the node was re-added
                nodesWereRemoved(parent, new int[] {index}, new Object[] {child});
            }

            /******************************************************************
             * Add the child node back to the parent node
             *****************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                super.redo();

                // Add the child to the parent
                parent.insert(child, index);

                // Notify any listeners that the node was re-added
                nodesWereInserted(parent, new int[] {index});
            }

            /******************************************************************
             * Get the name of the edit type
             * 
             * @return Name of the edit type
             *****************************************************************/
            @Override
            public String getPresentationName()
            {
                return "Tree node add";
            }
        }

        /**********************************************************************
         * Tree node change edit event handler class
         *********************************************************************/
        private class UndoableNodeChangeEdit extends AbstractUndoableEdit
        {
            private final TreePath path;
            private final Object oldValue;
            private final Object newValue;

            /******************************************************************
             * Tree node change edit event handler constructor
             *****************************************************************/
            public UndoableNodeChangeEdit(TreePath path,
                                          Object oldValue,
                                          Object newValue)
            {
                this.path = path;
                this.oldValue = oldValue;
                this.newValue = newValue;

                // Add the check box edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /******************************************************************
             * Restore the node's previous user object value
             *****************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                super.undo();

                // Set the node's user object to the previous value
                MutableTreeNode node = (MutableTreeNode) path.getLastPathComponent();
                node.setUserObject(oldValue);

                // Notify any listeners that the tree has changed
                nodeChanged(node);
            }

            /******************************************************************
             * Set the node's user object value to the new value
             *****************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                super.redo();

                // Set the node's user object to the the new value
                MutableTreeNode node = (MutableTreeNode) path.getLastPathComponent();
                node.setUserObject(newValue);

                // Notify any listeners that the tree has changed
                nodeChanged(node);
            }

            /******************************************************************
             * Get the name of the edit type
             * 
             * @return Name of the edit type
             *****************************************************************/
            @Override
            public String getPresentationName()
            {
                return "Tree node change";
            }
        }

        /**********************************************************************
         * Tree node remove edit event handler class
         *********************************************************************/
        private class UndoableNodeRemoveEdit extends AbstractUndoableEdit
        {
            private final MutableTreeNode parent;
            private final MutableTreeNode child;
            private final int index;

            /******************************************************************
             * Tree node remove edit event handler constructor
             *****************************************************************/
            public UndoableNodeRemoveEdit(MutableTreeNode parent,
                                          MutableTreeNode child,
                                          int index)
            {
                this.parent = parent;
                this.child = child;
                this.index = index;

                // Add the check box edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /******************************************************************
             * Add the child node to the parent node
             *****************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                super.undo();

                // Insert the child back into the parent
                parent.insert(child, index);

                // Notify all listeners that the node was re-added
                nodesWereInserted(parent, new int[] {index});
            }

            /******************************************************************
             * Remove the child node from the parent node
             *****************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                super.redo();

                // Remove the child from the parent
                parent.remove(index);

                // Notify all listeners that the child was removed
                nodesWereRemoved(parent, new int[] {index}, new Object[] {child});
            }

            /******************************************************************
             * Get the name of the edit type
             * 
             * @return Name of the edit type
             *****************************************************************/
            @Override
            public String getPresentationName()
            {
                return "Tree node remove";
            }
        }
    }

    /**************************************************************************
     * Data field panel undo/redo class. This handles 'structural' edits to the
     * data field(s) (e.g., field addition or deletion, or changes to the
     * field's name, input type, size, etc., as opposed to the field's value)
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableDataFieldPanel extends JComponent
    {
        private List<FieldInformation> oldFieldInfo;

        /**********************************************************************
         * Data field panel undo/redo class constructor
         *********************************************************************/
        UndoableDataFieldPanel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /**********************************************************************
         * Set the current state of the data field information. This is the
         * state the data fields revert to in the event of an undo operation on
         * a data field edit (not including edits of the field's value)
         * 
         * @param oldFieldInfo
         *            list containing the current data field information
         *********************************************************************/
        protected void setCurrentFieldInfo(List<FieldInformation> oldFieldInfo)
        {
            this.oldFieldInfo = oldFieldInfo;
        }

        /**********************************************************************
         * Add an edit of the data field panel to the undo/redo stack
         * 
         * @param fieldPnlHandler
         *            reference to the data field panel handler in which the
         *            edit occurred
         * 
         * @param newFieldInfo
         *            updated data field information list
         *********************************************************************/
        protected void addDataFieldEdit(CcddInputFieldPanelHandler fieldPnlHandler,
                                        List<FieldInformation> newFieldInfo)
        {
            // Check if the edit is undoable
            if (isAllowUndo)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new DataFieldPanelEdit(fieldPnlHandler,
                                                                                               oldFieldInfo,
                                                                                               newFieldInfo));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending
                // the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }
    }

    /**************************************************************************
     * Data field panel edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class DataFieldPanelEdit extends AbstractUndoableEdit
    {
        private final CcddInputFieldPanelHandler fieldPnlHandler;
        private final List<FieldInformation> oldFieldInfo;
        private final List<FieldInformation> newFieldInfo;

        /**********************************************************************
         * Data field panel edit event handler constructor
         * 
         * @param fieldPnlHandler
         *            reference to the data field panel handler in which the
         *            edit occurred
         * 
         * @param oldFieldInfo
         *            list containing the original state of the data field
         *            information
         * 
         * @param newFieldInfo
         *            list containing the new state of the data field
         *            information
         *********************************************************************/
        private DataFieldPanelEdit(CcddInputFieldPanelHandler fieldPnlHandler,
                                   List<FieldInformation> oldFieldInfo,
                                   List<FieldInformation> newFieldInfo)
        {
            this.fieldPnlHandler = fieldPnlHandler;
            this.oldFieldInfo = oldFieldInfo;
            this.newFieldInfo = newFieldInfo;

            // Add the text field edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Rebuild the data field panel with the original data field
         * information
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Rebuild the data fields using the original data field
            // information list. Do not store this edit operation on the
            // undo/redo stack
            fieldPnlHandler.setDataFieldInformation(oldFieldInfo);
            fieldPnlHandler.createDataFieldPanel(false);
        }

        /**********************************************************************
         * Rebuild the data field panel with the updated data field information
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Rebuild the data fields using the updated data field information
            // list. Do not store this edit operation on the undo/redo stack
            fieldPnlHandler.setDataFieldInformation(newFieldInfo);
            fieldPnlHandler.createDataFieldPanel(false);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "DataFieldPanel";
        }
    }
}