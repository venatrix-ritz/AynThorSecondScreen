package com.exojosh.forge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Routes commands sent from the companion app to game actions for Forge 1.20.1.
 */
public class CommandDispatcherForge {

    private static final String BINDING_PREFIX = "BIND:";
    private static final Map<String, KeyMapping> COMMANDS = new HashMap<>();
    private static final ConcurrentLinkedQueue<KeyMapping> PENDING_RELEASE = new ConcurrentLinkedQueue<>();
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        Options options = client.options;

        COMMANDS.put("INVENTORY", options.keyInventory);
        COMMANDS.put("E", options.keyInventory);
        COMMANDS.put("DROP", options.keyDrop);
        COMMANDS.put("Q", options.keyDrop);
        COMMANDS.put("G", options.keyDrop);
        COMMANDS.put("SWAP", options.keySwapOffhand);
        COMMANDS.put("F", options.keySwapOffhand);
        COMMANDS.put("R", options.keySwapOffhand);
        COMMANDS.put("CHAT", options.keyChat);
        COMMANDS.put("T", options.keyChat);
        COMMANDS.put("USE", options.keyUse);
        COMMANDS.put("H", options.keyUse);
        COMMANDS.put("ATTACK", options.keyAttack);
        COMMANDS.put("K", options.keyAttack);
        COMMANDS.put("JUMP", options.keyJump);
        COMMANDS.put("SNEAK", options.keyShift);
        COMMANDS.put("SPRINT", options.keySprint);
        COMMANDS.put("TAB", options.keyPlayerList);
        COMMANDS.put("PLAYER_LIST", options.keyPlayerList);

        KeyMapping mapKey = findMapKey(options);
        if (mapKey != null) {
            COMMANDS.put("MAP", mapKey);
            COMMANDS.put("M", mapKey);
            System.out.println("[ThorHud] MAP/M resolves to key binding: " + mapKey.getName());
        } else {
            System.out.println("[ThorHud] No map key binding found; MAP/M will be ignored.");
        }

        initialized = true;
    }

    /**
     * Picks the key binding the MAP/M command presses. Any name containing "map" used to qualify and the first one in
     * options order won, which in a modpack with Xaero's can be its minimap settings or a zoom key rather than the
     * open-map key. An explicit open-map name is preferred; otherwise the first "map" name that is not an obvious
     * helper action.
     */
    private static KeyMapping findMapKey(Options options) {
        if (options.keyMappings == null) return null;
        KeyMapping fallback = null;
        for (KeyMapping k : options.keyMappings) {
            if (k == null || k.getName() == null) continue;
            String name = k.getName().toLowerCase();
            if (!name.contains("map")) continue;
            if (name.contains("open_map") || name.contains("openmap") || name.contains("worldmap")
                    || name.contains("world_map")) {
                return k;
            }
            if (fallback == null && !isMapHelperKey(name)) {
                fallback = k;
            }
        }
        return fallback;
    }

    private static boolean isMapHelperKey(String name) {
        return name.contains("setting") || name.contains("zoom") || name.contains("toggle")
                || name.contains("enlarge") || name.contains("waypoint") || name.contains("tracked")
                || name.contains("mipmap");
    }

    public static void tick() {
        init();
        KeyMapping toRelease;
        while ((toRelease = PENDING_RELEASE.poll()) != null) {
            toRelease.setDown(false);
        }
    }

    public static void dispatch(String code) {
        init();
        Minecraft client = Minecraft.getInstance();

        if (client.screen != null) {
            if ("INVENTORY".equals(code) || "E".equals(code) || "ESCAPE".equals(code) || "PAUSE".equals(code) || "CLOSE".equals(code)) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.closeContainer();
                    }
                    client.setScreen(null);
                });
                return;
            }
            System.out.println("[ThorHud] Ignoring command '" + code + "' -- a screen is open");
            return;
        }

        if ("ESCAPE".equals(code) || "PAUSE".equals(code)) {
            client.execute(() -> client.pauseGame(false));
            return;
        }

        if ("F3".equals(code) || "DEBUG".equals(code)) {
            client.execute(() -> {
                if (client.options != null) {
                    client.options.renderDebug = !client.options.renderDebug;
                }
            });
            return;
        }

        if ("F5".equals(code) || "PERSPECTIVE".equals(code)) {
            client.execute(() -> {
                if (client.options != null) {
                    client.options.setCameraType(client.options.getCameraType().cycle());
                }
            });
            return;
        }

        Integer slot = parseHotbarSlot(code);
        if (slot != null && client.options != null && slot >= 1 && slot <= client.options.keyHotbarSlots.length) {
            pressForOneTick(client.options.keyHotbarSlots[slot - 1]);
            return;
        }

        if (code.startsWith(BINDING_PREFIX)) {
            String id = code.substring(BINDING_PREFIX.length());
            KeyMapping binding = findBindingById(id);
            if (binding == null) {
                System.out.println("[ThorHud] No key binding with id: " + id);
                return;
            }
            pressForOneTick(binding);
            return;
        }

        KeyMapping binding = COMMANDS.get(code);
        if (binding == null) {
            System.out.println("[ThorHud] Unknown command code: " + code);
            return;
        }
        pressForOneTick(binding);
    }

    private static KeyMapping findBindingById(String id) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.keyMappings == null) return null;
        for (KeyMapping k : client.options.keyMappings) {
            if (k != null && k.getName().equals(id)) {
                return k;
            }
        }
        return null;
    }

    private static Integer parseHotbarSlot(String code) {
        if (code.length() != 1) return null;
        char c = code.charAt(0);
        if (c < '1' || c > '9') return null;
        return c - '0';
    }

    private static void pressForOneTick(KeyMapping binding) {
        KeyMapping.click(binding.getKey());
        binding.setDown(true);
        PENDING_RELEASE.add(binding);
    }
}
