"""Real browser smoke test; the caller owns and cleans the registered fixture."""
import json
import re
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

data = json.load(sys.stdin)
ids = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome", headless=True)
    try:
        context = browser.new_context()
        context.add_cookies([{"name": "Admin-Token", "value": data["token"], "url": data["baseUrl"]}])
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        for width in (1920, 1440, 768, 390):
            page.set_viewport_size({"width": width, "height": 1000})
            page.goto(data["baseUrl"] + "/metal/material")
            page.wait_for_load_state("networkidle")
            expect(page.locator(".master-table")).to_be_visible()
            page.get_by_role("button", name="添加", exact=True).click()
            dialog = page.get_by_role("dialog", name="添加物料", exact=True)
            dialog.get_by_label("物料编码", exact=True).fill(f"UI_MT{width}_{data['suffix']}")
            for label, value in (("物料类型", "粉料"), ("金属类型", "铱")):
                dialog.get_by_role("combobox", name=re.compile(label + r"$")).press("ArrowDown")
                page.get_by_role("option", name=value, exact=True).click()
            assert dialog.evaluate("e=>e.getBoundingClientRect().left>=0&&e.getBoundingClientRect().right<=innerWidth+1")
            page.screenshot(path=str(Path(".runtime/material-20261102") / f"live-material-{width}.png"), full_page=True)
            with page.expect_response(lambda r: r.url.endswith("/metal/material") and r.request.method == "POST") as response:
                dialog.get_by_role("button", name="确定", exact=True).click()
            result = response.value.json()
            assert result["code"] == 200, result.get("msg")
            ids.append(str(result["id"]))
            expect(dialog).not_to_be_visible()
        assert not errors, errors
    finally:
        browser.close()
print(json.dumps({"passed": len(ids), "ids": ids}))
