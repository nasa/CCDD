/**
 * CFS Command and Data Dictionary web server.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_WEB_SERVER_PORT;
import static CCDD.CcddConstants.WEB_SERVER_PORT;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

import CCDD.CcddConstants.EventLogMessageType;

/**************************************************************************************************
 * CFS Command and Data Dictionary web server class
 *************************************************************************************************/
public class CcddWebServer
{
    // Class reference
    private final CcddMain ccddMain;
    private final CcddEventLogDialog eventLog;
    private final CcddDbControlHandler dbControl;
    private CcddWebDataAccessHandler accessHandler;

    // Web server
    private Server server;

    // Storage for a user+password combination authenticated by the PostgreSQL server for the
    // current project database
    private String validUser;
    private String validPassword;

    /**********************************************************************************************
     * Web server class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddWebServer(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        this.eventLog = ccddMain.getSessionEventLog();
        this.dbControl = ccddMain.getDbControlHandler();

        validUser = null;
        validPassword = null;

        // Set the web server log so that it outputs to the event log file
        org.eclipse.jetty.util.log.Log.setLog(new ServerLogging());

        // Create the server
        createServer();
    }

    /**********************************************************************************************
     * Get the reference to the web data access handler class
     *
     * @return Reference to the web data access handler class
     *********************************************************************************************/
    protected CcddWebDataAccessHandler getWebAccessHandler()
    {
        return accessHandler;
    }

