/**
 * CFS Command and Data Dictionary script executive dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.EXECUTE_ICON;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary script executive dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddScriptExecutiveDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddScriptHandler scriptHandler;
    private CcddTableTreeHandler tableTree;

    // Components referenced by multiple methods
    private JButton btnExecute;
    private JButton btnClose;

    /**********************************************************************************************
     * Script executive dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddScriptExecutiveDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create reference to shorten subsequent calls
        scriptHandler = ccddMain.getScriptHandler();

        // Set the reference to the script associations executive in the script handler
        scriptHandler.setScriptDialog(this);

        // Create the script executive dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the script executive dialog. This is executed in a separate thread since it can take
     * a noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Check if there are any open editors with uncommitted changes and if so check that the
        // user confirms ignoring the changes
        if (ccddMain.ignoreUncommittedChanges("Script Executive",
                                              "Ignore changes?",
                                              false,
                                              null,
                                              ccddMain.getMainFrame()))
        {
            // Build the script executive dialog in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                // Create panels to hold the components of the dialog
                JPanel dialogPnl = new JPanel(new GridBagLayout());
                JPanel buttonPnl = new JPanel();

                /**********************************************************************************
                 * Build the script executive dialog
                 *********************************************************************************/
                @Override
                protected void execute()
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
                                                                               ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                               0,
                                                                               ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                                    0,
                                                                    0);

                    dialogPnl.setBorder(BorderFactory.createEmptyBorder());

                    // Create the table group selection dialog labels and fields
                    JLabel scriptLbl = new JLabel("Select script association(s) to execute");
                    scriptLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                    dialogPnl.add(scriptLbl, gbc);

                    // Create the list to display the stored script associations and add it to the
                    // dialog
                    gbc.weighty = 1.0;
                    gbc.insets.top = 0;
                    gbc.gridy++;
                    dialogPnl.add(scriptHandler.getAssociationsPanel(null,
                                                                     false,
                                                                     CcddScriptExecutiveDialog.this),
                                  gbc);

                    // Execute selected script association(s) button
                    btnExecute = CcddButtonPanelHandler.createButton("Execute",
                                                                     EXECUTE_ICON,
                                                                     KeyEvent.VK_E,
                                                                     "Execute the selected script association(s)");

                    // Add a listener for the Execute button
                    btnExecute.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Execute the selected script association(s)
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Execute the selected associations
                            scriptHandler.executeScriptAssociations(tableTree,
                                                                    CcddScriptExecutiveDialog.this);
                        }
                    });

                    // Close button
                    btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                   CLOSE_ICON,
                                                                   KeyEvent.VK_C,
                                                                   "Close the script executive");

                    // Add a listener for the Close button
                    btnClose.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Close the script execution dialog
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Reset the reference to the script associations executive in the
                            // script handler since the handler remains active)
                            scriptHandler.setScriptDialog(null);

                            closeFrame();
                        }
                    });

                    // Add buttons to the button panel
                    buttonPnl.add(btnExecute);
                    buttonPnl.add(btnClose);
                }

                /**********************************************************************************
                 * Script executive dialog creation complete
                 *********************************************************************************/
                @Override
                protected void complete()
                {
                    // Display the script execution dialog
                    createFrame(ccddMain.getMainFrame(),
                                dialogPnl,
                                buttonPnl,
                                btnExecute,
                                "Execute Script Association(s)",
                                null);
                }
            });
        }
    }

    /**********************************************************************************************
     * Update the associations table with the latest script associations information
     *********************************************************************************************/
    protected void reloadAssociationsTable()
    {
        scriptHandler.getAssociationsTable().loadAndFormatData();
    }
}
