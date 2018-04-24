package org.junglestory.charlotte.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public class PluginMonitor {
    private static final Logger Log = LoggerFactory.getLogger( PluginMonitor.class );

    private final PluginManager pluginManager;

    private ScheduledExecutorService executor;

    private boolean isTaskRunning = false;

    public PluginMonitor( final PluginManager pluginManager )
    {
        this.pluginManager = pluginManager;
    }

    /**
     * Start periodically checking the plugin directory.
     */
    public void start()
    {
        if ( executor != null )
        {
            executor.shutdown();
        }

        executor = new ScheduledThreadPoolExecutor( 1 );

        // See if we're in development mode. If so, check for new plugins once every 5 seconds Otherwise, default to every 20 seconds.
        if ( Boolean.getBoolean( "developmentMode" ) )
        {
            executor.scheduleWithFixedDelay( new MonitorTask(), 0, 5, TimeUnit.SECONDS );
        }
        else
        {
            executor.scheduleWithFixedDelay( new MonitorTask(), 0, 20, TimeUnit.SECONDS );
        }
    }

    private class MonitorTask implements Runnable {
        @Override
        public void run() {
            // Prevent two tasks from running in parallel by using the plugin monitor itself as a mutex.
            synchronized (PluginMonitor.this) {
                isTaskRunning = true;
                try {
                    // The directory that contains all plugins.
                    final Path pluginsDirectory = pluginManager.getPluginsDirectory();
                    if (!Files.isDirectory(pluginsDirectory) || !Files.isReadable(pluginsDirectory)) {
                        Log.error("Unable to process plugins. The plugins directory does not exist (or is no directory): {}", pluginsDirectory);
                        return;
                    }

                    // Turn the list of JAR/WAR files into a set so that we can do lookups.
                    final Set<String> jarSet = new HashSet<>();

                    // Explode all plugin files that have not yet been exploded (or need to be re-exploded).
                    try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( pluginsDirectory, new DirectoryStream.Filter<Path>()
                    {
                        @Override
                        public boolean accept( final Path path ) throws IOException
                        {
                            if ( Files.isDirectory( path ) )
                            {
                                return false;
                            }

                            final String fileName = path.getFileName().toString().toLowerCase();
                            return ( fileName.endsWith( ".jar" ) || fileName.endsWith( ".war" ) );
                        }
                    } ) )
                    {
                        for ( final Path jarFile : ds )
                        {
                            final String fileName = jarFile.getFileName().toString();
                            final String canonicalPluginName = fileName.substring( 0, fileName.length() - 4 ).toLowerCase(); // strip extension.
Log.info(canonicalPluginName);
                            jarSet.add( canonicalPluginName );

                            // See if the JAR has already been exploded.
                            final Path dir = pluginsDirectory.resolve( canonicalPluginName );

                            // See if the JAR is newer than the directory. If so, the plugin needs to be unloaded and then reloaded.
                            if ( Files.exists( dir ) && Files.getLastModifiedTime( jarFile ).toMillis() > Files.getLastModifiedTime( dir ).toMillis() )
                            {
                                // If this is the first time that the monitor process is running, then plugins won't be loaded yet. Therefore, just delete the directory.
                                if ( !pluginManager.isExecuted() )
                                {
                                    int count = 0;
                                    // Attempt to delete the folder for up to 5 seconds.
                                    while ( !PluginManager.deleteDir( dir ) && count++ < 5 )
                                    {
                                        Thread.sleep( 1000 );
                                    }
                                }
                                else
                                {
                                    // Not the first time? Properly unload the plugin.
                                    pluginManager.unloadPlugin( canonicalPluginName );
                                }
                            }

                            // If the JAR needs to be exploded, do so.
                            if ( Files.notExists( dir ) )
                            {
                                unzipPlugin( canonicalPluginName, jarFile, dir );
                            }
                        }
                    }



                    // Load all plugins that need to be loaded. Make sure that the admin plugin is loaded first (as that
                    // should be available as soon as possible), followed by all other plugins. Ensure that parent plugins
                    // are loaded before their children.
                    try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( pluginsDirectory, new DirectoryStream.Filter<Path>()
                    {
                        @Override
                        public boolean accept( final Path path ) throws IOException
                        {
                            return Files.isDirectory( path );
                        }
                    } ) )
                    {
                        // Look for extra plugin directories specified as a system property.
                        final Set<Path> devPlugins = new HashSet<>();
                        final String devPluginDirs = System.getProperty( "pluginDirs" );
                        if ( devPluginDirs != null )
                        {
                            final StringTokenizer st = new StringTokenizer( devPluginDirs, "," );
                            while ( st.hasMoreTokens() )
                            {
                                try
                                {
                                    final String devPluginDir = st.nextToken().trim();
                                    final Path devPluginPath = Paths.get( devPluginDir );
                                    if ( Files.exists( devPluginPath ) && Files.isDirectory( devPluginPath ) )
                                    {
                                        devPlugins.add( devPluginPath );
                                    }
                                    else
                                    {
                                        Log.error( "Unable to load a dev plugin as its path (as supplied in the 'pluginDirs' system property) does not exist, or is not a directory. Offending path: [{}] (parsed from raw value [{}])", devPluginPath, devPluginDir );
                                    }
                                }
                                catch ( InvalidPathException ex )
                                {
                                    Log.error( "Unable to load a dev plugin as an invalid path was added to the 'pluginDirs' system property.", ex );
                                }
                            }
                        }


                        // Trigger event that plugins have been monitored
                        pluginManager.firePluginsMonitored();
                    }
                } catch (Throwable e) {
                    Log.error("An unexpected exception occurred:", e);
                } finally {
                    isTaskRunning = false;
                }
            }
        }
    }

    /**
     * Unzips a plugin from a JAR file into a directory. If the JAR file
     * isn't a plugin, this method will do nothing.
     *
     * @param pluginName the name of the plugin.
     * @param file       the JAR file
     * @param dir        the directory to extract the plugin to.
     */
    private void unzipPlugin( String pluginName, Path file, Path dir )
    {
        try ( ZipFile zipFile = new JarFile( file.toFile() ) )
        {
            // Ensure that this JAR is a plugin.
            if ( zipFile.getEntry( "plugin.xml" ) == null )
            {
                return;
            }
            Files.createDirectory( dir );
            // Set the date of the JAR file to the newly created folder
            Files.setLastModifiedTime( dir, Files.getLastModifiedTime( file ) );
            Log.debug( "Extracting plugin '{}'...", pluginName );
            for ( Enumeration e = zipFile.entries(); e.hasMoreElements(); )
            {
                JarEntry entry = (JarEntry) e.nextElement();
                Path entryFile = dir.resolve( entry.getName() );
                // Ignore any manifest.mf entries.
                if ( entry.getName().toLowerCase().endsWith( "manifest.mf" ) )
                {
                    continue;
                }
                if ( !entry.isDirectory() )
                {
                    Files.createDirectories( entryFile.getParent() );
                    try ( InputStream zin = zipFile.getInputStream( entry ) )
                    {
                        Files.copy( zin, entryFile, StandardCopyOption.REPLACE_EXISTING );
                    }
                }
            }
            Log.debug( "Successfully extracted plugin '{}'.", pluginName );
        }
        catch ( Exception e )
        {
            Log.error( "An exception occurred while trying to extract plugin '{}':", pluginName, e );
        }
    }


}
