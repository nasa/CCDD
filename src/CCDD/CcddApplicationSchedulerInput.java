/**
 * CFS Command and Data Dictionary application scheduler input.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.ApplicationData;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.SchedulerColumn;

/**************************************************************************************************
 * CFS Command and Data Dictionary application scheduler input class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddApplicationSchedulerInput implements CcddSchedulerInputInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddApplicationSchedulerDialog schedulerDlg;
    private CcddGroupTreeHandler applicationTree;
    private final CcddFieldHandler fieldHandler;

    // Panel containing the application tree
    private JPanel treePnl;

    // Currently selected rate
    private String selectedRate;

    // List of excluded applications
    private final List<String> excludedList;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    /**********************************************************************************************
     * Application scheduler input class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param schedulerDlg
     *            reference to the application scheduler dialog
     *********************************************************************************************/
    CcddApplicationSchedulerInput(CcddMain ccddMain, CcddApplicationSchedulerDialog schedulerDlg)
    {
        this.ccddMain = ccddMain;
        this.schedulerDlg = schedulerDlg;
        fieldHandler = ccddMain.getFieldHandler();

        excludedList = new ArrayList<String>();

        // Initialize the application tree
        initialize();
    }

    /**********************************************************************************************
     * Initialize the application tree
     *********************************************************************************************/
    private void initialize()
    {
        isNodeSelectionChanging = false;

        // Set the selected rate to a dummy value initially. Once the application information is
        // loaded the rate is initialized to 1 Hz if that rate is valid
        selectedRate = "0";

        // Create the application tree
        applicationTree = new CcddGroupTreeHandler(ccddMain,
                                                   null,
                                                   selectedRate,
                                                   true,
                                                   ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a node in the application tree
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Select the associated slot(s) in the scheduler table if an application is
                    // selected in the application tree. Note that below any assigned applications
                    // are deselected, so this call must occur first
                    selectTimeSlotbyApplication();

                    // Set the flag to prevent application tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Update the application scheduler table text highlighting
                    schedulerDlg.getSchedulerHandler().updateSchedulerTableHighlight();

                    // Reset the flag to allow application tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Create the tree panel
        treePnl = new JPanel(new GridBagLayout());

        // Create the applications tree and add it to the tree panel
        treePnl.add(applicationTree.createTreePanel("Applications",
                                                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                    true,
                                                    ccddMain.getMainFrame()),
                    new GridBagConstraints(0,
                                           0,
                                           1,
                                           1,
                                           1.0,
                                           1.0,
                                           GridBagConstraints.LINE_START,
                                           GridBagConstraints.BOTH,
                                           new Insets(0,
                                                      0,
                                                      ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                      0),
                                           0,
                                           0));

        // Initialize the currently selected rate to 1 Hz if present in the list of available
        // rates; otherwise choose the first rate if any rates exist, and if none exist set the
        // rate to a dummy value
        List<String> availableRates = Arrays.asList(getAvailableRates());
        updateVariableTree(availableRates.contains("1")
                                                        ? "1"
                                                        : (!availableRates.isEmpty()
                                                                                     ? availableRates.get(0)
                                                                                     : "0"));
    }

    /**********************************************************************************************
     * Get the index at which the specified application should be inserted in the list of
     * applications provided. The application tree is used to determine the target application's
     * position relative to the applications in the list (if any)
     *
     * @param application
     *            application for which to determine the insertion index
     *
     * @param applications
     *            list of applications into which the application is to be inserted
     *
     * @return Index at which to insert the target application
     *********************************************************************************************/
    @Override
    public int getVariableRelativeIndex(Variable application, List<Variable> applications)
    {
        int insertIndex = -1;

        // Check if any applications are in the list
        if (!applications.isEmpty())
        {
            int targetVarTreeIndex = 0;

            // Step through the application tree's root node's children, if any
            for (Enumeration<?> element = applicationTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
            {
                // Get the referenced node and its path
                ToolTipTreeNode tableNode = (ToolTipTreeNode) element.nextElement();

                // Check if the target node matches the application tree node
                if (application.getFullName().equals(CcddUtilities.removeHTMLTags(tableNode.getUserObject().toString())))
                {
                    // The target application is located in the tree; stop searching
                    break;
                }

                targetVarTreeIndex++;
            }

            // Step through each application in the list
            for (insertIndex = 0; insertIndex < applications.size(); insertIndex++)
            {
                int listVarTreeIndex = 0;

                // Step through the application tree's root node's children, if any
                for (Enumeration<?> element = applicationTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
                {
                    // Get the referenced node and its path
                    ToolTipTreeNode tableNode = (ToolTipTreeNode) element.nextElement();

                    // Check if the target node matches the application tree node
                    if (applications.get(insertIndex).getFullName().equals(CcddUtilities.removeHTMLTags(tableNode.getUserObject().toString())))
                    {
                        // The list application is located in the tree; stop searching
                        break;
                    }

                    listVarTreeIndex++;
                }

                // Check if the list application tree position is after the target application tree
                // position
                if (listVarTreeIndex > targetVarTreeIndex)
                {
                    // The relative position of the target application within the list is
                    // determined; stop searching
                    break;
                }
            }
        }

        return insertIndex;
    }

    /**********************************************************************************************
     * Get the total amount of time for the specified application(s)
     *
     * @param applications
     *            list of applications; null to use the currently selected application(s)
     *
     * @return Total amount of time for the specified application(s)
     *********************************************************************************************/
    @Override
    public int getSelectedVariableSize(List<Variable> applications)
    {
        // Check if no variable list is provided
        if (applications == null)
        {
            // Use the currently selected variables
            applications = getSelectedVariable();
        }

        int totalRunTime = 0;

        // Step through each application
        for (Variable application : applications)
        {
            // Get the application's execution run time
            totalRunTime = totalRunTime
                           + Integer.valueOf(Integer.valueOf(getDataFieldValue(application.getFullName(),
                                                                               DefaultApplicationField.EXECUTION_TIME)));
        }

        return totalRunTime;
    }

    /**********************************************************************************************
     * Get a list of applications with the specified rate
     *
     * @param rate
     *            currently selected rate
     *
     * @return List of variable object(s) representing the application(s) at the specified rate
     *********************************************************************************************/
    @Override
    public List<Variable> getVariablesAtRate(String rate)
    {
        List<String> applications = new ArrayList<String>();

        // Step though the application group information
        for (GroupInformation grpInfo : applicationTree.getGroupHandler().getGroupInformation())
        {
            // Check that the group represents a CFS application, the target rate matches the field
            // value, and the application isn't in the exclusion list
            if (grpInfo.isApplication()
                && rate.equals(getDataFieldValue(grpInfo.getName(),
                                                 DefaultApplicationField.SCHEDULE_RATE))
                && !excludedList.contains(grpInfo.getName()))
            {
                // Add the application name to the list
                applications.add(grpInfo.getName());
            }
        }

        return getApplicationFields(applications.toArray(new String[0]), rate);
    }

    /**********************************************************************************************
     * Get the selected node in the application tree
     *
     * @return List of variable object(s) representing the selected application(s)
     *********************************************************************************************/
    @Override
    public List<Variable> getSelectedVariable()
    {
        // Get the selected application node(s) in the tree (can only be one)
        return getApplicationFields(applicationTree.getTopLevelSelectedNodeNames(), selectedRate);
    }

    /**********************************************************************************************
     * Get the selected node in the application tree
     *
     * @param applications
     *            array of application names
     *
     * @param rate
     *            currently selected rate
     *
     * @return List of variable object(s) representing the specified application(s)
     *********************************************************************************************/
    private List<Variable> getApplicationFields(String[] applications, String rate)
    {
        List<Variable> appList = new ArrayList<Variable>();

        // Step through each application
        for (String application : applications)
        {
            // Remove any HTML tags, if present
            application = applicationTree.removeExtraText(application);

            // Use the application's data to create an application data object
            appList.add(new ApplicationData(application,
                                            CcddUtilities.convertStringToFloat(rate),
                                            Integer.valueOf(getDataFieldValue(application,
                                                                              DefaultApplicationField.EXECUTION_TIME)),
                                            Integer.valueOf(getDataFieldValue(application,
                                                                              DefaultApplicationField.PRIORITY)),
                                            Integer.valueOf(getDataFieldValue(application,
                                                                              DefaultApplicationField.MESSAGE_RATE)),
                                            getDataFieldValue(application,
                                                              DefaultApplicationField.WAKE_UP_MESSAGE),
                                            Integer.valueOf(getDataFieldValue(application,
                                                                              DefaultApplicationField.HK_SEND_RATE)),
                                            getDataFieldValue(application,
                                                              DefaultApplicationField.HK_WAKE_UP_MESSAGE),
                                            getDataFieldValue(application,
                                                              DefaultApplicationField.SCH_GROUP)));
        }

        return appList;
    }

    /**********************************************************************************************
     * Get an array of the available rates
     *
     * @return Array of available rates
     *********************************************************************************************/
    @Override
    public String[] getAvailableRates()
    {
        // List of all the current rates
        List<String> rates = new ArrayList<String>();

        // Step through each application in the application tree
        for (GroupInformation appInfo : applicationTree.getGroupHandler().getGroupInformation())
        {
            // Get the scheduler rate of the current application
            FieldInformation rateInfo = fieldHandler.getFieldInformationByName(CcddFieldHandler.getFieldGroupName(appInfo.getName()),
                                                                               "Schedule Rate");

            // Check if the application had a schedule rate assigned
            if (rateInfo != null && !rates.contains(rateInfo.getValue()))
            {
                // Add the rate to the list of current rates
                rates.add(rateInfo.getValue());
            }
        }

        // Sort the list of rates from greatest to least
        Collections.sort(rates, Collections.reverseOrder(new Comparator<String>()
        {
            /**************************************************************************************
             * Override the compare method to sort from greatest to least
             *************************************************************************************/
            @Override
            public int compare(String string1, String string2)
            {
                // Compare the integer value of the strings
                return Integer.valueOf(string1).compareTo(Integer.valueOf(string2));
            }
        }));

        return rates.toArray(new String[rates.size()]);
    }

    /**********************************************************************************************
     * Get the currently selected rate
     *
     * @return Currently selected rate
     *********************************************************************************************/
    @Override
    public String getSelectedRate()
    {
        return selectedRate;
    }

    /**********************************************************************************************
     * Add the specified application(s) to the excluded application list
     *
     * @param applications
     *            list containing the application(s) to be excluded
     *********************************************************************************************/
    @Override
    public void excludeVariable(List<String> applications)
    {
        // Check if the application name list contains any entries
        if (applications != null && applications.size() > 0)
        {
            // Step through each application
            for (String application : applications)
            {
                // Check if the excludes list already contains the string name
                if (!excludedList.contains(application))
                {
                    // Add the application name to the excluded applications list
                    excludedList.add(application);
                }
            }

            // Update the node name color if a name was removed
            applicationTree.adjustNodeText(applicationTree.getRootNode(), excludedList);
        }
    }

    /**********************************************************************************************
     * Remove the specified application(s) from the excluded application list
     *
     * @param applications
     *            list of applications to be removed from the excluded application list
     *********************************************************************************************/
    @Override
    public void includeVariable(List<String> applications)
    {
        // Check if the application name list contains any entries
        if (applications != null && !applications.isEmpty())
        {
            // Step through each application
            for (String application : applications)
            {
                // Check if the exclusion list contains the application
                if (excludedList.contains(application))
                {
                    // Remove the application from the list
                    excludedList.remove(application);
                }
            }

            // Update the node name color if a name was removed
            applicationTree.adjustNodeText(applicationTree.getRootNode(), excludedList);
        }
    }

    /**********************************************************************************************
     * Update the tree to display applications at the specified rate
     *
     * @param rate
     *            rate for filtering the applications
     *********************************************************************************************/
    @Override
    public void updateVariableTree(String rate)
    {
        // Check if the rate changed
        if (!rate.equals(selectedRate))
        {
            // Get the rate selected in the combo box
            selectedRate = rate;

            // Rebuild the application tree using the selected rate as a filter
            applicationTree.buildTree(false, rate, true, ccddMain.getMainFrame());

            // Set the node color based on the selected rate
            applicationTree.adjustNodeText(applicationTree.getRootNode(), excludedList);
        }
    }

    /**********************************************************************************************
     * Get the tree panel
     *
     * @return Tree panel object
     *********************************************************************************************/
    @Override
    public JPanel getInputPanel()
    {
        return treePnl;
    }

    /**********************************************************************************************
     * Get the data field value for the specified application and field name
     *
     * @param applicationName
     *            application name
     *
     * @param appField
     *            application data field
     *
     * @return Application data field value for the specified application
     *********************************************************************************************/
    private String getDataFieldValue(String applicationName, DefaultApplicationField appField)
    {
        // Initialize the value to the default
        String value = appField.getInitialValue();

        // Check if the application name isn't blank. This occurs if the application scheduler time
        // slot has no applications assigned
        if (!applicationName.isEmpty())
        {
            // Get the information for the specified data field
            FieldInformation groupInfo = fieldHandler.getFieldInformationByName(CcddFieldHandler.getFieldGroupName(applicationName),
                                                                                appField.getFieldName());
            // Check if the field exists and isn't empty
            if (groupInfo != null && !groupInfo.getValue().isEmpty())
            {
                // Get the data field value
                value = groupInfo.getValue();
            }
        }

        return value;
    }

    /**********************************************************************************************
     * Select the message(s) in the assignment tree for which the selected variable in the variable
     * tree is a member
     *********************************************************************************************/
    private void selectTimeSlotbyApplication()
    {
        // Check if only a single node is selected in the application tree
        if (applicationTree.getSelectionPaths().length == 1)
        {
            // Get the first selected application
            String application = applicationTree.getFullVariablePath(applicationTree.getSelectionPath().getPath(),
                                                                     applicationTree.getGroupNodeLevel());

            // Check if the application contains the HTML flags indicating it is in use; i.e.,
            // belongs to a time slot
            if (application.startsWith(DISABLED_TEXT_COLOR))
            {
                // Remove the HTML flags from the application name
                application = applicationTree.removeExtraText(application);

                // Clear any selected time slot(s) in the Scheduler table
                schedulerDlg.getSchedulerHandler().getSchedulerEditor().getTable().clearSelection();

                // Step through the list of current time slots. Go in reverse order so that the
                // first time slot containing the application gets the focus
                for (int row = schedulerDlg.getSchedulerHandler().getCurrentMessages().size() - 1; row >= 0; row--)
                {
                    String option = "";

                    // Get the time slot reference
                    Message slot = schedulerDlg.getSchedulerHandler().getCurrentMessages().get(row);

                    // Check if the application is a member of the time slot
                    if (slot.isVariableInMessage(application))
                    {
                        // Set the option to the time slot name
                        option = slot.getName();

                        // Select the time slot in the Scheduler table
                        schedulerDlg.getSchedulerHandler().getSchedulerEditor().getTable().changeSelection(row,
                                                                                                           SchedulerColumn.NAME.ordinal(),
                                                                                                           true,
                                                                                                           false);
                    }

                    // Check if a matching option was found
                    if (!option.isEmpty())
                    {
                        // Select the option in the options list
                        schedulerDlg.getSchedulerHandler().selectOptionByMessage(option);
                    }
                }
            }
        }
    }
}
