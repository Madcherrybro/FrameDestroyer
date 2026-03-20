package org.framebreaker.module;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import org.framebreaker.MapFrameBreakerConfig;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class MapFrameBreakerCommand extends Command {
    private final MapFrameBreakerModule module;

    public MapFrameBreakerCommand(MapFrameBreakerModule module) {
        this.module = module;
    }

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("framebreaker")
                .aliases("fb")
                .category(CommandCategory.MODULE)
                .description("Shows stats and manages the map frame breaker module.")
                .usageLines(
                        "",
                        "Quick use:",
                        "frames",
                        "framestats",
                        "framebreaker filter show",
                        "framebreaker target info",
                        "",
                        "Common filter setup:",
                        "framebreaker filter name add \"discord.gg\"",
                        "framebreaker filter name remove \"discord.gg\"",
                        "framebreaker filter name toggle on/off",
                        "framebreaker filter reset",
                        "",
                        "Advanced filter setup:",
                        "stats",
                        "list",
                        "list all",
                        "clear confirm",
                        "filter show",
                        "filter reset",
                        "filter reload",
                        "filter mapid list|add <id>|remove <id>|clear|toggle on/off|mode off|allowlist|blocklist",
                        "filter name list|add <keyword>|remove <keyword>|clear|toggle on/off|mode off|allowlist|blocklist",
                        "filter name case on/off",
                        "filter name partial on/off",
                        "target info",
                        "help"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("framebreaker")
                .executes(c -> {
                    fillDetailedStats(c);
                    return OK;
                })
                .then(literal("stats").executes(c -> {
                    fillDetailedStats(c);
                    return OK;
                }))
                .then(literal("list").executes(c -> {
                    fillFrameList(c, false);
                    return OK;
                }).then(literal("all").executes(c -> {
                    fillFrameList(c, true);
                    return OK;
                })))
                .then(literal("clear").then(literal("confirm").executes(c -> {
                    module.resetTrackingData();
                    c.getSource().getEmbed()
                            .title("Frame data cleared")
                            .description("Map frame breaker tracking data has been reset.")
                            .primaryColor();
                    return OK;
                })))
                .then(buildFilterCommands())
                .then(literal("target").then(literal("info").executes(c -> {
                    fillTargetInfo(c);
                    return OK;
                })))
                .then(literal("help").executes(c -> {
                    c.getSource().getEmbed()
                            .title("Frame Breaker Commands")
                            .description(commandUsage().mediumSerialize(c.getSource().getSource()))
                            .primaryColor();
                    return OK;
                }));
    }

    private LiteralArgumentBuilder<CommandContext> buildFilterCommands() {
        return literal("filter")
                .executes(c -> {
                    fillFilterSettings(c);
                    return OK;
                })
                .then(literal("list").executes(c -> {
                    fillFilterSettings(c);
                    return OK;
                }))
                .then(literal("show").executes(c -> {
                    fillFilterSettings(c);
                    return OK;
                }))
                .then(literal("reload").executes(c -> {
                    module.normalizeFilters();
                    c.getSource().getEmbed()
                            .title("Filters normalized")
                            .description("Filter lists were normalized in memory.")
                            .addField("Map ID filter", module.getMapIdFilterSummary(), false)
                            .addField("Name filter", module.getNameKeywordFilterSummary(), false)
                            .primaryColor();
                    return OK;
                }))
                .then(literal("reset").executes(c -> {
                    module.resetFiltersToDefaults();
                    c.getSource().getEmbed()
                            .title("Filters reset")
                            .description("Filter settings were reset to their defaults.")
                            .addField("Map ID filter", module.getMapIdFilterSummary(), false)
                            .addField("Name filter", module.getNameKeywordFilterSummary(), false)
                            .primaryColor();
                    return OK;
                }))
                .then(buildMapIdCommands())
                .then(buildNameCommands());
    }

    private LiteralArgumentBuilder<CommandContext> buildMapIdCommands() {
        return literal("mapid")
                .executes(c -> {
                    fillMapIdFilter(c);
                    return OK;
                })
                .then(literal("list").executes(c -> {
                    fillMapIdFilter(c);
                    return OK;
                }))
                .then(literal("add").then(argument("id", IntegerArgumentType.integer(0)).executes(c -> {
                    int mapId = IntegerArgumentType.getInteger(c, "id");
                    boolean added = module.addFilteredMapId(mapId);
                    c.getSource().getEmbed()
                            .title(added ? "Map ID added" : "Map ID unchanged")
                            .description(added
                                    ? "Added map ID " + mapId + " to the filter list."
                                    : "Map ID " + mapId + " is already in the filter list.")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("remove").then(argument("id", IntegerArgumentType.integer(0)).executes(c -> {
                    int mapId = IntegerArgumentType.getInteger(c, "id");
                    boolean removed = module.removeFilteredMapId(mapId);
                    c.getSource().getEmbed()
                            .title(removed ? "Map ID removed" : "Map ID not found")
                            .description(removed
                                    ? "Removed map ID " + mapId + " from the filter list."
                                    : "Map ID " + mapId + " was not in the filter list.")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("clear").executes(c -> {
                    boolean cleared = module.clearFilteredMapIds();
                    c.getSource().getEmbed()
                            .title(cleared ? "Map ID list cleared" : "Map ID list already empty")
                            .description(cleared
                                    ? "Removed all map IDs from the filter list."
                                    : "There were no map IDs to remove.")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("toggle").then(argument("toggle", toggle()).executes(c -> {
                    boolean enabled = getToggle(c, "toggle");
                    module.setMapIdFilterEnabled(enabled);
                    c.getSource().getEmbed()
                            .title("Map ID filter " + toggleStrCaps(enabled))
                            .description(module.getMapIdFilterSummary())
                            .primaryColor();
                    return OK;
                })))
                .then(buildModeCommand(true));
    }

    private LiteralArgumentBuilder<CommandContext> buildNameCommands() {
        return literal("name")
                .executes(c -> {
                    fillNameFilter(c);
                    return OK;
                })
                .then(literal("list").executes(c -> {
                    fillNameFilter(c);
                    return OK;
                }))
                .then(literal("add").then(argument("keyword", string()).executes(c -> {
                    String keyword = getString(c, "keyword");
                    boolean added = module.addNameKeyword(keyword);
                    c.getSource().getEmbed()
                            .title(added ? "Keyword added" : "Keyword unchanged")
                            .description(added
                                    ? "Added keyword `" + keyword.trim() + "` to the name filter list."
                                    : "That keyword was blank or already present.")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("remove").then(argument("keyword", string()).executes(c -> {
                    String keyword = getString(c, "keyword");
                    boolean removed = module.removeNameKeyword(keyword);
                    c.getSource().getEmbed()
                            .title(removed ? "Keyword removed" : "Keyword not found")
                            .description(removed
                                    ? "Removed keyword `" + keyword.trim() + "` from the name filter list."
                                    : "That keyword was not in the name filter list.")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("clear").executes(c -> {
                    boolean cleared = module.clearNameKeywords();
                    c.getSource().getEmbed()
                            .title(cleared ? "Name keyword list cleared" : "Name keyword list already empty")
                            .description(cleared
                                    ? "Removed all name keywords from the filter list."
                                    : "There were no name keywords to remove.")
                            .primaryColor();
                    return OK;
                }))
                .then(literal("toggle").then(argument("toggle", toggle()).executes(c -> {
                    boolean enabled = getToggle(c, "toggle");
                    module.setNameKeywordFilterEnabled(enabled);
                    c.getSource().getEmbed()
                            .title("Name filter " + toggleStrCaps(enabled))
                            .description(module.getNameKeywordFilterSummary())
                            .primaryColor();
                    return OK;
                })))
                .then(literal("case").then(argument("toggle", toggle()).executes(c -> {
                    boolean enabled = getToggle(c, "toggle");
                    module.setCaseInsensitiveNameMatching(enabled);
                    c.getSource().getEmbed()
                            .title("Case-insensitive matching " + toggleStrCaps(enabled))
                            .description(enabled ? "Name matching now ignores case." : "Name matching is now case-sensitive.")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("partial").then(argument("toggle", toggle()).executes(c -> {
                    boolean enabled = getToggle(c, "toggle");
                    module.setPartialNameMatching(enabled);
                    c.getSource().getEmbed()
                            .title("Partial matching " + toggleStrCaps(enabled))
                            .description(enabled ? "Keywords now match anywhere in the map name." : "Keywords must now match the whole map name.")
                            .primaryColor();
                    return OK;
                })))
                .then(buildModeCommand(false));
    }

    private LiteralArgumentBuilder<CommandContext> buildModeCommand(boolean mapIdFilter) {
        return literal("mode")
                .then(literal("off").executes(c -> {
                    setFilterMode(mapIdFilter, MapFrameBreakerConfig.FilterMode.OFF);
                    respondWithModeChange(c, mapIdFilter, MapFrameBreakerConfig.FilterMode.OFF);
                    return OK;
                }))
                .then(literal("allowlist").executes(c -> {
                    setFilterMode(mapIdFilter, MapFrameBreakerConfig.FilterMode.ALLOWLIST);
                    respondWithModeChange(c, mapIdFilter, MapFrameBreakerConfig.FilterMode.ALLOWLIST);
                    return OK;
                }))
                .then(literal("blocklist").executes(c -> {
                    setFilterMode(mapIdFilter, MapFrameBreakerConfig.FilterMode.BLOCKLIST);
                    respondWithModeChange(c, mapIdFilter, MapFrameBreakerConfig.FilterMode.BLOCKLIST);
                    return OK;
                }));
    }

    private void setFilterMode(boolean mapIdFilter, MapFrameBreakerConfig.FilterMode mode) {
        if (mapIdFilter) {
            module.setMapIdFilterMode(mode);
        } else {
            module.setNameKeywordFilterMode(mode);
        }
    }

    private void respondWithModeChange(com.mojang.brigadier.context.CommandContext<CommandContext> context,
                                       boolean mapIdFilter,
                                       MapFrameBreakerConfig.FilterMode mode) {
        context.getSource().getEmbed()
                .title((mapIdFilter ? "Map ID" : "Name") + " filter mode updated")
                .description("Mode set to " + mode.name())
                .addField("Current filter", mapIdFilter ? module.getMapIdFilterSummary() : module.getNameKeywordFilterSummary(), false)
                .primaryColor();
    }

    private void fillDetailedStats(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        context.getSource().getEmbed()
                .title("Frame Breaker Stats")
                .description("Use `frames` for quick stats, `framebreaker filter show` for filter settings, and `framebreaker target info` for the current target.")
                .addField("Frames broken", module.getFramesBrokenCount(), true)
                .addField("Attempts", module.getTotalBreakAttemptsCount(), true)
                .addField("Successful breaks", module.getSuccessfulBreaksCount(), true)
                .addField("Blocks broken", module.getBlocksBrokenCount(), true)
                .addField("Broken tracked", module.getBrokenFrameCount(), true)
                .addField("Unreachable", module.getUnreachableFrameCount(), true)
                .addField("Current target", module.hasCurrentTarget() ? "YES" : "NO", true)
                .addField("Success rate", String.format("%.1f%%", module.getSuccessRatePercentage()), true)
                .addField("Map ID filter", module.getMapIdFilterSummary(), false)
                .addField("Name filter", module.getNameKeywordFilterSummary(), false)
                .primaryColor();
    }

    private void fillFilterSettings(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        context.getSource().getEmbed()
                .title("Frame Filter Settings")
                .description("Name filter is the main one for renamed maps. Map ID filter is optional and more advanced.")
                .addField("Map ID filter", module.getMapIdFilterSummary(), false)
                .addField("Name filter", module.getNameKeywordFilterSummary(), false)
                .addField("Case-insensitive names", toggleStr(module.isCaseInsensitiveNameMatching()), true)
                .addField("Partial name matching", toggleStr(module.isPartialNameMatching()), true)
                .addField("Quick commands", String.join("\n",
                        "framebreaker filter name add \"discord.gg\"",
                        "framebreaker filter name remove \"discord.gg\"",
                        "framebreaker filter name toggle on",
                        "framebreaker target info"
                ), false)
                .primaryColor();
    }

    private void fillMapIdFilter(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        context.getSource().getEmbed()
                .title("Map ID Filter")
                .description("Advanced option. Use this when you know exact map IDs you want to allow or block.")
                .addField("Enabled", toggleStr(module.isMapIdFilterEnabled()), true)
                .addField("Mode", module.getMapFilterModeName(), true)
                .addField("Map IDs", module.getFilteredMapIds().isEmpty() ? "None" : module.getFilteredMapIds().toString(), false)
                .addField("Commands", String.join("\n",
                        "framebreaker filter mapid add 1234",
                        "framebreaker filter mapid remove 1234",
                        "framebreaker filter mapid toggle on",
                        "framebreaker filter mapid mode allowlist"
                ), false)
                .primaryColor();
    }

    private void fillNameFilter(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        context.getSource().getEmbed()
                .title("Name Filter")
                .description("Main filter for renamed maps. Best for Discord ads, shop names, and similar text.")
                .addField("Enabled", toggleStr(module.isNameKeywordFilterEnabled()), true)
                .addField("Mode", module.getNameFilterModeName(), true)
                .addField("Case-insensitive", toggleStr(module.isCaseInsensitiveNameMatching()), true)
                .addField("Partial matching", toggleStr(module.isPartialNameMatching()), true)
                .addField("Keywords", module.getFilteredNameKeywords().isEmpty() ? "None" : module.getFilteredNameKeywords().stream().collect(Collectors.joining("\n")), false)
                .addField("Commands", String.join("\n",
                        "framebreaker filter name add \"discord.gg\"",
                        "framebreaker filter name remove \"discord.gg\"",
                        "framebreaker filter name toggle on",
                        "framebreaker filter name partial on"
                ), false)
                .primaryColor();
    }

    private void fillTargetInfo(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        FrameTargetSnapshot snapshot = module.getCurrentTargetSnapshot();
        if (snapshot == null) {
            context.getSource().getEmbed()
                    .title("Target Info")
                    .description("There is no active target.")
                    .primaryColor();
            return;
        }

        context.getSource().getEmbed()
                .title("Current Target")
                .description("This shows what the current frame contains and why it does or does not match the active filters.")
                .addField("Entity ID", snapshot.entityId(), true)
                .addField("Location", snapshot.location(), true)
                .addField("Distance", String.format("%.1f blocks", snapshot.distance()), true)
                .addField("Map ID", snapshot.mapId() == null ? "None" : snapshot.mapId(), true)
                .addField("Map name", snapshot.mapName() == null || snapshot.mapName().isBlank() ? "None" : snapshot.mapName(), false)
                .addField("Map ID filter pass", toggleStr(snapshot.passesMapIdFilter()), true)
                .addField("Name filter pass", toggleStr(snapshot.passesNameFilter()), true)
                .addField("Targetable", toggleStr(snapshot.shouldTarget()), true)
                .primaryColor();
    }

    private void fillFrameList(com.mojang.brigadier.context.CommandContext<CommandContext> context, boolean includeAll) {
        var broken = module.getBrokenFrameLocations(includeAll ? 25 : 10);
        var unreachable = module.getUnreachableFrameLocations(includeAll ? 25 : 10);

        context.getSource().getEmbed()
                .title("Frame Breaker Lists")
                .addField("Broken frames", broken.isEmpty() ? "None" : broken.stream().collect(Collectors.joining("\n")), false)
                .addField("Unreachable frames", unreachable.isEmpty() ? "None" : unreachable.stream().collect(Collectors.joining("\n")), false)
                .primaryColor();
    }
}
