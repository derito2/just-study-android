# tools/

Python tools that read the web project (`/Users/dero/Documents/visual/korean-study-assistant`,
**read-only**) with its venv. Plan: `docs/POC_PLAN.md`, Task 0.

```bash
WEB=/Users/dero/Documents/visual/korean-study-assistant
cd $WEB/backend
export PYTHONDONTWRITEBYTECODE=1   # no .pyc inside the web project
```

## Golden files → `golden/`

```bash
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/golden/export_golden.py \
  --out /Users/dero/Documents/JustStudyApp/golden
```

- Inputs: the 5 PDFs in `backend/tests/fixtures/notes_libreoffice/` and
  `docs/templates/Korean_Notes_Example.pdf`. `--pdf <file>` replaces them.
- `--private` uses your notes (`data/uploads` + `data/extracted`) and writes only to
  `golden/private/` (git-ignored). A path under `data/` without `--private` exits with code 2.
- Deterministic: the script re-runs itself with `PYTHONHASHSEED=0` (the web's self-check
  iterates sets). Two runs give the same `manifest.json`.
- Needs Node ≥ 22.6 for `compare_answer.mjs` (`--experimental-strip-types` on `compare.ts`).
- Cases with `"transform": "nfd"` are stored in NFC: apply NFD to the input before calling.
- `teacher/*.jsonl` keeps Python's question order; compare by question, not by line.

## Backup → `build/poc/juststudy-backup-<date>.jsbackup.zip`

Needs MySQL up (`$WEB/scripts/study.sh`). Read-only transaction; stops with code 3 if
MySQL's time zone is not UTC.

```bash
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/export/export_backup.py \
  --out /Users/dero/Documents/JustStudyApp/build/poc [--no-pdfs]
```

## Database golden files → `golden/db/`

Synthetic SQLite with the web's models (the test PDFs, no personal data), filled through the web's own
services at fixed times in `America/Mexico_City`; then the read services with a fixed `now`. Writes
`base.jsbackup.zip` (after processing), `ops.json` (every later write), `fixture.jsbackup.zip` (the result) and
one JSON per read function. Deterministic (`tools/tests/test_export_db_golden.py`).

```bash
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/golden/export_db_golden.py
```

## Synthetic backup → `core/data/src/androidTest/assets/backup/synthetic.jsbackup.zip`

No MySQL and no personal data: every table with 2 made-up rows (in-memory SQLite with the web's models).
`BackupTest` imports it on the phone.

```bash
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/export/synthetic_backup.py \
  --out /Users/dero/Documents/JustStudyApp/core/data/src/androidTest/assets/backup
```

## Room v1 entities → `core/data/.../data/{db,backup}`

`Entities.kt`, `Daos.kt`, `JustStudyDatabase.kt` and `backup/BackupSchema.kt`, from the web's `models.py`:

```bash
PYTHONPATH=. .venv/bin/python /Users/dero/Documents/JustStudyApp/tools/data/gen_room_entities.py
```

## Checks

```bash
cd /Users/dero/Documents/JustStudyApp
PYTHONPATH=$WEB/backend $WEB/backend/.venv/bin/python -m pytest tools/tests -q -p no:cacheprovider
$WEB/backend/.venv/bin/ruff check tools && $WEB/backend/.venv/bin/ruff format --check tools
```

## CI (`.github/workflows/ci.yml`) in local

The same as GitHub Actions on every PR and push to `main` (stop the web, Colima and Ollama first):

```bash
./gradlew test verifyRoborazziDebug lint assembleDebug assembleRelease
python3 tools/ci/check_manifest.py      # debug and release merged manifests; exit 1 on a forbidden permission,
                                        # an exported component other than MainActivity, or a debuggable release
python3 -m pytest -q tools/tests/test_check_manifest.py
```

After a deliberate UI change: `./gradlew :app:recordRoborazziDebug` and commit the new `app/src/test/screenshots/`.

