package blossom.value;

/**
 * A value that knows how to combine with the one preceding it in a sum.
 *
 * <p>Used for the double dispatch of {@code a + b}: the core knows how to concatenate two
 * strings, but types defined by the integration layer (Minecraft's formatted text, for example)
 * need to be able to decide the result themselves even when they sit on the right of the
 * operator. This case used to be an {@code instanceof FormattedTextValue} check inside
 * {@link Value#add}, which tied the core to a type it has no business knowing about.
 */
public interface Concatenable
{
    /**
     * @param left the value to the left of the operator
     * @return the result of {@code left + this}
     */
    Value concatenatedWith(Value left);
}
