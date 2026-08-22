package blossom.project;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * A dependency declared in {@code blossom.json}, before it is resolved to a directory.
 *
 * <p>Three ways of saying where it comes from, mutually exclusive (a conflict between them would
 * leave no way to know which one won):
 *
 * <pre>
 * "utils":  { "path": "../utils" }                    // a directory, usually next to the project
 * "torre":  { "pack": "torre", "version": "^1.2" }    // another pack already installed in the same world
 * "vector": { "version": "^2.0" }                     // from the registry configured on the server
 * </pre>
 *
 * <p>The version constraint applies to all three; on a path dependency it is optional, but if
 * present it is checked against the manifest found there.
 *
 * @param alias   the name the project calls it by, i.e. the key in {@code dependencies}
 * @param path    the directory, for path dependencies
 * @param pack    the installed pack's name, for pack dependencies
 * @param version the version constraint, {@link VersionRange#ANY} if not declared
 */
public record Dependency(String alias, Kind kind, @Nullable Path path, @Nullable String pack, VersionRange version)
{
    public enum Kind
    {
        /** a directory given directly */
        PATH,
        /** another pack installed in the same world */
        PACK,
        /** fetched from a registry */
        REGISTRY
    }

    public static Dependency ofPath(String alias, Path path, VersionRange version)
    {
        return new Dependency(alias, Kind.PATH, path, null, version);
    }

    public static Dependency ofPack(String alias, String pack, VersionRange version)
    {
        return new Dependency(alias, Kind.PACK, null, pack, version);
    }

    public static Dependency ofRegistry(String alias, String name, VersionRange version)
    {
        return new Dependency(alias, Kind.REGISTRY, null, name, version);
    }

    /** The name by which the dependency is known outside the project that uses it. */
    public String targetName()
    {
        return pack != null ? pack : alias;
    }

    /** How it reads in an error message. */
    public String describe()
    {
        return switch (kind)
        {
            case PATH -> "path " + path;
            case PACK -> "pack '" + pack + "' installed in this world" + (version.isAny() ? "" : " " + version);
            case REGISTRY -> "registry, '" + targetName() + "' " + version;
        };
    }
}
