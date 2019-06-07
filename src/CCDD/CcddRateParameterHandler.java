/**
 * CFS Command and Data Dictionary rate parameter handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.TRAILING_ZEROES;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.RateParameter;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary rate parameter handler class
 *************************************************************************************************/
public class CcddRateParameterHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddInputTypeHandler inputTypeHandler;

    // Rate parameters
    private int maxSecPerMsg;
    private int maxMsgsPerSec;
    private boolean includeUneven;

    // List containing the rate information for a stream
    private List<RateInformation> rateInformation;

    /**********************************************************************************************
     * Rate parameter handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddRateParameterHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

        // Initialize the rate information list
        rateInformation = new ArrayList<RateInformation>();

        // Get the rate parameters from the project database
        getRateParameters();

        // Sort the rate information by data stream name
        Collections.sort(rateInformation, new Comparator<RateInformation>()
        {
            /**************************************************************************************
             * Compare the stream names of two rates, ignoring case
             *************************************************************************************/
            @Override
            public int compare(RateInformation rate1, RateInformation rate2)
            {
                return rate1.getStreamName().compareToIgnoreCase(rate2.getStreamName());
            }
        });
    }

    /**********************************************************************************************
     * Get the maximum number of seconds allowed between downlinking two of the same message
     *
     * @return Maximum number of seconds allowed between downlinking two of the same message
     *********************************************************************************************/
    protected int getMaxSecondsPerMsg()
    {
        return maxSecPerMsg;
    }

    /**********************************************************************************************
     * Get the maximum number of messages that can be downlinked in one second
     *
     * @return Maximum number of messages that can be downlinked in one second
     *********************************************************************************************/
    protected int getMaxMsgsPerSecond()
    {
        return maxMsgsPerSec;
    }

    /**********************************************************************************************
     * Get the value of the flag that indicates if unevenly time-space sample rates are to be
     * included
     *
     * @return true if unevenly time-spaced sample rate values are included; false if only sample
     *         rates that are evenly time-spaced are included
     *********************************************************************************************/
    protected boolean isIncludeUneven()
    {
        return includeUneven;
    }

    /**********************************************************************************************
     * Get the list of rate information
     *
     * @return List of rate information
     *********************************************************************************************/
    protected List<RateInformation> getRateInformation()
    {
        return rateInformation;
    }

    /**********************************************************************************************
     * Set the list of rate information
     *
     * @param rateInformation
     *            list of rate information
     *********************************************************************************************/
    protected void setRateInformation(List<RateInformation> rateInformation)
    {
        this.rateInformation = rateInformation;
    }

    /**********************************************************************************************
     * Add rate information to the list for the specified rate column name
     *
     * @param rateName
     *            new rate's column name
     *********************************************************************************************/
    protected void addRateInformation(String rateName)
    {
        // Get the rate information based on the rate column name
        RateInformation rateInfo = getRateInformationByRateName(rateName);

        // Check if rate information with this name doesn't already exist
        if (rateInfo == null)
        {
            // Create the specified rate, adjust the rate counter, and set the flag to indicate a
            // rate is added
            rateInformation.add(new RateInformation(rateName));
        }
        // The rate information already exists for this rate column name
        else
        {
            // Increment the share counter for the existing rate information
            rateInfo.setNumSharedTableTypes(rateInfo.getNumSharedTableTypes() + 1);
        }
    }

    /**********************************************************************************************
     * Change the rate column name for in the rate information list. If the original rate column
     * name matches one in another table type then create a new rate entry instead of renaming the
     * existing one. If the new rate column name matches one in another table type then merge it
     * with the existing one and delete the original
     *
     * @param oldRateName
     *            current rate column name
     *
     * @param newRateName
     *            new rate column name
     *********************************************************************************************/
    protected void renameRateInformation(String oldRateName, String newRateName)
    {
        // Get the rate information for the original rate column name
        RateInformation rateInfo = getRateInformationByRateName(oldRateName);

        // Check if rate information with this name exists
        if (rateInfo != null)
        {
            // Check if only a single table type references this rate column name
            if (rateInfo.getNumSharedTableTypes() == 1)
            {
                // Get the rate information for the new rate column name
                RateInformation rateInfoNew = getRateInformationByRateName(newRateName);

                // Check if the new rate column doesn't already exist
                if (rateInfoNew == null)
                {
                    // Rename the specified rate column and set the flag to indicate a rate is
                    // renamed
                    rateInfo.setRateName(newRateName);

                    // Check if the original rate column name is the same as the stream name; this
                    // implies the user hasn't chosen a name for the stream
                    if (oldRateName.equals(rateInfo.getStreamName()))
                    {
                        // Set the stream name to the new name as well
                        rateInfo.setStreamName(newRateName);
                    }
                }
                // A rate column by this name already exists
                else
                {
                    // Delete the original rate column's information and increment the counter for
                    // the 'new' rate column
                    deleteRateInformation(oldRateName);
                    rateInfoNew.setNumSharedTableTypes(rateInfoNew.getNumSharedTableTypes() + 1);
                }
            }
            // This rate column is referenced by another table type
            else
            {
                // Create new rate column information so that the shared one is unchanged and
                // decrement the share counter for the existing rate information
                addRateInformation(newRateName);
                rateInfo.setNumSharedTableTypes(rateInfo.getNumSharedTableTypes() - 1);
            }
        }
    }

    /**********************************************************************************************
     * Remove the specified rate's information from the list
     *
     * @param rateName
     *            rate name for the rate information object to remove
     *********************************************************************************************/
    protected void deleteRateInformation(String rateName)
    {
        // Get the rate information based on the rate column name
        RateInformation rateInfo = getRateInformationByRateName(rateName);

        // Check if rate information with this name exists
        if (rateInfo != null)
        {
            // Check is only one table type references this rate column name
            if (rateInfo.getNumSharedTableTypes() == 1)
            {
                // Remove the specified rate's information, adjust the rate counter, and set the
                // flag to indicate a rate is removed
                rateInformation.remove(rateInfo);
            }
            // The rate column name is shared between multiple table types
            else
            {
                // Decrement the share counter for the existing rate information
                rateInfo.setNumSharedTableTypes(rateInfo.getNumSharedTableTypes() - 1);
            }
        }
    }

    /**********************************************************************************************
     * Get the rate information with the specified rate column
     *
     * @param rateColumnName
     *            rate column name
     *
     * @return Rate information with the specified rate column; null if the rate column doesn't
     *         exist
     *********************************************************************************************/
    protected RateInformation getRateInformationByRateName(String rateColumnName)
    {
        RateInformation rateInfo = null;

        // Get the index into the rate information for the specified rate column name
        int index = getRateInformationIndexByRateName(rateColumnName);

        // Check if the rate column name exists
        if (index != -1)
        {
            // Get the reference to the rate information at the index
            rateInfo = rateInformation.get(index);
        }

        return rateInfo;
    }

    /**********************************************************************************************
     * Get the rate information with the specified data stream name
     *
     * @param streamName
     *            name of stream
     *
     * @return Rate information with the data stream name; null if the data stream doesn't exist
     *********************************************************************************************/
    protected RateInformation getRateInformationByStreamName(String streamName)
    {
        RateInformation rateInfo = null;

        // Get the index into the rate information for the specified data stream name
        int index = getRateInformationIndexByStreamName(streamName);

        // Check if the stream name exists
        if (index != -1)
        {
            // Get the reference to the rate information at the index
            rateInfo = rateInformation.get(index);
        }

        return rateInfo;
    }

    /**********************************************************************************************
     * Get the index of the rate information with the specified rate column name
     *
     * @param rateColumnName
     *            rate column name
     *
     * @return Index of the rate information with the specified rate column name; -1 if no rate
     *         information has this rate column name
     *********************************************************************************************/
    protected int getRateInformationIndexByRateName(String rateColumnName)
    {
        int rateIndex = -1;

        // Check that a valid rate column name is supplied
        if (rateColumnName != null && !rateColumnName.isEmpty())
        {
            int index = 0;

            // Step through the rate information
            for (RateInformation info : rateInformation)
            {
                // Check if the rate name matches the name for this rate information
                if (rateColumnName.equals(info.getRateName()))
                {
                    // Save the index and stop searching
                    rateIndex = index;
                    break;
                }

                index++;
            }
        }

        return rateIndex;
    }

    /**********************************************************************************************
     * Get the index of the rate information with the specified stream name
     *
     * @param streamName
     *            stream name
     *
     * @return Index of the rate information with the specified stream name; -1 if no rate
     *         information has this stream name
     *********************************************************************************************/
    protected int getRateInformationIndexByStreamName(String streamName)
    {
        int streamIndex = -1;
        int index = 0;

        // Step through the rate information
        for (RateInformation info : rateInformation)
        {
            // Check if the stream name matches the name for this rate information
            if (streamName.equals(info.getStreamName()))
            {
                // Save the index and stop searching
                streamIndex = index;
                break;
            }

            index++;
        }

        return streamIndex;
    }

    /**********************************************************************************************
     * Get the number of unique rate columns
     *
     * @return Number of unique rate columns
     *********************************************************************************************/
    protected int getNumRateColumns()
    {
        return rateInformation.size();
    }

    /**********************************************************************************************
     * Set the rate information list based on the unique rate columns
     *
     * @return true if the number of rate columns changed
     *********************************************************************************************/
    protected boolean setRateInformation()
    {
        // Store the current number of rate columns
        int oldNumRateColumns = rateInformation.size();

        // Step through any existing rate column information
        for (RateInformation rateInfo : rateInformation)
        {
            // Set the number of table types referencing this rate to zero. This is used to
            // determine if a rate is still in use
            rateInfo.setNumSharedTableTypes(0);
        }

        // Step through each table type
        for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
        {
            // Check if the type represents a structure
            if (typeDefn.isStructure())
            {
                // Step through each column in the type definition
                for (int index = 0; index < typeDefn.getColumnCountDatabase(); index++)
                {
                    // Check if the column is a sample rate
                    if (typeDefn.getInputTypes()[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.RATE)))
                    {
                        // Get the column's visible name
                        String colName = typeDefn.getColumnNamesUser()[index];

                        // Get the rate information for this rate column name
                        RateInformation rateInfo = getRateInformationByRateName(colName);

                        // Check if the column name hasn't been added
                        if (rateInfo == null)
                        {
                            // Add the rate column name to the list
                            rateInformation.add(new RateInformation(colName));
                        }
                        // The rate column already exists
                        else
                        {
                            // Increment the share counter for the existing rate information
                            rateInfo.setNumSharedTableTypes(rateInfo.getNumSharedTableTypes() + 1);
                        }
                    }
                }
            }
        }

        // Create a list to store any rate column information that no longer exists
        List<RateInformation> removedRates = new ArrayList<RateInformation>();

        // Step through each rate column's information
        for (RateInformation rateInfo : rateInformation)
        {
            // Check if the rate is not referenced by a table type
            if (rateInfo.getNumSharedTableTypes() == 0)
            {
                // Add the non-referenced rate to the list of those to remove
                removedRates.add(rateInfo);
            }
        }

        // Remove any rate information that's no longer valid
        rateInformation.removeAll(removedRates);

        return rateInformation.size() != oldNumRateColumns;
    }

    /**********************************************************************************************
     * Get the rate parameters from the database and calculate the sample rates
     *********************************************************************************************/
    private void getRateParameters()
    {
        // Build the rate information list from the table types
        setRateInformation();

        // Get the rate parameters from the database
        String[] rateValues = dbTable.queryTableComment(InternalTable.TLM_SCHEDULER.getTableName(),
                                                        0,
                                                        ccddMain.getMainFrame());

        try
        {
            // Check if the number of rate parameters is invalid
            if (rateValues.length < 3 || (rateValues.length - 3) % 4 != 0)
            {
                throw new Exception("missing rate value");
            }

            // Convert the rate parameters to integers and set the flag for whether or not unevenly
            // time-spaced rates should be included
            maxSecPerMsg = Integer.valueOf(rateValues[RateParameter.MAXIMUM_SECONDS_PER_MESSAGE.ordinal()]);
            maxMsgsPerSec = Integer.valueOf(rateValues[RateParameter.MAXIMUM_MESSAGES_PER_SECOND.ordinal()]);
            includeUneven = Boolean.valueOf(rateValues[RateParameter.INCLUDE_UNEVEN_RATES.ordinal()]);

            // Check if any of the values are less than 1
            if (maxSecPerMsg <= 0 || maxMsgsPerSec <= 0)
            {
                throw new Exception("zero or negative rate value");
            }

            // Get the number of stream-specific parameters per rate column
            int numStreamParms = RateParameter.values().length - 3;

            // Step through each stream
            for (int index = numStreamParms; index < rateValues.length; index += numStreamParms)
            {
                int offset = index - numStreamParms;

                // Remove the leading and trailing quotes from the rate column name
                String rateColName = rateValues[RateParameter.RATE_COLUMN_NAME.ordinal()
                                                + offset].replaceAll("^\"(.*)\"$", "$1");

                // Get the rate information for this rate column
                RateInformation rateInfo = getRateInformationByRateName(rateColName);

                // Check if the rate information for this column exists
                if (getRateInformationByRateName(rateColName) != null)
                {
                    // Remove the leading and trailing quotes from the stream name
                    String streamName = rateValues[RateParameter.STREAM_NAME.ordinal()
                                                   + offset].replaceAll("^\"(.*)\"$", "$1");

                    // Convert the rate parameters to integers
                    int maxMsgsPerCycle = Integer.valueOf(rateValues[RateParameter.MAXIMUM_MESSAGES_PER_CYCLE.ordinal() + offset]);
                    int maxBytesPerSec = Integer.valueOf(rateValues[RateParameter.MAXIMUM_BYTES_PER_SECOND.ordinal() + offset]);

                    // Check if any of the values are less than 1
                    if (maxMsgsPerCycle <= 0 || maxBytesPerSec <= 0)
                    {
                        throw new Exception("zero or negative rate value");
                    }

                    // Update the rate information
                    rateInfo.setStreamName(streamName);
                    rateInfo.setMaxMsgsPerCycle(maxMsgsPerCycle);
                    rateInfo.setMaxBytesPerSec(maxBytesPerSec);
                }
            }
        }
        catch (Exception e)
        {
            // Inform the user that calculating the rate parameters failed
            ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                       "Rate Parameter Error",
                                                       "Invalid rate parameter(s): using default values instead; cause '"
                                                                               + e.getMessage()
                                                                               + "'",
                                                       "<html><b>Invalid rate parameter(s): using default values instead");

            // Use default values
            maxSecPerMsg = 1;
            maxMsgsPerSec = 1;
            includeUneven = false;

            // Step through each stream
            for (int rateIndex = 0; rateIndex < rateInformation.size(); rateIndex++)
            {
                // Use default values
                rateInformation.get(rateIndex).setDefaultValues();
            }

            // Store the default rate parameters in the project database
            dbTable.storeRateParameters(ccddMain.getMainFrame());
        }

        // Calculate the sample rates from the rate parameter values
        calculateSampleRates();
    }

    /**********************************************************************************************
     * Set the rate parameters, store them in the project database, and calculate the sample rates
     *
     * @param maxSecPerMsg
     *            maximum number of seconds allowed between downlinking two of the same message
     *
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one second
     *
     * @param streamName
     *            array containing the stream name per stream
     *
     * @param maxMsgsPerCycle
     *            array containing the maximum number of messages that can be downlinked before
     *            repeating the message list per stream
     *
     * @param maxBytesPerSec
     *            array containing the maximum number of bytes that can be downlinked in one second
     *            per stream
     *
     * @param includeUneven
     *            true to include unevenly time-spaced sample rate values; false to only include
     *            sample rates that are evenly time-spaced
     *
     * @param parent
     *            component calling this method, used for positioning any error dialogs
     *********************************************************************************************/
    protected void setRateParameters(int maxSecPerMsg,
                                     int maxMsgsPerSec,
                                     String[] streamName,
                                     int[] maxMsgsPerCycle,
                                     int[] maxBytesPerSec,
                                     boolean includeUneven,
                                     Component parent)
    {
        this.maxSecPerMsg = maxSecPerMsg;
        this.maxMsgsPerSec = maxMsgsPerSec;
        this.includeUneven = includeUneven;

        // Step through each stream
        for (int index = 0; index < rateInformation.size(); index++)
        {
            // Store the rate parameters
            rateInformation.get(index).setStreamName(streamName[index].isEmpty()
                                                                                 ? rateInformation.get(index).getRateName()
                                                                                 : streamName[index]);
            rateInformation.get(index).setMaxMsgsPerCycle(maxMsgsPerCycle[index]);
            rateInformation.get(index).setMaxBytesPerSec(maxBytesPerSec[index]);
        }

        // Store the default rate parameters in the project database
        dbTable.storeRateParameters(ccddMain.getMainFrame());

        // Calculate the sample rates from the rate parameter values
        calculateSampleRates();
    }

    /**********************************************************************************************
     * Build the array of valid sample rates for all rate columns based on the current rate
     * parameters. Update open table editors that have a Rate column
     *********************************************************************************************/
    private void calculateSampleRates()
    {
        // Step through each data stream
        for (RateInformation rateInfo : rateInformation)
        {
            // Store the rates in array form in the rate information
            rateInfo.setSampleRates(calculateSampleRates(maxSecPerMsg,
                                                         maxMsgsPerSec,
                                                         rateInfo.getMaxMsgsPerCycle(),
                                                         includeUneven));
        }

        // Step through the open table editor dialogs
        for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
        {
            // Step through each individual editor in the editor dialog
            for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
            {
                // Update the sample rate column cells
                editor.setUpSampleRateColumn();
            }
        }
    }

    /**********************************************************************************************
     * Build the array of valid sample rates based on the specified rate parameters
     *
     * @param maxSecPerMsg
     *            maximum number of seconds allowed between downlinking two of the same message
     *
     * @param maxMsgsPerSec
     *            maximum number of messages that can be downlinked in one second
     *
     * @param maxMsgsPerCycle
     *            maximum number of messages that can be downlinked before repeating the message
     *            list per stream
     *
     * @param includeUneven
     *            true to include unevenly time-spaced sample rate values; false to only include
     *            sample rates that are evenly time-spaced
     *
     * @return Array containing the valid sample rates
     *********************************************************************************************/
    protected String[] calculateSampleRates(int maxSecPerMsg,
                                            int maxMsgsPerSec,
                                            int maxMsgsPerCycle,
                                            boolean includeUneven)
    {
        // Create storage for the valid sample rates
        List<String> rates = new ArrayList<String>();

        // Calculate the cycle period in seconds
        double period = (double) maxMsgsPerCycle / maxMsgsPerSec;

        // Check if unevenly time-spaced rates are to be included
        if (includeUneven)
        {
            // Step from the maximum messages per cycle to 1
            for (int cyc = maxMsgsPerCycle; cyc >= 1; cyc--)
            {
                // Add the rate to the list
                rates.add(formatRate(cyc, period));
            }
        }
        // Only include evenly time-spaced rates
        else
        {
            int index = 0;

            // Step through the potential factors, beginning with 1. The loop termination criteria
            // accounts for not needing to check a factor's companion value
            for (int div = 1; div <= maxMsgsPerCycle / div; div++)
            {
                // Check if the number is divisible by the divisor with no remainder
                if (maxMsgsPerCycle % div == 0)
                {
                    // Format the low and high factors
                    String lowFactor = formatRate(div, period);
                    String highFactor = formatRate(maxMsgsPerCycle / div, period);

                    // Check if the two factors differ
                    if (!highFactor.equals(lowFactor))
                    {
                        // Add the second factor to the list. Use of the index causes insertion of
                        // the values in descending order
                        rates.add(index, highFactor);
                        index++;
                    }

                    // Add the low factor to the list. Use of the index causes insertion of the
                    // values in descending order
                    rates.add(index, lowFactor);
                }
            }
        }

        // Step through the rates greater than once per cycle
        for (double sec = period * 2.0; sec <= maxSecPerMsg; sec += period)
        {
            // Format and add the rate to the list
            rates.add(formatRate(1, sec));
        }

        return rates.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Format a rate value as the specified number of samples over the specified number of seconds.
     * Use whole numbers if possible; otherwise use the format '1/x', where 'x' is the number of
     * seconds rounded to 5 decimal places. Remove any extra trailing zeroes after the decimal and
     * the decimal if no zeroes remain for both the numerator and denominator
     *
     * @param samples
     *            number of samples
     *
     * @param seconds
     *            number of seconds
     *
     * @return Rate, in samples per second, displaying a maximum of 5 decimal places)
     *********************************************************************************************/
    private String formatRate(double samples, double seconds)
    {
        String rate;

        // Check if the rate is evenly divisible into 1 (within 5 decimal places)
        if ((int) (samples * 100000) % (int) (seconds * 100000) <= 10)
        {
            // Add the rate as a whole number instead of a fraction
            rate = String.format("%.5f", samples / seconds);
        }
        // The rate isn't evenly divisible into 1
        else
        {
            // Add the rate in the form '1/x'
            rate = "1/" + String.format("%.5f", seconds / samples);
        }

        return rate.replaceAll(TRAILING_ZEROES, "");
    }

    /**********************************************************************************************
     * Get an array containing the unique data stream names
     *
     * @return Array containing the unique data stream names
     *********************************************************************************************/
    protected String[] getDataStreamNames()
    {
        List<String> columnNames = new ArrayList<String>();

        // Step through each data stream
        for (RateInformation rateInfo : rateInformation)
        {
            // Add the data stream name to the list
            columnNames.add(rateInfo.getStreamName());
        }

        return columnNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the array of the sample rate values for the specified rate column name with those rates
     * not assigned to any telemetry parameter in the structure tables grayed out
     *
     * @param rateName
     *            rate column name
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return Array of the sample rate values for the specified rate column name with those rates
     *         not assigned to any telemetry parameter in the structure tables grayed out; an empty
     *         array if the rate name isn't valid
     *********************************************************************************************/
    protected String[] getRatesInUse(String rateName, Component parent)
    {
        String[] availableRates = new String[0];

        // Create the string array list using the second column (rate values) for comparison
        // purposes
        ArrayListMultiple ratesInUse = new ArrayListMultiple(1);

        // Get the rate information for the specified rate
        RateInformation rateInfo = getRateInformationByRateName(rateName);

        // Check if the rate name is recognized
        if (rateInfo != null)
        {
            // Get a copy of the array of sample rates for this rate. If a copy isn't used then the
            // stored sample rates can be altered to show as disabled below; subsequent calls to
            // get the sample rates will have the disable tags
            availableRates = Arrays.copyOf(rateInfo.getSampleRates(),
                                           rateInfo.getSampleRates().length);

            // Query the database for those values of the specified rate that are in use in all
            // tables with a table type representing a structure, including any references in the
            // custom values table. Only unique rate values are returned
            ratesInUse.addAll(dbTable.queryDatabase("SELECT DISTINCT ON (2) * FROM find_columns_by_name('"
                                                    + rateName
                                                    + "', '"
                                                    + tableTypeHandler.convertVisibleToDatabase(rateName,
                                                                                                DefaultInputType.RATE.getInputName(),
                                                                                                true)
                                                    + "', '{"
                                                    + Arrays.toString(tableTypeHandler.getStructureTableTypes()).replaceAll("[\\[\\]]",
                                                                                                                            "")
                                                    + "}');",
                                                    parent));

            // Step through the available sample rates
            for (int index = 0; index < availableRates.length; index++)
            {
                // Check if the rate isn't used by any telemetry parameter
                if (!ratesInUse.contains(availableRates[index]))
                {
                    // Flag the rate as disabled
                    availableRates[index] = DISABLED_TEXT_COLOR + availableRates[index];
                }
            }
        }

        return availableRates;
    }
}
