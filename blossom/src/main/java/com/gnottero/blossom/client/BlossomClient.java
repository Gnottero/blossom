package com.gnottero.blossom.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Il lato client, che è facoltativo.
 *
 * <p>Il mod è server-side: tutto quello che fa passa dal server, e chi gioca vanilla non perde
 * nessuna funzione. Questa metà serve a una cosa sola — disegnare le forme come forme invece che
 * come particelle — e chi non ce l'ha vede l'approssimazione. È il patto dichiarato in
 * {@link com.gnottero.blossom.net.BlossomPayloads}, e vale la pena ripeterlo qui: nessuno è costretto a
 * installare niente.
 *
 * <p>Non c'è nessuna logica di gioco da questa parte, e non ci deve essere: un client che decidesse
 * qualcosa sarebbe un client di cui il server deve fidarsi.
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

    /** Entrypoint {@code client} dichiarato in {@code fabric.mod.json}. */
    public static void onClientStarted()
    {
        com.gnottero.blossom.BlossomMod.LOG.info("Blossom client side ready: shapes will be drawn");
    }
}
