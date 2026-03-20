package org.framebreaker;

import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import org.framebreaker.module.FrameStatsCommand;
import org.framebreaker.module.MapFrameBreakerCommand;
import org.framebreaker.module.MapFrameBreakerModule;

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
        MapFrameBreakerConfig config = pluginAPI.registerConfig("map-frame-breaker", MapFrameBreakerConfig.class);
        MapFrameBreakerModule module = new MapFrameBreakerModule(config);
        pluginAPI.registerModule(module);
        pluginAPI.registerCommand(new MapFrameBreakerCommand(module));
        pluginAPI.registerCommand(new FrameStatsCommand(module, false));
        pluginAPI.registerCommand(new FrameStatsCommand(module, true));
        pluginAPI.getLogger().info("[MapFrameBreaker] Module registered.");
    }
}
