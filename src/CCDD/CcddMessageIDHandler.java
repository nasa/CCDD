/**
 * CFS Command & Data Dictionary message ID handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;

import CCDD.CcddClasses.ArrayListMultiple;
import CCDD.CcddClasses.Message;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.DuplicateMsgIDColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************
 * CFS Command & Data Dictionary message ID handler class
 * 
 * @param ccddMain
 *            main class
 *************************************************************************/
public class CcddMessageIDHandler
{
    // Class references
    private final CcddDbTableCommandHandler dbTable;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddRateParameterHandler rateHandler;

    // Lists of the names (with paths) of tables that represent structures,
    // commands, and other table types
    private List<String> structureTables;
    private List<String> commandTables;
    private List<String> otherTables;

    // List of message IDs that are reserved or are already assigned to a
    // message
    private List<Integer> idsInUse;

    // List of message IDs that are used by multiple owners, and their owner
    private final ArrayListMultiple duplicates;

    // List of message IDs and their owners that are potential duplicates
    private final ArrayListMultiple potentialDuplicates;

    /**************************************************************************
     * Message ID handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddMessageIDHandler(CcddMain ccddMain)
    {
        // Create references to classes to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        rateHandler = ccddMain.getRateParameterHandler();

        // Create the lists
        idsInUse = new ArrayList<Integer>();
        duplicates = new ArrayListMultiple(1);
        potentialDuplicates = new ArrayListMultiple(1);
        structureTables = new ArrayList<String>();
        commandTables = new ArrayList<String>();
        otherTables = new ArrayList<String>();
    }

    /**************************************************************************
     * Get the list of tables that represent structures
     * 
     * @return List of tables that represent structures
     *************************************************************************/
    protected List<String> getStructureTables()
    {
        return structureTables;
    }

    /**************************************************************************
     * Get the list of tables that represent commands
     * 
     * @return List of tables that represent commands
     *************************************************************************/
    protected List<String> getCommandTables()
    {
        return commandTables;
    }

    /**************************************************************************
     * Get the list of tables that represent neither structures or commands
     * 
     * @return List of tables that represent neither structures or commands
     *************************************************************************/
    protected List<String> getOtherTables()
    {
        return otherTables;
    }

