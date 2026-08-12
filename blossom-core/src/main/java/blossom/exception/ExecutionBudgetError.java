package blossom.exception;

/**
 * Lo script ha superato il budget di esecuzione: troppo tempo, o troppa ricorsione.
 *
 * <p>È l'errore del watchdog (PIANO_LINGUAGGIO.md §9). Vive fuori dalla gerarchia catturabile di
 * {@code try}/{@code catch} apposta: se uno script potesse intercettarlo, un ciclo infinito dentro
 * un {@code try} continuerebbe a girare e il watchdog non servirebbe a niente. L'unico modo di
 * uscirne è che l'esecuzione finisca.
 */
public class ExecutionBudgetError extends BlossomError
{
    /** Cosa è stato superato: serve a distinguere i due casi anche dai log. */
    public enum Limit
    {
        TIME("tempo"), DEPTH("profondita' di chiamata");

        private final String label;

        Limit(String label)
        {
            this.label = label;
        }

        public String label()
        {
            return label;
        }
    }

    private final Limit limit;

    public ExecutionBudgetError(SourcePosition position, Limit limit, String message)
    {
        super(position, message);
        this.limit = limit;
    }

    public Limit limit()
    {
        return limit;
    }

    @Override
    public String kind()
    {
        return "ExecutionBudgetError[" + limit.name().toLowerCase(java.util.Locale.ROOT) + "]";
    }
}
