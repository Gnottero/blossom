package blossom.exception;

/**
 * The script has exceeded its execution budget: too much time, or too much recursion.
 *
 * <p>This is the watchdog's error. It deliberately lives outside the
 * catchable {@code try}/{@code catch} hierarchy: if a script could intercept it, an infinite loop
 * inside a {@code try} would keep running and the watchdog would be useless. The only way out is
 * for execution to end.
 */
public class ExecutionBudgetError extends BlossomError
{
    /** What was exceeded: lets the two cases be told apart even from the logs. */
    public enum Limit
    {
        TIME("time"), DEPTH("call depth"),
        /** the server stopped every script, with {@code /blossom script stop} */
        STOPPED("stopped");

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
