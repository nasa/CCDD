/**
 * CFS Command and Data Dictionary telemetry scheduler input.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_PROTOTYPE_NODE_NAME;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LINKED_VARIABLES_NODE_NAME;
import static CCDD.CcddConstants.UNLINKED_VARIABLES_NODE_NAME;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.AssociatedVariable;
import CCDD.CcddClassesDataTable.BitPackNodeIndex;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.TelemetryData;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddClassesDataTable.VariableGenerator;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.SchedulerColumn;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary telemetry scheduler input class
 *************************************************************************************************/
public class CcddTelemetrySchedulerInput implements CcddSchedulerInputInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddTableTreeHandler allVariableTree;
    private CcddTableTreeHandler variableTree;
    private CcddLinkTreeHandler linkTree;
    private final CcddTelemetrySchedulerDialog schedulerDlg;

    // Component referenced by multiple methods
    private JPanel treePnl;

    // true if a node change is in progress
    private boolean isNodeSelectionChanging;

    // Currently selected rate
    private String selectedRate;

    // Rate column name
    private final String rateName;

    // List of excluded variables
    private final List<String> excludedVars;

    // List containing the paths to all elements in the allVariableTree tree
    private final List<String> allVariableTreePaths;

    // Index position in the variable tree for the variable to be inserted in a list of existing
    // variables
    private int targetVarTreeIndex;

    /**********************************************************************************************
     * Variable tree position comparison class
     *********************************************************************************************/
    private class VariableComparator implements Comparator<Variable>
    {
        /******************************************************************************************
         * Compare the position indices of a variable in a variable list with that of a target
         * variable
         *****************************************************************************************/
        @Override
        public int compare(Variable variable1, Variable variable2)
        {
            return allVariableTreePaths.indexOf(variable1.getFullName())
                   - targetVarTreeIndex;
        }
    }

    /**********************************************************************************************
     * Telemetry scheduler input class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param schedulerDlg
     *            reference to the telemetry scheduler dialog that created this class
     *
     * @param rateName
     *            rate column name
     *
     * @param allVariableTree
     *            reference to a table tree containing all variables
     *
     * @param allVariableTreePaths
     *            list containing the paths to all elements in the allVariableTree tree
     *********************************************************************************************/
    CcddTelemetrySchedulerInput(CcddMain ccddMain,
                                CcddTelemetrySchedulerDialog schedulerDlg,
                                String rateName,
                                CcddTableTreeHandler allVariableTree,
                                List<String> allVariableTreePaths)
    {
        this.ccddMain = ccddMain;
        this.schedulerDlg = schedulerDlg;
        this.rateName = rateName;
        this.allVariableTree = allVariableTree;
        this.allVariableTreePaths = allVariableTreePaths;
        this.dataTypeHandler = ccddMain.getDataTypeHandler();

        excludedVars = new ArrayList<String>();

        // Initialize the variable tree
        initialize();
    }

    /**********************************************************************************************
     * Get a reference to the variable tree
     *
     * @return Reference to the variable tree
     *********************************************************************************************/
    protected CcddTableTreeHandler getVariableTree()
    {
        return variableTree;
    }

    /**********************************************************************************************
     * Get a reference to the link tree
     *
     * @return Reference to the link tree
     *********************************************************************************************/
    protected CcddLinkTreeHandler getLinkTree()
    {
        return linkTree;
    }

    /**********************************************************************************************
     * Initialize the variable tree table
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private void initialize()
    {
        isNodeSelectionChanging = false;

        // Initialize the currently selected rate to 1 Hz if present in the list of available
        // rates; otherwise choose the first rate if any rates exist, and if none exist set the
        // rate to a dummy value
        List<String> availableRates = Arrays.asList(getAvailableRates());
        selectedRate = availableRates.contains("1")
                                                    ? "1"
                                                    : (!availableRates.isEmpty()
                                                                                 ? CcddUtilities.removeHTMLTags(availableRates.get(0))
                                                                                 : "0");

        // Build a link tree
        linkTree = new CcddLinkTreeHandler(ccddMain, null, rateName, ccddMain.getMainFrame());

        // Set the linked selected rate
        linkTree.setSelectedRate(selectedRate);

        // Build the variable tree that shows tables and their variables for the selected rate. Use
        // the first rate in the available rates array to determine which variables to display in
        // the tree, or, if none, create the tree showing no variables
        variableTree = new CcddTableTreeHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain,
                                                                     null,
                                                                     ccddMain.getMainFrame()),
                                                TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES,
                                                rateName,
                                                selectedRate,
                                                excludedVars,
                                                DEFAULT_PROTOTYPE_NODE_NAME,
                                                UNLINKED_VARIABLES_NODE_NAME,
                                                ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a node in the variable tree. This replaces the
             * placeholder method in CcddTableTreeHandler
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Select the associated message(s) in the scheduler table if a variable is
                    // selected in the variable tree. Note that below any assigned variables are
                    // deselected, so this call must occur first
                    selectMessageByVariable();

                    // Set the flag to prevent variable tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Update the telemetry scheduler table text highlighting
                    schedulerDlg.getSchedulerHandler().updateSchedulerTableHighlight();

                    // Reset the flag to allow variable tree updates
                    isNodeSelectionChanging = false;
                }
            }

            /**************************************************************************************
             * Override building the table tree so that the links can be added
             *************************************************************************************/
            @Override
            protected void buildTableTree(Boolean isExpanded,
                                          String rateName,
                                          String rateFilter,
                                          Component parent)
            {
                // Call to the super to build the tree
                super.buildTableTree(isExpanded, rateName, rateFilter, parent);

                // Create a tree showing the links that contain variables with a sample rate
                // matching the currently selected rate
                ToolTipTreeNode validLinks = linkTree.getLinksMatchingRate(LINKED_VARIABLES_NODE_NAME,
                                                                           "Links containing variables with a sample rate of "
                                                                                                       + selectedRate);

                // Insert the valid links tree into the variable tree
                ((DefaultTreeModel) getModel()).insertNodeInto(validLinks, getRootNode(), 0);

                // Set the linked variables
                setLinkedVariables(linkTree.getLinkVariables(null));

                // Set the excluded variables
                setExcludedVariables(excludedVars);
            }
        };

        // Create the tree panel
        treePnl = new JPanel(new GridBagLayout());

        // Create the variable and link trees panels with buttons in between and add them to the
        // panel
        treePnl.add(variableTree.createTreePanel("Variables",
                                                 TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                 false,
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
    }

    /**********************************************************************************************
     * Set the link name for the message variables
     *
     * @param messages
     *            list of messages
     *
     * @param rateName
     *            rate column name
     *********************************************************************************************/
    protected void setLinks(List<Message> messages, String rateName)
    {
        // Step through each message
        for (Message message : messages)
        {
            // Step through each variable in the message
            for (Variable variable : message.getVariablesWithParent())
            {
                // Set the link for the variable (null if the variable is not a link member)
                ((TelemetryData) variable).setLink(linkTree.getLinkHandler().getVariableLink(variable.getFullName(),
                                                                                             rateName));
            }
        }
    }

    /**********************************************************************************************
     * Get the index at which the specified variable should be inserted into the list of variables
     * provided. The variable tree is used to determine the target variable's position relative to
     * the variables in the list (if any)
     *
     * @param newVariable
     *            variable for which to determine the insertion index
     *
     * @param existingVariables
     *            list of existing variables into which the variable is to be inserted
     *
     * @return Index at which to insert the target variable; -1 if the provided list is empty and
     *         -2 if the variable is already in the list
     *********************************************************************************************/
    @Override
    public int getVariableRelativeIndex(Variable newVariable, List<Variable> existingVariables)
    {
        int insertIndex = -1;

        // Check if any variables are in the list
        if (!existingVariables.isEmpty())
        {
            // Target variable's row index in the tree containing all variables
            targetVarTreeIndex = allVariableTreePaths.indexOf(newVariable.getFullName());

            // Check if the target isn't prior to the first existing variable in the list
            if (targetVarTreeIndex < allVariableTreePaths.indexOf(existingVariables.get(0).getFullName()))
            {
                // Insert the new variable at the beginning of the list of existing variables
                insertIndex = 0;
            }
            // Check if the target isn't after the last existing variable in the list
            else if (targetVarTreeIndex < allVariableTreePaths.indexOf(existingVariables.get(existingVariables.size()
                                                                                             - 1)
                                                                                        .getFullName()))
            {
                // Get the position in in the variable list where the new variable should be
                // inserted
                insertIndex = -1 - Collections.binarySearch(existingVariables,
                                                            newVariable,
                                                            new VariableComparator());

                // Check if the variable is already in the existing variables list
                if (insertIndex < 0)
                {
                    // Set the index to null to indicate the variable shouldn't be added to the
                    // message
                    insertIndex = -2;
                }
            }
        }

        return insertIndex;
    }

    /**********************************************************************************************
     * Get the total number of bytes of the specified or selected variable(s)
     *
     * @param variables
     *            list of variables; null to use the currently selected variable(s)
     *
     * @return Total number of bytes of the specified variable(s)
     *********************************************************************************************/
    @Override
    public int getSelectedVariableSize(List<Variable> variables)
    {
        // Check if no variable list is provided
        if (variables == null)
        {
            // Use the currently selected variables
            variables = getSelectedVariable();
        }

        // Initialize the total number of bytes remaining in the message
        int totalVarBytes = 0;

        // Step through each variable
        for (int varIndex = 0; varIndex < variables.size(); varIndex++)
        {
            // Add the variable's number of bytes to the total
            totalVarBytes += variables.get(varIndex).getSize();

            // Check if this is a bit-wise variable
            if (variables.get(varIndex).getFullName().contains(":"))
            {
                // Variable's row index in the tree containing all variables
                int treeIndex = allVariableTreePaths.indexOf(variables.get(varIndex).getFullName()) - 1;

                // Check if the variable is in the tree path. If a table is deleted and the
                // scheduler table isn't updated then the variable won't be located
                if (treeIndex > -1)
                {
                    // Get the variable's tree node
                    ToolTipTreeNode last = (ToolTipTreeNode) allVariableTree.getPathForRow(treeIndex).getLastPathComponent();

                    // Get the node indices that encompass the packed variables (if applicable)
                    BitPackNodeIndex nodeIndex = allVariableTree.getBitPackedVariables(last);

                    // Update the variable index to skip the packed members, if present, so that
                    // their bytes don't affect the total
                    varIndex += nodeIndex.getLastIndex() - nodeIndex.getFirstIndex();
                }
            }
        }

        return totalVarBytes;
    }

    /**********************************************************************************************
     * For the specified list of variables, get (1) the total size in bytes of the variables that
     * are associated with the first variable in the list, and (2) a list of Variable objects of
     * the associated variables. Variables are considered 'associated' if (a) they are all bit-wise
     * variables that are packed together, or (b) they are members of a string. If the first
     * variable isn't associated with the succeeding variables then the return value references
     * only the first variable
     *
     * @param variables
     *            list of variables where the first member of the list if the one to be checked for
     *            associates
     *
     * @return AssociatedVariable object containing the total size in bytes of the variables
     *         associated with the first variable in the specified list and the list of the
     *         associated variables
     *********************************************************************************************/
    protected AssociatedVariable getAssociatedVariables(List<Variable> variables)
    {
        // Get a reference to the lead variable. This is the variable that is checked for
        // associated variables
        Variable variable = variables.get(0);

        // Store the size of the variable
        int totalSize = variable.getSize();

        // Create a list to store the lead variable and its associates and add the lead variable to
        // the list
        List<Variable> associatedVars = new ArrayList<Variable>();
        associatedVars.add(variable);

        // Set flag to true if the lead variable is a bit-wise variable
        boolean isBitPack = variable.getFullName().contains(":");

        // Check if the lead variable is a bit-wise or string variable (and hence can have other
        // variables associated with it)
        if (isBitPack
            || dataTypeHandler.isString(((TelemetryData) variable).getDataType()))
        {
            // Get the variable's row index in the tree containing all variables
            int treeIndex = allVariableTreePaths.indexOf(variable.getFullName()) - 1;

            // Get the variable's tree node
            ToolTipTreeNode last = (ToolTipTreeNode) allVariableTree.getPathForRow(treeIndex).getLastPathComponent();

            // Get the indices of the other variables associated with this variable (i.e., other
            // variable bit-packed or other members of the string)
            BitPackNodeIndex nodeIndex = isBitPack
                                                   ? allVariableTree.getBitPackedVariables(last)
                                                   : allVariableTree.getStringVariableMembers(last);

            // Calculate the number of other variables associated with the lead variable
            int numVars = nodeIndex.getLastIndex() - nodeIndex.getFirstIndex();

            // Check if the number of associated variables doesn't exceed the number of variables
            // in the list provided
            if (numVars <= variables.size())
            {
                // Step through the associated variables
                for (int index = 1; index <= numVars; index++)
                {
                    // Check that this isn't a bit-wise variable (i.e, it's a string variable)
                    if (!isBitPack)
                    {
                        // Add the variable's size to the total
                        totalSize += variables.get(index).getSize();
                    }

                    // Add the variable to the list of associated variables
                    associatedVars.add(variables.get(index));
                }
            }
            // More associated variables were detected than were provided. This can only occur if
            // the rates for associated variables don't match - this shouldn't be possible
            else
            {
                // Inform the user if there is a rate assignment issue
                new CcddDialogHandler().showMessageDialog(schedulerDlg.getDialog(),
                                                          "<html><b> Auto-fill detected mismatched "
                                                                                    + "rates for variable(s) associated with </b>"
                                                                                    + variables.get(0).getFullName(),
                                                          "Assign Failure",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }

        return new AssociatedVariable(totalSize, associatedVars);
    }

    /**********************************************************************************************
     * Get a list of variables at the specified rate
     *
     * @param rate
     *            rate column name
     *
     * @return List of variables at the specified rate
     *********************************************************************************************/
    @Override
    public List<Variable> getVariablesAtRate(String rate)
    {
        List<Variable> varList = new ArrayList<Variable>();
        List<String> pathList = new ArrayList<String>();

        // Convert the rate string to a float
        float rateVal = CcddUtilities.convertStringToFloat(rate);

        // Update the tree to have the variables of the given rate
        updateVariableTree(rate);

        // Get all the paths of the variables in the current variable tree
        pathList.addAll(variableTree.getPrimitiveVariablePaths(variableTree.getRootNode(), true));

        // Step through each path in the list
        for (String path : pathList)
        {
            // Split the path (project name , linked/unlinked node header, and variable path)
            String[] pathParts = path.split(",");

            // Check if the variable is linked
            if (pathParts[1].trim().equals(LINKED_VARIABLES_NODE_NAME))
            {
                // Create the variable
                TelemetryData variable = VariableGenerator.generateTelemetryData(Arrays.copyOfRange(pathParts,
                                                                                                    3,
                                                                                                    pathParts.length),

                                                                                 rateVal);

                // Set the link to which the variable belongs
                variable.setLink(pathParts[2]);

                // Add it to the list of variables
                varList.add(variable);
            }
            // Variable isn't linked
            else
            {
                // Create the variable and add it to the list of variables
                varList.add(VariableGenerator.generateTelemetryData(Arrays.copyOfRange(pathParts,
                                                                                       variableTree.getHeaderNodeLevel(),
                                                                                       pathParts.length),
                                                                    rateVal));
            }
        }

        return varList;
    }

    /**********************************************************************************************
     * Get the selected variable(s) from the variable tree
     *
     * @return List containing the selected variable(s)
     *********************************************************************************************/
    @Override
    public List<Variable> getSelectedVariable()
    {
        // Create a list to hold the variables
        List<Variable> varList = new ArrayList<Variable>();

        // Get the node path(s) of the selected variable(s)
        List<Object[]> paths = variableTree.getSelectedVariables(true);

        // Step through all the selected paths
        for (Object[] path : paths)
        {
            boolean isLinked = false;

            // Set the start of the variable path
            int index = variableTree.getHeaderNodeLevel();

            // Check if the variable is linked
            if (path[1].toString().trim().equals(LINKED_VARIABLES_NODE_NAME))
            {
                // Set the start of the variable path to skip the root, link header, and link name
                // nodes
                index = 3;

                // Set the flag to indicate the variable is linked
                isLinked = true;
            }

            // Check if the path contains a variable
            if (path.length > index)
            {
                boolean isFound = false;

                // Step through the created variables
                for (Variable var : varList)
                {
                    // Get the variable name from the path
                    String name = allVariableTree.createNameFromPath(path, index);

                    // Check if the variable has already been created
                    if (var.getFullName().equals(name))
                    {
                        // Set the flag to indicate the variable already exists and stop searching
                        isFound = true;
                        break;
                    }
                }

                // Check if the variable was found in the created list
                if (!isFound)
                {
                    // Check if the variable is linked
                    if (isLinked)
                    {
                        // Gets the link's definitions
                        List<String[]> definitions = linkTree.getLinkHandler().getLinkDefinitionsByName(path[2].toString(),
                                                                                                        rateName);

                        // Step through the link's definitions
                        for (int defnIndex = 0; defnIndex < definitions.size(); defnIndex++)
                        {
                            // Add each variable in the link by first creating the variable
                            varList.add(VariableGenerator.generateTelemetryData(definitions.get(defnIndex)[LinksColumn.MEMBER.ordinal()].split(","),
                                                                                CcddUtilities.convertStringToFloat(selectedRate)));
                        }
                    }
                    // Not linked
                    else
                    {
                        // Add the variable to the variable list
                        varList.add(VariableGenerator.generateTelemetryData(Arrays.copyOfRange(path,
                                                                                               variableTree.getHeaderNodeLevel(),
                                                                                               path.length),
                                                                            CcddUtilities.convertStringToFloat(selectedRate)));
                    }
                }
            }
        }

        return varList;
    }

    /**********************************************************************************************
     * Get an array of the available rates
     *
     * @return Array of sample rates
     *********************************************************************************************/
    @Override
    public String[] getAvailableRates()
    {
        return ccddMain.getRateParameterHandler().getRatesInUse(rateName, schedulerDlg);
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
     * Add the specified variable(s) to the excluded variable list
     *
     * @param varName
     *            list of variables to be added to the excluded variable list
     *********************************************************************************************/
    @Override
    public void excludeVariable(List<String> varName)
    {
        // Check if a variable name is provided
        if (varName != null && !varName.isEmpty())
        {
            // Step through each name
            for (String name : varName)
            {
                // Check if the name is not in the list of excluded variables
                if (!excludedVars.contains(name))
                {
                    // Add the name to the list
                    excludedVars.add(name);
                }
            }

            // Exclude the variables
            variableTree.setExcludedVariables(excludedVars);
            variableTree.clearDisabledNodes();
        }
    }

    /**********************************************************************************************
     * Remove the variable(s) from the excluded variable list
     *
     * @param varName
     *            list of variables to be removed from the excluded variable list
     *********************************************************************************************/
    @Override
    public void includeVariable(List<String> varName)
    {
        // Check if a variable name is provided
        if (varName != null && !varName.isEmpty())
        {
            // Step through each name
            for (String name : varName)
            {
                // Check if the name is in the list of excluded variables
                if (excludedVars.contains(name))
                {
                    // Remove the variable from the list
                    excludedVars.remove(name);
                }
            }

            // Set the excluded variables
            variableTree.setExcludedVariables(excludedVars);
            variableTree.clearDisabledNodes();
        }
    }

    /**********************************************************************************************
     * Update the tree to display variables at the specified rate
     *
     * @param rate
     *            rate for filtering the variables
     *********************************************************************************************/
    @Override
    public void updateVariableTree(String rate)
    {
        // Store the selected rate
        selectedRate = rate;

        // Set the rate in the link tree to flag compatible links
        linkTree.setSelectedRate(selectedRate);

        // Rebuild the variable tree using the selected rate as a filter
        variableTree.buildTableTree(null,
                                    rateName,
                                    selectedRate,
                                    ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Get the tree panel
     *
     * @return Tree panel object
     *********************************************************************************************/
    @Override
    public JPanel getInputPanel()
    {
        // Return the tree panel object
        return treePnl;
    }

    /**********************************************************************************************
     * Select the message(s) in the assignment tree for which the selected variable in the variable
     * tree is a member
     *********************************************************************************************/
    private void selectMessageByVariable()
    {
        // Check if only a single node is selected in the variable tree
        if (variableTree.getSelectionPaths() != null
            && variableTree.getSelectionPaths().length == 1)
        {
            // Get the selected variable's path
            Object[] path = variableTree.getSelectionPath().getPath();

            // Set the start of the variable path
            int index = 0;

            // Check if the variable is linked
            if (variableTree.removeExtraText(path[1].toString().trim()).equals(LINKED_VARIABLES_NODE_NAME))
            {
                // Set the start of the variable path to skip the root, link header, and link name
                // nodes
                index = 1;
            }

            // Get the first selected variable
            String variablePath = variableTree.getFullVariablePath(path, index);

            // Check if the variable contains the HTML flags indicating it is in use; i.e., belongs
            // to a message
            if (variablePath.contains(DISABLED_TEXT_COLOR))
            {
                // Remove the HTML flags from the variable path
                variablePath = variableTree.removeExtraText(variablePath);

                // Clear any selected message(s) in the Scheduler table
                schedulerDlg.getSchedulerHandler().getSchedulerEditor().getTable().clearSelection();

                // Step through the list of current messages. Go in reverse order so that the first
                // message containing the variable gets the focus
                for (int row = schedulerDlg.getSchedulerHandler().getCurrentMessages().size() - 1; row >= 0; row--)
                {
                    String option = "";

                    // Get the message reference
                    Message message = schedulerDlg.getSchedulerHandler().getCurrentMessages().get(row);

                    // Check if the variable is a member of the message
                    if (message.isVariableInMessage(variablePath))
                    {
                        // Set the option to the message name
                        option = message.getName();

                        // Select the message in the Scheduler table
                        schedulerDlg.getSchedulerHandler().getSchedulerEditor().getTable().changeSelection(row,
                                                                                                           SchedulerColumn.NAME.ordinal(),
                                                                                                           true,
                                                                                                           false);
                    }
                    // Check if the message has any sub-messages
                    else if (message.getNumberOfSubMessages() > 1)
                    {
                        // Set the column index to the first sub-message ID column
                        int column = SchedulerColumn.ID.ordinal() + 1;

                        // Step through each sub-message
                        for (Message subMsg : message.getSubMessages())
                        {
                            // Check if the variable is a member of the sub-message
                            if (subMsg.isVariableInMessage(variablePath))
                            {
                                // Append the sub-message index to the option
                                option += (column - 2) + ", ";

                                // Select the sub-message in the Scheduler table
                                schedulerDlg.getSchedulerHandler().getSchedulerEditor().getTable().changeSelection(row,
                                                                                                                   column,
                                                                                                                   true,
                                                                                                                   false);
                            }

                            column++;
                        }

                        // Check if a matching sub-message was found
                        if (!option.isEmpty())
                        {
                            // Remove the trailing comma
                            option = CcddUtilities.removeTrailer(option, ", ");

                            // Prepend the message name and sub-message(s) text
                            option = message.getName()
                                     + " sub-msg"
                                     + (option.contains(",")
                                                             ? "s"
                                                             : "")
                                     + " "
                                     + option;
                        }
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
