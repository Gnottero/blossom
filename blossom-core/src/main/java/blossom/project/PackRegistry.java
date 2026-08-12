package blossom.project;

import java.nio.file.Path;
import java.util.List;

/**
 * Da dove si prendono i pack che non stanno già su disco.
 *
 * <p>È un'interfaccia e non una classe perché il core non deve sapere se dall'altra parte c'è una
 * cartella, un server http o niente: chi incorpora il core decide, e in un test decide di essere una
 * cartella. Il registry è l'unico punto del sistema che può toccare la rete, e per questo è anche
 * l'unico che si può spegnere del tutto ({@link PackEnvironment#registry()} può essere {@code null}).
 *
 * <h2>Cosa deve garantire un'implementazione</h2>
 *
 * <ul>
 *   <li>{@link #availableVersions} non lancia se il pack non esiste: restituisce una lista vuota, e
 *       la differenza fra "non c'è" e "non c'è una versione compatibile" la racconta chi chiama, che
 *       conosce il vincolo;</li>
 *   <li>{@link #fetch} restituisce una cartella <em>già completa</em>: chi la riceve la legge come
 *       un qualunque progetto su disco, senza sapere se è stata scaricata adesso o presa da cache;
 *   <li>quello che scarica va verificato prima di essere restituito. Un archivio scaricato e
 *       scompattato senza controllarne l'impronta è codice arbitrario che gira su un server, e
 *       "tanto è il nostro registry" non è una garanzia di integrità.</li>
 * </ul>
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
