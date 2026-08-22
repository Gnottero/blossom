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
        // 'dx' e' 'x' arrotondato al blocco: l'arrotondamento implica il seguire, non lo sostituisce
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

    /** @return se il riempimento è visibile: alfa zero vuol dire che non c'è */
    public boolean hasFill()
    {
        return (common.fillColor() & 0xFF) != 0;
    }

    /**
     * @return se il riempimento è opaco, cioè copre quello che ha dietro invece di lasciarlo
     *         trasparire
     */
    public boolean hasOpaqueFill()
    {
        return (common.fillColor() & 0xFF) == 0xFF;
    }

    /**
     * @return se il reticolo si vede: alfa zero nel colore vuol dire "solo il riempimento", ed è il
     *         modo di chiedere una forma piena senza spigoli
     */
    public boolean hasLines()
    {
        return (common.color() & 0xFF) != 0;
    }

    public long expiresAtTick()
    {
        return common.expiresAtTick();
    }

    /** l'id dell'entità a cui la forma è ancorata, {@code -1} se la forma sta ferma nel mondo */
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
     * Il punto assoluto di un punto della forma.
     *
     * @param followPos la posizione dell'entità seguita, o {@code null} se non c'è (o è sparita:
     *                  in quel caso la forma resta dov'è invece di collassare sull'origine)
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
     * I segmenti di cui la forma è fatta, in coordinate non ancorate.
     *
     * <p>Una sfera e un cilindro non sono fatti di segmenti: quello che restituiscono sono i loro
     * cerchi spezzati in corde. È il compromesso di disegnare per linee, ed è meglio dichiararlo qui
     * che fingere altrove.
     */
    public abstract List<Segment> segments();

    /**
     * I segmenti da approssimare con le particelle, in coordinate non ancorate.
     *
     * <p>Di solito sono gli stessi di {@link #segments()}, ma non per le forme tonde: il reticolo di
     * una sfera con un {@code level} alto sono migliaia di segmenti, e una particella ogni mezzo
     * blocco lungo ognuno vorrebbe dire seppellire il server di pacchetti per disegnare una cosa che
     * a quella risoluzione le particelle non sanno comunque rendere. Chi non ha il client vede la
     * sagoma - i cerchi principali - e chi ce l'ha vede il reticolo intero.
     */
    public List<Segment> particleOutline()
    {
        return segments();
    }

    /**
     * Le facce di cui la forma è fatta, per il riempimento. Vuoto se la forma non ha un dentro.
     *
     * <p>Ogni faccia è un poligono convesso, con i vertici in ordine: quattro punti sono un quad,
     * di più è un ventaglio di triangoli. Non c'è nessuna faccia se {@code fill} è trasparente, ma
     * quel controllo lo fa chi disegna: qui si dichiara solo la geometria.
     */
    public List<Vec3[]> faces()
    {
        return List.of();
    }

    /** Un segmento fra due punti: l'unica primitiva che le particelle sanno disegnare. */
    public record Segment(Vec3 from, Vec3 to)
    {
    }

    /**
     * La forma come NBT, per il client.
     *
     * <p>Sulla rete viaggia la <em>durata rimasta</em> e non l'istante di scadenza: server e client
     * contano i tick da momenti diversi, e mandare un numero assoluto vorrebbe dire chiedere ai due
     * orologi di essere d'accordo. La durata invece si conta da quando arriva, e sbaglia al massimo
     * del tempo di volo del pacchetto.
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
     * Ricostruisce una forma dal suo NBT.
     *
     * <p>È il client a chiamarla, e riusa esattamente le classi che il server ha usato per costruire
     * la forma: le due parti non possono divergere su cosa sia una sfera, perché è la stessa classe.
     * È anche il motivo per cui il disegno lato client è corto — la geometria è già qui, in
     * {@link #segments()} e {@link #faces()}.
     *
     * @param nowTick il tick del client in cui la forma arriva: da lì si conta la durata
     * @return la forma, o {@code null} se il tipo non è conosciuto (un server più nuovo del client)
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

    // --- le forme ---

    /** Un segmento fra due punti. */
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

    /** Il parallelepipedo fra due angoli: dodici spigoli, e sei facce se c'è un riempimento. */
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
     * Quanti lati per cerchio, quando chi scrive non lo dice.
     *
     * <p>Cresce con la radice del raggio: una sfera grande vista da vicino ha bisogno di più lati
     * per non sembrare un poligono, una piccola no e non vale la pena pagarli. Il numero si può
     * sempre imporre con {@code level}.
     */
    static int subdivisionsFor(int level, double radius)
    {
        return level > 0 ? level : Math.max(10, (int) (10 * Math.sqrt(radius)));
    }

    /** Una sfera attorno a un centro: un reticolo di meridiani e paralleli, e la stessa buccia piena. */
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

        /** quanti anelli da polo a polo: è mezzo giro invece di uno intero, quindi metà dei lati */
        private static int rings(int sides)
        {
            return Math.max(3, sides / 2);
        }

        /**
         * Il reticolo: un meridiano per lato, e i paralleli fra un anello e l'altro.
         *
         * <p>È la stessa suddivisione delle facce, e non un caso: {@code level} decide quanto è fitta
         * la sfera, e reticolo e riempimento devono essere la stessa sfera. Tre soli cerchi -
         * com'era prima - facevano sembrare che {@code level} riguardasse solo il riempimento.
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
            // i poli sono punti, non cerchi: i paralleli cominciano dal primo anello dopo
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

        /** La sagoma per le particelle: i tre cerchi massimi, uno per piano. */
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
            // la buccia a maglie: i paralleli tagliati dai meridiani. Con pochi lati si vede il
            // poliedro, ed e' giusto cosi' - e' la stessa suddivisione delle linee
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

        /** @param plane 0 = xz (orizzontale), 1 = xy, 2 = yz */
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
     * Un cilindro attorno a un asse: due cerchi e quattro montanti.
     *
     * <p>Con altezza zero è un disco — un cerchio pieno su un piano - ed è il modo di disegnare un
     * raggio a terra senza inventare una sesta forma.
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
         * I due cerchi e i montanti, uno per lato.
         *
         * <p>Un montante per lato e non quattro fissi: sono gli spigoli delle stesse facce, quindi
         * {@code level} infittisce il reticolo come infittisce il fianco.
         */
        @Override
        public List<Segment> segments()
        {
            return uprights(sides());
        }

        /** La sagoma per le particelle: i cerchi, e quattro montanti che bastano a dire dov'è alto. */
        @Override
        public List<Segment> particleOutline()
        {
            return uprights(4);
        }

        /** @param uprights quanti montanti fra i due cerchi */
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
                return polygons; // un disco: una faccia sola, e nessun fianco
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
     * Del testo che galleggia in un punto.
     *
     * <p>L'unica forma che le particelle non sanno approssimare: un testo non è fatto di segmenti.
     * Chi non ha il client vede una particella nel punto - sa che lì c'è qualcosa - e il testo gli
     * arriva in chat la prima volta, una volta sola.
     */
    public static final class Label extends Shape
    {
        /** il testo è pensato in unità di font, non di blocchi */
        public static final float DEFAULT_SIZE = 10.0F;

        /**
         * Come sta il testo nello spazio.
         *
         * @param size        la dimensione del carattere
         * @param align       {@code 0} centrato, {@code -1} a sinistra, {@code 1} a destra
         * @param indent      spostamento orizzontale, in unità di font
         * @param height      spostamento verticale, in unità di font
         * @param raise       spostamento verso chi guarda, in unità di font
         * @param tilt        rotazione attorno all'asse di vista, in gradi
         * @param lean        rotazione attorno all'orizzontale, in gradi
         * @param turn        rotazione attorno alla verticale, in gradi
         * @param facing      verso cosa guarda: {@code player}, {@code camera} o una direzione fissa
         * @param doublesided se si legge anche da dietro
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

        /** il testo senza stile: è quello che arriva in chat a chi non ha il client */
        public String text()
        {
            return text;
        }

        /**
         * Il testo con il suo stile, come lo serializza {@code core.mc.text}, o {@code null} se chi
         * ha disegnato l'etichetta ha passato una stringa e basta.
         *
         * <p>Viaggia serializzato e non già decodificato perché decodificarlo vuole i registri, che
         * qui non ci sono: è il client a farlo, nel momento in cui deve scriverlo.
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
     * Un blocco o un item disegnato a mezz'aria, senza esistere nel mondo.
     *
     * <p>Come l'etichetta, non è fatta di segmenti: quello che si vede è il modello vero del gioco,
     * e disegnarlo lo sa fare solo il client. Chi non ce l'ha vede una particella {@code block_marker}
     * nel punto, che almeno dice che lì c'è qualcosa e di che blocco si tratta.
     *
     * <p>Quello che viaggia sulla rete è il blocco <em>come si scrive</em>
     * ({@code minecraft:chest[facing=north]}) e non un identificatore numerico: gli id di registro
     * cambiano fra un mondo e l'altro, il nome no. Per gli item viaggia l'intero stack in NBT, che è
     * l'unico modo di portarsi dietro i data component.
     */
    public static final class Sprite extends Shape
    {
        /**
         * Come sta il modello nello spazio.
         *
         * @param facing     verso cosa guarda: {@code player}, {@code camera} o una direzione fissa
         * @param tilt       rotazione attorno all'asse di vista, in gradi
         * @param lean       rotazione attorno all'orizzontale, in gradi
         * @param turn       rotazione attorno alla verticale, in gradi
         * @param scaleX     quanto è grande sull'asse x, in blocchi
         * @param scaleY     quanto è grande sull'asse y, in blocchi
         * @param scaleZ     quanto è grande sull'asse z, in blocchi
         * @param blockLight la luce da blocco con cui illuminarlo, {@code -1} per quella del posto
         * @param skyLight   la luce dal cielo con cui illuminarlo, {@code -1} per quella del posto
         * @param variant    per gli item, in che posa si disegnano ({@code gui}, {@code ground}, …)
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
         * @param type {@code "block"} o {@code "item"}
         * @param name il blocco con i suoi stati, o l'id dell'item: serve al client per ricostruire
         *             il modello, e al server per scegliere la particella
         * @param data i dati della block entity, o l'intero item stack; {@code null} se non servono
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
