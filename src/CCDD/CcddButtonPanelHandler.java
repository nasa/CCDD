/**
 * CFS Command and Data Dictionary button panel handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CANCEL_ICON;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;

import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary button panel handling class
 *************************************************************************************************/
public class CcddButtonPanelHandler
{
    // Panel containing the buttons
    private JPanel buttonPnl;

    // Number of rows on which to arrange the buttons
    private int buttonRows;

    // Reference to the last button pressed in the button panel
    private JButton lastButtonPressed;

    /**********************************************************************************************
     * Button panel handling class constructor
     *********************************************************************************************/
    CcddButtonPanelHandler()
    {
        // Initialize the button panel and set the button spacing
        buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.setLayout(new FlowLayout(FlowLayout.CENTER,
                                           ModifiableSpacingInfo.BUTTON_GAP.getSpacing(),
                                           ModifiableSpacingInfo.BUTTON_GAP.getSpacing()));
        buttonRows = 1;
    }

    /**********************************************************************************************
     * Set the number of rows occupied by the window's buttons
     *
     * @param rows
     *            number of button rows
     *********************************************************************************************/
    protected void setButtonRows(int rows)
    {
        buttonRows = rows;
    }

    /**********************************************************************************************
     * Get the reference to the last button pressed in the button panel
     *
     * @return Reference to the last button pressed in the button panel
     *********************************************************************************************/
    protected JButton getLastButtonPressed()
    {
        return lastButtonPressed;
    }

    /**********************************************************************************************
     * Get the button panel's close/cancel button which is assumed to be the last button in the
     * button panel
     *
     * @return Button panel close/cancel button
     *********************************************************************************************/
    protected JButton getExitButton()
    {
        return (JButton) buttonPnl.getComponent(buttonPnl.getComponentCount() - 1);
    }

    /**********************************************************************************************
     * Placeholder for method to close the window
     *
     * @param buttonSelected
     *            button selected when closing the window
     *********************************************************************************************/
    protected void closeWindow(int buttonSelected)
    {
    }

    /**********************************************************************************************
     * Get the minimum width needed to display the button panel
     *
     * @return Minimum width of the button panel
     *********************************************************************************************/
    protected int getButtonPanelMinimumWidth()
    {
        return buttonPnl.getWidth()
               + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
    }

    /**********************************************************************************************
     * Enable/disable the button panel buttons
     *
     * @param enable
     *            true to enable the buttons; false to disable
     *********************************************************************************************/
    protected void setButtonsEnabled(boolean enable)
    {
        // Get the number of buttons in the panel
        int numButtons = buttonPnl.getComponentCount();

        // Step through each button
        for (int index = 0; index < numButtons; index++)
        {
            // Create a pointer to the individual button for easier reference
            JButton button = (JButton) buttonPnl.getComponent(index);

            // Set the button enable state based on the input flag
            button.setEnabled(enable);
        }
    }

