/**************************************************************************************************
 * /** \file CcddPy4JGatewayServer.java
 *
 * \author Kevin McCluney
 *
 * \brief Class for handling the Py4J gateway server.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: None
 *
 **************************************************************************************************/
package CCDD;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

/**************************************************************************************************
 * CFS Command and Data Dictionary Py4J handler class. This class handles execution of Python
 * scripts
 *************************************************************************************************/
public class CcddPy4JHandler
{
    // Class references
    private final CcddMain ccddMain;

    // Py4J gateway server reference
    private CcddPy4JGatewayServer gatewayServer = null;

    // Py4J version
    private String version = "";

    /**********************************************************************************************
     *  Py4J handler class constructor
     *
     *  @param ccddMain Main class
     *********************************************************************************************/
    CcddPy4JHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
    }

    /**********************************************************************************************
     * Start the Py4J gateway server
     *********************************************************************************************/
    protected void startGatewayServer()
    {
        if (gatewayServer == null)
        {
            gatewayServer = new CcddPy4JGatewayServer(ccddMain);
            gatewayServer.start();
        }
    }

    /**********************************************************************************************
     * Stop the Py4J gateway server
     *********************************************************************************************/
    protected void stopGatewayServer()
    {
        if (gatewayServer != null)
        {
            gatewayServer.shutdown();
            gatewayServer = null;
        }
    }

    /**********************************************************************************************
     * Determine if the Py4J gateway server is available
     *
     * @return True if the Py4J gateway server can be used
     *********************************************************************************************/
    protected boolean isPy4JAvailable()
    {
        boolean isAvailable = false;

        // Check if the server is not active
        if (gatewayServer == null)
        {
            // Start the server
            startGatewayServer();

            // Check if the server started successfully
            if (gatewayServer != null)
            {
                // Get the Py4J version
                version = gatewayServer.getVersion();

                // Set the server available flag and stop the server
                isAvailable = true;
                stopGatewayServer();
            }
        }
        // The server is currently active
        else
        {
            // Set the server available flag
            isAvailable = true;
        }

        return isAvailable;
    }

    /**********************************************************************************************
     * Get the Py4J version
     *
     * @return The Py4J version
     *********************************************************************************************/
    protected String getVersion()
    {
        return version;
    }
}

/**************************************************************************************************
 * CFS Command and Data Dictionary Py4J gateway server class. This class handles loading and
 * operation of the Py4J gateway server. Using this method, versus simply importing the
 * py4j.GatewayServer class, allows for the Py4J library to not be present (installed by the user)
 *************************************************************************************************/
class CcddPy4JGatewayServer
{
    private Class<?> serverClass = null;
    private Object instance = null;
    private String version = "";

    /**********************************************************************************************
     * Py4J gateway server class constructor. Load the Py4J gateway server class if available and
     * store the version based on the library file name
     *
     * @param entryPoint Reference to the application's main class
     *********************************************************************************************/
    CcddPy4JGatewayServer(Object entryPoint)
    {
        try
        {
            // Load the library, if present
            serverClass = Class.forName("py4j.GatewayServer");
            instance = serverClass.getDeclaredConstructor(Object.class).newInstance(entryPoint);

             URL location = serverClass.getResource('/' + serverClass.getName().replace('.', '/') + ".class");
             version = location.getFile().replaceFirst(".+" + File.separator + "py4j(.+)\\.jar.+", "$1");
        }
        catch (Exception e)
        {
        }
    }

    /**********************************************************************************************
     * Start the Py4J gateway server
     *********************************************************************************************/
    public void start()
    {
        // Check if the library exists
        if (instance != null)
        {
            try
            {
                // Execute the server start() method
                serverClass.getMethod("start").invoke(instance);
            }
            catch (Exception e)
            {
            }
        }
    }

    /**********************************************************************************************
     * Stop the Py4J gateway server
     *********************************************************************************************/
    public void shutdown()
    {
        // Check if the library exists
        if (instance != null)
        {
            try
            {
                // Execute the server stop() method
              serverClass.getMethod("shutdown").invoke(instance);
            }
            catch (Exception e)
            {
            }
        }
    }

    /**********************************************************************************************
     * Get the Py4J version
     *
     * @return The Py4J version
     *********************************************************************************************/
    protected String getVersion()
    {
        return version;
    }
}

/**************************************************************************************************
 * CFS Command and Data Dictionary Py4J script engine factory class. This class handles script
 * engine factory calls and allows the API to treat Py4J as a JSR-223 compliant scripting language
 *************************************************************************************************/
class CcddPy4JScriptEngineFactory implements ScriptEngineFactory
{
    // Class reference
    CcddScriptHandler scriptHndlr;

    // Py4J version
    private String version = "";

    /**********************************************************************************************
     * Py4J script engine class constructor
     *
     * @param version     Py4J version
     *
     * @param scriptHndlr Script handler reference
     *********************************************************************************************/
    CcddPy4JScriptEngineFactory(String version, CcddScriptHandler scriptHndlr)
    {
        this.version = version;
        this.scriptHndlr = scriptHndlr;
    }

    /**********************************************************************************************
     * Get the script handler reference
     *
     * @return The script handler reference
     *********************************************************************************************/
    protected CcddScriptHandler getScriptHandler()
    {
        return scriptHndlr;
    }

   /**********************************************************************************************
     * Get the Py4J script engine factory name
     *
     * @return The Py4J script engine factory name
     *********************************************************************************************/
    @Override
    public String getEngineName()
    {
        return "Py4J";
    }

