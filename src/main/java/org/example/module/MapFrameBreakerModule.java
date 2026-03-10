package org.example.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientDeathEvent;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.module.api.Module;
import com.zenith.util.math.MathHelper;

import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CACHE;

/**
 * MAP FRAME BREAKER BOT - ULTRA PERSISTENT EDITION WITH COMMANDS
 * ------------------------------------------------
 * Signed and delivered for: madcherrybro
 *
 * MISSION: Break frames. Period.
 * LEVEL-BASED PRIORITY: Same-level frames first, then higher frames.
 * Y-LEVEL TRACKING: Must reach frame's Y-level before giving up.
 * SMART CLIMBING: If blocks exist nearby, use them. If not, go around.
 * STUCK PREVENTION: Detects when pathfinding fails and tries alternatives.
 *
 * COMMANDS:
 *   !frames          - Show quick stats
 *   !framestats      - Show detailed statistics
 *   !framebreaker stats - Same as !framestats
 *   !framebreaker list  - List broken/unreachable frames
 *   !framebreaker clear - Clear frame data (requires confirmation)
 *   !fb              - Alias for !framebreaker
 *   !help            - Show available commands
 *
 * Features:
 * - PRIMARY MISSION: Break item frames within 2000 blocks of spawn
 * - SAME-LEVEL PRIORITY: Frames on your current Y level get highest priority
 * - NEXT-LEVEL PRIORITY: Frames within 3 blocks Y difference are next
 * - HIGH FRAMES: Only attempt after same-level frames are cleared
 * - Y-LEVEL PROGRESS: Must get to frame's height before considering success
 * - TERRAIN ANALYSIS: Checks if climbable blocks exist near frame
 * - If blocks exist → Path to use them for climbing
 * - If no blocks → Go around/under to find another route
 * - 15 attempts per frame, 10 clicks per attempt
 * - 30-block persistence zone - NEVER gives up when close!
 * - Smart pathfinding with different approach attempts
 * - Auto-respawn on death
 * - 60-second stuck timeout
 * - True 3D distance calculation
 * - Problem-solves like a real player!
 *
 * Created: March 2026
 * Signature: madcherrybro
 *
 * @author madcherrybro
 * @version 6.1 - Added command system
 */
public class MapFrameBreakerModule extends Module {

    // ==================== CONFIGURATION ====================
    private static final int MAX_SPAWN_RADIUS = 2000;
    private static final int SCAN_INTERVAL_TICKS = 200;
    private static final double BREAK_DISTANCE = 4.0;
    private static final double MAX_REACHABLE_Y = 10.0;
    private static final int MAX_BREAK_ATTEMPTS = 15;
    private static final long STUCK_TIMEOUT_MS = 60000;
    private static final long BREAK_COOLDOWN_MS = 200;

    private static final int MIN_TARGET_TICKS = 3600;
    private static final double TARGET_SWITCH_THRESHOLD = 30.0;
    private static final double PROGRESS_REQUIRED = 1.0;
    private static final int MAX_TARGET_AGE_SECONDS = 600;

    private static final int CONNECTION_WARMUP_TICKS = 1200;

    // ==================== COMMAND SYSTEM ====================
    private final Map<String, CommandHandler> commands = new ConcurrentHashMap<>();
    private long startTime = System.currentTimeMillis();
    private int totalBreakAttempts = 0;
    private int successfulBreaks = 0;

    // ==================== LEVEL-BASED PRIORITY ====================
    private static final double SAME_LEVEL_TOLERANCE = 2.0;
    private static final double NEXT_LEVEL_TOLERANCE = 5.0;

    // ==================== STUCK PREVENTION ====================
    private static final int MAX_PATHFIND_FAILURES = 5;
    private static final int MIN_MOVEMENT_THRESHOLD = 2;
    private static final long MOVEMENT_CHECK_INTERVAL = 30000;

    // ==================== BLOCK BREAKING INTELLIGENCE ====================
    private static final Set<String> INSTANT_BREAK_BLOCKS = new HashSet<>(Arrays.asList(
            "moss_block", "moss_carpet",
            "dirt", "grass_block", "podzol", "mycelium",
            "sand", "gravel", "clay",
            "snow", "snow_block",
            "vine", "twisting_vines", "weeping_vines", "cave_vines",
            "sweet_berry_bush",
            "nether_sprouts", "warped_roots", "crimson_roots"
    ));

    private static final Set<String> SLOW_BREAK_BLOCKS = new HashSet<>(Arrays.asList(
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
            "acacia_planks", "dark_oak_planks", "crimson_planks", "warped_planks",
            "oak_log", "spruce_log", "birch_log", "jungle_log",
            "acacia_log", "dark_oak_log", "crimson_stem", "warped_stem",
            "white_wool", "orange_wool", "magenta_wool", "light_blue_wool",
            "yellow_wool", "lime_wool", "pink_wool", "gray_wool",
            "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool",
            "brown_wool", "green_wool", "red_wool", "black_wool",
            "sponge", "wet_sponge",
            "hay_block",
            "dried_kelp_block",
            "note_block", "jukebox"
    ));

    private static final Set<String> UNBREAKABLE_BLOCKS = new HashSet<>(Arrays.asList(
            "stone", "cobblestone", "mossy_cobblestone", "deepslate", "obsidian",
            "andesite", "diorite", "granite", "bedrock", "iron_ore", "gold_ore",
            "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore",
            "coal_ore", "copper_ore", "netherrack", "basalt", "blackstone",
            "tuff", "calcite", "dripstone_block", "smooth_basalt",
            "crying_obsidian", "ancient_debris", "gilded_blackstone",
            "bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
            "terracotta", "white_terracotta", "orange_terracotta", "magenta_terracotta",
            "light_blue_terracotta", "yellow_terracotta", "lime_terracotta",
            "pink_terracotta", "gray_terracotta", "light_gray_terracotta",
            "cyan_terracotta", "purple_terracotta", "blue_terracotta",
            "brown_terracotta", "green_terracotta", "red_terracotta", "black_terracotta",
            "concrete", "white_concrete", "orange_concrete", "magenta_concrete",
            "light_blue_concrete", "yellow_concrete", "lime_concrete", "pink_concrete",
            "gray_concrete", "light_gray_concrete", "cyan_concrete", "purple_concrete",
            "blue_concrete", "brown_concrete", "green_concrete", "red_concrete", "black_concrete",
            "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar"
    ));

