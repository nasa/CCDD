/**
 * CFS Command & Data Dictionary link handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary link handler class
 *****************************************************************************/
public class CcddLinkHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;

    // List to contain the link definitions (link names and variable paths)
    // retrieved from the database
    private List<String[]> linkDefinitions;

    // Variable offset parameters
    private int bitCount;
    private int lastByteSize;
    private String lastDataType;
    private int lastBitLength;

    // List containing the paths for every structure and variable, and the
    // offset to the structures and variables relative to their root structures
    private List<String> structureAndVariablePaths;
    private List<Integer> structureAndVariableOffsets;

    /**************************************************************************
     * Link handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param linkDefinitions
     *            list containing the link definitions
     *************************************************************************/
    CcddLinkHandler(CcddMain ccddMain, List<String[]> linkDefinitions)
    {
        this.ccddMain = ccddMain;
        this.dataTypeHandler = ccddMain.getDataTypeHandler();
        this.linkDefinitions = linkDefinitions;

        // Create the variable path and offset lists
        buildPathAndOffsetLists();

        // Remove any variable references in the link definitions that aren't
        // found in the links tree
        removeInvalidLinks();
    }

    /**************************************************************************
     * Link handler class constructor. Load the link information from the
     * project database
     * 
     * @param ccddMain
     *            main class
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    CcddLinkHandler(CcddMain ccddMain, Component parent)
    {
        this(ccddMain,
             ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.LINKS,
                                                                          parent));
    }

    /**************************************************************************
     * Get the reference to all link definitions
     * 
     * @return List of all link definitions
     *************************************************************************/
    protected List<String[]> getLinkDefinitions()
    {
        return linkDefinitions;
    }

    /**************************************************************************
     * Set the link definitions
     * 
     * @param linkDefinitions
     *            list containing the link definitions
     *************************************************************************/
    protected void setLinkDefinitions(List<String[]> linkDefinitions)
    {
        this.linkDefinitions = linkDefinitions;
    }

    /**************************************************************************
     * Get the index of the link definition specified by the rate name, link
     * name, and variable path
     * 
     * @param rateName
     *            rate column name
     * 
     * @param linkName
     *            link name
     * 
     * @param variablePath
     *            full variable path, including the parent table name, data
     *            types, and variable names
     * 
     * @return Index of the specified link; -1 if the link doesn't exist
     *************************************************************************/
    protected int getLinkDefinitionIndex(String rateName,
                                         String linkName,
                                         String variablePath)
    {
        int linkRow = -1;

        // Step through the link definitions
        for (String[] linkDefn : linkDefinitions)
        {
            // Increment the link row
            linkRow++;

            // Check if this is the definition of the current
            // variable's link
            if (linkDefn[0].equals(rateName)
                && linkDefn[1].equals(linkName)
                && linkDefn[2].equals(variablePath))
            {
                // Stop searching
                break;
            }
        }

        return linkRow;
    }

    /**************************************************************************
     * Get the reference to a specified link's definitions
     * 
     * @param linkName
     *            link name
     * 
     * @param linkRate
     *            link rate name
     * 
     * @return List of a link's definitions
     *************************************************************************/
    protected List<String[]> getLinkDefinitionsByName(String linkName,
                                                      String linkRate)
    {
        List<String[]> definitions = new ArrayList<>();

        // Step through the link definitions
        for (int index = 0; index < linkDefinitions.size(); index++)
        {
            // Check if the link names match and that this is not the link's
            // rate/description row
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

    /**************************************************************************
     * Return an array of rate and link names to which the specified variable
     * belongs
     * 
     * @param variable
     *            variable path and name
     * 
     * @param useDataStream
     *            true to return the data stream name in place of the rate
     *            column name
     * 
     * @return Array containing the rates and links to which the specified
     *         variable is a member; an empty array if the variable does not
     *         belong to a link
     *************************************************************************/
    protected String[][] getVariableLinks(String variable,
                                          boolean useDataStream)
    {
        List<String[]> links = new ArrayList<String[]>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the rate name, link name, and rate/description or member
            String rateName = linkDefn[LinksColumn.RATE_NAME.ordinal()];
            String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is not a link description entry (these are
            // indicated if the first character is a digit, which is the link
            // rate) and if the link member matches the target variable
            if (!linkMember.matches("\\d.*") && variable.equals(linkMember))
            {
                // Check if the data stream name should be returned instead of
                // the rate column name
                if (useDataStream)
                {
                    // Get the rate information based on the rate column name
                    RateInformation rateInfo = ccddMain.getRateParameterHandler().getRateInformationByRateName(rateName);

                    // Check if the rate information exists for this rate
                    // column
                    if (rateInfo != null)
                    {
                        // Substitute the data stream name for the rate column
                        // name
                        rateName = rateInfo.getStreamName();
                    }
                }

                // Add the link to the list
                links.add(new String[] {rateName, linkName});
            }
        }

        return links.toArray(new String[0][0]);
    }

    /**************************************************************************
     * Return the link name to which the specified variable belongs for the
     * specified rate
     * 
     * @param variable
     *            variable path and name
     * 
     * @param rateName
     *            rate name
     * 
     * @return Name of the link if the variable and rate match; null if no
     *         match exists
     *************************************************************************/
    protected String getVariableLink(String variable, String rateName)
    {
        String linkName = null;

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the link rate/description or member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is not a link description entry (these are
            // indicated if the first character is a digit, which is the link
            // rate), and if the link member matches the target variable and
            // rate
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

    /**************************************************************************
     * Calculate the number of bytes represented by this link by totaling the
     * size of each variable member
     * 
     * @param rateName
     *            data stream rate column name
     * 
     * @param name
     *            name of the link to calculate the remaining bytes for
     * 
     * @return Number of bytes used in the link; 0 if no variables are in the
     *         link
     *************************************************************************/
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

            // Check if the rate name and link name for this entry matches the
            // target, and that this is the link information entry
            if (linkRate.equals(rateName)
                && linkName.equals(name)
                && linkMember.contains(".")
                && !linkMember.matches("\\d.*"))
            {
                // Get the data type from the variable name
                String dataType = linkMember.substring(linkMember.lastIndexOf(",") + 1,
                                                       linkMember.lastIndexOf("."));

                // Get the offset of this variable relative to its root
                // structure. A variable's bit length is ignored if provided
                int index = structureAndVariablePaths.indexOf(linkMember.replaceFirst(":.+$", ""));
                int offset = structureAndVariableOffsets.get(index);

                // Check if this variable is not bit-packed with the previous
                // one. The variables are packed together if this variable
                // immediately follows the previous one in the path list and
                // has the same offset
                if (!(linkRate.equals(lastRate)
                      && linkName.equals(lastName)
                      && index == lastIndex + 1
                      && offset == lastOffset))
                {
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

    /**************************************************************************
     * Get the description for a specified link
     * 
     * @param rateName
     *            data stream rate column name
     * 
     * @param name
     *            link name
     * 
     * @return Description of the specified link; returns a blank if the link
     *         doesn't exist
     *************************************************************************/
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

            // Check if the rate name and link name for this entry matches the
            // target, and that this is the link information entry
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

    /**************************************************************************
     * Get the link rate
     * 
     * @param rateName
     *            data stream rate column name
     * 
     * @param name
     *            name of the link
     * 
     * @return Link rate; blank if the link name doesn't exist
     *************************************************************************/
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

            // Check if the rate name and link name for this entry matches the
            // target, and that this is the link information entry
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

    /**************************************************************************
     * Get the application name data field values associated with the specified
     * link's variable members
     * 
     * @param fieldHandler
     *            field handler to access data fields
     * 
     * @param applicationFieldName
     *            name of the application name data field
     * 
     * @return Array containing the application name data field values
     *         associated with the specified link's variable members. Each
     *         application name is listed only once in the array
     *************************************************************************/
    protected String[] getApplicationNames(CcddFieldHandler fieldHandler,
                                           String applicationFieldName)
    {
        List<String> appNames = new ArrayList<String>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Extract the link rate/description or member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if the link name for this entry matches the target and
            // that this is not the link information entry
            if (!linkMember.matches("\\d.*"))
            {
                // Split the link definition's variable string into the
                // parent structure name and variable reference string
                String[] parentAndPath = linkMember.split(",", 2);

                // Get the information for the parent's application name data
                // field
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(parentAndPath[0],
                                                                                    applicationFieldName);

                // Check that the data field exists
                if (fieldInfo != null)
                {
                    // Get the application name field information
                    String appName = fieldHandler.getFieldInformationByName(parentAndPath[0],
                                                                            applicationFieldName).getValue();

                    // Check that the application name field exists for the
                    // specified table and that this name hasn't already been
                    // added to the list
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

    /**************************************************************************
     * Get the size in bytes of the specified primitive or structure data type
     * 
     * @param dataType
     *            structure name or primitive data type
     * 
     * @return Size in bytes required to store the data type; returns 0 if the
     *         data type doesn't exist
     *************************************************************************/
    protected int getDataTypeSizeInBytes(String dataType)
    {
        int sizeInBytes = 0;

        // Check if the data type is a primitive
        if (dataTypeHandler.isPrimitive(dataType))
        {
            sizeInBytes = dataTypeHandler.getSizeInBytes(dataType);
        }
        // The data type isn't a primitive; check for a structure
        else
        {
            // Get the index in the path list for the specified structure or
            // variable. Remove the bit length if provided
            int index = structureAndVariablePaths.indexOf(dataType);

            // Check if the target exists
            if (index != -1)
            {
                // Get the size of the structure
                sizeInBytes = structureAndVariableOffsets.get(index);
            }
        }

        return sizeInBytes;
    }

    /**************************************************************************
     * Get the byte offset of the specified variable relative to its root
     * structure. The variable's path, including data type and variable name,
     * is used to verify that the specified target has been located; i.e., not
     * another variable with the same name
     * 
     * @param targetVariable
     *            a comma separated string of the root structure and each data
     *            type and variable name of each variable in the current search
     *            path. The bit length may be omitted for bit-wise variables
     * 
     * @return The byte offset to the target prototype structure, or variable
     *         relative to its root structure; returns -1 if the prototype
     *         structure name or root-variable path combination is invalid
     *************************************************************************/
    protected int getVariableOffset(String targetVariable)
    {
        int offset = -1;

        // Get the index into the variable path list for the specified
        // structure/variable. A variable's bit length is ignored if present
        int index = structureAndVariablePaths.indexOf(targetVariable.replaceFirst(":.+$", ""));

        // Check that the structure/variable exists
        if (index != -1)
        {
            // Check if the target includes a variable
            if (targetVariable.contains(","))
            {
                // Retrieve the variable's offset
                offset = structureAndVariableOffsets.get(index);
            }
            // The target is a prototype/root structure
            else
            {
                // The offset for a prototype/root structure is always 0; the
                // offset list value for a prototype/root structure name is the
                // structure size
                offset = 0;
            }
        }

        return offset;
    }

    /**************************************************************************
     * Using a variable tree create two lists: one that contains a reference to
     * every structure and variable (keeping the child structures and variables
     * in the order in which they appear relative to their root structure), and
     * another list that has the offset for the variable relative to its root
     * structure. The total structure size in bytes is stored in place of the
     * offset value for each root structure entry in the list
     *************************************************************************/
    private void buildPathAndOffsetLists()
    {
        // Create a tree containing all of the prototype structures and
        // variables. This is used for determining bit-packing, variable
        // relative position, variable offsets, and structure sizes
        CcddTableTreeHandler allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                                        TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                                        ccddMain.getMainFrame());

        // Expand the tree so that all nodes are 'visible'
        allVariableTree.setTreeExpansion(true);

        structureAndVariablePaths = new ArrayList<String>();
        structureAndVariableOffsets = new ArrayList<Integer>();

        // Initialize the offset, bit count, and the previous variable's size,
        // type, and bit length
        int offset = 0;
        bitCount = 0;
        lastByteSize = 0;
        lastDataType = "";
        lastBitLength = 0;

        int lastIndex = 0;
        int structIndex = 0;

        // Step through all of the nodes in the variable tree
        for (Enumeration<?> element = allVariableTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the path to this node
            TreeNode[] nodePath = ((ToolTipTreeNode) element.nextElement()).getPath();

            // Check if the path references a structure or variable (instead of
            // the tree's root or header nodes)
            if (nodePath.length > allVariableTree.getHeaderNodeLevel())
            {
                // Get the variable path for this tree node
                String varPath = allVariableTree.getFullVariablePath(nodePath);

                // Check if the path contains a data type
                if (varPath.matches(".+,.+\\..+"))
                {
                    // Extract the data type from the variable path
                    String dataType = varPath.substring(varPath.lastIndexOf(",") + 1,
                                                        varPath.lastIndexOf("."));

                    // Check if this references a primitive data type
                    if (dataTypeHandler.isPrimitive(dataType))
                    {
                        String bitLength = "";

                        int bitIndex = varPath.indexOf(":");

                        // Check if this variable has a bit length
                        if (bitIndex != -1)
                        {
                            // Extract the bit length from the variable path
                            bitLength = ccddMain.getMacroHandler().getMacroExpansion(varPath.substring(bitIndex + 1));

                            // Remove the bit length from the variable path
                            varPath = varPath.substring(0, bitIndex);
                        }

                        // Adjust the offset to account for bit-packing
                        offset = adjustVariableOffset(dataType, bitLength, offset);
                    }
                    // Not a primitive data type (i.e., it's a structure)
                    else
                    {
                        // Add the last variable's byte size to the offset
                        // total
                        offset += lastByteSize;

                        // Reinitialize the bit count, and the previous
                        // variable's size, type, and bit length
                        bitCount = 0;
                        lastByteSize = 0;
                        lastDataType = "";
                        lastBitLength = 0;
                    }
                }
                // The path doesn't contain a data type; i.e., it's a prototype
                // structure reference
                else
                {
                    // Check that this isn't the first prototype structure
                    // detected. The size is stored once the end of the
                    // structure is reached
                    if (lastIndex != 0)
                    {
                        // Adjust the offset to account for bit-packing
                        offset = adjustVariableOffset(lastDataType, "", offset);

                        // Store the offset as the size for this structure
                        structureAndVariableOffsets.set(structIndex, offset);

                        // Store the index of the prototype structure
                        structIndex = lastIndex;
                    }

                    // Reset the offset since this indicates the start of a new
                    // root structure. Initialize the bit count, and the
                    // previous variable's size, type, and bit length
                    offset = 0;
                    bitCount = 0;
                    lastByteSize = 0;
                    lastDataType = "";
                    lastBitLength = 0;
                }

                // Add the variable path and its offset to the lists
                structureAndVariablePaths.add(varPath);
                structureAndVariableOffsets.add(offset);

                lastIndex++;
            }
        }

        // Check that a prototype structure was detected
        if (lastIndex != 0)
        {
            // Adjust the offset to account for bit-packing
            offset = adjustVariableOffset(lastDataType, "", offset);

            // Store the offset as the size for this structure
            structureAndVariableOffsets.set(structIndex, offset);
        }
    }

    /**************************************************************************
     * Adjust the offset to the current variable based on the last variable's
     * byte size and any bit packing
     * 
     * @param dataType
     *            variable's data type
     * 
     * @param bitLength
     *            string representing the number of bits used by variable;
     *            blank if this is a non-bit variable
     * 
     * @param offset
     *            offset to the previous variable
     * 
     * @return The adjusted byte offset to the target variable
     *************************************************************************/
    private int adjustVariableOffset(String dataType,
                                     String bitLength,
                                     int offset)
    {
        // Get the size in bytes based on the variable's data type
        int byteSize = dataTypeHandler.getSizeInBytes(dataType);

        // Get the bit length associated with the variable; use 0 if no bit
        // length is specified
        int bits = bitLength.matches("\\d+")
                                            ? bits = Integer.valueOf(bitLength)
                                            : 0;

        // Update the bit counter using the bit length
        bitCount += bits;

        // Check if the current or previous variable has no bit length
        // specified, the data type changed, or the data type has no room for
        // the requested number of bits
        if (bits == 0
            || lastBitLength == 0
            || !dataType.equals(lastDataType)
            || bitCount > byteSize * 8)
        {
            // Set the bit counter to the current variable's bit length (0 if
            // this is a non-bit variable)
            bitCount = bits;

            // Add the previous parameter's byte size to the offset counter
            offset += lastByteSize;
        }

        // Store the size in bytes, the data type, and bit length for
        // calculating the offset to the next variable
        lastByteSize = byteSize;
        lastDataType = dataType;
        lastBitLength = bits;

        return offset;
    }

    /**************************************************************************
     * Check that the variables referenced in the link definitions exist in the
     * data tables. Remove any invalid link definitions
     *************************************************************************/
    private void removeInvalidLinks()
    {
        List<String[]> invalidLinks = new ArrayList<String[]>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Get the link member
            String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

            // Check if this is a variable reference (and not the link
            // definition) and that the variable isn't in the link tree
            if (linkMember.contains(".")
                && !linkMember.matches("\\d.*")
                && structureAndVariablePaths.indexOf(linkMember.replaceFirst(":.+$", "")) == -1)
            {
                // Store the invalid link
                invalidLinks.add(linkDefn);
            }
        }

        // Remove any invalid link definitions
        linkDefinitions.removeAll(invalidLinks);
    }
}
