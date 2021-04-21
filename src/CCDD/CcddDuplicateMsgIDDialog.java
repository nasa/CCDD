/**************************************************************************************************
/** \file CcddDuplicateMsgIDDialog.java
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Dialog displaying a table containing duplicate message IDs and their owners. The dialog is
*     built on the CcddDialogHandler class.
*
*   \copyright
*     MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
*
*     Copyright (c) 2016-2021 United States Government as represented by the 
*     Administrator of the National Aeronautics and Space Administration.  All Rights Reserved.
*
*     This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
*     distributed and modified only pursuant to the terms of that agreement.  See the License for 
*     the specific language governing permissions and limitations under the
*     License at https://software.nasa.gov/.
*
*     Unless required by applicable law or agreed to in writing, software distributed under the
*     License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
*     either expressed or implied.
*
*   \par Limitations, Assumptions, External Events and Notes:
*     - TBD
*
**************************************************************************************************/
package CCDD;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.DuplicateMsgIDColumnInfo;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary show duplicate message IDs dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddDuplicateMsgIDDialog extends CcddDialogHandler {
    // List of message IDs that are used by multiple owners, and their owner
    private ArrayListMultiple duplicates;

    /**********************************************************************************************
     * Show duplicate message IDs dialog class constructor
     *
     * @param ccddMain main class
     *********************************************************************************************/
    CcddDuplicateMsgIDDialog(CcddMain ccddMain) {
        // Create the message ID dialog
        initialize(ccddMain.getMessageIDHandler(), ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Display the duplicate message ID dialog
     *
     * @param messageHandler message ID handler reference
     *
     * @param parent         GUI component over which to center any error dialog
     *********************************************************************************************/
    private void initialize(CcddMessageIDHandler messageHandler, Component parent) {
        // Get the list of message IDs in use - this creates the duplicates list
        messageHandler.getMessageIDsInUse(true, true, true, true, true, false, null, true, parent);

        // Get the list of duplicate message IDs
        duplicates = new ArrayListMultiple(1);
        duplicates.addAll(messageHandler.getDuplicates());

        // Sort the list based on the message ID values
        duplicates.sort(ArrayListMultipleSortType.HEXADECIMAL);

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
                GridBagConstraints.BOTH,
                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                        ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(), 0,
                        ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                0, 0);

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the table to display the duplicate message IDs
        CcddJTableHandler duplicatesTable = new CcddJTableHandler() {
            /**************************************************************************************
             * Allow multiple line display in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column) {
                return column == DuplicateMsgIDColumnInfo.OWNERS.ordinal();
            }

            /**************************************************************************************
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column) {
                return column == DuplicateMsgIDColumnInfo.OWNERS.ordinal();
            }

            /**************************************************************************************
             * Load the duplicate message ID data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData() {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(duplicates.toArray(new String[0][0]),
                        DuplicateMsgIDColumnInfo.getColumnNames(), "1:0", DuplicateMsgIDColumnInfo.getToolTips(), true,
                        true, true);
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(duplicatesTable);

        // Set up the field table parameters
        duplicatesTable.setFixedCharacteristics(scrollPane, false, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                TableSelectionMode.SELECT_BY_CELL, true, ModifiableColorInfo.TABLE_BACK.getColor(), false, true,
                ModifiableFontInfo.OTHER_TABLE_CELL.getFont(), true);

        // Define the panel to contain the table
        JPanel resultsTblPnl = new JPanel();
        resultsTblPnl.setLayout(new BoxLayout(resultsTblPnl, BoxLayout.X_AXIS));
        resultsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        resultsTblPnl.add(scrollPane);

        // Add the table to the dialog
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.gridy++;
        dialogPnl.add(resultsTblPnl, gbc);

        showOptionsDialog(parent, dialogPnl, "Duplicate Message IDs", DialogOption.PRINT_OPTION, true);
    }
}
