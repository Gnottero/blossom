package blossom.exception;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

/**
 * Il "forse intendevi" degli errori.
 *
 * <p>Il piano (§4) è esplicito su due punti: la distanza si calcola con un fuzzy match, e i
 * candidati sono solo i simboli davvero visibili in quel punto - non tutto lo spazio dei nomi, che
 * produrrebbe suggerimenti a caso e farebbe perdere tempo a chi legge.
 */
public final class Suggestions
{
    /** oltre questa distanza due nomi non si somigliano abbastanza da valere un suggerimento */
    private static final int MAX_DISTANCE = 3;

    private Suggestions()
    {
    }

    /**
     * @return il candidato più vicino a {@code name}, o {@code null} se nessuno gli somiglia
     */
    public static @Nullable String closestTo(String name, Collection<String> candidates)
    {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        // un nome corto tollera meno errori di uno lungo: 'ad' vs 'add' e' un refuso, 'a' vs 'add' no
        int limit = Math.min(MAX_DISTANCE, Math.max(1, name.length() / 2 + 1));
        for (String candidate : candidates)
        {
            int distance = distance(name, candidate, limit);
            if (distance <= limit && distance < bestDistance)
            {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Distanza di Levenshtein, con taglio: appena una riga intera supera {@code limit} il risultato
     * non può più scendere sotto, quindi si smette invece di riempire tutta la matrice.
     */
    public static int distance(String a, String b, int limit)
    {
        if (a.equals(b))
        {
            return 0;
        }
        if (Math.abs(a.length() - b.length()) > limit)
        {
            return limit + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
        {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++)
        {
            current[0] = i;
            int rowMinimum = current[0];
            for (int j = 1; j <= b.length(); j++)
            {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
                rowMinimum = Math.min(rowMinimum, current[j]);
            }
            if (rowMinimum > limit)
            {
                return limit + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
