# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-screen-imports.sh"
BASE = "app/src/main/kotlin/app/aria/music"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def test_di_may_import_data_and_innertube(tmp_path):
    put(tmp_path, f"{BASE}/di/M.kt", "import app.aria.data.AriaDatabase\nimport app.aria.innertube.YouTube\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_screens_may_not_import_data_or_innertube(tmp_path):
    for imp in ["import app.aria.data.LibraryDao", "import app.aria.innertube.models.SongItem"]:
        put(tmp_path, f"{BASE}/ui/home/HomeScreen.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "HomeScreen.kt" in out, (imp, out)


def test_root_package_files_may_not_import_them_either(tmp_path):
    put(tmp_path, f"{BASE}/Foo.kt", "import app.aria.data.AriaDatabase\n")
    code, out = run(tmp_path)
    assert code == 1 and "Foo.kt" in out, out


def test_ui_may_not_touch_the_engine_directly(tmp_path):
    for imp in ["import app.aria.playback.DefaultPlaybackEngine", "import app.aria.domain.PlaybackEngine"]:
        put(tmp_path, f"{BASE}/ui/nowplaying/X.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "X.kt" in out, (imp, out)


def test_domain_models_and_player_controller_are_fine_in_ui(tmp_path):
    put(tmp_path, f"{BASE}/ui/nowplaying/X.kt", "import app.aria.domain.Track\nimport app.aria.domain.PlaybackState\nimport app.aria.music.player.PlayerController\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out


DATA_EVASIONS = [
    "import app.aria.data.LibraryDao as Dao",
    "import app.aria.data.LibraryDao;",
    "import app.aria.data.LibraryDao \t",
    "import app.aria.data.LibraryDao\r",
    "    import app.aria.data.LibraryDao",
    "\timport app.aria.innertube.YouTube",
    "import app.aria.data.*",
    "import app.aria.innertube.*;",
    "val d = app.aria.data.AriaDatabase.get()",
    "val y: app.aria.innertube.YouTube = x",
]

ENGINE_EVASIONS = [
    "import app.aria.domain.PlaybackEngine as E",
    "import app.aria.domain.PlaybackEngine;",
    "import app.aria.domain.PlaybackEngine \t",
    "import app.aria.domain.PlaybackEngine\r",
    "   import app.aria.domain.PlaybackEngine",
    "import app.aria.domain.*",
    "import app.aria.playback.*",
    "import app.aria.playback.DefaultPlaybackEngine as P",
    "val e: app.aria.domain.PlaybackEngine = x",
    "val e = app.aria.playback.DefaultPlaybackEngine()",
]


def test_data_and_innertube_evasions_are_flagged_in_screens(tmp_path):
    for imp in DATA_EVASIONS:
        put(tmp_path, f"{BASE}/ui/home/HomeScreen.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "HomeScreen.kt" in out, (imp, out)


def test_data_and_innertube_evasions_are_flagged_outside_ui_too(tmp_path):
    for imp in DATA_EVASIONS:
        put(tmp_path, f"{BASE}/Foo.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "Foo.kt" in out, (imp, out)


def test_engine_evasions_are_flagged_in_ui(tmp_path):
    for imp in ENGINE_EVASIONS:
        put(tmp_path, f"{BASE}/ui/nowplaying/X.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "X.kt" in out, (imp, out)


def test_di_may_use_indented_aliased_and_qualified_forms(tmp_path):
    put(tmp_path, f"{BASE}/di/M.kt", "  import app.aria.data.AriaDatabase as Db\nval y = app.aria.innertube.YouTube\r\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_player_package_may_use_the_engine(tmp_path):
    put(tmp_path, f"{BASE}/player/PlayerController.kt", "import app.aria.domain.PlaybackEngine\nimport app.aria.playback.DefaultPlaybackEngine\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_similar_names_are_not_flagged(tmp_path):
    put(tmp_path, f"{BASE}/ui/X.kt", "import app.aria.database.Foo\nimport app.aria.domain.PlaybackEngineState\nimport app.aria.domain.Track\n")
    code, out = run(tmp_path)
    assert code == 0, out
