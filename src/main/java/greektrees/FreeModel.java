package greektrees;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A block model whose boxes are turned freely about all three axes, the way the newer Minecraft versions' model
 * files allow: 1.21.1 turns a box about one axis in steps of 22.5 degrees only, which the date cluster does not
 * fit into. Read from {@code assets/greektrees/free_models/<name>.json} (written by tools/make_dates.py) and
 * baked here; the block state names it as {@code greektrees:block/<name>}.
 */
// ponytail: read from the mod's own jar, so a resource pack cannot replace the shape (the texture it can)
final class FreeModel implements UnbakedModel {
    private static final FaceBakery BAKERY = new FaceBakery();

    /** A box in sixteenths of a block; turn is null for a box that stands straight. */
    private record Box(Vector3f from, Vector3f to, Map<Direction, float[]> uvs, Vector3f origin, Matrix4f turn) {
    }

    private final ResourceLocation texture;
    private final List<Box> boxes = new ArrayList<>();

    FreeModel(String name) {
        JsonObject json;
        try (Reader in = new InputStreamReader(FreeModel.class.getResourceAsStream(
                "/assets/" + GreekTrees.MOD_ID + "/free_models/" + name + ".json"), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(in).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        texture = ResourceLocation.parse(json.getAsJsonObject("textures").get("cluster").getAsString());
        for (JsonElement element : json.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            Map<Direction, float[]> uvs = new EnumMap<>(Direction.class);
            box.getAsJsonObject("faces").entrySet().forEach(face -> {
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                uvs.put(Direction.byName(face.getKey()), new float[]{uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        uv.get(2).getAsFloat(), uv.get(3).getAsFloat()});
            });
            JsonObject rotation = box.getAsJsonObject("rotation");
            // the newer versions' order: about x first, then y, then z
            boxes.add(new Box(vector(box.getAsJsonArray("from")), vector(box.getAsJsonArray("to")), uvs,
                    rotation == null ? null : vector(rotation.getAsJsonArray("origin")),
                    rotation == null ? null : new Matrix4f().rotationZYX(radians(rotation, "z"),
                            radians(rotation, "y"), radians(rotation, "x"))));
        }
    }

    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static float radians(JsonObject rotation, String axis) {
        return (float) Math.toRadians(rotation.get(axis).getAsFloat());
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> models) {
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        TextureAtlasSprite sprite = sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        List<BakedQuad> quads = new ArrayList<>();
        Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            culled.put(side, List.of());
        }
        Matrix4f facing = state.getRotation().getMatrix(); // the block state's turn, about the block's middle
        for (Box box : boxes) {
            for (Map.Entry<Direction, float[]> face : box.uvs().entrySet()) {
                // vanilla lays out the corners and the texture of the straight box; the turns are done here
                int[] vertices = BAKERY.bakeQuad(box.from(), box.to(), new BlockElementFace(null, -1, "",
                        new BlockFaceUV(face.getValue(), 0)), sprite, face.getKey(), BlockModelRotation.X0_Y0, null,
                        true).getVertices().clone();
                for (int i = 0; i < 32; i += 8) {
                    Vector3f p = new Vector3f(Float.intBitsToFloat(vertices[i]), Float.intBitsToFloat(vertices[i + 1]),
                            Float.intBitsToFloat(vertices[i + 2]));
                    if (box.turn() != null) {
                        p.mul(16).sub(box.origin()).mulPosition(box.turn()).add(box.origin()).div(16);
                    }
                    p.sub(0.5f, 0.5f, 0.5f).mulPosition(facing).add(0.5f, 0.5f, 0.5f);
                    vertices[i] = Float.floatToRawIntBits(p.x);
                    vertices[i + 1] = Float.floatToRawIntBits(p.y);
                    vertices[i + 2] = Float.floatToRawIntBits(p.z);
                }
                quads.add(new BakedQuad(vertices, -1, FaceBakery.calculateFacing(vertices), sprite, true));
            }
        }
        return new SimpleBakedModel(quads, culled, false, true, false, sprite, ItemTransforms.NO_TRANSFORMS,
                ItemOverrides.EMPTY);
    }
}
