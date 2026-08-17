package blossom.value;

import org.jspecify.annotations.Nullable;

/**
 * Il riferimento a una variabile: il nome a sinistra di un assegnamento, e il valore che c'è adesso.
 *
 * <p>È l'l-value delle variabili, come {@link LContainerValue} è quello degli elementi di un
 * contenitore. Nasce solo quando un nome viene valutato in contesto {@link
 * blossom.interpreter.Context#LVALUE} - dove qualcuno sta per assegnarci sopra - o {@link
 * blossom.interpreter.Context#LOCALIZATION}, cioè dentro una firma, dove un identificatore è il
 * nome di un parametro e non una variabile da leggere.
 *
 * <p>Prima il nome viveva in un campo mutabile su ogni {@link Value} ({@code boundVariable}):
 * informazione da l-value appiccicata all'r-value, che obbligava a copiare ogni valore che entrava
 * in una variabile perché il chiamante non se lo vedesse rinominare sotto. Il nome ce l'ha il nodo
 * che legge la variabile - dal parsing, e non cambia più - quindi è lui a dirlo, e solo quando
 * serve. Vedi DECISIONI.md D-093.
 */
public final class VariableReferenceValue extends FrameworkValue
{
    private final String name;
    private final @Nullable Value current;

    /** Un riferimento dentro una firma: lì il nome non ha ancora nessun valore dietro. */
    public VariableReferenceValue(String name)
    {
        this(name, null);
    }

    public VariableReferenceValue(String name, @Nullable Value current)
    {
        this.name = name;
        this.current = current;
    }

    public String name()
    {
        return name;
    }

    /** Il valore che la variabile ha adesso, o {@code null} dentro una firma. */
    public @Nullable Value current()
    {
        return current;
    }

    /**
     * Il valore dietro {@code value} se è un riferimento, {@code value} stesso altrimenti.
     *
     * <p>Serve a chi valuta il proprio argomento in contesto di l-value per poterci scrivere -
     * {@code put}, {@code get}, {@code delete} - e si ritrova un nome quando l'argomento era una
     * variabile e non un accesso a un elemento.
     */
    public static Value dereference(Value value)
    {
        if (value instanceof final VariableReferenceValue reference && reference.current != null)
        {
            return reference.current;
        }
        return value;
    }

    @Override
    public String getString()
    {
        return name;
    }

    @Override
    public String getPrettyString()
    {
        return name;
    }

    @Override
    public String getTypeString()
    {
        return "variable";
    }
}
