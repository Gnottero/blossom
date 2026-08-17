package blossom.interpreter;

/**
 * Il nodo compilato che legge una variabile, e sa dirne il nome.
 *
 * <p>Il nome di una variabile è cosa del nodo, non del valore: viene dal parsing e non cambia più.
 * Chi assegna - {@code =}, {@code +=}, {@code <>}, {@code let} - lo chiede qui invece di valutare la
 * parte sinistra e leggerlo su quello che ne esce. Così un assegnamento non legge il valore vecchio
 * per scoprire come si chiama la variabile, e nessun valore deve portarsi dietro il nome di dove è
 * finito. Vedi DECISIONI.md D-093.
 */
public interface VariableRead extends LazyValue
{
    /** Il nome della variabile letta, già internato. */
    String variableName();
}
