/**
 * CFS Command and Data Dictionary application parameter handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;

import CCDD.CcddConstants.ApplicationParameter;
import CCDD.CcddConstants.InternalTable;

/**************************************************************************************************
 * CFS Command and Data Dictionary application parameter handler class
 *************************************************************************************************/
public class CcddApplicationParameterHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;

    // Application parameters
    private int maxMsgsPerTimeSlot;
    private int maxMsgsPerSec;
    private int maxMsgsPerCycle;
    private int numberOfTimeSlots;

    /**********************************************************************************************
     * Application parameter handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddApplicationParameterHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();

        // Get the application parameters from the project database
        generateApplicationParameters();
    }

    /**********************************************************************************************
     * Get the number of messages per time slot
     *
     * @return Number of messages per time slot
     *********************************************************************************************/
    protected int getNumberOfMessagesPerTimeSlot()
    {
        return maxMsgsPerTimeSlot;
    }

    /**********************************************************************************************
     * Get the maximum number of messages that can be downlinked in one second
     *
     * @return Maximum number of messages that can be downlinked in one second
     *********************************************************************************************/
    protected int getMaxMsgsPerSecond()
    {
        return maxMsgsPerSec;
    }

    /**********************************************************************************************
     * Get the maximum number of messages that can be downlinked before repeating the message list
     *
     * @return Maximum number of messages that can be downlinked before repeating the message list
     *********************************************************************************************/
    protected int getMsgsPerCycle()
    {
        return maxMsgsPerCycle;
    }

    /**********************************************************************************************
     * Get the total number of time slots in the scheduler definition table
     *
     * @return Number of time slots in the scheduler definition table
     *********************************************************************************************/
    protected int getNumberOfTimeSlots()
    {
        return numberOfTimeSlots;
    }

    /**********************************************************************************************
     * Get the application parameters from the database
     *********************************************************************************************/
    protected void generateApplicationParameters()
    {
        // Get the application parameters from the database
        String[] appValues = dbTable.queryTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                                       0,
                                                       ccddMain.getMainFrame());

        try
        {
            // Convert the application parameters to integers
            maxMsgsPerTimeSlot = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_MESSAGES_PER_TIME_SLOT.ordinal()]);
            maxMsgsPerSec = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_MESSAGES_PER_SECOND.ordinal()]);
            maxMsgsPerCycle = Integer.valueOf(appValues[ApplicationParameter.MAXIMUM_MESSAGES_PER_CYCLE.ordinal()]);
            numberOfTimeSlots = Integer.valueOf(appValues[ApplicationParameter.NUMBER_OF_TIME_SLOTS.ordinal()]);

            // Check if any of the values are less than 1
            if (maxMsgsPerTimeSlot <= 0 || maxMsgsPerSec <= 0 || maxMsgsPerCycle <= 0 || numberOfTimeSlots <= 0)
            {
                throw new Exception("zero or negative application value");
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
            maxMsgsPerTimeSlot = 1;
            maxMsgsPerSec = 10;
            maxMsgsPerCycle = 10;
            numberOfTimeSlots = 128;
        }
    }

    /**********************************************************************************************
     * Set the application parameters in the database
     *
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one second
     *
     * @param maxMsgsPerCycle
     *            maximum number of messages that can be downlinked before repeating the message
     *            list
     *
     * @param maxMsgsPerTimeSlot
     *            maximum number of messages per time slot
     *
     * @param numberOfTimeSlots
     *            number of time slots in the scheduler definition table
     *
     * @param parent
     *            component calling this method, used for positioning any error dialogs
     *********************************************************************************************/
    protected void setApplicationParameters(int maxMsgsPerSec,
                                            int maxMsgsPerCycle,
                                            int maxMsgsPerTimeSlot,
                                            int numberOfTimeSlots,
                                            Component parent)
    {
        this.maxMsgsPerSec = maxMsgsPerSec;
        this.maxMsgsPerCycle = maxMsgsPerCycle;
        this.maxMsgsPerTimeSlot = maxMsgsPerTimeSlot;
        this.numberOfTimeSlots = numberOfTimeSlots;

        // Update the the stored application parameters
        dbTable.setTableComment(InternalTable.APP_SCHEDULER.getTableName(),
                                maxMsgsPerTimeSlot
                                                                            + ","
                                                                            + maxMsgsPerSec
                                                                            + ","
                                                                            + maxMsgsPerCycle
                                                                            + ","
                                                                            + numberOfTimeSlots,
                                parent);
    }
}
