/**
 * CFS Command and Data Dictionary scheduler dialog interface.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

/**************************************************************************************************
 * CFS Command and Data Dictionary scheduler dialog interface
 *************************************************************************************************/
public interface CcddSchedulerDialogInterface
{
    /**********************************************************************************************
     * Get the scheduler dialog
     *
     * @return Scheduler dialog
     *********************************************************************************************/
    abstract CcddDialogHandler getDialog();

    /**********************************************************************************************
     * Get the scheduler database handler
     *
     * @return Schedule database handler
     *********************************************************************************************/
    abstract CcddSchedulerDbIOHandler getSchedulerDatabaseHandler();

    /**********************************************************************************************
     * Get the scheduler input object
     *
     * @param value
     *            value to pass to the scheduler input
     *
     * @return Scheduler input object
     *********************************************************************************************/
    abstract CcddSchedulerInputInterface createSchedulerInput(String value);

    /**********************************************************************************************
     * Get the scheduler handler
     *
     * @return Scheduler handler
     *********************************************************************************************/
    abstract CcddSchedulerHandler getSchedulerHandler();

    /**********************************************************************************************
     * Update the change indicator for the scheduler dialog
     *********************************************************************************************/
    abstract void updateChangeIndicator();

    /**********************************************************************************************
     * Steps to perform following storing of the scheduler data in the project database
     *
     * @param errorFlag
     *            true if an error occurred updating the project database
     *********************************************************************************************/
    abstract void doSchedulerUpdatesComplete(boolean errorFlag);
}