    /**********************************************************************************************
     * Extract the button(s) from the supplied panel, then find the widest button calculated from
     * the button's text and icon. Set the width of the panel containing the buttons based on the
     * widest button
     *********************************************************************************************/
    protected void setButtonWidth()
    {
        int maxWidth = 0;
        int maxHeight = 0;

        // Get the number of buttons in the panel
        int numButtons = buttonPnl.getComponentCount();

        // Step through each button
        for (int index = 0; index < numButtons; index++)
        {
            // Create a pointer to the individual button for easier reference
            JButton button = (JButton) buttonPnl.getComponent(index);

            // Check if the button has no text (i.e., this is a placeholder button used for
            // positioning the other buttons)
            if (button.getText().isEmpty())
            {
                // Skip this button
                continue;
            }

            // Change the button padding
            setButtonMargins(button);

            // Calculate the button width based on the width of the icon, gap between the icon and
            // text, width of the text, left and right margins, and the button border
            int width = button.getIcon().getIconWidth()
                        + button.getIconTextGap()
                        + (int) button.getFontMetrics(ModifiableFontInfo.DIALOG_BUTTON.getFont()).getStringBounds(button.getText(),
                                                                                                                  button.getGraphics())
                                      .getWidth()
                        + button.getMargin().left
                        + button.getMargin().right
                        + button.getBorder().getBorderInsets(button).left
                        + button.getBorder().getBorderInsets(button).right;

            // Check if this is the widest button found in the group
            if (width > maxWidth)
            {
                // Store the width
                maxWidth = width;
            }

            // Get the height of the button text
            int height = (int) button.getFontMetrics(ModifiableFontInfo.DIALOG_BUTTON.getFont()).getStringBounds(button.getText(),
                                                                                                                 button.getGraphics())
                                     .getHeight();

            // Check if the button's icon height is greater than the text height
            if (button.getIcon().getIconHeight() > height)
            {
                // Use the icon height since it's larger
                height = button.getIcon().getIconHeight();
            }

            // Calculate the button height based on the icon/text height, the top and bottom
            // margin, and the button border
            height += button.getMargin().top
                      + button.getMargin().bottom
                      + button.getBorder().getBorderInsets(button).top
                      + button.getBorder().getBorderInsets(button).bottom;

            // Check if this button has the greatest height
            if (height > maxHeight)
            {
                // Store the height
                maxHeight = height;
            }
        }

        // Step through each button again, now that the width and height are known
        for (int index = 0; index < numButtons; index++)
        {
            // Set the size of the button(s)
            ((JButton) buttonPnl.getComponent(index)).setSize(maxWidth, maxHeight);
            ((JButton) buttonPnl.getComponent(index)).setPreferredSize(new Dimension(maxWidth, maxHeight));
        }

        // Calculate the number of columns to display the buttons
        int numColumns = (int) Math.ceil((float) numButtons / buttonRows);

        // Size the panel containing the buttons based on the buttons' widths and heights
        Dimension buttonPnlCoord = new Dimension(maxWidth * numColumns
                                                 + (ModifiableSpacingInfo.BUTTON_GAP.getSpacing() + 1)
                                                   * (numColumns + 1),
                                                 maxHeight * buttonRows
                                                                       + (ModifiableSpacingInfo.BUTTON_GAP.getSpacing() + 1)
                                                                         * (buttonRows + 1));

        // Set the button panel to a fixed size so that the buttons stay positioned correctly
        // relative to each other
        buttonPnl.setPreferredSize(buttonPnlCoord);
        buttonPnl.setMaximumSize(buttonPnlCoord);
        buttonPnl.setMinimumSize(buttonPnlCoord);
    }

    /**********************************************************************************************
     * Create a button
     *
     * @param buttonText
     *            text to display on the button
     *
     * @param iconName
     *            icon file name
     *
     * @param key
     *            key mnemonic for the menu item
     *
     * @param toolTip
     *            tool tip text to display when the pointer hovers over this button; null to not
     *            display a tool tip
     *
     * @return Button created
     *********************************************************************************************/
    protected static JButton createButton(String buttonText,
                                          String iconName,
                                          int key,
                                          String toolTip)
    {
        // Create button
        JButton button = new JButton(buttonText,
                                     new ImageIcon(CcddButtonPanelHandler.class.getResource(iconName)));
        button.setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
        button.setMnemonic(key);
        button.setToolTipText(CcddUtilities.wrapText(toolTip,
                                                     ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Change the button padding
        setButtonMargins(button);

        return button;
    }

    /**********************************************************************************************
     * Change the button padding between the button's icon/text and the perimeter of the button
     *
     * @param button
     *            button to adjust
     *********************************************************************************************/
    private static void setButtonMargins(JButton button)
    {
        // Change the button padding between the button's icon/text and the perimeter of the button
        button.setMargin(new Insets(1, 3, 1, 3));
    }

    /**********************************************************************************************
     * Create the buttons and button listeners based on the option type provided
     *
     * @param optionType
     *            DialogOption type
     *
     * @return Reference to the JPanel containing the buttons
     *********************************************************************************************/
    private JPanel createButtonPanel(DialogOption optionType)
    {
        // Create the Okay button
        JButton btnOkButton = new JButton(optionType.getButtonText(),
                                          new ImageIcon(getClass().getResource(optionType.getButtonIcon())));
        btnOkButton.setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
        btnOkButton.setMnemonic(optionType.getButtonMnemonic());
        buttonPnl.add(btnOkButton);

        // Add a listener for the Okay button
        btnOkButton.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Set the selected button to indicate Okay and exit the window
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                closeWindow(OK_BUTTON);
            }
        });

