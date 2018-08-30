/**
 * CFS Command and Data Dictionary background command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.awt.Cursor;

import javax.swing.SwingWorker;

/**************************************************************************************************
 * CFS Command and Data Dictionary background command class
 *************************************************************************************************/
public class CcddBackgroundCommand
{
    private static Component glassPane;
    private static Component dlgGlassPane;

    /**********************************************************************************************
     * Class for executing a command in the background. Consists of two user-provided methods: A
     * command execution method that is run in the background (this method must be supplied), and a
     * command completed method that is run after the execute method ends (this method can be
     * omitted if no special steps are required after the command completes)
     *********************************************************************************************/
    abstract protected static class BackgroundCommand
    {
        /******************************************************************************************
         * Steps to perform to execute a command
         *****************************************************************************************/
        abstract protected void execute();

        /******************************************************************************************
         * Placeholder for steps to perform following execution of a command
         *****************************************************************************************/
        protected void complete()
        {
        };
    }

    /**********************************************************************************************
     * Execute a command in the background. The mouse cursor for the main window is set to the
     * 'wait' cursor and the main window's menu items are disabled for the duration of the command
     * execution. Once complete, any clean-up steps are performed, the cursor is restored, and the
     * menu items are reenabled
     *
     * @param ccddMain
     *            main class reference
     *
     * @param backCommand
     *            background command
     *
     * @return SwingWorker reference for this thread
     *********************************************************************************************/
    protected static SwingWorker<?, ?> executeInBackground(final CcddMain ccddMain,
                                                           final BackgroundCommand backCommand)
    {
        return executeInBackground(ccddMain, null, backCommand);
    }

    /**********************************************************************************************
     * Execute a command in the background. The mouse cursor for the specified component is set to
     * the 'wait' cursor and the main window's menu items are disabled for the duration of the
     * command execution. Once complete, any clean-up steps are performed, the cursor is restored,
     * and the menu items are reenabled
     *
     * @param ccddMain
     *            main class reference
     *
     * @param dialog
     *            reference to the dialog responsible for this operation; null if not applicable
     *
     * @param backCommand
     *            background command
     *
     * @return SwingWorker reference for this thread
     *********************************************************************************************/
    protected static SwingWorker<?, ?> executeInBackground(final CcddMain ccddMain,
                                                           final Component dialog,
                                                           final BackgroundCommand backCommand)
    {
        // Check if the GUI is visible
        if (!ccddMain.isGUIHidden())
        {
            // Deactivate the main application window controls
            ccddMain.setGUIActivated(false);

            // Get the main window's glass pane
            glassPane = ccddMain.getMainFrame().getGlassPane();

            // Since this could take a while, show the "wait" mouse pointer over the main window to
            // alert the user. The pointer is restored to the default when the command completes
            glassPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            glassPane.setVisible(true);

            // Check if this is executed from a dialog or frame
            if (dialog != null)
            {
                // Check if this is a dialog
                if (dialog instanceof CcddDialogHandler)
                {
                    // Get the dialog's glass pane and disable the dialog's controls
                    dlgGlassPane = ((CcddDialogHandler) dialog).getGlassPane();
                    ((CcddDialogHandler) dialog).setControlsEnabled(false);
                }
                // Check if this is a frame
                else if (dialog instanceof CcddFrameHandler)
                {
                    // Get the frame's glass pane and disable the frame's controls
                    dlgGlassPane = ((CcddFrameHandler) dialog).getGlassPane();
                    ((CcddFrameHandler) dialog).setControlsEnabled(false);
                }
                // Not a dialog or frame
                else
                {
                    dlgGlassPane = null;
                }

                // Check if this is a dialog or frame
                if (dlgGlassPane != null)
                {
                    // Since this could take a while, show the "wait" mouse pointer over the dialog
                    // to alert the user. The pointer is restored to the default when the command
                    // completes
                    dlgGlassPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    dlgGlassPane.setVisible(true);
                }
            }
        }

        // Create a SwingWorker in which to perform the command on a separate thread
        SwingWorker<?, ?> backCommandWorker = new SwingWorker<Void, Object>()
        {
            /**************************************************************************************
             * Execute command steps. These are performed on a newly spawned background thread
             *************************************************************************************/
            @Override
            protected Void doInBackground()
            {
                // Execute the command
                backCommand.execute();

                return null;
            }

            /**************************************************************************************
             * Command completed steps. These are performed on the Event Dispatch Thread
             *************************************************************************************/
            @Override
            protected void done()
            {
                // Check if the GUI is visible
                if (!ccddMain.isGUIHidden())
                {
                    // Reactivate the main main application window controls
                    ccddMain.setGUIActivated(true);

                    // Check if this is executed from a dialog
                    if (dlgGlassPane != null)
                    {
                        // Check if this is a dialog
                        if (dialog instanceof CcddDialogHandler)
                        {
                            // Enable the dialog's controls
                            ((CcddDialogHandler) dialog).setControlsEnabled(true);
                        }

                        // Check if this is a frame
                        if (dialog instanceof CcddFrameHandler)
                        {
                            // Enable the frame's controls
                            ((CcddFrameHandler) dialog).setControlsEnabled(true);
                        }

                        // Restore the dialog's default mouse pointer and hide the glass pane
                        dlgGlassPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        dlgGlassPane.setVisible(false);
                    }

                    // Restore the main window's default mouse pointer
                    glassPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

                    // Hide the glass pane. If this isn't done the resize mouse pointer doesn't
                    // appear in the main window's session event log header
                    glassPane.setVisible(false);
                }

                // Perform any special command termination steps
                backCommand.complete();
            }
        };

        // Initiate the command. The application does not wait for this thread to complete
        backCommandWorker.execute();

        return backCommandWorker;
    }
}
