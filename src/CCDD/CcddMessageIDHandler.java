/**************************************************************************************************
 * /** \file CcddMessageIDHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class used to determined which message IDs are currently used in tables, data fields, and
 * telemetry messages, and to determine if any references are duplicated.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.PROTECTED_MSG_ID_IDENT;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.MessageIDSortOrder;
import CCDD.CcddConstants.MsgIDListColumnIndex;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary message ID handler class
 *************************************************************************************************/
public class CcddMessageIDHandler
{
    // Class references
    private final CcddDbTableCommandHandler dbTable;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddRateParameterHandler rateHandler;

    // Lists of the names (with paths) of tables that represent structures, commands, and other
    // table types
    private List<String> structureTables;
    private List<String> commandTables;
    private List<String> otherTables;

    // List of message IDs that are reserved or are already assigned to a message
    private List<Integer> idsInUse;

    // List of message IDs that are used by multiple owners, and their owner
    private final ArrayListMultiple duplicates;

    // List of message IDs and their owners that are potential duplicates
    private final ArrayListMultiple potentialDuplicates;

    /**********************************************************************************************
     * Message ID handler class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddMessageIDHandler(CcddMain ccddMain)
    {
        // Create references to classes to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        rateHandler = ccddMain.getRateParameterHandler();

        // Create the lists
        idsInUse = new ArrayList<Integer>();
        duplicates = new ArrayListMultiple(1);
        potentialDuplicates = new ArrayListMultiple(1);
        structureTables = new ArrayList<String>();
        commandTables = new ArrayList<String>();
        otherTables = new ArrayList<String>();
    }

    /**********************************************************************************************
     * Get the list of tables that represent structures
     *
     * @return List of tables that represent structures
     *********************************************************************************************/
    protected List<String> getStructureTables()
    {
        return structureTables;
    }

    /**********************************************************************************************
     * Get the list of tables that represent commands
     *
     * @return List of tables that represent commands
     *********************************************************************************************/
    protected List<String> getCommandTables()
    {
        return commandTables;
    }

    /**********************************************************************************************
     * Get the list of tables that represent neither structures or commands
     *
     * @return List of tables that represent neither structures or commands
     *********************************************************************************************/
    protected List<String> getOtherTables()
    {
        return otherTables;
    }

    /**********************************************************************************************
     * Get the list of duplicate message IDs
     *
     * @return List of duplicate message IDS
     *********************************************************************************************/
    protected ArrayListMultiple getDuplicates()
    {
        return duplicates;
    }

    /**********************************************************************************************
     * Separate the message name and ID in the supplied string
     *
     * @param nameID Message name and ID in the format {@literal <}name{@literal >}
     *               {@literal <}ID{@literal >}. A null, blank input, or an input containing only
     *               the name or ID is accepted
     *
     * @return Array containing the message name and ID. The name, ID,or both the name and ID can
     *         be blank
     *********************************************************************************************/
    protected static String[] getMessageNameAndID(String nameID)
    {
        String[] nameAndID = new String[] {"", ""};

        // Check if the name and ID are provided
        if (nameID != null && !nameID.isEmpty())
        {
            // Separate the message name (if present) from the ID
            String[] parts = nameID.split("\\s+", 2);

            // Check if only a single value is present
            if (parts.length == 1)
            {
                // Check if the value is a hexadecimal
                if (parts[0].matches(DefaultInputType.HEXADECIMAL.getInputMatch()))
                {
                    // Store the ID
                    nameAndID[1] = parts[0];
                }
                // Not a hexadecimal; assume this is the name
                else
                {
                    // Store the name
                    nameAndID[0] = parts[0];
                }
            }
            // Check if both the name and ID are present
            else if (parts.length == 2)
            {
                // Store the name and ID
                nameAndID[0] = parts[0];
                nameAndID[1] = parts[1];
            }
        }

        return nameAndID;
    }

