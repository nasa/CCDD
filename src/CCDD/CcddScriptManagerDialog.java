/**
 * CFS Command & Data Dictionary script association manager dialog. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.EXECUTE_ALL_ICON;
import static CCDD.CcddConstants.EXECUTE_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.LAST_SCRIPT_FILE;
import static CCDD.CcddConstants.LIST_TABLE_DESC_SEPARATOR;
import static CCDD.CcddConstants.LIST_TABLE_SEPARATOR;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPTS_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary script association manager dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddScriptManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddScriptHandler scriptHandler;
    private CcddTableTreeHandler tableTree;

    // Components referenced by multiple methods
    private JButton btnSelectScript;
    private JButton btnAddAssn;
    private JButton btnExecute;
    private JButton btnStoreAssns;
    private JButton btnRemoveAssn;
    private JButton btnExecuteAll;
    private JButton btnClose;
    private JTextField scriptFld;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    /**************************************************************************
     * Script association manager dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddScriptManagerDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        scriptHandler = ccddMain.getScriptHandler();

        // Create the file output selection dialog
        initialize();
    }

    /**************************************************************************
     * Perform the steps needed following execution of script association
     * updates to the database
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doAssnUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Check if the script association had been previously committed
            if (scriptHandler.getCommittedAssociations() != null)
            {
                // Clear the current committed script association list
                scriptHandler.getCommittedAssociations().clear();
            }

            // Store the script associations in the committed script
            // associations list
            scriptHandler.setCommittedAssociations(createAssociationsFromList());
        }

        // Reenable the dialog buttons
        setControlsEnabled(true);
    }

    /**************************************************************************
     * Create the script association manager dialog
     *************************************************************************/
    private void initialize()
    {
        // Check if there are any open editors with uncommitted changes and if
        // so check that the user confirms ignoring the changes
        if (ccddMain.ignoreUncommittedChanges("Script Manager",
                                              "Ignore changes?",
                                              false,
                                              null,
                                              CcddScriptManagerDialog.this))
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
                                                            new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                       LABEL_HORIZONTAL_SPACING,
                                                                       LABEL_VERTICAL_SPACING / 2,
                                                                       LABEL_HORIZONTAL_SPACING),
                                                            0,
                                                            0);

            // Create a panel to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            dialogPnl.setBorder(BorderFactory.createEmptyBorder());

            // Add the script selection components to the dialog
            dialogPnl.add(createScriptSelectionPanel(), gbc);

            // Add the table selection panel components to the dialog
            gbc.weighty = 1.0;
            gbc.insets.top = LABEL_VERTICAL_SPACING;
            gbc.gridy++;
            dialogPnl.add(createSelectionPanel("Select one or more tables",
                                               TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION),
                          gbc);

            // Add the script association list components to the dialog
            gbc.insets.left = LABEL_HORIZONTAL_SPACING;
            gbc.insets.bottom = 0;
            gbc.gridy++;
            dialogPnl.add(createScriptAssnPanelWithButtons(), gbc);

            // Create the button panel
            JPanel buttonPnl = new JPanel();

            // Define the buttons for the lower panel:
            // Add association button
            btnAddAssn = CcddButtonPanelHandler.createButton("Add",
                                                             INSERT_ICON,
                                                             KeyEvent.VK_A,
                                                             "Add the currently defined script association");

            // Add a listener for the Add button
            btnAddAssn.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Add a new script association
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check that a script is specified
                    if (!scriptFld.getText().trim().isEmpty())
                    {
                        addAssociation();
                    }
                    // The script file field is blank
                    else
                    {
                        // Inform the user that a script must be selected
                        new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                  "<html><b>Must enter or select a script",
                                                                  "Script Missing",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }
            });

            // Remove script association(s) button
            btnRemoveAssn = CcddButtonPanelHandler.createButton("Remove",
                                                                DELETE_ICON,
                                                                KeyEvent.VK_R,
                                                                "Remove the selected script association(s)");

            // Add a listener for the Remove button
            btnRemoveAssn.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Remove the selected script association(s)
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if at least one script association is selected
                    if (!scriptHandler.getAssociationsList().isSelectionEmpty())
                    {
                        removeAssociations();
                    }
                }
            });

            // Script execution button
            btnExecute = CcddButtonPanelHandler.createButton("Execute",
                                                             EXECUTE_ICON,
                                                             KeyEvent.VK_E,
                                                             "Execute the selected script association(s)");

            // Add a listener for the Execute button
            btnExecute.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Execute the selected script association(s)
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if at least one script association is selected
                    if (!scriptHandler.getAssociationsList().isSelectionEmpty())
                    {
                        scriptHandler.executeScriptAssociations(CcddScriptManagerDialog.this,
                                                                tableTree);
                    }
                }
            });

            // Execute all script associations button
            btnExecuteAll = CcddButtonPanelHandler.createButton("Execute All",
                                                                EXECUTE_ALL_ICON,
                                                                KeyEvent.VK_A,
                                                                "Execute all of the script associations");

            // Add a listener for the Execute All button
            btnExecuteAll.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Execute all of the script associations
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if at least one script association exists
                    if (!scriptHandler.getAssociationsModel().isEmpty()
                        && new CcddDialogHandler().showMessageDialog(CcddScriptManagerDialog.this,
                                                                     "<html><b>Execute all script associations?",
                                                                     "Execute All",
                                                                     JOptionPane.QUESTION_MESSAGE,
                                                                     DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                    {
                        // Select all script associations
                        scriptHandler.getAssociationsList().setSelectionInterval(0,
                                                                                 scriptHandler.getAssociationsModel().size()
                                                                                 - 1);

                        // Execute all script associations
                        scriptHandler.executeScriptAssociations(CcddScriptManagerDialog.this,
                                                                tableTree);
                    }
                }
            });

            // Store script associations button
            btnStoreAssns = CcddButtonPanelHandler.createButton("Store",
                                                                STORE_ICON,
                                                                KeyEvent.VK_S,
                                                                "Store the updated script associations to the database");

            // Add a listener for the Store button
            btnStoreAssns.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Store the script associations in the database
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if the script associations have changed since the
                    // last database commit and that the user confirms storing
                    // the script associations
                    if (isAssociationsChanged()
                        && new CcddDialogHandler().showMessageDialog(CcddScriptManagerDialog.this,
                                                                     "<html><b>Store script associations?",
                                                                     "Store Associations",
                                                                     JOptionPane.QUESTION_MESSAGE,
                                                                     DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                    {
                        // Disable the dialog buttons until the updates
                        // complete
                        setControlsEnabled(false);

                        // Store the script associations list into the database
                        dbTable.storeInformationTable(InternalTable.ASSOCIATIONS,
                                                      createAssociationsFromList(),
                                                      null,
                                                      CcddScriptManagerDialog.this);
                    }
                }
            });

            // Close button
            btnClose = CcddButtonPanelHandler.createButton("Close",
                                                           CLOSE_ICON,
                                                           KeyEvent.VK_C,
                                                           "Close the script association manager");

            // Add a listener for the Close button
            btnClose.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Close the script association dialog
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Check if there are no changes to the script associations
                    // or if the user elects to discard the changes
                    if (!isAssociationsChanged()
                        || new CcddDialogHandler().showMessageDialog(CcddScriptManagerDialog.this,
                                                                     "<html><b>Discard changes?",
                                                                     "Discard Changes",
                                                                     JOptionPane.QUESTION_MESSAGE,
                                                                     DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                    {
                        // Close the dialog
                        closeDialog();
                    }
                }
            });

            // Add buttons in the order in which they'll appear (left to right,
            // top to bottom)
            buttonPnl.add(btnAddAssn);
            buttonPnl.add(btnExecute);
            buttonPnl.add(btnStoreAssns);
            buttonPnl.add(btnRemoveAssn);
            buttonPnl.add(btnExecuteAll);
            buttonPnl.add(btnClose);

            // Distribute the buttons across two rows
            setButtonRows(2);

            // Display the script association management dialog
            showOptionsDialog(ccddMain.getMainFrame(),
                              dialogPnl,
                              buttonPnl,
                              "Manage Script Associations",
                              true);
        }
    }

    /**************************************************************************
     * Create the script selection panel
     * 
     * @return JPanel containing the script selection panel
     *************************************************************************/
    private JPanel createScriptSelectionPanel()
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
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel for the script selection components
        JPanel scriptPnl = new JPanel(new GridBagLayout());

        // Create the script selection dialog labels and fields
        JLabel scriptLbl = new JLabel("Enter or select a script file");
        scriptLbl.setFont(LABEL_FONT_BOLD);
        scriptPnl.add(scriptLbl, gbc);

        // Create a text field for entering & displaying the script file name
        scriptFld = new JTextField("");
        scriptFld.setFont(LABEL_FONT_PLAIN);
        scriptFld.setEditable(true);
        scriptFld.setForeground(Color.BLACK);
        scriptFld.setBackground(Color.WHITE);
        scriptFld.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                               Color.LIGHT_GRAY,
                                                                                               Color.GRAY),
                                                               BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.gridy++;
        scriptPnl.add(scriptFld, gbc);

        // Create a button for choosing an output script
        btnSelectScript = CcddButtonPanelHandler.createButton("Select...",
                                                              SCRIPTS_ICON,
                                                              KeyEvent.VK_S,
                                                              "Open the script selection dialog");

        // Add a listener for the Select script button
        btnSelectScript.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Select a script
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Allow the user to select the script file path + name
                File[] scriptFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                           CcddScriptManagerDialog.this,
                                                                           null,
                                                                           scriptHandler.getExtensions(),
                                                                           false,
                                                                           false,
                                                                           "Select Script",
                                                                           LAST_SCRIPT_FILE,
                                                                           DialogOption.OK_CANCEL_OPTION);

                // Check if a script file is selected
                if (scriptFile != null && scriptFile[0] != null)
                {
                    // Store the script file name in the program preferences
                    // backing store
                    ccddMain.getProgPrefs().put(LAST_SCRIPT_FILE,
                                                scriptFile[0].getAbsolutePath());

                    // Display the file name in the script name field
                    scriptFld.setText(scriptFile[0].getAbsolutePath());
                }
            }
        });

        // Add the select script button to the dialog
        gbc.weightx = 0.0;
        gbc.insets.right = 0;
        gbc.gridx++;
        scriptPnl.add(btnSelectScript, gbc);

        return scriptPnl;
    }

    /**************************************************************************
     * Create the table tree selection panel
     * 
     * @param labeltext
     *            label to display above the table tree
     * 
     * @param tableSelect
     *            table tree item selection model
     * 
     * @return JPanel containing the selection panel
     *************************************************************************/
    private JPanel createSelectionPanel(String labelText,
                                        int tableSelect)
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
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   0),
                                                        0,
                                                        0);

        // Create a panel to hold the table selection components of the dialog
        JPanel tablePnl = null;

        // Build the table tree showing only table parents with their child
        // tables
        tableTree = new CcddTableTreeHandler(ccddMain,
                                             new CcddGroupHandler(ccddMain,
                                                                  ccddMain.getMainFrame()),
                                             TableTreeType.INSTANCE_ONLY,
                                             true,
                                             false,
                                             ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a table in the table tree
             *****************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent table tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that don't represent a table
                    tableTree.clearNonTableNodes(1);

                    // Reset the flag to allow table tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Check if the database contains no tables
        if (tableTree.getRowCount() != (tableTree.isRootVisible()
                                                                 ? 1
                                                                 : 0))
        {
            // Create panels to hold the components of the dialog
            tablePnl = new JPanel(new GridBagLayout());

            // Create the table group selection dialog labels and fields
            JLabel dlgLabel = new JLabel(labelText);
            dlgLabel.setFont(LABEL_FONT_BOLD);
            tablePnl.add(dlgLabel, gbc);

            // Create the choices and selected lists and add them to the dialog
            gbc.insets.top = 0;
            gbc.insets.left = LABEL_HORIZONTAL_SPACING;
            gbc.weighty = 1.0;
            gbc.gridy++;
            tablePnl.add(tableTree.createTreePanel(null,
                                                   tableSelect,
                                                   ccddMain.getMainFrame()),
                         gbc);
        }
        // No tables are stored in the database
        else
        {
            // Inform the user that no table exists in this database
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Project '"
                                                          + dbControl.getDatabase()
                                                          + "' has no tables",
                                                      "No Tables",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return tablePnl;
    }

    /**************************************************************************
     * Create a panel to contain the list of script associations with up & down
     * list item movement buttons alongside
     * 
     * @return JPanel containing the combined script association list and
     *         buttons
     *************************************************************************/
    private JPanel createScriptAssnPanelWithButtons()
    {
        // Create the buttons
        JButton upBtn = new JButton();
        JButton downBtn = new JButton();

        // Create the 'move item(s) up in the saved script associations list'
        // button
        upBtn.setIcon(new ImageIcon(getClass().getResource(UP_ICON)));
        upBtn.setToolTipText("Move selected script association(s) up");

        // Create a listener for the move item(s) up button
        upBtn.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Move the selected item(s) in the saved script associations list
             * up one line
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                CcddUtilities.moveListItems(scriptHandler.getAssociationsList(),
                                            scriptHandler.getAssociationsModel(),
                                            scriptHandler.getAssociationsLongModel(),
                                            -1);
            }
        });

        // Create the 'move item(s) down in the saved script associations list'
        // button
        downBtn.setIcon(new ImageIcon(getClass().getResource(DOWN_ICON)));
        downBtn.setToolTipText("Move selected script association(s) down");

        // Create a listener for the move item(s) down button
        downBtn.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Move the selected item(s) in the saved script associations list
             * down one line
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                CcddUtilities.moveListItems(scriptHandler.getAssociationsList(),
                                            scriptHandler.getAssociationsModel(),
                                            scriptHandler.getAssociationsLongModel(),
                                            1);
            }
        });

        // Adjust the spacing based on the button and icon widths
        int spacing = 2 - (upBtn.getPreferredSize().width / 2 - upBtn.getIcon().getIconWidth());

        // Set the layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.CENTER,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(0,
                                                                   spacing,
                                                                   0,
                                                                   spacing),
                                                        0,
                                                        0);

        // Create a panel to contain the buttons. Arrange the buttons
        // vertically
        JPanel buttonPnl = new JPanel(new GridBagLayout());
        buttonPnl.setBorder(BorderFactory.createEmptyBorder());

        // Hide everything but the button icons
        upBtn.setOpaque(false);
        upBtn.setContentAreaFilled(false);
        upBtn.setBorderPainted(false);
        downBtn.setOpaque(false);
        downBtn.setContentAreaFilled(false);
        downBtn.setBorderPainted(false);

        // Add the buttons to the panel
        buttonPnl.add(upBtn, gbc);
        gbc.gridy++;
        buttonPnl.add(downBtn, gbc);

        // Reset the initial layout manager characteristics
        gbc = new GridBagConstraints(1,
                                     1,
                                     1,
                                     1,
                                     0.0,
                                     1.0,
                                     GridBagConstraints.LINE_START,
                                     GridBagConstraints.BOTH,
                                     new Insets(LABEL_VERTICAL_SPACING / 2,
                                                0,
                                                0,
                                                0),
                                     0,
                                     0);

        // Create the script association list
        JPanel listPnl = scriptHandler.createScriptAssociationPanel("Script Associations",
                                                                    7,
                                                                    true);

        // Add a listener for association list selection changes
        scriptHandler.getAssociationsList().addListSelectionListener(new ListSelectionListener()
        {
            /******************************************************************
             * Handle a list selection change. Populate the script file field
             * and table tree based on the lowest selected associations list
             * item
             *****************************************************************/
            @Override
            public void valueChanged(ListSelectionEvent lse)
            {
                // Check if a selection is made (for mouse button selection
                // this returns true when the button is pressed, then false
                // when released; for a keyboard input this returns false)
                if (!lse.getValueIsAdjusting())
                {
                    // Get the first selected list item
                    int index = scriptHandler.getAssociationsList().getSelectedIndex();

                    // Check if a list item is selected
                    if (index != -1)
                    {
                        // Separate the selected item into the script name and
                        // table name(s),if any
                        String[] association = scriptHandler.getAssociationsLongModel().get(index).split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR));

                        // Store the script file name with path in the the
                        // script file field
                        scriptFld.setText(association[AssociationsColumn.SCRIPT_FILE.ordinal()]);

                        // Separate the table member portion into the
                        // individual table names
                        String[] tableNames = association[AssociationsColumn.MEMBERS.ordinal()].split(Pattern.quote(LIST_TABLE_SEPARATOR));

                        List<TreePath> paths = new ArrayList<TreePath>();

                        // Step through each table name
                        for (String tableName : tableNames)
                        {
                            // Get the node in the table tree for this table
                            // name
                            ToolTipTreeNode node = tableTree.getNodeFromNodeName(tableName);

                            // Check if the table name is in the tree
                            if (node != null)
                            {
                                // Add the path to the list
                                paths.add(CcddCommonTreeHandler.getPathFromNode(node));
                            }
                        }

                        // Select the associated tables in the table tree
                        tableTree.setSelectionPaths(paths.toArray(new TreePath[0]));
                    }
                }
            }
        });

        // Add up/down list item movement buttons
        listPnl.add(buttonPnl, gbc);

        return listPnl;
    }

    /**************************************************************************
     * Add an association to the script associations list based on the script
     * and table selections
     *************************************************************************/
    private void addAssociation()
    {
        // Get an array of the selected table names
        String[] tables = tableTree.getSelectedTablesWithoutChildren().toArray(new String[0]);

        // Check if no tables are selected
        if (tables.length == 0)
        {
            // Use a blank for the table name
            tables = new String[] {" "};
        }

        // Get a file descriptor for the script file name
        File scriptFile = new File(scriptFld.getText());

        // Check that the script association doesn't already exist in the list
        if (!isAssociationExists(scriptFile.getAbsolutePath(), tables))
        {
            // Create the short and long form script association strings
            String assnShort = scriptFile.getName()
                               + LIST_TABLE_DESC_SEPARATOR;
            String assnLong = scriptFile.getAbsolutePath()
                              + LIST_TABLE_DESC_SEPARATOR;

            // Step through each selected table in the tree
            for (String table : tables)
            {
                // Add the table name to the script association
                assnShort += table + LIST_TABLE_SEPARATOR;
                assnLong += table + LIST_TABLE_SEPARATOR;
            }

            // Remove the trailing table separator
            assnShort = CcddUtilities.removeTrailer(assnShort, LIST_TABLE_SEPARATOR);
            assnLong = CcddUtilities.removeTrailer(assnLong, LIST_TABLE_SEPARATOR);

            // Check if the file exists; if not set the color to highlight the
            // unavailable association
            String textColor = scriptFile.exists()
                                                  ? ""
                                                  : DISABLED_TEXT_COLOR;

            // Add the association to the script association list
            scriptHandler.getAssociationsModel().addElement(textColor + assnShort);
            scriptHandler.getAssociationsLongModel().addElement(assnLong);

            // Scroll the association list to the new entry and select it
            int lastIndex = scriptHandler.getAssociationsModel().getSize() - 1;
            scriptHandler.getAssociationsList().ensureIndexIsVisible(lastIndex);
            scriptHandler.getAssociationsList().setSelectedIndex(lastIndex);
        }
    }

    /**************************************************************************
     * Remove the selected association(s) from the script associations list
     *************************************************************************/
    private void removeAssociations()
    {
        // Get an array of the selected script associations
        int[] selected = scriptHandler.getAssociationsList().getSelectedIndices();

        // Clear the selected script association(s) highlighting
        scriptHandler.getAssociationsList().clearSelection();

        // Step through each selected script association
        for (int index = selected.length - 1; index >= 0; index--)
        {
            // Remove the script association from the list
            scriptHandler.getAssociationsModel().remove(selected[index]);
            scriptHandler.getAssociationsLongModel().remove(selected[index]);
        }
    }

    /**************************************************************************
     * Create the script association definitions from the script association
     * list
     * 
     * @return The script association definition list
     *************************************************************************/
    private List<String[]> createAssociationsFromList()
    {
        List<String[]> currentAssociations = new ArrayList<String[]>();

        // Step through each defined script association
        for (Object assnInfo : scriptHandler.getAssociationsLongModel().toArray())
        {
            // Parse the definition into the tables and types, then add the
            // script association definition to the script association list
            currentAssociations.add(assnInfo.toString().split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR)));
        }

        return currentAssociations;
    }

    /**************************************************************************
     * Check if the script associations differ from those last committed to the
     * database
     * 
     * @return true if the script associations have changed
     *************************************************************************/
    private boolean isAssociationsChanged()
    {
        boolean hasChanges = false;

        // Get references to the current and committed associations in order to
        // shorten subsequent calls
        DefaultListModel<String> curr = scriptHandler.getAssociationsLongModel();
        List<String[]> comm = scriptHandler.getCommittedAssociations();

        // Check if the number of script associations in the dialog match the
        // number stored in the database
        if (comm != null && curr.getSize() == comm.size())
        {
            // Step through each association
            for (int index = 0; index < curr.getSize(); index++)
            {
                // Split the committed script association definition to
                // separate the script from the table names
                String[] scriptAndTable = curr.get(index).toString().split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR));

                // Check if the script association differs between the current
                // and committed lists
                if (!scriptAndTable[AssociationsColumn.SCRIPT_FILE.ordinal()].equals(comm.get(index)[AssociationsColumn.SCRIPT_FILE.ordinal()])
                    || !scriptAndTable[AssociationsColumn.MEMBERS.ordinal()].equals(comm.get(index)[AssociationsColumn.MEMBERS.ordinal()]))
                {
                    // Set the flag to indicate no match was found and quit
                    // searching
                    hasChanges = true;
                    break;
                }
            }
        }
        // The number of script associations differs
        else
        {
            // Set the flag to indicate a change exists
            hasChanges = true;
        }

        return hasChanges;
    }

    /**************************************************************************
     * Compare a script association to the existing ones in the list
     * 
     * @param scriptName
     *            script file path + name
     * 
     * @param tables
     *            array of tables referenced by the script association
     * 
     * @return true if the script association already exists in the list
     *************************************************************************/
    private boolean isAssociationExists(String scriptName, String[] tables)
    {
        boolean isExists = false;

        // Step through the committed script associations
        for (Object assn : scriptHandler.getAssociationsLongModel().toArray())
        {
            // Split the committed script association definition to separate
            // the script from the table names
            String[] scriptAndTable = assn.toString().split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR));

            // Check if the script and tables match between the two script
            // associations
            if (scriptName.equals(scriptAndTable[AssociationsColumn.SCRIPT_FILE.ordinal()].toString())
                && CcddUtilities.isArraySetsEqual(tables,
                                                  scriptAndTable[AssociationsColumn.MEMBERS.ordinal()].toString().split(Pattern.quote(LIST_TABLE_SEPARATOR))))
            {
                // Set the flag to indicate a match and quit searching
                isExists = true;
                break;
            }
        }

        return isExists;
    }
}
