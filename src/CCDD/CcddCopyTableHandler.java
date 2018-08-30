/**
 * CFS Command and Data Dictionary housekeeping copy table handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddConstants.CopyTableEntry;
import CCDD.CcddConstants.SchedulerType;

/**************************************************************************************************
 * CFS Command and Data Dictionary housekeeping copy table handler class
 *************************************************************************************************/
public class CcddCopyTableHandler
{
    // Class references
    private final CcddFieldHandler fieldHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddSchedulerDbIOHandler schedulerDb;
    private final CcddMacroHandler macroHandler;
    private final CcddVariableHandler variableHandler;

    // List of copy table entries
    private final List<String[]> copyTable;

    /**********************************************************************************************
     * Housekeeping copy table handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddCopyTableHandler(CcddMain ccddMain)
    {
        fieldHandler = ccddMain.getFieldHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        schedulerDb = new CcddSchedulerDbIOHandler(ccddMain,
                                                   SchedulerType.TELEMETRY_SCHEDULER,
                                                   null);
        macroHandler = ccddMain.getMacroHandler();
        variableHandler = ccddMain.getVariableHandler();

        copyTable = new ArrayList<String[]>();

        // Load the telemetry scheduler information from the project database
        schedulerDb.loadStoredData();
    }

    /**********************************************************************************************
     * Create a copy table based on the message definitions
     *
     * @param linkHandler
     *            link handler reference
     *
     * @param dataStreamName
     *            data stream name
     *
     * @param headerSize
     *            size of the packet header in bytes
     *
     * @param messageIDNameField
     *            name of the structure table data field containing the message ID name. If
     *            provided this is used instead of the tlmMessageIDs list
     *
     * @param tlmMessageIDs
     *            list containing string array entries giving the structure table path+name and the
     *            table's associated message ID name. Used if messageIDNameField is null
     *
     * @param optimize
     *            true to create copy table with memory copies optimized
     *
     * @param expandMacros
     *            true to expand any macro within the variable names
     *
     * @return Array containing the copy table entries
     *********************************************************************************************/
    protected String[][] createCopyTable(CcddLinkHandler linkHandler,
                                         String dataStreamName,
                                         int headerSize,
                                         String messageIDNameField,
                                         ArrayListMultiple tlmMessageIDs,
                                         boolean optimize,
                                         boolean expandMacros)
    {
        List<String[]> messageTable = new ArrayList<String[]>();

        // Empty the copy table in case a previous one exists
        copyTable.clear();

        // Step through each message for the specified rate
        for (Message message : getStoredMessages(dataStreamName))
        {
            // Step through the message's sub-messages
            for (Message subMsg : message.getSubMessages())
            {
                // Step through each packet definition
                for (Variable variable : subMsg.getVariablesWithParent())
                {
                    String tlmMsgID = null;

                    // Split the packet definition's variable string into the parent structure name
                    // and variable reference string
                    String[] parentAndPath = variable.getFullName().split(",", 2);

                    // Check if macro in the variable names are to be expanded
                    if (expandMacros)
                    {
                        // Replace any macros with their corresponding values
                        parentAndPath[1] = macroHandler.getMacroExpansion(parentAndPath[1]);
                    }

                    // Get the offset in the root structure of the variable indicated by the packet
                    // definition
                    int structureOffset = variableHandler.getVariableOffset(variable.getFullName());

                    // Check if the message ID name field name is provided
                    if (messageIDNameField != null)
                    {
                        // Get the field information for the message ID name field
                        FieldInformation msgIDNameFieldInfo = fieldHandler.getFieldInformationByName(parentAndPath[0],
                                                                                                     messageIDNameField);

                        // Check that the message ID name field exists for the specified table
                        if (msgIDNameFieldInfo != null)
                        {
                            // Get the message ID name associated with the table
                            tlmMsgID = msgIDNameFieldInfo.getValue();
                        }
                    }
                    // Check if the telemetry message ID names list is provided
                    else if (tlmMessageIDs != null)
                    {
                        // Get the index of the table in the list provided
                        int index = tlmMessageIDs.indexOf(parentAndPath[0]);

                        // Check if the table exists in the list
                        if (index != -1)
                        {
                            // Get the message ID name associated with the table
                            tlmMsgID = tlmMessageIDs.get(index)[1];
                        }
                    }

                    if (tlmMsgID != null)
                    {
                        // Build the copy table entry array for this variable. The fields are:
                        // Input message ID name, input offset, output message ID name (the
                        // sub-message separator character, a period, is replaced with an
                        // underscore), output offset (initialized to a blank; the value is
                        // computed later), variable size, variable root table, and variable path
                        messageTable.add(new String[] {tlmMsgID,
                                                       String.valueOf(structureOffset),
                                                       subMsg.getName().replace(".", "_"),
                                                       "",
                                                       String.valueOf(variable.getSize()),
                                                       parentAndPath[0],
                                                       parentAndPath[1]});
                    }
                }

                // Consolidate the bit-packed variables
                combineBitPackedVariables(messageTable);

                // Check if this copy table should be optimized
                if (optimize)
                {
                    // Combine consecutive memory copies
                    combineMemoryCopies(messageTable);
                }

                // Add the input and output offset to the list
                addInputAndOutputOffset(messageTable, headerSize);

                // Add this message's copy table entries to the list of all copy table entries
                copyTable.addAll(messageTable);

                // Clear out this message's entries to allow storage for the next message
                messageTable.clear();
            }
        }

        return copyTable.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the messages ID names and their corresponding ID values for the specified data stream
     *
     * @param streamName
     *            data stream name
     *
     * @return String array containing the message ID names and ID values; returns blank if there
     *         are no entries for the specified data stream or if data stream name is invalid
     *********************************************************************************************/
    protected String[][] getTelemetryMessageIDs(String streamName)
    {
        List<String[]> messageIDs = new ArrayList<String[]>();

        // Step through each message for the specified rate
        for (Message message : getStoredMessages(streamName))
        {
            // Check if the message ID name and ID value are not blank
            if (!message.getName().isEmpty() && !message.getID().isEmpty())
            {
                // Check if this parent message has no sub-messages (other than the default one)
                if (message.getNumberOfSubMessages() <= 1)
                {
                    // Add the message ID name and ID value to the list
                    messageIDs.add(new String[] {message.getName(), message.getID()});
                }
                // This parent has multiple sub-messages
                else
                {
                    // Step through each sub-message
                    for (Message subMsg : message.getSubMessages())
                    {
                        // Check if the sub-message ID name and ID value are not blank
                        if (!subMsg.getName().isEmpty() && !subMsg.getID().isEmpty())
                        {
                            // Add the sub-message ID name and ID value to the list. Replace the
                            // period in the sub-message name with an underscore so that it is a
                            // valid name
                            messageIDs.add(new String[] {subMsg.getName().replaceFirst("\\.", "_"),
                                                         subMsg.getID()});
                        }
                    }
                }
            }
        }

        return messageIDs.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get messages for the specified rate from the project database
     *
     * @param streamName
     *            data stream name
     *
     * @return List of messages for the specified rate
     *********************************************************************************************/
    private List<Message> getStoredMessages(String streamName)
    {
        // Get the index for the specified rate
        int rateIndex = rateHandler.getRateInformationIndexByStreamName(streamName);

        // Get the messages for the specified rate from project database
        return schedulerDb.getStoredData(rateIndex);
    }

    /**********************************************************************************************
     * Remove bit-packed variables, other than the leading one, from the specified message's copy
     * table entries
     *
     * @param messageTable
     *            message copy table
     *********************************************************************************************/
    private void combineBitPackedVariables(List<String[]> messageTable)
    {
        List<String[]> removedVars = new ArrayList<String[]>();
        String[] initial = null;

        // Step through the message's copy table entries
        for (String[] current : messageTable)
        {
            // Check if this variable is bit-packed with the previous one. This is indicated by the
            // variable containing a bit length, and by having matching root structures and input
            // offsets
            if (initial != null
                && current[CopyTableEntry.VARIABLE_NAME.ordinal()].contains(":")
                && current[CopyTableEntry.INPUT_OFFSET.ordinal()].equals(initial[CopyTableEntry.INPUT_OFFSET.ordinal()])
                && current[CopyTableEntry.VARIABLE_ROOT.ordinal()].equals(initial[CopyTableEntry.VARIABLE_ROOT.ordinal()]))
            {
                // Append the subsequent packed variable's name to the initial packed variable's
                // name
                initial[CopyTableEntry.VARIABLE_NAME.ordinal()] += " + "
                                                                   + current[CopyTableEntry.VARIABLE_NAME.ordinal()];

                // Add the subsequent bit-packed variable to the list of variables to remove from
                // the copy table
                removedVars.add(current);
            }
            // This is the first entry or else the initial and current entries offsets and root
            // structures don't match
            else
            {
                // Save the current entry as the potential head of the bit-packed variables
                initial = current;
            }
        }

        // Remove the flagged bit-packed variables from the message's copy table
        messageTable.removeAll(removedVars);
    }

    /**********************************************************************************************
     * Optimize the copy table by combining consecutive memory copies
     *
     * @param messageTable
     *            message copy table
     *********************************************************************************************/
    private void combineMemoryCopies(List<String[]> messageTable)
    {
        List<String[]> removedVars = new ArrayList<String[]>();
        String[] initial = null;

        // Step through the message's copy table entries
        for (String[] current : messageTable)
        {
            // Check if this variable follows in the same structure immediately after the previous
            // entry's variable
            if (initial != null
                && current[CopyTableEntry.VARIABLE_ROOT.ordinal()].equals(initial[CopyTableEntry.VARIABLE_ROOT.ordinal()])
                && Integer.valueOf(current[CopyTableEntry.INPUT_OFFSET.ordinal()]) == Integer.valueOf(initial[CopyTableEntry.INPUT_OFFSET.ordinal()])
                                                                                      + Integer.valueOf(initial[CopyTableEntry.VARIABLE_BYTES.ordinal()]))
            {
                // Add the size in bytes of the current variable to the initial one
                initial[CopyTableEntry.VARIABLE_BYTES.ordinal()] = String.valueOf(Integer.valueOf(initial[CopyTableEntry.VARIABLE_BYTES.ordinal()])
                                                                                  + Integer.valueOf(current[CopyTableEntry.VARIABLE_BYTES.ordinal()]));

                // Append the subsequent variable's name to the initial variable's name
                initial[CopyTableEntry.VARIABLE_NAME.ordinal()] = initial[CopyTableEntry.VARIABLE_NAME.ordinal()]
                                                                  + "; "
                                                                  + current[CopyTableEntry.VARIABLE_NAME.ordinal()];

                // Add the subsequent variable to the list of variables to remove from the copy
                // table
                removedVars.add(current);
            }
            // This is the first entry, or else the initial and current entries root structures
            // don't match or the variables are not consecutive
            else
            {
                // Save the current entry as the potential head of consecutive variables
                initial = current;
            }
        }

        // Remove the flagged combined variables from the message's copy table
        messageTable.removeAll(removedVars);
    }

    /**********************************************************************************************
     * Add the input and output structure offsets to the specified message's copy table entries
     *
     * @param headerSize
     *            message header size, bytes
     *
     * @param messageTable
     *            message copy table
     *********************************************************************************************/
    private void addInputAndOutputOffset(List<String[]> messageTable, int headerSize)
    {
        // Initialize the offset to the header size
        int offset = headerSize;

        // Step through each entry in the list of this message's copy table entries
        for (String[] entry : messageTable)
        {
            // Add the header size to the message's entries input location
            entry[CopyTableEntry.INPUT_OFFSET.ordinal()] = String.valueOf(headerSize
                                                                          + Integer.valueOf(entry[CopyTableEntry.INPUT_OFFSET.ordinal()]));

            // Add the output offset to the message's entries output location
            entry[CopyTableEntry.OUTPUT_OFFSET.ordinal()] = String.valueOf(offset);

            // Add the size of this data type to the packet offset
            offset += Integer.valueOf(entry[CopyTableEntry.VARIABLE_BYTES.ordinal()]);
        }
    }
}
