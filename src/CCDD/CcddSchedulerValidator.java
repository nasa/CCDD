/**
 * CFS Command & Data Dictionary scheduler data validator. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClasses.ApplicationData;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.Message;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TelemetryData;
import CCDD.CcddClasses.Variable;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.SchedulerType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary scheduler data validator class
 *****************************************************************************/
public class CcddSchedulerValidator
{
    // Class reference
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;

    // Parent component
    private final Component parent;

    // List of messages
    private List<Message> messages;

    // List of variables
    private List<Variable> varList;

    /**************************************************************************
     * Scheduler data validator class constructor
     *
     * @param ccddMain
     *            main class
     * 
     * @param parent
     *            component creating this class
     *************************************************************************/
    protected CcddSchedulerValidator(CcddMain ccddMain, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
    }

    /**************************************************************************
     * Validate the telemetry table and remove from the list of messages any
     * variable that is invalid
     * 
     * @param variables
     *            list of variables in the messages
     * 
     * @param messages
     *            list of messages in the table
     * 
     * @param option
     *            scheduler type
     * 
     * @param rateName
     *            rate column name
     * 
     * @return true if any invalid entry is detected
     *************************************************************************/
    protected boolean validateTableData(List<Variable> variables,
                                        List<Message> messages,
                                        SchedulerType option,
                                        String rateName)
    {
        this.varList = variables;
        this.messages = messages;

        int numInvalid = 0;
        String type = "";

        // Check if this is the telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            type = "telemetry scheduler variables";

            // Validate the table data and remove any invalid variables
            numInvalid += removeInvalidData(validateTelemetryData(rateName));

            // Validate the link data and remove any invalid variables
            numInvalid += removeInvalidData(validateLinkData(rateName));
        }
        // Check if it is the application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            type = "application scheduler applications";

