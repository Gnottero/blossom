#!/usr/bin/env python3
"""Nessun nome vecchio sopravvive nella documentazione, negli esempi o nei dati dell'editor.

Con novanta rinomine, cercarli a occhio non funziona: questo script legge la tabella dal catalogo
stesso - StdLib per i moduli core.*, BlossomApi per i core.mc.* - e cerca i nomi che non esistono
piu'. Le tabelle di migrazione in fondo alle pagine sono l'unico posto dove un nome vecchio ci deve
stare, e vengono saltate.

    python3 tools/check-old-names.py
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent

# le sezioni che elencano apposta i nomi vecchi
SKIP_FROM = re.compile(r'^##+ .*(Coming from|Prima .*Ora)', re.M)


def catalog_pairs(source: str):
    """Le coppie (nome pubblico, implementazione) dichiarate in un catalogo."""
    pairs = []
    for body in re.findall(r'java\.util\.Map\.(?:ofEntries|of)\((.*?)\);', source, re.S):
        pairs += re.findall(r'"([^"]+)"\s*,\s*"([^"]+)"', body)
    return pairs


def plain_symbols(source: str):
    """I simboli dichiarati senza mappa: il prelude, e i moduli che non hanno rinominato niente."""
    names = set()
    for body in re.findall(r'Set\.of\((.*?)\);', source, re.S):
        names |= set(re.findall(r'"([^"]+)"', body))
    for body in re.findall(r'module\("[\w.]+",\s*\n?((?:\s*"[^"]+",?\s*)+)\);', source, re.S):
        names |= set(re.findall(r'"([^"]+)"', body))
    return names


def old_names():
    sources = [
        ROOT / 'blossom-core/src/main/java/blossom/language/StdLib.java',
        ROOT / 'blossom/src/main/java/com/gnottero/blossom/BlossomApi.java',
    ]
    public, implementation = set(), set()
    for path in sources:
        source = path.read_text(encoding='utf-8')
        for name, impl in catalog_pairs(source):
            public.add(name)
            implementation.add(impl)
        # il prelude e i moduli che non hanno rinominato niente si dichiarano ancora per elenco
        public |= plain_symbols(source)
    # un nome che era solo un'implementazione e non e' piu' un nome pubblico e' un nome vecchio
    return sorted(implementation - public)


def files():
    for folder in ('docs', 'examples', 'vscode-blossom/data'):
        for path in (ROOT / folder).rglob('*'):
            if path.name == 'api.json':
                continue    # generato da tools/generate-api.js: porta anche i nomi delle
                            # implementazioni, che non sono nomi vecchi ma dettagli interni
            if path.is_file() and path.suffix in ('.md', '.bs', '.bsl', '.json'):
                yield path
    yield ROOT / 'README.md'


def main():
    names = old_names()
    if not names:
        print('nessuna rinomina dichiarata nei cataloghi')
        return 0
    found = []
    for path in files():
        text = path.read_text(encoding='utf-8')
        match = SKIP_FROM.search(text)
        if match:
            text = text[:match.start()]
        for name in names:
            # solo dove il nome e' davvero un simbolo: una chiamata 'nome(' o un accesso
            # qualificato 'alias.nome'. Senza questo, parole come 'task' o 'print' verrebbero
            # trovate in ogni riga di prosa che le nomina
            pattern = rf'(?:(?<=\.)|(?<![\w.])){re.escape(name)}(?=\s*\()|(?<=\.){re.escape(name)}(?![\w])'
            for hit in re.finditer(pattern, text):
                # 'core.task' e' il nome di un modulo, non l'uso del simbolo 'task'
                before = text[max(0, hit.start() - 8):hit.start()]
                if before.endswith('core.') or before.endswith('mc.'):
                    continue
                line = text.count('\n', 0, hit.start()) + 1
                found.append(f'{path.relative_to(ROOT)}:{line}: {name}')
    if found:
        print(f'{len(found)} nomi vecchi ancora in giro:')
        for line in found:
            print('  ' + line)
        return 1
    print(f'nessuno dei {len(names)} nomi vecchi sopravvive')
    return 0


if __name__ == '__main__':
    sys.exit(main())
