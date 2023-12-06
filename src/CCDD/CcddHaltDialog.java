/**************************************************************************************************
 * /** \file CcddHaltDialog.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Dialog that displays a button for canceling the current operation, and optionally
 * displays a progress bar. The dialog is built on the CcddDialogHandler class.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * Process cancellation dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddHaltDialog extends CcddDialogHandler
{
    // Components accessed by multiple methods
    private JProgressBar upperProgBar;
    private JProgressBar lowerProgBar;
    private JLabel textLbl;

    // Flag indicating if the operation is canceled by user input
    private boolean isHalted;

    // Number of divisions in the halt dialog's progress bar per step
    private int upperNumDivisionPerStep;
    private int lowerNumDivisionPerStep;

    // Total number of items for the current step
    private int upperItemsPerStep;
    private int lowerItemsPerStep;

    // Counters used to calculate the progress bar value
    private int upperProgCount;
    private int upperPrevProgCount;
    private int upperProgStart;
    private int lowerProgCount;
    private int lowerPrevProgCount;
    private int lowerProgStart;
    private Component parent;

    /**********************************************************************************************
     * Process cancellation dialog class constructor with a single (optional) progress bar
     *
     * @param title              Dialog title
     *
     * @param label              Main dialog label, describing the current operation
     *
     * @param operation          Dialog label describing the termination operation
     *
     * @param numDivisionPerStep Number of divisions per each major step in the operation
     *
     * @param numSteps           Total number of steps in the operation
     *
     * @param parent             Component over which to center the dialog
     *********************************************************************************************/
    CcddHaltDialog(String title,
                   String label,
                   String operation,
                   int numDivisionPerStep,
                   int numSteps,
                   Component parent)
    {
        isHalted = false;
        upperProgBar = null;
        lowerProgBar = null;

        // Check if the progress bar should be displayed in the dialog
        if (numDivisionPerStep > 0 && numSteps > 0)
        {
            // Create the progress bar
            upperProgBar = new JProgressBar(0, numSteps);
        }

        // Create the cancellation dialog
        initialize(title,
                   label,
                   operation,
                   numDivisionPerStep,
                   numSteps,
                   false,
                   parent);
    }

    /**********************************************************************************************
     * Process cancellation dialog class constructor with one or two (optional) progress bars
     *
     * @param title                   Dialog title
     *
     * @param label                   Main dialog label, describing the current operation
     *
     * @param operation               Dialog label describing the termination operation
     *
     * @param upperNumDivisionPerStep Upper progress bar number of divisions per each major step in
     *                                the operation
     *
     * @param upperNumSteps           Upper progress bar total number of steps in the operation
     *
     * @param lowerNumDivisionPerStep Lower progress bar number of divisions per each major step in
     *                                the operation
     *
     * @param lowerNumSteps           Lower progress bar total number of steps in the operation
     *
     * @param parent                  Component over which to center the dialog
     *********************************************************************************************/
    CcddHaltDialog(String title,
                   String label,
                   String operation,
                   int upperNumDivisionPerStep,
                   int upperNumSteps,
                   int lowerNumDivisionPerStep,
                   int lowerNumSteps,
                   Component parent)
    {
        isHalted = false;
        upperProgBar = null;
        lowerProgBar = null;

        // Check if the upper progress bar should be displayed in the dialog
        if (upperNumDivisionPerStep > 0 && upperNumSteps > 0)
        {
            // Create the progress bar
            upperProgBar = new JProgressBar(0, upperNumSteps);
        }

        // Check if the lower progress bar should be displayed in the dialog
        if (lowerNumDivisionPerStep > 0 && lowerNumSteps > 0)
        {
            // Create the progress bar
            lowerProgBar = new JProgressBar(0, lowerNumSteps);
        }

        // Create the cancellation dialog
        initialize(title,
                   label,
                   operation,
                   upperNumDivisionPerStep,
                   upperNumSteps,
                   lowerNumDivisionPerStep,
                   lowerNumSteps,
                   false,
                   parent);
    }

    /**********************************************************************************************
     * Process cancellation dialog class constructor. Sets up a non-modal dialog
     *
     * @param showProgressBar True to display a progress bar in the dialog
     *********************************************************************************************/
    CcddHaltDialog(boolean showProgressBar)
    {
        isHalted = false;
        upperProgBar = null;
        lowerProgBar = null;

        // Check if the progress bar should be displayed
        if (showProgressBar)
        {
            // Create the progress bar
            upperProgBar = new JProgressBar();
            upperProgBar.setIndeterminate(true);
        }
    }

    /**********************************************************************************************
     * Process cancellation dialog class constructor with no progress bar displayed
     *
     * @param title     Dialog title
     *
     * @param label     Main dialog label, describing the current operation
     *
     * @param operation Dialog label describing the termination operation
     *
     * @param modal     True to make the dialog modal
     *
     * @param parent    Parent component over which to center the dialog
     *********************************************************************************************/
    CcddHaltDialog(String title, String label, String operation, boolean modal, Component parent)
    {
        this(title, label, operation, -1, -1, parent);
    }

    /**********************************************************************************************
     * Get the reference to the upper progress bar
     *
     * @return Reference to the upper progress bar
     *********************************************************************************************/
    protected JProgressBar getProgressBar()
    {
        return upperProgBar;
    }

    /**********************************************************************************************
     * Get the reference to the lower progress bar
     *
     * @return Reference to the lower progress bar
     *********************************************************************************************/
    protected JProgressBar getLowerProgressBar()
    {
        return lowerProgBar;
    }

    /**********************************************************************************************
     * Get the upper progress bar number of divisions per each major step in the operation
     *
     * @return The upper progress bar number of divisions per each major step in the operation
     *********************************************************************************************/
    protected int getNumDivisionPerStep()
    {
        return upperNumDivisionPerStep;
    }

    /**********************************************************************************************
     * Get the lower progress bar number of divisions per each major step in the operation
     *
     * @return The lower progress bar number of divisions per each major step in the operation
     *********************************************************************************************/
    protected int getLowerNumDivisionPerStep()
    {
        return lowerNumDivisionPerStep;
    }

    /**********************************************************************************************
     * Set the upper progress bar total number of items for the current step. If the number less
     * than 1 then 1 is used
     *
     * @param itemsPerStep Upper progress bar total number of items for the current step
     *********************************************************************************************/
    protected void setItemsPerStep(int itemsPerStep)
    {
        upperItemsPerStep = itemsPerStep > 0 ? itemsPerStep : 1;
    }

    /**********************************************************************************************
     * Set the lower progress bar total number of items for the current step. If the number less
     * than 1 then 1 is used
     *
     * @param itemsPerStep Lower progress bar total number of items for the current step
     *********************************************************************************************/
    protected void setLowerItemsPerStep(int itemsPerStep)
    {
        lowerItemsPerStep = itemsPerStep > 0 ? itemsPerStep : 1;
    }

    /**********************************************************************************************
     * Set the upper progress bar maximum progress bar value
     *
     * @param progMaximum Upper progress bar maximum progress bar value
     *********************************************************************************************/
    protected void setMaximum(int progMaximum)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                upperProgBar.setMaximum(progMaximum);
            }
        });
    }

    /**********************************************************************************************
     * Set the lower progress bar maximum progress bar value
     *
     * @param progMaximum Lower progress bar maximum progress bar value
     *********************************************************************************************/
    protected void setLowerMaximum(int progMaximum)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                lowerProgBar.setMaximum(progMaximum);
            }
        });
    }

    /**********************************************************************************************
     * Set the main dialog label, describing the current operation, and resize the dialog to fit
     *
     * @param label Main dialog label, describing the current operation
     *********************************************************************************************/
    protected void setLabel(String label)
    {
        textLbl.setText("<html><b>" + label + "...<br><br>");
        setPreferredSize(null);
        setSize(getPreferredSize());
    }

    /**********************************************************************************************
     * Check if the Cancel button has been pressed
     *
     * @return True if the Cancel button has been pressed
     *********************************************************************************************/
    protected boolean isHalted()
    {
        return isHalted;
    }

    /**********************************************************************************************
     * Create the process cancellation dialog
     *
     * @param title              Dialog title
     *
     * @param label              Main dialog label, describing the current operation
     *
     * @param operation          Dialog label describing the termination operation
     *
     * @param numDivisionPerStep Number of divisions per each major step in the operation
     *
     * @param numSteps           Total number of steps in the operation
     *
     * @param modal              False to allow the other application windows to still be operated
     *                           while the dialog is open
     *
     * @param parent             Parent component over which to center the dialog
     *
     * @return Index of the button pressed to exit the dialog
     *********************************************************************************************/
    protected int initialize(String title,
                             String label,
                             String operation,
                             int numDivisionPerStep,
                             int numSteps,
                             boolean modal,
                             Component parent)
    {
        return initialize(title,
                          label,
                          operation,
                          numDivisionPerStep,
                          numSteps,
                          -1,
                          -1,
                          modal,
                          parent);
    }

    /**********************************************************************************************
     * Create the process cancellation dialog
     *
     * @param title                   Dialog title
     *
     * @param label                   Main dialog label, describing the current operation
     *
     * @param operation               Dialog label describing the termination operation
     *
     * @param upperNumDivisionPerStep Upper progress bar number of divisions per each major step in
     *                                the operation
     *
     * @param upperNumSteps           Upper progress bar total number of steps in the operation
     *
     * @param lowerNumDivisionPerStep Lower progress bar number of divisions per each major step in
     *                                the operation
     *
     * @param lowerNumSteps           Lower progress bar total number of steps in the operation
     *
     * @param modal                   False to allow the other application windows to still be
     *                                operated while the dialog is open
     *
     * @param parent                  Parent component over which to center the dialog
     *
     * @return Index of the button pressed to exit the dialog
     *********************************************************************************************/
    protected int initialize(String title,
                             String label,
                             String operation,
                             int upperNumDivisionPerStep,
                             int upperNumSteps,
                             int lowerNumDivisionPerStep,
                             int lowerNumSteps,
                             boolean modal,
                             Component parent)
    {
        // Set the number of divisions within each step and use it, along with the number of items,
        // to calculate the total number of steps
        this.upperNumDivisionPerStep = upperNumDivisionPerStep;
        upperItemsPerStep = upperNumDivisionPerStep;
        int upperProgMaximum = upperNumSteps * upperNumDivisionPerStep;
        upperProgCount = 0;
        upperPrevProgCount = 0;
        upperProgStart = 0;
        this.lowerNumDivisionPerStep = lowerNumDivisionPerStep;
        lowerItemsPerStep = lowerNumDivisionPerStep;
        int lowerProgMaximum = lowerNumSteps * lowerNumDivisionPerStep;
        this.parent = parent;
        lowerProgCount = 0;
        lowerPrevProgCount = 0;
        lowerProgStart = 0;

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

        // Create the cancellation dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());
        textLbl = new JLabel();
        textLbl.setHorizontalAlignment(SwingConstants.LEFT);
        setLabel(label);
        textLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        gbc.gridy++;
        dialogPnl.add(textLbl, gbc);
        JLabel textLbl2 = new JLabel("<html><b>"
                                     + CcddUtilities.colorHTMLText("<i>*** Press </i>Halt<i> to terminate "
                                                                   + operation
                                                                   + " ***",
                                                                   Color.RED)
                                     + "</b><br><br>",
                                     SwingConstants.CENTER);
        textLbl2.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        gbc.gridy++;
        dialogPnl.add(textLbl2, gbc);

        // Check if the progress is displayed
        if (upperProgBar != null)
        {
            // Set the progress bar attributes and add it to the dialog
            upperProgBar.setIndeterminate(false);
            upperProgBar.setMinimum(0);
            upperProgBar.setMaximum(upperProgMaximum);
            upperProgBar.setValue(0);
            upperProgBar.setStringPainted(true);
            upperProgBar.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.bottom = 0;
            gbc.gridy++;
            dialogPnl.add(upperProgBar, gbc);
        }

        // Check if the progress is displayed
        if (lowerProgBar != null)
        {
            // Set the progress bar attributes and add it to the dialog
            lowerProgBar.setIndeterminate(false);
            lowerProgBar.setMinimum(0);
            lowerProgBar.setMaximum(lowerProgMaximum);
            lowerProgBar.setValue(0);
            lowerProgBar.setStringPainted(true);
            lowerProgBar.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.bottom = 0;
            gbc.gridy++;
            dialogPnl.add(lowerProgBar, gbc);
        }

        // Display the cancellation dialog
        return showOptionsDialog(parent, dialogPnl, title, DialogOption.HALT_OPTION, false, modal);
    }

    /**********************************************************************************************
     * Update the upper progress bar
     *
     * @param progText Text to display within the upper progress bar; null to not change the text
     *********************************************************************************************/
    protected void updateProgressBar(final String progText)
    {
        updateProgressBar(progText, -1);
    }

    /**********************************************************************************************
     * Update the upper progress bar
     *
     * @param progText   Text to display within the progress bar; null to not change the text
     *
     * @param startValue Initial value at which to begin this sequence in the process; -1 to not
     *                   change the initial value
     *********************************************************************************************/
    protected void updateProgressBar(final String progText, int startValue)
    {
        // Check if the progress is displayed
        if (upperProgBar != null)
        {
            // Check if the start value is provided
            if (startValue != -1)
            {
                // Initialize the progress counters
                upperProgCount = 0;
                upperPrevProgCount = 0;
                upperProgStart = startValue;
            }

            // Update the progress counter
            upperProgCount++;

            // Create a runnable object to be executed
            SwingUtilities.invokeLater(new Runnable()
            {
                /**********************************************************************************
                 * Since the progress bar involves a GUI update use invokeLater to execute the call
                 * on the event dispatch thread
                 *********************************************************************************/
                @Override
                public void run()
                {
                    // Get the progress bar graphics context
                    Graphics gCont = upperProgBar.getGraphics();

                    // Check if the context is valid
                    if (gCont != null)
                    {
                        // Check if the progress text is provided
                        if (progText != null)
                        {
                            int currentWidth = upperProgBar.getSize().width;

                            // Update the progress text
                            upperProgBar.setString(" " + progText + " ");

                            // Check if the progress bar and dialog sizes need to expand to
                            // accommodate the progress bar text
                            if (upperProgBar.getPreferredSize().width > currentWidth)
                            {
                                // Resize the progress bar and dialog
                                setPreferredSize(null);
                                upperProgBar.setSize(upperProgBar.getPreferredSize());
                                setSize(getPreferredSize());

                                // Position the dialog frame centered on the parent
                                setLocationRelativeTo(parent);
                            }
                        }

                        // Step through the progress count values beginning with the last one
                        // processed
                        for (int count = upperPrevProgCount + 1; count <= upperProgCount; count++)
                        {
                            // Update the progress bar
                            upperProgBar.setValue(upperProgStart + (count * upperNumDivisionPerStep / upperItemsPerStep));
                            upperProgBar.update(gCont);
                        }

                        // Store the last processed progress counter value
                        upperPrevProgCount = upperProgCount;

                        // Redraw the halt dialog
                        update(getGraphics());
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Update the lower progress bar
     *
     * @param progText Text to display within the lower progress bar; null to not change the text
     *********************************************************************************************/
    protected void updateLowerProgressBar(final String progText)
    {
        updateLowerProgressBar(progText, -1);
    }

    /**********************************************************************************************
     * Update the lower progress bar
     *
     * @param progText   Text to display within the progress bar; null to not change the text
     *
     * @param startValue Initial value at which to begin this sequence in the process; -1 to not
     *                   change the initial value
     *********************************************************************************************/
    protected void updateLowerProgressBar(final String progText, int startValue)
    {
        // Check if the progress is displayed
        if (lowerProgBar != null)
        {
            // Check if the start value is provided
            if (startValue != -1)
            {
                // Initialize the progress counters
                lowerProgCount = 0;
                lowerPrevProgCount = 0;
                lowerProgStart = startValue;
            }

            // Update the progress counter
            lowerProgCount++;

            // Create a runnable object to be executed
            SwingUtilities.invokeLater(new Runnable()
            {
                /**********************************************************************************
                 * Since the progress bar involves a GUI update use invokeLater to execute the call
                 * on the event dispatch thread
                 *********************************************************************************/
                @Override
                public void run()
                {
                    // Get the progress bar graphics context
                    Graphics gCont = lowerProgBar.getGraphics();

                    // Check if the context is valid
                    if (gCont != null)
                    {
                        // Check if the progress text is provided
                        if (progText != null)
                        {
                            int currentWidth = lowerProgBar.getSize().width;

                            // Update the progress text
                            lowerProgBar.setString(" " + progText + " ");

                            // Check if the progress bar and dialog sizes need to expand to
                            // accommodate the progress bar text
                            if (lowerProgBar.getPreferredSize().width > currentWidth)
                            {
                                // Resize the progress bar and dialog
                                setPreferredSize(null);
                                lowerProgBar.setSize(lowerProgBar.getPreferredSize());
                                setSize(getPreferredSize());

                                // Position the dialog frame centered on the parent
                                setLocationRelativeTo(parent);
                            }
                        }

                        // Step through the progress count values beginning with the last one
                        // processed
                        for (int count = lowerPrevProgCount + 1; count <= lowerProgCount; count++)
                        {
                            // Update the progress bar
                            lowerProgBar.setValue(lowerProgStart + (count * lowerNumDivisionPerStep / lowerItemsPerStep));
                            lowerProgBar.update(gCont);
                        }

                        // Store the last processed progress counter value
                        lowerPrevProgCount = lowerProgCount;

                        // Redraw the halt dialog
                        update(getGraphics());
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Handle the close dialog button action
     *********************************************************************************************/
    @Override
    protected void closeDialog(int button)
    {
        // Set the flag to cancel verification
        isHalted = true;

        super.closeDialog(button);
    };
}
