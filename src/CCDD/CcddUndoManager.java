/**
 * CFS Command and Data Dictionary undo/redo edits manager.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

/**************************************************************************************************
 * CFS Command and Data Dictionary undo/redo edits manager class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddUndoManager extends UndoManager
{
    // Compound edit action sequence list
    private final ArrayList<CompoundEdit> compoundEdits;

    // Edit sequence stack pointer
    private int pointer;

    /**********************************************************************************************
     * Undo/redo edits manager class constructor
     *********************************************************************************************/
    CcddUndoManager()
    {
        super();

        // Set an unlimited undo/redo stack size
        setLimit(-1);

        // Create storage for the compound edit actions
        compoundEdits = new ArrayList<CompoundEdit>();

        // Initialize the edit sequence stack pointer
        pointer = -1;
    }

    /**********************************************************************************************
     * Add an edit action to the compound edit sequence in progress. If no sequence is active then
     * first create a new sequence
     *
     * @param editAction
     *            edit action
     *********************************************************************************************/
    protected void addEditSequence(AbstractUndoableEdit editAction)
    {
        // Check if no compound edit sequence is active
        if (pointer == -1 || !compoundEdits.get(pointer).isInProgress())
        {
            // Step through the edit actions beyond this sequence
            while (compoundEdits.size() - 1 > pointer)
            {
                // Remove the edit action
                compoundEdits.remove(compoundEdits.size() - 1);
            }

            // Initiate a new compound edit sequence and adjust the stack pointer
            compoundEdits.add(new CompoundEdit());
            pointer++;
        }

        // Add the edit action to the sequence
        compoundEdits.get(pointer).addEdit(editAction);

        // Send event indicating the owner has changed
        ownerHasChanged();
    }

    /**********************************************************************************************
     * End the current compound edit sequence
     *********************************************************************************************/
    protected void endEditSequence()
    {
        // Check that a sequence exists
        if (pointer != -1)
        {
            // End the sequence
            compoundEdits.get(pointer).end();
        }
    }

    /**********************************************************************************************
     * Override the canUndo method to check if the compound edit stack has an undo available
     *********************************************************************************************/
    @Override
    public boolean canUndo()
    {
        return pointer >= 0;
    }

    /**********************************************************************************************
     * Override the canRedo method to check if the compound edit stack has a redo available
     *********************************************************************************************/
    @Override
    public boolean canRedo()
    {
        return pointer < compoundEdits.size() - 1;
    }

    /**********************************************************************************************
     * Undo the last compound edit sequence and remove the action from the stack so that it can't
     * be redone
     *********************************************************************************************/
    protected void undoRemoveEdit()
    {
        // Check if an undo is allowed
        if (canUndo())
        {
            // Undo the last action
            undo();

            // Remove the edit action from the stack
            compoundEdits.remove(pointer + 1);
        }
    }

    /**********************************************************************************************
     * Override the undo method so that a check can be first performed that edits are available to
     * undo
     *********************************************************************************************/
    @Override
    public void undo()
    {
        // End any ongoing compound edit sequence
        endEditSequence();

        // Check if an undo is allowed
        if (canUndo())
        {
            // Undo all actions within this compound edit sequence and adjust the stack pointer
            compoundEdits.get(pointer).undo();
            pointer--;

            // Send event indicating the owner has changed
            ownerHasChanged();
        }
    }

    /**********************************************************************************************
     * Override the redo method so that a check can be first performed that edits are available to
     * redo
     *********************************************************************************************/
    @Override
    public void redo()
    {
        // End any ongoing compound edit
        endEditSequence();

        // Check if an redo is allowed
        if (canRedo())
        {
            // Adjust the stack pointer and redo all actions within this compound edit sequence
            pointer++;
            compoundEdits.get(pointer).redo();

            // Send event indicating the owner has changed
            ownerHasChanged();
        }
    }

    /**********************************************************************************************
     * Override the discard edits method so that the stack pointer can be reset
     *********************************************************************************************/
    @Override
    public void discardAllEdits()
    {
        super.discardAllEdits();

        // Check if there are any edits to discard
        if (pointer != -1)
        {
            // Reset the stack pointer and clear the edit list
            pointer = -1;
            compoundEdits.clear();

            // Send event indicating the owner has changed
            ownerHasChanged();
        }
    }

    /**********************************************************************************************
     * Placeholder for method to flag changes to the undo manager owner
     *********************************************************************************************/
    protected void ownerHasChanged()
    {
    }
}