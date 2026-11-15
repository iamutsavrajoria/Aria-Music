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