        // Check if the window has more than one button
        if (optionType.getNumButtons() == 2)
        {
            // Create the Cancel button
            JButton btnCancelButton = new JButton(optionType.getSecondaryButtonText(),
                                                  new ImageIcon(getClass().getResource(CANCEL_ICON)));
            btnCancelButton.setFont(ModifiableFontInfo.DIALOG_BUTTON.getFont());
            btnCancelButton.setMnemonic(KeyEvent.VK_C);
            buttonPnl.add(btnCancelButton);

            // Add a listener for the Cancel button
            btnCancelButton.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Set the selected button to indicate Cancel and exit the window
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    closeWindow(CANCEL_BUTTON);
                }
            });
        }

        return buttonPnl;
    }

    /**********************************************************************************************
     * Create the window's button panel and combine with the upper components, set up keyboard
     * focus management, and determine the window's exit button. If no button panel is provided
     * then create the buttons and button listeners needed based on the option type
     *
     * @param btnPanel
     *            panel containing the window's buttons; null if a defined option type is used
     *
     * @param defaultBtn
     *            reference to the JButton that is actuated if the Enter key is pressed; null to
     *            have no default button (unless btnPanel is null as well, in which case the first
     *            button is set as the default)
     *
     * @param upperComponent
     *            upper window components
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION. Ignored if btnPanel isn't null
     *
     * @param contentPane
     *            the content pane for the calling container
     *
     * @param rootPane
     *            the root pane for the calling container
     *
     * @return JPanel containing the window's buttons
     *********************************************************************************************/
    protected JPanel assembleWindowComponents(JPanel btnPanel,
                                              JButton defaultBtn,
                                              JComponent upperComponent,
                                              DialogOption optionType,
                                              final Container contentPane,
                                              final JRootPane rootPane)
    {
        // Remove any existing content from the dialog. This is necessary if the dialog content is
        // altered.
        contentPane.removeAll();
        buttonPnl.removeAll();

        // Check if no buttons were provided
        if (btnPanel == null)
        {
            // Create the button panel based on the option type provided
            buttonPnl = createButtonPanel(optionType);

            // Check if the option type specifies a default button
            if (optionType.getDefaultButton() != -1)
            {
                // Default to the button as specified by the option type
                defaultBtn = (JButton) buttonPnl.getComponent(optionType.getDefaultButton());
            }
            // No default button is specified
            else
            {
                defaultBtn = null;
            }
        }
        // A button panel was provided
        else
        {
            // Store the button panel in the button handler
            buttonPnl = btnPanel;
        }

        // Size and position the window's button(s)
        setButtonWidth();

        // Set the reference to the last button pressed to the default button
        lastButtonPressed = defaultBtn;

        // Create a listener for button press events
        ActionListener buttonListener = new ActionListener()
        {
            /**************************************************************************************
             * Handle a button press event
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Store the button reference as the last button pressed
                lastButtonPressed = (JButton) ae.getSource();

                // Set the button pressed as the new default button
                rootPane.setDefaultButton(lastButtonPressed);
            }
        };

        // Step through each button in the panel
        for (int index = 0; index < buttonPnl.getComponentCount(); index++)
        {
            // Add the button press listener to this button
            ((JButton) buttonPnl.getComponent(index)).addActionListener(buttonListener);
        }

        // Create a panel to contain the button panel. This is necessary so that the button panel
        // remains centered as the window is resized
        JPanel bpPanel = new JPanel();
        bpPanel.add(buttonPnl);

        // Add padding to the bottom of the button panel
        bpPanel.setBorder(BorderFactory.createEmptyBorder(0,
                                                          0,
                                                          ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing(),
                                                          0));

        // Add padding around the window's upper component
        upperComponent.setBorder(BorderFactory.createEmptyBorder(ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing(),
                                                                 ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing(),
                                                                 ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing(),
                                                                 ModifiableSpacingInfo.DIALOG_BORDER_PAD.getSpacing()));

        // Set the layout manager for the window and add the upper and lower components
        contentPane.setLayout(new BorderLayout());
        contentPane.add(upperComponent, BorderLayout.CENTER);
        contentPane.add(bpPanel, BorderLayout.PAGE_END);

        // Set the default dialog button
        rootPane.setDefaultButton(defaultBtn);

        return buttonPnl;
    }
}
