# `core.string`

Strings: formatting, case, regular-expression search and replace, splitting, encodings.

```
import "core.string" as string;

string.upper('hello');                   // 'HELLO'
string.replace('a-b-c', '-', '+');       // 'a+b+c'
string.split('-', 'a-b-c');              // ['a', 'b', 'c']
```

Strings are written with single quotes; double quotes are interpolated strings
(`"hello ${name}"`), which are syntax and need no import. A string is **not indexable**: `'abc':0`
is `null`, not `'a'`. To work character by character, split it first.

---

## Building and formatting

### `string.str(value)` · `string.str(format, ...)` · `string.str(format, list)`
With one argument, returns the textual representation of the value, the same one `print` and `+`
use.

With more, it is a printf-style format:

| Specifier | Inserts |
| --- | --- |
| `%s` | any value, as text |
| `%d` | an integer |
| `%f`, `%.2f` | a decimal, optionally with a fixed number of digits |
| `%x`, `%o`, `%e` | hexadecimal, octal, scientific notation |
| `%%` | a literal percent sign |

When the second argument is a single list, its elements are used as the arguments, so
`string.str('%s-%s', ['a', 'b'])` and `string.str('%s-%s', 'a', 'b')` are the same call. A malformed
format is an error rather than a mangled string.

The numeric specifiers want a number: given anything else they say so, instead of writing a `0`
nobody asked for. A string that is written like a number still passes, the same way the arithmetic
operators accept it. Arguments left over at the end are an error too, rather than being dropped.

```
string.str(42);                             // '42'
string.str('%s has %d apples', 'ada', 3);   // 'ada has 3 apples'
string.str('%.2f', 1/3);                    // '0.33'
string.str('%s-%s', ['a', 'b']);            // 'a-b'
```

---

## Case

### `string.lower(s)` · `string.upper(s)`
Lower and upper case, converted in a locale-independent way, so a script behaves the same on a
server configured in any language.

### `string.title(s)`
Title case: the first letter of every word in upper case, the rest lower.

```
string.lower('HELLO');      // 'hello'
string.upper('hello');      // 'HELLO'
string.title('a red hat');  // 'A Red Hat'
```

---

## Searching and replacing

### `string.replace(s, pattern)` · `string.replace(s, pattern, replacement)` · `string.replace(s, pattern, replacement, count)`
Replaces what the **regular expression** `pattern` matches. With no replacement the matches are
deleted; with a `count`, only the first `count` matches are replaced. The replacement text may use
`$1`, `$2` … to refer to the pattern's capture groups. An invalid pattern is an error that names the
syntax problem.

```
string.replace('a-b-c', '-', '+');          // 'a+b+c'
string.replace('a-b-c', '-', '+', 1);       // 'a+b-c'
string.replace('a-b-c', '-');               // 'abc'
string.replace('ada:3', '(\\w+):(\\d+)', '$2 of $1');   // '3 of ada'
```

To ask **whether** a string matches, and to get the matched portion or the capture groups, there is
the `~` operator, which needs no import:

```
'hello world' ~ 'o w';              // 'o w'       the matched portion, null if absent
'v1.2' ~ '([0-9])\.([0-9])';        // ['1', '2']  the groups, when the pattern has any
```

### `string.split(s)` · `string.split(separator, s)`
Splits a string into a list. The separator is a **regular expression**; with no separator, the
string is split into single characters.

```
string.split('-', 'a-b-c');         // ['a', 'b', 'c']
string.split('\\s+', 'a  b   c');   // ['a', 'b', 'c']
string.split('abc');                // ['a', 'b', 'c']
```

The opposite operation, `join(separator, list)`, lives in [`core.list`](core.list.md), because its
subject is the list rather than the string.

---

## Encodings and hashing

### `string.encode_base64(s)` · `string.decode_base64(s)`
Standard base64, over the UTF-8 bytes of the string. Decoding something that is not valid base64
throws a `b64_error`, which `try` can filter:

```
string.encode_base64('hello');                      // 'aGVsbG8='
string.decode_base64('aGVsbG8=');                   // 'hello'
try(string.decode_base64(text), 'b64_error', null);
```

### `string.hash_code(v)`
An integer derived from the value's contents. Equal values give equal hashes, which makes it useful
for bucketing or for a quick "has this changed?" comparison inside a single run. It is **not** a
cryptographic digest and it is not guaranteed to stay the same across versions of the mod or of the
JVM, so do not persist it or compare it against a hash stored elsewhere.

---

## Coming from the old names

| Before | Now |
| --- | --- |
| `encode_b64(s)` | `encode_base64(s)` |
| `decode_b64(s)` | `decode_base64(s)` |
| `replace_first(s, p, w)` | `replace(s, p, w, 1)` (how many is a number, not a name) |
| `join(sep, l)` | it lives in [`core.list`](core.list.md) |
