package com.gnottero.blossom.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

/**
 * A shape to show to someone for a while.
 *
 * <p>It is a value, not a live object: it is born from {@code draw_shape}'s arguments, travels
 * over the network as NBT, and disappears when it expires. The server only keeps the list of
 * still-alive ones, to redraw them as particles for whoever lacks the client.
 *
 * <p>Every shape knows how to do three things: write itself as NBT (so a Blossom client can draw
 * it for real), say which <em>segments</em> it is made of (particle approximation on any client),
 * and which <em>faces</em> it has (the fill). Whoever writes the script draws a sphere; what
 * happens on the other end is not their concern.
 *
 * <p>With a {@code follow}, coordinates are relative to the entity, added axis by axis according
 * to {@code snap}. The calculation lives in {@link #anchored} rather than in the two renderers,
 * because server and client must land on the same point.
 */
public abstract class Shape
{
    /** The types that can be written in {@code draw_shape}. */
    public static final List<String> TYPES =
            List.of("line", "box", "sphere", "cylinder", "label", "block", "item");

    /**
     * What applies to every shape, regardless of its geometry.
     *
     * @param color        the color of lines and text, {@code 0xRRGGBBAA}
     * @param fillColor    the fill color; zero alpha means "don't fill"
     * @param lineWidth    the thickness of the lines
     * @param expiresAtTick the server tick at which the shape stops existing
     * @param follow       the id of the entity the shape is anchored to, {@code -1} if none
     * @param snap         which axes follow the entity, and which are rounded to the block
     * @param debug        whether the shape is only visible with debug hitboxes enabled
     */
    public record Common(int color, int fillColor, float lineWidth, long expiresAtTick,
            int follow, String snap, boolean debug)
    {
        public static Common of(int color, int fillColor, float lineWidth, long expiresAtTick)
        {
            return new Common(color, fillColor, lineWidth, expiresAtTick, -1, "xyz", false);
        }
    }

    private final String type;
    private final Common common;
    private final boolean snapX;
    private final boolean snapY;
    private final boolean snapZ;
    private final boolean discreteX;
    private final boolean discreteY;
    private final boolean discreteZ;

    protected Shape(String type, Common common)
    {
        this.type = type;
        this.common = common;
        String snap = common.snap() == null ? "xyz" : common.snap().toLowerCase(Locale.ROOT);
        // 'dx' is 'x' rounded to the block: rounding implies following, it does not replace it
        this.snapX = snap.contains("x");
        this.snapY = snap.contains("y");
        this.snapZ = snap.contains("z");
        this.discreteX = snap.contains("dx");
        this.discreteY = snap.contains("dy");
        this.discreteZ = snap.contains("dz");
    }

    public String type()
    {
        return type;
    }

    public int color()
    {
        return common.color();
    }

    public float lineWidth()
    {
        return common.lineWidth();
    }

    public int fillColor()
    {
        return common.fillColor();
    }

    /** @return whether the fill is visible: zero alpha means there is none */
    public boolean hasFill()
    {
        return (common.fillColor() & 0xFF) != 0;
    }

    /**
     * @return whether the fill is opaque, that is it covers what is behind it instead of letting it
     *         show through
     */
    public boolean hasOpaqueFill()
    {
        return (common.fillColor() & 0xFF) == 0xFF;
    }

    /**
     * @return whether the wireframe is visible: zero alpha in the color means "fill only", and is
     *         the way to ask for a solid shape without edges
     */
    public boolean hasLines()
    {
        return (common.color() & 0xFF) != 0;
    }

    public long expiresAtTick()
    {
        return common.expiresAtTick();
    }

    /** the id of the entity the shape is anchored to, {@code -1} if it stands still in the world */
    public int follow()
    {
        return common.follow();
    }

    public boolean debug()
    {
        return common.debug();
    }

    public boolean isAliveAt(long tick)
    {
        return tick < common.expiresAtTick();
    }