            // Validate the application group data and remove any invalid
            // applications
            numInvalid += removeInvalidData(validateApplicationData());
        }

        // Check if no entries were removed (i.e., all are valid)
        if (numInvalid == 0)
        {
            // Log that validating the scheduler data succeeded
            ccddMain.getSessionEventLog().logEvent(SUCCESS_MSG,
                                                   "All "
                                                       + type
                                                       + " are valid");
        }
        // Invalid entries were found
        else
        {
            // Inform the user that invalid entries exist
            ccddMain.getSessionEventLog().logFailEvent(parent,
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
     * Check the stored messages' variables for inconsistencies. Update or flag
     * the variable for removal if any changes are found
     * 
     * @param rateName
     *            rate column name
     * 
     * @return List of invalid variables
     *************************************************************************/
    private List<Variable> validateTelemetryData(String rateName)
    {
        List<Variable> removedVars = new ArrayList<Variable>();
        TypeDefinition typeDefn = null;

        // Get the table information for the referenced tables
        List<TableInformation> tableInformation = getTableInformation();

        // Step through each variable
        for (Variable var : varList)
        {
            TelemetryData data = (TelemetryData) var;
            TableInformation tableInfo = null;
            boolean isValid = false;
            int variableRow = -1;

            // Variable name, data type, and bit length column indices
            int variableNameIndex = -1;
            int dataTypeIndex = -1;
            int bitLengthIndex = -1;

            // Create storage for the table data as it exists in the
            // database
            String[] path = data.getFullName().split(",");

            // Remove the variable path in order to get the table path
            String fullTablePath = data.getFullName().substring(0,
                                                                data.getFullName().lastIndexOf(','));

            // Step through each variable in the path
            for (int pathIndex = 1; pathIndex < path.length; pathIndex++)
            {
                boolean isFound = false;

                // Step through each table to check if the table still exists
                for (TableInformation info : tableInformation)
                {
                    // Check if the table paths match
                    if (fullTablePath.equals(info.getTablePath()))
                    {
                        isFound = true;

                        // Get the type definition for the table
                        typeDefn = tableTypeHandler.getTypeDefinition(info.getType());

                        // Get the column indices for the variable name, data
                        // type, and bit length columns
                        variableNameIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                        dataTypeIndex = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                        bitLengthIndex = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);

                        // Step through each row in the table
                        for (int row = 0; row < info.getData().length; row++)
                        {
                            // Check if the variable is the same size and that
                            // the structure is a member of that table
                            if (dataTypeHandler.getSizeInBytes(info.getData()[row][dataTypeIndex]) == dataTypeHandler.getSizeInBytes(path[pathIndex].replaceAll("\\..*", ""))
                                && info.getData()[row][variableNameIndex].equals(path[pathIndex].replaceAll("[^\\.]*\\.|:.*$", "")))
                            {
                                // Store the table information and variable
                                // row, and stop searching
                                tableInfo = info;
                                variableRow = row;
                                break;
                            }
                        }
                    }

                    // Check if the table has been found
                    if (isFound)
                    {
                        // Stop searching
                        break;
                    }
                }

                // Check if the table was not found or the table was not a
                // member of the structure
                if (!isFound && tableInfo == null)
                {
                    break;
                }
            }

            // Check if a matching table and variable exists
            if (variableRow != -1 && tableInfo != null)
            {
                // Get the this rate column's index
                int rateColumnIndex = typeDefn.getColumnIndexByUserName(rateName);

                // Check if the rate column exists
                if (rateColumnIndex != -1)
                {
                    // Check if the table information (variable name, bit
                    // length, data type size in bytes, and rate) matches the
                    // variable's information
                    if (tableInfo.getData()[variableRow][variableNameIndex].equals(data.getName())
                        && tableInfo.getData()[variableRow][bitLengthIndex].equals(data.getBitLength())
                        && dataTypeHandler.getSizeInBytes(tableInfo.getData()[variableRow][dataTypeIndex]) == data.getSize()
                        && !tableInfo.getData()[variableRow][rateColumnIndex].isEmpty()
                        && CcddUtilities.convertStringToFloat(tableInfo.getData()[variableRow][rateColumnIndex]) == data.getRate())
                    {
                        // Check if the data type changed (though the size in
                        // bytes remains the same)
                        if (!(tableInfo.getData()[variableRow][dataTypeIndex].equals(data.getDataType())))
                        {
                            // Update the variable's data type
                            data.setDataType(tableInfo.getData()[variableRow][dataTypeIndex]);
                        }

                        // Set valid to be true
                        isValid = true;
                    }
                }
            }

            // Check if the variable doesn't exist
            if (!isValid)
            {
                // Add the variable to the list of ones to be removed
                removedVars.add(data);
            }
        }

        return removedVars;
    }

    /**************************************************************************
     * Check the variables to find any inconsistencies in the link data. If the
     * variable has been unlinked then the variable's link information will be
     * updated. If the variable has been linked then it is flagged for removal
     * 
     * @param rateName
     *            rate column name
     * 
     * @return List of invalid variables
     *************************************************************************/
    private List<Variable> validateLinkData(String rateName)
    {
        List<Variable> unlinkedVars = new ArrayList<Variable>();
        List<Variable> linkedVars = new ArrayList<Variable>();
        List<Variable> removedVars = new ArrayList<Variable>();

        // Get the link information
        CcddLinkHandler linkHndlr = new CcddLinkHandler(ccddMain,
                                                        ccddMain.getMainFrame());

        // Initialize the unlinked list with all of the variables
        unlinkedVars.addAll(varList);

        // Step through each variable. If the variable references a link and
        // the link exists then add the variable to the list of linked
        // variables; if the link reference isn't valid then reset it
        for (Variable var : unlinkedVars)
        {
            TelemetryData data = (TelemetryData) var;

            // Check if the variable has a link reference
            if (data.getLink() != null)
            {
                // Check if the variable's link reference is valid
                if (linkHndlr.getVariableLink(data.getFullName(),
                                              rateName) != null)
                {
                    // Add the variable to the list of linked variables
                    linkedVars.add(data);
                }
                // The variable's link is invalid
                else
                {
                    // Clear the variable's link
                    data.setLink(null);
                }
            }
        }

        // Remove the linked variables from the variable list, leaving only
        // unlinked variables
        unlinkedVars.removeAll(linkedVars);

        // Step through each unlinked variable. If the variable does, in fact,
        // belong to a link then set its link reference and add it to the list
        // of variables that were added to a link
        for (Variable var : unlinkedVars)
        {
            TelemetryData data = (TelemetryData) var;

            // Get the link to which the variable belongs, if any
            String linkName = linkHndlr.getVariableLink(var.getFullName(),
                                                        rateName);

            // Check if the variable belongs to a link
            if (linkName != null)
            {
                // Set the variable's link
                data.setLink(linkName);

                // Add the variable to the list of variables that are no longer
                // unlinked
                removedVars.add(data);
            }
        }

        unlinkedVars.clear();

        // Step through the variables that were added to a link. If a variable
        // was added to a link and is contained in the same messages then don't
        // remove the variable
        for (Variable var : removedVars)
        {
            // Step through all the linked variables
            for (Variable linkedVar : linkedVars)
            {
                // Check if the variables have the same link
                if (var.getLink() != null
                    && var.getLink().equals(linkedVar.getLink())
                    && var.getMessageIndices().size() == linkedVar.getMessageIndices().size())
                {
                    // Step through each message the variable is added in
                    for (Integer msgIndex : linkedVar.getMessageIndices())
                    {
                        // Check if the message is not in the linked variables
                        // list
                        if (!var.getMessageIndices().contains(msgIndex))
                        {
                            // Add the variable to the list of those with
                            // mismatched message references and stop searching
                            unlinkedVars.add(var);
                            break;
                        }
                    }

                    // Stop searching since a match was found
                    break;
                }
            }
        }

        removedVars.clear();

        // Add the linked variables with mismatched messages to the list of
        // invalid variables
        removedVars.addAll(unlinkedVars);

        // Step through each invalid variable
        for (Variable var : unlinkedVars)
        {
            // Step through each linked variable
            for (Variable linkedVar : linkedVars)
            {
                // Check if the links match and the variable hasn't already
                // been added to the removed variables list
                if (var.getLink().equals(linkedVar.getLink())
                    && !removedVars.contains(linkedVar))
                {
                    // Remove any variable that went from being unlinked to
                    // linked
                    removedVars.add(linkedVar);
                }
            }
        }

        return removedVars;
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
        for (Variable app : varList)
        {
            boolean isValid = false;
            boolean isFound = false;

            // Step through the group names
            for (String name : groupNames)
            {
                // Check if the group's name matches the application's name
                if (app.getFullName().equals(name))
                {
                    isFound = true;

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

                // Check if the application was found
                if (isFound)
                {
                    // Stop searching
                    break;
                }
            }

            // Check if the application is invalid
            if (!isValid)
            {
                // Add the application's to the list of removed applications
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
        varList.removeAll(removedVars);

        // Step through the list of removed variables
        for (Variable var : removedVars)
        {
            // Get the indices of the messages to which the variable belongs
            indices = var.getMessageIndices();

            // Step through the messages in which the variable is contained
            for (Integer index : indices)
            {
                // Remove the variable from the general message
                messages.get(index).removeVariable(var.getFullName());
            }
        }

        return removedVars.size();
    }

    /**************************************************************************
     * Get a list of table information from the database. The method loads a
     * table once even if there are multiple variables for the table
     * 
     * @return List of all the tables' information
     *************************************************************************/
    private List<TableInformation> getTableInformation()
    {
        List<TableInformation> tableInformation = new ArrayList<TableInformation>();

        // Get the list of root structure tables
        List<String> rootStructures = dbTable.getRootStructures(ccddMain.getMainFrame());

        // Step through each variable
        for (Variable var : varList)
        {
            boolean isLoaded = false;

            // Remove the variable path in order to get the table path
            String fullTablePath = var.getFullName().substring(0,
                                                               var.getFullName().lastIndexOf(','));

            // Step through the tables already loaded
            for (TableInformation info : tableInformation)
            {
                // Check if the loaded table's path matches the table path
                if (fullTablePath.equals(info.getTablePath()))
                {
                    // Set the flag indicating the table is already loaded is
                    // found and stop searching
                    isLoaded = true;
                    break;
                }
            }

            // Check if the table is not already loaded
            if (!isLoaded)
            {
                String root = fullTablePath;

                // Get the index separating the root and path
                int index = fullTablePath.indexOf(",");

                // Check if a path exists
                if (index != -1)
                {
                    // Remove the path from the root and store the path
                    root = root.replaceFirst(",.*", "");
                }

                // Get the table's information from the database
                TableInformation tableInfo = dbTable.loadTableData(fullTablePath,
                                                                   rootStructures.contains(root),
                                                                   false,
                                                                   false,
                                                                   false,
                                                                   ccddMain.getMainFrame());

                // Check if the table loaded successfully and that the table
                // has data
                if (!tableInfo.isErrorFlag() && tableInfo.getData().length > 0)
                {
                    // Add the table information to the list
                    tableInformation.add(tableInfo);
                }
            }
        }

        return tableInformation;
    }
}
