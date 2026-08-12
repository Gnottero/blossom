package com.gnottero.blossom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import blossom.project.PackEnvironment;
import blossom.project.PackRegistry;
import blossom.project.ProjectManifest;

/**
 * Cosa c'è intorno a un pack, visto dal mondo: gli altri pack installati sotto
 * {@code <mondo>/blossom/}, e il registry se il server ne ha uno configurato.
 *
 * <p>È la risposta alla voce "dipendenze fra pack" della roadmap. Prima un pack poteva dipendere solo
 * da un percorso relativo, il che funziona per due progetti che vivono nella stessa cartella dello
 * sviluppatore e non per due pack installati da un amministratore, che stanno l'uno accanto
 * all'altro ma non sanno niente uno dell'altro. Con {@code {"pack": "nome"}} la dipendenza si
 * dichiara per nome, e chi installa non deve inventarsi percorsi.
 *
 * <p>Un pack è una cartella con dentro un {@code blossom.json}: la stessa definizione che usa
 * {@code /blossom script load}, così non ci sono due idee diverse di cosa sia un pack.
 */
public record WorldPacks(Path scriptDirectory, @Nullable PackRegistry registry) implements PackEnvironment
{
    @Override
    public Optional<Path> installedPack(String name)
    {
        Path candidate = scriptDirectory.resolve(name.toLowerCase(java.util.Locale.ROOT));
        // resolve() con un nome tipo "../altro" uscirebbe dalla cartella degli script: un pack puo'
        // dipendere da un altro pack del mondo, non da una cartella qualunque del disco del server
        if (!candidate.normalize().startsWith(scriptDirectory.normalize()))
        {
            return Optional.empty();
        }
        return Files.isRegularFile(candidate.resolve(ProjectManifest.FILE_NAME))
                ? Optional.of(candidate)
                : Optional.empty();
    }

    @Override
    public List<String> installedPackNames()
    {
        try (Stream<Path> entries = Files.list(scriptDirectory))
        {
            return entries
                    .filter(entry -> Files.isRegularFile(entry.resolve(ProjectManifest.FILE_NAME)))
                    .map(entry -> entry.getFileName().toString())
                    .sorted()
                    .toList();
        }
        catch (IOException e)
        {
            return List.of();
        }
    }
}
