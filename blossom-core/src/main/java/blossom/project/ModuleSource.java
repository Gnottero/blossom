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
 * Il preambolo di un modulo, separato dal corpo.
 *
 * <p>{@code module ...;} e {@code import ... as ...;} sono dichiarazioni, non espressioni: vanno
 * lette prima di compilare qualunque cosa, perché il resolver deve sapere quali moduli servono
 * ancora prima di costruire l'albero del corpo (§3.2 e §4 del piano: gli errori di import sono
 * errori di caricamento, non sorprese a runtime).
 *
 * <p>Il corpo restituito ha la stessa lunghezza del sorgente originale - il preambolo viene
 * sostituito con spazi, conservando le andate a capo - così riga e colonna di ogni errore del corpo
 * restano quelle vere del file, senza dover riportare offset in giro.
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

    /** Un import risolto staticamente: l'alias con cui il modulo corrente vede il modulo importato. */
    public record ImportDeclaration(String alias, ModuleName target, SourcePosition position)
    {
    }

    public @Nullable ModuleName declaredName()
    {
        return declaredName;
    }

    /** Gli import nell'ordine in cui compaiono, per alias. */
    public Map<String, ImportDeclaration> imports()
    {
        return imports;
    }

    /**
     * Il corpo del modulo, cioè quello che resta tolto il preambolo.
     *
     * <p>Un modulo che dichiara solo il proprio nome e i propri import è legittimo - è la facciata
     * che raccoglie e riespone altri moduli - e il suo corpo è vuoto. Al parser però un'espressione
     * vuota non si può dare, quindi diventa {@code null}: valutarlo non fa niente, che è appunto
     * quello che un modulo senza corpo deve fare.
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
     * Legge il preambolo di {@code code}.
     *
     * @param fileName il nome del file, per i messaggi di errore
     */
    public static ModuleSource parse(String fileName, String code)
    {
        Scanner scanner = new Scanner(fileName, code);
        ModuleName declared = scanner.readModuleDeclaration();
        Map<String, ImportDeclaration> imports = scanner.readImports();
        return new ModuleSource(declared, imports, scanner.blankOutPreamble());
    }

    /**
     * Lettore di dichiarazioni: minuscolo di proposito, perché la grammatica del preambolo è
     * minuscola. Salta spazi e commenti come il tokenizer del linguaggio.
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

        /** Sostituisce il preambolo con spazi, lasciando le andate a capo dove sono. */
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

        // --- primitive di lettura ---

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
                return false; // e' l'inizio di un identificatore piu' lungo, non la parola chiave
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
                throw new ParseError(here(), "manca " + what);
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
                throw new ParseError(here(), "manca " + what);
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
            advance(); // la virgoletta di chiusura
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

    /** Gli alias dichiarati, utile nei messaggi di errore con suggerimento. */
    public List<String> aliases()
    {
        return new ArrayList<>(imports.keySet());
    }
}
