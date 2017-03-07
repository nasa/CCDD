/**
 * CFS Command & Data Dictionary application scheduler table handler. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import CCDD.CcddClasses.ApplicationData;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.Message;
import CCDD.CcddClasses.Variable;
import CCDD.CcddConstants.SchedulerType;

/******************************************************************************
 * CFS Command & Data Dictionary scheduler table handler class
 *****************************************************************************/
public class CcddSchedulerTableHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddSchedulerDbIOHandler schedulerDB;

    private List<String[][]> entries;
    private List<Variable> appList;
    private String[][] defines;
    private List<String> schGroups;
    private List<Variable> applications;
    private List<Message> timeSlots;

    // Constants
    private final String ENABLE = "SCH_ENABLED";
    private final String ACTIVITY = "SCH_ACTIVITY_SEND_MSG";
    private final String UNUSED = "SCH_UNUSED";
    private final String GROUPNONE = "SCH_GROUP_NONE";

    /**************************************************************************
     * Scheduler table handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddSchedulerTableHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        schedulerDB = new CcddSchedulerDbIOHandler(ccddMain,
                                                   SchedulerType.APPLICATION_SCHEDULER,
                                                   null);

        // Generate the application parameters
        ccddMain.getApplicationParameterHandler().generateApplicationParameters();
        schGroupDefines();
    }

    /**************************************************************************
     * Create a scheduler table based on the time slot definitions
     *************************************************************************/
    protected void createApplicationSchedulerTable()
    {
        entries = new ArrayList<String[][]>();
        int slots = ccddMain.getApplicationParameterHandler().getNumberOfSlots();
        String[][] entry;

        for (Message message : getValidatedStoredData())
        {
            entry = new String[slots][6];

            for (int pos = 0; pos < slots; pos++)
            {
                prioritizeApps(message);

                if (message.getNumberOfVariables() > pos)
                {
                    entry[pos][0] = ENABLE;
                    entry[pos][1] = ACTIVITY;
                    entry[pos][2] = "1";
                    entry[pos][3] = "0";
                    entry[pos][4] = getMessageIndex(Integer.valueOf(((ApplicationData) message.getVariable(pos)).getWakeUpID().replace("0x", ""), 16));
                    entry[pos][5] = ((ApplicationData) message.getVariable(pos)).getSchGroup();
                }
                else
                {
                    entry[pos][0] = UNUSED;
                    entry[pos][1] = "0";
                    entry[pos][2] = "0";
                    entry[pos][3] = "0";
                    entry[pos][4] = "0";
                    entry[pos][5] = GROUPNONE;
                }
            }

            entries.add(entry);
        }
    }

    /**************************************************************************
     * 
     *************************************************************************/
    private void schGroupDefines()
    {
        schGroups = new ArrayList<String>();

        for (Variable app : appList)
        {
            if (!schGroups.contains(((ApplicationData) app).getSchGroup()))
            {
                schGroups.add(((ApplicationData) app).getSchGroup());
            }
        }
    }

    /**************************************************************************
     * 
     *************************************************************************/
    protected String[] getApplicationSchedulerGroups()
    {
        return schGroups.toArray(new String[schGroups.size()]);
    }

    /**************************************************************************
     * 
     *************************************************************************/
    private void prioritizeApps(Message msg)
    {
        Collections.sort(msg.getVariables(), new Comparator<Variable>()
        {
            @Override
            public int compare(Variable var, Variable otherVar)
            {
                return ((ApplicationData) var).getPriority() > ((ApplicationData) var).getPriority()
                                                                                                    ? 1
                                                                                                    : 0;
            }
        });
    }

    /**************************************************************************
     * 
     *************************************************************************/
    private String getMessageIndex(int x)
    {
        String index = "0";

        for (String[] message : defines)
        {
            if (Integer.valueOf(message[1]) == x)
            {
                index = message[0];
                break;
            }
        }

        return index;
    }

    /**************************************************************************
     * 
     *************************************************************************/
    protected String[][] getSchedulerTableDefines()
    {
        List<Variable> apps = new ArrayList<Variable>();

        apps.addAll(appList);

        Collections.sort(apps, new Comparator<Variable>()
        {
            @Override
            public int compare(Variable var, Variable otherVar)
            {
                return Integer.valueOf(((ApplicationData) var).getWakeUpID().replace("0x", ""), 16).compareTo(Integer.valueOf(((ApplicationData) otherVar).getWakeUpID().replace("0x", ""), 16));
            }
        });

        defines = new String[apps.size()][2];
        int index = 0;

        for (Variable app : apps)
        {
            defines[index][0] = app.getFullName().toUpperCase() + "_WAKEUP_MID";
            defines[index][1] = Integer.valueOf(((ApplicationData) app).getWakeUpID().replace("0x", ""), 16).toString();
            index++;
        }

        return defines;
    }

    /**************************************************************************
     * 
     *************************************************************************/
    protected String[] createSchedulerMessageTable()
    {
        List<Variable> apps = new ArrayList<Variable>();
        apps.addAll(appList);
        String[] scheduleCommands = new String[ccddMain.getApplicationParameterHandler().getCommandsPerTable()];
        String command;

        for (int x = 0; x < scheduleCommands.length; x++)
        {
            command = null;

            for (Variable var : apps)
            {
                if (x == Integer.valueOf(((ApplicationData) var).getWakeUpID().replace("0x", ""), 16))
                {
                    command = var.getFullName().toUpperCase() + "_WAKEUP_MID";
                    break;
                }
            }

            if (command == null)
            {
                command = "SCH_UNUSED_MID";
            }

            scheduleCommands[x] = command;
        }

        return scheduleCommands;
    }

    /**************************************************************************
     * 
     *************************************************************************/
    protected String[][] getApplicationScheduleTableIndex(int index)
    {
        return entries.get(index);
    }

    /**************************************************************************
     * Get time slots from the project database and validate the time slot
     * data. Invalid time slot entries are removed from the list
     * 
     * @return List of valid time slots
     *************************************************************************/
    private List<Message> getValidatedStoredData()
    {
        schedulerDB.loadStoredData();
        List<Message> messages = schedulerDB.getStoredData(0);
        appList = schedulerDB.getVariableList(0);
        validateTableData(appList, messages);
        return messages;
    }

    /**************************************************************************
     * Validate the application scheduler table and remove from the list of
     * messages any application that is invalid
     * 
     * @param applications
     *            list of applications in the time slots
     * 
     * @param timeSlots
     *            list of time slots in the table
     * 
     * @return true if any invalid entry is detected
     *************************************************************************/
    private boolean validateTableData(List<Variable> applications,
                                      List<Message> timeSlots)
    {
        this.applications = applications;
        this.timeSlots = timeSlots;

        int numInvalid = 0;
        String type = "application scheduler applications";

        // Validate the application group data and remove any invalid
        // applications
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

        return numInvalid != 0;
    }

    /**************************************************************************
     * Check all the stored application information for inconsistencies. Update
     * or flag the application for removal if any changes are found
     * 
     * @return List of invalid applications
     *************************************************************************/
    private List<Variable> validateApplicationData()
    {
        List<Variable> removedApps = new ArrayList<Variable>();

        // Create a group tree
        CcddGroupTreeHandler groupTree = new CcddGroupTreeHandler(ccddMain,
                                                                  ccddMain.getMainFrame());

        // Get a list of the current group's names
        String[] groupNames = groupTree.getGroupHandler().getGroupNames(true);

        // Create a data field handler to interact with the groups' fields
        CcddFieldHandler fieldHandler = new CcddFieldHandler();

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
                    // Get the group's information and set the data field
                    // handler to contain the current group's information
                    GroupInformation groupInfo = groupTree.getGroupHandler().getGroupInformationByName(name);
                    fieldHandler.setFieldInformation(groupInfo.getFieldInformation());

                    // Get the application data field owner name
                    String application = CcddFieldHandler.getFieldGroupName(groupInfo.getName());

                    // Get the application's schedule rate
                    FieldInformation appInfo = fieldHandler.getFieldInformationByName(application,
                                                                                      "Schedule Rate");

                    // Check if the application's rate equals its field's rate
                    if (Float.valueOf(appInfo.getValue()) == app.getRate())
                    {
                        // Set the applications's validity to true
                        isValid = true;

                        // Get the run time field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         "Execution Time");

                        // Check if the application's run time changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) != app.getSize())
                        {
                            // Update the run time to what the field has set
                            app.setSize(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the execution priority field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         "Execution Priority");

                        // Check if the application's priority changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) == ((ApplicationData) app).getPriority())
                        {
                            // Update the application's priority
                            ((ApplicationData) app).setPriority(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the wake up ID field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         "Wake_Up ID");

                        // Check if the application's wake up ID changed
                        if (appInfo != null
                            && appInfo.getValue().equals(((ApplicationData) app).getWakeUpID()))
                        {
                            // Update the application's wake up ID
                            ((ApplicationData) app).setWakeUpID(appInfo.getValue());
                        }

                        // Get the wake up name field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         "Wake_Up Name");

                        // Check if the application's wake up name changed
                        if (appInfo != null
                            && Integer.valueOf(appInfo.getValue()) == ((ApplicationData) app).getHkSendRate())
                        {
                            // Update the application's wake up name
                            ((ApplicationData) app).setHkSendRate(Integer.valueOf(appInfo.getValue()));
                        }

                        // Get the schedule group name field information
                        appInfo = fieldHandler.getFieldInformationByName(application,
                                                                         "SCH_GROUP Name");

                        // Check if the application's sch group name changed
                        if (appInfo != null
                            && appInfo.getValue().equals(((ApplicationData) app).getSchGroup()))
                        {
                            // Update the application's wake up name
                            ((ApplicationData) app).setSchGroup(appInfo.getValue());
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

    /**************************************************************************
     * Remove data that is determined to be invalid
     * 
     * @param removedVars
     *            list of invalid data
     * 
     * @return Number of invalid entries that were found and removed
     *************************************************************************/
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
            for (Integer index : indices)
            {
                // Remove the variable from the general message
                timeSlots.get(index).removeVariable(var.getFullName());
            }
        }

        return removedVars.size();
    }
}
