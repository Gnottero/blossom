package blossom.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import blossom.exception.ImportError;
import blossom.exception.ParseError;
import blossom.exception.SourcePosition;

/**
 * A module's preamble, separated from its body.
 *
 * <p>{@code module ...;} and {@code import ... as ...;} are declarations, not expressions: they must
 * be read before compiling anything, because the resolver needs to know which modules are needed
 * before the body's tree is even built: import errors are loading errors, not runtime surprises.
 *
 * <p>The returned body has the same length as the original source (the preamble is replaced with
 * spaces, keeping the line breaks), so line and column numbers in any error from the body stay the
 * true ones for the file, with no offsets to carry around.
 */
public final class ModuleSource
{
    private final @Nullable ModuleName declaredName;
    private final Map<String, ImportDeclaration> imports;
    private final String body;

    private ModuleSource(@Nullable ModuleName declaredName, Map<String, ImportDeclaration> imports, String body)
    {
        this.declaredName = declaredName;
        this.imports = imports;
        this.body = body;
    }

    /** A statically resolved import: the alias by which the current module sees the imported one. */
    public record ImportDeclaration(String alias, ModuleName target, SourcePosition position)
    {
    }

    public @Nullable ModuleName declaredName()
    {
        return declaredName;
    }

    /** The imports in the order they appear, keyed by alias. */
    public Map<String, ImportDeclaration> imports()
    {
        return imports;
    }

    /**
     * The module's body, i.e. what remains once the preamble is stripped.
     *
     * <p>A module that only declares its own name and imports is legitimate (it's a facade that
     * collects and re-exposes other modules), and its body is empty. The parser cannot be handed an
     * empty expression though, so it becomes {@code null}: evaluating it does nothing, which is
     * exactly what a bodyless module should do.
     */
    public String body()
    {
        return body.isBlank() ? "null" : body;
    }

    public List<ModuleName> importedModules()
    {
        return imports.values().stream().map(ImportDeclaration::target).toList();
    }

    /**
     * Reads the preamble of {@code code}.
     *
     * @param fileName the file's name, for error messages
     */
    public static ModuleSource parse(String fileName, String code)
    {
        Scanner scanner = new Scanner(fileName, code);
        ModuleName declared = scanner.readModuleDeclaration();
        Map<String, ImportDeclaration> imports = scanner.readImports();
        return new ModuleSource(declared, imports, scanner.blankOutPreamble());
    }

    /**
     * Reader for declarations: deliberately minimal, because the preamble's grammar is minimal too.
     * Skips whitespace and comments just like the language's tokenizer.
     */
    private static final class Scanner
    {
        private final String fileName;
        private final String code;
        private int position = 0;
        private int line = 1;
        private int column = 1;

        Scanner(String fileName, String code)
        {
            this.fileName = fileName;
            this.code = code;
        }

        @Nullable ModuleName readModuleDeclaration()
        {
            skipIrrelevant();
            if (!consumeKeyword("module"))
            {
                return null;
            }
            SourcePosition start = here();
            String name = readDottedName("the module name");
            expectSemicolon("module " + name);
            try
            {
                return new ModuleName(name);
            }
            catch (IllegalArgumentException e)
            {
                throw new ParseError(start, e.getMessage());
            }
        }

        Map<String, ImportDeclaration> readImports()
        {
            Map<String, ImportDeclaration> imports = new LinkedHashMap<>();
            while (true)
            {
                skipIrrelevant();
                SourcePosition start = here();
                if (!consumeKeyword("import"))
                {
                    return imports;
                }
                skipIrrelevant();
                String target = readImportTarget();
                skipIrrelevant();
                if (!consumeKeyword("as"))
                {
                    throw new ParseError(here(), "every import needs an alias: write import \"" + target
                            + "\" as <alias>;");
                }
                skipIrrelevant();
                String alias = readIdentifier("the import alias");
                expectSemicolon("import \"" + target + "\" as " + alias);
                ModuleName name;
                try
                {
                    name = ModuleName.ofImportTarget(target);
                }
                catch (IllegalArgumentException e)
                {
                    throw new ImportError(start, e.getMessage());
                }
                ImportDeclaration previous = imports.put(alias, new ImportDeclaration(alias, name, start));
                if (previous != null)
                {
                    throw new ImportError(start, "alias '" + alias + "' is already used for module '"
                            + previous.target() + "' in this file");
                }
            }
        }

