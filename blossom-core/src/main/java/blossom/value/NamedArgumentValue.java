package blossom.value;

import blossom.interpreter.LazyValue;

/**
 * Un nome attaccato a un valore, dentro una lista di argomenti: {@code f(pos, update = false)}.
 *
 * <p>Nasce a parse-time - un {@code =} con un nome a sinistra, fra gli argomenti di una chiamata -
 * e viene letto in due posti diversi a seconda di dove finisce:
 *
 * <ul>
 *   <li>in una <b>firma</b>, {@code fn place(pos, update = true)}, dice che quel parametro ha un
 *       valore di riserva quando chi chiama non lo passa;
 *   <li>in una <b>chiamata</b>, {@code place(p, update = false)}, dice a quale parametro va
 *       l'argomento, indipendentemente dall'ordine.
 * </ul>
 *
 * <p>Il valore resta pigro apposta: il default di un parametro va valutato ad ogni chiamata che lo
 * usa, altrimenti {@code fn f(x = {})} darebbe a tutti la stessa mappa.
 */
public final class NamedArgumentValue extends FrameworkValue
{
    private final String name;
    private final LazyValue value;

    public NamedArgumentValue(String name, LazyValue value)
    {
        this.name = name;
        this.value = value;
    }

    public String name()
    {
        return name;
    }

    public LazyValue value()
    {
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
        return name + " = ...";
    }

    @Override
    public String getTypeString()
    {
        return "named_argument";
    }
}
