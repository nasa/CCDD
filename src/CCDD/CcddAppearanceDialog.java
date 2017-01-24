/**
 * CFS Command & Data Dictionary appearance dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import CCDD.CcddConstants.DialogOption;

/******************************************************************************
 * CFS Command & Data Dictionary application appearance dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddAppearanceDialog extends CcddDialogHandler
{
    private final CcddMain ccddMain;

    /**************************************************************************
     * Application appearance dialog class constructor
     * 
     * @param perfMain
     *            main class
     *************************************************************************/
    protected CcddAppearanceDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create the application appearance dialog
        initialize();
    }

    /**************************************************************************
     * Create the application appearance dialog
     *************************************************************************/
    private void initialize()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel to contain the look & feel components
        JPanel lafPanel = new JPanel(new GridBagLayout());
        lafPanel.setBorder(BorderFactory.createEmptyBorder());

        // Obtain the list of available look & feels to use in creating the
        // radio buttons
        LookAndFeelInfo[] lafInfo = UIManager.getInstalledLookAndFeels();

        // Check if any look & feels exist
        if (lafInfo.length != 0)
        {
            // Create storage for the look & feel descriptions
            String[][] lafDescriptions = new String[lafInfo.length][2];

            // Step through each look & feel
            for (int index = 0; index < lafInfo.length; index++)
            {
                // Store the look & feel name
                lafDescriptions[index][0] = lafInfo[index].getName();
            }

            // Create a panel containing a grid of radio buttons representing
            // the look & feels from which to choose
            addRadioButtons(ccddMain.getLookAndFeel(),
                            true,
                            lafDescriptions,
                            null,
                            "Select the application's 'look & feel'",
                            lafPanel,
                            gbc);

            // Add a listener for radio button selection change events
            addPropertyChangeListener(new PropertyChangeListener()
            {
                /******************************************************************
                 * Handle a radio button selection change event
                 *****************************************************************/
                @Override
                public void propertyChange(PropertyChangeEvent pce)
                {
                    // Check if the event indicates a radio button selection
                    // change
                    if (pce.getPropertyName().equals(RADIO_BUTTON_CHANGE_EVENT))
                    {
                        // Get the radio button selected
                        String buttonName = pce.getNewValue().toString();

                        // Check if the selected look & feel differs from the
                        // one currently in use
                        if (!ccddMain.getLookAndFeel().equals(buttonName))
                        {
                            // Save the selected look & feel name for storage
                            // in the program preferences backing store and
                            // implement it
                            ccddMain.setLookAndFeel(buttonName);

                            // Update this dialog so that it uses the selected
                            // look & feel. The button sizes must be
                            // recalculated and the dialog packed since each
                            // look & feel changes the sizes of components
                            SwingUtilities.updateComponentTreeUI(CcddAppearanceDialog.this);
                            CcddAppearanceDialog.this.setButtonWidth();
                            CcddAppearanceDialog.this.pack();
                        }
                    }
                }
            });

            // Display the Application Appearance dialog and wait for the user
            // to select the Okay button
            showOptionsDialog(ccddMain.getMainFrame(),
                              lafPanel,
                              "Application Appearance",
                              DialogOption.CLOSE_OPTION);
        }
        // No look & feels exist to choose
        else
        {
            // Inform the user that no look & feel exists on the server
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>No 'look & feel' exists",
                                                      "Appearance",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }
}
