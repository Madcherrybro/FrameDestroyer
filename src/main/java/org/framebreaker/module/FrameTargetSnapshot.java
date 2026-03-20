package org.framebreaker.module;

public record FrameTargetSnapshot(
        int entityId,
        String location,
        double distance,
        Integer mapId,
        String mapName,
        boolean passesMapIdFilter,
        boolean passesNameFilter,
        boolean shouldTarget
) {
}