    /**
     * The absolute position of a point of the shape.
     *
     * @param followPos the position of the followed entity, or {@code null} if there is none (or it
     *                  is gone: in that case the shape stays where it is instead of collapsing onto
     *                  the origin)
     */
    public Vec3 anchored(Vec3 point, @org.jspecify.annotations.Nullable Vec3 followPos)
    {
        if (common.follow() < 0 || followPos == null)
        {
            return point;
        }
        return point.add(
                snapX ? (discreteX ? Math.floor(followPos.x) : followPos.x) : 0.0,
                snapY ? (discreteY ? Math.floor(followPos.y) : followPos.y) : 0.0,
                snapZ ? (discreteZ ? Math.floor(followPos.z) : followPos.z) : 0.0);
    }

    /**
     * The segments the shape is made of, in unanchored coordinates.
     *
     * <p>A sphere and a cylinder are not made of segments: what they return are their circles
     * broken into chords. That is the compromise of drawing with lines, and it is better stated
     * here than pretended elsewhere.
     */
    public abstract List<Segment> segments();

    /**
     * The segments to approximate with particles, in unanchored coordinates.
     *
     * <p>Usually the same as {@link #segments()}, but not for round shapes: the wireframe of a
     * sphere with a high {@code level} is thousands of segments, and one particle every half block
     * along each of them would bury the server in packets to draw something particles cannot render
     * at that resolution anyway. Whoever lacks the client sees the outline, that is the main
     * circles, and whoever has it sees the whole wireframe.
     */
    public List<Segment> particleOutline()
    {
        return segments();
    }

    /**
     * The faces the shape is made of, for the fill. Empty if the shape has no inside.
     *
     * <p>Every face is a convex polygon with its vertices in order: four points are a quad, more
     * are a triangle fan. There is no face when {@code fill} is transparent, but that check belongs
     * to whoever draws: here only the geometry is declared.
     */
    public List<Vec3[]> faces()
    {
        return List.of();
    }

    /** A segment between two points: the only primitive particles can draw. */
    public record Segment(Vec3 from, Vec3 to)
    {
    }

