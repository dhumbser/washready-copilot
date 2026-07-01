(function () {
  const af = window.authFetch;
  const escapeHtml = window.escapeHtml;

  const $ = (q) => document.querySelector(q);
  const editorContainer = $('#editorContainer'),
    form = $('#form'),
    toggle = $('#toggleForm'),
    cancel = $('#cancel'),
    tbody = $('#centros-tbody'),
    fEmpresa = $('#fEmpresa'),
    empresaId = $('#empresaId'),
    id = $('#id'),
    nombre = $('#nombre'),
    direccion = $('#direccion'),
    ciudad = $('#ciudad'),
    codigoPostal = $('#codigoPostal'),
    correo = $('#correo'),
    telefono = $('#telefono'),
    maxDevices = $('#maxDevices'),
    mostrarLogoTicket = $('#mostrarLogoTicket'),
    centerDevicesBody = $('#centerDevicesBody'),
    activeCountHint = $('#activeCountHint'),
    btnUpdateLimit = $('#btnUpdateLimit'),
    refreshDevicesBtn = $('#refreshDevices'),
    closeDevicesTabBtn = $('#closeDevicesTab');

  const tabLinks = document.querySelectorAll('.tab-link');
  const tabContents = document.querySelectorAll('.tab-content');
  const btnTabDevices = $('#btnTabDevices');

  function switchTab(tabId) {
    tabLinks.forEach(t => {
      if (t.dataset.tab === tabId) t.classList.add('active');
      else t.classList.remove('active');
    });
    tabContents.forEach(c => {
      if (c.id === tabId) c.classList.add('active');
      else c.classList.remove('active');
    });
  }

  function showEditor(isNewMode) {
    editorContainer.style.display = 'block';
    toggle.style.display = 'none';
    switchTab('tab-data');

    if (isNewMode) {
      btnTabDevices.style.display = 'none';
    } else {
      btnTabDevices.style.display = '';
      btnTabDevices.removeAttribute('disabled');
      btnTabDevices.title = "";
    }
  }

  function hideEditor() {
    editorContainer.style.display = 'none';
    toggle.style.display = 'inline-block';
    btnTabDevices.style.display = '';
    form.reset();
    id.value = '';
    activeCountHint.textContent = '';
    tbody.querySelectorAll('.selected-row').forEach(tr => tr.classList.remove('selected-row'));
  }

  function centerPayload() {
    return {
      nombre: nombre.value.trim(), direccion: direccion.value.trim() || null, ciudad: ciudad.value.trim() || null,
      codigoPostal: codigoPostal.value.trim() || null, correo: correo.value.trim() || null, telefono: telefono.value.trim() || null,
      maxDevices: maxDevices.value === '' ? null : Number(maxDevices.value),
      mostrarLogoTicket: !!mostrarLogoTicket.checked
    };
  }

  async function saveCenter() {
    if (!empresaId.value) { setMsg('Empresa es obligatoria', 'error'); return; }
    const body = JSON.stringify(centerPayload());
    const edit = !!id.value;
    const url = edit
      ? `/api/centros/${id.value}` + (empresaId.value ? `?empresaId=${empresaId.value}` : '')
      : `/api/centros?empresaId=${empresaId.value}`;
    const method = edit ? 'PUT' : 'POST';
    const res = await af(url, { method, headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }, body });

    if (!res.ok) {
      let errorMsg = 'Error guardando';
      try {
        const json = await res.json();
        if (json.message) errorMsg = json.message;
      } catch (e) {
        errorMsg = await res.text() || errorMsg;
      }
      setMsg(errorMsg, 'error');
      return false;
    }
    setMsg(edit ? 'Centro actualizado' : 'Centro creado', 'success');
    if (!edit) hideEditor();
    loadCentros();
    return true;
  }

  async function loadEmpresas(selectEl) {
    const res = await af('/api/empresas', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) { setMsg('No se pudieron cargar empresas (' + res.status + ')', 'error'); return []; }
    const list = await res.json();
    selectEl.innerHTML = `<option value="">— Selecciona —</option>`
      + list.map(e => `<option value="${e.id}">${escapeHtml(e.nombre)}</option>`).join('');
    return list;
  }

  async function loadCentros() {
    const q = fEmpresa.value ? `?empresaId=${fEmpresa.value}` : '';
    const res = await af('/api/centros' + q, { headers: { 'Accept': 'application/json' } });
    if (!res.ok) { setMsg('No se pudieron cargar centros (' + res.status + ')', 'error'); return; }
    renderRows(await res.json());
  }

  function scrollToForm() {
    editorContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function maxDevicesLabel(active, max) {
    const BAR = (pct, color) =>
      `<div style="background:#e5e7eb;border-radius:999px;height:6px;margin-bottom:5px;overflow:hidden;">` +
      `<div style="width:${pct}%;height:100%;background:${color};border-radius:999px;transition:width .3s;"></div></div>`;

    if (max === 0) return BAR(100, '#ef4444') + '<span class="pill warn">Bloqueado</span>';
    if (max == null) return `<span class="pill">${active} activos</span> <span class="muted" style="font-size:0.8rem;">· sin límite</span>`;

    const ratio  = active / max;
    const pct    = Math.min(ratio * 100, 100).toFixed(1);
    const warnAt = max <= 5 ? max - 1 : max * 0.8;
    const color  = ratio >= 1 ? '#ef4444' : active >= warnAt ? '#f59e0b' : '#818cf8';
    const cls    = ratio >= 1 ? 'pill danger' : active >= warnAt ? 'pill warn' : 'pill';

    return BAR(pct, color)
      + `<span class="${cls}">${active} activos</span>`
      + ` <span class="muted" style="font-size:0.8rem;">· límite ${max}</span>`;
  }

  let dt = null;
  let centrosList = [];

  function initDataTable() {
    if (dt) return;

    dt = jQuery('#centros-table').DataTable({
      pageLength: 15,
      lengthMenu: [10, 15, 25, 50, 100],
      ordering: true,
      searching: false,
      autoWidth: false,
      language: {
        url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json",
        infoEmpty: "Sin registros",
        emptyTable: "No hay centros registrados"
      },
      columns: [
        { data: "nombre", render: (v) => `<strong>${escapeHtml(v || '')}</strong>` },
        { data: "empresaNombre", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "direccion", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "ciudad", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "codigoPostal", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "telefono", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "correo", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "maxDevices", render: (v, _type, row) => maxDevicesLabel(row.activeDevices ?? 0, v) },
        {
          data: null,
          orderable: false,
          className: "col-actions",
          render: (row) => `<button class="secondary edit" data-id="${row.id}">Editar</button>`
        }
      ],
      initComplete: function() { window.dtMoveLengthControl('centros-table'); }
    });
  }

  function renderRows(list) {
    centrosList = list || [];
    initDataTable();
    dt.clear();
    dt.rows.add(centrosList);
    dt.draw();
  }

  function formatDate(value) {
    if (!value) return '—';
    const d = new Date(value);
    return isNaN(d.getTime()) ? value : d.toLocaleString();
  }

  async function loadCenterDevices(centroIdVal) {
    centerDevicesBody.innerHTML = '';
    if (!centroIdVal) return;
    const res = await af(`/api/centros/${centroIdVal}/devices`, { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
      setMsg('No se pudieron cargar dispositivos del centro (' + res.status + ')', 'error');
      return;
    }
    const list = await res.json();

    if (activeCountHint) {
      const activeCount = list.filter(d => !d.revoked).length;
      activeCountHint.textContent = `(Actuales: ${activeCount})`;
    }

    centerDevicesBody.innerHTML = list.map(d => `
      <tr>
        <td>
           <div>${escapeHtml(d.deviceId ?? '')}</div>
           ${!d.revoked ? `
           <div style="margin-top:4px; display:flex; gap:6px; align-items:center;">
              <input type="text" class="alias-input" data-device="${escapeHtml(d.deviceId)}" value="${escapeHtml(d.alias || '')}" placeholder="Añadir alias" style="padding:4px 8px; font-weight:bold; font-size:0.9rem; width:100%; max-width:180px; border:1px solid #ccc; border-radius:4px;">
              <button type="button" class="secondary small save-alias" data-device="${escapeHtml(d.deviceId)}" style="display:none; padding:4px 8px; font-size:0.8rem;">Guardar</button>
           </div>` : ''}
        </td>
        <td>${escapeHtml((d.usuarios || []).join(', ')) || '—'}</td>
        <td>${formatDate(d.lastSeenAt)}</td>
        <td>${d.revoked ? '<span class="pill warn">Revocado</span>' : '<span class="pill ok">Activo</span>'}</td>
        <td style="text-align:right;">
          ${!d.revoked
            ? `<button type="button" class="secondary warn center-revoke" data-device="${escapeHtml(d.deviceId)}" style="background:#fee; color:#900; border:1px solid #f99;">Revocar</button>`
            : `<div style="display:flex; gap:6px; justify-content:flex-end;">
                 <button type="button" class="secondary center-reactivate" data-device="${escapeHtml(d.deviceId)}">Reactivar</button>
                 <button type="button" class="secondary center-delete" data-device="${escapeHtml(d.deviceId)}" style="background:#fee; color:#900; border:1px solid #f99;">Eliminar</button>
               </div>`}
        </td>
      </tr>
    `).join('');
  }

  async function init() {
    tabLinks.forEach(link => {
      link.addEventListener('click', () => {
        if (link.hasAttribute('disabled')) return;
        switchTab(link.dataset.tab);
      });
    });

    toggle.addEventListener('click', async () => {
      setMsg('');
      form.reset(); id.value = '';
      mostrarLogoTicket.checked = true;
      activeCountHint.textContent = '';
      showEditor(true);
    });

    cancel.addEventListener('click', () => {
      setMsg('');
      hideEditor();
    });

    closeDevicesTabBtn.addEventListener('click', () => {
      setMsg('');
      hideEditor();
    });

    fEmpresa.addEventListener('change', loadCentros);

    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      const success = await saveCenter();
      if (success) hideEditor();
    });

    btnUpdateLimit.addEventListener('click', async () => {
      if (!id.value) return;
      const ok = await saveCenter();
      if (ok) setMsg('Límite de dispositivos actualizado', 'success');
    });

    refreshDevicesBtn.addEventListener('click', () => {
      if (id.value) loadCenterDevices(id.value);
    });

    centerDevicesBody.addEventListener('input', (ev) => {
      if (ev.target.classList.contains('alias-input')) {
        const btn = ev.target.nextElementSibling;
        if (btn && btn.classList.contains('save-alias')) btn.style.display = 'inline-block';
      }
    });

    centerDevicesBody.addEventListener('click', async (ev) => {
      const t = ev.target;
      if (!id.value) return;

      if (t.classList.contains('save-alias')) {
        const device = t.dataset.device;
        const input = t.previousElementSibling;
        const newAlias = input.value.trim();

        const res = await af(`/api/centros/${id.value}/devices/${device}/alias`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ alias: newAlias })
        });

        if (!res.ok) {
          setMsg('No se pudo actualizar el nombre', 'error');
          return;
        }

        t.style.display = 'none';
        setMsg('Alias guardado', 'success');
        input.setAttribute('value', newAlias);
        return;
      }

      if (t.classList.contains('center-revoke')) {
        const device = t.dataset.device;
        if (!confirm('¿Revocar acceso de este dispositivo para todos los usuarios del centro?')) return;

        const res = await af(`/api/centros/${id.value}/devices/${encodeURIComponent(device)}/revoke`, { method: 'PUT' });
        if (!res.ok) {
          const txt = await res.text();
          setMsg(txt || 'Error al revocar', 'error');
          return;
        }
        setMsg('Dispositivo revocado', 'success');
        loadCenterDevices(id.value);
        return;
      }

      if (t.classList.contains('center-reactivate')) {
        const device = t.dataset.device;
        const res = await af(`/api/centros/${id.value}/devices/${encodeURIComponent(device)}/reactivate`, { method: 'PUT' });
        if (!res.ok) {
          let errMsg = 'Error al reactivar';
          try { const j = await res.json(); if (j.message) errMsg = j.message; } catch (_) {}
          setMsg(errMsg, 'error');
          return;
        }
        setMsg('Dispositivo reactivado', 'success');
        loadCenterDevices(id.value);
        return;
      }

      if (t.classList.contains('center-delete')) {
        const device = t.dataset.device;
        if (!confirm(`¿Eliminar definitivamente el dispositivo "${device}"? Esta acción no se puede deshacer.`)) return;
        const res = await af(`/api/centros/${id.value}/devices/${encodeURIComponent(device)}`, { method: 'DELETE' });
        if (!res.ok) {
          let errMsg = 'Error al eliminar';
          try { const j = await res.json(); if (j.message) errMsg = j.message; } catch (_) {}
          setMsg(errMsg, 'error');
          return;
        }
        setMsg('Dispositivo eliminado definitivamente', 'success');
        loadCenterDevices(id.value);
        return;
      }
    });

    tbody.addEventListener('click', async ev => {
      const t = ev.target; if (!t.closest) return;

      if (t.classList.contains('edit')) {
        const tr = t.closest('tr');
        if (tr) {
          tbody.querySelectorAll('.selected-row').forEach(r => r.classList.remove('selected-row'));
          tr.classList.add('selected-row');
        }

        const res = await af(`/api/centros/${t.dataset.id}`, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) { setMsg('No se pudo cargar el centro', 'error'); return; }
        const c = await res.json();
        if (!empresaId.options.length) await loadEmpresas(empresaId);
        empresaId.value = c.empresaId || '';
        id.value = c.id; nombre.value = c.nombre || ''; direccion.value = c.direccion || '';
        ciudad.value = c.ciudad || ''; codigoPostal.value = c.codigoPostal || ''; correo.value = c.correo || ''; telefono.value = c.telefono || '';
        maxDevices.value = c.maxDevices ?? '';
        mostrarLogoTicket.checked = (c.mostrarLogoTicket !== false);

        activeCountHint.textContent = '';
        showEditor(false);
        setMsg('');

        await loadCenterDevices(c.id);
        scrollToForm();
      }

    });

    await loadEmpresas(fEmpresa);
    await loadCentros();
  }

  init();
})();
