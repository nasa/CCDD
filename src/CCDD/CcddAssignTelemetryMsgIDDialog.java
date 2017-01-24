/**
 * CFS Command & Data Dictionary telemetry message name and ID assignment.
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved. dialog
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.Message;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;

/******************************************************************************
 * CFS Command & Data Dictionary telemetry message name and ID assignment
 * dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddAssignTelemetryMsgIDDialog extends CcddDialogHandler
{
    // Class reference
    private final CcddTelemetrySchedulerDialog schedulerDlg;

    // Components that need to be accessed by multiple methods
    private JCheckBox assignMsgCbx;
    private JTextField msgPatternFld;
    private JTextField startMsgNumFld;
    private JTextField msgIntervalFld;
    private JCheckBox assignIDCbx;
    private JTextField startMsgIDFld;
    private JTextField msgIDIntervalFld;
    private JCheckBox overwriteIDCbx;

    // List of messages and their sub-messages
    private final List<Message> messages;

    /**************************************************************************
     * Telemetry message name and ID assignment dialog class constructor
     * 
     * @param messages
     *            list of telemetry messages
     * 
     * @param schedulerDlg
     *            component over which to center the dialog
     *************************************************************************/
    protected CcddAssignTelemetryMsgIDDialog(List<Message> messages,
                                             CcddTelemetrySchedulerDialog schedulerDlg)
    {
        this.messages = messages;
        this.schedulerDlg = schedulerDlg;

        // Create the message name and ID assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the telemetry message name and ID assignment dialog
     *************************************************************************/
    private void initialize()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        GridBagConstraints.REMAINDER,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create borders for the input fields and assignment panels
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));
        Border etchBorder = BorderFactory.createEtchedBorder();

        // Create panels to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        JPanel msgInputPnl = new JPanel(new GridBagLayout());
        JPanel idInputPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());
        msgInputPnl.setBorder(etchBorder);
        idInputPnl.setBorder(etchBorder);

        // Create the check box to enable message name assignment
        assignMsgCbx = new JCheckBox("Assign message names");
        assignMsgCbx.setFont(LABEL_FONT_BOLD);
        msgInputPnl.add(assignMsgCbx, gbc);

        // Create the message name pattern label
        JLabel msgPatternLbl = new JLabel("Name pattern");
        msgPatternLbl.setFont(LABEL_FONT_BOLD);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbc.gridy++;
        msgInputPnl.add(msgPatternLbl, gbc);

        // Create the message name pattern field
        msgPatternFld = new JTextField("Message_%03d", 12);
        msgPatternFld.setFont(LABEL_FONT_PLAIN);
        msgPatternFld.setEditable(true);
        msgPatternFld.setForeground(Color.BLACK);
        msgPatternFld.setBackground(Color.WHITE);
        msgPatternFld.setBorder(border);
        msgPatternFld.setToolTipText("<html>Format: <i>alphanumeric</i>#<i>&lt;alphanumeric&gt;");
        gbc.weightx = 1.0;
        gbc.gridx++;
        msgInputPnl.add(msgPatternFld, gbc);

        // Create the starting message number label
        JLabel startMsgNumLbl = new JLabel("Starting number");
        startMsgNumLbl.setFont(LABEL_FONT_BOLD);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy++;
        msgInputPnl.add(startMsgNumLbl, gbc);

        // Create the starting message number field
        startMsgNumFld = new JTextField("1", 7);
        startMsgNumFld.setFont(LABEL_FONT_PLAIN);
        startMsgNumFld.setEditable(true);
        startMsgNumFld.setForeground(Color.BLACK);
        startMsgNumFld.setBackground(Color.WHITE);
        startMsgNumFld.setBorder(border);
        gbc.weightx = 1.0;
        gbc.gridx++;
        msgInputPnl.add(startMsgNumFld, gbc);

        // Create the message number interval label
        JLabel msgIntervalLbl = new JLabel("Message interval");
        msgIntervalLbl.setFont(LABEL_FONT_BOLD);
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy++;
        msgInputPnl.add(msgIntervalLbl, gbc);

        // Create the message number interval field
        msgIntervalFld = new JTextField("1", 5);
        msgIntervalFld.setFont(LABEL_FONT_PLAIN);
        msgIntervalFld.setEditable(true);
        msgIntervalFld.setForeground(Color.BLACK);
        msgIntervalFld.setBackground(Color.WHITE);
        msgIntervalFld.setBorder(border);
        gbc.weightx = 1.0;
        gbc.gridx++;
        msgInputPnl.add(msgIntervalFld, gbc);

        // Create the check box to enable ID assignment
        assignIDCbx = new JCheckBox("Assign message IDs");
        assignIDCbx.setFont(LABEL_FONT_BOLD);
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.left = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        gbc.gridy = 0;
        idInputPnl.add(assignIDCbx, gbc);

        // Create the starting message ID label
        JLabel startIDLbl = new JLabel("Starting ID");
        startIDLbl.setFont(LABEL_FONT_BOLD);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        gbc.gridy++;
        idInputPnl.add(startIDLbl, gbc);

        // Create the starting message ID field
        startMsgIDFld = new JTextField("0x0", 7);
        startMsgIDFld.setFont(LABEL_FONT_PLAIN);
        startMsgIDFld.setEditable(true);
        startMsgIDFld.setForeground(Color.BLACK);
        startMsgIDFld.setBackground(Color.WHITE);
        startMsgIDFld.setBorder(border);
        startMsgIDFld.setToolTipText("<html>Format: <i>&lt;</i>0x<i>&gt;hexadecimal digits");
        gbc.weightx = 1.0;
        gbc.gridx++;
        idInputPnl.add(startMsgIDFld, gbc);

        // Create the message ID interval label
        JLabel idIntervalLbl = new JLabel("ID interval");
        idIntervalLbl.setFont(LABEL_FONT_BOLD);
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy++;
        idInputPnl.add(idIntervalLbl, gbc);

        // Create the message ID interval field
        msgIDIntervalFld = new JTextField("1", 5);
        msgIDIntervalFld.setFont(LABEL_FONT_PLAIN);
        msgIDIntervalFld.setEditable(true);
        msgIDIntervalFld.setForeground(Color.BLACK);
        msgIDIntervalFld.setBackground(Color.WHITE);
        msgIDIntervalFld.setBorder(border);
        gbc.weightx = 1.0;
        gbc.gridx++;
        idInputPnl.add(msgIDIntervalFld, gbc);

        // Create the overwrite existing IDs check box
        overwriteIDCbx = new JCheckBox("Overwrite existing IDs");
        overwriteIDCbx.setFont(LABEL_FONT_BOLD);
        overwriteIDCbx.setBorder(BorderFactory.createEmptyBorder());
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        gbc.gridy++;
        idInputPnl.add(overwriteIDCbx, gbc);

        // Add the input panels to the dialog panel
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialogPnl.add(msgInputPnl, gbc);
        gbc.insets.bottom = 0;
        gbc.gridy++;
        dialogPnl.add(idInputPnl, gbc);

        // Get the user's input
        if (showOptionsDialog(schedulerDlg,
                              dialogPnl,
                              "Assign Telemetry Messages",
                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Check if the message names should be assigned
            if (assignMsgCbx.isSelected())
            {
                // Update the telemetry message names
                updateMessageNames();

                // Update the options list with the new message names
                schedulerDlg.getSchedulerHandler().getTelemetryOptions();
            }

            // Check if the message IDs should be assigned
            if (assignIDCbx.isSelected())
            {
                // Update the telemetry message IDs
                updateMessageIDs();
            }

            // Update the scheduler table
            schedulerDlg.getSchedulerHandler().getSchedulerEditor().updateSchedulerTable(true);
        }
    }

    /**************************************************************************
     * Update the telemetry message names
     *************************************************************************/
    private void updateMessageNames()
    {
        // Get the message starting number and interval values
        int startNum = Integer.valueOf(startMsgNumFld.getText());
        int interval = Integer.valueOf(msgIntervalFld.getText());

        // Step through each message
        for (Message message : messages)
        {
            // Use the pattern and current message number to create the message
            // name
            String msgName = String.format(msgPatternFld.getText(), startNum);

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
     * Update the telemetry message IDs
     *************************************************************************/
    private void updateMessageIDs()
    {
        // Get the starting message ID and ID interval values
        int startID = Integer.decode(startMsgIDFld.getText());
        int interval = Integer.valueOf(msgIDIntervalFld.getText());

        // Create a list to contain the existing message ID values
        List<String> usedIDs = new ArrayList<String>();

        // Check if the overwrite check box is not selected
        if (!overwriteIDCbx.isSelected())
        {
            // Step through each message
            for (Message message : messages)
            {
                // Check if the message has an ID
                if (!message.getID().isEmpty())
                {
                    // Add the message ID to the list of existing ID values
                    usedIDs.add(message.getID());
                }

                // Step through each of the message's sub-messages
                for (Message subMessage : message.getSubMessages())
                {
                    // Check if the sub-message has an ID
                    if (!subMessage.getID().isEmpty())
                    {
                        // Add the sub-message ID to the list of existing ID
                        // values
                        usedIDs.add(subMessage.getID());
                    }
                }
            }
        }

        // Step through each message
        for (Message message : messages)
        {
            // Set the message's ID to the next one in the sequence
            startID = setMessageID(message, startID, interval, usedIDs);

            // Step through each of the message's sub-messages. The default
            // sub-message is skipped since its ID gets set when the parent
            // message's ID is set
            for (int index = 1; index < message.getSubMessages().size(); index++)
            {
                // Set the sub-message's ID to the next one in the sequence
                // (for the default sub-message use the parent message's ID
                startID = setMessageID(message.getSubMessage(index),
                                       startID,
                                       interval,
                                       usedIDs);
            }
        }
    }

    /**************************************************************************
     * Set the message ID. If the message already has an ID only update it if
     * the overwrite check box is selected
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
     * @param usedIDs
     *            list of IDs already in use
     * 
     * @return Next ID value in the sequence
     *************************************************************************/
    private int setMessageID(Message message,
                             int idValue,
                             int interval,
                             List<String> usedIDs)
    {
        // Check if the message has no ID, or if it does, that the overwrite ID
        // check box is selected
        if (message.getID().isEmpty() || overwriteIDCbx.isSelected())
        {
            String idString;

            do
            {
                // Format the message ID value
                idString = String.format("0x%04x", idValue);

                // Adjust the message ID value by the interval amount
                idValue += interval;

            } while (usedIDs.contains(idString));
            // Continue to loop as long as the ID value matches an existing
            // one. This prevents assigning a duplicate ID

            // Set the message ID
            message.setID(idString);
        }

        return idValue;
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
            // Check if the message names and IDs are not selected to be
            // assigned
            if (!assignMsgCbx.isSelected() && !assignIDCbx.isSelected())
            {
                // Inform the user that an assignment must be selected
                throw new CCDDException("Message name or ID assignment must be selected");
            }

            // Check if the message names are to be assigned
            if (assignMsgCbx.isSelected())
            {
                // Remove any excess white space
                msgPatternFld.setText(msgPatternFld.getText().trim());

                // Get the name pattern
                String pattern = msgPatternFld.getText();

                // Check if the message name pattern isn't in the format
                // text%<0#>d<text> where # is one or more digits
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

                // Check if the starting message number is not a non-negative
                // integer
                if (!startMsgNumFld.getText().matches(InputDataType.INT_NON_NEGATIVE.getInputMatch()))
                {
                    // Inform the user that the starting number is invalid
                    throw new CCDDException("Message starting number must be an integer >= 0");
                }

                // Check if the message name interval value is not a positive
                // integer
                if (!msgIntervalFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                {
                    // Inform the user that the interval is invalid
                    throw new CCDDException("Message interval must be a positive integer");
                }
            }

            // Check if the message IDs are to be assigned
            if (assignIDCbx.isSelected())
            {
                // Remove any excess white space
                startMsgIDFld.setText(startMsgIDFld.getText().trim());

                // Check if the starting message ID value is not in hexadecimal
                // format
                if (!startMsgIDFld.getText().matches(InputDataType.HEXADECIMAL.getInputMatch()))
                {
                    // Inform the user that the starting ID is invalid
                    throw new CCDDException("Starting ID must be in the format<br>&#160;&#160;<i>&lt;</i>"
                                            + "0x<i>&gt;</i>#<br>where # is one or more hexadecimal digits");
                }

                // Check if the message ID interval value is not a positive
                // integer
                if (!msgIDIntervalFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                {
                    // Inform the user that the interval is invalid
                    throw new CCDDException("ID interval must be a positive integer");
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddAssignTelemetryMsgIDDialog.this,
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
