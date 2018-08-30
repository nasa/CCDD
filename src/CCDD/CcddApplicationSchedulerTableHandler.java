/**
 * CFS Command and Data Dictionary application scheduler table handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import CCDD.CcddClassesDataTable.ApplicationData;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.SchedulerType;

/**************************************************************************************************
 * CFS Command and Data Dictionary application scheduler table handler class
 *************************************************************************************************/
public class CcddApplicationSchedulerTableHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddSchedulerDbIOHandler schedulerDB;
    private final CcddApplicationParameterHandler appHandler;

    private List<String[][]> sdtEntries;
    private final List<Variable> appList;
    private String[][] defines;
    private List<Variable> applications;
    private List<Message> timeSlots;

    // Constants
    private final String ENABLE = "SCH_ENABLED";
    private final String ACTIVITY = "SCH_ACTIVITY_SEND_MSG";
    private final String UNUSED = "SCH_UNUSED";
    private final String GROUPNONE = "SCH_GROUP_NONE";

    /**********************************************************************************************
     * Application scheduler table handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddApplicationSchedulerTableHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        schedulerDB = new CcddSchedulerDbIOHandler(ccddMain,
                                                   SchedulerType.APPLICATION_SCHEDULER,
                                                   null);
        appHandler = ccddMain.getApplicationParameterHandler();

        // Load the application scheduler information from the project database
        schedulerDB.loadStoredData();

        appList = schedulerDB.getVariableList(0);
        createSchedulerTableDefines();
        createApplicationScheduleDefinitionTable();
    }

    /**********************************************************************************************
     * Create the schedule definition table entries based on the time slot definitions
     *********************************************************************************************/
    private void createApplicationScheduleDefinitionTable()
    {
        sdtEntries = new ArrayList<String[][]>();
        int numMsgsPerSlot = appHandler.getNumberOfMessagesPerTimeSlot();
        String[][] sdtEntry;

        // Step through each message
        for (Message message : getValidatedStoredData())
        {
            sdtEntry = new String[numMsgsPerSlot][6];

            // Step through each message in the time slot
            for (int msgIndex = 0; msgIndex < numMsgsPerSlot; msgIndex++)
            {
                // Sort the messages based on their assigned priority values
                prioritizeApps(message);

                // Check if a message is assigned to this message/time slot
                if (message.getNumberOfVariables() > msgIndex)
                {
                    // Store the message entry
                    ApplicationData appData = (ApplicationData) message.getVariable(msgIndex);
                    sdtEntry[msgIndex][0] = ENABLE;
                    sdtEntry[msgIndex][1] = ACTIVITY;
                    sdtEntry[msgIndex][2] = "1";
                    sdtEntry[msgIndex][3] = "0";
                    sdtEntry[msgIndex][4] = getMessageIndex(Integer.decode(appData.getWakeUpID()));
                    sdtEntry[msgIndex][5] = appData.getSchGroup();
                }
                // No message is assigned
                else
                {
                    // Store an "unused" entry
                    sdtEntry[msgIndex][0] = UNUSED;
                    sdtEntry[msgIndex][1] = "0";
                    sdtEntry[msgIndex][2] = "0";
                    sdtEntry[msgIndex][3] = "0";
                    sdtEntry[msgIndex][4] = "0";
                    sdtEntry[msgIndex][5] = GROUPNONE;
                }
            }

            // Add the message to the list of schedule definition table entries
            sdtEntries.add(sdtEntry);
        }
    }

    /**********************************************************************************************
     * Sort the list of messages based on the assigned priorities
     *
     * @param msg
     *            message
     *********************************************************************************************/
    private void prioritizeApps(Message msg)
    {
        Collections.sort(msg.getVariables(), new Comparator<Variable>()
        {
            /**************************************************************************************
             * Compare the priorities for two messages
             *************************************************************************************/
            @Override
            public int compare(Variable var, Variable otherVar)
            {
                return ((ApplicationData) var).getPriority() > ((ApplicationData) var).getPriority()
                                                                                                     ? 1
                                                                                                     : 0;
            }
        });
    }

    /**********************************************************************************************
     * Get message index
     *
     * @param msgID
     *            wake-up ID
     *
     * @return Message index
     *********************************************************************************************/
    private String getMessageIndex(int msgID)
    {
        String index = "0";

        for (String[] message : defines)
        {
            if (Integer.valueOf(message[1]) == msgID)
            {
                index = message[0];
                break;
            }
        }

        return index;
    }

    /**********************************************************************************************
     * Build the application wake-up message ID define statement parameters
     *********************************************************************************************/
    protected void createSchedulerTableDefines()
    {
        List<Variable> appsSorted = new ArrayList<Variable>();

        // Copy the list of applications
        appsSorted.addAll(appList);

        // Sort the applications based on wake-up ID
        Collections.sort(appsSorted, new Comparator<Variable>()
        {
            /**************************************************************************************
             * Compare the wake-up IDs for two applications
             *************************************************************************************/
            @Override
            public int compare(Variable var, Variable otherVar)
            {
                return Integer.decode(((ApplicationData) var).getWakeUpID())
                              .compareTo(Integer.decode(((ApplicationData) otherVar).getWakeUpID()));
            }
        });

        defines = new String[appsSorted.size()][2];
        int index = 0;

        // Step through the sorted applications
        for (Variable app : appsSorted)
        {
            // Store the application wake-up message ID define statement parameters
            defines[index][0] = app.getFullName().toUpperCase() + "_WAKEUP_MID";
            defines[index][1] = Integer.decode(((ApplicationData) app).getWakeUpID()).toString();
            index++;
        }
    }

    /**********************************************************************************************
     * Get the application wake-up message ID define statement parameters
     *
     * @return Array containing the application wake-up message ID define statement parameters
     *********************************************************************************************/
    protected String[][] getScheduleDefinitionTableDefines()
    {
        return defines;
    }

    /**********************************************************************************************
     * Get the message definition table entries
     *
     * @return Array containing the message definition table entries
     *********************************************************************************************/
    protected String[] getMessageDefinitionTable()
    {
        String[] scheduleCommands = new String[appHandler.getNumberOfTimeSlots()];
        String command;

        for (int mdtIndex = 0; mdtIndex < scheduleCommands.length; mdtIndex++)
        {
            command = null;

            for (Variable var : appList)
            {
                if (mdtIndex == Integer.decode(((ApplicationData) var).getWakeUpID()))
                {
                    command = var.getFullName().toUpperCase() + "_WAKEUP_MID";
                    break;
                }
            }

            if (command == null)
            {
                command = "SCH_UNUSED_MID";
            }

            scheduleCommands[mdtIndex] = command;
        }

        return scheduleCommands;
    }

    /**********************************************************************************************
     * Get the specified entry in the schedule definition table
     *
     * @param row
     *            row index for the entry in the schedule definition table
     *
     * @return Array containing the specified entry in the schedule definition table
     *********************************************************************************************/
    protected String[][] getScheduleDefinitionTableByRow(int row)
    {
        return sdtEntries.get(row);
    }

    /**********************************************************************************************
     * Get the number of time slots in the schedule definition table
     *
     * @return Number of time slots in the schedule definition table
     *********************************************************************************************/
    protected int getNumberOfTimeSlots()
    {
        return sdtEntries.size();
    }

    /**********************************************************************************************
     * Get the time slots and validate the time slot data. Invalid time slot entries are removed
     * from the list
     *
     * @return List of valid time slots
     *********************************************************************************************/
    private List<Message> getValidatedStoredData()
    {
        List<Message> messages = schedulerDB.getStoredData(0);
        validateTableData(appList, messages);
        return messages;
    }

    /**********************************************************************************************
     * Validate the application scheduler table and remove from the list of messages any
     * application that is invalid
     *
     * @param applications
     *            list of applications in the time slots
     *
     * @param timeSlots
     *            list of time slots in the table
     *********************************************************************************************/
    private void validateTableData(List<Variable> applications, List<Message> timeSlots)
    {
        this.applications = applications;
        this.timeSlots = timeSlots;

        int numInvalid = 0;
        String type = "application scheduler applications";

        // Validate the application group data and remove any invalid applications
        numInvalid += removeInvalidData(validateApplicationData());

        // Check if invalid entries were removed
        if (numInvalid != 0)
        {
            // Inform the user that invalid entries exist
            ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                       "Application Error",
                                                       "Invalid "
                                                                            + type
                                                                            + " detected; "
                                                                            + numInvalid
                                                                            + " removed",
                                                       "<html><b>Invalid "
                                                                                          + type
                                                                                          + " detected; "
                                                                                          + numInvalid
                                                                                          + " removed");
        }
    }

    /**********************************************************************************************
     * Check all the stored application information for inconsistencies. Update or flag the
     * application for removal if any changes are found
     *
     * @return List of invalid applications
     *********************************************************************************************/
    private List<Variable> validateApplicationData()
    {
        List<Variable> removedApps = new ArrayList<Variable>();

        // Create a group tree
        CcddGroupTreeHandler groupTree = new CcddGroupTreeHandler(ccddMain,
                                                                  null,
                                                                  ccddMain.getMainFrame());

        // Get a list of the current group's names
        String[] groupNames = groupTree.getGroupHandler().getGroupNames(true);

        // Get a reference to the data field handler to interact with the groups' fields
        CcddFieldHandler fieldHandler = ccddMain.getFieldHandler();

        // Step through each application
        for (Variable app : applications)
        {
            boolean isValid = false;

            // Step through the group names
            for (String name : groupNames)
            {
                // Check if the group's name matches the application's name
                if (app.getFullName().equals(name))
                {
                    // Get the group's information and set the data field handler to contain the
                    // current group's information
                    GroupInformation groupInfo = groupTree.getGroupHandler().getGroupInformationByName(name);

                    // Get the application data field owner name
                    String application = CcddFieldHandler.getFieldGroupName(groupInfo.getName());

                    // Get the application's schedule rate
                    FieldInformation appInfo = fieldHandler.getFieldInformationByName(application,
                                                                                      DefaultApplicationField.SCHEDULE_RATE.getFieldName());

                    // Check if the application's rate equals its field's rate
                    if (Float.valueOf(appInfo.getValue()) == app.getRate())
                    {
                        ApplicationData appData = (ApplicationData) app;

                        // Set the applications's validity to true
                        isValid = true;

                        // Get the run time field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.EXECUTION_TIME.getFieldName());

                        // Check if the application's run time changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) != app.getSize())
                        {
                            // Update the run time to what the field has set
                            app.setSize(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the execution priority field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.PRIORITY.getFieldName());

                        // Check if the application's priority changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) != appData.getPriority())
                        {
                            // Update the application's priority
                            appData.setPriority(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the message rate field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.MESSAGE_RATE.getFieldName());

                        // Check if the application's message rate changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) != appData.getMessageRate())
                        {
                            // Update the application's message rate
                            appData.setMessageRate(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the wake-up message name & ID field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.WAKE_UP_MESSAGE.getFieldName());

                        // Check if the application's wake-up message name & ID changed
                        if (appInfo != null
                            && !appInfo.getValue().equals(appData.getWakeUpMessage()))
                        {
                            // Update the application's wake-up message name & ID
                            appData.setWakeUpMessage(appInfo.getValue());
                        }

                        // Get the HK send rate field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.HK_SEND_RATE.getFieldName());

                        // Check if the application's HK send rate changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) != appData.getHkSendRate())
                        {
                            // Update the application's HK send rate
                            appData.setHkSendRate(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the HK wake-up message name & ID field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.HK_WAKE_UP_MESSAGE.getFieldName());

                        // Check if the application's HK wake-up message name & ID changed
                        if (appInfo != null
                            && !appInfo.getValue().equals(appData.getHkWakeUpMessage()))
                        {
                            // Update the application's HK wake-up message name & ID
                            appData.setHkWakeUpMessage(appInfo.getValue());
                        }

                        // Get the schedule group field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         DefaultApplicationField.SCH_GROUP.getFieldName());

                        // Check if the application's schedule group changed
                        if (appInfo != null
                            && !appInfo.getValue().equals(appData.getSchGroup()))
                        {
                            // Update the application's schedule group
                            appData.setSchGroup(appInfo.getValue());
                        }
                    }

                    break;
                }
            }

            // Check if the application is invalid
            if (!isValid)
            {
                // Add the application to the list of removed applications
                removedApps.add(app);
            }
        }

        return removedApps;
    }

    /**********************************************************************************************
     * Remove data that is determined to be invalid
     *
     * @param removedVars
     *            list of invalid data
     *
     * @return Number of invalid entries that were found and removed
     *********************************************************************************************/
    private int removeInvalidData(List<Variable> removedVars)
    {
        List<Integer> indices;

        // Remove the invalid variables from the variables list
        applications.removeAll(removedVars);

        // Step through the list of removed variables
        for (Variable var : removedVars)
        {
            // Get the indices of the messages to which the variable belongs
            indices = var.getMessageIndices();

            // Step through the messages in which the variable is contained
            for (int index : indices)
            {
                // Remove the variable from the general message
                timeSlots.get(index).removeVariable(var.getFullName());
            }
        }

        return removedVars.size();
    }
}
