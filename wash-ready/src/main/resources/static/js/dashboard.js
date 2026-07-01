(function () {
  const af = window.authFetch;
  const escapeHtml = window.escapeHtml;

  const $ = s => document.querySelector(s);
  const fmtEur = n => (Number(n || 0)).toFixed(2).replace('.', ',') + ' €';
  const pad = n => String(n).padStart(2, '0');
  const toDateStr = d => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
  const toMonthId = d => `${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
  const toMonthLabel = d => {
    const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    return `${meses[d.getMonth()]} ${d.getFullYear()}`;
  };

  const getCentroSel = () => { const el = $('#selCentro'); return el ? (el.value || '').trim() : ''; };

  async function loadCentros() {
    const sel = $('#selCentro'); if (!sel) return;
    try {
      const r = await af('/api/centros', { headers: { Accept: 'application/json' } });
      if (!r.ok) return;
      const data = await r.json(); const list = Array.isArray(data) ? data : (data.content || []);
      sel.innerHTML = `<option value="">Todos</option>`
        + list.map(c => `<option value="${c.id}">${escapeHtml(c.nombre || ('Centro ' + c.id))}</option>`).join('');
    } catch { }
  }

  async function loadGastoMensual() {
    const now = new Date();
    const desde = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    const hasta = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const qs = new URLSearchParams({ desde, hasta });
    const centroId = getCentroSel();
    if (centroId) qs.set('centroId', centroId);
    try {
      const r = await af('/api/informes/resumen?' + qs.toString(), { headers: { Accept: 'application/json' } });
      if (!r.ok) return;
      const d = await r.json();
      const k = d.kpis || d.resumen || d || {};
      let base = Number(k.totalBase ?? k.base ?? k.totalSinIva ?? 0);
      let total = Number(k.totalConIva ?? k.total ?? 0);
      const iva = (k.totalIva != null) ? Number(k.totalIva) : (total - base);
      const tickets = Number(k.totalTickets ?? k.tickets ?? 0);
      $('#k_base').textContent = fmtEur(base);
      $('#k_iva').textContent = fmtEur(iva);
      $('#k_total').textContent = fmtEur(total);
      const kt = $('#k_tickets_mes');
      if (kt) kt.textContent = String(tickets);
    } catch { }
  }

  async function loadFacturacionDia() {
    const centroId = getCentroSel();
    const qs = new URLSearchParams();
    if (centroId) qs.set('centroId', centroId);
    const tb = $('#tbFacturacionDia'); tb.innerHTML = `<tr><td colspan="4" class="muted">Cargando…</td></tr>`;
    try {
      const r = await af('/api/admin/dashboard/facturacion-dia?' + qs.toString(), { headers: { Accept: 'application/json' } });
      if (!r.ok) { tb.innerHTML = `<tr><td colspan="4" class="muted">No se pudo cargar</td></tr>`; return; }
      const rows = await r.json();
      if (!rows.length) { tb.innerHTML = `<tr><td colspan="4" class="muted">Sin datos</td></tr>`; return; }
      tb.innerHTML = '';
      const fecha = toDateStr(new Date());
      rows.forEach(t => {
        const cen = t.centroNombre || '—';
        const tickets = Number(t.tickets ?? 0);
        const total = (t.total != null) ? fmtEur(t.total) : '—';
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${fecha}</td><td>${escapeHtml(cen)}</td><td>${tickets}</td><td>${total}</td>`;
        tb.appendChild(tr);
      });
    } catch { tb.innerHTML = `<tr><td colspan="4" class="muted">No se pudo cargar</td></tr>`; }
  }

  async function loadFacturacionMes() {
    const centroId = getCentroSel();
    const qs = new URLSearchParams();
    if (centroId) qs.set('centroId', centroId);
    const tb = $('#tbFacturacionMes'); tb.innerHTML = `<tr><td colspan="4" class="muted">Cargando…</td></tr>`;
    try {
      const r = await af('/api/admin/dashboard/facturacion-mes?' + qs.toString(), { headers: { Accept: 'application/json' } });
      if (!r.ok) { tb.innerHTML = `<tr><td colspan="4" class="muted">No se pudo cargar</td></tr>`; return; }
      const rows = await r.json();
      if (!rows.length) { tb.innerHTML = `<tr><td colspan="4" class="muted">Sin datos</td></tr>`; return; }
      tb.innerHTML = '';
      const now = new Date();
      const fecha = toMonthId(new Date(now.getFullYear(), now.getMonth(), 1));
      const factMesLabel = document.getElementById('factMesLabel');
      if (factMesLabel) factMesLabel.textContent = toMonthLabel(now);
      rows.forEach(c => {
        const cen = c.centroNombre || '—';
        const tickets = Number(c.tickets ?? 0);
        const total = (c.total != null) ? fmtEur(c.total) : '—';
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${fecha}</td><td>${escapeHtml(cen)}</td><td>${tickets}</td><td>${total}</td>`;
        tb.appendChild(tr);
      });
    } catch { tb.innerHTML = `<tr><td colspan="4" class="muted">No se pudo cargar</td></tr>`; }
  }

  const MAX_SOL = 5;

  function renderSolicitudCards(list, rows, expanded) {
    list.innerHTML = '';
    const visible = expanded ? rows : rows.slice(0, MAX_SOL);
    visible.forEach(s => {
      const d = new Date(s.creadoAt);
      const fecha = pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear()
        + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
      const badge = s.tipo === 'ANULACION'
        ? `<span class="badge-pending" style="color:#7f1d1d;background:#fef2f2;border-color:#fca5a5;">Anulación</span>`
        : `<span class="badge-pending">No deseado</span>`;
      const card = document.createElement('div');
      card.className = 'sol-card';
      card.innerHTML = `
        <div class="sol-card-head">
          <span class="sol-card-ref">${badge}&ensp;${escapeHtml(s.referencia)}</span>
          <span class="sol-card-date">${fecha}</span>
        </div>
        <div class="sol-card-meta">${escapeHtml(s.centroNombre || '—')}</div>
        ${s.motivo ? `<div class="sol-card-motivo">${escapeHtml(s.motivo)}</div>` : ''}
        <div class="sol-card-actions">
          <button class="sol-btn secondary" style="padding:6px 12px;font-size:.85em;" data-accion="RECHAZAR" data-tipo="${s.tipo}" data-id="${s.id}" data-token="${s.token}">Rechazar</button>
          <button class="sol-btn" style="padding:6px 12px;font-size:.85em;background:#0a7a0a;" data-accion="APROBAR" data-tipo="${s.tipo}" data-id="${s.id}" data-token="${s.token}">Aprobar</button>
        </div>`;
      list.appendChild(card);
    });
    if (!expanded && rows.length > MAX_SOL) {
      const btn = document.createElement('button');
      btn.className = 'secondary';
      btn.style.cssText = 'width:100%;margin-top:4px;font-size:.85em;';
      btn.textContent = `Ver todas · ${rows.length}`;
      btn.addEventListener('click', () => renderSolicitudCards(list, rows, true));
      list.appendChild(btn);
    }
  }

  async function loadSolicitudes() {
    const list = $('#solList'); if (!list) return;
    list.innerHTML = `<div class="sol-empty">Cargando…</div>`;
    try {
      const [rA, rN] = await Promise.all([
        af('/api/tickets/anulaciones/pendientes'),
        af('/api/clientes/no-deseado/pendientes')
      ]);
      const anulaciones = rA.ok ? (await rA.json()).map(a => ({
        tipo: 'ANULACION',
        referencia: a.referencia || ('#' + a.ticketId),
        centroNombre: a.centroNombre,
        motivo: a.motivo,
        creadoAt: a.creadoAt,
        id: a.ticketId,
        token: a.token
      })) : [];
      const noDeseados = rN.ok ? (await rN.json()).map(a => ({
        tipo: 'NO_DESEADO',
        referencia: a.clienteNombre || '—',
        centroNombre: a.centroNombre,
        motivo: a.motivo,
        creadoAt: a.creadoAt,
        id: a.solicitudId,
        token: a.token
      })) : [];

      const rows = [...anulaciones, ...noDeseados]
        .sort((a, b) => new Date(b.creadoAt) - new Date(a.creadoAt));

      const countEl = $('#solCount');
      if (countEl) { countEl.textContent = rows.length || ''; countEl.style.display = rows.length ? '' : 'none'; }

      if (!rows.length) { list.innerHTML = `<div class="sol-empty">No hay solicitudes pendientes</div>`; return; }

      renderSolicitudCards(list, rows, false);
    } catch { list.innerHTML = `<div class="sol-empty">No se pudo cargar</div>`; }
  }

  (async function init() {
    const r = await af('/auth/me', { headers: { Accept: 'application/json' } });
    if (r.status === 401) { location = '/login.html'; return; }
    const me = await r.json();
    $('#centro').textContent = me.centroTrabajo || '—';
    if (me.role !== 'ROLE_ADMIN') { location = '/'; return; }

    $('#selCentro')?.addEventListener('change', () => {
      loadGastoMensual();
      loadFacturacionDia();
      loadFacturacionMes();
    });

    $('#solList')?.addEventListener('click', async (ev) => {
      const btn = ev.target.closest('.sol-btn');
      if (!btn || btn.disabled) return;
      const { accion, tipo, id, token } = btn.dataset;
      const confirmMsg = accion === 'APROBAR'
        ? (tipo === 'ANULACION' ? '¿Aprobar la anulación del ticket?' : '¿Aprobar y marcar al cliente como no deseado?')
        : '¿Rechazar esta solicitud?';
      if (!confirm(confirmMsg)) return;

      btn.disabled = true;
      const url = tipo === 'ANULACION'
        ? `/api/tickets/anulaciones/${id}/resolver`
        : `/api/clientes/no-deseado/${id}/resolver`;
      try {
        const r = await af(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token, accion })
        });
        if (r.ok) {
          setMsg(`Solicitud ${accion === 'APROBAR' ? 'aprobada' : 'rechazada'}`, 'success');
          loadSolicitudes();
          if (tipo === 'ANULACION') { loadFacturacionDia(); loadFacturacionMes(); loadGastoMensual(); }
        } else {
          setMsg('No se pudo resolver la solicitud', 'error');
          btn.disabled = false;
        }
      } catch {
        setMsg('Error de red', 'error');
        btn.disabled = false;
      }
    });

    document.getElementById('logout')?.addEventListener('click', async () => {
      try { await fetch('/auth/logout', { method: 'POST' }); } catch { }
      localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token');
      location = '/login.html';
    });

    await loadCentros();
    await loadGastoMensual();
    await Promise.all([loadFacturacionDia(), loadFacturacionMes(), loadSolicitudes()]);
    document.querySelector('main').style.opacity = '1';
  })();
})();
