package blossom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import blossom.exception.ImportError;
import blossom.exception.ParseError;
import blossom.exception.UndefinedSymbolError;
import blossom.interpreter.Interpreter;
import blossom.project.ProjectLoader;

/**
 * The module system: one file is one module, imports carry a mandatory alias, and what is not
 * {@code pub} does not leave the module.
 *
 * <p>The tests build real projects on disk, manifest included: that is how the system will be used,
 * and it also exercises the match between declared name and file path.
 */
class ModuleSystemTest
{
    private static Path project(Path root, String manifest) throws IOException
    {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("blossom.json"), manifest);
        return root;
    }

    private static void module(Path root, String relativePath, String code) throws IOException
    {
        Path file = root.resolve("src").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, code);
    }

    private static final String SIMPLE_MANIFEST = """
            {
              "name": "test-project",
              "version": "0.1.0",
              "entrypoint": "src/main.bs"
            }
            """;

    // --- the case that has to work ---

    @Test
    void loadsAMultiModuleProject(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "math/vector.bs", """
                module math.vector;

                pub fn add(a, b) -> [a:0 + b:0, a:1 + b:1];
                pub fn length_squared(v) -> v:0 * v:0 + v:1 * v:1;

                fn helper_internal(x) -> x * 2;
                """);
        module(root, "counters.bs", """
                module counters;

                let mut count = 0;

                pub fn increment() -> count += 1;
                pub fn value() -> count;
                """);
        module(root, "main.bs", """
                module main;

                import "math/vector" as vec;
                import "counters" as counters;

                counters.increment();
                counters.increment();
                pub let result = [vec.add([1, 2], [10, 20]), counters.value()];
                """);

        Interpreter interpreter = Interpreter.forProject(root);
        assertEquals("[[11, 22], 2]", interpreter.eval("result").getString());
    }

    @Test
    void resolvesFunctionsPassedBetweenModules(@TempDir Path tmp) throws IOException
    {
        // a function passed as a value to another module has to keep resolving wherever it is
        // called: it is the FunctionValue that travels, not its name
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "runner.bs", """
                module runner;

                pub fn run_twice(f, x) -> call(f, call(f, x));
                """);
        module(root, "main.bs", """
                module main;

                import "runner" as runner;

                fn triple(x) -> x * 3;
                pub let answer = runner.run_twice(triple, 2);
                """);

        Interpreter interpreter = Interpreter.forProject(root);
        assertEquals("18", interpreter.eval("answer").getString());
    }

    // --- what has to fail, and fail well ---

    @Test
    void refusesAccessToPrivateSymbols(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "lib.bs", """
                module lib;

                fn secret(x) -> x;
                pub fn public_one(x) -> x;
                """);
        module(root, "main.bs", """
                module main;

                import "lib" as lib;

                pub let value = lib.secret(1);
                """);

        ImportError error = assertThrows(ImportError.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("secret"), error.getMessage());
        assertTrue(error.getMessage().contains("pub"), "the error must say how to export: " + error.getMessage());
    }

    @Test
    void suggestsTheRightSymbolName(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "math/vector.bs", """
                module math.vector;

                pub fn add(a, b) -> a + b;
                """);
        module(root, "main.bs", """
                module main;

                import "math/vector" as vec;

                pub let value = vec.ad(1, 2);
                """);

        UndefinedSymbolError error = assertThrows(UndefinedSymbolError.class, () -> Interpreter.forProject(root));
        assertEquals("add", error.suggestion());
        assertTrue(error.format().contains("Did you mean: add?"), error.format());
    }

    @Test
    void modulesCanImportEachOther(@TempDir Path tmp) throws IOException
    {
        // two modules calling each other are a normal case as soon as a project grows, and as long
        // as the calls sit inside functions there is no ordering problem
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "a.bs", """
                module a;

                import "b" as b;
                pub fn from_a() -> 1;
                pub fn both() -> from_a() + b.from_b();
                """);
        module(root, "b.bs", """
                module b;

                import "a" as a;
                pub fn from_b() -> 2;
                pub fn doubled() -> a.from_a() * 2;
                """);
        module(root, "main.bs", """
                module main;

                import "a" as a;
                import "b" as b;
                pub let value = a.both() + b.doubled();
                """);

        Interpreter interpreter = Interpreter.forProject(root);
        assertEquals("3", interpreter.eval("import \"a\" as a; a.both()").getString());
        assertEquals("5", interpreter.eval("value").getString());
        assertEquals("2", interpreter.eval("import \"b\" as b; b.doubled()").getString());
    }

    @Test
    void runsEveryModuleAfterTheOnesItImports(@TempDir Path tmp) throws IOException
    {
        // the body of a module may call a function of a module it imports: that function exists
        // only after the other body has run, so execution order is a contract. Many modules, with
        // names that an unordered map would shuffle, to notice if the order gets lost on the way
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "shared/registry.bs", """
                module shared.registry;

                let mut declared = 0;
                pub fn declare(name) -> (declared += 1; name);
                pub fn count() -> declared;
                """);
        StringBuilder mainSource = new StringBuilder("module main;\n\n");
        for (int i = 0; i < 12; i++)
        {
            String name = "machines/unit_" + i;
            module(root, name + ".bs", """
                    module machines.unit_%d;

                    import "shared/registry" as registry;

                    pub let id = registry.declare('unit_%d');
                    """.formatted(i, i));
            mainSource.append("import \"").append(name).append("\" as unit_").append(i).append(";\n");
        }
        mainSource.append("\nimport \"shared/registry\" as registry;\npub let value = registry.count();\n");
        module(root, "main.bs", mainSource.toString());

        // the order the loader declares: a module appears after everything it imports
        ProjectLoader.LoadedProject loaded = ProjectLoader.load(root);
        List<String> order = loaded.inDependencyOrder().stream().map(unit -> unit.name().value()).toList();
        assertEquals(0, order.indexOf("shared.registry"), order.toString());
        assertEquals(order.size() - 1, order.indexOf("main"), order.toString());

        Interpreter interpreter = Interpreter.forProject(root);
        assertEquals("12", interpreter.eval("value").getString());
    }

    @Test
    void readingACycleMateWhileItIsStillLoadingSaysWhy(@TempDir Path tmp) throws IOException
    {
        // here order really matters: one of the two has to run first, and at that point the
        // other's binding does not exist yet. The error has to say it is a cycle, not that the
        // symbol does not exist
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "a.bs", """
                module a;

                import "b" as b;
                pub let from_a = b.from_b + 1;
                """);
        module(root, "b.bs", """
                module b;

                import "a" as a;
                pub let from_b = a.from_a + 1;
                """);
        module(root, "main.bs", """
                module main;

                import "a" as a;
                pub let value = a.from_a;
                """);

        ImportError error = assertThrows(ImportError.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("has not finished loading yet"), error.getMessage());
        assertTrue(error.getMessage().contains("circular imports"), error.getMessage());
    }

    @Test
    void requiresTheDeclaredNameToMatchThePath(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "main.bs", """
                module something_else;

                pub let value = 1;
                """);

        ParseError error = assertThrows(ParseError.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("something_else"), error.getMessage());
        assertTrue(error.getMessage().contains("main"), error.getMessage());
    }

    @Test
    void requiresAnAliasOnEveryImport(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "lib.bs", "module lib;\n\npub fn f() -> 1;\n");
        module(root, "main.bs", """
                module main;

                import "lib";

                pub let value = 1;
                """);

        ParseError error = assertThrows(ParseError.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("alias"), error.getMessage());
    }

    @Test
    void reportsMissingModules(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "main.bs", """
                module main;

                import "math/vector" as vec;
                pub let value = 1;
                """);

        ImportError error = assertThrows(ImportError.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("math.vector"), error.getMessage());
    }

    @Test
    void refusesToReassignAnImmutableBinding(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        module(root, "main.bs", """
                module main;

                let fixed = 1;
                fn change() -> fixed = 2;
                pub let value = change();
                """);

        RuntimeException error = assertThrows(RuntimeException.class, () -> Interpreter.forProject(root));
        assertTrue(error.getMessage().contains("let mut"), error.getMessage());
    }

    // --- the manifest ---

    @Test
    void readsAPathLocalDependency(@TempDir Path tmp) throws IOException
    {
        Path shared = project(tmp.resolve("shared-utils"), """
                {
                  "name": "shared-utils",
                  "entrypoint": "src/main.bs"
                }
                """);
        module(shared, "text.bs", """
                module text;

                pub fn shout(s) -> s + '!';
                """);
        module(shared, "main.bs", "module main;\n");

        Path root = project(tmp.resolve("app"), """
                {
                  "name": "app",
                  "entrypoint": "src/main.bs",
                  "dependencies": {
                    "shared_utils": { "path": "../shared-utils" }
                  }
                }
                """);
        module(root, "main.bs", """
                module main;

                import "shared_utils/text" as text;

                pub let value = text.shout('hello');
                """);

        Interpreter interpreter = Interpreter.forProject(root);
        assertEquals("hello!", interpreter.eval("value").getString());
    }

    // --- autoload: who starts on their own when the world opens ---

    @Test
    void doesNotAutoloadUnlessDeclared(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, SIMPLE_MANIFEST);
        assertTrue(!blossom.project.ProjectManifest.read(root).autoload());
    }

    @Test
    void readsAutoload(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, """
                {
                  "name": "guardiana",
                  "entrypoint": "src/main.bs",
                  "autoload": true
                }
                """);
        assertTrue(blossom.project.ProjectManifest.read(root).autoload());
    }

    @Test
    void rejectsANonBooleanAutoload(@TempDir Path tmp) throws IOException
    {
        Path root = project(tmp, """
                {
                  "name": "guardiana",
                  "entrypoint": "src/main.bs",
                  "autoload": "yes"
                }
                """);
        ParseError error = assertThrows(ParseError.class, () -> blossom.project.ProjectManifest.read(root));
        assertTrue(error.getMessage().contains("'autoload' must be true or false"), error.getMessage());
    }
}
