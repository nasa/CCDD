/**
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LAF_CHECK_BOX_HEIGHT;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.tree.DefaultMutableTreeNode;

import org.json.simple.JSONObject;

import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary common component classes class
 *************************************************************************************************/
public class CcddClassesComponent
{
    // Main class reference
    private static CcddMain ccddMain;

    /**********************************************************************************************
     * Action listener to validate the contents of the table cell currently being edited prior to
     * performing an action. This prevents the action from being performed when the cell's contents
     * is invalid (which causes a warning dialog to appear), allowing the invalid value to be
     * corrected
     *********************************************************************************************/
    abstract static class ValidateCellActionListener implements ActionListener
    {
        private final CcddJTableHandler table;

        /******************************************************************************************
         * Validate cell action listener. getTable should be overridden to provide the specific
         * table to validate. If no table is specified then the action is performed
         *****************************************************************************************/
        protected ValidateCellActionListener()
        {
            table = null;
        }

        /******************************************************************************************
         * Validate cell action listener
         *
         * @param table
         *            reference to the CcddJTableHandler table to check for cell validation; null
         *            perform the action with no validation check
         *****************************************************************************************/
        protected ValidateCellActionListener(CcddJTableHandler table)
        {
            this.table = table;
        }

        /******************************************************************************************
         * Perform the specified action if no cell is currently being edited, or if the contents of
         * the cell currently being edited is valid
         *****************************************************************************************/
        @Override
        public void actionPerformed(ActionEvent ae)
        {
            // Check if no table is specified, or if the contents of the last cell edited in the
            // specified table is validated
            if (getTable() == null || getTable().isLastCellValid())
            {
                // Check if the item initiating the event is a button
                if (ae.getSource() instanceof JButton)
                {
                    // Update the focus to the button. If a data field is being edited (and so has
                    // the focus) this forces editing of the field to stop, which in turn causes
                    // input verification to be performed on the field's contents. Pressing the
                    // button directly does this already, but if the button is the default and the
                    // Enter key is pressed then the field doesn't stop editing (and get tested)
                    // without this call
                    ((JButton) ae.getSource()).requestFocus();
                }

                // Perform the action
                performAction(ae);
            }
        }

        /******************************************************************************************
         * Placeholder for the action to be performed
         *
         * @param ae
         *            event reference (ActionEvent)
         *****************************************************************************************/
        abstract protected void performAction(ActionEvent ae);

        /******************************************************************************************
         * Get the table for which the action is performed
         *
         * @return Table for which the action is performed
         *****************************************************************************************/
        protected CcddJTableHandler getTable()
        {
            return table;
        }
    }

    /**********************************************************************************************
     * Set the main class reference
     *
     * @param main
     *            main class reference
     *********************************************************************************************/
    protected static void setHandlers(CcddMain main)
    {
        ccddMain = main;
    }

    /**********************************************************************************************
     * Tree node with tool tip handling class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ToolTipTreeNode extends DefaultMutableTreeNode
    {
        private final String toolTipText;

        /******************************************************************************************
         * Tree node with tool tip handling class constructor
         *
         * @param nodeName
         *            node name
         *
         * @param toolTipText
         *            text to display when mouse pointer hovers over the node
         *****************************************************************************************/
        ToolTipTreeNode(String nodeName, String toolTipText)
        {
            super(nodeName);

            // Get the node name with any macro(s) expanded
            String expanded = ccddMain.getMacroHandler().getMacroExpansion(nodeName);

            // Check if a node name contains a macro
            if (!expanded.equals(nodeName))
            {
                // Amend the tool tip text to include the macro expansion of the node name
                toolTipText = toolTipText == null
                              || toolTipText.isEmpty()
                                                       ? expanded
                                                       : "("
                                                         + expanded
                                                         + ") "
                                                         + toolTipText;
            }

            this.toolTipText = CcddUtilities.wrapText(toolTipText,
                                                      ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize());
        }

