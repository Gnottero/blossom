package com.gnottero.blossom;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import blossom.interpreter.Context;
import blossom.interpreter.ScriptHost;

/**
 * The execution context inside the game: who launched the code, and from where.
 *
 * <p>The core does not know what a world or a player is: this context carries them, and that is why
 * every game API function starts by checking it has one. An expression evaluated outside the game
 * has no source, and indeed those functions cannot be used there.
 */
public class ScriptContext extends Context
{
    private CommandSourceStack source;
    /** the origin of relative coordinates: the block the script was launched from */
    private final BlockPos origin;

    public ScriptContext(ScriptHost host, CommandSourceStack source)
    {
        this(host, source, BlockPos.ZERO);
    }

    public ScriptContext(ScriptHost host, CommandSourceStack source, BlockPos origin)
    {
        super(host);
        this.source = source;
        this.origin = origin;
    }

    public CommandSourceStack source()
    {
        return source;
    }

    public MinecraftServer server()
    {
        return source.getServer();
    }

    public ServerLevel level()
    {
        return source.getLevel();
    }

    public RegistryAccess registryAccess()
    {
        return source.getLevel().registryAccess();
    }

    public <T> Registry<T> registry(ResourceKey<? extends Registry<? extends T>> key)
    {
        return registryAccess().lookupOrThrow(key);
    }

    public BlockPos origin()
    {
        return origin;
    }

    /** Changes who executes: used by {@code run()} and by functions running on behalf of another. */
    public void swapSource(CommandSourceStack source)
    {
        this.source = source;
    }

    @Override
    public ScriptContext duplicate()
    {
        return new ScriptContext(host, source, origin);
    }

    // '_x', '_y' and '_z' are not prepared here. 'scan' writes them before evaluating the body, and
    // outside a scan they mean nothing: seeding them in every frame meant three map insertions per
    // function call, for a value nobody reads. Outside a scan they are now undefined variables like
    // any other, that is null.
}
