package blossom.project;

import java.nio.file.Path;
import java.util.List;

/**
 * Da dove si prendono i pack che non stanno gia' su disco.
 *
 * <p>Un'interfaccia perche' il core non deve sapere se dall'altra parte c'e' una cartella, un server
 * http o niente: e' l'unico punto del sistema che puo' toccare la rete, ed e' anche l'unico che si
 * puo' spegnere del tutto ({@link PackEnvironment#registry()} puo' essere {@code null}).
 *
 * <p>Un'implementazione deve garantire tre cose: {@link #availableVersions} non lancia se il pack non
 * esiste ma restituisce una lista vuota; {@link #fetch} restituisce una cartella gia' completa, che
 * chi la riceve legge come un progetto qualunque; e quello che scarica va verificato prima, perche'
 * un archivio scompattato senza controllo e' codice arbitrario su un server.
 */
public interface PackRegistry
{
    /** Le versioni pubblicate di {@code name}, in ordine qualunque; vuota se il pack non esiste. */
    List<Version> availableVersions(String name);

    /**
     * La cartella locale che contiene il pack a quella versione, scaricandolo se serve.
     *
     * @throws java.io.IOException se non si riesce a ottenerlo o se l'integrità non torna
     */
    Path fetch(String name, Version version) throws java.io.IOException;

    /** Come si nomina questo registry in un messaggio di errore. */
    String describe();
}
