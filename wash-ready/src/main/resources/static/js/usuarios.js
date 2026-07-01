(function () {
  const af = window.authFetch;
  const escapeHtml = window.escapeHtml;

  const $ = (q) => document.querySelector(q);

  const editorContainer = $('#editorContainer'),
    form = $('#form'),
    toggle = $('#toggleForm'),
    cancelBtn = $('#cancel'),
    closeDevicesTabBtn = $('#closeDevicesTab'),
    tbody = $('#usuarios-tbody');

  const tabLinks = document.querySelectorAll('.tab-link');
  const tabContents = document.querySelectorAll('.tab-content');
  const btnTabDevices = $('#btnTabDevices');

  const id = $('#id'),
    usuario = $('#usuario'),
    password = $('#password'),
    role = $('#role'),
    empresaId = $('#empresaId'),
    centroId = $('#centroId');
  const disabledFrom = $('#disabledFrom'),
    disableNowBtn = $('#disableNow'),
    reactivateUserBtn = $('#reactivateUser');

  const userDevicesBody = $('#userDevicesBody'),
    refreshDevicesBtn = $('#refreshDevices');

  let allUsers = [];
  let originalDisabledFrom = null;

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
    tbody.querySelectorAll('.selected-row').forEach(tr => tr.classList.remove('selected-row'));
  }

  function scrollToForm() {
    if (editorContainer.style.display !== 'none') {
      editorContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function formatDate(value) {
    if (!value) return '-';
    const d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function toInputDateTime(value) {
    if (!value) return '';
    const d = new Date(value);
    if (!isNaN(d.getTime())) {
      const offsetMs = d.getTimezoneOffset() * 60000;
      return new Date(d.getTime() - offsetMs).toISOString().slice(0, 16);
    }
    return String(value).slice(0, 16);
  }

  function toBackendDateTime(value) {
    if (!value) return null;
    return value.length === 16 ? `${value}:00` : value;
  }

  function nowInputDateTime() {
    const d = new Date();
    d.setSeconds(0, 0);
    const offsetMs = d.getTimezoneOffset() * 60000;
    return new Date(d.getTime() - offsetMs).toISOString().slice(0, 16);
  }

  function renderAccessStatus(u) {
    if (u.disabledNow) return '<span class="pill warn">Deshabilitado</span>';
    if (u.disabledFrom) return `<span class="pill pending" title="${escapeHtml(formatDate(u.disabledFrom))}">Baja programada</span>`;
    return '<span class="pill ok">Activo</span>';
  }

  async function loadEmpresas(selectEl) {
    const res = await af('/api/empresas', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
      setMsg('No se pudieron cargar empresas (' + res.status + ')', 'error');
      selectEl.innerHTML = `<option value="">- Ninguna -</option>`;
      return [];
    }
    const list = await res.json();
    selectEl.innerHTML = `<option value="">- Ninguna -</option>`
      + list.map(e => `<option value="${e.id}">${escapeHtml(e.nombre)}</option>`).join('');
    return list;
  }

  async function loadCentrosForEmpresa(empId, selectEl) {
    selectEl.innerHTML = `<option value="">- Ninguno -</option>`;
    if (!empId) return;

    const res = await af(`/api/centros?empresaId=${empId}`, { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
      setMsg('No se pudieron cargar centros (' + res.status + ')', 'error');
      return;
    }
    const list = await res.json();
    selectEl.innerHTML = `<option value="">- Ninguno -</option>`
      + list.map(c => `<option value="${c.id}">${escapeHtml(c.nombre)}</option>`).join('');
  }

  let dt = null;

  function initDataTable() {
    if (dt) return;

    dt = jQuery('#usuarios-table').DataTable({
      pageLength: 15,
      lengthMenu: [10, 15, 25, 50, 100],
      ordering: true,
      searching: false,
      autoWidth: false,
      language: {
        url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json",
        infoEmpty: "Sin registros",
        emptyTable: "No hay usuarios registrados"
      },
      columns: [
        { data: "usuario", render: (v) => `<strong>${escapeHtml(v ?? '')}</strong>` },
        { data: "role", render: (v) => escapeHtml(v ?? '') },
        { data: "empresaNombre", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "centroNombre", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: null, render: (row) => renderAccessStatus(row) },
        { data: "disabledFrom", render: (v) => formatDate(v) },
        {
          data: null,
          render: (row) => row.role === 'ROLE_ADMIN'
            ? '<span class="muted">—</span>'
            : `<span class="pill" title="Activos">${row.activeDevices ?? 0}</span>
               <span style="margin:0 4px; color:#ccc;">/</span>
               <span class="pill warn" title="Revocados">${row.revokedDevices ?? 0}</span>`
        },
        {
          data: null,
          orderable: false,
          className: "col-actions",
          render: (row) => `<button class="secondary edit" data-id="${row.id}">Editar</button>`
        }
      ],
      initComplete: function() { window.dtMoveLengthControl('usuarios-table'); }
    });
  }

  function renderRows(list) {
    initDataTable();
    dt.clear();
    dt.rows.add(list || []);
    dt.draw();
  }

  async function loadUsers() {
    const res = await af('/api/users', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
      setMsg('No se pudieron cargar usuarios (' + res.status + ')', 'error');
      return;
    }
    allUsers = await res.json();
    renderRows(allUsers);
  }

  async function loadUserDevices(userIdVal) {
    userDevicesBody.innerHTML = '<tr><td colspan="3" style="text-align:center; color:#888;">Cargando...</td></tr>';
    if (!userIdVal) return;

    const res = await af(`/api/users/${userIdVal}/devices`, { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
      userDevicesBody.innerHTML = '';
      setMsg('No se pudieron cargar dispositivos del usuario (' + res.status + ')', 'error');
      return;
    }

    const list = await res.json();
    if (list.length === 0) {
      userDevicesBody.innerHTML = '<tr><td colspan="3" style="text-align:center; color:#888;">No hay dispositivos vinculados</td></tr>';
      return;
    }

    userDevicesBody.innerHTML = list.map(d => `
      <tr>
        <td>${escapeHtml(d.deviceId ?? '')}</td>
        <td>${formatDate(d.lastSeenAt)}</td>
        <td>${d.revokedAt ? '<span class="pill warn">Revocado</span>' : '<span class="pill">Activo</span>'}</td>
      </tr>
    `).join('');
  }

  async function init() {
    const fp = flatpickr(disabledFrom, {
      enableTime: true,
      time_24hr: true,
      locale: 'es',
      dateFormat: 'Y-m-dTH:i',
      disableMobile: true
    });

    tabLinks.forEach(link => {
      link.addEventListener('click', () => {
        if (link.hasAttribute('disabled')) return;
        switchTab(link.dataset.tab);
      });
    });

    toggle.addEventListener('click', async () => {
      setMsg('');
      form.reset();
      id.value = '';
      originalDisabledFrom = null;
      usuario.disabled = false;
      password.value = '';
      fp.clear();
      if (!empresaId.options.length) await loadEmpresas(empresaId);
      await loadCentrosForEmpresa(empresaId.value, centroId);
      showEditor(true);
    });

    cancelBtn.addEventListener('click', () => {
      setMsg('');
      hideEditor();
    });

    closeDevicesTabBtn.addEventListener('click', () => {
      setMsg('');
      hideEditor();
    });

    empresaId.addEventListener('change', (e) => {
      loadCentrosForEmpresa(e.target.value, centroId);
    });

    disableNowBtn.addEventListener('click', () => {
      fp.setDate(new Date(), false);
    });

    reactivateUserBtn.addEventListener('click', () => {
      fp.clear();
    });

    form.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const isEdit = !!id.value;

      if (!isEdit && (!usuario.value.trim() || !password.value.trim())) {
        setMsg('Usuario y contraseña son obligatorios al crear', 'error');
        return;
      }

      if (role.value === 'ROLE_USER' && !centroId.value) {
        setMsg('Un usuario con rol ROLE_USER debe tener un centro asignado', 'error');
        return;
      }

      const disabledFromValue = toBackendDateTime(disabledFrom.value);

      if (isEdit && disabledFromValue && new Date(disabledFromValue) <= new Date()) {
        if (!confirm(`¿Deshabilitar a "${usuario.value}" de forma inmediata?\nSu sesión activa quedará invalidada.`)) return;
      }

      const payload = {
        usuario: usuario.value.trim() || null,
        password: password.value || null,
        role: role.value || 'ROLE_USER',
        empresaId: empresaId.value ? Number(empresaId.value) : null,
        centroId: centroId.value ? Number(centroId.value) : null,
        disabledFrom: disabledFromValue,
        enabled: (isEdit && !disabledFromValue && !!originalDisabledFrom) ? true : null,
        clearCompany: !empresaId.value
      };

      const url = isEdit ? `/api/users/${id.value}` : '/api/users';
      const method = isEdit ? 'PUT' : 'POST';

      const res = await af(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const txt = await res.text();
        setMsg(txt || 'Error guardando', 'error');
        return;
      }

      setMsg(isEdit ? 'Usuario actualizado' : 'Usuario creado', 'success');
      hideEditor();
      loadUsers();
    });

    refreshDevicesBtn.addEventListener('click', () => {
      if (id.value) loadUserDevices(id.value);
    });

    tbody.addEventListener('click', async (ev) => {
      const t = ev.target;

      if (t.classList.contains('edit')) {
        const tr = t.closest('tr');
        if (tr) {
          tbody.querySelectorAll('.selected-row').forEach(r => r.classList.remove('selected-row'));
          tr.classList.add('selected-row');
        }

        const res = await af(`/api/users/${t.dataset.id}`, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) {
          setMsg('No se pudo cargar el usuario', 'error');
          return;
        }

        const u = await res.json();

        if (!empresaId.options.length) await loadEmpresas(empresaId);
        await loadCentrosForEmpresa(u.empresaId, centroId);

        id.value = u.id ?? '';
        usuario.value = u.usuario || '';
        usuario.disabled = true;
        role.value = u.role || 'ROLE_USER';
        empresaId.value = u.empresaId || '';
        centroId.value = u.centroId || '';
        password.value = '';
        originalDisabledFrom = u.disabledFrom || null;
        fp.setDate(toInputDateTime(u.disabledFrom) || null, false);

        userDevicesBody.innerHTML = '';
        showEditor(false);
        btnTabDevices.style.display = u.role === 'ROLE_ADMIN' ? 'none' : '';
        setMsg('');
        scrollToForm();

        if (u.id && u.role !== 'ROLE_ADMIN') {
          loadUserDevices(u.id);
        }
      }
    });

    await loadUsers();
  }

  init();
})();