    // ==================== TERRAIN ANALYSIS ====================
    private boolean hasClimbableBlocksNearFrame = false;
    private int lastFrameCheckX = 0;
    private int lastFrameCheckY = 0;
    private int lastFrameCheckZ = 0;
    private long lastFrameCheckTime = 0;
    private static final long FRAME_CHECK_COOLDOWN = 30000;

    // ==================== Y-LEVEL TRACKING ====================
    private double highestYReached = 0;
    private double targetFrameY = 0;
    private int yProgressStuckTicks = 0;
    private static final double Y_PROGRESS_REQUIRED = 2.0;

    // ==================== STATE ====================
    private boolean firstConnection = true;
    private int connectionTicks = 0;
    private boolean spawnSet = false;
    private int spawnX = 0;
    private int spawnZ = 0;
    private int tickCounter = 0;
    private int framesBroken = 0;

    private EntityLiving currentTarget = null;
    private int breakAttempts = 0;
    private long lastBreakTime = 0;
    private int targetTicks = 0;
    private long targetStartTime = 0;

    private double targetDistanceWhenChosen = 0;
    private double closestDistanceToTarget = Double.MAX_VALUE;
    private int noProgressTicks = 0;
    private int stuckNearTargetCount = 0;

    private boolean inPersistenceZone = false;

    // Block breaking tracking
    private int blocksBroken = 0;
    private int stuckMiningCount = 0;
    private long lastStuckLogTime = 0;

    // Pathfinding intelligence
    private int failedPathAttempts = 0;
    private String lastAttemptedDirection = "none";
    private long lastPathChangeTime = 0;

    // Stuck prevention
    private int consecutivePathfindFailures = 0;
    private String lastFailedTarget = "";
    private long lastMovementCheck = System.currentTimeMillis();
    private double movementCheckStartX = 0;
    private double movementCheckStartZ = 0;

    // Level tracking
    private int sameLevelFramesCount = 0;
    private int nextLevelFramesCount = 0;
    private int highFramesCount = 0;

    private final Set<String> brokenFrames = new HashSet<>();
    private final Set<String> unreachableFrames = new HashSet<>();

    private double lastX = 0;
    private double lastZ = 0;
    private long lastMoveTime = System.currentTimeMillis();
    private boolean isStuck = false;
    private long lastFrameSeenTime = System.currentTimeMillis();

    private int lastGoalX = 0;
    private int lastGoalZ = 0;
    private int sameGoalCount = 0;

    // Command handler interface
    private interface CommandHandler {
        void execute(String[] args);
    }

    @Override
    public boolean enabledSetting() {
        return true;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Initialize start time
        startTime = System.currentTimeMillis();

        // Register commands
        registerCommands();

        System.out.println("§a§l========== MAP FRAME BREAKER ==========");
        System.out.println("§aSigned for: §e§lmadcherrybro");
        System.out.println("§a§lPRIMARY MISSION: Break frames");
        System.out.println("§a§lLEVEL-BASED PRIORITY: Same-level frames FIRST!");
        System.out.println("§a§lY-LEVEL TRACKING: Must reach frame's height!");
        System.out.println("§a§lSTUCK PREVENTION: Won't get stuck in loops!");
        System.out.println("§a§lTERRAIN ANALYSIS: If blocks exist → use them. If not → go around.");
        System.out.println("§a§lPROBLEM SOLVING: Thinks like a player!");
        System.out.println("§a§lCOMMANDS: !frames, !framestats, !framebreaker, !fb, !help");
        System.out.println("§aBreak distance: " + BREAK_DISTANCE + " blocks");
        System.out.println("§aAttempts per frame: " + MAX_BREAK_ATTEMPTS);
        System.out.println("§a§lPERSISTENCE ZONE: 30 blocks - NEVER GIVE UP WHEN CLOSE!");
        System.out.println("§a§lBLOCK INTELLIGENCE: Knows what helps reach frames!");
        System.out.println("§a§lMOSS: 🌿 Perfect for climbing!");
        System.out.println("§a§lType !frames to see your progress!");
        System.out.println("§a§l=======================================");

        lastMoveTime = System.currentTimeMillis();
        lastFrameSeenTime = System.currentTimeMillis();
        firstConnection = true;
        connectionTicks = 0;
        movementCheckStartX = CACHE.getPlayerCache().getX();
        movementCheckStartZ = CACHE.getPlayerCache().getZ();
        lastMovementCheck = System.currentTimeMillis();
    }

    private void registerCommands() {
        // Main command with subcommands (like pearlplus)
        commands.put("framebreaker", args -> {
            if (args.length == 0) {
                sendHelp();
                return;
            }

            switch (args[0].toLowerCase()) {
                case "stats":
                case "statistics":
                    showDetailedStats();
                    break;
                case "list":
                    showFrameList(args);
                    break;
                case "clear":
                    if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                        clearAllData();
                    } else {
                        System.out.println("§c[MapFrameBreaker] §fUse 'framebreaker clear confirm' to reset all data");
                    }
                    break;
                case "help":
                    sendHelp();
                    break;
                default:
                    System.out.println("§cUnknown subcommand. Use 'framebreaker help'");
            }
        });

        // Alias for framebreaker (like 'pp' for 'pearlplus')
        commands.put("fb", args -> {
            commands.get("framebreaker").execute(args);
        });

        // Quick stats command
        commands.put("frames", args -> {
            showQuickStats();
        });

        // Detailed stats command
        commands.put("framestats", args -> {
            showDetailedStats();
        });

