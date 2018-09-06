/**
 * CFS Command and Data Dictionary show all message IDs dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.TABLE_ICON;

import java.awt.Component;
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
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddConstants.MessageIDSortOrder;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.MsgIDListColumnIndex;
import CCDD.CcddConstants.MsgIDTableColumnInfo;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary show all message IDs dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddMessageIDDialog extends CcddDialogHandler
{
    // Flag that indicates if any of the tables with message IDS to display are children of another
    // table, and therefore have a structure path
    private boolean isPath;

    /**********************************************************************************************
     * Show all message IDs dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddMessageIDDialog(CcddMain ccddMain)
    {
        // Create the message ID dialog
        initialize(ccddMain.getMessageIDHandler(), ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Display the owner, message ID name, and message ID dialog
     *
     * @param messageIDHandler
     *            message ID handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void initialize(CcddMessageIDHandler messageIDHandler, Component parent)
    {
        final List<String[]> msgIDs = messageIDHandler.getMessageOwnersNamesAndIDs(MessageIDSortOrder.BY_OWNER,
                                                                                   false,
                                                                                   parent);

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

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the table to display the message IDs and names
        final CcddJTableHandler msgIDTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow multiple line display in the all columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /**************************************************************************************
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column)
            {
                return column == MsgIDTableColumnInfo.OWNER.ordinal()
                       || column == MsgIDTableColumnInfo.PATH.ordinal();
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return !isPath && column == MsgIDTableColumnInfo.PATH.ordinal();
            }

            /**************************************************************************************
             * Load the message ID data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Build the message ID table information
                Object[][] messageIDData = getMessageIDsToDisplay(msgIDs);

                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(messageIDData,
                                            MsgIDTableColumnInfo.getColumnNames(),
                                            null,
                                            MsgIDTableColumnInfo.getToolTips(),
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

                // Get the column index in model coordinates
                int columnModel = convertColumnIndexToModel(column);

                // Check if the cell doesn't have the focus or is selected (the focus and selection
                // highlight colors override the invalid highlight color), and if this is the
                // message name or ID column
                if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                    && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor()
                    && columnModel != MsgIDTableColumnInfo.OWNER.ordinal())
                {
                    // Get the row index in model coordinates
                    int rowModel = convertRowIndexToModel(row);

                    // Get a reference to the table model to shorten subsequent calls
                    TableModel tableModel = getModel();

                    // Get the contents of the column
                    String value = tableModel.getValueAt(rowModel, columnModel).toString();

                    // Check if the value isn't blank
                    if (!value.isEmpty())
                    {
                        // Step through each row in the table
                        for (int checkRow = 0; checkRow < tableModel.getRowCount(); checkRow++)
                        {
                            // Check if this isn't the same row as the one being updated and if the
                            // text matches that in another row of the same column
                            if (rowModel != checkRow
                                && tableModel.getValueAt(checkRow, columnModel).toString().equals(value))
                            {
                                // Change the cell's background color to indicate it has the same
                                // value as another cell in the same column
                                comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                                break;
                            }
                        }
                    }
                }

                return comp;
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(msgIDTable);

        // Set up the field table parameters
        msgIDTable.setFixedCharacteristics(scrollPane,
                                           false,
                                           ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                           TableSelectionMode.SELECT_BY_CELL,
                                           true,
                                           ModifiableColorInfo.TABLE_BACK.getColor(),
                                           false,
                                           true,
                                           ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                           true);

        // Define the panel to contain the table
        JPanel msgIDTblPnl = new JPanel();
        msgIDTblPnl.setLayout(new BoxLayout(msgIDTblPnl, BoxLayout.X_AXIS));
        msgIDTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        msgIDTblPnl.add(scrollPane);

        // Add the table to the dialog
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(msgIDTblPnl, gbc);

        // Create a table opener for the Open tables command
        final TableOpener opener = new TableOpener()
        {
            /**************************************************************************************
             * Check if the field owner is a table
             *
             * @return true if the field owner is a table
             *************************************************************************************/
            @Override
            protected boolean isApplicable(String tableName)
            {
                // Remove any HTML tags from the table name
                tableName = CcddUtilities.removeHTMLTags(tableName);

                return !tableName.startsWith(CcddFieldHandler.getFieldGroupName(""))
                       && !tableName.startsWith("Tlm:");
            }

            /**************************************************************************************
             * Include the structure path, is applicable, with the table name
             *************************************************************************************/
            @Override
            protected String cleanUpTableName(String tableName, int row)
            {
                return getOwnerWithPath(tableName,
                                        CcddUtilities.removeHTMLTags(msgIDTable.getModel().getValueAt(row,
                                                                                                      MsgIDTableColumnInfo.PATH.ordinal())
                                                                               .toString()));
            }
        };

        // Open tables button
        JButton btnOpen = CcddButtonPanelHandler.createButton("Open",
                                                              TABLE_ICON,
                                                              KeyEvent.VK_O,
                                                              "Open the table(s) associated with the selected message ID(s)");

        // Add a listener for the Open button
        btnOpen.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Open the table(s) associated with the selected message ID(s)
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                opener.openTables(msgIDTable, MsgIDTableColumnInfo.OWNER.ordinal());
            }
        });

        // Print message ID table button
        JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                               PRINT_ICON,
                                                               KeyEvent.VK_P,
                                                               "Print the message ID table");

        // Add a listener for the Print button
        btnPrint.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Print the message ID data table
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                msgIDTable.printTable("Message ID owners, names, and ID values",
                                      null,
                                      CcddMessageIDDialog.this,
                                      PageFormat.LANDSCAPE);
            }
        });

        // Close button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the message ID dialog");

        // Add a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the message ID dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                closeDialog();
            }
        });

        // Create a button panel and add the buttons to it
        JPanel buttonPnl = new JPanel();
        buttonPnl.add(btnOpen);
        buttonPnl.add(btnPrint);
        buttonPnl.add(btnClose);

        // Display the dialog
        showOptionsDialog(parent, dialogPnl, buttonPnl, null, "Show All Message IDs", true);
    }

    /**********************************************************************************************
     * Build the message ID information array
     *
     * @param msgIDs
     *            list containing the message ID owners, names, and ID values
     *
     * @return Array containing the message ID information
     *********************************************************************************************/
    private Object[][] getMessageIDsToDisplay(List<String[]> msgIDs)
    {
        isPath = false;
        List<Object[]> ownerMsgIDs = new ArrayList<Object[]>();

        // Step through each message ID
        for (String[] msgID : msgIDs)
        {
            // Get the message ID owner's name
            String ownerName = msgID[MsgIDListColumnIndex.OWNER.ordinal()];

            String pathName = "";

            // Check that the owner isn't a group or telemetry scheduler
            if (!ownerName.startsWith(CcddFieldHandler.getFieldGroupName(""))
                && !ownerName.startsWith("Tlm:"))
            {
                // Get the index of the last comma in the field table path & name
                int commaIndex = ownerName.lastIndexOf(",");

                // Check if a comma was found in the table path & name
                if (commaIndex != -1)
                {
                    // Extract the path name from the table path and name
                    pathName = ownerName.substring(0, commaIndex);

                    // Count the number of commas in the path name, which indicates the structure
                    // nest level
                    int depth = pathName.split(",").length;

                    // Set the indentation
                    String indent = "";

                    // Step through each nest level
                    for (int count = 0; count < depth; count++)
                    {
                        // Add spaces to the indentation. This aids in identifying the structure
                        // members
                        indent += "&#160;&#160;";
                    }

                    // Remove the path and leave only the table name
                    ownerName = indent + ownerName.substring(commaIndex + 1);

                    // Add spaces after any remaining commas in the path
                    pathName = pathName.replaceAll(",", ", ");

                    // Check if this owner has a path (i.e., it's a structure table)
                    if (!pathName.isEmpty())
                    {
                        // Set the flag to indicate at least one of the owners has a path
                        isPath = true;
                    }
                }
            }

            // Add the message ID information to the list
            ownerMsgIDs.add(new Object[] {CcddUtilities.highlightDataType(ownerName),
                                          CcddUtilities.highlightDataType(pathName),
                                          msgID[MsgIDListColumnIndex.MESSAGE_NAME.ordinal()],
                                          msgID[MsgIDListColumnIndex.MESSAGE_ID.ordinal()]});
        }

        return ownerMsgIDs.toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Get the owner name with path, if applicable (child tables of a structure table have a path)
     *
     * @param ownerName
     *            table or group owner name
     *
     * @param path
     *            table path; blank if none
     *
     * @return Table or group name with path, if applicable
     *********************************************************************************************/
    private String getOwnerWithPath(String ownerName, String path)
    {
        // Remove and leading spaces used for indenting child structure names
        ownerName = ownerName.trim();

        // Check if the owner has a path
        if (!path.isEmpty())
        {
            // Prepend the path to the table name
            ownerName = path.replaceAll(" ", "") + "," + ownerName;
        }

        return ownerName;
    }
}