    /**********************************************************************************************
     * Get the message name from the message name and ID in the supplied string
     *
     * @param nameID Message name and ID in the format {@literal <}name{@literal >}
     *               {@literal <}ID{@literal >}. A blank input, or an input containing only the ID
     *               is accepted
     *
     * @return Message name from the supplied message name and ID (may be blank)
     *********************************************************************************************/
    protected static String getMessageName(String nameID)
    {
        return getMessageNameAndID(nameID)[0];
    }

    /**********************************************************************************************
     * Get the message ID from the message name and ID in the supplied string
     *
     * @param nameID Message name and ID in the format {@literal <}name{@literal >}
     *               {@literal <}ID{@literal >}. A blank input, or an input containing only the ID
     *               is accepted
     *
     * @return Message ID from the supplied message name and ID (may be blank)
     *********************************************************************************************/
    protected static String getMessageID(String nameID)
    {
        return getMessageNameAndID(nameID)[1];
    }

    /**********************************************************************************************
     * Create the list of message IDs that are reserved or are already in use
     *
     * @param includeStructures    True to include message IDs assigned to tables that represent
     *                             structures
     *
     * @param includeCommands      True to include message IDs assigned to tables that represent
     *                             commands
     *
     * @param includeOthers        True to include message IDs assigned to tables that do not
     *                             represent structures or commands
     *
     * @param includeGroups        True to include message IDs assigned to groups
     *
     * @param useTlmMsgIDsFromDb   True to include message IDs assigned to telemetry messages
     *                             stored in the project database; false to use the IDs from the
     *                             currently open telemetry scheduler
     *
     * @param isOverwriteTlmMsgIDs True to allow overwriting the telemetry message IDs for the
     *                             currently selected data stream in the open telemetry scheduler;
     *                             false to not allow overwriting. This value is only used if
     *                             useTlmMsgIDsFromDb is false
     *
     * @param tlmSchedulerDlg      Reference to the currently open telemetry scheduler. This value
     *                             is only used if useTlmMsgIDsFromDb is false, in which case it
     *                             can be set to null
     *
     * @param isGetDuplicates      True to create a list of duplicate IDs. The flags for including
     *                             tables and for using the telemetry message IDs from the database
     *                             should be set to true when getting the list of duplicates
     *
     * @param parent               GUI component over which to center any error dialog
     *
     * @return List of message IDs that are reserved or are already in use
     *********************************************************************************************/
    protected List<Integer> getMessageIDsInUse(boolean includeStructures,
                                               boolean includeCommands,
                                               boolean includeOthers,
                                               boolean includeGroups,
                                               boolean useTlmMsgIDsFromDb,
                                               boolean isOverwriteTlmMsgIDs,
                                               CcddTelemetrySchedulerDialog tlmSchedulerDlg,
                                               boolean isGetDuplicates,
                                               Component parent)
    {
        ArrayListMultiple tblAndFldMsgs = new ArrayListMultiple();

        // Empty the duplicates list in case this isn't the first execution of this method
        duplicates.clear();
        potentialDuplicates.clear();

        // Get the list of reserved message ID values
        idsInUse = rsvMsgIDHandler.getReservedMsgIDs();

        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Step through each column that contains a message name & ID
            for (int idColumn : typeDefn.getColumnIndicesByInputType(DefaultInputType.MESSAGE_NAME_AND_ID))
            {
                // Query the database for those values in the specified message ID column that are
                // in use in any table, including any references in the custom values table
                tblAndFldMsgs.addAll(dbTable.queryDatabase(new StringBuilder("SELECT").append((isGetDuplicates ? " "
                                                                                                               : " DISTINCT ON (2) "))
                                                                                      .append("* FROM find_columns_by_name('")
                                                                                      .append(typeDefn.getColumnNamesUser()[idColumn])
                                                                                      .append("', '")
                                                                                      .append(typeDefn.getColumnNamesDatabaseQuoted()[idColumn])
                                                                                      .append("', '{")
                                                                                      .append(typeDefn.getName())
                                                                                      .append("}');"), parent));
            }
        }

