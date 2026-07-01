(function () {
    // authFetch lo expone globalmente app.js (añade el Bearer token,
    // refresca la sesión en 401 y redirige a login en 403).
    const af = window.authFetch;
    const escapeHtml = window.escapeHtml;
    const $ = (q) => document.querySelector(q);

    const tbody = $('#vehiculos-tbody');
    const fMatricula = $('#fMatricula'), fMarca = $('#fMarca'), fModelo = $('#fModelo'),
        fColor = $('#fColor'), fPlaza = $('#fPlaza'), fCliente = $('#fCliente');
    const btnSearch = $('#btnSearch'), btnClear = $('#btnClear'), btnNew = $('#btnNew');

    const vehiculoModal = $('#vehiculoModal'), formTitle = $('#formTitle');
    const formMsg = $('#formMsg');
    const form = $('#form'), id = $('#id'), matricula = $('#matricula'), marca = $('#marca'),
        modelo = $('#modelo'), color = $('#color'), plaza = $('#plaza');
    const clienteSearch = $('#clienteSearch'), clienteIdHidden = $('#clienteId'), clienteList = $('#clienteList');
    const chips = $('#chips');
    const cancel = $('#cancel'), btnDelete = $('#btnDelete'), btnAddCliente = $('#btnAddCliente');

    let isAdmin = false;
    const state = { filters: {} };

    function highlightMatches(text, term) {
        const safe = escapeHtml(text);
        const terms = String(term || '').trim().split(/\s+/).filter(Boolean);
        if (!terms.length) return safe;
        const pattern = terms.map(t => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
        if (!pattern) return safe;
        const regex = new RegExp(`(${pattern})`, 'gi');
        return safe.replace(regex, '<strong>$1</strong>');
    }

    function setFormMsg(t, type = 'success') {
        if (!formMsg) return;
        formMsg.textContent = t || ''; formMsg.className = 'alert ' + type;
        formMsg.style.display = t ? 'block' : 'none';
    }
    function openModal() {
        setFormMsg('');
        vehiculoModal.classList.add('open');
    }
    function closeModal() {
        vehiculoModal.classList.remove('open');
        setFormMsg('');
    }
    function hasActiveFilters() {
        return Object.values(state.filters).some(v => v);
    }
    function updateEmptyMessage() {
        const empty = document.querySelector('#vehiculos-table tbody td.dataTables_empty');
        if (!empty) return;
        empty.textContent = hasActiveFilters()
            ? 'No se encontraron vehículos con esos filtros.'
            : 'Aún no hay vehículos registrados. Pulsa "Nuevo vehículo" para crear el primero.';
    }
    async function whoAmI() {
        try {
            const r = await af('/auth/me');
            if (r.ok) {
                const me = await r.json();
                isAdmin = me.role === 'ROLE_ADMIN';
            }
        } catch { }
    }

    // Carga ligera de los clientes asociados a cada fila visible
    function loadRowClientes() {
        Array.from(tbody.querySelectorAll('[data-clients="cell"]')).forEach(async cell => {
            const rowId = cell.getAttribute('data-veh-id');
            try {
                const r = await af(`/api/vehiculos/${rowId}/clientes`);
                if (!r.ok) { cell.textContent = '—'; return; }
                const cs = await r.json();
                cell.textContent = cs.length ? cs.map(c => ((c.nombre || '') + (c.apellido ? ' ' + c.apellido : '')).trim()).join(', ') : '—';
            } catch {
                cell.textContent = '—';
            }
        });
    }

    let dt = null;

    function initDataTable() {
        if (dt) return;

        // El backend de /api/vehiculos limita el tamaño de página (máx. 200) y cada
        // fila requiere una consulta adicional para los clientes asociados, así que
        // usamos el modo serverSide de DataTables: pide una página a la vez en vez
        // de intentar cargar todo el listado de golpe.
        dt = jQuery('#vehiculos-table').DataTable({
            serverSide: true,
            processing: true,
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: false,
            searching: false,
            autoWidth: false,
            language: {
                url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json",
                infoEmpty: "Sin registros",
                emptyTable: "No hay vehículos registrados"
            },
            ajax: async (data, callback) => {
                const qs = new URLSearchParams();
                qs.set('page', Math.floor(data.start / data.length));
                qs.set('size', data.length);
                for (const [k, v] of Object.entries(state.filters)) {
                    if (v) qs.set(k, v);
                }
                const r = await af('/api/vehiculos?' + qs.toString());
                if (!r.ok) {
                    setMsg('No se pudo cargar vehículos (' + r.status + ')', 'error');
                    callback({ draw: data.draw, recordsTotal: 0, recordsFiltered: 0, data: [] });
                    return;
                }
                const pageData = await r.json();
                callback({
                    draw: data.draw,
                    recordsTotal: pageData.totalElements ?? 0,
                    recordsFiltered: pageData.totalElements ?? 0,
                    data: pageData.content || []
                });
            },
            drawCallback: () => { loadRowClientes(); updateEmptyMessage(); },
            columns: [
                { data: "matricula", render: (v) => escapeHtml(v || '') },
                { data: "marca", render: (v) => escapeHtml(v || '') },
                { data: "modelo", render: (v) => escapeHtml(v || '') },
                { data: "color", render: (v) => escapeHtml(v || '') },
                { data: "plaza", render: (v) => escapeHtml(v || '') },
                { data: null, render: (row) => `<span data-clients="cell" data-veh-id="${row.id}">···</span>` },
                {
                    data: null,
                    orderable: false,
                    className: "col-actions",
                    render: (row) => `
                        <button class="secondary edit" data-id="${row.id}">Editar</button>
                        ${isAdmin ? `<button class="del" data-id="${row.id}">Eliminar</button>` : ''}
                    `
                }
            ],
            initComplete: function() { window.dtMoveLengthControl('vehiculos-table'); }
        });
    }

    // Autocomplete de clientes
    let clienteItems = [];
    let clienteIndex = -1;
    let clienteTimer = null;

    async function searchClientes(term) {
        term = (term || '').trim();
        if (term.length < 2) {
            clienteList.style.display = 'none';
            return;
        }
        const qs = new URLSearchParams({ q: term, page: 0, size: 8 });
        const r = await af('/api/clientes?' + qs.toString());
        if (!r.ok) {
            clienteList.innerHTML = '<div class="nores">Error cargando clientes</div>';
            clienteList.style.display = 'block';
            return;
        }
        const data = await r.json();
        const list = data.content || data;
        clienteItems = list;
        if (!list.length) {
            clienteList.innerHTML = '<div class="nores">Sin resultados</div>';
            clienteList.style.display = 'block';
            return;
        }
        clienteList.innerHTML = list.map((c, i) => {
            const full = `${c.nombre || ''}${c.apellido ? ' ' + c.apellido : ''}`.trim();
            const parts = [
                c.telefono ? highlightMatches(c.telefono, term) : null,
                c.nif      ? highlightMatches(c.nif, term)      : null,
            ].filter(Boolean).join(' · ');
            return `<div class="autocomplete-item" data-i="${i}">${highlightMatches(full, term)}${parts ? ' · ' + parts : ''}</div>`;
        }).join('');
        clienteIndex = -1;
        clienteList.style.display = 'block';
    }

    function selectCliente(i) {
        const c = clienteItems[i];
        if (!c) return;
        const full = `${c.nombre || ''}${c.apellido ? ' ' + c.apellido : ''}`.trim();
        clienteSearch.value = full;
        clienteIdHidden.value = c.id;
        clienteList.style.display = 'none';
    }

    function updateActive(items) {
        items.forEach((el, idx) => el.classList.toggle('active', idx === clienteIndex));
        if (items[clienteIndex]) items[clienteIndex].scrollIntoView({ block: 'nearest' });
    }

    clienteSearch.addEventListener('input', () => {
        clienteIdHidden.value = '';
        clearTimeout(clienteTimer);
        clienteTimer = setTimeout(() => searchClientes(clienteSearch.value), 250);
    });
    clienteSearch.addEventListener('focus', () => {
        if ((clienteSearch.value || '').trim().length >= 2) searchClientes(clienteSearch.value);
    });
    clienteSearch.addEventListener('keydown', (e) => {
        const items = clienteList.querySelectorAll('.autocomplete-item');
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (items.length) {
                clienteIndex = (clienteIndex + 1) % items.length; updateActive(items);
            }
        }
        else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (items.length) {
                clienteIndex = (clienteIndex - 1 + items.length) % items.length;
                updateActive(items);
            }
        }
        else if (e.key === 'Enter') {
            if (clienteIndex >= 0) {
                e.preventDefault();
                selectCliente(clienteIndex);
            }
        }
        else if (e.key === 'Escape') {
            clienteList.style.display = 'none';
        }
    });
    clienteList.addEventListener('mousedown', (e) => {
        const item = e.target.closest('.autocomplete-item'); if (!item) return;
        selectCliente(parseInt(item.dataset.i, 10));
    });
    clienteSearch.addEventListener('focus', () => { if ((clienteSearch.value || '').trim().length >= 2) searchClientes(clienteSearch.value); });
    clienteSearch.addEventListener('blur', () => { clienteList.style.display = 'none'; });
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.autocomplete')) clienteList.style.display = 'none';
    });

    // Chips (clientes asociados)
    function renderChips(list) {
        chips.innerHTML =
            list.length ? list.map(c => {
                const full = `${c.nombre || ''}${c.apellido ? ' ' + c.apellido : ''}`.trim();
                return `<span class="chip" data-id="${c.id}">${escapeHtml(full)}<button type="button" title="Quitar" class="chip-del">×</button></span>`;
            }).join('')
            : '<span class="muted">Sin clientes asociados</span>';
    }
    async function loadChips(vehiculoId) {
        const r = await af(`/api/vehiculos/${vehiculoId}/clientes`);
        if (!r.ok) { renderChips([]); return; }
        renderChips(await r.json());
    }

    chips.addEventListener('click', async (e) => {
        const btn = e.target.closest('.chip-del');
        if (!btn) return;
        const chip = btn.closest('.chip');
        const clienteId = chip.getAttribute('data-id');
        if (!id.value) return;
        const res = await af(`/api/vehiculos/${id.value}/clientes/${clienteId}`, { method: 'DELETE' });
        if (res.status === 204) {
            setFormMsg('Cliente desasociado', 'success');
            loadChips(id.value);
        } else {
            setFormMsg('No se pudo desasociar', 'error');
        }
    });

    btnAddCliente.addEventListener('click', async () => {
        if (!id.value) {
            setFormMsg('Guarda primero el vehículo antes de asociar clientes', 'error');
            return;
        }
        const cid = clienteIdHidden.value;
        if (!cid) {
            setFormMsg('Elige un cliente válido del buscador', 'error');
            return;
        }
        const res = await af(`/api/vehiculos/${id.value}/clientes/${cid}`, { method: 'POST' });
        if (res.ok) {
            setFormMsg('Cliente asociado', 'success');
            clienteIdHidden.value = ''; clienteSearch.value = ''; clienteList.style.display = 'none';
            loadChips(id.value);
        } else {
            setFormMsg('No se pudo asociar', 'error');
        }
    });

    // ===== Filtros =====
    btnSearch.addEventListener('click', (e) => {
        e.preventDefault();
        state.filters = {
            matricula: fMatricula.value.trim() || '',
            marca:     fMarca.value.trim()     || '',
            modelo:    fModelo.value.trim()    || '',
            color:     fColor.value.trim()     || '',
            plaza:     fPlaza.value.trim()     || '',
            cliente:   fCliente.value.trim()   || ''
        };
        dt.ajax.reload(null, true);
    });
    btnClear.addEventListener('click', (e) => {
        e.preventDefault();
        [fMatricula, fMarca, fModelo, fColor, fPlaza, fCliente].forEach(i => i.value = '');
        state.filters = {};
        dt.ajax.reload(null, true);
    });

    // -------- Form --------
    function toPayload() {
        return {
            matricula: (matricula.value || '').trim(),
            marca: (marca.value || '').trim() || null,
            modelo: (modelo.value || '').trim() || null,
            color: (color.value || '').trim() || null,
            plaza: (plaza.value || '').trim() || null
        };
    }

    btnNew.addEventListener('click', async () => {
        form.reset();
        id.value = '';
        clienteIdHidden.value = '';
        clienteSearch.value = '';
        clienteList.style.display = 'none';
        chips.innerHTML = '<span class="muted">Sin clientes asociados</span>';
        btnDelete.style.display = 'none';
        formTitle.textContent = 'Nuevo vehículo';
        openModal();
        matricula.focus();
    });
    cancel.addEventListener('click', () => {
        form.reset(); id.value = '';
        clienteIdHidden.value = ''; clienteSearch.value = '';
        closeModal();
    });
    $('#modalClose')?.addEventListener('click', () => {
        form.reset(); id.value = '';
        clienteIdHidden.value = ''; clienteSearch.value = '';
        closeModal();
    });
    vehiculoModal?.querySelector('.modal-backdrop')?.addEventListener('click', () => {
        form.reset(); id.value = '';
        clienteIdHidden.value = ''; clienteSearch.value = '';
        closeModal();
    });

    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const body = JSON.stringify(toPayload());
        const editing = !!id.value;
        const url = editing ? '/api/vehiculos/' + id.value : '/api/vehiculos';
        const method = editing ? 'PUT' : 'POST';
        const r = await af(url, { method, headers: { 'Content-Type': 'application/json' }, body });
        if (!r.ok) {
            let err = ''; try { err = (await r.text())?.trim(); } catch { };
            setFormMsg(err || 'Error guardando', 'error'); return;
        }
        const saved = await r.json();
        id.value = saved.id;

        if (clienteIdHidden.value) {
            const res = await af(`/api/vehiculos/${id.value}/clientes/${clienteIdHidden.value}`, { method: 'POST' });
            if (res.ok) { clienteIdHidden.value = ''; clienteSearch.value = ''; }
        }

        dt.ajax.reload(null, true);
        setMsg(editing ? 'Vehículo actualizado' : 'Vehículo creado', 'success');
        form.reset(); id.value = '';
        closeModal();
    });

    tbody.addEventListener('click', async (ev) => {
        const t = ev.target;
        if (t.classList.contains('del')) {
            if (!confirm('¿Eliminar vehículo?')) return;
            const r = await af('/api/vehiculos/' + t.dataset.id, { method: 'DELETE' });
            if (r.status === 204) {
                setMsg('Eliminado', 'success');
                dt.ajax.reload(null, false);
            }
            else if (r.status === 403) {
                setMsg('Solo ADMIN puede eliminar', 'error');
            }
            else {
                setMsg('No se pudo eliminar', 'error');
            }
        }
        if (t.classList.contains('edit')) {
            const r = await af('/api/vehiculos/' + t.dataset.id);
            if (!r.ok) {
                setMsg('No se pudo cargar el vehículo', 'error');
                return;
            }
            const v = await r.json();
            id.value = v.id;
            matricula.value = v.matricula || '';
            marca.value = v.marca || '';
            modelo.value = v.modelo || '';
            color.value = v.color || '';
            plaza.value = v.plaza || '';
            btnDelete.style.display = isAdmin ? 'inline-block' : 'none';
            formTitle.textContent = 'Editar vehículo: ' + (v.matricula || '');
            openModal();
            await loadChips(v.id);
        }
    });

    (async function init() {
        // app.js ya gestiona el saludo (Bienvenido/centro) y el botón de logout
        await whoAmI();
        initDataTable();
    })();
})();
