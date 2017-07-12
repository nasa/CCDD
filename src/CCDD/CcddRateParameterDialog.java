/**
 * CFS Command & Data Dictionary rate parameter assignment dialog. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;

/******************************************************************************
 * CFS Command & Data Dictionary rate parameter assignment dialog class
 *****************************************************************************/
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
    private JTextField[] availRatesFld;
    private JCheckBox unevenCb;
    private JTabbedPane tabbedPane;
    private Border border;

    // Reference to the rate parameter input verifier
    private InputVerifier verifyInputs;

    /**************************************************************************
     * Rate parameter assignment dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddRateParameterDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        rateHandler = ccddMain.getRateParameterHandler();

        // Create the rate parameter assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the rate parameter assignment dialog
     *************************************************************************/
    private void initialize()
    {
        // Create an input verifier so that the rate parameters can be verified
        // and the available rates calculated
        verifyInputs = new InputVerifier()
        {
            /******************************************************************
             * Verify the contents of a the rate parameters and update the
             * available rates
             *****************************************************************/
            @Override
            public boolean verify(JComponent input)
            {
                boolean isValid = verifySelection();

                // Check if the rate parameters are valid
                if (isValid)
                {
                    // Display the available rates for the currently selected
                    // rate column
                    updateAvailableRates();
                }

                return isValid;
            }
        };

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.HORIZONTAL,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the maximum seconds per message label
        JLabel maxSecPerMsgLbl = new JLabel("Maximum seconds per message");
        maxSecPerMsgLbl.setFont(LABEL_FONT_BOLD);
        dialogPnl.add(maxSecPerMsgLbl, gbc);

        // Create the maximum seconds per message input field
        maxSecPerMsgFld = new JTextField(String.valueOf(rateHandler.getMaxSecondsPerMsg()), 7);
        maxSecPerMsgFld.setFont(LABEL_FONT_PLAIN);
        maxSecPerMsgFld.setEditable(true);
        maxSecPerMsgFld.setForeground(Color.BLACK);
        maxSecPerMsgFld.setBackground(Color.WHITE);
        maxSecPerMsgFld.setBorder(border);
        maxSecPerMsgFld.setInputVerifier(verifyInputs);
        gbc.gridx++;
        dialogPnl.add(maxSecPerMsgFld, gbc);

        // Create the maximum messages per second label
        JLabel maxMsgsPerSecLbl = new JLabel("Maximum messages per second");
        maxMsgsPerSecLbl.setFont(LABEL_FONT_BOLD);
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(maxMsgsPerSecLbl, gbc);

        // Create the maximum messages per second input field
        maxMsgsPerSecFld = new JTextField(String.valueOf(rateHandler.getMaxMsgsPerSecond()), 7);
        maxMsgsPerSecFld.setFont(LABEL_FONT_PLAIN);
        maxMsgsPerSecFld.setEditable(true);
        maxMsgsPerSecFld.setForeground(Color.BLACK);
        maxMsgsPerSecFld.setBackground(Color.WHITE);
        maxMsgsPerSecFld.setBorder(border);
        maxMsgsPerSecFld.setInputVerifier(verifyInputs);
        gbc.gridx++;
        dialogPnl.add(maxMsgsPerSecFld, gbc);

        // Get the rate information for all data streams
        List<RateInformation> rateInformation = rateHandler.getRateInformation();

        // Create text fields for the stream-specific rate parameters
        streamNameFld = new JTextField[rateInformation.size()];
        maxMsgsPerCycleFld = new JTextField[rateInformation.size()];
        maxBytesPerSecFld = new JTextField[rateInformation.size()];
        availRatesFld = new JTextField[rateInformation.size()];

        // Create a tabbed pane to contain the rate parameters that are
        // stream-specific
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(LABEL_FONT_BOLD);

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
                                                     LABEL_HORIZONTAL_SPACING / 2,
                                                     0));
        unevenPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create a check box for including/excluding unevenly time-spaced
        // sample rates
        unevenCb = new JCheckBox("Include unevenly time-spaced rates");
        unevenCb.setBorder(BorderFactory.createEmptyBorder());
        unevenCb.setFont(LABEL_FONT_BOLD);
        unevenCb.setSelected(rateHandler.isIncludeUneven()
                                                          ? true
                                                          : false);
        unevenCb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Handle a change in the include uneven rates check box status
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                updateAvailableRates();
            }
        });

        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.gridwidth = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        unevenPnl.add(unevenCb);
        dialogPnl.add(unevenPnl, gbc);

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener()
        {
            /******************************************************************
             * Update the available rates using the values in the selected tab
             *****************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                // Display the available rates for the currently selected rate
                // column
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
                // Store the stream name, convert the rate parameters to
                // numeric form, and calculate the available rates
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

    /**************************************************************************
     * Calculate and display the available rates for the currently selected
     * rate column
     *************************************************************************/
    //
    private void updateAvailableRates()
    {
        // Get the rate column to calculate and display its valid rates
        int index = tabbedPane.getSelectedIndex();
        availRatesFld[index].setText(Arrays.toString(rateHandler.calculateSampleRates(Integer.valueOf(maxSecPerMsgFld.getText()),
                                                                                      Integer.valueOf(maxMsgsPerSecFld.getText()),
                                                                                      Integer.valueOf(maxMsgsPerCycleFld[index].getText()),
                                                                                      unevenCb.isSelected())).replaceAll("\\[|\\]", ""));
        availRatesFld[index].setCaretPosition(0);
    }

    /**************************************************************************
     * Create the tabs for the stream specific input fields
     * 
     * @param rateInfo
     *            list containing the rate information
     *************************************************************************/
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
                                                            new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                       LABEL_HORIZONTAL_SPACING / 2,
                                                                       LABEL_VERTICAL_SPACING / 2,
                                                                       LABEL_HORIZONTAL_SPACING / 2),
                                                            0,
                                                            0);

            // Create a panel for the rate calculation button and results
            JPanel availRatesPnl = new JPanel(new GridBagLayout());
            availRatesPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            JLabel availRatesLbl = new JLabel("Available rates");
            availRatesLbl.setFont(LABEL_FONT_BOLD);
            availRatesLbl.setForeground(LABEL_TEXT_COLOR);
            availRatesPnl.add(availRatesLbl, gbc);

            // Create the available rates field and add it to the rates panel
            availRatesFld[index] = new JTextField("", 1);
            availRatesFld[index].setFont(LABEL_FONT_PLAIN);
            availRatesFld[index].setEditable(false);
            availRatesFld[index].setForeground(Color.BLACK);
            availRatesFld[index].setBackground(Color.WHITE);
            availRatesFld[index].setBorder(border);
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.weightx = 1.0;
            gbc.gridx++;
            availRatesPnl.add(availRatesFld[index], gbc);

            // Create a panel to contain the stream's text fields
            JPanel streamPnl = new JPanel(new GridBagLayout());
            JLabel streamNameLbl = new JLabel("Data stream name");
            streamNameLbl.setFont(LABEL_FONT_BOLD);
            gbc.insets.top = LABEL_VERTICAL_SPACING;
            gbc.insets.bottom = LABEL_VERTICAL_SPACING;
            gbc.gridwidth = 1;
            gbc.gridx = 0;
            streamPnl.add(streamNameLbl, gbc);

            // Create the data stream name input field
            streamNameFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getStreamName()), 7);
            streamNameFld[index].setFont(LABEL_FONT_PLAIN);
            streamNameFld[index].setEditable(true);
            streamNameFld[index].setForeground(Color.BLACK);
            streamNameFld[index].setBackground(Color.WHITE);
            streamNameFld[index].setBorder(border);
            gbc.gridx++;
            streamPnl.add(streamNameFld[index], gbc);

            // Create the maximum messages per cycle label
            JLabel maxMsgsPerCycleLbl = new JLabel("Maximum messages per cycle");
            maxMsgsPerCycleLbl.setFont(LABEL_FONT_BOLD);
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(maxMsgsPerCycleLbl, gbc);

            // Create the maximum messages per cycle input field
            maxMsgsPerCycleFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getMaxMsgsPerCycle()), 7);
            maxMsgsPerCycleFld[index].setFont(LABEL_FONT_PLAIN);
            maxMsgsPerCycleFld[index].setEditable(true);
            maxMsgsPerCycleFld[index].setForeground(Color.BLACK);
            maxMsgsPerCycleFld[index].setBackground(Color.WHITE);
            maxMsgsPerCycleFld[index].setBorder(border);
            maxMsgsPerCycleFld[index].setInputVerifier(verifyInputs);
            gbc.gridx++;
            streamPnl.add(maxMsgsPerCycleFld[index], gbc);

            // Create the maximum bytes per second label
            JLabel bytesPerSecLbl = new JLabel("Maximum bytes per second");
            bytesPerSecLbl.setFont(LABEL_FONT_BOLD);
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(bytesPerSecLbl, gbc);

            // Create the maximum bytes per second input field
            maxBytesPerSecFld[index] = new JTextField(String.valueOf(rateInfo.get(index).getMaxBytesPerSec()), 7);
            maxBytesPerSecFld[index].setFont(LABEL_FONT_PLAIN);
            maxBytesPerSecFld[index].setEditable(true);
            maxBytesPerSecFld[index].setForeground(Color.BLACK);
            maxBytesPerSecFld[index].setBackground(Color.WHITE);
            maxBytesPerSecFld[index].setBorder(border);
            maxBytesPerSecFld[index].setInputVerifier(verifyInputs);
            gbc.gridx++;
            streamPnl.add(maxBytesPerSecFld[index], gbc);

            // Add the rate calculator panel to the dialog
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.gridx = 0;
            gbc.gridy++;
            streamPnl.add(availRatesPnl, gbc);

            // Create a tab for each stream
            tabbedPane.addTab(rateInfo.get(index).getRateName(),
                              null,
                              streamPnl,
                              null);
        }
    }

    /**************************************************************************
     * Check if any rate parameter changed
     * 
     * @param maxSecPerMsg
     *            maximum number of seconds allowed between downlinking two of
     *            the same message
     * 
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one
     *            second
     * 
     * @param streamName
     *            array containing the stream name per stream
     * 
     * @param maxMsgsPerCycle
     *            array containing the maximum number of messages that can be
     *            downlinked before repeating the message list per stream
     * 
     * @param maxBytesPerSec
     *            array containing the maximum number of bytes that can be
     *            downlinked in one second per stream
     * 
     * @param includeUneven
     *            true to include unevenly time-spaced sample rate values;
     *            false to only include sample rates that are evenly
     *            time-spaced
     * 
     * @return true if any of the rate parameters changed
     *************************************************************************/
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
            maxSecPerMsgFld.setText(maxSecPerMsgFld.getText().trim());
            maxMsgsPerSecFld.setText(maxMsgsPerSecFld.getText().trim());

            // Step through each stream
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Remove any excess white space
                streamNameFld[index].setText(streamNameFld[index].getText().trim());
                maxMsgsPerCycleFld[index].setText(maxMsgsPerCycleFld[index].getText().trim());
                maxBytesPerSecFld[index].setText(maxBytesPerSecFld[index].getText().trim());
            }

            // Check if any rate parameter is blank
            if (maxSecPerMsgFld.getText().isEmpty()
                || maxMsgsPerSecFld.getText().isEmpty())
            {
                // Inform the user that a rate parameter is missing
                throw new CCDDException("All rate parameters must be entered");
            }

            // Check if the any rate parameter is not a positive integer value
            if (!maxSecPerMsgFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch())
                || !maxMsgsPerSecFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
            {
                // Inform the user that a rate is invalid
                throw new CCDDException("Rate parameter values must be positive integer values");
            }

            // Step through each stream
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Get the rate information index using this stream name
                int streamIndex = rateHandler.getRateInformationIndexByStreamName(streamNameFld[index].getText());

                // Check if this stream name exists and if it duplicates
                // another rate's stream name
                if (streamIndex != -1 && index != streamIndex)
                {
                    // Select the tab with the error
                    tabbedPane.setSelectedIndex(index);

                    // Inform the user that a stream name is duplicated
                    throw new CCDDException("Duplicate stream name");
                }

                // Check if any rate parameter is blank
                if (maxMsgsPerCycleFld[index].getText().isEmpty()
                    || maxBytesPerSecFld[index].getText().isEmpty())
                {
                    // Select the tab with the error
                    tabbedPane.setSelectedIndex(index);

                    // Inform the user that a rate parameter is missing
                    throw new CCDDException("All rate parameters must be entered");
                }

                // Check if the any rate parameter is not a positive integer
                // value
                if (!maxMsgsPerCycleFld[index].getText().matches(InputDataType.INT_POSITIVE.getInputMatch())
                    || !maxBytesPerSecFld[index].getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                {
                    // Select the tab with the error
                    tabbedPane.setSelectedIndex(index);

                    // Inform the user that a rate is invalid
                    throw new CCDDException("Rate parameters must be positive integer values");
                }
            }

            // Format the rate parameter fields
            maxSecPerMsgFld.setText(Integer.valueOf(maxSecPerMsgFld.getText()).toString());
            maxMsgsPerSecFld.setText(Integer.valueOf(maxMsgsPerSecFld.getText()).toString());

            // Step through each stream
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Format the rate parameter fields
                maxMsgsPerCycleFld[index].setText(Integer.valueOf(maxMsgsPerCycleFld[index].getText()).toString());
                maxBytesPerSecFld[index].setText(Integer.valueOf(maxBytesPerSecFld[index].getText()).toString());
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddRateParameterDialog.this,
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
