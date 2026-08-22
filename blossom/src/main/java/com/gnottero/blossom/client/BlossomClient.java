package com.gnottero.blossom.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The client side, which is optional.
 *
 * <p>The mod is server-side: everything it does goes through the server, and a vanilla player
 * does not lose any feature. This half serves a single purpose (drawing shapes as shapes instead
 * of as particles), and whoever does not have it sees the approximation. This is the contract
 * declared in {@link com.gnottero.blossom.net.BlossomPayloads}, and it is worth repeating here:
 * nobody is forced to install anything.
 *
 * <p>There is no game logic on this side, and there must not be: a client that decided something
 * on its own would be a client the server would have to trust.
 */
@Environment(EnvType.CLIENT)
public final class BlossomClient
{
    private static final ClientShapes SHAPES = new ClientShapes();

    private BlossomClient()
    {
    }

    public static ClientShapes shapes()
    {
        return SHAPES;
    }

    /** The {@code client} entrypoint declared in {@code fabric.mod.json}. */
    public static void onClientStarted()
    {
        com.gnottero.blossom.BlossomMod.LOG.info("Blossom client side ready: shapes will be drawn");
    }
}