        // Get the list of all message name & ID data field values for project, group, or table
        // data fields (ignore fields assigned to table types)
        tblAndFldMsgs.addAll(dbTable.queryDatabase(new StringBuilder("SELECT").append((isGetDuplicates ? " "
                                                                                                       : " DISTINCT ON (2) "))
                                                                              .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                                              .append(", ")
                                                                              .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                                                              .append(" FROM ")
                                                                              .append(InternalTable.FIELDS.getTableName())
                                                                              .append(" WHERE ")
                                                                              .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                                                              .append(" = '")
                                                                              .append(DefaultInputType.MESSAGE_NAME_AND_ID.getInputName())
                                                                              .append("' AND ")
                                                                              .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                                                              .append(" != '' AND ")
                                                                              .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                                              .append(" !~ '")
                                                                              .append(TYPE_DATA_FIELD_IDENT)
                                                                              .append("';"), parent));

        // Get the list of tables representing structures
        structureTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE));

        // Get the list of tables representing commands
        commandTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_COMMAND));

        // Get the list of tables representing table types other than structures and commands
        otherTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_OTHER));

        // Step through each data field message ID
        for (String[] tblAndFldMsg : tblAndFldMsgs)
        {
            // Get the message owner
            String owner = tblAndFldMsg[0];

            // Get the message ID from the message name and ID
            String msgID = getMessageID(tblAndFldMsg[1]);

            // Replace any macro in the message ID with the corresponding text and format the ID as
            // hexadecimal
            msgID = CcddInputTypeHandler.formatInput(macroHandler.getMacroExpansion(msgID),
                                                     InputTypeFormat.HEXADECIMAL,
                                                     true);

            // Check if the message ID is flagged as protected, or the message ID data field is
            // assigned to a structure (command, other) table and the structure (command, other)
            // IDs are to be included
            if (msgID.endsWith(PROTECTED_MSG_ID_IDENT)
                || (includeStructures && structureTables.contains(TableInfo.getPrototypeName(owner)))
                || (includeCommands && commandTables.contains(owner))
                || (includeOthers && otherTables.contains(owner)))
            {
                // Get the IDs in use in the table cells and data fields, and update the duplicates
                // list (if the flag is set)
                updateUsageAndDuplicates("Table", new String[] {owner, msgID}, isGetDuplicates);
            }
            // Check if the message ID data field is assigned to a group and the group IDs are to
            // be included
            else if (includeGroups && owner.startsWith(GROUP_DATA_FIELD_IDENT))
            {
                // Get the IDs in use in the group data fields, and update the duplicates list (if
                // the flag is set)
                updateUsageAndDuplicates("Group", new String[] {owner, msgID}, isGetDuplicates);
            }
        }

        // Check if telemetry message IDs should be obtained from the database
        if (useTlmMsgIDsFromDb)
        {
            // Get the telemetry message IDs assigned in the telemetry scheduler table
            List<String[]> tlmIDs = dbTable.queryDatabase(new StringBuilder("SELECT DISTINCT ON (2) ").append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.RATE_NAME.ordinal()))
                                                                                                      .append(" || ', ' || ")
                                                                                                      .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal()))
                                                                                                      .append(", ")
                                                                                                      .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_ID.ordinal()))
                                                                                                      .append(" FROM ")
                                                                                                      .append(InternalTable.TLM_SCHEDULER.getTableName())
                                                                                                      .append(" WHERE ")
                                                                                                      .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_ID.ordinal()))
                                                                                                      .append(" != '' AND ")
                                                                                                      .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal()))
                                                                                                      .append(" !~ E'^.+\\\\..*$';"), parent);

            // Step through each telemetry message ID
            for (String[] tlmMsgNameAndID : tlmIDs)
            {
                // Check if the list of duplicate message IDs is to be created
                if (isGetDuplicates)
                {
                    // Replace the rate name with its corresponding stream name when displaying
                    // duplicate IDs
                    String rateName = tlmMsgNameAndID[0].replaceFirst(",.*", "");
                    String streamName = rateHandler.getRateInformationByRateName(rateName).getStreamName();
                    tlmMsgNameAndID[0] = tlmMsgNameAndID[0].replaceFirst(rateName, streamName);
                }

                // Update the IDs in use in the telemetry messages, and update the duplicates list
                // (if the flag is set)
                updateUsageAndDuplicates("Message", tlmMsgNameAndID, isGetDuplicates);
            }
        }
        // Get the telemetry message IDs from the telemetry scheduler if it's open. This is used in
        // place of the IDs stored in the database since the user may have modified the IDs in the
        // telemetry scheduler but not yet stored them to the database
        else if (tlmSchedulerDlg != null)
        {
            // Step through each data stream
            for (CcddSchedulerHandler schHndlr : tlmSchedulerDlg.getSchedulerHandlers())
            {
                // Check if this isn't the currently selected data stream, of if it is that the
                // overwrite check box is not selected
                if (!schHndlr.equals(tlmSchedulerDlg.getSchedulerHandler()) || !isOverwriteTlmMsgIDs)
                {
                    // Step through each message for this data stream
                    for (Message message : schHndlr.getSchedulerEditor().getCurrentMessages())
                    {
                        // Check if the message has an ID
                        if (!message.getID().isEmpty())
                        {
                            // Add the message ID to the list of existing ID values
                            idsInUse.add(Integer.decode(message.getID()));
                        }

                        // Step through each of the message's sub-messages
                        for (Message subMessage : message.getSubMessages())
                        {
                            // Check if the sub-message has an ID
                            if (!subMessage.getID().isEmpty())
                            {
                                // Add the sub-message ID to the list of existing ID values
                                idsInUse.add(Integer.decode(subMessage.getID()));
                            }
                        }
                    }
                }
            }
        }

        return idsInUse;
    }

    /**********************************************************************************************
     * Get the message ID, minus the auto-assign protection flag (if present), from the supplied
     * message ID
     *
     * @param msgID Message ID
     *
     * @return Message ID, minus the auto-assign protection flag (if present)
     *********************************************************************************************/
    protected static String removeProtectionFlag(String msgID)
    {
        return msgID.replaceFirst("\\s*" + PROTECTED_MSG_ID_IDENT, "");
    }

    /**********************************************************************************************
     * Get the list containing every message owner, name, and ID from every table cell, data field
     * (table or group), and telemetry message. Message names and IDs are determined by the input
     * type assigned to the table column or data field
     *
     * @param sortOrder          Order in which to sort the message ID list: BY_OWNER or BY_NAME
     *                           (MessageIDSortOrder)
     *
     * @param hideProtectionFlag True to not display the flag character that protects a message ID
     *                           from being changed by the auto-update methods; false to allow the
     *                           flag to remain
     *
     * @param parent             GUI component over which to center any error dialog
     *
     * @return List containing every message name and its corresponding message ID, and the owning
     *         entity
     *********************************************************************************************/
    protected List<String[]> getMessageOwnersNamesAndIDs(MessageIDSortOrder sortOrder,
                                                         boolean hideProtectionFlag,
                                                         Component parent)
    {
        ArrayListMultiple ownersNamesAndIDs = new ArrayListMultiple();
        ArrayListMultiple tblAndFldMsgs = new ArrayListMultiple();

        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Step through each column that contains a message name & ID
            for (int idColumn : typeDefn.getColumnIndicesByInputType(DefaultInputType.MESSAGE_NAME_AND_ID))
            {
                // Query the database for those values in the specified message ID name column that
                // are in use in any table, including any references in the custom values table
                tblAndFldMsgs.addAll(dbTable.queryDatabase(new StringBuilder("SELECT ").append("* FROM find_columns_by_name('")
                                                                                       .append(typeDefn.getColumnNamesUser()[idColumn])
                                                                                       .append("', '")
                                                                                       .append(typeDefn.getColumnNamesDatabaseQuoted()[idColumn])
                                                                                       .append("', '{")
                                                                                       .append(typeDefn.getName())
                                                                                       .append("}');"),
                                                                                       parent));
            }
        }

        // Get the list of all message name & ID data field values for project, group, or table
        // data fields (ignore fields assigned to table types)
        tblAndFldMsgs.addAll(dbTable.queryDatabase(new StringBuilder("SELECT ").append(FieldsColumn.OWNER_NAME.getColumnName())
                                                                               .append(", ")
                                                                               .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                                                               .append(" FROM ")
                                                                               .append(InternalTable.FIELDS.getTableName())
                                                                               .append(" WHERE ")
                                                                               .append(FieldsColumn.FIELD_TYPE.getColumnName())
                                                                               .append(" = '")
                                                                               .append(DefaultInputType.MESSAGE_NAME_AND_ID.getInputName())
                                                                               .append("' AND ")
                                                                               .append(FieldsColumn.FIELD_VALUE.getColumnName())
                                                                               .append(" != '' AND ")
                                                                               .append(FieldsColumn.OWNER_NAME.getColumnName())
                                                                               .append(" !~ '")
                                                                               .append(TYPE_DATA_FIELD_IDENT)
                                                                               .append("' ORDER BY ")
                                                                               .append(FieldsColumn.ROW_NUM.getColumnName())
                                                                               .append(";"),
                                                                               parent));

        // Step through each message name/ID belonging to a table cell or data field
        for (String[] tblAndFldMsg : tblAndFldMsgs)
        {
            // Get the message owner
            String owner = tblAndFldMsg[0];

            // Separate the message name (if present) from the ID
            String[] nameAndID = getMessageNameAndID(tblAndFldMsg[1]);

            // Replace any macro in the message ID with the corresponding text and format the ID as
            // hexadecimal
            nameAndID[1] = CcddInputTypeHandler.formatInput(macroHandler.getMacroExpansion(nameAndID[1]),
                                                            InputTypeFormat.HEXADECIMAL,
true);

            // Add the message owner, name, and ID to the list
            ownersNamesAndIDs.add(new String[] {owner, nameAndID[0], nameAndID[1]});
        }

        // Get the telemetry rates, message ID names, and IDs assigned in the telemetry scheduler
        // table. This query returns only those the message names with the sub-message index
        // appended, so for parent messages without any sub-messages this retrieves the 'default'
        // sub-message name
        ArrayListMultiple tlmMsgs = new ArrayListMultiple(1);
        tlmMsgs.addAll(dbTable.queryDatabase(new StringBuilder("SELECT DISTINCT ON (3,2) 'Tlm:' || ").append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.RATE_NAME.ordinal()))
                                                                                                     .append(", regexp_replace(")
                                                                                                     .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal()))
                                                                                                     .append(", E'\\\\.', '_'), ")
                                                                                                     .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_ID.ordinal()))
                                                                                                     .append(" FROM ")
                                                                                                     .append(InternalTable.TLM_SCHEDULER.getTableName())
                                                                                                     .append(" WHERE ")
                                                                                                     .append(InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal()))
                                                                                                     .append(" ~ E'\\\\.';"),
                                                                                                     parent));

        // Step through each of the telemetry messages retrieved
        for (String[] tlmMsg : tlmMsgs)
        {
            // Check if this message has the default sub-message name
            if (tlmMsg[1].endsWith("_0"))
            {
                // Get the parent message name
                String parentMsg = tlmMsg[1].substring(0, tlmMsg[1].length() - 2);

                // Check if the list of messages does not include the second sub-message. This
                // indicates that the parent has no 'real' sub-messages
                if (!tlmMsgs.contains((Object) (parentMsg + "_1")))
                {
                    // Store the parent message name in place of the default sub-message name
                    tlmMsg[1] = parentMsg;
                }
            }
        }

        // Add the processed telemetry message to the list
        ownersNamesAndIDs.addAll(tlmMsgs);

        // Sort the message ID list in the order specified
        switch (sortOrder)
        {
            case BY_OWNER:
                // Sort the message ID list by owner (then ID, then name)
                ownersNamesAndIDs.setComparisonColumn(MsgIDListColumnIndex.OWNER.ordinal(),
                                                      MsgIDListColumnIndex.MESSAGE_ID.ordinal(),
                                                      MsgIDListColumnIndex.MESSAGE_NAME.ordinal());
                ownersNamesAndIDs.sort(ArrayListMultipleSortType.STRING);
                break;

            case BY_NAME:
                // Sort the message ID list by ID name (then ID, then owner)
                ownersNamesAndIDs.setComparisonColumn(MsgIDListColumnIndex.MESSAGE_NAME.ordinal(),
                                                      MsgIDListColumnIndex.MESSAGE_ID.ordinal(),
                                                      MsgIDListColumnIndex.OWNER.ordinal());
                ownersNamesAndIDs.sort(ArrayListMultipleSortType.STRING);
                break;
        }

        return ownersNamesAndIDs;
    }

    /**********************************************************************************************
     * Update the list of message IDs in use and, based on the input flag, update the duplicate IDs
     * list
     *
     * @param ownerType       Message ID owner type (Table or Message)
     *
     * @param ownerAndID      Array where the first member is the owner (table name or telemetry
     *                        message name) and the second element is the message ID
     *
     * @param isGetDuplicates True to create a list of duplicate IDs
     *********************************************************************************************/
    private void updateUsageAndDuplicates(String ownerType,
                                          String[] ownerAndID,
                                          boolean isGetDuplicates)
    {
        // Convert the message ID from a hexadecimal string to an integer. Remove the protection
        // flag if present so that the ID can be converted to an integer
        int msgID = Integer.decode(ownerAndID[1].replaceFirst("\\s*" + PROTECTED_MSG_ID_IDENT, ""));

        // Check if the list of duplicate message IDs is to be created
        if (isGetDuplicates)
        {
            // Prepend the owner type to the owner name and reformat the message ID to remove extra
            // leading zeroes
            ownerAndID[0] = ownerType + ": " + ownerAndID[0].replaceFirst(".*:", "");
            ownerAndID[1] = "0x" + Integer.toHexString(msgID);
        }

        // Check the message ID isn't already in the list
        if (!idsInUse.contains(msgID))
        {
            // Add the ID value to the list of those in use
            idsInUse.add(msgID);

            // Check if the list of duplicate message IDs is to be created
            if (isGetDuplicates)
            {
                // Add the ID to the list of potential duplicates. This is used to get the ID's
                // owner if a duplicate of this ID is later detected
                potentialDuplicates.add(ownerAndID);
            }
        }
        // The message ID is already in the list; check if the list of duplicate message IDs is to
        // be created
        else if (isGetDuplicates)
        {
            // Get the index of the owner and ID pair with a matching message ID, if one exists
            int index = duplicates.indexOf((Object) ownerAndID[1]);

            // Check if this ID isn't already in the list
            if (index == -1)
            {
                // Get the index of the ID in the list of potential duplicates
                int pdIndex = potentialDuplicates.indexOf((Object) ownerAndID[1]);

                if (pdIndex != -1)
                {
                    // Add the owner and ID of the first occurrence of this ID to the duplicates
                    // list
                    duplicates.add(potentialDuplicates.get(pdIndex));

                    // Check if the owner of this occurrence of the ID differs from the owner of
                    // the first occurrence
                    if (!ownerAndID[0].equals(potentialDuplicates.get(pdIndex)[0]))
                    {
                        // Append this owner to the other owner(s) of this duplicate ID
                        duplicates.get(duplicates.size() - 1)[0] += "\n" + ownerAndID[0];
                    }
                }

            }
            // The ID is already in the list of duplicates; check if this owner isn't already
            // included
            else if (!duplicates.get(index)[0].matches("(?:^|.*\\\n)" + ownerAndID[0] + "(?:\\\n.*|$)"))
            {
                // Append the owner to the existing entry for this message ID
                duplicates.get(index)[0] += "\n" + ownerAndID[0];
            }
        }
    }
}
