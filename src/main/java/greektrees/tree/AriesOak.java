package greektrees.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Aries oak (Widdereiche, Quercus arietina), after the giant tree a fellow player built in the Greek city. It grows
 * only from sixteen saplings in a 4x4 square (see greektrees.AriesOakSaplingBlock); the trunk stands on the square,
 * whose 0, 0 corner is the feature's origin. Started as the port of concept/round6.py (KONZEPT.md, round 6), since
 * then worked on here:
 * <ul>
 * <li>a round trunk, a disc of wood per layer, wide and flared at the foot with buttresses and short roots, narrowing
 * upwards, with twisted ridges, burls and broken-off stubs; its middle line sways in smooth curves</li>
 * <li>five to seven round limbs at staggered heights, thick at the trunk and thin at the end, each with side branches
 * and twigs, ending in a hollow side crown that reaches out past the dome: a clean shell of leaves two thick over a
 * room, open underneath, spanned from the inside by twigs from the limb's tip; on the outside a lumpy layer</li>
 * <li>a hollow main dome, a clean closed shell of leaves and moss two thick, held up by ribs that run up under it
 * and fork once; on the outside a lumpy layer of leaves, a little ragged</li>
 * <li>dark oak slabs in the steps of the thick limbs and, fewer, of the trunk and its roots; stripped dark oak in
 * long streaks on the bark</li>
 * <li>azalea leaves, a fifth of them flowering, with patches of jungle leaves; vines down the outside, glow berries
 * from moss and from the limbs, a moss block with a firefly bush in some side crowns</li>
 * </ul>
 * The outer layers of the side crowns and the dome are drawn in warped space, so no outline runs straight. The
 * leaves are persistent: they never decay, so they need no wood within six steps and nothing is pruned.
 * <p>Half the trees weep: single strands of leaves, each apart from the next, hang from the outer rim of every side
 * crown, like the curtain of a weeping willow.
 */
public final class AriesOak {
    private static final Block WOOD = Blocks.DARK_OAK_WOOD;
    private static final double TAU = Math.PI * 2;
    /** Thickness of the crowns' leaves. */
    private static final double SHELL = 2.0;

    private final Shape t;
    private final RandomSource r;
    private final int h;
    private final double[][] ctrl = new double[5][];
    private final double rBase, rMid, rTop;
    private final int flare;
    private final double buttress = 0.28, ridges = 0.09, twist = 0.035;
    private final int lobes;
    private final double[] ph = new double[3];
    /** Trunk, roots, burls and stubs, and the thick parts of the limbs and side branches: where slabs go. */
    private final Set<BlockPos> trunkThick = new LinkedHashSet<>(), limbThick = new LinkedHashSet<>();
    /** The limbs with their side branches and twigs (not the twigs inside the crowns). */
    private final Set<BlockPos> limbCells = new LinkedHashSet<>();
    /** The air inside the side crowns. */
    private final Set<BlockPos> rooms = new HashSet<>();
    /** Moss blocks that may grow glow berries; true for those over a side crown's room. */
    private final Map<BlockPos, Boolean> moss = new LinkedHashMap<>();
    private final Waves warpX, warpY, warpZ, jungle, mossy, bark;
    private final double jungleLevel;
    private final boolean weeping;
    /** The leaves of every side crown, and the x, z of its middle. */
    private final List<List<BlockPos>> crownLeaves = new ArrayList<>();
    private final List<double[]> crownMiddles = new ArrayList<>();

    private AriesOak(RandomSource r, boolean weeping) {
        this.r = r;
        this.weeping = weeping;
        this.t = new Shape(r);
        h = randint(50, 56);
        rBase = uniform(5.2, 5.8);
        rMid = uniform(3.3, 3.7);
        rTop = uniform(2.1, 2.4);
        flare = randint(6, 8);
        lobes = randint(4, 5);
        for (int i = 0; i < 3; i++) {
            ph[i] = uniform(0, TAU);
        }
        warpX = new Waves(r, 7, 1);
        warpY = new Waves(r, 7, 1);
        warpZ = new Waves(r, 7, 1);
        jungle = new Waves(r, 9, 1);
        jungleLevel = jungle.level(0.2, r);
        mossy = new Waves(r, 6, 1);
        bark = new Waves(r, 5, 4); // long streaks up the trunk
    }

    public static Shape grow(RandomSource r) {
        return grow(r, r.nextBoolean());
    }

    /** An Aries oak with (weeping) or without strands of leaves under its side crowns. */
    public static Shape grow(RandomSource r, boolean weeping) {
        return new AriesOak(r, weeping).build();
    }

    private Shape build() {
        trunk();
        double r0 = uniform(13.5, 15.0); // the dome's radius; the side crowns reach out past it
        int y2 = (int) Math.round(h * 0.8);
        double[] top = centre(h);
        limbs(r0);
        dome(r0, y2, top[0], top[1]);
        if (weeping) {
            strands();
        }
        slabs();
        strip();
        t.finishPersistent();
        hangings(r0, top[0], top[1]);
        return t.softFoot(2);
    }

    // ------------------------------------------------------------------------------------------------ trunk

