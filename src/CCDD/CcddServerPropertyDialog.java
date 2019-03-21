/**
 * CFS Command and Data Dictionary server properties dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_HOST;
import static CCDD.CcddConstants.DEFAULT_WEB_SERVER_PORT;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SERVER_STRINGS;
import static CCDD.CcddConstants.STRING_LIST_TEXT_SEPARATOR;
import static CCDD.CcddConstants.WEB_SERVER_PORT;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClassesComponent.AutoCompleteTextField;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.ServerPropertyDialogType;

/**************************************************************************************************
 * CFS Command and Data Dictionary server properties dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddServerPropertyDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final ServerPropertyDialogType dialogType;

    // Components referenced by multiple methods
    private JPasswordField passwordFld;
    private JTextField userFld;
    private JLabel portLbl;
    private JTextField portFld;
    private AutoCompleteTextField hostFld;

    // Flag indicating if the default database should be used after changing the log-in credentials
    private final boolean useActiveDatabase;

    // Flag that indicates if the login dialog is resizable
    private boolean allowResize;

    // Flag that indicates if the password was successfully set in the Password dialog
    private boolean isPasswordSet;

    /**********************************************************************************************
     * Server properties dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param useActiveDatabase
     *            true to use the active database; false to use the default database. This is only
     *            used when opening the database after changing the login credentials
     *
     * @param dialogType
     *            database dialog type: LOGIN, PASSWORD, DB_SERVER, or WEB_SERVER
     *
     * @param errorMessage
     *            message to display following an unsuccessful login; null if no error has occurred
     *            or this isn't the login dialog
     *********************************************************************************************/
    CcddServerPropertyDialog(CcddMain ccddMain,
                             boolean useActiveDatabase,
                             ServerPropertyDialogType dialogType,
                             String errorMessage)
    {
        this.ccddMain = ccddMain;
        this.useActiveDatabase = useActiveDatabase;
        this.dialogType = dialogType;

        // Create reference to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();

        // Create the server properties dialog
        initialize(errorMessage);
    }

    /**********************************************************************************************
     * Server properties dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param dialogType
     *            database dialog type: LOGIN, PASSWORD, DB_SERVER, or WEB_SERVER
     *********************************************************************************************/
    CcddServerPropertyDialog(CcddMain ccddMain, ServerPropertyDialogType dialogType)
    {
        this(ccddMain, true, dialogType, null);
    }

    /**********************************************************************************************
     * Check if the password was successfully set in the Password dialog
     *
     * @return true if the password is set in the Password dialog; false if Cancel was selected or
     *         the dialog type is not Password
     *********************************************************************************************/
    protected boolean isPasswordSet()
    {
        return isPasswordSet;
    }

    /**********************************************************************************************
     * Create the server properties dialog
     *
     * @param errorMessage
     *            message to display following an unsuccessful login; null if no error has occurred
     *            or this isn't the login dialog
     *********************************************************************************************/
    private void initialize(String errorMessage)
    {
        isPasswordSet = false;

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

        // Set the initial layout manager characteristics
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

        // Create a panel to hold the components of the dialog
        JPanel selectPnl = new JPanel(new GridBagLayout());
        selectPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create dialog based on supplied dialog type
        switch (dialogType)
        {
            case LOGIN:
                // Add the user and password fields to the dialog. Check if there are any users
                // registered in the server (if connected), or if a user name is entered
                if (addLoginPanel(selectPnl, gbc, border))
                {
                    // Check if an error occurred on the previous login attempt
                    if (errorMessage != null)
                    {
                        // Add the error message to the dialog
                        JPanel errorPnl = new JPanel();
                        JLabel errorLbl = new JLabel(errorMessage);
                        errorLbl.setFont(ModifiableFontInfo.LABEL_ITALIC.getFont());
                        errorLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                        gbc.gridwidth = GridBagConstraints.REMAINDER;
                        gbc.gridx = 0;
                        gbc.gridy++;
                        errorPnl.add(errorLbl);
                        selectPnl.add(errorPnl, gbc);
                    }

                    // Display the user & password dialog
                    if (showOptionsDialog(ccddMain.getMainFrame(),
                                          selectPnl,
                                          "Select User",
                                          DialogOption.OK_CANCEL_OPTION,
                                          allowResize) == OK_BUTTON)
                    {
                        // Check if a connection exists to the server
                        if (dbControl.isServerConnected())
                        {
                            // Store the user name from the selected radio button
                            dbControl.setUser(getRadioButtonSelected());
                        }
                        // No server connection exists
                        else
                        {
                            // Store the user name from the user field
                            dbControl.setUser(userFld.getText());
                        }

                        // Store the password
                        dbControl.setPassword(String.valueOf(passwordFld.getPassword()));

                        // Open the specified database as the new user; use the flag to determine
                        // if the active or default database should be opened
                        dbControl.openDatabaseInBackground(useActiveDatabase
                                                                             ? dbControl.getProjectName()
                                                                             : DEFAULT_DATABASE);
                    }
                }
                // No other user exists to choose
                else
                {
                    // Inform the user that no other user exists on the server
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>No other user exists",
                                                              "Server Login",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                break;

            case PASSWORD:
                // Add the user and password fields to the dialog
                addLoginPanel(selectPnl, gbc, border);

                // Display the user & password dialog
                if (new CcddDialogHandler().showOptionsDialog(ccddMain.getMainFrame(),
                                                              selectPnl,
                                                              "Enter Password",
                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Store the password
                    dbControl.setPassword(String.valueOf(passwordFld.getPassword()));
                    isPasswordSet = true;
                }

                break;

            case DB_SERVER:
                // Create the database server host, using the list of remembered servers from the
                // program preferences, the port dialog labels and fields, and the check box for
                // enabling an SSL connection
                JLabel hostLbl = new JLabel("Host");
                hostLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.weightx = 0.0;
                selectPnl.add(hostLbl, gbc);

                List<String> servers = new ArrayList<String>(ModifiableSizeInfo.NUM_REMEMBERED_SERVERS.getSize());
                servers.addAll(Arrays.asList(ccddMain.getProgPrefs().get(SERVER_STRINGS, "")
                                                     .split(STRING_LIST_TEXT_SEPARATOR)));
                hostFld = new AutoCompleteTextField(servers,
                                                    ModifiableSizeInfo.NUM_REMEMBERED_SERVERS.getSize());
                hostFld.setText(dbControl.getHost());
                hostFld.setColumns(15);
                hostFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                hostFld.setEditable(true);
                hostFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                hostFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                hostFld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPnl.add(hostFld, gbc);

                portLbl = new JLabel("Port");
                portLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                selectPnl.add(portLbl, gbc);

                portFld = new JTextField(dbControl.getPort(), 4);
                portFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                portFld.setEditable(true);
                portFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                portFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                portFld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPnl.add(portFld, gbc);

                JCheckBox enableSSLCbox = new JCheckBox("Enable SSL", dbControl.isSSL());
                enableSSLCbox.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                enableSSLCbox.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                enableSSLCbox.setBorder(BorderFactory.createEmptyBorder());
                gbc.insets.bottom = 0;
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                selectPnl.add(enableSSLCbox, gbc);

                // Display the server properties parameter dialog
                if (showOptionsDialog(ccddMain.getMainFrame(),
                                      selectPnl,
                                      "Database Server",
                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Update the host list and store it in the program preferences
                    hostFld.updateList(hostFld.getText());
                    ccddMain.getProgPrefs().put(SERVER_STRINGS, hostFld.getListAsString());

                    // Open the default database using the new server properties
                    dbControl.openDatabaseInBackground(DEFAULT_DATABASE,
                                                       hostFld.getText(),
                                                       portFld.getText(),
                                                       enableSSLCbox.isSelected());
                }

                break;

            case WEB_SERVER:
                // Create the web server port dialog label and field
                portLbl = new JLabel("Port");
                portLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.weightx = 0.0;
                gbc.insets.bottom = 0;
                selectPnl.add(portLbl, gbc);

                portFld = new JTextField(ccddMain.getProgPrefs().get(WEB_SERVER_PORT,
                                                                     DEFAULT_WEB_SERVER_PORT),
                                         4);
                portFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                portFld.setEditable(true);
                portFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                portFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                portFld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPnl.add(portFld, gbc);

                // Display the server properties parameter dialog
                if (showOptionsDialog(ccddMain.getMainFrame(),
                                      selectPnl,
                                      "Web Server",
                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Store the web server port
                    ccddMain.getProgPrefs().put(WEB_SERVER_PORT, portFld.getText());

                    // Check if the web server exists
                    if (ccddMain.getWebServer() != null)
                    {
                        // Store the web server port and restart the web server
                        ccddMain.getWebServer().startServer();
                    }
                }

                break;
        }
    }

    /**********************************************************************************************
     * Add the user and password fields to the specified panel
     *
     * @param selectPnl
     *            JPanel to which the fields are added
     *
     * @param gbc
     *            reference to the GridBagConstraints governing the panel
     *
     * @param border
     *            border with which to surround the input fields
     *
     * @return true if at least one user exists in the server or if the user can be entered
     *         manually
     *********************************************************************************************/
    private boolean addLoginPanel(JPanel selectPnl, GridBagConstraints gbc, Border border)
    {
        // Initialize the flags that indicates if a user name is available and if the dialog should
        // be resizable
        boolean isUsers = false;
        allowResize = false;

        // Add the server host to the dialog so that the user knows what credentials are required
        JLabel serverLbl1 = new JLabel("Enter credentials for server: ");
        serverLbl1.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        serverLbl1.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets.bottom = 0;
        selectPnl.add(serverLbl1, gbc);
        JLabel serverLbl2 = new JLabel(dbControl.getHost());
        serverLbl2.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        serverLbl2.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        gbc.insets.top = 0;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.gridy++;
        selectPnl.add(serverLbl2, gbc);
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.weightx = 1.0;
        gbc.gridwidth = 1;
        gbc.gridy++;

        // Check if a connection exists to the server
        if (dbControl.isServerConnected() && dialogType == ServerPropertyDialogType.LOGIN)
        {
            // Get the array containing the users
            String[] users = dbControl.queryUserList(CcddServerPropertyDialog.this);
            String[][] userInfo = new String[users.length][2];

            int userIndex = -1;

            // Step through each user
            for (int index = 0; index < users.length; index++)
            {
                // Store the user name
                userInfo[index][0] = users[index];

                // Check if this is the current user
                if (users[index].equals(dbControl.getUser()))
                {
                    // Store the index for the current user
                    userIndex = index;
                }
            }

            // Add radio buttons for the users
            isUsers = addRadioButtons(dbControl.getUser(),
                                      false,
                                      userInfo,
                                      Arrays.asList(userIndex),
                                      "Select user",
                                      false,
                                      selectPnl,
                                      gbc);

            // Allow resizing the dialog if the number of users to choose from exceeds the
            // initial number of viewable rows (i.e., the scroll bar is displayed)
            allowResize = users.length > ModifiableSizeInfo.INIT_VIEWABLE_COMPONENT_ROWS.getSize();

            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
        }
        // No server connection exists; the user must enter a user name
        else
        {
            // Add the user label and field
            JLabel userLbl = new JLabel("User");
            userLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            selectPnl.add(userLbl, gbc);
            userFld = new JTextField(dbControl.getUser(), 15);
            userFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            userFld.setEditable(dialogType == ServerPropertyDialogType.LOGIN);
            userFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            userFld.setBackground(dialogType == ServerPropertyDialogType.LOGIN
                                                                               ? ModifiableColorInfo.INPUT_BACK.getColor()
                                                                               : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
            userFld.setBorder(border);
            gbc.weightx = 1.0;
            gbc.gridx++;
            selectPnl.add(userFld, gbc);

            isUsers = true;
        }

        // Check if any users exist
        if (isUsers)
        {
            // Add the password label and field
            JLabel passwordLbl = new JLabel("Password");
            passwordLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.weightx = 0.0;
            gbc.gridx = 0;
            gbc.gridy++;
            gbc.insets.bottom = 0;
            selectPnl.add(passwordLbl, gbc);

            passwordFld = new JPasswordField("", 15);
            passwordFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            passwordFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            passwordFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            passwordFld.setBorder(border);
            passwordFld.setEditable(true);
            gbc.weightx = 1.0;
            gbc.gridx++;
            selectPnl.add(passwordFld, gbc);

            // Check if a user is selected
            if (!dbControl.getUser().isEmpty())
            {
                // Set the password field to initially have the focus
                setInitialFocusComponent(passwordFld);
            }
        }

        return isUsers;
    }

    /**********************************************************************************************
     * Verify that the dialog content is valid
     *
     * @return true if the input values are valid
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            // Verify the dialog content based on the supplied dialog type
            switch (dialogType)
            {
                case LOGIN:
                    // Remove any excess white space
                    passwordFld.setText(String.valueOf(passwordFld.getPassword()).trim());

                    // Check if no connection exists to the server
                    if (!dbControl.isServerConnected())
                    {
                        // Remove any excess white space
                        userFld.setText(userFld.getText().trim());

                        // Check if the user field is blank
                        if (userFld.getText().isEmpty())
                        {
                            // Inform the user that a field is invalid
                            throw new CCDDException("User name must be entered");
                        }
                    }
                    // Check that a user other than the current one is selected
                    else if (getRadioButtonSelected().equals(dbControl.getUser()))
                    {
                        isValid = false;
                    }

                    break;

                case PASSWORD:
                    // Remove any excess white space
                    passwordFld.setText(String.valueOf(passwordFld.getPassword()).trim());

                    // Check if the password is valid for the user and database. Note that 'trust'
                    // authentication allows any password (including a blank) to be entered
                    if (!dbControl.authenticateUser(dbControl.getUser(),
                                                    String.valueOf(passwordFld.getPassword())))
                    {
                        // Inform the user that the database port field is invalid
                        throw new CCDDException("Password incorrect for user '"
                                                + dbControl.getUser()
                                                + "'");
                    }

                    break;

                case DB_SERVER:
                    // Remove any excess white space
                    hostFld.setText(hostFld.getText().trim());
                    portFld.setText(portFld.getText().trim());

                    // Check if the host field is blank
                    if (hostFld.getText().isEmpty())
                    {
                        // Use the default host is none is supplied
                        hostFld.setText(DEFAULT_POSTGRESQL_HOST);
                    }

                    // Check if the database server port is invalid
                    if (!portFld.getText().isEmpty()
                        && !portFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                    {
                        // Inform the user that the database port field is invalid
                        throw new CCDDException("Server port must be blank or a positive integer");
                    }

                    break;

                case WEB_SERVER:
                    // Remove any excess white space
                    portFld.setText(portFld.getText().trim());

                    // Check if the web server port is invalid
                    if (!portFld.getText().matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                    {
                        // Inform the user that the web server port field is invalid
                        throw new CCDDException("Server port must be a positive integer");
                    }

                    break;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddServerPropertyDialog.this,
                                                      "<html><b>"
                                                                                     + ce.getMessage(),
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