        /** Replaces the preamble with spaces, leaving line breaks where they are. */
        String blankOutPreamble()
        {
            StringBuilder blanked = new StringBuilder(code.length());
            for (int i = 0; i < position; i++)
            {
                char c = code.charAt(i);
                blanked.append(c == '\n' || c == '\r' ? c : ' ');
            }
            blanked.append(code, position, code.length());
            return blanked.toString();
        }

        // --- reading primitives ---

        private void skipIrrelevant()
        {
            while (position < code.length())
            {
                char c = code.charAt(position);
                if (Character.isWhitespace(c))
                {
                    advance();
                }
                else if (c == '/' && position + 1 < code.length() && code.charAt(position + 1) == '/')
                {
                    while (position < code.length() && code.charAt(position) != '\n')
                    {
                        advance();
                    }
                }
                else
                {
                    return;
                }
            }
        }

        private boolean consumeKeyword(String keyword)
        {
            skipIrrelevant();
            if (!code.startsWith(keyword, position))
            {
                return false;
            }
            int after = position + keyword.length();
            if (after < code.length() && (Character.isLetterOrDigit(code.charAt(after)) || code.charAt(after) == '_'))
            {
                return false; // it's the start of a longer identifier, not the keyword
            }
            for (int i = 0; i < keyword.length(); i++)
            {
                advance();
            }
            return true;
        }

        private String readDottedName(String what)
        {
            skipIrrelevant();
            int start = position;
            while (position < code.length())
            {
                char c = code.charAt(position);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.')
                {
                    advance();
                }
                else
                {
                    break;
                }
            }
            if (start == position)
            {
                throw new ParseError(here(), "missing " + what);
            }
            return code.substring(start, position);
        }

        private String readIdentifier(String what)
        {
            skipIrrelevant();
            int start = position;
            while (position < code.length())
            {
                char c = code.charAt(position);
                if (Character.isLetterOrDigit(c) || c == '_')
                {
                    advance();
                }
                else
                {
                    break;
                }
            }
            if (start == position)
            {
                throw new ParseError(here(), "missing " + what);
            }
            return code.substring(start, position);
        }

        private String readImportTarget()
        {
            skipIrrelevant();
            char quote = position < code.length() ? code.charAt(position) : ' ';
            if (quote != '"' && quote != '\'')
            {
                throw new ParseError(here(), "the module to import must be quoted, e.g. import \"math/vector\" as vec;");
            }
            advance();
            int start = position;
            while (position < code.length() && code.charAt(position) != quote)
            {
                if (code.charAt(position) == '\n')
                {
                    throw new ParseError(here(), "unterminated quotes in the import");
                }
                advance();
            }
            if (position >= code.length())
            {
                throw new ParseError(here(), "unterminated quotes in the import");
            }
            String target = code.substring(start, position);
            advance(); // the closing quote
            return target;
        }

        private void expectSemicolon(String declaration)
        {
            skipIrrelevant();
            if (position >= code.length() || code.charAt(position) != ';')
            {
                throw new ParseError(here(), "missing ';' after " + declaration);
            }
            advance();
        }

        private void advance()
        {
            if (code.charAt(position) == '\n')
            {
                line++;
                column = 1;
            }
            else
            {
                column++;
            }
            position++;
        }

        private SourcePosition here()
        {
            return new SourcePosition(fileName, line, column);
        }
    }

    /** The declared aliases, useful in error messages with a suggestion. */
    public List<String> aliases()
    {
        return new ArrayList<>(imports.keySet());
    }
}
