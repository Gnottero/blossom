package blossom.interpreter;

import blossom.exception.ExecutionBudgetError;
import blossom.exception.SourcePosition;

/**
 * Il watchdog: quanto può girare un'esecuzione prima che venga fermata (PIANO_LINGUAGGIO.md §9).
 *
 * <p>Il problema concreto è che il codice gira dentro il tick loop del server: uno script che cicla
 * all'infinito dentro un evento non rallenta l'app, ferma il mondo. Il fork di partenza non aveva
 * nessuna protezione da riportare, quindi questa è disegnata da zero.
 *
 * <h2>Come conta</h2>
 *
 * <p>Il contatore viene battuto solo dove l'esecuzione può ripetersi all'infinito: le iterazioni dei
 * loop e le chiamate a funzione definita dall'utente. Battere un contatore su ogni singolo nodo
 * dell'albero costerebbe più di quello che protegge, e non servirebbe: un'espressione senza cicli né
 * chiamate è finita per costruzione.
 *
 * <p>L'orologio invece viene letto una volta ogni {@link #CLOCK_CHECK_MASK}+1 battute. {@code
 * System.nanoTime()} costa decine di nanosecondi ed è il tipo di chiamata che, dentro un loop stretto
 * di un interprete tree-walking, si vede nel benchmark; un incremento di un {@code long} no.
 *
 * <h2>Un budget per esecuzione, non per app</h2>
 *
 * <p>Un'istanza misura una singola esecuzione: una chiamata di evento, un comando, il caricamento di
 * un modulo. Vive sul {@link Context} e viene ereditata dai frame figli, quindi il tempo speso in una
 * funzione conta per il budget di chi l'ha chiamata - altrimenti basterebbe spezzare il ciclo
 * infinito in due funzioni per aggirarlo.
 *
 * <p>Un'istanza non è condivisa fra thread: un {@code task()} parte su un thread suo e riceve il
 * proprio budget dalla stessa politica (vedi {@link Policy#start()}), perché un task in background
 * non occupa il tick del server e non ha senso misurarlo insieme a chi l'ha lanciato.
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

    /**
     * Forza il controllo dell'orologio, senza aspettare la battuta buona: serve dove una singola
     * operazione può essere lunga da sola, e dove il costo di una lettura in più è irrilevante.
     */
    public void checkNow()
    {
        if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() > deadlineNanos)
        {
            throw timedOut();
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
