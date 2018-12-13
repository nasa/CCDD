/**
 * CFS Command and Data Dictionary script storage dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPTS_ICON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.ScriptIOType;

/**************************************************************************************************
 * CFS Command and Data Dictionary script storage dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddScriptStorageDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddScriptHandler scriptHandler;
    private final CcddFileIOHandler fileIOHandler;

    // Components referenced by multiple methods
    private final ScriptIOType dialogType;

    // Array of file references containing the selected script file(s) (store) or the selected
    // script file path (retrieve)
    private FileEnvVar[] scriptFile;

    // Path selection field
    private JTextField pathFld;

    /**********************************************************************************************
     * Script storage dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param dialogType
     *            ScriptIOType.STORE or ScriptIOType.RETRIEVE
     *********************************************************************************************/
    CcddScriptStorageDialog(CcddMain ccddMain, ScriptIOType dialogType)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        scriptHandler = ccddMain.getScriptHandler();
        fileIOHandler = ccddMain.getFileIOHandler();

        // Create the script storage dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the script storage dialog
     *********************************************************************************************/
    private void initialize()
    {
        // Create the dialog based on the supplied dialog type
        switch (dialogType)
        {
            case STORE:
                // Allow the user to select the script file path(s) + name(s)
                scriptFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                    ccddMain.getMainFrame(),
                                                                    null,
                                                                    "script",
                                                                    scriptHandler.getExtensions(),
                                                                    true,
                                                                    "Select Script(s) to Store",
                                                                    ccddMain.getProgPrefs().get(ModifiablePathInfo.SCRIPT_PATH.getPreferenceKey(),
                                                                                                null),
                                                                    DialogOption.STORE_OPTION);

                // Check if the Cancel button wasn't selected
                if (scriptFile != null)
                {
                    // Check that a file is selected
                    if (scriptFile[0] != null && scriptFile[0].isFile())
                    {
                        // Remove the script file name and store the script file path in the
                        // program preferences backing store
                        CcddFileIOHandler.storePath(ccddMain,
                                                    scriptFile[0].getAbsolutePathWithEnvVars(),
                                                    true,
                                                    ModifiablePathInfo.SCRIPT_PATH);

                        // Step through each selected script
                        for (FileEnvVar file : scriptFile)
                        {
                            // Check if the script by this name isn't already stored or is the user
                            // elects to overwrite the existing script table
                            if (!dbTable.isTableExists(InternalTable.SCRIPT.getTableName(file.getName()),
                                                       CcddScriptStorageDialog.this)
                                || new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                             "<html><b>Overwrite existing stored script '</b>"
                                                                                                      + file.getName()
                                                                                                      + "<b>'?",
                                                                             "Overwrite Script",
                                                                             JOptionPane.QUESTION_MESSAGE,
                                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                            {
                                // Store the script in the database
                                fileIOHandler.storeScriptInDatabase(file);
                            }
                        }
                    }
                    // No script file is selected
                    else
                    {
                        // Inform the user that a script file must be selected
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Must select a script to store",
                                                                  "Store Script(s)",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }

                break;

            case RETRIEVE:
            case DELETE:
                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                1,
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

                // Create the panel to contain the dialog components
                JPanel dialogPnl = new JPanel(new GridBagLayout());
                dialogPnl.setBorder(BorderFactory.createEmptyBorder());

                // Get the list of script files from the database
                String[] scripts = dbTable.queryScriptTables(ccddMain.getMainFrame());
                String[][] checkBoxData = new String[scripts.length][];
                int index = 0;

                // Step through each script file
                for (String script : scripts)
                {
                    // Separate and store the database name and description
                    checkBoxData[index] = script.split(",", 2);
                    index++;
                }

                // Create a panel containing a grid of check boxes representing the scripts from
                // which to choose
                if (addCheckBoxes(null, checkBoxData, null, "Select script(s)", false, dialogPnl))
                {
                    // Check if more than one data field name check box exists
                    if (getCheckBoxes().length > 2)
                    {
                        // Create a Select All check box
                        final JCheckBox selectAllCb = new JCheckBox("Select all scripts", false);
                        selectAllCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                        selectAllCb.setBorder(BorderFactory.createEmptyBorder());

                        // Create a listener for changes to the Select All check box selection
                        // status
                        selectAllCb.addActionListener(new ActionListener()
                        {
                            /**********************************************************************
                             * Handle a change to the Select All check box selection status
                             *********************************************************************/
                            @Override
                            public void actionPerformed(ActionEvent ae)
                            {
                                // Step through each script name check box
                                for (JCheckBox scriptCb : getCheckBoxes())
                                {
                                    // Set the check box selection status to match the Select All
                                    // check box selection status
                                    scriptCb.setSelected(selectAllCb.isSelected());
                                }
                            }
                        });

                        // Add the Select All checkbox to the dialog panel
                        gbc.gridx = 0;
                        gbc.gridy++;
                        gbc.insets.bottom = 0;
                        dialogPnl.add(selectAllCb, gbc);

                        // Step through each script name check box
                        for (JCheckBox columnCb : getCheckBoxes())
                        {
                            // Create a listener for changes to the script name check box selection
                            // status
                            columnCb.addActionListener(new ActionListener()
                            {
                                /******************************************************************
                                 * Handle a change to the column name check box selection status
                                 *****************************************************************/
                                @Override
                                public void actionPerformed(ActionEvent ae)
                                {
                                    int columnCount = 0;

                                    // Step through each script name check box
                                    for (int index = 0; index < getCheckBoxes().length; index++)
                                    {
                                        // Check if the check box is selected
                                        if (getCheckBoxes()[index].isSelected())
                                        {
                                            // Increment the counter to track the number of
                                            // selected script name check boxes
                                            columnCount++;
                                        }
                                    }

                                    // Set the Select All check box status based on if all the
                                    // script name check boxes are selected
                                    selectAllCb.setSelected(columnCount == getCheckBoxes().length);
                                }
                            });
                        }
                    }

                    // Check if one or more scripts is to be retrieved
                    if (dialogType == ScriptIOType.RETRIEVE)
                    {
                        // Add the script path selection components to the dialog
                        gbc.gridy++;
                        gbc.insets.bottom = 0;
                        dialogPnl.add(createPathSelectionPanel(), gbc);

                        // Display the script retrieval dialog
                        if (showOptionsDialog(ccddMain.getMainFrame(),
                                              dialogPnl,
                                              "Retrieve Script(s)",
                                              DialogOption.RETRIEVE_OPTION,
                                              true) == OK_BUTTON)
                        {
                            // Store the script file path in the program preferences backing store
                            CcddFileIOHandler.storePath(ccddMain,
                                                        scriptFile[0].getAbsolutePathWithEnvVars(),
                                                        false,
                                                        ModifiablePathInfo.SCRIPT_PATH);

                            // Get an array containing the selected script names
                            String[] selectedScripts = getCheckBoxSelected();

                            // Step through each selected script file
                            for (String script : selectedScripts)
                            {
                                // Retrieve the selected scripts from the database and save them to
                                // the selected folder
                                fileIOHandler.retrieveScriptFromDatabase(script,
                                                                         new File(scriptFile[0].getAbsolutePath()
                                                                                  + File.separator
                                                                                  + script));
                            }
                        }
                    }
                    // One or more scripts is to be deleted
                    else
                    {
                        // Display the database deletion dialog
                        if (showOptionsDialog(ccddMain.getMainFrame(),
                                              dialogPnl,
                                              "Delete Script(s)",
                                              DialogOption.DELETE_OPTION,
                                              true) == OK_BUTTON)
                        {
                            // Get the array of selected scripts
                            String[] selectedScripts = getCheckBoxSelected();

                            // Step through each script selected
                            for (index = 0; index < selectedScripts.length; index++)
                            {
                                // Prepend the database script file identifier to the script name
                                selectedScripts[index] = InternalTable.SCRIPT.getTableName(selectedScripts[index]);
                            }

                            // Delete the selected scripts
                            dbTable.deleteTableInBackground(selectedScripts,
                                                            null,
                                                            ccddMain.getMainFrame());
                        }
                    }
                }
                // No scripts are stored in the database
                else
                {
                    // Inform the user that the project database contains no script to
                    // retrieve/delete
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Project '</b>"
                                                                                       + dbControl.getDatabaseName()
                                                                                       + "<b>' has no scripts",
                                                              (dialogType == ScriptIOType.RETRIEVE
                                                                                                   ? "Retrieve"
                                                                                                   : "Delete")
                                                                                                                + " Script(s)",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                break;
        }
    }

    /**********************************************************************************************
     * Create the path selection panel
     *
     * @return JPanel containing the script selection panel
     *********************************************************************************************/
    private JPanel createPathSelectionPanel()
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
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel for the path selection components
        JPanel pathPnl = new JPanel(new GridBagLayout());

        // Create the path selection dialog labels and fields
        JLabel scriptLbl = new JLabel("Enter or select a script storage path");
        scriptLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        pathPnl.add(scriptLbl, gbc);

        // Create a text field for entering & displaying the script path
        pathFld = new JTextField(ModifiablePathInfo.SCRIPT_PATH.getPath().replaceAll("\\"
                                                                                     + File.separator
                                                                                     + "\\.$", ""));
        pathFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        pathFld.setEditable(true);
        pathFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        pathFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        pathFld.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                             Color.LIGHT_GRAY,
                                                                                             Color.GRAY),
                                                             BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                             ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                             ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                             ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.gridy++;
        pathPnl.add(pathFld, gbc);

        // Create a button for choosing an output path
        JButton btnSelectPath = CcddButtonPanelHandler.createButton("Select...",
                                                                    SCRIPTS_ICON,
                                                                    KeyEvent.VK_S,
                                                                    "Open the script path selection dialog");

        // Add a listener for the Select path button
        btnSelectPath.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select a script storage path
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Allow the user to select the script storage path
                scriptFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                    CcddScriptStorageDialog.this,
                                                                    "Select Location for Script(s)",
                                                                    ccddMain.getProgPrefs().get(ModifiablePathInfo.SCRIPT_PATH.getPreferenceKey(),
                                                                                                null),
                                                                    DialogOption.OK_CANCEL_OPTION);

                // Check if a script path is selected
                if (scriptFile != null && scriptFile[0] != null)
                {
                    // Display the path name in the script path field
                    pathFld.setText(scriptFile[0].getAbsolutePathWithEnvVars());
                }
            }
        });

        // Add the select script button to the dialog
        gbc.weightx = 0.0;
        gbc.insets.right = 0;
        gbc.gridx++;
        pathPnl.add(btnSelectPath, gbc);

        return pathPnl;
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
                case RETRIEVE:
                    // Check that a script is selected
                    if (getCheckBoxSelected().length == 0)
                    {
                        // Inform the user that a script must be selected
                        throw new CCDDException("Must select a script to retrieve");
                    }

                    // Check if no script file path is selected via the selection button
                    if (scriptFile == null)
                    {
                        // Get a file reference using the file path entered into the path field
                        scriptFile = new FileEnvVar[] {new FileEnvVar(pathFld.getText())};
                    }

                    // Check if the script file path is valid
                    if (!scriptFile[0].isDirectory())
                    {
                        scriptFile = null;

                        // Inform the user that a valid path must be selected
                        throw new CCDDException("Must select a valid path");
                    }

                    break;

                case DELETE:
                    // Check that a script is selected
                    if (getCheckBoxSelected().length == 0)
                    {
                        // Inform the user that a script must be selected
                        throw new CCDDException("Must select a script to delete");
                    }

                    break;

                case STORE:
                    break;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddScriptStorageDialog.this,
                                                      "<html><b>"
                                                                                    + ce.getMessage(),
                                                      "Missing Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
