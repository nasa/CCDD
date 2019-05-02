/**
 * CFS Command and Data Dictionary script tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary script tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddScriptTreeHandler extends CcddInformationTreeHandler
{
    // List to contain the scripts stored in the database
    private List<String[]> storedScripts;

    /**********************************************************************************************
     * Script tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddScriptTreeHandler(CcddMain ccddMain, Component parent)
    {
        super(ccddMain, getScriptInformation(ccddMain, parent), parent);
    }

    /**********************************************************************************************
     * Get the list of scripts stored in the project database
     *
     * @param ccddMain
     *            main class
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return List of scripts stored in the project database. An empty list if no scripts are
     *         stored in the database
     *********************************************************************************************/
    private static List<String[]> getScriptInformation(CcddMain ccddMain, Component parent)
    {
        // Get the list of script files from the database
        String[] scripts = ccddMain.getDbTableCommandHandler().queryScriptTables(parent);
        List<String[]> scriptInformation = new ArrayList<String[]>();

        // Step through each script file
        for (String script : scripts)
        {
            // Separate and store the script name and description
            scriptInformation.add(script.split(",", 2));
        }

        return scriptInformation;
    }

    /**********************************************************************************************
     * Perform initialization steps prior to building the script tree
     *
     * @param ccddMain
     *            main class. This is not used for the script tree
     *
     * @param undoHandler
     *            reference to the undo handler. This is not used for the script tree
     *
     * @param storedScripts
     *            list containing the stored scripts
     *********************************************************************************************/
    @Override
    protected void initialize(CcddMain ccddMain,
                              CcddUndoHandler undoHandler,
                              List<String[]> storedScripts)
    {
        this.storedScripts = storedScripts;
    }

    /**********************************************************************************************
     * Build the script tree
     *
     * @param filterByApp
     *            true if the tree is filtered by application. This is not used for the script tree
     *
     * @param filterValue
     *            rate column name and message name, separated by a back slash. This is not used
     *            for the script tree
     *
     * @param filterFlag
     *            flag used to filter the tree content. Not used for the script tree
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    @Override
    protected void buildTree(boolean filterByApp,
                             String filterValue,
                             boolean filterFlag,
                             Component parent)
    {
        super.buildTree(false, filterValue, filterFlag, parent);

        // Register the tool tip manager for the script tree (otherwise the tool tips aren't
        // displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Step through each stored script
        for (String[] script : storedScripts)
        {
            // Create a node for the script and add it to the script tree
            addInformationNode(script[0], script[1]);
        }
    }

    /**********************************************************************************************
     * Create a script tree panel. The script tree is placed in a scroll pane
     *
     * @param label
     *            script tree title; null to not display a title
     *
     * @param selectionMode
     *            tree item selection mode (single versus multiple)
     *
     * @return JPanel containing the script tree components
     *********************************************************************************************/
    protected JPanel createTreePanel(String label, int selectionMode)
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

        // Set the script tree selection mode
        getSelectionModel().setSelectionMode(selectionMode);

        // Create a panel to contain the script tree
        JPanel treePnl = new JPanel(new GridBagLayout());
        treePnl.setBorder(emptyBorder);

        // Check if a label is provided
        if (label != null && !label.isEmpty())
        {
            // Create the tree labels
            JLabel treeLbl = new JLabel(label);
            treeLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            treeLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
            treePnl.add(treeLbl, gbc);
        }

        // Create the tree scroll pane
        JScrollPane treeScroll = new JScrollPane(this);
        treeScroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                Color.LIGHT_GRAY,
                                                                                                Color.GRAY),
                                                                BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));

        // Add the tree to the panel
        gbc.weighty = 1.0;
        gbc.gridy++;
        treePnl.add(treeScroll, gbc);

        // Set the table tree font and number of rows to display
        setFont(ModifiableFontInfo.TREE_NODE.getFont());
        setVisibleRowCount(10);

        return treePnl;
    }

    /**********************************************************************************************
     * Placeholder - required by information tree but unused in script tree
     *********************************************************************************************/
    @Override
    protected List<String[]> createDefinitionsFromInformation()
    {
        return null;
    }

    /**********************************************************************************************
     * Placeholder - required by information tree but unused in script tree
     *********************************************************************************************/
    @Override
    protected void addInformation(Object information, String nameOfCopy)
    {
    }
}
