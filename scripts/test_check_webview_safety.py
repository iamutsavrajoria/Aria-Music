# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-webview-safety.sh"
ALLOWED = "innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt"


def put(root: pathlib.Path, rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root: pathlib.Path):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def test_allowed_file_may_hold_everything_confined(tmp_path):
    put(tmp_path, ALLOWED, "webView.addJavascriptInterface(b, \"x\")\nval w = WebView(context)\nsettings.javaScriptEnabled = true\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_bridge_outside_allowed_file_fails(tmp_path):
    put(tmp_path, ALLOWED, "webView.addJavascriptInterface(b, \"x\")\n")
    put(tmp_path, "app/src/Bad.kt", "webView.addJavascriptInterface(o, \"y\")\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_webview_construction_outside_allowed_file_fails(tmp_path):
    put(tmp_path, "app/src/Bad.kt", "val w = WebView(ctx)\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_subclass_names_and_clients_are_not_construction(tmp_path):
    put(tmp_path, "app/src/Ok.kt", "val c = object : WebViewClient() {}\nclass PoTokenWebView(x: Int)\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_javascript_enablement_outside_allowed_file_fails(tmp_path):
    put(tmp_path, "app/src/Bad.kt", "w.settings.setJavaScriptEnabled(true)\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out
    put(tmp_path, "app/src/Bad.kt", "w.settings.javaScriptEnabled = true\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_file_access_enabled_anywhere_fails(tmp_path):
    put(tmp_path, ALLOWED, "settings.allowFileAccess = true\n")
    code, out = run(tmp_path)
    assert code == 1 and "PoTokenWebView.kt" in out, out
    put(tmp_path, ALLOWED, "settings.setAllowFileAccess(true)\n")
    code, out = run(tmp_path)
    assert code == 1, out


def test_file_url_load_anywhere_fails(tmp_path):
    put(tmp_path, "app/src/Bad.kt", "webView.loadUrl(\"file:///x\")\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_file_access_disabled_and_clean_tree_pass(tmp_path):
    put(tmp_path, ALLOWED, "settings.allowFileAccess = false\nwebView.loadUrl(\"about:blank\")\n")
    put(tmp_path, "app/src/Fine.kt", "fun f() = 1\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out