    /**
     * The shape as NBT, for the client.
     *
     * <p>What travels over the network is the <em>remaining duration</em> and not the expiry
     * instant: server and client count ticks from different moments, and sending an absolute number
     * would mean asking the two clocks to agree. The duration is counted from arrival instead, and
     * is off by the packet flight time at most.
     */
    public CompoundTag toTag(long nowTick)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type);
        tag.putInt("color", common.color());
        tag.putInt("fill", common.fillColor());
        tag.putFloat("line", common.lineWidth());
        tag.putLong("duration", Math.max(0L, common.expiresAtTick() - nowTick));
        tag.putInt("follow", common.follow());
        tag.putString("snap", common.snap());
        tag.putBoolean("debug", common.debug());
        writeGeometry(tag);
        return tag;
    }

    protected abstract void writeGeometry(CompoundTag tag);

    /**
     * Rebuilds a shape from its NBT.
     *
     * <p>The client calls it, and it reuses exactly the classes the server used to build the shape:
     * the two sides cannot diverge on what a sphere is, because it is the same class. It is also
     * why the client side drawing is short, since the geometry already lives here, in
     * {@link #segments()} and {@link #faces()}.
     *
     * @param nowTick the client tick the shape arrives at: the duration is counted from there
     * @return the shape, or {@code null} if the type is unknown (a server newer than the client)
     */
    public static @org.jspecify.annotations.Nullable Shape fromTag(CompoundTag tag, long nowTick)
    {
        String type = tag.getStringOr("type", "");
        Common common = new Common(
                tag.getIntOr("color", 0xFFFFFFFF),
                tag.getIntOr("fill", 0),
                tag.getFloatOr("line", 2.0F),
                nowTick + tag.getLongOr("duration", 0L),
                tag.getIntOr("follow", -1),
                tag.getStringOr("snap", "xyz"),
                tag.getBooleanOr("debug", false));
        return switch (type)
        {
            case "line" -> new Line(vec(tag, "from"), vec(tag, "to"), common);
            case "box" -> new Box(vec(tag, "from"), vec(tag, "to"), common);
            case "sphere" -> new Sphere(vec(tag, "center"), tag.getDoubleOr("radius", 1.0),
                    tag.getIntOr("level", 0), common);
            case "cylinder" -> new Cylinder(vec(tag, "center"), tag.getDoubleOr("radius", 1.0),
                    tag.getDoubleOr("height", 0.0), tag.getStringOr("axis", "y"),
                    tag.getIntOr("level", 0), common);
            case "label" -> new Label(vec(tag, "pos"), tag.getStringOr("text", ""),
                    tag.get("component"),
                    new Label.Text(
                            tag.getFloatOr("size", Label.DEFAULT_SIZE),
                            tag.getIntOr("align", 0),
                            tag.getFloatOr("indent", 0.0F),
                            tag.getFloatOr("height", 0.0F),
                            tag.getFloatOr("raise", 0.0F),
                            tag.getFloatOr("tilt", 0.0F),
                            tag.getFloatOr("lean", 0.0F),
                            tag.getFloatOr("turn", 0.0F),
                            tag.getStringOr("facing", "player"),
                            tag.getBooleanOr("doublesided", false)),
                    common);
            case "block", "item" -> new Sprite(type, vec(tag, "pos"), tag.getStringOr("name", ""),
                    tag.getCompound("data").orElse(null),
                    new Sprite.Pose(
                            tag.getStringOr("facing", "north"),
                            tag.getFloatOr("tilt", 0.0F),
                            tag.getFloatOr("lean", 0.0F),
                            tag.getFloatOr("turn", 0.0F),
                            tag.getFloatOr("scalex", 1.0F),
                            tag.getFloatOr("scaley", 1.0F),
                            tag.getFloatOr("scalez", 1.0F),
                            tag.getIntOr("blocklight", -1),
                            tag.getIntOr("skylight", -1),
                            tag.getStringOr("variant", "none")),
                    common);
            default -> null;
        };
    }

    private static Vec3 vec(CompoundTag tag, String key)
    {
        java.util.Optional<ListTag> list = tag.getList(key);
        if (list.isEmpty() || list.get().size() < 3)
        {
            return Vec3.ZERO;
        }
        ListTag values = list.get();
        return new Vec3(values.getDoubleOr(0, 0), values.getDoubleOr(1, 0), values.getDoubleOr(2, 0));
    }

    protected static void putVec(CompoundTag tag, String key, Vec3 vec)
    {
        ListTag list = new ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.y));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(vec.z));
        tag.put(key, list);
    }

    // --- the shapes ---

    /** A segment between two points. */
    public static final class Line extends Shape
    {
        private final Vec3 from;
        private final Vec3 to;

        public Line(Vec3 from, Vec3 to, Common common)
        {
            super("line", common);
            this.from = from;
            this.to = to;
        }

        @Override
        public List<Segment> segments()
        {
            return List.of(new Segment(from, to));
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "from", from);
            putVec(tag, "to", to);
        }
    }

    /** The box between two corners: twelve edges, and six faces when there is a fill. */
    public static final class Box extends Shape
    {
        private final Vec3 from;
        private final Vec3 to;

        public Box(Vec3 from, Vec3 to, Common common)
        {
            super("box", common);
            this.from = new Vec3(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z));
            this.to = new Vec3(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
        }

        @Override
        public List<Segment> segments()
        {
            List<Segment> edges = new ArrayList<>(12);
            double[] xs = {from.x, to.x};
            double[] ys = {from.y, to.y};
            double[] zs = {from.z, to.z};
            for (double y : ys)
            {
                for (double z : zs)
                {
                    edges.add(new Segment(new Vec3(xs[0], y, z), new Vec3(xs[1], y, z)));
                }
            }
            for (double x : xs)
            {
                for (double z : zs)
                {
                    edges.add(new Segment(new Vec3(x, ys[0], z), new Vec3(x, ys[1], z)));
                }
            }
            for (double x : xs)
            {
                for (double y : ys)
                {
                    edges.add(new Segment(new Vec3(x, y, zs[0]), new Vec3(x, y, zs[1])));
                }
            }
            return edges;
        }

        @Override
        public List<Vec3[]> faces()
        {
            List<Vec3[]> quads = new ArrayList<>(6);
            quads.add(new Vec3[]{
                    new Vec3(from.x, from.y, from.z), new Vec3(to.x, from.y, from.z),
                    new Vec3(to.x, from.y, to.z), new Vec3(from.x, from.y, to.z)});
            quads.add(new Vec3[]{
                    new Vec3(from.x, to.y, from.z), new Vec3(from.x, to.y, to.z),
                    new Vec3(to.x, to.y, to.z), new Vec3(to.x, to.y, from.z)});
            quads.add(new Vec3[]{
                    new Vec3(from.x, from.y, from.z), new Vec3(from.x, to.y, from.z),
                    new Vec3(to.x, to.y, from.z), new Vec3(to.x, from.y, from.z)});
            quads.add(new Vec3[]{
                    new Vec3(from.x, from.y, to.z), new Vec3(to.x, from.y, to.z),
                    new Vec3(to.x, to.y, to.z), new Vec3(from.x, to.y, to.z)});
            quads.add(new Vec3[]{
                    new Vec3(from.x, from.y, from.z), new Vec3(from.x, from.y, to.z),
                    new Vec3(from.x, to.y, to.z), new Vec3(from.x, to.y, from.z)});
            quads.add(new Vec3[]{
                    new Vec3(to.x, from.y, from.z), new Vec3(to.x, to.y, from.z),
                    new Vec3(to.x, to.y, to.z), new Vec3(to.x, from.y, to.z)});
            return quads;
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "from", from);
            putVec(tag, "to", to);
        }
    }

    /**
     * How many sides per circle, when the script does not say.
     *
     * <p>Grows with the square root of the radius: a large sphere seen up close needs more sides
     * not to look like a polygon, a small one does not and they are not worth paying for. The
     * number can always be forced with {@code level}.
     */
    static int subdivisionsFor(int level, double radius)
    {
        return level > 0 ? level : Math.max(10, (int) (10 * Math.sqrt(radius)));
    }

    /** A sphere around a center: a wireframe of meridians and parallels, and the same solid skin. */
    public static final class Sphere extends Shape
    {
        private final Vec3 center;
        private final double radius;
        private final int level;

        public Sphere(Vec3 center, double radius, int level, Common common)
        {
            super("sphere", common);
            this.center = center;
            this.radius = radius;
            this.level = level;
        }

        private int sides()
        {
            return subdivisionsFor(level, radius);
        }

        /** how many rings from pole to pole: half a turn instead of a full one, so half the sides */
        private static int rings(int sides)
        {
            return Math.max(3, sides / 2);
        }

        /**
         * The wireframe: one meridian per side, and the parallels between one ring and the next.
         *
         * <p>It is the same subdivision as the faces, and not by accident: {@code level} decides how
         * dense the sphere is, and wireframe and fill have to be the same sphere. Three circles
         * alone, as it was before, made {@code level} look like it only affected the fill.
         */
        @Override
        public List<Segment> segments()
        {
            int sides = sides();
            int rings = rings(sides);
            List<Segment> edges = new ArrayList<>(sides * rings * 2);
            for (int side = 0; side < sides; side++)
            {
                double theta = 2 * Math.PI * side / sides;
                Vec3 previous = null;
                for (int ring = 0; ring <= rings; ring++)
                {
                    Vec3 point = onSphere(Math.PI * ring / rings - Math.PI / 2, theta);
                    if (previous != null)
                    {
                        edges.add(new Segment(previous, point));
                    }
                    previous = point;
                }
            }
            // the poles are points, not circles: the parallels start from the first ring after
            for (int ring = 1; ring < rings; ring++)
            {
                double phi = Math.PI * ring / rings - Math.PI / 2;
                Vec3 previous = null;
                for (int side = 0; side <= sides; side++)
                {
                    Vec3 point = onSphere(phi, 2 * Math.PI * side / sides);
                    if (previous != null)
                    {
                        edges.add(new Segment(previous, point));
                    }
                    previous = point;
                }
            }
            return edges;
        }

        /** The outline for particles: the three great circles, one per plane. */
        @Override
        public List<Segment> particleOutline()
        {
            int sides = sides();
            List<Segment> edges = new ArrayList<>(sides * 3);
            addCircle(edges, center, radius, 0, sides);
            addCircle(edges, center, radius, 1, sides);
            addCircle(edges, center, radius, 2, sides);
            return edges;
        }

        @Override
        public List<Vec3[]> faces()
        {
            // the meshed skin: the parallels cut by the meridians. With few sides the polyhedron
            // shows, and that is right, since it is the same subdivision as the lines
            int sides = sides();
            int rings = rings(sides);
            List<Vec3[]> quads = new ArrayList<>(sides * rings);
            for (int ring = 0; ring < rings; ring++)
            {
                double phi0 = Math.PI * ring / rings - Math.PI / 2;
                double phi1 = Math.PI * (ring + 1) / rings - Math.PI / 2;
                for (int side = 0; side < sides; side++)
                {
                    double theta0 = 2 * Math.PI * side / sides;
                    double theta1 = 2 * Math.PI * (side + 1) / sides;
                    quads.add(new Vec3[]{
                            onSphere(phi0, theta0), onSphere(phi0, theta1),
                            onSphere(phi1, theta1), onSphere(phi1, theta0)});
                }
            }
            return quads;
        }

        private Vec3 onSphere(double phi, double theta)
        {
            double horizontal = Math.cos(phi) * radius;
            return center.add(horizontal * Math.cos(theta), Math.sin(phi) * radius, horizontal * Math.sin(theta));
        }

        /** @param plane 0 = xz (horizontal), 1 = xy, 2 = yz */
        private static void addCircle(List<Segment> into, Vec3 center, double radius, int plane, int sides)
        {
            Vec3 previous = null;
            for (int i = 0; i <= sides; i++)
            {
                double angle = 2 * Math.PI * i / sides;
                double a = Math.cos(angle) * radius;
                double b = Math.sin(angle) * radius;
                Vec3 point = switch (plane)
                {
                    case 0 -> center.add(a, 0, b);
                    case 1 -> center.add(a, b, 0);
                    default -> center.add(0, a, b);
                };
                if (previous != null)
                {
                    into.add(new Segment(previous, point));
                }
                previous = point;
            }
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "center", center);
            tag.putDouble("radius", radius);
            tag.putInt("level", level);
        }
    }

    /**
     * A cylinder around an axis: two circles and four uprights.
     *
     * <p>With zero height it is a disc, a filled circle on a plane, which is the way to draw a
     * radius on the ground without inventing a sixth shape.
     */
    public static final class Cylinder extends Shape
    {
        private final Vec3 center;
        private final double radius;
        private final double height;
        private final String axis;
        private final int level;

        public Cylinder(Vec3 center, double radius, double height, String axis, int level, Common common)
        {
            super("cylinder", common);
            this.center = center;
            this.radius = radius;
            this.height = height;
            this.axis = axis;
            this.level = level;
        }

        private int sides()
        {
            return subdivisionsFor(level, radius);
        }

        private Vec3 up()
        {
            return switch (axis)
            {
                case "x" -> new Vec3(height, 0, 0);
                case "z" -> new Vec3(0, 0, height);
                default -> new Vec3(0, height, 0);
            };
        }

        /**
         * The two circles and the uprights, one per side.
         *
         * <p>One upright per side rather than four fixed ones: they are the edges of the same
         * faces, so {@code level} densifies the wireframe the way it densifies the side.
         */
        @Override
        public List<Segment> segments()
        {
            return uprights(sides());
        }

        /** The outline for particles: the circles, plus four uprights that show how tall it is. */
        @Override
        public List<Segment> particleOutline()
        {
            return uprights(4);
        }

        /** @param uprights how many uprights between the two circles */
        private List<Segment> uprights(int uprights)
        {
            int sides = sides();
            List<Segment> edges = new ArrayList<>(sides * 2 + uprights);
            Vec3 up = up();
            addRing(edges, center, radius, sides);
            if (height != 0)
            {
                addRing(edges, center.add(up), radius, sides);
                for (int i = 0; i < uprights; i++)
                {
                    Vec3 onRing = pointOnRing(center, radius, 2 * Math.PI * i / uprights);
                    edges.add(new Segment(onRing, onRing.add(up)));
                }
            }
            return edges;
        }

        @Override
        public List<Vec3[]> faces()
        {
            int sides = sides();
            Vec3 up = up();
            List<Vec3[]> polygons = new ArrayList<>(sides + 2);
            Vec3[] bottom = new Vec3[sides];
            Vec3[] top = new Vec3[sides];
            for (int i = 0; i < sides; i++)
            {
                bottom[i] = pointOnRing(center, radius, 2 * Math.PI * i / sides);
                top[i] = bottom[i].add(up);
            }
            polygons.add(bottom);
            if (height == 0)
            {
                return polygons; // a disc: one single face, and no side
            }
            polygons.add(top);
            for (int i = 0; i < sides; i++)
            {
                int next = (i + 1) % sides;
                polygons.add(new Vec3[]{bottom[i], bottom[next], top[next], top[i]});
            }
            return polygons;
        }

        private Vec3 pointOnRing(Vec3 middle, double r, double angle)
        {
            double a = Math.cos(angle) * r;
            double b = Math.sin(angle) * r;
            return switch (axis)
            {
                case "x" -> middle.add(0, a, b);
                case "z" -> middle.add(a, b, 0);
                default -> middle.add(a, 0, b);
            };
        }

        private void addRing(List<Segment> into, Vec3 middle, double r, int sides)
        {
            Vec3 previous = null;
            for (int i = 0; i <= sides; i++)
            {
                Vec3 point = pointOnRing(middle, r, 2 * Math.PI * i / sides);
                if (previous != null)
                {
                    into.add(new Segment(previous, point));
                }
                previous = point;
            }
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "center", center);
            tag.putDouble("radius", radius);
            tag.putDouble("height", height);
            tag.putString("axis", axis);
            tag.putInt("level", level);
        }
    }

    /**
     * Text floating at a point.
     *
     * <p>The only shape particles cannot approximate: text is not made of segments. Whoever lacks
     * the client sees a particle at the point, so they know something is there, and the text
     * reaches them in chat the first time, once.
     */
    public static final class Label extends Shape
    {
        /** the text is measured in font units, not blocks */
        public static final float DEFAULT_SIZE = 10.0F;

        /**
         * How the text sits in space.
         *
         * @param size        the font size
         * @param align       {@code 0} centered, {@code -1} left, {@code 1} right
         * @param indent      horizontal offset, in font units
         * @param height      vertical offset, in font units
         * @param raise       offset towards the viewer, in font units
         * @param tilt        rotation around the view axis, in degrees
         * @param lean        rotation around the horizontal axis, in degrees
         * @param turn        rotation around the vertical axis, in degrees
         * @param facing      what it faces: {@code player}, {@code camera} or a fixed direction
         * @param doublesided whether it can be read from behind too
         */
        public record Text(float size, int align, float indent, float height, float raise,
                float tilt, float lean, float turn, String facing, boolean doublesided)
        {
            public static Text plain()
            {
                return new Text(DEFAULT_SIZE, 0, 0, 0, 0, 0, 0, 0, "player", false);
            }
        }

        private final Vec3 pos;
        private final String text;
        private final net.minecraft.nbt.@org.jspecify.annotations.Nullable Tag component;
        private final Text style;

        public Label(Vec3 pos, String text, net.minecraft.nbt.@org.jspecify.annotations.Nullable Tag component,
                Text style, Common common)
        {
            super("label", common);
            this.pos = pos;
            this.text = text;
            this.component = component;
            this.style = style;
        }

        public Vec3 pos()
        {
            return pos;
        }

        /** the unstyled text: what reaches chat for whoever lacks the client */
        public String text()
        {
            return text;
        }

        /**
         * The text with its style, as {@code core.mc.text} serializes it, or {@code null} when
         * whoever drew the label passed a plain string.
         *
         * <p>It travels serialized rather than decoded because decoding needs the registries, which
         * are not here: the client does it, at the moment it has to draw it.
         */
        public net.minecraft.nbt.@org.jspecify.annotations.Nullable Tag component()
        {
            return component;
        }

        public Text style()
        {
            return style;
        }

        @Override
        public List<Segment> segments()
        {
            return List.of();
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "pos", pos);
            tag.putString("text", text);
            if (component != null)
            {
                tag.put("component", component);
            }
            tag.putFloat("size", style.size());
            tag.putInt("align", style.align());
            tag.putFloat("indent", style.indent());
            tag.putFloat("height", style.height());
            tag.putFloat("raise", style.raise());
            tag.putFloat("tilt", style.tilt());
            tag.putFloat("lean", style.lean());
            tag.putFloat("turn", style.turn());
            tag.putString("facing", style.facing());
            tag.putBoolean("doublesided", style.doublesided());
        }
    }

    /**
     * A block or an item drawn in mid air, without existing in the world.
     *
     * <p>Like the label, it is not made of segments: what shows is the real game model, and only
     * the client can draw it. Whoever lacks it sees a {@code block_marker} particle at the point,
     * which at least says something is there and which block it is.
     *
     * <p>What travels over the network is the block <em>as it is written</em>
     * ({@code minecraft:chest[facing=north]}) and not a numeric identifier: registry ids change
     * between worlds, the name does not. For items the whole stack travels as NBT, the only way to
     * carry the data components along.
     */
    public static final class Sprite extends Shape
    {
        /**
         * How the model sits in space.
         *
         * @param facing     what it faces: {@code player}, {@code camera} or a fixed direction
         * @param tilt       rotation around the view axis, in degrees
         * @param lean       rotation around the horizontal axis, in degrees
         * @param turn       rotation around the vertical axis, in degrees
         * @param scaleX     how big it is on the x axis, in blocks
         * @param scaleY     how big it is on the y axis, in blocks
         * @param scaleZ     how big it is on the z axis, in blocks
         * @param blockLight the block light to light it with, {@code -1} for the one of the place
         * @param skyLight   the sky light to light it with, {@code -1} for the one of the place
         * @param variant    for items, the pose they are drawn in ({@code gui}, {@code ground}, …)
         */
        public record Pose(String facing, float tilt, float lean, float turn,
                float scaleX, float scaleY, float scaleZ,
                int blockLight, int skyLight, String variant)
        {
            public static Pose plain()
            {
                return new Pose("north", 0, 0, 0, 1, 1, 1, -1, -1, "none");
            }
        }

        private final Vec3 pos;
        private final String name;
        private final @org.jspecify.annotations.Nullable CompoundTag data;
        private final Pose pose;

        /**
         * @param type {@code "block"} or {@code "item"}
         * @param name the block with its states, or the item id: the client needs it to rebuild the
         *             model, the server to pick the particle
         * @param data the block entity data, or the whole item stack; {@code null} if not needed
         */
        public Sprite(String type, Vec3 pos, String name, @org.jspecify.annotations.Nullable CompoundTag data,
                Pose pose, Common common)
        {
            super(type, common);
            this.pos = pos;
            this.name = name;
            this.data = data;
            this.pose = pose;
        }

        public boolean isItem()
        {
            return "item".equals(type());
        }

        public Vec3 pos()
        {
            return pos;
        }

        public String name()
        {
            return name;
        }

        public @org.jspecify.annotations.Nullable CompoundTag data()
        {
            return data;
        }

        public Pose pose()
        {
            return pose;
        }

        @Override
        public List<Segment> segments()
        {
            return List.of();
        }

        @Override
        protected void writeGeometry(CompoundTag tag)
        {
            putVec(tag, "pos", pos);
            tag.putString("name", name);
            if (data != null)
            {
                tag.put("data", data);
            }
            tag.putString("facing", pose.facing());
            tag.putFloat("tilt", pose.tilt());
            tag.putFloat("lean", pose.lean());
            tag.putFloat("turn", pose.turn());
            tag.putFloat("scalex", pose.scaleX());
            tag.putFloat("scaley", pose.scaleY());
            tag.putFloat("scalez", pose.scaleZ());
            tag.putInt("blocklight", pose.blockLight());
            tag.putInt("skylight", pose.skyLight());
            tag.putString("variant", pose.variant());
        }
    }
}
