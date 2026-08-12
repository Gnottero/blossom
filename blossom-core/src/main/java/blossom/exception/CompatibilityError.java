package blossom.exception;

import org.jspecify.annotations.Nullable;

/**
 * L'app e quello che ha intorno non vanno d'accordo: livello di API sbagliato, dipendenza a una
 * versione che non soddisfa il vincolo dichiarato, integrità del lock che non torna.
 *
 * <p>Categoria a sé e non un {@code RuntimeError} generico perché è l'unico errore che non riguarda
 * il codice: il programma può essere perfetto e fallire lo stesso, e chi lo legge deve capire subito
 * che deve andare a guardare il manifesto e non i sorgenti.
 *
 * <p>Nasce sempre a caricamento, mai a runtime: se una versione non torna, lo si scopre prima che
 * una riga venga eseguita.
 */
public class CompatibilityError extends BlossomError
{
    public CompatibilityError(SourcePosition position, String message)
    {
        super(position, message);
    }

    public CompatibilityError(SourcePosition position, String message, @Nullable String suggestion)
    {
        super(position, message, suggestion);
    }
}
