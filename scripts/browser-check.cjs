/* Exercita a aplicação local real; não envia mensagens a empresas. */
const path = require("node:path");
const fs = require("node:fs");
const assert = require("node:assert/strict");
const root = path.resolve(__dirname, "..");
process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.join(root, ".tools", "browsers");
const { chromium } = require("playwright");
const base = process.env.PROSPECTGT_URL || "http://127.0.0.1:8080";
const output = path.join(root, "docs", "evidencias");
const credentials = Object.fromEntries(
  fs
    .readFileSync(path.join(root, "data", "acessos-iniciais.txt"), "utf8")
    .split(/\r?\n/)
    .filter((x) => x.includes(" : "))
    .map((x) => x.split(" : ")),
);
const failures = [],
  checks = [];
let testedLead,
  held = false,
  browser,
  admin;
let diagnosticPage;

async function login(context, username) {
  const page = await context.newPage();
  page.on("pageerror", (e) => failures.push(e.message));
  page.on("console", (m) => {
    if (m.type() === "error" && /Content Security Policy|Refused to/.test(m.text()))
      failures.push(m.text());
  });
  await page.goto(base);
  await page.locator("[name=username]").fill(username);
  await page.locator("[name=password]").fill(credentials[username]);
  await Promise.all([
    page.waitForURL(base + "/"),
    page.getByRole("button", { name: "Entrar na minha conta" }).click(),
  ]);
  await page.waitForFunction(() => document.querySelector("#category").options.length === 10);
  return page;
}
async function request(page, endpoint, body) {
  return page.evaluate(
    async ({ endpoint, body }) => {
      const response = await fetch(
        "/api" + endpoint,
        body === undefined
          ? {}
          : {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                [document.querySelector("meta[name=_csrf_header]").content]:
                  document.querySelector("meta[name=_csrf]").content,
              },
              body: JSON.stringify(body),
            },
      );
      return { status: response.status, data: await response.json() };
    },
    { endpoint, body },
  );
}
async function waitJob(page, id) {
  for (let i = 0; i < 120; i++) {
    const r = await request(page, "/jobs/" + id);
    assert.equal(r.status, 200);
    if (r.data.status === "COMPLETED") return r.data.result;
    if (r.data.status === "FAILED") throw Error(r.data.error);
    await page.waitForTimeout(1000);
  }
  throw Error("Tarefa não terminou no limite do teste.");
}
async function main() {
  fs.mkdirSync(output, { recursive: true });
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    permissions: ["clipboard-read", "clipboard-write"],
  });
  const page = await login(context, "vendedor1");
  diagnosticPage = page;
  checks.push("Login individual e configuração carregada");
  await page.locator("#search-button").click();
  await page.waitForFunction(
    () => document.querySelectorAll(".lead-row").length > 0,
    {},
    { timeout: 90000 },
  );
  const list = (await request(page, "/leads")).data;
  assert.ok(
    list.length > 0 && list.every((x) => x.sourceUrl.startsWith("https://www.openstreetmap.org/")),
  );
  assert.ok((await page.locator(".map-marker").count()) > 0);
  checks.push("Busca automática encontra registros OSM reais e pontos no mapa");
  const candidate =
    list.find((x) => x.state === "AVAILABLE" && x.website && (x.phone || x.whatsapp)) ||
    list.find((x) => x.state === "AVAILABLE" && (x.phone || x.whatsapp));
  assert.ok(candidate, "É necessário um registro disponível com contato para verificar o fluxo.");
  testedLead = candidate.id;
  await page.locator("#list-view").click();
  await page.locator('[data-id="' + testedLead + '"]').click();
  await page.locator("#message").waitFor();
  const analysis = await request(page, "/leads/" + testedLead + "/analyze", {});
  const analysisResult = await waitJob(page, analysis.data.id);
  assert.ok(["COMPLETED", "INCONCLUSIVE", "NO_WEBSITE"].includes(analysisResult.state));
  await page.locator('[data-id="' + testedLead + '"]').click();
  await page.locator("#message").waitFor();
  assert.ok((await page.locator("#message").inputValue()).includes(candidate.name));
  checks.push("Ficha, origem, análise básica e mensagem com nome da empresa");
  const customMessage =
    "Olá, equipe " + candidate.name + ". Mensagem revisada no teste, sem envio.";
  await page.locator("#message").fill(customMessage);
  await page.locator("#copy-message").click();
  await page.waitForFunction(() => document.querySelector("#feedback-form") !== null);
  held = true;
  assert.equal(await page.locator("#message").inputValue(), customMessage);
  assert.ok((await page.evaluate(() => navigator.clipboard.readText())).includes(candidate.name));
  const afterClaim = (await request(page, "/leads/" + testedLead)).data;
  assert.ok(afterClaim.reservedUntil);
  assert.ok(afterClaim.history.some((x) => x.action === "COPY_MESSAGE"));
  assert.ok(!afterClaim.history.some((x) => x.action === "MESSAGE_SENT"));
  checks.push("Cópia real, reserva persistida e evento sem alegar envio");
  const second = await browser.newContext({ viewport: { width: 1024, height: 768 } });
  const other = await login(second, "vendedor2");
  assert.equal((await request(other, "/leads/" + testedLead + "/claim", {})).status, 409);
  checks.push("Segundo vendedor impedido de assumir a mesma oportunidade");
  await second.close();
  await page
    .locator("#feedback-comment")
    .fill("Teste funcional do MVP — não houve contato com a empresa.");
  await page.locator("#feedback-form button").click();
  await page.waitForFunction(() =>
    document.querySelector("#detail").textContent.includes("Teste funcional do MVP"),
  );
  await page.reload();
  await page.waitForFunction(() => document.querySelector("#category").options.length === 10);
  await page.locator("#portfolio-nav").click();
  await page.locator('[data-id="' + testedLead + '"]').click();
  await page.waitForFunction(() =>
    document.querySelector("#detail").textContent.includes("Teste funcional do MVP"),
  );
  checks.push("Feedback e carteira preservados após recarregar");
  await page.locator("#explore-nav").click();
  await page.route("**/api/search", async (route) => {
    const response = await route.fetch();
    await new Promise((resolve) => setTimeout(resolve, 1500));
    await route.fulfill({ response });
  });
  await page.locator("#search-button").click();
  await page.locator("#portfolio-nav").click();
  await page.waitForFunction(() =>
    document.querySelector("#results-summary").textContent.includes("na sua carteira"),
  );
  await page.waitForTimeout(2000);
  assert.equal(await page.locator("#view-title").textContent(), "Minha carteira");
  const mine = (await request(page, "/leads?mine=true")).data.map((x) => x.id).sort();
  assert.deepEqual(
    (await page.locator(".lead-row").evaluateAll((rows) => rows.map((r) => r.dataset.id))).sort(),
    mine,
  );
  await page.unroute("**/api/search");
  checks.push("Busca lenta não sobrescreve a carteira ao trocar de tela");
  // Capture a visão do mapa com os resultados reais, antes de liberar o registro.
  await page.locator("#explore-nav").click();
  await page.locator("#search-button").click();
  await page.waitForFunction(() => document.querySelectorAll(".map-marker").length > 0);
  await page.locator("#list-view").click();
  await page.locator('[data-id="' + testedLead + '"]').click();
  await page.locator("#map-view").click();
  await page.waitForTimeout(2000);
  await page.locator("#toast").waitFor({ state: "hidden", timeout: 12000 });
  await page.screenshot({ path: path.join(output, "mvp-desktop.png") });
  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForTimeout(300);
  assert.ok(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
    "Layout móvel ultrapassou a largura da tela.",
  );
  await page.screenshot({ path: path.join(output, "mvp-celular.png") });
  await page.locator("#close-detail").click();
  await page.locator("#toggle-filters").click();
  assert.ok(await page.locator("#search-button").isVisible());
  await page.setViewportSize({ width: 820, height: 1180 });
  assert.ok(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
    "Layout de tablet ultrapassou a tela.",
  );
  checks.push("Layout de computador, tablet e celular sem rolagem horizontal");
  const adminContext = await browser.newContext();
  admin = await login(adminContext, "admin");
  assert.equal((await request(admin, "/admin/users")).data.length, 6);
  assert.equal(
    (
      await request(admin, "/admin/leads/" + testedLead + "/release", {
        reason: "Fim do teste funcional automatizado. Nenhuma abordagem foi enviada.",
      })
    ).status,
    200,
  );
  held = false;
  const finalLead = (await request(admin, "/leads/" + testedLead)).data;
  assert.equal(finalLead.state, "AVAILABLE");
  assert.ok(finalLead.history.some((x) => x.action === "FEEDBACK"));
  checks.push("Gestor consulta equipe e libera o teste preservando histórico");
  assert.deepEqual(failures, [], "Erros de JavaScript ou CSP");
  const evidence = {
    checkedAt: new Date().toISOString(),
    source: "Catálogo OpenStreetMap/Geofabrik Centro-Oeste real",
    resultCount: list.length,
    analysisState: analysisResult.state,
    checkedLeadId: testedLead,
    messageSent: false,
    checks,
    browserErrors: failures,
  };
  fs.writeFileSync(path.join(output, "browser-check.json"), JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
}
main()
  .catch(async (e) => {
    console.error(e.message);
    console.error("Etapas concluídas:", checks.join("; "));
    if (diagnosticPage) {
      console.error(await diagnosticPage.locator("#results-summary").textContent());
      await diagnosticPage.screenshot({ path: path.join(output, "falha-browser.png") });
    }
    process.exitCode = 1;
  })
  .finally(async () => {
    if (held && testedLead && browser) {
      try {
        if (!admin) admin = await login(await browser.newContext(), "admin");
        await request(admin, "/admin/leads/" + testedLead + "/release", {
          reason: "Limpeza de teste funcional interrompido, sem envio de mensagem.",
        });
      } catch {
        console.error("Libere a reserva do teste pelo gestor.");
      }
    }
    await browser?.close();
  });
