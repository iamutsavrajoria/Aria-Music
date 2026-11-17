# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-image-loader.sh"
BASE = "app/src/main/kotlin/app/aria/music"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def test_image_package_is_exempt(tmp_path):
    put(tmp_path, f"{BASE}/image/AriaImage.kt", "AsyncImage(model = m)\nSingletonImageLoader.get(ctx)\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_canary_file_is_no_longer_exempt(tmp_path):
    put(tmp_path, f"{BASE}/Canary.kt", "AsyncImage(model = m)\n")
    code, out = run(tmp_path)
    assert code == 1 and "Canary.kt" in out, out


def test_banned_apis_outside_image_package_fail(tmp_path):
    for i, text in enumerate([
        "val l = SingletonImageLoader.get(ctx)", "val l = context.imageLoader", "AsyncImage(model = m)",
        "SubcomposeAsyncImage(model = m)", "val p = rememberAsyncImagePainter(m)",
    ]):
        put(tmp_path, f"{BASE}/ui/home/S{i}.kt", text + "\n")
        code, out = run(tmp_path)
        assert code == 1 and f"S{i}.kt" in out, (text, out)
        (tmp_path / f"{BASE}/ui/home/S{i}.kt").unlink()


def test_other_modules_and_lookalike_paths_are_not_exempt(tmp_path):
    put(tmp_path, "playback/src/main/kotlin/x/Image.kt", "AsyncImage(model = m)\n")
    code, out = run(tmp_path)
    assert code == 1, out
    (tmp_path / "playback/src/main/kotlin/x/Image.kt").unlink()
    put(tmp_path, f"{BASE}/ui/image/Fake.kt", "AsyncImage(model = m)\n")
    code, out = run(tmp_path)
    assert code == 1 and "Fake.kt" in out, out


def test_aria_image_and_plain_mentions_pass(tmp_path):
    put(tmp_path, f"{BASE}/ui/home/Home.kt", "AriaImage(url, null)\nimport coil3.compose.AsyncImage\nval x = loader.execute(req)\n")
    code, out = run(tmp_path)
    assert code == 0, out
