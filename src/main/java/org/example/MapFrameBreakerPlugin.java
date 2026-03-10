package org.example;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;

@Plugin(
        id = BuildConstants.PLUGIN_ID,
        version = BuildConstants.VERSION,
        description = "Breaks map item frames around spawn",
        authors = {"YourName"},
        mcVersions = {BuildConstants.MC_VERSION}
)
public class MapFrameBreakerPlugin implements ZenithProxyPlugin {

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        pluginAPI.registerModule(new org.example.module.MapFrameBreakerModule());
        pluginAPI.getLogger().info("§a[MapFrameBreaker] §fModule registered!");
    }
}