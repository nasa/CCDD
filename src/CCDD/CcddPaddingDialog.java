/**
 * CFS Command & Data Dictionary variable padding selection dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_ICON;
import static CCDD.CcddConstants.DEFAULT_INSTANCE_NODE_NAME;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.INSERT_ICON;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.PadOperationType;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command & Data Dictionary variable padding selection dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddPaddingDialog extends CcddDialogHandler
{
    /**********************************************************************************************
     * Variable padding selection dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddPaddingDialog(CcddMain ccddMain)
    {
        // Create the variable padding selection dialog
        initialize(ccddMain);
    }

    /**********************************************************************************************
     * Create the variable padding selection dialog
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    private void initialize(final CcddMain ccddMain)
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        1.0,
                                                        GridBagConstraints.CENTER,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Build the table tree showing both table prototypes and table instances; i.e., parent
        // tables with their child tables (i.e., parents with children)
        final CcddTableTreeHandler prototypeTree = new CcddTableTreeHandler(ccddMain,
                                                                            new CcddGroupHandler(ccddMain,
                                                                                                 null,
                                                                                                 ccddMain.getMainFrame()),
                                                                            TableTreeType.PROTOTYPE_STRUCTURES,
                                                                            true,
                                                                            false,
                                                                            ccddMain.getMainFrame());

        // Add the tree to the dialog
        dialogPnl.add(prototypeTree.createTreePanel("Tables",
                                                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                    ccddMain.getMainFrame()),
                      gbc);

        // Add/update padding button
        JButton btnAddUpdate = CcddButtonPanelHandler.createButton("Add/Update",
                                                                   INSERT_ICON,
                                                                   KeyEvent.VK_A,
                                                                   "Add or update padding for selected table(s)");

        // Add a listener for the Add/Update button
        btnAddUpdate.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Add or update padding for selected table(s)
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if a table is selected
                if (isStructureSelected(prototypeTree))
                {
                    CcddPaddingDialog.this.closeDialog();
                    new CcddPaddingVariableHandler(ccddMain,
                                                   PadOperationType.ADD_UPDATE,
                                                   getPrototypeTables(ccddMain, prototypeTree),
                                                   CcddPaddingDialog.this);
                }
            }
        });

        // Remove padding button
        JButton btnRemove = CcddButtonPanelHandler.createButton("Remove",
                                                                DELETE_ICON,
                                                                KeyEvent.VK_R,
                                                                "Remove padding from selected table(s)");

        // Add a listener for the Remove button
        btnRemove.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Remove padding from selected table(s)
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if a table is selected
                if (isStructureSelected(prototypeTree))
                {
                    CcddPaddingDialog.this.closeDialog();
                    new CcddPaddingVariableHandler(ccddMain,
                                                   PadOperationType.REMOVE,
                                                   getPrototypeTables(ccddMain, prototypeTree),
                                                   CcddPaddingDialog.this);
                }
            }
        });

        // Close padding dialog button
        JButton btnCancel = CcddButtonPanelHandler.createButton("Cancel",
                                                                CANCEL_ICON,
                                                                KeyEvent.VK_C,
                                                                "Close the padding dialog without making any changes");

        // Add a listener for the Cancel button
        btnCancel.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Close the padding dialog
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                CcddPaddingDialog.this.closeDialog();
            }
        });

        // Create a panel for the dialog buttons and add the buttons to the panel
        JPanel buttonPnl = new JPanel();
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());
        buttonPnl.add(btnAddUpdate);
        buttonPnl.add(btnRemove);
        buttonPnl.add(btnCancel);

        // Display the padding selection dialog
        showOptionsDialog(ccddMain.getMainFrame(),
                          dialogPnl,
                          buttonPnl,
                          btnCancel,
                          "Add/Update/Remove Padding",
                          true);
    }

    /**********************************************************************************************
     * Check if at least one table is selected
     *
     * @param prototypeTree
     *            reference to the prototype table tree
     *
     * @return true if at least one table is selected
     *********************************************************************************************/
    private boolean isStructureSelected(CcddTableTreeHandler prototypeTree)
    {
        boolean isSelected = true;

        // Check if no table is selected
        if (prototypeTree.isSelectionEmpty())
        {
            // Inform the user that a table must be selected
            new CcddDialogHandler().showMessageDialog(CcddPaddingDialog.this,
                                                      "<html><b>Must select at least one structure table",
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
            isSelected = false;
        }

        return isSelected;
    }

    /**********************************************************************************************
     * Get a list of the prototype structure table names selected by the user from the table tree.
     * If the selected table is the prototype for a child table, then include the prototype name
     * for every table in the child's path
     *
     * @param ccddMain
     *            main class
     *
     * @param prototypeTree
     *            reference to the prototype table tree
     *
     * @return List containing the names of the prototype tables for which to alter the padding
     *********************************************************************************************/
    private List<String> getPrototypeTables(CcddMain ccddMain, CcddTableTreeHandler prototypeTree)
    {
        // Get the list of prototype table(s) selected by the user
        List<String> prototStructTables = prototypeTree.getSelectedTablesWithoutChildren();

        List<String> affectedTables = new ArrayList<String>();

        // Get an instance table tree
        CcddTableTreeHandler instanceTree = new CcddTableTreeHandler(ccddMain,
                                                                     new CcddGroupHandler(ccddMain,
                                                                                          null,
                                                                                          CcddPaddingDialog.this),
                                                                     TableTreeType.INSTANCE_TABLES,
                                                                     true,
                                                                     false,
                                                                     CcddPaddingDialog.this);

        // Step through each prototype table selected by the user
        for (String prototypeTable : prototStructTables)
        {
            // Step through each instance table that references the selected prototype table
            for (String tablePath : instanceTree.getTableTreePathList(prototypeTable,
                                                                      instanceTree.getNodeByNodeName(DEFAULT_INSTANCE_NODE_NAME),
                                                                      -1))
            {
                // Get the index of the last table in the path
                int index = tablePath.lastIndexOf(",");

                // Step through each table referenced in the instance table's path
                while (index != -1)
                {
                    // Get the parent table name for the last table in the path
                    String parentTable = TableInformation.getParentTable(tablePath);

                    // Check if this table hasn't already been added to the list of affected tables
                    if (!prototStructTables.contains(parentTable)
                        && !affectedTables.contains(parentTable))
                    {
                        // Add the table name to the list of affected tables
                        affectedTables.add(parentTable);
                    }

                    // Remove the last table from the path
                    tablePath = tablePath.substring(0, index);

                    // Get the index of the last table in the path
                    index = tablePath.lastIndexOf(",");
                }
            }
        }

        // Add any table(s) that the selected prototype(s) affect to the list of prototype tables
        prototStructTables.addAll(affectedTables);

        return prototStructTables;
    }
}
