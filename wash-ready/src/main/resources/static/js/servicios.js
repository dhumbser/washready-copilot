(function () {
    const $ = (s) => document.querySelector(s);
    const escapeHtml = window.escapeHtml;
    const af = window.authFetch;

    // refs
    const tbody = $('#servicios-tbody');
    const servicioModal = $('#servicioModal');
    const form = $('#servicio-form');
    const toggleFormBtn = $('#toggleForm');
    const formMsg = $('#formMsg');
    const fQ = $('#fQ');
    const fImporte = $('#fImporte');
    const fTipo = $('#fTipo');
    const fCentroWrap = $('#fCentroWrap');
    const fCentro = $('#fCentro');
    const centrosSelWrap = $('#centrosSelWrap');
    const centrosSel = $('#centrosSel');
    const saveBtn = $('#saveBtn');
    const cancelEditBtn = $('#cancelEditBtn');
    const tipoSel = $('#tipo');
    const importeCeroChk = $('#importeCero');
    const todosCentrosChk = $('#todosCentros');

    let ME = null;
    let IS_ADMIN = false;
    let EDIT_ID = null;

    const state = { page: 0, size: 15 };
    let servicios = [];
    let centrosList = [];
    let lastTipoValue = null;
    let lastNonGeneralTodos = false;
    let dt = null;

    // ---- Mensajes ----
    function setFormMsg(t, type = 'success') {
        if (!formMsg) return;
        formMsg.textContent = t || ''; formMsg.className = 'alert ' + type;
        formMsg.style.display = t ? 'block' : 'none';
    }

    // ---- row-flash ----
    function flashRow(id) {
        const tr = document.querySelector(`#servicios-tbody tr[data-id="${id}"]`);
        if (!tr) return;
        tr.classList.remove('row-flash');
        void tr.offsetWidth;
        tr.classList.add('row-flash');
        tr.addEventListener('animationend', () => tr.classList.remove('row-flash'), { once: true });
    }

    // ---- Helpers ----
    function tipoLabel(raw) { return (raw || '').replace(/^TIPO_/, 'Tipo '); }

    function rowCanEdit(item) {
        return IS_ADMIN || !!item.editable;
    }

    function formatCentros(it) {
        if (it.disponibleTodosCentros) {
            return `Todos los centros <span class="badge badge-all-centros" title="Disponible en todos los centros">Global</span>`;
        }
        if (Array.isArray(it.centrosNombres)) {
            if (centrosList.length && it.centrosNombres.length === centrosList.length) {
                return 'Todos los centros';
            }
            return it.centrosNombres.map(c => escapeHtml(c || '')).join('<br>');
        }
        return escapeHtml(it.centroNombre || '—');
    }

    // ---- DataTable ----
    function initDataTable() {
        if (dt) return;
        dt = jQuery('#servicios-table').DataTable({
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: true,
            searching: false,
            autoWidth: false,
            language: { url: 'https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json', infoEmpty: 'Sin registros', emptyTable: 'No hay servicios registrados' },
            columnDefs: [
                { targets: 4, visible: IS_ADMIN }
            ],
            columns: [
                { data: 'descripcion', className: 'col-desc',    render: v => escapeHtml(v || '') },
                { data: 'importe',                               render: v => `${Number(v).toFixed(2)} €` },
                { data: 'stock',                                 render: v => (v == null ? '—' : v) },
                { data: 'tipo',                                  render: v => escapeHtml(tipoLabel(v)) },
                { data: null,          className: 'col-centros', render: row => formatCentros(row) || '—' },
                { data: 'editable',                              render: v => (v ? 'Sí' : 'No') },
                {
                    data: null,
                    orderable: false,
                    render: row => {
                        const canEdit = rowCanEdit(row);
                        return `
                            ${canEdit ? `<button data-id="${row.id}" class="secondary edit">Editar</button>` : ''}
                            ${IS_ADMIN ? `<button data-id="${row.id}" class="del">Eliminar</button>` : ''}
                        `;
                    }
                }
            ],
            initComplete: function() { window.dtMoveLengthControl('servicios-table'); }
        });
    }

    function renderRows(list) {
        initDataTable();
        dt.clear();
        dt.rows.add(list || []);
        dt.draw();

        // Añadir data-id a cada fila para flashRow
        setTimeout(() => {
            (list || []).forEach(item => {
                const btn = tbody.querySelector(`button[data-id="${item.id}"]`);
                const tr = btn?.closest('tr');
                if (tr) tr.dataset.id = item.id;
            });
        }, 0);
    }

    // ---- Centros ----
    async function loadCentrosInto(selectEl, includeBlank = false) {
        const res = await af('/api/centros?soloTransaccional=true', { headers: { Accept: 'application/json' } });
        if (!res.ok) return;
        const data = await res.json();
        const list = Array.isArray(data) ? data : (data.content || []);
        selectEl.innerHTML = (includeBlank ? '<option value="">Todos</option>' : '') +
            list.map(c => `<option value="${c.id}">${escapeHtml(c.nombre || ('#' + c.id))}</option>`).join('');
        if (selectEl === centrosSel) centrosList = list;
    }

    async function loadMe() {
        const r = await af('/auth/me', { headers: { Accept: 'application/json' } });
        if (r.ok) { ME = await r.json(); IS_ADMIN = (ME.role === 'ROLE_ADMIN'); }
    }

    function getSelectedCentroIds() {
        return Array.from(centrosSel.selectedOptions).map(o => Number(o.value));
    }

    function setSelectedCentroIds(ids) {
        const idset = new Set((ids || []).map(Number));
        Array.from(centrosSel.options).forEach(opt => { opt.selected = idset.has(Number(opt.value)); });
    }

    // ---- Query ----
    function buildQuery() {
        const qs = new URLSearchParams();
        const q = (fQ.value || '').trim();
        if (q) qs.set('q', q);
        const imp = (fImporte.value || '').trim();
        if (imp) qs.set('importe', imp);
        const tipo = fTipo.value;
        if (tipo) qs.set('tipo', tipo);
        if (IS_ADMIN) {
            const cid = fCentro.value;
            if (cid) qs.set('centroId', cid);
        } else if (ME && ME.centroId) {
            qs.set('centroId', ME.centroId);
        }
        return qs;
    }

    async function loadServicios() {
        const res = await af('/api/servicios?' + buildQuery().toString(), { headers: { Accept: 'application/json' } });
        if (!res.ok) { setMsg('No se pudo cargar el listado', 'error'); return; }
        const data = await res.json();
        servicios = Array.isArray(data) ? data : (data?.content || []);
        renderRows(servicios);
    }

    // ---- Formulario ----
    function openForm(editing = false) {
        setFormMsg('');
        saveBtn.textContent = editing ? 'Guardar cambios' : 'Guardar';
        syncTipoRules({ preserveTodos: editing });
        servicioModal.classList.add('open');
    }

    function closeForm() {
        form.reset();
        EDIT_ID = null;
        servicioModal.classList.remove('open');
        saveBtn.textContent = 'Guardar';
        setFormMsg('');
        setSelectedCentroIds([]);
        hideCentrosSuggestion();
        todosCentrosChk.checked = false;
        todosCentrosChk.disabled = false;
        centrosSel.disabled = false;
        lastTipoValue = null;
        lastNonGeneralTodos = false;
        syncTipoRules();
    }

    const centrosSuggestion = document.getElementById('centrosSuggestion');

    function checkAllCentrosSelected() {
        if (!centrosSuggestion || todosCentrosChk.checked) { hideCentrosSuggestion(); return; }
        const total = centrosSel.options.length;
        const selected = centrosSel.selectedOptions.length;
        if (total > 0 && selected === total) {
            centrosSuggestion.style.display = 'block';
        } else {
            hideCentrosSuggestion();
        }
    }

    function hideCentrosSuggestion() {
        if (centrosSuggestion) centrosSuggestion.style.display = 'none';
    }

    function syncCentrosAvailability() {
        centrosSel.disabled = todosCentrosChk.checked;
        if (todosCentrosChk.checked) { setSelectedCentroIds([]); hideCentrosSuggestion(); }
    }

    function syncTipoRules({ preserveTodos = false } = {}) {
        const isGeneral = (tipoSel.value === 'GENERAL');
        const wasGeneral = (lastTipoValue === 'GENERAL');
        if (isGeneral) {
            if (!wasGeneral) lastNonGeneralTodos = todosCentrosChk.checked;
            if (!preserveTodos && !wasGeneral) todosCentrosChk.checked = true;
        } else {
            if (wasGeneral) todosCentrosChk.checked = lastNonGeneralTodos;
            else lastNonGeneralTodos = todosCentrosChk.checked;
        }
        todosCentrosChk.disabled = false;
        syncCentrosAvailability();
        lastTipoValue = tipoSel.value;
    }

    // ---- Init ----
    async function init() {
        await loadMe();

        if (IS_ADMIN) {
            if (toggleFormBtn) toggleFormBtn.style.display = '';
            fCentroWrap.style.display = '';
            centrosSelWrap.style.display = '';
            await loadCentrosInto(fCentro, true);
            await loadCentrosInto(centrosSel, false);
        } else {
            const editableWrap = document.getElementById('editableWrap');
            if (editableWrap) editableWrap.style.display = 'none';
        }

        tipoSel.addEventListener('change', () => syncTipoRules());
        todosCentrosChk.addEventListener('change', () => {
            if (tipoSel.value !== 'GENERAL') lastNonGeneralTodos = todosCentrosChk.checked;
            syncCentrosAvailability();
        });
        centrosSel.addEventListener('change', () => checkAllCentrosSelected());
        centrosSuggestion?.querySelector('.suggestion-apply')?.addEventListener('click', () => {
            todosCentrosChk.checked = true;
            if (tipoSel.value !== 'GENERAL') lastNonGeneralTodos = true;
            syncCentrosAvailability();
        });

        toggleFormBtn?.addEventListener('click', () => {
            if (!IS_ADMIN) return;
            openForm(false);
        });

        cancelEditBtn.addEventListener('click', () => closeForm());
        $('#modalClose')?.addEventListener('click', () => closeForm());
        servicioModal?.querySelector('.modal-backdrop')?.addEventListener('click', () => closeForm());

        $('#btnBuscar').addEventListener('click', async (e) => { e.preventDefault(); await loadServicios(); });
        $('#btnLimpiar').addEventListener('click', async (e) => {
            e.preventDefault();
            fQ.value = '';
            fImporte.value = '';
            fTipo.value = '';
            if (IS_ADMIN) fCentro.value = '';
            await loadServicios();
        });

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            setMsg(EDIT_ID ? 'Guardando cambios…' : 'Guardando…');

            const stockVal = ($('#stock').value || '').trim();
            const payload = {
                tipo: $('#tipo').value || null,
                descripcion: $('#descripcion').value.trim(),
                importe: parseFloat($('#importe').value),
                stock: stockVal === '' ? null : parseInt(stockVal, 10),
                editable: $('#editable').checked,
                importeCeroEnTicket: importeCeroChk.checked,
                disponibleTodosCentros: todosCentrosChk.checked
            };

            if (IS_ADMIN) {
                const sel = getSelectedCentroIds();
                payload.centroIds = payload.disponibleTodosCentros ? [] : sel;
            } else {
                if (!ME || !ME.centroId) { setMsg('No se pudo determinar tu centro', 'error'); return; }
                payload.centroIds = [Number(ME.centroId)];
            }

            try {
                const url = EDIT_ID ? '/api/servicios/' + EDIT_ID : '/api/servicios';
                const method = EDIT_ID ? 'PUT' : 'POST';
                const res = await af(url, {
                    method,
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (!res.ok) {
                    const t = await res.text();
                    setFormMsg(t || 'Error al guardar', 'error');
                    return;
                }
                const saved = await res.json();
                const savedId = saved?.id ?? EDIT_ID;
                setMsg(EDIT_ID ? 'Actualizado correctamente' : 'Guardado correctamente', 'success');
                closeForm();
                await loadServicios();
                if (savedId) dt?.one('draw', () => flashRow(savedId));
            } catch { setMsg('Error de red', 'error'); }
        });

        tbody.addEventListener('click', async (e) => {
            const editBtn = e.target.closest('button.edit');
            const delBtn = e.target.closest('button.del');

            if (editBtn) {
                const id = Number(editBtn.getAttribute('data-id'));
                const r = await af('/api/servicios/' + id, { headers: { Accept: 'application/json' } });
                if (!r.ok) { setMsg('No se pudo cargar el elemento', 'error'); return; }
                const it = await r.json();
                if (!rowCanEdit(it)) { setMsg('No tienes permiso para editar este elemento', 'error'); return; }

                $('#tipo').value = it.tipo;
                $('#descripcion').value = it.descripcion || '';
                $('#importe').value = Number(it.importe);
                $('#stock').value = (it.stock == null ? '' : it.stock);
                $('#editable').checked = !!it.editable;
                importeCeroChk.checked = !!it.importeCeroEnTicket;
                todosCentrosChk.checked = !!it.disponibleTodosCentros;

                if (IS_ADMIN) {
                    const ids = Array.isArray(it.centros) ? it.centros.map(c => Number(c.id)) : [];
                    if (!centrosSel.options.length) await loadCentrosInto(centrosSel, false);
                    setSelectedCentroIds(ids);
                }

                EDIT_ID = id;
                openForm(true);
                return;
            }

            if (delBtn) {
                const id = delBtn.getAttribute('data-id');
                const item = servicios.find(s => String(s.id) === String(id));
                const label = item?.descripcion ? `"${item.descripcion}"` : `#${id}`;
                if (!confirm(`¿Eliminar el servicio ${label}?`)) return;
                const res = await af('/api/servicios/' + id, { method: 'DELETE' });
                if (res.status === 204) { setMsg('Servicio eliminado', 'success'); await loadServicios(); }
                else { setMsg('No se pudo eliminar', 'error'); }
            }
        });

        syncTipoRules();
        await loadServicios();
    }

    init();
})();
