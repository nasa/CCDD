/**
 * CFS Command & Data Dictionary variable padding byte alignment assignment
 * dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PADDING_ALIGNMENT;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/******************************************************************************
 * CFS Command & Data Dictionary variable padding byte alignment assignment
 * dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddPaddingAlignmentDialog extends CcddDialogHandler
{
    // Class reference
    private final CcddMain ccddMain;

    /**************************************************************************
     * Variable padding byte alignment assignment dialog class constructor
     *
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddPaddingAlignmentDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create the variable padding byte byte alignment assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the variable padding byte alignment assignment dialog
     *************************************************************************/
    private void initialize()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.CENTER,
                                                        GridBagConstraints.NONE,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the byte alignment value label
        JLabel byteAlignmentLbl = new JLabel("Set byte alignment");
        byteAlignmentLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        dialogPnl.add(byteAlignmentLbl, gbc);

        // Create a slider of the available alignment choices and add it to the
        // dialog
        final JSlider byteAlignmentSld = new JSlider(0,
                                                     6,
                                                     (int) (Math.log(Double.valueOf(ccddMain.getProgPrefs().get(PADDING_ALIGNMENT,
                                                                                                                "4")))
                                                            / Math.log(2)));
        byteAlignmentSld.setSnapToTicks(true);
        byteAlignmentSld.setMajorTickSpacing(1);
        byteAlignmentSld.setPaintTicks(true);
        Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
        labels.put(0, new JLabel("1"));
        labels.put(1, new JLabel("2"));
        labels.put(2, new JLabel("4"));
        labels.put(3, new JLabel("8"));
        labels.put(4, new JLabel("16"));
        labels.put(5, new JLabel("32"));
        labels.put(6, new JLabel("64"));
        byteAlignmentSld.setLabelTable(labels);
        byteAlignmentSld.setPaintLabels(true);

        // Add a listener for mouse button clicks
        byteAlignmentSld.addMouseListener(new MouseAdapter()
        {
            /******************************************************************
             * Handle a mouse button click on the slider
             *****************************************************************/
            @Override
            public void mouseClicked(MouseEvent me)
            {
                // Set the slider value based on the mouse pointer position
                // when the button is clicked
                BasicSliderUI ui = (BasicSliderUI) byteAlignmentSld.getUI();
                int value = ui.valueForXPosition(me.getX());
                byteAlignmentSld.setValue(value);
            }
        });

        // Add the slider to the dialog
        gbc.gridy++;
        dialogPnl.add(byteAlignmentSld, gbc);

        // Get the user's input
        if (showOptionsDialog(ccddMain.getMainFrame(),
                              dialogPnl,
                              "Byte Alignment",
                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Store the padding alignment value
            ccddMain.getProgPrefs().put(PADDING_ALIGNMENT,
                                        String.valueOf((int) Math.pow(byteAlignmentSld.getValue(), 2)));
        }
    }
}
