/**
 * CFS Command & Data Dictionary custom dialog handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CANCEL_ICON;
import static CCDD.CcddConstants.CHECK_BOX_CHANGE_EVENT;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DIALOG_INNER_PAD;
import static CCDD.CcddConstants.DIALOG_MAX_LINE_LENGTH;
import static CCDD.CcddConstants.DIALOG_MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.ERROR_ICON;
import static CCDD.CcddConstants.IGNORE_BUTTON;
import static CCDD.CcddConstants.INFORMATION_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.OK_ICON;
import static CCDD.CcddConstants.QUESTION_ICON;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;
import static CCDD.CcddConstants.UPDATE_BUTTON;
import static CCDD.CcddConstants.WARNING_ICON;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import CCDD.CcddConstants.DialogOption;

/******************************************************************************
 * CFS Command & Data Dictionary dialog handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddDialogHandler extends JDialog
{
    // Common button utility methods
    private final CcddButtonPanelHandler buttonHandler;

    // Dialog button selected by the user
    private int buttonSelected;

    // Radio button selected by the user
    private String radioButtonSelected;

    // Text field containing the file name(s) chosen by the user
    private JTextField nameField;

    // Check boxes generated from user-supplied list
    private JCheckBox[] checkBox;

    // Initial width of the dialog in pixels
    private int initialDialogWidth;

    // Component that initially has the focus; null to set the focus to the
    // first (default) component
    private Component initialFocusComponent;

    /**************************************************************************
     * File extension filter class
     *************************************************************************/
    private class ExtensionFilter extends FileFilter
    {
        private final String extension;
        private final String description;

        /**********************************************************************
         * File extension filter class constructor
         * 
         * @param extension
         *            file extension
         * 
         * @param description
         *            file extension description
         *********************************************************************/
        ExtensionFilter(String extension, String description)
        {
            this.extension = extension;
            this.description = description;
        }

        /**********************************************************************
         * Determine if the specified file matches the file extension filter
         * 
         * @param file
         *            file or directory path
         * 
         * @return true if the file matches the extension or is a directory
         *         path
         *********************************************************************/
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

                // Check if the file ends with the extension. Extensions are
                // case sensitive
                if (path.endsWith(extension))
                {
                    // Accept the file
                    isAccepted = true;
                }
            }

            return isAccepted;
        }

        /**********************************************************************
         * Get the file extension
         * 
         * @return File extension
         *********************************************************************/
        protected String getExtension()
        {
            return extension;
        }

        /**********************************************************************
         * Get the file extension description
         * 
         * @return File extension description
         *********************************************************************/
        @Override
        public String getDescription()
        {
            return description;
        }
    }

    /**************************************************************************
     * Dialog box handler constructor
     *************************************************************************/
    protected CcddDialogHandler()
    {
        buttonSelected = JOptionPane.CLOSED_OPTION;
        checkBox = null;

        // Create a handler for the dialog buttons
        buttonHandler = new CcddButtonPanelHandler()
        {
            /******************************************************************
             * Override the window closing method
             * 
             * @param button
             *            button that initiated dialog closing
             *****************************************************************/
            @Override
            protected void closeWindow(int button)
            {
                // Check if the Cancel button was pressed, or if the Okay
                // button was pressed and the selection is verified
                if (button == CANCEL_BUTTON
                    || (button == OK_BUTTON && verifySelection()))
                {
                    // Close the dialog, indicating the selected button
                    closeDialog(button);
                }
            }
        };
    }

    /**************************************************************************
     * Close the dialog box
     *************************************************************************/
    protected void closeDialog()
    {
        setVisible(false);
        dispose();
    }

    /**************************************************************************
     * Close the dialog box and set the button selected
     * 
     * @param button
     *            selected button to return
     *************************************************************************/
    protected void closeDialog(int button)
    {
        // Set the selected button
        buttonSelected = button;

        setVisible(false);
        dispose();
    }

    /**************************************************************************
     * Handle the frame close button press event. The default is to activate
     * the last button in the dialog's button panel (close/cancel)
     *************************************************************************/
    protected void windowCloseButtonAction()
    {
        // Send a button press event for the last button in the dialog's button
        // panel, which is either the close or cancel button
        buttonHandler.getExitButton().doClick();
    }

    /**************************************************************************
     * Placeholder for any actions required when this dialog is closed. This is
     * intended to handle special actions for non-modal dialogs
     *************************************************************************/
    protected void windowClosedAction()
    {
    }

    /**************************************************************************
     * Set the component to initially have the focus
     * 
     * @param component
     *            component to initially have the focus
     *************************************************************************/
    protected void setInitialFocusComponent(Component component)
    {
        initialFocusComponent = component;
    }

    /**************************************************************************
     * Get the currently selected radio button's text component
     * 
     * @return Name associated with the currently selected radio button; null
     *         if no radio button is selected
     *************************************************************************/
    protected String getRadioButtonSelected()
    {
        return radioButtonSelected;
    }

    /**************************************************************************
     * Get the reference to the file chooser file name text field
     * 
     * @return Reference to the file chooser file name text field
     *************************************************************************/
    protected JTextField getFileNameField()
    {
        return nameField;
    }

    /**************************************************************************
     * Get the reference to the check box array
     * 
     * @return Reference to the check box array
     *************************************************************************/
    protected JCheckBox[] getCheckBoxes()
    {
        return checkBox;
    }

    /**************************************************************************
     * Get the text component for the currently selected check box(es)
     * 
     * @return Array containing the name associated with the currently selected
     *         check box(es); empty array if no check box is selected
     *************************************************************************/
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

    /**************************************************************************
     * Display a non-resizable, modal message dialog with the provided icon and
     * return the button type selected
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param message
     *            text message to display
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION,
     *            READ_OPTION, PRINT_OPTION, CLOSE_OPTION, OK_OPTION, or
     *            OK_CANCEL_OPTION
     * 
     * @param icon
     *            icon to display to the left of the text message
     * 
     * @return Selected button type
     *************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    String title,
                                    DialogOption optionType,
                                    Icon icon)
    {
        return createDialog(parent,
                            message,
                            null,
                            title,
                            optionType,
                            icon,
                            false,
                            true);
    }

    /**************************************************************************
     * Display a non-resizable, modal message dialog and return the button type
     * selected. The icon displayed is based on the message type
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param message
     *            text message to display
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param messageType
     *            message type: JOptionPane.PLAIN_MESSAGE,
     *            JOptionPane.INFORMATION_MESSAGE,
     *            JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE, or
     *            JOptionPane.ERROR_MESSAGE
     * 
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION,
     *            READ_OPTION, PRINT_OPTION, CLOSE_OPTION, OK_OPTION, or
     *            OK_CANCEL_OPTION
     * 
     * @return Selected button type
     *************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    String title,
                                    int messageType,
                                    DialogOption optionType)
    {
        Icon icon = null;

        // Get the icon to display beside the text message based on the current
        // look & feel. If the look & feel doesn't supply icons then revert to
        // a default set
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
                            title,
                            optionType,
                            icon,
                            false,
                            true);
    }

    /**************************************************************************
     * Display a non-resizable, modal message dialog using user-supplied
     * buttons, and return the button type selected. The icon displayed is
     * based on the message type
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param message
     *            text message to display
     * 
     * @param lowerPanel
     *            panel containing the dialog buttons
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param messageType
     *            message type: JOptionPane.PLAIN_MESSAGE,
     *            JOptionPane.INFORMATION_MESSAGE,
     *            JOptionPane.QUESTION_MESSAGE, JOptionPane.WARNING_MESSAGE, or
     *            JOptionPane.ERROR_MESSAGE
     * 
     * @return Selected button type
     *************************************************************************/
    protected int showMessageDialog(Component parent,
                                    String message,
                                    JPanel lowerPanel,
                                    String title,
                                    int messageType)
    {
        Icon icon = null;

        // Get the icon to display beside the text message based on the current
        // look & feel. If the look & feel doesn't supply icons then revert to
        // a default set
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
                            lowerPanel,
                            title,
                            null,
                            icon,
                            false,
                            true);
    }

    /**************************************************************************
     * Get the icon associated with the UI key provided. If no icon is
     * available for the key then substitute a default icon
     * 
     * @param UIKey
     *            an Object specifying the icon
     * 
     * @param defaultIcon
     *            icon to use if the icon specified by UIKey cannot be found
     * 
     * @return Icon associated with UIKey
     *************************************************************************/
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

    /**************************************************************************
     * Create the Ignore/Ignore All/Cancel dialog
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param message
     *            text message to display
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param ignoreToolTip
     *            Ignore button tool tip text; null if no tool tip is to be
     *            displayed
     * 
     * @param ignoreAllToolTip
     *            Ignore All button tool tip text; null if no tool tip is to be
     *            displayed
     * 
     * @param cancelToolTip
     *            Cancel button tool tip text; null if no tool tip is to be
     *            displayed
     * 
     * @return Selected button type
     *************************************************************************/
    protected int showIgnoreCancelDialog(Component parent,
                                         String message,
                                         String title,
                                         String ignoreToolTip,
                                         String ignoreAllToolTip,
                                         String cancelToolTip)
    {
        // Create the Ignore button
        final JButton btnIgnore = CcddButtonPanelHandler.createButton("Ignore",
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
            /******************************************************************
             * Indicate the which button was pressed and close the dialog
             *****************************************************************/
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

        // Set the button listeners
        btnIgnore.addActionListener(listener);
        btnIgnoreAll.addActionListener(listener);
        btnCancel.addActionListener(listener);

        // Create the panel for the dialog buttons and add the dialog buttons
        // to the panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(btnIgnore);
        buttonPanel.add(btnIgnoreAll);
        buttonPanel.add(btnCancel);

        return showMessageDialog(parent,
                                 message,
                                 buttonPanel,
                                 title,
                                 JOptionPane.QUESTION_MESSAGE);
    }

    /**************************************************************************
     * Display a modal, non-resizable user-interactive dialog using buttons
     * defined by the supplied option type. Return the button type selected
     * 
     * @param parent
     *            window to center the dialog over
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
     *************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Component dialogPanel,
                                    String title,
                                    DialogOption optionType)
    {
        // Used for the Appearance and database dialogs
        return createDialog(parent,
                            dialogPanel,
                            null,
                            title,
                            optionType,
                            null,
                            false,
                            true);
    }

    /**************************************************************************
     * Display a modal, user-interactive dialog using buttons defined by the
     * supplied option type. The dialog may be resized, based on the input
     * flag. Return the button type selected
     * 
     * @param parent
     *            window to center the dialog over
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
     *************************************************************************/
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
                            title,
                            optionType,
                            null,
                            resizable,
                            true);
    }

    /**************************************************************************
     * Display a modal, user-interactive dialog using user-supplied buttons.
     * The dialog may be resized, based on the input flag. Return the button
     * type selected
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param dialogPanel
     *            panel containing the dialog components
     * 
     * @param lowerPanel
     *            panel containing the dialog buttons
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param resizable
     *            true to allow the dialog to be resized
     * 
     * @return Selected button type
     *************************************************************************/
    protected int showOptionsDialog(Component parent,
                                    Object dialogPanel,
                                    JPanel lowerPanel,
                                    String title,
                                    boolean resizable)
    {
        // Used for the Preferences dialog
        return createDialog(parent,
                            dialogPanel,
                            lowerPanel,
                            title,
                            DialogOption.OK_OPTION,
                            null,
                            resizable,
                            true);
    }

    /**************************************************************************
     * Display a dialog that allows the user to select one or more files or a
     * folder
     * 
     * @param main
     *            main class
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param fileName
     *            file name to display in the input field chosen; null if no
     *            file name is initially displayed. Ignored if folderOnly is
     *            true
     * 
     * @param fileType
     *            describes the type of files when more than one file extension
     *            is supplied; null if only one (or no) file extension is
     *            provided. Ignored if folderOnly is true
     * 
     * @param fileExtensions
     *            valid file extensions with description. Use null to allow any
     *            extension. Ignored if folderOnly is true
     * 
     * @param folderOnly
     *            true to allow only folders to be selected
     * 
     * @param multipleFiles
     *            true to allow selection of more than one file
     * 
     * @param fileTitle
     *            title to display in the dialog window frame
     * 
     * @param filePathKey
     *            key to extract the file path name from the program
     *            preferences backing store
     * 
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION,
     *            READ_OPTION, PRINT_OPTION, CLOSE_OPTION, OK_OPTION, or
     *            OK_CANCEL_OPTION
     * 
     * @return Array containing the selected file handle(s). null if the Cancel
     *         button is selected. The first file reference is null if Okay is
     *         selected and the file name list is empty
     *************************************************************************/
    protected File[] choosePathFile(CcddMain main,
                                    Component parent,
                                    String fileName,
                                    String fileType,
                                    FileNameExtensionFilter[] fileExtensions,
                                    boolean folderOnly,
                                    boolean multipleFiles,
                                    String fileTitle,
                                    String filePathKey,
                                    DialogOption optionType)
    {
        return choosePathFile(main,
                              parent,
                              fileName,
                              fileType,
                              fileExtensions,
                              folderOnly,
                              multipleFiles,
                              fileTitle,
                              filePathKey,
                              optionType,
                              null);
    }

    /**************************************************************************
     * Display a dialog that allows the user to select one or more files or a
     * folder. Optionally allow a panel containing other components to be
     * displayed beneath the file chooser portion
     * 
     * @param main
     *            main class
     * 
     * @param parent
     *            window to center the dialog over
     * 
     * @param fileName
     *            file name to display in the input field chosen; null if no
     *            file name is initially displayed. Ignored if folderOnly is
     *            true
     * 
     * @param fileType
     *            describes the type of files when more than one file extension
     *            is supplied; null if only one (or no) file extension is
     *            provided. Ignored if folderOnly is true
     * 
     * @param fileExtensions
     *            valid file extensions with description. Use null to allow any
     *            extension. Ignored if folderOnly is true
     * 
     * @param folderOnly
     *            true to allow only folders to be selected
     * 
     * @param multipleFiles
     *            true to allow selection of more than one file
     * 
     * @param fileTitle
     *            title to display in the dialog window frame
     * 
     * @param filePathKey
     *            key to extract the file path from the program preferences
     *            backing store
     * 
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION,
     *            READ_OPTION, PRINT_OPTION, CLOSE_OPTION, OK_OPTION, or
     *            OK_CANCEL_OPTION
     * 
     * @param lowerPanel
     *            JPanel containing other components to display below the
     *            JChooser component; null if no lower components are to be
     *            displayed
     * 
     * @return Array containing the selected file handle(s). null if the Cancel
     *         button is selected. The first file reference is null if Okay is
     *         selected and the file name list is empty
     *************************************************************************/
    protected File[] choosePathFile(CcddMain main,
                                    Component parent,
                                    String fileName,
                                    String fileType,
                                    FileNameExtensionFilter[] fileExtensions,
                                    boolean folderOnly,
                                    boolean multipleFiles,
                                    String fileTitle,
                                    String filePathKey,
                                    DialogOption optionType,
                                    JPanel lowerPanel)
    {
        File[] file = new File[1];

        // Create the file chooser. Set the path to the one from the back store
        // per the provided key; if no entry exists for the key then use the
        // default path (the default location is operating system dependent)
        final JFileChooser chooser = new JFileChooser(main.getProgPrefs().get(filePathKey, null));

        // True to allow multiple files to be selected
        chooser.setMultiSelectionEnabled(multipleFiles);

        // Locate the file name input field in the file chooser dialog. In
        // order to use custom buttons in the file chooser dialog, the file
        // name input field must be located and the inputs to it listened for
        // so that the selected file can be updated. Otherwise, changes made to
        // the file name are ignored and when the Okay button is pressed the
        // file name used is the highlighted one in the file list box
        nameField = getFileChooserTextField(chooser);

        // Check if only a folder is allowed to be chosen
        if (folderOnly)
        {
            // Allow only the selection of a folder (not a file)
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            // Get the path to initially display
            fileName = chooser.getCurrentDirectory().getAbsolutePath();
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
                        // Build the extension names string and append the
                        // extension(s) to the list
                        extensions += "*."
                                      + CcddUtilities.convertArrayToString(fileExtension.getExtensions())
                                      + ", ";
                        extensionList.addAll(Arrays.asList(fileExtension.getExtensions()));
                    }

                    // Set the filter to the list of all applicable extensions
                    // so that initially all files of the acceptable types are
                    // displayed
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
                    // Set the file filter to show only files with the desired
                    // extension
                    chooser.setFileFilter(chooser.getChoosableFileFilters()[1]);
                }
            }

            // Add a listener for changes to the selected file(s)
            chooser.addPropertyChangeListener(new PropertyChangeListener()
            {
                /**************************************************************
                 * Handle changes to the file(s) selected. Whenever a file
                 * selection change strip the path name from the file(s) and
                 * build a list of just the names. Insert this list into the
                 * file chooser's file name text field. This is done
                 * automatically for most look & feels, but not all (e.g.,
                 * GTK+)
                 *************************************************************/
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
                            // Append the file name without the path,
                            // surrounded by quotes, to the name list
                            nameList += "\""
                                        + chooser.getSelectedFiles()[index].getName()
                                        + "\" ";
                        }

                        // Insert the file name list into the file chooser's
                        // file name text field
                        nameField.setText(nameList);
                    }
                }
            });
        }

        // Insert the file name into the file chooser's file name text field.
        // Most look & feels do this automatically, but not all (e.g., GTK+)
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
                         fileTitle,
                         optionType,
                         null,
                         true,
                         true) == OK_BUTTON)
        {
            // Extract the file name(s) from the file chooser name field.
            // Spaces may appear in the names only if the name is within double
            // quotes. The names are split on spaces outside of quotes, at
            // every second quote, or at commas. First any leading or trailing
            // whitespace characters are removed
            String names = nameField.getText().trim();

            // Create a flag to keep track of whether or not the character
            // being checked is inside or outside a pair of quotes
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

                // Check if the character is a space outside of a pair or
                // quotes, or is the second quote of a pair of quotes
                if ((c == ' ' || c == '"') && !isQuoted)
                {
                    // Replace the character with a comma. Occurrences of
                    // double commas can result from this operation; these are
                    // accounted for later
                    names = names.substring(0, index)
                            + ","
                            + names.substring(index + 1);
                }
            }

            // Replace every instance of back-to-back commas that may have
            // resulted in the replacement steps above with a single comma,
            // remove every remaining quote, then split the string at the
            // commas, which now delineate the separate file names
            String[] fileNames = names.replaceAll(",,+", ",").replaceAll("\"+",
                                                                         "").split(",");
            // Check if the file name text field isn't empty
            if (!fileNames[0].isEmpty())
            {
                // Create a file array
                file = new File[fileNames.length];

                // Step through the file names/paths
                for (int i = 0; i < fileNames.length; i++)
                {
                    // Check if a file type was specified and the file name has
                    // no extension
                    if (fileExtensions != null && !fileNames[i].contains("."))
                    {
                        // Add the extension to the file name. If more than one
                        // extension is provided then use the first one
                        fileNames[i] = fileNames[i]
                                       + "."
                                       + ((ExtensionFilter) chooser.getFileFilter()).getExtension();
                    }

                    // Create a file handle for each file name or the path
                    // name. If this is not a folder, prepend the file path to
                    // the name
                    file[i] = new File((folderOnly
                                                  ? ""
                                                  : chooser.getCurrentDirectory()
                                                    + File.separator)
                                       + fileNames[i]);
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

    /**************************************************************************
     * Locate and return the file name input field in the file chooser dialog
     * 
     * @param cont
     *            container containing the JFileChooser
     * 
     * @return JFileChooser file name input field
     *************************************************************************/
    private JTextField getFileChooserTextField(Container cont)
    {
        JTextField tf = null;

        // Step through the file chooser components. The components are checked
        // in reverse to account for those look & feels (i.e., Motif) that have
        // a text field for the path in addition to one for the file name. By
        // looking in reverse the field with the file name is found first
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
            // Component isn't a text field; check if the text field hasn't
            // been located (to prevent continued searching after the text
            // field is found) and if this is another container,
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

    /**************************************************************************
     * Placeholder for method to verify the the dialog box selection(s) prior
     * to closing
     * 
     * @return true
     *************************************************************************/
    protected boolean verifySelection()
    {
        return true;
    }

    /**************************************************************************
     * Add radio buttons, representing the available selections from a supplied
     * array, in a grid pattern to the dialog panel. Select and gray out the
     * currently selected item if supplied
     * 
     * @param rbtnSelected
     *            name of the initially selected item. null if no item is
     *            initially chosen
     * 
     * @param canReselect
     *            true if the initially selected item can be reselected; false
     *            to disable reselection of the initial item
     * 
     * @param itemInformation
     *            array of items from which to select. The first column is the
     *            item name and the second column is the item description
     * 
     * @param disabledItems
     *            list of indices for items that appear in the list but cannot
     *            be selected; null or empty list if no items are disabled
     * 
     * @param rbtnText
     *            text to display above the radio button panel
     * 
     * @param dialogPanel
     *            dialog panel on which to place the radio buttons
     * 
     * @param dialogGbc
     *            dialog panel GridBagLayout layout constraints
     * 
     * @return true if there is more than one item to display; the dialog is
     *         populated with the array of radio buttons. false if there are
     *         less than two items to display; the dialog is unchanged
     *************************************************************************/
    protected boolean addRadioButtons(String rbtnSelected,
                                      boolean canReselect,
                                      String[][] itemInformation,
                                      List<Integer> disabledItems,
                                      String rbtnText,
                                      JPanel dialogPanel,
                                      GridBagConstraints dialogGbc)
    {
        boolean rbtnsAdded = false;

        // Store the currently selected item. Use null if no preselected item
        // list is provided
        radioButtonSelected = rbtnSelected;

        // Check if any items exist
        if (itemInformation.length != 0)
        {
            // Create a copy of the layout constraints and update them
            GridBagConstraints dlgGbc = (GridBagConstraints) dialogGbc.clone();
            dlgGbc.insets.top = LABEL_VERTICAL_SPACING;
            dlgGbc.insets.bottom = 0;
            dlgGbc.gridwidth = GridBagConstraints.REMAINDER;
            dlgGbc.weighty = 0.0;
            dlgGbc.fill = GridBagConstraints.BOTH;

            // Create the label for the radio button panel
            JLabel rbtnLabel = new JLabel(rbtnText);
            rbtnLabel.setFont(LABEL_FONT_BOLD);
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
                                                            new Insets(LABEL_VERTICAL_SPACING,
                                                                       LABEL_HORIZONTAL_SPACING,
                                                                       LABEL_VERTICAL_SPACING,
                                                                       LABEL_HORIZONTAL_SPACING),
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
                /**************************************************************
                 * Select the item based on the radio button selection
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Get the text associated with the selected radio button
                    String buttonText = ((JRadioButton) ae.getSource()).getText();

                    // Check if the selected item differs from the one
                    // currently selected
                    if (radioButtonSelected == null ||
                        !radioButtonSelected.equals(buttonText))
                    {
                        // Update the currently selected item
                        radioButtonSelected = buttonText;

                        // Issue an event to any listeners that the radio
                        // button selection changed
                        firePropertyChange(RADIO_BUTTON_CHANGE_EVENT,
                                           "",
                                           radioButtonSelected);
                    }
                }
            };

            // Set up storage for the radio buttons
            JRadioButton[] radioButton = new JRadioButton[itemInformation.length];
            ButtonGroup rbtnGroup = new ButtonGroup();

            boolean isDescriptions = false;

            // Step through each item
            for (int index = 0; index < itemInformation.length; index++)
            {
                // Check if a description is provided
                if (itemInformation[index][1] != null)
                {
                    // Set the flag indicating descriptions are provided and
                    // stop searching
                    isDescriptions = true;
                    break;
                }
            }

            // Calculate the number of columns in the radio button grid based
            // on if there are descriptions and the number of items to display,
            // up to a maximum of 5 columns
            int gridWidth = isDescriptions
                                          ? 1
                                          : (int) Math.min(Math.sqrt(itemInformation.length), 5);

            // Create radio buttons for each available item
            for (int index = 0; index < itemInformation.length; index++)
            {
                radioButton[index] = new JRadioButton(itemInformation[index][0],
                                                      false);
                radioButton[index].setFont(LABEL_FONT_BOLD);
                radioButton[index].setBorder(emptyBorder);
                radioButton[index].addActionListener(listener);

                // Enable the radio button if the item isn't in the disabled
                // list
                radioButton[index].setEnabled(disabledItems == null
                                              || !disabledItems.contains(index));

                // Add the radio buttons to a group so that only one is active
                // at a time
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

                // Add the radio button to the dialog panel
                rbtnGridPnl.add(radioButton[index], gbc);

                // Check if a description is provided
                if (itemInformation[index].length != 1
                    && itemInformation[index][1] != null)
                {
                    // Add the item description
                    gbc.weightx = 1.0;
                    gbc.gridx++;
                    JLabel descriptionLbl = new JLabel(itemInformation[index][1]);
                    descriptionLbl.setFont(LABEL_FONT_PLAIN);
                    rbtnGridPnl.add(descriptionLbl, gbc);
                    gbc.weightx = 0.0;
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
            }

            // Create a scroll pane to house the radio buttons in case there
            // are a large number to choose from. Set the scroll speed of the
            // scroll pane based on the row (i.e., radio button) height
            JScrollPane scrollPane = new JScrollPane(rbtnGridPnl);
            scrollPane.setBorder(emptyBorder);
            scrollPane.setViewportBorder(emptyBorder);
            scrollPane.getVerticalScrollBar().setUnitIncrement(radioButton[0].getPreferredSize().height / 2
                                                               + LABEL_VERTICAL_SPACING);

            // Check if the scrollable list exceeds the maximum desirable
            // height
            if (rbtnGridPnl.getPreferredSize().getHeight() > MIN_WINDOW_HEIGHT)
            {
                // Set the size of the scrollable list; the vertical scroll bar
                // is displayed
                scrollPane.setPreferredSize(new Dimension((int) rbtnGridPnl.getPreferredSize().getWidth()
                                                          + LABEL_HORIZONTAL_SPACING * 2,
                                                          MIN_WINDOW_HEIGHT));
            }

            // Add the scrollable panel containing the radio buttons to the
            // outer radio button panel in order for the border to appear
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            rbtnPnl.add(scrollPane, gbc);

            // Add the radio button panel to the dialog
            dlgGbc.weighty = 1.0;
            dlgGbc.gridy++;
            dialogPanel.add(rbtnPnl, dlgGbc);

            // Adjust the input layout constraints grid y-coordinate so that
            // subsequent components using these constraints are placed
            // correctly
            dialogGbc.gridy = dlgGbc.gridy;

            rbtnsAdded = true;
        }

        return rbtnsAdded;
    }

    /**************************************************************************
     * Add check boxes, representing the available selections from a supplied
     * array, in a grid pattern to the dialog panel. Select and gray out the
     * currently selected item if supplied
     * 
     * @param cboxSelected
     *            name of the initially selected item. Null if no item is
     *            initially chosen. A protected item cannot be selected
     * 
     * @param itemInformation
     *            array of items from which to select. The first column is the
     *            item name and the second column is the item description
     * 
     * @param disabledItems
     *            list of indices for items that appear in the list but cannot
     *            be selected; null or empty list if no items are disabled
     * 
     * @param cboxText
     *            text to display above the check box panel
     * 
     * @param dialogPanel
     *            dialog panel on which to place the check boxes
     * 
     * @return true if there is more than one item to display; the dialog is
     *         populated with the array of check boxes. false if there are less
     *         than two items to display; the dialog is unchanged
     *************************************************************************/
    protected boolean addCheckBoxes(String cboxSelected,
                                    String[][] itemInformation,
                                    List<Integer> disabledItems,
                                    String cboxText,
                                    JPanel dialogPanel)
    {
        boolean cboxesAdded = false;

        // Check if any items exist
        if (itemInformation.length != 0)
        {
            // Create a copy of the layout constraints and update them
            GridBagConstraints dlgGbc = new GridBagConstraints(0,
                                                               0,
                                                               GridBagConstraints.REMAINDER,
                                                               1,
                                                               1.0,
                                                               0.0,
                                                               GridBagConstraints.LINE_START,
                                                               GridBagConstraints.BOTH,
                                                               new Insets(LABEL_VERTICAL_SPACING,
                                                                          LABEL_HORIZONTAL_SPACING,
                                                                          0,
                                                                          LABEL_HORIZONTAL_SPACING),
                                                               0,
                                                               0);

            // Create the label for the check box panel
            JLabel cboxLabel = new JLabel(cboxText);
            cboxLabel.setFont(LABEL_FONT_BOLD);
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
                                                            new Insets(LABEL_VERTICAL_SPACING,
                                                                       LABEL_HORIZONTAL_SPACING,
                                                                       LABEL_VERTICAL_SPACING,
                                                                       LABEL_HORIZONTAL_SPACING),
                                                            0,
                                                            0);

            // Create an empty border for use around the dialog components
            Border emptyBorder = BorderFactory.createEmptyBorder();

            // Create panels to contain the check boxes within a scroll pane
            JPanel cboxPanel = new JPanel(new GridBagLayout());
            cboxPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            JPanel cboxGridPanel = new JPanel(new GridBagLayout());
            cboxGridPanel.setBorder(emptyBorder);

            // Create a listener for radio button selection changes
            ActionListener listener = new ActionListener()
            {
                /**************************************************************
                 * Select the item based on the check box selection
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Issue an event to any listeners that the check box
                    // selection changed, sending the text associated with the
                    // selected check box
                    firePropertyChange(CHECK_BOX_CHANGE_EVENT,
                                       "",
                                       ((JCheckBox) ae.getSource()).getText());
                }
            };

            // Set up storage for the check boxes
            checkBox = new JCheckBox[itemInformation.length];

            boolean isDescriptions = false;

            // Step through each item
            for (int index = 0; index < itemInformation.length; index++)
            {
                // Check if a description is provided
                if (itemInformation[index][1] != null)
                {
                    // Set the flag indicating descriptions are provided and
                    // stop searching
                    isDescriptions = true;
                    break;
                }
            }

            // Calculate the number of columns in the check box grid based on
            // if there are descriptions and the number of items to display, up
            // to a maximum of 5 columns
            int gridWidth = isDescriptions
                                          ? 1
                                          : (int) Math.min(Math.sqrt(itemInformation.length), 5);

            // Create check boxes for each available item
            for (int index = 0; index < itemInformation.length; index++)
            {
                checkBox[index] = new JCheckBox(itemInformation[index][0], false);
                checkBox[index].setFont(LABEL_FONT_BOLD);
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

                // Add the check box to the dialog panel
                cboxGridPanel.add(checkBox[index], gbc);

                // Check if a description is provided
                if (itemInformation[index].length != 1
                    && itemInformation[index][1] != null)
                {
                    // Add the item description
                    gbc.weightx = 1.0;
                    gbc.gridx++;
                    JLabel descriptionLbl = new JLabel(itemInformation[index][1]);
                    descriptionLbl.setFont(LABEL_FONT_PLAIN);
                    cboxGridPanel.add(descriptionLbl, gbc);
                    gbc.weightx = 0.0;
                }

                // Check if the item name matches the preselected item
                if (checkBox[index].isEnabled()
                    && itemInformation[index][0].equals(cboxSelected))
                {
                    // Select the check box
                    checkBox[index].setSelected(true);
                }
            }

            // Create a scroll pane to house the check boxes in case there are
            // a large number to choose from. Set the scroll speed of the
            // scroll pane based on the row (i.e., check box) height
            JScrollPane scrollPane = new JScrollPane(cboxGridPanel);
            scrollPane.setBorder(emptyBorder);
            scrollPane.setViewportBorder(emptyBorder);
            scrollPane.getVerticalScrollBar().setUnitIncrement(checkBox[0].getPreferredSize().height / 2
                                                               + LABEL_VERTICAL_SPACING);

            // Check if the scrollable list exceeds the maximum desirable
            // height
            if (cboxGridPanel.getPreferredSize().getHeight() > MIN_WINDOW_HEIGHT)
            {
                // Set the size of the scrollable list; the vertical scroll bar
                // is displayed
                scrollPane.setPreferredSize(new Dimension((int)
                                                          cboxGridPanel.getPreferredSize().getWidth()
                                                          + LABEL_HORIZONTAL_SPACING * 2,
                                                          MIN_WINDOW_HEIGHT));
            }

            // Add the scrollable panel containing the check boxes to the outer
            // check box panel in order for the border to appear
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            cboxPanel.add(scrollPane, gbc);

            // Add the check box panel to the dialog
            dlgGbc.insets.top = LABEL_VERTICAL_SPACING / 2;
            dlgGbc.weighty = 1.0;
            dlgGbc.gridy++;
            dialogPanel.add(cboxPanel, dlgGbc);

            // Set the flag to indicate that check boxes were added
            cboxesAdded = true;
        }

        return cboxesAdded;
    }

    /**************************************************************************
     * Extract the dialog's button(s) from the supplied panel, then find the
     * widest button calculated from the button's text and icon. Set the width
     * of the panel containing the buttons based on the widest button
     *************************************************************************/
    protected void setButtonWidth()
    {
        buttonHandler.setButtonWidth();
    }

    /**************************************************************************
     * Set the number of rows occupied by the dialog's buttons
     * 
     * @param rows
     *            number of dialog button rows
     *************************************************************************/
    protected void setButtonRows(int rows)
    {
        buttonHandler.setButtonRows(rows);
    }

    /**************************************************************************
     * Enable/disable the button panel buttons. Override to include other
     * dialog controls
     * 
     * @param enable
     *            true to enable the buttons; false to disable
     *************************************************************************/
    protected void setControlsEnabled(boolean enable)
    {
        buttonHandler.setButtonsEnabled(enable);
    }

    /**************************************************************************
     * Create the dialog. If no buttons are provided (lower panel) then create
     * the buttons and button listeners needed based on the dialog type
     * 
     * @param parent
     *            window to center the dialog over; set to null to redirect
     *            message dialog text to the standard output stream (usually
     *            the command line)
     * 
     * @param upperObject
     *            object containing the dialog components or message
     * 
     * @param buttonPanel
     *            panel containing the dialog buttons
     * 
     * @param title
     *            title to display in the dialog window frame
     * 
     * @param optionType
     *            dialog type: LOAD_OPTION, SAVE_OPTION, SEARCH_OPTION,
     *            READ_OPTION, PRINT_OPTION, CLOSE_OPTION, OK_OPTION, or
     *            OK_CANCEL_OPTION
     * 
     * @param icon
     *            icon to display to the left of the text message
     * 
     * @param resizable
     *            true to allow the dialog to be resized
     * 
     * @param modal
     *            false to allow the main application window to still be
     *            operated while the dialog is open
     * 
     * @return Selected button type
     *************************************************************************/
    protected int createDialog(Component parent,
                               Object upperObject,
                               JPanel buttonPanel,
                               String title,
                               DialogOption optionType,
                               Icon icon,
                               boolean resizable,
                               boolean modal)
    {
        // Check if the parent component doesn't exist
        if (parent == null)
        {
            // Check if the component is a string and that the dialog is not a
            // question type; i.e., this is a plain, information, warning, or
            // error message dialog
            if (upperObject instanceof String
                && !icon.equals(getIcon("OptionPane.questionIcon", QUESTION_ICON)))
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
                // Not a warning or error message
                else
                {
                    // Output the message to the standard output stream
                    System.out.println(message);
                }
            }
            // Not a message dialog, or else is a question dialog requiring
            // user input
            else
            {
                // Use the root pane for the parent
                parent = getRootPane();
            }
        }

        // Check if a parent component exists
        if (parent != null)
        {
            // Create a component to point to the upper contents of the dialog
            JComponent upperComponent;

            // Check if a text message was provided instead of an upper panel
            if (upperObject instanceof String)
            {
                // Create a panel to hold the text message
                JPanel textPnl = new JPanel();
                textPnl.setBorder(BorderFactory.createEmptyBorder());

                // Create a panel to hold the icon. Add some padding between
                // the icon and the text message
                JPanel iconPanel = new JPanel();
                iconPanel.setBorder(BorderFactory.createEmptyBorder(0,
                                                                    0,
                                                                    0,
                                                                    DIALOG_INNER_PAD));
                iconPanel.add(new JLabel(icon));

                // Create a label to hold the text message. Format the message
                // to constrain the character width to a specified maximum
                JLabel textLbl = new JLabel(CcddUtilities.wrapText(upperObject.toString(),
                                                                   DIALOG_MAX_LINE_LENGTH),
                                            SwingConstants.LEFT);
                textLbl.setFont(LABEL_FONT_PLAIN);

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

            // Set up button panel related items and combine the button and
            // upper panels
            buttonHandler.assembleWindowComponents(buttonPanel,
                                                   upperComponent,
                                                   optionType,
                                                   getContentPane(),
                                                   getRootPane());

            // Check if the title is provided
            if (title != null)
            {
                // Set the dialog's title
                setTitle(title);
            }

            // Set if operation of the main window is allowed while this dialog
            // is open
            setModalityType(modal
                                 ? JDialog.ModalityType.APPLICATION_MODAL
                                 : JDialog.ModalityType.MODELESS);

            // Set the default close operation so that the dialog frame's close
            // button doesn't automatically exit the dialog. Instead, if this
            // close button is pressed a button press event is sent to the last
            // button on the dialog's button panel
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            // Add a listener for dialog window events
            addWindowListener(new WindowAdapter()
            {
                /**************************************************************
                 * Handle the dialog frame close button press event
                 *************************************************************/
                @Override
                public void windowClosing(WindowEvent we)
                {
                    // Perform action needed when the window's frame close
                    // button is pressed
                    windowCloseButtonAction();
                }

                /**************************************************************
                 * Handle the dialog window close event
                 *************************************************************/
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

            // Set the preferred size so that setRelativeTo call positions
            // other dialogs relative to this one's position when it's no
            // longer visible
            setPreferredSize(getPreferredSize());

            // Check if the dialog is resizable
            if (resizable)
            {
                // Set the minimum width to the larger of (1) the default
                // dialog width and (2) the packed dialog width (a pixel is
                // added to this width to prevent the dialog from being resized
                // smaller than its original width). Set the minimum height to
                // the packed height of the dialog components
                setMinimumSize(new Dimension(Math.max(DIALOG_MIN_WINDOW_WIDTH,
                                                      getWidth() + 1),
                                             getHeight()));
            }

            // Store the dialog's initial width
            initialDialogWidth = getMinimumSize().width;

            // Position the dialog frame centered on the parent
            setLocationRelativeTo(parent);

            // Check if a component is selected to initially have the focus
            // (otherwise default to the first component)
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

    /**************************************************************************
     * Set the dialog's minimum size
     *************************************************************************/
    protected void setDialogMinimumSize()
    {
        setMinimumSize(new Dimension(initialDialogWidth,
                                     getPreferredSize().height));
    }

    /**************************************************************************
     * Override the setLocationRelativeTo() method in order to modify the
     * default code so that the dialog can be positioned relative to a parent
     * that was present, but is no longer visible. Otherwise, the dialog would
     * be centered on the screen
     * 
     * @param comp
     *            component in relation to which the dialog's location is
     *            determined
     *************************************************************************/
    @Override
    public void setLocationRelativeTo(Component comp)
    {
        // Check if the component exists, has valid size and position values,
        // but is not visible
        if (comp != null && comp.isPreferredSizeSet() && !comp.isShowing())
        {
            // Get the component's graphics configuration, then determine the
            // new dialog's position relative to the component
            GraphicsConfiguration gc = comp.getGraphicsConfiguration();
            Rectangle gcBounds = gc.getBounds();
            Dimension newDlgSize = getSize();
            Dimension compSize = comp.getSize();
            int dx = comp.getX() + ((compSize.width - newDlgSize.width) / 2);
            int dy = comp.getY() + ((compSize.height - newDlgSize.height) / 2);

            // Check if the new dialog is off the bottom of the screen
            if (dy + newDlgSize.height > gcBounds.y + gcBounds.height)
            {
                // Position the dialog against the bottom side of the screen
                dy = gcBounds.y + gcBounds.height - newDlgSize.height;
            }

            // Check if the new dialog is off the top of the screen
            if (dy < gcBounds.y)
            {
                // Position the dialog against the top side of the screen
                dy = gcBounds.y;
            }

            // Check if the new dialog is off the right of the screen
            if (dx + newDlgSize.width > gcBounds.x + gcBounds.width)
            {
                // Position the dialog against the right side of the screen
                dx = gcBounds.x + gcBounds.width - newDlgSize.width;
            }

            // Check if the new dialog is off the left of the screen
            if (dx < gcBounds.x)
            {
                // Position the dialog against the left side of the screen
                dx = gcBounds.x;
            }

            // Position the new dialog
            setLocation(dx, dy);
        }
        // The component is visible, doesn't exist, or hasn't already been
        // sized and positioned
        else
        {
            // Set the location using the default method
            super.setLocationRelativeTo(comp);
        }
    }
}
