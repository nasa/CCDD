/**************************************************************************************************
 * /** \file CcddApplicationParameterHandler.java
 *
 * \author Kevin Mccluney Bryan Willis
 *
 * \brief Class that handles retrieval from and storage to the project database of the application
 * scheduling parameter values.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
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
     * @param ccddMain Main class
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
            ccddMain.getSessionEventLog()
                    .logFailEvent(ccddMain.getMainFrame(),
                                  "Invalid application parameter(s): using default values instead; cause '"
                                  + e.getMessage() + "'",
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
     * @param maxMsgsPerSec      Maximum number of messages that can be downlinked in one second
     *
     * @param maxMsgsPerCycle    Maximum number of messages that can be downlinked before repeating
     *                           the message list
     *
     * @param maxMsgsPerTimeSlot Maximum number of messages per time slot
     *
     * @param numberOfTimeSlots  Number of time slots in the scheduler definition table
     *
     * @param parent             Component calling this method, used for positioning any error
     *                           dialogs
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
                                maxMsgsPerTimeSlot + "," + maxMsgsPerSec + "," + maxMsgsPerCycle + "," + numberOfTimeSlots,
                                parent);
    }
}
