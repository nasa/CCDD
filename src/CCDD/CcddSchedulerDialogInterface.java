/**
 * CFS Command & Data Dictionary scheduler dialog interface. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

/******************************************************************************
 * CFS Command & Data Dictionary scheduler dialog interface
 *****************************************************************************/
public interface CcddSchedulerDialogInterface
{
    /**************************************************************************
     * Get the scheduler dialog
     * 
     * @return Scheduler dialog
     *************************************************************************/
    abstract CcddDialogHandler getDialog();

    /**************************************************************************
     * Get the scheduler database handler
     * 
     * @return Schedule database handler
     *************************************************************************/
    abstract CcddSchedulerDbIOHandler getSchedulerDatabaseHandler();

    /**************************************************************************
     * Get the scheduler validator
     * 
     * @return Schedule validator
     *************************************************************************/
    abstract CcddSchedulerValidator getSchedulerValidator();

    /**************************************************************************
     * Get the scheduler input object
     * 
     * @param value
     *            value to pass to the scheduler input
     * 
     * @return Scheduler input object
     *************************************************************************/
    abstract CcddSchedulerInputInterface createSchedulerInput(String value);

    /**************************************************************************
     * Get the scheduler handler
     * 
     * @return Scheduler handler
     *************************************************************************/
    abstract CcddSchedulerHandler getSchedulerHandler();
}