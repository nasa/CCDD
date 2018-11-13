/**
 * CFS Command and Data Dictionary group handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ALL_TABLES_GROUP_NODE_NAME;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.GroupsColumn;
import CCDD.CcddUndoHandler.UndoableArrayList;

/**************************************************************************************************
 * CFS Command and Data Dictionary group handler class
 *************************************************************************************************/
public class CcddGroupHandler
{
    private final UndoableArrayList<GroupInformation> groupInformation;

    /**********************************************************************************************
     * Group handler class constructor
     *
     * @param undoHandler
     *            reference to the undo handler; null if undo/redo operations are not needed for
     *            the groups
     *********************************************************************************************/
    CcddGroupHandler(CcddUndoHandler undoHandler)
    {
        // Check if no undo handler is specified
        if (undoHandler == null)
        {
            // Create a 'dummy' undo handler and set the flag to not allow undo operations
            undoHandler = new CcddUndoHandler(new CcddUndoManager());
            undoHandler.setAllowUndo(false);
        }

        // Create the group information list
        groupInformation = undoHandler.new UndoableArrayList<GroupInformation>();
    }

    /**********************************************************************************************
     * Group handler class constructor. Load and build the group information class from the group
     * definitions stored in the project database
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param component
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddGroupHandler(CcddMain ccddMain, CcddUndoHandler undoHandler, Component component)
    {
        this(undoHandler);

        buildGroupInformation(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.GROUPS,
                                                                                           false,
                                                                                           component));
    }

    /**********************************************************************************************
     * Get the group definitions from the group information
     *
     * @return Group definition list from the group information
     *********************************************************************************************/
    protected List<String[]> getGroupDefnsFromInfo()
    {
        List<String[]> groupDefinitions = new ArrayList<String[]>();

        // Step through each group's information
        for (GroupInformation groupInfo : groupInformation)
        {
            // Add the group application status and description to the definition list
            groupDefinitions.add(new String[] {groupInfo.getName(),
                                               (groupInfo.isApplication()
                                                                          ? "1"
                                                                          : "0")
                                                                    + ","
                                                                    + groupInfo.getDescription()});

            // Step through each group table member
            for (String member : groupInfo.getTablesAndAncestors())
            {
                // Add the member to the definition list
                groupDefinitions.add(new String[] {groupInfo.getName(), member});
            }
        }

        return groupDefinitions;
    }

    /**********************************************************************************************
     * Add a new group to the group information class
     *
     * @param name
     *            group name
     *
     * @param description
     *            group description
     *
     * @param isApplication
     *            true if the group represents a CFS application
     *
     * @return Reference to the new group's information
     *********************************************************************************************/
    protected GroupInformation addGroupInformation(String name,
                                                   String description,
                                                   boolean isApplication)
    {
        // Create the group information and add it to the list
        GroupInformation groupInfo = new GroupInformation(name, description, isApplication, null);
        groupInformation.add(groupInfo);

        // Sort the group information by group name
        sortGroupInformation();

        return groupInfo;
    }

    /**********************************************************************************************
     * Remove the specified group's information
     *
     * @param groupName
     *            group name
     *********************************************************************************************/
    protected void removeGroupInformation(String groupName)
    {
        // Step through each group's information
        for (int index = 0; index < groupInformation.size(); index++)
        {
            // Check if the name matches the target name
            if (groupInformation.get(index).getName().equals(groupName))
            {
                // Remove the group's information and stop searching
                groupInformation.remove(index);
                break;
            }
        }
    }

    /**********************************************************************************************
     * Build the group information using the group definitions and the field information in the
     * database
     *
     * @param groupDefinitions
     *            list of group definitions
     *********************************************************************************************/
    protected void buildGroupInformation(List<String[]> groupDefinitions)
    {
        // Clear the groups from the list
        groupInformation.clear();

        // Check if a group definition exists
        if (groupDefinitions != null)
        {
            // Step through the group definitions
            for (String[] groupDefn : groupDefinitions)
            {
                // Extract the link name and rate/description or member
                String groupName = groupDefn[GroupsColumn.GROUP_NAME.ordinal()];
                String groupMember = groupDefn[GroupsColumn.MEMBERS.ordinal()];

                // Check if this is a group definition entry. These are indicated if the first
                // character is a digit. A non-zero value indicates that this group represents a
                // CFS application
                if (groupMember.matches("\\d.*"))
                {
                    // Separate the CFS application identifier and description text
                    String[] appAndDesc = groupMember.split(",", 2);

                    // Create the group with its description and application status
                    groupInformation.add(new GroupInformation(groupName,
                                                              appAndDesc[1],
                                                              !appAndDesc[0].equals("0"),
                                                              null));
                }
                // This is a group's table path
                else
                {
                    // Get the reference to this group's information
                    GroupInformation groupInfo = getGroupInformationByName(groupName);

                    // Check that the group exists
                    if (groupInfo != null)
                    {
                        // Add the table to the group's table list
                        groupInfo.addTable(groupMember);
                    }
                }
            }

            // Sort the group information by group name
            sortGroupInformation();
        }
    }

