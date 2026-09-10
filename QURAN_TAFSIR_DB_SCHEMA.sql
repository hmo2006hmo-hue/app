-- Stage 20: SQLite / FTS5 Tafsir database (size-optimized)
CREATE TABLE metadata (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
);

CREATE TABLE tafsir_ayahs (
    id INTEGER PRIMARY KEY,
    surah_number INTEGER NOT NULL,
    surah_name TEXT NOT NULL,
    ayah_number INTEGER NOT NULL,
    type TEXT NOT NULL,
    author TEXT NOT NULL,
    text TEXT NOT NULL,
    UNIQUE(surah_number, ayah_number)
);

CREATE INDEX idx_tafsir_ayah_lookup
ON tafsir_ayahs(surah_number, ayah_number);

-- External-content FTS5 keeps only the search index here; source text is read
-- from tafsir_ayahs, avoiding a second stored copy of the tafsir text.
CREATE VIRTUAL TABLE tafsir_fts USING fts5(
    text,
    content='tafsir_ayahs',
    content_rowid='id',
    tokenize='unicode61 remove_diacritics 2'
);