        /******************************************************************************************
         * Display the tree node tool tip text
         *
         * @return Tree node tool tip text
         *****************************************************************************************/
        protected String getToolTipText()
        {
            return toolTipText;
        }
    }

    /**********************************************************************************************
     * Custom combo box with item matching, padding for the list items, and tool tip text. The
     * combo box may have a list of selection items defined by an input type. One or more
     * characters may be typed into the combo box's data table cell or data field; the combo box
     * list is pruned so that only items that match the character(s) are displayed (wildcard
     * characters are allowed).
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class PaddedComboBox extends JComboBox<String>
    {
        // Flag that indicates the combo box is undergoing a layout operation
        private boolean inLayOut = false;

        // Flag that indicates if item matching is enable for this combo box
        private boolean isItemMatchEnabled = false;

        // Flag that indicates that the auto-completion characters are being entered by the user
        private boolean isPrefixChanging = false;

        // Item matching the user's input criteria or list selection
        private String selection = null;

        // Flag that indicates if any list item is HTML formatted
        private boolean hasHTML = false;

        /******************************************************************************************
         * Padded combo box constructor with an empty list
         *
         * @param font
         *            combo box list item font
         *****************************************************************************************/
        PaddedComboBox(Font font)
        {
            setListItemCharacteristics(null, font);
        }

        /******************************************************************************************
         * Padded combo box constructor with initial list items and no tool tip text
         *
         * @param items
         *            combo box list items
         *
         * @param font
         *            combo box list item font
         *****************************************************************************************/
        PaddedComboBox(String[] items, Font font)
        {
            super(items);

            setListItemCharacteristics(null, font);
        }

        /******************************************************************************************
         * Padded combo box constructor with initial list items and tool tip text
         *
         * @param items
         *            combo box list items
         *
         * @param toolTips
         *            combo box list items
         *
         * @param font
         *            combo box list item font
         *****************************************************************************************/
        PaddedComboBox(String[] items, String[] toolTips, Font font)
        {
            super(items);

            setListItemCharacteristics(toolTips, font);
        }

        /******************************************************************************************
         * Override in order to set the flag indicating a layout is in progress for when getSize()
         * is called
         *****************************************************************************************/
        @Override
        public void doLayout()
        {
            inLayOut = true;
            super.doLayout();
            inLayOut = false;
        }

        /******************************************************************************************
         * Override in order to set the combo box width to that of the longest list item
         *
         * @return Combo box size
         *****************************************************************************************/
        @Override
        public Dimension getSize()
        {
            Dimension dim = super.getSize();

            // Check that the call to this method didn't originate during a layout operation
            if (!inLayOut)
            {
                // Get the preferred width of the combo box list. This accounts for the longest
                // list item name in addition to the width of a scroll bar, even if not needed
                int listWidth = super.getPreferredSize().width;

                // Check if the number of items in the list doesn't exceed the maximum, in which
                // case no scroll bar appears
                if (getItemCount() <= getMaximumRowCount())
                {
                    // Since there is no scroll bar, subtract its width from the list width in
                    // order to eliminate the unneeded padding
                    listWidth -= LAF_SCROLL_BAR_WIDTH / 2;
                }

                // Get the maximum of the width returned by the call to the default getSize() and
                // the adjusted preferred width
                dim.width = Math.max(dim.width, listWidth);
            }

            return dim;
        }

        /******************************************************************************************
         * Override in order to keep the combo box width to that of the longest list item. If item
         * matching is enabled then base the width on the longest item in the full list, not the
         * reduced list constrained by the item matching
         *****************************************************************************************/
        @Override
        public Dimension getPreferredSize()
        {
            return isItemMatchEnabled
                                      ? getSize()
                                      : super.getPreferredSize();
        }

        /******************************************************************************************
         * Override in order to store the current item selection if item matching is enabled
         *****************************************************************************************/
        @Override
        public void setSelectedItem(Object item)
        {
            // Check if item matching is enabled for this combo box
            if (isItemMatchEnabled)
            {
                // Store the selected item
                selection = item.toString();
            }

            super.setSelectedItem(item);
        }

        /******************************************************************************************
         * Set up list item characteristics
         *
         * @param toolTips
         *            combo box list items
         *
         * @param font
         *            combo box list item font
         *****************************************************************************************/
        private void setListItemCharacteristics(final String[] toolTips, Font font)
        {
            // Create the padded border for the list items
            final Border paddedBorder = BorderFactory.createEmptyBorder(ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing() / 2,
                                                                        ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing() / 2,
                                                                        ModifiableSpacingInfo.CELL_VERTICAL_PADDING.getSpacing() / 2,
                                                                        ModifiableSpacingInfo.CELL_HORIZONTAL_PADDING.getSpacing() / 2);

            // Set the foreground color, background color, and font for the list items, and the
            // maximum number of items to display
            setForeground(Color.BLACK);
            setBackground(Color.WHITE);
            setFont(font);
            setMaximumRowCount(ModifiableSizeInfo.MAX_VIEWABLE_LIST_ROWS.getSize());

            // Set the renderer
            setRenderer(new DefaultListCellRenderer()
            {
                /**********************************************************************************
                 * Override so that tool tip text can be displayed for each list item and padding
                 * is added to the items
                 *********************************************************************************/
                @Override
                public Component getListCellRendererComponent(JList<?> list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus)
                {
                    // Check if the combo box list item is null or a blank
                    if (value == null || value.toString().isEmpty())
                    {
                        // Set the value to a space so that the combo box list row height is set to
                        // match the other list items. Without this the row height is based on the
                        // padding only
                        value = " ";
                    }

                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list,
                                                                             value,
                                                                             index,
                                                                             isSelected,
                                                                             cellHasFocus);

                    // Add padding to the list item
                    lbl.setBorder(paddedBorder);

                    // Check if the list item is valid and if it has tool tip text associated with
                    // it
                    if (index > -1
                        && toolTips != null
                        && index < toolTips.length
                        && value != null)
                    {
                        // Set the item's tool tip text
                        list.setToolTipText(CcddUtilities.wrapText(toolTips[index],
                                                                   ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                    }

                    return lbl;
                }
            });
        }

        /******************************************************************************************
         * Get the list index of the specified list item
         *
         * @param itemName
         *            name of the list item for which to search
         *
         * @return the index of the specified item in the list; -1 if the item isn't in the list
         *****************************************************************************************/
        protected int getIndexOfItem(String itemName)
        {
            int itemIndex = -1;

            // Step through each item in the list
            for (int index = 0; index < getItemCount(); index++)
            {
                // Check if list item matches the specified item
                if (getItemAt(index).equals(itemName))
                {
                    // Store the list index and stop searching
                    itemIndex = index;
                    break;
                }
            }

            return itemIndex;
        }

        /******************************************************************************************
         * Enable text matching in the combo box. When enabled, iIn addition to selecting an item
         * directly from the list, the user may type in one or more characters to constrain the
         * list to those items that match the text entered. Wildcard characters are allowed: a '?'
         * matches a single character and a '*' matches one or more characters. Trailing characters
         * are automatically assumed to be included (i.e., the result is the same as if an asterisk
         * terminates the match text)
         *
         * @param table
         *            reference to the table in which the combo box is a cell editor; null if the
         *            combo box isn't in a table cell
         *****************************************************************************************/
        protected void enableItemMatching(final CcddJTableHandler table)
        {
            isPrefixChanging = false;
            selection = "";
            final List<String> inputItems = new ArrayList<String>();
            final List<String> cleanInputItems;

            // Step through each combo box item
            for (int index = 0; index < getItemCount(); index++)
            {
                // Check if the item is HTML tagged
                if (getItemAt(index).startsWith("<html>"))
                {
                    // Set the flag to indicate an HTML tagged item exists in the list
                    hasHTML = true;
                }

                // Add the item to the list
                inputItems.add(getItemAt(index));
            }

            // Check if any list item is HTML tagged
            if (hasHTML)
            {
                cleanInputItems = new ArrayList<String>();

                // Step through each combo box item
                for (int index = 0; index < getItemCount(); index++)
                {
                    // Add the item to the list with the HTML tags removed
                    cleanInputItems.add(CcddUtilities.removeHTMLTags(getItemAt(index)));
                }
            }
            // No item is HTML tagged
            else
            {
                // Point the HTML-free list to the first list
                cleanInputItems = inputItems;
            }

            // Set the combo box to be editable so that characters can be typed for item matching
            // purposes
            setEditable(true);

            // Create the text field used as the editor for the combo box. Set the size so that it
            // doesn't change as the user's text input constrains the list contents
            final JTextField matchFld = new JTextField("");

            // Set the combo box's editor, using the item matching text field as the editor
            setEditor(new ComboBoxEditor()
            {
                /**********************************************************************************
                 * Override so that the item matching text field can be returned as the editor
                 * component
                 *********************************************************************************/
                @Override
                public Component getEditorComponent()
                {
                    return matchFld;
                }

                /**********************************************************************************
                 * Override to retrieve the item matching text field's contents
                 *********************************************************************************/
                @Override
                public Object getItem()
                {
                    return matchFld.getText();
                }

                /**********************************************************************************
                 * Override so that the item matching text field's contents can be set
                 *********************************************************************************/
                @Override
                public void setItem(Object text)
                {
                    // Check if a prefix change isn't in effect and that the editor contents isn't
                    // null
                    if (!isPrefixChanging && text != null)
                    {
                        // Store the text in the item matching text field
                        matchFld.setText(hasHTML
                                                 ? CcddUtilities.removeHTMLTags(text.toString())
                                                 : text.toString());
                        // Check if the combo box is in a table cell
                        if (table != null)
                        {
                            // Force the table to be redrawn. Without this call when another cell
                            // is selected the table doesn't respond to the change (e.g., in
                            // prepareRenderer() to alter cell background colors) unless the table
                            // is forced to redraw my some other means (selecting another cell or
                            // resizing the table)
                            table.repaint();
                        }
                    }
                }

                /**********************************************************************************
                 * Override to select all of the item matching text field's contents
                 *********************************************************************************/
                @Override
                public void selectAll()
                {
                    matchFld.selectAll();
                }

                /**********************************************************************************
                 * Override to add an action listener to the item matching text field
                 *********************************************************************************/
                @Override
                public void addActionListener(ActionListener al)
                {
                    matchFld.addActionListener(al);
                }

                /**********************************************************************************
                 * Override to remove an action listener to the item matching text field
                 *********************************************************************************/
                @Override
                public void removeActionListener(ActionListener al)
                {
                    matchFld.removeActionListener(al);
                }
            });

            // Add a key listener to the combo box editor (item matching text field) to capture the
            // match text entered by the user
            getEditor().getEditorComponent().addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Override to capture key press events. Only those keys that alter the prefix need
                 * be processed
                 *********************************************************************************/
                @Override
                public void keyPressed(final KeyEvent ke)
                {
                    // Check if a prefix change is not already in progress and the key pressed is a
                    // character
                    if (!isPrefixChanging && !ke.isActionKey())
                    {
                        // Set the flag to indicate a prefix change is active. This is needed to
                        // prevent the item matching text field contents from being overridden
                        isPrefixChanging = true;

                        // Create a runnable object to be executed
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            /**********************************************************************
                             * Modifying the combo box list causes an illegal state exception if
                             * the listener is still active; use invokeLater to allow the listener
                             * to complete prior to making the list changes
                             *********************************************************************/
                            @Override
                            public void run()
                            {
                                // Set the flag if the selection list has any items
                                boolean hadItem = getItemCount() != 0;

                                // Check if the list currently has any items
                                if (hadItem)
                                {
                                    // Step through each combo box item, leaving the initial item
                                    // (if any). If all list items are removed a noticeable time
                                    // penalty is incurred for lists with a large number of items
                                    for (int index = getItemCount() - 1; index > 0; index--)
                                    {
                                        // Remove the item from the list
                                        removeItemAt(index);
                                    }
                                }

                                // A custom matching system is used: a question mark matches a
                                // single character and an asterisk matches one or more characters.
                                // This is turned into a regular expression to perform the actual
                                // match. First the reserved regular expression characters are
                                // escaped, other than the asterisk and question mark; these are
                                // then replaced with their corresponding regular expression.
                                // Finally, a condition is added so that any characters following
                                // the user's text is also matched
                                String typedChars = matchFld.getText().replaceAll("([\\[\\]\\(\\)\\{\\}\\.\\+\\^\\$\\|\\-])",
                                                                                  "\\\\$1")
                                                            .replaceAll("\\?", ".")
                                                            .replaceAll("\\*", ".*?")
                                                    + ".*";

                                // Step through each item in the input list, skipping the initial
                                // blank (which is retained when removing the existing items above)
                                for (int index = 0; index < inputItems.size(); index++)
                                {
                                    // Check if the item matches the user's criteria (use the list
                                    // that has any HTML tags removed so that the HTML formatting
                                    // doesn't alter the match check). If no match criteria are
                                    // supplied (i.e., the input is blank) then all items are
                                    // considered a match
                                    if (cleanInputItems.get(index).matches(typedChars)
                                        || typedChars.isEmpty())
                                    {
                                        // Add the matching item to the list
                                        addItem(inputItems.get(index));
                                    }
                                }

                                // Check if the list wan't empty to begin with
                                if (hadItem)
                                {
                                    // Remove the initial list item that wasn't removed above
                                    removeItemAt(0);
                                }

                                // Get the first matching item. This is used as the selected item
                                // if the focus is changed from the input field
                                selection = getItemCount() > 1
                                            && !matchFld.getText().isEmpty()
                                                                             ? getItemAt(1)
                                                                             : "";

                                isPrefixChanging = false;
                            }
                        });
                    }
                }
            });

            // Add a focus listener to the combo box editor (item matching text field) to update
            // the combo box's contents with the currently matched item from the combo box list
            matchFld.addFocusListener(new FocusAdapter()
            {
                /**********************************************************************************
                 * Override to capture focus gain events
                 *********************************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    selection = null;

                    // Set the flag to indicate item matching is enabled. This is done here since
                    // the combo box must have been instantiated in order to get the focus and
                    // therefore it's width has been calculated
                    isItemMatchEnabled = true;

                    // Check if the combo box is in a table cell and the cell editor is the custom
                    // one for a combo box in a cell
                    if (table != null && table.getCellEditor() instanceof ComboBoxCellEditor)
                    {
                        // Set the flag to disable the stopCellEditing() call from stopping cell
                        // editing. Without this the call below to remove an item from the combo
                        // box list ends editing which prevents the expected behavior of the combo
                        // box
                        ((ComboBoxCellEditor) table.getCellEditor()).allowCellEdit(false);
                    }

                    // Display the drop down menu
                    showPopup();
                }

                /**********************************************************************************
                 * Override to capture focus loss events
                 *********************************************************************************/
                @Override
                public void focusLost(FocusEvent fe)
                {
                    // Check if no text was typed by the user
                    if (selection == null)
                    {
                        // Set the selection text to the current combo box editor contents
                        selection = matchFld.getText();
                    }

                    // Set the flag so that the list updates don't trigger input field updates
                    isPrefixChanging = true;

                    // Step through each combo box item, skipping the initial blank item
                    for (int index = getItemCount() - 1; index > 0; index--)
                    {
                        // Remove the item from the list
                        removeItemAt(index);
                    }

                    // Step through each item in the input list, skipping the initial blank (which
                    // is retained when removing the existing items above)
                    for (int index = 1; index < inputItems.size(); index++)
                    {
                        // Add the matching item to the list
                        addItem(inputItems.get(index));
                    }

                    // Reset the flag so that changes are accepted
                    isPrefixChanging = false;

                    // Check if the combo box is in a table cell and the cell editor is the custom
                    // one for a combo box in a cell
                    if (table != null && table.getCellEditor() instanceof ComboBoxCellEditor)
                    {
                        // Reenable the stopCellEditing() call
                        ((ComboBoxCellEditor) table.getCellEditor()).allowCellEdit(true);
                    }

                    // Set the selected item from the list based on the user's match criteria or
                    // list selection
                    setSelectedItem(selection);
                }
            });
        }
    }

    /**********************************************************************************************
     * Combo box used as a table cell editor class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ComboBoxCellEditor extends DefaultCellEditor
    {
        // Flag used to determine if a call to stopCellEditing() is allowed to proceed
        private boolean allowStopEdit = true;

        JComboBox<?> comboBox;

        /******************************************************************************************
         * Combo box used as a table cell editor class constructor
         *
         * @param comboBox
         *            reference to the comb box used as a cell editor
         *****************************************************************************************/
        public ComboBoxCellEditor(JComboBox<?> comboBox)
        {
            super(comboBox);
            this.comboBox = comboBox;
        }

        /******************************************************************************************
         * Override so that calls to stop editing can be intercepted and disabled. This is
         * necessary to allow the combo box item matching to operate in a table cell the same way
         * it operates in a data field
         *****************************************************************************************/
        @Override
        public boolean stopCellEditing()
        {
            boolean result = false;

            // Check if the flag is set to allow stopping cell editing
            if (allowStopEdit)
            {
                result = super.stopCellEditing();
            }

            return result;
        }

        /******************************************************************************************
         * Set the flag that determines if calls to stop cell editing are allowed to proceed. This
         * is necessary to allow the combo box item matching to operate in a table cell the same
         * way it operates in a data field
         *
         * @param enable
         *            true to allow calls to stopCellEditing() to proceed normally
         *****************************************************************************************/
        protected void allowCellEdit(boolean enable)
        {
            allowStopEdit = enable;
        }

        /******************************************************************************************
         * Update the combo box's selected item to the currently highlighted list item. This is
         * used by the keyboard handler when the Enter key is pressed so that the cell reflects the
         * selected item
         *****************************************************************************************/
        protected void updateSelectedItem()
        {
            ComboPopup popup = (ComboPopup) comboBox.getAccessibleContext().getAccessibleChild(0);
            comboBox.setSelectedItem(popup.getList().getSelectedValue());
        }
    }

    /**********************************************************************************************
     * Pop-up combo box class. Display a pop-up PaddedComboBox containing the supplied selection
     * items. When the user selects an item insert it into the supplied text component
     *********************************************************************************************/
    static class PopUpComboBox
    {
        private PaddedComboBox popUpCbox;
        private JDialog popUpDlg;
        private JTextComponent textComp;
        private boolean isExiting;

        /******************************************************************************************
         * Pop-up combo box class constructor. No tool tips are displayed for the list items
         *
         * @param owner
         *            dialog owning the pop-up combo box
         *
         * @param textComp
         *            text component over which to display the pop-up combo box and insert the
         *            selected item
         *
         * @param selItems
         *            list of selection items
         *
         * @param font
         *            modifiable font reference
         *****************************************************************************************/
        PopUpComboBox(Window owner,
                      final JTextComponent textComp,
                      List<String> selItems,
                      ModifiableFont font)
        {
            this(owner, textComp, selItems, null, font);
        }

        /******************************************************************************************
         * Pop-up combo box class constructor
         *
         * @param owner
         *            dialog owning the pop-up combo box
         *
         * @param textComp
         *            text component over which to display the pop-up combo box and insert the
         *            selected item
         *
         * @param selItems
         *            list of selection items
         *
         * @param toolTips
         *            list of selection item tool tips; null if no tool tip text is associated with
         *            the item
         *
         * @param font
         *            modifiable font reference
         *****************************************************************************************/
        PopUpComboBox(Window owner,
                      JTextComponent textComp,
                      List<String> selItems,
                      List<String> toolTips,
                      ModifiableFont font)
        {
            // Check if any selection items exist
            if (selItems != null && !selItems.isEmpty())
            {
                this.textComp = textComp;
                isExiting = false;

                // Create the pop-up dialog
                popUpDlg = new JDialog(owner);

                // Create the pop-up combo box
                popUpCbox = new PaddedComboBox(selItems.toArray(new String[0]),
                                               (toolTips != null
                                                                 ? toolTips.toArray(new String[0])
                                                                 : null),
                                               font);

                // Enable item matching for the combo box
                popUpCbox.enableItemMatching(null);

                // Add a listener for key press events in the pop-up combo box
                popUpCbox.getEditor().getEditorComponent().addKeyListener(new KeyAdapter()
                {
                    /******************************************************************************
                     * Handle a pop-up combo box key press event
                     *****************************************************************************/
                    @Override
                    public void keyPressed(KeyEvent ke)
                    {
                        // Check if the enter or escape key is pressed
                        if (ke.getKeyCode() == KeyEvent.VK_ENTER
                            || ke.getKeyCode() == KeyEvent.VK_ESCAPE)
                        {
                            // Remove the pop-up and return to the caller
                            exitReferenceCombo();
                        }
                    }
                });

                // Add a listener for action (specifically mouse button) events in the pop-up combo
                // box
                popUpCbox.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a pop-up combo box action event
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the action event is due to a mouse button
                        if ((ae.getModifiers() & ActionEvent.MOUSE_EVENT_MASK) != 0)
                        {
                            // Remove the pop-up and return to the caller
                            exitReferenceCombo();
                        }
                    }
                });

                // Add a listener for changes to the expansion/contraction of the combo box
                popUpCbox.addPopupMenuListener(new PopupMenuListener()
                {
                    /******************************************************************************
                     * Handle a combo box expansion event
                     *****************************************************************************/
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent pme)
                    {
                    }

                    /******************************************************************************
                     * Handle a combo box contraction event
                     *****************************************************************************/
                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent pme)
                    {
                    }

                    /******************************************************************************
                     * Handle a combo box cancel event. This occurs if the mouse is clicked outside
                     * the combo box
                     *****************************************************************************/
                    @Override
                    public void popupMenuCanceled(PopupMenuEvent pme)
                    {
                        // Remove the pop-up and return to the caller
                        exitReferenceCombo();
                    }
                });

                // Create the dialog to contain the pop-up combo box. Set to modeless so that
                // pop-up dialog focus changes can be detected
                popUpDlg.setModalityType(ModalityType.MODELESS);
                popUpDlg.setUndecorated(true);
                popUpDlg.add(popUpCbox, BorderLayout.NORTH);
                popUpDlg.setSize(new Dimension(popUpCbox.getPreferredSize()));

                // Add a listener for focus changes to the pop-up dialog
                popUpDlg.addWindowFocusListener(new WindowFocusListener()
                {
                    /******************************************************************************
                     * Handle a gain of pop-up dialog focus
                     *****************************************************************************/
                    @Override
                    public void windowGainedFocus(WindowEvent we)
                    {
                        // Create a runnable object to be executed
                        SwingUtilities.invokeLater(new Runnable()
                        {
                            /**********************************************************************
                             * Delay showing the pop-up; if this isn't done then the pop-up isn't
                             * expanded consistently
                             *********************************************************************/
                            @Override
                            public void run()
                            {
                                // Expand the combo box when it appears
                                popUpCbox.showPopup();
                            }
                        });
                    }

                    /******************************************************************************
                     * Handle a loss of pop-up dialog focus
                     *****************************************************************************/
                    @Override
                    public void windowLostFocus(WindowEvent we)
                    {
                        // Remove the pop-up and return to the caller
                        exitReferenceCombo();
                    }
                });

                // Position and display the pop-up
                positionReferencePopup(textComp);
                popUpDlg.setVisible(true);
            }
        }

        /******************************************************************************************
         * Override to alter the selected item text
         *
         * @param selectedItem
         *            selected item text
         *
         * @return Altered selected item text
         *****************************************************************************************/
        protected String alterText(String selectedItem)
        {
            return selectedItem;
        }

        /******************************************************************************************
         * Position the dialog containing the pop-up combo box at the text cursor position in the
         * text component
         *
         * @param textComp
         *            text component over which to display the pop-up combo box
         *****************************************************************************************/
        private void positionReferencePopup(JTextComponent textComp)
        {
            try
            {
                // Get the position of the text cursor within the text component
                Rectangle popUp = textComp.modelToView(textComp.getCaretPosition());

                // Position the pop-up at the text cursor position
                popUpDlg.setLocation(textComp.getLocationOnScreen().x + popUp.x,
                                     textComp.getLocationOnScreen().y);
            }
            catch (BadLocationException ble)
            {
                // Position the pop-up at the left end of the text component
                popUpDlg.setLocation(textComp.getLocationOnScreen().x,
                                     textComp.getLocationOnScreen().y);
            }
        }

        /******************************************************************************************
         * Get the text of the specified text component with the selected item inserted at the
         * current selection point
         *
         * @param text
         *            reference
         *
         * @param textComp
         *            text component over which the pop-up combo box is displayed
         *
         * @return Text of the specified text component with the selected item inserted at the
         *         current selection point
         *****************************************************************************************/
        private String getInsertedReference(String text, JTextComponent textComp)
        {
            return textComp.getText().substring(0, textComp.getSelectionStart())
                   + text
                   + textComp.getText().substring(textComp.getSelectionEnd());
        }

        /******************************************************************************************
         * Remove the pop-up combo box and return to the caller
         *****************************************************************************************/
        private void exitReferenceCombo()
        {
            // Check if the pop-up combo box isn't already exiting
            if (!isExiting)
            {
                // Set the flag to indicate that the pop-up combo box is exiting. This flag
                // prevents multiple calls to this exit method
                isExiting = true;

                // Dispose of the dialog
                popUpDlg.setVisible(false);
                popUpDlg.dispose();

                // Check if a valid item is selected
                if (popUpCbox.getSelectedItem() != null)
                {
                    // Get the selected item, performing any custom text alteration
                    String selectedItem = alterText(popUpCbox.getSelectedItem().toString().trim());

                    // Get the starting index of the selected text in the component
                    int start = textComp.getSelectionStart();

                    // Insert the item into the text component's existing text, overwriting any
                    // of the text that is highlighted
                    textComp.setText(getInsertedReference(selectedItem, textComp));
                    textComp.setSelectionStart(start);

                    // Select the item that was inserted
                    textComp.setSelectionEnd(start + selectedItem.length());
                }
            }
        }
    }

    /**********************************************************************************************
     * Modifiable font class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ModifiableFont extends Font
    {
        private final String fontIdentifier;

        /******************************************************************************************
         * Modifiable font class constructor. Allows assigning an identifier to the font for the
         * purpose of identifying those Swing components that utilize the font in the event the
         * font's characteristics are changed
         *
         * @param fontIdentifier
         *            font identifier. The identifier must be unique for each modifiable font since
         *            this name is used to identify the font as being used by a particular Swing
         *            component. The program preferences key for the modifiable font information is
         *            used as the identifier
         *
         * @param fontFamily
         *            font family
         *
         * @param fontStyle
         *            font style
         *
         * @param fontSize
         *            font size
         *****************************************************************************************/
        ModifiableFont(String fontIdentifier, String fontFamily, int fontStyle, int fontSize)
        {
            // Create the font
            super(fontFamily, fontStyle, fontSize);

            // Store the font's identifier
            this.fontIdentifier = fontIdentifier;
        }

        /******************************************************************************************
         * Get the modifiable font's identifier
         *
         * @return Modifiable font's identifier
         *****************************************************************************************/
        protected String getModifiableFontIdentifier()
        {
            return fontIdentifier;
        }
    }

    /**********************************************************************************************
     * Modifiable color class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ModifiableColor extends Color
    {
        private final String colorIdentifier;

        /******************************************************************************************
         * Modifiable color class constructor. Allows assigning an identifier to the color for the
         * purpose of identifying those Swing components that utilize the color in the event the
         * color's characteristics are changed
         *
         * @param colorIdentifier
         *            color identifier. The identifier must be unique for each modifiable color
         *            since this name is used to identify the color as being used by a particular
         *            Swing component. The program preferences key for the modifiable color
         *            information is used as the identifier
         *
         * @param red
         *            red color component
         *
         * @param green
         *            green color component
         *
         * @param blue
         *            blue color component
         *****************************************************************************************/
        ModifiableColor(String colorIdentifier, int red, int green, int blue)
        {
            // Create the color
            super(red, green, blue);

            // Store the color's identifier
            this.colorIdentifier = colorIdentifier;
        }

        /******************************************************************************************
         * Get the modifiable color's identifier
         *
         * @return Modifiable color's identifier
         *****************************************************************************************/
        protected String getModifiableColorIdentifier()
        {
            return colorIdentifier;
        }
    }

    /**********************************************************************************************
     * Create a color selection component based on a check box. In place of the check box a color
     * box is displayed
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ColorCheckBox extends JCheckBox
    {
        private Color checkBoxColor;
        private ImageIcon noFocusIcon;
        private ImageIcon focusIcon;

        /******************************************************************************************
         * Color check box constructor
         *
         * @param text
         *            label to display along with the check box
         *
         * @param color
         *            check box color
         *****************************************************************************************/
        ColorCheckBox(String text, ModifiableColor color)
        {
            // Set the check box icon color
            setIconColor(color);

            // Create a label for the button. Adjust the label so that only the initial character
            // is capitalized. The initial caps version is used when displaying the color selection
            // dialog
            text = text.toLowerCase();
            text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            setText(text);
        }

        /******************************************************************************************
         * Get the color selected for the check box
         *
         * @return Color selected for the check box
         *****************************************************************************************/
        protected Color getIconColor()
        {
            return checkBoxColor;
        }

        /******************************************************************************************
         * Create the icons to display for the check box in the selected color
         *
         * @param color
         *            color in which to display the check box icon
         *****************************************************************************************/
        protected void setIconColor(Color color)
        {
            // Store the selected color
            checkBoxColor = color;

            // Create the icon for when the check box does not have the keyboard focus and the
            // mouse cursor is not positioned over the check box
            noFocusIcon = createIcon(Color.BLACK);

            // Create the icon for when the check box has the keyboard focus and the mouse cursor
            // is not positioned over the check box
            focusIcon = createIcon(Color.WHITE);

            // Create the icon for when the mouse cursor is positioned over the check box
            setRolloverIcon(createIcon(Color.LIGHT_GRAY));
            setRolloverSelectedIcon(createIcon(Color.LIGHT_GRAY));

            // Select the icons created for use with the check box. This ensures the color check
            // boxes are drawn as color check boxes and not regular check boxes initially
            setIconFocus(false);
        }

        /******************************************************************************************
         * Create a check box color icon
         *
         * @param borderColor
         *            color with which to outline the icon
         *
         * @return Icon in the selected color with a border in the color specified
         *****************************************************************************************/
        private ImageIcon createIcon(Color borderColor)
        {
            // Create an image to work with
            BufferedImage image = new BufferedImage(LAF_CHECK_BOX_HEIGHT,
                                                    LAF_CHECK_BOX_HEIGHT,
                                                    BufferedImage.TYPE_INT_ARGB);

            // Create the graphics object from the image
            Graphics2D g2 = image.createGraphics();

            // Draw the icon image
            g2.setColor(borderColor);
            g2.drawRect(0, 0, LAF_CHECK_BOX_HEIGHT - 1, LAF_CHECK_BOX_HEIGHT - 1);
            g2.setColor(checkBoxColor);
            g2.fillRect(1, 1, LAF_CHECK_BOX_HEIGHT - 2, LAF_CHECK_BOX_HEIGHT - 2);

            // Delete the graphics object
            g2.dispose();

            return new ImageIcon(image);
        }

        /******************************************************************************************
         * Update the color check box icon depending whether or not it has the keyboard focus
         *
         * @param isFocus
         *            true if the check box has the key board focus
         *****************************************************************************************/
        protected void setIconFocus(boolean isFocus)
        {
            // Select the icon to use based on the focus flag
            ImageIcon icon = isFocus ? focusIcon : noFocusIcon;

            // Set the icon to display
            setIcon(icon);
            setSelectedIcon(icon);
        }
    }

    /**********************************************************************************************
     * Custom split pane class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class CustomSplitPane extends JSplitPane
    {
        /******************************************************************************************
         * Custom split pane class constructor. Create a horizontally or vertically divided split
         * pane using the specified components. If no divider component is provided then the
         * divider blends in with the dialog background. If a component is specified then the
         * divider is displayed using the supplied component
         *
         * @param leftUpperComp
         *            component to display in the left or upper side of the split pane
         *
         * @param rightLowerComp
         *            component to display in the right or lower side of the split pane
         *
         * @param dividerComp
         *            component to display in place of the divider; null to create a hidden divider
         *
         * @param orientation
         *            JSplitPane.HORIZONTAL_SPLIT to split the pane horizontally or
         *            JSplitPane.VERTICAL_SPLIT to split the pane vertically
         *****************************************************************************************/
        protected CustomSplitPane(Component leftUpperComp,
                                  Component rightLowerComp,
                                  final Component dividerComp,
                                  int orientation)
        {
            super(orientation, true, leftUpperComp, rightLowerComp);

            // Set the pane's UI so that the divider isn't displayed
            setUI(new BasicSplitPaneUI()
            {
                /**********************************************************************************
                 * Override so that the divider isn't displayed or is replaced with the specified
                 * component
                 *********************************************************************************/
                @Override
                public BasicSplitPaneDivider createDefaultDivider()
                {
                    // Create the divider
                    BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this)
                    {
                        /**************************************************************************
                         * Override so that the divider border isn't displayed
                         *************************************************************************/
                        @Override
                        public void setBorder(Border border)
                        {
                        }

                        /**************************************************************************
                         * Override so that the divider size can be set to the divider component,
                         * if applicable
                         *************************************************************************/
                        @Override
                        public int getDividerSize()
                        {
                            int size = orientation == JSplitPane.HORIZONTAL_SPLIT
                                                                                  ? ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2
                                                                                  : ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();

                            // Check if a component is provided to represent the divider
                            if (dividerComp != null)
                            {
                                // Set the size to the divider component's preferred width
                                size = orientation == JSplitPane.HORIZONTAL_SPLIT
                                                                                  ? dividerComp.getPreferredSize().width
                                                                                  : dividerComp.getPreferredSize().height;
                            }

                            return size;
                        }
                    };

                    // Check if a component is provided to represent the divider
                    if (dividerComp != null)
                    {
                        // Add the divider component to the divider
                        divider.setLayout(new BorderLayout());
                        divider.add(dividerComp, BorderLayout.CENTER);
                    }

                    return divider;
                }
            });

            // Set the split pane characteristics
            setBorder(null);
            setResizeWeight(0.5);
        }
    }

    /**********************************************************************************************
     * File class with support for environment variables within the file path
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class FileEnvVar extends File
    {
        private final String pathWithEnvVars;

        /******************************************************************************************
         * File class with support for environment variables within the file path constructor
         *
         * @param pathName
         *            file path and name
         *****************************************************************************************/
        FileEnvVar(String pathName)
        {
            super(expandEnvVars(pathName, System.getenv()));

            // Store the path name with any environment variables intact
            pathWithEnvVars = pathName;
        }

        /******************************************************************************************
         * Get the absolute file path with any environment variables intact
         *
         * @return Absolute file path with any environment variables intact
         *****************************************************************************************/
        protected String getAbsolutePathWithEnvVars()
        {
            return pathWithEnvVars;
        }

        /******************************************************************************************
         * Replace any environment variables with the corresponding value in the supplied file path
         *
         * @param pathName
         *            file path
         *
         * @param envVarMap
         *            environment variable map
         *
         * @return File path with any environment variables replaced with the variable's value;
         *         blank if the file path is null or blank
         *****************************************************************************************/
        protected static String expandEnvVars(String pathName, Map<String, String> envVarMap)
        {
            String actualPath = "";

            // Check if the file path isn't blank
            if (pathName != null && !pathName.isEmpty())
            {
                // Replace a leading tilde (~), if present, with the user's home path
                pathName = pathName.replaceFirst("^~" + Pattern.quote(File.separator),
                                                 System.getProperty("user.home") + File.separator);

                // Step through each folder in the path
                for (String folder : pathName.split(Pattern.quote(File.separator)))
                {
                    // Check if the folder is an environment variable
                    if (folder.startsWith("$"))
                    {
                        // Get the value of the variable
                        folder = envVarMap.get(folder.substring(1));
                    }

                    // Build the path using the variable value. A blank is used if the variable
                    // doesn't exist
                    actualPath += (folder == null || folder.isEmpty()
                                                                      ? ""
                                                                      : folder)
                                  + File.separator;
                }

                actualPath = CcddUtilities.removeTrailer(actualPath, File.separator);
            }

            return actualPath;
        }

        /******************************************************************************************
         * Replace any portions of the specified file path that match an environment variable value
         * in the supplied map with the corresponding variable name
         *
         * @param pathName
         *            file path
         *
         * @param envVarMap
         *            map containing environment variables and their corresponding values
         *
         * @return File path with any portions that match an environment variable value in the
         *         supplied map replaced with the corresponding variable name; blank if the
         *         supplied file path is null of blank
         *****************************************************************************************/
        protected static String restoreEnvVars(String pathName, Map<String, String> envVarMap)
        {
            // Check if the file path isn't blank
            if (pathName != null && !pathName.isEmpty())
            {
                // Step through each environment variable
                for (Entry<String, String> envVar : envVarMap.entrySet())
                {
                    // Check if the variable has a non-blank value
                    if (envVar.getValue() != null && !envVar.getValue().isEmpty())
                    {
                        // Replace the variable value with its name
                        pathName = pathName.replaceFirst(Pattern.quote(envVar.getValue()),
                                                         "\\$" + envVar.getKey());
                    }
                }
            }
            // The file path is null or blank
            else
            {
                pathName = "";
            }

            return pathName;
        }

        /******************************************************************************************
         * Get a map of the environment variables and their corresponding value in the supplied
         * file path
         *
         * @param pathName
         *            file path
         *
         * @return Map containing the environment variables and corresponding values in the
         *         supplied file path
         *****************************************************************************************/
        protected static Map<String, String> getEnvVars(String pathName)
        {
            Map<String, String> envVars = new HashMap<String, String>();

            // Check if the file path isn't blank
            if (pathName != null && !pathName.isEmpty())
            {
                // Step through each folder in the path
                for (String folder : pathName.split(Pattern.quote(File.separator)))
                {
                    // Check if the folder is an environment variable
                    if (folder.startsWith("$"))
                    {
                        // Remove the environment variable identifier
                        folder = folder.substring(1);

                        // Store the variable and its value in the map
                        envVars.put(folder, System.getenv(folder));
                    }
                }
            }

            return envVars;
        }
    }

    /**********************************************************************************************
     * Selected table cell class
     *********************************************************************************************/
    protected static class SelectedCell
    {
        private int row;
        private int column;

        /******************************************************************************************
         * Selected table cell class constructor. An instance is created for each selected cell in
         * the table
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *****************************************************************************************/
        SelectedCell(int row, int column)
        {
            this.row = row;
            this.column = column;
        }

        /******************************************************************************************
         * Get the cell's row index
         *
         * @return Cell's row index
         *****************************************************************************************/
        protected int getRow()
        {
            return row;
        }

        /******************************************************************************************
         * Set the cell's row index
         *
         * @param row
         *            cell's row index
         *****************************************************************************************/
        protected void setRow(int row)
        {
            this.row = row;
        }

        /******************************************************************************************
         * Get the cell's column index
         *
         * @return Cell's column index
         *****************************************************************************************/
        protected int getColumn()
        {
            return column;
        }

        /******************************************************************************************
         * Set the cell's column index
         *
         * @param column
         *            cell's column index
         *****************************************************************************************/
        protected void setColumn(int column)
        {
            this.column = column;
        }

        /******************************************************************************************
         * Determine if the cell coordinates match the ones supplied
         *
         * @param checkRow
         *            row index in view coordinates to compare to this cell's row index
         *
         * @param checkColumn
         *            column index in view coordinates to compare to this cell's column index
         *
         * @return true if the cell's row and column indices match the specified ones
         *****************************************************************************************/
        protected boolean isCell(int checkRow, int checkColumn)
        {
            return row == checkRow && column == checkColumn;
        }
    }

    /**********************************************************************************************
     * Table cell selection handler
     *********************************************************************************************/
    protected static class CellSelectionHandler
    {
        // List containing the selected cells
        private final ArrayList<SelectedCell> cells;

        /******************************************************************************************
         * Table cell selection handler constructor
         *****************************************************************************************/
        CellSelectionHandler()
        {
            cells = new ArrayList<SelectedCell>();
        }

        /******************************************************************************************
         * Get the reference for the specified selected cell
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *
         * @return Reference to the specified cell
         *****************************************************************************************/
        protected SelectedCell getSelectedCell(int row, int column)
        {
            SelectedCell selectedCell = null;

            // Step through each selected cell
            for (SelectedCell cell : cells)
            {
                // Check if the cell is already in the list
                if (cell.isCell(row, column))
                {
                    // Store the selected cell reference and stop searching
                    selectedCell = cell;
                    break;
                }
            }

            return selectedCell;
        }

        /******************************************************************************************
         * Get the list of selected cells
         *
         * @return List of selected cells
         *****************************************************************************************/
        protected ArrayList<SelectedCell> getSelectedCells()
        {
            return cells;
        }

        /******************************************************************************************
         * Add the cell, specified by the supplied coordinates, to the list of selected cells if it
         * s not already in the list
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *****************************************************************************************/
        protected void add(int row, int column)
        {
            // Check if the cell is not already in the selected cells list
            if (!contains(row, column))
            {
                // Add the cell to the list
                cells.add(new SelectedCell(row, column));
            }
        }

        /******************************************************************************************
         * Remove the cell, specified by the supplied coordinates, from the list of selected cells
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *****************************************************************************************/
        protected void remove(int row, int column)
        {
            // Step through each selected cell
            for (SelectedCell cell : cells)
            {
                // Check if the cell is in the list
                if (cell.isCell(row, column))
                {
                    // Remove the cell from the list and stop searching
                    cells.remove(cell);
                    break;
                }
            }
        }

        /******************************************************************************************
         * Remove any cells in the selected cells list, then add the cell, specified by the
         * supplied coordinates, to the list
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *****************************************************************************************/
        protected void reset(int row, int column)
        {
            // Remove any cells currently in the list
            cells.clear();

            // Add the specified cell to the list
            cells.add(new SelectedCell(row, column));
        }

        /******************************************************************************************
         * Clear the list of selected cells
         *****************************************************************************************/
        protected void clear()
        {
            cells.clear();
        }

        /******************************************************************************************
         * Determine if the cell specified by the supplied coordinates is already in the list
         *
         * @param row
         *            cell row in view coordinates
         *
         * @param column
         *            cell column in view coordinates
         *
         * @return true if the cell is already in the selection list
         *****************************************************************************************/
        protected boolean contains(int row, int column)
        {
            return getSelectedCell(row, column) != null;
        }

        /******************************************************************************************
         * Determine if only one or no cells are currently selected
         *
         * @return true is one or no cells are currently selected
         *****************************************************************************************/
        protected boolean isOneOrNone()
        {
            return cells.size() <= 1;
        }
    }

    /**********************************************************************************************
     * Array list class with string arrays. The array column for use with the indexOf(),
     * contains(), and sort() methods can be specified (defaults to column 0). Multiple column
     * indices can be provided so that a sort() is based on the first column, and if the same the
     * second column, etc.
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class ArrayListMultiple extends ArrayList<String[]>
    {
        private int[] compareColumn;

        /******************************************************************************************
         * Array list class constructor with string arrays; sets the comparison column
         *
         * @param compareColumn
         *            index of the column for indexOf(), contains(), and sort() comparisons. If
         *            multiple columns are specified only the first is used for indexOf() and
         *            contains() calls; the sort() is based on the items in the column sequence
         *            provided
         ******************************************************************************************/
        protected ArrayListMultiple(int... compareColumn)
        {
            setComparisonColumn(compareColumn);
        }

        /******************************************************************************************
         * Array list class constructor with string arrays; assumes the first column is the
         * comparison column
         ******************************************************************************************/
        protected ArrayListMultiple()
        {
            this(0);
        }

        /******************************************************************************************
         * Set the comparison column(s)
         *
         * @param compareColumn
         *            index of the column for indexOf(), contains(), and sort() comparisons. If
         *            multiple columns are specified only the first is used for indexOf() and
         *            contains() calls; the sort() is based on the items in the column sequence
         *            provided
         ******************************************************************************************/
        protected void setComparisonColumn(int... compareColumn)
        {
            this.compareColumn = compareColumn;
        }

        /******************************************************************************************
         * Override the contains method. Compare the input object to the string in the comparison
         * column in each array member in the list and return true is a match is found
         *****************************************************************************************/
        @Override
        public boolean contains(Object obj)
        {
            return indexOf(obj) != -1;
        }

        /******************************************************************************************
         * Override the indexOf method. Compare the input object to the string in the comparison
         * column in each array member in the list and return the index of the matching array, or
         * -1 if no match is found
         *****************************************************************************************/
        @Override
        public int indexOf(Object obj)
        {
            int matchIndex = -1;
            int index = 0;
            String checkString = (String) obj;

            // Step through each string array in the list
            for (String[] listString : this)
            {
                // Check if the input string matches the one in the comparison column in the array
                if (checkString.equals(listString[compareColumn[0]]))
                {
                    // Set the index to the matching one and stop searching
                    matchIndex = index;
                    break;
                }

                index++;
            }

            return matchIndex;
        }

        /******************************************************************************************
         * Sort the list based on the comparison column, and convert the column value based on the
         * specified sort type
         *
         * @param sortType
         *            ArrayListMultipleSortType conversion type; determines how the values are
         *            compared (string (case insensitive) or hexadecimal)
         *****************************************************************************************/
        protected void sort(final ArrayListMultipleSortType sortType)
        {
            Collections.sort(this, new Comparator<String[]>()
            {
                /**********************************************************************************
                 * Sort the list based on the comparison column and sort type
                 *********************************************************************************/
                @Override
                public int compare(final String[] item1, final String[] item2)
                {
                    int result = 0;
                    int index = 0;

                    do
                    {
                        switch (sortType)
                        {
                            case STRING:
                                // Compare the two values as text (case insensitive)
                                result = item1[compareColumn[index]].compareToIgnoreCase(item2[compareColumn[index]]);
                                break;

                            case HEXADECIMAL:
                                // Compare the two hexadecimal values as integers, converted to
                                // base 10
                                result = Integer.decode(item1[compareColumn[index]]).compareTo(Integer.decode(item2[compareColumn[index]]));
                        }
                        index++;
                    } while (result == 0
                             && index < compareColumn.length
                             && index < item1.length
                             && index < item2.length);
                    // Continue to perform the comparison as long as the two items match, more
                    // comparison columns are defined, and the column index doesn't exceed the
                    // comparison item array size

                    return result;
                }
            });
        }
    }

    /**********************************************************************************************
     * WrapLayout layout manager class (by Rob Camick; tips4java.wordpress.com; public domain)
     *
     * This open source code modifies the standard FlowLayout manager to include wrapping
     * components to new lines if the container is resized smaller than that needed to display the
     * components. The original code is reformatted and amended to account for a scroll pane
     * sharing the container in which the WrapLayout container is located; without the update the
     * wrapping is negated whenever the scroll pane's horizontal scroll bar appears
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class WrapLayout extends FlowLayout
    {
        /******************************************************************************************
         * WrapLayout constructor. Creates a new WrapLayout with a left alignment and a default
         * 5-unit horizontal and vertical gap.
         *****************************************************************************************/
        WrapLayout()
        {
            super();
        }

        /******************************************************************************************
         * Constructs a new FlowLayout with the specified alignment and a default 5-unit horizontal
         * and vertical gap. The value of the alignment argument must be one of WrapLayout.LEFT,
         * WrapLayout.RIGHT, or WrapLayout.CENTER
         *
         * @param align
         *            the alignment value
         *****************************************************************************************/
        WrapLayout(int align)
        {
            super(align);
        }

        /******************************************************************************************
         * Returns the preferred dimensions for this layout given the visible components in the
         * specified target container
         *
         * @param target
         *            the component which needs to be laid out
         *
         * @return The preferred dimensions to lay out the subcomponents of the specified container
         *****************************************************************************************/
        @Override
        public Dimension preferredLayoutSize(Container target)
        {
            return layoutSize(target, true);
        }

        /******************************************************************************************
         * Returns the minimum dimensions needed to layout the visible components contained in the
         * specified target container
         *
         * @param target
         *            the component which needs to be laid out
         *
         * @return The minimum dimensions to lay out the subcomponents of the specified container
         *****************************************************************************************/
        @Override
        public Dimension minimumLayoutSize(Container target)
        {
            // CCDD: Updated the flag in this call to true in order to force use of the preferred
            // size
            Dimension minimum = layoutSize(target, true);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        /******************************************************************************************
         * Returns the minimum or preferred dimension needed to layout the target container
         *
         * @param target
         *            target to get layout size for
         *
         * @param preferred
         *            should preferred size be calculated
         *
         * @return The dimension to layout the target container
         *****************************************************************************************/
        private Dimension layoutSize(Container target, boolean preferred)
        {
            synchronized (target.getTreeLock())
            {
                // Each row must fit with the width allocated to the container. When the container
                // width = 0, the preferred width of the container has not yet been calculated so
                // get the maximum
                int targetWidth = target.getSize().width;
                Container container = target;

                while (container.getSize().width == 0 && container.getParent() != null)
                {
                    container = container.getParent();
                }

                targetWidth = container.getSize().width;

                if (targetWidth == 0)
                {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                // Fit components into the allowed width
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++)
                {
                    Component m = target.getComponent(i);

                    if (m.isVisible())
                    {
                        // CCDD: Added separator handling
                        // Check if the component is a JSeparator
                        if (m instanceof JSeparator)
                        {
                            m.setPreferredSize(new Dimension(maxWidth,
                                                             ((JSeparator) m).getOrientation() == SwingConstants.HORIZONTAL
                                                                                                                            ? 3
                                                                                                                            : -ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing()
                                                                                                                              * 2 / 3 - 1));
                            m.setSize(m.getPreferredSize());
                        }
                        // CCDD: End modification

                        Dimension d = preferred
                                                ? m.getPreferredSize()
                                                : m.getMinimumSize();

                        // Can't add the component to current row. Start a new row
                        if (rowWidth + d.width > maxWidth)
                        {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        // Add a horizontal gap for all components after the first
                        if (rowWidth != 0)
                        {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                addRow(dim, rowWidth, rowHeight);
                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                // When using a scroll pane or the DecoratedLookAndFeel we need to make sure the
                // preferred size is less than the size of the target container so shrinking the
                // container size works correctly. Removing the horizontal gap is an easy way to do
                // this
                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class,
                                                                         target);

                if (scrollPane != null && target.isValid())
                {
                    dim.width -= (hgap + 1);
                }

                return dim;
            }
        }

        /******************************************************************************************
         * A new row has been completed. Use the dimensions of this row to update the preferred
         * size for the container.
         *
         * @param dim
         *            update the width and height when appropriate
         *
         * @param rowWidth
         *            the width of the row to add
         *
         * @param rowHeight
         *            the height of the row to add
         *****************************************************************************************/
        private void addRow(Dimension dim, int rowWidth, int rowHeight)
        {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0)
            {
                dim.height += getVgap();
            }

            dim.height += rowHeight;
        }
    }

    /**********************************************************************************************
     * Multiple line label class. This uses a JTextArea to mimic a JLabel, but wraps the text to
     * another line as needed based on the size of the component
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class MultilineLabel extends JTextArea
    {
        /******************************************************************************************
         * Multiple line label class constructor. Assumes the label text is blank
         *****************************************************************************************/
        MultilineLabel()
        {
            this("");
        }

        /******************************************************************************************
         * Multiple line label class constructor
         *
         * @param text
         *            label text
         *****************************************************************************************/
        MultilineLabel(String text)
        {
            super(text);

            // Set the text area characteristics so as to mimic a JLabel, but with the ability to
            // wrap the text as needed
            setEditable(false);
            setCursor(null);
            setOpaque(false);
            setFocusable(false);
            setWrapStyleWord(true);
            setLineWrap(true);
        }

        /******************************************************************************************
         * Get the number of rows required to display the label text based on the specified maximum
         * label width
         *
         * @param maxWidth
         *            maximum label width in pixels
         *
         * @return Number of rows required to display the label text
         *****************************************************************************************/
        protected int getNumDisplayRows(int maxWidth)
        {
            int numRows = 0;

            // Get the label's font metrics s that the text length in pixels can be determined
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            FontMetrics fm = image.getGraphics().getFontMetrics(getFont());

            // Step through each portion of the text, separating on line-feeds if any are present
            for (String part : getText().split("\n"))
            {
                // Calculate the number of rows required to display this portion of the text and
                // update the row counter accordingly
                numRows += Math.ceil(fm.stringWidth(part) / maxWidth);
            }

            // Return a minimum row count of 1
            return Math.max(numRows, 1);
        }
    }

    /**********************************************************************************************
     * JTextField with auto-completion class. This is a modified version of Java2sAutoTextField,
     * which carries the following copyright notice:
     *
     * Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
     *
     * Redistribution and use in source and binary forms, with or without modification, are
     * permitted provided that the following conditions are met:
     *
     * -Redistribution of source code must retain the above copyright notice, this list of
     * conditions and the following disclaimer.
     *
     * -Redistribution in binary form must reproduce the above copyright notice, this list of
     * conditions and the following disclaimer in the documentation and/or other materials provided
     * with the distribution.
     *
     * Neither the name of Sun Microsystems, Inc. or the names of contributors may be used to
     * endorse or promote products derived from this software without specific prior written
     * permission.
     *
     * This software is provided "AS IS," without a warranty of any kind. ALL EXPRESS OR IMPLIED
     * CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
     * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED.
     * SUN MIDROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES
     * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
     * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR
     * DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES,
     * HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
     * INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
     * DAMAGES.
     *
     * You acknowledge that this software is not designed, licensed or intended for use in the
     * design, construction, operation or maintenance of any nuclear facility.
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class AutoCompleteTextField extends JTextField
    {
        // List containing the auto-completion text
        private List<String> autoCompList;

        // Maximum number of items to maintain in the auto-complete list
        private final int maxItems;

        // Flag that determines if the text entered must match the case of the text in the list
        private boolean isCaseSensitive;

        // Flag that determines if only selections from the list are valid; if false then input
        // other than what's in the list is valid
        private boolean isOnlyFromList;

        // Flag that indicates if auto-completion should be disabled
        private boolean noAutoComp = false;

        /******************************************************************************************
         * Auto-complete document class
         *****************************************************************************************/
        private class AutoDocument extends PlainDocument
        {
            /**************************************************************************************
             * Replace text in the text field
             *************************************************************************************/
            @Override
            public void replace(int startIndex,
                                int length,
                                String insertTxt,
                                AttributeSet attributeset) throws BadLocationException
            {
                super.remove(startIndex, length);
                insertString(startIndex, insertTxt, attributeset);
            }

            /**************************************************************************************
             * Insert text into the text field
             *************************************************************************************/
            @Override
            public void insertString(int startIndex,
                                     String insertTxt,
                                     AttributeSet attributeset) throws BadLocationException
            {
                if (insertTxt != null && !insertTxt.isEmpty())
                {
                    String s1 = getText(0, startIndex);
                    String s2 = getMatch(s1 + insertTxt);
                    int newStartIndex = (startIndex + insertTxt.length()) - 1;

                    if ((!isOnlyFromList && s2 == null) || noAutoComp)
                    {
                        super.insertString(startIndex, insertTxt, attributeset);
                    }
                    else
                    {
                        if (isOnlyFromList && s2 == null)
                        {
                            s2 = getMatch(s1);
                            newStartIndex--;
                        }

                        super.remove(0, getLength());
                        super.insertString(0, s2, attributeset);
                        setSelectionStart(newStartIndex + 1);
                        setSelectionEnd(getLength());
                    }
                }
            }

            /**************************************************************************************
             * Remove text from the text field
             *************************************************************************************/
            @Override
            public void remove(int startIndex, int length) throws BadLocationException
            {
                int selectStart = getSelectionStart();

                if (selectStart > 0)
                {
                    selectStart--;
                }

                String match = getMatch(getText(0, selectStart));

                if (!isOnlyFromList && match == null)
                {
                    super.remove(startIndex, length);
                }
                else
                {
                    super.remove(0, getLength());
                    super.insertString(0, match, null);

                    setSelectionStart(selectStart);
                    setSelectionEnd(getLength());
                }
            }
        }

        /******************************************************************************************
         * JTextField auto-completion class constructor. Assume 10 items are stored in the
         * auto-complete list
         *
         * @param autoCompList
         *            list containing the strings from which the auto-completion text is extracted
         *****************************************************************************************/
        AutoCompleteTextField(List<String> autoCompList)
        {
            this(autoCompList, 10);
        }

        /******************************************************************************************
         * JTextField auto-completion class constructor. Start with an empty auto-complete list
         *
         * @param maxItems
         *            maximum number of items to maintain in the auto-complete list
         *****************************************************************************************/
        AutoCompleteTextField(int maxItems)
        {
            this(new ArrayList<String>(), maxItems);
        }

        /******************************************************************************************
         * JTextField auto-completion class constructor
         *
         * @param autoCompList
         *            list containing the strings from which the auto-completion text is extracted
         *
         * @param maxItems
         *            maximum number of items to maintain in the auto-complete list
         *****************************************************************************************/
        AutoCompleteTextField(final List<String> autoCompList, int maxItems)
        {
            this.autoCompList = autoCompList;
            this.maxItems = maxItems;
            noAutoComp = false;

            // Initialize with case sensitivity disabled and allowing text other than that in the
            // auto-completion list from being entered
            isCaseSensitive = false;
            isOnlyFromList = false;

            // Add a key press listener to the text field
            addKeyListener(new KeyAdapter()
            {
                /**********************************************************************************
                 * Handle backspace and delete key presses. This allows these keys to removed one
                 * or more characters and not invoke auto-completion afterwards (changing the text
                 * otherwise enables auto-completion)
                 *********************************************************************************/
                @Override
                public void keyPressed(KeyEvent ke)
                {
                    // Check if the backspace or delete key was pressed
                    if (ke.getKeyCode() == KeyEvent.VK_BACK_SPACE
                        || ke.getKeyCode() == KeyEvent.VK_DELETE)
                    {
                        try
                        {
                            int startIndex = 0;
                            int length = 0;

                            // Check if one or more characters is selected
                            if (getSelectedText() != null)
                            {
                                // Delete the selected characters
                                startIndex = Math.min(getCaret().getDot(), getCaret().getMark());
                                length = Math.max(getCaret().getDot(), getCaret().getMark())
                                         - startIndex;
                            }
                            // Check if the backspace key was pressed and the text cursor isn't at
                            // the beginning of the text string
                            else if (ke.getKeyCode() == KeyEvent.VK_BACK_SPACE
                                     && getCaret().getDot() != 0)
                            {
                                // Delete the character to the left of the text cursor
                                startIndex = getCaret().getDot() - 1;
                                length = 1;
                            }
                            // Check if the delete key was pressed and the text cursor isn't at the
                            // end of the text string
                            else if (ke.getKeyCode() == KeyEvent.VK_DELETE
                                     && getCaret().getDot() < getText().length())
                            {
                                // Delete the character to the right of the text cursor
                                startIndex = getCaret().getDot();
                                length = 1;
                            }

                            // Check if one or more characters is to be deleted, and, if only items
                            // from the list are valid, that the entered text is in the list
                            // (otherwise the backspace/delete is ignored)
                            if (length != 0
                                && !(isOnlyFromList
                                     && getMatch(autoCompList.get(0).toString()) == null))
                            {
                                // Remove the character(s) from the text string without invoking
                                // auto-completion
                                ((PlainDocument) getDocument()).replace(startIndex,
                                                                        length,
                                                                        null,
                                                                        null);
                            }
                        }
                        catch (BadLocationException ble)
                        {
                        }

                        // Check if text not in the list is allowed (this is required for correct
                        // highlighting when backspacing or deleting)
                        if (!isOnlyFromList)
                        {
                            // Remove the key press so that further handling isn't performed
                            ke.consume();
                        }
                    }
                }
            });

            setDocument(new AutoDocument());

            if (isOnlyFromList && autoCompList.size() > 0)
            {
                setText(autoCompList.get(0).toString());
            }
        }

        /******************************************************************************************
         * Set the auto-complete list
         *
         * @param autoCompList
         *            list containing the strings from which the auto-completion text is extracted
         *****************************************************************************************/
        protected void setList(List<String> autoCompList)
        {
            this.autoCompList = autoCompList;
        }

        /******************************************************************************************
         * Set the case sensitivity flag
         *
         * @param isCaseSensitive
         *            true to match case when auto-completing a string
         *****************************************************************************************/
        protected void setCaseSensitive(boolean isCaseSensitive)
        {
            this.isCaseSensitive = isCaseSensitive;
        }

        /******************************************************************************************
         * Set the flag that determines if text other than the auto-completion strings can be
         * entered into the text field
         *
         * @param isOnlyFromList
         *            true to only allow text from he auto-completion strings to be entered into
         *            the text field
         *****************************************************************************************/
        protected void setOnlyFromList(boolean isOnlyFromList)
        {
            this.isOnlyFromList = isOnlyFromList;
        }

        /******************************************************************************************
         * Get the first auto-completion list string that matches the input text
         *
         * @param inputTxt
         *            text for which to find a match
         *
         * @return First auto-completion list string that matches the input text; null if no match
         *         is found
         *****************************************************************************************/
        private String getMatch(String inputTxt)
        {
            String match = null;

            if (!inputTxt.isEmpty())
            {
                for (String autoTxt : autoCompList)
                {
                    if (!isCaseSensitive
                        && autoTxt.toLowerCase().startsWith(inputTxt.toLowerCase()))
                    {
                        match = autoTxt;
                        break;
                    }
                    else if (isCaseSensitive && autoTxt.startsWith(inputTxt))
                    {
                        match = autoTxt;
                        break;
                    }
                }
            }

            return match;
        }

        /******************************************************************************************
         * Replace the selected text in the text field
         *****************************************************************************************/
        @Override
        public void replaceSelection(String selectedTxt)
        {
            try
            {
                int startIndex = Math.min(getCaret().getDot(), getCaret().getMark());
                int length = Math.max(getCaret().getDot(), getCaret().getMark()) - startIndex;
                ((AutoDocument) getDocument()).replace(startIndex, length, selectedTxt, null);
            }
            catch (Exception exception)
            {
            }
        }

        /******************************************************************************************
         * Update the list of auto-completion items with the specified string
         *
         * @param text
         *            item to add to the auto-completion list. The item is placed at the head of
         *            the list. The list size is constrained to the maximum number specified when
         *            the field was created
         *****************************************************************************************/
        protected void updateList(String text)
        {
            CcddUtilities.updateRememberedItemList(text, autoCompList, maxItems);
        }

        /******************************************************************************************
         * Get the list of auto-completion items as a single, delimited string
         *
         * @return String containing the items from which the auto-completion text is extracted,
         *         separated by delimiter characters
         *****************************************************************************************/
        protected String getListAsString()
        {
            return CcddUtilities.getRememberedItemListAsString(autoCompList);
        }

        /******************************************************************************************
         * Override so that text inserted into the field isn't altered by auto-completion
         *****************************************************************************************/
        @Override
        public void setText(String text)
        {
            // Set the flag to disable auto-completion, set the field's text, then re-enable
            // auto-completion. This prevents auto-completion from replacing the text being
            // inserted into the field
            noAutoComp = true;
            super.setText(text);
            noAutoComp = false;
        }
    }

    /**********************************************************************************************
     * Font chooser class. This open source class allows creation and manipulation of a dialog
     * component for selection of a font based on family, style, and size. The code has
     * modifications to the original source code taken from
     * https://sourceforge.net/projects/jfontchooser
     *
     * Copyright 2004-2008 Masahiko SAWAI All Rights Reserved.
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this
     * software and associated documentation files (the "Software"), to deal in the Software
     * without restriction, including without limitation the rights to use, copy, modify, merge,
     * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
     * to whom the Software is furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all copies or
     * substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
     * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
     * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
     * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
     * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
     * DEALINGS IN THE SOFTWARE.
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class JFontChooser extends JComponent
    {
        // Default selected font and controls font
        private static final Font DEFAULT_SELECTED_FONT = new Font("Monospaced", Font.PLAIN, 13);
        private static final Font DEFAULT_FONT = ModifiableFontInfo.LABEL_PLAIN.getFont();

        // Font styles
        private static final int[] FONT_STYLE_CODES = {Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC
        };

        // Default font sizes
        private static final String[] DEFAULT_FONT_SIZE_STRINGS = {"8", "9", "10", "11", "12", "13",
                                                                   "14", "16", "18", "20", "22", "24",
                                                                   "26", "28", "36", "48", "72"};

        // Arrays containing the available font families, styles, and sizes
        private String[] fontStyleNames;
        private String[] fontFamilyNames;
        private final String[] fontSizeStrings;

        // Components referenced by multiple methods
        private JTextField fontFamilyTextField;
        private JTextField fontStyleTextField;
        private JTextField fontSizeTextField;
        private JList<String> fontNameList;
        private JList<String> fontStyleList;
        private JList<String> fontSizeList;
        private JTextField sampleText;
        private final Border borderPnl;
        private final Border borderLbl;
        private final Border borderFld;

        /******************************************************************************************
         * Font chooser constructor. Uses a default list of font sizes
         *****************************************************************************************/
        JFontChooser()
        {
            this(DEFAULT_FONT_SIZE_STRINGS);
        }

        /******************************************************************************************
         * Font chooser constructor
         *
         * @param fontSizeStrings
         *            array containing the available font sizes; null to use the default sizes
         *****************************************************************************************/
        JFontChooser(String[] fontSizeStrings)
        {
            if (fontSizeStrings == null)
            {
                fontSizeStrings = DEFAULT_FONT_SIZE_STRINGS;
            }

            this.fontSizeStrings = fontSizeStrings;

            borderPnl = BorderFactory.createEmptyBorder(0,
                                                        ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                        ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                        ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2);
            borderLbl = BorderFactory.createEmptyBorder(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                        0,
                                                        ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                        0);
            borderFld = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                           ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

            JPanel selectPanel = new JPanel();
            selectPanel.setLayout(new BoxLayout(selectPanel, BoxLayout.X_AXIS));
            selectPanel.setBorder(BorderFactory.createEmptyBorder());
            selectPanel.add(getFontFamilyPanel());
            selectPanel.add(getFontStylePanel());
            selectPanel.add(getFontSizePanel());

            JPanel contentsPanel = new JPanel();
            contentsPanel.setBorder(BorderFactory.createEmptyBorder());
            contentsPanel.setLayout(new BorderLayout());
            contentsPanel.add(selectPanel, BorderLayout.CENTER);
            contentsPanel.add(getSamplePanel(), BorderLayout.SOUTH);

            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(BorderFactory.createEmptyBorder());
            add(contentsPanel);
            setSelectedFont(DEFAULT_SELECTED_FONT);
        }

        /******************************************************************************************
         * Get the font family text field
         *
         * @return Reference to the font family text field
         *****************************************************************************************/
        private JTextField getFontFamilyTextField()
        {
            if (fontFamilyTextField == null)
            {
                fontFamilyTextField = new JTextField();
                fontFamilyTextField.setBorder(borderFld);
                fontFamilyTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontFamilyTextField));
                fontFamilyTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontFamilyList()));
                fontFamilyTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontFamilyList()));
                fontFamilyTextField.setFont(DEFAULT_FONT);
            }

            return fontFamilyTextField;
        }

        /******************************************************************************************
         * Get the font style text field
         *
         * @return Reference to the font style text field
         *****************************************************************************************/
        private JTextField getFontStyleTextField()
        {
            if (fontStyleTextField == null)
            {
                fontStyleTextField = new JTextField();
                fontStyleTextField.setBorder(borderFld);
                fontStyleTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontStyleTextField));
                fontStyleTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontStyleList()));
                fontStyleTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontStyleList()));
                fontStyleTextField.setFont(DEFAULT_FONT);
            }

            return fontStyleTextField;
        }

        /******************************************************************************************
         * Get the font size text field
         *
         * @return Reference to the font size text field
         *****************************************************************************************/
        private JTextField getFontSizeTextField()
        {
            if (fontSizeTextField == null)
            {
                fontSizeTextField = new JTextField();
                fontSizeTextField.setBorder(borderFld);
                fontSizeTextField.addFocusListener(new TextFieldFocusHandlerForTextSelection(fontSizeTextField));
                fontSizeTextField.addKeyListener(new TextFieldKeyHandlerForListSelectionUpDown(getFontSizeList()));
                fontSizeTextField.getDocument().addDocumentListener(new ListSearchTextFieldDocumentHandler(getFontSizeList()));
                fontSizeTextField.setFont(DEFAULT_FONT);
            }

            return fontSizeTextField;
        }

        /******************************************************************************************
         * Get the font family list
         *
         * @return List containing the font families
         *****************************************************************************************/
        private JList<String> getFontFamilyList()
        {
            if (fontNameList == null)
            {
                fontNameList = new JList<String>(getFontFamilies());
                fontNameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                fontNameList.addListSelectionListener(new ListSelectionHandler(getFontFamilyTextField()));
                fontNameList.setSelectedIndex(0);
                fontNameList.setFont(DEFAULT_FONT);
                fontNameList.setFocusable(false);
            }

            return fontNameList;
        }

        /******************************************************************************************
         * Get the font style list
         *
         * @return List containing the font styles
         *****************************************************************************************/
        private JList<String> getFontStyleList()
        {
            if (fontStyleList == null)
            {
                fontStyleList = new JList<String>(getFontStyleNames());
                fontStyleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                fontStyleList.addListSelectionListener(new ListSelectionHandler(getFontStyleTextField()));
                fontStyleList.setSelectedIndex(0);
                fontStyleList.setFont(DEFAULT_FONT);
                fontStyleList.setFocusable(false);
            }

            return fontStyleList;
        }

        /******************************************************************************************
         * Get the font size list
         *
         * @return List containing the font sizes
         *****************************************************************************************/
        private JList<String> getFontSizeList()
        {
            if (fontSizeList == null)
            {
                fontSizeList = new JList<String>(fontSizeStrings);
                fontSizeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                fontSizeList.addListSelectionListener(new ListSelectionHandler(getFontSizeTextField()));
                fontSizeList.setSelectedIndex(0);
                fontSizeList.setFont(DEFAULT_FONT);
                fontSizeList.setFocusable(false);
            }

            return fontSizeList;
        }

        /******************************************************************************************
         * Get the selected font's family
         *
         * @return Family of the selected font
         *****************************************************************************************/
        protected String getSelectedFontFamily()
        {
            String fontName = getFontFamilyList().getSelectedValue();
            return fontName;
        }

        /******************************************************************************************
         * Set the selected font's family
         *
         * @param family
         *            font family
         *****************************************************************************************/
        protected void setSelectedFontFamily(String family)
        {
            String[] families = getFontFamilies();

            for (int index = 0; index < families.length; index++)
            {
                if (families[index].toLowerCase().equals(family.toLowerCase()))
                {
                    getFontFamilyList().setSelectedIndex(index);
                    break;
                }
            }

            updateSampleFont();
        }

        /******************************************************************************************
         * Get the selected font's style
         *
         * @return Style of the selected font
         *****************************************************************************************/
        protected int getSelectedFontStyle()
        {
            return FONT_STYLE_CODES[getFontStyleList().getSelectedIndex()];
        }

        /******************************************************************************************
         * Set the selected font's style
         *
         * @param style
         *            font style: Font.PLAIN, Font.BOLD, Font.ITALIC, or Font.BOLD|Font.ITALIC
         *****************************************************************************************/
        protected void setSelectedFontStyle(int style)
        {
            for (int index = 0; index < FONT_STYLE_CODES.length; index++)
            {
                if (FONT_STYLE_CODES[index] == style)
                {
                    getFontStyleList().setSelectedIndex(index);
                    break;
                }
            }

            updateSampleFont();
        }

        /******************************************************************************************
         * Get the selected font's size
         *
         * @return Size of the selected font
         *****************************************************************************************/
        protected int getSelectedFontSize()
        {
            int fontSize = 1;
            String fontSizeString = getFontSizeTextField().getText();

            while (true)
            {
                try
                {
                    fontSize = Integer.parseInt(fontSizeString);
                    break;
                }
                catch (NumberFormatException nfe)
                {
                    fontSizeString = getFontSizeList().getSelectedValue();
                    getFontSizeTextField().setText(fontSizeString);
                }
            }

            return fontSize;
        }

        /******************************************************************************************
         * Set the selected font's size
         *
         * @param size
         *            font size
         *****************************************************************************************/
        protected void setSelectedFontSize(int size)
        {
            String sizeString = String.valueOf(size);

            for (int index = 0; index < fontSizeStrings.length; index++)
            {
                if (fontSizeStrings[index].equals(sizeString))
                {
                    getFontSizeList().setSelectedIndex(index);
                    break;
                }
            }

            getFontSizeTextField().setText(sizeString);
            updateSampleFont();
        }

        /******************************************************************************************
         * Get the selected font
         *
         * @return Reference to the selected font
         *****************************************************************************************/
        protected Font getSelectedFont()
        {
            return new Font(getSelectedFontFamily(),
                            getSelectedFontStyle(),
                            getSelectedFontSize());
        }

        /******************************************************************************************
         * Set the selected font
         *
         * @param font
         *            font to select
         *****************************************************************************************/
        protected void setSelectedFont(Font font)
        {
            setSelectedFontFamily(font.getFamily());
            setSelectedFontStyle(font.getStyle());
            setSelectedFontSize(font.getSize());
        }

        /******************************************************************************************
         * Font chooser control list class
         *****************************************************************************************/
        private class ListSelectionHandler implements ListSelectionListener
        {
            private final JTextComponent textComponent;

            /**************************************************************************************
             * Font chooser control list class constructor
             *
             * @param textComponent
             *            text component
             *************************************************************************************/
            ListSelectionHandler(JTextComponent textComponent)
            {
                this.textComponent = textComponent;
            }

            /**************************************************************************************
             * Handle a value change in the list
             *************************************************************************************/
            @Override
            public void valueChanged(ListSelectionEvent lse)
            {
                if (lse.getValueIsAdjusting() == false)
                {
                    @SuppressWarnings("unchecked")
                    JList<String> list = (JList<String>) lse.getSource();
                    String selectedValue = CcddUtilities.removeHTMLTags(list.getSelectedValue());

                    String oldValue = textComponent.getText();
                    textComponent.setText(selectedValue);

                    if (!oldValue.equalsIgnoreCase(selectedValue))
                    {
                        textComponent.selectAll();
                        textComponent.requestFocus();
                    }

                    updateSampleFont();
                }
            }
        }

        /******************************************************************************************
         * Font chooser text field focus handling class
         *****************************************************************************************/
        private class TextFieldFocusHandlerForTextSelection extends FocusAdapter
        {
            private final JTextComponent textComponent;

            /**************************************************************************************
             * Font chooser text field focus handling class constructor
             *
             * @param textComponent
             *            text component
             *************************************************************************************/
            TextFieldFocusHandlerForTextSelection(JTextComponent textComponent)
            {
                this.textComponent = textComponent;
            }

            /**************************************************************************************
             * Handle a focus gained event
             *************************************************************************************/
            @Override
            public void focusGained(FocusEvent fe)
            {
                textComponent.selectAll();
            }

            /**************************************************************************************
             * Handle a focus lost event
             *************************************************************************************/
            @Override
            public void focusLost(FocusEvent fe)
            {
                textComponent.select(0, 0);
                updateSampleFont();
            }
        }

        /******************************************************************************************
         * Font chooser text field key handling class
         *****************************************************************************************/
        private class TextFieldKeyHandlerForListSelectionUpDown extends KeyAdapter
        {
            private final JList<String> targetList;

            /**************************************************************************************
             * Font chooser text field key handling class constructor
             *
             * @param list
             *            target list
             *************************************************************************************/
            TextFieldKeyHandlerForListSelectionUpDown(JList<String> list)
            {
                targetList = list;
            }

            /**************************************************************************************
             * Handle a key press event
             *************************************************************************************/
            @Override
            public void keyPressed(KeyEvent ke)
            {
                int index = targetList.getSelectedIndex();

                switch (ke.getKeyCode())
                {
                    case KeyEvent.VK_UP:
                        index = targetList.getSelectedIndex() - 1;

                        if (index < 0)
                        {
                            index = 0;
                        }

                        targetList.setSelectedIndex(index);
                        break;

                    case KeyEvent.VK_DOWN:
                        int listSize = targetList.getModel().getSize();
                        index = targetList.getSelectedIndex() + 1;

                        if (index >= listSize)
                        {
                            index = listSize - 1;
                        }

                        targetList.setSelectedIndex(index);
                        break;

                    default:
                        break;
                }
            }
        }

        /******************************************************************************************
         * Font chooser list search handling class
         *****************************************************************************************/
        private class ListSearchTextFieldDocumentHandler implements DocumentListener
        {
            JList<String> targetList;

            /**************************************************************************************
             * Font chooser list selector class
             *************************************************************************************/
            class ListSelector implements Runnable
            {
                private final int index;

                /**********************************************************************************
                 * Font chooser list selector class constructor
                 *
                 * @param index
                 *            list index
                 *********************************************************************************/
                ListSelector(int index)
                {
                    this.index = index;
                }

                /**********************************************************************************
                 * Execute in a separate thread
                 *********************************************************************************/
                @Override
                public void run()
                {
                    targetList.setSelectedIndex(index);
                }
            }

            /**************************************************************************************
             * Font chooser list search handling class constructor
             *
             * @param targetList
             *            search list
             *************************************************************************************/
            ListSearchTextFieldDocumentHandler(JList<String> targetList)
            {
                this.targetList = targetList;
            }

            /**************************************************************************************
             * Handle a list item insertion event
             *************************************************************************************/
            @Override
            public void insertUpdate(DocumentEvent de)
            {
                update(de);
            }

            /**************************************************************************************
             * Handle a list item deletion event
             *************************************************************************************/
            @Override
            public void removeUpdate(DocumentEvent de)
            {
                update(de);
            }

            /**************************************************************************************
             * Handle a list item change event
             *************************************************************************************/
            @Override
            public void changedUpdate(DocumentEvent de)
            {
                update(de);
            }

            /**************************************************************************************
             * Update the list
             *
             * @param de
             *            document event
             *************************************************************************************/
            private void update(DocumentEvent de)
            {
                String newValue = "";

                try
                {
                    Document doc = de.getDocument();
                    newValue = doc.getText(0, doc.getLength());
                }
                catch (BadLocationException ble)
                {
                    CcddUtilities.displayException(ble, ccddMain.getMainFrame());
                }

                if (newValue.length() > 0)
                {
                    int index = targetList.getNextMatch(newValue, 0, Position.Bias.Forward);

                    if (index < 0)
                    {
                        index = 0;
                    }

                    targetList.ensureIndexIsVisible(index);

                    String matchedName = targetList.getModel().getElementAt(index).toString();

                    if (newValue.equalsIgnoreCase(matchedName))
                    {
                        if (index != targetList.getSelectedIndex())
                        {
                            SwingUtilities.invokeLater(new ListSelector(index));
                        }
                    }
                }
            }
        }

        /******************************************************************************************
         * Refresh the sample text field
         *****************************************************************************************/
        private void updateSampleFont()
        {
            getSampleTextField().setFont(getSelectedFont());
        }

        /******************************************************************************************
         * Get the panel containing the font family selection controls
         *
         * @return JPanel containing the font family selection controls
         *****************************************************************************************/
        private JPanel getFontFamilyPanel()
        {
            JPanel fontNamePnl = new JPanel();
            fontNamePnl.setLayout(new BorderLayout());
            fontNamePnl.setBorder(borderPnl);

            JScrollPane scrollPane = new JScrollPane(getFontFamilyList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel familyPnl = new JPanel();
            familyPnl.setLayout(new BorderLayout());
            familyPnl.add(getFontFamilyTextField(), BorderLayout.NORTH);
            familyPnl.add(scrollPane, BorderLayout.CENTER);

            JLabel familyLbl = new JLabel(("Font Name"));
            familyLbl.setBorder(borderLbl);
            familyLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            familyLbl.setHorizontalAlignment(SwingConstants.LEFT);
            familyLbl.setHorizontalTextPosition(SwingConstants.LEFT);
            familyLbl.setLabelFor(getFontFamilyTextField());

            fontNamePnl.add(familyLbl, BorderLayout.NORTH);
            fontNamePnl.add(familyPnl, BorderLayout.CENTER);
            return fontNamePnl;
        }

        /******************************************************************************************
         * Get the panel containing the font style selection controls
         *
         * @return JPanel containing the font style selection controls
         *****************************************************************************************/
        private JPanel getFontStylePanel()
        {
            JPanel fontStylePnl = new JPanel();
            fontStylePnl.setLayout(new BorderLayout());
            fontStylePnl.setBorder(borderPnl);

            JScrollPane scrollPane = new JScrollPane(getFontStyleList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel stylePnl = new JPanel();
            stylePnl.setLayout(new BorderLayout());
            stylePnl.add(getFontStyleTextField(), BorderLayout.NORTH);
            stylePnl.add(scrollPane, BorderLayout.CENTER);

            JLabel styleLbl = new JLabel(("Font Style"));
            styleLbl.setBorder(borderLbl);
            styleLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            styleLbl.setHorizontalAlignment(SwingConstants.LEFT);
            styleLbl.setHorizontalTextPosition(SwingConstants.LEFT);
            styleLbl.setLabelFor(getFontStyleTextField());

            fontStylePnl.add(styleLbl, BorderLayout.NORTH);
            fontStylePnl.add(stylePnl, BorderLayout.CENTER);
            return fontStylePnl;
        }

        /******************************************************************************************
         * Get the panel containing the font size selection controls
         *
         * @return JPanel containing the font size selection controls
         *****************************************************************************************/
        private JPanel getFontSizePanel()
        {
            JPanel fontSizePnl = new JPanel();
            fontSizePnl.setLayout(new BorderLayout());
            fontSizePnl.setBorder(borderPnl);

            JScrollPane scrollPane = new JScrollPane(getFontSizeList());
            scrollPane.getVerticalScrollBar().setFocusable(false);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

            JPanel sizePnl = new JPanel();
            sizePnl.setLayout(new BorderLayout());
            sizePnl.add(getFontSizeTextField(), BorderLayout.NORTH);
            sizePnl.add(scrollPane, BorderLayout.CENTER);

            JLabel sizeLbl = new JLabel(("Font Size"));
            sizeLbl.setBorder(borderLbl);
            sizeLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            sizeLbl.setHorizontalAlignment(SwingConstants.LEFT);
            sizeLbl.setHorizontalTextPosition(SwingConstants.LEFT);
            sizeLbl.setLabelFor(getFontSizeTextField());

            fontSizePnl.add(sizeLbl, BorderLayout.NORTH);
            fontSizePnl.add(sizePnl, BorderLayout.CENTER);
            return fontSizePnl;
        }

        /******************************************************************************************
         * Get the panel containing the sample text field
         *
         * @return JPanel containing the sample text field
         *****************************************************************************************/
        private JPanel getSamplePanel()
        {
            JPanel samplePnl = new JPanel();
            samplePnl.setLayout(new BorderLayout());
            samplePnl.setBorder(BorderFactory.createEmptyBorder(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                0,
                                                                ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2));
            JLabel sampleLbl = new JLabel(("Sample"));
            sampleLbl.setBorder(borderLbl);
            sampleLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            sampleLbl.setHorizontalAlignment(SwingConstants.LEFT);
            sampleLbl.setHorizontalTextPosition(SwingConstants.LEFT);
            sampleLbl.setLabelFor(getFontSizeTextField());
            samplePnl.add(sampleLbl, BorderLayout.NORTH);
            samplePnl.add(getSampleTextField(), BorderLayout.CENTER);
            samplePnl.setPreferredSize(new Dimension(samplePnl.getPreferredSize().width,
                                                     samplePnl.getFontMetrics(new Font("DejaVu Sans",
                                                                                       Font.BOLD,
                                                                                       72))
                                                              .getHeight() * 3 / 2));
            return samplePnl;
        }

        /******************************************************************************************
         * Get the sample text field
         *
         * @return reference to the sample text field
         *****************************************************************************************/
        private JTextField getSampleTextField()
        {
            if (sampleText == null)
            {
                sampleText = new JTextField(("Sample text 12345"));
                sampleText.setBorder(borderFld);
            }

            return sampleText;
        }

        /******************************************************************************************
         * Get the array of available font families
         *
         * @return Array of available font families
         *****************************************************************************************/
        private String[] getFontFamilies()
        {
            if (fontFamilyNames == null)
            {
                GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
                fontFamilyNames = env.getAvailableFontFamilyNames();
            }

            return fontFamilyNames;
        }

        /******************************************************************************************
         * Get the array of font style names
         *
         * @return Array of font style names
         *****************************************************************************************/
        private String[] getFontStyleNames()
        {
            if (fontStyleNames == null)
            {
                fontStyleNames = new String[] {"Plain",
                                               "<html><b>Bold",
                                               "<html><i>Italic",
                                               "<html><b><i>BoldItalic"};
            }

            return fontStyleNames;
        }

        // CCDD: Added method to create the chooser panel
        /******************************************************************************************
         * Create a font chooser panel for insertion into a dialog, etc.
         *
         * @return JPanel containing the font chooser components
         *****************************************************************************************/
        protected JPanel createChooserPanel()
        {
            JPanel chooserPnl = new JPanel();
            chooserPnl.setBorder(BorderFactory.createEmptyBorder());
            chooserPnl.add(this, BorderLayout.CENTER);
            return chooserPnl;
        }
        // CCDD: End modification
    }

    /**********************************************************************************************
     * JTabbedPane with drag and drop of tabs within and between tabbed panes class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    public static class DnDTabbedPane extends JTabbedPane
    {
        private final DataFlavor flavor;
        private static DnDGlassPane glassPane;
        private boolean isDrawRect;
        private final Rectangle2D lineRect;
        private final Color lineColor;
        private final Class<?> compatibleTarget;
        private boolean spawnNeeded;
        private final int lineWidth;
        private static final String NAME = "TabTransferData";

        /******************************************************************************************
         * Transferable tab class
         *****************************************************************************************/
        public class TabTransferable implements Transferable
        {
            private TabTransferData data = null;

            /**************************************************************************************
             * Transferable tab class constructor
             *
             * @param tabbedPane
             *            reference to the tabbed pane
             *
             * @param tabIndex
             *            index of the tab being moved
             *************************************************************************************/
            public TabTransferable(DnDTabbedPane tabbedPane, int tabIndex)
            {
                data = new TabTransferData(DnDTabbedPane.this, tabIndex);
            }

            /**************************************************************************************
             * Get the transfer data
             *
             * @param flavor
             *            data flavor
             *
             * @return Transfer data
             *************************************************************************************/
            @Override
            public Object getTransferData(DataFlavor flavor)
            {
                return data;
            }

            /**************************************************************************************
             * Get an array containing the transfer data flavor
             *
             * @return Array containing the transfer data flavor
             *************************************************************************************/
            @Override
            public DataFlavor[] getTransferDataFlavors()
            {
                return new DataFlavor[] {flavor};
            }

            /**************************************************************************************
             * Determine if the data flavor is supported
             *
             * @param flavor
             *            data flavor
             *
             * @return true if the data flavor is supported
             *************************************************************************************/
            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor)
            {
                return flavor.getHumanPresentableName().equals(NAME);
            }
        }

        /******************************************************************************************
         * Tab transfer data class
         *****************************************************************************************/
        public class TabTransferData
        {
            private DnDTabbedPane tabbedPane = null;
            private int tabIndex = -1;

            /**************************************************************************************
             * Tab transfer data class constructor
             *************************************************************************************/
            TabTransferData()
            {
            }

            /**************************************************************************************
             * Tab transfer data class constructor
             *
             * @param tabbedPane
             *            reference to the tabbed pane
             *
             * @param tabIndex
             *            index of the tab being moved
             *************************************************************************************/
            TabTransferData(DnDTabbedPane tabbedPane, int tabIndex)
            {
                this.tabbedPane = tabbedPane;
                this.tabIndex = tabIndex;
            }

            /**************************************************************************************
             * Get the reference to the tabbed pane
             *
             * @return Reference to the tabbed pan
             *************************************************************************************/
            public DnDTabbedPane getTabbedPane()
            {
                return tabbedPane;
            }

            /**************************************************************************************
             * Set the reference to the tabbed pane
             *
             * @param tabbedPane
             *            reference to the tabbed pane
             *************************************************************************************/
            public void setTabbedPane(DnDTabbedPane tabbedPane)
            {
                this.tabbedPane = tabbedPane;
            }

            /**************************************************************************************
             * Get the index of the tab
             *
             * @return Index of the tab
             *************************************************************************************/
            public int getTabIndex()
            {
                return tabIndex;
            }

            /**************************************************************************************
             * Set the index of the tab
             *
             * @param tabIndex
             *            index of the tab
             *************************************************************************************/
            public void setTabIndex(int tabIndex)
            {
                this.tabIndex = tabIndex;
            }
        }

        /******************************************************************************************
         * Drop target listener class
         *****************************************************************************************/
        public class CCDDDropTargetListener implements DropTargetListener
        {
            /**************************************************************************************
             * Handle a drop target drag event
             *************************************************************************************/
            @Override
            public void dragEnter(DropTargetDragEvent dtde)
            {
                // Check if dragging is possible at the current location
                if (isDragAcceptable(dtde))
                {
                    dtde.acceptDrag(dtde.getDropAction());

                    // Update the glass pane to display the dragged tab
                    DnDTabbedPane.this.getRootPane().setGlassPane(glassPane);
                    glassPane.setImage(getTabTransferData(dtde).getTabbedPane().getDnDGlassPane().getImage());
                    glassPane.setVisible(true);
                }
                // Dragging isn't possible at the current location
                else
                {
                    dtde.rejectDrag();
                }
            }

            /**************************************************************************************
             * Handle a drag exit event
             *************************************************************************************/
            @Override
            public void dragExit(DropTargetEvent dte)
            {
                isDrawRect = false;
            }

            /**************************************************************************************
             * Handle a drop action changed event
             *************************************************************************************/
            @Override
            public void dropActionChanged(DropTargetDragEvent dtde)
            {
            }

            /**************************************************************************************
             * Handle a drag over event
             *************************************************************************************/
            @Override
            public void dragOver(final DropTargetDragEvent dtde)
            {
                // Get the reference to the tab transfer data
                TabTransferData sourceData = getTabTransferData(dtde);

                // Check if the tab transfer data exists
                if (sourceData != null)
                {
                    // Check if the tabs are placed along the top or bottom of the tabbed pane
                    if (getTabPlacement() == JTabbedPane.TOP
                        || getTabPlacement() == JTabbedPane.BOTTOM)
                    {
                        // Draw the line beside the tab to indicate the insertion point
                        drawTargetLineToSide(getTargetTabIndex(dtde.getLocation()), sourceData);
                    }
                    // The tabs are placed along the left or right of the tabbed pane
                    else
                    {
                        // Draw the line beside the tab to indicate the insertion point
                        drawTargetLineAboveBelow(getTargetTabIndex(dtde.getLocation()), sourceData);
                    }

                    // Redraw the tab
                    repaint();
                }

                // Reposition the glass pane displaying the dragged tab
                glassPane.setPoint(adjustGlassPaneLocation(dtde.getLocation(),
                                                           (sourceData != null
                                                                               ? sourceData.getTabbedPane()
                                                                               : null)));
                glassPane.repaint();
            }

            /**************************************************************************************
             * Handle a drop event
             *************************************************************************************/
            @Override
            public void drop(DropTargetDropEvent dtde)
            {
                // Check if dropping is possible at the current location
                if (isDropAcceptable(dtde))
                {
                    // Relocate the dragged tab to its new location
                    moveTabToTarget(getTabTransferData(dtde),
                                    getTargetTabIndex(dtde.getLocation()));
                    dtde.dropComplete(true);
                }
                // Dropping isn't possible at the current location
                else
                {
                    dtde.dropComplete(false);
                }

                isDrawRect = false;
                repaint();
            }

            /**************************************************************************************
             * Determine if dragging is possible
             *
             * @param dtde
             *            drop target drag event reference
             *
             * @return true is the drop target can accept the drag event
             *************************************************************************************/
            public boolean isDragAcceptable(DropTargetDragEvent dtde)
            {
                boolean isAcceptable = false;

                // Get the reference to the transferable object
                Transferable transferable = dtde.getTransferable();

                // Check if the transferable object exists
                if (transferable != null)
                {
                    // Get the drop target's data flavor
                    DataFlavor[] flavor = dtde.getCurrentDataFlavors();

                    // Check if the flavors match
                    if (transferable.isDataFlavorSupported(flavor[0]))
                    {
                        // Get the reference to the tab transfer data
                        TabTransferData sourceData = getTabTransferData(dtde);

                        // Check if the tab transfer data exists
                        if (sourceData != null)
                        {
                            // Set the flag if the drag occurs within the original tabbed pane, or
                            // if in another, compatible tabbed pane
                            isAcceptable = ((DnDTabbedPane.this == sourceData.getTabbedPane()
                                             && sourceData.getTabIndex() >= 0)
                                            || (DnDTabbedPane.this != sourceData.getTabbedPane()
                                                && DnDTabbedPane.this.compatibleTarget != null
                                                && DnDTabbedPane.this.compatibleTarget == sourceData.getTabbedPane().getCompatibleTarget()));
                        }
                    }
                }

                return isAcceptable;
            }

            /**************************************************************************************
             * Determine if dropping is possible
             *
             * @param dtde
             *            drop target drag event reference
             *
             * @return true is the drop target can accept the drop event
             *************************************************************************************/
            public boolean isDropAcceptable(DropTargetDropEvent dtde)
            {
                boolean isAcceptable = false;

                // Get the reference to the transferable object
                Transferable transferable = dtde.getTransferable();

                // Check if the transferable object exists
                if (transferable != null)
                {
                    // Get the drop target's data flavor
                    DataFlavor[] flavor = dtde.getCurrentDataFlavors();

                    // Check if the flavors match
                    if (transferable.isDataFlavorSupported(flavor[0]))
                    {
                        // Get the reference to the tab transfer data
                        TabTransferData sourceData = getTabTransferData(dtde);

                        // Check if the tab transfer data exists
                        if (sourceData != null)
                        {
                            // Set the flag if the drop occurs within the original tabbed pane, or
                            // if in another, compatible tabbed pane
                            isAcceptable = ((DnDTabbedPane.this == sourceData.getTabbedPane()
                                             && sourceData.getTabIndex() >= 0)
                                            || (DnDTabbedPane.this != sourceData.getTabbedPane()
                                                && DnDTabbedPane.this.compatibleTarget != null
                                                && DnDTabbedPane.this.compatibleTarget == sourceData.getTabbedPane().getCompatibleTarget()));
                        }
                    }
                }

                return isAcceptable;
            }
        }

        /******************************************************************************************
         * Drag and drop glass pane class. The glass pane is used to display an image of the tab
         * being moved
         *****************************************************************************************/
        public static class DnDGlassPane extends JPanel
        {
            private final Point location;
            private BufferedImage dragImage;
            private final AlphaComposite composite;

            /**************************************************************************************
             * Drag and drop glass pane class constructor
             *************************************************************************************/
            public DnDGlassPane()
            {
                setOpaque(false);
                dragImage = null;
                location = new Point(0, 0);
                composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f);
            }

            /**************************************************************************************
             * Get the drag image
             *
             * @return Drag image
             *************************************************************************************/
            public BufferedImage getImage()
            {
                return dragImage;
            }

            /**************************************************************************************
             * Set the drag image
             *
             * @param dragImage
             *            drag image
             *************************************************************************************/
            public void setImage(BufferedImage dragImage)
            {
                this.dragImage = dragImage;
            }

            /**************************************************************************************
             * Set the glass pane location
             *
             * @param location
             *            point at which to locate the glass pane
             *************************************************************************************/
            public void setPoint(Point location)
            {
                this.location.x = location.x;
                this.location.y = location.y;
            }

            /**************************************************************************************
             * Paint the glass pane
             *************************************************************************************/
            @Override
            public void paintComponent(Graphics g)
            {
                // Check if the drag image exists
                if (dragImage != null)
                {
                    // Draw the drag image
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setComposite(composite);
                    g2.drawImage(dragImage,
                                 (int) location.getX(),
                                 (int) location.getY(),
                                 null);
                }
            }
        }

        /******************************************************************************************
         * JTabbedPane with drag and drop of tabs within and between tabbed panes class
         * constructor. This constructor is configurable so that dropping the tab outside its
         * parent container is allowable
         *
         * @param tabPlacement
         *            determines the location of the tabs on the tabbed pane: JTabbedPane.TOP,
         *            JTabbedPane.BOTTOM, JTabbedPane.LEFT, or JTabbedPane.RIGHT
         *
         * @param compatibleTarget
         *            reference to the type of class that tabs from this class can be dropped onto;
         *            null if dropping outside the original container isn't allowed
         *
         * @param isSpawnAllowed
         *            true if a new container is created when the tab is dropped outside of a
         *            compatible target; false to ignore drops outside compatible containers
         *****************************************************************************************/
        public DnDTabbedPane(int tabPlacement,
                             Class<?> compatibleTarget,
                             final boolean isSpawnAllowed)
        {
            super();

            setTabPlacement(tabPlacement);
            this.compatibleTarget = compatibleTarget;
            spawnNeeded = true;

            flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, NAME);
            glassPane = new DnDGlassPane();

            // Set the tab insertion point indicator attributes
            isDrawRect = false;
            lineRect = new Rectangle2D.Double();
            lineWidth = ModifiableSizeInfo.TAB_MOVE_LOCATION_INDICATOR_WIDTH.getSize();
            lineColor = ModifiableColorInfo.TAB_MOVE_LOCATION_INDICATOR.getColor();

            final DragSourceListener dsl = new DragSourceAdapter()
            {
                /**********************************************************************************
                 * Handle a drag enter event
                 *********************************************************************************/
                @Override
                public void dragEnter(DragSourceDragEvent dsde)
                {
                    // Set the cursor indicating that dropping is allowed
                    dsde.getDragSourceContext().setCursor(DragSource.DefaultMoveDrop);
                }

                /**********************************************************************************
                 * Handle a drag exit event
                 *********************************************************************************/
                @Override
                public void dragExit(DragSourceEvent dse)
                {
                    // Check if the tab's original pane has no other tabs (the last remaining tab
                    // isn't allowed to be moved) or if spawning a new container isn't allowed for
                    // this tab
                    if (DnDTabbedPane.this.getTabCount() == 1 || !isSpawnAllowed)
                    {
                        // Set the cursor indicating that dropping isn't allowed
                        dse.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
                    }

                    // Set the tab insertion indicator line size and glass pane so that these
                    // aren't visible
                    lineRect.setRect(0, 0, 0, 0);
                    isDrawRect = false;
                    glassPane.setVisible(false);
                }

                /**********************************************************************************
                 * Handle a drag over event
                 *********************************************************************************/
                @Override
                public void dragOver(DragSourceDragEvent dsde)
                {
                    // Get the reference to the tab transfer data
                    TabTransferData sourceData = getTabTransferData(dsde);

                    // Check if the tab transfer data doesn't exist, and if the tab's original pane
                    // has no other tabs (the last remaining tab isn't allowed to be moved) or if
                    // spawning a new container isn't allowed for this tab
                    if (sourceData == null
                        && (DnDTabbedPane.this.getTabCount() == 1 || !isSpawnAllowed))
                    {
                        // Set the cursor indicating that dropping isn't allowed
                        dsde.getDragSourceContext().setCursor(DragSource.DefaultMoveNoDrop);
                    }
                    // Dragging is allowed
                    else
                    {
                        // Set the cursor indicating that dropping is allowed
                        dsde.getDragSourceContext().setCursor(DragSource.DefaultMoveDrop);
                    }
                }

                /**********************************************************************************
                 * Handle the end of the drag and drop operation. If the dragged tab wasn't dropped
                 * on an acceptable container and the tab allows creation of a new container in
                 * which to place it then create the new container for the tab
                 *********************************************************************************/
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde)
                {
                    isDrawRect = false;
                    lineRect.setRect(0, 0, 0, 0);

                    // Check if the tabbed pane has at least one other tab that the one being
                    // dragged, the tab didn't find an acceptable target container (i.e., spawning
                    // a new container is needed), and that spawning a new container is allowed
                    if (DnDTabbedPane.this.getTabCount() > 1 && spawnNeeded && isSpawnAllowed)
                    {
                        DnDTabbedPane sourcePane = DnDTabbedPane.this;

                        // Get the index of the tab to be moved
                        int sourceIndex = sourcePane.getTabTransferData(dsde).getTabIndex();

                        // Check if the tab is valid
                        if (sourceIndex >= 0)
                        {
                            // Remove the tab from its current pane
                            sourcePane.remove(sourceIndex);

                            // Check if the moved tab was the selected one in the original pane
                            if (sourcePane.getSelectedIndex() == sourcePane.getTabCount())
                            {
                                // Adjust the selected tab index in the original pane
                                sourcePane.setSelectedIndex(sourcePane.getTabCount() - 1);
                            }

                            // Remove the tab from its original container
                            Object tabContents = sourcePane.tabMoveCleanup(sourceIndex, -1, null);

                            // Create a new container in which to place the moved tab
                            sourcePane.spawnContainer(sourceIndex, tabContents);
                        }
                    }

                    // Reset the flag for the next move
                    spawnNeeded = true;

                    // Remove the image of the moved tab
                    glassPane.setVisible(false);
                    glassPane.setImage(null);
                }
            };

            // Create a drag gesture listener
            DragGestureListener gestureListener = new DragGestureListener()
            {
                /**********************************************************************************
                 * Handle a drag gesture recognized event
                 *********************************************************************************/
                @Override
                public void dragGestureRecognized(DragGestureEvent dge)
                {
                    // Get the point where the drag started
                    Point tabPt = dge.getDragOrigin();

                    // Get the index of the tab being dragged
                    int dragTabIndex = indexAtLocation(tabPt.x, tabPt.y);

                    // Check if the tab index is valid
                    if (dragTabIndex >= 0)
                    {
                        // initialize the glass pane that displays an image of the tab being moved
                        initGlassPane(dge.getComponent(), dge.getDragOrigin(), dragTabIndex);

                        try
                        {
                            // Begin the drag operation
                            dge.startDrag(DragSource.DefaultMoveDrop,
                                          new TabTransferable(DnDTabbedPane.this, dragTabIndex),
                                          dsl);
                        }
                        catch (InvalidDnDOperationException idoe)
                        {
                            CcddUtilities.displayException(idoe, ccddMain.getMainFrame());
                        }
                    }
                }
            };

            // Create a drag gesture recognizer and drop target
            new DragSource().createDefaultDragGestureRecognizer(this,
                                                                DnDConstants.ACTION_COPY_OR_MOVE,
                                                                gestureListener);
            new DropTarget(this,
                           DnDConstants.ACTION_COPY_OR_MOVE,
                           new CCDDDropTargetListener(),
                           true);
        }

        /******************************************************************************************
         * JTabbedPane with drag and drop of tabs within and between tabbed panes class
         * constructor. The tabs are placed at the top of the tabbed pane and dropping a tab
         * outside of its parent container isn't allowed
         *****************************************************************************************/
        public DnDTabbedPane()
        {
            this(JTabbedPane.TOP, null, false);
        }

        /******************************************************************************************
         * JTabbedPane with drag and drop of tabs within and between tabbed panes class
         * constructor. This constructor doesn't allow dropping a tab outside of its parent
         * container
         *
         * @param tabPlacement
         *            determines the location of the tabs on the tabbed pane: JTabbedPane.TOP,
         *            JTabbedPane.BOTTOM, JTabbedPane.LEFT, or JTabbedPane.RIGHT
         *****************************************************************************************/
        DnDTabbedPane(int tabPlacement)
        {
            this(tabPlacement, null, false);
        }

        /******************************************************************************************
         * Get the compatible drop target class
         *
         * @return The compatible drop target class; null if the tab isn't allowed to be dropped
         *         outside of its parent container
         *****************************************************************************************/
        protected Class<?> getCompatibleTarget()
        {
            return compatibleTarget;
        }

        /******************************************************************************************
         * Get the reference to the glass pane
         *
         * @return Reference to the glass pane
         ******************************************************************************************/
        protected DnDGlassPane getDnDGlassPane()
        {
            return glassPane;
        }

        /******************************************************************************************
         * Placeholder for method to do any other operations related to moving the tab
         *
         * @param oldTabIndex
         *            original tab index; -1 if the tab didn't originate in this tabbed pane
         *
         * @param newTabIndex
         *            new tab index; -1 if the tab is being moved to another tabbed pane
         *
         * @param tabContents
         *            reference to the components in the tab being moved
         *
         * @return Reference to the container receiving the tab
         *****************************************************************************************/
        protected Object tabMoveCleanup(int oldTabIndex, int newTabIndex, Object tabContents)
        {
            return null;
        };

        /******************************************************************************************
         * Placeholder for method to spawn a new container for a moved tab
         *
         * @param sourceTabIndex
         *            index of the tab in its original pane
         *
         * @param tabContents
         *            reference to the components in the tab being moved
         *****************************************************************************************/
        protected void spawnContainer(int sourceTabIndex, Object tabContents)
        {
        }

        /******************************************************************************************
         * Get the tab transfer data from the supplied drop target drop event
         *
         * @param dtde
         *            drop target drop event
         *
         * @return Tab transfer data from the supplied drop target drop event
         *****************************************************************************************/
        private TabTransferData getTabTransferData(DropTargetDropEvent dtde)
        {
            TabTransferData data = null;

            try
            {
                data = (TabTransferData) dtde.getTransferable().getTransferData(flavor);
            }
            catch (Exception e)
            {
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }

            return data;
        }

        /******************************************************************************************
         * Get the tab transfer data from the supplied drop target drag event
         *
         * @param dtde
         *            drop target drop event
         *
         * @return Tab transfer data from the supplied drop target drag event
         *****************************************************************************************/
        private TabTransferData getTabTransferData(DropTargetDragEvent dtde)
        {
            TabTransferData data = null;

            try
            {
                data = (TabTransferData) dtde.getTransferable().getTransferData(flavor);
            }
            catch (Exception e)
            {
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }

            return data;
        }

        /******************************************************************************************
         * Get the tab transfer data from the supplied drag source drag event
         *
         * @param dsde
         *            drop source drop event
         *
         * @return Tab transfer data from the supplied drag source drag event
         *****************************************************************************************/
        private TabTransferData getTabTransferData(DragSourceDragEvent dsde)
        {
            TabTransferData data = null;

            try
            {
                data = (TabTransferData) dsde.getDragSourceContext()
                                             .getTransferable()
                                             .getTransferData(flavor);
            }
            catch (Exception e)
            {
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }

            return data;
        }

        /******************************************************************************************
         * Get the tab transfer data from the supplied drag source drop event
         *
         * @param dsde
         *            drop source drop event
         *
         * @return Tab transfer data from the supplied drag source drop event
         *****************************************************************************************/
        private TabTransferData getTabTransferData(DragSourceDropEvent dsde)
        {
            TabTransferData data = null;

            try
            {
                data = (TabTransferData) dsde.getDragSourceContext()
                                             .getTransferable()
                                             .getTransferData(flavor);
            }
            catch (Exception e)
            {
                CcddUtilities.displayException(e, ccddMain.getMainFrame());
            }

            return data;
        }

        /******************************************************************************************
         * Get the tab index if the tab is dropped at the current location
         *
         * @param dropPoint
         *            location of the tab in the drop component's coordinates
         *
         * @return Tab index if the tab is dropped at the current location; after the last tab in
         *         the pane if the drop point isn't within a tab's boundaries
         *****************************************************************************************/
        private int getTargetTabIndex(Point dropPoint)
        {
            int insertBesideTab = getTabCount();

            // Check if the pane has a tab
            if (getTabCount() != 0)
            {
                // Step through each tab in the pane
                for (int tabIndex = 0; tabIndex < getTabCount(); tabIndex++)
                {
                    // Get the bounds of the tab at the current index
                    Rectangle tabBounds = getBoundsAt(tabIndex);

                    // Get the center point of the tab
                    int x = tabBounds.x - tabBounds.width / 2;
                    int y = tabBounds.y - tabBounds.height / 2;

                    // Check if the drop point is within the tab's bounds
                    if (dropPoint.x >= x
                        && dropPoint.x <= x + tabBounds.width
                        && dropPoint.y >= y
                        && dropPoint.y <= y + tabBounds.height)
                    {
                        // Set the index of the insertion point and stop searching
                        insertBesideTab = tabIndex;
                        break;
                    }
                }
            }

            return insertBesideTab;
        }

        /******************************************************************************************
         * Move the tab from its original location to the new one
         *
         * @param sourceData
         *            tab transfer data for the repositioned tab
         *
         * @param targetIndex
         *            index in the target container's tabbed pane at which to place the tab
         *****************************************************************************************/
        private void moveTabToTarget(TabTransferData sourceData, int targetIndex)
        {
            // Set the flag to indicate that spawning a new container isn't necessary
            sourceData.getTabbedPane().spawnNeeded = false;

            // Check if the tab transfer data exists
            if (sourceData != null)
            {
                // Get the tab index in the source pane
                int sourceIndex = sourceData.getTabIndex();

                // Check if the tab index is valid
                if (sourceIndex >= 0)
                {
                    // Get references to the target and source tab panes
                    DnDTabbedPane targetPane = this;
                    DnDTabbedPane sourcePane = sourceData.getTabbedPane();

                    // Get a reference to the tab component
                    Component comp = sourcePane.getComponentAt(sourceIndex);

                    // Get the tab's title
                    String title = sourcePane.getTitleAt(sourceIndex);

                    // Check if the tab source and target panes differ
                    if (this != sourcePane)
                    {
                        // Remove the tab from the source pane
                        sourcePane.removeTabAt(sourceIndex);

                        // Check if the pan'es selected tab is no longer valid
                        if (sourcePane.getSelectedIndex() == sourcePane.getTabCount())
                        {
                            // Set the selected tab to a valid index
                            sourcePane.setSelectedIndex(sourcePane.getTabCount() - 1);
                        }

                        // Check if the target position is at the end of the existing tabs
                        if (targetIndex == getTabCount())
                        {
                            // Add the tab at the end of the existing tabs in the pane
                            targetPane.addTab(title, comp);
                        }
                        // The target position is before the first tab or between tabs
                        else
                        {
                            // Check if the position is prior to the first tab
                            if (targetIndex < 0)
                            {
                                // Set the target index as the first tab
                                targetIndex = 0;
                            }

                            // Insert the tab at the specified position
                            targetPane.insertTab(title, null, comp, null, targetIndex);
                        }

                        // Perform any cleanup actions on the source and target panes due to moving
                        // the tab
                        Object tabContents = sourcePane.tabMoveCleanup(sourceIndex, -1, null);
                        setSelectedComponent(comp);
                        targetPane.tabMoveCleanup(-1, targetIndex, tabContents);
                    }
                    // The tab is being moved within the container. Check if the target index is
                    // valid
                    else if (targetIndex >= 0 && sourceIndex != targetIndex)
                    {
                        // Remove the tab from the source pane
                        sourcePane.removeTabAt(sourceIndex);

                        // Check if the target position is at the end of the existing tabs
                        if (targetIndex == getTabCount())
                        {
                            // Add the tab at the end of the existing tabs in the pane
                            addTab(title, comp);
                            setSelectedIndex(getTabCount() - 1);
                        }
                        // Check if the new position precedes the original one
                        else if (sourceIndex > targetIndex)
                        {
                            // Insert the tab at the new location
                            insertTab(title, null, comp, null, targetIndex);
                            setSelectedIndex(targetIndex);
                        }
                        // The new position follows the original one
                        else
                        {
                            // Insert the tab at the new location
                            insertTab(title, null, comp, null, targetIndex - 1);
                            setSelectedIndex(targetIndex - 1);
                        }

                        // Perform any cleanup actions on the pane due to moving the tab
                        tabMoveCleanup(sourceIndex, targetIndex, null);
                    }
                }
            }
        }

        /******************************************************************************************
         * Draw the tab insertion point indicator for tabbed panes where the tabs appear at the top
         * or bottom of the pane
         *
         * @param targetIndex
         *            index of the tab if dropped at the current location
         *
         * @param data
         *            reference to the tab transfer data
         *****************************************************************************************/
        private void drawTargetLineToSide(int targetIndex, TabTransferData data)
        {
            // Check if the target index precedes the first tab, the pane has no tabs, or the
            // target is for the tab being dragged
            if (targetIndex < 0
                || getTabCount() == 0
                || (data.getTabbedPane() == this
                    && (data.getTabIndex() == targetIndex
                        || targetIndex - data.getTabIndex() == 1)))
            {
                // Hide the indicator
                lineRect.setRect(0, 0, 0, 0);
                isDrawRect = false;
            }
            // Check if the target index is the first tab position
            else if (targetIndex == 0)
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(0);
                lineRect.setRect(-lineWidth / 2, rect.y, lineWidth, rect.height);
                isDrawRect = true;
            }
            // Check if the target index is the last tab position
            else if (targetIndex == getTabCount())
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(getTabCount() - 1);
                lineRect.setRect(rect.x + rect.width - lineWidth / 2,
                                 rect.y,
                                 lineWidth,
                                 rect.height);
                isDrawRect = true;
            }
            // The target index lies within the first and last tabs
            else
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(targetIndex - 1);
                lineRect.setRect(rect.x + rect.width - lineWidth / 2,
                                 rect.y,
                                 lineWidth,
                                 rect.height);
                isDrawRect = true;
            }
        }

        /******************************************************************************************
         * Draw the tab insertion point indicator for tabbed panes where the tabs appear at the
         * left or right of the pane
         *
         * @param targetIndex
         *            index of the tab if dropped at the current location
         *
         * @param data
         *            reference to the tab transfer data
         *****************************************************************************************/
        private void drawTargetLineAboveBelow(int targetIndex, TabTransferData data)
        {
            // Check if the target index precedes the first tab, the pane has no tabs, or the
            // target is for the tab being dragged
            if (targetIndex < 0
                || getTabCount() == 0
                || (data.getTabbedPane() == this
                    && (data.getTabIndex() == targetIndex
                        || targetIndex - data.getTabIndex() == 1)))
            {
                lineRect.setRect(0, 0, 0, 0);
                isDrawRect = false;
            }
            // Check if the target index is the first tab position
            else if (targetIndex == 0)
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(0);
                lineRect.setRect(rect.x, -lineWidth / 2, rect.width, lineWidth);
                isDrawRect = true;
            }
            // Check if the target index is the last tab position
            else if (targetIndex == getTabCount())
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(getTabCount() - 1);
                lineRect.setRect(rect.x,
                                 rect.y + rect.height - lineWidth / 2,
                                 rect.width,
                                 lineWidth);
                isDrawRect = true;
            }
            // The target index lies within the first and last tabs
            else
            {
                // Set the indicator size and set the flag so that the indicator is drawn
                Rectangle rect = getBoundsAt(targetIndex - 1);
                lineRect.setRect(rect.x,
                                 rect.y + rect.height - lineWidth / 2,
                                 rect.width,
                                 lineWidth);
                isDrawRect = true;
            }
        }

        /******************************************************************************************
         * Adjust the glass pane location to stay within the bounds set by the tabs in the supplied
         * tabbed pane
         *
         * @param location
         *            current position of the dragged glass pane
         *
         * @param pane
         *            tabbed pane in which the glass pane is displayed
         *
         * @return The glass pane location, adjusted to stay within the bounds set by the tabs
         *****************************************************************************************/
        private Point adjustGlassPaneLocation(Point location, DnDTabbedPane pane)
        {
            Point adjusted = new Point(location);

            // Check if the pane is valid and that it has at least one tab
            if (pane != null && pane.getTabCount() != 0)
            {
                // Initialize the minimum and maximum x and y values
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;

                // Step through each tab in the pane
                for (int index = 0; index < pane.getTabCount(); index++)
                {
                    // Get the tab's bounds and use it to update the minimum and maximum x and y
                    // values
                    Rectangle tabBounds = pane.getBoundsAt(index);
                    minX = (int) Math.min(minX, tabBounds.getMinX());
                    maxX = (int) Math.max(maxX, tabBounds.getMaxX());
                    minY = (int) Math.min(minY, tabBounds.getMinY());
                    maxY = (int) Math.max(maxY, tabBounds.getMaxY() - tabBounds.getHeight());
                }

                // Update the glass pane location to stay within the bounds set by the tabs
                if (adjusted.x < minX)
                {
                    adjusted.x = minX;
                }

                if (adjusted.x > maxX)
                {
                    adjusted.x = maxX;
                }

                if (adjusted.y < minY)
                {
                    adjusted.y = minY;
                }

                if (adjusted.y > maxY)
                {
                    adjusted.y = maxY;
                }
            }

            // Convert the coordinates
            return SwingUtilities.convertPoint(DnDTabbedPane.this, adjusted, glassPane);
        }

        /******************************************************************************************
         * Initialize the tabbed pane's glass pane to display an image of the tab being dragged
         *
         * @param comp
         *            reference to the tab to be dragged
         *
         * @param tabPt
         *            coordinates of the point in the tab being dragged
         *
         * @param tabIndex
         *            index of the tab to be dragged
         *****************************************************************************************/
        private void initGlassPane(Component comp, Point tabPt, int tabIndex)
        {
            // Set the glass pane
            getRootPane().setGlassPane(glassPane);

            // Get the bounds of the specified tab and create an image of it
            Rectangle rect = getBoundsAt(tabIndex);
            BufferedImage image = new BufferedImage(comp.getWidth(),
                                                    comp.getHeight(),
                                                    BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            comp.paint(g);
            image = image.getSubimage(rect.x, rect.y, rect.width, rect.height);

            // Place the image of the tab on the glass pane and make the glass pane visible
            glassPane.setImage(image);
            glassPane.setPoint(adjustGlassPaneLocation(tabPt, this));
            glassPane.setVisible(true);
        }

        /******************************************************************************************
         * Paint the tabbed pane, including the tab insertion point indicator
         *****************************************************************************************/
        @Override
        public void paintComponent(final Graphics g)
        {
            // TODO ADDED TRY-CATCH DUE TO ARRAY OUT OF BOUNDS EXCEPTIONS WHEN IMPORTING A LARGE
            // NUMBER OF TABLES (WHICH CREATE A LARGE NUMBER OF TABLE EDITORS)
            try
            {
                super.paintComponent(g);
            }
            catch (Exception e)
            {
                // Ignore the exception
            }

            // Check if the tab insertion indicator should be drawn
            if (isDrawRect)
            {
                // Draw the tab insertion point indicator
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(lineColor);
                g2.fill(lineRect);
            }
        }
    }

    /**********************************************************************************************
     * The standard JSONObject uses a HashMap and does not retain the order in which the key:value
     * pairs are stored. This custom JSON object uses a LinkedHashMap, so it maintains the
     * key:value pairs in the same order in which they were stored
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class OrderedJSONObject extends LinkedHashMap<Object, Object>
    {
        /******************************************************************************************
         * Override so as to use the standard JSONObject toString() method
         *****************************************************************************************/
        @Override
        public String toString()
        {
            return new JSONObject(this).toString();
        }

        /******************************************************************************************
         * Convert the key:value pair map into a string. The standard JSONObject toJSONString()
         * method can be used since the map is ordered at this point
         *
         * @return JSON key:value pair map converted to a string
         *****************************************************************************************/
        protected String toJSONString()
        {
            return new JSONObject(this).toJSONString();
        }
    }
}
