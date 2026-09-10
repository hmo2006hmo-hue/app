-- QuranStudy target data schema — Stage 3 specification only.
-- This file is documentation for the upcoming migration stages.

CREATE TABLE IF NOT EXISTS books (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT,
    description TEXT,
    db_file TEXT NOT NULL UNIQUE,
    cover TEXT,
    size_bytes INTEGER,
    chapters_count INTEGER,
    items_count INTEGER,
    download_url TEXT,
    sha256 TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    is_downloaded INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tafsirs (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    author TEXT,
    db_file TEXT NOT NULL UNIQUE,
    size_bytes INTEGER,
    download_url TEXT,
    sha256 TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    is_downloaded INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0
);

-- Template for each individual book database:
CREATE TABLE IF NOT EXISTS chapters (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items (
    id INTEGER PRIMARY KEY,
    chapter_id INTEGER,
    item_number INTEGER,
    title TEXT,
    content TEXT NOT NULL,
    author TEXT,
    source TEXT,
    page INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(chapter_id) REFERENCES chapters(id)
);

CREATE INDEX IF NOT EXISTS idx_items_chapter ON items(chapter_id);
CREATE INDEX IF NOT EXISTS idx_items_number ON items(item_number);
CREATE INDEX IF NOT EXISTS idx_items_sort ON items(sort_order);

CREATE VIRTUAL TABLE IF NOT EXISTS items_fts
USING fts5(
    title,
    content,
    author,
    source,
    content='items',
    content_rowid='id'
);

-- Template for each individual tafsir database:
CREATE TABLE IF NOT EXISTS tafsir (
    id INTEGER PRIMARY KEY,
    surah_number INTEGER NOT NULL,
    ayah_number INTEGER NOT NULL,
    text TEXT NOT NULL,
    UNIQUE(surah_number, ayah_number)
);

CREATE INDEX IF NOT EXISTS idx_tafsir_ayah
ON tafsir(surah_number, ayah_number);
