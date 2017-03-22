/**
 * CFS Command & Data Dictionary message ID assignment dialog. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.Message;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.MessageIDType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary message ID assignment dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddAssignMessageIDDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddDbTableCommandHandler dbTable;
    private CcddTelemetrySchedulerDialog schedulerDlg;
    private CcddTableTypeHandler tableTypeHandler;

    // Components that need to be accessed by multiple methods
    private Border border;
    private Border etchBorder;
    private JTabbedPane tabbedPane;

    // List of message IDs that are reserved or are already assigned to a
    // message
    private List<Integer> idsInUse;

    // Message ID assignment dialog type
    private final MessageIDType msgIDDialogType;

    // List of messages and their sub-messages
    private List<Message> messages;

    // Array of message ID tab information and GUI components
    private MsgTabInfo[] msgTabs;

    /**************************************************************************
     * Message name/ID assignment tab information class
     *************************************************************************/
    private class MsgTabInfo
    {
        private final String name;
        private final String type;
        private final String toolTip;
        private JCheckBox assignCbx;
        private JLabel startLbl;
        private JTextField startFld;
        private JLabel intervalLbl;
        private JTextField intervalFld;
        private JCheckBox overwriteCbx;
        private JLabel patternLbl;
        private JTextField patternFld;

        /**********************************************************************
         * Message name/ID assignment tab information class constructor
         * 
         * @param name
         *            tab name
         * 
         * @param type
         *            message type
         * 
         * @param toolTip
         *            tab tool tip
         *********************************************************************/
        MsgTabInfo(String name, String type, String toolTip)
        {
            this.name = name;
            this.type = type;
            this.toolTip = toolTip;
        }

        /**********************************************************************
         * Get the tab name
         * 
         * @return Tab name
         *********************************************************************/
        private String getName()
        {
            return name;
        }

        /**********************************************************************
         * Get the tab type
         * 
         * @return Tab type
         *********************************************************************/
        private String getType()
        {
            return type;
        }

        /**********************************************************************
         * Get the tab tool tip
         * 
         * @return Tab tool tip
         *********************************************************************/
        private String getToolTip()
        {
            return toolTip;
        }

        /**********************************************************************
         * Get the reference to the message name/ID assignment check box
         * 
         * @return Reference to the message name/ID assignment check box
         *********************************************************************/
        private JCheckBox getAssignCbx()
        {
            return assignCbx;
        }

        /**********************************************************************
         * Set the reference to the message name/ID assignment check box
         * 
         * @param idAssignCbx
         *            Reference to the message name/ID assignment check box
         *********************************************************************/
        private void setAssignCbx(JCheckBox idAssignCbx)
        {
            this.assignCbx = idAssignCbx;
        }

        /**********************************************************************
         * Get the reference to the message number/ID start label
         * 
         * @return Reference to the message number/ID start label
         *********************************************************************/
        private JLabel getStartLbl()
        {
            return startLbl;
        }

        /**********************************************************************
         * Set the reference to the message number/ID start label
         * 
         * @param startLbl
         *            reference to the message number/ID start label
         *********************************************************************/
        private void setStartLbl(JLabel startLbl)
        {
            this.startLbl = startLbl;
        }

        /**********************************************************************
         * Get the reference to the message number/ID start input field
         * 
         * @return Reference to the message number/ID start input field
         *********************************************************************/
        private JTextField getStartFld()
        {
            return startFld;
        }

        /**********************************************************************
         * Set the reference to the message number/ID start input field
         * 
         * @param startFld
         *            reference to the message number/ID start input field
         *********************************************************************/
        private void setStartFld(JTextField startFld)
        {
            this.startFld = startFld;
        }

        /**********************************************************************
         * Get the reference to the message number/ID interval label
         * 
         * @return Reference to the message number/ID interval label
         *********************************************************************/
        private JLabel getIntervalLbl()
        {
            return intervalLbl;
        }

        /**********************************************************************
         * Set the reference to the message number/ID interval label
         * 
         * @param intervalLbl
         *            reference to the message number/ID interval label
         *********************************************************************/
        private void setIntervalLbl(JLabel intervalLbl)
        {
            this.intervalLbl = intervalLbl;
        }

        /**********************************************************************
         * Get the reference to the message number/ID interval input field
         * 
         * @return Reference to the message number/ID interval input field
         *********************************************************************/
        private JTextField getIntervalFld()
        {
            return intervalFld;
        }

        /**********************************************************************
         * Set the reference to the message number/ID interval input field
         * 
         * @param intervalFld
         *            reference to the message number/ID interval input field
         *********************************************************************/
        private void setIntervalFld(JTextField intervalFld)
        {
            this.intervalFld = intervalFld;
        }

        /**********************************************************************
         * Get the reference to the overwrite check box
         * 
         * @return Reference to the overwrite check box
         *********************************************************************/
        private JCheckBox getOverwriteCbx()
        {
            return overwriteCbx;
        }

        /**********************************************************************
         * Set the reference to the overwrite check box
         * 
         * @param overwriteCbx
         *            reference to the overwrite check box
         *********************************************************************/
        private void setOverwriteCbx(JCheckBox overwriteCbx)
        {
            this.overwriteCbx = overwriteCbx;
        }

        /**********************************************************************
         * Get the reference to the message name pattern label
         * 
         * @return Reference to the message name pattern label
         *********************************************************************/
        private JLabel getPatternLbl()
        {
            return patternLbl;
        }

        /**********************************************************************
         * Set the reference to the message name pattern label
         * 
         * @param patternLbl
         *            reference to the message name pattern label
         *********************************************************************/
        private void setPatternLbl(JLabel patternLbl)
        {
            this.patternLbl = patternLbl;
        }

        /**********************************************************************
         * Get the reference to the message name pattern input field
         * 
         * @return Reference to the message name pattern input field
         *********************************************************************/
        private JTextField getPatternFld()
        {
            return patternFld;
        }

        /**********************************************************************
         * Set the reference to the message name pattern input field
         * 
         * @param patternLbl
         *            reference to the message name pattern input field
         *********************************************************************/
        private void setPatternFld(JTextField patternFld)
        {
            this.patternFld = patternFld;
        }
    }

    /**************************************************************************
     * Message ID assignment dialog class constructor for structure and command
     * messages
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddAssignMessageIDDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        msgIDDialogType = MessageIDType.TABLE_DATA_FIELD;

        // Create the structure and command message ID assignment dialog
        initialize();
    }

    /**************************************************************************
     * Message name and ID assignment dialog class constructor for telemetry
     * messages
     * 
     * @param ccddMain
     *            main class
     * 
     * @param messages
     *            list of telemetry messages
     * 
     * @param schedulerDlg
     *            component over which to center the dialog
     *************************************************************************/
    protected CcddAssignMessageIDDialog(CcddMain ccddMain,
                                        List<Message> messages,
                                        CcddTelemetrySchedulerDialog schedulerDlg)
    {
        this.ccddMain = ccddMain;
        this.messages = messages;
        this.schedulerDlg = schedulerDlg;
        msgIDDialogType = MessageIDType.TELEMETRY;

        // Create the telemetry message name and ID assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the message ID assignment dialog
     *************************************************************************/
    private void initialize()
    {
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        CcddMessageIDHandler msgIDHandler = new CcddMessageIDHandler(ccddMain);

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(0,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create borders for the input fields and assignment panels
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(2, 2, 2, 2));
        etchBorder = BorderFactory.createEtchedBorder();

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(etchBorder);

        // Create a tabbed pane to contain the message name/ID parameters
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(LABEL_FONT_BOLD);

        // Check if this is the structure, command, and other table type
        // message ID assignment dialog
        if (msgIDDialogType == MessageIDType.TABLE_DATA_FIELD)
        {
            // Create the information for each table type tab
            msgTabs = new MsgTabInfo[] {new MsgTabInfo(TYPE_STRUCTURE,
                                                       "structure",
                                                       "Structure message ID assignment"),
                                        new MsgTabInfo(TYPE_COMMAND,
                                                       "command",
                                                       "Command message ID assignment"),
                                        new MsgTabInfo(TYPE_OTHER,
                                                       "other",
                                                       "Other message ID assignment")};

            // Step through each tab information
            for (MsgTabInfo tabInfo : msgTabs)
            {
                // Add a tab for the table type
                addMessageIDTab(tabInfo, false);
            }

            // Add the tabbed pane to the dialog
            dialogPnl.add(tabbedPane, gbc);

            // Get the user's input and check if at least one of assignment
            // check boxes is selected
            if (showOptionsDialog(ccddMain.getMainFrame(),
                                  dialogPnl,
                                  "Assign Message IDs",
                                  DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                && isAssignTypeSelected())
            {
                // Get the list of message IDs that are reserved or already in
                // use
                idsInUse = msgIDHandler.getMessageIDsInUse(!msgTabs[0].getOverwriteCbx().isSelected(),
                                                           !msgTabs[1].getOverwriteCbx().isSelected(),
                                                           !msgTabs[2].getOverwriteCbx().isSelected(),
                                                           true,
                                                           false,
                                                           CcddAssignMessageIDDialog.this);

                // Create a field handler and populate it with the field
                // definitions for all of the tables in the database
                CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                                     null,
                                                                     CcddAssignMessageIDDialog.this);
                List<FieldInformation> fieldInformation = fieldHandler.getFieldInformation();

                // Sort the field information by table name so that sequence
                // order of the message ID values is applied to the tables'
                // alphabetical order
                Collections.sort(fieldInformation, new Comparator<FieldInformation>()
                {
                    /**********************************************************
                     * Compare the table names of two field definitions. Force
                     * lower case to eliminate case differences in the
                     * comparison
                     *********************************************************/
                    @Override
                    public int compare(FieldInformation fld1, FieldInformation fld2)
                    {
                        return fld1.getOwnerName().toLowerCase().compareTo(fld2.getOwnerName().toLowerCase());
                    }
                });

                // Assign the structure, command, and other table type column
                // and data field message IDs, skipping any that are in the
                // reserved message IDs list
                boolean structMsgChanged = assignTableMessageIDs(msgTabs[0],
                                                                 msgIDHandler.getStructureTables(),
                                                                 fieldInformation);
                boolean cmdMsgChanged = assignTableMessageIDs(msgTabs[1],
                                                              msgIDHandler.getCommandTables(),
                                                              fieldInformation);
                boolean otherMsgChanged = assignTableMessageIDs(msgTabs[2],
                                                                msgIDHandler.getOtherTables(),
                                                                fieldInformation);

                // Check if a structure, command, or other table type telemetry
                // message ID column or data field value changed
                if (structMsgChanged || cmdMsgChanged || otherMsgChanged)
                {
                    // Store the updated data fields table
                    dbTable.storeInformationTableInBackground(InternalTable.FIELDS,
                                                  fieldHandler.getFieldDefinitionList(),
                                                  null,
                                                  CcddAssignMessageIDDialog.this);
                }
            }
        }
        // This is the telemetry message ID assignment dialog
        else
        {
            // Create the information for the telemetry message name and ID
            // assignment
            msgTabs = new MsgTabInfo[] {new MsgTabInfo("Message Name",
                                                       "telemetry",
                                                       "Telemetry message name assignment"),
                                        new MsgTabInfo("Message ID",
                                                       "telemetry",
                                                       "Telemetry message ID assignment")};

            // Add the telemetry message name and ID tabs
            addMessageIDTab(msgTabs[0], true);
            addMessageIDTab(msgTabs[1], false);

            // Add the tabbed pane to the dialog
            dialogPnl.add(tabbedPane, gbc);

            // Get the user's input and check if at least one of assignment
            // check boxes is selected
            if (showOptionsDialog(ccddMain.getMainFrame(),
                                  dialogPnl,
                                  "Assign Message Names and IDs",
                                  DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                && isAssignTypeSelected())
            {
                // Get the list of message IDs that are reserved or already in
                // use
                idsInUse = msgIDHandler.getMessageIDsInUse(true,
                                                           true,
                                                           true,
                                                           !msgTabs[0].getOverwriteCbx().isSelected(),
                                                           false,
                                                           CcddAssignMessageIDDialog.this);

                // Check if the message names should be assigned
                if (msgTabs[0].getAssignCbx().isSelected())
                {
                    // Update the telemetry message names
                    assignTelemetryMessageNames(msgTabs[0]);

                    // Update the options list with the new message names
                    schedulerDlg.getSchedulerHandler().getTelemetryOptions();
                }

                // Check if the telemetry message IDs should be assigned
                if (msgTabs[1].getAssignCbx().isSelected())
                {
                    // Update the telemetry message IDs
                    assignTelemetryMessageIDs(msgTabs[1]);
                }

                // Update the scheduler table
                schedulerDlg.getSchedulerHandler().getSchedulerEditor().updateSchedulerTable(true);
            }
        }
    }

    /**************************************************************************
     * Add a tab for the specified table type or the telemetry message name/ID
     * 
     * @param tabInfo
     *            message name/ID tab information reference
     * 
     * @param isTlmName
     *            true if this is the tab for the telemetry message name
     *            assignment
     *************************************************************************/
    private void addMessageIDTab(final MsgTabInfo tabInfo,
                                 boolean isTlmName)
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create sub-panels for the dialog components
        JPanel tabPnl = new JPanel(new GridBagLayout());
        JPanel inputPnl = new JPanel(new GridBagLayout());
        tabPnl.setBorder(etchBorder);

        // Check if this is the telemetry message name assignment tab
        if (isTlmName)
        {
            // Create the telemetry message name pattern label
            tabInfo.setPatternLbl(new JLabel("Name pattern"));
            tabInfo.getPatternLbl().setFont(LABEL_FONT_BOLD);
            inputPnl.add(tabInfo.getPatternLbl(), gbc);

            // Create the telemetry message name pattern field
            tabInfo.setPatternFld(new JTextField("Message_%03d", 12));
            tabInfo.getPatternFld().setFont(LABEL_FONT_PLAIN);
            tabInfo.getPatternFld().setEditable(true);
            tabInfo.getPatternFld().setForeground(Color.BLACK);
            tabInfo.getPatternFld().setBackground(Color.WHITE);
            tabInfo.getPatternFld().setBorder(border);
            tabInfo.getPatternFld().setToolTipText("<html>Format: <i>alphanumeric</i>#<i>&lt;alphanumeric&gt;");
            gbc.gridx++;
            inputPnl.add(tabInfo.getPatternFld(), gbc);
            gbc.gridx = 0;
            gbc.gridy++;
        }

        // Create the starting message number/ID label
        tabInfo.setStartLbl(new JLabel("Starting "
                                       + (isTlmName
                                                   ? "number"
                                                   : "ID")));
        tabInfo.getStartLbl().setFont(LABEL_FONT_BOLD);
        inputPnl.add(tabInfo.getStartLbl(), gbc);

        // Create the starting message number/ID field
        tabInfo.setStartFld(new JTextField((isTlmName
                                                     ? "0"
                                                     : "0x0"),
                                           7));
        tabInfo.getStartFld().setFont(LABEL_FONT_PLAIN);
        tabInfo.getStartFld().setEditable(true);
        tabInfo.getStartFld().setForeground(Color.BLACK);
        tabInfo.getStartFld().setBackground(Color.WHITE);
        tabInfo.getStartFld().setBorder(border);
        tabInfo.getStartFld().setToolTipText("<html>Format: <i>&lt;</i>0x<i>&gt;hexadecimal digits");
        gbc.gridx++;
        inputPnl.add(tabInfo.getStartFld(), gbc);

        // Create the message name/ID interval label
        tabInfo.setIntervalLbl(new JLabel((isTlmName
                                                    ? "Message"
                                                    : "ID")
                                          + " interval"));
        tabInfo.getIntervalLbl().setFont(LABEL_FONT_BOLD);
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(tabInfo.getIntervalLbl(), gbc);

        // Create the message number/ID interval field
        tabInfo.setIntervalFld(new JTextField("1", 5));
        tabInfo.getIntervalFld().setFont(LABEL_FONT_PLAIN);
        tabInfo.getIntervalFld().setEditable(true);
        tabInfo.getIntervalFld().setForeground(Color.BLACK);
        tabInfo.getIntervalFld().setBackground(Color.WHITE);
        tabInfo.getIntervalFld().setBorder(border);
        gbc.gridx++;
        inputPnl.add(tabInfo.getIntervalFld(), gbc);

        // Create the check box to enable message name/ID assignment
        tabInfo.setAssignCbx(new JCheckBox("Assign "
                                           + tabInfo.getType()
                                           + " message "
                                           + (isTlmName
                                                       ? "names"
                                                       : "IDs")));
        tabInfo.getAssignCbx().setFont(LABEL_FONT_BOLD);

        // Add a listener for check box selection changes
        tabInfo.getAssignCbx().addItemListener(new ItemListener()
        {
            /******************************************************************
             * Set the remaining inputs based on the check box selection state
             *****************************************************************/
            @Override
            public void itemStateChanged(ItemEvent ae)
            {
                setTabGUIComponentsEnable(tabInfo, tabInfo.getAssignCbx().isSelected());
            }
        });

        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        tabPnl.add(tabInfo.getAssignCbx(), gbc);

        // Add the input panel to the tab panel
        gbc.insets.left = LABEL_HORIZONTAL_SPACING * 2;
        gbc.gridy++;
        tabPnl.add(inputPnl, gbc);

        // Create the overwrite existing name/IDs check box
        tabInfo.setOverwriteCbx(new JCheckBox("Overwrite existing "
                                              + (isTlmName
                                                          ? "names"
                                                          : "IDs")));
        tabInfo.getOverwriteCbx().setFont(LABEL_FONT_BOLD);
        gbc.gridy++;
        tabPnl.add(tabInfo.getOverwriteCbx(), gbc);

        // Add the tab
        tabbedPane.addTab(tabInfo.getName(),
                          null,
                          tabPnl,
                          tabInfo.getToolTip());

        // Disable the tab components initially
        setTabGUIComponentsEnable(tabInfo, false);
    }

    /**************************************************************************
     * Set the enable state of the specified table type or telemetry message
     * name/ID input fields and check box
     * 
     * @param tabInfo
     *            message name/ID tab information reference
     * 
     * @param enable
     *            true to enable the message name/ID input fields and check
     *            box; false to disable
     *************************************************************************/
    private void setTabGUIComponentsEnable(MsgTabInfo tabInfo, boolean enable)
    {
        tabInfo.getStartLbl().setEnabled(enable);
        tabInfo.getStartFld().setEnabled(enable);
        tabInfo.getIntervalLbl().setEnabled(enable);
        tabInfo.getIntervalFld().setEnabled(enable);
        tabInfo.getOverwriteCbx().setEnabled(enable);

        // Check if the pattern label and input field exist in this tab
        if (tabInfo.getPatternLbl() != null)
        {
            tabInfo.getPatternLbl().setEnabled(enable);
            tabInfo.getPatternFld().setEnabled(enable);
        }
    }

    /**************************************************************************
     * Determine if any of the assignment check boxes is selected
     * 
     * @return true if one or more of the assignment check boxes is selected
     *************************************************************************/
    private boolean isAssignTypeSelected()
    {
        boolean isSelected = false;

        // Step through each tab
        for (MsgTabInfo tabInfo : msgTabs)
        {
            // Check if the assignment check box is selected
            if (tabInfo.getAssignCbx().isSelected())
            {
                // Set the flag indicating a check box is selected and stop
                // searching
                isSelected = true;
                break;
            }
        }

        return isSelected;
    }

    /**************************************************************************
     * Assign message ID values to the structure, command, or other table type
     * message ID columns and data fields
     * 
     * @param tabInfo
     *            message ID tab information reference
     * 
     * @param tables
     *            list of structure, command, or other tables, with paths
     * 
     * @param fieldInformation
     *            list of data field information
     * 
     * @return true if a message ID value changed
     *************************************************************************/
    private boolean assignTableMessageIDs(MsgTabInfo type,
                                          List<String> tables,
                                          List<FieldInformation> fieldInformation)
    {
        boolean isChanges = false;

        // Get the starting message ID and ID interval values
        int startID = Integer.decode(type.getStartFld().getText());
        int interval = Integer.valueOf(type.getIntervalFld().getText());

        // ////////////////////////////////////////////////////////////////////
        // First assign message IDs to all table columns with a message ID
        // input type for those tables of the current table type
        // ////////////////////////////////////////////////////////////////////

        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Check if the tab information type and table type match the table
            // type definition
            if ((type.getName().equals(TYPE_STRUCTURE) && typeDefn.isStructure())
                || type.getName().equals(TYPE_COMMAND) && typeDefn.isCommand()
                || (type.getName().equals(TYPE_OTHER)
                    && !typeDefn.isStructure()
                    && !typeDefn.isCommand()))
            {
                // Get a list of the columns in this table type that are
                // message IDs
                List<Integer> msgIDColumns = typeDefn.getColumnIndicesByInputType(InputDataType.MESSAGE_ID);

                // Check if the table type has any columns that are message IDs
                if (!msgIDColumns.isEmpty())
                {
                    // Step through each table
                    for (String tablePath : tables)
                    {
                        // Load the table's information from the project
                        // database
                        TableInformation tableInformation = dbTable.loadTableData(tablePath,
                                                                                  !tablePath.contains(","),
                                                                                  false,
                                                                                  true,
                                                                                  false,
                                                                                  CcddAssignMessageIDDialog.this);

                        // Check that the table data loaded and if the table's
                        // type matches that of the current type definition
                        if (!tableInformation.isErrorFlag()
                            && tableInformation.getType().equals(typeDefn.getName()))
                        {
                            // Create a table editor handler, but without
                            // displaying the editor itself
                            CcddTableEditorHandler editor = new CcddTableEditorHandler(ccddMain,
                                                                                       tableInformation,
                                                                                       ccddMain.getDataTypeHandler());

                            // Check if the table arrays aren't expanded
                            if (!editor.isExpanded())
                            {
                                // Expand the table arrays
                                editor.showHideArrayMembers();
                            }

                            // Get the table's data (again if a name change
                            // occurred since changes were made)
                            Object[][] tableData = editor.getTable().getTableData(false);

                            // Step through each row in the table
                            for (int row = 0; row < editor.getTable().getModel().getRowCount(); row++)
                            {
                                // Step through each column that contains
                                // message IDs
                                for (int idColumn : msgIDColumns)
                                {
                                    // Check if the cell's value can be
                                    // changed, and if existing values should
                                    // be overwritten or if the cell is empty
                                    if (editor.getTable().isCellEditable(editor.getTable().convertRowIndexToView(row),
                                                                         editor.getTable().convertColumnIndexToView(idColumn))
                                        && (type.getOverwriteCbx().isSelected()
                                        || tableData[row][idColumn].toString().isEmpty()))
                                    {
                                        // Set the column message ID value to
                                        // the next unused message ID
                                        startID = getNextMessageID(startID, interval);
                                        tableData[row][idColumn] = formatMessageID(startID);
                                    }
                                }
                            }

                            // Check if the a message ID in the table was
                            // changed
                            if (editor.getTable().isTableChanged(tableData))
                            {
                                // Load the updated array of data into the
                                // table
                                editor.getTable().loadDataArrayIntoTable(tableData, false);

                                // Build the table updates
                                editor.buildUpdates();

                                // Make the table modifications to the project
                                // database and to any open table editors
                                dbTable.modifyTableData(editor.getTableInformation(),
                                                        editor.getAdditions(),
                                                        editor.getModifications(),
                                                        editor.getDeletions(),
                                                        true,
                                                        null,
                                                        CcddAssignMessageIDDialog.this);
                            }
                        }
                    }
                }
            }
        }

        // ////////////////////////////////////////////////////////////////////
        // Next assign message IDs to table data fields
        // ////////////////////////////////////////////////////////////////////

        // Step through each defined data field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Get the reference to the field information
            FieldInformation fieldInfo = fieldInformation.get(index);

            // Check if the field is for a table (and not a default field),
            // that the field name matches the one supplied by the user in the
            // text field, and that either the overwrite check box is selected
            // or the field is blank
            if (fieldInfo.getInputType().equals(InputDataType.MESSAGE_ID)
                && tables.contains(fieldInfo.getOwnerName())
                && (type.getOverwriteCbx().isSelected()
                || fieldInfo.getValue().isEmpty()))
            {
                // Set the message ID data field value to the next unused
                // message ID
                startID = getNextMessageID(startID, interval);
                fieldInfo.setValue(formatMessageID(startID));

                // If any table editor dialogs are open then the displayed data
                // fields need to be updated to match the message ID value
                // change. Both the currently displayed and committed field
                // values are updated so that when the editor dialog is closed
                // these changes aren't seen as table changes since they're
                // already committed to the database. Step through each table
                // editor dialog
                for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                {
                    boolean isUpdate = false;

                    // Step through each table editor in the editor dialog
                    for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                    {
                        // Get the reference to the table's field handler
                        CcddFieldHandler editorFldHandler = editor.getTableInformation().getFieldHandler();

                        // Check if the table contains the message ID field; if
                        // so then update it to the new value
                        if (editorFldHandler.updateField(fieldInfo))
                        {
                            // Update the committed message ID value
                            editor.getCommittedTableInformation().getFieldHandler().updateField(fieldInfo);

                            // Update the editor data fields
                            editor.updateDataFields();

                            // Set the flag to indicate the table/field
                            // combination was located and stop searching
                            isUpdate = true;
                            break;
                        }
                    }

                    // Check if this table/field combination has been located
                    if (isUpdate)
                    {
                        // Stop searching
                        break;
                    }
                }

                // Set the flag to indicate a message ID value is changed
                isChanges = true;
            }
        }

        return isChanges;
    }

    /**************************************************************************
     * Assign the telemetry message names
     * 
     * @param tlmName
     *            telemetry message name tab information reference
     *************************************************************************/
    private void assignTelemetryMessageNames(MsgTabInfo tlmName)
    {
        // Get the message starting number and interval values
        int startNum = Integer.valueOf(tlmName.getStartFld().getText());
        int interval = Integer.valueOf(tlmName.getIntervalFld().getText());

        // Step through each message
        for (Message message : messages)
        {
            // Use the pattern and current message number to create the message
            // name
            String msgName = String.format(tlmName.getPatternFld().getText(),
                                           startNum);

            // Adjust the message number by the interval amount
            startNum += interval;

            // Store the message's current name, then change the name to the
            // new one
            String originalName = message.getName();
            message.setName(msgName);

            // Check if the message has sub-messages other than the default
            if (message.getNumberOfSubMessages() > 1)
            {
                // Step through each of the message's sub-messages
                for (Message subMessage : message.getSubMessages())
                {
                    // Update the sub-message name to match the new pattern
                    subMessage.setName(subMessage.getName().replaceFirst(Pattern.quote(originalName),
                                                                         msgName));
                }
            }
        }
    }

    /**************************************************************************
     * Assign the telemetry message IDs
     * 
     * @param tlmID
     *            telemetry message ID tab information reference
     *************************************************************************/
    private void assignTelemetryMessageIDs(MsgTabInfo tlmID)
    {
        // Get the starting message ID and ID interval values
        int startID = Integer.decode(tlmID.getStartFld().getText());
        int interval = Integer.valueOf(tlmID.getIntervalFld().getText());

        // Check if the overwrite check box is not selected
        if (!tlmID.getOverwriteCbx().isSelected())
        {
            // Step through each message
            for (Message message : messages)
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
                        // Add the sub-message ID to the list of existing ID
                        // values
                        idsInUse.add(Integer.decode(subMessage.getID()));
                    }
                }
            }
        }

        // Step through each message
        for (Message message : messages)
        {
            // Set the message's ID to the next one in the sequence
            startID = setMessageID(tlmID, message, startID, interval);

            // Step through each of the message's sub-messages. The default
            // sub-message is skipped since its ID gets set when the parent
            // message's ID is set
            for (int index = 1; index < message.getSubMessages().size(); index++)
            {
                // Set the sub-message's ID to the next one in the sequence
                // (for the default sub-message use the parent message's ID
                startID = setMessageID(tlmID,
                                       message.getSubMessage(index),
                                       startID,
                                       interval);
            }
        }
    }

    /**************************************************************************
     * Set the message ID. If the message already has an ID only update it if
     * the overwrite check box is selected
     * 
     * 
     * @param tlmID
     *            telemetry message ID tab information reference
     * 
     * @param message
     *            reference to the (sub-)message for which to set the ID
     * 
     * @param idValue
     *            ID value
     * 
     * @param interval
     *            difference between two contiguous message ID values
     * 
     * @return Next ID value in the sequence
     *************************************************************************/
    private int setMessageID(MsgTabInfo tlmID,
                             Message message,
                             int idValue,
                             int interval)
    {
        int nextID = idValue;

        // Check if the message has no ID, or if it does, that the overwrite ID
        // check box is selected
        if (message.getID().isEmpty() || tlmID.getOverwriteCbx().isSelected())
        {
            // Get the next unused message ID value
            nextID = getNextMessageID(idValue, interval);

            // Set the message ID
            message.setID(formatMessageID(idValue));
        }

        return nextID;
    }

    /**************************************************************************
     * Get the next unused message ID value
     * 
     * 
     * @param idValue
     *            ID value from which to start checking
     * 
     * @param interval
     *            difference between two contiguous message ID values
     * 
     * @return Next unused ID value
     *************************************************************************/
    private int getNextMessageID(int idValue, int interval)
    {
        // Continue to loop as long as the ID value matches a reserved or
        // existing one. This prevents assigning a duplicate ID
        while (idsInUse.contains(idValue))
        {
            // Adjust the message ID value by the interval amount and set the
            // flag to indicate a message ID value is changed
            idValue += interval;
        }

        // Add the message ID to the list of those in use
        idsInUse.add(idValue);

        return idValue;
    }

    /**************************************************************************
     * Convert a message ID value into a formatted hexadecimal text string
     * 
     * @param idValue
     *            message ID value
     * 
     * @return Message ID value as a formatted hexadecimal text string
     *************************************************************************/
    private String formatMessageID(int idValue)
    {
        return String.format("0x%04x", idValue);
    }

    /**************************************************************************
     * Verify that the dialog content is valid
     * 
     * @return true if the input values are valid
     *************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            // Step through each table type
            for (MsgTabInfo tabInfo : msgTabs)
            {
                // Remove any excess white space
                tabInfo.getStartFld().setText(tabInfo.getStartFld().getText().trim());

                // Check if the assignment check box is selected
                if (tabInfo.getAssignCbx().isSelected())
                {
                    // Check if this is a message ID tab
                    if (tabInfo.getPatternFld() == null)
                    {
                        // Check if the starting message ID value is not in
                        // hexadecimal format
                        if (!tabInfo.getStartFld().getText().matches(InputDataType.HEXADECIMAL.getInputMatch()))
                        {
                            // Inform the user that the starting ID is invalid
                            throw new CCDDException("Starting ID must be in the format<br>&#160;&#160;<i>&lt;</i>"
                                                    + "0x<i>&gt;</i>#<br>where # is one or more hexadecimal digits");
                        }

                        // Check if the message ID interval value is not a
                        // positive integer
                        if (!tabInfo.getIntervalFld().getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                        {
                            // Inform the user that the interval is invalid
                            throw new CCDDException("ID interval must be a positive integer");
                        }
                    }
                    // This is the telemetry message name tab
                    else
                    {
                        // Remove any excess white space
                        tabInfo.getPatternFld().setText(tabInfo.getPatternFld().getText().trim());

                        // Get the name pattern
                        String pattern = tabInfo.getPatternFld().getText();

                        // Check if the message name pattern isn't in the
                        // format text%<0#>d<text> where # is one or more
                        // digits
                        if (!pattern.matches(InputDataType.ALPHANUMERIC.getInputMatch()
                                             + "%(0\\d+)?d[a-zA-Z0-9_]*"))
                        {
                            // Inform the user that the name pattern is invalid
                            throw new CCDDException("Message name pattern must be in the format:<br><br></b>"
                                                    + "&#160;&#160;&#160;<i>startText</i>%&lt;0#&gt;d<i>&lt;"
                                                    + "endText&gt;</i><b><br><br>where </b><i>startText</i><b> "
                                                    + "and </b><i>endText</i><b> consist of alphanumeric "
                                                    + "characters and/or underscores, </b><i>startText</i><b> "
                                                    + "begins with a letter or underscore, and </b><i>#</i><b> "
                                                    + "is one or more digits.&#160;&#160;Note: </b><i>0#</i><b> "
                                                    + "and </b><i>endText</i><b> are optional");
                        }

                        // Check if the starting message number is not a
                        // non-negative integer
                        if (!tabInfo.getStartFld().getText().matches(InputDataType.INT_NON_NEGATIVE.getInputMatch()))
                        {
                            // Inform the user that the starting number is
                            // invalid
                            throw new CCDDException("Message starting number must be an integer >= 0");
                        }

                        // Check if the message name interval value is not a
                        // positive integer
                        if (!tabInfo.getIntervalFld().getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                        {
                            // Inform the user that the interval is invalid
                            throw new CCDDException("Message interval must be a positive integer");
                        }
                    }
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddAssignMessageIDDialog.this,
                                                      "<html><b>"
                                                          + ce.getMessage(),
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
