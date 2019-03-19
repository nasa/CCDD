/**
 * CFS Command and Data Dictionary custom dialog handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CANCEL_ICON;
import static CCDD.CcddConstants.CHECK_BOX_CHANGE_EVENT;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.ERROR_ICON;
import static CCDD.CcddConstants.IGNORE_BUTTON;
import static CCDD.CcddConstants.INFORMATION_ICON;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.OK_ICON;
import static CCDD.CcddConstants.QUESTION_ICON;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;
import static CCDD.CcddConstants.UPDATE_BUTTON;
import static CCDD.CcddConstants.WARNING_ICON;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.MultilineLabel;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary dialog handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddDialogHandler extends JDialog
{
    // Common button utility methods
    private final CcddButtonPanelHandler buttonHandler;

    // Dialog button selected by the user
    private int buttonSelected;

    // Radio button selected by the user
    private final List<String> radioButtonSelected;

    // Text field containing the file name(s) chosen by the user
    private JTextField nameField;

    // Check boxes generated from user-supplied list
    private JCheckBox[] checkBox;

    // Component that initially has the focus; null to set the focus to the first (default)
    // component
    private Component initialFocusComponent;

    /**********************************************************************************************
     * File extension filter class
     *********************************************************************************************/
    private class ExtensionFilter extends FileFilter
    {
        private final String extension;
        private final String description;

        /******************************************************************************************
         * File extension filter class constructor
         *
         * @param extension
         *            file extension
         *
         * @param description
         *            file extension description
         *****************************************************************************************/
        ExtensionFilter(String extension, String description)
        {
            this.extension = extension;
            this.description = description;
        }

        /******************************************************************************************
         * Determine if the specified file matches the file extension filter
         *
         * @param file
         *            file or directory path
         *
         * @return true if the file matches the extension or is a directory path
         *****************************************************************************************/
        @Override
        public boolean accept(File file)
        {
            boolean isAccepted = false;

            // Check if the file represents a directory
            if (file.isDirectory())
            {
                // Accept all directories
                isAccepted = true;
            }
            // It's a file
            else
            {
                // Get the file path and name
                String path = file.getAbsolutePath();

                // Check if the file ends with the extension. Extensions are case sensitive
                if (path.endsWith(extension))
                {
                    // Accept the file
                    isAccepted = true;
                }
            }

            return isAccepted;
        }

        /******************************************************************************************
         * Get the file extension
         *
         * @return File extension
         *****************************************************************************************/
        protected String getExtension()
        {
            return extension;
        }

        /******************************************************************************************
         * Get the file extension description
         *
         * @return File extension description
         *****************************************************************************************/
        @Override
        public String getDescription()
        {
            return description;
        }
    }

    /**********************************************************************************************
     * Dialog box handler constructor
     *********************************************************************************************/
    CcddDialogHandler()
    {
        buttonSelected = JOptionPane.CLOSED_OPTION;
        radioButtonSelected = new ArrayList<String>();
        checkBox = null;

        // Create a handler for the dialog buttons
        buttonHandler = new CcddButtonPanelHandler()
        {
            /**************************************************************************************
             * Override the window closing method
             *
             * @param button
             *            button that initiated dialog closing
             *************************************************************************************/
            @Override
            protected void closeWindow(int button)
            {
                // Check if the Cancel button was pressed, or if the Okay button was pressed and
                // the selection is verified
                if (button == CANCEL_BUTTON || (button == OK_BUTTON && verifySelection()))
                {
                    // Close the dialog, indicating the selected button
                    closeDialog(button);
                }
            }
        };
    }

    /**********************************************************************************************
     * Close the dialog box
     *********************************************************************************************/
    protected void closeDialog()
    {
        setVisible(false);
        dispose();
    }

    /**********************************************************************************************
     * Set the button selected and close the dialog box
     *
     * @param button
     *            selected button to return
     *********************************************************************************************/
    protected void closeDialog(int button)
    {
        buttonSelected = button;
        closeDialog();
    }

    /**********************************************************************************************
     * Handle the frame close button press event. The default is to activate the last button in the
     * dialog's button panel (close/cancel)
     *********************************************************************************************/
    protected void windowCloseButtonAction()
    {
        // Send a button press event for the last button in the dialog's button panel, which is
        // either the close or cancel button
        buttonHandler.getExitButton().doClick();
    }

    /**********************************************************************************************
     * Placeholder for any actions required when this dialog is closed. This is intended to handle
     * special actions for non-modal dialogs
     *********************************************************************************************/
    protected void windowClosedAction()
    {
    }

    /**********************************************************************************************
     * Get the dialog minimum width
     *
     * @return Dialog minimum width
     *********************************************************************************************/
    protected int getMinimumWidth()
    {
        return Math.max(buttonHandler.getButtonPanelMinimumWidth(),
                        ModifiableSizeInfo.MIN_DIALOG_WIDTH.getSize());
    }

    /**********************************************************************************************
     * Set the component to initially have the focus
     *
     * @param component
     *            component to initially have the focus
     *********************************************************************************************/
    protected void setInitialFocusComponent(Component component)
    {
        initialFocusComponent = component;
    }

    /**********************************************************************************************
     * Get the currently selected radio button's text component for the first radio button group
     *
     * @return Name associated with the currently selected radio button; null if no radio button is
     *         selected
     *********************************************************************************************/
    protected String getRadioButtonSelected()
    {
        return getRadioButtonSelected(0);
    }

    /**********************************************************************************************
     * Get the currently selected radio button's text component for the specified radio button
     * group
     *
     * @param index
     *            radio button group index
     *
     * @return Name associated with the currently selected radio button for the specified radio
     *         button group; null if no radio button is selected
     *********************************************************************************************/
    protected String getRadioButtonSelected(int index)
    {
        String selected = null;

        // Check if the index is valid
        if (index < radioButtonSelected.size())
        {
            // Get the selected radio button for the button group
            selected = radioButtonSelected.get(index);
        }

        return selected;
    }

    /**********************************************************************************************
     * Get the reference to the file chooser file name text field
     *
     * @return Reference to the file chooser file name text field
     *********************************************************************************************/
    protected JTextField getFileNameField()
    {
        return nameField;
    }

    /**********************************************************************************************
     * Get the reference to the check box array
     *
     * @return Reference to the check box array
     *********************************************************************************************/
    protected JCheckBox[] getCheckBoxes()
    {
        return checkBox;
    }

    /**********************************************************************************************
     * Get the text component for the currently selected check box(es)
     *
     * @return Array containing the name associated with the currently selected check box(es);
     *         empty array if no check box is selected
     *********************************************************************************************/
    protected String[] getCheckBoxSelected()
    {
        List<String> checked = new ArrayList<String>();

        // Step through each check box
        for (JCheckBox cbox : checkBox)
        {
            // Check if the box is selected
            if (cbox.isSelected())
            {
                // Add the check box test to the list
                checked.add(cbox.getText());
            }
        }

        return checked.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Display a non-resizable, modal message dialog with the provided icon and return the button
     * type selected
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @param icon
     *            icon to display to the left of the text message
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    String title,
                                    DialogOption optionType,
                                    Icon icon)
    {
        return createDialog(parent,
                            message,
                            null,
                            null,
                            title,
                            optionType,
                            icon,
                            false,
                            true);
    }

    /**********************************************************************************************
     * Display a non-resizable, modal message dialog and return the button type selected. The icon
     * displayed is based on the message type
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param messageType
     *            message type: JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE,
     *            JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE, or
     *            JOptionPane.ERROR_MESSAGE
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    String title,
                                    int messageType,
                                    DialogOption optionType)
    {
        return showMessageDialog(parent,
                                 message,
                                 title,
                                 messageType,
                                 optionType,
                                 true);
    }

    /**********************************************************************************************
     * Display a non-resizable message dialog and return the button type selected. The icon
     * displayed is based on the message type. Dialog modality is based on the input flag
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param messageType
     *            message type: JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE,
     *            JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE, or
     *            JOptionPane.ERROR_MESSAGE
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @param modal
     *            false to allow the main application window to still be operated while the dialog
     *            is open
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    String title,
                                    int messageType,
                                    DialogOption optionType,
                                    boolean modal)
    {
        Icon icon = null;

        // Get the icon to display beside the text message based on the current look & feel. If the
        // look & feel doesn't supply icons then revert to a default set
        switch (messageType)
        {
            case JOptionPane.PLAIN_MESSAGE:
                icon = null;
                break;

            case JOptionPane.INFORMATION_MESSAGE:
                icon = getIcon("OptionPane.informationIcon", INFORMATION_ICON);
                break;

            case JOptionPane.QUESTION_MESSAGE:
                icon = getIcon("OptionPane.questionIcon", QUESTION_ICON);
                break;

            case JOptionPane.WARNING_MESSAGE:
                icon = getIcon("OptionPane.warningIcon", WARNING_ICON);
                break;

            case JOptionPane.ERROR_MESSAGE:
                icon = getIcon("OptionPane.errorIcon", ERROR_ICON);
                break;
        }

        return createDialog(parent,
                            message,
                            null,
                            null,
                            title,
                            optionType,
                            icon,
                            false,
                            modal);
    }

    /**********************************************************************************************
     * Display a non-resizable, modal message dialog using user-supplied buttons, and return the
     * button type selected. The icon displayed is based on the message type
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param message
     *            text message to display
     *
     * @param buttonPnl
     *            panel containing the dialog buttons
     *
     * @param defaultBtn
     *            reference to the JButton that is actuated if the Enter key is pressed; null to
     *            have no default button
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param messageType
     *            message type: JOptionPane.PLAIN_MESSAGE, JOptionPane.INFORMATION_MESSAGE,
     *            JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE, or
     *            JOptionPane.ERROR_MESSAGE
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    JPanel buttonPnl,
                                    JButton defaultBtn,
                                    String title,
                                    int messageType)
    {
        Icon icon = null;

        // Get the icon to display beside the text message based on the current look & feel. If the
        // look & feel doesn't supply icons then revert to a default set
        switch (messageType)
        {
            case JOptionPane.PLAIN_MESSAGE:
                icon = null;
                break;

            case JOptionPane.INFORMATION_MESSAGE:
                icon = getIcon("OptionPane.informationIcon", INFORMATION_ICON);
                break;

            case JOptionPane.QUESTION_MESSAGE:
                icon = getIcon("OptionPane.questionIcon", QUESTION_ICON);
                break;

            case JOptionPane.WARNING_MESSAGE:
                icon = getIcon("OptionPane.warningIcon", WARNING_ICON);
                break;

            case JOptionPane.ERROR_MESSAGE:
                icon = getIcon("OptionPane.errorIcon", ERROR_ICON);
                break;
        }

        return createDialog(parent,
                            message,
                            buttonPnl,
                            defaultBtn,
                            title,
                            null,
                            icon,
                            false,
                            true);
    }

    /**********************************************************************************************
     * Get the icon associated with the UI key provided. If no icon is available for the key then
     * substitute a default icon
     *
     * @param UIKey
     *            an Object specifying the icon
     *
     * @param defaultIcon
     *            icon to use if the icon specified by UIKey cannot be found
     *
     * @return Icon associated with UIKey
     *********************************************************************************************/
    private Icon getIcon(String UIKey, String defaultIcon)
    {
        // Get the icon associated with the UI key
        Icon icon = UIManager.getIcon(UIKey);

        // Check if the icon wasn't found
        if (icon == null)
        {
            // Use the default icon instead
            icon = new ImageIcon(getClass().getResource(defaultIcon));
        }

        return icon;
    }

    /**********************************************************************************************
     * Create the Ignore/Ignore All/Cancel or Ignore All/Cancel dialog
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param ignoreToolTip
     *            Ignore button tool tip text; null if no tool tip is to be displayed
     *
     * @param ignoreAllToolTip
     *            Ignore All button tool tip text; null if no tool tip is to be displayed
     *
     * @param cancelToolTip
     *            Cancel button tool tip text; null if no tool tip is to be displayed
     *
     * @param noIgnore
     *            true to not display the Ignore button
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showIgnoreCancelDialog(Component parent,
                                         String message,
                                         String title,
                                         String ignoreToolTip,
                                         String ignoreAllToolTip,
                                         String cancelToolTip,
                                         boolean noIgnore)
    {

        // Create the Ignore button unless the flag is set to skip it
        final JButton btnIgnore = noIgnore
                                           ? null
                                           : CcddButtonPanelHandler.createButton("Ignore",
                                                                                 OK_ICON,
                                                                                 KeyEvent.VK_I,
                                                                                 ignoreToolTip);

        // Create the Ignore All button
        final JButton btnIgnoreAll = CcddButtonPanelHandler.createButton("Ignore All",
                                                                         DELETE_ICON,
                                                                         KeyEvent.VK_A,
                                                                         ignoreAllToolTip);

        // Create the Cancel button
        JButton btnCancel = CcddButtonPanelHandler.createButton("Cancel",
                                                                CANCEL_ICON,
                                                                KeyEvent.VK_C,
                                                                cancelToolTip);

        // Create a listener for the button actions
        ActionListener listener = new ActionListener()
        {
            /**************************************************************************************
             * Indicate the which button was pressed and close the dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                JButton button = (JButton) ae.getSource();

                closeDialog(button == btnIgnore
                                                ? UPDATE_BUTTON
                                                : (button == btnIgnoreAll
                                                                          ? IGNORE_BUTTON
                                                                          : CANCEL_BUTTON));
            }
        };

        // Set the button listeners. Check if the Ignore button is displayed
        if (!noIgnore)
        {
            btnIgnore.addActionListener(listener);
        }

        btnIgnoreAll.addActionListener(listener);
        btnCancel.addActionListener(listener);

        // Create the panel for the dialog buttons and add the dialog buttons to the panel
        JPanel buttonPnl = new JPanel();

        // Check if the Ignore button is displayed
        if (!noIgnore)
        {
            buttonPnl.add(btnIgnore);
        }

        buttonPnl.add(btnIgnoreAll);
        buttonPnl.add(btnCancel);

        return showMessageDialog(parent,
                                 message,
                                 buttonPnl,
                                 btnCancel,
                                 title,
                                 JOptionPane.QUESTION_MESSAGE);
    }

    /**********************************************************************************************
     * Display a modal, non-resizable user-interactive dialog using buttons defined by the supplied
     * option type. Return the button type selected
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param dialogPanel
     *            panel containing the dialog components
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param optionType
     *            DialogOption type
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Component dialogPanel,
                                    String title,
                                    DialogOption optionType)
    {
        // Used for the Appearance and database dialogs
        return createDialog(parent,
                            dialogPanel,
                            null,
                            null,
                            title,
                            optionType,
                            null,
                            false,
                            true);
    }

    /**********************************************************************************************
     * Display a user-interactive dialog using buttons defined by the supplied option type. The
     * dialog may be resized, based on the input flag. The dialog's modality is set by input flag.
     * Return the button type selected
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param dialogPanel
     *            panel containing the dialog components
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param optionType
     *            DialogOption type
     *
     * @param resizable
     *            true to allow the dialog to be resized
     *
     * @param modal
     *            false to allow the main application window to still be operated while the dialog
     *            is open
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Component dialogPanel,
                                    String title,
                                    DialogOption optionType,
                                    boolean resizable,
                                    boolean modal)
    {
        // Used for the Appearance and database dialogs
        return createDialog(parent,
                            dialogPanel,
                            null,
                            null,
                            title,
                            optionType,
                            null,
                            resizable,
                            modal);
    }

    /**********************************************************************************************
     * Display a modal, user-interactive dialog using buttons defined by the supplied option type.
     * The dialog may be resized, based on the input flag. Return the button type selected
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param dialogPanel
     *            panel containing the dialog components
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param optionType
     *            DialogOption type
     *
     * @param resizable
     *            true to allow the dialog to be resized
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Component dialogPanel,
                                    String title,
                                    DialogOption optionType,
                                    boolean resizable)
    {
        // Used for the Appearance and database dialogs
        return createDialog(parent,
                            dialogPanel,
                            null,
                            null,
                            title,
                            optionType,
                            null,
                            resizable,
                            true);
    }

    /**********************************************************************************************
     * Display a modal, user-interactive dialog using user-supplied buttons. The dialog may be
     * resized, based on the input flag. Return the button type selected
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param dialogPanel
     *            panel containing the dialog components
     *
     * @param lowerPanel
     *            panel containing the dialog buttons
     *
     * @param defaultBtn
     *            reference to the JButton that is actuated if the Enter key is pressed; null to
     *            have no default button
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param resizable
     *            true to allow the dialog to be resized
     *
     * @return Selected button type
     *********************************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Object dialogPanel,
                                    JPanel lowerPanel,
                                    JButton defaultBtn,
                                    String title,
                                    boolean resizable)
    {
        // Used for the Preferences dialog
        return createDialog(parent,
                            dialogPanel,
                            lowerPanel,
                            defaultBtn,
                            title,
                            DialogOption.OK_OPTION,
                            null,
                            resizable,
                            true);
    }

    /**********************************************************************************************
     * Display a dialog that allows the user to select one or more files
     *
     * @param main
     *            main class
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param fileName
     *            file name to display in the input field chosen; null if no file name is initially
     *            displayed. Ignored if folderOnly is true
     *
     * @param fileType
     *            describes the type of files when more than one file extension is supplied; null
     *            if only one (or no) file extension is provided. Ignored if folderOnly is true
     *
     * @param fileExtensions
     *            valid file extensions with description. Use null to allow any extension. Ignored
     *            if folderOnly is true
     *
     * @param multipleFiles
     *            true to allow selection of more than one file
     *
     * @param dialogTitle
     *            title to display in the dialog window frame
     *
     * @param folder
     *            file path to use as the initial folder to display
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @return Array containing the selected file handle(s). null if the Cancel button is selected.
     *         The first file reference is null if Okay is selected and the file name list is empty
     *********************************************************************************************/
    protected FileEnvVar[] choosePathFile(CcddMain main,
                                          Component parent,
                                          String fileName,
                                          String fileType,
                                          FileNameExtensionFilter[] fileExtensions,
                                          boolean multipleFiles,
                                          String dialogTitle,
                                          String folder,
                                          DialogOption optionType)
    {
        return choosePathFile(main,
                              parent,
                              fileName,
                              fileType,
                              fileExtensions,
                              false,
                              multipleFiles,
                              dialogTitle,
                              folder,
                              optionType,
                              null);
    }

    /**********************************************************************************************
     * Display a dialog that allows the user to select a file folder
     *
     * @param main
     *            main class
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param dialogTitle
     *            title to display in the dialog window frame
     *
     * @param folder
     *            file path to use as the initial folder to display
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @return Array containing the selected file handle(s). null if the Cancel button is selected.
     *         The first file reference is null if Okay is selected and the file name list is empty
     *********************************************************************************************/
    protected FileEnvVar[] choosePathFile(CcddMain main,
                                          Component parent,
                                          String dialogTitle,
                                          String folder,
                                          DialogOption optionType)
    {
        return choosePathFile(main,
                              parent,
                              null,
                              null,
                              null,
                              true,
                              false,
                              dialogTitle,
                              folder,
                              optionType,
                              null);
    }

    /**********************************************************************************************
     * Display a dialog that allows the user to select one or more files or a folder. Optionally
     * allow a panel containing other components to be displayed beneath the file chooser portion
     *
     * @param main
     *            main class
     *
     * @param parent
     *            window over which to center the dialog
     *
     * @param fileName
     *            file name to display in the input field chosen; null if no file name is initially
     *            displayed. Ignored if folderOnly is true (may be null)
     *
     * @param fileType
     *            describes the type of files when more than one file extension is supplied; null
     *            if only one (or no) file extension is provided. Ignored if folderOnly is true
     *            (may be null)
     *
     * @param fileExtensions
     *            valid file extensions with description. Use null to allow any extension. Ignored
     *            if folderOnly is true (may be null)
     *
     * @param folderOnly
     *            true to allow only folders to be selected
     *
     * @param multipleFiles
     *            true to allow selection of more than one file. Unused if folderOnly is true
     *
     * @param dialogTitle
     *            title to display in the dialog window frame
     *
     * @param folder
     *            file path to use as the initial folder to display
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @param lowerPanel
     *            JPanel containing other components to display below the JChooser component; null
     *            if no lower components are to be displayed
     *
     * @return Array containing the selected file handle(s). null if the Cancel button is selected.
     *         The first file reference is null if Okay is selected and the file name list is empty
     *********************************************************************************************/
    protected FileEnvVar[] choosePathFile(CcddMain main,
                                          Component parent,
                                          String fileName,
                                          String fileType,
                                          FileNameExtensionFilter[] fileExtensions,
                                          boolean folderOnly,
                                          boolean multipleFiles,
                                          String dialogTitle,
                                          String folder,
                                          DialogOption optionType,
                                          JPanel lowerPanel)
    {
        FileEnvVar[] file = new FileEnvVar[1];

        // Get the environment variables within the folder path
        Map<String, String> envVars = FileEnvVar.getEnvVars(folder);

        // Create the file chooser. Set the path to the one from the back store per the provided
        // key; if no entry exists for the key then use the default path (the default location is
        // operating system dependent)
        final JFileChooser chooser = new JFileChooser(FileEnvVar.expandEnvVars(folder, envVars)
                                                      + File.separator
                                                      + ".");

        // True to allow multiple files to be selected
        chooser.setMultiSelectionEnabled(multipleFiles);

        // Locate the file name input field in the file chooser dialog. In order to use custom
        // buttons in the file chooser dialog, the file name input field must be located and the
        // inputs to it listened for so that the selected file can be updated. Otherwise, changes
        // made to the file name are ignored and when the Okay button is pressed the file name used
        // is the highlighted one in the file list box
        nameField = getFileChooserTextField(chooser);

        // Check if only a folder is allowed to be chosen
        if (folderOnly)
        {
            // Allow only the selection of a folder (not a file)
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            // Get the path to initially display
            fileName = FileEnvVar.restoreEnvVars(chooser.getCurrentDirectory().getAbsolutePath(),
                                                 envVars);
        }
        // Not folder-only
        else
        {
            // Check if no name is specified
            if (fileName == null)
            {
                // Set the file name to blank
                fileName = "";
            }
            // Check if the file name is present
            else if (!fileName.isEmpty())
            {
                // Bound the file name with quotes
                fileName = "\"" + fileName + "\"";
            }

            // Allow only the selection of files
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            // Check if one or more file extensions are specified
            if (fileExtensions != null)
            {
                // Check if more than one file extension is provided
                if (fileExtensions.length > 1)
                {
                    String extensions = "";
                    List<String> extensionList = new ArrayList<String>();

                    // Step through each file extension
                    for (FileNameExtensionFilter fileExtension : fileExtensions)
                    {
                        // Build the extension names string and append the extension(s) to the list
                        extensions += "*."
                                      + CcddUtilities.convertArrayToString(fileExtension.getExtensions())
                                      + ", ";
                        extensionList.addAll(Arrays.asList(fileExtension.getExtensions()));
                    }

                    // Set the filter to the list of all applicable extensions so that initially
                    // all files of the acceptable types are displayed
                    chooser.setFileFilter(new FileNameExtensionFilter("All "
                                                                      + fileType
                                                                      + " files ("
                                                                      + CcddUtilities.removeTrailer(extensions,
                                                                                                    ", ")
                                                                      + ")",
                                                                      extensionList.toArray(new String[0])));
                }

                // Step through each file extension
                for (FileNameExtensionFilter fileExtension : fileExtensions)
                {
                    // Convert the extension list into a string
                    String extensions = CcddUtilities.convertArrayToString(fileExtension.getExtensions());

                    // Add the extension to the file chooser
                    chooser.addChoosableFileFilter(new ExtensionFilter(extensions,
                                                                       fileExtension.getDescription()
                                                                                   + " (*."
                                                                                   + extensions
                                                                                   + ")"));
                }

                // Check if only a single file extension is applicable
                if (fileExtensions.length == 1)
                {
                    // Set the file filter to show only files with the desired extension
                    chooser.setFileFilter(chooser.getChoosableFileFilters()[1]);
                }
            }

            // Add a listener for changes to the selected file(s)
            chooser.addPropertyChangeListener(new PropertyChangeListener()
            {
                /**********************************************************************************
                 * Handle changes to the file(s) selected. Whenever a file selection change strip
                 * the path name from the file(s) and build a list of just the names. Insert this
                 * list into the file chooser's file name text field. This is done automatically
                 * for most look and feels, but not all (e.g., GTK+)
                 *********************************************************************************/
                @Override
                public void propertyChange(PropertyChangeEvent pce)
                {
                    // Check if the file selection has changed
                    if (pce.getPropertyName().equals("SelectedFilesChangedProperty"))
                    {
                        String nameList = "";

                        // Step through the selected files
                        for (int index = 0; index < chooser.getSelectedFiles().length; index++)
                        {
                            // Append the file name without the path, surrounded by quotes, to the
                            // name list
                            nameList += "\"" + chooser.getSelectedFiles()[index].getName() + "\" ";
                        }

                        // Insert the file name list into the file chooser's file name text field
                        nameField.setText(nameList);
                    }
                }
            });
        }

        // Insert the file name into the file chooser's file name text field. Most look & feels do
        // this automatically, but not all (e.g., GTK+)
        nameField.setText(fileName);

        // Hide the file chooser's default buttons
        chooser.setControlButtonsAreShown(false);

        // Create the dialog panel and add the file chooser to it
        JPanel dialogPanel = new JPanel();
        dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
        dialogPanel.add(chooser);

        // Check if a lower panel is provided
        if (lowerPanel != null)
        {
            // Add the lower panel to the dialog panel
            dialogPanel.add(lowerPanel);
        }

        // Open the file chooser dialog and wait for a button click
        if (createDialog(parent,
                         dialogPanel,
                         null,
                         null,
                         dialogTitle,
                         optionType,
                         null,
                         true,
                         true) == OK_BUTTON)
        {
            // Extract the file name(s) from the file chooser name field. Spaces may appear in the
            // names only if the name is within double quotes. The names are split on spaces
            // outside of quotes, at every second quote, or at commas. First any leading or
            // trailing white space characters are removed
            String names = nameField.getText().trim();

            // Check if only a folder is allowed to be chosen
            if (folderOnly)
            {
                // Surround the names with quotes, if not already, in order to preserve any spaces
                // in the path
                names = names.replaceFirst("^\"?([^\"]*)\"?$", "\"$1\"");
            }

            // Create a flag to keep track of whether or not the character being checked is inside
            // or outside a pair of quotes
            boolean isQuoted = false;

            // Step through the string containing the file name(s)
            for (int index = 0; index < names.length(); index++)
            {
                // Get the current character
                char c = names.charAt(index);

                // Check if the character is a quote
                if (c == '"')
                {
                    // Invert the quote on/off flag
                    isQuoted = !isQuoted;
                }

                // Check if the character is a space outside of a pair of quotes, or is the second
                // quote of a pair of quotes
                if ((c == ' ' || c == '"') && !isQuoted)
                {
                    // Replace the character with a comma. Occurrences of double commas can result
                    // from this operation; these are accounted for later
                    names = names.substring(0, index) + "," + names.substring(index + 1);
                }
            }

            // Replace every instance of back-to-back commas that may have resulted in the
            // replacement steps above with a single comma, remove every remaining quote, then
            // split the string at the commas, which now delineate the separate file names
            String[] fileNames = names.replaceAll(",,+", ",").replaceAll("\"+", "").split(",");

            // Check if the file name text field isn't empty
            if (!fileNames[0].isEmpty())
            {
                // Create a file array
                file = new FileEnvVar[fileNames.length];

                // Step through the file names/paths
                for (int i = 0; i < fileNames.length; i++)
                {
                    // Check if a file type was specified, only one extension is being sought, and
                    // the file name has no extension. If multiple extension types are available
                    // then no extension is added to the file name
                    if (fileExtensions != null
                        && fileExtensions.length == 1
                        && !fileNames[i].contains("."))
                    {
                        // Add the extension to the file name. If more than one extension is
                        // available for the extension type then use the first one
                        fileNames[i] = fileNames[i]
                                       + "."
                                       + ((ExtensionFilter) chooser.getFileFilter()).getExtension();
                    }

                    // Create a file handle for each file name or the path name. If this is not a
                    // folder, prepend the file path to the name
                    file[i] = new FileEnvVar(FileEnvVar.restoreEnvVars((folderOnly
                                                                                   ? ""
                                                                                   : chooser.getCurrentDirectory().getAbsolutePath()
                                                                                     + File.separator)
                                                                       + fileNames[i],
                                                                       envVars));
                }
            }
        }
        // The Cancel button was pressed
        else
        {
            // Set the return file reference to null
            file = null;
        }

        return file;
    }

    /**********************************************************************************************
     * Locate and return the file name input field in the file chooser dialog
     *
     * @param cont
     *            container containing the JFileChooser
     *
     * @return JFileChooser file name input field
     *********************************************************************************************/
    private JTextField getFileChooserTextField(Container cont)
    {
        JTextField tf = null;

        // Step through the file chooser components. The components are checked in reverse to
        // account for those look & feels (i.e., Motif) that have a text field for the path in
        // addition to one for the file name. By looking in reverse the field with the file name is
        // found first
        for (int i = cont.getComponentCount() - 1; i >= 0; i--)
        {
            // Get the file chooser component
            Component comp = cont.getComponent(i);

            // Check if the component is a text field
            if (comp instanceof JTextField && comp != null)
            {
                // Store the text field and exit the loop
                tf = (JTextField) comp;
                break;
            }
            // Component isn't a text field; check if the text field hasn't been located (to
            // prevent continued searching after the text field is found) and if this is another
            // container,
            else if (comp instanceof Container)
            {
                // Drill down into this container to look for the buttons
                tf = getFileChooserTextField((Container) comp);

                // Check if the text field was located
                if (tf != null)
                {
                    // Exit the loop
                    break;
                }
            }
        }

        return tf;
    }

    /**********************************************************************************************
     * Create a color choice and preview panel
     *
     * @param chooser
     *            reference to a JColorChooser
     *
     * @param initialColor
     *            color initially selected when the dialog appears
     *
     * @return Reference to a JPanel containing the color choice and preview panels
     *********************************************************************************************/
    protected JPanel getColorChoicePanel(final JColorChooser chooser, Color initialColor)
    {
        // Create a panel to hold the color preview text and color boxes
        JPanel previewPanel = new JPanel(new BorderLayout());
        JLabel previewLabel = new JLabel("Preview", SwingConstants.CENTER);
        previewLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Calculate the size of the color preview boxes based on the width of the preview label
        // text
        int previewWidth = (int) previewLabel.getFontMetrics(ModifiableFontInfo.LABEL_BOLD.getFont()).getStringBounds(previewLabel.getText(),
                                                                                                                      previewLabel.getGraphics())
                                             .getWidth()
                           / 2 - 1;
        Dimension previewSize = new Dimension(previewWidth, previewWidth);

        // Create a panel to show the original color
        JPanel initialPanel = new JPanel();
        initialPanel.setOpaque(true);
        initialPanel.setBackground(initialColor);
        initialPanel.setPreferredSize(previewSize);

        // Create a panel to show the new color
        final JPanel newPanel = new JPanel();
        newPanel.setOpaque(true);
        newPanel.setBackground(initialColor);
        newPanel.setPreferredSize(previewSize);

        // Add the preview text and color boxes to the preview panel
        previewPanel.add(previewLabel, BorderLayout.PAGE_START);
        previewPanel.add(initialPanel, BorderLayout.LINE_START);
        previewPanel.add(newPanel, BorderLayout.LINE_END);

        // Add a listener for changes to the preview panel in order to capture color change events.
        // These are generated when the user changes the color in the color chooser panel
        previewPanel.addPropertyChangeListener("foreground", new PropertyChangeListener()
        {
            /**************************************************************************************
             * Handle foreground color change events
             *************************************************************************************/
            @Override
            public void propertyChange(PropertyChangeEvent pce)
            {
                // Update the preview color to the current choice
                newPanel.setBackground(chooser.getColor());
            }
        });

        // Set the color chooser's color preview panel
        chooser.setPreviewPanel(previewPanel);

        // Create the panel for holding the color chooser(s) and the preview panel
        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,
                                                      20,
                                                      5));

        // Get the color chooser panels available to the current look & feel
        AbstractColorChooserPanel[] chooserPanel = chooser.getChooserPanels();

        // Check if there's only a single color chooser panel
        if (chooserPanel.length == 1)
        {
            // Add the color chooser panel to the dialog panel
            colorPanel.add(chooserPanel[0]);
        }
        // There's more than one color chooser panel
        else
        {
            // Create a tabbed pane
            JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.TOP);
            tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            colorPanel.add(tabbedPane);

            // Create a border for the tabbed panes
            Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

            // Step through the available color chooser panels
            for (int index = 0; index < chooserPanel.length; index++)
            {
                // Create a panel to hold the chooser so that it's centered in the tab panel
                JPanel pnl = new JPanel(new GridBagLayout());
                pnl.setBorder(border);
                pnl.add(chooserPanel[index]);

                // Set the name, component, and tool tip text (if extant) of the tab for each color
                // chooser panel
                tabbedPane.addTab(chooserPanel[index].getDisplayName(),
                                  null,
                                  pnl,
                                  chooserPanel[index].getToolTipText());
            }

            // Add the color preview panel
            colorPanel.add(previewPanel);
        }

        return colorPanel;
    }

    /**********************************************************************************************
     * Placeholder for method to verify the the dialog box selection(s) prior to closing
     *
     * @return true
     *********************************************************************************************/
    protected boolean verifySelection()
    {
        return true;
    }

    /**********************************************************************************************
     * Add radio buttons, representing the available selections from a supplied array, in a grid
     * pattern to the dialog panel. Select and gray out the currently selected item if supplied
     *
     * @param rbtnSelected
     *            name of the initially selected item. null if no item is initially chosen
     *
     * @param canReselect
     *            true if the initially selected item can be reselected; false to disable
     *            reselection of the initial item
     *
     * @param itemInformation
     *            array of items from which to select. The first column is the item name and the
     *            second column is the item description
     *
     * @param disabledItems
     *            list of indices for items that appear in the list but cannot be selected; null or
     *            empty list if no items are disabled
     *
     * @param rbtnText
     *            text to display above the radio button panel
     *
     * @param isDescItalic
     *            true to display the description text in the italic label font; false to use the
     *            plain label font
     *
     * @param dialogPanel
     *            dialog panel on which to place the radio buttons
     *
     * @param dialogGbc
     *            dialog panel GridBagLayout layout constraints
     *
     * @return true if there is one or more items to display; the dialog is populated with the
     *         array of radio buttons. false if there are no items to display; the dialog panel is
     *         unchanged
     *********************************************************************************************/
    protected boolean addRadioButtons(String rbtnSelected,
                                      boolean canReselect,
                                      String[][] itemInformation,
                                      List<Integer> disabledItems,
                                      String rbtnText,
                                      boolean isDescItalic,
                                      JPanel dialogPanel,
                                      GridBagConstraints dialogGbc)
    {
        boolean rbtnsAdded = false;

        // Store the currently selected item. Use null if no preselected item list is provided
        radioButtonSelected.add(rbtnSelected);

        // Check if any items exist
        if (itemInformation.length != 0)
        {
            int maxRBtnWidth = 0;
            int maxDescWidth = 0;
            int numWrappedRows = 0;
            MultilineLabel[] descriptionFld = null;
            boolean isDescriptions = false;

            // Set up storage for the radio buttons
            JRadioButton[] radioButton = new JRadioButton[itemInformation.length];
            ButtonGroup rbtnGroup = new ButtonGroup();

            // Create a copy of the layout constraints and update them
            GridBagConstraints dlgGbc = (GridBagConstraints) dialogGbc.clone();
            dlgGbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            dlgGbc.insets.bottom = 0;
            dlgGbc.gridwidth = GridBagConstraints.REMAINDER;
            dlgGbc.weighty = 0.0;
            dlgGbc.fill = GridBagConstraints.BOTH;

            // Create the label for the radio button panel
            JLabel rbtnLabel = new JLabel(rbtnText);
            rbtnLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            dialogPanel.add(rbtnLabel, dlgGbc);

            // Set the layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0,
                                                            0,
                                                            1,
                                                            1,
                                                            0.0,
                                                            0.0,
                                                            GridBagConstraints.LINE_START,
                                                            GridBagConstraints.BOTH,
                                                            new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                            0,
                                                            0);

            // Create an empty border for use around the dialog components
            Border emptyBorder = BorderFactory.createEmptyBorder();

            // Create panels to contain the radio buttons within a scroll pane
            JPanel rbtnPnl = new JPanel(new GridBagLayout());
            rbtnPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            JPanel rbtnGridPnl = new JPanel(new GridBagLayout());
            rbtnGridPnl.setBorder(emptyBorder);

            // Create a listener for radio button selection changes
            ActionListener listener = new ActionListener()
            {
                final int index = radioButtonSelected.size() - 1;

                /**********************************************************************************
                 * Select the item based on the radio button selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Get the text associated with the selected radio button
                    String buttonText = ((JRadioButton) ae.getSource()).getText();

                    // Check if the selected item differs from the one currently selected
                    if (radioButtonSelected.get(index) == null
                        || !radioButtonSelected.get(index).equals(buttonText))
                    {
                        // Update the currently selected item
                        radioButtonSelected.set(index, buttonText);

                        // Issue an event to any listeners that the radio button selection changed
                        firePropertyChange(RADIO_BUTTON_CHANGE_EVENT,
                                           "",
                                           radioButtonSelected.get(index));
                    }
                }
            };

            // Step through each item
            for (int index = 0; index < itemInformation.length; index++)
            {
                // Check if a description is provided
                if (itemInformation[index][1] != null)
                {
                    // Create storage for the description labels, set the flag indicating
                    // descriptions are provided, and stop searching
                    descriptionFld = new MultilineLabel[itemInformation.length];
                    isDescriptions = true;
                    break;
                }
            }

            // Calculate the number of columns in the radio button grid based on if there are
            // descriptions and the number of items to display, up to a maximum of x columns
            // (default is 5)
            int gridWidth = isDescriptions
                                           ? 1
                                           : (int) Math.min(Math.sqrt(itemInformation.length),
                                                            ModifiableSizeInfo.MAX_GRID_WIDTH.getSize());

            // Create radio buttons for each available item
            for (int index = 0; index < itemInformation.length; index++)
            {
                radioButton[index] = new JRadioButton(itemInformation[index][0], false);
                radioButton[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                radioButton[index].setBorder(emptyBorder);
                radioButton[index].addActionListener(listener);

                // Enable the radio button if the item isn't in the disabled list
                radioButton[index].setEnabled(disabledItems == null
                                              || !disabledItems.contains(index));

                // Add the radio buttons to a group so that only one is active at a time
                rbtnGroup.add(radioButton[index]);

                // Arrange the radio buttons in a grid
                if (index % gridWidth == 0)
                {
                    gbc.gridx = 0;
                    gbc.gridy++;
                }
                else
                {
                    gbc.gridx++;
                }

                // Add the radio button to the dialog panel. The inner panel allows the button to
                // align with the top of the description
                JPanel innerPnl = new JPanel(new BorderLayout());
                innerPnl.setBorder(emptyBorder);
                innerPnl.add(radioButton[index], BorderLayout.PAGE_START);
                rbtnGridPnl.add(innerPnl, gbc);

                // Check if a description is provided
                if (itemInformation[index][1] != null && itemInformation[index][1] != null)
                {
                    // Add the item description. The initial preferred size is updated to account
                    // for wrapping of the label text
                    gbc.weightx = 1.0;
                    gbc.weighty = 1.0;
                    gbc.gridx++;
                    descriptionFld[index] = new MultilineLabel(itemInformation[index][1]);
                    descriptionFld[index].setBackground(UIManager.getColor("Label.background"));
                    descriptionFld[index].setFont(isDescItalic
                                                               ? ModifiableFontInfo.LABEL_ITALIC.getFont()
                                                               : ModifiableFontInfo.LABEL_PLAIN.getFont());
                    descriptionFld[index].setBorder(emptyBorder);
                    rbtnGridPnl.add(descriptionFld[index], gbc);
                    gbc.weightx = 0.0;
                    gbc.weighty = 0.0;
                }

                // Check if the item name matches the preselected item
                if (itemInformation[index][0].equals(rbtnSelected))
                {
                    // Check if reselection of the radio button is prohibited
                    if (!canReselect)
                    {
                        // Disable the radio button
                        radioButton[index].setEnabled(false);
                    }

                    // Select the radio button
                    radioButton[index].setSelected(true);
                }

                // Store the widest radio button
                maxRBtnWidth = Math.max(maxRBtnWidth, radioButton[index].getPreferredSize().width);
            }

            // Check if descriptions are provided
            if (isDescriptions)
            {
                // Calculate the maximum pixel width allowed for the description text by
                // subtracting the maximum radio button width from the radio button grid panel
                // width
                maxDescWidth = rbtnGridPnl.getPreferredSize().width
                               - maxRBtnWidth
                               - (ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3);

                // Step through each item
                for (int index = 0; index < itemInformation.length; index++)
                {
                    // Check if a description exists for this item
                    if (descriptionFld[index] != null)
                    {
                        // Get the number of rows required to displayed the description text (in
                        // case it wraps or has line-feeds) and add it to the total row count.
                        // Subtract 1 since the initial row counter is set to the total number of
                        // items
                        numWrappedRows += descriptionFld[index].getNumDisplayRows(maxDescWidth) - 1;
                    }
                }
            }

            // Create an outer panel to contain the radio button panel, then add this outer panel
            // to a scroll pane (in case there are a large number to choose from). The use of the
            // outer panel allows the description column to wrap properly as the dialog is resized
            // in width (primarily when the width gets smaller). Set the scroll speed of the scroll
            // pane based on the row (i.e., radio button) height
            final JPanel rbtnOuterPnl = new JPanel(new BorderLayout());
            rbtnOuterPnl.add(rbtnGridPnl, BorderLayout.PAGE_START);
            JScrollPane scrollPane = new JScrollPane(rbtnOuterPnl);
            scrollPane.setBorder(emptyBorder);
            scrollPane.setViewportBorder(emptyBorder);
            scrollPane.getVerticalScrollBar().setUnitIncrement(radioButton[0].getPreferredSize().height / 2
                                                               + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            // Calculate the height of the panel required to display all of the radio button items
            int calcRowHeight = (itemInformation.length + gridWidth - 1) / gridWidth
                                * (radioButton[0].getPreferredSize().height
                                   + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                     * 2)
                                + radioButton[0].getPreferredSize().height
                                  * numWrappedRows;

            // Calculate the maximum desired height of the panel containing the radio buttons (= #
            // of rows * row height)
            int maxRowHeight = ModifiableSizeInfo.INIT_VIEWABLE_COMPONENT_ROWS.getSize()
                               * (radioButton[0].getPreferredSize().height
                                  + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                    * 2);

            // Set the size of the scrollable list based of if the scrollable list height exceeds
            // the maximum desirable height (the vertical scroll bar is displayed)
            scrollPane.setPreferredSize(new Dimension(rbtnGridPnl.getPreferredSize().width
                                                      + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()
                                                        * 2
                                                      + (isDescriptions
                                                                        ? maxDescWidth
                                                                          + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()
                                                                        : 0),
                                                      Math.min(calcRowHeight, maxRowHeight)));

            // Add a listener for changes in the scroll pane's size
            scrollPane.addComponentListener(new ComponentAdapter()
            {
                /**********************************************************************************
                 * Handle a change in the scroll pane's size
                 *********************************************************************************/
                @Override
                public void componentResized(ComponentEvent ce)
                {
                    // Update the size of the panel containing the radio buttons and descriptions.
                    // This causes any changes in width due to the descriptions wrapping to be
                    // incorporated
                    rbtnOuterPnl.setPreferredSize(new Dimension(rbtnOuterPnl.getPreferredSize().width,
                                                                rbtnOuterPnl.getMinimumSize().height));
                }
            });

            // Add the scrollable panel containing the radio buttons to the outer radio button
            // panel in order for the border to appear
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            rbtnPnl.add(scrollPane, gbc);

            // Add the radio button panel to the dialog
            dlgGbc.weighty = 1.0;
            dlgGbc.gridy++;
            dialogPanel.add(rbtnPnl, dlgGbc);

            // Adjust the input layout constraints grid y-coordinate so that subsequent components
            // using these constraints are placed correctly
            dialogGbc.gridy = dlgGbc.gridy;

            rbtnsAdded = true;
        }

        return rbtnsAdded;
    }

    /**********************************************************************************************
     * Add check boxes, representing the available selections from a supplied array, in a grid
     * pattern to the dialog panel. Select and gray out the currently selected item if supplied
     *
     * @param cboxSelected
     *            name of the initially selected item. Null if no item is initially chosen. A
     *            protected item cannot be selected
     *
     * @param itemInformation
     *            array of items from which to select. The first column is the item name and the
     *            second column is the item description
     *
     * @param disabledItems
     *            list of indices for items that appear in the list but cannot be selected; null or
     *            empty list if no items are disabled
     *
     * @param cboxText
     *            text to display above the check box panel
     *
     * @param isDescItalic
     *            true to display the description text in the italic label font; false to use the
     *            plain label font
     *
     * @param dialogPanel
     *            dialog panel on which to place the check boxes
     *
     * @return true if there is one or more items to display; the dialog is populated with the
     *         array of check boxes. false if there are no items to display; the dialog panel is
     *         unchanged
     *********************************************************************************************/
    protected boolean addCheckBoxes(String cboxSelected,
                                    String[][] itemInformation,
                                    List<Integer> disabledItems,
                                    String cboxText,
                                    boolean isDescItalic,
                                    JPanel dialogPanel)
    {
        boolean cboxesAdded = false;

        // Check if any items exist
        if (itemInformation.length != 0)
        {
            int maxCBoxWidth = 0;
            int maxDescWidth = 0;
            int numWrappedRows = 0;
            MultilineLabel[] descriptionFld = null;
            boolean isDescriptions = false;

            // Create a copy of the layout constraints and update them
            GridBagConstraints dlgGbc = new GridBagConstraints(0,
                                                               0,
                                                               GridBagConstraints.REMAINDER,
                                                               1,
                                                               1.0,
                                                               0.0,
                                                               GridBagConstraints.LINE_START,
                                                               GridBagConstraints.BOTH,
                                                               new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                          ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                          0,
                                                                          ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                               0,
                                                               0);

            // Create the label for the check box panel
            JLabel cboxLabel = new JLabel(cboxText);
            cboxLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            dialogPanel.add(cboxLabel, dlgGbc);

            // Set the layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0,
                                                            0,
                                                            1,
                                                            1,
                                                            0.0,
                                                            0.0,
                                                            GridBagConstraints.LINE_START,
                                                            GridBagConstraints.BOTH,
                                                            new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                            0,
                                                            0);

            // Create an empty border for use around the dialog components
            Border emptyBorder = BorderFactory.createEmptyBorder();

            // Create panels to contain the check boxes within a scroll pane
            JPanel cboxPanel = new JPanel(new GridBagLayout());
            cboxPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            JPanel cboxGridPnl = new JPanel(new GridBagLayout());
            cboxGridPnl.setBorder(emptyBorder);

            // Create a listener for radio button selection changes
            ActionListener listener = new ActionListener()
            {
                /**********************************************************************************
                 * Select the item based on the check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Issue an event to any listeners that the check box selection changed,
                    // sending the text associated with the selected check box
                    firePropertyChange(CHECK_BOX_CHANGE_EVENT,
                                       "",
                                       ((JCheckBox) ae.getSource()).getText());
                }
            };

            // Set up storage for the check boxes
            checkBox = new JCheckBox[itemInformation.length];

            // Step through each item
            for (int index = 0; index < itemInformation.length; index++)
            {
                // Check if a description is provided
                if (itemInformation[index][1] != null)
                {
                    // Create storage for the description labels, set the flag indicating
                    // descriptions are provided, and stop searching
                    descriptionFld = new MultilineLabel[itemInformation.length];
                    isDescriptions = true;
                    break;
                }
            }

            // Calculate the number of columns in the check box grid based on if there are
            // descriptions and the number of items to display, up to a maximum of x columns
            // (default is 5)
            int gridWidth = isDescriptions
                                           ? 1
                                           : (int) Math.min(Math.sqrt(itemInformation.length),
                                                            ModifiableSizeInfo.MAX_GRID_WIDTH.getSize());

            // Create check boxes for each available item
            for (int index = 0; index < itemInformation.length; index++)
            {
                checkBox[index] = new JCheckBox(itemInformation[index][0], false);
                checkBox[index].setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                checkBox[index].setBorder(emptyBorder);
                checkBox[index].addActionListener(listener);

                // Enable the check box if the item isn't in the disabled list
                checkBox[index].setEnabled(disabledItems == null
                                           || !disabledItems.contains(index));

                // Arrange the check boxes in a grid
                if (index % gridWidth == 0)
                {
                    gbc.gridx = 0;
                    gbc.gridy++;
                }
                else
                {
                    gbc.gridx++;
                }

                // Add the radio button to the dialog panel. The inner panel allows the box to
                // align with the top of the description
                JPanel innerPnl = new JPanel(new BorderLayout());
                innerPnl.setBorder(emptyBorder);
                innerPnl.add(checkBox[index], BorderLayout.PAGE_START);
                cboxGridPnl.add(innerPnl, gbc);

                // Check if a description is provided
                if (itemInformation[index].length != 1 && itemInformation[index][1] != null)
                {
                    // Add the item description. The initial preferred size is updated to account
                    // for wrapping of the label text
                    gbc.weightx = 1.0;
                    gbc.weighty = 1.0;
                    gbc.gridx++;
                    descriptionFld[index] = new MultilineLabel(itemInformation[index][1]);
                    descriptionFld[index].setBackground(UIManager.getColor("Label.background"));
                    descriptionFld[index].setFont(isDescItalic
                                                               ? ModifiableFontInfo.LABEL_ITALIC.getFont()
                                                               : ModifiableFontInfo.LABEL_PLAIN.getFont());
                    descriptionFld[index].setBorder(emptyBorder);
                    cboxGridPnl.add(descriptionFld[index], gbc);
                    gbc.weightx = 0.0;
                    gbc.weighty = 0.0;
                }

                // Check if the item name matches the preselected item
                if (checkBox[index].isEnabled() && itemInformation[index][0].equals(cboxSelected))
                {
                    // Select the check box
                    checkBox[index].setSelected(true);
                }

                // Store the widest radio button
                maxCBoxWidth = Math.max(maxCBoxWidth, checkBox[index].getPreferredSize().width);
            }

            // Check if descriptions are provided
            if (isDescriptions)
            {
                // Calculate the maximum pixel width allowed for the description text by
                // subtracting the maximum radio button width from the radio button grid panel
                // width
                maxDescWidth = cboxGridPnl.getPreferredSize().width
                               - maxCBoxWidth
                               - (ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3);

                // Step through each item
                for (int index = 0; index < itemInformation.length; index++)
                {
                    // Check if a description exists for this item
                    if (descriptionFld[index] != null)
                    {
                        // Get the number of rows required to displayed the description text (in
                        // case it wraps or has line-feeds) and add it to the total row count.
                        // Subtract 1 since the initial row counter is set to the total number of
                        // items
                        numWrappedRows += descriptionFld[index].getNumDisplayRows(maxDescWidth) - 1;
                    }
                }
            }

            // Create an outer panel to contain the check box panel, then add this outer panel
            // to a scroll pane (in case there are a large number to choose from). The use of the
            // outer panel allows the description column to wrap properly as the dialog is resized
            // in width (primarily when the width gets smaller). Set the scroll speed of the scroll
            // pane based on the row (i.e., check box) height
            final JPanel cboxOuterPnl = new JPanel(new BorderLayout());
            cboxOuterPnl.add(cboxGridPnl, BorderLayout.PAGE_START);
            JScrollPane scrollPane = new JScrollPane(cboxOuterPnl);
            scrollPane.setBorder(emptyBorder);
            scrollPane.setViewportBorder(emptyBorder);
            scrollPane.getVerticalScrollBar().setUnitIncrement(checkBox[0].getPreferredSize().height / 2
                                                               + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            // Calculate the height of the panel required to display all of the check box items
            int calcRowHeight = (itemInformation.length + gridWidth - 1) / gridWidth
                                * (checkBox[0].getPreferredSize().height
                                   + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                     * 2)
                                + checkBox[0].getPreferredSize().height
                                  * numWrappedRows;

            // Calculate the maximum desired height of the panel containing the check boxes (= # of
            // rows * row height)
            int maxRowHeight = ModifiableSizeInfo.INIT_VIEWABLE_COMPONENT_ROWS.getSize()
                               * (checkBox[0].getPreferredSize().height
                                  + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                    * 2);

            // Set the size of the scrollable list based of if the scrollable list height exceeds
            // the maximum desirable height (the vertical scroll bar is displayed)
            scrollPane.setPreferredSize(new Dimension(cboxGridPnl.getPreferredSize().width
                                                      + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()
                                                        * 2
                                                      + (isDescriptions
                                                                        ? maxDescWidth
                                                                          + ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()
                                                                        : 0),
                                                      Math.min(calcRowHeight, maxRowHeight)));

            // Add a listener for changes in the scroll pane's size
            scrollPane.addComponentListener(new ComponentAdapter()
            {
                /**********************************************************************************
                 * Handle a change in the scroll pane's size
                 *********************************************************************************/
                @Override
                public void componentResized(ComponentEvent ce)
                {
                    // Update the size of the panel containing the check boxes and descriptions.
                    // This causes any changes in width due to the descriptions wrapping to be
                    // incorporated
                    cboxOuterPnl.setPreferredSize(new Dimension(cboxOuterPnl.getPreferredSize().width,
                                                                cboxOuterPnl.getMinimumSize().height));
                }
            });

            // Add the scrollable panel containing the check boxes to the outer check box panel in
            // order for the border to appear
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            cboxPanel.add(scrollPane, gbc);

            // Add the check box panel to the dialog
            dlgGbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            dlgGbc.weighty = 1.0;
            dlgGbc.gridy++;
            dialogPanel.add(cboxPanel, dlgGbc);

            // Set the flag to indicate that check boxes were added
            cboxesAdded = true;
        }

        return cboxesAdded;
    }

    /**********************************************************************************************
     * Extract the dialog's button(s) from the supplied panel, then find the widest button
     * calculated from the button's text and icon. Set the width of the panel containing the
     * buttons based on the widest button
     *********************************************************************************************/
    protected void setButtonWidth()
    {
        buttonHandler.setButtonWidth();
    }

    /**********************************************************************************************
     * Set the number of rows occupied by the dialog's buttons
     *
     * @param rows
     *            number of dialog button rows
     *********************************************************************************************/
    protected void setButtonRows(int rows)
    {
        buttonHandler.setButtonRows(rows);
    }

    /**********************************************************************************************
     * Enable/disable the button panel buttons. Override to include other dialog controls
     *
     * @param enable
     *            true to enable the buttons; false to disable
     *********************************************************************************************/
    protected void setControlsEnabled(boolean enable)
    {
        buttonHandler.setButtonsEnabled(enable);
    }

    /**********************************************************************************************
     * Create the dialog. If no buttons are provided (lower panel) then create the buttons and
     * button listeners needed based on the dialog type
     *
     * @param parent
     *            window over which to center the dialog; set to null to redirect message dialog
     *            text to the standard output stream (usually the command line)
     *
     * @param upperObject
     *            object containing the dialog components or message
     *
     * @param buttonPnl
     *            panel containing the dialog buttons
     *
     * @param defaultBtn
     *            reference to the JButton that is actuated if the Enter key is pressed; null to
     *            have no default button
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION, READ_OPTION, PRINT_OPTION,
     *            CLOSE_OPTION, OK_OPTION, or OK_CANCEL_OPTION
     *
     * @param icon
     *            icon to display to the left of the text message
     *
     * @param resizable
     *            true to allow the dialog to be resized
     *
     * @param modal
     *            false to allow the other application windows to still be operated while the
     *            dialog is open
     *
     * @return Selected button type
     *********************************************************************************************/
    @SuppressWarnings("resource") // Can't close the scanner since it would also close System.in
    protected int createDialog(Component parent,
                               Object upperObject,
                               JPanel buttonPnl,
                               JButton defaultBtn,
                               String title,
                               DialogOption optionType,
                               Icon icon,
                               boolean resizable,
                               boolean modal)
    {
        // Check if the parent component doesn't exist. This is the case when the GUI is hidden
        if (parent == null)
        {
            // Check if the component is a string; i.e., this is a plain, information, question,
            // warning, or error message dialog. Non-message dialogs are ignored
            if (upperObject instanceof String)
            {
                // Build the message to display
                String message = "CCDD : "
                                 + title
                                 + " : "
                                 + CcddUtilities.removeHTMLTags(upperObject.toString());

                // Check if this is a warning or error message
                if (icon.equals(getIcon("OptionPane.warningIcon", WARNING_ICON))
                    || icon.equals(getIcon("OptionPane.errorIcon", ERROR_ICON)))
                {
                    // Output the message the standard error stream
                    System.err.println(message);
                }
                // Not an error or warning message
                else
                {
                    // Output the message to the standard output stream
                    System.out.println("\n" + message);

                    // Check if this is a question message
                    if (icon.equals(getIcon("OptionPane.questionIcon", QUESTION_ICON)))
                    {
                        message = "";

                        // Step through each button in the panel
                        for (int index = 0; index < buttonPnl.getComponentCount(); index++)
                        {
                            // Add the button index and text to the output string
                            message += " ("
                                       + (index + 1)
                                       + ") "
                                       + ((JButton) buttonPnl.getComponent(index)).getText()
                                       + "\n";
                        }

                        message += "  Enter selection: ";

                        // Create a scanner to obtain user input from the command line
                        Scanner scanner = new Scanner(System.in);

                        do
                        {
                            try
                            {
                                // Get the user's input
                                System.out.print(message);
                                buttonSelected = scanner.nextInt();

                                // Check if the value falls within the selection range
                                if (buttonSelected < 1
                                    || buttonSelected > buttonPnl.getComponentCount())
                                {
                                    // Set the input value to indicate an invalid input
                                    buttonSelected = 0;
                                }
                            }
                            catch (Exception e)
                            {
                                // A non-integer value was entered; set the input value to indicate
                                // an invalid input
                                buttonSelected = 0;
                            }

                            // Check if the input is invalid (non-integer or out of range)
                            if (buttonSelected == 0)
                            {
                                // Inform the user that the input is invalid
                                scanner.nextLine();
                                System.err.println("Invalid selection, re-enter");
                                System.err.flush();
                            }
                        } while (buttonSelected == 0);
                        // Repeat the input process until a valid value is entered

                        // Adjust the button selection index
                        buttonSelected--;
                    }
                }
            }
        }
        // A parent component exists
        else
        {
            // Create a component to point to the upper contents of the dialog
            JComponent upperComponent;

            // Check if a text message was provided instead of an upper panel
            if (upperObject instanceof String)
            {
                // Create a panel to hold the text message
                JPanel textPnl = new JPanel();
                textPnl.setBorder(BorderFactory.createEmptyBorder());

                // Create a panel to hold the icon. Add some padding between the icon and the text
                // message
                JPanel iconPanel = new JPanel();
                iconPanel.setBorder(BorderFactory.createEmptyBorder(0,
                                                                    0,
                                                                    0,
                                                                    ModifiableSpacingInfo.DIALOG_ICON_PAD.getSpacing()));
                iconPanel.add(new JLabel(icon));

                // Create a label to hold the text message. Format the message to constrain the
                // character width to a specified maximum
                JLabel textLbl = new JLabel(CcddUtilities.wrapText(upperObject.toString(),
                                                                   ModifiableSizeInfo.MAX_DIALOG_LINE_LENGTH.getSize()),
                                            SwingConstants.LEFT);
                textLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());

                // Place the icon and text message into the upper panel
                textPnl.add(iconPanel);
                textPnl.add(textLbl);

                // Point to the panel containing the icon and message
                upperComponent = textPnl;
            }
            // Not a text message dialog
            else
            {
                // Point to the dialog components provided
                upperComponent = (JComponent) upperObject;
            }

            // Set up button panel related items and combine the button and upper panels
            buttonHandler.assembleWindowComponents(buttonPnl,
                                                   defaultBtn,
                                                   upperComponent,
                                                   optionType,
                                                   getContentPane(),
                                                   getRootPane());

            // Add a listener for dialog focus gain and lost events
            addWindowFocusListener(new WindowFocusListener()
            {
                /**********************************************************************************
                 * Handle a dialog focus gained event
                 *********************************************************************************/
                @Override
                public void windowGainedFocus(WindowEvent we)
                {
                    // Set the default button to the last button pressed when the dialog (re)gains
                    // the focus. This enables the special highlighting associated with the default
                    // button
                    getRootPane().setDefaultButton(buttonHandler.getLastButtonPressed());
                }

                /**********************************************************************************
                 * Handle a dialog focus lost event
                 *********************************************************************************/
                @Override
                public void windowLostFocus(WindowEvent we)
                {
                    // Set so that there is no default button while the dialog doesn't have the
                    // focus. This removes the special highlighting associated with the default
                    // button
                    getRootPane().setDefaultButton(null);
                }
            });

            // Check if the title is provided
            if (title != null)
            {
                // Set the dialog's title
                setTitle(title);
            }

            // Set if operation of the main window is allowed while this dialog is open
            setModalityType(modal
                                  ? JDialog.ModalityType.APPLICATION_MODAL
                                  : JDialog.ModalityType.MODELESS);

            // Set the default close operation so that the dialog frame's close button doesn't
            // automatically exit the dialog. Instead, if this close button is pressed a button
            // press event is sent to the last button on the dialog's button panel
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

            // Add a listener for dialog window events
            addWindowListener(new WindowAdapter()
            {
                /**********************************************************************************
                 * Handle the dialog frame close button press event
                 *********************************************************************************/
                @Override
                public void windowClosing(WindowEvent we)
                {
                    // Perform action needed when the window's frame close button is pressed
                    windowCloseButtonAction();
                }

                /**********************************************************************************
                 * Handle the dialog window close event
                 *********************************************************************************/
                @Override
                public void windowClosed(WindowEvent we)
                {
                    // Perform action needed when the dialog window is closed
                    windowClosedAction();
                }
            });

            // Set whether or not this dialog can be resized
            setResizable(resizable);

            // Size the dialog to fit its components
            pack();

            // Set the preferred size so that setRelativeTo call positions other dialogs relative
            // to this one's position when it's no longer visible
            setPreferredSize(getPreferredSize());

            // Check if the dialog is resizable
            if (resizable)
            {
                // Set the minimum width to the larger of (1) the default dialog width and (2) the
                // packed dialog width (the pixel width of a vertical scroll bar is added if the
                // dialog contains a table so that the horizontal scroll bar isn't automatically
                // displayed if the table contents exceeds the initial height, and a pixel is added
                // to this width to prevent the dialog from being resized smaller than its original
                // width). Set the minimum height to the packed height of the dialog components
                setMinimumSize(new Dimension(Math.max(getMinimumWidth(),
                                                      getWidth() + (isContainsComponent(this,
                                                                                        JTable.class)
                                                                                                      ? LAF_SCROLL_BAR_WIDTH
                                                                                                      : 0)
                                                                         + 1),
                                             getHeight()));
            }

            // Position the dialog frame centered on the parent
            setLocationRelativeTo(parent);

            // Check if a component is selected to initially have the focus (otherwise default to
            // the first component)
            if (initialFocusComponent != null)
            {
                // Set the focus to the specified component
                initialFocusComponent.requestFocus();
            }

            // Display the dialog
            setVisible(true);
        }

        return buttonSelected;
    }

    /**********************************************************************************************
     * Check if the specified container or its children contains a component of the specified
     * class. This is a recursive method
     *
     * @param <E>
     *            generic type parameter
     *
     * @param container
     *            container object in which to search
     *
     * @param tgtClass
     *            target class for which to search
     *
     * @return true if the container contains an component of the specified class
     *********************************************************************************************/
    private <E> boolean isContainsComponent(Container container, Class<E> tgtClass)
    {
        boolean isFound = false;

        // Step through each component in the container
        for (Component comp : container.getComponents())
        {
            // Check if the component is an instance of the target class
            if (tgtClass.isInstance(comp))
            {
                // Set the flag to indicate a component of the target class is within the container
                isFound = true;
            }
            // Check if the component is a container
            else if (comp instanceof Container)
            {
                // Check the child container's components for the target class
                isFound = isContainsComponent((Container) comp, tgtClass);
            }

            // Check if a component of the target class has been found
            if (isFound)
            {
                // Stop searching
                break;
            }
        }

        return isFound;
    }

    /**********************************************************************************************
     * Override the setLocationRelativeTo() method so that the dialog can be positioned relative to
     * a parent that was present, but is no longer visible. Otherwise, the dialog would be centered
     * on the screen
     *
     * @param comp
     *            component in relation to which the dialog's location is determined
     *********************************************************************************************/
    @Override
    public void setLocationRelativeTo(Component comp)
    {
        // Check if the component to which the dialog is to be positioned relative to exists
        if (comp != null)
        {
            // Determine the new dialog's position relative to the component, adjusting the
            // location so that the dialog appears fully on the screen in which the component
            // resides
            Dimension newDlgSize = getSize();
            Dimension compSize = comp.getSize();
            Point adjLocation = adjustDialogLocationForScreen(new Rectangle(comp.getX()
                                                                            + ((compSize.width
                                                                                - newDlgSize.width)
                                                                               / 2),
                                                                            comp.getY()
                                                                                     + ((compSize.height
                                                                                         - newDlgSize.height)
                                                                                        / 2),
                                                                            newDlgSize.width,
                                                                            newDlgSize.height));

            // Position the new dialog
            setLocation(adjLocation.x, adjLocation.y);
        }
        // The component doesn't exist
        else
        {
            // Set the location using the default method
            super.setLocationRelativeTo(comp);
        }
    }

    /**********************************************************************************************
     * Set the component location so that it is completely inside the available screen(s), if
     * possible. Although the method makes an effort to place the component so that it is entirely
     * visible, it may end up partially outside the screen(s), either because it's larger than all
     * available screens or the screens are arranged badly
     *
     * @param comp
     *            rectangle representing a component's bounds for which to adjust the location
     *
     * @return Point giving the x, y coordinates of the component adjusted to fit within the screen
     *         in which the component resides
     *********************************************************************************************/
    protected static Point adjustDialogLocationForScreen(Rectangle comp)
    {
        // Set the default location to the component's current location
        Point location = new Point(comp.x, comp.y);

        // Get an array of the available screens bounds
        Rectangle[] availableRegions = getScreenRectangles();

        // Check if a screen is present
        if (availableRegions != null && availableRegions.length != 0)
        {
            boolean notFound = true;
            List<Rectangle> intersecting = new ArrayList<Rectangle>(3);

            // Step through each screen
            for (Rectangle region : availableRegions)
            {
                // Check if the component already fits within the screen
                if (region.contains(comp))
                {
                    // Set the flag to indicate the location is set
                    notFound = false;
                }
                // Check if the the component overlaps the screen bounds (i.e., is partially off
                // the screen)
                else if (region.intersects(comp))
                {
                    // Add the screen to those in which the component appears
                    intersecting.add(region);
                }
            }

            // Check if the location hasn't been finalized
            if (notFound)
            {
                // Base the position on the number of screens in which some portion of the
                // component appears
                switch (intersecting.size())
                {
                    case 0:
                        // The component appears entirely in a single screen. Adjust the
                        // component's location so that it is entirely within this screen
                        location = positionInsideRectangle(comp, availableRegions[0]);
                        break;

                    case 1:
                        // The component appears partially in a single screen. Adjust the
                        // component's location so that it is entirely within the first screen in
                        // which it partially appears
                        location = positionInsideRectangle(comp, intersecting.get(0));
                        break;

                    default:
                        // The component appears partially in more than one screens. Build an area
                        // containing all of the detected intersections and check if the bounds
                        // fall completely into the intersection area
                        Area area = new Area();

                        // Step through each screen in which the component appears
                        for (Rectangle region : intersecting)
                        {
                            // Get the bounds for this screen as a 2D rectangle object and add it
                            // to the total area
                            area.add(new Area(new Rectangle2D.Double(region.x,
                                                                     region.y,
                                                                     region.width,
                                                                     region.height)));
                        }

                        // Get the component bounds as a 2D rectangle object
                        Rectangle2D boundsRect = new Rectangle2D.Double(comp.x,
                                                                        comp.y,
                                                                        comp.width,
                                                                        comp.height);

                        // Check if this combined area doesn't contain the entire component
                        if (!area.contains(boundsRect))
                        {
                            // Since it doesn't fit, position is as best as possible within in the
                            // first screen in which it appears
                            location = positionInsideRectangle(comp,
                                                               intersecting.get(0));
                        }

                        break;
                }
            }
        }

        return location;
    }

    /**********************************************************************************************
     * Adjust the location of the specified rectangular boundary such that it is within the
     * specified rectangle representing the screen's bounds
     *
     * @param comp
     *            rectangle representing a component's bounds for which to adjust the location
     *
     * @param screen
     *            bounding rectangle, representing a screen, that the component is to remain within
     *
     * @return Point giving the x, y coordinates of the component rectangle adjusted to fit within
     *         the specified screen's bounds
     *********************************************************************************************/
    private static Point positionInsideRectangle(Rectangle comp,
                                                 Rectangle screen)
    {
        // Check if the new dialog is off the right of the screen
        if (comp.x + comp.width > screen.x + screen.width)
        {
            // Position the dialog against the right side of the screen
            comp.x = screen.x + screen.width - comp.width;
        }

        // Check if the new dialog is off the left of the screen
        if (comp.x < screen.x)
        {
            // Position the dialog against the left side of the screen
            comp.x = screen.x;
        }

        // Check if the new dialog is off the bottom of the screen
        if (comp.y + comp.height > screen.y + screen.height)
        {
            // Position the dialog against the bottom side of the screen
            comp.y = screen.y + screen.height - comp.height;
        }

        // Check if the new dialog is off the top of the screen
        if (comp.y < screen.y)
        {
            // Position the dialog against the top side of the screen
            comp.y = screen.y;
        }

        return new Point(comp.x, comp.y);
    }

    /**********************************************************************************************
     * Get the available display space as an array of rectangles (there is one rectangle for each
     * screen; if the environment is headless the resulting array will be empty).
     *
     * @return Array of rectangles representing the bounds of the screen(s)
     *********************************************************************************************/
    private static Rectangle[] getScreenRectangles()
    {
        Rectangle[] screenBounds;

        // Check that there is at least one screen
        if (!GraphicsEnvironment.isHeadless())
        {
            // Get the array of screens and create storage for their bounds
            GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            screenBounds = new Rectangle[devices.length];

            // Step through each screen
            for (int index = 0; index < devices.length; index++)
            {
                // Store the screen's bounds
                screenBounds[index] = devices[index].getDefaultConfiguration().getBounds();
            }
        }
        // There are no screens
        else
        {
            screenBounds = new Rectangle[0];
        }

        return screenBounds;
    }
}
