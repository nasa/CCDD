/**
 * CFS Command and Data Dictionary scheduler database I/O handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.TLM_SCH_SEPARATOR;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClassesDataTable.ApplicationData;
import CCDD.CcddClassesDataTable.DataStream;
import CCDD.CcddClassesDataTable.Message;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.Variable;
import CCDD.CcddClassesDataTable.VariableGenerator;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.AppSchedulerColumn;
import CCDD.CcddConstants.InternalTable.TlmSchedulerColumn;
import CCDD.CcddConstants.SchedulerType;

/**************************************************************************************************
 * CFS Command and Data Dictionary scheduler database I/O handler class
 *************************************************************************************************/
public class CcddSchedulerDbIOHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDialogHandler dialog;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddRateParameterHandler rateHandler;
    private final CcddApplicationParameterHandler appHandler;

    // Scheduler option for the database handler
    private final SchedulerType option;

    // List of data streams
    private final List<DataStream> dataStreams;

    /**********************************************************************************************
     * Scheduler database I/O handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param option
     *            scheduler option for the database handler
     *
     * @param dialog
     *            reference to the scheduler dialog
     *********************************************************************************************/
    CcddSchedulerDbIOHandler(CcddMain ccddMain, SchedulerType option, CcddDialogHandler dialog)
    {
        this.ccddMain = ccddMain;
        this.option = option;
        this.dialog = dialog;
        dbTable = ccddMain.getDbTableCommandHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        appHandler = ccddMain.getApplicationParameterHandler();

        dataStreams = new ArrayList<DataStream>();
    }

    /**********************************************************************************************
     * Format and store the current messages into the current project database
     *
     * @param streams
     *            list of the current data streams
     *********************************************************************************************/
    protected void storeData(List<DataStream> streams)
    {
        // Check if this is the telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            // Store the telemetry scheduler information in the project database
            dbTable.storeInformationTableInBackground(InternalTable.TLM_SCHEDULER,
                                                      getTelemetryData(streams),
                                                      null,
                                                      dialog);
        }
        // Check if this is the application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            // Store the application scheduler information in the project database
            dbTable.storeInformationTableInBackground(InternalTable.APP_SCHEDULER,
                                                      getApplicationData(streams.get(0).getMessages()),
                                                      null,
                                                      dialog);
        }
    }

    /**********************************************************************************************
     * Load the stored data from the database
     *********************************************************************************************/
    protected void loadStoredData()
    {
        // Check if this is the telemetry scheduler
        if (option == SchedulerType.TELEMETRY_SCHEDULER)
        {
            // Load the telemetry scheduler information from the project database
            loadTelemetryData();
        }
        // Check if this is the application scheduler
        else if (option == SchedulerType.APPLICATION_SCHEDULER)
        {
            // Load the application scheduler information from the project database
            loadApplicationData();
        }
    }

    /**********************************************************************************************
     * Get the list of stored messages for the specified data stream
     *
     * @param streamIndex
     *            data stream index
     *
     * @return List of current messages for the specified data stream
     *********************************************************************************************/
    protected List<Message> getStoredData(int streamIndex)
    {
        List<Message> messages = new ArrayList<Message>();

        // Check if the index is valid
        if (streamIndex != -1 && streamIndex < dataStreams.size())
        {
            // Get the list of messages for the specified data stream
            messages = dataStreams.get(streamIndex).getMessages();
        }

        return messages;
    }

    /**********************************************************************************************
     * Get the list of variables for the specified data stream
     *
     * @param streamIndex
     *            data stream index
     *
     * @return List of variables for the specified data stream
     **********************************************************************************************/
    protected List<Variable> getVariableList(int streamIndex)
    {
        List<Variable> variables = new ArrayList<Variable>();

        // Check if the index is valid
        if (streamIndex < dataStreams.size())
        {
            // Get the list of variables for the specified data stream
            variables = dataStreams.get(streamIndex).getVariableList();
        }

        return variables;
    }

    /**********************************************************************************************
     * Get the list of the message definitions for all data streams. Each list item is a string
     * array containing the rate name, message name, message ID, and a space-separated list of
     * variables in that message
     *
     * @param streams
     *            list of data streams
     *
     * @return List of message definitions
     *********************************************************************************************/
    private List<String[]> getTelemetryData(List<DataStream> streams)
    {
        List<String[]> currentMsg = new ArrayList<String[]>();

        // Step through each data stream
        for (DataStream stream : streams)
        {
            // Step through each message
            for (Message message : stream.getMessages())
            {
                // Add the definitions for the message's variables
                currentMsg.addAll(addVariablesToMessage(stream, message));

                // Step through each sub-message
                for (Message subMessage : message.getSubMessages())
                {
                    // Add the definitions for the sub-message's variables
                    currentMsg.addAll(addVariablesToMessage(stream, subMessage));
                }
            }
        }

        return currentMsg;
    }

    /**********************************************************************************************
     * Get the list of the message definitions for the specified (sub-)message. Each list item is a
     * string array containing the rate name, message name, message ID, and a space-separated list
     * of variables in that message
     *
     * @param stream
     *            data stream
     *
     * @param message
     *            (sub-)message reference
     *
     * @return List of the specified message's definitions
     *********************************************************************************************/
    private List<String[]> addVariablesToMessage(DataStream stream, Message message)
    {
        List<String[]> messageList = new ArrayList<String[]>();

        // Check if the message has no variables assigned to it
        if (message.getVariables().isEmpty())
        {
            // Create a new array for the row
            String[] msg = new String[TlmSchedulerColumn.values().length];

            // Add the data stream, sub-message name, message ID, and a blank as a placeholder for
            // the rate and variable name
            msg[TlmSchedulerColumn.RATE_NAME.ordinal()] = stream.getRateName();
            msg[TlmSchedulerColumn.MESSAGE_NAME.ordinal()] = message.getName();
            msg[TlmSchedulerColumn.MESSAGE_ID.ordinal()] = message.getID();
            msg[TlmSchedulerColumn.MEMBER.ordinal()] = "";

            // Add the array to the list of arrays
            messageList.add(msg);
        }
        // The message has a variable assigned
        else
        {
            // Step through each variable in the message
            for (Variable var : message.getVariables())
            {
                // Create a new array for the row
                String[] msg = new String[TlmSchedulerColumn.values().length];

                // Add the data stream, message name, message ID, and the rate and variable name
                msg[TlmSchedulerColumn.RATE_NAME.ordinal()] = stream.getRateName();
                msg[TlmSchedulerColumn.MESSAGE_NAME.ordinal()] = message.getName();
                msg[TlmSchedulerColumn.MESSAGE_ID.ordinal()] = message.getID();
                msg[TlmSchedulerColumn.MEMBER.ordinal()] = var.getRate()
                                                           + TLM_SCH_SEPARATOR
                                                           + var.getFullName();

                // Add the array to the list of arrays
                messageList.add(msg);
            }
        }

        return messageList;
    }

    /**********************************************************************************************
     * Load the stored data from the project database and initialize the telemetry table
     *********************************************************************************************/
    private void loadTelemetryData()
    {
        // Load the data from the database
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.TLM_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        // Check if there is data stored
        if (!storedData.isEmpty())
        {
            List<Message> msgList = null;
            List<Variable> varList;

            // Get the maximum messages per second in floating point format
            float msgsPerSec = Float.valueOf(rateHandler.getMaxMsgsPerSecond());

            // Step through each row in from the table
            for (String[] data : storedData)
            {
                RateInformation info = null;
                msgList = null;
                varList = null;

                // Get the rate column name, message name, message ID, and members
                String rateName = data[TlmSchedulerColumn.RATE_NAME.ordinal()];
                String messageName = data[TlmSchedulerColumn.MESSAGE_NAME.ordinal()];
                String messageID = data[TlmSchedulerColumn.MESSAGE_ID.ordinal()];
                String member = data[TlmSchedulerColumn.MEMBER.ordinal()];

                // Step through the existing data streams
                for (DataStream dataStream : dataStreams)
                {
                    // Check if the data stream already exists
                    if (rateName.equals(dataStream.getRateName()))
                    {
                        // Get the rate information for the data stream
                        info = rateHandler.getRateInformationByRateName(dataStream.getRateName());

                        // Get the messages for this the data stream
                        msgList = dataStream.getMessages();

                        // Get the variables for this the data stream
                        varList = dataStream.getVariableList();

                        break;
                    }
                }

                // Check if the message list is still null, indicating the data stream does not
                // exist
                if (msgList == null)
                {
                    // Get the rate information for this rate column
                    info = rateHandler.getRateInformationByRateName(rateName);

                    // Check if the rate exists
                    if (info != null)
                    {
                        // Create a new data stream for the data
                        DataStream stream = new DataStream(rateName);

                        // Add the stream to the existing list
                        dataStreams.add(stream);

                        // Get a reference to the created data stream message list
                        msgList = stream.getMessages();

                        // Get a reference to the created data stream variable list
                        varList = stream.getVariableList();
                    }
                }

                // Check if the rate exists
                if (info != null)
                {
                    int subIndex = -1;
                    Message message = null;

                    // Separate the message's name and the sub-index, if any
                    String[] nameAndIndex = messageName.split("\\.");

                    // Calculate the period (= total messages / total messages per second)
                    float period = Float.valueOf(info.getMaxMsgsPerCycle())
                                   / Float.valueOf(msgsPerSec);

                    // Step through the created messages
                    for (Message msg : msgList)
                    {
                        // Check if the message has already been created
                        if (msg.getName().equals(nameAndIndex[0]))
                        {
                            // Store the message object
                            message = msg;

                            // Check if this is a sub-message definition
                            if (nameAndIndex.length == 2)
                            {
                                // Step through all the message's sub-messages
                                for (Message subMessage : message.getSubMessages())
                                {
                                    // Check if the sub-message already exists
                                    if (subMessage.getName().equals(messageName))
                                    {
                                        // Get the sub-message's index and assign the sub-message's
                                        // ID
                                        subIndex = Integer.valueOf(nameAndIndex[1]);
                                        message.getSubMessage(subIndex).setID(messageID);
                                    }
                                }

                                // Check if no sub-index was found
                                if (subIndex == -1)
                                {
                                    // Get the sub-index from the message name
                                    subIndex = Integer.valueOf(nameAndIndex[1]);

                                    // Create a new sub-message and assign the sub-message's ID
                                    message.addNewSubMessage(messageID);
                                }
                            }

                            break;
                        }
                    }

                    // Check if no message object was found
                    if (message == null)
                    {
                        // Create a new parent message
                        message = new Message(nameAndIndex[0],
                                              messageID,
                                              info.getMaxBytesPerSec()
                                                         / info.getMaxMsgsPerCycle());

                        subIndex = 0;

                        // Add the message to the existing message list
                        msgList.add(message);
                    }

                    // Check if the message has a member
                    if (!member.isEmpty())
                    {
                        Variable variable = null;

                        // Split the member column to remove the rate and extract the variable name
                        String varName = member.split("\\" + TLM_SCH_SEPARATOR, 2)[1];

                        // Step through the variables
                        for (Variable var : varList)
                        {
                            // Check if the variable has already been created
                            if (var.getFullName().equals(varName))
                            {
                                // Store the variable and stop searching
                                variable = var;
                                break;
                            }
                        }

                        // Check if the variable doesn't already exist
                        if (variable == null)
                        {
                            // Create a new variable
                            variable = VariableGenerator.generateTelemetryData(member);

                            // Add the variable to the existing variable list
                            varList.add(variable);
                        }

                        // Check if the rate is a sub-rate
                        if (variable.getRate() < (1 / period))
                        {
                            // Assign the variable to the sub-message and store the message index
                            message.getSubMessage(subIndex).addVariable(variable);
                            variable.addMessageIndex(subIndex);
                        }
                        // The rate isn't a sub-rate
                        else
                        {
                            // Check if the variable has not already been assigned to the message
                            if (!message.getAllVariables().contains(variable))
                            {
                                // Add the variable to the general message
                                message.addVariable(variable);
                            }

                            // Store the message index
                            variable.addMessageIndex(msgList.indexOf(message));
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Get the list of the current messages. Each list item is a string arrays containing the
     * message name and a space-separated list of applications in that message
     *
     * @param messages
     *            list of messages
     *
     * @return List of current messages
     *********************************************************************************************/
    private List<String[]> getApplicationData(List<Message> messages)
    {
        List<String[]> currentMsg = new ArrayList<String[]>();

        // Step through each message
        for (Message message : messages)
        {
            // Check if the time slot has no applications assigned
            if (message.getVariables().isEmpty())
            {
                // Create a placeholder for the application information
                currentMsg.add(new String[] {message.getName(), ""});
            }
            // The time slot has applications assigned
            else
            {
                // Step through each application in the message
                for (Variable variable : message.getVariables())
                {
                    ApplicationData appData = (ApplicationData) variable;

                    // Create new array for the row
                    String[] app = new String[2];

                    // Store the message name, and application name, rate, run time, priority,
                    // wake-up message name & ID, send rate, housekeeping wake-up message name &
                    // ID, and schedule group name
                    app[AppSchedulerColumn.TIME_SLOT.ordinal()] = message.getName();
                    app[AppSchedulerColumn.APP_INFO.ordinal()] = appData.getFullName() + ","
                                                                 + appData.getRate() + ","
                                                                 + appData.getSize() + ","
                                                                 + appData.getPriority() + ","
                                                                 + appData.getMessageRate() + ","
                                                                 + appData.getWakeUpMessage() + ","
                                                                 + appData.getHkSendRate() + ","
                                                                 + appData.getHkWakeUpMessage() + ","
                                                                 + appData.getSchGroup();

                    // Add the application to the list
                    currentMsg.add(app);
                }
            }
        }

        return currentMsg;
    }

    /**********************************************************************************************
     * Get the stored data from the project database and initialize the application table
     *********************************************************************************************/
    private void loadApplicationData()
    {
        List<Message> messages = new ArrayList<Message>();
        List<Variable> varList = new ArrayList<Variable>();

        // Load the application scheduler table
        List<String[]> storedData = dbTable.retrieveInformationTable(InternalTable.APP_SCHEDULER,
                                                                     false,
                                                                     ccddMain.getMainFrame());

        // Check if any stored data exists
        if (!storedData.isEmpty())
        {
            // Calculate the message's time usage
            int time = 1000 / appHandler.getMaxMsgsPerSecond();

            for (String[] row : storedData)
            {
                Message msg = null;
                Variable var = null;

                // Step through all the created message
                for (Message message : messages)
                {
                    // Check if the message has already been created
                    if (message.getName().equals(row[AppSchedulerColumn.TIME_SLOT.ordinal()]))
                    {
                        // Assign the existing message to the message object and stop searching
                        msg = message;
                        break;
                    }
                }

                // Check if the message object is still null
                if (msg == null)
                {
                    // Create a new message
                    msg = new Message(row[AppSchedulerColumn.TIME_SLOT.ordinal()], "", time);

                    // Add the message to the existing message list
                    messages.add(msg);
                }

                // Check if the member column contains application information
                if (!row[AppSchedulerColumn.APP_INFO.ordinal()].isEmpty())
                {
                    // Split the member column to extract the application name
                    String name = row[AppSchedulerColumn.APP_INFO.ordinal()].split(",",
                                                                                   DefaultApplicationField.values().length)[0];

                    // Step through all created variables
                    for (Variable variable : varList)
                    {
                        // Check if the variable has already been created
                        if (variable.getFullName().equals(name))
                        {
                            // Assign the existing variable to the variable object
                            var = variable;
                            break;
                        }
                    }

                    // Check if the variable is still null
                    if (var == null)
                    {
                        // Create a new variable
                        var = VariableGenerator.generateApplicationData(row[AppSchedulerColumn.APP_INFO.ordinal()]);

                        // Add the variable to the existing variable list
                        varList.add(var);
                    }

                    // Add the variable to the general message
                    msg.addVariable(var);
                    var.addMessageIndex(Integer.valueOf(msg.getName().trim().split("_")[1]) - 1);
                }
            }
        }

        // Create a data stream object for the application information
        dataStreams.add(new DataStream(messages, varList));
    }
}
