package blossom.value;


/**
 * La base dei valori che esistono solo per l'interprete e non per chi scrive lo script - firme di
 * funzione, argomenti spacchettati, annotazioni.
 *
 * <p>Non sono valori del linguaggio: se uno di questi arriva dove ne serve uno vero, l'errore va
 * segnalato invece che nascosto dietro una conversione plausibile.
 */
public abstract class FrameworkValue extends Value
{
    @Override
    public String getString()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as a value");
    }

    @Override
    public boolean getBoolean()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as a value");
    }

    @Override
    public Value clone()
    {
        throw new UnsupportedOperationException("Internal language component cannot be copied");
    }

    @Override
    public int hashCode()
    {
        throw new UnsupportedOperationException("Internal language component cannot be used as map key");
    }

}
