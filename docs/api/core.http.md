# `core.http`

HTTP requests towards hosts an administrator declared.

```
import "core.http" as http;
import "core.task" as task;

fn fetch_motd() -> (
    response = http.get('https://api.example.com/motd');
    if (response:'status' == 200, response:'body', null)
);

// a request never runs on the server thread: it goes in a task
handle = task.start(fetch_motd);
motd = task.wait(handle);
```

## The boundaries

Nothing is reachable until it is declared in `config/blossomscript.json`:

```json
{
  "http": {
    "hosts": ["api.example.com", "*.status.example.org"],
    "schemes": ["https"],
    "timeout_ms": 5000,
    "max_response_bytes": 1048576,
    "follow_redirects": false
  }
}
```

- **without a declaration nothing is reachable.** `core.http` imported on a server that configured
  nothing exists but opens nothing, and says so.
- **a host is exact or one leading wildcard.** `api.example.com` matches that name only;
  `*.example.com` matches its subdomains and not `example.com` itself. `*` alone opens everything —
  it is logged as a warning at startup, because it turns every installed app into a channel out of
  the server.
- **`https` unless stated otherwise.** Allowing plain `http` is possible and logged: those requests
  travel unencrypted and can be read and altered in transit.
- **redirects are not followed by default.** With `follow_redirects` on they are followed at most
  five hops, and every hop is checked against the allowlist again — a `302` cannot walk a script out
  of the declared hosts.

## Off the server thread

A request blocks until the other side answers, and the watchdog cannot interrupt a socket read. A
call made on the server thread is refused with an error that says where to move it: inside
[`task`](core.task.md), where the waiting costs no player anything.

The refusal only happens in game. In a plain `blossom-core` test there is no server thread and the
call runs where it is written.

## The response

Every request gives back a map:

| Key | What it holds |
| --- | --- |
| `status` | the HTTP status code, as a number |
| `body` | the body, decoded as UTF-8 |
| `headers` | a map of header name (lowercase) to value, multiple values joined with `, ` |
| `url` | the url that actually answered, which differs from the one asked after a redirect |

A status outside 2xx is **not** an error: it is an answer, and the script decides what to do with
it. The errors are the things that produce no answer at all — host unreachable, timeout, a body
larger than the configured limit — and they arrive as `http_exception`:

```
try(
    http.get('https://api.example.com/slow'),
    'http_exception', print('the api did not answer: ' + _)
);
```

A refused host, scheme or method is not an `http_exception`: it is a program error, and it is not
catchable. A `try` written for a slow server must not end up hiding a missing configuration.

## The functions

### `hosts()`

The host patterns this server allows, sorted as they were declared. Empty means the network is
closed; a script that wants to be polite can check before failing.

```
if (length(http.hosts()) == 0, print('no network on this server'));
```

### `get(url)`, `get(url, headers)`

A `GET`. `headers` is a map of name to value.

```
http.get('https://api.example.com/players');
http.get('https://api.example.com/players', {'authorization' -> 'Bearer ' + token});
```

### `post(url, body)`, `post(url, body, headers)`

A `POST` with `body` sent as UTF-8.

```
http.post('https://api.example.com/events', 'player=steve', {'content-type' -> 'text/plain'});
```

### `request(method, url)`, `request(method, url, body)`, `request(method, url, body, headers)`

Any of `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, in any case. `body` may be `null`.

```
http.request('delete', 'https://api.example.com/session/' + id);
```

### `get_json(url)`, `get_json(url, headers)`

A `GET` with `accept: application/json`, giving back the body already decoded into values instead of
the response map. A body that is not valid json throws `json_error`.

```
players = http.get_json('https://api.example.com/players');
print(players:0:'name');
```

### `post_json(url, value)`, `post_json(url, value, headers)`

Encodes `value` as json, sends it with `content-type: application/json`, and decodes the answer the
same way `get_json` does.

```
http.post_json('https://api.example.com/events', {'kind' -> 'joined', 'player' -> 'steve'});
```

### `encode_url(text)`, `decode_url(text)`

Percent-encoding of a single piece of a url. `decode_url` throws `value_exception` on a malformed
sequence.

```
http.encode_url('due parole');   // 'due+parole'
```

### `encode_query(parameters)`

A map turned into a query string, keys and values encoded, sorted by pair so the same map always
gives the same string.

```
'https://api.example.com/search?' + http.encode_query({'q' -> 'due parole', 'page' -> 2});
// https://api.example.com/search?page=2&q=due+parole
```

## What is not here

- **no streaming and no downloads to disk.** The body is read into memory up to
  `max_response_bytes`, and the limit is enforced by reading, not by trusting the `content-length`
  the other side wrote.
- **no client certificates, no proxies, no cookie jar.** A script that needs a session sends the
  header it needs; there is no state kept between calls.
- **no server.** Blossom makes requests, it does not answer them: a port open on a game server is a
  decision that belongs to whoever runs it, not to an app installed on it.
