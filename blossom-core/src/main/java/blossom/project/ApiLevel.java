package blossom.project;

import blossom.exception.CompatibilityError;
import blossom.exception.SourcePosition;

/**
 * Il livello dell'API dichiarato nel manifesto, e la regola che dice se un'app può girare.
 *
 * <h2>Cosa vuol dire un numero</h2>
 *
 * <p>{@code api_level} è {@code MAJOR.MINOR} e descrive <em>l'API di Blossom</em>, non la versione
 * dell'app. Il contratto è questo, e vale sia per il linguaggio sia per i moduli {@code core.*}:
 *
 * <ul>
 *   <li><b>MAJOR</b> cambia quando qualcosa che funzionava smette di funzionare: un simbolo tolto o
 *       rinominato, la firma o il significato di una funzione che cambia, una regola di grammatica
 *       che invalida codice già scritto, un evento che smette di scattare o cambia argomenti.</li>
 *   <li><b>MINOR</b> cambia quando si aggiunge: un modulo nuovo, una funzione nuova, un parametro
 *       opzionale in più, un evento nuovo. Codice scritto per un MINOR precedente continua a
 *       girare.</li>
 * </ul>
 *
 * <p>Correzioni di bug che non cambiano un comportamento documentato non muovono nessuno dei due.
 *
 * <h2>La regola</h2>
 *
 * <p>Un'app gira se {@code MAJOR} combacia esattamente e il suo {@code MINOR} non supera quello del
 * runtime. Le due direzioni falliscono per motivi diversi e il messaggio lo dice: con un MAJOR
 * diverso l'app è scritta per un'altra API; con un MINOR più alto l'app usa qualcosa che questa
 * versione non ha ancora.
 *
 * <p>Il campo resta facoltativo: chi non lo scrive dichiara implicitamente il livello più basso del
 * MAJOR corrente, cioè "non uso niente di recente".
 */
public record ApiLevel(int major, int minor) implements Comparable<ApiLevel>
{
    /**
     * Il livello dell'API fornita da questo runtime.
     *
     * <p>Va alzato insieme al cambiamento che lo giustifica, non a fine ciclo: un numero aggiornato
     * dopo, in blocco, non dice più a quale versione risale ogni funzione.
     */
    public static final ApiLevel CURRENT = new ApiLevel(1, 1);

    /** Quello che si assume per un manifesto che non lo dichiara. */
    public static final ApiLevel DEFAULT = new ApiLevel(1, 0);

    /**
     * @throws IllegalArgumentException se non è nella forma {@code MAJOR.MINOR}
     */
    public static ApiLevel parse(String text)
    {
        String[] parts = text.trim().split("\\.");
        if (parts.length != 2)
        {
            throw new IllegalArgumentException("api_level '" + text + "': it must have the form MAJOR.MINOR, e.g. 1.0");
        }
        try
        {
            return new ApiLevel(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("api_level '" + text + "': MAJOR and MINOR must be numbers");
        }
    }

    /** @return se un'app che dichiara questo livello può girare su {@code runtime} */
    public boolean isSupportedBy(ApiLevel runtime)
    {
        return major == runtime.major() && minor <= runtime.minor();
    }

    /**
     * Controlla il livello e fallisce con un messaggio che dice cosa fare.
     *
     * @throws CompatibilityError se l'app non può girare su questo runtime
     */
    public void requireSupportedBy(ApiLevel runtime, String appName, SourcePosition where)
    {
        if (isSupportedBy(runtime))
        {
            return;
        }
        if (major != runtime.major())
        {
            throw new CompatibilityError(where, "App '" + appName + "' declares api_level " + this
                    + ", but this Blossom provides API " + runtime + ". These are two different APIs: between one"
                    + " MAJOR and the next, something that worked was removed or changed meaning, so the app has"
                    + " to be updated, not forced.");
        }
        throw new CompatibilityError(where, "App '" + appName + "' declares api_level " + this
                + ", but this Blossom only goes up to " + runtime + ". The app uses something this version does"
                + " not have yet: update the mod, or lower 'api_level' if you do not actually need it.");
    }

    @Override
    public int compareTo(ApiLevel other)
    {
        int byMajor = Integer.compare(major, other.major);
        return byMajor != 0 ? byMajor : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString()
    {
        return major + "." + minor;
    }
}
