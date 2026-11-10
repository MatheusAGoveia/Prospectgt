/* Verifica buscas reais nas três cidades prioritárias, sem abordagem comercial. */
const fs = require("node:fs"),
  path = require("node:path"),
  assert = require("node:assert/strict");
const root = path.resolve(__dirname, "..");
process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.join(root, ".tools/browsers");
const { chromium } = require("playwright");
const base = process.env.PROSPECTGT_URL || "http://127.0.0.1:8080";
const access = Object.fromEntries(
  fs
    .readFileSync(path.join(root, "data/acessos-iniciais.txt"), "utf8")
    .split(/\r?\n/)
    .filter((x) => x.includes(" : "))
    .map((x) => x.split(" : ")),
);
const output = path.join(root, "docs/evidencias");
const failures = [];
let browser;
(async () => {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 960 } });
  page.on("pageerror", (error) => failures.push(error.message));
  await page.goto(base);
  await page.locator("[name=username]").fill("vendedor1");
  await page.locator("[name=password]").waitFor();
  await page.locator("[name=password]").fill(access.vendedor1);
  await Promise.all([
    page.waitForURL(base + "/"),
    page.getByRole("button", { name: "Entrar na minha conta" }).click(),
  ]);
  await page.waitForFunction(
    () =>
      document.querySelector("#region").options.length === 25 &&
      !document.querySelector("#search-button").disabled,
  );
  const labels = await page.locator("#region option").allTextContents();
  assert.deepEqual(labels.slice(0, 3), ["Betim, MG", "Contagem, MG", "Belo Horizonte, MG"]);
  const result = [];
  for (const [label, slug] of [
    ["Betim, MG", "betim"],
    ["Contagem, MG", "contagem"],
    ["Belo Horizonte, MG", "bh"],
  ]) {
    const option = await page
      .locator("#region option")
      .evaluateAll((options, label) => options.find((x) => x.textContent === label).value, label);
    await page.locator("#region").selectOption(option);
    await page.locator("#category").selectOption("lojas");
    const posted = page.waitForResponse(
      (response) =>
        response.url().endsWith("/api/search") && response.request().method() === "POST",
    );
    await page.locator("#search-button").click();
    const response = await posted;
    assert.equal(response.status(), 200);
    const queued = await response.json();
    let completed;
    for (let i = 0; i < 120; i++) {
      const job = await page.evaluate(
        (id) => fetch("/api/jobs/" + id).then((result) => result.json()),
        queued.id,
      );
      if (job.status === "FAILED") throw Error(job.error);
      if (job.status === "COMPLETED") {
        completed = job.result;
        break;
      }
      await page.waitForTimeout(1000);
    }
    assert.ok(completed, "Busca não concluída.");
    assert.equal(completed.region, label);
    assert.ok(completed.source.includes("Catálogo regional"));
    await page.waitForFunction(
      () =>
        !document.querySelector("#search-button").disabled &&
        document.querySelectorAll(".lead-row").length > 0,
      {},
      { timeout: 120000 },
    );
    const count = await page.locator(".lead-row").count();
    const displayedIds = await page
      .locator(".lead-row")
      .evaluateAll((rows) => rows.map((row) => row.dataset.id));
    assert.deepEqual(displayedIds.sort(), completed.ids.slice().sort());
    assert.ok(count > 0);
    assert.ok((await page.locator("#map-caption").textContent()).includes("Catálogo regional"));
    assert.equal(await page.locator(".map-marker").count(), count);
    await page.locator("#toast").waitFor({ state: "hidden", timeout: 12000 });
    await page.screenshot({ path: path.join(output, "minas-" + slug + ".png") });
    result.push({
      region: label,
      category: "lojas",
      radiusKm: 3,
      displayed: count,
      source: "Catálogo regional OSM/Geofabrik",
      screenshot: "minas-" + slug + ".png",
    });
  }
  assert.deepEqual(failures, []);
  const evidence = {
    checkedAt: new Date().toISOString(),
    results: result,
    messagesSent: 0,
    reservationsChanged: 0,
    browserErrors: failures,
  };
  fs.writeFileSync(path.join(output, "minas-search-check.json"), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
})()
  .catch((error) => {
    let message = error.message;
    for (const password of Object.values(access))
      message = message.split(password).join("[senha ocultada]");
    console.error(message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await browser?.close();
  });
