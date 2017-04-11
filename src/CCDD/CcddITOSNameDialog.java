/**
 * CFS Command & Data Dictionary variable path+name to ITOS record name dialog.
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CELL_FONT;
import static CCDD.CcddConstants.TABLE_BACK_COLOR;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.TableSelectionMode;

/******************************************************************************
 * CFS Command & Data Dictionary variable path+name to ITOS record name dialog
 * class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddITOSNameDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddJTableHandler nameTable;

    /**************************************************************************
     * Variable path+name to ITOS record name dialog class constructor
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddITOSNameDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create the variable path+name to ITOS record name dialog
        initialize();
    }

    /**************************************************************************
     * Create the variable path+name to ITOS record name dialog. This is
     * executed in a separate thread since it can take a noticeable amount time
     * to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until
     * the telemetry scheduler initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the variable path+name to ITOS record name dialog in the
        // background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());

            /******************************************************************
             * Build the variable path+name to ITOS record name dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
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

                // Define the panel to contain the table and place it in the
                // editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createVariableITOSTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());
            }

            /******************************************************************
             * Variable path+name to ITOS record name dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Display the variable/ITOS name dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  editorPnl,
                                  "Variable/ITOS Names",
                                  DialogOption.PRINT_OPTION,
                                  true);
            }
        });
    }

    /**************************************************************************
     * Create the variable path+name to ITOS record name table
     *
     * @return Reference to the scroll pane in which the table is placed
     *************************************************************************/
    private JScrollPane createVariableITOSTable()
    {
        // Define the variable/ITOS name JTable
        nameTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
        {
            /******************************************************************
             * Allow multiple line display in all columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /******************************************************************
             * Load the structure table variables and ITOS record equivalents
             * into the table and format the table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Create the variable handler
                CcddVariableConversionHandler variableHandler = new CcddVariableConversionHandler(ccddMain);

                Object[][] tableData = new Object[variableHandler.getAllVariableNameList().size()][2];

                // Step through each row in the structure table
                for (int row = 0; row < variableHandler.getAllVariableNameList().size(); row++)
                {
                    // Get the variable path and name,removing the bit length
                    // (if present)
                    tableData[row][0] = variableHandler.getAllVariableNameList().get(row).toString().replaceFirst("\\:\\d+$", "");

                    // Convert the variable path and name to its ITOS record
                    // equivalent by removing the data types (parent structure
                    // names) from the path and replacing the commas with
                    // periods,
                    tableData[row][1] = variableHandler.getFullVariableName(tableData[row][0].toString(),
                                                                            ".",
                                                                            true);
                }

                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(tableData,
                                            new String[] {"Variable Path + Name",
                                                          "ITOS Record Name"},
                                            null,
                                            null,
                                            null,
                                            new String[] {"Variable name with structure path",
                                                          "Variable's equivalent ITOS name"},
                                            false,
                                            true,
                                            true,
                                            true);
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(nameTable);

        // Set common table parameters and characteristics
        nameTable.setFixedCharacteristics(scrollPane,
                                          false,
                                          ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                          TableSelectionMode.SELECT_BY_CELL,
                                          true,
                                          TABLE_BACK_COLOR,
                                          true,
                                          false,
                                          CELL_FONT,
                                          true);

        return scrollPane;
    }
}
