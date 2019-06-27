/**
 * CFS Command and Data Dictionary telemetry scheduler dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.AUTO_CREATE_ICON;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.DnDTabbedPane;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.DataStream;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SchedulerType;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary telemetry scheduler dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTelemetrySchedulerDialog extends CcddDialogHandler implements CcddSchedulerDialogInterface
{
    // Main class reference
    private final CcddMain ccddMain;
    private final CcddRateParameterHandler rateHandler;
    private CcddTableTreeHandler allVariableTree;
    private final CcddSchedulerDbIOHandler schedulerDb;
    private final List<CcddSchedulerHandler> schHandlers;
    private CcddSchedulerHandler activeSchHandler;

    // Components references by multiple methods
    private JButton btnAutoFill;
    private JButton btnAssign;
    private JButton btnClearRate;
    private JButton btnClear;
    private JButton btnAddSubMessage;
    private JButton btnDeleteSubMessage;
    private JButton btnStore;
    private JButton btnClose;
    private DnDTabbedPane tabbedPane;

    // List containing the path for all nodes in the variable tree
    private List<String> allVariableTreePaths;

    /**********************************************************************************************
     * Telemetry scheduler dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddTelemetrySchedulerDialog(final CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        rateHandler = ccddMain.getRateParameterHandler();
        schedulerDb = new CcddSchedulerDbIOHandler(ccddMain,
                                                   SchedulerType.TELEMETRY_SCHEDULER,
                                                   this);
        schHandlers = new ArrayList<CcddSchedulerHandler>();

        // Create the telemetry scheduler dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the telemetry scheduler dialog. This is executed in a separate thread since it can
     * take a noticeable amount time to complete, and by using a separate thread the GUI is allowed
     * to continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the telemetry scheduler dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create a button panel
            JPanel buttonPnl = new JPanel();

            /**************************************************************************************
             * Build the telemetry scheduler dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Create a tree containing all of the variables. This is used for determining
                // bit-packing and variable relative position
                allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                           TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES,
                                                           ccddMain.getMainFrame());

                // Expand the tree so that all nodes are 'visible'
                allVariableTree.setTreeExpansion(true);

                allVariableTreePaths = new ArrayList<String>();

                // Step through all of the nodes in the variable tree
                for (Enumeration<?> element = allVariableTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
                {
                    // Convert the variable path to a string and add it to the list
                    allVariableTreePaths.add(allVariableTree.getFullVariablePath(((ToolTipTreeNode) element.nextElement()).getPath()));
                }

                // Load the stored telemetry scheduler data from the project database
                schedulerDb.loadStoredData();

                // Auto-fill button
                btnAutoFill = CcddButtonPanelHandler.createButton("Auto-fill",
                                                                  AUTO_CREATE_ICON,
                                                                  KeyEvent.VK_F,
                                                                  "Auto-fill the message table with the variables");

                // Create a listener for the Auto-fill button
                btnAutoFill.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Auto-fill the variables into the telemetry scheduler for the currently
                     * selected data stream
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Run auto-fill
                        activeSchHandler.autoFill();
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Clear Rate button
                btnClearRate = CcddButtonPanelHandler.createButton("Clear Rate",
                                                                   UNDO_ICON,
                                                                   KeyEvent.VK_R,
                                                                   "Remove the variables of the currently selected rate from all messages");

                // Add a listener for the Clear Rate button
                btnClearRate.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Remove the variables of the currently selected rate from all messages in the
                     * currently selected data stream
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeSchHandler.getSchedulerEditor().clearVariablesFromMessages(activeSchHandler.getSchedulerInput().getSelectedRate());
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Clear Msgs button
                btnClear = CcddButtonPanelHandler.createButton("Clear Msgs",
                                                               UNDO_ICON,
                                                               KeyEvent.VK_R,
                                                               "Remove the variables from all messages");

                // Add a listener for the Clear Msgs button
                btnClear.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Remove the variables from all messages in the currently selected data stream
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeSchHandler.getSchedulerEditor().clearVariablesFromMessages(null);
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Add Sub-msg button
                btnAddSubMessage = CcddButtonPanelHandler.createButton("Add Sub-msg",
                                                                       INSERT_ICON,
                                                                       KeyEvent.VK_A,
                                                                       "Add a sub-message to the currently selected message");

                // Create a listener for the Add Sub-msg button
                btnAddSubMessage.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Add a sub-message to the current message
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeSchHandler.getSchedulerEditor().addSubMessage();
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Del Sub-msg button
                btnDeleteSubMessage = CcddButtonPanelHandler.createButton("Del Sub-msg",
                                                                          DELETE_ICON,
                                                                          KeyEvent.VK_D,
                                                                          "Delete the currently selected sub-message");

                // Create a listener for the Del Sub-msg button
                btnDeleteSubMessage.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Delete the current sub-message
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeSchHandler.getSchedulerEditor().deleteSubMessage();
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Assign message names and IDs button
                btnAssign = CcddButtonPanelHandler.createButton("Assign Msgs",
                                                                AUTO_CREATE_ICON,
                                                                KeyEvent.VK_M,
                                                                "Automatically assign message names and/or IDs to the messages and sub-messages");

                // Add a listener for the Assign Msgs button
                btnAssign.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Automatically assign names and/or IDs to the telemetry messages and
                     * sub-messages
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddAssignMessageIDDialog(ccddMain,
                                                      activeSchHandler.getCurrentMessages(),
                                                      CcddTelemetrySchedulerDialog.this);
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Store button
                btnStore = CcddButtonPanelHandler.createButton("Store",
                                                               STORE_ICON,
                                                               KeyEvent.VK_S,
                                                               "Store the message updates in the project database");
                btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Add a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Store the data from the various data streams into the database
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Check if any message has changed and, if so, that the user confirms
                        // storing the changes
                        if (isChanges()
                            && new CcddDialogHandler().showMessageDialog(CcddTelemetrySchedulerDialog.this,
                                                                         "<html><b>Store changes?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Store the messages in the project database
                            storeData();
                        }
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Create a button to close the dialog
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the telemetry scheduler");

                // Add a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener()
                {
                    /******************************************************************************
                     * Close the telemetry scheduler dialog
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        windowCloseButtonAction();
                    }

                    /******************************************************************************
                     * Get the reference to the currently displayed table
                     *****************************************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return activeSchHandler.getSchedulerEditor().getTable();
                    }
                });

                // Add buttons in the order in which they'll appear (left to right, top to bottom)
                buttonPnl.add(btnAutoFill);
                buttonPnl.add(btnClearRate);
                buttonPnl.add(btnAddSubMessage);
                buttonPnl.add(btnStore);
                buttonPnl.add(btnAssign);
                buttonPnl.add(btnClear);
                buttonPnl.add(btnDeleteSubMessage);
                buttonPnl.add(btnClose);

                // Create two rows of buttons
                setButtonRows(2);

                // Create a tabbed pane in which to place the scheduler handlers
                tabbedPane = new DnDTabbedPane(SwingConstants.TOP)
                {
                    /******************************************************************************
                     * Update the scheduler list order following a tab move
                     *****************************************************************************/
                    @Override
                    protected Object tabMoveCleanup(int oldTabIndex,
                                                    int newTabIndex,
                                                    Object tabContents)
                    {
                        // Adjust the new tab index if moving the tab to a higher index
                        newTabIndex -= newTabIndex > oldTabIndex ? 1 : 0;

                        // Re-order the rate information based on the new tab order
                        // Re-order the rate information based on the new tab order
                        RateInformation[] rateInfoArray = rateHandler.getRateInformation().toArray(new RateInformation[0]);
                        rateInfoArray = (RateInformation[]) CcddUtilities.moveArrayMember(rateInfoArray, oldTabIndex, newTabIndex);
                        List<RateInformation> rateInfoList = new ArrayList<RateInformation>(rateInfoArray.length);
                        rateInfoList.addAll(Arrays.asList(rateInfoArray));
                        rateHandler.setRateInformation(rateInfoList);

                        // Get the reference to the moved tab's original location in the list
                        CcddSchedulerHandler editor = schHandlers.get(oldTabIndex);

                        // Remove the tab
                        schHandlers.remove(oldTabIndex);

                        // Add the tab at its new location
                        schHandlers.add(newTabIndex, editor);

                        // Update the active tab pointer to the moved tab
                        activeSchHandler = schHandlers.get(tabbedPane.getSelectedIndex());

                        return editor;
                    }
                };

                tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

                // Listen for tab selection changes
                tabbedPane.addChangeListener(new ChangeListener()
                {
                    /******************************************************************************
                     * Update the editor to the one associated with the selected tab
                     *****************************************************************************/
                    @Override
                    public void stateChanged(ChangeEvent ce)
                    {
                        // Set the active editor to the one indicated by the currently selected tab
                        activeSchHandler = schHandlers.get(tabbedPane.getSelectedIndex());

                        // Force the scheduler table to redraw so that the row heights are
                        // calculated correctly
                        schHandlers.get(tabbedPane.getSelectedIndex()).getSchedulerEditor().redrawTable();
                    }
                });

                // Add the scheduler handlers to the tabbed pane
                addDataStreams();

                // Set the first tab as the active editor
                activeSchHandler = schHandlers.get(0);
            }

            /**************************************************************************************
             * Telemetry scheduler dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the telemetry scheduler dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  tabbedPane,
                                  buttonPnl,
                                  btnClose,
                                  "Telemetry Scheduler",
                                  true);
            }
        });
    }

    /**********************************************************************************************
     * Add a scheduler handler for each rate
     *********************************************************************************************/
    private void addDataStreams()
    {
        // Step through each rate
        for (RateInformation rateInfo : rateHandler.getRateInformation())
        {
            // Create a new scheduler handler for each rate column
            schHandlers.add(new CcddSchedulerHandler(ccddMain,
                                                     rateInfo.getRateName(),
                                                     this));

            // Add each table as a tab in the editor window tabbed pane
            tabbedPane.addTab(rateInfo.getStreamName(),
                              null,
                              schHandlers.get(schHandlers.size() - 1).getSchedulerPanel(),
                              null);
        }
    }

    /**********************************************************************************************
     * Store the data from the various data streams into the database
     *********************************************************************************************/
    private void storeData()
    {
        // Create a list to hold the data streams
        List<DataStream> streams = new ArrayList<DataStream>();

        // Step through all the current scheduler handlers
        for (CcddSchedulerHandler handler : schHandlers)
        {
            // Add a new data stream for each scheduler handler
            streams.add(new DataStream(handler.getCurrentMessages(),
                                       handler.getRateName()));

            // Update the copy of the messages so that subsequent changes can be detected
            handler.getSchedulerEditor().copyMessages();
        }

        // Store the data streams
        schedulerDb.storeData(streams);
    }

    /**********************************************************************************************
     * Handle the dialog close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the scheduler table is validated and
        // that no message has changed. If a change exists then confirm discarding the changes
        if (activeSchHandler.getSchedulerEditor().getTable().isLastCellValid()
            && (!isChanges()
                || new CcddDialogHandler().showMessageDialog(CcddTelemetrySchedulerDialog.this,
                                                             "<html><b>Discard changes?",
                                                             "Discard Changes",
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
        {
            // Close the telemetry scheduler dialog
            closeDialog();
        }
    }

    /**********************************************************************************************
     * Check if a change has been made to a message
     *
     * @return true if a message in any of the data streams changed; false otherwise
     *********************************************************************************************/
    private boolean isChanges()
    {
        boolean isChanged = false;

        // Step through each data stream
        for (CcddSchedulerHandler schHandler : schHandlers)
        {
            // Check if a message in the stream changed
            if (schHandler.getSchedulerEditor().isMessagesChanged())
            {
                // Set the flag indicating a change and stop searching
                isChanged = true;
                break;
            }
        }

        return isChanged;
    }

    /**********************************************************************************************
     * Get the scheduler dialog
     *
     * @return Scheduler dialog
     *********************************************************************************************/
    @Override
    public CcddDialogHandler getDialog()
    {
        return CcddTelemetrySchedulerDialog.this;
    }

    /**********************************************************************************************
     * Enable/disable the dialog controls
     *
     * @param enable
     *            true to enable the controls, false to disable
     *********************************************************************************************/
    @Override
    public void setControlsEnabled(boolean enable)
    {
        super.setControlsEnabled(enable);
        activeSchHandler.setArrowsEnabled(enable);
    }

    /**********************************************************************************************
     * Get the scheduler database handler
     *
     * @return Scheduler database handler
     *********************************************************************************************/
    @Override
    public CcddSchedulerDbIOHandler getSchedulerDatabaseHandler()
    {
        return schedulerDb;
    }

    /**********************************************************************************************
     * Create and return a scheduler input object
     *
     * @param rateName
     *            rate column name
     *
     * @return Telemetry input object
     *********************************************************************************************/
    @Override
    public CcddSchedulerInputInterface createSchedulerInput(String rateName)
    {
        return new CcddTelemetrySchedulerInput(ccddMain,
                                               this,
                                               rateName,
                                               allVariableTree,
                                               allVariableTreePaths);
    }

    /**********************************************************************************************
     * Get the scheduler handler
     *
     * @return Scheduler handler
     *********************************************************************************************/
    @Override
    public CcddSchedulerHandler getSchedulerHandler()
    {
        return activeSchHandler;
    }

    /**********************************************************************************************
     * Get the reference to the list of scheduler handlers
     *
     * @return Reference to the list of scheduler handlers
     *********************************************************************************************/
    protected List<CcddSchedulerHandler> getSchedulerHandlers()
    {
        return schHandlers;
    }

    /**********************************************************************************************
     * Update the change indicator for the scheduler handler
     *********************************************************************************************/
    @Override
    public void updateChangeIndicator()
    {
        // Get the index of the currently displayed tab
        int index = tabbedPane.getSelectedIndex();

        // Check that a tab is displayed
        if (index != -1)
        {
            // Replace the tab name, appending the change indicator if changes exist
            tabbedPane.setTitleAt(index,
                                  tabbedPane.getTitleAt(index).replaceAll("\\" + CHANGE_INDICATOR,
                                                                          "")
                                         + (schHandlers.get(index)
                                                       .getSchedulerEditor()
                                                       .isMessagesChanged()
                                                                            ? CHANGE_INDICATOR
                                                                            : ""));
        }
    }

    /**********************************************************************************************
     * Steps to perform following storing of the scheduler data in the project database
     *********************************************************************************************/
    @Override
    public void doSchedulerUpdatesComplete(boolean errorFlag)
    {
        // Check that no error occurred storing the telemetry scheduler table
        if (!errorFlag)
        {
            // Update the message ID names combo boxes in any open table editors in case the group
            // has a message ID name or message ID data field that may have changed
            ccddMain.getDbTableCommandHandler().updateInputTypeColumns(null,
                                                                       CcddTelemetrySchedulerDialog.this);

            // Step through each data stream tab
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Remove the change indicator from the tab title
                tabbedPane.setTitleAt(index,
                                      tabbedPane.getTitleAt(index)
                                                .replaceAll("\\" + CHANGE_INDICATOR, ""));
            }
        }
    }
}
