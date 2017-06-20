/**
 * CFS Command & Data Dictionary server properties dialog. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.AUTO_COMPLETE_TEXT_SEPARATOR;
import static CCDD.CcddConstants.DEFAULT_POSTGRESQL_HOST;
import static CCDD.CcddConstants.DEFAULT_WEB_SERVER_PORT;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.NUM_REMEMBERED_SERVERS;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SEARCH_STRINGS;
import static CCDD.CcddConstants.SERVER_STRINGS;
import static CCDD.CcddConstants.WEB_SERVER_PORT;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClasses.AutoCompleteTextField;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ServerPropertyDialogType;

/******************************************************************************
 * CFS Command & Data Dictionary server properties dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddServerPropertyDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final ServerPropertyDialogType dialogType;

    // Components referenced by multiple methods
    private JPasswordField passwordField;
    private JTextField userField;
    private JLabel portLabel;
    private JTextField portField;
    private AutoCompleteTextField hostField;

    /**************************************************************************
     * Server properties dialog class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param dialogType
     *            database dialog type: LOGIN, DB_SERVER, or WEB_SERVER
     *************************************************************************/
    protected CcddServerPropertyDialog(CcddMain ccddMain,
                                       ServerPropertyDialogType dialogType)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;

        // Create reference to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();

        // Create the server properties dialog
        initialize();
    }

    /**************************************************************************
     * Create the server properties dialog
     *************************************************************************/
    private void initialize()
    {
        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel to hold the components of the dialog
        JPanel selectPanel = new JPanel(new GridBagLayout());
        selectPanel.setBorder(BorderFactory.createEmptyBorder());

        // Create dialog based on supplied dialog type
        switch (dialogType)
        {
            case LOGIN:
                // Initialize the flag that indicates if a user name is
                // available
                boolean isUsers = false;

                // Check if a connection exists to the server
                if (dbControl.isServerConnected())
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
                                              selectPanel,
                                              gbc);

                    gbc.insets.top = LABEL_VERTICAL_SPACING * 2;
                }
                // No server connection exists; the user must enter a user name
                else
                {
                    // Add the user label and field
                    JLabel userLabel = new JLabel("User");
                    userLabel.setFont(LABEL_FONT_BOLD);
                    gbc.weightx = 0.0;
                    gbc.gridx = 0;
                    selectPanel.add(userLabel, gbc);
                    userField = new JTextField(dbControl.getUser(), 15);
                    userField.setFont(LABEL_FONT_PLAIN);
                    userField.setEditable(true);
                    userField.setForeground(Color.BLACK);
                    userField.setBackground(Color.WHITE);
                    userField.setBorder(border);
                    gbc.weightx = 1.0;
                    gbc.gridx++;
                    selectPanel.add(userField, gbc);

                    isUsers = true;
                }

                // Check if any users exist
                if (isUsers)
                {
                    // Add the password label and field
                    JLabel passwordLabel = new JLabel("Password");
                    passwordLabel.setFont(LABEL_FONT_BOLD);
                    gbc.weightx = 0.0;
                    gbc.gridx = 0;
                    gbc.gridy++;
                    gbc.insets.bottom = 0;
                    selectPanel.add(passwordLabel, gbc);

                    passwordField = new JPasswordField("", 15);
                    passwordField.setFont(LABEL_FONT_PLAIN);
                    passwordField.setForeground(Color.BLACK);
                    passwordField.setBackground(Color.WHITE);
                    passwordField.setBorder(border);
                    passwordField.setEditable(true);
                    gbc.weightx = 1.0;
                    gbc.gridx++;
                    selectPanel.add(passwordField, gbc);

                    // Check if a user is selected
                    if (!dbControl.getUser().isEmpty())
                    {
                        // Set the password field to initially have the focus
                        setInitialFocusComponent(passwordField);
                    }

                    // Display the user & password dialog
                    if (showOptionsDialog(ccddMain.getMainFrame(),
                                          selectPanel,
                                          "Select User",
                                          DialogOption.OK_CANCEL_OPTION,
                                          true) == OK_BUTTON)
                    {
                        // Check if a connection exists to the server
                        if (dbControl.isServerConnected())
                        {
                            // Store the user name from the selected radio
                            // button
                            dbControl.setUser(getRadioButtonSelected());
                        }
                        // No server connection exists
                        else
                        {
                            // Store the user name from the user field
                            dbControl.setUser(userField.getText());
                        }

                        // Store the password
                        dbControl.setPassword(String.valueOf(passwordField.getPassword()));

                        // Open the active database as the new user
                        dbControl.openDatabaseInBackground(dbControl.getDatabase());
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

            case DB_SERVER:
                // Create the database server host, using the list of
                // remembered servers from the program preferences, and port
                // dialog labels and fields
                JLabel hostLabel = new JLabel("Host");
                hostLabel.setFont(LABEL_FONT_BOLD);
                gbc.weightx = 0.0;
                selectPanel.add(hostLabel, gbc);

                List<String> servers = new ArrayList<String>(NUM_REMEMBERED_SERVERS);
                servers.addAll(Arrays.asList(ccddMain.getProgPrefs().get(SERVER_STRINGS,
                                                                         "").split(AUTO_COMPLETE_TEXT_SEPARATOR)));
                hostField = new AutoCompleteTextField(servers,
                                                      NUM_REMEMBERED_SERVERS);
                hostField.setText(dbControl.getHost());
                hostField.setColumns(15);
                hostField.setFont(LABEL_FONT_PLAIN);
                hostField.setEditable(true);
                hostField.setForeground(Color.BLACK);
                hostField.setBackground(Color.WHITE);
                hostField.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPanel.add(hostField, gbc);

                portLabel = new JLabel("Port");
                portLabel.setFont(LABEL_FONT_BOLD);
                gbc.insets.bottom = 0;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                selectPanel.add(portLabel, gbc);

                portField = new JTextField(dbControl.getPort(), 4);
                portField.setFont(LABEL_FONT_PLAIN);
                portField.setEditable(true);
                portField.setForeground(Color.BLACK);
                portField.setBackground(Color.WHITE);
                portField.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPanel.add(portField, gbc);

                // Display the server properties parameter dialog
                if (showOptionsDialog(ccddMain.getMainFrame(),
                                      selectPanel,
                                      "Database Server",
                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Update the host list and store it in the program
                    // preferences
                    hostField.updateList(hostField.getText());
                    ccddMain.getProgPrefs().put(SEARCH_STRINGS, hostField.getListAsString());

                    // Store the server host and port
                    dbControl.setHost(hostField.getText());
                    dbControl.setPort(portField.getText());

                    // Open the active database using the new properties
                    dbControl.openDatabaseInBackground(dbControl.getDatabase());
                }

                break;

            case WEB_SERVER:
                // Create the web server port dialog label and field
                portLabel = new JLabel("Port");
                portLabel.setFont(LABEL_FONT_BOLD);
                gbc.weightx = 0.0;
                gbc.insets.bottom = 0;
                selectPanel.add(portLabel, gbc);

                portField = new JTextField(ccddMain.getProgPrefs().get(WEB_SERVER_PORT,
                                                                       DEFAULT_WEB_SERVER_PORT), 4);
                portField.setFont(LABEL_FONT_PLAIN);
                portField.setEditable(true);
                portField.setForeground(Color.BLACK);
                portField.setBackground(Color.WHITE);
                portField.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                selectPanel.add(portField, gbc);

                // Display the server properties parameter dialog
                if (showOptionsDialog(ccddMain.getMainFrame(),
                                      selectPanel,
                                      "Web Server",
                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Store the web server port
                    ccddMain.getProgPrefs().put(WEB_SERVER_PORT, portField.getText());

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
            // Verify the dialog content based on the supplied dialog type
            switch (dialogType)
            {
                case LOGIN:
                    // Check if no connection exists to the server
                    if (!dbControl.isServerConnected())
                    {
                        // Remove any excess white space
                        userField.setText(userField.getText().trim());

                        // Check if the user field is blank
                        if (userField.getText().isEmpty())
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

                case DB_SERVER:
                    // Remove any excess white space
                    hostField.setText(hostField.getText().trim());
                    portField.setText(portField.getText().trim());

                    // Check if the host field is blank
                    if (hostField.getText().isEmpty())
                    {
                        // Use the default host is none is supplied
                        hostField.setText(DEFAULT_POSTGRESQL_HOST);
                    }

                    // Check if the database server port is invalid
                    if (!portField.getText().isEmpty()
                        && !portField.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                    {
                        // Inform the user that the database port field is
                        // invalid
                        throw new CCDDException("Server port must be blank or a positive integer");
                    }

                    break;

                case WEB_SERVER:
                    // Remove any excess white space
                    portField.setText(portField.getText().trim());

                    // Check if the web server port is invalid
                    if (!portField.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
                    {
                        // Inform the user that the web server port field is
                        // invalid
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
