/**
 * CFS Command and Data Dictionary undoable components handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import CCDD.CcddClassesComponent.CellSelectionHandler;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddUndoHandler.UndoableCheckBox.UndoableToggleButtonModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary undoable components handler class
 *************************************************************************************************/
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

    // Flag that indicates if an edit sequence should be ended when a new value is entered. This is
    // not used in the undoable table model and table column model classes
    private boolean isAutoEndEditSequence;

    // Array list edit types
    private static enum ListEditType
    {
        ADD,
        ADD_INDEX,
        ADD_ALL,
        REMOVE,
        REMOVE_INDEX,
        REMOVE_ALL,
        CLEAR
    }

    // Row and column edit types
    private static enum TableEditType
    {
        INSERT,
        DELETE,
        MOVE
    }

    /**********************************************************************************************
     * Undoable components handler constructor
     *
     * @param undoManager
     *            reference to the undo manager
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Set the reference to the data field handler. This allows changes to the contents of a data
     * field that is subsequently deleted, then restored via an undo, to retain the undo capability
     * for the content changes
     *
     * @param fieldHandler
     *            reference to the data field handler; null to not retain data field content
     *            changes following deletion and restoration of the field
     *********************************************************************************************/
    protected void setFieldHandler(CcddFieldHandler fieldHandler)
    {
        this.fieldHandler = fieldHandler;
    }

    /**********************************************************************************************
     * Set the reference to the table for which node selection changes are to be tracked
     *
     * @param table
     *            reference to the table for which node selection changes are to be tracked
     *********************************************************************************************/
    protected void setTable(CcddJTableHandler table)
    {
        this.table = table;
    }

    /**********************************************************************************************
     * Set the reference to the table cell selection container for which selection changes are to
     * be tracked
     *
     * @param selectedCells
     *            reference to the cell selection container for which selection changes are to be
     *            tracked
     *********************************************************************************************/
    protected void setSelectedCells(CellSelectionHandler selectedCells)
    {
        this.selectedCells = selectedCells;
    }

    /**********************************************************************************************
     * Set the reference to the tree for which node selection changes are to be tracked
     *
     * @param tree
     *            reference to the tree for which node selection changes are to be tracked
     *********************************************************************************************/
    protected void setTree(CcddCommonTreeHandler tree)
    {
        this.tree = tree;
    }

    /**********************************************************************************************
     * Set the flag that allows registering an edit with the undo manager
     *
     * @param allow
     *            true to allow edits to be stored for possible undo action; false to perform the
     *            edit, but without storing it
     *********************************************************************************************/
    protected void setAllowUndo(boolean allow)
    {
        isAllowUndo = allow;
    }

    /**********************************************************************************************
     * Get the flag that allows registering an edit with the undo manager
     *
     * @return true if edits are stored for possible undo action; false to if edits are performed
     *         without storing it
     *********************************************************************************************/
    protected boolean isAllowUndo()
    {
        return isAllowUndo;
    }

    /**********************************************************************************************
     * Set the flag that automatically ends an edit sequence. This flag is used by the group
     * manager, link manager, and the data field editor panel so that multiple group/link/field
     * changes are stored as a single edit rather than a number of individual edits; if the edit is
     * undone then the changes are restored in a single operation. This flag is not used in the
     * undoable table model and table column model classes
     *
     * @param autoEndEdit
     *            true to automatically end an edit sequence when a new edit operation occurs
     *********************************************************************************************/
    protected void setAutoEndEditSequence(boolean autoEndEdit)
    {
        isAutoEndEditSequence = autoEndEdit;
    }

    /**********************************************************************************************
     * Get the status of the flag that indicates if an edit sequence should be automatically ended.
     * This flag is not used in the undoable table model and table column model classes
     *
     * @return true if the edit sequence is ended automatically; false if automatic ending is
     *         suspended
     *********************************************************************************************/
    protected boolean isAutoEndEditSequence()
    {
        return isAutoEndEditSequence;
    }

    /**********************************************************************************************
     * Array list undo/redo class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableArrayList<E> extends ArrayList<E>
    {
        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list item
         * inserted in the undo stack
         *****************************************************************************************/
        @Override
        public boolean add(E listItem)
        {
            return add(listItem, true);
        }

        /******************************************************************************************
         * Add an item to the list
         *
         * @param listItem
         *            item to add to the list
         *
         * @param undoable
         *            true if the list addition can be undone
         *
         * @return true
         *****************************************************************************************/
        protected boolean add(E listItem, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      -1,
                                                      listItem,
                                                      null,
                                                      ListEditType.ADD));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            return super.add(listItem);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list item
         * inserted in the undo stack
         *****************************************************************************************/
        @Override
        public void add(int listIndex, E listItem)
        {
            add(listIndex, listItem, true);
        }

        /******************************************************************************************
         * Add an item to the list at the specified index
         *
         * @param listIndex
         *            index in the list at which to insert the list item
         *
         * @param listItem
         *            item to add to the list
         *
         * @param undoable
         *            true if the list addition can be undone
         *****************************************************************************************/
        protected void add(int listIndex, E listItem, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      listIndex,
                                                      listItem,
                                                      null,
                                                      ListEditType.ADD_INDEX));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            super.add(listIndex, listItem);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list items
         * inserted in the undo stack
         *****************************************************************************************/
        @Override
        public boolean addAll(Collection<? extends E> listItems)
        {
            return addAll(listItems, true);
        }

        /******************************************************************************************
         * Add a list of items to the list
         *
         * @param listItems
         *            list of items to add to the list
         *
         * @param undoable
         *            true if the list addition can be undone
         *
         * @return true if the list changed as a result of the call
         *****************************************************************************************/
        @SuppressWarnings("unchecked")
        protected boolean addAll(Collection<? extends E> listItems, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      -1,
                                                      null,
                                                      (Collection<E>) listItems,
                                                      ListEditType.ADD_ALL));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            return super.addAll(listItems);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list item
         * removed in the undo stack
         *****************************************************************************************/
        @Override
        public boolean remove(Object listItem)
        {
            return remove(listItem, true);
        }

        /******************************************************************************************
         * Remove an item from the list
         *
         * @param listItem
         *            item to remove from the list
         *
         * @param undoable
         *            true if the list removal can be undone
         *
         * @return true if the list contained the specified element
         *****************************************************************************************/
        @SuppressWarnings("unchecked")
        protected boolean remove(Object listItem, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      -1,
                                                      (E) listItem,
                                                      null,
                                                      ListEditType.REMOVE));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            return super.remove(listItem);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list item
         * removed in the undo stack
         *****************************************************************************************/
        @Override
        public E remove(int index)
        {
            return remove(index, true);
        }

        /******************************************************************************************
         * Remove an item from the list at the specified index
         *
         * @param index
         *            index in the list at which to remove the list item
         *
         * @param undoable
         *            true if the list removal can be undone
         *
         * @return The element that was removed from the list
         *****************************************************************************************/
        protected E remove(int index, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      index,
                                                      get(index),
                                                      null,
                                                      ListEditType.REMOVE_INDEX));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            return super.remove(index);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list items
         * removed in the undo stack
         *****************************************************************************************/
        @Override
        public boolean removeAll(Collection<?> listItems)
        {
            return remove(listItems, true);
        }

        /******************************************************************************************
         * Remove a list of items to the list
         *
         * @param listItems
         *            list of items to remove from the list
         *
         * @param undoable
         *            true if the list removal can be undone
         *
         * @return true if the list changed as a result of the call
         *****************************************************************************************/
        @SuppressWarnings("unchecked")
        protected boolean removeAll(Collection<?> listItems, boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      -1,
                                                      null,
                                                      (Collection<E>) listItems,
                                                      ListEditType.REMOVE_ALL));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            return super.removeAll(listItems);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the list items
         * removed in the undo stack
         *****************************************************************************************/
        @Override
        public void clear()
        {
            clear(true);
        }

        /******************************************************************************************
         * Remove all items from the list
         *
         * @param undoable
         *            true if the list removal can be undone
         *****************************************************************************************/
        protected void clear(boolean undoable)
        {
            // Check if undoing is enabled, if the edit is undoable, and if the check box selection
            // state changed
            if (isAllowUndo && undoable)
            {
                List<E> removed = new ArrayList<E>();
                removed.addAll(this);

                // Create the edit event
                new UndoableEditEvent(this,
                                      new ListEdit<E>(UndoableArrayList.this,
                                                      -1,
                                                      null,
                                                      removed,
                                                      ListEditType.CLEAR));

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            super.clear();
        }
    }

    /**********************************************************************************************
     * List edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class ListEdit<T> extends AbstractUndoableEdit
    {
        private final UndoableArrayList<T> arrayList;
        private final int listIndex;
        private final T listItem;
        private final Collection<T> listItems;
        private final ListEditType type;

        /******************************************************************************************
         * List edit event handler constructor
         *
         * @param undoableArrayList
         *            reference to the array list
         *
         * @param listIndex
         *            index at which to add or remove the item (ignored if not an INDEX operation)
         *
         * @param listItem
         *            item to add or remove from the list (ignored if not an item add or remove
         *            operation in which case null can be used)
         *
         * @param listItems
         *            list of items to add or remove (ignored if not a list item add or remove
         *            operation in which case null can be used)
         *
         * @param type
         *            ListEditType.ADD if if adding an item to the list; ListEditType.ADD_INDEX if
         *            if adding an item to the list at a specific index; ListEditType.ADD_ALL if if
         *            adding a list of items to the list; ListEditType.REMOVE if removing an item
         *            from the list; ListEditType.REMOVE_INDEX if removing the item at the
         *            specified index from the list; ListEditType.CLEAR if removing all items from
         *            the list
         *****************************************************************************************/
        ListEdit(UndoableArrayList<T> undoableArrayList,
                 int listIndex,
                 T listItem,
                 Collection<T> listItems,
                 ListEditType type)
        {
            this.arrayList = undoableArrayList;
            this.listIndex = listIndex;
            this.listItem = listItem;
            this.listItems = listItems;
            this.type = type;

            // Add the list edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Undo adding or removing one or more list items
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            switch (type)
            {
                case ADD:
                    // Undo the list item addition by removing it
                    arrayList.remove(listItem, false);
                    break;

                case ADD_INDEX:
                    // Undo the list item addition by removing it
                    arrayList.remove(listIndex, false);
                    break;

                case ADD_ALL:
                    // Undo the list item additions by removing them
                    arrayList.removeAll(listItems, false);
                    break;

                case REMOVE:
                    // Undo the list item removal by adding it
                    arrayList.add(listItem, false);
                    break;

                case REMOVE_INDEX:
                    // Undo the list item removal by adding it
                    arrayList.add(listIndex, listItem, false);
                    break;

                case REMOVE_ALL:
                    // Undo the list item removals by adding them
                    arrayList.addAll(listItems, false);
                    break;

                case CLEAR:
                    // Undo the list clear by adding all of the items
                    arrayList.addAll(listItems, false);
                    break;
            }
        }

        /******************************************************************************************
         * Redo adding or removing one or more list items
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            switch (type)
            {
                case ADD:
                    // Perform the list item addition again
                    arrayList.add(listItem, false);
                    break;

                case ADD_INDEX:
                    // Perform the list item addition again
                    arrayList.add(listIndex, listItem, false);
                    break;

                case ADD_ALL:
                    // Perform the list item additions again
                    arrayList.addAll(listItems, false);
                    break;

                case REMOVE:
                    // Perform the list item removal again
                    arrayList.remove(listItem, false);
                    break;

                case REMOVE_INDEX:
                    // Perform the list item removal again
                    arrayList.remove(listIndex, false);
                    break;

                case REMOVE_ALL:
                    // Perform the list item removals again
                    arrayList.removeAll(listItems, false);
                    break;

                case CLEAR:
                    // Perform the list clear again
                    arrayList.clear(false);
                    break;
            }
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "ListEdit";
        }
    }

    /**********************************************************************************************
     * Check box value undo/redo class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableCheckBox extends JCheckBox
    {
        private String fieldOwner = null;
        private String fieldName = null;

        /******************************************************************************************
         * Toggle button model undo/redo class
         *****************************************************************************************/
        protected class UndoableToggleButtonModel extends ToggleButtonModel
        {
            /**************************************************************************************
             * Toggle button model undo/redo class constructor
             *
             * @param select
             *            initial selection state of the check box
             *************************************************************************************/
            UndoableToggleButtonModel(boolean select)
            {
                super();

                // Register the undo manager as an edit listener for this class
                listenerList.add(UndoableEditListener.class, undoManager);

                // Set the check box's initial state
                setSelected(select, false);
            }

            /**************************************************************************************
             * Override the default method with a method that includes a flag to store the edit in
             * the undo stack
             *************************************************************************************/
            @Override
            public void setSelected(boolean select)
            {
                setSelected(select, true);
            }

            /**************************************************************************************
             * Change the value of a check box
             *
             * @param select
             *            check box state; true to select
             *
             * @param undoable
             *            true if the change can be undone
             *************************************************************************************/
            protected void setSelected(boolean select, boolean undoable)
            {
                // Store the current check box selection state
                boolean oldValue = isSelected();

                super.setSelected(select);

                // Check if undoing is enabled, if the edit is undoable, and if the check box
                // selection state changed
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
                                                                                             isSelected(),
                                                                                             fieldOwner,
                                                                                             fieldName));

                        // Step through the registered listeners
                        for (UndoableEditListener listener : listeners)
                        {
                            // Inform the listener that an update occurred
                            listener.undoableEditHappened(editEvent);
                        }
                    }

                    // Check if the flag is set that allows automatically ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }
                }
            }
        }

        /******************************************************************************************
         * Check box value undo/redo class constructor
         *****************************************************************************************/
        UndoableCheckBox()
        {
            // Create the check box
            super();

            // Set the model, and the edit and focus listeners
            setModelAndListeners(false);
        }

        /******************************************************************************************
         * Check box value undo/redo class constructor
         *
         * @param text
         *            text to display beside the check box
         *
         * @param selected
         *            initial check box selection state - true if selected
         *****************************************************************************************/
        UndoableCheckBox(String text, boolean selected)
        {
            // Create the check box
            super(text, selected);

            // Set the model, and the edit and focus listeners
            setModelAndListeners(selected);
        }

        /******************************************************************************************
         * Set the data field to which the check box belongs (only used if the check box is a data
         * field)
         *
         * @param fieldOwner
         *            owner of the data field to which the check box belongs
         *
         * @param fieldName
         *            name of the data field to which the check box belongs
         *****************************************************************************************/
        protected void setUndoFieldInformation(String fieldOwner, String fieldName)
        {
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;
        }

        /******************************************************************************************
         * Add a the undo manager to the edit listener list, set the check box model to be
         * undoable, and add a focus change listener to the check box
         *
         * @param select
         *            initial selection state of the check box
         *****************************************************************************************/
        private void setModelAndListeners(boolean select)
        {
            // Set the model so that check box edits can be undone/redone
            setModel(new UndoableToggleButtonModel(select));

            // Add a listener for check box focus changes
            addFocusListener(new FocusAdapter()
            {
                /**********************************************************************************
                 * Handle a focus gained event
                 *********************************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Check if the flag is set that allows automatically ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Check box edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class CheckBoxEdit extends AbstractUndoableEdit
    {
        private UndoableCheckBox checkBox;
        private final boolean oldValue;
        private final boolean newValue;
        private final String fieldOwner;
        private final String fieldName;

        /******************************************************************************************
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
         *
         * @param fieldOwner
         *            owner of the data field to which the check box belongs; null if the check box
         *            is not a data field
         *
         * @param fieldName
         *            name of the data field to which the check box belongs; null if the check box
         *            is not a data field
         *****************************************************************************************/
        CheckBoxEdit(UndoableCheckBox checkBox,
                     boolean oldValue,
                     boolean newValue,
                     String fieldOwner,
                     String fieldName)
        {
            this.checkBox = checkBox;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;

            // Add the check box edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Replace the current check box selection state with the old state
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the check box where the change was undone
            setSelectedCheckBox(oldValue);
        }

        /******************************************************************************************
         * Replace the current check box selection state with the new state
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the check box where the change was redone
            setSelectedCheckBox(newValue);
        }

        /******************************************************************************************
         * Set the selected check box. In order for this to select the check box it must be
         * scheduled to execute after other pending events
         *
         * @param selected
         *            check box selection state
         *****************************************************************************************/
        private void setSelectedCheckBox(boolean selected)
        {
            // Check if a data field handler exists and if the check box's owner and name have been
            // set (the owner and name are set when the data field is created)
            if (fieldHandler != null && fieldOwner != null && fieldName != null)
            {
                // Get the reference to the data field based on the owner and name
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldOwner,
                                                                                    fieldName);

                // Check if the data field exists
                if (fieldInfo != null)
                {
                    // Set the check box reference to that associated with the specified data
                    // field. When a data field is altered the entire set of associated fields are
                    // recreated and therefore the check box reference stored in this edit no
                    // longer points to an existing field. Since the owner and field names in the
                    // data field information do still match, this step directs the undo/redo
                    // operation to the correct data field
                    checkBox = (UndoableCheckBox) fieldInfo.getInputFld();
                }
            }

            // Update the check box selection state
            ((UndoableToggleButtonModel) checkBox.getModel()).setSelected(selected, false);

            // Request a focus change to the check box that was changed
            setComponentFocus(checkBox);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CheckBox";
        }
    }

    /**********************************************************************************************
     * Combo box value undo/redo class. This is based on the custom padded combo box so that the
     * undoable combo box matches the appearance of other combo boxes in the application
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableComboBox extends PaddedComboBox
    {
        private Object oldValue = "";
        private String fieldOwner = null;
        private String fieldName = null;

        /******************************************************************************************
         * Combo box constructor with an empty list
         *
         * @param items
         *            combo box list items
         *
         * @param font
         *            combo box list item font
         *****************************************************************************************/
        UndoableComboBox(String[] items, Font font)
        {
            super(items, font);

            // Set the model, and the edit and focus listeners
            setModelAndListeners();
        }

        /******************************************************************************************
         * Set the data field to which the combo box belongs (only used if the combo box is a data
         * field)
         *
         * @param fieldOwner
         *            owner of the data field to which the combo box belongs
         *
         * @param fieldName
         *            name of the data field to which the combo box belongs
         *****************************************************************************************/
        protected void setUndoFieldInformation(String fieldOwner, String fieldName)
        {
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;
        }

        /******************************************************************************************
         * Add a the undo manager to the edit listener list, set the combo box model to be
         * undoable, and add a focus change listener to the combo box
         *****************************************************************************************/
        private void setModelAndListeners()
        {
            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);

            // Initialize the combo box's original value
            oldValue = "";

            // Add a listener for combo box focus changes
            addFocusListener(new FocusAdapter()
            {
                /**********************************************************************************
                 * Handle a focus gained event
                 *********************************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Check if the flag is set that allows automatically ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }

                    // Store the current combo box selection
                    oldValue = getSelectedItem();
                }
            });
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the edit in the
         * undo stack
         *****************************************************************************************/
        @Override
        public void setSelectedItem(Object selection)
        {
            setSelectedItem(selection, true);
        }

        /******************************************************************************************
         * Change the combo box selected item
         *
         * @param selection
         *            new combo box selection
         *
         * @param undoable
         *            true if the change can be undone
         *****************************************************************************************/
        protected void setSelectedItem(Object selection, boolean undoable)
        {
            super.setSelectedItem(selection);

            // Check if undoing is enabled, the edit is undoable, and if the text field text
            // changed
            if (isAllowUndo && undoable && !selection.equals(oldValue))
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners.length != 0)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new ComboBoxEdit(UndoableComboBox.this,
                                                                                         oldValue,
                                                                                         selection,
                                                                                         fieldOwner,
                                                                                         fieldName));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Store the value as the old value for the next edit
            oldValue = selection;
        }
    }

    /**********************************************************************************************
     * Combo box edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class ComboBoxEdit extends AbstractUndoableEdit
    {
        private UndoableComboBox comboBox;
        private final Object oldValue;
        private final Object newValue;
        private final String fieldOwner;
        private final String fieldName;

        /******************************************************************************************
         * Combo box edit event handler constructor
         *
         * @param comboBox
         *            reference to the combo box being edited
         *
         * @param oldValue
         *            previous selected combo box item
         *
         * @param newValue
         *            new selected combo box item
         *
         * @param fieldOwner
         *            owner of the data field to which the combo box belongs; null if the combo box
         *            is not a data field
         *
         * @param fieldName
         *            name of the data field to which the combo box belongs; null if the combo box
         *            is not a data field
         *****************************************************************************************/
        ComboBoxEdit(UndoableComboBox comboBox,
                     Object oldValue,
                     Object newValue,
                     String fieldOwner,
                     String fieldName)
        {
            this.comboBox = comboBox;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;

            // Add the combo box edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Replace the current combo box selection state with the old state
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Set the combo box selection where the change was undone
            setSelectedComboBox(oldValue);
        }

        /******************************************************************************************
         * Replace the current combo box selection state with the new state
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Set the combo box selection where the change was redone
            setSelectedComboBox(newValue);
        }

        /******************************************************************************************
         * Set the selected combo box item
         *
         * @param selection
         *            combo box selection item
         *****************************************************************************************/
        private void setSelectedComboBox(Object selection)
        {
            // Check if a data field handler exists and if the combo box's owner and name have been
            // set (the owner and name are set when the data field is created)
            if (fieldHandler != null && fieldOwner != null && fieldName != null)
            {
                // Get the reference to the data field based on the owner and name
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldOwner,
                                                                                    fieldName);

                // Check if the data field exists
                if (fieldInfo != null)
                {
                    // Set the text field reference to that associated with the specified data
                    // field. When a data field is altered the entire set of associated fields are
                    // recreated and therefore the combo box reference stored in this edit no
                    // longer points to an existing field. Since the owner and field names in the
                    // data field information do still match, this step directs the undo/redo
                    // operation to the correct data field
                    comboBox = (UndoableComboBox) fieldInfo.getInputFld();
                }
            }

            // Update the combo box selection item
            comboBox.getModel().setSelectedItem(selection);

            // Request a focus change to the combo box that was changed
            setComponentFocus(comboBox);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "ComboBox";
        }
    }

    /**********************************************************************************************
     * Text field value undo/redo class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextField extends JTextField
    {
        private String oldValue = "";
        private String fieldOwner = null;
        private String fieldName = null;

        /******************************************************************************************
         * Text field value undo/redo class constructor
         *****************************************************************************************/
        UndoableTextField()
        {
            // Create the text field
            super();

            // Add a listener for text field focus changes
            setListeners("");
        }

        /******************************************************************************************
         * Text field value undo/redo class constructor
         *
         * @param text
         *            text to display in the text field
         *****************************************************************************************/
        UndoableTextField(String text)
        {
            // Create the text field
            super(text);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /******************************************************************************************
         * Text field value undo/redo class constructor
         *
         * @param text
         *            text to display in the text field
         *
         * @param columns
         *            the number of columns to use to calculate the preferred width of the text
         *            field
         *****************************************************************************************/
        UndoableTextField(String text, int columns)
        {
            // Create the text field
            super(text, columns);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /******************************************************************************************
         * Add the undo manager to the edit listeners and add a focus change listener to the text
         * field
         *
         * @param text
         *            text to display in the text field
         *****************************************************************************************/
        private void setListeners(String text)
        {
            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);

            // Initialize the text field's original value
            oldValue = text;

            // Add a listener for text field focus changes
            addFocusListener(new FocusAdapter()
            {
                /**********************************************************************************
                 * Handle a focus gained event
                 *********************************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Check if the flag is set that allows automatically ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }

                    // Store the current text field value
                    oldValue = getText();
                }
            });

            // Add a listener for keyboard inputs
            addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Handle a key press event. This allows the text field's owner to be informed of a
                 * change in text, such as to update the text field container's change indicator
                 *********************************************************************************/
                @Override
                public void keyPressed(final KeyEvent ke)
                {
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
                                // Inform the owner of the text field that the value has changed
                                undoManager.ownerHasChanged();
                            }
                        }
                    });
                }
            });
        }

        /******************************************************************************************
         * Set the data field to which the text field belongs (only used if the text field is a
         * data field)
         *
         * @param fieldOwner
         *            owner of the data field to which the text field belongs
         *
         * @param fieldName
         *            name of the data field to which the text field belongs
         *****************************************************************************************/
        protected void setUndoFieldInformation(String fieldOwner, String fieldName)
        {
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the edit in the
         * undo stack
         *****************************************************************************************/
        @Override
        public void setText(String text)
        {
            setText(text, true);
        }

        /******************************************************************************************
         * Change the value of a text field
         *
         * @param text
         *            new text field value
         *
         * @param undoable
         *            true if the change can be undone
         *****************************************************************************************/
        protected void setText(String text, boolean undoable)
        {
            super.setText(text);

            // Check if undoing is enabled, the edit is undoable, and if the text field text
            // changed
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
                                                                                          text,
                                                                                          fieldOwner,
                                                                                          fieldName));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Store the value as the old value for the next edit
            oldValue = text;
        }
    }

    /**********************************************************************************************
     * Text field edit event handler class. If the data field handler is set then the undo/redo
     * operation compares the text field's name to the data field owner and field names to
     * determine if these match, and if so the text field specified in the data field information
     * is the one on which the undo/redo operation is performed
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class TextFieldEdit extends AbstractUndoableEdit
    {
        private UndoableTextField textField;
        private final String oldValue;
        private final String newValue;
        private final String fieldOwner;
        private final String fieldName;

        /******************************************************************************************
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
         *
         * @param fieldOwner
         *            owner of the data field to which the text field belongs; null if the text
         *            field is not a data field
         *
         * @param fieldName
         *            name of the data field to which the text field belongs; null if the text
         *            field is not a data field
         *****************************************************************************************/
        TextFieldEdit(UndoableTextField textField,
                      String oldValue,
                      String newValue,
                      String fieldOwner,
                      String fieldName)
        {
            this.textField = textField;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.fieldOwner = fieldOwner;
            this.fieldName = fieldName;

            // Add the text field edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Replace the current text field value with the old value
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text field where the change was undone
            setSelectedTextField(oldValue);
        }

        /******************************************************************************************
         * Replace the current text field value with the new value
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text field where the change was redone
            setSelectedTextField(newValue);
        }

        /******************************************************************************************
         * Set the selected text field. In order for this to select the text field it must be
         * scheduled to execute after other pending events
         *
         * @param value
         *            text to place in the text field
         *****************************************************************************************/
        private void setSelectedTextField(final String value)
        {
            // Check if a data field handler exists and if the text field's owner and name have
            // been set (the owner and name are set when the data field is created)
            if (fieldHandler != null && fieldOwner != null && fieldName != null)
            {
                // Get the reference to the data field based on the owner and name
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldOwner,
                                                                                    fieldName);

                // Check if the data field exists
                if (fieldInfo != null)
                {
                    // Set the text field reference to that associated with the specified data
                    // field. When a data field is altered the entire set of associated fields are
                    // recreated and therefore the text field reference stored in this edit no
                    // longer points to an existing field. Since the owner and field names in the
                    // data field information do still match, this step directs the undo/redo
                    // operation to the correct data field
                    textField = (UndoableTextField) fieldInfo.getInputFld();
                }
            }

            // Update the text field value
            textField.setText(value, false);

            // Request a focus change to the text field that was changed
            setComponentFocus(textField);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextField";
        }
    }

    /**********************************************************************************************
     * Text area value undo/redo class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextArea extends JTextArea
    {
        private String oldValue;
        private boolean undoable;

        /******************************************************************************************
         * Text area value undo/redo class constructor
         *****************************************************************************************/
        UndoableTextArea()
        {
            // Create the text area
            super();

            // Set the edit and focus listeners
            setListeners("");
        }

        /******************************************************************************************
         * Text area value undo/redo class constructor
         *
         * @param text
         *            text to display in the text area
         *****************************************************************************************/
        UndoableTextArea(String text)
        {
            // Create the text area
            super(text);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /******************************************************************************************
         * Text area value undo/redo class constructor
         *
         * @param rows
         *            number of rows
         *
         * @param columns
         *            the number of columns to use to calculate the preferred width of the text
         *            area
         *****************************************************************************************/
        UndoableTextArea(int rows, int columns)
        {
            // Create the text area
            super("", rows, columns);

            // Set the edit and focus listeners
            setListeners("");
        }

        /******************************************************************************************
         * Text area value undo/redo class constructor
         *
         * @param text
         *            text to display in the text area
         *
         * @param rows
         *            number of rows
         *
         * @param columns
         *            the number of columns to use to calculate the preferred width of the text
         *            area
         *****************************************************************************************/
        UndoableTextArea(String text, int rows, int columns)
        {
            // Create the text area
            super(text, rows, columns);

            // Set the edit and focus listeners
            setListeners(text);
        }

        /******************************************************************************************
         * Add the undo manager to the edit listener list and add a focus change listener to the
         * text area
         *
         * @param text
         *            text to display in the text area
         *****************************************************************************************/
        private void setListeners(String text)
        {
            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);

            // Initialize the text area's original value
            oldValue = text;

            // Initialize the flag that allows undo/redo operations on this text area to be stored.
            // The text area must gain focus to be enabled
            undoable = false;

            // Add a listener for text area focus changes
            addFocusListener(new FocusListener()
            {
                /**********************************************************************************
                 * Handle a focus gained event
                 *********************************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Check if the flag is set that allows automatically ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }

                    // Store the current text area value
                    oldValue = getText();

                    // Set the flag to enable undo/redo operations in the text area
                    undoable = true;
                }

                /**********************************************************************************
                 * Handle a focus lost event
                 *********************************************************************************/
                @Override
                public void focusLost(FocusEvent fe)
                {
                    // Update the text area and store the edit in the undo/redo stack
                    setText();
                }
            });

            // Add a listener for keyboard inputs
            addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Handle a key press event. This allows the text field's owner to be informed of a
                 * change in text, such as to update the text area container's change indicator
                 *********************************************************************************/
                @Override
                public void keyPressed(final KeyEvent ke)
                {
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
                                // Inform the owner of the text field that the value has changed
                                undoManager.ownerHasChanged();
                            }
                        }
                    });
                }
            });
        }

        /******************************************************************************************
         * Flag the text as being allowed/prevented being stored as an update on the undo/redo
         * stack. The other criteria (the allow undo flag is set and the text has changed) must be
         * met in order for the edit to be undoable
         *
         * @param undoable
         *            true to enable putting the edit on the undo/redo stack
         *****************************************************************************************/
        protected void updateText(boolean undoable)
        {
            this.undoable = undoable;
            setText();
        }

        /******************************************************************************************
         * Check if an edit to the text area should be stored on the undo/redo stack, and if so add
         * it to the stack
         *****************************************************************************************/
        private void setText()
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

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Store the new text area value for the next edit
            oldValue = newValue;

            // Reset the flag so that any further edits must be preceded by another focus gained
            // event in order to be stored on the undo/redo stack
            undoable = false;
        }
    }

    /**********************************************************************************************
     * Text area edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class TextAreaEdit extends AbstractUndoableEdit
    {
        private final UndoableTextArea textArea;
        private final String oldValue;
        private final String newValue;

        /******************************************************************************************
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
         *****************************************************************************************/
        TextAreaEdit(UndoableTextArea textArea, String oldValue, String newValue)
        {
            this.textArea = textArea;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the text area edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Replace the current text area value with the old value
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text area where the change was undone
            setSelectedTextArea(oldValue);
        }

        /******************************************************************************************
         * Replace the current text area value with the new value
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text area where the change was redone
            setSelectedTextArea(newValue);
        }

        /******************************************************************************************
         * Set the text and focus to the text area
         *
         * @param value
         *            text to place in the text area
         *****************************************************************************************/
        private void setSelectedTextArea(String value)
        {
            // Update the text area value
            textArea.setText(value);

            // Request a focus change to the text area that was changed
            setComponentFocus(textArea);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextArea";
        }
    }

    /**********************************************************************************************
     * Set the focus to the specified component, without generating a new edit event
     *
     * @param comp
     *            JComponent to which to set the focus
     *********************************************************************************************/
    private void setComponentFocus(JComponent comp)
    {
        // Check that the component is still visible
        if (comp.isVisible())
        {
            // Disable input verification since this sets the component value, creating an edit
            // event
            comp.setVerifyInputWhenFocusTarget(false);

            // Request a focus change to the component that was changed
            comp.requestFocusInWindow();

            // Re-enable input verification
            comp.setVerifyInputWhenFocusTarget(true);
        }
    }

    /**********************************************************************************************
     * Table model class for handling cell undo/redo edits
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTableModel extends DefaultTableModel
    {
        /******************************************************************************************
         * Undoable table model class constructor
         *****************************************************************************************/
        UndoableTableModel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Return the class of the column object
         *****************************************************************************************/
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

        /******************************************************************************************
         * Placeholder for performing any clean-up of the cell value prior to setting the cell
         * contents. This can include, for example, removal of leading and trailing white space
         * characters
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
         * @return Cell value following any clean-up actions
         *****************************************************************************************/
        protected Object cleanUpCellValue(Object value, int row, int column)
        {
            return value;
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the edit in the
         * undo stack
         *****************************************************************************************/
        @Override
        public void setValueAt(Object value, int row, int column)
        {
            // Set the value in the cell and add this edit to the undo stack
            setValueAt(value, row, column, true);
        }

        /******************************************************************************************
         * Change the value of a cell
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
         * @param undoable
         *            true if the change can be undone
         *****************************************************************************************/
        protected void setValueAt(Object value, int row, int column, boolean undoable)
        {
            // Perform any clean-up actions on the cell
            value = cleanUpCellValue(value, row, column);

            // Get the cell's current value
            Object oldValue = getValueAt(row, column);

            // Check if the cell value has changed. Not processing duplicate updates prevents the
            // undo stack from registering the edit twice
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

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the table data
         * change in the undo stack
         *****************************************************************************************/
        @Override
        public void setDataVector(Object[][] dataVector, Object[] columnIdentifiers)
        {
            // Set the table data and add this update to the undo stack
            setDataVector(dataVector, columnIdentifiers, true);
        }

        /******************************************************************************************
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
         *****************************************************************************************/
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

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the row inserted
         * in the undo stack
         *****************************************************************************************/
        @Override
        public void insertRow(int row, Object[] values)
        {
            insertRow(row, values, true);
        }

        /******************************************************************************************
         * Insert a row into the table
         *
         * @param row
         *            row to insert
         *
         * @param values
         *            array containing the column information for the row to insert into the table
         *
         * @param undoable
         *            true if the row deletion can be undone
         *****************************************************************************************/
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

            // Set the table sort capability in case the table had no rows prior to the insert
            table.setTableSortable();
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the row removed
         * in the undo stack
         *****************************************************************************************/
        @Override
        public void removeRow(int row)
        {
            removeRow(row, true);
        }

        /******************************************************************************************
         * Remove a row from the table
         *
         * @param row
         *            row to remove
         *
         * @param undoable
         *            true if the row deletion can be undone
         *****************************************************************************************/
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

            // Disable the row sorter in case the last table row is removed and a row filter is
            // active
            table.setRowSorter(null);

            super.removeRow(row);

            // Set the table sort capability in case the table still has rows
            table.setTableSortable();
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the row moved in
         * the undo stack
         *****************************************************************************************/
        @Override
        public void moveRow(int start, int end, int to)
        {
            moveRow(start, end, to, true);
        }

        /******************************************************************************************
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
         *****************************************************************************************/
        protected void moveRow(int start, int end, int to, boolean undoable)
        {
            // Check if this row movement is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered and that the target row isn't the
                // same as the starting row
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

            // Check if the table row sorter exists and if a row filter is in effect
            if (sorter != null && sorter.getRowFilter() != null)
            {
                // Issue data and structural change events to ensure the table is redrawn
                // correctly. If this is done for all cases it can cause the table to scroll to an
                // unexpected position
                ((UndoableTableModel) table.getModel()).fireTableDataChanged();
                ((UndoableTableModel) table.getModel()).fireTableStructureChanged();
            }
        }

        /******************************************************************************************
         * Sort the table rows
         *
         * @param oldSortKeys
         *            sort keys prior to sorting the rows
         *
         * @param newSortKeys
         *            sort keys after sorting the rows
         *****************************************************************************************/
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

    /**********************************************************************************************
     * Table data array update event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class DataVectorEdit extends AbstractUndoableEdit
    {
        private final Object[][] oldDataVector;
        private final Object[][] newDataVector;

        /******************************************************************************************
         * Table data array update event handler class constructor
         *
         * @param oldDataVector
         *            table data prior to the update
         *
         * @param newDataVector
         *            table data after the update
         *****************************************************************************************/
        DataVectorEdit(Object[][] oldDataVector, Object[][] newDataVector)
        {
            this.oldDataVector = oldDataVector;
            this.newDataVector = newDataVector;

            // Add the table data update to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Undo setting the table data
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Set the table data to the old values
            table.loadDataArrayIntoTable(oldDataVector, false);
        }

        /******************************************************************************************
         * Redo setting the table data
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Set the table data back to the new values
            table.loadDataArrayIntoTable(newDataVector, false);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "DataVector";
        }
    }

    /**********************************************************************************************
     * Row sort event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class RowSort extends AbstractUndoableEdit
    {
        private final List<? extends SortKey> oldSortKeys;
        private final List<? extends SortKey> newSortKeys;

        /******************************************************************************************
         * Row sort event handler class constructor
         *
         * @param oldSortKeys
         *            sort keys prior to sorting the rows
         *
         * @param newSortKeys
         *            sort keys after sorting the rows
         *****************************************************************************************/
        RowSort(List<? extends SortKey> oldSortKeys, List<? extends SortKey> newSortKeys)
        {
            this.oldSortKeys = oldSortKeys;
            this.newSortKeys = newSortKeys;

            // Add the row sort to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Undo sorting rows
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Undo the previous row sort
            table.getRowSorter().setSortKeys(oldSortKeys);
        }

        /******************************************************************************************
         * Redo sorting rows
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Perform the row sort again
            table.getRowSorter().setSortKeys(newSortKeys);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "RowSort";
        }
    }

    /**********************************************************************************************
     * Row edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class RowEdit extends AbstractUndoableEdit
    {
        private final UndoableTableModel tableModel;
        private final Object[] values;
        private final int row;
        private final int start;
        private final int end;
        private final TableEditType type;

        /******************************************************************************************
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
         *            TableEditType.INSERT if inserting a row; TableEditType.DELETE if removing a
         *            row; TableEditType.MOVE if moving a row or rows
         *****************************************************************************************/
        RowEdit(UndoableTableModel tableModel,
                Object[] values,
                int row,
                int start,
                int end,
                TableEditType type)
        {
            this.tableModel = tableModel;
            this.values = values == null
                                         ? null
                                         : Arrays.copyOf(values, values.length);
            this.row = row;
            this.start = start;
            this.end = end;
            this.type = type;

            // Add the row edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Undo inserting, deleting, or moving a row
         *****************************************************************************************/
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

        /******************************************************************************************
         * Redo inserting, deleting, or moving a row
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "RowEdit";
        }
    }

    /**********************************************************************************************
     * Cell edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class CellEdit extends AbstractUndoableEdit
    {
        private final UndoableTableModel tableModel;
        private final Object oldValue;
        private final Object newValue;
        private final int row;
        private final int column;

        /******************************************************************************************
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
         *****************************************************************************************/
        CellEdit(UndoableTableModel tableModel,
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

        /******************************************************************************************
         * Replace the current cell value with the old value
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Update the table without generating an undo/redo event
            tableModel.setValueAt(oldValue, row, column, false);

            // Select the cell where the change was undone
            setSelectedCell();
        }

        /******************************************************************************************
         * Replace the current cell value with the new value
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Update the table without generating an undo/redo event
            tableModel.setValueAt(newValue, row, column, false);

            // Select the cell where the change was redone
            setSelectedCell();
        }

        /******************************************************************************************
         * Set the selected cell in the table. In order for this to select the cell it must be
         * scheduled to execute after other pending events
         *****************************************************************************************/
        private void setSelectedCell()
        {
            // Create a runnable object to be executed
            SwingUtilities.invokeLater(new Runnable()
            {
                /**********************************************************************************
                 * Execute after all pending Swing events are finished
                 *********************************************************************************/
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

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CellEdit";
        }
    }

    /**********************************************************************************************
     * Table column model class for handling column move undo/redo
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTableColumnModel extends DefaultTableColumnModel
    {
        /******************************************************************************************
         * Undoable table column model class constructor
         *****************************************************************************************/
        UndoableTableColumnModel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Override the default method with a method that includes a flag to store the column moved
         * in the undo stack
         *****************************************************************************************/
        @Override
        public void moveColumn(int columnIndex, int newIndex)
        {
            moveColumn(columnIndex, newIndex, true);
        }

        /******************************************************************************************
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
         *****************************************************************************************/
        private void moveColumn(int columnIndex, int newIndex, boolean undoable)
        {
            // Check if this column movement is undoable
            if (isAllowUndo && undoable)
            {
                // Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered and that the column position has
                // changed
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

    /**********************************************************************************************
     * Column edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class ColumnEdit extends AbstractUndoableEdit
    {
        private final UndoableTableColumnModel tableColumnModel;
        private final int start;
        private final int end;
        private final TableEditType type;

        /******************************************************************************************
         * Column edit event handler constructor
         *
         * @param tableColumnModel
         *            table column model
         *
         * @param start
         *            start index for a column move
         *
         * @param end
         *            end index for a column move
         *
         * @param type
         *            TableEditType.INSERT if inserting a column; TableEditType.DELETE if removing
         *            a column; TableEditType.MOVE if moving a column
         *****************************************************************************************/
        ColumnEdit(UndoableTableColumnModel tableColumnModel,
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

        /******************************************************************************************
         * Undo inserting, deleting, or moving a column
         *****************************************************************************************/
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

        /******************************************************************************************
         * Redo inserting, deleting, or moving a column
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "ColumnEdit";
        }
    }

    /**********************************************************************************************
     * Undoable table cell selection class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableCellSelection extends JComponent
    {
        /******************************************************************************************
         * Undoable table cell selection class constructor
         *****************************************************************************************/
        UndoableCellSelection()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Add or remove a table cell from the selection list
         *
         * @param row
         *            table row, view coordinates
         *
         * @param column
         *            table column, view coordinates
         *****************************************************************************************/
        protected void toggleCellSelection(int row, int column)
        {
            // Flag to indicate if a selection or deselection occurred
            boolean isSelected;

            // Check if the cell wasn't already selected
            if (!selectedCells.contains(row, column))
            {
                // Flag the data field represented by these coordinates for removal
                selectedCells.add(row, column);

                isSelected = true;
            }
            // The cell was already selected
            else
            {
                // Remove the data field represented by these coordinates from the list
                selectedCells.remove(row, column);

                isSelected = false;
            }

            // Update the undo manager with this event. Get the listeners for this event
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

    /**********************************************************************************************
     * Cell selection edit event handler class. This handles undo and redo of cell selection events
     * related to removing data field represented by the selected cell
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class CellSelectEdit extends AbstractUndoableEdit
    {
        private final int row;
        private final int column;
        private final boolean isSelected;

        /******************************************************************************************
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
         *****************************************************************************************/
        CellSelectEdit(int row, int column, boolean isSelected)
        {
            this.row = row;
            this.column = column;
            this.isSelected = isSelected;

            // Add the cell selection edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Replace the current cell selection state with the old state
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the cell where the change was undone
            setSelectedCell(!isSelected);
        }

        /******************************************************************************************
         * Replace the current cell selection state with the new state
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the cell where the change was redone
            setSelectedCell(isSelected);
        }

        /******************************************************************************************
         * Set the selected cell
         *
         * @param selectState
         *            state to which the cell should be set; true to show the cell selected and
         *            false for deselected
         *****************************************************************************************/
        private void setSelectedCell(boolean selectState)
        {
            // Check if the cell wasn't already selected
            if (selectState)
            {
                // Flag the data field represented by these coordinates for removal
                selectedCells.add(row, column);
            }
            // The cell was already selected
            else
            {
                // Remove the data field represented by these coordinates from the list
                selectedCells.remove(row, column);
            }

            // Force the table to redraw so that the selection state is displayed correctly
            table.repaint();
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CellSelectEdit";
        }
    }

    /**********************************************************************************************
     * Undoable tree node selection class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTreePathSelection extends JComponent
    {
        private TreePath[] oldPaths = null;

        /******************************************************************************************
         * Undoable tree node selection class constructor
         *****************************************************************************************/
        UndoableTreePathSelection()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Add the tree path selection edit to the undo/redo stack
         *
         * @param paths
         *            array of selected tree node paths
         *****************************************************************************************/
        protected void selectTreePath(TreePath[] paths)
        {
            // Check if undoing is enabled
            if (isAllowUndo)
            {
                // Update the undo manager with this event. Get the listeners for this event
                UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                // Check if there is an edit listener registered
                if (listeners != null)
                {
                    // Create the edit event to be passed to the listeners
                    UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                        new TreePathSelectEdit(oldPaths,
                                                                                               paths));

                    // Step through the registered listeners
                    for (UndoableEditListener listener : listeners)
                    {
                        // Inform the listener that an update occurred
                        listener.undoableEditHappened(editEvent);
                    }
                }

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            // Create a copy of the path selection for use in the next path selection edit
            oldPaths = Arrays.copyOf(paths, paths.length);
        }
    }

    /**********************************************************************************************
     * Tree path selection edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class TreePathSelectEdit extends AbstractUndoableEdit
    {
        private final TreePath[] oldPaths;
        private final TreePath[] newPaths;

        /******************************************************************************************
         * Tree path selection edit event handler class constructor
         *
         * @param oldPaths
         *            array of previous tree path(s) selected
         *
         * @param newPaths
         *            array of new tree path(s) selected
         *****************************************************************************************/
        TreePathSelectEdit(TreePath[] oldPaths, TreePath[] newPaths)
        {
            this.oldPaths = oldPaths;
            this.newPaths = newPaths;

            // Add the path selection edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Undo the tree path selection
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            // Set the flag so that changes to the tree caused by the undo operation aren't placed
            // on the edit stack
            isAllowUndo = false;

            super.undo();

            // Select the old paths
            changeTreePathSelection(oldPaths);

            // Reset the flag so that changes to the tree are handled by the undo/redo manager
            isAllowUndo = true;
        }

        /******************************************************************************************
         * Redo the tree path selection
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            // Set the flag so that changes to the tree caused by the redo operation aren't placed
            // on the edit stack
            isAllowUndo = false;

            super.redo();

            // Select the new paths
            changeTreePathSelection(newPaths);

            // Reset the flag so that changes to the tree are handled by the undo/redo manager
            isAllowUndo = true;
        }

        /******************************************************************************************
         * Change the selected tree nodes to the ones provided
         *
         * @param paths
         *            array of tree node paths to select
         *****************************************************************************************/
        private void changeTreePathSelection(TreePath[] paths)
        {
            // Check if a node is to be selected
            if (paths != null)
            {
                // Set the node selection in the tree to the selected path(s)
                tree.setSelectionPaths(paths);
            }
            // No path was selected after the current one
            else
            {
                // Deselect all nodes in the tree
                tree.clearSelection();
            }
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TreePathSelectEdit";
        }
    }

    /**********************************************************************************************
     * Tree model undo/redo class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTreeModel extends DefaultTreeModel
    {
        /******************************************************************************************
         * Tree model undo/redo class constructor. Creates a tree model in which any node can have
         * children
         *
         * @param root
         *            tree root node
         *****************************************************************************************/
        UndoableTreeModel(TreeNode root)
        {
            super(root);

            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Tree model undo/redo class constructor. Creates a tree model specifying whether any node
         * can have children, or whether only certain nodes can have children
         *
         * @param root
         *            tree root node
         *
         * @param asksAllowsChildren
         *            false if any node can have children, true if each node is asked to see if it
         *            can have children
         *****************************************************************************************/
        UndoableTreeModel(TreeNode root, boolean asksAllowsChildren)
        {
            super(root, asksAllowsChildren);

            // Register the undo manager as an edit listener for this class
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Add the specified child node to the specified parent node at the given position
         *
         * @param child
         *            node to add
         *
         * @param parent
         *            node to which to add the new node
         *
         * @param index
         *            position in the existing node at which to place the new node
         *****************************************************************************************/
        @Override
        public void insertNodeInto(MutableTreeNode child, MutableTreeNode parent, int index)
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

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }

        /******************************************************************************************
         * Change the user object value for the specified tree path
         *
         * @param path
         *            tree path for which to change the user object value
         *
         * @param newValue
         *            new value for the user object
         *****************************************************************************************/
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

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            super.valueForPathChanged(path, newValue);
        }

        /******************************************************************************************
         * Remove the specified node from its parent node
         *
         * @param child
         *            node to remove
         *****************************************************************************************/
        @Override
        public void removeNodeFromParent(MutableTreeNode child)
        {
            // First, get a reference to the child's index and parent
            MutableTreeNode parent = (MutableTreeNode) child.getParent();
            int index = (parent != null) ? parent.getIndex(child) : -1;

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

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }

            super.removeNodeFromParent(child);
        }

        /******************************************************************************************
         * Placeholder for performing any actions needed following an undo or redo operation that
         * adds or removes a node
         *****************************************************************************************/
        protected void nodeAddRemoveCleanup()
        {
        }

        /******************************************************************************************
         * Placeholder for performing any actions needed following an undo or redo operation that
         * affects a node's user object (name)
         *
         * @param wasValue
         *            node user object value prior to the undo/redo operation
         *
         * @param isValue
         *            node user object value after the undo/redo operation
         *****************************************************************************************/
        protected void nodeRenameCleanup(Object wasValue, Object isValue)
        {
        }

        /******************************************************************************************
         * Tree node add edit event handler class
         *****************************************************************************************/
        private class UndoableNodeAddEdit extends AbstractUndoableEdit
        {
            private final MutableTreeNode child;
            private final MutableTreeNode parent;
            private final int index;
            private String expState;

            /**************************************************************************************
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
             *************************************************************************************/
            UndoableNodeAddEdit(MutableTreeNode parent, MutableTreeNode child, int index)
            {
                this.child = child;
                this.parent = parent;
                this.index = index;

                // Check if the tree reference is set
                if (tree != null)
                {
                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Since the node restoration involves a GUI update use invokeLater to
                         * execute the call after other pending events. This is necessary for the
                         * tree to appear without blank lines between some nodes
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Store the tree's expansion state; this is used to restore the
                            // expansion state of the added nodes in the event of a redo operation
                            expState = tree.getExpansionState();
                        }
                    });
                }

                // Add the tree node add edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /**************************************************************************************
             * Remove the child node from the parent node
             *************************************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                // Set the flag so that changes to the tree caused by the undo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.undo();

                // Remove the child from the parent
                parent.remove(index);

                // Perform any clean-up actions required due to adding or removing a node
                nodeAddRemoveCleanup();

                // Notify any listeners that the node was re-added
                nodesWereRemoved(parent, new int[] {index}, new Object[] {child});

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;
            }

            /**************************************************************************************
             * Add the child node back to the parent node
             *************************************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                // Set the flag so that changes to the tree caused by the redo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.redo();

                // Add the child to the parent
                parent.insert(child, index);

                // Perform any clean-up actions required due to adding or removing a node
                nodeAddRemoveCleanup();

                // Check if the tree's expansion state was stored and that the state has changed
                if (expState != null && !expState.equals(tree.getExpansionState()))
                {
                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Since the node restoration involves a GUI update use invokeLater to
                         * execute the call after other pending events. This is necessary for the
                         * tree to appear without blank lines between some nodes
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Restore the tree's expansion state to that after the node was added
                            tree.setExpansionState(expState);
                        }
                    });
                }

                // Notify any listeners that the node was re-added
                nodesWereInserted(parent, new int[] {index});

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;
            }

            /**************************************************************************************
             * Get the name of the edit type
             *
             * @return Name of the edit type
             *************************************************************************************/
            @Override
            public String getPresentationName()
            {
                return "TreeNodeAdd";
            }
        }

        /******************************************************************************************
         * Tree node change edit event handler class
         *****************************************************************************************/
        private class UndoableNodeChangeEdit extends AbstractUndoableEdit
        {
            private final TreePath path;
            private final Object oldValue;
            private final Object newValue;

            /**************************************************************************************
             * Tree node change edit event handler constructor
             *
             * @param path
             *            tree path
             *
             * @param oldValue
             *            original node user object
             *
             * @param newValue
             *            new node user object
             *************************************************************************************/
            UndoableNodeChangeEdit(TreePath path, Object oldValue, Object newValue)
            {
                this.path = path;
                this.oldValue = oldValue;
                this.newValue = newValue;

                // Add the check box edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /**************************************************************************************
             * Restore the node's previous user object value
             *************************************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                // Set the flag so that changes to the tree caused by the undo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.undo();

                // Set the node's user object to the previous value
                valueForPathChanged(path, oldValue);

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;

                // Perform any clean-up actions required due to changing the node's user object
                // (name)
                nodeRenameCleanup(newValue, oldValue);
            }

            /**************************************************************************************
             * Set the node's user object value to the new value
             *************************************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                // Set the flag so that changes to the tree caused by the redo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.redo();

                // Set the node's user object to the the new value
                valueForPathChanged(path, newValue);

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;

                // Perform any clean-up actions required due to changing the node's user object
                // (name)
                nodeRenameCleanup(oldValue, newValue);
            }

            /**************************************************************************************
             * Get the name of the edit type
             *
             * @return Name of the edit type
             *************************************************************************************/
            @Override
            public String getPresentationName()
            {
                return "TreeNodeChange";
            }
        }

        /******************************************************************************************
         * Tree node remove edit event handler class
         *****************************************************************************************/
        private class UndoableNodeRemoveEdit extends AbstractUndoableEdit
        {
            private final MutableTreeNode parent;
            private final MutableTreeNode child;
            private final int index;
            private String expState;

            /**************************************************************************************
             * Tree node remove edit event handler constructor
             *
             * @param parent
             *            parent tree node
             *
             * @param child
             *            child tree node
             *
             * @param index
             *            child index
             *************************************************************************************/
            UndoableNodeRemoveEdit(MutableTreeNode parent, final MutableTreeNode child, int index)
            {
                this.parent = parent;
                this.child = child;
                this.index = index;

                // Check if the tree reference is set
                if (tree != null)
                {
                    // Store the tree's expansion state; this is used to restore the expansion
                    // state of the added nodes in the event of a redo operation
                    expState = tree.getExpansionState();
                }

                // Add the check box edit to the undo stack
                undoManager.addEditSequence(this);
            }

            /**************************************************************************************
             * Add the child node to the parent node
             *************************************************************************************/
            @Override
            public void undo() throws CannotUndoException
            {
                // Set the flag so that changes to the tree caused by the undo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.undo();

                // Insert the child back into the parent
                parent.insert(child, index);

                // Perform any clean-up actions required due to adding or removing a node
                nodeAddRemoveCleanup();

                // Check if the tree's expansion state was stored and that the state has changed
                if (expState != null && !expState.equals(tree.getExpansionState()))
                {
                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Since the node restoration involves a GUI update use invokeLater to
                         * execute the call after other pending events. This is necessary for the
                         * tree to appear without blank lines between some nodes
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Restore the tree's expansion state to that before the node was
                            // removed
                            tree.setExpansionState(expState);
                        }
                    });
                }

                // Notify all listeners that the node was re-added
                nodesWereInserted(parent, new int[] {index});

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;
            }

            /**************************************************************************************
             * Remove the child node from the parent node
             *************************************************************************************/
            @Override
            public void redo() throws CannotRedoException
            {
                // Set the flag so that changes to the tree caused by the redo operation aren't
                // placed on the edit stack
                isAllowUndo = false;

                super.redo();

                // Remove the child from the parent
                parent.remove(index);

                // Perform any clean-up actions required due to adding or removing a node
                nodeAddRemoveCleanup();

                // Notify all listeners that the child was removed
                nodesWereRemoved(parent, new int[] {index}, new Object[] {child});

                // Reset the flag so that changes to the tree are handled by the undo/redo manager
                isAllowUndo = true;
            }

            /**************************************************************************************
             * Get the name of the edit type
             *
             * @return Name of the edit type
             *************************************************************************************/
            @Override
            public String getPresentationName()
            {
                return "TreeNodeRemove";
            }
        }
    }

    /**********************************************************************************************
     * Data field panel undo/redo class. This handles 'structural' edits to the data field(s)
     * (e.g., field addition or deletion, or changes to the field's name, input type, size, etc.,
     * as opposed to the field's value)
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableDataFieldPanel extends JComponent
    {
        private List<FieldInformation> oldFieldInfo;

        /******************************************************************************************
         * Data field panel undo/redo class constructor
         *****************************************************************************************/
        UndoableDataFieldPanel()
        {
            // Register the undo manager as an edit listener
            listenerList.add(UndoableEditListener.class, undoManager);
        }

        /******************************************************************************************
         * Set the current state of the data field information. This is the state the data fields
         * revert to in the event of an undo operation on a data field edit (not including edits of
         * the field's value)
         *
         * @param oldFieldInfo
         *            list containing the current data field information
         *****************************************************************************************/
        protected void setUndoFieldInformation(List<FieldInformation> oldFieldInfo)
        {
            this.oldFieldInfo = oldFieldInfo;
        }

        /******************************************************************************************
         * Add an edit of the data field panel to the undo/redo stack
         *
         * @param fieldPnlHandler
         *            reference to the data field panel handler in which the edit occurred
         *
         * @param newFieldInfo
         *            updated data field information list
         *****************************************************************************************/
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

                // Check if the flag is set that allows automatically ending the edit sequence
                if (isAutoEndEditSequence)
                {
                    // End the editing sequence
                    undoManager.endEditSequence();
                }
            }
        }
    }

    /**********************************************************************************************
     * Data field panel edit event handler class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private class DataFieldPanelEdit extends AbstractUndoableEdit
    {
        private final CcddInputFieldPanelHandler fieldPnlHandler;
        private final List<FieldInformation> oldFieldInfo;
        private final List<FieldInformation> newFieldInfo;

        /******************************************************************************************
         * Data field panel edit event handler constructor
         *
         * @param fieldPnlHandler
         *            reference to the data field panel handler in which the edit occurred
         *
         * @param oldFieldInfo
         *            list containing the original state of the data field information
         *
         * @param newFieldInfo
         *            list containing the new state of the data field information
         *****************************************************************************************/
        DataFieldPanelEdit(CcddInputFieldPanelHandler fieldPnlHandler,
                           List<FieldInformation> oldFieldInfo,
                           List<FieldInformation> newFieldInfo)
        {
            this.fieldPnlHandler = fieldPnlHandler;
            this.oldFieldInfo = oldFieldInfo;
            this.newFieldInfo = newFieldInfo;

            // Add the text field edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /******************************************************************************************
         * Rebuild the data field panel with the original data field information
         *****************************************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Rebuild the data fields using the original data field information list. Do not store
            // this edit operation on the undo/redo stack
            fieldPnlHandler.createDataFieldPanel(false, oldFieldInfo);
        }

        /******************************************************************************************
         * Rebuild the data field panel with the updated data field information
         *****************************************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Rebuild the data fields using the updated data field information list. Do not store
            // this edit operation on the undo/redo stack
            fieldPnlHandler.createDataFieldPanel(false, newFieldInfo);
        }

        /******************************************************************************************
         * Get the name of the edit type
         *
         * @return Name of the edit type
         *****************************************************************************************/
        @Override
        public String getPresentationName()
        {
            return "DataFieldPanel";
        }
    }
}
