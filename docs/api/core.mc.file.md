# `core.mc.file`

The data of the app on disk.

```
import "core.mc.file" as file;

file.write_file('scores', 'json', {'ada' -> 12});
file.read_file('scores', 'json');
```

Every app writes under `<world>/blossom/<app>.data/`: two apps do not see each other's data by
accident. A file marked as shared ends up in `<world>/blossom/shared/` instead, and that is the
intended way to pass data between apps.

These functions need a loaded app: from `/blossom script run` they do not work, and they say so.

### `read_file(name, type)`
Reads a file. The types are `'text'` (a list of lines), `'json'`, `'nbt'`, `'raw'`, `'shared_text'`,
`'shared_json'`, `'shared_nbt'`, `'shared_raw'`.

### `write_file(name, type, data)`
Writes, or appends when the type is textual.

### `delete_file(name, type)` · `list_files(folder, type)`
Deletes a file, and lists the ones in a folder.

### `load_app_data()` · `load_app_data(name)` · `store_app_data(data)` · `store_app_data(data, name)`
The state of the app in NBT, saved and read back without thinking about paths. Without a name it is
the default state file of the app.

### `create_datapack(name, content)`
Creates a datapack from the given content (a map of paths -> files) and enables it.
