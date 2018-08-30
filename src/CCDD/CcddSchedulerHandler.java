/**
 * CFS Command and Data Dictionary scheduler handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesDataTable.AssociatedVariable;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.SchedulerType;

/**************************************************************************************************
 * CFS Command and Data Dictionary scheduler handler class
 *************************************************************************************************/
public class CcddSchedulerHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddSchedulerDialogInterface schedulerDlg;
    private final CcddSchedulerDbIOHandler schedulerDb;
    private CcddSchedulerEditorHandler schedulerEditor;
    private CcddSchedulerInputInterface schedulerInput;
    private final CcddRateParameterHandler rateHandler;
    private final CcddApplicationParameterHandler appHandler;

    // Components referenced by multiple methods
    private Border border;
    private JLabel unusedLbl;
    private JTextField unusedFld;
    private JTextField cycleFld;
    private JPanel schedulerPnl;
    private PaddedComboBox rateFilter;
    private JButton leftArrowBtn;
    private JButton rightArrowBtn;
    private static CcddHaltDialog haltDlg;

    // List of the message options
    private JList<String> optionList;

    // Model object for the position list
    private DefaultListModel<String> optionModel;

    // Name of the rate column
    private final String rateName;

    // No options message
    private final String NO_OPTION = "<html><i>No Available Options";

    /**********************************************************************************************
     * Scheduler handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param rateName
     *            rate column name
     *
     * @param schedulerDlg
     *            reference to the scheduler dialog that created this class
     *********************************************************************************************/
    CcddSchedulerHandler(CcddMain ccddMain,
                         String rateName,
                         CcddSchedulerDialogInterface schedulerDlg)
    {
        this.ccddMain = ccddMain;
        this.rateName = rateName;
        this.schedulerDlg = schedulerDlg;
        schedulerDb = schedulerDlg.getSchedulerDatabaseHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        appHandler = ccddMain.getApplicationParameterHandler();

        // Create the scheduler handler
        initialize();
    }

    /**********************************************************************************************
     * Get the scheduler option
     *
     * @return Scheduler option for the current scheduler; null if the scheduler dialog type is not
     *         recognized
     *********************************************************************************************/
    protected SchedulerType getSchedulerOption()
    {
        SchedulerType option = null;

        // Check if this is an application scheduler
        if (schedulerDlg instanceof CcddApplicationSchedulerDialog)
        {
            option = SchedulerType.APPLICATION_SCHEDULER;
        }
        // Check if this is a telemetry scheduler
        else if (schedulerDlg instanceof CcddTelemetrySchedulerDialog)
        {
            option = SchedulerType.TELEMETRY_SCHEDULER;
        }

        return option;
    }

    /**********************************************************************************************
     * Get the reference to the scheduler dialog
     *
     * @return Reference to the scheduler dialog
     *********************************************************************************************/
    protected CcddSchedulerDialogInterface getSchedulerDialog()
    {
        return schedulerDlg;
    }

    /**********************************************************************************************
     * Get the scheduler editor handler
     *
     * @return Scheduler editor handler
     *********************************************************************************************/
    protected CcddSchedulerEditorHandler getSchedulerEditor()
    {
        return schedulerEditor;
    }

    /**********************************************************************************************
     * Get the scheduler input
     *
     * @return Scheduler input
     *********************************************************************************************/
    protected CcddSchedulerInputInterface getSchedulerInput()
    {
        return schedulerInput;
    }

    /**********************************************************************************************
     * Set the variable(s) (application(s)) to be unavailable in the variable (application) tree
     *
     * @param names
     *            list of variable (application) names to exclude
     *********************************************************************************************/
    protected void setVariableUnavailable(List<String> names)
    {
        schedulerInput.excludeVariable(names);
    }

    /**********************************************************************************************
     * Set the variable(s) (application(s)) to be available in the variable (application) tree
     *
     * @param names
     *            list of variable (application) names to include
     *********************************************************************************************/
    protected void makeVariableAvailable(List<String> names)
    {
        schedulerInput.includeVariable(names);
    }

    /**********************************************************************************************
     * Set the label to display the given scheduler's set values
     *********************************************************************************************/
    private void setUnusedLabel()
    {
        // Get the scheduler dialog type
        SchedulerType option = getSchedulerOption();

        // Check if this is a telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            unusedLbl.setText("Total unused bytes ");
        }
        // Check if this is an application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            unusedLbl.setText("Total unused time ");
        }
    }

    /**********************************************************************************************
     * Set the total bytes or time remaining field
     *********************************************************************************************/
    protected void setUnusedField()
    {
        unusedFld.setText(String.valueOf(schedulerEditor.getTotalBytesRemaining()));
    }

    /**********************************************************************************************
     * Select the specified option in the Options list
     *
     * @param option
     *            option text to match in the Options list
     *********************************************************************************************/
    protected void selectOptionByMessage(String option)
    {
        optionList.clearSelection();
        optionList.setSelectedValue(option, true);
    }

    /**********************************************************************************************
     * Create the scheduler handler
     *********************************************************************************************/
    private void initialize()
    {
        // Create a border for the dialog components
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
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
                                                        1.0,
                                                        1.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 0, 0),
                                                        0,
                                                        0);

        // Create the panels for the display
        schedulerPnl = new JPanel(new GridBagLayout());
        JPanel titlePnl = new JPanel(new GridBagLayout());
        JPanel bytesPnl = new JPanel(new FlowLayout());
        JPanel cyclePnl = new JPanel(new FlowLayout());
        JPanel dualPnl = new JPanel(new GridBagLayout());

        // Create the unused bytes label and field and add them to the title panel
        unusedLbl = new JLabel();
        unusedLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        unusedFld = new JTextField(5);
        unusedFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        unusedFld.setEditable(false);
        unusedFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        unusedFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        unusedFld.setBorder(border);
        unusedFld.setHorizontalAlignment(SwingConstants.CENTER);
        setUnusedLabel();
        bytesPnl.add(unusedLbl);
        bytesPnl.add(unusedFld);
        titlePnl.add(bytesPnl, gbc);

        // Add a dummy label for alignment purposes
        gbc.gridx++;
        titlePnl.add(new JLabel());

        // Create the cycle label and field and add them to the cycle panel
        JLabel cycleLbl = new JLabel("Cycle time (sec) ");
        cycleLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        cycleFld = new JTextField(5);
        cycleFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        cycleFld.setEditable(false);
        cycleFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        cycleFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        cycleFld.setBorder(border);
        cycleFld.setHorizontalAlignment(SwingConstants.CENTER);
        cyclePnl.add(cycleLbl);
        cyclePnl.add(cycleFld);

        // Add the cycle panel to the scheduler panel
        gbc.gridx++;
        titlePnl.add(cyclePnl, gbc);

        // Add the title panel to the all panel
        gbc.gridx = 0;
        gbc.weighty = 0.0;
        schedulerPnl.add(titlePnl, gbc);

        // Create the dual panel and add it to the dual panel
        gbc.ipadx = 0;
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weighty = 1.0;
        dualPnl.add(createDualScrollPanelwithButtons(), gbc);

        // Set the unused bytes/time field
        setUnusedField();

        // Check if this is a telemetry scheduler
        if (getSchedulerOption() == SchedulerType.TELEMETRY_SCHEDULER)
        {
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        }
        // Use default values for an application scheduler or unknown type
        else
        {
            gbc.insets.left = 0;
            gbc.insets.right = 0;
        }

        // Add the dual panel to the scheduler panel
        schedulerPnl.add(dualPnl, gbc);
    }

    /**********************************************************************************************
     * Get the rate column name
     *
     * @return Rate column name
     *********************************************************************************************/
    protected String getRateName()
    {
        return rateName;
    }

    /**********************************************************************************************
     * Get a list of the current messages
     *
     * @return List of the current messages
     *********************************************************************************************/
    protected List<Message> getCurrentMessages()
    {
        return schedulerEditor.getCurrentMessages();
    }

    /**********************************************************************************************
     * Get the panel containing the dual scroll panels
     *
     * @return Panel containing the input, option, and table panels
     *********************************************************************************************/
    protected JPanel getSchedulerPanel()
    {
        return schedulerPnl;
    }

    /**********************************************************************************************
     * Get the list of variables for the data stream
     *
     * @return List of variables; null if the scheduler dialog type is not recognized
     *********************************************************************************************/
    protected List<Variable> getVariableList()
    {
        List<Variable> variables = null;

        // Get the scheduler dialog type
        SchedulerType option = getSchedulerOption();

        // Check if this is a telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            variables = schedulerDb.getVariableList(rateHandler.getRateInformationIndexByRateName(rateName));
        }
        // Check if this is an application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            variables = schedulerDb.getVariableList(Integer.valueOf(rateName));
        }

        return variables;
    }

    /**********************************************************************************************
     * Get the list of messages stored in the database
     *
     * @return List of messages for the data stream stored in the database; null if the scheduler
     *         dialog type is not recognized
     *********************************************************************************************/
    protected List<Message> getStoredData()
    {
        List<Message> messages = null;

        // Get the scheduler dialog type
        SchedulerType option = getSchedulerOption();

        // Check if this is a telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            // Get the variable message assignments for the current data stream
            messages = schedulerDb.getStoredData(rateHandler.getRateInformationIndexByRateName(rateName));

            // Set the link name for the variables in the messages
            ((CcddTelemetrySchedulerInput) schedulerInput).setLinks(messages, rateName);
        }
        // Check if this is an application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            // Get the application time slot assignments
            messages = schedulerDb.getStoredData(Integer.valueOf(rateName));
        }

        return messages;
    }

    /**********************************************************************************************
     * Automatically fill the variables/applications in messages/time slots corresponding to their
     * rate
     *********************************************************************************************/
    protected void autoFill()
    {
        // Create the auto-fill progress/cancellation dialog
        haltDlg = new CcddHaltDialog(true);

        // Get the scheduler dialog type
        final SchedulerType schedulerType = getSchedulerOption();

        // Get the text that describes the type of object being assigned
        final String variableType = schedulerType == SchedulerType.TELEMETRY_SCHEDULER
                                                                                       ? "variables"
                                                                                       : (schedulerType == SchedulerType.APPLICATION_SCHEDULER
                                                                                                                                               ? "applications"
                                                                                                                                               : "unknown");

        final List<String> ratesInUse = new ArrayList<String>();

        // Step through the available rates
        for (String rate : schedulerInput.getAvailableRates())
        {
            // Check if the rate has any parameters. Rates with no parameters are grayed
            // out using HTML tags
            if (!rate.startsWith("<html>"))
            {
                // Add the rate to the list of those in use
                ratesInUse.add(rate);
            }
        }

        // Create a thread to perform the auto-fill operation in the background
        final Thread autoFillThread = new Thread(new Runnable()
        {
            /**************************************************************************************
             * Auto-fill the telemetry messages (if this is the telemetry scheduler) or application
             * time slots (if this is the application scheduler). Note that in the comments below
             * 'variable' is used to describe the object being assigned, which is accurate for the
             * telemetry scheduler. For the application scheduler substitute 'application' for
             * 'variable'
             *************************************************************************************/
            @Override
            public void run()
            {
                int numVariables = 0;
                int unassigned = 0;

                // Step through rates in use getting the highest one each time. Add the variables
                // at each rate until no more rates are available
                for (String rate : ratesInUse)
                {
                    // Check if this is a telemetry scheduler
                    if (schedulerType == SchedulerType.TELEMETRY_SCHEDULER)
                    {
                        // Get the reference to the variable tree in order to shorten the
                        // subsequent call
                        CcddTableTreeHandler variableTree = ((CcddTelemetrySchedulerInput) schedulerInput).getVariableTree();

                        // Add the number of unassigned variables at the given rate.
                        // getVariablesAtRate() could be used, but it performs other operations
                        // associated with links and is slower
                        numVariables += variableTree.getPrimitiveVariablePaths(variableTree.getRootNode(), true).size();
                    }
                    // Check if this is an application scheduler
                    else if (schedulerType == SchedulerType.APPLICATION_SCHEDULER)
                    {
                        // Add the number of unassigned applications at the given rate
                        numVariables += schedulerInput.getVariablesAtRate(rate).size();
                    }
                }

                // Check if there are any variables to assign
                if (numVariables != 0)
                {
                    int step = 0;

                    // Total size of the variable or link
                    int totalSize;

                    // Message option to which the variable or link will be added
                    String option;

                    // Variable that will be added
                    Variable variable;

                    // Variables that will be removed from the remaining list
                    List<Variable> removedVars = new ArrayList<Variable>();

                    // List of variables at a given rate
                    List<Variable> varList;

                    // List of the variables to exclude from the Variables tree. This should be all
                    // of the variables unless there are any that can't be assigned
                    List<String> excludedVars = new ArrayList<String>();

                    // Step through rates in use getting the highest one each time
                    for (String rate : ratesInUse)
                    {
                        // Check if auto-fill is canceled
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Get the rate as a floating point value
                        float rateVal = CcddUtilities.convertStringToFloat(rate);

                        // Get a list of all variables at the given rate (ones already assigned
                        // are not included)
                        varList = schedulerInput.getVariablesAtRate(rate);

                        // Set the number of steps to the number of variables for this rate
                        haltDlg.setItemsPerStep(varList.size());

                        // Update the progress bar
                        haltDlg.updateProgressBar("Assigning " + rate + " Hz " + variableType,
                                                  haltDlg.getNumDivisionPerStep() * step);
                        step++;

                        // Sort the list from largest to smallest
                        Collections.sort(varList);

                        // Loop through the list of variables until all are removed
                        while (!varList.isEmpty())
                        {
                            // Check if auto-fill is canceled
                            if (haltDlg.isHalted())
                            {
                                break;
                            }

                            // Total size of the variable or link
                            totalSize = 0;

                            // Set to the first variable in the list
                            variable = varList.get(0);

                            // Check if the variable is linked
                            if (variable.getLink() != null)
                            {
                                // Step through each variable in the variable list
                                for (Variable linkVar : varList)
                                {
                                    // Check if the variable is in the link of the given
                                    // variable
                                    if (linkVar.getLink() != null
                                        && linkVar.getLink().equals(variable.getLink()))
                                    {
                                        // Add the variable's size to the total size
                                        totalSize += variable.getSize();

                                        // Add the variable to the list of removed variables
                                        removedVars.add(linkVar);
                                    }
                                }
                            }
                            // The variable is unlinked
                            else
                            {
                                // Check if this is a telemetry scheduler
                                if (schedulerType == SchedulerType.TELEMETRY_SCHEDULER)
                                {
                                    // Set total size to the given variable's size
                                    totalSize = variable.getSize();

                                    // Get the total size (in bytes) and the list of the
                                    // variable, or variables if this variable is associated
                                    // with others due to bit-packing or string membership and
                                    // therefore must be placed together in a message
                                    AssociatedVariable associates = ((CcddTelemetrySchedulerInput) schedulerInput).getAssociatedVariables(varList);

                                    // Set the total size to that of the associated variable(s)
                                    // and add the variable(s) to the list of those to be
                                    // removed
                                    totalSize = associates.getTotalSize();
                                    removedVars.addAll(associates.getAssociates());
                                }
                                // This is an application (or unknown type of) scheduler
                                else
                                {
                                    // Set total size to the given variable's size
                                    totalSize = variable.getSize();

                                    // Add the variable to the list of removed variables
                                    removedVars.add(variable);
                                }
                            }

                            // Find the option with the most room
                            option = getMessageWithRoom(rateVal, totalSize);

                            // Check to make sure there is an option
                            if (option != null)
                            {
                                // Parse the option string to extract the sub-index (if this is
                                // a sub-option) and the message indices
                                Object[] parsedIndices = parseOption(option);

                                // Add the variable to the given message. If a sub-index is not
                                // given it will be set to -1. Add the list of added variables
                                // to the list of those to exclude in the Variables tree
                                excludedVars.addAll(addVariableToMessage(removedVars,
                                                                         (Integer[]) parsedIndices[1],
                                                                         (int) parsedIndices[0]));
                            }
                            // No option is available
                            else
                            {
                                // Increment the unplaced variable counter
                                unassigned++;
                            }

                            // Remove all the variables in removed variables list. This
                            // includes variables that did not fit into the telemetry table
                            varList.removeAll(removedVars);

                            // Step through each removed variable
                            while (!removedVars.isEmpty())
                            {
                                // Remove the variable from the list
                                removedVars.remove(0);

                                // Update the within-step progress value
                                haltDlg.updateProgressBar(null, -1);
                            }
                        }
                    }

                    // Check if auto-fill isn't canceled
                    if (!haltDlg.isHalted())
                    {
                        // Perform any updates needed following adding variables to messages
                        updateAfterVariableAdded();

                        // Set the variable tree to exclude the variable(s)
                        setVariableUnavailable(excludedVars);

                        // Display the originally selected rate's variable tree
                        schedulerInput.updateVariableTree(rateFilter.getSelectedItem().toString());

                        // Update the remaining bytes column values
                        schedulerEditor.updateRemainingBytesColumn();

                        // Update the unused bytes/time field
                        setUnusedField();

                        // Update the assigned variables/applications list panel
                        schedulerEditor.updateAssignmentList();

                        // Update the scheduler dialog's change indicator
                        getSchedulerDialog().updateChangeIndicator();

                        // Check if there are items that are not assigned
                        if (unassigned != 0)
                        {
                            // Inform the user if there are items that are not assigned
                            new CcddDialogHandler().showMessageDialog(schedulerDlg.getDialog(),
                                                                      "<html><b> Auto-fill unable to assign "
                                                                                                + unassigned
                                                                                                + " "
                                                                                                + variableType,
                                                                      "Auto-fill",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                    }
                }
                // There are no unassigned variables
                else
                {
                    // Check if the user didn't cancel auto-fill
                    if (!haltDlg.isHalted())
                    {
                        // Close the cancellation dialog. This also sets the halt flag
                        haltDlg.closeDialog();
                    }

                    new CcddDialogHandler().showMessageDialog(schedulerDlg.getDialog(),
                                                              "<html><b>All "
                                                                                        + variableType
                                                                                        + " with a rate are already assigned",
                                                              "Auto-fill",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                // Check if the user didn't cancel auto-fill
                if (!haltDlg.isHalted())
                {
                    // Close the cancellation dialog
                    haltDlg.closeDialog();
                }
                // Auto-fill was canceled
                else
                {
                    ccddMain.getSessionEventLog().logEvent(EventLogMessageType.STATUS_MSG,
                                                           "Auto-fill terminated by user");
                }
            }
        });

        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, schedulerDlg.getDialog(), new BackgroundCommand()
        {
            /**************************************************************************************
             * Build and display the auto-fill progress/cancellation dialog. This is done as a
             * background operation so that the
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Display the auto-fill progress/cancellation dialog
                haltDlg.initialize("Auto-fill "
                                   + (schedulerType == SchedulerType.TELEMETRY_SCHEDULER
                                                                                         ? "Telemetry Messages"
                                                                                         : (schedulerType == SchedulerType.APPLICATION_SCHEDULER
                                                                                                                                                 ? "Time Slots"
                                                                                                                                                 : "")),
                                   "Assigning "
                                                                                                                                                         + variableType
                                                                                                                                                         + "...",
                                   "auto-fill",
                                   100,
                                   ratesInUse.size(),
                                   true,
                                   schedulerDlg.getDialog());
            }
        });

        // Perform the auto-fill operation in a background thread
        autoFillThread.start();
    }

    /**********************************************************************************************
     * Add one or more variables to the specified message
     *
     * @param variables
     *            list of variables to be added
     *
     * @param messageIndices
     *            (sub-)message indices to which the variable will be added
     *
     * @param parentIndex
     *            parent message index if this is a sub-message; -1 if this is not a sub-message
     *
     * @return List containing the names of the variables added
     *********************************************************************************************/
    private List<String> addVariableToMessage(List<Variable> variables,
                                              Integer[] messageIndices,
                                              int parentIndex)
    {
        List<String> addedVariables = new ArrayList<String>();

        // Step through each variable in the list of variables to be added
        for (Variable variable : variables)
        {
            // Step through each message in the option
            for (int index = 0; index < messageIndices.length; index++)
            {
                // Add the variable to the telemetry table
                schedulerEditor.addVariableToMessage(variable,
                                                     messageIndices[index],
                                                     parentIndex);
            }

            // Set the variable's messages it is contained in
            variable.setMessageIndices(messageIndices);

            // Add the variable to the list of excluded variables
            addedVariables.add(variable.getFullName());
        }

        // Update the total bytes remaining for each message
        schedulerEditor.calculateTotalBytesRemaining();

        return addedVariables;
    }

    /**********************************************************************************************
     * Update actions required after adding one or more variables to a message. This is a separate
     * method so that these update steps can be performed s single time if multiple variable
     * additions are made
     *********************************************************************************************/
    private void updateAfterVariableAdded()
    {
        // Check if this is a telemetry scheduler
        if (getSchedulerOption() == SchedulerType.TELEMETRY_SCHEDULER)
        {
            // Update the assignment definition list for when the assignment tree is rebuilt
            schedulerEditor.updateAssignmentDefinitions();

            // Set the link name for the variables in the messages
            ((CcddTelemetrySchedulerInput) schedulerInput).setLinks(getCurrentMessages(),
                                                                    rateName);
        }
    }

    /**********************************************************************************************
     * Parse an option string to extract the message indices and, if a sub-message, the sub-message
     * index
     *
     * @param option
     *            string representing the option in the format Message[s] #[, # [, ...]][
     *            sub-msg[s] #[, # [, ...]]]
     *
     * @return Two-element object array. If the option string does not contains only a message name
     *         or names then the first element is -1 (indicating no sub-messages) and the second
     *         element is the array containing the message index (or indices) corresponding to the
     *         message name(s). If the option string contains one or more sub-message indices then
     *         the first element is the parent message index and the second element is the array
     *         containing the sub-message index (or indices). A null array is returned if the
     *         option provided is invalid
     *********************************************************************************************/
    private Object[] parseOption(String option)
    {
        Object[] indices = null;

        // Check if a valid option is selected
        if (option != null && !option.equals(NO_OPTION))
        {
            int parentIndex = -1;
            List<Integer> msgIndices = new ArrayList<Integer>();

            // Check if the option is a sub-option
            if (option.contains(" sub-msg"))
            {
                // Set options to the sub-message string
                String[] optionArray = option.split(" sub-msgs?? ");

                // Set the sub-index to the message index
                parentIndex = getMessageIndexByName(optionArray[0]);

                // Step through each message number
                for (String subMsg : optionArray[1].split(", "))
                {
                    // Added the parse index to the array. Subtract 1 in order to adjust for the
                    // messages being internally zero-based
                    msgIndices.add(Integer.valueOf(subMsg) - 1);
                }
            }
            // Not a sub-option
            else
            {
                // Split the option string into the separate message names and step through each
                for (String msgName : option.split(", "))
                {
                    // Get the index of the message based on its name and add it to the list
                    msgIndices.add(getMessageIndexByName(msgName));
                }
            }

            // Store the indices
            indices = new Object[] {parentIndex, msgIndices.toArray(new Integer[0])};
        }

        return indices;
    }

    /**********************************************************************************************
     * Get the index of the specified message
     *
     * @param msgName
     *            message name
     *
     * @return Message index; -1 if the no message of the specified name exists
     *********************************************************************************************/
    private int getMessageIndexByName(String msgName)
    {
        int messageIndex = -1;

        // Step through each message
        for (int msgIndex = 0; msgIndex < getCurrentMessages().size(); msgIndex++)
        {
            // Check if the message name matches the target
            if (msgName.equals(getCurrentMessages().get(msgIndex).getName()))
            {
                // Store the message index and stop searching
                messageIndex = msgIndex;
                break;
            }
        }

        return messageIndex;
    }

    /**********************************************************************************************
     * Find the message option that has the largest message with the least amount of room
     *
     * @param rate
     *            rate of the options
     *
     * @param totalSize
     *            size of the variable(s) in bytes
     *
     * @return Message into which the specified bytes can fit; null if no option is chosen
     *********************************************************************************************/
    private String getMessageWithRoom(Float rate, int totalSize)
    {
        // Option that is chosen
        String selectedOption = null;

        // List of all available options
        List<String> options = schedulerEditor.getMessageAvailability(rate);

        // Check if there is at least one option available
        if (!options.isEmpty())
        {
            // Largest amount of bytes in the list of options
            int smallestSize = -100;

            // Step though each message object that the variable can fit into
            for (int optIndex = 0; optIndex < options.size(); optIndex++)
            {
                // Parse the option string to extract the sub-index (if this is a sub-option) and
                // the message indices
                Object[] parsedIndices = parseOption(options.get(optIndex));
                int parentIndex = (int) parsedIndices[0];
                Integer[] indices = (Integer[]) parsedIndices[1];

                // Check if the message has a remaining slot available
                if (checkSlotAvailability(indices, 1))
                {
                    // Set the size to that of the first message
                    int size = Integer.MAX_VALUE;

                    // Step through each message inside an option
                    for (int index = 0; index < indices.length; index++)
                    {
                        // Set message to the desired message
                        Message message = schedulerEditor.getMessage(indices[index], parentIndex);

                        // Check if the message's bytes are less then the current smallest. This is
                        // done to find the least amount of bytes remaining out of the message
                        // option
                        if (message.getBytesRemaining() < size)
                        {
                            // Set the smallest to the message's bytes
                            size = message.getBytesRemaining();
                        }
                    }

                    // Check if smallest size is still the default value
                    if (smallestSize == -100)
                    {
                        // Set smallest to the size
                        smallestSize = size;

                        // Set option to the current message
                        selectedOption = options.get(optIndex);
                    }
                    // Check if size is greater than smallest. This is done in order to find the
                    // option with the largest of amount of bytes based off the message with the
                    // least bytes remaining in each option
                    else if (size > smallestSize)
                    {
                        // Assign size to the smallest
                        smallestSize = size;

                        // Set the option to the message
                        selectedOption = options.get(optIndex);
                    }
                }
            }

            // Check if the smallest size causes the message to be negative
            if (totalSize > smallestSize)
            {
                // Set to indicate no option exists
                selectedOption = null;
            }
        }

        return selectedOption;
    }

    /**********************************************************************************************
     * Update the options list with all possible options for the selected variable at the currently
     * selected rate
     *********************************************************************************************/
    protected void getTelemetryOptions()
    {
        // Remove current options
        optionModel.removeAllElements();

        // Check if there are any applications available
        if (rateFilter.getSelectedItem() != null)
        {
            // Get the options from the telemetry scheduler
            List<String> options = schedulerEditor.getMessageAvailability(CcddUtilities.convertStringToFloat(CcddUtilities.removeHTMLTags(rateFilter.getSelectedItem().toString())));

            // Step through each option
            for (String option : options)
            {
                // Add each option to the option model
                optionModel.add(optionModel.size(), option);
            }
        }

        // Check if the option model is empty
        if (optionModel.isEmpty())
        {
            // Set the option model to display no options
            optionModel.addElement(NO_OPTION);
        }
    }

    /**********************************************************************************************
     * Create dual scroll panels
     *
     * @return Split pane containing the dual panels
     *********************************************************************************************/
    @SuppressWarnings("serial")
    private JSplitPane createDualScrollPanelwithButtons()
    {
        // Create an empty border
        Border emptyBorder = BorderFactory.createEmptyBorder();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(0,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   0),
                                                        0,
                                                        0);

        // Create the scheduler input (variables or applications) handler
        schedulerInput = schedulerDlg.createSchedulerInput(rateName);

        int totalMsgs = 0;
        int totalBytes = 0;
        int msgsPerSec = 0;

        // Get the scheduler dialog type
        SchedulerType option = getSchedulerOption();

        // Check if this is the telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            // Get the information for the rate
            RateInformation info = rateHandler.getRateInformationByRateName(rateName);

            // Get the rate parameters
            totalMsgs = info.getMaxMsgsPerCycle();
            totalBytes = info.getMaxBytesPerSec();
            msgsPerSec = rateHandler.getMaxMsgsPerSecond();
        }
        // Check if this is an application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            // Set the total number of messages to the largest rate
            totalMsgs = appHandler.getMsgsPerCycle();

            // Set messages per second to highest to ensure the cycle time is 1 second
            msgsPerSec = appHandler.getMaxMsgsPerSecond();
            totalBytes = (int) ((Float.valueOf(totalMsgs) / Float.valueOf(msgsPerSec)) * 1000);
        }

        // Create the scheduler editor handler
        schedulerEditor = new CcddSchedulerEditorHandler(ccddMain,
                                                         this,
                                                         totalMsgs,
                                                         totalBytes,
                                                         msgsPerSec);

        // Create the options model
        optionModel = new DefaultListModel<String>();

        // Set the cycle value label to the period
        cycleFld.setText(String.valueOf(Float.valueOf(totalMsgs)
                                        / Float.valueOf(msgsPerSec)));

        // Create panels to hold the components
        JPanel packPnl = new JPanel(new GridBagLayout());
        JPanel rateSelectPnl = new JPanel(new GridBagLayout());
        JPanel optionPnl = new JPanel(new GridBagLayout());
        packPnl.setBorder(emptyBorder);
        rateSelectPnl.setBorder(emptyBorder);
        optionPnl.setBorder(emptyBorder);

        // Create the options label and add it to the rate panel
        JLabel optionLbl = new JLabel("Options");
        optionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        optionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        optionPnl.add(optionLbl, gbc);

        // Create the rate label and add it to the rate panel
        JLabel rateSelectLbl = new JLabel("Rate Filter ");
        rateSelectLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        rateSelectLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
        gbc.weighty = 1.0;
        gbc.gridx++;
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        rateSelectPnl.add(rateSelectLbl, gbc);

        // Create the combo box that displays the variable rates
        rateFilter = new PaddedComboBox(schedulerInput.getAvailableRates(),
                                        ModifiableFontInfo.INPUT_TEXT.getFont())
        {
            /**************************************************************************************
             * Override so that items flagged as disabled (grayed out) can't be selected. Only the
             * telemetry scheduler makes use of this; it has no effect on the application scheduler
             *************************************************************************************/
            @Override
            public void setSelectedItem(Object anObject)
            {
                // Check if the item isn't flagged as disabled
                if (!anObject.toString().startsWith(DISABLED_TEXT_COLOR))
                {
                    // Set the selected item to the specified item, if it exists in the list
                    super.setSelectedItem(anObject);
                }
            }
        };

        rateFilter.setBorder(emptyBorder);
        rateFilter.setSelectedItem(schedulerInput.getSelectedRate());

        // Add a listener for rate filter selection changes
        rateFilter.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Rebuild the table tree using the selected rate filter
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the rate selected in the combo box
                String rate = ((JComboBox<?>) ae.getSource()).getSelectedItem().toString();

                // Update the variable tree to display variables with the given rate
                schedulerInput.updateVariableTree(rate);

                // Set the options panel to display the options for the selected rate
                getTelemetryOptions();
            }
        });

        // Add the rate filter to the rate panel
        gbc.gridx++;
        rateSelectPnl.add(rateFilter, gbc);

        // Create a list that will contain all the telemetry options for a variable
        optionList = new JList<String>(optionModel);
        optionList.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Add a listener to set the message availability given the selected option
        optionList.addListSelectionListener(new ListSelectionListener()
        {
            /**************************************************************************************
             * Handle a selection change
             *************************************************************************************/
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent)
            {
                // Update the scheduler table text highlighting
                updateSchedulerTableHighlight();
            }
        });

        // Add the list to a scroll pane that will be placed next to the variable list
        JScrollPane optionScroll = new JScrollPane(optionList);
        optionScroll.setBorder(border);

        // Set the preferred width of the tree's scroll pane
        optionScroll.setPreferredSize(new Dimension(Math.min(Math.max(optionScroll.getPreferredSize().width,
                                                                      200),
                                                             200),
                                                    optionScroll.getPreferredSize().height));

        // Set the minimum size to the preferred size
        optionScroll.setMinimumSize(optionScroll.getPreferredSize());

        // Add the option scroll pane to the option panel
        gbc.insets.top = 0;
        gbc.insets.bottom = 0;
        gbc.gridx = 0;
        gbc.gridy++;
        optionPnl.add(optionScroll, gbc);

        // Add the rate selection panel to the option panel
        gbc.gridy++;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        optionPnl.add(rateSelectPnl, gbc);

        // Add the option panel to the pack panel
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.gridx++;
        packPnl.add(optionPnl, gbc);

        // Create the split pane containing the input tree and options panel
        JSplitPane leftSpltPn = new CustomSplitPane(schedulerInput.getInputPanel(),
                                                    packPnl,
                                                    null,
                                                    JSplitPane.HORIZONTAL_SPLIT);

        // Create the split pane containing the left split pane and the split pane containing the
        // scheduler and assignment tree/list. Use the arrow button panel as the split pane divider
        JSplitPane allSpltPn = new CustomSplitPane(leftSpltPn,
                                                   schedulerEditor.getSchedulerAndAssignPanel(),
                                                   createArrowButtonPanel(),
                                                   JSplitPane.HORIZONTAL_SPLIT);

        // Set the options list to display the starting rate value
        getTelemetryOptions();

        return allSpltPn;
    }

    /**********************************************************************************************
     * Update the scheduler table text highlighting
     *********************************************************************************************/
    protected void updateSchedulerTableHighlight()
    {
        // Reset the message availability so no message is colored
        schedulerEditor.resetMessageAvailability();

        // Get the selected message's size in bytes
        int variableSize = schedulerInput.getSelectedVariableSize(null);

        // Check to make sure a message option is selected
        if (optionList.getSelectedValue() != null && variableSize > 0)
        {
            // The selected message option
            String option = optionList.getSelectedValue().toString();

            // Parse the option string to extract the sub-index (if this is a sub-option) and the
            // message indices
            Object[] parsedIndices = parseOption(option);

            // Check if a valid option is selected
            if (parsedIndices != null)
            {
                int parentIndex = (int) parsedIndices[0];

                // Check if this is a parent message
                if (parentIndex == -1)
                {
                    Integer[] indices = (Integer[]) parsedIndices[1];

                    // Set smallest to the smallest number of bytes out of the sub-messages
                    int smallest = getSmallestByteSize(indices, parentIndex);

                    // Step through each message
                    for (int msgIndex = 0; msgIndex < indices.length; msgIndex++)
                    {
                        // Set the message availability for the message
                        schedulerEditor.setMessageAvailability(indices[msgIndex],
                                                               smallest - variableSize);
                    }
                }
                // This is a sub-message
                else
                {
                    // Set the message availability for the message
                    schedulerEditor.setMessageAvailability(parentIndex,
                                                           schedulerEditor.getMessageSize(parentIndex)
                                                                        - variableSize);
                }
            }
        }
    }

    /**********************************************************************************************
     * Get the smallest byte count for a list of (sub-)messages
     *
     * @param indices
     *            (sub-)message indices
     *
     * @param parentIndex
     *            parent message index if this is a sub-message; -1 if this is not a sub-message
     *
     * @return Smallest number of bytes
     *********************************************************************************************/
    private int getSmallestByteSize(Integer[] indices, int parentIndex)
    {
        int smallest = Integer.MAX_VALUE;

        // Step through each (sub-)message index
        for (int index = 0; index < indices.length; index++)
        {
            // Set size to the given (sub-)message's size
            int size = schedulerEditor.getMessage(indices[index].intValue(), parentIndex)
                                      .getBytesRemaining();

            // Check if the (sub-)message's size is less then the smallest
            if (size < smallest)
            {
                // Set smallest to the (sub-)message's size
                smallest = size;
            }
        }

        return smallest;
    }

    /**********************************************************************************************
     * Create a panel to contain a pair of arrow buttons. Make all but the button icons transparent
     *
     * @return JPanel containing the arrow buttons in a vertical layout
     *********************************************************************************************/
    private JPanel createArrowButtonPanel()
    {
        // Create the left and right arrow buttons
        leftArrowBtn = new JButton();
        rightArrowBtn = new JButton();

        // Create a panel to hold the buttons
        JPanel buttonPnl = new JPanel(new GridBagLayout());

        // Create the 'remove item' button
        leftArrowBtn.setIcon(new ImageIcon(getClass().getResource(LEFT_ICON)));
        leftArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the remove item button
        leftArrowBtn.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Remove the selected variable(s) from the message
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Remove the selected variable(s) and get the variable name(s)
                List<String> varName = schedulerEditor.removeSelectedVariable();

                // Check if a variable is selected
                if (varName != null)
                {
                    // Set the variable to be available
                    makeVariableAvailable(varName);

                    // Set the unused bytes/time field
                    setUnusedField();
                }
            }
        });

        // Create the 'add item' button
        rightArrowBtn.setIcon(new ImageIcon(getClass().getResource(RIGHT_ICON)));
        rightArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the add item button
        rightArrowBtn.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Add the the selected variable(s) to the message
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the current selected option value
                String selectedValue = optionList.getSelectedValue();

                // Check if an option is selected
                if (selectedValue != null)
                {
                    // Get the selected variables
                    List<Variable> variables = schedulerInput.getSelectedVariable();

                    // Check if a variable is selected
                    if (!variables.isEmpty())
                    {
                        // Parse the option string to extract the sub-index (if this is a
                        // sub-option) and the message indices
                        Object[] parsedIndices = parseOption(selectedValue.toString());
                        int parentIndex = (int) parsedIndices[0];
                        Integer[] indices = (Integer[]) parsedIndices[1];

                        // Check if the message has a slot available of the specified size
                        if (checkSlotAvailability(indices, variables.size()))
                        {
                            // Assign the variable to the message. The sub-index is -1 if not a
                            // sub-message
                            List<String> excludedVars = addVariableToMessage(variables,
                                                                             indices,
                                                                             parentIndex);

                            // Perform any updates needed following adding variables to messages
                            updateAfterVariableAdded();

                            // Exclude the variable from the variable tree
                            setVariableUnavailable(excludedVars);

                            // Check if it is not a sub-message
                            if (parentIndex == -1)
                            {
                                // Set sub-index to the last message assigned
                                parentIndex = indices[indices.length - 1];
                            }

                            // Select the message
                            schedulerEditor.setSelectedRow(parentIndex);

                            // Update the package list with the new variables
                            schedulerEditor.updateAssignmentList();
                        }
                        // No slot is available
                        else
                        {
                            // Get the scheduler dialog type
                            SchedulerType option = getSchedulerOption();

                            // Check if this is a telemetry scheduler
                            if (option == SchedulerType.TELEMETRY_SCHEDULER)
                            {
                                // Inform the user that the variable can not be added
                                new CcddDialogHandler().showMessageDialog(schedulerDlg.getDialog(),
                                                                          "<html><b>Cannot assign variable to a message",
                                                                          "Assign Fail",
                                                                          JOptionPane.WARNING_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                            }
                            // Check if this is an application scheduler
                            else if (option == SchedulerType.APPLICATION_SCHEDULER)
                            {
                                // Inform the user that the application can not be added
                                new CcddDialogHandler().showMessageDialog(schedulerDlg.getDialog(),
                                                                          "<html><b>Cannot assign application to a time slot",
                                                                          "Over-scheduled Time Slot",
                                                                          JOptionPane.WARNING_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                            }
                        }

                        // Reset the Scheduler table names to normal
                        schedulerEditor.resetMessageAvailability();

                        // Update the unused bytes/time field
                        setUnusedField();
                    }
                }
            }
        });

        // Set the layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.CENTER,
                                                        GridBagConstraints.NONE,
                                                        new Insets(0, 0, 0, 0),
                                                        0,
                                                        0);

        // Set the border
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());

        // Hide everything but the arrow button icons
        rightArrowBtn.setOpaque(false);
        rightArrowBtn.setContentAreaFilled(false);
        rightArrowBtn.setBorderPainted(false);
        leftArrowBtn.setOpaque(false);
        leftArrowBtn.setContentAreaFilled(false);
        leftArrowBtn.setBorderPainted(false);

        // Add the buttons to the panel
        buttonPnl.add(rightArrowBtn, gbc);
        gbc.insets.bottom = (getSchedulerOption() == SchedulerType.TELEMETRY_SCHEDULER
                                                                                       ? 15
                                                                                       : 0);
        gbc.gridy++;
        buttonPnl.add(leftArrowBtn, gbc);

        return buttonPnl;
    }

    /**********************************************************************************************
     * Enable/disable the dialog arrow buttons
     *
     * @param enable
     *            true to enable the arrows, false to disable
     *********************************************************************************************/
    protected void setArrowsEnabled(boolean enable)
    {
        leftArrowBtn.setEnabled(enable);
        rightArrowBtn.setEnabled(enable);
    }

    /**********************************************************************************************
     * Check if a message has a remaining slot available for the variables. If this is not for the
     * application scheduler then always return true
     *
     * @param indices
     *            array of subMessge indices
     *
     * @param numOfVars
     *            number of variables being added
     *
     * @return true if there is room for the variable(s); false if there is insufficient room
     *********************************************************************************************/
    private boolean checkSlotAvailability(Integer[] indices, int numOfVars)
    {
        boolean valid = true;

        // Check if this is an application scheduler
        if (getSchedulerOption() == SchedulerType.APPLICATION_SCHEDULER)
        {
            // Get the number of slots from the application parameters
            int slots = appHandler.getNumberOfMessagesPerTimeSlot();

            // Step through each message
            for (int index = 0; index < indices.length; index++)
            {
                // Check if the amount of messages is more then the slots available
                if ((schedulerEditor.getPacketSize(indices[index]) + numOfVars) > slots)
                {
                    // Set the flag to indicate no room exists and stop searching
                    valid = false;
                    break;
                }
            }
        }

        return valid;
    }
}
