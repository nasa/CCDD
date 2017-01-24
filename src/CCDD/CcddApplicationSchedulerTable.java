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
import CCDD.CcddClasses.Message;
import CCDD.CcddClasses.Variable;
import CCDD.CcddConstants.SchedulerType;

/******************************************************************************
 * CFS Command & Data Dictionary application scheduler table handler class
 *****************************************************************************/
public class CcddApplicationSchedulerTable
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddSchedulerDbIOHandler schedulerDB;

    private List<String[][]> entries;
    private List<Variable> appList;
    private String[][] defines;
    private List<String> schGroups;

    // Constants
    private final String ENABLE = "SCH_ENABLED";
    private final String ACTIVITY = "SCH_ACTIVITY_SEND_MSG";
    private final String UNUSED = "SCH_UNUSED";
    private final String GROUPNONE = "SCH_GROUP_NONE";

    /**************************************************************************
     * Application scheduler table handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddApplicationSchedulerTable(CcddMain ccddMain)
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
     * Create a application scheduler table based on the time slot definitions
     *************************************************************************/
    protected void createApplicationSchedulerTable()
    {
        entries = new ArrayList<String[][]>();
        int slots = ccddMain.getApplicationParameterHandler().getNumberOfSlots();
        String[][] entry;

        for (Message message : getvalidatedStoredData())
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
    private List<Message> getvalidatedStoredData()
    {
        CcddSchedulerValidator validator = new CcddSchedulerValidator(ccddMain,
                                                                      null);
        schedulerDB.loadStoredData();
        List<Message> messages = schedulerDB.getStoredData(0);
        appList = schedulerDB.getVariableList(0);
        validator.validateTableData(appList,
                                    messages,
                                    SchedulerType.APPLICATION_SCHEDULER, "");
        return messages;
    }
}
