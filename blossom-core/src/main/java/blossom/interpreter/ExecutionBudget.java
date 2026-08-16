package blossom.interpreter;

import blossom.exception.ExecutionBudgetError;
import blossom.exception.SourcePosition;

/**
 * Il watchdog: quanto puo' girare un'esecuzione prima che venga fermata (PIANO_LINGUAGGIO.md §9).
 *
 * <p>Il codice gira dentro il tick loop del server: uno script che cicla dentro un evento non
 * rallenta l'app, ferma il mondo.
 *
 * <p>Il contatore viene battuto solo dove l'esecuzione puo' ripetersi all'infinito - iterazioni dei
 * loop e chiamate a funzione utente - perche' un'espressione senza cicli ne' chiamate e' finita per
 * costruzione. L'orologio si legge una volta ogni {@link #CLOCK_CHECK_MASK}+1 battute: dentro il
 * loop stretto di un interprete tree-walking, {@code System.nanoTime()} si vede nel benchmark.
 *
 * <p>Un'istanza misura una singola esecuzione (un evento, un comando, un caricamento), vive sul
 * {@link Context} ed e' ereditata dai frame figli - altrimenti basterebbe spezzare il ciclo in due
 * funzioni per aggirarla. Un {@code task()} riceve invece il proprio budget dalla stessa politica:
 * gira su un thread suo e non occupa il tick.
 */
public final class ExecutionBudget
{
    /**
     * Ogni quante battute si guarda l'orologio: 4096 battute di un loop vuoto sono nell'ordine delle
     * decine di microsecondi, quindi la risoluzione del watchdog resta molto sotto un tick.
     */
    private static final int CLOCK_CHECK_MASK = 0xFFF;

    /**
     * I limiti, senza lo stato: è quello che un'app dichiara nel manifesto e che il server tiene come
     * default. Immutabile, quindi condivisibile fra tutte le esecuzioni di un'app.
     *
     * @param timeoutMillis tempo massimo di una esecuzione, {@code 0} per nessun limite
     * @param maxDepth      chiamate a funzione annidate, {@code 0} per nessun limite
     */
    public record Policy(long timeoutMillis, int maxDepth)
    {
        /** Nessun limite: i test del linguaggio e chi incorpora il core fuori da un server. */
        public static final Policy UNLIMITED = new Policy(0, 0);

        /**
         * Il default dentro il gioco.
         *
         * <p>Due secondi sono tantissimo per un tick (che ne dura 0,05) e pochissimo per un ciclo
         * infinito: la soglia non serve a far rispettare il budget di tick - quello è compito di chi
         * scrive lo script - ma a distinguere "lento" da "non finirà mai". La profondità massima
         * arriva prima dello {@code StackOverflowError} della JVM, così l'errore dice dove sei
         * invece di essere un crash dello stack java.
         */
        public static final Policy DEFAULT = new Policy(2000, 256);

        /**
         * Il default per quello che l'utente ha lanciato a mano: un comando o il caricamento di
         * un'app possono legittimamente macinare a lungo, e chi ha premuto invio sta aspettando.
         */
        public static final Policy INTERACTIVE = new Policy(15000, 256);

        /**
         * I limiti di un {@code task()}.
         *
         * <p>Nessun limite di tempo, ma la profondità di chiamata resta: un task gira su un thread
         * suo e non tiene fermo il tick, e "gira a lungo" è esattamente il motivo per cui uno lo
         * lancia. Fermarlo a due secondi renderebbe inutile la funzione che serve a evitare il
         * problema che il watchdog risolve.
         */
        public Policy asBackground()
        {
            return new Policy(0, maxDepth);
        }

        public boolean isUnlimited()
        {
            return timeoutMillis <= 0 && maxDepth <= 0;
        }

        /** Un misuratore nuovo, con l'orologio che parte adesso. */
        public ExecutionBudget start()
        {
            return isUnlimited() ? UNLIMITED_BUDGET : new ExecutionBudget(this);
        }
    }

    /**
     * Il misuratore che non misura niente: un singleton, così un contesto senza watchdog non alloca e
     * le sue battute sono un ramo prevedibile e basta.
     */
    public static final ExecutionBudget UNLIMITED_BUDGET = new ExecutionBudget(Policy.UNLIMITED);

    private final Policy policy;
    private final long deadlineNanos;
    private long ticks;
    private int depth;

    private ExecutionBudget(Policy policy)
    {
        this.policy = policy;
        this.deadlineNanos = policy.timeoutMillis() <= 0
                ? Long.MAX_VALUE
                : System.nanoTime() + policy.timeoutMillis() * 1_000_000L;
    }

    public Policy policy()
    {
        return policy;
    }

    /** Quante iterazioni/chiamate ha contato finora: lo usa il profiler per il rapporto finale. */
    public long ticks()
    {
        return ticks;
    }

    /**
     * Una battuta: la chiama chi sta per ripetere qualcosa (una iterazione di loop) o chi sta per
     * entrare in una funzione.
     *
     * <p>Scritta corta apposta perché la JVM la faccia sparire nel codice inlined quando il budget è
     * illimitato.
     */
    public void tick()
    {
        if (deadlineNanos == Long.MAX_VALUE)
        {
            return;
        }
        if ((++ticks & CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos)
        {
            throw timedOut();
        }
    }

    /** Entra in una chiamata a funzione: conta come battuta e come livello di profondità. */
    public void enterCall(String function)
    {
        tick();
        if (policy.maxDepth() > 0 && ++depth > policy.maxDepth())
        {
            depth--; // il frame non è stato aperto: non deve restare contato
            throw new ExecutionBudgetError(SourcePosition.UNKNOWN, ExecutionBudgetError.Limit.DEPTH,
                    "Too many nested calls (limit: " + policy.maxDepth() + ") when entering '" + function
                            + "'. If the recursion is intended, raise 'max_depth' in the manifest; if it is not,"
                            + " a base case is missing.");
        }
    }

    public void exitCall()
    {
        if (policy.maxDepth() > 0)
        {
            depth--;
        }
    }

    private ExecutionBudgetError timedOut()
    {
        return new ExecutionBudgetError(SourcePosition.UNKNOWN, ExecutionBudgetError.Limit.TIME,
                "Execution stopped after " + policy.timeoutMillis() + " ms: the script is running too long and the"
                        + " server is waiting for it. If it really needs all that time, move it to a task() or"
                        + " raise 'timeout_ms' in the manifest.");
    }
}
