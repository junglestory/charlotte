package org.junglestory.charlotte;

import org.junglestory.charlotte.container.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Charlotte {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    private PluginManager pluginManager;
    private File home;

    public static  void main(String[] args) {
        Charlotte charlotte = new Charlotte();
        charlotte.start();
    }

    public void start() {
        home = new File(System.getProperty("home"));

        // Create PluginManager now (but don't start it) so that modules may use it
        File pluginDir = new File(home, "plugins");
        pluginManager = new PluginManager(pluginDir);

        pluginManager.start();

        // Log that the server has been started
//        String startupBanner = LocaleUtils.getLocalizedString("short.title") + " " + xmppServerInfo.getVersion().getVersionString() +
//                " - UbiAxonChat 1.0.0 [" + JiveGlobals.formatDateTime(new Date()) + "]";
//        logger.info(startupBanner);
//        System.out.println(startupBanner);

        logger.info(home.getAbsolutePath());
    }
}
