/**
 * CFS Command and Data Dictionary event log.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CCDD_ICON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.SEARCH_ICON;
import static CCDD.CcddConstants.EventLogMessageType.FAIL_MSG;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EventColumns;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchDialogType;
import CCDD.CcddConstants.TableInsertionPoint;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary event log class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddEventLogDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbControlHandler dbControl;
    private CcddJTableHandler eventTable;
    private DefaultTableModel eventTableModel;

    // Components that need to accessed by multiple methods
    private JPanel logPanel;
    private JCheckBox[] filterCheckBox;
    private FileEnvVar logFile;
    private PrintWriter logWriter;

    // Set to true if this event log is for the current session
    private final boolean isSessionLog;

    // Event log file write status; true if the file is writable
    private boolean isLogWrite;

    // Next logged event's index number
    private long indexNum;

    // Row filter, used to show/hide event types
    private RowFilter<TableModel, Object> rowFilter;

    // List for containing the logged events
    private List<Object[]> eventLogList;

    // CCDD logo graphic
    private BufferedImage image;

    /**********************************************************************************************
     * Event log class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param logFile
     *            event log file; null if creating the session log or if opening a user-selected
     *            log file
     *
     * @param targetRow
     *            row index a specific log entry in an existing log to display in a stand-alone
     *            table without message length constraints; null if not displaying a single log
     *            entry
     *
     * @param isSessionLog
     *            true if this is the event log for the current session
     *********************************************************************************************/
    CcddEventLogDialog(final CcddMain ccddMain,
                       FileEnvVar logFile,
                       Long targetRow,
                       boolean isSessionLog)
    {
        this.ccddMain = ccddMain;
        this.isSessionLog = isSessionLog;
        this.logFile = logFile;
        dbControl = ccddMain.getDbControlHandler();

        // Create the event log
        initialize(logFile, targetRow);
    }

    /**********************************************************************************************
     * Event log class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param isSessionLog
     *            true if this is the event log for the current session
     *********************************************************************************************/
    CcddEventLogDialog(final CcddMain ccddMain, boolean isSessionLog)
    {
        this(ccddMain, null, null, isSessionLog);
    }

    /**********************************************************************************************
     * Get the event log table reference
     *
     * @return Reference to the event log table
     *********************************************************************************************/
    protected CcddJTableHandler getEventTable()
    {
        return eventTable;
    }

    /**********************************************************************************************
     * Get the event log file reference
     *
     * @return Reference to the event log file
     *********************************************************************************************/
    protected FileEnvVar getEventLogFile()
    {
        return logFile;
    }

    /**********************************************************************************************
     * Create the event log
     *
     * @param targetLogFile
     *            event log file; null if creating the session log or if opening a user-selected
     *            log file
     *
     * @param targetRow
     *            row index a specific log entry in an existing log to display in a stand-alone
     *            table without message length constraints; null if not displaying a single log
     *            entry
     *********************************************************************************************/
    private void initialize(FileEnvVar targetLogFile, final Long targetRow)
    {
        // Create storage for stored log events. The list is initially empty for the session log
        eventLogList = new ArrayList<Object[]>();

        // Flag indicating if only a single entry from an existing log file is to be displayed in
        // this log table
        boolean isOpenSingleEntry = (targetLogFile != null && targetRow != null);

        // Initialize the event index number
        indexNum = 1;

        // If a log file is provided then use it; otherwise attempt to open the event log file
        if (isOpenSingleEntry || openEventLogFile())
        {
            // Check if this is not the current session's event log
            if (!isSessionLog)
            {
                // Read the user-selected event log
                readEventLog(targetRow);
            }

            // Create the event log window
            createEventLogWindow(!isOpenSingleEntry);

            // Check if this log can display multiple log entries (this prevents reopening the log
            // entry for a single entry table)
            if (!isOpenSingleEntry)
            {
                // Add a listener for double right clicks on the message column
                setLogMessageListener();
            }

            // Check if this is not the current session's event log
            if (!isSessionLog)
            {
                // Set the table's row sorter and add the event type filter
                eventTable.setTableSortable();

                // Search the event log button
                JButton btnSearch = CcddButtonPanelHandler.createButton("Search",
                                                                        SEARCH_ICON,
                                                                        KeyEvent.VK_S,
                                                                        "Search the event log");

                // Add a listener for the Search button
                btnSearch.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Display the event log search dialog
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        ccddMain.showSearchDialog(SearchDialogType.LOG,
                                                  targetRow,
                                                  CcddEventLogDialog.this,
                                                  CcddEventLogDialog.this);
                    }
                });

                // Print the event log button
                JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                                       PRINT_ICON,
                                                                       KeyEvent.VK_P,
                                                                       "Print the event log");

                // Add a listener for the Print button
                btnPrint.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Print the event log
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Output the log to the printer
                        printEventLog();
                    }
                });

                // Close the event log button
                JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                       CLOSE_ICON,
                                                                       KeyEvent.VK_C,
                                                                       "Close the event log viewer");

                // Add a listener for the Close button
                btnClose.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Close the event log viewer
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        CcddEventLogDialog.this.closeFrame();
                    }
                });

                // Create a panel for the dialog buttons and add the buttons to the panel
                JPanel buttonPnl = new JPanel();
                buttonPnl.setBorder(BorderFactory.createEmptyBorder());
                buttonPnl.add(btnSearch);
                buttonPnl.add(btnPrint);
                buttonPnl.add(btnClose);

                // Display the event log window
                createFrame(ccddMain.getMainFrame(),
                            logPanel,
                            buttonPnl,
                            btnClose,
                            "Event Log: " + logFile.getName(),
                            null);
            }
        }
        // The log couldn't be opened
        else
        {
            // Close the log's frame
            closeFrame();
        }
    }

    /**********************************************************************************************
     * Handle the frame close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if this is the session log (main application window)
        if (isSessionLog)
        {
            // Exit the application
            ccddMain.exitApplication(true, 0);
        }
        // Not the session log
        else
        {
            // Close the log's frame
            closeFrame();
        }
    }

    /**********************************************************************************************
     * Remove the event log from the event logs list
     *********************************************************************************************/
    @Override
    protected void windowClosedAction()
    {
        // Remove this event log dialog from the list of open event logs
        ccddMain.getEventLogs().remove(CcddEventLogDialog.this);
    }

    /**********************************************************************************************
     * Create the event log window
     *
     * @param showFilters
     *            true to display the log message display filters
     *********************************************************************************************/
    private void createEventLogWindow(boolean showFilters)
    {
        // Define the event log table
        eventTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow resizing of the specified columns only
             *************************************************************************************/
            @Override
            protected boolean isColumnResizable(int column)
            {
                return column == EventColumns.PROJECT.ordinal()
                       || column == EventColumns.SERVER.ordinal()
                       || column == EventColumns.USER.ordinal()
                       || column == EventColumns.MESSAGE.ordinal();
            }

            /**************************************************************************************
             * Allow multiple line display in the specified column only
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column == EventColumns.MESSAGE.ordinal();
            }

            /**************************************************************************************
             * Load the event log data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the event log data into the table model along with the column names, set
                // up the editors and renderers for the table cells, hide the table grid lines, and
                // calculate the minimum width required to display the table information
                int totalWidth = setUpdatableCharacteristics(eventLogList.toArray(new Object[0][0]),
                                                             EventColumns.getColumnNames(),
                                                             null,
                                                             new String[] {"Event sequence number",
                                                                           "Server hosting the project database",
                                                                           "Project to which the event applies",
                                                                           "User that invoked the event",
                                                                           "Event date and time tag",
                                                                           "Event message type",
                                                                           "Event message"},
                                                             true,
                                                             true,
                                                             false);

                // Store the table width
                setTableWidth(totalWidth + LAF_SCROLL_BAR_WIDTH);

                // Set the dialog's title so that it includes the event log file name
                setTitle("Event Log: " + logFile.getName());

                // Clear the event log list since it is no longer needed
                eventLogList.clear();
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method in order to show/hide the events based on
             * event type
             *************************************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Get the table's row sorter and add the event type filter
                TableRowSorter<?> sorter = (TableRowSorter<?>) getRowSorter();

                // Check if the table has a sorter (i.e., has at least one visible row), that the
                // filter hasn't been set, and that there is an event type row filter
                if (sorter != null && sorter.getRowFilter() != rowFilter && rowFilter != null)
                {
                    // Apply the row filter that shows/hides the event types
                    sorter.setRowFilter(rowFilter);
                }
            }

            /**************************************************************************************
             * Override the table layout so that all extra width goes to the message column when
             * the table is resized
             *************************************************************************************/
            @Override
            public void doLayout()
            {
                // Get a reference to the column being resized
                if (getTableHeader() != null && getTableHeader().getResizingColumn() == null)
                {
                    // Get a reference to the event table's column model to shorten subsequent
                    // calls
                    TableColumnModel tcm = getColumnModel();

                    // Calculate the change in the event dialog's width
                    int delta = getParent().getWidth() - tcm.getTotalColumnWidth();

                    // Get the reference to the Message column
                    TableColumn msgColumn = tcm.getColumn(EventColumns.MESSAGE.ordinal());

                    // Set the Message column's width to its current width plus the extra width
                    // added to the dialog due to the resize
                    msgColumn.setPreferredWidth(msgColumn.getPreferredWidth() + delta);
                    msgColumn.setWidth(msgColumn.getPreferredWidth());
                }
                // Table header or resize column not available
                else
                {
                    super.doLayout();
                }
            }

            /**************************************************************************************
             * Handle a paint component event
             *************************************************************************************/
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);

                // Check that the logo image was successfully loaded and that no event rows are
                // currently visible
                if (image != null && getRowCount() == 0)
                {
                    // Make the logo image semi-transparent
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));

                    // Display the CCDD logo, centered in the panel
                    g2.drawImage(image,
                                 (getWidth() - image.getWidth()) / 2,
                                 (getHeight() - image.getHeight()) / 2,
                                 null);
                }
            }
        };

        // Place the log data table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(eventTable);

        // Set common table parameters and characteristics
        eventTable.setFixedCharacteristics(scrollPane,
                                           false,
                                           ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                           TableSelectionMode.SELECT_BY_CELL,
                                           true,
                                           ModifiableColorInfo.TABLE_BACK.getColor(),
                                           false,
                                           false,
                                           ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                           true);

        // Store the event table model to simplify later references
        eventTableModel = (DefaultTableModel) eventTable.getModel();

        // Define panel to contain the log and filter check boxes
        JPanel logAndFilterPnl = new JPanel(new BorderLayout());
        logAndFilterPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        logAndFilterPnl.add(scrollPane, BorderLayout.CENTER);

        // Check if the message type filters are to be displayed
        if (showFilters)
        {
            // Create a row filter for displaying the events based on selected type
            rowFilter = new RowFilter<TableModel, Object>()
            {
                /**********************************************************************************
                 * Override method that determines if a row should be displayed
                 *********************************************************************************/
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry)
                {
                    // Display the row if this message type, reflected in the type column, is
                    // selected for display. The type column text contains HTML tags that must be
                    // stripped to get the type name
                    return isFilter(getMessageType(entry.getValue(EventColumns.TYPE.ordinal())
                                                        .toString().replaceAll("\\<.*?>", "")));
                }
            };

            // Create a listener for check box selection changes
            ActionListener filterListener = new ActionListener()
            {
                /**********************************************************************************
                 * Handle check box selection changes
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Get a reference to the check box selected
                    JCheckBox checkBox = (JCheckBox) ae.getSource();

                    // Check if the 'All' check box was toggled
                    if (checkBox.equals(filterCheckBox[EventLogMessageType.SELECT_ALL.ordinal()]))
                    {
                        // Step through each filter check box
                        for (JCheckBox cb : filterCheckBox)
                        {
                            // Check if this is not the 'All' check box
                            if (!cb.equals(filterCheckBox[EventLogMessageType.SELECT_ALL.ordinal()]))
                            {
                                // Set the state to match the 'All' check box
                                cb.setSelected(checkBox.isSelected());
                            }
                        }
                    }
                    // The check box selected is not the 'All' check box
                    else
                    {
                        boolean isAllSelected = true;

                        // Step through each filter check box
                        for (JCheckBox cb : filterCheckBox)
                        {
                            // Check if this is not the 'All' check box and that the check box
                            // isn't selected (but is visible)
                            if (!cb.equals(filterCheckBox[EventLogMessageType.SELECT_ALL.ordinal()])
                                && !cb.isSelected()
                                && cb.isVisible())
                            {
                                // Set the flag to indicate a check box isn't selected and stop
                                // searching
                                isAllSelected = false;
                                break;
                            }
                        }

                        // Set the 'All' check box state based on the state of the other event
                        // filter check boxes
                        filterCheckBox[EventLogMessageType.SELECT_ALL.ordinal()].setSelected(isAllSelected);
                    }

                    // Set the table's row sorter based on whether or not any rows are visible
                    eventTable.setRowSorter(null);
                    eventTable.setTableSortable();

                    // Issue a table change event so the rows are filtered
                    eventTableModel.fireTableDataChanged();
                    eventTableModel.fireTableStructureChanged();
                }
            };

            // Create a panel for the event filter check boxes
            JPanel filterPanel = new JPanel();
            JLabel filterLabel = new JLabel("Event filter: ");
            filterLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            filterPanel.add(filterLabel);

            // Create the filter check box array based on the number of event types
            filterCheckBox = new JCheckBox[EventLogMessageType.values().length];
            int index = 0;

            // Step through each event type
            for (EventLogMessageType eventType : EventLogMessageType.values())
            {
                // Create a filter check box for this event type
                filterCheckBox[index] = createCheckBox(eventType, filterListener);
                filterPanel.add(filterCheckBox[index]);
                index++;
            }

            // Enable or disable the web server message filter check box based on if the web server
            // exists or if this isn't the session log
            setServerFilterEnable(ccddMain.getWebServer() != null || !isSessionLog);

            // Add the filter check box panel to the log & filter panel
            logAndFilterPnl.add(filterPanel, BorderLayout.PAGE_END);

            // Adjust the dialog's minimum width if the filter panel exceeds the default minimum
            adjustFrameMinimumWidth(filterPanel.getWidth());
        }

        // Create an outer log panel in which to put the log table and filter check box panel. The
        // border doesn't appear without this
        logPanel = new JPanel();
        logPanel.setLayout(new BoxLayout(logPanel, BoxLayout.X_AXIS));
        logPanel.add(logAndFilterPnl);
        logPanel.setBorder(BorderFactory.createEmptyBorder());

        // Initialize the CCDD logo graphic
        image = null;

        try
        {
            // Create the CCDD logo graphic
            image = ImageIO.read(getClass().getResource(CCDD_ICON));
        }
        catch (IOException ioe)
        {
            // Ignore the error if the logo cannot be created
        }
    }

    /**********************************************************************************************
     * Show or hide the web server message filter check box. The server check box is shown only
     * when the web server exists (even if the server is subsequently disabled)
     *
     * @param show
     *            true to show the check box; false to hide it
     *********************************************************************************************/
    protected void setServerFilterEnable(boolean show)
    {
        filterCheckBox[EventLogMessageType.SERVER_MSG.ordinal()].setVisible(show);
    }

    /**********************************************************************************************
     * Create a check box with label
     *
     * @param eventType
     *            EventLogmessageType event type
     *
     * @param filterListener
     *            ActionListener to call when the check box's selection status changes
     *
     * @return New check box with the specified characteristics
     *********************************************************************************************/
    private JCheckBox createCheckBox(EventLogMessageType eventType, ActionListener filterListener)
    {
        // Create a check box with label
        JCheckBox checkBox = new JCheckBox(eventType.getTypeName());
        checkBox.setHorizontalAlignment(SwingConstants.LEFT);
        checkBox.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        checkBox.setForeground(Color.decode(eventType.getTypeColor()));
        checkBox.setSelected(true);
        checkBox.addActionListener(filterListener);
        return checkBox;
    }

    /**********************************************************************************************
     * Set the specified filter check box state
     *
     * @param type
     *            Event log message type (e.g., COMMAND_MSG)
     *
     * @param isFiltered
     *            true to set enable display of the specified message type; false to hide messages
     *            of the specified type
     *********************************************************************************************/
    protected void setFilter(EventLogMessageType type, boolean isFiltered)
    {
        // Get the message type enumerator index to shorten subsequent calls
        int index = EventLogMessageType.valueOf(type.name()).ordinal();

        // Check if the 'All' events filter state is specified
        if (type == EventLogMessageType.SELECT_ALL)
        {
            // Set the 'All' check box state to opposite the intended state so that the step below
            // triggers the correct update
            filterCheckBox[index].setSelected(!isFiltered);
        }

        // Check if the current filter check box state differs from the desired state
        if (isFiltered != filterCheckBox[index].isSelected())
        {
            // Send an event to the filter check box
            filterCheckBox[index].doClick();
        }
    }

    /**********************************************************************************************
     * Check if the supplied message type should be displayed based on the filter check box
     * statuses
     *
     * @param type
     *            Event log message type (e.g., COMMAND_MSG)
     *
     * @return true if the message is selected to display
     *********************************************************************************************/
    private boolean isFilter(EventLogMessageType type)
    {
        boolean showStatus = false;

        // Step through the event message types
        for (EventLogMessageType eventType : EventLogMessageType.values())
        {
            // Check if the type number matches the event type and if this type of message is
            // selected for display
            if (type == eventType
                && filterCheckBox[EventLogMessageType.valueOf(eventType.name()).ordinal()].isSelected())
            {
                // Set the flag to indicate that this message type should be displayed
                showStatus = true;
                break;
            }
        }

        return showStatus;
    }

    /**********************************************************************************************
     * Get the event log panel
     *
     * @return Event log window panel
     *********************************************************************************************/
    protected JPanel getEventPanel()
    {
        return logPanel;
    }

    /**********************************************************************************************
     * Open the event log file. If this is the current session's event log then create the file; if
     * this is an existing log then open the user-selected file
     *
     * @return true if the log is opened
     *********************************************************************************************/
    private boolean openEventLogFile()
    {
        boolean isOpen = false;

        // Check if this the log for the current session
        if (isSessionLog)
        {
            try
            {
                // The session log is considered open even if the file cannot be opened
                isOpen = true;

                // Create the session log file using the date and time stamp as part of the name,
                // and the log file path if set by command line command
                logFile = new FileEnvVar((!ModifiablePathInfo.SESSION_LOG_FILE_PATH.getPath().isEmpty()
                                                                                                        ? ModifiablePathInfo.SESSION_LOG_FILE_PATH.getPath()
                                                                                                          + File.separator
                                                                                                        : "")
                                         + "CCDD-"
                                         + getDateTimeStamp("yyyyMMdd_HHmmss")
                                         + ".log");

                // Get the path to the log folder
                File logDirectory = logFile.getParentFile();

                // Check if the folder doesn't exist; if not then check if the folder is created
                if (!logDirectory.isDirectory() && logDirectory.mkdir())
                {
                    // Inform the user that the folder was created for the log file
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Folder '</b>"
                                                                                       + logDirectory.getAbsolutePath()
                                                                                       + "<b>' created for event log file",
                                                              "Create Log Folder",
                                                              JOptionPane.INFORMATION_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                // Attempt to create the log file and check if it can be written to
                if (logFile.createNewFile() && logFile.canWrite())
                {
                    // Create a writer to the log
                    logWriter = new PrintWriter(logFile);

                    // Indicate that the log was created
                    isLogWrite = true;
                }
                // File cannot be created or written to
                else
                {
                    // Indicate that the log wasn't created
                    isLogWrite = false;
                }
            }
            catch (Exception e)
            {
                // Indicate that the log wasn't created
                isLogWrite = false;
            }

            // Check if the log couldn't be created
            if (!isLogWrite)
            {
                // Inform the user that an error occurred creating the log file
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Cannot create event log file",
                                                          "Log Error",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
        // Open an existing log file
        else
        {
            // Allow the user to select the event log file path + name to read from
            FileEnvVar[] file = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                       ccddMain.getMainFrame(),
                                                                       null,
                                                                       null,
                                                                       new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.LOG.getDescription(),
                                                                                                                                  FileExtension.LOG.getExtensionName())},
                                                                       false,
                                                                       "Open Event Log",
                                                                       ccddMain.getProgPrefs().get(ModifiablePathInfo.READ_LOG_FILE_PATH.getPreferenceKey(), null),
                                                                       DialogOption.OPEN_OPTION);

            // Check if a file was chosen
            if (file != null && file[0] != null)
            {
                // Check if the file doesn't exist
                if (!file[0].exists())
                {
                    // Inform the user that the event log file cannot be located
                    new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                              "<html><b>Cannot locate event log file '</b>"
                                                                                       + file[0].getAbsolutePath()
                                                                                       + "<b>'",
                                                              "Log Error",
                                                              JOptionPane.ERROR_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
                // The event log file exists
                else
                {
                    // Set the event log status flag to false since this log won't be added to.
                    // Save the log file object and store the selected log in the preferences
                    // backing store
                    isOpen = true;
                    isLogWrite = false;
                    logFile = file[0];

                    // Store the log path path in the program preferences backing store
                    CcddFileIOHandler.storePath(ccddMain,
                                                logFile.getAbsolutePathWithEnvVars(),
                                                true,
                                                ModifiablePathInfo.READ_LOG_FILE_PATH);
                }
            }
        }

        return isOpen;
    }

    /**********************************************************************************************
     * Read an existing event log file
     *
     * @param targetRow
     *            row index a specific log entry in an existing log to display in a stand-alone
     *            table without message length constraints; null if not displaying a single log
     *            entry
     *********************************************************************************************/
    private void readEventLog(Long targetRow)
    {
        try
        {
            // Create a log reader
            BufferedReader logReader = new BufferedReader(new FileReader(logFile));

            // Read first line in file
            String line = logReader.readLine();

            // Log entry row index
            long row = 1;

            // Continue to read the file until EOF is reached or an error is detected
            while (line != null)
            {
                // Break the input line into its separate columns
                String[] parts = line.split("[|]", EventColumns.values().length - 1);

                // Check if no target time stamp is provided, or if one is that it matches this log
                // entry's time stamp
                if (targetRow == null || row == targetRow)
                {
                    // Add the new event log entry. Truncate the message length if needed, unless
                    // this is a single log entry viewer
                    eventLogList.add(new Object[] {row,
                                                   getServerLog(parts[EventColumns.SERVER.ordinal() - 1]),
                                                   parts[EventColumns.PROJECT.ordinal() - 1],
                                                   parts[EventColumns.USER.ordinal() - 1],
                                                   getDateTimeStampLog(parts[EventColumns.TIME.ordinal() - 1]),
                                                   getMessageType(parts[EventColumns.TYPE.ordinal() - 1]).getTypeMsg(),
                                                   (targetRow == null
                                                                      ? truncateLogMessage(parts[EventColumns.MESSAGE.ordinal() - 1])
                                                                      : parts[EventColumns.MESSAGE.ordinal() - 1])});

                    // Check if a target time stamp is provided (i.e., only a single log entry is
                    // loaded for this event log)
                    if (targetRow != null)
                    {
                        // Stop searching
                        break;
                    }
                }

                row++;

                // Read the next line in the file
                line = logReader.readLine();
            }

            // Close the log reader
            logReader.close();
        }
        catch (Exception e)
        {
            // Inform the user that an error occurred reading the log
            new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                      "<html><b>Cannot read event log file",
                                                      "Log Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**********************************************************************************************
     * Close the event log file
     *********************************************************************************************/
    protected void closeEventLogFile()
    {
        // Check if the log is open
        if (isLogWrite)
        {
            // Close the event log file
            logWriter.close();
        }
    }

    /**********************************************************************************************
     * Determine an event log message type by the type name
     *
     * @param typeName
     *            event log message type name (e.g., "Success")
     *
     * @return type Event log message type (e.g., COMMAND_MSG)
     *********************************************************************************************/
    private EventLogMessageType getMessageType(String typeName)
    {
        EventLogMessageType type = null;

        // Step through each event message type
        for (EventLogMessageType msgType : EventLogMessageType.values())
        {
            // Check if the message type name matches the specified name
            if (msgType.getTypeName().equals(typeName))
            {
                // Save the event message type and exit the loop
                type = msgType;
                break;
            }
        }

        return type;
    }

    /**********************************************************************************************
     * Append an event message to the current session's event log window and file
     *
     * @param type
     *            message type (e.g., COMMAND_MSG)
     *
     * @param logMessage
     *            new event's log message
     *********************************************************************************************/
    protected void logEvent(final EventLogMessageType type, String logMessage)
    {
        // Get the server, database, and user responsible for the event.
        final String server = dbControl.getServer();
        final String database = dbControl.isDatabaseConnected()
                                                                ? dbControl.getProjectName()
                                                                : (dbControl.isServerConnected()
                                                                                                 ? "*server*"
                                                                                                 : "*none*");
        final String user = dbControl.getUser();

        // Get the current date and time stamp
        final String timestamp = getDateTimeStamp("MM/dd/yyyy HH:mm:ss.SSS");

        // Remove any embedded line feed characters since these interfere with parsing when reading
        // the log files
        final String message = logMessage.replaceAll("\n", "");

        // Check if the log event call is made on the event dispatch thread
        if (SwingUtilities.isEventDispatchThread())
        {
            // Add the message to the event log
            addMessageToLog(server, database, user, type, timestamp, message);
        }
        // The log event call is made from a background thread
        else
        {
            // Create a runnable object to be executed
            SwingUtilities.invokeLater(new Runnable()
            {
                /**********************************************************************************
                 * Since the log addition involves a GUI update use invokeLater to execute the call
                 * on the event dispatch thread
                 *********************************************************************************/
                @Override
                public void run()
                {
                    // Add the message to the event log
                    addMessageToLog(server, database, user, type, timestamp, message);
                }
            });
        }
    }

    /**********************************************************************************************
     * Append a database failure message to the event log window and file and display a
     * corresponding error dialog
     *
     * @param parent
     *            window to center the dialog over; null if no dialog should be displayed
     *
     * @param logMessage
     *            new event's log message
     *
     * @param dialogMessage
     *            error dialog message
     *********************************************************************************************/
    protected void logFailEvent(Component parent, String logMessage, String dialogMessage)
    {
        logFailEvent(parent, "Database Error", logMessage, dialogMessage);
    }

    /**********************************************************************************************
     * Append a failure message to the event log window and file and display a corresponding error
     * dialog
     *
     * @param parent
     *            window to center the dialog over; null if no dialog should be displayed
     *
     * @param dialogTitle
     *            error dialog title
     *
     * @param logMessage
     *            new event's log message
     *
     * @param dialogMessage
     *            error dialog message
     *********************************************************************************************/
    protected void logFailEvent(Component parent,
                                String dialogTitle,
                                String logMessage,
                                String dialogMessage)
    {
        // Append the failure message to the event log
        logEvent(FAIL_MSG, logMessage);

        // Display an error dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  (ccddMain.isGUIHidden()
                                                                          ? CcddUtilities.removeHTMLTags(logMessage)
                                                                          : dialogMessage),
                                                  dialogTitle,
                                                  JOptionPane.ERROR_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
     * Add a new log entry to the event log table
     *
     * @param server
     *            server host and port
     *
     * @param database
     *            database connection
     *
     * @param user
     *            user name
     *
     * @param type
     *            message type (e.g., COMMAND_MSG)
     *
     * @param timestamp
     *            date and time when event occurred
     *
     * @param logMessage
     *            new event's log message
     *********************************************************************************************/
    private void addMessageToLog(String server,
                                 String database,
                                 String user,
                                 EventLogMessageType type,
                                 String timestamp,
                                 String logMessage)
    {
        // Set the table row sorter. This is required so that command line options to filter the
        // events are handled properly
        eventTable.setTableSortable();

        // Insert the event at the end of the event log table
        eventTable.insertRow(false,
                             TableInsertionPoint.END,
                             new Object[] {indexNum,
                                           getServerLog(server),
                                           database,
                                           user,
                                           getDateTimeStampLog(timestamp),
                                           type.getTypeMsg(),
                                           truncateLogMessage(logMessage)});

        // Update the log entry counter
        indexNum++;

        // Check if the event log file exists
        if (isLogWrite)
        {
            try
            {
                // Use a StringBuilder to concatenate the log message in case the message is
                // lengthy (StringBuilder is much faster than string concatenation using '+')
                StringBuilder logEntry = new StringBuilder(server
                                                           + "|"
                                                           + database
                                                           + "|"
                                                           + user
                                                           + "|"
                                                           + timestamp
                                                           + "|"
                                                           + type.getTypeName()
                                                           + "|");
                logEntry.append(logMessage);

                // Write the message to the event log file
                logWriter.println(logEntry.toString());
                logWriter.flush();
            }
            catch (Exception e)
            {
                // Inform the user that an error occurred writing to the log
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Cannot write to event log",
                                                          "Log Error",
                                                          JOptionPane.WARNING_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }
        }
    }

    /**********************************************************************************************
     * Get the current system date and time stamp
     *
     * @param format
     *            date and time stamp format
     *
     * @return Date and time string in the format specified
     *********************************************************************************************/
    private String getDateTimeStamp(String format)
    {
        return new SimpleDateFormat(format).format(Calendar.getInstance().getTime());
    }

    /**********************************************************************************************
     * Format the date and time string provided for display in the event log table
     *
     * @param timestamp
     *            date and time string in the format [month/day/year][ ] [hours/minutes/seconds]
     *
     * @return The date and time string formatted for HTML, centered, and with the month/day/year
     *         and hours/minutes/seconds on separate lines
     *********************************************************************************************/
    private String getDateTimeStampLog(String timestamp)
    {
        return "<html><center>" + timestamp.replaceFirst(" ", "<br>");
    }

    /**********************************************************************************************
     * Format the server string provided for display in the event log table
     *
     * @param server
     *            server string in the format [server name][:][port number]
     *
     * @return The server string formatted for HTML, centered, and with the server name and port
     *         number on separate lines
     *********************************************************************************************/
    private String getServerLog(String server)
    {
        return "<html><center>" + server.replaceFirst(":", "<br>");
    }

    /**********************************************************************************************
     * Output the event log to a printer
     *********************************************************************************************/
    protected void printEventLog()
    {
        // Output the log to the printer
        eventTable.printTable("EventLog: " + logFile.getName(),
                              null,
                              CcddEventLogDialog.this,
                              PageFormat.LANDSCAPE);
    }

    /**********************************************************************************************
     * Create a mouse listener for opening single, non-length constrained log messages in a
     * separate table
     *********************************************************************************************/
    private void setLogMessageListener()
    {
        // Add a mouse listener to the table to handle mouse clicks on the message column
        eventTable.addMouseListener(new MouseAdapter()
        {
            /**********************************************************************************
             * Handle mouse press events
             *********************************************************************************/
            @Override
            public void mousePressed(MouseEvent me)
            {
                // Check if the right mouse button is double clicked
                if (me.getClickCount() == 2
                    && SwingUtilities.isRightMouseButton(me))
                {
                    // Get the table row that was selected
                    long row = eventTable.convertRowIndexToModel(eventTable.rowAtPoint(me.getPoint()));

                    // Check if the row is valid
                    if (row != -1)
                    {
                        // Open a new event log displaying the selected log entry, without
                        // constraining the message length
                        new CcddEventLogDialog(ccddMain, logFile, row + 1, false);
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Truncate the log message if its length exceeds the maximum allowed
     *
     * @param logMessage
     *            log message
     *
     * @return Log message, truncated to the maximum length, and with an ellipsis and number of
     *         truncated characters appended, if its length exceeds the maximum allowed
     *********************************************************************************************/
    private String truncateLogMessage(String logMessage)
    {
        // Check if the message length exceeds the maximum allowed
        if (logMessage.length() > ModifiableSizeInfo.MAX_LOG_MESSAGE_LENGTH.getSize())
        {
            // Calculate the number of characters to be truncated
            int numTruncated = logMessage.length()
                               - ModifiableSizeInfo.MAX_LOG_MESSAGE_LENGTH.getSize();

            // Truncate the log message to the maximum length and append an ellipsis as a
            // truncation indicator
            logMessage = logMessage.substring(0, ModifiableSizeInfo.MAX_LOG_MESSAGE_LENGTH.getSize())
                         + " ... ("
                         + numTruncated
                         + ")";
        }

        return logMessage;
    }
}
