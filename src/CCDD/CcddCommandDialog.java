/**
 * CFS Command and Data Dictionary command information dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.RENAME_ICON;
import static CCDD.CcddConstants.TABLE_ICON;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddCommandHandler.CommandInformation;
import CCDD.CcddConstants.CommandInformationTableColumnInfo;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary command information dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddCommandDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddJTableHandler commandTable;
    private final CcddCommandHandler commandHandler;
    private CcddTableTreeHandler tableTree;

    // Components referenced from multiple methods
    private JLabel numCommandsLbl;

    /**********************************************************************************************
     * Command information dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddCommandDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        commandHandler = ccddMain.getCommandHandler();

        // Create the command information dialog
        initialize();
    }

    /**********************************************************************************************
     * Create the command information dialog. This is executed in a separate thread since it can
     * take a noticeable amount time to complete, and by using a separate thread the GUI is allowed
     * to continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the command paths & names dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create a panel to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnShow;

            /**************************************************************************************
             * Build the command paths & names dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                Border emptyBorder = BorderFactory.createEmptyBorder();

                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                0.0,
                                                                0.0,
                                                                GridBagConstraints.FIRST_LINE_START,
                                                                GridBagConstraints.NONE,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           0,
                                                                           ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           0),
                                                                0,
                                                                0);

                // Create panels to hold the components of the dialog
                JPanel upperPnl = new JPanel(new GridBagLayout());
                JPanel inputPnl = new JPanel(new GridBagLayout());
                dialogPnl.setBorder(emptyBorder);
                upperPnl.setBorder(emptyBorder);
                inputPnl.setBorder(emptyBorder);

                // Add the inputs panel, containing the separator characters fields and check box,
                // to the upper panel
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridy = 0;
                upperPnl.add(inputPnl, gbc);

                // Build the table tree showing both table prototypes and table instances; i.e.,
                // parent tables with their child tables (i.e., parents with children)
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     new CcddGroupHandler(ccddMain,
                                                                          null,
                                                                          ccddMain.getMainFrame()),
                                                     TableTreeType.COMMAND_TABLES,
                                                     "Commands",
                                                     null,
                                                     ccddMain.getMainFrame());

                // Add the tree to the upper panel
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.fill = GridBagConstraints.BOTH;
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.weightx = 1.0;
                gbc.weighty = 1.0;
                gbc.gridx++;
                upperPnl.add(tableTree.createTreePanel("Command Tables",
                                                       TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                       false,
                                                       ccddMain.getMainFrame()),
                             gbc);
                gbc.gridwidth = 1;
                gbc.insets.right = 0;
                gbc.gridx = 0;
                gbc.fill = GridBagConstraints.BOTH;
                dialogPnl.add(upperPnl, gbc);

                // Create the commands and number of commands total labels
                JLabel commandsLbl = new JLabel("Commands");
                commandsLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                commandsLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                numCommandsLbl = new JLabel();
                numCommandsLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
                gbc.fill = GridBagConstraints.REMAINDER;

                // Add the commands labels to the dialog
                JPanel commandsPnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                commandsPnl.add(commandsLbl);
                commandsPnl.add(numCommandsLbl);
                gbc.weighty = 0.0;
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.insets.bottom = 0;
                gbc.gridy++;
                dialogPnl.add(commandsPnl, gbc);

                // Define the command paths & names JTable
                commandTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
                {
                    /******************************************************************************
                     * Allow multiple line display in all columns
                     *****************************************************************************/
                    @Override
                    protected boolean isColumnMultiLine(int column)
                    {
                        return true;
                    }

                    /******************************************************************************
                     * Load the command information into the table and format the table cells
                     *****************************************************************************/
                    @Override
                    protected void loadAndFormatData()
                    {
                        // Place the data into the table model along with the column names, set up
                        // the editors and renderers for the table cells, set up the table grid
                        // lines, and calculate the minimum width required to display the table
                        // information
                        setUpdatableCharacteristics(getCommands(),
                                                    CommandInformationTableColumnInfo.getColumnNames(),
                                                    null,
                                                    CommandInformationTableColumnInfo.getToolTips(),
                                                    false,
                                                    true,
                                                    true);
                    }

                    /******************************************************************************
                     * Override prepareRenderer to allow adjusting the background colors of table
                     * cells
                     *****************************************************************************/
                    @Override
                    public Component prepareRenderer(TableCellRenderer renderer,
                                                     int row,
                                                     int column)
                    {
                        JComponent comp = (JComponent) super.prepareRenderer(renderer,
                                                                             row,
                                                                             column);

                        // Get the column index in model coordinates
                        int columnModel = convertColumnIndexToModel(column);

                        // Check if the cell in in the command name column and doesn't have the
                        // focus or is selected. The focus and selection highlight colors override
                        // other highlight colors
                        if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                            && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor()
                            && columnModel == CommandInformationTableColumnInfo.COMMAND_NAME.ordinal())
                        {
                            // Get the row index in model coordinates
                            int rowModel = convertRowIndexToModel(row);

                            // Get a reference to the table model to shorten subsequent calls
                            TableModel tableModel = getModel();

                            // Get the contents of the command name column
                            String commandName = tableModel.getValueAt(rowModel,
                                                                       CommandInformationTableColumnInfo.COMMAND_NAME.ordinal())
                                                           .toString();

                            // Step through each row in the table
                            for (int checkRow = 0; checkRow < tableModel.getRowCount(); checkRow++)
                            {
                                // Check if this isn't the same row as the one being updated, that
                                // the cell isn't blank, and that the text matches that in another
                                // row of the same column
                                if (rowModel != checkRow
                                    && !commandName.isEmpty()
                                    && tableModel.getValueAt(checkRow, columnModel)
                                                 .toString()
                                                 .equals(commandName))
                                {
                                    // Change the cell's background color to indicate it has the
                                    // same value as another cell in the same column
                                    comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                                    break;
                                }
                            }
                        }

                        return comp;
                    }
                };

                // Place the table into a scroll pane
                JScrollPane scrollPane = new JScrollPane(commandTable);

                // Set common table parameters and characteristics
                commandTable.setFixedCharacteristics(scrollPane,
                                                     false,
                                                     ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                                     TableSelectionMode.SELECT_BY_CELL,
                                                     true,
                                                     ModifiableColorInfo.TABLE_BACK.getColor(),
                                                     true,
                                                     false,
                                                     ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                                     true);

                // Define the panel to contain the table
                JPanel commandsTblPnl = new JPanel();
                commandsTblPnl.setLayout(new BoxLayout(commandsTblPnl, BoxLayout.X_AXIS));
                commandsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                commandsTblPnl.add(scrollPane);

                // Add the table to the dialog
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = 1.0;
                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(commandsTblPnl, gbc);

                // Create a table opener for the Open tables command
                final TableOpener opener = new TableOpener();

                // Show commands button
                btnShow = CcddButtonPanelHandler.createButton("Show",
                                                              RENAME_ICON,
                                                              KeyEvent.VK_W,
                                                              "Show the project commands");

                // Add a listener for the Show button
                btnShow.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Filter the commands and display the results
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Display the commands, matching the filtering tables, if applicable
                        commandTable.loadAndFormatData();
                    }
                });

                // Print command information button
                JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                                       PRINT_ICON,
                                                                       KeyEvent.VK_P,
                                                                       "Print the command information");

                // Open table(s) button
                JButton btnOpen = CcddButtonPanelHandler.createButton("Open",
                                                                      TABLE_ICON,
                                                                      KeyEvent.VK_O,
                                                                      "Open the table(s) associated with the selected search result(s)");

                // Add a listener for the Open button
                btnOpen.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Open the selected table(s)
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        opener.openTables(commandTable,
                                          CommandInformationTableColumnInfo.COMMAND_TABLE.ordinal());
                    }
                });

                // Add a listener for the Print button
                btnPrint.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Print the commands list
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        commandTable.printTable("Project '"
                                                + ccddMain.getDbControlHandler().getDatabaseName()
                                                + "' Commands",
                                                null,
                                                CcddCommandDialog.this,
                                                PageFormat.LANDSCAPE);
                    }
                });

                // Close command dialog button
                JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                       CLOSE_ICON,
                                                                       KeyEvent.VK_C,
                                                                       "Close the commands dialog");

                // Add a listener for the Close button
                btnClose.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Close the commands dialog
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Close the dialog
                        closeDialog(CANCEL_BUTTON);
                    }
                });

                // Add the buttons to the dialog's button panel
                buttonPnl.setBorder(emptyBorder);
                buttonPnl.add(btnShow);
                buttonPnl.add(btnOpen);
                buttonPnl.add(btnPrint);
                buttonPnl.add(btnClose);
            }

            /**************************************************************************************
             * Command paths & names dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the command information dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  dialogPnl,
                                  buttonPnl,
                                  btnShow,
                                  "Command Information",
                                  true);
            }
        });
    }

    /**********************************************************************************************
     * Get the array of command information. If the table tree has any selections use these to
     * filter the command information array
     *
     * @return Array of command informations matching the filter tables, or all commands'
     *         information if no filter table is selected
     *********************************************************************************************/
    private Object[][] getCommands()
    {
        List<Object[]> commandList = new ArrayList<Object[]>();

        // Get the list of selected tables
        List<String> filterTables = tableTree.getSelectedTablesWithChildren();

        // Step through each command in the project
        for (CommandInformation commandInfo : commandHandler.getCommandInformation())
        {
            // Check if no tables are selected for use as filters or if the command table is in the
            // list of filter tables
            if (filterTables.isEmpty() || filterTables.contains(commandInfo.getTable()))
            {
                // Add the command information to the list
                commandList.add(new Object[] {commandInfo.getCommandName(),
                                              commandInfo.getCommandCode(),
                                              commandInfo.getTable(),
                                              commandInfo.getArguments()});
            }
        }

        // Update the number of commands label
        numCommandsLbl.setText("  (" + commandList.size() + " total)");

        return commandList.toArray(new Object[0][0]);
    }
}
