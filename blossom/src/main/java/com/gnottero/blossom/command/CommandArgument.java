package com.gnottero.blossom.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.datafixers.util.Either;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtTagArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.commands.arguments.TeamColorArgument;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.TeamColor;

import blossom.exception.SourcePosition;
import blossom.exception.TypeMismatchError;
import blossom.value.BooleanValue;
import blossom.value.FunctionValue;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;
import com.gnottero.blossom.value.BlockValue;
import com.gnottero.blossom.value.EntityValue;
import com.gnottero.blossom.value.FormattedTextValue;
import com.gnottero.blossom.value.NBTSerializableValue;
import com.gnottero.blossom.value.ValueConversions;

/**
 * The types an argument of an app command can have, and what each one becomes in the script.
 *
 * <p>Ported from Scarpet's {@code CommandArgument}, with the same catalogue and the same values, so
 * an app that works there finds what it expects here. Three things change:
 *
 * <ul>
 *     <li>a modifier that the type does not have ({@code 'mni'} for {@code 'min'}) is an error with
 *     the right name suggested, not a key quietly ignored;</li>
 *     <li>a type built on another ({@code {'type' -> 'effect'}}) keeps the suggestions of its base:
 *     Scarpet lost them, and a custom effect type suggested nothing;</li>
 *     <li>a custom type built on {@code 'scoreholder'} is a score holder: Scarpet made it a player
 *     list, and {@code 'biome'} reads the argument it was given instead of one called "biome".</li>
 * </ul>
 *
 * <p>The type of {@code <name>} is found from the name, the way Scarpet does it: the custom types of
 * the app first, then the built-in ones, trying the whole name and then every suffix after an
 * underscore. {@code <from_pos>} is a {@code pos}, {@code <target_biome>} a {@code biome}, and a name
 * that matches nothing is a {@code string}.
 */
public abstract class CommandArgument
{
    /** The modifiers every type accepts. */
    private static final Set<String> SHARED_MODIFIERS = Set.of("type", "suggest", "suggester", "case_sensitive");

    public static CommandSyntaxException error(String text)
    {
        return new SimpleCommandExceptionType(Component.literal(text)).create();
    }