    private void trunk() {
        // the middle line: a random walk that keeps turning, eased between its points (no straight runs, no kinks)
        ctrl[0] = new double[]{0, 0, 0};
        double ang = uniform(0, TAU), ox = 0, oz = 0;
        double[] f = {0.25, 0.5, 0.75, 1.0};
        for (int i = 0; i < 4; i++) {
            ang += (r.nextBoolean() ? 1 : -1) * uniform(1.2, 2.6);
            double len = uniform(1.6, 2.6);
            ox += Math.cos(ang) * len;
            oz += Math.sin(ang) * len;
            ctrl[i + 1] = new double[]{Math.round(h * f[i]), ox, oz};
        }
        for (int y = 0; y < h; y++) {
            double[] c = centre(y);
            double rm = radius(y) * (1 + ridges + buttress) + 1;
            for (int x = Mth.floor(c[0] - rm); x <= Mth.ceil(c[0] + rm); x++) {
                for (int z = Mth.floor(c[1] - rm); z <= Mth.ceil(c[1] + rm); z++) {
                    if (Math.hypot(x - c[0], z - c[1]) <= radiusAt(y, Math.atan2(z - c[1], x - c[0]))) {
                        t.log(x, y, z, WOOD);
                    }
                }
            }
        }
        // short roots out of the buttresses
        double[] foot = centre(0);
        for (int i = 0; i < lobes; i++) {
            double a = (Math.PI / 2 - ph[2] + i * TAU) / lobes + uniform(-0.15, 0.15); // the lobes' outer points
            double d0 = rBase * (1 + buttress) - 0.5, out = uniform(1.5, 2.8);
            t.path(new double[][]{{foot[0] + Math.cos(a) * d0, 1, foot[1] + Math.sin(a) * d0},
                    {foot[0] + Math.cos(a) * (d0 + out), 0, foot[1] + Math.sin(a) * (d0 + out)}}, WOOD, null);
        }
        // burls
        for (int n = randint(3, 5); n > 0; n--) {
            int y = randint(flare, (int) Math.round(h * 0.6));
            double[] c = centre(y);
            double a = uniform(0, TAU), d = radiusAt(y, a) + 0.3, s = uniform(0.9, 1.6);
            double bx = c[0] + Math.cos(a) * d, bz = c[1] + Math.sin(a) * d;
            for (int x = Mth.floor(bx - s); x <= Mth.ceil(bx + s); x++) {
                for (int yy = Mth.floor(y - s * 1.3); yy <= Mth.ceil(y + s * 1.3); yy++) {
                    for (int z = Mth.floor(bz - s); z <= Mth.ceil(bz + s); z++) {
                        if (sq((x - bx) / s) + sq((yy - y) / (s * 1.3)) + sq((z - bz) / s) <= 1) {
                            t.log(x, yy, z, WOOD);
                        }
                    }
                }
            }
        }
        t.cells().forEach((p, c) -> trunkThick.add(p));
        // stubs: short broken-off branches on the bare trunk, some with a tuft of leaves
        for (int n = randint(3, 5); n > 0; n--) {
            int y = randint(flare + 2, (int) Math.round(h * 0.38));
            double[] c = centre(y);
            double a = uniform(0, TAU), d = radiusAt(y, a), length = uniform(2.0, 4.0);
            double[] end = {c[0] + Math.cos(a) * (d + length), y + uniform(0, 2), c[1] + Math.sin(a) * (d + length)};
            tube(new double[][]{{c[0] + Math.cos(a) * (d - 0.5), y, c[1] + Math.sin(a) * (d - 0.5)}, end}, 0.9, 0.5,
                    trunkThick);
            if (r.nextBoolean()) {
                blob(end[0], end[1] + 0.8, end[2], 1.6, 1.2, 1.6, 0.3, null);
            }
        }
    }

    /** x, z of the trunk's middle at height y. */
    private double[] centre(double y) {
        for (int i = 0; i + 1 < ctrl.length; i++) {
            double[] a = ctrl[i], b = ctrl[i + 1];
            if (a[0] <= y && y <= b[0]) {
                double s = (1 - Math.cos(Math.PI * (y - a[0]) / Math.max(1, b[0] - a[0]))) / 2;
                return new double[]{1.5 + a[1] + (b[1] - a[1]) * s, 1.5 + a[2] + (b[2] - a[2]) * s};
            }
        }
        double[] last = ctrl[ctrl.length - 1];
        return new double[]{1.5 + last[1], 1.5 + last[2]};
    }

    /** Mean trunk radius at height y: a slow narrowing from rMid to rTop, plus the flare at the foot. */
    private double radius(double y) {
        double k = Mth.clamp(y / h, 0, 1), f = Math.max(0, 1 - y / flare);
        return rTop + (rMid - rTop) * Math.pow(1 - k, 1.3) + (rBase - rMid) * f * f;
    }

    /** Trunk radius at height y towards angle a: the ridges, and the buttresses at the foot. */
    private double radiusAt(double y, double a) {
        double f = Math.max(0, 1 - y / flare);
        double wave = ridges * (0.6 * Math.sin(3 * a + ph[0] + twist * y) + 0.4 * Math.sin(5 * a + ph[1] - 0.7 * twist * y));
        wave += buttress * f * f * Math.max(0, Math.sin(lobes * a + ph[2]));
        return radius(y) * (1 + wave);
    }

    // ------------------------------------------------------------------------------------------------ limbs

