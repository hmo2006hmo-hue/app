CREATE TABLE books (
    id INTEGER PRIMARY KEY,
    external_id TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    author TEXT,
    description TEXT,
    db_file TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'sqlite' CHECK(format IN ('sqlite','json')),
    cover TEXT,
    size_bytes INTEGER,
    chapters_count INTEGER,
    items_count INTEGER,
    download_url TEXT,
    sha256 TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    is_downloaded INTEGER NOT NULL DEFAULT 0 CHECK(is_downloaded IN (0,1)),
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE tafsirs (
    id INTEGER PRIMARY KEY,
    external_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    db_file TEXT NOT NULL,
    download_url TEXT,
    size_bytes INTEGER,
    is_downloaded INTEGER NOT NULL DEFAULT 0 CHECK(is_downloaded IN (0,1)),
    version INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0
);
