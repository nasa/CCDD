/**
 * CFS Command & Data Dictionary application parameter handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;

import CCDD.CcddConstants.ApplicationParameter;
import CCDD.CcddConstants.InternalTable;

/******************************************************************************
 * CFS Command & Data Dictionary application parameter handler class
 *****************************************************************************/
public class CcddApplicationParameterHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;

    // Application parameters
    private int slotsPerMessage;
    private int maxMsgsPerSec;
    private int maxMsgsPerCycle;
    private int commandsPerTable;

    /**************************************************************************
     * Application parameter handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddApplicationParameterHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();

        // Get the application parameters from the project database
        generateApplicationParameters();
    }

    /**************************************************************************
     * Get the total number of slots for a given time slot
     * 
     * @return Maximum number of slots for a time slot
     *************************************************************************/
    protected int getNumberOfSlots()
    {
        return slotsPerMessage;
    }

    /**************************************************************************
     * Get the maximum number of messages that can be downlinked in one second
     * 
     * @return Maximum number of messages that can be downlinked in one second
     *************************************************************************/
    protected int getMaxMsgsPerSecond()
    {
        return maxMsgsPerSec;
    }

    /**************************************************************************
     * Get the maximum number of messages that can be downlinked before
     * repeating the message list
     * 
     * @return Maximum number of messages that can be downlinked before
     *         repeating the message list
     *************************************************************************/
    protected int getMsgsPerCycle()
    {
        return maxMsgsPerCycle;
    }

    /**************************************************************************
     * Get the total number of commands for a given scheduler table
     * 
     * @return Number of commands for a scheduler table
     *************************************************************************/
    protected int getCommandsPerTable()
    {
        return commandsPerTable;
    }

    /**************************************************************************
     * Get the application parameters from the database
     *************************************************************************/
    protected void generateApplicationParameters()
    {
        // Get the application parameters from the database
        String[] appValues = dbTable.queryTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                                       ccddMain.getMainFrame());

        try
        {
            // Convert the application parameters to integers
            slotsPerMessage = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_NUMBER_OF_SLOTS.ordinal()]);
            maxMsgsPerSec = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_MESSAGES_PER_SECOND.ordinal()]);
            maxMsgsPerCycle = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_MESSAGES_PER_CYCLE.ordinal()]);
            commandsPerTable = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_NUMBEROF_COMMANDS.ordinal()]);

            // Check if any of the values are less than 1
            if (slotsPerMessage <= 0 || maxMsgsPerSec <= 0 || maxMsgsPerCycle <= 0 || commandsPerTable <= 0)
            {
                throw new Exception("zero or negative rate value");
            }
        }
        catch (Exception e)
        {
            // Inform the user that the application parameters are invalid
            ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                       "Invalid application parameter(s): using default values instead; cause '"
                                                           + e.getMessage()
                                                           + "'",
                                                       "<html><b>Invalid application parameter(s): using default values instead");

            // Use default values
            slotsPerMessage = 1;
            maxMsgsPerSec = 10;
            maxMsgsPerCycle = 10;
            commandsPerTable = 128;
        }
    }

    /**************************************************************************
     * Set the application parameters in the database
     * 
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one
     *            second
     * 
     * @param maxMsgsPerCycle
     *            maximum number of messages that can be downlinked before
     *            repeating the message list
     * 
     * @param slotsPerMessage
     *            maximum number of slots for a given time slot
     * 
     * @param maxCommands
     *            maximum number of commands for scheduler table
     * 
     * @param parent
     *            component calling this method, used for positioning any error
     *            dialogs
     *************************************************************************/
    protected void setApplicationParameters(int maxMsgsPerSec,
                                            int maxMsgsPerCycle,
                                            int slotsPerMessage,
                                            int maxCommands,
                                            Component parent)
    {
        this.maxMsgsPerSec = maxMsgsPerSec;
        this.maxMsgsPerCycle = maxMsgsPerCycle;
        this.slotsPerMessage = slotsPerMessage;
        this.commandsPerTable = maxCommands;

        // Update the the stored application parameters
        dbTable.setTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                slotsPerMessage
                                    + ","
                                    + maxMsgsPerSec
                                    + ","
                                    + maxMsgsPerCycle
                                    + ","
                                    + commandsPerTable,
                                parent);
    }
}
