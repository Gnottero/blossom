package blossom.utils;

/**
 * Le poche funzioni matematiche che il core prendeva da {@code net.minecraft.util.Mth}.
 *
 * <p>Riscritte qui perché il core non può dipendere da Minecraft: sono banali e stanno sul
 * percorso caldo dei generatori di rumore, quindi restano statiche e senza allocazioni.
 */
public final class MathUtils
{
    private MathUtils()
    {
    }

    /**
     * Parte intera inferiore, come {@code Mth.floor}: evita il doppio arrotondamento di
     * {@code (int) Math.floor(value)} passando per il cast diretto.
     */
    public static int floor(double value)
    {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