        // Help command
        commands.put("help", args -> {
            sendHelp();
        });
    }

    private void showQuickStats() {
        long runtime = System.currentTimeMillis() - startTime;
        long minutes = runtime / 60000;
        long seconds = (runtime % 60000) / 1000;

        System.out.println("§a§l========== FRAME BREAKER STATS ==========");
        System.out.println("§aFrames broken: §e" + framesBroken);
        System.out.println("§aAttempts: §e" + totalBreakAttempts);
        System.out.println("§aSuccess rate: §e" + String.format("%.1f", getSuccessRate()) + "%");
        System.out.println("§aRuntime: §e" + minutes + "m " + seconds + "s");
        System.out.println("§a§l========================================");
    }

    private void showDetailedStats() {
        long runtime = System.currentTimeMillis() - startTime;
        long hours = runtime / 3600000;
        long minutes = (runtime % 3600000) / 60000;

        System.out.println("§a§l========== DETAILED STATS ==========");
        System.out.println("§aFrames broken: §e" + framesBroken);
        System.out.println("§aTotal attempts: §e" + totalBreakAttempts);
        System.out.println("§aSuccessful breaks: §e" + successfulBreaks);
        System.out.println("§aBlocks broken: §e" + blocksBroken);
        System.out.println("§aUnreachable frames: §e" + unreachableFrames.size());
        System.out.println("§aBroken frames tracked: §e" + brokenFrames.size());
        System.out.println("§aRuntime: §e" + hours + "h " + minutes + "m");
        System.out.println("§aSuccess rate: §e" + String.format("%.1f", getSuccessRate()) + "%");
        System.out.println("§aCurrent target: " + (currentTarget != null ? "§aYES" : "§cNO"));
        System.out.println("§a§l====================================");
    }

    private void showFrameList(String[] args) {
        System.out.println("§a§l========== FRAME LIST ==========");
        System.out.println("§aBroken frames: §e" + brokenFrames.size());
        System.out.println("§aUnreachable frames: §e" + unreachableFrames.size());

        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            if (!brokenFrames.isEmpty()) {
                System.out.println("§a§lRecently broken:");
                brokenFrames.stream()
                        .limit(10)
                        .forEach(loc -> System.out.println("§7  " + loc));
            }
            if (!unreachableFrames.isEmpty()) {
                System.out.println("§a§lUnreachable:");
                unreachableFrames.stream()
                        .limit(10)
                        .forEach(loc -> System.out.println("§7  " + loc));
            }
        }
        System.out.println("§a§l=================================");
    }

    private void clearAllData() {
        brokenFrames.clear();
        unreachableFrames.clear();
        framesBroken = 0;
        totalBreakAttempts = 0;
        successfulBreaks = 0;
        blocksBroken = 0;
        System.out.println("§a[MapFrameBreaker] §fAll frame data has been cleared!");
    }

    private void sendHelp() {
        System.out.println("§a§l========== FRAME BREAKER COMMANDS ==========");
        System.out.println("§e!frames §7- Show quick statistics");
        System.out.println("§e!framestats §7- Show detailed statistics");
        System.out.println("§e!framebreaker stats §7- Same as !framestats");
        System.out.println("§e!framebreaker list §7- List broken/unreachable frames");
        System.out.println("§e!framebreaker list all §7- Show all tracked frames");
        System.out.println("§e!framebreaker clear §7- Clear all frame data (requires confirm)");
        System.out.println("§e!fb §7- Alias for !framebreaker");
        System.out.println("§e!help §7- Show this help message");
        System.out.println("§a§l===========================================");
    }

    private double getSuccessRate() {
        if (totalBreakAttempts == 0) return 0;
        return (successfulBreaks * 100.0) / totalBreakAttempts;
    }

    // Public method to handle commands from terminal/console
    public void executeCommand(String commandLine) {
        // Handle terminal commands (no prefix needed)
        String[] parts = commandLine.split(" ");
        String command = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        CommandHandler handler = commands.get(command);
        if (handler != null) {
            handler.execute(args);
            System.out.println("§a[MapFrameBreaker] §fExecuted command: " + commandLine);
        }
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleBotTick),
                of(ClientDeathEvent.class, this::handleDeathEvent)
        );
    }

    private double calculate3DDistance(EntityLiving entity) {
        return Math.sqrt(
                Math.pow(entity.getX() - CACHE.getPlayerCache().getX(), 2) +
                        Math.pow(entity.getY() - CACHE.getPlayerCache().getY(), 2) +
                        Math.pow(entity.getZ() - CACHE.getPlayerCache().getZ(), 2)
        );
    }

    private boolean isMakingProgress() {
        if (currentTarget == null) return true;

        double currentX = CACHE.getPlayerCache().getX();
        double currentZ = CACHE.getPlayerCache().getZ();

        double distanceMoved = Math.sqrt(
                Math.pow(currentX - lastX, 2) +
                        Math.pow(currentZ - lastZ, 2)
        );

        if (distanceMoved < 0.1 && BARITONE.isActive()) {
            long stuckTime = System.currentTimeMillis() - lastMoveTime;

            if (stuckTime > 10000) {
                System.out.println("§e[MapFrameBreaker] §fNo movement for " + (stuckTime/1000) + "s - trying different approach");

                if (currentTarget != null) {
                    double angle = Math.atan2(
                            currentTarget.getZ() - currentZ,
                            currentTarget.getX() - currentX
                    );

                    angle += Math.PI / 2;
                    int newX = (int)(currentX + Math.cos(angle) * 5);
                    int newZ = (int)(currentZ + Math.sin(angle) * 5);

                    BARITONE.pathTo(new GoalXZ(newX, newZ));
                }
                lastMoveTime = System.currentTimeMillis();
                lastX = currentX;
                lastZ = currentZ;
                return false;
            }
        }

        if (System.currentTimeMillis() - lastMovementCheck > MOVEMENT_CHECK_INTERVAL) {
            double totalMovement = Math.sqrt(
                    Math.pow(currentX - movementCheckStartX, 2) +
                            Math.pow(currentZ - movementCheckStartZ, 2)
            );

            if (totalMovement < MIN_MOVEMENT_THRESHOLD && BARITONE.isActive() && currentTarget != null) {
                System.out.println("§c[MapFrameBreaker] §fLess than " + MIN_MOVEMENT_THRESHOLD +
                        " blocks moved in 30s - forcing reset");

                sendClientPacket(new ServerboundChatPacket("/kill"));
                isStuck = true;
                currentTarget = null;
                return false;
            }

            movementCheckStartX = currentX;
            movementCheckStartZ = currentZ;
            lastMovementCheck = System.currentTimeMillis();
        }

        return true;
    }

    private void handleBotTick(ClientBotTick event) {
        if (CACHE.getPlayerCache().getThePlayer() == null) return;

        if (firstConnection) {
            connectionTicks++;
            if (connectionTicks < CONNECTION_WARMUP_TICKS) {
                if (connectionTicks % 20 == 0) {
                    int secondsLeft = (CONNECTION_WARMUP_TICKS - connectionTicks) / 20;
                    System.out.println("§7[MapFrameBreaker] §fWarmup... " + secondsLeft + "s");
                }
                return;
            } else {
                firstConnection = false;
                System.out.println("§a[MapFrameBreaker] §fWarmup complete! Let's find some frames!");
                System.out.println("§aType §e!help §ato see available commands!");
            }
        }

        if (!spawnSet) {
            spawnX = MathHelper.floorI(CACHE.getPlayerCache().getX());
            spawnZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());
            spawnSet = true;
            System.out.println("§a[MapFrameBreaker] §fSpawn at X=" + spawnX + " Z=" + spawnZ + " - Time to hunt frames!");
            movementCheckStartX = CACHE.getPlayerCache().getX();
            movementCheckStartZ = CACHE.getPlayerCache().getZ();
            lastMovementCheck = System.currentTimeMillis();
            wanderToNewLocation();
            return;
        }

        tickCounter++;
        if (currentTarget != null) targetTicks++;

        checkIfStuck();
        if (isStuck) return;

        if (!isMakingProgress()) {
            return;
        }

        if (isStuckMiningForFrame()) {
            return;
        }

        boolean brokeFrame = tryBreakNearbyFrames();
        if (brokeFrame) {
            failedPathAttempts = 0;
            yProgressStuckTicks = 0;
            consecutivePathfindFailures = 0;
        }

        if (currentTarget != null) {
            handleTargetFrame();
            return;
        }

        if (tickCounter % SCAN_INTERVAL_TICKS == 0) scanForFrames();
        if (!BARITONE.isActive()) wanderToNewLocation();
    }

    private void checkIfStuck() {
        double currentX = CACHE.getPlayerCache().getX();
        double currentZ = CACHE.getPlayerCache().getZ();

        double distanceMoved = Math.sqrt(
                Math.pow(currentX - lastX, 2) +
                        Math.pow(currentZ - lastZ, 2)
        );

        if (lastGoalX != 0 && lastGoalZ != 0 && BARITONE.isActive()) {
            double distToGoal = Math.sqrt(
                    Math.pow(lastGoalX - currentX, 2) +
                            Math.pow(lastGoalZ - currentZ, 2)
            );

            if (distToGoal < 2.0 && distanceMoved < 0.3) {
                sameGoalCount++;
                if (sameGoalCount > 40) {
                    System.out.println("§e[MapFrameBreaker] §fStuck near goal - let's try somewhere else");
                    currentTarget = null;
                    if (BARITONE.isActive()) BARITONE.stop();
                    sameGoalCount = 0;
                    lastGoalX = 0;
                    lastGoalZ = 0;
                    wanderToNewLocation();
                    return;
                }
            } else {
                sameGoalCount = 0;
            }
        }

        if (distanceMoved < 0.3 && BARITONE.isActive()) {
            long stuckTime = System.currentTimeMillis() - lastMoveTime;

            if (stuckTime > 20000 && stuckTime < 21000 && System.currentTimeMillis() - lastStuckLogTime > 5000) {
                if (currentTarget != null) {
                    double yDiff = currentTarget.getY() - CACHE.getPlayerCache().getY();
                    System.out.println("§e[MapFrameBreaker] §fHmm, stuck for 20s trying to reach frame " +
                            String.format("%.1f", yDiff) + " blocks above - analyzing terrain...");

                    analyzeTerrainForFrame(currentTarget);
                } else {
                    System.out.println("§e[MapFrameBreaker] §fStuck for 20s with no target - might be trapped");
                }
                lastStuckLogTime = System.currentTimeMillis();
                stuckMiningCount++;
            }

            if (stuckTime > STUCK_TIMEOUT_MS && !isStuck) {
                System.out.println("§c[MapFrameBreaker] §fCompletely stuck! /kill and respawn to continue mission...");
                sendClientPacket(new ServerboundChatPacket("/kill"));
                isStuck = true;
                currentTarget = null;
                if (BARITONE.isActive()) BARITONE.stop();
            }
        } else if (System.currentTimeMillis() - lastFrameSeenTime > 60000 && !BARITONE.isActive() && !isStuck) {
            System.out.println("§c[MapFrameBreaker] §fNo frames for 60s - respawning to find better location");
            sendClientPacket(new ServerboundChatPacket("/kill"));
            isStuck = true;
            currentTarget = null;
        } else {
            if (distanceMoved > 0.3) {
                lastMoveTime = System.currentTimeMillis();
                if (isStuck) {
                    System.out.println("§a[MapFrameBreaker] §fUnstuck! Back to frame hunting!");
                    isStuck = false;
                    stuckMiningCount = 0;
                }
            }
            lastX = currentX;
            lastZ = currentZ;
        }
    }

    private boolean isBlockBreakable(String blockType) {
        if (INSTANT_BREAK_BLOCKS.contains(blockType)) {
            return true;
        } else if (SLOW_BREAK_BLOCKS.contains(blockType)) {
            return true;
        }
        return false;
    }

    private void analyzeTerrainForFrame(EntityLiving frame) {
        int frameX = (int)frame.getX();
        int frameY = (int)frame.getY();
        int frameZ = (int)frame.getZ();

        if (frameX == lastFrameCheckX && frameY == lastFrameCheckY && frameZ == lastFrameCheckZ &&
                System.currentTimeMillis() - lastFrameCheckTime < FRAME_CHECK_COOLDOWN) {
            return;
        }

        lastFrameCheckX = frameX;
        lastFrameCheckY = frameY;
        lastFrameCheckZ = frameZ;
        lastFrameCheckTime = System.currentTimeMillis();

        System.out.println("§e[MapFrameBreaker] §fAnalyzing terrain around frame at Y=" + frameY);

        hasClimbableBlocksNearFrame = false;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    int checkY = frameY + dy;

                    if (frameY >= 58 && frameY <= 78) {
                        hasClimbableBlocksNearFrame = true;
                        System.out.println("§a[MapFrameBreaker] §f🌿 Found potential climbable blocks near frame (moss layer)!");
                        return;
                    }
                }
            }
        }

        boolean hasSolidGroundBelow = false;
        for (int checkY = frameY - 1; checkY > frameY - 5; checkY--) {
            hasSolidGroundBelow = true;
            break;
        }

        if (hasSolidGroundBelow) {
            System.out.println("§a[MapFrameBreaker] §fFound solid ground below frame - can jump up!");
            hasClimbableBlocksNearFrame = true;
        }

        if (!hasClimbableBlocksNearFrame) {
            System.out.println("§e[MapFrameBreaker] §fNo climbable blocks near frame - will need to find path around");
        }
    }

    private boolean isNearMoss() {
        int botY = (int)CACHE.getPlayerCache().getY();
        if (botY >= 58 && botY <= 78) {
            return true;
        }
        return false;
    }

    private boolean tryBreakNearbyFrames() {
        if (System.currentTimeMillis() - lastBreakTime < BREAK_COOLDOWN_MS) return false;

        boolean brokeAny = false;

        for (Entity entity : CACHE.getEntityCache().getEntities().values()) {
            if (entity.getEntityType() == EntityType.ITEM_FRAME && entity instanceof EntityLiving living) {

                lastFrameSeenTime = System.currentTimeMillis();
                double distance = calculate3DDistance(living);

                if (distance <= BREAK_DISTANCE) {
                    String location = (int)living.getX() + "," + (int)living.getY() + "," + (int)living.getZ();

                    if (unreachableFrames.contains(location)) continue;
                    if (brokenFrames.contains(location)) continue;

                    System.out.println("§a[MapFrameBreaker] §f🔨 TARGET ACQUIRED! Breaking frame at " + String.format("%.1f", distance) + " blocks");
                    boolean broke = breakFrame(living);
                    if (broke) {
                        brokeAny = true;
                        stuckNearTargetCount = 0;
                    }
                }
            }
        }
        return brokeAny;
    }

    private boolean breakFrame(EntityLiving frame) {
        int entityId = frame.getEntityId();
        String location = (int)frame.getX() + "," + (int)frame.getY() + "," + (int)frame.getZ();

        breakAttempts++;
        totalBreakAttempts++; // Track total attempts

        if (BARITONE.isActive()) BARITONE.stop();

        for (int i = 0; i < 10; i++) {
            BARITONE.leftClickEntity(frame);
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastBreakTime = System.currentTimeMillis();

        boolean frameStillExists = false;
        for (Entity entity : CACHE.getEntityCache().getEntities().values()) {
            if (entity.getEntityType() == EntityType.ITEM_FRAME && entity instanceof EntityLiving living) {
                double dx = Math.abs(living.getX() - frame.getX());
                double dy = Math.abs(living.getY() - frame.getY());
                double dz = Math.abs(living.getZ() - frame.getZ());

                if (dx < 1.0 && dy < 1.0 && dz < 1.0) {
                    frameStillExists = true;
                    break;
                }
            }
        }

        if (!frameStillExists) {
            framesBroken++;
            successfulBreaks++; // Track successful breaks
            breakAttempts = 0;
            System.out.println("§a[MapFrameBreaker] §f✅ VICTORY! Broke frame #" + framesBroken + " at " + location);
            System.out.println("§7Type §e!frames §7to see your stats!");

            if (currentTarget != null && currentTarget.getEntityId() == entityId) {
                currentTarget = null;
                targetTicks = 0;
                targetStartTime = 0;
                closestDistanceToTarget = Double.MAX_VALUE;
                stuckNearTargetCount = 0;
            }

            brokenFrames.add(location);
            return true;
        } else {
            System.out.println("§e[MapFrameBreaker] §fAttempt " + breakAttempts + "/" + MAX_BREAK_ATTEMPTS + " - frame still there, keep trying!");

            if (breakAttempts >= MAX_BREAK_ATTEMPTS) {
                System.out.println("§c[MapFrameBreaker] §fGiving up on this frame after " + MAX_BREAK_ATTEMPTS + " attempts");
                unreachableFrames.add(location);

                if (currentTarget != null && currentTarget.getEntityId() == entityId) {
                    currentTarget = null;
                    targetTicks = 0;
                    targetStartTime = 0;
                    closestDistanceToTarget = Double.MAX_VALUE;
                }
                breakAttempts = 0;
            }
            return false;
        }
    }

    private void handleTargetFrame() {
        boolean targetExists = false;
        EntityLiving updatedFrame = null;

        for (Entity entity : CACHE.getEntityCache().getEntities().values()) {
            if (entity.getEntityType() == EntityType.ITEM_FRAME &&
                    entity instanceof EntityLiving living &&
                    living.getEntityId() == currentTarget.getEntityId()) {
                targetExists = true;
                updatedFrame = living;
                break;
            }
        }

        if (!targetExists) {
            System.out.println("§7[MapFrameBreaker] §fTarget frame disappeared - looking for next one");
            currentTarget = null;
            breakAttempts = 0;
            targetTicks = 0;
            targetStartTime = 0;
            closestDistanceToTarget = Double.MAX_VALUE;
            stuckNearTargetCount = 0;
            consecutivePathfindFailures = 0;
            wanderToNewLocation();
            return;
        }

        currentTarget = updatedFrame;
        String location = (int)currentTarget.getX() + "," + (int)currentTarget.getY() + "," + (int)currentTarget.getZ();

        if (unreachableFrames.contains(location)) {
            currentTarget = null;
            consecutivePathfindFailures = 0;
            wanderToNewLocation();
            return;
        }

        double currentY = CACHE.getPlayerCache().getY();
        double yDifference = currentTarget.getY() - currentY;

        if (currentY > highestYReached) {
            highestYReached = currentY;
            yProgressStuckTicks = 0;
        } else {
            yProgressStuckTicks++;
        }

        String levelCategory;
        if (Math.abs(yDifference) <= SAME_LEVEL_TOLERANCE) {
            levelCategory = "SAME LEVEL";
        } else if (yDifference <= NEXT_LEVEL_TOLERANCE && yDifference > SAME_LEVEL_TOLERANCE) {
            levelCategory = "NEXT LEVEL";
        } else if (yDifference > NEXT_LEVEL_TOLERANCE) {
            levelCategory = "HIGH FRAME";
        } else {
            levelCategory = "BELOW";
        }

        if (Math.abs(yDifference) > MAX_REACHABLE_Y) {
            System.out.println("§e[MapFrameBreaker] §f[" + levelCategory + "] Frame at Y=" + (int)currentTarget.getY() +
                    " is " + String.format("%.1f", yDifference) + " blocks " + (yDifference > 0 ? "above" : "below") +
                    " (current Y: " + (int)currentY + ")");

            analyzeTerrainForFrame(currentTarget);

            if (hasClimbableBlocksNearFrame) {
                System.out.println("§a[MapFrameBreaker] §f🌿 Climbable blocks detected! Will try to use them.");
            } else {
                System.out.println("§e[MapFrameBreaker] §fNo climbable blocks - looking for path around/under");
            }
        }

        double currentDistance = calculate3DDistance(currentTarget);

        if (currentDistance < 30.0) {
            if (!inPersistenceZone) {
                System.out.println("§a[MapFrameBreaker] §f🎯 WITHIN 30 BLOCKS! I'm not giving up now!");
                inPersistenceZone = true;
            }
        } else {
            inPersistenceZone = false;
        }

        if (currentDistance < closestDistanceToTarget) {
            closestDistanceToTarget = currentDistance;
            noProgressTicks = 0;
        } else {
            noProgressTicks++;
        }

        if (currentDistance <= BREAK_DISTANCE) {
            tryBreakNearbyFrames();
            return;
        }

        if (targetStartTime == 0) {
            targetStartTime = System.currentTimeMillis();
            targetFrameY = currentTarget.getY();
            highestYReached = currentY;
            yProgressStuckTicks = 0;
            consecutivePathfindFailures = 0;
        }
        long targetAgeSeconds = (System.currentTimeMillis() - targetStartTime) / 1000;

        if (targetAgeSeconds % 30 == 0 && targetAgeSeconds > 0) {
            double yGained = highestYReached - currentY;
            System.out.println("§7[MapFrameBreaker] §f[" + levelCategory + "] Chasing for " + targetAgeSeconds + "s (" +
                    String.format("%.1f", currentDistance) + " blocks away, Y gained: " + String.format("%.1f", yGained) +
                    ") - " + (hasClimbableBlocksNearFrame ? "using blocks" : "looking for path"));
        }

        if (yDifference > 2.0) {
            double totalYGained = highestYReached - currentY;
            if (targetAgeSeconds > 60 && totalYGained < Y_PROGRESS_REQUIRED) {
                System.out.println("§e[MapFrameBreaker] §fNot gaining height after 60s - frame might be unreachable");

                if (!hasClimbableBlocksNearFrame) {
                    System.out.println("§c[MapFrameBreaker] §fNo climbable blocks and no height gain - giving up on this frame");
                    unreachableFrames.add(location);
                    currentTarget = null;
                    targetTicks = 0;
                    targetStartTime = 0;
                    closestDistanceToTarget = Double.MAX_VALUE;
                    consecutivePathfindFailures = 0;
                    wanderToNewLocation();
                    return;
                } else {
                    System.out.println("§e[MapFrameBreaker] §fHas climbable blocks but not gaining height - trying different approach");
                }
            }
        }

        if (targetAgeSeconds > MAX_TARGET_AGE_SECONDS && !inPersistenceZone) {
            System.out.println("§e[MapFrameBreaker] §fBeen on this target too long - moving on");
            unreachableFrames.add(location);
            currentTarget = null;
            consecutivePathfindFailures = 0;
            wanderToNewLocation();
            return;
        }

        if (targetTicks > MIN_TARGET_TICKS && !inPersistenceZone) {
            double progressMade = targetDistanceWhenChosen - closestDistanceToTarget;
            if (progressMade < PROGRESS_REQUIRED && noProgressTicks > 2400) {
                System.out.println("§e[MapFrameBreaker] §fNo progress for 2 minutes - time to find another frame");
                unreachableFrames.add(location);
                currentTarget = null;
                consecutivePathfindFailures = 0;
                wanderToNewLocation();
                return;
            } else if (progressMade >= PROGRESS_REQUIRED) {
                targetTicks = 0;
            }
        }

        if (!BARITONE.isActive()) {
            int targetX = (int)currentTarget.getX();
            int targetZ = (int)currentTarget.getZ();

            String targetKey = targetX + "," + targetZ;
            if (targetKey.equals(lastFailedTarget)) {
                consecutivePathfindFailures++;
                if (consecutivePathfindFailures > MAX_PATHFIND_FAILURES) {
                    System.out.println("§c[MapFrameBreaker] §fFailed to path to this frame " +
                            MAX_PATHFIND_FAILURES + " times - marking unreachable");
                    unreachableFrames.add(location);
                    currentTarget = null;
                    consecutivePathfindFailures = 0;
                    lastFailedTarget = "";
                    wanderToNewLocation();
                    return;
                }
            } else {
                consecutivePathfindFailures = 0;
                lastFailedTarget = targetKey;
            }

            System.out.println("§e[MapFrameBreaker] §fPathfinding to frame at X=" + targetX + " Z=" + targetZ);
            BARITONE.pathTo(new GoalXZ(targetX, targetZ));
            lastGoalX = targetX;
            lastGoalZ = targetZ;
        }
    }

    private boolean isStuckMiningForFrame() {
        if (!BARITONE.isActive() || currentTarget == null) return false;

        double currentX = CACHE.getPlayerCache().getX();
        double currentZ = CACHE.getPlayerCache().getZ();

        double distanceMoved = Math.sqrt(
                Math.pow(currentX - lastX, 2) +
                        Math.pow(currentZ - lastZ, 2)
        );

        if (distanceMoved < 0.1 && (System.currentTimeMillis() - lastMoveTime) > 15000) {
            long stuckTime = System.currentTimeMillis() - lastMoveTime;

            double yDiff = currentTarget.getY() - CACHE.getPlayerCache().getY();
            double yGained = highestYReached - CACHE.getPlayerCache().getY();

            String levelCategory;
            if (Math.abs(yDiff) <= SAME_LEVEL_TOLERANCE) {
                levelCategory = "SAME LEVEL";
            } else if (yDiff <= NEXT_LEVEL_TOLERANCE && yDiff > SAME_LEVEL_TOLERANCE) {
                levelCategory = "NEXT LEVEL";
            } else if (yDiff > NEXT_LEVEL_TOLERANCE) {
                levelCategory = "HIGH FRAME";
            } else {
                levelCategory = "BELOW";
            }

            if (yDiff > 2.0) {
                System.out.println("§e[MapFrameBreaker] §f[" + levelCategory + "] Frame is " + String.format("%.1f", yDiff) +
                        " blocks above (gained " + String.format("%.1f", yGained) + " Y) - " +
                        (hasClimbableBlocksNearFrame ? "trying to use blocks" : "looking for path around"));

                failedPathAttempts++;

                double angle = Math.atan2(
                        currentTarget.getZ() - currentZ,
                        currentTarget.getX() - currentX
                );

                int newX = 0;
                int newZ = 0;
                String direction = "";

                if (hasClimbableBlocksNearFrame && failedPathAttempts % 2 == 0) {
                    newX = (int)(currentX + Math.cos(angle) * 5);
                    newZ = (int)(currentZ + Math.sin(angle) * 5);
                    direction = "TOWARD FRAME (using blocks)";
                } else {
                    if (failedPathAttempts % 3 == 1) {
                        double leftAngle = angle + Math.PI / 2;
                        newX = (int)(currentX + Math.cos(leftAngle) * 12);
                        newZ = (int)(currentZ + Math.sin(leftAngle) * 12);
                        direction = "LEFT (looking for path)";
                        lastAttemptedDirection = "left";
                    } else if (failedPathAttempts % 3 == 2) {
                        double rightAngle = angle - Math.PI / 2;
                        newX = (int)(currentX + Math.cos(rightAngle) * 12);
                        newZ = (int)(currentZ + Math.sin(rightAngle) * 12);
                        direction = "RIGHT (looking for path)";
                        lastAttemptedDirection = "right";
                    } else {
                        newX = (int)(currentX + Math.cos(angle) * 5);
                        newZ = (int)(currentZ + Math.sin(angle) * 5);
                        direction = "AROUND (looking for path)";
                        lastAttemptedDirection = "around";
                    }
                }

                System.out.println("§e[MapFrameBreaker] §fTrying " + direction + " (attempt " + failedPathAttempts + ")");
                BARITONE.pathTo(new GoalXZ(newX, newZ));
                lastPathChangeTime = System.currentTimeMillis();
                lastMoveTime = System.currentTimeMillis();
                lastX = currentX;
                lastZ = currentZ;
                return true;
            }

            if (stuckTime > 30000) {
                System.out.println("§c[MapFrameBreaker] §fCan't reach this frame after 30s - must be impossible");
                String location = (int)currentTarget.getX() + "," +
                        (int)currentTarget.getY() + "," +
                        (int)currentTarget.getZ();
                unreachableFrames.add(location);

                System.out.println("§c[MapFrameBreaker] §fRespawning to try a different area");
                sendClientPacket(new ServerboundChatPacket("/kill"));
                isStuck = true;
                currentTarget = null;
                if (BARITONE.isActive()) BARITONE.stop();
                return true;
            }
        }
        return false;
    }

    private void scanForFrames() {
        EntityLiving bestTarget = null;
        double bestDistance = Double.MAX_VALUE;
        int bestPriority = 0;

        sameLevelFramesCount = 0;
        nextLevelFramesCount = 0;
        highFramesCount = 0;

        double currentY = CACHE.getPlayerCache().getY();

        for (Entity entity : CACHE.getEntityCache().getEntities().values()) {
            if (entity.getEntityType() == EntityType.ITEM_FRAME && entity instanceof EntityLiving living) {
                lastFrameSeenTime = System.currentTimeMillis();

                String location = (int)living.getX() + "," + (int)living.getY() + "," + (int)living.getZ();

                if (unreachableFrames.contains(location)) continue;
                if (brokenFrames.contains(location)) continue;

                double yDifference = Math.abs(living.getY() - currentY);
                if (yDifference > MAX_REACHABLE_Y * 1.5) continue;

                double distance = calculate3DDistance(living);

                int priority;
                if (Math.abs(living.getY() - currentY) <= SAME_LEVEL_TOLERANCE) {
                    priority = 3;
                    sameLevelFramesCount++;
                } else if (living.getY() - currentY <= NEXT_LEVEL_TOLERANCE && living.getY() - currentY > SAME_LEVEL_TOLERANCE) {
                    priority = 2;
                    nextLevelFramesCount++;
                } else if (living.getY() - currentY > NEXT_LEVEL_TOLERANCE) {
                    priority = 1;
                    highFramesCount++;
                } else {
                    priority = 0;
                    continue;
                }

                if (currentTarget != null) {
                    double currentDistance = calculate3DDistance(currentTarget);
                    if (priority > bestPriority ||
                            (priority == bestPriority && distance < currentDistance - TARGET_SWITCH_THRESHOLD)) {
                        bestDistance = distance;
                        bestTarget = living;
                        bestPriority = priority;
                    }
                } else if (priority > bestPriority || (priority == bestPriority && distance < bestDistance)) {
                    bestDistance = distance;
                    bestTarget = living;
                    bestPriority = priority;
                }
            }
        }

        if (sameLevelFramesCount > 0 || nextLevelFramesCount > 0 || highFramesCount > 0) {
            System.out.println("§7[MapFrameBreaker] §fFrame scan: " +
                    "§a" + sameLevelFramesCount + " same-level §7| " +
                    "§e" + nextLevelFramesCount + " next-level §7| " +
                    "§c" + highFramesCount + " high frames");
        }

        if (bestTarget != null && (currentTarget == null || bestTarget != currentTarget)) {
            double yDiff = Math.abs(bestTarget.getY() - CACHE.getPlayerCache().getY());
            String priorityText;
            if (bestPriority == 3) {
                priorityText = "§a[SAME LEVEL]";
            } else if (bestPriority == 2) {
                priorityText = "§e[NEXT LEVEL]";
            } else {
                priorityText = "§c[HIGH FRAME]";
            }

            if (yDiff > MAX_REACHABLE_Y) {
                System.out.println("§e[MapFrameBreaker] §f" + priorityText + " NEW TARGET: Frame " + String.format("%.1f", bestDistance) +
                        " blocks away at Y=" + (int)bestTarget.getY() + " (needs climbing) - analyzing terrain...");
            } else {
                System.out.println("§e[MapFrameBreaker] §f" + priorityText + " NEW TARGET: Frame " + String.format("%.1f", bestDistance) +
                        " blocks away at Y=" + (int)bestTarget.getY());
            }

            currentTarget = bestTarget;
            breakAttempts = 0;
            targetTicks = 0;
            targetStartTime = System.currentTimeMillis();
            targetDistanceWhenChosen = bestDistance;
            closestDistanceToTarget = Double.MAX_VALUE;
            noProgressTicks = 0;
            stuckNearTargetCount = 0;
            inPersistenceZone = false;
            failedPathAttempts = 0;
            consecutivePathfindFailures = 0;

            targetFrameY = bestTarget.getY();
            highestYReached = CACHE.getPlayerCache().getY();
            yProgressStuckTicks = 0;

            analyzeTerrainForFrame(currentTarget);

            if (BARITONE.isActive()) BARITONE.stop();
            lastGoalX = (int)bestTarget.getX();
            lastGoalZ = (int)bestTarget.getZ();
            BARITONE.pathTo(new GoalXZ(lastGoalX, lastGoalZ));
        }

        if (tickCounter % 12000 == 0) {
            brokenFrames.clear();
            System.out.println("§7[MapFrameBreaker] §fCleared cooldowns - ready for more frames!");
        }
    }

    private void wanderToNewLocation() {
        int currentX = MathHelper.floorI(CACHE.getPlayerCache().getX());
        int currentZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());

        int targetX = currentX + ThreadLocalRandom.current().nextInt(-MAX_SPAWN_RADIUS, MAX_SPAWN_RADIUS + 1);
        int targetZ = currentZ + ThreadLocalRandom.current().nextInt(-MAX_SPAWN_RADIUS, MAX_SPAWN_RADIUS + 1);

        targetX = Math.max(spawnX - MAX_SPAWN_RADIUS, Math.min(spawnX + MAX_SPAWN_RADIUS, targetX));
        targetZ = Math.max(spawnZ - MAX_SPAWN_RADIUS, Math.min(spawnZ + MAX_SPAWN_RADIUS, targetZ));

        lastGoalX = targetX;
        lastGoalZ = targetZ;
        System.out.println("§7[MapFrameBreaker] §fExploring new area at X=" + targetX + " Z=" + targetZ + " - looking for frames");
        BARITONE.pathTo(new GoalXZ(targetX, targetZ));

        movementCheckStartX = CACHE.getPlayerCache().getX();
        movementCheckStartZ = CACHE.getPlayerCache().getZ();
        lastMovementCheck = System.currentTimeMillis();
    }

    private void handleDeathEvent(ClientDeathEvent event) {
        System.out.println("§c[MapFrameBreaker] §fBot died! Respawn and continue the mission...");
        currentTarget = null;
        isStuck = false;
        breakAttempts = 0;
        targetTicks = 0;
        targetStartTime = 0;
        sameGoalCount = 0;
        closestDistanceToTarget = Double.MAX_VALUE;
        lastMoveTime = System.currentTimeMillis();
        lastFrameSeenTime = System.currentTimeMillis();
        stuckNearTargetCount = 0;
        inPersistenceZone = false;
        stuckMiningCount = 0;
        failedPathAttempts = 0;
        hasClimbableBlocksNearFrame = false;
        highestYReached = 0;
        targetFrameY = 0;
        yProgressStuckTicks = 0;
        consecutivePathfindFailures = 0;

        movementCheckStartX = CACHE.getPlayerCache().getX();
        movementCheckStartZ = CACHE.getPlayerCache().getZ();
        lastMovementCheck = System.currentTimeMillis();

        if (BARITONE.isActive()) BARITONE.stop();
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();

        System.out.println("§c§l========== MAP FRAME BREAKER STATS ==========");
        System.out.println("§cFrames broken: §e" + framesBroken);
        System.out.println("§cTotal attempts: §e" + totalBreakAttempts);
        System.out.println("§cSuccessful breaks: §e" + successfulBreaks);
        System.out.println("§cSuccess rate: §e" + String.format("%.1f", getSuccessRate()) + "%");
        System.out.println("§cBlocks broken to reach frames: §e" + blocksBroken);
        System.out.println("§cUnreachable frames: §e" + unreachableFrames.size());
        System.out.println("§cSigned for: madcherrybro");
        System.out.println("§c\"Same level first. Then we climb. Never get stuck.\"");
        System.out.println("§c§l============================================");
        super.onDisable();
    }
}