/**
 * CFS Command & Data Dictionary variable paths & names dialog. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
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
 * CFS Command & Data Dictionary variable paths & names dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddVariablesDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddJTableHandler variableTable;

    // Total number of variables
    private int numVariables;

    /**************************************************************************
     * Variable paths & names dialog class constructor
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddVariablesDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create the variable paths & names dialog
        initialize();
    }

    /**************************************************************************
     * Create the variable paths & names dialog. This is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The
     * GUI menu commands, however, are disabled until the telemetry scheduler
     * initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the variable paths & names dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());

            /******************************************************************
             * Build the variable paths & names dialog
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
                tablePnl.add(createVariableTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());
            }

            /******************************************************************
             * Variable paths & names dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Display the variable name dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  editorPnl,
                                  "Variable Paths & Names (" + numVariables + " total)",
                                  DialogOption.PRINT_OPTION,
                                  true);
            }
        });
    }

    /**************************************************************************
     * Create the variable paths & names table
     *
     * @return Reference to the scroll pane in which the table is placed
     *************************************************************************/
    private JScrollPane createVariableTable()
    {
        // Define the variable paths & names JTable
        variableTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
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
             * Load the structure table variables paths & names into the table
             * and format the table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Create the variable handler
                CcddVariableConversionHandler variableHandler = new CcddVariableConversionHandler(ccddMain);

                // Store the total number of variables
                numVariables = variableHandler.getAllVariableNameList().size();

                Object[][] tableData = new Object[numVariables][2];

                // Step through each row in the structure table
                for (int row = 0; row < numVariables; row++)
                {
                    // Get the variable path and name,removing the bit length
                    // (if present)
                    tableData[row][0] = variableHandler.getAllVariableNameList().get(row).toString();

                    // Display the variable path and name without the data
                    // types, replacing the commas with periods, and by
                    // changing the array member left brackets to underscores
                    // and removing the array member right brackets
                    tableData[row][1] = variableHandler.getFullVariableName(tableData[row][0].toString(),
                                                                            ".",
                                                                            true);
                }

                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(tableData,
                                            new String[] {"Application Format",
                                                          "Variable Name Only"},
                                            null,
                                            null,
                                            null,
                                            new String[] {"Variable name with structure path as used within the application",
                                                          "Variable name without data types"},
                                            false,
                                            true,
                                            true,
                                            true);
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(variableTable);

        // Set common table parameters and characteristics
        variableTable.setFixedCharacteristics(scrollPane,
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
