package greektrees.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greektrees.GreekTrees;
import greektrees.HangingFruitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Greek trees. Each method rolls every random choice and builds the blocks, the same rules as
 * concept/species.py and concept/round3.py (see VARIATIONEN.md for the dice tables). The random source is the one vanilla hands to the
 * tree feature when a sapling grows, so every growth is different.
 */
public final class TreeShapes {
    /** The trunk of the mulberry and the willow: Minecraft 1.21.1 has no pale oak, acacia bark is grey too. */
    private static final Block PALE_WOOD = Blocks.ACACIA_WOOD;
    private static final int[][] SIDE4 = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[][] CORNER4 = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final int[][] ARM4 = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}};
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private TreeShapes() {
    }

    // ================================================================================================ cypress

    /**
     * Measured on the 32 hand-built cypresses of the Greek city. Layers above the 4-block trunk, bottom to top:
     * foot (plus, then plus with corners), body (full 3x3 with single blocks two out from the side centres),
     * transition (plus with corners), plus band, cap (centre and 1-3 sides), spire (centre only).
     */
    public static Shape cypress(RandomSource r) {
        // The builds keep their height tight: roll it first, then the bands, the body takes the rest.
        int height = weighted(r, new int[]{20, 21, 22, 23}, new int[]{3, 7, 65, 25});
        int foot = 2;
        boolean footCorners = true;
        int transition = 1, plus = 3, cap = 1, spire = 4, body = 7;
        for (int attempt = 0; attempt < 1000; attempt++) {
            int footKind = weighted(r, new int[]{0, 1, 2}, new int[]{85, 10, 5}); // plus+corners, plus+plus, plus
            foot = footKind == 2 ? 1 : 2;
            footCorners = footKind == 0;
            transition = weighted(r, new int[]{0, 1, 2}, new int[]{1, 8, 1});
            plus = weighted(r, new int[]{2, 3, 4}, new int[]{3, 5, 2});
            cap = weighted(r, new int[]{0, 1, 2}, new int[]{15, 50, 35});
            spire = r.nextBoolean() ? 4 : 5;
            body = height - 4 - foot - transition - plus - cap - spire;
            if (cap + spire > 6) {
                continue; // the tip must stay within leaf distance 6 of the log, which ends in the top plus layer
            }
            double accept = switch (body) {
                case 5, 8 -> 0.12;
                case 6 -> 1.0;
                case 7 -> 0.75;
                default -> 0;
            };
            if (r.nextDouble() < accept) {
                break;
            }
        }
        height = 4 + foot + body + transition + plus + cap + spire;

        Shape t = new Shape(r);
        int logTop = height - spire - cap - 1;
        for (int y = 0; y <= logTop; y++) {
            t.log(0, y, 0, Blocks.ACACIA_WOOD);
        }
        int y = 4;
        for (int i = 0; i < foot; i++, y++) {
            cypressLayer(t, y, true, i == 1 && footCorners ? cornerPick(r) : null, false, 0, null);
        }
        for (int i = 0; i < body; i++, y++) {
            double armChance = i == 0 ? 0.7 : i == body - 1 ? 0.55 : 0.86; // the strips thin out at both ends
            cypressLayer(t, y, true, new int[]{0, 1, 2, 3}, true, armChance, null);
        }
        for (int i = 0; i < transition; i++, y++) {
            cypressLayer(t, y, true, cornerPick(r), false, 0, null);
        }
        for (int i = 0; i < plus; i++, y++) {
            cypressLayer(t, y, true, null, false, 0, null);
        }
        int sides = 4;
        for (int i = 0; i < cap; i++, y++) {
            sides = randint(r, 1, Math.min(3, sides)); // the upper cap layer never has more sides than the lower
            cypressLayer(t, y, false, null, false, 0, sample(r, 4, sides));
        }
        for (int i = 0; i < spire; i++, y++) {
            t.leaf(0, y, 0, Blocks.AZALEA_LEAVES);
        }
        if (r.nextDouble() < 0.08) { // a lone leaf on the trunk just below the crown
            int[] s = SIDE4[r.nextInt(4)];
            t.leaf(s[0], 3, s[1], Blocks.AZALEA_LEAVES);
        }
        return t.finish();
    }

    private static void cypressLayer(Shape t, int y, boolean allSides, int[] corners, boolean arms, double armChance,
                                     int[] someSides) {
        t.leaf(0, y, 0, Blocks.AZALEA_LEAVES);
        if (allSides) {
            for (int[] s : SIDE4) {
                t.leaf(s[0], y, s[1], Blocks.AZALEA_LEAVES);
            }
        }
        if (someSides != null) {
            for (int n : someSides) {
                t.leaf(SIDE4[n][0], y, SIDE4[n][1], Blocks.AZALEA_LEAVES);
            }
        }
        if (corners != null) {
            for (int n : corners) {
                t.leaf(CORNER4[n][0], y, CORNER4[n][1], Blocks.AZALEA_LEAVES);
            }
        }
        if (arms) {
            for (int[] a : ARM4) {
                if (t.rng.nextDouble() < armChance) {
                    t.leaf(a[0], y, a[1], Blocks.AZALEA_LEAVES);
                }
            }
        }
    }

    private static int[] cornerPick(RandomSource r) {
        return sample(r, 4, randint(r, 1, 3));
    }

    // ================================================================================================ olive

    /**
     * Short gnarled trunk that forks into two or three stems with a hole between them. The foot, the roots lying on
     * the ground and the stems all point anywhere; one size roll scales the whole tree, and every stem gets its own
     * height and crown, so the crowns sit at different heights with gaps between them.
     */
    public static Shape olive(RandomSource r) {
        Shape t = new Shape(r);
        double size = uniform(r, 0.85, 1.3);
        t.log(0, 0, 0, Blocks.OAK_WOOD);
        t.log(0, 1, 0, Blocks.OAK_WOOD);
        for (int s : sample(r, 4, randint(r, 0, 2))) { // a thicker foot on 0-2 sides
            t.log(SIDE4[s][0], 0, SIDE4[s][1], Blocks.OAK_WOOD);
        }
        int roots = randint(r, 1, 3);
        for (int i = 0; i < roots; i++) { // roots lying on the ground, any direction
            double[] end = polar(uniform(r, 0, 360), uniform(r, 1.5, 3.2));
            t.branch(new double[]{0, 0, 0}, new double[]{end[0], 0, end[1]}, Blocks.OAK_WOOD, null);
        }
        int n = weighted(r, new int[]{2, 3}, new int[]{60, 40});
        double a0 = uniform(r, 0, 360);
        List<double[]> crowns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double angle = a0 + i * 360.0 / n + uniform(r, -25, 25);
            int height = Math.max(3, (int) Math.round(uniform(r, 4.0, 6.5) * size));
            double[] d = polar(angle, uniform(r, 1.3, 2.6) * size);
            double kneeAt = uniform(r, 0.35, 0.7);
            double[] knee = {d[0] * kneeAt, uniform(r, 1.6, 2.8), d[1] * kneeAt};
            double[] top = {d[0], height, d[1]};
            t.path(new double[][]{{0, 1, 0}, knee, top}, Blocks.OAK_WOOD, Direction.Axis.Y);
            double[] o = polar(angle + uniform(r, -45, 45), uniform(r, 1.4, 2.6));
            t.branch(top, new double[]{d[0] + o[0], height + randint(r, 0, 1), d[1] + o[1]}, Blocks.OAK_WOOD, null);
            double[] c = {d[0] + o[0] * 0.5, height, d[1] + o[1] * 0.5};
            double crownR = uniform(r, 2.5, 3.9);
            double crownH = uniform(r, 1.3, 2.3);
            double lift = uniform(r, 1.0, 2.0);
            t.blob(c[0], c[1] + lift, c[2], crownR, crownH, crownR, Blocks.AZALEA_LEAVES, uniform(r, 0.2, 0.35), height);
            crowns.add(new double[]{c[0], c[1] + lift, c[2], height});
        }
        int extra = randint(r, 1, 3);
        for (int i = 0; i < extra; i++) { // small clouds around a crown, higher or lower than it
            double[] c = crowns.get(r.nextInt(n));
            double[] o = polar(uniform(r, 0, 360), uniform(r, 1.8, 3.0));
            double rad = uniform(r, 1.6, 2.6);
            t.blob(c[0] + o[0], c[1] + uniform(r, -0.8, 1.2), c[2] + o[1], rad, uniform(r, 1.0, 1.5), rad,
                    Blocks.AZALEA_LEAVES, 0.3, (int) c[3]);
        }
        return t.finish().hangUnder(randint(r, 4, 8), () -> fruit(GreekTrees.OLIVE_TWIG, r));
    }

    // ================================================================================================ fig

    /** Low and wider than tall, several grey stems straight from the ground, a leaf cloud on each, figs under it. */
    public static Shape fig(RandomSource r) {
        Shape t = new Shape(r);
        t.log(0, 0, 0, Blocks.ACACIA_WOOD);
        t.log(1, 0, 0, Blocks.ACACIA_WOOD);
        t.log(0, 0, 1, Blocks.ACACIA_WOOD);
        int n = randint(r, 4, 5);
        double a0 = uniform(r, 0, 360);
        for (int i = 0; i < n; i++) {
            double angle = a0 + i * 360.0 / n + uniform(r, -20, 20);
            double kneeReach = uniform(r, 2.0, 3.0);
            int kneeHeight = randint(r, 1, 2);
            double reach = uniform(r, 3.2, 4.2);
            int height = randint(r, 3, 4);
            double crownR = uniform(r, 2.5, 3.0);
            double[] k = polar(angle, kneeReach);
            double[] f = polar(angle, reach);
            t.path(new double[][]{{0.3, 0, 0.3}, {k[0], kneeHeight, k[1]}, {f[0], height, f[1]}}, Blocks.ACACIA_WOOD, null);
            t.blob(f[0], height + 0.6, f[1], crownR, 1.7, crownR, Blocks.AZALEA_LEAVES, 0.25, 2);
        }
        t.blob(0.3, 4.6, 0.3, 3.0, 1.6, 3.0, Blocks.AZALEA_LEAVES, 0.2, 3);
        return t.finish().hangUnder(randint(r, 4, 8), () -> fruit(GreekTrees.FIG_TWIG, r));
    }

    // ================================================================================================ strawberry tree

    /**
     * Thin stems like the branches of a vanilla acacia: each rise moves at most one block along one axis, so
     * neighbouring logs share an edge and no filler blocks thicken the bends.
     */
    public static Shape strawberryTree(RandomSource r) {
        Shape t = new Shape(r);
        int n = randint(r, 2, 3);
        int[] dirs;
        if (n == 2) {
            int d = r.nextInt(4);
            dirs = new int[]{d, (d + 2) % 4};
        } else {
            dirs = sample(r, 4, 3);
        }
        int trunk = randint(r, 2, 3);
        for (int y = 0; y < trunk; y++) {
            t.log(0, y, 0, Blocks.STRIPPED_ACACIA_WOOD);
        }
        for (int d : dirs) {
            int height = randint(r, 6, 8);
            int steps = height - trunk + 1;
            int reach = randint(r, 2, Math.min(4, steps - 1));
            boolean[] outwards = new boolean[steps];
            outwards[0] = outwards[1] = true; // the stems part at once instead of forming a block
            for (int k : sample(r, steps - 2, reach - 2)) {
                outwards[k + 2] = true;
            }
            List<Integer> free = new ArrayList<>();
            for (int k = 1; k < steps; k++) {
                if (!outwards[k]) {
                    free.add(k);
                }
            }
            int twist = free.isEmpty() ? 0 : r.nextInt(3) - 1;
            int twistAt = twist != 0 ? free.get(r.nextInt(free.size())) : -1;
            int side = r.nextBoolean() ? -1 : 1;
            int sideLength = randint(r, 1, 2);
            int sideFrom = randint(r, 2, 3);
            double crownR = uniform(r, 2.0, 2.6);
            double sideCrownR = uniform(r, 1.6, 2.0);

            int dx = SIDE4[d][0], dz = SIDE4[d][1];
            int px = SIDE4[(d + 1) % 4][0], pz = SIDE4[(d + 1) % 4][1];
            int x = 0, y = trunk - 1, z = 0;
            List<BlockPos> pts = new ArrayList<>();
            for (int k = 0; k < steps; k++) {
                y++;
                if (outwards[k]) {
                    x += dx;
                    z += dz;
                } else if (k == twistAt) {
                    x += px * twist;
                    z += pz * twist;
                }
                t.log(x, y, z, Blocks.STRIPPED_ACACIA_WOOD);
                pts.add(new BlockPos(x, y, z));
            }
            BlockPos b = pts.get(pts.size() - sideFrom);
            for (int i = 0; i < sideLength; i++) {
                b = b.offset(px * side, 1, pz * side);
                t.log(b.getX(), b.getY(), b.getZ(), Blocks.STRIPPED_ACACIA_WOOD);
            }
            t.blob(x, y + 0.9, z, crownR, 1.4, crownR, Blocks.MANGROVE_LEAVES, 0.35, y);
            t.blob(b.getX(), b.getY() + 0.8, b.getZ(), sideCrownR, 1.2, sideCrownR, Blocks.MANGROVE_LEAVES, 0.35, b.getY());
        }
        return t.finish().hangUnder(randint(r, 4, 8), () -> fruit(GreekTrees.ARBUTUS_TWIG, r));
    }

    // ================================================================================================ mulberry

    /**
     * The shade tree of the village square (concept card 12, concept/round3.py): a short thick trunk without roots
     * on the ground. Most fork low into three to five limbs under a dense round dome, as wide as it is tall or wider; three
     * in ten are pollarded, a taller trunk ending in a knobbly fist of short thick limbs under a flat umbrella.
     * Mulberries hang under the crown, one kind per tree (black, white or red, a third each).
     */
    public static Shape mulberry(RandomSource r) {
        Shape t = new Shape(r);
        Block wood = PALE_WOOD, leaves = Blocks.JUNGLE_LEAVES;
        Block twig = GreekTrees.MULBERRY_TWIGS[r.nextInt(GreekTrees.MULBERRY_TWIGS.length)];
        boolean pollard = r.nextDouble() < 0.3;
        int th = pollard ? randint(r, 3, 4) : randint(r, 2, 3);
        for (int y = 0; y <= th; y++) {
            t.log(0, y, 0, wood);
        }
        double a0 = uniform(r, 0, 360);
        if (pollard) {
            for (int[] c : CARDINALS) {
                if (r.nextDouble() < 0.6) {
                    t.log(c[0], th, c[1], wood); // knobs round the top
                }
            }
            int limbs = randint(r, 3, 4);
            for (int i = 0; i < limbs; i++) {
                double[] e = polar(a0 + i * 90 + uniform(r, -23, 23), uniform(r, 1.8, 2.4));
                t.stem(new double[][]{{0, th, 0}, {e[0], th + 2, e[1]}}, wood);
                t.log(e[0], th + 3, e[1], wood);
            }
            double rad = uniform(r, 3.8, 4.4);
            t.blob(0, th + 4.2, 0, rad, 2.1, rad, leaves, 0.15, th + 3);
        } else {
            int n = randint(r, 3, 5);
            List<double[]> tips = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                double[] e = polar(a0 + i * 360.0 / n + uniform(r, -20, 20), uniform(r, 2.0, 3.0));
                double[] mid = {e[0] * 0.55, th + randint(r, 1, 2), e[1] * 0.55};
                double[] tip = {e[0], th + randint(r, 3, 4), e[1]};
                t.stem(new double[][]{{0, th, 0}, mid, tip}, wood);
                tips.add(tip);
            }
            int top = th + 4;
            t.stem(new double[][]{{0, th, 0}, {0, top - 1, 0}}, wood); // a leader keeps the dome's middle near wood
            double rad = uniform(r, 4.3, 5.2);
            t.blob(0, top + 0.5, 0, rad, 3.0, rad, leaves, 0.14, th + 1, 1.6, th + 2);
            for (double[] p : tips) {
                t.blob(p[0] * 1.2, p[1] + 0.8, p[2] * 1.2, 2.4, 1.8, 2.4, leaves, 0.3, th + 1);
            }
        }
        return t.finish().hangUnder(randint(r, 5, 9), () -> fruit(twig, r));
    }

    // ================================================================================================ weeping willow

    /**
     * By springs and streams (concept card 13, concept/round3.py): pale oak and mangrove leaves, a trunk with a soft
     * bend (now and then a C, see {@link #willowTrunk}), five to seven limbs that rise, run over an arch and come down
     * to the rim, wrapped in leaves; a rounded crown over the trunk's top, highest over the middle. Sizes vary from a short trunk under a small dome up to a taller trunk
     * under a wider, higher one. From the lowest leaf of the outer columns single strands of leaves hang, each apart
     * from the next: at the rim most reach down to one or two blocks above the ground, further in they are short, and
     * under the middle a room stays free; a few vines hang down the outside. The leaves are persistent (strands that
     * long would be too far from the wood to stay).
     */
    public static Shape weepingWillow(RandomSource r) {
        return willow(r, false);
    }

    /**
     * The big weeping willow, grown from four saplings in a square like a vanilla dark oak: a 2x2 trunk that bends the
     * same way, its moves spread over a few layers, taller and wider, seven to nine limbs, longer strands (also from
     * the outline between the limb tips) and more vines. The trunk stands on the square's 0, 0 corner.
     */
    public static Shape largeWeepingWillow(RandomSource r) {
        return willow(r, true);
    }

    private static Shape willow(RandomSource r, boolean big) {
        Shape t = new Shape(r);
        double g = r.nextDouble(); // the size: 0 the smallest, 1 a taller trunk under a wider and higher dome
        willowCrown(t, r, big, g, willowTrunk(t, r, big, g));
        return t;
    }

    /**
     * Everything a willow carries on its trunk (trunk = its height and where its top layer stands over the foot: th,
     * dx, dz): the leader, the dome, the limbs, the curtain and the vines, all over the trunk's top; finishes the
     * shape.
     */
    private static void willowCrown(Shape t, RandomSource r, boolean big, double g, int[] trunk) {
        Block wood = PALE_WOOD, leaves = Blocks.MANGROVE_LEAVES;
        int size = big ? 2 : 1, th = trunk[0];
        double cx = trunk[1] + (size - 1) / 2.0, cz = trunk[2] + (size - 1) / 2.0; // middle of the trunk top
        int top = th + (big ? randint(r, 5, 6) : randint(r, 3, 4)) + Shape.ip(g * 2);
        for (int y = th + 1; y <= top; y++) {
            for (int ox = 0; ox < size; ox++) {
                for (int oz = 0; oz < size; oz++) {
                    t.log(trunk[1] + ox, y, trunk[2] + oz, wood);
                }
            }
        }
        // rounded crown, highest over the middle
        double rad = (big ? uniform(r, 4.2, 4.8) : uniform(r, 3.0, 3.6)) + g * 1.8;
        t.blob(cx, top + 1.0, cz, rad, (big ? 3.0 : 2.4) + g * 0.8, rad, leaves, 0.2, top - 1);
        int n = (big ? randint(r, 7, 9) : randint(r, 5, 7)) + Shape.ip(g * 2);
        double a0 = uniform(r, 0, 360);
        double reach = (big ? uniform(r, 6.3, 7.3) : uniform(r, 4.3, 5.2)) + g * 2.5;
        int drop = (big ? 3 : 2) + (g > 0.5 ? 1 : 0);
        double[][] arch = {{0.0, th + 1}, {0.3, top}, {0.6, top}, {0.85, top - 1}, {1.0, top - drop}};
        int[][] wrap = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1},
                {0, 1, -1}};
        for (int i = 0; i < n; i++) {
            double angle = a0 + i * 360.0 / n + uniform(r, -14, 14);
            double length = reach + uniform(r, -0.5, 0.5);
            double[][] pts = new double[arch.length][];
            for (int k = 0; k < arch.length; k++) {
                double[] o = polar(angle, length * arch[k][0]);
                double y = arch[k][1] - (k == arch.length - 1 ? r.nextInt(2) : 0);
                pts[k] = new double[]{cx + o[0], y, cz + o[1]};
            }
            Set<BlockPos> before = new HashSet<>(t.cells().keySet());
            t.stem(pts, wood);
            // leaves wrap the limb up in the crown, so the crown shows leaves with a branch here and there
            for (BlockPos p : new ArrayList<>(t.cells().keySet())) {
                if (!before.contains(p) && p.getY() >= top - drop) {
                    for (int[] w : wrap) {
                        if (r.nextDouble() < 0.85) {
                            t.leaf(p.getX() + w[0], p.getY() + w[1], p.getZ() + w[2], leaves);
                        }
                    }
                }
            }
            double[] tip = pts[pts.length - 1];
            t.blob(tip[0], tip[1] + 0.5, tip[2], 1.6, 1.0, 1.6, leaves, 0.25, null);
        }
        // the curtain
        Map<Long, int[]> lowest = new LinkedHashMap<>(); // column -> x, lowest leaf y, z
        t.cells().forEach((p, c) -> {
            if (c.kind() == Shape.Kind.LEAF) {
                lowest.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), new int[]{p.getX(), p.getY(), p.getZ()},
                        (a, b) -> a[1] <= b[1] ? a : b);
            }
        });
        double rim = 0;
        for (int[] col : lowest.values()) {
            rim = Math.max(rim, Math.hypot(col[0] - cx, col[2] - cz));
        }
        // the rim's strands first, so they get the room; most reach down to one or two blocks above the ground. The
        // big crown's outline is no circle (the limb tips stick out), so there every column on the outline seen from
        // above counts as rim too, and every rim column is tried: the rule that strands stand apart thins them out.
        double inner = (big ? 3.5 : 2.5) + g * 1.2;
        List<BlockPos> outer = new ArrayList<>(), middle = new ArrayList<>();
        for (int[] col : lowest.values()) {
            double d = Math.hypot(col[0] - cx, col[2] - cz);
            boolean edge = d >= rim - 1.6 || big && d >= rim - 3 && outline(lowest, col[0], col[2]);
            if (edge ? big || r.nextDouble() < 0.7 : d >= inner && r.nextDouble() < 0.35) {
                (edge ? outer : middle).add(new BlockPos(col[0], col[1], col[2]));
            }
        }
        shuffle(outer, r);
        shuffle(middle, r);
        Set<BlockPos> atRim = new HashSet<>(outer);
        List<BlockPos> from = new ArrayList<>(outer);
        from.addAll(middle);
        Set<Long> strands = t.strands(from, p -> Math.max(1, !atRim.contains(p)
                ? p.getY() - randint(r, 1, 3 + Shape.ip(g * 3))
                : r.nextDouble() < 0.8 ? randint(r, 1, 2) : p.getY() - randint(r, 2, Math.max(2, p.getY() / 2))),
                p -> leaves);
        for (BlockPos p : outer) {
            t.strandCounts[0] += strands.contains(Shape.column(p)) ? 1 : 0;
        }
        t.strandCounts[1] = strands.size() - t.strandCounts[0];
        for (long c : strands) {
            t.strandCounts[2] += Math.hypot(BlockPos.getX(c) - cx, BlockPos.getZ(c) - cz) < rim - 3 ? 1 : 0;
        }
        t.finishPersistent();
        willowVines(t, r, big, cx, cz, top, strands);
    }

    /**
     * The trunk of a willow up to where the crown starts, with its root flare, after six trunks the user rebuilt by
     * hand (concept/reference/, PLAN-0.17.md). It bends without winding: about two in three trunks move over once
     * and stay there (the thin one along an axis, the big one also diagonally; a tall thin one now and then moves on
     * once more the same way, at least three layers higher); the others go out and come back over the foot, a C with a
     * quiet belly between, diagonally only now and then. Never
     * more than two moves over, never a turn but the one way back. The foot and the top layer stand plain. Returns
     * the height th and where the top layer stands over the foot: {th, dx, dz}.
     */
    private static int[] willowTrunk(Shape t, RandomSource r, boolean big, double g) {
        Block wood = PALE_WOOD;
        int size = big ? 2 : 1;
        int th = Math.max(4, (big ? randint(r, 6, 8) : randint(r, 3, 5)) + Shape.ip(g * (big ? 4 : 5)));
        int side = r.nextInt(4);
        int[] a = SIDE4[side], b = SIDE4[(side + 1 + 2 * r.nextInt(2)) % 4]; // the way out, and one across it
        int[][] at = new int[th + 1][2]; // where the trunk (the big one's square by its corner) stands in each layer
        List<Set<BlockPos>> layers = big ? broadTrunk(r, th, a, b, at) : thinTrunk(r, th, a, b, at);
        for (int y = 0; y <= th; y++) {
            for (BlockPos p : layers.get(y)) {
                t.log(p.getX(), y, p.getZ(), wood);
            }
        }
        List<int[]> feet = new ArrayList<>(); // root flare: the cells beside the foot, corners left out
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                if ((x == -1 || x == size) != (z == -1 || z == size)) {
                    feet.add(new int[]{x, z});
                }
            }
        }
        int[] roots = sample(r, feet.size(), big ? randint(r, 4, 6) : randint(r, 2, 3));
        int tall = big ? randint(r, 0, 2) : 0; // big roots two blocks high
        for (int i = 0; i < roots.length; i++) {
            int[] f = feet.get(roots[i]);
            for (int y = 0; y <= (i < tall ? 1 : 0); y++) {
                t.log(f[0], y, f[1], wood);
            }
        }
        t.trunkTop = th;
        return new int[]{th, at[th][0], at[th][1]};
    }

    /**
     * The layers of the thin trunk (blocks at y 0), filling in at. Where it moves over by a block, the old column runs
     * one layer on beside the new one (a knee), so two columns share exactly one layer, face to face; a diagonal move
     * is two such moves in consecutive layers, along one axis and then across. A bend moves over along an axis only
     * (a lone diagonal move reads as a zigzag), somewhere between the third layer and the one under the top (on the
     * shortest trunk from the second); a C goes out in the third
     * layer (on a taller trunk the fourth) and back in one of the two layers under the top, its belly at least two
     * layers long, diagonally only now and then and on a tall trunk. A quarter of the trunks with a quiet column
     * three layers long or more carry a knot on it: one or two blocks on a free side, in its middle third.
     */
    private static List<Set<BlockPos>> thinTrunk(RandomSource r, int th, int[] a, int[] b, int[][] at) {
        List<int[]> moves = new ArrayList<>(); // {layer, dx, dz}
        if (th >= 5 && r.nextDouble() < 0.38) {
            boolean diagonal = th >= 8 && r.nextDouble() < 0.3;
            int out = th >= 7 ? randint(r, 2, 3) : 2, back = th - randint(r, 1, 2);
            if (back - out < (diagonal ? 4 : 2)) {
                back = th - 1;
            }
            moves.add(new int[]{out, a[0], a[1]});
            if (diagonal) {
                moves.add(new int[]{out + 1, b[0], b[1]});
                moves.add(new int[]{back - 1, -b[0], -b[1]});
            }
            moves.add(new int[]{back, -a[0], -a[1]});
        } else if (th >= 8 && r.nextDouble() < 0.3) { // a bend twice the same way, three layers apart: an even lean
            int first = randint(r, 2, th - 5);
            moves.add(new int[]{first, a[0], a[1]});
            moves.add(new int[]{randint(r, first + 4, th - 1), a[0], a[1]});
        } else { // a bend, along an axis only: a lone diagonal move reads as a zigzag
            moves.add(new int[]{randint(r, th == 4 ? 1 : 2, th - 1), a[0], a[1]});
        }
        for (int[] m : moves) {
            for (int y = m[0]; y <= th; y++) {
                at[y][0] += m[1];
                at[y][1] += m[2];
            }
        }
        List<Set<BlockPos>> layers = new ArrayList<>();
        int from = 0, start = -1, end = -1; // the longest quiet column, three layers or more
        for (int y = 0; y <= th + 1; y++) {
            boolean moved = y > 0 && (y > th || at[y][0] != at[y - 1][0] || at[y][1] != at[y - 1][1]);
            if (moved) {
                if (y - from >= 3 && y - from > end - start + 1) {
                    start = from;
                    end = y - 1;
                }
                from = y;
            }
            if (y <= th) {
                Set<BlockPos> layer = new HashSet<>(List.of(new BlockPos(at[y][0], 0, at[y][1])));
                if (moved) {
                    layer.add(new BlockPos(at[y - 1][0], 0, at[y - 1][1])); // the knee
                }
                layers.add(layer);
            }
        }
        if (start >= 0 && r.nextDouble() < 0.25) {
            int n = end - start + 1, lo = start + n / 3, hi = end - n / 3;
            int h = Math.min(randint(r, 1, 2), hi - lo + 1), y0 = randint(r, lo, hi - h + 1);
            List<int[]> free = new ArrayList<>(); // sides that face no other column of the trunk
            for (int[] s : SIDE4) {
                boolean taken = false;
                for (int[] p : at) {
                    taken |= p[0] == at[start][0] + s[0] && p[1] == at[start][1] + s[1];
                }
                if (!taken) {
                    free.add(s);
                }
            }
            int[] s = free.get(r.nextInt(free.size()));
            for (int y = y0; y < y0 + h; y++) {
                layers.get(y).add(new BlockPos(at[start][0] + s[0], 0, at[start][1] + s[1]));
            }
        }
        return layers;
    }

    /** How a move of the big trunk's square is spread over the layers (PLAN-0.17.md, E.1). */
    private static final int AHEAD = 0, BEHIND = 1, BOTH = 2, FULL = 3, DIAGONAL_A = 4, DIAGONAL_B = 5;

    /**
     * The layers of the big trunk (blocks at y 0), filling in at with its square's corner. The 2x2 square never moves
     * further than a block from the foot along either axis, and never jumps whole: at each move some blocks go a
     * layer ahead or stay a layer behind, so the layers in between hold five or six blocks and any two layers on top of
     * each other share three or more. A bend moves once (along an axis, or diagonally over three layers); a C goes
     * out low and comes back, now and then diagonally (each way in one diagonal move or two axis moves), with a belly
     * of at least two plain layers.
     */
    private static List<Set<BlockPos>> broadTrunk(RandomSource r, int th, int[] a, int[] b, int[][] at) {
        int[] ab = {a[0] + b[0], a[1] + b[1]};
        int[] starts;
        List<List<int[]>> moves = new ArrayList<>(); // each move's steps {layer from its first, dx, dz, form}
        for (; ; ) {
            moves.clear();
            // a C needs room for two moves and a belly; a short trunk rolls again, so about a third come out C
            boolean c = r.nextDouble() < 0.45, diagonal = r.nextDouble() < (c ? 0.3 : 0.5);
            int[] d = diagonal ? ab : a;
            moves.add(broadMove(r, d, c, false));
            if (!c) {
                starts = new int[]{randint(r, 2, th - span(moves.getFirst()))};
                break;
            }
            // when the way out sends a single block ahead or behind, the way back spreads over both lanes: else one
            // side shows a piece of trunk that is merely shifted, without a move
            moves.add(broadMove(r, new int[]{-d[0], -d[1]}, true, moves.getFirst().getFirst()[3] < BOTH));
            int out = randint(r, 2, 3), lo = out + span(moves.getFirst()) + 2, hi = th - span(moves.getLast());
            if (lo <= hi) {
                starts = new int[]{out, randint(r, lo, hi)};
                break;
            }
        }
        List<int[]> steps = new ArrayList<>(); // {layer, dx, dz, form}
        for (int i = 0; i < moves.size(); i++) {
            for (int[] s : moves.get(i)) {
                steps.add(new int[]{starts[i] + s[0], s[1], s[2], s[3]});
            }
        }
        for (int[] s : steps) {
            for (int y = s[0]; y <= th; y++) {
                at[y][0] += s[1];
                at[y][1] += s[2];
            }
        }
        List<Set<BlockPos>> layers = new ArrayList<>();
        for (int[] p : at) {
            layers.add(square(p));
        }
        for (int[] s : steps) {
            int y = s[0];
            Set<BlockPos> from = square(at[y - 1]), to = square(at[y]);
            List<BlockPos> old = new ArrayList<>(from), fresh = new ArrayList<>(to);
            old.removeAll(to);
            fresh.removeAll(from);
            BlockPos oldCorner = null;
            if (s[3] >= DIAGONAL_A) { // old and fresh lose their far corners: the sides beside the shared block
                BlockPos shared = from.stream().filter(to::contains).findFirst().orElseThrow();
                oldCorner = shared.offset(-s[1], 0, -s[2]);
                old.remove(oldCorner);
                fresh.remove(shared.offset(s[1], 0, s[2]));
            }
            shuffle(old, r);
            shuffle(fresh, r);
            switch (s[3]) {
                case AHEAD -> layers.get(y - 1).add(fresh.getFirst());
                case BEHIND -> layers.get(y).add(old.getFirst());
                case BOTH -> { // one ahead, and one behind in the other lane
                    BlockPos n = fresh.getFirst();
                    layers.get(y - 1).add(n);
                    layers.get(y).add(old.stream().filter(o -> s[1] != 0 ? o.getZ() != n.getZ() : o.getX() != n.getX())
                            .findFirst().orElseThrow());
                }
                case FULL -> layers.get(y).addAll(old);
                case DIAGONAL_A -> { // a fresh side, both fresh sides, the new square with both old sides
                    layers.get(y - 2).add(fresh.getFirst());
                    layers.get(y - 1).addAll(fresh);
                    layers.get(y).addAll(old);
                }
                default -> { // both fresh sides, a plus round the shared block, the new square with an old side
                    layers.get(y - 2).addAll(fresh);
                    layers.get(y - 1).remove(oldCorner);
                    layers.get(y - 1).addAll(fresh);
                    layers.get(y).add(old.getFirst());
                }
            }
        }
        return layers;
    }

    /**
     * One move of the big trunk's square by d, as steps {layer from the move's first, dx, dz, form}: along an axis in
     * one of four ways (with bothLanes only the two that move blocks in both lanes), diagonally in one of the two
     * three-layer forms or, when twice is allowed, half the time as two axis moves two layers apart (the first leaves
     * a block behind, the second sends one ahead, so no plain layer stands between them).
     */
    private static List<int[]> broadMove(RandomSource r, int[] d, boolean twice, boolean bothLanes) {
        if (d[0] == 0 || d[1] == 0) {
            int form = bothLanes ? randint(r, BOTH, FULL) : r.nextInt(4);
            return List.of(new int[]{form == AHEAD || form == BOTH ? 1 : 0, d[0], d[1], form});
        }
        if (!twice || r.nextBoolean()) {
            return List.of(new int[]{2, d[0], d[1], DIAGONAL_A + r.nextInt(2)});
        }
        boolean xFirst = r.nextBoolean();
        int first = randint(r, BEHIND, FULL), pre = first == BOTH ? 1 : 0;
        return List.of(new int[]{pre, xFirst ? d[0] : 0, xFirst ? 0 : d[1], first},
                new int[]{pre + 2, xFirst ? 0 : d[0], xFirst ? d[1] : 0, r.nextBoolean() ? AHEAD : BOTH});
    }

    /** The layers a move takes, from its first changed layer to the one its last step lands in. */
    private static int span(List<int[]> move) {
        return move.getLast()[0] + 1;
    }

    /** The 2x2 square with its corner at p (blocks at y 0). */
    private static Set<BlockPos> square(int[] p) {
        Set<BlockPos> s = new HashSet<>();
        for (int ox = 0; ox < 2; ox++) {
            for (int oz = 0; oz < 2; oz++) {
                s.add(new BlockPos(p[0] + ox, 0, p[1] + oz));
            }
        }
        return s;
    }

    /** Whether the column x, z has a side neighbour without leaves (columns is keyed by x, 0, z). */
    private static boolean outline(Map<Long, int[]> columns, int x, int z) {
        for (int[] s : SIDE4) {
            if (!columns.containsKey(BlockPos.asLong(x + s[0], 0, z + s[1]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A few vines down the outside of a willow whose leaves stand (crown middle cx, cz, crown top at top), each on
     * the outer face of a rim leaf and below it, never beside a strand.
     */
    private static void willowVines(Shape t, RandomSource r, boolean big, double cx, double cz, int top, Set<Long> strands) {
        double rim = 0;
        for (Map.Entry<BlockPos, Shape.Cell> e : t.cells().entrySet()) {
            if (e.getValue().kind() == Shape.Kind.LEAF) {
                rim = Math.max(rim, Math.hypot(e.getKey().getX() - cx, e.getKey().getZ() - cz));
            }
        }
        List<BlockPos> rimLeaves = new ArrayList<>();
        final double rimEdge = rim - 1.2;
        t.cells().forEach((p, c) -> {
            if (c.kind() == Shape.Kind.LEAF && p.getY() < top - 1 && Math.hypot(p.getX() - cx, p.getZ() - cz) >= rimEdge) {
                rimLeaves.add(p);
            }
        });
        shuffle(rimLeaves, r);
        int vines = big ? randint(r, 6, 10) : randint(r, 3, 6);
        Set<Long> used = new HashSet<>();
        for (BlockPos p : rimLeaves) {
            if (vines == 0) {
                break;
            }
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos v = p.relative(out);
            if (used.contains(BlockPos.asLong(v.getX(), 0, v.getZ())) || t.cells().containsKey(v)
                    || Shape.besideStrand(strands, v)) {
                continue;
            }
            used.add(BlockPos.asLong(v.getX(), 0, v.getZ()));
            vines--;
            // hangs on the leaf behind it; further down each vine holds on to the one above
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
            int length = randint(r, 3, big ? 8 : 6);
            for (int y = p.getY(); y > Math.max(0, p.getY() - length); y--) {
                if (t.cells().containsKey(new BlockPos(v.getX(), y, v.getZ()))) {
                    break;
                }
                t.put(v.getX(), y, v.getZ(), vine);
            }
        }
    }

    /** A fruit twig at a random stage, so a grown tree carries green, half ripe and ripe fruit together. */
    private static BlockState fruit(Block twig, RandomSource r) {
        return twig.defaultBlockState().setValue(HangingFruitBlock.STAGE, ripeness(r));
    }

    private static int ripeness(RandomSource r) {
        return weighted(r, new int[]{0, 1, 2}, new int[]{3, 4, 3});
    }

    // ================================================================================================ date palm

    /**
     * One, two or three trunks on the cells of a 2x2 square, each one block thick and leaning to its own side, bent
     * or slanted in its own way (see {@link #palmTrunk}). Two trunks stand on a diagonal of the square and touch at a
     * corner; three stand on three of its cells, an L whose arms touch the corner trunk face to face. The trunks lean
     * apart: of two, the tallest anywhere and the other away from it along x or z; of three, each arm along itself and
     * the corner trunk away from both. Which cell grows the tallest trunk is rolled; it carries the full crown, the
     * lower ones smaller crowns; date clusters hang under every crown. The square is turned and mirrored; a roll that
     * leaves two trunk blocks side by side above the lowest three layers is rolled again.
     */
    public static Shape datePalm(RandomSource r) {
        int n = randint(r, 1, 3);
        int[][] foot = n == 3 ? new int[][]{{0, 0}, {1, 0}, {0, 1}} : new int[][]{{0, 0}, {1, 1}};
        int[][] heights = {{9, 11}, {6, 7}, {4, 5}};
        for (; ; ) {
            int[] cells = n == 3 ? sample(r, 3, 3) : new int[]{0, 1}; // the cell of the tallest trunk, the next, ...
            int turns = r.nextInt(4);
            boolean mirror = r.nextBoolean();
            List<List<BlockPos>> trunks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int c = cells[i];
                int[] d = n == 3 ? (c > 0 ? foot[c] : r.nextBoolean() ? new int[]{-1, 0} : new int[]{0, -1})
                        : c == 0 ? SIDE4[r.nextInt(4)] : r.nextBoolean() ? new int[]{1, 0} : new int[]{0, 1};
                int[] cell = turn(foot[c], turns, mirror);
                trunks.add(palmTrunk(cell[0], cell[1], randint(r, heights[i][0], heights[i][1]), turn(d, turns, mirror),
                        r));
            }
            if (!sideBySide(trunks, 3)) {
                Shape t = new Shape(r);
                for (int i = 0; i < n; i++) {
                    palmCrown(t, trunks.get(i), r, i);
                }
                return addDates(t, trunks, r).finishPersistent();
            }
        }
    }

    /** Quarter turns, then an optional mirror across the x axis. */
    private static int[] turn(int[] p, int turns, boolean mirror) {
        int x = p[0], z = p[1];
        for (int i = 0; i < turns; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[]{mirror ? -x : x, z};
    }

    /**
     * Date clusters under every crown, each at its own stage of ripeness: on three or four sides of the top trunk
     * block of the first (tallest) trunk, two or three on every other trunk, so each hangs right under one of the
     * crown's four leaves beside the core. Called once all crowns stand, so a cluster never takes the place of a leaf
     * or a log.
     */
    private static Shape addDates(Shape t, List<List<BlockPos>> trunks, RandomSource r) {
        for (int i = 0; i < trunks.size(); i++) {
            for (int s : sample(r, 4, i == 0 ? randint(r, 3, 4) : randint(r, 2, 3))) {
                dateCluster(t, trunks.get(i).getLast(), s, r);
            }
        }
        return t;
    }

    private static void dateCluster(Shape t, BlockPos trunk, int side, RandomSource r) {
        int dx = CARDINALS[side][0], dz = CARDINALS[side][1];
        BlockState cluster = GreekTrees.DATE_CLUSTER.defaultBlockState()
                .setValue(CocoaBlock.AGE, ripeness(r))
                .setValue(CocoaBlock.FACING, towards(-dx, -dz));
        t.putIfFree(trunk.getX() + dx, trunk.getY(), trunk.getZ() + dz, cluster);
    }

    // ================================================================================================ large date palm

    /** Foot cells a side trunk may root on, around the main trunk at 0, 0: the four corners, and the whole ring two
     * blocks out (straight, diagonal and in between). Never right beside the main trunk. */
    private static final int[][] SIDE_FEET = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1},
            {-2, 0}, {-2, -1}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1}};

    /**
     * Grown from four date palm saplings in a square (like a vanilla dark oak), with the main trunk on the square's
     * 0, 0 corner. A clump of two to five trunks, each one block thick and leaning to its own side, bent or slanted
     * in its own way. The
     * trunks spread evenly round the clump: each side trunk roots in its direction, on a corner of the main trunk
     * or anywhere on the ring two blocks out, and slants outwards, to the side its foot lies on; the main trunk
     * slants into the gap between them. The main trunk is the tallest, bigger clumps grow taller, and no two crowns
     * stand closer than two blocks in height. The tallest trunk carries the full crown, the next two the middle
     * one, further ones the small one; date clusters hang under every crown. No two trunks share a block or stand
     * side by side at any height, and no two crowns touch; a roll that breaks a rule is rolled again (after many
     * failed rolls with one trunk less).
     */
    public static Shape largeDatePalm(RandomSource r) {
        int n = weighted(r, new int[]{2, 3, 4, 5}, new int[]{3, 4, 3, 2});
        for (int attempt = 1; ; attempt++) {
            if (attempt % 25 == 0 && n > 2) {
                n--;
            }
            double start = uniform(r, 0, 360);
            List<int[]> feet = new ArrayList<>();
            feet.add(new int[]{0, 0});
            for (int i = 1; i < n && feet.size() == i; i++) {
                int[] foot = freeFoot(feet, polar(start + i * 360.0 / n + uniform(r, -25, 25), uniform(r, 1.3, 2.9)));
                if (foot != null) {
                    feet.add(foot);
                }
            }
            if (feet.size() < n) {
                continue;
            }
            List<List<BlockPos>> trunks = new ArrayList<>();
            List<Integer> heights = new ArrayList<>();
            int main = randint(r, 12, 14) + (n >= 4 ? 2 : 1);
            for (int i = 0; i < n; i++) {
                // the side the foot lies on; for the main trunk the side of the gap between the others
                int[] f = i == 0 ? new int[]{Shape.ip(polar(start, 2)[0]), Shape.ip(polar(start, 2)[1])} : feet.get(i);
                boolean alongX = Math.abs(f[0]) == Math.abs(f[1]) ? r.nextBoolean() : Math.abs(f[0]) > Math.abs(f[1]);
                int[] d = alongX ? new int[]{Integer.signum(f[0]), 0} : new int[]{0, Integer.signum(f[1])};
                int height = i == 0 ? main : main - (i == 1 ? randint(r, 2, 4) : randint(r, 3, 6));
                while (tooClose(heights, height)) {
                    height++; // crowns at the same height melt into one roof; keep them two apart
                }
                heights.add(height);
                trunks.add(palmTrunk(feet.get(i)[0], feet.get(i)[1], height, d, r));
            }
            boolean crownsTouch = false;
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    BlockPos ta = trunks.get(a).getLast(), tb = trunks.get(b).getLast();
                    crownsTouch |= Math.max(Math.abs(ta.getX() - tb.getX()), Math.abs(ta.getZ() - tb.getZ())) < 2;
                }
            }
            if (sideBySide(trunks, 0) || crownsTouch) {
                continue;
            }
            Shape t = new Shape(r);
            for (int i = 0; i < n; i++) {
                int taller = 0;
                for (int h : heights) {
                    taller += h > heights.get(i) ? 1 : 0;
                }
                palmCrown(t, trunks.get(i), r, Math.min(2, (taller + 1) / 2));
            }
            return addDates(t, trunks, r).finishPersistent();
        }
    }

    /** The foot cell nearest to aim that is free and not right beside another foot, or null. */
    private static int[] freeFoot(List<int[]> feet, double[] aim) {
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int[] c : SIDE_FEET) {
            boolean free = true;
            for (int[] f : feet) {
                free &= Math.abs(c[0] - f[0]) + Math.abs(c[1] - f[1]) > 1;
            }
            double d = Math.hypot(c[0] - aim[0], c[1] - aim[1]);
            if (free && d < bestDist) {
                best = c;
                bestDist = d;
            }
        }
        return best;
    }

    private static boolean tooClose(List<Integer> heights, int height) {
        for (int h : heights) {
            if (Math.abs(h - height) < 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether two of the trunks share a block or have blocks side by side at the same height from layer from up; the
     * hidden crown core one above each top counts as trunk.
     */
    private static boolean sideBySide(List<List<BlockPos>> trunks, int from) {
        for (int a = 0; a < trunks.size(); a++) {
            for (int b = a + 1; b < trunks.size(); b++) {
                List<BlockPos> la = new ArrayList<>(trunks.get(a)), lb = new ArrayList<>(trunks.get(b));
                la.add(la.getLast().above());
                lb.add(lb.getLast().above());
                for (BlockPos p : la) {
                    for (BlockPos q : lb) {
                        if (p.getY() >= from && p.getY() == q.getY()
                                && Math.abs(p.getX() - q.getX()) + Math.abs(p.getZ() - q.getZ()) <= 1) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * One trunk from x, z up to height: one block thick, leaning only towards d, never winding, and no two alike.
     * It steps one block further one to six times, the more the taller it is (since 0.17.0 one step more than before,
     * for a clearer curve; a layer touching the one below at an edge, no filler block, so two trunk blocks never sit
     * side by side). Where it steps is rolled: a third of the
     * trunks bend towards the top (the steps crowd under the crown), a third bend at the foot and stand up straight
     * above, a third slant all the way; every step is then moved up or down by up to a block. At least two blocks
     * stand above each other at the foot, between two steps and under the crown. Returns the blocks from the foot
     * up; the last one is the top.
     */
    private static List<BlockPos> palmTrunk(int x, int z, int height, int[] d, RandomSource r) {
        int steps = Math.max(1, Math.min((height - 2) / 2, randint(r, height / 4, height / 3) + 1));
        double bend = new double[]{0.55, 1, 1.8}[r.nextInt(3)]; // steps crowd at the top, spread evenly, crowd at the foot
        int[] levels = new int[steps];
        for (int j = 0; j < steps; j++) {
            levels[j] = 2 + Shape.ip(Math.pow((j + 0.5) / steps, bend) * (height - 3)) + randint(r, -1, 1);
        }
        for (int j = 0; j < steps; j++) { // from the foot up: two blocks above each other before every step
            levels[j] = Math.max(levels[j], j == 0 ? 2 : levels[j - 1] + 2);
        }
        for (int j = steps - 1; j >= 0; j--) { // from the top down: and after every step
            levels[j] = Math.min(levels[j], j == steps - 1 ? height - 1 : levels[j + 1] - 2);
        }
        List<BlockPos> trunk = new ArrayList<>();
        for (int y = 0, next = 0; y <= height; y++) {
            if (next < steps && y == levels[next]) {
                next++;
                x += d[0];
                z += d[1];
            }
            trunk.add(new BlockPos(x, y, z));
        }
        return trunk;
    }

    /** One crown of the given size on a one-block trunk at 0, 0, 0 (its core at 0, 1, 0), for the self-test. */
    public static Shape palmCrown(RandomSource r, int size) {
        Shape t = new Shape(r);
        palmCrown(t, List.of(BlockPos.ZERO), r, size);
        return t;
    }

    /**
     * Places a trunk and its crown: a hidden core block on the top with a leaf on each side and one to three on it,
     * a star of eight fine fronds (long ones along the axes, arching out and hanging at the tip, shorter diagonal
     * ones) and a tuft of three to five steep fronds over it. Size 0 is the full crown (fronds about five blocks
     * long), 1 and 2 are the smaller ones of lower trunks (four and three). Every frond rolls its own direction,
     * length, arch and hanging tip, and a quarter of the crowns lack a frond or carry a stub in its place, so no
     * crown is as even as a drawing. The fronds reach further than vanilla leaves hold, so the palms' leaves are
     * persistent.
     */
    private static void palmCrown(Shape t, List<BlockPos> trunk, RandomSource r, int size) {
        for (BlockPos p : trunk) {
            t.log(p.getX(), p.getY(), p.getZ(), Blocks.JUNGLE_WOOD);
        }
        int x = trunk.getLast().getX(), c = trunk.getLast().getY() + 1, z = trunk.getLast().getZ();
        t.log(x, c, z, Blocks.JUNGLE_WOOD);
        for (int y = 1, tip = weighted(r, new int[]{1, 2, 3}, new int[]{30, 55, 15}); y <= tip; y++) {
            t.leaf(x, c + y, z, Blocks.JUNGLE_LEAVES);
        }
        for (int[] d : CARDINALS) {
            t.leaf(x + d[0], c, z + d[1], Blocks.JUNGLE_LEAVES); // the date clusters hang under these
        }
        Set<Integer> gaps = new HashSet<>(); // star fronds left out or cut to a stub; two only on the full crown, apart
        if (r.nextDouble() < 0.25) {
            int first = r.nextInt(8);
            gaps.add(first);
            if (size == 0 && r.nextBoolean()) {
                gaps.add((first + randint(r, 2, 6)) % 8);
            }
        }
        for (int k = 0; k < 8; k++) {
            double angle = k * 45 + uniform(r, -10, 10);
            if (gaps.contains(k)) {
                if (r.nextBoolean()) {
                    frond(t, x, c, z, angle, new double[][]{{1, 1}, {2, 1}});
                }
                continue;
            }
            int rise = randint(r, 1, 2);
            double kink = uniform(r, 0.5, 0.75); // where the arch tips over
            if (k % 2 == 0) { // along an axis: falls two from its top, then hangs
                double len = Math.max(2, 5 - size + new int[]{-1, 0, 0, 1}[r.nextInt(4)]);
                frond(t, x, c, z, angle, new double[][]{{1, 1}, {kink * len, rise}, {(kink + 1) / 2 * len, rise - 1},
                        {len, rise - 2}, {len, rise - 2 - randint(r, 0, 3)}});
            } else { // diagonal: shorter, falls one
                double len = Math.max(2, (size == 0 ? 3 : 2) * Math.sqrt(2) + randint(r, -1, 1));
                frond(t, x, c, z, angle, new double[][]{{1, 1}, {kink * len, rise}, {len, rise - 1},
                        {len, rise - 1 - randint(r, 0, 2)}});
            }
        }
        double[][] steep = size == 0 ? new double[][]{{1, 1}, {2, 3}, {3, 4}, {4, 4}}
                : size == 1 ? new double[][]{{1, 1}, {2, 3}, {3, 3}} : new double[][]{{1, 1}, {2, 2}};
        int n = randint(r, 3, 5);
        double a0 = uniform(r, 0, 360);
        for (int i = 0; i < n; i++) { // the tuft: each frond a block longer or shorter, higher or lower at its tip
            int longer = randint(r, -1, 1), higher = randint(r, -1, 1);
            double[][] profile = new double[steep.length][];
            for (int j = 0; j < steep.length; j++) {
                double f = (double) j / (steep.length - 1);
                profile[j] = new double[]{steep[j][0] + longer * f, steep[j][1] + higher * f};
            }
            frond(t, x, c, z, a0 + i * 360.0 / n + uniform(r, -15, 15), profile);
        }
    }

    /**
     * A frond one block wide: leaves along the points (blocks out, blocks up) from x, y, z towards angle. Where it
     * rises, falls or leaves the axes its leaves touch at the edges only, a line as fine as a block allows.
     */
    private static void frond(Shape t, int x, int y, int z, double angle, double[][] profile) {
        double[] u = polar(angle, 1);
        double[] a = {x, y, z};
        for (double[] p : profile) {
            double[] b = {x + u[0] * p[0], y + p[1], z + u[1] * p[0]};
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)))));
            for (int i = 1; i <= n; i++) {
                t.leaf(a[0] + dx * i / n, a[1] + dy * i / n, a[2] + dz * i / n, Blocks.JUNGLE_LEAVES);
            }
            a = b;
        }
    }

    // ================================================================================================ dice

    static int randint(RandomSource r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    static double uniform(RandomSource r, double a, double b) {
        return a + r.nextDouble() * (b - a);
    }

    static int weighted(RandomSource r, int[] values, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = r.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    static <T> void shuffle(List<T> list, RandomSource r) {
        for (int i = list.size() - 1; i > 0; i--) {
            list.set(i, list.set(r.nextInt(i + 1), list.get(i)));
        }
    }

    /** k distinct values out of 0..n-1. */
    static int[] sample(RandomSource r, int n, int k) {
        int[] all = new int[n];
        for (int i = 0; i < n; i++) {
            all[i] = i;
        }
        for (int i = 0; i < k; i++) {
            int j = i + r.nextInt(n - i);
            int tmp = all[i];
            all[i] = all[j];
            all[j] = tmp;
        }
        return java.util.Arrays.copyOf(all, k);
    }

    /** x, z of a direction in degrees and a length. */
    static double[] polar(double degrees, double length) {
        double a = Math.toRadians(degrees);
        return new double[]{Math.cos(a) * length, Math.sin(a) * length};
    }

    private static Direction towards(int dx, int dz) {
        if (dx > 0) {
            return Direction.EAST;
        }
        if (dx < 0) {
            return Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
