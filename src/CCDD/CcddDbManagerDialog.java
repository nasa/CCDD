/**
 * CFS Command & Data Dictionary project database manager dialog. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.DbManagerDialogType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;

/******************************************************************************
 * CFS Command & Data Dictionary project database manager dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddDbManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final DbManagerDialogType dialogType;

    // Components referenced by multiple methods
    private JTextField nameFld;
    private JTextArea descriptionFld;
    private JScrollPane descScrollPane;

    // Array containing the radio button or check box text and descriptions
    private String[][] arrayItemData;

    // List of radio button or check box item indices for items that appear in
    // the dialog but aren't selectable
    private List<Integer> disabledItems;

    // Indices into the array of arrayItemData when initially split
    private final int DB_NAME = 0;
    private final int DB_DESC = 1;
    private final int DB_LOCK = 2;

    /**************************************************************************
     * Project database manager dialog class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param dialogType
     *            database dialog type: LOGIN, SERVER, CREATE, OPEN, DELETE, or
     *            UNLOCK
     *************************************************************************/
    protected CcddDbManagerDialog(CcddMain ccddMain,
                                  DbManagerDialogType dialogType)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;

        // Create reference to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();

        // Create the project database manager dialog
        initialize();
    }

    /**************************************************************************
     * Create the project database manager dialog. This is executed in a
     * separate thread since it can take a noticeable amount time to complete,
     * and by using a separate thread the GUI is allowed to continue to update.
     * The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the project database manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            // Create panels to hold the components of the dialog
            JPanel selectPnl = new JPanel(new GridBagLayout());

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

            /******************************************************************
             * Build the project database manager dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Create a panel to hold the components of the dialog
                selectPnl.setBorder(BorderFactory.createEmptyBorder());

                // Create dialog based on supplied dialog type
                switch (dialogType)
                {
                    case CREATE:
                        // Get the array containing the roles
                        String[] roles = dbControl.queryRoleList(CcddDbManagerDialog.this);
                        String[][] roleInfo = new String[roles.length][2];

                        // Step through each role
                        for (int index = 0; index < roles.length; index++)
                        {
                            // Store the role name
                            roleInfo[index][0] = roles[index];
                        }

                        // Create a panel containing a grid of radio buttons
                        // representing the roles from which to choose
                        if (!addRadioButtons(null,
                                             false,
                                             roleInfo,
                                             null,
                                             "Select project owner",
                                             selectPnl,
                                             gbc))
                        {
                            // Inform the user that no roles exist on the
                            // server
                            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html><b>No role exists",
                                                                      "Create Project",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                            errorFlag = true;
                        }

                        break;

                    case OPEN:
                        // Get the database names and descriptions
                        getDatabaseInformation(true, false, null);

                        // Create a panel containing a grid of radio buttons
                        // representing the databases from which to choose
                        if (!addRadioButtons((dbControl.getDatabase() == DEFAULT_DATABASE
                                                                                         ? null
                                                                                         : dbControl.getDatabase()),
                                             false,
                                             arrayItemData,
                                             disabledItems,
                                             "Select a project to open",
                                             selectPnl,
                                             gbc))
                        {
                            // Inform the user that no database exists on the
                            // server for which the current user has access
                            displayDatabaseError("Open");
                            errorFlag = true;
                        }

                        break;

                    case RENAME:
                        // Get the database names and descriptions
                        getDatabaseInformation(true, false, dbControl.getDatabase());

                        // Create a panel containing a grid of radio buttons
                        // representing the databases from which to choose
                        if (addRadioButtons(null,
                                            false,
                                            arrayItemData,
                                            disabledItems,
                                            "Select a project to rename",
                                            selectPnl,
                                            gbc))
                        {
                            // Create the rename database name and description
                            // labels and fields
                            addDatabaseInputFields("New project name",
                                                   selectPnl,
                                                   false,
                                                   gbc);

                        }
                        // No database exists to choose
                        else
                        {
                            // Inform the user that no database exists on the
                            // server
                            displayDatabaseError("Rename");
                            errorFlag = true;
                        }

                        break;

                    case COPY:
                        // Get the database names and descriptions
                        getDatabaseInformation(false, false, null);

                        // Create a panel containing a grid of radio buttons
                        // representing the databases from which to choose
                        if (addRadioButtons(null,
                                            false,
                                            arrayItemData,
                                            disabledItems,
                                            "Select a project to copy",
                                            selectPnl,
                                            gbc))
                        {
                            // Create the copy database name and description
                            // labels and fields
                            addDatabaseInputFields("Project copy name",
                                                   selectPnl,
                                                   false,
                                                   gbc);
                        }
                        // No database exists to choose
                        else
                        {
                            // Inform the user that no database exists on the
                            // server
                            displayDatabaseError("Copy");
                            errorFlag = true;
                        }

                        break;

                    case DELETE:
                        // Get the database names and descriptions
                        getDatabaseInformation(true, false, null);

                        // Create a panel containing a grid of check boxes
                        // representing the databases from which to choose
                        if (!addCheckBoxes(null,
                                           arrayItemData,
                                           disabledItems,
                                           "Select a project to delete",
                                           selectPnl))
                        {
                            // Inform the user that no database exists on the
                            // server
                            displayDatabaseError("Delete");
                            errorFlag = true;
                        }

                        break;

                    case UNLOCK:
                        // Get the database names and descriptions
                        getDatabaseInformation(false, true, null);

                        // Create a panel containing a grid of check boxes
                        // representing the databases from which to choose
                        if (!addCheckBoxes(null,
                                           arrayItemData,
                                           disabledItems,
                                           "Select a project to unlock",
                                           selectPnl))
                        {
                            // Inform the user that no database exists on the
                            // server
                            displayDatabaseError("Unlock");
                            errorFlag = true;
                        }

                        break;
                }
            }

            /******************************************************************
             * Project database manager dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Check that no error occurred creating the dialog
                if (!errorFlag)
                {
                    // Display the dialog based on supplied dialog type
                    switch (dialogType)
                    {
                        case CREATE:
                            // Create the database name and description labels
                            // and fields
                            addDatabaseInputFields("New project name",
                                                   selectPnl,
                                                   true,
                                                   gbc);

                            // Display the database creation dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Create Project",
                                                  DialogOption.CREATE_OPTION) == OK_BUTTON)
                            {
                                // Create the database
                                dbControl.createDatabaseInBackground(nameFld.getText(),
                                                                     getRadioButtonSelected(),
                                                                     descriptionFld.getText());
                            }

                            break;

                        case OPEN:
                            // Display the database selection dialog. If the
                            // currently open database has uncommitted changes
                            // then confirm discarding the changes before
                            // proceeding
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Open Project",
                                                  DialogOption.OPEN_OPTION,
                                                  true) == OK_BUTTON
                                && ccddMain.ignoreUncommittedChanges("Open Project",
                                                                     "Discard changes?",
                                                                     true,
                                                                     null,
                                                                     CcddDbManagerDialog.this))
                            {
                                // Open the selected database
                                dbControl.openDatabaseInBackground(getRadioButtonSelected());
                            }

                            break;

                        case RENAME:
                            // Display the rename database dialog. Only the
                            // description can be altered for the currently
                            // open database
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Rename Project",
                                                  DialogOption.RENAME_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Rename the database
                                dbControl.renameDatabaseInBackground(getRadioButtonSelected(),
                                                                     nameFld.getText(),
                                                                     descriptionFld.getText());
                            }

                            break;

                        case COPY:
                            // Display the copy database dialog. If the
                            // currently open database is being renamed and
                            // there are uncommitted changes then confirm
                            // discarding the changes before proceeding
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Copy Project",
                                                  DialogOption.COPY_OPTION,
                                                  true) == OK_BUTTON
                                && (!getRadioButtonSelected().equals(dbControl.getDatabase())
                                || (getRadioButtonSelected().equals(dbControl.getDatabase())
                                && ccddMain.ignoreUncommittedChanges("Copy Project",
                                                                     "Discard changes?",
                                                                     true,
                                                                     null,
                                                                     CcddDbManagerDialog.this))))
                            {
                                // Copy the database
                                dbControl.copyDatabaseInBackground(getRadioButtonSelected(),
                                                                   nameFld.getText(),
                                                                   descriptionFld.getText());
                            }

                            break;

                        case DELETE:
                            // Display the database deletion dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Delete Project(s)",
                                                  DialogOption.DELETE_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Step through each selected database name
                                for (String name : getCheckBoxSelected())
                                {
                                    // Delete the database
                                    dbControl.deleteDatabaseInBackground(name);
                                }
                            }

                            break;

                        case UNLOCK:
                            // Display the database unlock dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Unlock Project(s)",
                                                  DialogOption.UNLOCK_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Step through each selected database name
                                for (String name : getCheckBoxSelected())
                                {
                                    // Unlock the database
                                    dbControl.setDatabaseLockStatus(name, false);
                                }
                            }

                            break;
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Display the project database selection error dialog
     * 
     * @param action
     *            database operation
     *************************************************************************/
    private void displayDatabaseError(String action)
    {
        // Inform the user that no project database exists on the server for
        // which the user has access
        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                  "<html><b>No project exists for which user '</b>"
                                                      + dbControl.getUser()
                                                      + "<b>' has access",
                                                  action + " Project",
                                                  JOptionPane.WARNING_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**************************************************************************
     * Add database name and description labels and fields to the dialog
     * 
     * @param nameText
     *            text to display beside the name input field
     * 
     * @param dialogPnl
     *            panel to which to add the labels and fields
     * 
     * @param enabled
     *            true if the fields are initially enabled, false if disabled
     * 
     * @param dialogGbc
     *            dialog panel GridBagLayout layout constraints
     *************************************************************************/
    private void addDatabaseInputFields(String nameText,
                                        JPanel dialogPanel,
                                        boolean enabled,
                                        GridBagConstraints dialogGbc)
    {
        // Create a border for the fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        GridBagConstraints.REMAINDER,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.HORIZONTAL,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create the name label and field
        JLabel nameLbl = new JLabel(nameText);
        nameLbl.setFont(LABEL_FONT_BOLD);
        nameFld = new JTextField("", 20);
        nameFld.setForeground(Color.BLACK);
        nameFld.setBackground(enabled
                                     ? Color.WHITE
                                     : Color.LIGHT_GRAY);
        nameFld.setFont(LABEL_FONT_PLAIN);
        nameFld.setEditable(enabled);
        nameFld.setBorder(border);

        // Create the description label and field
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setForeground(Color.BLACK);
        descriptionFld.setBackground(enabled
                                            ? Color.WHITE
                                            : Color.LIGHT_GRAY);
        descriptionFld.setFont(LABEL_FONT_PLAIN);
        descriptionFld.setEditable(enabled);
        descriptionFld.setLineWrap(true);
        descriptionFld.setBorder(BorderFactory.createEmptyBorder());
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(enabled
                                            ? Color.WHITE
                                            : Color.LIGHT_GRAY);
        descScrollPane.setBorder(border);

        // Add the name and description labels and fields to a panel
        JPanel nameDescPnl = new JPanel(new GridBagLayout());
        nameDescPnl.add(nameLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING;
        nameDescPnl.add(nameFld, gbc);
        gbc.gridy++;
        gbc.insets.left = 0;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        nameDescPnl.add(descriptionLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = 0;
        nameDescPnl.add(descScrollPane, gbc);

        // Add the panel to the dialog panel
        dialogGbc.gridx = 0;
        dialogGbc.gridy++;
        dialogGbc.gridwidth = GridBagConstraints.REMAINDER;
        dialogGbc.fill = GridBagConstraints.HORIZONTAL;
        dialogGbc.insets.bottom = 0;
        dialogPanel.add(nameDescPnl, dialogGbc);

        // Add a listener for radio button selection change events
        addPropertyChangeListener(new PropertyChangeListener()
        {
            /******************************************************************
             * Handle a radio button selection change event
             *****************************************************************/
            @Override
            public void propertyChange(PropertyChangeEvent pce)
            {
                // Check if the event indicates a radio button selection change
                if (pce.getPropertyName().equals(RADIO_BUTTON_CHANGE_EVENT))
                {
                    // Check if this is a rename or copy type dialog
                    if (dialogType == DbManagerDialogType.RENAME
                        || dialogType == DbManagerDialogType.COPY)
                    {
                        // Get the name of the selected database and assume the
                        // description is blank
                        String name = pce.getNewValue().toString();
                        String desc = "";

                        // Step through each item in the list
                        for (String[] data : arrayItemData)
                        {
                            // Check if the item matches the selected one
                            if (data[DB_NAME].equals(name))
                            {
                                // Store the item description and stop
                                // searching
                                desc = data[DB_DESC];
                                break;
                            }
                        }

                        // Check if this is a copy type dialog
                        if (dialogType == DbManagerDialogType.COPY)
                        {
                            // Append text to the name to differentiate the
                            // copy from the original
                            name += "_copy";
                        }

                        // Place the type name in the name field and the
                        // description in the description field
                        nameFld.setText(name);
                        descriptionFld.setText(desc);

                        // Set the enable state for the input fields. If
                        // renaming and the currently open database is selected
                        // then disable editing of the name input field
                        nameFld.setEditable(!(dialogType == DbManagerDialogType.RENAME
                               && name.equals(dbControl.getDatabase())));
                        nameFld.setBackground(Color.WHITE);
                        descriptionFld.setEditable(true);
                        descriptionFld.setBackground(Color.WHITE);
                        descScrollPane.setBackground(Color.WHITE);
                    }
                }
            }
        });
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
                case CREATE:
                case RENAME:
                case COPY:
                    // Remove any excess white space
                    nameFld.setText(nameFld.getText().trim());

                    // Check that a project or owner is selected
                    if (getRadioButtonSelected() == null)
                    {
                        // Set the target, project or owner, depending on the
                        // dialog type
                        String target = (dialogType == DbManagerDialogType.CREATE)
                                                                                  ? " Owner"
                                                                                  : "";

                        // Inform the user that the project or owner isn't
                        // selected
                        throw new CCDDException("Project"
                                                + target.toLowerCase()
                                                + " must be selected");
                    }

                    // Check if the database name is blank
                    if (nameFld.getText().isEmpty())
                    {
                        // Inform the user that the name is invalid
                        throw new CCDDException("Project name must be entered");
                    }

                    // Check if the name is too long
                    if (nameFld.getText().length() >= MAX_SQL_NAME_LENGTH)
                    {
                        // Inform the user that the name is too long
                        throw new CCDDException("Project name too long ("
                                                + (MAX_SQL_NAME_LENGTH - 1)
                                                + " characters maximum)");
                    }

                    // Check if the name contains an illegal character
                    if (!nameFld.getText().matches(InputDataType.ALPHANUMERIC.getInputMatch()))
                    {
                        // Inform the user that the name is invalid
                        throw new CCDDException("Illegal character(s) in project name");
                    }

                    // Check that the selected database is not the currently
                    // open one (only the description can be altered for this
                    // case)
                    if (!getRadioButtonSelected().equals(dbControl.getDatabase()))
                    {
                        // Get the list of available databases
                        String[] databases = dbControl.queryDatabaseList(CcddDbManagerDialog.this);

                        // Step through each of the database names
                        for (int index = 0; index < databases.length; index++)
                        {
                            // Check if the user-supplied name matches an
                            // existing database name
                            if (databases[index].split(",")[0].equals(nameFld.getText()))
                            {
                                // Inform the user that the name is already in
                                // use
                                throw new CCDDException("Project name already in use");
                            }
                        }
                    }

                    break;

                case OPEN:
                    // Check that a database other than the currently open one
                    // is selected
                    if (getRadioButtonSelected() == null
                        || getRadioButtonSelected().equals(dbControl.getDatabase()))
                    {
                        // Inform the user that a project must be selected
                        throw new CCDDException("Must select a project to open");
                    }

                    break;

                case DELETE:
                case UNLOCK:
                    // Check that a database is selected
                    if (getCheckBoxSelected().length == 0)
                    {
                        // Get the string describing the action to perform
                        String action = dialogType == DbManagerDialogType.DELETE
                                                                                ? "delete"
                                                                                : "unlock";

                        // Inform the user that a project must be selected
                        throw new CCDDException("Must select a project to "
                                                + action);
                    }

                    break;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
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

    /**************************************************************************
     * Get the project database names and descriptions
     * 
     * @param isOnlyUnlocked
     *            true if only unlocked databases are to be displayed
     * 
     * @param isOnlyLocked
     *            true if only locked databases are to be displayed
     * 
     * @param enabledItem
     *            name of an item to be enabled; null if none
     *************************************************************************/
    private void getDatabaseInformation(boolean isOnlyUnlocked,
                                        boolean isOnlyLocked,
                                        String enabledItem)
    {
        disabledItems = new ArrayList<Integer>();
        String[] activeUsers = null;

        // Check if this is an unlock dialog
        if (dialogType == DbManagerDialogType.UNLOCK)
        {
            // Get the list of users of databases with active connections
            activeUsers = dbControl.queryActiveList(CcddDbManagerDialog.this);
        }

        // Get the array containing the database names, lock statuses, and
        // descriptions
        String[] databases = dbControl.queryDatabaseByUserList(CcddDbManagerDialog.this,
                                                               dbControl.getUser());
        arrayItemData = new String[databases.length][];
        int index = 0;

        // Step through each database
        for (String database : databases)
        {
            // Separate and store the database name, lock
            // status, and description
            arrayItemData[index] = database.split(",", 3);
            boolean isLocked = !arrayItemData[index][DB_LOCK].equals("0");

            // Check if the database is locked and that locked databases are to
            // be disabled, if the database is unlocked and that unlocked
            // databases are to be disabled, and if the item is not specified
            // as enabled
            if (((isOnlyUnlocked && isLocked)
                || (isOnlyLocked && !isLocked))
                && !arrayItemData[index][DB_NAME].equals(enabledItem))
            {
                // Add the index of the item to the disabled list
                disabledItems.add(index);
            }

            // Check if this is an unlock dialog. The database description is
            // replaced by the database locked/unlocked status and the attached
            // users
            if (dialogType == DbManagerDialogType.UNLOCK)
            {
                String status = "";

                // Step through the list of databases and attached users
                for (String active : activeUsers)
                {
                    // Separate the database name from the user name
                    String[] dataBaseAndUser = active.split(",");

                    // Check if the database name matches the one in the list
                    if (arrayItemData[index][DB_NAME].equals(dataBaseAndUser[0]))
                    {
                        // Append the user name to the status text
                        status += dataBaseAndUser[1] + ", ";
                    }
                }

                status = CcddUtilities.removeTrailer(status, ", ");

                // Check if the database is locked
                if (isLocked)
                {
                    // Check if this is the currently open database
                    if (arrayItemData[index][DB_NAME].equals(dbControl.getDatabase()))
                    {
                        status = "Current; in use by " + status;
                    }
                    // Not the currently open database
                    else
                    {
                        status = "Locked by " + status;
                    }
                }
                // The database is not locked; check if no users are attached
                // (via other applications than CCDD)
                else if (status.isEmpty())
                {
                    status = "Unlocked";
                }
                // The database is not locked and no users are attached
                else
                {
                    status = "Unlocked; in use by " + status;
                }

                // Replace the database description with the lock status
                arrayItemData[index][DB_DESC] = "<html><i>" + status;
            }

            index++;
        }
    }
}
