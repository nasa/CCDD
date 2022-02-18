/**************************************************************************************************
/** \file CcddSchedulerDialogInterface.java
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Class that defines the interface for the application and telemetry scheduler dialog classes.
*
*   \copyright
*     MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
*
*     Copyright (c) 2016-2021 United States Government as represented by the
*     Administrator of the National Aeronautics and Space Administration.  All Rights Reserved.
*
*     This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
*     distributed and modified only pursuant to the terms of that agreement.  See the License for
*     the specific language governing permissions and limitations under the
*     License at https://software.nasa.gov/.
*
*     Unless required by applicable law or agreed to in writing, software distributed under the
*     License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
*     either expressed or implied.
*
*   \par Limitations, Assumptions, External Events and Notes:
*     - TBD
*
**************************************************************************************************/
package CCDD;

/**************************************************************************************************
 * CFS Command and Data Dictionary scheduler dialog interface
 *************************************************************************************************/
public interface CcddSchedulerDialogInterface {
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
     * @param value value to pass to the scheduler input
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
     * Steps to perform following storing of the scheduler data in the project
     * database
     *
     * @param errorFlag true if an error occurred updating the project database
     *********************************************************************************************/
    abstract void doSchedulerUpdatesComplete(boolean errorFlag);
}
