package com.gnottero.blossom.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * L'elenco dei file che i generatori hanno prodotto.
 *
 * <p>Serve a una cosa sola: sapere, alla rigenerazione, quali file sono nostri. Cosi' un json scritto
 * a mano dentro la stessa cartella non viene mai toccato, e uno generato che non serve piu' non resta
 * li' a fare da fantasma. Senza questo elenco le due cose sarebbero indistinguibili, e l'unica scelta
 * sarebbe fra cancellare troppo e non cancellare mai.
 *
 * <p>Un file di testo, una riga per percorso: si legge, si mette sotto controllo di versione, e se
 * qualcuno lo cancella il peggio che succede e' che la ripulitura salta un giro.
 */
public final class GeneratedIndex
{
    public static final String FILE_NAME = ".blossom-generated";

    private static final String HEADER =
            "# I file qui sotto li scrive '/blossom data generate'.\n"
            + "# Alla rigenerazione vengono riscritti, e quelli che non servono piu' tolti.\n"
            + "# Quello che non e' elencato qui non viene toccato: e' il posto dove scrivere a mano.\n";

    private GeneratedIndex()
    {
    }

    public static List<String> read(Path data)
    {
        Path file = data.resolve(FILE_NAME);
        if (!Files.isRegularFile(file))
        {
            return List.of();
        }
        try
        {
            List<String> paths = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
            {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
                {
                    paths.add(trimmed);
                }
            }
            return paths;
        }
        catch (IOException e)
        {
            // un elenco illeggibile non deve fermare una generazione: si riparte da zero, e al
            // massimo qualche file generato prima resta li'
            return List.of();
        }
    }

    public static void write(Path data, List<String> paths) throws IOException
    {
        paths.sort(String::compareTo);
        Files.writeString(data.resolve(FILE_NAME), HEADER + String.join("\n", paths) + "\n",
                StandardCharsets.UTF_8);
    }
}
