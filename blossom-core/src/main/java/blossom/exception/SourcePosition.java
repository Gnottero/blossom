package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * Dove, esattamente: file, riga, colonna.
 *
 * <p>Il piano (§4) chiede che ogni errore sappia dirlo. Un valore immutabile e minuscolo, perché ne
 * vengono costruiti tanti durante il parsing.
 */
public record SourcePosition(String file, int line, int column)
{
    /** Posizione ignota: usata solo dove l'informazione davvero non c'è, mai come scorciatoia. */
    public static final SourcePosition UNKNOWN = new SourcePosition("<unknown>", 0, 0);

    public boolean isKnown()
    {
        return line > 0;
    }

    @Override
    public String toString()
    {
        return isKnown() ? file + ":" + line + ":" + column : file;
    }

    public static SourcePosition of(@Nullable String file, int line, int column)
    {
        return file == null ? UNKNOWN : new SourcePosition(file, line, column);
    }
}