    private void limbs(double r0) {
        int n = randint(5, 7);
        double lo = 0.40, hi = 0.62;
        List<Double> heights = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            heights.add(lo + (hi - lo) * (i + uniform(-0.3, 0.3)) / Math.max(1, n - 1));
        }
        for (int i = n - 1; i > 0; i--) { // shuffled, so the heights go round the trunk in no order
            int j = r.nextInt(i + 1);
            heights.set(i, heights.set(j, heights.get(i)));
        }
        double a0 = uniform(0, TAU);
        for (int i = 0; i < n; i++) {
            double a = a0 + i * TAU / n + uniform(-0.2, 0.2);
            int y = (int) Math.round(h * heights.get(i));
            double[] c = centre(y);
            double rt = radiusAt(y, a), reach = r0 * uniform(1.15, 1.40), drift = uniform(-0.35, 0.35);
            int lift = randint(4, 7);
            Set<BlockPos> before = new HashSet<>(t.cells().keySet());
            // rises out of the trunk, arcs outward, lifts again at the tip
            double[] start = {c[0] + Math.cos(a) * (rt - 0.8), y, c[1] + Math.sin(a) * (rt - 0.8)};
            double[] p1 = {c[0] + Math.cos(a) * reach * 0.3, y + lift * 0.7, c[1] + Math.sin(a) * reach * 0.3};
            double[] p2 = {c[0] + Math.cos(a + drift) * reach * 0.7, y + lift * 0.6, c[1] + Math.sin(a + drift) * reach * 0.7};
            double[] tip = {c[0] + Math.cos(a + drift * 0.6) * reach, y + lift, c[1] + Math.sin(a + drift * 0.6) * reach};
            double limbR = uniform(1.5, 1.9);
            List<double[]> line = tube(new double[][]{start, p1, p2, tip}, limbR, 0.4, limbThick);
            // side branches with a tuft of leaves, and bare twigs
            for (int k = randint(2, 4); k > 0; k--) {
                int j = randint(line.size() / 4, 3 * line.size() / 4);
                double[] p = line.get(j);
                double fa = a + drift + (r.nextBoolean() ? 1 : -1) * uniform(0.6, 1.3), fl = uniform(3.0, 5.5);
                double[] ftip = {p[0] + Math.cos(fa) * fl, p[1] + uniform(1.5, 4.0), p[2] + Math.sin(fa) * fl};
                tube(new double[][]{p, {p[0] + Math.cos(fa) * fl * 0.55, p[1] + 0.6, p[2] + Math.sin(fa) * fl * 0.55}, ftip},
                        Math.max(0.4, (limbR + (0.4 - limbR) * j / line.size()) * 0.55), 0.4, limbThick);
                double s = uniform(2.0, 2.8);
                blob(ftip[0], ftip[1] + 1.0, ftip[2], s, uniform(1.5, 2.0), s, 0.35, Mth.floor(ftip[1] + 0.5));
            }
            for (int k = randint(2, 4); k > 0; k--) {
                double[] p = line.get(randint(line.size() / 5, 4 * line.size() / 5));
                double fa = uniform(0, TAU), len = limbR * 0.6 + uniform(1.0, 2.2);
                t.path(new double[][]{p, {p[0] + Math.cos(fa) * len, p[1] + uniform(0.5, 2.0), p[2] + Math.sin(fa) * len}},
                        WOOD, null);
            }
            for (BlockPos p : t.cells().keySet()) {
                if (!before.contains(p)) {
                    limbCells.add(p);
                }
            }
            crown(tip[0], tip[1] + 1, tip[2], uniform(6.4, 7.6), uniform(4.8, 5.6));
        }
    }

    /**
     * A hollow side crown round x, y, z: a wide puff, a higher one on top (the crown arches) and smaller ones round
     * the rim. Its base is the puffs' own clean shell, leaves two thick over a room that is open underneath where the
     * limb comes in, its underside rounded; on the outside the same puffs warped, with a few lumps on top, add a
     * layer of leaves, a little ragged. Twigs from the limb's tip (one below the middle) span the leaves from the
     * inside.
     */
    private void crown(double cx, double cy, double cz, double rad, double half) {
        List<Ball> balls = new ArrayList<>();
        balls.add(new Ball(cx, cy + 0.6, cz, rad, half, true));
        balls.add(new Ball(cx + uniform(-1.5, 1.5), cy + half * 0.85 + 0.6, cz + uniform(-1.5, 1.5),
                rad * uniform(0.55, 0.7), half * 0.75, false));
        int puffs = randint(4, 6);
        double a0 = uniform(0, TAU);
        for (int i = 0; i < puffs; i++) {
            double a = a0 + i * TAU / puffs + uniform(-0.4, 0.4), d = rad * uniform(0.5, 0.8);
            balls.add(new Ball(cx + Math.cos(a) * d, cy + uniform(-1.2, 1.8), cz + Math.sin(a) * d,
                    rad * uniform(0.45, 0.65), half * uniform(0.6, 0.9), true));
        }
        Hollow base = hollow(new ArrayList<>(balls), 0);
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (BlockPos p : base.shell()) {
            plan.put(p, leafAt(p).defaultBlockState());
        }
        // the looks: whatever of the warped puffs and the lumps lies outside the base; only their shell, so the
        // room stays open underneath
        List<Body> bodies = new ArrayList<>(balls);
        for (int n = randint(2, 4); n > 0; n--) {
            double a = uniform(0, TAU), d = rad * uniform(0.45, 0.75), s = uniform(2.2, 3.2);
            double y = cy + 0.6 + half * Math.sqrt(1 - d * d / (rad * rad)) - s * 0.4;
            bodies.add(new Ball(cx + Math.cos(a) * d, y, cz + Math.sin(a) * d, s, s * 0.8, false));
        }
        Hollow lumpy = hollow(bodies, 1.0);
        for (BlockPos p : lumpy.shell()) {
            if (base.outer().contains(p)) {
                continue;
            }
            boolean outside = lumpy.facesOut(p);
            if (outside && r.nextDouble() < 0.06) {
                continue; // a little ragged
            }
            plan.putIfAbsent(p, leafAt(p).defaultBlockState());
            if (outside && !lumpy.outer().contains(p.above()) && !base.outer().contains(p.above())
                    && r.nextDouble() < 0.02) {
                plan.putIfAbsent(p.above(), leafAt(p.above()).defaultBlockState()); // a tuft on top
            }
        }
        crownLeaves.add(place(plan));
        crownMiddles.add(new double[]{cx, cz});
        // twigs: one to the middle of every puff, from there three out towards the leaves
        double[] tip = {cx, cy - 1, cz};
        for (Ball b : balls) {
            if (b.rx() - SHELL < 1.2) {
                continue;
            }
            double[] fork = {b.x(), b.y(), b.z()};
            t.path(new double[][]{tip, fork}, WOOD, null);
            for (int k = 0; k < 3; k++) {
                double a = uniform(0, TAU), up = uniform(0.2, 0.9), flat = Math.sqrt(1 - up * up);
                double reach = b.rx() - SHELL - 0.2;
                t.path(new double[][]{fork, {b.x() + Math.cos(a) * reach * flat, b.y() + (b.ry() - SHELL - 0.2) * up,
                        b.z() + Math.sin(a) * reach * flat}}, WOOD, null);
            }
        }
        Set<BlockPos> room = base.room();
        // moss with a firefly bush on the limb, inside the room
        if (r.nextDouble() < 0.6) {
            List<int[]> around = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    around.add(new int[]{dx, dz});
                }
            }
            for (int i = around.size() - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                around.set(i, around.set(j, around.get(i)));
            }
            int rx = (int) Math.round(cx), ry = (int) Math.round(cy), rz = (int) Math.round(cz);
            for (int[] o : around) {
                int x = rx + o[0], z = rz + o[1], y = Integer.MIN_VALUE;
                for (int yy = ry - 3; yy < ry + 2; yy++) {
                    if (isLog(new BlockPos(x, yy, z))) {
                        y = yy + 1;
                    }
                }
                BlockPos m = new BlockPos(x, y, z);
                if (y != Integer.MIN_VALUE && free(m) && free(m.above()) && room.contains(m) && room.contains(m.above())) {
                    t.put(x, y, z, Blocks.MOSS_BLOCK.defaultBlockState());
                    t.put(x, y + 1, z, Blocks.FERN.defaultBlockState());
                    break;
                }
            }
        }
        rooms.addAll(room);
    }

    // ------------------------------------------------------------------------------------------------ dome

    private void dome(double r0, int y2, double ccx, double ccz) {
        double[][] bumps = new double[3][];
        int[] ks = {2, 3, 5};
        for (int i = 0; i < 3; i++) {
            bumps[i] = new double[]{uniform(0.04, 0.08), ks[i], uniform(0, TAU)};
        }
        double edgeDepth = uniform(0.8, 2.0); // how much lower the wall hangs, and where
        int edgeK = randint(2, 3);
        double edgePhase = uniform(0, TAU);
        int wallTop = y2 + randint(2, 3), domeTop = h + 1;
        Dome dome = new Dome(ccx, ccz, r0, bumps, wallTop, domeTop, y2, edgeDepth, edgeK, edgePhase);
        double mossOut = mossy.level(0.08, r), mossMid = mossy.level(0.3, r), mossIn = mossy.level(0.5, r);
        // the base: the dome's own clean shell, leaves and moss two thick and closed, moss in patches, most of it on
        // the inside
        double[] bb = dome.box();
        Set<BlockPos> base = new HashSet<>(), inner = new HashSet<>();
        List<BlockPos> baseShell = new ArrayList<>();
        for (int x = Mth.floor(bb[0]) - 2; x <= Mth.ceil(bb[3]) + 2; x++) {
            for (int y = Mth.floor(bb[1]) - 2; y <= Mth.ceil(bb[4]) + 2; y++) {
                for (int z = Mth.floor(bb[2]) - 2; z <= Mth.ceil(bb[5]) + 2; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (dome.inside(x, y, z, SHELL)) {
                        inner.add(p); // the hollow, and the space below it
                    } else if (dome.inside(x, y, z, 0)) {
                        baseShell.add(p);
                    }
                    if (dome.inside(x, y, z, 0)) {
                        base.add(p);
                    }
                }
            }
        }
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (BlockPos p : baseShell) {
            boolean inside = false, outside = false;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                inside |= inner.contains(n);
                outside |= !base.contains(n) && !inner.contains(n);
            }
            boolean mossBlock = mossy.at(p.getX(), p.getY(), p.getZ()) > (inside ? mossIn : outside ? mossOut : mossMid);
            plan.put(p, mossBlock ? Blocks.MOSS_BLOCK.defaultBlockState() : leafAt(p).defaultBlockState());
        }
        // the looks on the outside: the dome warped, with lumps on the cap and the shoulder and a few hanging lower
        // round the rim; whatever of that lies outside the base is filled with leaves, a little ragged, few tufts
        List<Body> bodies = new ArrayList<>();
        bodies.add(dome);
        for (int n = randint(8, 12); n > 0; n--) {
            double a = uniform(0, TAU), y = wallTop + uniform(0, 0.92) * (domeTop - wallTop), s = uniform(3.0, 5.0);
            double d = dome.shellR(y, a) - s * 0.5;
            bodies.add(new Ball(ccx + Math.cos(a) * d, y, ccz + Math.sin(a) * d, s, s * 0.8, false));
        }
        for (int n = randint(2, 4); n > 0; n--) {
            double a = uniform(0, TAU), s = uniform(3.0, 4.0), d = dome.wallR(a) - s * 0.6;
            bodies.add(new Ball(ccx + Math.cos(a) * d, uniform(y2, wallTop), ccz + Math.sin(a) * d, s, s * 0.8, false));
        }
        Hollow lumpy = hollow(bodies, 0.8);
        for (BlockPos p : lumpy.outer()) {
            if (base.contains(p) || inner.contains(p)) {
                continue;
            }
            boolean outside = lumpy.facesOut(p);
            if (outside && r.nextDouble() < 0.05) {
                continue; // a little ragged
            }
            boolean mossBlock = mossy.at(p.getX(), p.getY(), p.getZ()) > mossOut;
            plan.putIfAbsent(p, mossBlock ? Blocks.MOSS_BLOCK.defaultBlockState() : leafAt(p).defaultBlockState());
            if (outside && !lumpy.outer().contains(p.above()) && r.nextDouble() < 0.02) {
                plan.putIfAbsent(p.above(), leafAt(p.above()).defaultBlockState()); // a tuft on top
                if (r.nextDouble() < 0.2) {
                    plan.putIfAbsent(p.above(2), leafAt(p.above(2)).defaultBlockState());
                }
            }
        }
        for (BlockPos p : place(plan)) {
            if (inner.contains(p.below()) && t.cells().get(p).state().is(Blocks.MOSS_BLOCK)) {
                moss.put(p, false); // moss on the inside of the base may grow glow berries
            }
        }
        // ribs from the trunk out to the wall's foot and up under the shell, each forking once
        int ribs = Math.max(12, (int) Math.round(r0 * 0.9));
        double gap = TAU / ribs, a0 = uniform(0, TAU);
        for (int i = 0; i < ribs; i++) {
            double a = a0 + i * gap + uniform(-0.08, 0.08);
            int y = y2 - 3 + randint(-1, 1);
            double[] c = centre(y);
            double rt = radiusAt(y, a), wr = dome.wallR(a), kink = a + uniform(-0.25, 0.25);
            double[] foot = {ccx + Math.cos(a) * (wr - 3.0), y2 - 1, ccz + Math.sin(a) * (wr - 3.0)};
            tube(new double[][]{{c[0] + Math.cos(a) * (rt - 0.5), y - 1, c[1] + Math.sin(a) * (rt - 0.5)},
                    {c[0] + Math.cos(kink) * wr * 0.5, y + 1.5, c[1] + Math.sin(kink) * wr * 0.5}, foot}, 1.1, 0.4, null);
            rib(dome, a, foot, y2);
            // the fork, where the dome starts to close, half way to the next rib
            int fy = wallTop + randint(0, 2);
            double fa = a + (r.nextBoolean() ? 1 : -1) * gap * uniform(0.4, 0.55);
            double rr = dome.shellR(fy + 1, a) - 3.0;
            rib(dome, fa, new double[]{ccx + Math.cos(a) * rr, fy, ccz + Math.sin(a) * rr}, fy + 1);
        }
    }

    /** A rib up under the shell along angle a from prev, a layer at a time. */
    private void rib(Dome dome, double a, double[] prev, int yFrom) {
        for (int y = yFrom; y < dome.domeTop(); y++) {
            double rr = dome.shellR(y + 1, a) - 3.0; // under the shell of this layer and the one above it
            if (rr < 2.5) {
                break;
            }
            double[] next = {dome.cx() + Math.cos(a) * rr, y, dome.cz() + Math.sin(a) * rr};
            t.path(new double[][]{prev, next}, WOOD, Direction.Axis.Y);
            prev = next;
        }
    }

    // ------------------------------------------------------------------------------------------------ strands

    /** Columns up to two out on the four sides: one of them empty marks a column on a crown's rim. */
    private static final int[][] RIM = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};

    /**
     * The weeping trees: from the outer two rings of every side crown, on the half away from the trunk, single strands
     * of leaves hang down, short ones and long ones (3-17 blocks), never into a crown's room and never right beside
     * another.
     */
    private void strands() {
        double[] axis = centre(h * 0.5);
        List<BlockPos> from = new ArrayList<>();
        for (int i = 0; i < crownLeaves.size(); i++) {
            Map<Long, BlockPos> lowest = new LinkedHashMap<>(); // column -> its lowest leaf of this crown
            for (BlockPos p : crownLeaves.get(i)) {
                Shape.Cell c = t.cells().get(p);
                if (c != null && c.kind() == Shape.Kind.LEAF) {
                    lowest.merge(Shape.column(p), p, (a, b) -> a.getY() <= b.getY() ? a : b);
                }
            }
            double[] m = crownMiddles.get(i);
            double out = Math.hypot(m[0] - axis[0], m[1] - axis[1]);
            for (BlockPos p : lowest.values()) {
                boolean rim = false;
                for (int[] o : RIM) {
                    rim |= !lowest.containsKey(BlockPos.asLong(p.getX() + o[0], 0, p.getZ() + o[1]));
                }
                if (rim && Math.hypot(p.getX() - axis[0], p.getZ() - axis[1]) >= out - 1 && !rooms.contains(p.below())
                        && r.nextDouble() < 0.6) {
                    from.add(p);
                }
            }
        }
        TreeShapes.shuffle(from, r);
        t.strands(from, p -> Math.max(2, p.getY() - randint(3, 8) - (r.nextBoolean() ? randint(3, 9) : 0)),
                this::leafAt);
    }

    // ------------------------------------------------------------------------------------------------ hollow crowns

    /** One body of a hollow crown. */
    private interface Body {
        /** Whether x, y, z lies inside the body shrunk by shrink on every side (an open body stays open below). */
        boolean inside(double x, double y, double z, double shrink);

        /** min x, y, z and max x, y, z. */
        double[] box();
    }

    /** An ellipsoid; open ones keep their full depth below the middle when shrunk, so the hollow runs out there. */
    private record Ball(double x, double y, double z, double rx, double ry, boolean open) implements Body {
        @Override
        public boolean inside(double px, double py, double pz, double shrink) {
            double sx = rx - shrink, sy = open && shrink > 0 && py < y ? ry : ry - shrink;
            return sx > 0.3 && sy > 0.3 && sq((px - x) / sx) + sq((py - y) / sy) + sq((pz - z) / sx) <= 1;
        }

        @Override
        public double[] box() {
            return new double[]{x - rx, y - ry, z - rx, x + rx, y + ry, z + rx};
        }
    }

    /**
     * The main dome: a wall of radius wallR (with bumps) up to wallTop, its lower edge uneven, then a cap that
     * closes at domeTop. Shrunk, it is open below.
     */
    private record Dome(double cx, double cz, double r0, double[][] bumps, int wallTop, int domeTop, int y2,
                        double edgeDepth, int edgeK, double edgePhase) implements Body {
        double wallR(double a) {
            double w = 1;
            for (double[] b : bumps) {
                w += b[0] * Math.sin(b[1] * a + b[2]);
            }
            return r0 * w;
        }

        double shellR(double y, double a) {
            if (y <= wallTop) {
                return wallR(a);
            }
            double k = (y - wallTop) / (domeTop - wallTop);
            return wallR(a) * Math.sqrt(Math.max(0, 1 - k * k));
        }

        @Override
        public boolean inside(double x, double y, double z, double shrink) {
            double a = Math.atan2(z - cz, x - cx), d = Math.hypot(x - cx, z - cz), rr = wallR(a) - shrink;
            if (shrink == 0 && y < y2 - 1 - Math.round(edgeDepth * (1 + Math.sin(edgeK * a + edgePhase)))) {
                return false;
            }
            if (y <= wallTop) {
                return d <= rr;
            }
            double k = (y - wallTop) / (domeTop - wallTop - shrink);
            return k <= 1 && d <= rr * Math.sqrt(Math.max(0, 1 - k * k));
        }

        @Override
        public double[] box() {
            double m = r0 * 1.12;
            return new double[]{cx - m, y2 - 4, cz - m, cx + m, domeTop, cz + m};
        }
    }

    /**
     * Sets the planned leaves and moss where nothing is yet, all but loose bits: only the biggest face-connected
     * group of the plan stays (warping and the ragged edge can leave single blocks floating). Returns the cells set.
     */
    private List<BlockPos> place(Map<BlockPos, BlockState> plan) {
        Set<BlockPos> all = plan.keySet(), seen = new HashSet<>(), best = Set.of();
        for (BlockPos start : all) {
            if (!seen.add(start)) {
                continue;
            }
            Set<BlockPos> group = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                group.add(p);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (all.contains(n) && seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            if (group.size() > best.size()) {
                best = group;
            }
        }
        List<BlockPos> set = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockPos p = e.getKey();
            if (!best.contains(p) || !free(p)) {
                continue;
            }
            BlockState s = e.getValue();
            if (s.getBlock() instanceof LeavesBlock) {
                t.leaf(p.getX(), p.getY(), p.getZ(), s.getBlock());
            } else {
                t.put(p.getX(), p.getY(), p.getZ(), s);
            }
            set.add(p);
        }
        return set;
    }

    /** The leaves of a hollow crown (in order), everything inside its outline, and the room in it. */
    private record Hollow(List<BlockPos> shell, Set<BlockPos> outer, Set<BlockPos> room) {
        /** Whether p borders the open air outside. */
        boolean facesOut(BlockPos p) {
            for (Direction d : Direction.values()) {
                if (!outer.contains(p.relative(d))) {
                    return true;
                }
            }
            return false;
        }

        /** Whether p borders the room. */
        boolean facesIn(BlockPos p) {
            for (Direction d : Direction.values()) {
                if (room.contains(p.relative(d))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Shells SHELL thick round the union of the bodies, hollow inside. Space is warped by up to about warp blocks
     * first (the same for the outline and the hollow, so the leaves stay two thick), so no outline runs straight.
     */
    private Hollow hollow(List<Body> bodies, double warp) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (Body b : bodies) {
            double[] bb = b.box();
            for (int k = 0; k < 3; k++) {
                box[k] = Math.min(box[k], bb[k]);
                box[k + 3] = Math.max(box[k + 3], bb[k + 3]);
            }
        }
        double m = warp * 2.5 + 1;
        List<BlockPos> shell = new ArrayList<>();
        Set<BlockPos> outer = new LinkedHashSet<>(), room = new HashSet<>();
        for (int x = Mth.floor(box[0] - m); x <= Mth.ceil(box[3] + m); x++) {
            for (int y = Mth.floor(box[1] - m); y <= Mth.ceil(box[4] + m); y++) {
                for (int z = Mth.floor(box[2] - m); z <= Mth.ceil(box[5] + m); z++) {
                    double wx = x + warp * warpX.at(x, y, z), wy = y + warp * 0.7 * warpY.at(x, y, z),
                            wz = z + warp * warpZ.at(x, y, z);
                    boolean in = false, hollow = false;
                    for (Body b : bodies) {
                        in |= !in && b.inside(wx, wy, wz, 0);
                        hollow |= !hollow && b.inside(wx, wy, wz, SHELL);
                        if (in && hollow) {
                            break;
                        }
                    }
                    if (!in) {
                        continue;
                    }
                    BlockPos p = new BlockPos(x, y, z);
                    outer.add(p);
                    if (hollow) {
                        room.add(p);
                    } else {
                        shell.add(p);
                    }
                }
            }
        }
        return new Hollow(shell, outer, room);
    }

    /** A smooth random field: plane waves in random directions about wavelength apart, values spread round 0. */
    private static final class Waves {
        private final double[][] waves = new double[5][];

        Waves(RandomSource r, double wavelength, double yStretch) {
            for (int i = 0; i < waves.length; i++) {
                double kx = r.nextGaussian(), ky = r.nextGaussian() / yStretch, kz = r.nextGaussian();
                double k = TAU / (wavelength * (0.8 + r.nextDouble() * 0.45)) / Math.sqrt(kx * kx + ky * ky + kz * kz);
                waves[i] = new double[]{kx * k, ky * k, kz * k, r.nextDouble() * TAU};
            }
        }

        double at(double x, double y, double z) {
            double v = 0;
            for (double[] w : waves) {
                v += Math.sin(w[0] * x + w[1] * y + w[2] * z + w[3]);
            }
            return v / Math.sqrt(waves.length / 2.0);
        }

        /** The value that about the given share of all points lies above. */
        double level(double share, RandomSource r) {
            double[] v = new double[2000];
            for (int i = 0; i < v.length; i++) {
                v[i] = at(r.nextDouble() * 200, r.nextDouble() * 200, r.nextDouble() * 200);
            }
            Arrays.sort(v);
            return v[(int) ((1 - share) * (v.length - 1))];
        }
    }

    // ------------------------------------------------------------------------------------------------ bark

    /**
     * Dark oak slabs in the steps of the outline, so it goes in half blocks there: a slab on a step's tread, an upper
     * slab under an overhang. 160-200 in all, three quarters of them on the limbs and side branches, the rest on the
     * trunk and its roots.
     */
    private void slabs() {
        List<BlockPos> onLimbs = steps(limbThick), onTrunk = steps(trunkThick);
        int budget = randint(160, 200), limbs = Math.min(onLimbs.size(), budget * 3 / 4);
        List<BlockPos> chosen = new ArrayList<>(onLimbs.subList(0, limbs));
        chosen.addAll(onTrunk.subList(0, Math.min(onTrunk.size(), budget - limbs)));
        for (BlockPos c : chosen) {
            if (free(c)) {
                t.put(c.getX(), c.getY(), c.getZ(), Blocks.DARK_OAK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, isLog(c.below()) ? SlabType.BOTTOM : SlabType.TOP));
            }
        }
    }

    /** The steps round the given wood in random order: free cells on or under it with wood beside them. */
    private List<BlockPos> steps(Set<BlockPos> wood) {
        Set<BlockPos> spots = new LinkedHashSet<>();
        for (BlockPos p : wood) {
            spots.add(p.above());
            spots.add(p.below());
        }
        List<BlockPos> steps = new ArrayList<>();
        for (BlockPos c : spots) {
            if (c.getY() < 0 || !free(c) || isLog(c.below()) == isLog(c.above())) {
                continue;
            }
            int walls = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (isLog(c.relative(d))) {
                    walls++;
                }
            }
            if (walls > 0 && walls < 4) {
                steps.add(c);
            }
        }
        for (int i = steps.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            steps.set(i, steps.set(j, steps.get(i)));
        }
        return steps;
    }

    /** Stripped dark oak in long streaks up the bark, about a fifth of the wood. */
    private void strip() {
        double level = bark.level(0.2, r);
        List<BlockPos> logs = new ArrayList<>();
        t.cells().forEach((p, c) -> {
            if (c.kind() == Shape.Kind.LOG) {
                logs.add(p);
            }
        });
        for (BlockPos p : logs) {
            if (bark.at(p.getX(), p.getY(), p.getZ()) > level) {
                t.replaceLog(p, Blocks.STRIPPED_DARK_OAK_WOOD);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ hangings

    private void hangings(double r0, double ccx, double ccz) {
        List<BlockPos> leaves = new ArrayList<>();
        t.cells().forEach((p, c) -> {
            if (c.kind() == Shape.Kind.LEAF) {
                leaves.add(p);
            }
        });
        // moss under the side crowns grows glow berries, short ones in the rooms (light, but room to build); a
        // quarter of the moss inside the dome grows long ones
        for (BlockPos p : leaves) {
            if (free(p.below()) && rooms.contains(p.below()) && r.nextDouble() < 0.03) {
                t.put(p.getX(), p.getY(), p.getZ(), Blocks.MOSS_BLOCK.defaultBlockState());
                moss.put(p, true);
            }
        }
        moss.forEach((p, room) -> {
            if (!free(p.below())) {
                return;
            }
            if (room) {
                caveVines(p.below(), randint(1, 4), 0.09);
            } else if (r.nextDouble() < 0.25) {
                caveVines(p.below(), randint(3, Math.max(3, Math.min(24, p.getY() - 2))), 0.12);
            }
        });
        // glow berries from the undersides of the limbs and the side branches
        for (BlockPos p : limbCells) {
            if (isLog(p) && free(p.below()) && r.nextDouble() < 0.03) {
                caveVines(p.below(), randint(3, 14), 0.3);
            }
        }
        // vines on the outer side of rim leaves: air below the leaf and beside it, and no canopy over the vine
        for (BlockPos p : leaves) {
            Shape.Cell cell = t.cells().get(p);
            if (cell == null || cell.kind() != Shape.Kind.LEAF || !free(p.below())) {
                continue;
            }
            double dx = p.getX() - ccx, dz = p.getZ() - ccz;
            if (Math.hypot(dx, dz) < r0 * 0.6) {
                continue;
            }
            Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos v = p.relative(out);
            boolean open = free(v);
            for (int k = 1; k <= 4 && open; k++) {
                open = free(v.above(k));
            }
            if (!open || r.nextDouble() > 0.25) {
                continue;
            }
            // hangs on the leaf behind it; further down each vine holds on to the one above
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
            int length = r.nextDouble() < 0.35 ? p.getY() + 1 : randint(2, 10);
            for (int y = p.getY(); y > Math.max(-1, p.getY() - length); y--) {
                if (!free(new BlockPos(v.getX(), y, v.getZ()))) {
                    break;
                }
                t.put(v.getX(), y, v.getZ(), vine);
            }
        }
    }

    /** A glow berry vine hanging down from top, up to length long; the lowest block is the vine's head. */
    private void caveVines(BlockPos top, int length, double berries) {
        List<BlockPos> column = new ArrayList<>();
        for (int i = 0; i < length && top.getY() - i >= 0; i++) {
            BlockPos p = top.below(i);
            if (!free(p)) {
                break;
            }
            column.add(p);
        }
        for (int i = 0; i < column.size(); i++) {
            BlockPos p = column.get(i);
            BlockState s = i < column.size() - 1 ? Blocks.CAVE_VINES_PLANT.defaultBlockState()
                    : Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE);
            t.put(p.getX(), p.getY(), p.getZ(), s.setValue(CaveVines.BERRIES, r.nextDouble() < berries));
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /**
     * A round limb along a Bezier curve through the points, radius r0 at the start narrowing to r1; a face-connected
     * core line keeps it whole where it gets thin. Its thick part goes into thick (if given). Returns the curve's
     * sample points.
     */
    private List<double[]> tube(double[][] pts, double r0, double r1, Set<BlockPos> thick) {
        double length = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            length += Math.sqrt(sq(pts[i + 1][0] - pts[i][0]) + sq(pts[i + 1][1] - pts[i][1]) + sq(pts[i + 1][2] - pts[i][2]));
        }
        int n = Math.max(2, (int) (length * 3));
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            line.add(bezier(pts, (double) i / n));
        }
        t.path(line.toArray(new double[0][]), WOOD, null);
        for (int i = 0; i <= n; i++) {
            double[] p = line.get(i);
            double rad = r0 + (r1 - r0) * i / n;
            if (rad < 0.7) {
                continue;
            }
            for (int x = Mth.floor(p[0] - rad); x <= Mth.ceil(p[0] + rad); x++) {
                for (int y = Mth.floor(p[1] - rad); y <= Mth.ceil(p[1] + rad); y++) {
                    for (int z = Mth.floor(p[2] - rad); z <= Mth.ceil(p[2] + rad); z++) {
                        if (sq(x - p[0]) + sq(y - p[1]) + sq(z - p[2]) <= rad * rad) {
                            t.log(x, y, z, WOOD);
                            if (thick != null) {
                                thick.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return line;
    }

    private static double[] bezier(double[][] pts, double s) {
        int n = pts.length - 1;
        double[] out = new double[3];
        for (int i = 0; i <= n; i++) {
            double w = binomial(n, i) * Math.pow(1 - s, n - i) * Math.pow(s, i);
            for (int k = 0; k < 3; k++) {
                out[k] += w * pts[i][k];
            }
        }
        return out;
    }

    private static int binomial(int n, int k) {
        int b = 1;
        for (int i = 1; i <= k; i++) {
            b = b * (n - i + 1) / i;
        }
        return b;
    }

    /** Ellipsoid of mixed leaves; the rim is ragged (erode) and everything below floor is cut off. */
    private void blob(double cx, double cy, double cz, double rx, double ry, double rz, double erode, Integer floor) {
        for (int x = Mth.floor(cx - rx); x <= Mth.ceil(cx + rx); x++) {
            for (int y = Mth.floor(cy - ry); y <= Mth.ceil(cy + ry); y++) {
                if (floor != null && y < floor) {
                    continue;
                }
                for (int z = Mth.floor(cz - rz); z <= Mth.ceil(cz + rz); z++) {
                    double d = sq((x - cx) / rx) + sq((y - cy) / ry) + sq((z - cz) / rz);
                    if (d > 1 || (d > 0.5 && r.nextDouble() < erode * (d - 0.35) * 2)) {
                        continue;
                    }
                    t.leaf(x, y, z, leafAt(new BlockPos(x, y, z)));
                }
            }
        }
    }

    /** Jungle leaves in patches (about a fifth), elsewhere azalea leaves, a quarter of them flowering. */
    private Block leafAt(BlockPos p) {
        if (jungle.at(p.getX(), p.getY(), p.getZ()) > jungleLevel) {
            return Blocks.JUNGLE_LEAVES;
        }
        return r.nextDouble() < 0.25 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
    }

    private boolean free(BlockPos p) {
        return !t.cells().containsKey(p);
    }

    private boolean isLog(BlockPos p) {
        Shape.Cell c = t.cells().get(p);
        return c != null && c.kind() == Shape.Kind.LOG;
    }

    private int randint(int a, int b) {
        return TreeShapes.randint(r, a, b);
    }

    private double uniform(double a, double b) {
        return TreeShapes.uniform(r, a, b);
    }

    private static double sq(double v) {
        return v * v;
    }
}
