# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-webview-safety.sh"
ALLOWED = "innertube/src/main/kotlin/app/aria/innertube/potoken/PoTokenWebView.kt"


# A confined file that satisfies every positive rule; negative tests start from it and break one thing.
GOOD = (
    "settings.blockNetworkLoads = true\n"
    "settings.allowContentAccess = false\n"
    "settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW\n"
    "if (!NavigationPolicy.isAllowed(url)) return true\n"
    "@JavascriptInterface\nfun onResult(s: String) {}\n"
)


def put(root: pathlib.Path, rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root: pathlib.Path):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def test_allowed_file_may_hold_everything_confined(tmp_path):
    put(tmp_path, ALLOWED, GOOD + "webView.addJavascriptInterface(b, \"x\")\nval w = WebView(context)\nsettings.javaScriptEnabled = true\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_bridge_outside_allowed_file_fails(tmp_path):
    put(tmp_path, ALLOWED, GOOD + "webView.addJavascriptInterface(b, \"x\")\n")
    put(tmp_path, "app/src/Bad.kt", "webView.addJavascriptInterface(o, \"y\")\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_webview_construction_outside_allowed_file_fails(tmp_path):
    put(tmp_path, "app/src/Bad.kt", "val w = WebView(ctx)\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_subclass_names_and_clients_are_not_construction(tmp_path):
    put(tmp_path, ALLOWED, GOOD)
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
    put(tmp_path, ALLOWED, GOOD + "settings.allowFileAccess = true\n")
    code, out = run(tmp_path)
    assert code == 1 and "PoTokenWebView.kt" in out, out
    put(tmp_path, ALLOWED, GOOD + "settings.setAllowFileAccess(true)\n")
    code, out = run(tmp_path)
    assert code == 1, out


def test_file_url_load_anywhere_fails(tmp_path):
    put(tmp_path, "app/src/Bad.kt", "webView.loadUrl(\"file:///x\")\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_file_access_disabled_and_clean_tree_pass(tmp_path):
    put(tmp_path, ALLOWED, GOOD + "settings.allowFileAccess = false\nwebView.loadUrl(\"about:blank\")\n")
    put(tmp_path, "app/src/Fine.kt", "fun f() = 1\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_missing_confined_file_fails(tmp_path):
    put(tmp_path, "app/src/Fine.kt", "fun f() = 1\n")
    code, out = run(tmp_path)
    assert code == 1 and "missing" in out, out


def test_each_required_setting_is_enforced(tmp_path):
    for needle, label in [
        ("settings.blockNetworkLoads = true\n", "blockNetworkLoads"),
        ("settings.allowContentAccess = false\n", "allowContentAccess"),
        ("settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW\n", "MIXED_CONTENT_NEVER_ALLOW"),
        ("if (!NavigationPolicy.isAllowed(url)) return true\n", "NavigationPolicy"),
    ]:
        assert needle in GOOD
        put(tmp_path, ALLOWED, GOOD.replace(needle, ""))
        code, out = run(tmp_path)
        assert code == 1 and label in out, (label, out)


def test_wrong_setting_values_fail(tmp_path):
    put(tmp_path, ALLOWED, GOOD.replace("blockNetworkLoads = true", "blockNetworkLoads = false"))
    code, out = run(tmp_path)
    assert code == 1 and "blockNetworkLoads" in out, out
    put(tmp_path, ALLOWED, GOOD.replace("allowContentAccess = false", "allowContentAccess = true"))
    code, out = run(tmp_path)
    assert code == 1 and "allowContentAccess" in out, out


def test_commented_out_setting_does_not_count(tmp_path):
    put(tmp_path, ALLOWED, GOOD.replace("settings.blockNetworkLoads = true", "// settings.blockNetworkLoads = true"))
    code, out = run(tmp_path)
    assert code == 1 and "blockNetworkLoads" in out, out


def test_exactly_one_bridge_method_is_required(tmp_path):
    put(tmp_path, ALLOWED, GOOD + "@JavascriptInterface\nfun second(s: String) {}\n")
    code, out = run(tmp_path)
    assert code == 1 and "exactly one" in out, out
    put(tmp_path, ALLOWED, GOOD.replace("@JavascriptInterface\n", ""))
    code, out = run(tmp_path)
    assert code == 1 and "exactly one" in out, out


def test_bridge_annotation_outside_allowed_file_fails(tmp_path):
    put(tmp_path, ALLOWED, GOOD)
    put(tmp_path, "app/src/Bad.kt", "@JavascriptInterface\nfun leak() {}\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.kt" in out, out


def test_file_url_access_enabled_fails(tmp_path):
    for line in ["settings.allowFileAccessFromFileURLs = true", "settings.setAllowFileAccessFromFileURLs(true)",
                 "settings.allowUniversalAccessFromFileURLs = true", "settings.setAllowUniversalAccessFromFileURLs(true)"]:
        put(tmp_path, ALLOWED, GOOD + line + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "PoTokenWebView.kt" in out, (line, out)


def test_java_webview_construction_fails(tmp_path):
    put(tmp_path, ALLOWED, GOOD)
    put(tmp_path, "app/src/Bad.java", "class Bad { void f(Context c) { new WebView(c); } }\n")
    code, out = run(tmp_path)
    assert code == 1 and "Bad.java" in out, out


def test_layout_xml_webview_fails(tmp_path):
    put(tmp_path, ALLOWED, GOOD)
    put(tmp_path, "app/src/main/res/layout/screen.xml", "<FrameLayout><WebView android:id=\"@+id/w\"/></FrameLayout>\n")
    code, out = run(tmp_path)
    assert code == 1 and "screen.xml" in out, out
    put(tmp_path, "app/src/main/res/layout/screen.xml", "<android.webkit.WebView android:id=\"@+id/w\"/>\n")
    code, out = run(tmp_path)
    assert code == 1 and "screen.xml" in out, out


def test_layout_without_webview_passes(tmp_path):
    put(tmp_path, ALLOWED, GOOD)
    put(tmp_path, "app/src/main/res/layout/screen.xml", "<FrameLayout><WebViewClientNote/></FrameLayout>\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out
