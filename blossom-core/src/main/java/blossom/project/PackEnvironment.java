package blossom.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Cosa c'è intorno a un progetto: gli altri pack installati e, se configurato, un registry.
 *
 * <p>Il core non sa cosa sia un mondo di Minecraft. Sa che esiste un posto dove i pack stanno già
 * installati e che qualcuno sa elencarli: quel qualcuno è il mod, che qui dentro passa la cartella
 * {@code <mondo>/blossom/}. Fuori dal gioco - nei test, o incorporando il core altrove - c'è
 * {@link #NONE}, e allora esistono solo le dipendenze a percorso.
 */
public interface PackEnvironment
{
    /** Nessun pack installato intorno, nessun registry: solo dipendenze a percorso locale. */
    PackEnvironment NONE = new PackEnvironment()
    {
        @Override
        public Optional<Path> installedPack(String name)
        {
            return Optional.empty();
        }

        @Override
        public List<String> installedPackNames()
        {
            return List.of();
        }

        @Override
        public @Nullable PackRegistry registry()
        {
            return null;
        }
    };

    /** La cartella del pack {@code name} già installato, se c'è. */
    Optional<Path> installedPack(String name);

    /** I pack installati, per suggerire un nome a chi ne ha scritto uno che non esiste. */
    List<String> installedPackNames();

    /** Il registry configurato, o {@code null} se su questo server non ce n'è uno. */
    @Nullable PackRegistry registry();
}
