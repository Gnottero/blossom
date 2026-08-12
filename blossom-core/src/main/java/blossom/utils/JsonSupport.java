package blossom.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import blossom.value.Value;

/**
 * L'istanza gson condivisa che sa costruire {@link Value} a partire da json.
 *
 * <p>Nel fork stava in {@code carpet.script.api.Auxiliary}, cioè nel layer Minecraft, pur non
 * avendone alcun bisogno: la decodifica json è una funzione del linguaggio e vive nel core.
 */
public final class JsonSupport
{
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Value.class, new ScarpetJsonDeserializer())
            .create();

    private JsonSupport()
    {
    }
}
