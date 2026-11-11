"use strict";
const $ = (s) => document.querySelector(s);
const esc = (v) => {
  const e = document.createElement("span");
  e.textContent = v ?? "";
  return e.innerHTML.replaceAll('"', "&quot;").replaceAll("'", "&#39;");
};
const states = {
  AVAILABLE: "Disponível",
  IN_PROGRESS: "Em atendimento",
  FOLLOW_UP: "Retorno agendado",
  WON: "Venda informada",
  NOT_INTERESTED: "Sem interesse",
  DO_NOT_CONTACT: "Não contatar",
};
const actions = {
  LEAD_VIEWED: "Ficha consultada",
  CLAIMED: "Oportunidade assumida",
  COPY_MESSAGE: "Mensagem copiada",
  OPEN_WHATSAPP: "Clique para abrir WhatsApp",
  FEEDBACK: "Feedback registrado",
  RESERVATION_EXPIRED: "Reserva expirada",
  ADMIN_RELEASED: "Reabertura pela gestão",
};
let regions = [];
let me,
  config,
  leads = [],
  mode = "explore",
  selected = null,
  map,
  markers = [],
  centerMarker,
  currentRegion,
  view = "map",
  polling = false;
let toastTimer;
let navigation = 0;
let detailSequence = 0;
const drafts = new Map();
function toast(message, error = false) {
  clearTimeout(toastTimer);
  $("#toast").textContent = message;
  $("#toast").className = "toast" + (error ? " error" : "");
  toastTimer = setTimeout(() => $("#toast").classList.add("hidden"), 7000);
}
async function api(path, body) {
  const options = { headers: { Accept: "application/json" } };
  if (body !== undefined) {
    options.method = "POST";
    options.headers["Content-Type"] = "application/json";
    options.headers[$("meta[name=_csrf_header]").content] = $("meta[name=_csrf]").content;
    options.body = JSON.stringify(body);
  }
  const response = await fetch("/api" + path, options);
  if (response.status === 401) {
    location.href = "/login";
    throw Error("Entre novamente na sua conta.");
  }
  let data;
  try {
    data = await response.json();
  } catch {
    data = { message: "Resposta inesperada do servidor." };
  }
  if (!response.ok) throw Error(data.message || "Não foi possível concluir a ação.");
  return data;
}
function safeUrl(value) {
  if (!value) return null;
  try {
    const u = new URL(value.includes("://") ? value : "https://" + value);
    return ["http:", "https:"].includes(u.protocol) && !u.username && !u.password ? u.href : null;
  } catch {
    return null;
  }
}
function date(value) {
  return value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: "America/Sao_Paulo",
      }).format(new Date(value))
    : "Não informado";
}
function priority(e) {
  return e.score >= 70
    ? ["Alta", "green", "var(--high)"]
    : e.score >= 40
      ? ["Média", "amber", "var(--medium)"]
      : ["Baixa", "gray", "var(--low)"];
}
function badge(e) {
  let p = priority(e);
  return (
    '<span class="pill ' +
    p[1] +
    '">' +
    p[0] +
    (e.analysisState === "PENDING" ? " · preliminar" : "") +
    "</span>"
  );
}
function busy(value, text = "Buscando empresas…") {
  $("#busy").classList.toggle("hidden", !value);
  $("#busy-text").textContent = text;
  $("#search-button").disabled = value;
  polling = value;
}
function initMap() {
  if (!window.L) {
    $("#map").innerHTML =
      '<p class="muted">Mapa indisponível. A busca e a lista continuam disponíveis.</p>';
    return;
  }
  map = L.map("map", { zoomControl: false }).setView([currentRegion[1], currentRegion[2]], 14);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution:
      '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors',
    maxZoom: 19,
  }).addTo(map);
  L.control.zoom({ position: "bottomright" }).addTo(map);
  centerMarker = L.marker([currentRegion[1], currentRegion[2]], {
    draggable: true,
    icon: L.divIcon({
      className: "",
      html: '<span class="search-center"></span>',
      iconSize: [22, 22],
      iconAnchor: [11, 11],
    }),
  }).addTo(map);
  centerMarker.bindTooltip("Centro da busca · arraste para ajustar");
  centerMarker.on("dragend", () => {
    currentRegion = [
      "Área selecionada",
      centerMarker.getLatLng().lat,
      centerMarker.getLatLng().lng,
    ];
    toast("Centro da busca ajustado. Clique em Buscar empresas.");
  });
}
function filtered() {
  return leads
    .filter(
      (e) =>
        (!$("#with-contact").checked || e.phone || e.whatsapp) &&
        (!$("#available-only").checked || e.state === "AVAILABLE"),
    )
    .sort((a, b) => b.score - a.score);
}
function render() {
  const data = filtered();
  $("#empty-overlay").classList.toggle("hidden", data.length > 0 || view === "list");
  if (leads.length) {
    $("#results-summary").textContent =
      data.length + " oportunidades exibidas · " + leads.length + " registros na seleção";
  }
  const pickable = data.filter(contactable);
  $("#list").innerHTML = data.length
    ? (pickable.length
        ? '<div class="list-head"><button type="button" class="text-button" id="pick-all">' +
          (pickable.every((e) => queue.has(e.id))
            ? "Tirar todos da fila"
            : "Colocar todos com contato na fila (" + pickable.length + ")") +
          "</button></div>"
        : "") +
      data
        .map(
          (e) =>
            '<div class="lead-item"><label class="lead-pick" title="' +
            (contactable(e) ? "Colocar na fila de abordagem" : "Sem contato disponível para você") +
            '"><input type="checkbox" data-pick="' +
            e.id +
            '"' +
            (queue.has(e.id) ? " checked" : "") +
            (contactable(e) ? "" : " disabled") +
            ' aria-label="Fila: ' +
            esc(e.name) +
            '"></label><button class="lead-row" data-id="' +
            e.id +
            '"><span class="lead-symbol">⌖</span><span><strong>' +
            esc(e.name) +
            "</strong><p>" +
            esc(e.categoryLabel) +
            " · " +
            esc(e.city || "Região selecionada") +
            "</p>" +
            (e.ownerName
              ? '<p class="row-owner">' +
                esc(e.ownerName) +
                " · " +
                esc(states[e.state] || e.state) +
                "</p>"
              : "") +
            "</span>" +
            badge(e) +
            "</button></div>",
        )
        .join("")
    : '<div class="empty-overlay"><h2>Nenhuma oportunidade nesta seleção.</h2><p>Experimente outro ramo, amplie o raio ou ajuste os filtros.</p></div>';
  $("#list")
    .querySelectorAll("[data-id]")
    .forEach((el) => el.addEventListener("click", () => openLead(el.dataset.id)));
  $("#list")
    .querySelectorAll("[data-pick]")
    .forEach((el) =>
      el.addEventListener("change", () => {
        const e = leads.find((x) => x.id === el.dataset.pick);
        if (el.checked && e) queue.set(e.id, { id: e.id, name: e.name });
        else queue.delete(el.dataset.pick);
        updateQueueButton();
        render();
      }),
    );
  if ($("#pick-all"))
    $("#pick-all").onclick = () => {
      const all = pickable.every((e) => queue.has(e.id));
      pickable.forEach((e) =>
        all ? queue.delete(e.id) : queue.set(e.id, { id: e.id, name: e.name }),
      );
      updateQueueButton();
      render();
    };
  if (map) {
    markers.forEach((m) => m.remove());
    markers = [];
    data.forEach((e) => {
      if (e.lat == null || e.lon == null) return;
      const p = priority(e);
      const m = L.marker([e.lat, e.lon], {
        icon: L.divIcon({
          className: "",
          html:
            '<span class="map-marker' +
            (selected === e.id ? " selected" : "") +
            '" style="background:' +
            p[2] +
            '"></span>',
          iconSize: [20, 20],
          iconAnchor: [10, 10],
        }),
      }).addTo(map);
      const text = document.createElement("span");
      text.textContent = e.name + " · " + p[0] + " prioridade";
      m.bindTooltip(text, { className: "map-tooltip" });
      m.on("click", () => openLead(e.id));
      markers.push(m);
    });
  }
}
async function waitJob(id, onComplete, isCurrent = () => true) {
  for (let i = 0; i < 100; i++) {
    if (!isCurrent()) return;
    const job = await api("/jobs/" + id);
    if (job.status === "COMPLETED") {
      await onComplete(job.result);
      return;
    }
    if (job.status === "FAILED")
      throw Error(job.error || "A fonte não respondeu. Tente novamente.");
    await new Promise((r) => setTimeout(r, 1200));
  }
  throw Error("A tarefa continua em processamento. Consulte novamente em instantes.");
}
async function search(event) {
  event?.preventDefault();
  if (polling) return;
  const sequence = ++navigation;
  mode = "explore";
  activateNav();
  closeLead();
  $("#filters").classList.remove("open");
  busy(true, "Encontrando empresas na região…");
  try {
    const job = await api("/search", {
      lat: currentRegion[1],
      lon: currentRegion[2],
      radius: Number($("#radius").value),
      category: $("#category").value,
      region: currentRegion[0],
    });
    await waitJob(job.id, async (result) => {
      if (sequence !== navigation) return;
      const found = result.ids.length ? await api("/leads?ids=" + result.ids.join(",")) : [];
      if (sequence !== navigation) return;
      leads = found;
      $("#map-caption").textContent = result.source + " · dados sujeitos a lacunas";
      $("#results-summary").textContent =
        leads.length + " registros encontrados · " + result.region;
      render();
      if (!leads.length) {
        $("#empty-overlay h2").textContent = "Sem registros para este recorte.";
        $("#empty-overlay p").textContent =
          "Isso não significa que não existam empresas. Tente outro ramo ou uma área próxima.";
      }
    });
    if (sequence !== navigation) return;
    toast(leads.length + " registros encontrados. Confira contato e contexto antes da abordagem.");
  } catch (e) {
    if (sequence !== navigation) return;
    toast(e.message, true);
    $("#results-summary").textContent = "Busca não concluída. Nenhum resultado foi inventado.";
  } finally {
    if (sequence === navigation) busy(false);
  }
}
function activateNav() {
  $("#explore-nav").classList.toggle("active", mode === "explore");
  $("#portfolio-nav").classList.toggle("active", mode === "portfolio");
  $("#admin-nav").classList.toggle("active", mode === "team");
  $("#workspace").classList.toggle("hidden", mode === "team");
  $("#team-view").classList.toggle("hidden", mode !== "team");
  $("#view-title").textContent = mode === "portfolio" ? "Minha carteira" : "Explorar empresas";
}
async function portfolio() {
  const sequence = ++navigation;
  busy(false);
  mode = "portfolio";
  activateNav();
  closeLead();
  const found = await api("/leads?mine=true");
  if (sequence !== navigation) return;
  leads = found;
  $("#portfolio-count").textContent = leads.length;
  setView("list");
  render();
  $("#results-summary").textContent = leads.length + " oportunidades na sua carteira";
}
function setView(value) {
  view = value;
  $("#map-view").classList.toggle("selected", value === "map");
  $("#list-view").classList.toggle("selected", value === "list");
  $("#list").classList.toggle("hidden", value !== "list");
  $("#map").classList.toggle("hidden", value !== "map");
  $("#map-caption").classList.toggle("hidden", value !== "map");
  render();
  if (map) setTimeout(() => map.invalidateSize(), 80);
}
function closeLead() {
  ++detailSequence;
  $("#detail").inert = false;
  selected = null;
  $("#detail").classList.add("hidden");
  $("#workspace").classList.remove("with-detail");
  if (map) setTimeout(() => map.invalidateSize(), 80);
}
async function openLead(id) {
  const sequence = ++detailSequence;
  selected = id;
  $("#detail").inert = true;
  let e;
  try {
    e = await api("/leads/" + id);
  } catch (error) {
    if (sequence === detailSequence) {
      $("#detail").inert = false;
      toast(error.message, true);
    }
    return;
  }
  if (selected !== id || sequence !== detailSequence) return;
  const existing = leads.findIndex((x) => x.id === id);
  if (existing >= 0) leads[existing] = e;
  $("#workspace").classList.add("with-detail");
  $("#detail").classList.remove("hidden");
  renderDetail(e);
  $("#detail").dataset.leadId = e.id;
  $("#detail").inert = false;
  render();
  if (map) {
    setTimeout(() => map.invalidateSize(), 80);
  }
  if (e.analysisState === "PENDING") {
    try {
      const j = await api("/leads/" + e.id + "/analyze", {});
      waitJob(j.id, async () => {
        if (selected === e.id && !$("#detail").dataset.dirty) await openLead(e.id);
        else if (selected === e.id)
          toast("Análise pronta. Reabra a ficha para consultar os sinais atualizados.");
      }).catch(() => {});
    } catch {}
  }
}
function renderDetail(e) {
  delete $("#detail").dataset.dirty;
  const site = safeUrl(e.website);
  const hasNumber = e.phone || e.whatsapp;
  const owned = e.ownerId === me.id;
  const canFeedback = owned || me.role === "ADMIN";
  $("#detail").innerHTML =
    '<div class="detail-top"><div><h2>' +
    esc(e.name) +
    "</h2><p>" +
    esc(e.categoryLabel) +
    "<br>" +
    esc(e.address || e.city || "Endereço não informado na fonte") +
    '</p></div><button class="icon-btn" id="close-detail" aria-label="Fechar detalhes">×</button></div><div class="detail-badges">' +
    badge(e) +
    '<span class="pill gray">' +
    esc(states[e.state] || e.state) +
    "</span></div>" +
    (e.ownerName
      ? '<div class="owner-note">' +
        (owned ? "Na sua carteira" : "Responsável: " + esc(e.ownerName)) +
        (e.reservedUntil ? "<br>Reserva até " + date(e.reservedUntil) : "") +
        "</div>"
      : "") +
    '<section class="detail-section"><div class="section-label">Oportunidade identificada</div><ul class="signal-list">' +
    e.reasons.map((r) => "<li>" + esc(r) + "</li>").join("") +
    '</ul><button class="text-button" id="analyze-site">↻ ' +
    (e.analyzedAt ? "Atualizar análise básica" : "Analisar site agora") +
    "</button>" +
    (site
      ? '<p class="small"><a href="' +
        esc(site) +
        '" target="_blank" rel="noopener noreferrer">Visitar site ↗</a></p>'
      : "") +
    "</section>" +
    '<section class="detail-section"><div class="offer"><strong>Landing page / site de vendas</strong><p>Referência: R$ 500 a R$ 2.000 · orçamento após conversa</p></div></section>' +
    '<section class="detail-section"><div class="section-label">Contato publicado</div><div class="contact-line"><strong>' +
    esc(e.whatsapp || e.phone || "Não informado na fonte") +
    '</strong><p class="muted">' +
    (e.whatsapp
      ? "WhatsApp identificado em dado ou link publicado; conta ativa não verificada."
      : e.phone
        ? "Telefone publicado. Uso como WhatsApp ainda não confirmado."
        : "Confira o site ou a fonte. Nenhum contato foi presumido.") +
    "</p></div></section>" +
    '<section class="detail-section"><div class="section-label">Mensagem sugerida</div><textarea class="message-box" id="message" maxlength="3000" aria-label="Mensagem personalizada">' +
    esc(e.message) +
    '</textarea><div class="actions"><button class="btn" id="copy-message" ' +
    (!e.canContact ? "disabled" : "") +
    '>Copiar</button><button class="btn primary" id="open-whatsapp" ' +
    (!e.canContact || !hasNumber ? "disabled" : "") +
    '>Abrir WhatsApp ↗</button></div><p class="small muted" style="margin-top:8px">Copiar ou abrir reserva a oportunidade. O envio acontece no WhatsApp.</p>' +
    (contactable(e)
      ? '<button type="button" class="text-button" id="toggle-queue" style="margin-top:12px">' +
        (queue.has(e.id) ? "− Tirar da fila de abordagem" : "+ Colocar na fila de abordagem") +
        "</button>"
      : "") +
    "</section>" +
    (canFeedback
      ? '<section class="detail-section"><div class="section-label">Como foi a conversa?</div><form id="feedback-form"><label>Situação<select id="feedback-state"><option value="IN_PROGRESS">Em atendimento / sem resposta</option><option value="FOLLOW_UP">Retorno combinado</option><option value="WON">Contrato fechado</option><option value="NOT_INTERESTED">Sem interesse</option><option value="DO_NOT_CONTACT">Não contatar</option></select></label><label>Seu feedback<textarea id="feedback-comment" required maxlength="2000" placeholder="Registre o que aconteceu…"></textarea></label><label>Próximo contato<input type="date" id="feedback-date"></label><button class="btn primary wide">Salvar feedback</button></form></section>'
      : '<section class="detail-section"><button class="btn wide" id="claim-lead" ' +
        (!e.canContact ? "disabled" : "") +
        ">Assumir oportunidade</button></section>") +
    '<section class="detail-section"><div class="section-label">Histórico automático</div>' +
    e.history
      .slice(0, 12)
      .map(
        (h) =>
          '<div class="history-item"><strong>' +
          esc(actions[h.action] || h.action) +
          "</strong><p>" +
          esc(h.detail) +
          "</p><time>" +
          esc(h.actor) +
          " · " +
          date(h.date) +
          "</time></div>",
      )
      .join("") +
    "</section>" +
    (me.role === "ADMIN" && e.state !== "AVAILABLE"
      ? '<section class="detail-section"><button class="btn wide" id="release-lead">Reabrir oportunidade</button></section>'
      : "") +
    '<div class="detail-source">Fonte: <a href="' +
    esc(safeUrl(e.sourceUrl) || "#") +
    '" target="_blank" rel="noopener noreferrer">' +
    esc(e.sourceName) +
    "</a><br>Obtido em " +
    date(e.fetchedAt) +
    (e.sourceUpdatedAt ? "<br>Última edição do registro OSM: " + date(e.sourceUpdatedAt) : "") +
    "<br>" +
    (e.analyzedAt ? "Análise básica: " + date(e.analyzedAt) : "Análise de site ainda pendente.") +
    "<br>Prioridade por regras, sem garantia de compra.</div>";
  $("#detail")
    .querySelectorAll("input, select, textarea")
    .forEach((input) => {
      input.oninput = () => {
        $("#detail").dataset.dirty = "true";
      };
    });
  if (drafts.has(e.id)) $("#message").value = drafts.get(e.id);
  $("#message").addEventListener("input", () => drafts.set(e.id, $("#message").value));
  if ($("#feedback-form")) {
    $("#feedback-state").value =
      states[e.state] && e.state !== "AVAILABLE" ? e.state : "IN_PROGRESS";
    if (e.followUp)
      $("#feedback-date").value = new Date(e.followUp).toLocaleDateString("sv-SE", {
        timeZone: "America/Sao_Paulo",
      });
  }
  $("#close-detail").onclick = closeLead;
  $("#analyze-site").onclick = async () => {
    const button = $("#analyze-site");
    button.disabled = true;
    button.textContent = "Analisando…";
    try {
      const j = await api("/leads/" + e.id + "/analyze", {});
      await waitJob(j.id, async () => {
        if (selected === e.id) await openLead(e.id);
      });
      toast("Análise básica atualizada.");
    } catch (error) {
      toast(error.message, true);
      if (selected === e.id) await openLead(e.id);
    }
  };
  $("#copy-message").onclick = () => contact(e, "COPY_MESSAGE");
  $("#open-whatsapp").onclick = () => contact(e, "OPEN_WHATSAPP");
  if ($("#toggle-queue"))
    $("#toggle-queue").onclick = () => {
      if (queue.has(e.id)) queue.delete(e.id);
      else queue.set(e.id, { id: e.id, name: e.name });
      $("#toggle-queue").textContent = queue.has(e.id)
        ? "− Tirar da fila de abordagem"
        : "+ Colocar na fila de abordagem";
      updateQueueButton();
      render();
    };
  if ($("#claim-lead"))
    $("#claim-lead").onclick = async () => {
      try {
        await api("/leads/" + e.id + "/claim", {});
        await openLead(e.id);
        toast("Oportunidade reservada por três dias úteis.");
      } catch (error) {
        toast(error.message, true);
      }
    };
  if ($("#feedback-form"))
    $("#feedback-form").onsubmit = async (ev) => {
      ev.preventDefault();
      try {
        await api("/leads/" + e.id + "/feedback", {
          state: $("#feedback-state").value,
          comment: $("#feedback-comment").value,
          followUp: $("#feedback-date").value || null,
        });
        await openLead(e.id);
        toast("Feedback registrado.");
      } catch (error) {
        toast(error.message, true);
      }
    };
  if ($("#release-lead"))
    $("#release-lead").onclick = async () => {
      const reason = prompt(
        "Por que esta oportunidade deve ser reaberta? Confirme o motivo, especialmente se houve pedido para não contatar.",
      );
      if (!reason?.trim()) return;
      try {
        await api("/admin/leads/" + e.id + "/release", { reason });
        await openLead(e.id);
        toast("Oportunidade reaberta.");
      } catch (error) {
        toast(error.message, true);
      }
    };
}
async function contact(e, action) {
  ++detailSequence;
  const text = $("#message").value;
  drafts.set(e.id, text);
  let popup;
  if (action === "OPEN_WHATSAPP") {
    popup = window.open("about:blank", "_blank");
    if (popup) popup.opener = null;
  }
  try {
    const result = await api("/leads/" + e.id + "/contact", {
      action,
      message: text,
    });
    if (action === "OPEN_WHATSAPP") {
      if (popup) popup.location.href = result.url;
      else toast("Seu navegador bloqueou a nova aba. Permita popups para abrir o WhatsApp.", true);
    } else {
      try {
        await navigator.clipboard.writeText(result.message);
      } catch {
        const input = $("#message");
        input.select();
        if (!document.execCommand("copy")) throw Error("Selecione e copie o texto da mensagem.");
      }
      toast("Mensagem copiada. Oportunidade reservada.");
    }
    if (selected === e.id) await openLead(e.id);
  } catch (error) {
    if (popup) popup.close();
    toast(error.message, true);
  }
}
/* ---------- Fila de abordagem: o vendedor envia cada mensagem, uma por vez ---------- */
const DAILY_LIMIT = 40;
const queue = new Map();
let run = null;
let queueTimer;
function contactable(e) {
  return Boolean(e && e.canContact && (e.phone || e.whatsapp));
}
function store(key, value) {
  try {
    if (value === undefined) return localStorage.getItem(key);
    localStorage.setItem(key, String(value));
  } catch {
    return null;
  }
}
function sentKey() {
  return (
    "pgt-sent-" +
    me.id +
    "-" +
    new Date().toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" })
  );
}
function sentToday() {
  return Number(store(sentKey())) || 0;
}
function updateQueueButton() {
  const pending = run ? run.items.filter((i) => i.status === "pending").length : 0;
  const total = Math.max(queue.size, pending);
  $("#queue-button").classList.toggle("hidden", total === 0);
  $("#queue-count").textContent = total;
}
function openQueue() {
  if (!run || !run.items.some((i) => i.status === "pending")) {
    if (!queue.size) return toast("Marque empresas na lista para montar a fila.");
    run = {
      items: [...queue.values()].map((i) => ({ ...i, status: "pending" })),
      index: 0,
      pauseUntil: 0,
      lead: null,
    };
  } else {
    queue.forEach((i) => {
      if (!run.items.some((x) => x.id === i.id)) run.items.push({ ...i, status: "pending" });
    });
  }
  $("#queue-interval").value = store("pgt-queue-interval") || "3";
  $("#queue-dialog").showModal();
  clearInterval(queueTimer);
  queueTimer = setInterval(tickQueue, 1000);
  advanceQueue();
}
async function closeQueue() {
  clearInterval(queueTimer);
  if ($("#queue-dialog").open) $("#queue-dialog").close();
  if (run && !run.items.some((i) => i.status === "pending")) run = null;
  updateQueueButton();
  try {
    if (mode === "portfolio") await portfolio();
    else {
      $("#portfolio-count").textContent = (await api("/leads?mine=true")).length;
      if (leads.length) {
        leads = await api("/leads?ids=" + leads.map((e) => e.id).join(","));
        render();
      }
    }
  } catch {}
}
function advanceQueue() {
  if (!run) return;
  const next = run.items.findIndex((i) => i.status === "pending");
  run.index = next < 0 ? run.items.length : next;
  loadQueueItem();
}
async function loadQueueItem() {
  const item = run.items[run.index];
  run.lead = null;
  renderQueue();
  if (!item) return;
  try {
    const e = await api("/leads/" + item.id);
    if (!run || run.items[run.index] !== item) return;
    run.lead = e;
  } catch (error) {
    item.status = "skipped";
    item.note = error.message;
    queue.delete(item.id);
    return advanceQueue();
  }
  renderQueue();
}
function renderQueue() {
  const items = run.items;
  const done = items.filter((i) => i.status !== "pending").length;
  const item = items[run.index];
  const e = run.lead;
  const list =
    '<ol class="queue-list">' +
    items
      .map(
        (i, n) =>
          '<li class="' +
          i.status +
          (n === run.index ? " current" : "") +
          '"><span>' +
          esc(i.name) +
          "</span><small>" +
          (i.status === "sent" ? "Aberto" : i.status === "skipped" ? "Pulado" : "") +
          "</small></li>",
      )
      .join("") +
    "</ol>";
  const progress =
    '<div class="queue-progress"><div><strong>' +
    done +
    " de " +
    items.length +
    '</strong><span class="muted"> concluídas</span></div><div class="queue-bar"><i style="width:' +
    (items.length ? Math.round((done / items.length) * 100) : 0) +
    '%"></i></div></div>';
  let body;
  if (!item) {
    const sent = items.filter((i) => i.status === "sent").length;
    body =
      '<div class="queue-card queue-done"><h3>Fila concluída</h3><p class="muted">' +
      sent +
      " conversa(s) aberta(s) e " +
      (items.length - sent) +
      ' pulada(s). Registre o retorno de cada uma na ficha quando a pessoa responder.</p><button class="btn primary wide" id="queue-finish"><span>Concluir</span><span>✓</span></button></div>';
  } else if (!e) {
    body = '<div class="queue-card"><p class="muted">Carregando ' + esc(item.name) + "…</p></div>";
  } else {
    body =
      '<div class="queue-card"><div class="queue-lead"><div><h3>' +
      esc(e.name) +
      '</h3><p class="small muted">' +
      esc(e.categoryLabel) +
      " · " +
      esc(e.city || "Região selecionada") +
      '</p></div><strong class="queue-phone">' +
      esc(e.whatsapp || e.phone || "Sem número") +
      "</strong></div>" +
      (contactable(e)
        ? ""
        : '<p class="owner-note">Esta empresa não está mais disponível para você. Pule para a próxima.</p>') +
      '<label>Mensagem<textarea class="message-box" id="queue-message" maxlength="3000">' +
      esc(drafts.get(e.id) ?? e.message) +
      '</textarea></label><p class="small muted queue-wait" id="queue-wait"></p><div class="actions"><button class="btn" id="queue-skip">Pular</button><button class="btn primary" id="queue-open">Abrir WhatsApp ↗</button></div><p class="small muted queue-hint">O WhatsApp abre com o texto pronto. Confira e toque em enviar você mesmo.</p></div>';
  }
  $("#queue-body").innerHTML = progress + body + list;
  if ($("#queue-message"))
    $("#queue-message").oninput = () => drafts.set(e.id, $("#queue-message").value);
  if ($("#queue-skip"))
    $("#queue-skip").onclick = () => {
      item.status = "skipped";
      queue.delete(item.id);
      updateQueueButton();
      advanceQueue();
    };
  if ($("#queue-open")) $("#queue-open").onclick = () => queueOpen(item, e);
  if ($("#queue-finish")) $("#queue-finish").onclick = closeQueue;
  tickQueue();
}
function tickQueue() {
  if (!run) return;
  const today = sentToday();
  $("#queue-today").textContent =
    "Hoje: " + today + " de " + DAILY_LIMIT + " abordagens sugeridas por dia.";
  const button = $("#queue-open");
  if (!button) return;
  const wait = Math.max(0, Math.ceil((run.pauseUntil - Date.now()) / 1000));
  const blocked = !contactable(run.lead) || today >= DAILY_LIMIT || wait > 0;
  button.disabled = blocked || button.dataset.busy === "true";
  $("#queue-wait").textContent =
    today >= DAILY_LIMIT
      ? "Você chegou ao limite diário sugerido. Continue amanhã para manter seu número saudável."
      : wait > 0
        ? "Pausa antes da próxima: " +
          Math.floor(wait / 60) +
          ":" +
          String(wait % 60).padStart(2, "0") +
          ". Use o tempo para revisar a mensagem."
        : "";
}
async function queueOpen(item, e) {
  if (!run || run.lead !== e || Date.now() < run.pauseUntil || sentToday() >= DAILY_LIMIT) return;
  const text = $("#queue-message").value;
  drafts.set(e.id, text);
  const popup = window.open("about:blank", "_blank");
  if (popup) popup.opener = null;
  $("#queue-open").dataset.busy = "true";
  tickQueue();
  try {
    const result = await api("/leads/" + e.id + "/contact", {
      action: "OPEN_WHATSAPP",
      message: text,
    });
    if (!popup) {
      toast("Seu navegador bloqueou a nova aba. Permita popups e tente de novo.", true);
      return;
    }
    popup.location.href = result.url;
    item.status = "sent";
    queue.delete(item.id);
    store(sentKey(), sentToday() + 1);
    run.pauseUntil = Date.now() + Number($("#queue-interval").value) * 60000;
    updateQueueButton();
    advanceQueue();
  } catch (error) {
    popup?.close();
    toast(error.message, true);
  } finally {
    if ($("#queue-open")) delete $("#queue-open").dataset.busy;
    tickQueue();
  }
}
async function team() {
  ++navigation;
  busy(false);
  mode = "team";
  activateNav();
  closeLead();
  const users = await api("/admin/users");
  $("#team-list").innerHTML = users
    .map(
      (u) =>
        '<article class="team-card"><span class="avatar" aria-hidden="true">' +
        esc(u.name[0]) +
        "</span><div><h3>" +
        esc(u.name) +
        '</h3><p class="small muted">@' +
        esc(u.username) +
        " · " +
        (u.role === "ADMIN" ? "Gestor" : "Vendedor") +
        " · " +
        esc(u.specialty || "Perfil ainda não preenchido") +
        '</p></div><span class="pill green" style="margin-left:auto">' +
        (u.enabled ? "Ativo" : "Inativo") +
        "</span></article>",
    )
    .join("");
}
async function init() {
  try {
    [me, config] = await Promise.all([api("/me"), api("/config")]);
    regions = config.regions.map((city) => [city.label, city.lat, city.lon]);
    currentRegion = regions[0];
    $("#avatar")?.remove();
    $("#profile-button").textContent = me.name[0].toUpperCase();
    $("#region").innerHTML = regions
      .map((r, i) => '<option value="' + i + '">' + esc(r[0]) + "</option>")
      .join("");
    $("#category").innerHTML = Object.entries(config.categories)
      .map(([k, v]) => '<option value="' + k + '">' + esc(v) + "</option>")
      .join("");
    $("#category").value = "lojas";
    if (me.role === "ADMIN") $("#admin-nav").classList.remove("hidden");
    if (config.catalog.ready) {
      $("#source-note").innerHTML =
        '<span class="source-dot"></span><div><strong>Catálogo real · ' +
        esc(config.catalog.region || "regional") +
        "</strong><p>" +
        Number(config.catalog.records || 0).toLocaleString("pt-BR") +
        " registros OSM no recorte.<br>Contato e cobertura variam por região.</p></div>";
    } else {
      $("#source-note strong").textContent = "Catálogo local não preparado";
      $("#source-note p").textContent =
        "Buscas usam a fonte online, sujeita a indisponibilidade. Veja README para preparar o catálogo.";
    }
    initMap();
    const portfolioData = await api("/leads?mine=true");
    $("#portfolio-count").textContent = portfolioData.length;
    const suggested = /advog|jur[ií]d/i.test(me.specialty)
      ? "advocacia"
      : /doc|confeit|padar/i.test(me.specialty)
        ? "padarias"
        : /beleza|est[eé]t/i.test(me.specialty)
          ? "beleza"
          : null;
    if (suggested) {
      $("#profile-suggestion").classList.remove("hidden");
      $("#profile-suggestion").onclick = () => {
        $("#category").value = suggested;
        toast("Sugestão aplicada. Você pode escolher qualquer ramo.");
      };
    }
    $("#search-form").onsubmit = search;
    $("#region").onchange = () => {
      currentRegion = regions[Number($("#region").value)];
      if (map) {
        map.setView([currentRegion[1], currentRegion[2]], 14);
        centerMarker.setLatLng([currentRegion[1], currentRegion[2]]);
      }
    };
    $("#with-contact").onchange = render;
    $("#available-only").onchange = render;
    $("#list-view").onclick = () => setView("list");
    $("#map-view").onclick = () => setView("map");
    $("#toggle-filters").onclick = () => $("#filters").classList.toggle("open");
    $("#portfolio-nav").onclick = () => portfolio().catch((e) => toast(e.message, true));
    $("#explore-nav").onclick = () => {
      ++navigation;
      busy(false);
      mode = "explore";
      activateNav();
      closeLead();
      leads = [];
      render();
      $("#results-summary").textContent = "Escolha um ramo e uma região para buscar.";
    };
    $("#admin-nav").onclick = () => team().catch((e) => toast(e.message, true));
    $("#profile-button").onclick = () => {
      $("#profile-name").value = me.name;
      $("#profile-specialty").value = me.specialty;
      $("#profile-dialog").showModal();
    };
    $(".close-dialog").onclick = () => $("#profile-dialog").close();
    $("#queue-button").onclick = openQueue;
    $("#queue-close").onclick = closeQueue;
    $("#queue-dialog").addEventListener("cancel", (ev) => {
      ev.preventDefault();
      closeQueue();
    });
    $("#queue-interval").onchange = () => store("pgt-queue-interval", $("#queue-interval").value);
    $("#profile-form").onsubmit = async (ev) => {
      ev.preventDefault();
      try {
        me = await api("/me/profile", {
          name: $("#profile-name").value,
          specialty: $("#profile-specialty").value,
        });
        $("#profile-button").textContent = me.name[0].toUpperCase();
        $("#profile-dialog").close();
        toast("Perfil atualizado. Novas sugestões aparecem ao recarregar.");
      } catch (e) {
        toast(e.message, true);
      }
    };
    $("#password-form").onsubmit = async (ev) => {
      ev.preventDefault();
      try {
        await api("/me/password", {
          current: $("#current-password").value,
          password: $("#new-password").value,
        });
        $("#password-form").reset();
        toast("Senha atualizada.");
      } catch (e) {
        toast(e.message, true);
      }
    };
    $("#search-button").disabled = false;
    ["#region", "#category", "#radius"].forEach((selector) => {
      $(selector).disabled = false;
    });
  } catch (e) {
    toast(e.message, true);
  }
}
document.addEventListener("DOMContentLoaded", init);
