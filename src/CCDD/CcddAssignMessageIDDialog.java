/**
 * CFS Command and Data Dictionary message ID assignment dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PAD_VARIABLE_MATCH;
import static CCDD.CcddConstants.PROTECTED_MSG_ID_IDENT;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.STATUS_MSG;

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
import javax.swing.InputVerifier;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.MessageIDType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary message ID assignment dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddAssignMessageIDDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddDbTableCommandHandler dbTable;
    private final CcddTelemetrySchedulerDialog schedulerDlg;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddInputTypeHandler inputTypeHandler;
    private CcddFieldHandler fieldHandler;
    private final CcddMessageIDHandler messageIDHandler;
    private final CcddEventLogDialog eventLog;
    private CcddHaltDialog haltDlg;

    // Components that need to be accessed by multiple methods
    private Border border;
    private Border etchBorder;
    private JTabbedPane tabbedPane;

    // List of message IDs that are reserved or are already assigned to a message
    private List<Integer> idsInUse;

    // Message ID assignment dialog type
    private final MessageIDType msgIDDialogType;

    // List of messages and their sub-messages
    private final List<Message> messages;

    // Array of message ID tab information and GUI components
    private MsgTabInfo[] msgTabs;

    /**********************************************************************************************
     * Message name/ID assignment tab information class
     *********************************************************************************************/
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

        /******************************************************************************************
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
         *****************************************************************************************/
        MsgTabInfo(String name, String type, String toolTip)
        {
            this.name = name;
            this.type = type;
            this.toolTip = toolTip;
        }

        /******************************************************************************************
         * Get the tab name
         *
         * @return Tab name
         *****************************************************************************************/
        private String getName()
        {
            return name;
        }

        /******************************************************************************************
         * Get the tab type
         *
         * @return Tab type
         *****************************************************************************************/
        private String getType()
        {
            return type;
        }

        /******************************************************************************************
         * Get the tab tool tip
         *
         * @return Tab tool tip
         *****************************************************************************************/
        private String getToolTip()
        {
            return toolTip;
        }

        /******************************************************************************************
         * Get the reference to the message name/ID assignment check box
         *
         * @return Reference to the message name/ID assignment check box
         *****************************************************************************************/
        private JCheckBox getAssignCbx()
        {
            return assignCbx;
        }

        /******************************************************************************************
         * Set the reference to the message name/ID assignment check box
         *
         * @param idAssignCbx
         *            Reference to the message name/ID assignment check box
         *****************************************************************************************/
        private void setAssignCbx(JCheckBox idAssignCbx)
        {
            this.assignCbx = idAssignCbx;
        }

        /******************************************************************************************
         * Get the reference to the message number/ID start label
         *
         * @return Reference to the message number/ID start label
         *****************************************************************************************/
        private JLabel getStartLbl()
        {
            return startLbl;
        }

        /******************************************************************************************
         * Set the reference to the message number/ID start label
         *
         * @param startLbl
         *            reference to the message number/ID start label
         *****************************************************************************************/
        private void setStartLbl(JLabel startLbl)
        {
            this.startLbl = startLbl;
        }

        /******************************************************************************************
         * Get the reference to the message number/ID start input field
         *
         * @return Reference to the message number/ID start input field
         *****************************************************************************************/
        private JTextField getStartFld()
        {
            return startFld;
        }

        /******************************************************************************************
         * Set the reference to the message number/ID start input field
         *
         * @param startFld
         *            reference to the message number/ID start input field
         *****************************************************************************************/
        private void setStartFld(JTextField startFld)
        {
            this.startFld = startFld;
        }

        /******************************************************************************************
         * Get the reference to the message number/ID interval label
         *
         * @return Reference to the message number/ID interval label
         *****************************************************************************************/
        private JLabel getIntervalLbl()
        {
            return intervalLbl;
        }

        /******************************************************************************************
         * Set the reference to the message number/ID interval label
         *
         * @param intervalLbl
         *            reference to the message number/ID interval label
         *****************************************************************************************/
        private void setIntervalLbl(JLabel intervalLbl)
        {
            this.intervalLbl = intervalLbl;
        }

        /******************************************************************************************
         * Get the reference to the message number/ID interval input field
         *
         * @return Reference to the message number/ID interval input field
         *****************************************************************************************/
        private JTextField getIntervalFld()
        {
            return intervalFld;
        }

        /******************************************************************************************
         * Set the reference to the message number/ID interval input field
         *
         * @param intervalFld
         *            reference to the message number/ID interval input field
         *****************************************************************************************/
        private void setIntervalFld(JTextField intervalFld)
        {
            this.intervalFld = intervalFld;
        }

        /******************************************************************************************
         * Get the reference to the overwrite check box
         *
         * @return Reference to the overwrite check box
         *****************************************************************************************/
        private JCheckBox getOverwriteCbx()
        {
            return overwriteCbx;
        }

        /******************************************************************************************
         * Set the reference to the overwrite check box
         *
         * @param overwriteCbx
         *            reference to the overwrite check box
         *****************************************************************************************/
        private void setOverwriteCbx(JCheckBox overwriteCbx)
        {
            this.overwriteCbx = overwriteCbx;
        }

        /******************************************************************************************
         * Get the reference to the message name pattern label
         *
         * @return Reference to the message name pattern label
         *****************************************************************************************/
        private JLabel getPatternLbl()
        {
            return patternLbl;
        }

        /******************************************************************************************
         * Set the reference to the message name pattern label
         *
         * @param patternLbl
         *            reference to the message name pattern label
         *****************************************************************************************/
        private void setPatternLbl(JLabel patternLbl)
        {
            this.patternLbl = patternLbl;
        }

        /******************************************************************************************
         * Get the reference to the message name pattern input field
         *
         * @return Reference to the message name pattern input field
         *****************************************************************************************/
        private JTextField getPatternFld()
        {
            return patternFld;
        }

        /******************************************************************************************
         * Set the reference to the message name pattern input field
         *
         * @param patternFld
         *            reference to the message name pattern input field
         *****************************************************************************************/
        private void setPatternFld(JTextField patternFld)
        {
            this.patternFld = patternFld;
        }
    }

    /**********************************************************************************************
     * Message name and ID assignment dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param msgIDDialogType
     *            message ID dialog type: TABLE_DATA_FIELD for structure and command messages,
     *            TELEMETRY for telemetry messages (MessageIDType)
     *
     * @param messages
     *            list of telemetry messages (only used for telemetry messages dialog)
     *
     * @param schedulerDlg
     *            component over which to center the dialog (only used for telemetry messages
     *            dialog)
     *********************************************************************************************/
    CcddAssignMessageIDDialog(CcddMain ccddMain,
                              MessageIDType msgIDDialogType,
                              List<Message> messages,
                              CcddTelemetrySchedulerDialog schedulerDlg)
    {
        this.ccddMain = ccddMain;
        this.msgIDDialogType = msgIDDialogType;
        this.messages = messages;
        this.schedulerDlg = schedulerDlg;

        // Get references to shorten subsequent calls
        messageIDHandler = ccddMain.getMessageIDHandler();
        eventLog = ccddMain.getSessionEventLog();

        // Create the message name and ID assignment dialog
        initialize();
    }

    /**********************************************************************************************
     * Message ID assignment dialog class constructor for structure and command messages
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddAssignMessageIDDialog(CcddMain ccddMain)
    {
        this(ccddMain, MessageIDType.TABLE_DATA_FIELD, null, null);
    }

    /**********************************************************************************************
     * Message name and ID assignment dialog class constructor for telemetry messages
     *
     * @param ccddMain
     *            main class
     *
     * @param messages
     *            list of telemetry messages
     *
     * @param schedulerDlg
     *            component over which to center the dialog
     *********************************************************************************************/
    CcddAssignMessageIDDialog(CcddMain ccddMain,
                              List<Message> messages,
                              CcddTelemetrySchedulerDialog schedulerDlg)
    {
        this(ccddMain, MessageIDType.TELEMETRY, messages, schedulerDlg);
    }

    /**********************************************************************************************
     * Create the message ID assignment dialog
     *********************************************************************************************/
    private void initialize()
    {
        // Build the message ID assignment dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            /**************************************************************************************
             * Build the message ID assignment dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                dbTable = ccddMain.getDbTableCommandHandler();
                tableTypeHandler = ccddMain.getTableTypeHandler();
                inputTypeHandler = ccddMain.getInputTypeHandler();
                fieldHandler = ccddMain.getFieldHandler();

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
                                                                           ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                           0,
                                                                           ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                                0,
                                                                0);

                // Create borders for the input fields and assignment panels
                border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                            Color.LIGHT_GRAY,
                                                                                            Color.GRAY),
                                                            BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
                etchBorder = BorderFactory.createEtchedBorder();

                // Create a tabbed pane to contain the message name/ID parameters and add it to the
                // dialog
                tabbedPane = new JTabbedPane(SwingConstants.LEFT);
                tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                dialogPnl.add(tabbedPane, gbc);
                dialogPnl.setBorder(etchBorder);

                // Check if this is the table and group message ID assignment dialog
                if (msgIDDialogType == MessageIDType.TABLE_DATA_FIELD)
                {
                    // Create the information for each table type tab and the group tab
                    msgTabs = new MsgTabInfo[] {new MsgTabInfo(TYPE_STRUCTURE,
                                                               "structure",
                                                               "Structure message ID assignment"),
                                                new MsgTabInfo(TYPE_COMMAND,
                                                               "command",
                                                               "Command message ID assignment"),
                                                new MsgTabInfo(TYPE_OTHER,
                                                               "other table",
                                                               "Other table message ID assignment"),
                                                new MsgTabInfo("Group",
                                                               "group",
                                                               "Group message ID assignment")};

                    // Step through each tab information
                    for (MsgTabInfo tabInfo : msgTabs)
                    {
                        // Add a tab for the table type
                        addMessageIDTab(tabInfo, false);
                    }
                }
                // This is the telemetry message ID assignment dialog
                else
                {
                    // Create the information for the telemetry message name and ID assignment
                    msgTabs = new MsgTabInfo[] {new MsgTabInfo("Message Name",
                                                               "telemetry",
                                                               "Telemetry message name assignment"),
                                                new MsgTabInfo("Message ID",
                                                               "telemetry",
                                                               "Telemetry message ID assignment")};

                    // Add the telemetry message name and ID tabs
                    addMessageIDTab(msgTabs[0], true);
                    addMessageIDTab(msgTabs[1], false);
                }
            }

            /**************************************************************************************
             * Message ID assignment dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check if this is the table and group message ID assignment dialog
                if (msgIDDialogType == MessageIDType.TABLE_DATA_FIELD)
                {
                    // Get the user's input and check if at least one of assignment check boxes is
                    // selected
                    if (showOptionsDialog(ccddMain.getMainFrame(),
                                          dialogPnl,
                                          "Assign Message IDs",
                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                        && getNumAssignTypeSelected() != 0)
                    {
                        // Assign the table and/or group message IDs
                        performTableGroupMessageIDAssignment();
                    }
                }
                // This is the telemetry message ID/name assignment dialog
                else
                {
                    // Get the user's input and check if at least one of assignment check boxes is
                    // selected
                    if (showOptionsDialog(ccddMain.getMainFrame(),
                                          dialogPnl,
                                          "Assign Message Names and IDs",
                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                        && getNumAssignTypeSelected() != 0)
                    {
                        // Assign the telemetry message IDs and/or names
                        performTelemetryMessageIDNameAssignment();
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Add a tab for the specified table type or the telemetry message name/ID
     *
     * @param tabInfo
     *            message name/ID tab information reference
     *
     * @param isTlmName
     *            true if this is the tab for the telemetry message name assignment
     *********************************************************************************************/
    private void addMessageIDTab(final MsgTabInfo tabInfo, final boolean isTlmName)
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
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
            tabInfo.getPatternLbl().setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            inputPnl.add(tabInfo.getPatternLbl(), gbc);

            // Create the telemetry message name pattern field
            tabInfo.setPatternFld(new JTextField("Message_%03d", 12));
            tabInfo.getPatternFld().setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            tabInfo.getPatternFld().setEditable(true);
            tabInfo.getPatternFld().setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            tabInfo.getPatternFld().setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            tabInfo.getPatternFld().setBorder(border);
            tabInfo.getPatternFld().setToolTipText(CcddUtilities.wrapText("<html>Format: <i>alphanumeric</i>#<i>&lt;alphanumeric&gt;",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Create an input field verifier for the pattern field
            tabInfo.getPatternFld().setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the pattern field
                // value if an invalid value is entered. Initialize to the value at the time the
                // field is created
                String lastValid = tabInfo.getPatternFld().getText();

                /**********************************************************************************
                 * Verify the contents of a the pattern field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    boolean isValid = true;

                    // Check if the message name pattern isn't in the format
                    // <alphanumeric>%<0#>d<text> where # is one or more digits
                    if (!tabInfo.getPatternFld().getText().matches(DefaultInputType.ALPHANUMERIC.getInputMatch()
                                                                   + "%(0\\d+)?d[a-zA-Z0-9_]*"))
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddAssignMessageIDDialog.this,
                                                                  "<html><b>Message name pattern must be in the format:<br><br></b>"
                                                                                                  + "&#160;&#160;&#160;<i>startText</i>%&lt;0#&gt;d<i>&lt;"
                                                                                                  + "endText&gt;</i><b><br><br>where </b><i>startText</i><b> "
                                                                                                  + "and </b><i>endText</i><b> consist of alphanumeric "
                                                                                                  + "characters and/or underscores, </b><i>startText</i><b> "
                                                                                                  + "begins with a letter or underscore, and </b><i>#</i><b> "
                                                                                                  + "is one or more digits.&#160;&#160;Note: </b><i>0#</i><b> "
                                                                                                  + "and </b><i>endText</i><b> are optional",
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the previous value in the field
                        tabInfo.getPatternFld().setText(lastValid);

                        // Set the flag indicating the input is invalid
                        isValid = false;

                        // Toggle the controls enable status so that the buttons are redrawn
                        // correctly
                        CcddAssignMessageIDDialog.this.setControlsEnabled(false);
                        CcddAssignMessageIDDialog.this.setControlsEnabled(true);
                    }
                    // The value is valid
                    else
                    {
                        // Clean up the field value
                        tabInfo.getPatternFld().setText(tabInfo.getPatternFld().getText().trim());

                        // Store the new value as the last valid value
                        lastValid = tabInfo.getPatternFld().getText();
                    }

                    return isValid;
                }
            });

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
        tabInfo.getStartLbl().setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        inputPnl.add(tabInfo.getStartLbl(), gbc);

        // Create the starting message number/ID field
        tabInfo.setStartFld(new JTextField((isTlmName
                                                      ? "0"
                                                      : "0x0"),
                                           7));
        tabInfo.getStartFld().setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        tabInfo.getStartFld().setEditable(true);
        tabInfo.getStartFld().setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        tabInfo.getStartFld().setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        tabInfo.getStartFld().setBorder(border);
        tabInfo.getStartFld().setToolTipText(isTlmName
                                                       ? null
                                                       : CcddUtilities.wrapText("<html>Format: <i>&lt;</i>0x<i>&gt;hexadecimal digits",
                                                                                ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        // Create an input field verifier for the start field
        tabInfo.getStartFld().setInputVerifier(new InputVerifier()
        {
            // Storage for the last valid value entered; used to restore the start field value if
            // an invalid value is entered. Initialize to the value at the time the field is
            // created
            String lastValid = tabInfo.getStartFld().getText();

            /**************************************************************************************
             * Verify the contents of a the start field
             *************************************************************************************/
            @Override
            public boolean verify(JComponent input)
            {
                boolean isValid = true;

                // Check if this is the telemetry message name assignment tab
                if (isTlmName)
                {
                    // Check if the starting message number is not a non-negative integer
                    if (!tabInfo.getStartFld().getText().matches(DefaultInputType.INT_NON_NEGATIVE.getInputMatch()))
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddAssignMessageIDDialog.this,
                                                                  "<html><b>Message starting number must be an integer >= 0",
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the previous value in the field
                        tabInfo.getStartFld().setText(lastValid);

                        // Set the flag indicating the input is invalid
                        isValid = false;

                        // Toggle the controls enable status so that the buttons are redrawn
                        // correctly
                        CcddAssignMessageIDDialog.this.setControlsEnabled(false);
                        CcddAssignMessageIDDialog.this.setControlsEnabled(true);
                    }
                    // The value is valid
                    else
                    {
                        // Clean up the field value
                        tabInfo.getStartFld().setText(DefaultInputType.INT_NON_NEGATIVE.formatInput(tabInfo.getStartFld().getText()));

                        // Store the new value as the last valid value
                        lastValid = tabInfo.getStartFld().getText();
                    }
                }
                // Not the telemetry message name assignment tab
                else
                {
                    // Check if the starting message ID value is not in hexadecimal format
                    if (!tabInfo.getStartFld().getText().matches(DefaultInputType.HEXADECIMAL.getInputMatch()))
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddAssignMessageIDDialog.this,
                                                                  "<html><b>Starting ID must be in the format<br>&#160;&#160;<i>&lt;</i>"
                                                                                                  + "0x<i>&gt;</i>#<br>where # is one or "
                                                                                                  + "more hexadecimal digits",
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the previous value in the field
                        tabInfo.getStartFld().setText(lastValid);

                        isValid = false;
                    }
                    // The value is valid
                    else
                    {
                        // Clean up the field value
                        tabInfo.getStartFld().setText(DefaultInputType.HEXADECIMAL.formatInput(tabInfo.getStartFld().getText()));

                        // Store the new value as the last valid value
                        lastValid = tabInfo.getStartFld().getText();
                    }
                }

                return isValid;
            }
        });

        gbc.gridx++;
        inputPnl.add(tabInfo.getStartFld(), gbc);

        // Create the message name/ID interval label
        tabInfo.setIntervalLbl(new JLabel((isTlmName
                                                     ? "Message"
                                                     : "ID")
                                          + " interval"));
        tabInfo.getIntervalLbl().setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(tabInfo.getIntervalLbl(), gbc);

        // Create the message number/ID interval field
        tabInfo.setIntervalFld(new JTextField("1", 5));
        tabInfo.getIntervalFld().setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        tabInfo.getIntervalFld().setEditable(true);
        tabInfo.getIntervalFld().setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        tabInfo.getIntervalFld().setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        tabInfo.getIntervalFld().setBorder(border);

        // Create an input field verifier for the interval field
        tabInfo.getIntervalFld().setInputVerifier(new InputVerifier()
        {
            // Storage for the last valid value entered; used to restore the interval field value
            // if an invalid value is entered. Initialize to the value at the time the field is
            // created
            String lastValid = tabInfo.getIntervalFld().getText();

            /**************************************************************************************
             * Verify the contents of a the interval field
             *************************************************************************************/
            @Override
            public boolean verify(JComponent input)
            {
                boolean isValid = true;

                // Check if the message ID interval value is not a positive integer
                if (!tabInfo.getIntervalFld().getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                {
                    // Inform the user that the input value is invalid
                    new CcddDialogHandler().showMessageDialog(CcddAssignMessageIDDialog.this,
                                                              "<html><b>ID interval must be a positive integer",
                                                              "Missing/Invalid Input",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);

                    // Restore the previous value in the field
                    tabInfo.getIntervalFld().setText(lastValid);

                    // Set the flag indicating the input is invalid
                    isValid = false;

                    // Toggle the controls enable status so that the buttons are redrawn
                    // correctly
                    CcddAssignMessageIDDialog.this.setControlsEnabled(false);
                    CcddAssignMessageIDDialog.this.setControlsEnabled(true);
                }
                // The value is valid
                else
                {
                    // Clean up the field value
                    tabInfo.getIntervalFld().setText(DefaultInputType.INT_POSITIVE.formatInput(tabInfo.getIntervalFld().getText()));

                    // Store the new value as the last valid value
                    lastValid = tabInfo.getIntervalFld().getText();
                }

                return isValid;
            }
        });

        gbc.gridx++;
        inputPnl.add(tabInfo.getIntervalFld(), gbc);

        // Create the check box to enable message name/ID assignment
        tabInfo.setAssignCbx(new JCheckBox("Assign "
                                           + tabInfo.getType()
                                           + " message "
                                           + (isTlmName
                                                        ? "names"
                                                        : "IDs")));
        tabInfo.getAssignCbx().setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Add a listener for check box selection changes
        tabInfo.getAssignCbx().addItemListener(new ItemListener()
        {
            /**************************************************************************************
             * Set the remaining inputs based on the check box selection state
             *************************************************************************************/
            @Override
            public void itemStateChanged(ItemEvent ae)
            {
                setTabGUIComponentsEnable(tabInfo, tabInfo.getAssignCbx().isSelected());
            }
        });

        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        tabPnl.add(tabInfo.getAssignCbx(), gbc);

        // Add the input panel to the tab panel
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.gridy++;
        tabPnl.add(inputPnl, gbc);

        // Create the overwrite existing name/IDs check box
        tabInfo.setOverwriteCbx(new JCheckBox("Overwrite existing "
                                              + (isTlmName
                                                           ? "names"
                                                           : "IDs")));
        tabInfo.getOverwriteCbx().setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridy++;
        tabPnl.add(tabInfo.getOverwriteCbx(), gbc);

        // Add the tab
        tabbedPane.addTab(tabInfo.getName(), null, tabPnl, tabInfo.getToolTip());

        // Disable the tab components initially
        setTabGUIComponentsEnable(tabInfo, false);
    }

    /**********************************************************************************************
     * Set the enable state of the specified table type or telemetry message name/ID input fields
     * and check box
     *
     * @param tabInfo
     *            message name/ID tab information reference
     *
     * @param enable
     *            true to enable the message name/ID input fields and check box; false to disable
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Get the number of selected assignment check boxes
     *
     * @return The number of selected assignment check boxes
     *********************************************************************************************/
    private int getNumAssignTypeSelected()
    {
        int numSelected = 0;

        // Step through each tab
        for (MsgTabInfo tabInfo : msgTabs)
        {
            // Check if the assignment check box is selected
            if (tabInfo.getAssignCbx().isSelected())
            {
                // Set the flag indicating a check box is selected and stop searching
                numSelected++;
            }
        }

        return numSelected;
    }

    /**********************************************************************************************
     * Assign the table and/or group message IDs
     *********************************************************************************************/
    private void performTableGroupMessageIDAssignment()
    {
        // Execute the commands to assign table/group message IDs (or names) in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Assign table/group message IDs command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the message ID assignment cancellation dialog
                haltDlg = new CcddHaltDialog("Assign Message IDs",
                                             "Assigning table/group message IDs",
                                             "ID assignment",
                                             getNumAssignTypeSelected(),
                                             1,
                                             CcddAssignMessageIDDialog.this);

                // Get the list of message IDs that are reserved or already in use
                idsInUse = messageIDHandler.getMessageIDsInUse(!msgTabs[0].getOverwriteCbx().isSelected(),
                                                               !msgTabs[1].getOverwriteCbx().isSelected(),
                                                               !msgTabs[2].getOverwriteCbx().isSelected(),
                                                               !msgTabs[3].getOverwriteCbx().isSelected(),
                                                               true,
                                                               false,
                                                               null,
                                                               false,
                                                               CcddAssignMessageIDDialog.this);

                // Get a copy of the data field definitions for all of the fields in the database
                List<FieldInformation> fieldInformation = CcddFieldHandler.getFieldInformationCopy(fieldHandler.getFieldInformation());

                // Sort the field information by table name so that sequence order of the message
                // ID values is applied to the tables' alphabetical order
                Collections.sort(fieldInformation, new Comparator<FieldInformation>()
                {
                    /******************************************************************************
                     * Compare the table names of two field definitions, ignoring case
                     *****************************************************************************/
                    @Override
                    public int compare(FieldInformation fld1, FieldInformation fld2)
                    {
                        return fld1.getOwnerName().compareToIgnoreCase(fld2.getOwnerName());
                    }
                });

                boolean fieldChanged = false;

                // Check if the structure table message IDs should be assigned
                if (msgTabs[0].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Structure tables", -1);

                    // Assign the structure table and data field IDS, and update the flag that
                    // indicates if a data field changed
                    fieldChanged |= assignTableMessageIDs(msgTabs[0],
                                                          messageIDHandler.getStructureTables(),
                                                          fieldInformation);
                }

                // Check if the command table message IDs should be assigned
                if (msgTabs[1].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Command tables", -1);

                    // Assign the command table and data field IDS, and update the flag that
                    // indicates if a data field changed
                    fieldChanged |= assignTableMessageIDs(msgTabs[1],
                                                          messageIDHandler.getCommandTables(),
                                                          fieldInformation);
                }

                // Check if the other table message IDs should be assigned
                if (msgTabs[2].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Other tables", -1);

                    // Assign the other (not structure or command) table and data field IDS, and
                    // update the flag that indicates if a data field changed
                    fieldChanged |= assignTableMessageIDs(msgTabs[2],
                                                          messageIDHandler.getOtherTables(),
                                                          fieldInformation);
                }

                // Check if the group message IDs should be assigned
                if (msgTabs[3].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Groups", -1);

                    // Assign the group data field IDS, and update the flag that indicates if a
                    // data field changed
                    fieldChanged |= assignGroupMessageIDs(msgTabs[3], fieldInformation);
                }

                // Check if a structure, command, or other table type data field value, or a group
                // message ID data field value changed
                if (fieldChanged)
                {
                    // Store the updated field information in the field handler and in the database
                    fieldHandler.setFieldInformation(fieldInformation);
                    dbTable.storeInformationTableInBackground(InternalTable.FIELDS,
                                                              fieldHandler.getFieldDefnsFromInfo(),
                                                              null,
                                                              CcddAssignMessageIDDialog.this);

                    // Check if the data field editor table dialog is open
                    if (ccddMain.getFieldTableEditor() != null
                        && ccddMain.getFieldTableEditor().isShowing())
                    {
                        // Update the data field editor table
                        ccddMain.getFieldTableEditor().getTable().loadAndFormatData();
                    }
                }
            }

            /**************************************************************************************
             * Table/group message ID assignment complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Update the message name & ID reference list
                inputTypeHandler.updateMessageReferences(CcddAssignMessageIDDialog.this);

                // Check if the user didn't cancel message ID assignment
                if (!haltDlg.isHalted())
                {
                    // Close the cancellation dialog
                    haltDlg.closeDialog();

                    // Add a log entry indication the message ID assignment completed
                    eventLog.logEvent(STATUS_MSG, "Table/group message ID assignment completed");
                }
                // Message ID assignment was canceled
                else
                {
                    eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                                      "Table/group message ID assignment terminated by user");
                }

                haltDlg = null;
            }
        });
    }

    /**********************************************************************************************
     * Assign the telemetry message IDs and/or names
     *********************************************************************************************/
    private void performTelemetryMessageIDNameAssignment()
    {
        // Execute the commands to assign telemetry message IDs and/or names in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /**************************************************************************************
             * Assign telemetry message IDs and/or names command
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create the message ID assignment cancellation dialog
                haltDlg = new CcddHaltDialog("Assign Message IDs",
                                             "Assigning telemetry message names/IDs",
                                             "name/ID assignment",
                                             getNumAssignTypeSelected(),
                                             1,
                                             CcddAssignMessageIDDialog.this);

                // Get the list of message IDs that are reserved or already in use
                idsInUse = messageIDHandler.getMessageIDsInUse(true,
                                                               true,
                                                               true,
                                                               true,
                                                               false,
                                                               msgTabs[1].getOverwriteCbx().isSelected(),
                                                               schedulerDlg,
                                                               false,
                                                               CcddAssignMessageIDDialog.this);

                // Check if the message names should be assigned
                if (msgTabs[0].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Message names", -1);

                    // Update the telemetry message names
                    assignTelemetryMessageNames(msgTabs[0]);

                    // Update the options list with the new message names
                    schedulerDlg.getSchedulerHandler().getTelemetryOptions();
                }

                // Check if the telemetry message IDs should be assigned
                if (msgTabs[1].getAssignCbx().isSelected())
                {
                    // Update the progress bar text
                    haltDlg.updateProgressBar("Message IDs", -1);

                    // Update the telemetry message IDs
                    assignTelemetryMessageIDs(msgTabs[1]);
                }

                // Update the scheduler table
                schedulerDlg.getSchedulerHandler().getSchedulerEditor().updateSchedulerTable(true);
            }

            /**************************************************************************************
             * Telemetry message ID and/or name assignment complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Update the message name & ID reference list
                inputTypeHandler.updateMessageReferences(CcddAssignMessageIDDialog.this);

                // Check if the user didn't cancel message ID assignment
                if (!haltDlg.isHalted())
                {
                    // Close the cancellation dialog
                    haltDlg.closeDialog();

                    // Add a log entry indication the message ID/name assignment completed
                    eventLog.logEvent(STATUS_MSG,
                                      "Telemetry message ID/name assignment completed");
                }
                // Message ID assignment was canceled
                else
                {
                    eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                                      "Telemetry message ID/name assignment terminated by user");
                }

                haltDlg = null;
            }
        });
    }

    /**********************************************************************************************
     * Assign message ID values to the structure, command, or other table type message ID columns
     * and data fields
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
     *********************************************************************************************/
    private boolean assignTableMessageIDs(MsgTabInfo tabInfo,
                                          List<String> tables,
                                          List<FieldInformation> fieldInformation)
    {
        boolean isChanges = false;

        // Get the starting message ID, ID interval, and format length (to preserve leading zeroes)
        int startID = Integer.decode(tabInfo.getStartFld().getText());
        int interval = Integer.valueOf(tabInfo.getIntervalFld().getText());
        int formatLength = tabInfo.getStartFld().getText().length() - 2;

        // ////////////////////////////////////////////////////////////////////////////////////////
        // First assign message IDs to all table columns with a message ID input type for those
        // tables of the current table type
        // ////////////////////////////////////////////////////////////////////////////////////////
        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Check if the user canceled ID assignment
            if (haltDlg.isHalted())
            {
                break;
            }

            // Check if the tab information type and table type match the table type definition
            if ((tabInfo.getName().equals(TYPE_STRUCTURE) && typeDefn.isStructure())
                || tabInfo.getName().equals(TYPE_COMMAND) && typeDefn.isCommand()
                || (tabInfo.getName().equals(TYPE_OTHER)
                    && !typeDefn.isStructure()
                    && !typeDefn.isCommand()))
            {
                // Get a list of the columns in this table type that display a message name and ID
                List<Integer> msgNameIDColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.MESSAGE_NAME_AND_ID);

                // Check if the table type has any columns that display a message name and ID
                if (!msgNameIDColumns.isEmpty())
                {
                    // Step through each table
                    for (String tablePath : tables)
                    {
                        // Check if the user canceled ID assignment
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Load the table's information from the project database
                        TableInformation tableInformation = dbTable.loadTableData(tablePath,
                                                                                  false,
                                                                                  false,
                                                                                  CcddAssignMessageIDDialog.this);

                        // Check that the table data loaded and if the table's type matches that of
                        // the current type definition
                        if (!tableInformation.isErrorFlag()
                            && tableInformation.getType().equals(typeDefn.getName()))
                        {
                            int varNameColumn = -1;

                            // Check if the table type represents a structure
                            if (typeDefn.isStructure())
                            {
                                // Get the index of the variable name column
                                varNameColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
                            }

                            // Create a table editor handler, but without displaying the editor
                            // itself
                            CcddTableEditorHandler editor = new CcddTableEditorHandler(ccddMain,
                                                                                       tableInformation,
                                                                                       null);

                            // Check if the table arrays aren't expanded
                            if (!editor.isExpanded())
                            {
                                // Expand the table arrays
                                editor.showHideArrayMembers();
                            }

                            // Get the table's data (again if a name change occurred since changes
                            // were made)
                            Object[][] tableData = editor.getTable().getTableData(false);

                            // Step through each row in the table
                            for (int row = 0; row < editor.getTable().getModel().getRowCount(); row++)
                            {
                                // Check if the user canceled ID assignment
                                if (haltDlg.isHalted())
                                {
                                    break;
                                }

                                // Step through each column that contains a message name and ID
                                for (int nameIDColumn : msgNameIDColumns)
                                {
                                    // Check if the user canceled ID assignment
                                    if (haltDlg.isHalted())
                                    {
                                        break;
                                    }

                                    // Check if the cell's value can be changed, if existing
                                    // values should be overwritten or if the cell is empty, and if
                                    // the table is a structure that the variable is not a padding
                                    // variable
                                    if (editor.getTable().isCellEditable(editor.getTable().convertRowIndexToView(row),
                                                                         editor.getTable().convertColumnIndexToView(nameIDColumn))
                                        && !tableData[row][nameIDColumn].toString().endsWith(PROTECTED_MSG_ID_IDENT)
                                        && (tabInfo.getOverwriteCbx().isSelected()
                                            || tableData[row][nameIDColumn].toString().isEmpty())
                                        && (varNameColumn == -1
                                            || !tableData[row][varNameColumn].toString().matches(PAD_VARIABLE_MATCH)))
                                    {
                                        // Set the column message ID value to the next unused
                                        // message ID
                                        startID = getNextMessageID(startID, interval);
                                        tableData[row][nameIDColumn] = updateMessageID(tableData[row][nameIDColumn].toString(),
                                                                                       startID,
                                                                                       formatLength);
                                    }
                                }
                            }

                            // Check if the a message ID in the table was changed
                            if (editor.getTable().isTableChanged(tableData))
                            {
                                // Load the updated array of data into the table
                                editor.getTable().loadDataArrayIntoTable(tableData, false);

                                // Build the table updates
                                editor.buildUpdates();

                                // Make the table modifications to the project database and to any
                                // open table editors
                                dbTable.modifyTableData(editor.getTableInformation(),
                                                        editor.getAdditions(),
                                                        editor.getModifications(),
                                                        editor.getDeletions(),
                                                        true,
                                                        false,
                                                        false,
                                                        false,
                                                        false,
                                                        null,
                                                        CcddAssignMessageIDDialog.this);
                            }
                        }
                    }
                }
            }
        }

        // ////////////////////////////////////////////////////////////////////////////////////////
        // Next assign message IDs to table data fields
        // ////////////////////////////////////////////////////////////////////////////////////////
        // Step through each defined data field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Check if the user canceled ID assignment
            if (haltDlg.isHalted())
            {
                break;
            }

            // Get the reference to the field information
            FieldInformation fieldInfo = fieldInformation.get(index);

            // Check if the field contains a message name and ID, the field owner matches one of
            // the supplied tables, the field is applicable if the table is a structure based on
            // the structure's root/child status, and that either the overwrite check box is
            // selected or the field is blank
            if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID))
                && tables.contains(fieldInfo.getOwnerName())
                && !fieldInfo.getValue().endsWith(PROTECTED_MSG_ID_IDENT)
                && (fieldInfo.getApplicabilityType() == ApplicabilityType.ALL
                    || (fieldInfo.getApplicabilityType() == ApplicabilityType.ROOT_ONLY
                        && dbTable.isRootStructure(fieldInfo.getOwnerName()))
                    || (fieldInfo.getApplicabilityType() == ApplicabilityType.CHILD_ONLY
                        && !dbTable.isRootStructure(fieldInfo.getOwnerName())))
                && (tabInfo.getOverwriteCbx().isSelected() || fieldInfo.getValue().isEmpty()))
            {
                // Set the message ID data field value to the next unused message ID
                startID = getNextMessageID(startID, interval);
                fieldInfo.setValue(updateMessageID(fieldInfo.getValue(), startID, formatLength));

                // If any table editor dialogs are open then the displayed data fields need to be
                // updated to match the message ID value change. Both the currently displayed and
                // committed field values are updated so that when the editor dialog is closed
                // these changes aren't seen as table changes since they're already committed to
                // the database. Step through each table editor dialog
                for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                {
                    // Check if the user canceled ID assignment
                    if (haltDlg.isHalted())
                    {
                        break;
                    }

                    boolean isUpdate = false;

                    // Step through each table editor in the editor dialog
                    for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                    {
                        // Check if the user canceled ID assignment
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Check if the table contains the message ID field; if so then update it
                        // to the new value
                        if (fieldHandler.updateField(fieldInfo))
                        {
                            // Update the current and committed data field information for this
                            // table
                            editor.updateTableFieldInformationFromHandler();

                            // Update the editor data field components from the field values
                            editor.updateFieldComponentFromValue(editor.getTableInformation().getFieldInformation());

                            // Rebuild the data field panel in the table editor using the updated
                            // fields
                            editor.createDataFieldPanel(false,
                                                        editor.getTableInformation().getFieldInformation());

                            // Set the flag to indicate the table/field combination was located and
                            // stop searching
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

    /**********************************************************************************************
     * Assign message ID values to the group data fields
     *
     * @param tabInfo
     *            message ID tab information reference
     *
     * @param fieldInformation
     *            list of data field information
     *
     * @return true if a message ID value changed
     *********************************************************************************************/
    private boolean assignGroupMessageIDs(MsgTabInfo tabInfo,
                                          List<FieldInformation> fieldInformation)
    {
        boolean isChanges = false;

        // Get the starting message ID, ID interval, and format length (to preserve leading zeroes)
        int startID = Integer.decode(tabInfo.getStartFld().getText());
        int interval = Integer.valueOf(tabInfo.getIntervalFld().getText());
        int formatLength = tabInfo.getStartFld().getText().length() - 2;

        // Step through each defined data field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Check if the user canceled ID assignment
            if (haltDlg.isHalted())
            {
                break;
            }

            // Get the reference to the field information
            FieldInformation fieldInfo = fieldInformation.get(index);

            // Check if the field contains a message name and ID, is for a group, and that either
            // the overwrite check box is selected or the field is blank
            if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID))
                && fieldInfo.getOwnerName().startsWith(GROUP_DATA_FIELD_IDENT)
                && !fieldInfo.getValue().endsWith(PROTECTED_MSG_ID_IDENT)
                && (tabInfo.getOverwriteCbx().isSelected() || fieldInfo.getValue().isEmpty()))
            {
                // Set the message ID data field value to the next unused message ID
                startID = getNextMessageID(startID, interval);
                fieldInfo.setValue(updateMessageID(fieldInfo.getValue(), startID, formatLength));

                // Set the flag to indicate a message ID value is changed
                isChanges = true;
            }
        }

        return isChanges;
    }

    /**********************************************************************************************
     * Assign the telemetry message names
     *
     * @param tlmName
     *            telemetry message name tab information reference
     *********************************************************************************************/
    private void assignTelemetryMessageNames(MsgTabInfo tlmName)
    {
        // Get the message starting number and interval values
        int startNum = Integer.valueOf(tlmName.getStartFld().getText());
        int interval = Integer.valueOf(tlmName.getIntervalFld().getText());

        // Step through each message
        for (Message message : messages)
        {
            // Check if the user canceled ID assignment
            if (haltDlg.isHalted())
            {
                break;
            }

            // Use the pattern and current message number to create the message name
            String msgName = String.format(tlmName.getPatternFld().getText(), startNum);

            // Adjust the message number by the interval amount
            startNum += interval;

            // Store the message's current name, then change the name to the new one
            String originalName = message.getName();
            message.setName(msgName);

            // Check if the message has sub-messages other than the default
            if (message.getNumberOfSubMessages() > 1)
            {
                // Step through each of the message's sub-messages
                for (Message subMessage : message.getSubMessages())
                {
                    // Check if the user canceled ID assignment
                    if (haltDlg.isHalted())
                    {
                        break;
                    }

                    // Update the sub-message name to match the new pattern
                    subMessage.setName(subMessage.getName().replaceFirst(Pattern.quote(originalName),
                                                                         msgName));
                }
            }
        }
    }

    /**********************************************************************************************
     * Assign the telemetry message IDs
     *
     * @param tlmID
     *            telemetry message ID tab information reference
     *********************************************************************************************/
    private void assignTelemetryMessageIDs(MsgTabInfo tlmID)
    {
        // Get the starting message ID, ID interval, and format length (to preserve leading zeroes)
        int startID = Integer.decode(tlmID.getStartFld().getText());
        int interval = Integer.valueOf(tlmID.getIntervalFld().getText());
        int formatLength = tlmID.getStartFld().getText().length() - 2;

        // Get the first unused message ID, beginning with the ID provided
        startID = getNextMessageID(startID, interval);

        // Step through each message
        for (Message message : messages)
        {
            // Check if the user canceled ID assignment
            if (haltDlg.isHalted())
            {
                break;
            }

            // Set the message's ID to the next one in the sequence
            startID = setMessageID(tlmID, message, startID, interval, formatLength);

            // Step through each of the message's sub-messages. The default sub-message is skipped
            // since its ID gets set when the parent message's ID is set
            for (int index = 1; index < message.getSubMessages().size(); index++)
            {
                // Check if the user canceled ID assignment
                if (haltDlg.isHalted())
                {
                    break;
                }

                // Set the sub-message's ID to the next one in the sequence (for the default
                // sub-message use the parent message's ID
                startID = setMessageID(tlmID,
                                       message.getSubMessage(index),
                                       startID,
                                       interval,
                                       formatLength);
            }
        }
    }

    /**********************************************************************************************
     * Set the message ID. If the message already has an ID only update it if the overwrite check
     * box is selected
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
     * @param formatLength
     *            length of the formatted message ID; used to preserve the number of leading zeroes
     *
     * @return Next ID value in the sequence
     *********************************************************************************************/
    private int setMessageID(MsgTabInfo tlmID,
                             Message message,
                             int idValue,
                             int interval,
                             int formatLength)
    {
        int nextID = idValue;

        // Check if the message has no ID, or if it does, that the overwrite ID check box is
        // selected
        if (!message.getID().endsWith(PROTECTED_MSG_ID_IDENT)
            && (message.getID().isEmpty() || tlmID.getOverwriteCbx().isSelected()))
        {
            // Get the next unused message ID value
            nextID = getNextMessageID(idValue, interval);

            // Set the message ID
            message.setID(CcddInputTypeHandler.formatInput(String.format("0x%0"
                                                                         + formatLength
                                                                         + "x",
                                                                         idValue),
                                                           InputTypeFormat.HEXADECIMAL,
                                                           true));
        }

        return nextID;
    }

    /**********************************************************************************************
     * Get the next unused message ID value
     *
     * @param idValue
     *            ID value from which to start checking
     *
     * @param interval
     *            difference between two contiguous message ID values
     *
     * @return Next unused ID value
     *********************************************************************************************/
    private int getNextMessageID(int idValue, int interval)
    {
        // Continue to loop as long as the ID value matches a reserved or existing one. This
        // prevents assigning a duplicate ID
        while (idsInUse.contains(idValue))
        {
            // Adjust the message ID value by the interval amount and set the flag to indicate a
            // message ID value is changed
            idValue += interval;
        }

        // Add the message ID to the list of those in use
        idsInUse.add(idValue);

        return idValue;
    }

    /**********************************************************************************************
     * Replace the message ID an a message name and ID string, then reformat the name and ID
     *
     * @param idValue
     *            ID value
     *
     * @param nameID
     *            message name and ID
     *
     * @param formatLength
     *            length of the formatted message ID; used to preserve the number of leading zeroes
     *
     * @return Formatted message name and ID, using the new ID value
     *********************************************************************************************/
    private String updateMessageID(String nameID, int idValue, int formatLength)
    {
        // Get the message name from the message name and ID
        String msgName = CcddMessageIDHandler.getMessageName(nameID);

        // Replace the message ID, then recombine the name and ID and reformat the result
        return CcddInputTypeHandler.formatInput((msgName
                                                 + " "
                                                 + String.format("0x%0" + formatLength + "x",
                                                                 idValue)).trim(),
                                                InputTypeFormat.MESSAGE_ID,
                                                true);
    }
}
