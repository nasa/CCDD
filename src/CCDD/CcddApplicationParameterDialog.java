/**
 * CFS Command and Data Dictionary application parameter assignment dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
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

import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary application parameter assignment dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddApplicationParameterDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddApplicationParameterHandler appHandler;

    // Components that need to be accessed by multiple methods
    private JTextField maxMsgsPerTimeSlotFld;
    private JTextField numTimeSlotsFld;
    private JTextField maxMsgsPerSecFld;
    private JTextField maxMsgsPerCycleFld;

    /**********************************************************************************************
     * Application parameter assignment dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddApplicationParameterDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Get a reference to the application handler to shorten later calls
        appHandler = ccddMain.getApplicationParameterHandler();

        // Create the application parameter assignment dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the application parameter assignment dialog
     *********************************************************************************************/
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
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the maximum number of messages per time slot label
        JLabel maxMsgsPerTimeSlotLbl = new JLabel("Maximum messages per time slot");
        maxMsgsPerTimeSlotLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        dialogPnl.add(maxMsgsPerTimeSlotLbl, gbc);

        // Create the maximum number of messages per time slot input field
        maxMsgsPerTimeSlotFld = new JTextField(String.valueOf(appHandler.getNumberOfMessagesPerTimeSlot()), 5);
        maxMsgsPerTimeSlotFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxMsgsPerTimeSlotFld.setEditable(true);
        maxMsgsPerTimeSlotFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxMsgsPerTimeSlotFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxMsgsPerTimeSlotFld.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(maxMsgsPerTimeSlotFld, gbc);

        // Create the number of time slots label
        JLabel numTimeSlotsLbl = new JLabel("Total number of time slots");
        numTimeSlotsLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(numTimeSlotsLbl, gbc);

        // Create the number of time slots input field
        numTimeSlotsFld = new JTextField(String.valueOf(appHandler.getNumberOfTimeSlots()), 5);
        numTimeSlotsFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        numTimeSlotsFld.setEditable(true);
        numTimeSlotsFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        numTimeSlotsFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        numTimeSlotsFld.setBorder(border);
        gbc.gridx++;
        dialogPnl.add(numTimeSlotsFld, gbc);

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
                                                Integer.valueOf(maxMsgsPerTimeSlotFld.getText()),
                                                Integer.valueOf(numTimeSlotsFld.getText()),
                                                CcddApplicationParameterDialog.this);
        }
    }

    /**********************************************************************************************
     * Verify that the dialog content is valid
     *
     * @return true if the input values are valid
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            // Remove any excess white space
            maxMsgsPerTimeSlotFld.setText(maxMsgsPerTimeSlotFld.getText().trim());
            maxMsgsPerSecFld.setText(maxMsgsPerSecFld.getText().trim());
            maxMsgsPerCycleFld.setText(maxMsgsPerCycleFld.getText().trim());
            numTimeSlotsFld.setText(numTimeSlotsFld.getText().trim());

            // Check if any parameter is blank
            if (maxMsgsPerTimeSlotFld.getText().isEmpty()
                || maxMsgsPerSecFld.getText().isEmpty()
                || maxMsgsPerCycleFld.getText().isEmpty()
                || numTimeSlotsFld.getText().isEmpty())
            {
                // Inform the user that a parameter is missing
                throw new Exception("All application parameters must be entered");
            }

            // Check if the any parameter is not a positive integer value
            if (!maxMsgsPerTimeSlotFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch())
                || !maxMsgsPerSecFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch())
                || !maxMsgsPerCycleFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch())
                || !numTimeSlotsFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
            {
                // Inform the user that a parameter is invalid
                throw new Exception("Application parameter values must be positive integer values");
            }

            // Format the application parameter fields
            maxMsgsPerTimeSlotFld.setText(Integer.valueOf(maxMsgsPerTimeSlotFld.getText()).toString());
            maxMsgsPerSecFld.setText(Integer.valueOf(maxMsgsPerSecFld.getText()).toString());
            maxMsgsPerCycleFld.setText(Integer.valueOf(maxMsgsPerCycleFld.getText()).toString());
            numTimeSlotsFld.setText(Integer.valueOf(numTimeSlotsFld.getText()).toString());
        }
        catch (Exception e)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddApplicationParameterDialog.this,
                                                      "<html><b>" + e.getMessage(),
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
