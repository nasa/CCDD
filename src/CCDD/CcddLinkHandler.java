/**
 * CFS Command and Data Dictionary link handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.LinksColumn;

/**************************************************************************************************
 * CFS Command and Data Dictionary link handler class
 *************************************************************************************************/
public class CcddLinkHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddVariableHandler variableHandler;

    // List to contain the link definitions (link names and variable paths) retrieved from the
    // database
    private final List<String[]> linkDefinitions;

    /**********************************************************************************************
     * Link handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param linkDefinitions
     *            list containing the link definitions
     *********************************************************************************************/
    CcddLinkHandler(CcddMain ccddMain, List<String[]> linkDefinitions)
    {
        // Create the link definitions list
        this.linkDefinitions = CcddUtilities.copyListOfStringArrays(linkDefinitions);

        this.ccddMain = ccddMain;
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        variableHandler = ccddMain.getVariableHandler();

        // Remove any variable references in the link definitions that aren't found in the links
        // tree
        removeInvalidLinks();
    }

    /**********************************************************************************************
     * Link handler class constructor. Load the link information from the project database
     *
     * @param ccddMain
     *            main class
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddLinkHandler(CcddMain ccddMain, Component parent)
    {
        this(ccddMain,
             ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.LINKS,
                                                                          false,
                                                                          parent));
    }

    /**********************************************************************************************
     * Get the reference to all link definitions
     *
     * @return List of all link definitions
     *********************************************************************************************/
    protected List<String[]> getLinkDefinitions()
    {
        return linkDefinitions;
    }

    /**********************************************************************************************
     * Set the link definitions
     *
     * @param linkDefinitions
     *            list containing the link definitions
     *********************************************************************************************/
    protected void setLinkDefinitions(List<String[]> linkDefinitions)
    {
        this.linkDefinitions.clear();
        this.linkDefinitions.addAll(linkDefinitions);
    }

    /**********************************************************************************************
     * Get the link names for the specified rate column name
     *
     * @param rateName
     *            rate column name
     *
     * @return List containing the link names for the specified rate column name; an empty list if
     *         there are no links associated with the rate
     *********************************************************************************************/
    protected List<String> getLinkNamesByRate(String rateName)
    {
        List<String> linkNames = new ArrayList<String>();

        // Step through the link definitions
        for (String[] linkDefn : linkDefinitions)
        {
            // Check if the link's rate matches the specified rate and that this link hasn't
            // already been included
            if (linkDefn[LinksColumn.RATE_NAME.ordinal()].equals(rateName)
                && !linkNames.contains(linkDefn[LinksColumn.LINK_NAME.ordinal()]))
            {
                // Add the link name to the list
                linkNames.add(linkDefn[LinksColumn.LINK_NAME.ordinal()]);
            }
        }

        return linkNames;
    }

    /**********************************************************************************************
     * Get the reference to a specified link's definitions
     *
     * @param linkName
     *            link name
     *
     * @param linkRate
     *            link rate name
     *
     * @return List of a link's definitions
     *********************************************************************************************/
    protected List<String[]> getLinkDefinitionsByName(String linkName, String linkRate)
    {
        List<String[]> definitions = new ArrayList<String[]>();

        // Step through the link definitions
        for (int index = 0; index < linkDefinitions.size(); index++)
        {
            // Check if the link names match and that this is not the link's rate/description row
            if (linkRate.equals(linkDefinitions.get(index)[LinksColumn.RATE_NAME.ordinal()])
                && linkName.equals(linkDefinitions.get(index)[LinksColumn.LINK_NAME.ordinal()])
                && !linkDefinitions.get(index)[LinksColumn.MEMBER.ordinal()].matches("\\d.*"))
            {
                // Add the definition to the list
                definitions.add(linkDefinitions.get(index));
            }
        }

        return definitions;
    }

    /**********************************************************************************************
     * Return an array of rate and link names to which the specified variable belongs
     *
     * @param variable
     *            variable path and name
     *
     * @param useDataStream
     *            true to return the data stream name in place of the rate column name
     *
     * @return Array containing the rates and links to which the specified variable is a member; an
     *         empty array if the variable does not belong to a link
     *********************************************************************************************/
    protected String[][] getVariableLinks(String variable, boolean useDataStream)
    {
        List<String[]> links = new ArrayList<String[]>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the rate name, link name, and rate/description or member
            String rateName = linkDefn[LinksColumn.RATE_NAME.ordinal()];
            String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is not a link description entry (these are indicated if the first
            // character is a digit, which is the link rate) and if the link member matches the
            // target variable
            if (!linkMember.matches("\\d.*")
                && macroHandler.getMacroExpansion(variable).equals(macroHandler.getMacroExpansion(linkMember)))
            {
                // Check if the data stream name should be returned instead of the rate column name
                if (useDataStream)
                {
                    // Get the rate information based on the rate column name
                    RateInformation rateInfo = ccddMain.getRateParameterHandler().getRateInformationByRateName(rateName);

                    // Check if the rate information exists for this rate column
                    if (rateInfo != null)
                    {
                        // Substitute the data stream name for the rate column name
                        rateName = rateInfo.getStreamName();
                    }
                }

                // Add the link to the list
                links.add(new String[] {rateName, linkName});
            }
        }

        return links.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Return the link name to which the specified variable belongs for the specified rate
     *
     * @param variable
     *            variable path and name
     *
     * @param rateName
     *            rate name
     *
     * @return Name of the link if the variable and rate match; null if no match exists
     *********************************************************************************************/
    protected String getVariableLink(String variable, String rateName)
    {
        String linkName = null;

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the link rate/description or member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is not a link description entry (these are indicated if the first
            // character is a digit, which is the link rate), and if the link member matches the
            // target variable and rate
            if (!linkMember.matches("\\d.*")
                && variable.equals(linkMember)
                && rateName.equals(linkDefn[LinksColumn.RATE_NAME.ordinal()]))
            {
                // Get the link name and stop searching
                linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
                break;
            }
        }

        return linkName;
    }

    /**********************************************************************************************
     * Calculate the number of bytes represented by this link by totaling the size of each variable
     * member
     *
     * @param rateName
     *            data stream rate column name
     *
     * @param name
     *            name of the link to calculate the remaining bytes for
     *
     * @return Number of bytes used in the link; 0 if no variables are in the link
     *********************************************************************************************/
    protected int getLinkSizeInBytes(String rateName, String name)
    {
        String lastRate = "";
        String lastName = "";
        int lastIndex = -1;
        int lastOffset = -1;
        int size = 0;

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the rate name, link name, and rate/description or member
            String linkRate = linkDefn[LinksColumn.RATE_NAME.ordinal()];
            String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if the rate name and link name for this entry matches the target, and that
            // this is the link information entry
            if (linkRate.equals(rateName)
                && linkName.equals(name)
                && linkMember.contains(".")
                && !linkMember.matches("\\d.*"))
            {
                // Get the offset of this variable relative to its root structure. A variable's bit
                // length is ignored if provided
                int index = variableHandler.getStructureAndVariablePaths().indexOf(macroHandler.getMacroExpansion(linkMember).replaceFirst(":.+$", ""));
                int offset = variableHandler.getStructureAndVariableOffsets().get(index);

                // Check if this variable is not bit-packed with the previous one. The variables
                // are packed together if this variable immediately follows the previous one in the
                // path list and has the same offset
                if (!(linkRate.equals(lastRate)
                      && linkName.equals(lastName)
                      && index == lastIndex + 1
                      && offset == lastOffset))
                {
                    // Get the data type from the variable name
                    String dataType = linkMember.substring(linkMember.lastIndexOf(",") + 1,
                                                           linkMember.lastIndexOf("."));

                    // Add the size of this data type to the link size total
                    size += dataTypeHandler.getSizeInBytes(dataType);
                }

                // Store the parameters for comparison in the next loop
                lastRate = linkRate;
                lastName = linkName;
                lastIndex = index;
                lastOffset = offset;
            }
        }

        return size;
    }

    /**********************************************************************************************
     * Get the description for a specified link
     *
     * @param rateName
     *            data stream rate column name
     *
     * @param name
     *            link name
     *
     * @return Description of the specified link; returns a blank if the link doesn't exist
     *********************************************************************************************/
    protected String getLinkDescription(String rateName, String name)
    {
        String description = "";

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the rate name, link name, and rate/description or member
            String linkRate = linkDefn[LinksColumn.RATE_NAME.ordinal()];
            String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if the rate name and link name for this entry matches the target, and that
            // this is the link information entry
            if (linkRate.equals(rateName)
                && linkName.equals(name)
                && linkMember.matches("\\d.*"))
            {
                // Separate the link rate and description
                String[] rateAndDesc = linkMember.split(",", 2);

                // Check if the description is present
                if (rateAndDesc.length > 1)
                {
                    // Store the description
                    description = rateAndDesc[1];
                }

                // Stop searching
                break;
            }
        }

        return description;
    }

    /**********************************************************************************************
     * Get the link rate
     *
     * @param rateName
     *            data stream rate column name
     *
     * @param name
     *            name of the link
     *
     * @return Link rate; blank if the link name doesn't exist
     *********************************************************************************************/
    protected String getLinkRate(String rateName, String name)
    {
        String rate = "";

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the rate name, link name, and rate/description or member
            String linkRate = linkDefn[LinksColumn.RATE_NAME.ordinal()];
            String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if the rate name and link name for this entry matches the target, and that
            // this is the link information entry
            if (linkRate.equals(rateName)
                && linkName.equals(name)
                && linkMember.matches("\\d.*"))
            {
                // Get the rate and stop searching
                rate = linkMember.split(",")[0];
                break;
            }
        }

        return rate;
    }

    /**********************************************************************************************
     * Get the application name data field values associated with the specified link's variable
     * members
     *
     * @param applicationFieldName
     *            name of the application name data field
     *
     * @return Array containing the application name data field values associated with the
     *         specified link's variable members. Each application name is listed only once in the
     *         array
     *********************************************************************************************/
    protected String[] getApplicationNames(String applicationFieldName)
    {
        List<String> appNames = new ArrayList<String>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the link rate/description or member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if the link name for this entry matches the target and that this is not the
            // link information entry
            if (!linkMember.matches("\\d.*"))
            {
                // Split the link definition's variable string into the parent structure name and
                // variable reference string
                String[] parentAndPath = linkMember.split(",", 2);

                // Get the information for the parent's application name data field
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(parentAndPath[0],
                                                                                    applicationFieldName);

                // Check that the data field exists
                if (fieldInfo != null)
                {
                    // Get the application name field information
                    String appName = fieldHandler.getFieldInformationByName(parentAndPath[0],
                                                                            applicationFieldName)
                                                 .getValue();

                    // Check that the application name field exists for the specified table and
                    // that this name hasn't already been added to the list
                    if (appName != null && !appNames.contains(appName))
                    {
                        // Add the application name field name to the list
                        appNames.add(appName);
                    }
                }
            }
        }

        return appNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Check that the variables referenced in the link definitions exist in the data tables. Remove
     * any invalid link definitions
     *********************************************************************************************/
    private void removeInvalidLinks()
    {
        List<String[]> invalidLinks = new ArrayList<String[]>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Get the link member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is a variable reference (and not the link definition) and that the
            // variable isn't in the link tree
            if (linkMember.contains(".")
                && !linkMember.matches("\\d.*")
                && variableHandler.getStructureAndVariablePaths().indexOf(linkMember.replaceFirst(":.+$", "")) == -1)
            {
                // Store the invalid link
                invalidLinks.add(linkDefn);
            }
        }

        // Remove any invalid link definitions
        linkDefinitions.removeAll(invalidLinks);
    }
}
