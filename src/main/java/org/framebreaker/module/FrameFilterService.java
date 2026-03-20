package org.framebreaker.module;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.mc.item.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.framebreaker.MapFrameBreakerConfig;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FrameFilterService {
    private final MapFrameBreakerConfig config;

    FrameFilterService(MapFrameBreakerConfig config) {
        this.config = config;
        normalizeConfig();
    }

    void normalizeConfig() {
        config.normalize();
    }

    void resetToDefaults() {
        config.resetToDefaults();
        config.normalize();
    }

    ItemStack getFramedItem(EntityLiving frame) {
        return frame.getMetadataValue(8, MetadataTypes.ITEM, ItemStack.class);
    }

    boolean isFilledMap(ItemStack itemStack) {
        return itemStack != null && itemStack.getId() == ItemRegistry.FILLED_MAP.id();
    }

    Integer getMapIdFromFrame(EntityLiving frame) {
        ItemStack itemStack = getFramedItem(frame);
        if (!isFilledMap(itemStack)) {
            return null;
        }
        return itemStack.getDataComponentsOrEmpty().get(DataComponentTypes.MAP_ID);
    }

    String getMapNameFromFrame(EntityLiving frame) {
        ItemStack itemStack = getFramedItem(frame);
        if (!isFilledMap(itemStack)) {
            return null;
        }

        Component nameComponent = itemStack.getDataComponentsOrEmpty().get(DataComponentTypes.CUSTOM_NAME);
        if (nameComponent == null) {
            nameComponent = itemStack.getDataComponentsOrEmpty().get(DataComponentTypes.ITEM_NAME);
        }
        if (nameComponent == null) {
            return null;
        }

        String name = normalizeFilterText(extractComponentText(nameComponent));
        return name.isBlank() ? null : name;
    }

    boolean shouldTargetFrame(EntityLiving frame) {
        ItemStack itemStack = getFramedItem(frame);
        if (!isFilledMap(itemStack)) {
            return false;
        }

        Integer mapId = getMapIdFromFrame(frame);
        String mapName = getMapNameFromFrame(frame);
        return passesMapIdFilter(mapId) && passesNameKeywordFilter(mapName);
    }

    FrameTargetSnapshot buildSnapshot(EntityLiving frame, String location, double distance) {
        ItemStack itemStack = getFramedItem(frame);
        if (!isFilledMap(itemStack)) {
            return new FrameTargetSnapshot(frame.getEntityId(), location, distance, null, null, false, false, false);
        }

        Integer mapId = getMapIdFromFrame(frame);
        String mapName = getMapNameFromFrame(frame);
        boolean mapIdPass = passesMapIdFilter(mapId);
        boolean namePass = passesNameKeywordFilter(mapName);
        return new FrameTargetSnapshot(frame.getEntityId(), location, distance, mapId, mapName, mapIdPass, namePass, mapIdPass && namePass);
    }

    String getMapIdFilterSummary() {
        return buildFilterSummary(
                config.enableMapIdFilter,
                config.mapIdFilterMode,
                getSortedMapIds().isEmpty() ? "[]" : getSortedMapIds().toString()
        );
    }

    String getNameKeywordFilterSummary() {
        return buildFilterSummary(
                config.enableNameKeywordFilter,
                config.nameKeywordFilterMode,
                getSortedNameKeywords().isEmpty() ? "[]" : getSortedNameKeywords().toString()
        );
    }

    List<Integer> getSortedMapIds() {
        normalizeConfig();
        return new ArrayList<>(config.mapIds);
    }

    List<String> getSortedNameKeywords() {
        normalizeConfig();
        return new ArrayList<>(config.nameKeywords);
    }

    private String extractComponentText(Component component) {
        StringBuilder text = new StringBuilder();
        appendComponentText(component, text);
        return text.toString();
    }

    private void appendComponentText(Component component, StringBuilder text) {
        if (component instanceof TextComponent textComponent) {
            text.append(textComponent.content());
        }

        for (Component child : component.children()) {
            appendComponentText(child, text);
        }
    }

    private String normalizeFilterText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (config.caseInsensitiveNameMatching) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private boolean matchesNameKeyword(String mapName, String keyword) {
        if (mapName == null || keyword == null) {
            return false;
        }

        String normalizedKeyword = normalizeFilterText(keyword);
        if (normalizedKeyword.isBlank()) {
            return false;
        }

        if (config.partialNameMatching) {
            return mapName.contains(normalizedKeyword);
        }
        return mapName.equals(normalizedKeyword);
    }

    private boolean evaluateFilter(boolean enabled, MapFrameBreakerConfig.FilterMode mode, boolean matches) {
        if (!enabled || mode == MapFrameBreakerConfig.FilterMode.OFF) {
            return true;
        }

        return switch (mode) {
            case OFF -> true;
            case ALLOWLIST -> matches;
            case BLOCKLIST -> !matches;
        };
    }

    private boolean passesMapIdFilter(Integer mapId) {
        if (!config.enableMapIdFilter || config.mapIdFilterMode == MapFrameBreakerConfig.FilterMode.OFF || config.mapIds.isEmpty()) {
            return true;
        }

        boolean matches = mapId != null && config.mapIds.contains(mapId);
        return evaluateFilter(true, config.mapIdFilterMode, matches);
    }

    private boolean passesNameKeywordFilter(String mapName) {
        if (!config.enableNameKeywordFilter
                || config.nameKeywordFilterMode == MapFrameBreakerConfig.FilterMode.OFF
                || config.nameKeywords.isEmpty()) {
            return true;
        }

        for (String keyword : config.nameKeywords) {
            if (matchesNameKeyword(mapName, keyword)) {
                return evaluateFilter(true, config.nameKeywordFilterMode, true);
            }
        }

        return evaluateFilter(true, config.nameKeywordFilterMode, false);
    }

    private String buildFilterSummary(boolean enabled, MapFrameBreakerConfig.FilterMode mode, String values) {
        if (!enabled || mode == MapFrameBreakerConfig.FilterMode.OFF) {
            return "OFF";
        }

        return mode + " " + values;
    }
}