    /**********************************************************************************************
     * Get the reference to a specified group's information from the supplied list of group
     * information
     *
     * @param groupInformationList
     *            list of group information from which to extract the specific group's information
     *
     * @param name
     *            group name
     *
     * @return Reference to the group's information; null if the group doesn't exist
     *********************************************************************************************/
    protected GroupInformation getGroupInformationByName(List<GroupInformation> groupInformationList,
                                                         String name)
    {
        GroupInformation groupInfo = null;

        // Step through each group's information
        for (GroupInformation grpInfo : groupInformationList)
        {
            // Check if the group name matches the target name
            if (grpInfo.getName().equals(name))
            {
                // Store the group information reference and stop searching
                groupInfo = grpInfo;
                break;
            }
        }

        return groupInfo;
    }

    /**********************************************************************************************
     * Get the reference to a specified group's information
     *
     * @param groupName
     *            group name
     *
     * @return Reference to the group's information; null if the group doesn't exist
     *********************************************************************************************/
    protected GroupInformation getGroupInformationByName(String groupName)
    {
        return getGroupInformationByName(groupInformation, groupName);
    }

    /**********************************************************************************************
     * Get the group information list
     *
     * @return Group information list
     *********************************************************************************************/
    protected List<GroupInformation> getGroupInformation()
    {
        return groupInformation;
    }

    /**********************************************************************************************
     * Sort the group information list by group name
     *********************************************************************************************/
    private void sortGroupInformation()
    {
        // Check if any groups exist
        if (groupInformation != null)
        {
            // Sort the group information list based on the group names
            Collections.sort(groupInformation, new Comparator<Object>()
            {
                /**********************************************************************************
                 * Compare group names
                 *
                 * @param grpInfo1
                 *            first group's information
                 *
                 * @param grpInfo2
                 *            second group's information
                 *
                 * @return -1 if the first group's name is lexically less than the second group's
                 *         name; 0 if the two group names are the same; 1 if the first group's name
                 *         is lexically greater than the second group's name
                 *********************************************************************************/
                @Override
                public int compare(Object grpInfo1, Object grpInfo2)
                {
                    return ((GroupInformation) grpInfo1).getName().compareTo(((GroupInformation) grpInfo2).getName());
                }
            });
        }
    }

    /**********************************************************************************************
     * Get an array containing the group names
     *
     * @param applicationOnly
     *            true if only groups representing CFS applications should be returned
     *
     * @return Array containing the group names
     *********************************************************************************************/
    protected String[] getGroupNames(boolean applicationOnly)
    {
        return getGroupNames(applicationOnly, false);
    }

    /**********************************************************************************************
     * Get an array containing the group names
     *
     * @param applicationOnly
     *            true if only groups representing CFS applications should be returned
     *
     * @param includeAllGroup
     *            true to include the name of pseudo-group that contains all tables
     *
     * @return Array containing the group names
     *********************************************************************************************/
    protected String[] getGroupNames(boolean applicationOnly, boolean includeAllGroup)
    {
        List<String> groupNames = new ArrayList<String>();

        // Check if this list applies for application and non-application groups and if the name of
        // the pseudo-group containing all tables should be included
        if (includeAllGroup && !applicationOnly)
        {
            // Add the 'All tables' group name to the list
            groupNames.add(ALL_TABLES_GROUP_NODE_NAME);
        }

        // Step through each group
        for (GroupInformation groupInfo : groupInformation)
        {
            // Check if all groups are to be returned or if not, that this is an application group
            if (!applicationOnly || groupInfo.isApplication())
            {
                // Add the group name to the list
                groupNames.add(groupInfo.getName());
            }
        }

        return groupNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the description for the specified group
     *
     * @param groupName
     *            group name
     *
     * @return Description for the specified group; blank if the group has no description or the
     *         group doesn't exist
     *********************************************************************************************/
    protected String getGroupDescription(String groupName)
    {
        String description = "";

        // Get a reference to the group's information
        GroupInformation groupInfo = getGroupInformationByName(groupName);

        // Check if the group exists
        if (groupInfo != null)
        {
            // Get the group's description
            description = getGroupInformationByName(groupName).getDescription();
        }

        return description;
    }

    /**********************************************************************************************
     * Set the specified group's description
     *
     * @param groupName
     *            group name
     *
     * @param description
     *            group description
     *********************************************************************************************/
    protected void setDescription(String groupName, String description)
    {
        // Get a reference to the group's information
        GroupInformation groupInfo = getGroupInformationByName(groupName);

        // Check if the group exists
        if (groupInfo != null)
        {
            // Set the group description
            groupInfo.setDescription(description);
        }
    }

    /**********************************************************************************************
     * Set the specified group's CFS application status flag
     *
     * @param groupName
     *            group name
     *
     * @param isApplication
     *            true if the group represents a CFS application
     *********************************************************************************************/
    protected void setIsApplication(String groupName, boolean isApplication)
    {
        // Get a reference to the group's information
        GroupInformation groupInfo = getGroupInformationByName(groupName);

        // Check if the group exists
        if (groupInfo != null)
        {
            // Set the group application status flag
            groupInfo.setIsApplication(isApplication);
        }
    }
}
