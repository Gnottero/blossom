package blossom.utils;

/**
 * The handful of math functions the core used to take from {@code net.minecraft.util.Mth}.
 *
 * <p>Rewritten here because the core cannot depend on Minecraft. They are trivial and sit on
 * the hot path of the noise generators, so they stay static and allocation-free.
 */
public final class MathUtils
{
    private MathUtils()
    {
    }

    /**
     * Floor, like {@code Mth.floor}: avoids the double rounding of
     * {@code (int) Math.floor(value)} by going through a direct cast instead.
     */
    public static int floor(double value)
    {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
