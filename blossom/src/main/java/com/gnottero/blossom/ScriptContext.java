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
import blossom.value.Value;

/**
 * Il contesto di esecuzione dentro il gioco: chi ha lanciato il codice, e da dove.
 *
 * <p>Il core non sa cosa sia un mondo o un giocatore: e' questo contesto a portarli, ed e' per
 * questo che ogni funzione dell'API di gioco comincia controllando di averlo. Un'espressione
 * valutata fuori dal gioco non ha una sorgente, e infatti la' quelle funzioni non si possono usare.
 */
public class ScriptContext extends Context
{
    private CommandSourceStack source;
    /** l'origine delle coordinate relative: e' il blocco da cui lo script e' stato lanciato */
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

    /** Cambia chi esegue: serve a {@code run()} e alle funzioni che eseguono per conto di un altro. */
    public void swapSource(CommandSourceStack source)
    {
        this.source = source;
    }

    @Override
    public ScriptContext duplicate()
    {
        return new ScriptContext(host, source, origin);
    }

    // '_x', '_y' e '_z' non vengono preparate qui. Le scrive 'scan' prima di valutare il corpo, e
    // fuori da uno scan non vogliono dire niente: seminarle in ogni frame significava tre
    // inserimenti nella mappa ad ogni chiamata di funzione, per un valore che nessuno legge.
    // Fuori da uno scan ora sono variabili non definite come tutte le altre, cioe' null.
}
