/**
 * CFS Command and Data Dictionary variable padding selection dialog.
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
import java.util.Collections;
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
 * CFS Command and Data Dictionary variable padding selection dialog class
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
        dialogPnl.add(prototypeTree.createTreePanel("Structure Tables",
                                                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                    false,
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
                    // Close the dialog
                    CcddPaddingDialog.this.closeDialog();

                    // Add/update the padding for the selected table(s)
                    addUpdatePadding(ccddMain, PadOperationType.ADD_UPDATE, prototypeTree);
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
                    // Close the dialog
                    CcddPaddingDialog.this.closeDialog();

                    // Remove the padding from the selected table(s)
                    addUpdatePadding(ccddMain, PadOperationType.REMOVE, prototypeTree);
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
     * Perform the specified padding operation on the selected tables. If a selected table is the
     * prototype for a child table, then include the prototype name for every table in the child's
     * path in the tables to be altered
     *
     * @param ccddMain
     *            main class reference
     *
     * @param padOpType
     *            padding operation type: REMOVE to remove padding from the selected and affected
     *            tabled; ADD_UPDATE to add or update the padding for the selected and affected
     *            tables (PadOperationType)
     *
     * @param prototypeTree
     *            reference to the prototype table tree
     *********************************************************************************************/
    private void addUpdatePadding(CcddMain ccddMain,
                                  PadOperationType padOpType,
                                  CcddTableTreeHandler prototypeTree)
    {
        List<String> referencedPrototypeTables = new ArrayList<String>();
        List<String> affectedTables = new ArrayList<String>();

        // Get the list of prototype table(s) selected by the user
        List<String> selectedPrototypeTables = prototypeTree.getSelectedTablesWithoutChildren();

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
        for (String prototypeTable : selectedPrototypeTables)
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
                    if (!selectedPrototypeTables.contains(parentTable)
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

        // Add any table(s) (prototype and instance) that the selected prototype(s) affect to the
        // list of prototype tables
        selectedPrototypeTables.addAll(affectedTables);

        // Step through each selected table and its descendant table(s) (if any). Information on
        // the tables and the prototypes of their descendants is required when determining
        // structure size and alignment
        for (String table : instanceTree.getTablesWithChildren(selectedPrototypeTables))
        {
            // The table path can reference multiple prototypes (the root table plus one for each
            // child structure variable). Each of these prototypes must be included in the
            // referenced tables list
            do
            {
                // Get the prototype for the last child structure variable in the table path
                String protoTable = TableInformation.getPrototypeName(table);

                // Check if the list doesn't already contain this prototype table
                if (!referencedPrototypeTables.contains(protoTable))
                {
                    // Add the prototype table to the list
                    referencedPrototypeTables.add(protoTable);
                }

                // Get the index of the last child structure variable in the table path
                int index = table.lastIndexOf(",");

                // Check if a child structure variable remains in the path
                if (index != -1)
                {
                    // Remove the child structure variable from the table path
                    table = table.substring(0, index);
                }
            } while (table.contains(","));
            // Continue to process the table path, until no child structure variable reference
            // remains
        }

        // Sort the lists alphabetically
        Collections.sort(selectedPrototypeTables, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(referencedPrototypeTables, String.CASE_INSENSITIVE_ORDER);

        // Perform the padding operation
        new CcddPaddingVariableHandler(ccddMain,
                                       padOpType,
                                       selectedPrototypeTables,
                                       referencedPrototypeTables,
                                       CcddPaddingDialog.this);
    }
}
