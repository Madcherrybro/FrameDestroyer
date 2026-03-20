package org.framebreaker.module;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

public class FrameStatsCommand extends Command {
    private final MapFrameBreakerModule module;
    private final boolean detailed;

    public FrameStatsCommand(MapFrameBreakerModule module, boolean detailed) {
        this.module = module;
        this.detailed = detailed;
    }

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name(detailed ? "framestats" : "frames")
                .category(CommandCategory.MODULE)
                .description(detailed ? "Shows detailed frame breaker stats." : "Shows quick frame breaker stats.")
                .usageLines("")
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command(commandUsage().getName()).executes(c -> {
            c.getSource().getEmbed()
                    .title(detailed ? "Detailed Frame Stats" : "Frame Stats")
                    .addField("Frames broken", module.getFramesBrokenCount(), true)
                    .addField("Attempts", module.getTotalBreakAttemptsCount(), true)
                    .addField("Success rate", String.format("%.1f%%", module.getSuccessRatePercentage()), true)
                    .addField("Current target", module.hasCurrentTarget() ? "YES" : "NO", true)
                    .addField("Map ID filter", module.getMapIdFilterSummary(), false)
                    .addField("Name filter", module.getNameKeywordFilterSummary(), false)
                    .primaryColor();

            if (detailed) {
                c.getSource().getEmbed()
                        .addField("Successful breaks", module.getSuccessfulBreaksCount(), true)
                        .addField("Blocks broken", module.getBlocksBrokenCount(), true)
                        .addField("Broken tracked", module.getBrokenFrameCount(), true)
                        .addField("Unreachable", module.getUnreachableFrameCount(), true)
                        .addField("Map IDs", module.getFilteredMapIds().toString(), false)
                        .addField("Name keywords", module.getFilteredNameKeywords().toString(), false);
            }
            return OK;
        });
    }
}
