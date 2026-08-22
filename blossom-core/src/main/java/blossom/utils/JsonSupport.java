package blossom.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import blossom.value.Value;

/**
 * The shared Gson instance that knows how to build {@link Value} instances from JSON.
 *
 * <p>In the fork this lived in {@code carpet.script.api.Auxiliary}, i.e. the Minecraft layer,
 * even though it had no need to be there: JSON decoding is a language feature and belongs in
 * the core.
 */
public final class JsonSupport
{
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Value.class, new BlossomJsonDeserializer())
            .create();

    private JsonSupport()
    {
    }
}
