(function () {
    // authFetch lo expone globalmente app.js (añade el Bearer token,
    // refresca la sesión en 401 y redirige a login en 403).
    const $ = (q) => document.querySelector(q);
    const escapeHtml = window.escapeHtml;

    const q = $('#q');
    const clienteModal = $('#clienteModal'), formTitle = $('#formTitle');
    const formMsg = $('#formMsg');
    const id = $('#id'), nombre = $('#nombre'), apellido = $('#apellido'), direccion = $('#direccion'),
        codigoPostal = $('#codigoPostal'), nif = $('#nif'), telefono = $('#telefono'),
        correo = $('#correo'), noDeseado = $('#noDeseado'), ndField = $('#ndField');
    const telWarn = $('#telWarn');
    const vehSection = $('#vehSection'), vehBody = $('#vehiculos');
    const ndModal = $('#ndModal'), ndForm = $('#ndForm'), ndMotivo = $('#ndMotivo'),
        ndCancel = $('#ndCancel'), ndClose = $('#ndClose'), ndMsg = $('#ndMsg'), ndSubmit = $('#ndSubmit');
    const ndPendingInfo = $('#ndPendingInfo');
    const btnNew = $('#btnNew'), btnSave = $('#btnSave'), btnCancel = $('#btnCancel');
    const fEstado = $('#fEstado'), btnClear = $('#btnClear');

    let isAdmin = false;
    let currentNoDeseado = false;
    let pendingNdMotivo = '';
    const state = { filters: { q: '', noDeseado: null } };
    let dt = null;

    // ---- Mensajes ----
    function setFormMsg(t, type = 'success') {
        if (!formMsg) return;
        formMsg.textContent = t || ''; formMsg.className = 'alert ' + type;
        formMsg.style.display = t ? 'block' : 'none';
    }
    function setNdMsg(t, type = 'success') {
        if (!ndMsg) return;
        ndMsg.textContent = t || ''; ndMsg.className = 'alert ' + type;
        ndMsg.style.display = t ? 'block' : 'none';
    }

    // ---- row-flash ----
    function flashRow(cid) {
        const tr = document.querySelector(`#clientes-tbody tr[data-id="${cid}"]`);
        if (!tr) return;
        tr.classList.remove('row-flash');
        void tr.offsetWidth; // reflow para reiniciar animación
        tr.classList.add('row-flash');
        tr.addEventListener('animationend', () => tr.classList.remove('row-flash'), { once: true });
    }

    // ---- rol ----
    async function whoAmI() {
        try {
            const r = await authFetch('/auth/me');
            if (r.ok) { const me = await r.json(); isAdmin = me.role === 'ROLE_ADMIN'; }
        } catch { }
    }

    // ---- Empty message ----
    function updateEmptyMessage() {
        const empty = document.querySelector('#clientes-table tbody td.dataTables_empty');
        if (!empty) return;
        empty.textContent = state.filters.q
            ? `No se encontraron clientes para "${state.filters.q}".`
            : 'Aún no hay clientes registrados. Pulsa "Nuevo cliente" para crear el primero.';
    }

    // ---- DataTable ----
    function initDataTable() {
        if (dt) return;
        dt = jQuery('#clientes-table').DataTable({
            serverSide: true,
            processing: true,
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: false,
            searching: false,
            autoWidth: false,
            language: { url: 'https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json', infoEmpty: 'Sin registros', emptyTable: 'No hay clientes registrados' },
            ajax: async (data, callback) => {
                const qs = new URLSearchParams();
                qs.set('page', Math.floor(data.start / data.length));
                qs.set('size', data.length);
                if (state.filters.q) qs.set('q', state.filters.q);
                if (state.filters.noDeseado !== null) qs.set('noDeseado', String(state.filters.noDeseado));
                const r = await authFetch('/api/clientes?' + qs.toString());
                if (!r.ok) {
                    setMsg('No se pudo cargar clientes (' + r.status + ')', 'error');
                    callback({ draw: data.draw, recordsTotal: 0, recordsFiltered: 0, data: [] });
                    return;
                }
                const page = await r.json();
                callback({
                    draw: data.draw,
                    recordsTotal: page.totalElements ?? 0,
                    recordsFiltered: page.totalElements ?? 0,
                    data: page.content || []
                });
            },
            rowCallback: (row, c) => {
                row.dataset.id = c.id;
                row.classList.toggle('bad', !!c.noDeseado);
                if (c.noDeseado) row.title = 'Cliente no deseado';
                else row.removeAttribute('title');
            },
            drawCallback: () => updateEmptyMessage(),
            columns: [
                { data: 'nombre',   render: v => escapeHtml(v || '—') },
                { data: 'apellido', render: v => escapeHtml(v || '—') },
                { data: 'telefono', render: v => escapeHtml(v || '—') },
                { data: 'nif',     render: v => escapeHtml(v || '—') },
                { data: 'correo',  render: v => escapeHtml(v || '—') },
                { data: null, render: c => c.noDeseado ? '<span class="nd-flag">No deseado</span>' : '—' },
                {
                    data: null,
                    orderable: false,
                    className: 'col-actions',
                    render: c => `
                        <button class="secondary edit" data-id="${c.id}">Editar</button>
                        ${isAdmin ? `<button class="del" data-id="${c.id}">Eliminar</button>` : ''}
                    `
                }
            ],
            initComplete: function() { window.dtMoveLengthControl('clientes-table'); }
        });
    }

    // ---- Formulario ----
    function fillForm(c) {
        id.value = c.id || '';
        nombre.value = c.nombre || '';
        apellido.value = c.apellido || '';
        direccion.value = c.direccion || '';
        codigoPostal.value = c.codigoPostal || '';
        nif.value = c.nif || '';
        telefono.value = c.telefono || '';
        correo.value = c.correo || '';
        currentNoDeseado = !!c.noDeseado;
        noDeseado.checked = currentNoDeseado;
        pendingNdMotivo = '';

        const nombreCompleto = `${c.nombre || ''} ${c.apellido || ''}`.trim();
        formTitle.textContent = c.id
            ? (nombreCompleto || 'Cliente #' + c.id)
            : 'Nuevo cliente';

        ndField.style.display = c.id ? 'flex' : 'none';
        vehSection.style.display = c.id ? 'block' : 'none';
        setNdPending(false);

        setTelWarn('');
        telefono.setCustomValidity('');
        telDup = false;
    }

    function setNdPending(isPending) {
        if (!ndPendingInfo) return;
        noDeseado.style.display = isPending ? 'none' : '';
        ndPendingInfo.style.display = isPending ? 'block' : 'none';
    }

    function openForm() {
        setFormMsg('');
        clienteModal.classList.add('open');
    }

    function closeForm() {
        clienteModal.classList.remove('open');
        setFormMsg('');
    }

    // ---- Validación teléfono duplicado ----
    let telTimer = null;
    let telDup = false;

    function setTelWarn(text) {
        telWarn.textContent = text || '';
        telWarn.style.display = text ? 'block' : 'none';
    }

    async function checkTelefonoDup({ silent = false } = {}) {
        const tel = (telefono.value || '').trim();
        const excludeId = id.value ? Number(id.value) : null;
        telDup = false;
        telefono.setCustomValidity('');
        if (!tel) { setTelWarn(''); return false; }
        try {
            const qs = new URLSearchParams({ telefono: tel });
            if (excludeId) qs.set('excludeId', String(excludeId));
            const r = await authFetch('/api/clientes/exists-telefono?' + qs.toString());
            if (!r.ok) { if (!silent) setTelWarn('No se pudo validar el teléfono (servidor).'); return false; }
            const data = await r.json();
            telDup = !!data?.exists;
            if (telDup) {
                const m = 'Ya existe un cliente con ese teléfono';
                setTelWarn(m); telefono.setCustomValidity(m); return true;
            }
            setTelWarn(''); telefono.setCustomValidity(''); return false;
        } catch { if (!silent) setTelWarn('No se pudo validar el teléfono (red).'); return false; }
    }

    telefono.addEventListener('input', () => {
        clearTimeout(telTimer);
        telTimer = setTimeout(() => checkTelefonoDup({ silent: true }), 250);
    });
    telefono.addEventListener('blur', () => checkTelefonoDup({ silent: false }));

    // ---- Vehículos ----
    async function loadVehiculos(cid) {
        const r = await authFetch('/api/vehiculos?clienteId=' + encodeURIComponent(cid));
        if (!r.ok) { vehBody.innerHTML = ''; return; }
        const vs = await r.json();
        const vehiculos = Array.isArray(vs) ? vs : (vs.content || []);
        vehBody.innerHTML = vehiculos.length
            ? vehiculos.map(v => `
                <tr>
                    <td>${escapeHtml(v.matricula)}</td>
                    <td>${escapeHtml(v.marca)}</td>
                    <td>${escapeHtml(v.modelo)}</td>
                    <td>${escapeHtml(v.color)}</td>
                    <td>${escapeHtml(v.plaza)}</td>
                </tr>`).join('')
            : '<tr><td colspan="5" style="text-align:center;" class="muted">Sin vehículos asociados</td></tr>';
    }

    // ---- Acciones de la tabla ----
    document.querySelector('#clientes-tbody').addEventListener('click', async (e) => {
        const t = e.target;

        if (t.classList.contains('edit')) {
            const r = await authFetch('/api/clientes/' + t.dataset.id);
            if (!r.ok) { setMsg('No se pudo cargar el cliente', 'error'); return; }
            const c = await r.json();
            fillForm(c);
            openForm();
            authFetch(`/api/clientes/${c.id}/no-deseado/pendiente`)
                .then(res => res.ok ? res.json() : null)
                .then(data => { if (data?.pendiente) setNdPending(true); })
                .catch(() => {});
            await loadVehiculos(c.id);
            nombre.focus();
        }

        if (t.classList.contains('del')) {
            if (!confirm('¿Eliminar este cliente?')) return;
            const r = await authFetch('/api/clientes/' + t.dataset.id, { method: 'DELETE' });
            if (r.status === 204) {
                setMsg('Cliente eliminado', 'success');
                closeForm();
                dt.ajax.reload(null, false);
            } else if (r.status === 403) {
                setMsg('Solo ADMIN puede eliminar', 'error');
            } else {
                setMsg('No se pudo eliminar', 'error');
            }
        }
    });

    // ---- Nuevo / Cancelar ----
    btnNew.addEventListener('click', () => {
        fillForm({});
        openForm();
        nombre.focus();
    });

    btnCancel.addEventListener('click', () => closeForm());
    $('#modalClose')?.addEventListener('click', () => closeForm());
    clienteModal?.querySelector('.modal-backdrop')?.addEventListener('click', () => closeForm());

    // ---- Guardar ----
    document.querySelector('#form').addEventListener('submit', async (ev) => {
        ev.preventDefault();

        const dup = await checkTelefonoDup({ silent: false });
        if (dup) {
            setFormMsg('Ya existe un cliente con ese teléfono', 'error');
            telefono.reportValidity(); telefono.focus(); return;
        }

        const editing = !!id.value;
        const wantNoDeseado = !!noDeseado.checked;
        const needNdRequest = !isAdmin && editing && wantNoDeseado && !currentNoDeseado;

        const payload = {
            nombre: nombre.value.trim(),
            apellido: apellido.value.trim() || null,
            direccion: direccion.value.trim() || null,
            codigoPostal: codigoPostal.value.trim() || null,
            nif: nif.value.trim() || null,
            telefono: telefono.value.trim() || null,
            correo: correo.value.trim() || null,
            noDeseado: needNdRequest ? currentNoDeseado : wantNoDeseado
        };

        const url = editing ? '/api/clientes/' + id.value : '/api/clientes';
        const method = editing ? 'PUT' : 'POST';
        const r = await authFetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        if (!r.ok) {
            let err = ''; try { err = (await r.text())?.trim(); } catch { }
            if (r.status === 409) {
                const m = err || 'Ya existe un cliente con ese teléfono';
                setTelWarn(m); telefono.setCustomValidity(m); telefono.reportValidity(); telefono.focus();
            }
            setFormMsg(err || ('Error al guardar (' + r.status + ')'), 'error'); return;
        }

        const saved = await r.json();
        id.value = saved.id;

        setMsg(editing ? 'Cliente actualizado' : 'Cliente creado', 'success');
        closeForm();
        dt.ajax.reload(null, !editing);
        dt.one('draw', () => flashRow(saved.id));
    });

    // ---- Modal No deseado ----
    function openNdModal() {
        if (!ndModal) return;
        setNdMsg('');
        if (ndMotivo) {
            ndMotivo.value = '';
            const cnt = document.getElementById('ndMotivoCount');
            if (cnt) cnt.textContent = '0 / 300';
        }
        ndModal.classList.add('open');
        setTimeout(() => ndMotivo?.focus?.(), 30);
    }
    function closeNdModal(restore = true) {
        ndModal?.classList.remove('open');
        if (restore) noDeseado.checked = currentNoDeseado;
    }

    ndCancel?.addEventListener('click', () => closeNdModal(true));
    ndClose?.addEventListener('click', () => closeNdModal(true));
    ndModal?.querySelector('.modal-backdrop')?.addEventListener('click', () => closeNdModal(true));
    ndMotivo?.addEventListener('input', e => {
        const cnt = document.getElementById('ndMotivoCount');
        if (cnt) cnt.textContent = `${e.target.value.length} / 300`;
    });

    ndForm?.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        if (ndForm.dataset.busy === '1') return;
        const motivo = (ndMotivo?.value || '').trim();
        if (!motivo) { setNdMsg('El motivo es obligatorio', 'error'); ndMotivo?.focus?.(); return; }

        if (!id.value) {
            pendingNdMotivo = motivo;
            closeNdModal(false);
            return;
        }

        ndForm.dataset.busy = '1';
        if (ndSubmit) { ndSubmit.disabled = true; ndSubmit.textContent = 'Enviando…'; }
        try {
            await solicitarNoDeseado(id.value, motivo);
            closeNdModal(true);
            closeForm();
            setMsg('Solicitud de cliente No deseado enviada. Se notificará al administrador por correo electrónico.', 'success');
        } catch (e) {
            setNdMsg((e && e.message) ? e.message : 'Error de red al enviar la solicitud', 'error');
        } finally {
            ndForm.dataset.busy = '';
            if (ndSubmit) { ndSubmit.disabled = false; ndSubmit.textContent = 'Enviar solicitud'; }
        }
    });

    noDeseado?.addEventListener('change', () => {
        if (noDeseado.checked && !currentNoDeseado) {
            pendingNdMotivo = '';
            if (!isAdmin) openNdModal();
        } else if (!noDeseado.checked) {
            pendingNdMotivo = '';
        }
    });

    async function solicitarNoDeseado(cid, motivo) {
        const r = await authFetch(`/api/clientes/${cid}/no-deseado/solicitar`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ motivo: motivo || null })
        });
        if (!r.ok) {
            let t = ''; try { t = (await r.text())?.trim(); } catch { }
            throw new Error(t || 'No se pudo enviar la solicitud');
        }
    }

    // ---- Búsqueda ----
    function debounce(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }

    function applyFilters() {
        state.filters.q = q.value.trim();
        const v = fEstado.value;
        state.filters.noDeseado = v === '' ? null : (v === 'true');
        dt.ajax.reload(null, true);
    }

    q.addEventListener('input', debounce(applyFilters, 400));
    fEstado.addEventListener('change', applyFilters);

    btnClear.addEventListener('click', (e) => {
        e.preventDefault();
        q.value = '';
        fEstado.value = '';
        state.filters.q = '';
        state.filters.noDeseado = null;
        dt.ajax.reload(null, true);
    });

    (async function init() {
        await whoAmI();
        initDataTable();
    })();
})();
