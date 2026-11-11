"use strict";
// Carregado no <head> para aplicar o tema antes da página aparecer (evita piscar).
(function () {
  const root = document.documentElement;
  let saved = null;
  try {
    saved = localStorage.getItem("pgt-theme");
  } catch {}
  root.dataset.theme = saved === "light" ? "light" : "dark";
  function label(button) {
    const light = root.dataset.theme === "light";
    button.textContent = light ? "☾" : "☀";
    button.setAttribute("aria-label", light ? "Usar tema escuro" : "Usar tema claro");
    button.title = button.getAttribute("aria-label");
  }
  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
      label(button);
      button.addEventListener("click", () => {
        root.dataset.theme = root.dataset.theme === "light" ? "dark" : "light";
        try {
          localStorage.setItem("pgt-theme", root.dataset.theme);
        } catch {}
        document.querySelectorAll("[data-theme-toggle]").forEach(label);
      });
    });
  });
})();
