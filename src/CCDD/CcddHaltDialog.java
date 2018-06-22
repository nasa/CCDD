/**
 * CFS Command & Data Dictionary cancellation dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
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
    private JProgressBar progBar;
    private JLabel textLbl;

    // Flag indicating if table importing/exporting is canceled by user input
    private boolean haltImport;

    // Number of divisions in the halt dialog's progress bar per data file
    private final int numDivisionPerStep;

    // Counters used to calculate the progress bar value
    private int progCount;
    private int prevProgCount;
    private int progStart;
    private int progTotal;
    private int minWidth;

    /**********************************************************************************************
     * Process cancellation dialog class constructor
     *
     * @param title
     *            dialog title
     *
     * @param label
     *            main dialog label, describing the current operation
     *
     * @param operation
     *            dialog label describing the termination operation
     *
     * @param numDivisionPerStep
     *            number of divisions per each major step in the operation
     *
     * @param numSteps
     *            total number of steps in the operation
     *
     * @param parent
     *            component over which to center the dialog
     *********************************************************************************************/
    CcddHaltDialog(String title,
                   String label,
                   String operation,
                   int numDivisionPerStep,
                   int numSteps,
                   Component parent)
    {
        haltImport = false;
        minWidth = -1;

        // Set the number of divisions within each step and use it, along with the number of items,
        // to calculate the total number of steps
        this.numDivisionPerStep = numDivisionPerStep;
        progTotal = numSteps * numDivisionPerStep;

        // Create the cancellation dialog
        initialize(title, label, operation, numDivisionPerStep != 0 && numSteps != 0, parent);
    }

    /**********************************************************************************************
     * Process cancellation dialog class constructor. No progress bar is displayed
     *
     * @param title
     *            dialog title
     *
     * @param label
     *            main dialog label, describing the current operation
     *
     * @param operation
     *            dialog label describing the termination operation
     *
     * @param parent
     *            parent component over which to center the dialog
     *********************************************************************************************/
    CcddHaltDialog(String title, String label, String operation, Component parent)
    {
        this(title, label, operation, 0, 0, parent);
    }

    /**********************************************************************************************
     * Get the reference to the progress bar
     *
     * @return Reference to the progress bar
     *********************************************************************************************/
    protected JProgressBar getProgressBar()
    {
        return progBar;
    }

    /**********************************************************************************************
     * Get the number of divisions per each major step in the operation
     *
     * @return The number of divisions per each major step in the operation
     *********************************************************************************************/
    protected int getNumDivisionPerStep()
    {
        return numDivisionPerStep;
    }

    /**********************************************************************************************
     * Set the total number of steps for the progress bar
     *
     * @param progTotal
     *            Total number of steps for the progress bar
     *********************************************************************************************/
    protected void setProgressTotal(int progTotal)
    {
        this.progTotal = progTotal;
    }

    /**********************************************************************************************
     * Set the main dialog label, describing the current operation, and resize the dialog to fit
     *
     * @param label
     *            main dialog label, describing the current operation
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
     * @return true if the Cancel button has been pressed
     *********************************************************************************************/
    protected boolean isHalted()
    {
        return haltImport;
    }

    /**********************************************************************************************
     * @param title
     *            dialog title
     *
     * @param label
     *            main dialog label, describing the current operation
     *
     * @param operation
     *            dialog label describing the termination operation
     *
     * @param showProgressBar
     *            true to display a progress bar
     *
     * @param parent
     *            parent component over which to center the dialog
     *********************************************************************************************/
    private void initialize(String title,
                            String label,
                            String operation,
                            boolean showProgressBar,
                            Component parent)
    {
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
                                     + CcddUtilities.colorHTMLText("*** Press </i>Halt<i> "
                                                                   + "to terminate "
                                                                   + operation
                                                                   + " ***",
                                                                   Color.RED)
                                     + "</b><br><br>",
                                     SwingConstants.CENTER);
        textLbl2.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        gbc.gridy++;
        dialogPnl.add(textLbl2, gbc);

        // Check if a progress be should be displayed
        if (showProgressBar)
        {
            // Add a progress bar to the dialog
            progBar = new JProgressBar(0, progTotal);
            progBar.setValue(0);
            progBar.setStringPainted(true);
            progBar.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.insets.bottom = 0;
            gbc.gridy++;
            dialogPnl.add(progBar, gbc);
        }

        // Display the cancellation dialog
        showOptionsDialog(parent,
                          dialogPnl,
                          title,
                          DialogOption.HALT_OPTION,
                          false,
                          false);
    }

    /**********************************************************************************************
     * Update the progress bar
     *
     * @param progText
     *            text to display within the progress bar; null to not change the text
     *
     * @param startValue
     *            initial value at which to begin this sequence in the process; -1 to not change
     *            the initial value
     *********************************************************************************************/
    protected void updateProgressBar(final String progText, int startValue)
    {
        // Check if the start value is provided
        if (startValue != -1)
        {
            // Initialize the progress counters
            progCount = 0;
            prevProgCount = 0;
            progStart = startValue;
        }

        // Update the progress counter
        progCount++;

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Since the progress bar involves a GUI update use invokeLater to execute the call on
             * the event dispatch thread
             *************************************************************************************/
            @Override
            public void run()
            {
                // Check if the minimum progress bar width is set
                if (minWidth == -1)
                {
                    // Set and store the minimum progress bar width
                    progBar.setMinimumSize(progBar.getSize());
                    minWidth = progBar.getSize().width;
                }

                // Get the progress bar graphics context
                Graphics gCont = progBar.getGraphics();

                // Check if the context is valid
                if (gCont != null)
                {
                    // Check if the progress text is provided
                    if (progText != null)
                    {
                        // Update the progress text
                        progBar.setString(" " + progText + " ");

                        // Check if the progress bar and dialog sizes need to change to accommodate
                        // the progress bar text
                        if (progBar.getPreferredSize().width > minWidth)
                        {
                            // Resize the progress bar and dialog
                            setPreferredSize(null);
                            progBar.setSize(progBar.getPreferredSize());
                            setSize(getPreferredSize());
                        }
                    }

                    // Step through the progress count values beginning with the last one processed
                    for (int count = prevProgCount + 1; count <= progCount; count++)
                    {
                        // Update the progress bar
                        progBar.setValue(progStart + (numDivisionPerStep * count / progTotal));
                        progBar.update(gCont);
                    }

                    // Store the last processed progress counter value
                    prevProgCount = progCount;

                    // Redraw the halt dialog
                    update(getGraphics());
                }
            }
        });
    }

    /**********************************************************************************************
     * Handle the close dialog button action
     *********************************************************************************************/
    @Override
    protected void closeDialog(int button)
    {
        // Set the flag to cancel verification
        haltImport = true;

        super.closeDialog(button);
    };
}