    private static final List<CommandArgument> BASE_TYPES = List.of(
            new StringArgument(),
            new VanillaArgument("bool", c -> BoolArgumentType.bool(),
                    (c, p) -> BooleanValue.of(BoolArgumentType.getBool(c, p)), true),
            new FloatArgument(),
            new IntArgument(),
            new WordArgument(),
            new GreedyStringArgument(),
            new VanillaArgument("yaw", c -> AngleArgument.angle(),
                    (c, p) -> new NumericValue(AngleArgument.getAngle(c, p)), true),
            new BlockPosArgument(),
            new VanillaArgument("block", BlockStateArgument::block,
                    (c, p) -> {
                        BlockInput result = BlockStateArgument.getBlock(c, p);
                        return new BlockValue(result.getState(), c.getSource().getLevel(), result.tag);
                    },
                    param -> (ctx, builder) -> ctx.getArgument(param, BlockStateArgument.class).listSuggestions(ctx, builder)),
            new VanillaArgument("blockpredicate", BlockPredicateArgument::blockPredicate,
                    (c, p) -> ValueConversions.ofBlockPredicate(c.getSource().getServer().registryAccess(),
                            BlockPredicateArgument.getBlockPredicate(c, p)),
                    param -> (ctx, builder) -> ctx.getArgument(param, BlockPredicateArgument.class).listSuggestions(ctx, builder)),
            new VanillaArgument("teamcolor", c -> TeamColorArgument.teamColor(),
                    (c, p) -> {
                        TeamColor color = TeamColorArgument.getTeamColor(c, p);
                        return ListValue.of(StringValue.of(color.getSerializedName()), ValueConversions.ofRGB(color.rgb()));
                    }, false),
            new VanillaArgument("columnpos", c -> ColumnPosArgument.columnPos(),
                    (c, p) -> ValueConversions.of(ColumnPosArgument.getColumnPos(c, p)), false),
            new VanillaArgument("dimension", c -> DimensionArgument.dimension(),
                    (c, p) -> ValueConversions.of(DimensionArgument.getDimension(c, p)), false),
            new EntityArgument(),
            new VanillaArgument("anchor", c -> EntityAnchorArgument.anchor(),
                    (c, p) -> StringValue.of(EntityAnchorArgument.getAnchor(c, p).name().toLowerCase(Locale.ROOT)), false),
            new VanillaArgument("entitytype", c -> ResourceArgument.resource(c, Registries.ENTITY_TYPE),
                    (c, p) -> ValueConversions.of(ResourceArgument.getSummonableEntityType(c, p).key()),
                    param -> SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES)),
            new VanillaArgument("floatrange", c -> RangeArgument.floatRange(),
                    (c, p) -> ValueConversions.of(RangeArgument.Floats.getRange(c, p)), true),
            new PlayerProfileArgument(),
            new VanillaArgument("intrange", c -> RangeArgument.intRange(),
                    (c, p) -> ValueConversions.of(RangeArgument.Ints.getRange(c, p)), true),
            VanillaArgument.ofRegistry("enchantment", Registries.ENCHANTMENT),
            new SlotArgument(),
            new VanillaArgument("item", ItemArgument::item,
                    (c, p) -> ValueConversions.of(ItemArgument.getItem(c, p).createItemStack(1), c.getSource().registryAccess()),
                    param -> (ctx, builder) -> ctx.getArgument(param, ItemArgument.class).listSuggestions(ctx, builder)),
            new VanillaArgument("message", c -> MessageArgument.message(),
                    (c, p) -> new FormattedTextValue(MessageArgument.getMessage(c, p)), true),
            VanillaArgument.ofRegistry("effect", Registries.MOB_EFFECT),
            new TagArgument(),
            new VanillaArgument("path", c -> NbtPathArgument.nbtPath(),
                    (c, p) -> StringValue.of(NbtPathArgument.getPath(c, p).toString()), true),
            new VanillaArgument("objective", c -> ObjectiveArgument.objective(),
                    (c, p) -> ValueConversions.of(ObjectiveArgument.getObjective(c, p)), false),
            new VanillaArgument("criterion", c -> ObjectiveCriteriaArgument.criteria(),
                    (c, p) -> StringValue.of(ObjectiveCriteriaArgument.getCriteria(c, p).getName()), false),
            new VanillaArgument("particle", ParticleArgument::particle,
                    (c, p) -> ValueConversions.of(ParticleArgument.getParticle(c, p), c.getSource().registryAccess()),
                    param -> (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                            ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.PARTICLE_TYPE).keySet(), builder)),
            VanillaArgument.ofRegistry("recipe", Registries.RECIPE),
            VanillaArgument.ofRegistry("advancement", Registries.ADVANCEMENT),
            VanillaArgument.ofRegistry("lootcondition", Registries.LOOT_CONDITION_TYPE),
            VanillaArgument.ofRegistry("loottable", Registries.LOOT_TABLE),
            VanillaArgument.ofRegistry("attribute", Registries.ATTRIBUTE),
            new VanillaArgument("boss", c -> IdentifierArgument.id(),
                    (c, p) -> ValueConversions.of(IdentifierArgument.getId(c, p)), param -> BossBarCommands.SUGGEST_BOSS_BAR),
            new VanillaArgument("biome", c -> ResourceOrTagArgument.resourceOrTag(c, Registries.BIOME),
                    (c, p) -> {
                        Either<Holder.Reference<Biome>, HolderSet.Named<Biome>> result =
                                ResourceOrTagArgument.getResourceOrTag(c, p, Registries.BIOME).unwrap();
                        return result.map(biome -> ValueConversions.of(biome.key()), tag -> ValueConversions.of(tag.key()));
                    },
                    param -> (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                            ctx.getSource().getServer().registryAccess().lookupOrThrow(Registries.BIOME).keySet(), builder)),
            new VanillaArgument("sound", c -> IdentifierArgument.id(),
                    (c, p) -> ValueConversions.of(IdentifierArgument.getId(c, p)),
                    param -> SuggestionProviders.cast(SuggestionProviders.AVAILABLE_SOUNDS)),
            new VanillaArgument("storekey", c -> IdentifierArgument.id(),
                    (c, p) -> ValueConversions.of(IdentifierArgument.getId(c, p)),
                    param -> (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                            ctx.getSource().getServer().getCommandStorage().keys(), builder)),
            new IdentifierArgumentType(),
            new VanillaArgument("rotation", c -> RotationArgument.rotation(),
                    (c, p) -> {
                        Vec2 rotation = RotationArgument.getRotation(c, p).getRotation(c.getSource());
                        return ListValue.of(new NumericValue(rotation.x), new NumericValue(rotation.y));
                    }, true),
            new ScoreholderArgument(),
            new VanillaArgument("scoreboardslot", c -> ScoreboardSlotArgument.displaySlot(),
                    (c, p) -> StringValue.of(ScoreboardSlotArgument.getDisplaySlot(c, p).getSerializedName()), false),
            new VanillaArgument("swizzle", c -> SwizzleArgument.swizzle(),
                    (c, p) -> StringValue.of(SwizzleArgument.getSwizzle(c, p).stream()
                            .map(Direction.Axis::getSerializedName).sorted().collect(Collectors.joining())), true),
            new VanillaArgument("team", c -> TeamArgument.team(),
                    (c, p) -> StringValue.of(TeamArgument.getTeam(c, p).getName()), false),
            new VanillaArgument("time", c -> TimeArgument.time(),
                    (c, p) -> new NumericValue(IntegerArgumentType.getInteger(c, p)), false),
            new VanillaArgument("uuid", c -> UuidArgument.uuid(),
                    (c, p) -> StringValue.of(UuidArgument.getUuid(c, p).toString()), false),
            new VanillaArgument("surfacelocation", c -> Vec2Argument.vec2(),
                    (c, p) -> {
                        Vec2 location = Vec2Argument.getVec2(c, p);
                        return ListValue.of(NumericValue.of(location.x), NumericValue.of(location.y));
                    }, false),
            new LocationArgument());

    /** The built-in types, by name. */
    public static final Map<String, CommandArgument> BUILT_INS =
            BASE_TYPES.stream().collect(Collectors.toMap(CommandArgument::name, a -> a, (a, b) -> a, LinkedHashMap::new));

    /** What a name that matches no type is. */
    public static final CommandArgument DEFAULT = BUILT_INS.get("string");

    /**
     * The type of {@code <argument>}: the app's own types first, then the built-in ones, trying the
     * whole name and then every suffix after an underscore, longest first.
     */
    public static CommandArgument typeFor(String argument, Map<String, CommandArgument> custom)
    {
        String[] components = argument.split("_");
        for (int i = 0; i < components.length; i++)
        {
            String candidate = String.join("_", Arrays.asList(components).subList(i, components.length));
            CommandArgument type = custom.get(candidate);
            if (type == null)
            {
                type = BUILT_INS.get(candidate);
            }
            if (type != null)
            {
                return type;
            }
        }
        return DEFAULT;
    }

    /**
     * A custom type: a base type with its modifiers.
     *
     * @throws TypeMismatchError if the base does not exist or a modifier is not one of the type: both
     *         are spelling mistakes, and they are said here with the right name, not discovered
     *         later as an argument that behaves like a plain string
     */
    public static CommandArgument custom(String name, Map<String, Value> spec, AppCommands app)
    {
        Value declared = spec.get("type");
        if (declared == null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "The argument type '" + name + "' says what it is built on with 'type', e.g. {'type' -> 'int', 'min' -> 1}.");
        }
        String baseName = declared.getString().toLowerCase(Locale.ROOT);
        CommandArgument base = BUILT_INS.get(baseName);
        if (base == null)
        {
            throw new TypeMismatchError(SourcePosition.UNKNOWN,
                    "There is no argument type '" + baseName + "' to build '" + name + "' on.",
                    blossom.exception.Suggestions.closestTo(baseName, List.copyOf(BUILT_INS.keySet())));
        }
        Set<String> allowed = new HashSet<>(SHARED_MODIFIERS);
        allowed.addAll(base.modifiers());
        for (String key : spec.keySet())
        {
            if (!allowed.contains(key))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "An argument of type '" + baseName + "' has no '" + key + "'. It takes " + allowed.stream().sorted().toList() + ".",
                        blossom.exception.Suggestions.closestTo(key, List.copyOf(allowed)));
            }
        }
        CommandArgument variant = base.copy();
        variant.name = name;
        variant.configure(spec, app);
        return variant;
    }

    protected String name;
    protected Collection<String> examples;
    /** whether the suggestions come from {@link #examples} (or the custom ones) instead of the game */
    protected boolean needsMatching;
    protected boolean caseSensitive = true;
    /** the game's own suggestions for this argument, when it has them */
    protected @Nullable Function<String, SuggestionProvider<CommandSourceStack>> suggestionProvider;
    /** a function of the app, {@code fn(arguments) -> [options]}, called while typing */
    protected @Nullable FunctionValue suggester;

    protected CommandArgument(String name, Collection<String> examples, boolean needsMatching)
    {
        this.name = name;
        this.examples = examples;
        this.needsMatching = needsMatching;
    }

    public String name()
    {
        return name;
    }

    /** the modifiers of this type, beyond the shared ones */
    protected Set<String> modifiers()
    {
        return Set.of();
    }

    /** A fresh instance of the same base type, to configure. */
    protected abstract CommandArgument copy();

    protected abstract ArgumentType<?> argumentType(CommandBuildContext context);

    protected abstract Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException;

    protected void configure(Map<String, Value> spec, AppCommands app)
    {
        caseSensitive = spec.getOrDefault("case_sensitive", Value.TRUE).getBoolean();
        Value suggesterValue = spec.get("suggester");
        if (suggesterValue != null)
        {
            if (!(suggesterValue instanceof final FunctionValue function))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'suggester' of '" + name + "' is a function taking the arguments typed so far: 'suggester' -> fn(args) -> [...].");
            }
            if (function.getArguments().size() != 1 && function.getVarArgs() == null)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'suggester' of '" + name + "' takes one argument, the map of what was typed so far.");
            }
            suggester = function;
            suggestionProvider = null; // the app's suggestions replace the game's
        }
        Value suggest = spec.get("suggest");
        if (suggest != null)
        {
            if (suggester != null)
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN,
                        "'" + name + "' has both 'suggest' and 'suggester': a fixed list, or a function, not both.");
            }
            if (!(suggest instanceof final ListValue list))
            {
                throw new TypeMismatchError(SourcePosition.UNKNOWN, "'suggest' of '" + name + "' is a list.");
            }
            examples = list.getItems().stream().map(Value::getString).toList();
            if (!examples.isEmpty())
            {
                needsMatching = true;
                suggestionProvider = null;
            }
        }
    }

    /** The brigadier node of {@code <param>}, with the right suggestions. */
    public static RequiredArgumentBuilder<CommandSourceStack, ?> node(String param, AppCommands app, CommandBuildContext context)
    {
        CommandArgument type = typeFor(param, app.types());
        RequiredArgumentBuilder<CommandSourceStack, ?> node = Commands.argument(param, type.argumentType(context));
        if (type.suggestionProvider != null)
        {
            return node.suggests(type.suggestionProvider.apply(param));
        }
        if (type.suggester == null && !type.needsMatching)
        {
            return node; // the argument type suggests by itself, as in vanilla
        }
        return node.suggests((ctx, builder) -> type.suggest(ctx, builder, app));
    }

    /** What {@code <param>} became, once typed. */
    public static Value value(CommandContext<CommandSourceStack> context, String param, AppCommands app) throws CommandSyntaxException
    {
        return typeFor(param, app.types()).valueOf(context, param);
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder,
            AppCommands app) throws CommandSyntaxException
    {
        String prefix = caseSensitive ? builder.getRemaining() : builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : options(context, app))
        {
            if (matchesPrefix(prefix, option))
            {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    protected Collection<String> options(CommandContext<CommandSourceStack> context, AppCommands app) throws CommandSyntaxException
    {
        if (suggester != null)
        {
            Map<Value, Value> typed = new LinkedHashMap<>();
            for (ParsedCommandNode<CommandSourceStack> parsed : context.getNodes())
            {
                if (parsed.getNode() instanceof ArgumentCommandNode<?, ?> argument)
                {
                    typed.put(StringValue.of(argument.getName()), value(context, argument.getName(), app));
                }
            }
            Value response = app.call(context.getSource(), suggester, List.of(MapValue.wrap(typed)));
            if (!(response instanceof final ListValue list))
            {
                throw error("The suggester of '" + name + "' has to return a list of options");
            }
            return list.getItems().stream().map(Value::getString).toList();
        }
        return needsMatching ? examples : List.of("... " + name);
    }

    /** {@code prefix} at the start of the option, or of one of its words after an underscore. */
    private boolean matchesPrefix(String prefix, String option)
    {
        String candidate = caseSensitive ? option : option.toLowerCase(Locale.ROOT);
        for (int i = 0; !candidate.startsWith(prefix, i); ++i)
        {
            i = candidate.indexOf('_', i);
            if (i < 0)
            {
                return false;
            }
        }
        return true;
    }

    // --- the types ---

    private static class StringArgument extends CommandArgument
    {
        private Set<String> options = Set.of();

        StringArgument()
        {
            super("string", StringArgumentType.StringType.QUOTABLE_PHRASE.getExamples(), true);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("options");
        }

        @Override
        protected CommandArgument copy()
        {
            return new StringArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return StringArgumentType.string();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            String chosen = StringArgumentType.getString(context, param);
            if (!caseSensitive)
            {
                chosen = chosen.toLowerCase(Locale.ROOT);
            }
            if (!options.isEmpty() && !options.contains(chosen))
            {
                throw error("'" + chosen + "' is not a valid " + param + ": it can be " + options.stream().sorted().toList());
            }
            return StringValue.of(chosen);
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            Value declared = spec.get("options");
            if (declared != null)
            {
                if (!(declared instanceof final ListValue list))
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN, "'options' of '" + name + "' is a list.");
                }
                options = list.getItems().stream()
                        .map(v -> caseSensitive ? v.getString() : v.getString().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
            }
        }

        @Override
        protected Collection<String> options(CommandContext<CommandSourceStack> context, AppCommands app) throws CommandSyntaxException
        {
            return options.isEmpty() ? super.options(context, app) : options;
        }
    }

    private static final class WordArgument extends StringArgument
    {
        WordArgument()
        {
            name = "term";
            examples = StringArgumentType.StringType.SINGLE_WORD.getExamples();
        }

        @Override
        protected CommandArgument copy()
        {
            return new WordArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return StringArgumentType.word();
        }
    }

    private static final class GreedyStringArgument extends StringArgument
    {
        GreedyStringArgument()
        {
            name = "text";
            examples = StringArgumentType.StringType.GREEDY_PHRASE.getExamples();
        }

        @Override
        protected CommandArgument copy()
        {
            return new GreedyStringArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return StringArgumentType.greedyString();
        }
    }

    private static final class BlockPosArgument extends CommandArgument
    {
        private boolean mustBeLoaded = false;

        BlockPosArgument()
        {
            super("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos().getExamples(), false);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("loaded");
        }

        @Override
        protected CommandArgument copy()
        {
            return new BlockPosArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            BlockPos pos = mustBeLoaded
                    ? net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(context, param)
                    : net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(context, param);
            return ValueConversions.of(pos);
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            mustBeLoaded = spec.getOrDefault("loaded", Value.FALSE).getBoolean();
        }
    }

    private static final class LocationArgument extends CommandArgument
    {
        private boolean blockCentered = true;

        LocationArgument()
        {
            super("location", Vec3Argument.vec3().getExamples(), false);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("block_centered");
        }

        @Override
        protected CommandArgument copy()
        {
            return new LocationArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return Vec3Argument.vec3(blockCentered);
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param)
        {
            return ValueConversions.of(Vec3Argument.getVec3(context, param));
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            blockCentered = spec.getOrDefault("block_centered", Value.TRUE).getBoolean();
        }
    }

    private static final class EntityArgument extends CommandArgument
    {
        private boolean playersOnly = false;
        private boolean single = false;

        EntityArgument()
        {
            super("entities", net.minecraft.commands.arguments.EntityArgument.entities().getExamples(), false);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("players", "single");
        }

        @Override
        protected CommandArgument copy()
        {
            return new EntityArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            if (playersOnly)
            {
                return single ? net.minecraft.commands.arguments.EntityArgument.player() : net.minecraft.commands.arguments.EntityArgument.players();
            }
            return single ? net.minecraft.commands.arguments.EntityArgument.entity() : net.minecraft.commands.arguments.EntityArgument.entities();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            Collection<? extends Entity> found = net.minecraft.commands.arguments.EntityArgument.getOptionalEntities(context, param);
            if (!single)
            {
                return ListValue.wrap(found.stream().map(EntityValue::new));
            }
            if (found.isEmpty())
            {
                return Value.NULL;
            }
            if (found.size() == 1)
            {
                return new EntityValue(found.iterator().next());
            }
            throw error("'" + param + "' selected " + found.size() + " entities, and it takes one");
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            playersOnly = spec.getOrDefault("players", Value.FALSE).getBoolean();
            single = spec.getOrDefault("single", Value.FALSE).getBoolean();
        }
    }

    private static final class PlayerProfileArgument extends CommandArgument
    {
        private boolean single = false;

        PlayerProfileArgument()
        {
            super("players", GameProfileArgument.gameProfile().getExamples(), false);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("single");
        }

        @Override
        protected CommandArgument copy()
        {
            return new PlayerProfileArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return GameProfileArgument.gameProfile();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, param);
            if (!single)
            {
                return ListValue.wrap(profiles.stream().map(p -> StringValue.of(p.name())));
            }
            if (profiles.isEmpty())
            {
                return Value.NULL;
            }
            if (profiles.size() == 1)
            {
                return StringValue.of(profiles.iterator().next().name());
            }
            throw error("'" + param + "' selected " + profiles.size() + " players, and it takes one");
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            single = spec.getOrDefault("single", Value.FALSE).getBoolean();
        }
    }

    private static final class ScoreholderArgument extends CommandArgument
    {
        private boolean single = false;

        ScoreholderArgument()
        {
            super("scoreholder", ScoreHolderArgument.scoreHolder().getExamples(), false);
            suggestionProvider = param -> ScoreHolderArgument.SUGGEST_SCORE_HOLDERS;
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("single");
        }

        @Override
        protected CommandArgument copy()
        {
            return new ScoreholderArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return single ? ScoreHolderArgument.scoreHolder() : ScoreHolderArgument.scoreHolders();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            Collection<ScoreHolder> holders = ScoreHolderArgument.getNames(context, param);
            if (!single)
            {
                return ListValue.wrap(holders.stream().map(ValueConversions::of));
            }
            if (holders.isEmpty())
            {
                return Value.NULL;
            }
            if (holders.size() == 1)
            {
                return ValueConversions.of(holders.iterator().next());
            }
            throw error("'" + param + "' selected " + holders.size() + " score holders, and it takes one");
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            single = spec.getOrDefault("single", Value.FALSE).getBoolean();
        }
    }

    private static final class TagArgument extends CommandArgument
    {
        private boolean mapRequired = true;

        TagArgument()
        {
            super("tag", CompoundTagArgument.compoundTag().getExamples(), false);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("allow_element");
        }

        @Override
        protected CommandArgument copy()
        {
            return new TagArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return mapRequired ? CompoundTagArgument.compoundTag() : NbtTagArgument.nbtTag();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param)
        {
            return mapRequired
                    ? new NBTSerializableValue(CompoundTagArgument.getCompoundTag(context, param))
                    : new NBTSerializableValue(NbtTagArgument.getNbtTag(context, param));
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            mapRequired = !spec.getOrDefault("allow_element", Value.FALSE).getBoolean();
        }
    }

    private static final class IdentifierArgumentType extends CommandArgument
    {
        private Set<Identifier> options = Set.of();

        IdentifierArgumentType()
        {
            super("identifier", List.of(), true);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("options");
        }

        @Override
        protected CommandArgument copy()
        {
            return new IdentifierArgumentType();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return IdentifierArgument.id();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            Identifier chosen = IdentifierArgument.getId(context, param);
            if (!options.isEmpty() && !options.contains(chosen))
            {
                throw error("'" + chosen + "' is not a valid " + param);
            }
            return ValueConversions.of(chosen);
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            Value declared = spec.get("options");
            if (declared != null)
            {
                if (!(declared instanceof final ListValue list))
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN, "'options' of '" + name + "' is a list.");
                }
                options = list.getItems().stream().map(v -> Identifier.parse(v.getString())).collect(Collectors.toSet());
                if (examples.isEmpty())
                {
                    examples = list.getItems().stream().map(Value::getString).toList();
                }
            }
        }
    }

    private static final class FloatArgument extends CommandArgument
    {
        private @Nullable Double min = null;
        private @Nullable Double max = null;

        FloatArgument()
        {
            super("float", DoubleArgumentType.doubleArg().getExamples(), true);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("min", "max");
        }

        @Override
        protected CommandArgument copy()
        {
            return new FloatArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return DoubleArgumentType.doubleArg(min == null ? -Double.MAX_VALUE : min, max == null ? Double.MAX_VALUE : max);
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param)
        {
            return new NumericValue(DoubleArgumentType.getDouble(context, param));
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            if (spec.containsKey("min"))
            {
                min = NumericValue.asNumber(spec.get("min"), "min").getDouble();
            }
            if (spec.containsKey("max"))
            {
                max = NumericValue.asNumber(spec.get("max"), "max").getDouble();
            }
        }
    }

    private static final class IntArgument extends CommandArgument
    {
        private @Nullable Long min = null;
        private @Nullable Long max = null;

        IntArgument()
        {
            super("int", LongArgumentType.longArg().getExamples(), true);
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("min", "max");
        }

        @Override
        protected CommandArgument copy()
        {
            return new IntArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return LongArgumentType.longArg(min == null ? Long.MIN_VALUE : min, max == null ? Long.MAX_VALUE : max);
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param)
        {
            return new NumericValue(LongArgumentType.getLong(context, param));
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            if (spec.containsKey("min"))
            {
                min = NumericValue.asNumber(spec.get("min"), "min").getLong();
            }
            if (spec.containsKey("max"))
            {
                max = NumericValue.asNumber(spec.get("max"), "max").getLong();
            }
        }
    }

    private static final class SlotArgument extends CommandArgument
    {
        /** the slots of each restriction: the numbers the game uses, and the names one types */
        private record Slots(Set<Integer> numbers, List<String> names)
        {
        }

        private static final Map<String, Slots> RESTRICTED = restrictions();

        private @Nullable String restrict;

        SlotArgument()
        {
            super("slot", net.minecraft.commands.arguments.SlotArgument.slot().getExamples(), false);
        }

        private static Map<String, Slots> restrictions()
        {
            Map<String, Slots> slots = new HashMap<>();
            for (String source : List.of("player", "enderchest", "equipment", "armor", "weapon", "container", "villager", "horse"))
            {
                slots.put(source, new Slots(new HashSet<>(), new ArrayList<>()));
            }
            for (int i = 0; i < 41; i++)
            {
                slots.get("player").numbers().add(i);
                slots.get("player").names().add("container." + i);
                slots.get("container").names().add("container." + i);
            }
            for (int i = 0; i < 9; i++)
            {
                slots.get("player").names().add("hotbar." + i);
            }
            for (int i = 0; i < 27; i++)
            {
                slots.get("player").names().add("inventory." + i);
                slots.get("enderchest").numbers().add(200 + i);
                slots.get("enderchest").names().add("enderchest." + i);
            }
            for (String place : List.of("weapon", "weapon.mainhand", "weapon.offhand"))
            {
                slots.get("player").names().add(place);
                slots.get("equipment").names().add(place);
                slots.get("weapon").names().add(place);
            }
            for (String place : List.of("armor.feet", "armor.legs", "armor.chest", "armor.head"))
            {
                slots.get("player").names().add(place);
                slots.get("equipment").names().add(place);
                slots.get("armor").names().add(place);
            }
            for (int i = 0; i < 6; i++)
            {
                slots.get("equipment").numbers().add(98 + i);
            }
            for (int i = 0; i < 4; i++)
            {
                slots.get("armor").numbers().add(100 + i);
            }
            slots.get("weapon").numbers().addAll(List.of(98, 99));
            for (int i = 0; i < 54; i++)
            {
                slots.get("container").numbers().add(i);
            }
            for (int i = 0; i < 8; i++)
            {
                slots.get("villager").numbers().add(300 + i);
                slots.get("villager").names().add("villager." + i);
            }
            for (int i = 0; i < 15; i++)
            {
                slots.get("horse").numbers().add(500 + i);
                slots.get("horse").names().add("horse." + i);
            }
            slots.get("horse").numbers().addAll(List.of(400, 401));
            slots.get("horse").names().addAll(List.of("horse.saddle", "horse.armor"));
            return slots;
        }

        @Override
        protected Set<String> modifiers()
        {
            return Set.of("restrict");
        }

        @Override
        protected CommandArgument copy()
        {
            return new SlotArgument();
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return net.minecraft.commands.arguments.SlotArgument.slot();
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            int slot = net.minecraft.commands.arguments.SlotArgument.getSlot(context, param);
            if (restrict != null && !RESTRICTED.get(restrict).numbers().contains(slot))
            {
                throw error("'" + param + "' takes only " + restrict + " slots");
            }
            return ValueConversions.ofVanillaSlotResult(slot);
        }

        @Override
        protected void configure(Map<String, Value> spec, AppCommands app)
        {
            super.configure(spec, app);
            Value declared = spec.get("restrict");
            if (declared != null)
            {
                restrict = declared.getString().toLowerCase(Locale.ROOT);
                if (!RESTRICTED.containsKey(restrict))
                {
                    throw new TypeMismatchError(SourcePosition.UNKNOWN,
                            "'restrict' of '" + name + "' cannot be '" + restrict + "'. It takes one of "
                                    + RESTRICTED.keySet().stream().sorted().toList() + ".",
                            blossom.exception.Suggestions.closestTo(restrict, List.copyOf(RESTRICTED.keySet())));
                }
                needsMatching = true;
            }
        }

        @Override
        protected Collection<String> options(CommandContext<CommandSourceStack> context, AppCommands app) throws CommandSyntaxException
        {
            return restrict == null ? super.options(context, app) : RESTRICTED.get(restrict).names();
        }
    }

    @FunctionalInterface
    private interface ValueExtractor
    {
        Value apply(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException;
    }

    /**
     * A vanilla argument, taken as it is: the game parses it, and it is turned into a value.
     *
     * <p>Its suggestions come either from the game ({@code suggestionProvider}) or from its examples:
     * a copy keeps both, which is what makes a custom type built on {@code 'effect'} still suggest
     * the effects.
     */
    private static final class VanillaArgument extends CommandArgument
    {
        private final Function<CommandBuildContext, ArgumentType<?>> type;
        private final ValueExtractor extractor;
        private final boolean fromExamples;
        private final @Nullable Function<String, SuggestionProvider<CommandSourceStack>> gameSuggestions;

        VanillaArgument(String name, Function<CommandBuildContext, ArgumentType<?>> type, ValueExtractor extractor,
                boolean fromExamples)
        {
            this(name, type, extractor, fromExamples, null);
        }

        VanillaArgument(String name, Function<CommandBuildContext, ArgumentType<?>> type, ValueExtractor extractor,
                Function<String, SuggestionProvider<CommandSourceStack>> suggestions)
        {
            this(name, type, extractor, false, suggestions);
        }

        private VanillaArgument(String name, Function<CommandBuildContext, ArgumentType<?>> type, ValueExtractor extractor,
                boolean fromExamples, @Nullable Function<String, SuggestionProvider<CommandSourceStack>> suggestions)
        {
            super(name, examplesOf(type), fromExamples);
            this.type = type;
            this.extractor = extractor;
            this.fromExamples = fromExamples;
            this.gameSuggestions = suggestions;
            this.suggestionProvider = suggestions;
        }

        /** The examples of a type that needs no registries; a registry type has none of its own. */
        private static Collection<String> examplesOf(Function<CommandBuildContext, ArgumentType<?>> type)
        {
            try
            {
                return type.apply(null).getExamples();
            }
            catch (RuntimeException ignored)
            {
                return List.of();
            }
        }

        static <T> VanillaArgument ofRegistry(String name, ResourceKey<Registry<T>> registry)
        {
            return new VanillaArgument(name, c -> ResourceArgument.resource(c, registry),
                    (c, p) -> ValueConversions.of(ResourceArgument.getResource(c, p, registry).key()),
                    param -> (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                            ctx.getSource().getServer().registryAccess().lookupOrThrow(registry).keySet(), builder));
        }

        @Override
        protected CommandArgument copy()
        {
            return new VanillaArgument(name, type, extractor, fromExamples, gameSuggestions);
        }

        @Override
        protected ArgumentType<?> argumentType(CommandBuildContext context)
        {
            return type.apply(context);
        }

        @Override
        protected Value valueOf(CommandContext<CommandSourceStack> context, String param) throws CommandSyntaxException
        {
            return extractor.apply(context, param);
        }
    }

    /** The names of the built-in types, for the documentation and the suggestions of errors. */
    public static List<String> builtInNames()
    {
        return Collections.unmodifiableList(new ArrayList<>(BUILT_INS.keySet()));
    }
}
