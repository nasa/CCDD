/**
 * CFS Command and Data Dictionary frame handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary frame handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddFrameHandler extends JFrame
{
    // Common button utility methods
    private final CcddButtonPanelHandler buttonHandler;

    // Sum of the minimum column widths, in pixels, of the table to display a table within a window
    private int tableWidth;

    // Window minimum width in pixels
    private int frameMinimumWidth;

    /**********************************************************************************************
     * Frame handler constructor
     *********************************************************************************************/
    CcddFrameHandler()
    {
        tableWidth = -1;
        frameMinimumWidth = MIN_WINDOW_WIDTH;

        // Create a handler for the window buttons
        buttonHandler = new CcddButtonPanelHandler()
        {
            /**************************************************************************************
             * Override the window closing method
             *
             * @param button
             *            button that initiated window closing
             *************************************************************************************/
            @Override
            protected void closeWindow(int button)
            {
                // Check if the Cancel button was pressed, or if the Okay button was pressed and
                // the selection is verified
                if (button == CANCEL_BUTTON || (button == OK_BUTTON && verifySelection()))
                {
                    // Close the window
                    closeFrame();
                }
            }
        };
    }

    /**********************************************************************************************
     * Close the window
     *********************************************************************************************/
    protected void closeFrame()
    {
        setVisible(false);
        dispose();
    }

    /**********************************************************************************************
     * Handle the frame close button press event. The default is to activate the last button in the
     * frame's button panel (close/cancel)
     *********************************************************************************************/
    protected void windowCloseButtonAction()
    {
        // Send a button press event for the last button in the frame's button panel, which is
        // either the close or cancel button
        buttonHandler.getExitButton().doClick();
    }

    /**********************************************************************************************
     * Placeholder for any actions required when this window is closed. This is intended to handle
     * special actions for non-modal windows
     *********************************************************************************************/
    protected void windowClosedAction()
    {
    }

    /**********************************************************************************************
     * Get the frame minimum width
     *
     * @return Frame minimum width
     *********************************************************************************************/
    protected int getMinimumWidth()
    {
        return Math.max(buttonHandler.getButtonPanelMinimumWidth(), frameMinimumWidth);
    }

    /**********************************************************************************************
     * Get the total width of the table to be displayed in the window which is the sum of the
     * minimum column sizes
     *
     * @return Total width, in pixels, of the minimum column sizes of the table to be displayed in
     *         the window
     *********************************************************************************************/
    protected int getTableWidth()
    {
        return tableWidth;
    }

    /**********************************************************************************************
     * Set the total width of the table to be displayed in the window which is the sum of the
     * minimum column sizes
     *
     * @param width
     *            total width, in pixels, of the minimum column sizes of the table to be displayed
     *            in the window
     *********************************************************************************************/
    protected void setTableWidth(int width)
    {
        tableWidth = width;
    }

    /**********************************************************************************************
     * Adjust the minimum allowed window width
     *
     * @param width
     *            window's minimum allowed width, in pixels
     *********************************************************************************************/
    protected void adjustFrameMinimumWidth(int width)
    {
        frameMinimumWidth = Math.max(width, frameMinimumWidth);
    }

    /**********************************************************************************************
     * Placeholder for method to verify the the window box selection(s) prior to closing
     *
     * @return true
     *********************************************************************************************/
    protected boolean verifySelection()
    {
        return true;
    }

    /**********************************************************************************************
     * Extract the window's button(s) from the supplied panel, then find the widest button
     * calculated from the button's text and icon. Set the width of the panel containing the
     * buttons based on the widest button
     *********************************************************************************************/
    protected void setButtonWidth()
    {
        buttonHandler.setButtonWidth();
    }

    /**********************************************************************************************
     * Set the number of rows occupied by the window's buttons
     *
     * @param rows
     *            number of window button rows
     *********************************************************************************************/
    protected void setButtonRows(int rows)
    {
        buttonHandler.setButtonRows(rows);
    }

    /**********************************************************************************************
     * Enable/disable the button panel buttons. Override to include other frame controls
     *
     * @param enable
     *            true to enable the buttons; false to disable
     *********************************************************************************************/
    protected void setControlsEnabled(boolean enable)
    {
        buttonHandler.setButtonsEnabled(enable);
    }

    /**********************************************************************************************
     * Create the window. If no buttons are provided (lower panel) then create the buttons and
     * button listeners needed based on the option type
     *
     * @param parent
     *            window to center the window over
     *
     * @param upperComponent
     *            upper window components
     *
     * @param buttonPnl
     *            panel containing the window's buttons; null if a defined option type is used
     *
     * @param defaultBtn
     *            reference to the JButton that is actuated if the Enter key is pressed; null to
     *            have no default button
     *
     * @param title
     *            title to display in the window frame
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION; ignored if buttonPnl isn't null
     *********************************************************************************************/
    protected void createFrame(Component parent,
                               JComponent upperComponent,
                               JPanel buttonPnl,
                               JButton defaultBtn,
                               String title,
                               DialogOption optionType)
    {
        // Set up button panel related items and combine the button and upper panels
        buttonPnl = buttonHandler.assembleWindowComponents(buttonPnl,
                                                           defaultBtn,
                                                           upperComponent,
                                                           optionType,
                                                           getContentPane(),
                                                           getRootPane());

        // Add a listener for dialog focus gain and lost events
        addWindowFocusListener(new WindowFocusListener()
        {
            /**************************************************************************************
             * Handle a dialog focus gained event
             *************************************************************************************/
            @Override
            public void windowGainedFocus(WindowEvent we)
            {
                // Set the default button to the last button pressed when the dialog (re)gains the
                // focus. This enables the special highlighting associated with the default button
                getRootPane().setDefaultButton(buttonHandler.getLastButtonPressed());
            }

            /**************************************************************************************
             * Handle a dialog focus lost event
             *************************************************************************************/
            @Override
            public void windowLostFocus(WindowEvent we)
            {
                // Set so that there is no default button while the dialog doesn't have the focus.
                // This removes the special highlighting associated with the default button
                getRootPane().setDefaultButton(null);
            }
        });

        // Check if the title is provided
        if (title != null)
        {
            // Set the window's title
            setTitle(title);
        }

        // Set the default close operation so that the window frame's close button doesn't
        // automatically exit the window. Instead, if this close button is pressed a button press
        // event is sent to the last button on the window's button panel
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        // Add a listener for window events
        addWindowListener(new WindowAdapter()
        {
            /**************************************************************************************
             * Handle the window frame close button press event
             *************************************************************************************/
            @Override
            public void windowClosing(WindowEvent we)
            {
                // Perform action needed when the window's frame close button is pressed
                windowCloseButtonAction();
            }

            /**************************************************************************************
             * Handle the window close event
             *************************************************************************************/
            @Override
            public void windowClosed(WindowEvent we)
            {
                // Perform action needed when the frame window is closed
                windowClosedAction();
            }
        });

        // Size the window to fit its components
        pack();

        // Set the preferred width to the smaller of (1) the width of all of the columns and (2)
        // the screen width. Set minimum width to the larger of (1) the default window width (or
        // its updated value) and (2) the button panel width. Set the preferred height to larger of
        // (1) the minimum window height and (2) the packed height, then select (1) the smaller of
        // this value and (2) the screen height. Set the minimum height to the default minimum
        // window height. Finally, set the initial frame size to the preferred size
        setPreferredSize(new Dimension(Math.min(tableWidth
                                                + ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing() * 2,
                                                java.awt.Toolkit.getDefaultToolkit().getScreenSize().width),
                                       Math.min(Math.max(getHeight(), MIN_WINDOW_HEIGHT),
                                                java.awt.Toolkit.getDefaultToolkit().getScreenSize().height)));
        setMinimumSize(new Dimension(getMinimumWidth(), MIN_WINDOW_HEIGHT));
        setSize(getPreferredSize());

        // Position the frame centered on the parent
        setLocationRelativeTo(parent);

        // Display the window
        setVisible(true);
    }
}
