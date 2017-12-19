/**
 * CFS Command & Data Dictionary variable size handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.SIZEOF_DATATYPE;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary variable size handler class
 *****************************************************************************/
public class CcddVariableSizeHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddMacroHandler macroHandler;

    // Variable offset parameters
    private int bitCount;
    private int lastByteSize;
    private String lastDataType;
    private int lastBitLength;

    // Flag that indicates if a sizeof() call references an invalid data type
    private boolean isInvalid;

    // List containing the paths for every structure and variable, and the
    // offset to the structures and variables relative to their root structures
    private List<String> structureAndVariablePaths;
    private List<Integer> structureAndVariableOffsets;

    /**************************************************************************
     * Variable size handler class constructor
     *
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddVariableSizeHandler(CcddMain ccddMain)
    {
        // Create the link definitions list
        this.ccddMain = ccddMain;
        this.dataTypeHandler = ccddMain.getDataTypeHandler();
        this.macroHandler = ccddMain.getMacroHandler();
    }

    /**************************************************************************
     * Get the structure and variable paths list
     *
     * @return Reference to the structure and variable paths list
     *************************************************************************/
    protected List<String> getStructureAndVariablePaths()
    {
        return structureAndVariablePaths;
    }

    /**************************************************************************
     * Get the structure and variable offsets list
     *
     * @return Reference to the structure and variable offsets list
     *************************************************************************/
    protected List<Integer> getStructureAndVariableOffsets()
    {
        return structureAndVariableOffsets;
    }

    /**************************************************************************
     * Replace each instance of sizeof(data type) in the specified string with
     * its numeric value
     *
     * @param expression
     *            text in which to replace any sizeof() calls
     *
     * @param validDataTypes
     *            List containing the valid data types when evaluating sizeof()
     *            calls; null if there are no data type constraints for a
     *            sizeof() call
     *
     * @return Input string with each instance of sizeof(data type) replaced by
     *         its numeric value
     *************************************************************************/
    protected String replaceSizeofWithValue(String expression,
                                            List<String> validDataTypes)
    {
        isInvalid = false;

        // Continue to step through the string, replacing each sizeof()
        // instance
        while (expression != null
               && expression.matches(".*?" + SIZEOF_DATATYPE + ".*"))
        {
            // Get the data type (primitive or structure) for the sizeof() call
            String dataType = expression.replaceFirst(".*?" + SIZEOF_DATATYPE + ".*", "$1");

            // Check if the data type isn't in the list of valid ones
            if (validDataTypes != null && !validDataTypes.contains(dataType))
            {
                // Set the flag to indicate an invalid data type reference is
                // made in a sizeof() call
                isInvalid = true;
            }

            // Get the size of the data type in bytes and replace the sizeof()
            // call with this value
            expression = expression.replaceFirst(SIZEOF_DATATYPE,
                                                 String.valueOf(getDataTypeSizeInBytes(dataType)));
        }

        return expression;
    }

    /**************************************************************************
     * Check if the text string in the previous replaceSizeofWithValue() call
     * contained an invalid data type reference
     *
     * @return true if the text string in the previous replaceSizeofWithValue()
     *         call contained an invalid data type reference
     *************************************************************************/
    protected boolean isInvalidReference()
    {
        return isInvalid;
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
        int index = structureAndVariablePaths.indexOf(macroHandler.getMacroExpansion(targetVariable).replaceFirst(":.+$", ""));

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
    protected void buildPathAndOffsetLists()
    {
        // System.out.println("buildPathAndOffsetLists"); // TODO
        // Create a tree containing all of the structures, both prototypes and
        // instances, including primitive variables. This is used for
        // determining bit-packing, variable relative position, variable
        // offsets, and structure sizes
        CcddTableTreeHandler allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                                        TableTreeType.STRUCTURES_WITH_PRIMITIVES,
                                                                        ccddMain.getMainFrame());

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
                // Get the variable path for this tree node. Expand any macros
                // contained in the variable name(s)
                String varPath = macroHandler.getMacroExpansion(allVariableTree.getFullVariablePath(nodePath));

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
                            bitLength = varPath.substring(bitIndex + 1);

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

                // Check the list for this variable path. Due to the
                // construction of the table tree a prototype structure
                // reference can occur twice
                int index = structureAndVariablePaths.indexOf(varPath);

                // Check if the variable path (prototype table) is already in
                // the list
                if (index != -1)
                {
                    // The first listing is the prototype table only (no
                    // variables); the second includes the variables and is the
                    // one required. Remove the existing reference from the
                    // list and update the index pointer to the structure to
                    // account for the removal
                    structureAndVariablePaths.remove(index);
                    structureAndVariableOffsets.remove(index);
                    structIndex--;
                }
                // This is the first reference to this variable path
                else
                {
                    // Update the index pointing to the last member of the
                    // structure
                    lastIndex++;
                }

                // Add the variable path and its offset to the lists
                structureAndVariablePaths.add(varPath);
                structureAndVariableOffsets.add(offset);
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

}
