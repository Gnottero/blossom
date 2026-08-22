package blossom.project;

import java.nio.file.Path;
import java.util.List;

/**
 * Where to fetch packs that aren't already on disk.
 *
 * <p>This is an interface because the core doesn't need to know whether a directory, an http
 * server, or nothing at all is on the other side: it's the only part of the system that may touch
 * the network, and also the only one that can be switched off entirely
 * ({@link PackEnvironment#registry()} can be {@code null}).
 *
 * <p>An implementation must guarantee three things: {@link #availableVersions} does not throw if
 * the pack doesn't exist, but returns an empty list; {@link #fetch} returns an already-complete
 * directory, which the caller reads like any other project; and whatever is downloaded must be
 * verified first, because an archive unpacked without checking is arbitrary code on a server.
 */
public interface PackRegistry
{
    /** The published versions of {@code name}, in no particular order; empty if the pack doesn't exist. */
    List<Version> availableVersions(String name);

    /**
     * The local directory holding the pack at that version, downloading it if needed.
     *
     * @throws java.io.IOException if it cannot be obtained or its integrity check fails
     */
    Path fetch(String name, Version version) throws java.io.IOException;

    /** How to name this registry in an error message. */
    String describe();
}
