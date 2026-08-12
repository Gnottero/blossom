package blossom.value;

/**
 * Valore che sa combinarsi con quello che lo precede in una somma.
 *
 * <p>Serve per la doppia dispatch di {@code a + b}: il core sa concatenare due stringhe, ma tipi
 * definiti dal layer di integrazione - il testo formattato di Minecraft, per esempio - devono poter
 * decidere loro il risultato anche quando stanno a destra dell'operatore. Prima quel caso era una
 * {@code instanceof FormattedTextValue} dentro {@link Value#add}, che legava il core a un tipo che
 * non gli appartiene.
 */
public interface Concatenable
{
    /**
     * @param left il valore alla sinistra dell'operatore
     * @return il risultato di {@code left + this}
     */
    Value concatenatedWith(Value left);
}
