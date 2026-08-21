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
 * {@code core.mc.data} - scrivere il contenuto di gioco di un pack.
 *
 * <p>Il contenuto di un pack sta in {@code data/}, con l'albero di un datapack vero. Scriverlo a mano
 * si puo' e va benissimo; questo modulo serve a **generarlo**, che e' il motivo per cui conviene
 * averlo in un linguaggio invece che in quaranta file quasi identici.
 *
 * <p>Si usa solo durante {@code /blossom data generate <app>}, che chiama la {@code generate()}
 * dell'app: fuori di li' scrivere non avrebbe un momento giusto in cui farlo, e l'errore lo dice.
 */
public final class ContentApi
{
    // i nomi pubblici del modulo stanno in BlossomApi.DATA, con tutti gli altri

    private ContentApi()
    {
    }

    public static void apply(Expression expression)
    {
        /*
         * write(id, valore) - una risorsa json.
         *
         * L'id e' quello dei comandi del gioco, 'spazio:cartella/nome', e il percorso lo decide lui:
         * chi scrive non deve sapere che 'data/' ha quella forma li'.
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

        /* write_text(id, righe) - per i pochi file che json non sono, come una .mcfunction. */
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