    /**********************************************************************************************
     * Get the Py4J script engine factory version
     *
     * @return The Py4J script engine factory version
     *********************************************************************************************/
    @Override
    public String getEngineVersion()
    {
        return version;
    }

    /**********************************************************************************************
     * Get the list of script file extensions handled by Py4J
     *
     * @return List of script file extensions handled by Py4J
     *********************************************************************************************/
    @Override
    public List<String> getExtensions()
    {
         List<String> extensions = new ArrayList<String>(0);
         extensions.add("py");
         return extensions;
    }

    /**********************************************************************************************
     * Get the name of the language supported by the Py4J script engine factory
     *
     * @return The name of the language supported by the Py4J script engine factory
     *********************************************************************************************/
    @Override
    public String getLanguageName()
    {
        return "Python";
    }

    /**********************************************************************************************
     * Get the version(s) of the language supported by the Py4J script engine factory
     *
     * @return The version(s) of the language supported by the Py4J script engine factory
     *********************************************************************************************/
    @Override
    public String getLanguageVersion()
    {
        return "2.7, 3.x";
    }
    /**********************************************************************************************
     * Get the list of the Py4J script engine names
     *
     * @return List of the Py4J script engine names
     *********************************************************************************************/
    @Override
    public List<String> getNames()
    {
        List<String> names = new ArrayList<String>(0);
        names.add("Py4J");
        return names;
    }

    /**********************************************************************************************
     * Get the Python command for outputting the specified text
     *
     * @return The Python command for outputting the specified text
     *********************************************************************************************/
    @Override
    public String getOutputStatement(String toDisplay)
    {
        return "print(" + toDisplay + ")";
    }

    /**********************************************************************************************
     * Get a reference to the Py4J script engine
     *
     * @return Reference to the Py4J script engine
     *********************************************************************************************/
    @Override
    public ScriptEngine getScriptEngine()
    {
        return new CcddPy4JScriptEngine(this);
    }

    /**********************************************************************************************
     * The following methods are not implemented
     *********************************************************************************************/
    @Override
    public String getMethodCallSyntax(String obj, String m, String... args)
    {
        return null;
    }

    @Override
    public List<String> getMimeTypes()
    {
        return null;
    }

    @Override
    public Object getParameter(String key)
    {
        return null;
    }

    @Override
    public String getProgram(String... statements)
    {
        return null;
    }
}

/**************************************************************************************************
 * CFS Command and Data Dictionary Py4J script engine class. This class handles script
 * engine calls and allows the API to treat Py4J as a JSR-223 compliant scripting language
 *************************************************************************************************/
class CcddPy4JScriptEngine implements ScriptEngine
{
    // Py4J script engine factory reference
    private final CcddPy4JScriptEngineFactory factory;

    /**********************************************************************************************
     * Py4J script engine class constructor
     *
     * @param factory Reference to the Py4J script engine factory
     *********************************************************************************************/
    CcddPy4JScriptEngine(CcddPy4JScriptEngineFactory factory)
    {
        this.factory = factory;
    }

    /**********************************************************************************************
     * Execute a Python script
     *
     * @param scriptFileName Script file path and name
     *
     * @return 0 is the script terminates normally, -1 if the script is terminated by the user
     *
     * @throws ScriptException If the script encounters an error during execution or exits with a
     *                         non-zero exit code
     *********************************************************************************************/
    @Override
    public Object eval(String scriptFileName) throws ScriptException
    {
        Process process = null;
        int exitCode = -1;

        try
        {
            // Create the process builder to execute the command
            ProcessBuilder builder = new ProcessBuilder(factory.getScriptHandler().getPythonCommand(),
                                                        scriptFileName);

            // Direct the threads I/O to the standard streams
            builder.inheritIO();

            // Execute the command
            process = builder.start();

            // Wait for the command to complete and check if it failed to successfully complete
            exitCode = process.waitFor();

            // Check if the script did not terminate normally
            if (exitCode != 0)
            {
                // Output the exit code to the standard error stream and throw a script exception
                System.err.println("Script '" + scriptFileName + "' exited with error code: " + exitCode);
                throw new ScriptException("Error code: " + exitCode);
            }
        }
        catch (IOException | InterruptedException e)
        {
            if (process != null)
            {
                process.destroyForcibly();
            }
        }

        return exitCode;
    }

    /**********************************************************************************************
     * Get the Py4J script engine factory reference
     *
     * @return The Py4J script engine factory reference
     *********************************************************************************************/
    @Override
    public ScriptEngineFactory getFactory()
    {
        return factory;
    }

    /**********************************************************************************************
     * The following methods are not implemented
     *********************************************************************************************/
    @Override
    public Bindings createBindings()
    {
        return null;
    }

    @Override
    public Object eval(Reader reader) throws ScriptException
    {
        return null;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException
    {
        return null;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException
    {
        return null;
    }

    @Override
    public Object eval(String script, Bindings n) throws ScriptException
    {
        return null;
    }

    @Override
    public Object eval(Reader reader, Bindings n) throws ScriptException
    {
        return null;
    }

    @Override
    public Object get(String key)
    {
        return null;
    }

    @Override
    public Bindings getBindings(int scope)
    {
        return null;
    }

    @Override
    public ScriptContext getContext()
    {
        return null;
    }

    @Override
    public void put(String key, Object value)
    {
    }

    @Override
    public void setBindings(Bindings bindings, int scope)
    {
    }

    @Override
    public void setContext(ScriptContext context)
    {
    }
}
