/**
 * CFS Command and Data Dictionary reserved message ID handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;

/**************************************************************************************************
 * CFS Command and Data Dictionary reserved message ID handler class
 *************************************************************************************************/
public class CcddReservedMsgIDHandler
{
    // List containing the reserved message IDs and corresponding descriptions
    private List<String[]> reservedMsgIDData;

    /**********************************************************************************************
     * Reserved message ID handler class constructor used when setting the macros from a source
     * other than those in the project database
     *
     * @param reservedMsgIDs
     *            list of string arrays containing reserved message IDs and corresponding
     *            descriptions
     *********************************************************************************************/
    CcddReservedMsgIDHandler(List<String[]> reservedMsgIDs)
    {
        this.reservedMsgIDData = reservedMsgIDs;
    }

    /**********************************************************************************************
     * Reserved message ID handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddReservedMsgIDHandler(CcddMain ccddMain)
    {
        // Load the reserved message ID table from the project database
        this(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.RESERVED_MSG_IDS,
                                                                          true,
                                                                          ccddMain.getMainFrame()));
    }

    /**********************************************************************************************
     * Get the reserved message ID data
     *
     * @return List of string arrays containing reserved message IDs and the corresponding
     *         descriptions
     *********************************************************************************************/
    protected List<String[]> getReservedMsgIDData()
    {
        return reservedMsgIDData;
    }

    /**********************************************************************************************
     * Set the reserved message ID data to the supplied array
     *
     * @param reservedMsgIDData
     *            list of string arrays containing reserved message IDs and the corresponding
     *            descriptions
     *********************************************************************************************/
    protected void setReservedMsgIDData(List<String[]> reservedMsgIDData)
    {
        this.reservedMsgIDData = CcddUtilities.copyListOfStringArrays(reservedMsgIDData);
    }

    /**********************************************************************************************
     * Get a list of all reserved message ID values, converting ID ranges into individual values
     *
     * @return List containing all of the reserved message ID values
     *********************************************************************************************/
    protected List<Integer> getReservedMsgIDs()
    {
        List<Integer> reservedMsgIDs = new ArrayList<Integer>();

        // Step through each reserved message ID and ID range
        for (String[] reservedMsgID : reservedMsgIDData)
        {
            // Convert the ID string into the lower and upper (if present) value(s)
            int[] lowHigh = parseReservedMsgIDs(reservedMsgID[ReservedMsgIDsColumn.MSG_ID.ordinal()]);

            // Store the first reserved message ID value in the range (or only value if not a
            // range)
            reservedMsgIDs.add(lowHigh[0]);

            // Step through the remaining values in the range, if applicable
            for (int value = lowHigh[0] + 1; value <= lowHigh[1]; value++)
            {
                // Store the reserved message ID value
                reservedMsgIDs.add(value);
            }
        }

        return reservedMsgIDs;
    }

    /**********************************************************************************************
     * Parse a reserved message ID or ID range string into the single, or lower and upper (if
     * present), value(s)
     *
     * @param reservedMsgIDs
     *            string showing the single, or lower and upper (if present), reserved message ID
     *
     * @return Integer array where the first value is the lower (or single) ID, and the second
     *         value is the upper ID (if a range; -1 if not a range)
     *********************************************************************************************/
    protected int[] parseReservedMsgIDs(String reservedMsgIDs)
    {
        int[] lowHigh = new int[2];

        // Convert the (lower) ID into an integer value
        String[] range = reservedMsgIDs.split("\\s*+-\\s*+");
        lowHigh[0] = Integer.decode(range[0]);
        lowHigh[1] = -1;

        // Check if the ID is a range
        if (range.length == 2)
        {
            // Convert the upper ID into an integer value
            lowHigh[1] = Integer.decode(range[1]);
        }

        return lowHigh;
    }

    /**********************************************************************************************
     * Determine if the supplied message IDs match or their ranges overlap
     *
     * @param idA
     *            integer array defining the message ID or ID range to test
     *
     * @param otherID
     *            message ID or ID range to which to compare
     *
     * @return true if the IDs or ID ranges overlap
     *********************************************************************************************/
    protected boolean isWithinRange(int[] idA, String otherID)
    {
        // Convert the other lower and upper (if present) values into integers
        int[] idB = parseReservedMsgIDs(otherID);

        return ((
        // Check if the new and other IDs are single values (not ranges), and that the IDs match
        idA[1] == -1 && idB[1] == -1 && idA[0] == idB[0])

                // Check is the new ID is a single value and the other is a range, and if the new
                // ID falls within the other range
                || (idA[1] == -1
                    && idB[1] != -1
                    && idA[0] >= idB[0]
                    && idA[0] <= idB[1])

                // Check is the new ID is a range and the other is a single value, and if the other
                // ID falls within the new range
                || (idA[1] != -1
                    && idB[1] == -1
                    && idB[0] >= idA[0]
                    && idB[0] <= idA[1])

                // Check if both the new and other are ranges, and if the ranges overlap
                || idA[1] != -1
                   && idB[1] != -1
                   && Math.max(idA[0], idB[0]) <= Math.min(idA[1], idB[1]));
    }

    /**********************************************************************************************
     * Check if the supplied message ID is already reserved
     *
     * @param msgID
     *            message ID
     *
     * @return true if the supplied message ID is already reserved
     *********************************************************************************************/
    protected boolean isReservedMsgIDExists(String msgID)
    {
        boolean isExists = false;

        // Convert the lower and upper (if present) values into integers
        int[] lowHigh = parseReservedMsgIDs(msgID);

        // Step through each defined reserved message ID
        for (String[] reservedMsgID : reservedMsgIDData)
        {
            // Check if the message IDs or ID ranges overlap
            if (isWithinRange(lowHigh, reservedMsgID[ReservedMsgIDsColumn.MSG_ID.ordinal()]))
            {
                // Set the flag that indicates the supplied ID matches or falls within the range of
                // an existing reserved message ID, and stop searching
                isExists = true;
                break;
            }
        }

        return isExists;
    }

    /**********************************************************************************************
     * Add new reserved message IDs
     *
     * @param reservedMsgIDDefns
     *            list of reserved message ID definitions
     *********************************************************************************************/
    protected void updateReservedMsgIDs(List<String[]> reservedMsgIDDefns)
    {
        // Step through each imported reserved message ID definition
        for (String[] reservedMsgIDDefn : reservedMsgIDDefns)
        {
            // Check if the reserved message ID isn't already defined
            if (!isReservedMsgIDExists(reservedMsgIDDefn[ReservedMsgIDsColumn.MSG_ID.ordinal()]))
            {
                // Add the reserved message ID
                reservedMsgIDData.add(reservedMsgIDDefn);
            }
        }
    }
}
