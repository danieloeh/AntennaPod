package de.danoeh.antennapod.config;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.core.ClientConfig;

/**
 * Configures the ClientConfig class of the core package.
 */
class ClientConfigurator {

    private ClientConfigurator() {
    }

    static {
        ClientConfig.USER_AGENT = "AntennaPod/" + BuildConfig.VERSION_NAME;
        ClientConfig.applicationCallbacks = new ApplicationCallbacksImpl();
        ClientConfig.castCallbacks = new CastCallbackImpl();
    }
}
