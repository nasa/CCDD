/**
 * CFS Command and Data Dictionary project database manager dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DATABASE_ADMIN_SEPARATOR;
import static CCDD.CcddConstants.DATABASE_COMMENT_SEPARATOR;
import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.RADIO_BUTTON_CHANGE_EVENT;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.AccessLevel;
import CCDD.CcddConstants.AccessLevelEditorColumnInfo;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.DbManagerDialogType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.UsersColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary project database manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddDbManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private final DbManagerDialogType dialogType;
    private CcddJTableHandler accessTable;

    // Components referenced by multiple methods
    private JTextField nameFld;
    private JTextArea descriptionFld;
    private JScrollPane descScrollPane;
    private JCheckBox stampChkBx;

    // Cell editor for the user name and access level columns
    private ComboBoxCellEditor userNameCellEditor;
    private ComboBoxCellEditor accessLevelCellEditor;

    // Array containing the radio button or check box text and descriptions
    private String[][] arrayItemData;

    // List of radio button or check box item indices for items that appear in the dialog but
    // aren't selectable
    private List<Integer> disabledItems;

    // Table instance model data. Current copy is the table information as it exists in the table
    // editor and is used to determine what changes have been made to the table since the previous
    // editor update
    private String[][] committedData;

    // Owner of the currently selected project (used when changing a project's owner)
    private String currentOwner;

    // Indices into the array of arrayItemData
    private final int DB_PRJNAME = 0;
    private final int DB_INFO = 1;

    // Text to automatically append to the end of a project name when copying
    private final String COPY_APPEND = "_copy";

    private final String DIALOG_TITLE = "Manage User Access Level";

    private final String ADMIN_LIST = "[Project admin(s): ";

    /**********************************************************************************************
     * Project database manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param dialogType
     *            database dialog type: LOGIN, SERVER, CREATE, OPEN, DELETE, or UNLOCK
     *********************************************************************************************/
    CcddDbManagerDialog(CcddMain ccddMain, DbManagerDialogType dialogType)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;

        // Create references to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();

        // Create the project database manager dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the project database manager dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the project database manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;

            // Create panels to hold the components of the dialog
            JPanel selectPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            // Set the initial layout manager characteristics
            GridBagConstraints gbc = new GridBagConstraints(0,
                                                            0,
                                                            1,
                                                            1,
                                                            1.0,
                                                            0.0,
                                                            GridBagConstraints.LINE_START,
                                                            GridBagConstraints.BOTH,
                                                            new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                       ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                            0,
                                                            0);

            /**************************************************************************************
             * Build the project database manager dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                try
                {
                    // Create a panel to hold the components of the dialog
                    selectPnl.setBorder(BorderFactory.createEmptyBorder());

                    // Create dialog based on supplied dialog type
                    switch (dialogType)
                    {
                        case CREATE:
                            // Create a panel containing a grid of radio buttons representing the
                            // roles from which to choose
                            if (!addRadioButtons(null,
                                                 false,
                                                 getRoleInformation(),
                                                 null,
                                                 "Select project owner",
                                                 false,
                                                 selectPnl,
                                                 gbc))
                            {
                                // Inform the user that no roles exist on the server
                                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                          "<html><b>No role exists",
                                                                          "Create Project",
                                                                          JOptionPane.WARNING_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                                errorFlag = true;
                                throw new CCDDException();
                            }

                            break;

                        case OPEN:
                            // Get the project names and descriptions
                            getDatabaseInformation(true, false, null);

                            // Create a panel containing a grid of radio buttons representing the
                            // projects from which to choose
                            if (!addRadioButtons((dbControl.getDatabaseName() == DEFAULT_DATABASE
                                                                                                  ? null
                                                                                                  : dbControl.getDatabaseName()),
                                                 false,
                                                 arrayItemData,
                                                 disabledItems,
                                                 "Select a project to open",
                                                 false,
                                                 selectPnl,
                                                 gbc))
                            {
                                throw new CCDDException("Open");
                            }

                            break;

                        case RENAME:
                            // Get the project names and descriptions
                            getDatabaseInformation(true, false, dbControl.getDatabaseName());

                            // Create a panel containing a grid of radio buttons representing the
                            // projects from which to choose
                            if (addRadioButtons(null,
                                                false,
                                                arrayItemData,
                                                disabledItems,
                                                "Select a project to rename",
                                                false,
                                                selectPnl,
                                                gbc))
                            {
                                // Create the rename project name and description labels and fields
                                addDatabaseInputFields("New project name", selectPnl, false, gbc);
                            }
                            // No project exists to choose
                            else
                            {
                                throw new CCDDException("Rename");
                            }

                            break;

                        case COPY:
                            // Get the project names and descriptions
                            getDatabaseInformation(false, false, null);

                            // Create a panel containing a grid of radio buttons representing the
                            // projects from which to choose
                            if (addRadioButtons(null,
                                                false,
                                                arrayItemData,
                                                disabledItems,
                                                "Select a project to copy",
                                                false,
                                                selectPnl,
                                                gbc))
                            {
                                // Create the copy project name and description labels and fields
                                addDatabaseInputFields("Project copy name", selectPnl, false, gbc);

                                // Create a date and time stamp check box
                                stampChkBx = new JCheckBox("Append date and time to project name");
                                stampChkBx.setBorder(BorderFactory.createEmptyBorder());
                                stampChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                                stampChkBx.setSelected(false);
                                stampChkBx.setEnabled(false);

                                // Create a listener for check box selection actions
                                stampChkBx.addActionListener(new ActionListener()
                                {
                                    String timeStamp = "";
                                    boolean isCopy = false;

                                    /******************************************************************
                                     * Handle check box selection actions
                                     *****************************************************************/
                                    @Override
                                    public void actionPerformed(ActionEvent ae)
                                    {
                                        // Check if the data and time stamp check box is selected
                                        if (((JCheckBox) ae.getSource()).isSelected())
                                        {
                                            // Get the current date and time stamp
                                            timeStamp = "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());

                                            isCopy = nameFld.getText().endsWith(COPY_APPEND);

                                            // Append the date and time stamp to the file name
                                            nameFld.setText(nameFld.getText().replaceFirst("(?:"
                                                                                           + COPY_APPEND
                                                                                           + "$|$)",
                                                                                           timeStamp));
                                        }
                                        // The check box is not selected
                                        else
                                        {
                                            // Remove the date and time stamp
                                            nameFld.setText(nameFld.getText().replaceFirst(timeStamp,
                                                                                           isCopy
                                                                                                  ? COPY_APPEND
                                                                                                  : ""));
                                        }
                                    }
                                });

                                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
                                gbc.gridy++;
                                selectPnl.add(stampChkBx, gbc);
                                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                            }
                            // No project exists to choose
                            else
                            {
                                throw new CCDDException("Copy");
                            }

                            break;

                        case DELETE:
                            // Get the project names and descriptions
                            getDatabaseInformation(true, false, null);

                            // Create a panel containing a grid of check boxes representing the
                            // projects from which to choose
                            if (!addCheckBoxes(null,
                                               arrayItemData,
                                               disabledItems,
                                               "Select a project to delete",
                                               false,
                                               selectPnl))
                            {
                                throw new CCDDException("Delete");
                            }

                            break;

                        case UNLOCK:
                            // Get the project names and descriptions
                            getDatabaseInformation(false, true, null);

                            // Create a panel containing a grid of check boxes representing the
                            // projects from which to choose
                            if (!addCheckBoxes(null,
                                               arrayItemData,
                                               disabledItems,
                                               "Select a project to unlock",
                                               true,
                                               selectPnl))
                            {
                                throw new CCDDException("Unlock");
                            }

                            break;

                        case OWNER:
                            // Get the project names and descriptions
                            getDatabaseInformation(true, false, null);

                            // Create a panel containing a grid of radio buttons representing the
                            // projects from which to choose
                            if (!addRadioButtons(null,
                                                 true,
                                                 arrayItemData,
                                                 disabledItems,
                                                 "Select a project for which to change ownership",
                                                 true,
                                                 selectPnl,
                                                 gbc))
                            {
                                throw new CCDDException("Owner");
                            }

                            // Increment so that the project owner label appears
                            gbc.gridy++;

                            // Create a panel containing a grid of radio buttons representing the
                            // roles from which to choose
                            if (!addRadioButtons(null,
                                                 false,
                                                 getRoleInformation(),
                                                 null,
                                                 "Select new owner",
                                                 false,
                                                 selectPnl,
                                                 gbc))
                            {
                                // Inform the user that no roles exist on the server
                                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                          "<html><b>No role exists",
                                                                          "Change Project Owner",
                                                                          JOptionPane.WARNING_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                                errorFlag = true;
                                throw new CCDDException();
                            }

                            break;

                        case ACCESS:
                            // Set the initial layout manager characteristics
                            GridBagConstraints gbc = new GridBagConstraints(0,
                                                                            0,
                                                                            1,
                                                                            1,
                                                                            1.0,
                                                                            1.0,
                                                                            GridBagConstraints.LINE_START,
                                                                            GridBagConstraints.BOTH,
                                                                            new Insets(0, 0, 0, 0),
                                                                            0,
                                                                            0);

                            // Create a copy of the user access level data so it can be used to
                            // determine if
                            // changes are made
                            storeCurrentData();

                            // Define the panel to contain the table and place it in the editor
                            JPanel tablePnl = new JPanel();
                            tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                            tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                            tablePnl.add(createUserAccesslevelTable());
                            selectPnl.add(tablePnl, gbc);
                            selectPnl.setBorder(BorderFactory.createEmptyBorder());

                            // Create the cell editors for user names and access levels
                            createCellEditors();

                            // Set the modal undo manager and table references in the keyboard
                            // handler while the user access level editor is active
                            ccddMain.getKeyboardHandler().setModalDialogReference(accessTable.getUndoManager(),
                                                                                  accessTable);

                            // New button
                            JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                                       INSERT_ICON,
                                                                                       KeyEvent.VK_I,
                                                                                       "Insert a new row into the table");

                            // Create a listener for the Insert Row button
                            btnInsertRow.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Insert a new row into the table at the selected location
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.insertEmptyRow(true);
                                }
                            });

                            // Delete button
                            JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                                       DELETE_ICON,
                                                                                       KeyEvent.VK_D,
                                                                                       "Delete the selected row(s) from the table");

                            // Create a listener for the Delete row button
                            btnDeleteRow.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Delete the selected row(s) from the table
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.deleteRow(true);
                                }
                            });

                            // Move Up button
                            JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                                    UP_ICON,
                                                                                    KeyEvent.VK_U,
                                                                                    "Move the selected row(s) up");

                            // Create a listener for the Move Up button
                            btnMoveUp.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Move the selected row(s) up in the table
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.moveRowUp();
                                }
                            });

                            // Move Down button
                            JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                                      DOWN_ICON,
                                                                                      KeyEvent.VK_W,
                                                                                      "Move the selected row(s) down");

                            // Create a listener for the Move Down button
                            btnMoveDown.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Move the selected row(s) down in the table
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.moveRowDown();
                                }
                            });

                            // Undo button
                            JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                                  UNDO_ICON,
                                                                                  KeyEvent.VK_Z,
                                                                                  "Undo the last edit");

                            // Create a listener for the Undo button
                            btnUndo.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Undo the last cell edit
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.getUndoManager().undo();
                                }
                            });

                            // Redo button
                            JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                                  REDO_ICON,
                                                                                  KeyEvent.VK_Y,
                                                                                  "Redo the last undone edit");

                            // Create a listener for the Redo button
                            btnRedo.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Redo the last cell edit that was undone
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    accessTable.getUndoManager().redo();
                                }
                            });

                            // Store the user access levels button
                            JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                                   STORE_ICON,
                                                                                   KeyEvent.VK_S,
                                                                                   "Store the user access levels");
                            btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                            // Create a listener for the Store button
                            btnStore.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************
                                 * Store the user access level table
                                 *****************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    // Only update the table in the database if a cell's content
                                    // has changed, none of the required columns is missing a
                                    // value, and the user confirms the action
                                    if (accessTable.isTableChanged(committedData)
                                        && !checkForMissingColumns()
                                        && new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
                                                                                     "<html><b>Store changes in project database?",
                                                                                     "Store Changes",
                                                                                     JOptionPane.QUESTION_MESSAGE,
                                                                                     DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                                    {
                                        // Store the updated user access level table
                                        dbTable.storeInformationTableInBackground(InternalTable.USERS,
                                                                                  CcddUtilities.removeArrayListColumn(getUpdatedData(),
                                                                                                                      UsersColumn.OID.ordinal()),
                                                                                  null,
                                                                                  CcddDbManagerDialog.this);
                                    }
                                }
                            });

                            // Close button
                            btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                           CLOSE_ICON,
                                                                           KeyEvent.VK_C,
                                                                           "Close the user access level editor");

                            // Create a listener for the Close button
                            btnClose.addActionListener(new ValidateCellActionListener(accessTable)
                            {
                                /******************************************************************************
                                 * Close the user access level editor dialog
                                 *****************************************************************************/
                                @Override
                                protected void performAction(ActionEvent ae)
                                {
                                    windowCloseButtonAction();
                                }
                            });

                            // Add buttons in the order in which they'll appear (left to right, top
                            // to bottom)
                            buttonPnl.add(btnInsertRow);
                            buttonPnl.add(btnMoveUp);
                            buttonPnl.add(btnUndo);
                            buttonPnl.add(btnStore);
                            buttonPnl.add(btnDeleteRow);
                            buttonPnl.add(btnMoveDown);
                            buttonPnl.add(btnRedo);
                            buttonPnl.add(btnClose);

                            // Distribute the buttons across two rows
                            setButtonRows(2);

                            break;
                    }
                }
                catch (CCDDException ce)
                {
                    // Check if the error message is provided
                    if (!ce.getMessage().isEmpty())
                    {
                        // Inform the user that no project exists on the server for which the user
                        // has access
                        displayDatabaseError(ce.getMessage());
                    }

                    errorFlag = true;
                }
                catch (Exception e)
                {
                    // Inform the user than an unexpected error occurred
                    CcddUtilities.displayException(e, ccddMain.getMainFrame());
                    errorFlag = true;
                }
            }

            /**************************************************************************************
             * Project database manager dialog creation complete
             *************************************************************************************/
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
                            // Create the project name and description labels and fields
                            addDatabaseInputFields("New project name", selectPnl, true, gbc);

                            // Display the project creation dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Create Project",
                                                  DialogOption.CREATE_OPTION) == OK_BUTTON)
                            {
                                // Create the project
                                dbControl.createDatabaseInBackground(nameFld.getText(),
                                                                     getRadioButtonSelected(),
                                                                     getRadioButtonSelected(),
                                                                     descriptionFld.getText());
                            }

                            break;

                        case OPEN:
                            // Display the project selection dialog. If the currently open project
                            // has uncommitted changes then confirm discarding the changes before
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
                                // Open the selected project
                                dbControl.openDatabaseInBackground(getRadioButtonSelected());
                            }

                            break;

                        case RENAME:
                            // Display the rename project dialog. Only the description can be
                            // altered for the currently open project
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Rename Project",
                                                  DialogOption.RENAME_OPTION,
                                                  true) == OK_BUTTON
                                && (!getRadioButtonSelected().equals(dbControl.getDatabaseName())
                                    || ccddMain.ignoreUncommittedChanges("Rename Project",
                                                                         "Discard changes?",
                                                                         true,
                                                                         null,
                                                                         CcddDbManagerDialog.this)))
                            {
                                // Rename the project
                                dbControl.renameDatabaseInBackground(getRadioButtonSelected(),
                                                                     nameFld.getText(),
                                                                     descriptionFld.getText());
                            }

                            break;

                        case COPY:
                            // Display the copy project dialog. If the currently open database is
                            // being copied and there are uncommitted changes then confirm
                            // discarding the changes before proceeding
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Copy Project",
                                                  DialogOption.COPY_OPTION,
                                                  true) == OK_BUTTON
                                && (!getRadioButtonSelected().equals(dbControl.getDatabaseName())
                                    || (getRadioButtonSelected().equals(dbControl.getDatabaseName())
                                        && ccddMain.ignoreUncommittedChanges("Copy Project",
                                                                             "Discard changes?",
                                                                             true,
                                                                             null,
                                                                             CcddDbManagerDialog.this))))
                            {
                                // Copy the project
                                dbControl.copyDatabaseInBackground(getRadioButtonSelected(),
                                                                   nameFld.getText(),
                                                                   descriptionFld.getText());
                            }

                            break;

                        case DELETE:
                            // Display the project deletion dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Delete Project(s)",
                                                  DialogOption.DELETE_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Step through each selected project name
                                for (String name : getCheckBoxSelected())
                                {
                                    // Delete the project
                                    dbControl.deleteDatabaseInBackground(name);
                                }
                            }

                            break;

                        case UNLOCK:
                            // Display the project unlock dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Unlock Project(s)",
                                                  DialogOption.UNLOCK_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Step through each selected project name
                                for (String name : getCheckBoxSelected())
                                {
                                    // Unlock the project
                                    dbControl.setDatabaseLockStatus(name, false);
                                }
                            }

                            break;

                        case OWNER:
                            // Display the change project owner dialog
                            if (showOptionsDialog(ccddMain.getMainFrame(),
                                                  selectPnl,
                                                  "Change Project Owner",
                                                  DialogOption.OWNER_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Change the project owner
                                dbControl.changeProjectOwner(getRadioButtonSelected(0),
                                                             currentOwner,
                                                             getRadioButtonSelected(1),
                                                             CcddDbManagerDialog.this);
                            }

                            break;

                        case ACCESS:
                            // Display the user access level editor dialog
                            showOptionsDialog(ccddMain.getMainFrame(),
                                              selectPnl,
                                              buttonPnl,
                                              btnClose,
                                              DIALOG_TITLE,
                                              true);
                            break;
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Display the project database selection error dialog
     *
     * @param action
     *            database operation
     *********************************************************************************************/
    private void displayDatabaseError(String action)
    {
        // Inform the user that no project database exists on the server for which the user has
        // access
        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                  "<html><b>No project exists for which user '</b>"
                                                                           + dbControl.getUser()
                                                                           + "<b>' has access",
                                                  action + " Project",
                                                  JOptionPane.WARNING_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
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
     *********************************************************************************************/
    private void addDatabaseInputFields(String nameText,
                                        JPanel dialogPnl,
                                        boolean enabled,
                                        GridBagConstraints dialogGbc)
    {
        // Create a border for the fields
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
                                                        GridBagConstraints.REMAINDER,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.HORIZONTAL,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create the name label and field
        JLabel nameLbl = new JLabel(nameText);
        nameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        nameFld = new JTextField("", 20);
        nameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        nameFld.setBackground(enabled
                                      ? ModifiableColorInfo.INPUT_BACK.getColor()
                                      : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        nameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        nameFld.setEditable(enabled);
        nameFld.setBorder(border);

        // Create the description label and field
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setBackground(enabled
                                             ? ModifiableColorInfo.INPUT_BACK.getColor()
                                             : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(enabled);
        descriptionFld.setLineWrap(true);
        descriptionFld.setBorder(BorderFactory.createEmptyBorder());
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(enabled
                                             ? ModifiableColorInfo.INPUT_BACK.getColor()
                                             : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descScrollPane.setBorder(border);

        // Add the name and description labels and fields to a panel
        JPanel nameDescPnl = new JPanel(new GridBagLayout());
        nameDescPnl.add(nameLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        nameDescPnl.add(nameFld, gbc);
        gbc.gridy++;
        gbc.insets.left = 0;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        nameDescPnl.add(descriptionLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        nameDescPnl.add(descScrollPane, gbc);

        // Add the panel to the dialog panel
        dialogGbc.gridx = 0;
        dialogGbc.gridy++;
        dialogGbc.gridwidth = GridBagConstraints.REMAINDER;
        dialogGbc.fill = GridBagConstraints.HORIZONTAL;
        dialogGbc.insets.bottom = 0;
        dialogPnl.add(nameDescPnl, dialogGbc);

        // Add a listener for radio button selection change events
        addPropertyChangeListener(new PropertyChangeListener()
        {
            /**************************************************************************************
             * Handle a radio button selection change event
             *************************************************************************************/
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
                        // Get the name of the selected database and assume the description is
                        // blank
                        String name = pce.getNewValue().toString();
                        String desc = "";

                        // Step through each item in the list
                        for (String[] data : arrayItemData)
                        {
                            // Check if the item matches the selected one
                            if (data[DB_PRJNAME].equals(name))
                            {
                                // Store the item description (without the administrator name(s))
                                // and stop searching
                                desc = data[DB_INFO].replaceFirst("\\s*"
                                                                  + Pattern.quote(ADMIN_LIST)
                                                                  + ".*\\]$",
                                                                  "");
                                break;
                            }
                        }

                        // Check if this is a copy type dialog
                        if (dialogType == DbManagerDialogType.COPY)
                        {
                            // Append text to the name to differentiate the copy from the original
                            name += COPY_APPEND;
                        }

                        // Place the type name in the name field and the description in the
                        // description field
                        nameFld.setText(name);
                        descriptionFld.setText(desc);

                        // Set a flag that indicates if the name field can be changed (the name
                        // can't be changed in the rename dialog)
                        boolean isAlterable = dialogType != DbManagerDialogType.RENAME
                                              || !name.equals(dbControl.getProjectName());

                        // Set the enable state for the input fields
                        nameFld.setEditable(isAlterable);
                        nameFld.setBackground(isAlterable
                                                          ? ModifiableColorInfo.INPUT_BACK.getColor()
                                                          : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
                        descriptionFld.setEditable(true);
                        descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                        descScrollPane.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());

                        // Check if this is a copy type dialog
                        if (dialogType == DbManagerDialogType.COPY)
                        {
                            // Enable the date and time stamp check box
                            stampChkBx.setEnabled(true);
                        }
                    }
                }
            }
        });
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
                case CREATE:
                case RENAME:
                case COPY:
                    // Remove any excess white space
                    nameFld.setText(nameFld.getText().trim());

                    // Check that a project or owner is selected
                    if (getRadioButtonSelected() == null)
                    {
                        // Set the target, project or owner, depending on the dialog type
                        String target = (dialogType == DbManagerDialogType.CREATE)
                                                                                   ? " Owner"
                                                                                   : "";

                        // Inform the user that the project or owner isn't selected
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

                    // Check if the name contains an illegal character
                    if (nameFld.getText().contains(DATABASE_COMMENT_SEPARATOR))
                    {
                        // Inform the user that the name is invalid
                        throw new CCDDException("Illegal character(s) in project name");
                    }

                    // Convert the project name into its database form
                    String databaseName = dbControl.convertProjectNameToDatabase(nameFld.getText());

                    // Check that the selected database is not the currently open one (only the
                    // description can be altered for this case)
                    if (!getRadioButtonSelected().equals(dbControl.getProjectName()))
                    {
                        // Get the list of available databases
                        String[] databases = dbControl.queryDatabaseList(CcddDbManagerDialog.this);

                        // Step through each of the database names
                        for (int index = 0; index < databases.length; index++)
                        {
                            // Check if the user-supplied name matches an existing database name
                            if (databases[index].split(DATABASE_COMMENT_SEPARATOR, 2)[0].equals(databaseName))
                            {
                                // Inform the user that the name is already in use
                                throw new CCDDException("Project name already in use");
                            }
                        }
                    }

                    break;

                case OPEN:
                    // Check that a database other than the currently open one is selected
                    if (getRadioButtonSelected() == null
                        || getRadioButtonSelected().equals(dbControl.getDatabaseName()))
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
                        throw new CCDDException("Must select a project to " + action);
                    }

                    break;

                case OWNER:
                    // Check that a project and owner are selected
                    if (getRadioButtonSelected(0) == null || getRadioButtonSelected(1) == null)
                    {
                        // Inform the user that the project and/or owner isn't selected
                        throw new CCDDException("Project and owner must be selected");
                    }

                    // Step through the available project names and owners
                    for (int index = 0; index < arrayItemData.length; index++)
                    {
                        // Check if the selected owner matches
                        if (arrayItemData[index][DB_PRJNAME].equals(getRadioButtonSelected(0)))
                        {
                            // Check that the current and new owner names are the same
                            if (arrayItemData[index][DB_INFO].equals(getRadioButtonSelected(1)))
                            {
                                // Inform the user that the a different owner must be selected
                                throw new CCDDException("New project owner must be selected");
                            }

                            // Store the matching project's owner and stop searching
                            currentOwner = arrayItemData[index][DB_INFO];
                            break;
                        }
                    }

                    break;

                case ACCESS:
                    break;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
                                                      "<html><b>" + ce.getMessage(),
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }
        catch (Exception e)
        {
            // Inform the user than an unexpected error occurred
            CcddUtilities.displayException(e, CcddDbManagerDialog.this);
            isValid = false;
        }

        return isValid;
    }

    /**********************************************************************************************
     * Create the user access level table
     *
     * @return Reference to the scroll pane in which the table is placed
     *********************************************************************************************/
    private JScrollPane createUserAccesslevelTable()
    {
        // Define the user access level editor JTable
        accessTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow multiple line display in all columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == AccessLevelEditorColumnInfo.OID.ordinal();
            }

            /**************************************************************************************
             * Override isCellEditable so that all columns can be edited except those for the
             * current user
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return !getModel().getValueAt(convertRowIndexToModel(row),
                                              AccessLevelEditorColumnInfo.USER_NAME.ordinal())
                                  .toString().equals(dbControl.getUser());
            }

            /**************************************************************************************
             * Allow pasting data into the user access level cells except those for the current
             * user
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                return isCellEditable(convertRowIndexToView(row),
                                      convertColumnIndexToView(column));
            }

            /**************************************************************************************
             * Override getCellEditor so that for a user name or access level column cell the
             * correct combo box cell editor is returned
             *
             * @param row
             *            table view row number
             *
             * @param column
             *            table view column number
             *
             * @return The cell editor for the specified row and column
             *************************************************************************************/
            @Override
            public TableCellEditor getCellEditor(int row, int column)
            {
                // Get the editor for this cell
                TableCellEditor cellEditor = super.getCellEditor(row, column);

                // Convert the row and column indices to the model coordinates
                int modelColumn = convertColumnIndexToModel(column);

                // Check if the column for which the cell editor is requested is the user name
                // column
                if (modelColumn == AccessLevelEditorColumnInfo.USER_NAME.ordinal())
                {
                    // Select the combo box cell editor that displays the user names
                    cellEditor = userNameCellEditor;
                }
                // Check if the column for which the cell editor is requested is the access level
                // column
                else if (modelColumn == AccessLevelEditorColumnInfo.ACCESS_LEVEL.ordinal())
                {
                    // Select the combo box cell editor that displays the access level
                    cellEditor = accessLevelCellEditor;
                }

                return cellEditor;
            }

            /**************************************************************************************
             * Validate changes to the editable cells
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param row
             *            table model row number
             *
             * @param column
             *            table model column number
             *
             * @param oldValue
             *            original cell contents
             *
             * @param newValue
             *            new cell contents
             *
             * @param showMessage
             *            true to display the invalid input dialog, if applicable
             *
             * @param isMultiple
             *            true if this is one of multiple cells to be entered and checked; false if
             *            only a single input is being entered
             *
             * @return Always returns false
             *************************************************************************************/
            @Override
            protected Boolean validateCellContent(List<Object[]> tableData,
                                                  int row,
                                                  int column,
                                                  Object oldValue,
                                                  Object newValue,
                                                  Boolean showMessage,
                                                  boolean isMultiple)
            {
                // Reset the flag that indicates the last edited cell's content is invalid
                setLastCellValid(true);

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the user name has been changed
                        if (column == AccessLevelEditorColumnInfo.USER_NAME.ordinal())
                        {
                            // Compare this user name to the others in the table in order to avoid
                            // assigning a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being edited, and if the user
                                // name matches the one being added
                                if (otherRow != row
                                    && newValueS.equals(tableData.get(otherRow)[AccessLevelEditorColumnInfo.USER_NAME.ordinal()].toString()))
                                {
                                    throw new CCDDException("User name already in use");
                                }
                            }

                            // Store the new value in the table data array after formatting the
                            // cell value
                            newValue = newValueS;
                            tableData.get(row)[column] = newValueS;
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
                                                                  "<html><b>" + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    accessTable.getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /**************************************************************************************
             * Load the table user access level definition values into the table and format the
             * table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(committedData,
                                            AccessLevelEditorColumnInfo.getColumnNames(),
                                            null,
                                            AccessLevelEditorColumnInfo.getToolTips(),
                                            true,
                                            true,
                                            true);
            }

            /**************************************************************************************
             * Override prepareRenderer to allow adjusting the background colors of table cells
             *************************************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
            {
                JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

                // Check if the cell isn't already selected (selection highlighting overrides the
                // invalid highlighting, if applicable)
                if (!(isFocusOwner()
                      && isRowSelected(row)
                      && (isColumnSelected(column) || !getColumnSelectionAllowed())))
                {
                    // Check if the cell is required and is empty
                    if (AccessLevelEditorColumnInfo.values()[accessTable.convertColumnIndexToModel(column)].isRequired()
                        && accessTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                    }
                    // Check if the cell is protected from being changed
                    else if (!isCellEditable(row, column))
                    {
                        // Change the cell's text and background colors
                        comp.setForeground(ModifiableColorInfo.PROTECTED_TEXT.getColor());
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                    }
                }

                return comp;
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method to produce an array containing empty values
             * for a new row in this table
             *
             * @return Array containing blank cell values for a new row
             *************************************************************************************/
            @Override
            protected Object[] getEmptyRow()
            {
                return AccessLevelEditorColumnInfo.getEmptyRow();
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method so that the current user's row can't be
             * deleted
             *************************************************************************************/
            @Override
            protected void deleteRow(boolean endEdit)
            {
                // Check if at least one row is selected
                if (getSelectedRow() != -1)
                {
                    // Step through each selected row
                    for (int row : getSelectedRows())
                    {
                        // Check if this row is for the current user
                        if (!isCellEditable(row, AccessLevelEditorColumnInfo.USER_NAME.ordinal()))
                        {
                            // Deselect the row. The current user isn't allowed to alter or delete
                            // their own access level
                            removeRowSelectionInterval(row, row);
                        }
                    }

                    // Delete any remaining selected row(s)
                    super.deleteRow(endEdit);
                }
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether or not any unstored changes
                // exist
                setTitle(DIALOG_TITLE
                         + (accessTable.isTableChanged(committedData,
                                                       Arrays.asList(new Integer[] {AccessLevelEditorColumnInfo.OID.ordinal()}))
                                                                                                                                 ? CHANGE_INDICATOR
                                                                                                                                 : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(accessTable);

        // Set common table parameters and characteristics
        accessTable.setFixedCharacteristics(scrollPane,
                                            true,
                                            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                            TableSelectionMode.SELECT_BY_CELL,
                                            false,
                                            ModifiableColorInfo.TABLE_BACK.getColor(),
                                            true,
                                            true,
                                            ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                            true);

        return scrollPane;
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of user access level updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doAccessUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            String admins = "";

            // Update the copy of the user access level data so it can be used to determine if
            // changes are made
            storeCurrentData();

            // Step through each user and access level
            for (String[] userAccess : committedData)
            {
                // Check if the user has administrative access
                if (userAccess[AccessLevelEditorColumnInfo.ACCESS_LEVEL.ordinal()].equals(AccessLevel.ADMIN.getDisplayName()))
                {
                    // Add the user's name to the string
                    admins += userAccess[AccessLevelEditorColumnInfo.USER_NAME.ordinal()]
                              + DATABASE_ADMIN_SEPARATOR;
                }
            }

            // Remove the trailing separator and update the project database comment with the
            // administrator names
            admins = CcddUtilities.removeTrailer(admins, DATABASE_ADMIN_SEPARATOR);
            dbControl.setDatabaseAdmins(admins, CcddDbManagerDialog.this);

            // Accept all edits for this table
            accessTable.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Copy the user access level data so it can be used to determine if changes are made
     *********************************************************************************************/
    private void storeCurrentData()
    {
        // Store the user access level information
        committedData = dbTable.retrieveInformationTable(InternalTable.USERS,
                                                         true,
                                                         CcddDbManagerDialog.this)
                               .toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Handle the dialog close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the editor table is validated and that
        // there are changes that haven't been stored. If changes exist then confirm discarding the
        // changes
        if (accessTable == null ||
            (accessTable.isLastCellValid()
             && (!accessTable.isTableChanged(committedData,
                                             Arrays.asList(new Integer[] {AccessLevelEditorColumnInfo.OID.ordinal()}))
                 || new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
                                                              "<html><b>Discard changes?",
                                                              "Discard Changes",
                                                              JOptionPane.QUESTION_MESSAGE,
                                                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)))
        {
            // Close the dialog
            closeDialog();

            // Clear the modal dialog references in the keyboard handler
            ccddMain.getKeyboardHandler().setModalDialogReference(null, null);
        }
    }

    /**********************************************************************************************
     * Create the cell editors for the user names and access levels
     *********************************************************************************************/
    private void createCellEditors()
    {
        // Create a combo box for displaying the user names
        final PaddedComboBox userComboBox = new PaddedComboBox(accessTable.getFont());

        // Step through each user name
        for (String user : dbControl.queryUserList(CcddDbManagerDialog.this))
        {
            // Check if the user name doesn't match the current user name
            if (!user.equals(dbControl.getUser()))
            {
                // Add the user name to the list
                userComboBox.addItem(user);
            }
        }

        // Enable item matching for the combo box
        userComboBox.enableItemMatching(accessTable);

        // Create the cell editor for access levels
        userNameCellEditor = new ComboBoxCellEditor(userComboBox);

        // Create a combo box for displaying the access levels
        final PaddedComboBox accessComboBox = new PaddedComboBox(accessTable.getFont());

        // Step through each access level
        for (AccessLevel level : AccessLevel.values())
        {
            // Add the access level to the list
            accessComboBox.addItem(level.getDisplayName());
        }

        // Enable item matching for the combo box
        accessComboBox.enableItemMatching(accessTable);

        // Create the cell editor for user names
        accessLevelCellEditor = new ComboBoxCellEditor(accessComboBox);
    }

    /**********************************************************************************************
     * Get the updated user access level data
     *
     * @return List containing the updated user access level data
     *********************************************************************************************/
    private List<String[]> getUpdatedData()
    {
        return Arrays.asList(CcddUtilities.convertObjectToString(accessTable.getTableData(true)));
    }

    /**********************************************************************************************
     * Check that a row with contains data in the required columns
     *
     * @return true if a row is missing data in a required column
     *********************************************************************************************/
    private boolean checkForMissingColumns()
    {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Step through each row in the table
        for (int row = 0; row < accessTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = accessTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < accessTable.getRowCount())
            {
                // Step through each column in the row
                for (int column = 0; column < accessTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (AccessLevelEditorColumnInfo.values()[column].isRequired()
                        && accessTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddDbManagerDialog.this,
                                                                      "<html><b>Data must be provided for column '</b>"
                                                                                                + accessTable.getColumnName(column)
                                                                                                + "<b>' [row </b>"
                                                                                                + (row + 1)
                                                                                                + "<b>]",
                                                                      "Missing Data",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
                        {
                            // Set the stop flag to prevent further error checking
                            stopCheck = true;
                        }

                        break;
                    }
                }
            }
        }

        return dataIsMissing;
    }

    /**********************************************************************************************
     * Get the array of roles registered on the server
     *
     * @return Two-dimensional array where the first dimension contains the available roles and the
     *         second dimension is null
     *********************************************************************************************/
    private String[][] getRoleInformation()
    {
        // Get the array containing the roles
        String[] roles = dbControl.queryRoleList(CcddDbManagerDialog.this);
        String[][] roleInfo = new String[roles.length][2];

        // Step through each role
        for (int index = 0; index < roles.length; index++)
        {
            // Store the role name
            roleInfo[index][0] = roles[index];
        }

        return roleInfo;
    }

    /**********************************************************************************************
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
     *********************************************************************************************/
    private void getDatabaseInformation(boolean isOnlyUnlocked,
                                        boolean isOnlyLocked,
                                        String enabledItem)
    {
        int index = 0;
        disabledItems = new ArrayList<Integer>();
        String[] activeUsers = null;
        List<String> adminAccess = null;

        // Check if this is an unlock dialog
        if (dialogType == DbManagerDialogType.UNLOCK)
        {
            // Get the list of users of databases with active connections
            activeUsers = dbControl.queryActiveList(ccddMain.getMainFrame());
        }

        // Get the array containing the database name, lock status, visible (project) name, project
        // creator, and description for each project to which the user has access
        String[] userDatabaseInformation = dbControl.queryDatabaseByUserList(ccddMain.getMainFrame(),
                                                                             dbControl.getUser());
        arrayItemData = new String[userDatabaseInformation.length][3];

        // Check if this is the database rename, copy, delete, or change owner dialog
        if (dialogType == DbManagerDialogType.RENAME
            || dialogType == DbManagerDialogType.COPY
            || dialogType == DbManagerDialogType.DELETE
            || dialogType == DbManagerDialogType.OWNER)
        {
            // Get the list of databases for which the current user has administrative access
            adminAccess = dbControl.getUserAdminAccess();

            // Check if the is the database copy operation and the user has at least read/write
            // access
            if (dialogType == DbManagerDialogType.COPY
                && dbControl.isAccessReadWrite()
                && !adminAccess.contains(dbControl.getDatabaseName()))
            {
                // Add the current database to the list so that user's with read/write access can
                // make a copy
                adminAccess.add(dbControl.getDatabaseName());
            }
        }

        // Step through each database comment
        for (String userDbInfo : userDatabaseInformation)
        {
            // Separate the information retrieved into the database name and its comment, then
            // parse the comment into its separate fields
            String[] nameAndComment = userDbInfo.split(DATABASE_COMMENT_SEPARATOR, 2);
            String comment[] = dbControl.parseDatabaseComment(nameAndComment[0],
                                                              nameAndComment[1]);

            // Get the lock status
            boolean isLocked = comment[DatabaseComment.LOCK_STATUS.ordinal()].equals("1");
            boolean isDisabled = false;

            // Check if the database is locked and that locked databases are to be disabled, if the
            // database is unlocked and that unlocked databases are to be disabled, if the item is
            // not specified as enabled, and this is not the rename or change owner dialog and not
            // the currently open project
            if (((isOnlyUnlocked && isLocked)
                 || (isOnlyLocked && !isLocked))
                && !comment[DatabaseComment.PROJECT_NAME.ordinal()].equals(enabledItem)
                && ((dialogType != DbManagerDialogType.RENAME && dialogType != DbManagerDialogType.OWNER)
                    || !dbControl.getProjectName().equals(comment[DatabaseComment.PROJECT_NAME.ordinal()])))
            {
                // Add the index of the database to the disabled list
                disabledItems.add(index);
                isDisabled = true;
            }

            // Check if the database isn't already disabled, this is a rename or delete dialog, and
            // the user doesn't have administrative access to the database
            if (!isDisabled && (adminAccess != null && !adminAccess.contains(nameAndComment[0])))
            {
                // Add the index of the database to the disabled list
                disabledItems.add(index);
            }

            // Store the database name as the project name
            arrayItemData[index][DB_PRJNAME] = comment[DatabaseComment.PROJECT_NAME.ordinal()];

            // Check if this is an unlock dialog. The database description is replaced by the
            // database locked/unlocked status and the attached users
            if (dialogType == DbManagerDialogType.UNLOCK)
            {
                String status = "";

                // Step through the list of databases and attached users
                for (String active : activeUsers)
                {
                    // Separate the database name from the user name
                    String[] databaseAndUser = active.split(",");

                    // Check if the database name matches the one in the list
                    if (dbControl.convertProjectNameToDatabase(arrayItemData[index][DB_PRJNAME]).equals(databaseAndUser[0]))
                    {
                        // Append the user name to the status text
                        status += databaseAndUser[1] + ", ";
                    }
                }

                status = CcddUtilities.removeTrailer(status, ", ");

                // Check if the database is locked
                if (isLocked)
                {
                    // Use a dummy name if no user is identified
                    status = status.isEmpty()
                                              ? "*unknown*"
                                              : status;

                    // Check if this is the currently open database
                    if (arrayItemData[index][DB_PRJNAME].equals(dbControl.getDatabaseName()))
                    {
                        status = "Current; in use by " + status;
                    }
                    // Not the currently open database
                    else
                    {
                        status = "Locked by " + status;
                    }
                }
                // The database is not locked; check if no users are attached (via other
                // applications than CCDD)
                else if (status.isEmpty())
                {
                    status = "Unlocked";
                }
                // The database is not locked and no users are attached
                else
                {
                    status = "Unlocked; in use by " + status;
                }

                // Store the lock status
                arrayItemData[index][DB_INFO] = status;
            }
            // Check if this is the change owner dialog
            else if (dialogType == DbManagerDialogType.OWNER)
            {
                // Set the information field to the database owner
                arrayItemData[index][DB_INFO] = dbControl.getProjectOwner(comment[DatabaseComment.PROJECT_NAME.ordinal()],
                                                                          ccddMain.getMainFrame());
            }
            // Not the unlock or change owner dialog
            else
            {
                // Set the information field to the database description
                arrayItemData[index][DB_INFO] = comment[DatabaseComment.DESCRIPTION.ordinal()];

                // Check if the comment contains the project administrator(s)
                if (!comment[DatabaseComment.ADMINS.ordinal()].isEmpty())
                {
                    // Append the project administrator(s) to the information field
                    arrayItemData[index][DB_INFO] += (arrayItemData[index][DB_INFO].isEmpty()
                                                                                              ? ""
                                                                                              : "\n")
                                                     + ADMIN_LIST
                                                     + comment[DatabaseComment.ADMINS.ordinal()].replaceAll(DATABASE_ADMIN_SEPARATOR,
                                                                                                            ", ")
                                                     + "]";
                }
            }

            index++;
        }
    }
}
