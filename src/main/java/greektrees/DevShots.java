package greektrees;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;

/**
 * Dev only ({@code ./gradlew runClient -PdevShots}): once the player stands in the world, builds a row of every
 * sapling, potted sapling, ripe twig and date cluster and grows a date palm, a mulberry and a weeping willow, saves
 * screenshots/shot_details.png and shot_trees.png and quits. For looking at what a self-test cannot see: the cut-out
 * textures, the date cluster's model, the woods.
 */
final class DevShots {
    private static final String[] TWIGS = {"olive_twig", "fig_twig", "arbutus_twig", "black_mulberry_twig",
            "white_mulberry_twig", "red_mulberry_twig"};
    private static final String[] TWIG_LEAVES = {"azalea_leaves", "azalea_leaves", "mangrove_leaves", "jungle_leaves",
            "jungle_leaves", "jungle_leaves"};
    /** What is photographed: the file's name and where the player looks from. */
    private static final List<String[]> SHOTS = List.of(
            new String[]{"shot_details", "tp @p 1004.5 150.6 1000.5 0 0"},
            new String[]{"shot_fruit", "tp @p 1005.5 151.2 1004.0 0 -5"},
            new String[]{"shot_trees", "tp @p 1010.5 156 1004.5 0 0"});
    private static int tick, shot = -1;

    private DevShots() {
    }

    static void register() {
        if (System.getProperty("greektrees.shots") != null) {
            ClientTickEvents.END_CLIENT_TICK.register(DevShots::tick);
        }
    }

    private static void tick(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (minecraft.player == null || server == null || minecraft.getOverlay() != null) {
            return;
        }
        minecraft.options.hideGui = true;
        tick++;
        if (tick == 40) {
            run(server, "gamemode spectator @p", "time set noon", "weather clear", "tp @p 1010 152 1010");
        } else if (tick == 140) { // the chunks around are loaded by now
            build(server);
        } else if (tick > 140 && (tick - 140) % 120 == 20 && ++shot < SHOTS.size()) {
            run(server, SHOTS.get(shot)[1]);
        } else if (tick > 140 && (tick - 140) % 120 == 0 && shot >= 0) {
            Screenshot.grab(minecraft.gameDirectory, SHOTS.get(shot)[0] + ".png", minecraft.getMainRenderTarget(),
                    message -> { });
            if (shot == SHOTS.size() - 1) {
                minecraft.stop();
            }
        }
    }

    private static void build(MinecraftServer server) {
        for (int y = 180; y >= 140; y--) { // layer by layer: one fill takes 32768 blocks at most
            run(server, "fill 985 " + y + " 985 1045 " + y + " 1045 " + (y == 149 ? "grass_block" : "air"));
        }
        for (int i = 0; i < GreekTrees.SPECIES.size(); i++) {
            String name = GreekTrees.SPECIES.get(i).name();
            run(server, "setblock " + (1001 + i) + " 150 1007 greektrees:" + name + "_sapling",
                    "setblock " + (1001 + i) + " 150 1005 greektrees:potted_" + name + "_sapling");
        }
        for (int i = 0; i < TWIGS.length; i++) {
            run(server, "setblock " + (1001 + i) + " 153 1009 " + TWIG_LEAVES[i] + "[persistent=true]",
                    "setblock " + (1001 + i) + " 152 1009 greektrees:" + TWIGS[i] + "[stage=2]");
        }
        run(server, "fill 1008 150 1009 1008 153 1009 jungle_wood");
        for (int age = 0; age < 3; age++) {
            run(server, "setblock 1008 " + (151 + age) + " 1008 greektrees:date_cluster[age=" + age + ",facing=south]",
                    "setblock 1007 " + (151 + age) + " 1009 greektrees:date_cluster[age=" + age + ",facing=east]");
        }
        run(server, "place feature greektrees:date_palm 998 150 1030", "place feature greektrees:mulberry 1010 150 1030",
                "place feature greektrees:weeping_willow 1023 150 1030");
    }

    private static void run(MinecraftServer server, String... commands) {
        server.execute(() -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            }
        });
    }
}
