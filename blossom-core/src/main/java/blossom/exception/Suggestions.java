package blossom.exception;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

/**
 * The "did you mean" of errors.
 *
 * <p>The plan (§4) is explicit on two points: the distance is computed with a fuzzy match, and the
 * candidates are only the symbols actually visible at that point, not the whole namespace, which
 * would produce random suggestions and waste the reader's time.
 */
public final class Suggestions
{
    /** Beyond this distance, two names don't resemble each other enough to be worth suggesting. */
    private static final int MAX_DISTANCE = 3;

    private Suggestions()
    {
    }

    /**
     * @return the candidate closest to {@code name}, or {@code null} if none resembles it
     */
    public static @Nullable String closestTo(String name, Collection<String> candidates)
    {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        // a short name tolerates fewer errors than a long one: 'ad' vs 'add' is a typo, 'a' vs 'add' is not
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
     * Levenshtein distance, with a cutoff: as soon as an entire row exceeds {@code limit} the result
     * can no longer drop below it, so computation stops instead of filling the whole matrix.
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