    /**************************************************************************
     * Create the list of message IDs that are reserved or are already in use
     * 
     * @param includeStructures
     *            true to include message IDs assigned to tables that represent
     *            structures
     * 
     * @param includeCommands
     *            true to include message IDs assigned to tables that represent
     *            commands
     * 
     * @param includeOthers
     *            true to include message IDs assigned to tables that do not
     *            represent structures or commands
     * 
     * @param includeGroups
     *            true to include message IDs assigned to groups
     * 
     * @param useTlmMsgIDsFromDb
     *            true to include message IDs assigned to telemetry messages
     *            stored in the project database; false to use the IDs from the
     *            currently open telemetry scheduler
     * 
     * @param isOverwriteTlmMsgIDs
     *            true to allow overwriting the telemetry message IDs for the
     *            currently selected data stream in the open telemetry
     *            scheduler; false to not allow overwriting. This value is only
     *            used if useTlmMsgIDsFromDb is false
     * 
     * @param tlmSchedulerDlg
     *            Reference to the currently open telemetry scheduler. This
     *            value is only used if useTlmMsgIDsFromDb is false, in which
     *            case it can be set to null
     * 
     * @param isGetDuplicates
     *            true to create a list of duplicate IDs. The flags for
     *            including tables and for using the telemetry message IDs from
     *            the database should be set to true when getting the list of
     *            duplicates
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
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
        List<String[]> tableIDs = new ArrayList<String[]>();

        // Empty the duplicates list in case this isn't the first execution of
        // this method
        duplicates.clear();
        potentialDuplicates.clear();

        // Get the list of reserved message ID values
        idsInUse = rsvMsgIDHandler.getReservedMsgIDs();

        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Step through each column that contains message IDs
            for (int idColumn : typeDefn.getColumnIndicesByInputType(InputDataType.MESSAGE_ID))
            {
                // Query the database for those values in the specified message
                // ID column that are in use in any table, including any
                // references in the custom values table
                tableIDs.addAll(dbTable.queryDatabase("SELECT"
                                                      + (isGetDuplicates
                                                                        ? " "
                                                                        : " DISTINCT ON (2) ")
                                                      + "* FROM find_columns_by_name('"
                                                      + typeDefn.getColumnNamesUser()[idColumn]
                                                      + "', '"
                                                      + typeDefn.getColumnNamesDatabase()[idColumn]
                                                      + "', '{"
                                                      + typeDefn.getName()
                                                      + "}');",
                                                      parent));
            }
        }

        // Get the list of all message ID data field values
        tableIDs.addAll(dbTable.queryDatabase("SELECT"
                                              + (isGetDuplicates
                                                                ? " "
                                                                : " DISTINCT ON (2) ")
                                              + InternalTable.FIELDS.getColumnName(FieldsColumn.OWNER_NAME.ordinal())
                                              + ", "
                                              + InternalTable.FIELDS.getColumnName(FieldsColumn.FIELD_VALUE.ordinal())
                                              + " FROM "
                                              + InternalTable.FIELDS.getTableName()
                                              + " WHERE "
                                              + InternalTable.FIELDS.getColumnName(FieldsColumn.FIELD_TYPE.ordinal())
                                              + " = '"
                                              + InputDataType.MESSAGE_ID.getInputName()
                                              + "' AND "
                                              + InternalTable.FIELDS.getColumnName(FieldsColumn.FIELD_VALUE.ordinal())
                                              + " != '';",
                                              parent));

        // Get the list of tables representing structures
        structureTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE));

        // Get the list of tables representing commands
        commandTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_COMMAND));

        // Get the list of tables representing table types other than
        // structures and commands
        otherTables = Arrays.asList(dbTable.getPrototypeTablesOfType(TYPE_OTHER));

        // Step through each data field message ID
        for (String[] tableOwnerAndID : tableIDs)
        {
            // Replace any macro in the message ID with the corresponding text
            tableOwnerAndID[1] = macroHandler.getMacroExpansion(tableOwnerAndID[1]);

            // Check if the message ID data field is assigned to a structure
            // (command, other) table and the structure (command, other) IDs
            // are to be included, and that the ID is not already in the list
            if (includeStructures && structureTables.contains(tableOwnerAndID[0])
                || includeCommands && commandTables.contains(tableOwnerAndID[0])
                || includeOthers && otherTables.contains(tableOwnerAndID[0]))
            {
                // Get the IDs in use in the table cells and data fields, and
                // update the duplicates list (if the flag is set)
                updateUsageAndDuplicates("Table", tableOwnerAndID, isGetDuplicates);
            }
            // Check if the message ID data field is assigned to a group and
            // the group IDs are to be included, and that the ID is not already
            // in the list
            else if (includeGroups && tableOwnerAndID[0].startsWith(GROUP_DATA_FIELD_IDENT))
            {
                // Get the IDs in use in the group data fields, and update the
                // duplicates list (if the flag is set)
                updateUsageAndDuplicates("Group", tableOwnerAndID, isGetDuplicates);
            }
        }

        // Check if telemetry message IDs should be obtained from the database
        if (useTlmMsgIDsFromDb)
        {
            // Get the telemetry message IDs assigned in the telemetry
            // scheduler table
            List<String[]> tlmIDs = dbTable.queryDatabase("SELECT DISTINCT ON (2) "
                                                          + InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.RATE_NAME.ordinal())
                                                          + " || ', ' || "
                                                          + InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal())
                                                          + ", "
                                                          + InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_ID.ordinal())
                                                          + " FROM "
                                                          + InternalTable.TLM_SCHEDULER.getTableName()
                                                          + " WHERE "
                                                          + InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_ID.ordinal())
                                                          + " != '' AND "
                                                          + InternalTable.TLM_SCHEDULER.getColumnName(TlmSchedulerColumn.MESSAGE_NAME.ordinal())
                                                          + " !~ E'^.+\\\\..*$';",
                                                          parent);

            // Step through each telemetry message ID
            for (String[] tlmMsgNameAndID : tlmIDs)
            {
                // Check if the list of duplicate message IDs is to be created
                if (isGetDuplicates)
                {
                    // Replace the rate name with its corresponding stream name
                    // when displaying duplicate IDs
                    String rateName = tlmMsgNameAndID[0].replaceFirst(",.*", "");
                    String streamName = rateHandler.getRateInformationByRateName(rateName).getStreamName();
                    tlmMsgNameAndID[0] = tlmMsgNameAndID[0].replaceFirst(rateName, streamName);
                }

                // Update the IDs in use in the telemetry messages, and update
                // the duplicates list (if the flag is set)
                updateUsageAndDuplicates("Message",
                                         tlmMsgNameAndID,
                                         isGetDuplicates);
            }
        }
        // Get the telemetry message IDs from the telemetry scheduler if it's
        // open. This is used in place of the IDs stored in the database since
        // the user may have modified the IDs in the telemetry scheduler but
        // not yet stored them to the database
        else if (tlmSchedulerDlg != null)
        {
            // Step through each data stream
            for (CcddSchedulerHandler schHndlr : tlmSchedulerDlg.getSchedulerHandlers())
            {
                // Check if this isn't the currently selected data stream, of
                // if it is that the overwrite check box is not selected
                if (!schHndlr.equals(tlmSchedulerDlg.getSchedulerHandler())
                    || !isOverwriteTlmMsgIDs)
                {
                    // Step through each message for this data stream
                    for (Message message : schHndlr.getSchedulerEditor().getCurrentMessages())
                    {
                        // Check if the message has an ID
                        if (!message.getID().isEmpty())
                        {
                            // Add the message ID to the list of existing ID
                            // values
                            idsInUse.add(Integer.decode(message.getID()));
                        }

                        // Step through each of the message's sub-messages
                        for (Message subMessage : message.getSubMessages())
                        {
                            // Check if the sub-message has an ID
                            if (!subMessage.getID().isEmpty())
                            {
                                // Add the sub-message ID to the list of
                                // existing ID values
                                idsInUse.add(Integer.decode(subMessage.getID()));
                            }
                        }
                    }
                }
            }
        }

        return idsInUse;
    }

    /**************************************************************************
     * Update the list of message IDs in use and, based on the input flag,
     * update the duplicate IDs list
     * 
     * @param ownerType
     *            message ID owner type (Table or Message)
     * 
     * @param ownerAndID
     *            array where the first member is the owner (table name or
     *            telemetry message name) and the second element is the message
     *            ID
     * 
     * @param isGetDuplicates
     *            true to create a list of duplicate IDs
     *************************************************************************/
    private void updateUsageAndDuplicates(String ownerType,
                                          String[] ownerAndID,
                                          boolean isGetDuplicates)
    {
        // Convert the message ID from a hexadecimal string to an integer
        int msgID = Integer.decode(ownerAndID[1]);

        // Check if the list of duplicate message IDs is to be created
        if (isGetDuplicates)
        {
            // Prepend the owner type to the owner name and reformat the
            // message ID to remove extra leading zeroes
            ownerAndID[0] = ownerType + ": " + ownerAndID[0];
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
                // Add the ID to the list of potential duplicates. This is used
                // to get the ID's owner if a duplicate of this ID is later
                // detected
                potentialDuplicates.add(ownerAndID);
            }
        }
        // The message ID is already in the list; check if the list of
        // duplicate message IDs is to be created
        else if (isGetDuplicates)
        {
            // Get the index of the owner and ID pair with a matching message
            // ID, if one exists
            int index = duplicates.indexOf(ownerAndID[1]);

            // Check if this ID isn't already in the list
            if (index == -1)
            {
                // Get the index of the ID in the list of potential duplicates
                int pdIndex = potentialDuplicates.indexOf(ownerAndID[1]);

                // Add the owner and ID of the first occurrence of this ID to
                // the duplicates list
                duplicates.add(potentialDuplicates.get(pdIndex));

                // Check if the owner of this occurrence of the ID differs from
                // the owner of the first occurrence
                if (!ownerAndID[0].equals(potentialDuplicates.get(pdIndex)[0]))
                {
                    // Append this owner to the other owner(s) of this
                    // duplicate ID
                    duplicates.get(duplicates.size() - 1)[0] += "\n" + ownerAndID[0];
                }
            }
            // The ID is already in the list of duplicates; check if this owner
            // isn't already included
            else if (!duplicates.get(index)[0].matches("(?:^|.*\\\n)"
                                                       + ownerAndID[0]
                                                       + "(?:\\\n.*|$)"))
            {
                // Append the owner to the existing entry for this message ID
                duplicates.get(index)[0] += "\n" + ownerAndID[0];
            }
        }
    }

    /**************************************************************************
     * Display the duplicate message ID dialog
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    @SuppressWarnings("serial")
    protected void displayDuplicates(Component parent)
    {
        // Get the list of message IDs in use
        getMessageIDsInUse(true, true, true, true, true, false, null, true, parent);

        // Sort the duplicates list
        Collections.sort(duplicates, new Comparator<String[]>()
        {
            /******************************************************************
             * Sort the duplicates list based on the message ID (second column)
             *****************************************************************/
            @Override
            public int compare(final String[] msgID1, final String[] msgID2)
            {
                return Integer.decode(msgID1[1]).compareTo(Integer.decode(msgID2[1]));
            }
        });

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the table to display the duplicate message IDs
        CcddJTableHandler duplicatesTable = new CcddJTableHandler()
        {
            /******************************************************************
             * Allow multiple line display in the specified columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column == DuplicateMsgIDColumnInfo.OWNERS.ordinal();
            }

            /******************************************************************
             * Load the duplicate message ID data into the table and format the
             * table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(duplicates.toArray(new String[0][0]),
                                            DuplicateMsgIDColumnInfo.getColumnNames(),
                                            "1:0",
                                            null,
                                            null,
                                            DuplicateMsgIDColumnInfo.getToolTips(),
                                            true,
                                            true,
                                            true,
                                            true);
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(duplicatesTable);

        // Set up the field table parameters
        duplicatesTable.setFixedCharacteristics(scrollPane,
                                                false,
                                                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                                TableSelectionMode.SELECT_BY_CELL,
                                                true,
                                                ModifiableColorInfo.TABLE_BACK.getColor(),
                                                false,
                                                true,
                                                ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                                true);

        // Define the panel to contain the table
        JPanel resultsTblPnl = new JPanel();
        resultsTblPnl.setLayout(new BoxLayout(resultsTblPnl, BoxLayout.X_AXIS));
        resultsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        resultsTblPnl.add(scrollPane);

        // Add the table to the dialog
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(resultsTblPnl, gbc);

        new CcddDialogHandler().showOptionsDialog(parent,
                                                  dialogPnl,
                                                  "Duplicate Message IDs",
                                                  DialogOption.PRINT_OPTION,
                                                  true);
    }
}
