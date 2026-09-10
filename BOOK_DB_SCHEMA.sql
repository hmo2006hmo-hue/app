CREATE TABLE chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    source_name TEXT NOT NULL
);

CREATE TABLE hadith (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    sayer TEXT,
    source TEXT NOT NULL,
    chapter_id INTEGER,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id)
);

-- The 290 supplied book databases use the schema above. The app reads the
-- original schema without converting the books to another database format.
