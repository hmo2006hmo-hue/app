CREATE TABLE quran_ayahs (
    id INTEGER PRIMARY KEY,
    surah INTEGER NOT NULL,
    surah_name TEXT NOT NULL,
    ayah INTEGER NOT NULL,
    text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    page INTEGER NOT NULL,
    juz INTEGER NOT NULL
);

CREATE INDEX idx_quran_ayahs_surah_ayah ON quran_ayahs(surah, ayah);
CREATE INDEX idx_quran_ayahs_page ON quran_ayahs(page);

CREATE VIRTUAL TABLE quran_fts USING fts5(
    text,
    normalized_text,
    surah_name,
    content='quran_ayahs',
    content_rowid='id'
);
