/**
 * CFS Command & Data Dictionary application parameter assignment dialog.
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/******************************************************************************
 * CFS Command & Data Dictionary application parameter assignment dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddApplicationParameterDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddApplicationParameterHandler appHandler;

    // Components that need to be accessed by multiple methods
    private JTextField maxSlotsperMessage;
    private JTextField maxCommands;
    private JTextField maxMsgsPerSecFld;
    private JTextField maxMsgsPerCycleFld;

    /**************************************************************************
     * Application parameter assignment dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddApplicationParameterDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Get a reference to the application handler to shorten later calls
        appHandler = ccddMain.getApplicationParameterHandler();

        // Create the application parameter assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the application parameter assignment dialog
     *************************************************************************/
    private void initialize()
    {
        // Get the application parameters
        appHandler.generateApplicationParameters();

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

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the maximum seconds per message label
        JLabel maxSecPerMsgLbl = new JLabel("Maximum slots per message");
        maxSecPerMsgLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        dialogPnl.add(maxSecPerMsgLbl, gbc);

        // Create the maximum seconds per message input field
        maxSlotsperMessage = new JTextField(String.valueOf(appHandler.getNumberOfSlots()), 5);
        maxSlotsperMessage.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxSlotsperMessage.setEditable(true);
        maxSlotsperMessage.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxSlotsperMessage.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxSlotsperMessage.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(maxSlotsperMessage, gbc);

        // Create the maximum seconds per message label
        JLabel maxCommandAmount = new JLabel("Maximum number of commands");
        maxCommandAmount.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(maxCommandAmount, gbc);

        // Create the maximum seconds per message input field
        maxCommands = new JTextField(String.valueOf(appHandler.getCommandsPerTable()), 5);
        maxCommands.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxCommands.setEditable(true);
        maxCommands.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxCommands.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxCommands.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(maxCommands, gbc);

        // Create the maximum messages per second label
        JLabel maxMsgsPerSecLbl = new JLabel("Maximum messages per second");
        maxMsgsPerSecLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(maxMsgsPerSecLbl, gbc);

        // Create the maximum messages per second input field
        maxMsgsPerSecFld = new JTextField(String.valueOf(appHandler.getMaxMsgsPerSecond()), 5);
        maxMsgsPerSecFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxMsgsPerSecFld.setEditable(true);
        maxMsgsPerSecFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxMsgsPerSecFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxMsgsPerSecFld.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(maxMsgsPerSecFld, gbc);

        // Create the maximum messages per cycle label
        JLabel maxMsgsPerCycleLbl = new JLabel("Maximum messages per cycle");
        maxMsgsPerCycleLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.insets.bottom = 0;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(maxMsgsPerCycleLbl, gbc);

        // Create the maximum messages per cycle input field
        maxMsgsPerCycleFld = new JTextField(String.valueOf(appHandler.getMsgsPerCycle()), 5);
        maxMsgsPerCycleFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxMsgsPerCycleFld.setEditable(true);
        maxMsgsPerCycleFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxMsgsPerCycleFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxMsgsPerCycleFld.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(maxMsgsPerCycleFld, gbc);

        // Get the user's input
        if (showOptionsDialog(ccddMain.getMainFrame(),
                              dialogPnl,
                              "Application Parameters",
                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            appHandler.setApplicationParameters(Integer.valueOf(maxMsgsPerSecFld.getText()),
                                                Integer.valueOf(maxMsgsPerCycleFld.getText()),
                                                Integer.valueOf(maxSlotsperMessage.getText()),
                                                Integer.valueOf(maxCommands.getText()),
                                                CcddApplicationParameterDialog.this);
        }
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
            // Remove any excess white space
            maxSlotsperMessage.setText(maxSlotsperMessage.getText().trim());
            maxMsgsPerSecFld.setText(maxMsgsPerSecFld.getText().trim());
            maxMsgsPerCycleFld.setText(maxMsgsPerCycleFld.getText().trim());
            maxCommands.setText(maxCommands.getText().trim());

            // Check if any rate parameter is blank
            if (maxSlotsperMessage.getText().isEmpty()
                || maxMsgsPerSecFld.getText().isEmpty()
                || maxMsgsPerCycleFld.getText().isEmpty()
                || maxCommands.getText().isEmpty())
            {
                // Inform the user that a rate parameter is missing
                throw new CCDDException("All application parameters must be entered");
            }

            // Check if the any rate parameter is not a positive integer value
            if (!maxSlotsperMessage.getText().matches(InputDataType.INT_POSITIVE.getInputMatch())
                || !maxMsgsPerSecFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch())
                || !maxMsgsPerCycleFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch())
                || !maxCommands.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
            {
                // Inform the user that a rate is invalid
                throw new CCDDException("Application parameter values must be positive integer values");
            }

            // Format the rate parameter fields
            maxSlotsperMessage.setText(Integer.valueOf(maxSlotsperMessage.getText()).toString());
            maxMsgsPerSecFld.setText(Integer.valueOf(maxMsgsPerSecFld.getText()).toString());
            maxMsgsPerCycleFld.setText(Integer.valueOf(maxMsgsPerCycleFld.getText()).toString());
            maxCommands.setText(Integer.valueOf(maxCommands.getText()).toString());
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddApplicationParameterDialog.this,
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
