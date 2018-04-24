package org.junglestory.charlotte.container;

import com.sun.webkit.plugin.PluginListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

public class PluginManager {
    private static final Logger Log = LoggerFactory.getLogger( PluginManager.class );

    private final Path pluginDirectory;

    private final PluginMonitor pluginMonitor;
    private boolean executed = false;

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the directory containing all plugins, typically HOME/plugins/
     */
    public PluginManager( File pluginDir )
    {
        this.pluginDirectory = pluginDir.toPath();
        pluginMonitor = new PluginMonitor( this );
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public synchronized void start()
    {
        pluginMonitor.start();
    }

    /**
     * Returns the directory that contains all plugins. This typically is OPENFIRE_HOME/plugins.
     *
     * @return The directory that contains all plugins.
     */
    public Path getPluginsDirectory()
    {
        return pluginDirectory;
    }

    /**
     * Returns true if at least one attempt to load plugins has been done. A true value does not mean
     * that available plugins have been loaded nor that plugins to be added in the future are already
     * loaded. :)<p>
     *
     * @return true if at least one attempt to load plugins has been done.
     */
    public boolean isExecuted()
    {
        return executed;
    }

    /**
     * Deletes a directory.
     *
     * @param dir the directory to delete.
     * @return true if the directory was deleted.
     */
    static boolean deleteDir( Path dir )
    {
        try
        {
            if ( Files.isDirectory( dir ) )
            {
                Files.walkFileTree( dir, new SimpleFileVisitor<Path>()
                {
                    @Override
                    public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException
                    {
                        try
                        {
                            Files.deleteIfExists( file );
                        }
                        catch ( IOException e )
                        {
                            Log.debug( "Plugin removal: could not delete: {}", file );
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory( Path dir, IOException exc ) throws IOException
                    {
                        try
                        {
                            Files.deleteIfExists( dir );
                        }
                        catch ( IOException e )
                        {
                            Log.debug( "Plugin removal: could not delete: {}", dir );
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                } );
            }
            return Files.notExists( dir ) || Files.deleteIfExists( dir );
        }
        catch ( IOException e )
        {
            return Files.notExists( dir );
        }
    }

    /**
     * Unloads a plugin. The {@link Plugin#destroyPlugin()} method will be called and then any resources will be
     * released. The name should be the canonical name of the plugin (based on the plugin directory name) and not the
     * human readable name as given by the plugin meta-data.
     *
     * This method only removes the plugin but does not delete the plugin JAR file. Therefore, if the plugin JAR still
     * exists after this method is called, the plugin will be started again the next  time the plugin monitor process
     * runs. This is useful for "restarting" plugins. To completely remove the plugin, use {@link #deletePlugin(String)}
     * instead.
     *
     * This method is called automatically when a plugin's JAR file is deleted.
     *
     * @param canonicalName the canonical name of the plugin to unload.
     */
    void unloadPlugin( String canonicalName )
    {
        Log.debug( "Unloading plugin '{}'...", canonicalName );


    }

    /**
     * Notifies all registered PluginManagerListener instances that the service monitoring for plugin changes completed a
     * periodic check.
     */
    void firePluginsMonitored()
    {

    }

}