    /**********************************************************************************************
     * Create the web server
     *********************************************************************************************/
    private void createServer()
    {
        try
        {
            // Create the web server using the currently specified port
            server = new Server(Integer.valueOf(ccddMain.getProgPrefs().get(WEB_SERVER_PORT,
                                                                            DEFAULT_WEB_SERVER_PORT)));

            // Stop the web server when the application exits
            server.setStopAtShutdown(true);

            // Create the login service
            HashLoginService loginService = new HashLoginService("CCDDRealm")
            {
                /**********************************************************************************
                 * Override the login method so that the supplied user name and password can be
                 * authenticated by the PostgreSQL server
                 *********************************************************************************/
                @Override
                public UserIdentity login(String user, Object password)
                {
                    UserIdentity identity = null;

                    try
                    {
                        // Convert the password object to a string
                        String passwordS = password.toString();

                        // Check if the user+password hasn't been set of if either has changed.
                        // This prevents contacting the PostgreSQL server with each request after
                        // the user+password is authenticated initially
                        if (validUser == null
                            || !validUser.equals(user)
                            || !validPassword.equals(passwordS))
                        {
                            // Attempt to connect to the database using the supplied user and
                            // password
                            DriverManager.getConnection(dbControl.getDatabaseURL(dbControl.getDatabaseName()),
                                                        user,
                                                        passwordS);

                            // Store the authenticated user and password for future login requests
                            validUser = user;
                            validPassword = passwordS;
                        }

                        // User+password combination is valid, so set the user identity using the
                        // generic login credentials
                        identity = super.login("valid", "valid");
                    }
                    catch (SQLException se)
                    {
                        validUser = null;
                        validPassword = null;

                        // The supplied user+password combination is not valid; set the user
                        // identity using invalid credentials so that the request is rejected
                        identity = super.login("invalid", "invalid");
                    }

                    return identity;
                }
            };

            // Create the user credentials that are used by the login service if the user's
            // PostgreSQL credentials are authenticated
            loginService.putUser("valid",
                                 Credential.getCredential("valid"),
                                 new String[] {"user"});
            server.addBean(loginService);

            // Set the security handler that secures content behind a particular portion of a URL
            // space
            ConstraintSecurityHandler security = new ConstraintSecurityHandler();
            server.setHandler(security);

            // Set a constraint that requires authentication and in addition that an authenticated
            // user be a member of a given set of roles for authorization purposes
            Constraint constraint = new Constraint();
            constraint.setName("auth");
            constraint.setAuthenticate(true);
            constraint.setRoles(new String[] {"user"});

            // Bind the URL pattern with the previously created constraint.
            ConstraintMapping mapping = new ConstraintMapping();
            mapping.setPathSpec("/*");
            mapping.setConstraint(constraint);

            // Apply the constraint mapping to the handler, set an authenticator to check the
            // user's credentials, and set the login service which contains the single valid user
            security.setConstraintMappings(Collections.singletonList(mapping));
            security.setAuthenticator(new BasicAuthenticator());
            security.setLoginService(loginService);

            // Create the web server request access handler
            accessHandler = new CcddWebDataAccessHandler(ccddMain);
            security.setHandler(accessHandler);
        }
        catch (Exception e)
        {
            // Inform the user that creating the web server failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Web Server Error",
                                  "Cannot create web server; cause '" + e.getMessage() + "'",
                                  "<html><b>Cannot create web server");
        }
    }

    /**********************************************************************************************
     * Start the server
     *********************************************************************************************/
    protected void startServer()
    {
        try
        {
            // Start the server
            server.start();

            // Inform the user that the web server started
            eventLog.logEvent(EventLogMessageType.SERVER_MSG,
                              "Web server started; listening on port "
                                                              + ((ServerConnector) server.getConnectors()[0]).getLocalPort());
        }
        catch (Exception e)
        {
            // Inform the user that starting the web server failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Web Server Error",
                                  "Web server failed to start; cause '" + e.getMessage() + "'",
                                  "<html><b>Web server failed to start");
        }
    }

    /**********************************************************************************************
     * Stop the server
     *********************************************************************************************/
    protected void stopServer()
    {
        try
        {
            // Stop the server
            server.stop();

            // Inform the user that the web server stopped
            eventLog.logEvent(EventLogMessageType.SERVER_MSG, "Web server stopped");
        }
        catch (Exception e)
        {
            // Inform the user that stopping the web server failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Web Server Error",
                                  "Web server failed to stop; cause '" + e.getMessage() + "'",
                                  "<html><b>Web server failed to stop");
        }
    }

    /**********************************************************************************************
     * Server message logging class. Formats the message and outputs it to the event log file
     *********************************************************************************************/
    private class ServerLogging implements Logger
    {
        private boolean isDebug;

        /******************************************************************************************
         * Server message logging class constructor
         *****************************************************************************************/
        ServerLogging()
        {
            // Set the flag to indicate that debugging is initially disabled
            isDebug = false;
        }

        /******************************************************************************************
         * Create the log message from the input information and output the formatted message to
         * the log file
         *
         * @param type
         *            message type
         *
         * @param msg
         *            message contents; may be null
         *
         * @param args
         *            message arguments; may be null
         *****************************************************************************************/
        private void logOutput(String type, String msg, Object... args)
        {
            // Build the server event log message
            String message = msg != null
                                         ? msg.replaceFirst("\\{\\}$", "")
                                         : "";

            // Step through each argument
            for (Object arg : args)
            {
                // Check if the argument isn't a null
                if (arg != null)
                {
                    // Append the argument to the message text
                    message += " " + arg + ";";
                }
            }

            // Remove the trailing semi-colon, if present
            message = CcddUtilities.removeTrailer(message, ";");

            // Check if there is a message to output
            if (!message.isEmpty())
            {
                // Output the server message to the event log
                eventLog.logEvent(EventLogMessageType.SERVER_MSG, type + ": " + message);
            }
        }

        /******************************************************************************************
         * Get the logger reference
         *****************************************************************************************/
        @Override
        public Logger getLogger(String name)
        {
            return this;
        }

        /******************************************************************************************
         * Get the server logger name
         *****************************************************************************************/
        @Override
        public String getName()
        {
            return "CCDD Web Server Log";
        }

        /******************************************************************************************
         * Output a warning message
         *****************************************************************************************/
        @Override
        public void warn(String msg, Object... args)
        {
            logOutput("WARN", msg, args);
        }

        /******************************************************************************************
         * Output a warning message
         *****************************************************************************************/
        @Override
        public void warn(Throwable thrown)
        {
            logOutput("WARN", thrown.getMessage(), thrown.getCause());
        }

        /******************************************************************************************
         * Output a warning message
         *****************************************************************************************/
        @Override
        public void warn(String msg, Throwable thrown)
        {
            logOutput("WARN", msg, thrown.getMessage());
        }

        /******************************************************************************************
         * Output an information message
         *****************************************************************************************/
        @Override
        public void info(String msg, Object... args)
        {
            logOutput("INFO", msg, args);
        }

        /******************************************************************************************
         * Output an information message
         *****************************************************************************************/
        @Override
        public void info(Throwable thrown)
        {
            logOutput("INFO", thrown.getMessage(), thrown.getCause());
        }

        /******************************************************************************************
         * Output an information message
         *****************************************************************************************/
        @Override
        public void info(String msg, Throwable thrown)
        {
            logOutput("INFO", msg, thrown.getMessage());
        }

        /******************************************************************************************
         * Check if debugging is enabled
         *****************************************************************************************/
        @Override
        public boolean isDebugEnabled()
        {
            return isDebug;
        }

        /******************************************************************************************
         * Set debugging
         *****************************************************************************************/
        @Override
        public void setDebugEnabled(boolean enabled)
        {
            isDebug = enabled;
        }

        /******************************************************************************************
         * Output a debugging message
         *****************************************************************************************/
        @Override
        public void debug(String msg, Object... args)
        {
            // Check if debugging is enabled
            if (isDebug)
            {
                logOutput("DEBUG", msg, args);
            }
        }

        /******************************************************************************************
         * Output a debugging message
         *****************************************************************************************/
        @Override
        public void debug(Throwable thrown)
        {
            // Check if debugging is enabled
            if (isDebug)
            {
                logOutput("DEBUG", thrown.getMessage(), thrown.getCause());
            }
        }

        /******************************************************************************************
         * Output a debugging message
         *****************************************************************************************/
        @Override
        public void debug(String msg, Throwable thrown)
        {
            // Check if debugging is enabled
            if (isDebug)
            {
                logOutput("DEBUG", msg, thrown.getMessage());
            }
        }

        /******************************************************************************************
         * Output a debugging message
         *****************************************************************************************/
        @Override
        public void debug(String arg0, long arg1)
        {
            // Check if debugging is enabled
            if (isDebug)
            {
                logOutput("DEBUG", arg0, arg1);
            }
        }

        /******************************************************************************************
         * Output an ignore message
         *****************************************************************************************/
        @Override
        public void ignore(Throwable thrown)
        {
            logOutput("IGNORE", thrown.getMessage(), thrown.getCause());
        }
    }
}
