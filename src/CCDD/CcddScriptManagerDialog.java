/**
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.EXECUTE_ICON;
import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.REPLACE_ICON;
import static CCDD.CcddConstants.SCRIPTS_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.AssociationsTableColumnInfo;
import CCDD.CcddConstants.AvailabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableInsertionPoint;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary script association manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddScriptManagerDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddScriptHandler scriptHandler;
    private CcddTableTreeHandler tableTree;
    private CcddJTableHandler assnsTable;

    // Components referenced by multiple methods
    private JButton btnSelectScript;
    private JButton btnStoreAssns;
    private JTextField scriptNameFld;
    private JTextField nameFld;
    private JTextArea descriptionFld;
    private JButton btnExecute;
    private Border border;

    // Array to contain the script association table data
    private Object[][] committedAssnsData;

    // Flag that indicates if an addition to the associations table is in progress
    private boolean isAddingAssn = false;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Dialog title
    private static final String DIALOG_TITLE = "Manage Script Associations";

    /**********************************************************************************************
     * Script association manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddScriptManagerDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        scriptHandler = ccddMain.getScriptHandler();

        // Set the reference to the script associations manager in the script handler
        scriptHandler.setScriptDialog(this);

        // Create the script association manager dialog
        initialize();
    }

    /**********************************************************************************************
     * Enable/disable the script manager dialog menu controls
     *
     * @param enable
     *            true to enable the controls; false to disable
     *********************************************************************************************/
    @Override
    protected void setControlsEnabled(boolean enable)
    {
        super.setControlsEnabled(enable);

        // Enable/disable the Store button based on the user's access level
        btnStoreAssns.setEnabled(enable && dbControl.isAccessReadWrite());
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of script association updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doAssnUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Store the current table data as the last committed
            committedAssnsData = assnsTable.getTableData(true);

            // Clear the undo/redo cell edits stack
            assnsTable.getUndoManager().discardAllEdits();
        }

        // Reenable the dialog buttons and redraw the associations table
        setControlsEnabled(true);
        assnsTable.repaint();
    }

    /**********************************************************************************************
     * Create the script association manager dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Check if there are any open editors with uncommitted changes and if so check that the
        // user confirms ignoring the changes
        if (ccddMain.ignoreUncommittedChanges("Script Manager",
                                              "Ignore changes?",
                                              false,
                                              null,
                                              ccddMain.getMainFrame()))
        {
            // Build the script association manager dialog in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                // Create panels to hold the components of the dialog
                JPanel dialogPnl = new JPanel(new GridBagLayout());
                JPanel buttonPnl = new JPanel();

                /**********************************************************************************
                 * Build the script association manager dialog
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    // Create borders for the input fields
                    border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                Color.LIGHT_GRAY,
                                                                                                Color.GRAY),
                                                                BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));
                    Border emptyBorder = BorderFactory.createEmptyBorder();

                    // Set the initial layout manager characteristics
                    GridBagConstraints gbc = new GridBagConstraints(0,
                                                                    0,
                                                                    1,
                                                                    1,
                                                                    0.0,
                                                                    0.0,
                                                                    GridBagConstraints.LINE_START,
                                                                    GridBagConstraints.BOTH,
                                                                    new Insets(0,
                                                                               0,
                                                                               ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                               ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                                    0,
                                                                    0);

                    dialogPnl.setBorder(emptyBorder);

                    // Create a panel to contain the script file name, association name, and
                    // association description labels and fields
                    JPanel inputPnl = new JPanel(new GridBagLayout());

                    // Add the script file selection components to the input panel
                    inputPnl.add(createScriptSelectionPanel(), gbc);

                    // Create the name label and field, and add these to the input panel
                    JLabel nameLbl = new JLabel("Script association name");
                    nameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                    gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                    gbc.gridy++;
                    inputPnl.add(nameLbl, gbc);
                    nameFld = new JTextField("", 1);
                    nameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                    nameFld.setEditable(true);
                    nameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                    nameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                    nameFld.setBorder(border);
                    gbc.insets.top = 0;
                    gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                    gbc.weightx = 1.0;
                    gbc.gridy++;
                    inputPnl.add(nameFld, gbc);

                    // Create the description label and field, and add these to the input panel
                    JLabel descriptionLbl = new JLabel("Script association description");
                    descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                    gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                    gbc.insets.left = 0;
                    gbc.weightx = 0.0;
                    gbc.gridy++;
                    inputPnl.add(descriptionLbl, gbc);
                    descriptionFld = new JTextArea("", 3, 1);
                    descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                    descriptionFld.setEditable(true);
                    descriptionFld.setWrapStyleWord(true);
                    descriptionFld.setLineWrap(true);
                    descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                    descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                    descriptionFld.setBorder(emptyBorder);
                    JScrollPane scrollPane = new JScrollPane(descriptionFld);
                    scrollPane.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                    scrollPane.setBorder(emptyBorder);
                    scrollPane.setViewportBorder(border);
                    scrollPane.setMinimumSize(scrollPane.getPreferredSize());
                    gbc.insets.top = 0;
                    gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                    gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
                    gbc.gridwidth = GridBagConstraints.REMAINDER;
                    gbc.weightx = 1.0;
                    gbc.weighty = 1.0;
                    gbc.gridy++;
                    inputPnl.add(scrollPane, gbc);

                    // Add the input panel and the table selection components to the inputs pane
                    // within a horizontally split pane. Use a separator to denote the split pane's
                    // drag component
                    JSeparator inputSep = new JSeparator(SwingConstants.VERTICAL);
                    inputSep.setForeground(dialogPnl.getBackground().darker());
                    CustomSplitPane inputsPane = new CustomSplitPane(inputPnl,
                                                                     createSelectionPanel("Select associated tables",
                                                                                          TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION),
                                                                     inputSep,
                                                                     JSplitPane.HORIZONTAL_SPLIT);

                    // Add the inputs pane and the script association table components to the
                    // dialog within a vertically split pane. Use a separator to denote the split
                    // pane's drag component
                    JSeparator assnSep = new JSeparator();
                    assnSep.setForeground(dialogPnl.getBackground().darker());
                    gbc.weighty = 1.0;
                    gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                    gbc.insets.bottom = 0;
                    gbc.gridy = 0;
                    dialogPnl.add(new CustomSplitPane(inputsPane,
                                                      scriptHandler.getAssociationsPanel("Script Associations",
                                                                                         true,
                                                                                         CcddScriptManagerDialog.this),
                                                      assnSep,
                                                      JSplitPane.VERTICAL_SPLIT),
                                  gbc);

                    // Get a reference to the script associations table to shorten subsequent calls
                    assnsTable = scriptHandler.getAssociationsTable();

                    // Store the initial table data
                    committedAssnsData = assnsTable.getTableData(true);

                    // Add a listener for script association table row selection changes
                    assnsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener()
                    {
                        /**************************************************************************
                         * Handle a table row selection change. Populate the script description
                         * field, file field, and table tree based on the first selected
                         * associations table row
                         *************************************************************************/
                        @Override
                        public void valueChanged(ListSelectionEvent lse)
                        {
                            // Check if a selection is made (for mouse button selection this
                            // returns true when the button is pressed, then false when released;
                            // for a keyboard input this returns false) and that an association
                            // table update isn't in progress (adding an association table row
                            // could otherwise select tables inadvertently)
                            if (!lse.getValueIsAdjusting() && !isAddingAssn)
                            {
                                // Get the first selected table row
                                int row = assnsTable.getSelectedRow();

                                // Check if a table row item is selected
                                if (row != -1)
                                {
                                    // Store the association name in the the name field
                                    nameFld.setText(assnsTable.getValueAt(row,
                                                                          assnsTable.convertColumnIndexToView(AssociationsTableColumnInfo.NAME.ordinal()))
                                                              .toString());

                                    // Store the association description in the the description
                                    // field
                                    descriptionFld.setText(assnsTable.getValueAt(row,
                                                                                 assnsTable.convertColumnIndexToView(AssociationsTableColumnInfo.DESCRIPTION.ordinal()))
                                                                     .toString());

                                    // Store the script file name with path in the the script file
                                    // field
                                    scriptNameFld.setText(assnsTable.getValueAt(row,
                                                                                assnsTable.convertColumnIndexToView(AssociationsTableColumnInfo.SCRIPT_FILE.ordinal()))
                                                                    .toString());

                                    // Separate the table member portion into the individual table
                                    // names. The line breaks used for HTML formatting must be
                                    // replaced by line feed characters so that the split is made
                                    // correctly
                                    String[] tableNames = CcddUtilities.removeHTMLTags(assnsTable.getValueAt(row,
                                                                                                             assnsTable.convertColumnIndexToView(AssociationsTableColumnInfo.MEMBERS.ordinal()))
                                                                                                 .toString()
                                                                                                 .replaceAll("<br>", "\n"))
                                                                       .split(Pattern.quote(ASSN_TABLE_SEPARATOR));

                                    List<TreePath> paths = new ArrayList<TreePath>();

                                    // Step through each table name
                                    for (String tableName : tableNames)
                                    {
                                        ToolTipTreeNode node;

                                        // Check if the name refers to a group
                                        if (tableName.startsWith(GROUP_DATA_FIELD_IDENT))
                                        {
                                            // Get the node in the table tree for this group
                                            node = tableTree.getNodeByNodeName(tableName.substring(GROUP_DATA_FIELD_IDENT.length()));
                                        }
                                        // The name refers to a table
                                        else
                                        {
                                            // Get the node in the table tree for this table name
                                            node = tableTree.getNodeByNodePath(tableName);
                                        }

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

                    // Define the buttons for the lower panel: Add association button
                    JButton btnAddAssn = CcddButtonPanelHandler.createButton("Add",
                                                                             INSERT_ICON,
                                                                             KeyEvent.VK_A,
                                                                             "Add the currently defined script association");

                    // Add a listener for the Add button
                    btnAddAssn.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Add a new script association
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Check that a script is specified
                            if (!scriptNameFld.getText().trim().isEmpty())
                            {
                                addAssociation(TableInsertionPoint.START, -1);
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
                    JButton btnRemoveAssn = CcddButtonPanelHandler.createButton("Remove",
                                                                                DELETE_ICON,
                                                                                KeyEvent.VK_R,
                                                                                "Remove the selected script association(s)");

                    // Add a listener for the Remove button
                    btnRemoveAssn.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Remove the selected script association(s)
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            removeAssociations();
                        }
                    });

                    // Replace script association(s) button
                    JButton btnReplaceAssn = CcddButtonPanelHandler.createButton("Replace",
                                                                                 REPLACE_ICON,
                                                                                 KeyEvent.VK_P,
                                                                                 "Replace the selected script association");

                    // Add a listener for the Replace button
                    btnReplaceAssn.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Replace the selected script association
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            replaceAssociation();
                        }
                    });

                    // Move Up button
                    JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                            UP_ICON,
                                                                            KeyEvent.VK_U,
                                                                            "Move the selected row(s) up");

                    // Create a listener for the Move Up command
                    btnMoveUp.addActionListener(new ValidateCellActionListener()
                    {
                        /**************************************************************************
                         * Move the selected row(s) up in the table
                         *************************************************************************/
                        @Override
                        protected void performAction(ActionEvent ae)
                        {
                            assnsTable.moveRowUp();
                        }

                        /**************************************************************************
                         * Get the reference to the currently displayed table
                         *************************************************************************/
                        @Override
                        protected CcddJTableHandler getTable()
                        {
                            return assnsTable;
                        }
                    });

                    // Move Down button
                    JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                              DOWN_ICON,
                                                                              KeyEvent.VK_W,
                                                                              "Move the selected row(s) down");

                    // Create a listener for the Move Down command
                    btnMoveDown.addActionListener(new ValidateCellActionListener()
                    {
                        /**************************************************************************
                         * Move the selected row(s) down in the table
                         *************************************************************************/
                        @Override
                        protected void performAction(ActionEvent ae)
                        {
                            assnsTable.moveRowDown();
                        }

                        /**************************************************************************
                         * Get the reference to the currently displayed table
                         *************************************************************************/
                        @Override
                        protected CcddJTableHandler getTable()
                        {
                            return assnsTable;
                        }
                    });

                    // Undo button
                    JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                          UNDO_ICON,
                                                                          KeyEvent.VK_Z,
                                                                          "Undo the last edit action");

                    // Create a listener for the Undo command
                    btnUndo.addActionListener(new ValidateCellActionListener()
                    {
                        /**************************************************************************
                         * Undo the last addition to the script association table
                         *************************************************************************/
                        @Override
                        protected void performAction(ActionEvent ae)
                        {
                            assnsTable.getUndoManager().undo();
                        }

                        /**************************************************************************
                         * Get the reference to the currently displayed table
                         *************************************************************************/
                        @Override
                        protected CcddJTableHandler getTable()
                        {
                            return assnsTable;
                        }
                    });

                    // Redo button
                    JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                          REDO_ICON,
                                                                          KeyEvent.VK_Y,
                                                                          "Redo the last undone edit action");

                    // Create a listener for the Redo command
                    btnRedo.addActionListener(new ValidateCellActionListener()
                    {
                        /**************************************************************************
                         * Redo the last addition to the script association table that was undone
                         *************************************************************************/
                        @Override
                        protected void performAction(ActionEvent ae)
                        {
                            assnsTable.getUndoManager().redo();
                        }

                        /**************************************************************************************
                         * Get the reference to the currently displayed table
                         *************************************************************************************/
                        @Override
                        protected CcddJTableHandler getTable()
                        {
                            return assnsTable;
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
                        /**************************************************************************
                         * Execute the selected script association(s)
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Execute the selected associations
                            scriptHandler.executeScriptAssociations(tableTree,
                                                                    CcddScriptManagerDialog.this);
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
                        /**************************************************************************
                         * Store the script associations in the database
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Check if the script associations have changed since the last
                            // database commit and that the user confirms storing the script
                            // associations
                            if (assnsTable.isTableChanged(committedAssnsData,
                                                          Arrays.asList(new Integer[] {AssociationsTableColumnInfo.AVAILABLE.ordinal()}))
                                && new CcddDialogHandler().showMessageDialog(CcddScriptManagerDialog.this,
                                                                             "<html><b>Store script associations?",
                                                                             "Store Associations",
                                                                             JOptionPane.QUESTION_MESSAGE,
                                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                            {
                                // Disable the dialog buttons until the updates complete
                                setControlsEnabled(false);

                                // Store the script associations list into the database
                                dbTable.storeInformationTableInBackground(InternalTable.ASSOCIATIONS,
                                                                          createAssociationsFromTable(),
                                                                          null,
                                                                          CcddScriptManagerDialog.this);
                            }
                        }
                    });

                    // Close button
                    JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                           CLOSE_ICON,
                                                                           KeyEvent.VK_C,
                                                                           "Close the script association manager");

                    // Add a listener for the Close button
                    btnClose.addActionListener(new ActionListener()
                    {
                        /**************************************************************************
                         * Close the script association dialog
                         *************************************************************************/
                        @Override
                        public void actionPerformed(ActionEvent ae)
                        {
                            // Check if there are no changes to the script associations or if the
                            // user elects to discard the changes
                            if (!isAssociationsChanged()
                                || new CcddDialogHandler().showMessageDialog(CcddScriptManagerDialog.this,
                                                                             "<html><b>Discard changes?",
                                                                             "Discard Changes",
                                                                             JOptionPane.QUESTION_MESSAGE,
                                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                            {
                                // Reset the reference to the script associations manager in the
                                // script handler since the handler remains active)
                                scriptHandler.setScriptDialog(null);

                                // Close the dialog
                                closeFrame();
                            }
                        }
                    });

                    // Add buttons in the order in which they'll appear (left to right, top to
                    // bottom)
                    buttonPnl.add(btnAddAssn);
                    buttonPnl.add(btnReplaceAssn);
                    buttonPnl.add(btnMoveUp);
                    buttonPnl.add(btnUndo);
                    buttonPnl.add(btnStoreAssns);
                    buttonPnl.add(btnRemoveAssn);
                    buttonPnl.add(btnExecute);
                    buttonPnl.add(btnMoveDown);
                    buttonPnl.add(btnRedo);
                    buttonPnl.add(btnClose);

                    // Distribute the buttons across two rows
                    setButtonRows(2);
                }

                /**********************************************************************************
                 * Script association manager dialog creation complete
                 *********************************************************************************/
                @Override
                protected void complete()
                {
                    // Display the script association management dialog
                    createFrame(ccddMain.getMainFrame(),
                                dialogPnl,
                                buttonPnl,
                                btnExecute,
                                DIALOG_TITLE,
                                null);

                    // Enable the manager controls
                    setControlsEnabled(true);
                }
            });
        }
    }

    /**********************************************************************************************
     * Create the script selection panel
     *
     * @return JPanel containing the script selection panel
     *********************************************************************************************/
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
                                                        new Insets(0,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel for the script selection components
        JPanel scriptPnl = new JPanel(new GridBagLayout());

        // Create the script selection dialog labels and fields
        JLabel scriptNameLbl = new JLabel("Enter or select a script file");
        scriptNameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        scriptPnl.add(scriptNameLbl, gbc);

        // Create a text field for entering & displaying the script file name
        scriptNameFld = new JTextField("");
        scriptNameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        scriptNameFld.setEditable(true);
        scriptNameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        scriptNameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        scriptNameFld.setBorder(border);
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        gbc.gridy++;
        scriptPnl.add(scriptNameFld, gbc);

        // Create a button for choosing an output script
        btnSelectScript = CcddButtonPanelHandler.createButton("Select...",
                                                              SCRIPTS_ICON,
                                                              KeyEvent.VK_S,
                                                              "Open the script selection dialog");

        // Add a listener for the Select script button
        btnSelectScript.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select a script
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Allow the user to select the script file path + name
                FileEnvVar[] scriptFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                                 CcddScriptManagerDialog.this,
                                                                                 null,
                                                                                 "script",
                                                                                 scriptHandler.getExtensions(),
                                                                                 false,
                                                                                 "Select Script",
                                                                                 ccddMain.getProgPrefs().get(ModifiablePathInfo.SCRIPT_PATH.getPreferenceKey(),
                                                                                                             null),
                                                                                 DialogOption.OK_CANCEL_OPTION);

                // Check if a script file is selected
                if (scriptFile != null && scriptFile[0] != null)
                {
                    // Store the script file path in the program preferences backing store
                    CcddFileIOHandler.storePath(ccddMain,
                                                scriptFile[0].getAbsolutePathWithEnvVars(),
                                                true,
                                                ModifiablePathInfo.SCRIPT_PATH);

                    // Display the file name in the script name field
                    scriptNameFld.setText(scriptFile[0].getAbsolutePathWithEnvVars());
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

    /**********************************************************************************************
     * Create the table tree selection panel
     *
     * @param labelText
     *            label to display above the table tree
     *
     * @param tableSelect
     *            table tree item selection model
     *
     * @return JPanel containing the selection panel
     *********************************************************************************************/
    private JPanel createSelectionPanel(String labelText, int tableSelect)
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   0),
                                                        0,
                                                        0);

        // Create a panel to hold the table selection components of the dialog
        JPanel filterPnl = null;

        // Build the table tree showing all tables
        tableTree = new CcddTableTreeHandler(ccddMain,
                                             new CcddGroupHandler(ccddMain,
                                                                  null,
                                                                  ccddMain.getMainFrame()),
                                             TableTreeType.TABLES,
                                             true,
                                             false,
                                             ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a node in the table tree
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent link tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Reset the flag to allow link tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Check if the database contains any tables
        if (tableTree.getRowCount() != (tableTree.isRootVisible()
                                                                  ? 1
                                                                  : 0))
        {
            // Create panels to hold the components of the dialog
            filterPnl = new JPanel(new GridBagLayout());

            // Create the table filter label and tree
            JLabel filterLbl = new JLabel(labelText);
            filterLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            filterPnl.add(filterLbl, gbc);
            gbc.insets.top = 0;
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
            gbc.weighty = 1.0;
            gbc.gridy++;
            filterPnl.add(tableTree.createTreePanel(null,
                                                    tableSelect,
                                                    false,
                                                    ccddMain.getMainFrame()),
                          gbc);
        }
        // No tables are stored in the database
        else
        {
            // Inform the user that no table exists in this database
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Project '</b>"
                                                                               + dbControl.getDatabaseName()
                                                                               + "<b>' has no tables",
                                                      "No Tables",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return filterPnl;
    }

    /**********************************************************************************************
     * Add an association to the script associations list based on the script and table selections
     *
     * @param insertPoint
     *            insertion point for the added row into the associations table:
     *            TableInsertionPoint.START to insert as the first row in the table,
     *            TableInsertionPoint.SELECTION to insert below the currently selected row, or
     *            TableInsertionPoint.END to insert as the last row in the table
     *
     * @param ignoreRow
     *            row to ignore when checking for an identical, existing association (as is
     *            possible when replacing an association, if no changes are made); -1 to prevent a
     *            duplicate association (as when adding an association)
     *
     * @return true if the association inputs are valid and the association is successfully added
     *********************************************************************************************/
    private boolean addAssociation(TableInsertionPoint insertPoint, int ignoreRow)
    {
        boolean isAdded = false;

        try
        {
            List<String> members = new ArrayList<String>();

            // Check if the tree is filtered by group
            if (tableTree.isFilteredByGroup())
            {
                // Step through each selected group
                for (String group : tableTree.getSelectedGroups())
                {
                    // Add the group to the list. Any table belonging to the group is deselected
                    members.add(GROUP_DATA_FIELD_IDENT + group);
                }
            }

            // Remove any excess white space
            nameFld.setText(nameFld.getText().trim());
            descriptionFld.setText(descriptionFld.getText().trim());

            // Check if the name field isn't blank
            if (!nameFld.getText().isEmpty())
            {
                // Check if the association name does not match the alphanumeric input type
                if (!nameFld.getText().matches(DefaultInputType.ALPHANUMERIC.getInputMatch()))
                {
                    throw new CCDDException("Illegal character(s) in association name");
                }

                // Compare this association name to the others in the table in order to avoid
                // creating a duplicate
                for (int row = 0; row < assnsTable.getRowCount(); row++)
                {
                    // Check if this row isn't the one being edited, and if the association name
                    // matches the one being added (case insensitive)
                    if (row != ignoreRow
                        && nameFld.getText().equalsIgnoreCase(assnsTable.getValueAt(row,
                                                                                    AssociationsTableColumnInfo.NAME.ordinal())
                                                                        .toString()))
                    {
                        throw new CCDDException("Association name already in use");
                    }
                }
            }

            // Add the selected table names, skipping child tables if an ancestor of the table is
            // selected (when the association is executed and a table's data is read it
            // automatically reads all of its descendant's data, so there's no need to include the
            // descendants in the association))
            members.addAll(tableTree.getSelectedTablesWithoutChildren());

            // Get a file descriptor for the script file name
            FileEnvVar scriptFile = new FileEnvVar(scriptNameFld.getText());

            // Check if the script association already exists in the list
            if (CcddScriptHandler.getMatchingAssociation(Arrays.asList(CcddUtilities.convertObjectToString(assnsTable.getTableData(false))),
                                                      scriptFile.getAbsolutePathWithEnvVars(),
                                                      members.toArray(new String[0]),
                                                      ignoreRow) != -1)
            {
                throw new CCDDException("An association with this script and table(s) "
                                        + "already exists in the script associations table");
            }

            // Create the script association strings
            String assn = "";

            // Step through each selected table/group in the tree
            for (String member : members)
            {
                // Add the table/group name to the script association
                assn += member + ASSN_TABLE_SEPARATOR;
            }

            // Remove the trailing table separator
            assn = CcddUtilities.removeTrailer(assn, ASSN_TABLE_SEPARATOR);

            // Set the flag to indicate that an association addition is in progress
            isAddingAssn = true;

            // Insert the new script association at the end of the associations table, then select
            // it and scroll to it
            assnsTable.insertRow(true,
                                 insertPoint,
                                 new Object[] {nameFld.getText(),
                                               descriptionFld.getText(),
                                               scriptFile.getAbsolutePathWithEnvVars(),
                                               CcddUtilities.highlightDataType(assn),
                                               (scriptFile.exists()
                                                                    ? AvailabilityType.AVAILABLE
                                                                    : AvailabilityType.SCRIPT_MISSING)});

            isAddingAssn = false;
            isAdded = true;
        }
        catch (CCDDException ce)
        {
            // Inform the user that an input value is invalid
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>" + ce.getMessage(),
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return isAdded;
    }

    /**********************************************************************************************
     * Remove the selected association(s) from the script associations table
     *********************************************************************************************/
    private void removeAssociations()
    {
        assnsTable.removeRows(assnsTable.getSelectedRows());
    }

    /**********************************************************************************************
     * Replace the selected association in the script associations table
     *********************************************************************************************/
    private void replaceAssociation()
    {
        // Check if a single association is selected in the associations table
        if (assnsTable.getSelectedRowCount() == 1)
        {
            // Get the selected row
            int selectedRow = assnsTable.getSelectedRow();

            // Check if adding the new row below the selected row succeeded
            if (addAssociation(TableInsertionPoint.SELECTION, selectedRow))
            {
                // Remove the selected association and set the selection to the newly added
                // association
                assnsTable.removeRows(new int[] {selectedRow});
                assnsTable.setSelectedRow(selectedRow);
            }
        }
    }

    /**********************************************************************************************
     * Create the script association definitions from the script association table
     *
     * @return List containing the script associations
     *********************************************************************************************/
    private List<String[]> createAssociationsFromTable()
    {
        List<String[]> currentAssociations = new ArrayList<String[]>();

        // Step through each defined script association
        for (Object[] assn : assnsTable.getTableData(true))
        {
            // Get the script association information from the table and add it to the list
            currentAssociations.add(new String[] {assn[AssociationsTableColumnInfo.NAME.ordinal()].toString(),
                                                  assn[AssociationsTableColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                  assn[AssociationsTableColumnInfo.SCRIPT_FILE.ordinal()].toString(),
                                                  CcddUtilities.removeHTMLTags(assn[AssociationsTableColumnInfo.MEMBERS.ordinal()].toString().replaceAll("<br>", "\n"))});
        }

        return currentAssociations;
    }

    /**********************************************************************************************
     * Check if the script associations have changed
     *
     * @return true if a change exists
     *********************************************************************************************/
    protected boolean isAssociationsChanged()
    {
        return assnsTable.isTableChanged(committedAssnsData,
                                         Arrays.asList(new Integer[] {AssociationsTableColumnInfo.AVAILABLE.ordinal()}));
    }

    /**********************************************************************************************
     * Update the table tree and the associations table with the latest table and script
     * associations information
     *********************************************************************************************/
    protected void reloadAssociationsTable()
    {
        tableTree.setGroupHandler(new CcddGroupHandler(ccddMain, null, ccddMain.getMainFrame()));
        tableTree.buildTableTreeFromDatabase(CcddScriptManagerDialog.this);
        scriptHandler.getAssociationsTable().loadAndFormatData();
        assnsTable.getUndoManager().discardAllEdits();
    }

    /**********************************************************************************************
     * Update the change indicator for the script associations manager
     *********************************************************************************************/
    protected void updateChangeIndicator()
    {
        // Add or remove the change indicator based on whether or not any unstored changes exist
        setTitle(DIALOG_TITLE
                 + (isAssociationsChanged()
                                            ? CHANGE_INDICATOR
                                            : ""));

        // Force the table to redraw so that changes to the cells are displayed
        repaint();
    }
}
