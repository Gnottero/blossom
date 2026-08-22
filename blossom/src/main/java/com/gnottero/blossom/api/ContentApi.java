package com.gnottero.blossom.api;

import java.io.IOException;

import com.google.gson.GsonBuilder;

import blossom.exception.BlossomRuntimeError;
import blossom.exception.SourcePosition;
import blossom.interpreter.Expression;
import blossom.value.ListValue;
import blossom.value.StringValue;
import blossom.value.Value;

import com.gnottero.blossom.content.Generators;

/**
 * {@code core.mc.data} - writing a pack's game content.
 *
 * <p>A pack's content lives under {@code data/}, with the layout of a real datapack. Writing it by
 * hand works fine; this module exists to **generate** it, which is the reason it makes sense to have
 * it in a language instead of forty nearly identical files.
 *
 * <p>It is only used during {@code /blossom data generate <app>}, which calls the app's
 * {@code generate()}: outside of that there is no right moment to write, and the error says so.
 */
public final class ContentApi
{
    // the module's public names live in BlossomApi.DATA, along with all the others

    private ContentApi()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * write(id, value) - a json resource.
         *
         * The id is the one used by game commands, 'namespace:folder/name', and it decides the path
         * itself: the caller doesn't need to know that 'data/' has that shape.
         */
        expression.addContextFunction("data_write", 2, (c, t, lv) ->
        {
            String id = lv.get(0).getString();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(lv.get(1).toJson());
            try
            {
                return StringValue.of(Generators.write(id, json + "\n", ".json"));
            }
            catch (IOException e)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                        "Could not write '" + id + "': " + e.getMessage());
            }
        });

        /* write_text(id, lines) - for the few files that aren't json, like a .mcfunction. */
        expression.addContextFunction("data_write_text", 2, (c, t, lv) ->
        {
            String id = lv.get(0).getString();
            Value body = lv.get(1);
            String text = body instanceof final ListValue lines
                    ? String.join("\n", lines.getItems().stream().map(Value::getString).toList()) + "\n"
                    : body.getString();
            int dot = id.lastIndexOf('.');
            int slash = id.lastIndexOf('/');
            if (dot <= slash)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                        "'" + id + "' needs the extension: write_text does not know what kind of file it is.");
            }
            try
            {
                return StringValue.of(Generators.write(id.substring(0, dot), text, id.substring(dot)));
            }
            catch (IOException e)
            {
                throw new BlossomRuntimeError(SourcePosition.UNKNOWN, "content",
                        "Could not write '" + id + "': " + e.getMessage());
            }
        });
    }
}
