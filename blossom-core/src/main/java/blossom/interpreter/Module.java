package blossom.interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;


public record Module(String name, String code, boolean library)
{
    public Module
    {
        Objects.requireNonNull(name);
        Objects.requireNonNull(code);
    }

    public static Module fromPath(Path path)
    {
        boolean library = path.getFileName().toString().endsWith(".bsl");
        try
        {
            String name = path.getFileName().toString().replaceFirst("\\.bsl?", "").toLowerCase(Locale.ROOT);
            String code = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return new Module(name, code, library);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Failed to load blossom module", e);
        }
    }

    /**
     * Creates a new {@link Module} with an app located at a specified path inside some mod's JAR.
     *
     * @param path       A {@link String} being the path to the directory where the app is located.
     * @param scriptName A {@link String} being the name of the script. The extension will be autocompleted
     * @param isLibrary  A {@link boolean} indicating whether or not the script is a library
     * @return The created {@link BundledModule}
     * @see #fromJarPathWithCustomName(String, String, boolean)
     */
    public static Module fromJarPath(String path, String scriptName, boolean isLibrary)
    {
        return fromJarPathWithCustomName(path + scriptName + (isLibrary ? ".bsl" : ".bs"), scriptName, isLibrary);
    }

    /**
     * Creates a new {@link Module} with an app located at the specified fullPath (inside a mod jar)with a custom name.
     *
     * @param fullPath   A {@link String} being the full path to the app's code, including file and extension.
     * @param customName A {@link String} being the custom name for the script.
     * @param isLibrary  A {@link boolean} indicating whether or not the script is a library
     * @return The created {@link Module}
     * @see #fromJarPath(String, String, boolean)
     */
    public static Module fromJarPathWithCustomName(String fullPath, String customName, boolean isLibrary)
    {
        try
        {
            String name = customName.toLowerCase(Locale.ROOT);
            try (InputStream stream = Module.class.getClassLoader().getResourceAsStream(fullPath))
            {
                if (stream == null)
                {
                    throw new IOException("no such resource: " + fullPath);
                }
                String code = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new Module(name, code, isLibrary);
            }
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("Failed to load bundled module", e);
        }
    }

}
