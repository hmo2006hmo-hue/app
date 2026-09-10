# Book catalog verification

The clean project catalog contains exactly 290 SQLite books.

Each entry points to:
`https://github.com/hmo2006hmo-hue/books/releases/download/books-v1/book_N.db`

The size and SHA-256 metadata were checked against the supplied `books.zip` before rebuilding `app/src/main/assets/library.db`.
The source archive contained exactly `book_1.db` through `book_290.db`; macOS metadata files were excluded.

The application treats the catalog as metadata only. No book database is bundled in the APK assets.
