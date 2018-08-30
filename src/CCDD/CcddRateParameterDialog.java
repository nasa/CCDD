/**
 * CFS Command and Data Dictionary rate parameter assignment dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddClassesComponent.DnDTabbedPane;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary rate parameter assignment dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddRateParameterDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddRateParameterHandler rateHandler;

    // Components that need to be accessed by multiple methods
    private JTextField maxSecPerMsgFld;
    private JTextField maxMsgsPerSecFld;
    private JTextField[] maxMsgsPerCycleFld;
    private JTextField[] maxBytesPerSecFld;
    private JTextField[] streamNameFld;
    private JTextArea[] availRatesFld;
    private JCheckBox unevenCb;
    private DnDTabbedPane tabbedPane;
    private Border border;
    private Border emptyBorder;

    /**********************************************************************************************
     * Input field verification result class
     *********************************************************************************************/
    private class InputVerificationResult
    {
        String lastValid;
        boolean isValid;

        /******************************************************************************************
         * Input field verification result class constructor
         *
         * @param lastValid
         *            last valid field value
         *
         * @param isValid
         *            true if the input value is valid
         *****************************************************************************************/
        InputVerificationResult(String lastValid, boolean isValid)
        {
            this.lastValid = lastValid;
            this.isValid = isValid;
        }

        /******************************************************************************************
         * Get the last valid input value
         *
         * @return Last valid field value
         *****************************************************************************************/
        protected String getLastValid()
        {
            return lastValid;
        }

        /******************************************************************************************
         * Set the last valid input value
         *
         * @param lastValid
         *            last valid field value
         *****************************************************************************************/
        protected void setLastValid(String lastValid)
        {
            this.lastValid = lastValid;
        }

        /******************************************************************************************
         * Get the value validity status
         *
         * @return true if the input value is valid
         *****************************************************************************************/
        protected boolean isValid()
        {
            return isValid;
        }

        /******************************************************************************************
         * Set the value validity status
         *
         * @param isValid
         *            true if the input value is valid
         *****************************************************************************************/
        protected void setValid(boolean isValid)
        {
            this.isValid = isValid;
        }
    }

    /**********************************************************************************************
     * Rate parameter assignment dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddRateParameterDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        rateHandler = ccddMain.getRateParameterHandler();

        // Create the rate parameter assignment dialog
        initialize();
    }

    /**********************************************************************************************
     * Verify the contents of a rate input field
     *
     * @param field
     *            reference to the input field
     *
     * @param lastValid
     *            last valid field value
     *
     * @return input verification results containing the last valid value and the valid status
     *********************************************************************************************/
    private InputVerificationResult verifyInputField(JTextField field, String lastValid)
    {
        InputVerificationResult verifyResult = new InputVerificationResult(lastValid, true);

        // Remove any leading and trailing white space characters
        field.setText(field.getText().trim());

        // Check if the any rate parameter is not a positive integer value
        if (!field.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
        {
            // Inform the user that a rate is invalid
            new CcddDialogHandler().showMessageDialog(CcddRateParameterDialog.this,
                                                      "<html><b>Rate parameter values must be positive integers",
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Restore the previous value in the field
            field.setText(verifyResult.getLastValid());

            // Set the flag to indicate the dialog input is invalid
            verifyResult.setValid(false);

            // Toggle the controls enable status so that the buttons are redrawn correctly
            CcddRateParameterDialog.this.setControlsEnabled(false);
            CcddRateParameterDialog.this.setControlsEnabled(true);
        }
        // The input is valid
        else
        {
            // Format the rate parameter field
            field.setText(Integer.valueOf(field.getText()).toString());

            // Display the available rates for the currently selected rate column
            updateAvailableRates();

            // Store the new value as the last valid value
            verifyResult.setLastValid(field.getText());
        }

        return verifyResult;
    }

    /**********************************************************************************************
     * Create the rate parameter assignment dialog
     *********************************************************************************************/
    private void initialize()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.HORIZONTAL,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create borders for the input fields
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
        emptyBorder = BorderFactory.createEmptyBorder();

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(emptyBorder);

        // Create the maximum seconds per message label
        JLabel maxSecPerMsgLbl = new JLabel("Maximum seconds per message");
        maxSecPerMsgLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        dialogPnl.add(maxSecPerMsgLbl, gbc);

        // Create the maximum seconds per message input field
        maxSecPerMsgFld = new JTextField(String.valueOf(rateHandler.getMaxSecondsPerMsg()), 7);
        maxSecPerMsgFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxSecPerMsgFld.setEditable(true);
        maxSecPerMsgFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxSecPerMsgFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxSecPerMsgFld.setBorder(border);

        // Set the field's input verifier
        maxSecPerMsgFld.setInputVerifier(new InputVerifier()
        {
            // Storage for the last valid value entered; used to restore the input field value if
            // an invalid value is entered. Initialize to the value at the time the field is
            // created
            String lastValid = maxSecPerMsgFld.getText();

            /**************************************************************************************
             * Verify the contents of the input field
             *************************************************************************************/
            @Override
            public boolean verify(JComponent input)
            {
                // Verify the field contents
                InputVerificationResult doneIt = verifyInputField((JTextField) input, lastValid);

                // Update the last valid value
                lastValid = doneIt.getLastValid();

                return doneIt.isValid();
            }
        });

        gbc.gridx++;
        dialogPnl.add(maxSecPerMsgFld, gbc);

        // Create the maximum messages per second label
        JLabel maxMsgsPerSecLbl = new JLabel("Maximum messages per second");
        maxMsgsPerSecLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(maxMsgsPerSecLbl, gbc);

        // Create the maximum messages per second input field
        maxMsgsPerSecFld = new JTextField(String.valueOf(rateHandler.getMaxMsgsPerSecond()), 7);
        maxMsgsPerSecFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        maxMsgsPerSecFld.setEditable(true);
        maxMsgsPerSecFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        maxMsgsPerSecFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        maxMsgsPerSecFld.setBorder(border);

        // Set the field's input verifier
        maxMsgsPerSecFld.setInputVerifier(new InputVerifier()
        {
            // Storage for the last valid value entered; used to restore the input field value if
            // an invalid value is entered. Initialize to the value at the time the field is
            // created
            String lastValid = maxMsgsPerSecFld.getText();

            /**************************************************************************************
             * Verify the contents of the input field
             *************************************************************************************/
            @Override
            public boolean verify(JComponent input)
            {
                // Verify the field contents
                InputVerificationResult doneIt = verifyInputField((JTextField) input, lastValid);

                // Update the last valid value
                lastValid = doneIt.getLastValid();

                return doneIt.isValid();
            }
        });

        gbc.gridx++;
        dialogPnl.add(maxMsgsPerSecFld, gbc);

        // Get the rate information for all data streams
        List<RateInformation> rateInformation = rateHandler.getRateInformation();

        // Create text fields for the stream-specific rate parameters
        streamNameFld = new JTextField[rateInformation.size()];
        maxMsgsPerCycleFld = new JTextField[rateInformation.size()];
        maxBytesPerSecFld = new JTextField[rateInformation.size()];
        availRatesFld = new JTextArea[rateInformation.size()];

        // Create a tabbed pane to contain the rate parameters that are stream-specific
        tabbedPane = new DnDTabbedPane(SwingConstants.TOP)
        {
            /**************************************************************************************
             * Update the rate arrays order following a tab move
             *************************************************************************************/
            @Override
            protected Object tabMoveCleanup(int oldTabIndex, int newTabIndex, Object tabContents)
            {
                // Adjust the new tab index if moving the tab to a higher index
                newTabIndex -= newTabIndex > oldTabIndex
                                                         ? 1
                                                         : 0;

                // Re-order the rate information based on the new tab order
                RateInformation[] rateInfoArray = rateHandler.getRateInformation().toArray(new RateInformation[0]);
                rateInfoArray = (RateInformation[]) CcddUtilities.moveArrayMember(rateInfoArray,
                                                                                  oldTabIndex,
                                                                                  newTabIndex);
                List<RateInformation> rateInfoList = new ArrayList<RateInformation>(rateInfoArray.length);
                rateInfoList.addAll(Arrays.asList(rateInfoArray));
                rateHandler.setRateInformation(rateInfoList);

                // Re-order the fields based on the new tab order
                maxMsgsPerCycleFld = (JTextField[]) CcddUtilities.moveArrayMember(maxMsgsPerCycleFld,
                                                                                  oldTabIndex,
                                                                                  newTabIndex);
                maxBytesPerSecFld = (JTextField[]) CcddUtilities.moveArrayMember(maxBytesPerSecFld,
                                                                                 oldTabIndex,
                                                                                 newTabIndex);
                streamNameFld = (JTextField[]) CcddUtilities.moveArrayMember(streamNameFld,
                                                                             oldTabIndex,
                                                                             newTabIndex);
                availRatesFld = (JTextArea[]) CcddUtilities.moveArrayMember(availRatesFld,
                                                                            oldTabIndex,
                                                                            newTabIndex);

                return null;
            }
        };

        tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Create a tab for each stream
        addStreamTabs(rateInformation);

        gbc.insets.left = 0;
        gbc.insets.right = 0;
        gbc.insets.bottom = 0;
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(tabbedPane, gbc);

        // Create a panel for the uneven check box
        JPanel unevenPnl = new JPanel(new FlowLayout(FlowLayout.LEFT,
                                                     ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                     0));
        unevenPnl.setBorder(emptyBorder);

        // Create a check box for including/excluding unevenly time-spaced sample rates
        unevenCb = new JCheckBox("Include unevenly time-spaced rates");
        unevenCb.setBorder(emptyBorder);
        unevenCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        unevenCb.setSelected(rateHandler.isIncludeUneven()
                                                           ? true
                                                           : false);
        unevenCb.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change in the include uneven rates check box status
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                updateAvailableRates();
            }
        });

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.gridwidth = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        unevenPnl.add(unevenCb);
        dialogPnl.add(unevenPnl, gbc);

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener()
        {
            /**************************************************************************************
             * Update the available rates using the values in the selected tab
             *************************************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                // Display the available rates for the currently selected rate column
                updateAvailableRates();
            }
        });

        // Display the available rates for the initially selected rate column
        updateAvailableRates();

        // Get the user's input
        if (showOptionsDialog(ccddMain.getMainFrame(),
                              dialogPnl,
                              "Rate Parameters",
                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Convert the common rate parameters to integers
            int maxSecPerMsg = Integer.valueOf(maxSecPerMsgFld.getText());
            int maxMsgsPerSec = Integer.valueOf(maxMsgsPerSecFld.getText());

            // Create storage for the stream-specific parameters
            String[] streamNames = new String[tabbedPane.getTabCount()];
            int[] maxMsgsPerCycle = new int[tabbedPane.getTabCount()];
            int[] maxBytesPerSec = new int[tabbedPane.getTabCount()];

            // Step through each stream
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Store the stream name, convert the rate parameters to numeric form, and
                // calculate the available rates
                streamNames[index] = streamNameFld[index].getText();
                maxMsgsPerCycle[index] = Integer.valueOf(maxMsgsPerCycleFld[index].getText());
                maxBytesPerSec[index] = Integer.valueOf(maxBytesPerSecFld[index].getText());
            }

            // Check if any rate parameter changed
            if (isRateChanges(maxSecPerMsg,
                              maxMsgsPerSec,
                              streamNames,
                              maxMsgsPerCycle,
                              maxBytesPerSec,
                              unevenCb.isSelected()))
            {
                // Store the rate parameters and update the sample rates
                rateHandler.setRateParameters(maxSecPerMsg,
                                              maxMsgsPerSec,
                                              streamNames,
                                              maxMsgsPerCycle,
                                              maxBytesPerSec,
                                              unevenCb.isSelected(),
                                              CcddRateParameterDialog.this);
            }
        }
    }

    /**********************************************************************************************
     * Calculate and display the available rates for the currently selected rate column
     *********************************************************************************************/
    private void updateAvailableRates()
    {
        // Get the rate column to calculate and display its valid rates
        int index = tabbedPane.getSelectedIndex();
        availRatesFld[index].setText(Arrays.toString(rateHandler.calculateSampleRates(Integer.valueOf(maxSecPerMsgFld.getText()),
                                                                                      Integer.valueOf(maxMsgsPerSecFld.getText()),
                                                                                      Integer.valueOf(maxMsgsPerCycleFld[index].getText()),
                                                                                      unevenCb.isSelected()))
                                           .replaceAll("\\[|\\]", ""));
        availRatesFld[index].setCaretPosition(0);
    }

    /**********************************************************************************************
     * Create the tabs for the stream specific input fields
     *
     * @param rateInfo
     *            list containing the rate information
     *********************************************************************************************/
    private void addStreamTabs(List<RateInformation> rateInfo)
    {
        // Step through each stream
        for (int index = 0; index < rateInfo.size(); index++)
        {
            // Set the initial layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0,
                                                            0,
                                                            1,
                                                            1,
                                                            0.0,
                                                            0.0,
                                                            GridBagConstraints.LINE_START,
                                                            GridBagConstraints.HORIZONTAL,
                                                            new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                       ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                            0,
                                                            0);

            // Create a panel for the rate calculation button and results
            JPanel availRatesPnl = new JPanel(new GridBagLayout());
            availRatesPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            JLabel availRatesLbl = new JLabel("Available rates");
            availRatesLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            availRatesLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
            availRatesPnl.add(availRatesLbl, gbc);

            // Create the available rates field and add it to the rates panel
            availRatesFld[index] = new JTextArea("", 3, 1);
            availRatesFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            availRatesFld[index].setEditable(false);
            availRatesFld[index].setWrapStyleWord(true);
            availRatesFld[index].setLineWrap(true);
            availRatesFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            availRatesFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            availRatesFld[index].setBorder(emptyBorder);
            JScrollPane scrollPane = new JScrollPane(availRatesFld[index]);
            scrollPane.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            scrollPane.setBorder(emptyBorder);
            scrollPane.setViewportBorder(border);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.weightx = 1.0;
            gbc.gridx++;
            availRatesPnl.add(scrollPane, gbc);

            // Create a panel to contain the stream's text fields
            JPanel streamPnl = new JPanel(new GridBagLayout());
            JLabel streamNameLbl = new JLabel("Data stream name");
            streamNameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.gridwidth = 1;
            gbc.gridx = 0;
            streamPnl.add(streamNameLbl, gbc);

            // Create the data stream name input field
            streamNameFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getStreamName()), 7);
            streamNameFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            streamNameFld[index].setEditable(true);
            streamNameFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            streamNameFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            streamNameFld[index].setBorder(border);

            // Get the initial field value
            final String initStreamName = streamNameFld[index].getText();

            // Set the field's input verifier
            streamNameFld[index].setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the input field value
                // if an invalid value is entered. Initialize to the value at the time the field is
                // created
                String lastValid = initStreamName;

                /**********************************************************************************
                 * Verify the contents of the input field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    JTextField field = (JTextField) input;

                    // Remove any leading and trailing white space characters
                    field.setText(field.getText().trim());

                    // Assume the dialog input is valid
                    boolean isValid = true;

                    // Check if a matching stream name is found for a rate other than this one
                    if (rateHandler.getRateInformationIndexByStreamName(field.getText()) != tabbedPane.getSelectedIndex())
                    {
                        // Inform the user that a stream name is duplicated
                        new CcddDialogHandler().showMessageDialog(CcddRateParameterDialog.this,
                                                                  "<html><b>Duplicate stream name",
                                                                  "Missing/Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);

                        // Restore the previous value in the field
                        field.setText(lastValid);

                        // Set the flag to indicate the dialog input is invalid
                        isValid = false;

                        // Toggle the controls enable status so that the buttons are redrawn
                        // correctly
                        CcddRateParameterDialog.this.setControlsEnabled(false);
                        CcddRateParameterDialog.this.setControlsEnabled(true);
                    }
                    // The stream name is unique to this rate
                    else
                    {
                        // Update the last valid input
                        lastValid = field.getText();
                    }

                    return isValid;
                }
            });

            gbc.gridx++;
            streamPnl.add(streamNameFld[index], gbc);

            // Create the maximum messages per cycle label
            JLabel maxMsgsPerCycleLbl = new JLabel("Maximum messages per cycle");
            maxMsgsPerCycleLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(maxMsgsPerCycleLbl, gbc);

            // Create the maximum messages per cycle input field
            maxMsgsPerCycleFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getMaxMsgsPerCycle()), 7);
            maxMsgsPerCycleFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            maxMsgsPerCycleFld[index].setEditable(true);
            maxMsgsPerCycleFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            maxMsgsPerCycleFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            maxMsgsPerCycleFld[index].setBorder(border);

            // Get the initial field value
            final String initMaxMsgsPerCycle = maxMsgsPerCycleFld[index].getText();

            // Set the field's input verifier
            maxMsgsPerCycleFld[index].setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the input field value
                // if an invalid value is entered. Initialize to the value at the time the field is
                // created
                String lastValid = initMaxMsgsPerCycle;

                /**********************************************************************************
                 * Verify the contents of the input field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    // Verify the field contents
                    InputVerificationResult doneIt = verifyInputField((JTextField) input, lastValid);

                    // Update the last valid value
                    lastValid = doneIt.getLastValid();

                    return doneIt.isValid();
                }
            });

            gbc.gridx++;
            streamPnl.add(maxMsgsPerCycleFld[index], gbc);

            // Create the maximum bytes per second label
            JLabel bytesPerSecLbl = new JLabel("Maximum bytes per second");
            bytesPerSecLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(bytesPerSecLbl, gbc);

            // Create the maximum bytes per second input field
            maxBytesPerSecFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getMaxBytesPerSec()), 7);
            maxBytesPerSecFld[index].setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            maxBytesPerSecFld[index].setEditable(true);
            maxBytesPerSecFld[index].setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            maxBytesPerSecFld[index].setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            maxBytesPerSecFld[index].setBorder(border);

            // Get the initial field value
            final String initMaxBytesPerSec = maxBytesPerSecFld[index].getText();

            // Set the field's input verifier
            maxBytesPerSecFld[index].setInputVerifier(new InputVerifier()
            {
                // Storage for the last valid value entered; used to restore the input field value
                // if an invalid value is entered. Initialize to the value at the time the field is
                // created
                String lastValid = initMaxBytesPerSec;

                /**********************************************************************************
                 * Verify the contents of the input field
                 *********************************************************************************/
                @Override
                public boolean verify(JComponent input)
                {
                    // Verify the field contents
                    InputVerificationResult doneIt = verifyInputField((JTextField) input, lastValid);

                    // Update the last valid value
                    lastValid = doneIt.getLastValid();

                    return doneIt.isValid();
                }
            });

            gbc.gridx++;
            streamPnl.add(maxBytesPerSecFld[index], gbc);

            // Add the rate calculator panel to the dialog
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(availRatesPnl, gbc);

            // Create a tab for each stream
            tabbedPane.addTab(rateInfo.get(index).getRateName(), null, streamPnl, null);
        }
    }

    /**********************************************************************************************
     * Check if any rate parameter changed
     *
     * @param maxSecPerMsg
     *            maximum number of seconds allowed between downlinking two of the same message
     *
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one second
     *
     * @param streamName
     *            array containing the stream name per stream
     *
     * @param maxMsgsPerCycle
     *            array containing the maximum number of messages that can be downlinked before
     *            repeating the message list per stream
     *
     * @param maxBytesPerSec
     *            array containing the maximum number of bytes that can be downlinked in one second
     *            per stream
     *
     * @param includeUneven
     *            true to include unevenly time-spaced sample rate values; false to only include
     *            sample rates that are evenly time-spaced
     *
     * @return true if any of the rate parameters changed
     *********************************************************************************************/
    private boolean isRateChanges(int maxSecPerMsg,
                                  int maxMsgsPerSec,
                                  String[] streamName,
                                  int[] maxMsgsPerCycle,
                                  int[] maxBytesPerSec,
                                  boolean includeUneven)
    {
        boolean isChanges = false;

        // Check if any of the common rate parameters changed
        if (rateHandler.getMaxSecondsPerMsg() != maxSecPerMsg
            || rateHandler.getMaxMsgsPerSecond() != maxMsgsPerSec
            || rateHandler.isIncludeUneven() != includeUneven)
        {
            // Set the flag indicating a change
            isChanges = true;
        }
        // The common rate parameters didn't change
        else
        {
            int index = 0;

            // Step through each data stream
            for (RateInformation rateInfo : rateHandler.getRateInformation())
            {
                // Check if any of the stream-specific rate parameters changed
                if (!rateInfo.getStreamName().equals(streamName[index])
                    || rateInfo.getMaxMsgsPerCycle() != maxMsgsPerCycle[index]
                    || rateInfo.getMaxBytesPerSec() != maxBytesPerSec[index])
                {
                    // Set the flag indicating a change and stop searching
                    isChanges = true;
                    break;
                }

                index++;
            }
        }

        return isChanges;
    }
}
